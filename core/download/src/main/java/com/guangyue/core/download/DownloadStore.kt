package com.guangyue.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** 单个任务的可观察状态。 */
@Serializable
data class DownloadProgress(
    val key: String,
    val title: String = "",
    val artist: String = "",
    val stage: DownloadStage = DownloadStage.QUEUED,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val bytesPerSecond: Long = 0L,
    /** 0f..1f，总长度未知时为 -1f（不确定进度条）。 */
    val fraction: Float = 0f,
    val error: String? = null,
    /** 完成后的公共存储 uri。 */
    val uri: String? = null,
    val fileName: String? = null,
    val quality: String = "high",
    val artwork: String? = null,
    val updatedAt: Long = 0L,
) {
    val isActive: Boolean
        get() = stage in ACTIVE_STAGES

    val isFinished: Boolean get() = stage == DownloadStage.DONE

    val isFailed: Boolean get() = stage == DownloadStage.FAILED

    /** 形如 `12.3 MB / 45.6 MB · 1.2 MB/s`。 */
    fun progressText(): String = buildString {
        append(formatBytes(bytesDone))
        if (bytesTotal > 0L) append(" / ").append(formatBytes(bytesTotal))
        if (bytesPerSecond > 0L) append(" · ").append(formatBytes(bytesPerSecond)).append("/s")
    }

    fun stageText(): String = when (stage) {
        DownloadStage.QUEUED -> "排队中"
        DownloadStage.RESOLVING -> "解析音源"
        DownloadStage.DOWNLOADING -> progressText()
        DownloadStage.TAGGING -> "写入标签"
        DownloadStage.LYRICS -> "下载歌词"
        DownloadStage.SCANNING -> "刷新媒体库"
        DownloadStage.DONE -> "已完成 · " + formatBytes(bytesTotal)
        DownloadStage.FAILED -> "失败：" + (error ?: "未知错误")
        DownloadStage.CANCELED -> "已取消"
    }

    companion object {
        val ACTIVE_STAGES: Set<DownloadStage> = setOf(
            DownloadStage.QUEUED,
            DownloadStage.RESOLVING,
            DownloadStage.DOWNLOADING,
            DownloadStage.TAGGING,
            DownloadStage.LYRICS,
            DownloadStage.SCANNING,
        )

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var value = bytes.toDouble()
            var i = 0
            while (value >= 1024 && i < units.lastIndex) {
                value /= 1024
                i++
            }
            return if (i == 0) "${bytes} ${units[i]}" else String.format("%.1f %s", value, units[i])
        }
    }
}

/**
 * 下载模块的内存状态仓库 —— 所有下载相关界面都只订阅这一个 [StateFlow]。
 *
 * 进程被杀后状态由 [DownloadWorker] 重启时重新写回（任务本身持久化在 WorkManager）。
 */
@Singleton
class DownloadStore @Inject constructor() {

    private val mutable = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadProgress>> = mutable.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 任务顺序（新任务在前）。 */
    private val order = ArrayDeque<String>()

    fun upsert(progress: DownloadProgress) = synchronized(order) {
        if (progress.key !in order) order.addFirst(progress.key)
        mutable.update { it + (progress.key to progress.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun patch(key: String, block: (DownloadProgress) -> DownloadProgress) = synchronized(order) {
        val current = mutable.value[key] ?: return@synchronized
        val next = block(current).copy(updatedAt = System.currentTimeMillis())
        mutable.update { it + (key to next) }
    }

    fun remove(key: String) = synchronized(order) {
        order.remove(key)
        mutable.update { it - key }
    }

    fun clearFinished() = synchronized(order) {
        val finished = mutable.value.filterValues { !it.isActive }.keys
        order.removeAll(finished)
        mutable.update { map -> map - finished }
    }

    fun snapshot(): List<DownloadProgress> = synchronized(order) {
        order.mapNotNull { mutable.value[it] }
    }

    /** 进行中的任务（含排队）。 */
    fun active(): List<DownloadProgress> = snapshot().filter { it.isActive }

    /** 已完成（成功或失败）的任务。 */
    fun finished(): List<DownloadProgress> = snapshot().filter { !it.isActive }

    fun find(key: String): DownloadProgress? = mutable.value[key]

    /** 某个 key 是否已经在下，避免重复入队。 */
    fun isActive(key: String): Boolean = mutable.value[key]?.isActive == true

    /**
     * 监听某个任务的阶段变化，直到它进入终态（DONE / FAILED / CANCELED）或超时槽位释放。
     *
     * [DownloadManager] 用它把「WorkManager 任务结束」翻译成「释放下载通道」。
     */
    internal fun watch(key: String, onStage: (DownloadStage) -> Unit): Job = scope.launch {
        mutable
            .map { it[key]?.stage }
            .distinctUntilChanged()
            .drop(1)
            .filter { it == DownloadStage.DONE || it == DownloadStage.FAILED || it == DownloadStage.CANCELED }
            .collect { onStage(it!!) }
    }
}
