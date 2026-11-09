package com.guangyue.feature.player.lyrics.visualizer.tempera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6 凝彩②：构图逐节点对拍。金标 `tempera_comp_golden.txt` 由 Folia 源码（temperaCompositions.ts + compositions 目录，
 * temperaShapes.ts 换成记录器，esbuild 打包后 node 跑）对 121 种构图 × 横屏 800×360 / 竖屏 360×780 各画一遍生成：
 * K 行是上下文（seed、showDecor、gradient、bleed、flowAngle、decor），N 行是每次 ctx.add 的节点 + 入场参数，C 行是 createGroup。
 * 只对拍已经移植的构图族；全部移植完后 [allKindsPorted] 打开。
 */
class TemperaCompositionTest {
    private class Block(val kind: String, val width: Int, val k: List<String>, val nodes: List<String>, val groups: List<String>)

    private val blocks: List<Block> by lazy {
        val lines = javaClass.classLoader!!.getResourceAsStream("tempera_comp_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        val out = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val k = lines[i].split(' ')
            require(k[0] == "K")
            val nodes = ArrayList<String>(); val groups = ArrayList<String>()
            i++
            while (i < lines.size && !lines[i].startsWith("K ")) {
                if (lines[i].startsWith("N ")) nodes += lines[i] else groups += lines[i]
                i++
            }
            out += Block(k[1], k[2].toInt(), k, nodes, groups)
        }
        out
    }

    private fun fmt(v: Double?): String = if (v == null) "_" else v.toString()

    private fun record(item: TpItem): List<String> {
        val o = item.options
        val opts = listOf(o.alpha, o.enterDX, o.enterDY, o.delay, o.span).joinToString(",") { fmt(it) } + ",${if (o.drift) 1 else 0}${if (o.grow) 1 else 0}"
        val base = listOf(item.node.typeId(), item.node.tone.id, item.parent?.let { "g${it.id}" } ?: "-", opts, item.node.nums.joinToString(",") { it.toString() })
        return if (item.node.holes.isEmpty()) base else base + item.node.holes.joinToString("|") { h -> h.joinToString(",") { it.toString() } }
    }

    private fun sameTokens(expected: String, actual: String, what: String) {
        val e = expected.split(',', '|'); val a = actual.split(',', '|')
        assertEquals("$what 数目 ($expected | $actual)", e.size, a.size)
        e.indices.forEach { j ->
            if (e[j] == "_" || a[j] == "_" || e[j].length == 2 && e[j].all { it == '0' || it == '1' } && j == e.size - 1 && what.endsWith("opts")) {
                assertEquals("$what[$j]", e[j], a[j])
            } else {
                val ev = e[j].toDoubleOrNull()
                if (ev == null) assertEquals("$what[$j]", e[j], a[j])
                else assertEquals("$what[$j]", ev, a[j].toDouble(), 2e-5 + abs(ev) * 1e-9)
            }
        }
    }

    private fun abs(v: Double) = kotlin.math.abs(v)

    private fun check(block: Block) {
        val k = block.k
        val w = block.width.toDouble()
        val h = if (block.width == 800) 360.0 else 780.0
        val ctx = TpCompositionContext(
            kind = block.kind,
            decor = TpDecorLite(k[8], k[9].toDouble(), k[10].toInt(), k[11].toLong()),
            width = w, height = h, seed = k[3].toLong(), showDecor = k[4] == "1",
            bleed = k[6].toDouble(), flowAngle = k[7].toDouble(), gradient = k[5] == "1",
        )
        drawTemperaComposition(ctx)
        val tag = "${block.kind} ${block.width}"
        assertEquals("$tag 节点数", block.nodes.size, ctx.items.size)
        block.nodes.forEachIndexed { n, line ->
            // N kind W type tone parent opts nums [holes]
            val g = line.split(' ')
            val r = record(ctx.items[n])
            val what = "$tag #$n"
            assertEquals("$what type", g[3], r[0])
            assertEquals("$what tone", g[4], r[1])
            assertEquals("$what parent", g[5], r[2])
            sameTokens(g[6], r[3], "$what opts")
            sameTokens(g[7], r[4], "$what nums")
            if (g.size > 8) sameTokens(g[8], r[5], "$what holes")
        }
        assertEquals("$tag 组数", block.groups.size, ctx.groups.size)
        block.groups.forEachIndexed { n, line ->
            val g = line.split(' ')
            val grp = ctx.groups[n]
            assertEquals(g[4].toDouble(), grp.rotation, 2e-5)
            assertEquals(g[5].toDouble(), grp.x, 2e-5)
            assertEquals(g[6].toDouble(), grp.y, 2e-5)
        }
    }

    @Test
    fun portedCompositionsMatchFolia() {
        val ported = blocks.filter { temperaCompositionImplemented(it.kind) }
        assertTrue(ported.isNotEmpty())
        ported.forEach { check(it) }
    }

    @Test
    fun hatchLinesStayInsidePolygon() {
        val lines = buildHatchLines(rectPolygon(0.0, 0.0, 100.0, 50.0), TpHatchSpec(kotlin.math.PI / 4, 9.0, 1.5))
        assertTrue(lines.isNotEmpty())
        lines.forEach { l -> listOf(l.x1, l.x2).forEach { assertTrue(it >= -1e-6 && it <= 100 + 1e-6) } }
    }
}
