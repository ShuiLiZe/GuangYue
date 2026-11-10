package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 绘光星空开场（light/starfall.ts）与 Folia 对拍。金标 `lumiere_starfall_golden.txt` 由 docs/port/scripts/lumiere-starfall-golden-gen.ts
 * 用 Folia 源码 + 假 pixi 生成：800×360，种子 probe-0:lumiere-p0 / p1，两组规格（botany 3.6 s 开场 / astral 620 星），无光束，fade 0.9。
 *   SN 规格 点数 十字星数；SF 规格 local time 可见点 Σx加权 Σy Σalpha Σ直径 可见拖尾 Σ长 Σ粗 Σalpha。
 */
class LumiereStarfallTest {
    private val golden by lazy {
        javaClass.classLoader!!.getResourceAsStream("lumiere_starfall_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }
    private fun near(e: String, a: Double, what: String) { val v = e.toDouble(); assertEquals(what, v, a, 1e-7 * maxOf(1.0, kotlin.math.abs(v))) }
    private val specs = listOf(LumiereStarfallSpec(300, 3.6, 50, 0.16, 0.25, 0.85), LumiereStarfallSpec(620, 2.4, 60, 0.6, 1.8, 1.2))

    @Test
    fun starfallMatchesFolia() {
        specs.forEachIndexed { si, spec ->
            val sf = LumiereStarfall(800.0, 360.0, "probe-0:lumiere-p$si", spec)
            val sn = golden.first { it[0] == "SN" && it[1] == "$si" }
            assertEquals(sn[2].toInt(), sf.points.size); assertEquals(sn[3].toInt(), sf.brightAt.count { it })
            for (r in golden.filter { it[0] == "SF" && it[1] == "$si" }) {
                sf.update(r[2].toDouble(), r[3].toDouble(), emptyList(), 0.9)
                var nv = 0; var sx = 0.0; var sy = 0.0; var sa = 0.0; var sw = 0.0
                sf.points.forEachIndexed { i, p -> if (p.visible) { nv++; sx += p.x * (i % 3 + 1); sy += p.y; sa += p.alpha; sw += p.width } }
                var tv = 0; var tw = 0.0; var th = 0.0; var ta = 0.0
                sf.trails.forEach { t -> if (t.visible) { tv++; tw += t.width; th += t.height; ta += t.alpha } }
                val at = "spec $si t ${r[2]}"
                assertEquals(at, r[4].toInt(), nv); near(r[5], sx, "$at Σx"); near(r[6], sy, "$at Σy"); near(r[7], sa, "$at Σa"); near(r[8], sw, "$at Σw")
                assertEquals(at, r[9].toInt(), tv); near(r[10], tw, "$at Σtw"); near(r[11], th, "$at Σth"); near(r[12], ta, "$at Σta")
            }
        }
    }

    @Test
    fun setupShortensOpening() {
        val base = LumiereStarfallSpec(300, 3.6, 50, 0.16, 0.25, 0.85)
        val (open, off) = lumiereStarfallSetup(base, true, 6.0)!!
        assertEquals(2.4, open.opening, 1e-12); assertEquals(0.0, off, 0.0)
        val (still, off2) = lumiereStarfallSetup(base, false, 6.0)!!
        assertEquals(3.6, still.opening, 1e-12); assertEquals(23.6, off2, 1e-12)
    }
}
