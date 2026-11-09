package com.guangyue.core.download

import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

/**
 * 用 jaudiotagger 把标题 / 艺术家 / 专辑 / 封面写进音频文件。
 *
 * 覆盖 mp3(ID3v2) / flac(VorbisComment+PICTURE) / m4a / ogg；不支持的容器或标签写入失败时
 * **只记日志**，绝不让下载任务失败 —— 音频本体才是主产物。
 */
object AudioTagger {

    private const val TAG = "GuangYueTag"

    init {
        // jaudiotagger 默认往 java.util.logging 刷大量 WARNING，关掉；同时允许 ID3v1 保持关闭（只写 ID3v2）
        runCatching {
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
            // 0.0.18 真机/模拟器实测：不开 Android 模式时 FLAC 写封面要走 javax.imageio（Android 上没有），
            // 封面被静默丢掉（wrote=144951 read=0）。开了之后用 AndroidArtwork，不读图片尺寸。
            TagOptionSingleton.getInstance().isAndroid = true
        }
    }

    /**
     * @param file 已下载完成的音频文件（暂存区里的 `.part` 已改名为正式扩展名）
     * @param artworkBytes 封面原始字节；为 null 时按 [artworkUrl] 现拉
     */
    fun tag(
        file: File,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        artworkBytes: ByteArray? = null,
        trackNumber: Int? = null,
        year: String? = null,
        lyrics: String? = null,
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag: Tag = audioFile.tagOrCreateAndSetDefault

            writeField(tag, FieldKey.TITLE, title)
            writeField(tag, FieldKey.ARTIST, artist)
            writeField(tag, FieldKey.ALBUM_ARTIST, artist)
            writeField(tag, FieldKey.ALBUM, album)
            if (trackNumber != null) writeField(tag, FieldKey.TRACK, trackNumber.toString())
            writeField(tag, FieldKey.YEAR, year)
            writeField(tag, FieldKey.LYRICS, lyrics)

            val cover = artworkBytes ?: artworkUrl?.let(::fetchArtwork)
            if (cover != null && cover.isNotEmpty()) {
                runCatching {
                    if (tag is org.jaudiotagger.tag.flac.FlacTag) {
                        // FLAC：Artwork 路径要把图片解码成 BufferedImage（Android 没有，必失败），
                        // 直接拼 METADATA_BLOCK_PICTURE，宽高留 0（规范允许，播放器照常显示）。
                        tag.deleteArtworkField()
                        tag.setField(tag.createArtworkField(cover, 3, sniffImageMime(cover), "Cover", 0, 0, 24, 0))
                        return@runCatching
                    }
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = cover
                    artwork.mimeType = sniffImageMime(cover)
                    artwork.pictureType = 3
                    artwork.description = "Cover"
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                }.onFailure { Log.w(TAG, "artwork rejected: ${it.message}") }
            }

            audioFile.commit()
            // 回读确认封面真的写进去了（jaudiotagger 对个别容器会静默丢 PICTURE）。
            if (cover != null && cover.isNotEmpty()) {
                val back = runCatching { AudioFileIO.read(file).tag?.firstArtwork?.binaryData?.size ?: 0 }.getOrDefault(0)
                Log.i(TAG, "cover verify ${file.name}: wrote=${cover.size} read=$back")
            }
            true
        }.onFailure {
            Log.w(TAG, "tagging failed for ${file.name}: ${it.message}")
        }.getOrDefault(false)
    }

    /** 只读元数据（用于校验 / 补全）。 */
    fun read(file: File): Map<String, String> = runCatching {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: return emptyMap()
        mapOf(
            "title" to runCatching { tag.getFirst(FieldKey.TITLE) }.getOrDefault(""),
            "artist" to runCatching { tag.getFirst(FieldKey.ARTIST) }.getOrDefault(""),
            "album" to runCatching { tag.getFirst(FieldKey.ALBUM) }.getOrDefault(""),
            "duration" to (audioFile.audioHeader?.trackLength?.toString() ?: ""),
        )
    }.getOrDefault(emptyMap())

    // ------------------------------------------------------------------ internals

    /** jaudiotagger 的 Tag 是 `Tag<FieldKey>`，用 Kotlin 侧写会撞泛型，这里统一走这个函数。 */
    private fun writeField(tag: Tag, key: FieldKey, value: String?) {
        if (value.isNullOrBlank()) return
        runCatching {
            val current = runCatching { tag.getFirst(key) }.getOrNull()
            if (current.isNullOrBlank()) tag.addField(key, value) else tag.setField(key, value)
        }.onFailure { Log.w(TAG, "field ${key.name} rejected: ${it.message}") }
    }

    private fun sniffImageMime(data: ByteArray): String {
        if (data.size >= 4 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte()) return "image/png"
        if (data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) return "image/jpeg"
        if (data.size >= 12 && data[8] == 'W'.code.toByte() && data[9] == 'E'.code.toByte()) return "image/webp"
        return "image/jpeg"
    }

    /** 拉封面（最多 8MB，超了放弃）。 */
    fun fetchArtwork(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "GuangYue/1.0 (Android)")
        conn.getInputStream().use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > 8 * 1024 * 1024) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }.onFailure { Log.w(TAG, "artwork fetch failed: ${it.message}") }.getOrNull()
}
