package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** camera.ts 默认构图的几条性质（three 没进 JVM，不做逐像素金标）。 */
class MachiCameraTest {
    private val points = machiKowloonFramingPoints()

    @Test
    fun `缩放 1 时构图点全在画面里，并给字幕让出下沿`() {
        for ((w, h) in listOf(1600.0 to 720.0, 720.0 to 1600.0, 1000.0 to 1000.0)) {
            val rig = MachiCameraRig(points, MachiKowloonLayout.VIEW.copy(zoom = 1.0))
            rig.setAspect(w, h)
            val pose = rig.pose(0.0, 0.0)
            var minY = 1.0
            for (p in points) {
                val n = pose.project(p)
                assertTrue("x=${n[0]} @${w}x$h", abs(n[0]) <= 1.0 + 1e-6)
                assertTrue("y=${n[1]} @${w}x$h", abs(n[1]) <= 1.0 + 1e-6)
                minY = minOf(minY, n[1])
            }
            // 下沿至少空出 SUBTITLE_RESERVE（5%）的一半以上
            assertTrue("minY=$minY", minY > -1.0 + 0.04)
        }
    }

    @Test
    fun `镜头在目标点外 60 m、朝向按方位角与俯角`() {
        val rig = MachiCameraRig(points, MachiKowloonLayout.VIEW)
        val pose = rig.pose(0.0, 0.0)
        val d = pose.position - pose.target
        assertEquals(60.0, kotlin.math.sqrt(d.dot(d)), 1e-9)
        val dir = machiOrbitDirection(30 * MACHI_DEG, 30 * MACHI_DEG)
        assertEquals(dir.x * 60, d.x, 1e-9); assertEquals(dir.y * 60, d.y, 1e-9); assertEquals(dir.z * 60, d.z, 1e-9)
        assertEquals(1.55, pose.zoom, 1e-12)
        // 目标点投到画面中心偏上（视锥往下挪了字幕位）
        val c = pose.project(pose.target)
        assertEquals(0.0, c[0], 1e-6)
        assertTrue(c[1] > 0)
    }

    @Test
    fun `运镜幅度夹在拖拽范围里`() {
        val rig = MachiCameraRig(points, MachiKowloonLayout.VIEW)
        for (k in 0..400) {
            val pose = rig.pose(k * 1.7, 1.0)
            assertTrue(pose.zoom in MachiCameraLimits.MIN_ZOOM..MachiCameraLimits.MAX_ZOOM)
            val d = (pose.position - pose.target).normalized()
            val elevation = kotlin.math.asin(d.y)
            assertTrue(elevation >= MachiCameraLimits.MIN_ELEVATION - 1e-9 && elevation <= MachiCameraLimits.MAX_ELEVATION + 1e-9)
        }
    }
}
