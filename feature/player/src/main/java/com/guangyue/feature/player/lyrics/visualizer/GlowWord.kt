// 对应 Folia 文件：
//   src/components/visualizer/GlowWord.tsx（逐词三态、辉光层 + 正文层 + 合唱涟漪）
//   src/components/visualizer/wordGlow.ts（辉光半径与逐字扫光时序）
//   src/components/visualizer/glowWordVariants.ts（布局层与正文层的目标值、弹簧）
//   src/components/visualizer/glowWordTiming.ts（lookahead / activeEnd / 最短显示 / 行容器入离场）
//
// 手机化要点（同段会写进 docs/ui-v0/report.md）：
//  1. 三态由播放进度驱动：时间来自 [VisualizerClock]（withFrameNanos 推进），在 derivedStateOf 里读，
//     只有「三态」这一离散值变化才通知重组——等价 Folia 的「仅 status 变化时 setState」，
//     不对整行每帧重组。
//  2. 辉光用「同字两遍模糊文本」叠加实现：内圈 0.4*r1 @ alpha 0.7、外圈 0.4*r2 @ alpha 1.0，
//     就是 Folia wordGlow.ts 在 Linux 默认走的 drop-shadow 量化路径（text-shadow 会打爆 Chromium
//     glyph cache，Folia 自己也退到了这条）；半径按时序变化只重走 draw/layer 阶段。
//  3. 逐字扫光（normal 且词内字数 >1）按 Folia 的 charDuration*6 / times[0,0.3,1] / delay=字起点-词起点
//     逐字推进；单字（CJK）走 peaks[0,1,1] 持续到词尾。
//  4. 弹簧：spring(stiffness=200, dampingRatio=20/(2√200)≈0.7071067811865476)，推导见 VisualizerKernel.kt。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.GlowWordConfig
import com.guangyue.core.lyrics.GlowWordRenderProfile
import com.guangyue.core.lyrics.GlowWordStatus
import com.guangyue.core.lyrics.GlowWordTiming
import com.guangyue.core.lyrics.GlowWordVariants
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Word
import com.guangyue.core.lyrics.WordGlow
import com.guangyue.core.lyrics.WordRevealMode

/**
 * 一个词：布局层（位移 / 缩放 / 旋转 / 透明度）+ 辉光层 + 正文层。
 *
 * @param word 词及其起止时间（秒）
 * @param config 逐词布局配置（来自 ClassicLayout.buildLineLayout）
 * @param profile 渲染档案（lookahead / activeEnd / 揭示模式），来自 GlowWordTiming
 * @param clock 播放时钟：逐帧推进，但消费方只在离散值变化时重组
 * @param activeColor 高亮色（关键词着色之后的结果）
 * @param baseColor 基色（主题 primaryColor，即正文默认色）
 * @param intensity 动画强度（决定 passed 透明度 0.82 / 0.9）
 * @param enableWordRotation 字词旋转开关（流光调参之一）
 * @param fontSizeSp 正文字号（sp）
 * @param fontFamily 字体族（流光用衬线）
 * @param reduceMotion 系统关闭动画 / 静态截图模式：直接切状态、不做辉光与涟漪动画
 * @param isChorus 合唱行：active 时叠一圈涟漪
 */
@Composable
fun GlowWord(
    word: Word,
    config: GlowWordConfig,
    profile: GlowWordRenderProfile,
    clock: VisualizerClock,
    activeColor: Color,
    baseColor: Color,
    intensity: AnimationIntensity,
    enableWordRotation: Boolean,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    reduceMotion: Boolean = false,
    isChorus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val style = remember(fontSizeSp, fontFamily) {
        TextStyle(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.22f).sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
        )
    }

    // ---- 三态：只在状态变化时通知重组（等价 Folia 的 setStatus 分支） ----------------------
    val status by remember(word, profile, reduceMotion) {
        derivedStateOf {
            val t = clock.seconds()
            if (reduceMotion) {
                // 减少动效：不吃 lookahead 提前量，直接落到最终态。
                when {
                    t < word.startTime -> GlowWordStatus.WAITING
                    t <= GlowWordTiming.getGlowWordActiveEndTime(word, profile) -> GlowWordStatus.ACTIVE
                    else -> GlowWordStatus.PASSED
                }
            } else {
                GlowWordVariants.resolveStatus(t, word, profile)
            }
        }
    }

    val layout = GlowWordVariants.layoutState(status, config, intensity, enableWordRotation)
    val spring = activeSpring
    val active = status == GlowWordStatus.ACTIVE
    val passed = status == GlowWordStatus.PASSED
    val general = if (active) spring else tween(secondsToMillis(layout.durationSeconds))

    val x by animateFloatAsState(layout.x.toFloat(), general, label = "gw-x")
    val y by animateFloatAsState(layout.y.toFloat(), general, label = "gw-y")
    val scale by animateFloatAsState(layout.scale.toFloat(), general, label = "gw-scale")
    val rotate by animateFloatAsState(
        targetValue = layout.rotate.toFloat(),
        animationSpec = when {
            // passed：Folia 让旋转单独跑 5s 线性漂移（rotate: { duration: 5, ease: 'linear' }）
            passed -> tween(secondsToMillis(layout.rotateDurationSeconds), easing = kernelLinearEasing)
            active -> spring
            else -> tween(secondsToMillis(layout.durationSeconds))
        },
        label = "gw-rotate",
    )
    val opacity by animateFloatAsState(
        targetValue = layout.opacity.toFloat(),
        animationSpec = if (active) tween(secondsToMillis(GlowWordVariants.ACTIVE_OPACITY_DURATION)) else general,
        label = "gw-opacity",
    )

    // ---- 正文层颜色：active 线性过渡到高亮色（duration 默认 0.2），passed 回基色 -------------
    val displaySeconds = GlowWordTiming.getGlowWordDisplayDuration(word, profile)
    val body = GlowWordVariants.bodyState(status, profile.wordRevealMode, displaySeconds)
    val bodyTarget = if (body.color == "__ACTIVE__") activeColor else baseColor
    val bodyColor by animateColorAsState(
        targetValue = bodyTarget,
        animationSpec = tween(secondsToMillis(body.colorDurationSeconds), easing = easeByName(body.colorEase)),
        label = "gw-body",
    )

    // 正文模糊：waiting 10px → active 0（0.08/0.12/0.2）；passed 维持 0。
    val bodyBlur by animateFloatAsState(
        targetValue = body.blurPx.toFloat(),
        animationSpec = tween(secondsToMillis(body.filterDurationSeconds), easing = easeByName(body.filterEase)),
        label = "gw-blur",
    )

    // ---- 辉光强度：由播放时间求值；用 derivedStateOf 只在强度值变化时重组那一层 ----------------
    val glowStrength by remember(word, profile, reduceMotion) {
        derivedStateOf {
            glowStrengthAt(clock.seconds(), word, profile, reduceMotion)
        }
    }

    Box(modifier = modifier.graphicsLayer {
        translationX = x
        translationY = y
        scaleX = scale
        scaleY = scale
        rotationZ = rotate
        alpha = opacity
    }) {
        // 辉光层：内圈 + 外圈两遍模糊文本（对应 drop-shadow 的 0.4*r1@0.7 与 0.4*r2@1.0）
        if (glowStrength > 0.002f && !reduceMotion) {
            val radii = WordGlow.glowRadii(profile.wordRevealMode)
            val perChar = remember(word) { GraphemeTimings.buildWordGraphemeTimings(word) }
            if (perChar.size > 1) {
                // 逐字扫光：每个字自己的强度（delay = 字起点 - 词起点）
                androidx.compose.foundation.layout.Row {
                    perChar.forEach { g ->
                        val s = glowStrengthAt(
                            time = clock.seconds(),
                            word = word,
                            profile = profile,
                            reduceMotion = false,
                            charStartTime = g.startTime,
                            charEndTime = g.endTime,
                        )
                        GlowLayerText(
                            text = g.char,
                            style = style,
                            color = activeColor,
                            strength = s,
                            innerRadiusCss = radii.first,
                            outerRadiusCss = radii.second,
                        )
                    }
                }
            } else {
                GlowLayerText(
                    text = word.text,
                    style = style,
                    color = activeColor,
                    strength = glowStrength,
                    innerRadiusCss = radii.first,
                    outerRadiusCss = radii.second,
                )
            }
        }

        // 正文层：颜色线性过渡 + waiting 模糊
        Text(
            text = word.text,
            style = style.copy(color = bodyColor),
            maxLines = 1,
            modifier = if (bodyBlur > 0.05f) {
                Modifier.blur(bodyBlur.dp, BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            },
        )

        // 合唱涟漪
        if (isChorus && active) {
            ChorusRipple(
                color = activeColor,
                clock = clock,
                word = word,
                enabled = !reduceMotion,
            )
        }
    }
}

/**
 * 辉光层的一遍模糊文本。内圈半径 0.4·r1、透明度乘 0.7；外圈 0.4·r2、透明度 1.0。
 * 两者叠在同一位置，像素差与 Folia 的 text-shadow(r1, r2) 路径 ≤1/255（Folia 文件头已论证）。
 */
@Composable
private fun GlowLayerText(
    text: String,
    style: TextStyle,
    color: Color,
    strength: Float,
    innerRadiusCss: Double,
    outerRadiusCss: Double,
) {
    val innerDp = (innerRadiusCss * WordGlow.DROP_SHADOW_RADIUS_SCALE).toFloat().dp
    val outerDp = (outerRadiusCss * WordGlow.DROP_SHADOW_RADIUS_SCALE).toFloat().dp
    Box(contentAlignment = Alignment.Center) {
        // 外圈（半径大、透明度满）：先在底下铺开
        Text(
            text = text,
            style = style,
            maxLines = 1,
            color = color.copy(alpha = strength * 1.0f),
            modifier = Modifier.blur(outerDp, BlurredEdgeTreatment.Unbounded),
        )
        // 内圈（半径小、透明度 0.7）：把中心提亮，形成「芯 + 晕」两层
        Text(
            text = text,
            style = style,
            maxLines = 1,
            color = color.copy(alpha = strength * WordGlow.DROP_SHADOW_INNER_ALPHA.toFloat()),
            modifier = Modifier.blur(innerDp, BlurredEdgeTreatment.Unbounded),
        )
    }
}

/**
 * 合唱涟漪：对应 Folia GlowWord.tsx 的 `h-[150%] aspect-square rounded-full border-1`
 * （initial scale 0.2 / opacity 0.8 → scale 1.5~3.5 / opacity 0，0.5s easeOut）。
 */
@Composable
private fun ChorusRipple(
    color: Color,
    clock: VisualizerClock,
    word: Word,
    enabled: Boolean,
) {
    // 缩放终值：Folia 用一次 useState/useMemo 固定为 1.5 + random*2；这里按词文本哈希取一个稳定值，
    // 保证同一词每次重组、每次滚动回来都一致（截图可复现）。
    val target = remember(word) {
        val h = word.text.fold(0) { acc, c -> acc * 31 + c.code }
        GlowWordVariants.rippleScale(0.5 + ((h and 0xFFFF) % 1000) / 2000.0)
    }
    val progress by remember(word, enabled) {
        derivedStateOf {
            if (!enabled) 1.0
            else ((clock.seconds() - word.startTime) / GlowWordVariants.RIPPLE_DURATION).coerceIn(0.0, 1.0)
        }
    }
    val s = (GlowWordVariants.RIPPLE_INITIAL_SCALE + (target - GlowWordVariants.RIPPLE_INITIAL_SCALE) * progress)
    val o = GlowWordVariants.RIPPLE_INITIAL_OPACITY * (1.0 - progress)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = s.toFloat()
                scaleY = s.toFloat()
                alpha = o.toFloat()
            },
    )
}

/**
 * 当前时刻某词的辉光强度（0..1）。
 *
 * 直接消费内核 [WordGlow.variantFor] 的关键帧与时序：
 *  - instant / fast：peaks[0,1,0]，times[0,0.35,1] / [0,0.4,1]
 *  - normal 且词内字数 >1：peaks[0,1,0]，times[0,0.3,1]，时长 charDuration*6，delay = 字起点-词起点
 *  - normal 单字 / CJK：peaks[0,1,1]，times[0,0.9,1]
 *  - passed：半径与强度同步淡出（0.12/0.22/0.9）
 *
 * @param charStartTime 逐字层传入的该字起点（秒）；整词层传 null
 * @param charEndTime 逐字层传入的该字终点（秒）
 */
private fun glowStrengthAt(
    time: Double,
    word: Word,
    profile: GlowWordRenderProfile,
    reduceMotion: Boolean,
    charStartTime: Double? = null,
    charEndTime: Double? = null,
): Float {
    val status = if (reduceMotion) {
        when {
            time < word.startTime -> GlowWordStatus.WAITING
            time <= GlowWordTiming.getGlowWordActiveEndTime(word, profile) -> GlowWordStatus.ACTIVE
            else -> GlowWordStatus.PASSED
        }
    } else {
        GlowWordVariants.resolveStatus(time, word, profile)
    }
    val mode = profile.wordRevealMode
    val displaySeconds = GlowWordTiming.getGlowWordDisplayDuration(word, profile)
    val total = GraphemeTimings.splitLyricGraphemes(word.text).size

    if (status == GlowWordStatus.WAITING) return 0f

    // passed：辉光淡出，半径同期收到 0（Folia passedVariant）。
    if (status == GlowWordStatus.PASSED) {
        val fade = when (mode) {
            WordRevealMode.INSTANT -> 0.12
            WordRevealMode.FAST -> 0.22
            WordRevealMode.NORMAL -> 0.9
        }
        val activeEnd = GlowWordTiming.getGlowWordActiveEndTime(word, profile)
        val local = (time - activeEnd).coerceAtLeast(0.0)
        return (1.0 - local / fade).coerceIn(0.0, 1.0).toFloat()
    }

    // active：用内核给出的变体（含 times / peaks / delay / duration）求值。
    val variant = WordGlow.variantFor(
        status = status,
        color = "#FFFFFF",
        mode = mode,
        duration = displaySeconds,
        index = null,
        total = total,
        // 逐字层才有逐字时间；整词层不带，让内核按「无逐字时间」的公式回退。
        charStartTime = charStartTime,
        charEndTime = charEndTime,
        wordStartTime = if (charStartTime != null) word.startTime else null,
    )
    // 内核里 keyframes 是可空的（三态都有 transition，但只有部分态带 keyframes）。
    val keyframes = variant.keyframes ?: return 0f
    if (reduceMotion) return keyframes.peaks.lastOrNull()?.toFloat() ?: 1f

    val delay = variant.transition.delay
    val elapsed = time - word.startTime - delay
    if (elapsed <= 0.0) return 0f

    val duration = if (variant.transition.duration > 0.0) variant.transition.duration else 0.1
    if (mode == WordRevealMode.NORMAL && total > 1 && charStartTime != null) {
        // 逐字扫光：每个字按住自己的时长闪一下（charDuration*6 已由内核给出）。
        val local = elapsed / duration
        if (local >= 1.0) return keyframes.peaks.lastOrNull()?.toFloat() ?: 0f
        return interpolatePeaks(local, variant.transition.times, keyframes.peaks)
    }

    val t = (time - word.startTime) / duration
    if (t >= 1.0) return keyframes.peaks.lastOrNull()?.toFloat() ?: 1f
    if (t <= 0.0) return keyframes.peaks.firstOrNull()?.toFloat() ?: 0f
    return interpolatePeaks(t, variant.transition.times, keyframes.peaks)
}

/** 按 times 归一化位置在 peaks 上做线性插值（对应 framer-motion 关键帧的默认线性段）。 */
private fun interpolatePeaks(t: Double, times: List<Double>, peaks: List<Double>): Float {
    if (times.size < 2 || peaks.size < 2) return peaks.lastOrNull()?.toFloat() ?: 0f
    for (i in 0 until times.size - 1) {
        val t0 = times[i]
        val t1 = times[i + 1]
        if (t in t0..t1) {
            val local = if (t1 - t0 <= 0.0) 1.0 else (t - t0) / (t1 - t0)
            val p0 = peaks.getOrElse(i) { peaks.last() }
            val p1 = peaks.getOrElse(i + 1) { peaks.last() }
            return (p0 + (p1 - p0) * local).toFloat()
        }
    }
    return peaks.lastOrNull()?.toFloat() ?: 0f
}

/** 行容器入离场 → Compose 规格（glowWordTiming.getGlowWordLineContainerMotion）。 */
internal data class LineContainerSpec(
    val enterOpacity: Float,
    val enterScale: Float,
    val enterBlurDp: Float,
    val exitOpacity: Float,
    val exitScale: Float,
    val exitBlurDp: Float,
    val enterMillis: Int,
    val exitMillis: Int,
    val enterEase: androidx.compose.animation.core.Easing,
    val exitEase: androidx.compose.animation.core.Easing,
)

/**
 * 把内核的行容器 motion 换成 Compose 的入离场参数（含 none / fast / normal 三档）。
 *
 * 内核 [LineContainerMotion] 是扁平字段（initialOpacity / animateDuration / exitScale …），
 * 与 Folia 的嵌套结构（initial / animate / exit）是同一份数值的两种铺法。
 */
internal fun lineContainerSpec(profile: GlowWordRenderProfile): LineContainerSpec {
    val m = GlowWordTiming.getGlowWordLineContainerMotion(profile)
    return LineContainerSpec(
        enterOpacity = m.initialOpacity.toFloat(),
        enterScale = m.initialScale.toFloat(),
        enterBlurDp = m.initialBlurPx.toFloat(),
        exitOpacity = m.exitOpacity.toFloat(),
        exitScale = m.exitScale.toFloat(),
        exitBlurDp = m.exitBlurPx.toFloat(),
        enterMillis = secondsToMillis(m.animateDuration),
        exitMillis = secondsToMillis(m.exitDuration),
        enterEase = easeByName(m.animateEase),
        exitEase = easeByName(m.exitEase),
    )
}

/** 手机适配：一个词的触控目标不小于 48dp（需求「触控 ≥48dp」）。 */
internal val WordTouchTargetDp = 48f

/** 流光调参在渲染层的入口（样式层用它取「词距 / 旋转 / 呼吸浮动」）。 */
internal fun ClassicTuning.renderSpacing(): Double = wordSpacing