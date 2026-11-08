package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NebbiaTimingTest {
    private fun line(t0: Double, t1: Double, text: String) = Line(words = emptyList(), startTime = t0, endTime = t1, fullText = text)

    /** 一个「字」：一横一竖两笔（em 坐标）。 */
    private val cross: NebbiaGlyphShape = NebbiaGlyphs.measure(
        listOf(
            listOf(doubleArrayOf(0.2, 0.5), doubleArrayOf(0.8, 0.5)),
            listOf(doubleArrayOf(0.5, 0.2), doubleArrayOf(0.5, 0.8)),
        ),
    )!!

    @Test
    fun fogStartsHoldAfterNextLine() {
        // Folia fogStartOf：下一句开始（最多唱完 6 秒）后再保持 hold 1.2 秒
        assertEquals(4.2, NebbiaSceneBuilder.fogStartOf(2.0, 3.0), 1e-9)
        assertEquals(9.2, NebbiaSceneBuilder.fogStartOf(2.0, 20.0), 1e-9)
        assertEquals(9.2, NebbiaSceneBuilder.fogStartOf(2.0, Double.POSITIVE_INFINITY), 1e-9)
    }

    @Test
    fun linesFogBackOverRefogSeconds() {
        val src = NebbiaComposer.buildLines(listOf(line(0.0, 2.0, "一二"), line(30.0, 32.0, "三四")))
        val laid = NebbiaSceneBuilder.build(src, 1600.0, 720.0, 1.0, 52.0, 2.0) { cross }
        val l0 = laid[0]
        // 下一句 30 秒才开始：唱完 6 秒后 + hold → 9.2；屏上空着多留到最多 16 秒或第 3 句之后，这里两句不重叠，多留取不到更晚
        assertTrue(l0.fogAt >= 9.2)
        assertEquals(1.0, NebbiaSceneBuilder.lineValues(l0, l0.fogAt)[0], 1e-9)
        assertEquals(0.5, NebbiaSceneBuilder.lineValues(l0, l0.fogAt + NebbiaFixed.REFOG / 2)[0], 1e-9)
        assertEquals(0.0, NebbiaSceneBuilder.lineValues(l0, l0.fogAt + NebbiaFixed.REFOG)[0], 1e-9)
        // 每个字按自然书写速度一个接一个写，不早于开唱
        val c = l0.chars.filter { it.glyph != null }
        assertEquals(2, c.size)
        assertTrue(c[1].wt >= c[0].wt + c[0].wd - 1e-9)
        assertEquals(0.0, c[0].appearDur, 0.0)
    }

    @Test
    fun skeletonTracesABar() {
        val sk = NebbiaSkeleton.SK
        val img = ByteArray(sk * sk)
        for (y in 44..52) for (x in 10..85) img[y * sk + x] = 1
        val polys = NebbiaSkeleton.polys(img)
        assertNotNull(polys)
        val g = NebbiaGlyphs.measure(polys!!)!!
        assertTrue(g.strokes.size in 1..3)
        assertTrue(g.total > 0.6)
    }

    @Test
    fun cjkSplitsPerCharLatinPerWord() {
        val u = nebbiaUnits(line(0.0, 3.0, "夜 rain falls"))
        assertEquals(listOf("夜", "rain", "falls"), u.map { it.first })
        assertEquals(1.0, u[1].second, 1e-9)
        val w = nebbiaUnits(Line(listOf(Word("你好", 1.0, 2.0)), 1.0, 2.0, "你好"))
        assertEquals(1.5, w[1].second, 1e-9)
    }
}
