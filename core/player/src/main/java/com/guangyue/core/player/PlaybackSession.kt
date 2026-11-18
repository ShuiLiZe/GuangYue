package com.guangyue.core.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 11-18 照 Folia「恢复上次的歌曲」：队列、当前下标与进度存到 files/playback_session.json，冷启动时还原
 * （Folia useAudioSettingsStore autoPlayOnLaunch：关 = 恢复上次的歌曲并保持暂停，开 = 自动续播）。
 */
object PlaybackSession {
    data class Snapshot(val items: List<QueueItem>, val index: Int, val positionMs: Long)

    private const val FILE = "playback_session.json"
    private const val PREFS = "playback_session"
    private const val KEY_AUTO_PLAY = "auto_play_on_launch"
    /** 最多存这么多首（大队列整份写盘太重）。 */
    private const val MAX_ITEMS = 500

    fun autoPlayOnLaunch(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_PLAY, false)

    fun setAutoPlayOnLaunch(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_PLAY, on).apply()
    }

    private fun map(o: JSONObject?): Map<String, String> =
        o?.keys()?.asSequence()?.associateWith { o.optString(it) }.orEmpty()

    private fun mapJson(m: Map<String, String>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }

    fun encode(s: Snapshot): String {
        // 只留当前那首前后各一段，下标跟着平移。
        val start = (s.index - MAX_ITEMS / 2).coerceIn(0, (s.items.size - MAX_ITEMS).coerceAtLeast(0))
        val kept = s.items.drop(start).take(MAX_ITEMS)
        val arr = JSONArray()
        kept.forEach { q ->
            arr.put(JSONObject().apply {
                put("key", q.key); put("title", q.title); put("artist", q.artist)
                q.album?.let { put("album", it) }
                q.artworkUri?.let { put("art", it) }
                q.rawJson?.let { put("raw", it) }
                put("plugin", q.origin.pluginId); put("trackKey", q.origin.trackKey); put("quality", q.origin.quality.wire)
                q.origin.directUrl?.let { put("direct", it) }
                if (q.origin.extra.isNotEmpty()) put("extra", mapJson(q.origin.extra))
                if (q.origin.headers.isNotEmpty()) put("headers", mapJson(q.origin.headers))
                if (q.origin.availableQualities.isNotEmpty()) put("qualities", JSONArray(q.origin.availableQualities))
            })
        }
        return JSONObject().put("index", (s.index - start).coerceIn(0, (kept.size - 1).coerceAtLeast(0))).put("position", s.positionMs).put("items", arr).toString()
    }

    fun decode(text: String): Snapshot? = runCatching {
        val o = JSONObject(text)
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            QueueItem(
                key = j.getString("key"),
                title = j.optString("title"),
                artist = j.optString("artist"),
                album = j.optString("album").takeIf { j.has("album") },
                artworkUri = j.optString("art").takeIf { j.has("art") },
                rawJson = j.optString("raw").takeIf { j.has("raw") },
                origin = MediaOrigin(
                    pluginId = j.optString("plugin"),
                    trackKey = j.optString("trackKey"),
                    quality = Quality.fromWire(j.optString("quality")),
                    directUrl = j.optString("direct").takeIf { j.has("direct") },
                    extra = map(j.optJSONObject("extra")),
                    headers = map(j.optJSONObject("headers")),
                    availableQualities = j.optJSONArray("qualities")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                ),
            )
        }
        if (items.isEmpty()) null
        else Snapshot(items, o.optInt("index").coerceIn(0, items.size - 1), o.optLong("position").coerceAtLeast(0L))
    }.getOrNull()

    fun save(context: Context, s: Snapshot) {
        runCatching {
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(encode(s))
            tmp.renameTo(f)
        }
    }

    fun load(context: Context): Snapshot? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()?.let { decode(it) }
}
