package com.wangzan101.mika.approval

import com.wangzan101.mika.core.tool.ApprovalLevel
import com.wangzan101.mika.core.tool.ToolApprovalRequest
import com.wangzan101.mika.core.tool.Verb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolApprovalCoordinatorTest {
    private fun req(level: ApprovalLevel = ApprovalLevel.High) =
        ToolApprovalRequest("sandbox_run", null, Verb.RunCommand, level, "{}", null)

    private class Harness(val clock: () -> Long, canShow: Boolean = true) {
        val shown = mutableListOf<Int>()
        val dismissed = mutableListOf<Int>()
        val coordinator = ToolApprovalCoordinator(
            show = { id, _ -> if (canShow) shown += id; canShow },
            dismiss = { dismissed += it },
            clock = clock,
        )
    }

    @Test fun approveOnceThenAsksAgain() = runTest {
        val h = Harness(clock = { currentTime })
        var result: Boolean? = null
        launch { result = h.coordinator.approve(req()) }
        runCurrent()
        assertTrue(h.coordinator.resolve(h.shown.single(), ApprovalChoice.Approve))
        runCurrent()
        assertEquals(true, result)
        launch { h.coordinator.approve(req()) }
        runCurrent()
        assertEquals("second call must prompt again", 2, h.shown.size)
        h.coordinator.resolve(h.shown.last(), ApprovalChoice.Refuse)
        runCurrent()
        assertEquals(h.shown.toSet(), h.dismissed.toSet())
    }

    @Test fun refuseBlocks() = runTest {
        val h = Harness(clock = { currentTime })
        var result: Boolean? = null
        launch { result = h.coordinator.approve(req()) }
        runCurrent()
        h.coordinator.resolve(h.shown.single(), ApprovalChoice.Refuse)
        runCurrent()
        assertEquals(false, result)
    }

    @Test fun thirtyMinuteGrantCoversOnlyThatLevelAndExpires() = runTest {
        val h = Harness(clock = { currentTime })
        launch { h.coordinator.approve(req(ApprovalLevel.High)) }
        runCurrent()
        h.coordinator.resolve(h.shown.single(), ApprovalChoice.ApproveFor30Min)
        runCurrent()
        // 同一档:不再弹
        var again: Boolean? = null
        launch { again = h.coordinator.approve(req(ApprovalLevel.High)) }
        runCurrent()
        assertEquals(true, again)
        assertEquals(1, h.shown.size)
        // 另一档:还要问
        launch { h.coordinator.approve(req(ApprovalLevel.Medium)) }
        runCurrent()
        assertEquals(2, h.shown.size)
        h.coordinator.resolve(h.shown.last(), ApprovalChoice.Refuse)
        runCurrent()
        // 过了 30 分钟:又要问
        testScheduler.advanceTimeBy(ToolApprovalCoordinator.GRANT_MS + 1)
        launch { h.coordinator.approve(req(ApprovalLevel.High)) }
        runCurrent()
        assertEquals(3, h.shown.size)
        h.coordinator.resolve(h.shown.last(), ApprovalChoice.Refuse)
        runCurrent()
    }

    @Test fun noAnswerRefusesAfterTimeout() = runTest {
        val h = Harness(clock = { currentTime })
        var result: Boolean? = null
        launch { result = h.coordinator.approve(req()) }
        runCurrent()
        testScheduler.advanceTimeBy(ToolApprovalCoordinator.TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(false, result)
        assertFalse("late tap must be ignored", h.coordinator.resolve(h.shown.single(), ApprovalChoice.Approve))
        assertEquals(h.shown, h.dismissed)
    }

    @Test fun cannotAskMeansRefuse() = runTest {
        val h = Harness({ currentTime }, canShow = false)
        assertFalse(h.coordinator.approve(req()))
    }
}
