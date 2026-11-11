package com.guangyue.feature.player.lyrics.visualizer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 町景 lens.ts / clock.ts / camera.ts resolveMachiDrift 与 Folia v0.7.25 对拍。金标 `machi_lens_golden.txt`
 * （docs/port/scripts/machi-lens-golden-gen.js）：8 档光圈×强度×清晰范围×移轴×缩放的景深参数；-10..400 s 的运镜偏移；
 * 300 帧伪随机时间戳（夹暂停、改速度、resync、回拨、长停顿）的时钟状态。
 */
class MachiLensTest {
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 1e-9 + 1e-9 * abs(want))
    private fun JsonElement.d() = jsonPrimitive.double

    @Test
    fun machiLensDriftClockMatchFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_lens_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        var lens = 0; var drift = 0; var clock = 0
        for (o in rows) {
            when (o["kind"]!!.jsonPrimitive.content) {
                "lens" -> {
                    val t = MachiLensTuning(o["aperture"]!!.d(), o["dof"]!!.d(), o["dofRange"]!!.d(), o["tiltShift"]!!.d())
                    val z = o["zoom"]!!.d()
                    val g = resolveMachiLens(t, z)
                    val w = o["v"]!!.jsonArray.map { it.d() }
                    val tag = "lens $t z=$z"
                    close("$tag band", w[0], g.focusBand); close("$tag falloff", w[1], g.focusFalloff)
                    close("$tag coc", w[2], g.coc); close("$tag tilt", w[3], g.tilt)
                    lens++
                }
                "drift" -> {
                    val t = o["t"]!!.d()
                    val g = resolveMachiDrift(t)
                    val w = o["v"]!!.jsonArray.map { it.d() }
                    close("drift $t az", w[0], g.azimuth); close("drift $t el", w[1], g.elevation); close("drift $t zoom", w[2], g.zoom)
                    close("drift $t px", w[3], g.panX); close("drift $t pz", w[4], g.panZ)
                    drift++
                }
                "clock" -> {
                    val c = MachiClock()
                    for (e in o["seq"]!!.jsonArray) {
                        val a = e.jsonArray
                        val op = a[1].d().toInt()
                        when (op) {
                            0 -> c.paused = !c.paused
                            1 -> c.timeScale = a[4].d()
                            2 -> c.resync()
                        }
                        c.tick(a[2].d())
                        val tag = "clock #${a[0]} op=$op"
                        assertEquals("$tag paused", a[3].jsonPrimitive.boolean, c.paused)
                        close("$tag elapsed", a[5].d(), c.elapsed); close("$tag delta", a[6].d(), c.delta); close("$tag real", a[7].d(), c.realDelta)
                        clock++
                    }
                }
            }
        }
        assertTrue("lens $lens drift $drift clock $clock", lens == 1440 && drift > 1000 && clock == 300)
    }
}
