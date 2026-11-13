// 町景「便利店街角」（Folia machi scenes/konbini）白模起步：layout.ts 的尺寸表逐项照抄；base.ts 的台座 / 两条路 / 路缘 /
// 场地 / 斑马线与路边线 / 井盖 / 排水沟盖板，store.ts 的地坪、实墙与内衬、落地玻璃与竖框、对开自动门（先按关着摆）、
// 屋顶与女儿墙、落水管、店招带（底板 + 三色条）、屋顶三面广告塔（机箱、横梁、霓虹顶沿、顶盖、立柱与斜撑）照译。
// 店内（interior.ts）、道具（props.ts）、招牌字面（konbiniSigns.ts）、灯（konbiniLights.ts）、门动画（konbiniDoor.ts）后续接。
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
internal fun MachiMesh.tube(a: DoubleArray, b: DoubleArray, r0: Double, color: FloatArray, r1: Double = r0, segments: Int = 8, emissive: Float = 0f) {
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
    tube(v3(x, y, z), v3(x, y + h, z), r, color, rTop, segments, emissive)

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
        m.tube(v3(px, L.LOT_Y + 0.22, pz), v3(px - 0.05, L.LOT_Y + 0.06, pz + 0.2), 0.045, metal)
        m.tube(v3(px, top - 0.02, pz), v3(px + 0.18, top - 0.02, pz + 0.2), 0.04, metal)
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
            m.tube(v3(brace[0], L.ROOF_TOP, brace[2]), v3(foot[0], L.BOARD_Y0 - 0.1, foot[2]), 0.03, darkMetal)
            m.box(brace[0] - 0.14, brace[0] + 0.14, L.ROOF_TOP, L.ROOF_TOP + 0.06, brace[2] - 0.14, brace[2] + 0.14, trim)
        }
    }
    return MachiKonbiniModel(m.toArray(), g.toArray(), boardFaces, fasciaFaces, stats)
}
