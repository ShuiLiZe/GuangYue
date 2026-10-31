package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CoverTheme.kt（Folia coverPaletteAnalysis / themeColorRanges / generateBuiltinDualTheme）的单测。 */
class CoverThemeTest {

    // ---- ColorMath -------------------------------------------------------------------------

    @Test
    fun rgbToHsl_redIsZeroHueFullSaturation() {
        val hsl = ColorMath.rgbToHsl(Rgb(255, 0, 0))
        assertEquals(0.0, hsl.h, 0.01)
        assertEquals(1.0, hsl.s, 0.01)
        assertEquals(0.5, hsl.l, 0.01)
    }

    @Test
    fun rgbToHsl_grayHasNoSaturation() {
        val hsl = ColorMath.rgbToHsl(Rgb(128, 128, 128))
        assertEquals(0.0, hsl.s, 1e-9)
        assertEquals(128.0 / 255.0, hsl.l, 1e-9)
    }

    @Test
    fun hslToRgb_roundTripsThroughHsl() {
        val original = Rgb(213, 110, 66)
        val roundTrip = ColorMath.hslToRgb(ColorMath.rgbToHsl(original))
        assertEquals(original.r.toDouble(), roundTrip.r.toDouble(), 1.0)
        assertEquals(original.g.toDouble(), roundTrip.g.toDouble(), 1.0)
        assertEquals(original.b.toDouble(), roundTrip.b.toDouble(), 1.0)
    }

    @Test
    fun hslToRgb_wrapsNegativeHue() {
        assertEquals(
            ColorMath.hslToRgb(Hsl(350.0, 0.7, 0.5)),
            ColorMath.hslToRgb(Hsl(-10.0, 0.7, 0.5)),
        )
    }

    @Test
    fun contrastRatio_whiteOnBlackIs21() {
        assertEquals(21.0, ColorMath.contrastRatio(Rgb(255, 255, 255), Rgb(0, 0, 0)), 0.02)
    }

    @Test
    fun contrastRatio_isSymmetric() {
        val a = Rgb(20, 30, 40)
        val b = Rgb(240, 240, 230)
        assertEquals(ColorMath.contrastRatio(a, b), ColorMath.contrastRatio(b, a), 1e-9)
    }

    @Test
    fun hueDistance_isCircular() {
        assertEquals(20.0, ColorMath.hueDistance(350.0, 10.0), 1e-9)
    }

    @Test
    fun lerpHue_takesShortArcAcrossZero() {
        assertEquals(0.0, ColorMath.lerpHue(350.0, 10.0, 0.5), 1e-9)
    }

    @Test
    fun between_clampsAndInterpolates() {
        assertEquals(0.940, ColorMath.between(0.940..0.965, 0.0), 1e-9)
        assertEquals(0.965, ColorMath.between(0.940..0.965, 2.0), 1e-9)
        assertEquals(0.9525, ColorMath.between(0.940..0.965, 0.5), 1e-9)
    }

    // ---- CoverPaletteAnalyzer --------------------------------------------------------------

    @Test
    fun colorScore_peaksAtMidLightnessHighSaturation() {
        val vivid = CoverPaletteAnalyzer.colorScore(Hsl(18.0, 0.7, 0.5))
        val washed = CoverPaletteAnalyzer.colorScore(Hsl(18.0, 0.7, 0.97))
        assertTrue("vivid color must score higher than washed one", vivid > washed)
    }

    @Test
    fun pickBaseColor_returnsMostVividColor() {
        val colors = listOf(Hsl(200.0, 0.05, 0.9), Hsl(22.0, 0.72, 0.5), Hsl(300.0, 0.2, 0.8))
        assertEquals(22.0, CoverPaletteAnalyzer.pickBaseColor(colors)?.h ?: -1.0, 1e-9)
    }

    @Test
    fun pickBaseColor_emptyReturnsNull() {
        assertNull(CoverPaletteAnalyzer.pickBaseColor(emptyList()))
    }

    @Test
    fun pickSupportHue_requiresHueDistanceAndSaturation() {
        val near = listOf(Hsl(30.0, 0.8, 0.5))
        assertNull("hue distance below 25 is not a support hue", CoverPaletteAnalyzer.pickSupportHue(near, 22.0))
        val far = listOf(Hsl(260.0, 0.7, 0.4), Hsl(120.0, 0.2, 0.5))
        assertEquals(260.0, CoverPaletteAnalyzer.pickSupportHue(far, 22.0) ?: -1.0, 1e-9)
    }

    @Test
    fun pickScheme_duotoneWhenSupportAndLowRoll() {
        assertEquals(CoverScheme.DUOTONE, CoverPaletteAnalyzer.pickScheme(true, 0.6, 0.1))
    }

    @Test
    fun pickScheme_monochromeOnlyForLowSaturation() {
        val picked = (0..9).map { CoverPaletteAnalyzer.pickScheme(false, 0.1, it / 10.0) }
        assertTrue("low saturation may pick monochrome", CoverScheme.MONOCHROME in picked)
        val vivid = (0..9).map { CoverPaletteAnalyzer.pickScheme(false, 0.6, it / 10.0) }
        assertTrue("vivid cover must not pick monochrome", CoverScheme.MONOCHROME !in vivid)
    }

    @Test
    fun resolveAccentHue_monochromeKeepsBaseHue() {
        assertEquals(120.0, CoverPaletteAnalyzer.resolveAccentHue(CoverScheme.MONOCHROME, 120.0, null, 0.3), 1e-9)
    }

    @Test
    fun resolveAccentHue_complementaryOpposesBase() {
        val hue = CoverPaletteAnalyzer.resolveAccentHue(CoverScheme.COMPLEMENTARY, 10.0, null, 0.5)
        assertTrue("complementary huges must be far from base", ColorMath.hueDistance(hue, 10.0) > 120.0)
    }

    @Test
    fun analyze_returnsNullForEmptyInput() {
        assertNull(CoverPaletteAnalyzer.analyze(emptyList()))
    }

    @Test
    fun analyze_picksBaseAndCarriesSaturation() {
        val result = CoverPaletteAnalyzer.analyze(listOf(Rgb(30, 30, 30), Rgb(214, 111, 68)), rng = 0.42)
        requireNotNull(result)
        assertTrue("warm cover keeps saturation", result.baseSaturation > 0.3)
        assertTrue("warm cover base hue sits in orange band", result.baseHue in 10.0..40.0)
    }

    // ---- BuiltinDualTheme ------------------------------------------------------------------

    @Test
    fun coverWeight_saturatesTo1() {
        assertEquals(0.55, BuiltinDualTheme.coverWeight(0.0), 1e-9)
        assertEquals(1.0, BuiltinDualTheme.coverWeight(0.7), 1e-9)
    }

    @Test
    fun generate_lightBackgroundIsNearWhite() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68), Rgb(43, 38, 79)))
        val theme = BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42)
        val hsl = ColorMath.rgbToHsl(theme.backgroundColor)
        assertTrue("light background lightness >= 0.90, was ${hsl.l}", hsl.l >= 0.90)
        assertTrue("light background keeps cover saturation, was ${hsl.s}", hsl.s > 0.0)
    }

    @Test
    fun generate_darkBackgroundIsDeep() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68)))
        val theme = BuiltinDualTheme.generate(analysis, dark = true, rng = 0.42)
        val hsl = ColorMath.rgbToHsl(theme.backgroundColor)
        assertTrue("dark background lightness <= 0.16, was ${hsl.l}", hsl.l <= 0.16)
    }

    @Test
    fun generate_lightPrimaryMeetsContrast() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68), Rgb(43, 38, 79)))
        val theme = BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42)
        val contrast = ColorMath.contrastRatio(theme.primaryColor, theme.backgroundColor)
        assertTrue("primary contrast >= 9, was $contrast", contrast >= 9.0)
    }

    @Test
    fun generate_darkPrimaryMeetsContrast() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68)))
        val theme = BuiltinDualTheme.generate(analysis, dark = true, rng = 0.42)
        val contrast = ColorMath.contrastRatio(theme.primaryColor, theme.backgroundColor)
        assertTrue("dark primary contrast >= 9, was $contrast", contrast >= 9.0)
    }

    @Test
    fun generate_accentMeetsContrastOnBackground() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68), Rgb(60, 120, 90)))
        val theme = BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42)
        val contrast = ColorMath.contrastRatio(theme.accentColor, theme.backgroundColor)
        assertTrue("accent contrast >= 3.2, was $contrast", contrast >= 3.2)
    }

    @Test
    fun generate_accentIsNotPrimary() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68)))
        val theme = BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42)
        assertNotEquals(theme.primaryColor, theme.accentColor)
    }

    @Test
    fun generate_isDeterministicForSameInput() {
        val analysis = CoverPaletteAnalyzer.analyze(listOf(Rgb(214, 111, 68), Rgb(43, 38, 79)))
        assertEquals(
            BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42),
            BuiltinDualTheme.generate(analysis, dark = false, rng = 0.42),
        )
    }

    @Test
    fun generate_nullAnalysisFallsBack() {
        val theme = BuiltinDualTheme.generate(null, dark = false)
        assertTrue("fallback is not cover-driven", !theme.fromCover)
        assertTrue(ColorMath.contrastRatio(theme.primaryColor, theme.backgroundColor) >= 9.0)
    }

    @Test
    fun fallback_lightAndDarkDiffer() {
        val light = BuiltinDualTheme.fallback(dark = false)
        val dark = BuiltinDualTheme.fallback(dark = true)
        assertTrue(ColorMath.rgbToHsl(light.backgroundColor).l > 0.9)
        assertTrue(ColorMath.rgbToHsl(dark.backgroundColor).l < 0.15)
    }

    @Test
    fun adjustLightnessForContrast_keepsColorWhenAlreadyCompliant() {
        val bg = Rgb(250, 248, 245)
        val ink = Rgb(25, 25, 24)
        assertEquals(ink, BuiltinDualTheme.adjustLightnessForContrast(ColorMath.rgbToHsl(ink), bg, 9.0))
    }
}
