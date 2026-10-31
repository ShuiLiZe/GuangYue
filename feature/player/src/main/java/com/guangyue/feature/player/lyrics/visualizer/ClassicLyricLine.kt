// 对应 Folia 文件：
//   src/components/visualizer/classic/Visualizer.tsx（行 124–348：逐词布局、呼吸浮动、行容器）
//   src/components/visualizer/classic/tuning.ts（字词旋转 / 呼吸浮动 / 词距）
//   src/components/visualizer/GlowWord.tsx（每个词由 GlowWord 渲染）
//
// 流光（classic）样式的行渲染：把内核算出来的逐词布局（ClassicLineLayout）落到 Compose。
//
// 手机化改动（逐条写进 docs/ui-v0/report.md）：
//  1. Folia 是一次只画「当前行」的全屏画布（70vh + max-w-6xl + perspective）。手机上保留
//     上下句上下文（U5 的列表习惯），只有当前行走这条逐词画布——上下句仍用 U5 的档位样式，
//     这样点行跳转、空态三态、自动跟随都能原样保留。
//  2. 词的宽度用 Compose 的 TextMeasurer 实测（对应 Folia 的 canvas measureText），
//     测量失败才退到内核的 measureWordWidthFallback（text.length*px*0.65）。
//  3. CSS `perspective` 在 Compose 没有等价物，本段不做 3D 透视（旋转仍是 Z 轴），
//     其它 spread / rotate / scale / marginRight 全部照内核数值走。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicLayout
import com.guangyue.core.lyrics.ClassicLayout.BreathingKeyframes
import com.guangyue.core.lyrics.ClassicLineLayout
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.GlowWordTiming
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.LineAlign
import com.guangyue.core.lyrics.Word
import com.guangyue.core.lyrics.WordAlign

/**
 * 当前行：逐词画布。词与词之间用内核算出的右边距，整行做呼吸浮动。
 *
 * @param line 当前行（已带 renderHints）
 * @param clock 播放时钟（逐帧推进；词组件只在三态变化时重组）
 * @param tuning 流光调参（旋转开关 / 呼吸浮动倍率 / 词距）
 * @param intensity 动画强度（calm / normal / chaotic）
 * @param reduceMotion 系统关闭动画：不做呼吸浮动与辉光动画
 * @param viewportWidthPx 视口宽度（px），用于字号 clamp 与词宽测量
 * @param lyricsFontScale 歌词字号倍率（跟系统字号走）
 * @param activeColor / baseColor 高亮色 / 基色（主题色经取色后）
 */
@Composable
internal fun ClassicLyricLine(
    line: Line,
    clock: VisualizerClock,
    tuning: ClassicTuning,
    intensity: AnimationIntensity,
    reduceMotion: Boolean,
    viewportWidthPx: Double,
    lyricsFontScale: Double,
    activeColor: Color,
    baseColor: Color,
    fontFamily: FontFamily,
    // V0d 取证：非 null 时把辉光强度钉死（debug harness 用）。
    glowStrengthOverride: Float? = null,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 布局：与 Folia 一样按 (行, 调参, 强度, 字号, 视口宽) 缓存；同一行不重复计算。
    val layout: ClassicLineLayout = remember(line, tuning, intensity, viewportWidthPx, lyricsFontScale, measurer) {
        val pxFontSize = ClassicLayout.pixelFontSize(lyricsFontScale, viewportWidthPx)
        val widths = line.words.map { word ->
            measureWordWidth(measurer, word.text, pxFontSize.toFloat(), density)
        }
        ClassicLayout.buildLineLayout(
            line = line,
            wordWidthsPx = widths,
            lyricsFontScale = lyricsFontScale,
            viewportWidthPx = viewportWidthPx,
            remPx = 16.0,
            tuning = tuning,
            intensity = intensity,
        )
    }

    val profile = remember(line) { GlowWordTiming.resolveGlowWordRenderProfile(line) }
    // 字号：px 传给辉光/测量层，sp 传给 Text（1.3 倍系统字号下与内核 clamp 一起生效）。
    val fontSizePx = layout.mainFontSizePx.toFloat()
    // sp 数值（float）：GlowWord/PlainWord 内部自己 `.sp`，避免 TextUnit 与 Float 来回转。
    val fontSizeSp = with(density) { fontSizePx.toSp() }.value

    // 呼吸浮动：y 与 scale 两组关键帧（Folia classic/Visualizer.tsx 行 277–303）。
    // multiplier <= 0 时内核返回 null，这里就不挂动效。
    val breathing = remember(layout) { ClassicLayout.breathingKeyframes(layout) }
    val floatMotion = breathingMotion(breathing, reduceMotion)
    val floatY = floatMotion.first
    val floatScale = floatMotion.second

    // 行距：Folia 一次只画当前行、没有行距概念；广乐把当前行插在滚动列表里，必须自己给
    // 「active 词放大 scale×1.4」预留垂直空间，否则放大的当前行会压到下一行（审图第 1 条）。
    // 行盒 = 字形高度 + 上下留白：所有词槽位都锁到同一行高，并且永远按「最大放大」预留，
    // 于是任一时刻拍下的画面里，放大后的当前行也不会碰到上一行/下一行。
    val rowMinHeight = with(density) {
        val lineHeightPx = fontSizePx * LINE_HEIGHT_RATIO
        val overflowPx = lineHeightPx * (ACTIVE_SCALE - 1f) / 2f
        (lineHeightPx + overflowPx * 2f + lineHeightPx * LINE_GAP_RATIO).toDp()
    }
    // 总宽 = 每个词宽 + 内核算出的 marginRight，至少和视口一样宽：这样词距拉满时也不会把
    // 最后一个词顶出屏幕（Folia classic 的行是 flex-wrap 居中排布，画布是整块 70vh）。
    val rowWidth = with(density) {
        val contentPx = line.words.indices.sumOf { index ->
            val label = line.words[index].text
            val w = measureWordWidth(measurer, label, fontSizePx, density)
            w + (layout.words.getOrNull(index)?.marginRight ?: 0.0)
        }
        maxOf(contentPx, viewportWidthPx).toFloat().toDp()
    }

    Row(
        modifier = modifier
            .widthIn(min = rowWidth)
            .heightIn(min = rowMinHeight)
            .graphicsLayer {
                translationY = floatY
                scaleX = floatScale
                scaleY = floatScale
            },
        verticalAlignment = verticalOf(layout.line.alignItems),
    ) {
        line.words.forEachIndexed { index, word ->
            val config = layout.words.getOrNull(index)
            if (config == null) {
                PlainWord(word, fontSizeSp, baseColor, fontFamily)
            } else {
                WordSlot(config = config, density = density) {
                    if (profile != null) {
                        GlowWord(
                            word = word,
                            config = config,
                            profile = profile,
                            clock = clock,
                            activeColor = activeColor,
                            baseColor = baseColor,
                            intensity = intensity,
                            enableWordRotation = tuning.enableWordRotation,
                            fontSizeSp = fontSizeSp,
                            fontFamily = fontFamily,
                            reduceMotion = reduceMotion,
                            isChorus = line.isChorus,
                            glowStrengthOverride = glowStrengthOverride,
                        )
                    } else {
                        PlainWord(word, fontSizeSp, baseColor, fontFamily)
                    }
                }
            }
        }
    }
}

/**
 * 一个词的槽位：右边距（内核算好的 `marginRight`，单位 px）+ alignSelf + 48dp 触控下限。
 *
 * 触控 ≥48dp 是需求硬指标：词本身可能很窄（CJK 单字约 0.9em），所以槽位最小高度取 48dp，
 * 宽度至少覆盖词宽，保证「点这一句」的可点区域不会小于无障碍下限。
 */
@Composable
private fun RowScope.WordSlot(
    config: com.guangyue.core.lyrics.GlowWordConfig,
    density: Density,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = with(density) { config.marginRight.toFloat().toDp() })
            // 每个词槽位锁到行高：词盒高度固定，active 放大时只在盒内溢出，不去挤压邻行。
            .fillMaxHeight(0.999f)
            .heightIn(min = WordTouchTargetDp.dp)
            .align(alignSelfOf(config.alignSelf)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---------------- 行盒常量 ----------------
// Folia GlowWord.tsx：lineHeight 1.22（与 GlowWord 内 TextStyle 一致）。
internal const val LINE_HEIGHT_RATIO = 1.22f

// Folia glowWordVariants.ts：active 布局 scale = config.scale * 1.4（空则 1.5）。
// 当前行插入滚动列表时必须按这个倍数预留垂直空间，否则放大的字会压到邻行（审图第 1 条）。
internal const val ACTIVE_SCALE = 1.4f

// 行与行之间再留 0.12em（Folia classic 的 minMargin 同款比例），保证放大后仍有余量。
internal const val LINE_GAP_RATIO = 0.12f
/** 兜底：纯文本渲染（不辉光）。只在拿不到渲染档案（行被清空等）时用，保证不崩。 */
@Composable
private fun PlainWord(
    word: Word,
    fontSizeSp: Float,
    baseColor: Color,
    fontFamily: FontFamily,
) {
    Text(
        text = word.text,
        color = baseColor,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = fontSizeSp.sp,
        modifier = Modifier.heightIn(min = WordTouchTargetDp.dp),
    )
}

/** 内核的 alignSelf → Compose 的行内竖直对齐（RowScope.align 只吃 Alignment.Vertical）。 */
internal fun alignSelfOf(align: WordAlign): Alignment.Vertical = when (align) {
    WordAlign.FLEX_START -> Alignment.Top
    WordAlign.CENTER -> Alignment.CenterVertically
    WordAlign.FLEX_END -> Alignment.Bottom
    WordAlign.AUTO -> Alignment.CenterVertically
}

/** 内核的行内对齐 → Compose 的竖直对齐。 */
internal fun verticalOf(align: LineAlign): Alignment.Vertical = when (align) {
    LineAlign.START -> Alignment.Top
    LineAlign.CENTER -> Alignment.CenterVertically
    LineAlign.END -> Alignment.Bottom
}

/**
 * 呼吸浮动：把内核算出的关键帧（`ClassicLayout.breathingKeyframes`，对应 Folia
 * `classic/Visualizer.tsx` 行 277–303 的 `animate y:[...] scale:[...]`、repeat Infinity、easeInOut）
 * 挂到 `rememberInfiniteTransition` 上，返回 (位移 y, 缩放)。
 *
 * `multiplier <= 0`（调参把呼吸浮动拉到 0）或系统关闭动画时返回 (0f, 1f)，不挂任何动效。
 */
@Composable
private fun breathingMotion(breathing: BreathingKeyframes?, reduceMotion: Boolean): Pair<Float, Float> {
    if (breathing == null || reduceMotion || breathing.ys.size < 2 || breathing.scales.size < 2) {
        return 0f to 1f
    }
    val transition = rememberInfiniteTransition(label = "breathing")
    val ys: List<Float> = breathing.ys.map { it.toFloat() }
    val scales: List<Float> = breathing.scales.map { it.toFloat() }
    val duration: Int = secondsToMillis(breathing.durationSeconds)
    val lastIndexY: Int = (ys.size - 1).coerceAtLeast(1)
    val lastIndexS: Int = (scales.size - 1).coerceAtLeast(1)
    val floatY by transition.animateFloat(
        initialValue = ys.first(),
        targetValue = ys.first(),
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = duration
                ys.forEachIndexed { index: Int, value: Float ->
                    value at (duration * index / lastIndexY)
                }
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathing-y",
    )
    val floatScale by transition.animateFloat(
        initialValue = scales.first(),
        targetValue = scales.first(),
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = duration
                scales.forEachIndexed { index: Int, value: Float ->
                    value at (duration * index / lastIndexS)
                }
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathing-scale",
    )
    return floatY to floatScale
}

/**
 * 词宽测量。优先用 TextMeasurer 实测（对应 Folia 的 canvas measureText）；
 * 测量结果为 0（字体未就绪等）时退回内核的 `text.length * px * 0.65`。
 */
internal fun measureWordWidth(
    measurer: TextMeasurer,
    text: String,
    pxFontSize: Float,
    density: Density,
): Double {
    val measured = runCatching {
        measurer.measure(
            text = text,
            style = TextStyle(
                fontSize = with(density) { pxFontSize.toSp() },
                fontWeight = FontWeight.Medium,
            ),
            constraints = Constraints(),
        ).size.width
    }.getOrDefault(0)
    return if (measured > 0) measured.toDouble()
    else ClassicLayout.measureWordWidthFallback(text, pxFontSize.toDouble())
}