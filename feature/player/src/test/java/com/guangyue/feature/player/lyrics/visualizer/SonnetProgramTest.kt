package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4 商籁：与 Folia 原版逐项对拍。金标 `sonnet_golden.txt` 由 Folia 源码（sonnetProgram.ts / sonnetTypographyLayout.ts）
 * 在 node 里跑探针歌词（visualizerMemory.probe 的 cjk=1，seed `probe-0`）生成；量字用同一个桩
 * （CJK = 1 em、空格 0.28 em、其他 0.55 em），分词用 Intl.Segmenter 的结果（W 行）。
 */
class SonnetProgramTest {
    private val probeTexts = listOf(
        "迷失在无边际这幽深的森林", "风吹过", "我们慢慢走回去，星河与风一起落在很远很远的地方", "光落在晨雾里",
        "你说夜色会把所有的名字都轻轻藏起来", "晚安", "在城市尽头等一场迟到的雨", "把回忆折成纸船放进河流",
        "轻轻唱 hello again 在无人的街角", "我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔",
    )

    private fun probeLines(): List<Line> = (0 until 40).map { i ->
        val start = i * 3.0
        val text = probeTexts[i % 10]
        val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
        val step = 2.8 / chars.size
        Line(
            words = chars.mapIndexed { o, c -> Word(c, start + o * step, start + (o + 1) * step) },
            startTime = start,
            endTime = start + 2.9,
            fullText = text,
            translation = "translation $i",
            isChorus = i % 4 == 0,
        )
    }

    private val golden: List<List<String>> by lazy {
        javaClass.classLoader!!.getResourceAsStream("sonnet_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }

    private val splitter: SonnetWordSplitter by lazy {
        val map = golden.filter { it[0] == "W" }.associate { probeTexts[it[1].toInt()] to it.drop(2).joinToString(" ").split('|') }
        val fn: SonnetWordSplitter = { line -> map[line.fullText] ?: listOf(line.fullText) }
        fn
    }

    private val stubMeasure = SonnetMeasure { text, _, px ->
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

    private fun near(expected: String, actual: Double, what: String) {
        assertEquals(what, expected.toDouble(), actual, 2e-3)
    }

    @Test
    fun hashesMatchFolia() {
        assertEquals(1817709726L, sonnetHash("probe-0:sonnet-p6"))
        val shotSeed = 1817709726L
        assertEquals(26L, sonnetGeoVariant(shotSeed))
        assertEquals(7, sonnetBackgroundMgVariant(shotSeed))
        assertEquals(3, sonnetBackgroundDecorVariant(shotSeed))
        assertEquals(5, sonnetFixedGeoVariant(shotSeed))
    }

    @Test
    fun programMatchesFolia() {
        val program = compileSonnetProgram(probeLines(), "probe-0", splitter)
        near(golden.first { it[0] == "G" }[1], program.paragraphGapThreshold, "gap")
        val ps = golden.filter { it[0] == "P" }
        assertEquals(ps.size, program.paragraphs.size)
        ps.forEach { g ->
            val p = program.paragraphs[g[1].toInt()]
            assertEquals("P${g[1]} kind", g[2], p.kind.id)
            near(g[3], p.startTime, "P${g[1]} start")
            near(g[4], p.endTime, "P${g[1]} end")
            assertEquals("P${g[1]} transition", g[5], p.transitionOut?.kind?.id ?: "-")
            if (p.transitionOut != null) near(g[6], p.transitionOut!!.startTime, "P${g[1]} transition start")
        }
        golden.filter { it[0] == "S" }.forEach { g ->
            val s = program.paragraphs[g[1].toInt()].shots[g[2].toInt()]
            val tag = "S${g[1]}.${g[2]}"
            assertEquals("$tag kind", g[3], s.kind.id)
            assertEquals("$tag lines", g[4], s.lineIndices.joinToString(","))
            near(g[5], s.camera.x, "$tag cam x"); near(g[6], s.camera.y, "$tag cam y")
            near(g[7], s.camera.zoom, "$tag zoom"); near(g[8], s.camera.rotation, "$tag rot")
        }
    }

    @Test
    fun typographyMatchesFolia() {
        val program = compileSonnetProgram(probeLines(), "probe-0", splitter)
        val byShot = golden.filter { it[0] == "L" }.groupBy { it[1].toInt() to it[2].toInt() }
        assertTrue(byShot.isNotEmpty())
        byShot.forEach { (key, rows) ->
            val paragraph = program.paragraphs[key.first]
            val shot = paragraph.shots[key.second]
            val linesSegs = shot.lineIndices.map { li -> paragraph.lines.first { it.sourceIndex == li } }
                .map { l -> l.segments.filter { sonnetIsLayoutSegment(it) } }.filter { it.isNotEmpty() }
            val wc = maxOf(1, linesSegs.flatten().count { it.isWordLike })
            val fs = sonnetShotFontSize(shot.kind, wc, 800.0, 1.0)
            val placements = resolveSonnetTypographyLayout(linesSegs, shot.kind, 800.0, 360.0, fs, stubMeasure)
            assertEquals("S$key count", rows.size, placements.size)
            rows.forEachIndexed { i, g ->
                val p = placements[i]
                val tag = "S$key #$i"
                assertEquals("$tag role", g[3], when (p.role) { SonnetRole.HERO -> "hero"; SonnetRole.SEMI_HERO -> "semi-hero"; SonnetRole.SUPPORT -> "support"; SonnetRole.DECORATION -> "decoration" })
                assertEquals("$tag seg", g[4].toInt(), p.segmentIndex)
                near(g[5], p.x, "$tag x"); near(g[6], p.y, "$tag y"); near(g[7], p.fontScale, "$tag fs"); near(g[8], p.rotation, "$tag rot")
                assertEquals("$tag vertical", g[9] == "1", p.vertical)
                assertEquals("$tag dir", g[10] == "v", p.layoutVertical)
                near(g[11], p.enterX, "$tag ex"); near(g[12], p.enterY, "$tag ey")
                near(g[13], p.measuredWidth, "$tag w"); near(g[14], p.measuredHeight, "$tag h")
            }
        }
    }

    @Test
    fun transitionFramesAndGlyphTiming() {
        val program = compileSonnetProgram(probeLines(), "probe-0", splitter)
        // 段落 0 → 1 是 mono-glitch：出场末段撕裂量接近 1、透明度掉到 0
        val f = sonnetSceneTransitionFrame(program, 0, 0, 5.99, true)
        assertTrue(f.glitch > 0.9)
        assertEquals(1.0, sonnetSceneTransitionFrame(program, 0, 0, 3.0, true).alpha, 1e-9)
        assertEquals(0.65, sonnetGlyphMotionDuration(0.0, 1.0).coerceAtLeast(0.0).let { minOf(it, 0.72) }, 0.1)
        assertEquals(1.0, sonnetSegmentProgress(0.0, 0.5, 2.0), 1e-9)
    }
}
