// 商籁镜头 MG 几何变体 36–99（逐函数对译 Folia src/components/visualizer/sonnet/）：
//   sonnetOpenFrameShotMg.ts（36–47 开口框）、sonnetExtendedShotMg.ts 分发的
//   sonnetShotMgCelestial.ts（48–57 天体）/ Marine（58–67 海洋）/ Music（68–77 音乐）/ Craft（78–87 工艺）/ Kinetic（88–99 动态），
//   sonnetShotMgViewport.ts（出血范围）。
// 对译方式：数值一律按 JS number（Double）算——循环变量、种子都是 Double，`i / n` 不会变整除；
// 对象字面量换成 MgO（只用到的字段），回调 forEach 换 forEachIndexed。SonnetMgPenTest 对 node 直接跑 Folia 的金样（sonnet_mg_golden.txt）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Folia SonnetMgTarget：AnimatedGraphics 的路径记录接口（单测用记录器对照金样）。 */
internal interface SonnetMgPen {
    fun moveTo(x: Double, y: Double): SonnetMgPen
    fun lineTo(x: Double, y: Double): SonnetMgPen
    fun quadraticCurveTo(cx: Double, cy: Double, tx: Double, ty: Double): SonnetMgPen
    fun bezierCurveTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, tx: Double, ty: Double): SonnetMgPen
    fun arc(cx: Double, cy: Double, r: Double, start: Double, end: Double, anticlockwise: Boolean = false): SonnetMgPen
    fun circle(x: Double, y: Double, r: Double): SonnetMgPen
    fun rect(x: Double, y: Double, w: Double, h: Double): SonnetMgPen
    fun stroke(color: Int, width: Double, alpha: Double): SonnetMgPen
    fun fill(color: Int, alpha: Double): SonnetMgPen
}

/** AdditionalSonnetMgOptions（seed 按 JS number 存 Double）。 */
internal class SonnetMgOpts(
    val target: SonnetMgPen,
    val radius: Double,
    val width: Double,
    val height: Double,
    val seed: Double,
    val primary: Int,
    val secondary: Int,
)

/** 对象字面量的替身：各变体只用到其中几个字段。 */
private class MgO(
    val x: Double = 0.0, val y: Double = 0.0,
    val start: Double = 0.0, val end: Double = 0.0, val base: Double = 0.0,
    val baseY: Double = 0.0, val drift: Double = 0.0,
    val r: Double = 0.0, val s: Double = 0.0, val sx: Double = 0.0, val sy: Double = 0.0,
    val teeth: Double = 0.0, val up: Boolean = false,
)

private class MgBleed(val x: Double, val y: Double)

private const val TAU = PI * 2.0

/** JS `for (let v = a; v OP b; v += step)`，v 按 Double 走。 */
private fun jsFor(a: Double, b: Double, step: Double, op: String): List<Double> {
    val out = ArrayList<Double>()
    var v = a
    while (when (op) { "<" -> v < b; "<=" -> v <= b; ">" -> v > b; else -> v >= b }) { out.add(v); v += step }
    return out
}

/** JS Math.round。 */
private fun jsRound(v: Double) = floor(v + 0.5)

private fun h01(seed: Double, index: Double, salt: Double) = sonnetHash01(seed.toLong(), index.toInt(), salt.toInt())

/** sonnetShotMgViewport.ts：开口路径要伸出画面四边。 */
private fun resolveSonnetShotMgBleed(width: Double, height: Double, radius: Double) =
    MgBleed(max(radius * 0.92, width * 0.64), max(radius * 0.92, height * 0.64))

// ===== Folia src/components/visualizer/sonnet/sonnetOpenFrameShotMg.ts

// src/components/visualizer/sonnet/sonnetOpenFrameShotMg.ts
// Twelve airy "open frame" backgrounds. Text may sit outside the MG area, so
// none of these draw a closed hard boundary — arcs, brackets, rulers and
// fragments always stay open on at least one side.
private fun rotatePoint(x: Double, y: Double, angle: Double) = doubleArrayOf(
    x * cos(angle) - y * sin(angle),
    x * sin(angle) + y * cos(angle),
)
// Quarter arcs hugging four imaginary corners; nothing connects them.
private fun SonnetMgOpts.drawOpenArcBrackets() {
    val bx = width * 0.34
    val by = height * 0.34
    val arcR = radius * 0.17
    // (start, end) pairs sweep counter-clockwise through the outer quadrant.
    val corners = listOf(
        MgO(x = -bx, y = -by, start = PI, end = PI * 1.5),
        MgO(x = bx, y = -by, start = -PI / 2.0, end = 0.0),
        MgO(x = bx, y = by, start = 0.0, end = PI / 2.0),
        MgO(x = -bx, y = by, start = PI / 2.0, end = PI),
    )
    corners.forEachIndexed { index_, corner -> val index = index_.toDouble()
        target.arc(corner.x, corner.y, arcR, corner.start, corner.end)
            .stroke((if ((index % 2.0) != 0.0) secondary else primary), 3.0, 0.6)
        target.arc(corner.x, corner.y, arcR * 0.72, corner.start, corner.end)
            .stroke(primary, 1.0, 0.3)
        target.moveTo(corner.x - 5.0, corner.y).lineTo(corner.x + 5.0, corner.y)
            .stroke(primary, 1.0, 0.5)
        target.moveTo(corner.x, corner.y - 5.0).lineTo(corner.x, corner.y + 5.0)
            .stroke(primary, 1.0, 0.5)
    }
    val dotAngle = (seed % 8.0) * TAU / 8.0
    target.circle(cos(dotAngle) * radius * 0.5, sin(dotAngle) * radius * 0.5, radius * 0.02)
        .fill(secondary, 0.7)
}

// Concentric dashed orbits; every ring is broken into arcs with open gaps.
private fun SonnetMgOpts.drawDashedOrbits() {
    for (ring in jsFor(0.0, 3.0, 1.0, "<")) {
        val ringRadius = radius * (0.32 + ring * 0.18)
        val dashes = 12.0 + ring * 4.0
        val offset = seed * 0.13 + ring * 0.7
        val span = (TAU / dashes) * 0.55
        for (dash in jsFor(0.0, dashes, 1.0, "<")) {
            val start = offset + (dash / dashes) * TAU
            target.arc(0.0, 0.0, ringRadius, start, start + span)
                .stroke((if (ring == 1.0) secondary else primary), (if (ring == 1.0) 1.0 else 2.0), 0.28 + ring * 0.06)
        }
    }
    val markerAngle = seed * 0.31
    target.circle(cos(markerAngle) * radius * 0.68, sin(markerAngle) * radius * 0.68, radius * 0.022)
        .fill(primary, 0.75)
    target.circle(cos(markerAngle + PI) * radius * 0.5, sin(markerAngle + PI) * radius * 0.5, radius * 0.016)
        .fill(secondary, 0.6)
    target.circle(0.0, 0.0, radius * 0.05).stroke(primary, 1.5, 0.5)
}

// Scattered three-sided square fragments; no pane is ever closed.
private fun SonnetMgOpts.drawOpenFragments() {
    for (index in jsFor(0.0, 5.0, 1.0, "<")) {
        val angle = (index / 5.0) * TAU + seed * 0.05
        val distance = radius * (0.42 + ((seed + index * 7.0) % 30.0) / 100.0)
        val cx = cos(angle) * distance
        val cy = sin(angle) * distance * 0.8
        val size = radius * (0.07 + ((seed + index * 13.0) % 20.0) / 200.0)
        val rotation = seed * 0.11 + index * 0.9
        // Three sides of a square, the fourth left open.
        val points = listOf(
            rotatePoint(-size, -size, rotation),
            rotatePoint(size, -size, rotation),
            rotatePoint(size, size, rotation),
            rotatePoint(-size, size, rotation),
        )
        target.moveTo(cx + points[0][0], cy + points[0][1])
        for (point in jsFor(1.0, points.size.toDouble(), 1.0, "<")) {
            target.lineTo(cx + points[point.toInt()][0], cy + points[point.toInt()][1])
        }
        target.stroke((if ((index % 2.0) != 0.0) secondary else primary), 2.0, 0.5)
        if (index == (seed % 5.0)) {
            target.circle(cx, cy, size * 0.28).fill(primary, 0.35)
        }
    }
}

// Broken horizontal line bundles near the top and bottom, middle left empty.
private fun SonnetMgOpts.drawHorizonBundles() {
    val bundles = listOf(
        MgO(baseY = -height * 0.28, drift = 1.0),
        MgO(baseY = height * 0.3, drift = -1.0),
    )
    bundles.forEachIndexed { bundleIndex_, bundle -> val bundleIndex = bundleIndex_.toDouble()
        for (line in jsFor(0.0, 3.0, 1.0, "<")) {
            val y = bundle.baseY + line * 10.0 * bundle.drift
            val breakAt = width * ((((seed + line * 17.0 + bundleIndex * 31.0) % 40.0) + 30.0) / 100.0 - 0.5)
            val gapHalf = width * 0.045
            target.moveTo(-width * 0.4, y).lineTo(breakAt - gapHalf, y)
                .stroke((if (line == 1.0) secondary else primary), (if (line == 1.0) 2.0 else 1.0), 0.42 - line * 0.08)
            target.moveTo(breakAt + gapHalf, y).lineTo(width * 0.4, y)
                .stroke((if (line == 1.0) secondary else primary), (if (line == 1.0) 2.0 else 1.0), 0.42 - line * 0.08)
        }
        target.moveTo(-width * 0.42, bundle.baseY)
            .lineTo(-width * 0.42 + 6.0, bundle.baseY)
            .stroke(primary, 1.0, 0.4)
    }
    target.rect(width * 0.36, -height * 0.28 - 3.0, width * 0.04, 6.0).fill(secondary, 0.5)
}

// A 270.0-degree wreath with radial leaf ticks and a deliberate opening.
private fun SonnetMgOpts.drawSemiWreath() {
    val opening = (seed % 4.0) * (PI / 2.0) + PI / 8.0
    val span = TAU * 0.75
    val outer = radius * 0.58
    val inner = radius * 0.5
    target.arc(0.0, 0.0, outer, opening, opening + span)
        .stroke(primary, 2.5, 0.55)
    target.arc(0.0, 0.0, inner, opening, opening + span)
        .stroke(secondary, 1.0, 0.3)
    val ticks = 18.0
    for (index in jsFor(0.0, ticks, 1.0, "<")) {
        val angle = opening + (index / ticks) * span
        target.moveTo(cos(angle) * outer, sin(angle) * outer)
            .lineTo(cos(angle) * (outer + radius * 0.05), sin(angle) * (outer + radius * 0.05))
            .stroke(primary, 1.0, 0.4)
        if (index % 6.0 == 0.0) {
            target.circle(cos(angle) * inner, sin(angle) * inner, radius * 0.016)
                .fill(secondary, 0.6)
        }
    }
}

// Two vertical ruler columns with ticks; the composition never closes top/bottom.
private fun SonnetMgOpts.drawSideRulers() {
    for (side in listOf(-1.0, 1.0)) {
        val x = side * width * 0.36
        target.moveTo(x, -height * 0.3).lineTo(x, height * 0.3)
            .stroke(primary, 1.0, 0.35)
        for (tick in jsFor(0.0, 12.0, 1.0, "<")) {
            val y = -height * 0.3 + (tick / 11.0) * height * 0.6
            val length = (if (tick % 4.0 == 0.0) 18.0 else 9.0)
            target.moveTo(x, y).lineTo(x - side * length, y)
                .stroke((if (tick % 4.0 == 0.0) secondary else primary), 1.0, 0.45)
        }
        val accentY = -height * 0.3 + (((seed + ((if (side > 0.0) 5.0 else 0.0))) % 11.0) / 11.0) * height * 0.6
        target.rect((if (side > 0.0) x else x - 4.0), accentY - 4.0, 4.0, 8.0).fill(secondary, 0.6)
    }
}

// A diagonal bundle of parallel stream lines with two open diamonds.
private fun SonnetMgOpts.drawDiagonalStream() {
    val angle = PI / 4.0 + (seed % 3.0) * (PI / 12.0)
    val dirX = cos(angle)
    val dirY = sin(angle)
    val normalX = -dirY
    val normalY = dirX
    for (line in jsFor(0.0, 7.0, 1.0, "<")) {
        val offset = (line - 3.0) * radius * 0.16
        val length = radius * (0.55 + ((seed + line * 29.0) % 40.0) / 100.0)
        val cx = normalX * offset
        val cy = normalY * offset
        target.moveTo(cx - dirX * length, cy - dirY * length)
            .lineTo(cx + dirX * length * 0.6, cy + dirY * length * 0.6)
            .stroke((if (line == 3.0) secondary else primary), (if (line == 3.0) 2.0 else 1.0), 0.22 + line * 0.03)
    }
    // Diamonds drawn with one edge missing.
    listOf(-radius * 0.16, radius * 0.16).forEachIndexed { index_, offset -> val index = index_.toDouble()
        val cx = normalX * offset + dirX * radius * 0.1
        val cy = normalY * offset + dirY * radius * 0.1
        val size = radius * 0.07
        val points = listOf(
            rotatePoint(0.0, -size, angle), rotatePoint(size, 0.0, angle),
            rotatePoint(0.0, size, angle), rotatePoint(-size, 0.0, angle),
        )
        target.moveTo(cx + points[0][0], cy + points[0][1])
        for (point in jsFor(1.0, points.size.toDouble(), 1.0, "<")) {
            target.lineTo(cx + points[point.toInt()][0], cy + points[point.toInt()][1])
        }
        target.stroke(primary, 1.5, 0.55)
        if (index == 0.0) target.circle(cx, cy, size * 0.22).fill(secondary, 0.55)
    }
}

// Curved petal strokes spraying from two opposite corners only.
private fun SonnetMgOpts.drawCornerPetalSpray() {
    val sign = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    // Petals spray inward from two diagonally opposite corners.
    val corners = listOf(
        MgO(x = -sign * width * 0.28, y = -height * 0.26, base = atan2(1.0, sign)),
        MgO(x = sign * width * 0.28, y = height * 0.26, base = atan2(-1.0, -sign)),
    )
    corners.forEachIndexed { cornerIndex_, corner -> val cornerIndex = cornerIndex_.toDouble()
        for (petal in jsFor(0.0, 5.0, 1.0, "<")) {
            val spread = (petal - 2.0) * 0.3
            val angle = corner.base + spread
            val length = radius * (0.3 - abs(petal - 2.0) * 0.045)
            val endX = corner.x + cos(angle) * length
            val endY = corner.y + sin(angle) * length
            val ctrlX = corner.x + cos(angle + 0.35) * length * 0.55
            val ctrlY = corner.y + sin(angle + 0.35) * length * 0.55
            target.moveTo(corner.x, corner.y)
                .quadraticCurveTo(ctrlX, ctrlY, endX, endY)
                .stroke((if (petal == 2.0) secondary else primary), (if (petal == 2.0) 2.0 else 1.0), 0.4)
        }
        if (cornerIndex == 0.0) {
            target.circle(corner.x, corner.y, radius * 0.02).fill(primary, 0.6)
        }
    }
}

// A sparse dotted field with three open corner-window brackets.
private fun SonnetMgOpts.drawDottedWindows() {
    val columns = 7.0
    val rows = 5.0
    for (row in jsFor(0.0, rows, 1.0, "<")) {
        for (column in jsFor(0.0, columns, 1.0, "<")) {
            if ((row * columns + column + seed) % 5.0 == 0.0) continue
            val x = (column - (columns - 1.0) / 2.0) * width * 0.11
            val y = (row - (rows - 1.0) / 2.0) * height * 0.14
            target.circle(x, y, 1.5).fill(primary, 0.35)
        }
    }
    val arm = radius * 0.1
    for (window in jsFor(0.0, 3.0, 1.0, "<")) {
        val cellX = (((seed + window * 2.0) % columns) - (columns - 1.0) / 2.0) * width * 0.11
        val cellY = (((seed + window * 3.0 + 1.0) % rows) - (rows - 1.0) / 2.0) * height * 0.14
        val flipX = (if ((seed + window) % 2.0 == 0.0) 1.0 else -1.0)
        val flipY = (if ((seed + window * 2.0) % 2.0 == 0.0) 1.0 else -1.0)
        // A single right-angle bracket marking an imaginary pane corner.
        target.moveTo(cellX + flipX * arm, cellY)
            .lineTo(cellX, cellY)
            .lineTo(cellX, cellY + flipY * arm)
            .stroke(secondary, 2.0, 0.6)
    }
}

// Open 200.0-degree radar arcs with a sweep line and an unclosed target mark.
private fun SonnetMgOpts.drawOpenRadar() {
    val span = TAU * 0.56
    for (ring in jsFor(0.0, 3.0, 1.0, "<")) {
        val ringRadius = radius * (0.3 + ring * 0.18)
        val start = seed * 0.1 + ring * 0.9
        target.arc(0.0, 0.0, ringRadius, start, start + span)
            .stroke((if (ring == 1.0) secondary else primary), (if (ring == 1.0) 2.0 else 1.0), 0.35 + ring * 0.06)
    }
    val sweep = seed * 0.07
    target.moveTo(0.0, 0.0)
        .lineTo(cos(sweep) * radius * 0.66, sin(sweep) * radius * 0.66)
        .stroke(primary, 1.5, 0.5)
    val lockAngle = sweep + 0.8
    val lockX = cos(lockAngle) * radius * 0.48
    val lockY = sin(lockAngle) * radius * 0.48
    val tick = radius * 0.04
    // Four ticks around an imaginary square, none of them touching.
    target.moveTo(lockX - tick * 2.0, lockY - tick).lineTo(lockX - tick * 2.0, lockY - tick * 2.0).lineTo(lockX - tick, lockY - tick * 2.0)
        .stroke(secondary, 1.5, 0.7)
    target.moveTo(lockX + tick * 2.0, lockY + tick).lineTo(lockX + tick * 2.0, lockY + tick * 2.0).lineTo(lockX + tick, lockY + tick * 2.0)
        .stroke(secondary, 1.5, 0.7)
    target.circle(0.0, 0.0, radius * 0.018).fill(primary, 0.8)
    target.circle(lockX, lockY, radius * 0.014).fill(secondary, 0.7)
}

// Oversized tapered brush bars scattered loosely, paired with open squares.
private fun SonnetMgOpts.drawBrushStrokes() {
    for (stroke in jsFor(0.0, 4.0, 1.0, "<")) {
        val horizontal = stroke % 2.0 == 0.0
        val along = (((seed + stroke * 23.0) % 50.0) / 100.0 - 0.25) * ((if (horizontal) width else height))
        val across = (((seed + stroke * 41.0) % 60.0) / 100.0 - 0.3) * ((if (horizontal) height else width))
        val length = radius * (0.3 + ((seed + stroke * 7.0) % 25.0) / 100.0)
        val x1 = (if (horizontal) along - length else across)
        val y1 = (if (horizontal) across else along - length)
        val x2 = (if (horizontal) along + length else across)
        val y2 = (if (horizontal) across else along + length)
        target.moveTo(x1, y1).lineTo(x2, y2)
            .stroke((if (stroke == 1.0) secondary else primary), radius * 0.04, 0.22)
        target.moveTo(x1, y1 + ((if (horizontal) radius * 0.035 else 0.0)))
            .lineTo((if (horizontal) x2 * 0.6 else x2), (if (horizontal) y2 + radius * 0.035 else y2 * 0.6))
            .stroke(primary, radius * 0.012, 0.45)
    }
    // Three-sided open square accents at two stroke ends.
    val size = radius * 0.05
    val anchorX = width * 0.3
    val anchorY = -height * 0.3
    target.moveTo(anchorX - size, anchorY - size).lineTo(anchorX + size, anchorY - size).lineTo(anchorX + size, anchorY + size)
        .stroke(secondary, 1.5, 0.6)
    target.rect(-anchorX - size / 2.0, -anchorY - size / 2.0, size, size).fill(primary, 0.4)
}

// Stitch-dashed L corners far apart plus a dotted center crosshair.
private fun SonnetMgOpts.drawStitchCorners() {
    val arm = radius * 0.18
    val dash = radius * 0.03
    val corners = listOf(
        MgO(x = -width * 0.39, y = -height * 0.36, sx = 1.0, sy = 1.0),
        MgO(x = width * 0.39, y = -height * 0.36, sx = -1.0, sy = 1.0),
        MgO(x = width * 0.39, y = height * 0.36, sx = -1.0, sy = -1.0),
        MgO(x = -width * 0.39, y = height * 0.36, sx = 1.0, sy = -1.0),
    )
    corners.forEachIndexed { index_, corner -> val index = index_.toDouble()
        var offset = 0.0
        while (offset + dash <= arm) {
            target.moveTo(corner.x + corner.sx * offset, corner.y)
                .lineTo(corner.x + corner.sx * (offset + dash), corner.y)
                .stroke((if ((index % 2.0) != 0.0) secondary else primary), 2.0, 0.5)
            target.moveTo(corner.x, corner.y + corner.sy * offset)
                .lineTo(corner.x, corner.y + corner.sy * (offset + dash))
                .stroke((if ((index % 2.0) != 0.0) secondary else primary), 2.0, 0.5)
            offset += dash * 2.0
        }
    }
    val crossArm = radius * 0.09
    var offset = -crossArm
    while (offset + dash * 0.6 <= crossArm) {
        target.moveTo(offset, 0.0).lineTo(offset + dash * 0.6, 0.0)
            .stroke(primary, 1.0, 0.4)
        target.moveTo(0.0, offset).lineTo(0.0, offset + dash * 0.6)
            .stroke(primary, 1.0, 0.4)
        offset += dash * 1.2
    }
    target.circle(0.0, 0.0, radius * 0.012).fill(secondary, 0.7)
    // A seed-picked accent stitch near one corner arm keeps seeds distinct.
    val accentCorner = corners[(seed % corners.size.toDouble()).toInt()]
    target.circle(accentCorner.x + accentCorner.sx * arm, accentCorner.y, radius * 0.014)
        .fill(primary, 0.6)
}


// ===== Folia src/components/visualizer/sonnet/sonnetShotMgCelestial.ts

// src/components/visualizer/sonnet/sonnetShotMgCelestial.ts
// Ten celestial extended backgrounds (geo variants 48.0-57.0). Every composition
// is open — no closed viewport frames, no clip masks — and each motif is split
// into many short stroke/fill commands so the shared stagger schedule grows
// them in layered, offset waves.

// 48.0: twin log-spiral arms with a bright core and free-floating star dust.
private fun SonnetMgOpts.drawSpiralGalaxy() {
    for (arm in jsFor(0.0, 2.0, 1.0, "<")) {
        val offset = arm * PI + h01(seed, arm, 101.0) * 0.5
        target.moveTo(cos(offset) * radius * 0.06, sin(offset) * radius * 0.05)
        val steps = 56.0
        for (i in jsFor(1.0, steps, 1.0, "<=")) {
            val t = i / steps
            val angle = offset + t * PI * 3.1
            val r = radius * (0.06 + t * 0.62)
            target.lineTo(cos(angle) * r, sin(angle) * r * 0.72)
        }
        target.stroke((if (arm == 0.0) primary else secondary), 2.0, 0.5 - arm * 0.12)
    }
    target.circle(0.0, 0.0, radius * 0.07).fill(primary, 0.7)
    target.circle(0.0, 0.0, radius * 0.12).stroke(primary, 1.0, 0.3)
    for (i in jsFor(0.0, 14.0, 1.0, "<")) {
        val angle = h01(seed, i, 103.0) * TAU
        val r = radius * (0.2 + h01(seed, i, 107.0) * 0.55)
        target.circle(cos(angle) * r, sin(angle) * r * 0.72, 1.4 + h01(seed, i, 109.0) * 2.2)
            .fill((if (i % 3.0 == 0.0) secondary else primary), 0.3 + h01(seed, i, 113.0) * 0.35)
    }
}

// 49.0: a comet head with three curved tail trails and cross sparkles.
private fun SonnetMgOpts.drawCometTrail() {
    val direction = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    val hx = radius * 0.34 * direction
    val hy = -radius * 0.18
    for (tail in jsFor(0.0, 3.0, 1.0, "<")) {
        val spread = (tail - 1.0) * radius * 0.12
        target.moveTo(hx - direction * radius * 0.04, hy + spread * 0.3)
            .bezierCurveTo(
                hx - direction * radius * 0.35, hy + spread,
                hx - direction * radius * 0.6, hy + radius * 0.16 + spread,
                hx - direction * radius * (0.85 + tail * 0.06), hy + radius * 0.3 + spread * 1.2,
            )
            .stroke((if (tail == 1.0) secondary else primary), 3.0 - tail, 0.55 - tail * 0.12)
    }
    target.circle(hx, hy, radius * 0.09).fill(primary, 0.75)
    target.circle(hx, hy, radius * 0.14).stroke(primary, 1.0, 0.35)
    for (i in jsFor(0.0, 5.0, 1.0, "<")) {
        val x = (h01(seed, i, 127.0) - 0.5) * radius * 1.4
        val y = radius * (0.1 + h01(seed, i, 131.0) * 0.5)
        val s = 2.5 + h01(seed, i, 137.0) * 2.5
        target.moveTo(x - s, y).lineTo(x + s, y).stroke(secondary, 1.0, 0.45)
        target.moveTo(x, y - s).lineTo(x, y + s).stroke(secondary, 1.0, 0.45)
    }
}

// 50.0: eclipsed disc with an uneven corona of alternating rays.
private fun SonnetMgOpts.drawEclipseCorona() {
    val discR = radius * 0.24
    target.circle(0.0, 0.0, discR).fill(primary, 0.16)
    target.circle(0.0, 0.0, discR).stroke(secondary, 2.0, 0.65)
    target.circle(0.0, 0.0, discR * 1.14).stroke(primary, 1.0, 0.25)
    val rays = 28.0
    for (i in jsFor(0.0, rays, 1.0, "<")) {
        val angle = (i / rays) * TAU + h01(seed, i, 139.0) * 0.08
        val inner = discR * 1.2
        val outer = radius * ((if (i % 2.0 == 0.0) 0.6 else 0.42)) * (0.85 + h01(seed, i, 149.0) * 0.3)
        target.moveTo(cos(angle) * inner, sin(angle) * inner)
            .lineTo(cos(angle) * outer, sin(angle) * outer)
            .stroke((if (i % 4.0 == 0.0) secondary else primary), (if (i % 2.0 == 0.0) 2.0 else 1.0), 0.3 + (i % 3.0) * 0.1)
    }
}

// 51.0: diagonal meteor streaks with glowing heads, all open-ended.
private fun SonnetMgOpts.drawMeteorShower() {
    val direction = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    for (i in jsFor(0.0, 8.0, 1.0, "<")) {
        val x = (h01(seed, i, 151.0) - 0.5) * radius * 1.5
        val y = -radius * 0.55 + h01(seed, i, 157.0) * radius * 0.9
        val len = radius * (0.2 + h01(seed, i, 163.0) * 0.3)
        val dx = direction * len
        val dy = len * 0.55
        target.moveTo(x, y).lineTo(x - dx, y - dy)
            .stroke(primary, 2.0, 0.55)
        target.moveTo(x - dx * 0.15, y - dy * 0.15 + 3.0).lineTo(x - dx * 0.85, y - dy * 0.85 + 3.0)
            .stroke(secondary, 1.0, 0.3)
        target.circle(x, y, 2.0 + h01(seed, i, 167.0) * 2.0)
            .fill((if (i % 2.0 == 0.0) secondary else primary), 0.7)
    }
}

// 52.0: broken orbit rings carrying small satellite diamonds.
private fun SonnetMgOpts.drawOrbitSatellites() {
    for (ring in jsFor(0.0, 3.0, 1.0, "<")) {
        val r = radius * (0.28 + ring * 0.18)
        val gapStart = h01(seed, ring, 173.0) * TAU
        val segs = 3.0 + ring
        for (s in jsFor(0.0, segs, 1.0, "<")) {
            val start = gapStart + (s / segs) * TAU
            target.arc(0.0, 0.0, r, start, start + (TAU / segs) * 0.68)
                .stroke((if (ring == 1.0) secondary else primary), (if (ring == 0.0) 2.0 else 1.0), 0.35 + ring * 0.08)
        }
        val satAngle = h01(seed, ring, 179.0) * TAU
        val sx = cos(satAngle) * r
        val sy = sin(satAngle) * r
        val d = 5.0 + ring * 2.0
        target.moveTo(sx, sy - d).lineTo(sx + d, sy).lineTo(sx, sy + d).lineTo(sx - d, sy).lineTo(sx, sy - d)
            .fill(secondary, 0.75)
    }
    target.circle(0.0, 0.0, radius * 0.06).fill(primary, 0.8)
}

// 53.0: vertical aurora ribbons flowing down from the top, no edges.
private fun SonnetMgOpts.drawAuroraRibbons() {
    for (band in jsFor(0.0, 4.0, 1.0, "<")) {
        val x0 = -radius * 0.6 + band * radius * 0.38 + (h01(seed, band, 181.0) - 0.5) * radius * 0.1
        val sway = ((if (band % 2.0 == 0.0) 1.0 else -1.0)) * radius * 0.2
        target.moveTo(x0, -radius * 0.75)
            .bezierCurveTo(
                x0 + sway, -radius * 0.35,
                x0 - sway, radius * 0.1,
                x0 + sway * 0.6, radius * 0.55,
            )
            .stroke((if (band % 2.0 == 0.0) primary else secondary), 7.0 - band, 0.16 + band * 0.05)
        target.moveTo(x0 + radius * 0.06, -radius * 0.7)
            .bezierCurveTo(
                x0 + sway + radius * 0.06, -radius * 0.3,
                x0 - sway + radius * 0.06, radius * 0.12,
                x0 + sway * 0.6 + radius * 0.06, radius * 0.5,
            )
            .stroke(primary, 1.0, 0.3)
    }
    for (i in jsFor(0.0, 8.0, 1.0, "<")) {
        target.circle(
            (h01(seed, i, 191.0) - 0.5) * radius * 1.5,
            -radius * 0.6 + h01(seed, i, 193.0) * radius * 0.5,
            1.4,
        ).fill(secondary, 0.5)
    }
}

// 54.0: crescent with halo ring and hanging star pendants.
private fun SonnetMgOpts.drawCrescentHalo() {
    val moonR = radius * 0.3
    val cx = -radius * 0.12
    val cy = -radius * 0.1
    target.moveTo(cx, cy - moonR)
    target.arc(cx, cy, moonR, -PI / 2.0, PI / 2.0, false)
    target.quadraticCurveTo(cx - moonR * 0.45, cy, cx, cy - moonR)
    target.fill(primary, 0.55)
    target.circle(cx, cy, moonR * 1.35).stroke(secondary, 1.0, 0.3)
    target.circle(cx, cy, moonR * 1.5).stroke(primary, 1.0, 0.16)
    for (i in jsFor(0.0, 3.0, 1.0, "<")) {
        val px = radius * (0.18 + i * 0.16)
        val topY = -radius * 0.5 + h01(seed, i, 197.0) * radius * 0.1
        val len = radius * (0.14 + h01(seed, i, 199.0) * 0.12)
        target.moveTo(px, topY).lineTo(px, topY + len).stroke(primary, 1.0, 0.4)
        val sr = 4.0 + i
        val sy = topY + len + sr
        target.moveTo(px, sy - sr).lineTo(px + sr * 0.25, sy - sr * 0.25)
            .lineTo(px + sr, sy).lineTo(px + sr * 0.25, sy + sr * 0.25)
            .lineTo(px, sy + sr).lineTo(px - sr * 0.25, sy + sr * 0.25)
            .lineTo(px - sr, sy).lineTo(px - sr * 0.25, sy - sr * 0.25)
            .lineTo(px, sy - sr)
            .stroke(secondary, 1.0, 0.6)
    }
}

// 55.0: nested organic nebula veils — closed bezier blobs, no straight edges.
private fun SonnetMgOpts.drawNebulaVeil() {
    for (blob in jsFor(0.0, 4.0, 1.0, "<")) {
        val bx = (h01(seed, blob, 211.0) - 0.5) * radius * 0.5
        val by = (h01(seed, blob, 223.0) - 0.5) * radius * 0.4
        val br = radius * (0.2 + blob * 0.1)
        val wobble = h01(seed, blob, 227.0) * 0.6
        target.moveTo(bx + br, by)
        target.bezierCurveTo(bx + br, by - br * (0.6 + wobble * 0.3), bx + br * 0.5, by - br, bx, by - br * (0.9 - wobble * 0.2))
        target.bezierCurveTo(bx - br * 0.6, by - br * 0.8, bx - br, by - br * 0.3, bx - br * (0.85 + wobble * 0.2), by + br * 0.2)
        target.bezierCurveTo(bx - br * 0.7, by + br * 0.7, bx - br * 0.2, by + br, bx + br * 0.3, by + br * (0.8 + wobble * 0.2))
        target.bezierCurveTo(bx + br * 0.8, by + br * 0.6, bx + br, by + br * 0.4, bx + br, by)
        target.stroke((if (blob % 2.0 == 0.0) primary else secondary), 1.5, 0.35 - blob * 0.04)
        if (blob < 2.0) target.fill(primary, 0.05)
    }
    for (i in jsFor(0.0, 10.0, 1.0, "<")) {
        target.circle(
            (h01(seed, i, 229.0) - 0.5) * radius * 1.2,
            (h01(seed, i, 233.0) - 0.5) * radius * 1.0,
            1.2 + h01(seed, i, 239.0) * 1.8,
        ).fill(primary, 0.25 + h01(seed, i, 241.0) * 0.3)
    }
}

// 56.0: survey-style star map — faint cross grid plus one bright constellation.
private fun SonnetMgOpts.drawStarMap() {
    for (gx in jsFor(0.0, 5.0, 1.0, "<")) {
        for (gy in jsFor(0.0, 4.0, 1.0, "<")) {
            val x = -radius * 0.6 + gx * radius * 0.3
            val y = -radius * 0.45 + gy * radius * 0.3
            target.moveTo(x - 3.0, y).lineTo(x + 3.0, y).stroke(primary, 1.0, 0.18)
            target.moveTo(x, y - 3.0).lineTo(x, y + 3.0).stroke(primary, 1.0, 0.18)
        }
    }
    val nodes = 6.0
    var px = 0.0
    var py = 0.0
    for (i in jsFor(0.0, nodes, 1.0, "<")) {
        val x = -radius * 0.5 + h01(seed, i, 251.0) * radius
        val y = -radius * 0.4 + h01(seed, i, 257.0) * radius * 0.8
        if (i > 0.0) {
            target.moveTo(px, py).lineTo(x, y).stroke(secondary, 1.5, 0.55)
        }
        target.circle(x, y, 3.0).fill(primary, 0.8)
        target.circle(x, y, 6.5).stroke(primary, 1.0, 0.3)
        px = x
        py = y
    }
}

// 57.0: moon above, open tide arcs below — a bridge between sky and sea.
private fun SonnetMgOpts.drawLunarTide() {
    val mx = radius * 0.28 * ((if (seed % 2.0 == 0.0) 1.0 else -1.0))
    val my = -radius * 0.34
    target.circle(mx, my, radius * 0.16).fill(primary, 0.2)
    target.circle(mx, my, radius * 0.16).stroke(primary, 2.0, 0.6)
    target.arc(mx, my, radius * 0.24, PI * 0.2, PI * 0.8).stroke(secondary, 1.0, 0.35)
    for (row in jsFor(0.0, 4.0, 1.0, "<")) {
        val y = radius * (0.05 + row * 0.14)
        val arcs = 6.0 - row
        for (i in jsFor(0.0, arcs, 1.0, "<")) {
            val x = -radius * 0.62 + i * radius * 0.24 + (row % 2.0) * radius * 0.12
            target.arc(x, y, radius * 0.09, PI, TAU)
                .stroke((if (row % 2.0 == 0.0) primary else secondary), (if (row == 0.0) 2.0 else 1.0), 0.45 - row * 0.07)
        }
    }
}

// ===== Folia src/components/visualizer/sonnet/sonnetShotMgMarine.ts

// src/components/visualizer/sonnet/sonnetShotMgMarine.ts
// Ten marine extended backgrounds (geo variants 58.0-67.0). Open compositions only:
// wave rows and currents bleed past the viewport, shells and coral stay
// unframed, and every stroke is its own command for staggered growth.

// 58.0: rows of repeating wave-crest arcs across the full bleed width.
private fun SonnetMgOpts.drawWaveScrolls() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    for (row in jsFor(0.0, 4.0, 1.0, "<")) {
        val y = -radius * 0.3 + row * radius * 0.22
        val crestR = radius * 0.1
        val step = crestR * 2.1
        val count = ceil((bleed.x * 2.0) / step)
        for (i in jsFor(0.0, count, 1.0, "<")) {
            val x = -bleed.x + i * step + (row % 2.0) * crestR
            target.arc(x, y, crestR, PI, TAU)
                .stroke((if ((i + row) % 3.0 == 0.0) secondary else primary), (if (row == 1.0) 2.0 else 1.0), 0.42 - row * 0.06)
        }
    }
    for (i in jsFor(0.0, 6.0, 1.0, "<")) {
        target.circle(
            (h01(seed, i, 263.0) - 0.5) * bleed.x * 1.4,
            -radius * 0.55 + h01(seed, i, 269.0) * radius * 0.25,
            1.6,
        ).fill(secondary, 0.4)
    }
}

// 59.0: nautilus shell — sampled log spiral with chamber dividers, open outer tip.
private fun SonnetMgOpts.drawNautilus() {
    val turns = 2.6
    val steps = 90.0
    val startAngle = h01(seed, 0.0, 271.0) * TAU
    target.moveTo(cos(startAngle) * radius * 0.04, sin(startAngle) * radius * 0.04)
    for (i in jsFor(1.0, steps, 1.0, "<=")) {
        val t = i / steps
        val angle = startAngle + t * turns * TAU
        val r = radius * (0.04 + t * 0.5)
        target.lineTo(cos(angle) * r, sin(angle) * r * 0.94)
    }
    target.stroke(primary, 2.5, 0.65)
    // Chamber dividers radiate from the spiral core at growing radii.
    for (i in jsFor(1.0, 8.0, 1.0, "<=")) {
        val t = i / 9.0
        val angle = startAngle + t * turns * TAU
        val r = radius * (0.04 + t * 0.5)
        target.moveTo(cos(angle) * r * 0.55, sin(angle) * r * 0.52)
            .lineTo(cos(angle) * r, sin(angle) * r * 0.94)
            .stroke(secondary, 1.0, 0.35)
    }
    target.circle(0.0, 0.0, radius * 0.05).stroke(primary, 1.5, 0.5)
}

// 60.0: coral branch grown from the bottom with forked limbs and tip buds.
private fun SonnetMgOpts.drawCoralBranch() {
    val baseX = ((if (seed % 2.0 == 0.0) -1.0 else 1.0)) * radius * 0.12
    fun fork(x: Double, y: Double, angle: Double, len: Double, width_: Double, depth: Double, limb: Double) {
        val ex = x + cos(angle) * len
        val ey = y + sin(angle) * len
        target.moveTo(x, y)
            .quadraticCurveTo(
                x + cos(angle + 0.3) * len * 0.5,
                y + sin(angle + 0.3) * len * 0.5,
                ex, ey,
            )
            .stroke((if (depth == 0.0) secondary else primary), width_, 0.6 - depth * 0.12)
        target.circle(ex, ey, width_ * 0.9).fill(secondary, 0.5)
        if (depth < 2.0) {
            val spread = 0.55 + h01(seed, limb, 277.0) * 0.3
            fork(ex, ey, angle - spread, len * 0.62, max(1.0, width_ - 1.0), depth + 1.0, limb * 2.0 + 1.0)
            fork(ex, ey, angle + spread * 0.8, len * 0.68, max(1.0, width_ - 1.0), depth + 1.0, limb * 2.0 + 2.0)
        }
    }
    fork(baseX, radius * 0.62, -PI / 2.0, radius * 0.34, 3.0, 0.0, 0.0)
    // A few detached polyps drifting nearby.
    for (i in jsFor(0.0, 5.0, 1.0, "<")) {
        target.circle(
            baseX + (h01(seed, i, 281.0) - 0.5) * radius * 0.9,
            radius * (0.3 + h01(seed, i, 283.0) * 0.3),
            1.8,
        ).fill(primary, 0.35)
    }
}

// 61.0: lighthouse on a low rock, twin light beams fanning out, open sea arcs.
private fun SonnetMgOpts.drawLighthouseBeam() {
    val direction = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    val bx = -radius * 0.3 * direction
    val baseY = radius * 0.42
    // Tower: tapered trapezoid outline, no base bar.
    target.moveTo(bx - radius * 0.09, baseY)
        .lineTo(bx - radius * 0.05, baseY - radius * 0.42)
        .lineTo(bx + radius * 0.05, baseY - radius * 0.42)
        .lineTo(bx + radius * 0.09, baseY)
        .stroke(primary, 2.0, 0.6)
    target.rect(bx - radius * 0.07, baseY - radius * 0.52, radius * 0.14, radius * 0.1)
        .stroke(primary, 1.5, 0.55)
    target.circle(bx, baseY - radius * 0.47, radius * 0.025).fill(secondary, 0.9)
    // Twin beams fan to the open right side.
    for (beam in jsFor(0.0, 2.0, 1.0, "<")) {
        val spread = radius * (0.1 + beam * 0.12)
        target.moveTo(bx, baseY - radius * 0.47)
            .lineTo(bx + direction * radius * 0.85, baseY - radius * 0.47 - spread)
            .stroke(secondary, 1.5, 0.4 - beam * 0.1)
        target.moveTo(bx, baseY - radius * 0.47)
            .lineTo(bx + direction * radius * 0.85, baseY - radius * 0.47 + spread)
            .stroke(secondary, 1.5, 0.4 - beam * 0.1)
    }
    // Sea arcs below, unconnected.
    for (i in jsFor(0.0, 5.0, 1.0, "<")) {
        val x = -radius * 0.6 + i * radius * 0.3
        target.arc(x, radius * 0.56, radius * 0.1, PI, TAU)
            .stroke(primary, 1.0, 0.35)
    }
}

// 62.0: compass rose with long cardinal needles and a partial degree arc.
private fun SonnetMgOpts.drawCompassRose() {
    val roseR = radius * 0.42
    for (i in jsFor(0.0, 8.0, 1.0, "<")) {
        val angle = (i / 8.0) * TAU - PI / 2.0
        val long = i % 2.0 == 0.0
        val len = (if (long) roseR else roseR * 0.55)
        val halfWidth = (if (long) 0.09 else 0.06)
        // Needle = thin triangle from center.
        val tx = cos(angle) * len
        val ty = sin(angle) * len
        val lx = cos(angle + halfWidth) * roseR * 0.16
        val ly = sin(angle + halfWidth) * roseR * 0.16
        val rx = cos(angle - halfWidth) * roseR * 0.16
        val ry = sin(angle - halfWidth) * roseR * 0.16
        target.moveTo(lx, ly).lineTo(tx, ty).lineTo(rx, ry)
            .stroke((if (long) primary else secondary), (if (long) 2.0 else 1.0), (if (long) 0.65 else 0.45))
        if (long && i % 4.0 == 0.0) {
            target.moveTo(lx, ly).lineTo(tx, ty).lineTo(rx, ry)
                .fill(primary, 0.1)
        }
    }
    target.circle(0.0, 0.0, roseR * 0.14).stroke(primary, 1.5, 0.6)
    target.circle(0.0, 0.0, roseR * 0.05).fill(secondary, 0.8)
    // Degree ticks only along one open arc — deliberately not a closed dial.
    val arcStart = h01(seed, 0.0, 293.0) * TAU
    for (i in jsFor(0.0, 24.0, 1.0, "<=")) {
        val angle = arcStart + (i / 24.0) * PI * 1.2
        val inner = roseR * 1.12
        val outer = inner + ((if (i % 6.0 == 0.0) 10.0 else 5.0))
        target.moveTo(cos(angle) * inner, sin(angle) * inner)
            .lineTo(cos(angle) * outer, sin(angle) * outer)
            .stroke(primary, 1.0, 0.4)
    }
}

// 63.0: three abstract sailboats with curved sails and open water dashes.
private fun SonnetMgOpts.drawSailRegatta() {
    for (boat in jsFor(0.0, 3.0, 1.0, "<")) {
        val scale = 1.0 - boat * 0.24
        val bx = (boat - 1.0) * radius * 0.44 + (h01(seed, boat, 307.0) - 0.5) * radius * 0.08
        val by = radius * (0.28 - boat * 0.12)
        val mastH = radius * 0.34 * scale
        target.moveTo(bx, by).lineTo(bx, by - mastH)
            .stroke(primary, 2.0, 0.6)
        // Curved sail via quadratic leech.
        target.moveTo(bx, by - mastH)
            .quadraticCurveTo(bx + radius * 0.2 * scale, by - mastH * 0.55, bx, by - mastH * 0.08)
            .stroke((if (boat == 1.0) secondary else primary), 1.5, 0.55)
        target.moveTo(bx, by - mastH * 0.92)
            .lineTo(bx - radius * 0.13 * scale, by - mastH * 0.1)
            .lineTo(bx, by - mastH * 0.1)
            .stroke(primary, 1.0, 0.4)
        // Hull: shallow arc, open at both ends.
        target.moveTo(bx - radius * 0.15 * scale, by)
            .quadraticCurveTo(bx, by + radius * 0.07 * scale, bx + radius * 0.15 * scale, by)
            .stroke(primary, 2.0, 0.55)
    }
    for (i in jsFor(0.0, 7.0, 1.0, "<")) {
        val x = -radius * 0.66 + i * radius * 0.22
        val y = radius * (0.42 + (i % 2.0) * 0.05)
        target.moveTo(x, y).lineTo(x + radius * 0.1, y)
            .stroke(secondary, 1.0, 0.35)
    }
}

// 64.0: three rising bubble columns with highlight arcs on the large ones.
private fun SonnetMgOpts.drawBubbleRise() {
    for (column in jsFor(0.0, 3.0, 1.0, "<")) {
        val x = (column - 1.0) * radius * 0.34 + (h01(seed, column, 311.0) - 0.5) * radius * 0.12
        val count = 5.0 + column
        for (i in jsFor(0.0, count, 1.0, "<")) {
            val t = i / count
            val y = radius * 0.55 - t * radius * 1.05
            val wobble = (h01(seed, column * 10.0 + i, 313.0) - 0.5) * radius * 0.08
            val r = radius * (0.02 + t * 0.055)
            target.circle(x + wobble, y, r)
                .stroke((if (i % 3.0 == 0.0) secondary else primary), 1.0, 0.35 + t * 0.35)
            if (r > radius * 0.05) {
                target.arc(x + wobble, y, r * 0.55, PI * 1.1, PI * 1.6)
                    .stroke(secondary, 1.0, 0.5)
            }
        }
    }
}

// 65.0: overlapping organic tide-pool rings with pebble dots inside.
private fun SonnetMgOpts.drawTidePools() {
    for (pool in jsFor(0.0, 4.0, 1.0, "<")) {
        val px = (h01(seed, pool, 317.0) - 0.5) * radius * 0.7
        val py = (h01(seed, pool, 331.0) - 0.2) * radius * 0.5
        val pr = radius * (0.16 + h01(seed, pool, 337.0) * 0.12)
        target.moveTo(px + pr, py)
        target.bezierCurveTo(px + pr, py - pr * 0.7, px + pr * 0.4, py - pr, px, py - pr * 0.9)
        target.bezierCurveTo(px - pr * 0.7, py - pr * 0.8, px - pr, py - pr * 0.2, px - pr * 0.9, py + pr * 0.3)
        target.bezierCurveTo(px - pr * 0.6, py + pr * 0.8, px + pr * 0.2, py + pr, px + pr * 0.6, py + pr * 0.7)
        target.bezierCurveTo(px + pr * 0.95, py + pr * 0.5, px + pr, py + pr * 0.3, px + pr, py)
        target.stroke((if (pool % 2.0 == 0.0) primary else secondary), 1.5, 0.45)
        for (pebble in jsFor(0.0, 3.0, 1.0, "<")) {
            target.circle(
                px + (h01(seed, pool * 4.0 + pebble, 347.0) - 0.5) * pr,
                py + (h01(seed, pool * 4.0 + pebble, 349.0) - 0.5) * pr * 0.7,
                1.6 + pebble,
            ).fill(secondary, 0.45)
        }
    }
}

// 66.0: tall seaweed blades swaying from the bottom with drifting air bubbles.
private fun SonnetMgOpts.drawSeaweedSway() {
    for (blade in jsFor(0.0, 6.0, 1.0, "<")) {
        val x = -radius * 0.55 + blade * radius * 0.22 + (h01(seed, blade, 353.0) - 0.5) * radius * 0.06
        val h = radius * (0.4 + h01(seed, blade, 359.0) * 0.3)
        val sway = ((if (blade % 2.0 == 0.0) 1.0 else -1.0)) * radius * 0.12
        target.moveTo(x, radius * 0.62)
            .bezierCurveTo(x + sway, radius * 0.62 - h * 0.4, x - sway, radius * 0.62 - h * 0.7, x + sway * 0.5, radius * 0.62 - h)
            .stroke((if (blade % 2.0 == 0.0) primary else secondary), 2.0, 0.5 - blade * 0.03)
        target.circle(x + sway * 0.5, radius * 0.62 - h, 2.0).fill(secondary, 0.5)
    }
    for (i in jsFor(0.0, 6.0, 1.0, "<")) {
        target.circle(
            (h01(seed, i, 367.0) - 0.5) * radius * 1.1,
            -radius * 0.5 + h01(seed, i, 373.0) * radius * 0.5,
            1.4 + h01(seed, i, 379.0) * 1.6,
        ).stroke(primary, 1.0, 0.35)
    }
}

// 67.0: layered horizontal current lines with scattered fish chevrons.
private fun SonnetMgOpts.drawDeepCurrent() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    for (line in jsFor(0.0, 5.0, 1.0, "<")) {
        val y = -radius * 0.4 + line * radius * 0.2
        val lift = ((if (line % 2.0 == 0.0) 1.0 else -1.0)) * radius * 0.06
        target.moveTo(-bleed.x, y)
            .bezierCurveTo(-radius * 0.3, y + lift, radius * 0.3, y - lift, bleed.x, y)
            .stroke((if (line == 2.0) secondary else primary), (if (line == 2.0) 2.0 else 1.0), 0.32 + (line % 3.0) * 0.07)
    }
    // Fish chevrons swim against the current, offset per seed.
    for (i in jsFor(0.0, 6.0, 1.0, "<")) {
        val x = (h01(seed, i, 383.0) - 0.5) * radius * 1.2
        val y = -radius * 0.3 + h01(seed, i, 389.0) * radius * 0.6
        val s = 5.0 + h01(seed, i, 397.0) * 4.0
        val flip = (if (i % 2.0 == 0.0) 1.0 else -1.0)
        target.moveTo(x - s * flip, y - s * 0.5).lineTo(x, y).lineTo(x - s * flip, y + s * 0.5)
            .stroke(secondary, 1.5, 0.55)
    }
}

// ===== Folia src/components/visualizer/sonnet/sonnetShotMgMusic.ts

// src/components/visualizer/sonnet/sonnetShotMgMusic.ts
// Eight music-themed extended backgrounds (geo variants 68.0-75.0). Staves and
// ribbons stay open-ended (they bleed or dissolve at the edges); notes, forks
// and grooves are split into short commands for layered staggered growth.

// 68.0: symmetric waveform mirrored around an open center axis.
private fun SonnetMgOpts.drawSoundWave() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    val steps = 72.0
    fun envelope(t: Double) = sin(t * PI) * (0.4 + 0.6 * h01(seed, jsRound(t * 12.0), 401.0))
    for (mirror in listOf(-1.0, 1.0)) {
        target.moveTo(-bleed.x, 0.0)
        for (i in jsFor(1.0, steps, 1.0, "<=")) {
            val t = i / steps
            val x = -bleed.x + t * bleed.x * 2.0
            val y = mirror * sin(t * TAU * 5.0 + seed * 0.13) * radius * 0.22 * envelope(t)
            target.lineTo(x, y)
        }
        target.stroke((if (mirror < 0.0) primary else secondary), (if (mirror < 0.0) 2.0 else 1.0), 0.55)
    }
    target.moveTo(-bleed.x, 0.0).lineTo(bleed.x, 0.0).stroke(primary, 1.0, 0.18)
}

// 69.0: vinyl record — grooved arcs with gaps (never closed rings) and a tonearm.
private fun SonnetMgOpts.drawVinylGrooves() {
    for (groove in jsFor(0.0, 7.0, 1.0, "<")) {
        val r = radius * (0.2 + groove * 0.08)
        val gapAt = h01(seed, groove, 409.0) * TAU
        target.arc(0.0, 0.0, r, gapAt, gapAt + TAU * 0.86)
            .stroke((if (groove % 3.0 == 0.0) secondary else primary), (if (groove % 3.0 == 0.0) 2.0 else 1.0), 0.3 + groove * 0.05)
    }
    target.circle(0.0, 0.0, radius * 0.12).stroke(primary, 2.0, 0.6)
    target.circle(0.0, 0.0, radius * 0.03).fill(secondary, 0.8)
    // Tonearm sweeps in from a corner pivot.
    val pivotX = radius * 0.62
    val pivotY = -radius * 0.52
    target.circle(pivotX, pivotY, radius * 0.035).stroke(primary, 2.0, 0.6)
    target.moveTo(pivotX, pivotY)
        .lineTo(radius * 0.18, -radius * 0.1)
        .stroke(primary, 3.0, 0.5)
    target.circle(radius * 0.18, -radius * 0.1, 3.0).fill(secondary, 0.8)
}

// 70.0: equalizer bars blooming along a shallow bottom arc, no baseline frame.
private fun SonnetMgOpts.drawEqualizerBloom() {
    val bars = 17.0
    for (i in jsFor(0.0, bars, 1.0, "<")) {
        val t = i / (bars - 1.0)
        val x = -radius * 0.66 + t * radius * 1.32
        val arcY = radius * 0.5 - sin(t * PI) * radius * 0.12
        val h = radius * (0.08 + sin(t * PI) * 0.3 * (0.5 + h01(seed, i, 419.0) * 0.8))
        target.moveTo(x, arcY).lineTo(x, arcY - h)
            .stroke((if (i % 4.0 == 0.0) secondary else primary), 3.0, 0.5 + sin(t * PI) * 0.25)
        target.circle(x, arcY - h - 4.0, 1.6).fill(secondary, 0.55)
    }
}

// 71.0: five eighth notes stepping along an arc, joined by one open beam.
private fun SonnetMgOpts.drawNoteArc() {
    val headR = radius * 0.045
    val points = ArrayList<MgO>()
    for (i in jsFor(0.0, 5.0, 1.0, "<")) {
        val t = i / 4.0
        val x = -radius * 0.55 + t * radius * 1.1
        val y = radius * 0.22 - sin(t * PI * 0.9) * radius * 0.34
        points.add(MgO(x = x, y = y))
        target.circle(x, y, headR).fill((if (i == 2.0) secondary else primary), 0.8)
        target.moveTo(x + headR, y).lineTo(x + headR, y - radius * 0.16)
            .stroke((if (i == 2.0) secondary else primary), 2.0, 0.65)
    }
    // Beam connecting the stem tops, left open past the last note.
    target.moveTo(points[0].x + headR, points[0].y - radius * 0.16)
    for (i in jsFor(1.0, points.size.toDouble(), 1.0, "<")) {
        target.lineTo(points[i.toInt()].x + headR, points[i.toInt()].y - radius * 0.16)
    }
    target.lineTo(points[4].x + radius * 0.12, points[4].y - radius * 0.13)
    target.stroke(primary, 3.0, 0.5)
    // A stray flag curling off the first note.
    target.moveTo(points[0].x + headR, points[0].y - radius * 0.16)
        .quadraticCurveTo(points[0].x + radius * 0.1, points[0].y - radius * 0.1, points[0].x + radius * 0.06, points[0].y - radius * 0.02)
        .stroke(secondary, 1.5, 0.5)
}

// 72.0: tuning fork with sound rings emanating on both sides.
private fun SonnetMgOpts.drawTuningFork() {
    val fx = ((if (seed % 2.0 == 0.0) -1.0 else 1.0)) * radius * 0.08
    val topY = -radius * 0.4
    val prongW = radius * 0.05
    val prongGap = radius * 0.1
    val uY = radius * 0.02
    // Two prongs + U bend + handle, all one open path broken per segment.
    target.moveTo(fx - prongGap / 2.0 - prongW, topY).lineTo(fx - prongGap / 2.0 - prongW, uY)
        .stroke(primary, 2.5, 0.65)
    target.moveTo(fx + prongGap / 2.0 + prongW, topY).lineTo(fx + prongGap / 2.0 + prongW, uY)
        .stroke(primary, 2.5, 0.65)
    target.arc(fx, uY, prongGap / 2.0 + prongW, 0.0, PI)
        .stroke(primary, 2.5, 0.65)
    target.moveTo(fx, uY + prongGap / 2.0 + prongW).lineTo(fx, radius * 0.42)
        .stroke(primary, 3.0, 0.6)
    target.circle(fx, radius * 0.46, radius * 0.035).stroke(secondary, 2.0, 0.6)
    // Vibration arcs left and right of the prongs.
    for (side in jsFor(-1.0, 1.0, 2.0, "<=")) {
        for (ring in jsFor(0.0, 3.0, 1.0, "<")) {
            val r = radius * (0.14 + ring * 0.1)
            val cx = fx + side * radius * 0.06
            val cy = topY + radius * 0.1
            target.arc(cx, cy, r, (if (side < 0.0) PI * 0.6 else -PI * 0.4), (if (side < 0.0) PI * 1.4 else PI * 0.4))
                .stroke((if (ring == 1.0) secondary else primary), 1.5, 0.42 - ring * 0.1)
        }
    }
}

// 73.0: piano keys riding a shallow ribbon curve — alternating long/short bars.
private fun SonnetMgOpts.drawPianoRibbon() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    // Ribbon guide curve, open at both ends.
    target.moveTo(-bleed.x, radius * 0.18)
        .bezierCurveTo(-radius * 0.3, -radius * 0.05, radius * 0.3, radius * 0.3, bleed.x, radius * 0.05)
        .stroke(primary, 1.0, 0.25)
    val keys = 12.0
    for (i in jsFor(0.0, keys, 1.0, "<")) {
        val t = i / (keys - 1.0)
        val x = -radius * 0.6 + t * radius * 1.2
        val baseY = radius * 0.18 + sin(t * PI) * -radius * 0.1 + t * -radius * 0.06
        val black = (i % 12.0) in listOf(1.0, 3.0, 6.0, 8.0, 10.0)
        val len = radius * ((if (black) 0.14 else 0.24))
        target.moveTo(x, baseY).lineTo(x, baseY - len)
            .stroke((if (black) secondary else primary), (if (black) 4.0 else 3.0), (if (black) 0.7 else 0.45))
    }
}

// 74.0: metronome with tilted pendulum and motion echo arcs.
private fun SonnetMgOpts.drawMetronome() {
    val cx = 0.0
    val baseY = radius * 0.4
    val topY = -radius * 0.36
    // Tapered body outline, base left open.
    target.moveTo(cx - radius * 0.2, baseY).lineTo(cx - radius * 0.06, topY)
        .stroke(primary, 2.0, 0.6)
    target.moveTo(cx + radius * 0.2, baseY).lineTo(cx + radius * 0.06, topY)
        .stroke(primary, 2.0, 0.6)
    target.moveTo(cx - radius * 0.06, topY).lineTo(cx + radius * 0.06, topY)
        .stroke(primary, 2.0, 0.6)
    // Pendulum.
    val tilt = (h01(seed, 0.0, 431.0) - 0.5) * 0.9
    val pivotY = radius * 0.16
    val tipX = cx + sin(tilt) * radius * 0.5
    val tipY = pivotY - cos(tilt) * radius * 0.5
    target.circle(cx, pivotY, radius * 0.03).fill(secondary, 0.85)
    target.moveTo(cx, pivotY).lineTo(tipX, tipY).stroke(secondary, 2.0, 0.7)
    target.rect(tipX - 4.0, tipY - 4.0, 8.0, 8.0).fill(secondary, 0.7)
    // Echo arcs sweeping with the pendulum.
    for (i in jsFor(0.0, 3.0, 1.0, "<")) {
        val r = radius * (0.24 + i * 0.12)
        target.arc(cx, pivotY, r, -PI / 2.0 - 0.5 - i * 0.1, -PI / 2.0 + 0.5 + i * 0.1)
            .stroke(primary, 1.0, 0.3 - i * 0.06)
    }
}

// 75.0: five staff lines undulating across the bleed with a few free notes.
private fun SonnetMgOpts.drawStaffWave() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    for (line in jsFor(0.0, 5.0, 1.0, "<")) {
        val y0 = -radius * 0.16 + line * radius * 0.08
        val lift = ((if (line % 2.0 == 0.0) 1.0 else -1.0)) * radius * 0.05
        target.moveTo(-bleed.x, y0)
            .bezierCurveTo(-radius * 0.3, y0 + lift, radius * 0.3, y0 - lift, bleed.x, y0)
            .stroke(primary, 1.0, 0.3 + ((if (line == 2.0) 0.15 else 0.0)))
    }
    for (i in jsFor(0.0, 4.0, 1.0, "<")) {
        val x = -radius * 0.45 + i * radius * 0.3 + (h01(seed, i, 439.0) - 0.5) * radius * 0.08
        val y = -radius * 0.16 + floor(h01(seed, i, 443.0) * 5.0) * radius * 0.08
        target.circle(x, y, radius * 0.032).fill((if (i % 2.0 == 0.0) secondary else primary), 0.85)
        target.moveTo(x + radius * 0.032, y).lineTo(x + radius * 0.032, y - radius * 0.14)
            .stroke((if (i % 2.0 == 0.0) secondary else primary), 1.5, 0.6)
    }
}

// ===== Folia src/components/visualizer/sonnet/sonnetShotMgCraft.ts

// src/components/visualizer/sonnet/sonnetShotMgCraft.ts
// Ten craft-themed extended backgrounds (geo variants 76.0-85.0): paper, textile
// and knot motifs. Weaves and knots use gaps instead of masks to suggest
// over/under crossings — no clip rectangles anywhere in this range.

// 76.0: faceted origami crane in line art with two lightly filled folds.
private fun SonnetMgOpts.drawOrigamiCrane() {
    val direction = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    val s = radius * 0.34
    // Body diamond.
    target.moveTo(0.0, -s * 0.3).lineTo(direction * s * 0.5, 0.0).lineTo(0.0, s * 0.35).lineTo(-direction * s * 0.5, 0.0)
        .lineTo(0.0, -s * 0.3)
        .stroke(primary, 2.0, 0.6)
    // Raised wing.
    target.moveTo(0.0, -s * 0.3).lineTo(-direction * s * 0.15, -s * 0.95).lineTo(direction * s * 0.28, -s * 0.1)
        .stroke(primary, 1.5, 0.5)
    target.moveTo(0.0, -s * 0.3).lineTo(-direction * s * 0.15, -s * 0.95).lineTo(-direction * s * 0.42, s * 0.02)
        .fill(primary, 0.07)
    // Neck + head.
    target.moveTo(direction * s * 0.5, 0.0)
        .lineTo(direction * s * 0.78, -s * 0.62)
        .lineTo(direction * s * 0.98, -s * 0.5)
        .stroke(secondary, 1.5, 0.6)
    // Tail.
    target.moveTo(-direction * s * 0.5, 0.0).lineTo(-direction * s * 0.85, -s * 0.5)
        .stroke(primary, 1.5, 0.5)
    // Crease lines.
    target.moveTo(0.0, -s * 0.3).lineTo(0.0, s * 0.35).stroke(secondary, 1.0, 0.3)
    target.moveTo(-direction * s * 0.5, 0.0).lineTo(direction * s * 0.5, 0.0)
        .stroke(secondary, 1.0, 0.3)
}

// 77.0: paper plane with a segmented looping trail behind it.
private fun SonnetMgOpts.drawPaperPlaneTrail() {
    val direction = (if (seed % 2.0 == 0.0) 1.0 else -1.0)
    val px = radius * 0.4 * direction
    val py = -radius * 0.28
    val s = radius * 0.16
    // Plane: two folded triangles.
    target.moveTo(px + direction * s, py).lineTo(px - direction * s * 0.8, py - s * 0.55).lineTo(px - direction * s * 0.35, py)
        .lineTo(px + direction * s, py)
        .stroke(primary, 2.0, 0.65)
    target.moveTo(px + direction * s, py).lineTo(px - direction * s * 0.35, py).lineTo(px - direction * s * 0.8, py + s * 0.4)
        .stroke(secondary, 1.5, 0.5)
    // Trail: three arc segments with deliberate gaps.
    for (seg in jsFor(0.0, 3.0, 1.0, "<")) {
        val start = PI * (0.1 + seg * 0.55)
        target.arc(px - direction * radius * 0.35, py + radius * 0.3, radius * (0.34 + seg * 0.06), start, start + PI * 0.4)
            .stroke((if (seg == 1.0) secondary else primary), 1.5, 0.45 - seg * 0.08)
    }
}

// 78.0: woven band — vertical strips pass over/under two horizontals via gaps.
private fun SonnetMgOpts.drawWeaveBand() {
    val bandY = listOf(-radius * 0.12, radius * 0.12)
    val strips = 7.0
    // Horizontal strips first (behind), each broken at every other crossing.
    bandY.forEachIndexed { row_, y -> val row = row_.toDouble()
        for (i in jsFor(0.0, strips, 1.0, "<")) {
            val x0 = -radius * 0.63 + i * radius * 0.18
            if ((i + row) % 2.0 == 0.0) {
                target.moveTo(x0 + radius * 0.02, y).lineTo(x0 + radius * 0.16, y)
                    .stroke((if (row == 0.0) primary else secondary), 5.0, 0.4)
            } else {
                target.moveTo(x0 - radius * 0.05, y).lineTo(x0 + radius * 0.02, y)
                    .stroke((if (row == 0.0) primary else secondary), 5.0, 0.4)
                target.moveTo(x0 + radius * 0.16, y).lineTo(x0 + radius * 0.23, y)
                    .stroke((if (row == 0.0) primary else secondary), 5.0, 0.4)
            }
        }
    }
    // Vertical strips on top at the gapped crossings.
    for (i in jsFor(0.0, strips, 1.0, "<")) {
        val x = -radius * 0.54 + i * radius * 0.18
        val overRow = i % 2.0
        val y = bandY[overRow.toInt()]
        target.moveTo(x, y - radius * 0.05).lineTo(x, y + radius * 0.05)
            .stroke(primary, 6.0, 0.6)
        target.moveTo(x, bandY[(1.0 - overRow).toInt()] - radius * 0.03).lineTo(x, bandY[(1.0 - overRow).toInt()] + radius * 0.03)
            .stroke(secondary, 2.0, 0.3)
    }
}

// 79.0: figure-eight knot drawn in segments with crossing gaps for over/under.
private fun SonnetMgOpts.drawKnotLoop() {
    val s = radius * 0.34
    // Left loop, broken where the right loop passes over.
    target.moveTo(0.0, 0.0)
    target.bezierCurveTo(-s * 0.9, -s * 0.9, -s * 1.5, -s * 0.2, -s * 0.8, s * 0.28)
    target.stroke(primary, 3.0, 0.6)
    target.moveTo(-s * 0.62, s * 0.34)
    target.bezierCurveTo(-s * 0.3, s * 0.44, -s * 0.12, s * 0.2, 0.0, 0.0)
    target.stroke(primary, 3.0, 0.6)
    // Right loop, broken where the left loop passes over.
    target.moveTo(s * 0.12, -s * 0.08)
    target.bezierCurveTo(s * 0.6, -s * 0.6, s * 1.4, -s * 0.3, s * 0.9, s * 0.2)
    target.stroke(secondary, 3.0, 0.6)
    target.moveTo(s * 0.72, s * 0.26)
    target.bezierCurveTo(s * 0.4, s * 0.4, s * 0.05, s * 0.14, -s * 0.06, s * 0.04)
    target.stroke(secondary, 3.0, 0.6)
    // Loose ends drifting out.
    target.moveTo(0.0, 0.0).bezierCurveTo(-s * 0.2, s * 0.5, -s * 0.4, s * 0.8, -s * 0.3, s * 1.1)
        .stroke(primary, 2.0, 0.4)
    target.moveTo(s * 0.06, -s * 0.02).bezierCurveTo(s * 0.3, -s * 0.5, s * 0.5, -s * 0.8, s * 0.42, -s * 1.05)
        .stroke(secondary, 2.0, 0.4)
}

// 80.0: cross-stitch sampler rows that fade toward the edges.
private fun SonnetMgOpts.drawStitchSampler() {
    fun stitch(x: Double, y: Double, size: Double, color: Int, alpha: Double) {
        target.moveTo(x - size, y - size).lineTo(x + size, y + size).stroke(color, 1.5, alpha)
        target.moveTo(x + size, y - size).lineTo(x - size, y + size).stroke(color, 1.5, alpha)
    }
    for (row in jsFor(0.0, 4.0, 1.0, "<")) {
        val y = -radius * 0.36 + row * radius * 0.24
        val count = 7.0 - abs(row - 1.5)
        for (i in jsFor(0.0, count, 1.0, "<")) {
            val x = (i - (count - 1.0) / 2.0) * radius * 0.16 + (row % 2.0) * radius * 0.08
            val edgeFade = 1.0 - abs(i - (count - 1.0) / 2.0) / (count / 2.0 + 0.5)
            stitch(x, y, 4.0 + (row % 2.0), (if ((i + row) % 3.0 == 0.0) secondary else primary), 0.25 + edgeFade * 0.4)
        }
    }
}

// 81.0: folded fan — ribs from a pivot, double guard arc, open at the top.
private fun SonnetMgOpts.drawFoldedFan() {
    val pivotY = radius * 0.42
    val ribs = 11.0
    val spread = PI * 0.9
    for (i in jsFor(0.0, ribs, 1.0, "<")) {
        val angle = -PI / 2.0 - spread / 2.0 + (i / (ribs - 1.0)) * spread
        val len = radius * (0.5 + sin((i / (ribs - 1.0)) * PI) * 0.12)
        target.moveTo(0.0, pivotY)
            .lineTo(cos(angle) * len, pivotY + sin(angle) * len)
            .stroke((if (i % 2.0 == 0.0) primary else secondary), (if (i == 5.0) 2.5 else 1.5), 0.5)
    }
    target.arc(0.0, pivotY, radius * 0.5, -PI / 2.0 - spread / 2.0, -PI / 2.0 + spread / 2.0)
        .stroke(primary, 2.0, 0.45)
    target.arc(0.0, pivotY, radius * 0.58, -PI / 2.0 - spread / 2.0 + 0.06, -PI / 2.0 + spread / 2.0 - 0.06)
        .stroke(secondary, 1.0, 0.3)
    target.circle(0.0, pivotY, radius * 0.03).fill(secondary, 0.8)
}

// 82.0: curling gift ribbon — sampled spiral with a parallel echo stroke.
private fun SonnetMgOpts.drawRibbonCurl() {
    val cx = radius * 0.15 * ((if (seed % 2.0 == 0.0) 1.0 else -1.0))
    val start = h01(seed, 0.0, 449.0) * TAU
    for (echo in listOf(0.0, 1.0)) {
        val offset = echo * radius * 0.035
        target.moveTo(cx + cos(start) * radius * 0.06, -radius * 0.1 + sin(start) * radius * 0.06 + offset)
        val steps = 64.0
        for (i in jsFor(1.0, steps, 1.0, "<=")) {
            val t = i / steps
            val angle = start + t * TAU * 2.4
            val r = radius * (0.06 + t * 0.42)
            target.lineTo(cx + cos(angle) * r, -radius * 0.1 + sin(angle) * r * 0.8 + offset)
        }
        target.stroke((if (echo == 0.0) primary else secondary), (if (echo == 0.0) 3.0 else 1.0), (if (echo == 0.0) 0.55 else 0.3))
    }
    // Loose end flicks upward.
    val endAngle = start + TAU * 2.4
    val ex = cx + cos(endAngle) * radius * 0.48
    val ey = -radius * 0.1 + sin(endAngle) * radius * 0.38
    target.moveTo(ex, ey).quadraticCurveTo(ex + radius * 0.1, ey - radius * 0.12, ex + radius * 0.16, ey - radius * 0.04)
        .stroke(primary, 2.0, 0.5)
}

// 83.0: three overlapping patchwork triangles with hand-built inner stripes.
private fun SonnetMgOpts.drawPatchworkTrio() {
    val configs = listOf(
        MgO(x = -radius * 0.22, y = -radius * 0.05, s = radius * 0.3, up = true),
        MgO(x = radius * 0.18, y = -radius * 0.12, s = radius * 0.24, up = false),
        MgO(x = radius * 0.05, y = radius * 0.2, s = radius * 0.2, up = true),
    )
    configs.forEachIndexed { index_, cfg -> val index = index_.toDouble(); val x = cfg.x; val y = cfg.y; val s = cfg.s; val up = cfg.up
        val topY = (if (up) y - s * 0.6 else y)
        val baseY = (if (up) y + s * 0.4 else y + s)
        target.moveTo(x, topY).lineTo(x + s * 0.55, baseY).lineTo(x - s * 0.55, baseY).lineTo(x, topY)
            .stroke((if (index == 1.0) secondary else primary), 2.0, 0.55)
        // Inner stripes are computed inside the silhouette — no mask needed.
        for (stripe in jsFor(1.0, 3.0, 1.0, "<=")) {
            val t = stripe / 4.0
            val sy = topY + (baseY - topY) * t
            val half = s * 0.55 * ((if (up) t else 1.0 - t))
            target.moveTo(x - half, sy).lineTo(x + half, sy)
                .stroke((if (index == 1.0) primary else secondary), 1.0, 0.35)
        }
    }
}

// 84.0: dreamcatcher — ring, radial web to an off-center hub, hanging feathers.
private fun SonnetMgOpts.drawDreamcatcher() {
    val ringR = radius * 0.32
    val cy = -radius * 0.12
    target.circle(0.0, cy, ringR).stroke(primary, 2.0, 0.6)
    val hubX = radius * 0.05
    val hubY = cy - radius * 0.03
    for (i in jsFor(0.0, 8.0, 1.0, "<")) {
        val angle = (i / 8.0) * TAU + 0.2
        val rimX = cos(angle) * ringR * 0.92
        val rimY = cy + sin(angle) * ringR * 0.92
        target.moveTo(rimX, rimY).lineTo(hubX, hubY)
            .stroke((if (i % 2.0 == 0.0) primary else secondary), 1.0, 0.4)
    }
    target.circle(hubX, hubY, radius * 0.035).stroke(secondary, 1.5, 0.6)
    // Three hanging strings with feather barbs; bottom stays open.
    for (i in jsFor(-1.0, 1.0, 1.0, "<=")) {
        val sx = i * ringR * 0.55
        val topY = cy + sqrt(max(0.0, ringR * ringR - sx * sx))
        val len = radius * (0.22 + (1.0 - abs(i)) * 0.12 + h01(seed, i + 1.0, 457.0) * 0.06)
        target.moveTo(sx, topY).lineTo(sx, topY + len).stroke(primary, 1.0, 0.45)
        val featherY = topY + len
        target.moveTo(sx, featherY).lineTo(sx, featherY + radius * 0.12)
            .stroke(secondary, 1.5, 0.55)
        for (barb in jsFor(1.0, 3.0, 1.0, "<=")) {
            val by = featherY + barb * radius * 0.03
            val bl = radius * 0.04 * (1.0 - barb * 0.18)
            target.moveTo(sx, by).lineTo(sx - bl, by + radius * 0.02)
                .stroke(secondary, 1.0, 0.4)
            target.moveTo(sx, by).lineTo(sx + bl, by + radius * 0.02)
                .stroke(secondary, 1.0, 0.4)
        }
    }
}

// 85.0: tassel curtain — staggered hanging threads with bead tips from a short bar.
private fun SonnetMgOpts.drawTasselDrop() {
    val barY = -radius * 0.4
    target.moveTo(-radius * 0.3, barY).lineTo(radius * 0.3, barY)
        .stroke(primary, 2.0, 0.4)
    val threads = 7.0
    for (i in jsFor(0.0, threads, 1.0, "<")) {
        val x = -radius * 0.27 + i * radius * 0.09
        val len = radius * (0.3 + h01(seed, i, 461.0) * 0.35)
        val sway = (h01(seed, i, 463.0) - 0.5) * radius * 0.08
        target.moveTo(x, barY)
            .quadraticCurveTo(x + sway, barY + len * 0.6, x + sway * 0.6, barY + len)
            .stroke((if (i % 2.0 == 0.0) primary else secondary), 1.5, 0.45)
        target.circle(x + sway * 0.6, barY + len + 3.0, 2.5)
            .fill((if (i % 3.0 == 0.0) secondary else primary), 0.6)
    }
}

// ===== Folia src/components/visualizer/sonnet/sonnetShotMgKinetic.ts

// src/components/visualizer/sonnet/sonnetShotMgKinetic.ts
// Fourteen kinetic/technical extended backgrounds (geo variants 86.0-99.0). Motion
// is implied with phase-staggered static poses (pendulums, dominos, ripples)
// compositions stay open — nothing draws a closed viewport boundary.

// 86.0: pendulum wave — strings and bobs at staggered phases along an arc.
private fun SonnetMgOpts.drawPendulumWave() {
    val pivotY = -radius * 0.42
    val bobs = 9.0
    for (i in jsFor(0.0, bobs, 1.0, "<")) {
        val x = -radius * 0.5 + (i / (bobs - 1.0)) * radius
        val len = radius * (0.4 + i * 0.035)
        val swing = sin(i * 0.9 + seed * 0.07) * 0.35
        val bx = x + sin(swing) * len
        val by = pivotY + cos(swing) * len
        target.moveTo(x, pivotY).lineTo(bx, by)
            .stroke(primary, 1.0, 0.4)
        target.circle(bx, by, 3.5 + (i % 3.0))
            .fill((if (i % 2.0 == 0.0) secondary else primary), 0.7)
    }
    target.moveTo(-radius * 0.58, pivotY).lineTo(radius * 0.58, pivotY)
        .stroke(primary, 2.0, 0.35)
}

// 87.0: dominos toppling along an arc, each rotated a step further.
private fun SonnetMgOpts.drawDominoArc() {
    val count = 10.0
    val arcR = radius * 0.55
    for (i in jsFor(0.0, count, 1.0, "<")) {
        val angle = PI * 1.15 + (i / (count - 1.0)) * PI * 0.7
        val bx = cos(angle) * arcR
        val by = sin(angle) * arcR + radius * 0.5
        val tilt = (i / (count - 1.0)) * 1.1 * ((if (seed % 2.0 == 0.0) 1.0 else -1.0))
        val w = radius * 0.035
        val h = radius * 0.14
        val ct = cos(tilt)
        val st = sin(tilt)
        fun corner(cx: Double, cy: Double) = doubleArrayOf(bx + cx * ct - cy * st, by + cx * st + cy * ct)
        val (x1, y1) = corner(-w, 0.0)
        val (x2, y2) = corner(w, 0.0)
        val (x3, y3) = corner(w, -h * 2.0)
        val (x4, y4) = corner(-w, -h * 2.0)
        target.moveTo(x1, y1).lineTo(x2, y2).lineTo(x3, y3).lineTo(x4, y4).lineTo(x1, y1)
            .stroke((if (i % 3.0 == 0.0) secondary else primary), 1.5, 0.55)
    }
}

// 88.0: three intermeshed gears built from rings and radial teeth.
private fun SonnetMgOpts.drawGearCluster() {
    val gears = listOf(
        MgO(x = 0.0, y = 0.0, r = radius * 0.26, teeth = 10.0),
        MgO(x = radius * 0.42, y = -radius * 0.2, r = radius * 0.16, teeth = 8.0),
        MgO(x = -radius * 0.4, y = radius * 0.22, r = radius * 0.13, teeth = 7.0),
    )
    gears.forEachIndexed { gi_, gear -> val gi = gi_.toDouble()
        val color = (if (gi == 1.0) secondary else primary)
        target.circle(gear.x, gear.y, gear.r).stroke(color, 2.0, 0.55)
        target.circle(gear.x, gear.y, gear.r * 0.3).stroke(color, 1.5, 0.45)
        val offset = h01(seed, gi, 467.0) * TAU
        for (t in jsFor(0.0, gear.teeth, 1.0, "<")) {
            val angle = offset + (t / gear.teeth) * TAU
            target.moveTo(gear.x + cos(angle) * gear.r, gear.y + sin(angle) * gear.r)
                .lineTo(gear.x + cos(angle) * gear.r * 1.18, gear.y + sin(angle) * gear.r * 1.18)
                .stroke(color, 3.0, 0.5)
        }
    }
}

// 89.0: circuit traces with 45.0-degree bends and node pads, no board outline.
private fun SonnetMgOpts.drawCircuitDelta() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    val lanes = 4.0
    for (lane in jsFor(0.0, lanes, 1.0, "<")) {
        val y = -radius * 0.36 + lane * radius * 0.24
        val bendX = -radius * 0.3 + h01(seed, lane, 479.0) * radius * 0.6
        val drop = ((if (lane % 2.0 == 0.0) 1.0 else -1.0)) * radius * 0.08
        target.moveTo(-bleed.x, y)
            .lineTo(bendX - abs(drop), y)
            .lineTo(bendX, y + drop)
            .lineTo(bleed.x, y + drop)
            .stroke((if (lane == 1.0) secondary else primary), 1.5, 0.45)
        target.circle(bendX, y + drop, 3.0).fill(secondary, 0.7)
        target.circle(-bleed.x * 0.55, y, 2.5).stroke(primary, 1.0, 0.5)
    }
}

// 90.0: signal tower mast with radiating wave arcs on both sides.
private fun SonnetMgOpts.drawSignalTower() {
    val baseY = radius * 0.45
    val topY = -radius * 0.3
    target.moveTo(-radius * 0.12, baseY).lineTo(0.0, topY).lineTo(radius * 0.12, baseY)
        .stroke(primary, 2.0, 0.6)
    for (brace in jsFor(1.0, 3.0, 1.0, "<=")) {
        val y = baseY - (baseY - topY) * (brace / 4.0)
        val half = radius * 0.12 * (1.0 - brace / 4.5)
        target.moveTo(-half, y).lineTo(half, y - radius * 0.06)
            .stroke(primary, 1.0, 0.4)
    }
    target.circle(0.0, topY, radius * 0.03).fill(secondary, 0.9)
    for (side in jsFor(-1.0, 1.0, 2.0, "<=")) {
        for (ring in jsFor(0.0, 3.0, 1.0, "<")) {
            val r = radius * (0.12 + ring * 0.13)
            target.arc(0.0, topY, r, (if (side < 0.0) PI * 0.75 else -PI * 0.25), (if (side < 0.0) PI * 1.25 else PI * 0.25))
                .stroke((if (ring == 1.0) secondary else primary), 1.5, 0.45 - ring * 0.1)
        }
    }
}

// 91.0: spiral staircase ascending as staggered tread/riser polylines.
private fun SonnetMgOpts.drawSpiralStair() {
    val steps = 12.0
    val startAngle = h01(seed, 0.0, 487.0) * TAU
    for (i in jsFor(0.0, steps, 1.0, "<")) {
        val angle = startAngle + i * 0.42
        val r = radius * (0.14 + i * 0.04)
        val x = cos(angle) * r
        val y = radius * 0.4 - i * radius * 0.055
        val tread = radius * 0.09
        target.moveTo(x, y)
            .lineTo(x + cos(angle) * tread, y + sin(angle) * tread * 0.4)
            .stroke((if (i % 3.0 == 0.0) secondary else primary), 2.0, 0.55)
        target.moveTo(x + cos(angle) * tread, y + sin(angle) * tread * 0.4)
            .lineTo(x + cos(angle) * tread, y + sin(angle) * tread * 0.4 - radius * 0.055)
            .stroke(primary, 1.0, 0.35)
    }
    // Central spine, open at the top.
    target.moveTo(0.0, radius * 0.45).lineTo(0.0, -radius * 0.35)
        .stroke(primary, 2.0, 0.3)
}

// 92.0: falling vertical streams of staggered length with splash arcs below.
private fun SonnetMgOpts.drawWaterfallLines() {
    val streams = 9.0
    for (i in jsFor(0.0, streams, 1.0, "<")) {
        val x = -radius * 0.5 + (i / (streams - 1.0)) * radius + (h01(seed, i, 491.0) - 0.5) * radius * 0.05
        val topY = -radius * 0.6 + h01(seed, i, 499.0) * radius * 0.15
        val len = radius * (0.55 + h01(seed, i, 503.0) * 0.35)
        target.moveTo(x, topY).lineTo(x, topY + len)
            .stroke((if (i % 3.0 == 0.0) secondary else primary), (if (i % 3.0 == 0.0) 2.0 else 1.0), 0.4 + (i % 3.0) * 0.08)
    }
    for (i in jsFor(0.0, 5.0, 1.0, "<")) {
        val x = -radius * 0.4 + i * radius * 0.2
        target.arc(x, radius * 0.5, radius * 0.07, PI, TAU)
            .stroke(secondary, 1.0, 0.4)
    }
}

// 93.0: four-blade pinwheel with curved sails around a hub.
private fun SonnetMgOpts.drawPinwheel() {
    val hubR = radius * 0.05
    for (blade in jsFor(0.0, 4.0, 1.0, "<")) {
        val angle = (blade / 4.0) * TAU + h01(seed, 0.0, 509.0) * 0.5
        val tipR = radius * 0.5
        val tx = cos(angle) * tipR
        val ty = sin(angle) * tipR
        val edgeAngle = angle + 0.7
        target.moveTo(cos(angle) * hubR, sin(angle) * hubR)
            .quadraticCurveTo(
                cos(edgeAngle) * tipR * 0.55, sin(edgeAngle) * tipR * 0.55,
                tx, ty,
            )
            .stroke((if (blade % 2.0 == 0.0) primary else secondary), 2.0, 0.55)
        target.moveTo(tx, ty)
            .lineTo(cos(angle + 0.45) * tipR * 0.62, sin(angle + 0.45) * tipR * 0.62)
            .stroke((if (blade % 2.0 == 0.0) primary else secondary), 1.5, 0.4)
    }
    target.circle(0.0, 0.0, hubR).fill(secondary, 0.8)
    target.circle(0.0, 0.0, radius * 0.56).stroke(primary, 1.0, 0.15)
}

// 94.0: falling drop above broken ripple arcs — nothing touches the edges.
private fun SonnetMgOpts.drawRippleDrop() {
    val dx = (h01(seed, 0.0, 521.0) - 0.5) * radius * 0.2
    // Teardrop.
    target.moveTo(dx, -radius * 0.52)
    target.bezierCurveTo(dx + radius * 0.07, -radius * 0.36, dx + radius * 0.06, -radius * 0.3, dx, -radius * 0.27)
    target.bezierCurveTo(dx - radius * 0.06, -radius * 0.3, dx - radius * 0.07, -radius * 0.36, dx, -radius * 0.52)
    target.stroke(secondary, 2.0, 0.65)
    // Broken ripples: arc segments with gaps at alternating positions.
    for (ring in jsFor(0.0, 4.0, 1.0, "<")) {
        val r = radius * (0.14 + ring * 0.13)
        val y = radius * 0.25
        val gapAt = h01(seed, ring, 523.0) * TAU
        target.arc(dx, y, r, gapAt, gapAt + TAU * 0.72)
            .stroke((if (ring % 2.0 == 0.0) primary else secondary), (if (ring == 0.0) 2.0 else 1.0), 0.5 - ring * 0.09)
    }
    // Impact crown.
    target.moveTo(dx - radius * 0.05, radius * 0.2).lineTo(dx - radius * 0.02, radius * 0.12)
        .stroke(primary, 1.5, 0.5)
    target.moveTo(dx + radius * 0.05, radius * 0.2).lineTo(dx + radius * 0.02, radius * 0.12)
        .stroke(primary, 1.5, 0.5)
}

// 95.0: suspension bridge — sagging main cable, two towers, open deck line.
private fun SonnetMgOpts.drawSuspensionBridge() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    val deckY = radius * 0.3
    val towerX = radius * 0.34
    val towerTop = -radius * 0.28
    target.moveTo(-bleed.x, deckY).lineTo(bleed.x, deckY)
        .stroke(primary, 2.0, 0.5)
    for (side in listOf(-1.0, 1.0)) {
        val x = side * towerX
        target.moveTo(x - radius * 0.03, deckY).lineTo(x - radius * 0.03, towerTop)
            .stroke(primary, 2.0, 0.55)
        target.moveTo(x + radius * 0.03, deckY).lineTo(x + radius * 0.03, towerTop)
            .stroke(primary, 2.0, 0.55)
        target.moveTo(x - radius * 0.04, towerTop + radius * 0.08).lineTo(x + radius * 0.04, towerTop + radius * 0.08)
            .stroke(secondary, 1.5, 0.45)
    }
    // Main cable: three quadratic spans, ends bleed off-canvas.
    target.moveTo(-bleed.x, deckY - radius * 0.1)
        .quadraticCurveTo(-towerX, towerTop - radius * 0.06, -towerX, towerTop)
        .stroke(secondary, 1.5, 0.5)
    target.moveTo(-towerX, towerTop)
        .quadraticCurveTo(0.0, deckY - radius * 0.04, towerX, towerTop)
        .stroke(secondary, 1.5, 0.5)
    target.moveTo(towerX, towerTop)
        .quadraticCurveTo(bleed.x, deckY - radius * 0.1, bleed.x, deckY - radius * 0.06)
        .stroke(secondary, 1.5, 0.5)
    // Hangers along the center span.
    for (i in jsFor(1.0, 7.0, 1.0, "<")) {
        val t = i / 7.0
        val x = -towerX + t * towerX * 2.0
        val cableY = (1.0 - t) * (1.0 - t) * towerTop + 2.0 * (1.0 - t) * t * (deckY - radius * 0.04) + t * t * towerTop
        target.moveTo(x, cableY).lineTo(x, deckY)
            .stroke(primary, 1.0, 0.35)
    }
}

// 96.0: magnetic field loops through two poles, mirrored top and bottom.
private fun SonnetMgOpts.drawFieldLines() {
    val poleGap = radius * 0.2
    for (loop in jsFor(0.0, 4.0, 1.0, "<")) {
        val bulge = radius * (0.2 + loop * 0.16)
        for (mirror in listOf(-1.0, 1.0)) {
            target.moveTo(0.0, -poleGap)
            target.bezierCurveTo(
                mirror * bulge, -poleGap - radius * 0.1,
                mirror * bulge, poleGap + radius * 0.1,
                0.0, poleGap,
            )
            target.stroke((if (loop % 2.0 == 0.0) primary else secondary), (if (loop == 0.0) 2.0 else 1.0), 0.5 - loop * 0.08)
        }
    }
    target.circle(0.0, -poleGap, radius * 0.04).fill(secondary, 0.85)
    target.circle(0.0, poleGap, radius * 0.04).fill(primary, 0.85)
    target.moveTo(-radius * 0.1, -poleGap).lineTo(radius * 0.1, -poleGap)
        .stroke(secondary, 1.5, 0.5)
    target.moveTo(-radius * 0.1, poleGap).lineTo(radius * 0.1, poleGap)
        .stroke(primary, 1.5, 0.5)
}

// 97.0: prism splitting one inbound beam into a fanned spectrum.
private fun SonnetMgOpts.drawPrismBeam() {
    val bleed = resolveSonnetShotMgBleed(width, height, radius)
    val s = radius * 0.24
    val topY = -s * 0.7
    val baseY = s * 0.55
    target.moveTo(0.0, topY).lineTo(s * 0.8, baseY).lineTo(-s * 0.8, baseY).lineTo(0.0, topY)
        .stroke(primary, 2.0, 0.6)
    target.moveTo(0.0, topY).lineTo(s * 0.8, baseY).lineTo(-s * 0.8, baseY).lineTo(0.0, topY)
        .fill(primary, 0.06)
    // Inbound beam from the left edge.
    val entryX = -s * 0.35
    val entryY = s * 0.05
    target.moveTo(-bleed.x, entryY + radius * 0.12).lineTo(entryX, entryY)
        .stroke(secondary, 2.5, 0.6)
    // Outbound fan to the right edge.
    for (ray in jsFor(0.0, 4.0, 1.0, "<")) {
        val exitY = -radius * 0.1 + ray * radius * 0.09
        target.moveTo(s * 0.4, entryY - radius * 0.05)
            .lineTo(bleed.x, exitY)
            .stroke((if (ray == 1.0) secondary else primary), 1.5, 0.45 - ray * 0.05)
    }
}

// 98.0: echo arcs bouncing between two unseen walls, offset per hop.
private fun SonnetMgOpts.drawEchoArcs() {
    for (hop in jsFor(0.0, 5.0, 1.0, "<")) {
        val side = (if (hop % 2.0 == 0.0) -1.0 else 1.0)
        val cx = side * radius * 0.52
        val cy = -radius * 0.35 + hop * radius * 0.18
        val r = radius * (0.14 + hop * 0.045)
        target.arc(cx, cy, r, (if (side < 0.0) -PI / 2.0 else PI / 2.0), (if (side < 0.0) PI / 2.0 else PI * 1.5))
            .stroke((if (hop % 2.0 == 0.0) primary else secondary), 2.0 - hop * 0.2, 0.55 - hop * 0.07)
        target.circle(cx + ((if (side < 0.0) r else -r)) * 0.4, cy + r * 0.6, 1.8)
            .fill(secondary, 0.5)
    }
}

// 99.0: diamond kite with cross spars, tail bows and a long free string.
private fun SonnetMgOpts.drawKiteString() {
    val kx = radius * 0.22 * ((if (seed % 2.0 == 0.0) 1.0 else -1.0))
    val ky = -radius * 0.3
    val kw = radius * 0.16
    val kh = radius * 0.22
    target.moveTo(kx, ky - kh).lineTo(kx + kw, ky).lineTo(kx, ky + kh).lineTo(kx - kw, ky).lineTo(kx, ky - kh)
        .stroke(primary, 2.0, 0.6)
    target.moveTo(kx, ky - kh).lineTo(kx, ky + kh).stroke(secondary, 1.0, 0.4)
    target.moveTo(kx - kw, ky).lineTo(kx + kw, ky).stroke(secondary, 1.0, 0.4)
    target.moveTo(kx, ky - kh).lineTo(kx + kw, ky).lineTo(kx, ky + kh).lineTo(kx - kw, ky).lineTo(kx, ky - kh)
        .fill(primary, 0.06)
    // String with sampled sag; tail bows are separate commands so the string
    // stays one continuous stroke (and grows in one piece).
    val bows = ArrayList<MgO>()
    target.moveTo(kx, ky + kh)
    val stringSteps = 24.0
    for (i in jsFor(1.0, stringSteps, 1.0, "<=")) {
        val t = i / stringSteps
        val x = kx - t * radius * 0.5 + sin(t * PI * 2.2) * radius * 0.08
        val y = ky + kh + t * radius * 0.6
        target.lineTo(x, y)
        if (i == 7.0 || i == 13.0 || i == 19.0) bows.add(MgO(x = x, y = y))
    }
    target.stroke(primary, 1.5, 0.5)
    bows.forEach { b -> val x = b.x; val y = b.y
        val bowS = radius * 0.035
        target.moveTo(x, y).lineTo(x - bowS, y - bowS).lineTo(x, y - bowS * 0.3).lineTo(x + bowS, y - bowS)
            .lineTo(x, y)
            .stroke(secondary, 1.0, 0.55)
    }
}

private val SONNET_MG_36_99: List<SonnetMgOpts.() -> Unit> = listOf(
    SonnetMgOpts::drawOpenArcBrackets,
    SonnetMgOpts::drawDashedOrbits,
    SonnetMgOpts::drawOpenFragments,
    SonnetMgOpts::drawHorizonBundles,
    SonnetMgOpts::drawSemiWreath,
    SonnetMgOpts::drawSideRulers,
    SonnetMgOpts::drawDiagonalStream,
    SonnetMgOpts::drawCornerPetalSpray,
    SonnetMgOpts::drawDottedWindows,
    SonnetMgOpts::drawOpenRadar,
    SonnetMgOpts::drawBrushStrokes,
    SonnetMgOpts::drawStitchCorners,
    SonnetMgOpts::drawSpiralGalaxy,
    SonnetMgOpts::drawCometTrail,
    SonnetMgOpts::drawEclipseCorona,
    SonnetMgOpts::drawMeteorShower,
    SonnetMgOpts::drawOrbitSatellites,
    SonnetMgOpts::drawAuroraRibbons,
    SonnetMgOpts::drawCrescentHalo,
    SonnetMgOpts::drawNebulaVeil,
    SonnetMgOpts::drawStarMap,
    SonnetMgOpts::drawLunarTide,
    SonnetMgOpts::drawWaveScrolls,
    SonnetMgOpts::drawNautilus,
    SonnetMgOpts::drawCoralBranch,
    SonnetMgOpts::drawLighthouseBeam,
    SonnetMgOpts::drawCompassRose,
    SonnetMgOpts::drawSailRegatta,
    SonnetMgOpts::drawBubbleRise,
    SonnetMgOpts::drawTidePools,
    SonnetMgOpts::drawSeaweedSway,
    SonnetMgOpts::drawDeepCurrent,
    SonnetMgOpts::drawSoundWave,
    SonnetMgOpts::drawVinylGrooves,
    SonnetMgOpts::drawEqualizerBloom,
    SonnetMgOpts::drawNoteArc,
    SonnetMgOpts::drawTuningFork,
    SonnetMgOpts::drawPianoRibbon,
    SonnetMgOpts::drawMetronome,
    SonnetMgOpts::drawStaffWave,
    SonnetMgOpts::drawOrigamiCrane,
    SonnetMgOpts::drawPaperPlaneTrail,
    SonnetMgOpts::drawWeaveBand,
    SonnetMgOpts::drawKnotLoop,
    SonnetMgOpts::drawStitchSampler,
    SonnetMgOpts::drawFoldedFan,
    SonnetMgOpts::drawRibbonCurl,
    SonnetMgOpts::drawPatchworkTrio,
    SonnetMgOpts::drawDreamcatcher,
    SonnetMgOpts::drawTasselDrop,
    SonnetMgOpts::drawPendulumWave,
    SonnetMgOpts::drawDominoArc,
    SonnetMgOpts::drawGearCluster,
    SonnetMgOpts::drawCircuitDelta,
    SonnetMgOpts::drawSignalTower,
    SonnetMgOpts::drawSpiralStair,
    SonnetMgOpts::drawWaterfallLines,
    SonnetMgOpts::drawPinwheel,
    SonnetMgOpts::drawRippleDrop,
    SonnetMgOpts::drawSuspensionBridge,
    SonnetMgOpts::drawFieldLines,
    SonnetMgOpts::drawPrismBeam,
    SonnetMgOpts::drawEchoArcs,
    SonnetMgOpts::drawKiteString,
)

internal const val SONNET_OPEN_GEO_VARIANT_START = 36

/** 36–99 的几何变体（sonnetAdditionalShotMg.ts 末尾：drawOpenSonnetShotMg → drawExtendedSonnetShotMg）；不在范围内返回 false。 */
internal fun drawSonnetMg36to99(target: SonnetMgPen, variant: Int, radius: Double, width: Double, height: Double, seed: Long, primary: Int, secondary: Int): Boolean {
    val drawer = SONNET_MG_36_99.getOrNull(variant - SONNET_OPEN_GEO_VARIANT_START) ?: return false
    SonnetMgOpts(target, radius, width, height, seed.toDouble(), primary, secondary).drawer()
    return true
}
