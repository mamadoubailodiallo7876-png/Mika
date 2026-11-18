package com.wangzan101.mika.driving

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.wangzan101.mika.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 声音模型(Piper 英式男声 en_GB-alan-medium)的下载状态。 */
sealed interface ModelStatus {
    data object Missing : ModelStatus
    /** [percent] 为 -1 表示总大小未知。 */
    data class Downloading(val percent: Int) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
    /** 要求只在 Wi-Fi 下载,而当前是计费网络。 */
    data object WaitingForWifi : ModelStatus
}

/**
 * 声音模型的下载与落盘:[ensure] 幂等,已经就绪或正在下载都直接返回。
 *
 * 发布包是 sherpa-onnx 的 `vits-piper-en_GB-alan-medium.tar.bz2`(约 67 MB)。除了 .onnx 和 .onnx.json,
 * 合成还必须有 `tokens.txt` 和 `espeak-ng-data/`(音素字典),所以整包解到 `filesDir/tts/`(去掉最外层目录)。
 * 解压先进临时目录、全部成功才改名,中途断了不会留下半个模型。
 */
object JarvisModel {
    private const val TAG = "JarvisModel"
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2"
    const val ONNX = "en_GB-alan-medium.onnx"
    const val ONNX_JSON = "en_GB-alan-medium.onnx.json"
    const val TOKENS = "tokens.txt"
    const val ESPEAK_DIR = "espeak-ng-data"
    private const val TOP_DIR = "vits-piper-en_GB-alan-medium/"
    private const val READY_MARK = ".ready"
    private const val CHANNEL = "jarvis_model"
    private const val NOTIFICATION_ID = 0x4A41
    /** 发布包不会小于这个;太小说明拿到的是错误页。 */
    private const val MIN_BYTES = 10L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Missing)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun dir(context: Context) = File(context.filesDir, "tts")

    fun isReady(context: Context): Boolean {
        val d = dir(context)
        return File(d, READY_MARK).isFile && File(d, ONNX).isFile && File(d, TOKENS).isFile && File(d, ESPEAK_DIR).isDirectory
    }

    /** 没有模型就在后台下载(受 [DrivingPrefs.wifiOnlyDownload] 约束);已有或正在下载则不重复。 */
    @Synchronized
    fun ensure(context: Context) {
        val app = context.applicationContext
        if (isReady(app)) {
            _status.value = ModelStatus.Ready
            return
        }
        if (job?.isActive == true) return
        if (DrivingPrefs.get(app).wifiOnlyDownload && isMetered(app)) {
            _status.value = ModelStatus.WaitingForWifi
            return
        }
        job = scope.launch {
            _status.value = ModelStatus.Downloading(0)
            try {
                download(app)
                _status.value = ModelStatus.Ready
                notifyDone(app, "Jarvis voice ready", "Downloaded. Driving mode now speaks offline.")
            } catch (e: Exception) {
                if (!isActive) throw e
                Log.w(TAG, "download failed", e)
                _status.value = ModelStatus.Failed(e.message ?: e.javaClass.simpleName)
                notifyDone(app, "Jarvis voice download failed", "Using the phone's built-in voice for now.")
            }
        }
    }

    private fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return true) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun download(context: Context) {
        val root = dir(context)
        val parent = root.parentFile!!
        val archive = File(parent, "tts-download.tar.bz2")
        val staging = File(parent, "tts-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()
        client.newCall(Request.Builder().url(URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty response")
            val total = body.contentLength()
            var done = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                archive.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPercent) {
                                lastPercent = pct
                                _status.value = ModelStatus.Downloading(pct)
                                notifyProgress(context, pct)
                            }
                        }
                    }
                }
            }
            if (archive.length() < MIN_BYTES) throw IOException("download too small (${archive.length()} bytes)")
        }
        notifyProgress(context, -1)
        try {
            extract(archive, staging)
            if (!File(staging, ONNX).isFile || !File(staging, TOKENS).isFile || !File(staging, ESPEAK_DIR).isDirectory) {
                throw IOException("model archive is missing required files")
            }
            File(staging, READY_MARK).writeText("1")
            root.deleteRecursively()
            if (!staging.renameTo(root)) throw IOException("cannot move model into place")
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    /** 解 tar.bz2 到 [into],去掉最外层目录;拒绝跳出目标目录的条目。 */
    private fun extract(archive: File, into: File) {
        val base = into.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val name = entry.name.removePrefix("./").removePrefix(TOP_DIR)
                if (name.isEmpty() || !entry.isDirectory && !entry.isFile) continue
                val target = File(base, name).canonicalFile
                if (target != base && !target.path.startsWith(base.path + File.separator)) throw IOException("unsafe path in archive: ${entry.name}")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jarvis voice download", NotificationManager.IMPORTANCE_LOW))
        return nm
    }

    private fun notifyProgress(context: Context, percent: Int) {
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading Jarvis voice")
            .setContentText(if (percent >= 0) "$percent%" else "Unpacking…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()
        runCatching { manager(context).notify(NOTIFICATION_ID, n) }
    }

    private fun notifyDone(context: Context, title: String, text: String) {
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { manager(context).notify(NOTIFICATION_ID, n) }
    }
}
