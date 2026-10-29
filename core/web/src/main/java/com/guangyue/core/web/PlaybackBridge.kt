package com.guangyue.core.web

import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackController
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 原生 Media3 播放 → Folia 前端的单向状态流 + 双向控制口。
 *
 * 为什么状态只有原生说了算：后台播放、锁屏卡片、通知栏、耳机线控都由 [PlaybackController]
 * 背后的 MediaSessionService 维护；Web 侧只负责把它们画出来。前端用 requestAnimationFrame
 * 在两次推送之间按时间戳插值（本文件每 [POSITION_TICK_MS] 毫秒推一次基准），所以歌词滚动
 * 不会一顿一顿。
 *
 * Folia 自己的 `<audio>` 元素在这里会被“静音但存活”地对待：它的 src 指向
 * [GuangYueWebViewClient] 提供的 `/media/…` 桥地址，只用于 WebAudio 频谱与视频层取样，
 * 真正的声音来自 ExoPlayer。
 */
@Singleton
class PlaybackBridge @Inject constructor(
    private val controller: PlaybackController,
    private val tracks: TrackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 上一次推给页面的快照（JSON 字符串），只在内容变化时重推，省掉每秒 10 次的 evaluateJavascript。 */
    private val lastSent = MutableStateFlow<String?>(null)

    /** 页面挂载后才推送。 */
    @Volatile
    private var emitter: ((String) -> Unit)? = null

    private var tickerJob: kotlinx.coroutines.Job? = null

    /** 页面就绪：订阅状态并立刻补一次快照。 */
    fun attach(emit: (String) -> Unit) {
        emitter = emit
        scope.launch { controller.state.collect { publish(force = true) } }
        startTicker()
    }

    fun detach() {
        emitter = null
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                publish(force = false)
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun publish(force: Boolean) {
        val sink = emitter ?: return
        val state = controller.state.value
        val payload = buildJsonObject {
            put("isPlaying", state.isPlaying)
            put("positionMs", state.positionMs)
            put("durationMs", state.durationMs)
            put("bufferedMs", state.bufferedMs)
            put("queueSize", state.queueSize)
            put("index", state.index)
            put("shuffle", state.shuffle)
            put("repeatMode", state.repeatMode)
            put("error", state.error?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
            put("updatedAt", System.currentTimeMillis())
            val item = state.item
            put("track", if (item == null) JsonNull else trackJson(item))
        }
        val text = payload.toString()
        // 只有播放中位置在变；暂停时重复推送没意义，交给 force。
        val signature = if (state.isPlaying) text else text.replace(Regex("\"positionMs\":\\d+"), "")
        if (!force && signature == lastSent.value) return
        lastSent.value = signature
        sink("window.__gyPlayback && window.__gyPlayback($text);")
    }

    private fun trackJson(item: QueueItem) = buildJsonObject {
        put("key", item.key)
        put("title", item.title)
        put("artist", item.artist)
        put("album", item.album ?: "")
        put("artworkUri", item.artworkUri ?: "")
        put("pluginId", item.origin.pluginId)
        put("quality", item.origin.quality.wire)
        put("localUri", item.origin.directUrl ?: "")
    }

    // ------------------------------------------------------------- 控制

    /** 前端一次把整个队列交过来：`[{key,title,artist,album,artwork,pluginId,quality,raw}]`。 */
    suspend fun setQueue(items: List<JsonObject>, startIndex: Int, play: Boolean): JsonElement =
        withContext(Dispatchers.IO) {
            val queue = ArrayList<QueueItem>(items.size)
            items.forEach { raw ->
                val key = raw.str("key")
                val existing = key?.let { tracks.byKey(it) }
                queue += if (existing != null) {
                    QueueItem.of(existing).copy(
                        origin = QueueItem.of(existing).origin.copy(
                            quality = Quality.parse(raw.str("quality")),
                        ),
                    )
                } else {
                    toQueueItem(raw)
                }
            }
            if (queue.isEmpty()) return@withContext buildJsonObject { put("ok", false) }
            // 先落库，让 getMediaSource 能读到 raw JSON。
            for (item in queue) {
                val raw = items.firstOrNull { it.str("key") == item.key || it.str("id") == item.key }
                val json = raw?.objOrNull("raw") ?: raw
                tracks.cache(
                    TrackEntity(
                        key = item.key,
                        title = item.title,
                        artist = item.artist,
                        album = item.album ?: "",
                        artwork = item.artworkUri,
                        durationMs = 0L,
                        platform = item.origin.pluginId,
                        songId = json?.str("id") ?: item.key.substringAfter(':'),
                        quality = item.origin.quality.wire,
                        rawJson = json?.toString(),
                        localUri = item.origin.directUrl,
                        source = if (item.origin.directUrl?.startsWith("content://") == true) {
                            TrackEntity.SOURCE_LOCAL
                        } else {
                            TrackEntity.SOURCE_PLUGIN
                        },
                    ),
                )
            }
            withContext(Dispatchers.Main.immediate) {
                controller.setQueue(queue, startIndex.coerceIn(0, queue.lastIndex), play)
            }
            buildJsonObject { put("ok", true); put("size", queue.size) }
        }

    private fun toQueueItem(raw: JsonObject): QueueItem {
        val pluginId = raw.str("pluginId") ?: raw.str("platform") ?: ""
        val id = raw.str("id") ?: raw.str("songId") ?: raw.str("key").orEmpty()
        val key = raw.str("key") ?: "$pluginId:$id"
        return QueueItem(
            key = key,
            title = raw.strOr("title", "未知歌曲"),
            artist = raw.strOr("artist", ""),
            album = raw.str("album"),
            artworkUri = raw.str("artwork")?.takeIf { it.isNotBlank() },
            origin = MediaOrigin(
                pluginId = pluginId,
                trackKey = key,
                quality = Quality.parse(raw.str("quality")),
                directUrl = raw.str("localUri")?.takeIf { it.isNotBlank() },
                availableQualities = emptyList(),
            ),
            rawJson = (raw.objOrNull("raw") ?: raw).toString(),
        )
    }

    fun playNow(raw: JsonObject): JsonElement {
        val item = toQueueItem(raw)
        controller.playNow(item)
        return buildJsonObject { put("ok", true) }
    }

    fun play(): JsonElement { controller.play(); return OK }
    fun pause(): JsonElement { controller.pause(); return OK }
    fun toggle(): JsonElement { controller.toggle(); return OK }
    fun next(): JsonElement { controller.next(); return OK }
    fun previous(): JsonElement { controller.previous(); return OK }
    fun stop(): JsonElement { controller.stop(); return OK }

    fun seekTo(positionMs: Long): JsonElement {
        controller.seekTo(positionMs.coerceAtLeast(0L))
        return OK
    }

    fun setVolume(volume: Float): JsonElement {
        controller.setVolume(volume.coerceIn(0f, 1f))
        return OK
    }

    fun setShuffle(on: Boolean): JsonElement {
        controller.setShuffle(on); return OK
    }

    fun cycleRepeat(): JsonElement {
        controller.cycleRepeat()
        return buildJsonObject { put("repeatMode", controller.state.value.repeatMode) }
    }

    fun setQuality(quality: String): JsonElement {
        controller.setQuality(Quality.parse(quality))
        return OK
    }

    fun queueSnapshot(): JsonElement = buildJsonObject {
        put("queue", buildJsonArray { controller.queueSnapshot().forEach { add(trackJson(it)) } })
        put("index", controller.state.value.index)
    }

    fun removeFromQueue(index: Int): JsonElement {
        controller.removeAt(index)
        return OK
    }

    private val OK: JsonElement = buildJsonObject { put("ok", true) }

    companion object {
        /** 位置推送间隔。60ms 落在 50ms 误差预算内，留出一次 IPC 往返的余量。 */
        const val POSITION_TICK_MS = 60L
    }
}
