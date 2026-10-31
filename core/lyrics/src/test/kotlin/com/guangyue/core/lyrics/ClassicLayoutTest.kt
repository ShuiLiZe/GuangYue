// 对应 Folia 源码：src/components/visualizer/classic/Visualizer.tsx + classic/tuning.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicLayoutTest {

    private fun line(start: Double, end: Double, text: String, words: List<Word> = emptyList()) =
        Line(words = words, startTime = start, endTime = end, fullText = text)

    private val tuning = ClassicTuning()

    @Test
    fun `呼吸浮动参数：calm 10-8_5 normal 14-7 chaotic 18-5_8`() {
        assertEquals(BreathingConfig(10.0, 8.5), ClassicLayout.breathingConfig(AnimationIntensity.CALM))
        assertEquals(BreathingConfig(14.0, 7.0), ClassicLayout.breathingConfig(AnimationIntensity.NORMAL))
        assertEquals(BreathingConfig(18.0, 5.8), ClassicLayout.breathingConfig(AnimationIntensity.CHAOTIC))
    }

    @Test
    fun `字号 clamp：2_25rem 下限 4_5rem 上限`() {
        // 视口很窄 → 取 6*s*vw/100（可能仍低于下限）
        assertEquals(36.0, ClassicLayout.pixelFontSize(1.0, 300.0), 1e-12)   // max(36, min(18,72)) = 36
        // 视口适中 → 取 6vw
        assertEquals(60.0, ClassicLayout.pixelFontSize(1.0, 1000.0), 1e-12)
        // 视口很宽 → 取上限 72
        assertEquals(72.0, ClassicLayout.pixelFontSize(1.0, 2000.0), 1e-12)
        // 缩放 1.3 倍
        assertEquals(46.8, ClassicLayout.pixelFontSize(1.3, 300.0), 1e-12)
    }

    @Test
    fun `small 字号 clamp：1_5rem 到 2_25rem`() {
        assertEquals(24.0, ClassicLayout.emptyPixelFontSize(1.0, 300.0), 1e-12)
        assertEquals(35.0, ClassicLayout.emptyPixelFontSize(1.0, 1000.0), 1e-12)
        assertEquals(36.0, ClassicLayout.emptyPixelFontSize(1.0, 2000.0), 1e-12)
    }

    @Test
    fun `确定性 RNG 落在 0 到 1（含 0 不含 1）`() {
        for (seed in 0..20) {
            val value = ClassicLayout.random(seed, 1.0)
            assertTrue(value >= 0.0 && value < 1.0)
        }
        // 同一输入稳定
        assertEquals(ClassicLayout.random(7, 4.0), ClassicLayout.random(7, 4.0), 1e-12)
    }

    @Test
    fun `calm 下没有散布与旋转`() {
        val l = line(100.0, 110.0, "静", words = listOf(Word("静", 100.0, 103.0)))
        val layout = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(40.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0, tuning = tuning, intensity = AnimationIntensity.CALM,
        )
        val word = layout.words[0]
        assertEquals(0.0, word.x, 1e-12)
        assertEquals(0.0, word.y, 1e-12)
        assertEquals(0.0, word.rotate, 1e-12)
        // passedRotate 与动画强度无关，只被旋转开关控制（Folia 行 262）
        assertTrue(kotlin.math.abs(word.passedRotate) <= 22.5 + 1e-9)
        assertEquals(LineJustify.CENTER, layout.line.justifyContent)
        assertEquals(LineAlign.CENTER, layout.line.alignItems)
        assertEquals(1000.0, layout.line.perspective, 1e-12)
    }

    @Test
    fun `chaotic 下透视随 seed 变化并有散布`() {
        val l = line(777.0, 780.0, "乱", words = listOf(Word("乱", 777.0, 778.0)))
        val layout = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(40.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0, tuning = tuning, intensity = AnimationIntensity.CHAOTIC,
        )
        assertEquals((500 + (777 % 500)).toDouble(), layout.line.perspective, 1e-12)
        assertTrue(layout.words[0].x != 0.0 || layout.words[0].y != 0.0)
    }

    @Test
    fun `关掉旋转开关后角度与 pass 旋转都归零`() {
        val l = line(12.0, 20.0, "词", words = listOf(Word("词", 12.0, 14.0), Word("句", 14.0, 16.0)))
        val layout = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(40.0, 40.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0,
            tuning = ClassicTuning(enableWordRotation = false), intensity = AnimationIntensity.NORMAL,
        )
        assertTrue(layout.words.all { it.rotate == 0.0 && it.passedRotate == 0.0 })
    }

    @Test
    fun `词距倍率按 margin 公式缩放`() {
        val words = listOf(Word("甲", 0.0, 1.0), Word("乙", 1.0, 2.0))
        val l = line(0.0, 2.0, "甲乙", words = words)

        fun marginOf(spacing: Double): Double = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(60.0, 60.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0,
            tuning = ClassicTuning(wordSpacing = spacing), intensity = AnimationIntensity.NORMAL,
        ).words[0].marginRight

        // 词距 0 时 margin 归零（所有项都乘 spacingMultiplier）
        assertEquals(0.0, marginOf(0.0), 1e-12)
        assertEquals(marginOf(0.7), marginOf(0.7), 1e-12)
        assertTrue(marginOf(1.4) > marginOf(0.7))
    }

    @Test
    fun `间奏行固定：居中 1_5 倍缩放 3rem 右距`() {
        val l = line(30.0, 40.0, "......", words = listOf(Word("...", 30.0, 40.0)))
        val layout = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(30.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0, tuning = tuning, intensity = AnimationIntensity.NORMAL,
        )
        assertEquals(0.0, layout.words[0].x, 1e-12)
        assertEquals(1.5, layout.words[0].scale, 1e-12)
        assertEquals(48.0, layout.words[0].marginRight, 1e-12)
        assertEquals(WordAlign.CENTER, layout.words[0].alignSelf)
        assertEquals(LineJustify.CENTER, layout.line.justifyContent)
    }

    @Test
    fun `呼吸关键帧：乘数 0 时不做动效`() {
        val l = line(0.0, 2.0, "甲", words = listOf(Word("甲", 0.0, 1.0)))
        val layout = ClassicLayout.buildLineLayout(
            l, wordWidthsPx = listOf(40.0), lyricsFontScale = 1.0,
            viewportWidthPx = 1000.0, remPx = 16.0, tuning = tuning, intensity = AnimationIntensity.NORMAL,
        )
        assertNull(ClassicLayout.breathingKeyframes(layout.copy(breathingMultiplier = 0.0)))

        val keyframes = ClassicLayout.breathingKeyframes(layout.copy(breathingMultiplier = 2.0))!!
        assertEquals(listOf(0.0, -28.0, 0.0, 12.6, 0.0), keyframes.ys)
        assertEquals(listOf(1.0, 1.02, 1.0, 0.99, 1.0), keyframes.scales)
        assertEquals(7.0, keyframes.durationSeconds, 1e-12)
    }

    @Test
    fun `字幕字号按倍率缩放`() {
        val sizes = ClassicLayout.resolveSubtitleFontSizes(1.3)
        assertEquals(1.125 * 1.3, sizes.translationMinRem, 1e-12)
        assertEquals(2.6 * 1.3, sizes.translationPreferredVw, 1e-12)
        assertEquals(0.875 * 1.3, sizes.upcomingMinRem, 1e-12)
        assertEquals(2.0 * 1.3, sizes.upcomingPreferredVw, 1e-12)
    }
}