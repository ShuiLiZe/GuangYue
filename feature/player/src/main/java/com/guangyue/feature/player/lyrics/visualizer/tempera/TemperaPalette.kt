// 凝彩调色：逐函数对译 Folia temperaPalette.ts（duo 模式，Folia 默认）。纸色 = 主题背景，墨色 = 主色（与纸亮度差不足 96 就推向黑 / 白），
// 四档色阶 tone1..4 = 纸→墨 0.12/0.3/0.52/0.72，tone1/tone3 混 30% 强调色、tone2 混 30% 副色后再拉回原档亮度。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** 0..255 的 RGB。 */
internal class TpRgb(val r: Int, val g: Int, val b: Int) {
    val luminance get() = r * 0.2126 + g * 0.7152 + b * 0.0722
    fun argb(alpha: Double = 1.0): Int = ((floor(alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
    companion object {
        fun ofArgb(c: Int) = TpRgb((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff)
    }
}

internal class TpPalette(
    val paper: TpRgb,
    val ink: TpRgb,
    val accent: TpRgb,
    val tone1: TpRgb,
    val tone2: TpRgb,
    val tone3: TpRgb,
    val tone4: TpRgb,
    /** line = mix(paper, ink, 0.6) 的 50% 透明。 */
    val line: TpRgb,
    val lineAlpha: Double,
) {
    fun of(tone: TpTone): TpRgb = when (tone) {
        TpTone.PAPER -> paper
        TpTone.INK -> ink
        TpTone.TONE1 -> tone1
        TpTone.TONE2 -> tone2
        TpTone.TONE3 -> tone3
        TpTone.TONE4 -> tone4
        TpTone.LINE -> line
    }
    fun alphaOf(tone: TpTone) = if (tone == TpTone.LINE) lineAlpha else 1.0
}

private fun jr(v: Double) = floor(v + 0.5).toInt()

internal fun tpMix(from: TpRgb, to: TpRgb, amount: Double): TpRgb {
    val a = amount.coerceIn(0.0, 1.0)
    return TpRgb(jr(from.r + (to.r - from.r) * a), jr(from.g + (to.g - from.g) * a), jr(from.b + (to.b - from.b) * a))
}

private val TONE_STOPS = doubleArrayOf(0.12, 0.3, 0.52, 0.72)

private fun ensureInkContrast(paper: TpRgb, ink: TpRgb): TpRgb {
    if (abs(ink.luminance - paper.luminance) >= 96) return ink
    val target = if (paper.luminance < 128) TpRgb(0xf4, 0xf4, 0xf2) else TpRgb(0x14, 0x14, 0x14)
    val tinted = tpMix(target, ink, 0.14)
    return if (abs(tinted.luminance - paper.luminance) >= 96) tinted else target
}

private fun matchLuminance(color: TpRgb, target: TpRgb): TpRgb {
    val current = color.luminance
    val wanted = target.luminance
    if (current <= 0.5) return target
    val gain = wanted / current
    val scaled = TpRgb(jr(min(255.0, color.r * gain)), jr(min(255.0, color.g * gain)), jr(min(255.0, color.b * gain)))
    if (abs(scaled.luminance - wanted) > 1.5) return target
    return scaled
}

internal fun resolveTemperaPalette(background: TpRgb, primary: TpRgb, accent: TpRgb, secondary: TpRgb): TpPalette {
    val paper = background
    val ink = ensureInkContrast(paper, primary)
    fun step(i: Int, tint: TpRgb?): TpRgb {
        val base = tpMix(paper, ink, TONE_STOPS[i])
        return if (tint != null) matchLuminance(tpMix(base, tint, 0.3), base) else base
    }
    return TpPalette(
        paper = paper, ink = ink, accent = accent,
        tone1 = step(0, accent), tone2 = step(1, secondary), tone3 = step(2, accent), tone4 = step(3, null),
        line = tpMix(paper, ink, 0.6), lineAlpha = 0.5,
    )
}
