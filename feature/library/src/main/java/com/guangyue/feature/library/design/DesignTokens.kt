package com.guangyue.feature.library.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 会随深浅色自动切换的语义色。
 *
 * [GuangYueDesign] 里的常量是「浅色主题的绝对色值」，直接拿去当背景/文字用，深色模式下必然
 * 白底白字。这里把它们映射成语义令牌：`GuangYueDesignTokens.Ink` 浅色返回 [GuangYueDesign.Ink]，
 * 深色返回 [GuangYueDesign.OnNavy]；卡片、描边、次级文字同理。
 *
 * 强调色只有一支：谷歌蓝 [#GuangYueDesign.Blue]。深浅两套都只在这一处取蓝色。
 */
object Gyp {
    /** 主文字。 */
    val ink: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFE8ECF7) else GuangYueDesign.Ink

    /** 次级文字 / 说明。 */
    val inkSoft: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF9AA6BE) else GuangYueDesign.InkSoft

    /** 卡片 / 悬浮胶囊底。 */
    val card: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF161E33) else GuangYueDesign.CardWhite

    /** 半透明卡片底（叠在渐变上）。 */
    val cardTranslucent: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xCC1B2438) else Color(0xCCFFFFFF)

    /** 卡片描边。 */
    val hairline: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0x22FFFFFF) else Color(0x14000000)

    /** 弱填充（图标底、进度槽）。 */
    val tint: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0x1F4C8DF6) else Color(0x141A73E8)

    /** 页面底色（渐变不可用时兜底）。 */
    val page: Color
        @Composable @ReadOnlyComposable get() = if (isDark) GuangYueDesign.NavyMid else GuangYueDesign.MarbleMid

    /** 唯一强调色。 */
    val accent: Color
        @Composable @ReadOnlyComposable get() = GuangYueDesign.Blue

    /** 强调色的文字/图标变体（深色下需要更亮一点才够对比）。 */
    val accentText: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF7FB0F7) else GuangYueDesign.BlueDeep

    /** 警示（仅用于错误文案，不是第二强调色）。 */
    val danger: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFF08A82) else Color(0xFFC0392B)

    /** 深色判定：与 [GuangYueDesign.Theme] 严格一致。 */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luma() < 0.4f
}

/** 统一圆角：卡片 18、行 14、胶囊 22、输入框 12。 */
object GyRadius {
    val card: Dp = 18.dp
    val row: Dp = 14.dp
    val capsule: Dp = 22.dp
    val field: Dp = 12.dp

    fun cardShape() = RoundedCornerShape(card)
    fun rowShape() = RoundedCornerShape(row)
    fun capsuleShape() = RoundedCornerShape(capsule)
    fun fieldShape() = RoundedCornerShape(field)
}

/** 统一间距节奏（4 的倍数）。 */
object GySpace {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val pageH: Dp = 20.dp
    val topSafe: Dp = 36.dp
}

private fun Color.luma(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
