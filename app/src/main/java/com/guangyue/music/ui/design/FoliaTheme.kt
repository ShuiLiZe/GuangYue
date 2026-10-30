package com.guangyue.music.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import android.os.Build

/**
 * 广乐 U1 的设计地基主题：把 Folia 的 token 装进 Compose。
 *
 * 和 0.0.8 里已有的 `com.guangyue.music.theme.GuangYueTheme`（蓝点色、`LocalGuangYueDark`）
 * **并存不冲突**：那个主题继续服务现有页面，[FoliaTheme] 是照 Folia 源码重建的一套，
 * 后面每一页迁移过来时用这一套。两者只共享 Material3 的 `MaterialTheme` 容器，
 * 不会互相改值（[FoliaTheme] 内部自己选 light/dark scheme）。
 *
 * Folia 的主题切换只有一个 `daylight` 布尔（`src/services/baseThemes.ts:31-34`
 * `BASE_DUAL_THEME { light: DAYLIGHT_THEME, dark: DEFAULT_THEME }`，没有跟随系统媒体查询），
 * 所以这里用显式 [dark] 参数；默认值跟系统走只是手机上的便利，不改变 token 本身。
 */
@Composable
fun FoliaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) FoliaDarkColors else FoliaLightColors
    val type = if (dark) FoliaDarkType else FoliaLightType
    val shapes = FoliaShapeTokens()
    val spacing = FoliaSpacingTokens()
    val blur = FoliaBlurTokens()

    val scheme = remember(dark) {
        if (dark) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.inkSoft,
                background = colors.page,
                onBackground = colors.ink,
                surface = colors.card,
                onSurface = colors.ink,
                surfaceVariant = colors.raise,
                onSurfaceVariant = colors.inkSoft,
                outline = colors.lineStrong,
                outlineVariant = colors.line,
                error = colors.error,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.inkSoft,
                background = colors.page,
                onBackground = colors.ink,
                surface = colors.card,
                onSurface = colors.ink,
                surfaceVariant = colors.raise,
                onSurfaceVariant = colors.inkSoft,
                outline = colors.lineStrong,
                outlineVariant = colors.line,
                error = colors.error,
            )
        }
    }

    val typography = remember(dark) {
        Typography(
            headlineLarge = type.headline,
            titleMedium = type.bodyStrong,
            bodyMedium = type.body,
            bodySmall = type.caption,
            labelLarge = type.control,
            labelSmall = type.tag,
        )
    }

    CompositionLocalProvider(
        LocalFoliaColors provides colors,
        LocalFoliaType provides type,
        LocalFoliaShapes provides shapes,
        LocalFoliaSpacing provides spacing,
        LocalFoliaBlur provides blur,
        LocalFoliaBlurEnabled provides (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
        LocalFoliaDark provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** 当前是否为深色一套（Folia 的 `isDaylight` 取反）。 */
val LocalFoliaDark = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 取值便捷入口，等价于 `LocalFoliaColors.current`。 */
object Folia {
    val colors: FoliaColorTokens
        @Composable @ReadOnlyComposable get() = LocalFoliaColors.current
    val type: FoliaTypeTokens
        @Composable @ReadOnlyComposable get() = LocalFoliaType.current
    val shapes: FoliaShapeTokens
        @Composable @ReadOnlyComposable get() = LocalFoliaShapes.current
    val spacing: FoliaSpacingTokens
        @Composable @ReadOnlyComposable get() = LocalFoliaSpacing.current
    val blur: FoliaBlurTokens
        @Composable @ReadOnlyComposable get() = LocalFoliaBlur.current
    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalFoliaDark.current
}

/** 便利：把 token 的 TextStyle 直接当样式用。 */
@Composable
@ReadOnlyComposable
fun textStyleOf(token: TextStyle): TextStyle = token
