package com.guangyue.feature.player.screen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * U4 · 播放页/迷你条的动效参数（`feature:player` 版）。
 *
 * 数源自 Folia `FloatingPlayerControls.tsx:36-40` 的 `CONTROL_LAYOUT_SPRING`
 * （`{type:'spring', stiffness:280, damping:24}`）与 `usePlayerBottomBarBottomPx.ts:46`
 * 的 `SUBTITLE_PRESENCE_SPRING`（`{stiffness:280, damping:28}`）；时长与曲线取自 Folia
 * `components/modal/settings/kit/motion.ts`（0.12 / 0.22 / 0.3 / 0.48 s + `[0.22,1,0.36,1]`）。
 *
 * `feature:player` 不能依赖 `app` 模块（`FoliaMotion` 在 app 里），所以这里保留一份
 * **数值完全一致**的电话版：同一个 stiffness/damping 换算成 Compose 的 `dampingRatio`
 * （`damping / 2√stiffness`），见 `docs/ui-u4/report.md` §3。
 */
object FoliaMotion {
    /** Folia 播放控件布局弹簧：stiffness 280 / damping 24 → ratio 0.717。 */
    fun <T> playerSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = dampingRatio(24f, 280f),
        stiffness = 280f,
    )

    /** Folia 展开/收起布局弹簧：stiffness 280 / damping 28 → ratio 0.837（更「沉」一点）。 */
    fun <T> layoutSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = dampingRatio(28f, 280f),
        stiffness = 280f,
    )

    /** `damping / (2 * sqrt(stiffness * mass))`，与 `app` 侧 `FoliaMotion.dampingRatio` 同式。 */
    fun dampingRatio(damping: Float, stiffness: Float, mass: Float = 1f): Float =
        damping / (2f * kotlin.math.sqrt(stiffness * mass))

    /** motion.ts:15 `fast: 0.12`。 */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(120, easing = easeOut)

    /** motion.ts:16 `move: 0.22`。 */
    fun <T> move(): FiniteAnimationSpec<T> = tween(220, easing = easeOut)

    /** motion.ts:17 `unfold: 0.3`。 */
    fun <T> unfold(): FiniteAnimationSpec<T> = tween(300, easing = easeOut)

    /** motion.ts:18 `show: 0.48`。 */
    fun <T> show(): FiniteAnimationSpec<T> = tween(480, easing = easeOut)

    /** `KIT_EASE.out = [0.22, 1, 0.36, 1]`。 */
    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}
