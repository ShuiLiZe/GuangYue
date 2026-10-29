package com.guangyue.feature.library.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 页面底色与毛玻璃。
 *
 * Folia 的底色是纯色（`--bg-color`：深色 #09090b、浅色 #f5f5f4），没有渐变、没有纹理，
 * 所以这里只画一层纯色。0.0.5 的大理石渐变连同柔光、纹理、点阵一并去掉。
 *
 * 毛玻璃对应 Folia 的 `backdrop-blur` + 半透明底：
 *  - Android 12（API 31）及以上且不是低内存设备：用 `Modifier.blur`（走 RenderEffect），
 *    底为 Folia 标题栏的两种半透明色之一。
 *  - 低于 12 或 `ActivityManager.isLowRamDevice`：只保留半透明底，不做模糊。
 *
 * Folia 的取值（`src/components/app/AppShell.tsx`）：标题栏磨砂 `backdrop-blur-md`
 * （浅色 `bg-white/65` + `border-black/5`，深色 `bg-black/40` + `border-white/10`），
 * 普通档 `backdrop-blur-sm`（浅色 `bg-white/18`，深色 `bg-white/12`）。
 */
object GyGlass {
    /** 面板磨砂底（`backdrop-blur-md`）：浅色 white/65，深色 black/40。 */
    val panel: Color
        @Composable @ReadOnlyComposable get() = if (Gyp.isDark) Color(0x66000000) else Color(0xA6FFFFFF)

    /** 轻档磨砂底（`backdrop-blur-sm`）：浅色 white/18，深色 white/12。 */
    val light: Color
        @Composable @ReadOnlyComposable get() = if (Gyp.isDark) Color(0x1FFFFFFF) else Color(0x2EFFFFFF)

    /** 磨砂面板的描边：浅色 black/5，深色 white/10。 */
    val border: Color
        @Composable @ReadOnlyComposable get() = if (Gyp.isDark) Color(0x1AFFFFFF) else Color(0x0D000000)

    /** 模糊半径：`backdrop-blur-md` 是 12px，Compose 的 blur 单位是 dp，取 12dp。 */
    val radius: Dp = 12.dp
}

/**
 * 这台设备能不能做模糊。
 *
 * 只在静态层用（底栏、悬浮胶囊、标题栏）；内容滚动时不要在滚动层里挂模糊。
 */
@Composable
@ReadOnlyComposable
fun canBlur(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return manager?.isLowRamDevice != true
}

/** 当前环境是否允许模糊，供非 Composable 的地方（如判断要不要画降级底）读取。 */
val LocalCanBlur = staticCompositionLocalOf { false }

/**
 * Folia 的页面底：一层纯色。
 *
 * 保留旧名字 `marbleBackground`，让已有调用点一行都不用改，画的东西已经是 Folia 的纯色底。
 */
@Composable
fun Modifier.marbleBackground(dark: Boolean = Gyp.isDark): Modifier =
    background(if (dark) GuangYueDesign.Night else GuangYueDesign.Daylight)

/** 页面底 + 内容。 */
@Composable
fun MarbleBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = Gyp.isDark,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().marbleBackground(dark)) { content() }
}

/**
 * 一块毛玻璃面板：模糊 + 半透明底 + 描边。
 *
 * 模糊用 [canBlur] 判断，低端机与 Android 12 以下自动降级为纯半透明。调用方要把这块放在
 * **静态层**（不随内容滚动重绘），例如底栏、迷你播放条、顶部标题栏。
 */
@Composable
fun Modifier.glassPanel(corner: Dp = GyRadius.card, alphaBoost: Float = 0f): Modifier {
    val blurOn = LocalCanBlur.current
    var chain = this
    if (blurOn) chain = chain.blur(GyGlass.radius)
    val base = GyGlass.panel
    val tinted = if (alphaBoost > 0f) base.copy(alpha = (base.alpha + alphaBoost).coerceAtMost(1f)) else base
    return chain.background(tinted, androidx.compose.foundation.shape.RoundedCornerShape(corner))
}
