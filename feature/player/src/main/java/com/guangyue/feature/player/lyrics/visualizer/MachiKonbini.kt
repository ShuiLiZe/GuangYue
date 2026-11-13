// 町景「便利店街角」（Folia machi scenes/konbini）白模起步：layout.ts 的尺寸表逐项照抄；base.ts 的台座 / 两条路 / 路缘 /
// 场地 / 斑马线与路边线 / 井盖 / 排水沟盖板，store.ts 的地坪、实墙与内衬、落地玻璃与竖框、对开自动门（先按关着摆）、
// 屋顶与女儿墙、落水管、店招带（底板 + 三色条）、屋顶三面广告塔（机箱、横梁、霓虹顶沿、顶盖、立柱与斜撑）照译。
// 店内（interior.ts，商品按种子摆、颜色 = products × shade）已接；道具（props.ts，MachiFrame 本地坐标照搭）已接；、招牌字面（konbiniSigns.ts）、灯（konbiniLights.ts）、门动画（konbiniDoor.ts）后续接。
// 圆角盒（roundedBox）按直角盒画；圆柱按 N 棱柱。随机数走自己的种子序列（MachiRng），不与 Folia 逐个对齐。
// 网格格式同 MachiKowloon.kt：每顶点 10 个 float（位置 3、法线 3、颜色 3、自发光 1）。玻璃单独一份网格（透明 0.2、不写深度）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object MachiKonbiniLayout {
    const val BASE_HALF = 6.0
    const val BASE_DEPTH = 0.6
    // LEVEL
    const val ROAD_Y = 0.0
    const val LOT_Y = 0.12
    const val FLOOR_Y = 0.16
    // ROAD
    const val ROAD_INNER = 2.2
    const val ROAD_OUTER = 5.5
    const val CURB_WIDTH = 0.16
    const val EDGE_LINE_INSET = 0.24
    // STORE
    const val X0 = -5.4; const val X1 = 0.8; const val Z0 = -5.4; const val Z1 = 0.4
    const val WALL = 0.18; const val WALL_HEIGHT = 3.2; const val ROOF_TOP = 3.42; const val PARAPET_TOP = 3.62; const val OVERHANG = 0.12
    const val RIGHT_GLASS_END = -2.6; const val FRONT_PILLAR_END = -4.6; const val GLASS_BOTTOM = 0.24; const val GLASS_TOP = 2.5
    // DOOR
    const val DOOR_X0 = -1.3; const val DOOR_X1 = 0.5; const val DOOR_HEIGHT = 2.2; const val DOOR_PANEL_WIDTH = 0.92; const val DOOR_SLIDE = 0.86
    // FASCIA
    const val FASCIA_Y0 = 2.6; const val FASCIA_Y1 = 3.28; const val FASCIA_DEPTH = 0.16
    // BOARD
    const val BOARD_CX = -2.2; const val BOARD_CZ = -2.65; const val BOARD_W = 5.6; const val BOARD_H = 2.3; const val BOARD_Y0 = 4.0
    const val BOARD_AZIMUTH = 45 * MACHI_DEG
    /** KONBINI_VIEW。 */
    val VIEW = MachiView(45 * MACHI_DEG, 31 * MACHI_DEG, 1.58)
    // PROPS（构图点、灯位用到的几项）
    const val PYLON_X = 1.62; const val PYLON_Z = -5.4; const val PYLON_HEIGHT = 4.6
    const val LAMP_X = 5.74; const val LAMP_Z = -1.2; const val LAMP_HEIGHT = 5.2; const val LAMP_ARM = 1.3
    /** props.ts buildStreetLamp head：(x − arm − 0.12, lot + height − 0.02, z)。 */
    val LAMP_HEAD = doubleArrayOf(LAMP_X - LAMP_ARM - 0.12, LOT_Y + LAMP_HEIGHT - 0.02, LAMP_Z)
    /** konbiniScene reflectionPlaneY。 */
    const val REFLECT_Y = 0.06
    /** KONBINI_ART。 */
    val ART = mapOf(
        "wall" to "#cfc9bd", "trim" to "#7f8898", "roof" to "#3a404d", "frame" to "#272c35", "interiorFloor" to "#d6d0c3",
        "interiorWall" to "#ece6d9", "shelf" to "#c6ccd4", "counter" to "#e6dfcf", "fridgeBody" to "#d3d8de", "acUnit" to "#d4d6d0",
        "mirrorPole" to "#e5702a", "spillLight" to "#ffcf92",
    )
}

/** konbiniSeed.ts resolveKonbiniVariation（同样的取数顺序）。 */
internal data class MachiKonbiniVariation(
    val bikeSlot: Int, val secondBike: Boolean, val bikeYaw: Double, val umbrellaInStand: Boolean, val openUmbrella: Boolean,
    val posterLayout: Int, val windYaw: Double, val windSlant: Double, val puddleX: Double, val puddleZ: Double, val puddleAmount: Double,
    val doorPhase: Double, val doorInterval: Double, val fanSpeed: Double, val shelfSeed: Long,
)

internal fun resolveMachiKonbiniVariation(seed: String): MachiKonbiniVariation {
    val r = MachiRng(machiHashSeed("machi:$seed"))
    fun pick(a: Double, b: Double) = a + (b - a) * r.next()
    return MachiKonbiniVariation(
        bikeSlot = if (r.next() < 0.5) 0 else 1, secondBike = r.next() < 0.45, bikeYaw = pick(-0.12, 0.12),
        umbrellaInStand = r.next() < 0.75, openUmbrella = r.next() < 0.35, posterLayout = (r.next() * 3).toInt(),
        windYaw = pick(-0.6, 0.6), windSlant = pick(0.06, 0.2), puddleX = pick(-40.0, 40.0), puddleZ = pick(-40.0, 40.0),
        puddleAmount = pick(0.45, 0.75), doorPhase = pick(0.0, 1.0), doorInterval = pick(22.0, 40.0), fanSpeed = pick(1.6, 3.2),
        shelfSeed = (r.next() * 0xffffffffL).toLong(),
    )
}

/** konbiniScene.ts framingPoints：底座顶面四角与两侧厚度、广告塔顶、立柱灯箱顶、店的后角。 */
internal fun machiKonbiniFramingPoints(): List<MachiVec3> {
    val L = MachiKonbiniLayout
    val h = L.BASE_HALF
    val pts = ArrayList<MachiVec3>()
    for (x in listOf(-h, h)) for (z in listOf(-h, h)) {
        pts += MachiVec3(x, 0.0, z)
        if (x != z || x < 0) pts += MachiVec3(x, -L.BASE_DEPTH, z)
    }
    val boardTop = L.BOARD_Y0 + L.BOARD_H + 0.6
    val r = L.BOARD_W / sqrt(3.0)
    for (k in 0 until 3) {
        val a = L.BOARD_AZIMUTH + PI / 3 + k * 2 * PI / 3
        pts += MachiVec3(L.BOARD_CX + sin(a) * r, boardTop, L.BOARD_CZ + cos(a) * r)
    }
    pts += MachiVec3(L.PYLON_X, L.PYLON_HEIGHT + 0.3, L.PYLON_Z)
    pts += MachiVec3(L.X0, L.PARAPET_TOP, L.Z0)
    return pts
}

/** 便利店路灯的光柱（konbiniScene：apex 灯头，target (x − 0.2, road, z)，半径 1.65，gain 1）。 */
internal fun machiKonbiniLightCones(): List<MachiLightConeSpec> {
    val h = MachiKonbiniLayout.LAMP_HEAD
    return listOf(MachiLightConeSpec(h, doubleArrayOf(h[0] - 0.2, MachiKonbiniLayout.ROAD_Y, h[2]), 1.65, 1.0))
}

/** 场景的天气布置与水洼（konbiniScene weather / puddles；滴水的随机抖动走种子序列）。 */
internal fun resolveMachiKonbiniWeather(seed: String): Pair<MachiPuddles, MachiWeatherLayout> {
    val L = MachiKonbiniLayout
    val v = resolveMachiKonbiniVariation(seed)
    val r = MachiRng(machiHashSeed("konbini:drips:$seed"))
    val drips = ArrayList<MachiDripSpec>()
    val frontZ = L.Z1 + L.FASCIA_DEPTH + 0.07
    val rightX = L.X1 + L.FASCIA_DEPTH + 0.07
    var x = L.X0 + 0.3
    while (x < L.X1) { drips += MachiDripSpec(x + (r.next() - 0.5) * 0.12, frontZ, L.FASCIA_Y0 - 0.02, L.LOT_Y, 1.1 + r.next() * 1.8, r.next()); x += 0.42 }
    var z = L.Z1
    while (z > L.Z0 + 0.5) { drips += MachiDripSpec(rightX, z + (r.next() - 0.5) * 0.12, L.FASCIA_Y0 - 0.02, L.LOT_Y, 1.1 + r.next() * 1.8, r.next()); z -= 0.45 }
    for (i in 0 until 4) drips += MachiDripSpec(L.X0 - 0.12 + (r.next() - 0.5) * 0.04, L.Z1 + 0.06, L.LOT_Y + 0.08, L.LOT_Y, 0.32 + r.next() * 0.12, i / 4.0)
    val lamp = L.LAMP_HEAD
    val bf = L.BOARD_W / (2 * sqrt(3.0)) + 0.8
    val layout = MachiWeatherLayout(
        half = L.BASE_HALF,
        groundY = L.LOT_Y * 0.5,
        roofs = listOf(MachiRoof(L.X0 - L.OVERHANG, L.Z0 - L.OVERHANG, L.X1 + L.OVERHANG, L.Z1 + L.OVERHANG, L.ROOF_TOP)),
        drips = drips,
        glints = listOf(
            doubleArrayOf(lamp[0] - 0.2, 2.4, lamp[2], 1.7),
            doubleArrayOf((L.FRONT_PILLAR_END + L.X1) / 2, 1.5, L.Z1 + 0.9, 2.0),
            doubleArrayOf(L.BOARD_CX + sin(L.BOARD_AZIMUTH) * bf, L.BOARD_Y0 + L.BOARD_H * 0.55, L.BOARD_CZ + cos(L.BOARD_AZIMUTH) * bf, 2.1),
        ),
        windYaw = v.windYaw,
        windSlant = v.windSlant,
    )
    return MachiPuddles(v.puddleX, v.puddleZ, v.puddleAmount) to layout
}

/** 雾里的两处灯（konbiniScene glowA / glowB）：店门口、路灯下。 */
internal fun machiKonbiniGlows(): Pair<FloatArray, FloatArray> {
    val L = MachiKonbiniLayout
    return floatArrayOf(((L.FRONT_PILLAR_END + L.X1) / 2 + 0.6).toFloat(), 0f, (L.Z1 + 0.9).toFloat(), 3.4f) to
        floatArrayOf(L.LAMP_HEAD[0].toFloat(), 0f, L.LAMP_HEAD[2].toFloat(), 2.3f)
}

// ---- 几何小工具：有朝向的盒子、圆柱 / 管子（N 棱柱）、三角面。 ----

private fun v3(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)
private fun add(a: DoubleArray, b: DoubleArray, k: Double = 1.0) = v3(a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k)
private fun cross(a: DoubleArray, b: DoubleArray) = v3(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
private fun norm(a: DoubleArray): DoubleArray { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return if (l < 1e-12) v3(0.0, 1.0, 0.0) else v3(a[0] / l, a[1] / l, a[2] / l) }
private fun neg(a: DoubleArray) = v3(-a[0], -a[1], -a[2])

/** 有朝向的盒子：中心 c，三根正交单位轴 ex, ey, ez（ex × ey = ez），半尺寸 hx, hy, hz。 */
internal fun MachiMesh.orientedBox(c: DoubleArray, ex: DoubleArray, ey: DoubleArray, ez: DoubleArray, hx: Double, hy: Double, hz: Double, color: FloatArray, emissive: Float = 0f) {
    fun face(u: DoubleArray, hu: Double, v: DoubleArray, hv: Double, w: DoubleArray, hw: Double) {
        val o = add(c, u, hu)
        quad(add(add(o, v, -hv), w, -hw), add(add(o, v, hv), w, -hw), add(add(o, v, hv), w, hw), add(add(o, v, -hv), w, hw), u, color, emissive)
    }
    face(ez, hz, ex, hx, ey, hy); face(neg(ez), hz, ey, hy, ex, hx)
    face(ex, hx, ey, hy, ez, hz); face(neg(ex), hx, ez, hz, ey, hy)
    face(ey, hy, ez, hz, ex, hx); face(neg(ey), hy, ex, hx, ez, hz)
}

/** 绕 y 轴转 a 的盒子（three rotation.y = a）：中心 (cx, cy, cz)，尺寸 w × h × d。 */
internal fun MachiMesh.boxRotY(cx: Double, cy: Double, cz: Double, w: Double, h: Double, d: Double, a: Double, color: FloatArray, emissive: Float = 0f) =
    orientedBox(v3(cx, cy, cz), v3(cos(a), 0.0, -sin(a)), v3(0.0, 1.0, 0.0), v3(sin(a), 0.0, cos(a)), w / 2, h / 2, d / 2, color, emissive)

/** 两点之间的圆管（N 棱柱，含两端盖）：r0 在 a 端、r1 在 b 端。 */
internal fun MachiMesh.ktube(a: DoubleArray, b: DoubleArray, r0: Double, color: FloatArray, r1: Double = r0, segments: Int = 8, emissive: Float = 0f) {
    val axis = norm(v3(b[0] - a[0], b[1] - a[1], b[2] - a[2]))
    val ref = if (kotlin.math.abs(axis[1]) < 0.9) v3(0.0, 1.0, 0.0) else v3(1.0, 0.0, 0.0)
    val u = norm(cross(ref, axis)); val w = cross(axis, u)
    fun ring(i: Int) = add(v3(0.0, 0.0, 0.0), add(add(v3(0.0, 0.0, 0.0), u, cos(2 * PI * i / segments)), w, sin(2 * PI * i / segments)))
    for (i in 0 until segments) {
        val d0 = ring(i); val d1 = ring(i + 1)
        val n = norm(add(d0, d1))
        quad(add(a, d0, r0), add(a, d1, r0), add(b, d1, r1), add(b, d0, r1), n, color, emissive)
        quad(b, add(b, d0, r1), add(b, d1, r1), add(b, d1, r1), axis, color, emissive)
        quad(a, add(a, d1, r0), add(a, d0, r0), add(a, d0, r0), neg(axis), color, emissive)
    }
}

/** 竖直圆柱：底面中心 (x, y, z)，高 h（cylinderAt）。 */
internal fun MachiMesh.cylinderY(x: Double, y: Double, z: Double, r: Double, h: Double, color: FloatArray, rTop: Double = r, segments: Int = 10, emissive: Float = 0f) =
    ktube(v3(x, y, z), v3(x, y + h, z), r, color, rTop, segments, emissive)

/** 朝上的水平四边形（贴地薄板 / 路面漆）。 */
internal fun MachiMesh.floorQuad(x0: Double, x1: Double, z0: Double, z1: Double, y: Double, color: FloatArray, emissive: Float = 0f) =
    quad(v3(x0, y, z1), v3(x1, y, z1), v3(x1, y, z0), v3(x0, y, z0), v3(0.0, 1.0, 0.0), color, emissive)

/** 竖直平面（PlaneGeometry 绕 y 转 a）：中心 c，宽 w 高 h。 */
internal fun MachiMesh.planeRotY(cx: Double, cy: Double, cz: Double, w: Double, h: Double, a: Double, color: FloatArray, emissive: Float = 0f) {
    val ex = v3(cos(a), 0.0, -sin(a)); val n = v3(sin(a), 0.0, cos(a)); val c = v3(cx, cy, cz)
    quad(add(add(c, ex, -w / 2), v3(0.0, 1.0, 0.0), -h / 2), add(add(c, ex, w / 2), v3(0.0, 1.0, 0.0), -h / 2),
        add(add(c, ex, w / 2), v3(0.0, 1.0, 0.0), h / 2), add(add(c, ex, -w / 2), v3(0.0, 1.0, 0.0), h / 2), n, color, emissive)
}

/** 便利店白模：opaque 网格 + 玻璃网格（透明）。faces 留给招牌模块贴字面。 */
internal class MachiKonbiniModel(
    val mesh: FloatArray,
    val glass: FloatArray,
    /** 广告塔三面：面中心 x, y, z 与朝向（rotationY）。 */
    val boardFaces: List<DoubleArray>,
    /** 店招两块面：中心 x, y, z、朝向、宽、高。 */
    val fasciaFaces: List<DoubleArray>,
    val stats: Map<String, Int>,
    val propFaces: MachiKonbiniPropFaces? = null,
)

internal fun buildMachiKonbini(palette: MachiPalette, seed: String): MachiKonbiniModel {
    val L = MachiKonbiniLayout
    val m = MachiMesh(); val g = MachiMesh()
    val stats = LinkedHashMap<String, Int>()
    fun count(k: String) { stats[k] = (stats[k] ?: 0) + 1 }
    fun art(k: String) = machiRgb(L.ART.getValue(k))
    val wall = art("wall"); val trim = art("trim"); val roof = art("roof"); val frame = art("frame")
    val lining = art("interiorWall"); val floor = art("interiorFloor")
    val asphalt = machiRgb(palette.asphalt); val concrete = machiRgb(palette.concrete); val curb = machiRgb(palette.curb)
    val paint = machiRgb(palette.roadPaint); val darkMetal = machiRgb(palette.darkMetal); val metal = machiRgb(palette.metal)
    val rubber = machiRgb(palette.rubber); val glassC = machiRgb(palette.glass); val signBase = machiRgb(palette.signBase)
    val h = L.BASE_HALF

    // ---- base.ts ----
    // 台座（plinth.ts）：三层土石 + 深色底框。
    m.box(-h, h, -0.1, 0.0, -h, h, machiRgb(palette.plinthTop))
    m.box(-h + 0.012, h - 0.012, -0.3, -0.1, -h + 0.012, h - 0.012, machiRgb(palette.plinthMid))
    m.box(-h + 0.006, h - 0.006, -0.52, -0.3, -h + 0.006, h - 0.006, machiRgb(palette.plinthLow))
    m.box(-h - 0.04, h + 0.04, -L.BASE_DEPTH, -L.BASE_DEPTH + 0.1, -h - 0.04, h + 0.04, machiRgb(palette.plinthTrim))
    // 路面（沥青薄板，抬 0.002）。
    m.floorQuad(-h, h, -h, h, L.ROAD_Y + 0.002, asphalt)
    // 店前场地与路对面人行道：混凝土板 + 路缘石。
    val ri = L.ROAD_INNER; val ro = L.ROAD_OUTER; val c = L.CURB_WIDTH
    listOf(doubleArrayOf(-h, ri, -h, ri), doubleArrayOf(ro, h, -h, ri), doubleArrayOf(-h, ri, ro, h), doubleArrayOf(ro, h, ro, h)).forEach { s ->
        m.box(s[0] + 0.002, s[1] - 0.002, L.ROAD_Y, L.LOT_Y - 0.004, s[2] + 0.002, s[3] - 0.002, concrete); count("lot")
    }
    val curbTop = L.LOT_Y + 0.012
    listOf(
        doubleArrayOf(-h, ri, ri - c, ri), doubleArrayOf(ri - c, ri, -h, ri), doubleArrayOf(ro, h, ri - c, ri), doubleArrayOf(ro, ro + c, -h, ri),
        doubleArrayOf(-h, ri, ro, ro + c), doubleArrayOf(ri - c, ri, ro, h), doubleArrayOf(ro, h, ro, ro + c), doubleArrayOf(ro, ro + c, ro, h),
    ).forEach { s -> m.box(s[0], s[1], L.ROAD_Y, curbTop, s[2], s[3], curb); count("curb") }
    // 场地 / 人行道顶面薄板（湿地面装在这些上面：concrete，puddleBias 0.08）。
    m.floorQuad(-h, ri - c, -h, ri - c, L.LOT_Y + 0.002, concrete)
    m.floorQuad(ro + c, h, -h, ri, L.LOT_Y + 0.002, concrete)
    m.floorQuad(-h, ri, ro + c, h, L.LOT_Y + 0.002, concrete)
    m.floorQuad(ro + c, h, ro + c, h, L.LOT_Y + 0.002, concrete)
    // 斑马线、路边线、停止线（roadPaint，路面上 0.006）。
    val bars = ArrayList<DoubleArray>() // x, z, w, d
    run {
        val ax0 = 0.15; val ax1 = 1.95
        var z = ri + 0.42
        while (z < ro - 0.3) { bars += doubleArrayOf((ax0 + ax1) / 2, z + 0.2, ax1 - ax0, 0.4); z += 0.8 }
        val bz0 = -0.95; val bz1 = 0.85
        var x = ri + 0.42
        while (x < ro - 0.3) { bars += doubleArrayOf(x + 0.2, (bz0 + bz1) / 2, 0.4, bz1 - bz0); x += 0.8 }
        val inset = L.EDGE_LINE_INSET; val lw = 0.1
        listOf(doubleArrayOf(-h, ax0 - 0.3), doubleArrayOf(ax1 + 0.3, ri - 0.1)).forEach { (x0, x1) ->
            bars += doubleArrayOf((x0 + x1) / 2, ri + inset, x1 - x0, lw); bars += doubleArrayOf((x0 + x1) / 2, ro - inset, x1 - x0, lw)
        }
        listOf(doubleArrayOf(-h, bz0 - 0.3), doubleArrayOf(bz1 + 0.3, ri - 0.1)).forEach { (z0, z1) ->
            bars += doubleArrayOf(ri + inset, (z0 + z1) / 2, lw, z1 - z0); bars += doubleArrayOf(ro - inset, (z0 + z1) / 2, lw, z1 - z0)
        }
        bars += doubleArrayOf(ax0 - 0.45, (ri + ro) / 2 + 0.85, 0.3, (ro - ri) / 2 - 0.2)
        bars += doubleArrayOf((ri + ro) / 2 - 0.85, bz0 - 0.45, (ro - ri) / 2 - 0.2, 0.3)
    }
    bars.forEach { b -> m.floorQuad(b[0] - b[2] / 2, b[0] + b[2] / 2, b[1] - b[3] / 2, b[1] + b[3] / 2, L.ROAD_Y + 0.006, paint); count("paint") }
    // 井盖、排水沟盖板。
    m.cylinderY(-2.4, L.ROAD_Y, (ri + ro) / 2 + 0.3, 0.36, 0.02, darkMetal, segments = 24)
    run {
        var n = 0
        var x = -5.4
        while (x < ri - 0.6 && n < 7) { m.box(x - 0.35, x + 0.35, L.ROAD_Y, L.ROAD_Y + 0.012, ri + 0.13 - 0.11, ri + 0.13 + 0.11, darkMetal); x += 1.1; n++ }
        var z = -5.4
        while (z < ri - 0.6 && n < 14) { m.box(ri + 0.13 - 0.11, ri + 0.13 + 0.11, L.ROAD_Y, L.ROAD_Y + 0.012, z - 0.35, z + 0.35, darkMetal); z += 1.1; n++ }
        stats["grate"] = n
    }

    // ---- store.ts ----
    val x0 = L.X0; val x1 = L.X1; val z0 = L.Z0; val z1 = L.Z1; val w = L.WALL; val wh = L.WALL_HEIGHT
    // 地坪与实墙（外墙 + 内衬）。
    m.box(x0, x1, L.LOT_Y - 0.02, L.FLOOR_Y, z0, z1, floor)
    m.box(x0, x1, L.FLOOR_Y, wh, z0, z0 + w, wall)
    m.box(x0, x0 + w, L.FLOOR_Y, wh, z0, z1, wall)
    m.box(x1 - w, x1, L.FLOOR_Y, wh, z0, L.RIGHT_GLASS_END, wall)
    m.box(x0, L.FRONT_PILLAR_END, L.FLOOR_Y, wh, z1 - w, z1, wall)
    m.box(x0, x1, L.GLASS_TOP, wh, z1 - w, z1, wall)
    m.box(x1 - w, x1, L.GLASS_TOP, wh, L.RIGHT_GLASS_END, z1, wall)
    m.box(x0 + w, x1 - w, L.FLOOR_Y, wh - 0.05, z0 + w, z0 + w + 0.02, lining)
    m.box(x0 + w, x0 + w + 0.02, L.FLOOR_Y, wh - 0.05, z0 + w, z1 - w, lining)
    m.box(x1 - w - 0.02, x1 - w, L.FLOOR_Y, wh - 0.05, z0 + w, L.RIGHT_GLASS_END, lining)
    m.box(x1 - 0.12, x1, L.FLOOR_Y, L.GLASS_TOP, z1 - 0.12, z1, frame)
    m.box(L.FRONT_PILLAR_END, x1, L.FLOOR_Y, L.GLASS_BOTTOM, z1 - 0.1, z1, frame)
    m.box(x1 - 0.1, x1, L.FLOOR_Y, L.GLASS_BOTTOM, L.RIGHT_GLASS_END, z1, frame)
    m.box(L.FRONT_PILLAR_END, x1, L.GLASS_TOP - 0.05, L.GLASS_TOP + 0.02, z1 - 0.1, z1, frame)
    m.box(x1 - 0.1, x1, L.GLASS_TOP - 0.05, L.GLASS_TOP + 0.02, L.RIGHT_GLASS_END, z1, frame)
    // 落地玻璃：前面门两侧、右面前段；竖框约每 1.25 m 一根。
    run {
        val bottom = L.GLASS_BOTTOM; val top = L.GLASS_TOP - 0.05; val hh = top - bottom
        listOf(doubleArrayOf(L.FRONT_PILLAR_END, L.DOOR_X0 - 0.08), doubleArrayOf(L.DOOR_X1 + 0.08, x1 - 0.12)).forEach { (a, b) ->
            g.planeRotY((a + b) / 2, bottom + hh / 2, z1 - 0.05, b - a, hh, 0.0, glassC); count("glass")
            val n = max(1, ((b - a) / 1.25).roundToInt())
            for (i in 0..n) { val x = a + (b - a) * i / n; m.box(x - 0.03, x + 0.03, bottom, top, z1 - 0.09, z1 - 0.01, frame); count("mullion") }
        }
        val ra = L.RIGHT_GLASS_END; val rb = z1 - 0.12
        g.planeRotY(x1 - 0.05, bottom + hh / 2, (ra + rb) / 2, rb - ra, hh, PI / 2, glassC); count("glass")
        val n = max(1, ((rb - ra) / 1.25).roundToInt())
        for (i in 0..n) { val z = ra + (rb - ra) * i / n; m.box(x1 - 0.09, x1 - 0.01, bottom, top, z - 0.03, z + 0.03, frame); count("mullion") }
    }
    // 对开自动门：门框、门头、感应器、地垫、门上固定玻璃、两扇门（先按关着摆，门动画后接）。
    run {
        val ph = L.DOOR_HEIGHT - L.FLOOR_Y - 0.02
        val mid = (L.DOOR_X0 + L.DOOR_X1) / 2
        val dz = z1 + 0.035
        m.box(L.DOOR_X0 - 0.08, L.DOOR_X0, L.FLOOR_Y, L.GLASS_TOP, z1 - 0.1, z1 + 0.08, frame)
        m.box(L.DOOR_X1, L.DOOR_X1 + 0.08, L.FLOOR_Y, L.GLASS_TOP, z1 - 0.1, z1 + 0.08, frame)
        m.box(L.DOOR_X0 - 0.9, L.DOOR_X1 + 0.9, L.DOOR_HEIGHT, L.DOOR_HEIGHT + 0.16, z1 - 0.02, z1 + 0.1, frame)
        m.box(mid - 0.18, mid + 0.18, L.DOOR_HEIGHT - 0.07, L.DOOR_HEIGHT, z1 + 0.04, z1 + 0.11, darkMetal)
        m.box(L.DOOR_X0 - 0.1, L.DOOR_X1 + 0.1, L.LOT_Y, L.LOT_Y + 0.012, z1 + 0.12, z1 + 0.85, rubber)
        m.box(L.DOOR_X0, L.DOOR_X1, L.FLOOR_Y, L.FLOOR_Y + 0.01, z1 - 0.9, z1 - 0.12, rubber)
        g.planeRotY(mid, (L.DOOR_HEIGHT + 0.16 + L.GLASS_TOP) / 2, z1 - 0.05, L.DOOR_X1 - L.DOOR_X0, L.GLASS_TOP - L.DOOR_HEIGHT - 0.16, 0.0, glassC)
        val pw = L.DOOR_PANEL_WIDTH
        for (dir in listOf(-1, 1)) {
            val cx = mid + dir * (pw / 2 - 0.02); val by = L.FLOOR_Y + 0.01
            m.box(cx - pw / 2, cx - pw / 2 + 0.05, by, by + ph, dz - 0.025, dz + 0.025, frame)
            m.box(cx + pw / 2 - 0.05, cx + pw / 2, by, by + ph, dz - 0.025, dz + 0.025, frame)
            m.box(cx - pw / 2, cx + pw / 2, by, by + 0.09, dz - 0.025, dz + 0.025, frame)
            m.box(cx - pw / 2, cx + pw / 2, by + ph - 0.05, by + ph, dz - 0.025, dz + 0.025, frame)
            m.box(cx - 0.02, cx + 0.02, by + 0.9, by + 1.25, dz + 0.025, dz + 0.045, darkMetal)
            g.planeRotY(cx, by + ph / 2 + 0.02, dz, pw - 0.1, ph - 0.14, 0.0, glassC)
            count("doorPanel")
        }
    }
    // 屋顶、女儿墙、落水管。
    run {
        val o = L.OVERHANG; val top = L.ROOF_TOP; val p = L.PARAPET_TOP; val t = 0.1
        m.box(x0 - o, x1 + o, wh, top, z0 - o, z1 + o, roof)
        m.box(x0 - o, x1 + o, top, p, z0 - o, z0 - o + t, trim)
        m.box(x0 - o, x1 + o, top, p, z1 + o - t, z1 + o, trim)
        m.box(x0 - o, x0 - o + t, top, p, z0 - o, z1 + o, trim)
        m.box(x1 + o - t, x1 + o, top, p, z0 - o, z1 + o, trim)
        val px = x0 - 0.06; val pz = z1 - 0.12
        m.cylinderY(px, L.LOT_Y + 0.2, pz, 0.045, top - L.LOT_Y - 0.2, metal)
        m.ktube(v3(px, L.LOT_Y + 0.22, pz), v3(px - 0.05, L.LOT_Y + 0.06, pz + 0.2), 0.045, metal)
        m.ktube(v3(px, top - 0.02, pz), v3(px + 0.18, top - 0.02, pz + 0.2), 0.04, metal)
    }
    // 店招带：前、右两块灯箱底板，上沿一条、下沿两条三色条。
    val fasciaFaces = ArrayList<DoubleArray>()
    run {
        val d = L.FASCIA_DEPTH
        m.box(x0 - 0.02, x1 + d, L.FASCIA_Y0, L.FASCIA_Y1, z1, z1 + d, signBase)
        m.box(x1, x1 + d, L.FASCIA_Y0, L.FASCIA_Y1, z0 + 0.4, z1 + d - 0.01, signBase)
        listOf(
            Triple(palette.brand[0], L.FASCIA_Y1 - 0.07, L.FASCIA_Y1 - 0.01),
            Triple(palette.brand[2], L.FASCIA_Y0 + 0.1, L.FASCIA_Y0 + 0.15),
            Triple(palette.brand[0], L.FASCIA_Y0 + 0.03, L.FASCIA_Y0 + 0.08),
        ).forEach { (hex, y0, y1) ->
            val col = machiRgb(hex)
            m.box(x0 - 0.01, x1 + d + 0.01, y0, y1, z1 + d - 0.02, z1 + d + 0.008, col)
            m.box(x1 + d - 0.02, x1 + d + 0.008, y0, y1, z0 + 0.41, z1 + d, col)
        }
        val th = (L.FASCIA_Y1 - 0.08) - (L.FASCIA_Y0 + 0.16)
        val ty = (L.FASCIA_Y1 - 0.08 + L.FASCIA_Y0 + 0.16) / 2
        fasciaFaces += doubleArrayOf((x0 + x1) / 2, ty, z1 + d + 0.006, 0.0, x1 - x0 - 0.2, th)
        fasciaFaces += doubleArrayOf(x1 + d + 0.006, ty, (z0 + 0.4 + z1) / 2, PI / 2, z1 - z0 - 0.6, th)
    }
    // 屋顶三面广告塔：三块屏幕机箱 + 上下横梁 + 霓虹顶沿、顶盖、三根立柱与斜撑。
    val boardFaces = ArrayList<DoubleArray>()
    run {
        val W = L.BOARD_W; val H = L.BOARD_H
        val inr = W / (2 * sqrt(3.0)); val cir = W / sqrt(3.0)
        val cx = L.BOARD_CX; val cz = L.BOARD_CZ
        val verts = ArrayList<DoubleArray>()
        val neon = machiRgb(palette.neon)
        for (k in 0 until 3) {
            val a = L.BOARD_AZIMUTH + k * 2 * PI / 3
            val nx = sin(a); val nz = cos(a)
            boardFaces += doubleArrayOf(cx + nx * (inr + 0.012), L.BOARD_Y0 + H / 2, cz + nz * (inr + 0.012), a)
            val va = a + PI / 3
            verts += v3(cx + sin(va) * cir, 0.0, cz + cos(va) * cir)
            m.boxRotY(cx + nx * (inr - 0.07), L.BOARD_Y0 + H / 2, cz + nz * (inr - 0.07), W + 0.06, H + 0.16, 0.14, a, darkMetal)
            for (y in listOf(L.BOARD_Y0 + H + 0.1, L.BOARD_Y0 - 0.1)) m.boxRotY(cx + nx * (inr - 0.04), y, cz + nz * (inr - 0.04), W + 0.14, 0.09, 0.2, a, trim)
            // 霓虹顶沿（glow neonRim，level 1.4）。
            m.boxRotY(cx + nx * (inr + 0.075), L.BOARD_Y0 + H + 0.1, cz + nz * (inr + 0.075), W + 0.1, 0.04, 0.04, a, neon, 1.4f)
            // 屏面先铺暗底（字面由招牌模块贴，后续接）。
            m.planeRotY(cx + nx * (inr + 0.012), L.BOARD_Y0 + H / 2, cz + nz * (inr + 0.012), W - 0.14, H - 0.12, a, machiRgb(palette.boardUnlit))
        }
        // 顶盖：三角板（顶点外扩 1.01），厚 0.06 + 倒角。
        val lidY0 = L.BOARD_Y0 + H + 0.08; val lidY1 = lidY0 + 0.06 + 0.015
        val lv = verts.map { v3(cx + (it[0] - cx) * 1.01, 0.0, cz + (it[2] - cz) * 1.01) }
        m.quad(v3(lv[0][0], lidY1, lv[0][2]), v3(lv[1][0], lidY1, lv[1][2]), v3(lv[2][0], lidY1, lv[2][2]), v3(lv[2][0], lidY1, lv[2][2]), v3(0.0, 1.0, 0.0), metal)
        for (i in 0 until 3) {
            val a = lv[i]; val b = lv[(i + 1) % 3]
            val n = norm(v3((a[0] + b[0]) / 2 - cx, 0.0, (a[2] + b[2]) / 2 - cz))
            m.quad(v3(a[0], lidY0, a[2]), v3(b[0], lidY0, b[2]), v3(b[0], lidY1, b[2]), v3(a[0], lidY1, a[2]), n, metal)
        }
        verts.forEach { vtx ->
            val inward = norm(v3(cx - vtx[0], 0.0, cz - vtx[2]))
            val foot = add(vtx, inward, 0.18)
            m.cylinderY(foot[0], L.ROOF_TOP, foot[2], 0.06, L.BOARD_Y0 + H + 0.1 - L.ROOF_TOP, darkMetal, segments = 8)
            val brace = add(foot, inward, 0.9)
            m.ktube(v3(brace[0], L.ROOF_TOP, brace[2]), v3(foot[0], L.BOARD_Y0 - 0.1, foot[2]), 0.03, darkMetal)
            m.box(brace[0] - 0.14, brace[0] + 0.14, L.ROOF_TOP, L.ROOF_TOP + 0.06, brace[2] - 0.14, brace[2] + 0.14, trim)
        }
    }
    val variation = resolveMachiKonbiniVariation(seed)
    addMachiKonbiniInterior(m, g, palette, variation.shelfSeed, stats)
    val propFaces = addMachiKonbiniProps(m, palette, seed, variation, stats)
    return MachiKonbiniModel(m.toArray(), g.toArray(), boardFaces, fasciaFaces, stats, propFaces)
}

// ---- interior.ts：两排双面货架与商品、后墙冰柜与瓶子、收银台、杂志架、复印机与 ATM、员工门、天花灯。 ----

private val KONBINI_SHELF_ROWS = doubleArrayOf(-1.75, -3.15)
private const val KONBINI_SHELF_XA = -4.5
private const val KONBINI_SHELF_XB = -1.4
private const val KONBINI_SHELF_DEPTH = 0.86
private const val KONBINI_SHELF_HEIGHT = 1.45
private val KONBINI_SHELF_LEVELS = doubleArrayOf(0.12, 0.45, 0.78, 1.1)

/** 商品槽位：x, y（底）, z, w, h, d, 颜色序号, 明暗。 */
private class KonbiniSlot(val x: Double, val y: Double, val z: Double, val w: Double, val h: Double, val d: Double, val color: Int, val shade: Double)

private fun srgbToLinear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
private fun linearToSrgb(x: Double): Float { val v = x.coerceIn(0.0, 1.0); return (if (v <= 0.0031308) v * 12.92 else 1.055 * Math.pow(v, 1 / 2.4) - 0.055).toFloat() }
/** 线性空间里 × k（three 的实例色 = 商品色 × shade）。 */
internal fun machiShade(rgb: FloatArray, k: Double) = FloatArray(3) { linearToSrgb(srgbToLinear(rgb[it]) * k) }

internal fun addMachiKonbiniInterior(m: MachiMesh, g: MachiMesh, palette: MachiPalette, shelfSeed: Long, stats: MutableMap<String, Int>) {
    val L = MachiKonbiniLayout
    fun art(k: String) = machiRgb(L.ART.getValue(k))
    val shelf = art("shelf"); val dark = machiRgb(palette.darkMetal); val frame = art("frame"); val metal = machiRgb(palette.metal)
    val random = MachiRng(shelfSeed)
    val y = L.FLOOR_Y
    val shelfSlots = ArrayList<KonbiniSlot>(); val bottleSlots = ArrayList<KonbiniSlot>(); val magazineSlots = ArrayList<KonbiniSlot>()
    val xa = KONBINI_SHELF_XA; val xb = KONBINI_SHELF_XB; val half = KONBINI_SHELF_DEPTH / 2
    // 货架：底座、中间背板、端板、端头小招牌、三层隔板；商品两面都摆（与骨架同一条随机序列，先填后建）。
    for (z in KONBINI_SHELF_ROWS) {
        for (side in intArrayOf(-1, 1)) for (level in KONBINI_SHELF_LEVELS) {
            var x = xa + 0.06
            while (x < xb - 0.12) {
                val w = 0.1 + random.next() * 0.14; val h = 0.12 + random.next() * 0.16; val d = 0.18 + random.next() * 0.14
                if (random.next() > 0.08) {
                    shelfSlots += KonbiniSlot(x + w / 2, y + level, z + side * (half - d / 2 - 0.04), w, h, d, (random.next() * 8).toInt(), 0.82 + random.next() * 0.3)
                }
                x += w + 0.015 + random.next() * 0.03
            }
        }
        m.box(xa, xb, y, y + 0.1, z - half, z + half, dark)
        m.box(xa, xb, y + 0.1, y + KONBINI_SHELF_HEIGHT, z - 0.03, z + 0.03, shelf)
        m.box(xa - 0.04, xa, y, y + KONBINI_SHELF_HEIGHT + 0.05, z - half, z + half, shelf)
        m.box(xb, xb + 0.04, y, y + KONBINI_SHELF_HEIGHT + 0.05, z - half, z + half, shelf)
        m.box(xb + 0.04, xb + 0.06, y + KONBINI_SHELF_HEIGHT - 0.3, y + KONBINI_SHELF_HEIGHT, z - half + 0.05, z + half - 0.05, machiRgb(palette.brand[0]))
        for (level in KONBINI_SHELF_LEVELS.drop(1)) m.box(xa, xb, y + level - 0.025, y + level, z - half, z + half, shelf)
        stats["gondola"] = (stats["gondola"] ?: 0) + 1
    }
    // 冰柜里的瓶子。
    val fx0 = -4.92; val fx1 = -1.32; val fz0 = -5.2; val fz1 = -4.58; val fh = 2.05; val doors = 5
    for (level in 0 until 5) {
        val ly = y + 0.16 + level * 0.33
        var x = fx0 + 0.1
        while (x < fx1 - 0.14) {
            val tall = 0.18 + random.next() * 0.07
            bottleSlots += KonbiniSlot(x + 0.04, ly, fz0 + 0.32, 0.075, tall, 0.075, (random.next() * 8).toInt(), 0.85 + random.next() * 0.3)
            x += 0.11 + random.next() * 0.02
        }
    }
    // 冰柜：柜体、灯板（glow fridgeGlow = vendingLight × 1.5）、隔架、门框与把手、整面玻璃。
    val body = art("fridgeBody")
    m.box(fx0, fx1, y, y + fh, fz0, fz0 + 0.06, body)
    m.box(fx0, fx0 + 0.06, y, y + fh, fz0, fz1, body)
    m.box(fx1 - 0.06, fx1, y, y + fh, fz0, fz1, body)
    m.box(fx0, fx1, y + fh - 0.22, y + fh, fz0, fz1, body)
    m.box(fx0, fx1, y, y + 0.16, fz0, fz1, body)
    m.planeRotY((fx0 + fx1) / 2, y + 0.16 + (fh - 0.38) / 2, fz0 + 0.065, fx1 - fx0 - 0.14, fh - 0.42, 0.0, machiRgb(palette.vendingLight), 1.5f)
    for (level in 1..4) { val ly = y + 0.16 + level * 0.33; m.box(fx0 + 0.06, fx1 - 0.06, ly - 0.012, ly, fz0 + 0.06, fz1 - 0.05, metal) }
    val dw = (fx1 - fx0) / doors
    for (i in 0..doors) {
        val x = fx0 + dw * i
        m.box(x - 0.025, x + 0.025, y + 0.16, y + fh - 0.22, fz1 - 0.03, fz1 + 0.01, frame)
        if (i < doors) m.box(x + dw - 0.1, x + dw - 0.08, y + 0.8, y + 1.3, fz1 + 0.01, fz1 + 0.04, frame)
    }
    g.planeRotY((fx0 + fx1) / 2, y + 0.16 + (fh - 0.38) / 2, fz1 - 0.005, fx1 - fx0 - 0.08, fh - 0.4, 0.0, machiRgb(palette.glass))
    // 收银台：台身、色条、台面、收银机与小屏（glow screen = led × 1.3）、咖啡机、热食柜（glow snackCase = spillLight × 1.6）。
    run {
        val ca = -0.38; val cb = 0.18; val za = -2.55; val zb = -1.05
        m.box(ca, cb, y, y + 0.95, za, zb, art("counter"))
        m.box(ca - 0.01, ca + 0.01, y + 0.62, y + 0.72, za + 0.05, zb - 0.05, machiRgb(palette.brand[2]))
        m.box(ca - 0.04, cb + 0.02, y + 0.95, y + 0.99, za - 0.02, zb + 0.02, art("trim"))
        m.box(ca + 0.12, ca + 0.42, y + 0.99, y + 1.13, zb - 0.42, zb - 0.12, dark)
        m.box(ca + 0.4, ca + 0.43, y + 1.1, y + 1.34, zb - 0.38, zb - 0.16, dark)
        m.box(ca + 0.08, ca + 0.38, y + 0.99, y + 1.36, za + 0.12, za + 0.42, dark)
        m.planeRotY(ca + 0.395, y + 1.22, zb - 0.27, 0.2, 0.14, -PI / 2, machiRgb(palette.led), 1.3f)
        val spill = machiRgb(mcMixHex(L.ART.getValue("spillLight"), palette.interiorLight, 0.5))
        m.box(ca + 0.06, ca + 0.5, y + 0.99, y + 1.3, za + 0.58, za + 1.02, spill, 1.6f)
        m.box(ca + 0.04, ca + 0.52, y + 1.3, y + 1.34, za + 0.56, za + 1.04, dark)
    }
    // 杂志架、复印机、ATM（屏 glow screen）、员工门；杂志斜靠在架子上沿。
    m.box(-4.45, -1.65, y, y + 0.9, 0.02, 0.28, shelf)
    m.box(-5.18, -4.62, y, y + 1.12, -0.72, -0.08, art("acUnit"))
    m.box(-5.2, -4.72, y, y + 1.5, -1.72, -1.02, dark)
    m.box(-0.92, -0.08, y, y + 2.05, L.Z0 + L.WALL, L.Z0 + L.WALL + 0.04, frame)
    m.planeRotY(-4.715 + 0.006, y + 1.12, -1.37, 0.3, 0.22, PI / 2, machiRgb(palette.led), 1.3f)
    run {
        var x = -4.38
        while (x < -1.75) { magazineSlots += KonbiniSlot(x, y + 0.9, 0.15, 0.17, 0.24, 0.02, (random.next() * 8).toInt(), 0.9 + random.next() * 0.2); x += 0.2 + random.next() * 0.04 }
    }
    // 天花灯：三排灯条（glow ceilingLight = interiorLight × 2.4）。
    val ceiling = machiRgb(palette.interiorLight)
    for (z in doubleArrayOf(-0.7, -2.45, -4.0)) for (x in doubleArrayOf(-3.9, -2.2, -0.5)) {
        m.box(x - 0.625, x + 0.625, L.WALL_HEIGHT - 0.06 - 0.025, L.WALL_HEIGHT - 0.06 + 0.025, z - 0.11, z + 0.11, ceiling, 2.4f)
    }
    // 商品（实例色 = products[color] × shade）：盒子、瓶子（8 棱柱）、杂志（绕 x 轴倾 −0.25）。
    val products = palette.products.map { machiRgb(it) }
    fun col(s: KonbiniSlot) = machiShade(products[s.color % products.size], s.shade)
    shelfSlots.forEach { s -> m.box(s.x - s.w / 2, s.x + s.w / 2, s.y, s.y + s.h, s.z - s.d / 2, s.z + s.d / 2, col(s)) }
    bottleSlots.forEach { s -> m.cylinderY(s.x, s.y, s.z, s.w / 2, s.h, col(s), segments = 8) }
    val t = -0.25
    magazineSlots.forEach { s ->
        m.orientedBox(v3(s.x, s.y + s.h / 2, s.z), v3(1.0, 0.0, 0.0), v3(0.0, cos(t), sin(t)), v3(0.0, -sin(t), cos(t)), s.w / 2, s.h / 2, s.d / 2, col(s))
    }
    stats["product"] = shelfSlots.size; stats["bottle"] = bottleSlots.size; stats["magazine"] = magazineSlots.size
}

// ---- props.ts：贩卖机两台、空调外机四台、路灯、电线杆与电线、立柱灯箱、反光镜、停车标志、垃圾箱、A 字立牌；
//      按歌变的自行车与伞（SongProps）。每件在本地坐标里搭好再摆（MachiFrame：three 的 position + rotation XYZ）。 ----

/** 3×3 旋转（行主序）× 平移：本地坐标 → 世界。 */
internal class MachiFrame(val m: MachiMesh, val o: DoubleArray = v3(0.0, 0.0, 0.0), val r: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)) {
    fun dir(x: Double, y: Double, z: Double) = v3(r[0] * x + r[1] * y + r[2] * z, r[3] * x + r[4] * y + r[5] * z, r[6] * x + r[7] * y + r[8] * z)
    fun pt(x: Double, y: Double, z: Double) = add(o, dir(x, y, z))
    fun pt(p: DoubleArray) = pt(p[0], p[1], p[2])
    /** 子坐标系：在本地 (x, y, z) 处，按 three Euler XYZ（R = Rx · Ry · Rz）转。 */
    fun child(x: Double, y: Double, z: Double, rx: Double = 0.0, ry: Double = 0.0, rz: Double = 0.0): MachiFrame {
        val local = mul(mul(rotX(rx), rotY(ry)), rotZ(rz))
        return MachiFrame(m, pt(x, y, z), mul(r, local))
    }
    /** boxAt：底面中心在 at，尺寸 w × h × d，可绕本地 y 转。 */
    fun boxAt(w: Double, h: Double, d: Double, x: Double, y: Double, z: Double, color: FloatArray, emissive: Float = 0f, rotY: Double = 0.0) {
        val f = if (rotY != 0.0) child(x, y + h / 2, z, ry = rotY) else child(x, y + h / 2, z)
        m.orientedBox(f.o, f.dir(1.0, 0.0, 0.0), f.dir(0.0, 1.0, 0.0), f.dir(0.0, 0.0, 1.0), w / 2, h / 2, d / 2, color, emissive)
    }
    /** 以 (x, y, z) 为中心的盒子。 */
    fun boxC(w: Double, h: Double, d: Double, x: Double, y: Double, z: Double, color: FloatArray, emissive: Float = 0f) = boxAt(w, h, d, x, y - h / 2, z, color, emissive)
    fun cylinderAt(radius: Double, h: Double, x: Double, y: Double, z: Double, color: FloatArray, rTop: Double = radius, segments: Int = 12, emissive: Float = 0f) =
        m.ktube(pt(x, y, z), pt(x, y + h, z), radius, color, rTop, segments, emissive)
    fun tube(a: DoubleArray, b: DoubleArray, radius: Double, color: FloatArray, segments: Int = 8, rEnd: Double = radius) = m.ktube(pt(a), pt(b), radius, color, rEnd, segments)
    /** 本地 xy 平面上的圆环（TorusGeometry，轴沿本地 z）：中心 c，主半径 R，管半径 t。 */
    fun torus(c: DoubleArray, big: Double, t: Double, color: FloatArray, segments: Int = 20) {
        for (i in 0 until segments) {
            val a0 = 2 * PI * i / segments; val a1 = 2 * PI * (i + 1) / segments
            tube(v3(c[0] + cos(a0) * big, c[1] + sin(a0) * big, c[2]), v3(c[0] + cos(a1) * big, c[1] + sin(a1) * big, c[2]), t, color, 4)
        }
    }
    /** 本地 xy 平面上的多边形（朝本地 +z），扇形三角。 */
    fun polygon(pts: List<DoubleArray>, color: FloatArray, emissive: Float = 0f) {
        val n = norm(dir(0.0, 0.0, 1.0)); val c = pt(pts.fold(v3(0.0, 0.0, 0.0)) { acc, p -> add(acc, p, 1.0 / pts.size) })
        for (i in pts.indices) { val a = pt(pts[i]); val b = pt(pts[(i + 1) % pts.size]); m.quad(c, a, b, b, n, color, emissive) }
    }
    /** 本地竖直平面（PlaneGeometry，朝本地 +z）。 */
    fun plane(w: Double, h: Double, x: Double, y: Double, z: Double, color: FloatArray, emissive: Float = 0f) =
        polygon(listOf(v3(x - w / 2, y - h / 2, z), v3(x + w / 2, y - h / 2, z), v3(x + w / 2, y + h / 2, z), v3(x - w / 2, y + h / 2, z)), color, emissive)

    companion object {
        fun rotX(a: Double) = doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))
        fun rotY(a: Double) = doubleArrayOf(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a))
        fun rotZ(a: Double) = doubleArrayOf(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
        fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> val row = i / 3; val col = i % 3; a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col] }
    }
}

/** 下垂的电线（sagWire：抛物线近似，16 段）。 */
internal fun MachiMesh.sagWire(a: DoubleArray, b: DoubleArray, sag: Double, color: FloatArray, radius: Double = 0.012) {
    var prev = a
    for (i in 1..16) {
        val t = i / 16.0
        val p = v3(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t)
        ktube(prev, p, radius, color, segments = 4); prev = p
    }
}

private val KONBINI_BIKE_COLORS = listOf("#c8423a", "#2f6db0", "#e7c64a", "#3f8f5a", "#e9e6dc", "#2a2d34")
private val KONBINI_UMBRELLA_COLORS = listOf("#e8ecf2", "#2f6db0", "#d24d6e", "#3e4655", "#f0b23e")

/** 招牌模块要贴字的面：中心 x, y, z、朝向、宽、高（立柱灯箱两面、A 字立牌一面、贩卖机陈列窗两块）。 */
internal class MachiKonbiniPropFaces(val pylon: List<DoubleArray>, val aFrame: List<DoubleArray>, val vending: List<DoubleArray>)

internal fun addMachiKonbiniProps(m: MachiMesh, palette: MachiPalette, seed: String, variation: MachiKonbiniVariation, stats: MutableMap<String, Int>): MachiKonbiniPropFaces {
    val L = MachiKonbiniLayout
    val world = MachiFrame(m)
    fun art(k: String) = machiRgb(L.ART.getValue(k))
    val dark = machiRgb(palette.darkMetal); val metal = machiRgb(palette.metal); val rubber = machiRgb(palette.rubber)
    val white = floatArrayOf(1f, 1f, 1f); val trim = art("trim"); val acBody = art("acUnit")
    val lot = L.LOT_Y
    val products = palette.products.map { machiRgb(it) }
    // 自动贩卖机（正面朝本地 +z，底面中心在原点），立在店右墙外，转 90° 朝 +x。
    val vw = 1.0; val vd = 0.74; val vh = 1.83; val vx = 0.82
    val vendingFaces = ArrayList<DoubleArray>()
    for (z in doubleArrayOf(-3.25, -4.3)) {
        val f = world.child(vx + vd / 2 + 0.02, lot, z, ry = PI / 2)
        f.boxAt(vw, vh, vd, 0.0, 0.0, 0.0, machiRgb(palette.vendingBody))
        f.boxAt(vw + 0.04, 0.08, vd + 0.04, 0.0, 0.0, 0.0, dark)
        f.boxAt(vw - 0.08, 0.06, 0.02, 0.0, vh - 0.12, vd / 2, machiRgb(palette.vendingAccent))
        f.boxAt(vw - 0.3, 0.2, 0.04, -0.08, 0.16, vd / 2 - 0.01, dark)
        f.boxAt(0.18, 0.42, 0.03, vw / 2 - 0.17, 0.62, vd / 2, metal)
        val wh = 0.86
        f.plane(vw - 0.12, wh, 0.0, vh - 0.2 - wh / 2, vd / 2 - 0.05, machiRgb(palette.vendingLight), 1.7f)
        for (i in 0 until 16) {
            val row = i / 8
            f.boxC(0.05, 0.022, 0.02, -vw / 2 + 0.1 + (i % 8) * ((vw - 0.2) / 7), vh - 0.2 - (if (row == 0) 0.42 else wh + 0.05), vd / 2 - 0.02, machiRgb(palette.brand[0]), 1.0f)
        }
        for (row in 0 until 2) for (col in 0 until 8) {
            val c = products[(col + row * 3) % 8 % products.size]
            f.cylinderAt(0.035, 0.14, -vw / 2 + 0.1 + col * ((vw - 0.2) / 7), vh - 0.2 - wh + 0.08 + row * 0.44, vd / 2 - 0.12, machiShade(c, 1.1), segments = 8)
        }
        vendingFaces += doubleArrayOf(vx + vd / 2 + 0.02 + vd / 2 + 0.004, lot + vh - 0.2 - 0.43, z, PI / 2, vw - 0.12, 0.86)
        stats["vending"] = (stats["vending"] ?: 0) + 1
    }
    // 空调外机：屋顶两台、侧墙两台（风扇叶先按静止摆，转动后接）。
    fun acUnit(f: MachiFrame) {
        f.boxAt(0.82, 0.62, 0.3, 0.0, 0.04, 0.0, acBody)
        f.boxAt(0.86, 0.04, 0.34, 0.0, 0.0, 0.0, dark)
        f.tube(v3(-0.12, 0.36, 0.14), v3(-0.12, 0.36, 0.16), 0.22, dark, 20)
        val fan = f.child(-0.12, 0.36, 0.165)
        for (i in 0 until 3) { val a = i * 2 * PI / 3; fan.child(cos(a) * 0.09, sin(a) * 0.09, 0.0, rz = a).boxC(0.18, 0.07, 0.01, 0.0, 0.0, 0.0, metal) }
        f.boxAt(0.04, 0.3, 0.04, 0.32, 0.1, -0.14, dark)
        stats["ac"] = (stats["ac"] ?: 0) + 1
    }
    acUnit(world.child(-0.1, L.ROOF_TOP, -4.75))
    acUnit(world.child(-4.85, L.ROOF_TOP, -0.35, ry = PI / 2))
    for (z in doubleArrayOf(-2.0, -3.1)) acUnit(world.child(-5.7, lot, z, ry = -PI / 2))
    // 路灯：锥形灯杆、两段灯臂、灯头、底面发光（glow lampHead = streetLight × 3.2）。
    run {
        val f = world.child(L.LAMP_X, 0.0, L.LAMP_Z)
        val hgt = L.LAMP_HEIGHT; val arm = L.LAMP_ARM
        f.cylinderAt(0.085, hgt, 0.0, lot, 0.0, metal, rTop = 0.055, segments = 10)
        f.cylinderAt(0.12, 0.3, 0.0, lot, 0.0, dark, segments = 10)
        f.tube(v3(0.0, lot + hgt - 0.15, 0.0), v3(-arm * 0.55, lot + hgt + 0.12, 0.0), 0.04, metal)
        f.tube(v3(-arm * 0.55, lot + hgt + 0.12, 0.0), v3(-arm, lot + hgt + 0.08, 0.0), 0.04, metal)
        f.boxAt(0.56, 0.1, 0.24, -arm - 0.12, lot + hgt, 0.0, dark)
        // 灯罩朝下（PlaneGeometry 绕 x 转 +90° 朝 −y）。
        f.child(-arm - 0.12, lot + hgt - 0.003, 0.0, rx = PI / 2).plane(0.46, 0.16, 0.0, 0.0, 0.0, machiRgb(palette.streetLight), 3.2f)
    }
    // 电线杆：混凝土杆、黄黑护套、变压器、两层横担与瓷瓶；电线往后、往前出底座，另一根接到店的前左角。
    run {
        val x = -5.3; val z = 1.96; val hgt = 7.6; val top = lot + hgt
        world.cylinderAt(0.15, hgt, x, lot, z, machiRgb(palette.pole), rTop = 0.11, segments = 14)
        world.cylinderAt(0.165, 1.6, x, lot, z, machiRgb(palette.caution), segments = 14)
        world.cylinderAt(0.2, 0.62, x + 0.32, top - 2.2, z, acBody, segments = 12)
        world.tube(v3(x, top - 1.9, z), v3(x + 0.32, top - 1.9, z), 0.03, dark)
        val ins = ArrayList<DoubleArray>()
        for ((level, yy) in listOf(top - 0.7, top - 0.25).withIndex()) {
            val span = if (level == 0) 0.9 else 1.2
            m.box(x - span / 2, x + span / 2, yy - 0.05, yy + 0.05, z - 0.06, z + 0.06, dark)
            for (off in doubleArrayOf(-span / 2 + 0.08, 0.0, span / 2 - 0.08)) { if (off == 0.0 && level == 1) continue; ins += v3(x + off, yy + 0.05, z) }
        }
        val wire = machiRgb(palette.wire)
        ins.forEachIndexed { i, p ->
            world.cylinderAt(0.04, 0.1, p[0], p[1], p[2], white, rTop = 0.025, segments = 8)
            val end = v3(p[0], p[1] + 0.09, p[2])
            m.sagWire(end, v3(end[0] + (i - 2) * 0.03, end[1] - 0.3, -L.BASE_HALF), 0.55 + i * 0.04, wire)
            m.sagWire(end, v3(end[0] + (i - 2) * 0.03, end[1] - 0.2, L.BASE_HALF), 0.35 + i * 0.03, wire)
        }
        m.sagWire(v3(x + 0.1, top - 2.6, z), v3(L.X0 + 0.1, L.PARAPET_TOP - 0.1, L.Z1 - 0.1), 0.35, wire, 0.01)
        world.boxAt(0.03, 0.42, 0.14, x + 0.15, lot + 1.9, z, machiRgb(palette.brand[2]))
        stats["insulator"] = ins.size
    }
    // 立柱灯箱：两根立柱托着双面灯箱，转 45°。
    val pylonFaces = ArrayList<DoubleArray>()
    run {
        val bw = 1.35; val bh = 1.25; val bd = 0.32; val hgt = L.PYLON_HEIGHT; val rot = 45 * MACHI_DEG
        val f = world.child(L.PYLON_X, 0.0, L.PYLON_Z, ry = rot)
        f.boxAt(0.12, hgt - bh, 0.12, -0.42, lot, 0.0, dark)
        f.boxAt(0.12, hgt - bh, 0.12, 0.42, lot, 0.0, dark)
        f.boxAt(bw, bh, bd, 0.0, lot + hgt - bh, 0.0, machiRgb(palette.signBase))
        f.boxAt(bw + 0.04, 0.1, bd + 0.04, 0.0, lot + hgt - 0.04, 0.0, machiRgb(palette.brand[0]))
        f.boxAt(bw + 0.02, 0.07, bd + 0.02, 0.0, lot + hgt - bh, 0.0, machiRgb(palette.brand[2]))
        f.boxAt(1.0, 0.18, 0.4, 0.0, lot, 0.0, machiRgb(palette.concrete))
        val cy = lot + hgt - bh / 2 - 0.02
        for (turn in doubleArrayOf(0.0, PI)) {
            val s = if (turn == 0.0) 1.0 else -1.0
            pylonFaces += doubleArrayOf(L.PYLON_X + sin(rot) * s * (bd / 2 + 0.006), cy, L.PYLON_Z + cos(rot) * s * (bd / 2 + 0.006), rot + turn, bw - 0.12, bh - 0.22)
        }
    }
    // 反光镜：橙色立杆、支架、圆镜（朝 125°、前倾 12°）。
    run {
        val pole = art("mirrorPole"); val x = -1.2; val z = 5.74; val hgt = 2.7
        val f = world.child(x, 0.0, z)
        f.cylinderAt(0.035, hgt, 0.0, lot, 0.0, pole, segments = 8)
        f.tube(v3(0.0, lot + hgt - 0.1, 0.0), v3(0.2, lot + hgt - 0.1, -0.14), 0.025, pole)
        val mf = f.child(0.26, lot + hgt - 0.1, -0.18, ry = 125 * MACHI_DEG).child(0.0, 0.0, 0.0, rx = -12 * MACHI_DEG)
        mf.torus(v3(0.0, 0.0, 0.0), 0.3, 0.035, pole, 16)
        mf.polygon(List(16) { i -> v3(cos(2 * PI * i / 16) * 0.29, sin(2 * PI * i / 16) * 0.29, 0.0) }, metal)
    }
    // 停车标志：倒三角红牌、白边，转 60°。
    run {
        val f = world.child(1.95, 0.0, 5.74, ry = 60 * MACHI_DEG); val hgt = 2.4; val r = 0.36
        f.cylinderAt(0.03, hgt, 0.0, lot, 0.0, metal, segments = 8)
        val tri = List(3) { i -> val a = -PI / 2 + i * 2 * PI / 3; v3(cos(a) * r, sin(a) * r, 0.0) }
        val back = f.child(0.0, lot + hgt - 0.15, 0.03)
        back.polygon(tri.map { v3(it[0] * 1.08, it[1] * 1.08, 0.04) }, white)
        back.child(0.0, 0.0, 0.0, ry = PI).polygon(tri.map { v3(-it[0] * 1.08, it[1] * 1.08, 0.0) }, white)
        f.child(0.0, lot + hgt - 0.15 - 0.012, 0.08).polygon(tri.map { v3(it[0] * 0.86, it[1] * 0.86, 0.0) }, machiRgb(palette.stopRed))
    }
    // 分类垃圾箱：三个，盖子是招牌色。
    listOf(machiRgb(palette.brand[0]), machiRgb(palette.brand[2]), trim).forEachIndexed { i, lid ->
        world.boxAt(0.34, 0.82, 0.36, -3.55 + (i - 1) * 0.37, lot, 0.68, acBody)
        world.boxAt(0.36, 0.08, 0.38, -3.55 + (i - 1) * 0.37, lot + 0.82, 0.68, lid)
    }
    // A 字立牌：两块斜靠的板，转 30°。
    val aFrameFaces = ArrayList<DoubleArray>()
    run {
        val lean = 12 * MACHI_DEG; val bw = 0.56; val bh = 0.82; val rot = 30 * MACHI_DEG
        val f = world.child(0.95, 0.0, 1.15, ry = rot)
        for (side in intArrayOf(1, -1)) f.child(0.0, lot + cos(lean) * bh / 2, side * sin(lean) * bh / 2, rx = side * -lean).boxC(bw, bh, 0.03, 0.0, 0.0, 0.0, dark)
        val off = sin(lean) * bh / 2 + 0.02
        aFrameFaces += doubleArrayOf(0.95 + sin(rot) * off, lot + cos(lean) * bh / 2, 1.15 + cos(rot) * off, rot, bw - 0.06, bh - 0.08, lean)
    }
    // 按歌变的：自行车（一到两辆）、伞架里的伞、地上晾着的伞。
    val bikeColor = machiRgb(KONBINI_BIKE_COLORS[machiSeedIndex(seed, "bike", KONBINI_BIKE_COLORS.size)])
    val umbrella = machiRgb(KONBINI_UMBRELLA_COLORS[machiSeedIndex(seed, "umbrella", KONBINI_UMBRELLA_COLORS.size)])
    fun bicycle(f0: MachiFrame) {
        val f = f0.child(0.0, 0.0, 0.0, rx = -4 * MACHI_DEG)
        val wr = 0.33; val rearX = -0.55; val frontX = 0.58; val axleY = wr + 0.03
        for (wx in doubleArrayOf(rearX, frontX)) {
            val c = v3(wx, axleY, 0.0)
            f.torus(c, wr, 0.028, rubber, 20)
            f.torus(c, wr - 0.03, 0.01, metal, 16)
            f.tube(v3(wx, axleY, -0.045), v3(wx, axleY, 0.045), 0.03, metal)
            for (i in 0 until 6) { val a = i * PI / 6; val rr = wr - 0.03; f.tube(v3(wx + cos(a) * rr, axleY + sin(a) * rr, 0.0), v3(wx - cos(a) * rr, axleY - sin(a) * rr, 0.0), 0.004, metal, 3) }
        }
        val bb = v3(-0.05, 0.3, 0.0); val seatTop = v3(-0.25, 0.86, 0.0); val headTop = v3(0.42, 0.95, 0.0); val headLow = v3(0.48, 0.66, 0.0)
        f.tube(bb, seatTop, 0.022, bikeColor); f.tube(bb, headLow, 0.026, bikeColor); f.tube(headLow, headTop, 0.024, bikeColor)
        f.tube(bb, v3(rearX, axleY, 0.05), 0.014, bikeColor); f.tube(v3(-0.22, 0.78, 0.0), v3(rearX, axleY, 0.05), 0.014, bikeColor)
        f.tube(headLow, v3(frontX, axleY, 0.0), 0.016, metal)
        f.boxC(0.24, 0.06, 0.13, seatTop[0] - 0.02, seatTop[1], 0.0, rubber)
        f.tube(v3(headTop[0] - 0.12, headTop[1] + 0.08, -0.26), v3(headTop[0] - 0.12, headTop[1] + 0.08, 0.26), 0.014, metal)
        f.tube(headTop, v3(headTop[0] - 0.1, headTop[1] + 0.08, 0.0), 0.016, metal)
        f.boxC(0.42, 0.09, 0.025, -0.28, 0.28, 0.07, bikeColor)
        f.boxC(0.38, 0.02, 0.2, rearX - 0.02, axleY + 0.36, 0.0, metal)
        f.boxC(0.32, 0.22, 0.3, frontX + 0.04, 0.82, 0.0, metal)
        f.tube(v3(rearX + 0.1, axleY - 0.05, 0.04), v3(rearX + 0.25, 0.01, 0.14), 0.01, metal)
        stats["bike"] = (stats["bike"] ?: 0) + 1
    }
    val slots = listOf(doubleArrayOf(-4.5, 1.32), doubleArrayOf(-2.85, 1.36))
    val first = slots[variation.bikeSlot]
    bicycle(world.child(first[0], lot, first[1], ry = PI + variation.bikeYaw))
    if (variation.secondBike) { val o = slots[1 - variation.bikeSlot]; bicycle(world.child(o[0], lot, o[1] + 0.05, ry = PI - variation.bikeYaw * 0.7)) }
    run {
        val x = -1.62; val z = 0.68
        val f = world.child(x, 0.0, z)
        f.boxAt(0.36, 0.5, 0.22, 0.0, lot, 0.0, metal)
        if (variation.umbrellaInStand) doubleArrayOf(-0.09, 0.04, 0.11).forEachIndexed { i, off ->
            // ConeGeometry(0.05, 0.72) 倒过来插着：中心在 lot + 0.4，尖朝下。
            val cf = f.child(off, lot + 0.4, (i - 1) * 0.04, rx = PI, rz = (i - 1) * 0.12)
            cf.tube(v3(0.0, -0.36, 0.0), v3(0.0, 0.36, 0.0), 0.05, if (i == 1) trim else umbrella, 8, 0.0)
        }
        if (variation.openUmbrella) {
            val of = world.child(x - 0.75, lot + 0.32, z + 0.55, rx = 0.9, ry = 0.6, rz = 0.4)
            of.tube(v3(0.0, -0.14, 0.0), v3(0.0, 0.14, 0.0), 0.48, umbrella, 8, 0.0)
            of.tube(v3(0.0, 0.14, 0.0), v3(0.0, -0.45, 0.0), 0.01, dark)
        }
    }
    return MachiKonbiniPropFaces(pylonFaces, aFrameFaces, vendingFaces)
}
