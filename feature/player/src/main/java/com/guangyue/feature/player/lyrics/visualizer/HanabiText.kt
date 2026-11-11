package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * R7 花火⑥b：逐函数对译 Folia hanabi/text/lyricText.ts（文字引擎）+ glyphs.ts 的纯函数部分。
 * 一行歌词 → 逐字「精灵状态」（字 / 描边 / 硬阴影 / 光晕 / 两个特效层）与字底色块（HbBlocks），入场 / 被唱强调 / 退场全由 t 决定。
 * Pixi 的 Sprite 换成 HbSprite（visible / x / y / rotation / sx / sy / skew / alpha / tint / 用哪个字形），舞台按它画。
 * 字形图集只保留尺寸语义：entry.px = ceil(字号 × 1.12)（同字取最大），乱码字表一格 = 最大字号；sx = size / entry.px × 姿态缩放。
 * 金标 hanabi_text_golden.txt（node 里用假 Pixi + 注入估算字宽跑真的 buildHanabiText，逐帧读精灵状态）。
 */

// ─── glyphs.ts ───

private fun isUprightCp(cp: Int): Boolean =
    cp in 0x1100..0x11FF || cp in 0x2E80..0x2FFF || cp in 0x3000..0x303F || cp in 0x3040..0x30FF || cp in 0x3100..0x31FF ||
        cp in 0x3200..0x9FFF || cp in 0xAC00..0xD7AF || cp in 0xF900..0xFAFF || cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFFEF ||
        cp in 0x20000..0x3134F

/** 中日韩直立字（竖排不侧转）。 */
internal fun hbIsUprightGlyph(char: String): Boolean {
    var i = 0
    while (i < char.length) { val cp = char.codePointAt(i); if (isUprightCp(cp)) return true; i += Character.charCount(cp) }
    return false
}

internal fun hbHasCjk(text: String) = hbIsUprightGlyph(text)

/** 没有 canvas 时的估算：全角 1、空格 0.3、半角 0.6（单测用；舞台用真字宽）。 */
internal fun hbEstimateAdvance(char: String): Double {
    if (char.trim().isEmpty()) return 0.3
    if (hbIsUprightGlyph(char)) return 1.0
    if (Regex("[ilI.,:;'!|]").containsMatchIn(char)) return 0.34
    if (Regex("[mwMW@]").containsMatchIn(char)) return 0.85
    return 0.6
}

private fun cpList(s: String): List<String> {
    val out = ArrayList<String>(); var i = 0
    while (i < s.length) { val n = Character.charCount(s.codePointAt(i)); out += s.substring(i, i + n); i += n }
    return out
}

private val SCRAMBLE_KANA = cpList("アイウエオカキクケコサシスセソタチツテトナニヌネハヒフヘホマミムメモヤユヨラリルレロワヲン＃％＆＊？！◆※")
private val SCRAMBLE_LATIN = cpList("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#%&*?!<>/+=")

internal fun hbPickScrambleChars(text: String, count: Int, random: () -> Double): List<String> {
    val pool = ArrayList(if (hbHasCjk(text)) SCRAMBLE_KANA else SCRAMBLE_LATIN)
    val picked = ArrayList<String>()
    while (picked.size < count && pool.isNotEmpty()) picked += pool.removeAt(floor(random() * pool.size).toInt())
    return picked
}

// ─── lyricText.ts ───

internal class HbLineWord(val text: String, val start: Int, val end: Int, val blank: Boolean, val space: Boolean, var scale: Double = 1.0)

private val HB_LETTER_OR_NUMBER = Regex("[\\p{L}\\p{N}]")

/** 行 → 词（字区间按 grapheme 计）；对不上时退回逐字。 */
internal fun hbLineWords(line: Line, count: Int, split: SonnetWordSplitter = defaultSonnetWordSplitter): List<HbLineWord> {
    val words = ArrayList<HbLineWord>()
    var cursor = 0
    for (segment in split(line)) {
        val length = GraphemeTimings.splitLyricGraphemes(segment).size
        if (length == 0) continue
        val space = segment.trim().isEmpty()
        words += HbLineWord(segment, cursor, cursor + length, space || !lumiereIsWordLike(segment), space)
        cursor += length
    }
    if (cursor == count) return words
    return GraphemeTimings.splitLyricGraphemes(line.fullText).mapIndexed { i, ch ->
        val space = ch.trim().isEmpty()
        HbLineWord(ch, i, i + 1, space || !HB_LETTER_OR_NUMBER.containsMatchIn(ch), space)
    }
}

internal fun hbMixColorInt(a: Int, b: Int, t: Double): Int {
    if (t <= 0) return a
    if (t >= 1) return b
    val r = ((a shr 16) and 0xff) + ((((b shr 16) and 0xff) - ((a shr 16) and 0xff)) * t)
    val g = ((a shr 8) and 0xff) + ((((b shr 8) and 0xff) - ((a shr 8) and 0xff)) * t)
    val bl = (a and 0xff) + (((b and 0xff) - (a and 0xff)) * t)
    return (floor(r + 0.5).toInt() shl 16) or (floor(g + 0.5).toInt() shl 8) or floor(bl + 0.5).toInt()
}

internal const val HB_OUTLINE_WIDTH = 0.085
private const val SHADOW_OFFSET_X = 0.07
private const val SHADOW_OFFSET_Y = 0.085
private const val SHADOW_LAG = 0.045

/** 一个精灵此刻的状态。glyph = 用哪个字（scramble 时换成乱码字），half：0 整字 / 1 前半 / 2 后半（split 入场）。 */
internal class HbSprite {
    var visible = false
    var x = 0.0
    var y = 0.0
    var rotation = 0.0
    var sx = 1.0
    var sy = 1.0
    var skew = 0.0
    var alpha = 1.0
    var tint = 0xffffff
    var glyph = ""
    var glyphPx = 1.0
    var half = 0
    /** rgb 分色层用加色混合。 */
    var additive = false

    fun place(x: Double, y: Double, rotation: Double, sx: Double, sy: Double, skew: Double, alpha: Double) {
        visible = alpha > 0.002
        if (!visible) return
        this.x = x; this.y = y; this.rotation = rotation; this.sx = sx; this.sy = sy; this.skew = skew; this.alpha = alpha
    }
}

internal class HbGlyphAnchor(
    val char: String, val x: Double, val y: Double, val size: Double,
    val sungAt: Double, val sungEnd: Double, val landedAt: Double, val alpha: Double, val blank: Boolean,
)

internal class HbRig(
    val index: Int, val char: String, val x: Double, val y: Double, val size: Double, val rotation: Double, val angle: Double,
    val entryPx: Double,
    val tint: Int, val sungTint: Int, val flashTint: Int, val backTint: Int, val shadowTint: Int, val outlineTint: Int,
    val enterStart: Double, val landed: Double, val exitStart: Double, val exitEnd: Double,
    val sungAt: Double, val sungEnd: Double,
    val ctx: HbFxContext,
) {
    val main = HbSprite()
    val shadow = HbSprite()
    val outline = HbSprite()
    val halo = HbSprite()
    val fxA = HbSprite()
    val fxB = HbSprite()
    val pose = HbGlyphPose()
    val sing = HbSingPose()
    var visible = false
}

internal class HbUnderline(
    val members: List<HbRig>, val x: Double, val y: Double, val angle: Double, val length: Double, val thick: Double,
    val from: Double, val to: Double, val tint: Int,
) {
    var visible = false
    var px = 0.0
    var py = 0.0
    var sx = 0.0
    var sy = 0.0
    var alpha = 0.0
}

internal class HbTextOptions(
    val width: Double,
    val height: Double,
    val line: Line,
    val regionWidth: Double,
    val regionHeight: Double,
    val spec: HbTextSpec,
    val palette: HbPalette,
    val seed: String,
    val enterAt: Double,
    val exitAt: Double,
    val exitDuration: Double,
    val motion: Double,
    val blockOpacity: Double,
    val measure: (String) -> Double = ::hbEstimateAdvance,
    val split: SonnetWordSplitter = defaultSonnetWordSplitter,
    val keywordColor: ((String) -> Int?)? = null,
)

internal class HbText(private val o: HbTextOptions) {
    val spec = o.spec
    private val motion = max(0.0, o.motion)
    val placement: HbTextPlacement
    val blocks: HbBlocks
    val rigs: List<HbRig>
    val scramble: List<String>
    val scramblePx: Double
    val vertical: Boolean
    val halvesLr: Boolean
    val outlineTint: Int
    val underlines = ArrayList<HbUnderline>()
    val bounds: HbBounds
    var shakeX = 0.0
        private set
    var shakeY = 0.0
        private set
    /** type 入场的光标（visible / x / y / rotation / w / h）。 */
    var cursorVisible = false
        private set
    var cursorX = 0.0
        private set
    var cursorY = 0.0
        private set
    var cursorRot = 0.0
        private set
    var cursorW = 0.0
        private set
    var cursorH = 0.0
        private set
    val cursorTint: Int
    /** wipe 入场期间的遮罩：每个可见字一块四边形（8 个数），active 时字层只画在遮罩里。 */
    var wipeActive = false
        private set
    val wipeQuads = HashMap<Int, DoubleArray>()

    private val timeline: List<com.guangyue.core.lyrics.GraphemeTiming>
    private val glyphs: List<HbPlacedGlyph>
    private val enter: HbEnterSchedule
    private val impacts = ArrayList<Double>()
    private val shakeAmplitude: Double
    private val rigOf = HashMap<Int, HbRig>()
    private val firstStart: Double
    private val lastLanded: Double
    private val scratchEnter = HbGlyphPose()
    private val scratchExit = HbGlyphPose()
    private val scratchShadow = HbGlyphPose()
    private val scratchSing = HbSingPose()

    init {
        val rng = lumiereRng("${o.seed}:text")
        timeline = GraphemeTimings.buildDenseLineGraphemeTimeline(o.line)
        val chars = timeline.map { it.char }
        val words = hbLineWords(o.line, chars.size, o.split)
        val scales = hanabiWordScales(words.map { HbWordText(it.text, it.blank, it.space) }, spec.emphasis, o.seed)
        words.forEachIndexed { i, w -> w.scale = scales[i] }
        val glyphWord = IntArray(chars.size)
        words.forEachIndexed { wi, w -> for (g in w.start until min(w.end, chars.size)) glyphWord[g] = wi }
        val inputs = chars.mapIndexed { i, c -> HbLayoutGlyph(c, o.measure(c), c.trim().isEmpty(), hbIsUprightGlyph(c), glyphWord[i]) }
        val lp = hbBlockLayoutParams(spec.block, spec.hardShadow)
        placement = hanabiLayoutText(
            inputs, words.map { HbLayoutWord(it.start, it.end, it.blank, it.space, it.scale) },
            HbLayoutParams(spec.layout, o.regionWidth, o.regionHeight, spec.heroSize * min(o.width, o.height), spec.tilt, o.seed,
                lp.tracking, lp.wordGap, lp.lineHeight, lp.minAdvance, lp.stairStep, lp.padding),
        )
        glyphs = placement.glyphs
        vertical = placement.vertical
        bounds = placement.bounds
        val centerX = placement.bounds.x + placement.bounds.width / 2
        val centerY = placement.bounds.y + placement.bounds.height / 2

        val visibleIndices = glyphs.indices.filter { !glyphs[it].blank }
        val visibleCount = visibleIndices.size
        val orderRanks = hbEnterOrder(spec.enter, visibleCount) { rng.next() }
        val enterRanks = DoubleArray(chars.size) { -1.0 }
        visibleIndices.forEachIndexed { ord, i -> enterRanks[i] = orderRanks[ord] }
        val firstSung = visibleIndices.fold(Double.POSITIVE_INFINITY) { m, i -> min(m, timeline[i].startTime) }
        val deadline = if (firstSung.isFinite()) firstSung else o.line.startTime
        enter = hbScheduleEnter(spec.enter, enterRanks, o.enterAt, spec.stagger, deadline)
        val exitRanks = DoubleArray(chars.size) { -1.0 }
        val exitShuffle = if (spec.exit == "scatter" || spec.exit == "dissolve") {
            val r = lumiereRng("${o.seed}:exit-order"); hbEnterOrder("scramble", visibleCount) { r.next() }
        } else DoubleArray(visibleCount) { it.toDouble() }
        visibleIndices.forEachIndexed { ord, i -> exitRanks[i] = exitShuffle[ord] }
        val exit = hbScheduleExit(exitRanks, o.exitAt, o.exitDuration)

        val arrival = HB_ENTER_ARRIVAL[spec.enter] ?: 0.0
        blocks = HbBlocks(HbBlocksOptions(
            spec.block, placement, words.map { it.blank }, o.palette, hbC01(o.blockOpacity), motion, spec.exit, o.seed,
            DoubleArray(chars.size) { enter.start[it] + enter.duration * arrival }, exit.start, exit.end, centerX, centerY,
        ))

        halvesLr = !placement.vertical
        val charPx = HashMap<String, Double>()
        var maxSize = 0.0
        visibleIndices.forEach { i ->
            val g = glyphs[i]
            val px = ceil(g.size * 1.12)
            charPx[g.char] = max(charPx[g.char] ?: 0.0, px)
            maxSize = max(maxSize, px)
        }
        scramble = if (spec.enter == "scramble") { val r = lumiereRng("${o.seed}:scramble"); hbPickScrambleChars(o.line.fullText, 14) { r.next() } } else emptyList()
        scramblePx = maxSize

        val role = hbBlockTextRole(spec.block)
        val baseInk = if (role == "ink") o.palette.ink else o.palette.paper
        val firstBlock = o.palette.blocks.firstOrNull() ?: o.palette.accent
        val backdrop = hanabiBackdropOf(o.palette)
        outlineTint = o.palette.paper
        cursorTint = o.palette.accent
        val slideDistance = (max(o.regionWidth, o.regionHeight) * 0.8) / max(1.0, placement.fontPx) + 3
        rigs = visibleIndices.mapIndexed { ordinal, index ->
            val glyph = glyphs[index]
            val word = words.getOrNull(glyph.word)
            val keyword = o.keywordColor?.invoke(word?.text ?: glyph.char) ?: o.keywordColor?.invoke(glyph.char)
            val under = blocks.colorOf(index)
            val colors = resolveHanabiGlyphColors(o.palette, keyword ?: baseInk, under ?: backdrop)
            val tint = colors.ink
            val outlineTint = if (tint == o.palette.paper || under == o.palette.paper) firstBlock else o.palette.paper
            val shadowTint = if (under != null) hbDarken(under, 0.42)
            else if (spec.outline && outlineTint == firstBlock) (o.palette.blocks.getOrNull(1) ?: hbDarken(firstBlock, 0.45)) else firstBlock
            val rand = doubleArrayOf(rng.next(), rng.next(), rng.next(), rng.next())
            HbRig(
                index, glyph.char, glyph.x, glyph.y, glyph.size, glyph.rotation, glyph.angle, charPx[glyph.char] ?: 1.0,
                tint, colors.sung, colors.flash, colors.back, shadowTint, outlineTint,
                enter.start[index], enter.landed[index], exit.start[index], exit.end[index],
                timeline[index].startTime, timeline[index].endTime,
                HbFxContext(ordinal, visibleCount, rand,
                    (centerX - glyph.x) / max(1.0, glyph.size), (centerY - glyph.y) / max(1.0, glyph.size),
                    cos(glyph.angle), sin(glyph.angle), 0.0, motion, slideDistance),
            ).also { r ->
                r.shadow.tint = shadowTint; r.outline.tint = outlineTint; r.halo.tint = o.palette.glow; r.halo.additive = true; r.main.tint = tint
                r.main.glyph = r.char; r.main.glyphPx = r.entryPx
            }
        }
        rigs.forEach { rigOf[it.index] = it }

        if (spec.enter == "stamp") {
            val seen = HashSet<Int>()
            rigs.forEach { r ->
                val w = glyphs[r.index].word
                if (seen.add(w)) impacts += r.enterStart + enter.duration * HB_STAMP_IMPACT
            }
        }
        shakeAmplitude = placement.fontPx * 0.05 * motion

        if (spec.sing == "underline") {
            val groups = LinkedHashMap<String, MutableList<Int>>()
            rigs.forEach { r ->
                val g = glyphs[r.index]
                val list = groups.getOrPut("${g.word}") { ArrayList() }
                if (list.isEmpty() || glyphs[list[0]].row == g.row) list += r.index
            }
            groups.values.forEach { indices ->
                val frame = hbRunFrame(glyphs, indices, 0.0, 0.0) ?: return@forEach
                val members = indices.map { rigOf[it]!! }
                val tx = cos(frame.angle); val ty = sin(frame.angle)
                val nx = -ty; val ny = tx
                val avg = members.sumOf { it.size } / members.size
                val thick = max(3.0, avg * 0.15)
                val offset = frame.thickness / 2 + thick * 0.4
                underlines += HbUnderline(
                    members,
                    frame.cx - tx * frame.length / 2 + nx * offset, frame.cy - ty * frame.length / 2 + ny * offset,
                    frame.angle, frame.length, thick,
                    members.minOf { it.sungAt }, members.maxOf { it.sungEnd },
                    members.firstOrNull()?.sungTint ?: o.palette.accent,
                )
            }
        }
        firstStart = rigs.fold(Double.POSITIVE_INFINITY) { m, r -> min(m, r.enterStart) }
        lastLanded = rigs.fold(Double.NEGATIVE_INFINITY) { m, r -> max(m, r.landed) }
    }

    /** 第 rig 个字在 time − lag 的姿态，写进 out；返回是否可见。 */
    private fun evaluate(rig: HbRig, time: Double, lag: Double, out: HbGlyphPose, singOut: HbSingPose?): Boolean {
        val t = time - lag
        if (!(t >= rig.enterStart) || t >= rig.exitEnd) {
            out.rest(); out.alpha = 0.0
            if (singOut != null) hbSingPose("none", -1.0, 0.0, 0.0, 1.0, singOut)
            return false
        }
        rig.ctx.time = t - rig.enterStart
        hbEnterPose(spec.enter, (t - rig.enterStart) / max(1e-3, enter.duration), rig.ctx, scratchEnter)
        rig.ctx.time = t - rig.exitStart
        val span = rig.exitEnd - rig.exitStart
        hbExitPose(spec.exit, if (span > 0) (t - rig.exitStart) / span else 0.0, rig.ctx, scratchExit)
        hbCombinePoses(scratchEnter, scratchExit, out)
        if (singOut != null) {
            if (t >= rig.landed) hbSingPose(spec.sing, t - rig.sungAt, rig.sungEnd - rig.sungAt, rig.ctx.rand[3], motion, singOut)
            else hbSingPose("none", -1.0, 0.0, 0.0, 1.0, singOut)
        }
        return out.alpha > 0.002
    }

    private var evaluatedAt = Double.NaN

    private fun evaluateAll(time: Double) {
        if (time == evaluatedAt) return
        evaluatedAt = time
        if (impacts.isNotEmpty()) { val s = hbStampShake(time, impacts, shakeAmplitude); shakeX = s.first; shakeY = s.second } else { shakeX = 0.0; shakeY = 0.0 }
        rigs.forEach { it.visible = evaluate(it, time, 0.0, it.pose, it.sing) }
    }

    fun update(time: Double) {
        evaluateAll(time)
        blocks.update(time)
        for (rig in rigs) {
            val pose = rig.pose; val sing = rig.sing
            if (!rig.visible) {
                rig.main.visible = false; rig.shadow.visible = false; rig.outline.visible = false
                rig.halo.visible = false; rig.fxA.visible = false; rig.fxB.visible = false
                continue
            }
            val size = rig.size
            val base = size / rig.entryPx
            val x = rig.x + (pose.dx + sing.dx) * size
            val y = rig.y + (pose.dy + sing.dy) * size
            val rotation = rig.rotation + pose.rotation + sing.rotation
            val sx = base * pose.scaleX * sing.scale * sing.scaleX
            val sy = base * pose.scaleY * sing.scale * sing.scaleY
            val scrambled = if (pose.scramble >= 0 && scramble.isNotEmpty()) scramble[pose.scramble % scramble.size] else null
            val glyph = scrambled ?: rig.char
            val glyphPx = if (scrambled != null) scramblePx else rig.entryPx
            val scrambleScale = if (scrambled != null) rig.entryPx / scramblePx else 1.0
            var tint = hbMixColorInt(rig.tint, rig.sungTint, sing.color)
            tint = hbMixColorInt(tint, rig.flashTint, sing.flash)
            if (pose.backside) tint = rig.backTint
            rig.main.tint = tint
            rig.main.glyph = glyph; rig.main.glyphPx = glyphPx
            val splitting = pose.split > 0.001 && spec.enter == "split"
            rig.main.place(x, y, rotation, sx * scrambleScale, sy * scrambleScale, pose.skew, if (splitting) 0.0 else pose.alpha)
            val attach = if (splitting) 1 - hbC01(pose.split * 3) else 1.0
            if (spec.outline) {
                rig.outline.glyph = glyph; rig.outline.glyphPx = glyphPx
                rig.outline.place(x, y, rotation, sx * scrambleScale, sy * scrambleScale, pose.skew, pose.alpha * attach)
            }
            if (spec.hardShadow) {
                val lagged = if (evaluate(rig, time, SHADOW_LAG, scratchShadow, null)) scratchShadow else pose
                val lx = rig.x + (lagged.dx + sing.dx) * size + SHADOW_OFFSET_X * size
                val ly = rig.y + (lagged.dy + sing.dy) * size + SHADOW_OFFSET_Y * size
                rig.shadow.glyph = glyph; rig.shadow.glyphPx = glyphPx
                rig.shadow.place(
                    lx, ly, rig.rotation + lagged.rotation + sing.rotation,
                    base * lagged.scaleX * sing.scale * sing.scaleX * scrambleScale,
                    base * lagged.scaleY * sing.scale * sing.scaleY * scrambleScale,
                    lagged.skew, min(pose.alpha, lagged.alpha) * attach,
                )
            }
            if (spec.sing == "glow") {
                val k = 1 + 0.14 * sing.glow
                rig.halo.glyph = rig.char; rig.halo.glyphPx = rig.entryPx
                rig.halo.place(x, y, rotation, sx * k, sy * k, 0.0, pose.alpha * sing.glow * 0.95)
            }
            val needFx = spec.enter == "glitch" || spec.enter == "zoom" || spec.enter == "split" || spec.exit == "dissolve"
            if (needFx) {
                val a = rig.fxA; val b = rig.fxB
                if (splitting) {
                    a.half = 1; b.half = 2; a.additive = false; b.additive = false; a.tint = rig.tint; b.tint = rig.tint
                    a.glyph = rig.char; b.glyph = rig.char; a.glyphPx = rig.entryPx; b.glyphPx = rig.entryPx
                    val sep = pose.split
                    val c = cos(rotation); val s = sin(rotation)
                    val ax = if (halvesLr) -1.3 * sep else -0.45 * sep
                    val ay = if (halvesLr) -0.45 * sep else -1.3 * sep
                    a.place(x + (ax * c - ay * s) * size, y + (ax * s + ay * c) * size, rotation, sx, sy, pose.skew, pose.alpha)
                    b.place(x - (ax * c - ay * s) * size, y - (ax * s + ay * c) * size, rotation, sx, sy, pose.skew, pose.alpha)
                } else if (pose.rgbSplit > 0.001) {
                    a.half = 0; b.half = 0; a.additive = true; b.additive = true; a.tint = 0xff2850; b.tint = 0x20e8ff
                    a.glyph = glyph; b.glyph = glyph; a.glyphPx = glyphPx; b.glyphPx = glyphPx
                    val d = pose.rgbSplit * size
                    a.place(x - d, y, rotation, sx * scrambleScale, sy * scrambleScale, pose.skew, pose.alpha * 0.9)
                    b.place(x + d, y + d * 0.2, rotation, sx * scrambleScale, sy * scrambleScale, pose.skew, pose.alpha * 0.9)
                } else if (pose.blur > 0.01) {
                    a.half = 0; b.half = 0; a.additive = false; b.additive = false; a.tint = tint; b.tint = tint
                    a.glyph = glyph; b.glyph = glyph; a.glyphPx = glyphPx; b.glyphPx = glyphPx
                    val k1 = 1 + 0.16 * pose.blur; val k2 = 1 + 0.38 * pose.blur
                    a.place(x, y, rotation, sx * k1, sy * k1, pose.skew, pose.alpha * 0.4 * pose.blur)
                    b.place(x, y, rotation, sx * k2, sy * k2, pose.skew, pose.alpha * 0.22 * pose.blur)
                } else { a.visible = false; b.visible = false }
            }
        }
        // wipe：入场期间把字层遮在逐字擦出的矩形里。
        if (spec.enter == "wipe") {
            wipeActive = time >= firstStart && time < lastLanded
            wipeQuads.clear()
            if (wipeActive) rigs.forEach { rig ->
                if (!rig.visible) return@forEach
                val pose = rig.pose; val size = rig.size; val glyph = glyphs[rig.index]
                val tx = cos(rig.angle); val ty = sin(rig.angle)
                val nx = -ty; val ny = tx
                val margin = size * 0.2
                val along = glyph.advance + margin * 2
                val t0 = -along / 2
                val t1 = t0 + along * pose.reveal
                if (t1 <= t0) return@forEach
                val n0 = -size * 0.85; val n1 = size * 0.85
                val cx = rig.x + pose.dx * size; val cy = rig.y + pose.dy * size
                wipeQuads[rig.index] = doubleArrayOf(
                    cx + tx * t0 + nx * n0, cy + ty * t0 + ny * n0, cx + tx * t1 + nx * n0, cy + ty * t1 + ny * n0,
                    cx + tx * t1 + nx * n1, cy + ty * t1 + ny * n1, cx + tx * t0 + nx * n1, cy + ty * t0 + ny * n1,
                )
            }
        }
        // type：光标停在最后一个已出现的字后面，打字时常亮、停下后闪烁，退场时消失。
        if (spec.enter == "type") {
            var current: HbRig? = null
            for (rig in rigs) if (time >= rig.enterStart && (current == null || rig.enterStart >= current.enterStart)) current = rig
            val first = rigs.firstOrNull()
            val typing = time < lastLanded
            val visibleFrom = o.enterAt - 0.25
            val exitFrom = rigs.fold(Double.POSITIVE_INFINITY) { m, r -> min(m, r.exitStart) }
            val hideAt = min(lastLanded + 1.2, exitFrom)
            val blinkOn = typing || floor((time - lastLanded) / 0.28).toLong() % 2 == 0L
            val target = current ?: first
            if (target == null || time < visibleFrom || time >= hideAt || !blinkOn) cursorVisible = false
            else {
                val glyph = glyphs[target.index]
                val tx = cos(target.angle); val ty = sin(target.angle)
                val side = if (current != null) 1 else -1
                val offset = (glyph.advance / 2 + target.size * 0.1) * side
                cursorVisible = true
                cursorX = target.x + tx * offset; cursorY = target.y + ty * offset
                cursorRot = target.angle
                cursorW = max(2.0, target.size * 0.09); cursorH = target.size * 0.95
            }
        }
        // underline：被唱的词下面一条粗线随唱的进度伸长，唱完从词首收起。
        underlines.forEach { line ->
            val landed = line.members.all { time >= it.landed }
            val grow = hbC01((time - line.from) / max(0.12, line.to - line.from))
            val retract = hbC01((time - line.to - 0.12) / 0.28)
            val head = 1 - (1 - grow) * (1 - grow) * (1 - grow)
            val tail = retract * retract * retract
            val alpha = line.members.fold(1.0) { m, r -> min(m, if (r.visible) r.pose.alpha else 0.0) }
            if (!landed || time < line.from || head <= tail || alpha <= 0.002) { line.visible = false; return@forEach }
            val tx = cos(line.angle); val ty = sin(line.angle)
            line.visible = true
            line.px = line.x + tx * line.length * tail; line.py = line.y + ty * line.length * tail
            line.sx = line.length * (head - tail)
            line.sy = line.thick * (1 + 0.5 * (1 - hbC01((time - line.from) / 0.12)))
            line.alpha = alpha
        }
    }

    /** 每个字此刻的位置与状态（文字本地坐标），烟花与装饰件跟着它走。 */
    fun anchors(time: Double): List<HbGlyphAnchor> {
        evaluateAll(time)
        return glyphs.mapIndexed { index, glyph ->
            val rig = rigOf[index]
            val timing = timeline[index]
            if (rig == null) {
                HbGlyphAnchor(glyph.char, glyph.x + shakeX, glyph.y + shakeY, glyph.size, timing.startTime, timing.endTime,
                    if (enter.landed[index].isFinite()) enter.landed[index] else o.enterAt, 0.0, true)
            } else {
                val pose = rig.pose; val sing = rig.sing
                val scale = ((abs(pose.scaleX * sing.scaleX) + abs(pose.scaleY * sing.scaleY)) / 2) * sing.scale
                HbGlyphAnchor(glyph.char, rig.x + (pose.dx + sing.dx) * rig.size + shakeX, rig.y + (pose.dy + sing.dy) * rig.size + shakeY,
                    rig.size * scale, rig.sungAt, rig.sungEnd, rig.landed, if (rig.visible) hbC01(pose.alpha) else 0.0, false)
            }
        }
    }
}
