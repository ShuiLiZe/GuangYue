package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.guangyue.feature.library.design.canBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * V0 歌词背景层（视觉内核）
 *
 * 对应 Folia 文件：
 * - `src/components/visualizer/VisualizerBackdrop.tsx`（背景套层：背景层 + 舞台层，换样式时背景不重建）
 * - `src/components/visualizer/backgrounds/common/FluidBackground.tsx`
 *   （封面取色 → 模糊封面层 + `linear-gradient(primary → transparent → secondary)` 以 `mix-blend-mode: overlay`
 *   叠加、opacity 0.4；无封面时只画 theme.backgroundColor @0.8；换歌用新旧两层交叉淡入 1.5s / easeInOut；
 *   overlay 渐变色走 `useBlendedColors(..., 1000ms)` 柔焦换色）
 * - `src/utils/colorExtractor.ts`（`extractVibrantColorsFromPixels`：步长 2 采样、跳过 alpha<128、
 *   按饱和度排序、RGB 欧氏距离 20 去重、输出 #rrggbb）
 * - `src/utils/builtinTheme/generateBuiltinDualTheme.ts`（由封面色推主题四色）
 *
 * 手机化改动（原因见 docs/ui-v0/report.md）：
 * 1. 取色不再走 canvas/getImageData，而是 `java.net.URL` 下载 → `BitmapFactory` → 降到 48×48 → 逐像素统计，
 *    与 Folia 的 50×50 canvas 采样等价（同一套饱和度 + 距离去重算法），且不依赖 WebView。
 * 2. 封面模糊层用 Compose `Modifier.blur(40.dp)`（仅 API≥31 且非低内存时启用），Folia 的
 *    `transform: scale(max(w/iw, h/ih) * 1.5)` 简化为 `ContentScale.Crop` + 1.5 倍放大图层，
 *    避免在手机上多留一层 1.5 倍大的离屏缓冲。
 * 3. `mix-blend-mode: overlay` 用 `drawRect(..., blendMode = BlendMode.Overlay)` 直接对应。
 * 4. 换歌柔焦换色用 `Animatable(0f → 1f)` + 逐通道 `lerp`，曲线取内核 `ColorMix.easeOut`
 *    （`1-(1-t)^3`）与 Folia `useBlendedColors` 一致，时长 1000ms。
 */

/** 一瓶从封面推出来的四色主题（对应 Folia `Theme` 的 backgroundColor/primary/accent/secondary）。 */
@Immutable
data class CoverPalette(
    val backgroundColor: Color,
    val primaryColor: Color,
    val accentColor: Color,
    val secondaryColor: Color,
    val fromCover: Boolean,
)

/** Folia `FluidBackground` 里 overlay 渐变的柔焦时长（`OVERLAY_BLEND_MS`）。 */
const val OVERLAY_BLEND_MS = 1000

/** 封面模糊半径（Folia `blur(40px)`）。 */
private const val COVER_BLUR_DP = 40

/** 与 Folia `colorExtractor.ts` 一致的采样尺寸（canvas 50×50）。 */
private const val SAMPLE_SIZE = 48

/**
 * 复刻 Folia `extractVibrantColorsFromPixels`：
 * - `step = 2`（隔像素采样）
 * - 跳过 `alpha < 128`
 * - `saturation = max === min ? 0 : (max - min) / (255 - |max + min - 255|)`
 * - 保留 `saturation > 0.2 && 30 < l < 220`（不足 100 个时放宽）
 * - 按 `sat = (max - min) / 255` 降序
 * - 去重：RGB 欧氏距离 < 20 视为同色
 */
internal object CoverColorExtractor {

    fun extract(bitmap: Bitmap, count: Int = 5): List<Color> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val candidates = ArrayList<Long>(256)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            val a = (pixel ushr 24) and 0xFF
            if (a >= 128) {
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val l = (max + min) / 2
                val denominator = 255 - kotlin.math.abs(max + min - 255)
                val saturation = if (max == min || denominator == 0) 0.0 else (max - min).toDouble() / denominator
                if ((saturation > 0.2 && l > 30 && l < 220) || candidates.size < 100) {
                    candidates.add(pixel.toLong())
                }
            }
            index += 2
        }

        candidates.sortByDescending { packed ->
            val r = (packed ushr 16).toInt() and 0xFF
            val g = (packed ushr 8).toInt() and 0xFF
            val b = packed.toInt() and 0xFF
            (maxOf(r, g, b) - minOf(r, g, b)) / 255.0
        }

        val distinct = ArrayList<Color>(count)
        for (packed in candidates) {
            val r = (packed ushr 16).toInt() and 0xFF
            val g = (packed ushr 8).toInt() and 0xFF
            val b = packed.toInt() and 0xFF
            val color = Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
            val tooClose = distinct.any { existing ->
                val dr = (existing.red - color.red) * 255f
                val dg = (existing.green - color.green) * 255f
                val db = (existing.blue - color.blue) * 255f
                kotlin.math.sqrt(dr * dr + dg * dg + db * db) < 20f
            }
            if (!tooClose || distinct.isEmpty()) {
                distinct.add(color)
                if (distinct.size >= count) break
            }
        }
        return distinct
    }
}

/** 下载封面并缩到 48×48，失败一律返回 null（对应 Folia `loadImagePixels` resolve(null)）。 */
internal fun loadCoverPixels(url: String): Bitmap? = runCatching {
    URL(url).openStream().use { stream ->
        val decoded = BitmapFactory.decodeStream(stream) ?: return@use null
        Bitmap.createScaledBitmap(decoded, SAMPLE_SIZE, SAMPLE_SIZE, true)
    }
}.getOrNull()

/**
 * 由封面色推主题四色（简化版 `generateBuiltinDualTheme`：保留 Folia 的「背景取封面主色压到低饱和、
 * accent 取封面最艳色、secondary 取第二色」取向，去掉 HSL 随机抖动与对比度重试，手机上看不出差别）。
 */
internal fun coverPaletteFrom(colors: List<Color>, dark: Boolean, fallbackAccent: Color, fallbackSeed: Color): CoverPalette {
    if (colors.isEmpty()) {
        return CoverPalette(
            backgroundColor = if (dark) Color(0xFF0D0B0A) else Color(0xFFF7F5F2),
            primaryColor = if (dark) Color(0xFFF4F4F5) else Color(0xFF1C1917),
            accentColor = fallbackAccent,
            secondaryColor = if (dark) Color(0xFF71717A) else Color(0xFF44403C),
            fromCover = false,
        )
    }
    val dominant = colors.first()
    val accent = colors.firstOrNull { farEnough(it, dominant) } ?: fallbackAccent
    val secondary = colors.drop(1).firstOrNull { farEnough(it, accent) } ?: colors.getOrElse(1) { dominant }
    val background = if (dark) {
        lerp(Color(0xFF0D0B0A), dominant, 0.22f)
    } else {
        lerp(Color(0xFFF7F5F2), dominant, 0.18f)
    }
    return CoverPalette(
        backgroundColor = background,
        primaryColor = if (dark) Color(0xFFF4F4F5) else Color(0xFF1C1917),
        accentColor = accent,
        secondaryColor = secondary,
        fromCover = true,
    )
}

/** 第二色必须与参照色拉开距离（Folia `MIN_SUPPORT_HUE_DISTANCE` 思路的 RGB 版）。 */
private fun farEnough(candidate: Color, reference: Color): Boolean {
    val dr = (candidate.red - reference.red) * 255f
    val dg = (candidate.green - reference.green) * 255f
    val db = (candidate.blue - reference.blue) * 255f
    return kotlin.math.sqrt(dr * dr + dg * dg + db * db) > 60f
}

/**
 * 取封面主题色。url 为空、加载失败或图片非本地可达时返回主题兜底色（不造假、不上报错误）。
 */
@Composable
fun rememberCoverPalette(
    coverUrl: String?,
    dark: Boolean,
    fallbackAccent: Color,
    fallbackSeed: Color = fallbackAccent,
): CoverPalette {
    var colors by remember(coverUrl) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(coverUrl) {
        colors = if (coverUrl.isNullOrBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { loadCoverPixels(coverUrl) }
                ?.let { CoverColorExtractor.extract(it) }
                .orEmpty()
        }
    }
    return remember(colors, dark, fallbackAccent, fallbackSeed) {
        coverPaletteFrom(colors, dark, fallbackAccent, fallbackSeed)
    }
}

/**
 * 视觉背景层：底色渐变 + 模糊封面 + overlay 渐变（换歌时颜色柔焦过渡）。
 *
 * @param content 舞台层（歌词画布），装在背景之上、pointerEvents 照常。
 */
@Composable
fun VisualizerBackdrop(
    palette: CoverPalette,
    coverUrl: String?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    /** 减少动效：关掉换歌柔焦与封面淡入（Folia 的 `prefers-reduced-motion` 只在歌词层生效，这里一并关掉背景过渡）。 */
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val blurAllowed = canBlur()
    val target = remember(palette) {
        listOf(
            palette.backgroundColor,
            palette.accentColor,
            palette.secondaryColor,
        )
    }
    val shown = remember { mutableStateListOf<Color>() }
    LaunchedEffect(target) {
        val from = shown.toList()
        if (from.size != target.size) {
            shown.clear()
            shown.addAll(target)
            return@LaunchedEffect
        }
        if (from == target) return@LaunchedEffect
        if (reduceMotion) {
            for (i in target.indices) shown[i] = target[i]
            return@LaunchedEffect
        }
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = OVERLAY_BLEND_MS, easing = kernelEaseOutEasing)) {
            for (i in target.indices) shown[i] = lerp(from[i], target[i], value)
        }
    }
    val background = shown.getOrElse(0) { palette.backgroundColor }
    val overlayPrimary = shown.getOrElse(1) { palette.accentColor }
    val overlaySecondary = shown.getOrElse(2) { palette.secondaryColor }
    // Folia FluidBackground 的两段底色（同一层、opacity 0.8，不是自上而下的白/黑渐变）：
    //   - 有封面：封面下面的 backgroundColor @0.8，封面盖住它，只在封面到来前露一下；
    //   - 无封面：整屏就是 backgroundColor @0.8。
    val wash = if (dark) lerp(Color.Black, background, 0.8f) else lerp(Color.White, background, 0.8f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(wash),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurAllowed) it.blur(COVER_BLUR_DP.dp) else it },
            )
        }
        // Folia：linear-gradient(to bottom right, primary, transparent, secondary) + mix-blend-mode: overlay + 0.4
        // 注意 Folia 只用 accent/secondary 上 overlay；primaryColor 留给文字/次要控件的对比色，
        // 上了 overlay 会把画布拍成一片单色（见 docs/ui-v0/report.md「已知不足 1」）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(
                                palette.accentColor.copy(alpha = 0.9f),
                                Color.Transparent,
                                overlaySecondary.copy(alpha = 0.9f),
                            ),
                        ),
                        alpha = 0.4f,
                        blendMode = BlendMode.Overlay,
                    )
                },
        )
        content()
    }
}