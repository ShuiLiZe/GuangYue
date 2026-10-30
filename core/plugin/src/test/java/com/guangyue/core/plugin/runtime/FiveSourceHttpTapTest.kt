package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchParser
import com.guangyue.core.plugin.SearchType
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 0.0.9 a 段取证（task-3，five-source）：**在宿主桥里装一个 HTTP 探针**。
 *
 * 目的：`FiveSourceMediaProbeTest` 只能看到「插件最后抛了什么」，看不到「插件到底收到了什么」。
 * 本类把传给 PluginRuntime 的 OkHttpClient 换成带拦截器的客户端，逐条记录
 * 请求方法 / URL / 请求头（含 Range、Referer、Cookie）/ 响应码 / 耗时 /
 * peek 到的实际字节数 / Content-Type / Content-Length / 前 16 字节 hex。
 *
 * 只读产物：`docs/ui-0.0.9/a/five-source-raw/tap-<platform>.log`。
 * 不修改任何既有生产代码，不修改 RealPluginHealthTest / PluginUsableTest。
 */
class FiveSourceHttpTapTest {

    @Test
    fun tap() {
        val quickJs = runCatching {
            com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
        }.isSuccess
        assumeTrue("当前 JVM 没有 QuickJS natives，跳过", quickJs)

        val prelude = HealthCache.preludeText()
        val outDir = TapPaths.rawDir
        outDir.mkdirs()

        for (t in targets()) {
            val file = File(TapPaths.sourceDir, t.fileName)
            if (!file.isFile) {
                File(outDir, "tap-${t.platform}.log").writeText("## SKIP 源码不在缓存里：${file.absolutePath}\n")
                continue
            }
            val code = file.readText()
            val tap = ArrayList<String>()
            tap += "# HTTP tap · ${t.platform} ${t.name} ${t.version}"
            tap += "# 时间：${java.util.Date()}"
            tap += "# 源码：${file.absolutePath} (${code.length} chars)"
            tap += "# prelude: ${prelude.length} chars"
            tap += ""
            tap += "## 请求/响应逐条"

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val req = chain.request()
                    val t0 = System.nanoTime()
                    val resp = runCatching { chain.proceed(req) }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    if (resp.isFailure) {
                        tap += "TAP ${req.method} EXC ${ms}ms ${req.url}"
                        tap += "    ${resp.exceptionOrNull()?.javaClass?.name}: ${resp.exceptionOrNull()?.message}"
                        tap += "    req-headers: ${headerDump(req)}"
                        throw resp.exceptionOrNull()!!
                    }
                    val r = resp.getOrThrow()
                    val peek = runCatching { r.peekBody(64L * 1024).bytes() }.getOrDefault(ByteArray(0))
                    tap += "TAP ${req.method} ${r.code} ${ms}ms bytes=${peek.size} " +
                        "cl=${r.header("Content-Length")} ct=${r.header("Content-Type")} " +
                        "ce=${r.header("Content-Encoding")} range=${req.header("Range")} ${req.url}"
                    tap += "    req-headers: ${headerDump(req)}"
                    if (peek.isNotEmpty()) {
                        tap += "    head16: " + peek.take(16).joinToString("") { b -> "%02x".format(b) }
                        val ascii = String(peek.copyOfRange(0, minOf(120, peek.size)), Charsets.UTF_8)
                            .replace(Regex("[\\r\\n\\t]"), " ")
                        tap += "    head120: " + ascii
                    }
                    r
                }
                .build()

            val rt = PluginRuntime(
                meta = PluginMeta(
                    id = t.platform, platform = t.platform, name = t.name,
                    version = t.version, source = "tap://${t.platform}"
                ),
                code = code,
                http = client,
                preludeProvider = TapPrelude(prelude)
            )
            val logs = ArrayList<String>()
            rt.onLog = { lvl, msg -> logs += "$lvl: $msg" }
            tap += ""
            tap += "## 调用链"
            try {
                rt.install()
                tap += "install: OK"
                val raw = rt.call("search", JsonPrimitive("周杰伦"), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC))
                val parsed = SearchParser.parse(raw, t.platform, SearchType.MUSIC)
                tap += "search: items=${parsed.items.size} first=${parsed.items.firstOrNull()?.title}"
                val first = parsed.items.firstOrNull()
                tap += "first-raw: ${first?.raw ?: first?.toJson()}"
                if (first != null) {
                    val payload = first.raw ?: first.toJson()
                    for (q in listOf("standard", "high", "320k", "flac")) {
                        tap += ""
                        tap += "---- getMediaSource($q) ----"
                        val before = tap.size
                        try {
                            val ms = rt.call("getMediaSource", payload, JsonPrimitive(q))
                            val src = SearchParser.mediaSource(ms)
                            tap += "RESULT ok=${src != null} url=${src?.url}"
                            tap += "raw=$ms"
                        } catch (e: Throwable) {
                            tap += "RESULT FAIL ${e.javaClass.name}: ${e.message}"
                            tap += rawText(e)
                        }
                        if (tap.size == before) tap += "(无输出)"
                    }
                }
            } catch (e: Throwable) {
                tap += "FAIL(stage) ${e.javaClass.name}: ${e.message}"
                tap += rawText(e)
            } finally {
                runCatching { rt.close() }
            }
            tap += ""
            tap += "## console 记录（${logs.size} 条）"
            logs.forEach { tap += "  $it" }
            File(outDir, "tap-${t.platform}.log").writeText(tap.joinToString("\n") + "\n")
            println("[tap] ${t.platform} written: tap-${t.platform}.log (${tap.size} lines)")
        }
    }

    private fun headerDump(req: okhttp3.Request): String =
        req.headers.names().joinToString("; ") { n -> "$n=${req.header(n)}" }

    private fun rawText(t: Throwable): String {
        val d = (t as? PluginRuntimeException)?.detailText
        if (!d.isNullOrBlank()) return d
        val sb = StringBuilder()
        sb.append(t.javaClass.name).append(": ").append(t.message).append('\n')
        t.stackTrace.take(15).forEach { sb.append("    at ").append(it).append('\n') }
        return sb.toString()
    }

    private data class Target(val name: String, val version: String, val platform: String, val fileName: String)

    private fun targets() = listOf(
        Target("酷狗音乐", "1.9.24", "kugou", "kugou-source.plugin.v1.9.24.js"),
        Target("酷我音乐", "1.9.24", "kuwo", "kuwo-source.plugin.v1.9.24.js"),
        Target("QQ音乐", "1.9.26", "qq", "qq-source.plugin.v1.9.26.js"),
        Target("汽水音乐", "1.9.23", "qishui", "qishui-source.plugin.v1.9.23.js"),
        Target("网易云音乐", "1.9.27", "netease", "netease-source.plugin.v1.9.27.js"),
    )

    private class TapPrelude(private val text: String) : PreludeProvider(tapCtx()) {
        override fun code(): String = text
    }

    private object TapPaths {
        val root: File by lazy {
            listOf(
                File(System.getProperty("guangyue.rootDir") ?: ".", "docs/ui-0.0.9/a"),
                File("../docs/ui-0.0.9/a"),
                File("../../docs/ui-0.0.9/a"),
                File("docs/ui-0.0.9/a"),
            ).firstOrNull { it.isDirectory } ?: File("docs/ui-0.0.9/a").apply { mkdirs() }
        }
        val rawDir: File get() = File(root, "five-source-raw")
        val sourceDir: File by lazy {
            val marker = "kugou-source.plugin.v1.9.24.js"
            val cands = listOf(
                File("../build/plugin-cache-0.0.4"),
                File("build/plugin-cache-0.0.4"),
                File("../../build/plugin-cache-0.0.4"),
                File(System.getProperty("guangyue.rootDir") ?: ".", "build/plugin-cache-0.0.4"),
            )
            cands.firstOrNull { File(it, marker).isFile } ?: cands.last()
        }
    }

    companion object {
        @JvmStatic
        fun tapCtx(): android.content.Context = runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val f = unsafeClass.getDeclaredField("theUnsafe")
            f.isAccessible = true
            val unsafe = f.get(null) as sun.misc.Unsafe
            unsafe.allocateInstance(android.content.ContextWrapper::class.java) as android.content.Context
        }.getOrElse { throw IllegalStateException("无法构造测试用 Context", it) }
    }
}