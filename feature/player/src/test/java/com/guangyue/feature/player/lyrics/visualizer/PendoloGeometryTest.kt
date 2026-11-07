package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PendoloGeometryTest {
    @Test
    fun `nine lines span the hundred degree arc`() {
        assertEquals(12.5f * PI.toFloat() / 180f, PendoloGeometry.angleStepRad, 1e-5f)
    }

    @Test
    fun `tall blocks widen the angular gap`() {
        val step = PendoloGeometry.angleStepRad
        val short = PendoloGeometry.angles(5, 3, 7, 400f) { 10f }
        assertEquals(step, short.getValue(6), 1e-5f)
        assertEquals(-2 * step, short.getValue(3), 1e-5f)
        val tall = PendoloGeometry.angles(5, 3, 7, 400f) { 200f }
        assertEquals((200f + 200f + 24f) / 800f, tall.getValue(6), 1e-5f)
    }

    @Test
    fun `focal line is brightest and largest`() {
        assertEquals(1f, PendoloGeometry.alpha(0f, 0f), 1e-5f)
        assertTrue(PendoloGeometry.alpha(0.4f, 2f) < PendoloGeometry.alpha(0.2f, 1f))
        assertEquals(0.12f, PendoloGeometry.alpha(1.5f, 6f), 1e-5f)
        assertEquals(1.25f, PendoloGeometry.scale(true, 0f), 0f)
        assertEquals(0.7f, PendoloGeometry.scale(false, 8f), 0f)
        assertEquals(0f, PendoloGeometry.edgeFade(120f), 0f)
    }
}
