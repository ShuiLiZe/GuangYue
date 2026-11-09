package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 绘光：与 Folia 原版逐项对拍。金标 `lumiere_golden.txt` 由 Folia v0.7.25 源码（lumiereProgram.ts / program.ts /
 * catalog.ts / rigs 目录 / light/rig.ts / text/wordStyle.ts / text/windowSlots.ts / lumiereUnitLayout.ts）在 node 里
 * 跑探针歌词（visualizerMemory.probe 的 cjk=1，seed `probe-0`）生成。分词用 Intl.Segmenter 的结果（W 行）。
 * 行：P 程序头、G 段落、S 镜头、R 随机流、L 领头光位、Y 每行排版、C 逐词大小与错落、Q 跳步随机流、CAM 运镜、FB 文字带、
 * FS 固定槽位、FP 自由槽位、PAL 光色、RB 光束、LA 光强、LB 插值光位的光强。
 */
class LumiereProgramTest {
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
        javaClass.classLoader!!.getResourceAsStream("lumiere_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }

    private val splitter: LumiereWordSplitter by lazy {
        val map = golden.filter { it[0] == "W" }.associate { probeTexts[it[1].toInt()] to it.drop(2).joinToString(" ").split('|').map { s -> s.removeSuffix("~") } }
        val fn: LumiereWordSplitter = { line -> map[line.fullText] ?: listOf(line.fullText) }
        fn
    }

    private fun near(expected: String, actual: Double, what: String, tol: Double = 2e-3) = assertEquals(what, expected.toDouble(), actual, tol)

    @Test
    fun catalogHasTenFamiliesOfTen() {
        assertEquals(100, LumiereCatalog.kinds.size)
        assertEquals(10, LumiereCatalog.profiles.map { it.family }.distinct().size)
        LumiereCatalog.profiles.groupBy { it.family }.values.forEach { assertEquals(10, it.size) }
        assertEquals("zenith", LumiereCatalog.profileOf("没有这个").family)
    }

    @Test
    fun randomStreamMatchesFolia() {
        val r = golden.first { it[0] == "R" }
        val rng = lumiereRng("probe-0")
        r.drop(1).forEachIndexed { i, v -> near(v, rng.next(), "R$i", 1e-6) }
        val q = golden.first { it[0] == "Q" }
        val at = lumiereRngAt("probe-0:lumiere-p6:window", 37)
        q.drop(1).forEachIndexed { i, v -> near(v, at.next(), "Q$i", 1e-6) }
    }

    @Test
    fun programMatchesFolia() {
        val lines = probeLines()
        var i = 0
        var checked = 0
        while (i < golden.size) {
            val row = golden[i]
            if (row[0] != "P") { i++; continue }
            val seed = row[1]
            val program = compileLumiereProgram(lines, seed, seamless = row[2] == "1", split = splitter)
            val tag = "$seed/${row[2]}"
            assertEquals("$tag paragraphs", row[3].toInt(), program.paragraphs.size)
            near(row[4], program.duration, "$tag duration")
            near(row[6], program.paragraphGapThreshold, "$tag threshold")
            i++
            var p = -1
            var s = 0
            while (i < golden.size && (golden[i][0] == "G" || golden[i][0] == "S")) {
                val g = golden[i]
                if (g[0] == "G") {
                    p++; s = 0
                    val para = program.paragraphs[p]
                    assertEquals("$tag ${g[1]} id", g[1], para.id)
                    assertEquals("$tag ${g[1]} kind", g[2], para.kind)
                    assertEquals("$tag ${g[1]} boundary", g[3], para.boundary)
                    near(g[4], para.startTime, "$tag ${g[1]} start")
                    near(g[5], para.endTime, "$tag ${g[1]} end")
                    assertEquals("$tag ${g[1]} opening", g[7] == "1", para.opening)
                    assertEquals("$tag ${g[1]} transition", g[8], para.transitionOut?.let { "${it.kind}@${fmt(it.startTime)}" } ?: "-")
                    assertEquals("$tag ${g[1]} lines", g[9], para.lineIndices.joinToString(",").ifEmpty { "-" })
                    assertEquals("$tag ${g[1]} sections", g[10], para.sections?.joinToString(",") { "${fmt(it.startTime)}:${it.kind}" }?.ifEmpty { "-" } ?: "-")
                } else {
                    val shot = program.paragraphs[p].shots[s++]
                    assertEquals("$tag ${g[1]} id", g[1], shot.id)
                    assertEquals("$tag ${g[1]} kind", g[2], shot.kind)
                    near(g[3], shot.startTime, "$tag ${g[1]} start")
                    near(g[4], shot.endTime, "$tag ${g[1]} end")
                    assertEquals("$tag ${g[1]} bridge", g[6] == "1", shot.isBridge)
                    assertEquals("$tag ${g[1]} lines", g[7], shot.lineIndices.joinToString(",").ifEmpty { "-" })
                    checked++
                }
                i++
            }
        }
        assertTrue("至少对拍了一批镜头", checked > 50)
    }

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.')

    private val unit: LumiereUnitModel by lazy {
        val program = compileLumiereProgram(probeLines(), "probe-0", seamless = false, split = splitter)
        LumiereUnitModel(program.paragraphs[6], program.seed, 800.0, 360.0)
    }

    @Test
    fun unitLayoutMatchesFolia() {
        assertEquals(golden.first { it[0] == "L" }[1], unit.lead.kind)
        val y = golden.first { it[0] == "Y" }[1].split(',')
        y.forEachIndexed { i, v -> assertEquals("typography $i", v, unit.typographyOfLine(i)) }
        golden.filter { it[0] == "CAM" }.forEach { c ->
            val w = c[1].toDouble()
            val h = if (w == 800.0) 360.0 else 800.0
            val cam = lumiereCamera(w, h, unit.lead.camera, unit.paragraph.startTime, unit.paragraph.endTime, unit.paragraph.sections)(c[2].toDouble())
            near(c[3], cam.x, "cam x $w ${c[2]}"); near(c[4], cam.y, "cam y"); near(c[5], cam.scale, "cam scale", 1e-5); near(c[6], cam.rotation, "cam rot", 1e-6)
        }
        golden.filter { it[0] == "FB" }.forEach { f ->
            val w = f[1].toDouble()
            val band = lumiereFrameBand(w, if (w == 800.0) 360.0 else 800.0)
            near(f[2], band.left, "band left"); near(f[3], band.right, "band right"); near(f[4], band.top, "band top"); near(f[5], band.bottom, "band bottom")
        }
    }

    @Test
    fun wordStyleMatchesFolia() {
        golden.filter { it[0] == "C" }.forEach { c ->
            val index = c[1].toInt()
            val line = unit.paragraph.lines[index]
            val words = lumiereSegmentWords(line, splitter)
            val sd = "${unit.seed}:$index:${line.fullText}"
            val scales = lumiereWordScales(words, sd)
            val jags = lumiereWordJags(words, scales, sd)
            c[2].split(',').forEachIndexed { k, v -> near(v, scales[k], "scale $index/$k", 1e-5) }
            c[3].split(',').forEachIndexed { k, v -> near(v, jags[k], "jag $index/$k", 1e-5) }
        }
    }

    @Test
    fun slotsMatchFolia() {
        val lead = unit.lead
        val region = LumiereRegion(lead.region.cx * 2.2222, lead.region.cy, lead.region.w * 2.2222, lead.region.h)
        golden.filter { it[0] == "FS" }.forEach { f ->
            val s = lumiereFixedSlot(f[1], f[2].toInt(), region, lead.heroSize, 0.95) { o, sc -> lead.heroSize * sc * (1 + 0.1 * o) }
            near(f[3], s.dx, "fs dx ${f[1]} ${f[2]}", 1e-5); near(f[4], s.dy, "fs dy", 1e-5); near(f[5], s.scale, "fs scale"); near(f[6], s.alpha, "fs alpha"); near(f[7], s.orient, "fs orient")
        }
        val fp = lumiereFreePlacements(lumiereRng("${unit.seed}:placements:1"), region, 0.95)
        golden.filter { it[0] == "FP" }.forEach { f ->
            val s = fp.getValue(f[1].toInt())
            near(f[2], s.dx, "fp dx ${f[1]}", 1e-5); near(f[3], s.dy, "fp dy", 1e-5); near(f[4], s.scale, "fp scale", 1e-5)
            near(f[5], s.alpha, "fp alpha"); near(f[6], s.orient, "fp orient"); near(f[7], s.rotation, "fp rot", 1e-5)
        }
    }

    @Test
    fun lightMatchesFolia() {
        val pal = golden.first { it[0] == "PAL" }.drop(1).map { it.toDouble() }
        val palette = resolveLumierePalette(doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(1.0, 1.0, 1.0), lumiereRgbOfArgb(0x9ca3af), 0.3)
        (palette.light.toList() + palette.lit.toList() + palette.unlit.toList()).forEachIndexed { i, v -> assertEquals("pal $i", pal[i], v, 1e-5) }
        val rb = golden.first { it[0] == "RB" }
        assertEquals(rb[1], unit.shots[0].profile.kind)
        val beams = lumiereResolveBeams(unit.rigs[0], 37.5, 800.0 / 360.0, 1.0, 0.0, palette.light, 1.5)
        assertEquals(rb[2].toInt(), beams.size)
        rb.drop(3).filter { it.isNotEmpty() }.forEachIndexed { i, b ->
            val v = b.split(',')
            near(v[0], beams[i].ox, "ox $i", 1e-5); near(v[1], beams[i].oy, "oy $i", 1e-5); near(v[2], beams[i].dx, "dx $i", 1e-5)
            near(v[3], beams[i].dy, "dy $i", 1e-5); near(v[4], beams[i].reach, "reach $i", 1e-5)
        }
        golden.filter { it[0] == "LA" }.forEach { l -> near(l[3], lumiereLightAt(beams, l[1].toDouble(), l[2].toDouble()), "lightAt ${l[1]},${l[2]}", 1e-4) }
        golden.filter { it[0] == "LB" }.forEach { l ->
            val aspect = 800.0 / 360.0
            val rig = LumiereCatalog.profileOf(l[1]).light(aspect, lumiereRng("x"))
            val bs = lumiereResolveBeams(rig, 10.0, aspect, 1.0, 0.0, doubleArrayOf(1.0, 1.0, 1.0), 5.0)
            near(l[2], lumiereLightAt(bs, 1.1, 0.5), "${l[1]} a", 5e-3); near(l[3], lumiereLightAt(bs, 0.6, 0.3), "${l[1]} b", 5e-3)
        }
    }

    @Test
    fun windowLaysOutTheProbeLine() {
        // 量字桩：CJK = 1 em、空格 0.28 em、其他 0.55 em（与商籁同一个）。只验结构：37.5 秒当前行逐字都在、按词大小错落、横排一行。
        val measure = LumiereTextMeasure { text, px ->
            var s = 0.0
            text.codePoints().forEach { c -> s += when { (c in 0x2e80..0x9fff) || (c in 0xff00..0xffef) || (c in 0x3000..0x303f) -> px; c == ' '.code -> px * 0.28; else -> px * 0.55 } }
            s
        }
        val lead = unit.lead
        val window = LumiereLyricWindow(800.0, 360.0, unit.paragraph.lines, measure, unit.textRegion, lead.heroSize * 360, 2, lead.typography, unit.typographyOfLine, lead.decay, unit.seed, split = splitter)
        val frame = window.frame(37.5, emptyList(), doubleArrayOf(1.0, 0.95, 0.88), doubleArrayOf(0.8, 0.8, 0.85), 0.22, 1.0)
        val current = frame.glyphs.filter { it.fontPx > lead.heroSize * 360 * 0.55 }
        assertTrue("当前行至少 20 个字", current.size >= 20)
        val sizes = current.map { it.fontPx }
        assertTrue("逐字大小不一", sizes.max() / sizes.min() > 2.0)
        val t = window.lineTransform(0, 37.5)
        assertEquals("横排", 0.0, t.orient, 1e-9)
        assertEquals("单行", 0.0, t.wrap, 1e-9)
    }
}
