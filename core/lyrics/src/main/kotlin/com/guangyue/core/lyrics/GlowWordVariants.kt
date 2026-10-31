// 对应 Folia 文件：
//   src/components/visualizer/glowWordVariants.ts（行 1–77，布局层与正文层的 variants）
//   src/components/visualizer/GlowWord.tsx（行 1–137，三态判定与结构）
//
// GlowWord 的三态（waiting/active/passed）：布局层（位置/缩放/旋转）与正文层（颜色/模糊）。
// Folia 用 framer-motion variants；Kotlin 侧只保留目标值与时序，
// 由 Compose 层用 Animatable / animateFloatAsState 消费。
package com.guangyue.core.lyrics

/** 布局层目标值。对应 Folia buildGlowWordLayoutVariants 的每个状态返回值。 */
data class GlowWordLayoutState(
    val opacity: Double,
    val scale: Double,
    val x: Double,
    val y: Double,
    val rotate: Double,
    /** 通用时长（秒）；active 走弹簧时为 0，表示见 spring 字段。 */
    val durationSeconds: Double,
    /** 旋转专用时长（passed 的 5s 线性漂移）；0 表示与通用时长一致。 */
    val rotateDurationSeconds: Double = 0.0,
    val rotateEase: String = "linear",
    /** 透明度专用时长（active 的 0.1s）；0 表示与通用时长一致。 */
    val opacityDurationSeconds: Double = 0.0,
    val spring: SpringSpec? = null,
)

/** 弹簧参数。对应 Folia 的 type:'spring', stiffness:200, damping:20。 */
data class SpringSpec(
    val stiffness: Double,
    val damping: Double,
    val mass: Double = 1.0,
) {
    /**
     * 换算成 Compose 的 dampingRatio：ζ = c / (2 * sqrt(k * m))。
     * c=20, k=200, m=1 → ζ = 20 / (2 * sqrt(200)) = 0.7071067811865476（≈ 1/√2，微回弹）。
     */
    val dampingRatio: Double
        get() = damping / (2.0 * kotlin.math.sqrt(stiffness * mass))

    /** Compose 的 SpringSpec 参数（stiffness / dampingRatio）。 */
    fun toComposeDampingRatio(): Float = dampingRatio.toFloat()
}

/** 正文层目标值。对应 Folia glowWordBodyVariants。 */
data class GlowWordBodyState(
    val color: String,
    val blurPx: Double,
    val colorDurationSeconds: Double,
    val colorEase: String,
    val filterDurationSeconds: Double,
    val filterEase: String,
)

/** 对应 Folia glowWordVariants.ts。 */
object GlowWordVariants {

    /** 对应 Folia active 弹簧的 stiffness 与 damping。 */
    const val ACTIVE_SPRING_STIFFNESS = 200.0
    const val ACTIVE_SPRING_DAMPING = 20.0
    const val ACTIVE_SPRING_MASS = 1.0

    /** 对应 Folia active 的 opacity 时长 0.1。 */
    const val ACTIVE_OPACITY_DURATION = 0.1

    /** 对应 Folia waiting 的时长 0.4。 */
    const val WAITING_DURATION = 0.4

    /** 对应 Folia passed 的时长 0.5 与旋转时长 5。 */
    const val PASSED_DURATION = 0.5
    const val PASSED_ROTATE_DURATION = 5.0

    /** passed 透明度：chaotic 0.9，其余 0.82。 */
    fun passedOpacity(intensity: AnimationIntensity): Double =
        if (intensity == AnimationIntensity.CHAOTIC) 0.9 else 0.82

    /**
     * 布局层状态。对应 Folia buildGlowWordLayoutVariants。
     *
     * @param config 逐词布局配置（classic 样式来自 ClassicLayout.buildLineLayout）
     * @param intensity 动画强度（决定 passed 透明度）
     * @param enableWordRotation 字词旋转开关
     */
    fun layoutState(
        status: GlowWordStatus,
        config: GlowWordConfig,
        intensity: AnimationIntensity,
        enableWordRotation: Boolean = true,
    ): GlowWordLayoutState = when (status) {
        GlowWordStatus.WAITING -> GlowWordLayoutState(
            opacity = 0.0,
            scale = 0.5,
            // Folia: x: config.x + Math.sin(config.y) * 100，y: config.y + Math.cos(config.x) * 50
            x = config.x + kotlin.math.sin(config.y) * 100.0,
            y = config.y + kotlin.math.cos(config.x) * 50.0,
            rotate = if (enableWordRotation) config.rotate + 20.0 else 0.0,
            durationSeconds = WAITING_DURATION,
        )

        GlowWordStatus.ACTIVE -> GlowWordLayoutState(
            opacity = 1.0,
            // Folia: scale: isNaN(config.scale) ? 1.5 : config.scale * 1.4
            scale = if (config.scale.isNaN()) 1.5 else config.scale * 1.4,
            x = config.x,
            y = config.y,
            rotate = config.rotate,
            durationSeconds = 0.0,
            opacityDurationSeconds = ACTIVE_OPACITY_DURATION,
            spring = SpringSpec(ACTIVE_SPRING_STIFFNESS, ACTIVE_SPRING_DAMPING, ACTIVE_SPRING_MASS),
        )

        GlowWordStatus.PASSED -> GlowWordLayoutState(
            opacity = passedOpacity(intensity),
            scale = config.scale,
            x = config.x,
            y = config.y,
            rotate = config.rotate + config.passedRotate,
            durationSeconds = PASSED_DURATION,
            rotateDurationSeconds = PASSED_ROTATE_DURATION,
            rotateEase = "linear",
        )
    }

    /** waiting 正文层：基色 + 10px 模糊，0.4s。 */
    val waitingBodyState = GlowWordBodyState(
        color = "__BASE__", blurPx = 10.0,
        colorDurationSeconds = 0.4, colorEase = "linear",
        filterDurationSeconds = 0.4, filterEase = "linear",
    )

    /**
     * 正文层状态。对应 Folia glowWordBodyVariants。
     *
     * active：颜色从基色线性过渡到高亮色（duration，默认 0.2），模糊按档位 0.08/0.12/0.2 清掉；
     * passed：颜色回基色（0.12/0.24/0.8，easeInOut），模糊回 0（0.12/0.2/0.5）。
     */
    fun bodyState(
        status: GlowWordStatus,
        mode: WordRevealMode,
        duration: Double,
    ): GlowWordBodyState = when (status) {
        GlowWordStatus.WAITING -> waitingBodyState

        GlowWordStatus.ACTIVE -> GlowWordBodyState(
            color = "__ACTIVE__",
            blurPx = 0.0,
            colorDurationSeconds = if (duration == 0.0) 0.2 else duration,
            colorEase = "linear",
            filterDurationSeconds = when (mode) {
                WordRevealMode.INSTANT -> 0.08
                WordRevealMode.FAST -> 0.12
                WordRevealMode.NORMAL -> 0.2
            },
            filterEase = "linear",
        )

        GlowWordStatus.PASSED -> GlowWordBodyState(
            color = "__BASE__",
            blurPx = 0.0,
            colorDurationSeconds = when (mode) {
                WordRevealMode.INSTANT -> 0.12
                WordRevealMode.FAST -> 0.24
                WordRevealMode.NORMAL -> 0.8
            },
            colorEase = "easeInOut",
            filterDurationSeconds = when (mode) {
                WordRevealMode.INSTANT -> 0.12
                WordRevealMode.FAST -> 0.2
                WordRevealMode.NORMAL -> 0.5
            },
            filterEase = "linear",
        )
    }

    /**
     * 三态判定。对应 Folia GlowWord.tsx 行 62–71 的 useMotionValueEvent 回调：
     *   latest >= word.startTime - wordLookahead && latest <= activeEndTime -> active
     *   latest > activeEndTime -> passed
     *   否则 waiting
     * Folia 只在状态变化时 setState；Compose 侧同样只在变化时触发重组。
     */
    fun resolveStatus(currentTime: Double, word: Word, profile: GlowWordRenderProfile): GlowWordStatus {
        val activeEndTime = GlowWordTiming.getGlowWordActiveEndTime(word, profile)
        return when {
            currentTime >= word.startTime - profile.wordLookahead && currentTime <= activeEndTime -> GlowWordStatus.ACTIVE
            currentTime > activeEndTime -> GlowWordStatus.PASSED
            else -> GlowWordStatus.WAITING
        }
    }

    /**
     * 合唱涟漪的缩放终值。对应 Folia GlowWord.tsx 行 47：
     *   rippleScale = 1.5 + Math.random() * 2（一次 useState/useMemo，固定不变）
     * 调用方传入随机数（测试可注入），保证同一词每次重组缩放一致。
     */
    fun rippleScale(random01: Double): Double = 1.5 + random01.coerceIn(0.0, 1.0) * 2.0

    /** 涟漪起始值（Folia initial: { scale: 0.2, opacity: 0.8 }）与时长 0.5 easeOut。 */
    const val RIPPLE_INITIAL_SCALE = 0.2
    const val RIPPLE_INITIAL_OPACITY = 0.8
    const val RIPPLE_DURATION = 0.5

    /** 行容器透明度/缩放/模糊的关键帧时长（glowWordTiming.getGlowWordLineContainerMotion）。 */
    fun lineContainerMotion(profile: GlowWordRenderProfile): LineContainerMotion =
        GlowWordTiming.getGlowWordLineContainerMotion(profile)
}