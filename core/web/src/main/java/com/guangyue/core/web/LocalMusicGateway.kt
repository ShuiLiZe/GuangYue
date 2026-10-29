package com.guangyue.core.web

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地音乐条目。
 *
 * 广乐原本只有「插件曲」和「下载曲」两种来源（见 TrackEntity.source）；Folia 的「本地」页签
 * 还需要 MediaStore 里的手机音频。这里把 MediaStore 与下载目录统一成一种 DTO，前端拿到的
 * 形状与插件曲一致，`sourceRef.kind = 'local'` 时走本地播放。
 */
data class LocalSongEntry(
    /** 稳定 id：`mediastore:<id>` 或 `download:<key>`。 */
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** 供 `<img>` 用的封面地址；空串表示没有。 */
    val artworkUrl: String,
    val contentUri: String,
    val sizeBytes: Long,
    val mimeType: String,
    val fileName: String,
    val folder: String,
    val addedAt: Long,
    val source: String,
)

@Singleton
class LocalMusicGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 目录结构：根 → 文件夹 → 曲目。
     *
     * Folia 的本地页签按目录树渲染（`LocalLibrarySnapshotNode`），所以这里把 MediaStore 的
     * `BUCKET_DISPLAY_NAME` 当成第一层目录，下载曲统一放在「广乐下载」下。
     */
    suspend fun browse(rootName: String): JsonNode = withContext(Dispatchers.IO) {
        val songs = allSongs()
        val grouped = songs.groupBy { it.folder.ifBlank { "未分类" } }
        val folders = grouped.map { (name, list) ->
            JsonNode(
                name = name,
                relativePath = "$rootName/$name",
                isFolder = true,
                children = list.map { songNode(it, rootName, name) },
            )
        }.sortedBy { it.name }
        JsonNode(
            name = rootName,
            relativePath = rootName,
            isFolder = true,
            children = folders,
        )
    }

    private fun songNode(song: LocalSongEntry, rootName: String, folder: String) = JsonNode(
        name = song.fileName,
        relativePath = "$rootName/$folder/${song.fileName}",
        isFolder = false,
        song = song,
    )

    /** 全量扫描。Android 13+ 需要 READ_MEDIA_AUDIO；没有权限时返回下载目录里的曲目。 */
    suspend fun allSongs(): List<LocalSongEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<LocalSongEntry>()
        runCatching { out += scanMediaStore() }
        out
    }

    private fun scanMediaStore(): List<LocalSongEntry> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        ) ?: return emptyList()

        val result = ArrayList<LocalSongEntry>()
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = Uri.withAppendedPath(collection, id.toString()).toString()
                val albumId = c.getLong(albumIdCol)
                result += LocalSongEntry(
                    id = "mediastore:$id",
                    title = c.getString(titleCol) ?: c.getString(nameCol) ?: "未知歌曲",
                    artist = c.getString(artistCol) ?: "",
                    album = c.getString(albumCol) ?: "",
                    durationMs = c.getLong(durCol),
                    artworkUrl = "https://appassets.androidplatform.net/local/artwork/$albumId",
                    contentUri = contentUri,
                    sizeBytes = c.getLong(sizeCol),
                    mimeType = c.getString(mimeCol) ?: "audio/mpeg",
                    fileName = c.getString(nameCol) ?: "$id",
                    folder = c.getString(bucketCol) ?: "",
                    addedAt = c.getLong(addedCol) * 1000L,
                    source = "mediastore",
                )
            }
        }
        return result
    }

    /** 读一张 MediaStore 封面，交给 WebViewAssetLoader 的图片接口直接返回字节。 */
    fun openAlbumArtwork(albumId: Long): ByteArray? = runCatching {
        val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** 用 MediaMetadataRetriever 兜底读时长（下载文件不一定被 MediaStore 收录）。 */
    fun probeDuration(uri: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun exists(path: String): Boolean = runCatching { File(path).isFile }.getOrDefault(false)
}

/** 极小的目录节点，避免把 Folia 的完整类型搬进 Kotlin。 */
data class JsonNode(
    val name: String,
    val relativePath: String,
    val isFolder: Boolean,
    val children: List<JsonNode> = emptyList(),
    val song: LocalSongEntry? = null,
)
