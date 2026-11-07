package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2b：对照 Folia cadenza（envelopes / preparedState / placements）。 */
class CadenzaTest {
    private fun w(t: String, s: Double, e: Double = s + 0.4) = Word(text = t, startTime = s, endTime = e)
    private val line = Line(
        words = listOf(w("为", 4.0), w("你", 4.4), w("弹", 4.8), w("奏", 5.2), w("萧", 5.6), w("邦", 6.0), w("的", 6.4), w("夜", 6.8), w("曲", 7.2)),
        startTime = 4.0, endTime = 8.0, fullText = "为你弹奏萧邦的夜曲",
    )
    private val measure: (String, Double) -> Double = { t, px -> GraphemeTimings.splitLyricGraphemes(t).size * px }

    @Test
    fun fontPxFollowsFolia() {
        // 360*0.086 = 30.96 → clamp 34；9 字、9 词 → density 罚 3 → 31
        assertEquals(31.0, Cadenza.chooseFontPx(360.0, line), 1e-9)
    }

    @Test
    fun placementsCoverEveryWordWithOneHero() {
        val p = Cadenza.prepare(line, 360.0, 760.0, CadenzaTuning.DEFAULT, AnimationIntensity.NORMAL, measure)!!
        assertEquals(9, p.placements.size)
        assertEquals(1, p.placements.count { it.emphasis > 1 })
        val hero = p.placements.first { it.emphasis > 1 }
        // 主词落在中心附近（搜索半径 max(20, 行高*0.5) 内；原点被上下界挡住时会挪一步）
        assertTrue(kotlin.math.hypot(hero.x + hero.width / 2, hero.y) <= maxOf(20.0, 38.0 * 0.5) + 1e-9)
        assertEquals(p, Cadenza.prepare(line, 360.0, 760.0, CadenzaTuning.DEFAULT, AnimationIntensity.NORMAL, measure))
    }

    @Test
    fun statusUsesLookahead() {
        val lt = Cadenza.resolveLineTiming(line)
        assertEquals(CadenzaStatus.ACTIVE, Cadenza.wordStatus(3.85, lt, line.words[0]))
        assertEquals(CadenzaStatus.WAITING, Cadenza.wordStatus(3.7, lt, line.words[0]))
        assertEquals(CadenzaStatus.PASSED, Cadenza.wordStatus(4.5, lt, line.words[0]))
    }

    @Test
    fun glowEnvelopeRisesHoldsAndFades() {
        val lt = Cadenza.resolveLineTiming(line)
        val wd = line.words[0]
        assertEquals(0.0, Cadenza.glowEnvelope(3.9, lt, wd), 1e-9)
        assertEquals(1.0, Cadenza.glowEnvelope(4.2, lt, wd), 1e-9)
        assertTrue(Cadenza.glowEnvelope(4.8, lt, wd) in 0.0..0.9)
    }
}
