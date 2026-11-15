package com.guangyue.feature.player.lyrics.visualizer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 町景的三项用户选择（Folia MachiSettingsPanel 的场景 / 天气 / 时段，默认都是 auto）。
 * 存 SharedPreferences machi_prefs，设置页改、播放页读，同进程用 StateFlow 推。
 */
object MachiPrefs {
    data class Choice(val scene: String = "auto", val weather: String = "auto", val timeOfDay: String = "auto")

    val SCENES = listOf("auto" to "按歌自动", "konbini" to "便利店街角", "station" to "加油站", "kowloon" to "城寨酒吧")
    val WEATHERS = listOf("auto" to "按歌自动", "clear" to "晴", "rain" to "雨", "snow" to "雪")
    val TIMES = listOf("auto" to "跟随时钟", "day" to "白天", "dusk" to "黄昏", "night" to "夜晚")

    private val state = MutableStateFlow(Choice())
    private var loaded = false

    fun flow(context: Context): StateFlow<Choice> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences("machi_prefs", Context.MODE_PRIVATE)
            state.value = Choice(
                p.getString("scene", "auto") ?: "auto",
                p.getString("weather", "auto") ?: "auto",
                p.getString("time", "auto") ?: "auto",
            )
            loaded = true
        }
        return state
    }

    fun set(context: Context, scene: String? = null, weather: String? = null, timeOfDay: String? = null) {
        flow(context)
        val next = state.value.copy(
            scene = scene ?: state.value.scene,
            weather = weather ?: state.value.weather,
            timeOfDay = timeOfDay ?: state.value.timeOfDay,
        )
        state.value = next
        context.applicationContext.getSharedPreferences("machi_prefs", Context.MODE_PRIVATE).edit()
            .putString("scene", next.scene).putString("weather", next.weather).putString("time", next.timeOfDay).apply()
    }
}
