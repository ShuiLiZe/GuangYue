// 对应 Folia 文件：src/components/visualizer/fume/fumeMath.ts、fumeTypes.ts、tuning.ts、
//                src/types.ts（DEFAULT_FUME_TUNING / FumeTuning）
//
// V1a「浮名」(fume) 样式的纯逻辑底座：数学、调参，以及排版 / 镜头 / 绘制共用的数据结构。
// 纯 Kotlin/JVM，无 Android 依赖，可直接跑 JVM 单测；数值逐条照抄 Folia 源码。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.GraphemeTiming
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word

/** 对应 Folia fumeMath.ts（clamp / mix / 缓动 / 延迟辉光包络 / grapheme / FNV-1a 种子）。 */
object FumeMath {

    fun clamp(value: Double, min: Double, max: Double): Double = minOf(maxOf(value, min), max)

    fun mix(from: Double, to: Double, amount: Double): Double = from + (to - from) * amount

    /** easeOutCubic = 1-(1-t)^3。 */
    fun easeOutCubic(t: Double): Double {
        val x = 1.0 - t
        return 1.0 - x * x * x
    }

    /** easeInCubic = t^3。 */
    fun easeInCubic(t: Double): Double = t * t * t

    /** easeInOutCubic = t<0.5 ? 4t^3 : 1-(-2t+2)^3/2。 */
    fun easeInOutCubic(t: Double): Double {
        if (t < 0.5) return 4.0 * t * t * t
        val x = -2.0 * t + 2.0
        return 1.0 - x * x * x / 2.0
    }

    fun quadraticBezier(a: Double, b: Double, c: Double, t: Double): Double {
        val u = 1.0 - t
        return u * u * a + 2.0 * u * t * b + t * t * c
    }

    /** 对应 resolveDelayedGlowEnvelope(progress, peak = 0.8)。 */
    fun resolveDelayedGlowEnvelope(progress: Double, peak: Double = 0.8): Double =
        if (progress <= peak) {
            easeOutCubic(clamp(progress / peak, 0.0, 1.0))
        } else {
            1.0 - easeInCubic(clamp((progress - peak) / (1.0 - peak), 0.0, 1.0))
        }

    /** 对应 fumeMath.ts 的 CJK 正则（中日韩 + 假名 + 谚文）。 */
    private val CJK_REGEX = Regex("[\\u4e00-\\u9fff\\u3040-\\u30ff\\uac00-\\ud7af]")

    fun isCjk(text: String): Boolean = CJK_REGEX.containsMatchIn(text)

    /** FNV-1a 32 位哈希（无符号值装进 Long）。 */
    fun hashString(seed: String): Long {
        var hash = 2_166_136_261L
        for (ch in seed) {
            hash = hash xor (ch.code.toLong() and 0xFFL)
            hash = (hash * 16_777_619L) and 0xFFFFFFFFL
        }
        return hash
    }

    /** 对应 fumeMath.ts 的 seeded(seed) = (hash % 10000) / 10000。 */
    fun seeded(seed: String): Double = (hashString(seed) % 10_000L) / 10_000.0

    /**
     * 对应 fumeMath.ts 的 splitGraphemes。
     *
     * Folia 走 Intl.Segmenter(granularity:'grapheme')；Kotlin 用码点序列近似
     * （与 [com.guangyue.core.lyrics.GraphemeTimings.splitLyricGraphemes] 同一口径，
     * 代理对会各自算一个字符，差异见 docs/ui-v0/report.md）。
     */
    fun splitGraphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val count = Character.charCount(cp)
            out.add(text.substring(i, i + count))
            i += count
        }
        return out
    }
}

/** 对应 Folia fumeTypes.ts 的 ViewportSize。 */
data class FumeViewport(val width: Double, val height: Double)

/** 块类型（对应 Folia 的 'body' | 'hero'）。 */
enum class FumeVariant { BODY, HERO }

/** 字号权重：hero 780 / body 640（对应 resolveThemeFontWeight(theme, hero?780:640)）。 */
fun FumeVariant.fontWeight(): Int = if (this == FumeVariant.HERO) 780 else 640

/** 镜头跟随方式。对应 Folia FumeTuning.cameraTrackingMode 的 'stepped' | 'smooth'。 */
enum class FumeCameraTrackingMode { STEPPED, SMOOTH }

/**
 * 「浮名」样式可调参数。对应 Folia src/types.ts 的 FumeTuning 与 DEFAULT_FUME_TUNING。
 * V1a 只用默认值（tuning 面板 UI 留给 V1b），但 clamp 规则现在就落地，保证读回值一致。
 */
data class FumeTuning(
    val hidePrintSymbols: Boolean = false,
    val disableGeometricBackground: Boolean = true,
    val backgroundObjectOpacity: Double = 0.5,
    val textHoldRatio: Double = 1.0,
    val cameraTrackingMode: FumeCameraTrackingMode = FumeCameraTrackingMode.SMOOTH,
    val cameraSpeed: Double = 1.0,
    val glowIntensity: Double = 1.0,
    val heroScale: Double = 1.0,
) {
    companion object {
        val DEFAULT = FumeTuning()

        /** 对应 tuning.ts 的 clampSpeed 等：非有限值退回默认，否则钳到给定区间。 */
        fun clampRange(value: Double?, min: Double, max: Double, fallback: Double): Double =
            if (value == null || !value.isFinite()) fallback else FumeMath.clamp(value, min, max)

        /** 读写统一入口（与 Folia resolvedFumeTuning 一致）。 */
        fun sanitize(
            hidePrintSymbols: Boolean? = null,
            disableGeometricBackground: Boolean? = null,
            backgroundObjectOpacity: Double? = null,
            textHoldRatio: Double? = null,
            cameraTrackingMode: FumeCameraTrackingMode? = null,
            cameraSpeed: Double? = null,
            glowIntensity: Double? = null,
            heroScale: Double? = null,
        ): FumeTuning = FumeTuning(
            hidePrintSymbols = hidePrintSymbols ?: DEFAULT.hidePrintSymbols,
            disableGeometricBackground = disableGeometricBackground ?: DEFAULT.disableGeometricBackground,
            backgroundObjectOpacity = clampRange(backgroundObjectOpacity, 0.0, 1.0, DEFAULT.backgroundObjectOpacity),
            textHoldRatio = clampRange(textHoldRatio, 0.0, 1.0, DEFAULT.textHoldRatio),
            cameraTrackingMode = cameraTrackingMode ?: DEFAULT.cameraTrackingMode,
            cameraSpeed = clampRange(cameraSpeed, 0.55, 1.85, DEFAULT.cameraSpeed),
            glowIntensity = clampRange(glowIntensity, 0.0, 1.8, DEFAULT.glowIntensity),
            heroScale = clampRange(heroScale, 0.82, 1.32, DEFAULT.heroScale),
        )
    }
}

/** 对应 Folia fumeTypes.ts 的 SegmentMeta（一段连续文本覆盖的 grapheme 区间）。 */
data class FumeSegmentMeta(
    val graphemeStart: Int,
    val graphemeEnd: Int,
    val graphemeCount: Int,
)

/** 对应 Folia fumeTypes.ts 的 WordRange。 */
data class FumeWordRange(
    val wordIndex: Int,
    val word: Word,
    val start: Int,
    val end: Int,
    val colorStart: Int,
    val colorEnd: Int,
    val graphemeTimings: List<GraphemeTiming>,
)

/** 对应 Folia fumeTypes.ts 的 RenderSegmentSlice。 */
data class FumeRenderSegment(
    val text: String,
    val start: Int,
    val end: Int,
    val localStart: Int,
    val localEnd: Int,
    val x: Double,
    val width: Double,
    val isFullSegment: Boolean,
    val measuredGlyphOffsets: List<Double>,
)

/** 对应 Folia fumeTypes.ts 的 RenderLineSlice。 */
data class FumeRenderLine(
    val id: String,
    val text: String,
    val start: Int,
    val end: Int,
    val graphemes: List<String>,
    val glyphOffsets: List<Double>,
    val segments: List<FumeRenderSegment>,
    val left: Double,
    val top: Double,
    val width: Double,
)

/** 行内某个字素的位置（resolveRenderLineOffset / resolveSegmentGlyphOffset 的产物）。 */
fun FumeRenderLine.offsetOf(localOffset: Int): Double = when {
    localOffset <= 0 -> 0.0
    localOffset >= graphemes.size -> width
    else -> glyphOffsets.getOrElse(localOffset) { 0.0 }
}

/** 对应 resolveGlyphAdvance(renderLine, index)：下一个偏移减当前，末尾用行宽。 */
fun FumeRenderLine.glyphAdvance(localIndex: Int): Double {
    if (localIndex < 0 || localIndex >= graphemes.size) return 0.0
    val current = glyphOffsets.getOrElse(localIndex) { 0.0 }
    val next = glyphOffsets.getOrElse(localIndex + 1) { width }
    return maxOf(next - current, 0.0)
}

/**
 * 对应 Folia fumeTypes.ts 的 FumeBlock。
 *
 * [advances] 是整块文本逐字素的推进宽度（与 [graphemes] 同下标），
 * 用来替换 Folia 的 `pretext` 测量（widthBetweenOffsets）。
 */
data class FumeBlock(
    val id: String,
    val sourceLineIndex: Int,
    val line: Line,
    val variant: FumeVariant,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val innerWidth: Double,
    val fontPx: Double,
    val lineHeight: Double,
    val graphemes: List<String>,
    val advances: List<Double>,
    val segmentMetas: List<FumeSegmentMeta>,
    val wordRanges: List<FumeWordRange>,
    val wordRangeIndexByOffset: List<Int>,
    val colorRangeIndexByOffset: List<Int>,
    val renderLines: List<FumeRenderLine>,
) {
    /** 对应 Folia 的 widthBetweenOffsets(prepared, segmentMetas, from, to)（块内全局偏移）。 */
    fun widthBetweenOffsets(fromOffset: Int, toOffset: Int): Double {
        val from = fromOffset.coerceIn(0, advances.size)
        val to = toOffset.coerceIn(from, advances.size)
        var sum = 0.0
        for (index in from until to) sum += advances[index]
        return sum
    }
}

/** 对应 Folia fumeTypes.ts 的 FumePaperBounds。 */
data class FumePaperBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/** 对应 Folia fumeTypes.ts 的 FumeArticleLayout。 */
data class FumeArticleLayout(
    val width: Double,
    val height: Double,
    val viewportHeight: Double,
    val columns: Int,
    val gap: Double,
    val paperBounds: FumePaperBounds,
    val blocks: List<FumeBlock>,
    val blockBySourceLineIndex: Map<Int, FumeBlock>,
    val chronologicalBlocks: List<FumeBlock>,
    val firstRenderableStartTime: Double,
    val lastChronologicalRenderEndTime: Double,
)

/**
 * 文本测量抽象：Folia 用 `pretext` 的 layoutWithLines / measureText，
 * Kotlin 侧由绘制层用原生文字测量实现（Android Paint），纯逻辑单测用假测量器。
 */
interface FumeTextMeasurer {
    /** [text] 在 [fontPx] 字号下的单行宽度（px）。 */
    fun measureWidth(text: String, fontPx: Double, variant: FumeVariant): Double
}

/** 对应 Folia fumeTypes.ts 的 CameraTarget。 */
data class FumeCameraTarget(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val velocityX: Double = 0.0,
    val velocityY: Double = 0.0,
    val focusX: Double = 0.0,
    val focusY: Double = 0.0,
    val scale: Double = 1.0,
    val velocityScale: Double = 0.0,
    val focusScale: Double = 1.0,
)

/** 对应 Folia fumeTypes.ts 的 CameraRetargetState.bridgeMode。 */
enum class FumeBridgeMode { NONE, DIRECT, OVERVIEW }

/** 对应 Folia fumeTypes.ts 的 CameraRetargetState。 */
data class FumeCameraRetargetState(
    val sourceLineIndex: Int = -1,
    val startedAt: Double = 0.0,
    val duration: Double = 0.18,
    val fromX: Double = 0.0,
    val fromY: Double = 0.0,
    val fromScale: Double = 1.0,
    val bridgeMode: FumeBridgeMode = FumeBridgeMode.NONE,
    val bridgeWaypointX: Double = 0.0,
    val bridgeWaypointY: Double = 0.0,
    val bridgeWaypointScale: Double = 1.0,
    val bridgeWaypointPhase: Double = 0.36,
)

/** 对应 Folia fumeCameraStep.ts 的 FumeCameraState（逐帧原地推进）。 */
data class FumeCameraState(
    val camera: FumeCameraTarget,
    val retarget: FumeCameraRetargetState,
    val initialized: Boolean,
)

/** 对应 Folia fumeTypes.ts 的 CameraViewTarget。 */
data class FumeCameraViewTarget(
    val x: Double,
    val y: Double,
    val scale: Double,
)