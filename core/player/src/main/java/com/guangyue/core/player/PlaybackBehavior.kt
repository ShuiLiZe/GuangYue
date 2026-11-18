package com.guangyue.core.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 11-18 Folia 设置「播放控制 → 播放行为」里手机能落地的几项（进程级单例）：
 * - autoSkipUnplayable（无法播放时自动跳过，默认关）：取链失败且不是网络问题时，倒计时后跳到下一首。
 * - preventDisplaySleep（播放时阻止休眠，默认关）：播放中保持屏幕常亮。
 */
object PlaybackBehavior {
    data class Settings(
        val autoSkipUnplayable: Boolean = false,
        val preventDisplaySleep: Boolean = false,
        /** Folia queueAddBehavior：false = 追加到末尾（默认）/ true = 追加到下一首。 */
        val queueAddNext: Boolean = false,
    )

    private const val FILE = "playback_behavior"
    private val state = MutableStateFlow(Settings())
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Settings> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            state.value = Settings(p.getBoolean("auto_skip_unplayable", false), p.getBoolean("prevent_display_sleep", false), p.getBoolean("queue_add_next", false))
            loaded = true
        }
        return state
    }

    fun update(context: Context, change: (Settings) -> Settings) {
        flow(context)
        val next = change(state.value)
        state.value = next
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_skip_unplayable", next.autoSkipUnplayable)
            .putBoolean("prevent_display_sleep", next.preventDisplaySleep)
            .putBoolean("queue_add_next", next.queueAddNext)
            .apply()
    }
}
