// 绘光线稿的绘制部分：lineart/lineArt.ts 的 createLineArt（按进度描线、节点闪点、受光变亮）。
// 纯逻辑（图元、各光位的线稿、节奏）在 LumiereLineart.kt（JVM 单测可测）；这里只有 android.graphics 的画法。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── 绘制（lineArt.ts 的 createLineArt.update + scene.ts 的节奏） ─────────────────────────────

/** 一个镜头的线稿层：每条线缓存一份 Path，描线进度变了才重建。坐标 = 高度单位 × height（dp）。 */
internal class LumiereLineArtLayer(val spec: LumiereLineArtSpec, private val height: Double) {
    private class Built(val spec: LumiereLinePath, val lengths: DoubleArray, val total: Double, val midpoint: LPt) {
        val path = Path()
        var drawn = -1.0
    }

    private val built: List<Built> = spec.paths.filter { it.points.size > 1 }.map { p ->
        val lengths = DoubleArray(p.points.size)
        for (i in 1 until p.points.size) lengths[i] = lengths[i - 1] + hypot(p.points[i].x - p.points[i - 1].x, p.points[i].y - p.points[i - 1].y)
        Built(p, lengths, lengths.last(), p.points[p.points.size / 2])
    }

    /** 画出一条线的前 amount 长度（考虑虚线）。 */
    private fun redraw(b: Built, amount: Double) {
        val g = b.path
        g.rewind()
        if (amount <= 0) return
        val points = b.spec.points
        val dash = b.spec.dash
        val limit = min(amount, b.total)
        val period = if (dash != null) dash[0] + dash[1] else 0.0
        val hgt = height
        fun atX(index: Int, distance: Double): Float {
            val segment = b.lengths[index] - b.lengths[index - 1]
            val f = if (segment > 0) (distance - b.lengths[index - 1]) / segment else 0.0
            return ((points[index - 1].x + (points[index].x - points[index - 1].x) * f) * hgt).toFloat()
        }
        fun atY(index: Int, distance: Double): Float {
            val segment = b.lengths[index] - b.lengths[index - 1]
            val f = if (segment > 0) (distance - b.lengths[index - 1]) / segment else 0.0
            return ((points[index - 1].y + (points[index].y - points[index - 1].y) * f) * hgt).toFloat()
        }
        g.moveTo((points[0].x * hgt).toFloat(), (points[0].y * hgt).toFloat())
        for (i in 1 until points.size) {
            val start = b.lengths[i - 1]
            val end = min(b.lengths[i], limit)
            if (start >= limit) break
            if (dash == null) {
                g.lineTo(atX(i, end), atY(i, end))
                continue
            }
            // 虚线：按周期的整数下标取出落在本段里的每一截实线。
            var k = floor(start / period).toLong()
            while (k * period < end) {
                val from = max(start, k * period)
                val to = min(end, k * period + dash[0])
                if (to > from) {
                    g.moveTo(atX(i, from), atY(i, from))
                    g.lineTo(atX(i, to), atY(i, to))
                }
                k += 1
            }
        }
    }

    /**
     * draw：整组描线进度（未夹到 0..1，同 Folia）；fade：整体亮度。color：光色 0xRRGGBB（Pixi tint）。
     * 两道描边：3.2 倍宽、0.12 透明的光晕 + 本体；整条线的 alpha = min(1, alpha·fade·(0.55 + 0.9·受光))。
     */
    fun draw(nc: android.graphics.Canvas, time: Double, draw: Double, fade: Double, beams: List<LumiereResolvedBeam>, color: Int, stroke: Paint, spritePaint: Paint, star: Bitmap) {
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val rgb = color and 0xffffff
        for (b in built) {
            val local = lumiereClamp01((draw - b.spec.delay) / max(b.spec.span, 1e-3))
            val eased = 1 - (1 - local).let { it * it * it }
            val amount = eased * b.total
            if (abs(amount - b.drawn) > 1e-5) {
                redraw(b, amount)
                b.drawn = amount
            }
            if (amount <= 0) continue
            val lit = lumiereCompressLight(lumiereLightAt(beams, b.midpoint.x, b.midpoint.y))
            val alpha = min(1.0, b.spec.alpha * fade * (0.55 + 0.9 * lit))
            if (alpha <= 0.002) continue
            val px = (b.spec.width * height).toFloat()
            stroke.strokeWidth = px * 3.2f
            stroke.color = ((alpha * 0.12 * 255).toInt().coerceIn(0, 255) shl 24) or rgb
            nc.drawPath(b.path, stroke)
            stroke.strokeWidth = px
            stroke.color = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or rgb
            nc.drawPath(b.path, stroke)
        }
        if (spec.nodes.isEmpty()) return
        spritePaint.colorFilter = PorterDuffColorFilter((0xff shl 24) or rgb, PorterDuff.Mode.MULTIPLY)
        val rect = RectF()
        for (node in spec.nodes) {
            val appear = lumiereClamp01((draw - node.delay) / 0.08)
            val lit = lumiereCompressLight(lumiereLightAt(beams, node.at.x, node.at.y))
            val twinkle = 0.55 + 0.45 * sin(time * 2.3 + node.twinklePhase)
            val alpha = appear * fade * (0.25 + 0.75 * lit) * twinkle
            if (alpha <= 0.004) continue
            val pop = 1 + (1 - appear) * 1.5
            val px = node.size * height * pop * (0.8 + 0.2 * twinkle)
            val cx = node.at.x * height; val cy = node.at.y * height
            rect.set((cx - px / 2).toFloat(), (cy - px / 2).toFloat(), (cx + px / 2).toFloat(), (cy + px / 2).toFloat())
            spritePaint.alpha = (min(1.0, alpha) * 255).toInt()
            nc.drawBitmap(star, null, rect, spritePaint)
        }
        spritePaint.colorFilter = null
    }
}

