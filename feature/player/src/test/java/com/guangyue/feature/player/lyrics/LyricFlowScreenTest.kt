package com.guangyue.feature.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U5 歌词页的纯函数部分：三态判定、行距离档、点行跳转落点、文案拼装。
 *
 * 这些是 `LyricFlowScreen` 里唯一能在 JVM 上钉住的行为（Composable 本身由
 * `app` 模块的 `U5LyricsScreenshotTest` 负责出图），所以判定规则全部抽到外面。
 */
class LyricFlowScreenTest {

    // ---------------- 三态 ----------------

    @Test
    fun `loading wins over everything`() {
        assertEquals(
            LyricEmptyKind.Loading,
            lyricEmptyKind(lineCount = 0, loading = true, failed = false),
        )
    }

    @Test
    fun `failure shows failed state`() {
        assertEquals(
            LyricEmptyKind.Failed,
            lyricEmptyKind(lineCount = 0, loading = false, failed = true),
        )
    }

    @Test
    fun `no lines means no lyrics`() {
        assertEquals(
            LyricEmptyKind.NoLyrics,
            lyricEmptyKind(lineCount = 0, loading = false, failed = false),
        )
    }

    @Test
    fun `lines hide the empty state`() {
        assertNull(lyricEmptyKind(lineCount = 3, loading = false, failed = false))
    }

    @Test
    fun `lines with a stale failure still render lines`() {
        assertNull(lyricEmptyKind(lineCount = 3, loading = false, failed = true))
    }

    // ---------------- 纯音乐 ----------------

    @Test
    fun `pure music only on explicit reason`() {
        assertTrue(lyricIsPureMusic("纯音乐，无需匹配歌词"))
        assertTrue(lyricIsPureMusic("该曲为纯音乐"))
        assertTrue(!lyricIsPureMusic("暂无歌词"))
        assertTrue(!lyricIsPureMusic(null))
        assertTrue(!lyricIsPureMusic(""))
    }

    // ---------------- 失败副句 ----------------

    @Test
    fun `failure text keeps the datasource reason`() {
        assertEquals("歌词获取失败：超时", failureText("歌词获取失败：超时", "稍后再试"))
    }

    @Test
    fun `failure text falls back when blank`() {
        assertEquals("稍后再试", failureText(null, "稍后再试"))
        assertEquals("稍后再试", failureText("   ", "稍后再试"))
    }

    // ---------------- 行距离 ----------------

    @Test
    fun `current index is active with zero distance`() {
        val visual = lyricRowVisual(index = 4, currentIndex = 4)
        assertEquals(0, visual.distance)
        assertTrue(visual.active)
    }

    @Test
    fun `previous lines keep a negative offset`() {
        val visual = lyricRowVisual(index = 1, currentIndex = 4)
        assertEquals(-3, visual.offset)
        assertEquals(3, visual.distance)
        assertTrue(!visual.active)
    }

    @Test
    fun `before first line every row is equally far`() {
        val visual = lyricRowVisual(index = 0, currentIndex = -1)
        assertNull(visual.offset)
        assertEquals(LyricRowVisual.UNKNOWN, visual.distance)
        assertTrue(!visual.active)
    }

    // ---------------- 点行跳转 ----------------

    @Test
    fun `tap on a real line seeks to it`() {
        val blanks = List(5) { false }
        assertEquals(3, lyricSeekTarget(blanks, 3))
    }

    @Test
    fun `tap on a blank line seeks back to the previous real line`() {
        val blanks = listOf(false, false, true, true, false)
        assertEquals(1, lyricSeekTarget(blanks, 3))
    }

    @Test
    fun `tap on a leading blank line stays there`() {
        val blanks = listOf(true, true, false)
        assertEquals(0, lyricSeekTarget(blanks, 1))
    }

    @Test
    fun `tap outside the lyric returns nothing`() {
        val blanks = listOf(false, false)
        assertNull(lyricSeekTarget(blanks, 2))
        assertNull(lyricSeekTarget(blanks, -1))
    }

    // ---------------- 顶栏副标题 ----------------

    @Test
    fun `source line joins artist and plugin`() {
        assertEquals("周杰伦 · 网易云音乐", lyricSourceLine(artist = "周杰伦", source = "网易云音乐"))
    }

    @Test
    fun `source line tolerates a missing half`() {
        assertEquals("周杰伦", lyricSourceLine(artist = "周杰伦", source = null))
        assertEquals("网易云音乐", lyricSourceLine(artist = null, source = "网易云音乐"))
        assertEquals("", lyricSourceLine(artist = "  ", source = null))
    }
}
