// 对应 Folia 文件：
//   src/components/visualizer/VisualizerBackdrop.tsx（取色后的背景层 + 封面层 + 渐变叠加）
//   src/components/visualizer/backgrounds/common/FluidBackground.tsx（流体静态烘焙 + 渐变叠加）
//   src/utils/colorExtractor.ts（封面像素 → 候选色）
//   src/utils/builtinTheme/*（色相 / 明度 / 对比度，已纯 Kotlin 化到 core:lyrics CoverTheme.kt）
//
// 手机化要点：
//  1. 取色只认三件事：本地 file:// / 资源路径、data:、http(s)。取不到像素就退回 Folia 的兜底
//     色（BuiltinDualTheme.fallback），不画空白背景。
//  2. Folia 的流体背景是「封面放大 1.5 倍 + 40px 高斯模糊 + 三段 bottom-right 渐变 overlay」。
//     Web 端用 canvas 烘焙，Android 用同一个模型：封面层 blur(40dp) 叠渐变，静态即等同烘焙结果。
//  3. 封面图来源：[CoverSource] —— 业务封面（artworkUri）优先；播放页没有封面时用内置测试封面，
//     保证「取色流体背景」这一能力在真机上可见、可截图取证。
package com.guangyue.feature.player.lyrics.visualizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.guangyue.core.lyrics.BuiltinDualTheme
import com.guangyue.core.lyrics.CoverPaletteAnalyzer
import com.guangyue.core.lyrics.LyricTheme
import com.guangyue.core.lyrics.Rgb
import com.guangyue.feature.player.R
import java.io.File
import java.io.InputStream
import java.net.URL

/** 封面来源：业务封面 or 内置测试封面。 */
sealed interface CoverSource {
    /** 播放器给的封面 URL（http(s) / file:// / content:// / data:）。 */
    data class Url(val value: String) : CoverSource

    /** 内置测试封面（打包在 feature:player 的 drawable-nodpi 里）。 */
    data class Bundled(@DrawableRes val resId: Int) : CoverSource
}

/** 内置测试封面：用来证明「封面取色 → 流体背景」链路在真机上成立（浅/深各拍一张）。 */
val BundledTestCover = CoverSource.Bundled(R.drawable.v0_cover_fixture)

/** 取色采样尺寸（Folia colorExtractor 也先缩到小图再取色）。 */
private const val SAMPLE_SIZE = 48

/**
 * 选封面：有业务封面用业务封面，否则用内置测试封面。
 *
 * Folia 的做法是「没有封面就不取色、只画背景色 + 渐变」；这里保留一个可见的取色来源，
 * 让审图能在模拟器上直接看到取色流体背景（report 第 4 条）。
 */
fun resolveCoverSource(artworkUri: String?): CoverSource =
    if (artworkUri.isNullOrBlank()) BundledTestCover else CoverSource.Url(artworkUri)

/** 读封面像素（缩到 [SAMPLE_SIZE]）。失败返回 null，调用方退回兜底色。 */
internal fun loadCoverPixels(context: Context, source: CoverSource): Bitmap? = runCatching {
    when (source) {
        is CoverSource.Bundled -> BitmapFactory.decodeResource(context.resources, source.resId)
        is CoverSource.Url -> openStream(context, source.value)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()?.let { decoded ->
    runCatching { Bitmap.createScaledBitmap(decoded, SAMPLE_SIZE, SAMPLE_SIZE, true) }.getOrNull()
}

private fun openStream(context: Context, url: String): InputStream? = when {
    url.startsWith("data:") -> url.substringAfter("base64,").takeIf { it.isNotEmpty() }
        ?.let { java.util.Base64.getDecoder().decode(it).inputStream() }
    url.startsWith("file://") -> File(url.removePrefix("file://")).inputStream()
    url.startsWith("file:/") -> File(url.removePrefix("file:")).inputStream()
    url.startsWith("content://") || url.startsWith("android.resource://") ->
        context.contentResolver.openInputStream(android.net.Uri.parse(url))
    url.startsWith("http://") || url.startsWith("https://") -> URL(url).openStream()
    File(url).exists() -> File(url).inputStream()
    else -> null
}

/** 位图 → 候选色（RGB 列表）。步长 2 采样，跳过过暗/过亮的像素后按饱和度排序。 */
internal fun extractCoverColors(bitmap: Bitmap, limit: Int = 8): List<Rgb> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val candidates = mutableListOf<Pair<Rgb, Double>>()
    var index = 0
    while (index < pixels.size) {
        val pixel = pixels[index]
        val a = (pixel ushr 24) and 0xFF
        if (a >= 128) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val maxV = maxOf(r, maxOf(g, b))
            val minV = minOf(r, minOf(g, b))
            val lightness = (maxV + minV) / 2.0
            val denominator = 255.0 - kotlin.math.abs(maxV + minV - 255)
            val saturation = if (denominator <= 0.0) 0.0 else (maxV - minV) / denominator
            val keep = (saturation > 0.2 && lightness > 30 && lightness < 220) || candidates.size < 100
            if (keep) candidates += Rgb(r, g, b) to (maxV - minV) / 255.0
        }
        index += 2
    }
    val sorted = candidates.sortedByDescending { it.second }.map { it.first }
    val unique = mutableListOf<Rgb>()
    sorted.forEach { color ->
        if (unique.none { it.distanceTo(color) < 20.0 }) unique += color
        if (unique.size >= limit) return@forEach
    }
    return unique
}

/** 封面 → 主题色（Folia 取色 + builtin dual theme 生成）。 */
internal fun themeFromCover(bitmap: Bitmap?, dark: Boolean): LyricTheme {
    val colors = bitmap?.let { extractCoverColors(it) }.orEmpty()
    val analysis = CoverPaletteAnalyzer.analyze(colors)
    return BuiltinDualTheme.generate(analysis, dark)
}

/** 背景色板：取色主题 + 具体色值（Compose 层直接用）。 */
@androidx.compose.runtime.Immutable
data class BackdropPalette(
    val background: Color,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
    val fromCover: Boolean,
)

private fun Rgb.toColor(): Color = Color(red = r, green = g, blue = b, alpha = 255)

internal fun LyricTheme.toPalette(): BackdropPalette = BackdropPalette(
    background = backgroundColor.toColor(),
    primary = primaryColor.toColor(),
    accent = accentColor.toColor(),
    secondary = secondaryColor.toColor(),
    fromCover = fromCover,
)

/**
 * 背景画布上的墨色（顶栏标题 / 返回键 / 样式入口 / 当前行未唱字都取这个）。
 *
 * Folia 的 primaryColor 是「按对比度 9:1 调过明度」的主文字色，浅色主题近墨、深色主题近白，
 * 顶着取色背景始终可读 —— 这就是返工第 5 条「浅色顶栏『歌词』几乎看不见」的修法：
 * 不再用固定 0xFF1C1917 / 0xFFF7F5F2 猜，直接用取色主题算出来的 primary。
 */
val BackdropPalette.ink: Color get() = primary

/** 背景上需要强调的那一色（Folia 取色 accent），当前行激活字用它。 */
val BackdropPalette.emphasis: Color get() = accent

/**
 * 记住当前封面得到的色板。
 *
 * 取色是同步的位图操作（已经缩到 48×48），放在 composition 里做一次即可；
 * Robolectric 截图与真机走完全同一条代码路径，所以截图能看到取色结果。
 */
@Composable
fun rememberCoverBackdrop(source: CoverSource, dark: Boolean): BackdropPalette {
    val context = LocalContext.current
    return remember(source, dark) {
        themeFromCover(loadCoverPixels(context, source), dark).toPalette()
    }
}
