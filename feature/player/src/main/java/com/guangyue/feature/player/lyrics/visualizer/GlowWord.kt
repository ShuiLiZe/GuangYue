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
//  2. 辉光只对「字形轮廓」做高斯模糊：外圈 0.4*r2 @ alpha 1.0、内圈 0.4*r1 @ alpha 0.7，
//     语义等同 Folia wordGlow.ts 的 drop-shadow；半径按时序变化只重走 draw/layer 阶段。
//     不能用 Modifier.blur：它先把节点光栅化成实心矩形再模糊，字盒会露矩形底（审图第 3 条）。
//  3. 逐字扫光（normal 且词内字数 >1）按 Folia 的 charDuration*6 / times[0,0.3,1] / delay=字起点-词起点
//     逐字推进；单字（CJK）走 peaks[0,1,1] 持续到词尾。
//  4. 弹簧：spring(stiffness=200, dampingRatio=20/(2√200)≈0.7071067811865476)，推导见 VisualizerKernel.kt。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.BlurMaskFilter
import android.text.TextPaint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.guangyue.core.lyrics.GraphemeTiming
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
    // V0d 取证：非 null 时把辉光强度钉死（debug harness 用，release 恒为 null）。
    glowStrengthOverride: Float? = null,
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
    // 等待态照 Folia glowWordVariants.waiting：整词 opacity 0 + scale 0.5 + sin/cos 漂移 ——
    // 但那一套是给「舞台只画当前行」的入场动画准备的；广乐舞台同源，等待中的字还没登场，
    // 本来就不该看见（主控 m00570 已确认：保持 Folia，不要改透明度）。
    val layout = GlowWordVariants.layoutState(status, config, intensity, enableWordRotation)
    val spring = activeSpring
    val active = status == GlowWordStatus.ACTIVE
    val general = if (active) spring else tween(secondsToMillis(layout.durationSeconds))

    // V0d：Folia 的布局层（x/y/scale/rotate/opacity）是 framer-motion 的 motion.div，动画只改
    // transform 与 opacity 这两个合成属性。广乐原来用 animateFloatAsState，每个属性都是一次
    // 快照读，100ms 内连推 60 帧就够把整棵树重组十几遍——排版每帧重跑，字形反复重贴图，
    // 硬件层被反复重建，画面上就出现一闪一闪的矩形底/色块。
    // 现在四个几何量在 draw 阶段读动画值（`graphicsLayer` 的 lambda 只在绘制时执行），
    // 只有 opacity 仍然走组合阶段（它要驱动辉光强度，见下面的 glowStrength）。
    val x by animateFloatAsState(layout.x.toFloat(), general, label = "gw-x")
    val y by animateFloatAsState(layout.y.toFloat(), general, label = "gw-y")
    val scale by animateFloatAsState(layout.scale.toFloat(), general, label = "gw-scale")
    val rotate by animateFloatAsState(
        targetValue = layout.rotate.toFloat(),
        animationSpec = when {
            // passed：Folia 让旋转单独跑 5s 线性漂移（rotate: { duration: 5, ease: 'linear' }）
            status == GlowWordStatus.PASSED ->
                tween(secondsToMillis(layout.rotateDurationSeconds), easing = kernelLinearEasing)
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
    val bodyFinal = when {
        active -> activeColor
        status == GlowWordStatus.PASSED -> baseColor
        else -> baseColor
    }

    // 正文模糊：waiting 10px → active 0（0.08/0.12/0.2）；passed 维持 0。
    val bodyBlur by animateFloatAsState(
        targetValue = body.blurPx.toFloat(),
        animationSpec = tween(secondsToMillis(body.filterDurationSeconds), easing = easeByName(body.filterEase)),
        label = "gw-blur",
    )

    // ---- 辉光强度：由播放时间求值；用 derivedStateOf 只在强度值变化时重组那一层 ----------------
    // 注意：必须读 `clock.seconds()` 这个 State 本身，不能包在 derivedStateOf 里再读，
    // 否则快照系统只跟踪内层读取，外层 `glowStrength` 不会在播放时间推进时失效，
    // 辉光会永远停在第一次算出的强度（截图里表现为一块静止的斜矩形底）。
    val glowStrength = glowStrengthOverride ?: glowStrengthAt(clock.seconds(), word, profile, reduceMotion)

    // 逐字扫光（normal 且词内字数 >1）：每个字按自己的时序求强度；其余情况整词一个强度。
    // 在组合阶段算好再以 List 传进绘制层——draw lambda 里不读 clock，绘制阶段就不再有快照依赖。
    val graphemes = remember(word) { GraphemeTimings.buildWordGraphemeTimings(word) }
    val perCharGlow = graphemes.size > 1 &&
        profile.wordRevealMode == WordRevealMode.NORMAL &&
        !reduceMotion
    val charStrengths = if (perCharGlow) {
        val t = clock.seconds()
        graphemes.map { g ->
            glowStrengthAt(
                time = t,
                word = word,
                profile = profile,
                reduceMotion = false,
                charStartTime = g.startTime,
                charEndTime = g.endTime,
            )
        }
    } else {
        emptyList()
    }

    Box(modifier = modifier.graphicsLayer {
        // 位移照 Folia `glowWordVariants.ts` 的 layoutVariants：x/y 一律走 transform
        // （CSS `translate` → `graphicsLayer.translationX/Y`），**不参与布局**。
        // 这是 flex-wrap 能成立的前提：词再多也只会换行，不会被位移挤出父容器。
        translationX = x
        translationY = y
        scaleX = scale
        scaleY = scale
        alpha = opacity
        rotationZ = rotate
    }) {
        // 正文层：颜色线性过渡 + waiting 模糊。透明度完全照 Folia glowWordVariants.body：
        // waiting 是 baseColor + blur 10px，整词不可见由外层 opacity 0 负责；active 过渡到高亮色；
        // passed 回到 baseColor（外层 opacity 0.82）。不要再叠自定义 alpha，否则 passed 会「消失」。
        //
        // V0d：waiting 的 10px 模糊不再用 `Modifier.blur`。那一版会把整个 Text 节点先光栅化成
        // 一块不透明矩形再模糊，矩形底比字形先亮起来（V0c 审图第 3 条的矩形来源之一）。
        // 现在把正文和辉光画在同一块画布上：先画两圈模糊光晕（Folia 的 text-shadow），
        // 再按 0.82 的 passed 透明度叠正文；waiting 的模糊用 `TextPaint.maskFilter`（只作用于字形），
        // 于是任何状态下都不存在「矩形像素块」。
        WordInk(
            word = word,
            style = style,
            color = bodyColor,
            // 光晕颜色 = 高亮色（Folia 的 text-shadow 用 activeColor），与正文当前颜色无关。
            glowColor = activeColor,
            glowStrength = glowStrength,
            chars = if (perCharGlow) graphemes else emptyList(),
            charStrengths = charStrengths,
            blurPx = bodyBlur,
            profile = profile,
            reduceMotion = reduceMotion,
            bodyAlpha = if (status == GlowWordStatus.PASSED) PASSED_WORD_OPACITY else 1f,
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
 * 一个词的「光晕 + 正文」，画在同一块画布上（画布 = 词盒）。
 *
 * 层序照 Folia：光晕在下、正文在上。正文也用 drawText 画而不是叠一个 Text 子节点，
 * 这样正文与光晕共用同一份 [TextLayoutResult]，字位像素级对齐。
 *
 * @param glowStrength 整词强度（0..1），**只乘到 alpha** —— Folia 的光晕半径固定，只用 opacity 渐入渐出
 * @param chars 逐字时序（逐字扫光时用）；为空表示整词一次画完
 * @param charStrengths 与 [chars] 一一对应的逐字强度
 * @param blurPx 正文模糊半径（px）：waiting 10px → active 0。用 maskFilter 做，只糊字形不出矩形。
 * @param bodyAlpha 正文层透明度（passed 0.82 / 其余 1.0）。
 */
@Composable
private fun WordInk(
    word: Word,
    style: TextStyle,
    color: Color,
    glowColor: Color,
    glowStrength: Float,
    chars: List<GraphemeTiming>,
    charStrengths: List<Float>,
    blurPx: Float,
    bodyAlpha: Float,
    profile: GlowWordRenderProfile,
    reduceMotion: Boolean,
) {
    val wordText = remember(word) { word.text }
    val measurer = rememberTextMeasurer()
    val layout = remember(wordText, style) {
        measurer.measure(
            text = AnnotatedString(wordText),
            style = style,
            maxLines = 1,
            softWrap = false,
        )
    }
    val density = LocalDensity.current
    val radii = WordGlow.glowRadii(profile.wordRevealMode)
    val innerPx = with(density) { (radii.first * WordGlow.DROP_SHADOW_RADIUS_SCALE).toFloat().dp.toPx() }
    val outerPx = with(density) { (radii.second * WordGlow.DROP_SHADOW_RADIUS_SCALE).toFloat().dp.toPx() }
    val bodyArgb = color.toArgb()
    val glowArgb = glowColor.toArgb()
    // 字号：sp → px（需要 fontScale，所以借助当前 density）。
    // 字位全部来自 TextLayoutResult；这里只把像素字号交给 TextPaint。
    val textSizePx = with(density) { style.fontSize.toPx() }
    val baseline = layout.getLineBaseline(0).toFloat()
    val originX = layout.getBoundingBox(0).left

    // V0d 修复：这里必须是一个真正的布局节点。原来写成裸的 `Modifier.fillMaxSize()` 表达式，
    // WordInk 一个节点都没产出（Compose 把它当丢弃值），当前行整行空白、连 onDrawBehind 都不执行。
    // 尺寸 = 实测字形盒（不是 fillMaxSize）：FlowRow 里每个词必须按自身宽度参与排版，
    // 否则一个词就吃掉整行宽度，flex-wrap 变成「一词一行」。
    // 光晕照 text-shadow 语义溢出词盒，不裁也不占布局空间。
    val wordWidthDp = with(density) { layout.size.width.toDp() }
    val wordHeightDp = with(density) { layout.size.height.toDp() }
    Box(
        modifier = Modifier
            .size(wordWidthDp, wordHeightDp)
            .drawWithCache {
            // 光晕要能溢出词盒（text-shadow 本就不受盒边界拘束），这里刻意不裁。
            val outer = TextPaint().apply {
                isAntiAlias = true
                textSize = textSizePx
                this.color = glowArgb
                maskFilter = BlurMaskFilter(outerPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            val inner = TextPaint().apply {
                isAntiAlias = true
                textSize = textSizePx
                this.color = glowArgb
                maskFilter = BlurMaskFilter(innerPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            val body = TextPaint().apply {
                isAntiAlias = true
                textSize = textSizePx
                this.color = bodyArgb
            }
            onDrawBehind {
                val canvas = drawContext.canvas.nativeCanvas
                if (!reduceMotion && glowStrength > 0.002f) {
                    drawTextShadow(
                        canvas = canvas,
                        layout = layout,
                        chars = chars,
                        charStrengths = charStrengths,
                        inner = inner,
                        outer = outer,
                        wordText = wordText,
                        baseline = baseline,
                        originX = originX,
                        alphaScale = bodyAlpha,
                    )
                }
                // 正文：waiting 走 maskFilter 的 10px 模糊（只糊字形），active 起清晰。
                body.alpha = (255 * bodyAlpha).toInt().coerceIn(0, 255)
                body.maskFilter = if (blurPx > 0.05f) {
                    BlurMaskFilter(blurPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
                } else {
                    null
                }
                canvas.drawText(wordText, originX, baseline, body)
            }
        },
    )
}

/**
 * Folia 的两层 text-shadow（`0 0 inner` + `0 0 outer`），只画光晕、不画实心字。
 *
 * 模糊源是字形本身（maskFilter），全程没有 saveLayer / PorterDuff.CLEAR / 字盒矩形，
 * 所以不会出现 V0c 那种整块色块。强度只乘 alpha，半径恒定 —— 与 Folia wordGlow.ts 一致。
 */
private fun drawTextShadow(
    canvas: android.graphics.Canvas,
    layout: TextLayoutResult,
    chars: List<GraphemeTiming>,
    charStrengths: List<Float>,
    inner: TextPaint,
    outer: TextPaint,
    wordText: String,
    baseline: Float,
    originX: Float,
    alphaScale: Float,
) {
    val baseInnerAlpha = (255 * WordGlow.DROP_SHADOW_INNER_ALPHA).toInt().coerceIn(0, 255)
    fun applyStrength(s: Float) {
        val a = (s * alphaScale).coerceIn(0f, 1f)
        outer.alpha = (255 * a).toInt().coerceIn(0, 255)
        inner.alpha = (baseInnerAlpha * a).toInt().coerceIn(0, 255)
    }

    if (chars.isEmpty() || charStrengths.size != chars.size) {
        // 整词一次画完：字位用 Compose 量出的首字左边界 + 单词 maxLines=1 的基线。
        applyStrength(if (charStrengths.isEmpty()) 1f else charStrengths.first())
        canvas.drawText(wordText, originX, baseline, outer)
        canvas.drawText(wordText, originX, baseline, inner)
        return
    }
    for (index in chars.indices) {
        val s = charStrengths[index]
        if (s <= 0.002f) continue
        applyStrength(s)
        val grapheme = chars[index].char
        if (grapheme.isBlank()) continue
        val left = if (index == 0) originX else layout.getBoundingBox(index).left
        canvas.drawText(grapheme, left, baseline, outer)
        canvas.drawText(grapheme, left, baseline, inner)
    }
}

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
        // 逐字层已经按「字起点 - 词起点」给了 delay，不能再叠词级 delay（内核 transition.delay
        // 对逐字 keyframes 也是字级偏移），否则「还没唱到的字」会提前亮起并被模糊成色块。
        val charLocal = (time - word.startTime - delay) / duration
        if (charLocal <= 0.0) return 0f
        if (charLocal >= 1.0) return keyframes.peaks.lastOrNull()?.toFloat() ?: 0f
        return interpolatePeaks(charLocal, variant.transition.times, keyframes.peaks)
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
// Folia glowWordVariants.ts：passed 布局 opacity = chaotic 0.9 / normal 0.82。
internal const val PASSED_WORD_OPACITY = 0.82f

// 审图第 2 条：waiting（当前行未唱字）在列表里必须可读。
internal val WordTouchTargetDp = 48f

/** 流光调参在渲染层的入口（样式层用它取「词距 / 旋转 / 呼吸浮动」）。 */
internal fun ClassicTuning.renderSpacing(): Double = wordSpacing
