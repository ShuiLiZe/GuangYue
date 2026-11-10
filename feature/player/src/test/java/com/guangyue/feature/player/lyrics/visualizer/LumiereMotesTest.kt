package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 绘光浮尘 / 前景散景（light/motes.ts）与 Folia 对拍。金标 `lumiere_motes_golden.txt` 由 docs/port/scripts/lumiere-motes-golden-gen.ts
 * 用 Folia 源码 + 假 pixi（只记 sprite 属性）生成：800×360，种子 probe-0:lumiere-p6:motes / :front，MOTES / FRONT 预设，无光束，intensity 0.9。
 *   MO 名 t 颗数 可见数 Σx加权 Σy Σalpha Σ直径；M0 名 t 第 0 颗 x y alpha 直径。
 */
class LumiereMotesTest {
    private val golden by lazy {
        javaClass.classLoader!!.getResourceAsStream("lumiere_motes_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }
    private fun near(e: String, a: Double, what: String) { val v = e.toDouble(); assertEquals(what, v, a, 1e-7 * maxOf(1.0, kotlin.math.abs(v))) }

    private val motesSpec = LumiereMotesSpec(260, 0.004, 0.013, 0.004, -0.01, 0.02, 9.0, 0.5, 0.015, 1.3)
    private val frontSpec = LumiereMotesSpec(9, 0.05, 0.15, 0.006, -0.002, 0.012, 14.0, 0.2, 0.02, 0.3)

    @Test
    fun motesMatchFolia() {
        for (name in listOf("motes", "front")) {
            val m = LumiereMotes(800.0, 360.0, "probe-0:lumiere-p6:$name", if (name == "motes") motesSpec else frontSpec)
            for (row in golden.filter { it[1] == name }) {
                m.update(row[2].toDouble(), emptyList(), 0.9)
                if (row[0] == "MO") {
                    var sx = 0.0; var sy = 0.0; var sa = 0.0; var sw = 0.0; var nv = 0
                    m.sprites.forEachIndexed { i, s -> if (s.visible) { nv++; sx += s.x * (i % 3 + 1); sy += s.y; sa += s.alpha; sw += s.size } }
                    assertEquals(row[3].toInt(), m.sprites.size); assertEquals(row[4].toInt(), nv)
                    near(row[5], sx, "$name Σx"); near(row[6], sy, "$name Σy"); near(row[7], sa, "$name Σa"); near(row[8], sw, "$name Σw")
                } else {
                    val s = m.sprites[0]
                    near(row[3], s.x, "x"); near(row[4], s.y, "y"); near(row[5], s.alpha, "a"); near(row[6], s.size, "w")
                }
            }
        }
    }
}
