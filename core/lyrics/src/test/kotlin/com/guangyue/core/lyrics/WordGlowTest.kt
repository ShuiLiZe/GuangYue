// 对应 Folia 源码：src/components/visualizer/wordGlow.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class WordGlowTest {

    @Test
    fun `辉光半径按档位 14-24 18-32 20-40`() {
        assertEquals(14.0 to 24.0, WordGlow.glowRadii(WordRevealMode.INSTANT))
        assertEquals(18.0 to 32.0, WordGlow.glowRadii(WordRevealMode.FAST))
        assertEquals(20.0 to 40.0, WordGlow.glowRadii(WordRevealMode.NORMAL))
    }

    @Test
    fun `drop-shadow 换算常量照抄 Folia`() {
        assertEquals(0.4, WordGlow.DROP_SHADOW_RADIUS_SCALE, 1e-12)
        assertEquals(0.7, WordGlow.DROP_SHADOW_INNER_ALPHA, 1e-12)
    }

    @Test
    fun `glowKeyframes 把峰值乘到内外半径上`() {
        val keyframes = WordGlow.glowKeyframes("rgba(255,255,255,1)", 20.0, 40.0, listOf(0.0, 1.0, 0.0))
        assertEquals(listOf(0.0, 20.0, 0.0), keyframes.innerRadii)
        assertEquals(listOf(0.0, 40.0, 0.0), keyframes.outerRadii)
    }

    @Test
    fun `waiting 恒透明且零时长`() {
        assertEquals("transparent", WordGlow.waitingVariant.color)
        assertEquals(0.0, WordGlow.waitingVariant.opacity, 1e-12)
        assertEquals(0.0, WordGlow.waitingVariant.transition.duration, 1e-12)
    }

    @Test
    fun `instant 闪烁关键帧与时长上限 0_12`() {
        val variant = WordGlow.activeVariant("rgba(255,0,0,1)", WordRevealMode.INSTANT, 0.3)
        assertEquals(listOf(0.0, 1.0, 0.0), variant.keyframes!!.peaks)
        assertEquals(0.12, variant.transition.duration, 1e-12)
        assertEquals(listOf(0.0, 0.35, 1.0), variant.transition.times)
        assertEquals("easeOut", variant.transition.ease)
    }

    @Test
    fun `instant 未给时长时取下限 0_08`() {
        val variant = WordGlow.activeVariant(null, WordRevealMode.INSTANT, 0.0)
        assertEquals(0.08, variant.transition.duration, 1e-12)
    }

    @Test
    fun `fast 时长钳在 0_12 到 0_2`() {
        assertEquals(0.12, WordGlow.activeVariant(null, WordRevealMode.FAST, 0.05).transition.duration, 1e-12)
        assertEquals(0.2, WordGlow.activeVariant(null, WordRevealMode.FAST, 0.9).transition.duration, 1e-12)
        assertEquals(0.15, WordGlow.activeVariant(null, WordRevealMode.FAST, 0.15).transition.duration, 1e-12)
    }

    @Test
    fun `normal 逐字扫光时长是字时长六倍并带拖延`() {
        val variant = WordGlow.activeVariant(
            null, WordRevealMode.NORMAL,
            duration = 1.2, index = 1, total = 3,
            charStartTime = 0.4, charEndTime = 0.5, wordStartTime = 0.2,
        )
        // charDuration = max(0.5-0.4, 0.001) = 0.1 → duration = 0.1*6 = 0.6
        assertEquals(0.6, variant.transition.duration, 1e-12)
        // charDelay = max(0, 0.4-0.2) = 0.2
        assertEquals(0.2, variant.transition.delay, 1e-12)
        assertEquals(listOf(0.0, 0.3, 1.0), variant.transition.times)
    }

    @Test
    fun `normal 逐字没有逐字时间时按均分推拖延`() {
        val variant = WordGlow.activeVariant(null, WordRevealMode.NORMAL, 0.9, index = 2, total = 3)
        // singleDuration = 0.9/3 = 0.3 → duration = 1.8，delay = 0.3*2 = 0.6
        assertEquals(1.8, variant.transition.duration, 1e-12)
        assertEquals(0.6, variant.transition.delay, 1e-12)
    }

    @Test
    fun `normal 单字持续辉光（不落回）`() {
        val variant = WordGlow.activeVariant(null, WordRevealMode.NORMAL, 0.5)
        assertEquals(listOf(0.0, 1.0, 1.0), variant.keyframes!!.peaks)
        assertEquals(0.5, variant.transition.duration, 1e-12)
        assertEquals(listOf(0.0, 0.9, 1.0), variant.transition.times)
        assertEquals("easeInOut", variant.transition.ease)
    }

    @Test
    fun `normal 单字未给时长时取 0_1`() {
        val variant = WordGlow.activeVariant(null, WordRevealMode.NORMAL, 0.0)
        assertEquals(0.1, variant.transition.duration, 1e-12)
    }

    @Test
    fun `passed 淡出时长 instant 0_12 fast 0_22 normal 0_9 且半径归零`() {
        val instant = WordGlow.passedVariant("#FF0000", WordRevealMode.INSTANT)
        assertEquals("#FF0000", instant.color)
        assertEquals(0.0, instant.opacity, 1e-12)
        assertEquals(0.12, instant.transition.duration, 1e-12)
        assertEquals(listOf(0.0), instant.keyframes!!.innerRadii)

        assertEquals(0.22, WordGlow.passedVariant(null, WordRevealMode.FAST).transition.duration, 1e-12)
        assertEquals(0.9, WordGlow.passedVariant(null, WordRevealMode.NORMAL).transition.duration, 1e-12)
    }

    @Test
    fun `variantFor 按状态分发`() {
        assertEquals("transparent", WordGlow.variantFor(GlowWordStatus.WAITING, null, WordRevealMode.NORMAL, 1.0).color)
        assertEquals(1.0, WordGlow.variantFor(GlowWordStatus.ACTIVE, null, WordRevealMode.NORMAL, 1.0).opacity, 1e-12)
        assertEquals(0.0, WordGlow.variantFor(GlowWordStatus.PASSED, null, WordRevealMode.NORMAL, 1.0).opacity, 1e-12)
    }
}