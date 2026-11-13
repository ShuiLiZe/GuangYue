// 町景会动的东西（Folia machi engine/actors 与 fx/steam.ts）：
// - 风扇（konbiniScene direct：fan.rotation.z = −ambient × (fanSpeed + i × 0.37) × 2π）
// - 偶尔开过的车（actors/schedules.ts resolveMachiCar + actors/traffic.ts MachiTraffic）：按 9.5 s 窗口哈希挑车道 / 颜色，
//   车身、车灯、脚下软影、前灯光斑都按底座边缘裁掉（|x|、|z| ≤ half），车从底座边上“开出来”。
// - のぼり旗（actors/nobori.ts）：竖长布面，顶点着色器里沿布面法线的波；字面按 Canvas 画（竖排）。
// - 白气（fx/steam.ts）：朝镜头的软云片，按氛围时钟升起、变大、被风吹偏、淡出，加色。
// 差异（如实记）：车轮滚动没做（纯圆柱，转不转看不出）；车身的 sheen 光泽没做；旗只吃半球光 + 太阳（场景点光不照旗）；
// 旗上的图标用 ♪（主题图标集未移植）。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 会动的一块网格：本地坐标；[model] 列主序 4×4（null 时藏起来）；[mode] 画法；[clip] > 0 时裁掉底座外面。 */
internal class MachiDynPart(val mesh: FloatArray, val mode: Int = LIT, val tint: FloatArray? = null, val clip: Float = 0f) {
    companion object {
        /** 照常受光（自发光件照常发光）。 */
        const val LIT = 0
        /** 软影：dst × (1 − 颜色)。 */
        const val SHADOW = 1
        /** 加色：颜色（线性，原值）× tint。 */
        const val ADD = 2
    }
    var vao = 0
    var count = 0
    val model = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    var visible = true
}

internal class MachiActorFrame(val ambient: Double, val seed: String, val trafficChance: Double)

internal interface MachiActor {
    val parts: List<MachiDynPart>
    fun update(frame: MachiActorFrame)
}

/** 列主序模型矩阵 = T(o) · R（R 行主序 3×3）。 */
internal fun machiSetModel(out: FloatArray, r: DoubleArray, o: DoubleArray) {
    for (row in 0..2) for (col in 0..2) out[col * 4 + row] = r[row * 3 + col].toFloat()
    out[3] = 0f; out[7] = 0f; out[11] = 0f
    out[12] = o[0].toFloat(); out[13] = o[1].toFloat(); out[14] = o[2].toFloat(); out[15] = 1f
}

/** 小球（UV 球，seg × rings）。 */
internal fun MachiMesh.machiSphere(c: DoubleArray, r: Double, color: FloatArray, emissive: Float = 0f, seg: Int = 8, rings: Int = 6) {
    fun pt(i: Int, j: Int): DoubleArray {
        val th = 2 * PI * i / seg; val ph = PI * j / rings
        return doubleArrayOf(sin(ph) * cos(th), cos(ph), sin(ph) * sin(th))
    }
    for (i in 0 until seg) for (j in 0 until rings) {
        val n = listOf(pt(i, j), pt(i, j + 1), pt(i + 1, j + 1), pt(i + 1, j))
        val mid = doubleArrayOf(n.sumOf { it[0] } / 4, n.sumOf { it[1] } / 4, n.sumOf { it[2] } / 4)
        val ps = n.map { doubleArrayOf(c[0] + it[0] * r, c[1] + it[1] * r, c[2] + it[2] * r) }
        quad(ps[0], ps[3], ps[2], ps[1], mid, color, emissive)
    }
}

// ---- 风扇 ----

/** 一台外机的风扇：叶片网格（风扇本地坐标）、风扇坐标系原点与朝向（MachiFrame.o / r）。 */
internal class MachiFanSpec(val mesh: FloatArray, val o: DoubleArray, val r: DoubleArray)

/** 车站双闪（stationScene hazardOn）：每 18 s 一个窗口，按哈希决定打不打（打的话闪 6 s，每 1/2.8 s 翻一次）。 */
internal fun resolveMachiHazardOn(time: Double, seed: String): Boolean {
    val window = 18.0
    val index = floor(time / window).toInt()
    val key = machiHashSeed("hazard:$seed")
    if (machiHash01(key, index, 0x21) > 0.5) return false
    val local = time - index * window - machiHash01(key, index, 0x42) * 10
    if (local < 0 || local > 6) return false
    return floor(local * 2.8).toInt() % 2 == 0
}

/** 车站：车下软影（静态，一直在）、会打双闪那辆的小灯（亮 2.4 / 灭 0.06 × 2.4）、旅馆箭头走马灯（每四个一组，亮位沿箭头走）。 */
internal class MachiStationActors(shadows: FloatArray, hazardOn: FloatArray, hazardOff: FloatArray, bulbs: List<FloatArray>) : MachiActor {
    private val shadow = MachiDynPart(shadows, MachiDynPart.SHADOW)
    private val on = MachiDynPart(hazardOn)
    private val off = MachiDynPart(hazardOff)
    private val bulbParts = bulbs.map { MachiDynPart(it) }
    override val parts = listOf(shadow, on, off) + bulbParts
    override fun update(frame: MachiActorFrame) {
        val h = resolveMachiHazardOn(frame.ambient, frame.seed)
        on.visible = h; off.visible = !h
        // stationSigns BULB_VERTEX：on = step(0.5, fract((index − 6t) / 4))；按 index mod 4 分四组，每组一份亮、一份暗。
        for (k in 0 until bulbParts.size / 2) {
            val x = (k - frame.ambient * 6.0) / 4.0
            val lit = x - floor(x) >= 0.5
            bulbParts[k * 2].visible = lit; bulbParts[k * 2 + 1].visible = !lit
        }
    }
}

internal class MachiFans(private val fans: List<MachiFanSpec>, private val fanSpeed: Double) : MachiActor {
    override val parts = fans.map { MachiDynPart(it.mesh) }
    override fun update(frame: MachiActorFrame) {
        fans.forEachIndexed { i, f ->
            val a = -frame.ambient * (fanSpeed + i * 0.37) * PI * 2
            machiSetModel(parts[i].model, MachiFrame.mul(f.r, MachiFrame.rotZ(a)), f.o)
        }
    }
}

// ---- 车流（schedules.ts + traffic.ts） ----

/** 车速（米/秒）。 */
internal const val MACHI_CAR_SPEED = 6.5
private const val MACHI_CAR_WINDOW = 9.5

/** 车道：from (x, z) → to (x, z)。 */
internal class MachiLane(val fromX: Double, val fromZ: Double, val toX: Double, val toZ: Double) {
    val length get() = hypot(toX - fromX, toZ - fromZ)
}

internal class MachiCarPass(val lane: Int, val distance: Double, val colorIndex: Int)

/** schedules.ts resolveMachiCar 照译。 */
internal fun resolveMachiCar(time: Double, seed: String, lanes: List<MachiLane>, chance: Double = 0.62): MachiCarPass? {
    if (lanes.isEmpty()) return null
    val key = machiHashSeed("car:$seed")
    val window = floor(time / MACHI_CAR_WINDOW).toInt()
    for (index in intArrayOf(window, window - 1)) {
        if (machiHash01(key, index, 0x11) > chance) continue
        val start = index * MACHI_CAR_WINDOW + machiHash01(key, index, 0x22) * (MACHI_CAR_WINDOW * 0.5)
        val local = time - start
        val lane = floor(machiHash01(key, index, 0x33) * lanes.size).toInt()
        if (local < 0 || local > lanes[lane].length / MACHI_CAR_SPEED) continue
        return MachiCarPass(lane, local * MACHI_CAR_SPEED, floor(machiHash01(key, index, 0x44) * 1000).toInt())
    }
    return null
}

/** createMachiRuntime TRAFFIC_CHANCE（normal）。 */
internal const val MACHI_TRAFFIC_CHANCE = 0.6
internal val MACHI_TRAFFIC_COLORS = listOf("#e8eaee", "#aeb5bf", "#25292f", "#22385f", "#b8322e", "#f0c23a")

/** 软椭圆（traffic.ts createSoftEllipseTexture）烘成 16×16 网格的顶点色：forward 时靠 −u 一侧更亮。 */
internal fun MachiMesh.machiSoftEllipse(cx: Double, y: Double, cz: Double, w: Double, d: Double, level: Double, forward: Boolean, xf: ((DoubleArray) -> DoubleArray)? = null) {
    val n = 16
    fun v(u: Double, t: Double): FloatArray {
        val dd = sqrt(u * u + t * t)
        val bias = if (forward) 0.45 + 0.55 * (1 - (u + 1) / 2) else 1.0
        val k = (max(0.0, 1 - dd).pow(1.6) * bias * level).toFloat()
        return floatArrayOf(k, k, k)
    }
    val up = doubleArrayOf(0.0, 1.0, 0.0)
    for (i in 0 until n) for (j in 0 until n) {
        val u0 = -1 + 2.0 * i / n; val u1 = -1 + 2.0 * (i + 1) / n; val t0 = -1 + 2.0 * j / n; val t1 = -1 + 2.0 * (j + 1) / n
        fun p(u: Double, t: Double) = doubleArrayOf(cx + u * w / 2, y, cz + t * d / 2).let { xf?.invoke(it) ?: it }
        quadColors(p(u0, t1), p(u1, t1), p(u1, t0), p(u0, t0), up, v(u0, t1), v(u1, t1), v(u1, t0), v(u0, t0))
    }
}

internal class MachiTraffic(
    private val lanes: List<MachiLane>, private val roadY: Double, palette: MachiPalette, half: Double,
) : MachiActor {
    private val bodies: List<MachiDynPart>
    private val shadow: MachiDynPart
    private val pool: MachiDynPart
    override val parts: List<MachiDynPart>
    init {
        val clip = half.toFloat()
        val glass = machiRgb("#1a2130"); val rubber = machiRgb("#15171c")
        val head = machiRgb(palette.streetLight); val tail = machiRgb(palette.stopRed)
        bodies = MACHI_TRAFFIC_COLORS.mapIndexed { ci, hex ->
            val m = MachiMesh(); val f = MachiFrame(m); val body = machiRgb(hex)
            // 车身沿 +x，车头在 +x（roundedBox 按直角盒）。
            f.boxC(3.3, 0.56, 1.46, 0.0, 0.5, 0.0, body)
            f.boxC(1.92, 0.5, 1.32, -0.22, 1.02, 0.0, glass)
            f.boxC(1.78, 0.08, 1.36, -0.22, 1.3, 0.0, body)
            // 出租车顶灯：MeshBasicMaterial(1.6, 1.25, 0.6)（线性）≈ sRGB #ffe7a2 × 1.6。
            if (ci == MACHI_TRAFFIC_COLORS.size - 1) f.boxC(0.362, 0.142, 0.202, -0.2, 1.41, 0.0, machiRgb("#ffe7a2"), 1.6f)
            for (side in intArrayOf(-1, 1)) {
                f.boxC(0.06, 0.13, 0.32, 1.64, 0.6, side * 0.48, head, 3.2f)
                f.boxC(0.06, 0.12, 0.26, -1.64, 0.64, side * 0.52, tail, 2.6f)
            }
            for (w in listOf(doubleArrayOf(1.05, 0.66), doubleArrayOf(1.05, -0.66), doubleArrayOf(-1.05, 0.66), doubleArrayOf(-1.05, -0.66))) {
                m.ktube(doubleArrayOf(w[0], 0.27, w[1] - 0.1), doubleArrayOf(w[0], 0.27, w[1] + 0.1), 0.27, rubber, segments = 14)
            }
            MachiDynPart(m.toArray(), clip = clip)
        }
        shadow = MachiDynPart(MachiMesh().apply { machiSoftEllipse(0.0, 0.004, 0.0, 3.7, 1.9, 0.55, false) }.toArray(), MachiDynPart.SHADOW, clip = clip)
        val sl = machiLinear(palette.streetLight)
        pool = MachiDynPart(MachiMesh().apply { machiSoftEllipse(3.4, 0.008, 0.0, 3.6, 2.4, 1.0, true) }.toArray(), MachiDynPart.ADD, FloatArray(3) { sl[it] * 0.3f }, clip)
        parts = bodies + shadow + pool
    }
    /** 这一帧路上有没有车。 */
    var active = false
        private set

    override fun update(frame: MachiActorFrame) {
        val pass = resolveMachiCar(frame.ambient, frame.seed, lanes, frame.trafficChance)
        active = pass != null
        parts.forEach { it.visible = false }
        if (pass == null) return
        val lane = lanes[pass.lane]
        val dx = lane.toX - lane.fromX; val dz = lane.toZ - lane.fromZ
        val t = pass.distance / lane.length
        val o = doubleArrayOf(lane.fromX + dx * t, roadY, lane.fromZ + dz * t)
        val r = MachiFrame.rotY(atan2(-dz, dx))
        val body = bodies[pass.colorIndex % MACHI_TRAFFIC_COLORS.size]
        for (p in listOf(body, shadow, pool)) { p.visible = true; machiSetModel(p.model, r, o) }
    }
}

// ---- のぼり旗（nobori.ts） ----

internal class MachiNoboriSpot(val x: Double, val y: Double, val z: Double, val rotationY: Double)
internal class MachiNoboriFace(val background: String, val ink: String, val text: String, val icon: String? = null)

private const val FLAG_W = 0.46
private const val FLAG_H = 1.5
private const val FLAG_BOTTOM = 0.62
private const val FLAG_SEG = 10

/** 旗杆、横杆、底座（静态，合进场景网格）。 */
internal fun MachiMesh.addMachiNoboriPoles(spots: List<MachiNoboriSpot>, palette: MachiPalette) {
    val white = floatArrayOf(1f, 1f, 1f); val dark = machiRgb(palette.darkMetal)
    for (s in spots) {
        val f = MachiFrame(this).child(s.x, s.y, s.z, ry = s.rotationY)
        f.cylinderAt(0.022, FLAG_BOTTOM + FLAG_H + 0.12, 0.0, 0.0, 0.0, white, rTop = 0.018, segments = 6)
        f.tube(doubleArrayOf(-0.02, FLAG_BOTTOM + FLAG_H + 0.01, 0.0), doubleArrayOf(FLAG_W + 0.02, FLAG_BOTTOM + FLAG_H + 0.01, 0.0), 0.012, white, segments = 5)
        f.cylinderAt(0.16, 0.12, 0.0, 0.0, 0.0, dark, rTop = 0.14, segments = 10)
    }
}

private val MACHI_CJK = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")

/** nobori.ts paint：底色、上下白边、穿杆布、可选图标、竖排字（中日韩逐字竖排，拉丁字转 90°）。 */
internal fun paintMachiNobori(bitmap: Bitmap, face: MachiNoboriFace) {
    val c = Canvas(bitmap)
    val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.parseColor(face.background); c.drawRect(0f, 0f, w, h, p)
    p.color = android.graphics.Color.argb(217, 255, 255, 255)
    c.drawRect(0f, h * 0.03f, w, h * 0.042f, p); c.drawRect(0f, h * 0.955f, w, h * 0.967f, p)
    p.color = android.graphics.Color.argb(46, 0, 0, 0); c.drawRect(0f, 0f, w * 0.08f, h, p)
    p.color = android.graphics.Color.parseColor(face.ink)
    p.typeface = machiTypeface(800)
    p.textAlign = Paint.Align.CENTER
    var top = h * 0.07f
    if (face.icon != null) {
        val size = w * 0.62f
        p.textSize = size * 0.9f
        val fm = p.fontMetrics
        c.drawText(face.icon, w / 2 + w * 0.03f, top + size / 2 - (fm.ascent + fm.descent) / 2, p)
        top += size + h * 0.03f
    }
    val text = face.text.trim().ifEmpty { " " }
    val bottom = h * 0.93f; val avail = bottom - top; val x = w * 0.53f
    if (!MACHI_CJK.containsMatchIn(text)) {
        var px = w * 0.8f
        p.textSize = px
        val tw = p.measureText(text)
        if (tw > avail) { px = px * avail / tw; p.textSize = px }
        c.save(); c.translate(x, (top + bottom) / 2); c.rotate(90f)
        val fm = p.fontMetrics
        c.drawText(text, 0f, -(fm.ascent + fm.descent) / 2, p)
        c.restore()
        return
    }
    val chars = ArrayList<String>().also { l -> var i = 0; while (i < text.length) { val cp = text.codePointAt(i); l += String(Character.toChars(cp)); i += Character.charCount(cp) } }
    val px = min(w * 0.82f, avail / chars.size / 1.04f)
    p.textSize = px
    val step = px * 1.04f
    val start = top + (avail - step * chars.size) / 2 + step / 2
    val fm = p.fontMetrics
    chars.forEachIndexed { i, ch -> c.drawText(ch, x, start + i * step - (fm.ascent + fm.descent) / 2, p) }
}

private val NOBORI_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uTime;
uniform float uWind;
uniform float uPhase;
out vec2 vUv;
out vec3 vNor;
out vec3 vWorld;
void main() {
    // 布面沿本地 +x 从旗杆往外，离旗杆越远摆幅越大（nobori.ts WAVE_VERTEX）。
    vec3 p = vec3(aPos, 0.0);
    float u = clamp(p.x / ${FLAG_W}, 0.0, 1.0);
    float wave = sin(p.x * 7.0 - uTime * (3.2 + uWind * 2.0) + uPhase + p.y * 1.3) + 0.4 * sin(p.x * 13.0 - uTime * 5.1 + uPhase * 1.7);
    p.z += wave * u * (0.035 + 0.05 * uWind);
    p.x -= abs(wave) * u * 0.012;
    vUv = vec2(aPos.x / ${FLAG_W}, 1.0 - aPos.y / ${FLAG_H});
    vNor = mat3(uModel) * vec3(0.0, 0.0, 1.0);
    vec4 world = uModel * vec4(p.x, p.y + ${FLAG_BOTTOM}, p.z, 1.0);
    vWorld = world.xyz;
    gl_Position = uVP * world;
}
"""

private const val NOBORI_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec3 vNor;
in vec3 vWorld;
uniform sampler2D uTex;
uniform int uLightCount;
uniform vec3 uLightPos[6];
uniform vec3 uLightColor[6];
uniform float uLightDist[6];
uniform vec3 uSpotDir[6];
uniform vec2 uSpotCos[6];
uniform vec3 uSunDir;
uniform vec3 uSun;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
out vec4 fragColor;
vec3 toLinear(vec3 c) { return mix(c * 0.0773993808, pow(c * 0.9478672986 + 0.0521327014, vec3(2.4)), step(0.04045, c)); }
float toonStep(float dotNL) {
    float coord = clamp(dotNL * 0.5 + 0.5, 0.0, 0.9999);
    int i = int(floor(coord * 4.0));
    return i == 0 ? 86.0 / 255.0 : (i == 1 ? 146.0 / 255.0 : (i == 2 ? 206.0 / 255.0 : 1.0));
}
void main() {
    vec3 albedo = toLinear(texture(uTex, vUv).rgb);
    vec3 n = normalize(gl_FrontFacing ? vNor : -vNor);
    vec3 irr = mix(uHemiGround, uHemiSky, 0.5 * n.y + 0.5) + uSun * toonStep(dot(n, uSunDir));
    // 场景点光 / 聚光（路灯、店招灯）照旗面：与主着色器同一套衰减（physicallyCorrect 1/d² × (1−(d/dist)⁴)²）与 toon 阶梯。
    for (int i = 0; i < 6; i++) {
        if (i >= uLightCount) break;
        vec3 toL = uLightPos[i] - vWorld;
        float d = length(toL);
        vec3 l = toL / max(d, 1e-4);
        float fall = 1.0 / max(d * d, 0.01);
        float cut = clamp(1.0 - pow(d / uLightDist[i], 4.0), 0.0, 1.0);
        fall *= cut * cut;
        if (uSpotCos[i].x > -1.5) fall *= smoothstep(uSpotCos[i].x, uSpotCos[i].y, dot(-l, uSpotDir[i]));
        irr += uLightColor[i] * fall * toonStep(dot(n, l));
    }
    // MeshToonMaterial(map) + emissive 0.18 × emissiveMap。
    fragColor = vec4(albedo * irr / 3.14159265 + albedo * 0.18, 1.0);
}
"""

/** 主着色器那组场景灯的 uniform 数组（最多 6 盏），供旗面等独立着色器复用。 */
internal class MachiPointLights(val count: Int, val pos: FloatArray, val color: FloatArray, val dist: FloatArray, val spotDir: FloatArray, val spotCos: FloatArray)

internal class MachiNobori(private val spots: List<MachiNoboriSpot>, private val faces: List<MachiNoboriFace>) {
    private var program = 0
    private var vao = 0
    private var count = 0
    private val tex = IntArray(spots.size)
    private val model = FloatArray(16)

    fun init(link: (String, String) -> Int) {
        program = link(NOBORI_VS, NOBORI_FS)
        val data = ArrayList<Float>()
        fun v(i: Int, j: Int) { data += (FLAG_W * i / FLAG_SEG).toFloat(); data += (FLAG_H * j / 4).toFloat() }
        for (i in 0 until FLAG_SEG) for (j in 0 until 4) { v(i, j); v(i + 1, j); v(i + 1, j + 1); v(i, j); v(i + 1, j + 1); v(i, j + 1) }
        count = data.size / 2
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.toFloatArray()); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glGenTextures(spots.size, tex, 0)
        spots.indices.forEach { i ->
            val bmp = Bitmap.createBitmap(128, 416, Bitmap.Config.ARGB_8888)
            paintMachiNobori(bmp, faces[i % faces.size])
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i])
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            bmp.recycle()
        }
    }

    /** wind = 0.35 + 0.65 × min(1, rain)。 */
    /** lights：与主着色器同一组点光 / 聚光（数量、位置、颜色、距离、聚光方向、聚光余弦）；null = 不打点光。 */
    fun draw(vp: FloatArray, time: Double, wind: Double, light: MachiNightLight, lights: MachiPointLights? = null) {
        if (program == 0) return
        GLES30.glUseProgram(program)
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glUniform1f(u("uTime"), time.toFloat())
        GLES30.glUniform1f(u("uWind"), wind.toFloat())
        GLES30.glUniform3fv(u("uSunDir"), 1, light.sunDir, 0)
        GLES30.glUniform3fv(u("uSun"), 1, light.sun, 0)
        GLES30.glUniform3fv(u("uHemiSky"), 1, light.hemiSky, 0)
        GLES30.glUniform3fv(u("uHemiGround"), 1, light.hemiGround, 0)
        val nl = lights?.count ?: 0
        GLES30.glUniform1i(u("uLightCount"), nl)
        if (lights != null && nl > 0) {
            GLES30.glUniform3fv(u("uLightPos"), 6, lights.pos, 0)
            GLES30.glUniform3fv(u("uLightColor"), 6, lights.color, 0)
            GLES30.glUniform1fv(u("uLightDist"), 6, lights.dist, 0)
            GLES30.glUniform3fv(u("uSpotDir"), 6, lights.spotDir, 0)
            GLES30.glUniform2fv(u("uSpotCos"), 6, lights.spotCos, 0)
        }
        GLES30.glUniform1i(u("uTex"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(vao)
        spots.forEachIndexed { i, s ->
            machiSetModel(model, MachiFrame.rotY(s.rotationY), doubleArrayOf(s.x, s.y, s.z))
            GLES30.glUniformMatrix4fv(u("uModel"), 1, false, model, 0)
            GLES30.glUniform1f(u("uPhase"), (i * 1.9).toFloat())
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        }
        GLES30.glBindVertexArray(0)
    }
}

// ---- 白气（fx/steam.ts） ----

internal class MachiSteamSource(val x: Double, val y: Double, val z: Double, val puffs: Int, val period: Double, val size: Double)

private const val STEAM_VS = """#version 300 es
layout(location = 0) in vec4 aCorner; // xy 片内坐标 −0.5..0.5，zw uv
layout(location = 1) in vec4 aSource; // xyz 出气口，w 周期
layout(location = 2) in vec2 aSeed;   // 相位、大小
uniform mat4 uVP;
uniform float uTime;
uniform vec2 uWind;
uniform vec3 uRight;
uniform vec3 uUp;
out vec2 vUv;
out float vFade;
out float vSeed;
void main() {
    float life = fract(uTime / aSource.w + aSeed.x);
    float rise = life * 1.6;
    vec3 center = aSource.xyz + vec3(uWind.x * life * 1.8 + sin(life * 6.0 + aSeed.x * 20.0) * 0.08, rise, uWind.y * life * 1.8);
    float size = (0.25 + life * 0.9) * aSeed.y;
    vec3 world = center + (uRight * aCorner.x + uUp * aCorner.y) * size;
    vUv = aCorner.zw;
    vFade = smoothstep(0.0, 0.15, life) * (1.0 - smoothstep(0.55, 1.0, life));
    vSeed = aSeed.x * 31.0;
    gl_Position = uVP * vec4(world, 1.0);
}
"""

private const val STEAM_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
uniform float uTime;
in vec2 vUv;
in float vFade;
in float vSeed;
out vec4 fragColor;
float sHash(vec2 p) { return fract(sin(dot(p, vec2(12.7, 61.3))) * 43758.5453); }
float sNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(sHash(i), sHash(i + vec2(1.0, 0.0)), u.x), mix(sHash(i + vec2(0.0, 1.0)), sHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    vec2 c = vUv - 0.5;
    float shape = 1.0 - smoothstep(0.15, 0.5, length(c));
    float puff = sNoise(vUv * 3.0 + vec2(vSeed, uTime * 0.2)) * 0.6 + sNoise(vUv * 6.5 - vec2(uTime * 0.3, vSeed)) * 0.4;
    float alpha = shape * smoothstep(0.25, 0.75, puff) * vFade;
    fragColor = vec4(uColor * alpha, 1.0);
}
"""

internal class MachiSteam(private val sources: List<MachiSteamSource>) {
    private var program = 0
    private var vao = 0
    private var count = 0

    fun init(link: (String, String) -> Int) {
        program = link(STEAM_VS, STEAM_FS)
        val data = ArrayList<Float>()
        val corners = listOf(floatArrayOf(-0.5f, -0.5f, 0f, 0f), floatArrayOf(0.5f, -0.5f, 1f, 0f), floatArrayOf(0.5f, 0.5f, 1f, 1f),
            floatArrayOf(-0.5f, -0.5f, 0f, 0f), floatArrayOf(0.5f, 0.5f, 1f, 1f), floatArrayOf(-0.5f, 0.5f, 0f, 1f))
        for (s in sources) for (i in 0 until s.puffs) {
            val phase = i.toFloat() / s.puffs
            val size = (s.size * (0.8 + 0.4 * ((i * 7) % 5) / 5.0)).toFloat()
            for (c in corners) { c.forEach { data += it }; data += s.x.toFloat(); data += s.y.toFloat(); data += s.z.toFloat(); data += s.period.toFloat(); data += phase; data += size }
        }
        count = data.size / 10
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.toFloatArray()); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 40, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 40, 16)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 40, 32)
        GLES30.glBindVertexArray(0)
    }

    /** color = lerp(skyBottom, #dde6f2, 0.5) × 0.32（线性）；wind = (sin windYaw, cos windYaw) × 0.06。 */
    fun draw(vp: FloatArray, right: FloatArray, up: FloatArray, time: Double, wind: FloatArray, color: FloatArray) {
        if (program == 0 || count == 0) return
        GLES30.glUseProgram(program)
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glUniform1f(u("uTime"), time.toFloat())
        GLES30.glUniform2fv(u("uWind"), 1, wind, 0)
        GLES30.glUniform3fv(u("uRight"), 1, right, 0)
        GLES30.glUniform3fv(u("uUp"), 1, up, 0)
        GLES30.glUniform3fv(u("uColor"), 1, color, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }
}

/** steam.ts syncColors。 */
internal fun machiSteamColor(skyBottom: String): FloatArray {
    val a = machiLinear(skyBottom); val b = machiLinear("#dde6f2")
    return FloatArray(3) { (a[it] + (b[it] - a[it]) * 0.5f) * 0.32f }
}

/** 运行时 atmosphere.wind = (sin windYaw, cos windYaw) × 0.06。 */
internal fun machiSteamWind(windYaw: Double) = floatArrayOf((sin(windYaw) * 0.06).toFloat(), (cos(windYaw) * 0.06).toFloat())
