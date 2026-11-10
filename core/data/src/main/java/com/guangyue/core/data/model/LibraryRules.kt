package com.guangyue.core.data.model

/**
 * 曲库的合并规则（纯函数，JVM 单测覆盖）。
 *
 * 2026-11-10 用户真机：下载后点收藏，「我喜欢」里还是没有。根因有三：
 *  1. 每次开播 / 搜索都 `REPLACE` 整行写库，把 favorite / 播放次数 / 加入时间冲回默认值；
 *  2. 收藏是 `UPDATE … WHERE key`，库里还没这一行（下载页直接开播、写库是异步的）就什么都没改；
 *  3. 「我的 → 我喜欢」入口跳的是搜索页。
 * 这里把「写库时保留用户状态」和「收藏时没有行就先建行」收成两条规则，DAO 只管执行。
 *
 * 同一首歌在线版与下载版共用一个 key（`platform:songId`，下载任务的 key 就是在线曲目的 key），
 * 所以收藏状态天然互认；本地文件信息（source=local + localUri）在线行写进来时不丢。
 */
object LibraryRules {

    /** [incoming] 要写进库时与库里已有行 [existing] 合并：元数据取新的（空值不覆盖），用户状态取旧的。 */
    fun merge(existing: TrackEntity?, incoming: TrackEntity): TrackEntity {
        if (existing == null) return incoming
        val keepLocal = incoming.localUri.isNullOrBlank() && existing.source == TrackEntity.SOURCE_LOCAL &&
            !existing.localUri.isNullOrBlank()
        return incoming.copy(
            title = incoming.title.ifBlank { existing.title },
            artist = incoming.artist.ifBlank { existing.artist },
            album = incoming.album.ifBlank { existing.album },
            artwork = incoming.artwork?.takeIf { it.isNotBlank() } ?: existing.artwork,
            durationMs = if (incoming.durationMs > 0L) incoming.durationMs else existing.durationMs,
            platform = incoming.platform.ifBlank { existing.platform },
            songId = incoming.songId.ifBlank { existing.songId },
            rawJson = incoming.rawJson?.takeIf { it.isNotBlank() } ?: existing.rawJson,
            source = if (keepLocal) TrackEntity.SOURCE_LOCAL else incoming.source,
            localUri = if (keepLocal) existing.localUri else incoming.localUri,
            fileSize = if (keepLocal) existing.fileSize else incoming.fileSize,
            favorite = existing.favorite,
            playCount = existing.playCount,
            lastPlayedAt = existing.lastPlayedAt,
            addedAt = existing.addedAt,
        )
    }

    /**
     * 点收藏 / 取消收藏后要写的行：库里没有就用 [fallback] 建行。
     * 收藏时把 addedAt 记成收藏时间，「我喜欢」按它倒序 = 最近收藏的在最上面（Folia 的我喜欢歌单也是追加到最前）。
     */
    fun favorite(existing: TrackEntity?, fallback: TrackEntity, favorite: Boolean, now: Long): TrackEntity {
        val merged = if (existing == null) fallback else merge(existing, fallback)
        return merged.copy(favorite = favorite, addedAt = if (favorite && existing?.favorite != true) now else merged.addedAt)
    }

    /** 记一次播放：没有行先建行。 */
    fun played(existing: TrackEntity?, track: TrackEntity, now: Long): TrackEntity {
        val merged = merge(existing, track)
        return merged.copy(playCount = merged.playCount + 1, lastPlayedAt = now)
    }

    /** MediaStore 音频 uri 的数字 id（`content://media/external/audio/media/42` → 42），别的形式返回 null。 */
    fun mediaStoreId(uri: String?): String? {
        if (uri.isNullOrBlank() || !uri.startsWith("content://media/")) return null
        if (!uri.contains("/audio/media/")) return null
        return uri.substringAfterLast('/').substringBefore('?').takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }
    }

    /**
     * 「全部歌曲」= 已下载 + 手机里的本地音乐（Folia 本地页签的 all-songs 虚拟文件夹）。
     * 下载的歌本身也在 MediaStore 里（Music/GuangYue/），按文件去重，保留下载版（带歌词、平台 id）。
     * [favoriteKeys] 用来把收藏态盖到每一行上。
     */
    fun allSongs(downloaded: List<TrackEntity>, device: List<TrackEntity>, favoriteKeys: Set<String> = emptySet()): List<TrackEntity> {
        val seenUris = downloaded.mapNotNull { it.localUri }.toHashSet()
        val seenIds = downloaded.mapNotNull { mediaStoreId(it.localUri) }.toHashSet()
        val keys = downloaded.map { it.key }.toHashSet()
        val rest = device.filter { d ->
            d.key !in keys && d.localUri !in seenUris && (mediaStoreId(d.localUri)?.let { it !in seenIds } ?: true)
        }
        return (downloaded + rest).map { if (it.key in favoriteKeys && !it.favorite) it.copy(favorite = true) else it }
    }

    /**
     * 「我喜欢 / 最近播放」里的一行开播前的样子：下载过就换成下载版（本地文件 + 封面 + 歌词），
     * 收藏态保留；库里记着本地文件但下载已删了（非手机本地音乐）就退回在线取链。
     */
    fun playable(row: TrackEntity, downloaded: TrackEntity?): TrackEntity = when {
        downloaded != null -> downloaded.copy(
            favorite = row.favorite,
            playCount = row.playCount,
            lastPlayedAt = row.lastPlayedAt,
            addedAt = row.addedAt,
            durationMs = if (downloaded.durationMs > 0L) downloaded.durationMs else row.durationMs,
            rawJson = downloaded.rawJson ?: row.rawJson,
            artwork = downloaded.artwork ?: row.artwork,
        )
        row.source == TrackEntity.SOURCE_LOCAL && !row.key.startsWith(DEVICE_PREFIX) ->
            row.copy(source = TrackEntity.SOURCE_PLUGIN, localUri = null, fileSize = 0L)
        else -> row
    }

    /** 手机本地音乐（MediaStore）行的 key 前缀：`mediastore:<id>`。 */
    const val DEVICE_PREFIX = "mediastore:"
}
