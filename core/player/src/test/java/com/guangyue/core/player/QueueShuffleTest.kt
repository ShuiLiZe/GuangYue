package com.guangyue.core.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueShuffleTest {
    @Test
    fun `current song goes first and the rest is a permutation`() {
        val o = QueueShuffle.order(10, 4, Random(7))
        assertEquals(4, o.first())
        assertEquals((0 until 10).toList(), o.sorted())
    }

    @Test
    fun `no current shuffles everything`() {
        val o = QueueShuffle.order(6, -1, Random(3))
        assertEquals((0 until 6).toList(), o.sorted())
    }

    @Test
    fun `order actually changes for a long queue`() {
        val o = QueueShuffle.order(30, 0, Random(11))
        assertNotEquals((0 until 30).toList(), o)
    }

    @Test
    fun `needs two songs`() {
        assertFalse(QueueShuffle.canShuffle(1))
        assertTrue(QueueShuffle.canShuffle(2))
        assertEquals(listOf(0), QueueShuffle.order(1, 0))
        assertEquals(emptyList<Int>(), QueueShuffle.order(0, 0))
    }
}
