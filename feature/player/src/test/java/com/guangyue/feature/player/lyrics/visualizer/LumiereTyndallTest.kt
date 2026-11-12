package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 细光路芯乘的丁达尔项与光场着色器同式：tyndall = base + (1 - base) × 烟密度 × density × fogScale。 */
class LumiereTyndallTest {
    private val fog = LumiereFog(density = 0.8, tyndallBase = 0.35, scale = 2.4, driftX = 0.0, driftY = 0.0, warp = 0.6, ambient = 0.05)

    @Test
    fun `无烟处只剩底亮度，浓烟处升到 base + (1-base)·density`() {
        assertEquals(0.35, lumiereTyndall(fog, 0.0), 1e-12)
        assertEquals(0.35 + 0.65 * 0.8, lumiereTyndall(fog, 1.0), 1e-12)
        assertEquals(0.35 + 0.65 * 0.5 * 0.8 * 0.5, lumiereTyndall(fog, 0.5, fogScale = 0.5), 1e-12)
    }

    @Test
    fun `随烟密度单调`() {
        var last = -1.0
        for (k in 0..10) {
            val v = lumiereTyndall(fog, k / 10.0)
            assert(v >= last)
            last = v
        }
    }
}
