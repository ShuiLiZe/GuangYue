package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// 对应 Folia 文件：
//   src/components/visualizer/backgrounds/latent/LatentBackground.tsx（Folia 默认背景「隐现」：下层 MeshGradient 流体、
//     上层 Dithering 像素点阵 soft-light 叠加、再盖一层底色 overlay）
//   src/types.ts DEFAULT_LATENT_BACKGROUND_TUNING（displayMode both、ditheringSpeed 0.1、ditheringSize 2.5、
//     ditheringOpacity 0.55、meshSpeed 0.3、meshDistortion 0.8、meshSwirl 0.1、overlayOpacity 0.35）
//   @paper-design/shaders 0.0.80 dist/shaders/mesh-gradient.js、dithering.js、vertex-shader.js（GLSL 原样对译 AGSL）
//   src/utils/colorPalette.ts extractRepresentativeColorsFromPixels（50×50 采样、4bit 量化直方图、加权中位切分）
//
// 手机化：
//  1. WebGL → AGSL RuntimeShader（API 33+）；更低版本与 Robolectric 回落到「通用」背景（流体 + 几何）。
//  2. 没有音频频段：音频驱动的 speed / u_pxSize / u_distortion / 缩放 / 滤镜都取「静音」值
//     （smoothed* = 0 时 Folia 的取值），播放 / 暂停之间按 PAUSE_EASE_MS 300 指数过渡、暂停速度 ×0.12，同 Folia。
//  3. paper 画布像素比：minPixelRatio 1、maxPixelCount 1280×720，与 Folia 一样按 dp（= CSS px）面积封顶，
//     着色器在这块「虚拟画布」坐标里算（gl_FragCoord 原点在左下，y 翻转）。

/** Folia DEFAULT_LATENT_BACKGROUND_TUNING。 */
internal object LatentTuning {
    const val DITHERING_SPEED = 0.1f
    const val DITHERING_SIZE = 2.5f
    const val DITHERING_OPACITY = 0.55f
    const val MESH_SPEED = 0.3f
    const val MESH_DISTORTION = 0.8f
    const val MESH_SWIRL = 0.1f
    const val OVERLAY_OPACITY = 0.35f
    /** paper Dithering 默认 preset 的 scale（Folia 没传 scale）。 */
    const val DITHERING_SCALE = 0.6f
    const val PAUSED_SPEED_SCALE = 0.12f
    const val PAUSE_EASE_MS = 300f
    const val MAX_SHADER_PIXELS = 1280f * 720f
}

/** 能否画「隐现」（AGSL 需要 API 33；Robolectric 截图不跑着色器）。 */
// 只认真 Robolectric：取证页（V0cStageHarness）会把 runningUnderRobolectricOverride 置 true 来定格歌词时钟，
// 但它跑在真模拟器上，着色器照样能画。
val latentBackgroundSupported: Boolean by lazy {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        runCatching { Class.forName("org.robolectric.Robolectric") }.isFailure
}

/**
 * Folia `resolveLatentShaderColors(coverColors, theme, 'cover-theme')`。
 * 返回 [ditheringBack, ditheringFront, mesh0..mesh5]。
 */
internal fun latentShaderColors(cover: List<Color>, palette: CoverPalette): List<Color> {
    val primary = cover.getOrNull(0) ?: palette.secondaryColor
    val secondary = cover.getOrNull(1) ?: palette.primaryColor
    val tertiary = cover.getOrNull(2) ?: primary
    val quaternary = cover.getOrNull(3) ?: secondary
    return listOf(
        palette.backgroundColor,
        primary,
        primary, secondary, tertiary, quaternary, palette.backgroundColor, palette.accentColor,
    )
}

/** Folia colorPalette.ts `extractRepresentativeColorsFromPixels`（加权中位切分，按像素量降序）。 */
internal fun extractRepresentativeColors(bitmap: Bitmap, count: Int = 6): List<Color> {
    if (count <= 0) return emptyList()
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    data class Wc(var r: Long, var g: Long, var b: Long, var weight: Int)
    val hist = HashMap<Int, Wc>()
    for (p in px) {
        if (((p ushr 24) and 0xFF) < 128) continue
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        val e = hist[key]
        if (e != null) { e.r += r; e.g += g; e.b += b; e.weight += 1 } else hist[key] = Wc(r.toLong(), g.toLong(), b.toLong(), 1)
    }
    data class C(val r: Int, val g: Int, val b: Int, val weight: Int)
    val colors = hist.values.map {
        C(Math.round(it.r.toDouble() / it.weight).toInt(), Math.round(it.g.toDouble() / it.weight).toInt(), Math.round(it.b.toDouble() / it.weight).toInt(), it.weight)
    }
    if (colors.isEmpty()) return emptyList()
    fun range(cs: List<C>, ch: Int): Int {
        var lo = 255; var hi = 0
        for (c in cs) { val v = when (ch) { 0 -> c.r; 1 -> c.g; else -> c.b }; lo = min(lo, v); hi = max(hi, v) }
        return hi - lo
    }
    data class Bucket(val colors: List<C>, val weight: Int)
    val buckets = mutableListOf(Bucket(colors, colors.sumOf { it.weight }))
    while (buckets.size < min(count, colors.size)) {
        var bi = -1
        var best = -1.0
        buckets.forEachIndexed { i, bk ->
            if (bk.colors.size < 2) return@forEachIndexed
            val widest = maxOf(range(bk.colors, 0), range(bk.colors, 1), range(bk.colors, 2))
            val score = widest * sqrt(bk.weight.toDouble())
            if (score > best) { best = score; bi = i }
        }
        if (bi < 0) break
        val bk = buckets[bi]
        val rs = intArrayOf(range(bk.colors, 0), range(bk.colors, 1), range(bk.colors, 2))
        // reduce 从 'r' 起，严格大于才换（同 Folia）。
        var ch = 0
        if (rs[1] > rs[ch]) ch = 1
        if (rs[2] > rs[ch]) ch = 2
        val sorted = bk.colors.sortedBy { when (ch) { 0 -> it.r; 1 -> it.g; else -> it.b } }
        val mid = bk.weight / 2.0
        var acc = 0
        var split = 1
        for (i in 0 until sorted.size - 1) {
            acc += sorted[i].weight
            split = i + 1
            if (acc >= mid) break
        }
        val left = sorted.subList(0, split)
        val right = sorted.subList(split, sorted.size)
        buckets.removeAt(bi)
        buckets.add(bi, Bucket(right, right.sumOf { it.weight }))
        buckets.add(bi, Bucket(left, left.sumOf { it.weight }))
    }
    return buckets.map { bk ->
        var r = 0.0; var g = 0.0; var b = 0.0
        for (c in bk.colors) { r += c.r * c.weight; g += c.g * c.weight; b += c.b * c.weight }
        Triple(Math.round(r / bk.weight).toInt(), Math.round(g / bk.weight).toInt(), Math.round(b / bk.weight).toInt()) to bk.weight
    }.sortedByDescending { it.second }.map { (rgb, _) -> Color(rgb.first / 255f, rgb.second / 255f, rgb.third / 255f) }
}

/**
 * 「隐现」背景层：底色 + MeshGradient（scale 1.025，saturate 1.04 / brightness 0.94）+ Dithering
 * （warp、4×4 Bayer、像素 2.5，soft-light 0.55，scale 1.015）+ 底色 overlay 0.35。
 */
@Composable
fun LatentBackgroundLayer(
    coverUrl: String?,
    palette: CoverPalette,
    paused: Boolean,
    staticMode: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    LatentBackgroundImpl(coverUrl, palette, paused, staticMode, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LatentBackgroundImpl(
    coverUrl: String?,
    palette: CoverPalette,
    paused: Boolean,
    staticMode: Boolean,
    modifier: Modifier,
) {
    var cover by remember(coverUrl) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(coverUrl) {
        cover = if (coverUrl.isNullOrBlank()) emptyList() else withContext(Dispatchers.IO) {
            runCatching {
                java.net.URL(coverUrl).openStream().use { s ->
                    val d = android.graphics.BitmapFactory.decodeStream(s) ?: return@use null
                    Bitmap.createScaledBitmap(d, 50, 50, true)
                }
            }.getOrNull()?.let { extractRepresentativeColors(it, 6) }.orEmpty()
        }
    }
    val target = latentShaderColors(cover, palette)
    // 换歌柔焦换色（useBlendedColors）。
    val blended = target.mapIndexed { i, c -> animateColorAsState(c, tween(1000), label = "latent$i").value }

    val mesh = remember { RuntimeShader(MESH_AGSL) }
    val dither = remember { RuntimeShader(DITHER_AGSL) }
    val meshBrush = remember(mesh) { ShaderBrush(mesh) }
    val ditherBrush = remember(dither) { ShaderBrush(dither) }

    // u_time（秒）：paper ShaderMount 按 speed 累加帧时间。
    var meshTime by remember { mutableFloatStateOf(0f) }
    var ditherTime by remember { mutableFloatStateOf(0f) }
    val pausedNow by rememberUpdatedState(paused)
    LaunchedEffect(staticMode) {
        if (staticMode) return@LaunchedEffect
        var last = 0L
        var playing = if (pausedNow) 0f else 1f
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) {
                    val dtMs = ((n - last) / 1e6f).coerceAtMost(100f)
                    val step = 1f - exp(-dtMs / LatentTuning.PAUSE_EASE_MS)
                    playing += ((if (pausedNow) 0f else 1f) - playing) * step
                    fun speed(base: Float) = base * LatentTuning.PAUSED_SPEED_SCALE + (base - base * LatentTuning.PAUSED_SPEED_SCALE) * playing
                    meshTime += dtMs * speed(LatentTuning.MESH_SPEED) / 1000f
                    ditherTime += dtMs * speed(LatentTuning.DITHERING_SPEED) / 1000f
                }
                last = n
            }
        }
    }

    val density = LocalDensity.current.density
    val meshFilter = remember {
        val m = ColorMatrix().apply { setToSaturation(1.04f) }
        m.timesAssign(ColorMatrix(floatArrayOf(0.94f, 0f, 0f, 0f, 0f, 0f, 0.94f, 0f, 0f, 0f, 0f, 0f, 0.94f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        ColorFilter.colorMatrix(m)
    }

    Canvas(modifier.fillMaxSize()) {
        val wDp = size.width / density
        val hDp = size.height / density
        if (wDp <= 0f || hDp <= 0f) return@Canvas
        // paper：pixelRatio = clamp(devicePixelRatio, 1, sqrt(maxPixelCount / cssArea))。
        val pr = max(1f, min(density, sqrt(LatentTuning.MAX_SHADER_PIXELS / (wDp * hDp))))
        val k = pr / density
        val resW = (wDp * pr).roundToInt().toFloat()
        val resH = (hDp * pr).roundToInt().toFloat()

        drawRect(blended[0])

        mesh.setFloatUniform("u_res", resW, resH)
        mesh.setFloatUniform("u_k", k)
        mesh.setFloatUniform("u_time", meshTime)
        mesh.setFloatUniform("u_distortion", LatentTuning.MESH_DISTORTION)
        mesh.setFloatUniform("u_swirl", LatentTuning.MESH_SWIRL)
        val arr = FloatArray(24)
        for (i in 0 until 6) {
            val c = blended[2 + i]
            arr[i * 4] = c.red; arr[i * 4 + 1] = c.green; arr[i * 4 + 2] = c.blue; arr[i * 4 + 3] = c.alpha
        }
        mesh.setFloatUniform("u_colors", arr)
        scale(1.025f) {
            drawRect(meshBrush, colorFilter = meshFilter)
        }

        dither.setFloatUniform("u_res", resW, resH)
        dither.setFloatUniform("u_k", k)
        dither.setFloatUniform("u_time", ditherTime)
        dither.setFloatUniform("u_pr", pr)
        dither.setFloatUniform("u_pxSize", LatentTuning.DITHERING_SIZE)
        dither.setFloatUniform("u_scale", LatentTuning.DITHERING_SCALE)
        val back = blended[0]
        val front = blended[1]
        dither.setFloatUniform("u_colorBack", back.red, back.green, back.blue, back.alpha)
        dither.setFloatUniform("u_colorFront", front.red, front.green, front.blue, front.alpha)
        scale(1.015f) {
            drawRect(ditherBrush, alpha = LatentTuning.DITHERING_OPACITY, blendMode = BlendMode.Softlight)
        }

        drawRect(blended[0], alpha = LatentTuning.OVERLAY_OPACITY)
    }
}

// paper MeshGradient（fit contain、scale 1：v_objectUV = GL 裁剪坐标 ×0.5 × res/min(res)）。
private const val MESH_AGSL = """
uniform float2 u_res;
uniform float u_k;
uniform float u_time;
uniform float4 u_colors[6];
uniform float u_distortion;
uniform float u_swirl;

float2 rot(float2 uv, float th) { return float2x2(cos(th), sin(th), -sin(th), cos(th)) * uv; }

float4 main(float2 fragCoord) {
  float2 fc = fragCoord * u_k;
  float2 gl = float2(fc.x, u_res.y - fc.y);
  float2 uv = gl / u_res - 0.5;
  uv *= u_res / min(u_res.x, u_res.y);
  uv += 0.5;

  float t = 0.5 * (u_time + 41.5);
  float radius = smoothstep(0.0, 1.0, length(uv - 0.5));
  float center = 1.0 - radius;
  for (int k = 1; k <= 2; k++) {
    float i = float(k);
    uv.x += u_distortion * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, uv.y)) * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, uv.y));
    uv.y += u_distortion * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, uv.x));
  }
  float2 ur = uv - 0.5;
  ur = rot(ur, -3.0 * u_swirl * radius);
  ur += 0.5;

  float3 color = float3(0.0);
  float opacity = 0.0;
  float total = 0.0;
  for (int i = 0; i < 6; i++) {
    float a = float(i) * 0.37;
    float b = 0.6 + fract(float(i) / 3.0) * 0.9;
    float c = 0.8 + fract(float(i + 1) / 4.0);
    float2 pos = 0.5 + 0.5 * float2(sin(t * b + a), cos(t * c + a * 1.5));
    float d = pow(length(ur - pos), 3.5);
    float w = 1.0 / (d + 0.001);
    color += u_colors[i].rgb * u_colors[i].a * w;
    opacity += u_colors[i].a * w;
    total += w;
  }
  color /= max(0.0001, total);
  opacity = clamp(opacity / max(0.0001, total), 0.0, 1.0);
  return float4(color, opacity);
}
"""

// paper Dithering：shape warp、type 4×4、pattern sizing（fit none、scale 0.6）。
private const val DITHER_AGSL = """
uniform float2 u_res;
uniform float u_k;
uniform float u_time;
uniform float u_pr;
uniform float u_pxSize;
uniform float u_scale;
uniform float4 u_colorBack;
uniform float4 u_colorFront;

float bayer2(float x, float y) {
  if (x < 0.5) { return y < 0.5 ? 0.0 : 3.0; }
  return y < 0.5 ? 2.0 : 1.0;
}

float dithAt(float2 pxUV) {
  float2 p = floor(fract(pxUV / 4.0) * 4.0);
  float2 lo = mod(p, 2.0);
  float2 hi = floor(p / 2.0);
  return (4.0 * bayer2(lo.x, lo.y) + bayer2(hi.x, hi.y)) / 16.0 - 0.5;
}

// 浏览器里 paper 的 WebGL 画布只有 pr 倍（手机上约 1.6），再被页面按 devicePixelRatio（约 2.75）
// 双线性拉伸到屏幕上，所以 Folia 的点阵边缘是柔的。这里把那一步也做了：在「画布像素」网格上取
// 四个相邻像素中心各算一次点阵，再按片元在格子里的位置双线性混合。
// 形状场（warp）在格子之间变化很慢，四个采样共用片元所在格子的那一份，只多算四次查表。
float4 main(float2 fragCoord) {
  float2 fc = fragCoord * u_k;
  float t = 0.5 * u_time;
  float pxSize = u_pxSize * u_pr;

  float2 glc = float2(fc.x, u_res.y - fc.y);
  float2 cpUV = (floor((glc - 0.5 * u_res) / pxSize) + 0.5) * pxSize;
  float2 shapeUV = cpUV / u_pr / u_scale + 0.5;
  shapeUV *= 0.003;
  for (int k = 1; k < 6; k++) {
    float i = float(k);
    shapeUV.x += 0.6 / i * cos(i * 2.5 * shapeUV.y + t);
    shapeUV.y += 0.6 / i * cos(i * 1.5 * shapeUV.x + t);
  }
  float shape = 0.15 / max(0.001, abs(sin(t - shapeUV.y - shapeUV.x)));
  shape = smoothstep(0.02, 1.0, shape);

  float2 base = floor(fc - 0.5) + 0.5;
  float2 f = fc - 0.5 - floor(fc - 0.5);
  float r00 = step(0.5, shape + dithAt((float2(base.x, u_res.y - base.y) - 0.5 * u_res) / pxSize));
  float r10 = step(0.5, shape + dithAt((float2(base.x + 1.0, u_res.y - base.y) - 0.5 * u_res) / pxSize));
  float r01 = step(0.5, shape + dithAt((float2(base.x, u_res.y - base.y - 1.0) - 0.5 * u_res) / pxSize));
  float r11 = step(0.5, shape + dithAt((float2(base.x + 1.0, u_res.y - base.y - 1.0) - 0.5 * u_res) / pxSize));
  float res = mix(mix(r00, r10, f.x), mix(r01, r11, f.x), f.y);

  float3 fg = u_colorFront.rgb * u_colorFront.a;
  float3 bg = u_colorBack.rgb * u_colorBack.a;
  float3 color = fg * res;
  float opacity = u_colorFront.a * res;
  color += bg * (1.0 - opacity);
  opacity += u_colorBack.a * (1.0 - opacity);
  return float4(color, opacity);
}
"""
