package com.guangyue.core.download

import android.content.Context
import android.content.SharedPreferences
import com.guangyue.core.player.Quality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载设置（「我的 · 下载设置」页读写，[DownloadManager] / [DownloadWorker] 读取生效）。
 *
 * 对照 Folia：
 * - 下载音质 = Folia「默认音质」（`omni.ts audioQualityFor`）：音源没有这一档时取不高于它的最近一档；
 * - 保存歌词 / 附带翻译 = Folia 歌词导出「增强型 LRC · LRC 附带 翻译」（翻译写成与原文同一时间戳的下一行）；
 * - 已下载数量、占用、清理暂存 = Folia 存储页「已缓存歌曲 / 缓存占用 / 清空缓存」。
 * 同时下载数是广乐下载页原本就有的并行通道（之前写死 3 条），这里只是让它可调。
 *
 * 和 [com.guangyue.core.prefs.AppPreferences] 一样用 SharedPreferences：Worker 里同步读、不必 await。
 */
data class DownloadConfig(
    val quality: Quality = DownloadPolicy.DEFAULT_QUALITY,
    val saveLyric: Boolean = true,
    val lyricTranslation: Boolean = true,
    val concurrency: Int = DownloadPolicy.DEFAULT_CONCURRENCY,
)

/** 最小键值接口：生产用 SharedPreferences，单测用内存 Map。 */
interface DownloadKeyValue {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
}

private class SharedPrefsKeyValue(private val p: SharedPreferences) : DownloadKeyValue {
    override fun getString(key: String): String? = p.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = p.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = p.getInt(key, default)
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = p.edit().putInt(key, value).apply()
}

@Singleton
class DownloadSettings internal constructor(private val kv: DownloadKeyValue) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(SharedPrefsKeyValue(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)))

    private val state = MutableStateFlow(read())

    /** 当前设置（界面订阅；写入后立即更新）。 */
    val config: StateFlow<DownloadConfig> = state.asStateFlow()

    /** 同步快照（Worker / 调度用）。 */
    fun current(): DownloadConfig = state.value

    fun setQuality(q: Quality) = update { it.copy(quality = q) }
    fun setSaveLyric(on: Boolean) = update { it.copy(saveLyric = on) }
    fun setLyricTranslation(on: Boolean) = update { it.copy(lyricTranslation = on) }
    fun setConcurrency(n: Int) = update { it.copy(concurrency = DownloadPolicy.clampConcurrency(n)) }

    private fun update(block: (DownloadConfig) -> DownloadConfig) {
        val next = block(state.value).let { it.copy(concurrency = DownloadPolicy.clampConcurrency(it.concurrency)) }
        kv.putString(KEY_QUALITY, next.quality.wire)
        kv.putBoolean(KEY_LYRIC, next.saveLyric)
        kv.putBoolean(KEY_TRANSLATION, next.lyricTranslation)
        kv.putInt(KEY_CONCURRENCY, next.concurrency)
        state.value = next
    }

    private fun read(): DownloadConfig = DownloadConfig(
        quality = kv.getString(KEY_QUALITY)?.let { w -> Quality.entries.firstOrNull { it.wire == w } }
            ?: DownloadPolicy.DEFAULT_QUALITY,
        saveLyric = kv.getBoolean(KEY_LYRIC, true),
        lyricTranslation = kv.getBoolean(KEY_TRANSLATION, true),
        concurrency = DownloadPolicy.clampConcurrency(kv.getInt(KEY_CONCURRENCY, DownloadPolicy.DEFAULT_CONCURRENCY)),
    )

    companion object {
        const val FILE = "guangyue_download"
        const val KEY_QUALITY = "download_quality"
        const val KEY_LYRIC = "download_save_lyric"
        const val KEY_TRANSLATION = "download_lyric_translation"
        const val KEY_CONCURRENCY = "download_concurrency"
    }
}

/** 纯逻辑（JVM 单测覆盖）。 */
object DownloadPolicy {
    const val MIN_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 5
    const val DEFAULT_CONCURRENCY = 3

    /** 默认下载音质：320k（和以前搜索页下载的写死值一致）。 */
    val DEFAULT_QUALITY: Quality = Quality.HIGH

    fun clampConcurrency(n: Int): Int = n.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)

    /**
     * 取链时依次尝试的音质：先 [preferred]，取不到再一档档往下，直到最低档。
     *
     * 同 Folia `audioQualityFor`：只往下降、不往上升；插件声明了档位（[declared]，wire 或别名）
     * 就只在它声明的档位里走，都高于 [preferred] 时退到它的最低档。
     */
    fun qualityLadder(preferred: Quality, declared: List<String> = emptyList()): List<Quality> {
        val listed = com.guangyue.core.player.QualityLadder.of(declared).toSet()
        val levels = if (listed.isEmpty()) Quality.ladder else Quality.ladder.filter { it in listed }
        val atOrBelow = levels.filter { it.order <= preferred.order }.sortedByDescending { it.order }
        return atOrBelow.ifEmpty { listOfNotNull(levels.minByOrNull { it.order }) }
    }

    /** 插件没声明音质时按 MusicFree 约定的四档（同 Folia：没列档位的源只分得清这几档）。 */
    val MUSICFREE_LEVELS: List<String> = listOf("low", "standard", "high", "super")

    /**
     * 实际问插件的音质字符串，按尝试顺序：用插件自己声明的写法（如 `320k` / `flac`），
     * 没声明就按 MusicFree 四档。插件不认识的档位常会悄悄回落到低音质，所以只问它认识的。
     */
    fun pluginWires(preferred: Quality, declared: List<String>): List<String> {
        val d = declared.filter { it.isNotBlank() }.ifEmpty { MUSICFREE_LEVELS }
        return qualityLadder(preferred, d).map { q ->
            d.firstOrNull { com.guangyue.core.player.QualityLadder.map(it) == q } ?: q.wire
        }.distinct()
    }

    private val LRC_TIME = Regex("""\[\d{1,3}:\d{1,2}([.:]\d{1,3})?]""")

    /**
     * 要写进 .lrc / 标签的歌词：原文；[includeTranslation] 时把翻译里带时间戳的行插在原文同一时间戳那行后面
     * （同一时间戳两行，播放器显示成双语，即 Folia「翻译写成与原文同一时间戳的下一行」）。
     */
    fun lrcContent(lrc: String, translation: String?, includeTranslation: Boolean): String {
        val trans = if (includeTranslation) translation?.lineSequence()?.filter { LRC_TIME.containsMatchIn(it) }?.toList().orEmpty() else emptyList()
        if (trans.isEmpty()) return lrc.trimEnd() + "\n"
        // 翻译按时间戳归组，紧跟在原文同一时间戳那行后面；对不上的放到最后。
        val byStamp = LinkedHashMap<String, MutableList<String>>()
        trans.forEach { line -> byStamp.getOrPut(stampKey(line)) { mutableListOf() }.add(line) }
        return buildString {
            lrc.trimEnd().lineSequence().forEach { line ->
                append(line).append('\n')
                if (LRC_TIME.containsMatchIn(line)) byStamp.remove(stampKey(line))?.forEach { append(it).append('\n') }
            }
            byStamp.values.flatten().forEach { append(it).append('\n') }
        }
    }

    /** 行首时间戳归一成毫秒，`[00:01.5]` 与 `[00:01.50]` 视为同一时间。 */
    private fun stampKey(line: String): String {
        val m = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""").find(line) ?: return line
        val (mm, ss, frac) = m.destructured
        val ms = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.toInt() }
        return ((mm.toLong() * 60 + ss.toLong()) * 1000 + ms).toString()
    }
}
