// 町景的歌词大屏、LED 跑马灯、门头霓虹店招：逐函数对译 Folia v0.7.25
//   machi/engine/lyricScreen.ts（MachiLyricScreen）、engine/ledStrip.ts（MachiLedStrip）、engine/signRaster.ts
//   （paintMachiBoard / paintMachiLedStrip / strokeNeonText / fitFont）、scenes/kowloon/kowloonSigns.ts
//   （resolveKowloonTickerText、曲名卡两行、店招三组灯管亮度）；店招图集在 MachiKowloonStreet.kt。
// 牌面先画进 android.graphics.Bitmap（上半张未唱、下半张唱到），GL 线程上 texImage2D；着色器照抄
// （three 的 sRGB 贴图 → 线性；画进场景目标时输出线性，色调映射在后处理最后一道；Bitmap 是预乘 alpha，取样后先除回）。
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


/** 全息面板写什么（hologram.ts resolveKowloonHologramText）。 */
internal fun resolveKowloonHologramText(target: MachiBoardTarget, lines: List<Line>, title: String, artist: String): Pair<String, String> {
    val t = title.ifEmpty { "KOWLOON" }
    if (!target.isCard) {
        val next = lines.drop(target.index + 1).firstOrNull { it.fullText.trim().isNotEmpty() }
        if (next != null) return "NEXT" to next.fullText.trim()
        val tr = lines.getOrNull(target.index)?.translation?.trim().orEmpty()
        if (tr.isNotEmpty()) return "TRANSLATION" to tr
    }
    return "NOW PLAYING" to (if (artist.isNotEmpty()) "$t — $artist" else t)
}

/** signRaster.ts wrapText：中日韩逐字、拉丁按词，最多 maxLines 行，超出末行加省略号。 */
internal fun machiWrapText(paint: Paint, text: String, maxWidth: Float, maxLines: Int): List<String> {
    val units = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]|[^\\s\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]+\\s*|\\s+").findAll(text).map { it.value }.toList()
    val lines = ArrayList<String>()
    var current = ""
    for (unit in units) {
        val next = current + unit
        if (current.isNotEmpty() && paint.measureText(next.trimEnd()) > maxWidth) { lines += current.trimEnd(); current = unit.trimStart() } else current = next
    }
    if (current.isNotBlank()) lines += current.trimEnd()
    if (lines.size > maxLines) {
        val kept = lines.take(maxLines).toMutableList()
        var last = kept[maxLines - 1]
        while (last.length > 1 && paint.measureText("$last…") > maxWidth) last = last.dropLast(1)
        kept[maxLines - 1] = "$last…"
        return kept
    }
    return lines
}

/** hologram.ts paint：黑底白字，左上小标签，下面一到两行。 */
internal fun paintKowloonHologram(bitmap: Bitmap, typeface: Typeface, label: String, text: String) {
    val cv = Canvas(bitmap)
    val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    cv.drawColor(android.graphics.Color.BLACK)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); p.textSize = h * 0.1f; p.alpha = (0.75 * 255).toInt()
    p.textAlign = Paint.Align.LEFT
    fun mid() = p.fontMetrics.let { -(it.ascent + it.descent) / 2 }
    cv.drawText("▸ $label", w * 0.05f, h * 0.14f + mid(), p)
    p.alpha = 255; p.typeface = typeface; p.textAlign = Paint.Align.CENTER
    val maxWidth = w * 0.88f
    var px = h * 0.3f
    p.textSize = px
    var rows = machiWrapText(p, text, maxWidth, 2)
    if (rows.size > 1) { px = h * 0.28f; p.textSize = px; rows = machiWrapText(p, text, maxWidth, 2) }
    if (rows.size == 1) px = fitFont(p, rows[0], maxWidth, px)
    val center = h * 0.58f
    rows.forEachIndexed { i, row -> cv.drawText(row, w / 2, center + (i - (rows.size - 1) / 2f) * px * 1.18f + mid(), p) }
}

private const val HOLO_FS = """#version 300 es
precision highp float;
uniform sampler2D uMap;
uniform vec3 uColor;
uniform float uTime;
uniform float uLevel;
uniform float uGlitch;
uniform float uAspect;
in vec2 vUv;
out vec4 fragColor;
float hHash(float n) { return fract(sin(n * 91.7) * 43758.5453); }
void main() {
    vec2 uv = vUv;
    float band = floor(uv.y * 26.0);
    float tear = hHash(band + floor(uTime * 24.0) * 3.1);
    uv.x += (tear - 0.5) * 0.06 * uGlitch * step(0.55, tear);
    float text = texture(uMap, vec2(uv.x, 1.0 - uv.y)).r;
    float scan = 0.82 + 0.18 * sin(vUv.y * 190.0 - uTime * 8.0);
    float sweepAt = (fract(vUv.y - uTime * 0.22) - 0.5) * 7.0;
    float sweep = exp(-sweepAt * sweepAt) * 0.35;
    vec2 edge = min(vUv, 1.0 - vUv) * vec2(uAspect, 1.0);
    float frame = 1.0 - smoothstep(0.006, 0.016, min(edge.x, edge.y));
    float corner = step(min(edge.x, edge.y), 0.035) * step(max(edge.x, edge.y), 0.12);
    float fill = 0.07 + 0.06 * (1.0 - vUv.y);
    float flicker = 0.9 + 0.1 * hHash(floor(uTime * 14.0));
    float intensity = (text * 1.35 + frame * 0.5 + corner * 0.6 + fill) * scan * flicker + sweep * (0.15 + text * 0.4);
    fragColor = vec4(uColor * intensity * uLevel * (1.0 - 0.4 * uGlitch), 1.0);
}
"""

private const val BEAM_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
uniform float uLevel;
uniform float uTime;
in vec2 vUv;
out vec4 fragColor;
void main() {
    float across = max(0.0, 1.0 - abs(vUv.x - 0.5) * 2.0);
    float streak = 0.75 + 0.25 * sin(vUv.x * 40.0 + uTime * 1.3) * sin(vUv.y * 9.0 - uTime * 3.0);
    float alpha = pow(across, 1.5) * (0.35 + 0.65 * (1.0 - vUv.y)) * streak;
    fragColor = vec4(uColor * alpha * 0.3 * uLevel, 1.0);
}
"""

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
    fragColor = vec4(color, 1.0);
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
    fragColor = vec4(color, 1.0);
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
    fragColor = vec4(toLinear(c.rgb / max(c.a, 1e-4)) * uLevel, 1.0);
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

/** 场景的招牌（城寨 / 便利店）：GL 线程上建、换牌面、每帧画。[view] 是镜头方向（从目标指向镜头，单位向量）。 */
internal interface MachiSigns {
    fun init(link: (String, String) -> Int)
    fun show(target: MachiBoardTarget, lines: List<Line>)
    /** [advance] = false：只画不推进状态（倒影 pass 再画一遍用）。 */
    fun draw(vp: FloatArray, view: FloatArray, frame: MachiSignFrame, advance: Boolean = true)
}

/** 帧输入：歌曲时间、氛围时间、副歌权重目标、音频。 */
internal class MachiSignFrame(val songTime: Double, val ambientTime: Double, val delta: Double, val chorus: Double, val audio: Double, val artificial: Double, val flicker: Double, val daylight: Double = 0.0)

/**
 * 城寨的三块会发光的面：歌词大屏（逐字点亮 + 换行信号切换）、屏下 LED 跑马灯、门头霓虹店招。只在 GL 线程上用。
 */
internal class MachiKowloonSigns(
    private val palette: MachiPalette,
    private val title: String,
    private val artist: String,
    private val seed: String,
    private val placements: List<MachiSignPlacement> = emptyList(),
) : MachiSigns {
    private var holoProgram = 0
    private var beamProgram = 0
    private var holoTex = 0
    private val holoBitmap = Bitmap.createBitmap(1024, 376, Bitmap.Config.ARGB_8888)
    private var holoKey = ""
    private var holoPending: Pair<String, String>? = null
    private var holoSwitch = HOLO_SWITCH
    private var holoStart = 0
    private val holoColor = machiLinear(mcMixHex("#62f2ff", palette.led, 0.3))
    private var atlasCounts = IntArray(3)
    private var atlasStarts = IntArray(3)
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

    override fun init(link: (String, String) -> Int) {
        screenProgram = link(SIGN_VS, SCREEN_FS)
        ledProgram = link(SIGN_VS, LED_FS)
        neonProgram = link(SIGN_VS, NEON_FS)
        holoProgram = link(SIGN_VS, HOLO_FS)
        beamProgram = link(SIGN_VS, BEAM_FS)
        val texIds = IntArray(5)
        GLES30.glGenTextures(5, texIds, 0)
        holoTex = texIds[4]
        holoBitmap.eraseColor(android.graphics.Color.BLACK)
        upload(holoTex, holoBitmap)
        slots = Array(2) { Slot(texIds[it], Bitmap.createBitmap(SCREEN_TEXTURE_WIDTH, half * 2, Bitmap.Config.ARGB_8888)) }
        ledTex = texIds[2]; neonTex = texIds[3]
        // 店招图集（门头 + 伸出楼面的 8 块 + 楼顶「九龍城寨」+ 大排档），按灯管组排好。
        val bar = MachiSignPlacement(MachiKowloonLayout.SIGN_X, MachiKowloonLayout.SIGN_Y, MachiKowloonLayout.SIGN_Z + 0.006, 0.0, MachiKowloonLayout.SIGN_W, MachiKowloonLayout.SIGN_H, false, "bar")
        val atlas = buildMachiSignAtlas(placements, bar, seed, title, artist, palette.neon, palette.brand.firstOrNull() ?: palette.neon)
        upload(neonTex, atlas.bitmap, mip = true)
        atlas.bitmap.recycle()
        atlasStarts = IntArray(3) { 12 + atlas.groupStart[it] }; atlasCounts = atlas.groupCount.copyOf()
        val mesh = planeZ(MachiKowloonLayout.SCREEN_X, MachiKowloonLayout.SCREEN_Y, MachiKowloonLayout.SCREEN_Z + 0.006, MachiKowloonLayout.SCREEN_W, MachiKowloonLayout.SCREEN_H) +
            planeZ(TICKER_X, TICKER_Y, TICKER_Z, TICKER_W, TICKER_H) + atlas.mesh + hologramMesh()
        holoStart = 12 + atlas.mesh.size / 5
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
    override fun show(target: MachiBoardTarget, lines: List<Line>) {
        val key = keyOf(target)
        if (key == targetKey) return
        targetKey = key
        val slot = prepare(target, lines)
        if (current == null) { current = slot; switchElapsed = SWITCH_SECONDS; pending = null }
        else if (slot !== current) { pending = slot; switchElapsed = 0.0 }
        val holo = resolveKowloonHologramText(target, lines, title, artist)
        val hk = "${holo.first}|${holo.second}"
        if (hk != holoKey) {
            val first = holoKey.isEmpty()
            holoKey = hk
            if (first) { paintKowloonHologram(holoBitmap, typeface, holo.first, holo.second); upload(holoTex, holoBitmap); holoSwitch = HOLO_SWITCH }
            else { holoPending = holo; holoSwitch = 0.0 }
        }
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

    /** [advance] = false：只画不推进状态（倒影 pass 再画一遍用）。 */
    override fun draw(vp: FloatArray, view: FloatArray, frame: MachiSignFrame, advance: Boolean) {
        val viewZ = view[2]
        val cur = current ?: return
        var glitch = 0.0
        if (switchElapsed < SWITCH_SECONDS) {
            if (advance) switchElapsed += max(frame.delta, 1.0 / 120)
            val t = min(1.0, switchElapsed / SWITCH_SECONDS)
            if (advance && t >= 0.45) pending?.let { current = it; pending = null }
            glitch = if (t >= 1) 0.0 else sin(PI * t).pow(0.7)
        }
        if (advance) chorusGlow += (frame.chorus - chorusGlow) * (1 - exp(-frame.delta * 3))
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
        if (advance) ledScroll = (ledScroll + frame.delta * 35.0 * (0.8 + 0.4 * chorusGlow) / max(1.0, ledUsed * LED_W.toDouble())) % 1.0
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
        // 店招（kowloonSigns.ts update）：霓虹白天也亮着；稳定组 × (0.93 + 0.07 flicker)，两组坏灯管时不时闪一阵、灭一下。
        GLES30.glUseProgram(neonProgram)
        fun n(s: String) = GLES30.glGetUniformLocation(neonProgram, s)
        GLES30.glUniformMatrix4fv(n("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, neonTex)
        GLES30.glUniform1i(n("uMap"), 0)
        val neon = frame.artificial.pow(1.4) * (1 + 0.1 * chorusGlow)
        fun broken(salt: String, offset: Double): Double {
            val f = resolveMachiFlicker(frame.ambientTime + offset, "$seed:$salt", 1.8)
            return neon * (if (f < 1) 0.12 + 0.3 * (f - 0.5) else 1.0)
        }
        val levels = doubleArrayOf(neon * (0.93 + 0.07 * frame.flicker), broken("neonA", 1.3), broken("neonB", 3.7))
        for (g in 0..2) {
            if (atlasCounts[g] == 0) continue
            GLES30.glUniform1f(n("uLevel"), levels[g].toFloat())
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, atlasStarts[g], atlasCounts[g])
        }
        // 全息投影（hologram.ts）：加色、不写深度；先光束后面板。
        var holoGlitch = 0.0
        if (holoSwitch < HOLO_SWITCH) {
            if (advance) holoSwitch += max(frame.delta, 1.0 / 120)
            val t = min(1.0, holoSwitch / HOLO_SWITCH)
            if (advance && t >= 0.4) holoPending?.let { paintKowloonHologram(holoBitmap, typeface, it.first, it.second); upload(holoTex, holoBitmap); holoPending = null }
            holoGlitch = if (t >= 1) 0.0 else sin(PI * t)
        }
        val holoLevel = (0.3 + 0.7 * (1 - frame.daylight)) * (0.9 + 0.1 * frame.flicker) * (1 + 0.15 * chorusGlow)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(beamProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(beamProgram, "uVP"), 1, false, vp, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(beamProgram, "uColor"), 1, holoColor, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(beamProgram, "uLevel"), holoLevel.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(beamProgram, "uTime"), frame.ambientTime.toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, holoStart + 6, 6)
        GLES30.glUseProgram(holoProgram)
        fun hu(n: String) = GLES30.glGetUniformLocation(holoProgram, n)
        GLES30.glUniformMatrix4fv(hu("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, holoTex)
        GLES30.glUniform1i(hu("uMap"), 0)
        GLES30.glUniform3fv(hu("uColor"), 1, holoColor, 0)
        GLES30.glUniform1f(hu("uTime"), frame.ambientTime.toFloat())
        GLES30.glUniform1f(hu("uLevel"), holoLevel.toFloat())
        GLES30.glUniform1f(hu("uGlitch"), holoGlitch.toFloat())
        GLES30.glUniform1f(hu("uAspect"), (HOLO_W / HOLO_H).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, holoStart, 6)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }

    private fun linear(hex: String, k: Double) = machiLinear(hex, k)

    /** 面板（绕 y 转 30°）+ 光束梯形（镜头 → 面板下沿），pos3 + uv2。 */
    private fun hologramMesh(): FloatArray {
        val ry = 30 * PI / 180; val ax = kotlin.math.cos(ry); val az = -sin(ry)
        val out = ArrayList<Float>()
        fun pt(cx: Double, cy: Double, cz: Double, sx: Double, sy: Double, u: Float, v: Float) {
            out += (cx + ax * sx).toFloat(); out += (cy + sy).toFloat(); out += (cz + az * sx).toFloat(); out += u; out += v
        }
        val hw = HOLO_W / 2; val hh = HOLO_H / 2
        pt(HOLO_X, HOLO_Y, HOLO_Z, -hw, -hh, 0f, 0f); pt(HOLO_X, HOLO_Y, HOLO_Z, hw, -hh, 1f, 0f); pt(HOLO_X, HOLO_Y, HOLO_Z, hw, hh, 1f, 1f)
        pt(HOLO_X, HOLO_Y, HOLO_Z, -hw, -hh, 0f, 0f); pt(HOLO_X, HOLO_Y, HOLO_Z, hw, hh, 1f, 1f); pt(HOLO_X, HOLO_Y, HOLO_Z, -hw, hh, 0f, 1f)
        val rise = HOLO_Y - hh - 0.27; val lens = 0.08
        pt(HOLO_X, 0.27, HOLO_Z, -lens, 0.0, 0f, 0f); pt(HOLO_X, 0.27, HOLO_Z, lens, 0.0, 1f, 0f); pt(HOLO_X, 0.27, HOLO_Z, hw, rise, 1f, 1f)
        pt(HOLO_X, 0.27, HOLO_Z, -lens, 0.0, 0f, 0f); pt(HOLO_X, 0.27, HOLO_Z, hw, rise, 1f, 1f); pt(HOLO_X, 0.27, HOLO_Z, -hw, rise, 0f, 1f)
        return out.toFloatArray()
    }

    companion object {
        // layout.ts HOLOGRAM
        const val HOLO_X = 0.9; const val HOLO_Z = 2.3; const val HOLO_Y = 3.0; const val HOLO_W = 3.0; const val HOLO_H = 1.1
        const val HOLO_SWITCH = 0.35
        // layout.ts TICKER
        const val TICKER_X = 2.1; const val TICKER_Y = 3.09; const val TICKER_Z = -1.41; const val TICKER_W = 4.2; const val TICKER_H = 0.17
    }
}


/** 竖直平面（绕 y 转 a，朝 (sin a, 0, cos a)），pos3 + uv2。 */
private fun planeRotY(x: Double, y: Double, z: Double, w: Double, h: Double, a: Double): FloatArray {
    val ex = kotlin.math.cos(a); val ez = -sin(a)
    fun p(sx: Double, sy: Double, u: Float, v: Float) = floatArrayOf((x + ex * sx).toFloat(), (y + sy).toFloat(), (z + ez * sx).toFloat(), u, v)
    val a0 = p(-w / 2, -h / 2, 0f, 0f); val a1 = p(w / 2, -h / 2, 1f, 0f); val a2 = p(w / 2, h / 2, 1f, 1f); val a3 = p(-w / 2, h / 2, 0f, 1f)
    return a0 + a1 + a2 + a0 + a2 + a3
}

/** konbiniSignPaint.ts paintKonbiniFascia（图标走兜底 Store）：左圆徽、中店名（曲名）、右 24H 小牌。 */
internal fun paintKonbiniFascia(bitmap: Bitmap, typeface: Typeface, title: String, base: String, text: String, brand: List<String>) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(hexInt(base))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
    val badge = h * 0.86f; val bx = h * 0.3f; val by = (h - badge) / 2
    p.color = hexInt(brand[0]); c.drawCircle(bx + badge / 2, by + badge / 2, badge / 2, p)
    drawMachiIcon(c, MACHI_FALLBACK_ICONS[0], bx + badge * 0.2f, by + badge * 0.2f, badge * 0.6f, 0xFFFFFFFF.toInt(), 2.4f)
    val tagW = h * 1.5f; val left = bx + badge + h * 0.35f; val right = w - tagW - h * 0.3f
    p.color = hexInt(text); p.textAlign = Paint.Align.LEFT
    fitFont(p, title, right - left, h * 0.6f)
    c.drawText(title, left, h * 0.53f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    p.color = hexInt(brand[2])
    c.drawRoundRect(w - tagW - h * 0.15f, h * 0.18f, w - h * 0.15f, h * 0.82f, h * 0.12f, h * 0.12f, p)
    p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.CENTER; p.typeface = machiTypeface(800)
    fitFont(p, "24H", tagW * 0.8f, h * 0.42f)
    c.drawText("24H", w - tagW / 2 - h * 0.15f, h * 0.52f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
}

/** paintKonbiniPylon（图标走兜底 Store）：上半品牌色带、下半白底 24 HOURS。 */
internal fun paintKonbiniPylon(bitmap: Bitmap, typeface: Typeface, base: String, brand: List<String>) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(hexInt(base))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = machiTypeface(800) }
    p.color = hexInt(brand[0]); c.drawRect(0f, 0f, w, h * 0.58f, p)
    p.color = hexInt(brand[2]); c.drawRect(0f, h * 0.58f, w, h * 0.63f, p)
    val iconSize = h * 0.44f
    drawMachiIcon(c, MACHI_FALLBACK_ICONS[0], (w - iconSize) / 2, h * 0.07f, iconSize, 0xFFFFFFFF.toInt(), 2.6f)
    p.textAlign = Paint.Align.CENTER
    p.color = hexInt(brand[2]); fitFont(p, "24", w * 0.5f, h * 0.26f)
    c.drawText("24", w * 0.32f, h * 0.8f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    p.color = 0xFF2A2F3A.toInt(); p.typeface = typeface; fitFont(p, "HOURS", w * 0.36f, h * 0.12f)
    c.drawText("HOURS", w * 0.66f, h * 0.8f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
}

/**
 * 便利店的招牌（konbiniSigns.ts）：屋顶三面广告屏（同一块牌面，逐字点亮当前行）、右面店招 LED 跑马灯（下一句 / 译文）、
 * 前面店招（曲名当店名，level 0.78）、立柱灯箱两面（level 0.95）。玻璃海报、窗内霓虹、屋顶 logo、A 字立牌后接。
 * [boardFaces]：x, y, z, rotY；[fascia]：x, y, z, rotY, w, h（前、右）；[pylon]：同上两面。
 */
internal class MachiKonbiniSigns(
    private val palette: MachiPalette,
    private val title: String,
    private val artist: String,
    private val seed: String,
    private val boardFaces: List<DoubleArray>,
    private val fascia: List<DoubleArray>,
    private val pylon: List<DoubleArray>,
) : MachiSigns {
    private val screenW = MachiKonbiniLayout.BOARD_W - 0.14
    private val screenH = MachiKonbiniLayout.BOARD_H - 0.12
    private val typeface = machiTypeface(700)
    private val measure = MachiTextMeasure(typeface)
    private val half = (SCREEN_TEXTURE_WIDTH * (screenH / screenW)).roundToInt()
    private val box = MachiBoardBox(SCREEN_TEXTURE_WIDTH.toDouble(), half.toDouble(), 0.05, MACHI_REFERENCE_PX)
    private val cells = floatArrayOf(CELLS_X.toFloat(), (CELLS_X * screenH / screenW).roundToInt().toFloat())
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
    private var fasciaTex = 0
    private var pylonTex = 0
    private val ledBitmap = Bitmap.createBitmap(LED_W, LED_H, Bitmap.Config.ARGB_8888)
    private var ledText = ""
    private var ledUsed = 1f
    private var ledWindow = 0.3f
    private var ledScroll = 0.0
    // ledStrip.ts：竖向 18 个点。
    private val ledDots = 18f
    private val ledAspect = (fascia[1][4] / fascia[1][5]).toFloat()
    private val normals = boardFaces.map { floatArrayOf(sin(it[3]).toFloat(), 0f, kotlin.math.cos(it[3]).toFloat()) }

    override fun init(link: (String, String) -> Int) {
        screenProgram = link(SIGN_VS, SCREEN_FS)
        ledProgram = link(SIGN_VS, LED_FS)
        neonProgram = link(SIGN_VS, NEON_FS)
        val ids = IntArray(5); GLES30.glGenTextures(5, ids, 0)
        slots = Array(2) { Slot(ids[it], Bitmap.createBitmap(SCREEN_TEXTURE_WIDTH, half * 2, Bitmap.Config.ARGB_8888)) }
        ledTex = ids[2]; fasciaTex = ids[3]; pylonTex = ids[4]
        val ff = fascia[0]
        val fb = Bitmap.createBitmap(1600, (1600 * ff[5] / ff[4]).roundToInt(), Bitmap.Config.ARGB_8888)
        paintKonbiniFascia(fb, typeface, title.ifEmpty { "KONBINI" }, palette.signBase, palette.signText, palette.brand)
        upload(fasciaTex, fb, mip = true); fb.recycle()
        val pf = pylon[0]
        val pb = Bitmap.createBitmap(384, (384 * pf[5] / pf[4]).roundToInt(), Bitmap.Config.ARGB_8888)
        paintKonbiniPylon(pb, typeface, palette.signBase, palette.brand)
        upload(pylonTex, pb, mip = true); pb.recycle()
        var mesh = FloatArray(0)
        boardFaces.forEach { f -> mesh += planeRotY(f[0] + sin(f[3]) * 0.006, f[1], f[2] + kotlin.math.cos(f[3]) * 0.006, screenW, screenH, f[3]) }
        fascia.forEach { f -> mesh += planeRotY(f[0], f[1], f[2], f[4], f[5], f[3]) }
        pylon.forEach { f -> mesh += planeRotY(f[0], f[1], f[2], f[4], f[5], f[3]) }
        val va = IntArray(1); GLES30.glGenVertexArrays(1, va, 0); vao = va[0]
        val bufs = IntArray(1); GLES30.glGenBuffers(1, bufs, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        val buf = java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, buf, GLES30.GL_STATIC_DRAW)
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
        val layout = if (card) layoutMachiBoardCard("♪ ${title.ifEmpty { "KONBINI" }}", artist, box, measure) else layoutMachiBoardLine(line!!, box, measure)
        paintMachiBoard(slot.bitmap, layout, typeface, palette.boardUnlit, palette.boardLit, card)
        upload(slot.tex, slot.bitmap)
        slot.key = key; slot.layout = layout
        return slot
    }

    override fun show(target: MachiBoardTarget, lines: List<Line>) {
        val key = keyOf(target)
        if (key == targetKey) return
        targetKey = key
        val slot = prepare(target, lines)
        if (current == null) { current = slot; switchElapsed = SWITCH_SECONDS; pending = null }
        else if (slot !== current) { pending = slot; switchElapsed = 0.0 }
        val text = resolveMachiTickerText(target, lines, title, artist, "KONBINI")
        if (text != ledText) {
            ledText = text
            val u = paintMachiLedStrip(ledBitmap, typeface, text)
            upload(ledTex, ledBitmap)
            ledUsed = u / LED_W
            ledWindow = min(4f, (floor(ledDots * ledAspect) * (LED_H / ledDots)) / max(1f, u))
        }
    }

    override fun draw(vp: FloatArray, view: FloatArray, frame: MachiSignFrame, advance: Boolean) {
        val cur = current ?: return
        var glitch = 0.0
        if (switchElapsed < SWITCH_SECONDS) {
            if (advance) switchElapsed += max(frame.delta, 1.0 / 120)
            val t = min(1.0, switchElapsed / SWITCH_SECONDS)
            if (advance && t >= 0.45) pending?.let { current = it; pending = null }
            glitch = if (t >= 1) 0.0 else sin(PI * t).pow(0.7)
        }
        if (advance) chorusGlow += (frame.chorus - chorusGlow) * (1 - exp(-frame.delta * 3))
        val slot = current ?: cur
        val layout = slot.layout
        GLES30.glBindVertexArray(vao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
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
        GLES30.glUniform3fv(u("uBase"), 1, machiLinear(palette.boardBase, if (palette.boardIsLight) 0.98 else 1.0), 0)
        GLES30.glUniform1f(u("uLitGain"), (if (palette.boardIsLight) 1.0 else 1.25 + 0.2 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uUnlitGain"), if (palette.boardIsLight) 1f else 0.9f)
        GLES30.glUniform1f(u("uBright"), (1 + 0.05 * frame.audio + 0.06 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uPeak"), if (palette.boardIsLight) PEAK_LIGHT else PEAK_DARK)
        GLES30.glUniform1f(u("uGlitch"), glitch.toFloat())
        GLES30.glUniform1f(u("uTime"), frame.ambientTime.toFloat())
        GLES30.glUniform1f(u("uSoft"), 0.004f)
        GLES30.glUniform2fv(u("uCells"), 1, cells, 0)
        GLES30.glUniform3fv(u("uSheen"), 1, machiLinear(palette.moon, 0.35), 0)
        for (i in boardFaces.indices) {
            val n = normals[i]
            GLES30.glUniform1f(u("uViewZ"), view[0] * n[0] + view[1] * n[1] + view[2] * n[2])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, i * 6, 6)
        }
        val base = boardFaces.size * 6
        // 前面店招（静态，MeshBasic × 0.78 × (1 + 0.08 chorus)）、立柱灯箱两面（× 0.95 × flicker）。
        GLES30.glUseProgram(neonProgram)
        fun n(s: String) = GLES30.glGetUniformLocation(neonProgram, s)
        GLES30.glUniformMatrix4fv(n("uVP"), 1, false, vp, 0)
        GLES30.glUniform1i(n("uMap"), 0)
        val lift = 1 + 0.08 * chorusGlow + 0.05 * frame.audio
        val flicker = if (chorusGlow > 0.5) 1.0 else frame.flicker
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fasciaTex)
        GLES30.glUniform1f(n("uLevel"), (0.78 * lift).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, base, 6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pylonTex)
        GLES30.glUniform1f(n("uLevel"), (0.95 * flicker * lift).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, base + 12, pylon.size * 6)
        // 右面店招 LED 跑马灯（速度 × (1 + 0.5 chorus)）。
        if (advance) ledScroll = (ledScroll + frame.delta * 35.0 * (1 + 0.5 * chorusGlow) / max(1.0, ledUsed * LED_W.toDouble())) % 1.0
        GLES30.glUseProgram(ledProgram)
        fun l(s: String) = GLES30.glGetUniformLocation(ledProgram, s)
        GLES30.glUniformMatrix4fv(l("uVP"), 1, false, vp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ledTex)
        GLES30.glUniform1i(l("uMap"), 0)
        GLES30.glUniform3fv(l("uLed"), 1, machiLinear(palette.led, 1.0), 0)
        GLES30.glUniform1f(l("uScroll"), ledScroll.toFloat())
        GLES30.glUniform1f(l("uUsed"), ledUsed)
        GLES30.glUniform1f(l("uWindow"), ledWindow)
        GLES30.glUniform1f(l("uDots"), ledDots)
        GLES30.glUniform1f(l("uAspect"), ledAspect)
        GLES30.glUniform1f(l("uBright"), (0.9 + 0.2 * frame.audio).toFloat())
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, base + 6, 6)
        GLES30.glBindVertexArray(0)
    }
}

/** 一块会滚字的 LED 面：x, y, z, rotY, w, h；dots 竖向点数；text 按牌面给字。 */
internal class MachiLedSpec(val face: DoubleArray, val dots: Float, val text: (MachiBoardTarget, List<Line>) -> String)

/** 一组静态招牌（同一张贴图贴在几块面上）：painter 画贴图，key 变了才重画（null 只画一次），level 是 MeshBasic 的亮度。 */
internal class MachiStaticSpec(val faces: List<DoubleArray>, val widthPx: Int, val level: Double, val painter: (Bitmap, Double, Int) -> Unit, val key: ((Double, Int) -> String)? = null)

/**
 * 通用的场景招牌（车站 stationSigns.ts 等）：一块逐字点亮的歌词屏（可多面）、若干 LED 跑马灯、若干静态灯箱。
 * 牌面、跑马灯照 MachiKowloonSigns / MachiKonbiniSigns 的同一套着色器。
 */
internal class MachiPanelSigns(
    private val palette: MachiPalette,
    private val title: String,
    private val artist: String,
    private val fallbackTitle: String,
    private val screenW: Double,
    private val screenH: Double,
    private val screenFaces: List<DoubleArray>,
    private val leds: List<MachiLedSpec>,
    private val statics: List<MachiStaticSpec>,
) : MachiSigns {
    private val typeface = machiTypeface(700)
    private val measure = MachiTextMeasure(typeface)
    private val half = (SCREEN_TEXTURE_WIDTH * (screenH / screenW)).roundToInt()
    private val box = MachiBoardBox(SCREEN_TEXTURE_WIDTH.toDouble(), half.toDouble(), 0.05, MACHI_REFERENCE_PX)
    private val cells = floatArrayOf(CELLS_X.toFloat(), (CELLS_X * screenH / screenW).roundToInt().toFloat())
    private class Slot(val tex: Int, val bitmap: Bitmap) { var key = ""; var layout: MachiBoardLayout? = null }
    private class Led(val spec: MachiLedSpec, val tex: Int, val bitmap: Bitmap) { var text = ""; var used = 1f; var window = 0.3f; var scroll = 0.0 }
    private class Static(val spec: MachiStaticSpec, val tex: Int, val bitmap: Bitmap) { var key: String? = null }
    private lateinit var slots: Array<Slot>
    private lateinit var ledState: List<Led>
    private lateinit var staticState: List<Static>
    private var current: Slot? = null
    private var pending: Slot? = null
    private var switchElapsed = SWITCH_SECONDS
    private var targetKey = ""
    private var target: MachiBoardTarget? = null
    private var chorusGlow = 0.0
    private var screenProgram = 0
    private var ledProgram = 0
    private var neonProgram = 0
    private var vao = 0
    private val normals = screenFaces.map { floatArrayOf(sin(it[3]).toFloat(), 0f, kotlin.math.cos(it[3]).toFloat()) }

    override fun init(link: (String, String) -> Int) {
        screenProgram = link(SIGN_VS, SCREEN_FS)
        ledProgram = link(SIGN_VS, LED_FS)
        neonProgram = link(SIGN_VS, NEON_FS)
        val n = 2 + leds.size + statics.size
        val ids = IntArray(n); GLES30.glGenTextures(n, ids, 0)
        slots = Array(2) { Slot(ids[it], Bitmap.createBitmap(SCREEN_TEXTURE_WIDTH, half * 2, Bitmap.Config.ARGB_8888)) }
        ledState = leds.mapIndexed { i, s -> Led(s, ids[2 + i], Bitmap.createBitmap(LED_W, LED_H, Bitmap.Config.ARGB_8888)) }
        staticState = statics.mapIndexed { i, s ->
            val f = s.faces[0]
            Static(s, ids[2 + leds.size + i], Bitmap.createBitmap(s.widthPx, max(8, (s.widthPx * f[5] / f[4]).roundToInt()), Bitmap.Config.ARGB_8888))
        }
        staticState.forEach { repaint(it, 0.0, -1) }
        var mesh = FloatArray(0)
        screenFaces.forEach { f -> mesh += planeRotY(f[0] + sin(f[3]) * 0.006, f[1], f[2] + kotlin.math.cos(f[3]) * 0.006, screenW, screenH, f[3]) }
        leds.forEach { l -> val f = l.face; mesh += planeRotY(f[0], f[1], f[2], f[4], f[5], f[3]) }
        statics.forEach { s -> s.faces.forEach { f -> mesh += planeRotY(f[0], f[1], f[2], f[4], f[5], f[3]) } }
        val va = IntArray(1); GLES30.glGenVertexArrays(1, va, 0); vao = va[0]
        val bufs = IntArray(1); GLES30.glGenBuffers(1, bufs, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        val buf = java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
        GLES30.glBindVertexArray(0)
    }

    private fun repaint(s: Static, songTime: Double, lineIndex: Int) {
        val k = s.spec.key?.invoke(songTime, lineIndex) ?: "once"
        if (k == s.key) return
        s.key = k
        s.bitmap.eraseColor(0)
        s.spec.painter(s.bitmap, songTime, lineIndex)
        upload(s.tex, s.bitmap, mip = true)
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

    private fun keyOf(t: MachiBoardTarget) = if (t.isCard) "card" else "line:${t.index}"

    private fun prepare(t: MachiBoardTarget, lines: List<Line>): Slot {
        val key = keyOf(t)
        slots.firstOrNull { it.key == key }?.let { return it }
        val slot = slots.first { it !== current }
        val line = if (t.isCard) null else lines.getOrNull(t.index)
        val card = line == null
        val layout = if (card) layoutMachiBoardCard("♪ ${title.ifEmpty { fallbackTitle }}", artist, box, measure) else layoutMachiBoardLine(line!!, box, measure)
        paintMachiBoard(slot.bitmap, layout, typeface, palette.boardUnlit, palette.boardLit, card)
        upload(slot.tex, slot.bitmap)
        slot.key = key; slot.layout = layout
        return slot
    }

    override fun show(target: MachiBoardTarget, lines: List<Line>) {
        val key = keyOf(target)
        if (key == targetKey) return
        targetKey = key
        this.target = target
        val slot = prepare(target, lines)
        if (current == null) { current = slot; switchElapsed = SWITCH_SECONDS; pending = null }
        else if (slot !== current) { pending = slot; switchElapsed = 0.0 }
        val aspect = { l: Led -> (l.spec.face[4] / l.spec.face[5]).toFloat() }
        ledState.forEach { l ->
            val text = l.spec.text(target, lines)
            if (text != l.text) {
                l.text = text
                val u = paintMachiLedStrip(l.bitmap, typeface, text)
                upload(l.tex, l.bitmap)
                l.used = u / LED_W
                l.window = min(4f, (floor(l.spec.dots * aspect(l)) * (LED_H / l.spec.dots)) / max(1f, u))
            }
        }
    }

    override fun draw(vp: FloatArray, view: FloatArray, frame: MachiSignFrame, advance: Boolean) {
        val cur = current ?: return
        var glitch = 0.0
        if (switchElapsed < SWITCH_SECONDS) {
            if (advance) switchElapsed += max(frame.delta, 1.0 / 120)
            val t = min(1.0, switchElapsed / SWITCH_SECONDS)
            if (advance && t >= 0.45) pending?.let { current = it; pending = null }
            glitch = if (t >= 1) 0.0 else sin(PI * t).pow(0.7)
        }
        if (advance) {
            chorusGlow += (frame.chorus - chorusGlow) * (1 - exp(-frame.delta * 3))
            val li = target?.let { if (it.isCard) -1 else it.index } ?: -1
            staticState.forEach { repaint(it, frame.songTime, li) }
        }
        val slot = current ?: cur
        val layout = slot.layout
        GLES30.glBindVertexArray(vao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
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
        GLES30.glUniform3fv(u("uBase"), 1, machiLinear(palette.boardBase, if (palette.boardIsLight) 0.98 else 1.0), 0)
        GLES30.glUniform1f(u("uLitGain"), (if (palette.boardIsLight) 1.0 else 1.25 + 0.2 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uUnlitGain"), if (palette.boardIsLight) 1f else 0.9f)
        GLES30.glUniform1f(u("uBright"), (1 + 0.05 * frame.audio + 0.06 * chorusGlow).toFloat())
        GLES30.glUniform1f(u("uPeak"), if (palette.boardIsLight) PEAK_LIGHT else PEAK_DARK)
        GLES30.glUniform1f(u("uGlitch"), glitch.toFloat())
        GLES30.glUniform1f(u("uTime"), frame.ambientTime.toFloat())
        GLES30.glUniform1f(u("uSoft"), 0.004f)
        GLES30.glUniform2fv(u("uCells"), 1, cells, 0)
        GLES30.glUniform3fv(u("uSheen"), 1, machiLinear(palette.moon, 0.35), 0)
        for (i in screenFaces.indices) {
            val n = normals[i]
            GLES30.glUniform1f(u("uViewZ"), view[0] * n[0] + view[1] * n[1] + view[2] * n[2])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, i * 6, 6)
        }
        var first = screenFaces.size * 6
        GLES30.glUseProgram(ledProgram)
        fun l(s: String) = GLES30.glGetUniformLocation(ledProgram, s)
        GLES30.glUniformMatrix4fv(l("uVP"), 1, false, vp, 0)
        GLES30.glUniform1i(l("uMap"), 0)
        GLES30.glUniform3fv(l("uLed"), 1, machiLinear(palette.led, 1.0), 0)
        GLES30.glUniform1f(l("uBright"), (0.9 + 0.2 * frame.audio).toFloat())
        ledState.forEach { led ->
            if (advance) led.scroll = (led.scroll + frame.delta * 35.0 * (0.8 + 0.4 * chorusGlow) / max(1.0, led.used * LED_W.toDouble())) % 1.0
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, led.tex)
            GLES30.glUniform1f(l("uScroll"), led.scroll.toFloat())
            GLES30.glUniform1f(l("uUsed"), led.used)
            GLES30.glUniform1f(l("uWindow"), led.window)
            GLES30.glUniform1f(l("uDots"), led.spec.dots)
            GLES30.glUniform1f(l("uAspect"), (led.spec.face[4] / led.spec.face[5]).toFloat())
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, first, 6)
            first += 6
        }
        GLES30.glUseProgram(neonProgram)
        fun n(s: String) = GLES30.glGetUniformLocation(neonProgram, s)
        GLES30.glUniformMatrix4fv(n("uVP"), 1, false, vp, 0)
        GLES30.glUniform1i(n("uMap"), 0)
        staticState.forEach { st ->
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, st.tex)
            GLES30.glUniform1f(n("uLevel"), st.spec.level.toFloat())
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, first, st.spec.faces.size * 6)
            first += st.spec.faces.size * 6
        }
        GLES30.glBindVertexArray(0)
    }
}

/** 霓虹字（strokeNeonText 的近似：外发光 + 实心字）。 */
private fun neonText(c: Canvas, p: Paint, text: String, x: Float, y: Float, color: Int, glow: Float) {
    p.color = color
    p.setShadowLayer(glow, 0f, 0f, color)
    c.drawText(text, x, y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    p.clearShadowLayer()
    p.color = (color and 0x00FFFFFF) or 0xFF000000.toInt()
    c.drawText(text, x, y - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p.apply { alpha = 255 })
}

/** paintStationCanopy（图标走兜底 Store）：左圆徽、中店名（歌手大写）、右 SELF 小牌。 */
internal fun paintStationCanopy(bitmap: Bitmap, name: String, base: String, text: String, brand: List<String>) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(hexInt(base))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = machiTypeface(700) }
    val badge = h * 0.86f; val bx = h * 0.2f; val by = (h - badge) / 2
    p.color = hexInt(brand[0]); c.drawCircle(bx + badge / 2, by + badge / 2, badge / 2, p)
    drawMachiIcon(c, MACHI_FALLBACK_ICONS[0], bx + badge * 0.2f, by + badge * 0.2f, badge * 0.6f, 0xFFFFFFFF.toInt(), 2.4f)
    val tagW = h * 1.7f; val left = bx + badge + h * 0.3f; val right = w - tagW - h * 0.3f
    p.color = hexInt(text); p.textAlign = Paint.Align.LEFT
    fitFont(p, name, right - left, h * 0.62f)
    c.drawText(name, left, h * 0.53f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    p.color = hexInt(brand[2])
    c.drawRoundRect(w - tagW - h * 0.12f, h * 0.16f, w - h * 0.12f, h * 0.84f, h * 0.12f, h * 0.12f, p)
    p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.CENTER; p.typeface = machiTypeface(800)
    fitFont(p, "SELF", tagW * 0.8f, h * 0.46f)
    c.drawText("SELF", w - tagW / 2 - h * 0.12f, h * 0.53f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
}

/** paintStationPylon（七段数码管照译，图标走兜底 Store）：上头品牌色 SELF，下面三行价格牌（播放进度、总时长、第几句）。 */
internal fun paintStationPylon(bitmap: Bitmap, brand: List<String>, rows: List<Pair<String, String>>) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(0xFFF4F2EC.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = machiTypeface(800) }
    val head = h * 0.34f
    p.color = hexInt(brand[0]); c.drawRect(0f, 0f, w, head, p)
    p.color = hexInt(brand[2]); c.drawRect(0f, head - h * 0.025f, w, head, p)
    val iconSize = head * 0.62f
    drawMachiIcon(c, MACHI_FALLBACK_ICONS[0], (w - iconSize) / 2, head * 0.08f, iconSize, 0xFFFFFFFF.toInt(), 2.3f)
    p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.CENTER
    fitFont(p, "SELF", w * 0.8f, head * 0.2f)
    c.drawText("SELF", w / 2, head * 0.84f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    // 三行价格牌（stationSignPaint.ts）：深色底，标签在左上角一条小字，七段数字在它下面靠右；三行同一个字高。
    val rowTop = head + h * 0.03f
    val rowH = (h - rowTop - h * 0.03f) / rows.size
    fun span(value: String): Float {
        var sum = 0f
        value.forEachIndexed { i, ch -> sum += (if (ch == ':') 0.26f else 0.52f) + (if (i > 0 && ch != ':' && value[i - 1] != ':') 0.1664f else 0f) }
        return sum
    }
    val widest = max(1f, rows.maxOf { span(it.second) })
    val pt = rowH * 0.06f; val ph = rowH * 0.88f; val band = ph * 0.28f
    val digitArea = ph - band - ph * 0.06f
    val digitH = min(digitArea * 0.88f, (w * 0.8f) / widest)
    val digitColor = 0xFFFF5A3C.toInt(); val offColor = android.graphics.Color.argb(13, 255, 255, 255)
    rows.forEachIndexed { i, (label, value) ->
        val y = rowTop + i * rowH
        p.color = 0xFF16191F.toInt(); c.drawRect(w * 0.05f, y + pt, w * 0.95f, y + pt + ph, p)
        p.color = 0xFFE8EAEE.toInt(); p.textAlign = Paint.Align.LEFT; p.typeface = machiTypeface(700)
        fitFont(p, label, w * 0.78f, band * 0.62f)
        c.drawText(label, w * 0.1f, y + pt + band * 0.58f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
        val digitW = digitH * 0.52f; val gap = digitW * 0.32f; val colonW = digitW * 0.5f
        var x = w * 0.9f - span(value) * digitH
        val dy = y + pt + band + (digitArea - digitH) / 2
        value.forEachIndexed { ci, ch ->
            if (ci > 0 && ch != ':' && value[ci - 1] != ':') x += gap
            if (ch == ':') {
                p.color = digitColor
                val r = digitW * 0.09f
                c.drawCircle(x + colonW * 0.5f, dy + digitH * 0.32f, r, p)
                c.drawCircle(x + colonW * 0.5f, dy + digitH * 0.72f, r, p)
                x += colonW
                return@forEachIndexed
            }
            drawSevenSegment(c, p, ch, x, dy, digitW, digitH, digitColor, offColor)
            x += digitW
        }
    }
}

/** 七段数码管的段（a 上、b 右上、c 右下、d 下、e 左下、f 左上、g 中）。 */
private val SEVEN_SEGMENTS = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg", '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g", ' ' to "",
)

/** stationSignPaint.ts drawSevenSegment：亮的段用 on 色，暗的段用 off 色（像没点亮的灯管）。 */
private fun drawSevenSegment(c: Canvas, p: Paint, ch: Char, x: Float, y: Float, w: Float, h: Float, on: Int, off: Int) {
    val lit = SEVEN_SEGMENTS[ch] ?: ""
    val t = w * 0.18f; val half = h / 2
    fun segment(name: Char, sx: Float, sy: Float, horizontal: Boolean, length: Float) {
        p.color = if (lit.indexOf(name) >= 0) on else off
        val path = android.graphics.Path()
        if (horizontal) {
            path.moveTo(sx + t * 0.5f, sy); path.lineTo(sx + t, sy - t * 0.5f); path.lineTo(sx + length - t, sy - t * 0.5f)
            path.lineTo(sx + length - t * 0.5f, sy); path.lineTo(sx + length - t, sy + t * 0.5f); path.lineTo(sx + t, sy + t * 0.5f)
        } else {
            path.moveTo(sx, sy + t * 0.5f); path.lineTo(sx + t * 0.5f, sy + t); path.lineTo(sx + t * 0.5f, sy + length - t)
            path.lineTo(sx, sy + length - t * 0.5f); path.lineTo(sx - t * 0.5f, sy + length - t); path.lineTo(sx - t * 0.5f, sy + t)
        }
        path.close()
        c.drawPath(path, p)
    }
    segment('a', x, y + t * 0.5f, true, w)
    segment('g', x, y + half, true, w)
    segment('d', x, y + h - t * 0.5f, true, w)
    segment('f', x + t * 0.5f, y, false, half)
    segment('e', x + t * 0.5f, y + half, false, half)
    segment('b', x + w - t * 0.5f, y, false, half)
    segment('c', x + w - t * 0.5f, y + half, false, half)
}

/** paintMotelTall：「MOTEL」竖排霓虹。 */
internal fun paintMotelTall(bitmap: Bitmap, neon: String) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(0xFF10161C.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = machiTypeface(800); textAlign = Paint.Align.CENTER }
    val letters = "MOTEL"
    val step = h / (letters.length + 0.2f)
    p.textSize = min(w * 0.72f, step * 0.86f)
    letters.forEachIndexed { i, ch -> neonText(c, p, ch.toString(), w / 2, step * (i + 0.6f), hexInt(neon), w * 0.08f) }
}

/** paintMotelName：曲名霓虹（放不下缩字）。 */
internal fun paintMotelName(bitmap: Bitmap, title: String, neon: String) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(0xFF10161C.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = machiTypeface(700); textAlign = Paint.Align.CENTER }
    fitFont(p, title, w * 0.86f, h * 0.62f)
    neonText(c, p, title, w / 2, h * 0.54f, hexInt(neon), h * 0.1f)
}

/** paintMotelVacancy：noLit 时「NO」也亮（客满）。 */
internal fun paintMotelVacancy(bitmap: Bitmap, noLit: Boolean) {
    val c = Canvas(bitmap); val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
    c.drawColor(0xFF10161C.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = machiTypeface(800); textSize = h * 0.62f }
    p.textAlign = Paint.Align.LEFT
    if (noLit) neonText(c, p, "NO", w * 0.05f, h * 0.55f, 0xFFFF3B4A.toInt(), h * 0.12f)
    else { p.color = 0xFF3A2328.toInt(); c.drawText("NO", w * 0.05f, h * 0.55f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p) }
    p.textAlign = Paint.Align.RIGHT
    neonText(c, p, "VACANCY", w * 0.95f, h * 0.55f, 0xFF58F0A0.toInt(), h * 0.12f)
}

/** stationSigns formatClock：分（右对齐两位，封顶 99）:秒。 */
internal fun formatMachiClock(seconds: Double): String {
    val total = max(0, floor(seconds).toInt())
    val minutes = min(99, total / 60)
    return "${minutes.toString().padStart(2, ' ')}:${(total % 60).toString().padStart(2, '0')}"
}
