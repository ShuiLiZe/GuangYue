// 对应 Folia 文件：src/components/visualizer/tilt/VisualizerTilt.tsx（纯逻辑部分）、tilt/tuning.ts、src/types.ts（TiltTuning）
//
// V2d「倾诉」：长句按字数与种子拆成 1–4 段竖排，随机一段斜体强调；每段到点才出现，字随演唱脉动放大。
package com.guangyue.core.lyrics

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class TiltColorScheme(val id: String) { DEFAULT("default"), SWAP("swap"), ACCENT_ALL("accentAll"), PRIMARY_ALL("primaryAll");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT }
}

data class TiltTuning(
    val splitProbability: Double = 0.75,
    val tiltStyleProbability: Double = 0.35,
    val colorScheme: TiltColorScheme = TiltColorScheme.DEFAULT,
) {
    companion object {
        val DEFAULT = TiltTuning()
        fun sanitize(split: Double?, tilt: Double?, scheme: TiltColorScheme?) = TiltTuning(
            splitProbability = (split ?: DEFAULT.splitProbability).let { if (it.isFinite()) it.coerceIn(0.0, 1.0) else DEFAULT.splitProbability },
            tiltStyleProbability = (tilt ?: DEFAULT.tiltStyleProbability).let { if (it.isFinite()) it.coerceIn(0.0, 1.0) else DEFAULT.tiltStyleProbability },
            colorScheme = scheme ?: DEFAULT.colorScheme,
        )
    }
}

data class TiltSegment(val text: String, val isTilt: Boolean, val isShortLastLine: Boolean, val charOffset: Int)
data class TiltLayout(val segments: List<TiltSegment>, val scaleMultiplier: Double)
data class TiltCharTiming(val charIndex: Int, val startTime: Double, val endTime: Double)

object Tilt {
    const val REM_PX = 16.0
    private const val CHAR_REF_LENGTH = 20.0
    private const val LOG_OFFSET = 4.0
    private val LINE_THRESHOLDS = doubleArrayOf(0.45, 1.05, 1.7)
    private const val RESPLIT_THRESHOLD = 1.6
    private const val SCALE_FLOOR_NORMAL = 0.55
    private const val SCALE_FLOOR_TILT = 0.5
    private val ELLIPSIS_ONLY = Regex("^[\\s.…·。]+$")
    private val WS = Regex("^\\s+$")

    fun seededRandom(seed: Double, offset: Double): Double {
        val x = sin(seed * 1000 + offset) * 10000
        return x - floor(x)
    }

    fun lineCount(charCount: Int, seed: Double, splitProbability: Double): Int {
        val normalized = ln(charCount + LOG_OFFSET) / ln(CHAR_REF_LENGTH + LOG_OFFSET)
        val score = normalized * (seededRandom(seed, 1.0) * 0.6 + 0.7) * splitProbability
        return when {
            score < LINE_THRESHOLDS[0] -> 1
            score < LINE_THRESHOLDS[1] -> 2
            score < LINE_THRESHOLDS[2] -> 3
            else -> 4
        }
    }

    private fun toSegments(texts: List<String>, tiltIndex: Int): MutableList<TiltSegment> {
        var acc = 0
        return texts.mapIndexed { i, t ->
            val lead = t.length - t.trimStart().length
            val s = TiltSegment(t.trim(), i == tiltIndex, false, acc + lead)
            acc += t.length
            s
        }.toMutableList()
    }

    private fun markShortLast(segs: MutableList<TiltSegment>): List<TiltSegment> {
        if (segs.size < 2) return segs
        val last = segs.last()
        if (last.isTilt || last.text.trim().length > 2) return segs
        if (last.text.trim().length * 2 <= segs[segs.size - 2].text.length) segs[segs.size - 1] = last.copy(isShortLastLine = true)
        return segs
    }

    /**
     * 对应 buildTiltLayout。[measureNormal]/[measureTilt] 返回给定字号（px）下单行宽度；[viewportWidth] 为 window.innerWidth。
     */
    fun buildLayout(
        fullText: String,
        lineSeed: Double,
        tuning: TiltTuning,
        fontScale: Double,
        viewportWidth: Double,
        measureNormal: (String, Double) -> Double,
        measureTilt: (String, Double) -> Double,
    ): TiltLayout {
        val basePx = max(viewportWidth * 0.06875, 5.625 * REM_PX * fontScale)
        val available = max(320.0, viewportWidth) * 0.85
        val charCount = fullText.trim().length
        val merged = if (ELLIPSIS_ONLY.matches(fullText.trim())) listOf(fullText)
        else SentenceLayout.splitIntoSentences(fullText, lineCount(charCount, lineSeed, tuning.splitProbability), lineSeed)

        val candidates = merged.indices.filter { seededRandom(lineSeed, 100.0 + it) < tuning.tiltStyleProbability }
        val tiltIndex = if (candidates.isNotEmpty()) candidates[floor(seededRandom(lineSeed, 200.0) * candidates.size).toInt()] else -1
        val segments = toSegments(merged, tiltIndex)

        val tiltWidth = segments.filter { it.isTilt }.maxOfOrNull { measureTilt(it.text, basePx) } ?: 0.0
        val normalWidth = segments.filter { !it.isTilt }.maxOfOrNull { measureNormal(it.text, basePx) } ?: 0.0
        val tiltOverflow = if (tiltWidth > 0) tiltWidth / available else 0.0
        val normalOverflow = if (normalWidth > 0) normalWidth / available else 0.0
        val maxOverflow = max(tiltOverflow, normalOverflow)
        var scale = 1.0
        if (maxOverflow > 1) {
            if (maxOverflow >= RESPLIT_THRESHOLD) {
                val targetWidth = available * (RESPLIT_THRESHOLD - 0.15)
                val total = measureNormal(fullText, basePx)
                val needed = min(4, max(segments.size + 1, ceil(total / targetWidth).toInt()))
                if (needed > segments.size) {
                    val re = toSegments(SentenceLayout.splitIntoSentences(fullText, needed, lineSeed), -1)
                    if (re.isNotEmpty() && seededRandom(lineSeed, 300.0) < tuning.tiltStyleProbability) {
                        val picked = floor(seededRandom(lineSeed, 301.0) * re.size).toInt()
                        re[picked] = re[picked].copy(isTilt = true)
                    }
                    val postTilt = re.firstOrNull { it.isTilt }
                    val postNormal = re.filter { !it.isTilt }.maxOfOrNull { measureNormal(it.text, basePx) }
                    var post = 1.0
                    if (postTilt != null) {
                        val w = measureTilt(postTilt.text, basePx)
                        if (w > available) post = max(SCALE_FLOOR_TILT, available / w)
                    } else if (postNormal != null && postNormal > available) {
                        post = max(SCALE_FLOOR_NORMAL, available / postNormal)
                    }
                    return TiltLayout(markShortLast(re), post)
                }
            }
            scale = if (tiltOverflow >= normalOverflow && tiltWidth > 0) max(SCALE_FLOOR_TILT, available / tiltWidth)
            else if (normalWidth > 0) max(SCALE_FLOOR_NORMAL, available / normalWidth) else 1.0
        }
        return TiltLayout(markShortLast(segments), scale)
    }

    /** 对应 findSegmentWordRange。 */
    fun segmentWordRange(charOffset: Int, segmentText: String, fullText: String, words: List<Word>): IntRange {
        if (charOffset < 0 || charOffset >= fullText.length) return 0 until words.size
        val segEnd = charOffset + segmentText.length
        var pos = 0
        var start = 0
        var end = words.size
        for (wi in words.indices) {
            val wt = words[wi].text
            val ws = fullText.indexOf(wt, pos)
            if (ws == -1) { pos += wt.length; continue }
            val we = ws + wt.length
            if (ws <= charOffset && we > charOffset) start = wi
            if (ws < segEnd && we >= segEnd) { end = wi + 1; break }
            pos = we
        }
        return start until end
    }

    /** 每段可见起止（对应 segmentTimings）。 */
    fun segmentTimings(line: Line, layout: TiltLayout): List<Pair<Double, Double>> = layout.segments.map { seg ->
        val r = segmentWordRange(seg.charOffset, seg.text, line.fullText, line.words)
        val ws = line.words.subList(r.first.coerceIn(0, line.words.size), (r.last + 1).coerceIn(0, line.words.size))
        if (ws.isNotEmpty()) ws.first().startTime to ws.last().endTime
        else line.startTime to RenderHints.getLineRenderEndTime(line)
    }

    /** 可见段下标（对应 useLayoutEffect handler）。 */
    fun visibleSegmentIndex(time: Double, line: Line, timings: List<Pair<Double, Double>>): Int {
        var target = -1
        timings.forEachIndexed { i, t -> if (time >= t.first - 0.25) target = i }
        if (time < line.startTime - 0.1 || time > RenderHints.getLineRenderEndTime(line)) target = -1
        return target
    }

    /** 对应 buildCharTimings（按段内非空白字素）。 */
    fun charTimings(seg: TiltSegment, start: Double, end: Double, line: Line): List<TiltCharTiming> {
        val gs = GraphemeTimings.splitLyricGraphemes(seg.text)
        val nonSpace = gs.count { !WS.matches(it) }
        if (gs.isEmpty() || nonSpace == 0) return emptyList()
        val total = max(end - start, 0.3)
        val r = segmentWordRange(seg.charOffset, seg.text, line.fullText, line.words)
        val words = line.words.subList(r.first.coerceIn(0, line.words.size), (r.last + 1).coerceIn(0, line.words.size))
        val out = ArrayList<TiltCharTiming>()
        var ci = 0
        for (w in words) {
            if (w.syllables.isNotEmpty()) {
                for (t in GraphemeTimings.buildWordGraphemeTimings(w).filter { !WS.matches(it.char) }) {
                    if (ci >= nonSpace) break
                    out.add(TiltCharTiming(ci, t.startTime, t.endTime)); ci++
                }
                continue
            }
            val wg = GraphemeTimings.splitLyricGraphemes(w.text)
            val n = wg.count { !WS.matches(it) }
            if (n == 0) continue
            val cd = max(w.endTime - w.startTime, 0.05) / n
            var k = 0
            for (g in wg) {
                if (ci >= nonSpace) break
                if (WS.matches(g)) continue
                out.add(TiltCharTiming(ci, w.startTime + k * cd, w.startTime + (k + 1) * cd)); ci++; k++
            }
        }
        if (out.isEmpty() || out.size != nonSpace) {
            out.clear()
            val avg = total / nonSpace
            for (i in 0 until nonSpace) out.add(TiltCharTiming(i, start + i * avg, start + (i + 1) * avg))
        }
        return out
    }

    /** 对应 getCharPulseIntensity：sin 半周脉动，之后余辉 0.25。 */
    fun pulse(time: Double, t: TiltCharTiming): Double {
        val duration = min(max(max(t.endTime - t.startTime, 0.05), 0.2), 0.9)
        val elapsed = time - t.startTime
        if (elapsed < 0) return 0.0
        if (elapsed <= duration) return sin(elapsed / duration * PI)
        val after = elapsed - duration
        val ramp = duration * 1.2
        return if (after >= ramp) 0.25 else 0.25 * (after / ramp)
    }
}
