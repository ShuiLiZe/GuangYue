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
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
) {
    /** 当前播放时间（秒）。 */
    fun seconds(): Double = positionMs() / 1000.0 + frames.doubleValue

    /** 在 draw / layer lambda 里读：只重走绘制或图层阶段，不重组、不重布局。 */
    fun readForDraw(): Double {
        // 显式读 frames 这个 State，并在同一表达式里读 positionMs()，
        // 保证两处依赖都被记录到读取者（draw/layer 作用域）上。
        val accumulated = frames.doubleValue
        return positionMs() / 1000.0 + accumulated
    }

    /** 在 derivedStateOf 里读：帧时间变化不必然引发下游重组，只有派生的离散值变化才通知。 */
    fun readForComposition(): Double = readForDraw()
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
            return@LaunchedEffect
        }
        var lastFrameNanos = 0L
        var lastPositionMs = latestPosition.value()
        while (true) {
            withFrameNanos { frameNanos ->
                val position = latestPosition.value()
                if (position != lastPositionMs) {
                    // 进度跳变（seek / 换歌）：累积时间归零，避免与真实进度重复计时。
                    lastPositionMs = position
                    frames.doubleValue = 0.0
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