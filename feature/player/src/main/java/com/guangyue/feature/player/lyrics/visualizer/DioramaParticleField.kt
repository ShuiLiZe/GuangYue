package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// 镜台粒子场的逐帧控制：Folia DioramaParticleField.tsx 的 useFrame 部分照译（不含 React / three 对象）。
// 音频 → 几何只有一个交点：某频段越过触发阈值时往涟漪池写一个源，之后由着色器按自身时钟传播、衰减。
// 智能动感（beatFx 节拍源）安卓侧还没有，按 Folia 的 beatFxActive = false 分支走：三个频段都由起拍产生涟漪。

/** 涟漪池：每源 vec4（位置 + 出生时刻）与 vec4（强度、速度、包宽、波数）；各频段在自己的槽位里轮转。 */
internal class DioramaRipplePool {
    val sources = FloatArray(DIORAMA_RIPPLE_COUNT * 4)
    val shapes = FloatArray(DIORAMA_RIPPLE_COUNT * 4)
    val cursor = IntArray(DIORAMA_RIPPLE_BANDS.size)
    var spawned = 0
}

/** 隧道沿向坐标（半径单位）每行的长度，与 aWave.x 的烘焙空间一致。 */
internal const val DIORAMA_CORRIDOR_UNITS_PER_LINE = DIORAMA_STEP_DISTANCE / DIORAMA_PARTICLE_CORRIDOR_RADIUS

/** spawnRipple：确定性地在点云单位球面上 / 隧道读头前方墙面上写一个源，并围绕频段性格抖动高度、宽度、速度。 */
internal fun spawnDioramaRipple(pool: DioramaRipplePool, bandIndex: Int, strength: Double, now: Double, corridor: Boolean, readHeadAlong: Double) {
    val seed = dioramaHashSeed("diorama-ripple:${pool.spawned}")
    val preset = DIORAMA_RIPPLE_BANDS[bandIndex]
    val height = dioramaSeededUnit(seed + 1)
    val spread = dioramaSeededUnit(seed + 2)
    val pace = dioramaSeededUnit(seed + 3)
    val c = pool.cursor[bandIndex]
    val o = (bandIndex * DIORAMA_RIPPLE_SLOTS_PER_BAND + c) * 4
    if (corridor) {
        pool.sources[o] = (readHeadAlong + 0.4 + dioramaSeededUnit(seed + 4) * 2.2).toFloat()
        pool.sources[o + 1] = (dioramaSeededUnit(seed + 5) * PI * 2).toFloat()
        pool.sources[o + 2] = 0f
    } else {
        val up = dioramaSeededUnit(seed + 4) * 2 - 1
        val around = dioramaSeededUnit(seed + 5) * PI * 2
        val ring = sqrt(max(0.0, 1 - up * up))
        pool.sources[o] = (ring * cos(around)).toFloat()
        pool.sources[o + 1] = up.toFloat()
        pool.sources[o + 2] = (ring * sin(around)).toFloat()
    }
    pool.sources[o + 3] = now.toFloat()
    pool.shapes[o] = (preset.strength * strength * (0.7 + height * 0.6)).toFloat()
    pool.shapes[o + 1] = (preset.speed * (0.8 + pace * 0.45)).toFloat()
    pool.shapes[o + 2] = (preset.width * (0.78 + spread * 0.5)).toFloat()
    pool.shapes[o + 3] = preset.wavenumber.toFloat()
    pool.cursor[bandIndex] = (c + 1) % DIORAMA_RIPPLE_SLOTS_PER_BAND
    pool.spawned += 1
}

private const val FORMATION_SETTLE_SECONDS = 0.4
private const val CLOUD_SWELL = 0.34
private const val CORRIDOR_MAX_SWELL = 0.45
private const val CORRIDOR_QUIET_SWELL = 0.1
private const val MAX_WAVE = 0.85
internal const val DIORAMA_CLOUD_MAX_SWELL = MAX_WAVE * CLOUD_SWELL
internal const val DIORAMA_CORRIDOR_MAX_SWELL_REACH = MAX_WAVE * CORRIDOR_MAX_SWELL

/** 共享频段（0..255），与其它样式读同一份。 */
internal class DioramaAudioBands(val bass: Double, val lowMid: Double, val mid: Double, val treble: Double)

/** 每首歌 / 每轮重置的逐帧状态（resetKey 变了就新建一个）。 */
internal class DioramaParticleFieldState {
    val trackers = List(DIORAMA_RIPPLE_BANDS.size) { DioramaBandTracker() }
    var pool = DioramaRipplePool()
    var flowTime = 0.0
    var previousPlaybackTime: Double? = null
    val elastic = DioramaParticleElasticState()
    var formation = 1.0
    var lockedSeconds = FORMATION_SETTLE_SECONDS
}

internal class DioramaParticleFrameInput(
    val rawDelta: Double,
    val bands: DioramaAudioBands,
    /** 音频响应滑杆（0 = 不动；1 = 设计手感）。 */
    val audioLevel: Double,
    val playbackTime: Double,
    val corridor: Boolean,
    val readHeadLine: Int,
    val transitionActive: Boolean,
    val viewportHeight: Double,
    val waveNumberMax: Double,
    val targetColors: DioramaParticleColors,
)

/** useFrame 一帧：步进追踪器 / 涟漪池 / 弹簧 / 聚散，把结果写进两层材质的 uniform。 */
internal fun stepDioramaParticleField(state: DioramaParticleFieldState, input: DioramaParticleFrameInput, layers: List<DioramaParticleUniforms>) {
    val delta = min(input.rawDelta, 0.1)
    val bassTarget = min(1.0, input.bands.bass / 255)
    val midTarget = min(1.0, (input.bands.lowMid * 0.5 + input.bands.mid * 0.5) / 255)
    val trebleTarget = min(1.0, input.bands.treble / 255)
    val bass = stepDioramaBandTracker(state.trackers[0], bassTarget, delta)
    val mid = stepDioramaBandTracker(state.trackers[1], midTarget, delta)
    val treble = stepDioramaBandTracker(state.trackers[2], trebleTarget, delta)
    val bands = listOf(bass, mid, treble)
    val response = resolveDioramaParticleAudioResponse(bass, mid)
    val gain = max(0.0, input.audioLevel)

    val playbackTime = max(0.0, input.playbackTime)
    val prev = state.previousPlaybackTime
    val playbackDelta = if (prev == null) 0.0 else playbackTime - prev
    if (prev == null || playbackDelta < -0.05 || playbackDelta > 0.5) {
        state.flowTime = playbackTime * 0.28
        state.pool = DioramaRipplePool()
    } else if (playbackDelta > 0) {
        state.flowTime += playbackDelta * response.flowSpeed
    }
    state.previousPlaybackTime = playbackTime

    val pool = state.pool
    val readHeadAlong = input.readHeadLine * DIORAMA_CORRIDOR_UNITS_PER_LINE
    if (gain > 0.001) {
        bands.forEachIndexed { index, signal ->
            if (signal.onset) spawnDioramaRipple(pool, index, signal.transient, state.flowTime, input.corridor, readHeadAlong)
        }
    }

    val elasticPulse = stepDioramaParticleElasticResponse(
        state.elastic,
        resolveDioramaPulseTarget(response.clusterPulse, gain, input.corridor),
        delta,
    )
    val colorAmount = 1 - exp(-1.2 * delta)

    state.lockedSeconds = if (input.transitionActive) 0.0 else min(FORMATION_SETTLE_SECONDS, state.lockedSeconds + delta)
    val formationTarget = if (!input.transitionActive && state.lockedSeconds >= FORMATION_SETTLE_SECONDS) 1.0 else 0.0
    state.formation = stepDioramaEnvelope(state.formation, formationTarget, 1.5, 3.2, delta)

    val amplitude = gain * (if (input.corridor) min(CORRIDOR_MAX_SWELL, CORRIDOR_QUIET_SWELL + bass.sustained * 0.18 + mid.sustained * 0.08) else CLOUD_SWELL)
    val centroid = min(1.0, 0.2 + treble.sustained * 0.8)
    for (u in layers) {
        u.time = state.flowTime.toFloat()
        u.corridor = if (input.corridor) 1f else 0f
        u.amplitude = amplitude.toFloat()
        u.maxSwell = (if (input.corridor) DIORAMA_CORRIDOR_MAX_SWELL_REACH else DIORAMA_CLOUD_MAX_SWELL).toFloat()
        u.waveNumberMax = input.waveNumberMax.toFloat()
        u.detail = min(1.0, treble.sustained * gain).toFloat()
        pool.sources.copyInto(u.rippleSource)
        pool.shapes.copyInto(u.rippleShape)
        u.offsetGain = (mid.sustained * gain).toFloat()
        u.flow = if (input.corridor) 1f else 0f
        u.formation = state.formation.toFloat()
        u.scatter = if (input.corridor) 6f else 1.4f
        u.sizeBase = if (input.corridor) 0.072f else 0.05f
        u.sizeGain = if (input.corridor) 0.34f else 0.3f
        u.pulse = elasticPulse.toFloat()
        u.spectralCentroid = centroid.toFloat()
        u.viewportHeight = input.viewportHeight.toFloat()
        u.lerpColors(input.targetColors, colorAmount)
    }
}

/** 按模式建缓冲并算出该点阵能承载的波数上限（对应 useMemo built）。 */
internal fun buildDioramaParticleBuffer(
    corridor: Boolean,
    clusters: List<DioramaClusterAnchor>,
    spans: List<DioramaCorridorSpan>,
    density: Double,
): Pair<DioramaParticleGeometryData, Double> {
    val data = if (corridor) buildDioramaCorridorGeometryData(spans, density) else buildDioramaCloudGeometryData(clusters, density)
    return data to resolveDioramaWaveNumberMax(data.spacing)
}
