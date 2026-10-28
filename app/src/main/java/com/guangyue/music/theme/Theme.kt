package com.guangyue.music.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.guangyue.feature.library.design.GuangYueDesign

/**
 * Light / dark switch for the whole app.
 *
 * The Google blue (#1A73E8) is a point colour only — selected pills, the progress bar, the
 * play button and the current lyric. Everything else stays paper-white or deep navy.
 */
val LocalGuangYueDark = staticCompositionLocalOf { false }

@Composable
fun GuangYueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = GuangYueDesign.Blue,
            onPrimary = Color.White,
            background = GuangYueDesign.NavyMid,
            onBackground = Color(0xFFE8EDF7),
            surface = Color(0xFF16203A),
            onSurface = Color(0xFFE8EDF7),
        )
    } else {
        lightColorScheme(
            primary = GuangYueDesign.Blue,
            onPrimary = Color.White,
            background = Color(0xFFF7F9FC),
            onBackground = GuangYueDesign.Ink,
            surface = Color.White,
            onSurface = GuangYueDesign.Ink,
        )
    }
    CompositionLocalProvider(LocalGuangYueDark provides darkTheme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
