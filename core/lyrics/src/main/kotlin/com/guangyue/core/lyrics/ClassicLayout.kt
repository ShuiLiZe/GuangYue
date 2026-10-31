// 对应 Folia 文件：
//   src/components/visualizer/classic/Visualizer.tsx（行 63–348，布局与呼吸浮动部分）
//   src/components/visualizer/classic/tuning.ts（行 1–44）
//   src/components/visualizer/subtitleFontSizes.ts（行 1–19）
//
// 流光(classic)样式的确定性布局：呼吸浮动、逐词散布/旋转/缩放、词距、字号。
// 全部照抄 Folia 的数值与公式，用确定性 RNG 保证同一行每次布局一致
// （时间只改变动画状态，不改变基础几何）。
package com.guangyue.core.lyrics

/** 逐词布局配置。对应 Folia classic/Visualizer.tsx 的 WordLayoutConfig。 */
data class GlowWordConfig(
    val id: String,
    /** 水平偏移（px）。 */
    val x: Double,
    /** 垂直偏移（px）。 */
    val y: Double,
    val scale: Double,
    /** 基础旋转（度）。 */
    val rotate: Double,
    /** passed 态的额外旋转（度）。 */
    val passedRotate: Double,
    /** 与该词右侧相邻词之间的右边距（px）。 */
    val marginRight: Double,
    /** chaotic 下随机贴边：flex-start / flex-end / auto。 */
    val alignSelf: WordAlign = WordAlign.AUTO,
)

/** 逐词对齐。对应 Folia 的 alignSelf。 */
enum class WordAlign { FLEX_START, CENTER, FLEX_END, AUTO }

/** 呼吸浮动参数。对应 Folia configByIntensity。 */
data class BreathingConfig(val distance: Double, val duration: Double)

/** 行容器布局。对应 Folia 的 LineLayoutConfig。 */
data class LineLayoutConfig(
    val justifyContent: LineJustify,
    val alignItems: LineAlign,
    val perspective: Double,
)

/** 行水平对齐（Folia 的 justifyOptions）。 */
enum class LineJustify { CENTER, START, END, AROUND, BETWEEN }

/** 行垂直对齐（Folia 的 alignOptions）。 */
enum class LineAlign { CENTER, START, END }

/** 一整行的布局结果。 */
data class ClassicLineLayout(
    val words: List<GlowWordConfig>,
    val line: LineLayoutConfig,
    /** 字号，单位 px。 */
    val mainFontSizePx: Double,
    val emptyFontSizePx: Double,
    val breathing: BreathingConfig,
    val breathingMultiplier: Double,
)

/** 对应 Folia classic/Visualizer.tsx 与 classic/tuning.ts。 */
object ClassicLayout {

    /** Folia tuning.ts 行 12：DEFAULT_WORD_SPACING。 */
    const val DEFAULT_WORD_SPACING = ClassicTuning.DEFAULT_WORD_SPACING

    /** 对应 Folia tuning.ts 行 6 的 zeroToTwo。 */
    fun zeroToTwo(value: Double?, fallback: Double): Double = ClassicTuning.zeroToTwo(value, fallback)

    /**
     * 对应 Folia classic/Visualizer.tsx 行 199–203 的确定性 RNG：
     *   const x = Math.sin(wordSeed + offset) * 10000;  return x - Math.floor(x);
     * Kotlin 的 sin 与 JS 的 Math.sin 同为 IEEE754 double，结果逐位一致。
     */
    fun random(wordSeed: Int, offset: Double): Double {
        val value = kotlin.math.sin(wordSeed + offset) * 10000.0
        return value - kotlin.math.floor(value)
    }

    /** 对应 Folia 行 279–283 的 configByIntensity：calm 10/8.5s、normal 14/7s、chaotic 18/5.8s。 */
    fun breathingConfig(intensity: AnimationIntensity): BreathingConfig = when (intensity) {
        AnimationIntensity.CALM -> BreathingConfig(distance = 10.0, duration = 8.5)
        AnimationIntensity.NORMAL -> BreathingConfig(distance = 14.0, duration = 7.0)
        AnimationIntensity.CHAOTIC -> BreathingConfig(distance = 18.0, duration = 5.8)
    }

    /** 对应 Folia 行 156–170 的 getPixelFontSize：clamp(2.25*s*rem, 6*s*vw, 4.5*s*rem)。 */
    fun pixelFontSize(fontScale: Double, viewportWidthPx: Double, remPx: Double = 16.0): Double {
        val minPx = 2.25 * fontScale * remPx
        val valPx = 6.0 * fontScale * viewportWidthPx / 100.0
        val maxPx = 4.5 * fontScale * remPx
        return maxOf(minPx, minOf(valPx, maxPx))
    }

    /** 对应 Folia 的 emptyFontSize：clamp(1.5*s*rem, 3.5*s*vw, 2.25*s*rem)。 */
    fun emptyPixelFontSize(fontScale: Double, viewportWidthPx: Double, remPx: Double = 16.0): Double {
        val minPx = 1.5 * fontScale * remPx
        val valPx = 3.5 * fontScale * viewportWidthPx / 100.0
        val maxPx = 2.25 * fontScale * remPx
        return maxOf(minPx, minOf(valPx, maxPx))
    }

    /**
     * 对应 Folia 行 171–175 的 measureWordWidth：
     * 有 canvas 时 measureText，没有 document 时退化成 text.length * pxSize * 0.65。
     */
    fun measureWordWidthFallback(text: String, pxSize: Double): Double = text.length * pxSize * 0.65

    /**
     * 逐词布局 + 行容器布局。对应 Folia classic/Visualizer.tsx 行 124–274。
     *
     * @param line 当前行；seed 取 line.startTime 的整数部分（与 Folia `const seed = activeLine.startTime` 一致，
     *             JS 里 seed 是 double，Math.floor(seed % len) 与 Int 结果相同）
     * @param wordWidthsPx 每个词的测量宽度（px），索引与 words 对齐
     * @param lyricsFontScale 歌词字号倍率（Folia lyricsFontScale）
     * @param viewportWidthPx 视口宽度（px）
     * @param remPx 1rem 对应的 px（Folia 固定 16）
     * @param tuning 流光可调参数
     * @param intensity 动画强度
     */
    fun buildLineLayout(
        line: Line,
        wordWidthsPx: List<Double>,
        lyricsFontScale: Double,
        viewportWidthPx: Double,
        remPx: Double,
        tuning: ClassicTuning,
        intensity: AnimationIntensity,
    ): ClassicLineLayout {
        val seed = line.startTime.toInt()
        val isChaotic = intensity == AnimationIntensity.CHAOTIC
        val isCalm = intensity == AnimationIntensity.CALM

        val pxFontSize = pixelFontSize(lyricsFontScale, viewportWidthPx, remPx)
        val emptyFontSize = emptyPixelFontSize(lyricsFontScale, viewportWidthPx, remPx)

        val isInterlude = line.fullText == "......"

        // 行容器：calm 只有一套对齐方式，其它强度在候选里按 seed 确定性挑。
        val justifyOptions = if (isCalm) listOf(LineJustify.CENTER)
        else listOf(LineJustify.START, LineJustify.CENTER, LineJustify.END, LineJustify.AROUND, LineJustify.BETWEEN)
        val alignOptions = if (isCalm) listOf(LineAlign.CENTER)
        else listOf(LineAlign.START, LineAlign.CENTER, LineAlign.END)

        val lineConfig = LineLayoutConfig(
            justifyContent = if (isInterlude) LineJustify.CENTER
            else justifyOptions[(seed % justifyOptions.size + justifyOptions.size) % justifyOptions.size],
            alignItems = alignOptions[((seed * 2) % alignOptions.size + alignOptions.size) % alignOptions.size],
            perspective = if (isChaotic) (500 + (seed % 500)).toDouble() else 1000.0,
        )

        val baseSpread = if (isChaotic) 60.0 else if (isCalm) 0.0 else 20.0
        val baseRotate = if (isChaotic) 30.0 else if (isCalm) 0.0 else 5.0
        val spacingMultiplier = tuning.wordSpacing

        val displayWords = line.words
        val configs = displayWords.mapIndexed { i, word ->
            val wordSeed = seed + i

            // 间奏行：居中、轻微垂直随机、1.5 倍缩放、3rem 右边距。
            if (isInterlude) {
                return@mapIndexed GlowWordConfig(
                    id = "${word.text}-$i-$seed",
                    x = 0.0,
                    y = (random(wordSeed, 2.0) - 0.5) * 15.0,
                    rotate = 0.0,
                    scale = 1.5,
                    marginRight = 3.0 * remPx,       // Folia 写的 '3rem'
                    alignSelf = WordAlign.CENTER,    // Folia 写的 alignSelf: 'center'
                    passedRotate = 0.0,
                )
            }

            val wordConfigScale = if (isChaotic) 0.8 + random(wordSeed, 4.0) * 0.6
            else 1.1 + random(wordSeed, 4.0) * 0.2
            val xVal = (random(wordSeed, 1.0) - 0.5) * baseSpread * 2
            val yVal = (random(wordSeed, 2.0) - 0.5) * baseSpread * 2

            var marginRight = if (isChaotic) random(wordSeed, 5.0) * 1.5 * remPx else 0.8 * remPx

            if (!tuning.useLegacyLayout) {
                // 精确补偿缩放与位移造成的视觉重叠。
                val wI = wordWidthsPx.getOrElse(i) { 0.0 }
                val sI = wordConfigScale * 1.4

                var wNext = 0.0
                var sNext = 1.0
                var xNext = 0.0

                if (i + 1 < displayWords.size) {
                    val nextSeed = seed + (i + 1)
                    val nextConfigScale = if (isChaotic) 0.8 + random(nextSeed, 4.0) * 0.6
                    else 1.1 + random(nextSeed, 4.0) * 0.2
                    sNext = nextConfigScale * 1.4
                    xNext = (random(nextSeed, 1.0) - 0.5) * baseSpread * 2
                    wNext = wordWidthsPx.getOrElse(i + 1) { 0.0 }
                }

                val gap = 0.05 * pxFontSize
                val halfOverflowI = wI * (sI - 1) / 2
                val halfOverflowNext = wNext * (sNext - 1) / 2
                val xOffsetDiff = xVal - xNext

                val calculatedMargin = (halfOverflowI + halfOverflowNext + xOffsetDiff + gap) * spacingMultiplier
                val minMargin = (if (isChaotic) 0.08 * pxFontSize else 0.12 * pxFontSize) * spacingMultiplier
                marginRight = maxOf(minMargin, calculatedMargin)
            }

            GlowWordConfig(
                id = "${word.text}-$i-$seed",
                x = xVal,
                y = yVal,
                rotate = if (tuning.enableWordRotation) (random(wordSeed, 3.0) - 0.5) * baseRotate * 2 else 0.0,
                scale = wordConfigScale,
                marginRight = marginRight,
                alignSelf = if (isChaotic && random(wordSeed, 6.0) > 0.7) {
                    if (random(wordSeed, 7.0) > 0.5) WordAlign.FLEX_START else WordAlign.FLEX_END
                } else WordAlign.AUTO,
                passedRotate = if (tuning.enableWordRotation) (random(wordSeed, 8.0) - 0.5) * 45.0 else 0.0,
            )
        }

        return ClassicLineLayout(
            words = configs,
            line = lineConfig,
            mainFontSizePx = pxFontSize,
            emptyFontSizePx = emptyFontSize,
            breathing = breathingConfig(intensity),
            breathingMultiplier = tuning.breathingFloatMultiplier,
        )
    }

    /**
     * 呼吸浮动的动画关键帧。对应 Folia classic/Visualizer.tsx 行 277–303：
     *   y:     [0, -scaledDistance, 0, scaledDistance * 0.45, 0]
     *   scale: [1, 1 + 0.01*multiplier, 1, 1 - 0.005*multiplier, 1]
     * multiplier <= 0 时整段不加动效（返回 null）。
     */
    fun breathingKeyframes(layout: ClassicLineLayout): BreathingKeyframes? {
        val multiplier = layout.breathingMultiplier
        if (multiplier <= 0.0) return null
        val scaledDistance = layout.breathing.distance * multiplier
        return BreathingKeyframes(
            ys = listOf(0.0, -scaledDistance, 0.0, scaledDistance * 0.45, 0.0),
            scales = listOf(1.0, 1.0 + 0.01 * multiplier, 1.0, 1.0 - 0.005 * multiplier, 1.0),
            durationSeconds = layout.breathing.duration,
        )
    }

    /** 呼吸浮动关键帧。 */
    data class BreathingKeyframes(
        val ys: List<Double>,
        val scales: List<Double>,
        val durationSeconds: Double,
    )

    /** 字幕字号。对应 Folia subtitleFontSizes.ts 的 resolveSubtitleFontSizes。 */
    data class SubtitleFontSizes(
        val translationMinRem: Double,
        val translationPreferredVw: Double,
        val translationMaxRem: Double,
        val upcomingMinRem: Double,
        val upcomingPreferredVw: Double,
        val upcomingMaxRem: Double,
    )

    /**
     * 对应 Folia subtitleFontSizes.ts：
     *   translation = clamp(1.125*scale rem, 2.6*scale vw, 1.25*scale rem)
     *   upcoming    = clamp(0.875*scale rem, 2*scale vw, 1*scale rem)
     */
    fun resolveSubtitleFontSizes(lyricsFontScale: Double = 1.0): SubtitleFontSizes = SubtitleFontSizes(
        translationMinRem = 1.125 * lyricsFontScale,
        translationPreferredVw = 2.6 * lyricsFontScale,
        translationMaxRem = 1.25 * lyricsFontScale,
        upcomingMinRem = 0.875 * lyricsFontScale,
        upcomingPreferredVw = 2.0 * lyricsFontScale,
        upcomingMaxRem = 1.0 * lyricsFontScale,
    )
}

/** 对应 Folia colorMix.ts 与 backgrounds/useBlendedColors.ts 的混色工具。 */
object ColorMix {

    /** 对应 useBlendedColors.ts 的 easeOut(t) = 1 - (1-t)^3。 */
    fun easeOut(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        val inv = 1.0 - clamped
        return 1.0 - inv * inv * inv
    }

    /** 解析后的 RGB 通道。 */
    data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun toHex(): String =
            "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** 对应 Folia colorMix.ts 行 6 的 FALLBACK_RGB。 */
    val FALLBACK_RGB = Rgb(255, 255, 255)

    // 对应 Folia 的 /^rgba?\(([^)]+)\)$/：允许尾随 alpha，整体必须完全匹配。
    private val RGB_REGEX = Regex("^rgba?\\(([^)]+)\\)$")

    /**
     * 对应 Folia colorMix.ts 的 readColor：支持 #rgb / #rrggbb / rgb() / rgba()。
     * 其他形式（CSS 颜色名等）Folia 原样返回字符串；Kotlin 侧返回 null 表示「按原样 passthrough」。
     */
    fun parseColor(color: String): Rgb? {
        val text = color.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("#")) {
            val hex = text.substring(1)
            return when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    Rgb(r * 17, g * 17, b * 17)
                }
                6 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    Rgb(r, g, b)
                }
                else -> null
            }
        }

        // 对应 Folia 的 `rgbMatch[1].split(',').slice(0,3).map(parseFloat)`：取前三个通道，允许尾随 alpha。
        val match = RGB_REGEX.matchEntire(text) ?: return null
        val parts = match.groupValues[1].split(',').take(3).map { it.trim().toDoubleOrNull() }
        if (parts.size < 3 || parts.any { it == null || !it.isFinite() }) return null
        return Rgb(parts[0]!!.toInt(), parts[1]!!.toInt(), parts[2]!!.toInt())
    }

    /**
     * 对应 Folia colorMix.ts 的 resolveGlowColorStrength：
     *   value = max(r,g,b)；progress = clamp((value-110)/50,0,1)；返回 smoothstep(progress)
     * 暗色主题的辉光被这条曲线压住，避免糊成一团。
     */
    fun resolveGlowColorStrength(r: Int, g: Int, b: Int): Double {
        val value = maxOf(r, g, b).toDouble()
        val progress = ((value - 110.0) / 50.0).coerceIn(0.0, 1.0)
        return progress * progress * (3.0 - 2.0 * progress)
    }

    /** 对应 Folia colorMix.ts 的 mixColors(from,to,amount,alpha=1)。 */
    fun mixColors(from: Rgb, to: Rgb, amount: Double): Rgb {
        val t = amount.coerceIn(0.0, 1.0)
        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt()
        return Rgb(lerp(from.r, to.r), lerp(from.g, to.g), lerp(from.b, to.b))
    }

    /**
     * 对应 Folia backgrounds/useBlendedColors.ts 的核心行为（无 progress 时的分支）：
     * 同色不动；durationMs <= 0 或长度不等 → 立刻切换；否则按 easeOut(t) 混合。
     * 换歌柔焦换色就靠它。
     */
    fun blendColors(from: List<String>, to: List<String>, durationMs: Long, elapsedMs: Long): List<String> {
        if (from == to) return from
        if (durationMs <= 0L || from.size != to.size) return to

        val t = easeOut(elapsedMs.toDouble() / durationMs.toDouble())
        return from.indices.map { index ->
            val fromRgb = parseColor(from[index]) ?: FALLBACK_RGB
            val toRgb = parseColor(to[index]) ?: FALLBACK_RGB
            mixColors(fromRgb, toRgb, t).toHex()
        }
    }
}