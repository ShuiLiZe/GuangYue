// 对应 Folia 源码：src/utils/lyrics/wordSegmentation.ts（全文）
//   isWordLikeText / segmentsFromBoundaries / isValidWordSegmentation / getWordSegmentationKey
//   segmentLyricWords（保存分词 → icu 自动分词 → Intl.Segmenter 兜底）
//   splitEdgeWhitespace（保存分词的边缘空白要拆成独立片段）
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSegmentationTest {

    private fun line(
        text: String,
        wordSegments: List<String>? = null,
        auto: List<String>? = null,
    ) = Line(
        words = emptyList(),
        startTime = 0.0,
        endTime = 3.0,
        fullText = text,
        wordSegments = wordSegments,
        autoWordSegments = auto,
    )

    // ---------- 兜底切分（对应 segmentTextWords → splitLyricGraphemes 回退） ----------

    @Test
    fun `空文本不产生片段`() {
        assertEquals(0, WordSegmentation.segmentByRules("").size)
    }

    @Test
    fun `CJK 每字一个片段`() {
        val parts = WordSegmentation.segmentByRules("夜曲")
        assertEquals(2, parts.size)
        assertEquals("夜", parts[0].segment)
        assertEquals("曲", parts[1].segment)
        assertTrue(parts.all { it.isWordLike })
    }

    @Test
    fun `拉丁词连成一个片段`() {
        val parts = WordSegmentation.segmentByRules("Chopin's nocturne")
        // "Chopin's" / " " / "nocturne"
        assertEquals(3, parts.size)
        assertEquals("Chopin's", parts[0].segment)
        assertTrue(parts[0].isWordLike)
        assertEquals(" ", parts[1].segment)
        assertFalse("空白不算词（同 Intl.Segmenter 的 isWordLike=false）", parts[1].isWordLike)
        assertEquals("nocturne", parts[2].segment)
    }

    @Test
    fun `标点与空白各成片段且 isWordLike 为假（同 PUNCTUATION_ONLY）`() {
        val parts = WordSegmentation.segmentByRules("夜曲， 好听")
        assertEquals("夜曲， 好听", parts.joinToString("") { it.segment })
        assertFalse(parts.first { it.segment == "，" }.isWordLike)
        assertFalse(parts.first { it.segment == " " }.isWordLike)
    }

    @Test
    fun `片段 index 递增且拼回原文`() {
        val text = "为你 弹奏 Chopin 的夜曲"
        val parts = WordSegmentation.segmentByRules(text)
        assertEquals(text, parts.joinToString("") { it.segment })
        assertEquals(parts.indices.toList(), parts.map { it.index })
    }

    // ---------- 分词优先级（segmentLyricWords） ----------

    @Test
    fun `保存分词优先于自动分词`() {
        val parts = WordSegmentation.segmentLyricWords(
            line("为你弹奏萧邦的夜曲", wordSegments = listOf("为你", "弹奏", "萧邦的", "夜曲"), auto = listOf("为你弹奏", "萧邦的夜曲")),
        )
        assertEquals(listOf("为你", "弹奏", "萧邦的", "夜曲"), parts.map { it.segment })
    }

    @Test
    fun `保存分词拼不回原文时退回自动分词`() {
        val parts = WordSegmentation.segmentLyricWords(
            line("为你弹奏萧邦的夜曲", wordSegments = listOf("为你", "弹奏"), auto = listOf("为你弹奏", "萧邦的夜曲")),
        )
        assertEquals(listOf("为你弹奏", "萧邦的夜曲"), parts.map { it.segment })
    }

    @Test
    fun `都没有时走规则兜底`() {
        val parts = WordSegmentation.segmentLyricWords(line("夜曲"))
        assertEquals(listOf("夜", "曲"), parts.map { it.segment })
    }

    // ---------- 校验 / 指纹 ----------

    @Test
    fun `isValidWordSegmentation 要求非空且拼回原文`() {
        assertTrue(WordSegmentation.isValidWordSegmentation(listOf("夜", "曲"), "夜曲"))
        assertFalse(WordSegmentation.isValidWordSegmentation(emptyList(), "夜曲"))
        assertFalse(WordSegmentation.isValidWordSegmentation(listOf("夜"), "夜曲"))
        assertFalse(WordSegmentation.isValidWordSegmentation(listOf("曲", "夜"), "夜曲"))
    }

    @Test
    fun `分词键是各片段长度串（对应 getWordSegmentationKey）`() {
        assertEquals("2|2|3|2", WordSegmentation.getWordSegmentationKey(listOf("为你", "弹奏", "萧邦的", "夜曲")))
        assertEquals("", WordSegmentation.getWordSegmentationKey(emptyList()))
    }
}
