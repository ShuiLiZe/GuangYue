package com.guangyue.feature.library.viewmodel

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.guangyue.core.data.model.LibraryRules
import com.guangyue.core.data.model.TrackEntity

/**
 * 手机里的本地音乐（MediaStore）。对应 Folia 本地页签导入的文件夹：「全部歌曲」把它和下载合在一起。
 *
 * 读别的 App 的音频要 READ_MEDIA_AUDIO（13+）/ READ_EXTERNAL_STORAGE（12-）；没授权时只能看到
 * 广乐自己写进 Music/ 的文件（已经在下载列表里了），所以直接返回空。
 */
object DeviceAudio {

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** 扫描全部音乐（不含铃声 / 录音），按加入时间倒序。会查 ContentResolver，放后台线程。 */
    fun scan(context: Context): List<TrackEntity> {
        if (!granted(context)) return emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val out = ArrayList<TrackEntity>()
        runCatching {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val rowId = c.getLong(id)
                    val uri = ContentUris.withAppendedId(collection, rowId).toString()
                    val art = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumId)).toString()
                    val rawArtist = c.getString(artist).orEmpty()
                    out += TrackEntity(
                        key = LibraryRules.DEVICE_PREFIX + rowId,
                        title = c.getString(title)?.takeIf { it.isNotBlank() }
                            ?: c.getString(name)?.substringBeforeLast('.') ?: "未知歌曲",
                        artist = rawArtist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家",
                        album = c.getString(album).orEmpty(),
                        artwork = art,
                        durationMs = c.getLong(dur),
                        platform = "mediastore",
                        songId = rowId.toString(),
                        source = TrackEntity.SOURCE_LOCAL,
                        localUri = uri,
                        fileSize = c.getLong(size),
                        addedAt = c.getLong(added) * 1000L,
                    )
                }
            }
        }
        return out
    }

    private val ALBUM_ART = android.net.Uri.parse("content://media/external/audio/albumart")
}
