package com.guangyue.core.wall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B1a：对照 Folia components/wall（数值由 node 跑原 JS 得到）。 */
class WallTest {
    @Test
    fun orientationMatchesFoliaJs() {
        val cases = mapOf((0 to 0) to (0 to 0L), (1 to 0) to (7 to 3356328931L), (0 to 1) to (8 to 3376160260L),
            (-1 to -1) to (6 to 2253715438L), (5 to -3) to (15 to 3766972339L), (123 to 456) to (12 to 2442482024L))
        cases.forEach { (k, v) ->
            assertEquals("mix $k", v.second, BlockTemplates.mix(k.first, k.second))
            assertEquals("orient $k", v.first, BlockTemplates.orientation(k.first, k.second))
        }
    }

    @Test
    fun everyOrientationTilesTheBlockExactly() {
        for (c in 0 until 8) for (r in 0 until 8) {
            val grid = Array(BlockTemplates.BLOCK_ROWS) { IntArray(BlockTemplates.BLOCK_COLS) }
            val t = BlockTemplates.template(c, r)
            assertEquals(12, t.size)
            t.forEach { s -> for (y in s.y until s.y + s.rows) for (x in s.x until s.x + s.cols) grid[y][x]++ }
            assertTrue(grid.all { row -> row.all { it == 1 } })
        }
    }

    @Test
    fun slotGeometryAndCollect() {
        val s = WallSlots.slot(0, 0, 1)!!
        assertEquals(3 * 136.0, s.x, 0.0)
        assertEquals(6 * 136.0 - 8, s.width, 0.0)
        assertNotNull(WallSlots.parseKey(s.key))
        assertEquals(null, WallSlots.slot(0, 0, 12))
        val view = Bounds(-100.0, 700.0, -100.0, 1400.0)
        val got = WallSlots.collect(view)
        assertTrue(got.isNotEmpty())
        assertTrue(got.all { Bounds(it.x, it.x + it.width, it.y, it.y + it.height).overlaps(view) })
        assertFalse(got.map { it.key }.toSet().size != got.size)
    }

    @Test
    fun rankPutsBigCentralSlotFirst() {
        val slots = WallSlots.blockSlots(0, 0)
        val (bw, bh) = WallSlots.blockSize(Bravais.METRICS)
        val r = RankSlots.rank(slots, bw / 2, bh / 2)
        assertEquals(12, r.size)
        assertTrue(r.first().area >= 16)
        assertEquals(r[3].key, RankSlots.rank(slots, bw / 2, bh / 2, startKey = r[3].key).first().key)
    }

    @Test
    fun reflowsKeepBlockCoveredAndGrowTheTappedSlot() {
        for (c in 0 until 4) for (r in 0 until 4) for (e in 0 until 12) {
            val rf = BlockTemplates.reflow(c, r, e)!!
            val grid = Array(8) { IntArray(12) }
            rf.forEach { s -> for (y in s.y until s.y + s.rows) for (x in s.x until s.x + s.cols) grid[y][x]++ }
            assertTrue("cover $c,$r,$e", grid.all { row -> row.all { it == 1 } })
            assertEquals(6, rf[e].cols); assertEquals(6, rf[e].rows)
        }
        assertEquals(null, BlockTemplates.reflow(0, 0, 12))
    }
}
