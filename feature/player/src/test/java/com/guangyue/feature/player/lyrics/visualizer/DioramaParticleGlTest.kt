package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 金样：node 跑 Folia dioramaParticleMaterials.ts（three 0.185.1 的 Color，diorama_material_golden.txt）。 */
class DioramaParticleGlTest {
    private val rows = javaClass.classLoader!!.getResourceAsStream("diorama_material_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split("|") }
    private val hexes = listOf("#c8783c", "#101018", "#f4f1ea", "#7f7f7f", "#ff3366", "#2244aa", "#0a0a0a", "#ffffff", "#88ccee", "#3a2f28")
    private fun c(h: String) = dioramaColorFromHex(h)

    @Test
    fun contrastMatchesFolia() {
        val rs = rows.filter { it[0] == "R" }
        assertEquals(100, rs.size)
        for (r in rs) {
            assertEquals("${r[1]}/${r[2]}", r[3].toDouble(), dioramaParticleContrastRatio(c(r[1]), c(r[2])), 1e-9)
            assertEquals("${r[1]}/${r[2]} 显示", r[4].toDouble(), dioramaParticleDisplayedContrastRatio(c(r[1]), c(r[2])), 1e-9)
        }
    }

    @Test
    fun adaptMatchesFolia() {
        val rs = rows.filter { it[0] == "A" }
        assertEquals(100, rs.size)
        for (r in rs) {
            val i = hexes.indexOf(r[1])
            val got = resolveDioramaParticleContrastColors(DioramaParticleColors(c(hexes[i]), c(hexes[(i + 3) % 10]), c(hexes[(i + 7) % 10])), c(r[2]))
            val want = r[3].split(";").map { s -> s.split(",").map { it.toDouble() } }
            listOf(got.primary, got.accent, got.secondary).forEachIndexed { k, col ->
                listOf(col.r, col.g, col.b).forEachIndexed { j, v -> assertEquals("${r[1]}/${r[2]} #$k.$j", want[k][j], v, 1e-9) }
            }
        }
    }

    @Test
    fun shaderTextIsComplete() {
        assertTrue(DIORAMA_PARTICLE_VERTEX_SHADER.contains("#define RIPPLE_COUNT 9"))
        assertTrue(DIORAMA_PARTICLE_VERTEX_SHADER.contains("clamp((27.0 - distanceToCamera) / 7.0"))
        assertTrue(DIORAMA_PARTICLE_VERTEX_SHADER.contains("(distanceToCamera - 1.2) / 1.8"))
    }
}
