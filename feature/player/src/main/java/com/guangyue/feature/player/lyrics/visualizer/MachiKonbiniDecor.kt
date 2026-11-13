// 便利店剩下的招牌（Folia scenes/konbini/konbiniSigns.ts + konbiniSignPaint.ts）：玻璃海报两张、窗内霓虹图标、
// 广告塔顶的屋顶 logo、门口 A 字立牌；以及招牌共用的兜底图标（VisualizerMachi.tsx MACHI_FALLBACK_ICONS：
// Store / Umbrella / CloudRain / Moon，Lucide 线稿，engine/signRaster.ts drawIcon：24 格网、圆头圆角描边）。
// 差异（如实记）：主题自带的图标集（theme.lyricsIcons）未移植，一律走兜底四个；Lucide 路径取 lucide-static 1.72，
// 与 Folia 依赖的 lucide-react 1.48 可能有细微出入；受光的牌面（屋顶 logo、A 字牌）只吃半球光 + 太阳，场景点光不照。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** MACHI_FALLBACK_ICONS（主题没给图标时 icon(i) = 兜底第 i 个）。 */
internal val MACHI_FALLBACK_ICONS = listOf("Store", "Umbrella", "CloudRain", "Moon")

private val MACHI_LUCIDE = mapOf(
    "Store" to listOf(
        "M15 21v-5a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5",
        "M17.774 10.31a1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.451 0 1.12 1.12 0 0 0-1.548 0 2.5 2.5 0 0 1-3.452 0 1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.77-3.248l2.889-4.184A2 2 0 0 1 7 2h10a2 2 0 0 1 1.653.873l2.895 4.192a2.5 2.5 0 0 1-3.774 3.244",
        "M4 10.95V19a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8.05",
    ),
    "Umbrella" to listOf(
        "M12 13v7a2 2 0 0 0 4 0",
        "M12 2v2",
        "M20.992 13a1 1 0 0 0 .97-1.274 10.284 10.284 0 0 0-19.923 0A1 1 0 0 0 3 13z",
    ),
    "CloudRain" to listOf(
        "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242",
        "M16 14v6",
        "M8 14v6",
        "M12 16v6",
    ),
    "Moon" to listOf(
        "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
    ),
)

private val machiIconCache = HashMap<String, Path>()

private fun machiIconPath(name: String?): Path {
    val key = if (name != null && MACHI_LUCIDE.containsKey(name)) name else MACHI_FALLBACK_ICONS[0]
    return machiIconCache.getOrPut(key) {
        val out = Path()
        MACHI_LUCIDE.getValue(key).forEach { d -> out.addPath(PathParser().parsePathString(d).toPath().asAndroidPath()) }
        out
    }
}

/**
 * signRaster.ts drawIcon：图标放进 (x, y) 起、边长 size 的方格，24 格网缩放，描边宽 lineWidth（格网单位）。
 * [blur] > 0 时画成光晕（Canvas2D shadowBlur，像素）：σ = blur / 2。
 */
internal fun drawMachiIcon(c: Canvas, name: String?, x: Float, y: Float, size: Float, color: Int, lineWidth: Float, blur: Float = 0f) {
    val path = Path(machiIconPath(name))
    val m = Matrix(); m.setScale(size / 24f, size / 24f); m.postTranslate(x, y)
    path.transform(m)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        strokeWidth = lineWidth * size / 24f; this.color = color
    }
    if (blur > 0f) {
        // 光晕：先画一遍模糊（BlurMaskFilter 半径 ≈ 2σ），再画实线（Canvas2D 的 shadow 也会把本体画出来）。
        val glow = Paint(p).apply { maskFilter = BlurMaskFilter(max(1f, blur), BlurMaskFilter.Blur.NORMAL) }
        c.drawPath(path, glow)
    }
    c.drawPath(path, p)
}

private fun hexColor(hex: String): Int = android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")

private fun fit(p: Paint, text: String, maxWidth: Float, maxPx: Float, minPx: Float = 8f): Float {
    p.textSize = maxPx
    val w = p.measureText(text)
    val px = if (w > maxWidth) max(minPx, maxPx * maxWidth / w) else maxPx
    p.textSize = px
    return px
}

private fun Paint.mid() = fontMetrics.let { -(it.ascent + it.descent) / 2 }

/** paintKonbiniPoster：色底、细框、大图标、标题（两行内）、小字、角上斜贴的标签。 */
internal fun paintKonbiniPosterBitmap(bitmap: Bitmap, heading: String, body: String, icon: String, background: String, ink: String, accent: String, tag: String) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(hexColor(background))
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.STROKE; p.color = hexColor(ink); p.alpha = (0.18 * 255).toInt(); p.strokeWidth = w * 0.02f
    c.drawRect(w * 0.05f, w * 0.05f, w * 0.95f, h - w * 0.05f, p)
    p.style = Paint.Style.FILL; p.alpha = 255
    val iconSize = w * 0.5f
    drawMachiIcon(c, icon, (w - iconSize) / 2, h * 0.12f, iconSize, hexColor(ink), 2.4f)
    p.typeface = machiTypeface(700); p.textAlign = Paint.Align.CENTER
    val headingPx = fit(p, heading, w * 0.84f, w * 0.15f)
    machiWrapText(p, heading, w * 0.84f, 2).forEachIndexed { i, line -> c.drawText(line, w / 2, h * 0.62f + i * headingPx * 1.15f + p.mid(), p) }
    p.alpha = (0.75 * 255).toInt(); p.typeface = machiTypeface(400)
    fit(p, body, w * 0.8f, w * 0.075f)
    c.drawText(body, w / 2, h * 0.86f + p.mid(), p)
    p.alpha = 255
    c.save(); c.translate(w * 0.78f, h * 0.07f); c.rotate((0.22 * 180 / PI).toFloat())
    p.color = hexColor(accent)
    c.drawRoundRect(-w * 0.17f, -w * 0.06f, w * 0.17f, w * 0.06f, w * 0.03f, w * 0.03f, p)
    p.color = 0xFFFFFFFF.toInt(); p.typeface = machiTypeface(800)
    fit(p, tag, w * 0.28f, w * 0.08f)
    c.drawText(tag, 0f, w * 0.005f + p.mid(), p)
    c.restore()
}

/** paintKonbiniNeon：透明底上发光的图标线稿（光晕同色，芯近白）。 */
internal fun paintKonbiniNeonBitmap(bitmap: Bitmap, icon: String, color: String) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    bitmap.eraseColor(0)
    val size = minOf(w, h) * 0.7f; val x = (w - size) / 2; val y = (h - size) / 2
    drawMachiIcon(c, icon, x, y, size, hexColor(color), 1.6f, blur = size * 0.12f)
    drawMachiIcon(c, icon, x, y, size, 0xFFFFFFFF.toInt(), 0.6f, blur = size * 0.04f)
}

/** paintKonbiniRoofLogo：品牌色三角、内圈两道描边、中间大图标（转 rotation，从默认镜头看是正的）。corners 是画布像素。 */
internal fun paintKonbiniRoofLogoBitmap(bitmap: Bitmap, corners: List<FloatArray>, icon: String, brand: List<String>, rotation: Double) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat()
    bitmap.eraseColor(0)
    fun poly(scale: Float, cx: Float, cy: Float) = Path().apply {
        corners.forEachIndexed { i, k -> val x = cx + (k[0] - cx) * scale; val y = cy + (k[1] - cy) * scale; if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    val cx = corners.map { it[0] }.average().toFloat(); val cy = corners.map { it[1] }.average().toFloat()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = hexColor(brand[0]); c.drawPath(poly(1f, cx, cy), p)
    p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND
    p.color = hexColor(brand[1]); p.strokeWidth = w * 0.022f; c.drawPath(poly(0.86f, cx, cy), p)
    p.color = hexColor(brand[2]); p.strokeWidth = w * 0.014f; c.drawPath(poly(0.78f, cx, cy), p)
    val radius = corners.minOf { hypot((it[0] - cx).toDouble(), (it[1] - cy).toDouble()) }.toFloat()
    val size = radius * 0.62f
    c.save(); c.translate(cx, cy); c.rotate((rotation * 180 / PI).toFloat())
    drawMachiIcon(c, icon, -size / 2, -size / 2, size, 0xFFFFFFFF.toInt(), 2.6f)
    c.restore()
}

/** paintKonbiniAFrame：木框黑板、粉笔字（标题两行、小字四行）、底下一个图标。 */
internal fun paintKonbiniAFrameBitmap(bitmap: Bitmap, heading: String, title: String, note: String, icon: String, chalk: String, accent: String) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(hexColor("#7a5a3a"))
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = hexColor("#1f2a25"); c.drawRect(w * 0.05f, w * 0.05f, w * 0.95f, h - w * 0.05f, p)
    p.textAlign = Paint.Align.CENTER; p.typeface = machiTypeface(700)
    p.color = hexColor(accent); fit(p, heading, w * 0.8f, w * 0.09f)
    c.drawText(heading, w / 2, h * 0.12f + p.mid(), p)
    p.color = hexColor(chalk)
    val titlePx = fit(p, title, w * 0.82f, w * 0.15f, w * 0.08f)
    machiWrapText(p, title, w * 0.82f, 2).forEachIndexed { i, line -> c.drawText(line, w / 2, h * 0.3f + i * titlePx * 1.2f + p.mid(), p) }
    p.alpha = (0.85 * 255).toInt(); p.typeface = machiTypeface(400); p.textSize = w * 0.065f
    machiWrapText(p, note, w * 0.8f, 4).forEachIndexed { i, line -> c.drawText(line, w / 2, h * 0.55f + i * w * 0.085f + p.mid(), p) }
    p.alpha = 255
    drawMachiIcon(c, icon, w * 0.38f, h * 0.8f, w * 0.24f, hexColor(accent), 1.8f)
}

// ---- 渲染 ----

private const val DECAL_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec3 aNor;
uniform mat4 uVP;
out vec2 vUv;
out vec3 vNor;
void main() { vUv = aUv; vNor = aNor; gl_Position = uVP * vec4(aPos, 1.0); }
"""

private const val DECAL_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec3 vNor;
uniform sampler2D uMap;
// 0 MeshBasic（贴图 × level）；1 加色（同上，不写深度）；2 卡通受光 + 自发光 emissive × 贴图。
uniform int uMode;
uniform float uLevel;
uniform float uEmissive;
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
    vec4 t = texture(uMap, vec2(vUv.x, 1.0 - vUv.y));
    // 加色（three：未预乘上传，输出 color × map，AdditiveBlending 再乘 alpha）；Android 位图是预乘的，先还原。
    if (uMode == 1) { fragColor = vec4(toLinear(t.rgb / max(t.a, 1e-4)) * t.a * uLevel, 1.0); return; }
    if (t.a < 0.5) discard;
    vec3 albedo = toLinear(t.rgb / max(t.a, 1e-4));
    if (uMode == 0) { fragColor = vec4(albedo * uLevel, 1.0); return; }
    vec3 n = normalize(vNor);
    vec3 irr = mix(uHemiGround, uHemiSky, 0.5 * n.y + 0.5) + uSun * toonStep(dot(n, uSunDir));
    fragColor = vec4(albedo * irr / 3.14159265 + albedo * uEmissive, 1.0);
}
"""

/** 一块贴图牌面：顶点 pos3 + uv2 + nor3；mode 0 自发光、1 加色、2 受光；画法 painter 在 init 时画一次。 */
internal class MachiDecal(val mesh: FloatArray, val widthPx: Int, val heightPx: Int, val mode: Int, val level: Double, val emissive: Double = 0.0, val painter: (Bitmap) -> Unit)

/** 竖直平面（绕 y 转 rotY 再绕本地 x 转 rx），中心 (x, y, z)。 */
internal fun machiDecalPlane(x: Double, y: Double, z: Double, w: Double, h: Double, rotY: Double, rx: Double = 0.0): FloatArray {
    val r = MachiFrame.mul(MachiFrame.rotY(rotY), MachiFrame.rotX(rx))
    fun d(a: Double, b: Double, c: Double) = doubleArrayOf(r[0] * a + r[1] * b + r[2] * c, r[3] * a + r[4] * b + r[5] * c, r[6] * a + r[7] * b + r[8] * c)
    val n = d(0.0, 0.0, 1.0)
    fun p(sx: Double, sy: Double, u: Float, v: Float): FloatArray { val o = d(sx, sy, 0.0); return floatArrayOf((x + o[0]).toFloat(), (y + o[1]).toFloat(), (z + o[2]).toFloat(), u, v, n[0].toFloat(), n[1].toFloat(), n[2].toFloat()) }
    val a0 = p(-w / 2, -h / 2, 0f, 0f); val a1 = p(w / 2, -h / 2, 1f, 0f); val a2 = p(w / 2, h / 2, 1f, 1f); val a3 = p(-w / 2, h / 2, 0f, 1f)
    return a0 + a1 + a2 + a0 + a2 + a3
}

internal class MachiDecals(private val decals: List<MachiDecal>) {
    private var program = 0
    private var vao = 0
    private val tex = IntArray(decals.size)
    private val first = IntArray(decals.size)
    /** 每块的亮度倍数（霓虹脉动等），渲染器每帧写。 */
    val gain = DoubleArray(decals.size) { 1.0 }

    fun init(link: (String, String) -> Int) {
        program = link(DECAL_VS, DECAL_FS)
        var all = FloatArray(0)
        decals.forEachIndexed { i, d -> first[i] = all.size / 8; all += d.mesh }
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        val fb = ByteBuffer.allocateDirect(all.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(all); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, all.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 32, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 32, 20)
        GLES30.glBindVertexArray(0)
        GLES30.glGenTextures(decals.size, tex, 0)
        decals.forEachIndexed { i, d ->
            val bmp = Bitmap.createBitmap(d.widthPx, d.heightPx, Bitmap.Config.ARGB_8888)
            d.painter(bmp)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            bmp.recycle()
        }
    }

    fun draw(vp: FloatArray, light: MachiNightLight) {
        if (program == 0) return
        GLES30.glUseProgram(program)
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glUniform3fv(u("uSunDir"), 1, light.sunDir, 0)
        GLES30.glUniform3fv(u("uSun"), 1, light.sun, 0)
        GLES30.glUniform3fv(u("uHemiSky"), 1, light.hemiSky, 0)
        GLES30.glUniform3fv(u("uHemiGround"), 1, light.hemiGround, 0)
        GLES30.glUniform1i(u("uMap"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(vao)
        // 不透明的先画，加色的最后画（不写深度）。
        for (pass in 0..1) {
            if (pass == 1) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
                GLES30.glDepthMask(false)
            }
            decals.forEachIndexed { i, d ->
                if ((d.mode == 1) != (pass == 1)) return@forEachIndexed
                GLES30.glUniform1i(u("uMode"), d.mode)
                GLES30.glUniform1f(u("uLevel"), (d.level * gain[i]).toFloat())
                GLES30.glUniform1f(u("uEmissive"), d.emissive.toFloat())
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i])
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, first[i], d.mesh.size / 8)
            }
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }
}

/** 便利店：玻璃海报 ×2（basic 0.95）、窗内霓虹（加色 1.6 × 脉动）、屋顶 logo（受光，自发光 0.32）、A 字立牌（受光）。 */
internal fun machiKonbiniDecals(palette: MachiPalette, title: String, artist: String, aFrame: DoubleArray?): MachiDecals {
    val L = MachiKonbiniLayout
    val name = title.ifEmpty { "KONBINI" }
    fun icon(i: Int) = MACHI_FALLBACK_ICONS[i % MACHI_FALLBACK_ICONS.size]
    val list = ArrayList<MachiDecal>()
    // 玻璃海报：前面左段玻璃后面两张（歌手 / 专辑；专辑未接，按 Folia 无专辑时的兜底）。
    list += MachiDecal(machiDecalPlane(-4.05, 1.62, L.Z1 - 0.075, 0.56, 0.8, 0.0), 256, 364, 0, 0.95) { b ->
        paintKonbiniPosterBitmap(b, artist.ifEmpty { name }, "NOW ON SALE", icon(1), palette.brand[1], palette.brand[0], palette.brand[2], "NEW")
    }
    list += MachiDecal(machiDecalPlane(-4.05 + 0.72, 1.62, L.Z1 - 0.075, 0.56, 0.8, 0.0), 256, 364, 0, 0.95) { b ->
        paintKonbiniPosterBitmap(b, name, "LIMITED", icon(2), palette.brand[2], "#ffffff", palette.brand[0], "限定")
    }
    // 窗内霓虹：右面玻璃后面，朝外（+x）。
    list += MachiDecal(machiDecalPlane(L.X1 - 0.1, 1.55, -1.95, 0.86, 0.86, PI / 2), 256, 256, 1, 1.6) { b -> paintKonbiniNeonBitmap(b, icon(0), palette.neon) }
    // 屋顶 logo：顶盖三角（顶点外扩 1.01）贴在 lidTop + 0.004；贴图按外接正方形（reach = 最远顶点 × 1.02）映射。
    run {
        val W = L.BOARD_W; val cir = W / sqrt(3.0); val cx = L.BOARD_CX; val cz = L.BOARD_CZ
        val local = (0 until 3).map { k -> val va = L.BOARD_AZIMUTH + k * 2 * PI / 3 + PI / 3; doubleArrayOf(sin(va) * cir * 1.01, cos(va) * cir * 1.01) }
        val reach = local.maxOf { hypot(it[0], it[1]) } * 1.02
        val y = L.BOARD_Y0 + L.BOARD_H + 0.08 + 0.06 + 0.015 + 0.004
        val mesh = ArrayList<Float>()
        for (pt in local) {
            mesh += listOf((cx + pt[0]).toFloat(), y.toFloat(), (cz + pt[1]).toFloat(), (pt[0] / (2 * reach) + 0.5).toFloat(), (-pt[1] / (2 * reach) + 0.5).toFloat(), 0f, 1f, 0f)
        }
        val corners = local.map { floatArrayOf(((it[0] / (2 * reach) + 0.5) * 512).toFloat(), ((0.5 + it[1] / (2 * reach)) * 512).toFloat()) }
        list += MachiDecal(mesh.toFloatArray(), 512, 512, 2, 1.0, 0.32) { b -> paintKonbiniRoofLogoBitmap(b, corners, icon(0), palette.brand, -PI / 4) }
    }
    // A 字立牌（props aFrameFaces：x, y, z, rotY, w, h, lean）。
    if (aFrame != null) {
        val h = aFrame[5]; val w = aFrame[4]
        list += MachiDecal(machiDecalPlane(aFrame[0], aFrame[1], aFrame[2], w, h, aFrame[3], -aFrame[6]), 256, (256 * h / w).toInt(), 2, 1.0) { b ->
            paintKonbiniAFrameBitmap(b, "本日のテーマ", name, artist, icon(1), "#eef1ea", palette.brand[0])
        }
    }
    return MachiDecals(list)
}
