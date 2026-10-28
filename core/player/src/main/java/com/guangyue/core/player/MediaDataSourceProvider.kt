package com.guangyue.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the data-source stack every playback goes through:
 *
 * ```
 * ResolvingDataSource ──> plugin getMediaSource (only when playback actually starts)
 *        └──> DefaultDataSource ──> OkHttpDataSource (plugin headers, UA)
 * ```
 *
 * Nothing here is resolved ahead of time: only the ResolvingDataSource knows how to unpack a
 * `guangyue://resolve/<key>` uri, so opening a playlist costs zero plugin calls.
 */
@Singleton
@UnstableApi
class MediaDataSourceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    val resolver: MediaSourceResolver,
) {
    val pluginResolver: PluginResolver = PluginResolver(resolver, RESOLVE_TIMEOUT_MS, LogTag)

    fun okHttpFactory(): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient).setUserAgent(USER_AGENT)

    fun factory(): ResolvingDataSource.Factory =
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context, okHttpFactory()), pluginResolver)

    companion object {
        private const val LogTag = "GuangYueDataSource"
        const val USER_AGENT = "GuangYue/1.0 (Android)"
        const val RESOLVE_TIMEOUT_MS = 15_000L
    }
}
