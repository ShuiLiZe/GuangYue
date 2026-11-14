package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 首页背后的动态背景，照 Folia 手机版：首页不暂停可视化背景（`disableHomeDynamicBackground` 默认关），
 * 所以网格首页背后就是播放页同一层「流体底色 + 当前背景效果（隐现点阵 / 通用几何）」，
 * 颜色取当前播放歌曲的封面；没在放歌时按 Folia「无封面」路径只画底色 + 渐变。
 */
@Composable
fun HomeVisualizerBackground(
    coverUrl: String?,
    dark: Boolean,
    latent: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasCover = !coverUrl.isNullOrBlank()
    val source = remember(coverUrl) { resolveCoverSource(coverUrl) }
    // 没在放歌：Folia 无封面路径是中性灰底，不能拿内置测试封面去取色（会染成粉色）。
    val coverTheme = rememberCoverBackdrop(source, dark)
    val theme = if (hasCover) coverTheme else remember(dark) { themeFromCover(null, dark).toPalette() }
    val palette = remember(theme) {
        CoverPalette(
            backgroundColor = theme.background,
            primaryColor = theme.primary,
            accentColor = theme.accent,
            secondaryColor = theme.secondary,
            fromCover = theme.fromCover,
        )
    }
    val reduceMotion = rememberReduceMotion()
    VisualizerBackdrop(
        palette = palette,
        source = if (hasCover) source else null,
        dark = dark,
        modifier = modifier.fillMaxSize(),
    ) {
        if (latent) {
            LatentBackgroundLayer(
                coverUrl = coverUrl,
                palette = palette,
                paused = false,
                staticMode = reduceMotion,
            )
        } else {
            GeometricBackgroundLayer(
                seed = (coverUrl ?: "").hashCode().toLong(),
                secondaryColor = theme.secondary,
                accentColor = theme.emphasis,
                paused = !playing || reduceMotion,
            )
        }
    }
}
