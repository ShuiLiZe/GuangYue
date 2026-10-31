// 对应 Folia 源码：
//   src/utils/lyrics/renderHints.ts（行 175–201，ensureLyricDataRenderHints）
//   src/utils/lyrics/graphemeTiming.ts（无逐字时间时的字级均分）
//
// V0 时间轴接入：把 core:data 的逐行歌词（Lyric / LyricLine / LyricTag）转成歌词内核的 Line。
//   - 逐字时间戳（网易 yrc / QQ qrc 解析出来的 <mm:ss.xx>）：直接用，不造假；
//   - 没有逐字时间：按字均分（与 Folia 无 syllables 时完全一致）。
// 适配器只做搬运，所有数值计算都留给 :core:lyrics（纯 Kotlin，可 JVM 单测）。
package com.guangyue.core.data.lyrics

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import com.guangyue.core.lyrics.Timeline
import com.guangyue.core.lyrics.TimelineLineInput
import com.guangyue.core.lyrics.TimelineTag

/** 歌词数据 → 渲染内核的时间轴适配器。 */
object LyricTimeline {

    /**
     * 把一份歌词转成内核 Line 列表。
     *
     * @param lyric core:data 解析出的歌词
     * @param durationMs 整曲时长（ms），用于夹住最后一行的结束时间；未知传 null
     */
    fun toLines(lyric: Lyric, durationMs: Long? = null): List<Line> =
        Timeline.buildLines(toInputs(lyric), durationMs)

    /**
     * 直接吃 [LyricLine] 列表（歌词页只拿到行列表，拿不到整份 [Lyric] 时用这个口）。
     *
     * 与 [toLines] 走同一条内核路径，没有任何额外处理。
     */
    fun toLines(lines: List<LyricLine>, durationMs: Long? = null): List<Line> =
        Timeline.buildLines(toInputs(lines), durationMs)

    /** 逐行转换，保留时间戳、译文与逐字标签。 */
    fun toInputs(lyric: Lyric): List<TimelineLineInput> = toInputs(lyric.lines)

    /** 逐行转换，保留时间戳、译文与逐字标签。 */
    fun toInputs(lines: List<LyricLine>): List<TimelineLineInput> = lines.map { line ->
        TimelineLineInput(
            timeMs = line.timeMs,
            text = line.text,
            translation = line.translation,
            tags = line.tags.map { tag ->
                TimelineTag(startMs = tag.startMs, endMs = tag.endMs, data = tag.data)
            },
        )
    }

    /** 与 Folia ensureLyricDataRenderHints 等价：缺渲染提示的行补齐。 */
    fun ensureRenderHints(lines: List<Line>): List<Line> = RenderHints.ensureLyricLinesRenderHints(lines)
}