package com.guangyue.core.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.annotation.WorkerThread
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * On-disk plugin storage.
 *
 * ```
 * filesDir/plugins/
 *   <pluginId>/
 *     plugin.js       // the raw MusicFree plugin code
 *     meta.json       // PluginMeta cache
 *   index.json        // list of installed plugins (id, platform, version, enabled, source)
 * ```
 *
 * Nothing is executed here; installing only stores bytes and parses metadata in a sandboxed
 * runtime (see [PluginRuntime] / [PluginManager]).
 */
@Singleton
class PluginStore @Inject constructor(@ApplicationContext private val context: Context) {

    val root: File get() = File(context.filesDir, "plugins").apply { if (!exists()) mkdirs() }

    val indexFile: File get() = File(root, "index.json")

    fun dirOf(id: String): File = File(root, id)

    fun codeFile(id: String): File = File(dirOf(id), "plugin.js")

    fun metaFile(id: String): File = File(dirOf(id), "meta.json")

    /** 订阅源索引（subscription index）落盘位置。 */
    val subscriptionsFile: File get() = File(root, "subscriptions.json")

    @WorkerThread
    fun readIndex(): List<PluginMeta> {
        val text = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        return runCatching { Json2.json.decodeFromString(PLUGIN_LIST, text) }.getOrElse { emptyList() }
    }

    @WorkerThread
    fun writeIndex(list: List<PluginMeta>) {
        runCatching { indexFile.writeText(Json2.json.encodeToString(PLUGIN_LIST, list)) }
    }

    @WorkerThread
    fun readSubscriptions(): List<Subscription> {
        val text = runCatching { subscriptionsFile.readText() }.getOrNull() ?: return emptyList()
        return runCatching { Json2.json.decodeFromString(SUBSCRIPTION_LIST, text) }.getOrElse { emptyList() }
    }

    @WorkerThread
    fun writeSubscriptions(list: List<Subscription>) {
        runCatching { subscriptionsFile.writeText(Json2.json.encodeToString(SUBSCRIPTION_LIST, list)) }
    }

    /** 订阅源解析结果的离线缓存，断网时仍能列出「可安装的插件」。 */
    @WorkerThread
    fun readCachedSource(url: String): SubscriptionSource? {
        val f = File(sourceCacheDir(), CACHE_PREFIX + safeName(url) + ".json")
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return runCatching { Json2.json.decodeFromString(SOURCE_JSON, text) }.getOrNull()
    }

    @WorkerThread
    fun writeCachedSource(url: String, source: SubscriptionSource) {
        runCatching {
            val dir = sourceCacheDir()
            val f = File(dir, CACHE_PREFIX + safeName(url) + ".json")
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(Json2.json.encodeToString(SOURCE_JSON, source))
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }
    }

    private fun sourceCacheDir(): File = File(root, ".subs").apply { if (!exists()) mkdirs() }

    private fun safeName(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    @WorkerThread
    fun readCode(id: String): String? = codeFile(id).takeIf { it.isFile }?.readText()

    @WorkerThread
    fun writeCode(id: String, code: String) {
        val dir = dirOf(id).apply { if (!exists()) mkdirs() }
        val f = File(dir, "plugin.js")
        val tmp = File(dir, "plugin.js.tmp")
        tmp.writeText(code)
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    @WorkerThread
    fun writeMeta(meta: PluginMeta) {
        val dir = dirOf(meta.id).apply { if (!exists()) mkdirs() }
        runCatching {
            File(dir, "meta.json").writeText(Json2.json.encodeToString(PLUGIN_JSON, meta))
        }
    }

    @WorkerThread
    fun delete(id: String) {
        dirOf(id).deleteRecursively()
    }

    @WorkerThread
    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Stable, human-readable id derived from the plugin platform + source. */
    fun idFor(platform: String, source: String): String {
        val base = platform.ifBlank { "plugin" }.lowercase().replace(Regex("[^a-z0-9_.-]"), "-")
        val h = MessageDigest.getInstance("SHA-1").digest(source.toByteArray()).take(5)
            .joinToString("") { "%02x".format(it) }
        return "$base-$h"
    }

    private companion object {
        const val CACHE_PREFIX = "source-"
        val PLUGIN_JSON = kotlinx.serialization.serializer<PluginMeta>()
        val PLUGIN_LIST = ListSerializer(PLUGIN_JSON)
        val SUBSCRIPTION_JSON = kotlinx.serialization.serializer<Subscription>()
        val SUBSCRIPTION_LIST = ListSerializer(SUBSCRIPTION_JSON)
        val SOURCE_JSON = kotlinx.serialization.serializer<SubscriptionSource>()
    }
}

/** Fills a JsonObject from a MusicFree result `{ data, isEnd }`. */
internal fun pageEnvelope(data: JsonArray?, isEnd: Boolean): JsonObject = buildJsonObject {
    put("data", data ?: JsonArray(emptyList()))
    put("isEnd", JsonPrimitive(isEnd))
}
