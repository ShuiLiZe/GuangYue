package com.guangyue.core.player

import androidx.media3.datasource.cache.CacheDataSource
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.guangyue.core.plugin.di.PlaybackHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [MediaSourceResolver]: `plugin in → getMediaSource → direct url`.
 *
 * Lives in the player layer (not in :core:plugin) because quality switching, the one-shot
 * re-resolve and header plumbing are playback policy, not plugin runtime concerns.
 */
@Singleton
class PluginMediaSourceResolver @Inject constructor(
    private val bridge: MusicPluginBridge,
    private val tracks: TrackRepository,
) : MediaSourceResolver {

    override suspend fun resolve(origin: MediaOrigin): ResolvedSource = withContext(Dispatchers.IO) {
        // Prefer the exact payload the plugin produced (MusicFree contract), falling back to the
        // reconstructed item when the row was cached without raw JSON.
        val row = tracks.byKey(origin.trackKey)
        val pluginItem = row?.let { TrackMapper.toMusicItem(it) }
        val payload = pluginItem?.raw
            ?: row?.let {
                MusicItem(
                    platform = it.platform,
                    id = it.songId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artwork = it.artwork,
                    durationMs = it.durationMs,
                ).toJson()
            }

        val result = bridge.getMediaSource(
            pluginId = origin.pluginId,
            musicItemRaw = payload,
            item = pluginItem,
            quality = origin.quality.wire,
        ) ?: throw ResolutionException("插件「${origin.pluginId}」未能返回播放地址")

        ResolvedSource(result.url, result.headers, volatile = true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /**
     * 播放 / 解析直链用的 OkHttp。
     *
     * 必须带 [PlaybackHttp] 限定符：:core:download 还提供了一个「长读超时」的同类型客户端，
     * 两个 @Singleton OkHttpClient 会触发 Dagger DuplicateBindings。
     */
    @Provides
    @Singleton
    @PlaybackHttp
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun mediaSourceResolver(impl: PluginMediaSourceResolver): MediaSourceResolver = impl

    /**
     * 播放 / 预取共用的「带缓存的真实数据源」工厂。
     *
     * 抽成独立绑定是为了让 [PlaybackPrefetcher] 能直接构造 media3 官方
     * [androidx.media3.datasource.cache.CacheWriter]，而不是自己 hack SimpleCache 的
     * 逐段写入 API —— CacheWriter 需要的是 CacheDataSource，而 media3 1.11 的 SimpleCache
     * 不再暴露 startCacheWrite。
     */
    @Provides
    @Singleton
    fun cacheDataSourceFactory(provider: MediaDataSourceProvider): CacheDataSource.Factory =
        provider.preloadDataSourceFactory()
}
