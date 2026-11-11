package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * R7 花火⑥c：逐函数对译 Folia hanabi/scene.ts（场景单元装配）+ program.ts 的 sliceLine / shotLine + hanabiSceneEntry.ts 的单元参数。
 * 一个段落 = 一个单元：世界整体上升，每个镜头在世界里各自高度上组装主烟花 / 被唱到的小烟花 / 升空尾迹 / 字底色块 / 字。
 * 烟花事件构建时按镜头算好（纯数据，planShotEvents），每帧只按 t 求值。
 * 环境层见 HanabiAmbient.kt。本轮未接：MG 装饰件 ornaments、PV 拼贴 collage、随拍开花 beatFx、片尾卡（见 HANDOFF）。
 */

/** HanabiTuning 里舞台用到的几项（Folia DEFAULT_HANABI_TUNING）。 */
internal class HbTuning(
    val sparkAmount: Double = 1.0,
    val textMotion: Double = 1.0,
    val blockOpacity: Double = 1.0,
    val riseSpeed: Double = 1.0,
    val spin: Double = 1.0,
    val launches: Boolean = true,
    val sky: Double = 0.85,
    val ambient: Double = 1.0,
    /** 烟花层辉光 / 整体辉光 / 副歌辉光（Folia DEFAULT_HANABI_TUNING：1 / 0.8 / 1.3），见 HanabiBloom.kt。 */
    val glow: Double = 1.0,
    val bloom: Double = 0.8,
    val chorusBloom: Double = 1.3,
)

/** 整首歌的旋转方向（按程序种子定）。 */
internal fun hbSpinDirection(programSeed: String): Int =
    if ((lumiereHash("$programSeed:spin-direction").toLong() and 0xffffffffL) % 2 == 0L) 1 else -1

private class HbLocatedWord(val word: Word, val start: Int, val end: Int)

private fun locateWords(line: Line): List<HbLocatedWord>? {
    val text = line.fullText
    val out = ArrayList<HbLocatedWord>()
    var cursor = 0
    for (w in line.words) {
        if (w.text.isEmpty()) continue
        val i = text.indexOf(w.text, cursor)
        if (i < 0) return null
        out += HbLocatedWord(w, i, i + w.text.length)
        cursor = i + w.text.length
    }
    return if (out.isNotEmpty()) out else null
}

/** sliceLine：一行在 [start, end) 这段文字上的切片，词按偏移裁开、时间按比例分配；整行时原样返回。 */
internal fun hbSliceLine(line: Line, startOffset: Int, endOffset: Int): Line {
    val text = line.fullText
    val start = max(0, min(startOffset, text.length))
    val end = max(start, min(endOffset, text.length))
    if (start == 0 && end == text.length) return line
    val located = locateWords(line) ?: listOf(HbLocatedWord(Word(text, line.startTime, line.endTime), 0, text.length))
    val words = ArrayList<Word>()
    for (l in located) {
        val from = max(l.start, start); val to = min(l.end, end)
        if (to <= from) continue
        val span = max(l.end - l.start, 1)
        val d = l.word.endTime - l.word.startTime
        words += Word(text.substring(from, to), l.word.startTime + d * ((from - l.start).toDouble() / span), l.word.startTime + d * ((to - l.start).toDouble() / span))
    }
    val fullText = text.substring(start, end)
    if (words.isEmpty()) {
        val length = max(text.length, 1)
        fun timeAt(o: Int) = line.startTime + (line.endTime - line.startTime) * (o.toDouble() / length)
        words += Word(fullText, timeAt(start), timeAt(end))
    }
    return line.copy(fullText = fullText, words = words, startTime = words.first().startTime, endTime = words.last().endTime, translation = null,
        wordSegments = null, autoWordSegments = null)
}

/** shotLine：切片拼成一行（跨行的切片之间加一个空格）；全空返回 null。 */
internal fun hbShotLine(lines: Map<Int, Line>, slices: List<HbSlice>): Line? {
    val parts = slices.mapNotNull { s -> lines[s.lineIndex]?.let { hbSliceLine(it, s.startOffset, s.endOffset) } }.filter { it.fullText.trim().isNotEmpty() }
    if (parts.isEmpty()) return null
    if (parts.size == 1) return parts[0]
    val words = ArrayList<Word>()
    parts.forEachIndexed { i, p ->
        if (i > 0) { val gap = words.last().endTime; words += Word(" ", gap, max(gap, p.startTime)) }
        words += p.words
    }
    return parts[0].copy(fullText = words.joinToString("") { it.text }, words = words, startTime = parts[0].startTime, endTime = parts.maxOf { it.endTime },
        wordSegments = null, autoWordSegments = null)
}

internal class HbSceneShot(val id: String, val profile: HbProfile, val startTime: Double, val endTime: Double, val isBridge: Boolean, val line: Line?)

internal class HbBuiltShot(
    val shot: HbSceneShot,
    val timing: HbShotTiming,
    val palette: HbPalette,
    val text: HbText?,
    val bursts: List<HbBurstEvent>,
    val launches: List<HbLaunchEvent>,
    val lifeStart: Double,
    val lifeEnd: Double,
) {
    /** 烟花方案按事件缓存（同 fireworks.ts 的 plans）。 */
    val plans = HashMap<HbBurstEvent, HbBurstPlan>()
}

private const val MAX_SUNG_BURSTS = 28
private const val MAX_DISSOLVE_BURSTS = 24
private val DISSOLVE_SPARK = HbBurstSpec("botan", 10.0, 0.03, 0.65, listOf(0, 1, 2), 0.25, 0.6)
private val BRIDGE_KINDS = listOf("mine", "senrin", "kiku", "botan", "ring", "crossette", "willow-ring", "strobe")
internal const val HB_SHOT_PARTICLES = 750

private fun <T> thin(items: List<T>, maxN: Int): List<T> {
    if (items.size <= maxN) return items
    val step = items.size.toDouble() / maxN
    return List(maxN) { k -> items[floor(k * step).toInt()] }
}

internal class HbScene(
    val width: Double,
    val height: Double,
    val seed: String,
    theme: HbTheme,
    val tuning: HbTuning,
    shots: List<HbSceneShot>,
    val startTime: Double,
    val endTime: Double,
    exitStart: Double?,
    spinDirection: Int,
    fontScale: Double,
    measure: (String) -> Double,
    motion: Double = 1.0,
) {
    val unit = min(width, height)
    val timeline: HbTimeline
    val built: List<HbBuiltShot>
    /** 环境层（整单元一份）：星点、薄烟、远方小烟花、火星、散景。 */
    val ambient: HbAmbient?

    init {
        val paletteCache = HashMap<String, HbPalette>()
        fun paletteOf(p: HbProfile) = paletteCache.getOrPut(p.palette) { resolveHanabiPalette(p.palette, theme, "mixed") }
        val tlShots = shots.map { s ->
            val words = (s.line?.words ?: emptyList()).filter { it.text.trim().isNotEmpty() }
            val first = if (words.isEmpty()) s.line?.startTime else words[0].startTime
            val last = if (words.isEmpty()) s.line?.endTime else words.maxOf { it.endTime }
            HbTimelineShot(s.startTime, s.endTime, if (s.isBridge) null else first, if (s.isBridge) null else last, s.isBridge,
                s.profile.rise, s.profile.push, s.profile.roll, s.profile.punch)
        }
        val lead = shots.firstOrNull()
        val distantRate = shots.sumOf { when (it.profile.mood) { "quiet" -> 0.08; "loud" -> 0.35; else -> 0.2 } } / max(shots.size, 1)
        ambient = if (lead != null && tuning.ambient > 0) HbAmbient("$seed:ambient", paletteOf(lead.profile), width, height, startTime, endTime, min(2.0, tuning.ambient), distantRate) else null
        val spinRng = lumiereRng("$seed:spin")
        val (angles, drift) = hbPlanSpin(shots.map { it.endTime - it.startTime }, max(0.0, tuning.spin), spinDirection) { spinRng.next() }
        timeline = hbBuildTimeline(tlShots, endTime, height, max(0.0, tuning.riseSpeed), motion * max(0.0, tuning.textMotion), exitStart, angles, drift)
        built = shots.mapIndexed { index, shot ->
            val profile = shot.profile
            val timing = timeline.shots[index]
            val palette = paletteOf(profile)
            val region = profile.region
            val text = if (shot.line != null && !shot.isBridge) HbText(HbTextOptions(
                width, height, shot.line, region.w * width, region.h * height,
                HbTextSpec(profile.text.layout, profile.text.heroSize * fontScale, profile.text.emphasis, profile.text.tilt, profile.text.block,
                    profile.text.enter, profile.text.sing, profile.text.exit, profile.text.stagger, profile.text.outline, profile.text.hardShadow),
                palette, "$seed:${shot.id}:text", timing.enterAt, timing.exitAt, timing.exitDuration,
                max(0.0, tuning.textMotion), min(1.0, max(0.0, tuning.blockOpacity)), measure,
            )) else null
            val (bursts, launches) = planShotEvents(shot, index, timing, palette, text, region)
            val burstEnd = bursts.maxOfOrNull { it.at + it.spec.duration } ?: Double.NEGATIVE_INFINITY
            val launchEnd = launches.maxOfOrNull { it.at + it.duration + 0.7 } ?: Double.NEGATIVE_INFINITY
            HbBuiltShot(shot, timing, palette, text, bursts, launches,
                min(timing.launchAt, timing.enterAt) - 0.25,
                maxOf(timing.exitAt + timing.exitDuration + 0.3, burstEnd + 0.05, launchEnd))
        }
    }

    /** 画面坐标 (sx, sy) 在 time 时刻对应的镜头容器坐标（不计镜头内运镜）。 */
    private fun screenToShot(region: HbRegion, timing: HbShotTiming, sx: Double, sy: Double, time: Double): Pair<Double, Double> =
        (sx - region.cx * width) to (sy - (region.cy * height + timing.worldY + timeline.rise(time)))

    private fun shape(spec: HbBurstSpec, random: () -> Double): Pair<Double?, Double?> =
        if (spec.kind == "ring" || spec.kind == "willow-ring" || spec.kind == "heart" || spec.kind == "star") {
            val squash = if (spec.kind == "ring") 0.4 + random() * 0.5 else 1.0
            squash to (random() - 0.5) * 0.6
        } else null to null

    private fun planShotEvents(shot: HbSceneShot, index: Int, timing: HbShotTiming, palette: HbPalette, text: HbText?, region: HbRegion): Pair<List<HbBurstEvent>, List<HbLaunchEvent>> {
        val profile = shot.profile
        val rng = lumiereRng("$seed:${shot.id}:$index:events")
        val random = { rng.next() }
        val bursts = ArrayList<HbBurstEvent>()
        val launches = ArrayList<HbLaunchEvent>()
        fun locate(sx: Double, sy: Double, time: Double) = screenToShot(region, timing, sx, sy, time)
        val withLaunch = profile.launch && tuning.launches
        fun launchTo(x: Double, y: Double, arrive: Double, key: String) {
            val at = arrive - HB_LAUNCH_DURATION
            val bottom = locate(0.0, height * 1.03, at).second
            launches += HbLaunchEvent(x + (random() - 0.5) * width * 0.08, max(bottom, y + height * 0.2), x, y, at, HB_LAUNCH_DURATION, palette.glow, "$seed:${shot.id}:$key:launch")
        }
        if (shot.isBridge) {
            val from = timing.start + 0.15
            val to = timing.end - 0.3
            val duration = max(0.0, to - from)
            val count = max(1, min(10, floor(duration / 0.75 + 0.5).toInt()))
            val kinds = if (profile.burst != null) listOf(profile.burst.kind) + BRIDGE_KINDS else BRIDGE_KINDS
            for (k in 0 until count) {
                val at = from + (if (count == 1) 0.0 else (k.toDouble() / (count - 1)) * duration * 0.85) + random() * 0.12
                val kind = kinds[floor(random() * kinds.size).toInt()]
                val spec = hbBurstPreset(kind, listOf(k % 5, (k + 1) % 5, (k + 2) % 5))
                if (kind == "mine") {
                    val p = locate(width * (0.2 + random() * 0.6), height * 0.94, at)
                    bursts += HbBurstEvent(p.first, p.second, at, spec, "$seed:${shot.id}:b$k", 0.8 + random() * 0.3)
                    continue
                }
                val p = locate(width * (0.15 + random() * 0.7), height * (0.14 + random() * 0.34), at)
                val scale = 0.55 + random() * 0.4
                val (squash, rot) = shape(spec, random)
                bursts += HbBurstEvent(p.first, p.second, at, spec, "$seed:${shot.id}:b$k", scale, squash, rot)
                if (tuning.launches) launchTo(p.first, p.second, at, "b$k")
            }
            return bursts.sortedBy { it.at } to launches
        }
        val b = text?.bounds ?: HbBounds(-region.w * width / 2, -region.h * height / 2, region.w * width, region.h * height)
        val centerX = b.x + b.width / 2
        val centerY = b.y + b.height / 2
        val main = profile.burst
        if (main != null) {
            val radius = main.radius * unit
            val at = timing.enterAt
            val key = "$seed:${shot.id}:main"
            val minY = height * 0.12 - region.cy * height
            val maxX = width * 0.42
            class P(val x: Double, val y: Double, val at: Double)
            val points = ArrayList<P>()
            if (main.kind == "mine") {
                val sx = if (profile.burstAt == "sides") listOf(0.25, 0.75) else listOf(0.3 + random() * 0.4)
                sx.forEachIndexed { k, x -> points += P(x * width - region.cx * width, height * 0.94 - region.cy * height, at + k * 0.15) }
            } else if (profile.burstAt == "behind") {
                points += P(centerX + (random() - 0.5) * b.width * 0.15, centerY - radius * 0.15, at)
            } else if (profile.burstAt == "above") {
                points += P(centerX + (random() - 0.5) * b.width * 0.3, max(minY, b.y - radius * 0.45), at)
            } else if (profile.burstAt == "sides") {
                val dx = min(maxX, b.width / 2 + radius * 0.35)
                points += P(centerX - dx, max(minY, centerY - radius * 0.3), at)
                points += P(centerX + dx, max(minY, centerY - radius * 0.3), at + 0.15)
            } else {
                val settled = timing.enterAt + 0.6
                val p = locate(width * (0.2 + random() * 0.6), height * (0.12 + random() * 0.22), settled)
                points += P(p.first, p.second, at)
            }
            points.forEachIndexed { k, pt ->
                val x = max(-region.cx * width + width * 0.06, min(width * (1 - region.cx) - width * 0.06, pt.x))
                val (squash, rot) = shape(main, random)
                bursts += HbBurstEvent(x, pt.y, pt.at, main, "$key$k", 1.0, squash, rot)
                if (withLaunch && main.kind != "mine") launchTo(x, pt.y, pt.at, "main$k")
            }
        }
        if (text != null) {
            val glyphs = text.anchors(timing.enterAt).mapIndexed { k, a -> k to a }.filter { !it.second.blank }
            val sung = profile.sungBurst
            if (sung != null) {
                val candidates = glyphs.filter { it.second.sungAt >= timing.enterAt && it.second.sungAt < timing.exitAt }
                thin(candidates, MAX_SUNG_BURSTS).forEachIndexed { n, (k, anchor) ->
                    val now = text.anchors(anchor.sungAt).getOrNull(k) ?: anchor
                    val colors = if (sung.colors.isNotEmpty()) sung.colors.indices.map { j -> sung.colors[(j + n) % sung.colors.size] } else listOf(n)
                    bursts += HbBurstEvent(now.x, now.y - now.size * 0.1, anchor.sungAt,
                        HbBurstSpec(sung.kind, sung.count, sung.radius, sung.duration, colors, sung.trail, sung.twinkle),
                        "$seed:${shot.id}:sung$k", max(0.6, min(1.6, now.size / (unit * 0.1))))
                }
            }
            if (profile.text.exit == "dissolve") {
                val chosen = thin(glyphs, MAX_DISSOLVE_BURSTS)
                val step = min(0.05, (timing.exitDuration * 0.6) / max(chosen.size, 1))
                chosen.forEachIndexed { n, (k, _) ->
                    val at = timing.exitAt + n * step
                    val now = text.anchors(at).getOrNull(k) ?: return@forEachIndexed
                    bursts += HbBurstEvent(now.x, now.y, at, DISSOLVE_SPARK, "$seed:${shot.id}:dissolve$k", 1.0)
                }
            }
        }
        return bursts.sortedBy { it.at } to launches
    }

    fun planOf(item: HbBuiltShot, event: HbBurstEvent): HbBurstPlan =
        item.plans.getOrPut(event) { hbPlanBurst(event, item.palette.sparks, unit, max(0.0, tuning.sparkAmount)) }
}

/** launchDemand（mg/burstPlan.ts）：这条升空尾迹此刻要几个精灵。 */
internal fun hbLaunchDemand(l: HbLaunchEvent, time: Double): Int {
    val age = time - l.at
    if (age < 0 || time > hbLaunchEnd(l)) return 0
    val embers = floor(min(age, HB_LAUNCH_EMBER_LIFE) / 0.032).toInt() + 1
    return embers + (if (age <= l.duration) 5 + 2 else 0)
}

/** buildHanabiSceneEntry：编译出的段落 → 场景单元。 */
internal fun hbBuildSceneUnit(
    program: HbProgram, index: Int, lines: List<Line>, width: Double, height: Double, theme: HbTheme, tuning: HbTuning,
    fontScale: Double, measure: (String) -> Double,
): HbScene {
    val paragraph = program.paragraphs[index]
    val lineMap = paragraph.lineIndices.associateWith { lines[it] }
    val shots = ArrayList(paragraph.shots.map { s ->
        HbSceneShot(s.id, hanabiProfileOf(s.kind), s.startTime, s.endTime, s.isBridge, if (s.isBridge) null else hbShotLine(lineMap, s.slices))
    })
    if (shots.isEmpty()) shots += HbSceneShot(paragraph.id, hanabiProfileOf(""), paragraph.startTime, paragraph.endTime, true, null)
    return HbScene(width, height, "${program.seed}:${paragraph.id}", theme, tuning, shots, paragraph.startTime, paragraph.endTime,
        paragraph.transitionOut?.startTime, hbSpinDirection(program.seed), fontScale, measure)
}
