package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 花火⑥d：环境层与 Folia hanabi/mg/ambient.ts 对拍。金标 `hanabi_ambient_golden.txt`（docs/port/scripts/hanabi-ambient-golden-gen.js，
 * 假 Pixi 跑真的 createAmbient）：3 组（密度 / 远方烟花频率 / 种子）× 6 个时刻，逐点比星点 / 薄烟 / 火星 / 散景，远方小烟花比精灵数并抽查。
 */
class HanabiAmbientTest {
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 2e-6 + 2e-7 * kotlin.math.abs(want))

    private val palette = HbPalette(0x1a1030, 0xfff6ea, listOf(0xe8344e, 0x2f6fe0, 0xf28c28), listOf(0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff), 0xff5d8f, 0xffe08a)

    private class Spr(val x: Double, val y: Double, val w: Double, val h: Double, val r: Double, val a: Double, val tint: Int)

    @Test
    fun ambientMatchesFolia() {
        val lines = javaClass.classLoader!!.getResourceAsStream("hanabi_ambient_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        var amb: HbAmbient? = null
        var lastT = Double.NaN
        val seen = HashMap<String, HashSet<Int>>()
        val pools = arrayOf(ArrayList<Spr>(), ArrayList<Spr>())
        var n = 0
        fun flush() {
            val a = amb ?: return
            if (lastT.isNaN()) return
            listOf("st" to a.stars, "sm" to a.smoke, "em" to a.embers, "bo" to a.bokeh).forEach { (tag, list) ->
                list.forEachIndexed { k, d -> assertEquals("$tag $k t=$lastT visible", seen[tag]?.contains(k) == true, d.visible) }
            }
            seen.clear()
        }
        for (ln in lines) {
            val f = ln.split(' ')
            when (f[0]) {
                "AMB" -> {
                    flush(); lastT = Double.NaN
                    amb = HbAmbient(f[4], palette, 800.0, 360.0, 20.0, 46.0, f[2].toDouble(), f[3].toDouble())
                    assertEquals(f[5].toInt(), amb!!.stars.size); assertEquals(f[6].toInt(), amb!!.smoke.size)
                    assertEquals(f[7].toInt(), amb!!.embers.size); assertEquals(f[8].toInt(), amb!!.bokeh.size)
                }
                "st", "sm", "em", "bo", "d0", "d1", "dn" -> {
                    val a = amb!!
                    val t = f[1].toDouble()
                    if (t != lastT) {
                        flush(); lastT = t
                        val rise = f[2].toDouble()
                        a.update(t, rise)
                        pools[0].clear(); pools[1].clear()
                        a.drawDistant(t, rise) { tex, x, y, w, h, r, al, tint -> pools[if (tex == HbSparkTexture.FLASH) 1 else 0] += Spr(x, y, w, h, r, al, tint); true }
                    }
                    if (f[0] == "dn") { assertEquals("distant count t=$t", f[2].toInt(), pools[0].size + pools[1].size); n++; continue }
                    val k = f[3].toInt()
                    val m = "${f[0]} $k t=$t"
                    if (f[0].startsWith("d")) {
                        val s = pools[f[0][1] - '0'][k]
                        close("$m x", f[4].toDouble(), s.x); close("$m y", f[5].toDouble(), s.y); close("$m w", f[6].toDouble(), s.w); close("$m h", f[7].toDouble(), s.h)
                        close("$m r", f[8].toDouble(), s.r); close("$m a", f[9].toDouble(), s.a); assertEquals("$m tint", f[10].toInt(), s.tint)
                        n++; continue
                    }
                    seen.getOrPut(f[0]) { HashSet() } += k
                    val d = when (f[0]) { "st" -> a.stars; "sm" -> a.smoke; "em" -> a.embers; else -> a.bokeh }[k]
                    assertTrue("$m visible", d.visible)
                    close("$m x", f[4].toDouble(), d.px); close("$m y", f[5].toDouble(), d.py); close("$m size", f[6].toDouble(), d.ps); close("$m alpha", f[7].toDouble(), d.pa)
                    assertEquals("$m tint", f[8].toInt(), d.tint)
                    n++
                }
            }
        }
        flush()
        assertTrue("n=$n", n > 1000)
    }
}
