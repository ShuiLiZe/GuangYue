package com.guangyue.music.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Folia 圆角令牌（`rounded-*` → dp：Tailwind 1 单位 = 4px）。
 *
 *  - `rounded-md` 6px → [sm]（Segmented.tsx:46 选中标记 radius 6）
 *  - `rounded-lg` 8px → [md]（Button.tsx:14、TextField.tsx:9、Segmented.tsx:33、Chips.tsx:33）
 *  - `rounded-xl` 12px → [lg]（SearchResultRow.tsx:91 封面 h-12 w-12 rounded-xl）
 *  - `rounded-2xl` 16px → [xl]（SearchResultRow.tsx:78 行卡片；inventory.md 第 0 / §1 节：
 *    首页卡片「移植统一 CornerSize(16.dp)」）
 *  - `rounded-full` → [pill]（Toggle 轨道、pill 页签、搜索框胶囊、滑条拇指）
 */
@Immutable
data class FoliaShapeTokens(
    val sm: RoundedCornerShape = RoundedCornerShape(6.dp),
    val md: RoundedCornerShape = RoundedCornerShape(8.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(12.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(16.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** 当前生效的圆角令牌。 */
val LocalFoliaShapes = staticCompositionLocalOf { FoliaShapeTokens() }

/**
 * Folia 间距令牌。数值同样是 Tailwind 档位（1 单位 = 4dp）：
 *
 *  - [x0_5] 2dp：Segmented.tsx:33 `p-0.5`
 *  - [x1] 4dp：Tabs.tsx:107 下划线的 `inset-x-1`
 *  - [x1_5] 6dp：MatchTheme 行内小间距、Slider.tsx:160 `mt-1.5`
 *  - [x2] 8dp：Chips.tsx:33 `pl-2`、SearchResultRow.tsx:54 `px-2`
 *  - [x2_5] 10dp：Row.tsx:88 `py-2.5`、EmptyState.tsx:15 `gap-2.5`、bravaisHome.css:18 `pad-x 10px`
 *  - [x3] 12dp：SearchResultRow.tsx:78 `gap-3`、Slider.tsx:166 `gap-3`
 *  - [x4] 16dp：Row.tsx:88 `pl-4 pr-4`、kit.css:62-70 行发丝线 `inset-left 16px`
 *  - [x6] 24dp：分区块之间（见 [sectionGap]，取自 bravaisHome.css:28 的 `--bravais-home-gap 18px`
 *    与 index.css 的区块留白，手机上取 24dp 一档）
 *
 * 手机化：整套 4dp 栅格的倍数保留，只把 `min-h-14`(56px) 的行压到 [rowMin] = 56dp → 用 48dp 起步，
 * 见 FoliaComponents.kt 的说明。
 */
@Immutable
data class FoliaSpacingTokens(
    val x0_5: Int = 2,
    val x1: Int = 4,
    val x1_5: Int = 6,
    val x2: Int = 8,
    val x2_5: Int = 10,
    val x3: Int = 12,
    val x4: Int = 16,
    val x6: Int = 24,
    /** 页面左右边距：bravaisHome.css:18 首页窄缝 `pad-x 10px`，手机整屏取 16dp。 */
    val screenPad: Int = 16,
    /** 分区之间：bravaisHome.css:28 `--bravais-home-gap 18px` → 手机上取 18dp。 */
    val sectionGap: Int = 18,
    /** 行最小触控高度：Android 无障碍下限 48dp（Material / W3C 目标尺寸）。 */
    val rowMin: Int = 48,
    /** 行垂直内边距：Row.tsx:88 `py-2.5` = 10dp。 */
    val rowPadY: Int = 10,
) {
    val x0_5Dp get() = x0_5.dp
    val x1Dp get() = x1.dp
    val x1_5Dp get() = x1_5.dp
    val x2Dp get() = x2.dp
    val x2_5Dp get() = x2_5.dp
    val x3Dp get() = x3.dp
    val x4Dp get() = x4.dp
    val x6Dp get() = x6.dp
    val screenPadDp get() = screenPad.dp
    val sectionGapDp get() = sectionGap.dp
    val rowMinDp get() = rowMin.dp
    val rowPadYDp get() = rowPadY.dp
}

/** 当前生效的间距令牌。 */
val LocalFoliaSpacing = staticCompositionLocalOf { FoliaSpacingTokens() }
