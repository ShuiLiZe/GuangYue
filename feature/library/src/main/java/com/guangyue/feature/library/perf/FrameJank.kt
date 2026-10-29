package com.guangyue.feature.library.perf

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import java.util.Locale

/**
 * 滚动掉帧冒烟（JankStats 的自带替代）。
 *
 * 为什么不用 `androidx.metrics.performance`：这台机器离线构建，Gradle 缓存里没有
 * `androidx.metrics:performance` 这个坐标（只有 androidx.core / compose 等），release 与
 * debug 都会在依赖解析阶段直接失败；宏基准（Macrobenchmark）又必须有真机 + 联网拉
 * `com.android.test` 插件。所以这里用 Compose 自己的帧时钟 [withFrameNanos] 量：
 *
 *  - 每次 vsync 回调算一帧，相邻两帧间隔 ≥ [JANK_MS]（16.7ms 的 1.5 倍，与 Android 掉帧
 *    判定同尺度）记一次掉帧；同时记录最长帧与 p95。
 *  - 每 [LOG_EVERY] 帧向 logcat 打一行（tag [TAG]），退出页面时打汇总行：
 *    `GyJank: 首页滚动汇总 frames=612 jank=38 jankRate=6.21% worst=48ms avg=12.4ms p95=31ms`
 *  - 只在 DEBUG 构建生效（[enabled] 取 `BuildConfig.DEBUG`），正式包里 `if (enabled)` 恒假，
 *    R8 会把整段采样删掉，零开销。
 */
object FrameJank {

    const val TAG = "GyJank"

    /** 超过这个毫秒数算掉帧。 */
    const val JANK_MS = 25L

    /** 每多少帧汇总一次。 */
    const val LOG_EVERY = 120

    /**
     * 是否跑在 Robolectric 单测里。必须声明在 [enabled] 之前（Kotlin 按声明顺序初始化）。
     */
    private val runningUnderRobolectric: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * release 里为编译期常量 false（AGP 给每个模块都生成 BuildConfig）。
     *
     * 另外在 Robolectric 的单测里关掉：JVM 上的帧时钟不会真正推进，`withFrameNanos` 的循环会让
     * `waitForIdle()` 永远等不到空闲（实测把截图用例挂死了 15 分钟）。正式包里没有 Robolectric
     * 这个类，判断退化成常量 false。
     */
    val enabled: Boolean = com.guangyue.feature.library.BuildConfig.DEBUG && !runningUnderRobolectric

    private val gaps = ArrayList<Long>(256)
    private var frames = 0L
    private var janks = 0L
    private var lastNanos = 0L
    private var worstMs = 0L

    data class Stats(
        val frames: Long,
        val jankFrames: Long,
        val jankRate: Double,
        val worstFrameMs: Long,
        val averageFrameMs: Double,
        val p95FrameMs: Long,
    ) {
        override fun toString(): String = String.format(
            Locale.US,
            "frames=%d jank=%d jankRate=%.2f%% worst=%dms avg=%.1fms p95=%dms",
            frames,
            jankFrames,
            jankRate * 100,
            worstFrameMs,
            averageFrameMs,
            p95FrameMs,
        )
    }

    @Synchronized
    fun onFrame(frameNanos: Long) {
        if (!enabled) return
        if (lastNanos != 0L) {
            val gapMs = (frameNanos - lastNanos) / 1_000_000
            frames += 1
            gaps.add(gapMs)
            if (gapMs >= JANK_MS) janks += 1
            if (gapMs > worstMs) worstMs = gapMs
            if (frames % LOG_EVERY == 0L) Log.i(TAG, "滚动中 ${stats()}")
        }
        lastNanos = frameNanos
    }

    @Synchronized
    fun stats(): Stats {
        val sorted = gaps.sorted()
        val p95 = if (sorted.isEmpty()) {
            0L
        } else {
            sorted[((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        }
        return Stats(
            frames = frames,
            jankFrames = janks,
            jankRate = if (frames == 0L) 0.0 else janks.toDouble() / frames,
            worstFrameMs = worstMs,
            averageFrameMs = if (sorted.isEmpty()) 0.0 else sorted.average(),
            p95FrameMs = p95,
        )
    }

    @Synchronized
    fun reset() {
        frames = 0
        janks = 0
        lastNanos = 0
        worstMs = 0
        gaps.clear()
    }
}

/** 页面根 composable 里调用一次：开始逐帧采样，离开时把汇总打到 logcat。 */
@Composable
fun TrackScrollFrames() {
    if (!FrameJank.enabled) return
    LaunchedEffect(Unit) { FrameJank.reset() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { FrameJank.onFrame(it) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { Log.i(FrameJank.TAG, "滚动汇总 ${FrameJank.stats()}") }
    }
}
