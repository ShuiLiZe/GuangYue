// 町景（machi）的编排：逐函数对译 Folia v0.7.25 src/components/visualizer/machi/engine/director.ts（纯函数，三个场景共用）。
// 歌词屏此刻该亮哪一行还是曲名卡、跑马灯写什么、副歌权重、副歌开头的闪电、招牌的闪烁日程、音频包络。
// 牌面、副歌、闪电跟歌曲时间走（拖进度也对得上），闪烁跟氛围时钟走。金标 machi_board_golden.txt（docs/port/scripts/machi-board-golden-gen.js）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 牌面：第 index 行，或曲名卡（index = -1）。对应 MachiBoardTarget。 */
internal data class MachiBoardTarget(val index: Int) {
    val isCard get() = index < 0
    companion object { val CARD = MachiBoardTarget(-1) }
}

/** 一行唱完后，下一行还没来时牌面再留多久才换成曲名卡。 */
private const val BOARD_HOLD_SECONDS = 2.6
/** 牌面比歌词略早一点亮出来，翻牌动画落在唱起的那一刻。 */
private const val BOARD_LEAD_SECONDS = 0.12

/** parserCore.ts INTERLUDE_FULL_TEXT。 */
private fun machiIsInterlude(line: Line) = line.fullText == "......"

/** 时刻 time 的牌面：在唱或刚唱完的那一行；间奏、开唱前、长空隙里是曲名卡。 */
internal fun resolveMachiBoardTarget(lines: List<Line>, time: Double): MachiBoardTarget {
    var index = -1
    for (i in lines.indices) {
        if (lines[i].startTime <= time + BOARD_LEAD_SECONDS) index = i else break
    }
    if (index < 0) return MachiBoardTarget.CARD
    val line = lines[index]
    if (machiIsInterlude(line) || line.fullText.trim().isEmpty()) return MachiBoardTarget.CARD
    val next = lines.getOrNull(index + 1)
    val hold = RenderHints.getLineRenderEndTime(line) + BOARD_HOLD_SECONDS
    if (time > hold && (next == null || next.startTime - BOARD_LEAD_SECONDS > time)) return MachiBoardTarget.CARD
    return MachiBoardTarget(index)
}

internal fun sameMachiBoardTarget(a: MachiBoardTarget?, b: MachiBoardTarget) = a != null && a.index == b.index

private val CHORUS_PART = Regex("chorus|副歌", RegexOption.IGNORE_CASE)

internal fun isMachiChorusLine(line: Line?) = line != null && (line.isChorus || CHORUS_PART.containsMatchIn(line.songPart ?: ""))

/** 副歌权重：正在唱副歌行时为 1。运行时再做平滑。 */
internal fun resolveMachiChorus(lines: List<Line>, target: MachiBoardTarget): Double =
    if (target.isCard) 0.0 else if (isMachiChorusLine(lines.getOrNull(target.index))) 1.0 else 0.0

// —— 确定性散列（visualizerSeedHash.ts）————————————————————————————

/** FNV-1a 32 位（按 UTF-16 码元），返回 0..2^32-1。 */
internal fun machiHashSeed(value: String): Long {
    var hash = 2166136261L.toInt()
    for (ch in value) {
        hash = hash xor ch.code
        hash *= 16777619
    }
    return hash.toLong() and 0xffffffffL
}

/** mixVisualizerSeed(seed + imul(index + 1, 97), salt) / 2^32。 */
internal fun machiHash01(seed: Long, index: Int, salt: Int): Double {
    val s = (seed + (index + 1).toLong() * 97L).toInt()
    val mixed = ((s xor salt) * 2654435761L.toInt()).toLong() and 0xffffffffL
    return mixed / 4294967296.0
}

/** 招牌闪烁：每 6.5 秒一个窗口，按哈希决定这个窗口里有没有一阵快速的明暗（三下），返回亮度倍数 0.5..1。 */
internal fun resolveMachiFlicker(time: Double, seed: String, rate: Double = 1.0): Double {
    val window = 6.5 / max(0.3, rate)
    val index = floor(time / window).toInt()
    val key = machiHashSeed("flicker:$seed")
    if (machiHash01(key, index, 0x51) > 0.42) return 1.0
    val offset = machiHash01(key, index, 0x7a) * (window - 0.6)
    val local = time - index * window - offset
    if (local < 0 || local > 0.46) return 1.0
    // 三下：越来越短的暗。
    for (d in MACHI_DIPS) if (local >= d[0] && local <= d[1]) return d[2]
    return 1.0
}

private val MACHI_DIPS = arrayOf(doubleArrayOf(0.0, 0.07, 0.5), doubleArrayOf(0.15, 0.2, 0.68), doubleArrayOf(0.32, 0.36, 0.8))

/** 音频包络：一见到大于 1 的值就按 0..255 归一化，否则当 0..1；起音快、释放慢。 */
internal class MachiAudioFollower {
    var value = 0.0
    private var byteScale = false

    fun step(raw: Double, deltaSeconds: Double, attack: Double = 0.06, release: Double = 0.25): Double {
        if (raw > 1.0001) byteScale = true
        val target = max(0.0, min(1.0, if (byteScale) raw / 255 else raw))
        val tau = if (target > value) attack else release
        val k = if (deltaSeconds <= 0) 0.0 else 1 - exp(-deltaSeconds / tau)
        value += (target - value) * k
        return value
    }
}

// —— 闪电 ————————————————————————————————————————————————————————————

/** 每段副歌的开头（相邻副歌行算一段）。 */
internal fun buildMachiLightningTimes(lines: List<Line>): List<Double> =
    lines.filterIndexed { i, line -> isMachiChorusLine(line) && !isMachiChorusLine(lines.getOrNull(i - 1)) }.map { it.startTime }

/** 闪电亮度 0..1：两下闪，第二下略弱，一秒内收尽。 */
internal fun resolveMachiLightning(times: List<Double>, time: Double): Double {
    var since = Double.POSITIVE_INFINITY
    for (i in times.indices.reversed()) {
        if (times[i] <= time) { since = time - times[i]; break }
    }
    if (since > 1.2) return 0.0
    fun flash(at: Double, width: Double, height: Double) = height * exp(-((since - at) * (since - at)) / (width * width))
    return min(1.0, flash(0.04, 0.05, 1.0) + flash(0.24, 0.06, 0.7) + 0.25 * exp(-since * 3.5))
}

/** 跑马灯（LED 字条、出租车顶灯）写什么：下一句；没有下一句时用这一句的译文；曲名卡时是曲名与歌手。 */
internal fun resolveMachiTickerText(target: MachiBoardTarget, lines: List<Line>, title: String, artist: String, fallbackTitle: String): String {
    val t = title.ifEmpty { fallbackTitle }
    if (target.isCard) return "♪ $t${if (artist.isNotEmpty()) " — $artist" else ""}   ◆   OPEN 24H   ◆"
    val next = lines.drop(target.index + 1).firstOrNull { it.fullText.trim().isNotEmpty() }
    val current = lines.getOrNull(target.index)
    val parts = listOfNotNull(
        next?.let { "NEXT ▸ ${it.fullText}" },
        current?.translation?.takeIf { it.isNotEmpty() }?.let { "◆ $it" },
    )
    return if (parts.isNotEmpty()) "${parts.joinToString("   ")}   " else "♪ $t   ◆   "
}

// —— director.ts 闪电 ——————————————————————————————————————————————
/** 闪电的时刻：每段副歌的第一句（前一句不是副歌）开始时。 */
internal fun buildMachiLightningTimes(lines: List<Line>): List<Double> =
    lines.indices.filter { isMachiChorusLine(lines[it]) && !isMachiChorusLine(lines.getOrNull(it - 1)) }.map { lines[it].startTime }

/** 闪电亮度 0..1：两下闪，第二下略弱，一秒内收尽。 */
internal fun resolveMachiLightning(times: List<Double>, time: Double): Double {
    var since = Double.POSITIVE_INFINITY
    for (i in times.indices.reversed()) {
        if (times[i] <= time) { since = time - times[i]; break }
    }
    if (since > 1.2) return 0.0
    fun flash(at: Double, width: Double, height: Double) = height * exp(-((since - at) * (since - at)) / (width * width))
    return min(1.0, flash(0.04, 0.05, 1.0) + flash(0.24, 0.06, 0.7) + 0.25 * exp(-since * 3.5))
}
