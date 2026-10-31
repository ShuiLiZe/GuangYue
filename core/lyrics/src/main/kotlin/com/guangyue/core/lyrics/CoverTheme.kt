// 对应 Folia 文件：
//   src/utils/builtinTheme/coverPaletteAnalysis.ts（封面像素 → base 色相 / support 色相 / 配色方案 / 基调）
//   src/utils/builtinTheme/themeColorRanges.ts（色相 / 饱和度 / 明度的取值范围）
//   src/utils/builtinTheme/generateBuiltinDualTheme.ts（按取色生成 backgroundColor / primaryColor / accentColor / secondaryColor）
//
// 纯 Kotlin、无 Android 依赖：色相/明度/对比度全部按 Folia 的 HSL 口径算，
// 这样「封面取色 → 流体背景色」这一层可以跑 JVM 单测，Compose 只负责画。
package com.guangyue.core.lyrics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** RGB（0–255）。 */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    val r01: Double get() = r / 255.0
    val g01: Double get() = g / 255.0
    val b01: Double get() = b / 255.0

    /** 与另一色的欧氏距离（0–441.67）。 */
    fun distanceTo(other: Rgb): Double {
        val dr = (r - other.r).toDouble()
        val dg = (g - other.g).toDouble()
        val db = (b - other.b).toDouble()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }
}

/** HSL，h ∈ [0,360)、s/l ∈ [0,1]。Folia 的 coverPaletteAnalysis / themeColorRanges 都用这套口径。 */
data class Hsl(val h: Double, val s: Double, val l: Double)

object ColorMath {
    /** colorPalette.ts: rgb2hsl。 */
    fun rgbToHsl(rgb: Rgb): Hsl {
        val r = rgb.r01
        val g = rgb.g01
        val b = rgb.b01
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val l = (maxV + minV) / 2.0
        if (maxV == minV) return Hsl(0.0, 0.0, l)
        val d = maxV - minV
        val s = if (l > 0.5) d / (2.0 - maxV - minV) else d / (maxV + minV)
        val hue = when (maxV) {
            r -> ((g - b) / d + if (g < b) 6.0 else 0.0) * 60.0
            g -> ((b - r) / d + 2.0) * 60.0
            else -> ((r - g) / d + 4.0) * 60.0
        }
        return Hsl(hue % 360.0, s, l)
    }

    /** themeColorRanges.ts: hslToHex。 */
    fun hslToRgb(hsl: Hsl): Rgb {
        val h = ((hsl.h % 360.0) + 360.0) % 360.0
        val s = hsl.s.coerceIn(0.0, 1.0)
        val l = hsl.l.coerceIn(0.0, 1.0)
        if (s <= 0.0) {
            val v = (l * 255.0).roundToInt()
            return Rgb(v, v, v)
        }
        val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
        val p = 2.0 * l - q
        fun hue2rgb(t0: Double): Double {
            var t = t0
            if (t < 0.0) t += 1.0
            if (t > 1.0) t -= 1.0
            return when {
                t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
                t < 0.5 -> q
                t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
                else -> p
            }
        }
        val r = (hue2rgb(h / 360.0 + 1.0 / 3.0) * 255.0).roundToInt()
        val g = (hue2rgb(h / 360.0) * 255.0).roundToInt()
        val b = (hue2rgb(h / 360.0 - 1.0 / 3.0) * 255.0).roundToInt()
        return Rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** WCAG 相对亮度（generateBuiltinDualTheme.ts: getRelativeLuminance）。 */
    fun relativeLuminance(rgb: Rgb): Double {
        fun channel(c: Double): Double = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(rgb.r01) + 0.7152 * channel(rgb.g01) + 0.0722 * channel(rgb.b01)
    }

    /** generateBuiltinDualTheme.ts: getContrastRatio。 */
    fun contrastRatio(a: Rgb, b: Rgb): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 色相距离（环形，0–180）。 */
    fun hueDistance(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    /** generateBuiltinDualTheme.ts: lerpHue（沿最短弧插值）。 */
    fun lerpHue(a: Double, b: Double, t: Double): Double {
        val delta = ((b - a + 540.0) % 360.0) - 180.0
        return ((a + delta * t) % 360.0 + 360.0) % 360.0
    }

    /** 数值区间插值（themeColorRanges: between）。 */
    fun between(range: ClosedFloatingPointRange<Double>, t: Double): Double =
        range.start + (range.endInclusive - range.start) * t.coerceIn(0.0, 1.0)
}

/** Folia themeColorRanges.ts 的全部常量。 */
object ThemeColorRanges {
    const val MIN_BACKGROUND_SATURATION: Double = 0.04

    /** 背景基调（ink / tinted / rich）。 */
    data class Tone(
        val darkL: ClosedFloatingPointRange<Double>,
        val darkS: ClosedFloatingPointRange<Double>,
        val lightL: ClosedFloatingPointRange<Double>,
        val lightS: ClosedFloatingPointRange<Double>,
    )

    val INK = Tone(
        darkL = 0.055..0.085, darkS = 0.10..0.22,
        lightL = 0.940..0.965, lightS = 0.05..0.12,
    )
    val TINTED = Tone(
        darkL = 0.075..0.110, darkS = 0.20..0.36,
        lightL = 0.920..0.950, lightS = 0.10..0.20,
    )
    val RICH = Tone(
        darkL = 0.095..0.140, darkS = 0.32..0.50,
        lightL = 0.900..0.935, lightS = 0.18..0.30,
    )

    val TONES: List<Pair<Tone, Double>> = listOf(INK to 3.0, TINTED to 4.0, RICH to 2.5)

    val PRIMARY_LIGHT_S: ClosedFloatingPointRange<Double> = 0.15..0.40
    val PRIMARY_LIGHT_L: ClosedFloatingPointRange<Double> = 0.10..0.20
    val PRIMARY_DARK_S: ClosedFloatingPointRange<Double> = 0.10..0.30
    val PRIMARY_DARK_L: ClosedFloatingPointRange<Double> = 0.90..0.96

    val ACCENT_LIGHT_S: ClosedFloatingPointRange<Double> = 0.55..0.88
    val ACCENT_LIGHT_L: ClosedFloatingPointRange<Double> = 0.38..0.55
    val ACCENT_DARK_S: ClosedFloatingPointRange<Double> = 0.55..0.92
    val ACCENT_DARK_L: ClosedFloatingPointRange<Double> = 0.55..0.72

    val SECONDARY_LIGHT_S: ClosedFloatingPointRange<Double> = 0.15..0.45
    val SECONDARY_LIGHT_L: ClosedFloatingPointRange<Double> = 0.32..0.46
    val SECONDARY_DARK_S: ClosedFloatingPointRange<Double> = 0.15..0.45
    val SECONDARY_DARK_L: ClosedFloatingPointRange<Double> = 0.62..0.76

    const val PRIMARY_MIN_CONTRAST: Double = 9.0
    const val ACCENT_MIN_CONTRAST: Double = 3.2
    const val SECONDARY_MIN_CONTRAST: Double = 4.5
}

/** 配色方案（coverPaletteAnalysis.ts: pickScheme）。 */
enum class CoverScheme { DUOTONE, ANALOGOUS, COMPLEMENTARY, SPLIT, TRIADIC, MONOCHROME }

/** 封面取色结论：base 色相 + 可选 support 色相 + 方案。 */
data class CoverPaletteAnalysis(
    val baseHue: Double,
    val baseSaturation: Double,
    val supportHue: Double?,
    val scheme: CoverScheme,
    val colorCount: Int,
)

/** Folia coverPaletteAnalysis.ts 的取色判定。所有随机量都由 [rng] 给，便于单测。 */
object CoverPaletteAnalyzer {
    /** 单色评分：饱和度越高、明度越居中的颜色越适合作基调。 */
    fun colorScore(hsl: Hsl): Double = hsl.s * max(0.0, 1.0 - abs(hsl.l - 0.5) * 1.2)

    /** 取评分最高的颜色作 base。空输入返回 null。 */
    fun pickBaseColor(colors: List<Hsl>): Hsl? = colors.maxByOrNull { colorScore(it) }

    /** 支撑色：饱和度 ≥0.15 且与 base 的色相距离在 25–160 之间，取饱和度最高者。 */
    fun pickSupportHue(colors: List<Hsl>, baseHue: Double): Double? = colors
        .filter { it.s >= 0.15 }
        .filter { hueDistanceOk(baseHue, it.h) }
        .maxByOrNull { it.s }
        ?.h

    private fun hueDistanceOk(baseHue: Double, hue: Double): Boolean {
        val d = ColorMath.hueDistance(baseHue, hue)
        return d in 25.0..160.0
    }

    /**
     * 方案：有支撑色且 r<0.65 → duotone；否则按权重抽
     * analogous 3 / complementary 2 / split 2 / triadic 1.5 / monochrome 2.5（仅低饱和可用）。
     */
    fun pickScheme(hasSupport: Boolean, saturation: Double, rng: Double): CoverScheme {
        if (hasSupport && rng < 0.65) return CoverScheme.DUOTONE
        val candidates = mutableListOf<Triple<CoverScheme, Double, Double>>()
        val n = rng * 10.0
        var acc = 0.0
        fun push(scheme: CoverScheme, weight: Double) {
            acc += weight
            candidates += Triple(scheme, acc, weight)
        }
        push(CoverScheme.ANALOGOUS, 3.0)
        push(CoverScheme.COMPLEMENTARY, 2.0)
        push(CoverScheme.SPLIT, 2.0)
        push(CoverScheme.TRIADIC, 1.5)
        if (saturation < 0.18) push(CoverScheme.MONOCHROME, 2.5)
        val total = acc
        var cursor = (n % total)
        for ((scheme, upper, _) in candidates) {
            if (cursor < upper) return scheme
        }
        return candidates.last().first
    }

    /** accent 预选色相（resolveAccentHue）。 */
    fun resolveAccentHue(scheme: CoverScheme, baseHue: Double, supportHue: Double?, rng: Double): Double {
        val jitter = rng * 2.0 - 1.0
        return when (scheme) {
            CoverScheme.ANALOGOUS -> baseHue + jitter * 15.0 + if (jitter >= 0) 20.0 else -20.0
            CoverScheme.COMPLEMENTARY -> baseHue + 150.0 + (rng * 30.0)
            CoverScheme.SPLIT -> baseHue + if (rng < 0.5) 150.0 else 210.0
            CoverScheme.TRIADIC -> baseHue + if (rng < 0.5) 120.0 else 240.0
            CoverScheme.MONOCHROME -> baseHue
            CoverScheme.DUOTONE -> supportHue ?: baseHue
        }
    }

    /** 基调权重（ink 3 / tinted 4 / rich 2.5）。 */
    fun pickTone(rng: Double): ThemeColorRanges.Tone {
        val total = ThemeColorRanges.TONES.sumOf { it.second }
        var cursor = (rng * total) % total
        for ((tone, weight) in ThemeColorRanges.TONES) {
            if (cursor < weight) return tone
            cursor -= weight
        }
        return ThemeColorRanges.TINTED
    }

    /** 完整分析：空列表 → null（调用方退回内置兜底色）。 */
    fun analyze(rgbs: List<Rgb>, rng: Double = 0.42): CoverPaletteAnalysis? {
        if (rgbs.isEmpty()) return null
        val hsls = rgbs.map { ColorMath.rgbToHsl(it) }
        val base = pickBaseColor(hsls) ?: return null
        val support = pickSupportHue(hsls, base.h)
        val scheme = pickScheme(support != null, base.s, rng)
        return CoverPaletteAnalysis(
            baseHue = base.h,
            baseSaturation = base.s,
            supportHue = support,
            scheme = scheme,
            colorCount = rgbs.size,
        )
    }
}

/**
 * 一套歌词配色（对应 Folia Theme 的四个颜色字段）。
 *
 * [fromCover] 为 false 时是内置兜底（没有封面像素可用），四个色按 Folia 中性色给。
 */
data class LyricTheme(
    val backgroundColor: Rgb,
    val primaryColor: Rgb,
    val accentColor: Rgb,
    val secondaryColor: Rgb,
    val fromCover: Boolean,
)

/**
 * generateBuiltinDualTheme.ts：从封面取色结果生成四个颜色。
 *
 * - backgroundColor：baseHue + 基调饱和度/明度，饱和度按封面饱和度加权（coverWeight 0.55→1.0）；
 * - primaryColor：同色相、低饱和，对比度抬到 ≥9；
 * - accentColor：accent 色相，对比度抬到 ≥3.2；
 * - secondaryColor：base 与 accent 之间的插值色相，对比度抬到 ≥4.5。
 */
object BuiltinDualTheme {
    fun coverWeight(baseSaturation: Double): Double = 0.55 + 0.45 * kotlin.math.min(1.0, max(0.0, baseSaturation / 0.7))

    fun buildBackgroundColor(analysis: CoverPaletteAnalysis, dark: Boolean, rng: Double): Rgb {
        val tone = CoverPaletteAnalyzer.pickTone(rng)
        val satRange = if (dark) tone.darkS else tone.lightS
        val lightRange = if (dark) tone.darkL else tone.lightL
        val weight = coverWeight(analysis.baseSaturation)
        val s = max(ThemeColorRanges.MIN_BACKGROUND_SATURATION, ColorMath.between(satRange, rng) * weight)
        val l = ColorMath.between(lightRange, 1.0 - rng)
        return ColorMath.hslToRgb(Hsl(analysis.baseHue, s, l))
    }

    /** 抬明度直到对比度达标（深色字体往暗抬，浅色字体往亮抬）。 */
    fun adjustLightnessForContrast(hsl: Hsl, background: Rgb, minContrast: Double): Rgb {
        val onDarkBackground = ColorMath.relativeLuminance(background) < 0.4
        var best = ColorMath.hslToRgb(hsl)
        if (ColorMath.contrastRatio(best, background) >= minContrast) return best
        val steps = 20
        for (i in 1..steps) {
            val delta = i / steps.toDouble()
            val candidate = hsl.copy(l = if (onDarkBackground) min(1.0, hsl.l + delta * (1.0 - hsl.l)) else max(0.0, hsl.l * (1.0 - delta)))
            best = ColorMath.hslToRgb(candidate)
            if (ColorMath.contrastRatio(best, background) >= minContrast) return best
        }
        return best
    }

    fun buildAccentHue(analysis: CoverPaletteAnalysis, rng: Double): Double =
        CoverPaletteAnalyzer.resolveAccentHue(analysis.scheme, analysis.baseHue, analysis.supportHue, rng)

    /**
     * 完整生成。[rng] 只有一个随机源（0–1），按固定顺序消费，保证同封面同主题。
     */
    fun generate(analysis: CoverPaletteAnalysis?, dark: Boolean, rng: Double = 0.42): LyricTheme {
        if (analysis == null) return fallback(dark)
        val background = buildBackgroundColor(analysis, dark, rng)
        val primaryRange = if (dark) ThemeColorRanges.PRIMARY_DARK_L else ThemeColorRanges.PRIMARY_LIGHT_L
        val primarySat = if (dark) ThemeColorRanges.PRIMARY_DARK_S else ThemeColorRanges.PRIMARY_LIGHT_S
        val primary = adjustLightnessForContrast(
            Hsl(analysis.baseHue, ColorMath.between(primarySat, rng), ColorMath.between(primaryRange, rng)),
            background,
            ThemeColorRanges.PRIMARY_MIN_CONTRAST,
        )
        val accentRange = if (dark) ThemeColorRanges.ACCENT_DARK_L else ThemeColorRanges.ACCENT_LIGHT_L
        val accentSat = if (dark) ThemeColorRanges.ACCENT_DARK_S else ThemeColorRanges.ACCENT_LIGHT_S
        var accentHue = buildAccentHue(analysis, rng)
        var accent = adjustLightnessForContrast(
            Hsl(accentHue, ColorMath.between(accentSat, rng), ColorMath.between(accentRange, rng)),
            background,
            ThemeColorRanges.ACCENT_MIN_CONTRAST,
        )
        // accent 与 primary 撞色（色相距离 <15 且对比 <1.3）→ 色相 +40
        if (ColorMath.hueDistance(accentHue, analysis.baseHue) < 15.0 &&
            ColorMath.contrastRatio(accent, primary) < 1.3
        ) {
            accentHue += 40.0
            accent = adjustLightnessForContrast(
                Hsl(accentHue, ColorMath.between(accentSat, rng), ColorMath.between(accentRange, rng)),
                background,
                ThemeColorRanges.ACCENT_MIN_CONTRAST,
            )
        }
        val secondaryRange = if (dark) ThemeColorRanges.SECONDARY_DARK_L else ThemeColorRanges.SECONDARY_LIGHT_L
        val secondarySat = if (dark) ThemeColorRanges.SECONDARY_DARK_S else ThemeColorRanges.SECONDARY_LIGHT_S
        val secondaryHue = ColorMath.lerpHue(analysis.baseHue, accentHue, 0.2 + rng * 0.4)
        val secondary = adjustLightnessForContrast(
            Hsl(secondaryHue, ColorMath.between(secondarySat, rng), ColorMath.between(secondaryRange, rng)),
            background,
            ThemeColorRanges.SECONDARY_MIN_CONTRAST,
        )
        return LyricTheme(
            backgroundColor = background,
            primaryColor = primary,
            accentColor = accent,
            secondaryColor = secondary,
            fromCover = true,
        )
    }

    /** 没有封面像素时的兜底（Folia：baseHue 随手挑，仍是同一套 range 生成）。 */
    fun fallback(dark: Boolean): LyricTheme {
        val analysis = CoverPaletteAnalysis(
            baseHue = 22.0,
            baseSaturation = 0.0,
            supportHue = null,
            scheme = CoverScheme.MONOCHROME,
            colorCount = 0,
        )
        val background = ColorMath.hslToRgb(
            Hsl(
                analysis.baseHue,
                ThemeColorRanges.MIN_BACKGROUND_SATURATION,
                ColorMath.between(if (dark) ThemeColorRanges.INK.darkL else ThemeColorRanges.INK.lightL, 0.5),
            ),
        )
        val primary = adjustLightnessForContrast(
            Hsl(
                analysis.baseHue,
                ThemeColorRanges.PRIMARY_LIGHT_S.start,
                if (dark) 0.95 else 0.14,
            ),
            background,
            ThemeColorRanges.PRIMARY_MIN_CONTRAST,
        )
        val accent = adjustLightnessForContrast(
            Hsl(18.0, 0.86, if (dark) 0.63 else 0.47),
            background,
            ThemeColorRanges.ACCENT_MIN_CONTRAST,
        )
        val secondary = adjustLightnessForContrast(
            Hsl(analysis.baseHue, 0.24, if (dark) 0.68 else 0.39),
            background,
            ThemeColorRanges.SECONDARY_MIN_CONTRAST,
        )
        return LyricTheme(background, primary, accent, secondary, fromCover = false)
    }
}
