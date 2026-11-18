package com.wangzan101.mika.core.tool

import kotlinx.serialization.json.JsonObject

/**
 * 用户对「对外/危险」工具调用的逐次批准。分两档:
 * [High] 能在手机或网络上做不可预知的事(命令、脚本、拨号、改设置、定时任务、外部 MCP 工具……);
 * [Medium] 向外分享、写文件、设闹钟日历、开链接这类。点屏幕、滚动、打字和一切只读调用不问。
 */
enum class ApprovalLevel { Medium, High }

/** 弹给用户看的一次调用:[tool] 是工具名,[label] 是界面上的说法,[args] 是已截短的参数,[reason] 是模型写的用意。 */
data class ToolApprovalRequest(
    val tool: String,
    val label: String?,
    val verb: Verb,
    val level: ApprovalLevel,
    val args: String,
    val reason: String?,
)

/** 返回 true 才放行;false(拒绝、超时、没法询问)则这次调用不执行。 */
fun interface ToolApprovalGate {
    suspend fun approve(request: ToolApprovalRequest): Boolean
}

/**
 * 所有任务(对话、定时、电脑/MCP、微信渠道、驾驶模式)的工具调用都经 [ToolDispatcher] 执行,
 * 所以这一道关口放在那里。App 启动时装上 [gate];没装(核心的单元测试、评测)时不拦。
 */
object ToolApproval {
    @Volatile
    var gate: ToolApprovalGate? = null

    private val high = setOf(
        Verb.RunCommand, Verb.RootCommand, Verb.RunBundledScript, Verb.PluginScript, Verb.Script,
        Verb.EvaluateJs, Verb.SiteCommand, Verb.PhoneCommand, Verb.SmsLogin, Verb.Upload,
        Verb.CallDial, Verb.CallAnswer, Verb.CallHangup, Verb.CallTakeover,
        Verb.SettingsSet, Verb.ScheduleTask, Verb.SpawnAgent,
    )
    private val medium = setOf(
        Verb.WriteFile, Verb.ShareText, Verb.ShareFile, Verb.ShareAudio, Verb.OpenLink,
        Verb.SetAlarm, Verb.SetTimer, Verb.AddCalendarEvent, Verb.BulkAddCalendarEvents,
        Verb.CopyDeviceFile, Verb.AudioSave, Verb.SelfNotify, Verb.SelfCopy,
    )

    /** 这次调用要不要问、问哪一档;只读的调用(含可选写模式的工具在只读用法下)不问。 */
    internal fun levelOf(tool: AgentTool, args: JsonObject): ApprovalLevel? = when {
        tool.risk == Risk.Read || tool.readOnly(args) -> null
        tool.verb in high -> ApprovalLevel.High
        // 外部 MCP 服务器的工具:动词都是 Interact,不在屏幕上
        tool.verb == Verb.Interact && tool.offScreen -> ApprovalLevel.High
        tool.verb in medium -> ApprovalLevel.Medium
        else -> null
    }

    /** 参数摘要:去掉换行、截到 [max] 个字符。 */
    internal fun summarize(argsJson: String, max: Int = 300): String {
        val flat = argsJson.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max - 1) + "…"
    }
}
