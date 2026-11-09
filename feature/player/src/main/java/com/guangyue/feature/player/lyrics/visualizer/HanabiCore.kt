// 花火（Folia hanabi）纯逻辑第一块：整首编译逐函数对译，不依赖 Android，可单测（金标 hanabi_golden.txt）。
// 对应 Folia 文件：src/components/visualizer/hanabi/
//   hanabiProgram.ts（歌词 → 段落 → 镜头：前奏 ≥3 秒单独一个间奏段，段落首尾相接铺满时间轴，段后长空隙收尾给间奏镜头，
//   长于 10 秒的间奏切成约 7 秒的几段）、program.ts（行切 1–3 段、镜头时间、选预设 castShot + chain）、
//   catalog.ts（50 个预设的 kind / family / mood，表由 node 从源码导出）、hanabiTransitions.ts（ascend / flash / bloom 选取与时长）、
//   hanabiScreenBloom.ts 的副歌时段。分段规则与绘光共用 paragraphStructure（LumiereCore.kt）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class HbProfileLite(val kind: String, val family: String, val mood: String)

/** catalog.ts 的 HANABI_PROFILES 顺序（castShot 依赖这个顺序）。 */
internal val HANABI_PROFILE_LITE: List<HbProfileLite> = listOf(
HbProfileLite("uchiage-kiku", "uchiage", "neutral"),
HbProfileLite("uchiage-botan", "uchiage", "loud"),
HbProfileLite("uchiage-palm", "uchiage", "neutral"),
HbProfileLite("uchiage-crossette", "uchiage", "neutral"),
HbProfileLite("uchiage-double", "uchiage", "neutral"),
HbProfileLite("kiku-center", "kiku", "loud"),
HbProfileLite("kiku-ring", "kiku", "loud"),
HbProfileLite("kiku-spokes", "kiku", "neutral"),
HbProfileLite("kiku-halo", "kiku", "neutral"),
HbProfileLite("kiku-marker", "kiku", "neutral"),
HbProfileLite("botan-band", "botan", "loud"),
HbProfileLite("botan-tile", "botan", "neutral"),
HbProfileLite("botan-double", "botan", "loud"),
HbProfileLite("botan-split", "botan", "loud"),
HbProfileLite("botan-circle", "botan", "neutral"),
HbProfileLite("yanagi-shidare", "yanagi", "quiet"),
HbProfileLite("yanagi-kamuro", "yanagi", "quiet"),
HbProfileLite("yanagi-ame", "yanagi", "quiet"),
HbProfileLite("yanagi-kaze", "yanagi", "quiet"),
HbProfileLite("yanagi-palm", "yanagi", "quiet"),
HbProfileLite("senrin-scatter", "senrin", "loud"),
HbProfileLite("senrin-stagger", "senrin", "neutral"),
HbProfileLite("senrin-strobe", "senrin", "neutral"),
HbProfileLite("senrin-hachi", "senrin", "loud"),
HbProfileLite("senrin-heart", "senrin", "neutral"),
HbProfileLite("hoshikuzu-star", "hoshikuzu", "quiet"),
HbProfileLite("hoshikuzu-orbit", "hoshikuzu", "quiet"),
HbProfileLite("hoshikuzu-dust", "hoshikuzu", "quiet"),
HbProfileLite("hoshikuzu-line", "hoshikuzu", "quiet"),
HbProfileLite("hoshikuzu-veil", "hoshikuzu", "quiet"),
HbProfileLite("obi-band", "obi", "neutral"),
HbProfileLite("obi-ticket", "obi", "neutral"),
HbProfileLite("obi-slant", "obi", "loud"),
HbProfileLite("obi-marker", "obi", "neutral"),
HbProfileLite("obi-stair", "obi", "neutral"),
HbProfileLite("senko-glitch", "senko", "loud"),
HbProfileLite("senko-scramble", "senko", "neutral"),
HbProfileLite("senko-strobe", "senko", "loud"),
HbProfileLite("senko-stretch", "senko", "loud"),
HbProfileLite("senko-type", "senko", "neutral"),
HbProfileLite("senkohanabi-hachi", "senkohanabi", "quiet"),
HbProfileLite("senkohanabi-tama", "senkohanabi", "quiet"),
HbProfileLite("senkohanabi-matsuba", "senkohanabi", "quiet"),
HbProfileLite("senkohanabi-chiri", "senkohanabi", "quiet"),
HbProfileLite("senkohanabi-tsubomi", "senkohanabi", "quiet"),
HbProfileLite("shakudama-kamuro", "shakudama", "loud"),
HbProfileLite("shakudama-nishiki", "shakudama", "loud"),
HbProfileLite("shakudama-asahi", "shakudama", "loud"),
HbProfileLite("shakudama-heart", "shakudama", "loud"),
HbProfileLite("shakudama-mine", "shakudama", "loud")
)
private val HB_BY_KIND = HANABI_PROFILE_LITE.associateBy { it.kind }
internal fun hanabiProfileLite(kind: String) = HB_BY_KIND[kind] ?: HANABI_PROFILE_LITE[0]

internal class HbSlice(val lineIndex: Int, val startOffset: Int, val endOffset: Int)

internal class HbShot(
    val id: String,
    val kind: String,
    val slices: List<HbSlice>,
    val startTime: Double,
    val endTime: Double,
    val lyricEndTime: Double,
    val isBridge: Boolean,
)

internal class HbTransition(val kind: String, val startTime: Double, val endTime: Double)

internal class HbParagraph(
    val id: String,
    val index: Int,
    val kind: String,
    val startTime: Double,
    val endTime: Double,
    val lyricEndTime: Double,
    val lineIndices: List<Int>,
    val shots: List<HbShot>,
    val transitionOut: HbTransition?,
)

internal class HbChorusSpan(val startTime: Double, var endTime: Double)

internal class HbProgram(
    val seed: String,
    val instrumental: Boolean,
    val duration: Double,
    val lyricEndTime: Double?,
    val paragraphs: List<HbParagraph>,
    val chorus: List<HbChorusSpan>,
)

internal class HbParams(val bridgeGap: Double = 3.0, val maxChars: Int = 18, val maxLineSeconds: Double = 6.0)

private const val HB_RECENT = 6
private val HB_BRIDGE_FAMILIES = listOf("senrin", "uchiage", "kiku", "shakudama")
private const val HB_BRIDGE_TAIL = 0.6
private const val HB_MAX_PIECES = 3
private val HB_MOODS_BY_KIND: Map<String, List<String>?> = mapOf(
    "chorus" to listOf("loud", "neutral"), "lift" to listOf("loud", "neutral"), "verse" to listOf("neutral", "quiet"),
    "breath" to listOf("quiet", "neutral"), "outro" to listOf("quiet", "neutral"), "break" to null,
)

private class HbSegment(val startOffset: Int, val endOffset: Int, val startTime: Double, val endTime: Double)

private class HbLocated(val text: String, val startTime: Double, val endTime: Double, val start: Int, val end: Int)

private fun hbLocateWords(line: Line): List<HbLocated>? {
    val text = line.fullText
    val out = ArrayList<HbLocated>()
    var cursor = 0
    for (w in line.words) {
        if (w.text.isEmpty()) continue
        val index = text.indexOf(w.text, cursor)
        if (index < 0) return null
        out += HbLocated(w.text, w.startTime, w.endTime, index, index + w.text.length)
        cursor = index + w.text.length
    }
    return out.ifEmpty { null }
}

private val HB_LETTERS = Regex("[\\p{L}\\p{N}]")

/** segmentHanabiLine：有逐字时间就用逐字时间的词，否则用分词（时间按文字偏移在整行里线性分配），空白与标点并进前一个词。 */
private fun hbSegmentLine(line: Line, split: SonnetWordSplitter): List<HbSegment> {
    val text = line.fullText
    val located = hbLocateWords(line)
    if (located != null && located.size >= 2) return located.map { HbSegment(it.start, it.end, it.startTime, it.endTime) }
    val merged = ArrayList<IntArray>()
    var offset = 0
    for (piece in split(line)) {
        val start = offset; val end = offset + piece.length; offset = end
        val last = merged.lastOrNull()
        if (last != null && !HB_LETTERS.containsMatchIn(piece)) last[1] = end else merged += intArrayOf(start, end)
    }
    val length = max(text.length, 1)
    fun timeAt(o: Int) = line.startTime + (line.endTime - line.startTime) * (o.toDouble() / length)
    return merged.map { HbSegment(it[0], it[1], timeAt(it[0]), timeAt(it[1])) }
}

private class HbPiece(val slice: HbSlice, val startTime: Double, val lyricEndTime: Double, val slot: String = "")

private fun hbVisibleLength(text: String) = GraphemeTimings.splitLyricGraphemes(text).count { it.trim().isNotEmpty() }

private fun hbSplitLine(u: LumiereStructureLine, params: HbParams, split: SonnetWordSplitter): List<HbPiece> {
    val line = u.line
    val text = line.fullText
    val whole = listOf(HbPiece(HbSlice(u.sourceIndex, 0, text.length), line.startTime, u.renderEndTime))
    val count = hbVisibleLength(text)
    val duration = line.endTime - line.startTime
    val maxChars = max(4, params.maxChars)
    val maxSeconds = max(1.0, params.maxLineSeconds)
    if (count <= maxChars && duration <= maxSeconds) return whole
    val segments = hbSegmentLine(line, split)
    val pieces = minOf(HB_MAX_PIECES, segments.size, max(2, ceil(max(count.toDouble() / maxChars, duration / maxSeconds)).toInt()))
    if (pieces < 2) return whole
    val cuts = ArrayList<Int>()
    var after = -1
    for (k in 1 until pieces) {
        val target = (text.length * k).toDouble() / pieces
        var best = -1
        for (j in after + 1..segments.size - 1 - (pieces - k)) {
            val off = segments[j + 1].startOffset
            if (best < 0 || abs(off - target) < abs(segments[best + 1].startOffset - target)) best = j
        }
        if (best < 0) break
        cuts += best
        after = best
    }
    if (cuts.isEmpty()) return whole
    val bounds = listOf(-1) + cuts + listOf(segments.size - 1)
    return (0 until bounds.size - 1).map { k ->
        val first = segments[bounds[k] + 1]
        val last = segments[bounds[k + 1]]
        val isFirst = k == 0
        val isLast = k == bounds.size - 2
        HbPiece(
            HbSlice(u.sourceIndex, if (isFirst) 0 else first.startOffset, if (isLast) text.length else segments[bounds[k + 1] + 1].startOffset),
            if (isFirst) line.startTime else first.startTime,
            if (isLast) u.renderEndTime else last.endTime,
        )
    }
}

private class HbPlanned(val slices: List<HbSlice>, val startTime: Double, val endTime: Double, val lyricEndTime: Double, val isBridge: Boolean)

private fun hbPlanShots(lines: List<LumiereStructureLine>, paragraphStart: Double, paragraphEnd: Double, params: HbParams, split: SonnetWordSplitter): List<HbPlanned> {
    val pieces = lines.flatMap { hbSplitLine(it, params, split) }
    val shots = ArrayList<HbPlanned>()
    var cursor = if (pieces.isNotEmpty()) min(paragraphStart, pieces[0].startTime) else paragraphStart
    pieces.forEachIndexed { index, piece ->
        val start = cursor
        val nextStart = max(pieces.getOrNull(index + 1)?.startTime ?: paragraphEnd, start + 0.05)
        val lyricEnd = piece.lyricEndTime
        if (index < pieces.size - 1 && nextStart - lyricEnd >= params.bridgeGap) {
            val end = max(start + 0.05, lyricEnd + HB_BRIDGE_TAIL)
            shots += HbPlanned(listOf(piece.slice), start, end, lyricEnd, false)
            shots += HbPlanned(emptyList(), end, nextStart, nextStart, true)
        } else {
            val end = if (index < pieces.size - 1) nextStart else max(nextStart, lyricEnd)
            shots += HbPlanned(listOf(piece.slice), start, end, min(lyricEnd, end), false)
        }
        cursor = shots.last().endTime
    }
    if (pieces.isEmpty() && paragraphEnd > paragraphStart) shots += HbPlanned(emptyList(), paragraphStart, paragraphEnd, paragraphEnd, true)
    return shots
}

private class HbChain(var recent: List<String> = emptyList(), var family: String? = null)

private fun hbCastShot(seed: String, paragraphIndex: Int, shotIndex: Int, kind: String, isBridge: Boolean, chain: HbChain): String {
    var candidates: List<String> = HANABI_PROFILE_LITE.map { it.kind }
    fun narrow(keep: (String) -> Boolean) { val next = candidates.filter(keep); if (next.isNotEmpty()) candidates = next }
    val moods = HB_MOODS_BY_KIND[kind]
    if (isBridge) narrow { hanabiProfileLite(it).family in HB_BRIDGE_FAMILIES }
    if (moods != null) narrow { hanabiProfileLite(it).mood in moods }
    narrow { it !in chain.recent }
    chain.family?.let { f -> narrow { hanabiProfileLite(it).family != f } }
    val random = SonnetRandom(sonnetHash("$seed:$paragraphIndex:$shotIndex:hanabi-cast"))
    return candidates[floor(random.next() * candidates.size).toInt()]
}

private fun hbAdvanceChain(chain: HbChain, kind: String) {
    chain.recent = (chain.recent + kind).takeLast(HB_RECENT)
    chain.family = hanabiProfileLite(kind).family
}

private const val HB_BRIDGE_SHOT = 7.0
private const val HB_BRIDGE_SPLIT = 10.0
private const val HB_INSTRUMENTAL_DURATION = 480.0
private const val HB_INSTRUMENTAL_PARAGRAPH = 28.0

private fun hbJsRound(v: Double) = floor(v + 0.5).toInt()

private fun hbCloseTail(shots: List<HbPlanned>, endTime: Double, params: HbParams): List<HbPlanned> {
    val last = shots.lastOrNull() ?: return shots
    if (last.isBridge || endTime - last.lyricEndTime < params.bridgeGap) return shots
    val end = max(last.startTime + 0.05, last.lyricEndTime + HB_BRIDGE_TAIL)
    return shots.dropLast(1) + HbPlanned(last.slices, last.startTime, end, last.lyricEndTime, false) + HbPlanned(emptyList(), end, endTime, endTime, true)
}

private fun hbSplitLongBridges(shots: List<HbPlanned>): List<HbPlanned> = shots.flatMap { shot ->
    val length = shot.endTime - shot.startTime
    if (!shot.isBridge || length <= HB_BRIDGE_SPLIT) return@flatMap listOf(shot)
    val count = max(2, hbJsRound(length / HB_BRIDGE_SHOT))
    List(count) { i ->
        val s = shot.startTime + (length * i) / count
        val e = if (i == count - 1) shot.endTime else shot.startTime + (length * (i + 1)) / count
        HbPlanned(emptyList(), s, e, e, true)
    }
}

internal val HANABI_TRANSITION_KINDS = listOf("ascend", "flash", "bloom")

internal fun chooseHanabiTransition(seed: String, paragraphIndex: Int, previous: String?): String {
    var hash = 0
    for (ch in "$seed:$paragraphIndex:hanabi-transition") hash = hash * 31 + ch.code
    val roll = abs(hash.toLong())
    if (previous != "ascend" && roll % 2 == 0L) return "ascend"
    val choices = HANABI_TRANSITION_KINDS.filter { it != previous && it != "ascend" }
    return choices[(roll.toInt() shr 1) % choices.size]
}

internal fun hanabiTransitionDuration(kind: String, gap: Double) =
    if (kind == "flash") min(0.5, max(0.28, if (gap > 0) gap * 0.4 else 0.35)) else min(1.1, max(0.5, if (gap > 0) gap * 0.6 else 0.8))

internal fun resolveHanabiChorusSpans(lines: List<Line>): List<HbChorusSpan> {
    val spans = ArrayList<HbChorusSpan>()
    lines.forEachIndexed { index, line ->
        if (!line.isChorus) return@forEachIndexed
        val next = lines.getOrNull(index + 1)
        val endTime = if (next != null) max(line.startTime, next.startTime) else RenderHints.getLineRenderEndTime(line)
        val last = spans.lastOrNull()
        if (last != null && line.startTime - last.endTime < 1.5) last.endTime = max(last.endTime, endTime)
        else spans += HbChorusSpan(line.startTime, endTime)
    }
    return spans
}

private class HbPlan(val lines: List<LumiereStructureLine>, val kind: String, val startTime: Double, val endTime: Double, val lyricEndTime: Double)

internal fun compileHanabiProgram(
    lines: List<Line>,
    seed: String = "hanabi",
    params: HbParams = HbParams(),
    duration: Double? = null,
    split: SonnetWordSplitter = defaultSonnetWordSplitter,
): HbProgram {
    val instrumental = lines.isEmpty()
    var lyricEnd = 0.0
    var programEnd = 0.0
    val plans = ArrayList<HbPlan>()
    if (!instrumental) {
        val structure = lumiereBuildStructureLines(lines)
        val drafts = lumiereDraftParagraphs(structure, lumiereParagraphGapThreshold(lines))
        lyricEnd = structure.maxOf { it.renderEndTime }
        programEnd = max(lyricEnd, duration ?: 0.0)
        val firstStart = structure[0].line.startTime
        val intro = firstStart >= params.bridgeGap
        if (intro) plans += HbPlan(emptyList(), "break", 0.0, firstStart, firstStart)
        drafts.forEachIndexed { index, draft ->
            val next = drafts.getOrNull(index + 1)
            plans += HbPlan(
                draft.lines,
                lumiereClassifyParagraph(draft.lines, index, drafts.size, split),
                if (index == 0 && !intro) min(0.0, firstStart) else draft.lines[0].line.startTime,
                next?.lines?.get(0)?.line?.startTime ?: programEnd,
                draft.lines.last().renderEndTime,
            )
        }
    } else {
        val total = max(1.0, if (duration != null && duration > 0) duration else HB_INSTRUMENTAL_DURATION)
        programEnd = total
        val count = max(1, hbJsRound(total / HB_INSTRUMENTAL_PARAGRAPH))
        repeat(count) { i ->
            val s = (total * i) / count
            val e = if (i == count - 1) total else (total * (i + 1)) / count
            plans += HbPlan(emptyList(), "break", s, e, e)
        }
    }
    val chain = HbChain()
    var previousTransition: String? = null
    val paragraphs = plans.mapIndexed { index, plan ->
        val planned = hbSplitLongBridges(hbCloseTail(hbPlanShots(plan.lines, plan.startTime, plan.endTime, params, split), plan.endTime, params))
        val shots = planned.mapIndexed { shotIndex, shot ->
            val kind = hbCastShot(seed, index, shotIndex, plan.kind, shot.isBridge, chain)
            hbAdvanceChain(chain, kind)
            HbShot("p$index-hb$shotIndex", kind, shot.slices, shot.startTime, shot.endTime, shot.lyricEndTime, shot.isBridge)
        }
        val next = plans.getOrNull(index + 1)
        var transitionOut: HbTransition? = null
        if (next != null) {
            val kind = chooseHanabiTransition(seed, index, previousTransition)
            previousTransition = kind
            val gap = next.startTime - plan.lyricEndTime
            transitionOut = HbTransition(kind, max(plan.startTime, plan.endTime - hanabiTransitionDuration(kind, gap)), plan.endTime)
        }
        HbParagraph(
            id = "hanabi-p$index", index = index, kind = plan.kind,
            startTime = plan.startTime, endTime = plan.endTime, lyricEndTime = plan.lyricEndTime,
            lineIndices = shots.flatMap { s -> s.slices.map { it.lineIndex } }.distinct().sorted(),
            shots = shots, transitionOut = transitionOut,
        )
    }
    return HbProgram(seed, instrumental, programEnd, if (instrumental) null else lyricEnd, paragraphs, resolveHanabiChorusSpans(lines))
}

internal fun findHanabiParagraphIndexAtTime(program: HbProgram, time: Double): Int {
    for (i in program.paragraphs.indices.reversed()) if (time >= program.paragraphs[i].startTime) return i
    return 0
}
