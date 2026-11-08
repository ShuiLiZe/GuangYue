package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumiereProgramTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)

    private val song = listOf(
        line(6.0, 9.0, "一群嗜血的蚂蚁被腐肉所吸引"),
        line(9.5, 12.0, "我面无表情"),
        line(12.2, 16.0, "the rain keeps falling on the empty street"),
        line(16.3, 18.0, "看孤独的风景"),
        line(30.0, 32.0, "after the gap"),
        line(36.5, 38.0, "又一段"),
    )

    @Test
    fun shotsTileTheTimelineAndEveryLineLandsOnce() {
        val p = compileLumiere(song, songEnd = 50.0)
        assertEquals(p, compileLumiere(song, songEnd = 50.0))
        assertEquals(0.0, p.first().start, 1e-9)
        assertEquals(50.0, p.last().end, 1e-9)
        p.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start, 1e-9); assertTrue(a.start < b.start) }
        assertEquals(song.indices.toList(), p.flatMap { it.lines })
        assertTrue(p.all { it.lines.size <= LumiereTiming.LINES_PER_SHOT })
    }

    @Test
    fun introAndLongGapBecomeBridgesAndFamiliesDoNotRepeat() {
        val p = compileLumiere(song)
        // 前奏 6 秒：间奏镜头 + 星空开场；第一段首镜头不再重播。
        assertTrue(p.first().bridge && p.first().opening && p.first().lines.isEmpty())
        assertFalse(p[1].opening)
        // 18→30 秒空隙出间奏镜头；32→36.5 空隙 4.5 秒：分段但不出间奏，重播开场。
        assertTrue(p.any { it.bridge && it.start in 18.0..30.0 })
        val last = p.first { 5 in it.lines }
        assertTrue(last.opening)
        p.zipWithNext().forEach { (a, b) -> assertNotEquals(a.family, b.family) }
    }

    @Test
    fun lookupsFollowTime() {
        val p = compileLumiere(song)
        assertEquals(-1, lumiereLineAt(song, 1.0))
        assertEquals(0, lumiereLineAt(song, 5.8))
        assertEquals(4, lumiereLineAt(song, 31.0))
        val i = lumiereShotAt(p, 31.0)
        assertTrue(4 in p[i].lines)
    }
}
