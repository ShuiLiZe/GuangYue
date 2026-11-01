// 对照 Folia test/unit/visualizer/fumeLineFit.test.ts 与 fumeArticleLayout.ts 的公开数值
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FumeArticleLayoutBuilderTest {

    /** 按字素平均分配宽度的测量器：够用来验证二分与断行的几何关系。 */
    private inner class FakeMeasurer(private val emRatio: Double = 0.5) : FumeTextMeasurer {
        var calls = 0
        override fun measureWidth(text: String, fontPx: Double, variant: FumeVariant): Double {
            calls += 1
            return FumeMath.splitGraphemes(text).sumOf { fontPx * emRatio }
        }
    }

    private fun line(text: String, start: Double, end: Double, chorus: Boolean = false) = Line(
        words = listOf(Word(text, start, end)),
        startTime = start,
        endTime = end,
        fullText = text,
        isChorus = chorus,
    )

    private val theme = FumeLayoutTheme(name = "test", fontStyle = "sans")

    @Test
    fun `paper geometry matches folia`() {
        // 视口 720 时：max(720*1.95, 720+520)=1404 → 夹到 [920,2400] 内 = 1404。
        assertEquals(1404.0, FumeArticleLayoutBuilder.paperWidthFor(720.0), 1e-6)
        assertEquals(920.0, FumeArticleLayoutBuilder.paperWidthFor(100.0), 1e-6)
        assertEquals(2400.0, FumeArticleLayoutBuilder.paperWidthFor(2000.0), 1e-6)
        assertEquals(4, FumeArticleLayoutBuilder.maxColumnsFor(1240.0))
        assertEquals(3, FumeArticleLayoutBuilder.maxColumnsFor(800.0))
        assertEquals(2, FumeArticleLayoutBuilder.maxColumnsFor(600.0))
        assertEquals(1, FumeArticleLayoutBuilder.maxColumnsFor(400.0))
        // round(1240*0.0065)=8
        assertEquals(8.0, FumeArticleLayoutBuilder.gapFor(1240.0, 4), 1e-6)
        assertEquals(6.0, FumeArticleLayoutBuilder.gapFor(100.0, 4), 1e-6)
    }

    @Test
    fun `chorus short lines become hero blocks`() {
        assertEquals(
            FumeVariant.HERO,
            FumeArticleLayoutBuilder.chooseNaturalBlockVariant(line("为你弹奏", 0.0, 2.0, chorus = true), 0, 10),
        )
        assertEquals(
            FumeVariant.HERO,
            FumeArticleLayoutBuilder.chooseNaturalBlockVariant(line("为你弹奏", 0.0, 2.0), 5, 10),
        )
        // 第 1 条（index=0）既不落在 %6 上，随机数也不一定过阈值 → 与 Folia 同式，两种都可能。
        val first = FumeArticleLayoutBuilder.chooseNaturalBlockVariant(line("abcdefg", 0.0, 2.0), 0, 10)
        assertEquals(
            if (FumeMath.seeded("abcdefg:0") > 0.965) FumeVariant.HERO else FumeVariant.BODY,
            first,
        )
    }

    @Test
    fun `fallback hero picks the most central acceptable block when none is natural`() {
        val entries = listOf(
            line("一二三四五六七八九十一二三四五六七八九十十一二三四五六七八九十", 0.0, 1.0) to 0,
            line("短句居中最好", 1.0, 2.0) to 1,
            line("另一条很长的句子在这里继续延伸下去直到超过可接受的长度限制为止啊啊", 2.0, 3.0) to 2,
        )
        assertEquals(1, FumeArticleLayoutBuilder.chooseFallbackHeroBlockIndex(entries))
    }

    @Test
    fun `layout cache key is stable and geometry sensitive`() {
        val lines = listOf(line("为你弹奏", 0.0, 2.0), line("萧邦的夜曲", 2.0, 4.0))
        val viewport = FumeViewport(720.0, 1600.0)
        val a = FumeArticleLayoutBuilder.buildLayoutCacheKey(lines, viewport, theme, 1.0, FumeTuning.DEFAULT)
        val b = FumeArticleLayoutBuilder.buildLayoutCacheKey(lines, viewport, theme, 1.0, FumeTuning.DEFAULT)
        assertEquals(a, b)
        val c = FumeArticleLayoutBuilder.buildLayoutCacheKey(lines, viewport, theme, 1.3, FumeTuning.DEFAULT)
        assertTrue(a != c)
    }

    @Test
    fun `build lays out every non-empty line into a block with full grapheme coverage`() {
        val lines = listOf(
            line("一群嗜血的蚂蚁", 0.0, 4.0),
            line("为你弹奏萧邦的夜曲", 4.0, 8.0, chorus = true),
            line("纪念我死去的爱情", 8.0, 12.0),
            line("   ", 12.0, 13.0),
        )
        val layout = FumeArticleLayoutBuilder.build(
            lines, FumeViewport(720.0, 1600.0), theme, 1.0, FumeTuning.DEFAULT, FakeMeasurer(),
        )
        requireNotNull(layout)
        // 空行不参与排版。
        assertEquals(3, layout.blocks.size)
        assertEquals(3, layout.blockBySourceLineIndex.size)
        assertEquals(listOf(0, 1, 2), layout.chronologicalBlocks.map { it.sourceLineIndex })
        assertEquals(0.0, layout.firstRenderableStartTime, 1e-6)
        assertTrue(layout.height > 0.0)

        for (block in layout.blocks) {
            assertEquals(block.graphemes.size, block.advances.size)
            val covered = block.renderLines.sumOf { it.end - it.start }
            assertEquals(block.graphemes.size, covered)
            assertEquals(0, block.renderLines.first().start)
            assertEquals(block.graphemes.size, block.renderLines.last().end)
            assertTrue(block.width > 0.0)
            assertTrue(block.fontPx > 0.0)
            // 每行渲染段的字符要拼回该行文本。
            for (renderLine in block.renderLines) {
                assertEquals(renderLine.text, renderLine.segments.joinToString("") { it.text })
            }
        }
        // 副歌那句是 hero。
        assertEquals(FumeVariant.HERO, layout.blockBySourceLineIndex.getValue(1).variant)
    }

    @Test
    fun `layout is deterministic for the same inputs`() {
        val lines = (0 until 12).map { index -> line("第${index}句歌词", index * 2.0, index * 2.0 + 2.0) }
        val viewport = FumeViewport(720.0, 1600.0)
        val first = FumeArticleLayoutBuilder.build(lines, viewport, theme, 1.0, FumeTuning.DEFAULT, FakeMeasurer())
        val second = FumeArticleLayoutBuilder.build(lines, viewport, theme, 1.0, FumeTuning.DEFAULT, FakeMeasurer())
        requireNotNull(first)
        requireNotNull(second)
        assertEquals(
            first.blocks.map { listOf(it.id, it.x, it.y, it.width, it.height, it.fontPx) },
            second.blocks.map { listOf(it.id, it.x, it.y, it.width, it.height, it.fontPx) },
        )
    }

    @Test
    fun `word ranges and offset indexes line up with the block graphemes`() {
        val text = "为你弹奏萧邦的夜曲"
        val target = line(text, 0.0, 4.0)
        val ranges = FumeArticleLayoutBuilder.buildWordRangesFromWords(target, FumeMath.splitGraphemes(text))
        assertEquals(0, ranges.first().start)
        assertEquals(text.length, ranges.last().end)
        val index = FumeArticleLayoutBuilder.buildWordRangeIndexByOffset(text.length, ranges, false)
        assertEquals(text.length, index.size)
        assertEquals(0, index.first())
        assertEquals(ranges.size - 1, index.last())
    }

    @Test
    fun `greedy wrap never exceeds inner width unless a single grapheme does`() {
        val graphemes = List(10) { "字" }
        val advances = List(10) { 30.0 }
        val wraps = FumeArticleLayoutBuilder.wrapGraphemes(graphemes, advances, 100.0)
        assertEquals(listOf(0 to 3, 3 to 6, 6 to 9, 9 to 10), wraps)
    }
}