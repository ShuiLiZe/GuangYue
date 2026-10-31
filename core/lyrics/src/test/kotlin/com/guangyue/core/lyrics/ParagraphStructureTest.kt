// 对应 Folia 源码：src/components/visualizer/paragraphStructure.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphStructureTest {

    private fun line(start: Double, end: Double, text: String = "测试", songPart: String? = null, chorus: Boolean = false) =
        Line(words = emptyList(), startTime = start, endTime = end, fullText = text, songPart = songPart, isChorus = chorus)

    @Test
    fun `结构行渲染结束时间不越过下一行开始`() {
        val lines = listOf(line(0.0, 5.0), line(2.0, 4.0))
        val structure = ParagraphStructure.buildStructureLines(lines)
        // 第一行被下一行开始（2.0）夹住
        assertEquals(2.0, structure[0].renderEndTime, 1e-12)
        // 第二行没有下一行，取自己的渲染结束时间（含出场 0.32s + hold）
        assertEquals(4.38, structure[1].renderEndTime, 1e-12)
    }

    @Test
    fun `空隙阈值 = 中位数 x 2_5，钳在 1_25 到 3_5`() {
        // 间隙：2.0 / 4.0 / 6.0 → 中位数 4.0 → 10.0 钳到 3.5
        val wide = listOf(line(0.0, 0.0), line(2.0, 2.0), line(6.0, 6.0), line(12.0, 12.0))
        assertEquals(3.5, ParagraphStructure.resolveParagraphGapThreshold(wide), 1e-12)

        // 间隙：0.4 / 0.4 → 中位数 0.4 → 1.0 钳到 1.25
        val narrow = listOf(line(0.0, 1.0), line(1.4, 2.4), line(2.8, 3.8))
        assertEquals(1.25, ParagraphStructure.resolveParagraphGapThreshold(narrow), 1e-12)
    }

    @Test
    fun `无空隙时阈值取中位数默认 0_5 后钳到 1_25`() {
        val lines = listOf(line(0.0, 1.0), line(1.0, 2.0))
        assertEquals(1.25, ParagraphStructure.resolveParagraphGapThreshold(lines), 1e-12)
    }

    @Test
    fun `元数据变化：blockIndex 或 songPart 都非空且不同`() {
        val a = line(0.0, 1.0).copy(blockIndex = 0)
        val b = line(1.0, 2.0).copy(blockIndex = 1)
        val c = line(1.0, 2.0).copy(songPart = "chorus")
        assertEquals(true, ParagraphStructure.metadataChanged(a, b))
        assertEquals(true, ParagraphStructure.metadataChanged(line(1.0, 2.0).copy(songPart = "verse"), c))
        // 一方为空 → 不算变化
        assertEquals(false, ParagraphStructure.metadataChanged(line(1.0, 2.0), c))
    }

    @Test
    fun `超过最大行数的段落会被切开且每段不超限`() {
        val lines = (0 until 14).map { line(it * 1.0, it * 1.0 + 0.9) }
        val drafts = ParagraphStructure.draftParagraphs(
            ParagraphStructure.buildStructureLines(lines),
            threshold = 1.25,
        )
        assertTrue(drafts.isNotEmpty())
        assertTrue(drafts.all { it.lines.size <= ParagraphStructureParams.DEFAULT.maxLines })
    }

    @Test
    fun `空隙处切段`() {
        val lines = listOf(
            line(0.0, 1.0), line(1.5, 2.5),
            line(10.0, 11.0), line(11.5, 12.5),
        )
        val drafts = ParagraphStructure.draftParagraphs(
            ParagraphStructure.buildStructureLines(lines),
            threshold = 2.0,
        )
        assertEquals(2, drafts.size)
        assertEquals(ParagraphBoundary.TIME_GAP, drafts[1].boundary)
    }

    @Test
    fun `段落分类：副歌 break 尾声 breath lift verse`() {
        val structure = ParagraphStructure.buildStructureLines(listOf(line(0.0, 2.0), line(2.0, 4.0)))
        assertEquals(ParagraphKind.CHORUS, ParagraphStructure.classifyParagraph(
            ParagraphStructure.buildStructureLines(listOf(line(0.0, 4.0, chorus = true), line(4.0, 6.0))), 0, 2,
        ))
        assertEquals(ParagraphKind.BREAK, ParagraphStructure.classifyParagraph(
            ParagraphStructure.buildStructureLines(listOf(line(0.0, 4.0, songPart = "間奏"), line(4.0, 6.0))), 0, 2,
        ))
        // 最后一段
        assertEquals(ParagraphKind.OUTRO, ParagraphStructure.classifyParagraph(structure, 1, 2))
        // 时长短且词少 → breath（单行、非末段：index 0 < total-1）
        assertEquals(ParagraphKind.BREATH, ParagraphStructure.classifyParagraph(
            ParagraphStructure.buildStructureLines(listOf(line(0.0, 1.0, "短"))), 0, 3,
        ))
        // 标点多 → lift
        assertEquals(ParagraphKind.LIFT, ParagraphStructure.classifyParagraph(
            ParagraphStructure.buildStructureLines(listOf(
                line(0.0, 6.0, "你好吗？我很好！真的很好"),
                line(6.0, 12.0, "继续往前走吧朋友"),
            )), 0, 3,
        ))
        // 否则 verse
        assertEquals(ParagraphKind.VERSE, ParagraphStructure.classifyParagraph(
            ParagraphStructure.buildStructureLines(listOf(
                line(0.0, 10.0, "一路走来我们都在这里等待春天的消息慢慢到来"),
                line(10.0, 20.0, "继续往前走吧我的朋友不要回头"),
            )), 0, 3,
        ))
    }
}