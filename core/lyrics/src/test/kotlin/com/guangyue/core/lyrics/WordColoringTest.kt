// 对应 Folia 源码：src/components/visualizer/wordColoring.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordColoringTest {

    private val red = "#FF0000"
    private val blue = "#0000FF"

    @Test
    fun `CJK 判定覆盖中日韩`() {
        assertTrue(WordColoring.isCjk("夜曲"))
        assertTrue(WordColoring.isCjk("こんにちは"))
        assertTrue(WordColoring.isCjk("안녕"))
        assertEquals(false, WordColoring.isCjk("hello"))
    }

    @Test
    fun `token 归一化：小写化并去掉非单词字符`() {
        assertEquals("night", WordColoring.normalizeWordColorToken("Night!"))
        assertEquals("abc_1", WordColoring.normalizeWordColorToken("A-b c_1"))
    }

    @Test
    fun `区间重叠判定是半开区间`() {
        val a = WordColorRange(0, 4, red, 1)
        assertEquals(true, WordColoring.rangesOverlap(a, WordColorRange(3, 8, blue, 1)))
        assertEquals(false, WordColoring.rangesOverlap(a, WordColorRange(4, 8, blue, 1)))
    }

    @Test
    fun `高优先级先占位，重叠者被丢弃，结果按偏移升序`() {
        val ranges = listOf(
            WordColorRange(0, 6, red, 1),
            WordColorRange(2, 4, blue, 5),
        )
        val selected = WordColoring.selectNonOverlappingRanges(ranges)
        assertEquals(1, selected.size)
        assertEquals(blue, selected[0].color)
    }

    @Test
    fun `matchers：CJK 用整串，英文按空格拆词并归一化`() {
        val cjk = WordColoring.prepareWordColorMatchers(listOf(WordColorEntry("夜曲", red)))
        assertEquals(listOf("夜曲"), cjk[0].cjkPhrases)
        assertEquals(2, cjk[0].priority)

        val english = WordColoring.prepareWordColorMatchers(listOf(WordColorEntry("Silent Night", blue)))
        assertEquals(listOf("silent", "night"), english[0].englishWords)
        assertEquals(6, english[0].priority)
    }

    @Test
    fun `开关关闭时不产生 matcher`() {
        assertTrue(WordColoring.prepareWordColorMatchers(listOf(WordColorEntry("夜曲", red)), keywordColoringEnabled = false).isEmpty())
        assertTrue(WordColoring.prepareWordColorMatchers(emptyList()).isEmpty())
    }

    @Test
    fun `CJK 短语在行内多次出现都着色`() {
        val ranges = WordColoring.buildWordColorRanges("夜曲夜曲", listOf(WordColorEntry("夜曲", red)))
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].startOffset)
        assertEquals(2, ranges[1].startOffset)
    }

    @Test
    fun `英文按词着色且大小写无关`() {
        val ranges = WordColoring.buildWordColorRanges("Silent night, silent night", listOf(WordColorEntry("silent", blue)))
        assertEquals(2, ranges.size)
        assertTrue(ranges.all { it.color == blue })
    }

    @Test
    fun `resolveWordColor：CJK 默认 target 包含 token`() {
        assertEquals(red, WordColoring.resolveWordColor("夜曲", listOf(WordColorEntry("夜曲前奏", red)), "#FFFFFF"))
        assertEquals("#FFFFFF", WordColoring.resolveWordColor("不匹配", listOf(WordColorEntry("夜曲", red)), "#FFFFFF"))
    }

    @Test
    fun `resolveWordColor：exact 模式要求完全相等`() {
        assertEquals(
            "#FFFFFF",
            WordColoring.resolveWordColor(
                "夜曲", listOf(WordColorEntry("夜曲前奏", red)), "#FFFFFF",
                cjkMatchMode = WordColorCjkMatchMode.EXACT,
            ),
        )
        assertEquals(
            red,
            WordColoring.resolveWordColor(
                "夜曲", listOf(WordColorEntry("夜曲", red)), "#FFFFFF",
                cjkMatchMode = WordColorCjkMatchMode.EXACT,
            ),
        )
    }

    @Test
    fun `resolveWordColor：英文整词匹配且返回回退色`() {
        assertEquals(blue, WordColoring.resolveWordColor("Silent", listOf(WordColorEntry("silent night", blue)), "#FFFFFF"))
        assertEquals("#FFFFFF", WordColoring.resolveWordColor("silence", listOf(WordColorEntry("silent night", blue)), "#FFFFFF"))
    }

    @Test
    fun `token 映射单趟指针，未计时 token 跳过`() {
        val ranges = listOf(WordColorRange(2, 4, red, 1))
        val tokens = listOf(
            WordColorToken("a", timed = false, startOffset = 2, endOffset = 4),
            WordColorToken("b", timed = true, startOffset = 2, endOffset = 4),
            WordColorToken("c", timed = true, startOffset = 10, endOffset = 12),
        )
        val map = WordColoring.resolveTokenColorMap(tokens, ranges)
        assertEquals(1, map.size)
        assertEquals(red, map["b"])
    }
}