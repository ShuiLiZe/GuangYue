// 对应 Folia 文件：
//   src/components/visualizer/GlowWord.tsx（currentTime 是 MotionValue<number>，逐帧变化但不写 React state）
//   src/components/visualizer/README.md 的 Runtime guardrails（连续播放时间用 MotionValue/ref/CSS，
//     不许每帧写 React state）
//
// 手机化实现：把「逐帧时间」做成一个只读状态——帧循环里更新，消费方有两种读法，
// 两种都**不会**引起整行重组：
//   1. 需要离散变化的（词的三态）：在 derivedStateOf 里读，值不变就不通知重组；
//   2. 需要连续变化的（辉光透明度、涟漪进度）：在 graphicsLayer / drawBehind 的 lambda 里读，
//      Compose 只重走动 layer / draw 阶段，跳过重组与布局。
// 这正是 Folia 把逐帧动画交给 MotionValue 而不是 React state 的等价做法。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * 播放时钟。
 *
 * Folia 的 `currentTime` 是 framer-motion 的 `MotionValue<number>`，单位秒，逐帧更新，
 * 组件只在「词的三态」变化时重渲染。这里同构：
 *
 *  - [positionMs] 提供播放器的当前进度（毫秒，低频更新，例如 4–10Hz）；
 *  - [frames] 是自上次进度**跳变**以来累积的帧时间（秒），由 [withFrameNanos] 推进；
 *  - [seconds] = positionMs/1000 + frames.doubleValue，因此两帧之间也保持平滑；
 *  - 播放器进度一变（seek、换歌、缓冲回退）就把累积时间归零，避免重复计时。
 *
 * 读取方式决定重组代价：
 *  - [readForComposition] 读的是 State 本身，放在 `derivedStateOf` 中只会得到「值变化」的通知；
 *  - [readForDraw] 供 `drawBehind` / `graphicsLayer` 的 lambda 使用，只触发 draw/layer 阶段。
 */
@Stable
class VisualizerClock internal constructor(
    private val positionMs: () -> Long,
    internal val frames: MutableDoubleState,
    /** 帧循环最近一次「认领」的播放器进度；[frames] 是相对它累积的。 */
    internal val anchorMs: MutableLongState = mutableLongStateOf(Long.MIN_VALUE),
) {
    /** 当前播放时间（秒）。 */
    fun seconds(): Double = readForDraw()

    /** 在 draw / layer lambda 里读：只重走绘制或图层阶段，不重组、不重布局。 */
    fun readForDraw(): Double {
        // 三个 State 都在这里读，保证依赖记录到读取者（draw/layer 作用域）上。
        val accumulated = frames.doubleValue
        val anchor = anchorMs.longValue
        return VisualizerClockMath.resolve(anchor, accumulated, positionMs())
    }

    /** 在 derivedStateOf 里读：帧时间变化不必然引发下游重组，只有派生的离散值变化才通知。 */
    fun readForComposition(): Double = readForDraw()
}

/**
 * 时钟的纯计算（JVM 单测锁定）。
 *
 * 真机闪烁根因（0.0.13 雾笺 / 商籁）：播放器进度更新后，重组先把新进度交给 draw，
 * 帧循环要到下一帧才把累积帧时间归零 —— 中间那一帧 t = 新进度 + 旧累积，
 * 向前跳一个进度轮询间隔再跳回来。雾笺擦字 / 清晰度、商籁镜头切换都是按 t 分段的，
 * 于是整屏字和背景一起闪。修法：
 *  1. 读取时若进度还没被帧循环认领，继续用旧锚点的预测值（与新进度相差不大时）；
 *  2. 认领新进度时，若预测与新进度相差在 [SNAP_S] 以内，保留连续性（把差额记进累积），
 *     不往回跳；只有 seek / 换歌这类大跳变才硬对齐。
 */
internal object VisualizerClockMath {
    /** 小于这个差额视为同一条时间线上的轮询抖动，不硬跳。 */
    const val SNAP_S = 0.25

    fun resolve(anchorMs: Long, frames: Double, positionMs: Long): Double {
        val now = positionMs / 1000.0
        if (anchorMs == Long.MIN_VALUE) return now + frames
        val predicted = anchorMs / 1000.0 + frames
        if (positionMs == anchorMs) return predicted
        return if (kotlin.math.abs(predicted - now) < SNAP_S) predicted else now
    }

    /** 帧循环认领新进度后应写回的累积值。 */
    fun rebase(anchorMs: Long, frames: Double, positionMs: Long, playing: Boolean): Double {
        if (anchorMs == Long.MIN_VALUE || !playing) return 0.0
        val diff = anchorMs / 1000.0 + frames - positionMs / 1000.0
        // 保留 80% 差额：时间线连续（单次最多回拉 50ms），又让帧时钟与播放器的漂移收敛。
        return if (kotlin.math.abs(diff) < SNAP_S) diff * CARRY else 0.0
    }

    const val CARRY = 0.8
}

/**
 * 记住一个 [VisualizerClock]。
 *
 * @param positionMsProvider 播放器进度（毫秒）；低频更新即可，帧间由累积帧时间补齐
 * @param playingProvider 是否正在播放；暂停时不推进，画面停在当前时刻
 * @param enabled 关掉时（例如「减少动效」）不启动帧循环，直接跟随播放器进度
 */
@Composable
fun rememberVisualizerClock(
    positionMsProvider: () -> Long,
    playingProvider: () -> Boolean,
    enabled: Boolean = true,
): VisualizerClock {
    val frames = remember { mutableDoubleStateOf(0.0) }
    // 用 rememberUpdatedState 把两个 provider 钉住：provider 每帧重建 lambda 时时钟不会重建，
    // 帧循环也就不会被反复重启（Folia 侧 currentTime 是长期存活的 MotionValue，同构）。
    val latestPosition = rememberUpdatedState(positionMsProvider)
    val latestPlaying = rememberUpdatedState(playingProvider)
    val clock = remember {
        VisualizerClock(
            positionMs = { latestPosition.value() },
            frames = frames,
        )
    }

    LaunchedEffect(enabled, clock) {
        if (!enabled) {
            // 「减少动效」：不做帧间插值，时间完全由播放器进度决定。
            frames.doubleValue = 0.0
            clock.anchorMs.longValue = Long.MIN_VALUE
            return@LaunchedEffect
        }
        var lastFrameNanos = 0L
        clock.anchorMs.longValue = latestPosition.value()
        frames.doubleValue = 0.0
        while (true) {
            withFrameNanos { frameNanos ->
                val position = latestPosition.value()
                val anchor = clock.anchorMs.longValue
                if (position != anchor) {
                    // 进度更新：小抖动保持连续，seek / 换歌才硬对齐（见 VisualizerClockMath）。
                    frames.doubleValue = VisualizerClockMath.rebase(anchor, frames.doubleValue, position, latestPlaying.value())
                    clock.anchorMs.longValue = position
                } else if (latestPlaying.value()) {
                    if (lastFrameNanos != 0L) {
                        frames.doubleValue += (frameNanos - lastFrameNanos) / 1_000_000_000.0
                    }
                }
                lastFrameNanos = frameNanos
            }
        }
    }
    return clock
}

/**
 * 供「非 Compose 的回想」使用的空实现（预览/截图在 Robolectric 下用固定时间，
 * 避免 withFrameNanos 帧循环把 `waitForIdle()` 挂住）。
 */
@Composable
fun rememberStaticVisualizerClock(seconds: Double): VisualizerClock {
    val frames = remember { mutableDoubleStateOf(0.0) }
    val zero = remember { 0L }
    return remember(seconds) {
        VisualizerClock(positionMs = { zero }, frames = frames).also {
            frames.doubleValue = seconds
        }
    }
}

/** 让帧循环在离开组合时停下的哨兵（保留给需要显式取消的场景）。 */
internal class ClockStop(val onStop: () -> Unit) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = onStop()
    override fun onAbandoned() = onStop()
}