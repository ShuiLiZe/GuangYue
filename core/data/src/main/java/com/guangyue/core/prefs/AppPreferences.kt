package com.guangyue.core.prefs

import android.content.Context
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.PartitaTuning
import com.guangyue.core.lyrics.TiltColorScheme
import com.guangyue.core.lyrics.TiltTuning
import com.guangyue.core.lyrics.CladdaghTuning
import com.guangyue.core.lyrics.fume.FumeCameraTrackingMode
import com.guangyue.core.lyrics.fume.FumeTuning
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

    /**
     * V1b-4：「浮名」调参。对应 Folia `fume/tuning.ts` storage.key = 'fume_tuning'；
     * 逐字段存，读写都过 [FumeTuning.sanitize]（同 Folia read/merge 的钳位与回落）。
     */
    var fumeTuning: FumeTuning
        get() = FumeTuning.sanitize(
            hidePrintSymbols = prefs.getBoolean(KEY_FUME_HIDE_PRINT, FumeTuning.DEFAULT.hidePrintSymbols),
            disableGeometricBackground = prefs.getBoolean(KEY_FUME_NO_GEOMETRY, FumeTuning.DEFAULT.disableGeometricBackground),
            backgroundObjectOpacity = prefs.getFloat(KEY_FUME_BG_OPACITY, FumeTuning.DEFAULT.backgroundObjectOpacity.toFloat()).toDouble(),
            textHoldRatio = prefs.getFloat(KEY_FUME_TEXT_HOLD, FumeTuning.DEFAULT.textHoldRatio.toFloat()).toDouble(),
            cameraTrackingMode = if (prefs.getString(KEY_FUME_TRACKING, null) == "stepped") {
                FumeCameraTrackingMode.STEPPED
            } else {
                FumeCameraTrackingMode.SMOOTH
            },
            cameraSpeed = prefs.getFloat(KEY_FUME_CAMERA_SPEED, FumeTuning.DEFAULT.cameraSpeed.toFloat()).toDouble(),
            glowIntensity = prefs.getFloat(KEY_FUME_GLOW, FumeTuning.DEFAULT.glowIntensity.toFloat()).toDouble(),
            heroScale = prefs.getFloat(KEY_FUME_HERO_SCALE, FumeTuning.DEFAULT.heroScale.toFloat()).toDouble(),
        )
        set(value) {
            prefs.edit()
                .putBoolean(KEY_FUME_HIDE_PRINT, value.hidePrintSymbols)
                .putBoolean(KEY_FUME_NO_GEOMETRY, value.disableGeometricBackground)
                .putFloat(KEY_FUME_BG_OPACITY, value.backgroundObjectOpacity.toFloat())
                .putFloat(KEY_FUME_TEXT_HOLD, value.textHoldRatio.toFloat())
                .putString(KEY_FUME_TRACKING, if (value.cameraTrackingMode == FumeCameraTrackingMode.STEPPED) "stepped" else "smooth")
                .putFloat(KEY_FUME_CAMERA_SPEED, value.cameraSpeed.toFloat())
                .putFloat(KEY_FUME_GLOW, value.glowIntensity.toFloat())
                .putFloat(KEY_FUME_HERO_SCALE, value.heroScale.toFloat())
                .apply()
        }

    /** V2e：「回环」调参（Folia claddagh/tuning.ts storage.key = 'claddagh_tuning'）。 */
    var claddaghTuning: CladdaghTuning
        get() = CladdaghTuning.sanitize(
            focus = prefs.getFloat(KEY_CL_FOCUS, CladdaghTuning.DEFAULT.focusScaleRatio.toFloat()).toDouble(),
            radius = prefs.getFloat(KEY_CL_RADIUS, CladdaghTuning.DEFAULT.radiusScale.toFloat()).toDouble(),
            tilt = prefs.getFloat(KEY_CL_TILT, CladdaghTuning.DEFAULT.ellipseTiltDeg.toFloat()).toDouble(),
            axis = prefs.getBoolean(KEY_CL_AXIS, CladdaghTuning.DEFAULT.showAxisLine),
            spacing = prefs.getFloat(KEY_CL_SPACING, CladdaghTuning.DEFAULT.letterSpacingOffset.toFloat()).toDouble(),
        )
        set(v) {
            prefs.edit()
                .putFloat(KEY_CL_FOCUS, v.focusScaleRatio.toFloat())
                .putFloat(KEY_CL_RADIUS, v.radiusScale.toFloat())
                .putFloat(KEY_CL_TILT, v.ellipseTiltDeg.toFloat())
                .putBoolean(KEY_CL_AXIS, v.showAxisLine)
                .putFloat(KEY_CL_SPACING, v.letterSpacingOffset.toFloat())
                .apply()
        }

    /** V2d：「倾诉」调参（Folia tilt/tuning.ts storage.key = 'tilt_tuning'）。 */
    var tiltTuning: TiltTuning
        get() = TiltTuning.sanitize(
            split = prefs.getFloat(KEY_TILT_SPLIT, TiltTuning.DEFAULT.splitProbability.toFloat()).toDouble(),
            tilt = prefs.getFloat(KEY_TILT_STYLE, TiltTuning.DEFAULT.tiltStyleProbability.toFloat()).toDouble(),
            scheme = TiltColorScheme.fromId(prefs.getString(KEY_TILT_SCHEME, null)),
        )
        set(value) {
            prefs.edit()
                .putFloat(KEY_TILT_SPLIT, value.splitProbability.toFloat())
                .putFloat(KEY_TILT_STYLE, value.tiltStyleProbability.toFloat())
                .putString(KEY_TILT_SCHEME, value.colorScheme.id)
                .apply()
        }

    /** V2a：「云阶」调参（Folia partita/tuning.ts storage.key = 'partita_tuning'），读写过 [PartitaTuning.sanitize]。 */
    var partitaTuning: PartitaTuning
        get() = PartitaTuning.sanitize(
            showGuideLines = prefs.getBoolean(KEY_PARTITA_GUIDE, PartitaTuning.DEFAULT.showGuideLines),
            useSemanticLayout = prefs.getBoolean(KEY_PARTITA_SEMANTIC, PartitaTuning.DEFAULT.useSemanticLayout),
            staggerMin = prefs.getFloat(KEY_PARTITA_MIN, PartitaTuning.DEFAULT.staggerMin.toFloat()).toDouble(),
            staggerMax = prefs.getFloat(KEY_PARTITA_MAX, PartitaTuning.DEFAULT.staggerMax.toFloat()).toDouble(),
        )
        set(value) {
            prefs.edit()
                .putBoolean(KEY_PARTITA_GUIDE, value.showGuideLines)
                .putBoolean(KEY_PARTITA_SEMANTIC, value.useSemanticLayout)
                .putFloat(KEY_PARTITA_MIN, value.staggerMin.toFloat())
                .putFloat(KEY_PARTITA_MAX, value.staggerMax.toFloat())
                .apply()
        }

    /** 用户启用的取链源 id，多个用逗号分隔，按启用顺序依次尝试。 */
    var lxEnabledIds: String
        get() = prefs.getString(KEY_LX_ENABLED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LX_ENABLED, value).apply()

    /**
     * 当前歌词渲染样式的落盘 id（V1a）。
     *
     * 对应 Folia `src/components/visualizer/registry.tsx` 里被选中的 `VISUALIZER_REGISTRY` 项：
     * 那边把样式 id 存进全局 store，这边存进同一个 SharedPreferences。
     *
     * 这里**只存 String**（"classic" / "fume"），默认 "classic"：`core/data` 不能依赖
     * `feature:player`，所以 `String? → 枚举` 的解析留在 feature 侧的
     * `LyricStyle.fromId`（未知 / 空值一律回落 CLASSIC），本层不做枚举校验 —— 也因此
     * 即便用户偏好里是个已删除的样式 id，读出来也是原样一个字符串，界面层能安全回落。
     */
    var lyricStyleId: String
        get() = prefs.getString(KEY_LYRIC_STYLE, DEFAULT_LYRIC_STYLE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_LYRIC_STYLE
        set(value) = prefs.edit().putString(KEY_LYRIC_STYLE, value).apply()

    /**
     * R2：歌词舞台背景类型（Folia `visualizerBackgroundMode`）。"latent" = 隐现（Folia 默认
     * DEFAULT_VISUALIZER_BACKGROUND_MODE），"common" = 通用（流体 + 几何）。
     */
    var visualizerBackgroundId: String
        get() = prefs.getString(KEY_VIS_BACKGROUND, "latent")?.takeIf { it == "latent" || it == "common" } ?: "latent"
        set(value) = prefs.edit().putString(KEY_VIS_BACKGROUND, value).apply()

    private companion object {
        const val KEY_VIS_BACKGROUND = "visualizer_background_mode"
        const val FILE = "guangyue_prefs"
        const val KEY_DARK = "ui_dark_mode"
        const val KEY_LAST_SOURCE = "search_last_source"
        const val KEY_LX_QUALITY = "lx_default_quality"
        const val KEY_LX_ENABLED = "lx_enabled_ids"

        /** 歌词样式 id（V1a）：对应「流光」(classic)，也是未知值的回落目标。 */
        const val KEY_LYRIC_STYLE = "lyrics_style_id"
        const val DEFAULT_LYRIC_STYLE = "classic"

        /** 「流光」歌词样式调参（对应 Folia classic/tuning.ts 的 storage.key = 'classic_tuning'）。 */
        const val KEY_CLASSIC_ROTATION = "classic_tuning_rotation"
        const val KEY_CLASSIC_BREATHING = "classic_tuning_breathing"
        const val KEY_CLASSIC_LEGACY = "classic_tuning_legacy"
        const val KEY_CLASSIC_SPACING = "classic_tuning_spacing"

        const val KEY_CL_FOCUS = "claddagh_tuning_focus"
        const val KEY_CL_RADIUS = "claddagh_tuning_radius"
        const val KEY_CL_TILT = "claddagh_tuning_tilt"
        const val KEY_CL_AXIS = "claddagh_tuning_axis"
        const val KEY_CL_SPACING = "claddagh_tuning_spacing"
        const val KEY_TILT_SPLIT = "tilt_tuning_split"
        const val KEY_TILT_STYLE = "tilt_tuning_style"
        const val KEY_TILT_SCHEME = "tilt_tuning_scheme"
        const val KEY_PARTITA_GUIDE = "partita_tuning_guide"
        const val KEY_PARTITA_SEMANTIC = "partita_tuning_semantic"
        const val KEY_PARTITA_MIN = "partita_tuning_stagger_min"
        const val KEY_PARTITA_MAX = "partita_tuning_stagger_max"

        /** 「浮名」调参（Folia fume/tuning.ts storage.key = 'fume_tuning'）。 */
        const val KEY_FUME_HIDE_PRINT = "fume_tuning_hide_print"
        const val KEY_FUME_NO_GEOMETRY = "fume_tuning_no_geometry"
        const val KEY_FUME_BG_OPACITY = "fume_tuning_bg_opacity"
        const val KEY_FUME_TEXT_HOLD = "fume_tuning_text_hold"
        const val KEY_FUME_TRACKING = "fume_tuning_tracking"
        const val KEY_FUME_CAMERA_SPEED = "fume_tuning_camera_speed"
        const val KEY_FUME_GLOW = "fume_tuning_glow"
        const val KEY_FUME_HERO_SCALE = "fume_tuning_hero_scale"

        /** 取链源支持的音质档，由低到高。 */
        val LX_QUALITIES = listOf("128k", "320k", "flac", "flac24bit")
    }
}
