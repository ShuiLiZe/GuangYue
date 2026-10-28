package com.guangyue.core.download

import android.content.Context
import android.os.Environment
import androidx.annotation.WorkerThread
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * 断点续传下载工具：Range 请求 + `.part` 临时文件。
 *
 * 设计要点：
 *  - 已下载的字节永远只追加到 `<name>.part`，中断后再次请求 `Range: bytes=<已有>-`；
 *  - 服务端返回 `200`（没理会 Range）时截断重来，避免拼出字节错位的文件；
 *  - 网络层把「可重试 / 不可重试」分开，让 WorkManager 决定 retry 还是 fail。
 */
internal object HttpRange {

    data class Result(val written: Long, val total: Long)

    /** 不可重试的错误（4xx、链接过期），WorkManager 直接失败。 */
    class PermanentException(message: String) : Exception(message)

    /** 可重试的错误（5xx、网络抖动、超时），WorkManager 退避重试。 */
    class TransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * 把 [body] 续写到 [target]。
     *
     * @param resumeOffset 已经写入 [target] 的字节数
     */
    @WorkerThread
    fun writeTo(
        target: File,
        body: InputStream,
        resumeOffset: Long,
        totalHint: Long,
        progress: (Long, Long) -> Unit,
        meter: BandwidthMeter,
    ): Result {
        var total = totalHint
        var written = resumeOffset
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        val raf = RandomAccessFile(target, "rw")
        try {
            raf.seek(resumeOffset)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = body.read(buffer)
                if (n < 0) break
                raf.write(buffer, 0, n)
                written += n
                meter.add(n)
                progress(written, total)
            }
        } finally {
            runCatching { raf.fd.sync() }
            runCatching { raf.close() }
        }
        if (total <= 0L) total = written
        return Result(written, total)
    }

    const val BUFFER_SIZE = 64 * 1024
}

/** 简易带宽计（字节/秒，1 秒滑动窗口）。 */
class BandwidthMeter {
    private val windowStart = AtomicLong(System.currentTimeMillis())
    private val windowBytes = AtomicLong(0L)

    @Volatile
    var bytesPerSecond: Long = 0L
        private set

    fun add(bytes: Int) {
        windowBytes.addAndGet(bytes.toLong())
        val now = System.currentTimeMillis()
        val start = windowStart.get()
        if (now - start >= 1000L) {
            val elapsed = (now - start).coerceAtLeast(1L)
            bytesPerSecond = windowBytes.getAndSet(0L) * 1000L / elapsed
            windowStart.set(now)
        }
    }

    fun reset() {
        windowStart.set(System.currentTimeMillis())
        windowBytes.set(0L)
        bytesPerSecond = 0L
    }
}

/** 公共存储 `Music/GuangYue/` 的路径计算（下载与扫描共用）。 */
object DownloadPaths {

    const val SUB_DIR = "GuangYue"
    const val PART_SUFFIX = ".part"
    const val LRC_SUFFIX = ".lrc"

    /** 人类可读的相对目录：`Music/GuangYue`。 */
    fun relativeDir(): String = "${Environment.DIRECTORY_MUSIC}/$SUB_DIR"

    /** 目标文件名：`歌手 - 标题.ext`，去掉文件系统非法字符，最长 120 字符。 */
    fun fileName(artist: String, title: String, extension: String): String {
        val safeArtist = sanitize(artist).ifBlank { "未知艺术家" }
        val safeTitle = sanitize(title).ifBlank { "未命名" }
        val ext = extension.removePrefix(".").lowercase().ifBlank { "mp3" }
        val base = "$safeArtist - $safeTitle"
        return (if (base.length > 120) base.take(120) else base) + "." + ext
    }

    /** 歌词文件名（与音频同名，扩展名 `.lrc`）。 */
    fun lrcName(audioFileName: String): String =
        audioFileName.substringBeforeLast('.', audioFileName) + LRC_SUFFIX

    fun sanitize(raw: String): String = raw
        .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.')

    /** 从 URL 猜扩展名：先看 path，再看 mime。 */
    fun extensionFrom(url: String, mimeType: String?): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val fromPath = path.substringAfterLast('.', "")
        if (fromPath.length in 2..5 && fromPath.all { it.isLetterOrDigit() }) {
            return fromPath.lowercase()
        }
        return when {
            mimeType == null -> "mp3"
            mimeType.contains("flac", true) -> "flac"
            mimeType.contains("wav", true) -> "wav"
            mimeType.contains("m4a", true) || mimeType.contains("mp4", true) -> "m4a"
            mimeType.contains("aac", true) -> "aac"
            mimeType.contains("ogg", true) -> "ogg"
            mimeType.contains("mpeg", true) || mimeType.contains("mp3", true) -> "mp3"
            else -> "mp3"
        }
    }

    /** 应用私有暂存目录（`.part` 与封面缓存放这里，避免污染公共音乐库）。 */
    fun stagingDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
            .apply { if (!exists()) mkdirs() }

    /** 旧版直接写公共目录时的目录（API < 29 用）。 */
    fun legacyPublicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), SUB_DIR)
            .apply { if (!exists()) mkdirs() }
}

/** 把流复制到输出流并汇报进度（封面 / 小文件用）。 */
@WorkerThread
fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    onBytes: (Long) -> Unit = {},
) {
    input.use { i ->
        output.use { o ->
            val buffer = ByteArray(HttpRange.BUFFER_SIZE)
            var acc = 0L
            while (true) {
                val n = i.read(buffer)
                if (n < 0) break
                o.write(buffer, 0, n)
                acc += n
                onBytes(acc)
            }
            o.flush()
        }
    }
}
