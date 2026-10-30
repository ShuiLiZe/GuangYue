package com.guangyue.music.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Folia 字体令牌。
 *
 * 字体栈：`src/index.css:14-17` —— `--font-cjk-sans`（Noto Sans CJK SC / Source Han Sans SC /
 * PingFang SC / …）在前，`--font-sans: ui-sans-serif, system-ui, <cjk>, sans-serif`。
 * Android 上没有 Inter / PingFang，落到系统无衬线（Roboto + 系统中文字体 Noto Sans CJK），
 * 即 [FontFamily.SansSerif]；`headline` 用同一栈（Folia 正文与标题都是 sans，
 * `--font-serif` 只在歌词 / 海报里局部改，见 index.css:14-17 第三行）。
 *
 * ## 手机化：桌面小字整体上抬一档（m00169 要求正文 ≥14sp、次要 ≥12sp）
 * 
 *  Folia 原值 → 手机值：10px tag → 12sp；11px micro → 12sp；	ext-xs 12px 副题 → 12sp（保底）；
 *  	ext-[13px] 正文/控件 → 14sp；	ext-sm 14px 标题 → 16sp；15px 分区标题 → 16sp。
 *  理由：360dp 宽的屏上 13px ≈ 3.6mm 字高，比单手阅读舒适区小；iOS/Android 正文基线都是 14–17sp。
 *
 * 原始档位出处：
 *  - `text-[10px]` → 12sp（SearchResultRow.tsx:104 标签 chip）
 *  - `text-[11px] leading-[14px]` → 12/16（Slider.tsx:103 刻度名）
 *  - `text-xs` = 12px / `leading-[18px]` → 12/18（Row.tsx:134、SearchResultRow 副题）
 *  - `text-[13px] leading-5` → 14/22（Button.tsx:14-18、TextField.tsx:12、EmptyState.tsx:17）
 *  - `text-sm leading-5` → 16/22（Row.tsx:113、SearchResultRow.tsx:100 标题）
 *  - `15px/800/1.3` 字距 .3em → 16/22/800（bravaisHome.css:89-95 窄缝标题）
 *  - 详情页标题 22–24sp（inventory.md §3）
 */
@Immutable
data class FoliaTypeTokens(
    /** 顶部大字标题，22–24sp / 700（inventory §3）。 */
    val headline: TextStyle,
    /** 分区小标题 15sp/800，字距 .3em（bravaisHome.css:89-95）。 */
    val section: TextStyle,
    /** 列表主标题 Folia `text-sm` 14px/600 → 手机 16sp/600 / 行高 22（Row.tsx:113、SearchResultRow.tsx:100）。 */
    val bodyStrong: TextStyle,
    /** 正文 Folia 13px → 手机 14sp / 行高 22（EmptyState.tsx:17 的 text-[13px] leading-5）。 */
    val body: TextStyle,
    /** 次要说明 12sp / 行高 18（Row.tsx:134）。 */
    val caption: TextStyle,
    /** 控件文字 Folia 13px → 手机 14sp（Button.tsx:14-18、TextField.tsx:12）。 */
    val control: TextStyle,
    /** 小标签 Folia 10px → 手机 12sp（SearchResultRow.tsx:104 的 text-[10px]），12sp 是次要文字下限。 */
    val tag: TextStyle,
    /** 刻度名 Folia 11px → 手机 12sp / 行高 16（Slider.tsx:103）。 */
    val micro: TextStyle,
    /** 等宽数字读数（Slider.tsx:168 的 tabular-nums；Android 用 FontFamily.Monospace 近似）。 */
    val numeric: TextStyle,
)

private val Sans = FontFamily.SansSerif

val FoliaLightType = FoliaTypeTokens(
    headline = TextStyle(fontFamily = Sans, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    section = TextStyle(
        fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.3.sp,
    ),
    bodyStrong = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    control = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    tag = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    micro = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    numeric = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
)

/** 深色一套字号与浅色完全相同（Folia 的主题只换色，不换字，baseThemes.ts:31-34）。 */
val FoliaDarkType: FoliaTypeTokens = FoliaLightType

/** 当前生效的字体令牌。 */
val LocalFoliaType = staticCompositionLocalOf { FoliaLightType }
