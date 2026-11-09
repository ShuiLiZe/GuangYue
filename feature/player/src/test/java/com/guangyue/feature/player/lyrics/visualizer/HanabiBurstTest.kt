package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R7 花火③：烟花解析模型与 Folia mg/burstPlan.ts 对拍。金标 `hanabi_burst_golden.txt`：14 种烟花（catalog burstPreset 默认参数，
 * 含压扁 / 旋转 / scale 0 回退）各开一朵，在 0.01 / 0.2 / 0.7（精灵预算 60，测抽稀）/ 1.6 / 3.0 秒取全部精灵；加一条升空尾迹。
 * B = 输入，E = 星数 / 闪光数 / 结束时刻，D = 用掉的精灵数，X = 抽样的精灵（种类、中心、宽高、旋转、不透明度、颜色）。
 */
class HanabiBurstTest {
    private val sparks = listOf(0xff5a3c, 0x3cb4ff, 0xffd24a, 0x7cff8a, 0xffffff)

    private fun read() = javaClass.classLoader!!.getResourceAsStream("hanabi_burst_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }

    private fun record(list: MutableList<String>) = HbSparkEmit { t, x, y, w, h, r, a, tint ->
        list += listOf(t.name.lowercase(), x, y, w, h, r, a, tint).joinToString(" ") { if (it is Double) it.toString() else it.toString() }
        true
    }

    private fun sameEmit(expected: List<String>, actual: String, what: String) {
        val a = actual.split(' ')
        assertEquals("$what tex", expected[0], a[0])
        for (k in 1..6) assertEquals("$what #$k", expected[k].toDouble(), a[k].toDouble(), 2e-3 + kotlin.math.abs(expected[k].toDouble()) * 1e-6)
        assertEquals("$what tint", expected[7].toInt(), a[7].toInt())
    }

    @Test
    fun burstsMatchFolia() {
        val g = read()
        var plans = HashMap<String, HbBurstPlan>()
        val emits = HashMap<String, List<String>>()
        g.forEach { l ->
            when (l[0]) {
                "B" -> {
                    val spec = HbBurstSpec(l[1], l[2].toDouble(), l[3].toDouble(), l[4].toDouble(), l[5].split(',').map { it.toInt() }, l[6].toDouble(), l[7].toDouble())
                    val ev = HbBurstEvent(400.0, 200.0, 10.0, spec, l[8], l[9].toDouble(), l[10].toDoubleOrNull(), l[11].toDoubleOrNull())
                    plans[l[1]] = hbPlanBurst(ev, sparks, 360.0, 1.0)
                }
                "E" -> {
                    val p = plans.getValue(l[1])
                    assertEquals("${l[1]} stars", l[2].toInt(), p.stars.size)
                    assertEquals("${l[1]} flashes", l[3].toInt(), p.flashes.size)
                    assertEquals("${l[1]} end", l[4].toDouble(), p.end, 2e-4)
                }
                "D" -> {
                    val list = ArrayList<String>()
                    val used = hbDrawBursts(listOf(plans.getValue(l[1])), 10 + l[2].toDouble(), l[3].toInt(), record(list))
                    assertEquals("${l[1]} ${l[2]} used", l[4].toInt(), used)
                    emits["${l[1]} ${l[2]}"] = list
                }
                "X" -> sameEmit(l.drop(4), emits.getValue("${l[1]} ${l[2]}")[l[3].toInt()], "${l[1]} ${l[2]} #${l[3]}")
            }
        }
        val launch = HbLaunchEvent(300.0, 700.0, 340.0, 200.0, 5.0, 1.2, 0x3cb4ff, "launch-a")
        g.filter { it[0] == "LD" }.forEach { l ->
            val list = ArrayList<String>()
            hbDrawLaunch(launch, 5 + l[1].toDouble(), 360.0, record(list))
            assertEquals("launch ${l[1]} count", l[2].toInt(), list.size)
            g.filter { it[0] == "LX" && it[1] == l[1] }.forEach { x -> sameEmit(x.drop(3), list[x[2].toInt()], "launch ${l[1]} #${x[2]}") }
        }
    }
}
