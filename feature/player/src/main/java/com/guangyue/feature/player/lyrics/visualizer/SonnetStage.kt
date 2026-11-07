// 对应 Folia 文件：src/components/visualizer/sonnet/（商籁）——sonnetProgram.ts（整首编成可 seek 的 PV 时间线：
// 按时间空隙 / 段落元数据切段，段内每 ≤4 行、≤6 秒一个镜头，镜头版式从 7 种里按种子挑且不与上一个重复，
// 段落之间 fast-blur / mono-glitch / camera-pull 三种转场）、sonnetTypographyRoles.ts（主角词 = 可见字数×14 + 时长×18 最高者，
// 900 / 700 字重）、sonnetTypographyLayout.ts（7 种版式的主角 / 配角字号倍数与竖排规则）、sonnetGuides.ts（字到之前的定位记号）、
// sonnetFrameDecor.ts（约 40% 词块套不闭合的开口框 + 角饰）、sonnetBackgroundDecor.ts（种子决定的漂浮小形）、
// sonnetStaffView.ts（La Folia 乐句五线谱）、DEFAULT_SONNET_TUNING（outerFrameMode full、showGiantDecorativeText）。
//
// P6「商籁」手机版：不做 Pixi 纹理池 / 镜头 MG 图库 / 后期滤镜。保留：编排（段落→镜头→版式→转场）、
// 主角词放大与竖排、词按唱到的时刻逐个打进画面、定位记号、开口框、巨字底纹、漂浮小形、五线谱与外框。
// 竖屏版面在让开顶栏与底部胶囊的区域里排；镜头机位做成轻微推近与倾斜。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ───────────────────────────── 编排（纯函数，可单测） ─────────────────────────────

internal enum class SonnetShotKind { EDITORIAL_COLUMN, TYPE_IMPACT, FRAGMENT_COLLAGE, TRACKING_RIBBON, MASK_REVEAL, POSTER_BLOCKS, QUIET_TABLEAU }
internal enum class SonnetTransition { FAST_BLUR, MONO_GLITCH, CAMERA_PULL }
internal enum class SonnetParagraphKind { BREATH, VERSE, LIFT, CHORUS, BREAK, OUTRO }

/** 一个语义词块（Folia SonnetSemanticSegment）：文字、在句里的时刻、是否算「词」（标点不算）。 */
internal data class SonnetSegment(val text: String, val start: Double, val end: Double, val wordLike: Boolean) {
    val visibleLength: Int get() = text.count { !it.isWhitespace() }
    val cjk: Boolean get() = text.isNotEmpty() && text.all { sonnetIsCjk(it.code) || it.isWhitespace() }
}

internal data class SonnetShot(
    val index: Int,
    val paragraph: Int,
    val kind: SonnetShotKind,
    val start: Double,
    val end: Double,
    val lines: List<Int>,
    /** 机位：x / y 为宽高比例偏移，zoom 推近倍数，rot 弧度（Folia buildShots 的 camera）。 */
    val camX: Float,
    val camY: Float,
    val zoom: Float,
    val rot: Float,
    /** 进入本镜头时的转场（段落交界才有，镜头之间用短切）。 */
    val transitionIn: SonnetTransition?,
)

internal data class SonnetProgram(val segments: List<List<SonnetSegment>>, val shots: List<SonnetShot>, val paragraphKinds: List<SonnetParagraphKind>)

internal fun sonnetIsCjk(cp: Int): Boolean = Character.isIdeographic(cp) || Character.UnicodeScript.of(cp).let {
    it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
}

/** Folia hashSonnetSeed 的替身：FNV-1a 32 位，同一首歌每次编排一样。 */
internal fun sonnetHash(s: String): Int {
    var h = 0x811c9dc5.toInt()
    s.forEach { h = (h xor it.code) * 0x01000193 }
    return h
}

/**
 * 一句拆成词块：拉丁按空格分词；中日韩先用系统分词（Android 上是 ICU 词典分词），
 * 单字碎块两两并成一块（JVM 的分词器只会逐字切），最长 4 字。每块的时刻取首字开唱、末字唱完。
 */
internal fun sonnetSegments(line: Line): List<SonnetSegment> {
    val text = line.fullText
    if (text.isBlank()) return emptyList()
    // 每个字符的开唱 / 唱完时刻：有逐词时间就在词内均分，没有就整句均分。
    val starts = DoubleArray(text.length) { line.startTime }
    val ends = DoubleArray(text.length) { line.endTime }
    if (line.words.isNotEmpty()) {
        var cursor = 0
        line.words.forEach { w ->
            val at = text.indexOf(w.text, cursor).takeIf { it >= 0 && w.text.isNotEmpty() } ?: return@forEach
            val n = w.text.length
            val step = (w.endTime - w.startTime).coerceAtLeast(0.0) / n
            for (k in 0 until n) { starts[at + k] = w.startTime + step * k; ends[at + k] = w.startTime + step * (k + 1) }
            // 词与词之间的空格跟随前一个词
            cursor = at + n
            var g = cursor
            while (g < text.length && text[g].isWhitespace()) { starts[g] = w.endTime; ends[g] = w.endTime; g++ }
        }
    } else {
        val step = (line.endTime - line.startTime).coerceAtLeast(0.0) / text.length
        for (k in text.indices) { starts[k] = line.startTime + step * k; ends[k] = line.startTime + step * (k + 1) }
    }
    // 粗分：空白切开；中日韩连续段再交给分词器。
    val raw = ArrayList<IntRange>()
    var i = 0
    while (i < text.length) {
        if (text[i].isWhitespace()) { i++; continue }
        val cjk = sonnetIsCjk(text.codePointAt(i))
        var j = i
        while (j < text.length && !text[j].isWhitespace() && sonnetIsCjk(text.codePointAt(j)) == cjk) j += Character.charCount(text.codePointAt(j))
        if (!cjk) raw += i until j
        else {
            val bi = java.text.BreakIterator.getWordInstance(java.util.Locale.CHINESE)
            val run = text.substring(i, j)
            bi.setText(run)
            var a = bi.first()
            var b = bi.next()
            val pieces = ArrayList<IntRange>()
            while (b != java.text.BreakIterator.DONE) { pieces += (i + a) until (i + b); a = b; b = bi.next() }
            // 单字碎块两两并起来；超过 4 字的长词按 4 切。
            var k = 0
            while (k < pieces.size) {
                val p = pieces[k]
                if (p.count() == 1 && k + 1 < pieces.size && pieces[k + 1].count() == 1) { raw += p.first..pieces[k + 1].last; k += 2 }
                else if (p.count() > 4) { var s = p.first; while (s <= p.last) { raw += s..min(p.last, s + 3); s += 4 }; k++ }
                else { raw += p; k++ }
            }
        }
        i = j
    }
    return raw.map { r ->
        val t = text.substring(r.first, r.last + 1)
        SonnetSegment(t, starts[r.first], max(starts[r.first], ends[r.last]), t.any { it.isLetterOrDigit() })
    }
}

/** 主角词得分（Folia scoreSonnetHeroSegment）：可见字数（封顶 8）×14 + 时长（封顶 2.5 秒）×18。 */
internal fun sonnetHeroScore(s: SonnetSegment): Double = min(s.visibleLength, 8) * 14.0 + (s.end - s.start).coerceIn(0.0, 2.5) * 18.0

internal fun sonnetHeroIndex(segs: List<SonnetSegment>): Int {
    var best = segs.indexOfFirst { it.wordLike }.coerceAtLeast(0)
    var score = Double.NEGATIVE_INFINITY
    segs.forEachIndexed { k, s -> if (s.wordLike && s.visibleLength > 0 && sonnetHeroScore(s) > score) { score = sonnetHeroScore(s); best = k } }
    return best
}

private fun <T> chooseWithoutRepeat(choices: List<T>, seed: String, previous: T?): T {
    val start = Math.floorMod(sonnetHash(seed), choices.size)
    for (o in choices.indices) { val c = choices[(start + o) % choices.size]; if (c != previous) return c }
    return choices[start]
}

/** 整首编成镜头表（Folia compileSonnetProgram 的手机取值：竖屏一镜最多 3 行）。 */
internal fun compileSonnet(lines: List<Line>, seed: String = "sonnet"): SonnetProgram {
    val segs = lines.map { sonnetSegments(it) }
    val renderEnd = lines.mapIndexed { k, l -> max(l.startTime, min(l.endTime, lines.getOrNull(k + 1)?.startTime ?: Double.POSITIVE_INFINITY)) }
    val gaps = (1 until lines.size).map { lines[it].startTime - min(renderEnd[it - 1], lines[it].startTime) }.filter { it > 0 }.sorted()
    val median = if (gaps.isEmpty()) 0.5 else if (gaps.size % 2 == 0) (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2 else gaps[gaps.size / 2]
    val threshold = (median * 2.5).coerceIn(1.25, 3.5)
    // 1) 切段
    val paragraphs = ArrayList<MutableList<Int>>()
    lines.indices.forEach { k ->
        val prev = lines.getOrNull(k - 1)
        val meta = prev != null && ((prev.blockIndex != null && lines[k].blockIndex != null && prev.blockIndex != lines[k].blockIndex) ||
            (prev.songPart != null && lines[k].songPart != null && prev.songPart != lines[k].songPart))
        val gap = prev != null && lines[k].startTime - renderEnd[k - 1] >= threshold
        val tooLong = paragraphs.lastOrNull()?.let { it.size >= 6 || renderEnd[k] - lines[it.first()].startTime > 18 } ?: false
        if (paragraphs.isEmpty() || meta || gap || tooLong) paragraphs.add(mutableListOf(k)) else paragraphs.last() += k
    }
    // 2) 段落分类 + 段内分镜
    val kinds = paragraphs.mapIndexed { pi, p ->
        val part = p.joinToString(" ") { lines[it].songPart.orEmpty() }
        val dur = renderEnd[p.last()] - lines[p.first()].startTime
        val words = p.sumOf { li -> segs[li].count { it.wordLike } }
        val punct = p.sumOf { li -> lines[li].fullText.count { it in "!?！？…" } }
        when {
            p.any { lines[it].isChorus } || Regex("chorus|副歌", RegexOption.IGNORE_CASE).containsMatchIn(part) -> SonnetParagraphKind.CHORUS
            Regex("bridge|break|間奏|间奏", RegexOption.IGNORE_CASE).containsMatchIn(part) -> SonnetParagraphKind.BREAK
            pi == paragraphs.size - 1 -> SonnetParagraphKind.OUTRO
            dur <= 3.5 || words <= 3 -> SonnetParagraphKind.BREATH
            punct >= 2 || words / max(dur, 1.0) > 2.5 -> SonnetParagraphKind.LIFT
            else -> SonnetParagraphKind.VERSE
        }
    }
    val shots = ArrayList<SonnetShot>()
    var lastKind: SonnetShotKind? = null
    var lastTransition: SonnetTransition? = null
    val allKinds = SonnetShotKind.values().toList()
    paragraphs.forEachIndexed { pi, p ->
        val groups = ArrayList<MutableList<Int>>()
        p.forEach { li ->
            val g = groups.lastOrNull()
            if (g != null && g.size < 3 && renderEnd[li] - lines[g.first()].startTime <= 6.0) g += li else groups.add(mutableListOf(li))
        }
        groups.forEachIndexed { si, g ->
            val sig = g.joinToString("|") { lines[it].fullText }
            var kind = chooseWithoutRepeat(allKinds, "$seed:$pi:$si:$sig", lastKind)
            val wc = g.sumOf { li -> segs[li].count { it.wordLike } }
            if (kinds[pi] == SonnetParagraphKind.BREATH && si == 0 && wc <= 2) kind = SonnetShotKind.QUIET_TABLEAU
            if (kinds[pi] == SonnetParagraphKind.CHORUS && kind == SonnetShotKind.QUIET_TABLEAU) kind = SonnetShotKind.TYPE_IMPACT
            if (kind == lastKind) kind = allKinds[(kind.ordinal + 1) % allKinds.size]
            lastKind = kind
            val r = sonnetHash("$seed:$pi:$si:camera")
            val zr = ((r ushr 16) and 255) / 255f
            val zoomBase = when (kind) { SonnetShotKind.POSTER_BLOCKS -> 1.02f; SonnetShotKind.QUIET_TABLEAU -> 1.12f; else -> 1.22f }
            val zoomSpan = when (kind) { SonnetShotKind.POSTER_BLOCKS -> 0.16f; SonnetShotKind.QUIET_TABLEAU -> 0.2f; else -> 0.26f }
            val transition = if (si == 0 && pi > 0) chooseWithoutRepeat(SonnetTransition.values().toList(), "$seed:${pi - 1}:transition", lastTransition).also { lastTransition = it } else null
            shots += SonnetShot(
                index = shots.size,
                paragraph = pi,
                kind = kind,
                start = lines[g.first()].startTime,
                end = renderEnd[g.last()],
                lines = g.toList(),
                camX = ((r and 255) / 255f - 0.5f) * 0.18f,
                camY = (((r ushr 8) and 255) / 255f - 0.5f) * 0.14f,
                zoom = zoomBase + zr * zoomSpan,
                rot = (((r ushr 24) and 255) / 255f - 0.5f) * 0.08f,
                transitionIn = transition,
            )
        }
    }
    return SonnetProgram(segs, shots, kinds)
}

/** [t] 时在画的镜头：最后一个已开始的镜头（开头之前用第一个）。 */
internal fun sonnetShotAt(program: SonnetProgram, t: Double): Int {
    for (k in program.shots.indices.reversed()) if (t >= program.shots[k].start) return k
    return 0
}

/** 转场窗口：下一镜头开始前 [SONNET_TRANSITION_S] 秒内给出 0..1 的退场进度，进镜头后同样时长给出入场进度。 */
internal const val SONNET_TRANSITION_S = 0.28

internal fun sonnetEaseExpoOut(x: Float): Float = if (x >= 1f) 1f else 1f - 2f.pow(-10f * x.coerceAtLeast(0f))

// ───────────────────────────── 版面（每镜头一次） ─────────────────────────────

private val OnePx = androidx.compose.ui.unit.Density(1f, 1f)

private enum class SonnetRole { HERO, SEMI, SUPPORT }

/** 一个排好的词块。竖排时 [glyphs] 逐字自上而下；[inverted] 为实心底反白（海报块 / 色带）。 */
private class SonnetBox(
    val seg: SonnetSegment,
    val role: SonnetRole,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val size: Float,
    val vertical: Boolean,
    val rot: Float,
    val layout: TextLayoutResult?,
    val glyphs: List<TextLayoutResult>,
    val inverted: Boolean,
    val frame: Int,
    val block: FloatArray? = null,
)

private class SonnetLayout(val boxes: List<SonnetBox>, val giant: TextLayoutResult?, val giantX: Float, val giantY: Float, val ribbon: FloatArray? = null)

private class SonnetRng(seed: Int) {
    private var s = (seed.toLong() and 0xffffffffL) xor 0x9E3779B9L
    fun next(): Float { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return (s % 100000L) / 100000f }
}

private class SonnetTypesetter(val measurer: TextMeasurer, val heavy: TextStyle, val bold: TextStyle) {
    fun style(role: SonnetRole) = if (role == SonnetRole.SUPPORT) bold else heavy
    fun measure(text: String, role: SonnetRole, size: Float): TextLayoutResult = measurer.measure(text, style(role).copy(fontSize = size.sp), density = OnePx)
    fun width1(text: String, role: SonnetRole): Float = measure(text, role, 100f).size.width / 100f

    fun box(seg: SonnetSegment, role: SonnetRole, x: Float, y: Float, size: Float, vertical: Boolean, rot: Float = 0f, inverted: Boolean = false, frame: Int = -1, block: FloatArray? = null): SonnetBox {
        return if (vertical) {
            val chars = seg.text.filterNot { it.isWhitespace() }.map { it.toString() }
            val glyphs = chars.map { measure(it, role, size) }
            SonnetBox(seg, role, x, y, size * 1.02f, size * 0.95f * chars.size, size, true, rot, null, glyphs, inverted, frame, block)
        } else {
            val l = measure(seg.text, role, size)
            SonnetBox(seg, role, x, y, l.size.width.toFloat(), l.size.height.toFloat(), size, false, rot, l, emptyList(), inverted, frame, block)
        }
    }
}

/** 一串词块在 [maxW] 宽里折行排开（左 / 中 / 右对齐），返回词块与占用高度。 */
private fun SonnetTypesetter.flow(
    segs: List<SonnetSegment>, roles: List<SonnetRole>, sizeOf: (SonnetRole) -> Float,
    left: Float, top: Float, maxW: Float, align: Int, frames: (SonnetSegment) -> Int,
): Pair<List<SonnetBox>, Float> {
    if (segs.isEmpty()) return emptyList<SonnetBox>() to 0f
    val ws = segs.mapIndexed { k, s -> width1(s.text, roles[k]) * sizeOf(roles[k]) }
    val gapOf = { k: Int -> if (segs[k].cjk && segs.getOrNull(k - 1)?.cjk == true) sizeOf(roles[k]) * 0.12f else sizeOf(roles[k]) * 0.32f }
    val rows = ArrayList<MutableList<Int>>()
    var cw = 0f
    segs.indices.forEach { k ->
        val add = if (rows.isEmpty() || rows.last().isEmpty()) ws[k] else ws[k] + gapOf(k)
        if (rows.isEmpty() || (rows.last().isNotEmpty() && cw + add > maxW)) { rows.add(mutableListOf()); cw = 0f }
        cw += if (rows.last().isEmpty()) ws[k] else ws[k] + gapOf(k)
        rows.last() += k
    }
    val out = ArrayList<SonnetBox>()
    var y = top
    rows.forEach { row ->
        val rowH = row.maxOf { sizeOf(roles[it]) } * 1.22f
        val rowW = row.sumOf { ws[it].toDouble() }.toFloat() + row.drop(1).sumOf { gapOf(it).toDouble() }.toFloat()
        var x = when (align) { 1 -> left + (maxW - rowW) / 2f; 2 -> left + maxW - rowW; else -> left }
        row.forEachIndexed { n, k ->
            if (n > 0) x += gapOf(k)
            val s = sizeOf(roles[k])
            out += box(segs[k], roles[k], x, y + (rowH - s * 1.22f) * 0.8f, s, false, frame = frames(segs[k]))
            x += ws[k]
        }
        y += rowH
    }
    return out to (y - top)
}

/** 开口框（Folia SONNET_FRAME_DECOR_PROBABILITY 0.4，4 种）：按词块种子决定，-1 为不加框。 */
private fun frameSpec(seg: SonnetSegment): Int {
    val h = sonnetHash("frame:" + seg.text + seg.start)
    return if (Math.floorMod(h, 100) < 40) Math.floorMod(h ushr 8, 4) else -1
}

private fun layoutShot(
    ts: SonnetTypesetter, program: SonnetProgram, shot: SonnetShot,
    left: Float, top: Float, right: Float, bottom: Float, base: Float,
): SonnetLayout {
    val segs = shot.lines.flatMap { program.segments[it] }
    if (segs.isEmpty()) return SonnetLayout(emptyList(), null, 0f, 0f)
    val hero = sonnetHeroIndex(segs)
    // 次主角：其它行各自的主角（字数 ≥2，离主角 ≥2 块）。
    val semis = HashSet<Int>()
    var off = 0
    shot.lines.forEach { li ->
        val ls = program.segments[li]
        if (ls.isNotEmpty()) {
            val lh = off + sonnetHeroIndex(ls)
            if (lh != hero && abs(lh - hero) >= 2 && segs[lh].visibleLength >= 2) semis += lh
        }
        off += ls.size
    }
    val roles = segs.indices.map { if (it == hero) SonnetRole.HERO else if (it in semis) SonnetRole.SEMI else SonnetRole.SUPPORT }
    val (heroScale, supportScale) = when (shot.kind) {
        SonnetShotKind.EDITORIAL_COLUMN -> 4.0f to 1.2f
        SonnetShotKind.TYPE_IMPACT -> 5.5f to 1.5f
        SonnetShotKind.FRAGMENT_COLLAGE -> 3.2f to 1.35f
        SonnetShotKind.TRACKING_RIBBON -> 3.5f to 1.5f
        SonnetShotKind.MASK_REVEAL -> 4.5f to 1.6f
        SonnetShotKind.POSTER_BLOCKS -> 4.4f to 1.15f
        SonnetShotKind.QUIET_TABLEAU -> 3.0f to 1.15f
    }
    val aw = right - left
    val ah = bottom - top
    val heroSeg = segs[hero]
    val rnd = SonnetRng(sonnetHash(segs.joinToString("\u241f") { it.text }))
    val heroW1 = ts.width1(heroSeg.text, SonnetRole.HERO)
    val horizHero = min(base * heroScale, aw * 0.92f / max(heroW1, 0.01f))
    val semiSize = max(supportScale * 1.35f, heroScale * 0.5f) * base
    var support = base * supportScale
    val sizeOf = { r: SonnetRole -> when (r) { SonnetRole.HERO -> horizHero; SonnetRole.SEMI -> min(semiSize, horizHero * 0.8f); SonnetRole.SUPPORT -> support } }
    val heroN = heroSeg.text.count { !it.isWhitespace() }
    val canVertical = heroSeg.cjk && heroN in 1..6
    val boxes = ArrayList<SonnetBox>()
    var ribbon: FloatArray? = null
    val before = segs.indices.filter { it < hero }
    val after = segs.indices.filter { it > hero }
    val frames = { s: SonnetSegment -> frameSpec(s) }

    fun flowFit(idx: List<Int>, l: Float, t: Float, w: Float, maxH: Float, align: Int): Float {
        if (idx.isEmpty()) return 0f
        var s = support
        var res: Pair<List<SonnetBox>, Float>
        while (true) {
            val ss = s
            res = ts.flow(idx.map { segs[it] }, idx.map { roles[it] }, { r -> if (r == SonnetRole.SEMI) min(semiSize, ss * 1.6f) else ss }, l, t, w, align, frames)
            if (res.second <= maxH || s < base * 0.7f) break
            s *= 0.88f
        }
        boxes += res.first
        return res.second
    }

    when (shot.kind) {
        SonnetShotKind.TYPE_IMPACT -> {
            val hs = horizHero
            val hb = ts.box(heroSeg, SonnetRole.HERO, left + (aw - heroW1 * hs) / 2f, top + ah * 0.46f - hs * 0.6f, hs, false, rot = (rnd.next() - 0.5f) * 4f, frame = -1)
            boxes += hb
            val hBefore = flowFit(before, left, 0f, aw, ah * 0.3f, 1)
            // 上方词块整体挪到主角上沿之上
            val shift = hb.y - hBefore - hs * 0.15f
            for (k in boxes.indices) if (boxes[k] !== hb) boxes[k] = boxes[k].moved(0f, shift)
            flowFit(after, left, hb.y + hb.h + hs * 0.05f, aw, ah * 0.3f, 1)
        }
        SonnetShotKind.EDITORIAL_COLUMN, SonnetShotKind.MASK_REVEAL -> {
            val mirror = shot.kind == SonnetShotKind.MASK_REVEAL
            if (canVertical) {
                val vs = min(base * heroScale, min(ah * 0.78f / (heroN * 0.95f), aw * 0.36f))
                val px = if (mirror) right - vs * 1.2f else left
                boxes += ts.box(heroSeg, SonnetRole.HERO, px, top + ah * 0.1f, vs, true, frame = -1)
                val colL = if (mirror) left else left + vs * 1.25f
                val colW = aw - vs * 1.25f
                val rest = segs.indices.filter { it != hero }
                flowFit(rest, colL, top + ah * (if (mirror) 0.42f else 0.22f), colW, ah * 0.55f, if (mirror) 2 else 0)
            } else {
                val hs = min(horizHero, aw * 0.92f / max(heroW1, 0.01f))
                val hy = top + ah * (if (mirror) 0.58f else 0.16f)
                val hx = if (mirror) right - heroW1 * hs else left
                boxes += ts.box(heroSeg, SonnetRole.HERO, hx, hy, hs, false, frame = -1)
                val rest = segs.indices.filter { it != hero }
                if (mirror) {
                    val h0 = flowFit(rest, left, 0f, aw, ah * 0.4f, 2)
                    val shift = hy - h0 - hs * 0.2f
                    for (k in boxes.indices) if (boxes[k].role != SonnetRole.HERO) boxes[k] = boxes[k].moved(0f, shift)
                } else flowFit(rest, left, hy + hs * 1.3f, aw * 0.86f, ah * 0.55f, 0)
            }
        }
        SonnetShotKind.FRAGMENT_COLLAGE -> {
            val cx = left + aw / 2f
            val cy = top + ah * 0.48f
            val rMax = min(aw, ah) * 0.42f
            segs.forEachIndexed { k, s ->
                val role = roles[k]
                val vertical = s.cjk && (role == SonnetRole.SEMI || k % 4 == 0) && role != SonnetRole.HERO && s.visibleLength in 2..4
                val sz = if (role == SonnetRole.HERO) min(base * heroScale, aw * 0.8f / max(heroW1, 0.01f)) else if (role == SonnetRole.SEMI) min(semiSize, base * 2.2f) else base * supportScale
                val b0 = ts.box(s, role, 0f, 0f, sz, vertical, frame = if (role == SonnetRole.HERO) -1 else frames(s))
                val (bx, by) = if (role == SonnetRole.HERO) cx - b0.w / 2f to cy - b0.h / 2f else {
                    val a = k * 2.399f + rnd.next() * 0.5f
                    val rr = rMax * (0.45f + 0.55f * ((k % 5) / 4f))
                    (cx + cos(a) * rr - b0.w / 2f) to (cy + sin(a) * rr * 1.25f - b0.h / 2f)
                }
                boxes += b0.movedTo(bx.coerceIn(left, max(left, right - b0.w)), by.coerceIn(top, max(top, bottom - b0.h)), if (role == SonnetRole.HERO) 0f else (rnd.next() - 0.5f) * 24f)
            }
        }
        SonnetShotKind.TRACKING_RIBBON -> {
            // 色带：一条斜放的实心带，词块在带上一字排开，镜头横向跟着正在唱的词走（tracking）。
            val sz = min(base * 2.4f, ah * 0.11f)
            var x = 0f
            val y = top + ah * 0.46f - sz * 0.6f
            segs.forEachIndexed { k, s ->
                val role = roles[k]
                val ss = if (role == SonnetRole.HERO) sz * 1.25f else sz
                val b = ts.box(s, role, x, y + (sz * 1.25f - ss) * 0.8f, ss, false, inverted = true, frame = -1)
                boxes += b
                x += b.w + if (s.cjk && segs.getOrNull(k + 1)?.cjk == true) sz * 0.18f else sz * 0.4f
            }
            ribbon = floatArrayOf(y - sz * 0.25f, y + sz * 1.6f, x)
        }
        SonnetShotKind.POSTER_BLOCKS -> {
            // 海报块：按种子把区域横竖切成若干格，每格放一个词块；主角格实心反白。
            val n = min(segs.size, 6)
            val cells = ArrayList<FloatArray>()
            cells += floatArrayOf(left, top + ah * 0.04f, right, bottom - ah * 0.04f)
            while (cells.size < n) {
                val bi = cells.indices.maxBy { (cells[it][2] - cells[it][0]) * (cells[it][3] - cells[it][1]) }
                val c = cells.removeAt(bi)
                val cw = c[2] - c[0]; val ch = c[3] - c[1]
                val f = 0.38f + rnd.next() * 0.24f
                if (ch > cw * 0.9f) { val m = c[1] + ch * f; cells.add(bi, floatArrayOf(c[0], c[1], c[2], m)); cells.add(bi + 1, floatArrayOf(c[0], m, c[2], c[3])) }
                else { val m = c[0] + cw * f; cells.add(bi, floatArrayOf(c[0], c[1], m, c[3])); cells.add(bi + 1, floatArrayOf(m, c[1], c[2], c[3])) }
            }
            // 主角放进最大格
            val order = segs.indices.take(n).toMutableList()
            val big = cells.indices.maxBy { (cells[it][2] - cells[it][0]) * (cells[it][3] - cells[it][1]) }
            val heroSlot = if (hero < n) hero else 0
            val slots = order.toMutableList()
            if (heroSlot != big) { val t = slots[heroSlot]; slots[heroSlot] = slots[big]; slots[big] = t }
            if (hero >= n) slots[big] = hero
            slots.forEachIndexed { ci, k ->
                val c = cells[ci]
                val pad = base * 0.35f
                val cw = c[2] - c[0] - pad * 2; val ch = c[3] - c[1] - pad * 2
                val s = segs[k]
                val role = roles[k]
                val vertical = s.cjk && ch > cw * 1.6f && s.visibleLength >= 2
                val w1 = ts.width1(s.text, role)
                val cap = if (role == SonnetRole.HERO) base * heroScale else base * 2.4f
                val sz = if (vertical) min(cap, min(ch * 0.85f / (s.visibleLength * 0.95f), cw * 0.9f)) else min(cap, min(cw * 0.92f / max(w1, 0.01f), ch * 0.7f))
                val b = ts.box(s, role, 0f, 0f, sz, vertical, inverted = k == hero, frame = -1, block = c)
                boxes += b.movedTo(c[0] + pad + if (ci % 2 == 0) 0f else cw - b.w, c[3] - pad - b.h, 0f)
            }
            // 放不下的词块挤进主角格底部一行小字
            val restIdx = segs.indices.filter { it !in slots }
            if (restIdx.isNotEmpty()) {
                val c = cells[big]
                support = base
                flowFit(restIdx, c[0] + base * 0.35f, c[1] + base * 0.35f, c[2] - c[0] - base * 0.7f, (c[3] - c[1]) * 0.35f, 0)
            }
        }
        SonnetShotKind.QUIET_TABLEAU -> {
            val cy = top + ah * 0.5f
            if (canVertical && (rnd.next() < 0.5f)) {
                val vs = min(base * heroScale, ah * 0.5f / (heroN * 0.95f))
                boxes += ts.box(heroSeg, SonnetRole.HERO, left + aw / 2f - vs / 2f, cy - vs * 0.95f * heroN - base, vs, true, frame = -1)
                flowFit(segs.indices.filter { it != hero }, left + aw * 0.12f, cy + base * 0.6f, aw * 0.76f, ah * 0.35f, 1)
            } else {
                val hs = min(base * heroScale, aw * 0.8f / max(heroW1, 0.01f))
                boxes += ts.box(heroSeg, SonnetRole.HERO, left + (aw - heroW1 * hs) / 2f, cy - hs * 1.3f, hs, false, frame = -1)
                flowFit(segs.indices.filter { it != hero }, left + aw * 0.12f, cy + base * 0.4f, aw * 0.76f, ah * 0.35f, 1)
            }
        }
    }
    // 巨字底纹（showGiantDecorativeText）：主角词放到满屏宽，描边极淡，躲在一切之后。
    val gw1 = ts.width1(heroSeg.text, SonnetRole.HERO)
    val gs = min((right - left) * 1.1f / max(gw1, 0.01f), ah * 0.42f)
    val giant = ts.measure(heroSeg.text, SonnetRole.HERO, gs)
    return SonnetLayout(boxes, giant, left + (aw - giant.size.width) / 2f + (rnd.next() - 0.5f) * aw * 0.2f, top + ah * (0.15f + rnd.next() * 0.45f), ribbon)
}

private fun SonnetBox.moved(dx: Float, dy: Float) = SonnetBox(seg, role, x + dx, y + dy, w, h, size, vertical, rot, layout, glyphs, inverted, frame, block)
private fun SonnetBox.movedTo(nx: Float, ny: Float, r: Float) = SonnetBox(seg, role, nx, ny, w, h, size, vertical, r, layout, glyphs, inverted, frame, block)

// ───────────────────────────── 画 ─────────────────────────────

/** La Folia 的低音走向（d–A–d–C–F–C–d–A），五线谱上的格位（0 = 第一线，半格一步）。 */
private val FoliaStaff = intArrayOf(2, -1, 2, 1, 4, 1, 2, -1, 2, -1, 2, 1, 4, 1, 2, 2)

@Composable
internal fun SonnetStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    primaryColor: Color,
    secondaryColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    topReserve: Dp,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val program = remember(lines) { compileSonnet(lines) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topPx = with(density) { topReserve.toPx() }
        val bottomPx = with(density) { bottomReserve.toPx() }
        val dp1 = with(density) { 1.dp.toPx() }
        val ts = remember(fontFamily) {
            SonnetTypesetter(
                measurer,
                TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black),
                TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold),
            )
        }
        val label = remember(fontFamily) { TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium) }
        val base = min(w * 0.058f, with(density) { 22.sp.toPx() }) * lyricsFontScale.toFloat()
        val margin = w * 0.07f
        val left = margin
        val right = w - margin
        val top = topPx + h * 0.03f
        val bottom = h - bottomPx - h * 0.09f
        val cache = remember(program, w, h, base, ts) { HashMap<Int, SonnetLayout>() }
        fun layoutOf(i: Int): SonnetLayout = cache.getOrPut(i) { layoutShot(ts, program, program.shots[i], left, top, right, bottom, base) }
        val labelLayouts = remember(w, label) {
            HashMap<String, TextLayoutResult>()
        }
        fun lbl(s: String): TextLayoutResult = labelLayouts.getOrPut(s) { measurer.measure(s, label.copy(fontSize = with(density) { 9.sp.toPx() }.sp), density = OnePx) }
        Canvas(Modifier.fillMaxSize()) {
            if (program.shots.isEmpty()) return@Canvas
            val t = clock.readForDraw()
            val si = sonnetShotAt(program, t)
            val shot = program.shots[si]
            val lay = layoutOf(si)
            val next = program.shots.getOrNull(si + 1)
            // 镜头内进度、退场 / 入场
            val prog = ((t - shot.start) / max(0.5, shot.end - shot.start)).toFloat().coerceIn(0f, 1.4f)
            val exitP = if (next != null && !reduceMotion) ((t - (next.start - SONNET_TRANSITION_S)) / SONNET_TRANSITION_S).toFloat().coerceIn(0f, 1f) else 0f
            val enterP = if (!reduceMotion && shot.transitionIn != null) (1f - ((t - shot.start) / SONNET_TRANSITION_S).toFloat()).coerceIn(0f, 1f) else 0f
            val trans = next?.transitionIn ?: shot.transitionIn
            val tp = max(exitP, enterP)
            val ink = primaryColor
            val faint = secondaryColor

            // 1) 漂浮小形（BackgroundDecor）：每镜头换一种排布。
            val variant = Math.floorMod(sonnetHash("decor:${shot.index}"), 6)
            val rnd = SonnetRng(shot.index * 7919 + 17)
            val drift = if (reduceMotion) 0f else t.toFloat()
            repeat(22) { k ->
                val ax = rnd.next(); val ay = rnd.next(); val rs = rnd.next()
                val (px, py) = when (variant) {
                    0 -> ax * w to (top + ay * (bottom - top))
                    1 -> { val a = k / 22f * 2f * PI.toFloat(); (w / 2 + cos(a) * w * 0.42f) to (h * 0.48f + sin(a) * h * 0.3f) }
                    2 -> ((k % 6 + 0.5f) / 6f * w) to (top + (k / 6 + 0.5f) / 4f * (bottom - top))
                    3 -> (ax * w) to (top + (ax * 0.8f + ay * 0.2f) * (bottom - top))
                    4 -> (w * (0.1f + 0.8f * ax)) to (top + (bottom - top) * (if (k % 2 == 0) 0.04f else 0.96f))
                    else -> { val a = k * 0.6f; (w / 2 + cos(a) * k * w * 0.02f) to (h * 0.48f + sin(a) * k * h * 0.016f) }
                }
                val c = Offset(px + sin(drift * 0.3f + k) * 6f * dp1, py + cos(drift * 0.25f + k * 1.7f) * 6f * dp1)
                val r = (2f + rs * 5f) * dp1
                val a = 0.18f + 0.14f * rs
                when (k % 4) {
                    0 -> drawCircle(faint.copy(alpha = a), r, c, style = Stroke(dp1))
                    1 -> { drawLine(faint.copy(alpha = a), c - Offset(r, 0f), c + Offset(r, 0f), dp1); drawLine(faint.copy(alpha = a), c - Offset(0f, r), c + Offset(0f, r), dp1) }
                    2 -> drawCircle(faint.copy(alpha = a * 1.3f), r * 0.35f, c)
                    else -> rotate(drift * 8f + k * 30f, c) { drawRect(faint.copy(alpha = a), c - Offset(r * 0.7f, r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(dp1)) }
                }
            }

            // 2) 外框（outerFrameMode full）+ 角标，五线谱
            val fi = 14f * dp1
            val fTop = topPx + 4f * dp1
            val fBot = h - bottomPx - 6f * dp1
            val gap = 18f * dp1
            val fc = faint.copy(alpha = 0.55f)
            drawLine(fc, Offset(fi + gap, fTop), Offset(w - fi - gap, fTop), dp1)
            drawLine(fc, Offset(fi + gap, fBot), Offset(w - fi - gap, fBot), dp1)
            drawLine(fc, Offset(fi, fTop + gap), Offset(fi, fBot - gap), dp1)
            drawLine(fc, Offset(w - fi, fTop + gap), Offset(w - fi, fBot - gap), dp1)
            listOf(Offset(fi, fTop), Offset(w - fi, fTop), Offset(fi, fBot), Offset(w - fi, fBot)).forEach { drawCircle(fc, 2f * dp1, it) }
            val code = "SONNET  Nº ${(shot.paragraph + 1).toString().padStart(2, '0')}.${(si + 1).toString().padStart(3, '0')}"
            drawText(lbl(code), faint.copy(alpha = 0.8f), Offset(fi + gap, fTop + 6f * dp1))
            val sec = max(0.0, t).toInt()
            val tc = lbl("%02d:%02d".format(sec / 60, sec % 60) + "  " + shot.kind.name.replace('_', ' '))
            drawText(tc, faint.copy(alpha = 0.8f), Offset(w - fi - gap - tc.size.width, fTop + 6f * dp1))
            // 五线谱：外框底边之上，La Folia 低音走一圈 8 秒，游标随时间推进。
            val staffGap = 4.5f * dp1
            val staffBase = fBot - 14f * dp1
            val sl = fi + gap; val sr = w - fi - gap
            for (k in 0 until 5) drawLine(faint.copy(alpha = 0.35f), Offset(sl, staffBase - k * staffGap), Offset(sr, staffBase - k * staffGap), dp1 * 0.8f)
            val cyc = ((t / 8.0) % 1.0).toFloat().let { if (it < 0) it + 1 else it }
            FoliaStaff.forEachIndexed { k, pos ->
                val nx = sl + (k + 0.5f) / FoliaStaff.size * (sr - sl)
                val ny = staffBase - pos * staffGap / 2f
                val lit = (k + 0.5f) / FoliaStaff.size <= cyc
                withTransform({ rotate(-20f, Offset(nx, ny)) }) {
                    drawOval(if (lit) ink.copy(alpha = 0.75f) else faint.copy(alpha = 0.4f), Offset(nx - staffGap * 0.65f, ny - staffGap * 0.45f), Size(staffGap * 1.3f, staffGap * 0.9f))
                }
            }
            val cx = sl + cyc * (sr - sl)
            drawLine(accentColor.copy(alpha = 0.8f), Offset(cx, staffBase + staffGap), Offset(cx, staffBase - staffGap * 5f), dp1 * 1.2f)

            // 3) 镜头：机位推近 + 倾斜；转场叠在上面。
            val camK = if (reduceMotion) 0f else 1f
            val push = 1f + (shot.zoom - 1f) * 0.06f * min(prog, 1f) * camK
            var scale = push
            var dx = shot.camX * w * 0.12f * camK
            var dy = shot.camY * h * 0.06f * camK
            var alphaAll = 1f
            when (trans) {
                SonnetTransition.CAMERA_PULL -> { scale *= 1f - 0.18f * tp; alphaAll = 1f - 0.6f * tp }
                SonnetTransition.FAST_BLUR -> { scale *= 1f + 0.08f * tp; alphaAll = 1f - tp; dy += tp * 24f * dp1 }
                SonnetTransition.MONO_GLITCH -> { dx += if (tp > 0f) (sin(t * 90.0).toFloat()) * 14f * dp1 * tp else 0f; alphaAll = 1f - 0.5f * tp }
                null -> Unit
            }
            val pivot = Offset(w / 2f, (top + bottom) / 2f)
            withTransform({
                translate(dx, dy)
                scale(scale, scale, pivot)
                rotate(Math.toDegrees(shot.rot.toDouble()).toFloat() * 0.35f * camK, pivot)
            }) {
                // 巨字底纹
                lay.giant?.let { g ->
                    val gx = lay.giantX - (if (reduceMotion) 0f else prog * w * 0.05f)
                    drawText(g, ink, Offset(gx, lay.giantY), alpha = 0.07f * alphaAll, drawStyle = Stroke(dp1 * 1.2f))
                }
                // 色带（tracking-ribbon）：镜头横向跟随正在唱的词
                var trackDx = 0f
                lay.ribbon?.let { rb ->
                    val sung = lay.boxes.lastOrNull { it.seg.start <= t } ?: lay.boxes.first()
                    val nextB = lay.boxes.firstOrNull { it.seg.start > t }
                    var focus = sung.x + sung.w / 2f
                    if (nextB != null) {
                        val f = ((t - sung.seg.start) / max(0.05, nextB.seg.start - sung.seg.start)).toFloat().coerceIn(0f, 1f)
                        focus += (nextB.x + nextB.w / 2f - focus) * sonnetEaseExpoOut(f) * 0.6f
                    }
                    trackDx = (w / 2f - focus).coerceIn(min(0f, w - margin - rb[2]), margin)
                    if (rb[2] + 2 * margin < w) trackDx = (w - rb[2]) / 2f
                    rotate(-6f, pivot) {
                        drawRect(ink.copy(alpha = alphaAll), Offset(-w, rb[0]), Size(w * 3f, rb[1] - rb[0]))
                        drawLine(ink.copy(alpha = 0.5f * alphaAll), Offset(-w, rb[0] - 6f * dp1), Offset(w * 2f, rb[0] - 6f * dp1), dp1)
                        drawLine(ink.copy(alpha = 0.5f * alphaAll), Offset(-w, rb[1] + 6f * dp1), Offset(w * 2f, rb[1] + 6f * dp1), dp1)
                    }
                }
                // 海报块格线
                lay.boxes.forEach { b ->
                    val c = b.block ?: return@forEach
                    val appear = sonnetEaseExpoOut(((t - shot.start + 0.15) / 0.4).toFloat())
                    drawRect(faint.copy(alpha = 0.6f * appear * alphaAll), Offset(c[0], c[1]), Size(c[2] - c[0], c[3] - c[1]), style = Stroke(dp1 * 1.2f))
                    if (b.inverted) {
                        val ip = sonnetEaseExpoOut(((t - b.seg.start + 0.1) / 0.35).toFloat())
                        if (ip > 0f) drawRect(ink.copy(alpha = alphaAll), Offset(c[0], c[1]), Size((c[2] - c[0]) * ip, c[3] - c[1]))
                    }
                }
                lay.boxes.forEach { b ->
                    val rotateRibbon = lay.ribbon != null
                    val drawBox: DrawScope.() -> Unit = {
                        val since = (t - b.seg.start).toFloat()
                        // 定位记号（Guides）：字到之前 0.35 秒，在落点画「+」和一小段引线。
                        if (since < 0f) {
                            if (since > -0.35f && !rotateRibbon) {
                                val ga = (1f + since / 0.35f) * 0.7f * alphaAll
                                val c = Offset(b.x, b.y + b.size * 0.15f)
                                val r = b.size * 0.22f
                                drawLine(accentColor.copy(alpha = ga), c - Offset(r, 0f), c + Offset(r, 0f), dp1)
                                drawLine(accentColor.copy(alpha = ga), c - Offset(0f, r), c + Offset(0f, r), dp1)
                                drawLine(faint.copy(alpha = ga * 0.6f), c, c + Offset(b.w * (1f + since / 0.35f), 0f), dp1 * 0.8f)
                            }
                        } else {
                            val p = if (reduceMotion) 1f else sonnetEaseExpoOut(since / 0.32f)
                            val color = if (b.inverted && b.block == null) backgroundColor else if (b.inverted) backgroundColor else ink
                            val sungP = ((t - b.seg.start) / max(0.08, b.seg.end - b.seg.start)).toFloat().coerceIn(0f, 1f)
                            val a = p * alphaAll * (if (b.role == SonnetRole.SUPPORT && !b.inverted) 0.9f else 1f)
                            val enterDy = (1f - p) * b.size * 0.45f
                            val enterScale = if (b.role == SonnetRole.HERO) 1f + (1f - p) * 0.35f else 1f
                            val center = Offset(b.x + b.w / 2f, b.y + b.h / 2f)
                            withTransform({
                                translate(0f, if (b.role == SonnetRole.HERO) 0f else enterDy)
                                rotate(b.rot, center)
                                scale(enterScale, enterScale, center)
                            }) {
                                if (b.vertical) {
                                    // 竖排：逐字自上而下；mask-reveal 的主角自上而下揭开。
                                    val reveal = if (shot.kind == SonnetShotKind.MASK_REVEAL && b.role == SonnetRole.HERO) sonnetEaseExpoOut(since / 0.6f) else 1f
                                    clipRect(b.x - b.size, b.y - b.size, b.x + b.w + b.size, b.y + b.h * reveal) {
                                        b.glyphs.forEachIndexed { k, g ->
                                            drawText(g, color, Offset(b.x + (b.w - g.size.width) / 2f, b.y + k * b.size * 0.95f - (g.size.height - b.size) / 2f), alpha = a)
                                        }
                                    }
                                    if (sungP < 1f && b.role != SonnetRole.SUPPORT) drawLine(accentColor.copy(alpha = a), Offset(b.x - b.size * 0.12f, b.y), Offset(b.x - b.size * 0.12f, b.y + b.h * sungP), dp1 * 2.5f)
                                } else b.layout?.let { l ->
                                    drawText(l, color, Offset(b.x, b.y), alpha = a)
                                    // 正在唱的词：底下一道随唱推进的细线（hero / semi 用强调色）。
                                    if (sungP < 1f) {
                                        val uc = if (b.inverted) backgroundColor else if (b.role == SonnetRole.SUPPORT) faint else accentColor
                                        drawLine(uc.copy(alpha = a), Offset(b.x, b.y + b.h * 0.98f), Offset(b.x + b.w * sungP, b.y + b.h * 0.98f), dp1 * (if (b.role == SonnetRole.SUPPORT) 1.2f else 2.5f))
                                    }
                                }
                                // 开口框（FrameDecor）：四边从角往外长，角上留缺口，收笔一个小角饰。
                                if (b.frame >= 0 && !b.inverted) {
                                    val fp = sonnetEaseExpoOut((since - 0.1f) / 0.5f)
                                    if (fp > 0f) {
                                        val pad = b.size * 0.22f
                                        val l0 = b.x - pad; val t0 = b.y - pad * 0.4f; val r0 = b.x + b.w + pad; val b0 = b.y + b.h + pad * 0.4f
                                        val gp = pad * 0.6f
                                        val fcol = faint.copy(alpha = 0.7f * a)
                                        val sw = dp1 * (if (b.frame == 2) 1.6f else 1f)
                                        drawLine(fcol, Offset(l0 + gp, t0), Offset(l0 + gp + (r0 - l0 - 2 * gp) * fp, t0), sw)
                                        drawLine(fcol, Offset(r0, t0 + gp), Offset(r0, t0 + gp + (b0 - t0 - 2 * gp) * fp), sw)
                                        drawLine(fcol, Offset(r0 - gp, b0), Offset(r0 - gp - (r0 - l0 - 2 * gp) * fp, b0), sw)
                                        drawLine(fcol, Offset(l0, b0 - gp), Offset(l0, b0 - gp - (b0 - t0 - 2 * gp) * fp), sw)
                                        if (fp > 0.95f) listOf(Offset(l0, t0), Offset(r0, t0), Offset(r0, b0), Offset(l0, b0)).forEach { c ->
                                            when (b.frame) {
                                                0 -> drawCircle(fcol, pad * 0.18f, c)
                                                1 -> { val p2 = Path().apply { moveTo(c.x, c.y - pad * 0.25f); lineTo(c.x + pad * 0.25f, c.y); lineTo(c.x, c.y + pad * 0.25f); lineTo(c.x - pad * 0.25f, c.y); close() }; drawPath(p2, fcol) }
                                                2 -> drawCircle(fcol, pad * 0.22f, c, style = Stroke(dp1))
                                                else -> { drawLine(fcol, c - Offset(pad * 0.25f, 0f), c + Offset(pad * 0.25f, 0f), dp1); drawLine(fcol, c - Offset(0f, pad * 0.25f), c + Offset(0f, pad * 0.25f), dp1) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rotateRibbon) withTransform({ rotate(-6f, pivot); translate(trackDx, 0f) }, drawBox) else drawBox()
                }
            }
            // mono-glitch：转场时横向错位的扫描条
            if (trans == SonnetTransition.MONO_GLITCH && tp > 0f) {
                val g = SonnetRng((t * 30).toInt())
                repeat(5) {
                    val y = g.next() * h
                    drawRect(ink.copy(alpha = 0.5f * tp), Offset(g.next() * w * 0.3f, y), Size(w * (0.3f + g.next() * 0.6f), (2f + g.next() * 10f) * dp1))
                }
            }
            if (trans == SonnetTransition.FAST_BLUR && tp > 0f) drawRect(backgroundColor.copy(alpha = 0.55f * tp))
        }
    }
}
