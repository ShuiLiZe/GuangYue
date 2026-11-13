package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/** 金样：node 直接跑 Folia dioramaParticleSurfaces.ts / dioramaParticleModel.ts（diorama_particle_golden.txt）。 */
class DioramaParticleModelTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_particle_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private fun nums(s: String) = s.split(",").map { it.toDouble() }
    private fun close(msg: String, want: List<Double>, got: List<Double>, tol: Double) {
        assertEquals("$msg 长度", want.size, got.size)
        want.indices.forEach { assertEquals("$msg #$it", want[it], got[it], tol * maxOf(1.0, kotlin.math.abs(want[it]))) }
    }
    private fun sig(a: FloatArray): List<Double> {
        var s = 0.0; var w = 0.0
        a.forEachIndexed { i, f -> s += f.toDouble(); w += f.toDouble() * ((i % 97) + 1) }
        return listOf(s, w) + a.take(9).map { it.toDouble() }
    }
    private fun kindOf(id: String) = DioramaShapeKind.values().first { it.id == id }
    private fun anchor(seed: String, kind: String, x: Double, y: Double, z: Double, scale: Double, stretch: Double, slot: Int, far: Boolean) =
        DioramaClusterAnchor(seed, 0, seed, DioramaShapePlacement(kindOf(kind), DioramaVec(x, y, z), scale, stretch, false, 0.0, slot, far))
    private val clusters = listOf(
        anchor("p:0", "box", 1.0, 2.0, -3.0, 1.2, 1.7, 2, false),
        anchor("p:1", "torus", -4.0, 0.5, -20.0, 0.8, 1.0, 1, true),
        anchor("q", "cone", 0.0, -1.0, -9.0, 1.0, 1.0, 3, false),
        anchor("r:9", "sphere", 2.0, 1.0, -14.0, 1.4, 2.6, 0, true),
    )
    private fun check(tag: String, r: List<String>, g: DioramaParticleGeometryData) {
        assertEquals("$tag 点数", r[1].toInt(), g.pointCount)
        assertEquals("$tag 每单元", r[2].toInt(), g.pointsPerUnit)
        assertEquals("$tag 间距", r[3].toDouble(), g.spacing, 1e-9)
        listOf(g.positions, g.normals, g.anchors, g.scales, g.phases, g.styles, g.waves).forEachIndexed { i, a -> close("$tag 属性$i", nums(r[4 + i]), sig(a), 1e-5) }
    }

    @Test
    fun surfacesMatchFolia() {
        val us = rows.filter { it[0] == "U" }
        assertEquals(48, us.size)
        for (r in us) {
            val s = buildDioramaStructuredSurface(kindOf(r[1]), r[2].toInt(), r[3].toDouble())
            val tag = "${r[1]}/${r[2]}/${r[3]}"
            assertEquals("$tag 点数", r[4].toInt(), s.count)
            assertEquals("$tag 半径", r[5].toDouble(), s.radius, 1e-7)
            assertEquals("$tag 间距", r[6].toDouble(), s.spacing, 1e-7)
            close("$tag 位置", nums(r[7]), sig(s.positions), 1e-5)
            close("$tag 方向", nums(r[8]), sig(s.normals), 1e-5)
        }
        rows.filter { it[0] == "D" }.forEach { assertEquals(it[2].toDouble(), resolveDioramaWaveNumberMax(it[1].toDouble() / 1000), 1e-9 * maxOf(1.0, it[2].toDouble())) }
    }

    @Test
    fun geometryMatchesFolia() {
        for (d in listOf(96, 500, 2000)) check("C$d", rows.first { it[0] == "C$d" }, buildDioramaCloudGeometryData(clusters, d.toDouble()))
        val st = DioramaSequencerState()
        dioramaAppendSegment(st, "a", List(3) { Line(emptyList(), 0.0, 0.0, "") }, 0, DioramaVec(0.0, 0.0, 0.0))
        dioramaAppendSegment(st, "b", List(4) { Line(emptyList(), 0.0, 0.0, "") }, 0, DioramaVec(5.0, 1.0, -40.0))
        val w = buildDioramaCorridorWindow(st, 2, 2, 3)
        for (d in listOf(96, 700, 3000)) check("R$d", rows.first { it[0] == "R$d" }, buildDioramaCorridorGeometryData(w, d.toDouble()))
        check("Rr", rows.first { it[0] == "Rr" }, buildDioramaCorridorGeometryData(w, 400.0, 5.5))
    }

    @Test
    fun audioMatchesFolia() {
        val a = DioramaBandTracker(); val b = DioramaBandTracker()
        for (r in rows.filter { it[0] == "B" }) {
            val i = r[1].toInt()
            val lv = (if (i % 20 < 3) 0.9 else 0.25) + 0.05 * StrictMath.sin(i * 0.7)
            val lv2 = 0.4 + 0.3 * StrictMath.sin(i * 0.31)
            val sa = stepDioramaBandTracker(a, lv, 1.0 / 60); val sb = stepDioramaBandTracker(b, lv2, 1.0 / 45)
            val resp = resolveDioramaParticleAudioResponse(sa, sb)
            val got = listOf(sa.transient, sa.sustained, if (sa.onset) 1.0 else 0.0, sb.transient, sb.sustained, if (sb.onset) 1.0 else 0.0, resp.flowSpeed, resp.clusterPulse, resolveDioramaPulseTarget(resp.clusterPulse, 1.3, i % 2 == 0))
            close("B$i", r.drop(2).map { it.toDouble() }, got, 1e-9)
        }
        val el = DioramaParticleElasticState()
        val deltas = listOf(1.0 / 60, 0.2, 0.0, 1.0 / 30)
        for (r in rows.filter { it[0] == "E" }) {
            val i = r[1].toInt()
            val v = stepDioramaParticleElasticResponse(el, if (i % 15 < 2) 1.4 else if (i % 15 == 7) 0.5 else 1.0, deltas[i % 4])
            close("E$i", listOf(r[2].toDouble(), r[3].toDouble()), listOf(v, el.velocity), 1e-9)
        }
        val k = rows.first { it[0] == "K" }
        assertEquals(k[1].toInt(), DIORAMA_RIPPLE_COUNT)
        assertEquals(k[2].toDouble(), stepDioramaEnvelope(0.2, 0.9, 5.0, 2.0, 0.1), 1e-12)
        assertEquals(k[3].toDouble(), stepDioramaEnvelope(0.9, 0.2, 5.0, 2.0, 0.1), 1e-12)
    }
}
