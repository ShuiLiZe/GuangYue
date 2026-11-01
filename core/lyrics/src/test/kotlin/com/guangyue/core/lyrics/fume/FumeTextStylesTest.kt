// 对照 Folia test/unit/visualizer/fumeBlockFrame.test.ts
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FumeTextStylesTest {

    private val line = Line(
        words = listOf(Word("hello", 10.0, 11.0)),
        startTime = 10.0,
        endTime = 11.0,
        fullText = "hello",
    )

    private val block = FumeBlock(
        id = "b0",
        sourceLineIndex = 0,
        line = line,
        variant = FumeVariant.BODY,
        x = 0.0,
        y = 0.0,
        width = 100.0,
        height = 26.0,
        innerWidth = 100.0,
        fontPx = 20.0,
        lineHeight = 26.0,
        graphemes = FumeMath.splitGraphemes(line.fullText),
        advances = emptyList(),
        segmentMetas = emptyList(),
        wordRanges = emptyList(),
        wordRangeIndexByOffset = emptyList(),
        colorRangeIndexByOffset = emptyList(),
        renderLines = emptyList(),
    )

    private fun timingAt(time: Double, textHoldRatio: Double) =
        FumeTextStyles.resolveBlockTiming(block, time, null, textHoldRatio)

    private fun layersAt(
        time: Double,
        textHoldRatio: Double,
        passedFadeDuration: Double = 2.0,
        overviewTextRestoreProgress: Double = 0.0,
    ) = FumeTextStyles.staticLayerAlphas(
        timingAt(time, textHoldRatio), time, passedFadeDuration, overviewTextRestoreProgress,
    )

    @Test
    fun `draws a waiting block as a single unglowed standard layer`() {
        val timing = timingAt(5.0, 1.0)
        assertEquals(FumeStaticState.WAITING, timing.staticState)
        assertEquals(listOf(FumeTextHoldStyle.STANDARD to 1f), layersAt(5.0, 1.0))
    }

    @Test
    fun `draws the live block itself while it prints and during its colour trail`() {
        assertEquals(FumeStaticState.NONE, timingAt(10.5, 1.0).staticState)
        assertEquals(FumeStaticState.NONE, timingAt(11.2, 1.0).staticState)
        assertTrue(layersAt(10.5, 1.0).isEmpty())
    }

    @Test
    fun `keeps a passed block in its standard look when text is held`() {
        val timing = timingAt(40.0, 1.0)
        assertEquals(FumeStaticState.PASSED, timing.staticState)
        assertEquals(listOf(FumeTextHoldStyle.STANDARD to 1f), layersAt(40.0, 1.0))
    }

    @Test
    fun `cross-fades a dimmed passed block from its standard snapshot`() {
        val timing = timingAt(40.0, 0.5)
        val passedAt = timing.lineEndTime + timing.colorTrailDuration

        assertEquals(listOf(FumeTextHoldStyle.STANDARD to 1f), layersAt(passedAt, 0.5))

        val middle = layersAt(passedAt + 1.0, 0.5)
        assertEquals(listOf(FumeTextHoldStyle.STANDARD, FumeTextHoldStyle.DIMMED), middle.map { it.first })
        assertEquals(1.0, middle[0].second + middle[1].second.toDouble(), 1e-6)

        assertEquals(listOf(FumeTextHoldStyle.DIMMED to 1f), layersAt(passedAt + 60.0, 0.5))
    }

    @Test
    fun `restores the standard look while the overview shot flies in`() {
        val timing = timingAt(40.0, 0.5)
        val late = timing.lineEndTime + timing.colorTrailDuration + 60.0
        assertEquals(
            listOf(FumeTextHoldStyle.STANDARD to 1f),
            layersAt(late, 0.5, overviewTextRestoreProgress = 1.0),
        )
    }

    @Test
    fun `dimmed passed style drops glow entirely`() {
        val dimmed = FumeTextStyles.passedTextStyle(FumeVariant.BODY, FumeTextHoldStyle.DIMMED)
        assertEquals(0.075, dimmed.opacity, 1e-6)
        assertEquals(0.0, dimmed.glowMultiplier, 1e-6)
        val standard = FumeTextStyles.passedTextStyle(FumeVariant.HERO, FumeTextHoldStyle.STANDARD)
        assertEquals(0.74, standard.opacity, 1e-6)
        assertEquals(0.1, standard.shadowAlphaBase, 1e-6)
    }

    @Test
    fun `glow bases follow animation intensity`() {
        assertEquals(1.15, FumeTextStyles.glowBases(AnimationIntensity.CHAOTIC, 1.0).activeGlowBoost, 1e-6)
        assertEquals(0.72, FumeTextStyles.glowBases(AnimationIntensity.CALM, 1.0).activeGlowBoost, 1e-6)
        assertEquals(0.92, FumeTextStyles.glowBases(AnimationIntensity.NORMAL, 1.0).activeGlowBoost, 1e-6)
        assertEquals(0.62, FumeTextStyles.glowBases(AnimationIntensity.NORMAL, 1.0).passedGlowBase, 1e-6)
    }

    @Test
    fun `block on screen test uses camera scale and overscan`() {
        val viewport = FumeViewport(720.0, 1600.0)
        val centered = FumeCameraTarget(x = 0.0, y = 0.0, scale = 1.0)
        assertTrue(FumeTextStyles.isBlockOnScreen(block, centered, viewport))
        val farAway = FumeCameraTarget(x = 10_000.0, y = 0.0, scale = 1.0)
        assertTrue(!FumeTextStyles.isBlockOnScreen(block, farAway, viewport))
    }

    @Test
    fun `passed fade duration is infinite when text is held and clamped otherwise`() {
        assertEquals(
            Double.POSITIVE_INFINITY,
            FumeTextStyles.passedFadeDuration(listOf(line), 1.0),
            0.0,
        )
        assertEquals(2.4, FumeTextStyles.passedFadeDuration(listOf(line), 0.0001), 1e-6)
    }
}