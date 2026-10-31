// 对应 Folia 文件：src/components/visualizer/songHandover.ts（行 1–223）
//
// 换歌闸门：父层切换歌曲时先把歌词清成 []，几个渲染帧后才填上新歌词。
// 直接跟随 seed 的样式会拿空歌重建、再把词弹出来。这里让屏幕上一直留着「已提交」的那首歌，
// 直到新的那首准备就绪；纯音乐则靠播放进度确认（2 秒）或兜底时限（8 秒）落定。
package com.guangyue.core.lyrics

/** 已提交的歌曲。对应 Folia 的 VisualizerSongCommit。 */
data class VisualizerSongCommit(
    val seed: Any?,
    val lines: List<Line>,
    /** 播放确认后仍无词：走 ♪ / 程序化背景那条路。 */
    val isInstrumental: Boolean,
)

/** 决定换歌闸门动作的输入。对应 Folia 的 SongCommitInput。 */
data class SongCommitInput(
    val seed: Any?,
    val committedSeed: Any?,
    val lyricsSignature: String,
    val committedSignature: String,
    val isCommittedInstrumental: Boolean,
)

/** 闸门决定。对应 Folia 的 SongCommitDecision。 */
sealed interface SongCommitDecision {
    /** 立刻接下这首歌。 */
    data class Commit(val isInstrumental: Boolean) : SongCommitDecision
    /** 保持已提交的歌，看播放进度分辨这次静默属于哪种。 */
    data object Watch : SongCommitDecision
    /** 无事可做。 */
    data object Idle : SongCommitDecision
}

/** 纯音乐确认用的观测量。对应 Folia 的 InstrumentalWatchState。 */
data class InstrumentalWatchState(
    /** 看到新歌的播放时间回到开头，说明时钟确实重置了。 */
    val sawPlaybackReset: Boolean,
    val playbackTime: Double,
    val elapsedMs: Long,
)

/** 纯音乐确认的上限。对应 Folia 的 InstrumentalWatchLimits。 */
data class InstrumentalWatchLimits(
    val instrumentalCommitSeconds: Double = SongHandover.DEFAULT_INSTRUMENTAL_COMMIT_SECONDS,
    val readyGraceMs: Long = SongHandover.DEFAULT_READY_GRACE_MS,
)

/** 对应 Folia songHandover.ts。 */
object SongHandover {

    /** 稳定标识：提交「没有词」时不能被当成内容变化。 */
    val EMPTY_COMMITTED_LINES: List<Line> = emptyList()

    /** Folia 行 18：确认播放 2 秒仍无歌词，就当纯音乐，而不是还在加载。 */
    const val DEFAULT_INSTRUMENTAL_COMMIT_SECONDS = 2.0

    /** Folia 行 20：墙钟兜底（ms），播放时间永不前进时闸门也不能挂住。 */
    const val DEFAULT_READY_GRACE_MS = 8000L

    /**
     * 对应 Folia 行 37–57 的 hashWordSegments：FNV-1a，32 位无符号。
     * 只哈希边界长度，因为边界一定能拼回本行文本。
     * 保存过的分词与自动分词用 1 / 2 区分，形状相同也不会撞。
     */
    fun hashWordSegments(lines: List<Line>): Long {
        var hash = 2166136261.toInt()
        fun mix(value: Int) {
            hash = (hash xor value) * 16777619
        }
        lines.forEachIndexed { index, line ->
            val segments = line.wordSegments ?: line.autoWordSegments
            if (segments == null) return@forEachIndexed
            mix(index)
            mix(if (line.wordSegments != null) 1 else 2)
            segments.forEach { mix(it.length) }
        }
        return hash.toLong() and 0xFFFFFFFFL
    }

    /**
     * 对应 Folia 行 59–71 的 getLyricsSignature。
     * 空串表示「没有词」，故意对「还在加载」与「纯音乐」保持歧义——只有播放能分辨。
     */
    fun getLyricsSignature(lines: List<Line>): String =
        if (lines.isEmpty()) "" else "${lines.size}|${lines.first().fullText}|${hashWordSegments(lines)}"

    /** 对应 Folia 行 92–131 的 decideSongCommit（纯决策，方便单测）。 */
    fun decideSongCommit(input: SongCommitInput): SongCommitDecision {
        if (input.seed == input.committedSeed) {
            if (input.lyricsSignature.isEmpty()) {
                // 词消失从来不是这首歌的消息：那是父层站在两首歌中间。
                if (input.isCommittedInstrumental) return SongCommitDecision.Idle
                return SongCommitDecision.Watch
            }
            // 同一首歌、不同的词：迟到加载或重新处理，就地接住。
            if (input.lyricsSignature != input.committedSignature) return SongCommitDecision.Commit(false)
            return SongCommitDecision.Idle
        }

        // 新歌要等它的词落地才上场，而且必须是「不同的」词：
        // seed 比 lines 早一帧翻转，签名相同说明还是旧歌的词。
        if (input.lyricsSignature.isNotEmpty() && input.lyricsSignature != input.committedSignature) {
            return SongCommitDecision.Commit(false)
        }
        return SongCommitDecision.Watch
    }

    /**
     * 对应 Folia 行 137–172 的 isInstrumentalConfirmed。
     * 绑播放而不是墙钟：只是还在加载的歌不会前进，继续等词；
     * 真的播过这么久还没有词，就当纯音乐。readyGraceMs 是从不开始播放的逃生口。
     */
    fun isInstrumentalConfirmed(
        state: InstrumentalWatchState,
        limits: InstrumentalWatchLimits = InstrumentalWatchLimits(),
    ): Boolean =
        (state.sawPlaybackReset && state.playbackTime >= limits.instrumentalCommitSeconds) ||
            state.elapsedMs >= limits.readyGraceMs

    /**
     * 闸门状态机（对应 useVisualizerSongCommit 的非 React 部分）。
     * 调用方每次播放/歌词变化时喂入，得到「屏幕上该显示哪首歌」。
     * Folia 里用 rAF 循环等纯音乐落定，这里由 Compose 层用 animation clock 驱动 tick()。
     */
    class Gate(
        seed: Any?,
        lines: List<Line>,
        private val instrumentalCommitSeconds: Double = DEFAULT_INSTRUMENTAL_COMMIT_SECONDS,
        private val readyGraceMs: Long = DEFAULT_READY_GRACE_MS,
    ) {
        var committed: VisualizerSongCommit = VisualizerSongCommit(seed, lines, false)
            private set

        /** 当前处于 watch 的起始墙钟（ms）；null 表示不在 watch。 */
        private var watchStartMs: Long? = null
        private var sawPlaybackReset = false

        val isWatching: Boolean get() = watchStartMs != null

        /**
         * 喂入当前状态。返回 true 表示 committed 发生变化，调用方需要刷新。
         *
         * @param nowMs 墙钟毫秒（生产用 SystemClock，测试可注入）。
         */
        fun update(seed: Any?, lines: List<Line>, nowMs: Long): Boolean {
            val decision = decideSongCommit(
                SongCommitInput(
                    seed = seed,
                    committedSeed = committed.seed,
                    lyricsSignature = getLyricsSignature(lines),
                    committedSignature = getLyricsSignature(committed.lines),
                    isCommittedInstrumental = committed.isInstrumental,
                )
            )

            when (decision) {
                is SongCommitDecision.Idle -> {
                    watchStartMs = null
                    return false
                }

                is SongCommitDecision.Commit -> {
                    watchStartMs = null
                    val next = VisualizerSongCommit(seed, lines, decision.isInstrumental)
                    if (next == committed) return false
                    committed = next
                    return true
                }

                SongCommitDecision.Watch -> {
                    if (watchStartMs == null) {
                        watchStartMs = nowMs
                        sawPlaybackReset = false
                    }
                    return false
                }
            }
        }

        /** watch 期间每帧调用；播放时间归零即记下「时钟确实重置」。 */
        fun observePlayback(playbackTime: Double) {
            if (watchStartMs == null) return
            if (!sawPlaybackReset && playbackTime < 1.0) sawPlaybackReset = true
        }

        /** watch 落定则提交「无词的纯音乐」，返回是否发生变化。 */
        fun settleIfReady(seed: Any?, playbackTime: Double, nowMs: Long): Boolean {
            val start = watchStartMs ?: return false
            if (!sawPlaybackReset && playbackTime < 1.0) sawPlaybackReset = true
            val settled = isInstrumentalConfirmed(
                InstrumentalWatchState(
                    sawPlaybackReset = sawPlaybackReset,
                    playbackTime = playbackTime,
                    elapsedMs = nowMs - start,
                ),
                InstrumentalWatchLimits(instrumentalCommitSeconds, readyGraceMs),
            )
            if (!settled) return false
            watchStartMs = null
            val next = VisualizerSongCommit(seed, EMPTY_COMMITTED_LINES, true)
            if (next == committed) return false
            committed = next
            return true
        }
    }
}