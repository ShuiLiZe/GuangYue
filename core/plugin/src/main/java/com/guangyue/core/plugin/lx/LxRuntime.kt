package com.guangyue.core.plugin.lx

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import com.guangyue.core.plugin.Json2
import com.guangyue.core.plugin.asArr
import com.guangyue.core.plugin.asBool
import com.guangyue.core.plugin.asLong
import com.guangyue.core.plugin.asObj
import com.guangyue.core.plugin.asStr
import com.guangyue.core.plugin.di.PlaybackHttp
import com.guangyue.core.plugin.runtime.PreludeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 洛雪（LX Music）自定义源的运行环境。
 *
 * 与 [com.guangyue.core.plugin.runtime.PluginRuntime] 是**两套独立**的宿主：
 * MusicFree 插件导出 `module.exports`，用 `__gy_boot` 包装成 CommonJS 工厂；
 * 洛雪脚本不导出任何东西，它只跟 `globalThis.lx` 打交道 —— 注册 `request` 事件、
 * 用 `lx.send(EVENT_NAMES.inited, {sources})` 上报平台、用 `lx.request` 发 HTTP。
 * 所以这里装一份 `lx` 外壳（[LX_SHIM]），执行脚本，再通过 `__lx_call` 触发取链。
 *
 * 为什么 `lx.request` 是**同步回调**：QuickJS 里没有事件循环，无法在两次 evaluate 之间
 * 派发网络回调。洛雪桌面版把回调排到 Node 的事件循环上；在广乐里改成「宿主函数内同步
 * 阻塞发完请求，回来立刻调 callback」，语义仍是 `callback(err, response)`，脚本里的
 * `new Promise(...)` 会同步 resolve，剩下的 `.then` 链由宿主推微任务队列（[drivePending]）。
 * 这套机制和 MusicFree 插件的 async 支持完全一致，已在 0.0.7/0.0.8 里验证过。
 */
class LxRuntime(
    val source: LxSource,
    private val code: String,
    @PlaybackHttp private val http: OkHttpClient,
    private val preludeProvider: PreludeProvider,
) {
    @Volatile private var qjs: QuickJs? = null
    private var installed = false
    private val drainLatch = AtomicBoolean(false)

    var onLog: ((level: String, message: String) -> Unit)? = null
    var onHttp: ((method: String, url: String, status: Int, ms: Long) -> Unit)? = null

    /** 脚本 `inited` 时上报的源键（tx/wy/…）；装完才有效。 */
    var platforms: List<String> = emptyList()
        private set

    val isAlive: Boolean get() = qjs?.isClosed == false

    @Synchronized
    fun install() {
        if (installed) return
        val runtime = QuickJs.create(Dispatchers.Default, moduleLoader())
        try {
            runtime.memoryLimit = MEMORY_LIMIT_BYTES
            runtime.maxStackSize = MAX_STACK_BYTES
            runtime.evaluationTimeoutMillis = EVAL_TIMEOUT_MS

            runtime.function("__lx_http") { args ->
                val payload = args.getOrNull(0)?.let(::asBridgeText) ?: "{}"
                performHttp(payload)
            }
            runtime.function("__lx_log") { args ->
                val level = args.getOrNull(0)?.let(::asBridgeText) ?: "log"
                val msg = args.getOrNull(1)?.let(::asBridgeText) ?: ""
                onLog?.invoke(level, msg)
                runCatching { Log.d(TAG, "[${source.displayName}] $level: $msg") }
                ""
            }
            runtime.function("__lx_md5") { args ->
                val text = args.getOrNull(0)?.let(::asBridgeText) ?: ""
                md5Hex(text)
            }
            runtime.function("__lx_drainOneJob") { _ ->
                if (drainOneJob(runtime)) 1 else 0
            }

            step("lx-shim.js") { exec(runtime, LX_SHIM, "lx-shim.js") }
            if (step("lx-probe.js") { readRaw(runtime, "typeof globalThis.lx", "lx-probe.js") }
                != "object"
            ) {
                throw LxRuntimeException("洛雪宿主外壳没装好：typeof lx 不是 object")
            }

            // 部分洛雪源会用到 crypto/cheerio 之类的 prelude 库（这份不需要，但装上无害）。
            // 注意：prelude 是 esbuild 的 IIFE，末尾会 `globalThis.__GY_PRELUDE__ = …`；它不认识
            // `lx`，所以必须排在 shim 之后、source 之前，且不能覆盖 `globalThis.lx`。
            step("prelude.js") { exec(runtime, preludeProvider.code(), "prelude.js") }
            if (step("lx-probe2.js") { readRaw(runtime, "typeof globalThis.lx", "lx-probe2.js") }
                != "object"
            ) {
                throw LxRuntimeException("prelude 覆盖了 globalThis.lx，洛雪外壳失效")
            }
            step("source.js") { exec(runtime, code, "source.js") }

            step("dispatcher.js") { exec(runtime, LX_DISPATCHER, "dispatcher.js") }

            val inited = step("init-probe.js") {
                // 脚本可能只在回调/微任务里 inited，先跑一轮事件循环再读。
                runCatching { read(runtime, "__lx_pump(80)", "init-pump.js") }
                read(runtime, "JSON.stringify(globalThis.__lx_inited || null)", "init-probe.js")
            }
            platforms = parsePlatforms(inited)
            if (platforms.isEmpty()) {
                throw LxRuntimeException("脚本没有上报任何音源（lx.send(inited) 缺失或为空）")
            }
            if (step("handler-probe.js") {
                    readRaw(runtime, "typeof globalThis.__lx_handler", "handler-probe.js")
                } != "function"
            ) {
                throw LxRuntimeException("脚本没有注册 request 事件处理器")
            }
            qjs = runtime
            installed = true
        } catch (t: Throwable) {
            runCatching { runtime.close() }
            throw asRuntimeException(t)
        }
    }

    /**
     * 取一条播放链接。
     *
     * @param platform 洛雪源键（tx/wy/kg/kw/mg）
     * @param quality 音质档（128k/320k/flac/flac24bit）
     * @param musicInfo 洛雪协议要求的曲目对象；键名各源不同（songmid/hash/copyrightId…）
     * @return 直链 URL
     * @throws LxRuntimeException 源拒绝或超时
     */
    fun musicUrl(platform: String, quality: String, musicInfo: JsonObject): String {
        val runtime = qjs ?: throw LxRuntimeException("运行时未安装")
        val payload = JsonObject(
            mapOf(
                "source" to JsonPrimitive(platform),
                "action" to JsonPrimitive("musicUrl"),
                "info" to JsonObject(
                    mapOf(
                        "type" to JsonPrimitive(quality),
                        "musicInfo" to musicInfo,
                    ),
                ),
            ),
        )
        val argsJson = JsonArray(listOf(payload)).toString()
        val expr = "__lx_call(" + quoteJsString(argsJson) + ")"
        val raw = withWatchdog(runtime) {
            var text = read(runtime, "JSON.parse(" + expr + ")", "call.js")
            if (isPending(text)) text = drivePending(runtime)
            if (text.isBlank() || text == "null") "{\"ok\":false,\"error\":\"empty result\"}" else text
        }
        val obj = Json2.parse(raw).asObj() ?: throw LxRuntimeException("返回不是对象")
        if (obj["ok"].asBool() == true) {
            val data = obj["data"].asStr() ?: return ""
            return data.trim().trim('"')
        }
        if (obj["missing"].asBool() == true) {
            throw LxRuntimeException("脚本不支持 musicUrl")
        }
        throw LxRuntimeException(obj["error"].asStr() ?: "取链失败")
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
        val req = Json2.parse(payloadJson).asObj()
            ?: return httpError("bad request payload", started)
        val method = (req["method"].asStr() ?: "GET").uppercase()
        val url = req["url"].asStr() ?: return httpError("missing url", started)
        val timeoutMs = req["timeout"].asLong()?.takeIf { it > 0 } ?: 0L
        val body = req["body"].asStr()

        val builder = Request.Builder().url(url).header("User-Agent", UA)
        req["headers"].asObj()?.forEach { (k, v) ->
            v.asStr()?.let { runCatching { builder.header(k, it) } }
        }
        // 洛雪源会自己传 Accept-Encoding（有的要原始字节自己解），没传就交给 OkHttp 透明解压。
        val explicitEncoding = req["headers"].asObj()?.entries
            ?.firstOrNull { it.key.equals("accept-encoding", ignoreCase = true) }
            ?.value?.asStr()
        if (explicitEncoding.isNullOrBlank()) builder.header("Accept-Encoding", "gzip")

        val client = http.newBuilder()
            .connectTimeout(if (timeoutMs > 0) timeoutMs else 20_000L, TimeUnit.MILLISECONDS)
            .readTimeout(if (timeoutMs > 0) timeoutMs else 30_000L, TimeUnit.MILLISECONDS)
            .build()

        val request = try {
            if (method == "GET" || method == "HEAD") {
                builder.method(method, null).build()
            } else {
                val contentType = req["headers"].asObj()?.entries
                    ?.firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                    ?.value?.asStr() ?: "application/x-www-form-urlencoded; charset=utf-8"
                builder.method(method, (body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
                    .build()
            }
        } catch (e: IllegalArgumentException) {
            return httpError("bad url: ${e.message}", started)
        }

        return try {
            client.newCall(request).execute().use { resp ->
                val rawBytes = resp.body?.bytes() ?: ByteArray(0)
                val bytes = inflateIfNeeded(resp.headers["Content-Encoding"], rawBytes)
                onHttp?.invoke(method, url, resp.code, System.currentTimeMillis() - started)
                buildString {
                    append("{\"ok\":true,\"statusCode\":").append(resp.code)
                    append(",\"statusMessage\":").append(JsonPrimitive(resp.message))
                    append(",\"url\":").append(
                        JsonPrimitive(resp.request.url.toString()),
                    )
                    append(",\"headers\":{")
                    var first = true
                    for (name in resp.headers.names()) {
                        if (name.equals("Content-Encoding", true)) continue
                        if (!first) append(',')
                        first = false
                        append(JsonPrimitive(name)).append(':')
                            .append(JsonPrimitive(resp.headers.values(name).joinToString(", ")))
                    }
                    append("},\"body\":").append(JsonPrimitive(bodyText(bytes, resp.header("Content-Type"))))
                    append(",\"length\":").append(bytes.size)
                    append('}')
                }
            }
        } catch (e: IOException) {
            onHttp?.invoke(method, url, -1, System.currentTimeMillis() - started)
            httpError(e.message ?: "network error", started)
        } catch (e: Throwable) {
            httpError(e.message ?: "network error", started)
        }
    }

    /**
     * 二进制安全地把响应体交给脚本。
     *
     * 洛雪脚本里的 `toBytes(body)` 对字符串是逐字符 `charCodeAt` 取低 8 位，所以
     * **二进制响应必须给 latin-1 字符串**（每个字符就是一个字节），否则 Range 探测的
     * 魔数校验（fLaC / ID3 / MP3 帧头）与体积反推全错。而 JSON API 必须给 UTF-8 文本，
     * 否则中文标题会变成乱码、搜索型通道的标题比对不中。判据就是「字节是否严格合法
     * UTF-8」：64 字节的音频头里 0xFF/0x80 这些字节本身不可能是合法 UTF-8。
     */
    private fun bodyText(bytes: ByteArray, contentType: String?): String {
        if (bytes.isEmpty()) return ""
        val ct = contentType?.lowercase() ?: ""
        val textualHint = ct.isEmpty() || ct.contains("json") || ct.contains("text") ||
            ct.contains("html") || ct.contains("xml") || ct.contains("javascript")
        val decoded = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        val validUtf8 = decoded != null && !decoded.contains('\uFFFD')
        return if (validUtf8 && textualHint) decoded!! else String(bytes, Charsets.ISO_8859_1)
    }

    private fun inflateIfNeeded(encoding: String?, bytes: ByteArray): ByteArray {
        if (encoding.isNullOrBlank() || bytes.isEmpty()) return bytes
        val enc = encoding.lowercase()
        if (enc.contains("identity")) return bytes
        return try {
            val input = when {
                enc.contains("gzip") -> java.util.zip.GZIPInputStream(bytes.inputStream())
                enc.contains("deflate") -> java.util.zip.InflaterInputStream(bytes.inputStream())
                else -> return bytes
            }
            input.use { it.readBytes() }
        } catch (e: Throwable) {
            bytes
        }
    }

    private fun httpError(message: String, started: Long): String =
        "{\"ok\":false,\"error\":" + JsonPrimitive(message) +
            ",\"statusCode\":-1,\"ms\":" + (System.currentTimeMillis() - started) + "}"

    private fun withWatchdog(runtime: QuickJs, block: () -> String): String {
        val self = Thread.currentThread()
        val watchdog = Thread {
            runCatching {
                Thread.sleep(CALL_TIMEOUT_MS)
                runCatching { runtime.interruptEvaluation() }
                self.interrupt()
            }
        }.apply { isDaemon = true; start() }
        return try {
            block()
        } finally {
            watchdog.interrupt()
        }
    }

    private fun exec(runtime: QuickJs, source: String, filename: String) {
        runtime.evaluateBlocking(source + "\n;''", filename)
    }

    private fun read(runtime: QuickJs, jsExpr: String, filename: String): String {
        exec(
            runtime,
            "try { globalThis.__lx_out = JSON.stringify(($jsExpr)); globalThis.__lx_err = null } " +
                "catch (e) { globalThis.__lx_out = null; globalThis.__lx_err = String(e && e.stack || e) }",
            filename,
        )
        val text = runtime.evaluateBlocking(
            "String(globalThis.__lx_err == null ? globalThis.__lx_out : 'ERR:' + globalThis.__lx_err)",
        )
        // JS 侧 JSON.stringify 出来的本来就是一段 JSON 文本，外层又被 JSON.stringify 包了一层，
        // 这里把那一层剥掉：字符串字面量（以 \" 开头）按 JSON 解回原文本。
        if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return Json2.parse(text)?.let { el -> (el as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: text
        }
        return text
    }

    private fun isPending(text: String): Boolean = text.contains("\"pending\":true")

    /**
     * 只取原始值、不做二次反转义：给 `typeof globalThis.lx` 这类探针用。
     *
     * [read] 会把字符串结果按 JSON 解一层（脚本常写 `JSON.stringify(x)` 拿文本），
     * 用它读 `typeof` 会把 `"object"` 剥成 `object`。
     */
    private fun readRaw(runtime: QuickJs, jsExpr: String, filename: String): String {
        exec(
            runtime,
            "try { globalThis.__lx_out = String(($jsExpr)); globalThis.__lx_err = null } " +
                "catch (e) { globalThis.__lx_out = null; globalThis.__lx_err = String(e && e.stack || e) }",
            filename,
        )
        return runtime.evaluateBlocking(
            "String(globalThis.__lx_err == null ? globalThis.__lx_out : 'ERR:' + globalThis.__lx_err)",
        )
    }

    private fun drivePending(runtime: QuickJs): String {
        val deadline = System.nanoTime() + PENDING_BUDGET_NANOS
        while (System.nanoTime() < deadline) {
            val state = runCatching { read(runtime, "__lx_pump(1200)", "pump.js") }.getOrNull() ?: break
            val obj = Json2.parse(state).asObj() ?: break
            val result = obj["result"].asStr()
            if (obj["pending"].asBool() != true && result != null) return result
        }
        throw LxRuntimeException(
            "取链源「${source.displayName}」没有结算（超过 ${PENDING_BUDGET_NANOS / 1_000_000_000}s）",
        )
    }

    private fun drainOneJob(runtime: QuickJs): Boolean = runCatching {
        runtime.withNativeContext { native ->
            PendingJobDrainer.next(runtime, native.contextAddress, native.runtimeAddress)
        }
    }.getOrDefault(false)

    private fun parsePlatforms(initedJson: String): List<String> {
        if (initedJson.isBlank() || initedJson == "null" || initedJson.startsWith("ERR:")) {
            return emptyList()
        }
        val root = Json2.parse(initedJson) ?: return emptyList()
        // 脚本有时上报 `{status, sources:{…}}`，有时直接 `{tx:{…}}`。
        val obj = root.asObj() ?: return emptyList()
        val sources = obj["sources"].asObj() ?: obj
        return LxPlatforms.ALL.filter { key ->
            sources.containsKey(key) || sources.keys.any { it.equals(key, ignoreCase = true) }
        }
    }

    private fun <T> step(name: String, block: () -> T): T = try {
        block()
    } catch (e: LxRuntimeException) {
        throw e
    } catch (t: Throwable) {
        throw LxRuntimeException("[$name] ${t.message ?: t::class.simpleName}", original = t)
    }

    private fun QuickJs.evaluateBlocking(source: String, filename: String = "inline.js"): String =
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) { evaluate<String>(source, filename) } ?: ""
        }

    private fun asRuntimeException(t: Throwable): LxRuntimeException = when (t) {
        is LxRuntimeException -> t
        is InterruptedException -> LxRuntimeException(
            "取链源执行超时（${CALL_TIMEOUT_MS / 1000}s），已中断",
            original = t,
        )
        else -> LxRuntimeException(t.message ?: t::class.simpleName ?: "lx error", original = t)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asBridgeText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

    private fun md5Hex(text: String): String = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /**
     * 反射接出 quickjs-kt 私有的 `executePendingJob` —— 只能从 binding 回调里调用，
     * 见 `PluginRuntime.drainJobs` 的注释（从普通线程直接调会卡在 futex 上）。
     */
    private object PendingJobDrainer {
        private val method: java.lang.reflect.Method? = runCatching {
            QuickJs::class.java.getDeclaredMethod(
                "executePendingJob",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        }.getOrNull()

        fun next(runtime: QuickJs, contextAddress: Long, runtimeAddress: Long): Boolean =
            runCatching {
                method?.invoke(runtime, contextAddress, runtimeAddress) as? Boolean ?: false
            }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "GuangYueLx"
        const val UA = "GuangYue/1.0 (Android)"
        private const val MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
        private const val MAX_STACK_BYTES = 2L * 1024 * 1024

        /** 五源合一这类源一次取链要串好几十个 HTTP（竞速 + 接力），30s 不够。 */
        const val CALL_TIMEOUT_MS = 45_000L
        private const val EVAL_TIMEOUT_MS = CALL_TIMEOUT_MS
        private val PENDING_BUDGET_NANOS = 40_000_000_000L

        private val PRELUDE_MODULES = mapOf(
            "crypto-js" to "__lx_empty__",
            "cheerio" to "__lx_empty__",
            "dayjs" to "__lx_empty__",
            "qs" to "__lx_empty__",
            "he" to "__lx_empty__",
            "big-integer" to "__lx_empty__",
            "axios" to "__lx_empty__",
            "crypto" to "__lx_empty__",
        )

        private fun moduleLoader(): com.dokar.quickjs.ModuleLoader =
            com.dokar.quickjs.moduleLoader {
                normalize { specifier, _ -> PRELUDE_MODULES[specifier] ?: specifier }
                load { specifier ->
                    if (specifier == "__lx_empty__") {
                        com.dokar.quickjs.ModuleContent.Source("export default {};")
                    } else {
                        throw com.dokar.quickjs.QuickJsException("无法加载模块: $specifier")
                    }
                }
            }

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

        /**
         * `globalThis.lx` 外壳。
         *
         * 只实现洛雪桌面版文档里自定义源真正会用到的那一层：`version` / `env` /
         * `EVENT_NAMES` / `on` / `send` / `request` / `utils.crypto.md5` /
         * `utils.buffer.from|bufToString`。`request` 的响应字段名照洛雪原版
         * （`statusCode` / `headers` / `body` / `url`），因为脚本读的就是这些键。
         */
        private val LX_SHIM = """
(function () {
  'use strict';
  var G = globalThis;
  if (G.__LX_INSTALLED__) return;
  G.__LX_INSTALLED__ = true;

  var EVENT_NAMES = {
    request: 'request',
    inited: 'inited',
    updateAlert: 'updateAlert'
  };

  var handlers = {};
  G.__lx_handler = null;
  G.__lx_inited = null;

  function bytesOf(v) {
    if (!v) return new Uint8Array(0);
    if (v instanceof Uint8Array) return v;
    if (typeof v === 'string') {
      var out = new Uint8Array(v.length);
      for (var i = 0; i < v.length; i++) out[i] = v.charCodeAt(i) & 0xff;
      return out;
    }
    if (typeof v.length === 'number') {
      var arr = new Uint8Array(v.length);
      for (var j = 0; j < v.length; j++) arr[j] = v[j] & 0xff;
      return arr;
    }
    return new Uint8Array(0);
  }

  function textOf(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return s;
  }

  var lx = {
    version: '2.0.0',
    env: 'desktop',
    EVENT_NAMES: EVENT_NAMES,
    currentScriptInfo: { name: '', version: '', author: '', description: '', homepage: '' },
    on: function (eventName, handler) {
      if (typeof eventName !== 'string' || typeof handler !== 'function') return;
      handlers[eventName] = handler;
      if (eventName === EVENT_NAMES.request) G.__lx_handler = handler;
    },
    send: function (eventName, data) {
      if (eventName === EVENT_NAMES.inited) {
        G.__lx_inited = data || {};
      }
    },
    request: function (url, options, callback) {
      var opts = options || {};
      var payload = JSON.stringify({
        url: url,
        method: opts.method || 'GET',
        headers: opts.headers || {},
        timeout: opts.timeout || 0,
        body: (opts.body === undefined || opts.body === null) ? null : String(opts.body)
      });
      var text = G.__lx_http(payload);
      var res;
      try { res = JSON.parse(text); } catch (e) { res = { ok: false, error: 'bad host reply' }; }
      if (typeof callback !== 'function') return;
      if (!res || !res.ok) {
        callback(new Error((res && res.error) || 'request failed'), null, opts);
        return;
      }
      callback(null, {
        statusCode: res.statusCode,
        statusMessage: res.statusMessage,
        headers: res.headers || {},
        url: res.url || url,
        body: res.body === undefined ? '' : res.body,
        bytes: bytesOf(res.body)
      }, opts);
    },
    utils: {
      crypto: {
        md5: function (value) { return G.__lx_md5(String(value)); },
        randomBytes: function (size) {
          var out = new Uint8Array(size || 0);
          for (var i = 0; i < out.length; i++) out[i] = Math.floor(Math.random() * 256);
          return out;
        },
        aesEncrypt: function () { throw new Error('aesEncrypt not supported'); },
        rsaEncrypt: function () { throw new Error('rsaEncrypt not supported'); }
      },
      buffer: {
        from: function (v) { return bytesOf(v); },
        bufToString: function (buf, format) {
          var bytes = bytesOf(buf);
          if (format === 'hex') {
            var h = '';
            for (var i = 0; i < bytes.length; i++) {
              var x = bytes[i].toString(16);
              h += x.length < 2 ? '0' + x : x;
            }
            return h;
          }
          if (format === 'base64') {
            var b = '';
            for (var j = 0; j < bytes.length; j++) b += String.fromCharCode(bytes[j]);
            return G.btoa ? G.btoa(b) : b;
          }
          return textOf(bytes);
        }
      }
    }
  };

  G.lx = lx;
})();
"""

        /**
         * 取链调用入口。形状与 `HostBridge.CALL_DISPATCHER` 一致
         * （`{ok:true,data}` / `{ok:false,pending:true}` / `{ok:false,error}`），
         * 这样 Kotlin 侧可以复用同一套「顶层推 job」的驱动逻辑。
         */
        private val LX_DISPATCHER = """
(function () {
  'use strict';
  var G = globalThis;
  G.__lx_pending = null;

  function describe(e) {
    if (!e) return 'unknown error';
    if (typeof e === 'string') return e;
    return (e.message ? String(e.message) : String(e));
  }

  function settle(result) {
    if (!result || typeof result.then !== 'function') {
      return { ok: true, value: result };
    }
    var state = { done: false, ok: false, value: undefined, error: null };
    try {
      result.then(function (v) { state.done = true; state.ok = true; state.value = v; },
                  function (e) { state.done = true; state.ok = false; state.error = e; });
    } catch (e) {
      return { ok: false, error: 'broken Promise: ' + describe(e) };
    }
    G.__lx_pending = state;
    return { pending: true };
  }

  G.__lx_poll = function () {
    var state = G.__lx_pending;
    if (!state) {
      return { pending: false, result: JSON.stringify({ ok: false, error: 'pending call lost' }) };
    }
    if (!state.done) return { pending: true };
    G.__lx_pending = null;
    if (!state.ok) {
      var msg = describe(state.error);
      if (state.error && typeof state.error === 'object' && state.error.stack) {
        msg += '\n' + String(state.error.stack);
      }
      return { pending: false, result: JSON.stringify({ ok: false, error: msg }) };
    }
    var payload = JSON.stringify(state.value === undefined ? null : state.value);
    if (payload === undefined) payload = 'null';
    return { pending: false, result: JSON.stringify({ ok: true, data: payload }) };
  };

  G.__lx_pump = function (ms) {
    var started = (typeof Date !== 'undefined' && Date.now) ? Date.now() : 0;
    var advanced = 0;
    while (true) {
      if (G.__lx_pending && G.__lx_pending.done) break;
      if (started && Date.now() - started >= ms) break;
      if (typeof G.__lx_drainOneJob !== 'function') break;
      var moved = false;
      try { moved = G.__lx_drainOneJob() > 0; } catch (e) { break; }
      if (!moved) break;
      advanced++;
      if (advanced > 200000) break;
    }
    return G.__lx_poll();
  };

  G.__lx_call = function (argsJson) {
    var args;
    try { args = JSON.parse(argsJson); } catch (e) { args = []; }
    var handler = G.__lx_handler;
    if (typeof handler !== 'function') {
      return JSON.stringify({ ok: false, missing: true, error: 'source did not register request handler' });
    }
    var payload = (args && args.length) ? args[0] : {};
    if (payload.action && payload.action !== 'musicUrl') {
      return JSON.stringify({ ok: false, missing: true, error: 'action ' + payload.action + ' not supported' });
    }
    try {
      var raw = handler(payload);
      var s = settle(raw);
      if (s.pending) return JSON.stringify({ ok: false, pending: true });
      if (!s.ok) return JSON.stringify({ ok: false, error: s.error });
      var json = JSON.stringify(s.value === undefined ? null : s.value);
      if (json === undefined) json = 'null';
      return JSON.stringify({ ok: true, data: json });
    } catch (e) {
      return JSON.stringify({
        ok: false,
        error: describe(e),
        stack: e && e.stack ? String(e.stack) : ''
      });
    }
  };
})();
"""
    }
}

/** 取链源运行/加载失败。`original` 保留原始堆栈供「详情」展开。 */
class LxRuntimeException(
    message: String,
    val original: Throwable? = null,
) : Exception(message, original)