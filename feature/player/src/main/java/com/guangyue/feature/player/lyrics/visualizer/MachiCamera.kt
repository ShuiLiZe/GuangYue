// 町景（Folia machi）镜头：逐函数对译 engine/camera.ts 的正交斜俯视构图（无拖拽，只有默认构图 + 运镜）。
//   · fitDefault：按默认朝向把构图点投到镜头平面，取投影包围盒中心与半宽 / 半高。
//   · applyFrustum：按宽高比取能装下构图的半高（留白 2%、给字幕让 5%），竖向装不下时多出来的 85% 从下沿裁。
//   · settle：静止位置叠运镜（resolveMachiDrift），转角夹在默认 ±75°、俯角 20°–70°、缩放 0.75–3.6，平移 ±5 m。
//   · place：镜头在目标点 + 球坐标方向 × 60 m，lookAt 目标点；three 的 OrthographicCamera.zoom 绕视锥中心缩放。
// 输出列主序 4×4（GLES 直接用）。拖拽照 OrbitControls 的默认视角模式：单指转（rotateSpeed 0.55）、双指缩放 / 平移（panSpeed 0.8），
// 限角同 MACHI_CAMERA_LIMITS；一拖运镜就停，松手约 8 秒后缓回默认构图（k = 1 − e^(−1.6 dt)），回正后运镜 4 秒升满。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MACHI_DEG = PI / 180

/** MACHI_CAMERA_LIMITS。 */
internal object MachiCameraLimits {
    const val AZIMUTH_RANGE = 75 * MACHI_DEG
    const val MIN_ELEVATION = 20 * MACHI_DEG
    const val MAX_ELEVATION = 70 * MACHI_DEG
    const val MIN_ZOOM = 0.75
    const val MAX_ZOOM = 3.6
    const val PAN = 5.0
}

private const val MC_MARGIN = 0.02
private const val MC_SUBTITLE_RESERVE = 0.05
private const val MC_CAMERA_DISTANCE = 60.0
private const val MC_CROP_FROM_BOTTOM = 0.85
private const val MC_NEAR = 0.1
private const val MC_FAR = 200.0

/** MachiView：方位角（three 球坐标，从 +z 转向 +x）、俯角、缩放。 */
internal data class MachiView(val azimuth: Double, val elevation: Double, val zoom: Double)

internal class MachiVec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: MachiVec3) = MachiVec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: MachiVec3) = MachiVec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = MachiVec3(x * s, y * s, z * s)
    fun dot(o: MachiVec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: MachiVec3) = MachiVec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): MachiVec3 { val l = sqrt(dot(this)); return if (l < 1e-12) this else this * (1 / l) }
}

internal fun machiOrbitDirection(azimuth: Double, elevation: Double) =
    MachiVec3(sin(azimuth) * cos(elevation), sin(elevation), cos(azimuth) * cos(elevation))

private fun mcClamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

/** 一帧镜头：位置、目标点、缩放与视锥（缩放 1 时的 left/right/top/bottom）。 */
internal data class MachiCameraPose(
    val position: MachiVec3,
    val target: MachiVec3,
    val zoom: Double,
    val left: Double,
    val right: Double,
    val top: Double,
    val bottom: Double,
) {
    /** three Matrix4.lookAt（相机：z 轴 = 位置 − 目标）→ 视图矩阵（列主序）。 */
    fun viewMatrix(): FloatArray {
        val z = (position - target).normalized()
        var xa = MachiVec3(0.0, 1.0, 0.0).cross(z)
        if (xa.dot(xa) < 1e-12) xa = MachiVec3(1.0, 0.0, 0.0)
        val x = xa.normalized()
        val y = z.cross(x)
        return floatArrayOf(
            x.x.toFloat(), y.x.toFloat(), z.x.toFloat(), 0f,
            x.y.toFloat(), y.y.toFloat(), z.y.toFloat(), 0f,
            x.z.toFloat(), y.z.toFloat(), z.z.toFloat(), 0f,
            (-x.dot(position)).toFloat(), (-y.dot(position)).toFloat(), (-z.dot(position)).toFloat(), 1f,
        )
    }

    /** three OrthographicCamera.updateProjectionMatrix（zoom 绕视锥中心）→ GL 正交投影（列主序）。 */
    fun projectionMatrix(): FloatArray {
        val dx = (right - left) / (2 * zoom); val cx = (right + left) / 2
        val dy = (top - bottom) / (2 * zoom); val cy = (top + bottom) / 2
        val l = cx - dx; val r = cx + dx; val t = cy + dy; val b = cy - dy
        val w = 1.0 / (r - l); val h = 1.0 / (t - b); val p = 1.0 / (MC_FAR - MC_NEAR)
        return floatArrayOf(
            (2 * w).toFloat(), 0f, 0f, 0f,
            0f, (2 * h).toFloat(), 0f, 0f,
            0f, 0f, (-2 * p).toFloat(), 0f,
            (-(r + l) * w).toFloat(), (-(t + b) * h).toFloat(), (-(MC_FAR + MC_NEAR) * p).toFloat(), 1f,
        )
    }

    /** 投影 × 视图。 */
    fun viewProjection(): FloatArray = mcMul(projectionMatrix(), viewMatrix())

    /** 世界点 → 归一化设备坐标（-1..1，y 向上）。 */
    fun project(p: MachiVec3): DoubleArray {
        val m = viewProjection()
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        return doubleArrayOf(x, y, z)
    }
}

internal fun mcMul(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(16)
    for (c in 0 until 4) for (r in 0 until 4) {
        var s = 0f
        for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
        out[c * 4 + r] = s
    }
    return out
}

/** MachiCamera 的默认构图 + 运镜（无交互）。framing = 场景给的构图点与默认视角。 */
internal class MachiCameraRig(private val points: List<MachiVec3>, private val home: MachiView) {
    private var fitCenter = MachiVec3(0.0, 0.0, 0.0)
    private var fitUp = MachiVec3(0.0, 1.0, 0.0)
    private var fitHalfW = 9.0
    private var fitHalfH = 7.0
    private var aspect = 16.0 / 9
    private var halfHeight = 8.0
    private var base = MachiVec3(0.0, 0.0, 0.0)

    // ── 拖拽（默认视角模式）：相对默认构图的偏移，转角 / 俯仰（弧度）、缩放倍数、按默认朝向的左右 / 前后平移（米）。
    private var dAz = 0.0
    private var dEl = 0.0
    private var dZoom = 1.0
    private var dPanX = 0.0
    private var dPanZ = 0.0
    private var interacting = false
    private var idleMs = -1L
    private var returning = false
    /** 运镜闸门：拖动时归零，回到静止位置后按真实时间 4 秒升到 1（camera.ts stepMotion 的 motionWeight 归零再起）。 */
    private var gate = 1.0

    private fun offAmount() = kotlin.math.abs(dAz) + kotlin.math.abs(dEl) + kotlin.math.abs(dZoom - 1) + kotlin.math.abs(dPanX) + kotlin.math.abs(dPanZ)

    fun beginDrag() { interacting = true; returning = false; idleMs = -1; gate = 0.0 }

    fun endDrag(nowMs: Long) { interacting = false; idleMs = nowMs }

    /** 单指拖：OrbitControls rotateLeft / rotateUp（2π·Δ / 视口高 × rotateSpeed）。 */
    fun rotate(dxPx: Double, dyPx: Double, heightPx: Double) {
        val k = 2 * PI / max(1.0, heightPx) * 0.55
        dAz = mcClamp(dAz - dxPx * k, -MachiCameraLimits.AZIMUTH_RANGE, MachiCameraLimits.AZIMUTH_RANGE)
        val el = mcClamp(home.elevation + dEl + dyPx * k, MachiCameraLimits.MIN_ELEVATION, MachiCameraLimits.MAX_ELEVATION)
        dEl = el - home.elevation
    }

    /** 双指捏合：缩放倍数，夹在 0.75–3.6（按绝对缩放）。 */
    fun zoomBy(factor: Double) {
        val z = mcClamp(home.zoom * dZoom * factor, MachiCameraLimits.MIN_ZOOM, MachiCameraLimits.MAX_ZOOM)
        dZoom = z / home.zoom
    }

    /** 双指平移：沿地面（screenSpacePanning = false），按当前朝向换算后存成默认朝向的左右 / 前后，夹在 ±5 m。 */
    fun pan(dxPx: Double, dyPx: Double, heightPx: Double) {
        val zoom = home.zoom * dZoom
        val s = 2 * halfHeight / zoom / max(1.0, heightPx) * 0.8
        val az = home.azimuth + dAz
        val rightCur = MachiVec3(cos(az), 0.0, -sin(az))
        val backCur = MachiVec3(-sin(az), 0.0, -cos(az))
        val move = rightCur * (-dxPx * s) + backCur * (dyPx * s)
        val rightHome = MachiVec3(cos(home.azimuth), 0.0, -sin(home.azimuth))
        val backHome = MachiVec3(-sin(home.azimuth), 0.0, -cos(home.azimuth))
        dPanX = mcClamp(dPanX + move.dot(rightHome), -MachiCameraLimits.PAN, MachiCameraLimits.PAN)
        dPanZ = mcClamp(dPanZ + move.dot(backHome), -MachiCameraLimits.PAN, MachiCameraLimits.PAN)
    }

    /** 每帧推进闲置回正与运镜闸门；返回运镜幅度的乘数。 */
    fun step(realDelta: Double, nowMs: Long): Double {
        if (!interacting && idleMs > 0 && nowMs - idleMs > 8000) { returning = offAmount() > 1e-3; idleMs = -1 }
        if (returning) {
            val k = 1 - kotlin.math.exp(-realDelta * 1.6)
            dAz -= dAz * k; dEl -= dEl * k; dZoom += (1 - dZoom) * k; dPanX -= dPanX * k; dPanZ -= dPanZ * k
            if (offAmount() < 2e-3) { dAz = 0.0; dEl = 0.0; dZoom = 1.0; dPanX = 0.0; dPanZ = 0.0; returning = false }
        }
        val still = !interacting && !returning && idleMs < 0
        gate = if (still) min(1.0, gate + realDelta / 4.0) else 0.0
        return gate
    }

    init { fitDefault() }

    private fun fitDefault() {
        val direction = machiOrbitDirection(home.azimuth, home.elevation)
        val forward = direction * -1.0
        val right = forward.cross(MachiVec3(0.0, 1.0, 0.0)).normalized()
        val up = right.cross(forward).normalized()
        var minR = Double.POSITIVE_INFINITY; var maxR = Double.NEGATIVE_INFINITY
        var minU = Double.POSITIVE_INFINITY; var maxU = Double.NEGATIVE_INFINITY
        for (p in points) {
            val r = p.dot(right); val u = p.dot(up)
            minR = min(minR, r); maxR = max(maxR, r); minU = min(minU, u); maxU = max(maxU, u)
        }
        fitUp = up
        fitCenter = right * ((minR + maxR) / 2) + up * ((minU + maxU) / 2)
        fitHalfW = (maxR - minR) / 2
        fitHalfH = (maxU - minU) / 2
        applyFrustum()
    }

    private fun applyFrustum() {
        val usable = 1 - MC_MARGIN * 2 - MC_SUBTITLE_RESERVE
        halfHeight = max(fitHalfH, fitHalfW / aspect) / usable
        val overflow = max(0.0, fitHalfH / usable - halfHeight / home.zoom)
        base = fitCenter + fitUp * (overflow * (MC_CROP_FROM_BOTTOM - 0.5) * 2)
    }

    fun setAspect(width: Double, height: Double) {
        val a = width / max(1.0, height)
        if (kotlin.math.abs(a - aspect) < 1e-4) return
        aspect = a
        applyFrustum()
    }

    private fun restTarget(extraX0: Double, extraZ0: Double): MachiVec3 {
        val extraX = extraX0 + dPanX
        val extraZ = extraZ0 + dPanZ
        if (extraX == 0.0 && extraZ == 0.0) return base
        val right = MachiVec3(cos(home.azimuth), 0.0, -sin(home.azimuth))
        val back = MachiVec3(-sin(home.azimuth), 0.0, -cos(home.azimuth))
        val x = mcClamp(extraX, -MachiCameraLimits.PAN, MachiCameraLimits.PAN)
        val z = mcClamp(extraZ, -MachiCameraLimits.PAN, MachiCameraLimits.PAN)
        return base + right * x + back * z
    }

    /** 静止位置叠上运镜：[driftTime] 氛围时间，[motionWeight] 0..1 幅度（按 smoothstep 缓起缓停）。 */
    fun pose(driftTime: Double, motionWeight: Double): MachiCameraPose {
        val w0 = mcClamp(motionWeight, 0.0, 1.0)
        val weight = w0 * w0 * (3 - 2 * w0)
        val view: MachiView
        val target: MachiVec3
        val home = MachiView(
            azimuth = this.home.azimuth + dAz,
            elevation = mcClamp(this.home.elevation + dEl, MachiCameraLimits.MIN_ELEVATION, MachiCameraLimits.MAX_ELEVATION),
            zoom = mcClamp(this.home.zoom * dZoom, MachiCameraLimits.MIN_ZOOM, MachiCameraLimits.MAX_ZOOM),
        )
        if (weight <= 0) {
            view = home; target = restTarget(0.0, 0.0)
        } else {
            val drift = resolveMachiDrift(driftTime)
            val range = MachiCameraLimits.AZIMUTH_RANGE
            val panScale = weight * min(1.0, this.home.zoom / home.zoom)
            view = MachiView(
                azimuth = mcClamp(home.azimuth + drift.azimuth * MACHI_DEG * weight, this.home.azimuth - range, this.home.azimuth + range),
                elevation = mcClamp(home.elevation + drift.elevation * MACHI_DEG * weight, MachiCameraLimits.MIN_ELEVATION, MachiCameraLimits.MAX_ELEVATION),
                zoom = mcClamp(home.zoom * drift.zoom.pow(weight), MachiCameraLimits.MIN_ZOOM, MachiCameraLimits.MAX_ZOOM),
            )
            target = restTarget(drift.panX * panScale, drift.panZ * panScale)
        }
        val shift = halfHeight * MC_SUBTITLE_RESERVE
        return MachiCameraPose(
            position = target + machiOrbitDirection(view.azimuth, view.elevation) * MC_CAMERA_DISTANCE,
            target = target,
            zoom = view.zoom,
            left = -halfHeight * aspect,
            right = halfHeight * aspect,
            top = halfHeight - shift,
            bottom = -halfHeight - shift,
        )
    }
}
