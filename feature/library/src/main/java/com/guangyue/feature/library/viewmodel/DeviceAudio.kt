package com.guangyue.feature.library.viewmodel

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.guangyue.core.data.model.DeviceAudioRules
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

    /** 一次扫描的结果：保留的歌 + 被过滤规则排除的行 key（`mediastore:<id>`，用来清库）。 */
    data class Scan(val tracks: List<TrackEntity>, val rejectedKeys: Set<String>)

    /** 扫描全部音乐（不含铃声 / 录音 / 30 秒以下），按加入时间倒序。会查 ContentResolver，放后台线程。 */
    fun scan(context: Context): List<TrackEntity> = scanDetailed(context).tracks

    /**
     * 2026-11-10：有的 ROM 把通话录音也标成 IS_MUSIC=1，所以 SQL 只粗筛 IS_MUSIC，
     * 再把每行交给 [DeviceAudioRules] 按录音标记 / 铃声通知闹钟 / 路径 / 时长细筛。
     */
    fun scanDetailed(context: Context): Scan {
        if (!granted(context)) return Scan(emptyList(), emptySet())
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = ArrayList(
            listOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.DATA,
            ),
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 31) cols += MediaStore.Audio.Media.IS_RECORDING
        val out = ArrayList<TrackEntity>()
        val rejected = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                collection, cols.toTypedArray(),
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
                val isMusic = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                val isRingtone = c.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
                val isNotification = c.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
                val isAlarm = c.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
                @Suppress("DEPRECATION")
                val data = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPath = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
                val isRecording = if (Build.VERSION.SDK_INT >= 31) c.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING) else -1
                fun flag(i: Int) = i >= 0 && !c.isNull(i) && c.getInt(i) != 0
                fun str(i: Int) = if (i >= 0 && !c.isNull(i)) c.getString(i).orEmpty() else ""
                while (c.moveToNext()) {
                    val rowId = c.getLong(id)
                    val display = str(name)
                    val path = str(data).ifBlank { str(relPath).let { r -> if (r.isBlank()) "" else r.trimEnd('/') + "/" + display } }
                    val row = DeviceAudioRules.Row(
                        path = path,
                        displayName = display,
                        durationMs = c.getLong(dur),
                        isMusic = isMusic < 0 || flag(isMusic),
                        isRecording = flag(isRecording),
                        isRingtone = flag(isRingtone),
                        isNotification = flag(isNotification),
                        isAlarm = flag(isAlarm),
                    )
                    if (!DeviceAudioRules.accept(row)) {
                        rejected += LibraryRules.DEVICE_PREFIX + rowId
                        continue
                    }
                    val uri = ContentUris.withAppendedId(collection, rowId).toString()
                    val art = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumId)).toString()
                    val rawArtist = c.getString(artist).orEmpty()
                    out += TrackEntity(
                        key = LibraryRules.DEVICE_PREFIX + rowId,
                        title = c.getString(title)?.takeIf { it.isNotBlank() }
                            ?: display.substringBeforeLast('.').ifBlank { "未知歌曲" },
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
        return Scan(out, rejected)
    }

    private val ALBUM_ART = android.net.Uri.parse("content://media/external/audio/albumart")
}
