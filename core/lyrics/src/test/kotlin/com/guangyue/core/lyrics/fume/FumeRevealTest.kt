// 对照 Folia test/unit/visualizer/fumeWordTiming.test.ts
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.LineRenderHints
import com.guangyue.core.lyrics.LineTransitionMode
import com.guangyue.core.lyrics.LyricSyllable
import com.guangyue.core.lyrics.TimingClass
import com.guangyue.core.lyrics.Word
import com.guangyue.core.lyrics.WordRevealMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FumeRevealTest {

    private fun line(fullText: String, words: List<Word>, overrides: Line.() -> Line = { this }): Line {
        val base = Line(
            words = words,
            startTime = words.firstOrNull()?.startTime ?: 0.0,
            endTime = words.lastOrNull()?.endTime ?: 0.0,
            fullText = fullText,
        )
        return base.overrides()
    }

    private fun graphemes(line: Line) = FumeMath.splitGraphemes(line.fullText)

    private fun slices(line: Line, ranges: List<FumeWordRange>) =
        ranges.map { line.fullText.substring(it.start, it.end) }

    private fun rangesOf(line: Line) = FumeArticleLayoutBuilder.buildWordRangesFromWords(line, graphemes(line))

    @Test
    fun `builds grapheme ranges directly from word text order for latin text`() {
        val line = line(
            "Oh, not again",
            listOf(
                Word("Oh", 0.0, 0.3),
                Word(", ", 0.3, 0.35),
                Word("not ", 0.35, 0.6),
                Word("again", 0.6, 1.1),
            ),
        )
        val ranges = rangesOf(line)
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 8, 8 to 13), ranges.map { it.start to it.end })
        assertEquals(listOf("Oh", ", ", "not ", "again"), slices(line, ranges))
    }

    @Test
    fun `keeps punctuation and quotes inside the owning word ranges`() {
        val line = line(
            "train’s gone",
            listOf(
                Word("train", 0.0, 0.5),
                Word("’", 0.5, 0.55),
                Word("s ", 0.55, 0.68),
                Word("gone", 0.68, 1.1),
            ),
        )
        assertEquals(listOf("train", "’", "s ", "gone"), slices(line, rangesOf(line)))
    }

    @Test
    fun `preserves downstream word alignment when punctuation tokens have zero duration`() {
        val line = line(
            "and come to , me",
            listOf(
                Word("and ", 0.0, 0.4),
                Word("come ", 0.4, 0.9),
                Word("to ", 0.9, 1.1),
                Word(", ", 1.1, 1.1),
                Word("me", 1.1, 1.8),
            ),
        ) { copy(endTime = 1.8) }
        val ranges = rangesOf(line)
        assertEquals(listOf("and ", "come ", "to ", ", ", "me"), slices(line, ranges))
        val last = ranges.last()
        assertEquals(line.fullText.length - 2, last.start)
        assertEquals(line.fullText.length, last.end)
    }

    @Test
    fun `attaches inter-word whitespace when words omit spaces but fullText keeps them`() {
        val line = line(
            "baby boy your hands and habit and love and love",
            listOf(
                Word("baby", 0.0, 0.5),
                Word("boy", 0.5, 1.0),
                Word("your", 1.0, 1.5),
                Word("hands", 1.5, 2.0),
                Word("and", 2.0, 2.5),
                Word("habit", 2.5, 3.0),
                Word("and", 3.0, 3.5),
                Word("love", 3.5, 4.0),
                Word("and", 4.0, 4.5),
                Word("love", 4.5, 5.0),
            ),
        )
        assertEquals(
            listOf("baby ", "boy ", "your ", "hands ", "and ", "habit ", "and ", "love ", "and ", "love"),
            slices(line, rangesOf(line)),
        )
    }

    @Test
    fun `supports overlapping word times while keeping printed count as the maximum continuous prefix`() {
        val line = line("ABCD", listOf(Word("AB", 0.0, 1.0), Word("CD", 0.4, 1.2)))
        val ranges = rangesOf(line)
        assertEquals(1, FumeReveal.printedGraphemeCount(line, ranges, 4, 0.55))
        assertEquals(3, FumeReveal.printedGraphemeCount(line, ranges, 4, 1.05))
        assertEquals(4, FumeReveal.printedGraphemeCount(line, ranges, 4, 1.25))
    }

    @Test
    fun `keeps the progressive frontier logic variant-agnostic`() {
        val line = line("again", listOf(Word("again", 0.0, 1.0)))
        val ranges = rangesOf(line)
        assertEquals(2.5, FumeReveal.printedGraphemeProgress(line, ranges, 5, 0.5), 1e-5)
    }

    @Test
    fun `uses syllable timing for printed grapheme count when available`() {
        val line = line(
            "hurricane",
            listOf(
                Word(
                    "hurricane", 1.0, 2.0,
                    syllables = listOf(LyricSyllable("hurri", 1.0, 1.4), LyricSyllable("cane", 1.4, 2.0)),
                ),
            ),
        )
        val ranges = rangesOf(line)
        assertEquals(6, FumeReveal.printedGraphemeCount(line, ranges, 9, 1.45))
    }

    @Test
    fun `keeps reveal completion aligned with line endTime even when renderEndTime is later`() {
        val line = line(
            "while inside",
            listOf(Word("while ", 77.92, 80.9), Word("inside", 80.9, 81.49)),
        ) {
            copy(
                renderHints = LineRenderHints(
                    rawDuration = 3.57,
                    timingClass = TimingClass.NORMAL,
                    renderEndTime = 81.87,
                    lineTransitionMode = LineTransitionMode.NORMAL,
                    wordRevealMode = WordRevealMode.NORMAL,
                ),
            )
        }
        val count = graphemes(line).size
        val ranges = rangesOf(line)
        assertTrue(FumeReveal.printedGraphemeCount(line, ranges, count, 81.2) < count)
        assertEquals(count, FumeReveal.printedGraphemeCount(line, ranges, count, 81.49))
        assertEquals(count.toDouble(), FumeReveal.printedGraphemeProgress(line, ranges, count, 81.49), 1e-6)
    }

    @Test
    fun `uses renderEndTime as the pass cutoff when there is enough gap before the next line`() {
        val line = line("linger", listOf(Word("li", 12.19, 12.37), Word("nger", 12.37, 12.55))) {
            copy(
                renderHints = LineRenderHints(
                    rawDuration = 2.79,
                    timingClass = TimingClass.NORMAL,
                    renderEndTime = 12.93,
                    lineTransitionMode = LineTransitionMode.NORMAL,
                    wordRevealMode = WordRevealMode.NORMAL,
                ),
            )
        }
        assertEquals(12.93, FumeReveal.linePassCutoffTime(line, 13.4), 1e-6)
        assertTrue(FumeReveal.visualProgressWithCutoff(12.55, 0.45, 12.7, 12.93) < 1.0)
    }

    @Test
    fun `cuts the pass window off at the next line start when renderEndTime would overlap it`() {
        val line = line("linger", listOf(Word("li", 12.19, 12.37), Word("nger", 12.37, 12.55))) {
            copy(
                renderHints = LineRenderHints(
                    rawDuration = 2.79,
                    timingClass = TimingClass.NORMAL,
                    renderEndTime = 12.93,
                    lineTransitionMode = LineTransitionMode.NORMAL,
                    wordRevealMode = WordRevealMode.NORMAL,
                ),
            )
        }
        val count = graphemes(line).size
        val ranges = rangesOf(line)
        val passCutoffTime = FumeReveal.linePassCutoffTime(line, 12.55)
        assertTrue(FumeReveal.printedGraphemeCount(line, ranges, count, 12.49) < count)
        assertEquals(count, FumeReveal.printedGraphemeCount(line, ranges, count, 12.55))
        assertEquals(count.toDouble(), FumeReveal.printedGraphemeProgress(line, ranges, count, 12.55), 1e-6)
        assertEquals(12.55, passCutoffTime, 1e-6)
        assertEquals(0.0, FumeReveal.visualProgressWithCutoff(12.55, 0.45, 12.55, passCutoffTime), 1e-6)
        assertEquals(1.0, FumeReveal.visualProgressWithCutoff(12.55, 0.45, 12.6, passCutoffTime), 1e-6)
    }
}