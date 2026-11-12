// 町景的歌词大屏、LED 跑马灯、门头霓虹店招：逐函数对译 Folia v0.7.25
//   machi/engine/lyricScreen.ts（MachiLyricScreen）、engine/ledStrip.ts（MachiLedStrip）、engine/signRaster.ts
//   （paintMachiBoard / paintMachiLedStrip / strokeNeonText / fitFont）、scenes/kowloon/kowloonSigns.ts
//   （resolveKowloonTickerText、曲名卡两行）、kowloonSignPaint.ts（paintKowloonBarSign）。
// 牌面先画进 android.graphics.Bitmap（上半张未唱、下半张唱到），GL 线程上 texImage2D；着色器照抄
// （three 的 sRGB 贴图 → 线性、NeutralToneMapping、再回 sRGB；Bitmap 是预乘 alpha，取样后先除回）。
// 和 Folia 的差别：贴图槽只留「当前 + 换行中」两张（Folia 池子 3 张，第三张是预热下一行，这里换行时现画）；
// 跑马灯字条 2048×48（Folia 4096×96，滚动速度按像素减半，看上去一样快）；大屏闪拍（智能动感）未接，uSwell 恒 0。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val MACHI_REFERENCE_PX = 100.0
private const val SCREEN_TEXTURE_WIDTH = 1280
private const val SWITCH_SECONDS = 0.3
private const val CELLS_X = 150
private const val PEAK_DARK = 0.95f
private const val PEAK_LIGHT = 0.8f
private const val LED_W = 2048
private const val LED_H = 48

/** 屏幕下沿的跑马灯：这一句的译文；没有译文时滚曲名与歌手（kowloonSigns.ts resolveKowloonTickerText）。 */
internal fun resolveKowloonTickerText(target: MachiBoardTarget, lines: List<Line>, title: String, artist: String): String {
    val current = if (target.isCard) null else lines.getOrNull(target.index)
    val tr = current?.translation?.trim().orEmpty()
    if (tr.isNotEmpty()) return "◆ $tr   "
    return "♪ ${title.ifEmpty { "KOWLOON" }}${if (artist.isNotEmpty()) " — $artist" else ""}   ◆   九龍城寨   ◆   OPEN ALL NIGHT   ◆   "
}

internal fun machiTypeface(weight: Int): Typeface =
    if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
    else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

/** createMachiMeasure：参考字号 100 px 量字宽，带缓存。 */
internal class MachiTextMeasure(typeface: Typeface) : (String) -> Double {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = MACHI_REFERENCE_PX.toFloat() }
    private val cache = HashMap<String, Double>()
    override fun invoke(text: String): Double {
        if (text.isEmpty()) return 0.0
        return cache.getOrPut(text) { if (cache.size > 4000) cache.clear(); paint.measureText(text).toDouble() }
    }
}

private fun hexInt(hex: String): Int = machiRgb(hex).let { c ->
    (0xFF shl 24) or ((c[0] * 255).roundToInt() shl 16) or ((c[1] * 255).roundToInt() shl 8) or (c[2] * 255).roundToInt()
}

/** paintMachiBoard：上半张未唱、下半张唱到（曲名卡第一行 lit、第二行 unlit，上下一样）。 */
internal fun paintMachiBoard(bitmap: Bitmap, layout: MachiBoardLayout, typeface: Typeface, unlit: String, lit: String, card: Boolean) {
    bitmap.eraseColor(0)
    val canvas = Canvas(bitmap)
    val half = bitmap.height / 2f
    val scaleY = half / layout.height
    val scaleX = bitmap.width / layout.width
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.LEFT }
    layout.rows.forEachIndexed { rowIndex, row ->
        paint.textSize = (row.fontPx * scaleX).toFloat()
        val fm = paint.fontMetrics
        val mid = -(fm.ascent + fm.descent) / 2 // textBaseline = 'middle'
        for (g in row.glyphs) {
            val x = (g.x0 * scaleX).toFloat()
            val y = (row.centerY * scaleY).toFloat() + mid
            paint.color = hexInt(if (card && rowIndex == 0) lit else unlit)
            canvas.drawText(g.text, x, y, paint)
            paint.color = hexInt(if (card) (if (rowIndex == 0) lit else unlit) else lit)
            canvas.drawText(g.text, x, y + half, paint)
        }
    }
}

/** fitFont：把字缩到 maxWidth 以内。 */
private fun fitFont(paint: Paint, text: String, maxWidth: Float, maxPx: Float, minPx: Float = 8f): Float {
    paint.textSize = maxPx
    val w = paint.measureText(text)
    val px = if (w > maxWidth) max(minPx, maxPx * maxWidth / w) else maxPx
    paint.textSize = px
    return px
}

/** paintMachiLedStrip：黑底白字一长条，返回字条实际宽度。 */
internal fun paintMachiLedStrip(bitmap: Bitmap, typeface: Typeface, text: String): Float {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; color = android.graphics.Color.WHITE }
    fitFont(paint, text, w * 0.94f, h * 0.78f, h * 0.4f)
    val used = min(w, paint.measureText(text) + h * 3)
    val fm = paint.fontMetrics
    canvas.drawText(text, h * 1.5f, h * 0.54f - (fm.ascent + fm.descent) / 2, paint)
    return used
}

/** strokeNeonText：外圈同色光晕描边 + 近白芯（Canvas 2D shadowBlur ≈ setShadowLayer 半径 blur/2）。 */
private fun strokeNeonText(canvas: Canvas, paint: Paint, text: String, x: Float, y: Float, color: Int, glow: Float) {
    paint.style = Paint.Style.STROKE
    paint.strokeJoin = Paint.Join.ROUND
    paint.setShadowLayer(glow / 2, 0f, 0f, color)
    paint.color = color
    paint.strokeWidth = glow * 0.45f
    canvas.drawText(text, x, y, paint)
    paint.setShadowLayer(glow * 0.2f, 0f, 0f, color)
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = glow * 0.14f
    canvas.drawText(text, x, y, paint)
    paint.clearShadowLayer()
    paint.style = Paint.Style.FILL
}

/** paintKowloonBarSign 的近似：深底、左边鸡尾酒杯线稿、中间曲名霓虹、右边竖排「酒吧」。 */
internal fun paintKowloonBarSign(bitmap: Bitmap, typeface: Typeface, title: String, neon: String) {
    val canvas = Canvas(bitmap)
    val width = bitmap.width.toFloat(); val height = bitmap.height.toFloat()
    canvas.drawColor(0xFF120a14.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER }
    // 鸡尾酒杯（drawCocktail）：倒三角杯身 + 杯脚 + 底座，青色霓虹线。
    val glass = 0xFF3fe8ff.toInt()
    val cx = height * 0.6f; val cy = height * 0.5f; val s = height * 0.8f
    val path = android.graphics.Path().apply {
        moveTo(cx - s * 0.36f, cy - s * 0.32f); lineTo(cx + s * 0.36f, cy - s * 0.32f); lineTo(cx, cy + s * 0.08f); close()
        moveTo(cx, cy + s * 0.08f); lineTo(cx, cy + s * 0.36f)
        moveTo(cx - s * 0.2f, cy + s * 0.38f); lineTo(cx + s * 0.2f, cy + s * 0.38f)
    }
    paint.style = Paint.Style.STROKE; paint.strokeWidth = height * 0.05f; paint.color = glass
    paint.setShadowLayer(height * 0.08f, 0f, 0f, glass)
    canvas.drawPath(path, paint)
    paint.clearShadowLayer(); paint.style = Paint.Style.FILL
    val markWidth = height * 0.7f
    val markX = width - markWidth * 0.75f
    val mark = 0xFFff4fb0.toInt()
    val markPx = height * 0.36f
    paint.textSize = markPx
    fun mid() = paint.fontMetrics.let { -(it.ascent + it.descent) / 2 }
    strokeNeonText(canvas, paint, "酒", markX, height * 0.29f + mid(), mark, markPx * 0.18f)
    strokeNeonText(canvas, paint, "吧", markX, height * 0.71f + mid(), mark, markPx * 0.18f)
    val left = height * 1.15f
    val right = markX - markWidth * 0.55f
    val px = fitFont(paint, title, right - left, height * 0.6f)
    strokeNeonText(canvas, paint, title, (left + right) / 2, height * 0.54f + mid(), hexInt(neon), px * 0.18f)
}

private const val SIGN_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uVP;
out vec2 vUv;
void main() { vUv = aUv; gl_Position = uVP * vec4(aPos, 1.0); }
"""

private const val COMMON = """
vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }
vec3 neutral(vec3 color) {
    const float startCompression = 0.8 - 0.04;
    const float desaturation = 0.15;
    float x = min(color.r, min(color.g, color.b));
    float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
    color -= offset;
    float peak = max(color.r, max(color.g, color.b));
    if (peak < startCompression) return color;
    float d = 1.0 - startCompression;
    float newPeak = 1.0 - d * d / (peak + d - startCompression);
    color *= newPeak / peak;
    float g = 1.0 - 1.0 / (desaturation * (peak - newPeak) + 1.0);
    return mix(color, vec3(newPeak), g);
}
"""

// lyricScreen.ts FRAGMENT；tex() 把 three flipY 的 v 换成 Bitmap 自上而下的 v，并去预乘、sRGB → 线性。
private const val SCREEN_FS = """#version 300 es
precision highp float;
uniform sampler2D uMap;
uniform vec3 uBase;
uniform float uBright;
uniform float uLitGain;
uniform float uUnlitGain;
uniform vec3 uRowBottom;
uniform vec3 uReveal;
uniform float uSoft;
uniform vec2 uCells;
uniform float uTime;
uniform float uGlitch;
uniform float uPeak;
uniform vec3 uSheen;
uniform float uViewZ;
in vec2 vUv;
out vec4 fragColor;
""" + COMMON + """
float bHash(float n) { return fract(sin(n * 127.1) * 43758.5453); }
vec4 tex(vec2 uv) {
    vec4 c = texture(uMap, vec2(uv.x, 1.0 - uv.y));
    return vec4(toLinear(c.rgb / max(c.a, 1e-4)), c.a);
}
void main() {
    vec2 uv = vUv;
    float tear = bHash(floor(vUv.y * 22.0) + floor(uTime * 24.0) * 7.0);
    uv.x += (tear - 0.5) * 0.08 * uGlitch * step(0.62, tear);
    vec2 cellUv = vUv * uCells;
    vec2 perPixel = fwidth(cellUv);
    float grid = 1.0 - smoothstep(0.3, 0.55, max(perPixel.x, perPixel.y));
    float y = 1.0 - vUv.y;
    float split = 0.0008 + 0.006 * uGlitch;
    vec4 unlit = tex(vec2(uv.x, 0.5 + uv.y * 0.5));
    vec4 litR = tex(vec2(uv.x + split, uv.y * 0.5));
    vec4 litG = tex(vec2(uv.x, uv.y * 0.5));
    vec4 litB = tex(vec2(uv.x - split, uv.y * 0.5));
    float reveal = y < uRowBottom.x ? uReveal.x : (y < uRowBottom.y ? uReveal.y : uReveal.z);
    float snapped = (floor(vUv.x * uCells.x) + 0.5) / uCells.x;
    float on = 1.0 - smoothstep(reveal - uSoft, reveal + uSoft, mix(vUv.x, snapped, grid));
    float edge = smoothstep(0.0, 0.03, vUv.x) * smoothstep(1.0, 0.97, vUv.x) * smoothstep(0.0, 0.06, vUv.y) * smoothstep(1.0, 0.94, vUv.y);
    vec3 base = uBase * (0.92 + 0.08 * smoothstep(1.0, 0.0, y)) * (0.8 + 0.2 * edge);
    vec3 color = mix(base, unlit.rgb * uUnlitGain, unlit.a);
    color = mix(color, vec3(litR.r, litG.g, litB.b) * uLitGain, vec3(litR.a, litG.a, litB.a) * on);
    vec2 f = fract(cellUv) - 0.5;
    float gap = smoothstep(0.5, 0.38, abs(f.x)) * smoothstep(0.5, 0.36, abs(f.y));
    vec3 stripe = clamp(1.6 - abs(vec3(fract(cellUv.x) * 3.0) - vec3(0.5, 1.5, 2.5)) * 1.1, 0.0, 1.0) * 2.0;
    vec3 mask = gap * mix(vec3(1.0), stripe, 0.35) / 0.78;
    color *= mix(vec3(1.0), mask, grid * 0.8);
    float roll = fract(vUv.y + uTime * 0.07);
    color *= (1.0 + 0.045 * exp(-pow((roll - 0.5) * 9.0, 2.0))) * (1.0 - 0.3 * uGlitch);
    color += (bHash(dot(floor(cellUv), vec2(1.0, 157.0)) + floor(uTime * 30.0)) - 0.5) * 0.12 * uGlitch;
    color = max(color * uBright, 0.0);
    float peak = max(max(color.r, color.g), color.b);
    color *= min(1.0, uPeak / max(peak, 1e-4));
    float fresnel = pow(1.0 - clamp(uViewZ, 0.0, 1.0), 3.0);
    float glare = smoothstep(0.22, 0.0, abs(vUv.x * 0.7 + vUv.y * 0.9 - 1.05));
    color += uSheen * (0.035 + 0.22 * fresnel + 0.05 * glare);
    fragColor = vec4(toSrgb(neutral(color)), 1.0);
}
"""

// ledStrip.ts FRAGMENT（贴图 NoColorSpace：只读 r 当亮度）。
private const val LED_FS = """#version 300 es
precision highp float;
uniform sampler2D uMap;
uniform vec3 uLed;
uniform float uScroll;
uniform float uUsed;
uniform float uWindow;
uniform float uDots;
uniform float uAspect;
uniform float uBright;
in vec2 vUv;
out vec4 fragColor;
""" + COMMON + """
void main() {
    vec2 grid = vec2(floor(uDots * uAspect), uDots);
    vec2 cell = floor(vUv * grid);
    vec2 f = fract(vUv * grid) - 0.5;
    vec2 sampleUv = (cell + 0.5) / grid;
    float u = fract(uScroll + sampleUv.x * uWindow) * uUsed;
    float a = texture(uMap, vec2(u, 1.0 - sampleUv.y)).r;
    float dotMask = 1.0 - smoothstep(0.28, 0.42, length(f));
    vec3 off = vec3(0.025, 0.022, 0.03);
    vec3 color = mix(off, mix(uLed * 0.08, uLed * 2.2, a) * uBright, dotMask);
    fragColor = vec4(toSrgb(neutral(color)), 1.0);
}
"""

// 店招图集：MeshBasicMaterial（map × color 标量），toneMapped。
private const val NEON_FS = """#version 300 es
precision highp float;
uniform sampler2D uMap;
uniform float uLevel;
in vec2 vUv;
out vec4 fragColor;
""" + COMMON + """
void main() {
    vec4 c = texture(uMap, vec2(vUv.x, 1.0 - vUv.y));
    fragColor = vec4(toSrgb(neutral(toLinear(c.rgb) * uLevel)), 1.0);
}
"""

/** 一块竖直平面（朝 +z），中心 (x, y, z)，宽 w 高 h：两个三角形，pos3 + uv2。 */
private fun planeZ(x: Double, y: Double, z: Double, w: Double, h: Double): FloatArray {
    val x0 = (x - w / 2).toFloat(); val x1 = (x + w / 2).toFloat()
    val y0 = (y - h / 2).toFloat(); val y1 = (y + h / 2).toFloat(); val zz = z.toFloat()
    return floatArrayOf(
        x0, y0, zz, 0f, 0f, x1, y0, zz, 1f, 0f, x1, y1, zz, 1f, 1f,
        x0, y0, zz, 0f, 0f, x1, y1, zz, 1f, 1f, x0, y1, zz, 0f, 1f,
    )
}

/** 帧输入：歌曲时间、氛围时间、副歌权重目标、音频。 */
internal class MachiSignFrame(val songTime: Double, val ambientTime: Double, val delta: Double, val chorus: Double, val audio: Double, val artificial: Double, val flicker: Double)

/**
 * 城寨的三块会发光的面：歌词大屏（逐字点亮 + 换行信号切换）、屏下 LED 跑马灯、门头霓虹店招。只在 GL 线程上用。
 */
internal class MachiKowloonSigns(
    private val palette: MachiPalette,
    private val title: String,
    private val artist: String,
    private val seed: String,
) {
    private val typeface = machiTypeface(700)
    private val measure = MachiTextMeasure(typeface)
    private val half = (SCREEN_TEXTURE_WIDTH * (MachiKowloonLayout.SCREEN_H / MachiKowloonLayout.SCREEN_W)).roundToInt()
    private val box = MachiBoardBox(SCREEN_TEXTURE_WIDTH.toDouble(), half.toDouble(), 0.05, MACHI_REFERENCE_PX)
    private val cells = floatArrayOf(CELLS_X.toFloat(), (CELLS_X * MachiKowloonLayout.SCREEN_H / MachiKowloonLayout.SCREEN_W).roundToInt().toFloat())

    private class Slot(val tex: Int, val bitmap: Bitmap) { var key = ""; var layout: MachiBoardLayout? = null }
    private lateinit var slots: Array<Slot>
    private var current: Slot? = null
    private var pending: Slot? = null
    private var switchElapsed = SWITCH_SECONDS
    private var targetKey = ""
    private var chorusGlow = 0.0

    private var screenProgram = 0
    private var ledProgram = 0
    private var neonProgram = 0
    private var vao = 0
    private var ledTex = 0
    private var neonTex = 0
    private val ledBitmap = Bitmap.createBitmap(LED_W, LED_H, Bitmap.Config.ARGB_8888)
    private var ledText = ""
    private var ledUsed = 1f
    private var ledWindow = 0.3f
    private var ledScroll = 0.0
    private val tickerDots = 9f
    private val tickerAspect = (TICKER_W / TICKER_H).toFloat()

    fun init(link: (String, String) -> Int) {
        screenProgram = link(SIGN_VS, SCREEN_FS)
        ledProgram = link(SIGN_VS, LED_FS)
        neonProgram = link(SIGN_VS, NEON_FS)
        val texIds = IntArray(4)
        GLES30.glGenTextures(4, texIds, 0)
        slots = Array(2) { Slot(texIds[it], Bitmap.createBitmap(SCREEN_TEXTURE_WIDTH, half * 2, Bitmap.Config.ARGB_8888)) }
        ledTex = texIds[2]; neonTex = texIds[3]
        // 门头店招：图集里 fine 密度 300 px/m。
        val signBmp = Bitmap.createBitmap((MachiKowloonLayout.SIGN_W * 300).roundToInt(), (MachiKowloonLayout.SIGN_H * 300).roundToInt(), Bitmap.Config.ARGB_8888)
        paintKowloonBarSign(signBmp, machiTypeface(800), title.ifEmpty { "KOWLOON" }, palette.neon)
        upload(neonTex, signBmp, mip = true)
        signBmp.recycle()
        val mesh = planeZ(MachiKowloonLayout.SCREEN_X, MachiKowloonLayout.SCREEN_Y, MachiKowloonLayout.SCREEN_Z + 0.006, MachiKowloonLayout.SCREEN_W, MachiKowloonLayout.SCREEN_H) +
            planeZ(TICKER_X, TICKER_Y, TICKER_Z, TICKER_W, TICKER_H) +
            planeZ(MachiKowloonLayout.SIGN_X, MachiKowloonLayout.SIGN_Y, MachiKowloonLayout.SIGN_Z + 0.006, MachiKowloonLayout.SIGN_W, MachiKowloonLayout.SIGN_H)
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        val bufs = IntArray(1); GLES30.glGenBuffers(1, bufs, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        val fb = java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
        GLES30.glBindVertexArray(0)
    }

    private fun upload(tex: Int, bmp: Bitmap, mip: Boolean = false) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, if (mip) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        if (mip) GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    private fun keyOf(target: MachiBoardTarget) = if (target.isCard) "card" else "line:${target.index}"

    private fun prepare(target: MachiBoardTarget, lines: List<Line>): Slot {
        val key = keyOf(target)
        slots.firstOrNull { it.key == key }?.let { return it }
        val slot = slots.first { it !== current }
        val line = if (target.isCard) null else lines.getOrNull(target.index)
        val card = line == null
        val layout = if (card) layoutMachiBoardCard("♪ ${title.ifEmpty { "KOWLOON" }}", artist, box, measure) else layoutMachiBoardLine(line!!, box, measure)
        paintMachiBoard(slot.bitmap, layout, typeface, palette.boardUnlit, palette.boardLit, card)
        upload(slot.tex, slot.bitmap)
        slot.key = key; slot.layout = layout
        return slot
    }

    /** showBoard：牌面换了就画新的，信号切换过去（第一次直接上）。 */
    fun show(target: MachiBoardTarget, lines: List<Line>) {
        val key = keyOf(target)
        if (key == targetKey) return
        targetKey = key
        val slot = prepare(target, lines)
        if (current == null) { current = slot; switchElapsed = SWITCH_SECONDS; pending = null }
        else if (slot !== current) { pending = slot; switchElapsed = 0.0 }
        val text = resolveKowloonTickerText(target, lines, title, artist)
        if (text != ledText) {
            ledText = text
            ledUsed = paintMachiLedStrip(ledBitmap, typeface, text)
            upload(ledTex, ledBitmap)
            val u = ledUsed
            ledUsed = u / LED_W
            ledWindow = min(4f, (floor(tickerDots * tickerAspect) * (LED_H / tickerDots)) / max(1f, u))
        }
    }

    fun draw(vp: FloatArray, viewZ: Float, frame: MachiSignFrame) {
        val cur = current ?: return
        var glitch = 0.0
        if (switchElapsed < SWITCH_SECONDS) {
            switchElapsed += max(frame.delta, 1.0 / 120)
            val t = min(1.0, switchElapsed / SWITCH_SECONDS)
            if (t >= 0.45) pending?.let { current = it; pending = null }
            glitch = if (t >= 1) 0.0 else sin(PI * t).pow(0.7)
        }
        chorusGlow += (frame.chorus - chorusGlow) * (1 - exp(-frame.delta * 3))
        val slot = current ?: cur
        val layout = slot.layout
        GLES30.glBindVertexArray(vao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        // 大屏
        GLES30.glUseProgram(screenProgram)
        fun u(n: String) = GLES30.glGetUniformLocation(screenProgram, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, slot.tex)
        GLES30.glUniform1i(u("uMap"), 0)
        val bottoms = floatArrayOf(1f, 1f, 1f)
        val reveal = floatArrayOf(0f, 0f, 0f)
        if (layout != null) {
            layout.rows.forEachIndexed { i, row -> if (i < 3) bottoms[i] = if (i == layout.rows.size - 1) 1f else (row.bottom / layout.height).toFloat() }
            resolveMachiBoardReveal(layout, frame.songTime).forEachIndexed { i, r -> if (i < 3) reveal[i] = (r / box.width).toFloat() }
        }
        GLES30.glUniform3fv(u("uRowBottom"), 1, bottoms, 0)
        GLES30.glUniform3fv(u("uReveal"), 1, reveal, 0)
        val baseK = if (palette.boardIsLight) 0.98 else 1.0
        GLES30.glUniform3fv(u("uBase"), 1, linear(palette.boardBase, baseK), 0)
        GLES30.glUniform1f(u("uLitGain"), (if (palette.boardIsLight) 1.0 else 1.25 + 0.2 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uUnlitGain"), if (palette.boardIsLight) 1f else 0.9f)
        GLES30.glUniform1f(u("uBright"), (1 + 0.05 * frame.audio + 0.06 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uPeak"), if (palette.boardIsLight) PEAK_LIGHT else PEAK_DARK)
        GLES30.glUniform1f(u("uGlitch"), glitch.toFloat())
        GLES30.glUniform1f(u("uTime"), frame.ambientTime.toFloat())
        GLES30.glUniform1f(u("uSoft"), 0.004f)
        GLES30.glUniform2fv(u("uCells"), 1, cells, 0)
        GLES30.glUniform3fv(u("uSheen"), 1, linear(palette.moon, 0.35), 0)
        GLES30.glUniform1f(u("uViewZ"), viewZ)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        // 跑马灯
        ledScroll = (ledScroll + frame.delta * 35.0 * (0.8 + 0.4 * chorusGlow) / max(1.0, ledUsed * LED_W.toDouble())) % 1.0
        GLES30.glUseProgram(ledProgram)
        fun l(n: String) = GLES30.glGetUniformLocation(ledProgram, n)
        GLES30.glUniformMatrix4fv(l("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ledTex)
        GLES30.glUniform1i(l("uMap"), 0)
        GLES30.glUniform3fv(l("uLed"), 1, linear(palette.led, 1.0), 0)
        GLES30.glUniform1f(l("uScroll"), ledScroll.toFloat())
        GLES30.glUniform1f(l("uUsed"), ledUsed)
        GLES30.glUniform1f(l("uWindow"), ledWindow)
        GLES30.glUniform1f(l("uDots"), tickerDots)
        GLES30.glUniform1f(l("uAspect"), tickerAspect)
        GLES30.glUniform1f(l("uBright"), (0.9 + 0.2 * frame.audio).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 6, 6)
        // 门头霓虹（稳定组：neon = artificial^1.4 × (1 + 0.1 chorus)，× (0.93 + 0.07 flicker)）
        GLES30.glUseProgram(neonProgram)
        fun n(s: String) = GLES30.glGetUniformLocation(neonProgram, s)
        GLES30.glUniformMatrix4fv(n("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, neonTex)
        GLES30.glUniform1i(n("uMap"), 0)
        val neon = frame.artificial.pow(1.4) * (1 + 0.1 * chorusGlow)
        GLES30.glUniform1f(n("uLevel"), (neon * (0.93 + 0.07 * frame.flicker)).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 12, 6)
        GLES30.glBindVertexArray(0)
    }

    private fun linear(hex: String, k: Double) = machiRgb(hex).let { c -> FloatArray(3) { i -> (c[i].toDouble().pow(2.2) * k).toFloat() } }

    companion object {
        // layout.ts TICKER
        const val TICKER_X = 2.1; const val TICKER_Y = 3.09; const val TICKER_Z = -1.41; const val TICKER_W = 4.2; const val TICKER_H = 0.17
    }
}
