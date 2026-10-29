package com.guangyue.core.web

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `https://appassets.androidplatform.net` 上的资源与音频中转。
 *
 * 三条路径：
 * - `/assets/…` `index.html` 等 → assets 里的 Folia 构建产物（磁盘上不存在时回退到 assets）。
 * - `/media/<token>` → 音频中转。**不是**把文件搬进 Web：Folia 的 `<audio>` 元素需要真实
 *   可播放的 URL 来做频谱分析/视频层取样，所以这里按 Range 请求把插件给的直链（或本地
 *   content:// URI）转发成响应；真正的声音仍然由 ExoPlayer 输出，Web 侧那份是静音的。
 * - `/local/artwork/<albumId>` → MediaStore 封面。
 *
 * 所有 token 都是服务端签发的短 id，前端拿不到原始直链也就无从劫持；token 表跟着播放队列走。
 */
@Singleton
class MediaRelay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusic: LocalMusicGateway,
) {
    /** token → 中转目标。 */
    private val tokens = ConcurrentHashMap<String, Target>()

    data class Target(val url: String, val headers: Map<String, String>, val mimeType: String?)

    fun register(url: String, headers: Map<String, String>, mimeType: String?): String {
        val token = "%08x".format(System.nanoTime().toInt() xor url.hashCode())
        tokens[token] = Target(url, headers, mimeType)
        return token
    }

    fun target(token: String): Target? = tokens[token]

    /** WebViewAssetLoader 的 `shouldInterceptRequest` 入口。 */
    fun serve(path: String, range: String?): WebResourceResponse? = when {
        path.startsWith("media/") -> serveMedia(path.removePrefix("media/"), range)
        path.startsWith("local/artwork/") -> serveAlbumArt(path.removePrefix("local/artwork/"))
        else -> null
    }

    internal fun serveMedia(token: String, range: String?): WebResourceResponse? {
        val target = tokens[token] ?: return notFound()
        val bytes = runCatching { fetch(target, range) }.getOrNull()
            ?: return WebResourceResponse("audio/mpeg", null, 503, "Unavailable", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        return WebResourceResponse(
            target.mimeType ?: localMusic.mimeOf(target.url.substringBefore('?')),
            null,
            200,
            "OK",
            mapOf(
                "Accept-Ranges" to "bytes",
                "Cache-Control" to "no-store",
                "Access-Control-Allow-Origin" to "*",
            ),
            ByteArrayInputStream(bytes),
        )
    }

    internal fun fetch(target: Target, range: String?): ByteArray {
        val uri = android.net.Uri.parse(target.url)
        if (uri.scheme == "content" || uri.scheme == "file") {
            // 本地文件：直接整段读，Range 交给 WebAudio 自己处理（本地文件没有带宽问题）。
            return context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }
        val request = okhttp3.Request.Builder().url(target.url).apply {
            target.headers.forEach { (k, v) -> header(k, v) }
            range?.let { header("Range", it) }
        }.build()
        httpClient.newCall(request).execute().use { response ->
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    internal fun serveAlbumArt(albumId: String): WebResourceResponse? {
        val bytes = localMusic.openAlbumArtwork(albumId.toLongOrNull() ?: return notFound())
            ?: return notFound()
        return WebResourceResponse(
            "image/jpeg",
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "max-age=604800"),
            ByteArrayInputStream(bytes),
        )
    }

    private fun notFound() = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        mapOf(),
        ByteArrayInputStream("not found".toByteArray()),
    )

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
