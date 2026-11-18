package com.wangzan101.mika.core.tool

import com.wangzan101.mika.core.assembly.allTools
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolApprovalTest {
    private val none = JsonObject(emptyMap())
    private val tools = allTools().associateBy { it.name }

    private fun level(name: String) = ToolApproval.levelOf(requireNotNull(tools[name]) { "no tool $name" }, none)

    @Test fun screenGesturesAndReadsDoNotPrompt() {
        for (t in tools.values.filter { it.verb in setOf(Verb.Tap, Verb.TypeText, Verb.ClearText, Verb.Scroll, Verb.Swipe, Verb.Back, Verb.PressKey, Verb.Observe, Verb.ListElements) }) {
            assertNull("${t.name} should not prompt", ToolApproval.levelOf(t, none))
        }
    }

    @Test fun readOnlyToolsNeverPrompt() {
        for (t in tools.values.filter { it.risk == Risk.Read }) assertNull(t.name, ToolApproval.levelOf(t, none))
    }

    @Test fun commandsAndCallsAreHighLevel() {
        for (t in tools.values.filter { it.verb in setOf(Verb.RunCommand, Verb.RootCommand, Verb.CallDial, Verb.SettingsSet, Verb.ScheduleTask) && it.risk != Risk.Read }) {
            assertEquals(t.name, ApprovalLevel.High, ToolApproval.levelOf(t, none))
        }
    }

    @Test fun everyDangerousVerbIsCoveredByAtLeastOneTool() {
        // 防止分类表里的动词和真实工具对不上(改名后悄悄不拦)
        val gated = tools.values.filter { ToolApproval.levelOf(it, none) != null }
        assertNotNull(gated.firstOrNull { it.verb == Verb.RunCommand })
        assertNotNull(gated.firstOrNull { it.verb == Verb.RootCommand })
        assertNotNull(gated.firstOrNull { it.verb == Verb.CallDial })
        println("GATED: " + gated.joinToString { "${it.name}=${ToolApproval.levelOf(it, none)}" })
    }

    @Test fun summarizeCollapsesAndTruncates() {
        assertEquals("{ \"a\": 1 }", ToolApproval.summarize("{\n \"a\":   1\n}"))
        assertEquals(10, ToolApproval.summarize("x".repeat(50), 10).length)
    }
}
