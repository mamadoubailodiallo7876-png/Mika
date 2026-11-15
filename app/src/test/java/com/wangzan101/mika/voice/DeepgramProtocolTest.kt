package com.wangzan101.mika.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepgramProtocolTest {
    @Test fun normalizeBaseUrlHandlesCloudSelfHostedAndSchemes() {
        assertEquals("https://api.deepgram.com", DeepgramProtocol.normalizeBaseUrl("https://api.deepgram.com/"))
        assertEquals("https://api.deepgram.com", DeepgramProtocol.normalizeBaseUrl("wss://api.deepgram.com/"))
        assertEquals("http://localhost:8080", DeepgramProtocol.normalizeBaseUrl("localhost:8080/"))
        assertEquals("http://127.0.0.1:9000", DeepgramProtocol.normalizeBaseUrl("ws://127.0.0.1:9000"))
        assertEquals("wss://dg.example.com", DeepgramProtocol.wsBaseOf("https://dg.example.com"))
        assertEquals("ws://127.0.0.1:8080", DeepgramProtocol.wsBaseOf("127.0.0.1:8080"))
        assertNull(DeepgramProtocol.normalizeBaseUrl("   "))
        assertNull(DeepgramProtocol.normalizeBaseUrl("ftp://invalid.host"))
        assertNull(DeepgramProtocol.normalizeBaseUrl("https://user:secret@api.deepgram.com"))
    }

    @Test fun listenUrlEncodesConfigurableParameters() {
        val sttUrl = DeepgramProtocol.buildListenWsUrl(
            baseUrl = "https://custom.deepgram.local:8443/",
            model = "nova-3",
            language = AsrLanguage.English,
        )
        assertTrue(sttUrl.startsWith("wss://custom.deepgram.local:8443/v1/listen?"))
        assertTrue(sttUrl.contains("model=nova-3"))
        assertTrue(sttUrl.contains("language=en"))
        assertTrue(sttUrl.contains("sample_rate=16000"))

        val autoStt = DeepgramProtocol.buildListenWsUrl(language = AsrLanguage.Auto, bcp47Tag = "en-US")
        assertTrue(autoStt.contains("language=multi"))
    }

    @Test fun parsesSttServerEventsCleanly() {
        val interim = DeepgramProtocol.parseSttEvent(
            """{"type":"Results","is_final":false,"speech_final":false,"channel":{"alternatives":[{"transcript":"hello world"}]}}""",
        )
        assertEquals(DeepgramSttEvent.Transcript("hello world", isFinal = false, speechFinal = false), interim)
        assertEquals(DeepgramSttEvent.SpeechStarted, DeepgramProtocol.parseSttEvent("""{"type":"SpeechStarted"}"""))
        assertEquals(DeepgramSttEvent.UtteranceEnd, DeepgramProtocol.parseSttEvent("""{"type":"UtteranceEnd"}"""))
        assertEquals(DeepgramSttEvent.Error("bad token"), DeepgramProtocol.parseSttEvent("""{"type":"Error","description":"bad token"}"""))
    }

    @Test fun streamingProtocolAccumulatesFinalsAndRejectsAuthErrors() {
        val proto = DeepgramAsrProtocol()
        assertFalse(proto.awaitsStartSignal)
        assertEquals(emptyList<String>(), proto.opening())
        assertEquals(listOf("""{"type":"CloseStream"}"""), proto.closing())

        assertEquals(
            LiveAsrUpdate.Text("", "hello"),
            proto.read("""{"type":"Results","is_final":false,"channel":{"alternatives":[{"transcript":"hello"}]}}"""),
        )
        assertEquals(
            LiveAsrUpdate.Text("hello world", ""),
            proto.read("""{"type":"Results","is_final":true,"channel":{"alternatives":[{"transcript":"hello world"}]}}"""),
        )
        assertEquals(
            LiveAsrUpdate.Text("hello world", "how are"),
            proto.read("""{"type":"Results","is_final":false,"channel":{"alternatives":[{"transcript":"how are"}]}}"""),
        )
        assertEquals("hello world how are", proto.onStreamClosed(LiveAsrUpdate.Text("hello world", "how are")))
        assertEquals(LiveAsrUpdate.Ended(), proto.read("""{"type":"Metadata","request_id":"1"}"""))

        val err = assertThrows(LiveAsrError::class.java) {
            proto.read("""{"type":"Error","description":"Invalid credentials"}""")
        }
        assertTrue(err.auth)
    }
}
