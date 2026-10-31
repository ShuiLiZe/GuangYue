package com.guangyue.music.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * U7 音乐库页的纯函数单测（照 U6 `HomeWallStateTest` 的写法，不起 Compose）。
 *
 * 只测两件容易写错又影响截图判定的事：
 * 1. 四态优先级（有内容 > 载入 > 失败 > 空）；
 * 2. 「N 首」计数的替换（模板缺 `%d` 时不吞掉数字）。
 */
class LibraryStateTest {

    @Test
    fun contentWinsOverEverything() {
        // 有内容时哪怕还在载入 / 刚报过错，也照常显示内容，不闪三态面板。
        assertEquals(LibraryState.Content, libraryState(loading = true, failed = true, hasContent = true))
        assertEquals(LibraryState.Content, libraryState(loading = true, failed = false, hasContent = true))
        assertEquals(LibraryState.Content, libraryState(loading = false, failed = true, hasContent = true))
    }

    @Test
    fun loadingBeatsFailedWhenEmpty() {
        assertEquals(LibraryState.Loading, libraryState(loading = true, failed = true, hasContent = false))
        assertEquals(LibraryState.Loading, libraryState(loading = true, failed = false, hasContent = false))
    }

    @Test
    fun failedWhenEmptyAndSettled() {
        assertEquals(LibraryState.Failed, libraryState(loading = false, failed = true, hasContent = false))
    }

    @Test
    fun emptyWhenNothingHappened() {
        assertEquals(LibraryState.Empty, libraryState(loading = false, failed = false, hasContent = false))
    }

    @Test
    fun countTextFillsNumber() {
        assertEquals("12 首", countText("%d 首", 12))
        assertEquals("0 首", countText("%d 首", 0))
    }

    @Test
    fun countTextKeepsTemplateWhenNoPlaceholder() {
        // 模板里没有 %d 时原样返回，不会把数字丢掉换成半句。
        assertEquals("首", countText("首", 3))
    }

    @Test
    fun playlistCountUsesGeUnitNotShou() {
        // U7b：歌单段论「个」（3 首歌单 → 「3 个」），歌曲段仍旧论「首」。
        assertEquals("3 个", countText("%d 个", 3))
        assertEquals("0 个", countText("%d 个", 0))
    }
}