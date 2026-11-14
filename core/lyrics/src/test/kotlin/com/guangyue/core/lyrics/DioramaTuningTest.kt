package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对照 Folia types.ts DEFAULT_DIORAMA_TUNING / diorama/tuning.ts resolveStoredDioramaTuning。 */
class DioramaTuningTest {
    @Test
    fun defaultsMatchFolia() {
        val d = DioramaTuning.DEFAULT
        assertEquals(576, d.particleDensity)
        assertEquals(0.65, d.particleGlowIntensity, 1e-12)
        assertEquals(28, d.backgroundParticleCircumference)
        assertEquals(2, d.backgroundParticleRadial)
        assertTrue(d.glowEnabled && d.soulEnabled && !d.soulActiveEnabled && !d.gradientEnabled && d.keywordColoringEnabled)
        assertFalse(d.geometryCorridor)
    }

    @Test
    fun sanitizeClampsToFoliaRanges() {
        val t = DioramaTuning.sanitize(
            DioramaTuning(cameraSpeed = 9.0, motionAmount = 0.0, audioReactivity = -1.0, particleDensity = 5000, particleScale = 0.1,
                particleGlowIntensity = 9.0, backgroundParticleCircumference = 1, backgroundParticleRadial = 9,
                glowIntensity = 0.0, soulIntensity = Double.NaN, gradientIntensity = 2.0),
        )
        assertEquals(1.85, t.cameraSpeed, 1e-12); assertEquals(0.4, t.motionAmount, 1e-12); assertEquals(0.0, t.audioReactivity, 1e-12)
        assertEquals(1536, t.particleDensity); assertEquals(0.65, t.particleScale, 1e-12); assertEquals(1.5, t.particleGlowIntensity, 1e-12)
        assertEquals(4, t.backgroundParticleCircumference); assertEquals(4, t.backgroundParticleRadial)
        assertEquals(0.1, t.glowIntensity, 1e-12); assertEquals(1.0, t.soulIntensity, 1e-12); assertEquals(1.5, t.gradientIntensity, 1e-12)
    }

    @Test
    fun effectiveStrengthsFollowToggles() {
        val t = DioramaTuning(glowEnabled = false, soulEnabled = false, soulActiveEnabled = true, gradientEnabled = true, gradientIntensity = 0.7)
        assertEquals(0.0, t.effectiveGlow, 0.0); assertEquals(0.0, t.effectiveSoul, 0.0)
        assertFalse(t.effectiveSoulActive); assertEquals(0.7, t.effectiveGradient, 1e-12)
    }

    @Test
    fun encodeDecodeRoundTripAndMissingKeysDefault() {
        val t = DioramaTuning(cameraSpeed = 1.3, geometryCorridor = true, rings = false, particleDensity = 960, showParticles = false,
            backgroundParticleCircumference = 40, soulActiveEnabled = true, gradientEnabled = true, gradientIntensity = 0.45)
        assertEquals(t, DioramaTuning.decode(t.encode()))
        assertEquals(DioramaTuning.DEFAULT, DioramaTuning.decode(null))
        assertEquals(DioramaTuning.DEFAULT.copy(particleDensity = 1536), DioramaTuning.decode("pd=99999;cs=abc;junk"))
        assertEquals(29, DioramaTuning.decode("bc=28.5").backgroundParticleCircumference)
    }
}
