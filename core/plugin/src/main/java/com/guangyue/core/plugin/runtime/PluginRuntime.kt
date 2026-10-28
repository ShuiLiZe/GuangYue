package com.guangyue.core.plugin.runtime

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import com.guangyue.core.plugin.Json2
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.HostBridge
import com.guangyue.core.plugin.PluginFunctions
import com.guangyue.core.plugin.PluginMetaCodec
import com.guangyue.core.plugin.asArr
import com.guangyue.core.plugin.asBool
import com.guangyue.core.plugin.asLong
import com.guangyue.core.plugin.asObj
import com.guangyue.core.plugin.asStr
import com.guangyue.core.plugin.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * One sandboxed QuickJS runtime per plugin.
 *
 * Design notes
 * ------------
 * * **Isolation** — every plugin gets its own [QuickJs] instance (own heap, own globals), so a
 *   crashing or memory-hungry plugin cannot take down another one or the app.
 * * **Time limit** — the interpreter is created with `evaluationTimeoutMillis = 15s`, and the
 *   same budget bounds a single function call. Calling [QuickJs.interruptEvaluation] from the
 *   watchdog is what actually stops a `while(true)` loop inside a plugin.
 * * **Memory limit** — `memoryLimit` caps the QuickJS heap (see [MEMORY_LIMIT_BYTES]); once hit,
 *   QuickJS throws out-of-memory inside JS instead of exhausting the process heap.
 * * **Serialisation** — the JS side always returns a JSON *string*. Only strings cross the
 *   bridge, so no JS object ever becomes a Java object and the runtime can be disposed at will.
 */
class PluginRuntime(
    val meta: PluginMeta,
    private val code: String,
    @com.guangyue.core.plugin.di.PlaybackHttp private val http: OkHttpClient,
    private val preludeProvider: PreludeProvider,
) {
    @Volatile private var qjs: QuickJs? = null
    private var installed = false

    /** Called for anything the plugin logs through `console.*`. */
    var onLog: ((level: String, message: String) -> Unit)? = null

    /** Called when the plugin's HTTP layer is used, for the debug log in plugin management. */
    var onHttp: ((method: String, url: String, status: Int, ms: Long) -> Unit)? = null

    val isAlive: Boolean get() = qjs?.isClosed == false

    @Synchronized
    fun install() {
        if (installed) return
        val runtime = QuickJs.create(Dispatchers.Default, moduleLoader())
        try {
            runtime.memoryLimit = MEMORY_LIMIT_BYTES
            runtime.maxStackSize = MAX_STACK_BYTES
            runtime.evaluationTimeoutMillis = EVAL_TIMEOUT_MS

            runtime.function("__gy_log") { args ->
                // Never `Any.toString()` here: quickjs-kt converts a returned TypeRef<*> by
                // calling toString(), so a hard cast to String throws for non-string JS values.
                val level = args.getOrNull(0)?.let(::asBridgeText) ?: "log"
                val msg = args.getOrNull(1)?.let(::asBridgeText) ?: ""
                onLog?.invoke(level, msg)
                Log.d(TAG, "[${meta.platform}] $level: $msg")
                null
            }

            runtime.function("__gy_http") { args ->
                val payload = args.getOrNull(0)?.let(::asBridgeText) ?: "{}"
                performHttp(payload)
            }

            // 1) host bridge (CommonJS + axios + timers + console) – plain JS, no plugin code yet
            evalBlocking(runtime, HostBridge.COMMONJS_SHIM, "bridge.js")
            // 2) the shared prelude bundle (cheerio / crypto-js / dayjs / qs / he / big-integer)
            evalBlocking(runtime, preludeProvider.code(), "prelude.js")
            // 3) the plugin itself, wrapped as a CommonJS module factory
            val wrapped = HostBridge.wrapPluginSource(code)
            evalBlocking(runtime, "globalThis.__gy_boot($wrapped);", "plugin.js")
            // 4) the call dispatcher the host uses
            evalBlocking(runtime, HostBridge.CALL_DISPATCHER, "dispatcher.js")

            if (runtime.evaluateBlocking("typeof globalThis.__plugin === 'object' && globalThis.__plugin !== null") != "true") {
                throw PluginRuntimeException("插件未导出 module.exports 对象")
            }
            qjs = runtime
            installed = true
        } catch (t: Throwable) {
            runCatching { runtime.close() }
            throw asRuntimeException(t)
        }
    }

    /**
     * Invoke `__plugin.<fn>(...args)`.
     *
     * Returns the parsed JSON result, or throws [PluginRuntimeException]. A missing function is
     * reported as [PluginRuntimeException.Missing] so callers can degrade gracefully.
     */
    fun call(fn: String, vararg args: JsonElement): JsonElement {
        val runtime = qjs ?: throw PluginRuntimeException("runtime not installed")
        val argsJson = JsonArray(args.toList()).toString()
        val expr = HostBridge.callExpression(fn, quoteJsString(argsJson))

        val raw: String = try {
            withWatchdog(runtime) {
                runtime.evaluateBlocking(expr)
                    ?: "{\"ok\":false,\"error\":\"empty result\"}"
            }
        } catch (e: PluginRuntimeException) {
            throw e
        } catch (t: Throwable) {
            throw asRuntimeException(t)
        }

        val obj = Json2.parse(raw).asObj() ?: throw PluginRuntimeException("bad bridge payload")
        if (obj["ok"].asBool() == true) {
            return decodePayload(obj["data"])
        }
        val message = obj["error"].asStr() ?: "unknown plugin error"
        if (obj["missing"].asBool() == true) throw PluginRuntimeException.Missing(fn, message)
        throw PluginRuntimeException(message)
    }

    /**
     * The dispatcher returns `{"ok":true,"data":"<json text>"}`, so the payload is a *string*
     * holding JSON. Older/hand-written call sites returned it inline — handle both.
     */
    private fun decodePayload(data: JsonElement?): JsonElement {
        val text = (data as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return data ?: JsonNull
        return Json2.parse(text) ?: JsonNull
    }

    /** Probe which MusicFree functions the plugin implements. */
    fun probeFunctions(): Set<String> {
        val expr = """
            (function(){
              var out = [];
              var names = ${JsonArray(PluginFunctions.ALL.map { JsonPrimitive(it) }).toString()};
              for (var i = 0; i < names.length; i++) {
                if (typeof globalThis.__plugin[names[i]] === 'function') out.push(names[i]);
              }
              return JSON.stringify(out);
            })()
        """.trimIndent()
        val runtime = qjs ?: return emptySet()
        val raw = runCatching { runtime.evaluateBlocking(expr) }.getOrNull() ?: return emptySet()
        val parsed = Json2.parse(raw)
        return parsed.asArr()?.mapNotNull { it.asStr() }?.toSet() ?: emptySet()
    }

    /**
     * 读取插件导出的元信息并宽松解析。
     *
     * JS 侧只做 `JSON.stringify`（见 [PluginMetaProbe]），返回的是**字符串**；
     * Kotlin 侧交给 [PluginMetaCodec] 用 kotlinx.serialization 解析，字段缺失可兜底、
     * 类型不符就按文本读取。全程不经过 quickjs-kt 的 `TypeConverter`，因此
     * `platform: true` / `version: 1` / `cacheControl: false` 这类插件不会再报
     * `No such type converter to convert 'boolean ...' to 'java.lang.String'`。
     */
    fun readMeta(source: String, sizeBytes: Long, fallbackPlatform: String? = null): PluginMeta {
        val decoded = PluginMetaCodec.decode(
            json = readMetaJson(),
            source = source,
            sizeBytes = sizeBytes,
            fallbackPlatform = fallbackPlatform,
        )
        decoded.meta?.let { return it }
        throw PluginRuntimeException(decoded.error ?: "插件元信息不完整")
    }

    /** 取插件元信息的原始 JSON 文本（不含类型转换）。 */
    fun readMetaJson(): String {
        val runtime = qjs ?: throw PluginRuntimeException("runtime not installed")
        runCatching { evalBlocking(runtime, PluginMetaProbe.SCRIPT, "meta-probe.js") }
        val raw = runCatching { evalBlocking(runtime, PluginMetaProbe.callSource(PluginFunctions.ALL), "meta.js") }
            .getOrElse { "{}" }
        val err = Json2.parse(raw).asObj()?.get("__error").asStr()
        if (!err.isNullOrBlank()) throw PluginRuntimeException(err)
        return raw.trim().ifEmpty { "{}" }
    }

    /** Fetch the remote plugin version file (`srcUrl`) to detect updates. */
    fun remoteVersion(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val o = Json2.parse(body).asObj() ?: return@use null
                o["version"].asStr() ?: "0"
            }
        }.getOrNull()
    }

    @Synchronized
    fun close() {
        runCatching { qjs?.close() }
        qjs = null
        installed = false
    }

    // ------------------------------------------------------------------ internals

    private fun performHttp(payloadJson: String): String {
        val started = System.currentTimeMillis()
        val req = Json2.parse(payloadJson).asObj() ?: return netError("bad request payload", 0, started, "?")
        val method = (req["method"].asStr() ?: "GET").uppercase()
        val url = req["url"].asStr() ?: return netError("missing url", 0, started, "?")
        val responseType = (req["responseType"].asStr() ?: "json").lowercase()
        val timeoutMs = req["timeout"].asLong()?.takeIf { it > 0 } ?: 0L
        val body = req["body"].asStr()

        val builder = Request.Builder().url(url).header("User-Agent", UA)
        req["headers"].asObj()?.forEach { (k, v) ->
            v.asStr()?.let { runCatching { builder.header(k, it) } }
        }

        val httpClient = if (timeoutMs > 0) {
            http.newBuilder()
                .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        } else {
            http
        }

        val request = try {
            if (method == "GET" || method == "HEAD") {
                builder.method(method, null).build()
            } else {
                val contentType = req["headers"].asObj()?.entries
                    ?.firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                    ?.value?.asStr() ?: "application/json; charset=utf-8"
                builder.method(method, (body ?: "").toRequestBody(contentType.toMediaTypeOrNull())).build()
            }
        } catch (e: IllegalArgumentException) {
            return netError("bad url: ${e.message}", 0, started, url)
        }

        return try {
            httpClient.newCall(request).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val text = decode(resp.headers, bytes)
                val dataJson: String = when (responseType) {
                    "arraybuffer", "blob" -> JsonPrimitive(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).toString()
                    "text" -> JsonPrimitive(text).toString()
                    else -> {
                        val trimmed = text.trim()
                        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                            // Re-serialise through the parser so malformed JSON fails here, not in JS.
                            Json2.parse(trimmed)?.toString() ?: JsonPrimitive(trimmed).toString()
                        } else {
                            JsonPrimitive(text).toString()
                        }
                    }
                }
                onHttp?.invoke(method, url, resp.code, System.currentTimeMillis() - started)
                buildString {
                    append("{\"status\":").append(resp.code)
                    append(",\"statusText\":").append(JsonPrimitive(resp.message))
                    append(",\"headers\":{")
                    var first = true
                    for (name in resp.headers.names()) {
                        if (!first) append(',')
                        first = false
                        append(JsonPrimitive(name)).append(':').append(JsonPrimitive(resp.headers.values(name).joinToString(", ")))
                    }
                    append("},\"data\":").append(dataJson).append('}')
                }
            }
        } catch (e: IOException) {
            onHttp?.invoke(method, url, -1, System.currentTimeMillis() - started)
            netError(e.message ?: "network error", -1, started, url)
        } catch (e: Throwable) {
            netError(e.message ?: "network error", -1, started, url)
        }
    }

    private fun netError(message: String, status: Int, started: Long, url: String): String {
        onHttp?.invoke("?", url, status, System.currentTimeMillis() - started)
        return "{\"status\":$status,\"statusText\":${JsonPrimitive(message)},\"headers\":{},\"data\":null}"
    }

    private fun decode(headers: Headers, bytes: ByteArray): String {
        val charset = headers["content-type"]
            ?.substringAfter("charset=", "")
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
            ?: guessCharset(bytes)
        return String(bytes, charset)
    }

    private fun guessCharset(bytes: ByteArray): java.nio.charset.Charset {
        // Cheap UTF-8 validity check; fall back to GBK which most CN sources still use.
        val text = String(bytes, Charsets.UTF_8)
        return if (text.contains('\uFFFD')) {
            runCatching { java.nio.charset.Charset.forName("GBK") }.getOrDefault(Charsets.UTF_8)
        } else {
            Charsets.UTF_8
        }
    }

    /**
     * Runs [block] with a hard 15s wall-clock watchdog. The watchdog is what actually breaks a
     * plugin stuck in a tight loop: QuickJS only reports its own timeout between bytecode
     * instructions in some builds, `interruptEvaluation` is immediate.
     */
    private fun <T> withWatchdog(runtime: QuickJs, block: () -> T): T {
        val thread = Thread.currentThread()
        val watchdog = Thread {
            try {
                Thread.sleep(CALL_TIMEOUT_MS)
                runCatching { runtime.interruptEvaluation() }
                thread.interrupt()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; name = "gy-plugin-watchdog" }
        watchdog.start()
        return try {
            block()
        } finally {
            watchdog.interrupt()
        }
    }

    private fun evalBlocking(runtime: QuickJs, source: String, filename: String): String {
        val result: String? = runCatching { runtime.evaluateBlocking(source) }
            .getOrElse { throw asRuntimeException(it) }
        return result ?: ""
    }

    /** `QuickJs.evaluate` is suspending; the runtime always runs on a worker thread. */
    private fun QuickJs.evaluateBlocking(source: String): String? = kotlinx.coroutines.runBlocking {
        withContext(Dispatchers.IO) { evaluate<String>(source, "inline.js") }
    }

    private fun asRuntimeException(t: Throwable): PluginRuntimeException = when (t) {
        is PluginRuntimeException -> t
        is InterruptedException -> PluginRuntimeException("插件执行超时（${CALL_TIMEOUT_MS / 1000}s），已中断")
        else -> PluginRuntimeException(t.message ?: t::class.simpleName ?: "plugin error")
    }

    /**
     * Coerce a JS value handed to a host [QuickJs.function] into text.
     *
     * quickjs-kt binds `args` as `Array<TypeRef<*>>` and converts each entry by calling
     * `toString()` on it, so `args[0] as String` throws `ClassCastException` whenever the JS
     * side passes a number, boolean or object. Calling the same `toString()` on an `Any?` keeps
     * the conversion Kotlin-side and gets the JS value out alive.
     */
    @Suppress("UNCHECKED_CAST")
    private fun asBridgeText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

    companion object {
        private const val TAG = "GuangYuePlugin"
        const val UA = "GuangYue/1.0 (Android)"

        /**
         * Module loader for `require()` of npm packages (top-level await is rejected — MusicFree
         * plugins are synchronous). Virtual modules that the prelude already bakes into the
         * global scope resolve to an empty module: by the time plugin code runs,
         * `cheerio` / `crypto-js` / … are already globals *and* require-able via the shim,
         * so a plugin that also does `require('crypto-js')` still works.
         */
        private fun moduleLoader(): com.dokar.quickjs.ModuleLoader =
            com.dokar.quickjs.moduleLoader {
                normalize { specifier, referrer ->
                    when {
                        specifier.startsWith(".") -> specifier
                        specifier.startsWith("/") -> specifier
                        else -> PRELUDE_MODULES[specifier] ?: specifier
                    }
                }
                load { specifier ->
                    if (specifier == "__gy_empty__") {
                        com.dokar.quickjs.ModuleContent.Source("export default {};")
                    } else {
                        throw com.dokar.quickjs.QuickJsException("无法加载模块: $specifier")
                    }
                }
            }

        private val PRELUDE_MODULES = mapOf(
            "cheerio" to "__gy_empty__",
            "crypto-js" to "__gy_empty__",
            "dayjs" to "__gy_empty__",
            "qs" to "__gy_empty__",
            "he" to "__gy_empty__",
            "big-integer" to "__gy_empty__",
            "axios" to "__gy_empty__",
        )

        /** 15s per single plugin call, per spec. */
        const val CALL_TIMEOUT_MS = 15_000L

        /** QuickJS heap cap: 48 MiB is generous for cheerio+html parsing, still bounded. */
        const val MEMORY_LIMIT_BYTES = 48L * 1024 * 1024

        /** 1 MiB of JS stack — enough for deep recursion in cheerio selectors. */
        const val MAX_STACK_BYTES = 1024L * 1024

        private const val EVAL_TIMEOUT_MS = CALL_TIMEOUT_MS

        private fun quoteJsString(s: String): String = buildString {
            append('"')
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
    }
}

class PluginRuntimeException(message: String) : Exception(message) {
    /** Thrown when the plugin simply does not implement the requested MusicFree function. */
    class Missing(val fn: String, message: String) : Exception(message)
}
