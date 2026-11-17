package com.guangyue.feature.library.viewmodel

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.guangyue.core.data.model.TrackEntity

/**
 * 11-17：Folia 本地页签「导入文件夹」的安卓版。系统媒体库（[DeviceAudio]）已经扫全机，这里补上
 * 媒体库看不到的地方（U 盘、SD 卡私有目录、.nomedia 文件夹）：用户用系统文件夹选择器选一个文件夹，
 * 持久授权后递归列出里面的音频，作为本地歌并入「全部歌曲」，文件夹名进「文件夹」分段。
 *
 * 结果在内存缓存，直到 [invalidate]（首页「刷新」）。键 `saf:<文档 uri>`。
 */
object ImportedFolders {
    private const val PREFS = "gy_imported_folders"
    private const val KEY = "trees"
    const val PREFIX = "saf:"
    private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "ape", "wma", "alac", "aiff", "aif", "dsf", "mka")

    @Volatile private var cache: DeviceAudio.Scan? = null

    fun trees(context: Context): List<Uri> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
            .map(Uri::parse)
            .filter { u -> context.contentResolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission } }

    /** 选择器回来后调用：持久授权并记下。返回文件夹显示名。 */
    fun add(context: Context, tree: Uri): String {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet(KEY, emptySet()).orEmpty().toMutableSet().apply { add(tree.toString()) }
        p.edit().putStringSet(KEY, set).apply()
        invalidate()
        return folderName(tree)
    }

    fun invalidate() { cache = null }

    fun folderName(tree: Uri): String =
        DocumentsContract.getTreeDocumentId(tree).substringAfter(':').trimEnd('/').ifBlank {
            DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
        }

    /**
     * 列出所有导入文件夹里的音频（放后台线程）。`skip` 是媒体库已有的（文件名小写 + 大小）——同一个文件
     * 媒体库也扫到了就不重复。
     */
    fun scan(context: Context, skip: Set<Pair<String, Long>>): DeviceAudio.Scan {
        cache?.let { return it }
        val out = ArrayList<TrackEntity>()
        val folders = HashMap<String, String>()
        val files = HashMap<String, String>()
        for (tree in trees(context)) {
            val root = folderName(tree)
            runCatching { walk(context, tree, DocumentsContract.getTreeDocumentId(tree), root, 0, skip, out, folders, files) }
        }
        return DeviceAudio.Scan(out, emptySet(), folders, files).also { cache = it }
    }

    private fun walk(
        context: Context, tree: Uri, docId: String, path: String, depth: Int, skip: Set<Pair<String, Long>>,
        out: MutableList<TrackEntity>, folders: MutableMap<String, String>, files: MutableMap<String, String>,
    ) {
        if (depth > 8) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val subdirs = ArrayList<Pair<String, String>>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { subdirs += id to name; continue }
                val ext = name.substringAfterLast('.', "").lowercase()
                if (!mime.startsWith("audio/") && ext !in AUDIO_EXT) continue
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if ((name.lowercase() to size) in skip) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val key = PREFIX + uri
                val stem = name.substringBeforeLast('.')
                var title = stem
                var artist = ""
                var album = ""
                var dur = 0L
                runCatching {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(context, uri)
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                        artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
                        album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                        dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally { r.release() }
                }
                if (artist.isBlank() && stem.contains(" - ")) {
                    artist = stem.substringBefore(" - ").trim()
                    if (title == stem) title = stem.substringAfter(" - ").trim()
                }
                folders[key] = path
                files[key] = "$path/$name"
                out += TrackEntity(
                    key = key,
                    title = title.ifBlank { "未知歌曲" },
                    artist = artist.ifBlank { "未知艺术家" },
                    album = album,
                    artwork = null,
                    durationMs = dur,
                    platform = "saf",
                    songId = id,
                    source = TrackEntity.SOURCE_LOCAL,
                    localUri = uri.toString(),
                    fileSize = size,
                    addedAt = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        for ((id, name) in subdirs) walk(context, tree, id, "$path/$name", depth + 1, skip, out, folders, files)
    }
}
