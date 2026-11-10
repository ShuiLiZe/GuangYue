package com.guangyue.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLayoutTest {

    @Test fun `少量曲目默认列表`() {
        listOf(0, 1, 2, 12).forEach { assertTrue("$it", CollectionLayout.preferList(it)) }
    }

    @Test fun `中等数量用墙，墙放不下又回列表`() {
        assertFalse(CollectionLayout.preferList(13))
        assertFalse(CollectionLayout.preferList(CollectionLayout.WALL_CAPACITY))
        assertTrue(CollectionLayout.preferList(CollectionLayout.WALL_CAPACITY + 1))
        assertEquals(108, CollectionLayout.WALL_CAPACITY)
    }

    @Test fun `占位首字`() {
        assertEquals("发", CollectionLayout.initial("发如雪"))
        assertEquals("Y", CollectionLayout.initial("  yesterday"))
        assertEquals("七", CollectionLayout.initial("《七里香》"))
        assertNull(CollectionLayout.initial(""))
        assertNull(CollectionLayout.initial("♪ ~ !"))
        // 码点取字：增补平面汉字不拆半
        assertEquals("𠀀", CollectionLayout.initial("𠀀abc"))
    }

    @Test fun `占位色相稳定且在 0 到 360`() {
        val h = CollectionLayout.hue("发如雪")
        assertEquals(h, CollectionLayout.hue("发如雪"), 0f)
        assertTrue(h >= 0f && h < 360f)
    }
}
