package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMetaCodec
import com.guangyue.core.plugin.SearchParser
import com.guangyue.core.plugin.SearchType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.io.File

/**
 * 0.0.7 端到端可用性体检：**真搜一次 + 真取一次播放地址**。
 *
 * 与 0.0.4 的 [RealPluginHealthTest] 的区别就在这里——那份体检只断言
 * 「`search` 返回的 JSON 不是 null」，于是 57 个里 43 个被标为通过，但装机后大量音源
 * 搜不到东西（空数组也能通过、Promise 没结算也能通过、字段名不对也照样通过）。
 *
 * 这一份把用户真实路径跑两遍：
 *
 * ```
 * search("周杰伦", 1, "music") → SearchParser → MusicItem 至少一条
 * getMediaSource(第一条, "standard") → SearchParser.mediaSource → url 非空
 * ```
 *
 * 只有两步都过的插件才会被 [com.guangyue.core.plugin.PluginHealth.OK] 标记，
 * 结果写进 `build/plugin-cache-0.0.4/usable-report.jsonl`，再由
 * [PluginUsableReportTest] 汇总成 `docs/plugin-usable-0.0.7.md` 与
 * `core/plugin/src/main/assets/plugin-health.json`（出厂基线，「只启用体检可用的」用的就是它）。
 *
 * 体检用例**不 fail 构建**：失败是数据，不是回归。回归防线在 [RuntimeRegressionTest]、
 * SearchParserTest 与 SearchEndToEndTest。
 */
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PluginUsableTest(private val entry: RealPluginHealthTest.PluginEntry) {

    @Test
    fun usable() {
        assumeQuickJs()
        val code = HealthCache.source(entry)
        assumeTrue("插件源码拿不到（离线且无缓存）：${entry.name}", code != null)

        val row = UsableReport.check(entry, code!!)
        UsableReport.append(row)
        println("[usable] ${row.platform} ${entry.name} ${entry.version} health=${row.health} items=${row.items} media=${row.mediaOk} ${row.ms}ms ${row.note.orEmpty()}")
    }

    companion object {
        private val quickJsAvailable: Boolean by lazy {
            runCatching {
                com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
            }.isSuccess
        }

        private fun assumeQuickJs() = assumeTrue("当前 JVM 没有 QuickJS natives，跳过", quickJsAvailable)

        @JvmStatic
        @Parameterized.Parameters(name = "{index}-{0}")
        fun entries(): List<RealPluginHealthTest.PluginEntry> = UsableCache.entries()
    }
}

/** 一行可用性记录。 */
data class UsableRow(
    val name: String,
    val version: String,
    val platform: String = "",
    val health: String = PluginHealth.UNKNOWN,
    val items: Int = 0,
    val firstTitle: String = "",
    val mediaOk: Boolean = false,
    val mediaUrl: String = "",
    val note: String? = null,
    val functions: String = "",
    val searchTypes: String = "",
    val ms: Long = 0,
)

object UsableCache {
    val dir: File by lazy {
        listOf(
            File("../build/plugin-cache-0.0.4"),
            File("build/plugin-cache-0.0.4"),
            File("../../build/plugin-cache-0.0.4"),
            File("${System.getProperty("guangyue.rootDir") ?: "."}/build/plugin-cache-0.0.4"),
        ).firstOrNull { it.isDirectory } ?: File("../build/plugin-cache-0.0.4").apply { mkdirs() }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 与 0.0.4 体检同一批 57 个订阅条目（源码缓存 0.0.4 里已有，直接复用）。 */
    fun entries(): List<RealPluginHealthTest.PluginEntry> =
        com.guangyue.core.plugin.runtime.HealthCache.entries()

    fun source(entry: RealPluginHealthTest.PluginEntry): String? =
        com.guangyue.core.plugin.runtime.HealthCache.source(entry)

    fun legacyRows(): Map<String, HealthRow> {
        val f = File(HealthCache.dir, "health-report.jsonl").takeIf { it.isFile } ?: return emptyMap()
        return readRows(f)
            .mapNotNull { it.toHealthRow() }
            .associateBy { it.name.lowercase() }
    }

    fun readRows(file: File): List<JsonObject> = file.readLines()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() }

    private fun JsonObject.toHealthRow(): HealthRow? = runCatching {
        HealthRow(
            name = this["name"]?.jsonPrimitive?.content ?: return null,
            version = this["version"]?.jsonPrimitive?.content ?: "",
            platform = this["platform"]?.jsonPrimitive?.content ?: "",
            functions = this["functions"]?.jsonPrimitive?.content ?: "",
            searchOk = this["searchOk"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            stage = this["stage"]?.jsonPrimitive?.content,
            error = this["error"]?.jsonPrimitive?.content.orEmpty(),
            ms = this["ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
    }.getOrNull()
}

object UsableReport {
    private val file = File(UsableCache.dir, "usable-report.jsonl")

    @Synchronized
    fun append(row: UsableRow) {
        file.parentFile?.mkdirs()
        val obj = JsonObject(
            buildMap {
                put("name", JsonPrimitive(row.name))
                put("version", JsonPrimitive(row.version))
                put("platform", JsonPrimitive(row.platform))
                put("health", JsonPrimitive(row.health))
                put("items", JsonPrimitive(row.items))
                put("firstTitle", JsonPrimitive(row.firstTitle))
                put("mediaOk", JsonPrimitive(row.mediaOk))
                put("mediaUrl", JsonPrimitive(row.mediaUrl))
                row.note?.let { put("note", JsonPrimitive(it)) }
                put("functions", JsonPrimitive(row.functions))
                put("searchTypes", JsonPrimitive(row.searchTypes))
                put("ms", JsonPrimitive(row.ms))
            }
        )
        file.appendText(obj.toString() + "\n")
    }

    @Synchronized
    fun reset() {
        if (file.isFile) file.delete()
    }

    /** 真跑两步：search 出结果 → getMediaSource 拿地址。 */
    fun check(entry: RealPluginHealthTest.PluginEntry, code: String, keyword: String = "周杰伦"): UsableRow {
        val started = System.currentTimeMillis()
        var platform = ""
        var functions = ""
        var searchTypes = ""
        val rt = RealPluginHealthTest.runtimeFor(code, entry.slug)
        try {
            rt.install()
            val metaJson = rt.readMetaJson()
            val decoded = PluginMetaCodec.decode(metaJson, entry.url, code.toByteArray().size.toLong(), entry.name)
            val meta = decoded.meta
                ?: return UsableRow(entry.name, entry.version, health = PluginHealth.UNSUPPORTED, note = "元信息解析失败：${decoded.error}", ms = elapsed(started))
            platform = meta.platform
            functions = runCatching { rt.probeFunctions().sorted().joinToString(",") }.getOrDefault("")
            searchTypes = meta.searchTypes.joinToString("/")
            if (!functions.split(',').contains("search")) {
                return UsableRow(entry.name, entry.version, platform, PluginHealth.UNSUPPORTED, note = "exports 里没有 search", functions = functions, searchTypes = searchTypes, ms = elapsed(started))
            }

            // 第一步：真搜一次，并按 App 的同一份映射解析。
            val parsed = try {
                val raw = rt.call("search", JsonPrimitive(keyword), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC))
                SearchParser.parse(raw, platform, SearchType.MUSIC)
            } catch (t: Throwable) {
                return UsableRow(
                    entry.name, entry.version, platform,
                    health = PluginHealth.SEARCH_FAILED,
                    note = message(t), functions = functions, searchTypes = searchTypes, ms = elapsed(started),
                )
            }
            if (parsed.items.isEmpty()) {
                return UsableRow(
                    entry.name, entry.version, platform,
                    health = if (parsed.error != null) PluginHealth.SEARCH_FAILED else PluginHealth.NO_RESULT,
                    note = parsed.error?.let { message(it) } ?: "搜「$keyword」没有结果",
                    functions = functions, searchTypes = searchTypes, ms = elapsed(started),
                )
            }

            // 第二步：拿第一条去取播放地址（App 播放时走的就是这一步）。
            val first = parsed.items.first()
            val source = try {
                val payload = first.raw ?: first.toJson()
                SearchParser.mediaSource(rt.call("getMediaSource", payload, JsonPrimitive("standard")))
            } catch (t: Throwable) {
                return UsableRow(
                    entry.name, entry.version, platform,
                    health = PluginHealth.NO_MEDIA,
                    items = parsed.items.size, firstTitle = first.title,
                    note = "getMediaSource 失败：" + message(t),
                    functions = functions, searchTypes = searchTypes, ms = elapsed(started),
                )
            }
            return UsableRow(
                entry.name, entry.version, platform,
                health = if (source != null) PluginHealth.OK else PluginHealth.NO_MEDIA,
                items = parsed.items.size,
                firstTitle = first.title,
                mediaOk = source != null,
                mediaUrl = source?.url.orEmpty(),
                note = if (source == null) "「${first.title}」取不到播放地址" else null,
                functions = functions, searchTypes = searchTypes, ms = elapsed(started),
            )
        } catch (t: Throwable) {
            return UsableRow(
                entry.name, entry.version, platform,
                health = PluginHealth.SEARCH_FAILED, note = message(t),
                functions = functions, searchTypes = searchTypes, ms = elapsed(started),
            )
        } finally {
            runCatching { rt.close() }
        }
    }

    private fun message(t: Throwable): String {
        val raw = (t as? PluginRuntimeException)?.detailText?.takeIf { it.isNotBlank() }
            ?: (t.javaClass.simpleName + ": " + (t.message ?: ""))
        return message(raw)
    }

    private fun message(raw: String): String {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        return when {
            text.contains("Promise that has not settled") -> "插件返回的异步结果没结算"
            text.contains("InterruptedException") || text.contains("超时") -> "插件超时没返回"
            text.contains("not a function") -> "插件内部报错（函数不存在）"
            text.contains("UnknownHost") || text.contains("Unable to resolve host") -> "连不上源站"
            else -> text.take(200)
        }
    }

    private fun elapsed(started: Long) = System.currentTimeMillis() - started
}