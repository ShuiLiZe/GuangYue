package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

// 镜台粒子模型：Folia dioramaParticleSurfaces.ts（各形状焊接点阵）+ dioramaParticleModel.ts（点云 / 隧道两种模式共用一套属性布局、
// 分频段起拍追踪、整体脉动与弹簧）逐函数照译。缓冲用 FloatArray（对应 Float32Array），数值先按 Double 算再落 Float。
// GL 渲染（dioramaParticleShaders / Materials）另起文件，这里只出确定性的数据。

internal const val DIORAMA_PARTICLE_DENSITY_MIN = 96
internal const val DIORAMA_PARTICLE_DENSITY_MAX = 1536
internal const val DIORAMA_PARTICLE_DENSITY_STEP = 24

internal const val DIORAMA_MAX_PARTICLE_POINTS = 65536
private const val DIORAMA_MAX_CLOUD_POINTS_PER_CLUSTER = 1024
private const val DIORAMA_MAX_CORRIDOR_POINTS_PER_SPAN = 2048

private const val TWO_PI = PI * 2

private fun dpmRound(v: Double): Int = floor(v + 0.5).toInt()

private fun dpmHypot3(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

// ───────────────────────── dioramaParticleSurfaces.ts ─────────────────────────

/** 一个形状的点阵：位置 / 位移方向（连续场）、自身平均半径、点数、相邻点间距（以 radius 为单位）。 */
internal class DioramaStructuredSurface(
    val positions: FloatArray,
    val normals: FloatArray,
    val radius: Double,
    val count: Int,
    val spacing: Double,
)

private const val BOX_HALF = 0.55
private const val CYLINDER_RADIUS = 0.58
private const val CYLINDER_HALF_HEIGHT = 0.75
private const val TORUS_MAJOR = 0.68
private const val TORUS_TUBE = 0.12
private val TETRA_VERTICES = arrayOf(
    doubleArrayOf(0.0, 0.86, 0.0),
    doubleArrayOf(-0.81, -0.5, 0.47),
    doubleArrayOf(0.81, -0.5, 0.47),
    doubleArrayOf(0.0, -0.5, -0.94),
)
private val TETRA_FACES = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 1), intArrayOf(1, 3, 2))
private val TETRA_CENTER = doubleArrayOf(0.0, -0.16, 0.0)

private class Lattice {
    val positions = ArrayList<Double>()
    val normals = ArrayList<Double>()
    var spacing = 0.0
    fun push(px: Double, py: Double, pz: Double, nx: Double, ny: Double, nz: Double) {
        positions.add(px); positions.add(py); positions.add(pz)
        normals.add(nx); normals.add(ny); normals.add(nz)
    }
    /** 平面形状（盒 / 四面体）的位移方向：从形状中心直指出去，最平滑的连续场。 */
    fun pushRadial(px: Double, py: Double, pz: Double, cx: Double = 0.0, cy: Double = 0.0, cz: Double = 0.0) {
        val dx = px - cx; val dy = py - cy; val dz = pz - cz
        val len = dpmHypot3(dx, dy, dz).let { if (it == 0.0) 1.0 else it }
        push(px, py, pz, dx / len, dy / len, dz / len)
    }
}

private fun countBoxShell(across: Int, tall: Int): Int =
    2 * (across * tall + tall * across + across * across) - 4 * (across + tall + across) + 8

private fun resolveBoxShell(budget: Int, stretchY: Double): IntArray {
    var best = intArrayOf(2, 2)
    for (across in 2..96) {
        val tall = max(2, dpmRound(1 + (across - 1) * stretchY))
        if (countBoxShell(across, tall) > budget) break
        best = intArrayOf(across, tall)
    }
    return best
}

private fun buildBoxSurface(budget: Int, stretchY: Double, out: Lattice) {
    val (across, tall) = resolveBoxShell(budget, stretchY).let { it[0] to it[1] }
    fun axis(index: Int, steps: Int): Double = (index.toDouble() / (steps - 1) - 0.5) * 2 * BOX_HALF
    out.spacing = (2 * BOX_HALF) / max(1, min(across, tall) - 1)
    for (iy in 0 until tall) for (iz in 0 until across) for (ix in 0 until across) {
        val interior = ix > 0 && ix < across - 1 && iy > 0 && iy < tall - 1 && iz > 0 && iz < across - 1
        if (interior) continue
        out.pushRadial(axis(ix, across), axis(iy, tall), axis(iz, across))
    }
}

private fun buildCylinderSurface(budget: Int, stretchY: Double, out: Lattice) {
    val aspect = (TWO_PI * CYLINDER_RADIUS) / (2 * CYLINDER_HALF_HEIGHT * max(0.2, stretchY))
    val rows = max(2, dpmRound(sqrt(budget / max(0.1, aspect))))
    val columns = max(3, floor(budget.toDouble() / rows).toInt())
    out.spacing = max((2 * CYLINDER_HALF_HEIGHT) / (rows - 1), (TWO_PI * CYLINDER_RADIUS) / columns)
    for (row in 0 until rows) {
        val y = (row.toDouble() / (rows - 1) - 0.5) * 2 * CYLINDER_HALF_HEIGHT
        for (column in 0 until columns) {
            val angle = (column.toDouble() / columns) * TWO_PI
            val c = StrictMath.cos(angle); val s = StrictMath.sin(angle)
            out.push(c * CYLINDER_RADIUS, y, s * CYLINDER_RADIUS, c, 0.0, s)
        }
    }
}

private fun buildTetrahedronSurface(budget: Int, out: Lattice) {
    var steps = 1
    while (TETRA_FACES.size * (((steps + 2) * (steps + 3)) / 2) <= budget) steps += 1
    val v0 = TETRA_VERTICES[0]; val v1 = TETRA_VERTICES[1]
    out.spacing = dpmHypot3(v0[0] - v1[0], v0[1] - v1[1], v0[2] - v1[2]) / steps
    for (face in TETRA_FACES) {
        val a = TETRA_VERTICES[face[0]]; val b = TETRA_VERTICES[face[1]]; val c = TETRA_VERTICES[face[2]]
        for (i in 0..steps) for (j in 0..(steps - i)) {
            val wa = i.toDouble() / steps
            val wb = j.toDouble() / steps
            val wc = 1 - wa - wb
            out.pushRadial(
                a[0] * wa + b[0] * wb + c[0] * wc,
                a[1] * wa + b[1] * wb + c[1] * wc,
                a[2] * wa + b[2] * wb + c[2] * wc,
                TETRA_CENTER[0], TETRA_CENTER[1], TETRA_CENTER[2],
            )
        }
    }
}

private fun buildTorusSurface(budget: Int, out: Lattice) {
    val minor = max(4, dpmRound(sqrt(budget / 4.0)))
    val major = max(6, floor(budget.toDouble() / minor).toInt())
    out.spacing = max((TWO_PI * (TORUS_MAJOR + TORUS_TUBE)) / major, (TWO_PI * TORUS_TUBE) / minor)
    for (i in 0 until major) {
        val ma = (i.toDouble() / major) * TWO_PI
        val mc = StrictMath.cos(ma); val ms = StrictMath.sin(ma)
        for (j in 0 until minor) {
            val na = (j.toDouble() / minor) * TWO_PI
            val nc = StrictMath.cos(na); val ns = StrictMath.sin(na)
            val r = TORUS_MAJOR + TORUS_TUBE * nc
            out.push(r * mc, TORUS_TUBE * ns, r * ms, nc * mc, ns, nc * ms)
        }
    }
}

private val surfaceCache = HashMap<String, DioramaStructuredSurface>()

internal fun buildDioramaStructuredSurface(kind: DioramaShapeKind, budget: Int, stretchY: Double = 1.0): DioramaStructuredSurface {
    // 拉伸量分桶（0.25 一档），连续滑杆也只落在少数几套缓存点阵上。
    val stretchKey = dpmRound(max(0.2, stretchY) * 4) / 4.0
    val cacheKey = "${kind.id}:$budget:${dpmNumStr(stretchKey)}"
    synchronized(surfaceCache) { surfaceCache[cacheKey]?.let { return it } }
    val out = Lattice()
    when (kind) {
        DioramaShapeKind.BOX -> buildBoxSurface(budget, stretchKey, out)
        DioramaShapeKind.SPHERE -> buildCylinderSurface(budget, stretchKey, out)
        DioramaShapeKind.CONE -> buildTetrahedronSurface(budget, out)
        DioramaShapeKind.TORUS -> buildTorusSurface(budget, out)
    }
    val positions = FloatArray(out.positions.size) { out.positions[it].toFloat() }
    val normals = FloatArray(out.normals.size) { out.normals[it].toFloat() }
    val count = positions.size / 3
    var total = 0.0
    for (i in 0 until count) total += dpmHypot3(positions[i * 3].toDouble(), positions[i * 3 + 1].toDouble(), positions[i * 3 + 2].toDouble())
    val radius = if (count > 0) total / count else 1.0
    val surface = DioramaStructuredSurface(positions, normals, radius, count, out.spacing / radius)
    synchronized(surfaceCache) { surfaceCache[cacheKey] = surface }
    return surface
}

/** JS 的 String(number)：整数不带 .0（缓存键用，只需稳定）。 */
private fun dpmNumStr(v: Double): String = if (v == floor(v)) v.toLong().toString() else v.toString()

// ───────────────────────── dioramaParticleModel.ts ─────────────────────────

/**
 * 两种几何模式共用的属性布局（交给 GL 着色器）：每点 基础局部偏移 / 单位位移方向 / 锚点 / (整体缩放, Y 拉伸, 自身半径)
 * / 调色与涟漪相位 / (族, 色槽, 是否远景) / 波坐标 (w1, w2)；spacing = 本缓冲最粗的相邻间距（半径单位）。
 */
internal class DioramaParticleGeometryData(
    val positions: FloatArray,
    val normals: FloatArray,
    val anchors: FloatArray,
    val scales: FloatArray,
    val phases: FloatArray,
    val styles: FloatArray,
    val waves: FloatArray,
    val pointCount: Int,
    val pointsPerUnit: Int,
    val spacing: Double,
)

private fun familyIndex(kind: DioramaShapeKind): Int = when (kind) {
    DioramaShapeKind.BOX -> 0
    DioramaShapeKind.SPHERE -> 1
    DioramaShapeKind.CONE -> 2
    DioramaShapeKind.TORUS -> 3
}

internal fun normalizeDioramaDensity(density: Double): Int {
    val clamped = min(DIORAMA_PARTICLE_DENSITY_MAX.toDouble(), max(DIORAMA_PARTICLE_DENSITY_MIN.toDouble(), density))
    return max(DIORAMA_PARTICLE_DENSITY_MIN, (floor(clamped / DIORAMA_PARTICLE_DENSITY_STEP) * DIORAMA_PARTICLE_DENSITY_STEP).toInt())
}

/** 点阵能承载的最短波长对应的波数（每波长至少 4 个采样）。 */
internal fun resolveDioramaWaveNumberMax(spacing: Double): Double = TWO_PI / max(1e-4, spacing * 4)

private fun dpmAllocate(pointCount: Int, pointsPerUnit: Int, spacing: Double) = DioramaParticleGeometryData(
    FloatArray(pointCount * 3), FloatArray(pointCount * 3), FloatArray(pointCount * 3), FloatArray(pointCount * 3),
    FloatArray(pointCount), FloatArray(pointCount * 3), FloatArray(pointCount * 2), pointCount, pointsPerUnit, spacing,
)

private fun dpmWriteStyle(data: DioramaParticleGeometryData, index: Int, family: Int, colorSlot: Int, isFar: Int) {
    val o = index * 3
    data.styles[o] = family.toFloat(); data.styles[o + 1] = colorSlot.toFloat(); data.styles[o + 2] = isFar.toFloat()
}

/** 点云模式：每团可见阵列锚点展开成确定性的形状点阵。 */
internal fun buildDioramaCloudGeometryData(clusters: List<DioramaClusterAnchor>, density: Double): DioramaParticleGeometryData {
    val budget = normalizeDioramaDensity(min(density, DIORAMA_MAX_CLOUD_POINTS_PER_CLUSTER.toDouble()))
    val surfaces = clusters.map { buildDioramaStructuredSurface(it.shape.kind, budget, it.shape.stretchY) }
    val pointCount = surfaces.sumOf { it.count }
    val spacing = surfaces.fold(0.0) { w, s -> max(w, s.spacing) }
    val data = dpmAllocate(pointCount, budget, spacing)
    var target = 0
    clusters.forEachIndexed { ci, cluster ->
        val surface = surfaces[ci]
        val shape = cluster.shape
        val seed = dioramaHashSeed("${cluster.particleSeed}|${shape.kind.id}")
        val clusterPhase = dioramaSeededUnit(seed + 31) * PI * 2
        val family = familyIndex(shape.kind)
        val isFar = if (shape.far) 1 else 0
        for (pi in 0 until surface.count) {
            val v = target * 3
            val px = surface.positions[pi * 3]; val py = surface.positions[pi * 3 + 1]; val pz = surface.positions[pi * 3 + 2]
            data.positions[v] = px; data.positions[v + 1] = py; data.positions[v + 2] = pz
            data.normals[v] = surface.normals[pi * 3]; data.normals[v + 1] = surface.normals[pi * 3 + 1]; data.normals[v + 2] = surface.normals[pi * 3 + 2]
            data.anchors[v] = shape.position.x.toFloat(); data.anchors[v + 1] = shape.position.y.toFloat(); data.anchors[v + 2] = shape.position.z.toFloat()
            data.scales[v] = shape.scale.toFloat(); data.scales[v + 1] = shape.stretchY.toFloat(); data.scales[v + 2] = surface.radius.toFloat()
            data.phases[target] = clusterPhase.toFloat()
            data.waves[target * 2] = (clusterPhase + (px + pz * 0.6) * 2.2).toFloat()
            data.waves[target * 2 + 1] = (clusterPhase * 0.7 + py * 2.6).toFloat()
            dpmWriteStyle(data, target, family, shape.colorSlot, isFar)
            target += 1
        }
    }
    return data
}

private fun dpmNormalizeVec(x: Double, y: Double, z: Double): DioramaVec {
    val len = dpmHypot3(x, y, z).let { if (it == 0.0) 1.0 else it }
    return DioramaVec(x / len, y / len, z / len)
}

private fun resolveRingSegments(pointsPerSpan: Int): Int = max(12, min(256, dpmRound(sqrt(pointsPerSpan * 2.4))))

/** 隧道模式：沿挂载的路径段扫出规则环网格（环心贴路径、半径恒定 = 真圆柱）。 */
internal fun buildDioramaCorridorGeometryData(
    spans: List<DioramaCorridorSpan>,
    density: Double,
    radius: Double = DIORAMA_PARTICLE_CORRIDOR_RADIUS,
): DioramaParticleGeometryData {
    val active = spans.filter { it.enabled }
    val pointsPerSpan = normalizeDioramaDensity(min(density, DIORAMA_MAX_CORRIDOR_POINTS_PER_SPAN.toDouble()))
    val ringSegments = resolveRingSegments(pointsPerSpan)
    val rings = max(1, pointsPerSpan / ringSegments)
    val perSpan = ringSegments * rings
    val pointCount = active.size * perSpan
    val spanUnits = DIORAMA_STEP_DISTANCE / radius
    val spacing = max(spanUnits / max(1, rings), TWO_PI / ringSegments)
    val data = dpmAllocate(pointCount, perSpan, spacing)
    var target = 0
    for (span in active) {
        for (ring in 0 until rings) {
            val t = if (rings > 1) ring.toDouble() / (rings - 1) else 0.5
            val cx = span.start.x + (span.end.x - span.start.x) * t
            val cy = span.start.y + (span.end.y - span.start.y) * t
            val cz = span.start.z + (span.end.z - span.start.z) * t
            val right = dpmNormalizeVec(
                span.startRight.x + (span.endRight.x - span.startRight.x) * t,
                span.startRight.y + (span.endRight.y - span.startRight.y) * t,
                span.startRight.z + (span.endRight.z - span.startRight.z) * t,
            )
            val up = dpmNormalizeVec(
                span.startUp.x + (span.endUp.x - span.startUp.x) * t,
                span.startUp.y + (span.endUp.y - span.startUp.y) * t,
                span.startUp.z + (span.endUp.z - span.startUp.z) * t,
            )
            val longitudinal = span.pathStart + t
            val slot = (dpmRound(span.pathStart.toDouble()) + ring) % 2
            for (segment in 0 until ringSegments) {
                val angle = (segment.toDouble() / ringSegments) * TWO_PI
                val c = StrictMath.cos(angle); val s = StrictMath.sin(angle)
                val radial = dpmNormalizeVec(right.x * c + up.x * s, right.y * c + up.y * s, right.z * c + up.z * s)
                val v = target * 3
                data.positions[v] = (radial.x * radius).toFloat(); data.positions[v + 1] = (radial.y * radius).toFloat(); data.positions[v + 2] = (radial.z * radius).toFloat()
                data.normals[v] = radial.x.toFloat(); data.normals[v + 1] = radial.y.toFloat(); data.normals[v + 2] = radial.z.toFloat()
                data.anchors[v] = cx.toFloat(); data.anchors[v + 1] = cy.toFloat(); data.anchors[v + 2] = cz.toFloat()
                data.scales[v] = 1f; data.scales[v + 1] = 1f; data.scales[v + 2] = radius.toFloat()
                data.phases[target] = (longitudinal * 1.7).toFloat()
                data.waves[target * 2] = (longitudinal * spanUnits).toFloat()
                data.waves[target * 2 + 1] = angle.toFloat()
                dpmWriteStyle(data, target, 3, slot, 0)
                target += 1
            }
        }
    }
    return data
}

internal fun stepDioramaEnvelope(current: Double, target: Double, attack: Double, release: Double, delta: Double): Double =
    current + (target - current) * (1 - exp(-(if (target > current) attack else release) * delta))

private fun dpmClamp01(v: Double): Double = min(1.0, max(0.0, v))

/** 单频段追踪：谷底慢升快降、峰值快升慢降；transient = 起拍相对本频段动态范围，sustained = 相对整首响度。 */
internal class DioramaBandTracker(
    var fast: Double = 0.0,
    var floor: Double = 0.0,
    var peak: Double = 0.0,
    var armed: Boolean = false,
    var primed: Boolean = false,
)

private const val FAST_ATTACK = 22.0
private const val FAST_RELEASE = 7.0
private const val FLOOR_RISE = 2.5
private const val FLOOR_FALL = 4.0
private const val PEAK_RISE = 9.0
private const val PEAK_FALL = 0.28
private const val MIN_RANGE = 0.12
private const val MIN_PEAK = 0.22
private const val TRIGGER_HIGH = 0.42
private const val TRIGGER_LOW = 0.2

internal data class DioramaBandSignal(val transient: Double, val sustained: Double, val onset: Boolean)

internal fun stepDioramaBandTracker(state: DioramaBandTracker, level: Double, delta: Double): DioramaBandSignal {
    val safe = dpmClamp01(level)
    if (!state.primed) {
        state.fast = safe; state.floor = safe; state.peak = safe; state.primed = true
    } else {
        state.fast = stepDioramaEnvelope(state.fast, safe, FAST_ATTACK, FAST_RELEASE, delta)
        state.floor = stepDioramaEnvelope(state.floor, safe, FLOOR_RISE, FLOOR_FALL, delta)
        state.peak = stepDioramaEnvelope(state.peak, safe, PEAK_RISE, PEAK_FALL, delta)
    }
    val range = max(MIN_RANGE, state.peak - state.floor)
    val transient = dpmClamp01((state.fast - state.floor) / range)
    val sustained = dpmClamp01(state.fast / max(MIN_PEAK, state.peak))
    var onset = false
    if (!state.armed && transient >= TRIGGER_HIGH) {
        state.armed = true; onset = true
    } else if (state.armed && transient <= TRIGGER_LOW) {
        state.armed = false
    }
    return DioramaBandSignal(transient, sustained, onset)
}

internal enum class DioramaRippleBandId { BASS, MID, TREBLE }

internal data class DioramaRippleBand(val band: DioramaRippleBandId, val strength: Double, val speed: Double, val width: Double, val wavenumber: Double)

/** 各频段自己的涟漪尺度：低频慢而宽，高频快而窄（宽度/速度以形状自身半径为单位）。 */
internal val DIORAMA_RIPPLE_BANDS = listOf(
    DioramaRippleBand(DioramaRippleBandId.BASS, 1.45, 0.9, 0.66, 3.4),
    DioramaRippleBand(DioramaRippleBandId.MID, 0.9, 1.6, 0.36, 5.5),
    DioramaRippleBand(DioramaRippleBandId.TREBLE, 0.5, 2.5, 0.22, 9.0),
)
internal const val DIORAMA_RIPPLE_SLOTS_PER_BAND = 3
internal val DIORAMA_RIPPLE_COUNT = DIORAMA_RIPPLE_BANDS.size * DIORAMA_RIPPLE_SLOTS_PER_BAND

private fun dpmSmoothstep(e0: Double, e1: Double, v: Double): Double {
    val a = min(1.0, max(0.0, (v - e0) / (e1 - e0)))
    return a * a * (3 - 2 * a)
}

internal data class DioramaParticleAudioResponse(val flowSpeed: Double, val clusterPulse: Double)

internal fun resolveDioramaParticleAudioResponse(bass: DioramaBandSignal, mid: DioramaBandSignal): DioramaParticleAudioResponse =
    DioramaParticleAudioResponse(
        flowSpeed = min(1.55, 0.3 + dpmClamp01(bass.sustained) * 1.25),
        clusterPulse = min(
            DIORAMA_PARTICLE_AUDIO_SCALE_MAX,
            1 + dpmSmoothstep(0.1, 0.9, bass.transient) * 0.14 + dpmSmoothstep(0.15, 0.95, mid.transient) * 0.05,
        ),
    )

/** 隧道只保留整体脉动的 12%（整圈墙随拍一胀一缩读起来像机械收缩；节拍交给墙上的涟漪）。 */
internal const val DIORAMA_CORRIDOR_PULSE_SHARE = 0.12

internal fun resolveDioramaPulseTarget(clusterPulse: Double, gain: Double, isCorridor: Boolean): Double =
    1 + (clusterPulse - 1) * gain * (if (isCorridor) DIORAMA_CORRIDOR_PULSE_SHARE else 1.0)

internal class DioramaParticleElasticState(var value: Double = 1.0, var velocity: Double = 0.0)

/** 欠阻尼弹簧：拍点落下有回弹再稳住（1/240 s 子步积分，值夹在 0.9 … 音频缩放上限）。 */
internal fun stepDioramaParticleElasticResponse(state: DioramaParticleElasticState, target: Double, delta: Double): Double {
    var remaining = min(0.1, max(0.0, delta))
    val safeTarget = min(DIORAMA_PARTICLE_AUDIO_SCALE_MAX, max(0.9, target))
    while (remaining > 0) {
        val step = min(1.0 / 240, remaining)
        val acc = (safeTarget - state.value) * 150 - state.velocity * 15
        state.velocity += acc * step
        state.value += state.velocity * step
        remaining -= step
    }
    state.value = min(DIORAMA_PARTICLE_AUDIO_SCALE_MAX, max(0.9, state.value))
    return state.value
}
