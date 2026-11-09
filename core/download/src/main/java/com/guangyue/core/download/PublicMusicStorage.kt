package com.guangyue.core.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 写入公共存储 `Music/GuangYue/` 的统一入口。
 *
 * - **API 29+**：走 MediaStore.Audio（`RELATIVE_PATH`），写入即对音乐 App 可见，不需要任何权限，
 *   也无需 MANAGE_EXTERNAL_STORAGE。
 * - **API 26~28**：直接写 `ExternalStoragePublicDirectory(MUSIC)/GuangYue`，然后交给
 *   [MediaScannerConnection] 让系统索引。
 */
object PublicMusicStorage {

    /** 当前系统是否需要运行时写权限（只有 API 26~28）。 */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    val requiredPermission: String get() = Manifest.permission.WRITE_EXTERNAL_STORAGE

    /**
     * 在 `Music/GuangYue/` 下创建一个待写入的音频文件。
     *
     * @return 可写的 [Sink]；调用 [Sink.commit]（或 [Sink.copyFrom]）后会刷新媒体库
     */
    fun createAudio(
        context: Context,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
    ): Sink = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStoreSink(context, fileName, mimeType, title, artist, album, durationMs)
    } else {
        LegacySink(context, fileName)
    }

    /** 在 `Music/GuangYue/` 下写一个文本文件（歌词 .lrc）。 */
    fun writeText(context: Context, fileName: String, content: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DownloadPaths.relativeDir())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            // 0.0.15 真机：Android 11+ 不许把非音频文件放进 Music/（只许 Download、Documents），
            // insert 直接抛 IllegalArgumentException。放不进 Music/ 时退到 Download/GuangYue/。
            val uri = runCatching { resolver.insert(files, values) }.getOrNull()
                ?: runCatching {
                    values.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + DownloadPaths.relativeDir().substringAfter('/'),
                    )
                    resolver.insert(files, values)
                }.getOrNull()
                ?: return null
            runCatching {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }.getOrElse {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
            uri
        } else {
            runCatching {
                val file = File(DownloadPaths.legacyPublicDir(), fileName)
                file.writeText(content)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("text/plain"), null)
                Uri.fromFile(file)
            }.getOrNull()
        }

    /** 刷新媒体库（单个文件）。 */
    fun scan(context: Context, path: String?, mimeType: String?) {
        if (path.isNullOrBlank()) return
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
    }

    /** 一个待写入的目标文件。 */
    interface Sink {
        val uri: Uri

        /** 打开输出流；覆盖写。 */
        fun open(): OutputStream

        /** 写入完成，返回最终 uri（API 29+ 会把 IS_PENDING 清零）。 */
        fun commit(): Uri

        /** 失败回滚。 */
        fun abort()
    }

    private class MediaStoreSink(
        private val context: Context,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
    ) : Sink {
        override val uri: Uri

        init {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DownloadPaths.relativeDir())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                if (durationMs > 0L) put(MediaStore.Audio.Media.DURATION, durationMs)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
            }
            uri = context.contentResolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: throw IllegalStateException("无法在 Music/${DownloadPaths.SUB_DIR} 创建文件")
        }

        override fun open(): OutputStream =
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法写入 $uri")

        override fun commit(): Uri {
            // IS_PENDING=0 相当于「发布」，MediaStore 会立刻建立索引
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        }

        override fun abort() {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private class LegacySink(
        private val context: Context,
        fileName: String,
    ) : Sink {
        private val file = File(DownloadPaths.legacyPublicDir(), fileName)
        override val uri: Uri = Uri.fromFile(file)

        override fun open(): OutputStream = file.outputStream()

        override fun commit(): Uri {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return uri
        }

        override fun abort() {
            runCatching { file.delete() }
        }
    }
}

/** 从 [InputStream] 复制到 [Sink]（暂存目录 → 公共存储的最后一步）。 */
@Throws(java.io.IOException::class)
fun PublicMusicStorage.Sink.copyFrom(source: InputStream, onBytes: (Long) -> Unit = {}): Uri {
    try {
        source.use { input ->
            open().use { output ->
                val buffer = ByteArray(256 * 1024)
                var acc = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    acc += n
                    onBytes(acc)
                }
                output.flush()
            }
        }
        return commit()
    } catch (t: Throwable) {
        abort()
        throw t
    }
}

/** 公共存储根目录是否可写（API 29+ 恒为 true）。 */
fun isExternalStorageWritable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
