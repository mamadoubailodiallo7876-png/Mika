package com.wangzan101.mika.driving

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingLogicTest {
    @Test
    fun splitsSentencesAndStripsMarkdown() {
        val out = SentenceSplitter.split("**Sure.** Your next meeting is at 3 pm in [Room 4](http://x.y). See https://example.com for more!")
        assertEquals(listOf("Sure. Your next meeting is at 3 pm in Room 4.", "See for more!"), out)
    }

    @Test
    fun dropsCodeAndBullets() {
        val out = SentenceSplitter.split("Here you go:\n- first item\n- second item\n```\nrm -rf /\n```")
        assertTrue(out.none { "rm" in it })
        assertTrue(out.joinToString(" ").contains("first item"))
    }

    @Test
    fun emptyAndSymbolOnlyGiveNothing() {
        assertTrue(SentenceSplitter.split("").isEmpty())
        assertTrue(SentenceSplitter.split("***\n---").isEmpty())
    }

    @Test
    fun longSentenceIsWrapped() {
        val long = (1..80).joinToString(" ") { "word$it" } + "."
        val parts = SentenceSplitter.split(long)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 230 })
    }

    @Test
    fun bluetoothNameMatching() {
        assertTrue(BluetoothTrigger.nameLooksLikeCar("My BMW 320d"))
        assertTrue(BluetoothTrigger.nameLooksLikeCar("HEAD UNIT"))
        assertFalse(BluetoothTrigger.nameLooksLikeCar("Sony WH-1000XM5"))
        assertFalse(BluetoothTrigger.nameLooksLikeCar(null))
    }
}
