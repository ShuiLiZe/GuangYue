package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2a：对照 Folia cjkSemanticLayout.ts / partita/partitaLayout.ts。 */
class PartitaLayoutTest {

    private fun w(t: String, s: Double) = Word(text = t, startTime = s, endTime = s + 0.3)
    private fun line(vararg ws: Word) = Line(
        words = ws.toList(), startTime = ws.first().startTime, endTime = ws.last().endTime,
        fullText = ws.joinToString("") { it.text },
    )

    @Test
    fun stickyContractionBecomesOneDisplayWord() {
        val l = line(w("It", 1.0), w("’", 1.3), w("s", 1.6), w(" ", 1.8), w("unbelievable", 2.0))
        val units = PostLyricLayout.build(l, semantic = false, sticky = true)
        assertEquals("It’s", units[0].text)
        assertTrue(units[0].isSticky)
        assertEquals(3, units[0].words.size)
        val display = PostLyricLayout.displayWords(units.take(1))
        assertEquals(listOf("It’s"), display.map { it.text })
    }

    @Test
    fun trailingPunctuationSticks() {
        val l = line(w("世", 1.0), w("界", 1.3), w("。", 1.6))
        val units = PostLyricLayout.build(l, semantic = false, sticky = true)
        assertEquals(listOf("世", "界。"), units.map { it.text })
    }

    @Test
    fun savedSegmentationGroupsCjkSemantically() {
        val base = line(w("世", 1.0), w("界", 1.3), w("你", 1.6), w("好", 1.9))
        val l = base.copy(wordSegments = listOf("世界", "你好"))
        val units = PostLyricLayout.build(l, semantic = true, sticky = true)
        assertEquals(listOf("世界", "你好"), units.map { it.text })
        assertTrue(units.all { it.isSemantic })
        // 语义单元保留逐字 timing
        assertEquals(4, PostLyricLayout.displayWords(units).size)
    }

    @Test
    fun chunksCoverAllUnitsInOrderAndRowCountFollowsHeight() {
        val ws = (0 until 12).map { w("字$it".substring(0, 1) + it, 10.0 + it * 0.4) }.toTypedArray()
        val l = line(*ws)
        val layout = PartitaLayoutBuilder.build(l, AnimationIntensity.NORMAL, 800.0, PartitaTuning.DEFAULT)
        // floor(800*0.65/100) = 5 行
        assertEquals(5, layout.chunks.size)
        assertEquals(ws.map { it.text }, layout.chunks.flatMap { it.chunkWords }.map { it.text })
        layout.chunks.forEachIndexed { i, c ->
            val mag = kotlin.math.abs(c.config.x)
            assertTrue(mag in 20.0..100.0)
            assertEquals(if (i % 2 == 0) -mag else mag, c.config.x, 1e-9)
        }
        // 确定性
        assertEquals(layout, PartitaLayoutBuilder.build(l, AnimationIntensity.NORMAL, 800.0, PartitaTuning.DEFAULT))
    }

    @Test
    fun tuningSanitizeSwapsAndClamps() {
        val t = PartitaTuning.sanitize(staggerMin = 200.0, staggerMax = -5.0)
        assertEquals(0.0, t.staggerMin, 0.0)
        assertEquals(180.0, t.staggerMax, 0.0)
    }

    @Test
    fun jsRoundHalfUp() {
        assertEquals(3, PartitaLayoutBuilder.jsRound(2.5))
        assertEquals(-2, PartitaLayoutBuilder.jsRound(-2.5))
    }
}
