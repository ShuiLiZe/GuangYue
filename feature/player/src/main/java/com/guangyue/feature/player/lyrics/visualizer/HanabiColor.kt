// 花火配色：逐函数对译 Folia hanabi/color.ts（九种调色角色 + 主题 / 混合两种取色、色块与字色的对比度推开）
// 与 hanabiSky.ts 的夜空四色标。颜色一律 0xRRGGBB 的 Int。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal class HbPalette(val ink: Int, val paper: Int, val blocks: List<Int>, val sparks: List<Int>, val accent: Int, val glow: Int)

/** 主题四色（Folia Theme 的 backgroundColor / primaryColor / accentColor / secondaryColor）。 */
internal class HbTheme(val background: Int, val primary: Int, val accent: Int, val secondary: Int)

internal const val HB_NIGHT_BACKGROUND = 0x0b0d24

private fun rgbOf(hex: Int) = doubleArrayOf(((hex shr 16) and 255) / 255.0, ((hex shr 8) and 255) / 255.0, (hex and 255) / 255.0)
private fun jsr(v: Double) = floor(v + 0.5).toInt()
private fun hexOf(r: Double, g: Double, b: Double): Int {
    fun c(v: Double) = jsr(min(1.0, max(0.0, v)) * 255)
    return (c(r) shl 16) or (c(g) shl 8) or c(b)
}

internal fun hbToHsl(hex: Int): DoubleArray {
    val (r, g, b) = rgbOf(hex).let { Triple(it[0], it[1], it[2]) }
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
    val l = (mx + mn) / 2
    if (mx == mn) return doubleArrayOf(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = if (mx == r) (g - b) / d + (if (g < b) 6 else 0) else if (mx == g) (b - r) / d + 2 else (r - g) / d + 4
    return doubleArrayOf(h / 6, s, l)
}

private fun toRgbHex(h: Double, s: Double, l: Double): Int {
    if (s == 0.0) return hexOf(l, l, l)
    val q = if (l < 0.5) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun ch(t: Double): Double {
        val x = if (t < 0) t + 1 else if (t > 1) t - 1 else t
        if (x < 1.0 / 6) return p + (q - p) * 6 * x
        if (x < 1.0 / 2) return q
        if (x < 2.0 / 3) return p + (q - p) * (2.0 / 3 - x) * 6
        return p
    }
    return hexOf(ch(h + 1.0 / 3), ch(h), ch(h - 1.0 / 3))
}

private fun c01(v: Double) = min(1.0, max(0.0, v))
private fun mixHue(from: Double, to: Double, amount: Double): Double {
    var d = to - from
    if (d > 0.5) d -= 1
    if (d < -0.5) d += 1
    return (from + d * amount + 1) % 1
}
private fun vivid(hex: Int, sat: Double, minL: Double, maxL: Double): Int {
    val (h, s, l) = hbToHsl(hex).let { Triple(it[0], it[1], it[2]) }
    return toRgbHex(h, if (s < 0.05) s else max(s, sat), min(maxL, max(minL, l)))
}
private fun linear(v: Double) = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
internal fun hbRelativeLuminance(hex: Int): Double { val c = rgbOf(hex); return 0.2126 * linear(c[0]) + 0.7152 * linear(c[1]) + 0.0722 * linear(c[2]) }
internal fun hbContrast(a: Int, b: Int): Double { val la = hbRelativeLuminance(a); val lb = hbRelativeLuminance(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }

private const val DARK_INK = 0x2b1f3d
private const val LIGHT_INK = 0xffffff

private fun separate(block: Int, ink: Int): Int {
    if (hbContrast(block, ink) >= 3.2) return block
    val (h, s, l) = hbToHsl(block).let { Triple(it[0], it[1], it[2]) }
    val darker = hbRelativeLuminance(ink) > 0.5
    for (step in 1..20) {
        val next = toRgbHex(h, s, c01(if (darker) l - step * 0.04 else l + step * 0.04))
        if (hbContrast(next, ink) >= 3.2) return next
    }
    return if (darker) 0x000000 else 0xffffff
}

internal fun hbEnsureContrast(color: Int, against: Int, minRatio: Double): Int {
    if (hbContrast(color, against) >= minRatio) return color
    val (h, s, l) = hbToHsl(color).let { Triple(it[0], it[1], it[2]) }
    val dirs = if (hbRelativeLuminance(against) > 0.18) listOf(-1, 1) else listOf(1, -1)
    for (step in 1..25) for (d in dirs) {
        val next = toRgbHex(h, s, c01(l + d * step * 0.04))
        if (hbContrast(next, against) >= minRatio) return next
    }
    return if (hbContrast(0x000000, against) >= hbContrast(0xffffff, against)) 0x000000 else 0xffffff
}

internal class HbGlyphColors(val ink: Int, val sung: Int, val flash: Int, val back: Int)

internal fun hanabiBackdropOf(p: HbPalette) = if (hbRelativeLuminance(p.paper) > 0.5) 0x0b0d24 else 0xf3efe9

internal fun resolveHanabiGlyphColors(p: HbPalette, base: Int, under: Int): HbGlyphColors {
    val ink = hbEnsureContrast(base, under, 3.0)
    val candidates = listOf(p.accent) + p.sparks + p.glow
    val sung = candidates.firstOrNull { hbContrast(it, under) >= 3.0 && hbContrast(it, ink) >= 1.3 } ?: hbEnsureContrast(p.accent, under, 3.0)
    return HbGlyphColors(ink, sung, hbEnsureContrast(p.glow, under, 2.0), hbEnsureContrast(p.sparks.firstOrNull() ?: p.accent, under, 2.0))
}

private class RoleColors(val blocks: List<Int>, val sparks: List<Int>, val accent: Int, val glow: Int)

private val ROLES: Map<String, RoleColors> = mapOf(
    "night" to RoleColors(listOf(0x1d2b7a, 0xd62f74, 0x5b3cc4), listOf(0xffd166, 0xff6fa5, 0x7bdff2, 0xffffff, 0xb79cff), 0xffd166, 0xffe3a3),
    "festival" to RoleColors(listOf(0xe60033, 0x1e50a2, 0xd9480f), listOf(0xffcc1a, 0xff3355, 0x3cc8ff, 0x6dff9a, 0xffffff), 0xffc400, 0xffa24a),
    "pastel" to RoleColors(listOf(0xff9ec0, 0x8fd8ff, 0xfff08a), listOf(0xffc4e1, 0xa8e8ff, 0xfff5b0, 0xd4bfff), 0xff7fb2, 0xfff0f6),
    "mono" to RoleColors(listOf(0xf4f1ea, 0xc9c3b3), listOf(0xffffff, 0xe8e2d0, 0xfff3d6), 0xffffff, 0xfff7e6),
    "gold" to RoleColors(listOf(0xf2b01e, 0xffd970, 0xfff1c9), listOf(0xffd36e, 0xffae3b, 0xfff1c1, 0xff8a3d), 0xffc94a, 0xffcf70),
    "neon" to RoleColors(listOf(0xff2bd6, 0x00e5ff, 0x7a2cff), listOf(0xff2bd6, 0x00f0ff, 0xb8ff1f, 0xffffff, 0x8f6bff), 0x00f0ff, 0xff6bf0),
    "sakura" to RoleColors(listOf(0xff5f9e, 0xfff4f8, 0xffb8d2), listOf(0xffb7d5, 0xffffff, 0xff6fae, 0xffe0ec), 0xff4f93, 0xffd6e8),
    "kawaii" to RoleColors(listOf(0xff7eb6, 0x7fd8ff, 0xffe066), listOf(0xffb3d9, 0xa0f0ff, 0xfff3a0, 0xd9b8ff, 0xffffff), 0xff5fa2, 0xffd1e8),
    "ocean" to RoleColors(listOf(0x0a5cff, 0x00c2b8, 0xeaf6ff), listOf(0x5ce1ff, 0x2bffd5, 0xffffff, 0x4d86ff), 0x5ce1ff, 0xa8f0ff),
)

private fun themeColors(t: HbTheme): RoleColors {
    fun spark(hex: Int) = vivid(hex, 0.6, 0.58, 0.8)
    return RoleColors(
        listOf(vivid(t.accent, 0.55, 0.3, 0.7), vivid(t.primary, 0.45, 0.25, 0.75), vivid(t.secondary, 0.5, 0.3, 0.7)),
        listOf(spark(t.accent), spark(t.primary), spark(t.secondary), 0xffffff),
        spark(t.accent), vivid(t.accent, 0.5, 0.75, 0.88),
    )
}

private fun mixRoles(role: RoleColors, t: HbTheme): RoleColors {
    val target = hbToHsl(t.accent)
    if (target[1] < 0.2) return role
    val themed = themeColors(t)
    fun lean(hex: Int): Int {
        val (h, s, l) = hbToHsl(hex).let { Triple(it[0], it[1], it[2]) }
        if (s < 0.05) return hex
        return toRgbHex(mixHue(h, target[0], 0.4), s, l)
    }
    return RoleColors(listOf(themed.blocks[0]) + role.blocks.drop(1).map(::lean), listOf(themed.sparks[0]) + role.sparks.drop(1).map(::lean), themed.accent, lean(role.glow))
}

/** resolveHanabiPalette；colorMode = "role" | "theme" | "mixed"（Folia 缺省 mixed）。 */
internal fun resolveHanabiPalette(role: String, theme: HbTheme, colorMode: String = "mixed"): HbPalette {
    val base = ROLES[role] ?: ROLES.getValue("night")
    val colors = when (colorMode) { "theme" -> themeColors(theme); "mixed" -> mixRoles(base, theme); else -> base }
    val paper = if (hbRelativeLuminance(theme.background) > 0.4) DARK_INK else LIGHT_INK
    fun worst(ink: Int) = colors.blocks.minOf { hbContrast(it, ink) }
    val ink = if (worst(DARK_INK) >= worst(LIGHT_INK)) DARK_INK else LIGHT_INK
    return HbPalette(ink, paper, colors.blocks.map { separate(it, ink) }, colors.sparks, colors.accent, colors.glow)
}

/** hanabiSky.resolveHanabiSkyStops：夜空四色标（at, h, s, l），色相往主题强调色偏 0.3（强调色太灰就不偏）。 */
internal fun resolveHanabiSkyStops(theme: HbTheme): List<DoubleArray> {
    val hsl = hbToHsl(theme.accent)
    fun lean(base: Double): Double {
        if (hsl[1] < 0.2) return base
        var d = hsl[0] - base
        if (d > 0.5) d -= 1
        if (d < -0.5) d += 1
        return base + d * 0.3
    }
    fun norm(h: Double) = ((h % 1) + 1) % 1
    return listOf(
        doubleArrayOf(0.0, norm(lean(0.66)), 0.62, 0.06), doubleArrayOf(0.45, norm(lean(0.7)), 0.5, 0.11),
        doubleArrayOf(0.82, norm(lean(0.8)), 0.48, 0.19), doubleArrayOf(1.0, norm(lean(0.9)), 0.55, 0.27),
    )
}

/** 夜空色标的 0xRRGGBB（浏览器把 hsl() 换成 sRGB 时四舍五入到 0..255）。 */
internal fun hbHslHex(h: Double, s: Double, l: Double) = toRgbHex(h, s, l)
