package com.guangyue.core.data.model

/**
 * 手机本地音乐（MediaStore）的过滤规则（纯函数，JVM 单测覆盖）。
 *
 * 2026-11-10 用户真机：「全部歌曲」把通话录音也扫进来了。有的 ROM（MIUI / 华为 / 三星）把录音
 * 也标成 IS_MUSIC=1，只靠 IS_MUSIC 挡不住，所以再按以下几条筛：
 *  1. IS_MUSIC 必须为 1；
 *  2. API 31+ 的 IS_RECORDING=1、以及 IS_RINGTONE / IS_NOTIFICATION / IS_ALARM 一律不要；
 *  3. 路径里有录音 / 铃声目录（Recordings/、Call/、CallRecord、MIUI/sound_recorder、Sounds/、录音、通话录音…，大小写不敏感）；
 *  4. 时长已知且短于 [MIN_DURATION_MS]（30 秒）。时长读不到（0）的不按时长排除。
 */
object DeviceAudioRules {

    const val MIN_DURATION_MS = 30_000L

    /** MediaStore 一行里过滤要用到的字段。[path] 用 DATA 或 RELATIVE_PATH + DISPLAY_NAME，取得到哪个用哪个。 */
    data class Row(
        val path: String,
        val displayName: String = "",
        val durationMs: Long = 0L,
        val isMusic: Boolean = true,
        val isRecording: Boolean = false,
        val isRingtone: Boolean = false,
        val isNotification: Boolean = false,
        val isAlarm: Boolean = false,
    )

    /** 为什么被排除（null = 保留）。日志 / 单测用。 */
    fun rejectReason(r: Row): String? = when {
        !r.isMusic -> "not-music"
        r.isRecording -> "recording"
        r.isRingtone -> "ringtone"
        r.isNotification -> "notification"
        r.isAlarm -> "alarm"
        isExcludedPath(r.path) -> "path"
        isRecordingName(r.displayName.ifBlank { r.path.substringAfterLast('/') }) -> "name"
        r.durationMs in 1 until MIN_DURATION_MS -> "short"
        else -> null
    }

    fun accept(r: Row): Boolean = rejectReason(r) == null

    /** 目录名（去掉空格 / 下划线 / 连字符、转小写后）完全等于这些就排除。 */
    private val EXCLUDED_DIRS = setOf(
        "recordings", "recording", "recorder", "record",
        "call", "calls", "callrec", "callrecord", "callrecords", "callrecording", "callrecordings",
        "soundrecorder", "soundrecordings", "voicerecorder", "voicerecordings", "voicerecording",
        "sounds", "ringtones", "notifications", "alarms",
    )

    /** 目录名里含这些片段也排除（CallRecord、MIUI/sound_recorder/call_rec、Voice Recorder…）。 */
    private val EXCLUDED_FRAGMENTS = listOf("recorder", "recording", "callrec")

    private val CJK_FRAGMENTS = listOf("录音", "通话", "铃声")

    /** 路径里任意一级目录是录音 / 铃声 / 通知 / 闹钟目录。最后一段（文件名）不算目录。 */
    fun isExcludedPath(path: String): Boolean {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        if (parts.size <= 1) return false
        return parts.dropLast(1).any { seg -> isExcludedDir(seg) }
    }

    fun isExcludedDir(segment: String): Boolean {
        val n = segment.lowercase().filter { it != ' ' && it != '_' && it != '-' && it != '.' }
        if (n in EXCLUDED_DIRS) return true
        if (EXCLUDED_FRAGMENTS.any { n.contains(it) }) return true
        return CJK_FRAGMENTS.any { segment.contains(it) }
    }

    private val RECORDING_NAME = Regex(
        """^(录音|通话录音|record|recording|call|voice)[\s_\-@]*[\d(（+].*""",
        RegexOption.IGNORE_CASE,
    )

    /** 文件名像录音：「通话录音…」「录音 20241101…」「Call@138…」「Record_2024…」。 */
    fun isRecordingName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("通话录音")) return true
        return RECORDING_NAME.matches(name.trim())
    }
}
