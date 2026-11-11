package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 花火片尾卡：resolveHanabiCreditsFrame 对拍 Folia（esbuild 打包 credits.ts 后 node 算），编排按 credits.ts 逐项核对。 */
class HanabiCreditsTest {
    private val golden = """
118 0 1.00000000000 0.00000000000 0.00000000000
119.9 0 1.00000000000 0.00000000000 0.00000000000
120.1 1 0.925925925926 0.444444444444 0.00000000000
120.5 1 0.500000000000 3.00000000000 0.316406250000
120.9 1 0.0740740740741 5.55555555556 0.957031250000
121.3 1 0.00000000000 6.00000000000 1.00000000000
121.8 1 0.00000000000 6.00000000000 1.00000000000
125 1 0.00000000000 6.00000000000 1.00000000000
""".trim().lines()

    @Test
    fun frameMatchesFolia() {
        for (row in golden) {
            val p = row.split(' ')
            val f = resolveHanabiCreditsFrame(p[0].toDouble(), 119.9)
            assertEquals(row, p[1] == "1", f.active)
            assertEquals(row, p[2].toDouble(), f.lyricAlpha, 1e-9)
            assertEquals(row, p[3].toDouble(), f.lyricBlur, 1e-9)
            assertEquals(row, p[4].toDouble(), f.posterAlpha, 1e-9)
            assertEquals(0.0, f.posterOffsetY, 0.0)
            assertEquals(1.0, f.posterScale, 0.0)
        }
        assertFalse(resolveHanabiCreditsLayerFrame(125.0, null, "a", null, null).active)
        assertFalse(resolveHanabiCreditsLayerFrame(125.0, 119.9, " ", null, "").active)
        assertTrue(resolveHanabiCreditsLayerFrame(125.0, 119.9, null, null, "album").active)
    }

    @Test
    fun titleLineSingsEachChar() {
        val line = hbCreditsTitleLine("夜曲")
        assertEquals(2, line.words.size)
        assertEquals(1.45, line.startTime, 1e-12)
        assertEquals(1.57, line.words[1].startTime, 1e-12)
        assertEquals(1.45 + 0.24 + 0.4, line.endTime, 1e-12)
        // 字多时整行压进 2 秒：step = min(0.12, 2 / n)。
        val long = hbCreditsTitleLine("一二三四五六七八九十一二三四五六七八九十")
        assertEquals(1.45 + 0.1, long.words[1].startTime, 1e-12)
    }

    @Test
    fun creditsPlanMatchesFolia() {
        val theme = HbTheme(0x0b0d24, 0xffffff, 0xff5d8f, 0x2f6fe0)
        val cr = createHanabiCredits(800.0, 360.0, theme, " 夜曲 ", "Jay Chou", "十一月的萧邦", HbTuning())
        assertNotNull(cr.text)
        assertEquals(4, cr.bursts.size)
        val crown = cr.bursts[0]
        assertEquals("kamuro", crown.spec.kind)
        assertEquals(150.0, crown.spec.count, 0.0)
        assertEquals(0.46, crown.spec.radius, 0.0)
        assertEquals(4.2, crown.spec.duration, 0.0)
        assertEquals(listOf(0, 1, 3), crown.spec.colors)
        assertEquals(400.0, crown.x, 1e-9); assertEquals(360 * 0.34, crown.y, 1e-9); assertEquals(1.1, crown.at, 1e-12)
        assertEquals("willow-ring", cr.bursts[3].spec.kind)
        assertEquals(1.1 + 2.2, cr.bursts[3].at, 1e-12)
        val l0 = cr.launches[0]
        assertEquals(400.0 - 16.0, l0.fromX, 1e-9); assertEquals(360 * 1.03, l0.fromY, 1e-9)
        assertEquals(1.1 - 0.45, l0.at, 1e-12); assertEquals(0.45, l0.duration, 0.0)
        assertEquals(160.0 + 16.0, cr.launches[1].fromX, 1e-9)  // 左千輪 width*0.2，奇数号从右侧 +2% 升空
        // 艺人大写在 0.66、专辑在 0.35；曲名两字唱完 1.45 + 0.24 后艺人开始显出，专辑再晚 0.3 s。
        assertEquals("JAY CHOU", cr.details[0].text)
        assertEquals(0.66, cr.details[0].y, 0.0)
        assertEquals(13.0, cr.details[0].size, 1e-9)
        assertEquals(1.69, cr.details[0].delay, 1e-12)
        assertEquals(1.99, cr.details[1].delay, 1e-12)
        assertEquals(13.0 * 0.8, cr.details[1].size, 1e-9)
        cr.update(1.69 + 0.45)
        assertEquals(0.5 * 0.92, cr.details[0].alpha, 1e-9)
        assertEquals(0.5 * 360 * 0.02, cr.details[0].offsetY, 1e-9)
        // 没有曲名：艺人 1.2 s 起显出。
        val noTitle = createHanabiCredits(800.0, 360.0, theme, null, "A", null, HbTuning())
        assertNull(noTitle.text)
        assertEquals(1.2, noTitle.details.single().delay, 1e-12)
    }
}
