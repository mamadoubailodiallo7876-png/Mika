package com.wangzan101.mika.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal sealed interface DeepgramSttEvent {
    data object SpeechStarted : DeepgramSttEvent
    data object UtteranceEnd : DeepgramSttEvent
    data class Transcript(
        val text: String,
        val isFinal: Boolean,
        val speechFinal: Boolean,
    ) : DeepgramSttEvent
    data class Error(val description: String) : DeepgramSttEvent
    data class Other(val type: String) : DeepgramSttEvent
}

/** Pure-Kotlin protocol helpers for configurable Deepgram streaming STT (`/v1/listen`). */
internal object DeepgramProtocol {
    const val DEFAULT_BASE_URL = "https://api.deepgram.com"
    const val DEFAULT_STT_MODEL = "nova-3"
    val STT_MODELS = listOf("nova-3", "nova-2")

    /**
     * Normalizes user-entered Deepgram HTTP/WebSocket base URL to clean scheme + host (+ optional path).
     * Rejects URLs carrying embedded credentials so secrets are never stored in plain config.
     */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
            "://" in trimmed -> return null
            trimmed.startsWith("127.") || trimmed.startsWith("localhost") || trimmed.startsWith("10.") -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        return withScheme.trimEnd('/')
    }

    fun wsBaseOf(baseUrl: String): String {
        val clean = normalizeBaseUrl(baseUrl) ?: DEFAULT_BASE_URL
        return when {
            clean.startsWith("https://", ignoreCase = true) -> "wss://" + clean.substring(8)
            clean.startsWith("http://", ignoreCase = true) -> "ws://" + clean.substring(7)
            else -> clean
        }
    }

    fun hostOf(baseUrl: String): String =
        normalizeBaseUrl(baseUrl)?.toHttpUrlOrNull()?.host ?: "api.deepgram.com"

    fun validModel(model: String): Boolean {
        val clean = model.trim()
        return clean.isNotEmpty() && clean.length <= 120 && !clean.any(Char::isWhitespace)
    }

    fun listenLanguageParam(language: AsrLanguage, bcp47Tag: String? = null): String {
        if (language != AsrLanguage.Auto) return language.code
        val clean = bcp47Tag.orEmpty().trim()
        if (clean.isEmpty() || clean.startsWith("en", ignoreCase = true)) return "multi"
        return clean
    }

    fun buildListenWsUrl(
        baseUrl: String = DEFAULT_BASE_URL,
        model: String = DEFAULT_STT_MODEL,
        language: AsrLanguage = AsrLanguage.Auto,
        bcp47Tag: String? = null,
    ): String {
        val wsBase = wsBaseOf(baseUrl)
        val cleanModel = URLEncoder.encode(model.trim().ifEmpty { DEFAULT_STT_MODEL }, StandardCharsets.UTF_8.name())
        val lang = URLEncoder.encode(listenLanguageParam(language, bcp47Tag), StandardCharsets.UTF_8.name())
        return "$wsBase/v1/listen" +
            "?model=$cleanModel" +
            "&language=$lang" +
            "&encoding=linear16" +
            "&sample_rate=16000" +
            "&channels=1" +
            "&interim_results=true" +
            "&vad_events=true" +
            "&endpointing=1100" +
            "&utterance_end_ms=1600" +
            "&smart_format=true"
    }

    fun buildCloseStreamJson(): String = """{"type":"CloseStream"}"""

    fun parseSttEvent(rawJson: String): DeepgramSttEvent = runCatching {
        val obj = Json.parseToJsonElement(rawJson).jsonObject
        when (val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()) {
            "SpeechStarted" -> DeepgramSttEvent.SpeechStarted
            "UtteranceEnd" -> DeepgramSttEvent.UtteranceEnd
            "Results" -> {
                val alt = obj["channel"]
                    ?.jsonObject
                    ?.get("alternatives")
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                val text = alt?.get("transcript")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val isFinal = obj["is_final"]?.jsonPrimitive?.booleanOrNull == true
                val speechFinal = obj["speech_final"]?.jsonPrimitive?.booleanOrNull == true
                DeepgramSttEvent.Transcript(text = text, isFinal = isFinal, speechFinal = speechFinal)
            }
            "Error" -> DeepgramSttEvent.Error(
                obj["description"]?.jsonPrimitive?.contentOrNull
                    ?: obj["message"]?.jsonPrimitive?.contentOrNull
                    ?: "Deepgram STT error",
            )
            else -> {
                val errMsg = obj["err_msg"]?.jsonPrimitive?.contentOrNull
                if (!errMsg.isNullOrBlank()) DeepgramSttEvent.Error(errMsg)
                else DeepgramSttEvent.Other(type)
            }
        }
    }.getOrElse {
        DeepgramSttEvent.Other("malformed")
    }
}

/** [LiveAsrProtocol] adapter for Deepgram `/v1/listen` streaming sessions and setup probes. */
internal class DeepgramAsrProtocol : LiveAsrProtocol {
    private val finals = mutableListOf<String>()
    private var tail = ""

    override val awaitsStartSignal: Boolean get() = false

    override fun opening(): List<String> = emptyList()

    override fun closing(): List<String> = listOf(DeepgramProtocol.buildCloseStreamJson())

    override fun read(message: String): LiveAsrUpdate = when (val event = DeepgramProtocol.parseSttEvent(message)) {
        is DeepgramSttEvent.Transcript -> {
            if (event.isFinal) {
                if (event.text.isNotEmpty()) finals += event.text
                tail = ""
            } else {
                tail = event.text
            }
            LiveAsrUpdate.Text(joinAsr(finals), tail)
        }
        is DeepgramSttEvent.Error -> {
            val auth = event.description.contains("auth", ignoreCase = true) ||
                event.description.contains("credential", ignoreCase = true) ||
                event.description.contains("unauthorized", ignoreCase = true)
            throw LiveAsrError(if (auth) "401" else "400", auth = auth)
        }
        is DeepgramSttEvent.Other -> if (event.type == "Metadata") LiveAsrUpdate.Ended() else LiveAsrUpdate.None
        DeepgramSttEvent.SpeechStarted, DeepgramSttEvent.UtteranceEnd -> LiveAsrUpdate.None
    }

    override fun onStreamClosed(lastText: LiveAsrUpdate.Text): String =
        joinAsr(listOf(lastText.stable, lastText.tail))
}
