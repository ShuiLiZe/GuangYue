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
//  - 辉光走 TextStyle.shadow（offset 恒为 Offset.Zero + blurRadius），等价 Folia 的 canvas
//    `shadowBlur` + `shadowColor`：光晕与正文同位同形、只有模糊，既不会像 Modifier.blur 那样把
//    整块节点光栅化成矩形底（V0d 教训），也不会像旧的「多圈偏移副本」在字周围留下错位重影。
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** 逐字素要画成什么样（对应 Folia drawFumeLiveBlock 里每字素那份 fill/shadow 状态）。 */
private class GlyphInk(
    val alpha: Double,
    val color: Color,
    val shadowBlur: Double,
    val shadowColor: Color,
)

/**
 * V1a 取证：非 null 时把浮名辉光强度钉在这个值上（只给 debug 取景用）。
 *
 * 模拟器软件渲染下单帧要几百毫秒，行辉光的包络峰值几乎撞不上，光晕强弱拍出来全凭运气；
 * 这个开关只改一份本地 copy，不写 State、不进 release 逻辑（release 源集不设置它）。
 */
/** 公开只因为 debug 源集的取景 harness 要写它；业务代码只读这里。 */
var fumeGlowOverride: Float? = null

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
    /** V1a 自查：画块边界与对焦十字（仅取景用）。 */
    debugFrames: Boolean = false,
    /**
     * 取景定格时刻（秒）。仅当 settleSteps>0 或预览（Inspection）时生效：
     * 镜头与文字活性都按这个时刻算，不跟播放时钟走。
     */
    settleTargetTime: Double = 0.0,
    /**
     * 取景已经落位的镜头。非 null 时镜头、文字时刻、已读淡出进度全部用它，
     * 绘制层不再读任何 State、也不再等帧循环 —— 模拟器上单帧开销 3–4s，
     * 等 Async 落点再刷新几乎必然拍不及。
     */
    stillCamera: FumeStillFrame? = null,
    modifier: Modifier = Modifier,
) {
    // V1a 取证：debug 源集可把辉光强度钉住（模拟器上按帧采样，光晕最亮的那一帧很难撞上）。
    val tuning = fumeGlowOverride?.let { tuning.copy(glowIntensity = it.toDouble()) } ?: tuning
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val inspection = LocalInspectionMode.current
    // 取景 harness（settleSteps>0 或预览）用固定推进：不跟播放时钟，直接落到 settleTargetTime。
    val fixedMode = inspection || settleSteps > 0

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = FumeViewport(
            // Folia 的 viewport / 排版 / 相机全部是 CSS px，等价 Android dp。
            // 这里统一用 dp 世界坐标，只在 Canvas 入口 scale(density) 一次，
            // 避免出现「用 px 排版却按 dp 定字号」的错位。
            width = with(density) { maxWidth.toPx() / this.density }.toDouble(),
            height = with(density) { maxHeight.toPx() / this.density }.toDouble(),
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
        // 镜头状态。取值发生在 DrawScope 里，如果直接读 State 会让绘制 scope 订阅它，
        // 之后再画一遍也收不到 draw 请求（排查记录：整屏画布曾因此冻在第一帧）。
        // 用 Snapshot.withoutReadObservation 取，绘制只做消费者，不订阅。
        // 另外取景下要立刻拿到刚落地的镜头，所以直接反射当前值。
        val readCamera: () -> com.guangyue.core.lyrics.fume.FumeCameraState = {
            Snapshot.withoutReadObservation { cameraState }
        }

        // 取景落点（settleSteps>0 / 预览）：镜头直接落到 settleTargetTime 这一刻。
        // 必须在组合期外做——remember 初值里读 article 会导致重组命中缓存、镜头停在旧落点。
        LaunchedEffect(article, lineIndex, tuning, reduceMotion, settleSteps, settleTargetTime, fixedMode) {
            if (!fixedMode || reduceMotion) return@LaunchedEffect
            var state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
            var restore = 0.0
            repeat(24) {
                val (next, result) = FumeCameraStep.step(
                    state,
                    FumeCameraStepInput(
                        article = article,
                        time = settleTargetTime,
                        nowMs = 0.0,
                        dt = 1.0 / 60.0,
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
        }

        // 逐帧推进镜头（对应 useFumeCanvasStage 每帧的 stepFumeCamera）。
        // 取景（settleSteps>0）与预览（Inspection）走上面对应的落点分支，这里只跑真实帧循环。
        LaunchedEffect(article, lineIndex, tuning, reduceMotion, settleSteps) {
            if (fixedMode) return@LaunchedEffect
            if (reduceMotion) return@LaunchedEffect
            var lastNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (lastNanos == 0L) 1.0 / 60.0 else (nanos - lastNanos) / 1_000_000_000.0
                    lastNanos = nanos
                    val time = clock.readForDraw()
                    val (next, result) = FumeCameraStep.step(
                        readCamera(),
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

        // 取景：组合期就把镜头算到这里（纯计算，不写 State）。绘制层直接用，不订阅任何 State。
        val stillCamera = stillCamera
        val settleState = if (stillCamera != null && fixedMode) {
            var state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
            repeat(stillCamera.steps) {
                state = FumeCameraStep.step(
                    state,
                    FumeCameraStepInput(
                        article = article,
                        time = settleTargetTime,
                        nowMs = 0.0,
                        dt = 1.0 / 60.0,
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
                ).first
            }
            state
        } else {
            null
        }

        Canvas(Modifier.fillMaxSize()) {
            val camera = settleState?.camera ?: readCamera().camera
            val time = if (fixedMode) settleTargetTime else clock.readForDraw()
            val nextStart = lines.getOrNull(lineIndex + 1)?.startTime

            // 世界坐标全是 dp（Folia 的 CSS px）。Canvas 是 px，所以在最外层先把整张画布
            // 放大 density 倍，此后内部一切（相机、块、字号）都是 dp；scale(camera.scale) 与
            // 平移顺序严格照 useFumeCanvasStage.ts：translate(视口中心) → scale → translate(-camera)。
            val densityScale = density.density
            withTransform({
                scale(densityScale, densityScale, Offset.Zero)
                translate(
                    (viewport.width * 0.5).toFloat(),
                    (viewport.height * 0.5).toFloat(),
                )
                scale(camera.scale.toFloat(), camera.scale.toFloat(), Offset.Zero)
                translate(-camera.x.toFloat(), -camera.y.toFloat())
            }) {
                val ctx = FumeDrawContext(androidMeasurer, textMeasurer, density, fontFamily)
                for (block in article.chronologicalBlocks) {
                    if (debugFrames) {
                        drawRect(
                            color = if (block.sourceLineIndex == lineIndex) Color.Red else Color.Cyan,
                            topLeft = Offset(block.x.toFloat(), block.y.toFloat()),
                            size = Size(block.width.toFloat(), block.height.toFloat()),
                            style = Stroke(width = 1.5f / densityScale / camera.scale.toFloat()),
                        )
                        drawCameraCross(block.x + block.width * 0.5, block.y + block.height * 0.5, camera.scale, densityScale)
                    }
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

/**
 * 取景参数（只有调试 harness 传）：落点要推进多少帧。
 * staticMode 下每帧直接落到目标，24 帧足够走完一次完整重定向。
 */
class FumeStillFrame(val steps: Int = 24)

/** 绘制一帧要用的量，收在一起免得每个函数传一大串。 */
private class FumeDrawContext(
    val android: FumeTextMeasurerAndroid,
    val measurer: TextMeasurer,
    val density: Density,
    val fontFamily: FontFamily,
)

/**
 * 一次辉光采样：模糊半径（世界坐标 dp，等价 Folia 的 shadowBlur）与阴影颜色。
 * 对应 Folia `fumeCanvasText.ts` 里成对出现的 `shadowBlur` + `shadowColor`。
 */
private class FumeGlowSample(val blur: Double, val color: Color)

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
        val glow = if (blur > 0.0 && glowColor.alpha > 0.002f) FumeGlowSample(blur, glowColor) else null
        val style = ctx.style(block, textColor, glow)
        for (line in block.renderLines) {
            drawRun(ctx, block, line.text, block.lineX(line), block.baselineY(line, timing), style)
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
        val glow = if (blur > 0.0 && glowColor.alpha > 0.002f) FumeGlowSample(blur, glowColor) else null
        val style = ctx.style(block, textColor, glow)
        for (line in block.renderLines) {
            drawRun(ctx, block, line.text, block.lineX(line), block.baselineY(line, timing), style)
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

    // 行辉光：正在唱的这一行整行带一圈活动色光晕。等价 Folia 先画一遍「只有 shadowBlur 的整行
    // 文字」（fumeCanvasText.ts:145-185）再逐字素盖上去：形状与位置跟正文完全一致、没有位移，
    // 所以不会再出现旧实现那种「多圈偏移副本」的重影。
    val lineProgress = FumeReveal.visualProgressWithCutoff(
        block.line.startTime, timing.lineDuration, time, timing.linePassCutoffTime,
    )
    val envelope = FumeMath.resolveDelayedGlowEnvelope(lineProgress, 0.8)
    val lineGlowAlpha = ((if (hero) 0.16 else 0.12) + envelope * (if (hero) 0.26 else 0.2)) * tuning.glowIntensity
    val lineGlowBlur = ((if (hero) 12.0 else 8.0) + envelope * block.fontPx * (if (hero) 0.7 else 0.52)) * tuning.glowIntensity
    if (lineGlowAlpha > 0.002 && lineGlowBlur > 0.0) {
        val glow = FumeGlowSample(lineGlowBlur, accentColor.copy(alpha = (lineGlowAlpha * 1.35).toFloat()))
        val glowStyle = ctx.style(block, Color.Transparent, glow)
        for (line in block.renderLines) {
            drawRun(ctx, block, line.text, block.lineX(line), block.baselineY(line, timing), glowStyle)
        }
    }

    // 镜头的 scale 由 FumeCamera.cameraScaleForBlock 决定（目标行高 = clamp(短边×0.115,64,124)，
    // scale clamp 0.88–2.2）。块比视口宽是 Folia 的原样：长句靠镜头焦点沿当前字向右平移来读，
    // 当前正在唱的字必须留在屏内，行尾还没唱到的字出屏可以接受。
    // （前任自创的 hero「fitScale 等比缩进」已删除 —— Folia fumeCamera.ts 没有这段逻辑。）
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
            val ink = resolveGlyphInk(
                block, line, glyphOffset, time, timing, baseAlpha, activeColor, baseColor, glowBases,
            )
            if (ink.alpha <= 0.002) continue
            val glyphText = line.graphemes.getOrNull(glyphOffset - line.start) ?: continue
            val glyphX = originX + line.offsetOf(glyphOffset - line.start).toFloat()
            // 单字 shadow 只在整行辉光不覆盖它时才用（Folia：整行辉光画在下面，字素自身的
            // shadowBlur 仍按 fillStyle 走，两处叠加但都没有位移）。
            val glyphGlow = if (ink.shadowBlur > 0.0 && ink.shadowColor.alpha > 0.002f) {
                FumeGlowSample(ink.shadowBlur, ink.shadowColor)
            } else {
                null
            }
            val style = ctx.style(block, ink.color.copy(alpha = ink.alpha.toFloat()), glyphGlow)
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

/** 段内某一字素的横向落点（文章坐标，dp）：Folia 用 pretext 逐字宽度，这里用整块 advances 累计。 */
private fun FumeBlock.glyphCenterX(
    line: FumeRenderLine,
    segmentIndex: Int,
    localIndex: Int,
): Float {
    val segment = line.segments[segmentIndex]
    val offsets = segment.measuredGlyphOffsets
    val mid = if (localIndex + 1 < offsets.size) {
        (offsets[localIndex] + offsets[localIndex + 1]) * 0.5
    } else {
        offsets.getOrElse(localIndex) { segment.width }
    }
    return (x + line.left + segment.x + mid).toFloat()
}

/** V1a 自查：在指定点画一个十字，用来确认相机对焦点落在屏幕中心。 */
private fun DrawScope.drawCameraCross(cx: Double, cy: Double, scale: Double, densityScale: Float) {
    val arm = 12.0 / maxOf(scale, 0.001)
    val w = 1.5f / densityScale / maxOf(scale.toFloat(), 0.001f)
    drawLine(
        color = Color.Magenta,
        start = Offset((cx - arm).toFloat(), cy.toFloat()),
        end = Offset((cx + arm).toFloat(), cy.toFloat()),
        strokeWidth = w,
    )
    drawLine(
        color = Color.Magenta,
        start = Offset(cx.toFloat(), (cy - arm).toFloat()),
        end = Offset(cx.toFloat(), (cy + arm).toFloat()),
        strokeWidth = w,
    )
}

/** 块内某一行的左端（文章坐标）。 */
private fun FumeBlock.lineX(line: FumeRenderLine): Float = (x + line.left).toFloat()

/** 块内某一行的 baseline（文章坐标）。 */
private fun FumeBlock.baselineY(line: FumeRenderLine, timing: FumeBlockTiming): Float =
    (y + line.top + timing.baselineOffset).toFloat()

/**
 * 一段文字的统一样式（颜色每次不同，其余固定）。
 *
 * 辉光走 TextStyle.shadow，等价 Folia 的 canvas `shadowBlur` + `shadowColor`：偏移固定
 * Offset.Zero（Folia 从不设 shadowOffsetX/Y），只有模糊半径，因此光晕与正文同位同形、不会错位。
 *
 * blurRadius 直接给世界坐标（dp）的原值：外层 withTransform 已经把 density 与相机缩放写进
 * 画布矩阵，Skia 的模糊核会跟着矩阵一起缩放，光晕随镜头放大而放大。这里再乘一次
 * density × camera.scale 就是双重换算（320dpi 下约 4.4 倍），会把光晕糊成一团。
 */
private fun FumeDrawContext.style(
    block: FumeBlock,
    color: Color,
    glow: FumeGlowSample? = null,
): TextStyle = TextStyle(
    color = color,
    fontSize = with(density) { block.fontPx.toFloat().toSp() },
    fontWeight = FontWeight(block.variant.fumeFontWeight()),
    fontFamily = fontFamily,
    shadow = if (glow != null && glow.blur > 0.0 && glow.color.alpha > 0.002f) {
        Shadow(
            color = glow.color,
            offset = Offset.Zero,
            // Folia fumeCanvasText.ts:386 把 shadowBlur 取整到整像素。
            blurRadius = Math.round(glow.blur).toFloat().coerceAtLeast(0f),
        )
    } else {
        null
    },
)

/**
 * 画一段文字。
 *
 * Folia 用 canvas 的 textBaseline='middle'：给的 y 是文字竖直中线。Compose 的
 * drawText(textLayoutResult, topLeft) 里 topLeft.y 是排版框顶部，所以
 * topLeft.y = 中线 y − layout.size.height/2（对应派活方 D 条）。
 */
private fun DrawScope.drawRun(
    ctx: FumeDrawContext,
    block: FumeBlock,
    text: String,
    x: Float,
    centerY: Float,
    style: TextStyle,
    useInset: Boolean = true,
) {
    if (text.isEmpty()) return
    val layout = ctx.measurer.measure(text = text, style = style)
    val top = if (useInset) centerY - layout.size.height * 0.5f else centerY
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x, top),
    )
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