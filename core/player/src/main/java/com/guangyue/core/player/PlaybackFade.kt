package com.guangyue.core.player

import android.content.Context

/** 11-18 Folia「暂停/播放淡入淡出」（playbackFadeEnabled，默认开；PLAYBACK_FADE_SECONDS = 0.2）。 */
object PlaybackFade {
    const val SECONDS = 0.2
    private const val FILE = "playback_behavior"
    private const val KEY = "playback_fade"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
