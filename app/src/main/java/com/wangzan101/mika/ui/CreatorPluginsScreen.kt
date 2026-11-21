package com.wangzan101.mika.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private data class CreatorPluginItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val indexFile: File,
    val dir: File,
)
object PluginLaunchBus {
    var pendingOpenId by mutableStateOf<String?>(null)
    var activeScreenPluginId by mutableStateOf<String?>(null)
    var pendingClose by mutableStateOf(false)
}

private object PluginWebPool {
    val active = mutableStateMapOf<String, WebView>()

    var fileChooserTrigger: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(context: Context, item: CreatorPluginItem): WebView {
        val existing = active[item.id]
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            existing.onResume()
existing.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
existing.setBackgroundColor(android.graphics.Color.parseColor("#050811"))
existing.settings.useWideViewPort = true
existing.settings.loadWithOverviewMode = true
existing.settings.databaseEnabled = true
existing.settings.mediaPlaybackRequiresUserGesture = false
existing.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            return existing
        }
        val wv = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
setBackgroundColor(android.graphics.Color.parseColor("#050811"))
settings.useWideViewPort = true
settings.loadWithOverviewMode = true
settings.databaseEnabled = true
settings.mediaPlaybackRequiresUserGesture = false
settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
override fun onShowFileChooser(
webView: WebView?,
filePathCallback: ValueCallback<Array<Uri>>?,
fileChooserParams: FileChooserParams?
): Boolean {
if (filePathCallback == null) return false
val trigger = fileChooserTrigger
if (trigger != null) {
trigger(filePathCallback)
return true
}
filePathCallback.onReceiveValue(null)
return false
}
override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
Toast.makeText(context.applicationContext, message ?: "", Toast.LENGTH_SHORT).show()
result?.confirm()
return true
}
override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
result?.confirm()
return true
}
override fun onPermissionRequest(request: PermissionRequest?) {
request?.grant(request.resources)
}
            }
            loadUrl("file://${item.indexFile.absolutePath}")
        }
        active[item.id] = wv
        return wv
    }

    fun stop(id: String) {
        val wv = active.remove(id) ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.loadUrl("about:blank")
        wv.destroy()
    }
}

@Composable
private fun BlinkingGreenDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "plugin-active-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot-alpha"
    )
    Box(
        modifier = modifier
            .size(9.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color(0xFF10B981))
    )
}

private fun ensureStarterPlugins(baseDir: File, force: Boolean = false) {
    if (!baseDir.exists()) baseDir.mkdirs()
    val regFile = File(baseDir, "registry.json")
    if (regFile.exists() && !force) return
    val starters = listOf(
        Triple("chronometre", "Chronomètre & Focus" to "⏱️", """<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box;font-family:system-ui,sans-serif}body{margin:0;background:#0f172a;color:#f8fafc;padding:20px;display:flex;justify-content:center}.c{width:100%;max-width:420px;background:#1e293b;border-radius:18px;padding:22px;text-align:center}.t{font-size:54px;font-weight:800;margin:16px 0;color:#38bdf8}.r{display:flex;gap:10px;justify-content:center}button{border:none;border-radius:12px;padding:12px 16px;font-weight:600;font-size:14px}.s{background:#10b981;color:#fff}.p{background:#f59e0b;color:#0f172a}.x{background:#334155;color:#fff}.h{margin-top:16px;text-align:left;font-size:13px}.i{background:#0f172a;padding:8px 12px;border-radius:10px;margin-top:6px;display:flex;justify-content:space-between}</style></head><body><div class="c"><h2>⏱️ Chronomètre & Focus</h2><div class="t" id="d">00:00</div><div class="r"><button class="s" id="b" onclick="tog()">Démarrer</button><button class="p" onclick="sav()">Sauver</button><button class="x" onclick="rst()">Reset</button></div><div class="h" id="h"></div></div><script>let s=0,t=null;const f=x=>String(Math.floor(x/60)).padStart(2,'0')+':'+String(x%60).padStart(2,'0');function ren(){d.textContent=f(s);const l=JSON.parse(localStorage.getItem('mc_h')||'[]');h.innerHTML=l.map(x=>'<div class="i"><span>'+x.a+'</span><strong>'+x.d+'</strong></div>').join('')}function tog(){if(t){clearInterval(t);t=null;b.textContent='Reprendre'}else{t=setInterval(()=>{s++;d.textContent=f(s)},1000);b.textContent='Pause'}}function rst(){clearInterval(t);t=null;s=0;b.textContent='Démarrer';ren()}function sav(){if(!s)return;const l=JSON.parse(localStorage.getItem('mc_h')||'[]');l.unshift({d:f(s),a:new Date().toLocaleTimeString('fr-FR',{hour:'2-digit',minute:'2-digit'})});localStorage.setItem('mc_h',JSON.stringify(l.slice(0,10)));rst()}ren();</script></body></html>"""),
        Triple("kanban", "Mon Espace Kanban" to "📋", """<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box;font-family:system-ui,sans-serif}body{margin:0;background:#0f172a;color:#f8fafc;padding:16px}.top{display:flex;gap:8px;margin-bottom:14px}input{flex:1;background:#1e293b;border:1px solid #334155;color:#fff;padding:10px;border-radius:10px}button{background:#6366f1;color:#fff;border:none;border-radius:10px;padding:10px 14px;font-weight:600}.cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px}.col{background:#1e293b;border-radius:14px;padding:12px}.task{background:#0f172a;border-radius:10px;padding:10px;margin-top:8px;font-size:13px}.act{display:flex;gap:6px;margin-top:6px}.act button{padding:4px 8px;font-size:11px;background:#334155}</style></head><body><div class="top"><input id="i" placeholder="Nouvelle tâche..."><button onclick="add()">Ajouter</button></div><div class="cols" id="c"></div><script>const ST=[{k:'todo',l:'💡 Idées'},{k:'doing',l:'⚡ En cours'},{k:'done',l:'✅ Terminé'}];const ld=()=>JSON.parse(localStorage.getItem('mk_k')||'[]');const sv=d=>{localStorage.setItem('mk_k',JSON.stringify(d));ren()};function add(){const v=i.value.trim();if(!v)return;const d=ld();d.unshift({id:Date.now(),t:v,s:'todo'});i.value='';sv(d)}function mv(id,s){sv(ld().map(x=>x.id===id?{...x,s}:x))}function dl(id){sv(ld().filter(x=>x.id!==id))}function ren(){const d=ld();c.innerHTML=ST.map(st=>'<div class="col"><strong>'+st.l+'</strong>'+d.filter(x=>x.s===st.k).map(x=>'<div class="task"><div>'+x.t.replace(/</g,'&lt;')+'</div><div class="act">'+ST.filter(o=>o.k!==st.k).map(o=>'<button onclick="mv('+x.id+',\''+o.k+'\')">'+o.l.split(' ')[0]+'</button>').join('')+'<button onclick="dl('+x.id+')" style="background:#7f1d1d">✕</button></div></div>').join('')+'</div>').join('')}ren();</script></body></html>"""),
        Triple("notes-studio", "Bloc-Notes & Prompts" to "📝", """<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box;font-family:system-ui,sans-serif}body{margin:0;background:#0f172a;color:#f8fafc;padding:16px}textarea{width:100%;min-height:260px;background:#1e293b;border:1px solid #334155;color:#f8fafc;border-radius:14px;padding:14px;font-size:14px}.info{font-size:12px;color:#94a3b8;margin-top:8px}</style></head><body><h2>📝 Bloc-Notes</h2><textarea id="p" placeholder="Vos notes..."></textarea><div class="info" id="st">Sauvegardé localement</div><script>p.value=localStorage.getItem('mk_n')||'';p.oninput=()=>{localStorage.setItem('mk_n',p.value);st.textContent='Sauvegardé à '+new Date().toLocaleTimeString('fr-FR')};</script></body></html>""")
    )
    val arr = JSONArray()
    for ((id, meta, html) in starters) {
        val dir = File(baseDir, id)
        dir.mkdirs()
        File(dir, "index.html").writeText(html)
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", meta.first)
        obj.put("description", "Plugin interactif intégré")
        obj.put("icon", meta.second)
        arr.put(obj)
    }
    regFile.writeText(arr.toString(2))
}

private fun deletePluginEverywhere(context: Context, id: String) {
    PluginWebPool.stop(id)
    val dirs = mutableListOf(File(context.filesDir, "creator_plugins"))
    File(context.filesDir, "sandbox/work").listFiles()?.forEach { conv ->
        val pDir = File(conv, "plugins")
        if (pDir.isDirectory) dirs.add(pDir)
    }
    for (base in dirs) {
        File(base, id).deleteRecursively()
        val regFile = File(base, "registry.json")
        if (regFile.isFile) {
            runCatching {
                val arr = JSONArray(regFile.readText())
                val next = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("id") != id) next.put(o)
                }
                regFile.writeText(next.toString(2))
            }
        }
    }
}

private fun savePlugin(context: Context, existing: CreatorPluginItem?, title: String, icon: String, description: String, html: String, forcedId: String? = null) {
    val baseDir = existing?.dir?.parentFile ?: File(context.filesDir, "creator_plugins")
    if (!baseDir.exists()) baseDir.mkdirs()
    val slug = forcedId?.takeIf { it.isNotBlank() } ?: existing?.id ?: title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "plugin-${System.currentTimeMillis()}" }
    val pDir = existing?.dir ?: File(baseDir, slug)
    pDir.mkdirs()
    File(pDir, "index.html").writeText(html)
    PluginWebPool.stop(slug)

    val regFile = File(baseDir, "registry.json")
    val arr = if (regFile.isFile) runCatching { JSONArray(regFile.readText()) }.getOrElse { JSONArray() } else JSONArray()
    val newArr = JSONArray()
    val obj = JSONObject().apply {
        put("id", slug)
        put("title", title.trim())
        put("description", description.trim().ifEmpty { "Plugin personnalisé" })
        put("icon", icon.trim().ifEmpty { "🧩" })
    }
    newArr.put(obj)
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        if (item.optString("id") != slug) newArr.put(item)
    }
    regFile.writeText(newArr.toString(2))
}

private fun scanPlugins(context: Context): List<CreatorPluginItem> {
    val globalDir = File(context.filesDir, "creator_plugins")
    ensureStarterPlugins(globalDir)
    val roots = mutableListOf(globalDir)
    val workRoot = File(context.filesDir, "sandbox/work")
    workRoot.listFiles()?.forEach { conv ->
        val pDir = File(conv, "plugins")
        if (pDir.isDirectory) roots.add(pDir)
    }
    val byId = LinkedHashMap<String, CreatorPluginItem>()
    for (root in roots) {
        val metaMap = mutableMapOf<String, Triple<String, String, String>>()
        val reg = File(root, "registry.json")
        if (reg.isFile) {
            runCatching {
                val arr = JSONArray(reg.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    metaMap[o.optString("id")] = Triple(
                        o.optString("title", o.optString("id")),
                        o.optString("description", "Plugin créé par Mika"),
                        o.optString("icon", "🧩")
                    )
                }
            }
        }
        root.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            val idx = File(sub, "index.html")
            if (idx.isFile) {
                val id = sub.name
                val meta = metaMap[id] ?: Triple(id, "Plugin interactif", "🧩")
                byId[id] = CreatorPluginItem(id, meta.first, meta.second, meta.third, idx, sub)
            }
        }
    }
    return byId.values.toList()
}

fun syncPluginsAndProcessAiCommands(context: Context): Boolean {
    var changed = false
    val workRoot = File(context.filesDir, "sandbox/work")
    val listBefore = scanPlugins(context)

    workRoot.listFiles()?.forEach { conv ->
        val pDir = File(conv, "plugins")
        val cmdFile = File(pDir, ".cmd.json")
        if (cmdFile.isFile) {
            runCatching {
                val cmd = JSONObject(cmdFile.readText())
                val action = cmd.optString("action")
                val id = cmd.optString("id")
                when (action) {
                    "open" -> {
                        val query = cmd.optString("id", cmd.optString("title", "")).trim()
                        if (query.isNotBlank()) {
                            val found = scanPlugins(context).firstOrNull {
                                it.id.equals(query, ignoreCase = true) ||
                                it.title.equals(query, ignoreCase = true) ||
                                it.title.contains(query, ignoreCase = true)
                            }
                            if (found != null) {
                                PluginLaunchBus.pendingOpenId = found.id
                                changed = true
                            }
                        }
                    }
                    "start" -> {
                        val target = listBefore.find { it.id == id }
                        if (target != null) {
                            PluginWebPool.getOrCreate(context, target)
                            changed = true
                        }
                    }
                    "stop" -> {
                        if (id.isNotBlank()) {
                            PluginWebPool.stop(id)
                            changed = true
                        }
                    }
                    "delete" -> {
                        if (id.isNotBlank()) {
                            deletePluginEverywhere(context, id)
                            changed = true
                        }
                    }
                    "save" -> {
                        val title = cmd.optString("title", id)
                        val icon = cmd.optString("icon", "🧩")
                        val desc = cmd.optString("description", "Plugin personnalisé")
                        val html = cmd.optString("html", "")
                        if (html.isNotBlank()) {
                            val existing = listBefore.find { it.id == id }
                            savePlugin(context, existing, title, icon, desc, html, forcedId = id)
                            changed = true
                        }
                    }
                "start_bg" -> {
                    val query = cmd.optString("id", cmd.optString("title", "")).trim()
                    if (query.isNotBlank()) {
                        val found = scanPlugins(context).firstOrNull {
                            it.id.equals(query, ignoreCase = true) ||
                            it.title.equals(query, ignoreCase = true) ||
                            it.title.contains(query, ignoreCase = true)
                        }
                        if (found != null) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                PluginWebPool.getOrCreate(context, found)
                            }
                            changed = true
                        }
                    }
                }
                "close" -> {
                    PluginLaunchBus.pendingClose = true
                    changed = true
                }
                "eval" -> {
                    val js = cmd.optString("js", "")
                    val query = cmd.optString("id", cmd.optString("title", "")).trim()
                    if (query.isNotBlank() && js.isNotBlank()) {
                        val found = scanPlugins(context).firstOrNull {
                            it.id.equals(query, ignoreCase = true) ||
                            it.title.equals(query, ignoreCase = true) ||
                            it.title.contains(query, ignoreCase = true)
                        }
                        if (found != null) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                val wv = PluginWebPool.getOrCreate(context, found)
                                wv.evaluateJavascript(js) { res ->
                                    val payload = JSONObject().apply {
                                        put("id", found.id)
                                        put("title", found.title)
                                        put("ok", true)
                                        put("result", res ?: "null")
                                        put("timestamp", System.currentTimeMillis())
                                    }.toString()
                                    workRoot.listFiles()?.forEach { convDir ->
                                        if (convDir.isDirectory) {
                                            val outDir = File(convDir, "plugins")
                                            if (!outDir.exists()) outDir.mkdirs()
                                            runCatching {
                                                File(outDir, ".eval_result.json").writeText(payload)
                                            }
                                        }
                                    }
                                }
                            }
                            changed = true
                        }
                    }
                }
                }
            }
            cmdFile.delete()
        }
    }

    val listAfter = scanPlugins(context)
    val allArr = JSONArray()
    for (p in listAfter) {
        allArr.put(JSONObject().apply {
            put("id", p.id)
            put("title", p.title)
            put("description", p.description)
            put("icon", p.icon)
            put("running", PluginWebPool.active.containsKey(p.id))
            put("is_open", PluginLaunchBus.activeScreenPluginId == p.id)
            put("size_kb", (p.indexFile.length() / 1024L).toInt())
        })
    }
    workRoot.listFiles()?.forEach { conv ->
        if (conv.isDirectory) {
            val pDir = File(conv, "plugins")
            if (!pDir.exists()) pDir.mkdirs()
            runCatching {
                File(pDir, "all_plugins.json").writeText(allArr.toString(2))
                for (p in listAfter) {
                    val mirrorDir = File(pDir, p.id)
                    if (!mirrorDir.exists()) mirrorDir.mkdirs()
                    val mirrorHtml = File(mirrorDir, "index.html")
                    if (!mirrorHtml.exists()) {
                        p.indexFile.copyTo(mirrorHtml, overwrite = false)
                    }
                }
            }
        }
    }
    return changed
}

@Composable
fun CreatorPluginsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MikaTheme.colors
    var activePlugin by remember { mutableStateOf<CreatorPluginItem?>(null) }
    LaunchedEffect(activePlugin) { PluginLaunchBus.activeScreenPluginId = activePlugin?.id }
    LaunchedEffect(PluginLaunchBus.pendingClose) { if (PluginLaunchBus.pendingClose) { activePlugin = null; PluginLaunchBus.pendingClose = false; onBack() } }
    var pendingDelete by remember { mutableStateOf<CreatorPluginItem?>(null) }
    var editingPlugin by remember { mutableStateOf<CreatorPluginItem?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var menuOpenId by remember { mutableStateOf<String?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("🧩") }
    var newDesc by remember { mutableStateOf("") }
    var newHtml by remember { mutableStateOf("") }
var importedFileName by remember { mutableStateOf<String?>(null) }
val htmlFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
if (uri != null) {
runCatching {
val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
if (content.isNotEmpty()) {
newHtml = content
val titleMatch = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(content)
if (newTitle.isBlank() && titleMatch != null) {
newTitle = titleMatch.groupValues[1].trim()
}
var displayName = uri.lastPathSegment ?: "plugin.html"
context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
if (cursor.moveToFirst()) {
val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
if (idx >= 0) displayName = cursor.getString(idx) ?: displayName
}
}
importedFileName = displayName
showEditorDialog = true
}
}
}
}
var pendingWebFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
val webInnerFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
pendingWebFileCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
pendingWebFileCallback = null
}
DisposableEffect(Unit) {
PluginWebPool.fileChooserTrigger = { callback ->
pendingWebFileCallback?.onReceiveValue(null)
pendingWebFileCallback = callback
webInnerFilePicker.launch(arrayOf("*/*"))
}
onDispose {
PluginWebPool.fileChooserTrigger = null
}
}
    var refreshTick by remember { mutableIntStateOf(0) }
    val plugins = remember(refreshTick) { scanPlugins(context) }
    LaunchedEffect(PluginLaunchBus.pendingOpenId, plugins) {
        val targetId = PluginLaunchBus.pendingOpenId
        if (targetId != null) {
            val match = plugins.firstOrNull { it.id == targetId }
            if (match != null) {
                activePlugin = match
                PluginLaunchBus.pendingOpenId = null
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (syncPluginsAndProcessAiCommands(context)) {
                refreshTick++
            }
            delay(1500)
        }
    }

    val defaultTemplate = "<!DOCTYPE html>\n<html lang=\"fr\">\n<body style=\"background:#0f172a;color:#fff;font-family:sans-serif;padding:20px\">\n  <h2>Mon Plugin</h2>\n  <p>Contenu interactif...</p>\n</body>\n</html>"

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer ce plugin ?") },
            text = { Text("Voulez-vous vraiment supprimer « ${target.icon} ${target.title} » ? Cette action est définitive.") },
            confirmButton = {
                Button(onClick = {
                    deletePluginEverywhere(context, target.id)
                    pendingDelete = null
                    refreshTick++
                }) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showEditorDialog) {
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            title = { Text(if (editingPlugin != null) "Modifier le plugin" else "Créer un plugin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Nom du plugin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIcon,
                        onValueChange = { newIcon = it },
                        label = { Text("Icône (emoji)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
OutlinedButton(
onClick = { htmlFilePicker.launch(arrayOf("text/html", "*/*")) },
modifier = Modifier.fillMaxWidth()
) {
Text(if (importedFileName != null) "Importer .html ($importedFileName)" else "Importer .html")
}
if (newHtml.length > 12_000) {
Text(
text = "Fichier HTML chargé : ${newHtml.length / 1024} Ko · prêt à enregistrer sans coupure",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.primary
)
} else  {
OutlinedTextField(
                        value = newHtml,
                        onValueChange = { newHtml = it },
                        label = { Text("Code HTML / CSS / JS") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank() && newHtml.isNotBlank(),
                    onClick = {
                        savePlugin(context, editingPlugin, newTitle, newIcon, newDesc, newHtml)
                        showEditorDialog = false
                        editingPlugin = null
                        refreshTick++
                    }
                ) {
                    Text(if (editingPlugin != null) "Enregistrer" else "Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditorDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (activePlugin != null) activePlugin = null else onBack() }) {
                    Icon(MikaIcons.Back, contentDescription = "Retour", tint = c.ink)
                }
                Text(
                    text = activePlugin?.let { "${it.icon} ${it.title}" } ?: "✨ Studio Plugins",
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (activePlugin == null && PluginWebPool.active.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    BlinkingGreenDot()
                }
            }
            if (activePlugin != null) {
                val cur = activePlugin!!
                OutlinedButton(onClick = {
                    PluginWebPool.stop(cur.id)
                    activePlugin = null
                }) {
                    Text("Arrêter", fontSize = 12.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    
OutlinedButton(onClick = {
editingPlugin = null
newTitle = ""
newIcon = "⚡"
newDesc = ""
newHtml = ""
importedFileName = null
htmlFilePicker.launch(arrayOf("text/html", "*/*"))
}) {
Text("Importer .html", fontSize = 12.sp)
}
Spacer(Modifier.width(8.dp))
OutlinedButton(onClick = {
                        editingPlugin = null
                        newTitle = ""
                        newIcon = "🧩"
                        newDesc = ""
                        newHtml = defaultTemplate
                        showEditorDialog = true
                    }) {
                        Text("+ Nouveau", fontSize = 12.sp)
                    }
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(MikaIcons.Refresh, contentDescription = "Rafraîchir", tint = c.ink)
                    }
                }
            }
        }

        val current = activePlugin
        if (current != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PluginWebPool.getOrCreate(ctx, current) },
                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Appuie sur un plugin pour l'ouvrir (il reste actif en arrière-plan), ou sur ⋮ pour le modifier, l'arrêter ou le supprimer.",
                        color = c.ink2,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (plugins.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Aucun plugin installé.", color = c.ink2, fontSize = 14.sp)
                            Button(onClick = {
                                editingPlugin = null
                                newTitle = ""
                                newIcon = "🧩"
                                newDesc = ""
                                newHtml = defaultTemplate
                                showEditorDialog = true
                            }) {
                                Text("+ Créer un plugin manuellement")
                            }
                            OutlinedButton(onClick = {
                                ensureStarterPlugins(File(context.filesDir, "creator_plugins"), force = true)
                                refreshTick++
                            }) {
                                Text("Restaurer les plugins de base")
                            }
                        }
                    }
                }
                items(plugins, key = { it.id }) { item ->
                    val isRunning = PluginWebPool.active.containsKey(item.id)
                    Surface(
                        color = c.bg,
                        border = BorderStroke(1.dp, if (isRunning) Color(0xFF10B981) else c.hair),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { activePlugin = item }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.icon} ${item.title}",
                                        color = c.ink,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isRunning) {
                                        Spacer(Modifier.width(8.dp))
                                        BlinkingGreenDot()
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    color = c.ink2,
                                    fontSize = 12.sp
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpenId = item.id }) {
                                    Icon(MikaIcons.More, contentDescription = "Options", tint = c.ink2)
                                }
                                DropdownMenu(
                                    expanded = menuOpenId == item.id,
                                    onDismissRequest = { menuOpenId = null }
                                ) {
                                    if (isRunning) {
                                        DropdownMenuItem(
                                            text = { Text("Arrêter") },
                                            onClick = {
                                                menuOpenId = null
                                                PluginWebPool.stop(item.id)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Modifier") },
                                        onClick = {
                                            menuOpenId = null
                                            editingPlugin = item
                                            newTitle = item.title
                                            newIcon = item.icon
                                            newDesc = item.description
                                            newHtml = runCatching { item.indexFile.readText() }.getOrElse { defaultTemplate }
                                            showEditorDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Supprimer") },
                                        onClick = {
                                            menuOpenId = null
                                            pendingDelete = item
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
