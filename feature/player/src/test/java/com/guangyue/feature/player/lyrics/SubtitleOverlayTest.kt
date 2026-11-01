package com.guangyue.feature.player.lyrics

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底部字幕层的纯函数单测 —— 照抄 Folia `VisualizerSubtitleOverlay.tsx` 的三条规则：
 * 过滤不可读行、非 current 行同文去重、当前句没有可读翻译时改画后面几行的预告。
 */
class SubtitleOverlayTest {

    private fun line(
        text: String,
        translation: String? = null,
        startTime: Double = 0.0,
    ): Line = Line(
        words = listOf(Word(text, startTime, startTime + 1.0)),
        startTime = startTime,
        endTime = startTime + 1.0,
        fullText = text,
        translation = translation,
    )

    // ---------------- hasReadableText：过滤 ----------------

    @Test
    fun hasReadableTextFiltersPlaceholders() {
        assertFalse(hasReadableText(null))
        assertFalse(hasReadableText(""))
        assertFalse(hasReadableText("   "))
        // Folia 明确点名的三种占位串。
        assertFalse(hasReadableText("//"))
        assertFalse(hasReadableText("◑◒◑"))
        assertFalse(hasReadableText("———"))
        assertFalse(hasReadableText("......"))
    }

    @Test
    fun hasReadableTextKeepsLyrics() {
        assertTrue(hasReadableText("夜曲"))
        assertTrue(hasReadableText("For you I play"))
        assertTrue(hasReadableText("2024"))
        // 纯符号（Folia 的 \p{L}|\p{N} 语义不认它）仍然算不可读。
        assertFalse(hasReadableText("♪"))
    }

    @Test
    fun readableLinesDropsPlaceholders() {
        val lines = listOf(line("夜曲"), line("//"), line("纪念我死去的爱情"), line("◑◒◑"))
        assertEquals(listOf("夜曲", "纪念我死去的爱情"), readableLines(lines).map { it.fullText })
    }

    // ---------------- rows：翻译轨 ----------------

    @Test
    fun translationRowWhenTranslationReadable() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("为你弹奏萧邦的夜曲", translation = "For you I play Chopin's nocturne"),
            nextLines = listOf(line("纪念我死去的爱情")),
        )
        assertTrue(content.visible)
        assertEquals(listOf(SubtitleRowSource.Translation), content.rows.map { it.source })
        assertEquals("For you I play Chopin's nocturne", content.rows.single().text)
        assertTrue(content.previews.isEmpty())
    }

    @Test
    fun placeholderTranslationIsFilteredOut() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("为你弹奏萧邦的夜曲", translation = "//"),
            nextLines = emptyList(),
        )
        assertTrue(content.rows.isEmpty())
    }

    @Test
    fun invisibleWhenShowTextFalse() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("夜曲", translation = "Nocturne"),
            nextLines = emptyList(),
            showText = false,
        )
        assertFalse(content.visible)
        assertTrue(content.rows.isEmpty())
        assertTrue(content.previews.isEmpty())
    }

    @Test
    fun invisibleWithoutActiveLine() {
        val content = resolveSubtitleOverlayContent(activeLine = null, nextLines = listOf(line("夜曲")))
        assertFalse(content.visible)
        assertTrue(content.previews.isEmpty())
    }

    // ---------------- 无翻译 → nextLines 预告 ----------------

    @Test
    fun fallsBackToNextLinesWhenNoTranslation() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("一群嗜血的蚂蚁"),
            nextLines = listOf(line("为你弹奏萧邦的夜曲"), line("纪念我死去的爱情")),
        )
        assertTrue(content.visible)
        assertTrue(content.rows.isEmpty())
        assertEquals(listOf("为你弹奏萧邦的夜曲", "纪念我死去的爱情"), content.previews)
    }

    @Test
    fun previewsSkipPlaceholderLines() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("一群嗜血的蚂蚁"),
            nextLines = listOf(line("//"), line("◑◒◑"), line("纪念我死去的爱情")),
        )
        assertEquals(listOf("纪念我死去的爱情"), content.previews)
    }

    @Test
    fun previewsEmptyWhenNextLinesAllPlaceholders() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("一群嗜血的蚂蚁"),
            nextLines = listOf(line("//"), line("———")),
        )
        assertTrue(content.rows.isEmpty())
        assertTrue(content.previews.isEmpty())
    }

    // ---------------- 去重（同一行内同文的非 next 行只画一次） ----------------

    @Test
    fun duplicateTranslationRowIsDropped() {
        // 翻译与后面某条轨同文时只保留第一条（Folia 的 rows 去重规则）。
        val content = resolveSubtitleOverlayContent(
            activeLine = line("夜曲", translation = "Nocturne"),
            nextLines = emptyList(),
            layout = listOf(SubtitleRowSource.Translation, SubtitleRowSource.Next),
        )
        assertEquals(1, content.rows.size)
        assertEquals("Nocturne", content.rows.single().text)
    }

    @Test
    fun layoutRowsKeepDistinctTexts() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("夜曲", translation = "Nocturne"),
            nextLines = listOf(line("纪念我死去的爱情")),
            layout = listOf(SubtitleRowSource.Translation, SubtitleRowSource.Next),
        )
        assertEquals(
            listOf(SubtitleRowSource.Translation, SubtitleRowSource.Next),
            content.rows.map { it.source },
        )
        assertEquals(listOf("Nocturne", "纪念我死去的爱情"), content.rows.map { it.text })
    }

    // ---------------- key / 字号 ----------------

    @Test
    fun keyFollowsActiveLineStartTime() {
        val content = resolveSubtitleOverlayContent(
            activeLine = line("夜曲", translation = "Nocturne", startTime = 4.0),
            nextLines = emptyList(),
        )
        assertEquals(4.0, content.key, 0.0001)
    }

    @Test
    fun subtitleFontSizesFollowFoliaClamp() {
        // 360dp 宽：translation clamp(1.125rem=18, 2.6vw=9.36, 1.25rem=20) = 18
        assertEquals(18f, clampFontDp(1.125, 2.6, 1.25, 360f), 0.001f)
        // 1080dp 宽（平板）：clamp(18, 28.08, 20) = 20
        assertEquals(20f, clampFontDp(1.125, 2.6, 1.25, 1080f), 0.001f)
        // 320dp 宽：upcoming clamp(14, 6.4, 16) = 14
        assertEquals(14f, clampFontDp(0.875, 2.0, 1.0, 320f), 0.001f)
    }

    @Test
    fun secondaryTrackConstantsMatchFolia() {
        assertEquals(0.88f, SUBTITLE_SECONDARY_SIZE_FACTOR, 0.0001f)
        assertEquals(0.82f, SUBTITLE_SECONDARY_OPACITY, 0.0001f)
        assertEquals(0.6f, SUBTITLE_OVERLAY_OPACITY, 0.0001f)
        assertEquals(240, SUBTITLE_ENTER_MS)
    }
}