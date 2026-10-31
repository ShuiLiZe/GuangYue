// 对应 Folia 文件：src/types.ts（Word / Line / LineRenderHints / Theme / ClassicTuning）
//
// V0 歌词内核：与 Folia 一致的歌词渲染模型。纯 Kotlin/JVM，无 Android 依赖，可直接跑 JVM 单测。
package com.guangyue.core.lyrics

/** 逐字（音节）时间。对应 Folia types.ts 的 LyricSyllable。 */
data class LyricSyllable(
    val text: String,
    val startTime: Double,
    val endTime: Double,
)

/**
 * 一个词（Folia 的 `Word`）。时间单位为秒（与 Folia 一致）。
 */
data class Word(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    /** 有逐字时间时填，否则空；空时词内按字均分（GraphemeTimings.buildWordGraphemeTimings）。 */
    val syllables: List<LyricSyllable> = emptyList(),
)

/**
 * 一行歌词（Folia 的 `Line`）。
 * 只保留 V0 内核用得到的字段；`wordSegments` / `autoWordSegments` 供换歌闸门的签名哈希。
 */
data class Line(
    val words: List<Word>,
    val startTime: Double,
    val endTime: Double,
    val fullText: String,
    val translation: String? = null,
    /** 歌词元数据：分段/分类用（Folia 的 songPart）。 */
    val songPart: String? = null,
    /** 歌词元数据：段落块序号（Folia 的 blockIndex）。 */
    val blockIndex: Int? = null,
    /** 预先算好的渲染提示；为空时由 RenderHints.getLineRenderHints 现算。 */
    val renderHints: LineRenderHints? = null,
    val isChorus: Boolean = false,
    /** 用户保存的分词（Folia 的 wordSegments）。 */
    val wordSegments: List<String>? = null,
    /** 自动分词结果（Folia 的 autoWordSegments，icu-folia）。 */
    val autoWordSegments: List<String>? = null,
) {
    /** 行原始时长（秒）。 */
    val rawDuration: Double get() = endTime - startTime
}

/** 行时长短位。对应 Folia renderHints.ts 的 timingClass。 */
enum class TimingClass { MICRO, SHORT, NORMAL }

/** 行入离场模式。对应 Folia 的 lineTransitionMode。 */
enum class LineTransitionMode { NORMAL, FAST, NONE }

/** 逐字显现模式。对应 Folia 的 wordRevealMode。 */
enum class WordRevealMode { NORMAL, FAST, INSTANT }

/** 行渲染档位。对应 Folia renderHints.ts 的 LineRenderHints。 */
data class LineRenderHints(
    val rawDuration: Double,
    val timingClass: TimingClass,
    val renderEndTime: Double,
    val lineTransitionMode: LineTransitionMode,
    val wordRevealMode: WordRevealMode,
)

/** 行入离场时序。对应 Folia getLineTransitionTiming 的返回值。 */
data class LineTransitionTiming(
    val enterDuration: Double,
    val exitDuration: Double,
    val linePassHold: Double,
)

/** 动画强度。对应 Folia Theme.animationIntensity。 */
enum class AnimationIntensity { CALM, NORMAL, CHAOTIC }

/** 字体族。对应 Folia Theme.fontStyle。 */
enum class FontStyle { SANS, SERIF, MONO }

/** 主题。对应 Folia types.ts 的 Theme（只保留 V0 内核与取色需要的字段）。 */
data class Theme(
    val name: String,
    val backgroundColor: String,
    val primaryColor: String,
    val accentColor: String,
    val secondaryColor: String,
    val fontStyle: FontStyle = FontStyle.SANS,
    val animationIntensity: AnimationIntensity = AnimationIntensity.NORMAL,
)

/**
 * 流光(classic)样式可调参数。对应 Folia classic/tuning.ts 的 ClassicTuning
 * 与 src/types.ts 的 DEFAULT_CLASSIC_TUNING。
 */
data class ClassicTuning(
    /** 字词旋转开关。 */
    val enableWordRotation: Boolean = true,
    /** 呼吸浮动倍率，钳在 [0,2]。 */
    val breathingFloatMultiplier: Double = 1.0,
    /** 旧布局（不做逐词溢出补偿）。 */
    val useLegacyLayout: Boolean = false,
    /** 词距倍率，钳在 [0,2]，默认 0.7。 */
    val wordSpacing: Double = DEFAULT_WORD_SPACING,
) {
    companion object {
        /** Folia classic/tuning.ts：DEFAULT_WORD_SPACING = DEFAULT_CLASSIC_TUNING.wordSpacing ?? 0.7。 */
        const val DEFAULT_WORD_SPACING = 0.7

        /** Folia 的 zeroToTwo(value, fallback)：非有限值退回 fallback，否则钳到 [0,2]。 */
        fun zeroToTwo(value: Double?, fallback: Double): Double =
            if (value == null || !value.isFinite()) fallback else minOf(2.0, maxOf(0.0, value))

        /** 读回被钳过的 tuning，规则与 Folia tuning.storage.read 一致。 */
        fun sanitize(
            enableWordRotation: Boolean?,
            breathingFloatMultiplier: Double?,
            useLegacyLayout: Boolean?,
            wordSpacing: Double?,
        ): ClassicTuning = ClassicTuning(
            enableWordRotation = enableWordRotation ?: true,
            breathingFloatMultiplier = zeroToTwo(breathingFloatMultiplier, 1.0),
            useLegacyLayout = useLegacyLayout ?: false,
            wordSpacing = zeroToTwo(wordSpacing, DEFAULT_WORD_SPACING),
        )
    }
}

/** 一行里的「像词」的片段。对应 Folia utils/lyrics/wordSegmentation.ts 的 LyricWordSegment。 */
data class LyricWordSegment(
    val segment: String,
    val index: Int,
    val isWordLike: Boolean,
)