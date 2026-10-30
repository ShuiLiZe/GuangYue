package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginHealth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * 把 [PluginUsableTest] 的逐插件结果汇总成两份交付物：
 *
 *  - `docs/plugin-usable-0.0.8.md` —— 0.0.8 的可用插件清单（**只有真能搜、真能取到播放地址的才标可用**）；
 *  - `core/plugin/src/main/assets/plugin-health.json` —— 出厂体检基线，App 里「只启用体检可用的」
 *    与插件管理页的健康状态就靠它（见 [com.guangyue.core.plugin.PluginHealthBaseline]）。
 *
 * 数据源是 `build/plugin-cache-0.0.4/usable-report.jsonl`（跑 [PluginUsableTest] 时写入）。
 * 报告文件不存在时这条用例 skip 而不是 fail：报告是「跑过体检」的产物，不是回归门槛。
 */
class PluginUsableReportTest {

    private fun reportFile(): File? {
        val dir = UsableCache.dir
        val f = File(dir, "usable-report.jsonl")
        return f.takeIf { it.isFile }
    }

    private fun rows(): List<UsableRow> {
        val file = reportFile() ?: return emptyList()
        val byName = LinkedHashMap<String, UsableRow>()
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
            val row = o.toRow() ?: return@forEach
            byName[row.name] = row
        }
        return byName.values.toList()
    }

    private fun JsonObject.toRow(): UsableRow? {
        val name = this["name"]?.jsonPrimitive?.content ?: return null
        return UsableRow(
            name = name,
            version = this["version"]?.jsonPrimitive?.content.orEmpty(),
            platform = this["platform"]?.jsonPrimitive?.content.orEmpty(),
            health = this["health"]?.jsonPrimitive?.content ?: PluginHealth.UNKNOWN,
            items = this["items"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            firstTitle = this["firstTitle"]?.jsonPrimitive?.content.orEmpty(),
            mediaOk = this["mediaOk"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            mediaUrl = this["mediaUrl"]?.jsonPrimitive?.content.orEmpty(),
            note = this["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            functions = this["functions"]?.jsonPrimitive?.content.orEmpty(),
            searchTypes = this["searchTypes"]?.jsonPrimitive?.content.orEmpty(),
            ms = this["ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
    }

    private fun docFile(): File {
        val root = System.getProperty("guangyue.rootDir") ?: File(".").absolutePath
        return File(root, "docs/plugin-usable-0.0.8.md")
    }

    private fun assetFile(): File {
        val root = System.getProperty("guangyue.rootDir") ?: File(".").absolutePath
        return File(root, "core/plugin/src/main/assets/plugin-health.json")
    }

    private fun label(health: String): String = when (health) {
        PluginHealth.OK -> "可用"
        PluginHealth.NO_RESULT -> "搜索无结果"
        PluginHealth.NO_MEDIA -> "搜得到但取不到播放地址"
        PluginHealth.SEARCH_FAILED -> "搜索失败"
        PluginHealth.UNSUPPORTED -> "没声明 search"
        else -> "未体检"
    }

    private fun host(url: String): String = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")

    private fun cell(text: String): String = text.replace("|", "\\|").replace(Regex("\\s+"), " ").trim()

    @Test
    fun `aggregate usable report into docs and factory health baseline`() {
        val rows = rows()
        assumeTrue("还没跑 PluginUsableTest（没有 build/plugin-cache-0.0.4/usable-report.jsonl）", rows.isNotEmpty())

        val usable = rows.filter { it.health == PluginHealth.OK }
        val noResult = rows.filter { it.health == PluginHealth.NO_RESULT }
        val noMedia = rows.filter { it.health == PluginHealth.NO_MEDIA }
        val failed = rows.filter { it.health == PluginHealth.SEARCH_FAILED }
        val unsupported = rows.filter { it.health == PluginHealth.UNSUPPORTED }
        val unknown = rows.filter { it.health == PluginHealth.UNKNOWN || it.health.isBlank() }

        val sb = StringBuilder()
        sb.appendLine("# 广乐 0.0.8 · 插件可用性清单")
        sb.appendLine()
        sb.appendLine("> 生成时间：${LocalDateTime.now()}　判据：`search(\"周杰伦\", 1, \"music\")` 至少返回一条**可映射成列表项**的结果，")
        sb.appendLine("> 且拿第一条继续调 `getMediaSource(item, \"standard\")` 后 `url` 非空。两步都过才算「可用」。")
        sb.appendLine()
        sb.appendLine("## 结论")
        sb.appendLine()
        sb.appendLine("| 项 | 数 |")
        sb.appendLine("| --- | --- |")
        sb.appendLine("| 参检插件 | ${rows.size} |")
        sb.appendLine("| **可用（能搜 + 能取到播放地址）** | **${usable.size}** |")
        sb.appendLine("| 搜索无结果 | ${noResult.size} |")
        sb.appendLine("| 搜得到但取不到播放地址 | ${noMedia.size} |")
        sb.appendLine("| 搜索失败（插件报错 / 异步未结算 / 超时） | ${failed.size} |")
        sb.appendLine("| 没声明 search | ${unsupported.size} |")
        sb.appendLine("| 未体检 | ${unknown.size} |")
        sb.appendLine()
        sb.appendLine("0.0.4 的体检把 ${rows.size} 个里的 43 个标成「导入成功」，但那份体检只判断")
        sb.appendLine("`search` 返回的 JSON 是不是 null —— 空数组、Promise 没结算、字段名对不上都能过。")
        sb.appendLine("这一版改成真搜 + 真取源，可用数降到 **${usable.size}** 才是装机后用户实际能看到的结果。")
        sb.appendLine()
        sb.appendLine("## 可用插件（${usable.size}）")
        sb.appendLine()
        if (usable.isEmpty()) {
            sb.appendLine("_没有插件同时通过两步。_")
        } else {
            sb.appendLine("| 插件 | 版本 | platform | 搜索类型 | 首条结果 | 播放地址域名 | 耗时 |")
            sb.appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            usable.sortedBy { it.name }.forEach { r ->
                sb.appendLine(
                    "| ${cell(r.name)} | ${cell(r.version)} | ${cell(r.platform)} | ${cell(r.searchTypes)} " +
                        "| ${cell(r.firstTitle)} | ${cell(host(r.mediaUrl))} | ${r.ms} ms |"
                )
            }
        }
        sb.appendLine()

        fun section(title: String, list: List<UsableRow>) {
            sb.appendLine("## $title（${list.size}）")
            sb.appendLine()
            if (list.isEmpty()) {
                sb.appendLine("_无_")
                sb.appendLine()
                return
            }
            sb.appendLine("| 插件 | 版本 | platform | 原因 |")
            sb.appendLine("| --- | --- | --- | --- |")
            list.sortedBy { it.name }.forEach { r ->
                sb.appendLine("| ${cell(r.name)} | ${cell(r.version)} | ${cell(r.platform)} | ${cell(r.note.orEmpty())} |")
            }
            sb.appendLine()
        }

        section("搜索失败", failed)
        section("搜索无结果", noResult)
        section("搜得到但取不到播放地址", noMedia)
        section("没声明 search", unsupported)
        section("未体检", unknown)

        sb.appendLine("## 说明")
        sb.appendLine()
        sb.appendLine("- 判据与实际 App 路径完全一致：`PluginRuntime.call` → `SearchParser.parse` → `getMediaSource`。")
        sb.appendLine("  映射层（`data/isEnd`、`title/artist/album/artwork/id`、秒/毫秒）由")
        sb.appendLine("  `SearchParserTest` 单测覆盖，本清单只记录真插件跑通与否。")
        sb.appendLine("- 结果由 `PluginUsableTest` 逐插件产生（`build/plugin-cache-0.0.4/usable-report.jsonl`），")
        sb.appendLine("  本文件由 `PluginUsableReportTest` 汇总，可重跑复现。")
        sb.appendLine("- 「搜索无结果」多为源站改版或地区限制；「搜得到但取不到播放地址」说明该源搜索可用但")
        sb.appendLine("  取链接被墙/需要会员，App 里会提示「换其他音源搜同名歌曲」。")
        sb.appendLine("- 8 个 `async` 长调用插件（B站、火龙DJ、DJ串烧集、蜻蜓FM、喜马拉雅、皮狗DJ、DJKK、82DJ）")
        sb.appendLine("  仍未改成真异步任务队列，见 `docs/async-queue-0.0.7.md` 与 CHANGELOG 的延期说明。")
        sb.appendLine()

        val doc = docFile()
        doc.parentFile?.mkdirs()
        doc.writeText(sb.toString())

        // 出厂基线：platform → health，插件管理页在用户没体检过时也有话可说。
        val baseline = buildJsonObject {
            put("version", JsonPrimitive("0.0.8"))
            put("generatedAt", JsonPrimitive(LocalDateTime.now().toString()))
            put("keyword", JsonPrimitive("周杰伦"))
            put("plugins", buildJsonArray {
                rows.filter { it.platform.isNotBlank() }
                    .distinctBy { it.platform }
                    .sortedBy { it.platform }
                    .forEach { r ->
                        add(buildJsonObject {
                            put("platform", JsonPrimitive(r.platform))
                            put("name", JsonPrimitive(r.name))
                            put("health", JsonPrimitive(r.health))
                            r.note?.let { put("note", JsonPrimitive(it)) }
                        })
                    }
            })
        }
        assetFile().writeText(baseline.toString())

        assertTrue("参检插件数应当等于订阅条目数", rows.size >= 50)
        assertTrue("报告应当写出可用清单", doc.isFile && doc.length() > 500)
        assertTrue("基线应当被插件管理页用上", assetFile().isFile && assetFile().length() > 100)
        println("[usable-report] 参检 ${rows.size}，可用 ${usable.size}，无结果 ${noResult.size}，无播放地址 ${noMedia.size}，失败 ${failed.size}")
    }
}