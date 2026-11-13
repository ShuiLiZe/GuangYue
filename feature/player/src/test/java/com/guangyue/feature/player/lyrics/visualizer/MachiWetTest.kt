package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MachiWetTest {
    @Test
    fun `水洼参数落在 kowloonScene 的范围里且同种子同结果`() {
        val a = resolveMachiPuddles("probe")
        assertEquals(a, resolveMachiPuddles("probe"))
        assertTrue(a.offsetX in -40.0..40.0 && a.offsetZ in -40.0..40.0)
        assertTrue("amount=${a.amount}", a.amount in 0.5..0.75)
    }

    @Test
    fun `路面雨量照 createMachiRuntime 换算`() {
        assertEquals(0.0, resolveMachiGroundRain(0.0), 1e-9)
        assertEquals(0.0, resolveMachiGroundRain(0.02), 1e-9)
        assertEquals(0.7, resolveMachiGroundRain(0.5), 1e-9)
        assertEquals(0.95, resolveMachiGroundRain(1.0), 1e-9)
    }

    @Test
    fun `镜像点关于地面对称`() {
        val p = machiMirrorPoint(1.0, 3.0, -2.0, MACHI_KOWLOON_GROUND_Y)
        assertEquals(1.0, p[0], 1e-9)
        assertEquals(2 * MACHI_KOWLOON_GROUND_Y - 3.0, p[1], 1e-9)
        assertEquals(-2.0, p[2], 1e-9)
    }

    @Test
    fun `路灯光柱：尖在灯头 uv 为 1，底面落在地面半径 1点3`() {
        val specs = machiKowloonLightCones()
        assertEquals(1, specs.size)
        val mesh = machiConeMesh(specs)
        assertEquals(20 * 3 * 8, mesh.size)
        val apex = specs[0].apex; val target = specs[0].target
        for (v in 0 until mesh.size / 8) {
            val o = v * 8
            if (mesh[o + 6] == 1f) {
                assertEquals(apex[0], mesh[o].toDouble(), 1e-4); assertEquals(apex[1], mesh[o + 1].toDouble(), 1e-4)
            } else {
                val dx = mesh[o] - target[0]; val dy = mesh[o + 1] - target[1]; val dz = mesh[o + 2] - target[2]
                assertEquals(1.3, kotlin.math.sqrt(dx * dx + dy * dy + dz * dz), 1e-3)
            }
            val nl = kotlin.math.sqrt((mesh[o + 3] * mesh[o + 3] + mesh[o + 4] * mesh[o + 4] + mesh[o + 5] * mesh[o + 5]).toDouble())
            assertTrue(abs(nl - 1) < 1e-3)
            assertEquals(1f, mesh[o + 7])
        }
    }
}
