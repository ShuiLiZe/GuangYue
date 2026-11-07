package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2d：对照 Folia sentenceLayout.ts / tilt。 */
class TiltTest {
    @Test
    fun punctuationSplitKeepsPunctAndSpaces() {
        assertEquals(listOf("你好， ", "世界。"), SentenceLayout.splitIntoSentences("你好， 世界。", 2))
    }

    @Test
    fun mergeShortestNeighbours() {
        assertEquals(listOf("一，二，三，", "四五六七"), SentenceLayout.splitIntoSentences("一，二，三，四五六七", 2))
    }

    @Test
    fun secondarySplitReachesTarget() {
        val r = SentenceLayout.splitIntoSentences("为你弹奏萧邦的夜曲", 3, 4.0)
        assertEquals(3, r.size)
        assertEquals("为你弹奏萧邦的夜曲", r.joinToString(""))
    }

    @Test
    fun layoutSegmentsRejoinAndHaveOffsets() {
        val text = "我面无表情看孤独的风景"
        val l = Tilt.buildLayout(text, 16.0, TiltTuning.DEFAULT, 1.0, 360.0, { t, px -> t.length * px }, { t, px -> t.length * px })
        assertEquals(text, l.segments.joinToString("") { it.text })
        l.segments.forEach { assertEquals(it.text, text.substring(it.charOffset, it.charOffset + it.text.length)) }
        assertTrue(l.segments.count { it.isTilt } <= 1)
    }

    @Test
    fun pulseShape() {
        val t = TiltCharTiming(0, 1.0, 1.4)
        assertEquals(0.0, Tilt.pulse(0.9, t), 1e-9)
        assertEquals(1.0, Tilt.pulse(1.2, t), 1e-9)
        assertEquals(0.25, Tilt.pulse(3.0, t), 1e-9)
    }
}
