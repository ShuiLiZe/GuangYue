package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R7 花火④：配色与 Folia color.ts / hanabiSky.ts 对拍。金标 `hanabi_color_golden.txt`：4 套主题（深 / 浅 / 灰强调色 / 封面色）
 * × 3 种取色（role / theme / mixed）× 9 个调色角色的调色板与字色，外加夜空四色标。
 */
class HanabiColorTest {
    @Test
    fun palettesMatchFolia() {
        val g = javaClass.classLoader!!.getResourceAsStream("hanabi_color_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
        val themes = HashMap<String, HbTheme>()
        g.forEach { l ->
            when (l[0]) {
                "T" -> themes[l[1]] = HbTheme(l[2].toInt(), l[3].toInt(), l[4].toInt(), l[5].toInt())
                "P" -> {
                    val p = resolveHanabiPalette(l[3], themes.getValue(l[1]), l[2])
                    val gc = resolveHanabiGlyphColors(p, p.blocks[0], hanabiBackdropOf(p))
                    val got = listOf(p.ink, p.paper, p.blocks.joinToString(","), p.sparks.joinToString(","), p.accent, p.glow, gc.ink, gc.sung, gc.flash, gc.back).joinToString(" ")
                    assertEquals("${l[1]} ${l[2]} ${l[3]}", l.drop(4).joinToString(" "), got)
                }
                "S" -> {
                    val stops = resolveHanabiSkyStops(themes.getValue(l[1]))
                    l[2].split('|').forEachIndexed { i, s ->
                        val (at, css) = s.split(':')
                        val m = Regex("hsl\\(([-0-9.]+)deg_([-0-9.]+)%_([-0-9.]+)%\\)").find(css)!!.groupValues
                        assertEquals("sky ${l[1]} $i at", at.toDouble(), stops[i][0], 1e-9)
                        assertEquals("sky ${l[1]} $i h", m[1].toDouble() / 360, stops[i][1], 1e-9)
                        assertEquals("sky ${l[1]} $i s", m[2].toDouble() / 100, stops[i][2], 1e-9)
                        assertEquals("sky ${l[1]} $i l", m[3].toDouble() / 100, stops[i][3], 1e-9)
                    }
                }
            }
        }
    }
}
