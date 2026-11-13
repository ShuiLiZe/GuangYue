package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/** 金样：node 直接跑 Folia dioramaParticleCorridor.ts / dioramaMoteField.ts / dioramaGeometry.ts（diorama_corridor_golden.txt）。 */
class DioramaCorridorTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_corridor_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").map { it.toDouble() }
    private fun close(msg: String, want: List<Double>, got: List<Double>, tol: Double = 1e-9) {
        assertEquals(msg + " 长度", want.size, got.size)
        want.indices.forEach { assertEquals("$msg #$it", want[it], got[it], tol * maxOf(1.0, kotlin.math.abs(want[it]))) }
    }
    private fun L(n: Int) = List(n) { Line(emptyList(), 0.0, 0.0, "") }
    private fun v(p: DioramaVec) = listOf(p.x, p.y, p.z)
    private fun spanNums(s: DioramaCorridorSpan) = v(s.start) + v(s.end) + v(s.startRight) + v(s.endRight) + v(s.startUp) + v(s.endUp) + listOf(s.pathStart.toDouble(), if (s.enabled) 1.0 else 0.0)
    private fun wantSpan(s: String) = s.split(";").flatMap { nums(it) }
    private fun kindOf(id: String) = DioramaShapeKind.values().first { it.id == id }
    private fun anchors(row: List<String>) = row[1].split("/").map { e ->
        val p = e.split(";")
        val pos = nums(p[3])
        DioramaClusterAnchor(p[0], p[1].toInt(), p[0], DioramaShapePlacement(kindOf(p[2]), DioramaVec(pos[0], pos[1], pos[2]), p[4].toDouble(), p[5].toDouble(), false, 0.0, 0, false))
    }

    @Test
    fun corridorMatchesFolia() {
        val st = DioramaSequencerState()
        dioramaAppendSegment(st, "a", L(3), 0, DioramaVec(0.0, 0.0, 0.0))
        dioramaAppendSegment(st, "b", L(4), 0, DioramaVec(5.0, 1.0, -40.0))
        for (r in rows.filter { it[0] == "S" }) {
            val w = buildDioramaCorridorWindow(st, r[1].toInt(), r[2].toInt(), r[3].toInt())
            assertEquals(r.take(4).joinToString("|"), r[4].toInt(), w.size)
            if (w.isEmpty()) continue
            r[5].split("/").forEachIndexed { k, s -> close("${r.take(4)} span $k", wantSpan(s), spanNums(w[k])) }
        }
        val fr = buildDioramaPath(5, "s")
        close("P", wantSpan(rows.first { it[0] == "P" }[1]), spanNums(buildDioramaCorridorSpan(fr[4], null, 4, false)))
        close("F", nums(rows.first { it[0] == "F" }[1]), v(extendDioramaFrame(fr[2], -3).position))
    }

    @Test
    fun moteFieldMatchesFolia() {
        for (r in rows.filter { it[0] == "M" }) {
            val x = r[1].toDouble()
            assertEquals(r.joinToString("|"), r[2].toInt(), resolveDioramaMoteCircumference(x))
            assertEquals(r.joinToString("|"), r[3].toInt(), resolveDioramaMoteRadial(x))
            assertEquals(r.joinToString("|"), r[4].toInt(), resolveDioramaMoteCount(x, x))
        }
        val k = rows.first { it[0] == "K" }
        assertEquals(k[1].toInt(), DIORAMA_MOTE_WINDOW_LINES)
        assertEquals(k[2].toInt(), DIORAMA_MOTE_MAX_POINTS)
        assertEquals(k[3], listOf(-9, -1, 0, 7, 8, 15).map { dioramaMoteSlot(it) }.joinToString(","))
        val fr = buildDioramaPath(5, "s")
        for (r in rows.filter { it[0] == "W" }) {
            val line = r[1].toInt(); val c = r[2].toInt(); val rad = r[3].toInt(); val seed = r[4].takeIf { it != "-" }
            val arr = FloatArray(DIORAMA_MOTE_WINDOW_LINES * c * rad * 3)
            writeDioramaMoteLine(arr, fr[2], line, c, rad, seed)
            close("W $line $c $rad $seed", nums(r[5]), arr.map { it.toDouble() }, 1e-6)
        }
    }

    @Test
    fun clusterSelectionMatchesFolia() {
        val shapes = anchors(rows.first { it[0] == "A" })
        val vis = listOf(
            DioramaGeometryVisibility(),
            DioramaGeometryVisibility(strands = false, rings = false),
            DioramaGeometryVisibility(mode = DioramaGeometryMode.CORRIDOR),
            DioramaGeometryVisibility(enabled = false),
        )
        for (r in rows.filter { it[0] == "C" }) {
            assertEquals("C ${r[1]}", r[2], selectVisibleDioramaClusters(shapes, vis[r[1].toInt()]).joinToString(",") { it.key })
        }
        val syn = anchors(rows.first { it[0] == "Y" })
        assertEquals(rows.first { it[0] == "Z" }[1], selectVisibleDioramaClusters(syn, vis[0]).joinToString(",") { it.key })
    }
}
