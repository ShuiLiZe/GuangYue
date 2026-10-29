package com.guangyue.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.guangyue.core.download.DownloadManager
import com.guangyue.core.download.DownloadedLibrary
import com.guangyue.core.player.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folia 原版界面的宿主。
 *
 * 视觉上一个像素都不改：assets 里放的就是从 folia-major 构建出来的 dist，这里只做三件事——
 * 1. 用 `https://appassets.androidplatform.net` 提供资源（WebViewAssetLoader），让 IndexedDB、
 *    service worker、跨源规则都表现得像一个正常的 https 站点；
 * 2. 注入 [NativeCallBridge]（`window.GuangYueNative`）与播放推送（`window.__gyPlayback`）；
 * 3. 把原生播放、曲库、插件、下载接进 Folia 的数据源适配层（见 web/folia-overlay）。
 */
@Singleton
class FoliaWebHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryGateway,
    private val playback: PlaybackBridge,
    private val localMusic: LocalMusicGateway,
    private val relay: MediaRelay,
    private val downloads: DownloadManager,
    private val downloaded: DownloadedLibrary,
    private val controller: PlaybackController,
    private val demoSeeder: DemoPluginSeeder,
) : BridgeDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bridge = NativeCallBridge(scope, this)

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var lastPlaybackScript: String? = null

    /** 页面就绪后要补发的推送排队处（WebView 还没建好时先把播放状态存下来）。 */
    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(BASE_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/media/", RelayPathHandler("media/"))
            .addPathHandler("/local/", RelayPathHandler("local/"))
            .build()
    }

    /** 创建 WebView，返回给 Activity 直接塞进布局。 */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun createWebView(): WebView {
        // 首次启动先把随包的演示插件装上（只在插件列表为空时），否则新安装的数据源一片空白。
        scope.launch { runCatching { demoSeeder.seedIfEmpty() } }
        val view = WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                @Suppress("DEPRECATION")
                allowFileAccess = false
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "$userAgentString GuangYue/0.0.3 Android"
                // Folia 是给桌面 Chrome 写的：字号、缩放都按 CSS 像素算，禁止系统字号把它放大。
                textZoom = 100
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }
            addJavascriptInterface(bridge, JS_BRIDGE_NAME)
            webViewClient = Client()
            // Folia 的输入框、滑块靠键盘焦点工作（桌面版就是这样）。WebView 默认不在 touch
            // 模式下接收焦点，这会让 setTimeout 里的 input.focus() 无声失败，所以显式打开；
            // 光标本身不可见，只有接外接键盘/方向键时才会被注意到。
            isFocusable = true
            isFocusableInTouchMode = true
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d("FoliaWeb", "[${msg.messageLevel()}] ${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}")
                    return true
                }
            }
            // 刘海/挖孔安全区由 MainActivity 把真实 inset 写进 --gy-inset-* 自定义属性，
            // CSS 侧用 env(safe-area-inset-*) 与这两个变量配合（见 folia-overlay/mobile.css）。
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView = view
        view.loadUrl("$BASE_URL/assets/folia/index.html")
        scope.launch {
            playback.attach { script ->
                lastPlaybackScript = script
                postJavascript(script)
            }
        }
        return view
    }

    /** 回到前台：恢复 WebView 的计时器/渲染（播放由 Media3 自己管，不受影响）。 */
    fun resume() {
        webView?.onResume()
        webView?.resumeTimers()
    }

    /** 退到后台：只暂停 WebView 自己的渲染与定时器，音频继续在 Service 里跑。 */
    fun pause() {
        webView?.onPause()
        webView?.pauseTimers()
    }

    fun destroy() {
        playback.detach()
        bridge.cancelAll()
        scope.cancel()
        webView?.destroy()
        webView = null
    }

    // ------------------------------------------------------------ BridgeDispatcher

    override fun postJavascript(script: String) {
        val view = webView ?: return
        view.post { runCatching { view.evaluateJavascript(script, null) } }
    }

    override fun emit(event: String, payload: JsonElement) {
        postJavascript("window.__gyEvent && window.__gyEvent(${quote(event)}, ${quote(payload.toString())});")
    }

    private fun quote(s: String): String = JsonPrimitive(s).let {
        kotlinx.serialization.json.Json.encodeToString(JsonPrimitive.serializer(), it)
    }

    override suspend fun dispatch(method: String, params: JsonObject, scope: CoroutineScope): JsonElement {
        return when (method) {
            // ---- 元信息
            "app.info" -> buildJsonObject {
                put("name", "广乐")
                put("version", "0.0.3")
                put("platform", "android")
                put("sdkInt", Build.VERSION.SDK_INT)
                put("device", Build.MODEL)
                put("nativePlayback", true)
            }

            // ---- 插件
            "plugin.list" -> library.pluginList()
            "plugin.setEnabled" -> library.pluginSetEnabled(params.strOr("id", ""), params.bool("enabled", true))
            "plugin.setAllEnabled" -> library.pluginSetAllEnabled(params.bool("enabled", true))
            "plugin.delete" -> library.pluginDelete(params.strOr("id", ""))
            "plugin.installFromUrl" -> library.pluginInstallFromUrl(params.strOr("url", ""))
            "plugin.installCode" -> library.pluginInstallCode(params.strOr("code", ""), params.strOr("name", "plugin.js"))
            "plugin.checkUpdates" -> library.pluginCheckUpdates()
            "plugin.update" -> library.pluginUpdate(params.strOr("id", ""))
            "plugin.selfTest" -> library.pluginSelfTest(params.strOr("id", ""))
            "plugin.qualities" -> library.qualities(params.strOr("id", ""))

            // ---- 订阅源
            "subscription.list" -> library.subscriptionList()
            "subscription.add" -> library.subscriptionAdd(params.strOr("url", ""))
            "subscription.refresh" -> library.subscriptionRefresh(params.strOr("url", ""))
            "subscription.remove" -> library.subscriptionRemove(params.strOr("url", ""))
            "subscription.installAll" -> library.subscriptionInstallAll(params.strOr("url", ""))
            "subscription.installSelected" -> library.subscriptionInstallSelected(
                params.strOr("url", ""),
                params.arrayOrNull("entries") ?: JsonArray(emptyList()),
            )

            // ---- 搜索 / 插件方法（GuangYueProvider 的落点）
            "music.search" -> library.search(
                keyword = params.strOr("keyword", ""),
                type = params.strOr("type", "music"),
                page = params.int("page", 1).coerceAtLeast(1),
                limit = params.int("limit", 30),
            )
            "music.mediaSource" -> library.mediaSource(params)
            "music.lyric" -> library.lyric(params)
            "music.info" -> library.musicInfo(params)
            "music.albumInfo" -> library.albumInfo(params)
            "music.topLists" -> library.topLists(params.strOr("pluginId", ""))
            "music.topListDetail" -> library.topListDetail(
                params.strOr("pluginId", ""),
                params.objOrNull("item") ?: JsonObject(emptyMap()),
                params.int("page", 1),
            )
            "quality.ladder" -> library.qualityLadder()

            // ---- 曲库
            "library.tracks" -> library.libraryTracks(params.strOr("bucket", "recent"), params.int("limit", 100))
            "library.playlists" -> library.libraryPlaylists()
            "playlist.tracks" -> library.playlistTracks(params.long("id"))
            "playlist.create" -> library.playlistCreate(params.strOr("name", "新歌单"))
            "playlist.rename" -> library.playlistRename(params.long("id"), params.strOr("name", ""))
            "playlist.delete" -> library.playlistDelete(params.long("id"))
            "playlist.addItems" -> library.playlistAddItems(
                params.long("id"),
                params.arrayOrNull("items") ?: JsonArray(emptyList()),
                params.strOr("quality", "standard"),
            )
            "playlist.removeTrack" -> library.playlistRemoveTrack(params.long("id"), params.strOr("key", ""))
            "track.setFavorite" -> library.trackSetFavorite(params.strOr("key", ""), params.bool("favorite", true))
            "search.history" -> library.searchHistory()
            "search.record" -> library.searchHistoryRecord(params.strOr("query", ""))
            "search.clearHistory" -> library.searchHistoryClear()

            // ---- 本地音乐
            "local.browse" -> localBrowse(buildJsonObject {
                put("root", params.strOr("root", "手机存储"))
            })
            "local.tree" -> localBrowse(params)

            // ---- 下载
            "download.list" -> library.downloadList()
            "download.enqueue" -> library.downloadEnqueue(
                params.arrayOrNull("items") ?: JsonArray(emptyList()),
                params.strOr("quality", "high"),
            )
            "download.cancel" -> library.downloadCancel(params.strOr("key", ""))
            "download.remove" -> library.downloadRemove(params.strOr("key", ""), params.bool("deleteFile", true))

            // ---- 原生播放
            "player.state" -> playerState()
            "player.setQueue" -> playback.setQueue(
                items = params.arrayOrNull("items")?.mapNotNull { it as? JsonObject } ?: emptyList(),
                startIndex = params.int("startIndex", 0),
                play = params.bool("play", true),
            )
            "player.play" -> playback.play()
            "player.pause" -> playback.pause()
            "player.toggle" -> playback.toggle()
            "player.next" -> playback.next()
            "player.previous" -> playback.previous()
            "player.stop" -> playback.stop()
            "player.seek" -> playback.seekTo(params.long("positionMs"))
            "player.volume" -> playback.setVolume(params.int("percent", 100) / 100f)
            "player.shuffle" -> playback.setShuffle(params.bool("enabled", false))
            "player.repeat" -> playback.cycleRepeat()
            "player.quality" -> playback.setQuality(params.strOr("quality", "standard"))
            "player.queue" -> playback.queueSnapshot()
            "player.removeAt" -> playback.removeFromQueue(params.int("index", -1))

            // ---- 媒体中转（给 <audio> 用的短链）
            "media.token" -> buildJsonObject {
                val token = relay.register(
                    url = params.strOr("url", ""),
                    headers = params.stringMap("headers"),
                    mimeType = params.str("mimeType"),
                )
                put("token", JsonPrimitive(token))
                put("url", JsonPrimitive("$BASE_URL/media/$token"))
            }

            else -> buildJsonObject {
                put("error", JsonPrimitive("unknown_method"))
                put("method", JsonPrimitive(method))
            }
        }
    }

    private suspend fun playerState(): JsonElement = buildJsonObject {
        val s = controller.state.value
        put("isPlaying", JsonPrimitive(s.isPlaying))
        put("positionMs", JsonPrimitive(s.positionMs))
        put("durationMs", JsonPrimitive(s.durationMs))
        put("queueSize", JsonPrimitive(s.queueSize))
        put("index", JsonPrimitive(s.index))
    }

    private suspend fun localBrowse(params: JsonObject): JsonElement {
        val root = params.strOr("root", "手机存储")
        val tree = localMusic.browse(root)
        return buildJsonObject { put("tree", nodeJson(tree)); put("rootName", JsonPrimitive(root)) }
    }

    private fun nodeJson(node: JsonNode): JsonElement = buildJsonObject {
        put("name", JsonPrimitive(node.name))
        put("relativePath", JsonPrimitive(node.relativePath))
        put("isFolder", JsonPrimitive(node.isFolder))
        node.song?.let { s ->
            put("song", buildJsonObject {
                put("id", JsonPrimitive(s.id))
                put("title", JsonPrimitive(s.title))
                put("artist", JsonPrimitive(s.artist))
                put("album", JsonPrimitive(s.album))
                put("duration", JsonPrimitive(s.durationMs))
                put("artwork", JsonPrimitive(s.artworkUrl))
                put("contentUri", JsonPrimitive(s.contentUri))
                put("sizeBytes", JsonPrimitive(s.sizeBytes))
                put("mimeType", JsonPrimitive(s.mimeType))
                put("fileName", JsonPrimitive(s.fileName))
                put("folder", JsonPrimitive(s.folder))
            })
        }
        put("files", buildJsonArray {
            node.song?.let { s ->
                add(buildJsonObject {
                    put("name", JsonPrimitive(s.fileName))
                    put("relativePath", JsonPrimitive(node.relativePath))
                    put("kind", JsonPrimitive("audio"))
                    put("size", JsonPrimitive(s.sizeBytes))
                    put("lastModified", JsonPrimitive(0))
                    put("signature", JsonPrimitive(s.id))
                })
            }
        })
        put("children", buildJsonArray { node.children.forEach { add(nodeJson(it)) } })
    }

    // ------------------------------------------------------------ WebViewClient

    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != BASE_HOST) return null
            val path = url.path.orEmpty().trimStart('/')
            if (path.startsWith("media/") || path.startsWith("local/")) {
                val range = request.requestHeaders["range"] ?: request.requestHeaders["Range"]
                return relay.serve(path, range)
            }
            return assetLoader.shouldInterceptRequest(url)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.host == BASE_HOST) return false
            // 外部链接交给系统浏览器，WebView 自己永远不离开应用。
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            lastPlaybackScript?.let { view.evaluateJavascript(it, null) }
        }
    }

    /** 把 `/media/...`、`/local/...` 交给 [MediaRelay] 处理。 */
    private inner class RelayPathHandler(private val prefix: String) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? = relay.serve(prefix + path, null)
    }

    companion object {
        const val BASE_HOST = "appassets.androidplatform.net"
        const val BASE_URL = "https://$BASE_HOST"
        const val JS_BRIDGE_NAME = "GuangYueNative"
    }
}
