package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.SearchParser
import com.guangyue.core.plugin.SearchType
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 端到端：搜索框输入 → 插件 `search(query, page, type)` → `SearchParser` 映射成 UI 列表
 * → 选中一首 → `getMediaSource` 拿到播放地址。
 *
 * 走的是 App 生产代码的同一套路径（[PluginRuntime] 真引擎 + [SearchParser] 真映射），
 * 区别只在 HTTP 被 [PluginRuntimeHarness.offlineHttp] 掐断、插件用仓库内的 demo 源，
 * 所以能在 JVM 上确定性地跑：不联网、不依赖 Hilt、不依赖设备。
 *
 * 覆盖 0.0.7 排查清单里的前三条：
 *  1. 搜索页是不是真的调到了插件（这条用例证明插件侧链路是通的，剩下的是 UI 接线，见
 *     `feature/search` 的 SearchFlowTest）；
 *  2. async 结果有没有 await / drainJobs 到位（`promise search settles` 用例）；
 *  3. 返回字段映射 `data/isEnd`、`title/artist/album/artwork/id`（[com.guangyue.core.plugin.SearchParserTest] 细测，
 *     这里再用真插件跑一遍整链）。
 */
class SearchEndToEndTest {

    private val quickJsAvailable: Boolean by lazy {
        runCatching { com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close() }.isSuccess
    }

    private fun demoCode(): String? {
        val candidates = listOf(
            File("../docs/demo-plugin.js"),
            File("docs/demo-plugin.js"),
            File("../../docs/demo-plugin.js"),
            File("${System.getProperty("guangyue.rootDir") ?: "."}/docs/demo-plugin.js"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
    }

    @Test
    fun `search returns rows the UI can render and getMediaSource returns a url`() {
        assumeTrue("当前 JVM 没有 QuickJS natives", quickJsAvailable)
        val code = demoCode()
        assumeTrue("找不到 docs/demo-plugin.js", code != null)

        val runtime = PluginRuntimeHarness.runtimeFor(code!!, "demo")
        try {
            runtime.install()

            // ① 搜索框输入 → 插件 search(query, page, type)
            val raw = runtime.call("search", JsonPrimitive(""), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC))

            // ② 返回值 → UI 列表（生产代码用的同一个 parser）
            val parsed = SearchParser.parse(raw, "demo", SearchType.MUSIC)
            assertTrue("demo 插件应当返回若干条", parsed.items.isNotEmpty())
            val first = parsed.items.first()
            assertTrue("标题不能是空的", first.title.isNotBlank())
            assertTrue("id 不能是空的，否则点击无法回查", first.id.isNotBlank())
            assertEquals("platform 要落成插件平台，播放时靠它回找插件", "demo", first.platform)

            // ③ 点一首 → getMediaSource 拿播放地址
            val payload = first.raw ?: first.toJson()
            val source = SearchParser.mediaSource(runtime.call("getMediaSource", payload, JsonPrimitive("standard")))
            assertNotNull("点一首必须能拿到播放地址，否则就是「搜得到播不了」", source)
            assertTrue("地址不能是空的", source!!.url.startsWith("http"))
        } finally {
            runCatching { runtime.close() }
        }
    }

    @Test
    fun `promise search settles instead of timing out`() {
        assumeTrue("当前 JVM 没有 QuickJS natives", quickJsAvailable)
        // 真实插件里 B站 / 火龙DJ / 喜马拉雅等就是这种形状：search 里 await axios，
        // 返回 Promise。宿主必须把微任务与定时器抽干到 Promise 结算，不能直接判定「未结算」。
        val code = """
            module.exports = {
              platform: 'gy_async',
              version: '1.0.0',
              supportedSearchType: ['music'],
              search(query, page, type) {
                return new Promise(function (resolve) {
                  setTimeout(function () {
                    resolve({ isEnd: true, data: [{ id: 'a1', title: '异步结果', artist: '测试' }] });
                  }, 0);
                });
              },
              getMediaSource(item, quality) {
                return { url: 'https://example.com/' + item.id + '.mp3' };
              },
            };
        """.trimIndent()

        val runtime = PluginRuntimeHarness.runtimeFor(code, "gy_async")
        try {
            runtime.install()
            val parsed = SearchParser.parse(
                runtime.call("search", JsonPrimitive("x"), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC)),
                "gy_async",
                SearchType.MUSIC,
            )
            assertEquals("Promise 结算后应拿到结果，而不是空", 1, parsed.items.size)
            assertEquals("异步结果", parsed.items.first().title)

            val source = SearchParser.mediaSource(
                runtime.call("getMediaSource", parsed.items.first().raw ?: parsed.items.first().toJson(), JsonPrimitive("standard")),
            )
            assertEquals("https://example.com/a1.mp3", source?.url)
        } finally {
            runCatching { runtime.close() }
        }
    }

    @Test
    fun `media item survives json round trip through the queue`() {
        // 播放队列把 MusicItem 序列化成 rawJson 存起来，取源时再还原成 payload。
        // 字段丢了就会「搜得到、点下去没声音」。
        val item = MusicItem(
            platform = "demo",
            id = "demo-1",
            title = "晨光小段",
            artist = "周杰伦",
            album = "广乐演示曲目",
            artwork = "https://x/c.jpg",
            durationMs = 300_000,
        )
        val restored = MusicItem.from(item.toJson(), "fallback")
        assertNotNull(restored)
        assertEquals(item.id, restored!!.id)
        assertEquals(item.title, restored.title)
        assertEquals(item.artist, restored.artist)
        assertEquals(item.album, restored.album)
        assertEquals(item.artwork, restored.artwork)
        assertEquals(item.durationMs, restored.durationMs)
    }
}