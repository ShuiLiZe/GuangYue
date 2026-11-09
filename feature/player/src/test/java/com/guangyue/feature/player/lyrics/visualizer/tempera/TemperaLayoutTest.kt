package com.guangyue.feature.player.lyrics.visualizer.tempera

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import com.guangyue.feature.player.lyrics.visualizer.SonnetWordSplitter
import com.guangyue.feature.player.lyrics.visualizer.compileTemperaCoreProgram
import com.guangyue.feature.player.lyrics.visualizer.sonnetHash
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * R6 凝彩③：拼贴排版 + 逐字运动与 Folia 对拍。金标 `tempera_layout_golden.txt` 由 Folia temperaLayout.ts / temperaMotion.ts
 * （量字换成同一个桩：CJK = 1 em、空格 0.28 em、其他 0.55 em）对探针歌词前 4 段的每个 shot、横竖屏各排一遍生成；
 * L 行是每字落点 / 时序 / 入场参数，M 行是若干时刻的运动帧。
 */
class TemperaLayoutTest {
    private val texts = listOf(
        "迷失在无边际这幽深的森林", "风吹过", "我们慢慢走回去，星河与风一起落在很远很远的地方", "光落在晨雾里",
        "你说夜色会把所有的名字都轻轻藏起来", "晚安", "在城市尽头等一场迟到的雨", "把回忆折成纸船放进河流",
        "轻轻唱 hello again 在无人的街角", "我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔",
    )

    private fun probeLines(): List<Line> {
        val out = ArrayList<Line>()
        var t = 0.0
        for (i in 0 until 28) {
            val text = texts[i % 10]
            val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
            val dur = 1.2 + (i % 5) * 0.6
            val step = dur / chars.size
            out += Line(words = chars.mapIndexed { o, c -> Word(c, t + o * step, t + (o + 1) * step) }, startTime = t, endTime = t + dur + 0.1,
                fullText = text, translation = "translation $i", isChorus = i in 16 until 22)
            t += dur + 0.4 + (if (i == 7 || i == 15) 4.5 else 0.0) + (if (i == 22) 2.0 else 0.0)
        }
        return out
    }

    private fun read(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }

    private val splitter: SonnetWordSplitter by lazy {
        val map = read("tempera_golden.txt").filter { it[0] == "W" }.associate { texts[it[1].toInt()] to it.drop(2).joinToString(" ").split('|') }
        val fn: SonnetWordSplitter = { line -> map[line.fullText] ?: listOf(line.fullText) }
        fn
    }

    private val stub = TpMeasure { text, px ->
        var s = 0.0
        text.codePoints().forEach { c ->
            s += when {
                (c in 0x2e80..0x9fff) || (c in 0xac00..0xd7af) || (c in 0xff00..0xffef) || (c in 0x3000..0x303f) -> px
                c == ' '.code -> px * 0.28
                else -> px * 0.55
            }
        }
        s
    }

    private fun near(e: String, a: Double, what: String) = assertEquals(what, e.toDouble(), a, 2e-4)

    @Test
    fun layoutAndMotionMatchFolia() {
        val program = compileTemperaCoreProgram(probeLines(), "probe-0", false, splitter)
        val golden = read("tempera_layout_golden.txt")
        var placements: List<TpGlyphPlacement> = emptyList()
        var checked = 0
        golden.forEach { g ->
            val w = g[1].toDouble(); val h = if (g[1] == "800") 360.0 else 780.0
            val tag = g.take(5).joinToString(" ")
            when (g[0]) {
                "S" -> {
                    val p = program.paragraphs[g[2].toInt()]
                    val shot = p.shots[g[3].toInt()]
                    assertEquals(tag, g[4], shot.kind)
                    val shotSeed = sonnetHash("probe-0:${p.id}") + g[3].toInt() * 97L
                    assertEquals(tag, g[5].toLong(), shotSeed)
                    val segs = shot.slices.map { sl ->
                        p.lines.firstOrNull { it.sourceIndex == sl.lineIndex }?.segments?.subList(sl.segmentStart, sl.segmentEnd)?.filter { isTemperaLayoutSegment(it) } ?: emptyList()
                    }.filter { it.isNotEmpty() }
                    val maxG = max(3, segs.maxOfOrNull { s -> s.sumOf { it.graphemes.size } } ?: 0)
                    val base = max(34.0, min(150.0, (w / max(5.0, maxG * 1.05)) * 1.5))
                    placements = resolveTemperaLayout(segs, shot.kind, w, h, base, shotSeed, stub)
                    assertEquals("$tag 字数", g[6].toInt(), placements.size)
                }
                "L" -> {
                    val pl = placements[g[4].toInt()]
                    assertEquals(tag, g[5], pl.char)
                    val nums = listOf(pl.x, pl.y, pl.rotation, pl.startTime, pl.settleTime, pl.endTime, pl.fontSize, pl.enterX, pl.enterY, pl.enterRotation, pl.enterScale)
                    nums.forEachIndexed { k, v -> near(g[6 + k], v, "$tag #$k") }
                    assertEquals(tag, g[17], pl.enterStyle.name.lowercase().replace('_', '-'))
                    near(g[18], pl.releaseTime, "$tag release"); near(g[19], pl.trackingX, "$tag tx"); near(g[20], pl.trackingY, "$tag ty")
                    checked++
                }
                "M" -> {
                    val pl = placements[g[4].toInt()]
                    val f = resolveTemperaGlyphMotion(pl.motion(), pl.startTime + g[5].toDouble(), 1.0)
                    assertEquals(tag, g[6] == "1", f.visible)
                    listOf(f.alpha, f.x, f.y, f.rotation, f.scaleX, f.scaleY, f.echoX, f.echoY, f.echoAlpha).forEachIndexed { k, v -> near(g[7 + k], v, "$tag m$k") }
                }
            }
        }
        assertEquals(400, checked)
    }
}
