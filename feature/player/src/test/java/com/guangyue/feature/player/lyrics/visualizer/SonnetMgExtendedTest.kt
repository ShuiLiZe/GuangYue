package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金样：node --experimental-strip-types 直接跑 Folia sonnetAdditionalShotMg.ts 的 drawAdditionalSonnetShotMg（变体 36–99，
 * 每个变体 3 个种子 × 横竖两种尺寸），记录器把每次 moveTo / lineTo / … / stroke / fill 的参数记成一行（数值保留 4 位小数）。
 */
class SonnetMgExtendedTest {
    private class Rec : SonnetMgPen {
        val ops = ArrayList<Pair<String, List<Double>>>()
        private fun p(n: String, vararg a: Double): SonnetMgPen { ops += n to a.toList(); return this }
        override fun moveTo(x: Double, y: Double) = p("M", x, y)
        override fun lineTo(x: Double, y: Double) = p("L", x, y)
        override fun quadraticCurveTo(cx: Double, cy: Double, tx: Double, ty: Double) = p("Q", cx, cy, tx, ty)
        override fun bezierCurveTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, tx: Double, ty: Double) = p("B", c1x, c1y, c2x, c2y, tx, ty)
        override fun arc(cx: Double, cy: Double, r: Double, start: Double, end: Double, anticlockwise: Boolean) = p("A", cx, cy, r, start, end, if (anticlockwise) 1.0 else 0.0)
        override fun circle(x: Double, y: Double, r: Double) = p("C", x, y, r)
        override fun rect(x: Double, y: Double, w: Double, h: Double) = p("R", x, y, w, h)
        override fun stroke(color: Int, width: Double, alpha: Double) = p("S", color.toDouble(), width, alpha)
        override fun fill(color: Int, alpha: Double) = p("F", color.toDouble(), alpha)
    }

    @Test
    fun variants36to99MatchFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("sonnet_mg_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        assertEquals(384, rows.size)
        for (row in rows) {
            val f = row.split("|")
            val variant = f[0].toInt(); val seed = f[1].toLong(); val w = f[2].toDouble(); val h = f[3].toDouble()
            val want = f[4].trim().split(" ").filter { it.isNotEmpty() }.map { tok ->
                val (n, a) = tok.split(":", limit = 2)
                n to a.split(",").map { it.toDouble() }
            }
            val rec = Rec()
            assertTrue(drawSonnetMg36to99(rec, variant, minOf(w, h), w, h, seed, 0x112233, 0x445566))
            val tag = "variant $variant seed $seed ${w.toInt()}x${h.toInt()}"
            assertEquals("$tag op count", want.size, rec.ops.size)
            for (i in want.indices) {
                assertEquals("$tag op $i", want[i].first, rec.ops[i].first)
                val wa = want[i].second; val ga = rec.ops[i].second
                assertEquals("$tag op $i argc", wa.size, ga.size)
                for (k in wa.indices) assertEquals("$tag op $i ${want[i].first} arg $k", wa[k], ga[k], 2e-4 + 1e-9 * kotlin.math.abs(wa[k]))
            }
        }
        // 范围外不画
        assertTrue(!drawSonnetMg36to99(Rec(), 35, 360.0, 800.0, 360.0, 35L, 0, 0))
    }
}
