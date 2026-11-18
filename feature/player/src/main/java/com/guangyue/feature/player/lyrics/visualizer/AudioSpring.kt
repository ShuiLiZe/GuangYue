// 对应 Folia：framer-motion useSpring(audioBands.x, { stiffness, damping })，舞台每帧读平滑后的频段。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.player.AudioBands
import com.guangyue.core.player.AudioLevels

/**
 * 质量 1 的阻尼弹簧，追一个每帧变化的目标值（0..255 量纲）。在 draw 里按真实帧间隔步进。
 */
internal class AudioSpring(private val stiffness: Double, private val damping: Double) {
    var value = 0.0
        private set
    private var velocity = 0.0
    private var lastNanos = 0L

    fun step(target: Double, nowNanos: Long): Double {
        if (lastNanos == 0L) { value = target; lastNanos = nowNanos; return value }
        var dt = ((nowNanos - lastNanos) / 1e9).coerceIn(0.0, 0.1)
        lastNanos = nowNanos
        // 半隐式欧拉，按 4 ms 细分，刚度 150 也稳。
        while (dt > 0) {
            val h = minOf(dt, 0.004)
            val force = -stiffness * (value - target) - damping * velocity
            velocity += force * h
            value += velocity * h
            dt -= h
        }
        if (!value.isFinite()) { value = target; velocity = 0.0 }
        return value
    }
}

/** 舞台读电平的唯一入口（便于以后替换 / 取证时打桩）。 */
internal object StageAudio {
    fun bands(nowNanos: Long = System.nanoTime()): AudioBands = AudioLevels.read(nowNanos)
}
