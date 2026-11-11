// 町景的调色板：逐函数对译 Folia v0.7.25 machi/engine/palette.ts（及其用到的 utils/themeColorMath.ts）。
// 主题色 → 场景各部分的颜色。底子（深蓝灰的夜、暖琥珀的灯）是固定的美术方向，主题只在限定的范围里推它：
// 品牌三色条、屏幕字、LED、霓虹、贩卖机、商品用主题色，天空和暖光只偏一点色相。场景自己的颜色（resolveArt）待接场景时补。
// 颜色都是 #rrggbb。金标 machi_palette_golden.txt（docs/port/scripts/machi-palette-golden-gen.js）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// —— themeColorMath.ts ——————————————————————————————————————————————
internal data class McHsl(val h: Double, val s: Double, val l: Double)
private class McRgb(val r: Double, val g: Double, val b: Double)

private fun clamp01(v: Double) = max(0.0, min(1.0, v))
/** JS Math.round：.5 向 +∞。 */
private fun jsRound(v: Double) = floor(v + 0.5)
private fun clampChannel(v: Double) = max(0.0, min(255.0, jsRound(v)))
internal fun mcNormalizeHue(h: Double) = ((h % 360) + 360) % 360

/** #rgb / #rrggbb（Folia parseColorChannels 的十六进制分支；其它格式返回 null）。 */
private fun parseColor(value: String?): McRgb? {
    if (value == null) return null
    val v = value.trim().removePrefix("#")
    val hex = when (v.length) {
        3 -> v.map { "$it$it" }.joinToString("")
        6 -> v
        else -> return null
    }
    val n = hex.toLongOrNull(16) ?: return null
    return McRgb(clampChannel(((n shr 16) and 0xff).toDouble()), clampChannel(((n shr 8) and 0xff).toDouble()), clampChannel((n and 0xff).toDouble()))
}

private fun rgbToHex(c: McRgb) = "#" + listOf(c.r, c.g, c.b).joinToString("") { clampChannel(it).toInt().toString(16).padStart(2, '0') }

private fun rgbToHsl(c: McRgb): McHsl {
    val red = clampChannel(c.r) / 255; val green = clampChannel(c.g) / 255; val blue = clampChannel(c.b) / 255
    val mx = maxOf(red, green, blue); val mn = minOf(red, green, blue)
    val delta = mx - mn
    val lightness = (mx + mn) / 2
    if (delta == 0.0) return McHsl(0.0, 0.0, lightness)
    val saturation = delta / (1 - abs(2 * lightness - 1))
    val hue = when (mx) {
        red -> ((green - blue) / delta) % 6
        green -> (blue - red) / delta + 2
        else -> (red - green) / delta + 4
    }
    return McHsl(mcNormalizeHue(hue * 60), clamp01(saturation), clamp01(lightness))
}

private fun hslToRgb(hsl: McHsl): McRgb {
    val hue = mcNormalizeHue(hsl.h); val s = clamp01(hsl.s); val l = clamp01(hsl.l)
    val chroma = (1 - abs(2 * l - 1)) * s
    val secondary = chroma * (1 - abs(((hue / 60) % 2) - 1))
    val offset = l - chroma / 2
    val (r, g, b) = when (floor(hue / 60).toInt() % 6) {
        0 -> Triple(chroma, secondary, 0.0)
        1 -> Triple(secondary, chroma, 0.0)
        2 -> Triple(0.0, chroma, secondary)
        3 -> Triple(0.0, secondary, chroma)
        4 -> Triple(secondary, 0.0, chroma)
        else -> Triple(chroma, 0.0, secondary)
    }
    return McRgb(clampChannel((r + offset) * 255), clampChannel((g + offset) * 255), clampChannel((b + offset) * 255))
}

internal fun mcHexToHsl(color: String?): McHsl? = parseColor(color)?.let { rgbToHsl(it) }
internal fun mcHslToHex(hsl: McHsl) = rgbToHex(hslToRgb(hsl))

internal fun mcMixHex(from: String, to: String, amount: Double): String {
    val a = parseColor(from); val b = parseColor(to)
    if (a == null || b == null) return rgbToHex(b ?: a ?: parseColor("#4f7cff")!!)
    val t = clamp01(amount)
    return rgbToHex(McRgb(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t))
}

internal fun mcLuminance(color: String): Double {
    val c = parseColor(color) ?: return 0.0
    fun tr(ch: Double): Double { val n = ch / 255; return if (n <= 0.03928) n / 12.92 else ((n + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * tr(c.r) + 0.7152 * tr(c.g) + 0.0722 * tr(c.b)
}

internal fun mcContrast(fg: String, bg: String): Double {
    val a = mcLuminance(fg); val b = mcLuminance(bg)
    val (hi, lo) = if (a > b) a to b else b to a
    return (hi + 0.05) / (lo + 0.05)
}

internal fun mcHueDistance(a: Double, b: Double): Double {
    val d = abs(mcNormalizeHue(a) - mcNormalizeHue(b))
    return min(d, 360 - d)
}

internal fun mcAdjustLightnessForContrast(color: String, background: String, minRatio: Double, step: Double = 0.02, maxSteps: Int = 50): String {
    val hsl = mcHexToHsl(color) ?: return color
    val direction = if (mcLuminance(background) > 0.35) -1 else 1
    var bestColor = mcHslToHex(hsl)
    var bestRatio = mcContrast(bestColor, background)
    for (index in 0..maxSteps) {
        val lightness = clamp01(hsl.l + direction * step * index)
        val candidate = mcHslToHex(hsl.copy(l = lightness))
        val ratio = mcContrast(candidate, background)
        if (ratio > bestRatio) { bestRatio = ratio; bestColor = candidate }
        if (ratio >= minRatio) return candidate
        if (index > 0 && (lightness == 0.0 || lightness == 1.0)) break
    }
    return bestColor
}

// —— palette.ts ————————————————————————————————————————————————————————

/** Folia Theme 里町景用到的几项。wordColors = 关键词色（theme.wordColors[].color）。 */
internal data class MachiTheme(
    val backgroundColor: String,
    val primaryColor: String,
    val secondaryColor: String,
    val accentColor: String,
    val wordColors: List<String> = emptyList(),
)

internal data class MachiPalette(
    val skyTop: String, val skyBottom: String, val fog: String, val hemiSky: String, val hemiGround: String, val moon: String,
    val skyTopDusk: String, val skyBottomDusk: String, val hemiSkyDusk: String, val hemiGroundDusk: String, val moonDusk: String,
    val skyTopDay: String, val skyBottomDay: String, val hemiSkyDay: String, val hemiGroundDay: String, val moonDay: String,
    val asphalt: String, val roadPaint: String, val concrete: String, val curb: String,
    val plinthTop: String, val plinthMid: String, val plinthLow: String, val plinthTrim: String,
    val glass: String, val metal: String, val darkMetal: String, val pole: String, val rubber: String, val wire: String,
    val caution: String, val stopRed: String, val interiorLight: String, val streetLight: String, val vendingLight: String,
    val brand: List<String>, val signBase: String, val signText: String,
    val boardBase: String, val boardUnlit: String, val boardLit: String, val boardIsLight: Boolean,
    val led: String, val neon: String, val vendingBody: String, val vendingAccent: String, val products: List<String>,
    val art: Map<String, String> = emptyMap(),
) {
    /** 金标比对用：字段名 → 值（与 Folia 对象键一致）。 */
    fun fields(): Map<String, Any> = linkedMapOf(
        "skyTop" to skyTop, "skyBottom" to skyBottom, "fog" to fog, "hemiSky" to hemiSky, "hemiGround" to hemiGround, "moon" to moon,
        "skyTopDusk" to skyTopDusk, "skyBottomDusk" to skyBottomDusk, "hemiSkyDusk" to hemiSkyDusk, "hemiGroundDusk" to hemiGroundDusk, "moonDusk" to moonDusk,
        "skyTopDay" to skyTopDay, "skyBottomDay" to skyBottomDay, "hemiSkyDay" to hemiSkyDay, "hemiGroundDay" to hemiGroundDay, "moonDay" to moonDay,
        "asphalt" to asphalt, "roadPaint" to roadPaint, "concrete" to concrete, "curb" to curb,
        "plinthTop" to plinthTop, "plinthMid" to plinthMid, "plinthLow" to plinthLow, "plinthTrim" to plinthTrim,
        "glass" to glass, "metal" to metal, "darkMetal" to darkMetal, "pole" to pole, "rubber" to rubber, "wire" to wire,
        "caution" to caution, "stopRed" to stopRed, "interiorLight" to interiorLight, "streetLight" to streetLight, "vendingLight" to vendingLight,
        "brand" to brand, "signBase" to signBase, "signText" to signText,
        "boardBase" to boardBase, "boardUnlit" to boardUnlit, "boardLit" to boardLit, "boardIsLight" to boardIsLight,
        "led" to led, "neon" to neon, "vendingBody" to vendingBody, "vendingAccent" to vendingAccent, "products" to products,
    )
}

internal const val MACHI_LIGHT_THEME_LUMINANCE = 0.4
internal const val MACHI_BOARD_MIN_CONTRAST = 4.5
private const val CHROMATIC_SATURATION = 0.22

private val CLASSIC_BRAND_TRIADS = listOf(
    listOf("#f0781e", "#f4f1ea", "#1f9a52"),
    listOf("#2f6fd6", "#f4f1ea", "#58b8ea"),
    listOf("#1d9a63", "#f4f1ea", "#2d6cc0"),
    listOf("#e2402f", "#f4f1ea", "#f2b520"),
    listOf("#7a4fd0", "#f4f1ea", "#f07fae"),
)

private val PRODUCT_FALLBACK = listOf("#e85d4a", "#f2c14e", "#4fa3d8", "#7ccf7a", "#f08cc0", "#f4f1ea", "#9b7be0", "#ff9d4d")

private object Art {
    const val skyTop = "#0e1524"; const val skyBottom = "#26324a"; const val asphalt = "#2a303c"; const val roadPaint = "#c9d1dc"
    const val concrete = "#4b5363"; const val curb = "#5f6878"; const val plinthTop = "#20242c"; const val plinthMid = "#3d3a36"
    const val plinthLow = "#2b2622"; const val plinthTrim = "#15171c"; const val glass = "#9cc3e6"; const val metal = "#8a94a1"
    const val darkMetal = "#383e49"; const val pole = "#979ca4"; const val rubber = "#1c1f26"; const val wire = "#11141a"
    const val caution = "#e7b42e"; const val stopRed = "#c7302b"; const val hemiSky = "#3a4b6c"; const val hemiGround = "#141923"
    const val moon = "#9cb3dc"; const val skyTopDusk = "#2c2f5c"; const val skyBottomDusk = "#e58a62"; const val hemiSkyDusk = "#8b79a6"
    const val hemiGroundDusk = "#30262a"; const val moonDusk = "#ffae78"; const val skyTopDay = "#4d8bd4"; const val skyBottomDay = "#cde4f2"
    const val hemiSkyDay = "#bcd6ee"; const val hemiGroundDay = "#5f5b52"; const val moonDay = "#fff0da"; const val interiorLight = "#ffc887"
    const val streetLight = "#ffe4b8"; const val vendingLight = "#d4ebff"; const val gold = "#ffc861"
}

private fun hslOf(color: String?, fallback: String) = mcHexToHsl(color) ?: mcHexToHsl(fallback)!!

private fun isChromatic(color: String?): Boolean {
    val hsl = mcHexToHsl(color) ?: return false
    return hsl.s >= CHROMATIC_SATURATION && hsl.l > 0.08 && hsl.l < 0.94
}

/** 招牌色：拉到够鲜、亮度在招牌能读的区间。 */
internal fun machiVivid(color: String, minSaturation: Double = 0.58, minLightness: Double = 0.42, maxLightness: Double = 0.62): String {
    val hsl = hslOf(color, Art.gold)
    return mcHslToHex(McHsl(hsl.h, max(minSaturation, hsl.s), min(maxLightness, max(minLightness, hsl.l))))
}

private fun pullHue(base: String, toward: String, amount: Double): String {
    val from = mcHexToHsl(base)!!
    val to = mcHexToHsl(toward)
    if (to == null || amount <= 0) return base
    val distance = mcHueDistance(from.h, to.h)
    val forward = if (mcNormalizeHue(from.h + distance) == mcNormalizeHue(to.h)) 1 else -1
    return mcHslToHex(McHsl(mcNormalizeHue(from.h + forward * distance * amount), from.s, from.l))
}

private fun tintDark(base: String, themeBackground: String): String {
    val bg = mcHexToHsl(themeBackground)
    if (bg == null || bg.s < 0.08) return base
    return pullHue(base, themeBackground, min(0.25, bg.s * 0.45))
}

internal fun machiSeedIndex(seed: String, salt: String, length: Int) = (machiHashSeed("$seed:$salt") % length).toInt()

private fun chromaticCandidates(theme: MachiTheme) =
    (listOf(theme.accentColor) + theme.wordColors + listOf(theme.primaryColor, theme.secondaryColor)).filter { isChromatic(it) }

private fun resolveBrand(theme: MachiTheme, seed: String): List<String> {
    val candidates = chromaticCandidates(theme)
    if (candidates.isEmpty()) return CLASSIC_BRAND_TRIADS[machiSeedIndex(seed, "brand", CLASSIC_BRAND_TRIADS.size)]
    val first = machiVivid(candidates[0])
    val firstHsl = mcHexToHsl(first)!!
    val second = candidates.drop(1).firstOrNull { mcHueDistance(mcHexToHsl(it)!!.h, firstHsl.h) >= 40 }
    val third = if (second != null) machiVivid(second) else mcHslToHex(firstHsl.copy(h = mcNormalizeHue(firstHsl.h + 150)))
    return listOf(first, "#f4f1ea", third)
}

private fun resolveBoard(theme: MachiTheme, brand: List<String>, isLight: Boolean): Triple<String, String, String> {
    val accent = if (isChromatic(theme.accentColor)) theme.accentColor else brand[0]
    if (isLight) {
        val base = "#f3eee4"
        val lit = mcAdjustLightnessForContrast(machiVivid(accent, 0.62, 0.3, 0.46), base, MACHI_BOARD_MIN_CONTRAST)
        return Triple(base, "#9aa0ab", lit)
    }
    val base = mcMixHex("#0f1420", machiVivid(accent), 0.12)
    val glow = if (isChromatic(theme.accentColor) || isChromatic(brand[0])) machiVivid(accent, 0.62, 0.64, 0.74) else Art.gold
    return Triple(base, "#6c7384", mcAdjustLightnessForContrast(glow, base, MACHI_BOARD_MIN_CONTRAST))
}

private fun resolveProducts(theme: MachiTheme, brand: List<String>, seed: String): List<String> {
    val keywordColors = theme.wordColors.filter { isChromatic(it) }.map { machiVivid(it, 0.45, 0.45, 0.7) }
    val pool = keywordColors + listOf(brand[0], brand[2]) + PRODUCT_FALLBACK
    val offset = machiSeedIndex(seed, "products", PRODUCT_FALLBACK.size)
    return List(8) { i -> pool[if (i < keywordColors.size + 2) i else keywordColors.size + 2 + ((i + offset) % PRODUCT_FALLBACK.size)] }
}

/** 主题 → 町景调色板。同样的主题、seed 得到同样的结果。 */
internal fun resolveMachiPalette(theme: MachiTheme, seed: String): MachiPalette {
    val background = theme.backgroundColor
    val isLight = mcLuminance(background) > MACHI_LIGHT_THEME_LUMINANCE
    val brand = resolveBrand(theme, seed)
    val (boardBase, boardUnlit, boardLit) = resolveBoard(theme, brand, isLight)
    val accent = if (isChromatic(theme.accentColor)) theme.accentColor else brand[0]
    val skyTop = tintDark(Art.skyTop, background)
    val skyBottom = tintDark(Art.skyBottom, background)
    val interiorHsl = mcHexToHsl(mcMixHex(Art.interiorLight, machiVivid(accent), 0.16))!!
    val interiorLight = mcHslToHex(interiorHsl.copy(l = max(0.72, interiorHsl.l)))
    val signText = if (mcContrast("#1b1f27", brand[1]) >= MACHI_BOARD_MIN_CONTRAST) "#1b1f27" else "#ffffff"
    val b2 = mcHexToHsl(brand[2])!!
    return MachiPalette(
        skyTop = skyTop, skyBottom = skyBottom, fog = skyBottom,
        hemiSky = tintDark(Art.hemiSky, background), hemiGround = Art.hemiGround, moon = Art.moon,
        skyTopDusk = tintDark(Art.skyTopDusk, background), skyBottomDusk = Art.skyBottomDusk, hemiSkyDusk = Art.hemiSkyDusk,
        hemiGroundDusk = Art.hemiGroundDusk, moonDusk = Art.moonDusk,
        skyTopDay = tintDark(Art.skyTopDay, background), skyBottomDay = Art.skyBottomDay, hemiSkyDay = Art.hemiSkyDay,
        hemiGroundDay = Art.hemiGroundDay, moonDay = Art.moonDay,
        asphalt = tintDark(Art.asphalt, background), roadPaint = Art.roadPaint, concrete = tintDark(Art.concrete, background), curb = Art.curb,
        plinthTop = Art.plinthTop, plinthMid = Art.plinthMid, plinthLow = Art.plinthLow, plinthTrim = Art.plinthTrim,
        glass = Art.glass, metal = Art.metal, darkMetal = Art.darkMetal, pole = Art.pole, rubber = Art.rubber, wire = Art.wire,
        caution = Art.caution, stopRed = Art.stopRed, interiorLight = interiorLight, streetLight = Art.streetLight, vendingLight = Art.vendingLight,
        brand = brand, signBase = brand[1], signText = signText,
        boardBase = boardBase, boardUnlit = boardUnlit, boardLit = boardLit, boardIsLight = isLight,
        led = machiVivid(accent, 0.85, 0.58, 0.66), neon = machiVivid(accent, 0.75, 0.6, 0.7),
        vendingBody = mcHslToHex(b2.copy(s = min(0.62, max(0.35, b2.s)), l = 0.42)),
        vendingAccent = machiVivid(brand[0], 0.6, 0.48, 0.6),
        products = resolveProducts(theme, brand, seed),
    )
}
