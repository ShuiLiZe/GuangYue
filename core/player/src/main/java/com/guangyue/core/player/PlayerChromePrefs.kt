package com.guangyue.core.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 11-19 Folia 设置「播放页 → 控制栏 / 播放页界面」（usePlayerChromeSettingsStore）：
 * - 自动隐藏控制栏 auto_hide_player_chrome（Folia 默认关；广乐手机上一直是播放 3 秒无触摸自动隐藏，默认保持开，理由：手机无悬停、舞台要满屏）
 * - 隐藏底部控制条 hide_player_progress_bar、隐藏右侧按钮 hide_player_right_panel_button、
 *   始终显示播放页左上角返回按钮 always_show_player_back_button（均默认关，同 Folia）。
 */
object PlayerChromePrefs {
    data class Settings(
        val autoHide: Boolean = true,
        val hideProgressBar: Boolean = false,
        val hidePanelButton: Boolean = false,
        val alwaysBackButton: Boolean = false,
    )

    private const val FILE = "player_chrome_settings"
    private val state = MutableStateFlow(Settings())
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Settings> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            state.value = Settings(
                autoHide = p.getBoolean("auto_hide_player_chrome", true),
                hideProgressBar = p.getBoolean("hide_player_progress_bar", false),
                hidePanelButton = p.getBoolean("hide_player_right_panel_button", false),
                alwaysBackButton = p.getBoolean("always_show_player_back_button", false),
            )
            loaded = true
        }
        return state
    }

    fun update(context: Context, change: (Settings) -> Settings) {
        flow(context)
        val next = change(state.value)
        state.value = next
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_hide_player_chrome", next.autoHide)
            .putBoolean("hide_player_progress_bar", next.hideProgressBar)
            .putBoolean("hide_player_right_panel_button", next.hidePanelButton)
            .putBoolean("always_show_player_back_button", next.alwaysBackButton)
            .apply()
    }
}
