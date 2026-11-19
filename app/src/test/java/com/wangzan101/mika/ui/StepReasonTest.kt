package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StepReasonTest {

    @Test
    fun testUserScreenshotCases() {
        val s1 = translateStepReason("点击“发送消息”按钮以查找联系人 Ved。")
        assertEquals("Tap \"Send message\" button to find contact Ved", s1)

        val s2 = translateStepReason("点击搜索按钮查找 Ved。")
        assertEquals("Tap search button to find Ved", s2)

        val s3 = translateStepReason("在搜索框输入“ved”。")
        assertEquals("Type \"ved\"", s3)
    }

    @Test
    fun testPureEnglishUnchanged() {
        val eng = "Tap search button at top right"
        assertEquals(eng, translateStepReason(eng))
    }

    @Test
    fun testEmptyAndWhitespace() {
        assertEquals("", translateStepReason(""))
        assertEquals("   ", translateStepReason("   "))
    }
}
