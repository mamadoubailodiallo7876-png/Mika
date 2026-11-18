package com.wangzan101.mika.driving

/** 把模型的回答(常带 Markdown)切成适合逐句朗读的短句。纯函数。 */
object SentenceSplitter {
    private val codeBlock = Regex("```[\\s\\S]*?```")
    private val link = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val url = Regex("https?://\\S+")
    private val markup = Regex("[*_`#>|~]+")
    private val bullet = Regex("(?m)^\\s*(?:[-+•]|\\d+[.)])\\s+")
    private val boundary = Regex("(?<=[.!?。！？])\\s+|\\n+")
    private const val MAX_LEN = 220
    private const val MIN_LEN = 12

    /** 去掉朗读时会被念出来的标记。 */
    fun clean(text: String): String = text
        .replace(codeBlock, " ")
        .replace(link, "$1")
        .replace(url, " ")
        .replace(bullet, "")
        .replace(markup, "")
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    fun split(text: String): List<String> {
        val pieces = clean(text).split(boundary).map { it.trim() }.filter { it.any(Char::isLetterOrDigit) }
        val out = mutableListOf<String>()
        for (piece in pieces) {
            for (part in wrap(piece)) {
                // 太短的句子并进上一句,免得一个词一次合成
                if (out.isNotEmpty() && (part.length < MIN_LEN || out.last().length < MIN_LEN) && out.last().length + part.length < MAX_LEN) {
                    out[out.lastIndex] = out.last() + " " + part
                } else {
                    out += part
                }
            }
        }
        return out
    }

    /** 超长的句子在逗号等处折开。 */
    private fun wrap(sentence: String): List<String> {
        if (sentence.length <= MAX_LEN) return listOf(sentence)
        val out = mutableListOf<String>()
        var rest = sentence
        while (rest.length > MAX_LEN) {
            var cut = rest.lastIndexOfAny(charArrayOf(',', ';', ':', '，', '；', '：'), MAX_LEN)
            if (cut < MIN_LEN) cut = rest.lastIndexOf(' ', MAX_LEN)
            if (cut < MIN_LEN) cut = MAX_LEN
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}
