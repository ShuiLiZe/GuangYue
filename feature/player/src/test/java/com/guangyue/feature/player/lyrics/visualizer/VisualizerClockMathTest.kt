package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定 0.0.13 雾笺 / 商籁真机闪烁的修复：进度轮询更新不得让时间向前跳一帧再跳回。 */
class VisualizerClockMathTest {
    private val eps = 1e-9

    @Test
    fun unclaimedSmallUpdateKeepsPrediction() {
        // 锚点 10.000s，已累积 0.240s；播放器新报 10.250s，帧循环还没认领。
        // 旧实现会画 10.250 + 0.240 = 10.490（前跳）；现在应仍是预测值 10.240。
        assertEquals(10.24, VisualizerClockMath.resolve(10_000, 0.24, 10_250), eps)
    }

    @Test
    fun claimedPositionReturnsAnchorPlusFrames() {
        assertEquals(10.5, VisualizerClockMath.resolve(10_000, 0.5, 10_000), eps)
    }

    @Test
    fun seekSnapsImmediately() {
        assertEquals(42.0, VisualizerClockMath.resolve(10_000, 0.24, 42_000), eps)
        assertEquals(0.0, VisualizerClockMath.rebase(10_000, 0.24, 42_000, playing = true), eps)
    }

    @Test
    fun rebaseKeepsContinuityWithinSnapWindow() {
        // 预测 10.240，新进度 10.250：差 -0.010，保留 80% → 认领后 t = 10.250 - 0.008 = 10.242。
        val f = VisualizerClockMath.rebase(10_000, 0.24, 10_250, playing = true)
        assertEquals(-0.008, f, eps)
        val after = VisualizerClockMath.resolve(10_250, f, 10_250)
        assertEquals(10.242, after, eps)
        // 认领前后最多相差 20% 差额，不会出现整段回跳。
        assertEquals(0.002, after - 10.24, 1e-6)
    }

    @Test
    fun pausedRebaseSnaps() {
        assertEquals(0.0, VisualizerClockMath.rebase(10_000, 0.1, 10_050, playing = false), eps)
    }

    @Test
    fun noAnchorFallsBackToPositionPlusFrames() {
        // 静态时钟：positionMs=0，frames=秒数。
        assertEquals(37.5, VisualizerClockMath.resolve(Long.MIN_VALUE, 37.5, 0), eps)
    }
}
