// 凝彩圆滑族用的曲线多边形：逐函数对译 Folia temperaCurves.ts。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private class Pts { val a = ArrayList<Double>(); fun add(x: Double, y: Double) { a += x; a += y }; fun arr() = a.toDoubleArray() }

internal fun ellipsePolygon(cx: Double, cy: Double, rx: Double, ry: Double, segments: Double = 32.0): DoubleArray {
    val count = max(8, jsRound(segments))
    val p = Pts()
    for (i in 0 until count) { val a = i.toDouble() / count * TAU; p.add(cx + cos(a) * rx, cy + sin(a) * ry) }
    return p.arr()
}

internal fun roundedRectPolygon(x: Double, y: Double, width: Double, height: Double, radius: Double, cornerSegments: Double = 6.0): DoubleArray {
    val limit = max(0.0, min(radius, min(width, height) / 2))
    val steps = max(1, jsRound(cornerSegments))
    val corners = listOf(
        Triple(x + width - limit, y + limit, -PI / 2), Triple(x + width - limit, y + height - limit, 0.0),
        Triple(x + limit, y + height - limit, PI / 2), Triple(x + limit, y + limit, PI),
    )
    val p = Pts()
    corners.forEach { (ccx, ccy, start) ->
        for (s in 0..steps) { val a = start + s.toDouble() / steps * (PI / 2); p.add(ccx + cos(a) * limit, ccy + sin(a) * limit) }
    }
    return p.arr()
}

internal fun rotatePolygon(polygon: DoubleArray, cx: Double, cy: Double, angle: Double): DoubleArray {
    val c = cos(angle); val s = sin(angle)
    val out = DoubleArray(polygon.size)
    for (i in polygon.indices step 2) {
        val dx = polygon[i] - cx; val dy = polygon[i + 1] - cy
        out[i] = cx + dx * c - dy * s; out[i + 1] = cy + dx * s + dy * c
    }
    return out
}

internal fun lobedPolygon(cx: Double, cy: Double, rx: Double, ry: Double, lobes: Double, amplitude: Double, segments: Double = 96.0): DoubleArray {
    val count = max(24, jsRound(segments))
    val petals = max(1, jsRound(lobes))
    val swell = max(0.0, amplitude)
    val p = Pts()
    for (i in 0 until count) {
        val a = i.toDouble() / count * TAU
        val scale = (1 + cos(a * petals) * swell) / (1 + swell)
        p.add(cx + cos(a) * rx * scale, cy + sin(a) * ry * scale)
    }
    return p.arr()
}

internal fun scallopBandPolygon(x0: Double, x1: Double, flatY: Double, baseY: Double, bumps: Double, segmentsPerBump: Double = 10.0): DoubleArray {
    val count = max(1, jsRound(bumps))
    val steps = max(3, jsRound(segmentsPerBump))
    val span = (x1 - x0) / count
    val radius = abs(span) / 2
    val direction = if (baseY >= flatY) 1 else -1
    val p = Pts()
    p.add(x0, flatY); p.add(x1, flatY)
    for (bump in count - 1 downTo 0) {
        val cx = x0 + span * (bump + 0.5)
        for (s in 0..steps) { val a = s.toDouble() / steps * PI; p.add(cx + cos(a) * (span / 2), baseY + direction * sin(a) * radius) }
    }
    return p.arr()
}

internal fun heartPolygon(cx: Double, cy: Double, rx: Double, ry: Double, segments: Double = 64.0): DoubleArray {
    val count = max(16, jsRound(segments))
    val raw = DoubleArray(count * 2)
    var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
    for (i in 0 until count) {
        val t = i.toDouble() / count * TAU
        val x = sin(t).pow(3) * 16
        val y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))
        raw[i * 2] = x; raw[i * 2 + 1] = y
        minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
    }
    val hw = max(1e-6, (maxX - minX) / 2); val hh = max(1e-6, (maxY - minY) / 2)
    val mx = (minX + maxX) / 2; val my = (minY + maxY) / 2
    val out = DoubleArray(raw.size)
    for (i in raw.indices step 2) { out[i] = cx + ((raw[i] - mx) / hw) * rx; out[i + 1] = cy + ((raw[i + 1] - my) / hh) * ry }
    return out
}

internal fun annularSectorPolygon(cx: Double, cy: Double, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, segments: Double = 10.0): DoubleArray {
    val steps = max(2, jsRound(segments))
    val p = Pts()
    for (s in 0..steps) { val a = startAngle + ((endAngle - startAngle) * s) / steps; p.add(cx + cos(a) * outerRadius, cy + sin(a) * outerRadius) }
    for (s in steps downTo 0) { val a = startAngle + ((endAngle - startAngle) * s) / steps; p.add(cx + cos(a) * innerRadius, cy + sin(a) * innerRadius) }
    return p.arr()
}

internal fun starPolygon(cx: Double, cy: Double, outerRadius: Double, innerRadius: Double, points: Double, rotation: Double = 0.0): DoubleArray {
    val tips = max(3, jsRound(points))
    val p = Pts()
    for (i in 0 until tips * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val a = rotation - PI / 2 + (i.toDouble() / (tips * 2)) * TAU
        p.add(cx + cos(a) * r, cy + sin(a) * r)
    }
    return p.arr()
}

internal fun arcRibbonPolygon(x0: Double, x1: Double, y: Double, thickness: Double, sag: Double, steps: Double = 28.0): DoubleArray {
    val count = max(4, jsRound(steps))
    val half = thickness / 2
    val top = Pts(); val bottom = Pts()
    for (i in 0..count) {
        val pr = i.toDouble() / count
        val x = x0 + (x1 - x0) * pr
        val centre = y + sin(pr * PI) * sag
        top.add(x, centre - half); bottom.add(x, centre + half)
    }
    val b = bottom.arr()
    val p = Pts(); p.a.addAll(top.a)
    var i = b.size - 2
    while (i >= 0) { p.add(b[i], b[i + 1]); i -= 2 }
    return p.arr()
}

internal fun buildDiscField(seed: Long, salt: Int, width: Double, height: Double, count: Double, radius: Double): List<TpDisc> {
    val total = max(0, jsRound(count))
    if (total == 0 || width <= 0 || height <= 0) return emptyList()
    val columns = max(1, jsRound(sqrt(total * (width / height))))
    val rows = max(1, ceil(total.toDouble() / columns).toInt())
    val cw = width / columns; val ch = height / rows
    return List(total) { i ->
        val column = i % columns
        val row = floor(i.toDouble() / columns)
        TpDisc(
            cw * (column + 0.15 + th01(seed, i, salt) * 0.7),
            ch * (row + 0.15 + th01(seed, i, salt + 3) * 0.7),
            radius * (0.55 + th01(seed, i, salt + 7) * 0.75),
        )
    }
}
