package com.guangyue.core.download

import kotlinx.serialization.Serializable

/**
 * 一次下载任务。
 *
 * 由 [DownloadManager] 从库内曲目构造，随 WorkManager 的 inputData 以 JSON 字符串传递。
 */
@Serializable
data class DownloadRequest(
    /** 库内唯一键 `platform:songId`。 */
    val key: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    /** 时长（毫秒），仅用于展示。 */
    val durationMs: Long = 0L,
    /** 插件 id。 */
    val pluginId: String = "",
    /** 音质 wire 值（`high` / `lossless` / …）。 */
    val quality: String = "high",
    /** 已经解析好的直链（为空则下载时再解析一次）。 */
    val directUrl: String? = null,
    /** getMediaSource 返回的请求头。 */
    val headers: Map<String, String> = emptyMap(),
    /** 原始 MusicFree musicItem JSON，用于重新解析直链与拉歌词。 */
    val rawJson: String? = null,
    /** 是否同时下载同名 .lrc。 */
    val withLyric: Boolean = true,
) {
    /** WorkManager 的唯一任务名。 */
    val workName: String get() = "guangyue-download-$key"
}

/** 下载阶段。 */
enum class DownloadStage {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    TAGGING,
    LYRICS,
    SCANNING,
    DONE,
    FAILED,
    CANCELED,
}

/** 已保存的音频文件描述（写标签、写库共用）。 */
data class SavedAudio(
    val uri: String,
    val path: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val fileName: String,
)
