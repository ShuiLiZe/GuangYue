// 町景「城寨酒吧」（Folia machi scenes/kowloon）白模起步：layout.ts 的尺寸表逐项照抄，blocks.ts 的楼体 / 掏空酒吧 / 楼板线 /
// 窗格规则照译（行从一楼 3 m 起每 1 m、列宽 0.64 m、窗 0.36×0.48 m、伸出 0.035 m、46% 亮窗按 WINDOW_WEIGHTS 配色），
// 台座按 plinth.ts（半边 6 m、三层土石）。楼面杂件（加建小间、铁窗笼+盆栽、冷气机+水渍、晾衫竹+衫、水管、楼板霓虹灯带）
// 按 blocks.ts 逐条照译（MachiKowloonFacade.kt）。随机数用自己的种子序列（不与 Folia 的 createVisualizerRng 逐个对齐，亮哪扇窗会不同）。
// 网格：每顶点 10 个 float（位置 3、法线 3、颜色 3、自发光 1），三角形列表。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class MachiKowloonBlock(
    val name: String, val x0: Double, val x1: Double, val z0: Double, val z1: Double, val height: Double, val wall: String,
    val hollow: DoubleArray? = null, // x0, x1, z0, y1
)

internal object MachiKowloonLayout {
    const val BASE_HALF = 6.0
    const val BASE_DEPTH = 0.6
    const val FLOOR_GROUND = 3.0
    const val FLOOR_HEIGHT = 1.0
    const val LEVEL_GROUND = 0.02
    val BLOCKS = listOf(
        MachiKowloonBlock("L0", -6.0, -3.0, -6.0, -1.8, 6.9, "wallA"),
        MachiKowloonBlock("L1", -6.0, -3.35, -1.8, -0.2, 7.3, "wallB"),
        MachiKowloonBlock("L2", -6.0, -3.6, -0.2, 1.3, 6.6, "wallD"),
        MachiKowloonBlock("L3", -6.0, -3.2, 1.3, 2.9, 6.2, "wallC"),
        MachiKowloonBlock("L4", -6.0, -3.45, 2.9, 4.5, 5.2, "wallE"),
        MachiKowloonBlock("L5", -6.0, -3.75, 4.5, 6.0, 4.0, "wallD"),
        MachiKowloonBlock("B0", -3.0, -1.6, -6.0, -1.8, 6.4, "wallE"),
        MachiKowloonBlock("B0b", -1.6, -0.3, -6.0, -2.05, 7.0, "wallC"),
        MachiKowloonBlock("B1", -0.3, 4.5, -6.0, -1.5, 6.2, "wallA", doubleArrayOf(-0.1, 4.3, -4.2, 2.95)),
        MachiKowloonBlock("B2", 4.5, 6.0, -6.0, -2.3, 5.4, "wallB"),
    )
    /** SCREEN：歌词大屏面中心与尺寸（朝 +z）。 */
    const val SCREEN_X = 2.1; const val SCREEN_Y = 4.22; const val SCREEN_Z = -1.38; const val SCREEN_W = 4.2; const val SCREEN_H = 1.8
    /** BAR_SIGN：门头霓虹店招（曲名）。 */
    const val SIGN_X = 2.1; const val SIGN_Y = 2.68; const val SIGN_Z = -1.38; const val SIGN_W = 3.9; const val SIGN_H = 0.46
    /** ROOF_BILLBOARD、POLE（只用来取构图点）。 */
    val ROOF = doubleArrayOf(-0.05, 2.45, -2.15, 6.6, 7.5)
    val POLE = doubleArrayOf(5.55, -1.15, 7.0)
    /** KOWLOON_VIEW。 */
    val VIEW = MachiView(30 * MACHI_DEG, 30 * MACHI_DEG, 1.55)
    val ART = mapOf(
        "wallA" to "#6b6660", "wallB" to "#7a6e62", "wallC" to "#5d666e", "wallD" to "#857a6b", "wallE" to "#6e6a73",
        "slab" to "#4c4a47", "windowDark" to "#1c222b", "barFloor" to "#2a2426", "barWall" to "#2d2533", "screenFrame" to "#26282d",
        "signBox" to "#1d1a22",
    )
    val WINDOW_LIGHTS = listOf("#ffc47a", "#ffd39a", "#cfe2f5", "#b4f0cf", "#ff8fd0", "#8fc8f5")
    val WINDOW_WEIGHTS = doubleArrayOf(0.3, 0.16, 0.22, 0.12, 0.1, 0.1)
}

/** street.ts kowloonFramingPoints：台座四角（后、左两边连底）、每栋楼顶三角 +0.3、楼顶广告架、电线杆顶。 */
internal fun machiKowloonFramingPoints(): List<MachiVec3> {
    val h = MachiKowloonLayout.BASE_HALF
    val pts = ArrayList<MachiVec3>()
    for (x in listOf(-h, h)) for (z in listOf(-h, h)) {
        pts += MachiVec3(x, 0.0, z)
        if (z < 0 || x < 0) pts += MachiVec3(x, -MachiKowloonLayout.BASE_DEPTH, z)
    }
    for (b in MachiKowloonLayout.BLOCKS) {
        val y = b.height + 0.3
        pts += MachiVec3(b.x0, y, b.z0); pts += MachiVec3(b.x1, y, b.z0); pts += MachiVec3(b.x0, y, b.z1)
    }
    val r = MachiKowloonLayout.ROOF
    pts += MachiVec3(r[0], r[4] + 0.1, r[2]); pts += MachiVec3(r[1], r[4] + 0.1, r[2])
    val p = MachiKowloonLayout.POLE
    pts += MachiVec3(p[0], p[2] + 0.1, p[1])
    return pts
}

/** '#rrggbb' → 0..1 rgb（认不出给中灰）。 */
internal fun machiRgb(hex: String): FloatArray {
    val h = hex.trim().removePrefix("#")
    val v = if (h.length == 6) h.toLongOrNull(16) else null
    if (v == null) return floatArrayOf(0.5f, 0.5f, 0.5f)
    return floatArrayOf(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
}

/** 三角形列表的累加器。 */
internal class MachiMesh {
    private var data = FloatArray(4096)
    var size = 0
        private set
    val vertexCount get() = size / 10

    fun toArray(): FloatArray = data.copyOf(size)

    private fun vertex(x: Double, y: Double, z: Double, n: DoubleArray, c: FloatArray, e: Float) {
        if (size + 10 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = x.toFloat(); data[size++] = y.toFloat(); data[size++] = z.toFloat()
        data[size++] = n[0].toFloat(); data[size++] = n[1].toFloat(); data[size++] = n[2].toFloat()
        data[size++] = c[0]; data[size++] = c[1]; data[size++] = c[2]; data[size++] = e
    }

    /** 四边形 a b c d（逆时针朝外）。 */
    fun quad(a: DoubleArray, b: DoubleArray, c: DoubleArray, d: DoubleArray, n: DoubleArray, color: FloatArray, emissive: Float = 0f) {
        for (p in listOf(a, b, c, a, c, d)) vertex(p[0], p[1], p[2], n, color, emissive)
    }

    /** 轴对齐盒子（six faces；bottom 省掉——白模从不看底）。 */
    fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double, color: FloatArray, emissive: Float = 0f, bottom: Boolean = false) {
        quad(doubleArrayOf(x0, y0, z1), doubleArrayOf(x1, y0, z1), doubleArrayOf(x1, y1, z1), doubleArrayOf(x0, y1, z1), doubleArrayOf(0.0, 0.0, 1.0), color, emissive)
        quad(doubleArrayOf(x1, y0, z0), doubleArrayOf(x0, y0, z0), doubleArrayOf(x0, y1, z0), doubleArrayOf(x1, y1, z0), doubleArrayOf(0.0, 0.0, -1.0), color, emissive)
        quad(doubleArrayOf(x1, y0, z1), doubleArrayOf(x1, y0, z0), doubleArrayOf(x1, y1, z0), doubleArrayOf(x1, y1, z1), doubleArrayOf(1.0, 0.0, 0.0), color, emissive)
        quad(doubleArrayOf(x0, y0, z0), doubleArrayOf(x0, y0, z1), doubleArrayOf(x0, y1, z1), doubleArrayOf(x0, y1, z0), doubleArrayOf(-1.0, 0.0, 0.0), color, emissive)
        quad(doubleArrayOf(x0, y1, z1), doubleArrayOf(x1, y1, z1), doubleArrayOf(x1, y1, z0), doubleArrayOf(x0, y1, z0), doubleArrayOf(0.0, 1.0, 0.0), color, emissive)
        if (bottom) quad(doubleArrayOf(x0, y0, z0), doubleArrayOf(x1, y0, z0), doubleArrayOf(x1, y0, z1), doubleArrayOf(x0, y0, z1), doubleArrayOf(0.0, -1.0, 0.0), color, emissive)
    }
}

/** 城寨白模的几何与统计（单测用）。 */
/** 亮窗（kowloonScene.ts：窗色 × (0.28 + 0.6 × 夜里程度)，夜里 0.88；不乘 artificial，夜里 artificial = 1 相同）。 */
internal const val MACHI_WINDOW_LEVEL = 0.88f

internal class MachiKowloonModel(val mesh: FloatArray, val litWindows: Int, val darkWindows: Int, val signs: List<MachiSignPlacement> = emptyList(), val clutter: Map<String, Int> = emptyMap(),
    /** 楼面滴水（冷气机前沿）：x, z, 起点高, 落点高, 周期, 相位。 */
    val drips: List<DoubleArray> = emptyList())

private class MachiFace(val block: MachiKowloonBlock, val ox: Double, val oz: Double, val ax: Double, val az: Double, val nx: Double, val nz: Double, val width: Double)

private fun facesOf(b: MachiKowloonBlock) = listOf(
    MachiFace(b, b.x0, b.z1, 1.0, 0.0, 0.0, 1.0, b.x1 - b.x0),
    MachiFace(b, b.x1, b.z1, 0.0, -1.0, 1.0, 0.0, b.z1 - b.z0),
    MachiFace(b, b.x0, b.z0, 0.0, 1.0, -1.0, 0.0, b.z1 - b.z0),
)

private fun insideAnyBlock(x: Double, y: Double, z: Double, eps: Double = 0.01) = MachiKowloonLayout.BLOCKS.any { b ->
    x > b.x0 + eps && x < b.x1 - eps && z > b.z0 + eps && z < b.z1 - eps && y < b.height - eps
}

/** 歌词屏、门头招牌占的地方（不铺窗）：x0,x1,y0,y1,z0,z1。 */
private val AVOID = listOf(
    doubleArrayOf(MachiKowloonLayout.SCREEN_X - MachiKowloonLayout.SCREEN_W / 2 - 0.15, MachiKowloonLayout.SCREEN_X + MachiKowloonLayout.SCREEN_W / 2 + 0.15,
        MachiKowloonLayout.SCREEN_Y - MachiKowloonLayout.SCREEN_H / 2 - 0.5, MachiKowloonLayout.SCREEN_Y + MachiKowloonLayout.SCREEN_H / 2 + 0.15, -2.0, 0.5),
)

private val AVOID_ALL by lazy { AVOID + MachiKowloonStreet.windowAvoid() }

private fun inAvoid(x: Double, y: Double, z: Double) = AVOID_ALL.any { a -> x in a[0]..a[1] && y in a[2]..a[3] && z in a[4]..a[5] }

/** 种子 → 0..1 序列（mulberry32）。 */
internal class MachiRng(seed: Long) {
    private var a = seed.toInt()
    fun next(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

internal fun buildMachiKowloon(seed: String): MachiKowloonModel {
    val m = MachiMesh()
    val rnd = MachiRng(machiHashSeed("kowloon:blocks:$seed"))
    val art = MachiKowloonLayout.ART
    val h = MachiKowloonLayout.BASE_HALF
    // 台座：plinthTop / Mid / Low 三层（palette 的台座色）。
    m.box(-h, h, -0.1, 0.0, -h, h, machiRgb("#20242c"))
    m.box(-h + 0.012, h - 0.012, -0.3, -0.1, -h + 0.012, h - 0.012, machiRgb("#3d3a36"))
    m.box(-h + 0.006, h - 0.006, -0.52, -0.3, -h + 0.006, h - 0.006, machiRgb("#2b2622"))
    // 湿巷子的地面（asphalt）。
    m.box(-h, h, 0.0, MachiKowloonLayout.LEVEL_GROUND, -h, h, machiRgb("#2a303c"))
    val ctx = MachiFacadeCtx()
    for (b in MachiKowloonLayout.BLOCKS) {
        val wall = machiRgb(art.getValue(b.wall))
        val hollow = b.hollow
        if (hollow != null) {
            val (hx0, hx1, hz0, hy1) = listOf(hollow[0], hollow[1], hollow[2], hollow[3])
            m.box(b.x0, b.x1, hy1, b.height, b.z0, b.z1, wall)
            m.box(b.x0, b.x1, 0.0, hy1, b.z0, hz0, wall)
            m.box(b.x0, hx0, 0.0, hy1, hz0, b.z1, wall)
            m.box(hx1, b.x1, 0.0, hy1, hz0, b.z1, wall)
            addMachiKowloonBar(m, seed)
        } else {
            m.box(b.x0, b.x1, 0.0, b.height, b.z0, b.z1, wall)
        }
        for (f in facesOf(b)) facade(m, f, rnd, ctx)
    }
    // 歌词大屏：屏框（screenFrame）+ 屏面（深底，自发光弱；字由舞台在屏面上另画）。
    val sx = MachiKowloonLayout.SCREEN_X; val sy = MachiKowloonLayout.SCREEN_Y; val sz = MachiKowloonLayout.SCREEN_Z
    val sw = MachiKowloonLayout.SCREEN_W; val sh = MachiKowloonLayout.SCREEN_H
    m.box(sx - sw / 2 - 0.08, sx + sw / 2 + 0.08, sy - sh / 2 - 0.08, sy + sh / 2 + 0.08, sz - 0.14, sz - 0.02, machiRgb(art.getValue("screenFrame")))
    m.quad(doubleArrayOf(sx - sw / 2, sy - sh / 2, sz), doubleArrayOf(sx + sw / 2, sy - sh / 2, sz), doubleArrayOf(sx + sw / 2, sy + sh / 2, sz), doubleArrayOf(sx - sw / 2, sy + sh / 2, sz),
        doubleArrayOf(0.0, 0.0, 1.0), machiRgb("#0b1220"), 1f)
    // 门头店招灯箱。
    val gx = MachiKowloonLayout.SIGN_X; val gy = MachiKowloonLayout.SIGN_Y; val gz = MachiKowloonLayout.SIGN_Z
    m.box(gx - MachiKowloonLayout.SIGN_W / 2, gx + MachiKowloonLayout.SIGN_W / 2, gy - MachiKowloonLayout.SIGN_H / 2, gy + MachiKowloonLayout.SIGN_H / 2, gz - 0.12, gz, machiRgb(art.getValue("signBox")))
    // 街面道具：店招盒子、楼顶广告架、大排档、电线杆（MachiKowloonStreet.kt）。
    val signs = addMachiKowloonStreet(m, seed)
    addMachiKowloonRoofs(m, seed)
    return MachiKowloonModel(m.toArray(), ctx.lit, ctx.dark, signs, ctx.clutter, ctx.drips.toList())
}

private fun pickWeighted(roll: Double): Int {
    var r = roll * MachiKowloonLayout.WINDOW_WEIGHTS.sum()
    for (i in MachiKowloonLayout.WINDOW_WEIGHTS.indices) {
        r -= MachiKowloonLayout.WINDOW_WEIGHTS[i]
        if (r <= 0) return i
    }
    return MachiKowloonLayout.WINDOW_WEIGHTS.size - 1
}

/** 贴墙的竖直四边形：沿墙 a→c，高 y0..y1，朝面法线。 */
private fun faceQuad(m: MachiMesh, f: MachiFace, a: DoubleArray, c: DoubleArray, y0: Double, y1: Double, color: FloatArray, emissive: Float) {
    val n = doubleArrayOf(f.nx, 0.0, f.nz)
    m.quad(doubleArrayOf(a[0], y0, a[2]), doubleArrayOf(c[0], y0, c[2]), doubleArrayOf(c[0], y1, c[2]), doubleArrayOf(a[0], y1, a[2]), n, color, emissive)
}

/** 屏面四角（世界坐标）：左下、右下、右上、左上。 */
internal fun machiKowloonScreenCorners(): List<MachiVec3> {
    val sx = MachiKowloonLayout.SCREEN_X; val sy = MachiKowloonLayout.SCREEN_Y; val sz = MachiKowloonLayout.SCREEN_Z + 0.005
    val hw = MachiKowloonLayout.SCREEN_W / 2; val hh = MachiKowloonLayout.SCREEN_H / 2
    return listOf(MachiVec3(sx - hw, sy - hh, sz), MachiVec3(sx + hw, sy - hh, sz), MachiVec3(sx + hw, sy + hh, sz), MachiVec3(sx - hw, sy + hh, sz))
}

/** 门头店招四角。 */
internal fun machiKowloonSignCorners(): List<MachiVec3> {
    val x = MachiKowloonLayout.SIGN_X; val y = MachiKowloonLayout.SIGN_Y; val z = MachiKowloonLayout.SIGN_Z + 0.005
    val hw = MachiKowloonLayout.SIGN_W / 2; val hh = MachiKowloonLayout.SIGN_H / 2
    return listOf(MachiVec3(x - hw, y - hh, z), MachiVec3(x + hw, y - hh, z), MachiVec3(x + hw, y + hh, z), MachiVec3(x - hw, y + hh, z))
}

// ---- 楼面杂件：blocks.ts buildKowloonBlocks 的 facesOf 循环逐条照译 ----

/** 盒子：x0,x1,y0,y1,z0,z1。 */
private fun hits(a: DoubleArray, b: DoubleArray) = a[0] <= b[1] && a[1] >= b[0] && a[2] <= b[3] && a[3] >= b[2] && a[4] <= b[5] && a[5] >= b[4]
private fun inside(x: Double, y: Double, z: Double, b: DoubleArray) = x in b[0]..b[1] && y in b[2]..b[3] && z in b[4]..b[5]

internal class MachiFacadeCtx {
    var lit = 0
    var dark = 0
    val clutter = HashMap<String, Int>()
    /** 伸出墙外的东西各占的地方（claim）。 */
    val outward = ArrayList<DoubleArray>()
    val drips = ArrayList<DoubleArray>()
    fun count(k: String) { clutter[k] = (clutter[k] ?: 0) + 1 }
    fun claim(b: DoubleArray): Boolean { if (outward.any { hits(it, b) }) return false; outward += b; return true }
}

private object FacadeArt {
    val frame = machiRgb("#383c43"); val slab = machiRgb("#4c4a47"); val windowDark = machiRgb("#1c222b")
    val cage = machiRgb("#5a6068"); val rust = machiRgb("#7a4a33"); val ac = machiRgb("#c6c4bb"); val acGrille = machiRgb("#55595e")
    val pipe = machiRgb("#666a70"); val bamboo = machiRgb("#b9a06a"); val stain = machiRgb("#3f3a36"); val plant = machiRgb("#4f8a46")
    val wallD = machiRgb("#857a6b"); val wallB = machiRgb("#7a6e62")
    val stripPink = machiRgb("#ff4fb0"); val stripCyan = machiRgb("#3fe8ff")
    val clothes = listOf("#f1efe8", "#a9c8e8", "#d0463c", "#e8c24a", "#f0a3c0", "#2c3d63", "#9a9fa6", "#6fb08a").map { machiRgb(it) }
}

/** signs：店招盒子（0.08 余量）+ 歌词屏前沿（kowloonScreenFront）。 */
private val FACADE_SIGNS: List<DoubleArray> by lazy {
    val l = MachiKowloonLayout
    MachiKowloonStreet.HANGING_SIGNS.map { MachiKowloonStreet.signBox(it, 0.08) } + listOf(doubleArrayOf(
        l.SCREEN_X - l.SCREEN_W / 2 - 0.4, l.SCREEN_X + l.SCREEN_W / 2 + 0.4, l.SCREEN_Y - l.SCREEN_H / 2 - 0.3, l.SCREEN_Y + l.SCREEN_H / 2 + 1.2,
        l.SCREEN_Z - 0.2, l.SCREEN_Z + 1.6))
}

/** 楼脚的东西（掏空的门面、大排档）：水管碰到只落到一楼楼板。 */
private val FACADE_GROUND: List<DoubleArray> by lazy {
    MachiKowloonLayout.BLOCKS.mapNotNull { b -> b.hollow?.let { doubleArrayOf(it[0], it[1], 0.0, it[3], b.z1 - 0.05, b.z1 + 0.5) } } +
        listOf(doubleArrayOf(MachiKowloonStreet.ST_X0, MachiKowloonStreet.ST_X1, 0.0, MachiKowloonStreet.ST_CANOPY, MachiKowloonStreet.ST_Z0, MachiKowloonStreet.ST_Z1))
}

private fun facade(m: MachiMesh, f: MachiFace, rnd: MachiRng, ctx: MachiFacadeCtx) {
    val b = f.block
    fun p(s: Double, y: Double, out: Double) = doubleArrayOf(f.ox + f.ax * s + f.nx * out, y, f.oz + f.az * s + f.nz * out)
    fun bounds(s0: Double, s1: Double, y0: Double, y1: Double, o0: Double, o1: Double): DoubleArray {
        val a = p(s0, y0, o0); val c = p(s1, y1, o1)
        return doubleArrayOf(min(a[0], c[0]), max(a[0], c[0]), min(a[1], c[1]), max(a[1], c[1]), min(a[2], c[2]), max(a[2], c[2]))
    }
    /** faceBox：宽沿墙 w、高 h（y 为底）、厚沿法线 d，中心在墙外 out。 */
    fun fbox(s: Double, y: Double, w: Double, h: Double, d: Double, out: Double, color: FloatArray, emissive: Float = 0f) {
        val c = p(s, 0.0, out)
        val hx = abs(f.ax) * w / 2 + abs(f.nx) * d / 2; val hz = abs(f.az) * w / 2 + abs(f.nz) * d / 2
        m.box(c[0] - hx, c[0] + hx, y, y + h, c[2] - hz, c[2] + hz, color, emissive)
    }
    val probe = p(f.width / 2, b.height - 0.5, 0.2)
    val outer = abs(probe[0]) > 6 || abs(probe[2]) > 6
    // 楼板线：每层一道；有几道贴一段霓虹灯带（粉或青）。
    var y = MachiKowloonLayout.FLOOR_GROUND
    while (y < b.height - 0.3) {
        fbox(f.width / 2, y - 0.03, f.width, 0.06, 0.1, 0.0, FacadeArt.slab)
        if (!outer && rnd.next() < 0.14) {
            val length = min(f.width - 0.2, 0.9 + rnd.next() * 1.6)
            val s = 0.1 + length / 2 + rnd.next() * (f.width - 0.2 - length)
            val q = p(s, y + 0.1, 0.15); val q2 = p(s, y, 0.1)
            if (!insideAnyBlock(q[0], q[1], q[2]) && !inAvoid(q2[0], q2[1], q2[2])) {
                fbox(s, y + 0.03, length, 0.035, 0.03, 0.06, if (rnd.next() < 0.55) FacadeArt.stripPink else FacadeArt.stripCyan, 1.3f)
                ctx.count("strip")
            }
        }
        y += MachiKowloonLayout.FLOOR_HEIGHT
    }
    val columns = max(1, floor((f.width - 0.2) / 0.64).toInt())
    val spacing = f.width / columns
    val overhead = FACADE_SIGNS + AVOID_ALL
    // 水管：一两根，走在两列窗之间，从楼顶下来；楼脚有东西就只落到一楼楼板。
    val pipes = 1 + floor(rnd.next() * 2).toInt()
    repeat(pipes) {
        val pick = rnd.next()
        val s = if (columns > 1) (1 + floor(pick * (columns - 1))) * spacing else (if (pick < 0.5) 0.12 else f.width - 0.12)
        val q = p(s, b.height - 0.4, 0.12)
        if (insideAnyBlock(q[0], q[1], q[2])) return@repeat
        val top = b.height - 0.1
        val bottom = if (FACADE_GROUND.any { hits(it, bounds(s - 0.05, s + 0.05, 0.0, MachiKowloonLayout.FLOOR_GROUND, 0.04, 0.14)) }) MachiKowloonLayout.FLOOR_GROUND - 0.03 else 0.0
        val bb = bounds(s - 0.05, s + 0.05, bottom, top, 0.04, 0.14)
        if (overhead.any { hits(it, bb) } || !ctx.claim(bb)) return@repeat
        val at = p(s, 0.0, 0.09)
        m.prism(at[0], bottom, at[2], 0.045, top - bottom, FacadeArt.pipe, segments = 6)
        ctx.count("pipe")
    }
    y = MachiKowloonLayout.FLOOR_GROUND
    while (y + 0.85 <= b.height - 0.15) {
        for (col in 0 until columns) {
            val s = (col + 0.5) * spacing + (rnd.next() - 0.5) * 0.08
            val center = y + 0.55
            val probes = listOf(-0.27, 0.0, 0.27).map { p(s + it, center, 0.12) }
            if (probes.any { insideAnyBlock(it[0], it[1], it[2]) }) continue
            if (probes.any { inAvoid(it[0], it[1], it[2]) }) continue
            fun nearSign(out: Double, dy: Double = 0.0) = listOf(-0.26, 0.0, 0.26).any { ds -> val q = p(s + ds, center + dy, out); overhead.any { inside(q[0], q[1], q[2], it) } }
            fun glass(gy: Double, w: Double, h: Double, out: Double) {
                val litRoll = rnd.next()
                if (litRoll < 0.46) {
                    ctx.lit++
                    fbox(s, gy - h / 2, w, h, 0.004, out, machiRgb(MachiKowloonLayout.WINDOW_LIGHTS[pickWeighted(rnd.next())]), MACHI_WINDOW_LEVEL)
                } else { ctx.dark++; fbox(s, gy - h / 2, w, h, 0.004, out, FacadeArt.windowDark) }
            }
            val roll = rnd.next()
            // 封出去的加建小间：自己一扇小窗。
            if (!outer && roll < 0.08 && !nearSign(0.3) && ctx.claim(bounds(s - 0.3, s + 0.3, y + 0.06, y + 0.92, 0.0, 0.4))) {
                fbox(s, y + 0.06, 0.6, 0.86, 0.4, 0.2, if (rnd.next() < 0.5) FacadeArt.wallD else FacadeArt.wallB)
                fbox(s, y + 0.3, 0.42, 0.44, 0.04, 0.41, FacadeArt.frame)
                glass(center + 0.02, 0.32, 0.34, 0.435)
                ctx.count("addition")
                continue
            }
            fbox(s, center - 0.3, 0.46, 0.6, 0.06, 0.0, FacadeArt.frame)
            fbox(s, center - 0.33, 0.52, 0.04, 0.12, 0.02, FacadeArt.slab)
            glass(center, 0.36, 0.48, 0.035)
            if (outer) continue
            if (roll < 0.36 && !nearSign(0.15) && ctx.claim(bounds(s - 0.27, s + 0.27, center - 0.37, center + 0.37, 0.0, 0.25))) {
                // 铁窗笼：顶板、底板、前面五根竖枝、一道横枝、两侧横枝；笼里偶尔一盆绿。
                val mat = if (rnd.next() < 0.35) FacadeArt.rust else FacadeArt.cage
                val depth = 0.24; val bottom = center - 0.34; val top = center + 0.34
                fbox(s, top, 0.54, 0.03, depth, depth / 2, mat)
                fbox(s, bottom - 0.03, 0.54, 0.03, depth, depth / 2, mat)
                for (ds in listOf(-0.25, -0.125, 0.0, 0.125, 0.25)) fbox(s + ds, bottom, 0.02, top - bottom, 0.02, depth, mat)
                fbox(s, center, 0.54, 0.02, 0.02, depth, mat)
                for (ds in listOf(-0.25, 0.25)) fbox(s + ds, center, 0.02, 0.02, depth, depth / 2, mat)
                if (rnd.next() < 0.4) fbox(s + (rnd.next() - 0.5) * 0.24, bottom, 0.14, 0.14, 0.14, 0.14, FacadeArt.plant)
                ctx.count("cage")
            } else if (roll < 0.7 && !nearSign(0.2, -0.4) && ctx.claim(bounds(s - 0.21, s + 0.21, center - 0.72, center - 0.42, 0.0, 0.33))) {
                // 冷气机：窗下伸出墙外，前面深色格栅；偶尔一道水渍；下雨时前沿滴水（drips 留给天气）。
                val acY = center - 0.72
                fbox(s, acY, 0.42, 0.3, 0.32, 0.16, FacadeArt.ac)
                fbox(s - 0.04, acY + 0.05, 0.28, 0.2, 0.02, 0.325, FacadeArt.acGrille)
                if (rnd.next() < 0.45) {
                    val length = 0.45 + rnd.next() * 0.7
                    fbox(s + (rnd.next() - 0.5) * 0.2, acY - length, 0.08 + rnd.next() * 0.08, length, 0.012, 0.006, FacadeArt.stain)
                }
                if (ctx.drips.size < 28 && rnd.next() < 0.6) {
                    val at = p(s + 0.12, acY, 0.3)
                    ctx.drips += doubleArrayOf(at[0], at[2], acY, MachiKowloonLayout.LEVEL_GROUND, 1.4 + rnd.next() * 2.2, rnd.next())
                }
                ctx.count("ac")
            } else if (roll < 0.82 && !nearSign(0.6, 0.35) && ctx.claim(bounds(s - 0.03, s + 0.03, center - 0.08, center + 0.4, 0.0, 0.96))) {
                // 晾衫竹：从窗顶伸出去，挂两三件；衣服在竹竿下同一平面里，一件挨一件留缝，挂不下一起收窄。
                val poleY = center + 0.38
                m.tube(p(s, poleY, 0.02), p(s, poleY, 0.95), 0.014, FacadeArt.bamboo)
                val count = 2 + floor(rnd.next() * 2).toInt()
                val pieces = List(count) { doubleArrayOf(0.16 + rnd.next() * 0.12, 0.26 + rnd.next() * 0.18, rnd.next(), floor(rnd.next() * FacadeArt.clothes.size)) }
                val near = 0.12; val far = 0.92; val gap = 0.03
                val room = far - near - gap * (count - 1)
                val total = pieces.sumOf { it[0] }
                val narrow = min(1.0, room / total)
                val slack = (room - total * narrow) / count
                var out = near
                for (pc in pieces) {
                    val w = pc[0] * narrow
                    out += slack * pc[2]
                    // 旋转 rotationY + π/2：衣宽沿法线、厚 0.018 沿墙。
                    val c = p(s, 0.0, out + w / 2)
                    val hx = abs(f.ax) * 0.009 + abs(f.nx) * w / 2; val hz = abs(f.az) * 0.009 + abs(f.nz) * w / 2
                    m.box(c[0] - hx, c[0] + hx, poleY - 0.01 - pc[1], poleY - 0.01, c[2] - hz, c[2] + hz, FacadeArt.clothes[pc[3].toInt()])
                    out += w + gap + slack * (1 - pc[2])
                }
                ctx.count("laundry")
            }
        }
        y += MachiKowloonLayout.FLOOR_HEIGHT
    }
}

// ---- 酒吧（B1 一楼）：bar.ts buildKowloonBar 照译。玻璃面（透明）不画，只画框；倒影替身留给湿地反射。 ----
internal fun addMachiKowloonBar(m: MachiMesh, seed: String) {
    val rnd = MachiRng(machiHashSeed("kowloon:bar:$seed"))
    val x0 = -0.1; val x1 = 4.3; val front = -1.5; val glassZ = -1.62; val back = -4.2; val ceiling = 2.95; val d0 = 3.3; val d1 = 4.2
    val cx0 = 0.2; val cx1 = 3.0; val cz0 = -3.4; val cz1 = -2.85; val ch = 1.05; val floorY = 0.06
    val frame = machiRgb("#25222a"); val wall = machiRgb("#2d2533"); val chrome = machiRgb("#aab1b9"); val counterTop = machiRgb("#6b4a3a")
    m.box(x0, x1, 0.0, floorY, back, glassZ, machiRgb("#2a2426"))
    m.box(x0, x1, ceiling - 0.05, ceiling, back, glassZ, wall, bottom = true)
    m.box(x0, x0 + 0.04, floorY, ceiling, back, glassZ, wall)
    m.box(x1 - 0.04, x1, floorY, ceiling, back, glassZ, wall)
    m.box(x0, x1, floorY, ceiling, back, back + 0.04, wall)
    m.box(x0 - 0.22, x0 + 0.01, 0.0, ceiling, glassZ - 0.02, front + 0.03, frame)
    m.box(x1 - 0.01, x1 + 0.22, 0.0, ceiling, glassZ - 0.02, front + 0.03, frame)
    m.box(x0, d0, 0.0, 0.36, glassZ - 0.06, glassZ + 0.04, frame)
    m.box(x0, x1, 2.32, 2.4, glassZ - 0.06, glassZ + 0.04, frame, bottom = true)
    m.box(d0 - 0.1, d1 + 0.1, 0.0, floorY, glassZ, front + 0.3, machiRgb("#4c4a47"))
    val mullions = max(1, Math.round((d0 - x0) / 1.1).toInt())
    for (i in 0..mullions) { val x = x0 + (d0 - x0) * i / mullions; m.box(x - 0.03, x + 0.03, 0.36, 2.32, glassZ - 0.04, glassZ + 0.03, frame) }
    m.box(d0, d0 + 0.07, floorY, 2.32, glassZ - 0.04, glassZ + 0.04, frame)
    m.box(d1 - 0.07, d1, floorY, 2.32, glassZ - 0.04, glassZ + 0.04, frame)
    m.box(d0, d1, floorY, floorY + 0.12, glassZ - 0.04, glassZ + 0.04, frame)
    m.box(d1 - 0.2, d1 - 0.16, 0.9, 1.4, glassZ + 0.03, glassZ + 0.07, chrome)
    m.box(x1 - 0.2, x1, 0.36, 2.32, glassZ - 0.04, glassZ + 0.04, frame)
    // 酒架：整面背光（barBacklight，主题强调色的鲜艳版；取证默认主题取偏紫），三层隔板，瓶子。
    val sx0 = x0 + 0.35; val sx1 = cx1 + 0.15
    m.box(sx0, sx1, 1.0, 2.55, back + 0.04, back + 0.06, machiRgb("#b46ae0"), 1.2f)
    val bottleColors = listOf("#c98a3a", "#2f6b45", "#d9e4e8", "#3d6fb6", "#a8323a", "#e0b84f", "#7a4a2a").map { machiRgb(it) }
    for (level in listOf(1.05, 1.55, 2.05)) {
        m.box(sx0, sx1, level - 0.03, level, back + 0.06, back + 0.3, counterTop)
        var x = sx0 + 0.08
        while (x < sx1 - 0.08) {
            if (rnd.next() > 0.12) {
                val z = back + 0.17 + (rnd.next() - 0.5) * 0.06; val h = 0.2 + rnd.next() * 0.14
                m.prism(x, level, z, 0.04, h, bottleColors[(rnd.next() * bottleColors.size).toInt().coerceAtMost(bottleColors.size - 1)], 6, rt = 0.032)
            }
            x += 0.1 + rnd.next() * 0.05
        }
    }
    // 吧台：台身、挑出的台面、台面下青色霓虹灯带、脚踏杆。
    m.box(cx0, cx1, floorY, ch - 0.05, cz0, cz1, machiRgb("#3b2620"))
    m.box(cx0 - 0.06, cx1 + 0.06, ch - 0.05, ch, cz0 - 0.04, cz1 + 0.1, counterTop, bottom = true)
    m.box(cx0, cx1, ch - 0.11, ch - 0.08, cz1 + 0.005, cz1 + 0.03, machiRgb("#3fe8ff"), 1.6f)
    m.box(cx0 + 0.05, cx1 - 0.05, 0.22, 0.25, cz1 + 0.08, cz1 + 0.11, chrome)
    for (i in 0 until 5) {
        val x = cx0 + 0.3 + (cx1 - cx0 - 0.6) * i / 4; val z = cz1 + 0.42
        m.prism(x, floorY, z, 0.15, 0.03, chrome, 12); m.prism(x, floorY, z, 0.025, 0.7, chrome, 6); m.prism(x, 0.74, z, 0.17, 0.08, machiRgb("#c2384a"), 14)
    }
    // 吊灯：线 + 发光灯罩（pendant，暖光 2.0）。
    for (t in listOf(0.25, 0.5, 0.75)) {
        val x = cx0 + (cx1 - cx0) * t; val z = (cz0 + cz1) / 2 + 0.25
        m.prism(x, 2.25, z, 0.008, ceiling - 2.25, machiRgb("#11141a"), 4)
        m.prism(x, 2.11, z, 0.09, 0.18, machiRgb("#ffc887"), 10, rt = 0.06, emissive = 2f)
    }
    // 点唱机：右后角，机身、深色面板、拱顶的灯（jukebox 琥珀 1.5；随音乐明暗未接）。
    val jx = (cx1 + x1) / 2 + 0.15; val jz = back + 0.32
    m.box(jx - 0.32, jx + 0.32, floorY, 1.05, jz - 0.22, jz + 0.22, machiRgb("#8a2f3c"))
    m.box(jx - 0.22, jx + 0.22, 0.55, 0.95, jz + 0.2, jz + 0.235, machiRgb("#1c222b"))
    m.box(jx - 0.3, jx + 0.3, 1.05, 1.3, jz - 0.22, jz + 0.22, machiRgb("#ffb547"), 1.5f)
}
