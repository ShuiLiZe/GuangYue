package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class NebbiaTimingTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)
    private val song = listOf(line(0.0, 2.0, "一二"), line(3.0, 5.0, "三四"), line(6.0, 8.0, "五"), line(9.0, 10.0, "六"))

    @Test
    fun clarityHoldsThenFogsBack() {
        assertEquals(0f, NebbiaTiming.clarity(song, 0, -1.0), 0f)
        assertEquals(1f, NebbiaTiming.clarity(song, 0, 4.0), 0f)
        // 下一句 3.0 开唱，晚于本句 2.0 唱完 → 4.5 起雾，8 秒吞完
        assertEquals(0.5f, NebbiaTiming.clarity(song, 0, 8.5), 1e-4f)
        // 构图排版避让重叠后不再有槽位复用：按雾吞进度继续退（9.0 → 0.4375）
        assertEquals(0.4375f, NebbiaTiming.clarity(song, 0, 9.0), 1e-4f)
        assertEquals(0f, NebbiaTiming.clarity(song, 0, 12.5), 0f)
    }

    @Test
    fun cjkSplitsPerCharLatinPerWord() {
        val u = nebbiaUnits(line(0.0, 3.0, "夜 rain falls"))
        assertEquals(listOf("夜", "rain", "falls"), u.map { it.first })
        assertEquals(1.0, u[1].second, 1e-9)
        val w = nebbiaUnits(Line(listOf(Word("你好", 1.0, 2.0)), 1.0, 2.0, "你好"))
        assertEquals(1.5, w[1].second, 1e-9)
    }
}
