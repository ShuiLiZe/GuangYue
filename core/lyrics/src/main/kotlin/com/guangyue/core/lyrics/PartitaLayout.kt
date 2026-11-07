// 对应 Folia 文件：src/components/visualizer/partita/partitaLayout.ts、tuning.ts、src/types.ts（PartitaTuning）
//
// V2a「云阶」(partita) 的纯逻辑：把当前行切成不均匀的 chunk（一行可见 row），
// 每个 chunk 左右交错（stagger），chunk 内的词再加一点随机偏移。数值逐条照抄 Folia。
package com.guangyue.core.lyrics

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** 对应 Folia PartitaTuning / DEFAULT_PARTITA_TUNING。 */
data class PartitaTuning(
    val showGuideLines: Boolean = true,
    val useSemanticLayout: Boolean = true,
    val staggerMin: Double = 20.0,
    val staggerMax: Double = 100.0,
) {
    companion object {
        val DEFAULT = PartitaTuning()

        private fun stagger(v: Double?, fallback: Double) =
            if (v == null || !v.isFinite()) fallback else v.coerceIn(0.0, 180.0)

        /** 对应 tuning.ts 的 resolve：两个错落值钳到 0..180，小的在前。 */
        fun sanitize(
            showGuideLines: Boolean? = null,
            useSemanticLayout: Boolean? = null,
            staggerMin: Double? = null,
            staggerMax: Double? = null,
        ): PartitaTuning {
            val a = stagger(staggerMin, DEFAULT.staggerMin)
            val b = stagger(staggerMax, DEFAULT.staggerMax)
            return PartitaTuning(
                showGuideLines = showGuideLines ?: DEFAULT.showGuideLines,
                useSemanticLayout = useSemanticLayout ?: DEFAULT.useSemanticLayout,
                staggerMin = minOf(a, b),
                staggerMax = maxOf(a, b),
            )
        }
    }
}

/** 一个 chunk（一行可见 row）的变换。对应 partitaLayout.ts 的 WordLayoutConfig（chunk 级）。 */
data class PartitaChunkConfig(
    val id: String,
    val x: Double,
    val y: Double,
    val rotate: Double,
    val scale: Double,
    /** 下边距（rem）：chaotic 0.4，其余 0.6。 */
    val marginBottomRem: Double,
    val passedRotate: Double,
)

data class PartitaChunk(
    val units: List<LyricLayoutUnit>,
    val chunkWords: List<Word>,
    val displayWords: List<Word>,
    val config: PartitaChunkConfig,
    val rowIndex: Int,
)

data class PartitaLayout(
    val chunks: List<PartitaChunk>,
    val totalGraphemes: Int,
    val perspective: Double,
) {
    companion object {
        val EMPTY = PartitaLayout(emptyList(), 0, 1000.0)
    }
}

object PartitaLayoutBuilder {

    private val WS = Regex("\\s+")

    /** 对应 buildSequentialColumns。[windowHeight] 用 Folia 的 window.innerHeight（dp）。 */
    fun build(line: Line, intensity: AnimationIntensity, windowHeight: Double, tuning: PartitaTuning): PartitaLayout {
        val isChaotic = intensity == AnimationIntensity.CHAOTIC
        val isCalm = intensity == AnimationIntensity.CALM
        val totalGraphemes = maxOf(
            GraphemeTimings.splitLyricGraphemes(line.fullText.replace(WS, "")).size,
            line.words.size,
            1,
        )
        val units = PostLyricLayout.build(line, semantic = tuning.useSemanticLayout, sticky = true)
        if (units.isEmpty()) return PartitaLayout(emptyList(), totalGraphemes, if (isChaotic) 720.0 else 1000.0)

        val baseRowHeight = 100.0
        val targetRowCount = maxOf(1, floor(windowHeight * 0.65 / baseRowHeight).toInt())
        val actualRowCount = minOf(units.size, targetRowCount)

        var seed = line.startTime
        val random = {
            val x = sin(seed) * 10000
            seed += 1.0
            x - floor(x)
        }

        val chunks = ArrayList<List<LyricLayoutUnit>>()
        var remainingUnits = units.size
        var remainingChunks = actualRowCount
        var unitIndex = 0
        for (c in 0 until actualRowCount) {
            val isLast = c == actualRowCount - 1
            val avg = remainingUnits.toDouble() / remainingChunks
            var len = if (isLast) {
                remainingUnits
            } else {
                val max = ceil(avg * 1.5).toInt()
                val r = random()
                maxOf(1, minOf(max, jsRound(avg + (r - 0.5) * avg)))
            }
            len = maxOf(1, minOf(len, remainingUnits - (remainingChunks - 1)))
            chunks.add(units.subList(unitIndex, unitIndex + len).toList())
            unitIndex += len
            remainingUnits -= len
            remainingChunks -= 1
        }

        val out = ArrayList<PartitaChunk>()
        chunks.forEachIndexed { rowIndex, chunkUnits ->
            val chunkWords = chunkUnits.flatMap { it.words }
            val display = PostLyricLayout.displayWords(chunkUnits)
            if (chunkWords.isEmpty()) return@forEachIndexed
            val merged = chunkWords.joinToString("") { it.text.trim() }
            val graphemeCount = GraphemeTimings.splitLyricGraphemes(merged.replace(WS, "")).size
            val rowBias = rowIndex - (actualRowCount - 1) / 2.0
            val left = rowIndex % 2 == 0
            val magnitude = tuning.staggerMin + random() * maxOf(tuning.staggerMax - tuning.staggerMin, 0.0)
            val staggerScale = if (isCalm) 1.0 else 0.8 + random() * 0.9
            out.add(
                PartitaChunk(
                    units = chunkUnits,
                    chunkWords = chunkWords,
                    displayWords = display,
                    rowIndex = rowIndex,
                    config = PartitaChunkConfig(
                        id = "$rowIndex-${chunkWords[0].startTime}",
                        x = if (left) -magnitude else magnitude,
                        y = if (isCalm) 0.0 else rowBias * 2.5,
                        rotate = if (isChaotic) (if (left) -3.0 else 3.0) else 0.0,
                        scale = (if (isChaotic) 1 + minOf(graphemeCount * 0.01, 0.05) else 1.0) * staggerScale,
                        marginBottomRem = if (isChaotic) 0.4 else 0.6,
                        passedRotate = (if (rowIndex % 2 == 0) 1.0 else -1.0) * (if (isChaotic) 6.0 else 3.0),
                    ),
                ),
            )
        }
        return PartitaLayout(out, totalGraphemes, if (isChaotic) 720.0 else 1000.0)
    }

    /** JS Math.round：.5 向正无穷取整。 */
    internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

    /** chunk 内每个显示词的随机偏移（PartitaChunk.tsx：wordSeed = 首词 startTime + idx*7.13）。 */
    fun wordConfig(chunk: PartitaChunk, index: Int, intensity: AnimationIntensity): GlowWordConfig {
        val wordSeed = chunk.displayWords[0].startTime + index * 7.13
        fun r(offset: Double): Double {
            val x = sin(wordSeed + offset) * 10000
            return x - floor(x)
        }
        val isCalm = intensity == AnimationIntensity.CALM
        val isChaotic = intensity == AnimationIntensity.CHAOTIC
        val spread = if (isChaotic) 15.0 else if (isCalm) 0.0 else 6.0
        val rot = if (isChaotic) 8.0 else if (isCalm) 0.0 else 3.0
        return GlowWordConfig(
            id = "${chunk.config.id}-w$index",
            x = (r(1.0) - 0.5) * spread * 2,
            y = (r(2.0) - 0.5) * spread * 2,
            rotate = (r(3.0) - 0.5) * rot * 2,
            scale = chunk.config.scale,
            passedRotate = (r(8.0) - 0.5) * 20,
            // PARTITA_WORD_LAYOUT_STYLE = { marginRight: '0.8rem' }
            marginRight = 0.8 * 16.0,
        )
    }

    /** 字号 px：clamp(2.5rem, 5.5vw, 4.5rem) × 密度（>40 字 0.8）× 歌词字号倍率。 */
    fun fontSizePx(layout: PartitaLayout, lyricsFontScale: Double, viewportWidth: Double, remPx: Double = 16.0): Double {
        val d = if (layout.totalGraphemes > 40) 0.8 else 1.0
        val min = 2.5 * d * lyricsFontScale * remPx
        val max = 4.5 * d * lyricsFontScale * remPx
        val pref = 5.5 * d * lyricsFontScale * viewportWidth / 100.0
        return pref.coerceIn(min, max)
    }

    @Suppress("unused")
    private fun Double.r2() = (this * 100).roundToInt() / 100.0
}
