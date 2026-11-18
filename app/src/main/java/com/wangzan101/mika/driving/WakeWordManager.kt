package com.wangzan101.mika.driving

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 常驻的「Jarvis」/「Hey Jarvis」唤醒词:sherpa-onnx 关键词检测(模型见 [WakeWordModel]),免费、离线、不需要 Key。
 *
 * 一条专用后台线程独占麦克风:每 100 ms 读一段 16 kHz 音频喂给检测器。听到后发一次 [detections],并**自己先暂停**
 * (释放麦克风),避免同一声被连报、也把麦克风让给后面的语音识别;调用方处理完回到 IDLE 时再 [resume]。
 * [pause] 返回时麦克风已经释放(最多等 1 秒),可以放心接着开识别。
 *
 * 线程安全:start/stop/pause/resume 可以从任意线程调。
 */
class WakeWordManager(
    context: Context,
    /** 越大越不容易误唤醒、也越容易漏;和 [WakeWordModel.KEYWORDS] 里每个词自己的阈值一起起作用。 */
    private val keywordsThreshold: Float = 0.25f,
) {
    private val context = context.applicationContext

    private val _detections = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 每听到一次唤醒词发一次。 */
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** 起不来的原因(没有录音权限、模型还没下好、模型加载失败);正常时为 null。 */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var thread: Thread? = null
    private var wanted = false
    private var paused = false
    private var micOpen = false

    /** 启动监听线程;已经在跑就什么都不做。返回 false 表示起不来([error] 说明原因)。 */
    fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return fail("Microphone permission not granted")
        }
        if (!WakeWordModel.isReady(context)) return fail("Wake word model is not downloaded yet")
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

    /** 停掉线程并释放检测器和麦克风。 */
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

    private fun buildSpotter(): KeywordSpotter? {
        val dir = WakeWordModel.dir(context)
        val keywords = File(dir, "jarvis_keywords.txt").also { it.writeText(WakeWordModel.KEYWORDS) }
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(dir, WakeWordModel.ENCODER).path,
                    decoder = File(dir, WakeWordModel.DECODER).path,
                    joiner = File(dir, WakeWordModel.JOINER).path,
                ),
                tokens = File(dir, WakeWordModel.TOKENS).path,
                numThreads = 1,
                provider = "cpu",
                modelType = "zipformer2",
            ),
            maxActivePaths = 4,
            keywordsFile = keywords.path,
            keywordsScore = 1.0f,
            keywordsThreshold = keywordsThreshold,
            numTrailingBlanks = 1,
        )
        return try {
            KeywordSpotter(config = config)
        } catch (e: Throwable) {
            fail("Wake word engine failed to start: ${e.message}")
            null
        }
    }

    private fun run() {
        var spotter: KeywordSpotter? = null
        var stream: OnlineStream? = null
        var recorder: AudioRecord? = null
        try {
            spotter = buildSpotter() ?: return
            val pcm = ShortArray(FRAME)
            val floats = FloatArray(FRAME)
            while (true) {
                lock.withLock {
                    while (wanted && paused) {
                        if (recorder != null) {
                            releaseRecorder(recorder)
                            recorder = null
                        }
                        stream?.release()
                        stream = null
                        micOpen = false
                        changed.signalAll()
                        changed.await()
                    }
                    if (!wanted) return
                }
                val rec = recorder ?: openRecorder().also { recorder = it }
                if (rec == null) {
                    // 麦克风被别的 App 占着等:隔一会儿再试,不空转
                    lock.withLock { if (wanted && !paused) changed.awaitNanos(TimeUnit.SECONDS.toNanos(2)) }
                    continue
                }
                val s = stream ?: spotter.createStream("").also { stream = it }
                lock.withLock { micOpen = true }
                var filled = 0
                while (filled < FRAME) {
                    val n = rec.read(pcm, filled, FRAME - filled)
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
                if (filled < FRAME) continue
                for (i in 0 until FRAME) floats[i] = pcm[i] / 32768f
                s.acceptWaveform(floats, SAMPLE_RATE)
                var hit = false
                while (spotter.isReady(s)) {
                    spotter.decode(s)
                    if (spotter.getResult(s).keyword.isNotEmpty()) {
                        hit = true
                        spotter.reset(s)
                    }
                }
                if (hit) {
                    lock.withLock { paused = true }
                    _detections.tryEmit(Unit)
                }
            }
        } finally {
            recorder?.let(::releaseRecorder)
            runCatching { stream?.release() }
            runCatching { spotter?.release() }
            lock.withLock {
                micOpen = false
                changed.signalAll()
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // start() 已检查 RECORD_AUDIO
    private fun openRecorder(): AudioRecord? {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes, FRAME * 2 * 4),
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
        const val SAMPLE_RATE = 16_000
        /** 100 ms。 */
        const val FRAME = 1_600
    }
}
