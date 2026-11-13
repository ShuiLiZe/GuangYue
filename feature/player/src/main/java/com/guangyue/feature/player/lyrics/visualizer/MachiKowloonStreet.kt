// 城寨的街面道具与店招：对译 Folia v0.7.25 machi/scenes/kowloon/street.ts（buildHangingSigns / buildRoofBillboard /
// buildStall / buildPole）、layout.ts（HANGING_SIGNS、ROOF_BILLBOARD、STALL、STALL_TABLE、POLE）、
// kowloonSigns.ts（店名池、霓虹 / 灯箱配色、按行装箱的图集、稳定 + 两组坏灯管）、kowloonSignPaint.ts（paintKowloonSign、
// paintKowloonBarSign、drawCocktail、writeText、resolveKowloonVerticalText）。
// 圆柱 / 斜杆用少段数的棱柱（toon + 墨线下看不出差别）；镬、汤锅、电线（buildCables）、贩卖机与杂物（buildProps）、明渠/沙井已补；铺面（buildShopfronts：卷闸/冰室/药房/暗门/暗巷口日光灯/楼脚电线电表）已补。
// 随机数用自有 mulberry32（kowloon:signs:<seed>），店名与 Folia 同池同规则，不逐块对齐。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal class MachiHangingSign(val axisX: Boolean, val wall: Double, val tip: Double, val y0: Double, val y1: Double, val at: Double, val vertical: Boolean, val role: String)

internal object MachiKowloonStreet {
    val HANGING_SIGNS = listOf(
        MachiHangingSign(true, -3.45, -2.65, 2.95, 4.75, 3.65, true, "artist"),
        MachiHangingSign(true, -3.6, -1.35, 5.15, 5.95, 0.55, false, "shop"),
        MachiHangingSign(true, -3.35, -2.67, 3.3, 5.5, -0.85, true, "shop"),
        MachiHangingSign(true, -3.2, -1.45, 3.45, 4.2, 2.25, false, "shop"),
        MachiHangingSign(true, -3.2, -1.95, 4.45, 5.05, 1.5, false, "shop"),
        MachiHangingSign(true, -3.75, -3.1, 2.6, 3.75, 5.25, true, "shop"),
        MachiHangingSign(false, -2.05, -1.3, 3.4, 5.7, -0.72, true, "shop"),
        MachiHangingSign(false, -1.8, -1.25, 4.0, 5.5, -2.2, true, "shop"),
    )
    const val SIGN_DEPTH = 0.16
    // STALL / STALL_TABLE / POLE
    const val ST_X0 = 4.1; const val ST_X1 = 5.85; const val ST_Z0 = 1.3; const val ST_Z1 = 2.25
    const val ST_COUNTER = 1.0; const val ST_BACK = 2.1; const val ST_CANOPY = 2.35
    const val TABLE_X = 4.85; const val TABLE_Z = 3.25
    const val POLE_X = 5.55; const val POLE_Z = -1.15; const val POLE_H = 7.0; const val POLE_ARM = 1.25; const val POLE_LAMP_Y = 5.05
    val ART = mapOf(
        "signBox" to "#1d1a22", "antenna" to "#2b2f35", "chrome" to "#aab1b9", "stallGreen" to "#2e6a55", "tarpRed" to "#c8423a",
        "tarpWhite" to "#efe9df", "table" to "#d9d3c5", "plastic" to "#d23a3a", "stoolBlue" to "#2f6fc0", "slab" to "#4c4a47",
        "ac" to "#c6c4bb", "tank" to "#557282", "pole" to "#979ca4", "darkMetal" to "#383e49", "wire" to "#11141a",
    )

    /** hangingSignBox：x0,x1,y0,y1,z0,z1。 */
    fun signBox(s: MachiHangingSign, margin: Double = 0.0): DoubleArray {
        val a = min(s.wall, s.tip); val b = max(s.wall, s.tip); val half = SIGN_DEPTH / 2 + margin
        return if (s.axisX) doubleArrayOf(a - margin, b + margin, s.y0 - margin, s.y1 + margin, s.at - half, s.at + half)
        else doubleArrayOf(s.at - half, s.at + half, s.y0 - margin, s.y1 + margin, a - margin, b + margin)
    }

    /** 不铺窗的地方（店招盒子带 0.08 余量、楼顶广告架）。 */
    fun windowAvoid(): List<DoubleArray> =
        HANGING_SIGNS.map { signBox(it, 0.08) } + listOf(doubleArrayOf(-0.05 - 0.3, 2.45 + 0.3, 6.6 - 0.5, 7.5 + 0.3, -2.15 - 0.6, -2.15 + 0.6))
}

/** 一块招牌面：中心、朝向（0 = +z，π/2 = +x）、宽高、写什么。 */
internal class MachiSignPlacement(val x: Double, val y: Double, val z: Double, val rotY: Double, val width: Double, val height: Double, val vertical: Boolean, val role: String)

private fun c(hex: String) = machiRgb(hex)

/** 棱柱（cylinderAt）：底面中心 (x, y, z)，高 h，底半径 r、顶半径 rt。 */
internal fun MachiMesh.prism(x: Double, y: Double, z: Double, r: Double, h: Double, color: FloatArray, segments: Int = 8, rt: Double = r, emissive: Float = 0f) {
    for (i in 0 until segments) {
        val a0 = 2 * PI * i / segments; val a1 = 2 * PI * (i + 1) / segments; val am = (a0 + a1) / 2
        val n = doubleArrayOf(sin(am), 0.0, cos(am))
        quad(doubleArrayOf(x + sin(a0) * r, y, z + cos(a0) * r), doubleArrayOf(x + sin(a1) * r, y, z + cos(a1) * r),
            doubleArrayOf(x + sin(a1) * rt, y + h, z + cos(a1) * rt), doubleArrayOf(x + sin(a0) * rt, y + h, z + cos(a0) * rt), n, color, emissive)
        quad(doubleArrayOf(x, y + h, z), doubleArrayOf(x + sin(a0) * rt, y + h, z + cos(a0) * rt), doubleArrayOf(x + sin(a1) * rt, y + h, z + cos(a1) * rt),
            doubleArrayOf(x, y + h, z), doubleArrayOf(0.0, 1.0, 0.0), color, emissive)
    }
}

/** tubeBetween：两点间的细杆（四棱）。 */
internal fun MachiMesh.tube(a: DoubleArray, b: DoubleArray, r: Double, color: FloatArray) {
    val d = doubleArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
    val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]); if (len < 1e-6) return
    val u = doubleArrayOf(d[0] / len, d[1] / len, d[2] / len)
    val ref = if (abs(u[1]) < 0.9) doubleArrayOf(0.0, 1.0, 0.0) else doubleArrayOf(1.0, 0.0, 0.0)
    fun cross(p: DoubleArray, q: DoubleArray) = doubleArrayOf(p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0])
    fun norm(p: DoubleArray): DoubleArray { val l = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]); return doubleArrayOf(p[0] / l, p[1] / l, p[2] / l) }
    val s = norm(cross(u, ref)); val t = cross(u, s)
    val dirs = listOf(s, t, doubleArrayOf(-s[0], -s[1], -s[2]), doubleArrayOf(-t[0], -t[1], -t[2]))
    for (i in 0 until 4) {
        val p = dirs[i]; val q = dirs[(i + 1) % 4]
        val n = norm(doubleArrayOf(p[0] + q[0], p[1] + q[1], p[2] + q[2]))
        fun at(o: DoubleArray, e: DoubleArray) = doubleArrayOf(o[0] + e[0] * r, o[1] + e[1] * r, o[2] + e[2] * r)
        quad(at(a, p), at(a, q), at(b, q), at(b, p), n, color)
    }
}

/** 街面道具进同一张网格；返回各块招牌面的位置（顺序同 Folia：店招 ×8、楼顶广告架、大排档）。 */
internal fun addMachiKowloonStreet(m: MachiMesh, seed: String = ""): List<MachiSignPlacement> {
    val art = MachiKowloonStreet.ART
    val placements = ArrayList<MachiSignPlacement>()
    // buildHangingSigns
    for (s in MachiKowloonStreet.HANGING_SIGNS) {
        val b = MachiKowloonStreet.signBox(s)
        m.box(b[0], b[1], b[2], b[3], b[4], b[5], c(art.getValue("signBox")), bottom = true)
        val length = abs(s.tip - s.wall); val dir = sign(s.tip - s.wall)
        fun along(o: Double, y: Double) = if (s.axisX) doubleArrayOf(s.wall + dir * o, y, s.at) else doubleArrayOf(s.at, y, s.wall + dir * o)
        m.tube(along(0.0, s.y1 + 0.45), along(length * 0.85, s.y1 + 0.02), 0.015, c(art.getValue("antenna")))
        m.tube(along(0.0, s.y1 + 0.02), along(0.06, s.y1 + 0.02), 0.03, c(art.getValue("antenna")))
        val width = length - 0.08; val height = s.y1 - s.y0 - 0.08
        val mid = (s.wall + s.tip) / 2; val face = MachiKowloonStreet.SIGN_DEPTH / 2 + 0.004
        placements += if (s.axisX) MachiSignPlacement(mid, (s.y0 + s.y1) / 2, s.at + face, 0.0, width, height, s.vertical, s.role)
        else MachiSignPlacement(s.at + face, (s.y0 + s.y1) / 2, mid, PI / 2, width, height, s.vertical, s.role)
    }
    // buildRoofBillboard（B1 楼顶 6.2 + 0.03）
    run {
        val x0 = -0.05; val x1 = 2.45; val z = -2.15; val y0 = 6.6; val y1 = 7.5; val roof = 6.2 + 0.03
        val steel = c(art.getValue("antenna"))
        m.box(x0, x1, y0, y1, z - 0.1, z, c(art.getValue("signBox")), bottom = true)
        for (x in listOf(x0 + 0.25, x1 - 0.25)) {
            m.box(x - 0.04, x + 0.04, roof, y0 + 0.2, z - 0.14, z - 0.1, steel)
            m.tube(doubleArrayOf(x, roof, z - 0.7), doubleArrayOf(x, y0 + 0.1, z - 0.12), 0.02, steel)
        }
        m.box(x0, x1, y0 + 0.05, y0 + 0.1, z - 0.16, z - 0.1, steel)
        placements += MachiSignPlacement((x0 + x1) / 2, (y0 + y1) / 2, z + 0.004, 0.0, x1 - x0 - 0.06, y1 - y0 - 0.06, false, "billboard")
    }
    // buildStall
    run {
        val x0 = MachiKowloonStreet.ST_X0; val x1 = MachiKowloonStreet.ST_X1; val z0 = MachiKowloonStreet.ST_Z0; val z1 = MachiKowloonStreet.ST_Z1
        val counter = MachiKowloonStreet.ST_COUNTER; val back = MachiKowloonStreet.ST_BACK; val canopy = MachiKowloonStreet.ST_CANOPY
        val green = c(art.getValue("stallGreen")); val steel = c(art.getValue("chrome"))
        m.box(x0, x1, 0.0, counter, z0, z1, green)
        m.box(x0 - 0.04, x1 + 0.04, counter, counter + 0.04, z0 - 0.04, z1 + 0.08, steel, bottom = true)
        m.box(x0, x1, counter, back, z0, z0 + 0.06, green)
        m.box(x0, x0 + 0.05, counter, back, z0, z1, green)
        m.box(x1 - 0.05, x1, counter, back, z0, z1, green)
        m.box(x0 + 0.1, x1 - 0.1, 1.55, 1.58, z0 + 0.06, z0 + 0.3, c(art.getValue("slab")))
        val bx0 = x0 + 0.2; val bx1 = x1 - 0.2; val by0 = canopy + 0.12; val by1 = canopy + 0.52
        m.box(bx0, bx1, by0, by1, z0 - 0.1, z0 - 0.02, c(art.getValue("signBox")))
        for (x in listOf(bx0 + 0.15, bx1 - 0.15)) m.box(x - 0.02, x + 0.02, back, by0, z0 - 0.08, z0 - 0.04, steel)
        // 帆布篷：8 条红白，绕 x 轴转 0.2（从后沿往前往下斜）。
        val stripes = 8; val width = x1 - x0 + 0.3; val depth = z1 - z0 + 0.9
        val ox = (x0 + x1) / 2; val oy = canopy; val oz = z0 - 0.1; val ca = cos(0.2); val sa = sin(0.2)
        fun rot(lx: Double, ly: Double, lz: Double) = doubleArrayOf(ox + lx, oy + ly * ca - lz * sa, oz + ly * sa + lz * ca)
        val nUp = doubleArrayOf(0.0, ca, sa)
        for (i in 0 until stripes) {
            val sx0 = -width / 2 + i * (width / stripes); val sx1 = sx0 + width / stripes
            val col = c(art.getValue(if (i % 2 == 1) "tarpWhite" else "tarpRed"))
            m.quad(rot(sx0, 0.0125, depth), rot(sx1, 0.0125, depth), rot(sx1, 0.0125, 0.0), rot(sx0, 0.0125, 0.0), nUp, col)
            m.quad(rot(sx0, -0.0125, depth), rot(sx1, -0.0125, depth), rot(sx1, 0.0125, depth), rot(sx0, 0.0125, depth), doubleArrayOf(0.0, -sa, ca), col)
        }
        val front = z0 - 0.1 + depth * cos(0.2) - 0.06
        val frontY = canopy - depth * sin(0.2)
        for (x in listOf(x0 - 0.1, x1 + 0.1)) m.prism(x, 0.0, front, 0.025, frontY, steel, 6)
        // 灯泡（stallBulb 自发光）
        val bulbX = (x0 + x1) / 2 + 0.2; val bulbY = frontY - 0.35; val bulbZ = (z1 + front) / 2
        m.prism(bulbX, bulbY + 0.05, bulbZ, 0.006, 0.3, c(art.getValue("wire")), 4)
        m.box(bulbX - 0.06, bulbX + 0.06, bulbY - 0.06, bulbY + 0.06, bulbZ - 0.06, bulbZ + 0.06, c("#ffd28a"), 2.6f, bottom = true) // stallBulb level 2.6 × (0.4 + 0.6 lamps)
        // 镬：炉头 + 一只黑镬（倒圆台近似）；汤锅。
        val wx = x0 + 0.5; val wy = counter + 0.04; val wz = (z0 + z1) / 2 + 0.1
        m.prism(wx, wy, wz, 0.2, 0.08, c(art.getValue("darkMetal")), 12)
        m.prism(wx, wy + 0.1, wz, 0.1, 0.12, c(art.getValue("darkMetal")), 12, rt = 0.24)
        m.prism(x1 - 0.45, counter + 0.04, (z0 + z1) / 2, 0.2, 0.36, steel, 12)
        // 折台与四张塑料凳
        val tx = MachiKowloonStreet.TABLE_X; val tz = MachiKowloonStreet.TABLE_Z
        m.box(tx - 0.425, tx + 0.425, 0.7, 0.74, tz - 0.425, tz + 0.425, c(art.getValue("table")), bottom = true)
        for ((dx, dz) in listOf(-0.36 to -0.36, 0.36 to -0.36, -0.36 to 0.36, 0.36 to 0.36)) m.box(tx + dx - 0.015, tx + dx + 0.015, 0.0, 0.7, tz + dz - 0.015, tz + dz + 0.015, steel)
        listOf(-0.68 to 0.1, 0.68 to -0.05, 0.05 to 0.7, -0.1 to -0.68).forEachIndexed { i, (dx, dz) ->
            m.prism(tx + dx, 0.0, tz + dz, 0.15, 0.42, c(art.getValue(if (i % 3 == 2) "stoolBlue" else "plastic")), 10, rt = 0.13)
        }
        placements += MachiSignPlacement((bx0 + bx1) / 2, (by0 + by1) / 2, z0 - 0.016, 0.0, bx1 - bx0 - 0.06, by1 - by0 - 0.06, false, "stall")
    }
    // buildPole
    run {
        val x = MachiKowloonStreet.POLE_X; val z = MachiKowloonStreet.POLE_Z; val h = MachiKowloonStreet.POLE_H
        val steel = c(art.getValue("antenna"))
        m.prism(x, 0.0, z, 0.12, h, c(art.getValue("pole")), 10, rt = 0.085)
        for (y in listOf(h - 0.35, h - 0.8)) {
            m.box(x - 0.05, x + 0.05, y - 0.05, y + 0.05, z - 0.65, z + 0.65, steel, bottom = true)
            for (dz in listOf(-0.55, -0.2, 0.2, 0.55)) m.prism(x, y + 0.05, z + dz, 0.03, 0.09, c(art.getValue("ac")), 6)
        }
        m.prism(x + 0.3, h - 2.2, z, 0.24, 0.62, c(art.getValue("tank")), 12)
        m.box(x + 0.05, x + 0.1, h - 2.0, h - 1.9, z - 0.04, z + 0.04, steel)
        val hx = x - 0.05; val hy = MachiKowloonStreet.POLE_LAMP_Y + 0.12; val hz = z + MachiKowloonStreet.POLE_ARM
        m.tube(doubleArrayOf(x, MachiKowloonStreet.POLE_LAMP_Y - 0.15, z + 0.1), doubleArrayOf(hx, hy + 0.08, hz - 0.2), 0.03, steel)
        m.box(hx - 0.13, hx + 0.13, hy, hy + 0.1, hz - 0.26, hz + 0.26, c(art.getValue("darkMetal")))
        m.box(hx - 0.09, hx + 0.09, hy - 0.02, hy, hz - 0.2, hz + 0.2, c("#e2f2ff"), 3f, bottom = true) // lampHead level 3.0 × (0.15 + 0.85 lamps)
    }
    val rnd = MachiRng(machiHashSeed("kowloon:street:$seed"))
    addMachiKowloonShopfronts(m)
    // buildGround：左排楼脚的明渠 + 路缘、沙井盖。
    for (b in MachiKowloonLayout.BLOCKS.filter { it.name.startsWith("L") }) {
        val z0 = max(b.z0, -1.8); if (b.z1 <= z0) continue
        m.box(b.x1, b.x1 + 0.22, 0.0, MachiKowloonLayout.LEVEL_GROUND + 0.004, z0, b.z1, c("#1d2026"))
        m.box(b.x1 + 0.22, b.x1 + 0.28, 0.0, MachiKowloonLayout.LEVEL_GROUND + 0.05, z0, b.z1, c("#5f6878"))
    }
    m.prism(1.6, MachiKowloonLayout.LEVEL_GROUND, 4.1, 0.32, 0.02, c(art.getValue("darkMetal")), 20)
    // buildCables：杆横担 → 后排楼顶 / B2 楼顶 / 左排楼高处；左排楼面 ↔ 后排楼面在巷子那角交错 11 根（sagWire 16 段抛物下垂）。
    run {
        val wire = c(art.getValue("wire"))
        fun sag(a: DoubleArray, b: DoubleArray, sagM: Double, r: Double) {
            var prev = a
            for (i in 1..16) {
                val t = i / 16.0
                val q = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sagM * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t)
                m.tube(prev, q, r, wire); prev = q
            }
        }
        val x = MachiKowloonStreet.POLE_X; val z = MachiKowloonStreet.POLE_Z; val h = MachiKowloonStreet.POLE_H
        val anchors = listOf(h - 0.35, h - 0.8).flatMap { y -> listOf(-0.55, -0.2, 0.2, 0.55).map { dz -> doubleArrayOf(x, y + 0.12, z + dz) } }
        val bl = MachiKowloonLayout.BLOCKS
        val b1 = bl.first { it.name == "B1" }; val b2 = bl.first { it.name == "B2" }; val l1 = bl.first { it.name == "L1" }
        anchors.take(4).forEachIndexed { i, a ->
            val to = if (i < 2) doubleArrayOf(3.4 + i * 0.5, b1.height + 0.2, b1.z1 - 0.3 - i * 0.4) else doubleArrayOf(b2.x0 + 0.4 + i * 0.2, b2.height + 0.15, b2.z1 - 0.2)
            sag(a, to, 0.15 + rnd.next() * 0.15, 0.013)
        }
        anchors.drop(4).forEachIndexed { i, a -> sag(a, doubleArrayOf(l1.x1, l1.height - 0.25 - i * 0.12, l1.z0 + 0.4 + i * 0.25), 0.3 + rnd.next() * 0.2, 0.013) }
        fun leftFaceAt(zz: Double) = bl.firstOrNull { it.name.startsWith("L") && zz >= it.z0 && zz < it.z1 }?.x1 ?: -3.4
        repeat(11) {
            val zz = -1.6 + rnd.next() * 4.4
            val from = doubleArrayOf(leftFaceAt(zz), 3.3 + rnd.next() * 2.8, zz)
            val toX = -2.9 + rnd.next() * 2.5
            val to = doubleArrayOf(toX, 3.3 + rnd.next() * 2.7, if (toX < -1.6) -1.8 else -2.05)
            sag(from, to, 0.12 + rnd.next() * 0.35, 0.011)
        }
    }
    // buildProps：贩卖机（发光正面）、木箱一摞、垃圾袋、石油气罐、两架电单车、手推车、蓝胶箱。
    run {
        val vx = 4.95; val vz = -1.95
        m.box(vx - 0.34, vx + 0.34, 0.0, 1.8, vz - 0.32, vz + 0.28, c("#2f5f9e"))
        m.box(vx - 0.27, vx + 0.27, 0.75, 1.65, vz + 0.28, vz + 0.3, c("#d4ebff"), 0.75f)
        m.box(vx - 0.24, vx + 0.24, 0.18, 0.4, vz + 0.28, vz + 0.31, c("#1c222b"))
        val crate = c("#8a6a45")
        for ((cx, cz, cy) in listOf(Triple(-3.45, 5.75, 0.0), Triple(-3.45, 5.75, 0.36), Triple(-3.0, 5.6, 0.0))) m.box(cx - 0.25, cx + 0.25, cy, cy + 0.36, cz - 0.21, cz + 0.21, crate)
        for (i in 0 until 4) {
            val sy = 0.75 + rnd.next() * 0.2; val bx = -3.05 + rnd.next() * 0.25; val bz = -0.75 + i * 0.24 + rnd.next() * 0.1
            m.prism(bx, 0.16 - 0.22 * sy, bz, 0.2, 0.44 * sy, c("#22282a"), 8, rt = 0.12)
        }
        for (cz in listOf(4.25, 3.95)) m.prism(-3.1, MachiKowloonLayout.LEVEL_GROUND, cz, 0.13, 0.62, c("#c9452f"), 10)
        // 电单车：车头朝 +x 再转约 90°（这里取正 90°，车头朝 -z；Folia 另加 ±0.15 rad 随机偏）。
        for ((sx, sz, col) in listOf(Triple(-2.75, 4.75, "#c43c3c"), Triple(-2.75, 5.35, "#e3dfd2"))) {
            rnd.next()
            fun sb(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double, k: FloatArray) = m.box(sx + z0, sx + z1, y0, y1, sz - x1, sz - x0, k)
            val body = c(col); val dark = c("#1c1f26")
            sb(-0.42, 0.05, 0.22, 0.55, -0.16, 0.16, body); sb(0.05, 0.42, 0.18, 0.26, -0.13, 0.13, body); sb(0.38, 0.48, 0.2, 0.85, -0.17, 0.17, body)
            sb(-0.4, 0.02, 0.55, 0.64, -0.13, 0.13, dark); sb(0.45, 0.5, 0.85, 0.9, -0.24, 0.24, c(art.getValue("chrome")))
            for (wx in listOf(-0.38, 0.5)) sb(wx - 0.16, wx + 0.16, 0.0, 0.32, -0.04, 0.04, dark)
        }
        m.box(2.75, 3.75, 0.55, 0.61, 3.25, 3.85, crate)
        m.box(2.75, 3.75, 0.61, 0.79, 3.25, 3.29, crate); m.box(2.75, 3.75, 0.61, 0.79, 3.81, 3.85, crate)
        m.tube(doubleArrayOf(2.75, 0.62, 3.3), doubleArrayOf(2.35, 0.8, 3.3), 0.025, crate); m.tube(doubleArrayOf(2.75, 0.62, 3.8), doubleArrayOf(2.35, 0.8, 3.8), 0.025, crate)
        for (wz in listOf(3.22, 3.88)) m.box(3.23, 3.67, 0.02, 0.46, wz - 0.025, wz + 0.025, c("#1c1f26"))
        m.box(2.83, 2.87, 0.24, 0.56, 3.53, 3.57, crate)
        for ((cx, cz, cy) in listOf(Triple(5.55, 2.8, 0.0), Triple(5.55, 2.8, 0.3), Triple(5.15, 2.75, 0.0))) m.box(cx - 0.21, cx + 0.21, cy, cy + 0.3, cz - 0.16, cz + 0.16, c("#2f6fb0"))
    }
    // 全息投影机（street.ts buildProps：holoBase 方盒 + holoLens 发光圆片）
    m.box(0.9 - 0.23, 0.9 + 0.23, 0.0, 0.24, 2.3 - 0.23, 2.3 + 0.23, c("#2b2f36"))
    m.prism(0.9, 0.24, 2.3, 0.13, 0.03, c("#62f2ff"), 14, emissive = 2.2f) // holoLens level 2.2 × (0.4 + 0.6 night)
    return placements
}

// —— 店招图集（kowloonSigns.ts）————————————————————————————————————

private val VERTICAL_SHOPS = listOf(
    "義體診所" to "CYBER CLINIC", "夜總會" to "NIGHT CLUB", "麻雀館" to null, "跌打" to null, "大押" to null, "旅館" to "HOTEL",
    "牙科" to "DENTAL", "電腦維修" to null, "髮廊" to null, "找換店" to null, "義肢" to null, "桑拿" to null,
)
private val HORIZONTAL_SHOPS = listOf("冰室", "魚蛋粉", "卡拉OK", "當舖", "網吧", "雀館", "燒臘", "涼茶", "電子煙", "記憶交易")
private val NEON_COLORS = listOf("#ff4fb0", "#3fe8ff", "#ffb547", "#57ff9a", "#ff5a4a", "#b38bff")
private val BOX_STYLES = listOf("#f4efe2" to "#c8282c", "#ffd23f" to "#1c1c1c", "#d8262c" to "#fff4d6", "#1f6fd1" to "#ffffff", "#18a058" to "#ffffff")
private const val ATLAS_WIDTH = 2048
private const val ATLAS_PAD = 6
private const val NEON_BACKGROUND = 0xFF130f19.toInt()

internal sealed class KowloonSignStyle {
    class Neon(val color: String, val border: String) : KowloonSignStyle()
    class Box(val background: String, val ink: String) : KowloonSignStyle()
}

private fun argb(hex: String): Int = machiRgb(hex).let { c -> (0xFF shl 24) or ((c[0] * 255).roundToInt() shl 16) or ((c[1] * 255).roundToInt() shl 8) or (c[2] * 255).roundToInt() }

private val CJK_RE = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")

/** resolveKowloonVerticalText：中日韩或 ≤7 个字母竖排（>8 截 7 + …），否则转 90° 横写（返回 null）。 */
internal fun resolveKowloonVerticalGlyphs(text: String): List<String>? {
    val compact = text.replace(Regex("\\s+"), "")
    val glyphs = ArrayList<String>()
    var i = 0
    while (i < compact.length) { val cp = compact.codePointAt(i); glyphs += String(Character.toChars(cp)); i += Character.charCount(cp) }
    if (glyphs.isEmpty()) return glyphs
    if (CJK_RE.containsMatchIn(compact) || glyphs.size <= 7) return if (glyphs.size > 8) glyphs.take(7) + "…" else glyphs
    return null
}

private fun midline(p: Paint) = p.fontMetrics.let { -(it.ascent + it.descent) / 2 }

/** writeText：霓虹 = 填色带光晕 + strokeNeonText；灯箱 = 实心粗字。 */
private fun writeText(cv: Canvas, p: Paint, text: String, cx: Float, cy: Float, px: Float, style: KowloonSignStyle) {
    val y = cy + midline(p)
    when (style) {
        is KowloonSignStyle.Neon -> {
            val col = argb(style.color)
            p.style = Paint.Style.FILL; p.color = col
            p.setShadowLayer(px * 0.125f, 0f, 0f, col)
            cv.drawText(text, cx, y, p)
            p.clearShadowLayer()
            val glow = px * 0.07f
            p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND
            p.setShadowLayer(glow / 2, 0f, 0f, col); p.strokeWidth = glow * 0.45f; cv.drawText(text, cx, y, p)
            p.setShadowLayer(glow * 0.2f, 0f, 0f, col); p.color = android.graphics.Color.WHITE; p.strokeWidth = glow * 0.14f; cv.drawText(text, cx, y, p)
            p.clearShadowLayer(); p.style = Paint.Style.FILL
        }
        is KowloonSignStyle.Box -> { p.style = Paint.Style.FILL; p.color = argb(style.ink); cv.drawText(text, cx, y, p) }
    }
}

private fun fitPx(p: Paint, text: String, maxWidth: Float, maxPx: Float, minPx: Float = 8f): Float {
    p.textSize = maxPx
    val w = p.measureText(text)
    val px = if (w > maxWidth) max(minPx, maxPx * maxWidth / w) else maxPx
    p.textSize = px
    return px
}

private fun paintPanel(cv: Canvas, r: RectF, style: KowloonSignStyle) {
    val unit = min(r.width(), r.height())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    when (style) {
        is KowloonSignStyle.Neon -> {
            p.color = NEON_BACKGROUND; cv.drawRect(r, p)
            p.style = Paint.Style.STROKE; p.color = argb(style.border); p.strokeWidth = max(2f, unit * 0.025f)
            p.setShadowLayer(unit * 0.025f, 0f, 0f, argb(style.border))
            val inset = unit * 0.06f
            cv.drawRoundRect(RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), unit * 0.06f, unit * 0.06f, p)
        }
        is KowloonSignStyle.Box -> {
            p.color = argb(style.background); cv.drawRect(r, p)
            p.color = 0x29000000; cv.drawRect(r, p)
            p.style = Paint.Style.STROKE; p.color = argb(style.ink); p.alpha = (0.55 * 255).roundToInt(); p.strokeWidth = max(2f, unit * 0.02f)
            val inset = unit * 0.05f
            cv.drawRoundRect(RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), unit * 0.04f, unit * 0.04f, p)
        }
    }
}

/** paintKowloonSign。 */
internal fun paintKowloonSign(cv: Canvas, r: RectF, text: String, vertical: Boolean, style: KowloonSignStyle, caption: String?, heavy: Typeface, bold: Typeface) {
    cv.save(); cv.clipRect(r)
    paintPanel(cv, r, style)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = heavy }
    val x = r.left; val y = r.top; val width = r.width(); val height = r.height()
    val captionHeight = if (caption != null) min(width, height) * 0.22f else 0f
    if (vertical) {
        val glyphs = resolveKowloonVerticalGlyphs(text)
        val top = y + height * 0.07f
        val usable = height * 0.86f - captionHeight
        if (glyphs != null) {
            val step = usable / max(1, glyphs.size)
            val px = min(width * 0.7f, step * 0.86f)
            p.textSize = px
            glyphs.forEachIndexed { i, g -> writeText(cv, p, g, x + width / 2, top + step * (i + 0.5f), px, style) }
        } else {
            cv.save(); cv.translate(x + width / 2, top + usable / 2); cv.rotate(90f)
            val px = fitPx(p, text.trim(), usable * 0.94f, width * 0.62f)
            writeText(cv, p, text.trim(), 0f, 0f, px, style)
            cv.restore()
        }
        if (caption != null) {
            p.typeface = bold
            val px = fitPx(p, caption, width * 0.84f, captionHeight * 0.7f)
            writeText(cv, p, caption, x + width / 2, y + height * 0.93f - captionHeight / 2, px, style)
        }
    } else {
        val px = fitPx(p, text, width * 0.84f, height * 0.66f)
        writeText(cv, p, text, x + width / 2, y + height * 0.53f, px, style)
    }
    cv.restore()
}

/** drawCocktail。 */
private fun drawCocktail(cv: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = color; strokeWidth = size * 0.07f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        setShadowLayer(size * 0.09f, 0f, 0f, color)
    }
    val top = cy - size * 0.42f
    val path = Path().apply {
        moveTo(cx - size * 0.38f, top); lineTo(cx + size * 0.38f, top); lineTo(cx, cy + size * 0.05f); close()
        moveTo(cx, cy + size * 0.05f); lineTo(cx, cy + size * 0.38f)
        moveTo(cx - size * 0.2f, cy + size * 0.4f); lineTo(cx + size * 0.2f, cy + size * 0.4f)
    }
    cv.drawPath(path, p)
    cv.drawCircle(cx + size * 0.08f, top + size * 0.14f, size * 0.07f, p)
}

/** paintKowloonBarSign。 */
internal fun paintKowloonBarSignAt(cv: Canvas, r: RectF, title: String, neon: String, heavy: Typeface, regular: Typeface) {
    val x = r.left; val y = r.top; val width = r.width(); val height = r.height()
    cv.save(); cv.clipRect(r)
    cv.drawRect(r, Paint().apply { color = NEON_BACKGROUND })
    drawCocktail(cv, x + height * 0.6f, y + height * 0.5f, height * 0.8f, argb("#3fe8ff"))
    val markWidth = height * 0.7f
    val markX = x + width - markWidth * 0.75f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = heavy }
    val markPx = height * 0.36f
    p.textSize = markPx
    val mark = KowloonSignStyle.Neon("#ff4fb0", "#ff4fb0")
    writeText(cv, p, "酒", markX, y + height * 0.29f, markPx, mark)
    writeText(cv, p, "吧", markX, y + height * 0.71f, markPx, mark)
    val left = x + height * 1.15f
    val right = markX - markWidth * 0.55f
    p.typeface = regular
    val px = fitPx(p, title, right - left, height * 0.6f)
    writeText(cv, p, title, (left + right) / 2, y + height * 0.54f, px, KowloonSignStyle.Neon(neon, neon))
    cv.restore()
}

/** 图集里的一格：面（世界）、在图集里的矩形、哪组灯管（0 稳定、1/2 会坏）。 */
internal class MachiAtlasEntry(val placement: MachiSignPlacement, val group: Int, val fine: Boolean, val paint: (Canvas, RectF) -> Unit) {
    var rect = RectF()
}

internal class MachiSignAtlas(val bitmap: Bitmap, val entries: List<MachiAtlasEntry>) {
    /** 按组排好的三角形（pos3 + uv2），groupStart / groupCount 以顶点计。 */
    val mesh: FloatArray
    val groupStart = IntArray(3)
    val groupCount = IntArray(3)

    init {
        val out = ArrayList<Float>()
        val h = bitmap.height.toFloat()
        for (g in 0..2) {
            groupStart[g] = out.size / 5
            for (e in entries.filter { it.group == g }) {
                val p = e.placement
                val u0 = e.rect.left / ATLAS_WIDTH; val u1 = e.rect.right / ATLAS_WIDTH
                val vTop = e.rect.top / h; val vBottom = e.rect.bottom / h // Bitmap 自上而下
                val ax = cos(p.rotY); val az = -sin(p.rotY) // 面内 +x 方向（绕 y 转 rotY）
                fun pt(sx: Double, sy: Double, u: Float, v: Float) {
                    out += (p.x + ax * sx).toFloat(); out += (p.y + sy).toFloat(); out += (p.z + az * sx).toFloat(); out += u; out += v
                }
                val hw = p.width / 2; val hh = p.height / 2
                // shader 里 v 翻转（1 - v），这里存 three 的 v：底 = 1 - bottom/h
                pt(-hw, -hh, u0, 1 - vBottom); pt(hw, -hh, u1, 1 - vBottom); pt(hw, hh, u1, 1 - vTop)
                pt(-hw, -hh, u0, 1 - vBottom); pt(hw, hh, u1, 1 - vTop); pt(-hw, hh, u0, 1 - vTop)
            }
            groupCount[g] = out.size / 5 - groupStart[g]
        }
        mesh = out.toFloatArray()
    }
}

/** 按 seed 给每块店招挑店名、写法、颜色、坏不坏，按行装箱画进一张图集（kowloonSigns.ts 构造函数 + setContent）。 */
internal fun buildMachiSignAtlas(placements: List<MachiSignPlacement>, barSign: MachiSignPlacement, seed: String, title: String, artist: String, neon: String, brand0: String): MachiSignAtlas {
    val rnd = MachiRng(machiHashSeed("kowloon:signs:$seed"))
    val verticalPool = VERTICAL_SHOPS.toMutableList(); val horizontalPool = HORIZONTAL_SHOPS.toMutableList()
    fun <T> take(pool: MutableList<T>) = pool.removeAt((rnd.next() * pool.size).toInt().coerceIn(0, pool.size - 1))
    fun neonStyle(): KowloonSignStyle { val col = NEON_COLORS[(rnd.next() * NEON_COLORS.size).toInt()]; return KowloonSignStyle.Neon(col, if (rnd.next() < 0.5) col else NEON_COLORS[(rnd.next() * NEON_COLORS.size).toInt()]) }
    fun boxStyle(): KowloonSignStyle { val (bg, ink) = BOX_STYLES[(rnd.next() * BOX_STYLES.size).toInt()]; return KowloonSignStyle.Box(bg, ink) }
    val heavy = machiTypeface(800); val bold = machiTypeface(700); val regular = machiTypeface(700)
    val entries = ArrayList<MachiAtlasEntry>()
    entries += MachiAtlasEntry(barSign, 0, true) { cv, r -> paintKowloonBarSignAt(cv, r, title.ifEmpty { "KOWLOON" }, neon, heavy, regular) }
    for (b in placements) {
        if (b.role == "artist") {
            val bg = "#f4efe2"
            val ink = mcAdjustLightnessForContrast(machiVivid(brand0, 0.62, 0.3, 0.46), bg, 4.5)
            entries += MachiAtlasEntry(b, 0, true) { cv, r -> paintKowloonSign(cv, r, artist.ifEmpty { title.ifEmpty { "歌手" } }, true, KowloonSignStyle.Box(bg, ink), null, heavy, bold) }
            continue
        }
        var caption: String? = null
        val text: String; val style: KowloonSignStyle
        when {
            b.role == "billboard" -> { text = "九龍城寨"; style = KowloonSignStyle.Neon("#ff4a5a", "#3fe8ff") }
            b.role == "stall" -> { text = "大牌檔"; style = KowloonSignStyle.Box("#f3ead6", "#c6282c") }
            b.vertical -> { val (t, cap) = take(verticalPool); text = t; caption = cap; style = if (rnd.next() < 0.55) neonStyle() else boxStyle() }
            else -> { text = take(horizontalPool); style = if (rnd.next() < 0.6) neonStyle() else boxStyle() }
        }
        val group = if (b.role == "shop" && style is KowloonSignStyle.Neon && rnd.next() < 0.45) 1 + (rnd.next() * 2).toInt() else 0
        val cap = caption
        entries += MachiAtlasEntry(b, group, b.role == "billboard") { cv, r -> paintKowloonSign(cv, r, text, b.vertical, style, cap, heavy, bold) }
    }
    // packAtlas：从高到矮，按行装。
    val sizes = entries.map { e -> val d = if (e.fine) 300 else 220; (e.placement.width * d).roundToInt() to (e.placement.height * d).roundToInt() }
    val order = sizes.indices.sortedByDescending { sizes[it].second }
    var x = ATLAS_PAD; var y = ATLAS_PAD; var row = 0
    for (i in order) {
        val (w, h) = sizes[i]
        if (x + w + ATLAS_PAD > ATLAS_WIDTH) { x = ATLAS_PAD; y += row + ATLAS_PAD; row = 0 }
        entries[i].rect = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        x += w + ATLAS_PAD; row = max(row, h)
    }
    val height = (ceil((y + row + ATLAS_PAD) / 4.0) * 4).toInt()
    val bmp = Bitmap.createBitmap(ATLAS_WIDTH, height, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    entries.forEach { it.paint(cv, it.rect) }
    return MachiSignAtlas(bmp, entries)
}

/**
 * 楼顶（blocks.ts）：油毡面、一圈女儿墙，按 0.95 m 格子撒水箱（四脚架 + 圆桶）、铁皮小屋、鱼骨天线、锅盖、冷气机，
 * 碰到更高的楼、楼顶广告架就不放。随机数自有（kowloon:roof:<seed>），不与 Folia 逐件对齐；锅盖不斜（平碟近似）。
 */
internal fun addMachiKowloonRoofs(m: MachiMesh, seed: String) {
    val rnd = MachiRng(machiHashSeed("kowloon:roof:$seed"))
    val tar = c("#3a393b"); val slab = c("#4c4a47"); val tank = c("#557282"); val metal = c("#2b2f35")
    val ac = c("#c6c4bb"); val grille = c("#55595e"); val windowDark = c("#1c222b")
    val billboard = doubleArrayOf(-0.05 - 0.3, 2.45 + 0.3, 6.6 - 0.5, 7.5 + 0.3, -2.15 - 0.6, -2.15 + 0.6)
    fun insideBlock(x: Double, y: Double, z: Double) = MachiKowloonLayout.BLOCKS.any { b ->
        x > b.x0 + 0.01 && x < b.x1 - 0.01 && z > b.z0 + 0.01 && z < b.z1 - 0.01 && y > 0.01 && y < b.height - 0.01
    }
    for (b in MachiKowloonLayout.BLOCKS) {
        val top0 = b.height
        m.box(b.x0 + 0.06, b.x1 - 0.06, top0, top0 + 0.03, b.z0 + 0.06, b.z1 - 0.06, tar)
        m.box(b.x0, b.x1, top0, top0 + 0.26, b.z1 - 0.08, b.z1, slab)
        m.box(b.x0, b.x1, top0, top0 + 0.26, b.z0, b.z0 + 0.08, slab)
        m.box(b.x0, b.x0 + 0.08, top0, top0 + 0.26, b.z0, b.z1, slab)
        m.box(b.x1 - 0.08, b.x1, top0, top0 + 0.26, b.z0, b.z1, slab)
        val top = top0 + 0.03
        fun fits(x: Double, z: Double, r: Double, h: Double): Boolean = listOf(x - r to z - r, x + r to z - r, x - r to z + r, x + r to z + r).all { (cx, cz) ->
            if (cx < b.x0 + 0.15 || cx > b.x1 - 0.15 || cz < b.z0 + 0.15 || cz > b.z1 - 0.15) return@all false
            val y = top + h * 0.5
            !insideBlock(cx, y, cz) && !(cx in billboard[0]..billboard[1] && y in billboard[2]..billboard[3] && cz in billboard[4]..billboard[5])
        }
        var x = b.x0 + 0.6
        while (x < b.x1 - 0.4) {
            var z = b.z0 + 0.6
            while (z < b.z1 - 0.4) {
                val px = x + (rnd.next() - 0.5) * 0.4; val pz = z + (rnd.next() - 0.5) * 0.4
                val roll = rnd.next()
                when {
                    roll < 0.22 && fits(px, pz, 0.4, 1.0) -> {
                        for ((dx, dz) in listOf(-0.22 to -0.22, 0.22 to -0.22, -0.22 to 0.22, 0.22 to 0.22)) m.box(px + dx - 0.025, px + dx + 0.025, top, top + 0.36, pz + dz - 0.025, pz + dz + 0.025, metal)
                        m.prism(px, top + 0.36, pz, 0.33, 0.6, tank, 14)
                    }
                    roll < 0.36 && fits(px, pz, 0.55, 1.0) -> {
                        m.box(px - 0.5, px + 0.5, top, top + 0.85, pz - 0.425, pz + 0.425, c(if (rnd.next() < 0.5) "#857a6b" else "#8d9095"))
                        m.box(px - 0.56, px + 0.56, top + 0.85, top + 0.9, pz - 0.49, pz + 0.49, tar, bottom = true)
                        m.box(px + 0.2 - 0.16, px + 0.2 + 0.16, top, top + 0.62, pz + 0.41, pz + 0.45, windowDark)
                    }
                    roll < 0.68 && fits(px, pz, 0.32, 1.4) -> {
                        val height = 0.9 + rnd.next() * 0.6
                        m.prism(px, top, pz, 0.018, height, metal, 5)
                        val yaw = rnd.next() * PI
                        listOf(0.55, 0.75, 0.92).forEachIndexed { i, t ->
                            val half = (0.62 - i * 0.12) / 2; val y = top + height * t
                            m.tube(doubleArrayOf(px - cos(yaw) * half, y, pz + sin(yaw) * half), doubleArrayOf(px + cos(yaw) * half, y, pz - sin(yaw) * half), 0.008, metal)
                        }
                    }
                    roll < 0.76 && fits(px, pz, 0.3, 0.8) -> {
                        m.prism(px, top, pz, 0.025, 0.35, metal, 5)
                        m.prism(px, top + 0.35, pz, 0.08, 0.1, ac, 12, rt = 0.26)
                        rnd.next(); rnd.next()
                    }
                    roll < 0.86 && fits(px, pz, 0.36, 0.6) -> {
                        m.box(px - 0.31, px + 0.31, top, top + 0.48, pz - 0.24, pz + 0.24, ac)
                        m.box(px - 0.2, px + 0.2, top + 0.48, top + 0.52, pz - 0.2, pz + 0.2, grille)
                    }
                }
                z += 0.95
            }
            x += 0.95
        }
    }
}

/** street.ts buildShopfronts 照译：各家铺面贴在楼面上的卷闸、玻璃、灯箱。 */
internal fun addMachiKowloonShopfronts(m: MachiMesh) {
    val shutter = c("#8d9095"); val shutterDark = c("#62656a"); val frame = c("#383c43"); val dark = c("#1c222b")
    fun face(name: String): Double { val b = MachiKowloonLayout.BLOCKS.first { it.name == name }; return if (name.startsWith("L")) b.x1 else b.z1 }
    fun shutterX(x0: Double, x1: Double, z: Double, top: Double, open: Double = 0.0) {
        m.box(x0 - 0.05, x1 + 0.05, top, top + 0.28, z, z + 0.2, shutterDark, bottom = true)
        var y = open
        while (y < top - 0.05) { m.box(x0, x1, y, min(top, y + 0.15), z, z + 0.04, if (y % 0.32 < 0.16) shutter else shutterDark); y += 0.16 }
    }
    fun shutterZ(z0: Double, z1: Double, x: Double, top: Double, open: Double = 0.0) {
        m.box(x, x + 0.2, top, top + 0.28, z0 - 0.05, z1 + 0.05, shutterDark, bottom = true)
        var y = open
        while (y < top - 0.05) { m.box(x, x + 0.04, y, min(top, y + 0.15), z0, z1, if (y % 0.32 < 0.16) shutter else shutterDark); y += 0.16 }
    }
    fun doorZ(z0: Double, z1: Double, x: Double, top: Double = 2.3) {
        m.box(x, x + 0.03, 0.0, top, z0, z1, dark)
        m.box(x, x + 0.08, 0.0, top + 0.1, z0 - 0.07, z0, frame)
        m.box(x, x + 0.08, 0.0, top + 0.1, z1, z1 + 0.07, frame)
        m.box(x, x + 0.08, top, top + 0.12, z0 - 0.07, z1 + 0.07, frame)
    }
    fun windowZ(z0: Double, z1: Double, x: Double, glow: FloatArray, level: Float) {
        m.box(x, x + 0.02, 0.45, 2.3, z0, z1, glow, level)
        val count = max(1, Math.round((z1 - z0) / 0.65).toInt())
        for (i in 0..count) { val z = z0 + (z1 - z0) * i / count; m.box(x, x + 0.07, 0.45, 2.3, z - 0.03, z + 0.03, frame) }
        m.box(x, x + 0.08, 0.0, 0.45, z0 - 0.03, z1 + 0.03, frame)
        m.box(x, x + 0.08, 2.3, 2.42, z0 - 0.03, z1 + 0.03, frame, bottom = true)
    }
    // B0：落了的卷闸。B0b：亮着的冰室（cafe 暖光 0.8、竖框、两张卡座剪影）。
    shutterX(-2.9, -1.7, face("B0"), 2.45)
    val cafe = face("B0b")
    m.box(-1.5, -0.42, 0.4, 2.4, cafe, cafe + 0.02, c("#ffd2a0"), 0.8f)
    m.box(-1.56, -0.36, 0.0, 0.4, cafe, cafe + 0.08, frame)
    m.box(-1.56, -0.36, 2.4, 2.55, cafe, cafe + 0.08, frame, bottom = true)
    for (x in listOf(-1.53, -0.96, -0.39)) m.box(x - 0.03, x + 0.03, 0.4, 2.4, cafe, cafe + 0.07, frame)
    m.box(-1.42, -1.04, 0.4, 0.95, cafe + 0.02, cafe + 0.035, dark)
    m.box(-0.88, -0.5, 0.4, 0.95, cafe + 0.02, cafe + 0.035, dark)
    // L1 楼梯口与信箱；L2 药房；L3 卷闸半开透暖光；L4 暗门 + 面档小窗；L5 暗门。
    val l1 = face("L1")
    doorZ(-1.55, -0.85, l1)
    m.box(l1, l1 + 0.1, 1.1, 1.6, -0.65, -0.32, c("#7a4a33"))
    windowZ(0.0, 1.15, face("L2"), c("#c9ffe0"), 0.72f)
    val l3 = face("L3")
    m.box(l3, l3 + 0.02, 0.02, 0.95, 1.45, 2.75, c("#ffc887"), 0.9f)
    shutterZ(1.45, 2.75, l3, 2.5, 0.95)
    val l4 = face("L4")
    doorZ(3.05, 3.7, l4)
    windowZ(3.9, 4.4, l4, c("#d6ecff"), 0.72f)
    doorZ(4.8, 5.5, face("L5"), 2.2)
    // B2：暗巷口，门洞上一根日光灯管（tube 1.5）。
    val b2 = face("B2")
    m.box(5.3, 5.95, 0.0, 2.4, b2, b2 + 0.03, dark)
    m.box(5.22, 5.3, 0.0, 2.5, b2, b2 + 0.08, frame)
    m.box(5.35, 5.9, 2.55, 2.59, b2, b2 + 0.06, c("#c8f4ff"), 1.5f, bottom = true)
    // 楼脚一道道下垂的电线（每 0.7 m 一段）、电表箱。
    val wire = c("#11141a")
    for (b in MachiKowloonLayout.BLOCKS.filter { it.name.startsWith("L") && it.z1 > -1.8 }) {
        var z = b.z0 + 0.1
        while (z < b.z1 - 0.3) {
            val a = doubleArrayOf(b.x1 + 0.05, 2.8, z); val e = doubleArrayOf(b.x1 + 0.05, 2.8, min(b.z1 - 0.05, z + 0.7))
            var prev = a
            for (i in 1..8) { val t = i / 8.0; val q = doubleArrayOf(a[0], 2.8 - 0.06 * 4 * t * (1 - t), a[2] + (e[2] - a[2]) * t); m.tube(prev, q, 0.018, wire); prev = q }
            z += 0.7
        }
    }
    m.box(l1, l1 + 0.12, 1.6, 2.1, -0.3, -0.18, c("#c6c4bb"))
    m.box(-2.95, -2.6, 2.55, 2.85, face("B0"), face("B0") + 0.12, c("#c6c4bb"))
}
