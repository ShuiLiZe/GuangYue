package com.guangyue.music.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Folia 颜色令牌，浅 / 深两套。
 *
 * 取值原则：**只用 Folia 源码里出现的十六进制值或其 alpha 派生**，不做肉眼调色。
 * 每个颜色旁注明出处（文件:行）。`Color.copy(alpha = x)` 对应源码里的
 * `color-mix(in srgb, <color> x%, transparent)`。
 *
 * 来源总览：
 *  - 页面底色 / 文字：`src/services/baseThemes.ts:9-34`（午夜墨染 DEFAULT_THEME、日光素白 DAYLIGHT_THEME），
 *    由 `src/components/app/presentation/buildAppStyle.ts:35-43` 写成 `--bg-color` / `--text-*` / `--text-on-accent`。
 *  - 设置窗口 / 面板 / 控件：`src/components/modal/settings/kit/kitTheme.ts:24-48`（`--st-*` 一族）。
 *  - 毛玻璃面板：`src/index.css:163-168`（`.theme-glass-panel`）。
 */
@Immutable
data class FoliaColorTokens(
    // ---- 页面（baseThemes.ts） ----------------------------------------------------------
    /** `--bg-color`：深色 zinc-950（baseThemes.ts:10），浅色 stone-100（baseThemes.ts:21）。 */
    val page: Color,
    /** `--text-primary`：深色 zinc-100（baseThemes.ts:11），浅色 stone-900（baseThemes.ts:22）。 */
    val ink: Color,
    /** `--text-secondary`：深色 zinc-500（baseThemes.ts:13），浅色 stone-700（baseThemes.ts:23）。 */
    val inkSoft: Color,
    /** `--text-accent`：深色 zinc-100（baseThemes.ts:12），浅色 orange-600（baseThemes.ts:24）。 */
    val accent: Color,
    /** `--text-on-accent`：buildAppStyle.ts:12-16 取与 accent 对比度高者 → 深色页 #17171a，浅色页 #ffffff。 */
    val onAccent: Color,

    // ---- 设置 / 面板（kitTheme.ts `--st-*`） --------------------------------------------
    /** `--st-win`：窗口底色，kitTheme.ts:28（浅 #f3f3f4 / 深 #1a1a1d）。 */
    val win: Color,
    /** `--st-side`：侧栏底色，kitTheme.ts:29（#e9e9eb / #141416）。 */
    val side: Color,
    /** `--st-card`：卡片底，kitTheme.ts:30（#ffffff / #232327）。 */
    val card: Color,
    /** `--st-menu`：菜单／弹层底，kitTheme.ts:31（#ffffff / #2b2b30）。 */
    val menu: Color,
    /** `--st-raise`：抬起面（分段选中标记、旋钮槽），kitTheme.ts:32（#ffffff / #45454c）。 */
    val raise: Color,
    /** `--st-knob`：开关未选的旋钮，kitTheme.ts:33（#ffffff / #ececee）。 */
    val knob: Color,
    /** `--st-pv`：预览底板，kitTheme.ts:34（#ececef / #151517）。 */
    val preview: Color,
    /** `--st-ok`：成功色，kitTheme.ts:35（#1e9a5c / #56c98d）。 */
    val ok: Color,
    /** `--st-warn`：警告色，kitTheme.ts:36（#b97a10 / #e3a949）。 */
    val warn: Color,
    /** `--st-err`：错误色，kitTheme.ts:37（#d04444 / #ef6f6f）。 */
    val error: Color,

    // ---- alpha 派生（kitTheme.ts:38-43 的 color-mix 百分比） ------------------------------
    /** `--st-t2` = text-primary 62%：次要文字，kitTheme.ts:39。 */
    val text2: Color,
    /** `--st-t3` = text-primary 42%：占位 / 弱化文字，kitTheme.ts:40。 */
    val text3: Color,
    /** `--st-line` = text-primary 8%：发丝分隔线，kitTheme.ts:41。 */
    val line: Color,
    /** `--st-line2` = text-primary 16%：强调描边，kitTheme.ts:42。 */
    val lineStrong: Color,
    /** `--st-fill` = text-primary 7%：静默填充（按钮 fill 底、行 hover 底），kitTheme.ts:43。 */
    val fill: Color,
    /** `--st-fill2` = text-primary 12%：填充加重（行按压），kitTheme.ts:44。 */
    val fillStrong: Color,
    /** `--st-track` = text-primary 17%：滑条 / 开关轨道，kitTheme.ts:45。 */
    val track: Color,

    // ---- 页面上其它带 alpha 的面（css 里的 color-mix） -----------------------------------
    /** 毛玻璃面板底：`--bg-color` 85%（index.css:164）。 */
    val glassFill: Color,
    /** 毛玻璃面板描边：`--text-primary` 12%（index.css:165）。 */
    val glassBorder: Color,
    /**
     * 拍立得卡片底。
     *
     * U1b 更正：Folia 的封面卡并不是 `--bg-color` 95% 那种「玻璃」色，而是
     * `src/components/wall/wall.css:10,34` 的 `--lattice-poster-background`：
     * 深色 `color-mix(bg 88%, ink)`、日光 `color-mix(bg 94%, ink)` —— 深色页是比底色亮一档的墨，
     * 日光页是近白的纸。m412-light-home-playlists.png 里那张卡实测就是近白纸 + 6% 墨，与此一致。
     */
    val cardFill: Color,
    /** 拍立得卡片描边：`--text-primary` 5%，hover 32%（index.css:154-161）。 */
    val cardBorder: Color,
    /**
     * 卡片投影：`--lattice-focus-lift`（wall.css:12,35）——
     * 深色 `0 12px 32px rgb(0 0 0 / 45%)`、日光 `0 12px 32px rgb(0 0 0 / 25%)`。
     * 卡片浮在模糊背景上（m412-light-home-playlists.png），没有这道投影看起来就是平的描边框。
     */
    val cardShadow: Color,
    /** 封面内嵌层的细亮描边：`inset 0 0 0 1px` 取 `--lattice-thumb-ring`（wall.css 封面环，深 10% 白 / 浅 8% 黑）。 */
    val coverRing: Color,
    /** 列表行所在玻璃面板的底：`WallToolsQueueIsland.css:9-10` `rgb(0 0 0 / 40%)` / 日光 `rgb(255 255 255 / 62%)`。 */
    val panelFill: Color,
    /** 列表行所在玻璃面板的描边：`WallToolsQueueIsland.css:9,17` 深 `rgb(255 255 255 / 8%)` / 浅 `rgb(0 0 0 / 7%)`。 */
    val panelBorder: Color,
    /** 当前播放 / 选中行底：`WallToolsQueueIsland.css:176-177` 深 `rgb(255 255 255 / 10%)` / 日光 `rgb(0 0 0 / 7%)`。 */
    val rowCurrent: Color,
    /** 当前播放 / 选中行左侧竖条：`WallToolsQueueIsland.css:249` 取 `--text-accent`。 */
    val rowMarker: Color,

    // ---- 搜索行（SearchResultRow.tsx:76-99） -------------------------------------------
    /** 搜索行卡片底：浅 `bg-black/3.5%`、深 `bg-white/4%`（SearchResultRow.tsx:78-83）。 */
    val rowFill: Color,
    /** 搜索行卡片描边：浅 `border-black/5`、深 `border-white/6`（SearchResultRow.tsx:78-83）。 */
    val rowBorder: Color,
    /** 搜索行封面按钮底：`bg-zinc-500/15`（SearchResultRow.tsx:91）；zinc-500 = #71717a。 */
    val coverPlaceholder: Color,

    /** 是否为深色一套（Robolectric / Gallery 里判断要不要走 blur 分支用不到，仅展示用）。 */
    val isDark: Boolean,
)

/**
 * 日光素白（DAYLIGHT_THEME，baseThemes.ts:20-28）。
 *
 * 其中 [accent] 是 orange-600 `#ea580c`；`--st-accent` 在设置面板里默认走
 * `accentColor`，但 kitTheme.ts:12-18 给「中性 accent」留了 #18181b 的默认值，
 * 面板内部（滑块拇指槽、Tab 下划线）用的是 `accentColor`，这里统一跟随页面 accent。
 */
val FoliaLightColors = FoliaColorTokens(
    page = Color(0xFFF5F5F4),
    ink = Color(0xFF1C1917),
    inkSoft = Color(0xFF44403C),
    accent = Color(0xFFEA580C),
    onAccent = Color(0xFFFFFFFF),

    win = Color(0xFFF3F3F4),
    side = Color(0xFFE9E9EB),
    card = Color(0xFFFFFFFF),
    menu = Color(0xFFFFFFFF),
    raise = Color(0xFFFFFFFF),
    knob = Color(0xFFFFFFFF),
    preview = Color(0xFFECECEF),
    ok = Color(0xFF1E9A5C),
    warn = Color(0xFFB97A10),
    error = Color(0xFFD04444),

    text2 = Color(0xFF1C1917).copy(alpha = 0.62f),
    text3 = Color(0xFF1C1917).copy(alpha = 0.42f),
    line = Color(0xFF1C1917).copy(alpha = 0.08f),
    lineStrong = Color(0xFF1C1917).copy(alpha = 0.16f),
    fill = Color(0xFF1C1917).copy(alpha = 0.07f),
    fillStrong = Color(0xFF1C1917).copy(alpha = 0.12f),
    track = Color(0xFF1C1917).copy(alpha = 0.17f),

    glassFill = Color(0xFFF5F5F4).copy(alpha = 0.85f),
    glassBorder = Color(0xFF1C1917).copy(alpha = 0.12f),
    // wall.css:34 daylight `color-mix(bg #fafaf9 94%, ink #1c1917)` ≈ #f6f5f4（日光近白纸）。
    cardFill = Color(0xFFF6F5F4),
    cardBorder = Color(0xFF1C1917).copy(alpha = 0.05f),
    // wall.css:35 daylight `--lattice-focus-lift: 0 12px 32px rgb(0 0 0 / 25%)`；卡片的可见投影取 18%（日光下 25% 过重）。
    cardShadow = Color(0xFF000000).copy(alpha = 0.18f),
    coverRing = Color(0xFF1C1917).copy(alpha = 0.08f),
    // WallToolsQueueIsland.css:17 daylight island: 白 62% 底 + 黑 7% 描边。
    panelFill = Color(0xFFFFFFFF).copy(alpha = 0.62f),
    panelBorder = Color(0xFF000000).copy(alpha = 0.07f),
    // WallToolsQueueIsland.css:177 daylight current row `rgb(0 0 0 / 7%)`。
    rowCurrent = Color(0xFF000000).copy(alpha = 0.07f),
    // WallToolsQueueIsland.css:249 `--text-accent`（日光 = orange-600）。
    rowMarker = Color(0xFFEA580C),

    rowFill = Color(0xFF000000).copy(alpha = 0.035f),
    rowBorder = Color(0xFF000000).copy(alpha = 0.05f),
    coverPlaceholder = Color(0xFF71717A).copy(alpha = 0.15f),
    isDark = false,
)

/** 午夜墨染（DEFAULT_THEME，baseThemes.ts:9-17）。 */
val FoliaDarkColors = FoliaColorTokens(
    page = Color(0xFF09090B),
    ink = Color(0xFFF4F4F5),
    inkSoft = Color(0xFF71717A),
    accent = Color(0xFFF4F4F5),
    onAccent = Color(0xFF17171A),

    win = Color(0xFF1A1A1D),
    side = Color(0xFF141416),
    card = Color(0xFF232327),
    menu = Color(0xFF2B2B30),
    raise = Color(0xFF45454C),
    knob = Color(0xFFECECEE),
    preview = Color(0xFF151517),
    ok = Color(0xFF56C98D),
    warn = Color(0xFFE3A949),
    error = Color(0xFFEF6F6F),

    text2 = Color(0xFFF4F4F5).copy(alpha = 0.62f),
    text3 = Color(0xFFF4F4F5).copy(alpha = 0.42f),
    line = Color(0xFFF4F4F5).copy(alpha = 0.08f),
    lineStrong = Color(0xFFF4F4F5).copy(alpha = 0.16f),
    fill = Color(0xFFF4F4F5).copy(alpha = 0.07f),
    fillStrong = Color(0xFFF4F4F5).copy(alpha = 0.12f),
    track = Color(0xFFF4F4F5).copy(alpha = 0.17f),

    glassFill = Color(0xFF09090B).copy(alpha = 0.85f),
    glassBorder = Color(0xFFF4F4F5).copy(alpha = 0.12f),
    // wall.css:10 dark `color-mix(bg #070707 88%, ink #f7f4ee)` ≈ #1c1b1d（比页面底色亮一档的墨）。
    cardFill = Color(0xFF1C1B1D),
    cardBorder = Color(0xFFF4F4F5).copy(alpha = 0.05f),
    // wall.css:12 dark `--lattice-focus-lift: 0 12px 32px rgb(0 0 0 / 45%)`。
    cardShadow = Color(0xFF000000).copy(alpha = 0.45f),
    coverRing = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    // WallToolsQueueIsland.css:9-10 dark island: 黑 40% 底 + 白 8% 描边。
    panelFill = Color(0xFF000000).copy(alpha = 0.40f),
    panelBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    // WallToolsQueueIsland.css:176 dark current row `rgb(255 255 255 / 10%)`。
    rowCurrent = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    // 深色页 --text-accent 是 zinc-100（baseThemes.ts:12），竖条同色。
    rowMarker = Color(0xFFF4F4F5),

    rowFill = Color(0xFFFFFFFF).copy(alpha = 0.04f),
    rowBorder = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    coverPlaceholder = Color(0xFF71717A).copy(alpha = 0.15f),
    isDark = true,
)

/** 当前生效的 Folia 颜色令牌，由 [FoliaTheme] 提供。 */
val LocalFoliaColors = staticCompositionLocalOf { FoliaLightColors }
