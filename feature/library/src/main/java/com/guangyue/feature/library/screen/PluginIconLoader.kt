package com.guangyue.feature.library.screen

import android.content.Context
import android.util.Log
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 插件站点图标的加载器（0.0.9 b）。
 *
 * 真机 0.0.9 a 的现场：8 个源的图标**全部**是首字圆圈，logcat 里 Coil 一条日志都没有。
 * 排查结论（详见 `docs/ui-0.0.9/b/report.md`）：
 *
 * 1. `AsyncImage(model = 裸 URL 字符串)` 走的是 App 的 **coil3 单例 ImageLoader**，
 *    该单例由 `coil-network-okhttp` 的 ServiceLoader 自动装一个**自带 OkHttp 客户端**的
 *    `OkHttpNetworkFetcherFactory` —— 跟 App 里那些没证书 pin、没代理配置的自建客户端不是一回事，
 *    而且它默认 `connectTimeout/readTimeout = 10s`、失败也不打印。
 * 2. 更关键的是：a 版把「失败」直接解释成「换下一条候选并立刻标记用完」，
 *    站点 favicon 常见 301/302 到 https、以及慢 CDN，一次抖动就永久停在首字占位；
 *    而 `AsyncImage` 在 28dp 控件里按精确尺寸去解 PNG/ICO，解码失败也会被当成 URL 失效。
 *
 * 现在统一走 [probe]：**先**用一条独立、短超时、跟随重定向的 OkHttp 把候选地址试一下
 * （只要 HTTP 2xx 且拿到字节就算过），**再**交给 Coil 用 INEXACT 精度去解——两步都在 IO 线程，
 * 每个图标各自一个 `produceState`，不进 `LazyColumn` 的合成/布局路径，所以不阻塞列表滚动。
 */
internal object PluginIconLoader {

    private const val TAG = "GYICON"

    /** 只用于「这个 URL 到底通不通」的探测。超时压到 4s，图标不值得等更久。 */
    private val prober: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 试一个候选地址。返回 true 表示「网络层拿得到这张图」。
     *
     * 不做全量下载：只读前 16 字节确认非空即可（favicon/PNG 的头都在前几字节），
     * 但会把 Body 读完前的连接关掉，避免为了画一个 28dp 图标把整张 300KB 图下两遍。
     */
    suspend fun probe(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext false
        val ok = runCatching {
            prober.newCall(okhttp3.Request.Builder().url(url).header("Accept", "image/*,*/*").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.d(TAG, "probe ${resp.code} $url")
                        return@use false
                    }
                    val bytes = resp.body?.byteStream()?.read(ByteArray(16)) ?: -1
                    // favicon.ico 的魔数 00 00 01 00 / PNG 的 89 50 4E 47 —— 拿不到至少说明有内容。
                    bytes > 0
                }
        }.onFailure { Log.d(TAG, "probe fail ${it.javaClass.simpleName} $url") }.getOrDefault(false)
        ok
    }

    /**
     * 用 Coil 预解码一次，确认 28dp 图标真能画出来（ICO/APNG 这类 Coil 解不了的会在这里被挡下）。
     * 命中缓存时几乎零成本。
     */
    suspend fun decode(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                // 图标控件的最终尺寸是 28dp × 28dp，按 dp 的两倍给 px 足够清晰又不至于解 3000px 的图。
                .size(112, 112)
                .memoryCacheKey(url)
                .diskCacheKey(url)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
            context.imageLoader.execute(request) is SuccessResult
        }.onFailure { Log.d(TAG, "decode fail ${it.javaClass.simpleName} $url") }.getOrDefault(false)
    }

    /** 候选地址里第一个「网络通且能解码」的；全部不行返回 null（调用方回退首字占位）。 */
    suspend fun firstUsable(context: Context, candidates: List<String>): String? {
        for (url in candidates) {
            if (probe(context, url) && decode(context, url)) return url
        }
        return null
    }
}
