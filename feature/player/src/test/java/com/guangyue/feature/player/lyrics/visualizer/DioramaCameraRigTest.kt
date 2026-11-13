package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/** 金样：node 打桩 react / @react-three/fiber，直接跑 Folia CameraRig.tsx 的 useFrame（three 0.185.1 PerspectiveCamera，diorama_rig_golden.txt）。 */
class DioramaCameraRigTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_rig_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private val texts = listOf("春风十里不如你", "hello world again", "שלום עולם", "一二三四五六七八九十", "short", "最后一行歌词")

    @Test
    fun rigMatchesFolia() {
        val lines = List(12) { i -> Line(emptyList(), i * 4.0, i * 4 + 3.2, texts[i % texts.size]) }
        val st = DioramaSequencerState()
        dioramaAppendSegment(st, "rig", lines, 0, DioramaVec(0.0, 0.0, 0.0))
        val motion = resolveDioramaMotionParams()
        val cases = listOf(Triple(0, 0, 0.45), Triple(3, 0, 2.2), Triple(5, 1, 0.45), Triple(9, 0, 1.0))
        var checked = 0
        for ((gi, epoch, aspect) in cases) {
            val rig = DioramaCameraRig()
            var now = gi * 4 - 0.7
            var elapsed = 0.0
            val want = rows.filter { it[1] == "$gi" && it[2] == "$epoch" }.associateBy { it[4].toInt() }
            for (f in 0 until 150) {
                val delta = listOf(1.0 / 60, 1.0 / 30, 0.05, 1.0 / 60)[f % 4]
                now += delta; elapsed += delta
                val pose = rig.step(now, delta, elapsed, st, gi, 3.1 + gi * 0.4, motion, epoch, 55.0, aspect)!!
                val w = want[f] ?: continue
                val got = listOf(pose.position.x, pose.position.y, pose.position.z, pose.quaternion.x, pose.quaternion.y, pose.quaternion.z, pose.quaternion.w)
                w.drop(8).map { it.toDouble() }.forEachIndexed { k, v -> assertEquals("$gi/$epoch f$f #$k", v, got[k], 1e-7 * maxOf(1.0, kotlin.math.abs(v))) }
                checked++
            }
        }
        assertEquals(120, checked)
    }
}
