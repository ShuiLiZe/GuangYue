package com.guangyue.core.player

import androidx.media3.datasource.cache.CacheDataSource
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.prefs.AppPreferences
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.lx.LxSourceManager
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
    private val lx: LxSourceManager,
    private val prefs: AppPreferences,
) : MediaSourceResolver {

    override suspend fun resolve(origin: MediaOrigin): ResolvedSource = withContext(Dispatchers.IO) {
        // Prefer the exact payload the plugin produced (MusicFree contract), falling back to the
        // reconstructed item when the row was cached without raw JSON.
        val row = tracks.byKey(origin.trackKey)
        android.util.Log.d("GYHOME", "resolve key=" + origin.trackKey + " row=" + (row != null) + " raw=" + (if (row?.rawJson != null) "yes" else "no"))
        // 首页 / 歌单点进来的曲目可能还没落本地库（byKey 为 null）。这时用 origin 里的
        // 平台+歌曲 id 兜底：插件取链可以失败，但洛雪取链源只要有 platform+id 就能取到。
        val pluginItem = row?.let { TrackMapper.toMusicItem(it) }
            ?: origin.let { o ->
                val (platform, songId) = o.trackKey.split(":", limit = 2).takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] } ?: return@let null
                MusicItem(
                    platform = platform,
                    id = songId,
                    title = "",
                    artist = "",
                    album = null,
                    artwork = null,
                    durationMs = null,
                )
            }
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

        val label = row?.title?.takeIf { it.isNotBlank() } ?: "这首歌"
        val result = bridge.getMediaSource(
            pluginId = origin.pluginId,
            musicItemRaw = payload,
            item = pluginItem,
            quality = origin.quality.wire,
        )

        if (result != null) {
            return@withContext ResolvedSource(result.url, result.headers, volatile = true)
        }

        // 插件自己取不到链时，按启用顺序问「取链源」（洛雪自定义源）。
        // 它们只提供 musicUrl，不参与搜索；这里把曲目映射成洛雪 musicInfo 再问。
        val lxItem = pluginItem ?: row?.let {
            MusicItem(
                platform = it.platform,
                id = it.songId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                artwork = it.artwork,
                durationMs = it.durationMs,
            )
        }
        if (lxItem != null) {
            val fallback = runCatching {
                lx.resolveMediaUrl(lxItem, origin.quality.wire, prefs.lxDefaultQuality)
            }.getOrNull()
            if (!fallback.isNullOrBlank()) {
                return@withContext ResolvedSource(fallback, volatile = true)
            }
        }

        throw ResolutionException("这个音源取不到「$label」的播放地址")
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
