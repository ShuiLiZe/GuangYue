// 凝彩（Folia tempera）场景后处理：照 temperaSceneBuilder.applyTemperaScenePostProcess + sonnetPostProcess.createSonnetPostProcessFilters
// 的默认链逐个对译——镜头畸变（sonnetLensFilter，distortion = postProcessLensDistortion 0.3，dispersion 0）→ 颗粒（Pixi NoiseFilter，
// noise = postProcessGrain 0.2 × 0.35，seed = (场景种子 % 10000) / 10000）→ 对比度（默认 0，跳过）→ 印刷 RGB 错位（默认 0，跳过）→
// 暗角（sonnetPrintFilters vignette，0.85）。Folia 把这条链挂在每个段落 scene 容器上（filterArea = 视口），所以这里也是每个场景一层。
// 转场模糊（temperaSceneFilters.setTemperaTransitionBlur）：只在 shape-carry 转场里 blur > 0.01 时挂在链尾，repeatEdgePixels → CLAMP。
// 手机化：
//   · 三个 GLSL pass 合成一个 AGSL（RuntimeShader，API 33+），顺序与 Pixi 链相同；API 31–32 只有转场模糊；更低版本退回原来的径向渐变暗角。
//   · 颗粒的 gl_FragCoord 在 Pixi 里是 1.5× 画布像素，这里是设备像素（同样逐像素、同样静态，颗粒密度略不同）。
//   · 模糊半径换算：Pixi BlurFilter（kernel 5、quality 1）抽头在 0、±s、±2s 纹素，权重 0.2503/0.2215/0.1534 → σ ≈ 1.29 s 纹素；
//     纹理分辨率是画布的一半（0.75），一纹素 = 1/0.75 CSS 像素 → σ ≈ 1.72 s dp。Android createBlurEffect 的半径按 σ 传给 Skia。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Folia DEFAULT_TEMPERA_TUNING 的后处理默认值。 */
internal object TemperaPostDefaults {
    const val LENS_DISTORTION = 0.3
    const val GRAIN = 0.2
    const val VIGNETTE = 0.85
    /** createSonnetPostProcessFilters：noise = postProcessGrain × 0.35。 */
    const val NOISE = GRAIN * 0.35
}

/** sonnetLensFilter：屏幕 uv（0..1）→ 采样 uv。 */
internal fun temperaLensUv(u: Double, v: Double, aspect: Double, distortion: Double): Pair<Double, Double> {
    var cx = (u - 0.5) * aspect
    val cy = v - 0.5
    val r2 = cx * cx + cy * cy
    val curvature = distortion * 0.32
    val radialScale = 1.0 - curvature * r2 + curvature * 0.16 * r2 * r2
    cx = cx * radialScale / aspect
    return (cx + 0.5) to (cy * radialScale + 0.5)
}

/** sonnetPrintFilters vignette：向不透明黑混合的比例。 */
internal fun temperaVignetteMix(u: Double, v: Double, aspect: Double, amount: Double): Double {
    val cx = (u - 0.5) * aspect
    val cy = v - 0.5
    val r = sqrt(cx * cx + cy * cy)
    val s = ((r - 0.52) / (1.08 - 0.52)).coerceIn(0.0, 1.0)
    return (s * s * (3 - 2 * s) * amount * 0.6).coerceIn(0.0, 1.0)
}

/** Pixi NoiseFilter 的 rand（fract(sin(dot(co, (12.9898, 78.233))) × 43758.5453)）。 */
internal fun temperaNoiseRand(x: Double, y: Double): Double {
    val s = sin(x * 12.9898 + y * 78.233) * 43758.5453
    return s - floor(s)
}

/** Pixi NoiseFilter 的 seed：(hashTemperaSeed % 10000) / 10000（hash 是无符号 32 位）。 */
internal fun temperaNoiseSeed(sceneHash: Long): Double = ((sceneHash and 0xFFFFFFFFL) % 10_000) / 10_000.0

/** Pixi BlurFilter strength（CSS px，半分辨率纹理）→ Android 模糊 σ（设备像素）。 */
internal fun temperaTransitionBlurSigma(strength: Double, density: Double): Double = strength * 1.29 / 0.75 * density

internal const val TEMPERA_POST_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uDistortion;
uniform float uNoise;
uniform float uSeed;
uniform float uVignette;

float rand(float2 co) { return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453); }

half4 main(float2 p) {
    float2 uv = p / uSize;
    float aspect = uSize.x / max(uSize.y, 1.0);
    // 镜头畸变（sonnetLensFilter，dispersion = 0 时只剩 center 采样）。
    float2 c = uv - 0.5;
    c.x *= aspect;
    float r2 = dot(c, c);
    float curvature = uDistortion * 0.32;
    float radialScale = 1.0 - curvature * r2 + curvature * 0.16 * r2 * r2;
    float2 lc = c * radialScale;
    lc.x /= aspect;
    float2 luv = lc + 0.5;
    half4 col = half4(0.0);
    if (luv.x >= 0.0 && luv.y >= 0.0 && luv.x <= 1.0 && luv.y <= 1.0) col = content.eval(luv * uSize);
    // 颗粒（Pixi NoiseFilter：去预乘 → rgb += diff → 再预乘）。
    if (uNoise > 0.0) {
        float diff = (rand(p * uSeed) - 0.5) * uNoise;
        if (col.a > 0.0) {
            float3 rgb = float3(col.rgb) / float(col.a) + diff;
            col.rgb = half3(clamp(rgb, 0.0, 1.0) * float(col.a));
        }
    }
    // 暗角（屏幕空间，向不透明黑混合）。
    float2 v = uv - 0.5;
    v.x *= aspect;
    float vig = clamp(smoothstep(0.52, 1.08, length(v)) * uVignette * 0.6, 0.0, 1.0);
    return mix(col, half4(0.0, 0.0, 0.0, 1.0), half(vig));
}
"""

/** API 33+ 才有整条链；否则调用方自己画渐变暗角。 */
internal fun temperaSupportsPostShader() = Build.VERSION.SDK_INT >= 33

/**
 * 一个场景槽（当前段 / 预卷的下一段）的后处理。RenderEffect 只在尺寸、种子或模糊档位变了时重建：
 * 平时每帧零分配；shape-carry 转场里模糊半径按 0.25 σ 量化，约一秒十几次。
 */
internal const val BLUR_SIGMA_CAP_PX = 24.0

internal class TemperaPostSlot {
    private val shader: RuntimeShader? = if (temperaSupportsPostShader()) runCatching { RuntimeShader(TEMPERA_POST_AGSL) }
        .onFailure { android.util.Log.e("TemperaStage", "post shader", it) }.getOrNull() else null
    private var key: String? = null
    private var effect: RenderEffect? = null

    fun effect(widthPx: Float, heightPx: Float, seed: Double, blurSigma: Double, post: Boolean): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31) return null
        // 模糊 σ 封顶 BLUR_SIGMA_CAP_PX、按整像素量化：CI 模拟器（软件 GL）在 t=37.5 的 shape-carry 转场里
        // 大 σ 模糊叠两层全屏着色器，RenderThread 一帧超 5 s → 主线程卡在 HardwareRenderer.setStopped 报 ANR。
        val blurQ = if (blurSigma > 0.5) blurSigma.coerceAtMost(BLUR_SIGMA_CAP_PX).toInt().toFloat() else 0f
        val k = "$widthPx|$heightPx|$seed|$blurQ|$post"
        if (k == key) return effect
        key = k
        effect = runCatching { build(widthPx, heightPx, seed, blurQ, post) }
            .onFailure { android.util.Log.e("TemperaStage", "post effect", it) }.getOrNull()
        return effect
    }

    private fun build(widthPx: Float, heightPx: Float, seed: Double, blurQ: Float, post: Boolean): RenderEffect? {
        var native: android.graphics.RenderEffect? = null
        if (post && shader != null && Build.VERSION.SDK_INT >= 33) {
            shader.setFloatUniform("uSize", widthPx, heightPx)
            shader.setFloatUniform("uDistortion", TemperaPostDefaults.LENS_DISTORTION.toFloat())
            shader.setFloatUniform("uNoise", TemperaPostDefaults.NOISE.toFloat())
            shader.setFloatUniform("uSeed", seed.toFloat())
            shader.setFloatUniform("uVignette", TemperaPostDefaults.VIGNETTE.toFloat())
            native = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        if (blurQ > 0f) {
            native = if (native != null) android.graphics.RenderEffect.createBlurEffect(blurQ, blurQ, native, Shader.TileMode.CLAMP)
            else android.graphics.RenderEffect.createBlurEffect(blurQ, blurQ, Shader.TileMode.CLAMP)
        }
        return native?.asComposeRenderEffect()
    }
}
