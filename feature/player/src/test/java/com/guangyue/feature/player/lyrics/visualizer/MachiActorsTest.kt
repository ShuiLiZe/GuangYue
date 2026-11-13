package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachiActorsTest {
    /** 金样：Folia schedules.ts resolveMachiCar（便利店四条车道，chance 0.6），node 跑出来的。 */
    @Test
    fun carScheduleMatchesFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_car_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        assertTrue(rows.size >= 400)
        var cars = 0
        for (row in rows) {
            val f = row.split("|")
            val pass = resolveMachiCar(f[1].toDouble(), f[0], MACHI_KONBINI_LANES, 0.6)
            if (f[2] == "-") { assertNull(row, pass); continue }
            cars++
            assertEquals(row, f[2].toInt(), pass!!.lane)
            assertEquals(row, f[3].toDouble(), pass.distance, 1e-6)
            assertEquals(row, f[4].toInt(), pass.colorIndex)
        }
        assertTrue(cars > 20)
    }

    @Test
    fun konbiniFansAndFlags() {
        val palette = resolveMachiPalette(MachiTheme("#09090b", "#f4f4f5", "#71717a", "#f4f4f5"), "probe-0")
        val model = buildMachiKonbini(palette, "probe-0")
        val fans = model.propFaces!!.fans
        assertEquals(4, fans.size)
        assertTrue(fans.all { it.mesh.isNotEmpty() && it.mesh.size % 10 == 0 })
        // 第一台是屋顶 (−0.1, roofTop, −4.75) 那台：风扇中心在 (−0.22, roofTop + 0.36, −4.585)。
        assertEquals(-0.22, fans[0].o[0], 1e-9)
        assertEquals(MachiKonbiniLayout.ROOF_TOP + 0.36, fans[0].o[1], 1e-9)
        assertEquals(-4.585, fans[0].o[2], 1e-9)
        assertEquals(3, MACHI_KONBINI_NOBORI.size)
        assertEquals(4, MACHI_KONBINI_LANES.size)
        assertEquals(2, MACHI_STATION_LANES.size)
    }

    @Test
    fun modelMatrixIsColumnMajor() {
        val m = FloatArray(16)
        machiSetModel(m, MachiFrame.rotY(Math.PI / 2), doubleArrayOf(1.0, 2.0, 3.0))
        // 绕 y 转 90°：本地 +x → 世界 −z。
        assertEquals(0f, m[0], 1e-6f); assertEquals(-1f, m[2], 1e-6f)
        assertEquals(1f, m[12]); assertEquals(2f, m[13]); assertEquals(3f, m[14]); assertEquals(1f, m[15])
    }

    /** 金样：Folia stationScene hazardOn（seed probe-0，每 0.37 s 取一次），node 跑出来的。 */
    @Test
    fun hazardBlinkMatchesFolia() {
        val golden = "0000000000000000101010100101010100000000000000000010101010101010101000000000000000000000000000000000001010101010101010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010101010101010101000000000000000000000000000000000000000000000000000000000000000000010100101010101010000000000000000000000000000000000"
        val got = (0 until golden.length).joinToString("") { if (resolveMachiHazardOn(it * 0.37, "probe-0")) "1" else "0" }
        assertEquals(golden, got)
    }

    @Test
    fun stationShadowsBulbsAndHazard() {
        val palette = resolveMachiPalette(MachiTheme("#09090b", "#f4f4f5", "#71717a", "#f4f4f5"), "probe-0")
        val model = buildMachiStation(palette, "probe-0")
        // 箭头轮廓 9 段，每 0.16 一颗：5 + 12 + 2 + 3 + 3 + 2 + 9 + 3 + 3 = 42。
        assertEquals(42, model.bulbCount)
        assertEquals(8, model.bulbs.size)
        assertTrue(model.shadows.isNotEmpty() && model.shadows.size % 10 == 0)
        val v = resolveMachiStationVariation("probe-0")
        assertEquals(v.hazardStall >= 0, model.hazardOn.isNotEmpty())
        assertEquals(model.hazardOn.size, model.hazardOff.size)
    }
}
