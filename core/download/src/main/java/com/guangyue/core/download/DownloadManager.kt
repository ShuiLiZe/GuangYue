package com.guangyue.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.player.Quality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载调度中心：把「一首歌」变成 WorkManager 任务，并对外暴露队列状态。
 *
 * 并发模型分两层：
 *
 * - **WorkManager** 只负责「持久化 + 约束 + 进程重启后继续」；
 * - **应用内线程池** [CHANNELS]（默认 3 条）才是真正的并行下载通道：超过 3 个的任务
 *   先停在 [DownloadStage.QUEUED]，由 [pump] 按先进先出放行。
 *
 * 这样既不会像 `enqueueAll` 那样一瞬间把几十个 HTTP 连接拍在源站上，也不会因为
 * WorkManager 的默认串行执行器把整张专辑下成单线程。
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DownloadStore,
    val library: DownloadedLibrary,
) {

    /** 进行中/排队任务的实时快照（界面上「下载管理器」直接订阅它）。 */
    val storeRef: DownloadStore get() = store

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** 已入队的请求（含完整 rawJson / headers），用于「重试」与「继续」。 */
    private val requests = ConcurrentHashMap<String, DownloadRequest>()

    /** 排队等待通道的 key（FIFO）。 */
    private val waiting = ArrayDeque<String>()

    /** 正在占用通道的 key。 */
    private val running = ConcurrentHashMap.newKeySet<String>()

    private val channels = Executors.newFixedThreadPool(CHANNELS) { r ->
        Thread(r, "GuangYueDownload-${ThreadCounter.incrementAndGet()}").apply { isDaemon = true }
    }

    /**
     * 0.0.19：未完成的下载请求落盘（filesDir/download_pending.json）。
     *
     * 以前 [requests] 只在内存里，App 被杀后下载页的失败任务一点「重试」就报「下载请求已失效」；
     * 现在冷启动时读回来：还没下完的任务以「上次没下完」失败态出现在下载页，点重试即可接着下
     * （WorkManager 若自己把任务续跑起来，Worker 会把状态改回进行中）。
     */
    private val pendingFile: File get() = File(context.filesDir, PENDING_FILE)

    private val pendingJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        restorePending()
    }

    private fun restorePending() {
        if (!pendingFile.exists()) return
        val list = runCatching {
            pendingJson.decodeFromString<List<DownloadRequest>>(pendingFile.readText())
        }.onFailure { android.util.Log.w("GYDownload", "restore pending failed", it) }.getOrNull() ?: return
        android.util.Log.i("GYDownload", "restore pending n=" + list.size)
        var changed = false
        for (request in list) {
            val done = library.find(request.key)
            if (done != null && library.exists(done)) {
                changed = true
                continue
            }
            requests[request.key] = request
            if (store.find(request.key) == null) {
                store.upsert(
                    DownloadProgress(
                        key = request.key,
                        title = request.title,
                        artist = request.artist,
                        stage = DownloadStage.FAILED,
                        error = "上次没下完（App 被关闭），点重试继续",
                        quality = request.quality,
                        artwork = request.artwork,
                    ),
                )
            }
        }
        if (changed) savePending()
    }

    /** 把还没下完的请求写回磁盘（每次增删都整份重写，几十条以内很小）。 */
    private fun savePending() {
        val snapshot = requests.values.filter { library.find(it.key) == null }
        runCatching {
            val tmp = File(context.filesDir, "$PENDING_FILE.tmp")
            tmp.writeText(pendingJson.encodeToString(snapshot))
            if (!tmp.renameTo(pendingFile)) {
                pendingFile.delete()
                tmp.renameTo(pendingFile)
            }
        }.onFailure { android.util.Log.w("GYDownload", "save pending failed", it) }
    }

    // ------------------------------------------------------------------ 入队

    /** 从库内曲目入队（播放器 / 列表菜单用）。 */
    fun enqueue(track: TrackEntity, quality: Quality = Quality.fromWire(track.quality)): Boolean {
        val request = DownloadRequest(
            key = track.key,
            title = track.title.ifBlank { "未命名" },
            artist = track.artist.ifBlank { "未知艺术家" },
            album = track.album,
            artwork = track.artwork,
            durationMs = track.durationMs,
            pluginId = track.platform,
            quality = quality.wire,
            directUrl = track.localUri,
            rawJson = track.rawJson,
        )
        return enqueue(request)
    }

    /** 从插件搜索结果入队（搜索页 / 播放页用）。 */
    fun enqueue(item: MusicItem, quality: Quality = Quality.HIGH): Boolean = enqueue(
        DownloadRequest(
            key = "${item.platform}:${item.id}",
            title = item.title.ifBlank { "未命名" },
            artist = item.displayArtist,
            album = item.album,
            artwork = item.artwork,
            durationMs = item.durationMs ?: 0L,
            pluginId = item.platform,
            quality = quality.wire,
            rawJson = item.raw?.toString(),
        ),
    )

    /** 批量入队（专辑 / 歌单 / 搜索结果「全部下载」）。返回实际新增的数量。 */
    fun enqueueAll(items: List<MusicItem>, quality: Quality = Quality.HIGH): Int =
        items.count { enqueue(it, quality) }

    fun enqueue(request: DownloadRequest): Boolean {
        if (requests.containsKey(request.key) && store.isActive(request.key)) return false
        library.find(request.key)?.let { done ->
            // 已经下过且文件还在，不重复下；文件被用户删了就清掉记录重新下。
            if (library.exists(done)) return false
            library.remove(done.key, deleteFile = false)
        }

        requests[request.key] = request
        savePending()
        store.upsert(
            DownloadProgress(
                key = request.key,
                title = request.title,
                artist = request.artist,
                stage = DownloadStage.QUEUED,
                quality = request.quality,
                artwork = request.artwork,
            ),
        )

        synchronized(waiting) { waiting.addLast(request.key) }
        notifyChanged()
        pump()
        return true
    }

    // ------------------------------------------------------------------ 控制

    fun cancel(key: String) {
        synchronized(waiting) { waiting.remove(key) }
        requests.remove(key)
        savePending()
        workManager.cancelUniqueWork(workName(key))
        store.patch(key) { it.copy(stage = DownloadStage.CANCELED) }
        release(key)
        pump()
    }

    fun cancelAll() {
        synchronized(waiting) { waiting.clear() }
        workManager.cancelAllWorkByTag(TAG_DOWNLOAD)
        store.snapshot().filter { it.isActive }.forEach { p ->
            store.patch(p.key) { it.copy(stage = DownloadStage.CANCELED) }
        }
        running.toList().forEach(::release)
        requests.clear()
        savePending()
    }

    /** 重试失败 / 取消的任务（重新解析直链，从 `.part` 继续）。 */
    fun retry(key: String) {
        val progress = store.find(key) ?: return
        if (progress.isActive) return
        store.patch(key) {
            it.copy(stage = DownloadStage.QUEUED, error = null, bytesPerSecond = 0L, fraction = 0f)
        }
        synchronized(waiting) {
            if (key !in waiting) waiting.addLast(key)
        }
        pump()
    }

    /**
     * 清空已完成 / 失败的任务记录。
     *
     * @param deleteFiles true 时把已下载到公共音乐库的文件也删掉
     */
    fun clearFinished(deleteFiles: Boolean = false) {
        val finished = store.snapshot().filter { !it.isActive }
        finished.forEach { progress ->
            store.find(progress.key)?.fileName?.let { name ->
                if (deleteFiles) library.remove(progress.key, deleteFile = true)
            }
            requests.remove(progress.key)
        }
        savePending()
        store.clearFinished()
    }

    // ------------------------------------------------------------------ 查询

    /** 进行中（含排队）+ 已完成，按入队顺序。 */
    fun snapshot(): List<DownloadProgress> = store.snapshot()

    fun isDownloading(key: String): Boolean = store.isActive(key)

    fun isDownloaded(key: String): Boolean = library.find(key) != null

    /** 汇总：进行中数量 / 已完成数量 / 合并速度。 */
    fun summary(): DownloadSummary {
        val all = store.snapshot()
        val active = all.filter { it.isActive }
        return DownloadSummary(
            activeCount = active.size,
            doneCount = all.count { it.isFinished },
            failedCount = all.count { it.isFailed },
            bytesPerSecond = active.sumOf { it.bytesPerSecond },
        )
    }

    /** 观察某个任务的 WorkInfo（调试 / 进度条兜底）。 */
    fun observeWork(key: String) =
        workManager.getWorkInfosForUniqueWorkFlow(workName(key))

    // ------------------------------------------------------------------ 调度

    /** 按通道数放行排队任务。 */
    private fun pump() {
        synchronized(waiting) {
            while (running.size < CHANNELS && waiting.isNotEmpty()) {
                val key = waiting.removeFirst()
                val progress = store.find(key)
                if (progress == null || progress.isFinished || progress.stage == DownloadStage.FAILED ||
                    progress.stage == DownloadStage.CANCELED
                ) {
                    continue
                }
                // 极老的记录没留请求：用 key（platform:id）+ 标题拼一个最小请求，Worker 会自己补全 MusicItem。
                val request = requests[key] ?: DownloadRequest(
                    key = key,
                    title = progress.title.ifBlank { "未命名" },
                    artist = progress.artist.ifBlank { "未知艺术家" },
                    artwork = progress.artwork,
                    pluginId = key.substringBefore(':'),
                    quality = progress.quality,
                ).also {
                    requests[key] = it
                    savePending()
                }
                running.add(key)
                channels.execute { runTask(request) }
            }
        }
    }

    private fun runTask(request: DownloadRequest) {
        try {
            enqueueWork(request)
        } catch (t: Throwable) {
            store.patch(request.key) { it.copy(stage = DownloadStage.FAILED, error = t.message ?: "入队失败") }
            release(request.key)
        }
    }

    private fun release(key: String) {
        running.remove(key)
        pump()
    }

    private fun enqueueWork(request: DownloadRequest) {
        val data = workDataOf(DownloadWorker.KEY_REQUEST to DownloadWorker.encode(request))
        val work = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            // 0.0.18：不再要求 NetworkType.CONNECTED。它在 API 28+ 要求网络「已验证」(VALIDATED)，
            // 国内连不上 Google 连通性检测的机器（模拟器、部分原生系统、开代理）网络永远是「受限」，
            // 任务就一直卡在「解析音源」。没网时 Worker 自己报错 + 退避重试即可。
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .addTag("download:${request.key}")
            .build()

        // 用一个能看到「结束」的协程监工：任务变 DONE / FAILED 就释放通道
        store.watch(request.key) { stage ->
            if (stage == DownloadStage.DONE || stage == DownloadStage.FAILED || stage == DownloadStage.CANCELED) {
                if (stage == DownloadStage.DONE) {
                    requests.remove(request.key)
                    savePending()
                }
                release(request.key)
                // 已完成记录由 DownloadWorker 直接写进 DownloadedLibrary（带本地封面 / 歌词）。
            }
        }
        // 用户点了下载就以这次为准：旧进程留下的同名任务（卡在退避 / 约束里）直接替换掉。
        workManager.enqueueUniqueWork(workName(request.key), ExistingWorkPolicy.REPLACE, work)
        store.patch(request.key) { it.copy(stage = DownloadStage.RESOLVING) }
    }

    private fun notifyChanged() = Unit

    private fun workName(key: String) = "guangyue-download-$key"

    companion object {
        const val TAG_DOWNLOAD = "guangyue-download"

        /** 并行下载通道数。 */
        const val CHANNELS = 3

        private const val PENDING_FILE = "download_pending.json"

        private val ThreadCounter = AtomicInteger()
    }
}

/** 下载总览。 */
data class DownloadSummary(
    val activeCount: Int = 0,
    val doneCount: Int = 0,
    val failedCount: Int = 0,
    val bytesPerSecond: Long = 0L,
) {
    fun speedText(): String = if (bytesPerSecond > 0L) DownloadProgress.formatBytes(bytesPerSecond) + "/s" else ""
}
