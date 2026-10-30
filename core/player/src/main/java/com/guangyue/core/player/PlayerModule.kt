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
import kotlinx.coroutines.withTimeoutOrNull
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val app: android.content.Context,
    private val bridge: MusicPluginBridge,
    private val tracks: TrackRepository,
    private val lx: LxSourceManager,
    private val prefs: AppPreferences,
    private val health: com.guangyue.core.plugin.PlaybackHealthHook,
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
        val payload = row?.rawJson
            ?.let { runCatching { com.guangyue.core.plugin.Json2.parse(it) as? kotlinx.serialization.json.JsonObject }.getOrNull() }
            ?: pluginItem?.raw
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
        // 0.0.9 b：这条曲目归哪个音源 —— 插件直出与洛雪兜底成功后都用它把该插件标成「可用」。
        val pluginKey = origin.pluginId.takeIf { it.isNotBlank() }
            ?: row?.platform?.takeIf { it.isNotBlank() }
        // 插件取链通常 5~15s（慢网/聚合源更久）。洛雪取链源（自带五源脚本）在 PC 上实测 1 秒级，
        // 所以把插件取链压到较短预算：超时就直接落到洛雪源，别让用户等一分钟。
        val result = runCatching {
            withTimeoutOrNull(PLUGIN_RESOLVE_BUDGET_MS) {
                bridge.getMediaSource(
                    pluginId = origin.pluginId,
                    musicItemRaw = payload,
                    item = pluginItem,
                    quality = origin.quality.wire,
                )
            }
        }.onFailure { android.util.Log.w("GYHOME", "plugin getMediaSource threw: " + it.message) }
            .getOrNull()

        if (result != null) {
            // 拿到播放地址就把播放服务拉起来，这样一开播就有 media session（通知栏/锁屏/媒体键）。
            GuangYuePlaybackService.start(app)
            // 插件自己真取到了链 → 体检结论按实测改写（管理页由「取不到链」变「可用」）。
            health.recordPlayback(pluginKey, "插件直出")
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
            }.onFailure { android.util.Log.w("GYHOME", "lx fallback threw: " + it.message) }
                .getOrNull()
            android.util.Log.d("GYHOME", "lx fallback for " + origin.trackKey + " -> " + (fallback ?: "null"))
            if (!fallback.isNullOrBlank()) {
                // 「能出声」就算是可用：洛雪取链源兜底成功也说明这个音源能播，
                // 状态里写明是哪个取链源给的地址，别让列表继续显示「取不到链」。
                health.recordPlayback(pluginKey, "洛雪取链源「${lx.lastResolvedBy ?: "已启用源"}」")
                return@withContext ResolvedSource(fallback, volatile = true)
            }
        }

        throw ResolutionException("这个音源取不到「$label」的播放地址")
    }

    private companion object {
        /**
         * 插件 getMediaSource 的时间预算。
         *
         * 第三方聚合插件在弱网/挂掉时要几十秒才超时，而超时后还必须再等洛雪源取一次，
         * 用户看到的就是「点播放一直转、进度不动」。8 秒没回来就跳到洛雪取链源。
         */
        const val PLUGIN_RESOLVE_BUDGET_MS = 8_000L
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
