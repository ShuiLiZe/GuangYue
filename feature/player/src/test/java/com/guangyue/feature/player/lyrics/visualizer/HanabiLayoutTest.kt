package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R7 花火⑥a：排版与 Folia hanabi/text/layout.ts 对拍。金标 `hanabi_layout_golden.txt`：10 句（中 / 日 / 中英混排）× 9 种排法，
 * 参数轮换（文字区、字号、倾斜、字距、词距、行距、最小字宽、阶梯、边距），逐字比位置 / 字号 / 转角 / 字框 / 行号，外加行划分、缩放、包围盒与词字号倍率。
 */
class HanabiLayoutTest {
    private fun close(msg: String, want: Double, got: Double) =
        assertEquals(msg, want, got, 2e-6 + 1e-7 * kotlin.math.abs(want))

    @Test
    fun layoutMatchesFolia() {
        val lines = javaClass.classLoader!!.getResourceAsStream("hanabi_layout_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        var cases = 0
        var i = 0
        while (i < lines.size) {
            val c = lines[i].split(' ')
            val glyphs = lines[i + 1].removePrefix("G ").split(';').map { s ->
                val f = s.split(',')
                HbLayoutGlyph(String(Character.toChars(f[0].toInt())), f[1].toDouble(), f[2] == "1", f[3] == "1", f[4].toInt())
            }
            val words = lines[i + 2].removePrefix("W ").split(';').map { s ->
                val f = s.split(',')
                HbLayoutWord(f[0].toInt(), f[1].toInt(), f[2] == "1", f[3] == "1", f[4].toDouble())
            }
            val e = lines[i + 3].split(' ')
            val texts = e[2].split(';').map { w -> w.split('.').joinToString("") { String(Character.toChars(it.toInt())) } }
            val params = HbLayoutParams(
                layout = c[1], regionWidth = c[2].toDouble(), regionHeight = c[3].toDouble(), fontPx = c[4].toDouble(), tilt = c[5].toDouble(),
                seed = c[6], tracking = c[7].toDouble(), wordGap = c[8].toDouble(), lineHeight = c[9].toDouble(),
                minAdvance = c[10].toDouble(), stairStep = c[11].toDouble(), padding = c[12].toDouble(),
            )
            val tag = "${c[1]} ${c[6]}"
            val scales = hanabiWordScales(texts.mapIndexed { k, t -> HbWordText(t, words[k].blank, words[k].space) }, e[1].toDouble(), c[6])
            scales.forEachIndexed { k, s -> close("$tag scale $k", words[k].scale, s) }
            val p = hanabiLayoutText(glyphs, words, params)
            val r = lines[i + 4].split(' ')
            close("$tag fit", r[1].toDouble(), p.fit)
            close("$tag fontPx", r[2].toDouble(), p.fontPx)
            close("$tag bx", r[3].toDouble(), p.bounds.x)
            close("$tag by", r[4].toDouble(), p.bounds.y)
            close("$tag bw", r[5].toDouble(), p.bounds.width)
            close("$tag bh", r[6].toDouble(), p.bounds.height)
            assertEquals("$tag vertical", r[7] == "1", p.vertical)
            assertEquals("$tag rows", r.getOrElse(8) { "" }, p.rows.joinToString("|") { it.joinToString(",") })
            lines[i + 5].removePrefix("P ").split(';').forEachIndexed { g, s ->
                val f = s.split(',')
                val q = p.glyphs[g]
                val got = listOf(q.x, q.y, q.size, q.advance, q.angle, q.rotation, q.boxW, q.boxH)
                got.forEachIndexed { k, v -> close("$tag glyph $g field $k", f[k].toDouble(), v) }
                assertEquals("$tag glyph $g row", f[8].toInt(), q.row)
            }
            cases++
            i += 6
        }
        assertEquals(90, cases)
    }
}
