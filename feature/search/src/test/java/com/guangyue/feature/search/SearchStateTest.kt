package com.guangyue.feature.search

import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索页状态层（0.0.7）：**只用用户选中的一个音源**，Tab 只显示该音源支持的分类。
 *
 * 这里是「启用了但搜不到」在 UI 侧的防线：
 *  - 0.0.6 之前 `SearchUiState.platforms` 为空就代表「全部」，点击后 6 个插件并发打出去，
 *    谁超时谁被限流都说不清，用户看到的是空页面；
 *  - 0.0.7 起 `sourceId` 只有一个，永远只有一路请求，失败也有 [SearchUiState.failure] 整句提示。
 *
 * 这些断言不依赖 Android / Hilt / 网络，纯状态映射。
 */
class SearchStateTest {

    private fun plugin(
        id: String,
        name: String,
        platform: String = id,
        types: List<String> = listOf(SearchType.MUSIC),
        enabled: Boolean = true,
        health: String = PluginHealth.UNKNOWN,
        sortOrder: Int = 0,
    ) = PluginMeta(
        id = id,
        platform = platform,
        name = name,
        version = "1.0.0",
        supportedSearchType = types,
        enabled = enabled,
        health = health,
        sortOrder = sortOrder,
    )

    private fun item(id: String, title: String = "歌") = MusicItem(
        platform = "demo",
        id = id,
        title = title,
        artist = "歌手",
    )

    @Test
    fun `state without loaded source has no label`() {
        val state = SearchUiState()
        assertEquals("", state.sourceId)
        assertEquals("音源", state.sourceLabel)
        assertEquals(null, state.selected)
    }

    @Test
    fun `selected source drives the label`() {
        val netease = plugin("netease-1", "网易云音乐", "netease")
        val state = SearchUiState(available = listOf(netease), sourceId = netease.id)
        assertEquals(netease, state.selected)
        assertEquals("网易", state.sourceLabel)
    }

    @Test
    fun `available types follow the chosen plugin`() {
        val state = SearchUiState(
            available = listOf(plugin("a", "甲", types = listOf("music", "album"))),
            sourceId = "a",
            availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS),
        )
        assertEquals(listOf(SearchTab.TRACKS, SearchTab.ALBUMS), state.availableTypes)
        assertFalse("没声明 sheet 的音源不显示歌单 Tab", SearchTab.SHEETS in state.availableTypes)
    }

    @Test
    fun `tab counts are the sizes of the four buckets`() {
        val state = SearchUiState(
            rows = listOf(SearchRow("a", "甲", "甲", item("1")), SearchRow("a", "甲", "甲", item("2"))),
            albums = emptyList(),
            artists = emptyList(),
            sheets = emptyList(),
        )
        assertEquals(TabCounts(tracks = 2, albums = 0, sheets = 0, artists = 0), state.tabCounts)
        assertEquals(2, state.tabCounts.of(SearchTab.TRACKS))
        assertEquals(0, state.tabCounts.of(SearchTab.SHEETS))
    }

    @Test
    fun `failure text is surfaced as-is for the banner`() {
        val text = "网易 音源搜索失败：连不上源站（网络或域名失效），换个音源试试"
        val state = SearchUiState(failure = text, searched = true)
        assertEquals(text, state.failure)
        assertTrue("失败也代表搜过一次，不能继续显示空态引导", state.searched)
    }

    @Test
    fun `source label falls back to platform when name is empty`() {
        val anonymous = plugin("x-1", name = "", platform = "kugou")
        val state = SearchUiState(available = listOf(anonymous), sourceId = anonymous.id)
        assertEquals("酷狗", state.sourceLabel)
    }

    @Test
    fun `healthy plugins are the ones the one-click enable keeps`() {
        val ok = plugin("ok", "能用的", health = PluginHealth.OK)
        val noResult = plugin("nr", "搜不到的", health = PluginHealth.NO_RESULT)
        val unknown = plugin("u", "没体检的")
        assertTrue(ok.isHealthy)
        assertFalse(noResult.isHealthy)
        assertFalse(unknown.isHealthy)
        assertEquals("可用", ok.healthLabel)
        assertEquals("搜索无结果", noResult.healthLabel)
        assertEquals("未体检", unknown.healthLabel)
    }

    @Test
    fun `search types are normalised and default to music`() {
        assertEquals(listOf(SearchType.MUSIC), plugin("a", "甲").searchTypes)
        assertEquals(
            listOf(SearchType.MUSIC, SearchType.ALBUM, SearchType.ARTIST),
            plugin("b", "乙", types = listOf(" MUSIC ", "Album", "artist", "")).searchTypes,
        )
        assertTrue(plugin("c", "丙", types = listOf("sheet")).supportsSheet)
        assertFalse(plugin("c", "丙", types = listOf("sheet")).supportsMusic)
    }
}