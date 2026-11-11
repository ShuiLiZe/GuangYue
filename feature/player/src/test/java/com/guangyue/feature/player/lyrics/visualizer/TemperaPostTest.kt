package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 凝彩后处理公式对拍 Folia GLSL（金样：docs/port/scripts/tempera-post-golden-gen.mjs，float64 逐点算）。 */
class TemperaPostTest {
    @Test
    fun lensAndVignetteMatchFolia() {
        val golden = listOf(
            doubleArrayOf(0.0, 0.0, 2.2222, 0.054332363675, 0.054332363675, 0.510000000000),
            doubleArrayOf(1.0, 1.0, 0.45, 0.986264083000, 0.986264083000, 0.003773874891),
            doubleArrayOf(0.25, 0.8, 1.7778, 0.256583380203, 0.792099943756, 0.001259338648),
            doubleArrayOf(0.5, 0.5, 1.0, 0.5, 0.5, 0.0),
            doubleArrayOf(0.9, 0.1, 2.2222, 0.869062082857, 0.130937917143, 0.462713472227),
        )
        for (g in golden) {
            val (lu, lv) = temperaLensUv(g[0], g[1], g[2], TemperaPostDefaults.LENS_DISTORTION)
            assertEquals(g[3], lu, 1e-10)
            assertEquals(g[4], lv, 1e-10)
            assertEquals(g[5], temperaVignetteMix(g[0], g[1], g[2], TemperaPostDefaults.VIGNETTE), 1e-10)
        }
    }

    @Test
    fun noiseRandMatchesPixi() {
        assertEquals(0.237847638, temperaNoiseRand(0.5 * 0.4321, 0.5 * 0.4321), 1e-6)
        assertEquals(0.961466649, temperaNoiseRand(123.5 * 0.4321, 77.5 * 0.4321), 1e-6)
        assertEquals(0.105236872, temperaNoiseRand(1599.5 * 0.4321, 719.5 * 0.4321), 1e-6)
        assertEquals(0.07, TemperaPostDefaults.NOISE, 1e-12)
    }

    @Test
    fun seedAndBlurSigma() {
        // hashTemperaSeed 是无符号 32 位：-1 即 4294967295，% 10000 = 7295。
        assertEquals(0.7295, temperaNoiseSeed(-1L), 1e-12)
        assertEquals(0.1234, temperaNoiseSeed(1234L), 1e-12)
        // Pixi blur 6（shape-carry 最大）在 density 2 下 σ ≈ 20.64 px。
        assertEquals(6 * 1.29 / 0.75 * 2, temperaTransitionBlurSigma(6.0, 2.0), 1e-12)
    }
}
