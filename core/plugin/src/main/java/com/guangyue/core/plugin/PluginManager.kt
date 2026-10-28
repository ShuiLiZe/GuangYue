package com.guangyue.core.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import com.guangyue.core.plugin.runtime.PluginRuntime
import com.guangyue.core.plugin.runtime.PluginRuntimeException
import com.guangyue.core.plugin.runtime.PreludeProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plugin lifecycle: install from a local file or a URL, enable/disable, delete, check updates.
 *
 * Installing never trusts the bytes: the code is parsed and booted in a throwaway sandbox
 * runtime first, and only stored when it exports a valid MusicFree module.
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PluginStore,
    private val http: OkHttpClient,
    private val prelude: PreludeProvider,
) {
    private val _plugins = MutableStateFlow<List<PluginMeta>>(emptyList())
    val plugins: StateFlow<List<PluginMeta>> = _plugins.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @Volatile private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        _plugins.value = store.readIndex().sortedBy { it.name }
        loaded = true
    }

    fun enabled(): List<PluginMeta> = _plugins.value.filter { it.enabled && it.error == null }

    fun find(id: String): PluginMeta? = _plugins.value.firstOrNull { it.id == id }

    fun findByPlatform(platform: String): PluginMeta? = _plugins.value.firstOrNull { it.platform == platform }

    // ------------------------------------------------------------------ install

    /**
     * Install from a machine-local file (SAF uri or plain path).
     *
     * @param uri `content://…` or `file://…`
     */
    suspend fun installFromUri(uri: Uri): InstallResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val code = readText(uri) ?: return@withContext InstallResult.Failure("无法读取文件")
            installCode(code, uri.toString())
        } finally {
            _busy.value = false
        }
    }

    /** Install from a remote URL (raw .js file). */
    suspend fun installFromUrl(url: String): InstallResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val code = download(url) ?: return@withContext InstallResult.Failure("下载失败：$url")
            installCode(code, url)
        } finally {
            _busy.value = false
        }
    }

    /**
     * Validate + store plugin source.
     *
     * The sandbox probe is what rejects non-MusicFree files (HTML error pages, minified bundles
     * without `module.exports`, …) before anything is written to disk.
     */
    suspend fun installCode(code: String, source: String): InstallResult = withContext(Dispatchers.IO) {
        if (code.isBlank()) return@withContext InstallResult.Failure("插件内容为空")

        val runtime = PluginRuntime(
            meta = PluginMeta(id = "", platform = "probe", name = "probe", version = "0", source = source),
            code = code,
            http = http,
            preludeProvider = prelude,
        )
        try {
            runtime.install()
            val probed = runtime.readMeta(source, code.toByteArray().size.toLong())
            if (!probed.hasSearch && probed.functions.isNotEmpty()) {
                return@withContext InstallResult.Failure("插件未实现 search 函数")
            }
            val id = store.idFor(probed.platform, source)

            // Same platform + same source → update; same platform + different source → reject.
            val existing = _plugins.value.firstOrNull { it.platform == probed.platform }
            if (existing != null && existing.id != id) {
                return@withContext InstallResult.Failure(
                    "已安装同平台插件「${existing.displayName}」，请先删除后再安装",
                )
            }

            val finalMeta = probed.copy(
                id = id,
                enabled = existing?.enabled ?: true,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            store.writeCode(id, code)
            store.writeMeta(finalMeta)
            upsert(finalMeta)
            InstallResult.Success(finalMeta)
        } catch (e: PluginRuntimeException) {
            InstallResult.Failure(e.message ?: "插件加载失败")
        } catch (t: Throwable) {
            InstallResult.Failure(t.message ?: "插件加载失败")
        } finally {
            runtime.close()
        }
    }

    // ------------------------------------------------------------------ manage

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext
        upsert(meta.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        store.delete(id)
        _plugins.value = _plugins.value.filterNot { it.id == id }
        store.writeIndex(_plugins.value)
    }

    /**
     * Compare the installed version with the plugin's `srcUrl` (MusicFree publishes a
     * `<id>.json` with `{ version, desc }` next to the .js file).
     */
    suspend fun checkUpdate(id: String): UpdateCheck = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext UpdateCheck.Unknown
        val url = meta.srcUrl ?: return@withContext UpdateCheck.NoSource
        val remote = runCatching { fetchVersion(url) }.getOrNull()
            ?: return@withContext UpdateCheck.Error("无法获取远程版本")
        if (compareVersion(remote, meta.version) > 0) {
            UpdateCheck.Available(current = meta.version, latest = remote)
        } else {
            UpdateCheck.Latest(current = meta.version)
        }
    }

    /** Download `srcUrl` again and re-install, preserving the enabled flag. */
    suspend fun update(id: String): InstallResult = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext InstallResult.Failure("插件不存在")
        val url = meta.srcUrl ?: return@withContext InstallResult.Failure("插件没有 srcUrl，无法更新")
        val wasEnabled = meta.enabled
        val code = download(url) ?: return@withContext InstallResult.Failure("下载失败：$url")
        installCode(code, url).also { if (it is InstallResult.Success && !wasEnabled) setEnabled(it.meta.id, false) }
    }

    /** Called after an install/update so the runtime reloads the code. */
    fun notifyReload(platform: String? = null) {
        reloadHook?.invoke(platform)
    }

    /** Installed by [MusicPluginBridge] so it can drop its runtime cache. */
    var reloadHook: ((String?) -> Unit)? = null

    // ------------------------------------------------------------------ internals

    private fun upsert(meta: PluginMeta) {
        val list = _plugins.value.toMutableList()
        val at = list.indexOfFirst { it.id == meta.id }
        if (at >= 0) list[at] = meta else list.add(meta)
        _plugins.value = list.sortedBy { it.name }
        store.writeIndex(_plugins.value)
        notifyReload(meta.platform)
    }

    private fun readText(uri: Uri): String? {
        return runCatching {
            if (uri.scheme == "file" || uri.scheme == null) {
                File(uri.path ?: return null).readText()
            } else {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
        }.getOrNull()
    }

    /** Display name of a picked file, for the "installed from x" line. */
    fun displayNameOf(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return File(uri.path ?: return null).name
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun download(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", PluginRuntime.UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
    }.getOrNull()

    /**
     * MusicFree stores the version either in a sibling `<name>.json` or in the plugin's own
     * `srcUrl` target. We try the .js first (some authors put the JSON at srcUrl - 3 chars).
     */
    private fun fetchVersion(srcUrl: String): String? {
        val candidates = buildList {
            add(srcUrl)
            if (srcUrl.endsWith(".js")) add(srcUrl.dropLast(3) + ".json")
            if (srcUrl.endsWith(".user.js")) add(srcUrl.removeSuffix(".user.js") + ".json")
        }
        for (u in candidates) {
            val body = download(u) ?: continue
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                val o = Json2.parse(trimmed).asObj() ?: continue
                o["version"].asStr()?.let { return it }
            } else if (u.endsWith(".js")) {
                // fall back to scanning the source for a version literal
                Regex("""version\s*:\s*['"]([^'"]+)['"]""").find(body)?.groupValues?.getOrNull(1)?.let { return it }
            }
        }
        return null
    }

    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

sealed interface InstallResult {
    data class Success(val meta: PluginMeta) : InstallResult
    data class Failure(val message: String) : InstallResult
}

sealed interface UpdateCheck {
    data class Available(val current: String, val latest: String) : UpdateCheck
    data class Latest(val current: String) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
    data object NoSource : UpdateCheck
    data object Unknown : UpdateCheck
}
