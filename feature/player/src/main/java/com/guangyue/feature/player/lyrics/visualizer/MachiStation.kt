// 町景「加油站停车场」（Folia machi scenes/station）白模起步：layout.ts 的尺寸表逐项照抄；structures.ts 的底座与路
// （路缘与两处出入口、车道线、停车位线、车轮挡、井盖）、抬高的加油区、罩棚（柱子、顶板、品牌条、底面灯板）、两座加油岛
// 与四台加油机、价格立柱、汽车旅馆高立牌（竖灯箱、横牌、VACANCY、箭头）、场地灯、小道具；vehicles.ts 的停着的车
// （轿车 / 出租车 / 轻卡）与后排アドトラック照译。stationSeed.ts 的按歌变化照抄（同一条 mulberry32 序列，与 Folia 对齐）。
// 招牌字面（stationSigns.ts）、灯（stationLights.ts）、双闪、车下软影、のぼり旗、车流、白气后接。
// 圆角盒按直角盒画；圆柱按 N 棱柱；箭头（凹多边形）拆成两条横竖杆 + 三角箭头。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object MachiStationLayout {
    const val ROAD_Y = 0.0; const val LOT_Y = 0.02; const val APRON_Y = 0.1
    // ROAD
    const val ROAD_Z0 = 2.35; const val ROAD_Z1 = 6.0; const val ROAD_CURB = 0.16
    val LANES = doubleArrayOf(3.36, 4.99)
    val GAPS = listOf(doubleArrayOf(-1.2, 0.8), doubleArrayOf(1.8, 3.6))
    // CANOPY
    const val C_X0 = 1.4; const val C_X1 = 5.9; const val C_Z0 = -5.6; const val C_Z1 = -1.4; const val C_UNDER = 3.85; const val C_TOP = 4.3
    val COLUMNS = listOf(doubleArrayOf(2.05, -2.1), doubleArrayOf(5.25, -2.1), doubleArrayOf(2.05, -4.9), doubleArrayOf(5.25, -4.9))
    // ISLANDS
    const val I_X0 = 2.45; const val I_X1 = 4.85; val I_Z = doubleArrayOf(-4.35, -2.75); const val I_W = 0.5; const val I_H = 0.16
    val PUMPS = doubleArrayOf(3.05, 4.25)
    // APRON
    const val A_X0 = 1.2; const val A_X1 = 6.0; const val A_Z0 = -6.0; const val A_Z1 = 0.4
    // TRUCK
    const val T_REAR = -5.85; const val T_FRONT = 1.1; const val T_Z0 = -5.7; const val T_Z1 = -3.6; const val T_DECK = 1.0
    const val T_BOX_TOP = 3.4; const val T_BOX_FRONT = -0.7; const val T_CAB_BACK = -0.55; const val T_CAB_TOP = 2.75
    // TRUCK_SCREEN
    const val TS_X = (T_REAR + T_BOX_FRONT) / 2
    const val TS_Y = (T_DECK + 0.22 + T_BOX_TOP - 0.16) / 2
    const val TS_Z = T_Z1 + 0.03
    const val TS_W = T_BOX_FRONT - T_REAR - 0.36
    const val TS_H = T_BOX_TOP - 0.16 - (T_DECK + 0.22)
    // STALLS / CAR
    val STALL_X = doubleArrayOf(-4.75, -2.85, -0.95); const val STALL_Z = -0.3; const val STALL_W = 1.9; const val STALL_D = 3.6
    const val CAR_L = 3.2; const val CAR_W = 1.42
    val PUMP_CAR = doubleArrayOf(3.65, -0.95); val KEI_TRUCK = doubleArrayOf(3.1, 1.1)
    // APRON_PROPS
    val AIR_PUMP = doubleArrayOf(1.6, -0.75, 0.36, 0.3); val BINS_X = 1.6; val BINS_Z = doubleArrayOf(-1.2, -1.57); const val BIN_SIZE = 0.34
    val TIRES = doubleArrayOf(5.6, -2.9, 0.37)
    // PYLON / MOTEL / LOT_LAMP
    const val PY_X = 5.35; const val PY_Z = 1.75; const val PY_H = 4.1; const val PY_W = 1.0
    const val MO_X = -5.25; const val MO_Z = 1.85; const val MO_H = 6.4
    const val LL_X = 0.9; const val LL_Z = 1.85; const val LL_H = 5.0; const val LL_ARM = 1.2
    /** STATION_VIEW。 */
    val VIEW = MachiView(25 * MACHI_DEG, 30 * MACHI_DEG, 1.48)
    /** stationScene reflectionPlaneY。 */
    const val REFLECT_Y = 0.04
    /** 场地灯头（buildLotLamp head）。 */
    val LAMP_HEAD = doubleArrayOf(LL_X - LL_ARM - 0.1, ROAD_Y + LL_H - 0.06, LL_Z - 0.45)
    val ART = mapOf(
        "canopyFascia" to "#f3f1eb", "canopyTop" to "#aeb4bc", "canopyUnder" to "#d4d7dd", "canopyColumn" to "#e5e3dd",
        "canopyLight" to "#eef4ff", "pumpBody" to "#ecebe6", "truckBox" to "#e9ecef", "carGlass" to "#1b2230",
        "motelBack" to "#1b333d", "motelPole" to "#bdb6a8", "marker" to "#ffae42",
    )
    val CAR_COLORS = listOf("#e8eaee", "#aeb5bf", "#25292f", "#22385f", "#b8322e", "#2f5a48", "#d8cdb4")
    val TAXI_LIVERIES = listOf("#f1c232" to "#2b2f36", "#2a2d33" to "#e8a23a", "#e9ecef" to "#2f8a4f", "#2f6fb0" to "#f1f1ec")
}

/** stationSeed.ts resolveStationVariation（同样的取数顺序）。stalls：taxi / sedan / empty。 */
internal data class MachiStationVariation(
    val stalls: List<String>, val carColors: List<Int>, val pumpColor: Int, val keiColor: Int, val taxiLivery: Int,
    val pumpCar: Boolean, val keiTruck: Boolean, val hazardStall: Int, val windYaw: Double, val windSlant: Double,
    val puddleX: Double, val puddleZ: Double, val puddleAmount: Double,
)

private val STATION_STALL_PATTERNS = listOf(
    listOf("taxi", "sedan", "taxi"), listOf("sedan", "taxi", "taxi"), listOf("taxi", "taxi", "sedan"),
    listOf("taxi", "empty", "taxi"), listOf("sedan", "taxi", "empty"), listOf("empty", "taxi", "sedan"),
)

internal fun resolveMachiStationVariation(seed: String): MachiStationVariation {
    val r = MachiRng(machiHashSeed("station:$seed"))
    val colorCount = MachiStationLayout.CAR_COLORS.size; val liveryCount = MachiStationLayout.TAXI_LIVERIES.size
    fun pick(a: Double, b: Double) = a + (b - a) * r.next()
    val stalls = STATION_STALL_PATTERNS[(r.next() * STATION_STALL_PATTERNS.size).toInt()]
    val occupied = stalls.indices.filter { stalls[it] == "sedan" }
    val carColors = stalls.map { (r.next() * colorCount).toInt() }
    val pumpColor = (r.next() * colorCount).toInt()
    val keiColor = (r.next() * colorCount).toInt()
    val taxiLivery = (r.next() * liveryCount).toInt()
    val pumpCar = r.next() < 0.7
    val keiTruck = r.next() < 0.6
    // 照原文：occupied 为空时 `occupied.length && …` 短路，不取随机数。
    val hazard = if (occupied.isNotEmpty() && r.next() < 0.75) occupied[(r.next() * occupied.size).toInt()] else -1
    return MachiStationVariation(
        stalls, carColors, pumpColor, keiColor, taxiLivery, pumpCar, keiTruck, hazard,
        windYaw = pick(-0.6, 0.6), windSlant = pick(0.06, 0.2), puddleX = pick(-40.0, 40.0), puddleZ = pick(-40.0, 40.0), puddleAmount = pick(0.45, 0.75),
    )
}

/** structures.ts stationFramingPoints：底座四角（后、左两边连底）、罩棚顶三角、旅馆立牌顶、货车顶两角。 */
internal fun machiStationFramingPoints(): List<MachiVec3> {
    val S = MachiStationLayout
    val h = 6.0
    val pts = ArrayList<MachiVec3>()
    for (x in listOf(-h, h)) for (z in listOf(-h, h)) {
        pts += MachiVec3(x, 0.0, z)
        if (z < 0 || x < 0) pts += MachiVec3(x, -0.6, z)
    }
    pts += MachiVec3(S.C_X0, S.C_TOP, S.C_Z0); pts += MachiVec3(S.C_X1, S.C_TOP, S.C_Z0); pts += MachiVec3(S.C_X1, S.C_TOP, S.C_Z1)
    pts += MachiVec3(S.MO_X, S.MO_H + 0.2, S.MO_Z)
    pts += MachiVec3(-5.9, 3.5, -5.7); pts += MachiVec3(1.1, 3.5, -5.7)
    return pts
}

/** 场地灯的光柱（stationScene：apex 灯头，target (x − 0.4, road, z − 0.6)，半径 1.6）。 */
internal fun machiStationLightCones(): List<MachiLightConeSpec> {
    val h = MachiStationLayout.LAMP_HEAD
    return listOf(MachiLightConeSpec(h, doubleArrayOf(h[0] - 0.4, MachiStationLayout.ROAD_Y, h[2] - 0.6), 1.6, 1.0))
}

/** 旅馆立牌的局部 → 世界（绕 y 转方位角 25°，原点在立柱脚）。 */
private fun motelWorld(x: Double, y: Double, z: Double): DoubleArray {
    val a = MachiStationLayout.VIEW.azimuth
    return doubleArrayOf(MachiStationLayout.MO_X + cos(a) * x + sin(a) * z, y, MachiStationLayout.MO_Z - sin(a) * x + cos(a) * z)
}

/** 场景的天气布置（stationScene weather；滴水的随机抖动走种子序列）。 */
internal fun resolveMachiStationWeather(seed: String): Pair<MachiPuddles, MachiWeatherLayout> {
    val S = MachiStationLayout
    val v = resolveMachiStationVariation(seed)
    val r = MachiRng(machiHashSeed("station:drips:$seed"))
    val drips = ArrayList<MachiDripSpec>()
    var x = S.C_X0 + 0.2
    while (x < S.C_X1) { drips += MachiDripSpec(x + (r.next() - 0.5) * 0.1, S.C_Z1 + 0.06, S.C_UNDER - 0.02, S.APRON_Y, 1.0 + r.next() * 1.6, r.next()); x += 0.45 }
    var z = S.C_Z1
    while (z > S.C_Z0 + 0.3) { drips += MachiDripSpec(S.C_X1 + 0.06, z + (r.next() - 0.5) * 0.1, S.C_UNDER - 0.02, S.APRON_Y, 1.0 + r.next() * 1.6, r.next()); z -= 0.5 }
    val lamp = S.LAMP_HEAD
    val tallY0 = S.MO_H - 2.55
    val glow = motelWorld(0.8, tallY0 + 0.5, 0.6)
    val layout = MachiWeatherLayout(
        half = 6.0,
        groundY = S.APRON_Y * 0.5,
        roofs = listOf(
            MachiRoof(S.C_X0 - 0.05, S.C_Z0 - 0.05, S.C_X1 + 0.05, S.C_Z1 + 0.05, S.C_TOP),
            MachiRoof(S.T_REAR, S.T_Z0, S.T_BOX_FRONT, S.T_Z1, S.T_BOX_TOP),
            MachiRoof(S.T_CAB_BACK, S.T_Z0, S.T_FRONT, S.T_Z1, S.T_CAB_TOP),
        ),
        drips = drips,
        glints = listOf(
            doubleArrayOf(lamp[0] - 0.3, 2.4, lamp[2] - 0.5, 1.7),
            doubleArrayOf((S.C_X0 + S.C_X1) / 2, 2.0, S.C_Z1 + 0.6, 2.2),
            doubleArrayOf(glow[0], glow[1], glow[2], 1.6),
        ),
        windYaw = v.windYaw,
        windSlant = v.windSlant,
    )
    return MachiPuddles(v.puddleX, v.puddleZ, v.puddleAmount) to layout
}

/** 雾里的两处灯（stationScene glowA / glowB）：罩棚下、场地灯下。 */
internal fun machiStationGlows(): Pair<FloatArray, FloatArray> {
    val S = MachiStationLayout
    return floatArrayOf(((S.C_X0 + S.C_X1) / 2).toFloat(), 0f, ((S.C_Z0 + S.C_Z1) / 2 + 1).toFloat(), 3.6f) to
        floatArrayOf((S.LAMP_HEAD[0] - 0.4).toFloat(), 0f, (S.LAMP_HEAD[2] - 0.6).toFloat(), 2.3f)
}

/** 车站白模：不透明网格；招牌面留给招牌模块（货车屏、出租车顶灯、罩棚前右、价格立柱两面、旅馆三块）。 */
internal class MachiStationModel(
    val mesh: FloatArray,
    val stats: Map<String, Int>,
    /** 出租车顶灯 LED 面：x, y, z, w, h（朝 +z）。 */
    val andons: List<DoubleArray>,
    /** 罩棚前、右招牌面：x, y, z, rotY, w, h。 */
    val canopySigns: List<DoubleArray>,
    /** 价格立柱两面：x, y, z, rotY, w, h。 */
    val pylonFaces: List<DoubleArray>,
    /** 旅馆立牌 tall / name / vacancy 三块：x, y, z, rotY, w, h。 */
    val motelFaces: List<DoubleArray>,
)

private fun v3s(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)

internal fun buildMachiStation(palette: MachiPalette, seed: String): MachiStationModel {
    val S = MachiStationLayout
    val m = MachiMesh()
    val world = MachiFrame(m)
    val stats = LinkedHashMap<String, Int>()
    fun count(k: String) { stats[k] = (stats[k] ?: 0) + 1 }
    fun art(k: String) = machiRgb(S.ART.getValue(k))
    val v = resolveMachiStationVariation(seed)
    val livery = S.TAXI_LIVERIES[v.taxiLivery]
    val colors = mapOf(
        "truckCab" to machiRgb(machiVivid(palette.brand[2], 0.5, 0.38, 0.5)),
        "taxiBody" to machiRgb(livery.first), "taxiTrim" to machiRgb(livery.second),
        "car0" to machiRgb(S.CAR_COLORS[v.carColors[0]]), "car1" to machiRgb(S.CAR_COLORS[v.carColors[1]]), "car2" to machiRgb(S.CAR_COLORS[v.carColors[2]]),
        "carPump" to machiRgb(S.CAR_COLORS[v.pumpColor]),
        "carKei" to machiRgb(S.CAR_COLORS[(v.keiColor + machiSeedIndex(seed, "kei", 2)) % S.CAR_COLORS.size]),
    )
    val asphalt = machiRgb(palette.asphalt); val concrete = machiRgb(palette.concrete); val curb = machiRgb(palette.curb)
    val paint = machiRgb(palette.roadPaint); val dark = machiRgb(palette.darkMetal); val metal = machiRgb(palette.metal)
    val rubber = machiRgb(palette.rubber); val caution = machiRgb(palette.caution)
    val brand0 = machiRgb(palette.brand[0]); val brand2 = machiRgb(palette.brand[2])
    val marker = art("marker"); val carGlass = art("carGlass")
    val h = 6.0

    // ---- 底座、路、停车场、加油区 ----
    m.box(-h, h, -0.1, 0.0, -h, h, machiRgb(palette.plinthTop))
    m.box(-h + 0.012, h - 0.012, -0.3, -0.1, -h + 0.012, h - 0.012, machiRgb(palette.plinthMid))
    m.box(-h + 0.006, h - 0.006, -0.52, -0.3, -h + 0.006, h - 0.006, machiRgb(palette.plinthLow))
    m.box(-h - 0.04, h + 0.04, -0.6, -0.5, -h - 0.04, h + 0.04, machiRgb(palette.plinthTrim))
    m.floorQuad(-h, h, -h, h, S.ROAD_Y + 0.002, asphalt)
    m.box(S.A_X0 + 0.002, S.A_X1 - 0.002, S.ROAD_Y, S.APRON_Y - 0.004, S.A_Z0 + 0.002, S.A_Z1 - 0.002, concrete)
    m.floorQuad(S.A_X0, S.A_X1, S.A_Z0, S.A_Z1, S.APRON_Y + 0.002, concrete)
    run {
        val z0 = S.ROAD_Z0 - S.ROAD_CURB
        var from = -h
        for (g in S.GAPS + listOf(doubleArrayOf(h, h))) {
            if (g[0] - from > 0.05) { m.box(from, g[0], S.ROAD_Y, 0.12, z0, S.ROAD_Z0, curb); count("curb") }
            from = g[1]
        }
    }
    run {
        val bars = ArrayList<DoubleArray>()
        val mid = (S.LANES[0] + S.LANES[1]) / 2
        var x = -h + 0.3
        while (x < h - 0.6) { bars += doubleArrayOf(x + 0.4, mid, 0.8, 0.1); x += 1.4 }
        bars += doubleArrayOf(0.0, S.ROAD_Z0 + 0.22, h * 2, 0.08)
        bars += doubleArrayOf(0.0, S.ROAD_Z1 - 0.2, h * 2, 0.08)
        val edges = listOf(S.STALL_X[0] - S.STALL_W / 2) + S.STALL_X.map { it + S.STALL_W / 2 }
        edges.forEach { bars += doubleArrayOf(it, S.STALL_Z, 0.09, S.STALL_D) }
        bars += doubleArrayOf((edges.first() + edges.last()) / 2, S.STALL_Z - S.STALL_D / 2, edges.last() - edges.first() + 0.09, 0.09)
        bars.forEach { b -> m.floorQuad(b[0] - b[2] / 2, b[0] + b[2] / 2, b[1] - b[3] / 2, b[1] + b[3] / 2, S.ROAD_Y + 0.006, paint); count("paint") }
        S.STALL_X.forEach { sx -> world.boxAt(0.9, 0.1, 0.16, sx, S.ROAD_Y, S.STALL_Z - S.STALL_D / 2 + 0.45, curb) }
        m.cylinderY(-2.6, S.ROAD_Y, mid + 0.4, 0.34, 0.02, dark, segments = 24)
    }

    // ---- 罩棚 ----
    val canopySigns = ArrayList<DoubleArray>()
    run {
        m.box(S.C_X0, S.C_X1, S.C_UNDER, S.C_TOP - 0.06, S.C_Z0, S.C_Z1, art("canopyUnder"))
        m.box(S.C_X0 - 0.04, S.C_X1 + 0.04, S.C_TOP - 0.08, S.C_TOP, S.C_Z0 - 0.04, S.C_Z1 + 0.04, art("canopyTop"))
        fun band(col: FloatArray, y0: Double, y1: Double, inset: Double) {
            val o = 0.02 + inset
            m.box(S.C_X0 - o, S.C_X1 + o, y0, y1, S.C_Z1 - 0.02, S.C_Z1 + o, col)
            m.box(S.C_X0 - o, S.C_X1 + o, y0, y1, S.C_Z0 - o, S.C_Z0 + 0.02, col)
            m.box(S.C_X1 - 0.02, S.C_X1 + o, y0, y1, S.C_Z0 - o, S.C_Z1 + o, col)
            m.box(S.C_X0 - o, S.C_X0 + 0.02, y0, y1, S.C_Z0 - o, S.C_Z1 + o, col)
        }
        band(brand0, S.C_UNDER - 0.02, S.C_UNDER + 0.14, 0.01)
        band(brand2, S.C_UNDER + 0.16, S.C_UNDER + 0.21, 0.012)
        S.COLUMNS.forEach { (x, z) ->
            world.boxAt(0.32, S.C_UNDER - S.APRON_Y, 0.32, x, S.APRON_Y, z, art("canopyColumn"))
            world.boxAt(0.34, 0.5, 0.34, x, 1.4, z, brand0)
            world.boxAt(0.5, 0.42, 0.5, x, S.APRON_Y, z, caution)
            count("column")
        }
        // 底面灯板：两排各三块（glow canopyLight × 2.4），朝下。
        val light = art("canopyLight")
        for (row in 0 until 2) for (col in 0 until 3) {
            val x = S.C_X0 + 0.85 + col * ((S.C_X1 - S.C_X0 - 1.7) / 2)
            val z = S.C_Z0 + 1.2 + row * (S.C_Z1 - S.C_Z0 - 2.4)
            world.child(x, S.C_UNDER - 0.005, z, rx = PI / 2).plane(0.9, 0.5, 0.0, 0.0, 0.0, light, 2.4f)
            count("canopyLight")
        }
        canopySigns += doubleArrayOf((S.C_X0 + S.C_X1) / 2, S.C_TOP - 0.2, S.C_Z1 + 0.035, 0.0, 2.3, 0.34)
        canopySigns += doubleArrayOf(S.C_X1 + 0.035, S.C_TOP - 0.2, (S.C_Z0 + S.C_Z1) / 2, PI / 2, 2.3, 0.34)
    }

    // ---- 加油岛与加油机 ----
    run {
        val screen = machiRgb(palette.led)
        S.I_Z.forEach { z ->
            m.box(S.I_X0, S.I_X1, S.APRON_Y, S.APRON_Y + S.I_H, z - S.I_W / 2, z + S.I_W / 2, concrete)
            doubleArrayOf(S.I_X0 + 0.12, S.I_X1 - 0.12).forEach { x -> m.cylinderY(x, S.APRON_Y + S.I_H, z, 0.06, 0.75, caution, segments = 10) }
            S.PUMPS.forEach { x ->
                val y0 = S.APRON_Y + S.I_H
                world.boxAt(0.55, 1.45, 0.36, x, y0, z, art("pumpBody"))
                world.boxAt(0.58, 0.24, 0.39, x, y0 + 1.45, z, brand0)
                for (side in intArrayOf(-1, 1)) {
                    world.child(x, y0 + 1.05, z + side * 0.185, ry = if (side < 0) PI else 0.0).plane(0.34, 0.2, 0.0, 0.0, 0.0, screen, 1.1f)
                    val holster = v3s(x + side * 0.3, y0 + 0.8, z)
                    world.boxAt(0.08, 0.2, 0.12, holster[0], holster[1] - 0.1, holster[2], dark)
                    val hoseTop = v3s(x + side * 0.2, y0 + 1.3, z); val hoseLow = v3s(x + side * 0.42, y0 + 0.35, z + 0.05)
                    world.tube(hoseTop, hoseLow, 0.025, dark); world.tube(hoseLow, holster, 0.025, dark)
                }
                count("pump")
            }
        }
    }

    // ---- 价格立柱（转 0.6 × 方位角） ----
    val pylonFaces = ArrayList<DoubleArray>()
    run {
        val rot = S.VIEW.azimuth * 0.6
        val f = world.child(S.PY_X, 0.0, S.PY_Z, ry = rot)
        val bb = 0.9; val bh = S.PY_H - bb
        f.boxAt(0.1, bb + 0.1, 0.14, -S.PY_W * 0.3, S.ROAD_Y, 0.0, dark)
        f.boxAt(0.1, bb + 0.1, 0.14, S.PY_W * 0.3, S.ROAD_Y, 0.0, dark)
        f.boxAt(S.PY_W, bh, 0.22, 0.0, bb, 0.0, art("canopyFascia"))
        f.boxAt(S.PY_W + 0.04, 0.08, 0.26, 0.0, S.PY_H - 0.02, 0.0, brand0)
        f.boxAt(S.PY_W + 0.2, 0.16, 0.5, 0.0, S.ROAD_Y, 0.0, concrete)
        for (side in intArrayOf(1, -1)) {
            val p = f.pt(0.0, bb + bh / 2, side * 0.115)
            pylonFaces += doubleArrayOf(p[0], p[1], p[2], rot + (if (side > 0) 0.0 else PI), S.PY_W - 0.08, bh - 0.1)
        }
    }

    // ---- 汽车旅馆高立牌（朝默认镜头，转方位角） ----
    val motelFaces = ArrayList<DoubleArray>()
    run {
        val rot = S.VIEW.azimuth
        val f = world.child(S.MO_X, 0.0, S.MO_Z, ry = rot)
        val back = art("motelBack"); val neon = machiRgb(palette.neon)
        f.cylinderAt(0.11, S.MO_H, 0.0, S.ROAD_Y, 0.0, art("motelPole"), rTop = 0.09, segments = 12)
        f.cylinderAt(0.3, 0.2, 0.0, S.ROAD_Y, 0.0, concrete, segments = 14)
        val tw = 0.86; val th = 2.5; val ty0 = S.MO_H - 2.55; val tx = 0.38
        f.boxAt(tw, th, 0.26, tx, ty0, 0.0, back)
        f.boxAt(tw + 0.06, 0.04, 0.3, tx, ty0 + th, 0.0, neon, 1.6f)
        f.boxAt(tw + 0.06, 0.04, 0.3, tx, ty0 - 0.04, 0.0, neon, 1.6f)
        val nw = 2.1; val nh = 0.62; val ny0 = ty0 - 0.85; val nx = 0.55
        f.boxAt(nw, nh, 0.26, nx, ny0, 0.0, back)
        val vw = 1.3; val vh = 0.34; val vy0 = ny0 - 0.5; val vx = 0.7
        f.boxAt(vw, vh, 0.24, vx, vy0, 0.0, back)
        // 箭头（深 0.12）：上横杆 + 竖杆 + 三角箭头。
        val ax = tx + tw / 2; val top = ty0 + th * 0.78; val neck = ny0 + nh + 0.28; val tip = ny0 + nh + 0.02
        f.boxC(0.85, 0.4, 0.12, ax + 0.425, top - 0.2, 0.0, brand0)
        f.boxC(0.4, top - 0.4 - neck, 0.12, ax + 0.65, (top - 0.4 + neck) / 2, 0.0, brand0)
        val tri = listOf(v3s(ax + 0.18, neck, 0.06), v3s(ax + 0.65, tip, 0.06), v3s(ax + 1.12, neck, 0.06))
        f.polygon(tri, brand0)
        f.child(0.0, 0.0, -0.12, ry = 0.0).polygon(tri.reversed(), brand0)
        fun face(x: Double, y: Double, w: Double, hh: Double, d: Double) { val p = f.pt(x, y, d); motelFaces += doubleArrayOf(p[0], p[1], p[2], rot, w, hh) }
        face(tx, ty0 + th / 2, tw - 0.12, th - 0.12, 0.135)
        face(nx, ny0 + nh / 2, nw - 0.1, nh - 0.1, 0.135)
        face(vx, vy0 + vh / 2, vw - 0.08, vh - 0.06, 0.125)
    }

    // ---- 场地灯 ----
    run {
        val pole = machiRgb(palette.pole); val top = S.ROAD_Y + S.LL_H
        m.cylinderY(S.LL_X, S.ROAD_Y, S.LL_Z, 0.085, S.LL_H, pole, rTop = 0.06, segments = 10)
        m.cylinderY(S.LL_X, S.ROAD_Y, S.LL_Z, 0.13, 0.3, dark, segments = 10)
        m.ktube(v3s(S.LL_X, top - 0.15, S.LL_Z), v3s(S.LL_X - S.LL_ARM, top + 0.05, S.LL_Z - 0.4), 0.04, pole)
        world.boxAt(0.6, 0.1, 0.3, S.LL_X - S.LL_ARM - 0.1, top - 0.04, S.LL_Z - 0.45, dark, rotY = 0.3)
        world.child(S.LL_X - S.LL_ARM - 0.1, top - 0.045, S.LL_Z - 0.45, ry = 0.3).child(0.0, 0.0, 0.0, rx = PI / 2)
            .plane(0.5, 0.22, 0.0, 0.0, 0.0, machiRgb(palette.streetLight), 3.2f)
    }

    // ---- 小道具 ----
    run {
        val ap = S.AIR_PUMP
        world.boxAt(ap[2], 1.1, ap[3], ap[0], S.APRON_Y, ap[1], brand2)
        world.tube(v3s(ap[0] + 0.1, S.APRON_Y + 0.95, ap[1] + 0.15), v3s(ap[0] + 0.3, S.APRON_Y + 0.1, ap[1] + 0.3), 0.02, dark)
        for (i in 0 until 4) {
            // TorusGeometry(0.26, 0.11) 平放：轴沿 y。
            world.child(S.TIRES[0], S.APRON_Y + 0.11 + i * 0.21, S.TIRES[1], rx = PI / 2).torus(v3s(0.0, 0.0, 0.0), 0.26, 0.11, rubber, 18)
        }
        S.BINS_Z.forEachIndexed { i, z -> world.boxAt(S.BIN_SIZE, 0.78, S.BIN_SIZE, S.BINS_X, S.APRON_Y, z, if (i == 0) brand0 else brand2) }
        world.boxAt(0.9, 1.0, 0.32, 5.3, S.APRON_Y, -5.55, art("canopyColumn"))
        val products = palette.products.map { machiRgb(it) }
        for (i in 0 until 6) world.boxAt(0.1, 0.18, 0.08, 4.95 + (i % 3) * 0.32, S.APRON_Y + 1.0, -5.42, products[i % products.size])
    }

    // ---- 停着的车 ----
    val andons = ArrayList<DoubleArray>()
    fun parkedCar(f: MachiFrame, kind: String, body: FloatArray) {
        val length = if (kind == "kei") 3.0 else S.CAR_L
        val width = if (kind == "kei") 1.36 else S.CAR_W
        val half = length / 2
        fun span(c: FloatArray, x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double, e: Float = 0f) =
            f.boxAt(x1 - x0, y1 - y0, z1 - z0, (x0 + x1) / 2, y0, (z0 + z1) / 2, c, e)
        if (kind == "kei") {
            span(body, -width / 2, width / 2, 0.3, 0.72, -half, half)
            span(body, -width / 2 + 0.02, width / 2 - 0.02, 0.72, 1.42, half - 1.05, half)
            span(carGlass, -width / 2 + 0.06, width / 2 - 0.06, 0.95, 1.36, half - 0.06, half + 0.005)
            span(carGlass, -width / 2 - 0.005, width / 2 + 0.005, 0.98, 1.34, half - 0.95, half - 0.2)
            val bedBack = -half + 0.04; val bedFront = half - 1.12
            for (side in intArrayOf(-1, 1)) span(body, side * width / 2 - 0.03, side * width / 2 + 0.03, 0.72, 1.0, bedBack, bedFront)
            span(body, -width / 2, width / 2, 0.72, 1.0, bedBack - 0.03, bedBack + 0.03)
            span(dark, -width / 2 + 0.06, width / 2 - 0.06, 1.42, 1.48, bedFront - 0.02, bedFront + 0.04)
        } else {
            span(body, -width / 2, width / 2, 0.25, 0.8, -half, half)
            span(carGlass, -width / 2 + 0.07, width / 2 - 0.07, 0.78, 1.3, -half * 0.62, half * 0.42)
            span(body, -width / 2 + 0.08, width / 2 - 0.08, 1.27, 1.35, -half * 0.55, half * 0.32)
            span(dark, -width / 2 - 0.004, width / 2 + 0.004, 0.52, 0.54, -half + 0.3, half - 0.3)
            if (kind == "taxi") {
                val trim = colors.getValue("taxiTrim")
                val ay = 1.35
                span(dark, -0.5, 0.5, ay, ay + 0.05, -0.2, 0.12)
                span(trim, -0.6, 0.6, ay + 0.05, ay + 0.43, -0.18, 0.1)
                val p = f.pt(0.0, ay + 0.24, 0.106)
                andons += doubleArrayOf(p[0], p[1], p[2], 1.08, 0.32)
                f.plane(0.34, 0.11, width / 2 - 0.42, 0.9, half * 0.42 + 0.004, machiRgb(palette.stopRed), 1.8f)
                for (side in intArrayOf(-1, 1)) span(trim, side * (width / 2 + 0.004) - 0.004, side * (width / 2 + 0.004) + 0.004, 0.56, 0.66, -half + 0.25, half - 0.25)
            }
        }
        span(dark, -width / 2 + 0.04, width / 2 - 0.04, 0.22, 0.38, half - 0.04, half + 0.04)
        span(dark, -width / 2 + 0.04, width / 2 - 0.04, 0.22, 0.38, -half - 0.04, -half + 0.04)
        for (side in intArrayOf(-1, 1)) {
            f.boxAt(0.3, 0.1, 0.04, side * (width / 2 - 0.26), 0.58, half + 0.005, machiRgb(palette.streetLight), 2.2f)
            f.boxAt(0.26, 0.1, 0.04, side * (width / 2 - 0.22), 0.62, -half - 0.005, machiRgb(palette.stopRed), 1.6f)
            // 双闪小灯（hazardIdle：marker × 0.12；会闪的那辆后接）。
            f.boxAt(0.1, 0.06, 0.03, side * (width / 2 - 0.06), 0.5, half + 0.012, marker, 0.12f)
            f.boxAt(0.1, 0.06, 0.03, side * (width / 2 - 0.06), 0.5, -half - 0.012, marker, 0.12f)
            if (kind != "kei") f.boxAt(0.14, 0.08, 0.06, side * (width / 2 + 0.05), 0.92, half * 0.36, body)
        }
        val axle = half - (if (kind == "kei") 0.5 else 0.62)
        val r = 0.27
        for ((side, z) in listOf(-1.0 to axle, 1.0 to axle, -1.0 to -axle, 1.0 to -axle)) {
            val x = side * (width / 2 - 0.085)
            f.tube(v3s(x - 0.1, r, z), v3s(x + 0.1, r, z), r, rubber, 14)
        }
        count(kind)
    }
    v.stalls.forEachIndexed { i, kind ->
        if (kind == "empty") return@forEachIndexed
        parkedCar(world.child(S.STALL_X[i], S.ROAD_Y, S.STALL_Z + 0.15), kind, if (kind == "taxi") colors.getValue("taxiBody") else colors.getValue("car$i"))
    }
    if (v.pumpCar) parkedCar(world.child(S.PUMP_CAR[0], S.APRON_Y, S.PUMP_CAR[1], ry = PI / 2), "sedan", colors.getValue("carPump"))
    if (v.keiTruck) parkedCar(world.child(S.KEI_TRUCK[0], S.ROAD_Y, S.KEI_TRUCK[1], ry = -PI / 2), "kei", colors.getValue("carKei"))

    // ---- アドトラック（车头朝 +x，货厢侧面 +z 是屏幕） ----
    run {
        val zc = (S.T_Z0 + S.T_Z1) / 2; val width = S.T_Z1 - S.T_Z0
        val box = art("truckBox"); val cab = colors.getValue("truckCab")
        m.box(S.T_REAR + 0.1, S.T_FRONT - 0.2, 0.55, S.T_DECK, zc - 0.7, zc + 0.7, dark)
        m.box(S.T_REAR, S.T_BOX_FRONT, S.T_DECK, S.T_BOX_TOP, S.T_Z0, S.T_Z1, box)
        m.box(S.T_REAR + 0.12, S.T_BOX_FRONT - 0.12, S.T_DECK + 0.14, S.T_BOX_TOP - 0.08, S.T_Z1 - 0.02, S.T_Z1 + 0.02, dark)
        // 屏面先铺暗底（字面由招牌模块贴，后续接）。
        m.quad(v3s(S.TS_X - S.TS_W / 2, S.TS_Y - S.TS_H / 2, S.TS_Z), v3s(S.TS_X + S.TS_W / 2, S.TS_Y - S.TS_H / 2, S.TS_Z),
            v3s(S.TS_X + S.TS_W / 2, S.TS_Y + S.TS_H / 2, S.TS_Z), v3s(S.TS_X - S.TS_W / 2, S.TS_Y + S.TS_H / 2, S.TS_Z), v3s(0.0, 0.0, 1.0), machiRgb(palette.boardUnlit))
        val sp = (S.T_BOX_FRONT - S.T_REAR - 0.4) / 7
        for (i in 0..7) {
            val x = S.T_REAR + 0.2 + i * sp
            world.boxAt(0.1, 0.05, 0.03, x, S.T_BOX_TOP - 0.05, S.T_Z1 + 0.02, marker, 1.8f)
            world.boxAt(0.1, 0.05, 0.03, x, S.T_DECK + 0.02, S.T_Z1 + 0.02, marker, 1.8f)
        }
        m.box(S.T_CAB_BACK, S.T_FRONT, 0.75, S.T_CAB_TOP, S.T_Z0 + 0.05, S.T_Z1 - 0.05, cab)
        m.box(S.T_FRONT - 0.03, S.T_FRONT + 0.005, 1.55, S.T_CAB_TOP - 0.25, S.T_Z0 + 0.18, S.T_Z1 - 0.18, carGlass)
        for (z in doubleArrayOf(S.T_Z0 + 0.045, S.T_Z1 - 0.045)) m.box(S.T_FRONT - 0.75, S.T_FRONT - 0.12, 1.6, S.T_CAB_TOP - 0.3, z - 0.01, z + 0.01, carGlass)
        m.box(S.T_FRONT - 0.05, S.T_FRONT + 0.06, S.T_CAB_TOP - 0.2, S.T_CAB_TOP - 0.12, S.T_Z0 + 0.1, S.T_Z1 - 0.1, dark)
        m.box(S.T_FRONT - 0.1, S.T_FRONT + 0.08, 0.45, 0.85, S.T_Z0 + 0.04, S.T_Z1 - 0.04, dark)
        for (z in doubleArrayOf(S.T_Z0 + 0.32, S.T_Z1 - 0.32)) world.boxAt(0.04, 0.14, 0.3, S.T_FRONT + 0.085, 0.86, z, machiRgb(palette.streetLight), 2.2f)
        m.box(S.T_FRONT - 0.03, S.T_FRONT + 0.01, 0.95, 1.45, zc - 0.45, zc + 0.45, dark)
        for (z in doubleArrayOf(S.T_Z0 - 0.08, S.T_Z1 + 0.08)) {
            m.ktube(v3s(S.T_FRONT - 0.2, 2.05, if (z > zc) S.T_Z1 - 0.05 else S.T_Z0 + 0.05), v3s(S.T_FRONT - 0.05, 2.05, z), 0.02, dark)
            world.boxAt(0.06, 0.38, 0.14, S.T_FRONT - 0.03, 1.86, z, dark)
        }
        val stackY = S.T_CAB_TOP + 0.35
        m.cylinderY(S.T_CAB_BACK - 0.1, 1.0, S.T_Z1 - 0.18, 0.07, stackY - 1.0, metal, segments = 10)
        m.ktube(v3s(S.T_CAB_BACK + 0.55 - 0.45, 0.57, S.T_Z1 - 0.3), v3s(S.T_CAB_BACK + 0.55 + 0.45, 0.57, S.T_Z1 - 0.3), 0.22, metal, segments = 14)
        val wz = width / 2 - 0.22
        for (x in doubleArrayOf(S.T_REAR + 0.95, S.T_REAR + 1.95, S.T_FRONT - 0.9)) for (side in intArrayOf(-1, 1)) {
            val z = zc + side * wz
            m.ktube(v3s(x, 0.45, z - 0.16), v3s(x, 0.45, z + 0.16), 0.45, rubber, segments = 14)
        }
        count("truck")
    }
    return MachiStationModel(m.toArray(), stats, andons, canopySigns, pylonFaces, motelFaces)
}
