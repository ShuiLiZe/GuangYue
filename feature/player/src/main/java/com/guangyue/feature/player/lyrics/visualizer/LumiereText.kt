// 绘光（Folia lumiere）文字层纯逻辑：歌词窗口的排版、换位、崩解与每帧的明暗，逐函数对译，不依赖 Android，可单测。
// 对应 Folia 文件：src/components/visualizer/lumiere/text/
//   windowTypes.ts、windowTiming.ts（游标、相位、飞行曲线、聚拢、崩解、光斑）、wordStyle.ts（逐词大小与错落）、
//   glyphLine.ts（逐字切片与竖排直立判断）、lineWrap.ts（画框内的文字带、横 / 竖 × 单行 / 折行四种排法）、
//   windowSlots.ts（固定槽位与纵横交错的自由槽位）、windowGeometry.ts（逐行变换、逐字局部位置）、
//   lineClearance.ts + windowProtection.ts（邻行避让、当前行保护）、windowLines.ts（逐行逐字的随机量，19 + 4 次抽取）、
//   reveal.ts（逐字时间与闪光包络）、lyricWindow.ts 的 update（每字的透明度、颜色、光晕、闪点、光斑）。
// 单位同 Folia：舞台逻辑像素（这里是 dp），区域与漂移以画面高度为 1。
//
// 偏离：
//   1. lineWrap.ts 的折行用的是 @chenglou/pretext 的富文本行走（walkRichInlineLineRanges）。这里按同样的输入
//      （不可拆的词块 + 可折的空白，空白在行首折叠）做贪心折行；词块宽度直接用逐字 advance 之和，所以 occupiedWidth = extent。
//   2. 字宽测量交给调用方（LumiereTextMeasure：舞台用 android.graphics.Paint，单测用桩）。letterSpacing 按每个字素加一份。
//   3. 关键字着色（keywordColors.ts）：GuangYue 的主题没有 wordColors，这里不着色（接口留着，tints 恒为 null）。
//   4. 径迹（drawTracks）只在纵横交错换位飞行的 0.45 秒里出现，第 2 阶段再画；字的飞行本身照常。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** 自然宽度（不含字距），单位与 px 相同。 */
internal fun interface LumiereTextMeasure {
    fun width(text: String, px: Double): Double
}

internal class LumierePoint(val x: Double, val y: Double)

// ───────────────────────────── windowTiming.ts / reveal.ts ─────────────────────────────

internal class LumiereGlyphTiming(val start: Double, val end: Double)

internal class LumiereFlight(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double, val delay: Double, val duration: Double, val spin: Double, val wobble: Double)

internal fun lumiereFlightProgress(flight: LumiereFlight, phase: Double, lag: Double = 0.0): Double {
    val t = min(1.0, max(0.0, (phase - flight.delay - lag) / flight.duration))
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

internal fun lumiereFlightPoint(flight: LumiereFlight, from: LumierePoint, to: LumierePoint, s: Double): LumierePoint {
    val u = 1 - s
    val a = u * u * u; val b = 3 * u * u * s; val c = 3 * u * s * s; val d = s * s * s
    return LumierePoint(
        a * from.x + b * (from.x + flight.c1x) + c * (to.x + flight.c2x) + d * to.x,
        a * from.y + b * (from.y + flight.c1y) + c * (to.y + flight.c2y) + d * to.y,
    )
}

internal const val LUMIERE_TRACK_TIME = 0.45
internal const val LUMIERE_LEAD = 1.2
internal const val LUMIERE_SLIDE = 1.5
internal const val LUMIERE_MAX_LAG = 0.6
internal val LUMIERE_DRIFT_SPEED = 0.013 to 0.022
internal const val LUMIERE_ORBIT = 0.03
internal const val LUMIERE_LIGHT_UP = 0.3
internal const val LUMIERE_GATHER_LEAD = 1.6
internal const val LUMIERE_GATHER = 1.8
internal const val LUMIERE_DISSOLVE_FROM = 1.0
internal const val LUMIERE_DISSOLVE_SPAN = 2.0
private const val LUMIERE_WINDOW_REACH = 3

private fun lumiereSlideLag(relative: Int) = when (max(-2, min(2, relative))) { -2 -> 0.0; -1 -> 0.15; 0 -> 0.3; 1 -> 0.5; else -> 0.6 }

internal class LumiereCursor(val current: Int, val phase: Double, val slide: Double)

internal fun lumiereWindowCursor(lines: List<Line>, time: Double): LumiereCursor {
    var current = -1
    for (i in lines.indices) { if (lines[i].startTime - LUMIERE_LEAD <= time) current = i else break }
    val phase = if (current < 0) 1.0 else lumiereClamp01((time - (lines[current].startTime - LUMIERE_LEAD)) / LUMIERE_SLIDE)
    return LumiereCursor(current, phase, lumiereEaseInOutSine(phase))
}

internal fun lumiereLinePhase(lines: List<Line>, current: Int, relative: Int, time: Double): Pair<Double, Double> {
    if (current < 0) return 1.0 to Double.NEGATIVE_INFINITY
    val start = lines[current].startTime - LUMIERE_LEAD + lumiereSlideLag(relative)
    return lumiereClamp01((time - start) / LUMIERE_SLIDE) to start
}

internal fun lumiereGlyphProgress(t: LumiereGlyphTiming, time: Double) = lumiereClamp01((time - t.start) / max(t.end - t.start, 0.08))

internal fun lumiereFlashEnvelope(t: LumiereGlyphTiming, time: Double, decay: Double = 0.5, attack: Double = 0.15): Double {
    val age = time - t.start
    if (age <= 0) return 0.0
    if (age < attack) { val k = age / attack; return k * k * (3 - 2 * k) }
    return kotlin.math.exp(-(age - attack) / decay)
}

/** resolveSpotX：光斑位置 = 已唱进度在字中心之间插值，取最近 0.3 秒的 8 个采样平均。 */
internal fun lumiereSpotX(timings: List<LumiereGlyphTiming>, centers: List<Double>, time: Double, window: Double = 0.3): Double {
    fun position(t: Double) = timings.sumOf { lumiereGlyphProgress(it, t) }
    fun toX(position: Double): Double {
        val n = centers.size
        if (n == 0) return 0.0
        val index = min((n - 1).toDouble(), max(0.0, position - 0.5))
        val lower = floor(index).toInt()
        val upper = min(n - 1, lower + 1)
        return centers[lower] + (centers[upper] - centers[lower]) * (index - lower)
    }
    if (window <= 0) return toX(position(time))
    var x = 0.0
    for (s in 0 until 8) x += toX(position(time - (window * s) / 7))
    return x / 8
}

internal fun lumiereDecayAmount(decay: LumiereDecaySpec, litAt: Double, time: Double): Double {
    val age = time - litAt - decay.delay
    return if (age > 0) decay.strength * 0.08 * age.pow(1.5) else 0.0
}

// ───────────────────────────── wordStyle.ts ─────────────────────────────

internal class LumiereWordSpan(val text: String, val start: Int, val end: Int, val blank: Boolean)

internal const val LUMIERE_MAX_WORD_SCALE = 1.5

internal fun lumiereSegmentWords(line: Line, split: LumiereWordSplitter): List<LumiereWordSpan> {
    val words = ArrayList<LumiereWordSpan>()
    var cursor = 0
    for (segment in split(line)) {
        val length = GraphemeTimings.splitLyricGraphemes(segment).size
        if (length == 0) continue
        words += LumiereWordSpan(segment, cursor, cursor + length, !lumiereIsWordLike(segment))
        cursor += length
    }
    return words
}

private val LUMIERE_FUNCTION_WORDS = "的了在把是我你他她它们和与也就都着过吗呢吧啊呀哦被让给从向到这那之而又很".map { it.toString() }.toSet()
private val LUMIERE_MINOR_LATIN = setOf("a", "an", "the", "of", "to", "in", "on", "at", "me", "my", "with", "and", "or", "is", "i", "you", "it", "by", "for")

/** wordScales：最长的实词 1.45，虚词 0.62，其余 0.85–1.2（按种子）。 */
internal fun lumiereWordScales(words: List<LumiereWordSpan>, seed: String): List<Double> {
    val rng = lumiereRng("$seed:words")
    fun len(w: LumiereWordSpan) = w.end - w.start
    fun minor(w: LumiereWordSpan) = w.blank || (len(w) == 1 && w.text in LUMIERE_FUNCTION_WORDS) || w.text.lowercase() in LUMIERE_MINOR_LATIN
    var key = -1
    words.forEachIndexed { i, w -> if (!minor(w) && (key < 0 || len(w) >= len(words[key]))) key = i }
    return words.mapIndexed { i, w ->
        val jitter = rng.next()
        when {
            w.blank && w.text.isBlank() -> 1.0
            i == key && words.size > 1 -> 1.45
            minor(w) -> 0.62
            else -> 0.85 + jitter * 0.35
        }
    }
}

internal fun lumiereWordJags(words: List<LumiereWordSpan>, scales: List<Double>, seed: String): List<Double> {
    val rng = lumiereRng("$seed:jags")
    val keyScale = scales.maxOrNull() ?: 1.0
    return words.indices.map { i ->
        val offset = (rng.next() - 0.5) * 2
        val scale = scales.getOrElse(i) { 1.0 }
        if (scale == keyScale && words.size > 1) 0.0 else offset * (if (scale < 0.7) 0.32 else 0.2)
    }
}

// ───────────────────────────── glyphLine.ts ─────────────────────────────

private val LUMIERE_UPRIGHT = Regex("[\\u1100-\\u11ff\\u2e80-\\u2fff\\u3000-\\u303f\\u3040-\\u30ff\\u3100-\\u31ff\\u3200-\\u9fff\\uac00-\\ud7af\\uf900-\\ufaff\\ufe30-\\ufe4f\\uff00-\\uffef]|[\\x{20000}-\\x{3134f}]")
internal fun lumiereIsUpright(char: String) = LUMIERE_UPRIGHT.containsMatchIn(char)

internal class LumiereGlyphSlice(val char: String, val charWidth: Double, val blank: Boolean, val upright: Boolean)

/** buildGlyphLine 里与绘制无关的部分：逐字前缀量宽（含字距），得到每个字的 advance。 */
internal fun lumiereGlyphSlices(text: String, fontPx: Double, measure: LumiereTextMeasure, letterSpacing: Double): List<LumiereGlyphSlice> {
    val graphemes = GraphemeTimings.splitLyricGraphemes(text)
    val spacing = letterSpacing * fontPx
    val offsets = DoubleArray(graphemes.size + 1)
    val prefix = StringBuilder()
    graphemes.forEachIndexed { i, ch -> prefix.append(ch); offsets[i + 1] = measure.width(prefix.toString(), fontPx) + spacing * (i + 1) }
    return graphemes.mapIndexed { i, ch -> LumiereGlyphSlice(ch, max(0.5, offsets[i + 1] - offsets[i]), ch.isBlank(), lumiereIsUpright(ch)) }
}

// ───────────────────────────── lineWrap.ts ─────────────────────────────

internal class LumiereFrameBand(val left: Double, val right: Double, val top: Double, val bottom: Double)

internal fun lumiereFrameInsets(width: Double, height: Double) = max(30.0, width * 0.065) to max(30.0, height * 0.085)

internal fun lumiereFrameBand(width: Double, height: Double): LumiereFrameBand {
    val (padX, padY) = lumiereFrameInsets(width, height)
    val left = padX / height + 0.04
    return LumiereFrameBand(left, width / height - left, padY / height + 0.035, max(padY / height + 0.035 + 0.2, 1 - 0.2))
}

internal const val LUMIERE_SINGLE_MIN_FIT = 0.8
internal const val LUMIERE_MAX_LINES = 2
internal const val LUMIERE_ROW_PITCH = 1.6
internal const val LUMIERE_COLUMN_PITCH = 1.6

internal class LumiereMeasurer(private val measure: LumiereTextMeasure, private val letterSpacing: Double) {
    private val cache = HashMap<String, Double>()
    fun spacing(px: Double) = letterSpacing * px
    fun natural(text: String, px: Double): Double = cache.getOrPut("$px|$text") {
        measure.width(text, px) + spacing(px) * GraphemeTimings.splitLyricGraphemes(text).size
    }
}

internal class LumiereFlowGlyph(val char: String, val scale: Double, val advance: Double, val upright: Boolean, val jag: Double)

internal class LumiereVariant(
    val points: List<LumierePoint>, val spots: List<LumierePoint>, val along: Double, val across: Double,
    val lines: Int, val inkWidth: Double, val inkHeight: Double,
)

/** [orient][wrap]：orient 0 横 / 1 竖，wrap 0 单行 / 1 至多两行。 */
internal typealias LumiereFlow = List<List<LumiereVariant>>

private val LUMIERE_OPENING = Regex("^[(\\[{（【《「『〈〔［“‘]+$")
private val LUMIERE_LETTER_OR_NUMBER = Regex("[\\p{L}\\p{N}]")

private class LumiereSpan(val start: Int, val end: Int, val kind: String)
private class LumiereUnit(val start: Int, var end: Int, val text: String, val px: Double)
private class LumierePiece(val unit: LumiereUnit?, val span: LumiereSpan?)

private fun lumiereSpansOf(glyphs: List<LumiereFlowGlyph>, words: List<LumiereWordSpan>): List<LumiereSpan> {
    fun isSpace(i: Int) = glyphs[i].char.isBlank()
    val ranges = ArrayList<Triple<Int, Int, Boolean>>()
    var cursor = 0
    for (word in words) {
        val start = max(cursor, min(glyphs.size, word.start))
        val end = min(glyphs.size, word.end)
        for (i in cursor until start) ranges += Triple(i, i + 1, false)
        if (end > start) ranges += Triple(start, end, !word.blank)
        cursor = max(cursor, end)
    }
    for (i in cursor until glyphs.size) ranges += Triple(i, i + 1, false)
    val spans = ArrayList<LumiereSpan>()
    for ((s0, e0, word) in ranges) {
        var start = s0; var end = e0
        val lead = ArrayList<LumiereSpan>(); val tail = ArrayList<LumiereSpan>()
        while (start < end && isSpace(start)) { lead += LumiereSpan(start, start + 1, "space"); start++ }
        while (end > start && isSpace(end - 1)) { tail.add(0, LumiereSpan(end - 1, end, "space")); end-- }
        spans += lead
        if (end > start) spans += LumiereSpan(start, end, if (word) "word" else "punct")
        spans += tail
    }
    return spans
}

private fun lumiereTextOf(glyphs: List<LumiereFlowGlyph>, start: Int, end: Int) = glyphs.subList(start, end).joinToString("") { it.char }

private fun lumiereUnitsOf(glyphs: List<LumiereFlowGlyph>, spans: List<LumiereSpan>, heroPx: Double): List<LumierePiece> {
    val pieces = ArrayList<LumierePiece>()
    fun pxOf(i: Int) = heroPx * glyphs[i].scale
    var pendingOpen: LumiereSpan? = null
    spans.forEachIndexed { index, span ->
        if (span.kind == "space") {
            pendingOpen?.let { pieces += LumierePiece(LumiereUnit(it.start, it.end, lumiereTextOf(glyphs, it.start, it.end), pxOf(it.start)), null) }
            pendingOpen = null
            pieces += LumierePiece(null, span)
            return@forEachIndexed
        }
        val previous = pieces.lastOrNull()
        val next = spans.getOrNull(index + 1)
        if (span.kind == "punct") {
            val text = lumiereTextOf(glyphs, span.start, span.end)
            if (LUMIERE_OPENING.matches(text) && next != null && next.kind != "space") {
                pendingOpen = pendingOpen?.let { LumiereSpan(it.start, span.end, it.kind) } ?: span
                return@forEachIndexed
            }
            if (pendingOpen == null && previous?.unit != null && previous.unit.end == span.start) {
                previous.unit.end = span.end
                return@forEachIndexed
            }
        }
        val start = pendingOpen?.start ?: span.start
        pendingOpen = null
        pieces += LumierePiece(LumiereUnit(start, span.end, lumiereTextOf(glyphs, span.start, span.end), pxOf(span.start)), null)
    }
    pendingOpen?.let { pieces += LumierePiece(LumiereUnit(it.start, it.end, lumiereTextOf(glyphs, it.start, it.end), pxOf(it.start)), null) }
    return pieces
}

private class LumiereFragment(val item: Int, val gapBefore: Double, val width: Double)
private class LumiereRow(val fragments: List<LumiereFragment>, val width: Double)

/** pretext 富文本行走的等价：词块不可拆，空白可折（行首折叠）；maxWidth 内贪心放。 */
private fun lumiereWalk(widths: List<Double>, isSpace: List<Boolean>, maxWidth: Double): List<LumiereRow> {
    val rows = ArrayList<LumiereRow>()
    var frags = ArrayList<LumiereFragment>()
    var width = 0.0
    var gap = 0.0
    for (i in widths.indices) {
        if (isSpace[i]) { if (frags.isNotEmpty()) gap += widths[i]; continue }
        if (frags.isNotEmpty() && width + gap + widths[i] > maxWidth + 1e-6) {
            rows += LumiereRow(frags, width)
            frags = ArrayList(); width = 0.0; gap = 0.0
        }
        val g = if (frags.isEmpty()) 0.0 else gap
        frags += LumiereFragment(i, g, widths[i])
        width += g + widths[i]
        gap = 0.0
    }
    if (frags.isNotEmpty() || rows.isEmpty()) rows += LumiereRow(frags, width)
    return rows
}

private class LumiereBreak(val row: IntArray, val offset: DoubleArray, val lengths: List<Double>)

private fun lumiereBreakLines(measurer: LumiereMeasurer, glyphs: List<LumiereFlowGlyph>, pieces: List<LumierePiece>, advances: DoubleArray, heroPx: Double, lines: Int): LumiereBreak {
    fun extentOf(u: LumiereUnit): Double { var s = 0.0; for (i in u.start until u.end) s += advances[i]; return s }
    val widths = ArrayList<Double>(); val isSpace = ArrayList<Boolean>(); val unitOf = ArrayList<LumiereUnit?>()
    var widest = 0.0; var widestGap = 0.0
    for (piece in pieces) {
        val span = piece.span
        if (span != null) {
            val px = heroPx * glyphs[span.start].scale
            widths += measurer.natural(lumiereTextOf(glyphs, span.start, span.end), px); isSpace += true; unitOf += null
            widestGap = max(widestGap, measurer.natural("a a", px) - measurer.natural("aa", px))
            continue
        }
        val unit = piece.unit!!
        val extent = extentOf(unit)
        widths += extent; isSpace += false; unitOf += unit
        widest = max(widest, extent)
    }
    var rows = lumiereWalk(widths, isSpace, Double.POSITIVE_INFINITY)
    if (lines > 1 && rows.size == 1 && rows[0].fragments.size > 1) {
        val total = rows[0].width
        var target = (total + widest) / 2 + widestGap
        if (lumiereWalk(widths, isSpace, target).size > lines) target = total / 2 + widest + widestGap
        rows = lumiereWalk(widths, isSpace, target)
        if (rows.size > lines) {
            val kept = rows.subList(0, lines - 1)
            val rest = rows.subList(lines - 1, rows.size)
            val fragments = rest.flatMapIndexed { idx, row -> row.fragments.mapIndexed { k, f -> if (idx > 0 && k == 0) LumiereFragment(f.item, widestGap, f.width) else f } }
            rows = kept + LumiereRow(fragments, fragments.sumOf { it.gapBefore + it.width })
        }
    }
    val row = IntArray(glyphs.size) { -1 }
    val offset = DoubleArray(glyphs.size)
    rows.forEachIndexed { r, line ->
        var cursor = 0.0
        for (fragment in line.fragments) {
            val unit = unitOf[fragment.item]
            val gapStart = cursor
            cursor += fragment.gapBefore
            if (unit == null) continue
            var i = unit.start - 1
            while (i >= 0 && row[i] == -1 && glyphs[i].char.isBlank()) { row[i] = r; offset[i] = gapStart + fragment.gapBefore / 2; i-- }
            val extent = extentOf(unit)
            val ratio = if (extent > 0) fragment.width / extent else 0.0
            var inner = cursor
            for (k in unit.start until unit.end) { val adv = advances[k] * ratio; row[k] = r; offset[k] = inner + adv / 2; inner += adv }
            cursor += fragment.width
        }
    }
    for (i in glyphs.indices) {
        if (row[i] != -1) continue
        if (i > 0 && row[i - 1] != -1) { row[i] = row[i - 1]; offset[i] = offset[i - 1] + advances[i - 1] / 2 }
    }
    for (i in glyphs.indices.reversed()) {
        if (row[i] != -1) continue
        row[i] = if (i + 1 < glyphs.size && row[i + 1] != -1) row[i + 1] else 0
        offset[i] = if (i + 1 < glyphs.size) max(0.0, offset[i + 1] - advances[i + 1] / 2) else 0.0
    }
    val lengths = rows.map { it.width }
    return LumiereBreak(row, offset, if (lengths.isEmpty()) listOf(0.0) else lengths)
}

private fun lumiereSplitLongUnits(glyphs: List<LumiereFlowGlyph>, pieces: List<LumierePiece>, advances: DoubleArray, limit: Double, heroPx: Double): List<LumierePiece> = pieces.flatMap { piece ->
    val unit = piece.unit ?: return@flatMap listOf(piece)
    var extent = 0.0
    for (i in unit.start until unit.end) extent += advances[i]
    if (extent <= limit || unit.end - unit.start < 2) return@flatMap listOf(LumierePiece(LumiereUnit(unit.start, unit.end, unit.text, unit.px), null))
    val out = ArrayList<LumierePiece>()
    for (i in unit.start until unit.end) {
        val ch = glyphs[i].char
        val last = out.lastOrNull()?.unit
        if (last != null && !LUMIERE_LETTER_OR_NUMBER.containsMatchIn(ch)) { last.end = i + 1; continue }
        out += LumierePiece(LumiereUnit(i, i + 1, ch, heroPx * glyphs[i].scale), null)
    }
    out
}

internal fun lumiereFlowLine(measurer: LumiereMeasurer, glyphs: List<LumiereFlowGlyph>, words: List<LumiereWordSpan>, heroPx: Double, limitH: Double, limitV: Double): LumiereFlow {
    val spans = lumiereSpansOf(glyphs, words)
    val horizontal = DoubleArray(glyphs.size)
    for (span in spans) {
        val px = heroPx * glyphs[span.start].scale
        val width = measurer.natural(lumiereTextOf(glyphs, span.start, span.end), px)
        var canvas = 0.0
        for (i in span.start until span.end) canvas += glyphs[i].advance
        for (i in span.start until span.end) horizontal[i] = if (canvas > 0) (width * glyphs[i].advance) / canvas else width / (span.end - span.start)
    }
    val vertical = DoubleArray(glyphs.size) { i -> if (glyphs[i].upright) heroPx * glyphs[i].scale * (1 + measurer.spacing(1.0)) else horizontal[i] }
    fun variant(orient: Int, lines: Int): LumiereVariant {
        // unitsOf 会改写词块的 end（标点黏前），每个排法各取一份。
        val pieces = lumiereSplitLongUnits(glyphs, lumiereUnitsOf(glyphs, spans, heroPx), if (orient == 0) horizontal else vertical, if (orient == 0) limitH else limitV, heroPx)
        val advances = if (orient == 0) horizontal else vertical
        val br = lumiereBreakLines(measurer, glyphs, pieces, advances, heroPx, lines)
        val count = br.lengths.size
        val along = br.lengths.max()
        val pitch = (if (orient == 0) LUMIERE_ROW_PITCH else LUMIERE_COLUMN_PITCH) * heroPx
        val points = ArrayList<LumierePoint>(); val spots = ArrayList<LumierePoint>()
        var inkX = 0.0; var inkY = 0.0
        glyphs.forEachIndexed { i, glyph ->
            val r = br.row[i]
            val size = heroPx * glyph.scale
            if (orient == 0) {
                val x = br.offset[i] - br.lengths[r] / 2
                val rowY = (r - (count - 1) / 2.0) * pitch
                val p = LumierePoint(x, rowY + (1 - glyph.scale) * heroPx * 0.32 + glyph.jag)
                points += p; spots += LumierePoint(x, rowY)
                if (glyph.char.isNotBlank()) { inkX = max(inkX, abs(p.x) + advances[i] / 2); inkY = max(inkY, abs(p.y) + size / 2) }
            } else {
                val columnX = ((count - 1) / 2.0 - r) * pitch
                val y = br.offset[i] - br.lengths[r] / 2
                val p = LumierePoint(columnX + glyph.jag * 0.8, y)
                points += p; spots += LumierePoint(columnX, y)
                if (glyph.char.isNotBlank()) { inkX = max(inkX, abs(p.x) + size / 2); inkY = max(inkY, abs(p.y) + advances[i] / 2) }
            }
        }
        return LumiereVariant(points, spots, along, (count - 1) * pitch + heroPx, count, inkX * 2, inkY * 2)
    }
    return listOf(listOf(variant(0, 1), variant(0, LUMIERE_MAX_LINES)), listOf(variant(1, 1), variant(1, LUMIERE_MAX_LINES)))
}

internal fun lumiereFitScale(along: Double, budget: Double, scale: Double = 1.0) = min(1.0, budget / max(scale * along, 1e-6))
internal fun lumiereShouldWrap(flow: List<LumiereVariant>, budget: Double, scale: Double) = flow[1].lines > 1 && scale * flow[0].along * LUMIERE_SINGLE_MIN_FIT > budget
internal fun lumiereClampInto(center: Double, extent: Double, lo: Double, hi: Double): Double {
    if (extent >= hi - lo) return (lo + hi) / 2
    return min(hi - extent / 2, max(lo + extent / 2, center))
}

// ───────────────────────────── windowSlots.ts ─────────────────────────────

internal data class LumiereSlot(val dx: Double, val dy: Double, val scale: Double, val alpha: Double, val orient: Double, val rotation: Double, val wrap: Double)

internal fun lumiereFreePlacements(random: LumiereRng, region: LumiereRegion, nextAlpha: Double): Map<Int, LumiereSlot> {
    fun placement(upper: Boolean, alpha: Double): LumiereSlot {
        val angle = if (upper) PI * (1.12 + random.next() * 0.76) else PI * (0.12 + random.next() * 0.76)
        val rx = region.w * (0.24 + random.next() * 0.22)
        val ry = region.h * (0.3 + random.next() * 0.18)
        val vertical = random.next() < 0.5
        val scale = 0.5 + random.next() * 0.2
        val rotation = (random.next() - 0.5) * (if (vertical) 0.3 else 0.7)
        return LumiereSlot(cos(angle) * rx, sin(angle) * ry, scale, alpha, if (vertical) 1.0 else 0.0, rotation, 0.0)
    }
    fun farther(s: LumiereSlot) = s.copy(dx = s.dx * 1.45, dy = s.dy * 1.45, scale = s.scale * 0.85, alpha = 0.0)
    val previous = placement(true, 0.9)
    val next = placement(false, nextAlpha)
    return mapOf(-1 to previous, 1 to next, -2 to farther(previous), 2 to farther(next))
}

internal fun lumiereFixedSlot(typography: String, relative: Int, region: LumiereRegion, fontH: Double, nextAlpha: Double, across: (Int, Double) -> Double): LumiereSlot {
    val vertical = typography == "vertical"
    val orient = if (vertical) 1.0 else 0.0
    if (relative == 0) return LumiereSlot(0.0, 0.0, 1.0, 1.0, orient, 0.0, 0.0)
    val previous = relative < 0
    val far = abs(relative) > 1
    val nearScale = if (vertical) 0.58 else 0.52
    val scale = if (far) (if (vertical) 0.48 else 0.44) else nearScale
    val gPrev = if (vertical) 0.95 else 0.93
    val gNext = if (vertical) 0.88 else 0.735
    val gFarPrev = if (vertical) 0.78 else 0.56
    val gFarNext = if (vertical) 0.7 else 0.56
    val sign = if (previous) -1 else 1
    var offset = across(0, 1.0) / 2 + (if (previous) gPrev else gNext) * fontH
    if (far) offset += across(sign, nearScale) + (if (previous) gFarPrev else gFarNext) * fontH
    offset += across(relative, scale) / 2
    val alpha = if (previous) (if (far) 0.0 else 0.9) else (if (far) 0.0 else nextAlpha)
    return if (vertical) LumiereSlot(-sign * offset, sign * (if (far) 0.07 else 0.03), scale, alpha, orient, 0.0, 0.0)
    else LumiereSlot(sign * region.w * (if (far) 0.24 else 0.16), sign * offset, scale, alpha, orient, 0.0, 0.0)
}

internal const val LUMIERE_CROSSED_CLEARANCE = 0.5
internal fun lumiereStackX(kind: String) = if (kind == "vertical") 1.0 else 0.0
internal fun lumiereAwayOf(index: Int, c: Int, kind: String) = (if (index == c) 0.0 else if (index < c) -1.0 else 1.0) * (if (kind == "vertical") -1 else 1)

// ───────────────────────────── lineClearance.ts ─────────────────────────────

private const val LUMIERE_AWAY_SOFTNESS = 0.005
internal const val LUMIERE_HOLD = 0.018
private fun lumiereSoftAbs(a: Double) = sqrt(a * a + LUMIERE_AWAY_SOFTNESS * LUMIERE_AWAY_SOFTNESS) - LUMIERE_AWAY_SOFTNESS

internal fun lumiereAwayDrift(drift: Double, away: Double): Double {
    if (away == 0.0) return drift
    val s = if (away > 0) 1.0 else -1.0
    val along = drift * s
    return drift + abs(away) * (lumiereSoftAbs(along) - along) * s
}

internal fun lumiereHeldDrift(vx: Double, vy: Double, age: Double, axis: Int): Double {
    val speed = hypot(vx, vy)
    if (speed < 1e-9) return 0.0
    val phi = (speed * age) / LUMIERE_HOLD
    val along = LUMIERE_HOLD * sin(phi)
    val turn = LUMIERE_HOLD * (1 - cos(phi))
    val ux = vx / speed; val uy = vy / speed
    return if (axis == 0) along * ux - turn * uy else along * uy + turn * ux
}

internal const val LUMIERE_PROTECT_FLOOR = 0.4
internal const val LUMIERE_PROTECT_MARGIN = 0.5

internal class LumiereProtectBox(var line: Int = -1, var x: Double = 0.0, var y: Double = 0.0, var cos: Double = 1.0, var sin: Double = 0.0, var halfW: Double = 0.0, var halfH: Double = 0.0, var margin: Double = 1.0, var weight: Double = 0.0)

internal fun lumiereProtectionAt(boxes: List<LumiereProtectBox>, count: Int, line: Int, x: Double, y: Double, half: Double): Double {
    var protect = 0.0
    for (i in 0 until count) {
        val box = boxes[i]
        if (box.line == line || box.weight <= 0) continue
        val dx = x - box.x; val dy = y - box.y
        val u = abs(dx * box.cos + dy * box.sin) - box.halfW - half
        val v = abs(dy * box.cos - dx * box.sin) - box.halfH - half
        val outside = if (u > 0 && v > 0) hypot(u, v) else max(max(u, v), 0.0)
        protect += box.weight * (1 - lumiereSmooth(outside / box.margin))
    }
    return min(1.0, protect)
}

internal fun lumiereProtectedAlpha(protect: Double) = 1 - (1 - LUMIERE_PROTECT_FLOOR) * protect

// ───────────────────────────── windowLines.ts ─────────────────────────────

internal class LumiereGlyph(
    val char: String, val blank: Boolean, val index: Int, val timing: LumiereGlyphTiming, val vRotation: Double,
    val flight: LumiereFlight, val scale: Double,
    val starDx: Double, val starDy: Double, val starRotation: Double, val starSize: Double,
    val driftDx: Double, val driftDy: Double, val driftSpeed: Double, val driftSpin: Double,
    val scatterX: Double, val scatterY: Double, val phase: Double,
)

internal class LumiereLineView(
    val index: Int, val line: Line, val glyphs: List<LumiereGlyph>, val flow: LumiereFlow, val placements: Map<Int, LumiereSlot>,
    val singStart: Double, val singEnd: Double, val jitter: Double, val vx: Double, val vy: Double, val motionPhase: Double,
)

internal const val LUMIERE_GLYPH_RANDOM_DRAWS = 19
internal const val LUMIERE_LINE_RANDOM_DRAWS = 4

/** buildGlyphTimings：逐字时间线（缺的字用整行时间）。 */
internal fun lumiereGlyphTimings(line: Line): List<LumiereGlyphTiming> =
    GraphemeTimings.buildLineGraphemeTimeline(line).map { t -> if (t != null) LumiereGlyphTiming(t.startTime, t.endTime) else LumiereGlyphTiming(line.startTime, line.endTime) }

// ───────────────────────────── 歌词窗口（lyricWindow.ts + windowGeometry.ts + windowProtection.ts） ─────────────────────────────

internal class LumiereLineTransform(
    val current: Int, val x: Double, val y: Double, val scale: Double, val rotation: Double, val alpha: Double,
    val toOrient: Double, val wrap: Double, val orient: Double, val phase: Double, val fly: Boolean,
    val restOrient: Double, val restWrap: Double, val moves: List<LumiereMove>, val slideStart: Double,
)

internal class LumiereMove(val toOrient: Double, val toWrap: Double, val phase: Double, val fly: Boolean)

internal class LumiereGlyphLocal(val x: Double, val y: Double, val rotation: Double, val gather: Double, val flying: Double, val scale: Double)

/** 每帧一个字的画法（世界坐标 = 舞台 dp，运镜另外套）。 */
internal class LumiereGlyphDraw(
    val char: String, val x: Double, val y: Double, val rotation: Double, val fontPx: Double, val alpha: Double, val color: Int,
    val lit: Double, val haloAlpha: Double, val haloSize: Double, val haloColor: Int,
    val starAlpha: Double, val starX: Double, val starY: Double, val starRotation: Double, val starSize: Double, val starColor: Int,
)

internal class LumiereSpotDraw(val x: Double, val y: Double, val rotation: Double, val width: Double, val height: Double, val alpha: Double, val color: Int)

internal class LumiereWindowFrame(val glyphs: List<LumiereGlyphDraw>, val spots: List<LumiereSpotDraw>)

internal class LumiereLyricWindow(
    val width: Double,
    val height: Double,
    val lines: List<Line>,
    private val measure: LumiereTextMeasure,
    val region: LumiereRegion,
    val heroPx: Double,
    neighbors: Int,
    val typography: String,
    private val typographyOf: ((Int) -> String)?,
    val decay: LumiereDecaySpec,
    val seed: String,
    private val alwaysFly: Boolean = false,
    private val driftScale: Double = 1.0,
    letterSpacing: Double = 0.04,
    private val split: LumiereWordSplitter = defaultSonnetWordSplitter,
) {
    private val lineCount = lines.size
    private val nextAlpha = if (neighbors >= 2) 0.95 else 0.0
    val band = lumiereFrameBand(width, height)
    private val columnCap = (band.bottom - band.top) * height
    private fun maxWidthOf(kind: String) = (band.right - band.left) * height * (if (kind == "crossed") 0.9 else 1.0)
    private val measurer = LumiereMeasurer(measure, letterSpacing)
    private val spacing = letterSpacing
    val fontH = heroPx / height

    private val graphemes = lines.map { GraphemeTimings.splitLyricGraphemes(it.fullText) }
    private val randomOffsets = IntArray(lineCount).also { arr ->
        var offset = 0
        lines.indices.forEach { i -> arr[i] = offset; offset += graphemes[i].size * LUMIERE_GLYPH_RANDOM_DRAWS + LUMIERE_LINE_RANDOM_DRAWS }
    }
    private val views = arrayOfNulls<LumiereLineView>(lineCount)

    fun lineOf(index: Int): LumiereLineView = views[index] ?: buildLine(index).also { views[index] = it }

    private fun buildLine(lineIndex: Int): LumiereLineView {
        val line = lines[lineIndex]
        val rng = lumiereRngAt("$seed:window", randomOffsets[lineIndex])
        val timings = lumiereGlyphTimings(line)
        val chars = graphemes[lineIndex]
        val sung = chars.indices.filter { chars[it].isNotBlank() }.map { timings.getOrNull(it) ?: LumiereGlyphTiming(line.startTime, line.endTime) }
        val singStart = if (sung.isNotEmpty()) sung.minOf { it.start } else line.startTime
        val singEnd = if (sung.isNotEmpty()) sung.maxOf { it.end } else line.endTime
        val slices = lumiereGlyphSlices(line.fullText, heroPx * LUMIERE_MAX_WORD_SCALE, measure, spacing)
        val words = lumiereSegmentWords(line, split)
        val wordSeed = "$seed:$lineIndex:${line.fullText}"
        val scales = lumiereWordScales(words, wordSeed)
        val jags = lumiereWordJags(words, scales, wordSeed)
        val wordOf = slices.indices.map { i -> max(0, words.indexOfFirst { i >= it.start && i < it.end }) }
        fun glyphScale(i: Int) = scales.getOrElse(wordOf[i]) { 1.0 }
        fun glyphJag(i: Int) = jags.getOrElse(wordOf[i]) { 0.0 } * heroPx
        val flow = lumiereFlowLine(
            measurer,
            slices.mapIndexed { i, s -> LumiereFlowGlyph(s.char, glyphScale(i), (s.charWidth / LUMIERE_MAX_WORD_SCALE) * glyphScale(i), s.upright, glyphJag(i)) },
            words, heroPx, maxWidthOf("horizontal"), columnCap,
        )
        val glyphs = slices.mapIndexed { index, slice ->
            val a1 = rng.next() * PI * 2
            val a2 = a1 + PI * (if (rng.next() < 0.5) 0.5 else 1.5) + (rng.next() - 0.5) * 0.8
            val m1 = heroPx * (1.6 + rng.next() * 3.2)
            val m2 = heroPx * (1 + rng.next() * 2.6)
            val duration = 0.45 + rng.next() * 0.4
            val delay = rng.next() * (1 - duration)
            val spin = (rng.next() - 0.5) * 2.4
            val wobble = rng.next() * PI * 2
            val flight = LumiereFlight(cos(a1) * m1, sin(a1) * m1, cos(a2) * m2, sin(a2) * m2, delay, duration, spin, wobble)
            val angle = -PI / 2 + (rng.next() - 0.5) * 2.2
            val scatterAngle = rng.next() * PI * 2
            val scatterDistance = 0.6 + rng.next() * 1.4
            val sdx = -0.15 + rng.next() * 0.5
            val sdy = -0.62 + rng.next().pow(1.4) * 0.9
            val srot = (rng.next() - 0.5) * 0.5
            val ssize = 0.65 + rng.next() * 0.7
            val speed = 0.6 + rng.next() * 0.9
            val dspin = (rng.next() - 0.5) * 2
            val phase = rng.next() * PI * 2
            LumiereGlyph(
                slice.char, slice.blank, index, timings.getOrNull(index) ?: LumiereGlyphTiming(line.startTime, line.endTime),
                if (slice.upright) 0.0 else PI / 2, flight, glyphScale(index), sdx, sdy, srot, ssize,
                cos(angle), sin(angle), speed, dspin, cos(scatterAngle) * scatterDistance, sin(scatterAngle) * scatterDistance, phase,
            )
        }
        val placements = lumiereFreePlacements(lumiereRng("$seed:placements:$lineIndex"), region, nextAlpha)
        val jitter = (rng.next() - 0.5) * 0.16
        val vAngle = rng.next() * PI * 2
        val vSpeed = LUMIERE_DRIFT_SPEED.first + rng.next() * (LUMIERE_DRIFT_SPEED.second - LUMIERE_DRIFT_SPEED.first)
        val motionPhase = rng.next() * PI * 2
        return LumiereLineView(lineIndex, line, glyphs, flow, placements, singStart, singEnd, jitter, cos(vAngle) * vSpeed, sin(vAngle) * vSpeed, motionPhase)
    }

    // ── windowGeometry.ts ──

    private fun typographyAt(c: Int): String = typographyOf?.invoke(max(0, min(lineCount - 1, c))) ?: typography

    private class Settled(val wrap: Int, val variant: LumiereVariant, val scale: Double)

    private fun settle(index: Int, kind: String, orient: Int, scale: Double): Settled {
        val flow = lineOf(index).flow[orient]
        val budget = if (orient == 1) columnCap else maxWidthOf(kind)
        val wrap = if (lumiereShouldWrap(flow, budget, scale)) 1 else 0
        val variant = flow[wrap]
        return Settled(wrap, variant, scale * lumiereFitScale(variant.along, budget, scale))
    }

    private fun acrossOf(index: Int, kind: String, orient: Int, scale: Double): Double {
        if (index < 0 || index >= lineCount) return fontH * scale
        val s = settle(index, kind, orient, scale)
        return (s.variant.across / height) * s.scale
    }

    private fun windowShift(current: Int, kind: String): LumierePoint {
        if (current < 0 || current >= lineCount) return LumierePoint(0.0, 0.0)
        val s = settle(current, kind, if (kind == "vertical") 1 else 0, 1.0)
        val w = (s.variant.inkWidth / height) * s.scale
        val h = (s.variant.inkHeight / height) * s.scale
        return LumierePoint(lumiereClampInto(region.cx, w, band.left, band.right) - region.cx, lumiereClampInto(region.cy, h, band.top, band.bottom) - region.cy)
    }

    private val slotCache = HashMap<String, LumiereSlot>()

    private fun slotOf(index: Int, current: Int, kind: String): LumiereSlot {
        val key = "$index|$current|$kind"
        slotCache[key]?.let { return it }
        val view = lineOf(index)
        val clamped = max(-2, min(2, index - current))
        val shift = windowShift(current, kind)
        val slot: LumiereSlot
        var wrap: Double? = null
        if (kind == "crossed" && clamped != 0) {
            val base = view.placements.getValue(clamped)
            val baseOrient = if (base.orient >= 0.5) 1 else 0
            val sign = if (base.dy < 0) -1.0 else 1.0
            val currentHalf = if (current in 0 until lineCount) settle(current, kind, 0, 1.0).let { (it.variant.inkHeight / height) * it.scale / 2 } else fontH / 2
            val gap = fontH * LUMIERE_CROSSED_CLEARANCE
            val centerY = region.cy + shift.y
            val room = if (sign < 0) centerY - currentHalf - gap - band.top else band.bottom - (centerY + currentHalf + gap)
            val flow = view.flow[baseOrient]
            val budget = if (baseOrient == 1) columnCap else maxWidthOf(kind)
            val tilt = abs(base.rotation) + 0.03
            fun heightOf(w: Int, s: Double) = ((flow[w].inkHeight * cos(tilt) + flow[w].inkWidth * sin(tilt)) / height) * s * lumiereFitScale(flow[w].along, budget, s)
            var scale = base.scale
            var w = if (settle(index, kind, baseOrient, scale).wrap != 0) 1 else 0
            if (baseOrient == 1 && room > 0) {
                if (w == 0 && flow[1].lines > 1 && heightOf(0, scale) > room) w = 1
                val tall = heightOf(w, scale)
                if (tall > room) scale = max(base.scale * 0.5, scale * (room / tall))
            }
            wrap = w.toDouble()
            val clearance = currentHalf + gap + heightOf(w, scale) / 2
            val dy = sign * max(abs(base.dy), clearance)
            val wider = if (baseOrient == 1) max(0.0, acrossOf(index, kind, 1, scale) - fontH * scale) / 2 else 0.0
            slot = base.copy(scale = scale, dy = dy, dx = base.dx + sign(base.dx) * wider)
        } else {
            val orient = if (kind == "vertical") 1 else 0
            slot = lumiereFixedSlot(if (kind == "crossed") "horizontal" else kind, clamped, region, fontH, nextAlpha) { offset, scale ->
                acrossOf(if (offset == clamped) index else current + offset, kind, orient, scale)
            }
        }
        val result = slot.copy(
            dx = slot.dx + shift.x, dy = slot.dy + shift.y,
            wrap = wrap ?: settle(index, kind, if (slot.orient >= 0.5) 1 else 0, slot.scale).wrap.toDouble(),
        )
        slotCache[key] = result
        return result
    }

    private fun firstLiveChange(time: Double, current: Int): Int {
        var c = current
        while (c >= 0 && lines[c].startTime - LUMIERE_LEAD + LUMIERE_MAX_LAG + LUMIERE_SLIDE + LUMIERE_TRACK_TIME > time) c -= 1
        return c + 1
    }

    fun lineTransform(index: Int, time: Double): LumiereLineTransform {
        val view = lineOf(index)
        val current = lumiereWindowCursor(lines, time).current
        val first = firstLiveChange(time, current)
        val base = first - 1
        val initialKind = typographyAt(base)
        val initial = slotOf(index, base, initialKind)
        var dx = initial.dx; var dy = initial.dy; var scale = initial.scale; var rotation = initial.rotation
        var alpha = initial.alpha; var orient = initial.orient; var wrap = initial.wrap
        var latestBefore: LumiereSlot? = null; var latestAfter: LumiereSlot? = null
        var latestPhase = 1.0; var latestStart = Double.NEGATIVE_INFINITY; var latestKind = ""
        var restOrient = initial.orient; var restWrap = initial.wrap
        val moves = ArrayList<LumiereMove>()
        var widthCap = maxWidthOf(initialKind)
        var jitterOn = if (initialKind == "crossed") 0.0 else 1.0
        var awayX = lumiereStackX(initialKind) * lumiereAwayOf(index, base, initialKind)
        var awayY = (1 - lumiereStackX(initialKind)) * lumiereAwayOf(index, base, initialKind)
        var hero = if (index == base) 1.0 else 0.0
        val last = min(lineCount - 1, current)
        for (c in first..last) {
            val beforeKind = typographyAt(c - 1)
            val afterKind = typographyAt(c)
            val before = slotOf(index, c - 1, beforeKind)
            val after = slotOf(index, c, afterKind)
            val kindChanged = beforeKind != afterKind
            if (before == after && !kindChanged) continue
            val (phase, start) = lumiereLinePhase(lines, c, index - c, time)
            val k = lumiereEaseInOutSine(phase)
            if (kindChanged) {
                widthCap += (maxWidthOf(afterKind) - maxWidthOf(beforeKind)) * k
                jitterOn += ((if (afterKind == "crossed") 0.0 else 1.0) - (if (beforeKind == "crossed") 0.0 else 1.0)) * k
            }
            val awayBefore = lumiereAwayOf(index, c - 1, beforeKind)
            val awayAfter = lumiereAwayOf(index, c, afterKind)
            awayX += (lumiereStackX(afterKind) * awayAfter - lumiereStackX(beforeKind) * awayBefore) * k
            awayY += ((1 - lumiereStackX(afterKind)) * awayAfter - (1 - lumiereStackX(beforeKind)) * awayBefore) * k
            hero += ((if (index == c) 1.0 else 0.0) - (if (index == c - 1) 1.0 else 0.0)) * k
            val fade = if (after.alpha < before.alpha) lumiereEaseInOutCubic(phase / 0.6)
            else if (after.alpha > before.alpha) lumiereEaseInOutCubic((phase - 0.4) / 0.6) else k
            dx += (after.dx - before.dx) * k
            dy += (after.dy - before.dy) * k
            scale += (after.scale - before.scale) * k
            rotation += (after.rotation - before.rotation) * k
            alpha += (after.alpha - before.alpha) * fade
            orient += (after.orient - before.orient) * phase
            wrap += (after.wrap - before.wrap) * k
            if (phase > 0) { latestBefore = before; latestAfter = after; latestPhase = phase; latestStart = start; latestKind = afterKind }
            if (phase >= 1) { restOrient = after.orient; restWrap = after.wrap; moves.clear() }
            else if (phase > 0) moves += LumiereMove(after.orient, after.wrap, phase, alwaysFly || afterKind == "crossed" || before.orient != after.orient)
        }
        val o = lumiereClamp01(orient)
        val w = lumiereClamp01(wrap)
        val hS = view.flow[0][0]; val hW = view.flow[0][1]; val vS = view.flow[1][0]; val vW = view.flow[1][1]
        val fitH = lumiereLerp(lumiereFitScale(hS.along, widthCap, scale), lumiereFitScale(hW.along, widthCap, scale), w)
        val fitV = lumiereLerp(lumiereFitScale(vS.along, columnCap, scale), lumiereFitScale(vW.along, columnCap, scale), w)
        val fitted = scale * lumiereLerp(fitH, fitV, o)
        val jitter = jitterOn * view.jitter * region.w * lumiereClamp01((1 - scale) / 0.5)
        val baseX = region.cx + dx + jitter * (1 - orient)
        val baseY = region.cy + dy + jitter * 0.5 * orient
        val alongH = (lumiereLerp(hS.inkWidth, hW.inkWidth, w) / height) * fitted
        val alongV = (lumiereLerp(vS.inkHeight, vW.inkHeight, w) / height) * fitted
        val x = baseX + (lumiereClampInto(baseX, alongH, band.left, band.right) - baseX) * (1 - o) * jitterOn
        val y = baseY + (lumiereClampInto(baseY, alongV, band.top, band.bottom) - baseY) * o * jitterOn
        val age = time - view.line.startTime
        val m = view.motionPhase
        val orbitX = sin(time * 0.52 + m) * LUMIERE_ORBIT
        val orbitY = cos(time * 0.41 + m * 1.3) * LUMIERE_ORBIT * 0.7
        val held = lumiereClamp01(hero)
        val driftX = lumiereLerp(lumiereAwayDrift((view.vx * age + orbitX) * driftScale, awayX), (lumiereHeldDrift(view.vx, view.vy, age, 0) + orbitX) * driftScale, held)
        val driftY = lumiereLerp(lumiereAwayDrift((view.vy * age + orbitY) * driftScale, awayY), (lumiereHeldDrift(view.vx, view.vy, age, 1) + orbitY) * driftScale, held)
        val settledOrient = latestAfter?.orient ?: initial.orient
        return LumiereLineTransform(
            current = current, x = (x + driftX) * height, y = (y + driftY) * height,
            scale = fitted * (1 + 0.025 * sin(time * 0.43 + m)), rotation = rotation + 0.03 * sin(time * 0.23 + m * 0.7),
            alpha = lumiereClamp01(alpha), toOrient = settledOrient, wrap = w, orient = o,
            phase = if (latestAfter != null) latestPhase else 1.0,
            fly = latestAfter != null && latestPhase < 1 && (alwaysFly || latestKind == "crossed" || latestBefore!!.orient != latestAfter.orient),
            restOrient = restOrient, restWrap = restWrap, moves = moves, slideStart = latestStart,
        )
    }

    fun glyphLocal(view: LumiereLineView, glyph: LumiereGlyph, time: Double, t: LumiereLineTransform): LumiereGlyphLocal {
        fun at(orient: Double, wrap: Double) = view.flow[if (orient >= 0.5) 1 else 0][if (wrap >= 0.5) 1 else 0].points[glyph.index]
        var point = at(t.restOrient, t.restWrap)
        var orientation = t.restOrient
        var flying = 0.0
        for (move in t.moves) {
            val target = at(move.toOrient, move.toWrap)
            if (move.fly) {
                val s = lumiereFlightProgress(glyph.flight, move.phase)
                point = lumiereFlightPoint(glyph.flight, point, target, s)
                orientation = lumiereLerp(orientation, move.toOrient, s)
                flying = max(flying, sin(PI * s))
            } else {
                val k = lumiereEaseInOutSine(move.phase)
                point = LumierePoint(lumiereLerp(point.x, target.x, k), lumiereLerp(point.y, target.y, k))
                orientation = lumiereLerp(orientation, move.toOrient, k)
            }
        }
        var x = point.x
        var y = point.y
        var rotation = glyph.vRotation * orientation + glyph.flight.spin * flying
        val pulse = 1 - 0.28 * flying
        val gather = lumiereSmooth((time - (view.line.startTime - LUMIERE_LEAD - LUMIERE_GATHER_LEAD)) / LUMIERE_GATHER)
        val scatter = (1 - gather).pow(2) * decay.strength
        x += glyph.scatterX * heroPx * scatter
        y += glyph.scatterY * heroPx * scatter
        val amount = lumiereDecayAmount(decay, glyph.timing.start, time) * glyph.driftSpeed
        x += glyph.driftDx * heroPx * amount
        y += glyph.driftDy * heroPx * amount
        rotation += glyph.driftSpin * amount * 0.18
        val breath = heroPx * 0.022 * min(1.0, decay.strength + 0.3)
        x += sin(time * 0.9 + glyph.phase) * breath
        y += cos(time * 1.13 + glyph.phase * 1.7) * breath
        return LumiereGlyphLocal(x, y, rotation, gather, flying, glyph.scale * pulse)
    }

    fun activeRange(time: Double): Triple<Int, Int, Int> {
        val current = lumiereWindowCursor(lines, time).current
        val first = firstLiveChange(time, current)
        return Triple(max(0, min(current, first) - LUMIERE_WINDOW_REACH), min(lineCount - 1, max(current, 0) + LUMIERE_WINDOW_REACH), current)
    }

    // ── windowProtection.ts ──

    private val boxes = List(4) { LumiereProtectBox() }
    private var protectCount = 0

    private fun buildProtect(time: Double, current: Int, low: Int, transforms: Map<Int, LumiereLineTransform>) {
        protectCount = 0
        var later = 0.0
        var j = current
        while (j >= low && protectCount < boxes.size) {
            val eased = lumiereEaseInOutSine((time - (lines[j].startTime - LUMIERE_LEAD)) / LUMIERE_SLIDE)
            val t = transforms.getValue(j)
            val o = lumiereClamp01(t.orient)
            val weight = (eased - later) * lumiereClamp01(t.alpha) * (1 - 4 * o * (1 - o))
            later = eased
            if (weight > 1e-4) {
                val f = lineOf(j).flow
                val w = t.wrap
                val inkW = lumiereLerp(lumiereLerp(f[0][0].inkWidth, f[0][1].inkWidth, w), lumiereLerp(f[1][0].inkWidth, f[1][1].inkWidth, w), t.orient)
                val inkH = lumiereLerp(lumiereLerp(f[0][0].inkHeight, f[0][1].inkHeight, w), lumiereLerp(f[1][0].inkHeight, f[1][1].inkHeight, w), t.orient)
                val box = boxes[protectCount]
                box.line = j; box.x = t.x; box.y = t.y; box.cos = cos(t.rotation); box.sin = sin(t.rotation)
                box.halfW = (inkW / 2) * t.scale; box.halfH = (inkH / 2) * t.scale
                box.margin = LUMIERE_PROTECT_MARGIN * heroPx * t.scale; box.weight = weight
                protectCount += 1
            }
            if (eased >= 1) break
            j -= 1
        }
    }

    private fun shield(index: Int, x: Double, y: Double, radius: Double, flying: Double) =
        if (protectCount > 0) lumiereProtectedAlpha(lumiereProtectionAt(boxes, protectCount, index, x, y, radius) * (1 - flying)) else 1.0

    // ── lyricWindow.ts 的 update ──

    fun frame(time: Double, beams: List<LumiereResolvedBeam>, litColor: DoubleArray, unlitColor: DoubleArray, unlitAlpha: Double, intensity: Double): LumiereWindowFrame {
        val out = ArrayList<LumiereGlyphDraw>()
        val spots = ArrayList<LumiereSpotDraw>()
        if (lineCount == 0) return LumiereWindowFrame(out, spots)
        val litHex = lumiereHexOf(litColor)
        val starHex = lumiereHexOf(lumiereMixRgb(litColor, LUMIERE_WHITE, 0.5))
        val (low, high, cursor) = activeRange(time)
        val transforms = HashMap<Int, LumiereLineTransform>()
        for (index in low..high) transforms[index] = lineTransform(index, time)
        buildProtect(time, cursor, low, transforms)
        for (index in low..high) {
            val view = lineOf(index)
            val t = transforms.getValue(index)
            val lineAlpha = t.alpha * intensity
            if (lineAlpha <= 0.003) continue
            val passed = index < t.current || (index == t.current && time > view.line.endTime)
            val passedAge = if (passed) time - view.line.endTime else 0.0
            val passedDim = if (passed) lumiereLerp(1.0, 0.75, lumiereClamp01(passedAge / 2.5)) else 1.0
            val lineFontPx = heroPx * t.scale
            val cs = cos(t.rotation); val sn = sin(t.rotation)
            for (glyph in view.glyphs) {
                if (glyph.blank) continue
                val local = glyphLocal(view, glyph, time, t)
                val fontPx = lineFontPx * local.scale
                val gx = t.x + (local.x * cs - local.y * sn) * t.scale
                val gy = t.y + (local.x * sn + local.y * cs) * t.scale
                val lit = lumiereSmooth((time - glyph.timing.start) / max(glyph.timing.end - glyph.timing.start, LUMIERE_LIGHT_UP))
                val flash = lumiereFlashEnvelope(glyph.timing, time, 0.45)
                val illumination = lumiereCompressLight(lumiereLightAt(beams, gx / height, gy / height))
                val heat = lumiereClamp01(illumination * 1.3 + flash * 0.2)
                val dissolve = 1 - lumiereClamp01((lumiereDecayAmount(decay, glyph.timing.start, time) * glyph.driftSpeed - LUMIERE_DISSOLVE_FROM) / LUMIERE_DISSOLVE_SPAN)
                val sh = shield(index, gx, gy, fontPx / 2, local.flying)
                val glyphAlpha = lineAlpha * passedDim * dissolve * (0.35 + 0.65 * local.gather) * sh
                val revealed = min(1.0, unlitAlpha + illumination * 0.45 + local.flying * 0.4)
                val alpha = glyphAlpha * (revealed * (1 - lit) + lit * min(1.0, 0.55 + 0.4 * heat + 0.3 * local.flying))
                val hot = lumiereMixRgb(litColor, LUMIERE_WHITE, lumiereClamp01(0.08 * illumination + 0.1 * flash + 0.25 * local.flying))
                val cold = lumiereMixRgb(unlitColor, litColor, illumination * 0.35)
                val color = lumiereHexOf(lumiereMixRgb(cold, hot, lit))
                val haloAlpha = glyphAlpha * lit * (0.03 + 0.14 * illumination + 0.08 * flash)
                val haloSize = fontPx * (2.2 + 0.8 * illumination)
                val starAlpha = lineAlpha * flash * 0.55 * sh
                out += LumiereGlyphDraw(
                    glyph.char, gx, gy, t.rotation + local.rotation, fontPx, alpha, color, lit,
                    if (haloAlpha > 0.003) haloAlpha else 0.0, haloSize, litHex,
                    if (starAlpha > 0.003) starAlpha else 0.0, gx + fontPx * glyph.starDx, gy + fontPx * glyph.starDy,
                    glyph.starRotation * (1 + (1 - flash) * 0.6), fontPx * glyph.starSize * (0.8 + 0.5 * flash), starHex,
                )
            }
            val spotAlpha = lineAlpha * lumiereSmooth((time - view.singStart + 0.3) / 0.3) * (1 - lumiereSmooth((time - view.singEnd) / 0.6))
            if (spotAlpha > 0.003) {
                val vertical = t.toOrient >= 0.5
                fun spotOf(wrap: Int): LumierePoint {
                    val path = view.flow[if (vertical) 1 else 0][wrap].spots
                    val timings = view.glyphs.map { it.timing }
                    return LumierePoint(lumiereSpotX(timings, view.glyphs.map { path[it.index].x }, time), lumiereSpotX(timings, view.glyphs.map { path[it.index].y }, time))
                }
                val local = if (t.wrap <= 0) spotOf(0) else if (t.wrap >= 1) spotOf(1) else spotOf(0).let { a -> spotOf(1).let { b -> LumierePoint(lumiereLerp(a.x, b.x, t.wrap), lumiereLerp(a.y, b.y, t.wrap)) } }
                val px = t.x + (local.x * cs - local.y * sn) * t.scale
                val py = t.y + (local.x * sn + local.y * cs) * t.scale
                val size = lineFontPx * 5
                spots += LumiereSpotDraw(px, py, t.rotation, size * (if (vertical) 1.0 else 1.6), size * (if (vertical) 1.6 else 1.0), 0.07 * spotAlpha, litHex)
            }
        }
        return LumiereWindowFrame(out, spots)
    }

    /** glyphAnchor：某个字在时刻 time 的位置（dp，含崩解偏移）与字号，给十字爆闪定位。 */
    fun glyphAnchor(lineIndex: Int, glyphIndex: Int, time: Double): Triple<Double, Double, Double> {
        val t = lineTransform(lineIndex, time)
        val view = lineOf(lineIndex)
        val glyph = view.glyphs.getOrNull(glyphIndex)?.takeIf { it.index == glyphIndex } ?: view.glyphs.first { it.index == glyphIndex }
        val local = glyphLocal(view, glyph, time, t)
        val cs = cos(t.rotation); val sn = sin(t.rotation)
        return Triple(t.x + (local.x * cs - local.y * sn) * t.scale, t.y + (local.x * sn + local.y * cs) * t.scale, heroPx * t.scale * local.scale)
    }

    /** glyphTimes：十字爆闪按它挑字。 */
    fun glyphTimes(lineIndex: Int): List<Pair<Int, Double>> {
        val chars = graphemes.getOrNull(lineIndex) ?: return emptyList()
        val timings = lumiereGlyphTimings(lines[lineIndex])
        return chars.indices.filter { chars[it].isNotBlank() }.map { it to (timings.getOrNull(it)?.start ?: lines[lineIndex].startTime) }
    }
}
