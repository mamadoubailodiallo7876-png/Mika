package com.wangzan101.mika.core.cap.plugin

import com.wangzan101.mika.core.capability.Capability
import com.wangzan101.mika.core.capability.CapabilityStatus
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.failCode
import com.wangzan101.mika.core.tool.ok
import com.wangzan101.mika.core.tool.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val PLUGINS_CAP = "plugins"

interface PluginPort {
    suspend fun listPlugins(): String
    suspend fun openPlugin(idOrTitle: String, background: Boolean): String
    suspend fun readPlugin(idOrTitle: String): String
    suspend fun interactPlugin(idOrTitle: String, action: String, selector: String?, text: String?): String
    suspend fun evalPlugin(idOrTitle: String, js: String): String
    suspend fun managePlugin(
        action: String,
        idOrTitle: String?,
        title: String?,
        icon: String?,
        description: String?,
        html: String?,
    ): String
}

object PluginPortRegistry {
    @Volatile
    var port: PluginPort? = null
}

private fun requirePort(): PluginPort? = PluginPortRegistry.port

internal object PluginListTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Read
    override val def = ToolDef(
        "plugin_list",
        "List all installed Mika Studio plugins with id, title, description, running status, is_open status, and size_kb.",
        emptyMap(),
        emptyList(),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val res = runCatching { port.listPlugins() }.getOrElse { return failCode("plugin_error") }
        return ok { put("plugins", res) }
    }
}

internal object PluginOpenTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Write
    override val def = ToolDef(
        "plugin_open",
        "Open a Mika Studio plugin on screen (or start it silently in background if background=true). Never use Chrome or external browsers for Mika plugins.",
        mapOf(
            "id" to str("Plugin id or title (e.g. 'g' or 'FluxGraph')."),
            "background" to buildJsonObject {
                put("type", "boolean")
                put("description", "If true, starts the plugin in background without switching screen (default false).")
            },
        ),
        listOf("id"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val id = a.str("id", max = 200)?.trim().orEmpty()
        if (id.isBlank()) return failCode("missing_id")
        val bg = a.bool("background", false)
        val res = runCatching { port.openPlugin(id, bg) }.getOrElse { return failCode("plugin_error") }
        return ok { put("result", res) }
    }
}

internal object PluginReadTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Read
    override val def = ToolDef(
        "plugin_read",
        "Inspect the live UI of a Mika plugin (visible text, clickable buttons, inputs, selects with CSS selectors), just like reading a browser page.",
        mapOf("id" to str("Plugin id or title.")),
        listOf("id"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val id = a.str("id", max = 200)?.trim().orEmpty()
        if (id.isBlank()) return failCode("missing_id")
        val res = runCatching { port.readPlugin(id) }.getOrElse { return failCode("plugin_error") }
        return ok { put("snapshot", res) }
    }
}

internal object PluginInteractTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Write
    override val def = ToolDef(
        "plugin_interact",
        "Interact with a Mika plugin UI directly: click a button/element (by CSS selector or visible text), type into an input/textarea, or select an option.",
        mapOf(
            "id" to str("Plugin id or title."),
            "action" to str("Action to perform: 'click', 'type', or 'select'."),
            "selector" to str("CSS selector of the target element (optional for click if text is provided)."),
            "text" to str("Visible text to click (for 'click') or value to enter/select (for 'type'/'select')."),
        ),
        listOf("id", "action"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val id = a.str("id", max = 200)?.trim().orEmpty()
        val action = a.str("action", max = 40)?.trim()?.lowercase().orEmpty()
        if (id.isBlank() || action.isBlank()) return failCode("missing_args")
        val selector = a.str("selector", max = 400)
        val text = a.str("text", max = 4000)
        val res = runCatching { port.interactPlugin(id, action, selector, text) }.getOrElse { return failCode("plugin_error") }
        return ok { put("result", res) }
    }
}

internal object PluginEvalTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Write
    override val def = ToolDef(
        "plugin_eval",
        "Execute JavaScript directly inside any Mika plugin WebView (open or background) and return the result immediately.",
        mapOf(
            "id" to str("Plugin id or title."),
            "js" to str("JavaScript expression or IIFE to execute inside the plugin."),
        ),
        listOf("id", "js"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val id = a.str("id", max = 200)?.trim().orEmpty()
        val js = a.str("js", max = 20000).orEmpty()
        if (id.isBlank() || js.isBlank()) return failCode("missing_args")
        val res = runCatching { port.evalPlugin(id, js) }.getOrElse { return failCode("plugin_error") }
        return ok { put("result", res) }
    }
}

internal object PluginManageTool : AgentTool {
    override val capability = PLUGINS_CAP
    override val risk = Risk.Write
    override val def = ToolDef(
        "plugin_manage",
        "Manage Mika plugins: 'close' (return to chat keeping background running), 'stop' (terminate plugin), 'create', 'update', or 'delete'.",
        mapOf(
            "action" to str("One of: 'close', 'stop', 'create', 'update', 'delete'."),
            "id" to str("Plugin id or title (required for stop/update/delete)."),
            "title" to str("Plugin title (for create/update)."),
            "icon" to str("Plugin emoji icon (for create/update)."),
            "description" to str("Plugin description (for create/update)."),
            "html" to str("Full HTML/CSS/JS source (for create/update)."),
        ),
        listOf("action"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = requirePort() ?: return failCode("plugins_unavailable")
        val action = a.str("action", max = 40)?.trim()?.lowercase().orEmpty()
        if (action.isBlank()) return failCode("missing_action")
        val res = runCatching {
            port.managePlugin(
                action = action,
                idOrTitle = a.str("id", max = 200),
                title = a.str("title", max = 200),
                icon = a.str("icon", max = 20),
                description = a.str("description", max = 400),
                html = a.str("html", max = 200000),
            )
        }.getOrElse { return failCode("plugin_error") }
        return ok { put("result", res) }
    }
}

internal object PluginGroup {
    val tools: List<AgentTool> = listOf(
        PluginListTool,
        PluginOpenTool,
        PluginReadTool,
        PluginInteractTool,
        PluginEvalTool,
        PluginManageTool,
    )
}

internal class PluginCapability : Capability {
    override val id = PLUGINS_CAP
    override val folds = false
    override fun status(): CapabilityStatus =
        if (PluginPortRegistry.port != null) CapabilityStatus.Available
        else CapabilityStatus.Unavailable("plugins unavailable")
    override fun tools(): List<AgentTool> = PluginGroup.tools
}
