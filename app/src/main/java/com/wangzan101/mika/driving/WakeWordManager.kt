package com.wangzan101.mika.driving

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.wangzan101.mika.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 常驻的「Jarvis」唤醒词(Porcupine 内置关键词,不需要 .ppn 文件)。
 *
 * 一条专用后台线程独占麦克风:循环读一帧、交给 Porcupine。听到后发一次 [detections],并**自己先暂停**
 * (释放麦克风),避免同一声被连报、也把麦克风让给后面的语音识别;调用方处理完回到 IDLE 时再 [resume]。
 * [pause] 返回时麦克风已经释放(最多等 1 秒),可以放心接着开识别。
 *
 * 线程安全:start/stop/pause/resume 可以从任意线程调。
 */
class WakeWordManager(
    context: Context,
    private val accessKey: String = BuildConfig.PORCUPINE_ACCESS_KEY,
    private val sensitivity: Float = 0.6f,
) {
    private val context = context.applicationContext

    private val _detections = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 每听到一次「Jarvis」发一次。 */
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** 起不来的原因(没有 AccessKey、没有录音权限、Porcupine 初始化失败);正常时为 null。 */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var thread: Thread? = null
    private var wanted = false
    private var paused = false
    private var micOpen = false

    /** 启动监听线程;已经在跑就什么都不做。返回 false 表示起不来([error] 说明原因)。 */
    fun start(): Boolean {
        if (accessKey.isBlank()) return fail("No Porcupine AccessKey: set PORCUPINE_ACCESS_KEY in local.properties")
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return fail("Microphone permission not granted")
        }
        lock.withLock {
            if (thread?.isAlive == true) {
                wanted = true
                return true
            }
            wanted = true
            paused = false
            _error.value = null
            thread = Thread(::run, "jarvis-wakeword").also { it.start() }
        }
        return true
    }

    /** 停掉线程并释放 Porcupine 和麦克风。 */
    fun stop() {
        val t = lock.withLock {
            wanted = false
            changed.signalAll()
            thread.also { thread = null }
        }
        if (t != null && t !== Thread.currentThread()) t.join(2_000)
    }

    /** 暂停监听并释放麦克风(进入 LISTENING/PROCESSING/SPEAKING 时调)。返回时麦克风已放开,最多等 1 秒。 */
    fun pause() {
        lock.withLock {
            paused = true
            changed.signalAll()
            var left = TimeUnit.SECONDS.toNanos(1)
            while (micOpen && thread?.isAlive == true && left > 0) left = changed.awaitNanos(left)
        }
    }

    /** 回到 IDLE 后恢复监听。 */
    fun resume() {
        lock.withLock {
            paused = false
            changed.signalAll()
        }
    }

    private fun fail(message: String): Boolean {
        Log.w(TAG, message)
        _error.value = message
        return false
    }

    private fun run() {
        var porcupine: Porcupine? = null
        var recorder: AudioRecord? = null
        try {
            porcupine = try {
                Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                    .setSensitivity(sensitivity)
                    .build(context)
            } catch (e: PorcupineException) {
                fail("Porcupine failed to start: ${e.message}")
                return
            }
            val frame = ShortArray(porcupine.frameLength)
            while (true) {
                lock.withLock {
                    while (wanted && paused) {
                        if (recorder != null) {
                            releaseRecorder(recorder)
                            recorder = null
                        }
                        micOpen = false
                        changed.signalAll()
                        changed.await()
                    }
                    if (!wanted) return
                }
                val rec = recorder ?: openRecorder(frame.size, porcupine.sampleRate).also { recorder = it }
                if (rec == null) {
                    // 麦克风被别的 App 占着等:隔一会儿再试,不空转
                    lock.withLock { if (wanted && !paused) changed.awaitNanos(TimeUnit.SECONDS.toNanos(2)) }
                    continue
                }
                lock.withLock { micOpen = true }
                var filled = 0
                while (filled < frame.size) {
                    val n = rec.read(frame, filled, frame.size - filled)
                    if (n < 0) {
                        // 录音器出错(被抢占、设备断开):丢掉重开
                        Log.w(TAG, "AudioRecord.read failed: $n")
                        releaseRecorder(rec)
                        recorder = null
                        lock.withLock { micOpen = false; changed.signalAll() }
                        break
                    }
                    filled += n
                }
                if (filled < frame.size) continue
                val hit = try {
                    porcupine.process(frame) >= 0
                } catch (e: PorcupineException) {
                    fail("Porcupine error: ${e.message}")
                    return
                }
                if (hit) {
                    lock.withLock { paused = true }
                    _detections.tryEmit(Unit)
                }
            }
        } finally {
            recorder?.let(::releaseRecorder)
            runCatching { porcupine?.delete() }
            lock.withLock {
                micOpen = false
                changed.signalAll()
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // start() 已检查 RECORD_AUDIO
    private fun openRecorder(frameLength: Int, sampleRate: Int): AudioRecord? {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes, frameLength * 2 * 4),
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord init failed", e)
            return null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        return try {
            rec.startRecording()
            rec
        } catch (e: IllegalStateException) {
            rec.release()
            null
        }
    }

    private fun releaseRecorder(rec: AudioRecord) {
        runCatching { rec.stop() }
        runCatching { rec.release() }
    }

    private companion object {
        const val TAG = "JarvisWakeWord"
    }
}
