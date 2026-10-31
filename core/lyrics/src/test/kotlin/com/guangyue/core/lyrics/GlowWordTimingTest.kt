// 对应 Folia 源码：src/components/visualizer/glowWordTiming.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class GlowWordTimingTest {

    private fun profileFor(start: Double, end: Double): GlowWordRenderProfile =
        GlowWordTiming.resolveGlowWordRenderProfile(
            Line(words = emptyList(), startTime = start, endTime = end, fullText = "测试")
        )!!

    @Test
    fun `唤醒提前量 instant 0_03 fast 0_08 normal 0_15`() {
        assertEquals(0.03, profileFor(0.0, 0.05).wordLookahead, 1e-12)
        assertEquals(0.08, profileFor(0.0, 0.15).wordLookahead, 1e-12)
        assertEquals(0.15, profileFor(0.0, 2.0).wordLookahead, 1e-12)
    }

    @Test
    fun `空行返回 null`() {
        assertEquals(null, GlowWordTiming.resolveGlowWordRenderProfile(null))
    }

    @Test
    fun `档位映射到过渡与显现模式`() {
        assertEquals(LineTransitionMode.NONE, profileFor(0.0, 0.05).lineTransitionMode)
        assertEquals(LineTransitionMode.FAST, profileFor(0.0, 0.15).lineTransitionMode)
        assertEquals(LineTransitionMode.NORMAL, profileFor(0.0, 2.0).lineTransitionMode)
    }

    @Test
    fun `instant 的 active 结束时间就是行渲染结束时间`() {
        val profile = profileFor(0.0, 0.05) // micro → 渲染结束 = max(0.05, 0.067) = 0.067
        val word = Word("a", 0.0, 0.05)
        assertEquals(profile.lineRenderEndTime, GlowWordTiming.getGlowWordActiveEndTime(word, profile), 1e-12)
    }

    @Test
    fun `fast 的 active 至少亮 0_12 但不越过行渲染结束`() {
        val profile = profileFor(0.0, 0.15)
        val word = Word("ab", 0.0, 0.05)
        // max(0.05, 0.0+0.12) = 0.12，与 lineRenderEnd 0.213 取小 → 0.12
        assertEquals(0.12, GlowWordTiming.getGlowWordActiveEndTime(word, profile), 1e-12)
    }

    @Test
    fun `normal 的 active 结束时间就是词结束时间`() {
        val profile = profileFor(0.0, 2.0)
        val word = Word("你好", 0.5, 1.1)
        assertEquals(1.1, GlowWordTiming.getGlowWordActiveEndTime(word, profile), 1e-12)
    }

    @Test
    fun `显示时长有最短下限`() {
        val instant = profileFor(0.0, 0.05)
        assertEquals(0.08, GlowWordTiming.getGlowWordDisplayDuration(Word("a", 0.0, 0.05), instant), 1e-12)

        val fast = profileFor(0.0, 0.15)
        assertEquals(0.12, GlowWordTiming.getGlowWordDisplayDuration(Word("ab", 0.0, 0.05), fast), 1e-12)

        val normal = profileFor(0.0, 2.0)
        assertEquals(0.1, GlowWordTiming.getGlowWordDisplayDuration(Word("你好", 1.0, 1.01), normal), 1e-12)
    }

    @Test
    fun `显示时长取实际时长当它更长时`() {
        val normal = profileFor(0.0, 2.0)
        assertEquals(0.6, GlowWordTiming.getGlowWordDisplayDuration(Word("你好", 1.0, 1.6), normal), 1e-12)
    }

    @Test
    fun `none 档的行容器动效照抄 Folia`() {
        val motion = GlowWordTiming.getGlowWordLineContainerMotion(profileFor(0.0, 0.05))
        assertEquals(1.0, motion.initialOpacity, 1e-12)
        assertEquals(1.0, motion.initialScale, 1e-12)
        assertEquals(0.0, motion.initialBlurPx, 1e-12)
        assertEquals(0.0, motion.exitOpacity, 1e-12)
        assertEquals(1.02, motion.exitScale, 1e-12)
        assertEquals(6.0, motion.exitBlurPx, 1e-12)
        assertEquals(0.12, motion.exitDuration, 1e-12)
    }

    @Test
    fun `fast 档的行容器动效照抄 Folia`() {
        val motion = GlowWordTiming.getGlowWordLineContainerMotion(profileFor(0.0, 0.15))
        assertEquals(0.35, motion.initialOpacity, 1e-12)
        assertEquals(0.96, motion.initialScale, 1e-12)
        assertEquals(4.0, motion.initialBlurPx, 1e-12)
        assertEquals(0.16, motion.animateDuration, 1e-12)
        assertEquals(1.04, motion.exitScale, 1e-12)
        assertEquals(10.0, motion.exitBlurPx, 1e-12)
        assertEquals(0.16, motion.exitDuration, 1e-12)
    }

    @Test
    fun `normal 档的行容器动效照抄 Folia`() {
        val motion = GlowWordTiming.getGlowWordLineContainerMotion(profileFor(0.0, 2.0))
        assertEquals(0.0, motion.initialOpacity, 1e-12)
        assertEquals(0.9, motion.initialScale, 1e-12)
        assertEquals(10.0, motion.initialBlurPx, 1e-12)
        assertEquals(1.1, motion.exitScale, 1e-12)
        assertEquals(20.0, motion.exitBlurPx, 1e-12)
        assertEquals(0.3, motion.exitDuration, 1e-12)
    }
}