package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** 金样：node --experimental-transform-types 直接跑 Folia dioramaSceneFrame.ts（three 0.185.1，三种 mesh / material 用真 three 对象）。 */
class DioramaSceneFrameTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_frame_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").filter { it.isNotEmpty() }.map { it.toDouble() }
    private fun close(msg: String, want: Double, got: Double, tol: Double = 1e-9) = assertEquals(msg, want, got, tol * maxOf(1.0, kotlin.math.abs(want)))

    private fun mk(n: Int) = List(n) { Line(emptyList(), it * 4.0, it * 4.0 + 3.5, "line$it") }
    private fun sequencer() = DioramaSequencerState().also {
        dioramaAppendSegment(it, "probe-0", mk(9), 0, DioramaVec(0.0, 0.0, 0.0))
        dioramaAppendSegment(it, "夜曲", mk(6), 1, DioramaVec(12.0, 3.0, -90.0))
    }
    private val themes by lazy {
        rows.filter { it[0] == "C" }.associate { r ->
            fun c(s: String) = nums(s).let { DioramaLinearColor(it[0], it[1], it[2]) }
            r[1] to DioramaDampedColors(c(r[2]), c(r[3]), c(r[4]), c(r[5]))
        }
    }

    @Test
    fun moteWindowMatchesFolia() {
        val st = sequencer(); val total = dioramaTotalGlobalLines(st)
        val state = HashMap<String, Pair<IntArray, FloatArray>>()
        for (r in rows.filter { it[0] == "M" }) {
            val a = r[1].split(","); val circ = a[0].toInt(); val rad = a[1].toInt(); val seed = a[2]; val g = a[3].toInt()
            val (written, pos) = state.getOrPut("$circ,$rad,$seed") { newDioramaMoteWritten() to FloatArray(DIORAMA_MOTE_MAX_POINTS * 3) }
            val dirty = recycleDioramaMoteWindow(written, pos, st, g, total, circ, rad, seed)
            assertEquals(r[1], r[2] == "1", dirty)
            assertEquals(r[1], r[3], written.joinToString(","))
            val n = DIORAMA_MOTE_WINDOW_LINES * circ * rad * 3
            val want = nums(r[4])
            if (want.size == 1) {
                var sum = 0.0
                for (k in 0 until n) sum += pos[k].toDouble()
                close("sum ${r[1]}", want[0], sum, 1e-7)
            } else {
                assertEquals(n, want.size)
                want.forEachIndexed { k, w -> close("pos ${r[1]} #$k", w, pos[k].toDouble(), 1e-6) }
            }
        }
    }

    @Test
    fun neighborLinesMatchFolia() {
        val st = sequencer(); val total = dioramaTotalGlobalLines(st)
        for (r in rows.filter { it[0] == "N" }) {
            val a = r[1].split(","); val g = a[0].toInt(); val o = a[1].toInt().takeIf { it >= 0 }
            val vis = buildDioramaVisibleLines(st, resolveDioramaMountedIndices(g, o, total), g, o, 1.0)
            val rasters = vis.filter { it.index % 5 != 4 }.associate { it.index to DioramaRasterMetrics(300 + it.index * 37, 64 + (it.index % 3) * 8) }
            val looks = updateDioramaNeighborLines(
                vis, g, o, rasters, DioramaVec(a[2].toDouble(), a[3].toDouble(), a[4].toDouble()),
                a[5].toDouble(), a[6].toDouble(), a[7].toDouble(), themes.getValue(a[8]),
            )
            val want = r[2].split(";").filter { it.isNotEmpty() }.map { nums(it) }
            assertEquals(r[1], want.size, looks.size)
            want.forEachIndexed { k, e ->
                val l = looks[k]
                val got = listOf(l.index.toDouble(), l.scale, l.opacity, l.color.r, l.color.g, l.color.b)
                e.indices.forEach { close("nb ${r[1]} #$k f$it", e[it], got[it]) }
            }
        }
    }

    @Test
    fun activeUnitsMatchFolia() {
        val units = (0 until 5).map { i -> DioramaLyricUnit("x", i, i + 1, 0.2 + i * 0.35, 0.2 + i * 0.35 + (if (i == 2) 0.05 else 0.3)) }
        for (k in rows.filter { it[0] == "K" }) {
            val ci = k[1]
            val c = k[2].split(","); val damped = themes.getValue(c[0])
            val glow = c[1].toDouble(); val soul = c[2].toDouble(); val act = c[3] == "1"; val grad = c[4].toDouble()
            val kw = if (c[5] == "1") mapOf(1 to dioramaColorFromHex("#33ccff")) else emptyMap()
            val want = rows.filter { it[0] == "A" && it[1].startsWith("$ci,") }.associateBy { it[1].split(",")[1].toInt() }
            val looks = List(units.size) { DioramaUnitLook() }
            val light = FloatArray(units.size); val soulV = FloatArray(units.size)
            for (fr in 0 until 260) {
                val delta = if (fr % 7 == 3) 1.0 / 30 else 1.0 / 60
                val now = fr / 60.0
                val s = sin(fr * 0.05)
                val life = 0.7 + 0.3 * (s * s); val breath = 0.85 + 0.15 * cos(fr * 0.11); val power = (fr % 40) / 40.0
                updateDioramaActiveUnits(units, looks, light, soulV, now, delta, life, breath, power, damped, kw, glow, soul, act, grad)
                val r = want[fr] ?: continue
                val e = r[2].split(";").map { nums(it) }
                units.indices.forEach { i ->
                    val l = looks[i]
                    val got = listOf(l.baseOpacity, l.baseColor.r, l.baseColor.g, l.baseColor.b, l.glowOpacity, if (l.glowVisible) 1.0 else 0.0,
                        l.soulOpacity, l.soulY, l.soulScale, if (l.soulVisible) 1.0 else 0.0, light[i].toDouble(), soulV[i].toDouble())
                    e[i].indices.forEach { f -> close("unit c$ci fr$fr u$i f$f", e[i][f], got[f], 1e-6) }
                }
            }
        }
    }
}
