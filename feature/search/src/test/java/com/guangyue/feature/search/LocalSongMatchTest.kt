package com.guangyue.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSongMatchTest {
    // 测试用的小拼音表（真机用 ICU Han-Latin）。
    private val table = mapOf('晴' to "qing", '天' to "tian", '周' to "zhou", '杰' to "jie", '伦' to "lun", '后' to "hou", '来' to "lai")
    private val toPy: (String) -> String? = { s -> s.map { c -> table[c] ?: c.toString() }.joinToString(" ") }
    private fun py(s: String) = LocalSongMatch.keys(s, toPy)

    @Test fun keysFullAndInitials() {
        assertEquals("qingtian" to "qt", py("晴天"))
        assertNull(LocalSongMatch.keys("Yellow", toPy))
        assertNull(LocalSongMatch.keys("晴天", null))
    }

    @Test fun matchesRawPinyinAndInitials() {
        val f = listOf("晴天", "周杰伦", "叶惠美")
        assertTrue(LocalSongMatch.matches("晴", f, ::py))
        assertTrue(LocalSongMatch.matches("qingtian", f, ::py))
        assertTrue(LocalSongMatch.matches("qing tian", f, ::py))
        assertTrue(LocalSongMatch.matches("QT", f, ::py))
        assertTrue(LocalSongMatch.matches("zjl", f, ::py))
        assertTrue(LocalSongMatch.matches("jielun", f, ::py))
        assertFalse(LocalSongMatch.matches("houlai", f, ::py))
        assertFalse(LocalSongMatch.matches("  ", f, ::py))
        assertTrue(LocalSongMatch.matches("yel", listOf("Yellow", "Coldplay", ""), ::py))
    }
}
