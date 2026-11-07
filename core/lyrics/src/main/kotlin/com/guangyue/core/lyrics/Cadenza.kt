// 对应 Folia 文件：src/components/visualizer/cadenza/
//   cadenzaEnvelopes.ts（状态 / 包络，逐条照抄）
//   cadenzaPreparedState.ts（字号、换行宽度）
//   cadenzaFragments.ts（按行切词片段）
//   cadenzaPlacements.ts（强调词、碰撞避让、落点与 passed 漂移，逐条照抄）
//   src/types.ts（CadenzaTuning）
//
// V2b「心象」(cadenza)：整句拆成词散落在焦点周围，主词放大居中，其余词绕开它。
// 手机化：Folia 用 pretext 做测量与换行；这里由绘制层传入测宽函数，按词贪心换行
//（词比整行还宽时按字素拆开，与 pretext 的 breakable 行为同向），片段坐标与 Folia 同口径。
package com.guangyue.core.lyrics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 对应 Folia CadenzaTuning / DEFAULT_CADENZA_TUNING（beamIntensity 已从样式移除，恒 0，不建模）。 */
data class CadenzaTuning(
    val fontScale: Double = 1.12,
    val widthRatio: Double = 0.72,
    val motionAmount: Double = 1.0,
    val glowIntensity: Double = 1.0,
) {
    companion object {
        val DEFAULT = CadenzaTuning()
    }
}

enum class CadenzaStatus { WAITING, ACTIVE, PASSED }

data class CadenzaLineTiming(
    val renderHints: LineRenderHints?,
    val lineRenderEndTime: Double,
    val wordRevealMode: WordRevealMode,
    val lastWordEndTime: Double,
    val linePassHold: Double,
    val transition: LineTransitionTiming,
)

data class CadenzaLineEnvelope(val opacity: Double, val scale: Double, val blur: Double)

data class CadenzaPlacement(
    val id: String,
    val wordIndex: Int,
    val word: Word,
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotate: Double,
    val scale: Double,
    val passedRotate: Double,
    val passedDriftX: Double,
    val passedDriftY: Double,
    val fragmentStartInWord: Int,
    val wordGraphemeCount: Int,
    val wordGraphemeTimings: List<GraphemeTiming>,
    val emphasis: Double,
    val isInterlude: Boolean,
)

data class CadenzaPrepared(
    val fontPx: Double,
    val lineHeight: Double,
    val maxWidth: Double,
    val placements: List<CadenzaPlacement>,
)

/** 一帧里一个落点的平滑状态（对应 AnimatedPlacementState）。 */
data class CadenzaAnimState(
    var x: Double,
    var y: Double,
    var rotation: Double,
    var scale: Double,
    var bodyAlpha: Double,
    var blur: Double,
    var activeMix: Double,
    var glowAlpha: Double,
)

object Cadenza {

    const val ACTIVE_PULSE_FREQUENCY = 10.0
    private val CJK = Regex("[\\u4e00-\\u9fa5\\u3040-\\u30ff\\uac00-\\ud7af]")

    fun isCjk(text: String) = CJK.containsMatchIn(text)
    fun clamp(v: Double, a: Double, b: Double) = max(a, min(b, v))
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    private fun easeOutCubic(v: Double) = 1 - (1 - clamp(v, 0.0, 1.0)).pow(3)
    private fun easeInOutQuad(v: Double): Double {
        val n = clamp(v, 0.0, 1.0)
        return if (n < 0.5) 2 * n * n else 1 - (-2 * n + 2).pow(2) / 2
    }
    private fun graphemes(t: String) = GraphemeTimings.splitLyricGraphemes(t)

    // ---------------- cadenzaEnvelopes.ts ----------------

    fun resolveLineTiming(line: Line): CadenzaLineTiming {
        val hints = line.renderHints
        val reveal = hints?.wordRevealMode ?: WordRevealMode.NORMAL
        val raw = hints?.rawDuration ?: max(line.endTime - line.startTime, 0.0)
        val transition = RenderHints.getLineTransitionTiming(raw, hints?.lineTransitionMode ?: LineTransitionMode.NORMAL, reveal)
        return CadenzaLineTiming(
            renderHints = hints,
            lineRenderEndTime = hints?.renderEndTime ?: line.endTime,
            wordRevealMode = reveal,
            lastWordEndTime = line.words.lastOrNull()?.endTime ?: line.endTime,
            linePassHold = transition.linePassHold,
            transition = transition,
        )
    }

    fun wordStatus(time: Double, lt: CadenzaLineTiming, word: Word): CadenzaStatus {
        val lookahead = when (lt.wordRevealMode) {
            WordRevealMode.FAST -> 0.045
            WordRevealMode.INSTANT -> 0.0
            else -> 0.18
        }
        val activeEnd = if (lt.wordRevealMode == WordRevealMode.INSTANT) lt.lineRenderEndTime else word.endTime
        return when {
            time >= word.startTime - lookahead && time <= activeEnd -> CadenzaStatus.ACTIVE
            time > activeEnd -> CadenzaStatus.PASSED
            else -> CadenzaStatus.WAITING
        }
    }

    fun wordProgress(time: Double, mode: WordRevealMode, word: Word): Double {
        if (mode == WordRevealMode.INSTANT) return if (time < word.startTime) 0.0 else 1.0
        val minD = if (mode == WordRevealMode.FAST) 0.045 else 0.01
        return clamp((time - word.startTime) / max(word.endTime - word.startTime, minD), 0.0, 1.0)
    }

    private fun keyframedGlow(p: Double): Double = when {
        p <= 0 || p >= 1 -> 0.0
        p < 0.3 -> easeOutCubic(p / 0.3)
        else -> 1 - clamp((p - 0.3) / 0.7, 0.0, 1.0)
    }

    fun glowEnvelope(time: Double, lt: CadenzaLineTiming, word: Word): Double {
        when (lt.wordRevealMode) {
            WordRevealMode.INSTANT -> {
                if (time < word.startTime || time > lt.lineRenderEndTime) return 0.0
                return keyframedGlow(clamp((time - word.startTime) / 0.067, 0.0, 1.0))
            }
            WordRevealMode.FAST -> {
                val d = max(word.endTime - word.startTime, 0.045)
                if (time < word.startTime) return 0.0
                if (time <= word.endTime) {
                    val p = clamp((time - word.startTime) / d, 0.0, 1.0)
                    return when {
                        p < 0.14 -> easeOutCubic(p / 0.14)
                        p < 0.82 -> 1.0
                        else -> mix(1.0, 0.92, (p - 0.82) / 0.18)
                    }
                }
                return (1 - clamp((time - word.endTime) / 0.14, 0.0, 1.0)).pow(2)
            }
            else -> {
                val d = max(word.endTime - word.startTime, 0.1)
                if (time < word.startTime) return 0.0
                if (time <= word.endTime) {
                    val p = clamp((time - word.startTime) / d, 0.0, 1.0)
                    return when {
                        p < 0.18 -> easeOutCubic(p / 0.18)
                        p < 0.9 -> 1.0
                        else -> mix(1.0, 0.9, (p - 0.9) / 0.1)
                    }
                }
                return 0.9 * (1 - clamp((time - word.endTime) / 0.9, 0.0, 1.0)).pow(2)
            }
        }
    }

    fun charGlow(time: Double, word: Word, glyphIndex: Int, glyphCount: Int, timings: List<GraphemeTiming>): Double {
        val duration = max(word.endTime - word.startTime, 0.1)
        val single = duration / max(glyphCount, 1)
        val t = timings.getOrNull(glyphIndex)
        val charDuration = if (t != null) max(t.endTime - t.startTime, 0.001) else single
        val charStart = t?.startTime ?: (word.startTime + single * glyphIndex)
        val elapsed = time - charStart
        val active = if (elapsed <= 0) 0.0 else keyframedGlow(elapsed / (charDuration * 6))
        if (time <= word.endTime) return active
        return active * (1 - clamp((time - word.endTime) / 0.9, 0.0, 1.0)).pow(2)
    }

    fun bodyMix(time: Double, lt: CadenzaLineTiming, word: Word): Double {
        if (lt.wordRevealMode == WordRevealMode.INSTANT) {
            if (time < word.startTime) return 0.0
            return if (time <= lt.lineRenderEndTime) 1.0 else 0.0
        }
        if (time < word.startTime) return 0.0
        if (time <= word.endTime) return wordProgress(time, lt.wordRevealMode, word)
        val fade = clamp((time - word.endTime) / (if (lt.wordRevealMode == WordRevealMode.FAST) 0.12 else 0.8), 0.0, 1.0)
        return 1 - fade
    }

    fun lineEnvelope(time: Double, line: Line?, lt: CadenzaLineTiming): CadenzaLineEnvelope {
        if (line == null) return CadenzaLineEnvelope(1.0, 1.0, 0.0)
        val hints = lt.renderHints
        val lineEnd = lt.lineRenderEndTime
        val passStart = max(lt.lastWordEndTime, line.startTime) + lt.linePassHold
        if (hints?.lineTransitionMode == LineTransitionMode.NONE) return CadenzaLineEnvelope(1.0, 1.0, 0.0)
        val enter = lt.transition.enterDuration
        val exit = lt.transition.exitDuration
        if (hints?.lineTransitionMode == LineTransitionMode.FAST) {
            val exitStart = maxOf(line.startTime + enter + 0.01, passStart, lineEnd - exit)
            val ep = easeOutCubic(clamp((time - line.startTime) / enter, 0.0, 1.0))
            var o = mix(0.65, 1.0, ep)
            var s = mix(0.97, 1.0, ep)
            var b = mix(4.0, 0.0, ep)
            val xp = easeOutCubic(clamp((time - exitStart) / exit, 0.0, 1.0))
            if (xp > 0) {
                o = mix(o, 0.0, xp); s = mix(s, 1.03, xp); b = max(b, mix(0.0, 6.0, xp))
            }
            return CadenzaLineEnvelope(clamp(o, 0.0, 1.0), s, b)
        }
        val pre = min(0.1, enter * 0.35)
        val ep = easeOutCubic(clamp((time - (line.startTime - pre)) / (enter + pre), 0.0, 1.0))
        var o = mix(0.0, 1.0, ep)
        var s = mix(0.9, 1.0, ep)
        var b = mix(10.0, 0.0, ep)
        val exitStart = max(passStart, lineEnd - exit)
        val xp = easeOutCubic(clamp((time - exitStart) / exit, 0.0, 1.0))
        if (xp > 0) {
            o *= 1 - xp; s = mix(s, 1.1, xp); b = max(b, mix(0.0, 20.0, xp))
        }
        return CadenzaLineEnvelope(clamp(o, 0.0, 1.0), s, b)
    }

    fun passedDrift(time: Double, word: Word): Double =
        if (time <= word.endTime) 0.0 else easeInOutQuad(clamp((time - word.endTime) / 5, 0.0, 1.0))

    // ---------------- cadenzaPreparedState.ts ----------------

    fun chooseFontPx(width: Double, line: Line): Double {
        val gc = graphemes(line.fullText).size.coerceAtLeast(1)
        val wc = line.words.size.coerceAtLeast(1)
        val base = clamp(width * 0.086, 34.0, 94.0)
        val lengthPenalty = if (gc > 12) min((gc - 12) * 1.8, 34.0) else 0.0
        val densityPenalty = if (wc > 7) min((wc - 7) * 1.5, 18.0) else 0.0
        return clamp(base - lengthPenalty - densityPenalty, 28.0, 104.0)
    }

    private class Frag(
        val wordIndex: Int,
        val word: Word,
        val text: String,
        val startX: Double,
        val endX: Double,
        val startInWord: Int,
        val wordGraphemeCount: Int,
        val timings: List<GraphemeTiming>,
        var indexInWord: Int = 0,
        var countInWord: Int = 1,
    ) {
        val isPrimary get() = indexInWord == 0
        val isSplit get() = countInWord > 1
    }

    private class LayLine(val frags: MutableList<Frag> = ArrayList(), var width: Double = 0.0)

    /**
     * 一行歌词的完整准备（字号 → 换行 → 片段 → 落点）。[measure] 返回给定字号下文本的单行宽度（与 Folia canvas font 同口径，dp）。
     */
    fun prepare(
        line: Line,
        viewportWidth: Double,
        viewportHeight: Double,
        tuning: CadenzaTuning,
        intensity: AnimationIntensity,
        measure: (text: String, fontPx: Double) -> Double,
    ): CadenzaPrepared? {
        if (viewportWidth <= 0 || viewportHeight <= 0 || line.words.isEmpty()) return null
        val fontPx = clamp(chooseFontPx(viewportWidth, line) * tuning.fontScale, 24.0, 132.0)
        val allGraphemes = graphemes(line.fullText)
        val lineHeight = (fontPx * (if (isCjk(line.fullText)) 1.22 else 1.1)).roundToInt().toDouble()
        val available = max(viewportWidth - 48, 120.0)
        val minWidth = min(220.0, available)
        val compression = if (allGraphemes.size > 12) clamp(0.92 - (allGraphemes.size - 12) * 0.018, 0.62, 0.92) else 0.92
        val maxWidth = clamp(min(viewportWidth * tuning.widthRatio * compression, 820.0), minWidth, available)

        // 贪心换行：词整体放得下就放；放不下且当前行非空则换行；词本身比整行还宽时按字素拆。
        val lines = ArrayList<LayLine>()
        var cur = LayLine()
        lines.add(cur)
        line.words.forEachIndexed { wi, word ->
            val gs = graphemes(word.text)
            val timings = GraphemeTimings.buildWordGraphemeTimings(word)
            val trimmedW = measure(word.text.trimEnd(), fontPx)
            val fullW = measure(word.text, fontPx)
            if (cur.frags.isNotEmpty() && cur.width + trimmedW > maxWidth) {
                cur = LayLine(); lines.add(cur)
            }
            if (trimmedW <= maxWidth || gs.size <= 1) {
                cur.frags.add(Frag(wi, word, word.text, cur.width, cur.width + trimmedW, 0, max(gs.size, 1), timings))
                cur.width += fullW
            } else {
                var start = 0
                while (start < gs.size) {
                    var end = start + 1
                    while (end < gs.size && cur.width + measure(gs.subList(start, end + 1).joinToString(""), fontPx) <= maxWidth) end++
                    val text = gs.subList(start, end).joinToString("")
                    val w = measure(text, fontPx)
                    cur.frags.add(Frag(wi, word, text, cur.width, cur.width + w, start, gs.size, timings))
                    cur.width += w
                    start = end
                    if (start < gs.size) { cur = LayLine(); lines.add(cur) }
                }
            }
        }
        // 行宽不计行尾空白（pretext 的 line.width 同样不含尾随空格）。
        lines.forEach { l -> l.width = l.frags.lastOrNull()?.endX ?: 0.0 }
        val count = HashMap<Int, Int>()
        lines.forEach { l -> l.frags.forEach { count[it.wordIndex] = (count[it.wordIndex] ?: 0) + 1 } }
        val seen = HashMap<Int, Int>()
        lines.forEach { l ->
            l.frags.forEach { f ->
                f.countInWord = count[f.wordIndex] ?: 1
                f.indexInWord = seen[f.wordIndex] ?: 0
                seen[f.wordIndex] = f.indexInWord + 1
            }
        }
        val placements = buildPlacements(
            lines.filter { it.frags.isNotEmpty() }, fontPx, lineHeight, maxWidth, intensity,
            line.startTime * 1000, line.fullText == "......", viewportWidth,
        )
        return CadenzaPrepared(fontPx, lineHeight, maxWidth, placements)
    }

    // ---------------- cadenzaPlacements.ts ----------------

    private class Plan(
        val frag: Frag, val lineIndex: Int, val fragmentIndex: Int,
        val baseX: Double, val baseY: Double, val emphasis: Double,
        val width: Double, val height: Double, val scale: Double,
        val cw: Double, val ch: Double, val padding: Double,
    )

    private data class Rect(val l: Double, val t: Double, val r: Double, val b: Double)

    private fun buildPlacements(
        lineData: List<LayLine>,
        fontPx: Double,
        lineHeight: Double,
        maxWidth: Double,
        intensity: AnimationIntensity,
        seed: Double,
        isInterlude: Boolean,
        viewportWidth: Double,
    ): List<CadenzaPlacement> {
        val totalHeight = max(lineData.size, 1) * lineHeight
        val baseMargin = if (intensity == AnimationIntensity.CALM) 4.0 else 6.0
        val baseScale = when (intensity) {
            AnimationIntensity.CHAOTIC -> 1.02
            AnimationIntensity.CALM -> 1.0
            else -> 1.01
        }
        // 强调词（buildEmphasisMap）
        val emphasis = HashMap<Int, Double>()
        if (!isInterlude) {
            val primaries = lineData.flatMap { it.frags }.filter { it.isPrimary }
            val hero = primaries
                .filter { !it.isSplit && it.text.trim().isNotEmpty() }
                .map { f ->
                    val gc = maxOf(f.wordGraphemeCount, graphemes(f.word.text).size, 1)
                    val semantic = if (isCjk(f.word.text)) 0.18 else min(gc * 0.08, 0.36)
                    val centerBias = 1 - abs(f.wordIndex - (primaries.size - 1) / 2.0) / max(primaries.size, 1)
                    f to semantic + centerBias * 0.18
                }
                .sortedByDescending { it.second } // 稳定排序，与 JS Array.sort 一致
                .firstOrNull()
            if (hero != null) emphasis[hero.first.wordIndex] = 1.46 * (1 + clamp(hero.second - 0.48, 0.0, 0.52))
        }
        val heroWordIndex = emphasis.entries.maxByOrNull { it.value }?.key

        val rects = ArrayList<Rect>()
        fun collide(l: Double, t: Double, r: Double, b: Double): Pair<Boolean, Double> {
            var hit = false
            var area = 0.0
            for (rc in rects) {
                val ow = max(0.0, min(r, rc.r) - max(l, rc.l))
                val oh = max(0.0, min(b, rc.b) - max(t, rc.t))
                if (ow <= 0 || oh <= 0) continue
                hit = true; area += ow * oh
            }
            return hit to area
        }
        fun push(left: Double, top: Double, w: Double, h: Double, p: Double) {
            rects.add(Rect(left - p, top - h - p, left + w + p, top + p))
        }

        val plans = lineData.flatMapIndexed { li, lv ->
            val lineLeft = -lv.width / 2
            val baselineY = -totalHeight / 2 + fontPx + li * lineHeight
            lv.frags.mapIndexed { fi, f ->
                val e = emphasis[f.wordIndex] ?: 1.0
                val w = max(f.endX - f.startX, fontPx * 0.18)
                val s = if (e > 1) baseScale * e else baseScale
                val h = fontPx * s * 0.95
                Plan(
                    f, li, fi, lineLeft + f.startX, baselineY, e, w, h, s,
                    w * s * (if (e > 1) 1.48 else 1.26), h * (if (e > 1) 1.36 else 1.24),
                    baseMargin + (if (e > 1) 10 else 2),
                )
            }
        }.sortedWith(
            Comparator { a, b ->
                val d = b.emphasis - a.emphasis
                when {
                    abs(d) > 0.001 -> if (d > 0) 1 else -1
                    a.lineIndex != b.lineIndex -> a.lineIndex - b.lineIndex
                    else -> a.frag.startX.compareTo(b.frag.startX)
                }
            },
        )
        val heroPlan = heroWordIndex?.let { idx -> plans.firstOrNull { it.frag.wordIndex == idx } }
        val heroCx = heroPlan?.let { it.width * it.scale / 2 }
        val heroCy = heroPlan?.let { -it.height * 0.46 }
        val heroW = heroPlan?.let { it.width * it.scale }

        val out = ArrayList<CadenzaPlacement>()
        for (p in plans) {
            val f = p.frag
            val wordSeed = seed + f.wordIndex * 17 + p.lineIndex * 31 + p.fragmentIndex * 13
            fun rnd(o: Double): Double { val x = sin(wordSeed + o) * 10000; return x - floor(x) }
            val passedRotate = (rnd(3.0) - 0.5) * (if (intensity == AnimationIntensity.CHAOTIC) 20 else 12)
            val step = max(10.0, (fontPx * 0.14).roundToInt().toDouble())
            val maxRadius = if (p.emphasis > 1) max(20.0, lineHeight * 0.5) else maxOf(lineHeight * 2.2, p.cw * 0.75, 56.0)
            var px = if (p.emphasis > 1) -p.width / 2 else p.baseX
            var py = if (p.emphasis > 1) 0.0 else p.baseY
            if (!isInterlude && p.emphasis <= 1 && heroCx != null && heroCy != null && heroW != null) {
                var dx = px + p.width / 2 - heroCx
                var dy = py - p.height * 0.46 - heroCy
                val dist = hypot(dx, dy)
                if (dist < 1) {
                    dx = if (px >= 0) 1.0 else -1.0
                    dy = if (p.lineIndex % 2 == 0) -0.65 else 0.65
                }
                val minSep = heroW * 0.34 + p.width * 0.52 + p.padding * 2
                if (dist < minSep) {
                    val n = max(hypot(dx, dy), 1.0)
                    val push = minSep - dist
                    px += dx / n * push
                    py += dy / n * push * 0.92
                }
            }
            // 手机化：Folia 允许超出排版宽 72px；窄屏上这会把词推出屏幕，所以再夹到视口内 12dp
            // （碰撞盒已含 1.26×/1.48× 放大余量）。
            val hMin = max(-maxWidth / 2 - 72, -viewportWidth / 2 + 12)
            val hMax = min(maxWidth / 2 + 72, viewportWidth / 2 - 12)
            val vMin = -max(totalHeight * 0.9, lineHeight * 1.6)
            val vMax = max(totalHeight * 0.9, lineHeight * 1.45)
            var cx = px
            var cy = py
            var found = false
            var bestX = px
            var bestY = py
            var bestScore = Double.POSITIVE_INFINITY
            val baseAngle = if (heroCx != null && p.emphasis <= 1) atan2(py, px + p.width / 2) else 0.0
            var radius = 0.0
            while (radius <= maxRadius && !found) {
                val n = if (radius == 0.0) 1 else if (p.emphasis > 1) 8 else max(12, ((PI * 2 * radius) / max(step * 1.1, 10.0)).roundToInt())
                for (i in 0 until n) {
                    val (dx, dy) = if (radius == 0.0) 0.0 to 0.0 else {
                        val a = baseAngle + i.toDouble() / n * PI * 2
                        cos(a) * radius to sin(a) * radius * (if (p.emphasis > 1) 0.8 else 0.92)
                    }
                    val left = px + dx - p.padding
                    val top = py + dy - p.ch - p.padding
                    val right = left + p.cw + p.padding * 2
                    val bottom = top + p.ch + p.padding * 2
                    if (left < hMin || right > hMax || top < vMin || bottom > vMax) continue
                    val (hit, area) = collide(left, top, right, bottom)
                    val score = area * 2.2 + hypot(dx, dy)
                    if (score < bestScore) { bestScore = score; bestX = px + dx; bestY = py + dy }
                    if (!hit) {
                        cx = px + dx; cy = py + dy
                        push(cx, cy, p.cw, p.ch, p.padding)
                        found = true
                        break
                    }
                }
                radius += step
            }
            if (!found) {
                cx = bestX; cy = bestY
                push(cx, cy, p.cw, p.ch, p.padding)
            }
            val ox = cx + p.width / 2
            val oy = cy - p.height * 0.46
            val ol = max(hypot(ox, oy), 1.0)
            val drift = when {
                isInterlude -> 3 + rnd(6.0) * 3
                p.emphasis > 1 -> 4 + rnd(6.0) * 4
                intensity == AnimationIntensity.CHAOTIC -> 8 + rnd(6.0) * 9
                else -> 5 + rnd(6.0) * 6
            }
            out.add(
                CadenzaPlacement(
                    id = "${f.word.text}-${f.wordIndex}-${p.lineIndex}-${p.fragmentIndex}-${f.indexInWord}",
                    wordIndex = f.wordIndex,
                    word = f.word,
                    text = f.text,
                    x = cx,
                    y = cy,
                    width = p.width,
                    height = p.height,
                    rotate = 0.0,
                    scale = p.scale,
                    passedRotate = passedRotate,
                    passedDriftX = ox / ol * drift + (rnd(7.0) - 0.5) * 2.4,
                    passedDriftY = oy / ol * drift * 0.72 + (rnd(8.0) - 0.5) * 2,
                    fragmentStartInWord = f.startInWord,
                    wordGraphemeCount = f.wordGraphemeCount,
                    wordGraphemeTimings = f.timings,
                    emphasis = p.emphasis,
                    isInterlude = isInterlude,
                ),
            )
        }
        return out
    }

    // ---------------- VisualizerCadenza.tsx 每帧目标 ----------------

    /** 一帧推进（draw() 里的目标值计算 + 指数平滑）。返回更新后的状态与该落点当前状态。 */
    fun step(
        state: CadenzaAnimState?,
        placement: CadenzaPlacement,
        placementIndex: Int,
        status: CadenzaStatus,
        time: Double,
        dt: Double,
        lt: CadenzaLineTiming,
        width: Double,
        focusY: Double,
        motionEnergy: Double,
        tuning: CadenzaTuning,
        intensity: AnimationIntensity,
    ): CadenzaAnimState {
        val instant = lt.wordRevealMode == WordRevealMode.INSTANT
        val passedAlpha = if (instant) 0.0 else if (intensity == AnimationIntensity.CHAOTIC) 0.9 else 0.82
        val pulse = if (status == CadenzaStatus.ACTIVE && !instant) {
            1 + sin(time * ACTIVE_PULSE_FREQUENCY + placement.word.startTime * 5) * 0.04 * tuning.motionAmount
        } else 1.0
        val drift = if (instant) 0.0 else passedDrift(time, placement.word)
        val targetScale = when (status) {
            CadenzaStatus.WAITING -> if (instant) placement.scale else max(placement.scale * 0.5, 0.5)
            CadenzaStatus.ACTIVE -> if (instant) placement.scale else placement.scale * 1.3 * pulse
            else -> placement.scale
        }
        val targetRotation = when (status) {
            CadenzaStatus.WAITING -> if (instant) placement.rotate else placement.rotate + 20
            CadenzaStatus.PASSED -> if (instant) placement.rotate else placement.rotate + placement.passedRotate * drift
            else -> placement.rotate
        }
        val fx = sin(time * 1.2 + placementIndex * 0.6) * motionEnergy * 4
        val fy = cos(time * 1.5 + placementIndex * 0.4) * motionEnergy * 2.5
        val dX = if (status == CadenzaStatus.PASSED) placement.passedDriftX * drift else 0.0
        val dY = if (status == CadenzaStatus.PASSED) placement.passedDriftY * drift else 0.0
        val tx = width / 2 + placement.x + fx + dX
        val ty = focusY + placement.y + fy + dY
        val tAlpha = when (status) { CadenzaStatus.WAITING -> 0.0; CadenzaStatus.ACTIVE -> 1.0; else -> passedAlpha }
        val tBlur = if (status == CadenzaStatus.WAITING && !instant) 10.0 else 0.0
        val tMix = bodyMix(time, lt, placement.word)
        val tGlow = glowEnvelope(time, lt, placement.word)
        val a1 = 1 - exp(-11 * dt)
        val a2 = 1 - exp(-14 * dt)
        val a3 = 1 - exp(-16 * dt)
        val initActive = instant && time >= placement.word.startTime
        val s = state ?: CadenzaAnimState(
            x = width / 2 + placement.x,
            y = focusY + placement.y,
            rotation = if (initActive) targetRotation else targetRotation + 16,
            scale = if (initActive) targetScale else max(placement.scale * 0.5, 0.5),
            bodyAlpha = if (initActive) tAlpha else 0.0,
            blur = if (initActive) tBlur else 10.0,
            activeMix = if (initActive) tMix else 0.0,
            glowAlpha = if (initActive) tGlow else 0.0,
        )
        s.x = mix(s.x, tx, a1); s.y = mix(s.y, ty, a1)
        s.rotation = mix(s.rotation, targetRotation, a1); s.scale = mix(s.scale, targetScale, a1)
        s.bodyAlpha = mix(s.bodyAlpha, tAlpha, a2); s.blur = mix(s.blur, tBlur, a2)
        s.activeMix = mix(s.activeMix, tMix, a2); s.glowAlpha = mix(s.glowAlpha, tGlow, a3)
        return s
    }
}
