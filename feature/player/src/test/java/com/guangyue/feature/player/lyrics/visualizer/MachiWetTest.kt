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

class MachiWeatherTest {
    @Test
    fun `天气布置：水洼与 resolveMachiPuddles 同序列，屋顶 4 块，大排档与门头滴水都在`() {
        val model = buildMachiKowloon("probe")
        val (puddles, layout) = resolveMachiKowloonWeather("probe", model.drips)
        assertEquals(resolveMachiPuddles("probe"), puddles)
        assertEquals(4, layout.roofs.size)
        assertEquals(-3.6, layout.roofs[0].x1, 1e-9)
        assertEquals(6.6, layout.roofs[0].y, 1e-9)
        assertEquals(4.0, layout.roofs[1].y, 1e-9)
        // 大排档 4点1 到 5点95 每 0点45 一滴（5 滴）、门头 3点9 宽每 0点6 一滴（7 滴）
        assertEquals(model.drips.size + 5 + 7, layout.drips.size)
        assertTrue(model.drips.isNotEmpty())
        assertTrue(layout.windYaw in -0.6..0.6 && layout.windSlant in 0.06..0.18)
        layout.drips.forEach { assertTrue(it.from > it.to && it.period > 0) }
    }

    @Test
    fun `雨丝种子两端成对、落在底座加 0点6 里`() {
        val s = machiRainSeeds(50, 6.0)
        assertEquals(50 * 2 * 5, s.size)
        for (i in 0 until 50) {
            val a = i * 10; val b = a + 5
            for (k in 0..3) assertEquals(s[a + k], s[b + k])
            assertEquals(0f, s[a + 4]); assertEquals(1f, s[b + 4])
            assertTrue(kotlin.math.abs(s[a]) <= 6.6f && kotlin.math.abs(s[a + 1]) <= 6.6f)
        }
        assertEquals(0.62, resolveMachiRainAmount(1.0), 1e-9)
    }
}
