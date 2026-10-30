package com.guangyue.music.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Folia 动效令牌。
 *
 * ## 弹簧：stiffness / damping(kg·s⁻¹) → Compose Spring
 *
 * Folia 的 framer-motion 弹簧写作 `{ stiffness: 300, damping: 30, mass: 1 }`
 * （`src/components/app/carousel/Carousel3D.tsx:42,59`；播放条 `FloatingPlayerControls.tsx:38` 用 280，
 * `usePlayerBottomBarBottomPx.ts:46` 用 `{280, 28}`）。framer-motion 的 stiffness 单位是 N/m（= kg/s²），
 * damping 是 N·s/m（= kg/s），mass 是 kg，与 Compose 的 `Spring.DampingRatio*` / `stiffness`
 * 底层是同一个简谐振动模型：
 *
 *   ω₀ = √(k / m)                       （自然角频率, rad/s）
 *   c_c = 2·√(k·m)                      （临界阻尼系数）
 *   ζ   = c / c_c = c / (2·√(k·m))      （阻尼比 = Compose 的 dampingRatio）
 *
 * k=300, c=30, m=1 → ω₀ = √300 ≈ 17.32 rad/s，c_c = 2·√300 ≈ 34.64 → **ζ = 30/34.64 ≈ 0.866**（欠阻尼，轻微回弹）；
 * k=280, c=28 → ζ = 28/(2·√280) = 0.837；k=280, c=28 → 同上。
 * Compose 的 `spring(stiffness = …)` 直接收的就是 N/m 量级的 stiffness，因此 Folia 的
 * stiffness **原样传入**，只需把 damping 换算成 ζ。
 *
 * ## 时长 / 曲线
 *
 * `src/components/modal/settings/kit/motion.ts:12-38`：
 *  - KIT_MOTION：fast 0.12s、move 0.22s、unfold 0.3s、show 0.48s（flash 2.2s / tooltipDelay 0.4s /
 *    confirm 1.6s 是等待时长，不是过渡，不进令牌）。
 *  - KIT_EASE：out `cubic-bezier(.22,1,.36,1)`、standard `cubic-bezier(.4,0,.2,1)`、
 *    inOut `cubic-bezier(.65,0,.35,1)`。
 * CSS 的 cubic-bezier(x1,y1,x2,y2) 与 Compose `CubicBezierEasing(a,b,c,d)` 一一对应，直接数组传入。
 */
@Immutable
object FoliaMotion {

    // ---- 弹簧（换算见类注释） -----------------------------------------------------------

    /** Carousel3D.tsx:42,59 的 `{stiffness: 300, damping: 30}` → ζ = 0.866。卡片 / 页面滑动。 */
    val springCard = spring<Float>(dampingRatio = dampingRatio(30f, 300f), stiffness = 300f)

    /** FloatingPlayerControls.tsx:38 的 `{stiffness: 280, damping: 28}` → ζ = 0.837。 */
    val springPlayer = spring<Float>(dampingRatio = dampingRatio(28f, 280f), stiffness = 280f)

    /** 按压缩放 0.97 用同一套弹簧（Button.tsx:11 `transition-… scale`）。 */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = dampingRatio(30f, 300f),
        stiffness = 300f,
    )

    /**
     * framer-motion 的 damping → Compose dampingRatio。
     *
     * @param damping N·s/m（framer 的 damping）
     * @param stiffness N/m（framer 的 stiffness），mass 取 1（Folia 未指定 mass，默认 1kg）
     */
    fun dampingRatio(damping: Float, stiffness: Float, mass: Float = 1f): Float =
        damping / (2f * kotlin.math.sqrt(stiffness * mass))

    // ---- 时长 ---------------------------------------------------------------------------

    /** motion.ts:15 `fast: 0.12` → tween(120ms)。颜色 / 透明度微过渡。 */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(120, easing = easeOut)

    /** motion.ts:16 `move: 0.22` → tween(220ms)。位移。 */
    fun <T> move(): FiniteAnimationSpec<T> = tween(220, easing = easeOut)

    /** motion.ts:17 `unfold: 0.3` → tween(300ms)。展开。 */
    fun <T> unfold(): FiniteAnimationSpec<T> = tween(300, easing = easeOut)

    /** motion.ts:18 `show: 0.48` → tween(480ms)。整体出现。 */
    fun <T> show(): FiniteAnimationSpec<T> = tween(480, easing = easeOut)

    // ---- 曲线（motion.ts:31-38） --------------------------------------------------------

    /** `KIT_EASE.out = [0.22, 1, 0.36, 1]`（motion.ts:32）。 */
    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** `KIT_EASE.standard = [0.4, 0, 0.2, 1]`（motion.ts:33）。 */
    val easeStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** `KIT_EASE.inOut = [0.65, 0, 0.35, 1]`（motion.ts:34）。 */
    val easeInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    // ---- 常量 ---------------------------------------------------------------------------

    /** hover → 按下时的缩放（Button.tsx:11 `enabled:active:scale-[0.97]`、Chips.tsx:33）。 */
    const val PRESS_SCALE = 0.97f

    /** 滑条拇指按下放大到 1.15（kit.css:26-32 `:active scale(1.15)`）。 */
    const val THUMB_ACTIVE_SCALE = 1.15f

    /** 位移入场的距离：kit.css:151-160 `.st-page-in { translateY(6px) }`。 */
    const val PAGE_IN_OFFSET_DP = 6f

    /** 弹层入场缩放：kit.css:128-148 `scale(.98)`。 */
    const val POP_SCALE = 0.98f
}

/** 让「按压缩放」可以被 `.graphicsLayer` 直接使用的小工具，避免每个组件重复写弹簧。 */
internal fun <T> pressSpec(): FiniteAnimationSpec<T> = FoliaMotion.press()
