package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachiKowloonTest {
    @Test
    fun `白模网格是完整三角形，窗约 46% 亮，同种子同结果`() {
        val a = buildMachiKowloon("probe")
        val b = buildMachiKowloon("probe")
        assertEquals(0, a.mesh.size % 30)
        assertTrue(a.mesh.contentEquals(b.mesh))
        val total = a.litWindows + a.darkWindows
        assertTrue("windows=$total", total > 100)
        val share = a.litWindows.toDouble() / total
        assertTrue("share=$share", share in 0.36..0.56)
    }

    @Test
    fun `窗不铺在歌词屏上、不埋进别的楼`() {
        val m = buildMachiKowloon("x").mesh
        val sx = MachiKowloonLayout.SCREEN_X; val sy = MachiKowloonLayout.SCREEN_Y
        var i = 0
        while (i < m.size) {
            val emissive = m[i + 9]
            val r = m[i + 6]
            // 亮窗（自发光且不是屏面的深蓝）
            if (emissive > 0.9f && r > 0.4f) {
                val x = m[i].toDouble(); val y = m[i + 1].toDouble(); val z = m[i + 2].toDouble()
                val onScreen = kotlin.math.abs(x - sx) < MachiKowloonLayout.SCREEN_W / 2 && kotlin.math.abs(y - sy) < MachiKowloonLayout.SCREEN_H / 2 && z > -1.6
                assertTrue("window on screen at $x,$y,$z", !onScreen)
            }
            i += 10
        }
    }

    @Test
    fun `构图点包含台座四角与最高楼顶`() {
        val pts = machiKowloonFramingPoints()
        assertTrue(pts.any { it.x == -6.0 && it.z == -6.0 && it.y == -0.6 })
        assertTrue(pts.any { kotlin.math.abs(it.y - 7.6) < 1e-9 })
    }
}
