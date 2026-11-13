package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTiming
import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/** 金样：node --experimental-transform-types 直接跑 Folia dioramaSceneConstants / dioramaSceneUnits / dioramaSceneLayout.ts（three 0.185.1）。 */
class DioramaSceneTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_scene_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").filter { it.isNotEmpty() }.map { it.toDouble() }
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 1e-9 * maxOf(1.0, kotlin.math.abs(want)))

    @Test
    fun scalarsMatchFolia() {
        for (r in rows.filter { it[0] == "N" }) {
            val o = r[1].toInt()
            close("neighbor $o", r[2].toDouble(), resolveDioramaNeighborLineOpacity(o))
            close("outgoing $o", r[3].toDouble(), resolveDioramaOutgoingLineOpacity(o))
        }
        for (r in rows.filter { it[0] == "E" }) { val a = nums(r[1]); close("env ${r[1]}", r[2].toDouble(), dioramaStepEnvelope(a[0], a[1], a[2], a[3], a[4])) }
        for (r in rows.filter { it[0] == "G" }) {
            val now = r[1].toDouble()
            close("grad $now", r[2].toDouble(), resolveDioramaGradientEnergy(now, 0.3, 1.4))
            close("grad0 $now", r[3].toDouble(), resolveDioramaGradientEnergy(now, 2.0, 2.0))
        }
        for (r in rows.filter { it[0] == "T" }) close("life ${r[1]}", r[2].toDouble(), resolveDioramaTextLife(r[1].toDouble()))
        for (r in rows.filter { it[0] == "F" }) { val a = nums(r[1]); close("fit ${r[1]}", r[2].toDouble(), resolveDioramaFrameFitScale(a[0], a[1], a[2], a[3])) }
    }

    private fun mk(n: Int) = List(n) { Line(emptyList(), it * 4.0, it * 4.0 + 3.5, "line$it") }

    @Test
    fun visibleLinesAndQuaternionsMatchFolia() {
        val st = DioramaSequencerState()
        dioramaAppendSegment(st, "probe-0", mk(9), 0, DioramaVec(0.0, 0.0, 0.0))
        dioramaAppendSegment(st, "夜曲", mk(6), 1, DioramaVec(12.0, 3.0, -90.0))
        val total = dioramaTotalGlobalLines(st)
        for (r in rows.filter { it[0] == "V" }) {
            val a = r[1].split(","); val g = a[0].toInt(); val o = a[1].toInt().takeIf { it >= 0 }; val w = a[2].toDouble()
            val idx = resolveDioramaMountedIndices(g, o, total)
            assertEquals(r[1], r[2], idx.joinToString(","))
            val vis = buildDioramaVisibleLines(st, idx, g, o, w)
            val want = r[3].split(";").filter { it.isNotEmpty() }.map { nums(it) }
            assertEquals(r[1], want.size, vis.size)
            want.forEachIndexed { k, e ->
                val v = vis[k]
                val got = listOf(v.index.toDouble(), v.position.x, v.position.y, v.position.z, v.quaternion.x, v.quaternion.y, v.quaternion.z, v.quaternion.w, if (v.isOutgoing) 1.0 else 0.0)
                e.indices.forEach { close("vis ${r[1]} #$k f$it", e[it], got[it]) }
            }
        }
        val frame = dioramaResolveGlobal(st, 3)!!.frame
        for (r in rows.filter { it[0] == "Q" }) {
            val a = nums(r[1]); val q = dioramaFrameQuaternion(frame, a[0], a[1]); val e = nums(r[2])
            listOf(q.x, q.y, q.z, q.w).forEachIndexed { k, v -> close("quat ${r[1]} $k", e[k], v) }
        }
    }

    @Test
    fun unitsMatchFolia() {
        for (r in rows.filter { it[0] == "U" }) {
            val text = r[1]; val words = r[2].split(",").map { it.toInt() }
            val graphemes = text.codePoints().toArray().map { String(Character.toChars(it)) }
            val tl = graphemes.mapIndexed { i, ch -> GraphemeTiming(ch, i * 0.3, i * 0.3 + 0.25, words[i]) }
            val units = splitDioramaLyricUnits(Line(emptyList(), 0.0, 9.0, text), tl)
            val want = r[3].split(";").filter { it.isNotEmpty() }.map { it.split("^") }
            assertEquals(text, want.size, units.size)
            want.forEachIndexed { k, e ->
                val u = units[k]
                assertEquals("$text #$k", e[0], u.text)
                assertEquals("$text #$k", e[1].toInt(), u.charStart)
                assertEquals("$text #$k", e[2].toInt(), u.charEnd)
                close("$text #$k s", e[3].toDouble(), u.startTime)
                close("$text #$k e", e[4].toDouble(), u.endTime)
            }
        }
    }
}
