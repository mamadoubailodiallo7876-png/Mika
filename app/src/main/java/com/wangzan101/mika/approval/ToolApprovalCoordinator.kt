package com.wangzan101.mika.approval

import com.wangzan101.mika.core.tool.ApprovalLevel
import com.wangzan101.mika.core.tool.ToolApprovalGate
import com.wangzan101.mika.core.tool.ToolApprovalRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class ApprovalChoice { Approve, Refuse, ApproveFor30Min }

/**
 * 批准流程本身,不碰安卓:先看这一档有没有还在有效期内的「30 分钟不再问」,没有就用 [show] 弹给用户,
 * 等 [resolve];超过 [timeoutMs] 没人答按拒绝。[show] 返回 false 表示没法问(通知被关了),也按拒绝。
 * 「30 分钟」按档([ApprovalLevel])记,只在内存里,进程重启就没了。
 */
class ToolApprovalCoordinator(
    private val show: (id: Int, request: ToolApprovalRequest) -> Boolean,
    private val dismiss: (id: Int) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val grantMs: Long = GRANT_MS,
) : ToolApprovalGate {
    private val grants = ConcurrentHashMap<ApprovalLevel, Long>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<ApprovalChoice>>()
    private val ids = AtomicInteger()

    override suspend fun approve(request: ToolApprovalRequest): Boolean {
        if ((grants[request.level] ?: 0L) > clock()) return true
        val id = ids.incrementAndGet()
        val answer = CompletableDeferred<ApprovalChoice>()
        pending[id] = answer
        try {
            if (!show(id, request)) return false
            val choice = withTimeoutOrNull(timeoutMs) { answer.await() } ?: ApprovalChoice.Refuse
            if (choice == ApprovalChoice.ApproveFor30Min) grants[request.level] = clock() + grantMs
            return choice != ApprovalChoice.Refuse
        } finally {
            pending.remove(id)
            dismiss(id)
        }
    }

    /** 用户点了通知上的按钮。已经超时、已经答过或进程重启后返回 false。 */
    fun resolve(id: Int, choice: ApprovalChoice): Boolean = pending[id]?.complete(choice) == true

    companion object {
        const val TIMEOUT_MS = 2 * 60_000L
        const val GRANT_MS = 30 * 60_000L
    }
}
