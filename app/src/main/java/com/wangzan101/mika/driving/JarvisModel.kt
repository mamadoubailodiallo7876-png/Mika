package com.wangzan101.mika.driving

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * 声音模型(Piper 英式男声 en_GB-alan-medium)。发布包是 sherpa-onnx 的 `vits-piper-en_GB-alan-medium.tar.bz2`(约 67 MB)。
 * 除了 .onnx 和 .onnx.json,合成还必须有 `tokens.txt` 和 `espeak-ng-data/`(音素字典),所以整包解到 `filesDir/tts/`。
 */
object JarvisModel {
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2"
    const val ONNX = "en_GB-alan-medium.onnx"
    const val ONNX_JSON = "en_GB-alan-medium.onnx.json"
    const val TOKENS = "tokens.txt"
    const val ESPEAK_DIR = "espeak-ng-data"

    private val pkg = ModelPackage(
        url = URL,
        topDir = "vits-piper-en_GB-alan-medium/",
        dirName = "tts",
        required = listOf(ONNX, TOKENS, ESPEAK_DIR),
        minBytes = 10L * 1024 * 1024, // 发布包不会小于这个;太小说明拿到的是错误页
        notificationId = 0x4A41,
        title = "Jarvis voice",
    )

    val status: StateFlow<ModelStatus> get() = pkg.status
    fun dir(context: Context) = pkg.dir(context)
    fun isReady(context: Context) = pkg.isReady(context)

    /** 没有模型就在后台下载(受 [DrivingPrefs.wifiOnlyDownload] 约束);已有或正在下载则不重复。 */
    fun ensure(context: Context) = pkg.ensure(context)
}
