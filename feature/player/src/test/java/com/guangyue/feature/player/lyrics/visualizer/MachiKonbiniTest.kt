package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachiKonbiniTest {
    private val palette = resolveMachiPalette(MachiTheme("#09090b", "#f4f4f5", "#71717a", "#f4f4f5"), "probe-0")

    @Test
    fun framingPointsMatchKonbiniScene() {
        // 底座 4 角 + 3 处厚度（前右角不框）、广告塔顶 3 点、立柱灯箱顶、店后角。
        val pts = machiKonbiniFramingPoints()
        assertEquals(12, pts.size)
        assertEquals(MachiKonbiniLayout.PARAPET_TOP, pts.last().y, 1e-9)
        assertEquals(4.0 + 2.3 + 0.6, pts[7].y, 1e-9)
    }

    @Test
    fun storeModelHasGlassDoorAndBoardFaces() {
        val model = buildMachiKonbini(palette, "probe-0")
        assertTrue(model.mesh.isNotEmpty() && model.mesh.size % 10 == 0)
        assertTrue(model.glass.isNotEmpty() && model.glass.size % 10 == 0)
        assertEquals(3, model.stats["glass"])
        assertEquals(2, model.stats["doorPanel"])
        assertEquals(3, model.boardFaces.size)
        assertEquals(2, model.fasciaFaces.size)
        assertEquals(4, model.stats["lot"])
        assertEquals(8, model.stats["curb"])
        assertEquals(14, model.stats["grate"])
        // 广告塔正面法线方位角 = 默认镜头方位角 45°。
        assertEquals(45 * MACHI_DEG, model.boardFaces[0][3], 1e-9)
    }

    @Test
    fun variationIsDeterministicAndInRange() {
        val a = resolveMachiKonbiniVariation("probe-0")
        assertEquals(a, resolveMachiKonbiniVariation("probe-0"))
        assertTrue(a.windSlant in 0.06..0.2 && a.puddleAmount in 0.45..0.75 && a.doorInterval in 22.0..40.0)
        val (_, w) = resolveMachiKonbiniWeather("probe-0")
        assertEquals(1, w.roofs.size)
        assertEquals(3, w.glints.size)
        assertTrue(w.drips.size > 20)
    }
}
