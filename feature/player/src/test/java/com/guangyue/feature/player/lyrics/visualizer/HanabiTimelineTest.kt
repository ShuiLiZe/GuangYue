package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R7 花火②：镜头时间线与 Folia timeline.ts 对拍。金标 `hanabi_timeline_golden.txt` 由 Folia 源码在 node 里对 6 组随机镜头
 * （含间奏镜头、无冲击镜头、横竖屏高度、有无 exitStart、spin 关掉）跑 planSpin + buildTimeline，
 * I 行是输入，A 行是旋转角 / 慢转角速度，T 行是镜头时序，F 行是 61 个时刻的 rise / spin / camera（输入在金标里取到 1e-6，容差放到 3e-3）。
 */
class HanabiTimelineTest {
    private fun read() = javaClass.classLoader!!.getResourceAsStream("hanabi_timeline_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }

    private fun near(e: String, a: Double, w: String) = assertEquals(w, e.toDouble(), a, 3e-3 + kotlin.math.abs(e.toDouble()) * 1e-5)

    @Test
    fun timelineMatchesFolia() {
        val all = read()
        for (c in 0 until 6) {
            val cfg = all.first { it[0] == "C" && it[1] == "$c" }
            val inputs = all.filter { it[0] == "I" && it[1] == "$c" }.map { g ->
                HbTimelineShot(g[3].toDouble(), g[4].toDouble(), g[5].toDoubleOrNull(), g[6].toDoubleOrNull(), g[7] == "1",
                    g[8].toDouble(), g[9].toDouble(), g[10].toDouble(), g[11].toDouble())
            }
            val rng = SonnetRandom(sonnetHash("seed$c:spin"))
            val (angles, drift) = hbPlanSpin(inputs.map { it.endTime - it.startTime }, cfg[4].toDouble(), cfg[3].toInt()) { rng.next() }
            all.filter { it[0] == "A" && it[1] == "$c" }.forEach { g ->
                near(g[3], angles[g[2].toInt()], "A$c ${g[2]} angle"); near(g[4], drift[g[2].toInt()], "A$c ${g[2]} drift")
            }
            val tl = hbBuildTimeline(inputs, cfg[7].toDouble(), cfg[5].toDouble(), 1.0, 1.0, cfg[6].toDoubleOrNull(), angles, drift)
            all.filter { it[0] == "T" && it[1] == "$c" }.forEach { g ->
                val s = tl.shots[g[2].toInt()]
                listOf(s.worldY, s.launchAt, s.enterAt, s.exitAt, s.exitDuration, s.punchAt, s.drift).forEachIndexed { k, v -> near(g[3 + k], v, "T$c ${g[2]} #$k") }
            }
            all.filter { it[0] == "F" && it[1] == "$c" }.forEach { g ->
                val time = g[2].toDouble()
                assertEquals("F$c $time shotAt", g[3].toInt(), tl.shotAt(time))
                val cam = tl.camera(time)
                listOf(tl.rise(time), tl.spin(time), cam.scale, cam.rotation, cam.dx, cam.dy).forEachIndexed { k, v -> near(g[4 + k], v, "F$c $time #$k") }
            }
        }
    }
}
