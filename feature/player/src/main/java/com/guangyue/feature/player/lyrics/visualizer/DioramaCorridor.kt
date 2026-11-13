// 镜台：粒子隧道与点云的纯逻辑——dioramaParticleCorridor.ts（沿飞行路径的常驻点隧道分段）、
// dioramaMoteField.ts（背景尘埃环形缓冲窗口、每行写点）、dioramaGeometry.ts（点云阵列跨行防撞筛选）逐函数照译。
// 不碰 GL；金样由 node 直接跑 Folia 源（diorama_corridor_golden.txt）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---- dioramaParticleCorridor.ts ----

internal const val DIORAMA_PARTICLE_CORRIDOR_RADIUS = 7.4

/** 一行的隧道分段：起止路径中心点、两端的环基（right/up）、沿路相位（全局行号）、是否启用。 */
internal data class DioramaCorridorSpan(
    val start: DioramaVec,
    val end: DioramaVec,
    val startRight: DioramaVec,
    val endRight: DioramaVec,
    val startUp: DioramaVec,
    val endUp: DioramaVec,
    val pathStart: Int,
    val enabled: Boolean,
)

/** buildDioramaParticleCorridorSpan：没有下一行就沿 forward 往前一步。 */
internal fun buildDioramaCorridorSpan(frame: DioramaFrame, nextFrame: DioramaFrame?, pathStart: Int, enabled: Boolean): DioramaCorridorSpan {
    val start = frame.position
    val end = nextFrame?.position ?: DioramaVec(
        start.x + frame.forward.x * DIORAMA_STEP_DISTANCE,
        start.y + frame.forward.y * DIORAMA_STEP_DISTANCE,
        start.z + frame.forward.z * DIORAMA_STEP_DISTANCE,
    )
    return DioramaCorridorSpan(
        start, end,
        frame.right, nextFrame?.right ?: frame.right,
        frame.up, nextFrame?.up ?: frame.up,
        pathStart, enabled,
    )
}

/**
 * buildDioramaParticleCorridorWindow：在 center 所属那段走廊里、按该段自己的行号取帧，超出歌词两端按同一朝向直线延伸
 * （不夹到歌词范围、不跨段），每个分段的终点 = 下一分段的起点，环不断。
 */
internal fun buildDioramaCorridorWindow(sequencer: DioramaSequencerState, center: Int, behind: Int, ahead: Int): List<DioramaCorridorSpan> {
    val anchor = dioramaResolveGlobal(sequencer, center) ?: return emptyList()
    val segment = anchor.segment
    val first = segment.globalStart
    val last = segment.globalStart + segment.span - 1
    fun frameAt(index: Int): DioramaFrame {
        val clamped = min(max(index, first), last)
        return extendDioramaFrame(dioramaGetFrame(segment.frames, clamped - first), index - clamped)
    }
    val spans = ArrayList<DioramaCorridorSpan>()
    for (i in center - behind..center + ahead) spans += buildDioramaCorridorSpan(frameAt(i), frameAt(i + 1), i, true)
    return spans
}

// ---- dioramaMoteField.ts ----

internal const val DIORAMA_MOTE_CIRCUMFERENCE_MIN = 4
internal const val DIORAMA_MOTE_CIRCUMFERENCE_MAX = 48
internal const val DIORAMA_MOTE_RADIAL_MIN = 1
internal const val DIORAMA_MOTE_RADIAL_MAX = 4
/** DEFAULT_DIORAMA_TUNING 的背景尘埃默认：圆周 28、径向 2。 */
internal const val DIORAMA_MOTE_DEFAULT_CIRCUMFERENCE = 28
internal const val DIORAMA_MOTE_DEFAULT_RADIAL = 2

internal const val DIORAMA_MOTE_LINES_BEHIND = 2
internal const val DIORAMA_MOTE_LINES_AHEAD = 5
internal const val DIORAMA_MOTE_WINDOW_LINES = DIORAMA_MOTE_LINES_BEHIND + DIORAMA_MOTE_LINES_AHEAD + 1
internal const val DIORAMA_MOTE_MAX_POINTS = DIORAMA_MOTE_WINDOW_LINES * DIORAMA_MOTE_CIRCUMFERENCE_MAX * DIORAMA_MOTE_RADIAL_MAX

/** JS Math.round：.5 往 +∞。 */
private fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

internal fun resolveDioramaMoteCircumference(requested: Double): Int = jsRound(min(
    DIORAMA_MOTE_CIRCUMFERENCE_MAX.toDouble(),
    max(DIORAMA_MOTE_CIRCUMFERENCE_MIN.toDouble(), if (requested.isFinite()) requested else DIORAMA_MOTE_DEFAULT_CIRCUMFERENCE.toDouble()),
))

internal fun resolveDioramaMoteRadial(requested: Double): Int = jsRound(min(
    DIORAMA_MOTE_RADIAL_MAX.toDouble(),
    max(DIORAMA_MOTE_RADIAL_MIN.toDouble(), if (requested.isFinite()) requested else DIORAMA_MOTE_DEFAULT_RADIAL.toDouble()),
))

internal fun resolveDioramaMoteCount(circumference: Double, radial: Double): Int =
    resolveDioramaMoteCircumference(circumference) * resolveDioramaMoteRadial(radial)

/** 环形缓冲里一行占的槽位；相邻行永不同槽。 */
internal fun dioramaMoteSlot(line: Int): Int = ((line % DIORAMA_MOTE_WINDOW_LINES) + DIORAMA_MOTE_WINDOW_LINES) % DIORAMA_MOTE_WINDOW_LINES

private const val MOTE_INNER_RADIUS = 2.4
private const val MOTE_RADIAL_SPAN = 5.2
private const val MOTE_VERTICAL_SQUASH = 0.64

/** van der Corput 根逆序列。 */
private fun radicalInverse(index: Int, base: Int): Double {
    var result = 0.0
    var fraction = 1.0 / base
    var i = index
    while (i > 0) {
        result += (i % base) * fraction
        i /= base
        fraction /= base
    }
    return result
}

/** 路径帧沿自身朝向直线外推 steps 行（歌词两端之外隧道 / 尘埃继续）。 */
internal fun extendDioramaFrame(frame: DioramaFrame, steps: Int): DioramaFrame = if (steps == 0) frame else frame.copy(
    position = DioramaVec(
        frame.position.x + frame.forward.x * DIORAMA_STEP_DISTANCE * steps,
        frame.position.y + frame.forward.y * DIORAMA_STEP_DISTANCE * steps,
        frame.position.z + frame.forward.z * DIORAMA_STEP_DISTANCE * steps,
    ),
)

/**
 * writeDioramaMoteLine：把一行的尘埃写进它的槽位（out 按 xyz 平铺，长度 ≥ WINDOW × 圆周 × 径向 × 3）。
 * 每 (seed, 行) 确定：叶序角 + 黄金角错相、sqrt 分层半径、根逆序列深度、种子抖动。
 */
internal fun writeDioramaMoteLine(out: FloatArray, frame: DioramaFrame, line: Int, circumference: Int, radial: Int, seed: String?) {
    val base = dioramaHashSeed(seed)
    val goldenAngle = PI * (3 - sqrt(5.0))
    val twoPi = PI * 2
    val phase = dioramaSeededUnit(base + 991) * twoPi
    val total = circumference * radial
    var write = dioramaMoteSlot(line) * total * 3
    for (ri in 0 until radial) {
        for (ci in 0 until circumference) {
            val p = ri * circumference + ci
            val s = base + line * 131 + p * 17
            val stratum = (ri + 0.35 + dioramaSeededUnit(s + 4) * 0.3) / radial
            val radius = MOTE_INNER_RADIUS + sqrt(stratum) * MOTE_RADIAL_SPAN
            val angle = phase + (line + ri) * goldenAngle +
                (ci.toDouble() / circumference) * twoPi +
                (dioramaSeededUnit(s + 3) - 0.5) * (twoPi / circumference)
            val depth = (radicalInverse(p + 1, 2) + (dioramaSeededUnit(s + 2) - 0.5) / total - 0.5) * DIORAMA_STEP_DISTANCE
            val point = dioramaComposeLocal(frame, cos(angle) * radius, sin(angle) * radius * MOTE_VERTICAL_SQUASH, depth)
            out[write] = point.x.toFloat()
            out[write + 1] = point.y.toFloat()
            out[write + 2] = point.z.toFloat()
            write += 3
        }
    }
}

// ---- dioramaGeometry.ts ----

internal enum class DioramaGeometryMode { CLOUDS, CORRIDOR }

/** DioramaGeometryVisibility：总开关、模式（点云阵列 / 常驻隧道，二选一）、各形状族开关。 */
internal data class DioramaGeometryVisibility(
    val enabled: Boolean = true,
    val mode: DioramaGeometryMode = DioramaGeometryMode.CLOUDS,
    val strands: Boolean = true,
    val blobs: Boolean = true,
    val ribbons: Boolean = true,
    val rings: Boolean = true,
)

/** DioramaParticleClusterAnchor：阵列里的一团点云（role 恒为 formation）。 */
internal data class DioramaClusterAnchor(
    val key: String,
    val sourceLine: Int,
    val particleSeed: String,
    val shape: DioramaShapePlacement,
)

private fun isKindVisible(kind: DioramaShapeKind, v: DioramaGeometryVisibility): Boolean = when (kind) {
    DioramaShapeKind.BOX -> v.strands
    DioramaShapeKind.SPHERE -> v.blobs
    DioramaShapeKind.CONE -> v.ribbons
    DioramaShapeKind.TORUS -> v.rings
}

internal fun dioramaClusterRadius(shape: DioramaShapePlacement): Double {
    val familyRadius = when (shape.kind) {
        DioramaShapeKind.BOX -> 0.5
        DioramaShapeKind.SPHERE -> 0.74
        DioramaShapeKind.CONE -> 0.68
        DioramaShapeKind.TORUS -> 0.9
    }
    return shape.scale * max(familyRadius, shape.stretchY * 0.55) * DIORAMA_PARTICLE_AUDIO_SCALE_MAX
}

private fun distanceBetween(a: DioramaShapePlacement, b: DioramaShapePlacement): Double {
    val dx = a.position.x - b.position.x; val dy = a.position.y - b.position.y; val dz = a.position.z - b.position.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

internal const val DIORAMA_CLUSTER_COLLISION_LINE_SPAN = 2

/**
 * selectVisibleDioramaClusters：只去掉跨行相撞的点云（同一行的阵列不减），输入按 sourceLine 升序，早的行赢；
 * 每个候选对 ±2 行内所有对手（不论对手自己留没留）判定，结论只取决于歌、不取决于镜头。
 */
internal fun selectVisibleDioramaClusters(shapes: List<DioramaClusterAnchor>, visibility: DioramaGeometryVisibility): List<DioramaClusterAnchor> {
    if (!visibility.enabled || visibility.mode != DioramaGeometryMode.CLOUDS) return emptyList()
    val candidates = shapes.filter { isKindVisible(it.shape.kind, visibility) }
    return candidates.filterIndexed { index, candidate ->
        val candidateRadius = dioramaClusterRadius(candidate.shape)
        var keep = true
        var rivalIndex = index - 1
        while (rivalIndex >= 0) {
            val rival = candidates[rivalIndex]
            if (candidate.sourceLine - rival.sourceLine > DIORAMA_CLUSTER_COLLISION_LINE_SPAN) break
            if (rival.sourceLine != candidate.sourceLine) {
                val clearance = max(1.8, (dioramaClusterRadius(rival.shape) + candidateRadius) * 1.18)
                if (distanceBetween(rival.shape, candidate.shape) < clearance) { keep = false; break }
            }
            rivalIndex -= 1
        }
        keep
    }
}
