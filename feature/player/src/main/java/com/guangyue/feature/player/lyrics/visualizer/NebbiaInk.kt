// 对应 Folia src/components/visualizer/nebbia/：
//  - nebbiaSkeleton.ts（没有笔顺数据时：主题字体画出 96 格的字 → Zhang-Suen 细化 → 追踪折线 → RDP 化简 → Chaikin 磨角，
//    笔顺按从上到下、从左到右估）。广乐没有 AnimCJK 笔顺数据（APL 授权、构建时下载），所有字都走骨架，与 Folia 读不到
//    nebbia-strokes.json 时一致。
//  - nebbiaGlyphs.ts（measureGlyph / warpGlyph 手写变形 / pointAt）。
//  - nebbiaScene.ts（decorate 水珠、naturalWriteTime、applyAppear 书写节奏、fogStartOf / 多留 / 让位 / 哈气、yieldFactor / yieldMask）。
//  - nebbiaInk.ts drawChar / drawDrip 的「每帧该画什么」部分（真正的光栅化在 NebbiaStage）。
//  - nebbiaBreath.ts buildBreath（白气的几团）。
// 坐标：Folia 用 CSS 像素，这里用设备像素，凡是 Folia 写死的像素常量都乘 [px]（每 CSS 像素多少设备像素）。
// 默认 tuning（types.ts DEFAULT_NEBBIA_TUNING）：appear [write, fade]、fade [fog]、hold 1.2、refog 4、strokeWidth 0.09、
// writeSpeed 1、jitter 0.6、warp 0.6、mist 0.75。
package com.guangyue.feature.player.lyrics.visualizer

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

/** Folia NEBBIA_FIXED + DEFAULT_NEBBIA_TUNING 里雾笺用到的值。 */
internal object NebbiaFixed {
    const val EDGE = 0.6
    const val BEADS = 0.35
    const val DRIP = 0.25
    const val END_BEAD = 1.0
    /** 收笔大水珠：字号小于 48 CSS 像素的不留。 */
    const val END_BEAD_MIN = 48.0
    const val BEAD_HOLD = 4.0
    const val BREATH_SPREAD = 1.2
    const val BREATH_HOLD = 1.0
    const val BREATH_FADE = 8.0
    const val BREATH_THICK = 0.7
    const val FIELD_DROPS = 0.5
    const val MIST = 0.75
    const val HOLD = 1.2
    const val REFOG = 4.0
    const val STROKE_WIDTH = 0.09
    /**
     * 手机上的笔宽（第 5 轮，可读性优先，偏离 Folia）：Folia 的笔宽 0.09 em、下限 8 CSS 像素是按桌面 1600 CSS 宽定的；
     * 手机横屏只有 800×360 CSS，字只有 30 来 CSS 像素，8 像素下限占到 0.25 em，笔画糊成墨团（Folia 自己在 800 宽下也一样糊）。
     * 这里基准收到 0.055 em，下限 2.2 CSS 像素（沙盒里用 Noto Sans SC 按三层遮罩 + 阈值模拟过，64 设备像素的字可读）。
     */
    const val PHONE_STROKE_WIDTH = 0.055
    const val PHONE_STROKE_MIN = 2.2
    /**
     * 手机上擦痕边水珠的出现概率（Folia BEADS 0.35）：字小、笔画多时水珠挤成一团盖住字（竖屏「雾」「里」），
     * 收到 0.25，再按笔画数像笔宽那样收（最少一半）。
     */
    const val PHONE_BEADS = 0.25
    /** 字号小于这个（CSS 像素）时擦痕边水珠按比例缩小（最小一半），免得水珠把小字的笔画盖住。 */
    const val BEAD_FULL_SIZE = 64.0
    const val WRITE_SPEED = 1.0
    const val JITTER = 0.6
    const val WARP = 0.6
    const val SPOT = 0.5
    const val SPOT_X = 0.8
    /** 擦痕遮罩相对 CSS 像素的分辨率。 */
    const val MASK_SCALE = 0.5
    /** 屏上还空着时一句最多多留几秒、最多留到后面第几句开始。 */
    const val LINGER_MAX = 16.0
    const val LINGER_LINES = 3
    const val YIELD_LEAD = 0.6
    const val YIELD_READ = 0.3
    /** 哈气盖掉的旧字在遮罩上留的印子。 */
    const val BREATH_GHOST = 0.08
}

internal fun nClamp(v: Double, lo: Double = 0.0, hi: Double = 1.0): Double = if (v < lo) lo else if (v > hi) hi else v
internal fun nSmooth(t: Double): Double = t * t * (3 - 2 * t)

// ---------------- nebbiaSkeleton.ts ----------------
internal object NebbiaSkeleton {
    const val SK = 96
    private val N = arrayOf(
        intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1),
        intArrayOf(1, 1), intArrayOf(-1, 1), intArrayOf(-1, -1), intArrayOf(1, -1),
    )

    /** Zhang-Suen 细化：两遍交替剥掉边界像素，直到只剩一像素宽的骨架。img 是 SK×SK 的 0/1。只扫有字的包围盒，省时。 */
    fun thin(img: ByteArray) {
        var x0 = SK; var x1 = -1; var y0 = SK; var y1 = -1
        for (y in 0 until SK) for (x in 0 until SK) if (img[y * SK + x].toInt() != 0) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return
        fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= SK || y >= SK) 0 else img[y * SK + x].toInt()
        val del = IntArray(SK * SK)
        var changed = true
        while (changed) {
            changed = false
            for (pass in 0..1) {
                var nd = 0
                for (y in y0..y1) for (x in x0..x1) {
                    if (img[y * SK + x].toInt() == 0) continue
                    val n0 = at(x, y - 1); val n1 = at(x + 1, y - 1); val n2 = at(x + 1, y); val n3 = at(x + 1, y + 1)
                    val n4 = at(x, y + 1); val n5 = at(x - 1, y + 1); val n6 = at(x - 1, y); val n7 = at(x - 1, y - 1)
                    val b = n0 + n1 + n2 + n3 + n4 + n5 + n6 + n7
                    if (b < 2 || b > 6) continue
                    var a = 0
                    if (n0 == 0 && n1 != 0) a++; if (n1 == 0 && n2 != 0) a++; if (n2 == 0 && n3 != 0) a++; if (n3 == 0 && n4 != 0) a++
                    if (n4 == 0 && n5 != 0) a++; if (n5 == 0 && n6 != 0) a++; if (n6 == 0 && n7 != 0) a++; if (n7 == 0 && n0 != 0) a++
                    if (a != 1) continue
                    val keep = if (pass == 0) (n0 * n2 * n4 != 0 || n2 * n4 * n6 != 0) else (n0 * n2 * n6 != 0 || n0 * n4 * n6 != 0)
                    if (keep) continue
                    del[nd++] = y * SK + x
                }
                for (i in 0 until nd) img[del[i]] = 0
                if (nd > 0) changed = true
            }
        }
    }

    /** 骨架 → 折线：先从端点出发走，再收拾剩下的环；每步优先沿原方向。 */
    fun trace(img: ByteArray): List<List<IntArray>> {
        fun on(x: Int, y: Int) = x >= 0 && y >= 0 && x < SK && y < SK && img[y * SK + x].toInt() == 1
        fun deg(x: Int, y: Int) = N.count { on(x + it[0], y + it[1]) }
        val seen = BooleanArray(SK * SK)
        val polys = ArrayList<List<IntArray>>()
        fun isolated(p: List<IntArray>) = p.all { q ->
            N.all { d -> !on(q[0] + d[0], q[1] + d[1]) || p.any { r -> r[0] == q[0] + d[0] && r[1] == q[1] + d[1] } }
        }
        fun walk(sx: Int, sy: Int) {
            var x = sx
            var y = sy
            val p = arrayListOf(intArrayOf(x, y))
            seen[y * SK + x] = true
            var hasDir = false
            var dx0 = 0
            var dy0 = 0
            while (true) {
                var bx = -1
                var bY = -1
                var bdx = 0
                var bdy = 0
                var bestScore = -9.0
                for (d in N) {
                    val nx = x + d[0]
                    val ny = y + d[1]
                    if (!on(nx, ny) || seen[ny * SK + nx]) continue
                    val score = if (hasDir) (d[0] * dx0 + d[1] * dy0) / hypot(d[0].toDouble(), d[1].toDouble()) else 0.0
                    if (score > bestScore) { bestScore = score; bx = nx; bY = ny; bdx = d[0]; bdy = d[1] }
                }
                if (bx < 0) break
                x = bx; y = bY; dx0 = bdx; dy0 = bdy; hasDir = true
                seen[y * SK + x] = true
                p += intArrayOf(x, y)
            }
            if (p.size >= 4) polys += p
            // 短的多是交叉处的毛刺，扔掉；不连着别的笔画的是一个点，留下
            else if (isolated(p)) polys += if (p.size > 1) p else listOf(p[0], intArrayOf(p[0][0] + 1, p[0][1] + 1))
        }
        for (y in 0 until SK) for (x in 0 until SK) if (img[y * SK + x].toInt() == 1 && deg(x, y) == 1 && !seen[y * SK + x]) walk(x, y)
        for (y in 0 until SK) for (x in 0 until SK) if (img[y * SK + x].toInt() == 1 && !seen[y * SK + x]) walk(x, y)
        return polys
    }

    /** Ramer-Douglas-Peucker 化简。 */
    fun rdp(pts: List<DoubleArray>, eps: Double): List<DoubleArray> {
        if (pts.size < 3) return pts
        val a = pts[0]
        val b = pts[pts.size - 1]
        val l = hypot(b[0] - a[0], b[1] - a[1]).let { if (it == 0.0) 1.0 else it }
        var idx = 0
        var dmax = 0.0
        for (i in 1 until pts.size - 1) {
            val d = abs((b[0] - a[0]) * (a[1] - pts[i][1]) - (a[0] - pts[i][0]) * (b[1] - a[1])) / l
            if (d > dmax) { dmax = d; idx = i }
        }
        return if (dmax > eps) rdp(pts.subList(0, idx + 1), eps).dropLast(1) + rdp(pts.subList(idx, pts.size), eps) else listOf(a, b)
    }

    /** Chaikin 磨角。 */
    fun chaikin(input: List<DoubleArray>, iterations: Int): List<DoubleArray> {
        var pts = input
        var k = 0
        while (k < iterations && pts.size > 2) {
            val o = ArrayList<DoubleArray>()
            o += pts[0]
            for (i in 0 until pts.size - 1) {
                val p = pts[i]
                val q = pts[i + 1]
                o += doubleArrayOf(p[0] * 0.75 + q[0] * 0.25, p[1] * 0.75 + q[1] * 0.25)
                o += doubleArrayOf(p[0] * 0.25 + q[0] * 0.75, p[1] * 0.25 + q[1] * 0.75)
            }
            o += pts[pts.size - 1]
            pts = o
            k += 1
        }
        return pts
    }

    /** 一个字的骨架笔画（em 坐标 0..1，y 向下）；img 是按 Folia 画法（字号 0.78·SK、基线 0.8·SK、居中）二值化后的字。 */
    fun polys(img: ByteArray): List<List<DoubleArray>>? {
        thin(img)
        val polys = trace(img).map { p ->
            chaikin(rdp(p.map { doubleArrayOf(it[0].toDouble(), it[1].toDouble()) }, 1.2), 2)
                .map { doubleArrayOf(it[0] / SK, it[1] / SK) }.toMutableList()
        }
        for (p in polys) {
            val s = p[0]
            val e = p[p.size - 1]
            if (s[1] + s[0] * 0.6 > e[1] + e[0] * 0.6) p.reverse()
        }
        val sorted = polys.sortedBy { p -> 0.6 * p.minOf { it[1] } + p.minOf { it[0] } }
        return sorted.ifEmpty { null }
    }
}

// ---------------- nebbiaGlyphs.ts ----------------
internal class NebbiaStroke(val xs: DoubleArray, val ys: DoubleArray, val cum: DoubleArray) {
    val len: Double get() = cum[cum.size - 1]
}

internal class NebbiaGlyphShape(val strokes: List<NebbiaStroke>, val total: Double)

internal object NebbiaGlyphs {
    /** 量出每一笔的累计弧长；太短的笔画丢掉。 */
    fun measure(polys: List<List<DoubleArray>>): NebbiaGlyphShape? {
        var total = 0.0
        val strokes = polys.filter { it.size > 1 }.map { pts ->
            val cum = DoubleArray(pts.size)
            for (i in 1 until pts.size) cum[i] = cum[i - 1] + hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
            NebbiaStroke(DoubleArray(pts.size) { pts[it][0] }, DoubleArray(pts.size) { pts[it][1] }, cum)
        }.filter { it.len > 0.005 }
        strokes.forEach { total += it.len }
        return if (strokes.isEmpty()) null else NebbiaGlyphShape(strokes, total)
    }

    /** 手写变形：整字斜切胖瘦带弯；每笔偏移、转动、长短不一，沿笔画手抖。 */
    fun warp(g: NebbiaGlyphShape, rnd: () -> Double, j: Double): NebbiaGlyphShape {
        if (j <= 0) return g
        fun r() = rnd() * 2 - 1
        val shear = r() * 0.16 * j
        val sx = 1 + r() * 0.1 * j
        val sy = 1 + r() * 0.1 * j
        val bx = r() * 0.035 * j
        val bY = r() * 0.035 * j
        val px = rnd() * 6.28
        val py = rnd() * 6.28
        fun whole(x: Double, y: Double): DoubleArray {
            var u = (x - 0.5) * sx
            var v = (y - 0.5) * sy
            u += v * shear
            u += bx * sin(v * PI * 1.6 + px)
            v += bY * sin(u * PI * 1.6 + py)
            return doubleArrayOf(u + 0.5, v + 0.5)
        }
        val sj = nClamp(8.0 / g.strokes.size, 0.35, 1.0)
        val polys = g.strokes.map { st ->
            val n = st.xs.size
            val cx = st.xs.average()
            val cy = st.ys.average()
            val rot = r() * 0.09 * j * sj
            val len = 1 + r() * 0.1 * j * sj
            val ox = r() * 0.022 * j * sj
            val oy = r() * 0.022 * j * sj
            val cr = cos(rot)
            val sr = sin(rot)
            val x0 = st.xs[0]
            val y0 = st.ys[0]
            val tremor = 0.006 * j
            val tf = 18 + rnd() * 14
            val tp = rnd() * 6.28
            (0 until n).map { i ->
                var u = x0 + (st.xs[i] - x0) * len
                var v = y0 + (st.ys[i] - y0) * len
                val du = u - cx
                val dv = v - cy
                u = cx + du * cr - dv * sr + ox
                v = cy + du * sr + dv * cr + oy
                val a = max(0, i - 1)
                val b = min(n - 1, i + 1)
                val tx = st.xs[b] - st.xs[a]
                val ty = st.ys[b] - st.ys[a]
                val tl = hypot(tx, ty).let { if (it == 0.0) 1.0 else it }
                val k = sin(st.cum[i] * tf + tp) * tremor
                whole(u - ty / tl * k, v + tx / tl * k)
            }
        }
        return measure(polys) ?: g
    }

    /** 折线上弧长 s 处的点和法线：[x, y, nx, ny]。 */
    fun pointAt(st: NebbiaStroke, s: Double): DoubleArray {
        var i = 1
        while (i < st.xs.size - 1 && st.cum[i] < s) i += 1
        val l = max(1e-6, st.cum[i] - st.cum[i - 1])
        val k = nClamp((s - st.cum[i - 1]) / l)
        val dx = (st.xs[i] - st.xs[i - 1]) / l
        val dy = (st.ys[i] - st.ys[i - 1]) / l
        return doubleArrayOf(st.xs[i - 1] + (st.xs[i] - st.xs[i - 1]) * k, st.ys[i - 1] + (st.ys[i] - st.ys[i - 1]) * k, -dy, dx)
    }
}

// ---------------- nebbiaScene.ts ----------------
internal class NebbiaBead(val s: Double, val x: Double, val y: Double, val nx: Double, val ny: Double, val r: Double, val j: Double)
internal class NebbiaDrip(val delay: Double, val len: Double, val dur: Double, val r: Double, val seed: Double)

internal class NebbiaChar(val ch: String, val t: Double, val d: Double, val x: Double, val y: Double, val s: Double, val rot: Double) {
    var glyph: NebbiaGlyphShape? = null
    var beads: List<List<NebbiaBead>> = emptyList()
    var drips: List<NebbiaDrip?> = emptyList()
    var ends: List<DoubleArray> = emptyList()
    /** 慢慢显现用几秒；0 = 手指书写。 */
    var appearDur = 0.0
    /** 手指书写的开始时刻和用时。 */
    var wt = t
    var wd = 0.0
}

internal class NebbiaBreathShape(val lobes: List<DoubleArray>, val cx: Double, val cy: Double)

internal class NebbiaLine(val chars: List<NebbiaChar>, val t: Double, val end: Double, val next: Double) {
    var fogAt = Double.POSITIVE_INFINITY
    var yieldAt = Double.POSITIVE_INFINITY
    var breathAt = Double.POSITIVE_INFINITY
    var breathUntil = Double.POSITIVE_INFINITY
    var breathDur = NebbiaFixed.BREATH_SPREAD
    var breath: NebbiaBreathShape? = null
}

internal class NebbiaRect(val x0: Double, val x1: Double, val y0: Double, val y1: Double, val s: Double)

internal object NebbiaSceneBuilder {
    private const val INF = Double.POSITIVE_INFINITY

    fun rng(seed: Int): () -> Double { val r = NebbiaComposer.Rng(seed); return { r.next() } }

    /** 写完后开始起雾的时刻：下一句开始（最多唱完 6 秒）后再保持 hold 秒。 */
    fun fogStartOf(end: Double, next: Double, hold: Double = NebbiaFixed.HOLD) = min(next, end + 6) + hold

    /** 自然书写一个字要几秒。 */
    fun naturalWriteTime(g: NebbiaGlyphShape, speed: Double = NebbiaFixed.WRITE_SPEED) = (g.total * 0.28 + (g.strokes.size - 1) * 0.06) / speed

    /** 一个字挑水珠：每一笔沿边缘零星凝出小水珠，少数笔画底部会淌下一颗。 */
    fun decorate(c: NebbiaChar, base: NebbiaGlyphShape, rnd: () -> Double, warp: Double, px: Double) {
        val g = NebbiaGlyphs.warp(base, rng((rnd() * 1e9).toLong().toInt()), warp)
        c.glyph = g
        val beadK = nClamp(c.s / (NebbiaFixed.BEAD_FULL_SIZE * px), 0.5, 1.0)
        val beadP = NebbiaFixed.PHONE_BEADS * nClamp(sqrt(9.0 / g.strokes.size), 0.5, 1.0)
        c.beads = g.strokes.map { st ->
            val out = ArrayList<NebbiaBead>()
            val step = 7 * px / c.s
            var s = step * rnd()
            while (s < st.len) {
                if (rnd() <= beadP) {
                    val p = NebbiaGlyphs.pointAt(st, s)
                    val side = if (rnd() < 0.5) -1.0 else 1.0
                    out += NebbiaBead(s, p[0], p[1], p[2] * side, p[3] * side, (1.6 + rnd().pow(2.2) * 3.6) * px * beadK, rnd())
                }
                s += step * (0.5 + rnd())
            }
            out
        }
        c.drips = g.strokes.map {
            if (rnd() < NebbiaFixed.DRIP * 0.22) NebbiaDrip(0.3 + rnd() * 1.6, (30 + rnd() * 130) * px, 2 + rnd() * 3.5, (3.2 + rnd() * 2.6) * px, rnd() * 100) else null
        }
        c.ends = g.strokes.map { doubleArrayOf(rnd(), rnd()) }
    }

    private class Plan(val starts: DoubleArray, val end: Double)

    private fun scheduleWrite(cs: List<NebbiaChar>, nat: DoubleArray, k: Double): Plan {
        var end = Double.NEGATIVE_INFINITY
        val starts = DoubleArray(cs.size) { i -> val t0 = max(cs[i].t, end); end = t0 + nat[i] * k; t0 }
        return Plan(starts, end)
    }

    /** 书写节奏（applyAppear，appear = [write, fade]）：一直写到下一句开唱或唱完（取晚），压到 0.3 倍都写不完的改慢慢显现。 */
    fun applyAppear(line: NebbiaLine) {
        val cs = line.chars.filter { it.glyph != null }
        val nat = DoubleArray(cs.size) { naturalWriteTime(cs[it].glyph!!) }
        val until = max(line.next, line.end)
        var k = 1.0
        if (scheduleWrite(cs, nat, 1.0).end > until) {
            var lo = 0.3
            var hi = 1.0
            repeat(14) { val m = (lo + hi) / 2; if (scheduleWrite(cs, nat, m).end > until) { hi = m } else { lo = m } }
            k = lo
        }
        val plan = scheduleWrite(cs, nat, k)
        val fade = cs.isNotEmpty() && plan.end > until + 1e-3
        cs.forEachIndexed { i, c -> c.wt = plan.starts[i]; c.wd = nat[i] * k }
        for (c in line.chars) c.appearDur = if (fade) nClamp(c.d * 3, 0.35, 1.2) else 0.0
    }

    fun lineRect(line: NebbiaLine): NebbiaRect {
        var x0 = INF; var x1 = -INF; var y0 = INF; var y1 = -INF; var s = 0.0
        for (c in line.chars) if (c.glyph != null) {
            x0 = min(x0, c.x - c.s / 2); x1 = max(x1, c.x + c.s / 2)
            y0 = min(y0, c.y - c.s / 2); y1 = max(y1, c.y + c.s / 2); s = max(s, c.s)
        }
        return NebbiaRect(x0, x1, y0, y1, s)
    }

    /** 这句最后一个字写完（慢慢显现的显出一半）的时刻。 */
    fun writtenAt(line: NebbiaLine): Double {
        var done = line.t
        // 偏离 Folia：慢慢显现的字（appearDur > 0）在 c.t 起就显出来，按「显出一半」算；Folia 这里先看 wd，而 applyAppear 给慢慢显现的字
        // 也填了压缩后的书写时长，长句会被算成十几秒后才写完 → 不让位，被后一句压在底下（竖屏取证里两句叠在一起）
        for (c in line.chars) if (c.glyph != null) {
            done = max(done, if (c.appearDur > 0) c.t + min(c.appearDur, 0.5) else if (c.wd > 0) c.wt + c.wd else c.t)
        }
        return done
    }

    fun yieldMoment(nextT: Double, line: NebbiaLine) = max(nextT, writtenAt(line) + NebbiaFixed.YIELD_READ + NebbiaFixed.YIELD_LEAD)

    /**
     * 排一首歌（buildNebbiaScene，fade = fog）：排版、字形、水珠、书写节奏，以及每句的起雾 / 多留 / 让位 / 哈气时刻。
     * [glyphOf] 给出字的骨架（画不出来的 null）；[px] 每 CSS 像素多少设备像素。
     */
    fun build(
        src: List<NebbiaComposer.Src>,
        w: Double, h: Double, fontScale: Double, minPx: Double, px: Double,
        glyphOf: (String) -> NebbiaGlyphShape?,
    ): List<NebbiaLine> {
        val refog = NebbiaFixed.REFOG
        val hold = NebbiaFixed.HOLD
        val next = DoubleArray(src.size) { src.getOrNull(it + 1)?.t ?: INF }
        fun fogAt(i: Int) = fogStartOf(src[i].end, next[i], hold)
        fun lingerAt(i: Int) = max(fogAt(i), min(fogAt(i) + NebbiaFixed.LINGER_MAX, (src.getOrNull(i + NebbiaFixed.LINGER_LINES)?.t ?: INF) + hold))
        val pos = NebbiaComposer.layoutSong(
            src, w, h, fontScale, minPx, NebbiaFixed.JITTER,
            visibleUntil = { fogAt(it) + refog }, fogAt = { fogAt(it) }, lingerUntil = { lingerAt(it) + refog },
        )
        val laid = src.mapIndexed { li, line ->
            NebbiaLine(
                line.chars.mapIndexed { i, c ->
                    val p = pos[li][i]
                    val wide = NebbiaComposer.advanceOf(c.ch) >= 1.0
                    val out = NebbiaChar(c.ch, c.t, c.d, p.x, p.y, p.s * (if (wide) 1.0 else 1.15), p.rot)
                    val base = if (c.ch.isBlank()) null else glyphOf(c.ch)
                    if (base != null) decorate(out, base, rng(li * 131 + i * 17 + 5), NebbiaFixed.WARP, px)
                    out
                },
                line.t, line.end, next[li],
            ).also { it.fogAt = fogAt(li) }
        }
        laid.forEach { applyAppear(it) }
        val rects = laid.map { lineRect(it) }
        laid.forEachIndexed { i, line ->
            val gone = lingerAt(i) + refog
            var byIdx = -1
            var j = i + 1
            while (j < laid.size && laid[j].t < gone) {
                val a = rects[i]
                val b = rects[j]
                if (min(a.x1, b.x1) > max(a.x0, b.x0) && min(a.y1, b.y1) > max(a.y0, b.y0)) {
                    line.yieldAt = yieldMoment(laid[j].t, line); byIdx = j; break
                }
                j += 1
            }
            line.fogAt = max(fogAt(i), min(lingerAt(i), line.yieldAt - refog))
            if (byIdx >= 0 && line.yieldAt < line.fogAt + refog) {
                val from = max(line.yieldAt - NebbiaFixed.BREATH_SPREAD - 0.3, writtenAt(line) + NebbiaFixed.YIELD_READ)
                line.breathAt = from
                line.breathDur = max(0.3, line.yieldAt - 0.3 - from)
                line.breathUntil = laid[byIdx].end + NebbiaFixed.BREATH_HOLD
                line.breath = buildBreath(line, i)
            }
        }
        return laid
    }

    /** 让位的句子在后面那句开写前 0.6 秒起用 1 秒快速起雾；哈气盖掉的在白气鼓开的这一下里没了。 */
    fun yieldFactor(line: NebbiaLine, t: Double): Double = if (line.breathAt < INF) {
        1 - nSmooth(nClamp(((t - line.breathAt) / line.breathDur - 0.15) / 0.7))
    } else {
        1 - nSmooth(nClamp((t - line.yieldAt + NebbiaFixed.YIELD_LEAD) / 1.0))
    }

    fun yieldMask(line: NebbiaLine, t: Double): Double {
        val yf = yieldFactor(line, t)
        return if (line.breathAt < INF) max(yf, NebbiaFixed.BREATH_GHOST) else yf
    }

    /** 起雾的句子这一帧的擦痕清晰度 v 和擦痕边水珠的 vb（nebbiaMask.ts drawLine，fog 分支）。 */
    fun lineValues(line: NebbiaLine, t: Double): DoubleArray {
        val v = (1 - nSmooth(nClamp((t - line.fogAt) / NebbiaFixed.REFOG))) * yieldMask(line, t)
        val vb = (1 - nSmooth(nClamp((t - line.fogAt - NebbiaFixed.BEAD_HOLD) / (NebbiaFixed.REFOG * 3)))) * yieldFactor(line, t)
        return doubleArrayOf(v, vb)
    }

    /** 一句的白气：贴着整句外框的椭圆，再在四周叠几团大圆（每团 [x, y, rx, ry]）。 */
    fun buildBreath(line: NebbiaLine, idx: Int): NebbiaBreathShape? {
        val rnd = rng(idx * 6151 + 29)
        val r = lineRect(line)
        if (!r.x0.isFinite()) return null
        val cx = (r.x0 + r.x1) / 2
        val cy = (r.y0 + r.y1) / 2
        val rx = (r.x1 - r.x0) / 2 + r.s * 0.3
        val ry = (r.y1 - r.y0) / 2 + r.s * 0.3
        val lobes = arrayListOf(doubleArrayOf(cx, cy, rx * 0.95, ry * 0.95))
        val n = 5 + floor(rnd() * 2).toInt()
        val r0 = min(rx, ry)
        for (k in 0 until n) {
            val a = (k + rnd() * 0.6) / n * PI * 2
            val rr = r0 * (0.45 + rnd() * 0.25)
            lobes += doubleArrayOf(cx + cos(a) * rx * 0.6, cy + sin(a) * ry * 0.6, rr, rr)
        }
        return NebbiaBreathShape(lobes, cx, cy)
    }

    /**
     * 这一帧这句的白气：null = 不画；否则 [形状 0..1, 浓度 0..1, 糊开 0..1, 鼓开进度 u]（nebbiaBreath.ts drawLine）。
     */
    fun breathPuff(line: NebbiaLine, t: Double): DoubleArray? {
        if (line.breathAt == INF || line.breath == null || t < line.breathAt) return null
        val s = 1 - nSmooth(nClamp((t - max(line.breathUntil, line.breathAt + line.breathDur)) / NebbiaFixed.BREATH_FADE))
        if (s <= 0.002) return null
        val u = nClamp((t - line.breathAt) / line.breathDur)
        val g0 = nSmooth(nClamp(u * 1.4))
        val b0 = nSmooth(nClamp(u * 2.5)) * (1 - nSmooth(nClamp((u - 0.8) / 0.2)))
        return doubleArrayOf(s, g0, b0, u)
    }

    // ---------- nebbiaSpot.ts：正在写的字 ----------
    private fun startOf(c: NebbiaChar) = if (c.appearDur > 0) c.t else c.wt
    private fun doneOf(c: NebbiaChar) = startOf(c) + if (c.appearDur > 0) c.appearDur else c.wd
    private fun lineDone(line: NebbiaLine): Double {
        var e = line.end
        for (c in line.chars) if (c.glyph != null) e = max(e, doneOf(c))
        return e
    }

    /** 聚光灯目标 [x, y, r]（正在写的字滑向下一个字），没有在写的字时 null。 */
    fun spotTarget(laid: List<NebbiaLine>, t: Double): DoubleArray? {
        for (li in laid.indices.reversed()) {
            val line = laid[li]
            if (t < line.t - 0.05 || t > lineDone(line) + 0.8) continue
            var k = -1
            line.chars.forEachIndexed { i, c -> if (c.glyph != null && startOf(c) <= t && (k < 0 || startOf(c) >= startOf(line.chars[k]))) k = i }
            if (k < 0) continue
            val c = line.chars[k]
            var nx: NebbiaChar? = null
            var i = k + 1
            while (i < line.chars.size && nx == null) { if (line.chars[i].glyph != null) nx = line.chars[i]; i += 1 }
            if (nx == null) return doubleArrayOf(c.x, c.y, c.s)
            val u = nSmooth(nClamp((t - startOf(c)) / max(0.05, startOf(nx) - startOf(c))))
            return doubleArrayOf(c.x + (nx.x - c.x) * u, c.y + (nx.y - c.y) * u, c.s + (nx.s - c.s) * u)
        }
        return null
    }
}

/** 一个字的笔宽（em）：笔画多的收细；见 [NebbiaFixed.PHONE_STROKE_WIDTH]。 */
internal fun strokeWidthOf(c: NebbiaChar, g: NebbiaGlyphShape, px: Double): Double {
    val thin = nClamp(sqrt(9.0 / g.strokes.size), 0.55, 1.0)
    return max(NebbiaFixed.PHONE_STROKE_WIDTH * thin, NebbiaFixed.PHONE_STROKE_MIN * px * thin / c.s)
}

/**
 * 每帧收集擦痕（nebbiaInk.ts drawChar / drawDrip）：线段写进 [seg]（x0, y0, x1, y1, 宽, 亮度；设备像素），
 * 水珠写进 [beads]（x, y, r, 透明度, sx, sy）。笔画是一笔一段折线，[pathStart] 记每段折线在 seg 里开始的下标。
 */
internal class NebbiaInkFrame {
    /** 折线：每条 [宽, 亮度, x0, y0, x1, y1, ...]。 */
    val polylines = ArrayList<DoubleArray>()
    val beads = ArrayList<DoubleArray>()

    fun clear() { polylines.clear(); beads.clear() }

    private fun toScreen(c: NebbiaChar, u: Double, v: Double): DoubleArray {
        val cs = cos(c.rot)
        val sn = sin(c.rot)
        val lx = (u - 0.5) * c.s
        val ly = (v - 0.5) * c.s
        return doubleArrayOf(c.x + cs * lx - sn * ly, c.y + sn * lx + cs * ly)
    }

    /** 某一笔在弧长 [0, l] 上已经写出来的部分，转成屏幕坐标折线。 */
    private fun partial(c: NebbiaChar, st: NebbiaStroke, l: Double, width: Double, v: Double) {
        val out = ArrayList<Double>()
        out += width; out += v
        val p0 = toScreen(c, st.xs[0], st.ys[0]); out += p0[0]; out += p0[1]
        for (i in 1 until st.xs.size) {
            if (st.cum[i] >= l) {
                val k = (l - st.cum[i - 1]) / max(1e-6, st.cum[i] - st.cum[i - 1])
                val p = toScreen(c, st.xs[i - 1] + (st.xs[i] - st.xs[i - 1]) * k, st.ys[i - 1] + (st.ys[i] - st.ys[i - 1]) * k)
                out += p[0]; out += p[1]
                break
            }
            val p = toScreen(c, st.xs[i], st.ys[i]); out += p[0]; out += p[1]
        }
        if (out.size >= 6) polylines += out.toDoubleArray()
    }

    private fun put(x: Double, y: Double, r: Double, a: Double, sx: Double = 1.0, sy: Double = 1.0) {
        beads += doubleArrayOf(x, y, r, a, sx, sy)
    }

    /** 水珠流下来的水痕：宽度接近水珠本身，边缘起伏，越往上越细。 */
    private fun trail(x: Double, y0: Double, y1: Double, width: Double, v: Double, seed: Double, px: Double, wob: (Double) -> Double) {
        if (y1 - y0 < 2 * px) return
        var y = y0
        while (y < y1) {
            val ye = min(y1, y + 5 * px)
            val along = (y - y0) / max(1.0, y1 - y0)
            val w = width * (0.72 + 0.28 * along) * (0.85 + 0.3 * (0.5 + 0.5 * sin(y / px * 0.19 + seed * 3)))
            polylines += doubleArrayOf(w, v, x + wob(y), y, x + wob(ye), ye)
            y += 4 * px
        }
    }

    private fun drip(c: NebbiaChar, st: NebbiaStroke, d: NebbiaDrip, age: Double, width: Double, clarity: Double, beadV: Double, px: Double) {
        var bx = 0.0
        var bY = -1e9
        for (i in st.xs.indices) { val p = toScreen(c, st.xs[i], st.ys[i]); if (p[1] > bY) { bx = p[0]; bY = p[1] } }
        bY += width * c.s * 0.35
        val u = nClamp(age / d.dur)
        val f = u - sin(u * 6 * PI) / (6 * PI)
        val dist = d.len * f
        val r = d.r * (1 - 0.35 * u)
        val wob = { y: Double -> (sin(y / px * 0.07 + d.seed) * 1.6 + sin(y / px * 0.023 + d.seed * 2) * 2.5) * px }
        if (dist > px && clarity > 0.002) trail(bx, bY, bY + dist, r * 1.5, clarity * 0.95, d.seed, px) { y -> wob(y - bY) }
        if (beadV <= 0.002) return
        val speed = 1 - cos(u * 6 * PI)
        var y = (14 + (d.seed % 7) * 2) * px
        while (y < dist - r * 2) {
            put(bx + wob(y), bY + y, r * (0.3 + 0.2 * (((y / px + d.seed) * 0.37) % 1.0)), beadV)
            y += (18 + ((y / px * 7 + d.seed * 13) % 11)) * px
        }
        put(bx + wob(dist), bY + dist + r * 0.3, r, beadV, 1.0, 1 + 0.35 * speed * (if (u < 1) 1 else 0))
    }

    /** 画一个字到时刻 t（drawChar）：clarity 管擦痕，beadIn 管擦痕边的水珠。 */
    fun drawChar(c: NebbiaChar, t: Double, clarityIn: Double, beadIn: Double, px: Double) {
        val g = c.glyph ?: return
        val fadeIn = c.appearDur > 0
        if (t < (if (fadeIn) c.t else c.wt)) return
        var clarity = clarityIn
        var beadV = beadIn
        if (fadeIn) { val a = nSmooth(nClamp((t - c.t) / c.appearDur)); clarity *= a; beadV *= a }
        val wd = c.wd
        val gap = min(0.06 / NebbiaFixed.WRITE_SPEED, wd * 0.08)
        val inkTime = wd - gap * (g.strokes.size - 1)
        var local = t - (if (fadeIn) c.t else c.wt)
        // 笔画多的字笔画收细一点；下限也跟着收（Folia 0.09 em / 8 CSS 像素，手机上收细，见 PHONE_STROKE_WIDTH）
        val width = strokeWidthOf(c, g, px)
        val drips = ArrayList<Triple<NebbiaStroke, NebbiaDrip, Double>>()
        g.strokes.forEachIndexed { si, st ->
            val dur = if (fadeIn) 0.0 else inkTime * st.len / g.total
            if (local <= 0) return@forEachIndexed
            val p = if (dur > 0) nClamp(local / dur) else 1.0
            val drawn = (p * 0.4 + nSmooth(p) * 0.6) * st.len
            if (clarity > 0.002) partial(c, st, drawn, width * c.s, clarity)
            if (!fadeIn) {
                for (b in c.beads.getOrElse(si) { emptyList() }) {
                    if (b.s > drawn) break
                    val off = width / 2 + (b.r * 0.7 + b.j * 1.5 * px) / c.s
                    val q = toScreen(c, b.x + b.nx * off, b.y + b.ny * off)
                    if (beadV <= 0.002) continue
                    put(q[0], q[1], b.r * (0.55 + 0.45 * beadV), beadV, 1 + b.j * 0.25, 1 + (1 - b.j) * 0.3)
                }
                // 收笔水珠：笔尖推着走的水越堆越多；字太小就不留
                if (drawn > 0 && c.s >= NebbiaFixed.END_BEAD_MIN * px) {
                    val e = c.ends.getOrElse(si) { doubleArrayOf(0.5, 0.5) }
                    val a = NebbiaGlyphs.pointAt(st, drawn)
                    val b = NebbiaGlyphs.pointAt(st, max(0.0, drawn - 2 * px / c.s))
                    var dx = a[0] - b[0]
                    var dy = a[1] - b[1]
                    val dl = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }
                    dx /= dl; dy /= dl
                    val r = c.s * (0.031 + 0.018 * e[0]) * NebbiaFixed.END_BEAD * (0.5 + 0.5 * nSmooth(p))
                    val q = toScreen(c, a[0] + (dx * r * 0.35) / c.s, a[1] + (dy * r * 0.35 + r * 0.15) / c.s)
                    if (beadV > 0.002) put(q[0], q[1], r * (0.6 + 0.4 * beadV), beadV, 1.0, 1.12 + e[1] * 0.12)
                }
                val d = c.drips.getOrNull(si)
                if (d != null && p >= 1 && local - dur > d.delay) drips += Triple(st, d, local - dur - d.delay)
            }
            local -= dur + (if (fadeIn) 0.0 else gap)
        }
        for ((st, d, age) in drips) drip(c, st, d, age, width, clarity, beadV, px)
    }

    /** 整帧：每句按起雾 / 让位算清晰度，开唱前 0.5 秒起画。 */
    fun draw(laid: List<NebbiaLine>, t: Double, px: Double) {
        clear()
        for (line in laid) {
            if (t < line.t - 0.5) continue
            val (v, vb) = NebbiaSceneBuilder.lineValues(line, t).let { it[0] to it[1] }
            if (vb > 0.002 || v > 0.002) for (c in line.chars) drawChar(c, t, v, vb, px)
        }
    }
}
