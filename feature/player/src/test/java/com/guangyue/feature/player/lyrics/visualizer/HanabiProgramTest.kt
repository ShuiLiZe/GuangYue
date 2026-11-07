package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HanabiProgramTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)

    private val song = listOf(
        line(6.0, 8.0, "夜空に咲いた"),
        line(8.5, 10.0, "一瞬の花"),
        line(10.5, 16.0, "a long line that keeps going"),
        line(30.0, 32.0, "after the gap"),
    )

    @Test
    fun everyLineOpensABurstAndGapsFillWithStarmines() {
        val p = compileHanabi(song)
        assertEquals(p, compileHanabi(song))
        song.forEach { l -> assertTrue(p.any { !it.bridge && it.at == l.startTime }) }
        // 长句中段补一朵
        assertTrue(p.any { !it.bridge && it.at == 13.25 })
        // 前奏、16→30 秒的空隙、尾奏都有间奏烟花
        assertTrue(p.any { it.bridge && it.at < 6.0 })
        assertTrue(p.count { it.bridge && it.at > 16.0 && it.at < 30.0 } >= 8)
        assertTrue(p.any { it.bridge && it.at > 32.0 })
        // 相邻两朵不同种、落点在上部
        p.zipWithNext().forEach { (a, b) -> assertTrue(a.kind != b.kind || a.at == b.at) }
        assertTrue(p.all { it.y in 0.16f..0.46f && it.x in 0.2f..0.8f })
        assertEquals(p.sortedBy { it.at }, p)
    }

    @Test
    fun lineSelectionLeadsAndExitsBeforeNextLine() {
        assertEquals(-1, hanabiLineAt(song, 0.0))
        assertEquals(0, hanabiLineAt(song, 6.0 - HanabiTiming.ENTER_LEAD_S))
        assertEquals(1, hanabiLineAt(song, 9.0))
        assertTrue(hanabiExitAt(song, 0) < song[1].startTime)
        // 后面是长空隙：唱完再挂一会儿
        assertEquals(16.0 + HanabiTiming.HOLD_S, hanabiExitAt(song, 2), 1e-9)
        assertEquals(32.0 + HanabiTiming.HOLD_S, hanabiExitAt(song, 3), 1e-9)
    }
}
