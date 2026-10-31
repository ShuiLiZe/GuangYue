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
     * 用户是否已经**显式**选过一次深浅色。
     *
     * 0.0.9 起主题默认跟随系统（`uiMode`）；只有这里为 true（`ui_dark_mode` 写过一次）
     * 时才用 [darkMode] 的值覆盖系统，否则界面层跟着 `isSystemInDarkTheme()` 走。
     */
    val hasDarkMode: Boolean
        get() = prefs.contains(KEY_DARK)

    /**
     * 搜索页上次选中的音源（插件 id）。
     *
     * 空串 = 从未选过，搜索页会落到可选音源里的第一个。
     * 0.0.7 的搜索**只**打这一个插件，所以「上次选的」必须记住：不然每次进搜索页都得重选。
     */
    var lastSourceId: String
        get() = prefs.getString(KEY_LAST_SOURCE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_SOURCE, value).apply()

    /**
     * 0.0.8 起「当前音源」是**全局单选**：搜索页顶部的音源选择器和「我的 · 音源插件」页的
     * 单选列表读写同一个键，选一处等于选两处。
     *
     * 它取代了 0.0.7 的「每个插件一个开关 + 全选」—— 那套做法会让十几个插件同时处于启用
     * 状态，用户根本不知道一次搜索会打到谁。现在只有一个当前音源在跑。
     */
    var currentSourceId: String
        get() = lastSourceId
        set(value) {
            lastSourceId = value
        }

    /**
     * 取链源（洛雪 / LX Music 自定义源）每次取链的默认音质档。
     *
     * 取值 `128k` / `320k` / `flac` / `flac24bit`，与 LX 自定义源的 quality 枚举一致。
     */
    var lxDefaultQuality: String
        get() = prefs.getString(KEY_LX_QUALITY, "320k")?.takeIf { it in LX_QUALITIES } ?: "320k"
        set(value) = prefs.edit().putString(KEY_LX_QUALITY, value).apply()

    /** 用户启用的取链源 id，多个用逗号分隔，按启用顺序依次尝试。 */
    var lxEnabledIds: String
        get() = prefs.getString(KEY_LX_ENABLED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LX_ENABLED, value).apply()

    /**
     * P1：订阅分类（`main` 常用六平台 / `dj` / `radio` / `music` / `all`）。
     *
     * 只存数据层与设置项，本版还不做新 UI 页面；未知值一律回落到 `main`。
     * 分类清单文件是 `sub/<线路>/<分类>.json`，可选分类只换文件名。
     */
    var subscriptionCategory: String
        get() = prefs.getString(KEY_SUB_CATEGORY, "")?.takeIf { it in CATEGORIES } ?: CATEGORIES.first()
        set(value) = prefs.edit().putString(KEY_SUB_CATEGORY, value).apply()

    /**
     * P1：订阅线路（`padao` 加速主线路 / `githubio` Pages 备用 / `fastly` jsDelivr 备用）。
     *
     * 主线路失败会自动按主 → 备顺序回退，这个键只记「优先走哪条」。未知值回落 `padao`。
     */
    var subscriptionLine: String
        get() = prefs.getString(KEY_SUB_LINE, "")?.takeIf { it in LINES } ?: LINES.first()
        set(value) = prefs.edit().putString(KEY_SUB_LINE, value).apply()

    private companion object {
        const val FILE = "guangyue_prefs"
        const val KEY_DARK = "ui_dark_mode"
        const val KEY_LAST_SOURCE = "search_last_source"
        const val KEY_LX_QUALITY = "lx_default_quality"
        const val KEY_LX_ENABLED = "lx_enabled_ids"
        const val KEY_SUB_CATEGORY = "plugin_subscription_category"
        const val KEY_SUB_LINE = "plugin_subscription_line"

        /** 与 `SubscriptionRoutes.Category` 的 id 保持一致（core:plugin 不反向依赖 core:data）。 */
        val CATEGORIES = listOf("main", "dj", "radio", "music", "all")

        /** 与 `SubscriptionRoutes.Line` 的 id 保持一致。 */
        val LINES = listOf("padao", "githubio", "fastly")

        /** 取链源支持的音质档，由低到高。 */
        val LX_QUALITIES = listOf("128k", "320k", "flac", "flac24bit")
    }
}
