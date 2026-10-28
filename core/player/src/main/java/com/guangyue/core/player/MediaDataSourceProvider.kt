package com.guangyue.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
 *        └──> CacheDataSource ──> SimpleCache (LRU, 边听边存)
 *                 └──> DefaultDataSource ──> OkHttpDataSource (plugin headers, UA)
 * ```
 *
 * [CacheDataSource] 是关键一环：`guangyue://` 地址解析成真实直链后，同一段音频会被写进
 * [PlaybackCacheFactory.cache]，重播 / 回退 / 拖动时命中本地，省流量也省解析次数。
 * 缓存只对可随机读写的 http(s) 生效，插件返回的本地文件会自动跳过（详见
 * [CacheDataSource.Factory.setCacheWriteDataSinkFactory] 的说明）。
 *
 * Nothing here is resolved ahead of time: only the ResolvingDataSource knows how to unpack a
 * `guangyue://resolve/<key>` uri, so opening a playlist costs zero plugin calls.
 */
@Singleton
@UnstableApi
class MediaDataSourceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @com.guangyue.core.plugin.di.PlaybackHttp private val okHttpClient: OkHttpClient,
    private val cacheFactory: PlaybackCacheFactory,
    val resolver: MediaSourceResolver,
) {

    val pluginResolver: PluginResolver = PluginResolver(resolver, RESOLVE_TIMEOUT_MS, LogTag)

    fun okHttpFactory(): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient).setUserAgent(USER_AGENT)

    /** 上游真实数据源（带插件 headers）。 */
    private fun upstream(): DefaultDataSource.Factory =
        DefaultDataSource.Factory(context, okHttpFactory())

    /** 带缓存的真实数据源：读优先命中缓存，写同时落盘（边听边存）。 */
    fun cachedUpstream(): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cacheFactory.cache)
        .setUpstreamDataSourceFactory(upstream())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun factory(): ResolvingDataSource.Factory =
        ResolvingDataSource.Factory(cachedUpstream(), pluginResolver)

    /** 预加载专用：和播放链路共用同一个缓存，所以在解析之前就先把数据写进去了。 */
    fun preloadDataSourceFactory(): CacheDataSource.Factory = cachedUpstream()

    companion object {
        private const val LogTag = "GuangYueDataSource"
        const val USER_AGENT = "GuangYue/1.0 (Android)"
        const val RESOLVE_TIMEOUT_MS = 15_000L
    }
}
