package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachiStationTest {
    private val palette = resolveMachiPalette(MachiTheme("#09090b", "#f4f4f5", "#71717a", "#f4f4f5"), "probe-0")

    @Test
    fun framingPointsMatchStructures() {
        // 底座 4 角 + 3 处厚度、罩棚顶 3 点、旅馆立牌顶、货车顶两角。
        val pts = machiStationFramingPoints()
        assertEquals(13, pts.size)
        assertEquals(6.4 + 0.2, pts[10].y, 1e-9)
    }

    @Test
    fun variationIsDeterministicWithAtLeastOneTaxi() {
        val a = resolveMachiStationVariation("probe-0")
        assertEquals(a, resolveMachiStationVariation("probe-0"))
        assertEquals(3, a.stalls.size)
        assertTrue(a.stalls.contains("taxi"))
        assertTrue(a.hazardStall == -1 || a.stalls[a.hazardStall] == "sedan")
        assertTrue(a.windSlant in 0.06..0.2 && a.puddleAmount in 0.45..0.75)
    }

    @Test
    fun stationModelHasCanopyPumpsTruckAndSignFaces() {
        val v = resolveMachiStationVariation("probe-0")
        val model = buildMachiStation(palette, "probe-0")
        assertTrue(model.mesh.isNotEmpty() && model.mesh.size % 10 == 0)
        assertEquals(4, model.stats["column"])
        assertEquals(6, model.stats["canopyLight"])
        assertEquals(4, model.stats["pump"])
        assertEquals(1, model.stats["truck"])
        assertEquals(v.stalls.count { it == "taxi" }, model.andons.size)
        assertEquals(2, model.canopySigns.size)
        assertEquals(2, model.pylonFaces.size)
        assertEquals(3, model.motelFaces.size)
        val (_, w) = resolveMachiStationWeather("probe-0")
        assertEquals(3, w.roofs.size)
        assertTrue(w.drips.size > 15)
    }

    @Test
    fun lightsPoolsAndClockMatchStation() {
        val lights = machiStationLights(palette)
        assertEquals(5, lights.size)
        assertEquals(3, lights.count { it.target != null })
        assertEquals(3 * 16 * 16 * 6 * 10, buildMachiStationPools(palette).size)
        assertEquals(" 1:05", formatMachiClock(65.9))
        assertEquals("99:00", formatMachiClock(99 * 60.0 + 3600))
    }
}
