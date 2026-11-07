// 对应 Folia 文件：src/components/visualizer/claddagh/
//   claddaghTimeline.ts（逐字时间修补、分数下标、行内偏移、进度）
//   claddaghLayout.ts（逐字弧长、阅读角）
//   CladdaghRingLine.tsx（每字在倾斜椭圆环上的位置 / 缩放 / 透明度 / 颜色 / 辉光，逐条照抄）
//   tuning.ts、src/types.ts（CladdaghTuning）
//
// V2e「回环」：每句歌词排在一条压扁、倾斜的椭圆轨道上，句与句相隔 180°，当前字转到焦点。
package com.guangyue.core.lyrics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class CladdaghTuning(
    val focusScaleRatio: Double = 0.65,
    val radiusScale: Double = 1.0,
    val ellipseTiltDeg: Double = 45.0,
    val showAxisLine: Boolean = true,
    val letterSpacingOffset: Double = 0.0,
) {
    companion object {
        val DEFAULT = CladdaghTuning()
        private fun r(v: Double?, lo: Double, hi: Double, f: Double) = if (v == null || !v.isFinite()) f else v.coerceIn(lo, hi)
        fun sanitize(focus: Double?, radius: Double?, tilt: Double?, axis: Boolean?, spacing: Double?) = CladdaghTuning(
            focusScaleRatio = r(focus, 0.0, 1.5, DEFAULT.focusScaleRatio),
            radiusScale = r(radius, 0.5, 1.5, DEFAULT.radiusScale),
            ellipseTiltDeg = r(tilt, 0.0, 60.0, DEFAULT.ellipseTiltDeg),
            showAxisLine = axis ?: DEFAULT.showAxisLine,
            letterSpacingOffset = r(spacing, -5.0, 20.0, DEFAULT.letterSpacingOffset),
        )
    }
}

data class CladdaghGlyph(
    val char: String,
    val startTime: Double,
    val endTime: Double,
    val nominalAngle: Double,
    val scaleFactor: Double,
)

/** 一个字一帧的结果（中心偏移、阅读角、缩放、透明度、颜色进度、辉光半径）。 */
data class CladdaghGlyphFrame(
    val x: Double,
    val y: Double,
    val rotate: Double,
    val scale: Double,
    val opacity: Double,
    /** 0 = 基色（primary@0.55），1 = 高亮色。 */
    val colorMix: Double,
    val alpha: Double,
    val glowRadius: Double,
    val blur: Double,
)

object Claddagh {
    private const val MAX_ARC_SPAN = 4.25
    const val LETTER_SPACING_EM = 0.04
    private const val BASE_TRACKING_EM = 0.18
    const val BACK_FOLLOW_RATIO = 0.28
    const val BACK_ORBIT_FOLLOW_RATIO = 0.52
    const val NEXT_LINE_ENTRY_LEAD = 0.34
    private val CJK = Regex("[\\u4e00-\\u9fa5\\u3040-\\u30ff\\uac00-\\ud7af]")

    fun clamp(v: Double, a: Double, b: Double) = max(a, min(b, v))

    fun normalizeReadableAngle(deg: Double): Double {
        var n = deg
        while (n > 90) n -= 180
        while (n < -90) n += 180
        return n
    }

    fun visualLength(text: String): Double = text.sumOf { if (CJK.matches(it.toString())) 1.0 else 0.5 }

    /** 对应 adjustCladdaghTimeline：零时长（空格等）字平分相邻空隙，必要时向两侧借时间（每字至少 60ms）。 */
    fun adjustTimeline(src: List<GraphemeTiming>, line: Line): List<GraphemeTiming> {
        if (src.isEmpty()) return src
        val a = src.map { doubleArrayOf(it.startTime, it.endTime) }
        val n = a.size
        var i = 0
        while (i < n) {
            if (a[i][0] == a[i][1]) {
                var j = i
                while (j < n && a[j][0] == a[j][1]) j++
                val gap = j - i
                var gs = if (i > 0) a[i - 1][1] else line.startTime
                var ge = if (j < n) a[j][0] else line.endTime
                val need = gap * 0.06
                var d = ge - gs
                if (d < need) {
                    val deficit = need - d
                    if (i > 0 && j < n) {
                        val ps = min(deficit / 2, max(0.0, a[i - 1][1] - a[i - 1][0] - 0.04))
                        val ns = min(deficit - ps, max(0.0, a[j][1] - a[j][0] - 0.04))
                        gs -= ps; ge += ns; a[i - 1][1] = gs; a[j][0] = ge
                    } else if (i > 0) {
                        val ps = min(deficit, max(0.0, a[i - 1][1] - a[i - 1][0] - 0.04))
                        gs -= ps; a[i - 1][1] = gs
                    } else if (j < n) {
                        val ns = min(deficit, max(0.0, a[j][1] - a[j][0] - 0.04))
                        ge += ns; a[j][0] = ge
                    }
                    d = ge - gs
                }
                val unit = if (d > 0) d / gap else 0.0
                for (k in i until j) {
                    a[k][0] = gs + unit * (k - i); a[k][1] = gs + unit * (k - i + 1)
                }
                i = j
            } else i++
        }
        return src.mapIndexed { k, g -> g.copy(startTime = a[k][0], endTime = a[k][1]) }
    }

    /** 对应 buildMeasuredSpacingInfo。[prefixWidth] = 前 n 个字连写宽度（含 0.04em 字距）。 */
    fun spacing(
        timeline: List<GraphemeTiming>,
        fontPx: Double,
        radiusPx: Double,
        spacingScale: Double,
        letterSpacingOffsetPx: Double,
        prefixWidth: (String) -> Double,
    ): List<CladdaghGlyph> {
        if (timeline.isEmpty()) return emptyList()
        val chars = timeline.map { it.char }
        val offsets = DoubleArray(chars.size + 1)
        var fallback = 0.0
        for (idx in 1..chars.size) {
            val c = chars[idx - 1]
            fallback += when {
                c.isBlank() -> fontPx * 0.36
                CJK.containsMatchIn(c) -> fontPx
                else -> fontPx * 0.62
            }
            val measured = prefixWidth(chars.subList(0, idx).joinToString("")).let { if (it.isFinite() && it > 0) it else fallback }
            val tracking = max(0, idx - 1) * fontPx * BASE_TRACKING_EM
            val extra = max(0, idx - 1) * letterSpacingOffsetPx
            offsets[idx] = max(offsets[idx - 1], measured + tracking + extra)
        }
        val s = if (spacingScale.isFinite()) max(0.1, spacingScale) else 1.0
        val total = offsets[chars.size] * s
        val r = maxOf(radiusPx, fontPx * 2, 1.0)
        val span = total / r
        val sf = if (span > MAX_ARC_SPAN) MAX_ARC_SPAN / span else 1.0
        return timeline.mapIndexed { idx, t ->
            val center = (offsets[idx] + offsets[idx + 1]) / 2 * s
            val start = center / r
            CladdaghGlyph(t.char, t.startTime, t.endTime, (start - span / 2) * sf, sf)
        }
    }

    private fun fractionalIndex(tl: List<CladdaghGlyph>, t: Double, renderEnd: Double?): Double {
        if (tl.isEmpty()) return 0.0
        if (tl.size == 1) {
            val it0 = tl[0]
            val end = renderEnd?.takeIf { it.isFinite() } ?: it0.endTime
            if (t <= it0.startTime) return 0.0
            return (t - it0.startTime) / max(0.2, end - it0.startTime)
        }
        if (t <= tl[0].startTime) return 0.0
        val last = tl.size - 1
        if (t >= tl[last].startTime) {
            val li = tl[last]
            if (renderEnd != null && renderEnd.isFinite() && renderEnd > li.startTime) {
                return last + clamp((t - li.startTime) / (renderEnd - li.startTime), 0.0, 1.0) * 2.0
            }
            val itemDur = li.endTime - li.startTime
            val gapDur = li.startTime - tl[last - 1].startTime
            val step = if (itemDur > 0) itemDur else if (gapDur > 0) gapDur else 0.5
            return last + min((t - li.startTime) / step, 1.8)
        }
        for (i in 0 until last) {
            val a = tl[i].startTime
            val b = tl[i + 1].startTime
            if (t >= a && t < b) return if (b == a) i.toDouble() else i + (t - a) / (b - a)
        }
        return last.toDouble()
    }

    fun wordOffset(tl: List<CladdaghGlyph>, t: Double, renderEnd: Double?): Double {
        if (tl.isEmpty()) return 0.0
        val f = fractionalIndex(tl, t, renderEnd)
        val last = tl.size - 1
        if (f <= last) {
            val ip = floor(f).toInt()
            val fp = f - ip
            val a = tl[ip].nominalAngle
            val b = tl[min(ip + 1, last)].nominalAngle
            return a + (b - a) * fp
        }
        val la = tl[last].nominalAngle
        val pa = tl[max(0, last - 1)].nominalAngle
        return la + (la - pa) * (f - last)
    }

    fun playbackProgress(tl: List<CladdaghGlyph>, t: Double, renderEnd: Double?): Double {
        if (tl.isEmpty()) return 0.0
        if (tl.size == 1) {
            val it0 = tl[0]
            val end = renderEnd?.takeIf { it.isFinite() } ?: it0.endTime
            return clamp((t - it0.startTime) / max(0.001, end - it0.startTime), 0.0, 1.0)
        }
        return clamp(fractionalIndex(tl, t, renderEnd) / max(1, tl.size - 1), 0.0, 1.0)
    }

    fun renderEnd(lines: List<Line>, index: Int): Double? {
        val l = lines.getOrNull(index) ?: return null
        val next = lines.getOrNull(index + 1)
        return max(l.renderHints?.renderEndTime ?: l.endTime, next?.let { it.startTime - NEXT_LINE_ENTRY_LEAD } ?: (l.endTime + 10.0))
    }

    /**
     * 一行所有字的一帧（对应 RingLine handler）。无音频：power = 0（scaleFactor = 1）。
     */
    fun frame(
        glyphs: List<CladdaghGlyph>,
        line: Line,
        lineIndex: Int,
        centerLineIndex: Int,
        renderBaseIndex: Int,
        lines: List<Line>,
        activeGlyphs: List<CladdaghGlyph>,
        time: Double,
        lineOffset: Double,
        rx: Double,
        fontPx: Double,
        tuning: CladdaghTuning,
    ): List<CladdaghGlyphFrame?> {
        if (glyphs.isEmpty()) return emptyList()
        val isUpcoming = lineIndex > centerLineIndex
        val entry = if (isUpcoming) clamp((time - (line.startTime - NEXT_LINE_ENTRY_LEAD)) / NEXT_LINE_ENTRY_LEAD, 0.0, 1.0) else 0.0
        val diff = abs(lineOffset - lineIndex * PI) / PI
        val curLen = lines.getOrNull(centerLineIndex)?.let { visualLength(it.fullText) } ?: 0.0
        val tgtLen = lines.getOrNull(lineIndex)?.let { visualLength(it.fullText) } ?: 0.0
        var lenFade = 1.0
        var lenScale = 1.0
        if (lineIndex > centerLineIndex && curLen > 10) {
            val combined = clamp((curLen - 10) / 8, 0.0, 1.0) * clamp((tgtLen - 5) / 5, 0.4, 1.0)
            val tp = clamp((diff - 0.4) / 0.5, 0.0, 1.0)
            lenFade = 1.0 - combined * tp
            lenScale = 1.0 - combined * 0.25 * tp
        }
        val activeOff = wordOffset(activeGlyphs, time, renderEnd(lines, renderBaseIndex))
        val ownOff = wordOffset(glyphs, time, renderEnd(lines, lineIndex))
        val activeProg = playbackProgress(activeGlyphs, time, renderEnd(lines, renderBaseIndex))
        val backOrbit = PI * BACK_ORBIT_FOLLOW_RATIO * (1 - (1 - activeProg).pow(1.35))
        var off = ownOff
        if (lineIndex >= centerLineIndex) {
            val factor = if (lineIndex > centerLineIndex) 1.0 else clamp(diff, 0.0, 1.0)
            off += (activeOff * BACK_FOLLOW_RATIO + backOrbit) * factor
        }
        val rMajor = rx
        val rMinor = rx * 0.09
        val rot = -(tuning.ellipseTiltDeg * PI) / 180
        val ct = cos(rot)
        val st = sin(rot)
        return glyphs.mapIndexed { i, g ->
            val psi = lineIndex * PI + g.nominalAngle - lineOffset - off
            val dd = psi * rx
            val th = dd / rMajor
            val lc = cos(th)
            val dD = (lc + 1) / 2
            val sp = 0.35 + 0.65 * dD.pow(1.2)
            val rawX = sin(th) * rMajor * sp
            var rawY = lc * rMinor
            if (line.isChorus) rawY += (if (i % 2 == 0) 1 else -1) * fontPx * 0.06
            val x = rawX * ct - rawY * st
            val y = rawX * st + rawY * ct
            val tx = cos(th) * rMajor
            val ty = -sin(th) * rMinor
            val tangent = normalizeReadableAngle(atan2(tx * st + ty * ct, tx * ct - ty * st) * 180 / PI)
            val af = max(0.0, 1 - diff)
            val F = af * (1 - min(1.0, abs(dd) / (rx * 0.48))).pow(1.8)
            var op = (0.35 + 0.65 * dD.pow(1.5) * (0.35 + 0.65 * F)) * (0.68 + 0.32 * dD.pow(1.9))
            op *= clamp(2 - diff, 0.0, 1.0)
            if (lineIndex < centerLineIndex) op *= max(0.0, 1 - diff)
            op *= lenFade
            if (isUpcoming) op *= 0.18 + 0.82 * entry
            if (diff > 0.02) {
                val p = clamp((diff - 0.3) / 0.6, 0.0, 1.0)
                val thr = 1.0 - 1.2 * p
                if (lc > thr) op *= clamp(1.0 - (lc - thr) / 0.15, 0.0, 1.0)
            }
            if (op < 0.0005) return@mapIndexed null
            val scale = (0.22 + 0.98 * dD.pow(1.5)) * (1.0 + tuning.focusScaleRatio * F) * lenScale * g.scaleFactor
            val blur = 8.0 * (1 - dD) * (1 - 0.5 * F)
            val tilt = clamp(tangent * (0.4 + 0.6 * dD), -38.0, 38.0)
            val prog = when {
                time >= g.endTime -> 1.0
                time > g.startTime -> (g.endTime - g.startTime).let { if (it > 0) (time - g.startTime) / it else 1.0 }
                else -> 0.0
            }
            val baseGlow = if (line.isChorus) 36 * F.pow(1.5) else 24 * F.pow(2.0)
            var mixT = 0.0
            var alpha = 0.55
            var pop = 0.0
            if (prog >= 1) {
                mixT = 1.0; alpha = 1.0
            } else if (prog > 0) {
                val pf = min(1.0, prog * 15.0)
                alpha = 0.55 + 0.45 * pf
                mixT = pf
                if (prog < 0.4) pop = (1 - prog / 0.4).pow(2.0) * 0.65
            }
            CladdaghGlyphFrame(x, y, tilt, scale, op, mixT, alpha, baseGlow * (1 + pop), blur)
        }
    }
}
