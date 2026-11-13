package com.guangyue.feature.player.lyrics.visualizer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 町景 createMachiRuntime.applyEnvironmentColors 与 Folia v0.7.25（three r185 Color，线性空间）对拍。
 * 金标 `machi_envpal_golden.txt`（docs/port/scripts/machi-envpal-golden-gen.cjs）：首行是三套底色，其后
 * daylight × overcast × snowCover 30 组，每组 5 个键的线性 RGB。
 */
class MachiEnvPaletteTest {
    @Test
    fun machiEnvironmentColorsMatchFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_envpal_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        val base = rows.first()
        fun b(k: String) = base[k]!!.jsonPrimitive.content
        var n = 0
        for (o in rows.drop(1)) {
            val daylight = o["daylight"]!!.jsonPrimitive.double
            val overcast = o["overcast"]!!.jsonPrimitive.double
            val snow = o["snowCover"]!!.jsonPrimitive.double
            for ((key, want) in o["res"]!!.jsonObject) {
                val got = machiEnvironmentColor(key, b(key), b(key + "Dusk"), b(key + "Day"), daylight, overcast, snow)
                want.jsonArray.forEachIndexed { i, w -> assertEquals("$key d=$daylight o=$overcast s=$snow [$i]", w.jsonPrimitive.double, got[i], 2e-6) }
                n++
            }
        }
        assertTrue(n == 150)
    }
}
