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

    fun localUriFor(key: String): String? = find(key)?.uri

    fun totalBytes(): Long = _tracks.value.sumOf { it.sizeBytes }

    // ---------------------------------------------------------------- private

    private fun deleteQuietly(track: DownloadedTrack) {
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
                append("\"downloadedAt\":").append(t.downloadedAt)
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
