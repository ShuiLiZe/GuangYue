package com.guangyue.feature.player.lyrics.visualizer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 町景 environment.ts 与 Folia v0.7.25 对拍。金标 `machi_env_golden.txt`（docs/port/scripts/machi-env-golden-gen.js）：
 * 白天程度（-3..27 时，步长 1/16）、自动天气（14 个种子）、4×4 天气切换 × 3 种昼夜变化各 90 步阻尼追赶的状态与灯光标量。
 */
class MachiEnvironmentTest {
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 1e-9 + 1e-9 * abs(want))
    private fun JsonObject.d(k: String) = this[k]!!.jsonPrimitive.let { it.doubleOrNull ?: it.content.toDouble() }
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content
    private fun kind(id: String) = MachiWeatherKind.entries.first { it.id == id }

    @Test
    fun machiEnvironmentMatchesFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_env_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        var n = 0
        for (o in rows) {
            when (o.s("kind")) {
                "daylightAt" -> close("daylight @${o.d("h")}", o.d("v"), resolveMachiDaylightAt(o.d("h")))
                "weather" -> assertEquals("weather ${o.s("seed")}", o.s("v"), resolveMachiWeatherKind("auto", o.s("seed")).id)
                "env" -> {
                    val tag = "${o.s("from")}→${o.s("to")} ${o.d("d0")}→${o.d("d1")}"
                    var st = createMachiEnvironmentState(resolveMachiEnvironmentTarget(o.d("d0"), kind(o.s("from"))))
                    val tgt = resolveMachiEnvironmentTarget(o.d("d1"), kind(o.s("to")))
                    val seq = o["seq"]!!.jsonArray
                    var k = 0
                    for (i in 0 until 90) {
                        st = stepMachiEnvironment(st, tgt, if (i % 7 == 0) 0.0 else 0.05 + (i % 5) * 0.11)
                        if (i % 3 != 0) continue
                        val pair = seq[k++].jsonArray
                        val ws = pair[0].jsonObject; val wl = pair[1].jsonObject
                        close("$tag #$i daylight", ws.d("daylight"), st.daylight)
                        close("$tag #$i rain", ws.d("rain"), st.rain)
                        close("$tag #$i snow", ws.d("snow"), st.snow)
                        close("$tag #$i fog", ws.d("fog"), st.fog)
                        close("$tag #$i wetness", ws.d("wetness"), st.wetness)
                        close("$tag #$i snowCover", ws.d("snowCover"), st.snowCover)
                        val l = resolveMachiEnvironmentLook(st)
                        close("$tag #$i warmth", wl.d("warmth"), l.warmth)
                        close("$tag #$i overcast", wl.d("overcast"), l.overcast)
                        close("$tag #$i artificial", wl.d("artificial"), l.artificial)
                        close("$tag #$i streetLamps", wl.d("streetLamps"), l.streetLamps)
                        close("$tag #$i sun", wl.d("sunIntensity"), l.sunIntensity)
                        close("$tag #$i hemi", wl.d("hemiIntensity"), l.hemiIntensity)
                        close("$tag #$i bloom", wl.d("bloom"), l.bloom)
                        close("$tag #$i streaks", wl.d("streaks"), l.streaks)
                        close("$tag #$i clouds", wl.d("clouds"), l.clouds)
                        close("$tag #$i stars", wl.d("stars"), l.stars)
                        close("$tag #$i mist", wl.d("mist"), l.mist)
                        close("$tag #$i haze", wl.d("haze"), l.haze)
                    }
                    assertEquals("$tag seq", seq.size, k)
                }
            }
            n++
        }
        assertTrue("rows $n", n > 500)
    }
}
