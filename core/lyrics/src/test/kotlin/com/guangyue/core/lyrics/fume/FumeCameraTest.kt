// 对照 Folia test/unit/visualizer/fumeLiveRaster.test.ts 之外，覆盖 fumeCamera.ts / fumeCameraStep.ts
// 的公开数值契约（Folia 那边由 fumeCamera 内联断言覆盖）。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FumeCameraTest {

    private fun block(
        id: String = "b0",
        variant: FumeVariant = FumeVariant.BODY,
        x: Double = 100.0,
        y: Double = 200.0,
        width: Double = 300.0,
        height: Double = 60.0,
        fontPx: Double = 20.0,
        lineHeight: Double = 26.0,
        text: String = "为你弹奏",
        start: Double = 0.0,
        end: Double = 2.0,
        renderLines: List<FumeRenderLine> = emptyList(),
    ): FumeBlock {
        val line = Line(
            words = listOf(Word(text, start, end)),
            startTime = start,
            endTime = end,
            fullText = text,
        )
        return FumeBlock(
            id = id,
            sourceLineIndex = 0,
            line = line,
            variant = variant,
            x = x,
            y = y,
            width = width,
            height = height,
            innerWidth = width,
            fontPx = fontPx,
            lineHeight = lineHeight,
            graphemes = FumeMath.splitGraphemes(text),
            advances = List(FumeMath.splitGraphemes(text).size) { fontPx },
            segmentMetas = emptyList(),
            wordRanges = emptyList(),
            wordRangeIndexByOffset = emptyList(),
            colorRangeIndexByOffset = emptyList(),
            renderLines = renderLines,
        )
    }

    private fun renderLine(
        start: Int,
        end: Int,
        left: Double,
        width: Double,
        step: Double,
        graphemeCount: Int = 0,
    ) =
        FumeRenderLine(
            id = "l",
            text = "",
            start = start,
            end = end,
            graphemes = List(if (graphemeCount > 0) graphemeCount else end - start) { "字" },
            glyphOffsets = List(end - start) { it * step },
            segments = emptyList(),
            left = left,
            top = 0.0,
            width = width,
        )

    /** 中英混排行「Hello 世界」：英文窄、汉字宽，glyphOffsets 按实测推进逐字给出（合计 101）。 */
    private fun mixedRenderLine(): FumeRenderLine = FumeRenderLine(
        id = "mixed",
        text = "Hello 世界",
        start = 0,
        end = 8,
        graphemes = FumeMath.splitGraphemes("Hello 世界"),
        // H e l l o 各 11，空格 6，世 / 界 各 20 → 0,11,22,33,44,55,61,81，行宽 101。
        glyphOffsets = listOf(0.0, 11.0, 22.0, 33.0, 44.0, 55.0, 61.0, 81.0),
        segments = emptyList(),
        left = 0.0,
        top = 0.0,
        width = 101.0,
    )

    // 1080×2160px @320dpi = 360×720dp 的手机视口（Hark 截图那台）。
    private val phone = FumeViewport(360.0, 720.0)

    /** 平板/桌面视口：短边 720dp ≥ 600dp，走 Folia 原口径。 */
    private val tablet = FumeViewport(720.0, 1600.0)

    /** 手机尺寸的一条长句行：行宽 900 >> 视口安全区，只能跟字。 */
    private fun phoneLongLine() = renderLine(0, 12, left = 0.0, width = 900.0, step = 75.0)

    @Test
    fun `stepped focus point follows printed grapheme count inside the line bounds`() {
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        // 720×1600 的平板视口下行宽 200 放得下（200 < 720−48），V1a-fix3 起走「短句整句居中」：
        // 焦点 = 行中心 = 100+4+100 = 204，不再跟着已打印字数走。
        val atZero = FumeCamera.steppedBlockFocusPoint(target, 0, FumeViewport(720.0, 1600.0))
        assertEquals(204.0, atZero.x, 1e-6)
        assertEquals(200.0 + 26.0 * 0.5, atZero.y, 1e-6)
        assertEquals(204.0, FumeCamera.steppedBlockFocusPoint(target, 4, FumeViewport(720.0, 1600.0)).x, 1e-6)
        assertEquals(204.0, FumeCamera.steppedBlockFocusPoint(target, 99, FumeViewport(720.0, 1600.0)).x, 1e-6)

        // 长句（行宽 900 放不下）才真的按已打印字数跟字，且焦点取「字心」：
        // 行左缘 100 + baseWidth(0 个字素=0) + advance×0.5 = 137.5；4 个字素 → 100+300+37.5。
        val long = block(renderLines = listOf(phoneLongLine()))
        assertEquals(137.5, FumeCamera.steppedBlockFocusPoint(long, 0, phone).x, 1e-6)
        assertEquals(437.5, FumeCamera.steppedBlockFocusPoint(long, 4, phone).x, 1e-6)
    }

    @Test
    fun `smooth focus point is continuous in the printed progress`() {
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        // 720×1600 视口下行宽 200 放得下 ⇒ 短句整句居中，与进度无关。
        assertEquals(204.0, FumeCamera.smoothBlockFocusPoint(target, 2.0, FumeViewport(720.0, 1600.0)).x, 1e-6)
        assertEquals(204.0, FumeCamera.smoothBlockFocusPoint(target, 1.5, FumeViewport(720.0, 1600.0)).x, 1e-6)
        // 长句（放不下）才连续跟字：进度 2.0 → 第 2 个字素左缘 150 + 半个字宽 37.5；
        // 1.5 → 第 1 个字素左缘 75 + 半个字宽 37.5 + 0.5 个推进 37.5 = 150。
        val long = block(renderLines = listOf(phoneLongLine()))
        assertEquals(100.0 + 150.0 + 37.5, FumeCamera.smoothBlockFocusPoint(long, 2.0, phone).x, 1e-6)
        assertEquals(100.0 + 75.0 + 37.5 + 37.5, FumeCamera.smoothBlockFocusPoint(long, 1.5, phone).x, 1e-6)
    }

    @Test
    fun `camera scale for block targets about 11_5 percent of the short viewport side`() {
        val hero = block(variant = FumeVariant.HERO, lineHeight = 40.0)
        // minViewportSide=720 → targetLineHeight=clamp(82.8,64,124)=82.8 → 82.8/40=2.07。
        assertEquals(2.07, FumeCamera.cameraScaleForBlock(hero, FumeViewport(720.0, 1600.0)), 1e-6)
        // 行高很小 → 夹到 2.2。
        assertEquals(2.2, FumeCamera.cameraScaleForBlock(block(lineHeight = 10.0), FumeViewport(720.0, 1600.0)), 1e-6)
    }

    @Test
    fun `phone viewport lowers the target line height floor to 40dp`() {
        val hero = block(variant = FumeVariant.HERO, lineHeight = 40.0)
        // 360dp 手机：短边 360 → 360×0.115 = 41.4，与手机下限 40 取大者 = 41.4 → scale = 41.4/40。
        assertEquals(41.4 / 40.0, FumeCamera.cameraScaleForBlock(hero, phone), 1e-6)
        // 320dp（Hark 的 320dp 截图）：320×0.115 = 36.8 < 40 → 抬到 40 → scale = 1.0。
        assertEquals(1.0, FumeCamera.cameraScaleForBlock(hero, FumeViewport(320.0, 800.0)), 1e-6)
        // 桌面 1080dp 短边仍用 Folia 原值 64 下限：clamp(124.2,64,124)=124 → 124/40=3.1 夹到 2.2。
        assertEquals(2.2, FumeCamera.cameraScaleForBlock(hero, FumeViewport(1080.0, 1920.0)), 1e-6)
        // 桌面 lineHeight=100 → 124/100=1.24（若误用手机下限 40 会变成 0.88 附近）。
        val tall = block(lineHeight = 100.0)
        assertEquals(1.24, FumeCamera.cameraScaleForBlock(tall, FumeViewport(1080.0, 1920.0)), 1e-6)
    }

    @Test
    fun `short line centres the whole sentence inside the phone viewport`() {
        // 手机 scale = clamp(41.4/26, 0.88, 2.2) ≈ 1.592，安全区 360 − 48 = 312dp，即世界宽上限 ≈ 196dp。
        // 长句：行宽 260 × 1.592 ≈ 414dp > 312dp ⇒ 放不下，必须跟字（防止判定写反）。
        val wide = block(renderLines = listOf(renderLine(0, 12, left = 0.0, width = 260.0, step = 21.67)))
        assertNull(FumeCamera.fitLineFocusPoint(wide, wide.renderLines.first(), phone))
        // 短句：10 个汉字、行宽 150 → 屏上 150 × 1.592 ≈ 239dp ≤ 312dp，整句居中可取。
        val narrow = block(renderLines = listOf(renderLine(0, 10, left = 0.0, width = 150.0, step = 15.0)))
        val fit = FumeCamera.fitLineFocusPoint(narrow, narrow.renderLines.first(), phone)
        assertNotNull(fit)
        assertEquals(100.0 + 75.0, fit!!.x, 1e-6)
        // 整句居中后，行两端在屏上的位置都落在 [24dp, 336dp] 安全区内。
        val scale = FumeCamera.cameraScaleForBlock(narrow, phone)
        val halfScreen = 360.0 * 0.5
        val leftOnScreen = halfScreen - (fit.x - 100.0) * scale
        val rightOnScreen = halfScreen + (100.0 + 150.0 - fit.x) * scale
        assertTrue("left=$leftOnScreen", leftOnScreen >= 24.0 - 1e-6)
        assertTrue("right=$rightOnScreen", rightOnScreen <= 336.0 + 1e-6)
    }

    @Test
    fun `long line focus lands on the screen centre`() {
        // 12 个汉字的长句：行宽 260、逐字 21.67dp，在 360dp 视口里放不下（≈414dp）⇒ 跟字。
        val line = renderLine(0, 12, left = 0.0, width = 260.0, step = 21.67)
        val long = block(text = "一".repeat(12), start = 0.0, end = 12.0, renderLines = listOf(line))
        val scale = FumeCamera.cameraScaleForBlock(long, phone)
        // 直接问镜头取点，确认「正在唱的第 n 个字」落在屏幕正中：camera.x 就是落在屏幕 50% 的世界点，
        // 所以｜(camera.x − 该字字心) × scale｜必须 ≤ 2dp。
        for (n in listOf(2, 6, 9)) {
            val point = FumeCamera.smoothBlockFocusPoint(long, n.toDouble(), phone)
            val wordCentre = 100.0 + n * 21.67 + 21.67 * 0.5
            val offsetDp = (point.x - wordCentre) * scale
            assertTrue("第 $n 个字要落在屏宽 50%±2dp，实测偏 $offsetDp dp", Math.abs(offsetDp) <= 2.0)
        }
    }

    @Test
    fun `overview camera frames all blocks with padding`() {
        val article = articleOf(listOf(block(x = 100.0, y = 100.0, width = 400.0, height = 80.0)))
        val camera = FumeCamera.articleOverviewCamera(article, FumeViewport(720.0, 1600.0))
        assertTrue(camera.scale in FumeCamera.SCALE_MIN..0.72)
        assertEquals(300.0, camera.x, 1e-6)
    }

    @Test
    fun `flight bridge needs a big enough screen distance`() {
        val article = articleOf(listOf(block()))
        val overview = FumeCamera.articleOverviewCamera(article, FumeViewport(720.0, 1600.0))
        val near = FumeCamera.overviewFlightBridge(
            fromX = overview.x,
            fromY = overview.y,
            fromScale = overview.scale,
            targetX = overview.x + 100.0,
            targetY = overview.y,
            targetScale = 1.0,
            overviewCamera = overview,
            viewport = FumeViewport(720.0, 1600.0),
        )
        assertNull(near)
        val far = FumeCamera.overviewFlightBridge(
            fromX = 0.0,
            fromY = 0.0,
            fromScale = overview.scale,
            targetX = 4000.0,
            targetY = 4000.0,
            targetScale = 1.0,
            overviewCamera = overview,
            viewport = FumeViewport(720.0, 1600.0),
        )
        assertNotNull(far)
        assertTrue(far!!.waypointPhase in 0.26..0.44)
        assertTrue(far.duration in 0.3..0.68)
    }

    @Test
    fun `camera state initialises to the article centre`() {
        val article = articleOf(listOf(block()))
        val state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
        assertEquals(article.width * 0.5, state.camera.x, 1e-6)
        assertEquals(1.18, state.camera.scale, 1e-6)
        val clamped = FumeCameraStep.syncToArticle(state.copy(camera = state.camera.copy(x = -50.0)), article)
        assertEquals(0.0, clamped.camera.x, 1e-6)
    }

    @Test
    fun `camera step converges toward the focused block and reports overview`() {
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        val article = articleOf(listOf(target))
        var state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
        var sawOverview = false
        repeat(240) {
            val (next, result) = FumeCameraStep.step(
                state,
                FumeCameraStepInput(
                    article = article,
                    time = 2.5,
                    nowMs = it * (1000.0 / 60.0),
                    dt = 1.0 / 60.0,
                    lineIndex = target.sourceLineIndex,
                    viewport = FumeViewport(720.0, 1600.0),
                    overviewCamera = FumeCamera.articleOverviewCamera(article, FumeViewport(720.0, 1600.0)),
                    overviewStartTime = 0.5,
                    cameraSpeed = 1.0,
                    cameraTrackingMode = FumeCameraTrackingMode.SMOOTH,
                    staticMode = false,
                    intensity = AnimationIntensity.NORMAL,
                    line = target.line,
                    nextLineStartTime = null,
                ),
            )
            sawOverview = sawOverview || result.shouldShowOverview
            assertTrue(next.camera.scale in FumeCamera.SCALE_MIN..FumeCamera.SCALE_MAX)
            state = next
        }
        assertTrue(sawOverview)
        assertTrue(Math.abs(state.camera.y - (200.0 + 13.0)) < 200.0)
    }

    /**
     * 第二句 8s 起唱、4s 长；单行 4 个字素（0..4），行宽 200。
     */
    private fun secondLineBlock(startTime: Double = 8.0): FumeBlock = block(
        id = "b1",
        text = "纪念我死去爱",
        start = startTime,
        end = startTime + 4.0,
        renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)),
    )

    private fun settleCameraAt(
        article: FumeArticleLayout,
        block: FumeBlock,
        time: Double,
        viewport: FumeViewport = FumeViewport(720.0, 1600.0),
    ): FumeCameraTarget {
        var state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
        repeat(24) {
            state = FumeCameraStep.step(
                state,
                FumeCameraStepInput(
                    article = article,
                    time = time,
                    nowMs = 0.0,
                    dt = 1.0 / 60.0,
                    lineIndex = block.sourceLineIndex,
                    viewport = viewport,
                    overviewCamera = null,
                    overviewStartTime = Double.MAX_VALUE,
                    cameraSpeed = 1.0,
                    cameraTrackingMode = FumeCameraTrackingMode.SMOOTH,
                    staticMode = true,
                    intensity = AnimationIntensity.NORMAL,
                    line = block.line,
                    nextLineStartTime = null,
                ),
            ).first
        }
        return state.camera
    }

    @Test
    fun `smooth focus point walks past the line head as printing progresses`() {
        // 短句：行宽 150（字体偏小、行高 26 → 手机 scale 41.4/26≈1.592，屏上 239dp ≤ 312dp）
        // ⇒ 整句居中，与进度无关：行中心 = 100 + 4 + 75 = 179。
        val target = block(
            renderLines = listOf(renderLine(0, 4, left = 4.0, width = 150.0, step = 15.0)),
        )
        val fit = target.x + 4.0 + 75.0
        assertEquals(fit, FumeCamera.smoothBlockFocusPoint(target, 0.0, phone).x, 1e-6)
        assertEquals(fit, FumeCamera.smoothBlockFocusPoint(target, 4.0, phone).x, 1e-6)
        // 长句（行宽 900 放不下）跟字，且焦点单调右移、绝不回到行首：
        // 0 → 字心 137.5；第 4 个字素 → 100+300+37.5=437.5；行末（offset 夹到行尾 12）→ 100+900=1000。
        val long = block(renderLines = listOf(phoneLongLine()))
        assertEquals(137.5, FumeCamera.smoothBlockFocusPoint(long, 0.0, phone).x, 1e-6)
        assertEquals(437.5, FumeCamera.smoothBlockFocusPoint(long, 4.0, phone).x, 1e-6)
        assertEquals(1000.0, FumeCamera.smoothBlockFocusPoint(long, 12.5, phone).x, 1e-6)
        assertTrue(FumeCamera.smoothBlockFocusPoint(long, 12.5, phone).x > 437.5)
    }

    @Test
    fun `smooth focus point measures width from the line start offset`() {
        val target = secondLineBlock()
        val line = target.renderLines.single()
        val lineLeft = target.x + line.left
        // 720×1600 视口下行宽 200 放得下 ⇒ 短句整句居中（V1a-fix3），焦点恒为行中心。
        val centre = lineLeft + 100.0
        assertEquals(centre, FumeCamera.smoothBlockFocusPoint(target, 0.0, tablet).x, 1e-6)
        assertEquals(centre, FumeCamera.smoothBlockFocusPoint(target, 2.0, tablet).x, 1e-6)
        assertEquals(centre, FumeCamera.smoothBlockFocusPoint(target, 4.0, tablet).x, 1e-6)
        // 跟字路径按「块内偏移」而不是行内序号换算：offset 2.0 = 行内第 2 个字素 → 左缘 40 + 半个字宽 10。
        assertEquals(lineLeft + 40.0 + 10.0, FumeCamera.centeredGlyphFocusPoint(target, line, 2.0).x, 1e-6)
        // 行末（offset = 行尾 4.0 = 行内字素数）落在行右缘 200，与 Folia resolvePointOnRenderLine
        // 同值（fumeCamera.ts:83-96）：baseWidth=widthBetweenOffsets(行起点→4)=200，localGlyphIndex=4
        // 越界 ⇒ advance=0、fractional=0，故 x = 行左缘 + 200。指认这里的 200 就是实测行宽。
        assertEquals(lineLeft + 200.0, FumeCamera.centeredGlyphFocusPoint(target, line, 4.0).x, 1e-6)
        assertEquals(lineLeft + 200.0, FumeCamera.centeredGlyphFocusPoint(target, line, 4.5).x, 1e-6)
        // 行宽 200 由逐字推进给出（每字素 20），不是把行宽按字素数均分出来的巧合：
        // blockWidthBetween(行起点, 行起点+4) == 4×20 = 200 == 行宽。
        assertEquals(200.0, line.blockWidthBetween(line.start, line.end), 1e-6)
    }

    @Test
    fun `smooth focus point uses measured glyph widths on a mixed CJK and latin line`() {
        // V1b-1：中英混排「Hello 世界」实测推进 0,11,22,33,44,55,61,81（英文窄、汉字宽），行宽 101。
        // V1a-fix3 起取点再加半个字宽（取字心），这里直接验证字心函数。
        val target = block(text = "Hello 世界", renderLines = listOf(mixedRenderLine()))
        val line = target.renderLines.single()
        val lineLeft = target.x + line.left
        // 实测宽度口径：逐字推进 0,11,22,33,44,55,61,81，行宽 101 —— 不是 8 个字素均分 12.625。
        assertEquals(61.0, line.blockWidthBetween(line.start, line.start + 6), 1e-6)
        assertEquals(20.0, line.glyphAdvance(6), 1e-6)
        // 唱到第 7 个字素「世」：实测左缘 61 + 半个字宽 10 = 71。
        assertEquals(lineLeft + 71.0, FumeCamera.centeredGlyphFocusPoint(target, line, 6.0).x, 1e-6)
        // 行内小数推进用「世」自己的宽度 20：61 + 10 + 0.5×20 = 81。
        assertEquals(lineLeft + 81.0, FumeCamera.centeredGlyphFocusPoint(target, line, 6.5).x, 1e-6)
        // 行尾：最后半个字宽（空格 20 的一半）以外不再往右跑。
        assertEquals(lineLeft + 101.0, FumeCamera.centeredGlyphFocusPoint(target, line, 8.0).x, 1e-6)
        assertEquals(lineLeft + 101.0, FumeCamera.centeredGlyphFocusPoint(target, line, 9.0).x, 1e-6)
    }

    @Test
    fun `smooth focus point at the line end follows Folia without jumping back`() {
        // V1b-1：行末判定与 Folia fumeCamera.ts:59-62 对齐（offset <= renderLine.end）。
        val target = block(
            text = "纪念我死去爱",
            renderLines = listOf(
                renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0),
                renderLine(4, 6, left = 4.0, width = 40.0, step = 20.0),
            ),
        )
        val lineLeft = target.x + 4.0
        // 单行 0..4、行宽 200 在 720×1600 里放得下 ⇒ 整句居中（204），不会跳到行尾。
        assertEquals(lineLeft + 100.0, FumeCamera.smoothBlockFocusPoint(target, 4.0, tablet).x, 1e-6)
        // 字心函数按行内偏移取点：首行第 4 个字素（0..4 行的最后一个）的左缘 60、
        // 该字素 advance 取到行宽收口 = 200 − 60 = 140，字心 = 60 + 70 = 130；
        // 第二行（4..6、宽 40）行首 4.0 的字心 = 行左缘 + 半个字宽 10。
        assertEquals(lineLeft + 130.0, FumeCamera.centeredGlyphFocusPoint(target, target.renderLines[0], 3.0).x, 1e-6)
        assertEquals(lineLeft + 10.0, FumeCamera.centeredGlyphFocusPoint(target, target.renderLines[1], 4.0).x, 1e-6)
    }

    @Test
    fun `static settle camera follows the printed progress of that instant`() {
        val article = articleOf(listOf(secondLineBlock()))
        val target = article.blocks.single()
        val lineLeft = target.x + target.renderLines.single().left

        // V1a-fix3：行宽 200 在 720×1600 视口里放得下 ⇒ 整句居中，镜头不再跟着字走；
        // scale 仍由 cameraScaleForBlock 决定。
        val mid = settleCameraAt(article, target, 10.0)
        assertEquals(lineLeft + 100.0, mid.x, 1e-3)
        assertEquals(FumeCamera.cameraScaleForBlock(target, FumeViewport(720.0, 1600.0)), mid.scale, 1e-6)
    }

    /** 构造一个最小可用的文章，方便镜头单测。 */
    private fun articleOf(blocks: List<FumeBlock>): FumeArticleLayout {
        var width = 0.0
        var height = 0.0
        for (block in blocks) {
            width = maxOf(width, block.x + block.width)
            height = maxOf(height, block.y + block.height)
        }
        return FumeArticleLayout(
            width = width + 200.0,
            height = height + 200.0,
            viewportHeight = 1600.0,
            columns = 1,
            gap = 8.0,
            paperBounds = FumePaperBounds(100.0, 100.0, width, height),
            blocks = blocks,
            blockBySourceLineIndex = blocks.associateBy { it.sourceLineIndex },
            chronologicalBlocks = blocks,
            firstRenderableStartTime = blocks.firstOrNull()?.line?.startTime ?: 0.0,
            lastChronologicalRenderEndTime = blocks.lastOrNull()?.line?.endTime ?: 0.0,
        )
    }
}
