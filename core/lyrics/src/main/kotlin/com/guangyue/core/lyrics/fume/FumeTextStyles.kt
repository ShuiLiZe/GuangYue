// 对应 Folia 文件：src/components/visualizer/fume/fumeTextStyle.ts、fumeBlockFrame.ts
//
// 「浮名」的文字三态与已读淡出：
//  - waiting（还没唱到）/ active（正在打印）/ passed（唱过了）；
//  - passed 之后按 textHoldRatio 决定是「留淡影」还是「基本收干净」，
//    两者之间用 cross-fade 过渡（对应 Folia 的 standard / dimmed 快照叠加）；
//  - 块要不要画由 [FumeTextStyles.isBlockOnScreen] 先筛一遍。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints

/** 已读之后画面的保留程度。对应 Folia 的 'standard' | 'dimmed'。 */
enum class FumeTextHoldStyle { STANDARD, DIMMED }

/** 块的三态（null 态用 [NONE] 表示：正在打印中）。 */
enum class FumeStaticState { WAITING, NONE, PASSED }

/** 对应 resolvePassedTextStyle 的返回值。 */
data class FumePassedTextStyle(
    val opacity: Double,
    val glowMultiplier: Double,
    val shadowAlphaBase: Double,
    val shadowAlphaTrail: Double,
)

/** 对应 resolveFumeGlowBases 的返回值。 */
data class FumeGlowBases(val activeGlowBoost: Double, val passedGlowBase: Double)

/** 对应 resolveFumeBlockTiming 的返回值。 */
data class FumeBlockTiming(
    val waitingOpacity: Double,
    val activeOpacity: Double,
    val effectiveTextHoldStyle: FumeTextHoldStyle,
    val passedStyle: FumePassedTextStyle,
    val transitionPassedStyle: FumePassedTextStyle,
    val baselineOffset: Double,
    val lineEndTime: Double,
    val linePassCutoffTime: Double,
    val hasRevealCompleted: Boolean,
    val hasPassCutoffReached: Boolean,
    val lineDuration: Double,
    val colorTrailDuration: Double,
    val staticState: FumeStaticState,
)

object FumeTextStyles {
    /** 对应 resolvePassedTextStyle：standard 留一层淡影 + 一点辉光，dimmed 基本收干净。 */
    fun passedTextStyle(variant: FumeVariant, holdStyle: FumeTextHoldStyle): FumePassedTextStyle =
        if (holdStyle == FumeTextHoldStyle.DIMMED) {
            FumePassedTextStyle(
                opacity = if (variant == FumeVariant.HERO) 0.11 else 0.075,
                glowMultiplier = 0.0,
                shadowAlphaBase = 0.0,
                shadowAlphaTrail = 0.0,
            )
        } else {
            FumePassedTextStyle(
                opacity = if (variant == FumeVariant.HERO) 0.74 else 0.58,
                glowMultiplier = 1.0,
                shadowAlphaBase = 0.1,
                shadowAlphaTrail = 0.16,
            )
        }

    /**
     * 对应 resolvePassedDimAmount：已读后「变暗」到什么程度。
     * 参数不合法或 fadeDuration<=0 一律按 1（=全暗），与 Folia 的兜底一致。
     */
    fun passedDimAmount(currentTime: Double, passedAt: Double, fadeDuration: Double): Double {
        if (!currentTime.isFinite() || !passedAt.isFinite() || !fadeDuration.isFinite() || fadeDuration <= 0.0) {
            return 1.0
        }
        val passedAge = maxOf(currentTime - passedAt, 0.0)
        return FumeMath.easeInCubic(FumeMath.clamp(passedAge / fadeDuration, 0.0, 1.0))
    }

    /**
     * 对应 resolveFumePassedFadeDuration：
     * textHoldRatio>=1 表示歌词一直留着（无限淡出时长），否则用整首时长乘比例，夹在 [2.4, 130]。
     */
    fun passedFadeDuration(lines: List<Line>, textHoldRatio: Double): Double {
        if (textHoldRatio >= 1.0) return Double.POSITIVE_INFINITY
        val timed = lines
            .map { it.startTime to RenderHints.getLineRenderEndTime(it) }
            .filter { (start, end) -> start.isFinite() && end.isFinite() && end >= start }
            .sortedBy { it.first }
        if (timed.size <= 1) return FumeMath.clamp(8.0 * textHoldRatio, 2.4, 130.0)
        val totalDuration = maxOf(timed.last().second - timed.first().first, 0.0)
        return FumeMath.clamp(totalDuration * textHoldRatio, 2.4, 130.0)
    }

    /** 对应 resolveFumeGlowBases：动画越强，辉光越冲。 */
    fun glowBases(intensity: AnimationIntensity, glowIntensity: Double): FumeGlowBases {
        val activeFactor = when (intensity) {
            AnimationIntensity.CHAOTIC -> 1.15
            AnimationIntensity.CALM -> 0.72
            else -> 0.92
        }
        val passedFactor = when (intensity) {
            AnimationIntensity.CHAOTIC -> 0.95
            AnimationIntensity.CALM -> 0.35
            else -> 0.62
        }
        return FumeGlowBases(activeFactor * glowIntensity, passedFactor * glowIntensity)
    }

    /** 对应 isFumeBlockOnScreen：块在屏幕外就整块跳过（overscan 让边缘不闪）。 */
    fun isBlockOnScreen(block: FumeBlock, camera: FumeCameraTarget, viewport: FumeViewport, overscan: Double = 180.0): Boolean {
        val screenLeft = viewport.width * 0.5 + (block.x - camera.x) * camera.scale
        val screenTop = viewport.height * 0.5 + (block.y - camera.y) * camera.scale
        val screenRight = screenLeft + block.width * camera.scale
        val screenBottom = screenTop + block.height * camera.scale
        val left = -overscan
        val top = -overscan
        val right = viewport.width + overscan
        val bottom = viewport.height + overscan
        return screenRight > left && screenLeft < right && screenBottom > top && screenTop < bottom
    }

    /** 对应 resolveFumeBlockTiming：把「这一刻这一块该长什么样」算清楚。 */
    fun resolveBlockTiming(
        block: FumeBlock,
        currentTime: Double,
        nextLineStartTime: Double?,
        textHoldRatio: Double,
    ): FumeBlockTiming {
        val line = block.line
        val hero = block.variant == FumeVariant.HERO
        val effectiveHoldStyle = if (textHoldRatio >= 1.0) FumeTextHoldStyle.STANDARD else FumeTextHoldStyle.DIMMED
        val lineEndTime = RenderHints.getLineRenderEndTime(line)
        val linePassCutoffTime = FumeReveal.linePassCutoffTime(line, nextLineStartTime)
        val lineDuration = maxOf(lineEndTime - line.startTime, 0.18)
        val colorTrailDuration = FumeMath.clamp(
            lineDuration * (if (hero) 0.42 else 0.52), 0.45, 1.45,
        )
        val staticState = when {
            currentTime < line.startTime -> FumeStaticState.WAITING
            currentTime >= lineEndTime + colorTrailDuration -> FumeStaticState.PASSED
            else -> FumeStaticState.NONE
        }
        return FumeBlockTiming(
            waitingOpacity = if (hero) 0.06 else 0.035,
            activeOpacity = if (hero) 0.985 else 0.92,
            effectiveTextHoldStyle = effectiveHoldStyle,
            passedStyle = passedTextStyle(block.variant, effectiveHoldStyle),
            transitionPassedStyle = passedTextStyle(block.variant, FumeTextHoldStyle.STANDARD),
            baselineOffset = block.lineHeight * (if (FumeMath.isCjk(line.fullText)) 0.52 else 0.5),
            lineEndTime = lineEndTime,
            linePassCutoffTime = linePassCutoffTime,
            hasRevealCompleted = currentTime >= line.endTime,
            hasPassCutoffReached = currentTime >= linePassCutoffTime,
            lineDuration = lineDuration,
            colorTrailDuration = colorTrailDuration,
            staticState = staticState,
        )
    }

    /** 对应 resolvePassedTextStyle 之外的一步：passed 阶段辉光模糊半径。 */
    fun passedShadowBlur(block: FumeBlock, passedStyle: FumePassedTextStyle, passedGlowBase: Double): Double =
        (2.0 + block.fontPx * 0.1) * 0.65 * passedGlowBase * passedStyle.glowMultiplier

    /**
     * 对应 resolveFumeStaticLayers：已读块画两层（standard 在下、dimmed 在上）做 cross-fade。
     * 返回空列表表示这块不该按静态层画（交给 live 绘制）。
     */
    fun staticLayerAlphas(
        timing: FumeBlockTiming,
        currentTime: Double,
        passedFadeDuration: Double,
        overviewTextRestoreProgress: Double,
    ): List<Pair<FumeTextHoldStyle, Float>> {
        if (timing.staticState == FumeStaticState.WAITING) {
            return listOf(FumeTextHoldStyle.STANDARD to 1f)
        }
        if (timing.staticState != FumeStaticState.PASSED) return emptyList()
        if (timing.effectiveTextHoldStyle != FumeTextHoldStyle.DIMMED) {
            return listOf(FumeTextHoldStyle.STANDARD to 1f)
        }
        val passedAt = timing.lineEndTime + timing.colorTrailDuration
        val dimAmount = passedDimAmount(currentTime, passedAt, passedFadeDuration) *
            (1.0 - overviewTextRestoreProgress)
        return when {
            dimAmount <= 0.0 -> listOf(FumeTextHoldStyle.STANDARD to 1f)
            dimAmount >= 1.0 -> listOf(FumeTextHoldStyle.DIMMED to 1f)
            else -> listOf(
                FumeTextHoldStyle.STANDARD to (1.0 - dimAmount).toFloat(),
                FumeTextHoldStyle.DIMMED to dimAmount.toFloat(),
            )
        }
    }
}