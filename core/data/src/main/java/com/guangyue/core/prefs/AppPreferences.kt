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

    private companion object {
        const val FILE = "guangyue_prefs"
        const val KEY_DARK = "ui_dark_mode"
    }
}
