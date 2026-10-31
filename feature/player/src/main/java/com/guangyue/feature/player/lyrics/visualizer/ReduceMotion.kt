// 对应 Folia 文件：
//   src/components/visualizer/GlowWord.tsx（三态与辉光动画；Folia 无「系统关闭动画」这一档）
//   src/components/visualizer/glowWordTiming.ts（行容器入离场；降级时直接切状态）
//
// 手机化改动：Android 的「移除动画」是系统级开关（开发者选项 / 无障碍，
// `Settings.Global.ANIMATOR_DURATION_SCALE == 0`，`ValueAnimator.getDurationScale()` 同源）。
// 系统关掉动画时必须直接切三态、不做辉光动画与呼吸浮动，而不是继续按秒数插值，
// 所以这里读系统设置并通过 ContentObserver 跟随变化。
package com.guangyue.feature.player.lyrics.visualizer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** 读系统动画时长倍率；取不到时按 1（正常动效）处理，不因为读不到就降级。 */
private fun readAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

/**
 * 系统是否关闭了动画（`ANIMATOR_DURATION_SCALE == 0`）。
 *
 * 关闭时歌词页：三态由进度直接判定、辉光强度直接归零、呼吸浮动不挂动画。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var scale by remember(context) { mutableStateOf(readAnimatorScale(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // 首次回来同步一次：observer 注册前可能已被系统改过。
        scale = readAnimatorScale(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale == 0f
}