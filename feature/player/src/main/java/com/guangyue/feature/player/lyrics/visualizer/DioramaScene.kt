// 镜台（Folia diorama）场景的纯逻辑：逐函数对译
//   src/components/visualizer/diorama/dioramaSceneConstants.ts（挂载窗口、字号、雾带、生命周期、跟唱常量、邻行透明度、包络步进）
//   src/components/visualizer/diorama/dioramaSceneUnits.ts（渐变跟唱能量、文字生命周期、取景缩放、行朝向四元数）
//   src/components/visualizer/diorama/dioramaSceneLayout.ts（挂载哪些行、每行摆位、当前行拆逐字 / 逐词单元）
//   src/components/visualizer/diorama/dioramaSceneTypes.ts（VisibleLineEntry / LyricUnit / CJK 判定）
// 未接：粒子隧道 / 点云（dioramaParticleCorridor / dioramaGeometry）、栅格（dioramaTextRaster）、渲染（GL）。
// DioramaSceneTest 对 node 直接跑 Folia 这几个文件的金样（three 0.185.1）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTiming
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ---- dioramaSceneConstants.ts ----

internal const val DIORAMA_LINES_AHEAD = 3
internal const val DIORAMA_LINES_BEHIND = 2
internal const val DIORAMA_OUTGOING_LINES_BEHIND = 2
internal const val DIORAMA_OUTGOING_LINES_AHEAD = 1
internal const val DIORAMA_CORRIDOR_LINES_AHEAD = 7
internal const val DIORAMA_CORRIDOR_LINES_BEHIND = 6
internal const val DIORAMA_NEIGHBOR_RASTER_BUDGET = 2

/** 非当前行按「相对当前行的带符号偏移」取透明度：过去的行留一条暗尾，将来的行在雾里生出来。 */
internal fun resolveDioramaNeighborLineOpacity(offset: Int): Double = when (offset) {
    -1 -> 0.3
    -2 -> 0.1
    1 -> 0.34
    2 -> 0.16
    3 -> 0.06
    else -> 0.0
}

/** 转场中离场走廊的行：原当前行最亮，其余一致偏暗（淡出交给距离生命周期与雾）。 */
internal fun resolveDioramaOutgoingLineOpacity(offsetFromOutgoing: Int): Double =
    if (offsetFromOutgoing == 0) 0.7 else if (abs(offsetFromOutgoing) <= 2) 0.45 else 0.25

internal const val DIORAMA_LINE_FONT_SIZE = 0.62
internal const val DIORAMA_TARGET_FRAME_WIDTH_FRACTION = 0.72
internal const val DIORAMA_MIN_FIT_SCALE = 0.28
internal const val DIORAMA_DEG_TO_RAD = PI / 180
internal const val DIORAMA_FOG_NEAR = 12.0
internal const val DIORAMA_FOG_FAR = 30.0
internal const val DIORAMA_TEXT_DISSOLVE_START = 2.0
internal const val DIORAMA_TEXT_DISSOLVE_END = 0.9
internal const val DIORAMA_TEXT_FADE_IN_START = 32.0
internal const val DIORAMA_TEXT_FADE_IN_END = 40.0
internal const val DIORAMA_COLOR_DAMP_RATE = 1.2
internal const val DIORAMA_ACTIVE_LINE_OPACITY = 0.92
internal const val DIORAMA_UNSUNG_UNIT_OPACITY = 0.5
internal const val DIORAMA_UNIT_GLOW_MAX_OPACITY = 0.9
internal const val DIORAMA_SOUL_MAX_OPACITY = 0.6
internal const val DIORAMA_SOUL_ACTIVE_LIFT_EM = 0.06
internal const val DIORAMA_SOUL_DETACH_LIFT_EM = 0.5
internal const val DIORAMA_SOUL_ACTIVE_SWELL = 0.1
internal const val DIORAMA_SOUL_DETACH_SWELL = 0.3
internal const val DIORAMA_SOUL_HANDOFF_SECONDS = 0.5
internal const val DIORAMA_GRADIENT_HOLD_SECONDS = 0.35
internal const val DIORAMA_GRADIENT_TRAIL_SECONDS = 1.8

private fun sClamp01(v: Double) = min(1.0, max(0.0, v))

/** stepEnvelope：上升快追、下降慢放（attack / release 是每秒速率）。 */
internal fun dioramaStepEnvelope(current: Double, target: Double, attack: Double, release: Double, delta: Double): Double =
    current + (target - current) * (1 - exp(-(if (target > current) attack else release) * delta))

internal fun dioramaSmoothstep01(t: Double) = t * t * (3 - 2 * t)

// ---- dioramaSceneUnits.ts ----

/** 渐变跟唱：一个单元在某一刻的能量（唱前 0 → 唱时缓升到 1 → 保持 → 单向衰减回 0），只看它自己的起止时间。 */
internal fun resolveDioramaGradientEnergy(now: Double, startTime: Double, endTime: Double): Double {
    if (now <= startTime) return 0.0
    if (now < endTime) {
        val span = max(endTime - startTime, 0.001)
        return dioramaSmoothstep01(sClamp01((now - startTime) / span))
    }
    val sinceSung = now - endTime
    if (sinceSung <= DIORAMA_GRADIENT_HOLD_SECONDS) return 1.0
    return 1 - dioramaSmoothstep01(sClamp01((sinceSung - DIORAMA_GRADIENT_HOLD_SECONDS) / DIORAMA_GRADIENT_TRAIL_SECONDS))
}

/** resolveDioramaUnitFill：底色 primary 按自身进度染向 target（ARGB 线性插值，three Color.lerp 同式）。 */
internal fun resolveDioramaUnitFill(primary: Int, target: Int, progress: Double): Int {
    val t = sClamp01(progress)
    fun ch(c: Int, s: Int) = (c shr s) and 0xff
    fun mix(s: Int) = (ch(primary, s) + (ch(target, s) - ch(primary, s)) * t).let { kotlin.math.round(it).toInt().coerceIn(0, 255) }
    return (0xff shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** 当前行的逐单元状态要不要重分配：换了行，或同一行号下歌词被就地换掉（单元数变了）。 */
internal fun shouldResetDioramaUnitState(previousGlobalIndex: Int, globalIndex: Int, unitStateLength: Int?, unitCount: Int): Boolean =
    previousGlobalIndex != globalIndex || unitStateLength != unitCount

/** 歌词平面的距离生命周期（两端）：远处雾外为 0，中段 1，贴近镜头再溶掉。 */
internal fun resolveDioramaTextLife(distanceToCamera: Double): Double {
    val farT = sClamp01((DIORAMA_TEXT_FADE_IN_END - distanceToCamera) / (DIORAMA_TEXT_FADE_IN_END - DIORAMA_TEXT_FADE_IN_START))
    val nearT = sClamp01((distanceToCamera - DIORAMA_TEXT_DISSOLVE_END) / (DIORAMA_TEXT_DISSOLVE_START - DIORAMA_TEXT_DISSOLVE_END))
    return (farT * farT * (3 - 2 * farT)) * (nearT * nearT * (3 - 2 * nearT))
}

/** 整行缩到英雄距离下画面宽的 72% 以内（fov 是竖直视角）。 */
internal fun resolveDioramaFrameFitScale(renderedWidth: Double, distance: Double, verticalFovDeg: Double, aspect: Double): Double {
    if (renderedWidth <= 0 || distance <= 0) return 1.0
    val frameWidth = 2 * distance * tan((verticalFovDeg * DIORAMA_DEG_TO_RAD) / 2) * aspect
    val targetWidth = frameWidth * DIORAMA_TARGET_FRAME_WIDTH_FRACTION
    return min(1.0, max(DIORAMA_MIN_FIT_SCALE, targetWidth / renderedWidth))
}

/** 四元数 [x, y, z, w]。 */
internal data class DioramaQuat(val x: Double, val y: Double, val z: Double, val w: Double) {
    /** three Quaternion.multiply（this × q）。 */
    operator fun times(q: DioramaQuat) = DioramaQuat(
        x * q.w + w * q.x + y * q.z - z * q.y,
        y * q.w + w * q.y + z * q.x - x * q.z,
        z * q.w + w * q.z + x * q.y - y * q.x,
        w * q.w - x * q.x - y * q.y - z * q.z,
    )

    companion object {
        fun axisAngle(ax: Double, ay: Double, az: Double, angle: Double): DioramaQuat {
            val s = sin(angle / 2)
            return DioramaQuat(ax * s, ay * s, az * s, cos(angle / 2))
        }

        /** three Quaternion.setFromRotationMatrix（m 按行列 mRC，列向量是三条轴）。 */
        fun fromBasis(xa: DioramaVec, ya: DioramaVec, za: DioramaVec): DioramaQuat {
            val m11 = xa.x; val m12 = ya.x; val m13 = za.x
            val m21 = xa.y; val m22 = ya.y; val m23 = za.y
            val m31 = xa.z; val m32 = ya.z; val m33 = za.z
            val trace = m11 + m22 + m33
            return if (trace > 0) {
                val s = 0.5 / sqrt(trace + 1.0)
                DioramaQuat((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25 / s)
            } else if (m11 > m22 && m11 > m33) {
                val s = 2.0 * sqrt(1.0 + m11 - m22 - m33)
                DioramaQuat(0.25 * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s)
            } else if (m22 > m33) {
                val s = 2.0 * sqrt(1.0 + m22 - m11 - m33)
                DioramaQuat((m12 + m21) / s, 0.25 * s, (m23 + m32) / s, (m13 - m31) / s)
            } else {
                val s = 2.0 * sqrt(1.0 + m33 - m11 - m22)
                DioramaQuat((m13 + m31) / s, (m23 + m32) / s, 0.25 * s, (m21 - m12) / s)
            }
        }
    }
}

/** frameQuaternion：字面朝回相机（+X → 右、+Y → 上、+Z → −前），再叠摆位的偏航与平面内滚转。 */
internal fun dioramaFrameQuaternion(frame: DioramaFrame, roll: Double = 0.0, yaw: Double = 0.0): DioramaQuat {
    val fwd = DioramaVec(-frame.forward.x, -frame.forward.y, -frame.forward.z)
    var q = DioramaQuat.fromBasis(frame.right, frame.up, fwd)
    if (yaw != 0.0) q *= DioramaQuat.axisAngle(0.0, 1.0, 0.0, yaw)
    if (roll != 0.0) q *= DioramaQuat.axisAngle(0.0, 0.0, 1.0, roll)
    return q
}

// ---- dioramaSceneTypes.ts / dioramaSceneLayout.ts ----

internal class DioramaVisibleLine(
    val index: Int,
    val line: Line,
    val placement: DioramaTextPlacement,
    val position: DioramaVec,
    val quaternion: DioramaQuat,
    /** 转场中属于离场走廊（离离场中心比离当前行近）。 */
    val isOutgoing: Boolean,
)

/** 当前行的一个单元：CJK 每字一个，其他文字每词一个；charStart / charEnd 是整行字符串里的码元下标。 */
internal data class DioramaLyricUnit(val text: String, val charStart: Int, val charEnd: Int, val startTime: Double, val endTime: Double)

/** CJK_GRAPHEME_RE：汉字、假名、兼容表意、半角假名，以及间奏倒数点（•·■–◿）——这些逐字成单元。 */
private val DIORAMA_CJK_GRAPHEME_RE = Regex("[⺀-鿿぀-ヿ豈-﫿ｦ-ﾟ•·■-◿]")

internal fun isDioramaCjkGrapheme(g: String) = DIORAMA_CJK_GRAPHEME_RE.containsMatchIn(g)

/** 挂载哪些全局行：当前行前 2 后 3，转场时再加离场行前 2 后 1；升序。 */
internal fun resolveDioramaMountedIndices(globalIndex: Int, transitionOutgoingIndex: Int?, total: Int): List<Int> {
    val indices = java.util.TreeSet<Int>()
    fun addWindow(center: Int, behind: Int, ahead: Int) {
        val start = max(center - behind, 0)
        val end = min(center + ahead, total - 1)
        for (i in start..end) indices.add(i)
    }
    addWindow(globalIndex, DIORAMA_LINES_BEHIND, DIORAMA_LINES_AHEAD)
    if (transitionOutgoingIndex != null) addWindow(transitionOutgoingIndex, DIORAMA_OUTGOING_LINES_BEHIND, DIORAMA_OUTGOING_LINES_AHEAD)
    return indices.toList()
}

/** 每条挂载行按它在路径上的帧摆好（偏移、滚转、偏航）。 */
internal fun buildDioramaVisibleLines(
    sequencer: DioramaSequencerState,
    mountedIndices: List<Int>,
    globalIndex: Int,
    transitionOutgoingIndex: Int?,
    weaveScale: Double,
): List<DioramaVisibleLine> {
    val result = ArrayList<DioramaVisibleLine>()
    for (i in mountedIndices) {
        val resolved = dioramaResolveGlobal(sequencer, i) ?: continue
        val line = resolved.line ?: continue
        val frame = resolved.frame
        val placement = getDioramaTextPlacement(resolved.localIndex, resolved.segment.seed, weaveScale)
        val position = DioramaVec(
            frame.position.x + frame.right.x * placement.offsetR + frame.up.x * placement.offsetU,
            frame.position.y + frame.right.y * placement.offsetR + frame.up.y * placement.offsetU,
            frame.position.z + frame.right.z * placement.offsetR + frame.up.z * placement.offsetU,
        )
        result += DioramaVisibleLine(
            i, line, placement, position,
            dioramaFrameQuaternion(frame, placement.roll, placement.yaw),
            transitionOutgoingIndex != null && abs(i - transitionOutgoingIndex) <= abs(i - globalIndex),
        )
    }
    return result
}

/** 当前行拆单元：CJK 每字单独，非 CJK 同一词（wordIndex 相同）连着的字归一个单元；空白不成单元。 */
internal fun splitDioramaLyricUnits(activeLine: Line?, timeline: List<GraphemeTiming>): List<DioramaLyricUnit> {
    if (activeLine == null || timeline.isEmpty()) return emptyList()
    val graphemes = GraphemeTimings.splitLyricGraphemes(activeLine.fullText)
    val charOffsets = IntArray(graphemes.size)
    var acc = 0
    graphemes.forEachIndexed { k, g -> charOffsets[k] = acc; acc += g.length }
    val units = ArrayList<DioramaLyricUnit>()
    fun pushUnit(from: Int, to: Int) {
        val text = graphemes.subList(from.coerceAtMost(graphemes.size), to.coerceAtMost(graphemes.size)).joinToString("")
        if (text.trim().isEmpty()) return
        units += DioramaLyricUnit(
            text,
            charOffsets.getOrElse(from) { 0 },
            charOffsets.getOrElse(to - 1) { 0 } + (graphemes.getOrNull(to - 1)?.length ?: 1),
            timeline[from].startTime,
            timeline[to - 1].endTime,
        )
    }
    var i = 0
    while (i < timeline.size) {
        val g = graphemes.getOrElse(i) { "" }
        if (g.trim().isEmpty()) { i += 1; continue }
        if (isDioramaCjkGrapheme(g)) { pushUnit(i, i + 1); i += 1; continue }
        val wordIndex = timeline[i].wordIndex
        var j = i + 1
        while (j < timeline.size && timeline[j].wordIndex == wordIndex &&
            graphemes.getOrElse(j) { "" }.trim().isNotEmpty() && !isDioramaCjkGrapheme(graphemes.getOrElse(j) { "" })
        ) j += 1
        pushUnit(i, j)
        i = j
    }
    return units
}
