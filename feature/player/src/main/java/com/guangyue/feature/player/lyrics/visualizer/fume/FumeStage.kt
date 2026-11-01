// V1a「浮名」(fume) 绘制层：整首歌排成一张「纸」，镜头在纸面上移动。
//
// 对应 Folia 文件：src/components/visualizer/fume/VisualizerFume.tsx + useFumeCanvasStage.ts
//                + fumeCanvasText.ts（逐字素活性、行辉光、打印方块）
//
// 手机化改动（与 Folia 的差异在 report.md 有对照表）：
//  - 用 Compose 的 DrawScope + drawText 画，不用 <canvas>；排版先在 JVM 侧算好（core/lyrics/fume）；
//  - Folia 的位图快照缓存（createStaticBlockSnapshot + drawImage）在手机上收益不大却要多一层位图，
//    这里统一走即时绘制：waiting / active / passed 三态直接按同一套逐字素逻辑画，
//    已读淡出用 standard / dimmed 两层 alpha 叠加，得到与快照 cross-fade 相同的观感；
//  - 辉光不用任何 blur 节点：按「多层文字阴影」在字周围补画若干圈低透明度副本，
//    既不会像 Modifier.blur 那样把整块节点光栅化成矩形底（V0d 教训），也不会有越界裁切（V0e 教训）。
package com.guangyue.feature.player.lyrics.visualizer.fume

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import com.guangyue.core.lyrics.fume.FumeArticleLayout
import com.guangyue.core.lyrics.fume.FumeArticleLayoutBuilder
import com.guangyue.core.lyrics.fume.FumeBlock
import com.guangyue.core.lyrics.fume.FumeBlockTiming
import com.guangyue.core.lyrics.fume.FumeCamera
import com.guangyue.core.lyrics.fume.FumeCameraStep
import com.guangyue.core.lyrics.fume.FumeCameraStepInput
import com.guangyue.core.lyrics.fume.FumeCameraViewTarget
import com.guangyue.core.lyrics.fume.FumeGlowBases
import com.guangyue.core.lyrics.fume.FumeLayoutTheme
import com.guangyue.core.lyrics.fume.FumeMath
import com.guangyue.core.lyrics.fume.FumeRenderLine
import com.guangyue.core.lyrics.fume.FumeReveal
import com.guangyue.core.lyrics.fume.FumeStaticState
import com.guangyue.core.lyrics.fume.FumeTextStyles
import com.guangyue.core.lyrics.fume.FumeTuning
import com.guangyue.core.lyrics.fume.FumeVariant
import com.guangyue.core.lyrics.fume.FumeViewport
import com.guangyue.core.lyrics.fume.glyphAdvance
import com.guangyue.core.lyrics.fume.offsetOf
import com.guangyue.feature.player.lyrics.visualizer.VisualizerClock
import com.guangyue.core.lyrics.fume.fontWeight as fumeFontWeight
import kotlin.math.cos
import kotlin.math.sin

/** 逐字素要画成什么样（对应 Folia drawFumeLiveBlock 里每字素那份 fill/shadow 状态）。 */
private class GlyphInk(
    val alpha: Double,
    val color: Color,
    val shadowBlur: Double,
    val shadowColor: Color,
)

/**
 * 「浮名」舞台：把整首歌词排成一张纸，镜头在里面移动。
 *
 * @param lineIndex 当前句在内核时间轴上的下标（`LyricFlowScreen` 的 currentIndex）
 * @param lines 内核时间轴（整首歌）
 */
@Composable
internal fun FumeBulletinStage(
    lineIndex: Int,
    lines: List<Line>,
    clock: VisualizerClock,
    tuning: FumeTuning = FumeTuning.DEFAULT,
    intensity: AnimationIntensity = AnimationIntensity.NORMAL,
    lyricsFontScale: Double = 1.0,
    activeColor: Color = Color.White,
    baseColor: Color = Color.White,
    accentColor: Color = Color.White,
    fontFamily: FontFamily = FontFamily.Serif,
    reduceMotion: Boolean = false,
    /** 取证用：>0 时不做帧循环，而是按固定 dt 一次性推进这么多帧再画（模拟器帧时间不可靠）。 */
    settleSteps: Int = 0,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val inspection = LocalInspectionMode.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = FumeViewport(
            width = with(density) { maxWidth.toPx() }.toDouble(),
            // 手机化：Folia 的纸页按「视口高」定目标高（2.45×），竖屏舞台只有 800px 高时
            // 会把整首歌压成一条很矮的横幅。这里按视口宽补一个下限，让纸页接近方形。
            height = maxOf(with(density) { maxHeight.toPx() }.toDouble(), with(density) { maxWidth.toPx() }.toDouble()),
        )
        if (viewport.width <= 0.0 || viewport.height <= 0.0 || lines.none { it.fullText.isNotBlank() }) {
            return@BoxWithConstraints
        }

        val androidMeasurer = remember(textMeasurer, density, fontFamily) {
            FumeTextMeasurerAndroid(textMeasurer, density, fontFamily)
        }
        val layoutTheme = remember(fontFamily) { FumeLayoutTheme(name = "guangyue", fontStyle = "serif") }

        // 排版：对应 Folia buildArticleLayout。Folia 用 96ms 防抖重排（LAYOUT_REBUILD_DEBOUNCE_MS），
        // 手机上改用 remember 的 key：只有视口 / 歌词 / 字号变化时才重建。
        val article: FumeArticleLayout? = remember(lines, viewport, lyricsFontScale, tuning.heroScale, layoutTheme) {
            // 手机化：Folia 的纸页是「宽 1.95×视口、3–4 栏」的横向海报，搬到竖屏手机上会出现
            // 单栏整屏高、横向溢出。这里按舞台宽高比选列数：竖长舞台最多 2 栏，屏宽 ≥360 时才允许 2 栏。
            val aspect = if (viewport.height > 0) viewport.width / viewport.height else 1.0
            val maxColumns = when {
                viewport.width < 360.0 -> 1
                aspect >= 1.2 -> FumeArticleLayoutBuilder.maxColumnsFor(viewport.width * 1.95)
                aspect >= 0.62 -> 2
                else -> 1
            }
            FumeArticleLayoutBuilder.build(
                lines = lines,
                viewport = viewport,
                theme = layoutTheme,
                lyricsFontScale = lyricsFontScale,
                tuning = tuning,
                measurer = androidMeasurer,
                maxColumns = maxColumns,
            )
        }
        if (article == null) {
            // 排版失败时也不能留整片空白——先把原因喊出来（V1a 调试可见性）。
            android.util.Log.e("FumeBulletinStage", "article=null viewport=$viewport lines=${lines.size}")
            return@BoxWithConstraints
        }

        val overviewCamera: FumeCameraViewTarget = remember(article, viewport) {
            FumeCamera.articleOverviewCamera(article, viewport)
        }
        // 对应 Folia 行 221–238：overviewStartTime = 最后一条可渲染行的 startTime + 行时长的一半。
        val overviewStartTime = remember(lines) {
            val last = lines.lastOrNull { it.fullText.isNotBlank() }
            if (last == null) {
                Double.POSITIVE_INFINITY
            } else {
                last.startTime + maxOf(RenderHints.getLineRenderEndTime(last) - last.startTime, 0.0) * 0.5
            }
        }
        val passedFadeDuration = remember(lines, tuning.textHoldRatio) {
            FumeTextStyles.passedFadeDuration(lines, tuning.textHoldRatio)
        }
        val glowBases: FumeGlowBases = remember(intensity, tuning.glowIntensity) {
            FumeTextStyles.glowBases(intensity, tuning.glowIntensity)
        }

        var cameraState by remember(article) {
            mutableStateOf(FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article))
        }
        var overviewTextRestore by remember(article) { mutableStateOf(0.0) }

        // 逐帧推进镜头（对应 useFumeCanvasStage 每帧的 stepFumeCamera）。
        // 正式运行走 withFrameNanos；只有 Robolectric / 取景 harness 才用固定 dt 推进 settleSteps 帧
        // （前者 withFrameNanos 会挂住 waitForIdle，后者模拟器帧时间极不规律、画面不可复现）。
        LaunchedEffect(article, lineIndex, tuning, reduceMotion, inspection, settleSteps) {
            val fixedSteps = if (inspection) settleSteps.coerceAtLeast(1) else if (settleSteps > 0) settleSteps else -1
            if (fixedSteps == 0) return@LaunchedEffect
            if (fixedSteps > 0 && (reduceMotion || inspection || settleSteps > 0)) {
                var state = cameraState
                var restore = 0.0
                // 推进起点 = 当前画面时刻（秒）；逐帧只往前推 dt，且不越过当前句尾，
                // 否则会一路推到歌末被误判成 overview 总览。
                val settleStart = clock.readForDraw()
                val settleLine = lines.getOrNull(lineIndex)
                val settleCeiling = if (settleLine == null) {
                    Double.POSITIVE_INFINITY
                } else {
                    maxOf(RenderHints.getLineRenderEndTime(settleLine) - 0.05, settleLine.startTime)
                }
                repeat(fixedSteps) { frame ->
                    val (next, result) = FumeCameraStep.step(
                        state,
                        FumeCameraStepInput(
                            article = article,
                            time = minOf(settleStart + frame * (1.0 / 60.0), settleCeiling),
                            nowMs = 0.0,
                            // Folia 的镜头靠 dt 追赶 + 重定向时长；固定 dt=1/60 在手机 DPI 下
                            // 弹簧推得极慢，推进 90 帧也到不了目标。这里按帧放大步长，
                            // 使它等价于「在 settleSteps 帧内走完一次完整重定向」。
                            dt = 1.0 / 60.0 * 8.0,
                            lineIndex = lineIndex,
                            viewport = viewport,
                            overviewCamera = overviewCamera,
                            overviewStartTime = overviewStartTime,
                            cameraSpeed = tuning.cameraSpeed,
                            cameraTrackingMode = tuning.cameraTrackingMode,
                            staticMode = true,
                            intensity = intensity,
                            line = lines.getOrNull(lineIndex),
                            nextLineStartTime = lines.getOrNull(lineIndex + 1)?.startTime,
                        ),
                    )
                    state = next
                    restore = result.overviewTextRestoreProgress
                }
                cameraState = state
                overviewTextRestore = restore
                return@LaunchedEffect
            }
            var lastNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (lastNanos == 0L) 1.0 / 60.0 else (nanos - lastNanos) / 1_000_000_000.0
                    lastNanos = nanos
                    val time = clock.readForDraw()
                    val (next, result) = FumeCameraStep.step(
                        cameraState,
                        FumeCameraStepInput(
                            article = article,
                            time = time,
                            nowMs = nanos / 1_000_000.0,
                            dt = dt,
                            lineIndex = lineIndex,
                            viewport = viewport,
                            overviewCamera = overviewCamera,
                            overviewStartTime = overviewStartTime,
                            cameraSpeed = tuning.cameraSpeed,
                            cameraTrackingMode = tuning.cameraTrackingMode,
                            staticMode = false,
                            intensity = intensity,
                            line = lines.getOrNull(lineIndex),
                            nextLineStartTime = lines.getOrNull(lineIndex + 1)?.startTime,
                        ),
                    )
                    cameraState = next
                    overviewTextRestore = result.overviewTextRestoreProgress
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val camera = cameraState.camera
            val time = clock.readForDraw()
            val nextStart = lines.getOrNull(lineIndex + 1)?.startTime

            withTransform({
                translate(size.width * 0.5f, size.height * 0.5f)
                scale(camera.scale.toFloat(), camera.scale.toFloat(), Offset.Zero)
                translate(-camera.x.toFloat(), -camera.y.toFloat())
            }) {
                val ctx = FumeDrawContext(androidMeasurer, textMeasurer, density, fontFamily)
                android.util.Log.e(
                    "FumeBulletinStage",
                    "draw time=$time cam=${camera.x},${camera.y},${camera.scale} " +
                        "ovStart=$overviewStartTime ovScale=${overviewCamera.scale} lineIndex=$lineIndex " +
                        "article=${article.width}x${article.height} blocks=${article.blocks.size}",
                )
                for (block in article.chronologicalBlocks) {
                    if (!FumeTextStyles.isBlockOnScreen(block, camera, viewport)) continue
                    val timing = FumeTextStyles.resolveBlockTiming(block, time, nextStart, tuning.textHoldRatio)
                    when (timing.staticState) {
                        FumeStaticState.WAITING, FumeStaticState.PASSED -> drawPlainBlock(
                            ctx, block, timing, time, glowBases, passedFadeDuration, overviewTextRestore,
                        )
                        FumeStaticState.NONE -> drawLiveBlock(
                            ctx, block, timing, time, glowBases, tuning, accentColor, activeColor, baseColor,
                        )
                    }
                }
            }
        }
    }
}

/** 绘制一帧要用的量，收在一起免得每个函数传一大串。 */
private class FumeDrawContext(
    val android: FumeTextMeasurerAndroid,
    val measurer: TextMeasurer,
    val density: Density,
    val fontFamily: FontFamily,
)

/** waiting：整块一层极淡的主色（对应 Folia 的 waiting 快照）。 */
private fun DrawScope.drawWaitingBlock(ctx: FumeDrawContext, block: FumeBlock, timing: FumeBlockTiming) {
    val style = ctx.style(block, Color.White.copy(alpha = timing.waitingOpacity.toFloat()))
    for (line in block.renderLines) {
        drawRun(ctx, block, line.text, block.lineX(line), block.baselineY(line, timing), style)
    }
}

/**
 * waiting / passed 的静态层：整块一层同色文字（passed 时是 standard / dimmed 的 cross-fade）。
 */
private fun DrawScope.drawPlainBlock(
    ctx: FumeDrawContext,
    block: FumeBlock,
    timing: FumeBlockTiming,
    currentTime: Double,
    glowBases: FumeGlowBases,
    passedFadeDuration: Double,
    overviewTextRestore: Double,
) {
    val layers = FumeTextStyles.staticLayerAlphas(
        timing,
        if (timing.staticState == FumeStaticState.PASSED) currentTime else timing.lineEndTime,
        passedFadeDuration,
        overviewTextRestore,
    )
    for ((holdStyle, alpha) in layers) {
        if (alpha <= 0.002f) continue
        val passedStyle = FumeTextStyles.passedTextStyle(block.variant, holdStyle)
        val isWaiting = timing.staticState == FumeStaticState.WAITING
        val opacity = if (isWaiting) timing.waitingOpacity else passedStyle.opacity
        val blur = if (isWaiting) 0.0 else FumeTextStyles.passedShadowBlur(block, passedStyle, glowBases.passedGlowBase)
        val textColor = Color.White.copy(alpha = (opacity * alpha).toFloat())
        val glowColor = Color.White.copy(alpha = (passedStyle.shadowAlphaBase * alpha).toFloat())
        val style = ctx.style(block, textColor)
        for (line in block.renderLines) {
            val originX = block.lineX(line)
            val originY = block.baselineY(line, timing)
            if (blur > 0.5 && glowColor.alpha > 0.002f) {
                drawGlow(ctx, block, line.text, originX, originY, style, glowColor, blur)
            }
            drawRun(ctx, block, line.text, originX, originY, style)
        }
    }
}

/**
 * passed：standard 层（留淡影）与 dimmed 层（基本收干净）按 alpha 叠加，
 * 等价 Folia 两张快照的 cross-fade。
 */
private fun DrawScope.drawPassedBlock(
    ctx: FumeDrawContext,
    block: FumeBlock,
    timing: FumeBlockTiming,
    currentTime: Double,
    glowBases: FumeGlowBases,
    passedFadeDuration: Double,
    overviewTextRestore: Double,
) {
    val layers = FumeTextStyles.staticLayerAlphas(timing, currentTime, passedFadeDuration, overviewTextRestore)
    for ((holdStyle, alpha) in layers) {
        if (alpha <= 0.002f) continue
        val passedStyle = FumeTextStyles.passedTextStyle(block.variant, holdStyle)
        val blur = FumeTextStyles.passedShadowBlur(block, passedStyle, glowBases.passedGlowBase)
        val textColor = Color.White.copy(alpha = (passedStyle.opacity * alpha).toFloat())
        val glowColor = Color.White.copy(alpha = (passedStyle.shadowAlphaBase * alpha).toFloat())
        val style = ctx.style(block, textColor)
        for (line in block.renderLines) {
            val originX = block.lineX(line)
            val originY = block.baselineY(line, timing)
            if (blur > 0.5 && glowColor.alpha > 0.002f) {
                drawGlow(ctx, block, line.text, originX, originY, style, glowColor, blur)
            }
            drawRun(ctx, block, line.text, originX, originY, style)
        }
    }
}

/**
 * active：逐字素打印 + 行辉光 + 打印方块（对应 Folia drawFumeLiveBlock）。
 * 正在唱的字以活动色点亮，尚未唱到的字是极淡底影，唱过的字随 colorTrail 褪回主色。
 */
private fun DrawScope.drawLiveBlock(
    ctx: FumeDrawContext,
    block: FumeBlock,
    timing: FumeBlockTiming,
    time: Double,
    glowBases: FumeGlowBases,
    tuning: FumeTuning,
    accentColor: Color,
    activeColor: Color,
    baseColor: Color,
) {
    val hero = block.variant == FumeVariant.HERO
    val total = block.graphemes.size
    if (total == 0) return
    val printedCount = FumeReveal.printedGraphemeCount(block, time)

    // 行辉光：正在唱的这一行整行带一圈主色光晕（Folia lineGlowAlpha / lineGlowBlur）。
    val lineProgress = FumeReveal.visualProgressWithCutoff(
        block.line.startTime, timing.lineDuration, time, timing.linePassCutoffTime,
    )
    val envelope = FumeMath.resolveDelayedGlowEnvelope(lineProgress, 0.8)
    val lineGlowAlpha = ((if (hero) 0.16 else 0.12) + envelope * (if (hero) 0.26 else 0.2)) * tuning.glowIntensity
    val lineGlowBlur = ((if (hero) 12.0 else 8.0) + envelope * block.fontPx * (if (hero) 0.7 else 0.52)) * tuning.glowIntensity

    for (line in block.renderLines) {
        val originX = block.lineX(line)
        val originY = block.baselineY(line, timing)
        for (glyphOffset in line.start until line.end) {
            val isPrinted = timing.hasRevealCompleted || glyphOffset < printedCount
            val isFrontier = printedCount > 0 && glyphOffset == printedCount &&
                printedCount < total && !timing.hasRevealCompleted && !timing.hasPassCutoffReached
            val baseAlpha = when {
                                    timing.hasRevealCompleted || glyphOffset < printedCount -> timing.activeOpacity
                glyphOffset == printedCount -> 0.82
                else -> timing.waitingOpacity
            }
            val ink = resolveGlyphInk(block, line, glyphOffset, time, timing, baseAlpha, activeColor, baseColor, glowBases)
            if (ink.alpha <= 0.002) continue
            val glyphText = line.graphemes.getOrNull(glyphOffset - line.start) ?: continue
            val glyphX = originX + line.offsetOf(glyphOffset - line.start).toFloat()
            val style = ctx.style(block, ink.color.copy(alpha = ink.alpha.toFloat()))
            val useLineGlow = isPrinted
            val blur = if (useLineGlow) maxOf(ink.shadowBlur, lineGlowBlur) else ink.shadowBlur
            val glowColor = if (useLineGlow && lineGlowBlur > ink.shadowBlur) {
                accentColor.copy(alpha = (lineGlowAlpha * 1.35).toFloat())
            } else {
                ink.shadowColor
            }
            if (blur > 0.5 && glowColor.alpha > 0.002f) {
                drawGlow(ctx, block, glyphText, glyphX, originY, style, glowColor, blur)
            }
            drawRun(ctx, block, glyphText, glyphX, originY, style)

            // 打印方块（Folia 的 activation block，即 `hidePrintSymbols` 关掉的那层符号）。
            if (!tuning.hidePrintSymbols && isFrontier && glyphText.isNotBlank()) {
                drawPrintStamp(block, line, glyphX, originY, timing, hero, time, accentColor, activeColor)
            }
        }
    }
}

/**
 * 逐字素的墨色（对应 Folia drawFumeLiveBlock 的三段分支：已过颜色轨 / 尚未唱到 / 正在打印）。
 */
private fun resolveGlyphInk(
    block: FumeBlock,
    line: FumeRenderLine,
    glyphOffset: Int,
    time: Double,
    timing: FumeBlockTiming,
    baseAlpha: Double,
    activeColor: Color,
    baseColor: Color,
    glowBases: FumeGlowBases,
): GlyphInk {
    val rangeIndex = block.wordRangeIndexByOffset.getOrElse(glyphOffset) { 0 }
    val range = block.wordRanges.getOrNull(rangeIndex) ?: block.wordRanges.lastOrNull()
        ?: return GlyphInk(baseAlpha, baseColor, 0.0, Color.Transparent)

    val indexInRange = glyphOffset - range.start
    val glyphCount = maxOf(range.end - range.start, 1)
    val wordDuration = maxOf(range.word.endTime - range.word.startTime, 0.08)
    val glyphTiming = range.graphemeTimings.getOrNull(indexInRange)
    val glyphStartTime = glyphTiming?.startTime
        ?: (range.word.startTime + (indexInRange.toDouble() / glyphCount) * wordDuration)
    val glyphEndTime = glyphTiming?.endTime
        ?: (range.word.startTime + ((indexInRange + 1).toDouble() / glyphCount) * wordDuration)
    val glyphDuration = maxOf(glyphEndTime - glyphStartTime, 0.001)
    val glyphProgress = if (glyphTiming != null) {
        FumeMath.clamp((time - glyphStartTime) / glyphDuration + 0.16, 0.0, 1.0)
    } else {
        val wordProgress = FumeReveal.wordRevealProgress(range, time)
        FumeMath.clamp(wordProgress * glyphCount - indexInRange + 0.16, 0.0, 1.0)
    }
    val eased = FumeMath.easeOutCubic(glyphProgress)
    val glyphTrailStart = glyphStartTime + glyphDuration * 0.18
    val trailProgress = Math.pow(
        FumeReveal.visualProgressWithCutoff(
            glyphTrailStart, timing.colorTrailDuration, time, timing.linePassCutoffTime,
        ),
        1.35,
    )

    return when {
        // 已过颜色轨（或这一句已过裁剪时刻）：由活动色褪回主色。
        timing.hasPassCutoffReached || time > glyphTrailStart -> {
            val alpha = FumeMath.mix(timing.activeOpacity, timing.transitionPassedStyle.opacity, trailProgress)
            val color = mixColors(activeColor, baseColor, 0.18 + trailProgress * 0.82, alpha)
            val blur = (2.0 + block.fontPx * 0.1) * (1.0 - trailProgress * 0.35) *
                glowBases.passedGlowBase * timing.transitionPassedStyle.glowMultiplier
            val shadowAlpha = timing.transitionPassedStyle.shadowAlphaBase +
                (1.0 - trailProgress) * timing.transitionPassedStyle.shadowAlphaTrail
            GlyphInk(
                alpha, color, blur,
                mixColors(activeColor, baseColor, 0.55 + trailProgress * 0.45, shadowAlpha),
            )
        }

        // 还没唱到：极淡底影。
        time < range.word.startTime -> GlyphInk(baseAlpha, baseColor, 0.0, Color.Transparent)

        // 正在打印：从主色过渡到活动色，并带一圈渐强的光。
        else -> {
            val alpha = FumeMath.mix(timing.waitingOpacity, timing.activeOpacity, eased)
            val color = mixColors(baseColor, activeColor, 0.22 + eased * 0.78, alpha)
            val blur = (4.0 + block.fontPx * 0.22) * eased * glowBases.activeGlowBoost
            GlyphInk(alpha, color, blur, activeColor.copy(alpha = (0.4 + eased * 0.44).toFloat()))
        }
    }
}

/** 打印方块（对应 Folia 的 activation block）。 */
private fun DrawScope.drawPrintStamp(
    block: FumeBlock,
    line: FumeRenderLine,
    glyphX: Float,
    baselineY: Float,
    timing: FumeBlockTiming,
    hero: Boolean,
    currentTime: Double,
    accentColor: Color,
    activeColor: Color,
) {
    val glyphCount = maxOf(line.end - line.start, 1)
    val glyphWindowDuration = maxOf(timing.lineDuration / glyphCount, 0.04)
    val lead = FumeMath.clamp(
        minOf(glyphWindowDuration * 0.86, timing.lineDuration * 0.16),
        0.055,
        if (hero) 0.2 else 0.16,
    )
    val trailStart = timing.lineEndTime
    val windowStart = trailStart - lead
    val windowEnd = trailStart + lead * 0.42
    val progress = FumeReveal.visualProgressWithCutoff(
        windowStart, windowEnd - windowStart, currentTime, timing.linePassCutoffTime,
    )
    // 只有 0<progress<1 时才画（Folia 同款门槛）。
    if (progress <= 0.0 || progress >= 1.0) return

    val dropProgress = FumeMath.easeOutCubic(
        FumeReveal.visualProgressWithCutoff(
            windowStart, maxOf(trailStart - windowStart, 0.001), currentTime, timing.linePassCutoffTime,
        ),
    )
    val pulse = if (dropProgress < 1.0) FumeMath.mix(0.18, 1.0, Math.pow(dropProgress, 0.78)) else 0.55
    val stampWidth = block.fontPx * (if (hero) 0.6 else 0.5) + block.fontPx * (if (hero) 0.18 else 0.12)
    val stampHeight = block.fontPx * (if (hero) 0.72 else 0.62)
    val dropDistance = block.lineHeight * (if (hero) 0.24 else 0.2)
    val blockY = baselineY - block.fontPx * 0.38 - FumeMath.mix(dropDistance, 0.0, dropProgress)
    val alpha = (pulse * if (hero) 0.82 else 0.72).toFloat()

    drawRect(
        color = activeColor.copy(alpha = alpha * 0.22f),
        topLeft = Offset(glyphX - stampWidth.toFloat() * 0.25f, (blockY - stampHeight * 0.5).toFloat()),
        size = Size(stampWidth.toFloat() * 1.5f, stampHeight.toFloat() * 1.6f),
    )
    drawRect(
        color = accentColor.copy(alpha = alpha),
        topLeft = Offset(glyphX, (blockY - stampHeight * 0.5).toFloat()),
        size = Size(stampWidth.toFloat(), stampHeight.toFloat()),
    )
}

/** 块内某一行的左端（文章坐标）。 */
private fun FumeBlock.lineX(line: FumeRenderLine): Float = (x + line.left).toFloat()

/** 块内某一行的 baseline（文章坐标）。 */
private fun FumeBlock.baselineY(line: FumeRenderLine, timing: FumeBlockTiming): Float =
    (y + line.top + timing.baselineOffset).toFloat()

/** 一段文字的统一样式（颜色每次不同，其余固定）。 */
private fun FumeDrawContext.style(block: FumeBlock, color: Color): TextStyle = TextStyle(
    color = color,
    fontSize = with(density) { block.fontPx.toFloat().toSp() },
    fontWeight = FontWeight(block.variant.fumeFontWeight()),
    fontFamily = fontFamily,
)

/** 画一段文字：Compose 的 drawText 以文字盒顶部对齐，这里先用 baseline 反算盒顶。 */
private fun DrawScope.drawRun(
    ctx: FumeDrawContext,
    block: FumeBlock,
    text: String,
    x: Float,
    baselineY: Float,
    style: TextStyle,
    useInset: Boolean = true,
) {
    if (text.isEmpty()) return
    val layout = ctx.measurer.measure(text = text, style = style)
    val inset = if (useInset) layout.firstBaseline else 0f
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x, baselineY - inset),
    )
}

/**
 * 辉光：不用任何 blur 节点，而是在文字周围画若干圈低透明度副本（多层文字阴影）。
 * 圈数固定，半径随 blur 走，观感接近 Folia 的 shadowBlur。
 */
private fun DrawScope.drawGlow(
    ctx: FumeDrawContext,
    block: FumeBlock,
    text: String,
    x: Float,
    baselineY: Float,
    style: TextStyle,
    glowColor: Color,
    blur: Double,
) {
    if (text.isEmpty() || glowColor.alpha <= 0.002f) return
    val radius = blur.coerceIn(0.0, 48.0).toFloat() * 0.5f
    val glowStyle = style.copy(color = glowColor.copy(alpha = glowColor.alpha * 0.34f))
    val layout = ctx.measurer.measure(text = text, style = glowStyle)
    val inset = layout.firstBaseline
    val steps = 6
    for (i in 0 until steps) {
        val angle = (i.toFloat() / steps) * 6.28318f
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(x + cos(angle) * radius, baselineY - inset + sin(angle) * radius),
        )
    }
}

/** 把两个颜色按比例混起来，并带上目标 alpha（对应 Folia 的 mixColors(..., alpha)）。 */
private fun mixColors(from: Color, to: Color, amount: Double, alpha: Double): Color {
    val t = FumeMath.clamp(amount, 0.0, 1.0).toFloat()
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = FumeMath.clamp(alpha, 0.0, 1.0).toFloat(),
    )
}