package com.guangyue.core.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一首已下载曲目的本地记录（用于「下载管理」已完成列表 + 离线播放）。 */
@Serializable
data class DownloadedTrack(
    val key: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val quality: String = "high",
    val downloadedAt: Long = System.currentTimeMillis(),
    /** 插件原始 musicItem JSON（离线播放时还原歌词 / 再次取链）。 */
    val rawJson: String? = null,
    /** 原始网络封面地址（[artwork] 优先放本地封面文件 file://）。 */
    val artworkUrl: String? = null,
    /** App 私有目录里的歌词 .lrc（原文）。 */
    val lyricPath: String? = null,
    /** App 私有目录里的翻译 .lrc。 */
    val translationPath: String? = null,
    /** 公共目录 Download/GuangYue/ 里的 .lrc uri（删除时一起删）。 */
    val lrcUri: String? = null,
) {
    /** 形如 `12.3 MB`。 */
    val sizeText: String get() = DownloadProgress.formatBytes(sizeBytes)
}

/**
 * 已完成下载的持久化索引（`filesDir/downloads/index.json`）。
 *
 * 为什么不用 Room：这份数据只被「下载管理」页与离线判断读到，量级几百条；用 JSON 文件
 * 能让 :core:download 不反向依赖 :core:data 的 schema 迁移，升级更省心。
 */
@Singleton
class DownloadedLibrary @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) {
    private val appContext: Context = context.applicationContext
    private val file: File = File(appContext.filesDir, "downloads").apply { mkdirs() }
        .resolve("index.json")

    private val _tracks = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val tracks: StateFlow<List<DownloadedTrack>> = _tracks.asStateFlow()

    init {
        _tracks.value = read()
    }

    fun record(track: DownloadedTrack) {
        _tracks.update { list ->
            val next = list.filterNot { it.key == track.key } + track
            persist(next)
            next.sortedByDescending { it.downloadedAt }
        }
    }

    fun remove(key: String, deleteFile: Boolean = true) {
        val target = _tracks.value.firstOrNull { it.key == key } ?: return
        if (deleteFile) deleteQuietly(target)
        _tracks.update { list ->
            val next = list.filterNot { it.key == key }
            persist(next)
            next
        }
    }

    fun find(key: String): DownloadedTrack? = _tracks.value.firstOrNull { it.key == key }

    /** 音频文件是否还在（用户可能在文件管理器 / 系统音乐 App 里删掉了）。 */
    fun exists(track: DownloadedTrack): Boolean = runCatching {
        val uri = Uri.parse(track.uri)
        when (uri.scheme) {
            "content" -> appContext.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
            "file", null -> uri.path?.let { File(it).isFile } ?: false
            else -> true
        }
    }.getOrDefault(true)

    /** 去掉文件已不存在的记录（不删任何文件）；返回清掉的条数。 */
    fun pruneMissing(): Int {
        val gone = _tracks.value.filterNot(::exists)
        if (gone.isEmpty()) return 0
        gone.forEach { deletePrivate(it) }
        _tracks.update { list ->
            val keys = gone.map { it.key }.toSet()
            val next = list.filterNot { it.key in keys }
            persist(next)
            next
        }
        return gone.size
    }

    /** 下载封面 / 歌词的私有目录。 */
    fun coverFile(key: String): File = File(appContext.filesDir, "downloads/covers").apply { mkdirs() }
        .resolve(safe(key) + ".jpg")

    fun lyricFile(key: String, translation: Boolean = false): File =
        File(appContext.filesDir, "downloads/lyrics").apply { mkdirs() }
            .resolve(safe(key) + if (translation) ".trans.lrc" else ".lrc")

    /**
     * 离线播放用的曲目行：本地封面（没有就从音频内嵌封面里抠出来存一份）+ 歌词塞进 rawJson
     * （`rawLrc` / `translation`，歌词页优先读它），platform/songId 留给插件兜底取歌词。
     * 会做文件 IO，放后台线程调。
     */
    fun toPlayable(track: DownloadedTrack): com.guangyue.core.data.model.TrackEntity {
        val parts = track.key.split(":", limit = 2)
        var artwork = track.artwork
        val localCover = artwork?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path }?.let(::File)
        if (artwork.isNullOrBlank() || (localCover != null && !localCover.isFile)) {
            artwork = extractEmbeddedCover(track) ?: track.artworkUrl ?: artwork?.takeIf { localCover == null }
            if (artwork != null && artwork != track.artwork) record(track.copy(artwork = artwork))
        }
        val lrc = track.lyricPath?.let(::File)?.takeIf { it.isFile }?.readText()
        val trans = track.translationPath?.let(::File)?.takeIf { it.isFile }?.readText()
        val base = track.rawJson?.let { runCatching { com.guangyue.core.plugin.Json2.parse(it) as? kotlinx.serialization.json.JsonObject }.getOrNull() }
        val raw = if (base == null && lrc == null) null else kotlinx.serialization.json.buildJsonObject {
            base?.forEach { (k, v) -> put(k, v) }
            if (!lrc.isNullOrBlank()) put("rawLrc", kotlinx.serialization.json.JsonPrimitive(lrc))
            if (!trans.isNullOrBlank()) put("translation", kotlinx.serialization.json.JsonPrimitive(trans))
        }.toString()
        return com.guangyue.core.data.model.TrackEntity(
            key = track.key,
            title = track.title,
            artist = track.artist,
            album = track.album.orEmpty(),
            artwork = artwork,
            platform = parts.getOrElse(0) { "" },
            songId = parts.getOrElse(1) { "" },
            quality = track.quality,
            rawJson = raw,
            localUri = track.uri,
            fileSize = track.sizeBytes,
            source = com.guangyue.core.data.model.TrackEntity.SOURCE_LOCAL,
        )
    }

    /** 从音频文件内嵌封面里取图存到私有目录，返回 file:// uri。 */
    private fun extractEmbeddedCover(track: DownloadedTrack): String? = runCatching {
        val mmr = android.media.MediaMetadataRetriever()
        try {
            val uri = Uri.parse(track.uri)
            if (uri.scheme == "content") mmr.setDataSource(appContext, uri) else mmr.setDataSource(uri.path)
            val bytes = mmr.embeddedPicture ?: return null
            val out = coverFile(track.key)
            out.writeBytes(bytes)
            Uri.fromFile(out).toString()
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrNull()

    fun localUriFor(key: String): String? = find(key)?.uri

    fun totalBytes(): Long = _tracks.value.sumOf { it.sizeBytes }

    // ---------------------------------------------------------------- private

    private fun deletePrivate(track: DownloadedTrack) {
        runCatching { track.lyricPath?.let { File(it).delete() } }
        runCatching { track.translationPath?.let { File(it).delete() } }
        runCatching { track.artwork?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path }?.let { File(it).delete() } }
    }

    private fun safe(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun deleteQuietly(track: DownloadedTrack) {
        deletePrivate(track)
        runCatching {
            track.lrcUri?.let { Uri.parse(it) }?.let { u ->
                if (u.scheme == "content") appContext.contentResolver.delete(u, null, null) else u.path?.let { File(it).delete() }
            }
        }
        runCatching {
            val uri = Uri.parse(track.uri)
            if (uri.scheme == "content") {
                appContext.contentResolver.delete(uri, null, null)
            } else {
                uri.path?.let { File(it).delete() }
                MediaScannerConnection.scanFile(appContext, arrayOf(uri.path ?: ""), null, null)
            }
        }
    }

    private fun read(): List<DownloadedTrack> = runCatching {
        if (!file.isFile) return emptyList()
        Json2Index.decode(file.readText())
    }.getOrDefault(emptyList())

    private fun persist(list: List<DownloadedTrack>) {
        runCatching { file.writeText(Json2Index.encode(list)) }
    }

    /** 极简 JSON 编解码（避免为了 5 个字段引入 kotlinx-serialization 的依赖链）。 */
    private object Json2Index {
        fun encode(list: List<DownloadedTrack>): String = buildString {
            append("[\n")
            list.forEachIndexed { i, t ->
                append("  {")
                append("\"key\":").append(q(t.key)).append(',')
                append("\"title\":").append(q(t.title)).append(',')
                append("\"artist\":").append(q(t.artist)).append(',')
                append("\"album\":").append(q(t.album ?: "")).append(',')
                append("\"artwork\":").append(q(t.artwork ?: "")).append(',')
                append("\"uri\":").append(q(t.uri)).append(',')
                append("\"fileName\":").append(q(t.fileName)).append(',')
                append("\"sizeBytes\":").append(t.sizeBytes).append(',')
                append("\"quality\":").append(q(t.quality)).append(',')
                append("\"downloadedAt\":").append(t.downloadedAt).append(',')
                append("\"rawJson\":").append(q(t.rawJson ?: "")).append(',')
                append("\"artworkUrl\":").append(q(t.artworkUrl ?: "")).append(',')
                append("\"lyricPath\":").append(q(t.lyricPath ?: "")).append(',')
                append("\"translationPath\":").append(q(t.translationPath ?: "")).append(',')
                append("\"lrcUri\":").append(q(t.lrcUri ?: ""))
                append("}")
                if (i < list.lastIndex) append(',')
                append('\n')
            }
            append("]\n")
        }

        fun decode(text: String): List<DownloadedTrack> {
            val arr = com.guangyue.core.plugin.Json2.parse(text) as? kotlinx.serialization.json.JsonArray
                ?: return emptyList()
            return arr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                DownloadedTrack(
                    key = s("key") ?: return@mapNotNull null,
                    title = s("title").orEmpty(),
                    artist = s("artist").orEmpty(),
                    album = s("album")?.takeIf { it.isNotBlank() },
                    artwork = s("artwork")?.takeIf { it.isNotBlank() },
                    uri = s("uri").orEmpty(),
                    fileName = s("fileName").orEmpty(),
                    sizeBytes = s("sizeBytes")?.toLongOrNull() ?: 0L,
                    quality = s("quality") ?: "high",
                    downloadedAt = s("downloadedAt")?.toLongOrNull() ?: 0L,
                    rawJson = s("rawJson")?.takeIf { it.isNotBlank() },
                    artworkUrl = s("artworkUrl")?.takeIf { it.isNotBlank() },
                    lyricPath = s("lyricPath")?.takeIf { it.isNotBlank() },
                    translationPath = s("translationPath")?.takeIf { it.isNotBlank() },
                    lrcUri = s("lrcUri")?.takeIf { it.isNotBlank() },
                )
            }
        }

        private fun q(v: String): String = buildString {
            append('"')
            for (c in v) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
    }
}
