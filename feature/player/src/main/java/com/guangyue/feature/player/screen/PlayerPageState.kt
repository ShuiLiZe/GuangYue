package com.guangyue.feature.player.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * U4 · 全屏播放页的纯逻辑层（不依赖 Android 运行时，可在 JVM 单测里直接跑）。
 *
 * 这里只放三件「能算出来、也值得测」的事：
 *  1. 播放模式的**显示态**——Folia 的 `loopOff / loopAll / loopOne` 三态怎么排、怎么写中文、点一下去哪；
 *  2. 封面主色 → 渐变背景的**取色兜底链**（取不到色回落主题强调色）；
 *  3. 播放页顶部「左歌手 / 竖线 / 大标题 / 副标题」那块的**文案挑选与两行省略策略**。
 */

// ---------------------------------------------------------------- 播放模式 --

/**
 * 手机版把 Folia 桌面的「循环开关 + 打乱开关」两个独立布尔合并成**一颗三态按钮**。
 *
 * Folia 的循环是 `loopOff → loopAll → loopOne` 三档（`zh-CN.ts` 的 `loopOff/loopAll/loopOne`）；
 * 打乱（`shuffle`）在手机上是**同一颗按钮让出来的一档**，因为 360dp 宽一行放五个传输键
 * （上一首 / 播放 / 下一首 / 模式 / 队列）已经没有第二个循环键的位置。
 * 需求原文是「顺序 / 单曲 / 随机」，正好对上 `repeatMode=0 / 2 / shuffle=true`。
 */
enum class PlayerMode { Sequential, LoopOne, Shuffle }

/**
 * 把播放器状态映射成三态。
 *
 * - `shuffle = true` → [PlayerMode.Shuffle]（打乱优先，与 Folia「打乱时循环仍生效但显示打乱」一致）；
 * - 否则 `repeatMode == 2` → [PlayerMode.LoopOne]；
 * - 其余（0 = OFF / 1 = ALL）→ [PlayerMode.Sequential]。
 *
 * 说明：`repeatMode = 1`（LIST_ALL）在手机上不单独占一档。ExoPlayer 的播放在队尾本来就会推进
 * 下一首，OFF 与 ALL 在「有下一首」时体感一致，只有「队尾是否回头」不同；这一档留给后续
 * 设置页暴露，不在三态主按钮上出现（见 docs/ui-u4/report.md §7）。
 */
fun playerMode(shuffle: Boolean, repeatMode: Int = 0): PlayerMode = when {
    shuffle -> PlayerMode.Shuffle
    repeatMode == 2 -> PlayerMode.LoopOne
    else -> PlayerMode.Sequential
}

/**
 * 点一下模式按钮之后要执行的动作。
 *
 * 三态循环：顺序 → 单曲 → 随机 → 顺序。
 * 动作返回两份**目标状态**而不是「翻转一下」，这样 [applyPlayerMode] 不读取中间态，
 * 连点两次也不会出现「随机开着又切单曲」这种冲突（两者在 ExoPlayer 里是两个开关）。
 */
data class PlayerModeTarget(val shuffle: Boolean, val repeatMode: Int)

/** 模式按钮点击后的目标状态（纯函数，单测直接断言）。 */
fun nextPlayerModeTarget(current: PlayerMode): PlayerModeTarget = when (current) {
    PlayerMode.Sequential -> PlayerModeTarget(shuffle = false, repeatMode = 2) // → 单曲循环
    PlayerMode.LoopOne -> PlayerModeTarget(shuffle = true, repeatMode = 0) // → 随机
    PlayerMode.Shuffle -> PlayerModeTarget(shuffle = false, repeatMode = 0) // → 顺序
}

/** 文案键：交给 strings.xml（feature 模块没有 R，用 [PlayerStrings] 转成中文）。 */
fun playerModeLabelKey(mode: PlayerMode): PlayerModeString = when (mode) {
    PlayerMode.Sequential -> PlayerModeString.Sequential
    PlayerMode.LoopOne -> PlayerModeString.LoopOne
    PlayerMode.Shuffle -> PlayerModeString.Shuffle
}

/** 三态文案的抽象键（[PlayerStrings] 用资源 id 填，测试里用中文常量填）。 */
enum class PlayerModeString { Sequential, LoopOne, Shuffle }

// -------------------------------------------------------------- 封面渐变 --

/** 渐变背景的一组颜色，直接喂给 `Brush.linearGradient`。 */
data class PlayerBackdrop(val colors: List<Color>, val seed: Color)

/**
 * 封面主色 → 背景渐变，取不到色时按三级兜底。
 *
 * Folia 的播放页背景是从封面里抽出来的柔光渐变（`player-panel-cover-m412-light.png` 是粉橙、
 * 深色版是暗红）。手机端不引第三方取色库（不在依赖白名单里），改用一条**确定性、可复现**的路径：
 *
 *  1. 调用方给的主色（若能拿到）→ 用它；
 *  2. 给不到 → 用 `seed`（播放页传的是主题强调色 `Gyp.accent`）；
 *  3. 连 seed 都算不出（Alpha ≈ 0）→ 用 [FALLBACK_SEED] 的中性橙。
 *
 * 渐变本身：深色下整体压暗、浅色下整体提亮，再叠一层顶亮底暗的同色系，保证白字/黑字在
 * 两端都有足够对比度。返回的 [PlayerBackdrop.seed] 是真正用上的种子色，播放页可以拿它
 * 去设置系统栏图标的明暗。
 */
fun playerBackdrop(
    coverColor: Color?,
    seed: Color,
    dark: Boolean,
): PlayerBackdrop {
    val base = when {
        coverColor != null && coverColor.alpha > 0.02f -> coverColor
        seed.alpha > 0.02f -> seed
        else -> FALLBACK_SEED
    }
    val vivid = base.saturated(dark)
    val colors = if (dark) {
        listOf(
            vivid.darken(0.56f),
            vivid.darken(0.38f),
            vivid.darken(0.26f),
        )
    } else {
        listOf(
            vivid.lighten(0.58f),
            vivid.lighten(0.76f),
            vivid.lighten(0.90f),
        )
    }
    return PlayerBackdrop(colors = colors, seed = vivid)
}

/** 兜底种子：主题橙（`Gyp.accent` 浅色值 `#EA580C`），保证「取不到色」时仍是暖色柔光。 */
val FALLBACK_SEED: Color = Color(0xFFEA580C)

/** 背景亮时文字用深色：播放页顶部的裸标题压不压深色字，就按这一句判。 */
fun backdropUsesDarkInk(backdrop: PlayerBackdrop): Boolean =
    backdrop.colors.first().luminance() > 0.55f

private fun Color.darken(amount: Float): Color {
    val f = (1f - amount).coerceIn(0f, 1f)
    return Color(red * f, green * f, blue * f, alpha)
}

private fun Color.lighten(amount: Float): Color =
    lerp(this, Color.White, amount.coerceIn(0f, 1f))

/**
 * 把封面抽到的色压成「能当背景」的观感：先中和成同明度灰，再按 [retain] 回填原色。
 * 深色版回填多一点（暗红要留住，纯灰会脏），浅色版回填少一点（粉橙要淡）。
 */
private fun Color.saturated(dark: Boolean): Color {
    val lum = luminance()
    val neutral = Color(lum, lum, lum, 1f)
    val retain = if (dark) 0.46f else 0.34f
    return lerp(neutral, this, retain).copy(alpha = 1f)
}

// ---------------------------------------------------------------- 标题区 --

/** 顶部标题区的展示数据。 */
data class PlayerHeaderText(
    val artist: String,
    val title: String,
    val subtitle: String?,
    /** 标题是否允许两行（长中文标题走两行省略，而不是一行跑马灯）。 */
    val titleMaxLines: Int,
)

/**
 * 顶部标题区文案。
 *
 * Folia 是「衬线斜体歌手 / 竖线 / 大标题 / 副标题（Daily MIX）」四段，手机上把它压成
 * 「歌手 → 两行大标题 → 副标题（音质 · 队列位置）」。
 *
 * - 歌手空 → 用 [unknownArtist]（`未知艺术家`，与 `QueueItem.toCachedTrack` 的兜底一致）；
 * - 歌名空 → 用 [noSong]（`当前没有正在播放的歌曲`，取自 `zh-CN.ts` 的 `noSongPlaying`）；
 * - 副标题：`音质 · 队列 2/9`，两项都拿不到时给 null（不占高度）；
 * - 长标题：字符数超过 [LONG_TITLE_CHARS] 才允许两行，否则一行，避免短标题被拉成两行。
 */
fun playerHeaderText(
    title: String?,
    artist: String?,
    album: String?,
    qualityLabel: String?,
    queuePosition: String?,
    unknownArtist: String,
    noSong: String,
): PlayerHeaderText {
    val t = title?.trim().orEmpty()
    val a = artist?.trim().orEmpty()
    val parts = listOfNotNull(
        qualityLabel?.takeIf { it.isNotBlank() },
        album?.takeIf { it.isNotBlank() },
        queuePosition?.takeIf { it.isNotBlank() },
    )
    return PlayerHeaderText(
        artist = a.ifEmpty { unknownArtist },
        title = t.ifEmpty { noSong },
        subtitle = parts.joinToString(" · ").ifEmpty { null },
        titleMaxLines = if (t.length > LONG_TITLE_CHARS) 2 else 1,
    )
}

/** 超过这个字符数的中文标题才走两行；20 字大约正好占满 360dp 一行的 24sp。 */
const val LONG_TITLE_CHARS: Int = 20

/**
 * 队列位置文案 `2/9`（Folia 的 `queueCount` 只给总数，位置是本项目自拟）。
 * 队列空或索引越界时返回 null，让副标题自动省掉这一段。
 */
fun queuePositionText(index: Int, size: Int): String? {
    if (size <= 0) return null
    val at = index + 1
    if (at < 1 || at > size) return null
    return "$at/$size"
}
