package com.guangyue.feature.player.lyrics.visualizer.tempera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 凝彩片尾海报对拍。金标 `tempera_credits_golden.txt` 由 Folia 源码 buildTemperaCreditsPoster / resolveTemperaCreditsFrame
 * （esbuild 打包，假 Pixi 记录 Graphics / Text，Text 高 = 字号 × 1.2）生成，脚本 docs/port/scripts/tempera-credits-golden-gen.js。
 * N 行：每个 child 的类型 / 色位 / 线宽 / 透明度 / 多边形长度与前 4 个数 / 网线段数 / 枢轴；T 行：updateTime(e) 后每个 child 的
 * alpha、visible、位置、缩放；F 行：片尾帧。
 */
class TemperaCreditsTest {
    private val golden: List<String> by lazy {
        javaClass.classLoader!!.getResourceAsStream("tempera_credits_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
    }
    private val cases = listOf(
        Triple(arrayOf<String?>("夜曲", "周杰伦", "十一月的萧邦"), 800.0 to 360.0, 1.0),
        Triple(arrayOf<String?>("Blue Bird", "いきものがかり", null), 360.0 to 800.0, 1.2),
        Triple(arrayOf<String?>(null, null, "Album Only"), 640.0 to 480.0, 0.9),
    )

    private fun near(expected: String, actual: Double, what: String) {
        val e = expected.toDouble()
        assertEquals(what, e, actual, 1e-6 * maxOf(1.0, kotlin.math.abs(e)))
    }

    private fun build(c: Int): TpCreditsPoster {
        val (meta, size, fs) = cases[c]
        return buildTemperaCreditsPoster(meta[0], meta[1], meta[2], size.first, size.second, fs) { _, fontSize, _ -> fontSize * 1.2 }
    }

    private fun segs(node: TpNode): Int = when (node.type) {
        TpNodeType.HATCH -> buildHatchLines(node.nums.copyOfRange(4, node.nums.size), TpHatchSpec(node.nums[0], node.nums[1], node.nums[2])).size
        TpNodeType.LINES -> (node.nums.size - 2) / 4
        TpNodeType.CROSSES -> (node.nums.size - 2) / 4 * 2
        else -> 0
    }

    @Test
    fun posterNodesMatchFolia() {
        for (c in cases.indices) {
            val poster = build(c)
            val head = golden.first { it.startsWith("CASE $c ") }.split(' ')
            assertEquals("case $c children", head[5].toInt(), poster.items.size + 1)
            val rows = golden.filter { it.startsWith("N $c ") }
            rows.forEach { row ->
                val p = row.split(' ')
                val i = p[2].toInt()
                if (p[3] == "title") {
                    val parts = row.substringAfter(" title ").split(" ; ")
                    assertEquals(parts.size, poster.lines.size)
                    parts.forEachIndexed { k, s ->
                        val f = s.split('|')
                        val line = poster.lines[k]
                        assertEquals(f[0], line.text)
                        near(f[1], line.fontSize, "c$c title size $k")
                        near(f[2], poster.wrapWidth, "c$c wrap")
                        near(f[3], line.offsetY, "c$c title y $k")
                        near(f[4], line.alpha, "c$c title alpha $k")
                    }
                    assertTrue(poster.items[i - 1].node == null)
                    return@forEach
                }
                val node = if (i == 0) poster.ground else poster.items[i - 1].node!!
                val isStroke = p[4] == "stroke"
                assertEquals("c$c n$i op", isStroke, node.type != TpNodeType.FILL)
                assertEquals("c$c n$i tone", p[5], node.tone.id)
                val (width, alpha, poly) = when (node.type) {
                    TpNodeType.FILL -> Triple(0.0, node.nums[0], node.nums.copyOfRange(1, node.nums.size))
                    TpNodeType.OUTLINE -> Triple(node.nums[0], node.nums[1], node.nums.copyOfRange(2, node.nums.size))
                    TpNodeType.HATCH -> Triple(node.nums[2], node.nums[3], DoubleArray(0))
                    else -> Triple(node.nums[0], node.nums[1], DoubleArray(0))
                }
                near(p[6], width, "c$c n$i width")
                near(p[7], alpha, "c$c n$i alpha")
                assertEquals("c$c n$i poly", p[8].toInt(), poly.size)
                var k = 9
                if (poly.isNotEmpty()) { for (j in 0 until 4) near(p[9 + j], poly[j], "c$c n$i poly$j"); k = 13 } else k = 10
                assertEquals("c$c n$i segs", p[k].removePrefix("segs=").toInt(), segs(node))
                val pv = if (i == 0) 0.0 to 0.0 else poster.items[i - 1].let { it.pivotX to it.pivotY }
                near(p[k + 2], pv.first, "c$c n$i pivotX")
                near(p[k + 3], pv.second, "c$c n$i pivotY")
            }
        }
    }

    @Test
    fun posterMotionMatchesFolia() {
        for (c in cases.indices) {
            val poster = build(c)
            golden.filter { it.startsWith("T $c ") }.forEach { row ->
                val p = row.split(' ')
                poster.updateTime(p[2].toDouble())
                val cells = p.drop(3)
                assertEquals(poster.items.size + 1, cells.size)
                cells.drop(1).forEachIndexed { i, cell ->
                    val f = cell.split(',')
                    val item = poster.items[i]
                    val what = "c$c e=${p[2]} item $i"
                    near(f[0], item.alpha, "$what alpha")
                    assertEquals("$what visible", f[1] == "1", item.visible)
                    near(f[2], item.x, "$what x")
                    near(f[3], item.y, "$what y")
                    near(f[4], item.scale, "$what scale")
                }
            }
        }
    }

    @Test
    fun creditsFrameMatchesFolia() {
        golden.filter { it.startsWith("F ") }.forEach { row ->
            val p = row.split(' ')
            val fr = resolveTemperaCreditsFrame(p[1].toDouble(), 119.9)
            assertEquals(row, p[2] == "1", fr.active)
            near(p[3], fr.lyricAlpha, "$row lyric")
            near(p[4], fr.posterAlpha, "$row poster")
            near(p[5], fr.posterOffsetY, "$row offset")
            near(p[6], fr.posterScale, "$row scale")
        }
        assertTrue(hasTemperaCreditsMetadata(null, " ", "a"))
        assertTrue(!hasTemperaCreditsMetadata(null, " ", ""))
    }
}
