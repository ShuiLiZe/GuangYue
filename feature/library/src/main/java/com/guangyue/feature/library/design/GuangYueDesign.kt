package com.guangyue.feature.library.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * The one design system shared by every feature module.
 *
 * Google blue [#1A73E8] is an accent only: selected capsule, progress, play button, the lyric
 * being sung. Everything else is marble-white (light) or deep navy (dark / stage).
 */
object GuangYueDesign {

    val Blue = Color(0xFF1A73E8)
    val BlueDeep = Color(0xFF1257C4)

    // light — marble home / library / lyric flow
    val MarbleTop = Color(0xFFFBFCFE)
    val MarbleMid = Color(0xFFF1F4F9)
    val MarbleBottom = Color(0xFFEEF2F8)
    val MarbleWarm = Color(0xFFF7F3EF)
    val MarbleBlueTint = Color(0xFFEAF1FB)
    val Ink = Color(0xFF16202E)
    val InkSoft = Color(0xFF5B6676)
    val CardWhite = Color(0xFFFFFFFF)

    // dark — lyric stage / night mode
    val NavyTop = Color(0xFF0C1230)
    val NavyMid = Color(0xFF0B1026)
    val NavyBottom = Color(0xFF090E20)
    val OnNavy = Color(0xFFE8ECF7)

    // radii / spacing from the design handoff
    const val RadiusCapsule = 28
    const val RadiusTab = 22
    const val RadiusTabItem = 18
    const val RadiusPolaroid = 4

    private val light = lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        secondary = BlueDeep,
        background = MarbleMid,
        onBackground = Ink,
        surface = CardWhite,
        onSurface = Ink,
        surfaceVariant = Color(0xFFEFF2F8),
        onSurfaceVariant = InkSoft,
    )

    private val dark = darkColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        secondary = BlueDeep,
        background = NavyMid,
        onBackground = OnNavy,
        surface = Color(0xFF141A2E),
        onSurface = OnNavy,
        surfaceVariant = Color(0xFF1A2236),
        onSurfaceVariant = Color(0xFFB9C2D6),
    )

    /** Serif for headings, sans for everything else. */
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 27.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = TextStyle(fontSize = 15.sp),
        bodyMedium = TextStyle(fontSize = 13.sp),
        bodySmall = TextStyle(fontSize = 11.sp),
    )

    @Composable
    fun Theme(darkTheme: Boolean, content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = if (darkTheme) dark else light,
            typography = typography,
            content = content,
        )
    }
}
