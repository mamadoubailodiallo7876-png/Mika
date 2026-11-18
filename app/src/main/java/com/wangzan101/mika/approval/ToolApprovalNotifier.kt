package com.wangzan101.mika.approval

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.core.tool.ApprovalLevel
import com.wangzan101.mika.core.tool.ToolApprovalRequest

/** 把一次待批准的调用做成带三个按钮的弹出通知(批准 / 拒绝 / 批准并 30 分钟内不再问这一档)。 */
class ToolApprovalNotifier(context: Context) {
    private val app = context.applicationContext
    private val nm = app.getSystemService(NotificationManager::class.java)

    /** 没法问(通知被用户关了)返回 false:宁可不执行也不静默放行。 */
    fun show(id: Int, request: ToolApprovalRequest): Boolean {
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "notifications are disabled; refusing ${request.tool}")
            return false
        }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tool approvals", NotificationManager.IMPORTANCE_HIGH))
        val level = if (request.level == ApprovalLevel.High) "high-risk" else "outward"
        val body = buildString {
            append("Tool: ").append(request.tool).append(" (").append(level).append(")")
            request.reason?.let { append("\nWhy: ").append(it) }
            if (request.args.isNotBlank() && request.args != "{}") append("\nDetails: ").append(request.args)
        }
        val shownName = request.label?.takeIf { it.isNotBlank() } ?: request.tool
        val public = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mika needs your approval")
            .build()
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mika wants to use: $shownName")
            .setContentText(request.reason ?: request.args)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setOngoing(true)
            .setTimeoutAfter(ToolApprovalCoordinator.TIMEOUT_MS)
            .setDeleteIntent(intent(id, ApprovalChoice.Refuse))
            .addAction(Notification.Action.Builder(null, "Approve", intent(id, ApprovalChoice.Approve)).build())
            .addAction(Notification.Action.Builder(null, "Refuse", intent(id, ApprovalChoice.Refuse)).build())
            .addAction(Notification.Action.Builder(null, "Approve 30 min", intent(id, ApprovalChoice.ApproveFor30Min)).build())
            .build()
        nm.notify(BASE_ID + id, n)
        return true
    }

    fun dismiss(id: Int) = nm.cancel(BASE_ID + id)

    private fun intent(id: Int, choice: ApprovalChoice): PendingIntent = PendingIntent.getBroadcast(
        app, id * 4 + choice.ordinal,
        Intent(app, ToolApprovalReceiver::class.java).setAction(ACTION)
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_CHOICE, choice.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "ToolApproval"
        private const val CHANNEL = "tool_approval"
        private const val BASE_ID = 0x7A00_0000
        const val ACTION = "com.wangzan101.mika.TOOL_APPROVAL"
        const val EXTRA_ID = "id"
        const val EXTRA_CHOICE = "choice"
    }
}

/** 通知按钮的落点(不导出)。 */
class ToolApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ToolApprovalNotifier.ACTION) return
        val id = intent.getIntExtra(ToolApprovalNotifier.EXTRA_ID, -1)
        val choice = runCatching { ApprovalChoice.valueOf(intent.getStringExtra(ToolApprovalNotifier.EXTRA_CHOICE).orEmpty()) }
            .getOrDefault(ApprovalChoice.Refuse)
        (context.applicationContext as? MikaApp)?.toolApproval?.resolve(id, choice)
        context.getSystemService(NotificationManager::class.java).cancel(0x7A00_0000 + id)
    }
}
