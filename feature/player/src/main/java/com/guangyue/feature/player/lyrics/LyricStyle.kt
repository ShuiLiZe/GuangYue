package com.guangyue.feature.player.lyrics

/**
 * V1a：歌词渲染样式。
 *
 * 对应 Folia `src/components/visualizer/registry.tsx` 的 `VISUALIZER_REGISTRY`：那边是一张
 * 「id → 渲染器」的表，手机端先落两项：
 *
 * - [CLASSIC]：「流光」（Folia classic。zh-CN.ts:915 `visualizerClassic`）；
 * - [FUME]：「浮名」（Folia fume。zh-CN.ts `ui.visualizerFume`）。
 *
 * [id] 是**落盘用的稳定串**：`core/data` 的偏好层不能依赖 `feature:player`，所以那边只存
 * String（默认 "classic"），解析（`String? → LyricStyle`）由这一层负责，枚举本身留在 feature 侧。
 *
 * 注意：这里的 id 只用于持久化，**不要**当文案显示；界面上显示的中文一律走
 * `app/src/main/res/values/strings.xml` 的资源 id（`lyrics_style_classic` / `lyrics_style_fume`）。
 */
enum class LyricStyle(val id: String) {
    /** 「流光」：Folia classic 渲染器（逐词布局 / 逐字辉光 / 呼吸浮动）。 */
    CLASSIC("classic"),

    /** 「浮名」：Folia fume 渲染器（V1a 只接样式切换与持久化，渲染由 fume-draw 那一层落）。 */
    FUME("fume"),

    /** 「云阶」：Folia partita 渲染器（V2a：当前句切 chunk 左右错落 + 引导线，词仍是 GlowWord）。 */
    PARTITA("partita"),

    /** 「心象」：Folia cadenza 渲染器（V2b：整句拆词散落、主词居中放大、碰撞避让）。Folia 无参数面板。 */
    CADENZA("cadenza"),

    /** 「静止」：Folia still（V2c：三行静态居中 + 暗角，不做动画，最省资源）。无参数。 */
    STILL("still"),

    /** 「倾诉」：Folia tilt（V2d：长句断成 1–4 段，随机一段斜体强调，逐字脉动）。 */
    TILT("tilt"),
    ;

    companion object {
        /**
         * 由落盘串还原样式；未知 / 空 / 非法值一律回落 [CLASSIC]。
         *
         * 与 Folia registry 的默认行为一致：用户偏好里的样式被删掉（或手改坏）时，
         * 界面要开得起来，落到默认渲染器而不是崩在解析上。
         */
        fun fromId(id: String?): LyricStyle {
            val key = id?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == key } ?: CLASSIC
        }
    }
}