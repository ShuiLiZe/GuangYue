// 对应 Folia 文件：src/components/visualizer/fume/fumeCamera.ts、fumeCameraStep.ts
//
// 浮名的镜头：整首歌排成一张「纸」，镜头在纸面上移动，逐句对焦。
//  - overview 阶段（开篇）先拉远看全篇；
//  - 之后每句切换到「对焦块」，[FumeCameraTrackingMode.STEPPED] 按已打印字素整数跳，
//    [FumeCameraTrackingMode.SMOOTH] 按连续进度平滑跟随；
//  - 大跨度转场走「桥」（桥路径 / 直线），小跨度靠弹簧。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints

object FumeCamera {
    /** 对应 CAMERA_SCALE_MIN / CAMERA_SCALE_MAX。 */
    const val SCALE_MIN = 0.22
    const val SCALE_MAX = 2.24

    /** 对应 OVERVIEW_CAMERA_SOURCE：retarget.sourceLineIndex 用 -2 表示「正在去 overview」。 */
    const val OVERVIEW_CAMERA_SOURCE = -2

    /** 对应 FUME_CAMERA_TELEPORT_TRIGGER_SCREENS。 */
    const val TELEPORT_TRIGGER_SCREENS = 2.75

    /** 对应跨行混合窗 crossLineBlendWindow。 */
    private const val CROSS_LINE_BLEND_WINDOW = 0.7

    /** 对应 resolveSteppedBlockFocusPoint：按「已打印到第几个字素」定位，整数跳。 */
    fun steppedBlockFocusPoint(block: FumeBlock, printedCount: Int): FumeCameraViewTarget {
        if (block.renderLines.isEmpty()) {
            return FumeCameraViewTarget(block.x + block.width * 0.5, block.y + block.height * 0.5, 1.0)
        }
        val effectiveOffset = FumeMath.clamp(printedCount.toDouble(), 0.0, block.graphemes.size.toDouble())
        val targetLine = block.renderLines.firstOrNull { effectiveOffset <= it.end }
            ?: block.renderLines.last()
        val minX = block.x + targetLine.left
        val maxX = minX + targetLine.width
        val localOffset = FumeMath.clamp(effectiveOffset, targetLine.start.toDouble(), targetLine.end.toDouble())
        val progressWidth = block.widthBetweenOffsets(targetLine.start, Math.round(localOffset).toInt())
        return FumeCameraViewTarget(
            x = FumeMath.clamp(minX + progressWidth, minX, maxX),
            y = block.y + targetLine.top + block.lineHeight * 0.5,
            scale = 1.0,
        )
    }

    /** 对应 resolveSmoothBlockFocusPoint：按连续进度跟随，跨行时在窗口内混合。 */
    fun smoothBlockFocusPoint(block: FumeBlock, printedProgress: Double): FumeCameraViewTarget {
        if (block.renderLines.isEmpty()) {
            return FumeCameraViewTarget(block.x + block.width * 0.5, block.y + block.height * 0.5, 1.0)
        }
        val effectiveOffset = FumeMath.clamp(printedProgress, 0.0, block.graphemes.size.toDouble())

        fun pointOnLine(lineIndex: Int, offset: Double): FumeCameraViewTarget {
            val line = block.renderLines[lineIndex]
            val clampedOffset = FumeMath.clamp(offset, line.start.toDouble(), line.end.toDouble())
            val baseOffset = Math.floor(clampedOffset).toInt()
            val fractional = clampedOffset - baseOffset
            val baseWidth = block.widthBetweenOffsets(line.start, baseOffset)
            val localGlyphIndex = baseOffset - line.start
            val glyphAdvance = line.glyphAdvance(localGlyphIndex)
            val minX = block.x + line.left
            val maxX = minX + line.width
            return FumeCameraViewTarget(
                x = FumeMath.clamp(minX + baseWidth + glyphAdvance * fractional, minX, maxX),
                y = block.y + line.top + block.lineHeight * 0.5,
                scale = 1.0,
            )
        }

        val targetLineIndex = block.renderLines.indexOfFirst { effectiveOffset <= it.end }
            .let { if (it < 0) block.renderLines.size - 1 else it }
        val targetLine = block.renderLines[targetLineIndex]
        var point = pointOnLine(targetLineIndex, effectiveOffset)

        // 行内快走完时提前把镜头往下一行开头带一点，换行才不会「顿」一下。
        if (targetLineIndex > 0 && effectiveOffset < targetLine.start + CROSS_LINE_BLEND_WINDOW) {
            val previousLine = block.renderLines[targetLineIndex - 1]
            val blend = FumeMath.easeInOutCubic(
                FumeMath.clamp(1.0 - ((effectiveOffset - previousLine.end) / CROSS_LINE_BLEND_WINDOW), 0.0, 1.0),
            )
            val previousPoint = pointOnLine(targetLineIndex - 1, previousLine.end.toDouble())
            point = FumeCameraViewTarget(
                x = FumeMath.mix(previousPoint.x, point.x, blend),
                y = FumeMath.mix(previousPoint.y, point.y, blend),
                scale = point.scale,
            )
        } else if (targetLineIndex < block.renderLines.size - 1 &&
            effectiveOffset > targetLine.end - CROSS_LINE_BLEND_WINDOW
        ) {
            val nextLine = block.renderLines[targetLineIndex + 1]
            val blend = FumeMath.easeInOutCubic(
                FumeMath.clamp(
                    (effectiveOffset - (targetLine.end - CROSS_LINE_BLEND_WINDOW)) / CROSS_LINE_BLEND_WINDOW, 0.0, 1.0,
                ),
            )
            val nextPoint = pointOnLine(targetLineIndex + 1, nextLine.start.toDouble())
            point = FumeCameraViewTarget(
                x = FumeMath.mix(point.x, nextPoint.x, blend),
                y = FumeMath.mix(point.y, nextPoint.y, blend),
                scale = point.scale,
            )
        }
        return point
    }

    /** 对应 resolveBlockEntryFocusPoint：一句刚进来时先看它的首行开头。 */
    fun blockEntryFocusPoint(block: FumeBlock): FumeCameraViewTarget {
        val firstLine = block.renderLines.firstOrNull()
            ?: return FumeCameraViewTarget(block.x + block.width * 0.5, block.y + block.height * 0.5, 1.0)
        return FumeCameraViewTarget(
            x = block.x + firstLine.left,
            y = block.y + firstLine.top + block.lineHeight * 0.5,
            scale = 1.0,
        )
    }

    /** 对应 resolveCameraScaleForBlock：让行高落在视口短的边约 11.5% 的位置。 */
    fun cameraScaleForBlock(block: FumeBlock, viewport: FumeViewport): Double {
        val minViewportSide = maxOf(minOf(viewport.width, viewport.height), 1.0)
        val targetLineHeight = FumeMath.clamp(minViewportSide * 0.115, 64.0, 124.0)
        return FumeMath.clamp(targetLineHeight / maxOf(block.lineHeight, 1.0), 0.88, 2.2)
    }

    /** 对应 resolveCameraRetargetDuration：按行的过渡模式决定对焦快慢。 */
    fun cameraRetargetDuration(line: Line): Double {
        val hints = RenderHints.getLineRenderHints(line) ?: return 0.09
        val rawDuration = maxOf(line.rawDuration, 0.0)
        // Folia 侧 lineTransitionTiming 直接挂在 hints 上；Kotlin 侧由 RenderHints 现算。
        val timing = RenderHints.getLineTransitionTiming(rawDuration, hints.lineTransitionMode, hints.wordRevealMode)
        return when (hints.lineTransitionMode) {
            com.guangyue.core.lyrics.LineTransitionMode.NONE ->
                FumeMath.clamp(maxOf(rawDuration, 0.08) * 0.34, 0.04, 0.075)
            com.guangyue.core.lyrics.LineTransitionMode.FAST ->
                FumeMath.clamp(timing.enterDuration * 0.5 + timing.exitDuration * 0.12, 0.055, 0.095)
            else ->
                FumeMath.clamp(timing.enterDuration * 0.44 + timing.linePassHold * 0.22, 0.075, 0.13)
        }
    }

    /** 对应 resolveOverviewRetargetDuration。 */
    fun overviewRetargetDuration(viewport: FumeViewport): Double =
        FumeMath.clamp(minOf(viewport.width, viewport.height) / 1500.0, 0.38, 0.58)

    /** 对应 resolveArticleOverviewCamera：开篇「看全篇」的机位。 */
    fun articleOverviewCamera(article: FumeArticleLayout, viewport: FumeViewport): FumeCameraViewTarget {
        if (article.blocks.isEmpty()) {
            val fitScale = minOf(
                viewport.width / maxOf(article.width, 1.0),
                viewport.height / maxOf(article.height, 1.0),
            )
            return FumeCameraViewTarget(
                x = article.width * 0.5,
                y = article.height * 0.5,
                scale = FumeMath.clamp(fitScale * 0.92, SCALE_MIN, 0.72),
            )
        }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (block in article.blocks) {
            minX = minOf(minX, block.x)
            minY = minOf(minY, block.y)
            maxX = maxOf(maxX, block.x + block.width)
            maxY = maxOf(maxY, block.y + block.height)
        }
        val paddingX = FumeMath.clamp(viewport.width * 0.2, 120.0, 280.0)
        val paddingY = FumeMath.clamp(viewport.height * 0.2, 96.0, 220.0)
        val framedWidth = maxOf(maxX - minX + paddingX * 2, 1.0)
        val framedHeight = maxOf(maxY - minY + paddingY * 2, 1.0)
        val fitScale = minOf(viewport.width / framedWidth, viewport.height / framedHeight)
        return FumeCameraViewTarget(
            x = (minX + maxX) * 0.5,
            y = (minY + maxY) * 0.5,
            scale = FumeMath.clamp(fitScale, SCALE_MIN, 0.72),
        )
    }

    /** 对应 resolveOverviewFlightBridge：跨度太大时插一段「起飞 → 掠过全篇 → 落地」的桥。 */
    fun overviewFlightBridge(
        fromX: Double,
        fromY: Double,
        fromScale: Double,
        targetX: Double,
        targetY: Double,
        targetScale: Double,
        overviewCamera: FumeCameraViewTarget?,
        viewport: FumeViewport,
    ): FumeBridge? {
        if (overviewCamera == null) return null
        val safeScale = maxOf(fromScale, targetScale, overviewCamera.scale, 0.001)
        val worldDistance = hypot(targetX - fromX, targetY - fromY)
        val screenDistance = worldDistance * safeScale
        val minViewportSide = maxOf(minOf(viewport.width, viewport.height), 1.0)
        if (worldDistance <= 0.0 || screenDistance < minViewportSide * TELEPORT_TRIGGER_SCREENS) return null

        val loftStrength = FumeMath.clamp((screenDistance / minViewportSide - TELEPORT_TRIGGER_SCREENS) / 3.4, 0.0, 1.0)
        val midpointX = FumeMath.mix(fromX, targetX, 0.5)
        val midpointY = FumeMath.mix(fromY, targetY, 0.5)
        val waypointCenterBias = FumeMath.mix(0.18, 0.42, loftStrength)
        val waypointX = FumeMath.mix(midpointX, overviewCamera.x, waypointCenterBias)
        val waypointY = FumeMath.mix(midpointY, overviewCamera.y, waypointCenterBias)
        val endpointScale = maxOf(fromScale, targetScale, 0.001)
        val loftedScale = endpointScale * FumeMath.mix(0.62, 0.4, loftStrength)
        val overviewLimitedScale = overviewCamera.scale * FumeMath.mix(1.85, 1.55, loftStrength)
        val maxWaypointScale = maxOf(endpointScale * 0.92, SCALE_MIN)
        val waypointScale = FumeMath.clamp(maxOf(loftedScale, overviewLimitedScale), SCALE_MIN, maxWaypointScale)

        val fromLegDistance = hypot(waypointX - fromX, waypointY - fromY) * maxOf(fromScale, waypointScale, 0.001)
        val toLegDistance = hypot(targetX - waypointX, targetY - waypointY) * maxOf(targetScale, waypointScale, 0.001)
        val totalDistance = fromLegDistance + toLegDistance
        val waypointPhase = if (totalDistance <= 0.0) {
            0.36
        } else {
            FumeMath.clamp(fromLegDistance / totalDistance, 0.26, 0.44)
        }
        val duration = FumeMath.clamp(
            0.26 + (screenDistance / (minViewportSide * 5.5)) * 0.28,
            0.3,
            0.68,
        )
        return FumeBridge(waypointX, waypointY, waypointScale, waypointPhase, duration)
    }

    /** 对应 resolveFocusBlock：这一刻该看哪一块。 */
    fun resolveFocusBlock(
        article: FumeArticleLayout,
        currentLineIndex: Int,
        currentTime: Double,
    ): FumeBlock? {
        val blocks = article.blocks
        if (blocks.isEmpty()) return null
        val byLine = article.blockBySourceLineIndex[currentLineIndex]
        if (currentLineIndex >= 0 && byLine != null) return byLine
        val last = blocks.last()
        if (currentTime >= article.lastChronologicalRenderEndTime) return last
        for (index in blocks.indices.reversed()) {
            val block = blocks[index]
            if (currentTime < block.line.startTime) continue
            val printed = FumeReveal.printedGraphemeCount(block, currentTime)
            if (printed > 0) return block
        }
        return blocks.first()
    }

    private fun hypot(x: Double, y: Double): Double = kotlin.math.sqrt(x * x + y * y)
}

/** 对应 resolveOverviewFlightBridge 的返回值。 */
data class FumeBridge(
    val waypointX: Double,
    val waypointY: Double,
    val waypointScale: Double,
    val waypointPhase: Double,
    val duration: Double,
)

/** 对应 stepFumeCamera 的输入。 */
data class FumeCameraStepInput(
    val article: FumeArticleLayout?,
    val time: Double,
    /** 驱动用时钟（ms），只参与 idle 呼吸浮动，跟播放时间无关。 */
    val nowMs: Double,
    val dt: Double,
    val lineIndex: Int,
    val viewport: FumeViewport,
    val overviewCamera: FumeCameraViewTarget?,
    val overviewStartTime: Double,
    val cameraSpeed: Double,
    val cameraTrackingMode: FumeCameraTrackingMode,
    val staticMode: Boolean,
    val intensity: AnimationIntensity,
    val line: Line? = null,
    val nextLineStartTime: Double? = null,
)

/** 对应 stepFumeCamera 的返回值。 */
data class FumeCameraStepResult(
    val shouldShowOverview: Boolean,
    val overviewTextRestoreProgress: Double,
)

object FumeCameraStep {
    /** 对应 createFumeCameraState。 */
    fun initialState(): FumeCameraState = FumeCameraState(
        camera = FumeCameraTarget(),
        retarget = FumeCameraRetargetState(),
        initialized = false,
    )

    /** 对应 syncFumeCameraToArticle：相机初始化 / 越界收敛。 */
    fun syncToArticle(state: FumeCameraState, article: FumeArticleLayout?): FumeCameraState {
        if (article == null) return state.copy(initialized = false)
        if (!state.initialized) {
            return state.copy(
                initialized = true,
                camera = FumeCameraTarget(
                    x = article.width * 0.5,
                    y = article.height * 0.5,
                    focusX = article.width * 0.5,
                    focusY = article.height * 0.5,
                    scale = 1.18,
                    focusScale = 1.18,
                ),
            )
        }
        val camera = state.camera
        return state.copy(
            camera = camera.copy(
                x = FumeMath.clamp(camera.x, 0.0, article.width),
                y = FumeMath.clamp(camera.y, 0.0, article.height),
                focusX = FumeMath.clamp(camera.focusX, 0.0, article.width),
                focusY = FumeMath.clamp(camera.focusY, 0.0, article.height),
                scale = FumeMath.clamp(camera.scale, FumeCamera.SCALE_MIN, FumeCamera.SCALE_MAX),
                focusScale = FumeMath.clamp(camera.focusScale, FumeCamera.SCALE_MIN, FumeCamera.SCALE_MAX),
            ),
        )
    }

    /** 对应 stepFumeCamera：推进一帧。 [state] 以 copy 方式返回新状态。 */
    fun step(state: FumeCameraState, input: FumeCameraStepInput): Pair<FumeCameraState, FumeCameraStepResult> {
        val article = input.article
        if (article == null) {
            return state to FumeCameraStepResult(shouldShowOverview = false, overviewTextRestoreProgress = 0.0)
        }
        val dt = FumeMath.clamp(input.dt, 1.0 / 240.0, 0.05)
        val cameraSpeed = FumeMath.clamp(input.cameraSpeed, 0.55, 1.85)
        val viewport = input.viewport
        val minViewportSide = maxOf(minOf(viewport.width, viewport.height), 1.0)

        val focusBlock = FumeCamera.resolveFocusBlock(article, input.lineIndex, input.time)
        val shouldShowOverview = input.overviewCamera != null && input.time >= input.overviewStartTime

        var targetX = article.width * 0.5
        var targetY = article.height * 0.5
        var targetScale = 1.18
        var entryFocusPoint: FumeCameraViewTarget? = null

        var retarget = state.retarget
        var didRetarget = false

        if (shouldShowOverview) {
            val overviewCamera = input.overviewCamera!!
            targetX = overviewCamera.x
            targetY = overviewCamera.y
            targetScale = overviewCamera.scale
            if (retarget.sourceLineIndex != FumeCamera.OVERVIEW_CAMERA_SOURCE) {
                val duration = FumeMath.clamp(
                    FumeCamera.overviewRetargetDuration(viewport) / cameraSpeed, 0.12, 1.2,
                )
                retarget = retarget.copy(
                    sourceLineIndex = FumeCamera.OVERVIEW_CAMERA_SOURCE,
                    startedAt = input.time,
                    duration = duration,
                    fromX = state.camera.x,
                    fromY = state.camera.y,
                    fromScale = state.camera.scale,
                    bridgeMode = FumeBridgeMode.NONE,
                )
                didRetarget = true
            }
        } else if (focusBlock != null) {
            val printedCount = FumeReveal.printedGraphemeCount(focusBlock, input.time)
            val focusPoint = if (input.cameraTrackingMode == FumeCameraTrackingMode.STEPPED) {
                FumeCamera.steppedBlockFocusPoint(focusBlock, printedCount)
            } else {
                val progress = FumeReveal.printedGraphemeProgress(focusBlock, input.time)
                FumeCamera.smoothBlockFocusPoint(focusBlock, progress)
            }
            targetX = focusPoint.x
            targetY = focusPoint.y
            targetScale = FumeCamera.cameraScaleForBlock(focusBlock, viewport)
            entryFocusPoint = FumeCamera.blockEntryFocusPoint(focusBlock)
            if (retarget.sourceLineIndex != focusBlock.sourceLineIndex) {
                val line = input.line ?: focusBlock.line
                val duration = FumeMath.clamp(FumeCamera.cameraRetargetDuration(line) / cameraSpeed, 0.03, 0.3)
                retarget = retarget.copy(
                    sourceLineIndex = focusBlock.sourceLineIndex,
                    startedAt = input.time,
                    duration = duration,
                    fromX = state.camera.x,
                    fromY = state.camera.y,
                    fromScale = state.camera.scale,
                    bridgeMode = FumeBridgeMode.NONE,
                )
                didRetarget = true
            }
        } else if (retarget.sourceLineIndex != -1) {
            retarget = retarget.copy(
                sourceLineIndex = -1,
                startedAt = input.time,
                duration = FumeMath.clamp(0.18 / cameraSpeed, 0.05, 0.4),
            )
            didRetarget = true
        }

        val retargetElapsed = maxOf(input.time - retarget.startedAt, 0.0)
        val retargetPhase = FumeMath.clamp(retargetElapsed / maxOf(retarget.duration, 0.001), 0.0, 1.0)
        val retargetBoost = 1.0 - FumeMath.easeOutCubic(retargetPhase)
        val entryFocusBias = Math.pow(retargetBoost, 0.58)
        val overviewTextRestoreProgress =
            if (shouldShowOverview && retarget.sourceLineIndex == FumeCamera.OVERVIEW_CAMERA_SOURCE) {
                FumeMath.easeInOutCubic(FumeMath.clamp(retargetElapsed / maxOf(retarget.duration, 0.001), 0.0, 1.0))
            } else {
                0.0
            }

        if (entryFocusPoint != null) {
            targetX = FumeMath.mix(targetX, entryFocusPoint.x, entryFocusBias)
            targetY = FumeMath.mix(targetY, entryFocusPoint.y, entryFocusBias)
        }

        // idle 浮动：镜头停在某处时不是死的，仍在极慢地呼吸（对应 Folia 的 idle float）。
        if (!input.staticMode) {
            val float = when (input.intensity) {
                AnimationIntensity.CHAOTIC -> floatOf(24.0, 5.8, 0.014)
                AnimationIntensity.CALM -> floatOf(14.0, 8.5, 0.008)
                AnimationIntensity.NORMAL -> floatOf(18.0, 7.0, 0.011)
            }
            val floatPhase = (input.nowMs / 1000.0 / float.duration) * 2.0 * Math.PI
            val overviewAttenuation = if (shouldShowOverview) 0.36 else 1.0
            val screenFloatX = Math.sin(floatPhase * 0.74 + 0.8) * float.distance * 0.34
            val screenFloatY = (Math.sin(floatPhase) * float.distance +
                Math.sin(floatPhase * 0.5 + 1.1) * float.distance * 0.22) * overviewAttenuation
            val divisor = maxOf(targetScale, 0.001)
            targetX -= screenFloatX / divisor
            targetY -= screenFloatY / divisor
            targetScale = FumeMath.clamp(
                targetScale * (1.0 + Math.sin(floatPhase + 0.9) * float.scaleAmplitude * overviewAttenuation),
                FumeCamera.SCALE_MIN,
                FumeCamera.SCALE_MAX,
            )
        }

        // 大跨度转场定桥（对应 Folia 行 300–345）。
        if (didRetarget) {
            val bridgeScale = maxOf(state.camera.scale, targetScale, 0.001)
            val screenDistance = hypot((targetX - retarget.fromX) * bridgeScale, (targetY - retarget.fromY) * bridgeScale)
            var bridgeMode = if (screenDistance >= minViewportSide * 0.42) FumeBridgeMode.DIRECT else FumeBridgeMode.NONE
            var bridgeX = targetX
            var bridgeY = targetY
            var bridgeS = targetScale
            var bridgePhase = 0.5
            if (retarget.sourceLineIndex >= 0) {
                val bridge = FumeCamera.overviewFlightBridge(
                    fromX = retarget.fromX,
                    fromY = retarget.fromY,
                    fromScale = retarget.fromScale,
                    targetX = targetX,
                    targetY = targetY,
                    targetScale = targetScale,
                    overviewCamera = input.overviewCamera,
                    viewport = viewport,
                )
                if (bridge != null) {
                    bridgeMode = FumeBridgeMode.OVERVIEW
                    bridgeX = bridge.waypointX
                    bridgeY = bridge.waypointY
                    bridgeS = bridge.waypointScale
                    bridgePhase = bridge.waypointPhase
                    retarget = retarget.copy(
                        duration = maxOf(retarget.duration, FumeMath.clamp(bridge.duration / cameraSpeed, 0.16, 0.9)),
                    )
                }
            }
            retarget = retarget.copy(
                bridgeMode = bridgeMode,
                bridgeWaypointX = bridgeX,
                bridgeWaypointY = bridgeY,
                bridgeWaypointScale = bridgeS,
                bridgeWaypointPhase = bridgePhase,
            )
        }

        val camera = state.camera
        var nextCamera: FumeCameraTarget
        val shouldUseBridge = retarget.bridgeMode != FumeBridgeMode.NONE && retargetPhase < 1.0
        if (input.staticMode) {
            // 固定帧推进（Robolectric / 取景 harness，也用于 reduceMotion）：Folia 的弹簧追赶依赖
            // 真实帧时长，固定 dt 下会推得极慢、画面停在途中。这里直接落到目标，
            // 等价于「重定向已完成」的那一帧。
            nextCamera = camera.copy(
                x = targetX,
                y = targetY,
                scale = targetScale,
                focusX = targetX,
                focusY = targetY,
                focusScale = targetScale,
                velocityX = 0.0,
                velocityY = 0.0,
                velocityScale = 0.0,
            )
        } else if (shouldUseBridge) {
            val easeOut = retarget.bridgeMode == FumeBridgeMode.OVERVIEW
            val bridged: Triple<Double, Double, Double> = if (easeOut) {
                val bridgePhase = FumeMath.easeOutCubic(retargetPhase)
                Triple(
                    FumeMath.quadraticBezier(retarget.fromX, retarget.bridgeWaypointX, targetX, bridgePhase),
                    FumeMath.quadraticBezier(retarget.fromY, retarget.bridgeWaypointY, targetY, bridgePhase),
                    FumeMath.quadraticBezier(
                        retarget.fromScale, retarget.bridgeWaypointScale, targetScale, bridgePhase,
                    ),
                )
            } else {
                val bridgePhase = FumeMath.easeInOutCubic(retargetPhase)
                Triple(
                    FumeMath.mix(retarget.fromX, targetX, bridgePhase),
                    FumeMath.mix(retarget.fromY, targetY, bridgePhase),
                    FumeMath.mix(retarget.fromScale, targetScale, bridgePhase),
                )
            }
            val catchUp = if (easeOut) {
                val rate = FumeMath.mix(12.5, 22.0, 1.0 - retargetPhase)
                1.0 - Math.exp(-dt * rate)
            } else {
                val rate = FumeMath.mix(10.5, 17.5, 1.0 - retargetPhase)
                1.0 - Math.exp(-dt * rate)
            }
            nextCamera = camera.copy(
                focusX = bridged.first,
                focusY = bridged.second,
                focusScale = bridged.third,
                x = FumeMath.mix(camera.x, bridged.first, catchUp),
                y = FumeMath.mix(camera.y, bridged.second, catchUp),
                scale = FumeMath.mix(camera.scale, bridged.third, catchUp),
                velocityX = camera.velocityX * 0.72,
                velocityY = camera.velocityY * 0.72,
                velocityScale = camera.velocityScale * 0.68,
            )
        } else {
            val boostedCatchUpRate = FumeMath.clamp(4.8 / maxOf(retarget.duration, 0.05), 20.0, 54.0)
            val targetCatchUp = 1.0 - Math.exp(-dt * FumeMath.mix(11.2, boostedCatchUpRate, retargetBoost))
            val focusCatchUp = 1.0 - Math.exp(-dt * FumeMath.mix(5.4, 12.8, retargetBoost))
            val focusX = FumeMath.mix(camera.focusX, targetX, focusCatchUp)
            val focusY = FumeMath.mix(camera.focusY, targetY, focusCatchUp)
            val focusScale = FumeMath.mix(camera.focusScale, targetScale, focusCatchUp)

            val springStrength = FumeMath.mix(
                208.0,
                FumeMath.clamp(15.8 / maxOf(retarget.duration * retarget.duration, 0.0064), 260.0, 780.0),
                retargetBoost,
            )
            val damping = FumeMath.mix(
                24.0,
                FumeMath.clamp(Math.sqrt(springStrength) * 1.36, 24.0, 40.0),
                retargetBoost,
            )
            val cameraDistance = hypot(focusX - camera.x, focusY - camera.y)
            val maxVelocity = FumeMath.mix(
                1320.0,
                FumeMath.clamp(cameraDistance / maxOf(retarget.duration * 0.28, 0.028), 2600.0, 8800.0),
                retargetBoost,
            )
            var velocityX = camera.velocityX + ((focusX - camera.x) * springStrength - camera.velocityX * damping) * dt
            var velocityY = camera.velocityY + ((focusY - camera.y) * springStrength - camera.velocityY * damping) * dt
            velocityX = FumeMath.clamp(velocityX, -maxVelocity, maxVelocity)
            velocityY = FumeMath.clamp(velocityY, -maxVelocity, maxVelocity)

            val scaleStrength = FumeMath.mix(54.0, 108.0, retargetBoost)
            val scaleDamping = FumeMath.mix(13.5, 21.0, retargetBoost)
            var velocityScale = camera.velocityScale +
                ((focusScale - camera.scale) * scaleStrength - camera.velocityScale * scaleDamping) * dt
            velocityScale = FumeMath.clamp(velocityScale, -1.6, 1.6)

            nextCamera = FumeCameraTarget(
                x = camera.x + velocityX * dt,
                y = camera.y + velocityY * dt,
                velocityX = velocityX,
                velocityY = velocityY,
                focusX = focusX,
                focusY = focusY,
                scale = FumeMath.clamp(camera.scale + velocityScale * dt, FumeCamera.SCALE_MIN, FumeCamera.SCALE_MAX),
                velocityScale = velocityScale,
                focusScale = focusScale,
            )
        }

        return state.copy(camera = nextCamera, retarget = retarget, initialized = true) to
            FumeCameraStepResult(shouldShowOverview, overviewTextRestoreProgress)
    }

    private fun targetCatchUpOrZero(bridged: Boolean): Double = if (bridged) 0.0 else 0.0

    private data class FloatSpec(val distance: Double, val duration: Double, val scaleAmplitude: Double)

    private fun floatOf(distance: Double, duration: Double, scaleAmplitude: Double) =
        FloatSpec(distance, duration, scaleAmplitude)

    private fun hypot(x: Double, y: Double): Double = kotlin.math.sqrt(x * x + y * y)

    /** 对应 quadraticBezier(x0, x1, x2, t)。 */
    private fun quadraticBezier(x0: Double, x1: Double, x2: Double, t: Double): Double =
        FumeMath.quadraticBezier(x0, x1, x2, t)
}

/** 对应 resolveFumeBackgroundView 的返回：背景视差机位。 */
data class FumeBackgroundView(val x: Double, val y: Double, val scale: Double)

object FumeBackgroundViewResolver {
    /** 对应 FUME_BACKGROUND_PARALLAX_X / _Y。 */
    const val PARALLAX_X = 0.9
    const val PARALLAX_Y = 0.74
    const val SCALE_FACTOR = 0.94
    const val VERTICAL_OFFSET_RATIO = 0.22

    fun resolve(
        backgroundCenterX: Double,
        backgroundCenterY: Double,
        camera: FumeCameraTarget,
        viewport: FumeViewport,
        screenScale: Double,
    ): FumeBackgroundView {
        val safeScale = maxOf(screenScale, 0.001)
        val verticalOffset = FumeMath.clamp(viewport.height * VERTICAL_OFFSET_RATIO / safeScale, 48.0, 180.0)
        return FumeBackgroundView(
            x = FumeMath.mix(backgroundCenterX, camera.x, PARALLAX_X),
            y = FumeMath.mix(backgroundCenterY, camera.y, PARALLAX_Y) - verticalOffset,
            scale = FumeMath.clamp(screenScale * SCALE_FACTOR, FumeCamera.SCALE_MIN, FumeCamera.SCALE_MAX),
        )
    }
}