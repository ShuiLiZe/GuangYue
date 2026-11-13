package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 金样：node 截取 Folia DioramaParticleField.tsx 的 createRipplePool / spawnRipple 原文跑（diorama_field_golden.txt）。 */
class DioramaParticleFieldTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_field_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").map { it.toDouble() }

    @Test
    fun spawnMatchesFolia() {
        assertEquals(rows.first { it[0] == "U" }[1].toDouble(), DIORAMA_CORRIDOR_UNITS_PER_LINE, 1e-12)
        for (corridor in listOf(false, true)) {
            val pool = DioramaRipplePool()
            for (r in rows.filter { it[0] == "S" && it[1] == (if (corridor) "1" else "0") }) {
                val i = r[2].toInt()
                spawnDioramaRipple(pool, if (i % 3 == 2 && i > 6) 0 else i % 3, 0.3 + (i % 5) * 0.17, 1.5 + i * 0.37, corridor, 4 * DIORAMA_CORRIDOR_UNITS_PER_LINE)
                val ws = nums(r[3]); val wh = nums(r[4])
                ws.indices.forEach { assertEquals("源 $corridor/$i #$it", ws[it], pool.sources[it].toDouble(), 1e-6 * maxOf(1.0, kotlin.math.abs(ws[it]))) }
                wh.indices.forEach { assertEquals("形 $corridor/$i #$it", wh[it], pool.shapes[it].toDouble(), 1e-6 * maxOf(1.0, kotlin.math.abs(wh[it]))) }
                assertEquals(r[5], pool.cursor.joinToString(","))
            }
        }
    }

    @Test
    fun frameStepIsSane() {
        val colors = DioramaParticleColors(dioramaColorFromHex("#c8783c"), dioramaColorFromHex("#88ccee"), dioramaColorFromHex("#ff3366"))
        val layers = listOf(DioramaParticleUniforms(colors, 0f, 0f), DioramaParticleUniforms(colors, 1f, 0.6f))
        val st = DioramaParticleFieldState()
        var spawnedAt = -1
        for (i in 0 until 240) {
            val kick = if (i % 30 < 3) 230.0 else 60.0
            stepDioramaParticleField(st, DioramaParticleFrameInput(1.0 / 60, DioramaAudioBands(kick, 90.0, 90.0, 40.0), 1.0, i / 60.0, true, 3, false, 1600.0, 6.0, colors), layers)
            if (spawnedAt < 0 && st.pool.spawned > 0) spawnedAt = i
        }
        assertTrue("底鼓应产生涟漪", st.pool.spawned >= 3)
        assertEquals(layers[0].time, layers[1].time, 0f)
        assertTrue(layers[0].amplitude in 0.1f..0.45f)
        assertTrue(layers[0].formation > 0.99f)
        assertEquals(1f, layers[0].corridor, 0f)
        // 静音（滑杆 0）不产生涟漪，振幅为 0
        val quiet = DioramaParticleFieldState()
        for (i in 0 until 120) stepDioramaParticleField(quiet, DioramaParticleFrameInput(1.0 / 60, DioramaAudioBands(if (i % 30 < 3) 230.0 else 60.0, 0.0, 0.0, 0.0), 0.0, i / 60.0, false, 0, false, 1600.0, 6.0, colors), layers)
        assertEquals(0, quiet.pool.spawned)
        assertEquals(0f, layers[0].amplitude, 0f)
    }
}
