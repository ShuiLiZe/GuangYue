package com.guangyue.core.lyrics.fume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V1b-5：对照 Folia FumeBackground.ts 的 buildFumeBackgroundScene / resolveFumeBackgroundView。 */
class FumeBackgroundTest {

    private val viewport = FumeViewport(360.0, 640.0)

    @Test
    fun hashMatchesJsFnv1aIncludingCjk() {
        // JS: hashString('fume') —— FNV-1a 32 位，按 UTF-16 码元整值异或。
        assertEquals(3967538154L, FumeBackground.hashString("fume"))
        // 中文种子不能只取低 8 位：「浮」与只取低位的结果必须不同
        val low = FumeMath.hashString("浮")
        assertTrue(FumeBackground.hashString("浮") != low)
    }

    @Test
    fun jsNumberDropsTrailingZero() {
        assertEquals("432", FumeBackground.jsNumber(432.0))
        assertEquals("432.5", FumeBackground.jsNumber(432.5))
    }

    @Test
    fun portraitSceneHasSevenShapesAndNineSparksSortedByDepth() {
        val scene = FumeBackground.buildScene(viewport, 300.0, 1600.0, null, "fume:guangyue")
        assertEquals(16, scene.shapes.size)
        assertEquals(7, scene.shapes.count { it.kind != FumeBackgroundShapeKind.SPARK })
        assertEquals(9, scene.shapes.count { it.kind == FumeBackgroundShapeKind.SPARK })
        // 世界至少 1.2 倍视口
        assertEquals(432.0, scene.width, 1e-9)
        assertTrue(scene.shapes.zipWithNext().all { (a, b) -> a.depth <= b.depth })
        scene.shapes.forEach {
            assertTrue(it.x in 0.0..scene.width && it.y in 0.0..scene.height)
            if (it.kind == FumeBackgroundShapeKind.SPARK) {
                assertTrue(it.opacity in 0.08..0.22)
            } else {
                assertTrue(it.opacity in 0.01..0.16)
                // baseUnit = clamp(360*0.72, 320, 760) = 320 → 宽 262.4..435.2
                assertTrue(it.width in 262.4..435.2)
            }
        }
    }

    @Test
    fun sceneIsDeterministic() {
        val a = FumeBackground.buildScene(viewport, 800.0, 2000.0, FumePaperBounds(40.0, 60.0, 760.0, 1900.0))
        val b = FumeBackground.buildScene(viewport, 800.0, 2000.0, FumePaperBounds(40.0, 60.0, 760.0, 1900.0))
        assertEquals(a, b)
    }

    @Test
    fun emptyViewportGivesNoShapes() {
        assertTrue(FumeBackground.buildScene(FumeViewport(0.0, 0.0), 10.0, 10.0, null).shapes.isEmpty())
    }

    @Test
    fun viewTrailsCameraAndSitsLower() {
        val scene = FumeBackgroundScene(1000.0, 2000.0, emptyList())
        val v = FumeBackground.view(scene, cameraX = 600.0, cameraY = 1200.0, cameraScale = 1.0, viewport = viewport)
        assertEquals(500.0 + 100.0 * 0.9, v.cameraX, 1e-9)
        // 640*0.22 = 140.8（夹在 48..180 内）
        assertEquals(1000.0 + 200.0 * 0.74 - 140.8, v.cameraY, 1e-9)
        assertEquals(0.94, v.scale, 1e-9)
    }

    @Test
    fun opacityMultiplierIsTwiceSettingAndCapped() {
        assertEquals(1.0, FumeBackground.opacityMultiplier(0.5), 1e-9)
        val s = FumeBackground.buildScene(viewport, 300.0, 1600.0, null).shapes.first()
        assertTrue(FumeBackground.drawOpacity(s, 2.0) <= 0.42)
        assertEquals(0.0, FumeBackground.drawOpacity(s, 0.0), 1e-9)
    }
}
