package com.guangyue.music.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 与 Folia buildArtistGridCoords（手机断点 205×270）对拍。 */
class ArtistGridCoordsTest {
    private fun near(x: Float, y: Float, p: Pair<Float, Float>) {
        assertEquals(x, p.first, 0.01f)
        assertEquals(y, p.second, 0.01f)
    }

    private val c = ArtistGridCoords.build(5, 3, 205f, 270f)

    @Test
    fun `avatar and bio sit on the middle row`() {
        near(-194.75f, 0f, c[0])
        near(133.25f, 0f, c[1])
        assertEquals(2 + 5 + 3, c.size)
    }

    @Test
    fun `songs fill the upper half nearest first`() {
        near(-102.5f, -270f, c[2])
        near(102.5f, -270f, c[3])
        near(-307.5f, -270f, c[4])
        near(307.5f, -270f, c[5])
        near(0f, -540f, c[6])
    }

    @Test
    fun `albums fill the lower half`() {
        near(-102.5f, 270f, c[7])
        near(102.5f, 270f, c[8])
        assertTrue(c.drop(7).all { it.second > 0f })
    }

    @Test
    fun `camera opens on the bio card`() {
        near(-133.25f, 0f, ArtistGridCoords.cameraAtOpen(205f))
    }
}
