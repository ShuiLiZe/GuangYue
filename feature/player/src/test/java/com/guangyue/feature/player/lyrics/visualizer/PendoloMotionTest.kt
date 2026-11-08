package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PendoloMotionTest {
    private val tooth = (2 * PI / 15).toFloat()

    @Test fun secondsGearSettlesOnWholeTooth() {
        // 一秒末弹簧已收敛：37.99 s 时应停在第 37 齿附近。
        assertEquals(37 * tooth, PendoloMotion.secondGearAngle(37.99), tooth * 0.02f)
    }

    @Test fun secondsGearStartsFromPreviousTooth() {
        assertEquals(36 * tooth, PendoloMotion.secondGearAngle(37.0), 1e-3f)
        assertTrue(PendoloMotion.secondGearAngle(37.2) > 36 * tooth)
    }

    @Test fun balanceSwingAmplitudeIsSubtle() {
        assertEquals(0.15f, PendoloMotion.bassOscillation((PI / 2).toFloat()), 1e-4f)
    }
}
