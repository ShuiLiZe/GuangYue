package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 花火⑥b：文字引擎与 Folia hanabi/text/（animations.ts / blocks.ts / lyricText.ts）对拍。
 * 金标 `hanabi_text_golden.txt`（docs/port/scripts/hanabi-text-golden-gen.js）：node 里用假 Pixi + 估算字宽跑真的 buildHanabiText，
 * 27 组（12 种色块 × 17 种入场 × 9 种强调 × 7 种退场 × 9 种排法轮换）× 15 个时刻，逐个比字 / 阴影 / 描边 / 光晕 / 特效层精灵、
 * 色块 Graphics 的 transform 与重画多边形、anchors；外加 enterPose / exitPose / singPose / 时间表 / 震动 / 哈希 / 色块排版参数。
 */
class HanabiTextTest {
    private fun close(msg: String, want: Double, got: Double) {
        if (want.isNaN()) { assertTrue("$msg want NaN got $got", got.isNaN()); return }
        assertEquals(msg, want, got, 2e-6 + 2e-7 * kotlin.math.abs(want))
    }

    private val palette = HbPalette(0x1a1030, 0xfff6ea, listOf(0xe8344e, 0x2f6fe0, 0xf28c28), listOf(0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff), 0xff5d8f, 0xffe08a)

    private fun mkLine(segs: List<String>, t0: Double, per: Double): Line {
        val words = ArrayList<Word>()
        var t = t0
        segs.forEach { s ->
            val d = if (s.trim().isNotEmpty()) per * s.codePointCount(0, s.length) else 0.0
            words += Word(s, t, t + d); t += d
        }
        return Line(words = words, startTime = t0, endTime = t, fullText = segs.joinToString(""), wordSegments = segs)
    }

    private val lines = listOf(
        mkLine(listOf("星河", "与", "风", "一起", "落在", "很远", "很远", "的", "地方"), 10.0, 0.22),
        mkLine(listOf("我们", "慢慢", "走", "回去", "，"), 10.0, 0.3),
        mkLine(listOf("Hold", "　", "me", "　", "tonight"), 10.0, 0.25),
    )

    private fun golden() = javaClass.classLoader!!.getResourceAsStream("hanabi_text_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }

    @Test
    fun textEngineMatchesFolia() {
        val all = golden()
        var text: HbText? = null
        var tag = ""
        var lastT = Double.NaN
        val seen = HashMap<String, HashSet<Int>>()
        var checked = 0
        fun flushVisibility() {
            val tx = text ?: return
            if (lastT.isNaN()) return
            // golden 里没出现的精灵 / 色块在 Kotlin 侧也必须不可见。
            tx.rigs.forEachIndexed { k, r ->
                assertEquals("$tag t=$lastT main $k visible", seen["M"]?.contains(k) == true, r.main.visible)
                if (tx.spec.hardShadow) assertEquals("$tag t=$lastT shadow $k visible", seen["S"]?.contains(k) == true, r.shadow.visible)
            }
            tx.blocks.pieces.flatMap { it.parts }.forEachIndexed { k, p -> assertEquals("$tag t=$lastT block $k visible", seen["G"]?.contains(k) == true, p.visible) }
            seen.clear()
        }
        for (ln in all) {
            val f = ln.split(' ')
            when (f[0]) {
                "CASE" -> {
                    flushVisibility(); lastT = Double.NaN
                    val spec = HbTextSpec(f[3], f[4].toDouble(), f[5].toDouble(), f[6].toDouble(), f[7], f[8], f[9], f[10], f[11].toDouble(), f[12] == "true", f[13] == "true")
                    val id = f[1].toInt()
                    text = HbText(HbTextOptions(800.0, 360.0, lines[f[2].toInt()], 560.0, 144.0, spec, palette, "case$id", 9.5, 13.2, 0.7, f[14].toDouble(), 0.95))
                    tag = "case$id(${f[7]}/${f[8]}/${f[9]}/${f[10]}/${f[3]})"
                    assertEquals("$tag rigs", f[15].toInt(), text!!.rigs.size)
                    assertEquals("$tag block parts", f[16].toInt(), text!!.blocks.pieces.sumOf { it.parts.size } + extraGraphics(text!!))
                }
                "M", "S", "O", "H", "X", "G", "A" -> {
                    val tx = text!!
                    val t = f[1].toDouble()
                    if (t != lastT) { flushVisibility(); lastT = t; tx.update(t) }
                    val k = f[2].toInt()
                    seen.getOrPut(f[0]) { HashSet() } += k
                    val m = "$tag t=$t ${f[0]}$k"
                    if (f[0] == "G") {
                        val parts = tx.blocks.pieces.flatMap { it.parts }
                        if (k >= parts.size) continue
                        val p = parts[k]
                        assertTrue("$m visible", p.visible)
                        close("$m x", f[3].toDouble(), p.x); close("$m y", f[4].toDouble(), p.y); close("$m rot", f[5].toDouble(), p.rotation)
                        close("$m sx", f[6].toDouble(), p.sx); close("$m sy", f[7].toDouble(), p.sy); close("$m alpha", f[8].toDouble(), p.alpha)
                        assertEquals("$m tint", f[9].toInt(), p.tint)
                        val want = if (f.size > 10 && f[10].isNotEmpty()) f[10].split(';').map { s -> s.split(',').map { it.toDouble() } } else emptyList()
                        val got = p.ops.filter { it.kind == HbShapeOp.POLY }.map { it.f.toList() }
                        assertEquals("$m polys", want.size, got.size)
                        want.forEachIndexed { a, w -> assertEquals("$m poly$a n", w.size, got[a].size); w.forEachIndexed { b, v -> close("$m poly$a[$b]", v, got[a][b]) } }
                        checked++
                        continue
                    }
                    if (f[0] == "A") {
                        val a = tx.anchors(t)[k]
                        close("$m x", f[3].toDouble(), a.x); close("$m y", f[4].toDouble(), a.y); close("$m size", f[5].toDouble(), a.size); close("$m alpha", f[6].toDouble(), a.alpha)
                        checked++
                        continue
                    }
                    val rig = if (f[0] == "X") tx.rigs[k / 2] else tx.rigs[k]
                    val s = when (f[0]) { "M" -> rig.main; "S" -> rig.shadow; "O" -> rig.outline; "H" -> rig.halo; else -> if (k % 2 == 0) rig.fxA else rig.fxB }
                    assertTrue("$m visible", s.visible)
                    close("$m x", f[3].toDouble(), s.x); close("$m y", f[4].toDouble(), s.y); close("$m rot", f[5].toDouble(), s.rotation)
                    // Pixi 的 scale = size / entry.px × 姿态；Kotlin 同一套。
                    close("$m sx", f[6].toDouble(), s.sx); close("$m sy", f[7].toDouble(), s.sy); close("$m skew", f[8].toDouble(), s.skew)
                    close("$m alpha", f[9].toDouble(), s.alpha)
                    assertEquals("$m tint", f[10].toInt(), s.tint)
                    if (f[0] == "M") assertEquals("$m glyph", f[11], s.glyph)
                    checked++
                }
            }
        }
        flushVisibility()
        assertTrue("checked $checked", checked > 5000)
    }

    /** TS 侧色块之外另建的 Graphics：type 光标、每条下划线、wipe 遮罩。 */
    private fun extraGraphics(t: HbText) =
        (if (t.spec.enter == "type") 1 else 0) + t.underlines.size + (if (t.spec.enter == "wipe") 1 else 0)

    @Test
    fun animationCurvesMatchFolia() {
        val ctx = { motion: Double, time: Double -> HbFxContext(2, 7, doubleArrayOf(0.31, 0.77, 0.12, 0.58), 1.7, -0.6, 0.8, 0.6, time, motion, 9.0) }
        var n = 0
        for (ln in golden()) {
            val f = ln.split(' ')
            when (f[0]) {
                "EN", "EX" -> {
                    val p = f[2].toDouble()
                    val pose = if (f[0] == "EN") hbEnterPose(f[1], p, ctx(1.3, p * 0.7 + 0.013)) else hbExitPose(f[1], p, ctx(0.8, p * 0.6 + 0.021))
                    val got = doubleArrayOf(pose.dx, pose.dy, pose.scaleX, pose.scaleY, pose.rotation, pose.alpha, pose.skew, pose.scramble.toDouble(), pose.rgbSplit, pose.blur, pose.split, if (pose.backside) 1.0 else 0.0, pose.reveal)
                    got.forEachIndexed { k, v -> close("${f[0]} ${f[1]} p=$p #$k", f[3 + k].toDouble(), v) }
                    n++
                }
                "SG" -> {
                    val s = hbSingPose(f[1], f[2].toDouble(), 0.45, 0.37, 1.25)
                    doubleArrayOf(s.dx, s.dy, s.scale, s.scaleX, s.scaleY, s.rotation, s.flash, s.glow, s.color).forEachIndexed { k, v -> close("SG ${f[1]} u=${f[2]} #$k", f[3 + k].toDouble(), v) }
                    n++
                }
                "OR" -> {
                    val i = HB_ENTER_FX.indexOf(f[1])
                    val r = lumiereRng("ord$i")
                    val ranks = hbEnterOrder(f[1], 9) { r.next() }
                    ranks.forEachIndexed { k, v -> close("OR ${f[1]} $k", f[2 + k].toDouble(), v) }
                    n++
                }
                "SE" -> {
                    val i = HB_ENTER_FX.indexOf(f[1])
                    val r = lumiereRng("ord$i")
                    val ranks = hbEnterOrder(f[1], 9) { r.next() }.toList()
                    val withBlank = (ranks.subList(0, 4) + listOf(-1.0) + ranks.subList(4, 9)).toDoubleArray()
                    val s = hbScheduleEnter(f[1], withBlank, 10.0, f[2].toDouble(), f[3].toDouble())
                    close("SE ${f[1]} dur", f[4].toDouble(), s.duration)
                    s.start.forEachIndexed { k, v -> close("SE ${f[1]} ${f[2]} start $k", f[5 + k].toDouble(), v) }
                    n++
                }
                "SX" -> {
                    val ranks = f[2].split(',').map { it.toDouble() }.toDoubleArray()
                    val s = hbScheduleExit(ranks, 20.0, f[1].toDouble())
                    val bar = f.indexOf("|")
                    s.start.forEachIndexed { k, v -> close("SX start $k", f[3 + k].toDouble(), v) }
                    s.end.forEachIndexed { k, v -> close("SX end $k", f[bar + 1 + k].toDouble(), v) }
                    n++
                }
                "SH" -> {
                    val s = hbStampShake(10 + f[1].toDouble(), listOf(10.0, 10.08, 10.2), 4.5)
                    close("SH x ${f[1]}", f[2].toDouble(), s.first); close("SH y ${f[1]}", f[3].toDouble(), s.second); n++
                }
                "HS" -> { close("HS ${f[1]} ${f[2]}", f[3].toDouble(), hbTextHash01(f[1].toInt(), f[2].toInt())); n++ }
                "BP" -> {
                    val p = hbBlockLayoutParams(f[1], f[2] == "true")
                    doubleArrayOf(p.tracking, p.wordGap, p.lineHeight, p.minAdvance, p.stairStep, p.padding).forEachIndexed { k, v -> close("BP ${f[1]} ${f[2]} $k", f[3 + k].toDouble(), v) }
                    n++
                }
                "DK" -> { assertEquals("DK", f[3].toInt(), hbDarken(f[1].toInt(), f[2].toDouble())); n++ }
            }
        }
        assertTrue("n=$n", n > 400)
    }

    @Test
    fun catalogOrderMatchesLite() {
        assertEquals(HANABI_PROFILE_LITE.size, HANABI_PROFILES.size)
        HANABI_PROFILES.forEachIndexed { i, p ->
            val l = HANABI_PROFILE_LITE[i]
            assertEquals(l.kind, p.kind); assertEquals(l.family, p.family); assertEquals(l.mood, p.mood)
            close("${p.kind} rise", l.rise, p.rise); close("${p.kind} push", l.push, p.push); close("${p.kind} roll", l.roll, p.roll); close("${p.kind} punch", l.punch, p.punch)
        }
    }
}
