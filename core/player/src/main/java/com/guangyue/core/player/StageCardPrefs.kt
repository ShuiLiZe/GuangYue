package com.guangyue.core.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 11-18 Folia 设置「播放页 → 歌曲信息卡片」（useStageSettingsStore stageTrackPillMode / stageTrackPillTimeoutSec）：
 * 播放页正在播放卡片限时显示（auto，默认 10 秒，3–60）/ 常驻显示（always）/ 不显示（never）。
 */
object StageCardPrefs {
    data class Settings(val mode: String = "auto", val timeoutSec: Int = 10)

    private const val FILE = "stage_settings"
    private val state = MutableStateFlow(Settings())
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Settings> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            state.value = Settings(
                mode = p.getString("stage_track_pill_mode", "auto").takeIf { it == "always" || it == "never" } ?: "auto",
                timeoutSec = p.getInt("stage_track_pill_timeout_sec", 10).coerceIn(3, 60),
            )
            loaded = true
        }
        return state
    }

    fun update(context: Context, change: (Settings) -> Settings) {
        flow(context)
        val next = change(state.value).let { it.copy(timeoutSec = it.timeoutSec.coerceIn(3, 60)) }
        state.value = next
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("stage_track_pill_mode", next.mode)
            .putInt("stage_track_pill_timeout_sec", next.timeoutSec)
            .apply()
    }
}
