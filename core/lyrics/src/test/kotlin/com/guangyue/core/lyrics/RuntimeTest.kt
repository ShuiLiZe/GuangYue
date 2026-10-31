// 对应 Folia 源码：src/components/visualizer/runtime.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeTest {

    private fun line(start: Double, end: Double, text: String) =
        Line(words = emptyList(), startTime = start, endTime = end, fullText = text)

    private val lines = listOf(
        line(0.0, 2.0, "第一句"),
        line(2.0, 4.0, "第二句"),
        line(4.0, 6.0, "第三句"),
    )

    @Test
    fun `有活动行时不返回上一句`() {
        assertNull(Runtime.getRecentCompletedLine(lines, currentLineIndex = 1, currentTime = 3.0))
    }

    @Test
    fun `没有活动行时返回最后一句已完成的`() {
        assertEquals("第二句", Runtime.getRecentCompletedLine(lines, currentLineIndex = -1, currentTime = 5.0)!!.fullText)
        assertNull(Runtime.getRecentCompletedLine(lines, currentLineIndex = -1, currentTime = 0.5))
    }

    @Test
    fun `下一句：有活动行取后一行，否则取第一句还没开始的`() {
        assertEquals("第三句", Runtime.getUpcomingLine(lines, 1, 2.5)!!.fullText)
        // 第一句前（时间早于 0）→ 第一句
        assertEquals("第一句", Runtime.getUpcomingLine(lines, -1, -0.5)!!.fullText)
        // 刚好落在第一句开始时刻 → 它已经开始，取第二句（Folia: startTime > time）
        assertEquals("第二句", Runtime.getUpcomingLine(lines, -1, 0.0)!!.fullText)
        assertNull(Runtime.getUpcomingLine(lines, 2, 5.0))
    }

    @Test
    fun `下几句：没有活动行返回空，否则按数量切片`() {
        assertTrue(Runtime.getUpcomingLines(lines, -1).isEmpty())
        assertEquals(2, Runtime.getUpcomingLines(lines, 0).size)
        assertEquals(1, Runtime.getUpcomingLines(lines, 1).size)
    }

    @Test
    fun `字幕下几句在没有活动行时从上一句起算`() {
        val recent = lines[1]
        val next = Runtime.getSubtitleNextLines(lines, currentLineIndex = -1, recentCompletedLine = recent, count = 2)
        assertEquals(1, next.size)
        assertEquals("第三句", next[0].fullText)
    }

    @Test
    fun `预热窗口：提前量落在窗口内才预热`() {
        val line = line(10.0, 12.0, "第十句")
        // 窗口 0.4–2.0：提前 0.5s / 1.5s 预热，提前 0.3s 太晚，提前 2.5s 太早
        assertTrue(Runtime.shouldPreheatLine(line, 9.5, PrelatureWindow.DEFAULT))
        assertTrue(Runtime.shouldPreheatLine(line, 8.5, PrelatureWindow.DEFAULT))
        assertFalse(Runtime.shouldPreheatLine(line, 9.7, PrelatureWindow.DEFAULT))
        assertFalse(Runtime.shouldPreheatLine(line, 7.5, PrelatureWindow.DEFAULT))
        assertFalse(Runtime.shouldPreheatLine(null, 9.5, PrelatureWindow.DEFAULT))
    }

    @Test
    fun `准备活动行并顺带预热下一行`() {
        val prepared = ArrayList<String>()
        val result = Runtime.prepareActiveAndUpcoming(lines[0], lines[1]) { target ->
            if (target != null) prepared.add(target.fullText)
            target
        }
        assertEquals("第一句", result!!.fullText)
        assertEquals(listOf("第一句", "第二句"), prepared)
    }

    @Test
    fun `没有活动行时只预热并返回 null`() {
        val prepared = ArrayList<String>()
        val result = Runtime.prepareActiveAndUpcoming(null, lines[0]) { target ->
            if (target != null) prepared.add(target.fullText)
            target
        }
        assertNull(result)
        assertEquals(listOf("第一句"), prepared)
    }

    @Test
    fun `当前行下标二分与第一句前 -1`() {
        assertEquals(-1, Runtime.currentLineIndex(lines, 0.0 - 0.1))
        assertEquals(0, Runtime.currentLineIndex(lines, 0.0))
        assertEquals(1, Runtime.currentLineIndex(lines, 3.0))
        assertEquals(-1, Runtime.currentLineIndex(emptyList(), 1.0))
    }

    @Test
    fun `运行时视图把活动行上下句与下几句一起给出`() {
        val runtime = Runtime.computeRuntime(lines, currentLineIndex = 1, currentTimeValue = 3.0)
        assertEquals("第二句", runtime.activeLine!!.fullText)
        assertEquals("第三句", runtime.upcomingLine!!.fullText)
        assertNull(runtime.recentCompletedLine)
        assertEquals(1, runtime.nextLines.size)
    }
}