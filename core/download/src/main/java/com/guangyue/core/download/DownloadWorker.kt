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
    private val library: DownloadedLibrary,
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

            // ---------------------------------------------------------------- 3. 补全信息 + 封面 + 歌词
            store.patch(request.key) { it.copy(stage = DownloadStage.TAGGING) }
            val item = requestItem(request)
            // 搜索结果常常没有封面 / 专辑（要 getMusicInfo 才给），下载时补一次。
            var album = request.album?.takeIf { it.isNotBlank() }
            var artworkUrl = request.artwork?.takeIf { it.isNotBlank() }
            if ((album == null || artworkUrl == null) && item != null) {
                val info = runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(INFO_BUDGET_MS) { bridge.getMusicInfo(request.pluginId.ifBlank { item.platform }, item) }
                }.getOrNull()
                if (album == null) album = info?.album?.takeIf { it.isNotBlank() }
                if (artworkUrl == null) artworkUrl = info?.artwork?.takeIf { it.isNotBlank() }
            }
            val cover = artworkUrl?.let(::fetchCover)
            val coverUri = cover?.let { bytes ->
                runCatching {
                    val f = library.coverFile(request.key)
                    f.writeBytes(bytes)
                    android.net.Uri.fromFile(f).toString()
                }.getOrNull()
            }
            val lyric = if (request.withLyric && item != null) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(LYRIC_BUDGET_MS) { bridge.getLyric(request.pluginId.ifBlank { item.platform }, item) }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            } else null
            val lrcText = lyric?.rawLrc?.takeIf { it.isNotBlank() }
                ?: request.rawJson?.let(::inlineLrc)
            val transText = lyric?.translation?.takeIf { it.isNotBlank() }
            val lyricPath = lrcText?.let { t -> runCatching { library.lyricFile(request.key).also { it.writeText(t) }.absolutePath }.getOrNull() }
            val transPath = transText?.let { t -> runCatching { library.lyricFile(request.key, true).also { it.writeText(t) }.absolutePath }.getOrNull() }

            val tagged = AudioTagger.tag(
                file = audioFile,
                title = request.title,
                artist = request.artist,
                album = album,
                artworkUrl = null,
                artworkBytes = cover,
                lyrics = lrcText?.let { mergedLrc(it, transText) },
            )
            android.util.Log.i(LOG_TAG, "tagged=$tagged cover=${cover?.size ?: 0}B lyric=${lrcText?.length ?: 0} album=$album for ${request.key}")

            // ---------------------------------------------------------------- 4. 复制进公共音乐库
            val size = audioFile.length()
            val sink = PublicMusicStorage.createAudio(
                context = context,
                fileName = targetName,
                mimeType = mime,
                title = request.title,
                artist = request.artist,
                album = album,
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

            // ---------------------------------------------------------------- 5. 公共 .lrc（给系统音乐 App 用）
            var lrcUri: String? = null
            if (lrcText != null) {
                store.patch(request.key) { it.copy(stage = DownloadStage.LYRICS) }
                // 歌词只是附带品：写不进去也不能让整首下载算失败（0.0.15 真机就是这样卡住的）。
                lrcUri = runCatching {
                    PublicMusicStorage.writeText(context, DownloadPaths.lrcName(targetName), mergedLrc(lrcText, transText))?.toString()
                }.onFailure { android.util.Log.w(LOG_TAG, "lyric skipped for ${request.key}: ${it.message}") }.getOrNull()
            }

            // ---------------------------------------------------------------- 6. 刷新媒体库
            store.patch(request.key) { it.copy(stage = DownloadStage.SCANNING) }
            PublicMusicStorage.scan(context, localPathOf(audioUri), mime)

            // 下载记录由 Worker 自己写（以前靠 DownloadManager 的内存监听，进程被杀就丢记录）。
            library.record(
                DownloadedTrack(
                    key = request.key,
                    title = request.title,
                    artist = request.artist,
                    album = album,
                    artwork = coverUri ?: artworkUrl,
                    uri = audioUri.toString(),
                    fileName = targetName,
                    sizeBytes = size,
                    quality = request.quality,
                    rawJson = request.rawJson ?: item?.raw?.toString(),
                    artworkUrl = artworkUrl,
                    lyricPath = lyricPath,
                    translationPath = transPath,
                    lrcUri = lrcUri,
                ),
            )
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
                    artwork = coverUri ?: artworkUrl,
                ),
            )
            Result.success(workDataOf(KEY_DONE to size, KEY_TOTAL to size))
        } catch (t: Throwable) {
            android.util.Log.w(LOG_TAG, "download failed for ${request.key} (attempt $runAttemptCount)", t)
            if (t !is kotlinx.coroutines.CancellationException) {
                ensureActive()
                store.patch(request.key) { it.copy(stage = DownloadStage.FAILED, error = readableError(t)) }
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

            val written = HttpRange.writeTo(
                target = part,
                body = body,
                resumeOffset = offset,
                totalHint = total,
                progress = onProgress,
                meter = meter,
            )
            // 连接中途断了 read() 也会返回 -1：没下满就当可重试，下次从 .part 接着下，别把半截文件当成品。
            if (total > 0L && written.written < total) {
                throw HttpRange.TransientException("网络中断（${written.written}/$total），已保存进度，重试会接着下")
            }
        }
        return part.isFile && part.length() > 0L
    }

    private fun requestItem(request: DownloadRequest): MusicItem? {
        val raw = request.rawJson?.let { runCatching { Json2.parse(it) as? JsonObject }.getOrNull() }
        return raw?.let { MusicItem.from(it, request.pluginId) } ?: fallbackItem(request)
    }

    /** 拉封面字节：http 失败再试 https（部分平台 http 图床会 302 / 拒连）。 */
    private fun fetchCover(url: String): ByteArray? {
        if (!url.startsWith("http")) return null
        fun get(u: String): ByteArray? = runCatching {
            okHttp.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body ?: return@use null
                if ((body.contentLength()) > 8L * 1024 * 1024) return@use null
                body.bytes().takeIf { it.size > 64 }
            }
        }.getOrNull()
        return get(url) ?: if (url.startsWith("http://")) get("https://" + url.removePrefix("http://")) else null
    }

    /** musicItem 里直接带的 LRC 文本（MusicFree `rawLrc`），URL 不算。 */
    private fun inlineLrc(rawJson: String): String? {
        val o = runCatching { Json2.parse(rawJson) as? JsonObject }.getOrNull() ?: return null
        val v = (o["rawLrc"] ?: o["lyric"] ?: o["lrc"]) as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return v.content.takeIf { LRC_TIME.containsMatchIn(it) }
    }

    /** 原文 + 翻译合成一份标准 LRC：同一时间戳两行，播放器会当作双语显示。 */
    private fun mergedLrc(lrc: String, translation: String?): String = buildString {
        append(lrc.trimEnd()).append('\n')
        translation?.lineSequence()?.filter { LRC_TIME.containsMatchIn(it) }?.forEach { append(it).append('\n') }
    }

    private fun readableError(t: Throwable): String = when {
        t is HttpRange.PermanentException || t is HttpRange.TransientException -> t.message ?: "下载失败"
        t is java.net.UnknownHostException -> "网络不通，连不上下载服务器"
        t is java.net.SocketTimeoutException -> "下载超时，网络太慢，稍后重试"
        t is java.io.IOException && (t.message?.contains("ENOSPC") == true || t.message?.contains("No space") == true) -> "手机存储空间不足"
        t is java.io.IOException -> "网络中断，已保存进度，重试会接着下"
        t is SecurityException -> "没有写入存储的权限"
        t.message?.any { it.code > 0x2E80 } == true -> t.message!!
        else -> "下载失败（${t.javaClass.simpleName}）"
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
        private const val INFO_BUDGET_MS = 10_000L
        private const val LYRIC_BUDGET_MS = 15_000L
        private val LRC_TIME = Regex("""\[\d{1,3}:\d{1,2}([.:]\d{1,3})?]""")

        fun decode(json: String): DownloadRequest? =
            runCatching { kotlinx.serialization.json.Json.decodeFromString<DownloadRequest>(json) }.getOrNull()

        fun encode(request: DownloadRequest): String =
            kotlinx.serialization.json.Json.encodeToString<DownloadRequest>(request)
    }
}
