// 对应 Folia 文件：src/components/visualizer/glowWordTiming.ts（行 1–77）
//
// GlowWord 的时序档位：唤醒提前量（wordLookahead）、显示时长、行容器入离场动效参数。
package com.guangyue.core.lyrics

/** GlowWord 渲染档位。对应 Folia 的 GlowWordRenderProfile。 */
data class GlowWordRenderProfile(
    val renderHints: LineRenderHints?,
    val lineRenderEndTime: Double,
    val lineTransitionMode: LineTransitionMode,
    val wordRevealMode: WordRevealMode,
    /** 词进入 active 的提前量（秒）。 */
    val wordLookahead: Double,
)

/** 行容器的入/离场动效参数。对应 Folia getGlowWordLineContainerMotion 的返回值。 */
data class LineContainerMotion(
    val initialOpacity: Double,
    val initialScale: Double,
    val initialBlurPx: Double,
    val animateDuration: Double,       // 0 表示 Folia 未指定 duration（走 framer-motion 默认/弹簧）
    val animateEase: String,
    val exitOpacity: Double,
    val exitScale: Double,
    val exitBlurPx: Double,
    val exitDuration: Double,
    val exitEase: String,
)

/** 对应 Folia glowWordTiming.ts。 */
object GlowWordTiming {

    /** Folia 行 46–54：instant 0.03 / fast 0.08 / normal 0.15。 */
    const val WORD_LOOKAHEAD_INSTANT = 0.03
    const val WORD_LOOKAHEAD_FAST = 0.08
    const val WORD_LOOKAHEAD_NORMAL = 0.15

    /** Folia 行 56–70：最短显示时长 instant 0.08 / fast 0.12 / normal 0.1。 */
    const val MIN_DISPLAY_INSTANT = 0.08
    const val MIN_DISPLAY_FAST = 0.12
    const val MIN_DISPLAY_NORMAL = 0.1

    /** 对应 Folia 行 56–71 的 resolveGlowWordRenderProfile。 */
    fun resolveGlowWordRenderProfile(line: Line?): GlowWordRenderProfile? {
        if (line == null) return null
        val renderHints = RenderHints.getLineRenderHints(line)
        val wordRevealMode = renderHints?.wordRevealMode ?: WordRevealMode.NORMAL
        val wordLookahead = when (wordRevealMode) {
            WordRevealMode.INSTANT -> WORD_LOOKAHEAD_INSTANT
            WordRevealMode.FAST -> WORD_LOOKAHEAD_FAST
            WordRevealMode.NORMAL -> WORD_LOOKAHEAD_NORMAL
        }
        return GlowWordRenderProfile(
            renderHints = renderHints,
            lineRenderEndTime = RenderHints.getLineRenderEndTime(line),
            lineTransitionMode = renderHints?.lineTransitionMode ?: LineTransitionMode.NORMAL,
            wordRevealMode = wordRevealMode,
            wordLookahead = wordLookahead,
        )
    }

    /**
     * 对应 Folia 行 60–65 的 getGlowWordActiveEndTime。
     * instant 整行一起亮；fast 至少亮 0.12s 但不越过行渲染结束；normal 到词本身结束。
     */
    fun getGlowWordActiveEndTime(word: Word, profile: GlowWordRenderProfile): Double =
        when (profile.wordRevealMode) {
            WordRevealMode.INSTANT -> profile.lineRenderEndTime
            WordRevealMode.FAST ->
                minOf(profile.lineRenderEndTime, maxOf(word.endTime, word.startTime + MIN_DISPLAY_FAST))
            WordRevealMode.NORMAL -> word.endTime
        }

    /** 对应 Folia 行 67–77 的 getGlowWordDisplayDuration。 */
    fun getGlowWordDisplayDuration(word: Word, profile: GlowWordRenderProfile): Double {
        val minDuration = when (profile.wordRevealMode) {
            WordRevealMode.INSTANT -> MIN_DISPLAY_INSTANT
            WordRevealMode.FAST -> MIN_DISPLAY_FAST
            WordRevealMode.NORMAL -> MIN_DISPLAY_NORMAL
        }
        return maxOf(getGlowWordActiveEndTime(word, profile) - word.startTime, minDuration)
    }

    /**
     * 对应 Folia 行 79–117 的 getGlowWordLineContainerMotion。
     *
     * none：进入即就位，离场只做极轻的放大 + 6px 模糊；
     * fast：0.35→1 淡入、0.96→1 放大、4px→0 清晰，0.16s；
     * normal：0→1、0.9→1、10px→0，离场 0→1.1 倍 + 20px 模糊，0.3s。
     */
    fun getGlowWordLineContainerMotion(profile: GlowWordRenderProfile): LineContainerMotion =
        when (profile.lineTransitionMode) {
            LineTransitionMode.NONE -> LineContainerMotion(
                initialOpacity = 1.0, initialScale = 1.0, initialBlurPx = 0.0,
                animateDuration = 0.0, animateEase = "linear",
                exitOpacity = 0.0, exitScale = 1.02, exitBlurPx = 6.0,
                exitDuration = 0.12, exitEase = "easeOut",
            )

            LineTransitionMode.FAST -> LineContainerMotion(
                initialOpacity = 0.35, initialScale = 0.96, initialBlurPx = 4.0,
                animateDuration = 0.16, animateEase = "easeOut",
                exitOpacity = 0.0, exitScale = 1.04, exitBlurPx = 10.0,
                exitDuration = 0.16, exitEase = "easeInOut",
            )

            LineTransitionMode.NORMAL -> LineContainerMotion(
                initialOpacity = 0.0, initialScale = 0.9, initialBlurPx = 10.0,
                animateDuration = 0.0, animateEase = "linear",
                exitOpacity = 0.0, exitScale = 1.1, exitBlurPx = 20.0,
                exitDuration = 0.3, exitEase = "linear",
            )
        }
}