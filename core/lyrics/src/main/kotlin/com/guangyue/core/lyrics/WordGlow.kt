// 对应 Folia 文件：src/components/visualizer/wordGlow.ts（行 1–156）
//
// 逐字辉光：半径、关键帧（含逐字扫光 / 单字持续 / passed 淡出）与分层结构。
// Folia 输出的是 CSS filter/text-shadow 字符串；Kotlin 侧只保留数值与结构，
// 由 Compose 层（RenderEffect / drawText 阴影）消费。
package com.guangyue.core.lyrics

/** 一帧辉光关键帧：opacity 序列 + 每帧对应的内/外半径倍率。对应 Folia glowKeyframes。 */
data class GlowKeyframes(
    val color: String?,
    val peaks: List<Double>,
    /** peaks[i] 对应的内半径（px），null 表示该帧不动辉光。 */
    val innerRadii: List<Double?>,
    val outerRadii: List<Double?>,
)

/** 辉光动画时序。对应 Folia wordGlowVariants 的 transition。 */
data class GlowTransition(
    val duration: Double,
    /** 关键帧时间位置（0–1）。 */
    val times: List<Double>,
    val ease: String,
    val delay: Double = 0.0,
)

/** 辉光动画关键帧 + 时序。对应 Folia wordGlowVariants[status] 的返回值。 */
data class GlowVariant(
    val color: String?,
    val opacity: Double,
    val keyframes: GlowKeyframes?,
    /** Folia 三帧都有 transition；waiting 用 duration=0 表示不过渡。 */
    val transition: GlowTransition,
)

/** 心智不同的 lineTransition 档次：与 Folia 相同的三档。 */
object WordGlow {

    /** Folia 行 24：DROP_SHADOW_RADIUS_SCALE。 */
    const val DROP_SHADOW_RADIUS_SCALE = 0.4

    /** Folia 行 25：DROP_SHADOW_INNER_ALPHA。 */
    const val DROP_SHADOW_INNER_ALPHA = 0.7

    /** Folia 行 30–39：glowRadii(mode) = instant [14,24] / fast [18,32] / else [20,40]。 */
    fun glowRadii(mode: WordRevealMode): Pair<Double, Double> = when (mode) {
        WordRevealMode.INSTANT -> 14.0 to 24.0
        WordRevealMode.FAST -> 18.0 to 32.0
        WordRevealMode.NORMAL -> 20.0 to 40.0
    }

    /** Folia 行 41–46：glowKeyframes（只保留数值结构）。 */
    fun glowKeyframes(
        color: String?,
        inner: Double,
        outer: Double,
        peaks: List<Double>,
    ): GlowKeyframes = GlowKeyframes(
        color = color,
        peaks = peaks,
        innerRadii = peaks.map { inner * it },
        outerRadii = peaks.map { outer * it },
    )

    /** Folia 行 50–56：waiting 帧（恒透明）。 */
    val waitingVariant: GlowVariant = GlowVariant(
        color = "transparent",
        opacity = 0.0,
        keyframes = null,
        transition = GlowTransition(duration = 0.0, times = emptyList(), ease = "linear"),
    )

    /**
     * Folia 行 60–133：active 帧。
     *
     * @param duration 词的显示时长（getGlowWordDisplayDuration）
     * @param index 逐字渲染时的字序号
     * @param total 逐字渲染时的字数；<=1 走单字/CJK 分支
     * @param charStartTime / charEndTime / wordStartTime 有逐字时间时传入，否则为 null（走均分）
     */
    fun activeVariant(
        color: String?,
        mode: WordRevealMode,
        duration: Double,
        index: Int? = null,
        total: Int? = null,
        charStartTime: Double? = null,
        charEndTime: Double? = null,
        wordStartTime: Double? = null,
    ): GlowVariant {
        val (inner, outer) = glowRadii(mode)

        if (mode == WordRevealMode.INSTANT) {
            // 闪烁一下再落回：peaks [0,1,0]
            val peaks = listOf(0.0, 1.0, 0.0)
            return GlowVariant(
                color = color,
                opacity = 1.0,
                keyframes = glowKeyframes(color, inner, outer, peaks),
                transition = GlowTransition(
                    duration = minOf(if (duration == 0.0) 0.08 else duration, 0.12),
                    times = listOf(0.0, 0.35, 1.0),
                    ease = "easeOut",
                ),
            )
        }

        if (mode == WordRevealMode.FAST) {
            val peaks = listOf(0.0, 1.0, 0.0)
            return GlowVariant(
                color = color,
                opacity = 1.0,
                keyframes = glowKeyframes(color, inner, outer, peaks),
                transition = GlowTransition(
                    duration = minOf(maxOf(if (duration == 0.0) 0.12 else duration, 0.12), 0.2),
                    times = listOf(0.0, 0.4, 1.0),
                    ease = "easeInOut",
                ),
            )
        }

        // normal
        val count = total ?: 1
        if (count > 1) {
            // 逐字扫光：每个字按住自己的时长闪一下
            val singleDuration = duration / count
            val hasCharTiming = charStartTime != null && charEndTime != null && wordStartTime != null
            val charDuration = if (hasCharTiming) maxOf(charEndTime - charStartTime, 0.001) else singleDuration
            val charDelay = if (hasCharTiming) maxOf(0.0, charStartTime - wordStartTime) else singleDuration * (index ?: 0)
            val peaks = listOf(0.0, 1.0, 0.0)
            return GlowVariant(
                color = color,
                opacity = 1.0,
                keyframes = glowKeyframes(color, inner, outer, peaks),
                transition = GlowTransition(
                    duration = charDuration * 6,
                    times = listOf(0.0, 0.3, 1.0),
                    ease = "easeInOut",
                    delay = charDelay,
                ),
            )
        }

        // 单字 / CJK：一直亮到词结束
        val peaks = listOf(0.0, 1.0, 1.0)
        return GlowVariant(
            color = color,
            opacity = 1.0,
            keyframes = glowKeyframes(color, inner, outer, peaks),
            transition = GlowTransition(
                duration = if (duration == 0.0) 0.1 else duration,
                times = listOf(0.0, 0.9, 1.0),
                ease = "easeInOut",
            ),
        )
    }

    /**
     * Folia 行 140–156：passed 帧（辉光淡出，半径归零）。
     * Folia 返回 `{ color: activeColor, opacity: 0, filter: dropShadowAt(activeColor, 0, 0) }`，
     * 即颜色仍是高亮色、半径收到 0；淡出时长 instant 0.12 / fast 0.22 / normal 0.9。
     */
    fun passedVariant(activeColor: String?, mode: WordRevealMode): GlowVariant {
        val fallback = when (mode) {
            WordRevealMode.INSTANT -> 0.12
            WordRevealMode.FAST -> 0.22
            WordRevealMode.NORMAL -> 0.9
        }
        val (inner, outer) = glowRadii(mode)
        return GlowVariant(
            color = activeColor,
            opacity = 0.0,
            // 半径收到 0（等价 dropShadowAt(activeColor, 0, 0)）
            keyframes = GlowKeyframes(
                color = activeColor,
                peaks = listOf(0.0),
                innerRadii = listOf(0.0),
                outerRadii = listOf(0.0),
            ),
            transition = GlowTransition(duration = fallback, times = emptyList(), ease = "easeOut"),
        ).also { /* inner/outer 仅用于保持 API 完整 */ }
    }

    /** 便利：按状态取辉光帧。 */
    fun variantFor(
        status: GlowWordStatus,
        color: String?,
        mode: WordRevealMode,
        duration: Double,
        index: Int? = null,
        total: Int? = null,
        charStartTime: Double? = null,
        charEndTime: Double? = null,
        wordStartTime: Double? = null,
    ): GlowVariant = when (status) {
        GlowWordStatus.WAITING -> waitingVariant
        GlowWordStatus.ACTIVE -> activeVariant(
            color, mode, duration, index, total, charStartTime, charEndTime, wordStartTime
        )
        GlowWordStatus.PASSED -> passedVariant(color, mode)
    }
}

/** GlowWord 三态。对应 Folia GlowWord.tsx 的 status。 */
enum class GlowWordStatus { WAITING, ACTIVE, PASSED }