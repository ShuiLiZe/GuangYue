package com.guangyue.feature.player.lyrics.visualizer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 11-18：Folia 设置「动画与性能」与「歌词 → 时间与过滤」里广乐能落地的几项（进程级单例，播放页 / 首页背景 / 歌词同步共用）。
 *
 * - staticMode：Folia useThemeSettingsStore.staticMode（options.enableStaticMode「静态模式」：禁用几何背景以节省资源）。
 *   播放页与首页的隐现背景停在静帧，通用几何背景不画。
 * - homeBackgroundOff：Folia disableHomeDynamicBackground（「关闭主页动态背景」）。首页背景停在静帧。
 * - followSystem：Folia useMotionSettingsStore.followSystemReducedMotion（「跟随系统设置」）。
 *   ✋ Folia 默认关；广乐默认开：Android「移除动画」是无障碍开关，0.0.x 起一直跟随它（ReduceMotion.kt），不悄悄改掉老行为。
 * - lyricFilter：Folia「歌词过滤」的手机子集（core/data LyricLineRules），显示时套用，改了立刻重排当前歌词。
 * - globalLyricOffsetMs：Folia globalLyricTimelineOffsetMs（「全局时间偏移」，±2000ms），与每首歌自己的偏移相加。
 */
object MotionPrefs {
    data class Settings(
        val staticMode: Boolean = false,
        val homeBackgroundOff: Boolean = false,
        val followSystem: Boolean = true,
        val globalLyricOffsetMs: Long = 0L,
        /** Folia「歌词 → 歌词过滤」（LyricLineRules）。 */
        val lyricFilter: com.guangyue.core.data.lyrics.LyricFilterSettings = com.guangyue.core.data.lyrics.LyricFilterSettings(),
    )

    const val GLOBAL_OFFSET_LIMIT_MS = 2000L

    private const val FILE = "motion_prefs"
    private val state = MutableStateFlow(Settings())
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Settings> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            state.value = Settings(
                staticMode = p.getBoolean("static_mode", false),
                homeBackgroundOff = p.getBoolean("disable_home_dynamic_background", false),
                followSystem = p.getBoolean("reduce_motion_follow_system", true),
                globalLyricOffsetMs = clampOffset(p.getLong("global_lyric_timeline_offset_ms", 0L)),
                lyricFilter = com.guangyue.core.data.lyrics.LyricFilterSettings(
                    copyright = p.getBoolean("lyric_filter_copyright", false),
                    lyricSource = p.getBoolean("lyric_filter_source", false),
                    containsWords = p.getString("lyric_filter_words", "").orEmpty(),
                    written = p.getString("lyric_filter_written", "").orEmpty(),
                ),
            )
            loaded = true
        }
        return state
    }

    fun clampOffset(ms: Long): Long = ms.coerceIn(-GLOBAL_OFFSET_LIMIT_MS, GLOBAL_OFFSET_LIMIT_MS)

    fun update(context: Context, change: (Settings) -> Settings) {
        flow(context)
        val next = change(state.value).let { it.copy(globalLyricOffsetMs = clampOffset(it.globalLyricOffsetMs)) }
        state.value = next
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("static_mode", next.staticMode)
            .putBoolean("disable_home_dynamic_background", next.homeBackgroundOff)
            .putBoolean("reduce_motion_follow_system", next.followSystem)
            .putLong("global_lyric_timeline_offset_ms", next.globalLyricOffsetMs)
            .putBoolean("lyric_filter_copyright", next.lyricFilter.copyright)
            .putBoolean("lyric_filter_source", next.lyricFilter.lyricSource)
            .putString("lyric_filter_words", next.lyricFilter.containsWords)
            .putString("lyric_filter_written", next.lyricFilter.written)
            .apply()
    }
}
