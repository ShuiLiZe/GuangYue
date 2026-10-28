package com.guangyue.core.player

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Media3 [ResolvingDataSource.Factory] that only asks the plugin for a fresh direct url when
 * playback is about to start — lazily, at open()-time, never while browsing.
 *
 * Rules:
 *  - `guangyue://resolve/<key>` uris carry a key into [OriginStore]; the real payload is
 *    resolved on open via [MediaSourceResolver] (plugin `getMediaSource`).
 *  - `http(s)` / `file` uris pass straight through (demo plugin, local files, downloads).
 *  - A failed resolution is retried **once** with a freshly resolved url, because
 *    MusicFree-style sources hand out short-lived signed links.
 */
// Note: ResolvingDataSource.Factory is final in Media3 1.11, so the factory is built in
// MediaDataSourceProvider and only the resolver implementation lives here.
@UnstableApi
class PluginResolver(
    private val resolver: MediaSourceResolver,
    private val timeoutMs: Long,
    private val logTag: String,
) : ResolvingDataSource.Resolver {

    private data class Cache(val key: String, val url: String, val headers: Map<String, String>)

    private var cache: Cache? = null
    private var failedOnce = false

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val raw = dataSpec.uri.toString()
        if (!raw.startsWith(SCHEME)) return dataSpec

        val key = raw.substringAfter("$SCHEME://resolve/", missingDelimiterValue = "")
        val origin = OriginStore.peek(key) ?: throw IOException("no plugin origin registered for key=$key")

        val hit = cache
        if (hit != null && hit.key == key) return apply(hit, dataSpec)

        val source = resolveBlocking(origin)
        cache = Cache(key, source.url, source.headers)
        failedOnce = false
        Log.d(logTag, "resolved ${origin.pluginId} -> ${source.url.take(80)}")
        return apply(cache!!, dataSpec)
    }

    override fun resolveReportedUri(uri: android.net.Uri): android.net.Uri = uri

    /**
     * Called after a read error. Returns true when the caller should retry, having dropped the
     * cached url so the next open() resolves again.
     */
    fun invalidate(reason: String?): Boolean {
        if (failedOnce) {
            Log.w(logTag, "giving up after second resolve attempt: $reason")
            return false
        }
        failedOnce = true
        cache = null
        Log.i(logTag, "invalidating resolved url, retrying once ($reason)")
        return true
    }

    private fun apply(c: Cache, spec: DataSpec): DataSpec {
        val b = spec.buildUpon().setUri(c.url)
        if (c.headers.isNotEmpty()) b.setHttpRequestHeaders(c.headers)
        return b.build()
    }

    private fun resolveBlocking(origin: MediaOrigin): ResolvedSource {
        origin.directUrl?.let { direct ->
            if (direct.startsWith("http") || direct.startsWith("file") || direct.startsWith("content")) {
                return ResolvedSource(direct, origin.headers, volatile = false)
            }
        }
        val result = runBlocking { withTimeoutOrNull(timeoutMs) { resolver.resolve(origin) } }
        return result ?: throw IOException("plugin getMediaSource timed out after ${timeoutMs}ms")
    }

    companion object {
        const val SCHEME = "guangyue"
    }
}

/**
 * Media3 uris cannot carry a Bundle, so the session stashes the origin under the same key it
 * puts into the DataSpec uri and the resolver pops it lazily.
 */
object OriginStore {
    private val map = LinkedHashMap<String, MediaOrigin>()

    @Synchronized
    fun put(key: String, origin: MediaOrigin) {
        if (map.size > 96) map.remove(map.keys.first())
        map[key] = origin
    }

    @Synchronized
    fun peek(key: String): MediaOrigin? = map[key]

    @Synchronized
    fun remove(key: String): MediaOrigin? = map.remove(key)

    fun clear() {
        synchronized(this) { map.clear() }
    }
}

/** Build the DataSpec uri marker for an item key. */
fun resolveUri(key: String): String = "guangyue://resolve/$key"
