// 对应 Folia 源码：src/utils/lyrics/graphemeTiming.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphemeTimingTest {

    @Test
    fun `字级均分：最后一个字拿到行结束时间`() {
        val timings = GraphemeTimings.buildEvenGraphemeTimings("你好世界", 0.0, 4.0)
        assertEquals(4, timings.size)
        assertEquals(0.0, timings[0].startTime, 1e-12)
        assertEquals(1.0, timings[1].startTime, 1e-12)
        assertEquals(1.0, timings[0].endTime, 1e-12)
        assertEquals(4.0, timings[3].endTime, 1e-12)
    }

    @Test
    fun `字级均分带上词下标`() {
        val timings = GraphemeTimings.buildEvenGraphemeTimings("ab", 2.0, 3.0, wordIndex = 7)
        assertEquals(2, timings.size)
        assertEquals(7, timings[0].wordIndex)
        assertEquals("a", timings[0].char)
        assertEquals(3.0, timings[1].endTime, 1e-12)
    }

    @Test
    fun `空文本不产生字`() {
        assertTrue(GraphemeTimings.buildEvenGraphemeTimings("", 0.0, 1.0).isEmpty())
        assertTrue(GraphemeTimings.splitLyricGraphemes("").isEmpty())
    }

    @Test
    fun `有 syllables 时逐音节均分后拼接`() {
        val word = Word(
            text = "你好",
            startTime = 1.0,
            endTime = 3.0,
            syllables = listOf(
                LyricSyllable("你", 1.0, 1.5),
                LyricSyllable("好", 1.5, 3.0),
            ),
        )
        val timings = GraphemeTimings.buildWordGraphemeTimings(word)
        assertEquals(2, timings.size)
        assertEquals(1.5, timings[0].endTime, 1e-12)
        // 第二个音节「好」是单字，直接拿到该音节自己的区间 1.5–3.0（不再二次均分）
        assertEquals(1.5, timings[1].startTime, 1e-12)
        assertEquals(3.0, timings[1].endTime, 1e-12)
    }

    @Test
    fun `无 syllables 时整词均分`() {
        val word = Word(text = "abcd", startTime = 0.0, endTime = 2.0)
        val timings = GraphemeTimings.buildWordGraphemeTimings(word)
        assertEquals(4, timings.size)
        assertEquals(0.5, timings[0].endTime, 1e-12)
        assertEquals(2.0, timings[3].endTime, 1e-12)
    }

    @Test
    fun `序列查找命中与未命中`() {
        assertEquals(1, GraphemeTimings.findGraphemeSequence(listOf("a", "b", "c"), listOf("b", "c"), 0))
        assertEquals(2, GraphemeTimings.findGraphemeSequence(listOf("a", "b", "c"), listOf("c"), 0))
        assertEquals(-1, GraphemeTimings.findGraphemeSequence(listOf("a", "b"), listOf("c"), 0))
        assertEquals(0, GraphemeTimings.findGraphemeSequence(listOf("a"), emptyList(), 0))
    }

    @Test
    fun `行时间轴以行 grapheme 为基准并保留缺口`() {
        val words = listOf(Word("你", 1.0, 2.0), Word("好", 3.0, 4.0))
        val line = Line(words = words, startTime = 1.0, endTime = 4.0, fullText = "你，好")
        val timeline = GraphemeTimings.buildLineGraphemeTimeline(line)
        assertEquals(3, timeline.size)
        assertEquals("你", timeline[0]!!.char)
        // 缺口（逗号）记零长，起点是下一个词的起点
        assertEquals("，", timeline[1]!!.char)
        assertEquals(3.0, timeline[1]!!.startTime, 1e-12)
        assertEquals(3.0, timeline[1]!!.endTime, 1e-12)
    }

    @Test
    fun `无词行按整行均分`() {
        val line = Line(words = emptyList(), startTime = 0.0, endTime = 2.0, fullText = "ab")
        val timeline = GraphemeTimings.buildLineGraphemeTimeline(line)
        assertEquals(2, timeline.size)
        assertEquals(1.0, timeline[0]!!.endTime, 1e-12)
    }
}