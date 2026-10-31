// 对应 Folia 源码：src/utils/lyrics/renderHints.ts
//
// 逐条对照 Folia 数值：档位阈值、入离场时序、渲染结束时间。
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderHintsTest {

    private fun line(
        start: Double,
        end: Double,
        fullText: String = "文本",
        words: List<Word> = emptyList(),
    ) = Line(words = words, startTime = start, endTime = end, fullText = fullText)

    @Test
    fun `档位阈值与 Folia 的 0_-10 _ 0_-18 一致`() {
        assertEquals(TimingClass.MICRO, RenderHints.getTimingClass(0.099))
        assertEquals(TimingClass.SHORT, RenderHints.getTimingClass(RenderHints.MICRO_LINE_DURATION_THRESHOLD))
        assertEquals(TimingClass.SHORT, RenderHints.getTimingClass(0.179))
        assertEquals(TimingClass.NORMAL, RenderHints.getTimingClass(RenderHints.SHORT_LINE_DURATION_THRESHOLD))
    }

    @Test
    fun `档位映射到过渡模式与显现模式`() {
        assertEquals(LineTransitionMode.NONE, RenderHints.getLineTransitionMode(TimingClass.MICRO))
        assertEquals(LineTransitionMode.FAST, RenderHints.getLineTransitionMode(TimingClass.SHORT))
        assertEquals(LineTransitionMode.NORMAL, RenderHints.getLineTransitionMode(TimingClass.NORMAL))

        assertEquals(WordRevealMode.INSTANT, RenderHints.getWordRevealMode(TimingClass.MICRO))
        assertEquals(WordRevealMode.FAST, RenderHints.getWordRevealMode(TimingClass.SHORT))
        assertEquals(WordRevealMode.NORMAL, RenderHints.getWordRevealMode(TimingClass.NORMAL))
    }

    @Test
    fun `none 模式的入离场时序全为 0`() {
        val timing = RenderHints.getLineTransitionTiming(1.0, LineTransitionMode.NONE, WordRevealMode.NORMAL)
        assertEquals(0.0, timing.enterDuration, 1e-12)
        assertEquals(0.0, timing.exitDuration, 1e-12)
        assertEquals(0.0, timing.linePassHold, 1e-12)
    }

    @Test
    fun `fast 模式的时序照抄 Folia 的 clamp`() {
        // raw 0.15 → enter clamp(0.0675,0.045,0.06)=0.06，exit clamp(0.033,0.03,0.04)=0.033，hold 0.03
        val fast = RenderHints.getLineTransitionTiming(0.15, LineTransitionMode.FAST, WordRevealMode.FAST)
        assertEquals(0.06, fast.enterDuration, 1e-12)
        assertEquals(0.033, fast.exitDuration, 1e-12)
        assertEquals(0.03, fast.linePassHold, 1e-12)

        // raw 0.02 → enter 落到下限 0.045，exit 落到下限 0.03
        val tiny = RenderHints.getLineTransitionTiming(0.02, LineTransitionMode.FAST, WordRevealMode.FAST)
        assertEquals(0.045, tiny.enterDuration, 1e-12)
        assertEquals(0.03, tiny.exitDuration, 1e-12)

        // raw 0.5 超过上限 → enter 0.06 / exit 0.04
        val big = RenderHints.getLineTransitionTiming(0.5, LineTransitionMode.FAST, WordRevealMode.FAST)
        assertEquals(0.06, big.enterDuration, 1e-12)
        assertEquals(0.04, big.exitDuration, 1e-12)
    }

    @Test
    fun `normal 模式的时序照抄 Folia 的 min-max 组合`() {
        // raw 1.0 → enter min(0.42,max(0.22,0.34))=0.34，exit min(0.32,max(0.18,0.18))=0.18，hold 0.06
        val normal = RenderHints.getLineTransitionTiming(1.0, LineTransitionMode.NORMAL, WordRevealMode.NORMAL)
        assertEquals(0.34, normal.enterDuration, 1e-12)
        assertEquals(0.18, normal.exitDuration, 1e-12)
        assertEquals(0.06, normal.linePassHold, 1e-12)

        // raw 0.05 被抬高到 0.12 参与计算
        val micro = RenderHints.getLineTransitionTiming(0.05, LineTransitionMode.NORMAL, WordRevealMode.NORMAL)
        assertEquals(0.22, micro.enterDuration, 1e-12)
        assertEquals(0.18, micro.exitDuration, 1e-12)
    }

    @Test
    fun `instant 显现不占行通过保持时间`() {
        assertEquals(
            0.0,
            RenderHints.getLineTransitionTiming(0.15, LineTransitionMode.FAST, WordRevealMode.INSTANT).linePassHold,
            1e-12,
        )
        assertEquals(
            0.0,
            RenderHints.getLineTransitionTiming(1.0, LineTransitionMode.NORMAL, WordRevealMode.INSTANT).linePassHold,
            1e-12,
        )
    }

    @Test
    fun `micr 行渲染结束时间有 0_-067 的下限`() {
        val hints = RenderHints.buildLineRenderHints(line(start = 5.0, end = 5.02))
        assertEquals(TimingClass.MICRO, hints.timingClass)
        assertEquals(LineTransitionMode.NONE, hints.lineTransitionMode)
        assertEquals(5.067, hints.renderEndTime, 1e-12)
    }

    @Test
    fun `normal 行渲染结束时间等于 exitStart 加 exit`() {
        val hints = RenderHints.buildLineRenderHints(line(start = 1.0, end = 2.0))
        // lastWordEnd 取 endTime，linePassStart = 2.0+0.06 = 2.06
        // exitStart = max(2.06, 2.0-0.18) = 2.06；renderEnd = max(2.0, 2.06+0.18) = 2.24
        assertEquals(2.24, hints.renderEndTime, 1e-12)
        assertEquals(1.0, hints.rawDuration, 1e-12)
    }

    @Test
    fun `fast 行渲染结束时间把入场也纳入 exitStart`() {
        val hints = RenderHints.buildLineRenderHints(line(start = 0.0, end = 0.15))
        assertEquals(TimingClass.SHORT, hints.timingClass)
        // linePassStart = 0.15+0.03 = 0.18；exitStart = max(0.07,0.18,0.117) = 0.18
        // renderEnd = max(0.15, 0.18+0.033) = 0.213
        assertEquals(0.213, hints.renderEndTime, 1e-12)
    }

    @Test
    fun `最后一个词结束时间晚于行结束时间时以词为准`() {
        val words = listOf(Word("你", 1.0, 1.8), Word("好", 2.0, 2.4))
        val hints = RenderHints.buildLineRenderHints(line(start = 1.0, end = 3.0, words = words))
        assertEquals(2.4, RenderHints.getLastWordEndTime(line(start = 1.0, end = 3.0, words = words)), 1e-12)
        // raw 2.0 → enter 0.42 / exit 0.32；linePassStart = 2.4+0.06 = 2.46
        // exitStart = max(2.46, 3.0-0.32=2.68) = 2.68；renderEnd = max(3.0, 3.0) = 3.0
        assertEquals(3.0, hints.renderEndTime, 1e-12)
    }

    @Test
    fun `无词时最后词结束时间退回行结束时间`() {
        val noWords = line(start = 1.0, end = 2.0)
        assertEquals(2.0, RenderHints.getLastWordEndTime(noWords), 1e-12)
    }

    @Test
    fun `getLineRenderEndTime 对空行返回负无穷`() {
        assertEquals(Double.NEGATIVE_INFINITY, RenderHints.getLineRenderEndTime(null), 0.0)
    }

    @Test
    fun `已有 renderHints 时不再重算`() {
        val hints = RenderHints.buildLineRenderHints(line(start = 1.0, end = 2.0))
        val withHints = line(start = 1.0, end = 2.0).copy(renderHints = hints)
        assertEquals(hints, RenderHints.getLineRenderHints(withHints))
        assertEquals(hints, RenderHints.getLineRenderHints(line(start = 1.0, end = 2.0)))
    }

    @Test
    fun `migrate 只在缺失或不一致时改动`() {
        val raw = line(start = 1.0, end = 2.0)
        val missing = RenderHints.migrateLyricLinesRenderHints(listOf(raw))
        assertTrue(missing.changed)
        assertEquals(1, missing.value.size)
        assertEquals(2.24, missing.value[0].renderHints!!.renderEndTime, 1e-12)

        val clean = RenderHints.migrateLyricLinesRenderHints(missing.value)
        assertFalse(clean.changed)

        // 手工塞一个错的 renderHints，应当被纠正
        val corrupted = listOf(
            raw.copy(renderHints = RenderHints.buildLineRenderHints(raw).copy(renderEndTime = 99.0))
        )
        val fixed = RenderHints.migrateLyricLinesRenderHints(corrupted)
        assertTrue(fixed.changed)
        assertEquals(2.24, fixed.value[0].renderHints!!.renderEndTime, 1e-12)
    }

    @Test
    fun `双签名重载构造只有起止时间的行`() {
        val byNumbers = RenderHints.buildLineRenderHints(3.0, 4.0)
        val byLine = RenderHints.buildLineRenderHints(line(start = 3.0, end = 4.0))
        assertEquals(byLine, byNumbers)
    }
}