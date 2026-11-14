package com.guangyue.feature.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folia resolveNextUpTrack / countdownActive 的移植规则。repeatMode：0 不循环、1 单曲、2 列表（Media3）。 */
class NowPlayingCardTest {

    @Test
    fun nextUp_middleOfQueue_isNextIndex() {
        assertEquals(3, nextUpIndex(queueSize = 5, index = 2, shuffle = false, repeatMode = 0))
    }

    @Test
    fun nextUp_lastTrack_noRepeat_isNull() {
        assertNull(nextUpIndex(5, 4, shuffle = false, repeatMode = 0))
    }

    @Test
    fun nextUp_lastTrack_repeatAll_wrapsToHead() {
        assertEquals(0, nextUpIndex(5, 4, shuffle = false, repeatMode = 2))
    }

    @Test
    fun nextUp_repeatOne_isNull() {
        assertNull(nextUpIndex(5, 1, shuffle = false, repeatMode = 1))
    }

    @Test
    fun nextUp_shuffle_isNull() {
        assertNull(nextUpIndex(5, 1, shuffle = true, repeatMode = 0))
    }

    @Test
    fun nextUp_emptyOrOutOfRange_isNull() {
        assertNull(nextUpIndex(0, 0, false, 0))
        assertNull(nextUpIndex(3, 5, false, 0))
    }

    @Test
    fun countdown_onlyInLastFiveSeconds() {
        assertFalse(nextUpCountdown(positionMs = 100_000, durationMs = 200_000))
        assertFalse(nextUpCountdown(positionMs = 194_999, durationMs = 200_000))
        assertTrue(nextUpCountdown(positionMs = 195_000, durationMs = 200_000))
        assertTrue(nextUpCountdown(positionMs = 199_999, durationMs = 200_000))
        assertFalse(nextUpCountdown(positionMs = 200_000, durationMs = 200_000))
        assertFalse(nextUpCountdown(positionMs = 0, durationMs = 0))
    }
}
