package com.guangyue.core.prefs

import android.content.Context
import com.guangyue.core.lyrics.ClassicTuning
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

    /**
     * 歌词样式「流光」（Folia 的 classic）的调参。
     *
     * 对应 Folia `classic/tuning.ts` 的 storage.key = 'classic_tuning'：那边存 JSON，
     * 这边按同一份字段存成一行竖线分隔的文本（`旋转|呼吸浮动|legacy布局|词距`），
     * 存储键沿用 `classic_tuning`，读取时用内核的 [ClassicTuning.sanitize] 做同一套钳位
     * （呼吸浮动与词距都钳到 [0,2]，非法值退回默认；默认词距 0.7、旋转开、legacy 关）。
     * 解析失败一律退回 [ClassicTuning.sanitize] 给出的默认值，不抛异常。
     */
    var classicTuning: ClassicTuning
        get() = ClassicTuning.sanitize(
            enableWordRotation = prefs.getBoolean(KEY_CLASSIC_ROTATION, true),
            breathingFloatMultiplier = prefs.getFloat(KEY_CLASSIC_BREATHING, 1f).toDouble(),
            useLegacyLayout = prefs.getBoolean(KEY_CLASSIC_LEGACY, false),
            wordSpacing = prefs.getFloat(KEY_CLASSIC_SPACING, 0.7f).toDouble(),
        )
        set(value) {
            val safe = ClassicTuning.sanitize(
                enableWordRotation = value.enableWordRotation,
                breathingFloatMultiplier = value.breathingFloatMultiplier,
                useLegacyLayout = value.useLegacyLayout,
                wordSpacing = value.wordSpacing,
            )
            prefs.edit()
                .putBoolean(KEY_CLASSIC_ROTATION, safe.enableWordRotation)
                .putFloat(KEY_CLASSIC_BREATHING, safe.breathingFloatMultiplier.toFloat())
                .putBoolean(KEY_CLASSIC_LEGACY, safe.useLegacyLayout)
                .putFloat(KEY_CLASSIC_SPACING, safe.wordSpacing.toFloat())
                .apply()
        }

    /** 用户启用的取链源 id，多个用逗号分隔，按启用顺序依次尝试。 */
    var lxEnabledIds: String
        get() = prefs.getString(KEY_LX_ENABLED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LX_ENABLED, value).apply()

    private companion object {
        const val FILE = "guangyue_prefs"
        const val KEY_DARK = "ui_dark_mode"
        const val KEY_LAST_SOURCE = "search_last_source"
        const val KEY_LX_QUALITY = "lx_default_quality"
        const val KEY_LX_ENABLED = "lx_enabled_ids"

        /** 「流光」歌词样式调参（对应 Folia classic/tuning.ts 的 storage.key = 'classic_tuning'）。 */
        const val KEY_CLASSIC_ROTATION = "classic_tuning_rotation"
        const val KEY_CLASSIC_BREATHING = "classic_tuning_breathing"
        const val KEY_CLASSIC_LEGACY = "classic_tuning_legacy"
        const val KEY_CLASSIC_SPACING = "classic_tuning_spacing"

        /** 取链源支持的音质档，由低到高。 */
        val LX_QUALITIES = listOf("128k", "320k", "flac", "flac24bit")
    }
}
