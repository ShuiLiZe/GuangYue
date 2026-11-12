// 町景「城寨酒吧」（Folia machi scenes/kowloon）白模起步：layout.ts 的尺寸表逐项照抄，blocks.ts 的楼体 / 掏空酒吧 / 楼板线 /
// 窗格规则照译（行从一楼 3 m 起每 1 m、列宽 0.64 m、窗 0.36×0.48 m、伸出 0.035 m、46% 亮窗按 WINDOW_WEIGHTS 配色），
// 台座按 plinth.ts（半边 6 m、三层土石）。还没移：铁窗笼、冷气机、晾衫竹、加建、水管、楼顶杂物、招牌、大排档、电线杆、
// 全息、天气。随机数用自己的种子序列（不与 Folia 的 createVisualizerRng 逐个对齐，亮哪扇窗会不同）。
// 网格：每顶点 10 个 float（位置 3、法线 3、颜色 3、自发光 1），三角形列表。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.floor
import kotlin.math.max

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
internal class MachiKowloonModel(val mesh: FloatArray, val litWindows: Int, val darkWindows: Int)

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

private fun inAvoid(x: Double, y: Double, z: Double) = AVOID.any { a -> x in a[0]..a[1] && y in a[2]..a[3] && z in a[4]..a[5] }

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
    var lit = 0; var dark = 0
    val slab = machiRgb(art.getValue("slab"))
    val windowDark = machiRgb(art.getValue("windowDark"))
    for (b in MachiKowloonLayout.BLOCKS) {
        val wall = machiRgb(art.getValue(b.wall))
        val hollow = b.hollow
        if (hollow != null) {
            val (hx0, hx1, hz0, hy1) = listOf(hollow[0], hollow[1], hollow[2], hollow[3])
            m.box(b.x0, b.x1, hy1, b.height, b.z0, b.z1, wall)
            m.box(b.x0, b.x1, 0.0, hy1, b.z0, hz0, wall)
            m.box(b.x0, hx0, 0.0, hy1, hz0, b.z1, wall)
            m.box(hx1, b.x1, 0.0, hy1, hz0, b.z1, wall)
            // 酒吧里：地板、后墙暖光（interiorLight 压暗）、天花。
            m.box(hx0, hx1, 0.0, 0.06, hz0, b.z1, machiRgb(art.getValue("barFloor")))
            m.quad(doubleArrayOf(hx0, 0.0, hz0 + 0.01), doubleArrayOf(hx1, 0.0, hz0 + 0.01), doubleArrayOf(hx1, hy1, hz0 + 0.01), doubleArrayOf(hx0, hy1, hz0 + 0.01),
                doubleArrayOf(0.0, 0.0, 1.0), machiRgb("#ffc887"), 0.55f)
        } else {
            m.box(b.x0, b.x1, 0.0, b.height, b.z0, b.z1, wall)
        }
        for (f in facesOf(b)) {
            fun pt(s: Double, out: Double) = doubleArrayOf(f.ox + f.ax * s + f.nx * out, 0.0, f.oz + f.az * s + f.nz * out)
            // 楼板线：每层一道。
            var y = MachiKowloonLayout.FLOOR_GROUND
            while (y < b.height - 0.3) {
                val a = pt(0.0, 0.05); val c = pt(f.width, 0.05)
                faceQuad(m, f, a, c, y - 0.06, y, slab, 0f)
                y += MachiKowloonLayout.FLOOR_HEIGHT
            }
            val columns = max(1, floor((f.width - 0.2) / 0.64).toInt())
            val spacing = f.width / columns
            y = MachiKowloonLayout.FLOOR_GROUND
            while (y + 0.85 <= b.height - 0.15) {
                for (col in 0 until columns) {
                    val s = (col + 0.5) * spacing + (rnd.next() - 0.5) * 0.08
                    val center = y + 0.55
                    val probe = pt(s, 0.2)
                    val litRoll = rnd.next(); val colorRoll = rnd.next()
                    if (insideAnyBlock(probe[0], center, probe[2]) || inAvoid(probe[0], center, probe[2])) continue
                    val a = pt(s - 0.18, 0.035); val c = pt(s + 0.18, 0.035)
                    if (litRoll < 0.46) {
                        lit++
                        faceQuad(m, f, a, c, center - 0.24, center + 0.24, machiRgb(MachiKowloonLayout.WINDOW_LIGHTS[pickWeighted(colorRoll)]), 1f)
                    } else {
                        dark++
                        faceQuad(m, f, a, c, center - 0.24, center + 0.24, windowDark, 0f)
                    }
                }
                y += MachiKowloonLayout.FLOOR_HEIGHT
            }
        }
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
    return MachiKowloonModel(m.toArray(), lit, dark)
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
