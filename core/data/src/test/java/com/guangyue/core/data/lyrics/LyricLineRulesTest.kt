package com.guangyue.core.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LyricLineRulesTest {
    private fun lyric(vararg texts: String) = Lyric(lines = texts.mapIndexed { i, t -> LyricLine(i * 1000L, t) })

    @Test fun presetsRemoveMatchingLines() {
        val l = lyric("词：方文山", "未经许可不得翻唱", "窗外的麻雀", "歌词贡献者：某人", "Copyright ©2004")
        val out = LyricLineRules.apply(l, LyricFilterSettings(copyright = true, lyricSource = true))
        assertEquals(listOf("词：方文山", "窗外的麻雀"), out.lines.map { it.text })
    }

    @Test fun containsWordsFoldWidthAndCase() {
        val l = lyric("ＯＰ：Sony", "op: x", "晴天", "")
        val out = LyricLineRules.apply(l, LyricFilterSettings(containsWords = "op:、不存在"))
        assertEquals(listOf("晴天", ""), out.lines.map { it.text })
    }

    @Test fun writtenRegexAndInvalid() {
        val l = lyric("作曲：周杰伦", "故事的小黄花")
        assertEquals(listOf("故事的小黄花"), LyricLineRules.apply(l, LyricFilterSettings(written = "^作[词曲]")).lines.map { it.text })
        assertNotNull(LyricLineRules.writtenError("(abc"))
        assertNull(LyricLineRules.writtenError("^a"))
        assertEquals(2, LyricLineRules.apply(l, LyricFilterSettings(written = "(abc")).lines.size)
    }

    @Test fun noRulesReturnsSame() {
        val l = lyric("a")
        assertSame(l, LyricLineRules.apply(l, LyricFilterSettings()))
    }
}
