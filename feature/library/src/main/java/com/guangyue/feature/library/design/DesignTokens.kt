package com.guangyue.feature.library.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Folia 的颜色令牌（逐条对应 Folia 的 CSS 变量）。
 *
 * 两处来源：
 *  - 页面：`src/services/baseThemes.ts` 的 DEFAULT_THEME（午夜墨染）与 DAYLIGHT_THEME（日光素白），
 *    由 `buildAppStyle` 写成 `--bg-color` / `--text-primary` / `--text-secondary` / `--text-accent`。
 *  - 设置窗口与面板：`src/components/modal/settings/kit/kitTheme.ts` 的 settingsTokens，
 *    `--st-*` 一族。
 *
 * Folia 的底色是**纯色**，没有渐变、没有大理石纹理；这里的 [page] 就是那几个十六进制值本身。
 */
object Gyp {
    // ---- 页面（baseThemes.ts） ------------------------------------------------------------

    /** `--bg-color`。深色 `#09090b`（zinc-950），浅色 `#f5f5f4`（stone-100）。 */
    val page: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF09090B) else Color(0xFFF5F5F4)

    /** `--text-primary`。深色 `#f4f4f5`（zinc-100），浅色 `#1c1917`（stone-900）。 */
    val ink: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1917)

    /** `--text-secondary`。深色 `#71717a`（zinc-500），浅色 `#44403c`（stone-700）。 */
    val inkSoft: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF71717A) else Color(0xFF44403C)

    /** `--text-accent`。深色 `#f4f4f5`，浅色 `#ea580c`（orange-600）。 */
    val accent: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFF4F4F5) else Color(0xFFEA580C)

    /** `--text-on-accent`：在强调色上取对比度更高的一支（kitTheme.ts 的 onAccentColor）。 */
    val onAccent: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF17171A) else Color(0xFFFFFFFF)

    // ---- 设置窗口 / 面板（kitTheme.ts settingsTokens） --------------------------------------

    /** `--st-card`：行的卡片底。深色 `#232327`，浅色 `#ffffff`。 */
    val card: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF232327) else Color(0xFFFFFFFF)

    /** `--st-menu`：浮层底。深色 `#2b2b30`，浅色 `#ffffff`。 */
    val menu: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF2B2B30) else Color(0xFFFFFFFF)

    /** `--st-raise`：选中标记（分段控件的滑块）底。深色 `#45454c`，浅色 `#ffffff`。 */
    val raise: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF45454C) else Color(0xFFFFFFFF)

    /** `--st-knob`：开关未选中的滑钮。深色 `#ececee`，浅色 `#ffffff`。 */
    val knob: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFECECEE) else Color(0xFFFFFFFF)

    /** `--st-pv`：预览块底。深色 `#151517`，浅色 `#ececef`。 */
    val preview: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF151517) else Color(0xFFECECEF)

    /** `--st-side`：设置页侧栏底。深色 `#141416`，浅色 `#e9e9eb`。 */
    val side: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF141416) else Color(0xFFE9E9EB)

    /** `--st-win`：设置窗口底。深色 `#1a1a1d`，浅色 `#f3f3f4`。 */
    val window: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF1A1A1D) else Color(0xFFF3F3F4)

    /** `--st-ok`。深色 `#56c98d`，浅色 `#1e9a5c`。 */
    val ok: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF56C98D) else Color(0xFF1E9A5C)

    /** `--st-warn`。深色 `#e3a949`，浅色 `#b97a10`。 */
    val warn: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFE3A949) else Color(0xFFB97A10)

    /** `--st-err` / 危险文案。深色 `#ef6f6f`，浅色 `#d04444`。 */
    val danger: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFFEF6F6F) else Color(0xFFD04444)

    // ---- 用 --text-primary 调出来的半透明层（kitTheme.ts 的 color-mix） ----------------------

    /** 旧名兼容：强调色上的文字（Folia 的 --st-on-accent）。 */
    val accentText: Color
        @Composable @ReadOnlyComposable get() = onAccent

    /** 旧名兼容：半透明卡片底（Folia 用 --st-card 加玻璃层，这里给 92% 的卡片底）。 */
    val cardTranslucent: Color
        @Composable @ReadOnlyComposable get() = card.copy(alpha = 0.92f)

    /** `--st-t2`：次级文字，`--text-primary` 62%。 */
    val t2: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.62f)

    /** `--st-t3`：三级文字，`--text-primary` 42%。 */
    val t3: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.42f)

    /** `--st-line`：发丝线，`--text-primary` 8%。 */
    val hairline: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.08f)

    /** `--st-line2`：更实的线，`--text-primary` 16%。 */
    val line2: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.16f)

    /** `--st-fill`：弱填充（按钮、字段、分段容器），`--text-primary` 7%。 */
    val tint: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.07f)

    /** `--st-fill2`：弱填充按下态，`--text-primary` 12%。 */
    val fill2: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.12f)

    /** `--st-track`：轨道（开关关、滑杆槽），`--text-primary` 17%。 */
    val track: Color
        @Composable @ReadOnlyComposable get() = ink.copy(alpha = 0.17f)

    /** 卡片描边：Folia 用 `shadow-[0_0_0_1px_var(--st-line)]` 画的 1px 环。 */
    val cardRing: Color
        @Composable @ReadOnlyComposable get() = hairline

    /** 深色判定，跟着 MaterialTheme 的 background 走（= Folia 的 is-daylight 取反）。 */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luma() < 0.4f
}

/**
 * Folia 的圆角。
 *
 * 来源：`kit/Group.tsx` 的 `rounded-xl`（卡片 12dp）、`kit/Row.tsx` 的 `rounded-[10px]`（整行可点）、
 * `kit/Button.tsx` 与 `kit/TextField.tsx` 的 `rounded-lg`（8dp）、`kit/Button.tsx` 的 quiet 档
 * `rounded-md`（6dp）、`Segments.tsx` 的 `l` 档（容器 10dp / 选项 8dp）。
 */
object GyRadius {
    /** 卡片（`rounded-xl`，Group.tsx 的 GROUP_CARD_CLASS）。 */
    val card: Dp = 12.dp

    /** 整行可点的响应区、分段容器 l 档。 */
    val row: Dp = 10.dp

    /** 按钮、输入框、字段表面（`rounded-lg`）。 */
    val field: Dp = 8.dp

    /** quiet 按钮、分段选项 m 档（`rounded-md`）。 */
    val quiet: Dp = 6.dp

    /** 分段选项 l 档的选中标记。 */
    val mark: Dp = 8.dp

    /** 胶囊（开关本身是 rounded-full）。 */
    val capsule: Dp = 999.dp

    fun cardShape() = RoundedCornerShape(card)
    fun rowShape() = RoundedCornerShape(row)
    fun fieldShape() = RoundedCornerShape(field)
    fun quietShape() = RoundedCornerShape(quiet)
    fun markShape() = RoundedCornerShape(mark)
    fun capsuleShape() = RoundedCornerShape(capsule)
}

/**
 * Folia 的间距。
 *
 * 来源：`kit/Row.tsx`（行 min-h 56dp、左右 padding 16dp、sub 缩进 40dp、上下 10dp、控件间距 8dp）、
 * `kit/Group.tsx`（组间距 28dp、首组 20dp、组标题下 8dp）、`kit/PageHeader.tsx`（页面标题 16/20dp）、
 * `panelTab/shell/Segments.tsx`（容器内边距 2dp、选项左右 10dp）。
 */
object GySpace {
    /** 2dp：分段容器内边距。 */
    val hairlinePad: Dp = 2.dp

    /** 4dp。 */
    val xxs: Dp = 4.dp

    /** 6dp：图标与文字之间（Segments 的 gap-1.5）。 */
    val xs: Dp = 6.dp

    /** 8dp：控件之间（Row 的 gap-2）。 */
    val s: Dp = 8.dp

    /** 10dp：行的上下内边距（Row 的 py-2.5）、选项左右内边距（px-2.5）。 */
    val m: Dp = 10.dp

    /** 12dp。 */
    val l: Dp = 12.dp

    /** 16dp：页面左右内边距、行的左右 padding（pl-4 / pr-4）。 */
    val pageH: Dp = 16.dp

    /** 24dp：行内标签与控件之间的距离（gap-x-6）。 */
    val xl: Dp = 24.dp

    /** 28dp：组与组之间（GROUP_SPACING_CLASS 的 mt-7）。 */
    val group: Dp = 28.dp

    /** 40dp：子行的缩进（pl-10）。 */
    val subIndent: Dp = 40.dp

    /** 旧名兼容：顶部安全区（0.0.5 的自绘留白，改为 WindowInsets 后仅作兜底）。 */
    val topSafe: Dp = 0.dp

    /** 行的最小高度（min-h-14）。 */
    val rowMin: Dp = 56.dp
}

/**
 * Folia 的字号与行高。
 *
 * 来源：`kit/Row.tsx`（标签 text-sm 14px / leading-5 20px，hint text-xs 12px / 18px）、
 * `kit/PageHeader.tsx`（标题 text-xl 20px / leading-8 32px）、`kit/Button.tsx`（13px / 12px）、
 * `kit/Group.tsx`（组标题 13px / 20px）、`Segments.tsx`（13px）、
 * `Lattice.css`（空状态 17px / letter-spacing .16em 与 12px）。
 */
object GyText {
    /** 组标题、按钮、分段、字段：13sp。 */
    val label: TextUnit = 13.sp
    val labelHeight: TextUnit = 20.sp

    /** 行标签：14sp / 20sp。 */
    val row: TextUnit = 14.sp
    val rowHeight: TextUnit = 20.sp

    /** 页面标题：20sp / 32sp。 */
    val title: TextUnit = 20.sp
    val titleHeight: TextUnit = 32.sp

    /** 说明、hint、footer：12sp / 18sp。 */
    val hint: TextUnit = 12.sp
    val hintHeight: TextUnit = 18.sp

    /** 空状态主句：17sp，字距 0.16em。 */
    val emptyTitle: TextUnit = 17.sp
    val emptyTitleSpacing: TextUnit = 2.72.sp

    /** 空状态副句：12sp。 */
    val emptySub: TextUnit = 12.sp
}

/**
 * Folia 的动效词表（`kit/motion.ts` 的 KIT_MOTION 与 KIT_EASE）。
 *
 * 所有组件只从这里取时长与曲线，不各自写数字。
 */
object GyMotion {
    /** 秒换算成毫秒：颜色变化、按下、工具提示。 */
    const val FAST_MS = 120

    /** 走一小段：开关滑钮、选中标记、页面进入。 */
    const val MOVE_MS = 220

    /** 行、说明、折叠组展开。 */
    const val UNFOLD_MS = 300

    /** 被揭开、或从一个地方移到另一个地方。 */
    const val SHOW_MS = 480

    /** 到达 / 位移：`cubic-bezier(0.22, 1, 0.36, 1)`。 */
    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** 颜色：`cubic-bezier(0.4, 0, 0.2, 1)`。 */
    val easeStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 一处离、一处落：`cubic-bezier(0.65, 0, 0.35, 1)`。 */
    val easeInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

private fun Color.luma(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
