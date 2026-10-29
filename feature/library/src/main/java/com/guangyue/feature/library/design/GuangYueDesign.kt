package com.guangyue.feature.library.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.R

/**
 * 广乐的主题。
 *
 * 颜色与字号逐条取自 Folia（AGPL-3.0）：
 *  - 页面底色 / 文字色：`src/services/baseThemes.ts` 的 DEFAULT_THEME 与 DAYLIGHT_THEME，
 *    由 `src/services/themeStyle.ts` 的 buildAppStyle 写成 `--bg-color` 等变量。底色是纯色。
 *  - 设置与面板：`src/components/modal/settings/kit/kitTheme.ts`。
 *  - 字体：Folia 的 `--font-cjk-serif`（Noto Serif CJK SC / Source Han Serif SC / Songti SC…）
 *    在 App 里用打包的 Noto Serif SC 子集 [FontFamily 里的 serif]；正文用系统的 CJK 无衬线
 *    （`--font-cjk-sans` 的候选表，Android 上落到 Noto Sans CJK）。
 */
object GuangYueDesign {

    // ---- 页面（Folia baseThemes.ts） ------------------------------------------------------

    /** 午夜墨染：`--bg-color` #09090b。 */
    val Night = Color(0xFF09090B)

    /** 日光素白：`--bg-color` #f5f5f4。 */
    val Daylight = Color(0xFFF5F5F4)

    /** 深色 `--text-primary` #f4f4f5。 */
    val NightInk = Color(0xFFF4F4F5)

    /** 浅色 `--text-primary` #1c1917。 */
    val DaylightInk = Color(0xFF1C1917)

    /** 深色 `--text-secondary` #71717a。 */
    val NightInkSoft = Color(0xFF71717A)

    /** 浅色 `--text-secondary` #44403c。 */
    val DaylightInkSoft = Color(0xFF44403C)

    /** 强调色：深色下与正文同色 #f4f4f5，浅色下是 orange-600 #ea580c。 */
    val AccentNight = Color(0xFFF4F4F5)
    val AccentDaylight = Color(0xFFEA580C)

    /** 旧名保留，指向新的强调色（浅色一支），供已有调用点平滑过渡。 */
    val Blue = AccentDaylight
    val BlueDeep = Color(0xFFC2410C)

    /** 旧名保留：Folia 的页面是纯色，这几支只作历史引用，不再用于背景。 */
    val MarbleTop = Daylight
    val MarbleMid = Daylight
    val MarbleBottom = Daylight
    val MarbleWarm = Color(0xFFF5F5F4)
    val MarbleBlueTint = Color(0xFFF5F5F4)
    val Ink = DaylightInk
    val InkSoft = DaylightInkSoft
    val CardWhite = Color(0xFFFFFFFF)
    val NavyTop = Night
    val NavyMid = Night
    val NavyBottom = Night
    val OnNavy = NightInk

    // ---- 旧的手稿圆角（新代码用 GyRadius） ------------------------------------------------

    const val RadiusCapsule = 999
    const val RadiusTab = 10
    const val RadiusTabItem = 8
    const val RadiusPolaroid = 4

    /** Folia 的衬线：打包的 Noto Serif SC 子集（见 res/font/noto_serif_sc_subset_*.ttf）。 */
    val Serif = FontFamily(
        androidx.compose.ui.text.font.Font(R.font.noto_serif_sc_subset_400, FontWeight.Normal),
        androidx.compose.ui.text.font.Font(R.font.noto_serif_sc_subset_500, FontWeight.Medium),
        androidx.compose.ui.text.font.Font(R.font.noto_serif_sc_subset_500, FontWeight.SemiBold),
    )

    /** Folia 的正文：`--font-cjk-sans` 在 Android 上落到系统 Noto Sans CJK。 */
    val Sans = FontFamily.SansSerif

    private val light = lightColorScheme(
        primary = AccentDaylight,
        onPrimary = Color.White,
        secondary = DaylightInkSoft,
        onSecondary = Color.White,
        background = Daylight,
        onBackground = DaylightInk,
        surface = Color.White,
        onSurface = DaylightInk,
        surfaceVariant = Color(0xFFECECEF),
        onSurfaceVariant = DaylightInkSoft,
        outline = Color(0x141C1917),
        error = Color(0xFFD04444),
    )

    private val dark = darkColorScheme(
        primary = AccentNight,
        onPrimary = Color(0xFF17171A),
        secondary = NightInkSoft,
        onSecondary = NightInk,
        background = Night,
        onBackground = NightInk,
        surface = Color(0xFF232327),
        onSurface = NightInk,
        surfaceVariant = Color(0xFF2B2B30),
        onSurfaceVariant = NightInkSoft,
        outline = Color(0x14F4F4F5),
        error = Color(0xFFEF6F6F),
    )

    /**
     * 字号：Folia 的设置页标题 20sp/32、行标签 14sp/20、组标题与按钮 13sp/20、说明 12sp/18。
     * 衬线只用于大标题与「广乐」这类品牌字，正文与控件一律无衬线（与 Folia 一致）。
     */
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp),
        headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 27.sp),
        headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = GyText.title, lineHeight = GyText.titleHeight),
        titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = GyText.row, lineHeight = GyText.rowHeight),
        bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = Sans, fontSize = GyText.label, lineHeight = GyText.labelHeight),
        bodySmall = TextStyle(fontFamily = Sans, fontSize = GyText.hint, lineHeight = GyText.hintHeight),
        labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = GyText.label),
        labelSmall = TextStyle(fontFamily = Sans, fontSize = 11.sp),
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
