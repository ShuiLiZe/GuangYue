// 对应 Folia 文件：src/components/visualizer/nebbia/（雾笺）——VisualizerNebbia.tsx（外壳 inset-0、底色 #05070a）、
// nebbiaScene.ts / nebbiaInk.ts / nebbiaMask.ts（按笔顺一笔一笔用手指在雾上写字，擦痕遮罩取最大值叠加）、
// nebbiaShaders.ts 主画面 MAIN_FS（遮罩按细噪声 + 大块噪声取阈值 → 擦痕边毛糙、起雾时一片片回来；雾 = 模糊窗外提亮发灰 +
// 光晕 + 乳白，擦开处是清晰的窗外；哈气把那一片雾变密）、nebbiaBreath.ts（让位哈气）、nebbiaDrops.ts（雾上凝结水珠、
// 擦痕边水珠）、nebbiaSceneGlsl.ts（窗外雨夜街景，见 NebbiaCity）。
//
// 手机化：
//  - 主画面合成着色器 GLSL → AGSL（API 33+），窗外三张图在后台线程算好当纹理；擦痕遮罩每帧在 1/2 CSS 分辨率的位图上画
//    （圆头折线，LIGHTEN = Folia 的 MAX 混合）；白气遮罩 1/4 CSS 分辨率。
//  - 水珠不做法线贴图折射：画成透出清晰窗外的小圆，暗边 + 左上高光（照 raindrop-fx 的光照方向）。
//  - API 33 以下：雾图 + 遮罩按 0.3 阈值剪出清晰窗外（没有噪声毛边与哈气）。
//  - 不做：雨刷 / 颠簸 / 雨点打窗 / 车行平移 / 片尾曲名 / 句尾涂鸦 / 手指抹除（默认 fade 只有 fog）。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** 把一句拆成「字」：中日韩逐字，拉丁按词，每个字分到它在词里的那段时间（花火 / 绘光 / 凝彩共用）。 */
internal fun nebbiaUnits(line: Line): List<Pair<String, Double>> {
    val out = ArrayList<Pair<String, Double>>()
    fun split(text: String, t0: Double, t1: Double) {
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            val ch = text.substring(i, i + n)
            val cjk = Character.isIdeographic(cp) || Character.UnicodeScript.of(cp).let {
                it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
            }
            when {
                Character.isWhitespace(cp) -> { if (sb.isNotEmpty()) { parts += sb.toString(); sb.clear() } }
                cjk -> { if (sb.isNotEmpty()) { parts += sb.toString(); sb.clear() }; parts += ch }
                else -> sb.append(ch)
            }
            i += n
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        if (parts.isEmpty()) return
        val step = (t1 - t0).coerceAtLeast(0.0) / parts.size
        parts.forEachIndexed { k, p -> out += p to (t0 + step * k) }
    }
    if (line.words.isEmpty()) split(line.fullText, line.startTime, line.endTime)
    else line.words.forEach { w -> split(w.text, w.startTime, w.endTime) }
    return out
}

private const val TAG = "NebbiaStage"

/** 窗外三张图（清晰 1/2、雾里两层 1/4 与 1/8 设备分辨率）、回落用的雾图和雾上凝结水珠。 */
private class NebbiaCityImages(
    val sharp: Bitmap,
    val blur: Bitmap,
    val glow: Bitmap,
    val fog: Bitmap,
    /** 每颗 [x, y, rx, ry]，设备像素。 */
    val field: List<FloatArray>,
)

private fun cityBitmap(pw: Int, ph: Int, aspect: Double, b: Double, pal: NebbiaPalette): Bitmap {
    val px = IntArray(pw * ph)
    NebbiaCity.render(pw, ph, aspect, b, pal, px)
    return Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888)
}

private suspend fun buildCityImages(w: Int, h: Int, px: Double, pal: NebbiaPalette): NebbiaCityImages = coroutineScope {
    val aspect = w.toDouble() / h
    // 分辨率按 CSS 像素定（手机像素密度高，按设备像素算太慢）：清晰的 ≈ 2/3 CSS，雾里两层 1/3、1/6 CSS（Folia 1/4、1/8 渲染分辨率）。
    // 清晰的那张分几段并行算（整屏场景最贵的一张）
    val sDiv = max(2, (1.5 * px).roundToInt())
    val fDiv = max(4, (3 * px).roundToInt())
    val sw = max(1, w / sDiv); val sh = max(1, h / sDiv)
    val sharpPx = IntArray(sw * sh)
    val parts = 4
    val jobs = (0 until parts).map { k ->
        async(Dispatchers.Default) { NebbiaCity.render(sw, sh, aspect, 0.0012, pal, sharpPx, sh * k / parts, sh * (k + 1) / parts) }
    }
    val bw = max(1, w / fDiv); val bh = max(1, h / fDiv)
    val gw = max(1, w / (fDiv * 2)); val gh = max(1, h / (fDiv * 2))
    val blurJob = async(Dispatchers.Default) { cityBitmap(bw, bh, aspect, 0.03, pal) }
    val glowJob = async(Dispatchers.Default) { cityBitmap(gw, gh, aspect, 0.09, pal) }
    jobs.awaitAll()
    val blur = blurJob.await()
    val glow = glowJob.await()
    // 回落（API 33 以下）用的雾：在雾的分辨率上照 MAIN_FS 的式子合成一次，不随时间变
    val fogPx = IntArray(bw * bh)
    val bp = IntArray(bw * bh); blur.getPixels(bp, 0, bw, 0, 0, bw, bh)
    val gp = IntArray(gw * gh); glow.getPixels(gp, 0, gw, 0, 0, gw, gh)
    for (y in 0 until bh) for (x in 0 until bw) {
        val dens = NebbiaFixed.MIST * 0.82 * (0.9 + 0.2 * (y + 0.5) / bh)
        val b = bp[y * bw + x]
        val g = gp[min(gh - 1, y * gh / bh) * gw + min(gw - 1, x * gw / bw)]
        fun ch(c: Int, s: Int) = ((c shr s) and 0xFF) / 255.0
        val k = max(0.08, 1 - 0.45 * dens)
        fogPx[y * bw + x] = NebbiaCity.argb(
            ch(b, 16) * k + ch(g, 16) * 0.9 * dens + 0.70 * 0.2 * dens,
            ch(b, 8) * k + ch(g, 8) * 0.9 * dens + 0.74 * 0.2 * dens,
            ch(b, 0) * k + ch(g, 0) * 0.9 * dens + 0.78 * 0.2 * dens,
        )
    }
    // 雾上的凝结水珠（buildField）：W·H/2200·fieldDrops 颗（CSS 像素），下半部分略多；看得见的半径是精灵宽的 1/8
    val rnd = NebbiaSceneBuilder.rng(4242)
    val cw = w / px; val chh = h / px
    val n = (cw * chh / 2200 * NebbiaFixed.FIELD_DROPS).roundToInt()
    val field = (0 until n).map {
        val big = rnd() < 0.12
        val dw = if (big) 40 + rnd() * 34 else 14 + rnd().pow(1.4) * 22
        val sp = rnd() * 0.25
        val x = rnd() * w
        val y = h * (1 - rnd().pow(1.25))
        floatArrayOf(x.toFloat(), y.toFloat(), (dw * (1 + sp * 0.5) / 8 * px).toFloat(), (dw * (1 + sp) / 8 * px).toFloat())
    }
    NebbiaCityImages(Bitmap.createBitmap(sharpPx, sw, sh, Bitmap.Config.ARGB_8888), blur, glow, Bitmap.createBitmap(fogPx, bw, bh, Bitmap.Config.ARGB_8888), field)
}

/** 主题字体画出 96 格的字，二值化后交给骨架追踪（nebbiaSkeleton.ts skeletonPolys）。 */
private class NebbiaGlyphRaster(typeface: Typeface) {
    private val sk = NebbiaSkeleton.SK
    private val bmp = Bitmap.createBitmap(sk, sk, Bitmap.Config.ARGB_8888)
    private val canvas = android.graphics.Canvas(bmp)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = sk * 0.78f
        this.typeface = typeface
    }
    private val pixels = IntArray(sk * sk)
    private val cache = HashMap<String, NebbiaGlyphShape?>()

    fun get(ch: String): NebbiaGlyphShape? = cache.getOrPut(ch) {
        bmp.eraseColor(android.graphics.Color.BLACK)
        canvas.drawText(ch, sk / 2f, sk * 0.8f, paint)
        bmp.getPixels(pixels, 0, sk, 0, 0, sk, sk)
        val img = ByteArray(sk * sk) { if (((pixels[it] shr 16) and 0xFF) > 110) 1 else 0 }
        NebbiaSkeleton.polys(img)?.let { NebbiaGlyphs.measure(it) }
    }
}

/** Folia 主画面 MAIN_FS（雨窗、街景、不透明）的 AGSL 对译；窗外三张图、擦痕遮罩、白气遮罩都是子着色器。 */
private const val NEBBIA_MAIN_AGSL = """
uniform shader blurT;
uniform shader glowT;
uniform shader sharpT;
uniform shader maskT;
uniform shader breathT;
uniform float2 uRes;
uniform float uPx;
uniform float uTime;
uniform float uMist;
uniform float uEdge;
uniform float uBreathOn;
uniform float uBreathThick;
uniform float3 uLa;
uniform float2 uSpotTgt;
uniform float uSpotR;
uniform float uSpotI;

float hash(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}

half4 main(float2 fc) {
  float2 px = fc / uPx;
  float2 uv = fc / uRes;
  float aspect = uRes.x / uRes.y;
  float2 q = float2(uv.x * aspect, uv.y);
  float fine = vnoise(px / 6.0) * 0.6 + vnoise(px / 2.2) * 0.4;
  float blotch = vnoise(px / 55.0 + 3.1);
  float spark = vnoise(px / 1.6);
  float breath = 0.0;
  float melt = 0.0;
  if (uBreathOn > 0.5) {
    float3 bm = float3(breathT.eval(fc).rgb);
    if (bm.r > 0.003) {
      float2 r1 = float2(0.80 * px.x + 0.60 * px.y, -0.60 * px.x + 0.80 * px.y);
      float2 r2 = float2(0.36 * px.x - 0.93 * px.y, 0.93 * px.x + 0.36 * px.y);
      float bn = vnoise(r1 / 34.0 + 7.0) * 0.55 + vnoise(r2 / 15.0 + 2.3) * 0.3 + vnoise(px / 6.5) * 0.15;
      float bth = 0.3 + (bn - 0.5) * 0.5;
      float cover = smoothstep(bth - 0.2, bth + 0.25, bm.r);
      breath = cover * bm.g * uBreathThick;
      melt = cover * bm.b;
    }
  }
  float m = float(maskT.eval(fc).r);
  if (melt > 0.01) {
    float rad = melt * 0.014 * uRes.y;
    float a0 = hash(px) * 6.2832;
    float acc = 0.0;
    for (int i = 0; i < 8; i++) {
      float fi = float(i);
      float a = a0 + fi * 0.7854;
      float rr = rad * (mod(fi, 2.0) < 0.5 ? 1.0 : 0.5);
      acc += float(maskT.eval(fc + float2(cos(a), sin(a)) * rr).r);
    }
    m = mix(m, acc / 8.0, melt);
  }
  float th = 0.3 + (fine - 0.5) * 0.22 * uEdge + (blotch - 0.5) * 0.12;
  float clear = smoothstep(th - 0.05 - 0.12 * melt, th + 0.06 + 0.06 * melt, m);
  if (breath > 0.001) {
    float gr = 0.006 * uRes.y;
    float ga = hash(px + 17.0) * 6.2832;
    float gm = 0.0;
    float gx = m;
    for (int i = 0; i < 4; i++) {
      float a = ga + float(i) * 1.5708;
      float g = float(maskT.eval(fc + float2(cos(a), sin(a)) * gr).r);
      gm += g;
      gx = max(gx, g);
    }
    breath *= 1.0 - 0.55 * smoothstep(0.0, 0.07, gm * 0.25) * (1.0 - smoothstep(0.1, 0.15, gx));
  }
  float dens = uMist * (0.82 + 0.3 * vnoise(px / 380.0 + uTime * 0.015)) * (0.9 + 0.2 * uv.y) + breath * 0.9;
  float3 haze = float3(0.70, 0.74, 0.78);
  float3 blur = float3(blurT.eval(fc).rgb);
  float3 glow = float3(glowT.eval(fc).rgb);
  float3 fog = blur * max(0.08, 1.0 - 0.45 * dens - 0.25 * breath) + glow * 0.9 * dens + haze * (0.2 * dens + 0.25 * breath);
  fog += (hash(px) - 0.5) * 0.025;
  float2 sd = q - uSpotTgt;
  fog += uLa * uSpotI * exp(-dot(sd, sd) / (uSpotR * uSpotR * 2.2)) * (0.18 + 0.4 * dens);
  fog += smoothstep(0.78, 0.98, spark) * 0.05 * dens;
  float3 col = fog;
  if (clear > 0.001) {
    float3 wiped = float3(sharpT.eval(fc).rgb) * 1.05 + haze * 0.02 * dens;
    float rim = clear * (1.0 - clear) * 4.0;
    col = mix(fog, wiped, clear) + rim * haze * 0.16 * dens;
  }
  return half4(half3(col), 1.0);
}
"""

/** 每帧的可变状态：遮罩位图与画笔、聚光灯的平滑强度。 */
private class NebbiaFrameState(w: Int, h: Int, val px: Double) {
    /** 擦痕遮罩相对设备像素的比例（Folia MASK_SCALE 是相对 CSS 像素）。 */
    val ms = (NebbiaFixed.MASK_SCALE / px).toFloat()
    val mask: Bitmap = Bitmap.createBitmap(max(1, (w * ms).roundToInt()), max(1, (h * ms).roundToInt()), Bitmap.Config.ARGB_8888)
    val maskCanvas = android.graphics.Canvas(mask)
    /** 白气遮罩（BREATH_SCALE 0.25，相对 CSS 像素）。 */
    val bs = (0.25 / px).toFloat()
    val breath: Bitmap = Bitmap.createBitmap(max(1, (w * bs).roundToInt()), max(1, (h * bs).roundToInt()), Bitmap.Config.ARGB_8888)
    val breathCanvas = android.graphics.Canvas(breath)
    var breathDirty = true
    val path = Path()
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
    }
    val lobe = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN) }
    val ink = NebbiaInkFrame()
    var spotI = -1.0
    var spotX = 0.0
    var spotY = 0.0
    var spotR = 0.0
    var lastNanos = 0L
    var logged = false
}

/** 每帧在遮罩位图上画擦痕（圆头折线，亮度取最大）。 */
private fun drawMask(st: NebbiaFrameState) {
    st.mask.eraseColor(android.graphics.Color.BLACK)
    val c = st.maskCanvas
    c.save()
    c.scale(st.ms, st.ms)
    for (pl in st.ink.polylines) {
        st.path.reset()
        st.path.moveTo(pl[2].toFloat(), pl[3].toFloat())
        var i = 4
        while (i + 1 < pl.size) { st.path.lineTo(pl[i].toFloat(), pl[i + 1].toFloat()); i += 2 }
        val v = (nClamp(pl[1]) * 255).roundToInt()
        st.stroke.color = android.graphics.Color.rgb(v, v, v)
        st.stroke.strokeWidth = pl[0].toFloat()
        c.drawPath(st.path, st.stroke)
    }
    c.restore()
}

/** 白气遮罩（nebbiaBreath.ts paint）：R 形状、G 浓度、B 糊开；返回这一帧有没有白气。 */
private fun drawBreath(st: NebbiaFrameState, laid: List<NebbiaLine>, t: Double): Boolean {
    var any = false
    for (line in laid) {
        val puff = NebbiaSceneBuilder.breathPuff(line, t) ?: continue
        val b = line.breath ?: continue
        if (!any) { st.breath.eraseColor(android.graphics.Color.BLACK); any = true }
        val c0 = (255 * puff[0]).roundToInt(); val g0 = (255 * puff[1]).roundToInt(); val b0 = (255 * puff[2]).roundToInt()
        val u = puff[3]
        val e = 1 - (1 - u).pow(3)
        val kx = 0.7 + 0.3 * e
        val ky = 0.08 + 0.92 * e
        val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                android.graphics.Color.rgb(c0, g0, b0), android.graphics.Color.rgb((c0 * 0.7).roundToInt(), g0, b0),
                android.graphics.Color.rgb(0, g0, b0), android.graphics.Color.BLACK,
            ),
            floatArrayOf(0f, 0.45f, 0.999f, 1f), Shader.TileMode.CLAMP,
        )
        st.lobe.shader = shader
        val cv = st.breathCanvas
        for (lb in b.lobes) {
            val x = b.cx + (lb[0] - b.cx) * kx
            val y = b.cy + (lb[1] - b.cy) * ky
            cv.save()
            cv.translate((st.bs * x).toFloat(), (st.bs * y).toFloat())
            cv.scale((st.bs * lb[2] * kx).toFloat(), (st.bs * max(lb[3] * ky, 4.0 * st.px)).toFloat())
            cv.drawCircle(0f, 0f, 1f, st.lobe)
            cv.restore()
        }
    }
    if (!any && st.breathDirty) st.breath.eraseColor(android.graphics.Color.BLACK)
    st.breathDirty = any
    return any
}

private fun scaledShader(bmp: Bitmap, w: Float, h: Float): BitmapShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
    setLocalMatrix(Matrix().apply { setScale(w / bmp.width, h / bmp.height) })
}

/** API 33+ 的主画面：一个 AGSL 着色器铺满。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class NebbiaMainShader(city: NebbiaCityImages, st: NebbiaFrameState, w: Float, h: Float) {
    val shader = RuntimeShader(NEBBIA_MAIN_AGSL)
    val brush = ShaderBrush(shader)

    init {
        fun lin(s: BitmapShader) = s.apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
        shader.setInputShader("blurT", lin(scaledShader(city.blur, w, h)))
        shader.setInputShader("glowT", lin(scaledShader(city.glow, w, h)))
        shader.setInputShader("sharpT", lin(scaledShader(city.sharp, w, h)))
        shader.setInputShader("maskT", lin(scaledShader(st.mask, w, h)))
        shader.setInputShader("breathT", lin(scaledShader(st.breath, w, h)))
        shader.setFloatUniform("uRes", w, h)
        shader.setFloatUniform("uPx", st.px.toFloat())
        shader.setFloatUniform("uMist", NebbiaFixed.MIST.toFloat())
        shader.setFloatUniform("uEdge", NebbiaFixed.EDGE.toFloat())
        shader.setFloatUniform("uBreathThick", NebbiaFixed.BREATH_THICK.toFloat())
    }

    fun update(t: Double, breathOn: Boolean, la: DoubleArray, spot: DoubleArray, st: NebbiaFrameState, w: Float, h: Float) {
        shader.setFloatUniform("uTime", t.toFloat())
        shader.setFloatUniform("uBreathOn", if (breathOn) 1f else 0f)
        shader.setFloatUniform("uLa", la[0].toFloat(), la[1].toFloat(), la[2].toFloat())
        shader.setFloatUniform("uSpotTgt", (spot[0] / h).toFloat(), (spot[1] / h).toFloat())
        shader.setFloatUniform("uSpotR", max(0.02, spot[2] / h * 0.75).toFloat())
        shader.setFloatUniform("uSpotI", spot[3].toFloat())
        // 遮罩位图每帧重画：BitmapShader 建好时就把位图做成了不可变的图，所以每帧重新挂一个，子着色器才拿到这一帧的内容
        shader.setInputShader("maskT", scaledShader(st.mask, w, h).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
        if (breathOn) shader.setInputShader("breathT", scaledShader(st.breath, w, h).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
    }
}

private val maskToAlpha = ColorMatrixColorFilter(
    floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        8f, 0f, 0f, 0f, -0.3f * 8f * 255f,
    ),
)

/** 水珠：透出清晰窗外的小圆，暗边，左上高光（光从左上照来，同 raindrop-fx compose 的 lightDir）。 */
private fun DrawScope.drawDrop(x: Float, y: Float, rx: Float, ry: Float, a: Float, sharp: ShaderBrush?) {
    if (a <= 0.01f || rx < 0.5f) return
    val tl = Offset(x - rx, y - ry)
    val sz = Size(rx * 2, ry * 2)
    if (sharp != null) drawOval(sharp, tl, sz, alpha = a)
    drawOval(Color(0xFF0B1018).copy(alpha = 0.32f * a), tl, sz)
    drawOval(Color(0xFF05070A).copy(alpha = 0.5f * a), tl, sz, style = Stroke(max(0.8f, rx * 0.22f)))
    drawOval(Color(0xFFBCC6D2).copy(alpha = 0.22f * a), Offset(x - rx * 0.62f, y + ry * 0.05f), Size(rx * 1.24f, ry * 0.86f), style = Stroke(max(0.6f, rx * 0.1f)))
    val hr = rx * 0.3f
    drawCircle(Color.White.copy(alpha = 0.85f * a), hr, Offset(x - rx * 0.36f, y - ry * 0.4f))
}

@Composable
internal fun NebbiaStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wi = constraints.maxWidth
        val hi = constraints.maxHeight
        val w = wi.toFloat()
        val h = hi.toFloat()
        val px = density.density.toDouble()
        val minPx = with(density) { 26.sp.toPx() }.toDouble()
        val typeface = remember(fontFamily) {
            runCatching { resolver.resolve(fontFamily, FontWeight.Bold).value as? Typeface }.getOrNull() ?: Typeface.DEFAULT_BOLD
        }
        val accentKey = accentColor.value
        val pal = remember(accentKey) {
            NebbiaCity.palette(doubleArrayOf(accentColor.red.toDouble(), accentColor.green.toDouble(), accentColor.blue.toDouble()))
        }
        val city by produceState<NebbiaCityImages?>(null, wi, hi, pal) {
            if (wi > 0 && hi > 0) {
                val t0 = System.nanoTime()
                Log.i(TAG, "city start ${wi}x$hi px=$px")
                value = runCatching { withContext(Dispatchers.Default) { buildCityImages(wi, hi, px, pal) } }
                    .onFailure { Log.e(TAG, "city failed", it) }.getOrNull()
                Log.i(TAG, "city done ${(System.nanoTime() - t0) / 1_000_000} ms ok=${value != null}")
            }
        }
        val laid by produceState<List<NebbiaLine>?>(null, lines, wi, hi, lyricsFontScale, typeface) {
            if (wi > 0 && hi > 0) {
                val t0 = System.nanoTime()
                value = runCatching {
                    withContext(Dispatchers.Default) {
                        val raster = NebbiaGlyphRaster(typeface)
                        var glyphNs = 0L
                        NebbiaSceneBuilder.build(NebbiaComposer.buildLines(lines), w.toDouble(), h.toDouble(), lyricsFontScale, minPx, px) {
                            val g0 = System.nanoTime(); val g = raster.get(it); glyphNs += System.nanoTime() - g0; g
                        }.also { Log.i(TAG, "glyphs ${glyphNs / 1_000_000} ms") }
                    }
                }.onFailure { Log.e(TAG, "scene failed", it) }.getOrNull()
                Log.i(TAG, "scene done ${(System.nanoTime() - t0) / 1_000_000} ms lines=${value?.size} glyphs=${value?.flatMap { it.chars }?.count { it.glyph != null }}")
            }
        }
        val frame = remember(wi, hi) { if (wi > 0 && hi > 0) NebbiaFrameState(wi, hi, px) else null }
        val main = remember(city, frame) {
            val c = city
            if (c != null && frame != null && latentBackgroundSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { NebbiaMainShader(c, frame, w, h) }.onFailure { Log.w(TAG, "AGSL 主画面编译失败，回落", it) }.getOrNull()
            } else {
                null
            }
        }
        val sharpBrush = remember(city) { city?.let { ShaderBrush(scaledShader(it.sharp, w, h)) } }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.readForDraw()
            val c = city
            val st = frame
            if (c == null || st == null) {
                drawRect(Color(0xFF05070A))
                return@Canvas
            }
            if (!st.logged) { st.logged = true; Log.i(TAG, "first frame t=$t shader=${main != null} laid=${laid?.size}") }
            val song = laid.orEmpty()
            st.ink.draw(song, t, px)
            drawMask(st)
            val breathOn = drawBreath(st, song, t)
            // 聚光灯：打向正在写的字，强度 0.15 秒亮起、0.5 秒熄灭（nebbiaSpot.ts，按真实时间平滑）
            val tgt = NebbiaSceneBuilder.spotTarget(song, t)
            val now = System.nanoTime()
            val dt = if (st.lastNanos == 0L) 0.0 else min(0.25, (now - st.lastNanos) / 1e9)
            st.lastNanos = now
            if (tgt != null) { st.spotX = tgt[0]; st.spotY = tgt[1]; st.spotR = tgt[2] }
            val on = if (tgt != null) 1.0 else 0.0
            st.spotI = if (st.spotI < 0 || dt == 0.0) on else st.spotI + (on - st.spotI) * (1 - exp(-dt / (if (tgt != null) 0.15 else 0.5)))
            val m = main
            if (m != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                m.update(t, breathOn, pal.la, doubleArrayOf(st.spotX, st.spotY, st.spotR, st.spotI * NebbiaFixed.SPOT), st, w, h)
                drawRect(m.brush)
            } else {
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    val dst = android.graphics.RectF(0f, 0f, w, h)
                    val filter = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                    nc.drawBitmap(c.fog, null, dst, filter)
                    val layer = nc.saveLayer(dst, null)
                    nc.drawBitmap(st.mask, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { colorFilter = maskToAlpha })
                    nc.drawBitmap(c.sharp, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
                    nc.restoreToCount(layer)
                }
            }
            // 雾上的凝结水珠（擦开的地方没有：这里不知道遮罩，按 Folia 也只是被擦痕盖住的少数几颗）
            for (d in c.field) drawDrop(d[0], d[1], d[2], d[3], 1f, sharpBrush)
            // 擦痕边、收笔、下淌的水珠：透明度低于 0.96 的在 Folia 里被阈值吃掉，这里收成平滑的一小段
            for (b in st.ink.beads) {
                val a = nSmooth(nClamp((b[3] - 0.86) / 0.14)).toFloat()
                val r = b[2].toFloat()
                drawDrop(b[0].toFloat(), b[1].toFloat(), r * b[4].toFloat(), r * b[5].toFloat(), a, sharpBrush)
            }
        }
    }
}
