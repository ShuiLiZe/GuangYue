// 商籁（Folia sonnet）纯逻辑：逐函数对译，不依赖 Android，可单测。
// 对应 Folia 文件：src/components/visualizer/sonnet/
//   sonnetProgram.ts（编排：段落→镜头→转场）、sonnetSemantic.ts（语义词块）、visualizerSeedHash.ts（散列）、
//   sonnetTypographyRoles.ts（主角 / 半主角）、sonnetTypographyLayout.ts（7 种版式的字号、竖排、巨字底纹）、
//   sonnetShotFlowLayouts.ts（流式版式）、sonnetPosterBlocksLayout.ts（海报块）、sonnetGlyphLayout.ts（逐字落点）、
//   sonnetMotion.ts（缓动、机位路径、呼吸、焦点平滑）、sonnetTransitions.ts（镜头 / 段落转场帧）、
//   sonnetCameraTracking.ts（焦点跟踪）、sonnetGuides.ts 的 resolveSonnetGuideCue、sonnetFrameDecor.ts 的 spec。
// 单位：Folia 的 CSS 像素在这里就是 dp（舞台按 density 整体缩放），所以常数原样照搬。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

// ───────────────────────────── 散列（visualizerSeedHash.ts） ─────────────────────────────

/** hashVisualizerSeed：FNV-1a 32 位，返回无符号值。 */
internal fun sonnetHash(value: String): Long {
    var hash = 2166136261L.toInt()
    for (ch in value) {
        hash = hash xor ch.code
        hash *= 16777619
    }
    return hash.toLong() and 0xffffffffL
}

/** mixVisualizerSeed：Math.imul((trunc(seed) ^ salt) >>> 0, 2654435761) >>> 0。 */
internal fun sonnetMix(seed: Long, salt: Int): Long =
    ((seed.toInt() xor salt) * 2654435761L.toInt()).toLong() and 0xffffffffL

/** visualizerHash01。 */
internal fun sonnetHash01(seed: Long, index: Int, salt: Int): Double =
    sonnetMix(seed + (index + 1).toLong() * 97L, salt) / 4294967296.0

/** createVisualizerRng（mulberry32）：Folia 里用 Math.random 的地方（引导线碎片、巨字景深、圆的起笔）改用它，保证同一首歌每次一样。 */
internal class SonnetRandom(seed: Long) {
    private var state = seed.toInt()
    fun next(): Double {
        state += 0x6d2b79f5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
}

internal fun clamp01(v: Double) = min(1.0, max(0.0, v))
private fun clampD(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

// ───────────────────────────── 缓动（sonnetMotion.ts） ─────────────────────────────

private fun cubicCoordinate(p1: Double, p2: Double, t: Double): Double {
    val inv = 1 - t
    return 3 * inv * inv * t * p1 + 3 * inv * t * t * p2 + t * t * t
}

internal fun sonnetCubicBezier(x1: Double, y1: Double, x2: Double, y2: Double, value: Double): Double {
    val target = clamp01(value)
    if (target == 0.0 || target == 1.0) return target
    var low = 0.0
    var high = 1.0
    var parameter = target
    repeat(12) {
        val x = cubicCoordinate(x1, x2, parameter)
        if (x < target) low = parameter else high = parameter
        parameter = (low + high) / 2
    }
    return cubicCoordinate(y1, y2, parameter)
}

internal fun sonnetEaseInOut(v: Double) = sonnetCubicBezier(0.65, 0.0, 0.35, 1.0, v)
internal fun sonnetEaseExpoOut(v: Double) = if (v == 1.0) 1.0 else 1 - 2.0.pow(-10 * v)
internal fun sonnetEaseElasticOut(v: Double): Double {
    val p = 0.3
    return 2.0.pow(-10 * v) * sin((v - p / 4) * (2 * PI) / p) + 1
}

internal fun sonnetSegmentProgress(start: Double, end: Double, time: Double) =
    sonnetEaseExpoOut(clamp01((time - start) / max(end - start, 0.08)))

// ───────────────────────────── 类型（types.ts） ─────────────────────────────

internal enum class SonnetShotKind(val id: String) {
    EDITORIAL_COLUMN("editorial-column"), TYPE_IMPACT("type-impact"), FRAGMENT_COLLAGE("fragment-collage"),
    TRACKING_RIBBON("tracking-ribbon"), MASK_REVEAL("mask-reveal"), POSTER_BLOCKS("poster-blocks"), QUIET_TABLEAU("quiet-tableau"),
}

internal enum class SonnetTransitionKind(val id: String) { FAST_BLUR("fast-blur"), MONO_GLITCH("mono-glitch"), CAMERA_PULL("camera-pull") }

internal enum class SonnetParagraphKind(val id: String) { BREATH("breath"), VERSE("verse"), LIFT("lift"), CHORUS("chorus"), BREAK("break"), OUTRO("outro") }

internal data class SonnetGrapheme(val char: String, val startTime: Double, val endTime: Double)

internal data class SonnetSegment(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val startTime: Double,
    val endTime: Double,
    val graphemes: List<SonnetGrapheme>,
    val isWordLike: Boolean,
)

internal class SonnetCompiledLine(val sourceIndex: Int, val line: Line, val renderEndTime: Double, val segments: List<SonnetSegment>)

internal class SonnetCamera(val x: Double, val y: Double, val zoom: Double, val rotation: Double)

internal class SonnetShot(
    val id: String,
    val kind: SonnetShotKind,
    val startTime: Double,
    val endTime: Double,
    val lineIndices: List<Int>,
    /** buildCues：每个词块一个入场时刻（最后一个是 accent）。MG 只读最后一个。 */
    val cueTimes: List<Double>,
    val camera: SonnetCamera,
)

internal class SonnetTransition(val kind: SonnetTransitionKind, val startTime: Double, val endTime: Double)

internal class SonnetParagraph(
    val id: String,
    val kind: SonnetParagraphKind,
    val startTime: Double,
    val endTime: Double,
    val lines: List<SonnetCompiledLine>,
    val shots: List<SonnetShot>,
    val transitionOut: SonnetTransition?,
)

internal class SonnetProgram(val seed: String, val paragraphGapThreshold: Double, val paragraphs: List<SonnetParagraph>)

// ───────────────────────────── 语义词块（sonnetSemantic.ts + wordSegmentation.ts） ─────────────────────────────

private val PUNCTUATION_ONLY = Regex("^[\\s\\p{P}\\p{S}]+$")
private val WHITESPACE_ONLY = Regex("^\\s+$")

/** 一句的分词片段（拼起来等于原文）。 */
internal typealias SonnetWordSplitter = (Line) -> List<String>

/**
 * segmentLyricWords：保存的分词有效就用它，否则 Intl.Segmenter('word') ——
 * Android 的 java.text.BreakIterator 由 ICU 实现（中文按词典分词，与浏览器 Intl.Segmenter 同源）。
 */
internal val defaultSonnetWordSplitter: SonnetWordSplitter = { line ->
    val text = line.fullText
    val saved = line.wordSegments
    val auto = line.autoWordSegments
    if (saved != null && saved.isNotEmpty() && saved.joinToString("") == text) saved
    else if (auto != null && auto.isNotEmpty() && auto.joinToString("") == text) auto
    else {
        val bi = java.text.BreakIterator.getWordInstance()
        bi.setText(text)
        val out = ArrayList<String>()
        var a = bi.first()
        var b = bi.next()
        while (b != java.text.BreakIterator.DONE) {
            out += text.substring(a, b)
            a = b
            b = bi.next()
        }
        if (out.isEmpty() && text.isNotEmpty()) listOf(text) else out
    }
}

internal fun buildSonnetSemanticSegments(line: Line, split: SonnetWordSplitter = defaultSonnetWordSplitter): List<SonnetSegment> {
    if (line.fullText.isEmpty()) return emptyList()
    val timeline = GraphemeTimings.buildDenseLineGraphemeTimeline(line)
    val graphemeStrings = GraphemeTimings.splitLyricGraphemes(line.fullText)
    val rangeStart = IntArray(graphemeStrings.size)
    val rangeEnd = IntArray(graphemeStrings.size)
    var cursor = 0
    graphemeStrings.forEachIndexed { i, g -> rangeStart[i] = cursor; cursor += g.length; rangeEnd[i] = cursor }
    val parts = split(line)
    val segments = ArrayList<SonnetSegment>()
    var offset = 0
    parts.forEach { part ->
        val startOffset = offset
        val endOffset = offset + part.length
        offset = endOffset
        val gs = ArrayList<SonnetGrapheme>()
        for (i in graphemeStrings.indices) {
            if (rangeEnd[i] > startOffset && rangeStart[i] < endOffset) {
                val t = timeline.getOrNull(i) ?: continue
                gs += SonnetGrapheme(t.char, t.startTime, t.endTime)
            }
        }
        segments += SonnetSegment(
            text = line.fullText.substring(startOffset, min(endOffset, line.fullText.length)),
            startOffset = startOffset,
            endOffset = endOffset,
            startTime = gs.firstOrNull()?.startTime ?: line.startTime,
            endTime = gs.lastOrNull()?.endTime ?: line.endTime,
            graphemes = gs,
            isWordLike = !PUNCTUATION_ONLY.matches(part),
        )
    }
    // 标点黏到前一个词块上（空白不黏）。
    val sticky = ArrayList<SonnetSegment>()
    for (segment in segments) {
        val previous = sticky.lastOrNull()
        if (previous != null && !segment.isWordLike && !WHITESPACE_ONLY.matches(segment.text)) {
            sticky[sticky.size - 1] = previous.copy(
                text = previous.text + segment.text,
                endOffset = segment.endOffset,
                endTime = max(previous.endTime, segment.endTime),
                graphemes = previous.graphemes + segment.graphemes,
            )
        } else sticky += segment
    }
    return sticky
}

// ───────────────────────────── 编排（sonnetProgram.ts） ─────────────────────────────

internal val SONNET_SHOT_KINDS = SonnetShotKind.values().toList()
internal val SONNET_TRANSITION_KINDS = SonnetTransitionKind.values().toList()

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.5
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
}

internal fun sonnetParagraphGapThreshold(lines: List<Line>): Double {
    val gaps = (1 until lines.size).map { i ->
        lines[i].startTime - min(RenderHints.getLineRenderEndTime(lines[i - 1]), lines[i].startTime)
    }.filter { it > 0 }
    return clampD(median(gaps) * 2.5, 1.25, 3.5)
}

private fun metadataChanged(previous: Line, next: Line): Boolean =
    (previous.blockIndex != null && next.blockIndex != null && previous.blockIndex != next.blockIndex) ||
        (previous.songPart != null && next.songPart != null && previous.songPart != next.songPart)

private fun splitOversizedDraft(draft: List<SonnetCompiledLine>): List<List<SonnetCompiledLine>> {
    val output = ArrayList<List<SonnetCompiledLine>>()
    var remaining = draft
    var guard = 0
    while (remaining.size > 6 || (remaining.size > 1 && (remaining.last().renderEndTime - remaining[0].line.startTime) > 18)) {
        if (guard++ > 1000) break
        val candidates = ArrayList<Pair<Int, Double>>()
        // remaining.slice(2, -1)：下标 2 .. size-2
        for (k in 2 until remaining.size - 1) candidates += k to (remaining[k].line.startTime - remaining[k - 1].renderEndTime)
        val raw = candidates.filter { !it.second.isNaN() }.sortedByDescending { it.second }.firstOrNull()?.first ?: min(4, remaining.size - 1)
        val splitIndex = max(1, raw)
        output += remaining.subList(0, splitIndex)
        remaining = remaining.subList(splitIndex, remaining.size)
    }
    output += remaining
    return output
}

private val CHORUS_RE = Regex("chorus|副歌", RegexOption.IGNORE_CASE)
private val BREAK_RE = Regex("bridge|break|間奏|ブリッジ", RegexOption.IGNORE_CASE)
private val EXCLAIM_RE = Regex("[!?！？…]")

private fun classifyParagraph(lines: List<SonnetCompiledLine>, index: Int, total: Int): SonnetParagraphKind {
    if (lines.any { it.line.isChorus || CHORUS_RE.containsMatchIn(it.line.songPart ?: "") }) return SonnetParagraphKind.CHORUS
    if (lines.any { BREAK_RE.containsMatchIn(it.line.songPart ?: "") }) return SonnetParagraphKind.BREAK
    if (index == total - 1) return SonnetParagraphKind.OUTRO
    val duration = lines.last().renderEndTime - lines[0].line.startTime
    val segmentCount = lines.sumOf { l -> l.segments.count { it.isWordLike } }
    val punctuationCount = lines.sumOf { EXCLAIM_RE.findAll(it.line.fullText).count() }
    if (duration <= 3.5 || segmentCount <= 3) return SonnetParagraphKind.BREATH
    if (punctuationCount >= 2 || segmentCount / max(duration, 1.0) > 2.5) return SonnetParagraphKind.LIFT
    return SonnetParagraphKind.VERSE
}

private fun <T> chooseWithoutRepeat(choices: List<T>, seed: String, previous: T?): T {
    val start = (sonnetHash(seed) % choices.size).toInt()
    for (offset in choices.indices) {
        val candidate = choices[(start + offset) % choices.size]
        if (candidate != previous) return candidate
    }
    return choices[start]
}

private fun groupShotLines(lines: List<SonnetCompiledLine>): List<List<SonnetCompiledLine>> {
    val groups = ArrayList<List<SonnetCompiledLine>>()
    var current = ArrayList<SonnetCompiledLine>()
    var groupStart = 0.0
    for (line in lines) {
        if (current.isEmpty()) {
            current.add(line); groupStart = line.line.startTime
        } else {
            val durationSoFar = line.renderEndTime - groupStart
            if (current.size < 4 && durationSoFar <= 6.0) current.add(line)
            else {
                groups += current
                current = arrayListOf(line)
                groupStart = line.line.startTime
            }
        }
    }
    if (current.isNotEmpty()) groups += current
    return groups
}

private fun buildShots(
    lines: List<SonnetCompiledLine>,
    kind: SonnetParagraphKind,
    paragraphIndex: Int,
    seed: String,
    previousKind: SonnetShotKind?,
): List<SonnetShot> {
    var lastKind = previousKind
    return groupShotLines(lines).mapIndexed { shotIndex, group ->
        val signature = group.joinToString("|") { it.line.fullText }
        var shotKind = chooseWithoutRepeat(SONNET_SHOT_KINDS, "$seed:$paragraphIndex:$shotIndex:$signature", lastKind)
        val wordCount = group.sumOf { g -> g.segments.count { it.isWordLike } }
        if (kind == SonnetParagraphKind.BREATH && shotIndex == 0 && wordCount <= 2) shotKind = SonnetShotKind.QUIET_TABLEAU
        if (kind == SonnetParagraphKind.CHORUS && shotKind == SonnetShotKind.QUIET_TABLEAU) shotKind = SonnetShotKind.TYPE_IMPACT
        lastKind = shotKind
        val random = sonnetHash("$seed:$paragraphIndex:$shotIndex:camera")
        val zoomRandom = ((random ushr 16) and 255) / 255.0
        val zoomBase = when (shotKind) { SonnetShotKind.POSTER_BLOCKS -> 1.02; SonnetShotKind.QUIET_TABLEAU -> 1.12; else -> 1.22 }
        val zoomSpan = when (shotKind) { SonnetShotKind.POSTER_BLOCKS -> 0.16; SonnetShotKind.QUIET_TABLEAU -> 0.2; else -> 0.26 }
        SonnetShot(
            id = "p$paragraphIndex-s$shotIndex",
            kind = shotKind,
            startTime = group[0].line.startTime,
            endTime = group.last().renderEndTime,
            lineIndices = group.map { it.sourceIndex },
            cueTimes = group.flatMap { it.segments }.filter { it.text.isNotEmpty() }.map { it.startTime },
            camera = SonnetCamera(
                x = ((random and 255) / 255.0 - 0.5) * 0.18,
                y = (((random ushr 8) and 255) / 255.0 - 0.5) * 0.14,
                zoom = zoomBase + zoomRandom * zoomSpan,
                rotation = (((random ushr 24) and 255) / 255.0 - 0.5) * 0.08,
            ),
        )
    }
}

internal fun compileSonnetProgram(lines: List<Line>, seed: String = "sonnet", split: SonnetWordSplitter = defaultSonnetWordSplitter): SonnetProgram {
    val compiled = lines.mapIndexed { i, line ->
        SonnetCompiledLine(
            sourceIndex = i,
            line = line,
            renderEndTime = max(line.startTime, min(RenderHints.getLineRenderEndTime(line), lines.getOrNull(i + 1)?.startTime ?: Double.POSITIVE_INFINITY)),
            segments = buildSonnetSemanticSegments(line, split),
        )
    }
    val gapThreshold = sonnetParagraphGapThreshold(lines)
    val drafts = ArrayList<List<SonnetCompiledLine>>()
    var current = ArrayList<SonnetCompiledLine>()
    compiled.forEachIndexed { index, line ->
        val previous = compiled.getOrNull(index - 1)
        val gap = if (previous != null) line.line.startTime - previous.renderEndTime else 0.0
        val boundary = previous != null && (metadataChanged(previous.line, line.line) || gap >= gapThreshold)
        if (boundary && current.isNotEmpty()) {
            drafts += splitOversizedDraft(current)
            current = ArrayList()
        }
        current.add(line)
    }
    if (current.isNotEmpty()) drafts += splitOversizedDraft(current)

    var previousShot: SonnetShotKind? = null
    var previousTransition: SonnetTransitionKind? = null
    val paragraphs = drafts.mapIndexed { index, draft ->
        val kind = classifyParagraph(draft, index, drafts.size)
        val shots = buildShots(draft, kind, index, seed, previousShot)
        previousShot = shots.lastOrNull()?.kind ?: previousShot
        val next = drafts.getOrNull(index + 1)
        val endTime = draft.last().renderEndTime
        val gap = if (next != null) next[0].line.startTime - endTime else 0.0
        val transitionKind = if (next != null) chooseWithoutRepeat(SONNET_TRANSITION_KINDS, "$seed:$index:transition", previousTransition) else null
        if (transitionKind != null) previousTransition = transitionKind
        val transitionDuration = if (next != null) min(0.3, max(0.16, if (gap > 0) gap * 0.5 else 0.2)) else 0.0
        val transitionEnd = next?.get(0)?.line?.startTime ?: endTime
        SonnetParagraph(
            id = "sonnet-p$index",
            kind = kind,
            startTime = draft[0].line.startTime,
            endTime = endTime,
            lines = draft,
            shots = shots,
            transitionOut = transitionKind?.let { SonnetTransition(it, max(draft[0].line.startTime, transitionEnd - transitionDuration), transitionEnd) },
        )
    }
    return SonnetProgram(seed, gapThreshold, paragraphs)
}

internal fun findSonnetParagraphIndexAtTime(program: SonnetProgram, time: Double): Int {
    for (i in program.paragraphs.indices.reversed()) if (time >= program.paragraphs[i].startTime) return i
    return 0
}

// ───────────────────────────── 主角（sonnetTypographyRoles.ts） ─────────────────────────────

internal enum class SonnetRole { HERO, SEMI_HERO, SUPPORT, DECORATION }

internal fun SonnetRole.isEmphasis() = this == SonnetRole.HERO || this == SonnetRole.SEMI_HERO

/** resolveSonnetRoleFontWeight（自动字重）。 */
internal fun sonnetRoleWeight(role: SonnetRole): Int = when (role) {
    SonnetRole.HERO, SonnetRole.SEMI_HERO -> 900
    SonnetRole.DECORATION -> 300
    SonnetRole.SUPPORT -> 700
}

internal fun sonnetVisibleLength(segment: SonnetSegment) = segment.graphemes.count { it.char.isNotBlank() }

internal fun sonnetHeroScore(segment: SonnetSegment): Double =
    min(sonnetVisibleLength(segment), 8) * 14.0 + min(2.5, max(0.0, segment.endTime - segment.startTime)) * 18

internal fun findSonnetHeroIndex(segments: List<SonnetSegment>): Int {
    var bestIndex = segments.indexOfFirst { it.isWordLike }
    var bestScore = Double.NEGATIVE_INFINITY
    segments.forEachIndexed { index, segment ->
        if (!segment.isWordLike || sonnetVisibleLength(segment) == 0) return@forEachIndexed
        val score = sonnetHeroScore(segment)
        if (score > bestScore) { bestScore = score; bestIndex = index }
    }
    return max(0, bestIndex)
}

internal fun findSonnetSemiHeroIndices(segments: List<SonnetSegment>, heroIndex: Int): List<Int> {
    val hero = segments.getOrNull(heroIndex) ?: return emptyList()
    val wordLikeCount = segments.count { it.isWordLike && sonnetVisibleLength(it) > 0 }
    if (wordLikeCount < 4) return emptyList()
    val threshold = sonnetHeroScore(hero) * 0.35
    val candidates = segments.indices.filter { i ->
        val s = segments[i]
        i != heroIndex && s.isWordLike && sonnetVisibleLength(s) >= 2 && abs(i - heroIndex) >= 2 && sonnetHeroScore(s) >= threshold
    }
    if (candidates.isEmpty()) return emptyList()
    fun bestOf(list: List<Int>): Int? = list.fold(null as Int?) { best, item ->
        if (best == null || sonnetHeroScore(segments[item]) > sonnetHeroScore(segments[best])) item else best
    }
    val heroLeansEarly = heroIndex <= (segments.size - 1) / 2.0
    val primarySide = candidates.filter { if (heroLeansEarly) it > heroIndex else it < heroIndex }
    val secondarySide = candidates.filter { if (heroLeansEarly) it < heroIndex else it > heroIndex }
    val picks = ArrayList<Int>()
    val primary = bestOf(primarySide) ?: bestOf(secondarySide)
    if (primary != null) picks += primary
    if (wordLikeCount >= 9 && primary != null) {
        bestOf(secondarySide.filter { abs(it - primary) >= 2 })?.let { picks += it }
    }
    return picks.sorted()
}

// ───────────────────────────── 版式（sonnetTypographyLayout.ts + 流式 + 海报块） ─────────────────────────────

/** 量字宽：text 在 weight / fontSize（dp）下的横向前进量。 */
internal fun interface SonnetMeasure {
    fun width(text: String, weight: Int, fontSize: Double): Double
}

internal class SonnetPlacement(
    val segmentIndex: Int,
    val displayText: String,
    val role: SonnetRole,
    val fontScale: Double,
    val measuredWidth: Double,
    val measuredHeight: Double,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val enterX: Double,
    val enterY: Double,
    val vertical: Boolean,
    val layoutVertical: Boolean,
    val timingPhase: Double,
)

internal class SonnetBox(
    val index: Int,
    var isHero: Boolean,
    var isSemiHero: Boolean,
    var displayText: String,
    var verticalDisplayText: String?,
    var verticalMeasuredWidth: Double?,
    var verticalMeasuredHeight: Double?,
    var verticalFontScale: Double?,
    var fontScale: Double,
    var vertical: Boolean,
    var layoutVertical: Boolean,
    var rotation: Double,
    var measuredWidth: Double,
    var measuredHeight: Double,
    val timingPhase: Double,
    var role: SonnetRole?,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var enterX: Double = 0.0,
    var enterY: Double = 0.0,
) {
    fun copyBox() = SonnetBox(
        index, isHero, isSemiHero, displayText, verticalDisplayText, verticalMeasuredWidth, verticalMeasuredHeight, verticalFontScale,
        fontScale, vertical, layoutVertical, rotation, measuredWidth, measuredHeight, timingPhase, role, x, y, enterX, enterY,
    )
}

private val CJK_TEXT = Regex("[\\u4e00-\\u9fff\\u3040-\\u30ff\\uac00-\\ud7af]")

private fun graphemeChars(segment: SonnetSegment): List<String> =
    if (segment.graphemes.isNotEmpty()) segment.graphemes.map { it.char } else segment.text.codePoints().toArray().map { String(Character.toChars(it)) }

internal fun sonnetIsLayoutSegment(segment: SonnetSegment) = segment.text.isNotBlank()

internal fun sonnetFlowGaps(baseFontSize: Double): Pair<Double, Double> {
    val flowGap = clampD(baseFontSize * 0.35, 16.0, 40.0)
    return flowGap to max(24.0, flowGap * 1.35)
}

private class FlowCtx(val boxes: List<SonnetBox>, val heroIndex: Int, val width: Double, val height: Double, val flowGap: Double, val stackGap: Double)

private fun placeWithGlobalFit(ctx: FlowCtx, place: (Double) -> Unit) {
    val snapshot = ctx.boxes.map { Triple(it.fontScale, it.measuredWidth, it.measuredHeight) }
    val safeHalfW = ctx.width * 0.48
    val safeHalfH = ctx.height * 0.46
    for (globalScale in doubleArrayOf(1.0, 0.92, 0.84, 0.76, 0.68, 0.6, 0.52)) {
        ctx.boxes.forEachIndexed { i, box ->
            box.fontScale = snapshot[i].first * globalScale
            box.measuredWidth = snapshot[i].second * globalScale
            box.measuredHeight = snapshot[i].third * globalScale
        }
        place(globalScale)
        val fits = ctx.boxes.all { abs(it.x) + it.measuredWidth / 2 <= safeHalfW + 0.5 && abs(it.y) + it.measuredHeight / 2 <= safeHalfH + 0.5 }
        if (fits) return
    }
}

private fun layoutQuietTableau(ctx: FlowCtx, variant: Int) {
    val boxes = ctx.boxes
    val heroBox = boxes[ctx.heroIndex]
    val horizontalCard = variant == 2 || variant == 3
    boxes.forEach { it.layoutVertical = !horizontalCard }
    val safeHalfH = ctx.height * 0.46
    placeWithGlobalFit(ctx) {
        heroBox.x = 0.0
        heroBox.y = if (horizontalCard) 0.0 else -ctx.height * 0.1
        val stagger = if (variant == 3) 70.0 else 0.0
        val columnStep = boxes.maxOf { it.measuredWidth } + ctx.stackGap + stagger
        fun xFor(box: SonnetBox, index: Int): Double = when (variant) {
            1 -> heroBox.x - heroBox.measuredWidth / 2 + box.measuredWidth / 2
            3 -> heroBox.x + (if (index % 2 == 0) 1 else -1) * 35
            else -> heroBox.x
        }
        var column = 0
        var currentY = heroBox.y - heroBox.measuredHeight / 2 - ctx.stackGap
        for (i in ctx.heroIndex - 1 downTo 0) {
            val box = boxes[i]
            if (currentY - box.measuredHeight < -safeHalfH) { column += 1; currentY = safeHalfH }
            box.x = xFor(box, i) + column * columnStep
            box.y = currentY - box.measuredHeight / 2
            currentY -= box.measuredHeight + ctx.stackGap
            when (variant) {
                1 -> { box.enterX = 20.0; box.enterY = 0.0 }
                3 -> { box.enterX = if (box.x > heroBox.x) 30.0 else -30.0; box.enterY = 0.0 }
                else -> { box.enterX = 0.0; box.enterY = 20.0 }
            }
        }
        column = 0
        currentY = heroBox.y + heroBox.measuredHeight / 2 + ctx.stackGap
        for (i in ctx.heroIndex + 1 until boxes.size) {
            val box = boxes[i]
            if (currentY + box.measuredHeight > safeHalfH) { column += 1; currentY = -safeHalfH }
            box.x = xFor(box, i) - column * columnStep
            box.y = currentY + box.measuredHeight / 2
            currentY += box.measuredHeight + ctx.stackGap
            when (variant) {
                1 -> { box.enterX = -20.0; box.enterY = 0.0 }
                3 -> { box.enterX = if (box.x > heroBox.x) 30.0 else -30.0; box.enterY = 0.0 }
                else -> { box.enterX = 0.0; box.enterY = -20.0 }
            }
        }
    }
}

private fun layoutTrackingRibbon(ctx: FlowCtx, variant: Int) {
    val boxes = ctx.boxes
    val heroBox = boxes[ctx.heroIndex]
    boxes.forEach { it.layoutVertical = false }
    placeWithGlobalFit(ctx) {
        heroBox.x = 0.0
        heroBox.y = 0.0
        fun alignY(box: SonnetBox, index: Int): Double = when (variant) {
            1 -> heroBox.y + heroBox.measuredHeight / 2 - box.measuredHeight / 2
            2 -> heroBox.y - heroBox.measuredHeight / 2 + box.measuredHeight / 2
            else -> heroBox.y + (if (index % 2 == 0) 10 else -10)
        }
        val enter = if (variant == 2) 20.0 else 30.0
        var currentX = heroBox.x - heroBox.measuredWidth / 2 - ctx.flowGap
        for (i in ctx.heroIndex - 1 downTo 0) {
            val box = boxes[i]
            box.x = currentX - box.measuredWidth / 2
            box.y = alignY(box, i)
            currentX -= box.measuredWidth + ctx.flowGap
            box.enterX = enter; box.enterY = 0.0
        }
        currentX = heroBox.x + heroBox.measuredWidth / 2 + ctx.flowGap
        for (i in ctx.heroIndex + 1 until boxes.size) {
            val box = boxes[i]
            box.x = currentX + box.measuredWidth / 2
            box.y = alignY(box, i)
            currentX += box.measuredWidth + ctx.flowGap
            box.enterX = -enter; box.enterY = 0.0
        }
    }
}

private fun layoutEditorialColumn(ctx: FlowCtx, variant: Int, secondaryHeroIndex: Int) {
    val boxes = ctx.boxes
    val heroIndex = ctx.heroIndex
    val heroBox = boxes[heroIndex]
    val width = ctx.width
    val height = ctx.height
    val flowGap = ctx.flowGap
    val stackGap = ctx.stackGap
    when (variant) {
        0 -> {
            boxes.forEach { it.layoutVertical = true }
            placeWithGlobalFit(ctx) {
                heroBox.x = -width * 0.15
                heroBox.y = 0.0
                var currentY = heroBox.y - heroBox.measuredHeight / 2 + stackGap * 0.5
                for (i in 0 until heroIndex) {
                    val box = boxes[i]
                    box.x = heroBox.x + heroBox.measuredWidth / 2 + flowGap + box.measuredWidth / 2
                    box.y = currentY + box.measuredHeight / 2
                    currentY += box.measuredHeight + stackGap
                    box.enterX = -20.0; box.enterY = 0.0
                }
                currentY = heroBox.y - heroBox.measuredHeight / 2 + stackGap * 0.5
                for (i in heroIndex + 1 until boxes.size) {
                    val box = boxes[i]
                    box.x = heroBox.x - heroBox.measuredWidth / 2 - flowGap - box.measuredWidth / 2
                    box.y = currentY + box.measuredHeight / 2
                    currentY += box.measuredHeight + stackGap
                    box.enterX = 20.0; box.enterY = 0.0
                }
            }
        }
        1 -> {
            boxes.forEach { it.layoutVertical = true }
            placeWithGlobalFit(ctx) {
                val rightEdge = width * 0.28
                val safeHalfH = height * 0.46
                val railStep = boxes.maxOf { it.measuredWidth } + stackGap
                val totalHeight = boxes.sumOf { it.measuredHeight } + stackGap * (boxes.size - 1)
                val fitsSingleRail = boxes.sumOf { it.measuredHeight } * 0.52 + stackGap * (boxes.size - 1) <= safeHalfH * 2
                if (fitsSingleRail) {
                    var currentY = -totalHeight / 2
                    boxes.forEach { box ->
                        box.x = rightEdge - box.measuredWidth / 2
                        box.y = currentY + box.measuredHeight / 2
                        currentY += box.measuredHeight + stackGap
                        box.enterX = 20.0; box.enterY = 0.0
                    }
                    return@placeWithGlobalFit
                }
                var rail = 0
                var currentY = -safeHalfH
                boxes.forEach { box ->
                    if (currentY + box.measuredHeight > safeHalfH) { rail += 1; currentY = -safeHalfH }
                    box.x = (rightEdge - rail * railStep) - box.measuredWidth / 2
                    box.y = currentY + box.measuredHeight / 2
                    currentY += box.measuredHeight + stackGap
                    box.enterX = 20.0; box.enterY = 0.0
                }
            }
        }
        2 -> {
            boxes.forEach { it.layoutVertical = false }
            placeWithGlobalFit(ctx) {
                heroBox.x = 0.0
                heroBox.y = -height * 0.25
                val before = boxes.subList(0, heroIndex)
                val after = boxes.subList(heroIndex + 1, boxes.size)
                if (before.isNotEmpty()) {
                    val kickerHeight = before.maxOf { it.measuredHeight }
                    val kickerWidth = before.sumOf { it.measuredWidth } + flowGap * (before.size - 1)
                    val kickerY = heroBox.y - heroBox.measuredHeight / 2 - stackGap - kickerHeight / 2
                    var currentX = heroBox.x - kickerWidth / 2
                    before.forEach { box ->
                        box.x = currentX + box.measuredWidth / 2
                        box.y = kickerY
                        currentX += box.measuredWidth + flowGap
                        box.enterX = 0.0; box.enterY = -20.0
                    }
                }
                val leftAnchor = heroBox.x - heroBox.measuredWidth * 0.25 - flowGap
                val rightAnchor = heroBox.x + heroBox.measuredWidth * 0.25 + flowGap
                var currentY = heroBox.y + heroBox.measuredHeight / 2 + stackGap
                var pair = 0
                while (pair < after.size) {
                    val left = after[pair]
                    val right = after.getOrNull(pair + 1)
                    val rowHeight = max(left.measuredHeight, right?.measuredHeight ?: 0.0)
                    left.x = leftAnchor - left.measuredWidth / 2
                    left.y = currentY + left.measuredHeight / 2
                    left.enterX = -20.0; left.enterY = 0.0
                    if (right != null) {
                        right.x = rightAnchor + right.measuredWidth / 2
                        right.y = currentY + right.measuredHeight / 2
                        right.enterX = 20.0; right.enterY = 0.0
                    }
                    currentY += rowHeight + stackGap
                    pair += 2
                }
            }
        }
        3 -> {
            boxes.forEach { it.layoutVertical = false }
            placeWithGlobalFit(ctx) {
                heroBox.x = 0.0
                heroBox.y = 0.0
                val firstHero = min(heroIndex, secondaryHeroIndex)
                val line1 = boxes.subList(0, firstHero + 1)
                val line2 = boxes.subList(firstHero + 1, boxes.size)
                val line1Height = line1.maxOf { it.measuredHeight }
                // Math.max(...[]) = -Infinity：第二行为空时照 Folia 原样
                val line2Height = if (line2.isEmpty()) Double.NEGATIVE_INFINITY else line2.maxOf { it.measuredHeight }
                val totalHeight = line1Height + stackGap + line2Height
                val line1Y = heroBox.y - totalHeight / 2 + line1Height / 2
                val line2Y = line1Y + line1Height / 2 + stackGap + line2Height / 2
                fun layLine(line: List<SonnetBox>, lineY: Double, enterX: Double): Double {
                    val lineWidth = line.sumOf { it.measuredWidth } + flowGap * (line.size - 1)
                    var currentX = -lineWidth / 2
                    line.forEach { box ->
                        box.x = currentX + box.measuredWidth / 2
                        box.y = lineY
                        currentX += box.measuredWidth + flowGap
                        box.enterX = enterX; box.enterY = 0.0
                    }
                    return lineWidth
                }
                val w1 = layLine(line1, line1Y, 30.0)
                val w2 = layLine(line2, line2Y, -30.0)
                val offsetAmount = max(w1, w2) * 0.12
                line1.forEach { it.x -= offsetAmount }
                line2.forEach { it.x += offsetAmount }
            }
        }
        4 -> {
            boxes.forEachIndexed { i, box -> box.layoutVertical = i == heroIndex }
            placeWithGlobalFit(ctx) {
                val heroOnRight = heroIndex == boxes.size - 1
                val blockLeft = -width * 0.40
                val blockRight = width * 0.40
                var currentY = -height * 0.34
                fun flowWords(indices: List<Int>, regionFor: (Double) -> Pair<Double, Double>) {
                    var (left, right) = regionFor(currentY)
                    var currentX = left
                    var rowHeight = 0.0
                    indices.forEach { index ->
                        val box = boxes[index]
                        if (currentX > left && currentX + box.measuredWidth > right) {
                            currentY += rowHeight + stackGap
                            val r = regionFor(currentY)
                            left = r.first; right = r.second
                            currentX = left
                            rowHeight = 0.0
                        }
                        box.x = currentX + box.measuredWidth / 2
                        box.y = currentY + box.measuredHeight / 2
                        box.enterX = if (heroOnRight) -25.0 else 25.0
                        box.enterY = 0.0
                        currentX += box.measuredWidth + flowGap
                        rowHeight = max(rowHeight, box.measuredHeight)
                    }
                    if (indices.isNotEmpty()) currentY += rowHeight
                }
                // boxes.slice(...).map(box => box.index)：box.index 就是它在 boxes 里的下标（此时还没插入巨字）
                val beforeIndices = boxes.subList(0, heroIndex).map { it.index }
                val afterIndices = boxes.subList(heroIndex + 1, boxes.size).map { it.index }
                flowWords(beforeIndices) { blockLeft to blockRight }
                currentY += stackGap
                val pillarLeft = if (heroOnRight) blockRight - heroBox.measuredWidth else blockLeft
                heroBox.x = pillarLeft + heroBox.measuredWidth / 2
                heroBox.y = currentY + heroBox.measuredHeight / 2
                val pillarBottom = currentY + heroBox.measuredHeight + stackGap
                val besideLeft = if (heroOnRight) blockLeft else pillarLeft + heroBox.measuredWidth + flowGap
                val besideRight = if (heroOnRight) pillarLeft - flowGap else blockRight
                flowWords(afterIndices) { rowTop -> if (rowTop < pillarBottom - 0.5) besideLeft to besideRight else blockLeft to blockRight }
            }
        }
    }
}

private fun layoutFragmentCollage(ctx: FlowCtx, variant: Int) {
    val boxes = ctx.boxes
    val heroIndex = ctx.heroIndex
    val heroBox = boxes[heroIndex]
    class Rect(val left: Double, val right: Double, val top: Double, val bottom: Double)
    fun sep(a: Rect, b: Rect) = max(max(a.left - b.right, b.left - a.right), max(a.top - b.bottom, b.top - a.bottom))
    boxes.forEachIndexed { index, box ->
        if (index == heroIndex) return@forEachIndexed
        if (abs(Math.round(box.rotation / (PI / 2)) % 2) == 1L) {
            val rotatedWidth = box.measuredHeight
            box.measuredHeight = box.measuredWidth
            box.measuredWidth = rotatedWidth
        }
        box.rotation = 0.0
    }
    placeWithGlobalFit(ctx) { globalScale ->
        heroBox.x = 0.0
        heroBox.y = 0.0
        val baseRadius = hypot(heroBox.measuredWidth, heroBox.measuredHeight) / 2 + ctx.stackGap
        val count = max(1, boxes.size - 1)
        val squash = 0.65
        val placed = arrayListOf(Rect(heroBox.x - heroBox.measuredWidth / 2, heroBox.x + heroBox.measuredWidth / 2, heroBox.y - heroBox.measuredHeight / 2, heroBox.y + heroBox.measuredHeight / 2))
        var angle = PI / 4
        var supportIndex = 0
        for (i in boxes.indices) {
            if (i == heroIndex) continue
            val box = boxes[i]
            var radius = baseRadius
            radius += when (variant) {
                1 -> (35 + (supportIndex.toDouble() / count) * 150) * globalScale
                2 -> (if (supportIndex % 2 == 1) 140.0 else 50.0) * globalScale
                else -> (45 + ((supportIndex * 23) % 90)) * globalScale
            }
            supportIndex += 1
            var candidate = angle
            var rect = Rect(0.0, 0.0, 0.0, 0.0)
            var resolvedRadius = radius
            var placedClear = false
            var ring = 0
            while (ring < 14 && !placedClear) {
                var attempt = 0
                while (attempt < 400) {
                    rect = Rect(
                        cos(candidate) * resolvedRadius - box.measuredWidth / 2,
                        cos(candidate) * resolvedRadius + box.measuredWidth / 2,
                        sin(candidate) * resolvedRadius * squash - box.measuredHeight / 2,
                        sin(candidate) * resolvedRadius * squash + box.measuredHeight / 2,
                    )
                    if (placed.all { sep(it, rect) >= ctx.flowGap }) { placedClear = true; break }
                    candidate += 0.07
                    attempt++
                }
                if (!placedClear) resolvedRadius += (36 + ring * 12) * globalScale
                ring++
            }
            angle = candidate + 0.02
            placed += rect
            box.x = heroBox.x + cos(candidate) * resolvedRadius
            box.y = heroBox.y + sin(candidate) * resolvedRadius * squash
            box.layoutVertical = abs(cos(candidate)) >= abs(sin(candidate))
            box.enterX = cos(candidate) * -60
            box.enterY = sin(candidate) * -60
        }
    }
}

private fun layoutCrossStack(ctx: FlowCtx) {
    val boxes = ctx.boxes
    val heroIndex = ctx.heroIndex
    val heroBox = boxes[heroIndex]
    val stackGap = ctx.stackGap
    val flowGap = ctx.flowGap
    val topCount = heroIndex / 2
    val afterCount = boxes.size - 1 - heroIndex
    val rightCount = ceil(afterCount / 2.0).toInt()
    fun fillColumn(column: List<SonnetBox>): Double {
        if (column.isEmpty()) return 0.0
        val available = max(0.0, ctx.height * 0.46 - heroBox.measuredHeight / 2 - stackGap)
        if (available <= 0) return 0.0
        val gaps = stackGap * (column.size - 1)
        val contentHeight = column.sumOf { it.measuredHeight }
        val target = available * 0.72
        if (contentHeight + gaps < target) {
            val boost = min(2.2, (target - gaps) / max(1.0, contentHeight))
            column.forEach { box ->
                val capped = min(boost, (heroBox.fontScale * 0.6) / box.fontScale)
                if (capped > 1.05) {
                    box.fontScale *= capped
                    box.measuredWidth *= capped
                    box.measuredHeight *= capped
                }
            }
        }
        if (column.size < 2) return 0.0
        val grown = column.sumOf { it.measuredHeight }
        val pitch = (available * 0.95 - grown) / (column.size - 1)
        return max(0.0, min(stackGap * 2, pitch - stackGap))
    }
    placeWithGlobalFit(ctx) {
        heroBox.x = 0.0
        heroBox.y = 0.0
        val topStretch = fillColumn(boxes.subList(0, topCount))
        val bottomStretch = fillColumn(boxes.subList(min(boxes.size, heroIndex + rightCount + 1), boxes.size))
        var currentX = heroBox.x - heroBox.measuredWidth / 2 - stackGap
        for (i in heroIndex - 1 downTo topCount) {
            val box = boxes[i]
            box.layoutVertical = false
            box.x = currentX - box.measuredWidth / 2
            box.y = heroBox.y + (if (i % 2 == 0) 10 else -10)
            currentX -= box.measuredWidth + flowGap
            box.enterX = -30.0; box.enterY = 0.0
        }
        var currentY = heroBox.y - heroBox.measuredHeight / 2 - stackGap
        for (i in topCount - 1 downTo 0) {
            val box = boxes[i]
            box.layoutVertical = true
            box.x = heroBox.x + (if (i % 2 == 0) 15 else -15)
            box.y = currentY - box.measuredHeight / 2
            currentY -= box.measuredHeight + stackGap + topStretch
            box.enterX = 0.0; box.enterY = -30.0
        }
        currentX = heroBox.x + heroBox.measuredWidth / 2 + stackGap
        for (i in heroIndex + 1..heroIndex + rightCount) {
            val box = boxes[i]
            box.layoutVertical = false
            box.x = currentX + box.measuredWidth / 2
            box.y = heroBox.y + (if (i % 2 == 0) 10 else -10)
            currentX += box.measuredWidth + flowGap
            box.enterX = 30.0; box.enterY = 0.0
        }
        currentY = heroBox.y + heroBox.measuredHeight / 2 + stackGap
        for (i in heroIndex + rightCount + 1 until boxes.size) {
            val box = boxes[i]
            box.layoutVertical = true
            box.x = heroBox.x + (if (i % 2 == 0) 15 else -15)
            box.y = currentY + box.measuredHeight / 2
            currentY += box.measuredHeight + stackGap + bottomStretch
            box.enterX = 0.0; box.enterY = 30.0
        }
    }
}

// sonnetPosterBlocksLayout.ts
private class FlowRect(var u: Double, var v: Double, var uSize: Double, var vSize: Double)
private class FlowPlacement(val box: SonnetBox, val rect: FlowRect, var scale: Double, val vertical: Boolean)

private fun attemptPosterFlow(boxes: List<SonnetBox>, horizontal: Boolean, spaceU: Double, spaceV: Double, globalScale: Double, chipGap: Double, lineGap: Double, seed: Long): Pair<List<FlowPlacement>, Double> {
    // partitionFlowItems
    val items = ArrayList<Pair<SonnetBox?, List<SonnetBox>?>>()
    var group = ArrayList<SonnetBox>()
    boxes.forEach { box ->
        if (box.isHero || box.isSemiHero) {
            if (group.isNotEmpty()) items += null to group
            group = ArrayList()
            items += box to null
        } else group.add(box)
    }
    if (group.isNotEmpty()) items += null to group
    val placements = ArrayList<FlowPlacement>()
    val floats = ArrayList<DoubleArray>() // [extent, vBottom]
    var vCursor = 0.0
    var ownBandOnEndSide = ((seed shr 1) and 1L) == 1L
    class Dims(val useVertical: Boolean, val baseScale: Double, val width: Double, val height: Double)
    fun measure(box: SonnetBox): Dims {
        val useVertical = !horizontal && box.verticalMeasuredWidth != null && box.verticalMeasuredHeight != null && box.verticalFontScale != null
        return Dims(
            useVertical,
            if (useVertical) box.verticalFontScale!! else box.fontScale,
            (if (useVertical) box.verticalMeasuredWidth!! else box.measuredWidth) * globalScale,
            (if (useVertical) box.verticalMeasuredHeight!! else box.measuredHeight) * globalScale,
        )
    }
    fun toFlow(w: Double, h: Double) = if (horizontal) w to h else h to w
    fun pruneFloats() { floats.removeAll { it[1] <= vCursor } }
    items.forEachIndexed { itemIndex, item ->
        pruneFloats()
        val grp = item.second
        if (grp != null) {
            val reservedU = floats.sumOf { it[0] }
            val capacity = max(chipGap * 2, spaceU - reservedU)
            val uStart = reservedU
            class Chip(val box: SonnetBox, val dims: Dims, val uSize: Double, val vSize: Double, var shrink: Double = 1.0)
            val chips = grp.map { box -> val d = measure(box); val f = toFlow(d.width, d.height); Chip(box, d, f.first, f.second) }
            var line = ArrayList<Chip>()
            var lineUsedU = 0.0
            fun flushLine() {
                if (line.isEmpty()) return
                val lineV = line.maxOf { it.vSize * it.shrink }
                val leftover = capacity - lineUsedU
                val spread = if (line.size > 1 && leftover > 0) min(leftover / (line.size - 1), chipGap * 2.5) else 0.0
                var uCursor = uStart
                line.forEach { chip ->
                    placements += FlowPlacement(chip.box, FlowRect(uCursor, vCursor, chip.uSize * chip.shrink, chip.vSize * chip.shrink), chip.dims.baseScale * globalScale * chip.shrink, chip.dims.useVertical)
                    uCursor += chip.uSize * chip.shrink + chipGap + spread
                }
                vCursor += lineV + lineGap
                pruneFloats()
                line = ArrayList()
                lineUsedU = 0.0
            }
            chips.forEach { chip ->
                val needed = lineUsedU + (if (line.isNotEmpty()) chipGap else 0.0) + chip.uSize
                if (needed > capacity && line.isNotEmpty()) flushLine()
                if (chip.uSize > capacity) {
                    chip.shrink = max(0.5, capacity / chip.uSize)
                    lineUsedU = 0.0
                    line.add(chip)
                    flushLine()
                    return@forEach
                }
                lineUsedU += (if (line.isNotEmpty()) chipGap else 0.0) + chip.uSize
                line.add(chip)
            }
            flushLine()
            return@forEachIndexed
        }
        val zone = item.first!!
        vCursor = max(max(vCursor, floats.maxOfOrNull { it[1] } ?: Double.NEGATIVE_INFINITY), 0.0)
        floats.clear()
        val dims = measure(zone)
        val flow = toFlow(dims.width, dims.height)
        val followedByGroup = items.getOrNull(itemIndex + 1)?.second != null
        val zoneShrink = minOf(1.0, (spaceU * (if (followedByGroup) 0.62 else 0.9)) / flow.first, (spaceV * 0.66) / flow.second)
        val uSize = flow.first * zoneShrink
        val vSize = flow.second * zoneShrink
        val onlyZone = items.size == 1
        val u = when {
            onlyZone -> (spaceU - uSize) / 2
            followedByGroup -> 0.0
            ownBandOnEndSide -> spaceU - uSize
            else -> 0.0
        }
        placements += FlowPlacement(zone, FlowRect(u, vCursor, uSize, vSize), dims.baseScale * globalScale * zoneShrink, dims.useVertical)
        if (followedByGroup) floats += doubleArrayOf(uSize + chipGap, vCursor + vSize + lineGap)
        else {
            vCursor += vSize + lineGap
            ownBandOnEndSide = !ownBandOnEndSide
        }
    }
    val vTotal = placements.fold(0.0) { m, p -> max(m, p.rect.v + p.rect.vSize) }
    return placements to vTotal
}

private fun layoutSonnetPosterBlocks(boxes: List<SonnetBox>, width: Double, height: Double, baseFontSize: Double, seed: Long) {
    if (boxes.isEmpty()) return
    val gap = clampD(baseFontSize * 0.35, 16.0, 40.0)
    val chipGap = gap
    val lineGap = gap * 1.15
    val cx = -width * 0.42
    val cy = -height * 0.40
    val cw = width * 0.84
    val ch = height * 0.80
    val horizontal = seed % 2 == 0L
    val spaceU = if (horizontal) cw else ch
    val spaceV = if (horizontal) ch else cw
    var attempt = attemptPosterFlow(boxes, horizontal, spaceU, spaceV, 1.0, chipGap, lineGap, seed)
    for (globalScale in doubleArrayOf(0.92, 0.84, 0.76, 0.68, 0.6, 0.52)) {
        if (attempt.second <= spaceV + 0.5) break
        attempt = attemptPosterFlow(boxes, horizontal, spaceU, spaceV, globalScale, chipGap, lineGap, seed)
    }
    var vTotal = attempt.second
    if (vTotal > spaceV) {
        val fit = spaceV / vTotal
        attempt.first.forEach { p ->
            p.rect.u *= fit; p.rect.v *= fit; p.rect.uSize *= fit; p.rect.vSize *= fit; p.scale *= fit
        }
        vTotal = spaceV
    }
    val vShift = max(0.0, (spaceV - vTotal) / 2)
    attempt.first.forEach { placement ->
        val box = placement.box
        val r = placement.rect
        val v = r.v + vShift
        val sx: Double; val sy: Double; val sw: Double; val sh: Double
        if (horizontal) { sx = cx + r.u; sy = cy + v; sw = r.uSize; sh = r.vSize }
        else { sx = cx + cw - v - r.vSize; sy = cy + r.u; sw = r.vSize; sh = r.uSize }
        box.fontScale = placement.scale
        box.measuredWidth = sw
        box.measuredHeight = sh
        box.x = sx + sw / 2
        box.y = sy + sh / 2
        box.rotation = 0.0
        box.vertical = placement.vertical
        if (placement.vertical && box.verticalDisplayText != null) box.displayText = box.verticalDisplayText!!
        box.layoutVertical = !horizontal
        if (horizontal) {
            box.enterX = (if (sx + sw / 2 < 0) -1 else 1) * min(28.0, baseFontSize * 0.45)
            box.enterY = min(18.0, baseFontSize * 0.25)
        } else {
            box.enterX = min(18.0, baseFontSize * 0.25)
            box.enterY = (if (sy + sh / 2 < 0) -1 else 1) * min(28.0, baseFontSize * 0.45)
        }
    }
}

internal fun resolveSonnetTypographyLayout(
    lines: List<List<SonnetSegment>>,
    shotKind: SonnetShotKind,
    width: Double,
    height: Double,
    baseFontSize: Double,
    measure: SonnetMeasure,
): List<SonnetPlacement> {
    val segments = lines.flatten()
    if (segments.isEmpty()) return emptyList()
    var offset = 0
    val heroIndices = ArrayList<Int>()
    val semiHeroIndices = ArrayList<Int>()
    lines.forEach { lineSegs ->
        val localHero = findSonnetHeroIndex(lineSegs)
        heroIndices += offset + localHero
        findSonnetSemiHeroIndices(lineSegs, localHero).forEach { semiHeroIndices += offset + it }
        offset += lineSegs.size
    }
    val heroIndex = findSonnetHeroIndex(segments)
    val midpoints = segments.map { (it.startTime + it.endTime) / 2 }
    val tStart = midpoints.minOrNull()!!
    val tEnd = midpoints.maxOrNull()!!
    val tDur = tEnd - tStart
    val phases = midpoints.mapIndexed { i, m -> if (tDur > 0.001) (m - tStart) / tDur else i.toDouble() / max(1, segments.size - 1) }
    val layoutVariantSeed = segments.sumOf { s -> s.text.trim().length.let { if (it == 0) 1 else it } } + segments.size
    val posterLayoutSeed = sonnetHash(segments.joinToString("\u241f") { it.text })
    var editorialVariant = layoutVariantSeed % 5
    val ribbonVariant = layoutVariantSeed % 3
    val tableauVariant = layoutVariantSeed % 4
    val collageVariant = layoutVariantSeed % 3
    var secondaryHeroIndex = -1
    if (editorialVariant == 3 && segments.size > 2) {
        var best = Double.NEGATIVE_INFINITY
        segments.forEachIndexed { index, s ->
            if (index == heroIndex || !s.isWordLike || sonnetVisibleLength(s) == 0) return@forEachIndexed
            val score = sonnetHeroScore(s) + (if (abs(index - heroIndex) > 1) 50 else 0)
            if (score > best) { best = score; secondaryHeroIndex = index }
        }
        if (secondaryHeroIndex == -1) editorialVariant = 0
    } else if (editorialVariant == 3) editorialVariant = 0
    else if (editorialVariant == 4 && segments.size < 2) editorialVariant = 2

    val boxes = segments.mapIndexed { index, segment ->
        val isHero = index in heroIndices || (index == secondaryHeroIndex && shotKind == SonnetShotKind.EDITORIAL_COLUMN && editorialVariant == 3)
        val isSemiHero = index in semiHeroIndices && !isHero
        val isEmphasized = isHero || isSemiHero
        var heroFontScale = 1.0
        var supportFontScale = 1.0
        var vertical = false
        var rotation = 0.0
        when (shotKind) {
            SonnetShotKind.EDITORIAL_COLUMN -> when (editorialVariant) {
                3 -> { heroFontScale = 3.8; supportFontScale = 1.3; vertical = false }
                4 -> { heroFontScale = 4.2; supportFontScale = 1.25; vertical = isEmphasized }
                else -> { heroFontScale = if (editorialVariant == 2) 3.2 else 4.0; supportFontScale = 1.2; vertical = isEmphasized && editorialVariant != 2 }
            }
            SonnetShotKind.TYPE_IMPACT -> { heroFontScale = 5.5; supportFontScale = 1.5 }
            SonnetShotKind.FRAGMENT_COLLAGE -> { heroFontScale = 3.2; supportFontScale = 1.35; vertical = isSemiHero || index % 4 == 0 }
            SonnetShotKind.TRACKING_RIBBON -> { heroFontScale = 3.5; supportFontScale = 1.5 }
            SonnetShotKind.MASK_REVEAL -> { heroFontScale = 4.5; supportFontScale = 1.6; vertical = isEmphasized }
            SonnetShotKind.POSTER_BLOCKS -> { heroFontScale = 4.4; supportFontScale = 1.15 }
            SonnetShotKind.QUIET_TABLEAU -> { heroFontScale = 3.0; supportFontScale = 1.15; vertical = isEmphasized && (tableauVariant == 0 || tableauVariant == 1) }
        }
        var fontScale = if (isHero) heroFontScale else if (isSemiHero) max(supportFontScale * 1.35, heroFontScale * 0.72) else supportFontScale
        val chars = graphemeChars(segment)
        val rotatesNonCjk = vertical && segment.graphemes.count { it.char.isNotBlank() } > 1 && !CJK_TEXT.containsMatchIn(segment.text)
        if (rotatesNonCjk) { vertical = false; rotation += PI / 2 }
        val displayText = if (vertical) chars.joinToString("\n") else segment.text
        val renderRole = if (isHero) SonnetRole.HERO else if (isSemiHero) SonnetRole.SEMI_HERO else SonnetRole.SUPPORT
        val weight = sonnetRoleWeight(renderRole)
        var targetFontSize = baseFontSize * fontScale
        val horizontalAdvance = if (rotatesNonCjk) {
            segment.graphemes.sumOf { if (it.char.isNotBlank()) max(targetFontSize * 0.2, measure.width(it.char, weight, targetFontSize)) else 0.0 }
        } else measure.width(displayText.substringBefore('\n'), weight, targetFontSize)
        var measuredWidth = if (rotatesNonCjk) targetFontSize * 1.2 else horizontalAdvance
        var measuredHeight = if (rotatesNonCjk) horizontalAdvance else targetFontSize * 1.2
        if (vertical) {
            val adv = chars.filter { it.isNotBlank() }.map { max(targetFontSize * 0.2, measure.width(it, weight, targetFontSize)) }
            measuredWidth = adv.maxOrNull() ?: targetFontSize
            measuredHeight = max(1, chars.size) * targetFontSize * 0.9
        }
        val maxW = width * 0.82
        val maxH = height * 0.82
        var fitScale = 1.0
        if (measuredWidth > maxW) fitScale = min(fitScale, maxW / measuredWidth)
        if (measuredHeight > maxH) fitScale = min(fitScale, maxH / measuredHeight)
        if (fitScale < 1.0) {
            targetFontSize *= fitScale; fontScale *= fitScale; measuredWidth *= fitScale; measuredHeight *= fitScale
        }
        var pvText: String? = null
        var pvW: Double? = null
        var pvH: Double? = null
        var pvScale: Double? = null
        if (shotKind == SonnetShotKind.POSTER_BLOCKS && CJK_TEXT.containsMatchIn(segment.text)) {
            val adv = chars.filter { it.isNotBlank() }.map { max(targetFontSize * 0.2, measure.width(it, weight, targetFontSize)) }
            var columnWidth = adv.maxOrNull() ?: targetFontSize
            var columnHeight = max(1, chars.size) * targetFontSize * 0.9
            val verticalFit = minOf(1.0, maxW / columnWidth, maxH / columnHeight)
            columnWidth *= verticalFit
            columnHeight *= verticalFit
            pvText = chars.joinToString("\n"); pvW = columnWidth; pvH = columnHeight; pvScale = fontScale * verticalFit
        }
        SonnetBox(
            index = index, isHero = isHero, isSemiHero = isSemiHero, displayText = displayText,
            verticalDisplayText = pvText, verticalMeasuredWidth = pvW, verticalMeasuredHeight = pvH, verticalFontScale = pvScale,
            fontScale = fontScale, vertical = vertical, layoutVertical = false, rotation = rotation,
            measuredWidth = measuredWidth, measuredHeight = measuredHeight, timingPhase = phases[index], role = null,
        )
    }.toMutableList()

    val heroBox = boxes.getOrNull(heroIndex)
    if (heroBox != null) {
        if (shotKind == SonnetShotKind.POSTER_BLOCKS) layoutSonnetPosterBlocks(boxes, width, height, baseFontSize, posterLayoutSeed)
        else {
            val (flowGap, stackGap) = sonnetFlowGaps(baseFontSize)
            val ctx = FlowCtx(boxes, heroIndex, width, height, flowGap, stackGap)
            when (shotKind) {
                SonnetShotKind.QUIET_TABLEAU -> layoutQuietTableau(ctx, tableauVariant)
                SonnetShotKind.TRACKING_RIBBON -> layoutTrackingRibbon(ctx, ribbonVariant)
                SonnetShotKind.EDITORIAL_COLUMN -> layoutEditorialColumn(ctx, editorialVariant, secondaryHeroIndex)
                SonnetShotKind.FRAGMENT_COLLAGE -> layoutFragmentCollage(ctx, collageVariant)
                else -> layoutCrossStack(ctx)
            }
        }
        heroBox.enterX = 0.0
        heroBox.enterY = height * 0.15
        val decorations = ArrayList<SonnetBox>()
        if (shotKind != SonnetShotKind.QUIET_TABLEAU && shotKind != SonnetShotKind.POSTER_BLOCKS) {
            val allHeroes = boxes.filter { it.isHero }
            allHeroes.forEachIndexed { idx, h ->
                decorations += h.copyBox().apply {
                    isHero = false
                    role = SonnetRole.DECORATION
                    fontScale = max(2.8, min(h.fontScale * 3.5, 5.5))
                    vertical = false
                    x = h.x - width * (0.1 - idx * 0.03)
                    y = h.y - height * (0.05 - idx * 0.02)
                    rotation = -0.15 + (if (idx % 2 == 0) 0.0 else 0.05)
                    enterX = -width * 0.05
                    enterY = -height * 0.05
                }
            }
            if (boxes.size > 1 && allHeroes.isNotEmpty()) {
                val dec2 = if (boxes.last().isHero) boxes[0] else boxes.last()
                decorations += dec2.copyBox().apply {
                    isHero = false
                    role = SonnetRole.DECORATION
                    fontScale = max(1.8, min(allHeroes[0].fontScale * 2.2, 3.5))
                    vertical = false
                    x = allHeroes[0].x + width * 0.25
                    y = allHeroes[0].y + height * 0.15
                    rotation = 0.08
                    enterX = width * 0.05
                    enterY = height * 0.05
                }
            }
        }
        boxes.addAll(0, decorations)
    }
    return boxes.map { b ->
        SonnetPlacement(
            segmentIndex = b.index,
            displayText = b.displayText,
            role = b.role ?: if (b.isHero) SonnetRole.HERO else if (b.isSemiHero) SonnetRole.SEMI_HERO else SonnetRole.SUPPORT,
            fontScale = b.fontScale,
            measuredWidth = b.measuredWidth,
            measuredHeight = b.measuredHeight,
            x = b.x,
            y = b.y,
            rotation = b.rotation,
            enterX = b.enterX,
            enterY = b.enterY,
            vertical = b.vertical,
            layoutVertical = b.layoutVertical,
            timingPhase = b.timingPhase,
        )
    }
}

// ───────────────────────────── 逐字落点（sonnetGlyphLayout.ts） ─────────────────────────────

internal class SonnetGlyphPlacement(
    val char: String,
    val baseX: Double,
    val baseY: Double,
    val enterX: Double,
    val enterY: Double,
    val entryRotation: Double,
    val startTime: Double,
    val settleTime: Double,
)

internal fun sonnetGlyphMotionDuration(startTime: Double, endTime: Double): Double {
    val shotDuration = max(0.001, endTime - startTime)
    val preferred = min(1.8, max(0.65, shotDuration * 0.42))
    return min(preferred, shotDuration * 0.72)
}

internal fun buildSonnetGlyphLayout(
    segment: SonnetSegment,
    placement: SonnetPlacement,
    fontSize: Double,
    measureGlyph: (String) -> Double,
    shotStart: Double,
    shotEnd: Double,
): List<SonnetGlyphPlacement> {
    val graphemes = if (segment.graphemes.isNotEmpty()) segment.graphemes else {
        val chars = segment.text.codePoints().toArray().map { String(Character.toChars(it)) }
        chars.mapIndexed { i, c ->
            SonnetGrapheme(
                c,
                segment.startTime + (segment.endTime - segment.startTime) * i / max(1, chars.size),
                segment.startTime + (segment.endTime - segment.startTime) * (i + 1) / max(1, chars.size),
            )
        }
    }
    val advances = graphemes.map { if (placement.vertical) fontSize * 0.9 else max(fontSize * 0.2, measureGlyph(it.char)) }
    val total = advances.sum()
    val motionDuration = sonnetGlyphMotionDuration(shotStart, shotEnd)
    var cursor = -total / 2
    val c = cos(placement.rotation)
    val s = sin(placement.rotation)
    return graphemes.mapIndexed { index, g ->
        val advance = advances[index]
        val localX = if (placement.vertical) 0.0 else cursor + advance / 2
        val localY = if (placement.vertical) cursor + advance / 2 else 0.0
        cursor += advance
        val stagger = if (index % 2 == 0) -1 else 1
        SonnetGlyphPlacement(
            char = g.char,
            baseX = placement.x + localX * c - localY * s,
            baseY = placement.y + localX * s + localY * c,
            enterX = placement.enterX + (if (placement.vertical) stagger * fontSize * 0.28 else 0.0),
            enterY = placement.enterY + (if (placement.vertical) 0.0 else stagger * fontSize * 0.24),
            entryRotation = stagger * (if (placement.role.isEmphasis()) 0.055 else 0.035),
            startTime = g.startTime,
            settleTime = max(g.startTime, g.startTime + motionDuration),
        )
    }
}

/** resolveSonnetSegmentNormalOffset：support 词沿版式法线错开 ±0.3 字号。 */
internal fun sonnetNormalOffset(role: SonnetRole, layoutVertical: Boolean, rotation: Double, fontSize: Double, randomValue: Double): Pair<Double, Double> {
    if (role != SonnetRole.SUPPORT) return 0.0 to 0.0
    val distance = (clamp01(randomValue) * 2 - 1) * fontSize * 0.3
    val normalAngle = rotation + (if (layoutVertical) 0.0 else PI / 2)
    return cos(normalAngle) * distance to sin(normalAngle) * distance
}

// ───────────────────────────── 机位（sonnetMotion.ts） ─────────────────────────────

internal class SonnetMotionFrame(var x: Double, var y: Double, var scale: Double, var rotation: Double)

internal fun sonnetShotProgress(shot: SonnetShot, time: Double) = clamp01((time - shot.startTime) / max(shot.endTime - shot.startTime, 0.001))

internal fun sonnetShotPathProgress(kind: SonnetShotKind, progress: Double): Double {
    val linear = clamp01(progress)
    if (kind == SonnetShotKind.TRACKING_RIBBON || kind == SonnetShotKind.FRAGMENT_COLLAGE || kind == SonnetShotKind.QUIET_TABLEAU || kind == SonnetShotKind.POSTER_BLOCKS) {
        return linear * 0.55 + sonnetEaseInOut(linear) * 0.45
    }
    if (linear < 0.18) return sonnetEaseExpoOut(linear / 0.18) * 0.22
    if (linear < 0.78) return 0.22 + ((linear - 0.18) / 0.6) * 0.56
    val settle = (linear - 0.78) / 0.22
    return 0.78 + (1 - (1 - settle) * (1 - settle)) * 0.22
}

internal fun sonnetShotMotionFrame(kind: SonnetShotKind, progress: Double): SonnetMotionFrame {
    val linear = clamp01(progress)
    val e = sonnetShotPathProgress(kind, linear)
    return when (kind) {
        SonnetShotKind.EDITORIAL_COLUMN -> SonnetMotionFrame(-0.055 + e * 0.095, 0.025 - e * 0.04, 0.98 + e * 0.07, -0.006 + e * 0.01)
        SonnetShotKind.TYPE_IMPACT -> SonnetMotionFrame(-0.035 + e * 0.07, 0.018 - e * 0.028, 1 + (1 - sonnetEaseExpoOut(min(linear / 0.18, 1.0))) * 0.22 + e * 0.08, -0.01 + e * 0.016)
        SonnetShotKind.FRAGMENT_COLLAGE -> SonnetMotionFrame(-0.045 + e * 0.085, 0.028 - sin(e * PI) * 0.055, 0.97 + e * 0.09, -0.014 + e * 0.028)
        SonnetShotKind.TRACKING_RIBBON -> SonnetMotionFrame(-0.16 + e * 0.28, 0.05 - e * 0.085, 0.98 + e * 0.07, 0.008 - e * 0.014)
        SonnetShotKind.MASK_REVEAL -> SonnetMotionFrame(0.035 - e * 0.065, 0.1 - e * 0.135, 0.96 + e * 0.12, -0.006 + e * 0.009)
        SonnetShotKind.POSTER_BLOCKS -> SonnetMotionFrame(-0.012 + e * 0.024, 0.008 - e * 0.016, 0.99 + e * 0.025, -0.0015 + e * 0.003)
        SonnetShotKind.QUIET_TABLEAU -> SonnetMotionFrame(-0.022 + e * 0.04, 0.014 - e * 0.025, 1 + e * 0.028, -0.002 + e * 0.003)
    }
}

internal fun sonnetCameraBreath(time: Double, phase: Double): SonnetMotionFrame {
    val tau = time * PI * 2
    return SonnetMotionFrame(
        (sin(tau * 0.13 + phase) * 0.65 + sin(tau * 0.31 + phase * 1.7) * 0.35) * 0.006,
        (cos(tau * 0.11 + phase * 2.3) * 0.65 + sin(tau * 0.29 + phase * 0.9) * 0.35) * 0.006,
        sin(tau * 0.09 + phase * 1.3) * 0.002,
        sin(tau * 0.07 + phase * 2.9) * 0.0015,
    )
}

internal fun sonnetBreathWeight(time: Double, revealDoneTime: Double, rampDuration: Double = 1.2): Double =
    sonnetEaseInOut(clamp01((time - revealDoneTime) / rampDuration))

internal fun sonnetFocusWeights(ranges: List<Pair<Double, Double>>, time: Double, sigma: Double = 0.35): DoubleArray {
    if (ranges.isEmpty()) return DoubleArray(0)
    val s = max(0.001, sigma)
    val logs = ranges.map { (a, b) ->
        val st = min(a, b); val en = max(a, b)
        val d = if (time < st) st - time else if (time > en) time - en else 0.0
        -(d * d) / (2 * s * s)
    }
    val m = logs.maxOrNull()!!
    val w = logs.map { exp(it - m) }
    val total = w.sum()
    return DoubleArray(w.size) { w[it] / total }
}

internal fun sonnetSmoothedFocus(time: Double, startTime: Double, endTime: Double, sample: (Double) -> Pair<Double, Double>, window: Double = 0.12, maxBlend: Double = 96.0): Pair<Double, Double> {
    val a = min(startTime, endTime)
    val b = max(startTime, endTime)
    if (window <= 0 || a == b) return sample(min(b, max(a, time)))
    val offsets = doubleArrayOf(-1.0, -0.5, 0.0, 0.5, 1.0)
    val weights = doubleArrayOf(1.0, 4.0, 6.0, 4.0, 1.0)
    val pts = offsets.map { sample(min(b, max(a, time + it * window))) }
    val center = pts[2]
    var x = 0.0; var y = 0.0; var tw = 0.0
    pts.forEachIndexed { i, p ->
        val d2 = (p.first - center.first).pow(2) + (p.second - center.second).pow(2)
        if (d2 > maxBlend * maxBlend) return@forEachIndexed
        x += p.first * weights[i]; y += p.second * weights[i]; tw += weights[i]
    }
    return x / tw to y / tw
}

/** resolveSonnetSegmentCameraFocus（trackingFactor 0.5）。glyphs = (baseX, baseY, startTime)。 */
internal fun sonnetSegmentCameraFocus(glyphs: List<DoubleArray>, time: Double, factor: Double = 0.5): Pair<Double, Double> {
    if (glyphs.isEmpty()) return 0.0 to 0.0
    val first = glyphs.first()
    val last = glyphs.last()
    val cx = (first[0] + last[0]) / 2
    val cy = (first[1] + last[1]) / 2
    fun f(x: Double, y: Double) = cx + (x - cx) * factor to cy + (y - cy) * factor
    if (time <= first[2]) return f(first[0], first[1])
    if (time >= last[2]) return f(last[0], last[1])
    for (i in 0 until glyphs.size - 1) {
        val c = glyphs[i]; val n = glyphs[i + 1]
        if (time < c[2] || time > n[2]) continue
        val p = (time - c[2]) / max(0.001, n[2] - c[2])
        return f(c[0] + (n[0] - c[0]) * p, c[1] + (n[1] - c[1]) * p)
    }
    return f(first[0], first[1])
}

// ───────────────────────────── 转场（sonnetTransitions.ts + sonnetSceneFrame.ts） ─────────────────────────────

internal class SonnetTransitionFrame(val alpha: Double, val blur: Double, val glitch: Double, val glitchSeed: Double) {
    companion object { val IDLE = SonnetTransitionFrame(1.0, 0.0, 0.0, 0.0) }
}

private fun boundaryKind(seed: Long, boundaryIndex: Int): SonnetTransitionKind {
    val mixed = (seed.toInt() xor ((boundaryIndex + 1) * 0x9e3779b1L.toInt())).toLong() and 0xffffffffL
    return SONNET_TRANSITION_KINDS[(mixed % SONNET_TRANSITION_KINDS.size).toInt()]
}

/** resolveSonnetTransitionEffectFrame（x / y / scale / rotation 三种转场都是 0，只留 alpha / 模糊 / 撕裂）。 */
internal fun sonnetTransitionEffectFrame(kind: SonnetTransitionKind, exit: Boolean, progress: Double, seed: Double): SonnetTransitionFrame {
    val linear = clamp01(progress)
    val eased = sonnetEaseInOut(linear)
    val amount = if (exit) eased else 1 - eased
    return when (kind) {
        SonnetTransitionKind.FAST_BLUR -> SonnetTransitionFrame(if (exit) 1 - amount else 1 - amount * 0.82, amount * 14, 0.0, 0.0)
        SonnetTransitionKind.MONO_GLITCH -> {
            val step = floor(linear * 14)
            SonnetTransitionFrame(if (exit && linear > 0.86) 1 - (linear - 0.86) / 0.14 else 1.0, 0.0, amount, seed * 0.0001 + step * 0.173)
        }
        SonnetTransitionKind.CAMERA_PULL -> SonnetTransitionFrame(if (exit) 1 - amount else 1 - amount * 0.72, 0.0, 0.0, 0.0)
    }
}

internal fun sonnetSceneTransitionFrame(program: SonnetProgram, index: Int, shotIndex: Int, time: Double, enabled: Boolean): SonnetTransitionFrame {
    val paragraph = program.paragraphs[index]
    val seed = sonnetHash("${program.seed}:${paragraph.id}:transition-frame")
    val prev = if (index > 0) program.paragraphs[index - 1].transitionOut else null
    val enterDuration = if (prev != null) max(0.16, min(0.3, prev.endTime - prev.startTime)) else 0.0
    val entering = enabled && prev != null && time >= paragraph.startTime && time <= paragraph.startTime + enterDuration
    val paragraphFrame = if (entering) {
        sonnetTransitionEffectFrame(prev!!.kind, false, (time - paragraph.startTime) / max(enterDuration, 0.001), seed.toDouble())
    } else {
        val t = paragraph.transitionOut
        if (!enabled || t == null || time < t.startTime) SonnetTransitionFrame.IDLE
        else sonnetTransitionEffectFrame(t.kind, true, (time - t.startTime) / max(t.endTime - t.startTime, 0.001), seed.toDouble())
    }
    // resolveSonnetShotTransitionFrame
    val shots = paragraph.shots
    var shotFrame: SonnetTransitionFrame? = null
    if (enabled && shots.size >= 2) {
        val current = shots.getOrNull(shotIndex)
        if (current != null) {
            if (shotIndex > 0) {
                val previous = shots[shotIndex - 1]
                val duration = min(0.24, max(0.14, (current.startTime - previous.startTime) * 0.18))
                if (time <= current.startTime + duration) {
                    val since = time - current.startTime
                    shotFrame = if (since < 0 || since > duration) null
                    else sonnetTransitionEffectFrame(boundaryKind(seed, shotIndex - 1), false, since / max(duration, 0.001), (seed + shotIndex * 97).toDouble())
                }
            }
            if (shotFrame == null) {
                val next = shots.getOrNull(shotIndex + 1)
                if (next != null) {
                    val duration = min(0.24, max(0.14, (next.startTime - current.startTime) * 0.18))
                    val transitionStart = next.startTime - duration
                    if (time >= transitionStart) {
                        shotFrame = sonnetTransitionEffectFrame(boundaryKind(seed, shotIndex), true, (time - transitionStart) / duration, (seed + (shotIndex + 1) * 97).toDouble())
                    }
                }
            }
        }
    }
    return shotFrame ?: paragraphFrame
}

// ───────────────────────────── 引导线 / 开口框的时间（sonnetGuides.ts、sonnetFrameDecor.ts） ─────────────────────────────

internal fun sonnetGuideCue(segment: SonnetSegment, textStartTime: Double): Pair<Double, Double> {
    val lead = min(0.38, max(0.2, 0.18 + (segment.endTime - segment.startTime) * 0.1))
    return textStartTime - lead to textStartTime + 0.65
}

/** resolveSonnetFrameDecorSpec：约 40% 的词块套开口框，4 种角饰。返回 -1 = 不套。 */
internal fun sonnetFrameDecorVariant(segment: SonnetSegment): Int {
    val hash = sonnetHash("${segment.text}:${segment.startOffset}:${segment.endOffset}:frame-decor")
    val applied = (hash and 1023) / 1024.0 < 0.4
    return if (applied) ((hash ushr 10) % 4).toInt() else -1
}

/** 段落场景种子：hashSonnetSeed(`${programSeed}:${paragraph.id}`)。 */
internal fun sonnetSceneSeed(program: SonnetProgram, paragraph: SonnetParagraph) = sonnetHash("${program.seed}:${paragraph.id}")

/** 镜头基准字号（sonnetSceneBuilder.ts）。 */
internal fun sonnetShotFontSize(kind: SonnetShotKind, wordCount: Int, width: Double, lyricsFontScale: Double): Double {
    val heroScale = when (kind) { SonnetShotKind.TYPE_IMPACT -> 1.55; SonnetShotKind.QUIET_TABLEAU -> 0.82; else -> 1.0 }
    return max(24.0, min(112.0, (width / max(7.0, wordCount * 2.15)) * heroScale * lyricsFontScale))
}

internal fun sonnetRound(v: Double) = round(v)
