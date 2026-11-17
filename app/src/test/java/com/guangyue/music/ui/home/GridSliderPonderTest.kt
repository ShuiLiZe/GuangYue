package com.guangyue.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridSliderPonderTest {
    @Test
    fun chaptersKeepOnlyPresentAnchors() {
        val all = GridPagePonder.ALL.flatMap { c -> c.steps.map { it.anchor } }.toSet()
        assertEquals(GridPagePonder.ALL, GridPagePonder.chapters(all))
        // 不在本地页签：没有专属操作，也没有「全部」与播放条。
        val ch = GridPagePonder.chapters(setOf("help", "tabs", "search", "shelf", "focusedCard", "provider"))
        assertEquals(listOf("页头的每个入口", "切换来源与专属操作", "移动焦点并打开集合", "平台与播放"), ch.map { it.title })
        assertEquals(listOf("tabs"), ch[1].steps.map { it.anchor })
        assertEquals(listOf("provider"), ch[3].steps.map { it.anchor })
        assertTrue(GridPagePonder.chapters(emptySet()).isEmpty())
    }

    @Test
    fun hotspotIsTopRightSquare() {
        assertTrue(GridPagePonder.inHotspot(700f, 10f, 720f, 192f))
        assertTrue(GridPagePonder.inHotspot(528f, 192f, 720f, 192f))
        assertFalse(GridPagePonder.inHotspot(500f, 10f, 720f, 192f))
        assertFalse(GridPagePonder.inHotspot(700f, 200f, 720f, 192f))
    }
}
