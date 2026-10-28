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
        val PLUGIN_JSON = kotlinx.serialization.serializer<PluginMeta>()
        val PLUGIN_LIST = ListSerializer(PLUGIN_JSON)
    }
}

/** Fills a JsonObject from a MusicFree result `{ data, isEnd }`. */
internal fun pageEnvelope(data: JsonArray?, isEnd: Boolean): JsonObject = buildJsonObject {
    put("data", data ?: JsonArray(emptyList()))
    put("isEnd", JsonPrimitive(isEnd))
}
