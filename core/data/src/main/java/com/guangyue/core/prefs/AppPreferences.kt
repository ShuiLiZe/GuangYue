package com.guangyue.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级偏好（主题等）。
 *
 * 用 SharedPreferences 而不是 DataStore：首帧就要知道深浅色，`getBoolean` 读的是已经加载进内存的
 * Map，同步拿到不会卡主线程；写入走 `apply()` 异步落盘。DataStore 的 `data.first()` 必须 await，
 * 在 Compose 的 setContent 里没法同步用。
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    /**
     * 搜索页上次选中的音源（插件 id）。
     *
     * 空串 = 从未选过，搜索页会落到可选音源里的第一个。
     * 0.0.7 的搜索**只**打这一个插件，所以「上次选的」必须记住：不然每次进搜索页都得重选。
     */
    var lastSourceId: String
        get() = prefs.getString(KEY_LAST_SOURCE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_SOURCE, value).apply()

    private companion object {
        const val FILE = "guangyue_prefs"
        const val KEY_DARK = "ui_dark_mode"
        const val KEY_LAST_SOURCE = "search_last_source"
    }
}
