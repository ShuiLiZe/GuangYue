// 对应 Folia 源码：src/components/visualizer/colorMix.ts + backgrounds/useBlendedColors.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorMixTest {

    @Test
    fun `easeOut 三次曲线`() {
        assertEquals(0.0, ColorMix.easeOut(0.0), 1e-12)
        assertEquals(1.0, ColorMix.easeOut(1.0), 1e-12)
        assertEquals(1.0 - 0.5 * 0.5 * 0.5, ColorMix.easeOut(0.5), 1e-12)
    }

    @Test
    fun `解析三位与六位十六进制`() {
        assertEquals(ColorMix.Rgb(255, 0, 0), ColorMix.parseColor("#f00"))
        assertEquals(ColorMix.Rgb(18, 52, 86), ColorMix.parseColor("#123456"))
        assertEquals(ColorMix.Rgb(255, 255, 255), ColorMix.parseColor("#FFFFFF"))
    }

    @Test
    fun `解析 rgb 与 rgba 函数形式`() {
        assertEquals(ColorMix.Rgb(10, 20, 30), ColorMix.parseColor("rgb(10, 20, 30)"))
        assertEquals(ColorMix.Rgb(10, 20, 30), ColorMix.parseColor("rgba(10, 20, 30, 0.5)"))
        assertNull(ColorMix.parseColor("rebeccapurple"))
        assertNull(ColorMix.parseColor(""))
    }

    @Test
    fun `辉光强度是 smoothstep，暗色被抑制`() {
        // value = 110 → progress 0 → 0
        assertEquals(0.0, ColorMix.resolveGlowColorStrength(110, 110, 110), 1e-12)
        // value = 160 → progress 1 → 1
        assertEquals(1.0, ColorMix.resolveGlowColorStrength(160, 160, 160), 1e-12)
        // value = 135 → progress 0.5 → 0.5
        assertEquals(0.5, ColorMix.resolveGlowColorStrength(135, 135, 135), 1e-12)
        // 取最大值通道
        assertEquals(1.0, ColorMix.resolveGlowColorStrength(0, 0, 255), 1e-12)
    }

    @Test
    fun `混色按比例插值`() {
        assertEquals(ColorMix.Rgb(0, 0, 0), ColorMix.mixColors(ColorMix.Rgb(0, 0, 0), ColorMix.Rgb(255, 255, 255), 0.0))
        assertEquals(ColorMix.Rgb(255, 255, 255), ColorMix.mixColors(ColorMix.Rgb(0, 0, 0), ColorMix.Rgb(255, 255, 255), 1.0))
        assertEquals(ColorMix.Rgb(127, 127, 127), ColorMix.mixColors(ColorMix.Rgb(0, 0, 0), ColorMix.Rgb(255, 255, 255), 0.5))
    }

    @Test
    fun `换歌柔焦换色：同色不动、零时长立刻切、长度不等立刻切`() {
        val from = listOf("#000000", "#FFFFFF")
        val to = listOf("#111111", "#EEEEEE")
        // 同色不动
        assertEquals(from, ColorMix.blendColors(from, from, 600L, 100L))
        // Folia: durationMs <= 0 → 立刻切到目标色
        assertEquals(to, ColorMix.blendColors(from, to, 0L, 100L))
        // 长度不等 → 立刻切到目标色
        assertEquals(listOf("#111111"), ColorMix.blendColors(from, listOf("#111111"), 600L, 100L))
    }

    @Test
    fun `换歌柔焦换色：中途按 easeOut 插值`() {
        val blended = ColorMix.blendColors(listOf("#000000"), listOf("#FFFFFF"), durationMs = 600L, elapsedMs = 300L)
        // easeOut(0.5) = 0.875 → 0xDF
        assertEquals("#DFDFDF", blended[0])
    }

    @Test
    fun `换歌柔焦换色：走完时长后就是目标色`() {
        val blended = ColorMix.blendColors(listOf("#000000"), listOf("#FFFFFF"), durationMs = 600L, elapsedMs = 600L)
        assertEquals("#FFFFFF", blended[0])
    }
}