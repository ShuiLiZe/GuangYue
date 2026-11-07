package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonnetProgramTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)

    @Test
    fun shotsGroupAtMostThreeLinesAndNeverRepeatKind() {
        val song = (0 until 14).map { line(it * 2.0, it * 2.0 + 1.8, "第${it}句 hello world") } +
            listOf(line(40.0, 42.0, "after the gap"), line(42.5, 44.0, "终章"))
        val p = compileSonnet(song)
        assertTrue(p.shots.all { it.lines.size in 1..3 })
        assertEquals(song.indices.toList(), p.shots.flatMap { it.lines })
        p.shots.zipWithNext().forEach { (a, b) -> assertNotEquals(a.kind, b.kind) }
        // 第一个镜头没有转场；10 秒空隙处开新段落，带转场进入
        assertNull(p.shots.first().transitionIn)
        val gapShot = p.shots.first { 14 in it.lines }
        assertEquals(14, gapShot.lines.first())
        assertTrue(gapShot.transitionIn != null)
        assertEquals(gapShot.index, sonnetShotAt(p, 41.0))
        assertEquals(0, sonnetShotAt(p, -3.0))
    }

    @Test
    fun segmentsFollowWordTimingAndHeroPrefersLongWords() {
        val l = Line(listOf(Word("rain ", 1.0, 2.0), Word("falls", 2.0, 4.0)), 1.0, 4.0, "rain falls")
        val s = sonnetSegments(l)
        assertEquals(listOf("rain", "falls"), s.map { it.text })
        assertEquals(2.0, s[1].start, 1e-9)
        assertEquals(1, sonnetHeroIndex(s))
        // 中日韩：块最长 4 字，拼回原句不丢字
        val c = sonnetSegments(line(0.0, 4.0, "夜空中最亮的星，能否听清"))
        assertTrue(c.all { it.text.length <= 4 })
        assertEquals("夜空中最亮的星，能否听清", c.joinToString("") { it.text })
    }
}
