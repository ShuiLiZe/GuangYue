package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

class MonetPortraitCrossfadeTest {
    private val C = MonetPortraitCrossfade

    @Test fun repeatOfTopIsDropped() {
        val a = C.push(emptyList(), "a", 1)
        assertEquals(a, C.push(a, "a", 2))
    }

    @Test fun stackIsCappedAtFour() {
        var l = emptyList<MonetPortraitLayer>()
        (1..6).forEach { l = C.push(l, "s$it", it) }
        assertEquals(listOf("s3", "s4", "s5", "s6"), l.map { it.src })
    }

    @Test fun settleDropsEverythingBelowKey() {
        var l = emptyList<MonetPortraitLayer>()
        (1..3).forEach { l = C.push(l, "s$it", it) }
        assertEquals(listOf(3), C.settle(l, 3).map { it.key })
        assertEquals(l, C.settle(l, 99))
        assertEquals(l, C.settle(l, 1))
    }
}
