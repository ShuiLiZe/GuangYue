package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folia LatentBackground.tsx updateAudioResponse / resolveLatent* 对拍。 */
class LatentAudioTest {
    private val zero = LatentAudio.State(0f, 0f, 0f, 0f, 0f, 0f)

    @Test fun broadbandWeights() {
        assertEquals(0f, LatentAudio.broadband(0.0, 0.0, 0.0, 0.0, 0.0), 1e-6f)
        assertEquals(1f, LatentAudio.broadband(255.0, 255.0, 255.0, 255.0, 255.0), 1e-5f)
        // 只有人声满格：0.28^0.55
        assertEquals(Math.pow(0.28, 0.55).toFloat(), LatentAudio.broadband(0.0, 0.0, 0.0, 255.0, 0.0), 1e-5f)
    }

    @Test fun onsetRisesFastDecaysSlow() {
        val up = LatentAudio.step(zero, 200.0, 200.0, 200.0, 200.0, 200.0, 200.0, paused = false, enhanced = true)
        assertTrue(up.onset > 0.9f)
        assertTrue("快升 ${up.beatSpeed}", up.beatSpeed > 0.4f)
        var s = up
        repeat(5) { s = LatentAudio.step(s, 200.0, 200.0, 200.0, 200.0, 200.0, 200.0, paused = false, enhanced = true) }
        assertEquals(up.onset * Math.pow(0.84, 5.0).toFloat(), s.onset, 1e-4f)
    }

    @Test fun pausedGoesToZeroTargets() {
        val s = LatentAudio.step(LatentAudio.State(1f, 1f, 1f, 1f, 0.5f, 0.5f), 255.0, 255.0, 255.0, 255.0, 255.0, 255.0, paused = true, enhanced = true)
        assertEquals(0.88f, s.power, 1e-5f)
        assertEquals(0.84f, s.bass, 1e-5f)
        assertEquals(0.87f, s.mid, 1e-5f)
    }

    @Test fun speedEasesBetweenPausedAndPlaying() {
        assertEquals(0.3f * 0.12f, LatentAudio.speed(0.3f, 2f, 1f, 0f), 1e-6f)
        assertEquals(2f, LatentAudio.speed(0.3f, 2f, 1f, 1f), 1e-6f)
        assertEquals(0.3f, LatentAudio.speed(0.3f, 2f, 0f, 1f), 1e-6f)
    }
}
