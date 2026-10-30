package com.guangyue.music.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build

/**
 * Folia 毛玻璃面（backdrop blur）�? *
 * 源码里真正的 `backdrop-blur` 只有两处�? *  - `src/components/app/carousel/Carousel3D.tsx:405` —�?`bg-white/80 | bg-black/80 backdrop-blur-md`�?2px�? *  - `src/components/app/player/FloatingPlayerControls.tsx:384` —�?`backdrop-blur-xl`�?4px�? * 另加 `.theme-glass-panel`（`src/index.css:163-168`）：半透明�?+ 12% 描边（不依赖背后是否模糊）�? *
 * ## Android 侧的三档处理
 *
 *  1. **API 31+（Android 12�?*：`Modifier.blur(radius)` = `RenderEffect.createBlurEffect` +
 *     `View.setRenderEffect`，对施加层的内容做高斯模糊。把墙面图案垫在玻璃面下面并给它�?blur�? *     就等价于 CSS `backdrop-blur`�? *  2. **API 26�?0**：没�?RenderEffect，低版本 `Modifier.blur` 只作用于图层且代价高，故**不模�?*�? *     玻璃�?= 半透明底（[FoliaColorTokens.glassFill]，`--bg-color` 85%�? 12% 描边�? *     背后色块直接透出，观感仍是玻璃片�? *  3. **测试 / Robolectric**：同 API 26�?0 分支，保证截图稳定可复现�? *
 * 判定入口�?[LocalFoliaBlurEnabled]（由 [FoliaTheme] �?`SDK_INT >= 31` 提供）�? */

/** 模糊半径令牌�?*/
data class FoliaBlurTokens(
    /** Carousel3D.tsx:405 �?`backdrop-blur-md` = 12px �?12dp�?*/
    val md: Dp = 12.dp,
    /** FloatingPlayerControls.tsx:384 �?`backdrop-blur-xl` = 24px �?24dp�?*/
    val xl: Dp = 24.dp,
) {
    /** 当前系统能否做真模糊（Android 12+）�?*/
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

val LocalFoliaBlur = androidx.compose.runtime.staticCompositionLocalOf { FoliaBlurTokens() }

/** 系统是否支持真模糊。组件据此选择「模�?+ 深底」还是「半透底」�?*/
val LocalFoliaBlurEnabled = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 常用胶囊形状�?*/
val GlassPillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/** 玻璃面外观：clip + 半透明�?+ 1dp 描边（index.css:163-168）。只负责「面」�?*/
fun Modifier.foliaGlassSurface(
    shape: Shape,
    fill: Color,
    border: Color,
): Modifier = this
    .clip(shape)
    .background(fill)
    .androidBorder(1.dp, border, shape)

internal fun Modifier.androidBorder(width: Dp, color: Color, shape: Shape): Modifier =
    this.drawBehind {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = width.toPx()),
        )
    }
/**
 * 背景模糊层：Android 12+ 对内容做 `Modifier.blur(radius)`；低版本原样层（不模糊，玻璃面靠半透明底）。
 * 用法：把要透出的色块 / 图放进 [content]，再在上面盖玻璃面。
 */
@Composable
fun FoliaBlurLayer(
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val enabled = LocalFoliaBlurEnabled.current
    Box(modifier = modifier.then(if (enabled) Modifier.blur(radius) else Modifier), content = content)
}

/** 允许子树强制开关模糊（Gallery 里演示低版本降级观感用）�?*/
@Composable
fun ProvideFoliaBlurEnabled(enabled: Boolean, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalFoliaBlurEnabled provides enabled, content = content)

/**
 * Gallery / 测试用背景：模拟 Folia 页面背后的「墙面」�? *
 * 源：`src/library/suites/wall/wall.css:7-32` `.lattice-root` —�? * `radial-gradient(accent 16% at 14% 12%)`、`radial-gradient(secondary 20% at 86% 82%)` 叠在 `--bg-color` 上�? * 注意：源码的 16% / 20% �?*不透明�?*，这里照搬；两团光的中心�?14%/12%�?6%/82% 坐标按比例换算成
 * Compose �?`Offset`。这只是 U1 的占位背景，真正的墙面由后续页面段落实现�? */
@Composable
fun FoliaWallBackdrop(modifier: Modifier = Modifier) {
    val c = Folia.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.page)
            .background(
                Brush.radialGradient(
                    colors = listOf(c.accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset.Unspecified,
                ),
            ),
    )
}
