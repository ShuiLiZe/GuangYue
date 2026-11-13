// 镜台：dioramaSequencer.ts（每首歌 / 每轮单曲循环是一段走廊，接在同一个世界里，行号全局连续）与
// dioramaTransition.ts（切歌一镜到底：新走廊生在雾外的偏移处，镜头沿弯弧飞过去）逐函数照译。纯函数，金样同 DioramaPath。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class DioramaSegment(
    val key: String,
    val seed: String,
    val round: Int,
    var lines: List<Line>,
    var frames: List<DioramaFrame>,
    val globalStart: Int,
    var span: Int,
    val placementOrigin: DioramaVec,
    var linesEpoch: Int = 0,
)

internal class DioramaSequencerState {
    var segments: MutableList<DioramaSegment> = ArrayList()
    var nextGlobalStart = 0
}

internal class DioramaResolvedLine(val segment: DioramaSegment, val localIndex: Int, val frame: DioramaFrame, val line: Line?)

internal fun dioramaActiveSegment(state: DioramaSequencerState): DioramaSegment? = state.segments.lastOrNull()

/** appendSegment：新走廊放在 placementOrigin（它第 0 行的世界位置），至少占一格全局行号。 */
internal fun dioramaAppendSegment(state: DioramaSequencerState, seed: String, lines: List<Line>, round: Int, placementOrigin: DioramaVec): DioramaSegment {
    val frames = dioramaTranslateFrames(buildDioramaPath(lines.size, seed), placementOrigin)
    val span = max(lines.size, 1)
    val seg = DioramaSegment("$seed#$round", seed, round, lines, frames, state.nextGlobalStart, span, placementOrigin)
    state.segments.add(seg)
    state.nextGlobalStart += span
    return seg
}

/** updateActiveSegmentLines：歌词晚到时就地重建当前段（同一原点、同一 globalStart），linesEpoch + 1。 */
internal fun dioramaUpdateActiveSegmentLines(state: DioramaSequencerState, lines: List<Line>) {
    val seg = state.segments.lastOrNull() ?: return
    val span = max(lines.size, 1)
    state.nextGlobalStart += span - seg.span
    seg.lines = lines
    seg.frames = dioramaTranslateFrames(buildDioramaPath(lines.size, seg.seed), seg.placementOrigin)
    seg.span = span
    seg.linesEpoch += 1
}

internal fun dioramaTotalGlobalLines(state: DioramaSequencerState) = state.nextGlobalStart

internal fun dioramaResolveGlobal(state: DioramaSequencerState, globalIndex: Int): DioramaResolvedLine? {
    for (seg in state.segments) {
        val local = globalIndex - seg.globalStart
        if (local >= 0 && local < seg.span) return DioramaResolvedLine(seg, local, dioramaGetFrame(seg.frames, local), seg.lines.getOrNull(local))
    }
    return null
}

/** pruneSegments：整段都在 keepFromGlobal 之前的丢掉（当前段永远保留）。 */
internal fun dioramaPruneSegments(state: DioramaSequencerState, keepFromGlobal: Int) {
    if (state.segments.size <= 1) return
    state.segments = state.segments.filter { it.globalStart + it.span - 1 >= keepFromGlobal }.toMutableList()
}

// ---- 切歌一镜到底（dioramaTransition.ts） ----

internal const val DIORAMA_TRANSITION_DURATION = 3.2
internal const val DIORAMA_TRANSITION_DISTANCE = 46.0
internal const val DIORAMA_TRANSITION_BANK = 0.14
internal const val DIORAMA_TRANSITION_AIM_SWEEP = 0.14

private fun vlen(a: DioramaVec) = sqrt(a.x * a.x + a.y * a.y + a.z * a.z)

/** 水平面里垂直于飞行方向的单位向量（弧往哪边鼓、视线往哪边扫、机身往哪边倾）。 */
internal fun dioramaFlightPerp(from: DioramaVec, to: DioramaVec): DioramaVec {
    val p = DioramaVec(to.z - from.z, 0.0, -(to.x - from.x))
    val l = vlen(p)
    return if (l < 1e-3) DioramaVec(1.0, 0.0, 0.0) else DioramaVec(p.x / l, p.y / l, p.z / l)
}

internal fun dioramaFlightSide(seed: String?, epoch: Int): Int = if (dioramaSeededUnit(dioramaHashSeed(seed) + epoch * 17) < 0.5) -1 else 1

/** pickTransitionOffset：方位角整圈、仰角偏上，z 总为负（新场景大致在前方），长度 46（在雾外）。 */
internal fun dioramaPickTransitionOffset(seed: String?, epoch: Int): DioramaVec {
    val base = dioramaHashSeed(seed) + epoch * 131
    val azimuth = dioramaSeededUnit(base + 1) * PI * 2
    val elevation = (dioramaSeededUnit(base + 2) - 0.35) * 1.3
    val ce = cos(elevation)
    val dir = DioramaVec(sin(azimuth) * ce, sin(elevation), -abs(cos(azimuth) * ce) - 0.35)
    val l = vlen(dir).let { if (it == 0.0) 1.0 else it }
    return DioramaVec(dir.x / l * DIORAMA_TRANSITION_DISTANCE, dir.y / l * DIORAMA_TRANSITION_DISTANCE, dir.z / l * DIORAMA_TRANSITION_DISTANCE)
}

/** smootherstep 6t⁵ − 15t⁴ + 10t³。 */
internal fun dioramaTransitionEase(t: Double): Double { val c = min(1.0, max(0.0, t)); return c * c * c * (c * (c * 6 - 15) + 10) }

/** bezierControl：把直线飞行鼓成一道弧（垂直方向 × 长度 × 0.32，顶点再抬 0.55 倍）。 */
internal fun dioramaBezierControl(from: DioramaVec, to: DioramaVec, seed: String?, epoch: Int): DioramaVec {
    val mid = DioramaVec((from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2)
    val flightLen = vlen(to - from).let { if (it == 0.0) 1.0 else it }
    val perp = dioramaFlightPerp(from, to)
    val side = dioramaFlightSide(seed, epoch)
    val bow = flightLen * 0.32
    return DioramaVec(mid.x + perp.x * bow * side, mid.y + bow * 0.55, mid.z + perp.z * bow * side)
}

internal fun dioramaBezierArc(from: DioramaVec, control: DioramaVec, to: DioramaVec, t: Double): DioramaVec {
    val mt = 1 - t; val a = mt * mt; val b = 2 * mt * t; val c = t * t
    return DioramaVec(a * from.x + b * control.x + c * to.x, a * from.y + b * control.y + c * to.y, a * from.z + b * control.z + c * to.z)
}
