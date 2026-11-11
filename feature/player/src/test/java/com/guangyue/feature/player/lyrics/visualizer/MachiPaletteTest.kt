package com.guangyue.feature.player.lyrics.visualizer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 町景 palette.ts（含 themeColorMath.ts）与 Folia v0.7.25 对拍。金标 `machi_palette_golden.txt`（docs/port/scripts/machi-palette-golden-gen.js）：
 * 6 个手挑主题 + 40 个伪随机主题（部分带关键词色）× 种子，逐字段比（#rrggbb 全等）；另比 vivid 的几组参数。
 */
class MachiPaletteTest {
    @Test
    fun machiPaletteMatchesFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_palette_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        var n = 0
        for (o in rows) {
            o["vivid"]?.let { vv ->
                vv.jsonArray.forEach { e ->
                    val a = e.jsonArray.map { it.jsonPrimitive.content }
                    assertEquals("vivid ${a[0]}", a[1], machiVivid(a[0]))
                    assertEquals("vivid led ${a[0]}", a[2], machiVivid(a[0], 0.85, 0.58, 0.66))
                    assertEquals("vivid kw ${a[0]}", a[3], machiVivid(a[0], 0.45, 0.45, 0.7))
                }
                return@let
            } ?: run {
                val t = o["theme"]!!.jsonObject
                fun s(k: String) = t[k]!!.jsonPrimitive.content
                val theme = MachiTheme(s("backgroundColor"), s("primaryColor"), s("secondaryColor"), s("accentColor"), t["wordColors"]!!.jsonArray.map { it.jsonPrimitive.content })
                val seed = o["seed"]!!.jsonPrimitive.content
                val got = resolveMachiPalette(theme, seed).fields()
                val want = o["palette"]!!.jsonObject
                assertEquals("keys", want.keys, got.keys)
                for ((k, w) in want) {
                    val g = got[k]
                    val tag = "${theme.backgroundColor}/${theme.accentColor} $seed $k"
                    when (w) {
                        is JsonArray -> assertEquals(tag, w.map { it.jsonPrimitive.content }, g)
                        is JsonPrimitive -> if (g is Boolean) assertEquals(tag, w.boolean, g) else assertEquals(tag, w.content, g)
                        else -> error(tag)
                    }
                }
                n++
            }
        }
        assertTrue("palettes $n", n > 80)
    }
}
