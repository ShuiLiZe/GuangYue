package com.guangyue.core.download

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * 11-19 Folia LyricsPage「本地歌曲歌词优先级」（useLyricSettingsStore localLyricsPriority：local / online，默认 local）：
 * 本地歌曲同时有同目录 / 内嵌歌词和在线匹配歌词时先用哪个。
 */
object LocalLyricPrefs {
    private const val FILE = "lyric_settings"
    private const val KEY = "local_lyrics_priority"
    private val state = MutableStateFlow("local")
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<String> {
        if (!loaded) {
            val v = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "local")
            state.value = if (v == "online") "online" else "local"
            loaded = true
        }
        return state
    }

    fun set(context: Context, value: String) {
        val v = if (value == "online") "online" else "local"
        state.value = v
        loaded = true
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, v).apply()
    }
}

/**
 * 本地歌曲的歌词（Folia：同目录歌词文件 + 内嵌歌词）：
 * 1. 同目录同名 `.lrc`（广乐下载也是这么存的）；
 * 2. 音频标签里的歌词（jaudiotagger FieldKey.LYRICS，下载时写过）。
 * 只认带时间戳的 LRC 文本；读不到（权限 / 格式）静默返回 null。
 */
object LocalLyrics {
    private val LRC_TIME = Regex("""\[\d{1,3}:\d{1,2}([.:]\d{1,3})?]""")

    /** 只做纯计算的部分（单测）：同名 .lrc 的路径。 */
    fun sidecarPath(audioPath: String): String {
        val slash = audioPath.lastIndexOf('/')
        val dot = audioPath.lastIndexOf('.')
        val base = if (dot > slash) audioPath.substring(0, dot) else audioPath
        return "$base.lrc"
    }

    /** 文本解码：UTF-8（去 BOM），出现替换符就当 GB18030 再解一次（老 .lrc 常见）。 */
    fun decode(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if ('\uFFFD' !in utf) return utf
        return runCatching { String(bytes, charset("GB18030")).removePrefix("\uFEFF") }.getOrDefault(utf)
    }

    fun isTimed(text: String?): Boolean = text != null && LRC_TIME.containsMatchIn(text)

    /** 播放地址 → 本地文件（file:// / 绝对路径 / content://media 查 DATA 列）。 */
    fun fileOf(context: Context, uriOrPath: String?): File? {
        if (uriOrPath.isNullOrBlank()) return null
        if (uriOrPath.startsWith("/")) return File(uriOrPath)
        val uri = runCatching { Uri.parse(uriOrPath) }.getOrNull() ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> runCatching {
                @Suppress("DEPRECATION")
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { File(it) } else null
                }
            }.getOrNull()
            else -> null
        }
    }

    fun read(context: Context, uriOrPath: String?): String? {
        val f = fileOf(context, uriOrPath) ?: return null
        val side = File(sidecarPath(f.path))
        runCatching { if (side.isFile && side.canRead()) decode(side.readBytes()).takeIf { isTimed(it) } }.getOrNull()?.let { return it }
        return runCatching {
            if (!f.isFile || !f.canRead()) return@runCatching null
            AudioTagger.toString() // 触发 AudioTagger.init（关 jaudiotagger 日志、Android 模式）
            AudioFileIO.read(f).tag?.getFirst(FieldKey.LYRICS)?.takeIf { isTimed(it) }
        }.getOrNull()
    }
}
