// 绘光（Folia lumiere）纯逻辑：编排层与光位，逐函数对译，不依赖 Android，可单测。
// 对应 Folia 文件：src/components/visualizer/lumiere/
//   lumiereKernel.ts（仿射 / 运镜变换）、lumiereRandom.ts + ../visualizerSeedHash.ts（FNV-1a + mulberry32，含 O(1) 跳步）、
//   ../paragraphStructure.ts（分段与段落性质，recursiveSplit）、program.ts（切块、选光位、chain）、
//   lumiereProgram.ts（整首编译、前奏段、纯音乐、段落转场与星空开场）、lumiereSeamless.ts（轨迹过渡并段）、
//   lumiereTransitions.ts（熄灯 / 闪白 / 拉焦）、lumiereUnit.ts（段落 → 场景镜头、shotAt）、
//   lumiereUnitLayout.ts（领头光位、每行排版、运镜）、catalog.ts + rigs/*.ts（10 族 × 10 种光位 profile）、
//   light/rig.ts（光束声明、resolveBeams、beamMask、噪声，与着色器同一公式）、color.ts、scene.ts 的 resolveLumierePalette /
//   blendRig / 每帧的光位交接、lumiereDarkField.ts 的 resolveLumiereDarkField。
// 单位：Folia 的 CSS 像素在这里就是 dp（舞台按 density 整体缩放），所以常数原样照搬。
//
// 偏离：
//   1. rigs/*.ts 的 light(context) 是 100 个函数。除了用到随机数的两个（optics-raytrace、prism-shard，这里手译），
//      其余只依赖宽高比：由 Folia 源码在 node 里对 5 个宽高比（0.45 / 0.5625 / 1 / 1.7778 / 2.2222）求值，
//      存成 resources/lumiere_rigs.txt，运行时按宽高比在相邻两份之间逐项线性插值（720×1600 与 1600×720 正好命中采样点，逐值一致）。
//   2. lineArt(context) 是第 2 阶段：这里只留接口（LumiereLineArtSpec 为空）。
//   3. Folia 文件头里那些 LUMIERE_NEUTRAL_OFFSET（x − x 恒为 0）直接省掉。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan

// ───────────────────────────── 随机（lumiereRandom.ts / visualizerSeedHash.ts） ─────────────────────────────

/** hashVisualizerSeed：FNV-1a 32 位（按 UTF-16 码元，同 JS charCodeAt）。返回 32 位整数位型。 */
internal fun lumiereHash(value: String): Int {
    var hash = 2166136261L.toInt()
    for (ch in value) {
        hash = hash xor ch.code
        hash *= 16777619
    }
    return hash
}

/** createVisualizerRng（mulberry32）。 */
internal class LumiereRng(seedState: Int) {
    private var state = seedState
    fun next(): Double {
        state += 0x6d2b79f5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
}

internal fun lumiereRng(seed: String) = LumiereRng(lumiereHash(seed))

/** createVisualizerRngAt：同一条流跳过 skip 个值（每取一次状态加一个常数，跳步 O(1)）。 */
internal fun lumiereRngAt(seed: String, skip: Int) = LumiereRng(lumiereHash(seed) + skip * 0x6d2b79f5)

internal fun lumiereSmooth(value: Double): Double {
    val t = min(1.0, max(0.0, value))
    return t * t * (3 - 2 * t)
}

internal fun lumiereEaseInOutCubic(value: Double): Double {
    val t = min(1.0, max(0.0, value))
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

internal fun lumiereEaseInOutSine(value: Double) = (1 - cos(PI * min(1.0, max(0.0, value)))) / 2
internal fun lumiereClamp01(value: Double) = min(1.0, max(0.0, value))
internal fun lumiereLerp(a: Double, b: Double, k: Double) = a + (b - a) * k

// ───────────────────────────── 仿射（lumiereKernel.ts） ─────────────────────────────

internal class LumiereAffine(val a: Double, val b: Double, val c: Double, val d: Double, val tx: Double, val ty: Double)

/** TransformParams：先减 pivot，再缩放、旋转，最后移到 position。 */
internal class LumiereTransform(val x: Double, val y: Double, val pivotX: Double, val pivotY: Double, val scale: Double, val rotation: Double)

internal fun lumiereFromParams(p: LumiereTransform): LumiereAffine {
    val cs = cos(p.rotation) * p.scale
    val sn = sin(p.rotation) * p.scale
    return LumiereAffine(cs, sn, -sn, cs, p.x - (cs * p.pivotX - sn * p.pivotY), p.y - (sn * p.pivotX + cs * p.pivotY))
}

internal fun lumiereMultiply(m: LumiereAffine, n: LumiereAffine) = LumiereAffine(
    m.a * n.a + m.c * n.b, m.b * n.a + m.d * n.b, m.a * n.c + m.c * n.d, m.b * n.c + m.d * n.d,
    m.a * n.tx + m.c * n.ty + m.tx, m.b * n.tx + m.d * n.ty + m.ty,
)

internal fun lumiereInvert(m: LumiereAffine): LumiereAffine {
    val det = (m.a * m.d - m.b * m.c).let { if (it == 0.0) 1e-12 else it }
    return LumiereAffine(m.d / det, -m.b / det, -m.c / det, m.a / det, (m.c * m.ty - m.d * m.tx) / det, (m.b * m.tx - m.a * m.ty) / det)
}

/** cameraBetween：没有运镜时的画面坐标 → 有运镜时的画面坐标。 */
internal fun lumiereCameraBetween(rest: LumiereTransform, current: LumiereTransform) = lumiereMultiply(lumiereFromParams(current), lumiereInvert(lumiereFromParams(rest)))

// ───────────────────────────── 分段（paragraphStructure.ts） ─────────────────────────────

internal class LumiereStructureLine(val sourceIndex: Int, val line: Line, val renderEndTime: Double)

/** 分词：一句的片段（拼起来等于原文）。缺省用商籁同一个（BreakIterator，Android 上由 ICU 实现，与 Intl.Segmenter 同源）。 */
internal typealias LumiereWordSplitter = (Line) -> List<String>

private val LUMIERE_NOT_WORD = Regex("^[\\s\\p{P}\\p{S}]+$")

/** Intl.Segmenter 的 isWordLike：标点与空白不算词。 */
internal fun lumiereIsWordLike(segment: String) = !LUMIERE_NOT_WORD.matches(segment)

internal fun lumiereBuildStructureLines(lines: List<Line>): List<LumiereStructureLine> = lines.mapIndexed { i, line ->
    val next = lines.getOrNull(i + 1)?.startTime ?: Double.POSITIVE_INFINITY
    LumiereStructureLine(i, line, max(line.startTime, min(RenderHints.getLineRenderEndTime(line), next)))
}

private fun lumiereMedian(values: List<Double>): Double {
    if (values.isEmpty()) return 0.5
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
}

internal fun lumiereParagraphGapThreshold(lines: List<Line>): Double {
    val gaps = (1 until lines.size).map { i -> lines[i].startTime - min(RenderHints.getLineRenderEndTime(lines[i - 1]), lines[i].startTime) }.filter { it > 0 }
    return min(3.5, max(1.25, lumiereMedian(gaps) * 2.5))
}

internal class LumiereDraft(val lines: List<LumiereStructureLine>, val boundary: String)

private fun lumiereMetadataChanged(previous: Line, next: Line) =
    (previous.blockIndex != null && next.blockIndex != null && previous.blockIndex != next.blockIndex) ||
        (previous.songPart != null && next.songPart != null && previous.songPart != next.songPart)

/** splitOversizedDraft（maxLines 6、maxDuration 18、recursiveSplit）。 */
internal fun lumiereSplitOversizedDraft(draft: LumiereDraft): List<LumiereDraft> {
    val output = ArrayList<LumiereDraft>()
    var remaining = draft.lines
    var boundary = draft.boundary
    var guard = 0
    while (remaining.size > 6 || (remaining.size > 1 && (remaining.last().renderEndTime - remaining[0].line.startTime) > 18)) {
        if (guard++ > 1000) break
        val candidates = ArrayList<Pair<Int, Double>>()
        for (k in 2 until remaining.size - 1) candidates += k to (remaining[k].line.startTime - remaining[k - 1].renderEndTime)
        // Array.prototype.sort 是稳定排序：同样的空隙保留原顺序（sortedByDescending 也稳定）。
        val raw = candidates.filter { !it.second.isNaN() }.sortedByDescending { it.second }.firstOrNull()?.first ?: min(4, remaining.size - 1)
        val splitIndex = max(1, raw)
        output += lumiereSplitOversizedDraft(LumiereDraft(remaining.subList(0, splitIndex), boundary))
        remaining = remaining.subList(splitIndex, remaining.size)
        boundary = if (output.last().lines.size >= 6) "line-cap" else "duration-cap"
    }
    output += LumiereDraft(remaining, boundary)
    return output
}

internal fun lumiereDraftParagraphs(lines: List<LumiereStructureLine>, threshold: Double): List<LumiereDraft> {
    val drafts = ArrayList<LumiereDraft>()
    var current = ArrayList<LumiereStructureLine>()
    var currentBoundary = "song-start"
    lines.forEachIndexed { index, line ->
        val previous = lines.getOrNull(index - 1)
        val gap = if (previous != null) line.line.startTime - previous.renderEndTime else 0.0
        val boundary = when {
            previous != null && lumiereMetadataChanged(previous.line, line.line) -> "metadata"
            previous != null && gap >= threshold -> "time-gap"
            else -> null
        }
        if (boundary != null && current.isNotEmpty()) {
            drafts += lumiereSplitOversizedDraft(LumiereDraft(current, currentBoundary))
            current = ArrayList()
            currentBoundary = boundary
        }
        current += line
    }
    if (current.isNotEmpty()) drafts += lumiereSplitOversizedDraft(LumiereDraft(current, currentBoundary))
    return drafts
}

private val LUMIERE_CHORUS_RE = Regex("chorus|副歌", RegexOption.IGNORE_CASE)
private val LUMIERE_BREAK_RE = Regex("bridge|break|間奏|ブリッジ", RegexOption.IGNORE_CASE)
private val LUMIERE_EXCLAIM_RE = Regex("[!?！？…]")

internal fun lumiereClassifyParagraph(lines: List<LumiereStructureLine>, index: Int, total: Int, split: LumiereWordSplitter): String {
    if (lines.any { it.line.isChorus || LUMIERE_CHORUS_RE.containsMatchIn(it.line.songPart ?: "") }) return "chorus"
    if (lines.any { LUMIERE_BREAK_RE.containsMatchIn(it.line.songPart ?: "") }) return "break"
    if (index == total - 1) return "outro"
    val duration = lines.last().renderEndTime - lines[0].line.startTime
    val segmentCount = lines.sumOf { l -> split(l.line).count { lumiereIsWordLike(it) } }
    val punctuationCount = lines.sumOf { LUMIERE_EXCLAIM_RE.findAll(it.line.fullText).count() }
    if (duration <= 3.5 || segmentCount <= 3) return "breath"
    if (punctuationCount >= 2 || segmentCount / max(duration, 1.0) > 2.5) return "lift"
    return "verse"
}

// ───────────────────────────── 切块与选光位（program.ts） ─────────────────────────────

internal class LumiereShot(
    val id: String, val kind: String, val lineIndices: List<Int>,
    val startTime: Double, val endTime: Double, val lyricEndTime: Double, val isBridge: Boolean,
)

internal class LumiereSection(val startTime: Double, val endTime: Double, val kind: String)

internal class LumiereTransitionOut(val kind: String, val startTime: Double, val endTime: Double)

internal class LumiereParagraph(
    val id: String, val index: Int, val kind: String, val boundary: String,
    val startTime: Double, val endTime: Double, val lyricEndTime: Double,
    val lineIndices: List<Int>, val lines: List<Line>, val shots: List<LumiereShot>,
    val transitionOut: LumiereTransitionOut?, val opening: Boolean, val sections: List<LumiereSection>? = null,
)

internal class LumiereProgram(
    val seed: String, val instrumental: Boolean, val paragraphGapThreshold: Double,
    val duration: Double, val lyricEndTime: Double?, val paragraphs: List<LumiereParagraph>,
)

internal class LumiereParams(val maxShotDuration: Double = 7.0, val maxLinesPerShot: Int = 2, val longLine: Double = 3.6, val bridgeGap: Double = 4.0)

internal class LumiereChain(var recent: List<String> = emptyList(), var family: String? = null)

private const val LUMIERE_RECENT = 3
private val LUMIERE_BRIDGE_FAMILIES = listOf("motes", "astral", "zenith")

private fun lumiereMoodsByKind(kind: String): List<String>? = when (kind) {
    "chorus", "lift" -> listOf("loud", "neutral")
    "verse", "breath" -> listOf("quiet", "neutral")
    "outro" -> listOf("quiet")
    else -> null
}

internal fun lumiereMoodsForEnergy(energy: Double): List<String>? =
    if (energy < 0.33) listOf("quiet", "neutral") else if (energy < 0.66) null else listOf("neutral", "loud")

/** groupLines：短行两两合成一个镜头（不超过时长上限），长行单独成镜头。 */
internal fun lumiereGroupLines(lines: List<LumiereStructureLine>, params: LumiereParams): List<MutableList<LumiereStructureLine>> {
    val groups = ArrayList<MutableList<LumiereStructureLine>>()
    for (line in lines) {
        val duration = line.renderEndTime - line.line.startTime
        val last = groups.lastOrNull()
        val lastStart = last?.firstOrNull()?.line?.startTime ?: 0.0
        val lastLong = if (last != null) last.size == 1 && (last[0].renderEndTime - lastStart) >= params.longLine else true
        if (last != null && !lastLong && duration < params.longLine && last.size < params.maxLinesPerShot && line.renderEndTime - lastStart <= params.maxShotDuration) {
            last += line
        } else groups += mutableListOf(line)
    }
    return groups
}

internal class LumierePlannedShot(val lines: List<LumiereStructureLine>, val startTime: Double, val endTime: Double, val lyricEndTime: Double, val isBridge: Boolean)

internal fun lumierePlanShots(lines: List<LumiereStructureLine>, paragraphStart: Double, paragraphEnd: Double, params: LumiereParams): List<LumierePlannedShot> {
    val groups = lumiereGroupLines(lines, params)
    val shots = ArrayList<LumierePlannedShot>()
    groups.forEachIndexed { index, group ->
        val start = if (index == 0) min(paragraphStart, group[0].line.startTime) else group[0].line.startTime
        val lyricEnd = group.maxOf { it.renderEndTime }
        val nextStart = groups.getOrNull(index + 1)?.firstOrNull()?.line?.startTime ?: paragraphEnd
        if (nextStart - lyricEnd >= params.bridgeGap) {
            val end = lyricEnd + 0.6
            shots += LumierePlannedShot(group, start, end, lyricEnd, false)
            shots += LumierePlannedShot(emptyList(), end, nextStart, nextStart, true)
        } else {
            shots += LumierePlannedShot(group, start, max(nextStart, lyricEnd), min(lyricEnd, nextStart), false)
        }
    }
    if (groups.isEmpty() && paragraphEnd > paragraphStart) shots += LumierePlannedShot(emptyList(), paragraphStart, paragraphEnd, paragraphEnd, true)
    return shots
}

/** castShot：全部光位 → 间奏限定族 → mood → 避开最近用过的与上一个的族，每一步筛空了就退回上一步。 */
internal fun lumiereCastShot(seed: String, paragraphIndex: Int, shotIndex: Int, kind: String, isBridge: Boolean, chain: LumiereChain, energy: Double? = null): String {
    var candidates: List<String> = LumiereCatalog.kinds
    fun narrow(atLeast: Int = 1, keep: (String) -> Boolean) {
        val next = candidates.filter(keep)
        if (next.size >= atLeast) candidates = next
    }
    val moods = if (energy == null) lumiereMoodsByKind(kind) else lumiereMoodsForEnergy(energy)
    val profile = LumiereCatalog::profileOf
    if (isBridge) narrow { profile(it).family in LUMIERE_BRIDGE_FAMILIES && profile(it).mood != "loud" }
    if (moods != null) narrow { profile(it).mood in moods }
    narrow { it !in chain.recent }
    chain.family?.let { fam -> narrow(3) { profile(it).family != fam } }
    val random = lumiereRng("$seed:$paragraphIndex:$shotIndex:cast")
    return candidates[floor(random.next() * candidates.size).toInt()]
}

internal fun lumiereAdvanceChain(chain: LumiereChain, kind: String) {
    chain.recent = (chain.recent + kind).takeLast(LUMIERE_RECENT)
    chain.family = LumiereCatalog.profileOf(kind).family
}

// ───────────────────────────── 段落转场（lumiereTransitions.ts） ─────────────────────────────

internal val LUMIERE_TRANSITION_KINDS = listOf("lights-out", "flare-cut", "focus-pull")

internal class LumiereTransitionFrame(val alpha: Double, val scale: Double, val blur: Double)

internal fun lumiereTransitionDuration(kind: String, gap: Double): Double = if (kind == "focus-pull")
    min(0.6, max(0.35, if (gap > 0) gap * 0.5 else 0.45)) else min(1.1, max(0.5, if (gap > 0) gap * 0.6 else 0.8))

internal fun lumiereTransitionFrame(kind: String, exit: Boolean, progress: Double): LumiereTransitionFrame {
    val near = if (exit) lumiereSmooth(progress) else 1 - lumiereSmooth(progress)
    return when (kind) {
        "lights-out" -> LumiereTransitionFrame(1 - near, 1.0, 0.0)
        "flare-cut" -> LumiereTransitionFrame(1 - 0.35 * near * near, 1 + 0.03 * near, 7 * near * near)
        else -> LumiereTransitionFrame(1.0, 1 + 0.015 * near, 12 * near)
    }
}

internal fun lumiereEnterDuration(kind: String, startTime: Double, endTime: Double): Double {
    val (lo, hi) = if (kind == "focus-pull") 0.3 to 0.6 else 0.4 to 0.9
    return max(lo, min(hi, endTime - startTime))
}

/** chooseLumiereTransition：不与上一次相同，按 (seed, 段落序号) 的 31 进制散列确定。 */
internal fun lumiereChooseTransition(seed: String, paragraphIndex: Int, previous: String?): String {
    val choices = LUMIERE_TRANSITION_KINDS.filter { it != previous }
    var hash = 0
    for (ch in "$seed:$paragraphIndex:transition") hash = hash * 31 + ch.code
    return choices[(abs(hash.toLong()) % choices.size).toInt()]
}

// ───────────────────────────── 整首编译（lumiereProgram.ts + lumiereSeamless.ts） ─────────────────────────────

internal const val LUMIERE_REOPEN_GAP = 2.5
private const val LUMIERE_INSTRUMENTAL_DURATION = 480.0
private const val LUMIERE_INSTRUMENTAL_PARAGRAPH = 32.0
private const val LUMIERE_BRIDGE_SHOT = 10.0
private const val LUMIERE_BRIDGE_SPLIT = 16.0

private class LumiereParagraphPlan(val lines: List<LumiereStructureLine>, val kind: String, val boundary: String, val startTime: Double, val endTime: Double, val lyricEndTime: Double)

private fun lumiereSplitLongBridges(shots: List<LumierePlannedShot>): List<LumierePlannedShot> = shots.flatMap { shot ->
    val length = shot.endTime - shot.startTime
    if (!shot.isBridge || length <= LUMIERE_BRIDGE_SPLIT) listOf(shot) else {
        val count = max(2, round(length / LUMIERE_BRIDGE_SHOT).toInt())
        (0 until count).map { i ->
            val s = shot.startTime + (length * i) / count
            val e = if (i == count - 1) shot.endTime else shot.startTime + (length * (i + 1)) / count
            LumierePlannedShot(emptyList(), s, e, e, true)
        }
    }
}

/** mergeLumiereParagraphs：轨迹过渡把首尾相接的段落并成一个单元（镜头原样保留，原段落范围留在 sections）。 */
internal fun lumiereMergeParagraphs(paragraphs: List<LumiereParagraph>): List<LumiereParagraph> {
    if (paragraphs.size < 2) return paragraphs
    val first = paragraphs.first()
    val last = paragraphs.last()
    return listOf(
        LumiereParagraph(
            id = "lumiere-seamless", index = 0, kind = first.kind, boundary = first.boundary,
            startTime = first.startTime, endTime = last.endTime, lyricEndTime = last.lyricEndTime,
            lineIndices = paragraphs.flatMap { it.lineIndices }, lines = paragraphs.flatMap { it.lines },
            shots = paragraphs.flatMap { it.shots }, transitionOut = null, opening = first.opening,
            sections = paragraphs.map { LumiereSection(it.startTime, it.endTime, it.kind) },
        ),
    )
}

/**
 * compileLumiereProgram。seamless 对应 Folia 的 tuning.seamlessTransitions：Folia v0.7.25 缺省为 true，
 * 但基准截图（dev-probe 的 visualizerMemory）是按段落切单元的（optics-raytrace 的光路坐标只与 `probe-0:lumiere-p6` 的种子吻合），
 * 所以舞台缺省传 false，见 LumiereStage。
 */
internal fun compileLumiereProgram(
    lines: List<Line>,
    seed: String = "lumiere",
    params: LumiereParams = LumiereParams(),
    duration: Double? = null,
    seamless: Boolean = false,
    split: LumiereWordSplitter = defaultSonnetWordSplitter,
): LumiereProgram {
    val instrumental = lines.isEmpty()
    var gapThreshold = 0.0
    var lyricEnd: Double? = null
    var programEnd = 0.0
    val plans = ArrayList<LumiereParagraphPlan>()
    val instrumentalDuration = max(1.0, if (duration != null && duration > 0) duration else LUMIERE_INSTRUMENTAL_DURATION)
    if (!instrumental) {
        val structure = lumiereBuildStructureLines(lines)
        gapThreshold = lumiereParagraphGapThreshold(lines)
        val drafts = lumiereDraftParagraphs(structure, gapThreshold)
        lyricEnd = structure.maxOf { it.renderEndTime }
        programEnd = max(lyricEnd, duration ?: 0.0)
        val firstStart = structure[0].line.startTime
        val intro = firstStart >= params.bridgeGap
        if (intro) plans += LumiereParagraphPlan(emptyList(), "break", "intro", 0.0, firstStart, firstStart)
        drafts.forEachIndexed { index, draft ->
            val next = drafts.getOrNull(index + 1)
            plans += LumiereParagraphPlan(
                draft.lines, lumiereClassifyParagraph(draft.lines, index, drafts.size, split), draft.boundary,
                if (index == 0 && !intro) min(0.0, firstStart) else draft.lines[0].line.startTime,
                next?.lines?.get(0)?.line?.startTime ?: programEnd,
                draft.lines.last().renderEndTime,
            )
        }
    } else {
        val count = max(1, round(instrumentalDuration / LUMIERE_INSTRUMENTAL_PARAGRAPH).toInt())
        for (i in 0 until count) {
            val s = (instrumentalDuration * i) / count
            val e = if (i == count - 1) instrumentalDuration else (instrumentalDuration * (i + 1)) / count
            plans += LumiereParagraphPlan(emptyList(), "break", if (i == 0) "song-start" else "instrumental", s, e, e)
        }
    }
    val chain = LumiereChain()
    var previousTransition: String? = null
    val paragraphs = plans.mapIndexed { index, plan ->
        val planned = lumiereSplitLongBridges(lumierePlanShots(plan.lines, plan.startTime, plan.endTime, params))
        val shots = planned.mapIndexed { shotIndex, shot ->
            val kind = lumiereCastShot(seed, index, shotIndex, plan.kind, shot.isBridge, chain)
            lumiereAdvanceChain(chain, kind)
            LumiereShot("p$index-lu$shotIndex", kind, shot.lines.map { it.sourceIndex }, shot.startTime, shot.endTime, shot.lyricEndTime, shot.isBridge)
        }
        val next = plans.getOrNull(index + 1)
        var transitionOut: LumiereTransitionOut? = null
        if (next != null) {
            val kind = lumiereChooseTransition(seed, index, previousTransition)
            previousTransition = kind
            val gap = next.startTime - plan.lyricEndTime
            transitionOut = LumiereTransitionOut(kind, max(plan.startTime, plan.endTime - lumiereTransitionDuration(kind, gap)), plan.endTime)
        }
        val previous = plans.getOrNull(index - 1)
        val opening = previous == null || plan.startTime - previous.lyricEndTime >= LUMIERE_REOPEN_GAP
        val lineIndices = plan.lines.map { it.sourceIndex }
        LumiereParagraph(
            "lumiere-p$index", index, plan.kind, plan.boundary, plan.startTime, plan.endTime, plan.lyricEndTime,
            lineIndices, lineIndices.map { lines[it] }, shots, transitionOut, opening,
        )
    }
    return LumiereProgram(
        seed, instrumental, gapThreshold,
        if (!instrumental) programEnd else instrumentalDuration,
        lyricEnd,
        if (seamless) lumiereMergeParagraphs(paragraphs) else paragraphs,
    )
}

internal fun findLumiereParagraphIndexAtTime(program: LumiereProgram, time: Double): Int {
    for (index in program.paragraphs.indices.reversed()) if (time >= program.paragraphs[index].startTime) return index
    return 0
}

// ───────────────────────────── 单元（lumiereUnit.ts + lumiereUnitLayout.ts） ─────────────────────────────

/** 场景镜头：光位、时间、覆盖哪几行（单元内下标）。 */
internal class LumiereSceneShot(val profile: LumiereProfile, val startTime: Double, val endTime: Double, val lines: List<Int>)

internal fun lumiereBuildSceneShots(paragraph: LumiereParagraph): List<LumiereSceneShot> {
    val local = HashMap<Int, Int>().also { m -> paragraph.lineIndices.forEachIndexed { i, li -> m[li] = i } }
    val shots = paragraph.shots.map { s -> LumiereSceneShot(LumiereCatalog.profileOf(s.kind), s.startTime, s.endTime, s.lineIndices.mapNotNull { local[it] }) }.toMutableList()
    if (shots.isEmpty()) shots += LumiereSceneShot(LumiereCatalog.profileOf(""), paragraph.startTime, paragraph.endTime, emptyList())
    return shots
}

internal fun lumiereShotAt(paragraph: LumiereParagraph, time: Double): LumiereShot? {
    var found = paragraph.shots.firstOrNull()
    for (shot in paragraph.shots) if (shot.startTime <= time) found = shot
    return found
}

/** resolveLumiereLeadShot：第一个有歌词的镜头（整首一个单元时跳过前奏的间奏镜头）。 */
internal fun lumiereLeadShot(shots: List<LumiereSceneShot>): LumiereSceneShot = shots[max(0, shots.indexOfFirst { it.lines.isNotEmpty() })]

/** lumiereTypographyOfLine：第 i 行成为当前行时用它所在镜头的排版（不在任何镜头里的行跟随前一个有歌词的镜头）。 */
internal fun lumiereTypographyOfLine(shots: List<LumiereSceneShot>): (Int) -> String = { lineIndex ->
    var found = shots[0]
    var result: String? = null
    for (shot in shots) {
        if (lineIndex in shot.lines) { result = shot.profile.typography; break }
        if (shot.lines.isNotEmpty() && shot.lines[0] < lineIndex) found = shot
    }
    result ?: found.profile.typography
}

/** resolveLumiereCameraProgress：没有 sections 整个单元推一次；有 sections 时逐段往返推拉。 */
internal fun lumiereCameraProgress(time: Double, startTime: Double, endTime: Double, sections: List<LumiereSection>?): Double {
    fun eased(s: Double, e: Double): Double {
        val linear = min(1.0, max(0.0, (time - s) / max(e - s, 0.001)))
        return (1 - cos(linear * PI)) / 2
    }
    if (sections.isNullOrEmpty()) return eased(startTime, endTime)
    var index = 0
    for (i in sections.indices) if (sections[i].startTime <= time) index = i
    val p = eased(sections[index].startTime, sections[index].endTime)
    return if (index % 2 == 0) p else 1 - p
}

/** createLumiereCamera：缓慢推近 + 平移，再叠手持感浮动。motion：calm 0.6 / normal 1 / chaotic 1.4。 */
internal fun lumiereCamera(width: Double, height: Double, camera: LumiereCameraSpec, startTime: Double, endTime: Double, sections: List<LumiereSection>?, motion: Double = 1.0): (Double) -> LumiereTransform = { time ->
    val progress = lumiereCameraProgress(time, startTime, endTime, sections)
    val floatX = (sin(time * 0.21 + 0.4) * 0.6 + sin(time * 0.53 + 1.9) * 0.4) * 0.006 * motion
    val floatY = (cos(time * 0.17 + 1.1) * 0.6 + sin(time * 0.47 + 0.3) * 0.4) * 0.006 * motion
    LumiereTransform(
        width / 2 + (camera.driftX * progress + floatX) * width,
        height / 2 + (camera.driftY * progress + floatY) * height,
        width / 2, height / 2,
        (1 + camera.push * progress) * (1 + 0.008 * motion * sin(time * 0.31 + 0.7)),
        0.004 * motion * sin(time * 0.13 + 2.1),
    )
}

internal fun lumiereApplyCamera(t: LumiereTransform, x: Double, y: Double): Pair<Double, Double> {
    val cs = cos(t.rotation)
    val sn = sin(t.rotation)
    val dx = (x - t.pivotX) * t.scale
    val dy = (y - t.pivotY) * t.scale
    return (t.x + dx * cs - dy * sn) to (t.y + dx * sn + dy * cs)
}

// ───────────────────────────── 光位声明（light/rig.ts） ─────────────────────────────

internal class LumiereOsc(val amplitude: Double, val period: Double, val phase: Double)
internal class LumiereGobo(val pattern: String, val frequency: Double, val duty: Double, val drift: Double)

internal class LumiereBeamSpec(
    val x: Double, val y: Double, val angle: Double, val spread: Double, val width: Double, val length: Double,
    val softness: Double, val intensity: Double, val streaks: Double, val streakFreq: Double, val streakSpeed: Double,
    val core: Double, val sway: LumiereOsc?, val pulse: LumiereOsc?, val tint: DoubleArray?, val reveal: Double,
    val gobo: LumiereGobo?, val spectrum: Double, val reach: Double,
)

internal class LumiereFog(val density: Double, val tyndallBase: Double, val scale: Double, val driftX: Double, val driftY: Double, val warp: Double, val ambient: Double)
internal class LumiereGlare(val x: Double, val y: Double, val radius: Double, val intensity: Double, val streak: Double)
/** floor：cx, cy, rx, ry, strength（画面比例）。 */
internal class LumiereCaustic(val scale: Double, val speed: Double, val inBeam: Double, val floor: DoubleArray?)
internal class LumiereWave(val mode: String, val cx: Double, val cy: Double, val radius: Double, val frequency: Double, val speed: Double, val strength: Double, val separation: Double, val shield: Double)
internal class LumiereRig(val beams: List<LumiereBeamSpec>, val fog: LumiereFog, val glare: LumiereGlare?, val caustic: LumiereCaustic? = null, val wave: LumiereWave? = null)

internal const val LUMIERE_MAX_BEAMS = 6
internal val LUMIERE_GOBO_ID = mapOf("blinds" to 1, "cross" to 2, "lattice" to 3, "leaves" to 4)
internal val LUMIERE_WAVE_ID = mapOf("rings" to 1, "slits" to 2, "sources" to 3, "airy" to 4)

internal class LumiereResolvedBeam(
    val ox: Double, val oy: Double, val dx: Double, val dy: Double, val halfWidth: Double, val tanSpread: Double,
    val softness: Double, val length: Double, val intensity: Double, val streaks: Double, val streakFreq: Double,
    val streakPhase: Double, val core: Double, val r: Double, val g: Double, val b: Double,
    val goboPattern: Int, val goboFreq: Double, val goboDuty: Double, val goboPhase: Double, val spectrum: Double, val reach: Double,
)

internal fun lumiereAudioLift(bass: Double) = 1 + 0.15 * max(0.0, bass).pow(1.5)

private fun lumiereOscillate(osc: LumiereOsc?, time: Double) = if (osc == null) 0.0 else osc.amplitude * sin((time / max(osc.period, 0.001) + osc.phase) * PI * 2)

private fun lumiereSmoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)
}

/** resolveBeams：intensity / bass / color / local（镜头开始后的秒数，null = 早已开始）。 */
internal fun lumiereResolveBeams(rig: LumiereRig, time: Double, aspect: Double, intensity: Double, bass: Double, color: DoubleArray, local: Double?): List<LumiereResolvedBeam> =
    rig.beams.take(LUMIERE_MAX_BEAMS).map { beam ->
        val angle = beam.angle + lumiereOscillate(beam.sway, time)
        val pulse = 1 + lumiereOscillate(beam.pulse, time)
        val tint = beam.tint ?: doubleArrayOf(1.0, 1.0, 1.0)
        val reveal = if (beam.reveal > 0 && local != null) 0.06 + 0.94 * lumiereSmoothstep(0.0, 1.0, local / beam.reveal) else 1.0
        LumiereResolvedBeam(
            ox = beam.x * aspect, oy = beam.y, dx = cos(angle), dy = sin(angle), halfWidth = beam.width / 2, tanSpread = tan(beam.spread),
            softness = beam.softness, length = beam.length * reveal, intensity = beam.intensity * pulse * intensity * lumiereAudioLift(bass),
            streaks = beam.streaks, streakFreq = beam.streakFreq, streakPhase = time * beam.streakSpeed, core = beam.core,
            r = tint[0] * color[0], g = tint[1] * color[1], b = tint[2] * color[2],
            goboPattern = beam.gobo?.let { LUMIERE_GOBO_ID[it.pattern] } ?: 0, goboFreq = beam.gobo?.frequency ?: 0.0,
            goboDuty = beam.gobo?.duty ?: 1.0, goboPhase = (beam.gobo?.drift ?: 0.0) * time, spectrum = beam.spectrum, reach = beam.reach,
        )
    }

private fun lumiereFract(v: Double) = v - floor(v)

internal fun lumiereHash11(input: Double): Double {
    var p = lumiereFract(input * 0.1031)
    p *= p + 33.33
    p *= p + p
    return lumiereFract(p)
}

internal fun lumiereValueNoise1(x: Double): Double {
    val i = floor(x)
    val f = x - i
    val u = f * f * (3 - 2 * f)
    return lumiereHash11(i) * (1 - u) + lumiereHash11(i + 1) * u
}

internal fun lumiereHash12(x: Double, y: Double): Double {
    var a = lumiereFract(x * 0.1031)
    var b = lumiereFract(y * 0.1031)
    var c = lumiereFract(x * 0.1031)
    val d = a * (b + 33.33) + b * (c + 33.33) + c * (a + 33.33)
    a += d; b += d; c += d
    return lumiereFract((a + b) * c)
}

internal fun lumiereValueNoise2(x: Double, y: Double): Double {
    val ix = floor(x); val iy = floor(y)
    val fx = x - ix; val fy = y - iy
    val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
    val a = lumiereHash12(ix, iy); val b = lumiereHash12(ix + 1, iy)
    val c = lumiereHash12(ix, iy + 1); val d = lumiereHash12(ix + 1, iy + 1)
    return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy
}

private fun lumiereBand(x: Double, duty: Double): Double {
    val f = lumiereFract(x)
    return 1 - lumiereSmoothstep(duty - 0.06, duty + 0.06, f) + lumiereSmoothstep(1 - 0.06, 1.0, f)
}

internal const val LUMIERE_GOBO_LEAK = 0.12

private fun lumiereGoboPattern(pattern: Int, frequency: Double, duty: Double, phase: Double, u: Double, v: Double): Double = when (pattern) {
    1 -> min(1.0, lumiereBand(u * frequency * 0.5 + phase, duty))
    2 -> lumiereSmoothstep(duty * 0.5, duty * 0.5 + 0.08, abs(u))
    3 -> min(1.0, lumiereBand(u * frequency * 0.5 + v * frequency * 0.8 + phase, duty)) * min(1.0, lumiereBand(u * frequency * 0.5 - v * frequency * 0.8 - phase, duty))
    4 -> lumiereSmoothstep(duty - 0.12, duty + 0.12, lumiereValueNoise2(u * frequency, v * frequency * 0.6 + phase))
    else -> 1.0
}

internal fun lumiereGoboMask(pattern: Int, frequency: Double, duty: Double, phase: Double, u: Double, v: Double) =
    if (pattern > 0) LUMIERE_GOBO_LEAK + (1 - LUMIERE_GOBO_LEAK) * lumiereGoboPattern(pattern, frequency, duty, phase, u, v) else 1.0

/** beamMask：一束光在 (x, y)（高度单位）处的强度，与着色器的 beamMask 相同。 */
internal fun lumiereBeamMask(beam: LumiereResolvedBeam, x: Double, y: Double): Double {
    val px = x - beam.ox
    val py = y - beam.oy
    val along = px * beam.dx + py * beam.dy
    if (along <= 0) return 0.0
    val signedPerp = px * -beam.dy + py * beam.dx
    val perp = abs(signedPerp)
    val half = abs(beam.halfWidth + along * beam.tanSpread) + 1e-4
    val s = perp / half
    val edge = lumiereSmoothstep(1.0, 1 - max(beam.softness, 0.02), s)
    if (edge <= 0) return 0.0
    val signed = s * sign(signedPerp)
    val streak = 1 - beam.streaks + beam.streaks * lumiereValueNoise1(signed * beam.streakFreq + beam.streakPhase)
    val core = 1 - beam.core + beam.core * (1 - s * s)
    val falloff = exp(-along / max(beam.length, 0.001))
    val gobo = if (beam.goboPattern > 0) lumiereGoboMask(beam.goboPattern, beam.goboFreq, beam.goboDuty, beam.goboPhase, signed, along) else 1.0
    val reach = if (beam.reach > 0) 1 - lumiereSmoothstep(beam.reach - max(0.04, half * 2.5), beam.reach, along) else 1.0
    val start = lumiereSmoothstep(0.0, abs(beam.halfWidth) * 2 + 0.01, along)
    return edge * streak * core * falloff * gobo * reach * start * beam.intensity
}

internal fun lumiereLightAt(beams: List<LumiereResolvedBeam>, x: Double, y: Double): Double {
    var sum = 0.0
    for (beam in beams) sum += lumiereBeamMask(beam, x, y)
    return sum
}

internal fun lumiereCompressLight(value: Double) = 1 - exp(-value)

// ───────────────────────────── 光位目录（catalog.ts + rigs/*.ts） ─────────────────────────────

internal class LumiereMotesSpec(val count: Int, val sizeMin: Double, val sizeMax: Double, val driftX: Double, val driftY: Double, val swirl: Double, val swirlPeriod: Double, val twinkle: Double, val ambient: Double, val gain: Double)
internal class LumiereStarfallSpec(val stars: Int, val opening: Double, val rain: Int, val rainSpeed: Double, val rainSpread: Double, val brightness: Double)
internal class LumiereBurstSpec(val mode: String, val density: Double, val every: Int, val offset: Int, val size: Double, val tint: DoubleArray)
internal class LumiereRegion(val cx: Double, val cy: Double, val w: Double, val h: Double)
internal class LumiereDecaySpec(val strength: Double, val delay: Double)
internal class LumiereCameraSpec(val push: Double, val driftX: Double, val driftY: Double)

/** 线稿（第 2 阶段）：lineart/lineArt.ts 的 LineArtSpec 的占位。 */
internal class LumiereLineArtSpec(val paths: List<DoubleArray> = emptyList())

internal class LumiereProfile(
    val kind: String, val label: String, val family: String, val mood: String, val typography: String,
    val region: LumiereRegion, val heroSize: Double, val decay: LumiereDecaySpec, val camera: LumiereCameraSpec,
    val echoSize: Double?, val artGain: Double, val motes: LumiereMotesSpec, val front: LumiereMotesSpec?,
    val starfall: LumiereStarfallSpec?, val burst: LumiereBurstSpec?,
    private val samples: List<Pair<Double, LumiereRig>>,
) {
    /** light(context)：按宽高比插值的离线求值；用到随机数的两个光位手译。 */
    fun light(aspect: Double, random: LumiereRng): LumiereRig = when (kind) {
        "optics-raytrace" -> lumiereOpticsRaytrace(aspect, random)
        "prism-shard" -> lumierePrismShard(random)
        else -> lumiereInterpolateRig(samples, aspect)
    }

    /** lineArt(context)：第 2 阶段。 */
    @Suppress("UNUSED_PARAMETER")
    fun lineArt(aspect: Double, random: LumiereRng): LumiereLineArtSpec = LumiereLineArtSpec()
}

private fun lumiereLerpArr(a: DoubleArray?, b: DoubleArray?, k: Double): DoubleArray? =
    if (a == null || b == null || a.size != b.size) a else DoubleArray(a.size) { lumiereLerp(a[it], b[it], k) }

private fun lumiereLerpOsc(a: LumiereOsc?, b: LumiereOsc?, k: Double) =
    if (a == null || b == null) a else LumiereOsc(lumiereLerp(a.amplitude, b.amplitude, k), lumiereLerp(a.period, b.period, k), lumiereLerp(a.phase, b.phase, k))

private fun lumiereLerpRig(a: LumiereRig, b: LumiereRig, k: Double): LumiereRig {
    if (k <= 0.0 || a.beams.size != b.beams.size) return a
    if (k >= 1.0) return b
    fun l(x: Double, y: Double) = lumiereLerp(x, y, k)
    val beams = a.beams.indices.map { i ->
        val p = a.beams[i]; val q = b.beams[i]
        LumiereBeamSpec(
            l(p.x, q.x), l(p.y, q.y), l(p.angle, q.angle), l(p.spread, q.spread), l(p.width, q.width), l(p.length, q.length),
            l(p.softness, q.softness), l(p.intensity, q.intensity), l(p.streaks, q.streaks), l(p.streakFreq, q.streakFreq), l(p.streakSpeed, q.streakSpeed),
            l(p.core, q.core), lumiereLerpOsc(p.sway, q.sway, k), lumiereLerpOsc(p.pulse, q.pulse, k), lumiereLerpArr(p.tint, q.tint, k), l(p.reveal, q.reveal),
            if (p.gobo != null && q.gobo != null) LumiereGobo(p.gobo.pattern, l(p.gobo.frequency, q.gobo.frequency), l(p.gobo.duty, q.gobo.duty), l(p.gobo.drift, q.gobo.drift)) else p.gobo,
            l(p.spectrum, q.spectrum), l(p.reach, q.reach),
        )
    }
    val f = a.fog; val g = b.fog
    val glare = if (a.glare != null && b.glare != null) LumiereGlare(l(a.glare.x, b.glare.x), l(a.glare.y, b.glare.y), l(a.glare.radius, b.glare.radius), l(a.glare.intensity, b.glare.intensity), l(a.glare.streak, b.glare.streak)) else a.glare
    val caustic = if (a.caustic != null && b.caustic != null) LumiereCaustic(l(a.caustic.scale, b.caustic.scale), l(a.caustic.speed, b.caustic.speed), l(a.caustic.inBeam, b.caustic.inBeam), lumiereLerpArr(a.caustic.floor, b.caustic.floor, k)) else a.caustic
    val wave = if (a.wave != null && b.wave != null) LumiereWave(a.wave.mode, l(a.wave.cx, b.wave.cx), l(a.wave.cy, b.wave.cy), l(a.wave.radius, b.wave.radius), l(a.wave.frequency, b.wave.frequency), l(a.wave.speed, b.wave.speed), l(a.wave.strength, b.wave.strength), l(a.wave.separation, b.wave.separation), l(a.wave.shield, b.wave.shield)) else a.wave
    return LumiereRig(beams, LumiereFog(l(f.density, g.density), l(f.tyndallBase, g.tyndallBase), l(f.scale, g.scale), l(f.driftX, g.driftX), l(f.driftY, g.driftY), l(f.warp, g.warp), l(f.ambient, g.ambient)), glare, caustic, wave)
}

internal fun lumiereInterpolateRig(samples: List<Pair<Double, LumiereRig>>, aspect: Double): LumiereRig {
    if (aspect <= samples.first().first) return samples.first().second
    if (aspect >= samples.last().first) return samples.last().second
    for (i in 0 until samples.size - 1) {
        val (a0, r0) = samples[i]
        val (a1, r1) = samples[i + 1]
        if (aspect <= a1) return lumiereLerpRig(r0, r1, (aspect - a0) / (a1 - a0))
    }
    return samples.last().second
}

/** rigs/optics.ts 的 ray()：从 a 到 b（画面比例）的一段细光束，射程正好到 b。 */
private fun lumiereRay(aspect: Double, ax: Double, ay: Double, bx: Double, by: Double, intensity: Double, width: Double): LumiereBeamSpec {
    val dx = (bx - ax) * aspect
    val dy = by - ay
    return LumiereBeamSpec(ax, ay, atan2(dy, dx), 0.004, width, 3.0, 0.6, intensity, 0.1, 3.0, 0.05, 0.8, null, null, null, 0.0, null, 0.0, hypot(dx, dy) + 0.02)
}

/** optics-raytrace（追迹）：7 个随机点连成 6 段光线。 */
private fun lumiereOpticsRaytrace(aspect: Double, random: LumiereRng): LumiereRig {
    val points = (0 until 7).map { i -> doubleArrayOf(0.05 + (i / 6.0) * 0.9, 0.1 + random.next() * 0.55) }
    val beams = (0 until 6).map { i -> lumiereRay(aspect, points[i][0], points[i][1], points[i + 1][0], points[i + 1][1], 0.8, 0.012) }
    return LumiereRig(beams, LumiereFog(0.9, 0.2, 2.4, 0.008, -0.04, 0.9, 0.02), LumiereGlare(points[0][0], points[0][1], 0.05, 0.9, 0.6))
}

/** prism-shard（碎晶）：5 片晶片各自折出一小束光谱（shaft 预设 + 覆盖）。 */
private fun lumierePrismShard(random: LumiereRng): LumiereRig {
    val beams = (0 until 5).map { i ->
        val x = 0.15 + random.next() * 0.7
        val y = 0.15 + random.next() * 0.5
        val angle = random.next() * PI * 2
        val reach = 0.5 + random.next() * 0.3
        val phase = random.next()
        LumiereBeamSpec(x, y, angle, 0.12, 0.01, 0.5, 0.5, 0.8, 0.3, 6.0, 0.08, 0.3, LumiereOsc(0.1, 9.0 + i, phase), null, null, 0.0, null, 1.0, reach)
    }
    return LumiereRig(beams, LumiereFog(0.9, 0.12, 2.4, 0.008, -0.03, 0.9, 0.03), null)
}

/** 光位目录：10 族 × 10 种，顺序同 catalog.ts（天光、窗隙、棱镜、焦散、光路、衍射、叶脉、星象、追光、萤尘）。 */
internal object LumiereCatalog {
    val familyLabels = mapOf(
        "zenith" to "天光", "lattice" to "窗隙", "prism" to "棱镜", "caustic" to "焦散", "optics" to "光路",
        "wave" to "衍射", "botany" to "叶脉", "astral" to "星象", "stage" to "追光", "motes" to "萤尘",
    )
    val profiles: List<LumiereProfile> by lazy { parseLumiereRigs(LumiereCatalog::class.java.classLoader!!.getResourceAsStream("lumiere_rigs.txt")!!.bufferedReader(Charsets.UTF_8).readText()) }
    private val byKind by lazy { profiles.associateBy { it.kind } }
    val kinds: List<String> by lazy { profiles.map { it.kind } }
    /** 未知的 kind 退回天井（第一个）。 */
    fun profileOf(kind: String): LumiereProfile = byKind[kind] ?: profiles[0]
    fun hasProfile(kind: String) = byKind.containsKey(kind)
}

/** resources/lumiere_rigs.txt 的格式见 docs（K / M / A / B 行，由 Folia 源码在 node 里生成）。 */
internal fun parseLumiereRigs(text: String): List<LumiereProfile> {
    val out = ArrayList<LumiereProfile>()
    var head: List<String>? = null
    var mLine: List<String>? = null
    var samples = ArrayList<Pair<Double, LumiereRig>>()
    var curAspect = 0.0
    var fog: LumiereFog? = null
    var glare: LumiereGlare? = null
    var caustic: LumiereCaustic? = null
    var wave: LumiereWave? = null
    var beams = ArrayList<LumiereBeamSpec>()
    fun d(s: String) = s.toDouble()
    fun flushAspect() {
        if (fog != null) samples += curAspect to LumiereRig(beams, fog!!, glare, caustic, wave)
        fog = null; beams = ArrayList()
    }
    fun motes(p: List<String>): LumiereMotesSpec? = if (p.size < 10) null else LumiereMotesSpec(d(p[0]).toInt(), d(p[1]), d(p[2]), d(p[3]), d(p[4]), d(p[5]), d(p[6]), d(p[7]), d(p[8]), d(p[9]))
    fun flushProfile() {
        flushAspect()
        val k = head ?: return
        val parts = mLine!!.joinToString(" ").split("|").map { it.trim().split(' ').filter { s -> s.isNotEmpty() } }
        val sf = parts[2]
        val bu = parts[3]
        out += LumiereProfile(
            kind = k[0], family = k[1], mood = k[2], typography = k[3],
            region = LumiereRegion(d(k[4]), d(k[5]), d(k[6]), d(k[7])), heroSize = d(k[8]),
            decay = LumiereDecaySpec(d(k[9]), d(k[10])), camera = LumiereCameraSpec(d(k[11]), d(k[12]), d(k[13])),
            echoSize = d(k[14]).takeIf { it >= 0 }, artGain = d(k[15]), label = k.drop(16).joinToString(" "),
            motes = motes(parts[0])!!, front = motes(parts[1]),
            starfall = if (sf.size >= 6) LumiereStarfallSpec(d(sf[0]).toInt(), d(sf[1]), d(sf[2]).toInt(), d(sf[3]), d(sf[4]), d(sf[5])) else null,
            burst = if (bu.size >= 8) LumiereBurstSpec(bu[0], d(bu[1]), d(bu[2]).toInt(), d(bu[3]).toInt(), d(bu[4]), doubleArrayOf(d(bu[5]), d(bu[6]), d(bu[7]))) else null,
            samples = samples,
        )
        samples = ArrayList(); head = null
    }
    for (raw in text.lineSequence()) {
        if (raw.isBlank()) continue
        val tag = raw.substringBefore(' ')
        val rest = raw.substringAfter(' ')
        when (tag) {
            "K" -> { flushProfile(); head = rest.split(' ') }
            "M" -> mLine = listOf(rest)
            "A" -> {
                flushAspect()
                val parts = rest.split("|").map { it.trim().split(' ').filter { s -> s.isNotEmpty() } }
                val f = parts[0]
                curAspect = d(f[0])
                fog = LumiereFog(d(f[1]), d(f[2]), d(f[3]), d(f[4]), d(f[5]), d(f[6]), d(f[7]))
                val g = parts[1]
                glare = if (g.size >= 5) LumiereGlare(d(g[0]), d(g[1]), d(g[2]), d(g[3]), d(g[4])) else null
                val c = parts[2]
                caustic = if (c.size >= 3) LumiereCaustic(d(c[0]), d(c[1]), d(c[2]), if (c.size >= 8) DoubleArray(5) { d(c[3 + it]) } else null) else null
                val w = parts[3]
                wave = if (w.size >= 9) LumiereWave(w[0], d(w[1]), d(w[2]), d(w[3]), d(w[4]), d(w[5]), d(w[6]), d(w[7]), d(w[8])) else null
            }
            "B" -> {
                val p = rest.split(' ')
                fun osc(s: String) = if (s == "-") null else s.split(',').let { LumiereOsc(d(it[0]), d(it[1]), d(it[2])) }
                val tint = if (p[14] == "-") null else p[14].split(',').map { d(it) }.toDoubleArray()
                val gobo = if (p[16] == "-") null else p[16].split(',').let { LumiereGobo(it[0], d(it[1]), d(it[2]), d(it[3])) }
                beams += LumiereBeamSpec(
                    d(p[0]), d(p[1]), d(p[2]), d(p[3]), d(p[4]), d(p[5]), d(p[6]), d(p[7]), d(p[8]), d(p[9]), d(p[10]), d(p[11]),
                    osc(p[12]), osc(p[13]), tint, d(p[15]), gobo, d(p[17]), d(p[18]),
                )
            }
        }
    }
    flushProfile()
    return out
}

// ───────────────────────────── 颜色（color.ts + scene.ts 的 resolveLumierePalette） ─────────────────────────────

internal val LUMIERE_CHAMPAGNE = doubleArrayOf(1.0, 0.86, 0.62)
internal val LUMIERE_WHITE = doubleArrayOf(1.0, 1.0, 1.0)
private val LUMIERE_UNLIT_COOL = doubleArrayOf(0.62, 0.68, 0.8)

internal fun lumiereMixRgb(a: DoubleArray, b: DoubleArray, k: Double) = DoubleArray(3) { a[it] + (b[it] - a[it]) * k }
internal fun lumiereScaleRgb(a: DoubleArray, k: Double) = DoubleArray(3) { a[it] * k }
internal fun lumiereRgbOfArgb(argb: Int) = doubleArrayOf(((argb shr 16) and 0xff) / 255.0, ((argb shr 8) and 0xff) / 255.0, (argb and 0xff) / 255.0)
internal fun lumiereHexOf(rgb: DoubleArray): Int {
    fun c(v: Double) = round(min(1.0, max(0.0, v)) * 255).toInt()
    return (c(rgb[0]) shl 16) or (c(rgb[1]) shl 8) or c(rgb[2])
}
internal fun lumiereLuminance(rgb: DoubleArray) = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

private fun lumiereGlowOf(rgb: DoubleArray, fallback: DoubleArray): DoubleArray {
    val peak = rgb.max()
    return if (peak < 0.04) fallback else lumiereScaleRgb(rgb, 1 / peak)
}

internal class LumierePalette(val light: DoubleArray, val lit: DoubleArray, val unlit: DoubleArray)

/** resolveLumierePalette：themeMix 0 = 香槟金光（掺 18% 强调色），越高越跟随主题色。 */
internal fun resolveLumierePalette(accent: DoubleArray?, primary: DoubleArray?, secondary: DoubleArray?, themeMix: Double = 0.3): LumierePalette {
    val mix = lumiereClamp01(themeMix)
    val acc = accent ?: LUMIERE_CHAMPAGNE
    var light = lumiereMixRgb(lumiereMixRgb(LUMIERE_CHAMPAGNE, acc, 0.18), lumiereGlowOf(acc, LUMIERE_CHAMPAGNE), mix)
    light = lumiereScaleRgb(light, 1 / max(light.max(), 1e-3))
    val warmLit = lumiereMixRgb(light, LUMIERE_WHITE, 0.2)
    val prim = lumiereGlowOf(primary ?: warmLit, warmLit)
    val sec = if (secondary != null) lumiereGlowOf(secondary, prim) else prim
    return LumierePalette(
        light,
        lumiereMixRgb(warmLit, lumiereMixRgb(prim, LUMIERE_WHITE, 0.2), mix),
        lumiereMixRgb(lumiereMixRgb(light, LUMIERE_UNLIT_COOL, 0.55), lumiereMixRgb(sec, LUMIERE_UNLIT_COOL, 0.35), mix),
    )
}

/** resolveLumiereDarkField：背景色 × 0.06，强度 darkField（亮背景保底 0.94）。返回 rgb + alpha。 */
internal fun resolveLumiereDarkField(background: DoubleArray, darkField: Double = 0.75): Pair<DoubleArray, Double> {
    val strength = if (darkField.isFinite()) lumiereClamp01(darkField) else 0.0
    val bright = lumiereLuminance(background) > 0.18
    return lumiereScaleRgb(background, 0.06) to (if (bright) max(strength, 0.94) else strength)
}

/** scene.ts 的 blendRig：两套光位之间的烟雾与眩光（光束另算）。 */
internal fun lumiereBlendRig(from: LumiereRig, to: LumiereRig, k: Double, glareK: Double): LumiereRig {
    fun l(a: Double, b: Double) = lumiereLerp(a, b, k)
    val f = from.fog; val t = to.fog
    val glare = if (from.glare != null && to.glare != null) LumiereGlare(
        lumiereLerp(from.glare.x, to.glare.x, glareK), lumiereLerp(from.glare.y, to.glare.y, glareK), lumiereLerp(from.glare.radius, to.glare.radius, glareK),
        lumiereLerp(from.glare.intensity, to.glare.intensity, glareK), lumiereLerp(from.glare.streak, to.glare.streak, glareK),
    ) else if (k < 0.5) from.glare?.let { LumiereGlare(it.x, it.y, it.radius, it.intensity * (1 - k * 2), it.streak) }
    else to.glare?.let { LumiereGlare(it.x, it.y, it.radius, it.intensity * (k * 2 - 1), it.streak) }
    fun fade(c: LumiereCaustic?, kk: Double) = c?.let { LumiereCaustic(it.scale, it.speed, it.inBeam * kk, it.floor?.let { fl -> fl.copyOf().also { a -> a[4] *= kk } }) }
    val caustic = if (k < 0.5) fade(from.caustic, 1 - k * 2) else fade(to.caustic, k * 2 - 1)
    val wave = if (k < 0.5) from.wave?.let { LumiereWave(it.mode, it.cx, it.cy, it.radius, it.frequency, it.speed, it.strength * (1 - k * 2), it.separation, it.shield) }
    else to.wave?.let { LumiereWave(it.mode, it.cx, it.cy, it.radius, it.frequency, it.speed, it.strength * (k * 2 - 1), it.separation, it.shield) }
    return LumiereRig(emptyList(), LumiereFog(l(f.density, t.density), l(f.tyndallBase, t.tyndallBase), l(f.scale, t.scale), l(f.driftX, t.driftX), l(f.driftY, t.driftY), l(f.warp, t.warp), l(f.ambient, t.ambient)), glare, caustic, wave)
}

// ───────────────────────────── 场景的光位交接（scene.ts 的 update 前半） ─────────────────────────────

internal const val LUMIERE_HANDOFF = 0.9
internal const val LUMIERE_GLARE_MOVE = 1.6

/** 场景单元每帧的光：当前镜头的光束渐强、上一个渐弱（两套都进光场，超过上限留最亮的），烟雾与眩光交叉。 */
internal class LumiereLightFrame(val beams: List<LumiereResolvedBeam>, val rig: LumiereRig, val intensity: Double, val exit: Double, val glareScale: Double, val shotIndex: Int)

/** 一个场景单元（一个段落）里与绘制无关的部分：镜头、领头光位、每个镜头的光位（按单元与镜头播种）、运镜。 */
internal class LumiereUnitModel(
    val paragraph: LumiereParagraph,
    val programSeed: String,
    val width: Double,
    val height: Double,
    val motion: Double = 1.0,
) {
    val aspect = width / height
    val seed = "$programSeed:${paragraph.id}"
    val shots = lumiereBuildSceneShots(paragraph)
    val lead: LumiereProfile = lumiereLeadShot(shots).profile
    val rigs: List<LumiereRig> = shots.mapIndexed { i, s -> s.profile.light(aspect, lumiereRng("$seed:$i:${s.profile.kind}:light")) }
    val camera = lumiereCamera(width, height, lead.camera, paragraph.startTime, paragraph.endTime, paragraph.sections, motion)
    val rest = LumiereTransform(width / 2, height / 2, width / 2, height / 2, 1.0, 0.0)
    val typographyOfLine = lumiereTypographyOfLine(shots)
    /** 文字区（高度单位）：整个单元按领头光位排字。 */
    val textRegion = LumiereRegion(lead.region.cx * aspect, lead.region.cy, lead.region.w * aspect, lead.region.h)
    private val fadeOut = paragraph.transitionOut?.takeIf { it.kind == "lights-out" }
    /** 开场（星空点亮）是第 2 阶段：这里没有 starfall 的点亮时刻，开场按 0 秒点亮处理（光从 −0.4 秒开始升起）。 */
    private val opening = paragraph.opening && (paragraph.endTime - paragraph.startTime) >= 2 && lead.starfall != null
    private val ignition = if (opening) lumiereStarfallIgnition(lead.starfall!!) else 0.0

    fun activeShot(time: Double): Int {
        var index = 0
        for (i in shots.indices) if (shots[i].startTime <= time) index = i
        return index
    }

    fun light(time: Double, palette: LumierePalette, lightIntensity: Double = 1.0, bass: Double = 0.0, boost: Double = 0.0): LumiereLightFrame {
        val local = time - paragraph.startTime
        val enter = if (opening) lumiereSmooth((local - ignition + 0.4) / 1.6) else 1.0
        val ignite = if (opening && local >= ignition) 1.8 * exp(-(local - ignition) / 0.35) else 0.0
        val exit = fadeOut?.let { 1 - lumiereSmooth((time - it.startTime) / max(it.endTime - it.startTime, 0.05)) } ?: 1.0
        val intensity = enter * exit
        val index = activeShot(time)
        val sinceShot = time - shots[index].startTime
        val handoff = if (index > 0) lumiereSmooth(sinceShot / LUMIERE_HANDOFF) else 1.0
        val glareK = if (index > 0) lumiereEaseInOutCubic(sinceShot / LUMIERE_GLARE_MOVE) else 1.0
        fun drive(shotIndex: Int, weight: Double) = lumiereResolveBeams(rigs[shotIndex], time, aspect, intensity * lightIntensity * (1 + boost) * weight, bass, palette.light, time - shots[shotIndex].startTime)
        var beams = drive(index, handoff)
        var rig = rigs[index]
        if (handoff < 1) beams = (beams + drive(index - 1, 1 - handoff)).sortedByDescending { it.intensity }.take(LUMIERE_MAX_BEAMS)
        if (handoff < 1 || glareK < 1) rig = lumiereBlendRig(rigs[index - 1], rigs[index], handoff, glareK)
        return LumiereLightFrame(beams, rig, intensity, exit, intensity * lightIntensity * (1 + boost * 1.5 + ignite), index)
    }
}

/** light/starfall.ts 的 starfallIgnition 只用到 opening：主光柱在倾泻到一半左右点亮（第 2 阶段补全星空）。 */
internal fun lumiereStarfallIgnition(spec: LumiereStarfallSpec) = spec.opening * 0.45
