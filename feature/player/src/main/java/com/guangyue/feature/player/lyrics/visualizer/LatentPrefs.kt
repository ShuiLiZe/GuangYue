package com.guangyue.feature.player.lyrics.visualizer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 11-18：Folia 隐现背景调参里手机上能落地的三项（latent/tuning.ts，DEFAULT_LATENT_BACKGROUND_TUNING），
 * 播放面板「背景类型」行的快捷控件改它（latent entry renderQuickControls）。进程级单例，播放页与首页背景共用。
 *
 * - displayMode：both 混合 / dithering 像素 / mesh 流体（showDithering = 不是 mesh，showMesh = 不是 dithering）。
 * - colorSource：cover-theme 封面与主题 / cover-only 仅封面取色（resolveLatentShaderColors）。
 * - overlayEnabled：可读性叠色（底色 @overlayOpacity 0.35）。
 */
object LatentPrefs {
    data class Settings(
        val displayMode: String = "both",
        val colorSource: String = "cover-theme",
        val overlayEnabled: Boolean = true,
    )

    val DISPLAY_MODES = listOf("dithering", "mesh", "both")
    val COLOR_SOURCES = listOf("cover-theme", "cover-only")

    fun displayLabel(mode: String) = when (mode) { "dithering" -> "像素"; "mesh" -> "流体"; else -> "混合" }
    fun colorLabel(source: String) = if (source == "cover-only") "仅封面取色" else "封面与主题"

    /** Folia cycleValue：下一项（到底回第一项）。 */
    fun <T> cycle(list: List<T>, current: T): T = list[(list.indexOf(current) + 1).mod(list.size)]

    private const val FILE = "latent_background_tuning"
    private val state = MutableStateFlow(Settings())
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Settings> {
        if (!loaded) {
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            state.value = Settings(
                displayMode = p.getString("display_mode", "both").takeIf { it in DISPLAY_MODES } ?: "both",
                colorSource = p.getString("color_source", "cover-theme").takeIf { it in COLOR_SOURCES } ?: "cover-theme",
                overlayEnabled = p.getBoolean("overlay_enabled", true),
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
            .putString("display_mode", next.displayMode)
            .putString("color_source", next.colorSource)
            .putBoolean("overlay_enabled", next.overlayEnabled)
            .apply()
    }
}
