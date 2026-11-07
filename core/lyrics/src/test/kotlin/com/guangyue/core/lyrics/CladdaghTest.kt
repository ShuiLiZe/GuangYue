package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2e：对照 Folia claddaghTimeline.ts / claddaghLayout.ts。 */
class CladdaghTest {
    private val line = Line(words = listOf(Word("a b", 1.0, 2.0)), startTime = 1.0, endTime = 2.0, fullText = "a b")

    @Test
    fun zeroDurationGapsGetSpread() {
        val tl = listOf(GraphemeTiming("a", 1.0, 1.5), GraphemeTiming(" ", 1.5, 1.5), GraphemeTiming("b", 1.5, 2.0))
        val r = Claddagh.adjustTimeline(tl, line)
        assertTrue(r[1].endTime - r[1].startTime >= 0.06 - 1e-9)
        assertEquals(r[0].endTime, r[1].startTime, 1e-9)
        assertEquals(r[1].endTime, r[2].startTime, 1e-9)
    }

    @Test
    fun readableAngleFolds() {
        assertEquals(-80.0, Claddagh.normalizeReadableAngle(100.0), 1e-9)
        assertEquals(80.0, Claddagh.normalizeReadableAngle(-100.0), 1e-9)
    }

    @Test
    fun spacingCentredAndOffsetsInterpolate() {
        val tl = (0 until 5).map { GraphemeTiming("字", 1.0 + it * 0.5, 1.5 + it * 0.5) }
        val g = Claddagh.spacing(tl, 72.0, 158.4, 1.0, 0.0) { it.length * 72.0 * 1.04 }
        assertTrue(kotlin.math.abs(g[2].nominalAngle) < 0.1)
        assertEquals(g[0].nominalAngle, Claddagh.wordOffset(g, 0.5, null), 1e-9)
        val mid = Claddagh.wordOffset(g, 1.25, null)
        assertEquals((g[0].nominalAngle + g[1].nominalAngle) / 2, mid, 1e-9)
    }
}
