package com.wangzan101.mika.cap.web

import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.SystemPrompt
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.fetch.WebFetch
import com.wangzan101.mika.core.fetch.WebFetchResult
import com.wangzan101.mika.core.fetch.WebPage
import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.core.model.OpenAiCompatModel
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.record.RunSink
import com.wangzan101.mika.core.search.WebSearch
import com.wangzan101.mika.core.search.WebSearchHit
import com.wangzan101.mika.core.search.WebSearchResult
import com.wangzan101.mika.settings.ProviderStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Opt in with -e liveWebResearch 1. Search and fetch use fixtures; the model and Android WebView are real. */
class WebResearchRoutingDeviceTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val url = "https://bulletin.example.org/assets/web-fixtures/conference.html"

    @Test fun suppliedUrl() = checkRoute("请读一下 $url，告诉我公告中公布的会场编号。", "CEDAR-209", listOf("web_fetch"))

    @Test fun searchThenFetch() = checkRoute("请搜索青岚发布会公告，告诉我公告正文里公布的会场编号。", "CEDAR-209", listOf("web_search", "web_fetch"))

    @Test fun fetchFailureUsesBrowser() = checkRoute("请读一下 $url，告诉我公告中公布的会场编号。", "CEDAR-209", listOf("web_fetch"), failFetch = true)

    private fun checkRoute(goal: String, answer: String, expected: List<String>, failFetch: Boolean = false) {
        assumeTrue(args.getString("liveWebResearch") == "1")
        val ctx = instr.targetContext
        // An adb-reversed test server can supply the model without changing the user's provider settings.
        val model = args.getString("webResearchModelPort")?.toInt()?.let { port ->
            OpenAiCompatModel("http://127.0.0.1:$port/v1", null, args.getString("webResearchModel") ?: "local",
                Capabilities(true, false, 32768, false), OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build())
        } ?: checkNotNull(ProviderStore.get(ctx).modelFor(ModelRole.Main)) { "Configure a main model or supply a test model port" }
        val search = object : WebSearch {
            override suspend fun search(query: String, count: Int) = WebSearchResult.Success(
                listOf(WebSearchHit("青岚发布会公告", url, "会场安排详见公告正文。")))
        }
        val fetch = object : WebFetch {
            override suspend fun fetch(url: String): WebFetchResult {
                require(url == this@WebResearchRoutingDeviceTest.url)
                return if (failFetch) WebFetchResult.Failure("empty_page", "Source returned no article text")
                else WebFetchResult.Success(WebPage(url, url, "青岚发布会公告", "本次发布会的会场编号是 CEDAR-209。"))
            }
        }
        lateinit var host: WebHost
        lateinit var pool: WebTabPool
        instr.runOnMainSync {
            val bounds = ctx.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            host = WebHost(ctx, bounds.width(), bounds.height(), ctx.resources.displayMetrics.densityDpi)
            val loader = WebViewAssetLoader.Builder().setDomain("bulletin.example.org").addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(instr.context)).build()
            pool = WebTabPool(ctx, host, assetLoaders = listOf(loader))
        }
        try {
            val screen = WebScreen(pool, "web-research", host, SystemClock::uptimeMillis)
            val tools = toolDispatcher(screen, web = screen, webSearch = search, webFetch = fetch)
            val calls = mutableListOf<String>()
            val sink = object : RunSink {
                override fun event(e: RunEvent) {
                    if (e is RunEvent.ToolResult) {
                        calls += e.tool
                        Log.i("WebResearchRouting", "step=${e.step} tool=${e.tool}")
                    }
                }
            }
            val end = runBlocking { AgentLoop(model, tools, SystemPrompt.identity(), maxSteps = 8, sink = sink,
                inputTimeoutMs = 2000).run(goal) }
            Log.i("WebResearchRouting", "model=${model.name} tools=$calls end=$end")
            assertTrue("$calls $end", end is TaskState.Succeeded && answer in end.answer)
            val webCalls = calls.filter { it.startsWith("web_") }
            if (failFetch) {
                assertEquals(expected, webCalls.take(1))
                assertTrue(webCalls.toString(), webCalls.getOrNull(1) in listOf("web_read_page", "web_navigate"))
            } else assertEquals(expected, webCalls)
        } finally {
            instr.runOnMainSync { pool.close(); host.close() }
        }
    }
}
