package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTiming
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// 镜台镜头：Folia CameraRig.tsx 的 useFrame 逐行照译（three 的 Quaternion.slerp / Matrix4.lookAt / Object3D.rotateZ 按 0.185.1 源码手写）。
// 镜头只读路径与歌词时间，不读音频；每帧输出相机位置与朝向四元数。

private val ALIGN_SHOT_CEILING = mapOf(
    DioramaShotKind.HOLD to 0.9, DioramaShotKind.PUSH_IN to 0.85, DioramaShotKind.TRACK to 0.85, DioramaShotKind.SWELL to 0.8,
    DioramaShotKind.FLOAT to 0.78, DioramaShotKind.GLIDE to 0.72, DioramaShotKind.CRANE to 0.7, DioramaShotKind.PULL_BACK to 0.7,
    DioramaShotKind.ARC to 0.62, DioramaShotKind.PENDULUM to 0.6, DioramaShotKind.ORBIT to 0.55, DioramaShotKind.SPIRAL to 0.5,
    DioramaShotKind.FLYBY to 0.5,
)
private const val ALIGN_LEAD_IN = 0.6
private const val ALIGN_SETTLE = 0.35
private const val ALIGN_RELEASE = 1.0
private const val ALIGN_RATE = 1.2
private const val ALIGN_TURN_RATE = 2.6
private const val FRAME_KEEP_FRACTION = 0.7
private const val FLIGHT_SMOOTH_TIME = 1.1
private const val FLIGHT_ARC_FRAC = 0.16
private const val ORIENT_FOLLOW_RATE = 4.2

private fun rigClamp01(v: Double) = min(1.0, max(0.0, v))
private fun rigSmoothstep01(t: Double): Double { val c = rigClamp01(t); return c * c * (3 - 2 * c) }

/** three Quaternion.slerp（0.185.1：点积 < 0 取反；< 0.9995 走球面，否则线性后归一）。 */
internal fun dioramaSlerp(a: DioramaQuat, b: DioramaQuat, tIn: Double): DioramaQuat {
    var x = b.x; var y = b.y; var z = b.z; var w = b.w
    var dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
    if (dot < 0) { x = -x; y = -y; z = -z; w = -w; dot = -dot }
    var s = 1 - tIn
    var t = tIn
    return if (dot < 0.9995) {
        val theta = acos(dot)
        val sn = sin(theta)
        s = sin(s * theta) / sn
        t = sin(t * theta) / sn
        DioramaQuat(a.x * s + x * t, a.y * s + y * t, a.z * s + z * t, a.w * s + w * t)
    } else {
        val q = DioramaQuat(a.x * s + x * t, a.y * s + y * t, a.z * s + z * t, a.w * s + w * t)
        val l = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
        if (l == 0.0) DioramaQuat(0.0, 0.0, 0.0, 1.0) else DioramaQuat(q.x / l, q.y / l, q.z / l, q.w / l)
    }
}

private fun vlen(v: DioramaVec) = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
private fun vnorm(v: DioramaVec): DioramaVec { val l = vlen(v); return if (l == 0.0) v else DioramaVec(v.x / l, v.y / l, v.z / l) }
private fun vcross(a: DioramaVec, b: DioramaVec) = DioramaVec(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/** 相机 Object3D.lookAt（eye → target，up），即 Matrix4.lookAt + setFromRotationMatrix。 */
internal fun dioramaCameraLookAt(eye: DioramaVec, target: DioramaVec, up: DioramaVec): DioramaQuat {
    var z = eye - target
    if (z.x * z.x + z.y * z.y + z.z * z.z == 0.0) z = DioramaVec(z.x, z.y, 1.0)
    z = vnorm(z)
    var x = vcross(up, z)
    if (x.x * x.x + x.y * x.y + x.z * x.z == 0.0) {
        z = if (kotlin.math.abs(up.z) == 1.0) DioramaVec(z.x + 0.0001, z.y, z.z) else DioramaVec(z.x, z.y, z.z + 0.0001)
        z = vnorm(z)
        x = vcross(up, z)
    }
    x = vnorm(x)
    val y = vcross(z, x)
    return DioramaQuat.fromBasis(x, y, z)
}

/** Vector3.applyQuaternion。 */
internal fun dioramaApplyQuat(v: DioramaVec, q: DioramaQuat): DioramaVec {
    val tx = 2 * (q.y * v.z - q.z * v.y); val ty = 2 * (q.z * v.x - q.x * v.z); val tz = 2 * (q.x * v.y - q.y * v.x)
    return DioramaVec(
        v.x + q.w * tx + q.y * tz - q.z * ty,
        v.y + q.w * ty + q.z * tx - q.x * tz,
        v.z + q.w * tz + q.x * ty - q.y * tx,
    )
}

private fun angleTo(a: DioramaVec, b: DioramaVec): Double {
    val den = sqrt((a.x * a.x + a.y * a.y + a.z * a.z) * (b.x * b.x + b.y * b.y + b.z * b.z))
    if (den == 0.0) return PI / 2
    return acos(min(1.0, max(-1.0, (a.x * b.x + a.y * b.y + a.z * b.z) / den)))
}

private fun resolveWordProgress(line: Line, timeline: List<GraphemeTiming>, now: Double): Double {
    if (timeline.isEmpty()) return 0.0
    val renderEnd = RenderHints.getLineRenderEndTime(line)
    if (now >= renderEnd) return 1.0
    if (now <= line.startTime) return 0.0
    var revealed = 0.0
    for (i in timeline.indices) {
        val t = timeline[i]
        if (now >= t.endTime) {
            revealed = (i + 1).toDouble()
        } else if (now >= t.startTime) {
            val span = max(t.endTime - t.startTime, 0.001)
            revealed = i + rigClamp01((now - t.startTime) / span)
            break
        } else break
    }
    return revealed / timeline.size
}

/** 每帧的镜头结果：世界坐标位置 + 朝向（相机看 −Z）。 */
internal data class DioramaCameraPose(val position: DioramaVec, val quaternion: DioramaQuat)

internal class DioramaCameraRig {
    private var flying = false
    private var flightEpoch = 0
    private var flightStart = 0.0
    private var flightPerp = DioramaVec(0.0, 0.0, 0.0)
    private var flightSide = 1
    private var flightLen = 1.0
    private var pos = DioramaVec(0.0, 0.6, 9.0)
    private var posVel = DioramaVec(0.0, 0.0, 0.0)
    private var look = DioramaVec(0.0, 0.0, -10.0)
    private var lookVel = DioramaVec(0.0, 0.0, 0.0)
    private var align = 0.0
    private var alignQuat = DioramaQuat(0.0, 0.0, 0.0, 1.0)
    private var alignQuatInit = false
    private var orient = DioramaQuat(0.0, 0.0, 0.0, 1.0)
    private var orientInit = false
    /** 相机当前状态（three 的 camera.position / quaternion），首帧前是 r3f 默认 (0, 0.6, 9)、单位朝向。 */
    var cameraPosition = DioramaVec(0.0, 0.6, 9.0)
        private set
    var cameraQuaternion = DioramaQuat(0.0, 0.0, 0.0, 1.0)
        private set

    private var memoKey: Any? = null
    private var memoIndex = -1
    private var resolved: DioramaResolvedLine? = null
    private var timeline: List<GraphemeTiming> = emptyList()
    private var rtl = false
    private var shotKind = DioramaShotKind.HOLD
    private var placement = getDioramaTextPlacement(0, null, 1.0)

    private fun memo(sequencer: DioramaSequencerState, globalIndex: Int, motion: DioramaMotionParams) {
        val key = Triple(sequencer, motion.subMode, motion.weaveScale)
        if (key == memoKey && globalIndex == memoIndex) return
        memoKey = key; memoIndex = globalIndex
        val r = dioramaResolveGlobal(sequencer, globalIndex)
        resolved = r
        val line = r?.line
        timeline = if (line != null) GraphemeTimings.buildLineGraphemeTimeline(line).filterNotNull() else emptyList()
        rtl = line != null && isDioramaRtlText(line.fullText)
        shotKind = if (r != null) getDioramaShot(r.localIndex, r.segment.lines, r.segment.seed, motion.subMode) else DioramaShotKind.HOLD
        placement = getDioramaTextPlacement(r?.localIndex ?: 0, r?.segment?.seed, motion.weaveScale)
    }

    /**
     * 一帧。now = 播放时间（秒），delta = 帧间隔，elapsed = 场景时钟（换歌飞行计时用），activeLineWidth = 当前行世界宽度，
     * fov / aspect = 相机参数。返回 null 表示没有可解析的行（相机不动）。
     */
    fun step(
        now: Double, delta: Double, elapsed: Double, sequencer: DioramaSequencerState, globalIndex: Int, activeLineWidth: Double,
        motion: DioramaMotionParams, transitionEpoch: Int, fov: Double = 55.0, aspect: Double = 1.0,
    ): DioramaCameraPose? {
        memo(sequencer, globalIndex, motion)
        val r = resolved ?: return null
        val line = r.line
        val wordProgress = if (line != null) resolveWordProgress(line, timeline, now) else 0.0
        var progress = if (line != null) 1.0 else 0.0
        if (line != null) {
            val renderEnd = RenderHints.getLineRenderEndTime(line)
            val span = renderEnd - line.startTime
            progress = if (span > 0) rigClamp01((now - line.startTime) / span) else if (now >= line.startTime) 1.0 else 0.0
        }
        val visibleHalf = dioramaFrameHalfWidth(DIORAMA_HERO_DISTANCE, fov, aspect) * DIORAMA_SAFE_FRAME_FRACTION
        val frame = r.frame
        val R = frame.right; val U = frame.up; val F = frame.forward
        val settle = if (line != null) resolveDioramaHoldSettle(now - RenderHints.getLineRenderEndTime(line)) else 1.0
        val truck = resolveDioramaReadHeadTruck(if (rtl) 1 - wordProgress else wordProgress, activeLineWidth, visibleHalf) * settle
        val headR = placement.offsetR + truck
        val headU = placement.offsetU
        val baseX = frame.position.x + R.x * headR + U.x * headU
        val baseY = frame.position.y + R.y * headR + U.y * headU
        val baseZ = frame.position.z + R.z * headR + U.z * headU
        val shotSeed = r.segment.seed
        val shot = resolveDioramaShotOffset(shotKind, progress, wordProgress, DIORAMA_HERO_DISTANCE, DIORAMA_CAMERA_LIFT, shotSeed, r.localIndex, motion.moveScale * settle)
        val drift = resolveDioramaCameraDrift(now, shotSeed, motion.driftScale)
        val offRight = shot.right + drift.swayX
        val offUp = shot.up + drift.swayY + drift.lift
        val offBack = shot.back + drift.dist
        val pose = DioramaVec(
            baseX + R.x * offRight + U.x * offUp - F.x * offBack,
            baseY + R.y * offRight + U.y * offUp - F.y * offBack,
            baseZ + R.z * offRight + U.z * offUp - F.z * offBack,
        )

        if (transitionEpoch != flightEpoch && transitionEpoch > 0) {
            flightEpoch = transitionEpoch
            flying = true
            flightStart = elapsed
            flightPerp = dioramaFlightPerp(pos, pose)
            flightSide = dioramaFlightSide(shotSeed, transitionEpoch)
            flightLen = vlen(pos - pose)
        }
        var isFlying = false
        var te = 1.0
        if (flying) {
            te = (elapsed - flightStart) / DIORAMA_TRANSITION_DURATION
            if (te >= 1) flying = false else isFlying = true
        }
        val followSmooth = if (isFlying) FLIGHT_SMOOTH_TIME + (motion.smoothTime - FLIGHT_SMOOTH_TIME) * dioramaTransitionEase(te) else motion.smoothTime
        val swayEnv = if (isFlying) sin(PI * te).let { it * it } else 0.0
        val arcMag = swayEnv * flightLen * FLIGHT_ARC_FRAC
        val sweepMag = swayEnv * flightLen * DIORAMA_TRANSITION_AIM_SWEEP * flightSide
        val bank = swayEnv * DIORAMA_TRANSITION_BANK * flightSide

        val posGap = vlen(pose - pos)
        val snapped = !isFlying && posGap > DIORAMA_SNAP_DISTANCE
        if (snapped) {
            pos = pose; posVel = DioramaVec(0.0, 0.0, 0.0)
        } else {
            val sx = dioramaSmoothDamp(pos.x, pose.x, posVel.x, followSmooth, delta)
            val sy = dioramaSmoothDamp(pos.y, pose.y, posVel.y, followSmooth, delta)
            val sz = dioramaSmoothDamp(pos.z, pose.z, posVel.z, followSmooth, delta)
            pos = DioramaVec(sx.first, sy.first, sz.first); posVel = DioramaVec(sx.second, sy.second, sz.second)
        }
        val camPos = DioramaVec(pos.x + flightPerp.x * arcMag, pos.y + flightPerp.y * arcMag + arcMag * 0.5, pos.z + flightPerp.z * arcMag)

        val lookOff = placement.lookR * settle
        val lookTarget = DioramaVec(
            baseX + R.x * lookOff + flightPerp.x * sweepMag,
            baseY + R.y * lookOff + flightPerp.y * sweepMag,
            baseZ + R.z * lookOff + flightPerp.z * sweepMag,
        )
        val lookGap = vlen(lookTarget - look)
        if (!isFlying && lookGap > DIORAMA_SNAP_DISTANCE) {
            look = lookTarget; lookVel = DioramaVec(0.0, 0.0, 0.0)
        } else {
            val lx = dioramaSmoothDamp(look.x, lookTarget.x, lookVel.x, followSmooth, delta)
            val ly = dioramaSmoothDamp(look.y, lookTarget.y, lookVel.y, followSmooth, delta)
            val lz = dioramaSmoothDamp(look.z, lookTarget.z, lookVel.z, followSmooth, delta)
            look = DioramaVec(lx.first, ly.first, lz.first); lookVel = DioramaVec(lx.second, ly.second, lz.second)
        }
        val lookQuat = dioramaCameraLookAt(camPos, look, DioramaVec(0.0, 1.0, 0.0))

        val alignTarget = dioramaFrameQuaternion(frame, placement.roll, placement.yaw)
        if (snapped || !alignQuatInit) {
            alignQuat = alignTarget; alignQuatInit = true
        } else {
            alignQuat = dioramaSlerp(alignQuat, alignTarget, 1 - exp(-ALIGN_TURN_RATE * delta))
        }
        var readTarget = 0.0
        if (line != null) {
            val renderEnd = RenderHints.getLineRenderEndTime(line)
            val rampIn = rigSmoothstep01((now - (line.startTime - ALIGN_LEAD_IN)) / (ALIGN_LEAD_IN + ALIGN_SETTLE))
            val rampOut = 1 - rigSmoothstep01((now - renderEnd) / ALIGN_RELEASE)
            readTarget = min(rampIn, rampOut) * (ALIGN_SHOT_CEILING[shotKind] ?: 0.7)
        }
        align += (readTarget - align) * (1 - exp(-ALIGN_RATE * delta))
        var target = dioramaSlerp(lookQuat, alignQuat, align)
        val viewDir = vnorm(look - camPos)
        val camForward = dioramaApplyQuat(DioramaVec(0.0, 0.0, -1.0), target)
        val offAxis = angleTo(camForward, viewDir)
        val halfV = fov * PI / 180 / 2
        val halfH = atan(tan(halfV) * aspect)
        val maxOffAxis = min(halfV, halfH) * FRAME_KEEP_FRACTION
        if (offAxis > maxOffAxis) target = dioramaSlerp(target, lookQuat, 1 - maxOffAxis / offAxis)
        if (snapped || !orientInit) {
            orient = target; orientInit = true
        } else {
            orient = dioramaSlerp(orient, target, 1 - exp(-ORIENT_FOLLOW_RATE * delta))
        }
        var q = orient
        if (bank != 0.0) q *= DioramaQuat.axisAngle(0.0, 0.0, 1.0, bank)
        cameraPosition = camPos
        cameraQuaternion = q
        return DioramaCameraPose(camPos, q)
    }
}
