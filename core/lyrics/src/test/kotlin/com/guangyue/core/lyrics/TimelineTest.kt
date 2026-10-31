// 对应 Folia 源码：src/utils/lyrics/renderHints.ts + graphemeTiming.ts（时间轴接入）
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    @Test
    fun `毫秒转秒`() {
        assertEquals(1.5, Timeline.ms(1500L), 1e-12)
        assertEquals(0.0, Timeline.ms(0L), 1e-12)
    }

    @Test
    fun `行结束时间取下一行开始`() {
        val inputs = listOf(
            TimelineLineInput(1_000L, "第一句"),
            TimelineLineInput(3_000L, "第二句"),
        )
        val lines = Timeline.buildLines(inputs, durationMs = 10_000L)
        assertEquals(1.0, lines[0].startTime, 1e-12)
        assertEquals(3.0, lines[0].endTime, 1e-12)
        assertEquals(3.0, lines[1].startTime, 1e-12)
        assertEquals(10.0, lines[1].endTime, 1e-12)
    }

    @Test
    fun `最后一行没有曲长时用兜底节奏 4000ms`() {
        val lines = Timeline.buildLines(listOf(TimelineLineInput(1_000L, "唯一一句")))
        assertEquals(5.0, lines[0].endTime, 1e-12)
    }

    @Test
    fun `每行都预填渲染提示`() {
        val lines = Timeline.buildLines(listOf(TimelineLineInput(0L, "短句")))
        val hints = lines[0].renderHints!!
        assertEquals(4.0, hints.rawDuration, 1e-12)
        assertEquals(TimingClass.NORMAL, hints.timingClass)
        assertEquals(LineTransitionMode.NORMAL, hints.lineTransitionMode)
        assertEquals(WordRevealMode.NORMAL, hints.wordRevealMode)
    }

    @Test
    fun `有逐字时间戳就直接做成 Word 不造假`() {
        val input = TimelineLineInput(
            1_000L, "你好",
            tags = listOf(
                TimelineTag(1_000L, 1_500L, "你"),
                TimelineTag(1_500L, 2_200L, "好"),
            ),
        )
        val words = Timeline.buildWords(input, endMs = 2_200L)
        assertEquals(2, words.size)
        assertEquals("你", words[0].text)
        assertEquals(1.0, words[0].startTime, 1e-12)
        assertEquals(1.5, words[0].endTime, 1e-12)
        assertEquals(2.2, words[1].endTime, 1e-12)
    }

    @Test
    fun `没有逐字时间戳时按整行字均分`() {
        val input = TimelineLineInput(0L, "你好世界")
        val words = Timeline.buildWords(input, endMs = 4_000L)
        // CJK 每字一片：4 个词，各 1 秒
        assertEquals(4, words.size)
        assertEquals(1.0, words[0].endTime, 1e-12)
        assertEquals(1.0, words[1].startTime, 1e-12)
        assertEquals(4.0, words[3].endTime, 1e-12)
    }

    @Test
    fun `混合文本按 grapheme 数均分且末尾取行结束时间`() {
        val words = Timeline.distributeWordsEvenly(listOf("ab", "你"), startTime = 0.0, endTime = 3.0)
        assertEquals(2, words.size)
        assertEquals(2.0, words[0].endTime, 1e-12)
        assertEquals(2.0, words[1].startTime, 1e-12)
        assertEquals(3.0, words[1].endTime, 1e-12)
    }

    @Test
    fun `空文本不产生词`() {
        assertTrue(Timeline.buildWords(TimelineLineInput(0L, ""), 1_000L).isEmpty())
        assertTrue(Timeline.distributeWordsEvenly(emptyList(), 0.0, 1.0).isEmpty())
    }

    @Test
    fun `行结束时间至少比开始时间大 1ms`() {
        val lines = Timeline.buildLines(listOf(TimelineLineInput(5_000L, "同一时刻"), TimelineLineInput(5_000L, "下一句")))
        assertEquals(5.001, lines[0].endTime, 1e-9)
    }
}