// 镜台（Folia visualizer/diorama）起步：cameraPath.ts 逐函数照译——蜿蜒路径与每行坐标系（buildDioramaPath）、
// 每行文字摆位（getDioramaTextPlacement）、运镜语言 13 种（getDioramaShot / resolveShotOffset）、手持呼吸（resolveCameraDrift）、
// 停留收束（resolveHoldSettle）、程序化阵列（buildFormation）、远近生灭（resolveShapeLifeOpacity）、SmoothDamp、画面半宽。
// 纯函数，不碰 GL；金样由 node 直接跑 Folia cameraPath.ts 生成（diorama_path_golden.txt）。
// 随机：seededUnit 用 StrictMath.sin（与 V8 的 fdlibm 一致，sin × 43758 放大后末位差也会翻转取值）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

internal const val DIORAMA_STEP_DISTANCE = 8.0
internal const val DIORAMA_MAX_YAW = 0.5
internal const val DIORAMA_MAX_PITCH = 0.32
internal const val DIORAMA_HERO_DISTANCE = 5.2
internal const val DIORAMA_CAMERA_LIFT = 0.5
internal const val DIORAMA_SAFE_FRAME_FRACTION = 0.92
internal const val DIORAMA_SWAY_AMP = 0.4
internal const val DIORAMA_LIFT_AMP = 0.4
internal const val DIORAMA_DIST_AMP = 0.5
internal const val DIORAMA_SNAP_DISTANCE = 24.0
internal const val DIORAMA_TEXT_OFFSET_R = 1.8
internal const val DIORAMA_TEXT_OFFSET_U = 1.2
internal const val DIORAMA_TEXT_ROLL = 0.2
internal const val DIORAMA_TEXT_YAW = 0.16
internal const val DIORAMA_TEXT_LOOK = 1.1
internal const val DIORAMA_SHAPE_FADE_IN_START = 20.0
internal const val DIORAMA_SHAPE_FADE_IN_END = 27.0
internal const val DIORAMA_SHAPE_DISSOLVE_START = 3.0
internal const val DIORAMA_SHAPE_DISSOLVE_END = 1.2
internal const val DIORAMA_PARTICLE_AUDIO_SCALE_MAX = 1.44

private fun dClamp01(v: Double) = min(1.0, max(0.0, v))
private fun dClamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
private fun hypot3(a: Double, b: Double, c: Double) = sqrt(a * a + b * b + c * c)

internal data class DioramaVec(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: DioramaVec) = DioramaVec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: DioramaVec) = DioramaVec(x - o.x, y - o.y, z - o.z)
    fun cross(o: DioramaVec) = DioramaVec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): DioramaVec { val l = hypot3(x, y, z).let { if (it == 0.0) 1.0 else it }; return DioramaVec(x / l, y / l, z / l) }
}

/** seededUnit：sin(seed × 12.9898) × 43758.5453 的小数部分。 */
internal fun dioramaSeededUnit(seed: Double): Double {
    val x = StrictMath.sin(seed * 12.9898) * 43758.5453
    return x - floor(x)
}

/** hashSeed：字符串按 (h × 31 + 码元) mod 100000；空串为 1。 */
internal fun dioramaHashSeed(seed: String?): Double {
    if (seed.isNullOrEmpty()) return 1.0
    var hash = 0L
    for (ch in seed) hash = (hash * 31 + ch.code) % 100000
    return if (hash == 0L) 1.0 else hash.toDouble()
}

/** Unity 风格临界阻尼 SmoothDamp（一维）。返回 (value, velocity)。 */
internal fun dioramaSmoothDamp(current: Double, target: Double, velocity: Double, smoothTime: Double, delta: Double): Pair<Double, Double> {
    val t = max(0.0001, smoothTime)
    val omega = 2 / t
    val x = omega * delta
    val exp = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x)
    val change = current - target
    val temp = (velocity + omega * change) * delta
    val newVelocity = (velocity - omega * temp) * exp
    return (target + (change + temp) * exp) to newVelocity
}

/** 透视相机在 distance 处可见的半宽（fov 是竖直视角）。 */
internal fun dioramaFrameHalfWidth(distance: Double, verticalFovDeg: Double, aspect: Double) = distance * tan(verticalFovDeg * PI / 180 / 2) * aspect

internal enum class DioramaSubMode { CALM, NORMAL, CHAOTIC }

internal data class DioramaMotionParams(val moveScale: Double, val driftScale: Double, val smoothTime: Double, val audioLevel: Double, val weaveScale: Double, val subMode: DioramaSubMode)

/** resolveDioramaMotionParams：主题动效强度 → 子模式预设，再乘滑杆（cameraSpeed / motionAmount / audioReactivity）。 */
internal fun resolveDioramaMotionParams(cameraSpeed: Double = 1.0, motionAmount: Double = 1.0, audioReactivity: Double = 1.0, intensity: String? = null): DioramaMotionParams {
    val mode = when (intensity) { "calm" -> DioramaSubMode.CALM; "chaotic" -> DioramaSubMode.CHAOTIC; else -> DioramaSubMode.NORMAL }
    val p = when (mode) {
        DioramaSubMode.NORMAL -> doubleArrayOf(1.0, 0.62, 0.42, 1.0, 1.0)
        DioramaSubMode.CALM -> doubleArrayOf(0.6, 0.4, 0.56, 0.5, 0.5)
        DioramaSubMode.CHAOTIC -> doubleArrayOf(1.35, 0.85, 0.33, 1.35, 1.35)
    }
    val motion = dClamp(motionAmount, 0.4, 1.6); val speed = dClamp(cameraSpeed, 0.55, 1.85); val audio = dClamp(audioReactivity, 0.0, 1.5)
    return DioramaMotionParams(p[0] * motion, p[1] * motion, p[2] / speed, p[3] * audio, p[4] * motion, mode)
}

// ---- 蜿蜒路径与每行坐标系 ----

internal data class DioramaFrame(val position: DioramaVec, val forward: DioramaVec, val right: DioramaVec, val up: DioramaVec)

private val DIORAMA_WORLD_UP = DioramaVec(0.0, 1.0, 0.0)
internal val DIORAMA_DEFAULT_FRAME = DioramaFrame(DioramaVec(0.0, 0.0, 0.0), DioramaVec(0.0, 0.0, -1.0), DioramaVec(1.0, 0.0, 0.0), DioramaVec(0.0, 1.0, 0.0))

/** buildDioramaPath：乌龟每行前进一步，航向按带种子的正弦偏航 / 俯仰；每行坐标系 right = forward × 世界上，up = right × forward。 */
internal fun buildDioramaPath(count: Int, seed: String?): List<DioramaFrame> {
    val base = dioramaHashSeed(seed)
    val n = max(count, 1)
    val positions = ArrayList<DioramaVec>()
    var pos = DioramaVec(0.0, 0.0, 0.0)
    for (i in 0..n) {
        positions += pos
        val yaw = DIORAMA_MAX_YAW * (sin(i * 0.23 + base * 0.017) * 0.62 + sin(i * 0.11 + base * 0.041 + 1.3) * 0.38)
        val pitch = DIORAMA_MAX_PITCH * sin(i * 0.17 + base * 0.029 + 0.7)
        val cp = cos(pitch)
        val dir = DioramaVec(sin(yaw) * cp, sin(pitch), -cos(yaw) * cp)
        pos = DioramaVec(pos.x + dir.x * DIORAMA_STEP_DISTANCE, pos.y + dir.y * DIORAMA_STEP_DISTANCE, pos.z + dir.z * DIORAMA_STEP_DISTANCE)
    }
    return (0 until n).map { i ->
        val forward = (positions[i + 1] - positions[i]).norm()
        val right = forward.cross(DIORAMA_WORLD_UP).norm()
        val up = right.cross(forward).norm()
        DioramaFrame(positions[i], forward, right, up)
    }
}

internal fun dioramaGetFrame(frames: List<DioramaFrame>, index: Int): DioramaFrame =
    if (frames.isEmpty()) DIORAMA_DEFAULT_FRAME else frames[index.coerceIn(0, frames.size - 1)]

internal fun dioramaTranslateFrames(frames: List<DioramaFrame>, offset: DioramaVec) = frames.map { it.copy(position = it.position + offset) }

/** composeLocal：坐标系里 (right, up, depth) → 世界点；depth 正为沿路往前（远离尾随的镜头）。 */
internal fun dioramaComposeLocal(f: DioramaFrame, right: Double, up: Double, depth: Double) = DioramaVec(
    f.position.x + f.right.x * right + f.up.x * up + f.forward.x * depth,
    f.position.y + f.right.y * right + f.up.y * up + f.forward.y * depth,
    f.position.z + f.right.z * right + f.up.z * up + f.forward.z * depth,
)

/** resolveReadHeadTruck：镜头跟着唱到的字横移（tanh 平滑饱和）。 */
internal fun resolveDioramaReadHeadTruck(wordProgress: Double, trackWidth: Double, visibleHalfWidth: Double): Double {
    if (trackWidth <= 0) return 0.0
    val wordOffset = (dClamp01(wordProgress) - 0.5) * trackWidth
    val allowed = max(abs(visibleHalfWidth - trackWidth / 2), 0.001)
    return allowed * tanh(wordOffset / allowed)
}

// ---- 每行文字摆位 ----

internal data class DioramaTextPlacement(val offsetR: Double, val offsetU: Double, val scale: Double, val roll: Double, val yaw: Double, val lookR: Double)

internal fun getDioramaTextPlacement(lineIndex: Int, seed: String?, weave: Double = 1.0): DioramaTextPlacement {
    val base = dioramaHashSeed(seed)
    val i = max(lineIndex, 0)
    fun u(k: Int) = dioramaSeededUnit(base + i * 11 + k)
    val jitterR = (u(2) - 0.5) * 0.6
    val jitterU = (u(4) - 0.5) * 0.6
    val wanderR = sin(i * 0.47 + base * 0.019) * 0.7 + jitterR
    val wanderU = sin(i * 0.29 + base * 0.027 + 0.9) * 0.7 + jitterU
    return DioramaTextPlacement(
        offsetR = wanderR * DIORAMA_TEXT_OFFSET_R * weave,
        offsetU = wanderU * DIORAMA_TEXT_OFFSET_U * weave,
        scale = 0.82 + u(5) * 0.46,
        roll = (u(6) - 0.5) * 2 * DIORAMA_TEXT_ROLL * weave,
        yaw = (u(7) - 0.5) * 2 * DIORAMA_TEXT_YAW * weave,
        lookR = (u(8) - 0.5) * 2 * DIORAMA_TEXT_LOOK * weave,
    )
}

// ---- 运镜语言 ----

internal enum class DioramaShotKind(val id: String) {
    PUSH_IN("pushIn"), PULL_BACK("pullBack"), ORBIT("orbit"), TRACK("track"), CRANE("crane"), HOLD("hold"), SWELL("swell"),
    SPIRAL("spiral"), PENDULUM("pendulum"), FLYBY("flyby"), ARC("arc"), FLOAT("float"), GLIDE("glide"),
}

private val SHOT_KINDS = DioramaShotKind.values().toList()

private fun easeInOut(p: Double): Double { val c = dClamp01(p); return c * c * (3 - 2 * c) }

private val CHORUS_PART = Regex("chorus|hook|refrain|drop", RegexOption.IGNORE_CASE)

/** 这一行运镜往哪边（orbit 往哪边绕、flyby 从哪边过）：运镜与阵列共用。 */
internal fun dioramaShotDir(lineIndex: Int, seed: String?): Int = if (dioramaSeededUnit(dioramaHashSeed(seed) + lineIndex * 7 + 3) < 0.5) -1 else 1

private fun computeShotWeights(lineIndex: Int, lines: List<Line>, subMode: DioramaSubMode): DoubleArray {
    val line = lines.getOrNull(lineIndex)
    val prev = if (lineIndex > 0) lines.getOrNull(lineIndex - 1) else null
    val wordCount = line?.words?.size ?: 0
    val isChorus = line?.isChorus == true || CHORUS_PART.containsMatchIn(line?.songPart ?: "")
    val sectionStart = line != null && prev != null && (line.blockIndex != prev.blockIndex || (line.songPart ?: "") != (prev.songPart ?: ""))
    var maxNote = 0.0; var sum = 0.0; var noteCount = 0
    for (w in line?.words.orEmpty()) { val d = w.endTime - w.startTime; if (d > 0) { maxNote = max(maxNote, d); sum += d; noteCount++ } }
    val avgNote = if (noteCount > 0) sum / noteCount else 0.0
    val hasLongNote = maxNote >= 1.3
    val staccato = noteCount >= 4 && avgNote > 0 && avgNote <= 0.34
    // 顺序同 SHOT_KINDS：pushIn pullBack orbit track crane hold swell spiral pendulum flyby arc float glide
    val w = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.85, 0.9, 1.0, 0.9, 1.0, 0.9, 1.0)
    val PI_ = 0; val PB = 1; val OR = 2; val TR = 3; val CR = 4; val HO = 5; val SW = 6; val SP = 7; val PE = 8; val FL = 9; val AR = 10; val FT = 11; val GL = 12
    if (isChorus) {
        w[OR] += 2.4; w[PI_] += 1.8; w[SP] += 1.2; w[CR] += 0.8; w[FL] += 0.6; w[AR] += 1.0; w[GL] += 0.6; w[HO] -= 0.6; w[TR] -= 0.2
    } else {
        w[TR] += 1.1; w[CR] += 0.9; w[HO] += 0.9; w[PE] += 0.8; w[FT] += 0.8; w[GL] += 0.6; w[AR] += 0.4
    }
    if (wordCount >= 8) { w[TR] += 1.6; w[PB] += 1.0; w[OR] -= 0.4; w[PI_] -= 0.3 }
    else if (wordCount <= 3) { w[PI_] += 1.4; w[OR] += 0.9; w[HO] += 0.5; w[TR] -= 0.8 }
    if (sectionStart) { w[PB] += 2.2; w[CR] += 1.4; w[GL] += 1.0; w[PI_] -= 0.4 }
    if (hasLongNote) { w[SW] += 2.4; w[SP] += 1.0; w[FT] += 1.2; w[PE] += 0.6; w[OR] += 0.8; w[AR] += 0.6; w[HO] += 0.3; w[TR] -= 0.5 }
    if (staccato) { w[TR] += 1.4; w[FL] += 1.5; w[PI_] += 0.8; w[SW] -= 1.6; w[HO] -= 0.8; w[CR] -= 0.4 }
    if (subMode == DioramaSubMode.CALM) {
        w[HO] += 1.4; w[FT] += 1.3; w[SW] += 0.9; w[GL] += 0.8; w[AR] += 0.5; w[CR] += 0.4
        w[OR] *= 0.4; w[SP] *= 0.35; w[FL] *= 0.3; w[PE] *= 0.5; w[PI_] *= 0.7
    } else if (subMode == DioramaSubMode.CHAOTIC) {
        w[OR] += 1.3; w[SP] += 1.3; w[FL] += 1.2; w[PE] += 1.0; w[PI_] += 0.8; w[AR] += 0.6; w[CR] += 0.4
        w[HO] *= 0.35; w[FT] *= 0.55; w[SW] *= 0.7
    }
    for (k in w.indices) w[k] = max(0.05, w[k])
    return w
}

private fun weightedPickShot(w: DoubleArray, u: Double): DioramaShotKind {
    // JS 用 reduce 从 0 起累加（顺序同 SHOT_KINDS），这里同序累加，保证同一浮点结果。
    var total = 0.0
    for (x in w) total += x
    var acc = dClamp01(u) * total
    for (k in SHOT_KINDS.indices) { acc -= w[k]; if (acc <= 0) return SHOT_KINDS[k] }
    return DioramaShotKind.HOLD
}

private fun basePickShot(lineIndex: Int, lines: List<Line>, seed: String?, subMode: DioramaSubMode): DioramaShotKind {
    if (lines.getOrNull(lineIndex) == null) return DioramaShotKind.HOLD
    val u = dioramaSeededUnit(dioramaHashSeed(seed) + lineIndex * 101 + 7)
    return weightedPickShot(computeShotWeights(lineIndex, lines, subMode), u)
}

/** getDioramaShot：按行的性格加权挑运镜；与前两行撞了就换一个（只看前两行的基础挑选，O(1)）。 */
internal fun getDioramaShot(lineIndex: Int, lines: List<Line>, seed: String?, subMode: DioramaSubMode = DioramaSubMode.NORMAL): DioramaShotKind {
    val pick = basePickShot(lineIndex, lines, seed, subMode)
    val prev1 = if (lineIndex > 0) basePickShot(lineIndex - 1, lines, seed, subMode) else null
    val prev2 = if (lineIndex > 1) basePickShot(lineIndex - 2, lines, seed, subMode) else null
    if (pick == prev1 || pick == prev2) {
        val others = SHOT_KINDS.filter { it != pick && it != prev1 && it != prev2 }
        val pool = if (others.isNotEmpty()) others else SHOT_KINDS.filter { it != pick }
        val alt = dioramaSeededUnit(dioramaHashSeed(seed) + lineIndex * 53 + 19)
        return pool[floor(dClamp01(alt) * pool.size).toInt() % pool.size]
    }
    return pick
}

internal data class DioramaShotOffset(val right: Double, val up: Double, val back: Double)

/** resolveShotOffset：运镜 + 行内进度 → 镜头在行坐标系里的偏移（right / up / back），偏离中性位的幅度 × moveScale。 */
internal fun resolveDioramaShotOffset(
    kind: DioramaShotKind, progress: Double, wordProgress: Double, hero: Double, lift: Double, seed: String?, lineIndex: Int, moveScale: Double,
): DioramaShotOffset {
    val e = easeInOut(progress)
    val dir = dioramaShotDir(lineIndex, seed).toDouble()
    var right = 0.0; var up = lift; var back = hero
    when (kind) {
        DioramaShotKind.PUSH_IN -> { back = hero * (1.5 - 0.8 * e); up = lift * (1.2 - 0.5 * e) + 0.15 }
        DioramaShotKind.PULL_BACK -> { back = hero * (0.85 + 1.15 * e); up = lift + 1.7 * e }
        DioramaShotKind.ORBIT -> { val th = dir * 0.55 * (2 * e - 1); right = hero * sin(th); back = hero * cos(th); up = lift + 0.4 }
        DioramaShotKind.TRACK -> { right = dir * hero * 0.34 * sin(PI * dClamp01(wordProgress)); back = hero * 1.06; up = lift + 0.12 }
        DioramaShotKind.CRANE -> { val from = if (dir > 0) -1.5 else 2.4; val to = if (dir > 0) 2.4 else -1.4; up = from + (to - from) * e; back = hero * 1.05 }
        DioramaShotKind.SWELL -> { val th = dir * 0.4 * e; right = hero * sin(th) * 1.1; back = hero * (1.0 + 0.35 * e); up = lift + 1.2 * e + 0.3 }
        DioramaShotKind.SPIRAL -> { val th = dir * 0.7 * (2 * e - 1); right = hero * sin(th) * 0.85; back = hero * (1.05 - 0.1 * e); up = lift - 0.8 + 2.4 * e }
        DioramaShotKind.PENDULUM -> { right = dir * hero * 0.45 * (2 * e - 1); up = lift + 0.9 - 0.9 * sin(PI * e); back = hero * 1.02 }
        DioramaShotKind.FLYBY -> { right = dir * hero * (0.7 - 1.4 * e); back = hero * (0.78 + 0.12 * sin(PI * e)); up = lift + 0.05 }
        DioramaShotKind.ARC -> { val th = dir * 0.5 * sin(PI * e); right = hero * sin(th) * 1.3; back = hero * (1.05 + 0.12 * (1 - cos(th))); up = lift + 0.5 * sin(PI * e) }
        DioramaShotKind.FLOAT -> { right = dir * hero * 0.16 * sin(e * PI * 2); up = lift + 0.6 + 0.5 * sin(e * PI * 2 + 1.0); back = hero * (1.05 + 0.06 * sin(e * PI)) }
        DioramaShotKind.GLIDE -> { right = dir * hero * 0.22 * e; up = lift + 1.4 * e; back = hero * (1.15 - 0.25 * e) }
        DioramaShotKind.HOLD -> { up = lift + 0.3 * sin(progress * PI); back = hero }
    }
    val m = moveScale
    return DioramaShotOffset(right * m, lift + (up - lift) * m, hero + (back - hero) * m)
}

/** resolveHoldSettle：行唱完停留超过 3 s 后，5 s 内把构图（运镜偏离、跟字横移、三分法偏移）收回中性位。 */
internal fun resolveDioramaHoldSettle(secondsHeld: Double): Double {
    val t = dClamp01((secondsHeld - 3) / 5)
    return 1 - t * t * (3 - 2 * t)
}

internal data class DioramaCameraDrift(val swayX: Double, val swayY: Double, val lift: Double, val dist: Double)

internal fun resolveDioramaCameraDrift(time: Double, seed: String?, scale: Double): DioramaCameraDrift {
    val ph = dioramaHashSeed(seed) * 0.017
    return DioramaCameraDrift(
        swayX = (sin(time * 0.11 + ph) * 0.6 + sin(time * 0.047 + ph * 1.7) * 0.4) * DIORAMA_SWAY_AMP * scale,
        swayY = sin(time * 0.09 + ph * 0.7) * DIORAMA_SWAY_AMP * 0.5 * scale,
        lift = (sin(time * 0.13 + ph * 1.3) * 0.6 + sin(time * 0.061 + ph * 2.1) * 0.4) * DIORAMA_LIFT_AMP * scale,
        dist = sin(time * 0.05 + ph * 1.1) * DIORAMA_DIST_AMP * scale,
    )
}

// ---- 程序化阵列 ----

internal enum class DioramaShapeKind(val id: String) { BOX("box"), SPHERE("sphere"), CONE("cone"), TORUS("torus") }

internal data class DioramaShapePlacement(
    val kind: DioramaShapeKind, val position: DioramaVec, val scale: Double, val stretchY: Double, val upright: Boolean,
    val spinSpeed: Double, val colorSlot: Int, val far: Boolean,
)

private class FormationSpec(val kind: DioramaShapeKind, var r: Double, var u: Double, var d: Double, val scale: Double, val stretchY: Double, val upright: Boolean, val colorSlot: Int?)

private fun clearanceRadius(s: FormationSpec): Double {
    val family = when (s.kind) { DioramaShapeKind.BOX -> 0.5; DioramaShapeKind.SPHERE -> 0.74; DioramaShapeKind.CONE -> 0.68; DioramaShapeKind.TORUS -> 0.9 }
    return s.scale * max(family, s.stretchY * 0.55)
}

private fun separateSpecs(specs: List<FormationSpec>) {
    repeat(6) {
        for (a in specs.indices) for (b in a + 1 until specs.size) {
            val A = specs[a]; val B = specs[b]
            val minDist = (clearanceRadius(A) + clearanceRadius(B)) * 1.3
            var dr = B.r - A.r; var du = B.u - A.u; var dd = B.d - A.d
            var dist = hypot3(dr, du, dd)
            if (dist >= minDist) continue
            if (dist < 0.001) { dr = 1.0; du = 0.5; dd = 0.25; dist = hypot3(dr, du, dd) }
            val push = (minDist - dist) / 2 / dist
            A.r -= dr * push; A.u -= du * push; A.d -= dd * push
            B.r += dr * push; B.u += du * push; B.d += dd * push
        }
    }
    specs.forEach { it.d = max(it.d, 1.0) }
}

private const val TEXT_CLEAR_LATERAL = 5.2
private const val TEXT_CLEAR_VERTICAL = 1.4

private fun enforceTextClearance(specs: List<FormationSpec>) {
    specs.forEachIndexed { i, s ->
        val radius = clearanceRadius(s) * DIORAMA_PARTICLE_AUDIO_SCALE_MAX
        if (abs(s.u) - radius < TEXT_CLEAR_VERTICAL && abs(s.r) - radius < TEXT_CLEAR_LATERAL) {
            val side = if (s.r == 0.0) (if (i % 2 == 0) 1.0 else -1.0) else sign(s.r)
            s.r = side * (TEXT_CLEAR_LATERAL + radius + 0.35)
        }
    }
}

private fun enforceRailClearance(specs: List<FormationSpec>) {
    specs.forEachIndexed { i, s ->
        val radius = clearanceRadius(s) * DIORAMA_PARTICLE_AUDIO_SCALE_MAX
        val minimum = 2.8 + radius
        val distance = sqrt(s.r * s.r + s.u * s.u)
        if (distance >= minimum) return@forEachIndexed
        val fallback = (if (i % 2 == 0) 1 else -1) * PI * 0.16
        val dirR = if (distance > 0.001) s.r / distance else cos(fallback)
        val dirU = if (distance > 0.001) s.u / distance else sin(fallback)
        s.r = dirR * minimum; s.u = dirU * minimum
    }
}

/** buildFormation：按运镜长出一组几何（orbit 环、pushIn 门柱、track 柱廊、crane 拱、spiral 螺旋……），分开重叠、让开文字与镜头轨道。 */
internal fun buildDioramaFormation(
    lineIndex: Int, seed: String?, shot: DioramaShotKind, frame: DioramaFrame, placement: DioramaTextPlacement, volumeScale: Double = 1.0,
): List<DioramaShapePlacement> {
    val salt = dioramaHashSeed(seed) + lineIndex * 97
    fun rnd(k: Int) = dioramaSeededUnit(salt + k)
    val dir = dioramaShotDir(lineIndex, seed).toDouble()
    val specs = ArrayList<FormationSpec>()
    val vs = min(1.6, max(0.65, volumeScale))
    fun add(kind: DioramaShapeKind, r: Double, u: Double, d: Double, scale: Double, stretchY: Double = 1.0, colorSlot: Int? = null) {
        specs += FormationSpec(kind, r, u, d, scale * vs, stretchY, stretchY > 1.2, colorSlot)
    }
    fun addPair(kind: DioramaShapeKind, lateral: Double, u: Double, d: Double, scale: Double, stretchY: Double = 1.0, colorSlot: Int = 0) {
        add(kind, -abs(lateral), u, d, scale, stretchY, colorSlot)
        add(kind, abs(lateral), u, d, scale, stretchY, colorSlot)
    }
    val B = DioramaShapeKind.BOX; val S = DioramaShapeKind.SPHERE; val C = DioramaShapeKind.CONE; val T = DioramaShapeKind.TORUS
    when (shot) {
        DioramaShotKind.ORBIT -> {
            val count = 6; val radius = 4.4 + rnd(1) * 0.6
            for (j in 0 until count) {
                val a = j.toDouble() / count * PI * 2 + rnd(2) * 0.6
                add(if (j == 0) T else S, cos(a) * radius, sin(a) * radius, 0.6 + rnd(10 + j) * 1.4, 0.42 + rnd(20 + j) * 0.32)
            }
        }
        DioramaShotKind.PUSH_IN -> {
            val gate = 4.3 + rnd(1) * 0.5
            listOf(-1, 1).forEachIndexed { idx, side ->
                add(B, side * gate, -0.6, 0.5 + rnd(idx) * 0.5, 0.55 + rnd(2 + idx) * 0.2, 2.8 + rnd(6 + idx) * 0.8)
                add(B, side * (gate + 1.6), 0.6, 4.5 + rnd(3 + idx) * 2, 0.7 + rnd(4 + idx) * 0.3, 3.4)
            }
        }
        DioramaShotKind.TRACK -> {
            for (si in 0 until 2) {
                val side = if (si == 0) -1 else 1
                for (j in 0 until 3) add(B, side * (4.2 + rnd(si * 5 + j) * 0.4), -0.4 + (rnd(si * 7 + j) - 0.5) * 0.6, 0.5 + j * 2.4, 0.5 + rnd(si * 3 + j) * 0.15, 2.6 + rnd(si + j) * 0.6)
            }
        }
        DioramaShotKind.CRANE -> {
            val count = 5; val radius = 3.6 + rnd(1) * 0.5
            for (j in 0 until count) {
                val a = PI * (0.15 + 0.7 * (j.toDouble() / (count - 1)))
                add(B, cos(a) * radius, abs(sin(a)) * radius + 1.2, 0.6 + rnd(30 + j) * 0.8, 0.45 + rnd(40 + j) * 0.25, 1.4)
            }
        }
        DioramaShotKind.SWELL -> {
            val lat = 4.2 + rnd(2) * 0.5
            for (j in 0 until 2) addPair(B, lat, -1.5 + j * 2.1, 1.0 + j * 1.2, 0.48 - j * 0.06, 1.45, j % 2)
            addPair(S, lat, 2.65, 3.4, 0.5, 1.0, 0)
        }
        DioramaShotKind.SPIRAL -> {
            val count = 7; val radius = 4.3
            for (j in 0 until count) {
                val frac = j.toDouble() / (count - 1)
                val a = dir * frac * PI * 2.2 + rnd(1) * 0.5
                add(S, cos(a) * radius, -2.2 + frac * 5.2, 0.5 + frac * 2.2, 0.32 + rnd(20 + j) * 0.2)
            }
        }
        DioramaShotKind.PENDULUM -> {
            for (j in 0 until 4) add(B, -3.9 + j * 2.6 + (rnd(j) - 0.5) * 0.5, 3.0 + rnd(j * 2) * 1.2, 0.5 + rnd(j * 3) * 1.2, 0.3 + rnd(j * 4) * 0.12, 2.2 + rnd(j * 5) * 0.8)
        }
        DioramaShotKind.FLYBY -> {
            for (j in 0 until 3) addPair(B, 4.25 + rnd(j) * 0.25, -0.45 + j * 0.45, 1.0 + j * 2.1, 0.42 + rnd(j * 3) * 0.1, 2.2, j % 2)
        }
        DioramaShotKind.PULL_BACK -> {
            addPair(S, 5.8 + rnd(9) * 0.5, 1.25, 6.8 + rnd(11), 1.25 + rnd(12) * 0.25, 1.0, 0)
            for (j in 0 until 2) addPair(if (j == 0) B else C, 4.4 + j * 1.15, -1.4 + j * 2.2, 2.2 + j * 2.1, 0.58 + rnd(j * 4) * 0.18, if (j == 0) 1.5 else 1.0, 1)
        }
        DioramaShotKind.ARC -> {
            val count = 5; val radius = 5.2 + rnd(1) * 0.6
            for (j in 0 until count) {
                val a = (-0.5 + j.toDouble() / (count - 1)) * 1.5 * dir
                add(if (j == count / 2) T else S, sin(a) * radius, 0.3 + cos(a) * 0.7, 1.0 + rnd(20 + j) * 1.6, 0.45 + rnd(30 + j) * 0.32)
            }
        }
        DioramaShotKind.FLOAT -> {
            for (j in 0 until 3) addPair(S, 4.1 + j * 0.85, -1.2 + j * 1.35, 1.2 + j * 1.55, 0.42 + rnd(j * 4) * 0.22, 1.0, j % 2)
        }
        DioramaShotKind.GLIDE -> {
            for (j in 0 until 3) addPair(B, 4.25 + rnd(j) * 0.3, -1.5 + j * 1.7, 1.0 + j * 1.7, 0.46 + rnd(j * 2) * 0.12, 1.85, j % 2)
        }
        DioramaShotKind.HOLD -> {
            addPair(T, 5.6, 1.55, 7.2 + rnd(1), 1.05 + rnd(2) * 0.2, 1.0, 0)
            addPair(S, 4.45, -1.1, 2.1 + rnd(4), 0.58 + rnd(5) * 0.16, 1.0, 1)
        }
    }
    enforceRailClearance(specs); enforceTextClearance(specs); separateSpecs(specs); enforceRailClearance(specs); enforceTextClearance(specs)
    return specs.mapIndexed { j, s ->
        DioramaShapePlacement(
            s.kind, dioramaComposeLocal(frame, placement.offsetR + s.r, placement.offsetU + s.u, s.d), s.scale, s.stretchY, s.upright,
            0.04 + rnd(j * 13 + 5) * 0.1, s.colorSlot ?: (j % 2), s.d > 3.5,
        )
    }
}

/** resolveShapeLifeOpacity：远处雾里淡入（27 → 20），镜头逼近时化掉（3 → 1.2），两头都 smoothstep。 */
internal fun resolveDioramaShapeLifeOpacity(distance: Double): Double {
    val farT = dClamp01((DIORAMA_SHAPE_FADE_IN_END - distance) / (DIORAMA_SHAPE_FADE_IN_END - DIORAMA_SHAPE_FADE_IN_START))
    val nearT = dClamp01((distance - DIORAMA_SHAPE_DISSOLVE_END) / (DIORAMA_SHAPE_DISSOLVE_START - DIORAMA_SHAPE_DISSOLVE_END))
    return farT * farT * (3 - 2 * farT) * (nearT * nearT * (3 - 2 * nearT))
}
