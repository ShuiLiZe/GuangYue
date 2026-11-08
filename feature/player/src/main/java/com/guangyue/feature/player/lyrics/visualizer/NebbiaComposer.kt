// 对应 Folia（v0.7.25 沙箱浅克隆）src/components/visualizer/nebbia/：nebbiaChars.ts（逐字序列 + 分词分组）、
// nebbiaBlocks.ts（主词 / 副主词 / 虚词打分、并块、横竖量尺寸）、nebbiaCompositions.ts（横条、两三行错落、十字、
// 竖柱、刊头、徽章、竖排几列、环绕）、nebbiaLayout.ts（整首按时间一次排完：每句挑构图、在 3×4 个候选位置与
// 四档字号里找不压住「还没被雾吞掉的句子」的地方）。坐标单位都是基准字号 1；结果只由歌词、时间和屏幕尺寸决定。
// 省略：emoji → lucide 图标与句尾涂鸦、片尾曲名、按笔画数放大复杂字（广乐没有笔顺数据）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.WordSegmentation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object NebbiaComposer {
    data class Ch(val ch: String, val t: Double, val d: Double)
    data class Src(val chars: List<Ch>, val words: List<List<Int>>, val t: Double, val end: Double)

    enum class Role { HERO, SEMI, SUPPORT, PARTICLE }
    private val ROLE_SCALE = mapOf(Role.HERO to 2.0, Role.SEMI to 1.35, Role.SUPPORT to 1.0, Role.PARTICLE to 0.78)
    private val ROLE_SCALE_FAST = mapOf(Role.HERO to 1.55, Role.SEMI to 1.18, Role.SUPPORT to 1.0, Role.PARTICLE to 0.85)
    const val FLOW_GAP = 0.3
    const val STACK_GAP = 0.32

    private val CJK = Regex("[\u3040-\u30ff\u3400-\u9fff\uf900-\ufaff\uac00-\ud7af]")
    private val PUNCT = Regex("^[，。、！？!?,.・…]$")
    private val TURN_IN_VERTICAL = Regex("^[ー—～〜…-]$")
    private val KATA = Regex("^[\u30a0-\u30ff]$")
    private val PARTICLES = "的了着过吗呢吧啊呀嘛之与和是也都就还又被把给のがをにはへとでやもねよなかしてたっ".map { it.toString() }.toSet()

    private fun isWide(ch: String) = ch.isNotEmpty() && ch[0] in '\u3000'..'\u9fff' || ch.isNotEmpty() && ch[0] in '\uff00'..'\uffef'
    fun advanceOf(ch: String): Double = when {
        ch == " " -> 0.32
        Regex("[，。、・．]").matches(ch) -> 0.5
        isWide(ch) -> 1.0
        Regex("[il.,'!|:;]").matches(ch) -> 0.3
        Regex("[mwMW]").matches(ch) -> 0.72
        else -> 0.5
    }

    // ---------- nebbiaChars ----------
    /** 逐字序列：有逐字时间轴时每个词的时间平分给它的字，没有时整句平分。 */
    fun buildLines(lines: List<Line>): List<Src> = lines.map { line ->
        val text = line.fullText
        val cps = ArrayList<String>()
        var i = 0
        while (i < text.length) { val n = Character.charCount(text.codePointAt(i)); cps += text.substring(i, i + n); i += n }
        val starts = DoubleArray(cps.size) { line.startTime }
        val durs = DoubleArray(cps.size)
        if (line.words.isNotEmpty()) {
            var cursor = 0
            val unitStart = IntArray(cps.size + 1).also { var o = 0; cps.forEachIndexed { k, s -> it[k] = o; o += s.length }; it[cps.size] = o }
            fun idxAt(u: Int): Int { var k = 0; while (k + 1 < cps.size && unitStart[k + 1] <= u) k++; return k }
            for (w in line.words) {
                val wt = w.text
                if (wt.isBlank()) continue
                val at = text.indexOf(wt, cursor).takeIf { it >= 0 } ?: continue
                val a = idxAt(at); val b = idxAt(at + wt.length - 1)
                val n = b - a + 1
                val step = (w.endTime - w.startTime).coerceAtLeast(0.0) / n
                for (k in a..b) { starts[k] = w.startTime + step * (k - a); durs[k] = step }
                cursor = at + wt.length
            }
        } else {
            val step = (line.endTime - line.startTime).coerceAtLeast(0.0) / max(1, cps.size)
            cps.indices.forEach { k -> starts[k] = line.startTime + step * k; durs[k] = step }
        }
        val chars = cps.indices.map { Ch(cps[it], starts[it], durs[it]) }
        // 分词（WordSegmentation = Folia segmentLyricWords）对到字下标；纯空白的段不成词。
        val offs = IntArray(cps.size).also { var o = 0; cps.forEachIndexed { k, s -> it[k] = o; o += s.length } }
        fun indexAt(u: Int): Int { var k = 0; while (k + 1 < offs.size && offs[k + 1] <= u) k++; return k }
        val words = ArrayList<List<Int>>()
        if (cps.isNotEmpty()) for (seg in WordSegmentation.segmentLyricWords(line)) {
            if (seg.segment.isBlank()) continue
            val from = indexAt(seg.index); val to = indexAt(seg.index + seg.segment.length - 1)
            val run = (from..to).filter { chars[it].ch.isNotBlank() }
            if (run.isNotEmpty()) words += run
        }
        Src(chars, words, line.startTime, max(line.startTime, line.endTime))
    }

    // ---------- nebbiaBlocks ----------
    fun mergeWords(chars: List<Ch>, words: List<List<Int>>): List<List<Int>> {
        val out = ArrayList<MutableList<Int>>()
        fun kata(w: List<Int>) = w.all { KATA.matches(chars[it].ch) }
        for (w in words) {
            val last = out.lastOrNull()
            if (last != null && (w.all { PUNCT.matches(chars[it].ch) } || (kata(w) && kata(last) && w[0] == last.last() + 1))) last += w
            else out += w.toMutableList()
        }
        return out
    }

    private fun scoreWord(chars: List<Ch>, w: List<Int>): Double {
        val len = w.count { chars[it].ch.isNotBlank() }
        val t0 = chars[w.first()].t; val last = chars[w.last()]
        return min(len, 8) * 14.0 + (last.t + last.d - t0).coerceIn(0.0, 2.5) * 18.0
    }

    fun assignRoles(chars: List<Ch>, words: List<List<Int>>): List<Role> {
        val scores = words.map { scoreWord(chars, it) }
        var hero = 0
        scores.forEachIndexed { i, s -> if (s > scores[hero]) hero = i }
        var semi = -1
        if (words.size >= 4) words.forEachIndexed { i, w ->
            if (i == hero || abs(i - hero) < 2 || w.size < 2 || scores[i] < scores[hero] * 0.35) return@forEachIndexed
            if (semi < 0 || scores[i] > scores[semi]) semi = i
        }
        return words.mapIndexed { i, w ->
            when {
                i == hero && words.size > 1 -> Role.HERO
                i == semi -> Role.SEMI
                w.size == 1 && chars[w[0]].ch in PARTICLES -> Role.PARTICLE
                else -> Role.SUPPORT
            }
        }
    }

    data class BlockWord(val idx: List<Int>, val scale: Double, val cjk: Boolean)
    data class Block(val role: Role, val words: List<BlockWord>)

    private fun isCjkWord(chars: List<Ch>, w: List<Int>) = w.all { CJK.containsMatchIn(chars[it].ch) || PUNCT.matches(chars[it].ch) }

    fun makeBlocks(chars: List<Ch>, words: List<List<Int>>, roles: List<Role>, scales: Map<Role, Double>, maxBlocks: Int): List<Block> {
        val blocks = ArrayList<Block>()
        words.forEachIndexed { i, w ->
            val role = roles[i]; val word = BlockWord(w, scales.getValue(role), isCjkWord(chars, w))
            val last = blocks.lastOrNull()
            if (role == Role.PARTICLE && last != null && last.role != Role.HERO && last.role != Role.SEMI) blocks[blocks.size - 1] = last.copy(words = last.words + word)
            else blocks += Block(role, listOf(word))
        }
        fun len(b: Block) = b.words.sumOf { it.idx.size }
        fun strong(b: Block) = b.role == Role.HERO || b.role == Role.SEMI
        while (blocks.size > maxBlocks) {
            var bi = -1; var bl = Int.MAX_VALUE
            for (i in 0 until blocks.size - 1) {
                if (strong(blocks[i]) || strong(blocks[i + 1])) continue
                val l = len(blocks[i]) + len(blocks[i + 1])
                if (l < bl) { bl = l; bi = i }
            }
            if (bi < 0) break
            blocks[bi] = Block(Role.SUPPORT, blocks[bi].words + blocks[bi + 1].words)
            blocks.removeAt(bi + 1)
        }
        return blocks
    }

    data class MItem(val k: Int, val off: Double, val s: Double, val rot: Double, val punct: Boolean)
    data class Measured(val vertical: Boolean, val items: List<MItem>, val w: Double, val h: Double)
    data class Placed(val k: Int, val x: Double, val y: Double, val s: Double, val rot: Double)

    private fun gapOf(a: BlockWord, b: BlockWord) = if (a.cjk && b.cjk) 0.04 else 0.24

    fun measure(block: Block, chars: List<Ch>, vertical: Boolean): Measured {
        val items = ArrayList<MItem>()
        var pen = 0.0; var thick = 0.0
        block.words.forEachIndexed { wi, w ->
            if (wi > 0) pen += gapOf(block.words[wi - 1], w) * (if (vertical && w.cjk) 0.0 else 1.0)
            for (k in w.idx) {
                val ch = chars[k].ch
                val vc = vertical && w.cjk
                val a = if (vc) (if (PUNCT.matches(ch)) 0.55 else 1.02) else advanceOf(ch)
                val rot = if (vertical && (!w.cjk || TURN_IN_VERTICAL.matches(ch))) PI / 2 else 0.0
                val s = w.scale
                items += MItem(k, pen + a * s / 2, s, rot, vc && PUNCT.matches(ch))
                pen += a * s
                thick = max(thick, s)
            }
        }
        return if (!vertical) Measured(false, items, pen, thick) else Measured(true, items, thick, pen)
    }

    fun emit(m: Measured, cx: Double, cy: Double, out: MutableList<Placed>) {
        for (it in m.items) {
            if (!m.vertical) out += Placed(it.k, cx - m.w / 2 + it.off, cy + m.h / 2 - it.s / 2, it.s, 0.0)
            else out += Placed(it.k, cx + if (it.punct) it.s * 0.3 else 0.0, cy - m.h / 2 + it.off - if (it.punct) it.s * 0.2 else 0.0, it.s, it.rot)
        }
    }

    private fun turned(m: Measured) = if (m.items.any { it.rot != 0.0 }) 1 else 0

    // ---------- nebbiaCompositions ----------
    class Ctx(val blocks: List<Block>, val h: Int, val chars: List<Ch>) {
        fun m(i: Int, vertical: Boolean) = measure(blocks[i], chars, vertical)
    }
    data class Result(val items: List<Placed>, val vRot: Int)

    private fun flowRows(ctx: Ctx, rows: List<List<Int>>, shifts: DoubleArray): Result {
        val out = ArrayList<Placed>()
        val rowMs = rows.map { r -> r.map { ctx.m(it, false) } }
        val rowW = rowMs.map { ms -> ms.sumOf { it.w } + FLOW_GAP * (ms.size - 1) }
        val widest = rowW.max()
        var y = 0.0
        rowMs.forEachIndexed { ri, ms ->
            val rh = ms.maxOf { it.h }
            var x = -rowW[ri] / 2 + shifts[ri] * widest
            ms.forEach { m -> emit(m, x + m.w / 2, y + rh - m.h / 2, out); x += m.w + FLOW_GAP }
            y += rh + STACK_GAP
        }
        return Result(out, 0)
    }

    val COMPOSITIONS: LinkedHashMap<String, (Ctx) -> Result?> = linkedMapOf(
        "ribbon" to { ctx -> flowRows(ctx, listOf(ctx.blocks.indices.toList()), doubleArrayOf(0.0)) },
        "lines2" to lines2@{ ctx ->
            val n = ctx.blocks.size
            if (n < 2) return@lines2 null
            val ms = ctx.blocks.indices.map { ctx.m(it, false) }; val total = ms.sumOf { it.w }
            var acc = 0.0; var cut = 1; var best = Double.MAX_VALUE
            ms.forEachIndexed { i, m -> acc += m.w; if (i < n - 1 && abs(acc - total / 2) < best) { best = abs(acc - total / 2); cut = i + 1 } }
            flowRows(ctx, listOf((0 until cut).toList(), (cut until n).toList()), doubleArrayOf(-0.12, 0.12))
        },
        "lines3" to lines3@{ ctx ->
            val n = ctx.blocks.size
            if (n < 3) return@lines3 null
            val ms = ctx.blocks.indices.map { ctx.m(it, false) }; val total = ms.sumOf { it.w }
            val rows = arrayListOf(ArrayList<Int>())
            var acc = 0.0
            ms.forEachIndexed { i, m ->
                if (rows.last().isNotEmpty() && rows.size < 3 && (acc + m.w / 2 > total / 3 * rows.size || n - i <= 3 - rows.size)) rows += ArrayList<Int>()
                rows.last() += i
                acc += m.w
            }
            if (rows.size == 3) flowRows(ctx, rows, doubleArrayOf(-0.14, 0.1, -0.04)) else null
        },
        "cross" to cross@{ ctx ->
            val n = ctx.blocks.size; val h = ctx.h
            if (n < 3 || n > 7) return@cross null
            val top = h / 2; val right = ceil((n - 1 - h) / 2.0).toInt()
            if (top == 0 && h + right + 1 >= n) return@cross null
            val out = ArrayList<Placed>(); val hm = ctx.m(h, false)
            emit(hm, 0.0, 0.0, out)
            var vRot = 0
            var x = -hm.w / 2 - STACK_GAP
            for (i in h - 1 downTo top) { val m = ctx.m(i, false); emit(m, x - m.w / 2, (hm.h - m.h) / 2, out); x -= m.w + FLOW_GAP }
            var y = -hm.h / 2 - STACK_GAP
            for (i in top - 1 downTo 0) { val m = ctx.m(i, true); vRot += turned(m); emit(m, 0.0, y - m.h / 2, out); y -= m.h + STACK_GAP }
            x = hm.w / 2 + STACK_GAP
            for (i in h + 1..h + right) { if (i >= n) break; val m = ctx.m(i, false); emit(m, x + m.w / 2, (hm.h - m.h) / 2, out); x += m.w + FLOW_GAP }
            y = hm.h / 2 + STACK_GAP
            for (i in h + right + 1 until n) { val m = ctx.m(i, true); vRot += turned(m); emit(m, 0.0, y + m.h / 2, out); y += m.h + STACK_GAP }
            Result(out, vRot)
        },
        "pillar" to pillar@{ ctx ->
            val n = ctx.blocks.size; val h = ctx.h
            if (n < 2 || n > 6) return@pillar null
            val out = ArrayList<Placed>(); val hm = ctx.m(h, true)
            var vRot = turned(hm)
            emit(hm, 0.0, 0.0, out)
            val top = -hm.h / 2 + 0.15
            fun side(from: Int, to: Int, dirX: Int) {
                val cols = arrayListOf(ArrayList<Measured>())
                var y = top
                for (i in from until to) {
                    val m = ctx.m(i, true)
                    vRot += turned(m)
                    if (y + m.h > hm.h / 2 + 0.6 && cols.last().isNotEmpty()) { cols += ArrayList<Measured>(); y = top }
                    cols.last() += m
                    y += m.h + STACK_GAP
                }
                var colX = dirX * (hm.w / 2 + FLOW_GAP)
                for (col in if (dirX > 0) cols else cols.reversed()) {
                    y = top
                    for (m in col) { emit(m, colX + dirX * m.w / 2, y + m.h / 2, out); y += m.h + STACK_GAP }
                    colX += dirX * ((col.maxOfOrNull { it.w } ?: 0.0).coerceAtLeast(0.0) + FLOW_GAP)
                }
            }
            side(0, h, -1)
            side(h + 1, n, 1)
            Result(out, vRot)
        },
        "header" to header@{ ctx ->
            val n = ctx.blocks.size; val h = ctx.h
            if (n < 3 || n - 1 - h < 2) return@header null
            val out = ArrayList<Placed>(); val hm = ctx.m(h, false)
            emit(hm, 0.0, 0.0, out)
            if (h > 0) {
                val ms = (0 until h).map { ctx.m(it, false) }
                val rowW = ms.sumOf { it.w } + FLOW_GAP * (h - 1); val rowH = ms.maxOf { it.h }
                var x = -rowW / 2
                val y = -hm.h / 2 - STACK_GAP - rowH / 2
                ms.forEach { m -> emit(m, x + m.w / 2, y + (rowH - m.h) / 2, out); x += m.w + FLOW_GAP }
            }
            var y = hm.h / 2 + STACK_GAP
            val l = -hm.w * 0.22 - FLOW_GAP / 2; val r = hm.w * 0.22 + FLOW_GAP / 2
            var i = h + 1
            while (i < n) {
                val a = ctx.m(i, false); val b = if (i + 1 < n) ctx.m(i + 1, false) else null
                val rh = max(a.h, b?.h ?: 0.0)
                emit(a, l - a.w / 2, y + rh - a.h / 2, out)
                if (b != null) emit(b, r + b.w / 2, y + rh - b.h / 2, out)
                y += rh + STACK_GAP
                i += 2
            }
            Result(out, 0)
        },
        "badge" to badge@{ ctx ->
            val n = ctx.blocks.size
            if (n < 3 || n > 7 || ctx.h != 0) return@badge null
            val out = ArrayList<Placed>(); val hm = ctx.m(0, true)
            val ms = (1 until n).map { ctx.m(it, false) }
            val colW = max(ms.maxOf { it.w }, hm.h * 0.9)
            emit(hm, hm.w / 2, hm.h / 2, out)
            var x = hm.w + FLOW_GAP; var y = 0.0; var rowH = 0.0
            fun left() = if (y < hm.h) hm.w + FLOW_GAP else 0.0
            ms.forEach { m ->
                if (x > left() && x + m.w > hm.w + FLOW_GAP + colW) { y += rowH + STACK_GAP; x = left(); rowH = 0.0 }
                emit(m, x + m.w / 2, y + m.h / 2, out)
                x += m.w + FLOW_GAP
                rowH = max(rowH, m.h)
            }
            Result(out, turned(hm))
        },
        "columns" to columns@{ ctx ->
            val n = ctx.blocks.size
            if (n < 2 || n > 5 || ctx.blocks.any { b -> b.words.any { !it.cjk } }) return@columns null
            val out = ArrayList<Placed>()
            var x = 0.0
            ctx.blocks.indices.forEach { i -> val m = ctx.m(i, true); emit(m, x + m.w / 2, m.h / 2 + (i % 2) * 0.5, out); x += m.w + STACK_GAP }
            Result(out, 0)
        },
        "orbit" to orbit@{ ctx ->
            val n = ctx.blocks.size; val h = ctx.h
            if (n < 4 || n > 7) return@orbit null
            val out = ArrayList<Placed>(); val hm = ctx.m(h, false)
            emit(hm, 0.0, 0.0, out)
            val rects = arrayListOf(doubleArrayOf(-hm.w / 2, hm.w / 2, -hm.h / 2, hm.h / 2))
            fun sep(a: DoubleArray, b: DoubleArray) = maxOf(a[0] - b[1], b[0] - a[1], a[2] - b[3], b[2] - a[3])
            var ang = -PI * 0.75; var vRot = 0
            val base = hypot(hm.w, hm.h) / 2 + STACK_GAP
            for (i in 0 until n) {
                if (i == h) continue
                var r = base; var a = ang; var rect: DoubleArray? = null; var m = ctx.m(i, false)
                var tries = 0
                while (tries < 600 && rect == null) {
                    m = ctx.m(i, abs(cos(a)) > 0.75)
                    val cx = cos(a) * r * 1.25; val cy = sin(a) * r * 0.8
                    val c = doubleArrayOf(cx - m.w / 2, cx + m.w / 2, cy - m.h / 2, cy + m.h / 2)
                    if (rects.all { sep(it, c) >= FLOW_GAP }) rect = c
                    else { a += 0.08; if (tries % 80 == 79) r += 0.5 }
                    tries++
                }
                if (rect == null) return@orbit null
                rects += rect
                vRot += turned(m)
                emit(m, (rect[0] + rect[1]) / 2, (rect[2] + rect[3]) / 2, out)
                ang = a + 0.5
            }
            Result(out, vRot)
        },
    )
    val BIAS = mapOf("ribbon" to 12, "lines2" to 10, "lines3" to 14, "cross" to 0, "pillar" to 1, "header" to 1, "badge" to 4, "columns" to 2, "orbit" to 4)
    val SIMPLE = setOf("ribbon", "lines2", "lines3")

    // ---------- nebbiaLayout ----------
    data class Pos(val x: Double, val y: Double, val s: Double, val rot: Double)

    /** mulberry32 风格的可复现随机数（Folia createVisualizerRng 同族）。 */
    class Rng(seed: Int) {
        private var a = seed
        fun next(): Double {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
        }
    }

    private class Box(val x0: Double, val x1: Double, val y0: Double, val y1: Double)
    private class Cand(val score: Double, val name: String, val items: List<Placed>, val bb: Box, val s: Double, val cx: Double, val cy: Double, val box: Box)
    private fun bbox(items: List<Placed>): Box {
        var x0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        for (it in items) { x0 = min(x0, it.x - it.s / 2); x1 = max(x1, it.x + it.s / 2); y0 = min(y0, it.y - it.s / 2); y1 = max(y1, it.y + it.s / 2) }
        return Box(x0, x1, y0, y1)
    }
    private fun overlap(a: Box, b: Box) = max(0.0, min(a.x1, b.x1) - max(a.x0, b.x0)) * max(0.0, min(a.y1, b.y1) - max(a.y0, b.y0))
    private const val LINGER_WEIGHT = 0.03

    /**
     * 排整首：每句一组，与 chars 一一对应（空格也有位置，但不画）。[minPx] 为 Folia 的 26 CSS px 换算到像素。
     * [visibleUntil] 第 i 句擦痕完全被雾吞掉的时刻，[fogAt] 开始起雾的时刻。
     */
    fun layoutSong(
        lines: List<Src>, w: Double, h: Double, fontScale: Double, minPx: Double, jitter: Double,
        visibleUntil: (Int) -> Double, fogAt: (Int) -> Double,
        lingerUntil: ((Int) -> Double)? = null,
    ): List<List<Pos>> {
        val mx = w * 0.05; val my = h * 0.07
        val sMin = max(minPx, h * 0.065) * fontScale; val sMax = h * 0.14 * fontScale
        data class P(val box: Box, val until: Double, val fogAt: Double, val linger: Double)
        val placed = ArrayList<P>()
        val recent = ArrayList<String>()
        var prev: Cand? = null
        return lines.mapIndexed { li, line ->
            val chars = line.chars
            val rnd = Rng(chars.joinToString("") { it.ch }.hashCode() + li * 7919)
            val visible = chars.count { it.ch.isNotBlank() }
            val fast = visible > 0 && (line.end - line.t) / visible < 0.22
            val words = mergeWords(chars, line.words)
            if (words.isEmpty()) return@mapIndexed chars.map { Pos(w / 2, h / 2, sMin, 0.0) }
            val roles = assignRoles(chars, words)
            val rs = if (fast) ROLE_SCALE_FAST else ROLE_SCALE
            // 这句出现时还在屏上的句子（含屏上空着时多留的）；已经开始起雾的按剩下的清晰度打折，多留的只占一点权重
            val busy = placed.filter { it.linger > line.t - 0.3 }.map { p ->
                p.box to (
                    if (p.until <= line.t - 0.3) LINGER_WEIGHT
                    else if (line.t <= p.fogAt) 1.0
                    else 0.6 + 0.4 * ((p.until - line.t) / max(1e-3, p.until - p.fogAt)).coerceIn(0.0, 1.0)
                    )
            }
            var best: Cand? = null
            for (maxBlocks in intArrayOf(5, 7)) {
                val blocks = makeBlocks(chars, words, roles, rs, maxBlocks)
                val ctx = Ctx(blocks, max(0, blocks.indexOfFirst { it.role == Role.HERO }), chars)
                for ((name, comp) in COMPOSITIONS) {
                    val res = comp(ctx) ?: continue
                    if (res.items.isEmpty()) continue
                    val bb = bbox(res.items)
                    val bw0 = bb.x1 - bb.x0; val bh0 = bb.y1 - bb.y0
                    val maxH = h * (if (name == "ribbon") 0.28 else 0.5); val maxW = (w - mx * 2) * (if (name in SIMPLE) 0.94 else 0.68)
                    val sFit = minOf(maxW / bw0, maxH / bh0, sMax)
                    if (sFit < sMin * 0.75) continue
                    val sizes = listOf(sFit, sFit * 0.8, sFit * 0.64, sFit * 0.5).filterIndexed { i, v -> i == 0 || v >= sMin * 0.75 }
                    for (s in sizes) {
                        val bw = bw0 * s; val bh = bh0 * s
                        for (fx in doubleArrayOf(0.5, 0.3, 0.7)) for (fy in doubleArrayOf(0.25, 0.42, 0.58, 0.75)) {
                            val cx = (fx * w).coerceIn(mx + bw / 2, max(mx + bw / 2, w - mx - bw / 2))
                            val cy = (fy * h).coerceIn(my + bh / 2, max(my + bh / 2, h - my - bh / 2))
                            val box = Box(cx - bw / 2, cx + bw / 2, cy - bh / 2, cy + bh / 2)
                            val pad = s * 0.3
                            val padBox = Box(box.x0 - pad, box.x1 + pad, box.y0 - pad, box.y1 + pad)
                            var score = 0.0
                            for ((b, wt) in busy) score += overlap(padBox, b) / (bw * bh) * 1000 * wt
                            score -= s / h * 100
                            if (s < sMin) score += 40 + (sMin - s) / sMin * 200
                            score += BIAS.getValue(name)
                            score += res.vRot * (if (fast) 10 else 4)
                            if (fast && name !in SIMPLE) score += 6
                            if (maxBlocks == 7 && blocks.size <= 5) score += 100
                            val rep = recent.lastIndexOf(name)
                            if (rep >= 0) score += intArrayOf(18, 9, 4)[recent.size - 1 - rep]
                            prev?.let { if (abs(it.cx - cx) < w * 0.08 && abs(it.cy - cy) < h * 0.08) score += 8 }
                            score += abs(cy - h * 0.5) / h * 8
                            score += rnd.next() * 8
                            if (best == null || score < best!!.score) best = Cand(score, name, res.items, bb, s, cx, cy, box)
                        }
                    }
                }
            }
            if (best == null) {
                val blocks = makeBlocks(chars, words, roles, ROLE_SCALE_FAST, 9)
                val ctx = Ctx(blocks, 0, chars)
                val res = COMPOSITIONS.getValue("lines3")(ctx) ?: COMPOSITIONS.getValue("lines2")(ctx) ?: COMPOSITIONS.getValue("ribbon")(ctx)!!
                val bb = bbox(res.items)
                val s = minOf(sMax, (w - mx * 2) / (bb.x1 - bb.x0), (h - my * 2) / (bb.y1 - bb.y0))
                val bw = (bb.x1 - bb.x0) * s; val bh = (bb.y1 - bb.y0) * s
                best = Cand(0.0, "lines3", res.items, bb, s, w / 2, h / 2, Box(w / 2 - bw / 2, w / 2 + bw / 2, h / 2 - bh / 2, h / 2 + bh / 2))
            }
            val c = best!!
            placed += P(c.box, visibleUntil(li), fogAt(li), max(visibleUntil(li), lingerUntil?.invoke(li) ?: 0.0))
            recent += c.name
            if (recent.size > 3) recent.removeAt(0)
            prev = c
            val tilt = (rnd.next() - 0.5) * 0.04 * jitter
            val byK = c.items.associateBy { it.k }
            var lastPos = Pos(c.cx, c.cy, c.s, 0.0)
            chars.indices.map { k ->
                val it = byK[k] ?: return@map lastPos.copy(rot = 0.0)
                val x = c.cx + (it.x - (c.bb.x0 + c.bb.x1) / 2) * c.s
                val y0 = c.cy + (it.y - (c.bb.y0 + c.bb.y1) / 2) * c.s
                val s = it.s * c.s * (1 + (rnd.next() - 0.5) * 0.08 * jitter)
                lastPos = Pos(
                    x + (rnd.next() - 0.5) * c.s * 0.04 * jitter,
                    y0 + (x - c.cx) * tilt + (rnd.next() - 0.5) * c.s * 0.05 * jitter,
                    s,
                    it.rot + (rnd.next() - 0.5) * 0.08 * jitter + tilt,
                )
                lastPos
            }
        }
    }
}
