package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 绘光十字爆闪（light/crossBurst.ts）与 Folia 对拍。金标 `lumiere_burst_golden.txt` 由 docs/port/scripts/lumiere-burst-golden-gen.ts
 * 用 Folia 源码 + 假 pixi（只记 sprite 属性）生成：
 *   PN 规格 个数；PB 规格 行 字 时刻 尺寸 dy —— planBursts，5 行合成字表，三组规格（sung / sweep / 低密度兜底）。
 *   BL age 提亮；BS age 名 x y 宽 高 alpha tint rotation —— 一个十字（x 120.5 y 200.25 L 64 光色 1/0.78/0.52）各 age 的六张贴图。
 */
class LumiereBurstTest {
    private val golden by lazy {
        javaClass.classLoader!!.getResourceAsStream("lumiere_burst_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }
    private fun near(e: String, a: Double, what: String) { val v = e.toDouble(); assertEquals(what, v, a, 1e-7 * maxOf(1.0, kotlin.math.abs(v))) }

    private val specs = listOf(
        LumiereBurstSpec("sung", 0.35, 1, 0, 3.6, doubleArrayOf(1.0, 0.6, 0.45)),
        LumiereBurstSpec("sweep", 0.5, 3, 1, 3.0, doubleArrayOf(1.0, 0.8, 0.7)),
        LumiereBurstSpec("sung", 0.05, 2, 1, 3.2, doubleArrayOf(0.8, 0.9, 1.0)),
    )
    private val lines = (0..4).map { li -> List(3 + li * 2) { g -> (g * (li % 2 + 1)) to (10 + li * 3 + g * 0.21) } }

    @Test
    fun planMatchesFolia() {
        specs.forEachIndexed { si, spec ->
            val tr = lumierePlanBursts(spec, lines, "probe-0:lumiere-p6:$si")
            val rows = golden.filter { it[0] == "PB" && it[1] == "$si" }
            assertEquals(golden.first { it[0] == "PN" && it[1] == "$si" }[2].toInt(), tr.size)
            rows.forEachIndexed { i, r ->
                assertEquals(r[2].toInt(), tr[i].lineIndex); assertEquals(r[3].toInt(), tr[i].glyphIndex)
                near(r[4], tr[i].time, "time"); near(r[5], tr[i].size, "size"); near(r[6], tr[i].dy, "dy")
            }
        }
    }

    @Test
    fun spritesMatchFolia() {
        val ages = golden.filter { it[0] == "BL" }
        assertEquals(8, ages.size)
        for (bl in ages) {
            val age = bl[1].toDouble()
            near(bl[2], lumiereBurstLightBoost(age), "boost")
            val sprites = lumiereBurstSprites(LumiereBurstEvent(age, 120.5, 200.25, 64.0, doubleArrayOf(1.0, 0.78, 0.52)))
            val rows = golden.filter { it[0] == "BS" && it[1] == bl[1] }
            assertEquals(6, sprites.size)
            rows.forEachIndexed { i, r ->
                val s = sprites[i]
                near(r[3], s.x, "${r[2]} x"); near(r[4], s.y, "${r[2]} y"); near(r[5], s.width, "${r[2]} w"); near(r[6], s.height, "${r[2]} h")
                near(r[7], s.alpha, "${r[2]} a"); assertEquals("${r[2]} tint", r[8].toInt(), s.tint); near(r[9], s.rotation, "${r[2]} rot")
            }
        }
    }
}
