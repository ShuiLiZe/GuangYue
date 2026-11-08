package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.ui.graphics.Color
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetRailTest {
    @Test
    fun `active line is full size and sharp`() {
        val t = MonetRail.tone(0, 0.8f)
        assertEquals(1f, t.scale, 0f)
        assertEquals(1f, t.opacity, 0f)
        assertEquals(0f, t.blurDp, 0f)
    }

    @Test
    fun `waiting lines are brighter than passed lines and shrink with distance`() {
        val next = MonetRail.tone(1, 0.8f)
        val prev = MonetRail.tone(-1, 0.8f)
        val far = MonetRail.tone(3, 0.8f)
        assertTrue(next.opacity > prev.opacity)
        assertTrue(far.scale < next.scale)
        assertTrue(far.scale >= 0.68f)
        assertEquals(0.36f, MonetRail.tone(9, 0.8f).opacity, 1e-4f)
    }

    @Test
    fun `css clamp picks the rem floor on a phone`() {
        assertEquals(21.44f, MonetRail.clampDp(1.34f, 2.75f, 2.28f, 412f), 1e-3f)
        assertEquals(36.48f, MonetRail.clampDp(1.34f, 2.75f, 2.28f, 2000f), 1e-3f)
    }

    @Test
    fun `active text colours sung words and keeps spaces`() {
        val line = Line(
            words = listOf(Word("hello", 0.0, 1.0), Word("world", 1.0, 2.0)),
            startTime = 0.0, endTime = 2.0, fullText = "hello world",
        )
        val text = monetActiveText(line, 1.0, Color.White)
        assertEquals("hello world", text.text)
        val first = text.spanStyles.first { it.start == 0 }
        assertEquals(0.98f, first.item.color.alpha, 1e-2f)
        val second = text.spanStyles.first { it.start == 6 }
        assertEquals(0.34f, second.item.color.alpha, 1e-2f)
    }

    @Test
    fun glowRisesThenFadesLikeFolia() {
        // 词 1.0–2.0s，句尾 3.0s：峰值在 1 + 1.18 = 2.18s，尾巴到 max(3.0, 3.05) = 3.05s。
        org.junit.Assert.assertEquals(0f, monetGlow(1.0, 1.0, 2.0, 3.0), 1e-6f)
        org.junit.Assert.assertEquals(1f, monetGlow(2.18, 1.0, 2.0, 3.0), 1e-4f)
        org.junit.Assert.assertEquals(0.5f, monetGlow(1.59, 1.0, 2.0, 3.0), 1e-3f)
        org.junit.Assert.assertEquals(0f, monetGlow(3.05, 1.0, 2.0, 3.0), 1e-6f)
    }
}
