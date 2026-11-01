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

    @Test
    fun `stepped focus point follows printed grapheme count inside the line bounds`() {
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        val atZero = FumeCamera.steppedBlockFocusPoint(target, 0)
        assertEquals(104.0, atZero.x, 1e-6)
        val atEnd = FumeCamera.steppedBlockFocusPoint(target, 4)
        // 块内 advances 全为 fontPx=20，4 个字素 → widthBetweenOffsets(0,4)=80 → 104+80=184。
        assertEquals(184.0, atEnd.x, 1e-6)
        // 超出字素数一律夹到行尾（即块内字素总数）。
        assertEquals(184.0, FumeCamera.steppedBlockFocusPoint(target, 99).x, 1e-6)
        // 垂直方向是行中线。
        assertEquals(200.0 + 26.0 * 0.5, atEnd.y, 1e-6)
    }

    @Test
    fun `smooth focus point is continuous in the printed progress`() {
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        // 行宽 200 按 4 个字素均分，每档 50；144=行中线（镜头应当正对正在唱的字）。
        val half = FumeCamera.smoothBlockFocusPoint(target, 2.0)
        assertEquals(204.0, half.x, 1e-6)
        val quarter = FumeCamera.smoothBlockFocusPoint(target, 1.5)
        assertEquals(179.0, quarter.x, 1e-6)
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
    fun `overview camera frames all blocks with padding`() {
        val article = articleOf(
            listOf(
                block(id = "a", x = 0.0, y = 0.0, width = 100.0, height = 50.0),
                block(id = "b", x = 500.0, y = 400.0, width = 100.0, height = 50.0),
            ),
        )
        val camera = FumeCamera.articleOverviewCamera(article, FumeViewport(720.0, 1600.0))
        assertEquals(300.0, camera.x, 1e-6)
        assertEquals(225.0, camera.y, 1e-6)
        assertTrue(camera.scale in 0.22..0.72)
    }

    @Test
    fun `flight bridge needs a big enough screen distance`() {
        val viewport = FumeViewport(720.0, 1600.0)
        val overview = FumeCameraViewTarget(x = 0.0, y = 0.0, scale = 0.4)
        assertNull(
            FumeCamera.overviewFlightBridge(0.0, 0.0, 1.0, 100.0, 0.0, 1.0, overview, viewport),
        )
        val bridge = FumeCamera.overviewFlightBridge(0.0, 0.0, 1.0, 4000.0, 0.0, 1.0, overview, viewport)
        assertNotNull(bridge)
        assertTrue(requireNotNull(bridge).waypointPhase in 0.26..0.44)
        assertTrue(requireNotNull(bridge).duration in 0.3..0.68)
    }

    @Test
    fun `camera state initialises to the article centre`() {
        val article = articleOf(listOf(block()))
        val synced = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
        assertTrue(synced.initialized)
        assertEquals(article.width * 0.5, synced.camera.x, 1e-6)
        assertEquals(1.18, synced.camera.scale, 1e-6)
        // 再同步一次只做钳制，不再重置。
        val clamped = FumeCameraStep.syncToArticle(synced.copy(camera = synced.camera.copy(x = -50.0)), article)
        assertEquals(0.0, clamped.camera.x, 1e-6)
    }

    @Test
    fun `camera step converges toward the focused block and reports overview`() {
        val article = articleOf(listOf(block(start = 0.0, end = 4.0)))
        var state = FumeCameraStep.syncToArticle(FumeCameraStep.initialState(), article)
        val viewport = FumeViewport(720.0, 1600.0)
        val overview = FumeCamera.articleOverviewCamera(article, viewport)
        var time = 0.0
        var sawOverview = false
        repeat(240) {
            time += 1.0 / 60.0
            val input = FumeCameraStepInput(
                article = article,
                time = time,
                nowMs = time * 1000.0,
                dt = 1.0 / 60.0,
                lineIndex = 0,
                viewport = viewport,
                overviewCamera = overview,
                overviewStartTime = 0.5,
                cameraSpeed = 1.0,
                cameraTrackingMode = FumeCameraTrackingMode.SMOOTH,
                staticMode = false,
                intensity = AnimationIntensity.NORMAL,
            )
            val (next, result) = FumeCameraStep.step(state, input)
            state = next
            sawOverview = sawOverview || result.shouldShowOverview
            assertTrue(state.camera.scale in FumeCamera.SCALE_MIN..FumeCamera.SCALE_MAX)
        }
        assertTrue(sawOverview)
        // 播到 4s 时已经离开 overview，靠近第一个块。
        val blockCenterY = 200.0 + 26.0 * 0.5
        assertTrue(Math.abs(state.camera.y - blockCenterY) < 200.0)
    }

    // ---- V1a-fix2：镜头焦点必须跟着「正在唱的字」走 ----
    // 症状：实拍里当前句行首永远落在屏幕中线，正在唱的字贴到右边缘（320dp 下被切掉一半），
    // 对应 printedProgress=0（= block.x+line.left）。下面三条把整条链子锁住。

    /**
     * 第二句 8s 起唱、4s 长；块文本 5 个字素，首行只排前 4 个（0..4），末字落到第二行。
     * 用来验证「按行起点换算行内偏移」而不是拿行内序号去索引块内 advances。
     */
    private fun secondLineBlock(): FumeBlock = block(
        id = "b1",
        text = "纪念我死去爱",
        start = 8.0,
        end = 12.0,
        renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)),
    )

    /** 块起点在时间轴上：起唱 8s、结束 12s（4 个字素各占 1s）。 */
    private fun secondLineBlock(startTime: Double = 8.0): FumeBlock = block(
        id = "b1",
        text = "纪念我死去爱",
        start = startTime,
        end = startTime + 4.0,
        renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)),
    )

    private fun settleCameraAt(article: FumeArticleLayout, block: FumeBlock, time: Double): FumeCameraTarget {
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
                    viewport = FumeViewport(720.0, 1600.0),
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
        val target = block(renderLines = listOf(renderLine(0, 4, left = 4.0, width = 200.0, step = 20.0)))
        // 行首：printedProgress=0 → 只剩 block.x + line.left。
        assertEquals(104.0, FumeCamera.smoothBlockFocusPoint(target, 0.0).x, 1e-6)
        // 唱到第 4 个字：104 + 4×50（行宽 200 按 4 个字素均分，第 4 个字左缘即行右缘）。
        assertEquals(304.0, FumeCamera.smoothBlockFocusPoint(target, 4.0).x, 1e-6)
        // 再往后夹在行右缘，绝不回到行首。
        assertEquals(304.0, FumeCamera.smoothBlockFocusPoint(target, 4.5).x, 1e-6)
        // 再往后夹在行右缘 204，绝不回到行首。
        assertEquals(304.0, FumeCamera.smoothBlockFocusPoint(target, 9.5).x, 1e-6)
    }

    @Test
    fun `smooth focus point measures width from the line start offset`() {
        // 首行从块内 0 开始，末字（块内第 4 个）在第二行：靠行起点换算，不拿行内序号索引。
        val target = secondLineBlock()
        val lineLeft = target.x + target.renderLines.single().left
        assertEquals(lineLeft, FumeCamera.smoothBlockFocusPoint(target, 0.0).x, 1e-6)
        // 行宽 200 按 4 个字素均分，每档 50。
        assertEquals(lineLeft + 100.0, FumeCamera.smoothBlockFocusPoint(target, 2.0).x, 1e-6)
        assertEquals(lineLeft + 200.0, FumeCamera.smoothBlockFocusPoint(target, 4.0).x, 1e-6)
    }

    @Test
    fun `static settle camera follows the printed progress of that instant`() {
        val article = articleOf(listOf(secondLineBlock()))
        val target = article.blocks.single()
        val lineLeft = target.x + target.renderLines.single().left

        // 句中 10s（8s 起唱）：必须离开行首，往右走了 2 个字宽（advances 全是 fontPx=20）。
        val mid = settleCameraAt(article, target, 10.0)
        assertTrue("镜头停在行首 (x=${mid.x})，没有跟字", mid.x > lineLeft + 20.0)
        assertEquals(lineLeft + 200.0, mid.x, 1e-3)
        assertEquals(FumeCamera.cameraScaleForBlock(target, FumeViewport(720.0, 1600.0)), mid.scale, 1e-6)

        // 刚起唱一小会儿就必须已经离开行首。
        val early = settleCameraAt(article, target, 8.5)
        assertTrue("起唱 0.5s 后镜头仍停在行首 (x=${early.x})", early.x > lineLeft + 5.0)
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