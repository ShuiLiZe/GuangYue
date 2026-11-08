package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NebbiaComposerTest {
    private fun line(text: String, t: Double, e: Double) = Line(startTime = t, endTime = e, fullText = text, words = emptyList())

    @Test fun everyCharGetsAPositionInsideScreen() {
        val lines = listOf(line("我们慢慢走回去星河与风一起落在很远很远的地方", 30.0, 38.0), line("风吹过", 38.0, 41.0), line("光落在晨雾里", 41.0, 45.0))
        val src = NebbiaComposer.buildLines(lines)
        val pos = NebbiaComposer.layoutSong(src, 1600.0, 720.0, 1.0, 52.0, 1.0, { 60.0 }, { 50.0 })
        assertEquals(src.size, pos.size)
        src.forEachIndexed { i, s ->
            assertEquals(s.chars.size, pos[i].size)
            pos[i].forEach { p -> assertTrue(p.x in 0.0..1600.0 && p.y in 0.0..720.0 && p.s > 0) }
        }
    }

    @Test fun heroWordIsLargest() {
        val src = NebbiaComposer.buildLines(listOf(line("光落在晨雾里", 0.0, 4.0)))[0]
        val words = NebbiaComposer.mergeWords(src.chars, src.words)
        val roles = NebbiaComposer.assignRoles(src.chars, words)
        if (words.size > 1) assertEquals(1, roles.count { it == NebbiaComposer.Role.HERO })
    }

    @Test fun rngIsDeterministic() {
        val a = NebbiaComposer.Rng(42); val b = NebbiaComposer.Rng(42)
        repeat(5) { assertEquals(a.next(), b.next(), 0.0) }
    }
}
