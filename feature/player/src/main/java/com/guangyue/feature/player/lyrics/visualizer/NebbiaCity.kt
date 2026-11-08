// 对应 Folia src/components/visualizer/nebbia/：
//  - nebbiaPalette.ts（主题 → 夜景配色：天色两档、三个灯光色，强调色打头）。
//  - nebbiaSceneCommonGlsl.ts（hash / h1 / pick / glowPt / reach / HZ 0.6 / ROAD 0.7 / SODIUM）。
//  - nebbiaSceneGlsl.ts（公交车侧窗外的雨夜街景：远近两层楼群（收分顶层、天线、立面下亮上暗、左侧边光、窗格亮灯）、
//    主题色霓虹散景 + 大霓虹光晕、路灯招牌店面、湿路面与倒影、车灯）。
// GLSL 逐函数对译成 Kotlin，在后台线程按 Folia 的分辨率档算成三张图（主画面 + 雾的预渲染两层，同 nebbiaFogGl.ts）：
// 清晰的窗外 b = 0.0012（擦开处，1/2 分辨率）、雾里的两层 b = 0.03（1/4）与 0.09（1/8）。
// 手机化：窗外定格在 t = 0（不随车行平移，省掉每帧整屏场景计算）；雨丝与聚光灯光柱不画（雨丝只在光柱里才看得见），
// 聚光灯照到雾上的那一圈在主画面合成里照画。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/** 0..1 的 RGB。 */
internal class NebbiaPalette(val sky0: DoubleArray, val sky1: DoubleArray, val la: DoubleArray, val lb: DoubleArray, val lc: DoubleArray)

internal object NebbiaCity {
    const val HZ = 0.6
    const val ROAD = 0.7
    private val SODIUM = doubleArrayOf(1.0, 0.68, 0.32)

    // ---------- nebbiaPalette.ts ----------
    private fun toHsl(c: DoubleArray): DoubleArray {
        val r = c[0]; val g = c[1]; val b = c[2]
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val l = (mx + mn) / 2; val d = mx - mn
        if (d == 0.0) return doubleArrayOf(0.0, 0.0, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            r -> ((g - b) / d + 6) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        return doubleArrayOf(h / 6, s, l)
    }

    private fun fromHsl(h: Double, s: Double, l: Double): DoubleArray {
        val a = s * min(l, 1 - l)
        fun f(n: Double): Double { val k = (n + h * 12) % 12; return l - a * max(-1.0, min(k - 3, min(9 - k, 1.0))) }
        return doubleArrayOf(f(0.0), f(8.0), f(4.0))
    }

    private fun asLight(c: DoubleArray): DoubleArray { val h = toHsl(c); return fromHsl(h[0], max(0.55, h[1]), min(0.78, max(0.6, h[2]))) }
    private fun asSky(c: DoubleArray, l: Double): DoubleArray { val h = toHsl(c); return fromHsl(h[0], min(0.55, h[1]), l) }

    /** resolveNebbiaPalette：主题背景缺省 #0a0f17 深夜色（Folia 的回落值），强调色取歌词强调色。 */
    fun palette(accent: DoubleArray, bg: DoubleArray = doubleArrayOf(0.04, 0.06, 0.09)): NebbiaPalette {
        val ah = toHsl(accent)
        val lb = fromHsl((ah[0] + 0.55) % 1.0, ah[1], ah[2])
        val lc = fromHsl((ah[0] + 0.9) % 1.0, ah[1], ah[2])
        return NebbiaPalette(
            sky0 = asSky(bg, 0.05),
            sky1 = asSky(DoubleArray(3) { bg[it] * 0.7 + accent[it] * 0.3 }, 0.14),
            la = asLight(accent), lb = asLight(lb), lc = asLight(lc),
        )
    }

    // ---------- nebbiaSceneCommonGlsl.ts ----------
    private fun fract(x: Double) = x - floor(x)
    fun hash(x: Double, y: Double): Double {
        var px = fract(x * 123.34); var py = fract(y * 456.21)
        val d = px * (px + 45.32) + py * (py + 45.32)
        px += d; py += d
        return fract(px * py)
    }
    fun h1(n0: Double): Double { var n = fract(n0 * 0.1031); n *= n + 33.33; n *= n + n; return fract(n) }
    private fun sst(e0: Double, e1: Double, x: Double): Double { val t = nClamp((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t) }
    private fun glowPt(dx0: Double, dy: Double, r: Double, b: Double, streak: Double): Double {
        val dx = sign(dx0) * max(abs(dx0) - streak, 0.0)
        val s2 = r * r + b * b
        return (r * r / s2) * exp(-(dx * dx + dy * dy) / s2 * 1.6)
    }
    private fun reach(r: Double, b: Double) = 3.0 * kotlin.math.sqrt(r * r + b * b)
    private fun pick(h: Double, p: NebbiaPalette) = if (h < 0.34) p.la else if (h < 0.67) p.lb else p.lc

    // ---------- nebbiaSceneGlsl.ts ----------
    /** 路灯和招牌（倒影也用它），加到 out。 */
    private fun lampsSigns(qx: Double, qy: Double, b: Double, t: Double, p: NebbiaPalette, out: DoubleArray, k0: Double) {
        val xs = qx + t * 0.42; val cw = 0.34; val streak = 0.012
        val cs = floor(xs / cw)
        val sg = abs(qy - 0.51) < 0.05 + reach(0.016, b + 0.002)
        val sh = abs(qy - 0.645) < reach(0.026, b + 0.003)
        if (sg || sh) for (k in -1..1) {
            val n = cs + k
            if (sg) {
                val cx = (n + 0.2 + 0.6 * h1(n)) * cw; val cy = 0.46 + 0.1 * h1(n + 17.0)
                if (h1(n + 33.0) < 0.75) {
                    val g = glowPt(xs - cx, qy - cy, 0.006 + 0.01 * h1(n + 5.0), b + 0.002, streak) * 1.6 * k0
                    val c = pick(h1(n + 2.0), p); for (i in 0..2) out[i] += c[i] * g
                }
            }
            if (sh && h1(n + 41.0) < 0.6) {
                val c = pick(h1(n + 8.0), p)
                val g = glowPt(xs - (n + 0.5) * cw, qy - 0.645, 0.016 + 0.01 * h1(n + 3.0), b + 0.003, streak) * 0.9 * k0
                for (i in 0..2) out[i] += (SODIUM[i] * 0.7 + c[i] * 0.3) * g
            }
        }
        val xl = qx + t * 0.22; val lw = 0.55; val cl = floor(xl / lw)
        if (abs(qy - 0.36) < reach(0.07, b)) for (k in -1..1) {
            val dx = xl - (cl + k + 0.5) * lw; val dy = qy - 0.36
            val g = (glowPt(dx, dy, 0.007, b, 0.004) * 2.6 + glowPt(dx, dy, 0.07, b, 0.0) * 0.3) * k0
            for (i in 0..2) out[i] += SODIUM[i] * g
        }
    }

    private val FAC_DARK = doubleArrayOf(0.015, 0.018, 0.025)
    private val WIN_WARM = doubleArrayOf(1.0, 0.78, 0.5)
    private val WIN_COOL = doubleArrayOf(0.78, 0.88, 1.0)
    private val WIN_AVG = doubleArrayOf(0.95 * 0.85, 0.82 * 0.85, 0.66 * 0.85)
    private val TAIL = doubleArrayOf(1.0, 0.18, 0.12)
    private val HEAD = doubleArrayOf(1.0, 0.95, 0.85)

    /** 一层楼群（layer 0 远、1 近）叠到 col 上（col = mix(col, rgb, a)）。 */
    private fun building(qx: Double, qy: Double, b: Double, t: Double, layer: Double, p: NebbiaPalette, col: DoubleArray) {
        val near = layer
        val x = qx + t * (0.02 + (0.035 - 0.02) * near) + layer * 7.7
        val cw = 0.065 + (0.12 - 0.065) * near
        val aa = max(b, 0.0012)
        val c0 = floor(x / cw)
        val fx = x - c0 * cw; val nbReach = aa + 0.0013
        var r0 = 0.0; var r1 = 0.0; var r2 = 0.0; var ra = 0.0
        val sky0 = p.sky0; val sky1 = p.sky1
        // 楼底在 HZ + 0.015 以上：再往下（路面）整层没有覆盖，跳过
        if (qy > HZ + 0.015 + aa * 3) return
        for (k in -1..1) {
            if ((k < 0 && fx > nbReach) || (k > 0 && cw - fx > nbReach)) continue
            val n = c0 + k + layer * 100.0
            val w = cw * (0.72 + 0.26 * h1(n))
            val left = (c0 + k) * cw + (cw - w) * h1(n + 1.0)
            val lx = x - left
            val hh = h1(n + 2.0)
            val hgt = (0.07 + 0.04 * near) + (0.16 + 0.11 * near) * hh * kotlin.math.sqrt(hh)
            val base = HZ + near * 0.015
            val top = base - hgt
            val tier = h1(n + 3.0) < 0.5
            val tw = w * (0.5 + 0.25 * h1(n + 4.0)); val tl = (w - tw) * 0.5; val th = hgt * (0.12 + 0.15 * h1(n + 5.0))
            val body = sst(-aa, aa, lx) * sst(-aa, aa, w - lx) * sst(-aa, aa, qy - top)
            val up = if (tier) sst(-aa, aa, lx - tl) * sst(-aa, aa, tl + tw - lx) * sst(-aa, aa, qy - (top - th)) else 0.0
            val roof = if (tier) top - th else top
            val ant = if (h1(n + 6.0) < 0.3) sst(0.0012 + aa, 0.0, abs(lx - w * 0.5)) * sst(-aa, aa, qy - (roof - 0.04 - 0.03 * h1(n + 7.0))) else 0.0
            val cov = max(max(body, up), ant)
            if (cov <= 0.0) continue
            val fk = 0.7 + 0.6 * h1(n + 8.0)
            val ll = nClamp((qy - roof) / (base - roof))
            val lowLit = ll * ll * kotlin.math.sqrt(ll)
            val edge = sst(0.003 + aa, 0.0, lx) * body
            val fh = 0.0075 + 0.0045 * near; val wc = 0.0055 + 0.003 * near
            val gx = (lx - wc * 0.6) / wc; val gy = (qy - roof - fh * 0.8) / fh
            val idx = floor(gx); val idy = floor(gy); val ffx = gx - idx; val ffy = gy - idy
            val dens = 0.15 + 0.5 * h1(n + 9.0)
            val inside = sst(-aa, aa, lx - wc * 0.6) * sst(-aa, aa, w - wc * 0.6 - lx) * sst(-aa, aa, qy - roof - fh * 0.8) * sst(-aa, aa, base - fh - qy)
            val blurMix = nClamp(b / (wc * 0.8))
            var win = 0.0
            var wcol = WIN_AVG
            var wk = 1.0
            if (inside > 0.0) {
                val floorOn = if (hash(idy, n) >= 0.18) 1.0 else 0.0
                val on = (if (hash(idx + n * 3.7, idy + n * 3.7) <= dens) 1.0 else 0.0) * floorOn
                val pane = sst(0.32, 0.22, abs(ffx - 0.5)) * sst(0.3, 0.2, abs(ffy - 0.5))
                win = (pane * on * (1 - blurMix) + 0.3 * dens * 0.82 * blurMix) * inside * max(body, up)
                val hc = hash(idx + n * 1.3, idy + n * 1.3)
                wcol = if (hc < 0.5) WIN_WARM else if (hc < 0.8) WIN_COOL else pick(hash(idx + 5.0, idy + 5.0), p)
                wk = 0.55 + 0.6 * hash(idx + 2.9, idy + 2.9)
            }
            val f = 0.45 + (0.12 - 0.45) * near
            for (i in 0..2) {
                var fac = (sky0[i] * 0.45 + FAC_DARK[i] * 0.55) * fk
                fac += (SODIUM[i] + p.la[i]) * 0.5 * 0.1 * lowLit
                fac += (sky1[i] * 0.7 + p.lb[i] * 0.3) * 0.12 * edge
                val wci = wcol[i] * wk * (1 - blurMix) + WIN_AVG[i] * blurMix
                var bc = fac + wci * win * (0.7 + 0.3 * near)
                bc = bc * (1 - f) + (sky0[i] * 0.3 + sky1[i] * 0.7) * f
                when (i) {
                    0 -> r0 = r0 * (1 - cov) + bc * cov
                    1 -> r1 = r1 * (1 - cov) + bc * cov
                    else -> r2 = r2 * (1 - cov) + bc * cov
                }
            }
            ra = max(ra, cov)
        }
        if (ra > 0) {
            col[0] = col[0] * (1 - ra) + r0 * ra
            col[1] = col[1] * (1 - ra) + r1 * ra
            col[2] = col[2] * (1 - ra) + r2 * ra
        }
    }

    /** 主题色霓虹：楼群之间密密的彩色小散景，偶尔一大团霓虹带一圈很大的光晕。加到 out。 */
    private fun neonLights(qx: Double, qy: Double, b: Double, t: Double, p: NebbiaPalette, out: DoubleArray, k0: Double) {
        val bb = b + 0.003
        if (abs(qy - (HZ - 0.12)) < 0.1 + reach(0.012, bb)) for (row in 0..1) {
            val x = qx + t * (0.06 + row * 0.03) + row * 7.3; val cw = 0.045 - row * 0.012
            val c0 = floor(x / cw)
            for (k in -1..1) {
                val n = c0 + k + row * 300.0
                if (h1(n + 11.0) > 0.85) continue
                val cx = (c0 + k + 0.15 + 0.7 * h1(n)) * cw; val cy = HZ - 0.02 - 0.2 * h1(n + 3.0).pow(1.6)
                val r = 0.004 + 0.008 * h1(n + 6.0).pow(3.0)
                val g = glowPt(x - cx, qy - cy, r, bb, 0.0) * (1.0 + 1.8 * h1(n + 13.0)) * k0
                val c = pick(h1(n + 9.0), p); for (i in 0..2) out[i] += c[i] * g
            }
        }
        val xb = qx + t * 0.1; val bw = 0.75; val cb = floor(xb / bw)
        if (abs(qy - (HZ - 0.14)) < 0.06 + reach(0.13, b)) for (k in -1..1) {
            val n = cb + k + 500.0
            val dx = xb - (cb + k + 0.2 + 0.6 * h1(n)) * bw; val dy = qy - (HZ - 0.08 - 0.12 * h1(n + 2.0))
            val c = pick(h1(n + 4.0), p)
            val g = (glowPt(dx, dy, 0.014, bb, 0.0) * 2.0 + glowPt(dx, dy, 0.13, b, 0.0) * 0.4) * k0
            for (i in 0..2) out[i] += c[i] * g
        }
    }

    /** 公交车侧窗外的雨夜街景 scene(q, b)：q 以屏高为单位（x 0..W/H，y 0..1 向下），b 是模糊半径（屏高的比例）。 */
    fun scene(qx: Double, qy: Double, b: Double, p: NebbiaPalette, col: DoubleArray, t: Double = 0.0) {
        val s = sst(0.0, HZ, qy)
        val hg = 0.14 * exp(-abs(qy - HZ + 0.12) * 6.0)
        for (i in 0..2) col[i] = p.sky0[i] * (1 - s) + p.sky1[i] * s + (p.la[i] * 0.6 + SODIUM[i] * 0.4) * hg
        val aa = max(b, 0.0015)
        building(qx, qy, b, t, 0.0, p, col)
        building(qx, qy, b, t, 1.0, p, col)
        neonLights(qx, qy, b, t, p, col, 1.0)
        val wide = 0.12 * exp(-abs(qy - HZ + 0.1) * 5.0)
        for (i in 0..2) col[i] += p.la[i] * wide
        lampsSigns(qx, qy, b, t, p, col, 1.0)
        val road = sst(ROAD - aa, ROAD + aa, qy)
        for (i in 0..2) col[i] = col[i] * (1 - road * 0.85) + p.sky0[i] * 0.5 * road * 0.85
        if (road > 0.0) {
            lampsSigns(qx, ROAD - (qy - ROAD) * 0.55, b + 0.012, t, p, col, road * 0.35)
            neonLights(qx, HZ - (qy - ROAD) * 0.22, b + 0.008, t, p, col, road * 0.45)
        }
        for (lane in 0..1) {
            val sp = if (lane == 0) 0.55 else -1.1; val ly = if (lane == 0) 0.76 else 0.83; val ww = 0.9
            val xc = qx + t * sp; val cc = floor(xc / ww)
            val lc = if (lane == 0) TAIL else HEAD
            val hd = abs(qy - ly) < reach(0.004, b)
            val rf = road > 0.0 && abs((qy - ly) * 3.0 - 0.03) < reach(0.02, b * 2.0)
            if (!hd && !rf) continue
            for (k in -1..1) {
                val n = cc + k + lane * 50.0
                if (h1(n) > 0.6) continue
                val cx = (cc + k + 0.2 + 0.6 * h1(n + 4.0)) * ww
                val st = abs(sp) * 0.03
                var g = 0.0
                if (hd) g += (glowPt(xc - cx + 0.035, qy - ly, 0.004, b, st) + glowPt(xc - cx - 0.035, qy - ly, 0.004, b, st)) * 2.2
                if (rf) g += glowPt(xc - cx, (qy - ly) * 3.0 - 0.03, 0.02, b * 2.0, st) * road * 0.3
                for (i in 0..2) col[i] += lc[i] * g
            }
        }
    }

    /** 把 scene 画成 [pw]×[ph] 像素（ARGB_8888 的 int），整屏宽高比 [aspect] = W/H。行区间 [y0, y1)。 */
    fun render(pw: Int, ph: Int, aspect: Double, b: Double, p: NebbiaPalette, out: IntArray, y0: Int = 0, y1: Int = ph) {
        val col = DoubleArray(3)
        for (y in y0 until y1) {
            val qy = (y + 0.5) / ph
            for (x in 0 until pw) {
                scene((x + 0.5) / pw * aspect, qy, b, p, col)
                out[y * pw + x] = argb(col[0], col[1], col[2])
            }
        }
    }

    fun argb(r: Double, g: Double, b: Double): Int {
        val ri = (nClamp(r) * 255 + 0.5).toInt(); val gi = (nClamp(g) * 255 + 0.5).toInt(); val bi = (nClamp(b) * 255 + 0.5).toInt()
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
