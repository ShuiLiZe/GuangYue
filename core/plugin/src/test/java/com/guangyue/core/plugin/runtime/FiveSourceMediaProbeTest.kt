package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginMetaCodec
import com.guangyue.core.plugin.SearchParser
import com.guangyue.core.plugin.SearchType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 0.0.9 a 段：**只看这 5 个源**（酷狗 / 酷我 / QQ / 汽水 / 网易云）的离机取证。
 *
 * 与 [RealPluginHealthTest] / [PluginUsableTest] 的区别：
 *  - 只跑 5 个，跑完一定把**原始异常全文（含堆栈）+ 该插件这一轮发出的每一条 HTTP 记录
 *    + console.* 日志**落盘到 `docs/ui-0.0.9/a/five-source-raw/<platform>.log`；
 *  - `getMediaSource` 按多个音质逐个试，每次单独记一行，用来区分「取不到」是普遍失败还是
 *    「只有 standard 这一个音质失败」；
 *  - 结果（一行一个平台）追加到 `docs/ui-0.0.9/a/five-source-evidence.jsonl`，带 `phase`
 *    字段，便于 before/after 对比。
 *
 * 这是**取证**用例，不是回归用例：任何一步失败都只记录、不 fail 构建（同 0.0.8 的两份体检）。
 *
 * 源码一律走 `build/plugin-cache-0.0.4/`（已 gitignore），缺文件就 skip，不联网现下。
 */
class FiveSourceMediaProbeTest {

    @Test
    fun probe() {
        assumeTrue("当前 JVM 没有 QuickJS natives，跳过", quickJsAvailable)
        val phase = System.getProperty("gy.five.phase")
            ?: File(FiveSourcePaths.root, ".phase").takeIf { it.isFile }
                ?.readText()?.removePrefix("\uFEFF")?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv("GY_FIVE_PHASE")
            ?: "before"
        for (t in targets()) {
            val row = probeOne(t, phase)
            FiveSourceEvidence.append(row)
            println("[five] ${row.platform} ${row.name} ${row.version} search=${row.searchOk} items=${row.items} media=${row.mediaOk} verdict=${row.verdict} ${row.ms}ms")
        }
    }

    private data class Target(
        val name: String,
        val version: String,
        val platform: String,
        val fileName: String,
        val url: String,
    )

    private fun targets(): List<Target> = listOf(
        Target("酷狗音乐", "1.9.24", "kugou", "kugou-source.plugin.v1.9.24.js", URL_KUGOU),
        Target("酷我音乐", "1.9.24", "kuwo", "kuwo-source.plugin.v1.9.24.js", URL_KUWO),
        Target("QQ音乐", "1.9.26", "qq", "qq-source.plugin.v1.9.26.js", URL_QQ),
        Target("汽水音乐", "1.9.23", "qishui", "qishui-source.plugin.v1.9.23.js", URL_QISHUI),
        Target("网易云音乐", "1.9.27", "netease", "netease-source.plugin.v1.9.27.js", URL_NETEASE),
    )

    private fun probeOne(t: Target, phase: String): FiveSourceRow {
        val started = System.currentTimeMillis()
        val log = StringBuilder()
        log.append("# ${t.platform} ${t.name} ${t.version}\n")
        log.append("# phase=$phase\n")
        log.append("# file=$FILE_DATE\n")
        log.append("# fileName=${t.fileName}\n# url=${t.url}\n")
        log.append("# prelude=${FiveSourcePaths.preludeLength} chars\n")
        log.append("# jdk=${System.getProperty("java.version")}\n# os=${System.getProperty("os.name")}\n")
        log.append("# cwd=${File(".").absolutePath}\n")
        log.append("# cacheDir=${FiveSourcePaths.sourceDir.absolutePath}\n")
        log.append("# cacheCandidates=${FiveSourcePaths.cacheCandidates}\n\n")

        val src = File(FiveSourcePaths.sourceDir, t.fileName)
        if (!src.isFile || src.length() == 0L) {
            log.append("## SKIP 源码不在缓存里：${src.absolutePath}（cacheDir=${HealthCache.dir.absolutePath}）\n")
            writeRaw(t.platform, log.toString(), phase)
            return FiveSourceRow(t.name, t.version, t.platform, t.url, "no-source", false, 0, "", false, "", "", "", phase, 0, "缓存缺失，未取证")
        }
        val code = src.readText()

        val httpTrace = mutableListOf<String>()
        val consoleTrace = mutableListOf<String>()
        val attempts = mutableListOf<FiveSourceAttempt>()

        var platform = t.platform
        var functions = ""
        var items = 0
        var firstTitle = ""
        var searchFail = ""
        var installFail = ""
        var mediaFail = ""
        var mediaUrl = ""
        var mediaOk = false
        var okQuality = ""

        val rt = RealPluginHealthTest.runtimeFor(code, t.platform)
        rt.onHttp = { m, u, s, ms -> httpTrace += "HTTP $m $s ${ms}ms $u" }
        rt.onLog = { lvl, msg -> consoleTrace += "console.$lvl: $msg" }

        try {
            try {
                rt.install()
            } catch (t2: Throwable) {
                installFail = raw(t2)
                log.append("## install 失败\n").append(installFail).append("\n\n")
            }

            if (installFail.isEmpty()) {
                val metaJson = runCatching { rt.readMetaJson() }.getOrNull() ?: "{}"
                val decoded = PluginMetaCodec.decode(metaJson, t.url, code.toByteArray().size.toLong(), t.name)
                decoded.meta?.let { platform = it.platform }
                functions = runCatching { rt.probeFunctions().sorted().joinToString(",") }.getOrDefault("")
                log.append("## meta\n").append(metaJson.take(1200)).append("\n")
                log.append("## functions\n").append(functions).append("\n\n")

                if (!functions.split(',').contains("search")) {
                    searchFail = "exports 里没有 search：[$functions]"
                } else {
                    val parsed = try {
                        val rawSearch = rt.call("search", JsonPrimitive(KEYWORD), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC))
                        log.append("## search 原始返回（前 2000 字）\n").append(rawSearch.toString().take(2000)).append("\n\n")
                        SearchParser.parse(rawSearch, platform, SearchType.MUSIC)
                    } catch (t2: Throwable) {
                        searchFail = raw(t2)
                        log.append("## search 失败\n").append(searchFail).append("\n\n")
                        null
                    }
                    if (parsed != null) {
                        items = parsed.items.size
                        if (parsed.items.isEmpty()) {
                            searchFail = parsed.error?.takeIf { it.isNotBlank() } ?: "搜「$KEYWORD」没有结果"
                            log.append("## search 空结果\n").append(searchFail).append("\n\n")
                        } else {
                            val first = parsed.items.first()
                            firstTitle = first.title
                            val payload = first.raw ?: first.toJson()
                            log.append("## 第一条\n").append(payload.toString().take(1500)).append("\n\n")
                            for (q in QUALITIES) {
                                val one = try {
                                    val s = SearchParser.mediaSource(
                                        rt.call("getMediaSource", payload, JsonPrimitive(q))
                                    )
                                    FiveSourceAttempt(q, true, s?.url ?: "", s?.headers?.toString() ?: "", "")
                                } catch (t2: Throwable) {
                                    FiveSourceAttempt(q, false, "", "", raw(t2))
                                }
                                attempts += one
                                log.append("## getMediaSource(quality=$q) ")
                                    .append(if (one.ok) "OK url=${one.url.take(180)}" else "FAIL").append("\n")
                                if (!one.ok) log.append(one.error).append("\n")
                                log.append("\n")
                                if (one.ok) { mediaOk = true; mediaUrl = one.url; okQuality = q; break }
                            }
                            if (!mediaOk) {
                                mediaFail = attempts.lastOrNull { !it.ok }?.error.orEmpty()
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { rt.close() }
        }

        val stage = when {
            installFail.isNotEmpty() -> "install"
            searchFail.isNotEmpty() && items == 0 -> "search"
            !mediaOk -> "getMediaSource"
            else -> null
        }
        val verdict = autoVerdict(installFail, searchFail, mediaFail, httpTrace, consoleTrace)
        val needed = neededFor(verdict)

        log.append("## HTTP 记录（${httpTrace.size} 条）\n")
        if (httpTrace.isEmpty()) log.append("（无）\n") else httpTrace.forEach { log.append("  ").append(it).append("\n") }
        log.append("\n## console 记录（${consoleTrace.size} 条）\n")
        if (consoleTrace.isEmpty()) log.append("（无）\n") else consoleTrace.forEach { log.append("  ").append(it.take(500)).append("\n") }
        log.append("\n## 结论\n stage=$stage\n searchOk=${searchFail.isEmpty() && items > 0}\n mediaOk=$mediaOk\n okQuality=$okQuality\n verdict=$verdict\n needed=$needed\n totalMs=${System.currentTimeMillis() - started}\n")

        writeRaw(t.platform, log.toString(), phase)
        return FiveSourceRow(
            t.name, t.version, platform, t.url, stage,
            searchOk = searchFail.isEmpty() && items > 0,
            items = items,
            firstTitle = firstTitle,
            mediaOk = mediaOk,
            mediaUrl = mediaUrl,
            quality = okQuality,
            verdict = verdict,
            phase = phase,
            ms = System.currentTimeMillis() - started,
            note = listOf(installFail, searchFail, mediaFail).firstOrNull { it.isNotBlank() }?.lines()?.firstOrNull()?.take(220).orEmpty(),
            functions = functions,
        )
    }

    // ---------------------------------------------------------------- 判定

    private fun autoVerdict(
        installFail: String,
        searchFail: String,
        mediaFail: String,
        httpTrace: List<String>,
        console: List<String>,
    ): String {
        val all = (installFail + searchFail + mediaFail + console.joinToString(" ")).lowercase()
        val statuses = httpTrace.mapNotNull { Regex("""HTTP \S+ (-?\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val allBridgeDown = statuses.isNotEmpty() && statuses.all { it == -1 }
        val has4xx = statuses.any { it in 400..499 }
        val has2xx = statuses.any { it in 200..299 }
        return when {
            // 宿主桥没有的能力：模块加载失败 / JS 里调用了 undefined 的宿主函数
            all.contains("无法加载模块") -> "(a)"
            all.contains("not a function") -> "(a)"
            // 宿主网络层全挂（每条请求都是 -1）→ 桥缺能力或出口被拦
            allBridgeDown -> "(a)"
            // 网络层没通 / 连不上 → 环境或出口问题
            all.contains("域名解析失败") || all.contains("连不上服务器") || all.contains("请求超时") -> "(f)"
            // 签名/鉴权类：源站返回 4xx 且插件报签名失败
            has4xx && (all.contains("sign") || all.contains("签名") || all.contains("auth")) -> "(c)"
            all.contains("sign 全部端点失败") || all.contains("签名校验") -> "(c)"
            // 请求都通了（2xx）但插件自己说取不到链接 → 头/Cookie/参数不对，或会员限制
            has2xx && (all.contains("未取得播放链接") || all.contains("取不到") || all.contains("no url")) -> "(b)"
            has2xx -> "(b)"
            else -> "(f)"
        }
    }

    private fun neededFor(v: String): String = when (v) {
        "(a)" -> "宿主桥补齐该插件实际依赖的能力（见 raw log 的首个 JS 异常 + 模块名）"
        "(b)" -> "对齐源站当前请求头/Cookie/UA（见 raw log 的 HTTP 记录与插件内常量）"
        "(c)" -> "跟随源站改版的签名算法（需逆向该源站当前 sign 计算）"
        "(d)" -> "插件失效：等上游更新版本，或换源"
        "(e)" -> "需要会员 Cookie/登录态才能取链"
        else -> "待人工判读（见 raw log）"
    }

    private fun raw(t: Throwable): String {
        val d = (t as? PluginRuntimeException)?.detailText?.takeIf { it.isNotBlank() }
        if (d != null) return d
        return t::class.java.name + ": " + (t.message ?: "") +
            "\n" + t.stackTrace.take(15).joinToString("\n") { "at $it" }
    }

    private fun writeRaw(platform: String, text: String, phase: String) {
        val name = if (phase == "before") "$platform.log" else "$platform.$phase.log"
        val f = File(FiveSourcePaths.rawDir, name)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    companion object {
        private const val KEYWORD = "周杰伦"

        /** 插件的音质串各源不同，这里覆盖 0.0.8 订阅里出现过的常见四档。 */
        private val QUALITIES = listOf("standard", "high", "320k", "flac", "low")

        private const val URL_KUGOU = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/kugou-source.plugin.v1.9.24.js"
        private const val URL_KUWO = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/kuwo-source.plugin.v1.9.24.js"
        private const val URL_QQ = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/qq-source.plugin.v1.9.26.js"
        private const val URL_QISHUI = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/qishui-source.plugin.v1.9.23.js"
        private const val URL_NETEASE = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/netease-source.plugin.v1.9.27.js"

        private val FILE_DATE: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())

        private val quickJsAvailable: Boolean by lazy {
            runCatching {
                com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
            }.isSuccess
        }
    }
}

data class FiveSourceAttempt(
    val quality: String,
    val ok: Boolean,
    val url: String,
    val headers: String,
    val error: String,
)

data class FiveSourceRow(
    val name: String,
    val version: String,
    val platform: String,
    val url: String,
    val stage: String?,
    val searchOk: Boolean,
    val items: Int,
    val firstTitle: String,
    val mediaOk: Boolean,
    val mediaUrl: String,
    val quality: String,
    val verdict: String,
    val phase: String,
    val ms: Long,
    val note: String,
    val functions: String = "",
)

/** 证据落盘目录：只写 docs 下 ui-0.0.9 的 a 段（白名单内）。 */
object FiveSourcePaths {
    val root: File by lazy {
        val candidates = listOf(
            File("${System.getProperty("guangyue.rootDir") ?: "."}/docs/ui-0.0.9/a"),
            File("../docs/ui-0.0.9/a"),
            File("../../docs/ui-0.0.9/a"),
            File("docs/ui-0.0.9/a"),
        )
        candidates.firstOrNull { it.isDirectory } ?: candidates.first().apply { mkdirs() }
    }

    val rawDir: File get() = File(root, "five-source-raw")

    /**
     * 插件源码缓存。
     *
     * 注意：`core/build/plugin-cache-0.0.4` 这个**空目录**会先被 `../build/...` 命中，
     * 所以不能只按「目录存在」挑，必须按「里面真的有这 5 个源」挑；都不满足时退到
     * 仓库根（Gradle 传进来的 `guangyue.rootDir`）。
     */
    val sourceDir: File by lazy {
        val cands = listOf(
            File("../build/plugin-cache-0.0.4"),
            File("build/plugin-cache-0.0.4"),
            File("../../build/plugin-cache-0.0.4"),
            File("${System.getProperty("guangyue.rootDir") ?: "."}/build/plugin-cache-0.0.4"),
        )
        cands.firstOrNull { d -> d.isDirectory && File(d, PROBE_MARKER).isFile }
            ?: cands.last()
    }

    private const val PROBE_MARKER = "kugou-source.plugin.v1.9.24.js"

    val cacheCandidates: String
        get() = listOf(
            "../build/plugin-cache-0.0.4",
            "build/plugin-cache-0.0.4",
            "../../build/plugin-cache-0.0.4",
            "${System.getProperty("guangyue.rootDir") ?: "."}/build/plugin-cache-0.0.4",
        ).joinToString(" | ")

    val preludeLength: Int by lazy { runCatching { HealthCache.preludeText().length }.getOrDefault(-1) }
}

object FiveSourceEvidence {
    private val json = Json { ignoreUnknownKeys = true }

    private val file: File get() = File(FiveSourcePaths.root, "five-source-evidence.jsonl")

    @Synchronized
    fun append(row: FiveSourceRow) {
        file.parentFile?.mkdirs()
        val obj = buildJsonObject {
            put("name", JsonPrimitive(row.name))
            put("version", JsonPrimitive(row.version))
            put("platform", JsonPrimitive(row.platform))
            put("url", JsonPrimitive(row.url))
            put("stage", row.stage?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            put("searchOk", JsonPrimitive(row.searchOk))
            put("items", JsonPrimitive(row.items))
            put("firstTitle", JsonPrimitive(row.firstTitle))
            put("mediaOk", JsonPrimitive(row.mediaOk))
            put("mediaUrl", JsonPrimitive(row.mediaUrl))
            put("quality", JsonPrimitive(row.quality))
            put("verdict", JsonPrimitive(row.verdict))
            put("needed", JsonPrimitive(
                when (row.verdict) {
                    "(a)" -> "宿主桥补齐该插件实际依赖的能力"
                    "(b)" -> "对齐源站请求头/Cookie/UA"
                    "(c)" -> "跟随源站改版的签名算法"
                    "(d)" -> "插件失效，等上游更新或换源"
                    "(e)" -> "需要会员 Cookie/登录态"
                    else -> "待人工判读"
                }
            ))
            put("phase", JsonPrimitive(row.phase))
            put("ms", JsonPrimitive(row.ms))
            put("functions", JsonPrimitive(row.functions))
            put("note", JsonPrimitive(row.note))
        }
        file.appendText(obj.toString() + "\n")
    }

    @Synchronized
    fun reset() {
        if (file.isFile) file.delete()
    }

    @Suppress("unused")
    fun parse(line: String): JsonObject? = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()

    @Suppress("unused")
    fun asArray(items: List<JsonPrimitive>) = buildJsonArray { items.forEach { add(it) } }
}