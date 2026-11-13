package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 金样：node --experimental-strip-types 直接跑 Folia diorama/cameraPath.ts（types 导入换成桩）。 */
class DioramaPathTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_path_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private val lines: List<Line> = rows.filter { it[0] == "L" }.map { r ->
        val words = r[7].split(";").map { w -> val (s, e) = w.split(","); Word("x", s.toDouble(), e.toDouble()) }
        Line(words, r[2].toDouble(), r[3].toDouble(), "", songPart = r[5], blockIndex = r[6].toInt(), isChorus = r[4] == "1")
    }
    private fun nums(s: String) = s.split(",").map { it.toDouble() }
    private fun close(msg: String, want: List<Double>, got: List<Double>, tol: Double = 1e-9) {
        assertEquals(msg, want.size, got.size)
        want.indices.forEach { assertEquals(msg, want[it], got[it], tol * maxOf(1.0, kotlin.math.abs(want[it]))) }
    }
    private fun mode(s: String) = when (s) { "calm" -> DioramaSubMode.CALM; "chaotic" -> DioramaSubMode.CHAOTIC; else -> DioramaSubMode.NORMAL }
    private fun shot(id: String) = DioramaShotKind.values().first { it.id == id }

    @Test
    fun hashPathPlacementShots() {
        assertEquals(24, lines.size)
        var checked = 0
        val framesBySeed = HashMap<String, List<DioramaFrame>>()
        for (r in rows) when (r[0]) {
            "H" -> assertEquals(r[2].toDouble(), dioramaHashSeed(r[1]), 0.0)
            "F" -> {
                val fr = framesBySeed.getOrPut(r[1]) { buildDioramaPath(lines.size, r[1]) }[r[2].toInt()]
                close(r.joinToString("|"), nums(r[3]), listOf(fr.position.x, fr.position.y, fr.position.z, fr.forward.x, fr.forward.y, fr.forward.z, fr.right.x, fr.right.y, fr.right.z, fr.up.x, fr.up.y, fr.up.z))
                checked++
            }
            "P" -> { val p = getDioramaTextPlacement(r[2].toInt(), r[1], 1.0); close(r.joinToString("|"), nums(r[3]), listOf(p.offsetR, p.offsetU, p.scale, p.roll, p.yaw, p.lookR)); checked++ }
            "S" -> { assertEquals(r.joinToString("|"), r[4], getDioramaShot(r[2].toInt(), lines, r[1], mode(r[3])).id); checked++ }
            "O" -> {
                val i = r[2].toInt(); val p = r[3].toDouble()
                val o = resolveDioramaShotOffset(getDioramaShot(i, lines, r[1]), p, p * 0.8, DIORAMA_HERO_DISTANCE, DIORAMA_CAMERA_LIFT, r[1], i, 1.2)
                close(r.joinToString("|"), nums(r[4]), listOf(o.right, o.up, o.back)); checked++
            }
            "D" -> { val d = resolveDioramaCameraDrift(r[2].toDouble(), r[1], 0.62); close(r.joinToString("|"), nums(r[3]), listOf(d.swayX, d.swayY, d.lift, d.dist)); checked++ }
        }
        assertTrue(checked > 500)
    }

    @Test
    fun formationsMatchFolia() {
        val bySeedLine = rows.filter { it[0] == "G" }.groupBy { it[1] to it[2].toInt() }
        for ((key, want) in bySeedLine) {
            val (seed, i) = key
            val frames = buildDioramaPath(lines.size, seed)
            val got = buildDioramaFormation(i, seed, getDioramaShot(i, lines, seed), frames[i], getDioramaTextPlacement(i, seed, 1.0), 1.1)
            assertEquals("$seed/$i", want.size, got.size)
            want.forEachIndexed { j, r ->
                val s = got[j]
                assertEquals(r.joinToString("|"), r[4], s.kind.id)
                close(r.joinToString("|"), nums(r[5]), listOf(s.position.x, s.position.y, s.position.z, s.scale, s.stretchY, s.spinSpeed), 1e-7)
                assertEquals(r[6] == "1", s.upright); assertEquals(r[7].toInt(), s.colorSlot); assertEquals(r[8] == "far", s.far)
            }
        }
        // 每种运镜单独出一遍：偏移与阵列。
        for (r in rows.filter { it[0] == "K" }) {
            val o = resolveDioramaShotOffset(shot(r[1]), r[2].toDouble(), 0.6, 5.2, 0.5, "probe-0", 3, 1.0)
            close(r.joinToString("|"), nums(r[3]), listOf(o.right, o.up, o.back))
        }
        val frame = buildDioramaPath(8, "probe-0")[5]; val pl = getDioramaTextPlacement(5, "probe-0", 1.0)
        for ((k, want) in rows.filter { it[0] == "Q" }.groupBy { it[1] }) {
            val got = buildDioramaFormation(5, "probe-0", shot(k), frame, pl, 1.0)
            assertEquals(k, want.size, got.size)
            want.forEachIndexed { j, r ->
                assertEquals(r.joinToString("|"), r[3], got[j].kind.id)
                close(r.joinToString("|"), nums(r[4]), listOf(got[j].position.x, got[j].position.y, got[j].position.z, got[j].scale), 1e-7)
            }
        }
    }

    @Test
    fun scalarsMatchFolia() {
        for (r in rows) when (r[0]) {
            "Y" -> assertEquals(r[2].toDouble(), resolveDioramaShapeLifeOpacity(r[1].toDouble()), 1e-12)
            "Z" -> assertEquals(r[2].toDouble(), resolveDioramaHoldSettle(r[1].toDouble()), 1e-12)
            "M" -> { val p = resolveDioramaMotionParams(1.2, 0.9, 1.1, r[1]); close(r[1], nums(r[2]), listOf(p.moveScale, p.driftScale, p.smoothTime, p.audioLevel, p.weaveScale)) }
            "A" -> { val (v, vel) = dioramaSmoothDamp(1.0, 4.0, 0.3, 0.42, 1.0 / 60); close("A", nums(r[1]), listOf(v, vel)) }
            "T" -> { assertEquals(r[1].toDouble(), resolveDioramaReadHeadTruck(0.8, 6.0, 4.0), 1e-12); assertEquals(r[2].toDouble(), resolveDioramaReadHeadTruck(0.1, 2.0, 4.0), 1e-12) }
        }
    }
}
