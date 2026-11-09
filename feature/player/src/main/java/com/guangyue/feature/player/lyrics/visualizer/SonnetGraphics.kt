// 商籁（Folia sonnet）图形层：逐函数对译，画到 android.graphics.Canvas（单位 = Folia CSS 像素 = dp）。
// 对应 Folia 文件：src/components/visualizer/sonnet/
//   sonnetAnimatedGraphics.ts（逐笔描出的路径记录器，黄金分割错开 + 三次缓出）
//   sonnetShotMg.ts（镜头 MG：几何变体 0–17）、sonnetSpatialMgGeometry.ts（立方 / 棱柱）
//   sonnetAdditionalShotMg.ts（18–23：等高线 / 放射波 / 线路图 / 计时盘 / 折带 / 半调）
//   sonnetShotMgFlora / Botanical / Architecture / Landscape.ts + sonnetThemedShotMgPrimitives.ts（主题变体 24–35）
//   sonnetBackgroundMgVariants.ts（8 种 HUD 外框）、sonnetFixedGeoVariants.ts（8 种定格几何 + 斜线填充）
//   sonnetBackgroundDecor.ts + sonnetIcons.ts（漂浮小形 + 主题图标，没有图标时用 Lucide Flower）
//   sonnetTextFixedGeo.ts（词块底纹）、sonnetGuides.ts（引导线 / 彗尾 / 碎片）、sonnetFrameDecor.ts（开口框）
//   sonnetFrameOverlay.ts（画面四角的固定标记）
// 偏离：Folia 里 Math.random 的地方（圆的起笔方向、引导线碎片）改用种子随机，同一首歌每次一样；
// 几何变体 0–35 已移植（18–23 sonnetAdditionalShotMg.ts，24–35 sonnetThemedShotMg.ts 的花 / 植物 / 建筑 / 地景）；
// 36–99（开口框 sonnetOpenFrameShotMg.ts、天体 / 海洋 / 音乐 / 工艺 / 动态）还没移植，暂按 variant % 36 退回。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal fun sonnetArgb(rgb: Int, alpha: Double): Int {
    val a = (clamp01(alpha) * 255).toInt()
    return (a shl 24) or (rgb and 0xffffff)
}

/** 画一段弧：Pixi arc 从当前点连线到弧起点；|sweep| ≥ 360 时 Skia arcTo 会退化，拆成两半。 */
private fun Path.sonnetArc(cx: Double, cy: Double, r: Double, startRad: Double, sweepRad: Double) {
    val oval = RectF((cx - r).toFloat(), (cy - r).toFloat(), (cx + r).toFloat(), (cy + r).toFloat())
    val start = Math.toDegrees(startRad).toFloat()
    val sweep = Math.toDegrees(sweepRad).toFloat()
    if (abs(sweep) > 180f) {
        arcTo(oval, start, sweep / 2f, false)
        arcTo(oval, start + sweep / 2f, sweep / 2f, false)
    } else arcTo(oval, start, sweep, false)
}

// ───────────────────────────── AnimatedGraphics ─────────────────────────────

/** sonnetAnimatedGraphics.ts：记录 moveTo / lineTo / 曲线 / 弧 / 矩形 + stroke / fill，按进度逐笔描出。 */
internal class SonnetAnimGraphics(seed: Long) {
    private class El(val type: Int, val v: DoubleArray, val len: Double = 0.0)
    private class Cmd(val stroke: Boolean, val path: List<El>, val length: Double, val color: Int, val width: Double, val alpha: Double) {
        var delay = 0.0
        var span = 1.0
    }

    private val rng = SonnetRandom(seed)
    private val commands = ArrayList<Cmd>()
    private var current = ArrayList<El>()
    private var currentLength = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var scheduled = false
    var rotation = 0.0
    val isEmpty get() = commands.isEmpty()

    fun moveTo(x: Double, y: Double): SonnetAnimGraphics {
        current.add(El(MOVE, doubleArrayOf(x, y))); lastX = x; lastY = y; return this
    }

    fun lineTo(x: Double, y: Double): SonnetAnimGraphics {
        val len = hypot(x - lastX, y - lastY)
        current.add(El(LINE, doubleArrayOf(x, y, lastX, lastY), len)); currentLength += len; lastX = x; lastY = y; return this
    }

    fun quadraticCurveTo(cx: Double, cy: Double, tx: Double, ty: Double): SonnetAnimGraphics {
        val len = hypot(cx - lastX, cy - lastY) + hypot(tx - cx, ty - cy)
        current.add(El(QUAD, doubleArrayOf(cx, cy, tx, ty, lastX, lastY), len)); currentLength += len; lastX = tx; lastY = ty; return this
    }

    fun bezierCurveTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, tx: Double, ty: Double): SonnetAnimGraphics {
        val len = hypot(c1x - lastX, c1y - lastY) + hypot(c2x - c1x, c2y - c1y) + hypot(tx - c2x, ty - c2y)
        current.add(El(BEZ, doubleArrayOf(c1x, c1y, c2x, c2y, tx, ty, lastX, lastY), len)); currentLength += len; lastX = tx; lastY = ty; return this
    }

    fun arc(cx: Double, cy: Double, r: Double, start: Double, end: Double, anticlockwise: Boolean = false): SonnetAnimGraphics {
        var diff = end - start
        if (anticlockwise && diff > 0) diff -= PI * 2 else if (!anticlockwise && diff < 0) diff += PI * 2
        val len = abs(diff) * r
        current.add(El(ARC, doubleArrayOf(cx, cy, r, start, diff), len)); currentLength += len
        lastX = cx + cos(end) * r; lastY = cy + sin(end) * r
        return this
    }

    fun circle(x: Double, y: Double, r: Double): SonnetAnimGraphics {
        val start = rng.next() * PI * 2
        val anticlockwise = rng.next() > 0.5
        val diff = if (anticlockwise) -PI * 2 else PI * 2
        moveTo(x + cos(start) * r, y + sin(start) * r)
        current.add(El(ARC, doubleArrayOf(x, y, r, start, diff), PI * 2 * r)); currentLength += PI * 2 * r
        lastX = x + cos(start + diff) * r; lastY = y + sin(start + diff) * r
        return this
    }

    fun rect(x: Double, y: Double, w: Double, h: Double): SonnetAnimGraphics {
        current.add(El(RECT, doubleArrayOf(x, y, w, h)))
        moveTo(x, y).lineTo(x + w, y).lineTo(x + w, y + h).lineTo(x, y + h).lineTo(x, y)
        return this
    }

    fun poly(points: DoubleArray): SonnetAnimGraphics {
        moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
        return lineTo(points[0], points[1])
    }

    fun stroke(color: Int, width: Double, alpha: Double): SonnetAnimGraphics {
        if (current.isNotEmpty()) {
            commands.add(Cmd(true, current, currentLength, color, width, alpha))
            current = ArrayList(); currentLength = 0.0
        }
        return this
    }

    fun fill(color: Int, alpha: Double): SonnetAnimGraphics {
        if (current.isNotEmpty()) {
            commands.add(Cmd(false, current, currentLength, color, 0.0, alpha))
            current = ArrayList(); currentLength = 0.0
        }
        return this
    }

    private fun schedule() {
        val golden = 0.6180339887498949
        var strokeIndex = 0
        var fillIndex = 0
        for (cmd in commands) {
            val index = if (cmd.stroke) strokeIndex++ else fillIndex++
            val slot = (index * golden) % 1.0
            val jitter = ((index.toLong() * 2654435761L) and 0xffffffffL) / 4294967296.0
            val delay = slot * (if (cmd.stroke) 0.5 else 0.45)
            val span = if (cmd.stroke) 0.32 + jitter * 0.26 else 0.4 + jitter * 0.25
            cmd.delay = delay
            cmd.span = min(span, 1 - delay)
        }
        scheduled = true
    }

    private fun opensOwnPath(cmd: Cmd): Boolean {
        if (!cmd.stroke) return true
        if (cmd.length <= 0) return false
        return cmd.path.firstOrNull { it.type != RECT }?.type == MOVE
    }

    private val path = Path()

    /** 照 update(rawProgress) 画到 canvas；alphaMul 是外层容器的透明度。 */
    fun draw(canvas: Canvas, rawProgress: Double, alphaMul: Double, paint: Paint) {
        if (commands.isEmpty() || alphaMul <= 0.002) return
        if (!scheduled) schedule()
        for (index in commands.indices) {
            val cmd = commands[index]
            if (rawProgress <= cmd.delay && opensOwnPath(cmd) && (index + 1 >= commands.size || opensOwnPath(commands[index + 1]))) continue
            val localRaw = clamp01((rawProgress - cmd.delay) / cmd.span)
            val local = 1 - (1 - localRaw).pow(3)
            path.reset()
            if (!cmd.stroke) {
                path.moveTo(0f, 0f)
                val first = cmd.path[0]
                if (cmd.path.size == 6 && first.type == RECT) {
                    val v = first.v
                    path.addRect(v[0].toFloat(), v[1].toFloat(), (v[0] + v[2] * local).toFloat(), (v[1] + v[3]).toFloat(), Path.Direction.CW)
                } else {
                    for (p in cmd.path) appendFull(p)
                }
                val alphaP = 1 - (1 - min(1.0, localRaw * 2)).pow(3)
                paint.style = Paint.Style.FILL
                paint.color = sonnetArgb(cmd.color, cmd.alpha * alphaP * alphaMul)
                canvas.drawPath(path, paint)
            } else {
                if (cmd.length <= 0) continue
                val target = cmd.length * local
                var len = 0.0
                var started = false
                for (p in cmd.path) {
                    if (p.type == RECT) continue
                    if (p.type == MOVE) { path.moveTo(p.v[0].toFloat(), p.v[1].toFloat()); started = true; continue }
                    if (len >= target) break
                    if (!started) { startAt(p); started = true }
                    if (len + p.len <= target) { appendFull(p); len += p.len }
                    else {
                        appendPartial(p, (target - len) / p.len)
                        len = target
                        break
                    }
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = cmd.width.toFloat()
                paint.color = sonnetArgb(cmd.color, cmd.alpha * alphaMul)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun startAt(p: El) {
        when (p.type) {
            LINE -> path.moveTo(p.v[2].toFloat(), p.v[3].toFloat())
            QUAD -> path.moveTo(p.v[4].toFloat(), p.v[5].toFloat())
            BEZ -> path.moveTo(p.v[6].toFloat(), p.v[7].toFloat())
            ARC -> {
                val v = p.v
                path.moveTo((v[0] + cos(v[3]) * v[2]).toFloat(), (v[1] + sin(v[3]) * v[2]).toFloat())
            }
        }
    }

    private fun appendFull(p: El) {
        val v = p.v
        when (p.type) {
            MOVE -> path.moveTo(v[0].toFloat(), v[1].toFloat())
            LINE -> path.lineTo(v[0].toFloat(), v[1].toFloat())
            QUAD -> path.quadTo(v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat())
            BEZ -> path.cubicTo(v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat(), v[4].toFloat(), v[5].toFloat())
            ARC -> path.sonnetArc(v[0], v[1], v[2], v[3], v[4])
        }
    }

    private fun appendPartial(p: El, ratio: Double) {
        val v = p.v
        when (p.type) {
            LINE -> path.lineTo((v[2] + (v[0] - v[2]) * ratio).toFloat(), (v[3] + (v[1] - v[3]) * ratio).toFloat())
            ARC -> path.sonnetArc(v[0], v[1], v[2], v[3], v[4] * ratio)
            QUAD -> {
                val lx = v[4]; val ly = v[5]
                val ncx = lx + ratio * (v[0] - lx); val ncy = ly + ratio * (v[1] - ly)
                val ntx = (1 - ratio) * (1 - ratio) * lx + 2 * (1 - ratio) * ratio * v[0] + ratio * ratio * v[2]
                val nty = (1 - ratio) * (1 - ratio) * ly + 2 * (1 - ratio) * ratio * v[1] + ratio * ratio * v[3]
                path.quadTo(ncx.toFloat(), ncy.toFloat(), ntx.toFloat(), nty.toFloat())
            }
            BEZ -> {
                val lx = v[6]; val ly = v[7]
                val q0x = lx + ratio * (v[0] - lx); val q0y = ly + ratio * (v[1] - ly)
                val q1x = v[0] + ratio * (v[2] - v[0]); val q1y = v[1] + ratio * (v[3] - v[1])
                val q2x = v[2] + ratio * (v[4] - v[2]); val q2y = v[3] + ratio * (v[5] - v[3])
                val r0x = q0x + ratio * (q1x - q0x); val r0y = q0y + ratio * (q1y - q0y)
                val r1x = q1x + ratio * (q2x - q1x); val r1y = q1y + ratio * (q2y - q1y)
                val bx = r0x + ratio * (r1x - r0x); val by = r0y + ratio * (r1y - r0y)
                path.cubicTo(q0x.toFloat(), q0y.toFloat(), r0x.toFloat(), r0y.toFloat(), bx.toFloat(), by.toFloat())
            }
        }
    }

    private companion object {
        const val MOVE = 0
        const val LINE = 1
        const val QUAD = 2
        const val BEZ = 3
        const val ARC = 4
        const val RECT = 5
    }
}

// ───────────────────────────── 立方 / 棱柱（sonnetSpatialMgGeometry.ts） ─────────────────────────────

private fun SonnetAnimGraphics.face(points: List<DoubleArray>, color: Int, alpha: Double) {
    moveTo(points[0][0], points[0][1])
    for (i in 1 until points.size) lineTo(points[i][0], points[i][1])
    lineTo(points[0][0], points[0][1]).fill(color, alpha)
}

private fun pt(x: Double, y: Double) = doubleArrayOf(x, y)

private fun SonnetAnimGraphics.solidCuboid(x: Double, y: Double, w: Double, h: Double, dx: Double, dy: Double, color: Int, alpha: Double) {
    val l = x - w / 2; val r = x + w / 2; val t = y - h / 2; val b = y + h / 2
    face(listOf(pt(l, t), pt(l + dx, t + dy), pt(r + dx, t + dy), pt(r, t)), color, alpha * 0.42)
    face(listOf(pt(r, t), pt(r + dx, t + dy), pt(r + dx, b + dy), pt(r, b)), color, alpha * 0.68)
    face(listOf(pt(l, t), pt(r, t), pt(r, b), pt(l, b)), color, alpha * 0.24)
}

private fun SonnetAnimGraphics.extruded(front: List<DoubleArray>, dx: Double, dy: Double, color: Int, alpha: Double) {
    val back = front.map { pt(it[0] + dx, it[1] + dy) }
    for (i in front.indices) {
        val n = (i + 1) % front.size
        face(listOf(front[i], back[i], back[n], front[n]), color, alpha * (0.34 + (i % 3) * 0.12))
    }
    face(front, color, alpha * 0.22)
}

private fun SonnetAnimGraphics.triPrism(x: Double, y: Double, w: Double, h: Double, dx: Double, dy: Double, c: Int, a: Double) =
    extruded(listOf(pt(x, y - h / 2), pt(x + w / 2, y + h / 2), pt(x - w / 2, y + h / 2)), dx, dy, c, a)

private fun SonnetAnimGraphics.hexPrism(x: Double, y: Double, w: Double, h: Double, dx: Double, dy: Double, c: Int, a: Double) =
    extruded(listOf(pt(x - w * 0.25, y - h / 2), pt(x + w * 0.25, y - h / 2), pt(x + w / 2, y), pt(x + w * 0.25, y + h / 2), pt(x - w * 0.25, y + h / 2), pt(x - w / 2, y)), dx, dy, c, a)

private fun SonnetAnimGraphics.trapPrism(x: Double, y: Double, tw: Double, bw: Double, h: Double, dx: Double, dy: Double, c: Int, a: Double) =
    extruded(listOf(pt(x - tw / 2, y - h / 2), pt(x + tw / 2, y - h / 2), pt(x + bw / 2, y + h / 2), pt(x - bw / 2, y + h / 2)), dx, dy, c, a)

// ───────────────────────────── 叶片 / 花瓣（sonnetThemedShotMgPrimitives.ts） ─────────────────────────────

private fun SonnetAnimGraphics.leaf(x: Double, y: Double, length: Double, width: Double, angle: Double, color: Int, fillAlpha: Double) {
    val dx = cos(angle); val dy = sin(angle)
    val nx = -dy; val ny = dx
    val tipX = x + dx * length; val tipY = y + dy * length
    moveTo(x, y).quadraticCurveTo(x + dx * length * 0.45 + nx * width, y + dy * length * 0.45 + ny * width, tipX, tipY)
        .quadraticCurveTo(x + dx * length * 0.45 - nx * width, y + dy * length * 0.45 - ny * width, x, y).fill(color, fillAlpha)
    moveTo(x, y).quadraticCurveTo(x + dx * length * 0.45 + nx * width, y + dy * length * 0.45 + ny * width, tipX, tipY)
        .quadraticCurveTo(x + dx * length * 0.45 - nx * width, y + dy * length * 0.45 - ny * width, x, y).stroke(color, 1.5, min(0.8, fillAlpha * 3.2))
    moveTo(x, y).lineTo(tipX, tipY).stroke(color, 1.0, 0.32)
}

/** sonnetThemedShotMgPrimitives.ts tracePolygon：逐点连线再回到首点。 */
private fun SonnetAnimGraphics.tracePolygon(pts: Array<DoubleArray>): SonnetAnimGraphics {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p[0], p[1]) else lineTo(p[0], p[1]) }
    return lineTo(pts[0][0], pts[0][1])
}
private fun SonnetAnimGraphics.fillPolygon(pts: Array<DoubleArray>, color: Int, alpha: Double) { tracePolygon(pts).fill(color, alpha) }
private fun SonnetAnimGraphics.strokePolygon(pts: Array<DoubleArray>, color: Int, alpha: Double, width: Double = 1.5) { tracePolygon(pts).stroke(color, width, alpha) }

// ───────────────────────────── 镜头 MG（sonnetShotMg.ts） ─────────────────────────────

internal const val SONNET_GEO_VARIANT_COUNT = 100

internal fun sonnetGeoVariant(seed: Long) = ((seed % SONNET_GEO_VARIANT_COUNT) + SONNET_GEO_VARIANT_COUNT) % SONNET_GEO_VARIANT_COUNT
private fun geoSubVariant(seed: Long, count: Int) = ((floor(seed / 100.0).toLong() % count) + count) % count
internal fun sonnetBackgroundMgVariant(seed: Long) = (sonnetMix(seed, 0x9e3779b9L.toInt()) % 8).toInt()
internal fun sonnetFixedGeoVariant(seed: Long) = (sonnetMix(seed, 0x85ebca6bL.toInt()) % 8).toInt()
internal fun sonnetBackgroundDecorVariant(seed: Long) = (sonnetMix(seed, 0xc2b2ae35L.toInt()) % 6).toInt()

/** 已移植的几何变体：0–35。其余（36–47 开口框、48–99 扩展主题）按 % 36 退回（见文件头）。 */
internal fun sonnetPortedGeoVariant(variant: Long): Long = if (variant <= 35) variant else variant % 36

private fun drawGeo(geo: SonnetAnimGraphics, variant: Long, seed: Long, width: Double, height: Double, radius: Double, primary: Int, secondary: Int) {
    val g = geo
    when (variant.toInt()) {
        0 -> {
            g.circle(0.0, 0.0, radius * 0.6).stroke(primary, 6.0, 0.8)
            g.circle(0.0, 0.0, radius * 0.58).stroke(primary, 2.0, 0.4)
            for (i in 0 until 32) {
                val a = i / 32.0 * PI * 2
                val r1 = radius * (0.3 + (i % 3) * 0.05)
                val r2 = radius * 0.55
                g.moveTo(cos(a) * r1, sin(a) * r1).lineTo(cos(a) * r2, sin(a) * r2).stroke(primary, 1.0, 0.2 + (i % 2) * 0.1)
            }
        }
        1 -> {
            val r = radius * 0.7
            g.moveTo(0.0, -r).lineTo(r, 0.0).lineTo(0.0, r).lineTo(-r, 0.0).lineTo(0.0, -r).stroke(primary, 6.0, 0.8)
            g.moveTo(0.0, -r * 0.96).lineTo(r * 0.96, 0.0).lineTo(0.0, r * 0.96).lineTo(-r * 0.96, 0.0).lineTo(0.0, -r * 0.96).stroke(primary, 2.0, 0.4)
            g.moveTo(0.0, -r * 0.4).lineTo(r * 0.4, 0.0).lineTo(0.0, r * 0.4).lineTo(-r * 0.4, 0.0).lineTo(0.0, -r * 0.4).stroke(primary, 1.0, 0.6)
            g.moveTo(-r, 0.0).lineTo(r, 0.0).stroke(primary, 1.0, 0.3)
            g.moveTo(0.0, -r).lineTo(0.0, r).stroke(primary, 1.0, 0.3)
        }
        2 -> {
            fun hex(x: Double, y: Double, r: Double, w: Double, a: Double) {
                g.moveTo(x + r * sin(0.0), y - r * cos(0.0))
                for (j in 1..6) g.lineTo(x + r * sin(j * PI / 3), y - r * cos(j * PI / 3))
                g.stroke(primary, w, a)
            }
            hex(0.0, 0.0, radius * 0.6, 6.0, 0.8)
            hex(0.0, 0.0, radius * 0.57, 2.0, 0.4)
            hex(0.0, 0.0, radius * 0.25, 1.0, 0.5)
            for (j in 0 until 6) {
                val a = j * PI / 3 - PI / 6
                g.moveTo(cos(a) * radius * 0.25, sin(a) * radius * 0.25).lineTo(cos(a) * radius * 0.57, sin(a) * radius * 0.57).stroke(primary, 2.0, 0.4)
            }
        }
        3 -> {
            val mol = geoSubVariant(seed, 3)
            val hexR = radius * 0.22
            if (mol == 0L) {
                fun benzene(cx: Double, cy: Double, scale: Double, rot: Double = 0.0) {
                    val r = hexR * scale
                    g.moveTo(cx + r * sin(rot), cy - r * cos(rot))
                    for (j in 1..6) g.lineTo(cx + r * sin(j * PI / 3 + rot), cy - r * cos(j * PI / 3 + rot))
                    g.stroke(primary, 3.0, 0.8)
                    var j = 0
                    while (j < 6) {
                        val ir = r * 0.82
                        g.moveTo(cx + ir * sin(j * PI / 3 + rot), cy - ir * cos(j * PI / 3 + rot))
                            .lineTo(cx + ir * sin((j + 1) * PI / 3 + rot), cy - ir * cos((j + 1) * PI / 3 + rot)).stroke(primary, 2.0, 0.5)
                        j += 2
                    }
                }
                val rMain = hexR * 1.2
                benzene(0.0, 0.0, 1.2)
                benzene(sin(PI / 3) * rMain * 2, 0.0, 1.2)
                val bd = sin(PI / 3) * rMain * 2
                benzene(-sin(PI / 6) * bd, -cos(PI / 6) * bd, 1.2)
                g.moveTo(0.0, rMain).lineTo(0.0, rMain + radius * 0.2).lineTo(radius * 0.15, rMain + radius * 0.35).stroke(primary, 2.0, 0.6)
            } else if (mol == 1L) {
                g.moveTo(0.0, -hexR)
                for (j in 1..6) g.lineTo(hexR * sin(j * PI / 3), -hexR * cos(j * PI / 3))
                g.stroke(primary, 3.0, 0.8)
                g.moveTo(hexR * 0.8 * sin(PI / 3), -hexR * 0.8 * cos(PI / 3)).lineTo(hexR * 0.8 * sin(2 * PI / 3), -hexR * 0.8 * cos(2 * PI / 3)).stroke(primary, 2.0, 0.5)
                g.moveTo(hexR * 0.8 * sin(4 * PI / 3), -hexR * 0.8 * cos(4 * PI / 3)).lineTo(hexR * 0.8 * sin(5 * PI / 3), -hexR * 0.8 * cos(5 * PI / 3)).stroke(primary, 2.0, 0.5)
                val px1 = hexR * sqrt(3.0) / 2; val py1 = -hexR / 2
                val px2 = hexR * sqrt(3.0) / 2; val py2 = hexR / 2
                val ptx = px1 + hexR * 0.8; val pty = py1 - hexR * 0.1
                val pmx = px1 + hexR * 1.2; val pmy = 0.0
                val pbx = px2 + hexR * 0.8; val pby = py2 + hexR * 0.1
                g.moveTo(px1, py1).lineTo(ptx, pty).lineTo(pmx, pmy).lineTo(pbx, pby).lineTo(px2, py2).stroke(primary, 3.0, 0.8)
                fun branch(sx: Double, sy: Double, a: Double, len: Double, node: Boolean) {
                    val ex = sx + cos(a) * len; val ey = sy + sin(a) * len
                    g.moveTo(sx, sy).lineTo(ex, ey).stroke(primary, 2.0, 0.6)
                    if (node) g.circle(ex, ey, 6.0).stroke(primary, 2.0, 0.8)
                }
                branch(0.0, -hexR, -PI / 2, radius * 0.15, true)
                branch(-hexR * sqrt(3.0) / 2, hexR / 2, PI * 0.8, radius * 0.2, true)
                branch(-hexR * sqrt(3.0) / 2, -hexR / 2, -PI * 0.8, radius * 0.15, false)
                branch(pmx, pmy, 0.0, radius * 0.18, false)
                branch(pmx + radius * 0.18, pmy, PI / 4, radius * 0.1, true)
            } else {
                val segLen = radius * 0.18
                val steps = 7
                val startX = -segLen * (steps / 2.0) * cos(PI / 6)
                val pts = ArrayList<DoubleArray>()
                var cx = startX
                var cy: Double
                pts += pt(cx, 0.0)
                for (i in 0 until steps) {
                    cx += segLen * cos(PI / 6)
                    cy = (if (i % 2 == 0) 1 else -1) * segLen * sin(PI / 6)
                    pts += pt(cx, cy)
                }
                g.moveTo(pts[0][0], pts[0][1])
                for (i in 1..steps) g.lineTo(pts[i][0], pts[i][1])
                g.stroke(primary, 3.0, 0.8)
                val nx = -sin(PI / 6) * 6; val ny = cos(PI / 6) * 6
                g.moveTo(pts[1][0] + nx, pts[1][1] + ny).lineTo(pts[2][0] + nx, pts[2][1] + ny).stroke(primary, 2.0, 0.5)
                for (i in 1 until steps) {
                    val a = if (i % 2 == 0) PI / 2 else -PI / 2
                    val bx = pts[i][0]; val by = pts[i][1] + sin(a) * segLen * 0.6
                    g.moveTo(pts[i][0], pts[i][1]).lineTo(bx, by).stroke(primary, 2.0, 0.5)
                    if (i % 2 != 0) g.circle(bx, by, 5.0).stroke(primary, 2.0, 0.8)
                    else g.moveTo(bx, by).lineTo(bx + segLen * 0.5, by - segLen * 0.3).stroke(primary, 2.0, 0.5)
                }
            }
        }
        4 -> {
            val ellR = radius * 0.7
            for (i in 0 until 3) {
                val a = i * PI / 3
                for (j in 0..60) {
                    val t = j * PI * 2 / 60
                    val ex = cos(t) * ellR; val ey = sin(t) * ellR * 0.18
                    val rx = ex * cos(a) - ey * sin(a); val ry = ex * sin(a) + ey * cos(a)
                    if (j == 0) g.moveTo(rx, ry) else g.lineTo(rx, ry)
                }
                g.stroke(primary, 1.0, 0.3)
            }
            g.circle(0.0, 0.0, radius * 0.05).fill(primary, 0.8)
        }
        5 -> {
            val pr = radius * 0.25
            // .fill().stroke()：fill 先吃掉路径，stroke 拿到空路径 —— 与 Folia AnimatedGraphics 一致，只填不描
            g.circle(0.0, 0.0, pr).fill(primary, 0.15).stroke(primary, 2.0, 0.8)
            g.moveTo(-pr * 0.7, -pr * 0.5).quadraticCurveTo(0.0, -pr * 0.2, pr * 0.7, -pr * 0.5).stroke(primary, 1.0, 0.4)
            g.moveTo(-pr * 0.9, 0.0).quadraticCurveTo(0.0, pr * 0.3, pr * 0.9, 0.0).stroke(primary, 1.0, 0.4)
            val ringRx = radius * 0.6; val ringRy = radius * 0.15; val a = PI / 6
            fun tilted(rx: Double, ry: Double, w: Double, al: Double) {
                for (j in 0..60) {
                    val t = j * PI * 2 / 60
                    val ex = cos(t) * rx; val ey = sin(t) * ry
                    val x = ex * cos(a) - ey * sin(a); val y = ex * sin(a) + ey * cos(a)
                    if (j == 0) g.moveTo(x, y) else g.lineTo(x, y)
                }
                g.stroke(primary, w, al)
            }
            tilted(ringRx, ringRy, 4.0, 0.5)
            tilted(ringRx * 1.1, ringRy * 1.15, 1.0, 0.3)
            tilted(ringRx * 1.25, ringRy * 1.3, 2.0, 0.2)
            g.circle(0.0, 0.0, radius * 0.7).stroke(primary, 1.0, 0.2)
            g.circle(cos(PI / 4) * radius * 0.7, sin(PI / 4) * radius * 0.7, 8.0).fill(primary, 0.6)
        }
        6 -> {
            val bleedX = max(radius * 0.92, width * 0.64)
            val bleedY = max(radius * 0.92, height * 0.64)
            val w = bleedX * 1.08
            val h = radius * 0.8
            val baseY = radius * 0.2
            g.circle(0.0, baseY - h * 0.6, radius * 0.3).stroke(primary, 2.0, 0.4)
            for (i in 0 until 5) g.moveTo(-bleedX, baseY - h * 0.6 + i * 15).lineTo(bleedX, baseY - h * 0.6 + i * 15).stroke(primary, 1.0, 0.3)
            val peaks = 7
            for (layer in 0 until 3) {
                val lw = w * (1 + layer * 0.2)
                val lh = h * (0.5 + layer * 0.25)
                g.moveTo(-lw / 2, baseY)
                for (i in 1 until peaks) {
                    val px = -lw / 2 + (lw / peaks) * i
                    val py = baseY - lh * (0.3 + 0.7 * abs(sin((seed + layer * 11 + i * 7).toDouble())))
                    g.lineTo(px, py)
                }
                g.lineTo(lw / 2, baseY)
                g.stroke(primary, (3 - layer).toDouble(), 0.6 - layer * 0.15)
            }
            g.moveTo(-w, baseY).lineTo(w, baseY).stroke(primary, 4.0, 0.8)
            for (i in 0 until 5) {
                val gy = baseY + i.toDouble().pow(1.5) * 12
                g.moveTo(-w, gy).lineTo(w, gy).stroke(primary, 1.0, 0.4 - i * 0.08)
            }
            for (i in -4..4) g.moveTo(i * radius * 0.2, baseY).lineTo(i * bleedX * 0.32, bleedY).stroke(primary, 1.0, 0.3)
        }
        7 -> {
            for (i in 1..6) g.circle(0.0, 0.0, radius * 0.15 * i).stroke(primary, if (i % 2 == 0) 2.0 else 1.0, 0.2 + (i % 3) * 0.1)
            g.moveTo(-radius * 0.9, 0.0).lineTo(radius * 0.9, 0.0).stroke(primary, 1.0, 0.4)
            g.moveTo(0.0, -radius * 0.9).lineTo(0.0, radius * 0.9).stroke(primary, 1.0, 0.4)
            g.moveTo(0.0, 0.0)
            g.arc(0.0, 0.0, radius * 0.75, 0.0, PI / 4)
            g.lineTo(0.0, 0.0)
            g.fill(primary, 0.1)
            g.stroke(primary, 2.0, 0.5)
            val ro = radius * 0.8
            for (i in 0 until 72) {
                val a = i / 72.0 * PI * 2
                val len = if (i % 18 == 0) 20.0 else if (i % 6 == 0) 10.0 else 5.0
                g.moveTo(cos(a) * ro, sin(a) * ro).lineTo(cos(a) * (ro + len), sin(a) * (ro + len)).stroke(primary, 1.0, 0.4)
            }
            val la = (seed % 360) * PI / 180
            val lr = radius * 0.45
            val lx = cos(la) * lr; val ly = sin(la) * lr
            g.rect(lx - 15, ly - 15, 30.0, 30.0).stroke(primary, 2.0, 0.8)
            g.moveTo(lx, ly - 20).lineTo(lx, ly + 20).stroke(primary, 1.0, 0.6)
            g.moveTo(lx - 20, ly).lineTo(lx + 20, ly).stroke(primary, 1.0, 0.6)
        }
        8 -> {
            val fw = radius * 0.85; val fh = radius * 0.65; val bs = radius * 0.15
            fun bracket(cx: Double, cy: Double, sx: Double, sy: Double) {
                g.moveTo(cx - sx * bs, cy).lineTo(cx, cy).lineTo(cx, cy - sy * bs).stroke(primary, 3.0, 0.7)
                g.moveTo(cx - sx * bs * 0.8, cy - sy * 8).lineTo(cx - sx * 8, cy - sy * 8).lineTo(cx - sx * 8, cy - sy * bs * 0.8).stroke(primary, 1.0, 0.4)
            }
            bracket(-fw, -fh, -1.0, -1.0); bracket(fw, -fh, 1.0, -1.0); bracket(-fw, fh, -1.0, 1.0); bracket(fw, fh, 1.0, 1.0)
            var i = -fw + 20
            while (i < fw - 20) {
                // JS 的 i % 60 对浮点取余
                val major = (i % 60.0) == 0.0
                g.moveTo(i, -fh).lineTo(i, -fh - (if (major) 12 else 6)).stroke(primary, 1.0, 0.5)
                g.moveTo(i, fh).lineTo(i, fh + (if (major) 12 else 6)).stroke(primary, 1.0, 0.5)
                i += 20
            }
            g.circle(0.0, 0.0, radius * 0.1).stroke(primary, 2.0, 0.4)
            g.moveTo(-radius * 0.15, 0.0).lineTo(radius * 0.15, 0.0).stroke(primary, 1.0, 0.4)
            g.moveTo(0.0, -radius * 0.15).lineTo(0.0, radius * 0.15).stroke(primary, 1.0, 0.4)
            g.rect(-fw, -fh + 20, 10.0, 40.0).fill(primary, 0.5)
            g.rect(-fw, -fh + 65, 10.0, 15.0).fill(primary, 0.3)
            g.rect(fw - 10, fh - 60, 10.0, 40.0).fill(primary, 0.5)
        }
        9 -> {
            fun cube(cx: Double, cy: Double, size: Double, alpha: Double) {
                val dy = size * 0.5; val dx = size * 0.866
                g.moveTo(cx, cy - size).lineTo(cx + dx, cy - dy).lineTo(cx, cy).lineTo(cx - dx, cy - dy).lineTo(cx, cy - size).fill(primary, alpha * 0.15).stroke(primary, 2.0, alpha * 0.8)
                g.moveTo(cx, cy).lineTo(cx + dx, cy - dy).lineTo(cx + dx, cy + size - dy).lineTo(cx, cy + size).lineTo(cx, cy).fill(primary, alpha * 0.3).stroke(primary, 2.0, alpha * 0.8)
                g.moveTo(cx, cy).lineTo(cx - dx, cy - dy).lineTo(cx - dx, cy + size - dy).lineTo(cx, cy + size).lineTo(cx, cy).fill(primary, alpha * 0.05).stroke(primary, 2.0, alpha * 0.8)
            }
            cube(0.0, 0.0, radius * 0.35, 0.8)
            cube(radius * 0.4, -radius * 0.15, radius * 0.2, 0.5)
            cube(-radius * 0.45, radius * 0.25, radius * 0.25, 0.6)
            cube(0.0, radius * 0.45, radius * 0.15, 0.4)
            g.moveTo(0.0, 0.0).lineTo(radius * 0.4, -radius * 0.15).stroke(primary, 1.0, 0.3)
            g.moveTo(0.0, 0.0).lineTo(-radius * 0.45, radius * 0.25).stroke(primary, 1.0, 0.3)
        }
        10 -> {
            g.circle(0.0, 0.0, radius * 0.75).stroke(primary, 1.0, 0.2)
            g.circle(0.0, 0.0, radius * 0.73).stroke(primary, 2.0, 0.1)
            val nodes = (0 until 18).map { i ->
                val r = radius * (0.1 + ((seed * 17 + i * 23) % 65) / 100.0)
                val a = ((seed * 11 + i * 37) % 360) * PI / 180
                pt(cos(a) * r, sin(a) * r)
            }
            for (i in nodes.indices) {
                g.circle(nodes[i][0], nodes[i][1], 3.0).fill(primary, 0.7)
                g.circle(nodes[i][0], nodes[i][1], 6.0).stroke(primary, 1.0, 0.3)
                for (j in i + 1 until nodes.size) {
                    val d = hypot(nodes[i][0] - nodes[j][0], nodes[i][1] - nodes[j][1])
                    if (d < radius * 0.45) g.moveTo(nodes[i][0], nodes[i][1]).lineTo(nodes[j][0], nodes[j][1]).stroke(primary, 1.0, 0.4 * (1 - d / (radius * 0.45)))
                }
            }
        }
        11 -> {
            val mr = radius * 0.4
            g.moveTo(0.0, -mr)
            g.arc(0.0, 0.0, mr, -PI / 2, PI / 2, false)
            g.quadraticCurveTo(-mr * 0.4, 0.0, 0.0, -mr)
            g.fill(primary, 0.8)
            g.circle(0.0, 0.0, mr).stroke(primary, 1.0, 0.3)
            val orbitR = radius * 0.65
            g.circle(0.0, 0.0, orbitR).stroke(primary, 1.0, 0.2)
            for (i in 0 until 8) {
                val a = i / 8.0 * PI * 2 - PI / 2
                val mx = cos(a) * orbitR; val my = sin(a) * orbitR
                g.circle(mx, my, 8.0).stroke(primary, 1.0, 0.5)
                if (i == 4) g.circle(mx, my, 6.0).fill(primary, 0.8)
                else if (i != 0) g.moveTo(mx, my - 6).arc(mx, my, 6.0, -PI / 2, PI / 2, i > 4).lineTo(mx, my - 6).fill(primary, 0.5)
            }
            fun star(sx: Double, sy: Double, sr: Double) {
                g.moveTo(sx, sy - sr).lineTo(sx + sr * 0.2, sy - sr * 0.2).lineTo(sx + sr, sy).lineTo(sx + sr * 0.2, sy + sr * 0.2)
                    .lineTo(sx, sy + sr).lineTo(sx - sr * 0.2, sy + sr * 0.2).lineTo(sx - sr, sy).lineTo(sx - sr * 0.2, sy - sr * 0.2).fill(primary, 0.7)
            }
            star(-radius * 0.4, -radius * 0.5, 12.0)
            star(radius * 0.5, -radius * 0.3, 8.0)
            star(-radius * 0.2, radius * 0.5, 15.0)
        }
        12 -> {
            val petalLen = radius * 0.5
            fun petal(a: Double, length: Double, width: Double, alpha: Double) {
                val ex = cos(a) * length; val ey = sin(a) * length
                val cd = length * 0.5
                g.moveTo(0.0, 0.0)
                g.quadraticCurveTo(cos(a - width) * cd, sin(a - width) * cd, ex, ey)
                g.quadraticCurveTo(cos(a + width) * cd, sin(a + width) * cd, 0.0, 0.0)
                g.stroke(primary, 1.0, alpha)
                if (alpha > 0.5) g.fill(primary, alpha * 0.2)
            }
            for (layer in 0 until 4) {
                val petals = 8 + layer * 4
                val len = petalLen * (1 - layer * 0.2)
                val wdt = 0.3 - layer * 0.05
                val off = layer * (PI / petals)
                for (i in 0 until petals) petal(i.toDouble() / petals * PI * 2 + off, len, wdt, 0.8 - layer * 0.15)
            }
            g.circle(0.0, 0.0, radius * 0.08).stroke(primary, 2.0, 0.8)
            g.circle(0.0, 0.0, radius * 0.03).fill(primary, 0.9)
            val stemR = radius * 0.65
            for (i in 0 until 3) {
                val sa = i / 3.0 * PI * 2
                g.moveTo(cos(sa) * stemR, sin(sa) * stemR)
                g.bezierCurveTo(cos(sa + 1) * stemR * 1.2, sin(sa + 1) * stemR * 1.2, cos(sa + 2) * stemR * 0.8, sin(sa + 2) * stemR * 0.8, cos(sa + 3) * stemR, sin(sa + 3) * stemR).stroke(primary, 1.0, 0.4)
                g.circle(cos(sa + 1.5) * stemR * 1.05, sin(sa + 1.5) * stemR * 1.05, 4.0).fill(primary, 0.6)
            }
        }
        13 -> {
            val sr = radius * 0.25
            g.circle(0.0, 0.0, sr).stroke(primary, 1.5, 0.35)
            for (i in 0 until 6) { val a = i / 6.0 * PI * 2; g.circle(cos(a) * sr, sin(a) * sr, sr).stroke(primary, 1.5, 0.2) }
            for (i in 0 until 12) {
                val a = i / 12.0 * PI * 2
                val d = if (i % 2 == 0) sr * 2 else sr * sqrt(3.0)
                g.circle(cos(a) * d, sin(a) * d, sr).stroke(secondary, 1.0, 0.1)
            }
            g.circle(0.0, 0.0, sr * 3).stroke(primary, 1.5, 0.2)
            g.circle(0.0, 0.0, sr * 3.1).stroke(secondary, 1.0, 0.08)
            for (i in 0 until 12) {
                val a = i / 12.0 * PI * 2
                g.moveTo(cos(a) * sr * 0.5, sin(a) * sr * 0.5).lineTo(cos(a) * sr * 3, sin(a) * sr * 3).stroke(secondary, 1.0, 0.05)
            }
        }
        14 -> {
            val d = if (seed % 2 == 0L) 1 else -1
            g.solidCuboid(radius * 0.18 * d, radius * 0.03, radius * 0.62, radius * 0.7, radius * 0.22 * d, -radius * 0.16, primary, 0.34)
            g.solidCuboid(-radius * 0.48 * d, radius * 0.24, radius * 0.28, radius * 0.38, radius * 0.12 * d, -radius * 0.09, primary, 0.24)
            g.solidCuboid(radius * 0.55 * d, -radius * 0.3, radius * 0.2, radius * 0.26, radius * 0.09 * d, -radius * 0.07, primary, 0.2)
        }
        15 -> {
            val d = if (seed % 2 == 0L) 1 else -1
            g.triPrism(-radius * 0.12 * d, radius * 0.02, radius * 0.72, radius * 0.68, radius * 0.18 * d, -radius * 0.13, primary, 0.34)
            g.triPrism(radius * 0.48 * d, radius * 0.26, radius * 0.28, radius * 0.25, -radius * 0.08 * d, -radius * 0.06, primary, 0.22)
            g.triPrism(-radius * 0.5 * d, -radius * 0.3, radius * 0.2, radius * 0.18, radius * 0.06 * d, -radius * 0.05, primary, 0.18)
        }
        16 -> {
            val d = if (seed % 2 == 0L) 1 else -1
            g.hexPrism(radius * 0.12 * d, 0.0, radius * 0.68, radius * 0.72, radius * 0.2 * d, -radius * 0.14, primary, 0.32)
            g.hexPrism(-radius * 0.48 * d, radius * 0.27, radius * 0.25, radius * 0.28, radius * 0.07 * d, -radius * 0.05, primary, 0.2)
            g.hexPrism(radius * 0.52 * d, -radius * 0.3, radius * 0.18, radius * 0.2, -radius * 0.06 * d, -radius * 0.045, primary, 0.17)
        }
        17 -> {
            val d = if (seed % 2 == 0L) 1 else -1
            g.trapPrism(radius * 0.12 * d, radius * 0.04, radius * 0.3, radius * 0.68, radius * 0.62, radius * 0.18 * d, -radius * 0.13, primary, 0.34)
            g.trapPrism(-radius * 0.42 * d, radius * 0.28, radius * 0.2, radius * 0.38, radius * 0.22, radius * 0.08 * d, -radius * 0.06, primary, 0.21)
            g.trapPrism(radius * 0.5 * d, -radius * 0.3, radius * 0.2, radius * 0.12, radius * 0.22, -radius * 0.06 * d, -radius * 0.05, primary, 0.18)
        }
        18 -> { // contour atlas
            val steps = 48
            for (ring in 0 until 8) {
                val baseR = radius * (0.16 + ring * 0.075)
                for (step in 0..steps) {
                    val a = step.toDouble() / steps * PI * 2
                    val ripple = sin(a * 3 + seed * 0.07 + ring) * radius * 0.018 + cos(a * 5 - seed * 0.03 + ring * 0.7) * radius * 0.012
                    val x = cos(a) * (baseR + ripple) + sin(ring * 1.7) * radius * 0.055
                    val y = sin(a) * (baseR + ripple) * 0.72 + cos(ring * 1.3) * radius * 0.035
                    if (step == 0) g.moveTo(x, y) else g.lineTo(x, y)
                }
                g.stroke(if (ring % 3 == 0) secondary else primary, if (ring % 3 == 0) 2.0 else 1.0, 0.2 + ring * 0.045)
            }
            g.moveTo(-radius * 0.78, radius * 0.52).lineTo(-radius * 0.58, radius * 0.52).lineTo(-radius * 0.58, radius * 0.46).lineTo(-radius * 0.38, radius * 0.46).stroke(primary, 3.0, 0.64)
        }
        19 -> { // radial wave
            val bars = 64
            for (i in 0 until bars) {
                val a = i.toDouble() / bars * PI * 2
                val signal = 0.5 + 0.5 * sin(i * 1.83 + seed * 0.11)
                val inner = radius * (0.29 + signal * 0.035)
                val outer = radius * (0.43 + signal * 0.21)
                g.moveTo(cos(a) * inner, sin(a) * inner).lineTo(cos(a) * outer, sin(a) * outer)
                    .stroke(if (i % 8 == 0) secondary else primary, if (i % 8 == 0) 3.0 else 1.0, if (i % 2 == 0) 0.58 else 0.32)
            }
            g.circle(0.0, 0.0, radius * 0.24).stroke(primary, 5.0, 0.7)
            g.circle(0.0, 0.0, radius * 0.68).stroke(secondary, 1.0, 0.18)
            g.circle(0.0, 0.0, radius * 0.72).stroke(primary, 2.0, 0.12)
        }
        20 -> { // transit blueprint
            val d = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            val routes = arrayOf(
                arrayOf(pt(-0.72, -0.38), pt(-0.38, -0.38), pt(-0.38, 0.08), pt(0.08, 0.08), pt(0.08, 0.52), pt(0.68, 0.52)),
                arrayOf(pt(-0.62, 0.58), pt(-0.62, 0.24), pt(-0.14, 0.24), pt(-0.14, -0.52), pt(0.5, -0.52), pt(0.5, -0.2), pt(0.74, -0.2)),
                arrayOf(pt(-0.78, -0.06), pt(-0.5, -0.06), pt(-0.5, -0.62), pt(0.24, -0.62), pt(0.24, 0.3), pt(0.72, 0.3)),
            )
            routes.forEachIndexed { ri, route ->
                g.moveTo(-bx * d, route[0][1] * radius)
                for (p in route) g.lineTo(p[0] * radius * d, p[1] * radius)
                g.lineTo(bx * d, route.last()[1] * radius)
                g.stroke(if (ri == 1) secondary else primary, if (ri == 0) 5.0 else 2.0, 0.34 + ri * 0.12)
                route.forEachIndexed { pi, p ->
                    if (pi == 0 || pi == route.size - 1 || (pi + ri) % 2 == 0) {
                        val px = p[0] * radius * d; val py = p[1] * radius
                        g.circle(px, py, if (pi == 0) 10.0 else 6.0).fill(if (pi % 2 == 0) primary else secondary, 0.72)
                        g.circle(px, py, if (pi == 0) 16.0 else 11.0).stroke(primary, 1.0, 0.36)
                    }
                }
            }
        }
        21 -> { // chronograph
            doubleArrayOf(0.2, 0.38, 0.62).forEachIndexed { i, sc ->
                g.circle(0.0, 0.0, radius * sc).stroke(if (i == 1) secondary else primary, if (i == 2) 4.0 else 2.0, 0.3 + i * 0.13)
            }
            for (tick in 0 until 48) {
                val a = tick / 48.0 * PI * 2
                val outer = radius * 0.72
                val inner = outer - radius * (if (tick % 4 == 0) 0.11 else 0.045)
                g.moveTo(cos(a) * inner, sin(a) * inner).lineTo(cos(a) * outer, sin(a) * outer)
                    .stroke(if (tick % 4 == 0) secondary else primary, if (tick % 4 == 0) 3.0 else 1.0, 0.5)
            }
            val hand = (seed % 60) / 60.0 * PI * 2 - PI / 2
            val sec = ((seed * 7) % 60) / 60.0 * PI * 2 - PI / 2
            g.moveTo(-cos(hand) * radius * 0.12, -sin(hand) * radius * 0.12).lineTo(cos(hand) * radius * 0.55, sin(hand) * radius * 0.55).stroke(primary, 6.0, 0.74)
            g.moveTo(0.0, 0.0).lineTo(cos(sec) * radius * 0.65, sin(sec) * radius * 0.65).stroke(secondary, 2.0, 0.72)
            g.circle(0.0, 0.0, radius * 0.055).fill(primary, 0.85)
        }
        22 -> { // folded ribbons
            val d = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            val by = max(radius * 0.92, height * 0.64)
            for (band in 0 until 5) {
                val y = (-0.5 + band * 0.25) * radius
                val off = (if (band % 2 == 0) 1 else -1) * d
                g.moveTo(-bx, y).bezierCurveTo(-radius * 0.38, y - radius * 0.28 * off, radius * 0.18, y + radius * 0.28 * off, bx, y)
                    .stroke(if (band % 2 == 0) primary else secondary, if (band == 2) 12.0 else 5.0, 0.24 + band * 0.08)
                val e = radius * 0.055
                g.moveTo(-bx, y + e).bezierCurveTo(-radius * 0.38, y - radius * 0.28 * off + e, radius * 0.18, y + radius * 0.28 * off + e, bx, y + e)
                    .stroke(primary, 1.0, 0.3)
            }
            g.moveTo(-radius * 0.34, -by).lineTo(-radius * 0.34, -radius * 0.18).stroke(primary, 2.0, 0.18)
            g.moveTo(radius * 0.34, radius * 0.18).lineTo(radius * 0.34, by).stroke(primary, 2.0, 0.18)
        }
        23 -> { // halftone poster
            val spacing = radius * 0.17
            val bx = max(radius * 0.92, width * 0.64)
            val by = max(radius * 0.92, height * 0.64)
            val columns = kotlin.math.ceil(bx * 2 / spacing).toInt() + 2
            val rows = kotlin.math.ceil(by * 2 / spacing).toInt() + 2
            for (row in 0 until rows) for (col in 0 until columns) {
                val x = (col - (columns - 1) / 2.0) * spacing
                val y = (row - (rows - 1) / 2.0) * spacing
                val dist = hypot(x, y) / radius
                val pulse = 0.5 + 0.5 * sin(col * 0.9 + row * 1.4 + seed * 0.08)
                val dr = radius * (0.009 + max(0.0, 0.72 - dist) * 0.034 + pulse * 0.012)
                g.circle(x, y, dr).fill(if ((row + col) % 5 == 0) secondary else primary, 0.24 + pulse * 0.5)
            }
            g.moveTo(-bx, -by * 0.72).lineTo(bx, -by * 0.72).stroke(secondary, 2.0, 0.28)
            g.moveTo(-bx * 0.58, -by).lineTo(-bx * 0.58, by).stroke(primary, 1.0, 0.2)
        }
        24 -> { // camellia
            val turn = (seed % 12) * PI / 72
            for (ring in 0 until 3) {
                val count = 7 + ring * 4
                for (i in 0 until count) {
                    val a = turn + i.toDouble() / count * PI * 2 + ring * 0.12
                    g.leaf(0.0, 0.0, radius * (0.28 + ring * 0.15), radius * (0.075 + ring * 0.018), a, if (ring == 1) secondary else primary, 0.07 + ring * 0.045)
                }
            }
            g.circle(0.0, 0.0, radius * 0.1).fill(secondary, 0.22)
            g.circle(0.0, 0.0, radius * 0.13).stroke(primary, 3.0, 0.68)
        }
        25 -> { // tulip field
            val d = if (seed % 2 == 0L) 1 else -1
            for (i in 0 until 7) {
                val x = (-0.66 + i * 0.22) * radius
                val top = (-0.3 + ((seed + i * 5) % 5) * 0.085) * radius
                val bottom = radius * 0.68
                g.moveTo(x, bottom).bezierCurveTo(x + radius * 0.04 * d, radius * 0.28, x - radius * 0.05 * d, top + radius * 0.12, x, top).stroke(if (i % 2 != 0) secondary else primary, 2.0, 0.5)
                g.leaf(x, radius * 0.24, radius * 0.26, radius * 0.055, if (i % 2 != 0) -2.7 else -0.45, primary, 0.1)
                val bloom = if (i % 3 == 0) secondary else primary
                g.moveTo(x, top + radius * 0.14).quadraticCurveTo(x - radius * 0.18, top - radius * 0.04, x - radius * 0.11, top - radius * 0.2)
                    .lineTo(x, top - radius * 0.1).lineTo(x + radius * 0.11, top - radius * 0.2)
                    .quadraticCurveTo(x + radius * 0.18, top - radius * 0.04, x, top + radius * 0.14).fill(bloom, 0.12 + (i % 3) * 0.045)
                g.moveTo(x, top + radius * 0.14).quadraticCurveTo(x - radius * 0.18, top - radius * 0.04, x - radius * 0.11, top - radius * 0.2)
                    .lineTo(x, top - radius * 0.1).lineTo(x + radius * 0.11, top - radius * 0.2)
                    .quadraticCurveTo(x + radius * 0.18, top - radius * 0.04, x, top + radius * 0.14).stroke(bloom, 2.0, 0.65)
            }
        }
        26 -> { // wildflower
            for (stem in 0 until 9) {
                val x = (-0.72 + stem * 0.18) * radius
                val lean = (((seed + stem * 7) % 9) - 4) * radius * 0.018
                val fy = (-0.45 + ((seed + stem * 3) % 6) * 0.08) * radius
                g.moveTo(x, radius * 0.72).quadraticCurveTo(x - lean, radius * 0.12, x + lean, fy).stroke(if (stem % 2 != 0) secondary else primary, 1.5, 0.42)
                for (p in 0 until 5) {
                    val a = p / 5.0 * PI * 2 - PI / 2
                    g.leaf(x + lean, fy, radius * 0.105, radius * 0.032, a, if (stem % 3 != 0) primary else secondary, 0.1)
                }
                g.circle(x + lean, fy, radius * 0.025).fill(secondary, 0.48)
            }
        }
        27 -> { // fern
            val tilt = (if (seed % 2 != 0L) 1 else -1) * 0.18
            g.moveTo(-radius * 0.12, radius * 0.72).bezierCurveTo(-radius * 0.04, radius * 0.2, radius * 0.12, -radius * 0.24, radius * 0.02, -radius * 0.72).stroke(primary, 3.0, 0.62)
            for (i in 0 until 13) {
                val r = i / 13.0
                val x = -radius * 0.12 + radius * 0.14 * r
                val y = radius * (0.63 - r * 1.23)
                val len = radius * (0.3 - abs(r - 0.5) * 0.18)
                g.leaf(x, y, len, len * 0.22, PI + tilt - r * 0.25, if (i % 3 != 0) primary else secondary, 0.09 + r * 0.05)
                g.leaf(x, y, len, len * 0.22, -tilt + r * 0.25, if (i % 3 != 0) secondary else primary, 0.07 + r * 0.04)
            }
        }
        28 -> { // ginkgo
            val d = if (seed % 2 == 0L) 1 else -1
            g.moveTo(-radius * 0.72 * d, radius * 0.55).bezierCurveTo(-radius * 0.25 * d, radius * 0.16, radius * 0.08 * d, -radius * 0.12, radius * 0.65 * d, -radius * 0.5).stroke(primary, 5.0, 0.38)
            for (i in 0 until 8) {
                val r = i / 7.0
                val x = (-0.58 + r * 1.12) * radius * d
                val y = (0.4 - r * 0.78 + sin(i * 1.8) * 0.08) * radius
                val a = -1.1 + (i % 3) * 0.7
                val size = radius * (0.13 + (i % 4) * 0.018)
                g.moveTo(x, y).lineTo(x + cos(a) * size * 0.7, y + sin(a) * size * 0.7).stroke(secondary, 1.5, 0.42)
                val cx = x + cos(a) * size; val cy = y + sin(a) * size
                g.moveTo(cx, cy).arc(cx, cy, size, a + PI * 0.1, a + PI * 0.9).lineTo(cx, cy).fill(if (i % 2 != 0) primary else secondary, 0.09 + (i % 3) * 0.04)
                g.moveTo(cx, cy).arc(cx, cy, size, a + PI * 0.1, a + PI * 0.9).lineTo(cx, cy).stroke(primary, 1.5, 0.58)
            }
        }
        29 -> { // climbing vine
            val m = if (seed % 2 == 0L) 1 else -1
            for (vine in 0 until 3) {
                val off = (vine - 1) * radius * 0.3
                g.moveTo(off, radius * 0.76).bezierCurveTo(off + radius * 0.5 * m, radius * 0.38, off - radius * 0.48 * m, -radius * 0.1, off + radius * 0.22 * m, -radius * 0.76)
                    .stroke(if (vine == 1) secondary else primary, if (vine == 1) 3.0 else 1.5, 0.46)
                for (lf in 0 until 5) {
                    val r = (lf + 1) / 6.0
                    val x = off + sin(r * PI * 4 + vine) * radius * 0.16
                    val y = radius * (0.7 - r * 1.36)
                    g.leaf(x, y, radius * 0.2, radius * 0.055, if (lf % 2 != 0) -0.25 else PI + 0.25, if (lf % 2 != 0) secondary else primary, 0.08 + vine * 0.035)
                }
            }
        }
        30 -> { // greenhouse
            val d = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            val shell = arrayOf(pt(-radius * 0.7, radius * 0.58), pt(-radius * 0.7, -radius * 0.12), pt(0.0, -radius * 0.62), pt(radius * 0.7, -radius * 0.12), pt(radius * 0.7, radius * 0.58))
            g.fillPolygon(shell, primary, 0.055)
            g.strokePolygon(shell, primary, 0.68, 3.0)
            g.moveTo(0.0, -radius * 0.62).lineTo(0.0, radius * 0.58).stroke(secondary, 2.0, 0.5)
            for (pane in -3..3) {
                val x = pane * radius * 0.18
                g.moveTo(x, radius * 0.58).lineTo(x * 0.38, -radius * (0.58 - abs(pane) * 0.04)).stroke(if (pane % 2 != 0) secondary else primary, 1.0, 0.32)
            }
            val doorX = radius * 0.2 * d
            g.rect(doorX - radius * 0.13, radius * 0.08, radius * 0.26, radius * 0.5).fill(secondary, 0.1)
            g.rect(doorX - radius * 0.13, radius * 0.08, radius * 0.26, radius * 0.5).stroke(secondary, 2.0, 0.62)
            g.moveTo(-bx, radius * 0.58).lineTo(-radius * 0.7, radius * 0.58).stroke(primary, 1.0, 0.3)
            g.moveTo(radius * 0.7, radius * 0.58).lineTo(bx, radius * 0.58).stroke(primary, 1.0, 0.3)
        }
        31 -> { // pagoda
            val lean = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            for (fl in 0 until 4) {
                val y = radius * (0.47 - fl * 0.27)
                val hw = radius * (0.5 - fl * 0.075)
                val roof = arrayOf(pt(-hw * 1.18, y), pt(-hw, y - radius * 0.11), pt(0.0, y - radius * 0.19), pt(hw, y - radius * 0.11), pt(hw * 1.18, y))
                val c = if (fl % 2 != 0) secondary else primary
                g.fillPolygon(roof, c, 0.07 + fl * 0.025)
                g.strokePolygon(roof, c, 0.58, 2.0)
                g.rect(-hw * 0.68, y, hw * 1.36, radius * 0.17).fill(primary, 0.035 + fl * 0.018)
                g.rect(-hw * 0.68, y, hw * 1.36, radius * 0.17).stroke(primary, 1.0, 0.36)
            }
            g.moveTo(0.0, -radius * 0.62).lineTo(radius * 0.035 * lean, -radius * 0.78).stroke(secondary, 3.0, 0.65)
            g.moveTo(-bx, radius * 0.64).lineTo(-radius * 0.52, radius * 0.64).stroke(secondary, 1.0, 0.22)
            g.moveTo(radius * 0.52, radius * 0.64).lineTo(bx, radius * 0.64).stroke(secondary, 1.0, 0.22)
        }
        32 -> { // city facade
            val bx = max(radius * 0.92, width * 0.64)
            val heights = doubleArrayOf(0.52, 0.88, 0.66, 1.08, 0.74, 0.94, 0.58)
            val bw = radius * 0.205
            heights.forEachIndexed { i, hr ->
                val x = radius * (-0.73 + i * 0.24)
                val hh = radius * hr
                val c = if (i % 3 == 1) secondary else primary
                g.rect(x, radius * 0.62 - hh, bw, hh).fill(c, 0.045 + (i % 3) * 0.035)
                g.rect(x, radius * 0.62 - hh, bw, hh).stroke(c, if (i == 3) 3.0 else 1.5, 0.5)
                for (row in 0 until floor(hr * 6).toInt()) for (col in 0 until 2) {
                    if ((row + col + i + seed) % 3 == 0L) {
                        g.rect(x + radius * (0.035 + col * 0.085), radius * 0.53 - hh + row * radius * 0.13, radius * 0.045, radius * 0.055).fill(if (col != 0) secondary else primary, 0.2)
                    }
                }
            }
            g.moveTo(-bx, radius * 0.63).lineTo(bx, radius * 0.63).stroke(primary, 4.0, 0.48)
        }
        33 -> { // terraces
            val d = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            for (band in 0 until 7) {
                val y = radius * (-0.5 + band * 0.16)
                val amp = radius * (0.09 + band * 0.012)
                val c = if (band % 2 != 0) secondary else primary
                g.moveTo(-bx, y).bezierCurveTo(-radius * 0.35, y + amp * d, radius * 0.08, y - amp * d, bx, y + amp * 0.35)
                    .lineTo(bx, y + radius * 0.12)
                    .bezierCurveTo(radius * 0.12, y + radius * 0.04, -radius * 0.3, y + radius * 0.2, -bx, y + radius * 0.12)
                    .fill(c, 0.025 + band * 0.018)
                g.moveTo(-bx, y).bezierCurveTo(-radius * 0.35, y + amp * d, radius * 0.08, y - amp * d, bx, y + amp * 0.35)
                    .stroke(c, if (band % 3 == 0) 2.5 else 1.0, 0.34 + band * 0.04)
            }
        }
        34 -> { // mountain lake
            val bx = max(radius * 0.92, width * 0.64)
            val shift = ((seed % 7) - 3) / 3.0 * radius * 0.04
            val back = arrayOf(pt(-bx, radius * 0.1), pt(-radius * 0.42, -radius * 0.46), pt(-radius * 0.14, -radius * 0.16), pt(radius * 0.22, -radius * 0.62), pt(bx, radius * 0.1))
            val front = arrayOf(pt(-bx, radius * 0.22), pt(-radius * 0.28 + shift, -radius * 0.2), pt(radius * 0.06, radius * 0.05), pt(radius * 0.48 + shift, -radius * 0.28), pt(bx, radius * 0.22))
            g.fillPolygon(back, secondary, 0.07)
            g.strokePolygon(back, secondary, 0.46, 1.5)
            g.fillPolygon(front, primary, 0.12)
            g.strokePolygon(front, primary, 0.66, 2.5)
            for (line in 0 until 7) {
                val y = radius * (0.28 + line * 0.07)
                val inset = radius * (0.08 + (line % 3) * 0.08)
                g.moveTo(-bx + inset, y).lineTo(bx - inset, y).stroke(if (line % 2 != 0) secondary else primary, 1.0, 0.2 + line * 0.035)
            }
            g.circle(-radius * 0.48, -radius * 0.48, radius * 0.1).fill(secondary, 0.14)
            g.circle(-radius * 0.48, -radius * 0.48, radius * 0.13).stroke(secondary, 2.0, 0.5)
        }
        35 -> { // coastal cliff
            val d = if (seed % 2 == 0L) 1 else -1
            val bx = max(radius * 0.92, width * 0.64)
            val by = max(radius * 0.92, height * 0.64)
            val cliff = arrayOf(pt(-bx * d, by), pt(-radius * 0.78 * d, -radius * 0.1), pt(-radius * 0.5 * d, -radius * 0.34), pt(-radius * 0.18 * d, radius * 0.08), pt(radius * 0.08 * d, radius * 0.58))
            g.fillPolygon(cliff, primary, 0.11)
            g.strokePolygon(cliff, primary, 0.62, 2.5)
            val tx = -radius * 0.5 * d
            g.rect(tx - radius * 0.09, -radius * 0.42, radius * 0.18, radius * 0.45).fill(secondary, 0.12)
            g.rect(tx - radius * 0.09, -radius * 0.42, radius * 0.18, radius * 0.45).stroke(secondary, 2.0, 0.7)
            g.moveTo(tx - radius * 0.14, -radius * 0.42).lineTo(tx, -radius * 0.56).lineTo(tx + radius * 0.14, -radius * 0.42).fill(secondary, 0.2)
            g.moveTo(tx - radius * 0.14, -radius * 0.42).lineTo(tx, -radius * 0.56).lineTo(tx + radius * 0.14, -radius * 0.42).lineTo(tx - radius * 0.14, -radius * 0.42).stroke(secondary, 2.0, 0.72)
            for (wave in 0 until 6) {
                val y = radius * (0.14 + wave * 0.1)
                g.moveTo(-radius * 0.05 * d, y).quadraticCurveTo(radius * 0.35 * d, y - radius * 0.08, bx * d, y)
                    .stroke(if (wave % 2 != 0) secondary else primary, if (wave % 3 == 0) 2.0 else 1.0, 0.26 + wave * 0.045)
            }
        }
    }
}

// ───────────────────────────── HUD 外框（sonnetBackgroundMgVariants.ts） ─────────────────────────────

private fun hudCross(g: SonnetAnimGraphics, x: Double, y: Double, s: Double, c: Int, a: Double) {
    g.moveTo(x - s, y - s).lineTo(x + s, y + s).stroke(c, 1.0, a)
    g.moveTo(x + s, y - s).lineTo(x - s, y + s).stroke(c, 1.0, a)
}

private fun drawHud(g: SonnetAnimGraphics, variant: Int, width: Double, height: Double, seed: Long, primary: Int, secondary: Int) {
    val hw = width / 2; val hh = height / 2; val mx = width * 0.05; val my = height * 0.05
    when (variant % 8) {
        1 -> {
            val arm = min(hw, hh) * 0.08; val inset = 6.0
            val corners = listOf(doubleArrayOf(-hw + mx, -hh + my, 1.0, 1.0), doubleArrayOf(hw - mx, -hh + my, -1.0, 1.0), doubleArrayOf(-hw + mx, hh - my, 1.0, -1.0), doubleArrayOf(hw - mx, hh - my, -1.0, -1.0))
            corners.forEachIndexed { i, c ->
                val (cx, cy, sx, sy) = c.toList()
                g.moveTo(cx + sx * arm, cy).lineTo(cx, cy).lineTo(cx, cy + sy * arm).stroke(primary, 2.0, 0.55)
                g.moveTo(cx + sx * (arm + inset), cy + sy * inset).lineTo(cx + sx * inset, cy + sy * inset).lineTo(cx + sx * inset, cy + sy * (arm + inset)).stroke(primary, 1.0, 0.25)
                if (i % 2 == 0) g.rect(cx + sx * arm * 0.4 - 2, cy + sy * arm * 0.4 - 2, 4.0, 4.0).fill(secondary, 0.6)
            }
            val ry = hh - my + inset
            g.moveTo(-hw + mx + arm + 12, ry).lineTo(hw - mx - arm - 12, ry).stroke(primary, 1.0, 0.3)
            val span = (hw - mx - arm - 12) * 2
            for (i in 0..24) {
                val x = -hw + mx + arm + 12 + span * i / 24
                val long = i % 6 == 0
                g.moveTo(x, ry).lineTo(x, ry - (if (long) 8 else 4)).stroke(if (long) secondary else primary, 1.0, if (long) 0.55 else 0.3)
            }
            for (i in 0 until 5) {
                val y = -hh + my + arm + 14 + i * ((hh - my - arm - 14) * 2) / 5
                g.circle(-hw + mx - 4, y, 1.5).fill(primary, 0.35)
                g.circle(hw - mx + 4, y + (seed % 7), 1.5).fill(primary, 0.35)
            }
        }
        2 -> {
            val left = -hw + mx; val right = hw - mx
            for (dir in intArrayOf(-1, 1)) {
                val y = dir * (hh - my)
                g.moveTo(left, y).lineTo(right, y).stroke(primary, 2.0, 0.45)
                g.moveTo(left, y + dir * 6).lineTo(right, y + dir * 6).stroke(primary, 1.0, 0.2)
                g.rect(left, y - 3, 14.0, 6.0).fill(secondary, 0.55)
                g.rect(right - 14, y - 3, 14.0, 6.0).fill(secondary, 0.55)
                for (i in 1 until 18) {
                    val x = left + (right - left) * i / 18
                    if (i % 3 == 0) g.moveTo(x, y).lineTo(x, y + dir * 6).stroke(primary, 1.0, 0.35)
                }
            }
            hudCross(g, 0.0, -hh + my, 4.0, primary, 0.5)
            g.moveTo(-6.0, hh - my - 14).lineTo(0.0, hh - my - 8).lineTo(6.0, hh - my - 14).stroke(secondary, 1.0, 0.6)
        }
        3 -> {
            val corner = min(hw, hh) * 0.12
            val corners = listOf(doubleArrayOf(-hw + mx, -hh + my, 1.0, 1.0), doubleArrayOf(hw - mx, -hh + my, -1.0, 1.0), doubleArrayOf(-hw + mx, hh - my, 1.0, -1.0), doubleArrayOf(hw - mx, hh - my, -1.0, -1.0))
            corners.forEachIndexed { i, c ->
                val (cx, cy, sx, sy) = c.toList()
                g.moveTo(cx + sx * corner, cy).lineTo(cx, cy).lineTo(cx, cy + sy * corner).lineTo(cx + sx * corner, cy).stroke(primary, 1.5, 0.5)
                g.moveTo(cx + sx * corner * 0.55, cy).lineTo(cx, cy + sy * corner * 0.55).stroke(if (i % 2 == 0) secondary else primary, 1.0, 0.4)
                g.moveTo(cx + sx * corner * 0.3, cy).lineTo(cx, cy + sy * corner * 0.3).stroke(primary, 1.0, 0.25)
            }
            val by = hh - my - 12
            g.moveTo(-hw + mx + corner + 10, by).lineTo(hw - mx - corner - 10, by).stroke(primary, 1.0, 0.3)
            for (i in 0 until 5) {
                val x = -hw + mx + corner + 30 + i * 26
                val s = if (i == 2) 5.0 else 3.0
                g.moveTo(x, by - s).lineTo(x + s, by).lineTo(x, by + s).lineTo(x - s, by).lineTo(x, by - s).stroke(if (i == 2) secondary else primary, 1.0, 0.55)
            }
        }
        4 -> {
            for (i in 0 until 14) {
                val y = -hh + my + 10 + i * ((hh - my - 10) * 2) / 13
                val strong = i % 4 == 0
                g.circle(-hw + mx, y, if (strong) 2.4 else 1.4).fill(if (strong) secondary else primary, if (strong) 0.6 else 0.3)
                g.circle(hw - mx, y, if (strong) 2.4 else 1.4).fill(if (strong) secondary else primary, if (strong) 0.6 else 0.3)
            }
            g.moveTo(-18.0, 0.0).lineTo(18.0, 0.0).stroke(primary, 1.0, 0.22)
            g.moveTo(0.0, -18.0).lineTo(0.0, 18.0).stroke(primary, 1.0, 0.22)
            g.circle(0.0, 0.0, 6.0).stroke(primary, 1.0, 0.3)
            val by = hh - my - 8
            g.moveTo(-hw + mx + 16, by).lineTo(hw - mx - 16, by).stroke(primary, 1.0, 0.3)
            hudCross(g, -hw + mx + 8, by, 3.0, primary, 0.5)
            hudCross(g, hw - mx - 8, by, 3.0, primary, 0.5)
        }
        5 -> {
            val left = -hw + mx; val right = hw - mx; val top = -hh + my; val bottom = hh - my
            val gx = (right - left) * 0.18; val gy = (bottom - top) * 0.22
            val cx = (left + right) / 2; val cy = (top + bottom) / 2
            g.moveTo(left, top).lineTo(cx - gx / 2, top).stroke(primary, 2.0, 0.45)
            g.moveTo(cx + gx / 2, top).lineTo(right, top).stroke(primary, 2.0, 0.45)
            g.moveTo(left, bottom).lineTo(cx - gx / 2, bottom).stroke(primary, 2.0, 0.45)
            g.moveTo(cx + gx / 2, bottom).lineTo(right, bottom).stroke(primary, 2.0, 0.45)
            g.moveTo(left, top).lineTo(left, cy - gy / 2).stroke(primary, 2.0, 0.45)
            g.moveTo(left, cy + gy / 2).lineTo(left, bottom).stroke(primary, 2.0, 0.45)
            g.moveTo(right, top).lineTo(right, cy - gy / 2).stroke(primary, 2.0, 0.45)
            g.moveTo(right, cy + gy / 2).lineTo(right, bottom).stroke(primary, 2.0, 0.45)
            g.rect(left + 7, top + 7, right - left - 14, bottom - top - 14).stroke(primary, 1.0, 0.18)
            listOf(pt(left, top), pt(right, top), pt(left, bottom), pt(right, bottom)).forEachIndexed { i, p ->
                g.rect(p[0] - 3, p[1] - 3, 6.0, 6.0).fill(if (i % 2 == 0) secondary else primary, 0.6)
            }
            g.moveTo(cx - 5, top).lineTo(cx + 5, top).stroke(secondary, 3.0, 0.5)
            g.moveTo(cx - 5, bottom).lineTo(cx + 5, bottom).stroke(secondary, 3.0, 0.5)
        }
        6 -> {
            val left = -hw + mx; val right = hw - mx; val top = -hh + my; val bottom = hh - my
            for (i in 0..32) {
                val x = left + (right - left) * i / 32
                val major = i % 8 == 0; val mid = i % 4 == 0
                val len = if (major) 12.0 else if (mid) 7.0 else 4.0
                val c = if (major) secondary else primary
                g.moveTo(x, top).lineTo(x, top + len).stroke(c, 1.0, if (major) 0.55 else 0.32)
                g.moveTo(x, bottom).lineTo(x, bottom - len).stroke(c, 1.0, if (major) 0.55 else 0.32)
            }
            for (i in 0..18) {
                val y = top + (bottom - top) * i / 18
                val major = i % 6 == 0
                val len = if (major) 12.0 else if (i % 3 == 0) 7.0 else 4.0
                val c = if (major) secondary else primary
                g.moveTo(left, y).lineTo(left + len, y).stroke(c, 1.0, if (major) 0.55 else 0.32)
                g.moveTo(right, y).lineTo(right - len, y).stroke(c, 1.0, if (major) 0.55 else 0.32)
            }
            g.circle(0.0, 0.0, 10.0).stroke(primary, 1.0, 0.25)
            g.circle(0.0, 0.0, 3.0).fill(secondary, 0.4)
        }
        7 -> {
            val arcR = min(hw, hh) * 0.11
            val corners = listOf(doubleArrayOf(-hw + mx, -hh + my, 0.0, PI / 2), doubleArrayOf(hw - mx, -hh + my, PI / 2, PI), doubleArrayOf(hw - mx, hh - my, PI, PI * 1.5), doubleArrayOf(-hw + mx, hh - my, PI * 1.5, PI * 2))
            fun arcFrom(cx: Double, cy: Double, r: Double, s: Double, e: Double) = g.moveTo(cx + cos(s) * r, cy + sin(s) * r).arc(cx, cy, r, s, e)
            corners.forEachIndexed { i, c ->
                val (cx, cy, s, e) = c.toList()
                arcFrom(cx, cy, arcR, s, e).stroke(primary, 2.0, 0.5)
                arcFrom(cx, cy, arcR * 0.72, s, e).stroke(primary, 1.0, 0.25)
                val mid = (s + e) / 2
                g.circle(cx + cos(mid) * arcR, cy + sin(mid) * arcR, 2.0).fill(if (i % 2 == 0) secondary else primary, 0.6)
            }
            g.circle(0.0, -hh + my, 2.0).fill(primary, 0.45)
            g.circle(-hw + mx, 0.0, 2.0).fill(primary, 0.45)
            g.circle(hw - mx, 0.0, 2.0).fill(primary, 0.45)
            val gy = hh - my + arcR * 0.4
            val gr = min(hw, hh) * 0.16
            arcFrom(0.0, gy, gr, PI, PI * 2).stroke(primary, 1.5, 0.4)
            for (i in 0..8) {
                val a = PI + i / 8.0 * PI
                g.moveTo(cos(a) * (gr - 5), gy + sin(a) * (gr - 5)).lineTo(cos(a) * gr, gy + sin(a) * gr).stroke(if (i % 4 == 0) secondary else primary, 1.0, 0.45)
            }
            val needle = PI + ((seed % 100) / 100.0) * PI
            g.moveTo(0.0, gy).lineTo(cos(needle) * (gr - 8), gy + sin(needle) * (gr - 8)).stroke(secondary, 2.0, 0.6)
            g.circle(0.0, gy, 2.5).fill(primary, 0.7)
        }
        else -> {
            val size = 4.0
            hudCross(g, -hw + mx, -hh + my, size, primary, 0.4)
            hudCross(g, hw - mx, -hh + my, size, primary, 0.4)
            hudCross(g, -hw + mx, hh - my, size, primary, 0.4)
            hudCross(g, hw - mx, hh - my, size, primary, 0.4)
            for (i in 0 until 8) hudCross(g, -hw + mx, -hh + my + i * 20 + 30, 3.0, primary, 0.3)
            val by = hh - my - 10
            g.moveTo(-hw + mx + 20, by).lineTo(hw - mx - 20, by).stroke(primary, 1.0, 0.3)
            hudCross(g, -hw + mx + 10, by, 3.0, primary, 0.5)
            hudCross(g, -hw + mx + 30, by, 3.0, primary, 0.5)
            hudCross(g, hw - mx - 10, by, 3.0, primary, 0.5)
            g.circle(0.0, by, 2.0).fill(secondary, 0.8)
        }
    }
}

// ───────────────────────────── 定格几何（sonnetFixedGeoVariants.ts） ─────────────────────────────

/** 一块带遮罩的斜线填充，或主体几何（mask = null）。 */
internal class SonnetFixedPart(val g: SonnetAnimGraphics, val mask: RectF?)

private fun buildFixedGeo(variant: Int, r: Double, seed: Long, primary: Int, secondary: Int): List<SonnetFixedPart> {
    val geo = SonnetAnimGraphics(seed * 31 + 7)
    val parts = arrayListOf(SonnetFixedPart(geo, null))
    val accent = if (seed % 2 == 0L) secondary else primary
    fun hatch(x: Double, y: Double, w: Double, h: Double, spacing: Double = 6.0) {
        val g = SonnetAnimGraphics(seed + parts.size)
        var i = -w
        while (i < w + h) { g.moveTo(x + i, y).lineTo(x + i + h, y + h).stroke(primary, 1.0, 0.15); i += spacing }
        parts += SonnetFixedPart(g, RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()))
    }
    val d = if (seed % 2 == 0L) 1 else -1
    when (variant % 8) {
        1 -> {
            geo.rect(-r * 0.34, -r * 0.28, r * 0.12, r * 0.56).fill(accent, 0.65)
            geo.rect(-r * 0.34 + r * 0.035, -r * 0.28 + r * 0.035, r * 0.05, r * 0.49).fill(primary, 0.35)
            geo.rect(r * 0.06, -r * 0.34, r * 0.28, r * 0.68).stroke(primary, 2.0, 0.6)
            geo.rect(r * 0.06 + r * 0.04, -r * 0.34 + r * 0.04, r * 0.2, r * 0.6).stroke(primary, 1.0, 0.3)
            hatch(-r * 0.14, -r * 0.2, r * 0.12, r * 0.4, 5.0)
        }
        2 -> {
            geo.circle(-r * 0.2, r * 0.12, r * 0.15).fill(accent, 0.7)
            geo.circle(-r * 0.2, r * 0.12, r * 0.06).fill(primary, 0.5)
            geo.circle(r * 0.14, -r * 0.06, r * 0.3).stroke(primary, 2.0, 0.6)
            geo.circle(r * 0.14, -r * 0.06, r * 0.22).stroke(primary, 1.0, 0.3)
            hatch(r * 0.02, -r * 0.14, r * 0.24, r * 0.16, 5.0)
        }
        3 -> {
            val dr = r * 0.3; val cx = -r * 0.08 * d
            geo.moveTo(cx, -dr).lineTo(cx + dr, 0.0).lineTo(cx, dr).lineTo(cx - dr, 0.0).lineTo(cx, -dr).stroke(primary, 2.0, 0.6)
            geo.moveTo(cx, -dr * 0.7).lineTo(cx + dr * 0.7, 0.0).lineTo(cx, dr * 0.7).lineTo(cx - dr * 0.7, 0.0).lineTo(cx, -dr * 0.7).stroke(primary, 1.0, 0.3)
            val sr = r * 0.11; val sx = r * 0.3 * d; val sy = -r * 0.2
            geo.moveTo(sx, sy - sr).lineTo(sx + sr, sy).lineTo(sx, sy + sr).lineTo(sx - sr, sy).lineTo(sx, sy - sr).fill(accent, 0.7)
            hatch(sx - sr * 0.8, r * 0.16, sr * 1.6, sr * 1.2, 4.0)
        }
        4 -> {
            geo.rect(-r * 0.36 * d - r * 0.2, -r * 0.26, r * 0.56, r * 0.09).fill(accent, 0.7)
            geo.rect(-r * 0.28, -r * 0.06, r * 0.56, r * 0.16).stroke(primary, 2.0, 0.6)
            geo.rect(-r * 0.2 * d, r * 0.2, r * 0.4, r * 0.045).fill(primary, 0.5)
            hatch(r * 0.26 * d, -r * 0.3, r * 0.12, r * 0.6, 5.0)
        }
        5 -> {
            val arm = r * 0.24; val thick = r * 0.07
            val x1 = -r * 0.3 * d; val y1 = -r * 0.24
            geo.rect(x1 - (if (d < 0) arm else 0.0), y1, arm, thick).fill(accent, 0.7)
            geo.rect(if (d < 0) x1 - arm else x1, y1, thick, arm).fill(accent, 0.7)
            val x2 = r * 0.3 * d; val y2 = r * 0.24
            geo.rect(if (d < 0) x2 else x2 - arm, y2 - thick, arm, thick).fill(primary, 0.55)
            geo.rect(if (d < 0) x2 + arm - thick else x2 - thick, y2 - arm, thick, arm).fill(primary, 0.55)
            geo.rect(-r * 0.13, -r * 0.13, r * 0.26, r * 0.26).stroke(primary, 2.0, 0.6)
            hatch(-r * 0.09 * d, r * 0.02, r * 0.16, r * 0.1, 4.0)
        }
        6 -> {
            val wx = -r * 0.14 * d
            geo.moveTo(wx, -r * 0.3).lineTo(wx + r * 0.24, r * 0.02).lineTo(wx - r * 0.24, r * 0.02).lineTo(wx, -r * 0.3).fill(accent, 0.6)
            val hx = r * 0.16 * d
            geo.moveTo(hx, r * 0.3).lineTo(hx + r * 0.24, -r * 0.02).lineTo(hx - r * 0.24, -r * 0.02).lineTo(hx, r * 0.3).stroke(primary, 2.0, 0.6)
            geo.moveTo(hx, r * 0.2).lineTo(hx + r * 0.15, 0.0).lineTo(hx - r * 0.15, 0.0).lineTo(hx, r * 0.2).stroke(primary, 1.0, 0.3)
            hatch(-r * 0.3 * d - r * 0.05, r * 0.1, r * 0.2, r * 0.18, 5.0)
        }
        7 -> {
            val cx = (seed % 3 - 1) * r * 0.08
            val arm = r * 0.17; val thick = r * 0.075
            geo.rect(cx - arm, -thick / 2, arm * 2, thick).fill(accent, 0.7)
            geo.rect(cx - thick / 2, -arm, thick, arm * 2).fill(accent, 0.7)
            geo.circle(cx, 0.0, r * 0.3).stroke(primary, 2.0, 0.6)
            geo.circle(cx, 0.0, r * 0.36).stroke(primary, 1.0, 0.25)
            hatch(cx + r * 0.18, r * 0.14, r * 0.16, r * 0.16, 4.0)
        }
        else -> {
            geo.rect(-r * 0.4, -r * 0.2, r * 0.6, r * 0.15).fill(primary, 0.7)
            geo.rect(-r * 0.1, r * 0.1, r * 0.5, r * 0.3).stroke(primary, 2.0, 0.6)
            hatch(-r * 0.3, -r * 0.4, r * 0.4, r * 0.25)
        }
    }
    return parts
}

// ───────────────────────────── 漂浮小形 + 图标（sonnetBackgroundDecor.ts、sonnetIcons.ts） ─────────────────────────────

internal class SonnetParticle(val x: Double, val y: Double, val rotation: Double, val shape: SonnetAnimGraphics?, val icon: SonnetIcon?)

/** 主题图标精灵：Lucide Flower（Folia 没有主题图标时的兜底），24 格画布，宽 = pSize×7。 */
internal class SonnetIcon(val size: Double, val color: Int, val preferredDuration: Double, val phase: Double) {
    var entryPhase = 0.12
}

private val SHAPE_PALETTES = listOf(
    listOf("square", "diamond", "sparkle"), listOf("ring", "hexagon", "dot"), listOf("bar", "plus", "square"),
    listOf("triangle", "diamond", "plus"), listOf("dot", "ring", "sparkle"), listOf("chevron", "bar", "hexagon"),
)

private fun decorShape(g: SonnetAnimGraphics, shape: String, p: Double, color: Int, alpha: Double) {
    when (shape) {
        "diamond" -> g.moveTo(0.0, -p).lineTo(p, 0.0).lineTo(0.0, p).lineTo(-p, 0.0).fill(color, alpha * 0.85)
        "sparkle" -> g.moveTo(0.0, -p * 1.5).quadraticCurveTo(0.0, 0.0, p * 1.5, 0.0).quadraticCurveTo(0.0, 0.0, 0.0, p * 1.5)
            .quadraticCurveTo(0.0, 0.0, -p * 1.5, 0.0).quadraticCurveTo(0.0, 0.0, 0.0, -p * 1.5).fill(color, alpha * 1.2)
        "plus" -> { val arm = p * 0.34; g.rect(-p, -arm, p * 2, arm * 2).fill(color, alpha * 0.9); g.rect(-arm, -p, arm * 2, p * 2).fill(color, alpha * 0.9) }
        "ring" -> g.circle(0.0, 0.0, p).stroke(color, max(1.0, p * 0.22), alpha * 0.9)
        "triangle" -> g.moveTo(0.0, -p).lineTo(p * 0.9, p * 0.7).lineTo(-p * 0.9, p * 0.7).lineTo(0.0, -p).fill(color, alpha * 0.85)
        "hexagon" -> {
            for (j in 0..6) { val a = j * PI / 3; val x = sin(a) * p; val y = -cos(a) * p; if (j == 0) g.moveTo(x, y) else g.lineTo(x, y) }
            g.stroke(color, max(1.0, p * 0.16), alpha)
        }
        "bar" -> g.rect(-p, -p * 0.18, p * 2, p * 0.36).fill(color, alpha * 0.85)
        "dot" -> g.circle(0.0, 0.0, p * 0.34).fill(color, alpha)
        "chevron" -> g.moveTo(-p * 0.5, -p * 0.55).lineTo(p * 0.35, 0.0).lineTo(-p * 0.5, p * 0.55).stroke(color, max(1.5, p * 0.2), alpha)
        else -> g.rect(-p / 2, -p / 2, p, p).fill(color, alpha)
    }
}

private fun iconParticleIndices(iconCount: Int, particleCount: Int, seed: Long): List<Int?> {
    if (iconCount == 0) return List(particleCount) { null }
    val iconParticleCount = min(particleCount, max(ceil1(particleCount / 4.0), iconCount))
    var emitted = 0
    return List(particleCount) { index ->
        val prev = index * iconParticleCount / particleCount
        val cur = (index + 1) * iconParticleCount / particleCount
        if (cur == prev) null else {
            val i = (((seed + emitted) % iconCount + iconCount) % iconCount).toInt()
            emitted += 1
            i
        }
    }
}

private fun ceil1(v: Double) = kotlin.math.ceil(v).toInt()

private fun decorPlacement(variant: Int, index: Int, count: Int, seed: Long, width: Double, height: Double): DoubleArray {
    val hw = width / 2; val hh = height / 2
    val radius = min(width, height)
    fun jitter(salt: Int, range: Double) = (sonnetHash01(seed, index, salt) - 0.5) * range
    val baseRotation = sonnetHash01(seed, index, 11) * PI * 2
    return when (variant) {
        1 -> {
            val ring = index % 2
            val rr = radius * (0.36 + ring * 0.26)
            val a = index.toDouble() / count * PI * 4 + jitter(13, 0.35)
            doubleArrayOf(cos(a) * rr, sin(a) * rr * 0.86, a + PI / 2)
        }
        2 -> {
            val side = if (index % 2 == 0) -1 else 1
            val t = (index / 2 + 0.5) / max(1, count / 2)
            doubleArrayOf(-hw + width * (0.06 + 0.88 * t) + jitter(17, width * 0.03), side * hh * 0.78 + jitter(19, height * 0.05), if (side < 0) 0.0 else PI)
        }
        3 -> {
            val corner = index % 4
            val sx = if (corner % 2 == 0) -1 else 1
            val sy = if (corner < 2) -1 else 1
            doubleArrayOf(sx * hw * 0.68 + jitter(23, width * 0.12), sy * hh * 0.62 + jitter(29, height * 0.12), baseRotation)
        }
        4 -> {
            val col = index % 6
            val row = (index / 6) % 4
            doubleArrayOf(-hw * 0.8 + (col / 5.0) * hw * 1.6 + jitter(31, width * 0.06), -hh * 0.72 + (row / 3.0) * hh * 1.44 + jitter(37, height * 0.06), baseRotation)
        }
        5 -> {
            val side = if (index % 2 == 0) -1 else 1
            val t = (index / 2 + 0.5) / max(1, ceil1(count / 2.0))
            doubleArrayOf(side * hw * 0.74 + jitter(41, width * 0.04), -hh * 0.8 + t * hh * 1.6 + jitter(43, height * 0.05), if (side < 0) PI else 0.0)
        }
        else -> doubleArrayOf(-hw + width * sonnetHash01(seed, index, 47), -hh + height * sonnetHash01(seed, index, 53), baseRotation)
    }
}

// ───────────────────────────── 镜头 MG 整体 ─────────────────────────────

internal class SonnetMg(
    val geo: SonnetAnimGraphics?,
    val fixedParts: List<SonnetFixedPart>,
    val bg: SonnetAnimGraphics,
    val particles: List<SonnetParticle>,
    val icons: List<SonnetIcon>,
)

internal fun buildSonnetShotMg(kind: SonnetShotKind, width: Double, height: Double, seed: Long, primary: Int, secondary: Int, accent: Int, withIcons: Boolean): SonnetMg {
    val radius = min(width, height)
    val bg = SonnetAnimGraphics(seed * 3 + 1)
    val hw = width / 2; val hh = height / 2
    drawHud(bg, sonnetBackgroundMgVariant(seed), width, height, seed, primary, secondary)
    var geo: SonnetAnimGraphics? = null
    var fixed: List<SonnetFixedPart> = emptyList()
    if (kind == SonnetShotKind.TYPE_IMPACT || kind == SonnetShotKind.FRAGMENT_COLLAGE) {
        val g = SonnetAnimGraphics(seed * 5 + 2)
        val original = sonnetGeoVariant(seed)
        val variant = sonnetPortedGeoVariant(original)
        drawGeo(g, variant, seed, width, height, radius, primary, secondary)
        val keepsUpright = variant in listOf(6L, 8L, 9L, 14L, 15L, 16L, 17L, 20L, 22L, 23L) || variant >= 24
        if (!keepsUpright) g.rotation = ((seed * 13) % 360) * (PI / 180)
        else if (variant == 8L) g.rotation = geoSubVariant(seed, 4) * (PI / 2)
        geo = g
        fixed = buildFixedGeo(sonnetFixedGeoVariant(seed), radius, seed, primary, secondary)
    } else if (kind == SonnetShotKind.EDITORIAL_COLUMN) {
        for (i in 1..6) { val x = -hw + width * (i / 7.0); bg.moveTo(x, -hh).lineTo(x, hh).stroke(primary, 1.0, 0.15) }
        for (i in 1..4) { val y = -hh + height * (i / 5.0); bg.moveTo(-hw, y).lineTo(hw, y).stroke(primary, 1.0, 0.15) }
        bg.rect(-hw + width * 0.2, -hh + height * 0.2, width * 0.6, height * 0.6).stroke(primary, 4.0, 0.5)
    } else {
        for (i in 0 until 5) {
            val size = 10.0 + (seed % (i + 1)) * 5
            bg.rect(-hw + width * (0.2 + ((seed * 11 + i) % 60) / 100.0), -hh + height * (0.2 + ((seed * 17 + i) % 60) / 100.0), size, size).fill(primary, 0.4)
        }
    }
    // 漂浮小形
    val variant = sonnetBackgroundDecorVariant(seed)
    val palette = SHAPE_PALETTES[variant]
    val count = if (kind == SonnetShotKind.TYPE_IMPACT) 24 else 12
    val iconIdx = iconParticleIndices(if (withIcons) 1 else 0, count, seed)
    val particles = ArrayList<SonnetParticle>()
    val icons = ArrayList<SonnetIcon>()
    for (i in 0 until count) {
        val pSize = 4.0 + ((seed + i) % 12)
        val place = decorPlacement(variant, i, count, seed, width, height)
        if (iconIdx[i] != null) {
            val iconSeed = abs(seed + i * 17)
            val icon = SonnetIcon(pSize * 7, accent, 0.62 + (iconSeed % 4) * 0.08, (iconSeed % 31) * 0.2)
            icons += icon
            particles += SonnetParticle(place[0], place[1], place[2], null, icon)
        } else {
            val g = SonnetAnimGraphics(seed + i * 131L)
            val shape = palette[((seed + i) % palette.size).toInt()]
            decorShape(g, shape, pSize, if (i % 2 == 0) primary else secondary, 0.55 + sonnetHash01(seed, i, 59) * 0.3)
            particles += SonnetParticle(place[0], place[1], place[2], g, null)
        }
    }
    icons.forEachIndexed { index, icon ->
        icon.entryPhase = if (icons.size <= 1) 0.12 else 0.04 + (index.toDouble() / (icons.size - 1)) * 0.82
    }
    return SonnetMg(geo, fixed, bg, particles, icons)
}

/** MG 的描线进度（updateTime 里的 rawProgress）。 */
internal fun sonnetMgProgress(shot: SonnetShot, time: Double): Double {
    val start = shot.startTime
    val end = shot.endTime
    var finish = if (shot.cueTimes.isNotEmpty()) max(shot.cueTimes.last(), start + (end - start) * 0.95) else start + (end - start) * 0.95
    finish = min(end, finish)
    val drawDuration = max(1.0, finish - start)
    return clamp01((time - start) / drawDuration)
}

// ───────────────────────────── 词块底纹（sonnetTextFixedGeo.ts） ─────────────────────────────

internal class SonnetTextGeo(val g: SonnetAnimGraphics, val rotation: Double)

internal fun buildSonnetTextFixedGeo(seed: Int, isChorusEffect: Boolean, fontSize: Double, layoutWidth: Double, primary: Int, secondary: Int, accent: Int): SonnetTextGeo {
    val hollow = listOf("straight-frame", "rotated-frame", "orbit-crosshair", "split-arches")
    val solid = listOf("orb-hatch", "music-steps", "bent-lines")
    fun hollowVariant(divisor: Int, offset: Int): String {
        val index = Math.floorDiv(seed, divisor) + offset
        return hollow[((index % hollow.size) + hollow.size) % hollow.size]
    }
    val isHollow: Boolean
    val variant: String
    if (isChorusEffect) {
        val chorusSeed = ((seed % 10) + 10) % 10
        if (chorusSeed < 9) { isHollow = true; variant = hollowVariant(10, chorusSeed) }
        else { isHollow = false; variant = solid[(seed / 10) % solid.size] }
    } else {
        val legacy = ((seed % 4) + 4) % 4
        if (legacy == 1 || legacy == 2) { isHollow = true; variant = hollowVariant(4, legacy) }
        else { isHollow = false; variant = solid[(seed / 4) % solid.size] }
    }
    val g = SonnetAnimGraphics(seed.toLong() * 7 + 3)
    val color = if (seed % 2 == 0) primary else secondary
    val alpha = (if (isChorusEffect) 0.4 else 0.25) + (seed % 10) * 0.03
    val sm = if (isChorusEffect) 1.5 + (seed % 5) * 0.3 else 1.0
    val width = max(fontSize * 2.5 * sm, layoutWidth * 0.12 * sm)
    val height = max(fontSize * 1.8 * sm, layoutWidth * 0.08 * sm)
    var rotation = 0.0
    if (isHollow) {
        when (variant) {
            "orbit-crosshair" -> {
                val r = min(width, height) * 0.46
                g.circle(0.0, 0.0, r).stroke(color, 1.5, alpha)
                g.circle(-width * 0.17, 0.0, r * 0.72).stroke(secondary, 1.0, alpha * 0.72)
                g.circle(width * 0.17, 0.0, r * 0.72).stroke(secondary, 1.0, alpha * 0.72)
                g.moveTo(-width * 0.62, 0.0).lineTo(width * 0.62, 0.0).stroke(color, 1.0, alpha * 0.64)
                g.moveTo(0.0, -height * 0.62).lineTo(0.0, height * 0.62).stroke(color, 1.0, alpha * 0.64)
            }
            "split-arches" -> {
                val halfW = width * 0.46
                val ar = min(width * 0.34, height * 0.52)
                listOf(-1, 1).forEachIndexed { index, dir ->
                    val x = dir * halfW * 0.42
                    g.moveTo(x - ar * 0.72, height * 0.42).lineTo(x - ar * 0.72, 0.0).arc(x, 0.0, ar * 0.72, PI, 0.0).lineTo(x + ar * 0.72, height * 0.42)
                        .stroke(if (index == 0) color else secondary, 1.5, alpha)
                    g.moveTo(x - ar * 0.48, height * 0.42).lineTo(x - ar * 0.48, 0.0).arc(x, 0.0, ar * 0.48, PI, 0.0).lineTo(x + ar * 0.48, height * 0.42)
                        .stroke(if (index == 0) secondary else color, 1.0, alpha * 0.58)
                }
                g.moveTo(-halfW, height * 0.42).lineTo(halfW, height * 0.42).stroke(color, 2.0, alpha * 0.72)
            }
            else -> {
                val rot = variant == "rotated-frame"
                val fw = if (rot) width * 0.8 else width
                val fh = if (rot) height * 0.8 else height
                g.rect(-fw / 2, -fh / 2, fw, fh).stroke(color, max(1.5, fontSize * 0.02), alpha)
                if (isChorusEffect && seed % 2 == 0) g.rect(-fw * 0.6, -fh * 0.6, fw * 1.2, fh * 1.2).stroke(color, 1.0, alpha * 0.5)
                if (rot) rotation = PI / 4
            }
        }
    } else when (variant) {
        "music-steps" -> {
            val hs = doubleArrayOf(0.24, 0.35, 0.2, 0.82, 0.3, 0.1, 0.23, 0.16)
            val spacing = width / (hs.size + 1)
            hs.forEachIndexed { i, hr ->
                val x = -width / 2 + spacing * (i + 1)
                val base = height * (0.12 - i * 0.035)
                g.moveTo(x - spacing * 0.12, base - height * hr * 0.5).lineTo(x + spacing * 0.12, base + height * hr * 0.5)
                    .stroke(if (i % 2 == 0) accent else secondary, max(2.0, height * 0.025), alpha * 0.52)
            }
        }
        "bent-lines" -> {
            for (i in 0 until 5) {
                val x = -width * 0.34 + i * width * 0.17
                g.moveTo(x - width * 0.16, -height * (0.42 - i * 0.035)).lineTo(x, -height * (0.08 - i * 0.025)).lineTo(x - width * 0.015, height * (0.35 + i * 0.035))
                    .stroke(if (i % 2 == 0) accent else secondary, max(2.0, height * 0.022), alpha * 0.52)
            }
        }
        else -> {
            val r = width * 0.5
            g.circle(0.0, 0.0, r).fill(color, alpha * 0.15)
            val sp = max(4.0, width * 0.05)
            var o = -r
            while (o < r) {
                val lh = sqrt(max(0.0, r * r - o * o))
                g.moveTo(o + r * 0.4, -lh + r * 0.4)
                g.lineTo(o + r * 0.4, lh + r * 0.4)
                o += sp
            }
            g.stroke(color, 1.5, alpha * 0.6)
        }
    }
    return SonnetTextGeo(g, rotation)
}

// ───────────────────────────── 引导线（sonnetGuides.ts） ─────────────────────────────

internal class SonnetGuide(
    val x: Double,
    val y: Double,
    val startTime: Double,
    val endTime: Double,
    val maxAlpha: Double,
    private val hero: Boolean,
    private val color: Int,
    private val p: Array<DoubleArray>,
    private val shapes: List<DoubleArray>, // type, size, angle, speed, rotSpeed
    private val trails: List<DoubleArray>, // p0x,p0y,p1x,p1y,p2x,p2y,p3x,p3y,delay
    private val spline: DoubleArray?, // p0x,p0y,p1x,p1y,thickness,delay,duration
) {
    private val path = Path()

    private fun bez(a: DoubleArray, b: DoubleArray, c: DoubleArray, d: DoubleArray, t: Double): DoubleArray {
        val mt = 1 - t
        return doubleArrayOf(
            mt * mt * mt * a[0] + 3 * mt * mt * t * b[0] + 3 * mt * t * t * c[0] + t * t * t * d[0],
            mt * mt * mt * a[1] + 3 * mt * mt * t * b[1] + 3 * mt * t * t * c[1] + t * t * t * d[1],
        )
    }

    private fun seg(canvas: Canvas, paint: Paint, a: DoubleArray, b: DoubleArray, color: Int, width: Double, alpha: Double) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.color = sonnetArgb(color, alpha)
        canvas.drawLine(a[0].toFloat(), a[1].toFloat(), b[0].toFloat(), b[1].toFloat(), paint)
    }

    private fun dot(canvas: Canvas, paint: Paint, x: Double, y: Double, r: Double, color: Int, alpha: Double, stroke: Double = 0.0) {
        paint.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
        if (stroke > 0) paint.strokeWidth = stroke.toFloat()
        paint.color = sonnetArgb(color, alpha)
        canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), paint)
    }

    /** update(progress)；alpha = 容器透明度（maxAlpha × 外层）。 */
    fun draw(canvas: Canvas, progress: Double, alpha: Double, paint: Paint) {
        val strokeAlpha = if (hero) 0.82 else 0.55
        val drawProgress = min(1.0, max(0.0, progress / 0.35))
        val fadeOut = 1 - min(1.0, max(0.0, (progress - 0.4) / 0.3))
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        if (drawProgress > 0 && fadeOut > 0) {
            var prev = p[0]
            for (i in 1..20) {
                val t = i / 20.0 * drawProgress
                val q = bez(p[0], p[1], p[2], p[3], t)
                val intensity = (i / 20.0).pow(2)
                seg(canvas, paint, prev, q, color, (if (hero) 2.5 else 1.5) + intensity * (if (hero) 5.0 else 3.0), alpha * min(1.0, intensity * strokeAlpha * fadeOut * 1.6))
                prev = q
            }
            val head = bez(p[0], p[1], p[2], p[3], drawProgress)
            dot(canvas, paint, head[0], head[1], if (hero) 14.0 else 9.0, color, alpha * 0.5 * fadeOut)
            dot(canvas, paint, head[0], head[1], if (hero) 4.5 else 3.0, 0xffffff, alpha * fadeOut)
        }
        trails.forEach { tr ->
            val local = (progress - tr[8]) / 0.55
            if (local > 0 && local < 1.3 && fadeOut > 0) {
                val a = doubleArrayOf(tr[0], tr[1]); val b = doubleArrayOf(tr[2], tr[3]); val c = doubleArrayOf(tr[4], tr[5]); val d = doubleArrayOf(tr[6], tr[7])
                val headT = min(1.0, local)
                val tailT = max(0.0, local - 0.35)
                if (headT > tailT) {
                    var prev = bez(a, b, c, d, tailT)
                    for (i in 1..25) {
                        val st = tailT + (i / 25.0) * (headT - tailT)
                        val q = bez(a, b, c, d, st)
                        val intensity = (i / 25.0).pow(2)
                        seg(canvas, paint, prev, q, color, (if (hero) 2.0 else 1.0) + intensity * (if (hero) 5.0 else 2.5), alpha * intensity * strokeAlpha * fadeOut * 0.9)
                        prev = q
                    }
                }
                if (headT > 0 && headT < 1) {
                    val h = bez(a, b, c, d, headT)
                    dot(canvas, paint, h[0], h[1], if (hero) 7.0 else 4.0, color, alpha * 0.9 * fadeOut)
                    dot(canvas, paint, h[0], h[1], if (hero) 2.5 else 1.5, 0xffffff, alpha * fadeOut)
                    dot(canvas, paint, h[0], h[1], if (hero) 20.0 else 12.0, color, alpha * 0.4 * fadeOut, if (hero) 2.0 else 1.0)
                }
            }
        }
        if (spline != null) {
            val local = (progress - spline[5]) / spline[6]
            if (local > 0 && local < 1.3 && fadeOut > 0) {
                val headT = min(1.0, max(0.0, local * 1.5))
                val tailT = min(1.0, max(0.0, (local - 0.3) * 1.5))
                if (headT > tailT) {
                    val h = doubleArrayOf(spline[0] + (spline[2] - spline[0]) * headT, spline[1] + (spline[3] - spline[1]) * headT)
                    val t = doubleArrayOf(spline[0] + (spline[2] - spline[0]) * tailT, spline[1] + (spline[3] - spline[1]) * tailT)
                    seg(canvas, paint, t, h, color, spline[4], alpha * strokeAlpha * fadeOut * 0.7)
                    seg(canvas, paint, t, h, 0xffffff, spline[4] * 0.3, alpha * strokeAlpha * fadeOut * 0.9)
                }
            }
        }
        val burst = min(1.0, max(0.0, (progress - 0.3) / 0.7))
        val ease = 1 - (1 - burst).pow(3)
        shapes.forEach { s ->
            val sx = p[3][0] + cos(s[2]) * s[3] * ease
            val sy = p[3][1] + sin(s[2]) * s[3] * ease
            val sa = alpha * (1 - burst) * (if (hero) 1.0 else 0.8)
            if (sa <= 0.002) return@forEach
            canvas.save()
            canvas.translate(sx.toFloat(), sy.toFloat())
            canvas.rotate(Math.toDegrees(s[4] * burst).toFloat())
            val sc = (1 - burst * 0.4).toFloat()
            canvas.scale(sc, sc)
            val size = s[1]
            val shapeAlpha = if (hero) 0.8 else 0.6
            paint.color = sonnetArgb(color, shapeAlpha * sa)
            when (s[0].toInt()) {
                0 -> { paint.style = Paint.Style.FILL; canvas.drawCircle(0f, 0f, size.toFloat(), paint) }
                1 -> { paint.style = Paint.Style.FILL; canvas.drawRect(-size.toFloat(), -size.toFloat(), size.toFloat(), size.toFloat(), paint) }
                2 -> {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                    canvas.drawLine(-size.toFloat(), 0f, size.toFloat(), 0f, paint)
                    canvas.drawLine(0f, -size.toFloat(), 0f, size.toFloat(), paint)
                }
                else -> {
                    paint.style = Paint.Style.FILL
                    path.reset()
                    path.moveTo(0f, -size.toFloat()); path.lineTo(size.toFloat(), 0f); path.lineTo(0f, size.toFloat()); path.lineTo(-size.toFloat(), 0f); path.close()
                    canvas.drawPath(path, paint)
                }
            }
            canvas.restore()
        }
        canvas.restore()
    }
}

internal fun buildSonnetGuide(segment: SonnetSegment, placement: SonnetPlacement, fontSize: Double, textStartTime: Double, accent: Int, secondary: Int, seed: Long): SonnetGuide {
    val rnd = SonnetRandom(seed)
    val hero = placement.role.isEmphasis()
    val fallbackDir = if (placement.timingPhase < 0.5) -1 else 1
    val startX = if (placement.enterX != 0.0) placement.enterX else fallbackDir * fontSize * 1.8
    val startY = if (placement.enterY != 0.0) placement.enterY else -fontSize * 0.9
    val color = if (hero) accent else secondary
    val pts = arrayOf(doubleArrayOf(startX, startY), doubleArrayOf(startX * 0.6, startY * 0.4), doubleArrayOf(startX * 0.2, startY * 0.1), doubleArrayOf(0.0, 0.0))
    val shapes = ArrayList<DoubleArray>()
    repeat(if (hero) 6 else 3) {
        val type = floor(rnd.next() * 4)
        val size = (if (hero) 3.0 else 1.5) + rnd.next() * 3.5
        shapes += doubleArrayOf(type, size, rnd.next() * PI * 2, (15 + rnd.next() * 45) * (if (hero) 1.0 else 0.7), (rnd.next() - 0.5) * 8)
    }
    val trails = ArrayList<DoubleArray>()
    if (hero || rnd.next() > 0.4) {
        val d = if (rnd.next() > 0.5) 1 else -1
        val yo = (rnd.next() - 0.5) * fontSize * 0.8
        trails += doubleArrayOf(-d * fontSize * 2.5, yo + fontSize * 1.5, -d * fontSize * 0.8, yo - fontSize * 2.0, d * fontSize * 0.8, yo + fontSize * 2.0, d * fontSize * 2.5, yo - fontSize * 1.5, rnd.next() * 0.15)
    }
    if (hero || rnd.next() > 0.6) {
        val d = if (rnd.next() > 0.5) 1 else -1
        trails += doubleArrayOf(-fontSize * 2.0, -d * fontSize * 1.8, fontSize * 2.0, d * fontSize * 1.8, -fontSize * 2.0, d * fontSize * 1.8, fontSize * 2.0, -d * fontSize * 1.8, rnd.next() * 0.1)
    }
    var spline: DoubleArray? = null
    if (rnd.next() > 0.4) {
        val length = fontSize * (1.2 + rnd.next() * 1.5)
        val thickness = (if (hero) 6.0 else 3.0) + rnd.next() * 8
        val a = (rnd.next() - 0.5) * PI
        val rx = (rnd.next() - 0.5) * fontSize * 1.2
        val ry = (rnd.next() - 0.5) * fontSize * 1.2
        spline = doubleArrayOf(rx, ry, rx + cos(a) * length, ry + sin(a) * length, thickness, rnd.next() * 0.15, 0.25 + rnd.next() * 0.2)
    }
    val (s, e) = sonnetGuideCue(segment, textStartTime)
    return SonnetGuide(placement.x, placement.y, s, e, if (hero) 0.95 else 0.7, hero, color, pts, shapes, trails, spline)
}

// ───────────────────────────── 开口框（sonnetFrameDecor.ts） ─────────────────────────────

internal class SonnetFrameDecor(
    val x: Double, val y: Double, val rotation: Double,
    private val variant: Int, private val halfW: Double, private val halfH: Double, private val cornerGap: Double, private val pad: Double,
    private val color: Int, private val strokeWidth: Double, private val fontSize: Double,
    val startTime: Double, val endTime: Double,
) {
    private val path = Path()
    private val corners = arrayOf(doubleArrayOf(-halfW, -halfH, -1.0, -1.0), doubleArrayOf(halfW, -halfH, 1.0, -1.0), doubleArrayOf(halfW, halfH, 1.0, 1.0), doubleArrayOf(-halfW, halfH, -1.0, 1.0))

    private fun sideEnds(side: Int): DoubleArray {
        val from = corners[side]; val to = corners[(side + 1) % 4]
        val ix = if (to[0] == from[0]) 0.0 else cornerGap * (if (to[0] > from[0]) 1 else -1)
        val iy = if (to[1] == from[1]) 0.0 else cornerGap * (if (to[1] > from[1]) 1 else -1)
        return doubleArrayOf(from[0] + ix, from[1] + iy, to[0] - ix, to[1] - iy)
    }

    private fun op(eased: Double, corner: Int) = sonnetEaseElasticOut(clamp01((eased - 0.35 - corner * 0.14) / 0.3))

    private fun line(c: Canvas, p: Paint, pts: DoubleArray, width: Double, alpha: Double, col: Int = color) {
        p.style = Paint.Style.STROKE; p.strokeWidth = width.toFloat(); p.color = sonnetArgb(col, alpha)
        path.reset()
        path.moveTo(pts[0].toFloat(), pts[1].toFloat())
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i].toFloat(), pts[i + 1].toFloat()); i += 2 }
        c.drawPath(path, p)
    }

    private fun fillPoly(c: Canvas, p: Paint, pts: DoubleArray, alpha: Double) {
        p.style = Paint.Style.FILL; p.color = sonnetArgb(color, alpha)
        path.reset()
        path.moveTo(pts[0].toFloat(), pts[1].toFloat())
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i].toFloat(), pts[i + 1].toFloat()); i += 2 }
        path.close()
        c.drawPath(path, p)
    }

    private fun circle(c: Canvas, p: Paint, x: Double, y: Double, r: Double, col: Int, alpha: Double) {
        p.style = Paint.Style.FILL; p.color = sonnetArgb(col, alpha)
        c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    }

    fun draw(canvas: Canvas, progress: Double, alpha: Double, paint: Paint) {
        val eased = sonnetEaseExpoOut(clamp01(progress))
        if (eased <= 0 || alpha <= 0.002) return
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.rotate(Math.toDegrees(rotation).toFloat())
        val dashed = variant == 3
        for (side in 0 until 4) {
            val sp = clamp01((eased - side * 0.2) / 0.4)
            if (sp <= 0) continue
            val e = sideEnds(side)
            val tipX = e[0] + (e[2] - e[0]) * sp; val tipY = e[1] + (e[3] - e[1]) * sp
            if (!dashed) { line(canvas, paint, doubleArrayOf(e[0], e[1], tipX, tipY), strokeWidth, 0.85 * alpha); continue }
            val sl = hypot(e[2] - e[0], e[3] - e[1])
            if (sl < 1) continue
            val ux = (e[2] - e[0]) / sl; val uy = (e[3] - e[1]) / sl
            val dash = clampFd(fontSize * 0.14, 5.0, 8.0); val gap = dash * 0.7
            val traced = sl * sp
            var o = 0.0
            while (o + dash <= traced + 0.001) {
                line(canvas, paint, doubleArrayOf(e[0] + ux * o, e[1] + uy * o, e[0] + ux * (o + dash), e[1] + uy * (o + dash)), strokeWidth, 0.85 * alpha)
                o += dash + gap
            }
        }
        when (variant) {
            0 -> {
                val arm = clampFd(pad * 0.8, 5.0, 12.0)
                corners.forEachIndexed { i, c ->
                    val o = op(eased, i); if (o <= 0.02) return@forEachIndexed
                    val ix = c[0] + c[2] * 3; val iy = c[1] + c[3] * 3
                    line(canvas, paint, doubleArrayOf(ix + c[2] * arm * o, iy, ix, iy, ix, iy + c[3] * arm * o), strokeWidth, 0.9 * min(1.0, o) * alpha)
                }
            }
            1 -> {
                val r = clampFd(pad * 0.55, 4.0, 9.0)
                corners.forEachIndexed { i, c ->
                    val o = op(eased, i); if (o <= 0.02) return@forEachIndexed
                    val pr = r * 0.44 * o
                    circle(canvas, paint, c[0] - r, c[1], pr, color, 0.8 * alpha)
                    circle(canvas, paint, c[0] + r, c[1], pr, color, 0.8 * alpha)
                    circle(canvas, paint, c[0], c[1] - r, pr, color, 0.8 * alpha)
                    circle(canvas, paint, c[0], c[1] + r, pr, color, 0.8 * alpha)
                    circle(canvas, paint, c[0], c[1], r * 0.3 * o, 0xffffff, 0.5 * alpha)
                }
            }
            2 -> {
                val arm = clampFd(pad, 6.0, 14.0)
                corners.forEachIndexed { i, c ->
                    val o = op(eased, i); if (o <= 0.02) return@forEachIndexed
                    val ix = c[0] + c[2] * 2.5; val iy = c[1] + c[3] * 2.5
                    line(canvas, paint, doubleArrayOf(ix + c[2] * arm * o, iy, ix, iy, ix, iy + c[3] * arm * o), strokeWidth * 1.4, 0.9 * alpha)
                    line(canvas, paint, doubleArrayOf(ix + c[2] * (arm * o + 4), iy + c[3] * 4, ix + c[2] * 4, iy + c[3] * 4, ix + c[2] * 4, iy + c[3] * (arm * o + 4)), strokeWidth * 0.8, 0.5 * alpha)
                }
                val dm = clampFd(pad * 0.45, 3.5, 7.0)
                for (side in 0 until 4) {
                    val o = op(eased, side); if (o <= 0.02) continue
                    val e = sideEnds(side)
                    val mx = (e[0] + e[2]) / 2; val my = (e[1] + e[3]) / 2
                    val s = dm * o
                    fillPoly(canvas, paint, doubleArrayOf(mx, my - s, mx + s, my, mx, my + s, mx - s, my), 0.85 * alpha)
                }
            }
            else -> {
                val size = clampFd(pad * 0.6, 4.0, 9.0)
                corners.forEachIndexed { i, c ->
                    val o = op(eased, i); if (o <= 0.02) return@forEachIndexed
                    val dx = c[2] / sqrt(2.0); val dy = c[3] / sqrt(2.0)
                    val px = c[3] / sqrt(2.0); val py = -c[2] / sqrt(2.0)
                    val tx = c[0] + dx * (2 + size * o); val ty = c[1] + dy * (2 + size * o)
                    val bx = c[0] + dx * 2; val by = c[1] + dy * 2
                    val half = size * 0.7 * o
                    fillPoly(canvas, paint, doubleArrayOf(tx, ty, bx + px * half, by + py * half, bx - px * half, by - py * half), 0.85 * alpha)
                    circle(canvas, paint, c[0], c[1], strokeWidth, color, 0.9 * alpha)
                }
            }
        }
        canvas.restore()
    }
}

private fun clampFd(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

/** 原色 × 0.55（dimmedColor）。 */
private fun dimmed(rgb: Int, m: Double): Int {
    val r = Math.round(((rgb shr 16) and 255) * m).toInt()
    val g = Math.round(((rgb shr 8) and 255) * m).toInt()
    val b = Math.round((rgb and 255) * m).toInt()
    return (r shl 16) or (g shl 8) or b
}

internal fun buildSonnetFrameDecor(segment: SonnetSegment, placement: SonnetPlacement, fontSize: Double, primary: Int, shotStart: Double, shotEnd: Double, firstGlyphStart: Double): SonnetFrameDecor? {
    if (placement.role == SonnetRole.DECORATION) return null
    val variant = sonnetFrameDecorVariant(segment)
    if (variant < 0) return null
    val pad = clampFd(fontSize * 0.22, 8.0, 20.0)
    val quarter = Math.round(placement.rotation / (PI / 2))
    val snapped = quarter * (PI / 2)
    val odd = abs(placement.rotation - snapped) < 1e-6 && abs(quarter % 2) == 1L
    val w = if (odd) placement.measuredHeight else placement.measuredWidth
    val h = if (odd) placement.measuredWidth else placement.measuredHeight
    val halfW = w / 2 + pad
    val halfH = h / 2 + pad
    val gap = clampFd(min(halfW, halfH) * (if (variant == 1) 0.42 else 0.3), 6.0, 30.0)
    val grow = sonnetGlyphMotionDuration(shotStart, shotEnd) * 1.25
    return SonnetFrameDecor(placement.x, placement.y, placement.rotation, variant, halfW, halfH, gap, pad, dimmed(primary, 0.55), clampFd(fontSize * 0.03, 1.2, 2.2), fontSize, firstGlyphStart, firstGlyphStart + grow)
}

// ───────────────────────────── 画面固定标记（sonnetFrameOverlay.ts） ─────────────────────────────

internal fun drawSonnetFrameOverlay(canvas: Canvas, width: Double, height: Double, primary: Int, paint: Paint, starPaint: Paint) {
    val px = max(30.0, width * 0.05)
    val py = max(30.0, height * 0.05)
    fun fillRect(x: Double, y: Double, w: Double, h: Double, a: Double) {
        paint.style = Paint.Style.FILL; paint.color = sonnetArgb(primary, a)
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, a: Double) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = sonnetArgb(primary, a)
        canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)
    }
    fillRect(px, py, 30.0, 4.0, 0.8)
    line(px, py + 16, px, py + 120, 0.5)
    fillRect(width - px - 4, height - py - 16, 4.0, 16.0, 0.8)
    line(width - px - 160, height - py, width - px - 20, height - py, 0.5)
    line(width - px, height - py - 180, width - px, height - py - 30, 0.5)
    val cx = width - px; val cy = py + 20
    line(cx - 6, cy, cx + 6, cy, 0.8)
    line(cx, cy - 6, cx, cy + 6, 0.8)
    val path = Path()
    path.moveTo(px.toFloat(), (height - py - 4).toFloat()); path.lineTo((px + 4).toFloat(), (height - py).toFloat())
    path.lineTo(px.toFloat(), (height - py + 4).toFloat()); path.lineTo((px - 4).toFloat(), (height - py).toFloat()); path.close()
    paint.style = Paint.Style.FILL; paint.color = sonnetArgb(primary, 0.7)
    canvas.drawPath(path, paint)
    starPaint.textSize = 12f
    starPaint.color = sonnetArgb(primary, 0.6)
    starPaint.textAlign = Paint.Align.RIGHT
    val fm = starPaint.fontMetrics
    canvas.drawText("✦", (width - px - 10).toFloat(), (height - py).toFloat() - (fm.ascent + fm.descent) / 2f, starPaint)
}
