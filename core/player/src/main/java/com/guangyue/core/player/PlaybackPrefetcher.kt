package com.guangyue.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.guangyue.core.plugin.MusicPluginBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 切歌预热（无缝切歌）。
 *
 * 当前曲播放进度到 80% 或剩余 ≤ 15 秒时，提前为「下一首」做两件事：
 *
 * 1. 调 [MusicPluginBridge.getMediaSource] 先把直链拿回来 —— 插件解析通常 0.5~3 秒，
 *    这一步省掉的正是切歌后的黑屏时间；
 * 2. 用 media3 官方 [CacheWriter] 把前 [PREFETCH_BYTES] 字节写进 [PlaybackCacheFactory]
 *    那张 SimpleCache。写入键与 [PlaybackController] 播放时的 mediaId 完全一致，
 *    新曲真正 prepare() 时 CacheDataSource 直接命中，起播时间近似为 0。
 *
 * 预取失败不阻塞播放：真实切歌时 [MediaSourceResolver] 自己会重新解析。
 */
@Singleton
@UnstableApi
class PlaybackPrefetcher @Inject constructor(
    private val bridge: MusicPluginBridge,
    private val cacheFactory: PlaybackCacheFactory,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 上一次预取的目标 key，避免同一首反复预取。 */
    @Volatile
    private var lastKey: String? = null

    @Volatile
    private var job: Job? = null

    /** 预热结果，UI / 日志用。 */
    @Volatile
    var lastResult: PrefetchResult = PrefetchResult.Idle
        private set

    /**
     * 预取 [item]。同一首重复调用是幂等的。
     *
     * @param force true 时即使已预取过也重来（例如切换音质后）
     */
    fun prefetch(item: QueueItem, force: Boolean = false) {
        if (!force && lastKey == item.key && lastResult !is PrefetchResult.Failed) return
        lastKey = item.key
        job?.cancel()
        job = scope.launch {
            mutex.withLock {
                lastResult = PrefetchResult.Running(item.key)
                lastResult = runCatching { doPrefetch(item) }
                    .getOrElse { PrefetchResult.Failed(item.key, it.message ?: "预取失败") }
            }
        }
    }

    /** 用户手动跳曲 / 停止时丢弃预取。 */
    fun reset() {
        job?.cancel()
        job = null
        lastKey = null
        lastResult = PrefetchResult.Idle
    }

    private suspend fun doPrefetch(item: QueueItem): PrefetchResult {
        val origin = item.origin
        if (origin.directUrl?.startsWith("file://") == true) {
            return PrefetchResult.Skipped(item.key, "本地文件")
        }

        val source = bridge.getMediaSource(
            pluginId = origin.pluginId,
            musicItemRaw = item.rawJson?.let { com.guangyue.core.plugin.Json2.parse(it) as? kotlinx.serialization.json.JsonObject },
            item = null,
            quality = origin.quality.wire,
        ) ?: return PrefetchResult.Failed(item.key, "插件未返回地址")

        if (source.url.startsWith("file://")) return PrefetchResult.Skipped(item.key, "本地文件")

        val mediaId = mediaIdFor(item.key)
        val cache = cacheFactory.cache
        if (cache.isCached(mediaId, 0L, PREFETCH_BYTES)) {
            return PrefetchResult.Hit(item.key)
        }

        // 只取前 PREFETCH_BYTES 字节，DataSpec.length 同时决定写进缓存的数据量。
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(source.url))
            .setKey(mediaId)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .setLength(PREFETCH_BYTES)
            .setHttpRequestHeaders(
                buildMap {
                    put("User-Agent", MediaDataSourceProvider.USER_AGENT)
                    putAll(source.headers)
                },
            )
            .build()

        CacheWriter(
            /* dataSource = */ cacheDataSourceFactory.createDataSource(),
            /* dataSpec = */ dataSpec,
            /* temporaryBuffer = */ null,
            /* progressListener = */ null,
        ).cache()

        return PrefetchResult.Prefetched(item.key, PREFETCH_BYTES)
    }

    companion object {
        /** 预热多少字节：约等于 320kbps 音频 60 秒，足够撑过切歌后的第一屏缓冲。 */
        const val PREFETCH_BYTES = 2_400_000L

        /** 和 PlaybackController 的 mediaId 保持一致：预取键 == 播放键。 */
        fun mediaIdFor(trackKey: String): String = trackKey

        /** 进度判定：是否该预热下一首（80% 或剩余 15 秒）。 */
        fun shouldPrefetch(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L) return false
            if (positionMs <= 0L) return false
            val byPercent = positionMs.toFloat() / durationMs >= 0.8f
            val byRemaining = durationMs - positionMs <= 15_000L
            return byPercent || byRemaining
        }
    }
}

/** 预取状态。 */
sealed interface PrefetchResult {
    data object Idle : PrefetchResult
    data class Running(val key: String) : PrefetchResult
    data class Hit(val key: String) : PrefetchResult
    data class Prefetched(val key: String, val bytes: Long) : PrefetchResult
    data class Skipped(val key: String, val reason: String) : PrefetchResult
    data class Failed(val key: String, val message: String) : PrefetchResult
}
