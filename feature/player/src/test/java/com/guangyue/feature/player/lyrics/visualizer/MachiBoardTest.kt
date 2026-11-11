package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.LyricSyllable
import com.guangyue.core.lyrics.Word
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 町景 director.ts + boardLayout.ts 与 Folia v0.7.25 对拍。金标 `machi_board_golden.txt`（docs/port/scripts/machi-board-golden-gen.js）：
 * 9 行歌词（逐字均分 / 分词 / 音节 / 间奏 / 空行 / 拉丁 / 副歌标记），约 140 个时刻比牌面、副歌、跑马灯；闪电、招牌闪烁（3 种子 × 3 速率）、
 * 音频包络；3 种字面 × 9 行的排版（断行、字号、每字 x 区间与时间）与逐时刻点亮位置；曲名卡。
 */
class MachiBoardTest {
    private fun close(msg: String, want: Double, got: Double) {
        if (want.isInfinite() || got.isInfinite()) { assertEquals(msg, want, got, 0.0); return }
        assertEquals(msg, want, got, 1e-6 + 1e-8 * abs(want))
    }
    private fun JsonElement.d() = jsonPrimitive.doubleOrNull ?: jsonPrimitive.content.toDouble()
    private fun JsonObject.d(k: String) = this[k]!!.d()
    private fun JsonObject.s(k: String) = this[k]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    /** 与 gen.js 的假测量一致：参考字号 100 px。 */
    private fun cw(cp: Int): Double = when {
        cp in 0x3000..0x9fff || cp in 0xac00..0xd7af || cp in 0xff00..0xffef -> 100.0
        cp == ' '.code -> 28.0
        (cp in 'A'.code..'Z'.code) || (cp in 'a'.code..'z'.code) || (cp in '0'.code..'9'.code) -> 56.0
        else -> 40.0
    }
    private val measure: MachiMeasure = { text -> var s = 0.0; text.codePoints().forEach { s += cw(it) }; s }

    private fun parseLine(o: JsonObject) = Line(
        words = o["words"]!!.jsonArray.map { w ->
            val wo = w.jsonObject
            Word(wo.s("text")!!, wo.d("startTime"), wo.d("endTime"), wo["syllables"]!!.jsonArray.map { y ->
                val yo = y.jsonObject; LyricSyllable(yo.s("text")!!, yo.d("startTime"), yo.d("endTime"))
            })
        },
        startTime = o.d("startTime"), endTime = o.d("endTime"), fullText = o.s("fullText")!!,
        translation = o.s("translation"), songPart = o.s("songPart"), isChorus = o["isChorus"]!!.jsonPrimitive.booleanOrNull == true,
    )

    private fun checkRows(tag: String, want: JsonObject, got: MachiBoardLayout) {
        close("$tag fontPx", want.d("fontPx"), got.fontPx)
        val rows = want["rows"]!!.jsonArray
        assertEquals("$tag rows", rows.size, got.rows.size)
        rows.forEachIndexed { r, re ->
            val ro = re.jsonObject; val gr = got.rows[r]
            close("$tag r$r fontPx", ro.d("fontPx"), gr.fontPx)
            close("$tag r$r centerY", ro.d("centerY"), gr.centerY)
            close("$tag r$r top", ro.d("top"), gr.top)
            close("$tag r$r bottom", ro.d("bottom"), gr.bottom)
            val gs = ro["glyphs"]!!.jsonArray
            assertEquals("$tag r$r glyphs", gs.size, gr.glyphs.size)
            gs.forEachIndexed { k, ge ->
                val a = ge.jsonArray; val g = gr.glyphs[k]
                assertEquals("$tag r$r g$k text", a[0].jsonPrimitive.content, g.text)
                assertEquals("$tag r$r g$k offset", a[1].jsonPrimitive.int, g.offset)
                assertEquals("$tag r$r g$k length", a[2].jsonPrimitive.int, g.length)
                close("$tag r$r g$k x0", a[3].d(), g.x0)
                close("$tag r$r g$k x1", a[4].d(), g.x1)
                close("$tag r$r g$k start", a[5].d(), g.startTime)
                close("$tag r$r g$k end", a[6].d(), g.endTime)
            }
        }
    }

    @Test
    fun machiDirectorAndBoardMatchFolia() {
        val rows = javaClass.classLoader!!.getResourceAsStream("machi_board_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        val lines = rows.first { it.s("kind") == "lines" }["lines"]!!.jsonArray.map { parseLine(it.jsonObject) }
        val boxes = listOf(MachiBoardBox(1024.0, 256.0, 0.06, 100.0), MachiBoardBox(512.0, 512.0, 0.1, 100.0), MachiBoardBox(900.0, 120.0, 0.04, 64.0))
        var lightningTimes: List<Double> = emptyList()
        var checked = 0
        for (o in rows) {
            when (o.s("kind")) {
                "target" -> {
                    val t = o.d("t")
                    val target = resolveMachiBoardTarget(lines, t)
                    assertEquals("target @$t", o["target"]!!.jsonPrimitive.int, target.index)
                    close("chorus @$t", o.d("chorus"), resolveMachiChorus(lines, target))
                    assertEquals("ticker @$t", o.s("ticker"), resolveMachiTickerText(target, lines, "晴天", "周杰伦", "Untitled"))
                }
                "lightningTimes" -> {
                    lightningTimes = buildMachiLightningTimes(lines)
                    val want = o["times"]!!.jsonArray.map { it.d() }
                    assertEquals("lightning times", want.size, lightningTimes.size)
                    want.forEachIndexed { i, v -> close("lightning time $i", v, lightningTimes[i]) }
                }
                "lightning" -> close("lightning @${o.d("t")}", o.d("v"), resolveMachiLightning(lightningTimes, o.d("t")))
                "flicker" -> {
                    val seed = o.s("seed")!!; val rate = o.d("rate"); val step = o.d("step")
                    var t = 0.0
                    o["vs"]!!.jsonArray.forEachIndexed { i, v ->
                        close("flicker $seed×$rate #$i", v.d(), resolveMachiFlicker(t, seed, rate)); t += step
                    }
                }
                "audio" -> {
                    val f = MachiAudioFollower()
                    val raws = o["raws"]!!.jsonArray.map { it.d() }
                    val vs = o["vs"]!!.jsonArray.map { it.d() }
                    var n = 0
                    raws.forEach { raw -> for (k in 0 until 5) { close("audio #$n", vs[n], f.step(raw, if (k == 4) 0.0 else 0.016 + 0.01 * k)); n++ } }
                }
                "layout" -> {
                    val bi = o["box"]!!.jsonPrimitive.int; val li = o["line"]!!.jsonPrimitive.int
                    val got = layoutMachiBoardLine(lines[li], boxes[bi], measure)
                    checkRows("layout b$bi l$li", o, got)
                    o["reveal"]!!.jsonArray.forEach { pe ->
                        val p = pe.jsonArray; val t = p[0].d()
                        val want = p[1].jsonArray.map { it.d() }
                        val lit = resolveMachiBoardReveal(got, t)
                        assertEquals("reveal rows b$bi l$li @$t", want.size, lit.size)
                        want.forEachIndexed { i, v -> close("reveal b$bi l$li r$i @$t", v, lit[i]) }
                    }
                }
                "card" -> {
                    val bi = o["box"]!!.jsonPrimitive.int
                    checkRows("card b$bi ${o.s("title")}", o, layoutMachiBoardCard(o.s("title")!!, o.s("subtitle")!!, boxes[bi], measure))
                }
                else -> continue
            }
            checked++
        }
        assertTrue("golden rows checked: $checked", checked > 600)
    }
}
