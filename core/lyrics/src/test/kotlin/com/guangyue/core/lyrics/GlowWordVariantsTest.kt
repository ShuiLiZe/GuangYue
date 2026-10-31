// 对应 Folia 源码：src/components/visualizer/glowWordVariants.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowWordVariantsTest {

    private fun config(x: Double = 10.0, y: Double = 20.0, scale: Double = 1.0, rotate: Double = 3.0, passedRotate: Double = 5.0) =
        GlowWordConfig(id = "w", x = x, y = y, scale = scale, rotate = rotate, passedRotate = passedRotate, marginRight = 0.0)

    private fun profile(start: Double = 0.0, end: Double = 2.0): GlowWordRenderProfile =
        GlowWordTiming.resolveGlowWordRenderProfile(
            Line(words = emptyList(), startTime = start, endTime = end, fullText = "测试")
        )!!

    @Test
    fun `弹簧阻尼比由 damping 20 stiffness 200 质量 1 换算`() {
        val spec = SpringSpec(200.0, 20.0, 1.0)
        // dampingRatio = damping / (2 * sqrt(stiffness * mass))
        assertEquals(20.0 / (2.0 * kotlin.math.sqrt(200.0)), spec.dampingRatio, 1e-12)
        // Compose 侧取 Float，0.7071067811865476 的 Float 表示有 ~1e-8 误差
        assertEquals(0.7071067811865476, spec.toComposeDampingRatio().toDouble(), 1e-7)
    }

    @Test
    fun `waiting 布局：透明 半缩放 正弦余弦偏移 旋转加 20`() {
        val state = GlowWordVariants.layoutState(GlowWordStatus.WAITING, config(), AnimationIntensity.NORMAL)
        assertEquals(0.0, state.opacity, 1e-12)
        assertEquals(0.5, state.scale, 1e-12)
        assertEquals(10.0 + kotlin.math.sin(20.0) * 100.0, state.x, 1e-12)
        assertEquals(20.0 + kotlin.math.cos(10.0) * 50.0, state.y, 1e-12)
        assertEquals(23.0, state.rotate, 1e-12)
        assertEquals(0.4, state.durationSeconds, 1e-12)
    }

    @Test
    fun `waiting 关掉旋转时角度归零`() {
        val state = GlowWordVariants.layoutState(GlowWordStatus.WAITING, config(), AnimationIntensity.NORMAL, enableWordRotation = false)
        assertEquals(0.0, state.rotate, 1e-12)
    }

    @Test
    fun `active 布局：缩放乘 1_4 并带弹簧与 0_1 透明度过渡`() {
        val state = GlowWordVariants.layoutState(GlowWordStatus.ACTIVE, config(scale = 1.2), AnimationIntensity.NORMAL)
        assertEquals(1.0, state.opacity, 1e-12)
        assertEquals(1.2 * 1.4, state.scale, 1e-12)
        assertEquals(10.0, state.x, 1e-12)
        assertEquals(3.0, state.rotate, 1e-12)
        assertEquals(0.1, state.opacityDurationSeconds, 1e-12)
        assertEquals(200.0, state.spring!!.stiffness, 1e-12)
        assertEquals(20.0, state.spring!!.damping, 1e-12)
    }

    @Test
    fun `active 缩放是 NaN 时取 1_5`() {
        val state = GlowWordVariants.layoutState(GlowWordStatus.ACTIVE, config(scale = Double.NaN), AnimationIntensity.NORMAL)
        assertEquals(1.5, state.scale, 1e-12)
    }

    @Test
    fun `passed 布局：透明度 0_82（chaotic 0_9）与 5 秒线性旋转`() {
        val normal = GlowWordVariants.layoutState(GlowWordStatus.PASSED, config(), AnimationIntensity.NORMAL)
        assertEquals(0.82, normal.opacity, 1e-12)
        assertEquals(8.0, normal.rotate, 1e-12)
        assertEquals(0.5, normal.durationSeconds, 1e-12)
        assertEquals(5.0, normal.rotateDurationSeconds, 1e-12)
        assertEquals("linear", normal.rotateEase)

        val chaotic = GlowWordVariants.layoutState(GlowWordStatus.PASSED, config(), AnimationIntensity.CHAOTIC)
        assertEquals(0.9, chaotic.opacity, 1e-12)
    }

    @Test
    fun `正文层：waiting 基色模糊 10px；active 高亮色按档位清模糊；passed 回基色`() {
        assertEquals(10.0, GlowWordVariants.waitingBodyState.blurPx, 1e-12)
        assertEquals("__BASE__", GlowWordVariants.waitingBodyState.color)

        val active = GlowWordVariants.bodyState(GlowWordStatus.ACTIVE, WordRevealMode.INSTANT, 0.0)
        assertEquals("__ACTIVE__", active.color)
        assertEquals(0.2, active.colorDurationSeconds, 1e-12)
        assertEquals(0.08, active.filterDurationSeconds, 1e-12)

        assertEquals(0.12, GlowWordVariants.bodyState(GlowWordStatus.ACTIVE, WordRevealMode.FAST, 0.0).filterDurationSeconds, 1e-12)
        assertEquals(0.2, GlowWordVariants.bodyState(GlowWordStatus.ACTIVE, WordRevealMode.NORMAL, 0.0).filterDurationSeconds, 1e-12)

        val passed = GlowWordVariants.bodyState(GlowWordStatus.PASSED, WordRevealMode.NORMAL, 0.0)
        assertEquals("__BASE__", passed.color)
        assertEquals(0.8, passed.colorDurationSeconds, 1e-12)
        assertEquals(0.5, passed.filterDurationSeconds, 1e-12)
    }

    @Test
    fun `三态判定：提前量内 active，过后 passed，之前 waiting`() {
        val p = profile(0.0, 2.0)   // normal：lookahead 0.15
        val word = Word("你好", 1.0, 1.5)

        assertEquals(GlowWordStatus.WAITING, GlowWordVariants.resolveStatus(0.5, word, p))
        assertEquals(GlowWordStatus.ACTIVE, GlowWordVariants.resolveStatus(0.9, word, p))
        assertEquals(GlowWordStatus.ACTIVE, GlowWordVariants.resolveStatus(1.5, word, p))
        assertEquals(GlowWordStatus.PASSED, GlowWordVariants.resolveStatus(1.6, word, p))
    }

    @Test
    fun `涟漪缩放 1_5 到 3_5 与常量`() {
        assertEquals(1.5, GlowWordVariants.rippleScale(0.0), 1e-12)
        assertEquals(3.5, GlowWordVariants.rippleScale(1.0), 1e-12)
        assertEquals(2.5, GlowWordVariants.rippleScale(0.5), 1e-12)
        assertEquals(0.2, GlowWordVariants.RIPPLE_INITIAL_SCALE, 1e-12)
        assertEquals(0.8, GlowWordVariants.RIPPLE_INITIAL_OPACITY, 1e-12)
        assertEquals(0.5, GlowWordVariants.RIPPLE_DURATION, 1e-12)
    }

    @Test
    fun `行容器动效与 glowWordTiming 一致`() {
        val motion = GlowWordVariants.lineContainerMotion(profile(0.0, 2.0))
        assertEquals(0.9, motion.initialScale, 1e-12)
        assertTrue(motion.exitBlurPx == 20.0)
    }
}