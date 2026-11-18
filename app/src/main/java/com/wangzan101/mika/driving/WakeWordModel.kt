package com.wangzan101.mika.driving

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * 唤醒词模型:sherpa-onnx 的关键词检测器(zipformer,约 3.3M 参数,Apache-2.0,英文,完全离线、不需要任何 Key)。
 * 发布包约 17 MB;运行只用 int8 的三个 onnx 和 tokens.txt(约 5 MB),其余不留。
 *
 * 唤醒词是文字、不是训练好的音频:按模型的 BPE 切好写在 [KEYWORDS](用 `sherpa-onnx-cli text2token` 生成)。
 * 想换唤醒词,要用同样的模型重新生成这一行。
 */
object WakeWordModel {
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
    const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val TOKENS = "tokens.txt"

    /**
     * 「Hey Jarvis」和「Jarvis」。格式:BPE 片段 `:boost #threshold @名字`。
     * threshold 越大越不容易误唤醒、也越容易漏;单说「Jarvis」比「Hey Jarvis」更容易误触发。
     */
    val KEYWORDS = """
        ▁HE Y ▁JA R VI S :2.0 #0.3 @hey_jarvis
        ▁JA R VI S :2.0 #0.3 @jarvis
    """.trimIndent() + "\n"

    private val pkg = ModelPackage(
        url = URL,
        topDir = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/",
        dirName = "kws",
        required = listOf(ENCODER, DECODER, JOINER, TOKENS),
        minBytes = 5L * 1024 * 1024,
        notificationId = 0x4A42,
        title = "Wake word model",
        keep = { it.endsWith(".int8.onnx") || it == TOKENS },
    )

    val status: StateFlow<ModelStatus> get() = pkg.status
    fun dir(context: Context) = pkg.dir(context)
    fun isReady(context: Context) = pkg.isReady(context)
    fun ensure(context: Context) = pkg.ensure(context)
}
