package com.guangyue.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局播放缓存（边听边存）。
 *
 * 采用 LRU 淘汰策略的 [SimpleCache]：听过的流会被写进磁盘，再次播放（尤其是重播、
 * 缓冲段回退）直接命中，不重复消耗源站流量。容量按「512MB 起，最多 1GB」动态决定：
 *
 * - 可用空间 < 2GB → 512MB（保底，避免把用户存储吃满）
 * - 可用空间 < 8GB → 768MB
 * - 其余          → 1GB
 *
 * 一个进程只能有一个 [SimpleCache] 实例，因此这里用 @Singleton 持有，并保证
 * [SimpleCache] 的锁目录不与下载模块混用（下载走 WorkManager，不经过这个缓存）。
 */
@Singleton
@UnstableApi
class PlaybackCacheFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 缓存目录：`filesDir/media_cache`（应用私有，卸载即清理，无需权限）。 */
    val cacheDir: File get() = File(context.filesDir, DIR_NAME)

    private val databaseProvider: StandaloneDatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    private val evictor: LeastRecentlyUsedCacheEvictor by lazy {
        LeastRecentlyUsedCacheEvictor(maxBytes())
    }

    val cache: SimpleCache by lazy {
        val dir = cacheDir
        if (!dir.exists()) dir.mkdirs()
        SimpleCache(dir, evictor, databaseProvider)
    }

    /** 预留空间（字节）：`cacheDir` 所在分区剩余空间 > [minFreeReserve] 时不额外限制。 */
    private fun maxBytes(): Long {
        val usable = runCatching { cacheDir.usableSpace }.getOrNull() ?: Long.MAX_VALUE
        val budget = when {
            usable < 2L * GB -> 512L * MB
            usable < 8L * GB -> 768L * MB
            else -> 1L * GB
        }
        // 至少给系统留 512MB 或可用空间的 10%
        val reserve = maxOf(512L * MB, usable / 10)
        return (usable - reserve).coerceAtLeast(256L * MB).coerceAtMost(budget)
    }

    /** 缓存占用的字节数。 */
    fun sizeBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0L)

    /** 清空缓存（设置页用）。 */
    fun clear() {
        runCatching {
            val keys = cache.keys.toList()
            keys.forEach { key -> cache.removeResource(key) }
        }
    }

    /** 释放底层数据库（进程退出 / 测试用）；应用生命周期内一般不需要。 */
    fun release() {
        runCatching { cache.release() }
    }

    companion object {
        private const val DIR_NAME = "media_cache"
        private const val MB = 1024L * 1024L
        private const val GB = 1024L * MB
    }
}
