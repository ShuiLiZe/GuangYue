package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R7 花火①：整首编译与 Folia 原版逐项对拍。金标 `hanabi_golden.txt` 由 Folia hanabiProgram.ts / program.ts / catalog.ts
 * （esbuild 打包后 node 跑）生成：A = 逐字时间、seed probe-0；B = 前奏 4.2 秒（出前奏段）+ 每三句一句逐词时间；
 * C = 换 seed；I = 纯音乐。探针含长句（切成多个镜头）、两处 4.5 秒空隙、一处 14 秒空隙（切成多个间奏镜头）、一段副歌。
 * 分词用 Intl.Segmenter 的结果（tempera_golden.txt 的 W 行）。
 */
class HanabiCoreProgramTest {
    private val texts = listOf(
        "迷失在无边际这幽深的森林", "风吹过", "我们慢慢走回去，星河与风一起落在很远很远的地方", "光落在晨雾里",
        "你说夜色会把所有的名字都轻轻藏起来", "晚安", "在城市尽头等一场迟到的雨", "把回忆折成纸船放进河流",
        "轻轻唱 hello again 在无人的街角", "我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔",
    )

    private fun read(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }

    private val segs: Map<String, List<String>> by lazy {
        read("tempera_golden.txt").filter { it[0] == "W" }.associate { texts[it[1].toInt()] to it.drop(2).joinToString(" ").split('|') }
    }
    private val splitter: SonnetWordSplitter = { line -> segs[line.fullText] ?: listOf(line.fullText) }

    private fun lines(offset: Double, wordMode: Boolean): List<Line> {
        val out = ArrayList<Line>()
        var t = offset
        for (i in 0 until 28) {
            val text = texts[i % 10]
            val dur = 1.2 + (i % 5) * 0.6 + (if (i % 7 == 3) 5.0 else 0.0)
            val pieces = if (wordMode && i % 3 == 0) segs.getValue(text) else text.codePoints().toArray().map { String(Character.toChars(it)) }
            val step = dur / pieces.size
            out += Line(words = pieces.mapIndexed { o, c -> Word(c, t + o * step, t + (o + 1) * step) }, startTime = t, endTime = t + dur + 0.1,
                fullText = text, translation = "translation $i", isChorus = i in 16 until 22)
            t += dur + 0.4 + (if (i == 7 || i == 15) 4.5 else 0.0) + (if (i == 22) 14.0 else 0.0)
        }
        return out
    }

    private fun near(e: String, a: Double, w: String) = assertEquals(w, e.toDouble(), a, 2e-6)

    @Test
    fun programsMatchFolia() {
        val golden = read("hanabi_golden.txt")
        val programs = mapOf(
            "A" to compileHanabiProgram(lines(0.0, false), "probe-0", split = splitter),
            "B" to compileHanabiProgram(lines(4.2, true), "probe-0", split = splitter),
            "C" to compileHanabiProgram(lines(1.0, true), "song-xyz", split = splitter),
            "I" to compileHanabiProgram(emptyList(), "probe-0", split = splitter),
        )
        var shots = 0
        golden.forEach { g ->
            val p = programs.getValue(g[1])
            val w = g.take(4).joinToString(" ")
            when (g[0]) {
                "G" -> {
                    assertEquals("$w 段数", g[2].toInt(), p.paragraphs.size)
                    near(g[3], p.duration, "$w duration")
                    if (g[1] != "I") {
                        near(g[4], p.lyricEndTime!!, "$w lyricEnd")
                        assertEquals("$w chorus", g[5], p.chorus.joinToString(",") { "${fmt(it.startTime)}:${fmt(it.endTime)}" })
                    }
                }
                "P" -> {
                    val pp = p.paragraphs[g[2].toInt()]
                    assertEquals("$w kind", g[3], pp.kind)
                    near(g[4], pp.startTime, "$w start"); near(g[5], pp.endTime, "$w end"); near(g[6], pp.lyricEndTime, "$w lyricEnd")
                    assertEquals("$w lines", g[7], pp.lineIndices.joinToString(",").ifEmpty { "-" })
                    assertEquals("$w transition", g[8], pp.transitionOut?.kind ?: "-")
                    pp.transitionOut?.let { near(g[9], it.startTime, "$w tStart"); near(g[10], it.endTime, "$w tEnd") }
                }
                "S" -> {
                    val s = p.paragraphs[g[2].toInt()].shots[g[3].toInt()]
                    assertEquals("$w id", g[4], s.id)
                    assertEquals("$w kind", g[5], s.kind)
                    near(g[6], s.startTime, "$w start"); near(g[7], s.endTime, "$w end"); near(g[8], s.lyricEndTime, "$w lyricEnd")
                    assertEquals("$w bridge", g[9] == "1", s.isBridge)
                    assertEquals("$w slices", g[10], s.slices.joinToString(",") { "${it.lineIndex}:${it.startOffset}-${it.endOffset}" }.ifEmpty { "-" })
                    shots++
                }
            }
        }
        assertEquals(golden.count { it[0] == "S" }, shots)
        programs.forEach { (k, p) -> assertEquals("$k 镜头总数", golden.count { it[0] == "S" && it[1] == k }, p.paragraphs.sumOf { it.shots.size }) }
    }

    private fun fmt(v: Double): String {
        val x = Math.round(v * 1e6) / 1e6
        return if (x == Math.floor(x)) x.toLong().toString() else x.toString()
    }
}
