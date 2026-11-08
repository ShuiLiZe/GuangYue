package com.guangyue.core.download

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.guangyue.core.plugin.Json2
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 单个音乐的真实下载任务。
 *
 * 生命周期：
 * ```
 * QUEUED → RESOLVING → DOWNLOADING(断点续传) → TAGGING(ID3+封面) → LYRICS(.lrc)
 *        → SCANNING(MediaStore / MediaScanner) → DONE
 * ```
 *
 * 断点续传靠暂存目录里的 `.part`：进程被杀后重启会从已有字节继续。可重试错误返回
 * [Result.retry]，永久错误（404/403/链接过期）返回 [Result.failure]。
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val store: DownloadStore,
    private val bridge: MusicPluginBridge,
    @com.guangyue.core.plugin.di.DownloadHttp private val okHttp: OkHttpClient,
    private val lx: com.guangyue.core.plugin.lx.LxSourceManager,
    private val prefs: com.guangyue.core.prefs.AppPreferences,
) : CoroutineWorker(context, params) {

    private val meter = BandwidthMeter()

    /** 进度回调里不能直接调 setProgress（suspend），先落到这里，循环里再上报。 */
    @Volatile
    private var progressBridge: Pair<Long, Long>? = null

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
        val request = inputData.getString(KEY_REQUEST)?.let(::decode) ?: return@withContext Result.failure()

        val staging = DownloadPaths.stagingDir(context)
        var createdSink: PublicMusicStorage.Sink? = null

        try {
            store.upsert(
                DownloadProgress(
                    key = request.key,
                    title = request.title,
                    artist = request.artist,
                    stage = DownloadStage.RESOLVING,
                    quality = request.quality,
                    artwork = request.artwork,
                ),
            )

            // ---------------------------------------------------------------- 1. 解析直链
            val source = resolveSource(request)
            val extension = DownloadPaths.extensionFrom(source.url, source.mime).ifBlank { "mp3" }
            val mime = mimeFor(extension)
            val targetName = uniqueName(staging, DownloadPaths.fileName(request.artist, request.title, extension))
            val part = File(staging, targetName + DownloadPaths.PART_SUFFIX)

            // ---------------------------------------------------------------- 2. 断点续传
            store.patch(request.key) {
                it.copy(stage = DownloadStage.DOWNLOADING, bytesDone = part.length(), bytesTotal = 0L)
            }
            val ok = download(source, part) { done, total ->
                store.patch(request.key) {
                    it.copy(
                        stage = DownloadStage.DOWNLOADING,
                        bytesDone = done,
                        bytesTotal = total,
                        bytesPerSecond = meter.bytesPerSecond,
                        fraction = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else -1f,
                    )
                }
                progressBridge = done to total
            }
            if (!ok) return@withContext Result.retry()
            progressBridge?.let { (done, total) -> setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }

            // 落盘为正式文件（暂存区），写标签
            val audioFile = File(staging, targetName)
            if (audioFile.exists()) audioFile.delete()
            if (!part.renameTo(audioFile)) {
                part.copyTo(audioFile, overwrite = true)
                part.delete()
            }

            // ---------------------------------------------------------------- 3. 写 ID3 + 封面
            store.patch(request.key) { it.copy(stage = DownloadStage.TAGGING) }
            val cover = request.artwork?.takeIf { it.startsWith("http") }?.let(AudioTagger::fetchArtwork)
            AudioTagger.tag(
                file = audioFile,
                title = request.title,
                artist = request.artist,
                album = request.album,
                artworkUrl = request.artwork,
                artworkBytes = cover,
            )

            // ---------------------------------------------------------------- 4. 复制进公共音乐库
            val size = audioFile.length()
            val sink = PublicMusicStorage.createAudio(
                context = context,
                fileName = targetName,
                mimeType = mime,
                title = request.title,
                artist = request.artist,
                album = request.album,
                durationMs = request.durationMs,
            )
            createdSink = sink
            val audioUri = audioFile.inputStream().use { input ->
                sink.copyFrom(input) { copied ->
                    store.patch(request.key) {
                        it.copy(bytesDone = copied, bytesTotal = size, fraction = 1f)
                    }
                }
            }
            audioFile.delete()

            // ---------------------------------------------------------------- 5. 歌词 .lrc
            if (request.withLyric) {
                store.patch(request.key) { it.copy(stage = DownloadStage.LYRICS) }
                writeLyric(request, targetName)
            }

            // ---------------------------------------------------------------- 6. 刷新媒体库
            store.patch(request.key) { it.copy(stage = DownloadStage.SCANNING) }
            PublicMusicStorage.scan(context, localPathOf(audioUri), mime)

            store.upsert(
                DownloadProgress(
                    key = request.key,
                    title = request.title,
                    artist = request.artist,
                    stage = DownloadStage.DONE,
                    bytesDone = size,
                    bytesTotal = size,
                    fraction = 1f,
                    uri = audioUri.toString(),
                    fileName = targetName,
                    quality = request.quality,
                    artwork = request.artwork,
                ),
            )
            Result.success(workDataOf(KEY_DONE to size, KEY_TOTAL to size))
        } catch (t: Throwable) {
            if (t !is kotlinx.coroutines.CancellationException) {
                ensureActive()
                store.patch(request.key) { it.copy(stage = DownloadStage.FAILED, error = t.message ?: "下载失败") }
                runCatching { createdSink?.abort() }
            }
            if ((t is HttpRange.TransientException || isNetworkError(t)) && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
        }
    }

    // ------------------------------------------------------------------ steps

    private class Source(val url: String, val mime: String?, val headers: Map<String, String>)

    private suspend fun resolveSource(request: DownloadRequest): Source {
        // 插件直链有有效期，重试时必须重新解析
        val raw: JsonObject? = request.rawJson?.let { runCatching { Json2.parse(it) as? JsonObject }.getOrNull() }
        val item = raw?.let { MusicItem.from(it, request.pluginId) } ?: fallbackItem(request)
        if (raw != null || item != null) {
            val fresh = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(PLUGIN_BUDGET_MS) {
                    bridge.getMediaSource(
                        pluginId = request.pluginId,
                        musicItemRaw = raw,
                        item = item,
                        quality = request.quality,
                    )
                }
            }.onFailure { android.util.Log.w(LOG_TAG, "plugin getMediaSource threw for ${request.key}: ${it.message}") }
                .getOrNull()
            if (fresh != null && fresh.url.isNotBlank()) return Source(fresh.url, fresh.mimeType, fresh.headers)
            android.util.Log.w(LOG_TAG, "plugin gave no url for ${request.key}, trying lx sources")
        }

        // 2026-11-08 修「不能下载」：播放链路在插件取不到链时会问洛雪取链源，下载以前不会，
        // 于是能播的歌下载却报「无法解析下载地址」。这里补上同一个兜底。
        if (item != null) {
            val lxUrl = runCatching {
                lx.resolveMediaUrl(item, request.quality, prefs.lxDefaultQuality)
            }.onFailure { android.util.Log.w(LOG_TAG, "lx fallback threw for ${request.key}: ${it.message}") }
                .getOrNull()
            if (!lxUrl.isNullOrBlank()) return Source(lxUrl, null, emptyMap())
        }

        val fallback = request.directUrl
        if (!fallback.isNullOrBlank()) return Source(fallback, null, request.headers)
        throw HttpRange.PermanentException("这首歌当前音源和取链源都取不到下载地址，换个音源再试")
    }

    /** 没有 rawJson 时（播放队列里直连开播的曲目）用 `platform:id` 拼一个最小 MusicItem。 */
    private fun fallbackItem(request: DownloadRequest): MusicItem? {
        val parts = request.key.split(":", limit = 2)
        if (parts.size != 2 || parts[1].isBlank()) return null
        return MusicItem(
            platform = parts[0].ifBlank { request.pluginId },
            id = parts[1],
            title = request.title,
            artist = request.artist,
            album = request.album,
            artwork = request.artwork,
            durationMs = request.durationMs.takeIf { it > 0L },
        )
    }

    /** @return true 表示至少拿到了完整数据 */
    private fun download(
        source: Source,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        var offset = if (part.isFile) part.length() else 0L

        val req = Request.Builder()
            .url(source.url)
            .header("User-Agent", USER_AGENT)
            .apply { source.headers.forEach { (k, v) -> header(k, v) } }
            .apply { if (offset > 0L) header("Range", "bytes=$offset-") }
            .build()

        meter.reset()
        okHttp.newCall(req).execute().use { response ->
            val code = response.code
            if (code == 416) return part.length() > 0L // 已经下完
            if (code == 401 || code == 403 || code == 404 || code == 410) {
                throw HttpRange.PermanentException("源站拒绝（HTTP $code），可能链接已过期")
            }
            if (code >= 500 || code == 408 || code == 429) {
                throw HttpRange.TransientException("源站繁忙（HTTP $code）")
            }
            if (!response.isSuccessful) throw HttpRange.PermanentException("下载失败（HTTP $code）")

            if (code == 200 && offset > 0L) {
                // 服务端忽略了 Range：必须从头来
                part.delete()
                offset = 0L
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val total = if (contentLength > 0L) contentLength + offset else 0L
            val body = response.body?.byteStream() ?: throw HttpRange.TransientException("空响应")

            HttpRange.writeTo(
                target = part,
                body = body,
                resumeOffset = offset,
                totalHint = total,
                progress = onProgress,
                meter = meter,
            )
        }
        return part.isFile && part.length() > 0L
    }

    private suspend fun writeLyric(request: DownloadRequest, audioFileName: String) {
        val raw = request.rawJson?.let { Json2.parse(it) as? JsonObject } ?: return
        val item = MusicItem.from(raw, request.pluginId) ?: return
        val lyric = runCatching { bridge.getLyric(request.pluginId, item) }.getOrNull() ?: return
        val lrc = lyric.rawLrc?.takeIf { it.isNotBlank() } ?: return
        val content = buildString {
            append(lrc.trimEnd())
            lyric.translation?.takeIf { it.isNotBlank() }?.let { tr ->
                append("\n")
                tr.lineSequence().filter { it.isNotBlank() }.forEach { append("[译] ").append(it).append('\n') }
            }
        }
        PublicMusicStorage.writeText(context, DownloadPaths.lrcName(audioFileName), content)
    }

    // ------------------------------------------------------------------ helpers

    private fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mp3")
        var i = 1
        while (File(dir, "$base ($i).$ext").exists()) i++
        return "$base ($i).$ext"
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }

    @Suppress("DEPRECATION")
    private fun localPathOf(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return uri.path
        val column = android.provider.MediaStore.MediaColumns.DATA
        context.contentResolver.query(uri, arrayOf(column), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun isNetworkError(t: Throwable): Boolean = t is java.io.IOException

    companion object {
        const val KEY_REQUEST = "request"
        const val KEY_DONE = "bytes_done"
        const val KEY_TOTAL = "bytes_total"
        const val MAX_ATTEMPTS = 3
        const val USER_AGENT = "GuangYue/1.0 (Android)"
        private const val LOG_TAG = "GYDOWNLOAD"
        /** 插件取链预算，超时就落到洛雪取链源。 */
        private const val PLUGIN_BUDGET_MS = 25_000L

        fun decode(json: String): DownloadRequest? =
            runCatching { kotlinx.serialization.json.Json.decodeFromString<DownloadRequest>(json) }.getOrNull()

        fun encode(request: DownloadRequest): String =
            kotlinx.serialization.json.Json.encodeToString<DownloadRequest>(request)
    }
}
