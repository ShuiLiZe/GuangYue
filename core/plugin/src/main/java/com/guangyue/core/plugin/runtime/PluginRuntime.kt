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
import java.util.concurrent.atomic.AtomicBoolean

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

    private val httpSeq = java.util.concurrent.atomic.AtomicInteger()
    private val httpDone = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val httpPool: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r, "gy-plugin-http").apply { isDaemon = true }
    }

    /** 见 [drainJobs]：保证 job 队列的推进永不递归重入。 */
    private val drainLatch = AtomicBoolean(false)



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

            runtime.function("__gy_drainJobs") { args ->
                // 驱动 QuickJS 的 job 队列（微任务）。`async function` 的续体挂在这里，
                // 必须把「这一轮有没有推进」如实返回：settle() 靠它决定继续 drain 还是收工。
                // 以前这里返回 args.size（恒 0），JS 侧第一轮就判无进展退出，多层 await 的
                // 插件于是永远停在 "Promise that has not settled"。
                if (drainJobs(runtime)) 1 else 0
            }

            runtime.function("__gy_drainOneJob") { args ->
                // 只推进一个 job。由 JS 侧 `G.__gy_pump(ms)` 在**自己的循环里**调用 ——
                // 此时本函数正运行在 QuickJS 的 binding 回调上下文里，运行时锁是递归持有的，
                // `JS_ExecutePendingJob` 才能安全推进。若从普通 Kotlin 线程直接调 native
                // （0.0.7 试过的方案），会永久卡在 `NonPI::MutexLockWithTimeout` 的 futex 上。
                if (drainOneJob(runtime)) 1 else 0
            }

            runtime.function("__gy_log") { args ->
                // Never `Any.toString()` here: quickjs-kt converts a returned TypeRef<*> by
                // calling toString(), so a hard cast to String throws for non-string JS values.
                val level = args.getOrNull(0)?.let(::asBridgeText) ?: "log"
                val msg = args.getOrNull(1)?.let(::asBridgeText) ?: ""
                onLog?.invoke(level, msg)
                runCatching { Log.d(TAG, "[${meta.platform}] $level: $msg") }
                ""
            }

            runtime.function("__gy_http") { args ->
                val payload = args.getOrNull(0)?.let(::asBridgeText) ?: "{}"
                performHttp(payload)
            }
            // M8：异步并发 HTTP。start 立即返回编号，请求在宿主线程池里跑；
            // take 一次取回一条已完成的 "id\n<响应 JSON>"；idle 在等待时让出几毫秒。
            if (asyncHttpEnabled) runtime.function("__gy_http_start") { args ->
                val payload = args.getOrNull(0)?.let(::asBridgeText) ?: "{}"
                val id = httpSeq.incrementAndGet()
                httpPool.execute {
                    val raw = runCatching { performHttp(payload) }
                        .getOrElse { netError(it.message ?: "network error", -1, System.currentTimeMillis(), "?") }
                    httpDone.add("$id\n$raw")
                }
                id.toString()
            }
            runtime.function("__gy_http_take") { _ -> httpDone.poll() ?: "" }
            runtime.function("__gy_idle") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong() ?: 5L
                try { Thread.sleep(ms.coerceIn(1L, 20L)) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                ""
            }
            // 0.0.8：TextDecoder 的非 UTF-8 分支（gbk/gb18030/big5）交给宿主，宿主才有 Charset。
            runtime.function("__gy_text_in") { args ->
                val b64 = args.getOrNull(0)?.let(::asBridgeText) ?: ""
                val codec = args.getOrNull(1)?.let(::asBridgeText) ?: "gbk"
                runCatching {
                    val bytes = java.util.Base64.getDecoder().decode(b64)
                    String(bytes, java.nio.charset.Charset.forName(codec))
                }.getOrElse { e -> "ERR:${e.message}" }
            }

            // 1) host bridge (CommonJS + axios + timers + console) – plain JS, no plugin code yet
            //    每一步都自己包 try/catch 并带上阶段名：0.0.1 时代所有阶段错误都只有一句
            //    `SyntaxError: expecting ','` + 一个指向别处的行号，完全无法定位是哪一段坏了。
            step("bridge.js") { exec(runtime, HostBridge.COMMONJS_SHIM, "bridge.js") }
            //    shim 是整条链的地基：装完立刻断言 `__gy_boot` / `__gy_require` 真的存在，
            //    否则后面的报错会伪装成「插件语法错误」，排查时会一路查错方向。
            val bootType = step("bridge-probe.js") { read(runtime, "typeof globalThis.__gy_boot", "bridge-probe.js") }
            if (bootType != "\"function\"") {
                throw PluginRuntimeException("宿主桥未就绪：typeof __gy_boot = $bootType")
            }

        // 2) the shared prelude bundle (cheerio / crypto-js / dayjs / qs / he / big-integer)
            //    prelude 是 esbuild 打包的 IIFE，结尾自带 `;`；[exec] 无条件把整段当脚本执行，
            //    不会再把它塞进任何实参列表里。
            step("prelude.js") { exec(runtime, preludeProvider.code(), "prelude.js") }
            // 3) the plugin itself, wrapped as a CommonJS module factory.
            //    `__gy_boot` 返回 boolean：先把它**赋给一个全局变量**（语句），再用 [read] 单独
            //    读一次。这样语句分号永远待在语句里，读值那一步只含单个表达式。
            step("plugin.js") {
                exec(
                    runtime,
                    "globalThis.__gy_ret = globalThis.__gy_boot(" + HostBridge.wrapPluginSource(code) + ");",
                    "plugin.js",
                )
                val booted = read(runtime, "globalThis.__gy_ret === true", "plugin.js:ret")
                if (booted != "true") throw PluginRuntimeException("插件加载返回 [$booted]")
            }
            // 4) the call dispatcher the host uses
            step("dispatcher.js") { exec(runtime, HostBridge.CALL_DISPATCHER, "dispatcher.js") }

            if (step("probe.js") { read(runtime, PROBE_EXPORTED, "probe.js") } != "true") {
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
                // dispatcher 已经返回 JSON **文本**，[read] 会再 JSON.stringify 一次把它包成
                // JSON 字符串字面量，所以这里要 `JSON.parse` 还原成原来的文本。
                var text = read(runtime, "JSON.parse(" + expr + ")", "call.js")
                // Promise 未结算时 dispatcher 返回 {"pending":true}，把结果留在 __gy_pending，
                // 由这里在**顶层**（不在任何 JS 回调里）逐 job 推进直到结算。
                //
                // 为什么还要单独线程 + Future 超时：真机上 `JS_ExecutePendingJob` 会卡在
                // QuickJS 运行时锁的 futex 上不返回（native 栈
                // `NonPI::MutexLockWithTimeout ← Java_com_dokar_quickjs_QuickJs_executePendingJob`），
                // 这种 native 阻塞既不在 `evaluate` 的字节码检查点里（`interruptEvaluation()`
                // 管不到），也无法用「每轮之间看 nanoTime」的预算打断。因此把推进会话放到
                // 独占线程上，主流程只用 `Future.get(超时)` 等它：等不到就如实报
                // 「异步结果没结算」，UI 立刻有明确提示，而不是空白。
                if (isPending(text)) text = drivePending(runtime)
                if (text == "null" || text.isEmpty()) "{\"ok\":false,\"error\":\"empty result\"}" else text
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

    fun probeFunctions(): Set<String> {
        val names = JsonArray(PluginFunctions.ALL.map { JsonPrimitive(it) }).toString()
        // 注意：`read()` 会把这个表达式的**结果**再 JSON.stringify 一次，所以这里必须是
        // 一个完整的 IIFE 表达式。0.0.3 拆分时丢了开头的 `(function(){` 与 `var out = [];`，
        // 表达式成了悬空片段 —— 解析失败被 runCatching 吞掉，probe 恒返回空集，
        // 于是所有插件都被判成「exports 里没有 search」。
        val expr = """
            (function(){
              var out = [];
              var names = ${names};
              for (var i = 0; i < names.length; i++) {
                if (typeof globalThis.__plugin[names[i]] === 'function') out.push(names[i]);
              }
              return out;
            })()
        """.trimIndent()

        val runtime = qjs ?: return emptySet()
        val raw = runCatching { read(runtime, expr, "probe-functions.js") }.getOrNull() ?: return emptySet()
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
        exec(runtime, PluginMetaProbe.SCRIPT, "meta-probe.js")
        // `__gy_meta_json` 自己已经返回 JSON **文本**；[read] 会再 stringify 一次把它变成
        // 「带引号的 JSON 字符串字面量」，所以这里用 JSON.parse 还原成原始文本。
        val raw = read(runtime, "JSON.parse(" + PluginMetaProbe.callSource(PluginFunctions.ALL) + ")", "meta.js")
        if (raw.startsWith("ERR:")) throw PluginRuntimeException("读取插件元信息失败：" + raw.removePrefix("ERR:").take(600))
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
        // M8：异步 HTTP 之后，关闭时可能还挂着没结算的 Promise / 未到期计时器 / 等响应的回调。
        // 直接 JS_FreeRuntime 会在 releaseRuntime 里 native 崩溃（JVM 实测 EXCEPTION_ACCESS_VIOLATION，
        // 咪咕榜单）。先清掉这些引用、把 job 队列推空，再释放。
        qjs?.let { rt ->
            runCatching {
                read(rt, "(function(){try{__gy_resetHttp();}catch(e){}try{__gy_resetTimers();}catch(e){}globalThis.__gy_pending=null;return 'ok';})()", "reset.js")
            }
            runCatching { read(rt, "__gy_pump(300)", "drain-on-close.js") }
        }
        runCatching { qjs?.close() }
        httpDone.clear()
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
        // 0.0.8：部分插件（DJ 类）自己传了 `Accept-Encoding: gzip`，并要拿原始字节自己解压。
        // OkHttp 默认透明解压，只有当调用方没显式设 Accept-Encoding 时才会解 —— 我们这里
        // 统一「显式声明 gzip/br/deflate → 自己解」，并保证返回给插件的 headers 里
        // Content-Encoding / Content-Length 与返回的 data 一致，避免插件拿到「已解压的字节
        // 却被告知是 gzip」或相反。
        val explicitEncoding = req["headers"].asObj()?.entries
            ?.firstOrNull { it.key.equals("accept-encoding", ignoreCase = true) }
            ?.value?.asStr()

        val builder = Request.Builder().url(url).header("User-Agent", UA)
        req["headers"].asObj()?.forEach { (k, v) ->
            v.asStr()?.let { runCatching { builder.header(k, it) } }
        }
        // 重定向由 OkHttp 默认跟随（GET 跨域也跟，最多 20 跳）；这里不再覆写，
        // 0.0.7 里 7 个源报 -1 的既有重定向也有 TLS/超时，见下面 httpClient 的超时设置。
        if (explicitEncoding.isNullOrBlank()) {
            // 让 OkHttp 负责解压（它会在解压后把 Content-Encoding / Content-Length 去掉）。
            builder.header("Accept-Encoding", "gzip")
        }

        val httpClient = http.newBuilder()
            .connectTimeout(if (timeoutMs > 0) timeoutMs else 20_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(if (timeoutMs > 0) timeoutMs else 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

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
                // M8：插件常用 `Range: bytes=0-0` 探测音频直链是否可用；服务器不认 Range 时会回整首歌，
                // 只读前 64KB 就够插件判断（状态码 / 魔数），不把几 MB 的音频拉进 JS。
                val hasRange = req["headers"].asObj()?.keys?.any { it.equals("range", ignoreCase = true) } == true
                val rawBytes = if (hasRange) {
                    resp.body?.source()?.let { src ->
                        src.request(RANGE_PROBE_BYTES)
                        src.buffer.readByteArray(minOf(src.buffer.size, RANGE_PROBE_BYTES))
                    } ?: ByteArray(0)
                } else {
                    resp.body?.bytes() ?: ByteArray(0)
                }
                // 手写解码：调用方自己声明了 Accept-Encoding 时，OkHttp 会把压缩字节原样给我们。
                val bytes = inflateIfNeeded(resp.headers["Content-Encoding"], rawBytes)
                val text = decode(resp.headers, bytes)
                val dataJson: String = when (responseType) {
                    "arraybuffer", "blob", "buffer" -> JsonPrimitive(
                        // 用 JDK 的 Base64：android.util.Base64 在 JVM 单测里是 not-mocked 桩，
                        // 会让所有 resultbuffer 插件在体检里假失败。java.util.Base64 在 Android(26+) 一样可用。
                        java.util.Base64.getEncoder().encodeToString(bytes),
                    ).toString()
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
                    append(",\"finalUrl\":").append(JsonPrimitive(resp.request.url.toString()))
                    append(",\"headers\":{")
                    var first = true
                    for (name in resp.headers.names()) {
                        // 已经解压过就不要再把 Content-Encoding / Content-Length 透给插件，
                        // 否则插件里的 zlib.gunzip 会二次解压报「invalid stored block lengths」。
                        if (name.equals("Content-Encoding", true) || name.equals("Content-Length", true)) continue
                        if (!first) append(',')
                        first = false
                        append(JsonPrimitive(name)).append(':').append(JsonPrimitive(resp.headers.values(name).joinToString(", ")))
                    }
                    append("},\"data\":").append(dataJson).append('}')
                }
            }
        } catch (e: IOException) {
            // 0.0.8 诊断：把真实失败原因和地址带出去，别只留一句 `status code -1`。
            runCatching { Log.w(TAG, "HTTP -1 ${method} ${url} => ${e.javaClass.simpleName}: ${e.message}") }
            netError(describeNetworkError(e), -1, started, url)
        } catch (e: Throwable) {
            netError(e.message ?: "network error", -1, started, url)
        }
    }

    /** 把 OkHttp 的 IOException 说人话：连不上 / 超时 / 证书 / 重定向 / DNS。 */
    private fun describeNetworkError(e: IOException): String {
        val reason = e.message ?: ""
        return when {
            e is java.net.UnknownHostException -> "域名解析失败：$reason"
            e is java.net.SocketTimeoutException -> "请求超时：$reason"
            e is javax.net.ssl.SSLException -> "HTTPS 证书/TLS 握手失败：$reason"
            e is java.net.ConnectException -> "连不上服务器：$reason"
            reason.contains("too many redirects", ignoreCase = true) -> "重定向次数过多：$reason"
            reason.contains("unwrap", ignoreCase = true) || reason.contains("unexpected end of stream", ignoreCase = true) ->
                "响应被截断（可能被防盗链/代理改写）：$reason"
            else -> reason.ifBlank { "网络错误（${e.javaClass.simpleName}）" }
        }
    }
    /** 按 `Content-Encoding` 解 gzip / deflate / br；解不开就原样返回（不因为解压失败丢数据）。 */
    private fun inflateIfNeeded(encoding: String?, bytes: ByteArray): ByteArray {
        if (encoding.isNullOrBlank() || bytes.isEmpty()) return bytes
        val enc = encoding.lowercase()
        if (enc.contains("identity")) return bytes
        return try {
            val input = when {
                enc.contains("gzip") -> java.util.zip.GZIPInputStream(bytes.inputStream())
                enc.contains("br") -> throw IllegalStateException("br")
                enc.contains("deflate") -> java.util.zip.InflaterInputStream(bytes.inputStream())
                else -> return bytes
            }
            input.use { it.readBytes() }
        } catch (e: Throwable) {
            // br 需要 brotli 解码器（未内置）；此时宁可把原始字节给插件，也不让请求整体失败。
            bytes
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

    /**
     * **求值原语一：执行脚本。**
     *
     * 直接求值 `source + "\n;''"`：不包任何函数调用、不拼括号，所以 [source] 里出现多少
     * 语句分号都无所谓（prelude 是 esbuild IIFE 自带 `;`、插件工厂本身、dispatcher 都是语句）。
     * 结尾的 `;''` 是一条空串表达式语句，保证整段脚本的**完成值一定是 JS string**。
     *
     * 这一点是 0.0.1 release 包翻车的根因：quickjs-kt 的 `evaluate<T>` 会对非 null 返回值做
     * 一次 `KType` 反射转换（`castValueOr` → `isInstance` → `TypeConverters`），R8 剥掉
     * kotlin-reflect 后直接抛
     * `No such type converter to convert 'boolean ... (Kotlin reflection is not available)'
     *  to 'java.lang.String'`。完成值固定成 string 后，Kotlin 侧只会拿到 `String`，
     * TypeConverter 永远不会被触发。
     */
    private fun exec(runtime: QuickJs, source: String, filename: String) {
        try {
            runtime.evaluateBlocking(source + "\n;''", filename)
        } catch (t: Throwable) {
            throw asRuntimeException(t)
        }
    }

    /**
     * **求值原语二：读一个表达式的值。**
     *
     * 第一步是一条**语句**：把表达式求值结果 `JSON.stringify` 进 `__gy_out`，异常进 `__gy_err`；
     * 第二步单独求值一个表达式，把结果压成字符串还给 Kotlin。两步都不拼括号，
     * 所以 [jsExpr] 里允许出现任意字符串字面量与嵌套调用。
     *
     * [jsExpr] **必须是单个表达式**，不能带语句分号 —— 它会被放进 `JSON.stringify((...))` 的
     * 括号里。要执行语句请用 [exec]，再把结果读出来。
     *
     * 返回：`undefined` → 字符串 `"undefined"`；JS 异常 → `ERR:<stack>`。
     *
     * 为什么要把「求值」和「读结果」放进**同一次** [exec]：QuickJS 的微任务（job 队列）只在
     * eval 期间被驱动。以前这里拆成两次 eval —— 第一次 `JSON.stringify(expr)`、第二次读
     * `__gy_out` —— 表达式若返回 Promise（大量 MusicFree 插件写成 `async function`，虽然
     * 立即 resolve），它的 `then` 回调要等**下一次** job 驱动才跑，而两次 eval 之间没人驱动，
     * 于是永远读到「Promise has not settled」。现在整条 `try/catch` 与 JSON.stringify 在同一个
     * 脚本里，Promise 结算后的微任务在这次 eval 内就能跑完（`settle()` 自己也会在
     * `__gy_call` 里驱动一次 job）。第二次 eval 只做一件事：把 `__gy_err / __gy_out`
     * 拼成字符串 —— 它是一段不含任何 Promise 的纯字符串运算，所以稳定。
     */
    private fun read(runtime: QuickJs, jsExpr: String, filename: String): String {
        exec(
            runtime,
            "try { globalThis.__gy_out = JSON.stringify(($jsExpr)); globalThis.__gy_err = null } " +
                "catch (e) { globalThis.__gy_out = null; globalThis.__gy_err = String(e && e.stack || e) }",
            filename,
        )
        return try {
            val result: String? = runtime.evaluateBlocking(
                "String(globalThis.__gy_err == null ? globalThis.__gy_out : 'ERR:' + globalThis.__gy_err)",
                filename,
            )
            result ?: ""
        } catch (t: Throwable) {
            throw asRuntimeException(t)
        }
    }

    /**
     * 带阶段名的 eval：异常信息前缀加上脚本名，最原始的 cause（含 JS 堆栈）留在 `cause` 里。
     *
     * 这一步是为了排障：`PluginRuntimeException.detailText` 会打印 message + 堆栈，
     * 只写「SyntaxError: expecting ','」时根本看不出是 shim / prelude / 插件 / dispatcher
     * 哪一段坏掉——0.0.1 与 0.0.2 都在这上面浪费过大量时间。
     */
    /**
     * 驱动 QuickJS 的 job 队列直到空。
     *
     * 为什么需要它：QuickJS 的 Promise 续体（`async function` 的 await 之后、`then` 回调）
     * 都排在 job 队列里，只能靠 `JS_ExecutePendingJob` 推进；quickjs-kt 没有把它暴露到公开
     * API（只在每次 `evaluate` 内部驱动），而 `__gy_call` 是**一次性**返回值，没有机会
     * 回到 JVM 再进一次 eval。所以这里直接走 native context 里的 `JS_ExecutePendingJob`。
     *
     * 用 JNI 反射而不是新加依赖：这个符号是同库里导出的 C 函数，quickjs-kt 的 so 一定带它。
     * 任何一步失败都静默降级（返回 false），调用方会照旧报「Promise 未结算」——不会伪装成功。
     */
    private fun drainJobs(runtime: QuickJs): Boolean {
        // **重入闩**：JS 侧 `settle()` 调 `__gy_drainJobs` → JVM 调 `executePendingJob` → 执行到的
        // 微任务里又可能再调 `__gy_drainJobs`。没有闩时它是**递归重入**：每层还能跑
        // MAX_PENDING_JOBS 个 job，真机上实测把线程钉在 `JS_ExecutePendingJob` 里 100% CPU 空转
        // 三分钟以上（0.0.7 装机后「搜周杰伦」永远不出结果的真因）。嵌套调用直接返回 false
        // 表示「外层已经在推队列了」，语义正确且立刻解除递归。
        if (!drainLatch.compareAndSet(false, true)) return false
        return try {
            var drained = false
            val deadline = System.nanoTime() + DRAIN_BUDGET_NANOS
            runtime.withNativeContext { native ->
                var guard = 0
                // 返回 true = 真的推进了一个 job；false = 队列已空（或出错），停下。
                // 双重闸门：job 条数上限 + 墙钟预算 —— 插件写出自我续期的微任务链时，
                // 预算到点就收工，让上层如实报「Promise 未结算」，而不是无限空转。
                while (guard++ < MAX_PENDING_JOBS &&
                    System.nanoTime() < deadline &&
                    PendingJobDrainer.next(runtime, native.contextAddress, native.runtimeAddress)
                ) {
                    drained = true
                }
            }
            drained
        } catch (t: Throwable) {
            false
        } finally {
            drainLatch.set(false)
        }
    }


    /** dispatcher 在 Promise 未结算时返回的就是这个形状（见 HostBridge.CALL_DISPATCHER）。 */
    private fun isPending(text: String): Boolean = text.contains("\"pending\":true")

    /**
     * 推进 Promise 队列直到结算，超过 [PENDING_BUDGET_NANOS] 如实报「没结算」。
     *
     * 这是 0.0.7 修的第三处根因。真机 trace 证明：`JS_ExecutePendingJob` **只能在 binding
     * 回调里**被调用。从普通线程直接调，`withNativeContext` 会先抢 QuickJS 的 jsMutex，
     * 然后 native 里的 `JS_LockRuntime` 又去抢同一把不可重入锁 → 线程永久卡在
     * `NonPI::MutexLockWithTimeout` 的 futex 上；watchdog 的 `interruptEvaluation()` 与
     * `Thread.interrupt()` 都救不了（它们只在 `evaluate` 的字节码检查点生效，管不到
     * host 回调里的 native 循环）。B站 / 清风DJ / 好听轻音乐「搜周杰伦」永远不出结果、
     * 也不报错，就是这么来的。
     *
     * 所以改成：JS 只**登记** Promise 状态（`G.__gy_pending`），Kotlin 在顶层反复发
     * **有界的** `evaluate("__gy_pump(ms)")`。JS 侧循环调 `__gy_drainOneJob()`（宿主注入，
     * 内部就是 executePendingJob，执行时天然处于 binding 回调上下文）推进队列，到时限或
     * 结算就返回；每轮之间回到 Kotlin 才检查总预算。既能持续推进，也不会在一次 native
     * 调用里跑成分钟级空转。
     */
    private fun drivePending(runtime: QuickJs): String {
        val deadline = System.nanoTime() + PENDING_BUDGET_NANOS
        while (System.nanoTime() < deadline) {
            val state = runCatching {
                read(runtime, "__gy_pump(1200)", "pump.js")
            }.getOrNull() ?: break
            val obj = Json2.parse(state).asObj() ?: break
            val result = obj["result"].asStr()
            if (obj["pending"].asBool() != true && result != null) return result
        }
        throw PluginRuntimeException(
            "插件返回的异步结果没结算（超过 ${PENDING_BUDGET_NANOS / 1_000_000_000}s）"
        )
    }

    /**
     * 推进一个 job；返回是否真的推进了。
     *
     * 只在 `__gy_pump` 的 JS 循环里被调用 —— 那是 binding 回调上下文，runtime 锁递归持有，
     * 所以能安全推进（见 [drivePending] 的说明）。[drainJobs] 保留给旧的 `__gy_drainJobs`
     * 回调（`G.__gy_pump` 之前的老路径），带重入闩。
     */
    private fun drainOneJob(runtime: QuickJs): Boolean = runCatching {
        runtime.withNativeContext { native ->
            PendingJobDrainer.next(runtime, native.contextAddress, native.runtimeAddress)
        }
    }.getOrDefault(false)

    /**
     * 把 quickjs-kt 私有的 `executePendingJob` native 方法接出来。
     *
     * quickjs-kt 没有公开「驱动 job 队列」的 API —— 它自己的 `evaluate` 内部会调这个 native，
     * 但插件调用是一次性返回值，没有机会再进一次 eval。`async function` 的续体恰恰排在
     * 这个队列里，所以必须手动推一下，否则所有声明成 async（哪怕同步返回）的插件函数
     * 都会报「Promise has not settled」。
     *
     * 反射失败就返回 false，调用方照旧报未结算 —— 不伪装成功。
     */
    private object PendingJobDrainer {
        private val method: java.lang.reflect.Method? = runCatching {
            QuickJs::class.java.getDeclaredMethod(
                "executePendingJob",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        }.getOrNull()

        fun next(runtime: QuickJs, contextAddress: Long, runtimeAddress: Long): Boolean = runCatching {
            method?.invoke(runtime, contextAddress, runtimeAddress) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private inline fun <T> step(name: String, block: () -> T): T = try {
        block()
    } catch (e: PluginRuntimeException) {
        throw e
    } catch (t: Throwable) {
        throw PluginRuntimeException("[$name] ${t.message ?: t::class.simpleName}", original = t)
    }

    /** `QuickJs.evaluate` is suspending; the runtime always runs on a worker thread. */
    private fun QuickJs.evaluateBlocking(source: String, filename: String = "inline.js"): String? =
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) { evaluate<String>(source, filename) }
        }

    private fun asRuntimeException(t: Throwable): PluginRuntimeException = when (t) {
        is PluginRuntimeException -> t
        is InterruptedException -> PluginRuntimeException(
            message = "插件执行超时（${CALL_TIMEOUT_MS / 1000}s），已中断",
            original = t,
        )
        else -> PluginRuntimeException(
            message = t.message ?: t::class.simpleName ?: "plugin error",
            original = t,
        )
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

        /** 「插件是否导出了对象」的探针表达式（单个表达式，走 [read]）。 */
        private const val PROBE_EXPORTED =
            "typeof globalThis.__plugin === 'object' && globalThis.__plugin !== null"

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

        /**
         * 单次插件调用预算。
         *
         * 原来写 15s，实测不够：B站 / 火龙DJ / DJ串烧集 / 蜻蜓FM / 皮狗DJ / 喜马拉雅
         * 这类插件一次 search 要串行打 5~15 个 HTTP 请求（QuickJS 里没有事件循环，
         * 全部同步阻塞），首屏冷启动经常 16~30s。15s 会把「慢」误判成「坏」。
         * 30s 只放宽预算，不改语义；真挂死的插件仍然会被中断。
         */
        const val CALL_TIMEOUT_MS = 30_000L

        /** M8：异步并发 HTTP 开关（排查用；关掉退回旧的同步串行桥）。 */
        @JvmStatic var asyncHttpEnabled: Boolean = System.getenv("GUANGYUE_SYNC_HTTP") != "true"
        private const val RANGE_PROBE_BYTES = 65_536L
        /** QuickJS heap cap: 48 MiB is generous for cheerio+html parsing, still bounded. */
        const val MEMORY_LIMIT_BYTES = 48L * 1024 * 1024

        /** 1 MiB of JS stack — enough for deep recursion in cheerio selectors. */
        const val MAX_STACK_BYTES = 1024L * 1024

        /**
         * 一次 `__gy_call` 里最多推进多少个微任务。插件里正常的 `async` 链只有个位数层级；
         * 给 10 万是为了容错，同时防止插件写出自我续接的死循环把调用线程钉死。
         */
        const val MAX_PENDING_JOBS = 100_000

        /**
         * 单次 [drainJobs] 的墙钟预算（1.5s）。
         *
         * 没有它的时候，插件里「一起微任务就再发一次 HTTP」的自我续期链会让
         * `JS_ExecutePendingJob` 一直在 native 里跑，JVM 线程 100% CPU 打转几分钟，
         * 30s watchdog 的 `Thread.interrupt()` 对 native 循环无效。给它一个上限，
         * 到点就收工，上层如实报「Promise 未结算」，用户看到的是明确错误而不是空转。
         */
        private val DRAIN_BUDGET_NANOS = 1_500_000_000L

    /** 顶层推 Promise 队列的总预算；到点如实报「异步结果没结算」。 */
    private val PENDING_BUDGET_NANOS = 25_000_000_000L

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

class PluginRuntimeException(
    message: String,
    /** 原始异常：类名 + message + 前 15 行堆栈，供「展开详情」原样展示。 */
    val original: Throwable? = null,
) : Exception(message, original) {
    /** 现场取证文本；没有原始异常时退回本异常的 message。 */
    val detailText: String
        get() = original?.let { ErrorDetail.of(it).format() }
            ?: ErrorDetail.of(this).format()

    /** Thrown when the plugin simply does not implement the requested MusicFree function. */
    class Missing(val fn: String, message: String) : Exception(message)

}
