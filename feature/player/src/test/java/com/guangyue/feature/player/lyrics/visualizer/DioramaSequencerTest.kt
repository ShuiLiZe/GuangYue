package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/** 金样：node 直接跑 Folia dioramaTransition.ts / dioramaSequencer.ts。 */
class DioramaSequencerTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_seq_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").map { it.toDouble() }
    private fun close(msg: String, want: List<Double>, got: List<Double>) = want.indices.forEach { assertEquals(msg, want[it], got[it], 1e-9 * maxOf(1.0, kotlin.math.abs(want[it]))) }
    private fun L(n: Int) = List(n) { Line(emptyList(), 0.0, 0.0, "") }

    @Test
    fun transitionMatchesFolia() {
        for (r in rows.filter { it[0] == "X" }) {
            val seed = r[1]; val e = r[2].toInt()
            val o = dioramaPickTransitionOffset(seed, e)
            val from = DioramaVec(1.0, 2.0, -3.0); val to = from + o
            val c = dioramaBezierControl(from, to, seed, e)
            val p = dioramaBezierArc(from, c, to, dioramaTransitionEase(0.37))
            val pp = dioramaFlightPerp(from, to)
            close(r.joinToString("|"), nums(r[3]), listOf(o.x, o.y, o.z, c.x, c.y, c.z, p.x, p.y, p.z, pp.x, pp.z))
            assertEquals(r[4].toInt(), dioramaFlightSide(seed, e))
        }
    }

    @Test
    fun sequencerMatchesFolia() {
        val st = DioramaSequencerState()
        dioramaAppendSegment(st, "a", L(3), 0, DioramaVec(0.0, 0.0, 0.0))
        dioramaAppendSegment(st, "b", L(0), 0, DioramaVec(5.0, 1.0, -40.0))
        dioramaUpdateActiveSegmentLines(st, L(5))
        dioramaAppendSegment(st, "b", L(2), 1, DioramaVec(-9.0, 3.0, -80.0))
        val n = rows.first { it[0] == "N" }
        assertEquals(n[1].toInt(), dioramaTotalGlobalLines(st))
        assertEquals(n[2], st.segments.joinToString(",") { "${it.key}:${it.globalStart}:${it.span}:${it.linesEpoch}" })
        for (r in rows.filter { it[0] == "R" }) {
            val got = dioramaResolveGlobal(st, r[1].toInt())
            if (r[2] == "-") { assertEquals(null, got); continue }
            assertEquals(r[2], got!!.segment.key); assertEquals(r[3].toInt(), got.localIndex)
            close(r.joinToString("|"), nums(r[4]), listOf(got.frame.position.x, got.frame.position.y, got.frame.position.z))
        }
        dioramaPruneSegments(st, 4)
        assertEquals(rows.first { it[0] == "U" }[1], st.segments.joinToString(",") { it.key })
    }
}
