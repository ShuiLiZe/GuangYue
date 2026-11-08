package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** R2：Folia `resolveLatentShaderColors(coverColors, theme, 'cover-theme')` 的对译。 */
class LatentColorsTest {
    private val palette = CoverPalette(
        backgroundColor = Color(0xFF09090B),
        primaryColor = Color(0xFFF4F4F5),
        accentColor = Color(0xFFE0B040),
        secondaryColor = Color(0xFF71717A),
        fromCover = false,
    )

    @Test
    fun noCoverFallsBackToTheme() {
        val c = latentShaderColors(emptyList(), palette)
        // ditheringBack = 背景，ditheringFront = secondary
        assertEquals(palette.backgroundColor, c[0])
        assertEquals(palette.secondaryColor, c[1])
        // mesh = [secondary, primary, secondary, primary, 背景, accent]
        assertEquals(
            listOf(palette.secondaryColor, palette.primaryColor, palette.secondaryColor, palette.primaryColor, palette.backgroundColor, palette.accentColor),
            c.drop(2),
        )
    }

    @Test
    fun coverColorsLeadTheMesh() {
        val a = Color.Red; val b = Color.Green; val d = Color.Blue
        val c = latentShaderColors(listOf(a, b, d), palette)
        assertEquals(a, c[1])
        assertEquals(listOf(a, b, d, b, palette.backgroundColor, palette.accentColor), c.drop(2))
    }
}
