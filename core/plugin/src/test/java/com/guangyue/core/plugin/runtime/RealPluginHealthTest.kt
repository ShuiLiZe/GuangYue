package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginMetaCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 0.0.4 全量体检：把默认订阅源里的 **全部 57 个**插件逐个走一遍 App 的同一条导入路径。
 *
 * 为什么要有它：0.0.1 的 release 包让这 57 个插件全部导入失败，0.0.2/0.0.3 又改了三次求值通道，
 * 但仓库里始终只有 6 个 fixture 做回归。真机上的「导入」实际包含的步骤比 6 个 fixture 覆盖的多得多：
 *
 * ```
 * 下载 → install()（bridge → prelude → plugin.js → dispatcher → probe）
 *      → readMetaJson() → PluginMetaCodec.decode()（类型校验）
 *      → probeFunctions()（反射 exports 里真正挂着的函数）
 *      → call("search")（走完 dispatcher → JSON 文本 → Kotlin 解析）
 * ```

 *
 * 插件源码不进仓库：放在 build/plugin-cache-0.0.4/，根目录 build 下已被 .gitignore 覆盖。
 * 缓存缺失时用 `plugins.json` 里的 url 现下（先写 `build/plugin-cache-0.0.4/plugins.json`）。
 * 任一插件用例失败都会把该行的体检记录（阶段 + 报错前 300 字）追加到
 * `build/plugin-cache-0.0.4/health-report.jsonl`，`docs/plugin-health-0.0.4.md` 就是由它生成的。
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RealPluginHealthTest(private val entry: PluginEntry) {

    data class PluginEntry(val name: String, val version: String, val url: String) {
        val fileName: String get() = url.substringAfterLast('/')
        val slug: String get() = fileName.removeSuffix(".js").removeSuffix(".plugin")
    }

    @Test
    fun health() {
        assumeQuickJs()
        val cache = HealthCache
        val code = cache.source(entry)
        assumeTrue("插件源码拿不到（离线且无缓存）：${entry.name}", code != null)

        val row = cache.run(entry, code!!)
        HealthReport.append(row)
        // 这是**体检**用例，不是回归用例：失败要如实记进 health-report.jsonl（再由
        // PluginHealthReportTest 汇总成 docs/plugin-health-0.0.4.md），但不能让整个构建挂掉。
        // 失败里相当一部分是插件自身混淆代码/外部站点改版/真机网络才跑得完的慢插件，
        // 修不修都不是 0.0.4 的运行时问题。回归防线在 RuntimeRegressionTest。
        if (row.stage != null) {
            println("[health] FAIL ${entry.name} ${entry.version} [${row.stage}] ${row.error.take(200)}")
        }
    }

    companion object {
        private val prelude: String by lazy { HealthCache.preludeText() }

        private val quickJsAvailable: Boolean by lazy {
            runCatching {
                com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
            }.isSuccess
        }

        private fun assumeQuickJs() {
            assumeTrue("当前 JVM 没有 QuickJS natives，跳过", quickJsAvailable)
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{index}-{0}")
        fun entries(): List<PluginEntry> = HealthCache.entries()

        /** 供 [RealPluginHealthTest] 之外统一取用（体检报告生成脚本也走这里）。 */
        fun runtimeFor(code: String, platform: String) = PluginRuntime(
            meta = com.guangyue.core.plugin.PluginMeta(
                id = platform, platform = platform, name = platform, version = "1", source = "health://$platform",
            ),
            code = code,
            http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
            preludeProvider = HealthPrelude(prelude),
        )
    }
}

/** 一行体检记录。`stage == null` 表示全链路通过。 */
data class HealthRow(
    val name: String,
    val version: String,
    val platform: String = "",
    val pluginVersion: String = "",
    val typesOk: Boolean = true,
    val typeNotes: String = "",
    val functions: String = "",
    val searchOk: Boolean = false,
    val stage: String? = null,
    val error: String = "",
    val ms: Long = 0,
)

object HealthReport {
    private val file = File(HealthCache.dir, "health-report.jsonl")

    @Synchronized
    fun append(row: HealthRow) {
        file.parentFile?.mkdirs()
        val obj = JsonObject(
            mapOf(
                "name" to JsonPrimitive(row.name),
                "version" to JsonPrimitive(row.version),
                "platform" to JsonPrimitive(row.platform),
                "pluginVersion" to JsonPrimitive(row.pluginVersion),
                "typesOk" to JsonPrimitive(row.typesOk),
                "typeNotes" to JsonPrimitive(row.typeNotes),
                "functions" to JsonPrimitive(row.functions),
                "searchOk" to JsonPrimitive(row.searchOk),
                "stage" to (row.stage?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull),
                "error" to JsonPrimitive(row.error),
                "ms" to JsonPrimitive(row.ms),
            )
        )
        file.appendText(obj.toString() + "\n")
    }

    @Synchronized
    fun reset() {
        if (file.isFile) file.delete()
    }
}

object HealthCache {
    val dir: File by lazy {
        // 注意顺序：Gradle 的 workingDir 是 core/plugin，但 JUnit fork 出来的 JVM 里
        // `File(".")` 可能是 core/plugin/.，两条相对路径都指向同一个地方；
        // 这里同时用 `..`/`.//`/`../..` 全试一遍，再退到 Gradle 的根目录属性。
        listOf(
            File("../build/plugin-cache-0.0.4"),
            File("build/plugin-cache-0.0.4"),
            File("../../build/plugin-cache-0.0.4"),
            File("${System.getProperty("guangyue.rootDir") ?: "."}/build/plugin-cache-0.0.4"),
        ).firstOrNull { it.isDirectory } ?: File("../build/plugin-cache-0.0.4").apply { mkdirs() }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun preludeText(): String = listOf(
        "src/main/assets/prelude.js",
        "core/plugin/src/main/assets/prelude.js",
        "../../core/plugin/src/main/assets/prelude.js",
    ).map { File(it) }.firstOrNull { it.isFile }?.readText()
        ?: throw AssertionError("找不到 prelude.js，当前目录 ${File(".").absolutePath}")

    fun entries(): List<RealPluginHealthTest.PluginEntry> {
        val listFile = File(dir, "plugins.json").takeIf { it.isFile } ?: return emptyList()
        val root = json.parseToJsonElement(listFile.readText().removePrefix("\uFEFF")).jsonObject
        val plugins = root["plugins"] as? JsonArray ?: return emptyList()
        return plugins.mapNotNull { el ->
            val o = el.jsonObject
            val url = o["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RealPluginHealthTest.PluginEntry(
                name = o["name"]?.jsonPrimitive?.content ?: url.substringAfterLast('/'),
                version = o["version"]?.jsonPrimitive?.content ?: "",
                url = url,
            )
        }
    }

    /** 插件源码：优先本地缓存，缺失时现下（下载失败返回 null，用例转 skip 而不是假失败）。 */
    fun source(entry: RealPluginHealthTest.PluginEntry): String? {
        val f = File(dir, entry.fileName)
        if (f.isFile && f.length() > 0) return f.readText()
        return runCatching {
            val text = java.net.URI(entry.url).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) }
            f.writeText(text)
            text
        }.getOrNull()
    }

    /** 一条插件的完整导入链路，返回体检记录。 */
    fun run(entry: RealPluginHealthTest.PluginEntry, code: String): HealthRow {
        val started = System.currentTimeMillis()
        var platform = ""
        var pluginVersion = ""
        var typesOk = true
        var typeNotes = ""
        var functions = ""
        var searchOk = false
        val rt = RealPluginHealthTest.runtimeFor(code, entry.slug)
        try {
            try {
                rt.install()
            } catch (t: Throwable) {
                return HealthRow(entry.name, entry.version, stage = stageOf(t), error = brief(t), ms = ms(started))
            }

            // 元信息：读原始 JSON 再走 App 用的同一套宽松解析，并校验类型没有降级。
            val metaJson = try {
                rt.readMetaJson()
            } catch (t: Throwable) {
                return HealthRow(entry.name, entry.version, stage = stageOf(t), error = brief(t), ms = ms(started))
            }
            val decoded = PluginMetaCodec.decode(metaJson, entry.url, code.toByteArray().size.toLong(), entry.name)
            val meta = decoded.meta
            if (meta == null) {
                return HealthRow(
                    entry.name, entry.version, stage = "meta-decode",
                    error = "元信息解析失败：${decoded.error} / $metaJson".take(300), ms = ms(started),
                )
            }
            platform = meta.platform
            pluginVersion = meta.version
            typeNotes = typeAudit(metaJson).also { typesOk = it.isBlank() }

            // 函数表：走真反射 exports，而不是插件 metadata 里声称的 functions 字段。
            functions = try {
                rt.probeFunctions().sorted().joinToString(",")
            } catch (t: Throwable) {
                return HealthRow(
                    entry.name, entry.version, platform, pluginVersion, typesOk, typeNotes,
                    stage = stageOf(t), error = brief(t), ms = ms(started),
                )
            }
            if (!functions.split(',').contains("search")) {
                return HealthRow(
                    entry.name, entry.version, platform, pluginVersion, typesOk, typeNotes, functions,
                    stage = "probe-functions", error = "exports 里没有 search：[$functions]", ms = ms(started),
                )
            }

            // 真调一次 search：dispatcher → JSON 文本 → Kotlin 解析，整条路都要活着。
            try {
                val res = rt.call("search", JsonPrimitive("周杰伦"))
                searchOk = res is JsonObject || res is JsonArray || res is JsonPrimitive
            } catch (t: Throwable) {
                return HealthRow(
                    entry.name, entry.version, platform, pluginVersion, typesOk, typeNotes, functions,
                    stage = stageOf(t), error = brief(t), ms = ms(started),
                )
            }

            return HealthRow(
                entry.name, entry.version, platform, pluginVersion, typesOk, typeNotes, functions, searchOk,
                stage = if (typesOk) null else "meta-types",
                error = if (typesOk) "" else typeNotes, ms = ms(started),
            )
        } finally {
            runCatching { rt.close() }
        }
    }

    /**
     * 元信息字段类型体检：抓到被 toString() 过的垃圾值（"true" / "[object Object]" / "NaN"）。
     *
     * 只有**确实被降级成字符串**的字段才算失败。插件显式写 `primaryKey: null` 是合法的
     * （MusicFree 允许没有主键），`JsonNull` 不是 `JsonPrimitive`，所以不会被误判。
     * 数组类字段（`primaryKey`）允许 `[...]`，不当成 "toString 过"
     * ——实际只要它是 `JsonArray` 就说明类型没坏。
     */
    private fun typeAudit(metaJson: String): String {
        val obj = runCatching { Json.parseToJsonElement(metaJson).jsonObject }.getOrNull() ?: return ""
        val notes = mutableListOf<String>()
        val bad = setOf("true", "false", "undefined", "NaN", "[object Object]", "[object Array]")
        for (field in listOf("platform", "version", "name", "author", "cacheControl", "srcUrl")) {
            val v = obj[field] as? JsonPrimitive ?: continue
            val text = v.content
            if (text.lowercase() in bad) notes += "$field=$text"
            if (text.startsWith("[")) notes += "$field=$text"
        }
        // primaryKey 合法类型是 JSON 数组或 null；字符串化（"[object Array]"/"true"）才算坏。
        val pk = obj["primaryKey"]
        if (pk is JsonPrimitive && pk.isString && pk.content.lowercase() in bad) {
            notes += "primaryKey=${pk.content}"
        }
        return notes.joinToString("; ")
    }

    private fun stageOf(t: Throwable): String {
        val msg = t.message ?: ""
        val m = Regex("""^\[([^\]]+)\]""").find(msg)
        if (m != null) return m.groupValues[1]
        return when (t) {
            is PluginRuntimeException.Missing -> "call-missing"
            is PluginRuntimeException -> "runtime"
            else -> t::class.java.simpleName
        }
    }

    private fun brief(t: Throwable): String {
        val detail = (t as? PluginRuntimeException)?.detailText
        val text = detail?.takeIf { it.isNotBlank() } ?: (t::class.java.simpleName + ": " + (t.message ?: ""))
        return text.replace(Regex("\\s+"), " ").trim().take(300)
    }

    private fun ms(started: Long) = System.currentTimeMillis() - started
}

private class HealthPrelude(private val text: String) : PreludeProvider(placeholderCtx()) {
    override fun code(): String = text
}

private fun placeholderCtx(): android.content.Context = try {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    unsafeClass.getMethod("allocateInstance", Class::class.java)
        .invoke(theUnsafe, android.content.ContextWrapper::class.java) as android.content.Context
} catch (t: Throwable) {
    throw IllegalStateException("无法构造测试用 Context", t)
}
