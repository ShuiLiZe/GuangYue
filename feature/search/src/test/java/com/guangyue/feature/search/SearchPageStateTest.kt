package com.guangyue.feature.search

import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U3：搜索页状态机 + 输入防抖（纯逻辑，不依赖 Android / 协程 / 网络）。
 *
 * 断言的目标是「界面上会显示哪一屏」，所以只喂 [SearchUiState]，不碰 ViewModel 的依赖。
 */
class SearchPageStateTest {

    // ---------------- 状态机 ----------------

    @Test
    fun `no usable source wins over everything else`() {
        // 一个音源都没装时，即便历史里有词、searched 置真，也只能显示「还没有可用的音源」。
        val state = base().copy(available = emptyList(), searched = true, history = listOf("晴天"))
        assertEquals(SearchPagePhase.NoSource, state.pagePhase())
    }

    @Test
    fun `fresh page starts idle so history and suggestions show`() {
        assertEquals(SearchPagePhase.Idle, base().pagePhase())
    }

    @Test
    fun `loading beats results so the spinner is never hidden by stale rows`() {
        // 输入第二次关键词时旧结果还在 rows 里，这时必须显示加载而不是旧列表。
        val state = base().copy(loading = true, rows = listOf(row("夜曲")), searched = true, query = "周杰伦")
        assertEquals(SearchPagePhase.Loading, state.pagePhase())
    }

    @Test
    fun `failure with nothing to show becomes the failure screen`() {
        val state = base().copy(searched = true, failure = "网易 音源里没有结果，换个关键词试试")
        assertEquals(SearchPagePhase.Failure, state.pagePhase())
    }

    @Test
    fun `failure with stale results keeps the list and lets the banner carry the error`() {
        // 有旧结果时只顶一条错误条（ResultsBody 里的 FailureBanner），不能整屏换成失败页。
        val state = base().copy(searched = true, failure = "超时", rows = listOf(row("稻香")))
        assertEquals(SearchPagePhase.Results, state.pagePhase())
    }

    @Test
    fun `searched but empty is the empty screen`() {
        val state = base().copy(searched = true, query = "asdkjhasd")
        assertEquals(SearchPagePhase.Empty, state.pagePhase())
    }

    @Test
    fun `any bucket with items counts as results`() {
        // 只有专辑 / 歌手 / 歌单有内容时也算有结果（Tab 分类搜索）。
        assertTrue(base().copy(albums = listOf(album())).pagePhase() == SearchPagePhase.Results)
        assertTrue(base().copy(artists = listOf(artist())).pagePhase() == SearchPagePhase.Results)
        assertTrue(base().copy(sheets = listOf(album())).pagePhase() == SearchPagePhase.Results)
    }

    // ---------------- 输入防抖 ----------------

    @Test
    fun `typing a new keyword schedules one search`() {
        assertTrue(shouldSearchOnInput(base().copy(query = "周杰"), "周杰伦"))
    }

    @Test
    fun `whitespace only changes do not re-search`() {
        // 中文输入法上屏常带尾随空格；不该因此重发请求。
        assertFalse(shouldSearchOnInput(base().copy(query = "周杰伦"), "周杰伦 "))
        assertFalse(shouldSearchOnInput(base().copy(query = " 周杰伦 "), "周杰伦"))
    }

    @Test
    fun `clearing the field does not search`() {
        assertFalse(shouldSearchOnInput(base().copy(query = "周杰伦", searched = true), ""))
        assertFalse(shouldSearchOnInput(base().copy(query = "周杰伦", searched = true), "   "))
    }

    @Test
    fun `debounce fires once after the window and drops the earlier keystroke`() {
        val scheduler = RecordingScheduler()
        scheduler.schedule() // 「周」
        scheduler.schedule() // 「周杰」
        scheduler.schedule() // 「周杰伦」
        assertTrue(scheduler.pending)

        // 400ms 到了：只有最后一次还会触发。
        scheduler.elapse(SEARCH_DEBOUNCE_MS)
        assertEquals(1, scheduler.fired)
        assertFalse(scheduler.pending)
    }

    @Test
    fun `cancel drops a pending search`() {
        // 换音源 / 清空输入时会 cancel，不能再偷偷发一次旧关键词。
        val scheduler = RecordingScheduler()
        scheduler.schedule()
        scheduler.cancel()
        scheduler.elapse(SEARCH_DEBOUNCE_MS)
        assertEquals(0, scheduler.fired)
        assertFalse(scheduler.pending)
    }

    @Test
    fun `debounce window is 400ms`() {
        assertEquals(400L, SEARCH_DEBOUNCE_MS)
    }

    // ---------------- 辅助 ----------------

    private fun base(): SearchUiState = SearchUiState(
        available = listOf(plugin()),
        sourceId = "netease-1",
        availableTypes = listOf(SearchTab.TRACKS),
    )

    private fun plugin(): PluginMeta = PluginMeta(
        id = "netease-1",
        platform = "netease",
        name = "网易云音乐",
        version = "1.0.0",
        supportedSearchType = listOf(SearchType.MUSIC),
        enabled = true,
        health = PluginHealth.UNKNOWN,
        sortOrder = 0,
    )

    private fun item(title: String): MusicItem = MusicItem(
        platform = "netease",
        id = title,
        title = title,
        artist = "周杰伦",
    )

    private fun row(title: String): SearchRow = SearchRow(
        pluginId = "netease-1",
        pluginName = "网易云音乐",
        platformLabel = "网易",
        item = item(title),
    )

    private fun album(): AlbumItem = AlbumItem(
        platform = "netease",
        id = "album-1",
        title = "叶惠美",
        artist = "周杰伦",
        artwork = null,
    )

    private fun artist(): ArtistItem = ArtistItem(
        platform = "netease",
        id = "artist-1",
        name = "周杰伦",
    )

    /** 记账式调度器：不真的等 400ms，手动 elapse。 */
    private class RecordingScheduler : DebounceScheduler(SEARCH_DEBOUNCE_MS) {
        private val queue = mutableListOf<Pair<Long, () -> Unit>>()
        var fired: Int = 0
            private set

        override fun enqueue(delayMs: Long, block: () -> Unit) {
            queue += delayMs to block
        }

        override fun onCancel() {
            queue.clear()
        }

        override fun onFire() {
            fired++
        }

        fun elapse(ms: Long) {
            val due = queue.filter { it.first <= ms }
            queue.clear()
            due.forEach { it.second() }
        }
    }
}

