package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R6 凝彩第一块：编排与 Folia 原版逐项对拍。金标 `tempera_golden.txt` 由 Folia 源码
 * （temperaProgram.ts + temperaShotProfiles.ts，esbuild 打包后在 node 里跑）对下面这组 28 句探针歌词生成，seed `probe-0`，
 * 切片模式（G/P/S 行）和整行模式（HG/HP/HS 行）各一份；分词用 Intl.Segmenter 的结果（W 行）。
 * 探针带两处 4.5 s 间隙（time-gap + bridge shot）和一段副歌（第 16–21 句）。
 */
class TemperaCoreProgramTest {
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
            out += Line(
                words = chars.mapIndexed { o, c -> Word(c, t + o * step, t + (o + 1) * step) },
                startTime = t,
                endTime = t + dur + 0.1,
                fullText = text,
                translation = "translation $i",
                isChorus = i in 16 until 22,
            )
            t += dur + 0.4 + (if (i == 7 || i == 15) 4.5 else 0.0) + (if (i == 22) 2.0 else 0.0)
        }
        return out
    }

    private val golden: List<List<String>> by lazy {
        javaClass.classLoader!!.getResourceAsStream("tempera_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.map { it.split(' ') }
    }

    private val splitter: SonnetWordSplitter by lazy {
        val map = golden.filter { it[0] == "W" }.associate { texts[it[1].toInt()] to it.drop(2).joinToString(" ").split('|') }
        val fn: SonnetWordSplitter = { line -> map[line.fullText] ?: listOf(line.fullText) }
        fn
    }

    private fun near(expected: String, actual: Double, what: String) = assertEquals(what, expected.toDouble(), actual, 2e-5)

    private fun check(whole: Boolean) {
        val m = if (whole) "H" else ""
        val program = compileTemperaCoreProgram(probeLines(), "probe-0", whole, splitter)
        near(golden.first { it[0] == "${m}G" }[1], program.paragraphGapThreshold, "gap")
        val ps = golden.filter { it[0] == "${m}P" }
        assertEquals("段落数", ps.size, program.paragraphs.size)
        ps.forEach { g ->
            val p = program.paragraphs[g[1].toInt()]
            val w = "${m}P${g[1]}"
            assertEquals("$w kind", g[2], p.kind.id)
            assertEquals("$w boundary", g[3], p.boundary.id)
            near(g[4], p.startTime, "$w start")
            near(g[5], p.endTime, "$w end")
            assertEquals("$w lines", g[6].toInt(), p.lines.size)
            assertEquals("$w transition", g[7], p.transitionOut?.kind ?: "-")
            if (p.transitionOut != null) {
                near(g[8], p.transitionOut!!.startTime, "$w tStart")
                near(g[9], p.transitionOut!!.endTime, "$w tEnd")
            }
        }
        val ss = golden.filter { it[0] == "${m}S" }
        assertEquals("shot 总数", ss.size, program.paragraphs.sumOf { it.shots.size })
        ss.forEach { g ->
            val s = program.paragraphs[g[1].toInt()].shots[g[2].toInt()]
            val w = "${m}S ${g[3]}"
            assertEquals("$w id", g[3], s.id)
            assertEquals("$w kind", g[4], s.kind)
            near(g[5], s.startTime, "$w start")
            near(g[6], s.endTime, "$w end")
            near(g[7], s.lyricEndTime, "$w lyricEnd")
            assertEquals("$w bridge", g[8] == "1", s.isBridge)
            assertEquals("$w slices", g[9], s.slices.joinToString(",") { "${it.lineIndex}:${it.segmentStart}-${it.segmentEnd}" }.ifEmpty { "-" })
            near(g[10], s.flowAngle, "$w flow")
            val cams = listOf(s.camera.x, s.camera.y, s.camera.zoom, s.camera.rotation, s.cameraEnd.x, s.cameraEnd.y, s.cameraEnd.zoom, s.cameraEnd.rotation)
            cams.forEachIndexed { k, v -> near(g[11 + k], v, "$w cam$k") }
            assertEquals("$w motif", g[19], s.decor.motif)
            near(g[20], s.decor.hatchAngle, "$w hatch")
            assertEquals("$w cross", g[21].toInt(), s.decor.crossCount)
            assertEquals("$w scribble", g[22].toLong(), s.decor.scribbleSeed)
            assertEquals("$w fragments", g[23].toInt(), s.decor.fragments.size)
            assertEquals("$w fragChars", g[24], s.decor.fragments.joinToString("") { it.char }.ifEmpty { "-" })
            assertEquals("$w watermark", g[25], s.decor.watermark?.text?.replace(' ', '_') ?: "-")
            s.decor.watermark?.let {
                near(g[26], it.x, "$w wmX")
                near(g[27], it.scale, "$w wmScale")
            }
        }
    }

    @Test
    fun slicedProgramMatchesFolia() = check(whole = false)

    @Test
    fun wholeLineProgramMatchesFolia() = check(whole = true)

    @Test
    fun profileTableMatchesFolia() {
        assertEquals(121, TEMPERA_PROFILES.size)
        assertEquals("duo-split", TEMPERA_PROFILES.first().kind)
        assertEquals("monogatari-flash", TEMPERA_PROFILES.last().kind)
        val duo = temperaShotProfile("duo-split")
        assertEquals(0.86, duo.region.w, 1e-12)
        assertEquals(1.13, duo.zoomEnd, 1e-12)
        assertEquals(TpMood.NEUTRAL, duo.mood)
        assertEquals(false, TEMPERA_PROFILES.last().sharedDecor)
    }
}
