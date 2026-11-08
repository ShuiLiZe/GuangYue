package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperaProgramTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)

    private val song = listOf(
        line(6.0, 9.0, "一群嗜血的蚂蚁被腐肉所吸引"),
        line(9.5, 12.0, "我面无表情"),
        line(12.2, 16.0, "the rain keeps falling on the empty street"),
        line(30.0, 32.0, "after the gap"),
    )

    @Test
    fun everyUnitLandsInExactlyOneShotAndGapsBecomeBridges() {
        val p = compileTempera(song)
        assertEquals(p, compileTempera(song))
        // 每个字 / 词恰好进一个 shot，顺序不乱。
        val all = p.flatMap { it.units.map { u -> u.text } }
        assertEquals(song.flatMap { nebbiaUnits(it).map { u -> u.first } }, all)
        // shot 首尾相接、按时间排好。
        p.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start, 1e-9); assertTrue(a.start < b.start) }
        // 前奏、16→30 秒空隙、尾奏都有无字 bridge，单个不超过 5 秒多一点。
        assertTrue(p.first().bridge)
        val gap = p.filter { it.bridge && it.start > 16.0 && it.start < 30.0 }
        assertTrue(gap.size in 2..3)
        assertTrue(p.last().bridge)
        // 留白卡只给有字的 shot；相邻（含隔一个）构图不重复。
        assertTrue(p.none { it.bridge && it.comp == TemperaComp.BLANK })
        assertTrue(p.any { it.comp == TemperaComp.BLANK })
        p.zipWithNext().forEach { (a, b) -> assertTrue(a.comp != b.comp) }
        p.windowed(3).forEach { (a, _, c) -> assertTrue(a.comp != c.comp) }
        // 有字 shot 都有 hero；相邻词入场方式不同。
        p.filter { !it.bridge }.forEach { s ->
            assertTrue(s.hero in s.units.indices)
            s.units.zipWithNext().forEach { (a, b) -> assertTrue(a.enter != b.enter) }
            assertTrue(s.flow in -18f..18f)
        }
    }

    @Test
    fun slicesCutLongLinesButKeepTailsAttached() {
        val sl = temperaSlices(nebbiaUnits(song[0]))
        assertTrue(sl.size >= 2)
        assertTrue(sl.all { it.size >= 2 })
        val en = temperaSlices(nebbiaUnits(song[2]))
        assertTrue(en.all { it.size in 2..5 })
    }

    @Test
    fun handoffOverlapsTwoShotsAroundEachBoundary() {
        val p = compileTempera(song)
        val b = p.indexOfFirst { !it.bridge && it.line == 1 }
        val at = p[b].start
        val before = temperaShotsAt(p, at - 0.05)
        assertEquals(listOf(b - 1, b), before.map { it.first })
        assertTrue(before[0].second in 0f..0.5f && before[1].second in -1f..-0.5f)
        val after = temperaShotsAt(p, at + 0.05)
        assertEquals(listOf(b - 1, b), after.map { it.first })
        // 交接结束后只剩自己，就位。
        val mid = (p[b].start + p[b].end) / 2
        assertEquals(listOf(b to 0f), temperaShotsAt(p, mid))
    }
}
