package com.guangyue.core.plugin.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 用户常用订阅「常用六平台」（hebijunge sub/padao/main.json）现网实测：逐个 search「周杰伦」→ getMediaSource。
 * 只在 -Dguangyue.live=true 时跑（要联网），失败是数据不是回归。
 */
class Main6LiveTest {
    @Test
    fun main6() {
        assumeTrue(System.getProperty("guangyue.live") == "true" || System.getenv("GUANGYUE_LIVE") == "true")
        val url = System.getenv("GUANGYUE_SUB") ?: "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json"
        val text = java.net.URI(url).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) }
        val arr = Json.parseToJsonElement(text.removePrefix("\uFEFF")).jsonObject["plugins"] as JsonArray
        for (el in arr) {
            val o = el.jsonObject
            val entry = RealPluginHealthTest.PluginEntry(
                name = o["name"]!!.jsonPrimitive.content,
                version = o["version"]?.jsonPrimitive?.content.orEmpty(),
                url = o["url"]!!.jsonPrimitive.content,
            )
            val code = runCatching { java.net.URI(entry.url).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrElse { println("[main6] ${entry.name} 下载失败 ${it.message}"); continue }
            val row = UsableReport.check(entry, code)
            println("[main6] ${entry.name} ${entry.version} health=${row.health} items=${row.items} first=${row.firstTitle} media=${row.mediaOk} ${row.ms}ms note=${row.note.orEmpty()} url=${row.mediaUrl.take(80)}")
        }
    }

    /** 首页要用的：推荐歌单标签 → 歌单 → 歌单曲目；榜单 → 榜单曲目。 */
    @Test
    fun main6Sheets() {
        assumeTrue(System.getProperty("guangyue.live") == "true" || System.getenv("GUANGYUE_LIVE") == "true")
        val url = System.getenv("GUANGYUE_SUB") ?: "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json"
        val text = java.net.URI(url).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) }
        val arr = Json.parseToJsonElement(text.removePrefix("\uFEFF")).jsonObject["plugins"] as JsonArray
        fun arrOf(e: kotlinx.serialization.json.JsonElement?): List<kotlinx.serialization.json.JsonObject> =
            ((e as? JsonArray) ?: ((e as? kotlinx.serialization.json.JsonObject)?.get("data") as? JsonArray))
                ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject } ?: emptyList()
        for (el in arr) {
            val o = el.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            System.getenv("GUANGYUE_ONLY")?.let { only -> if (!name.contains(only)) continue }
            val code = runCatching { java.net.URI(o["url"]!!.jsonPrimitive.content).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: continue
            val rt = RealPluginHealthTest.runtimeFor(code, name)
            if (System.getenv("GUANGYUE_TAP") == "true") {
                rt.onHttp = { m, u, st, ms -> println("[tap] $m $st ${ms}ms ${u.take(220)}") }
                rt.onLog = { lv, msg -> println("[log] $lv ${msg.take(300)}") }
            }
            val out = StringBuilder("[sheets] $name")
            try {
                rt.install()
                runCatching {
                    val groups = arrOf(rt.call("getRecommendSheetTags"))
                    val leaf = groups.firstNotNullOfOrNull { g -> arrOf(g["data"]).firstOrNull() }
                        ?: kotlinx.serialization.json.JsonObject(emptyMap())
                    val sheets = arrOf(rt.call("getRecommendSheetsByTag", leaf, kotlinx.serialization.json.JsonPrimitive(1)))
                    out.append(" tags=${groups.size} sheets=${sheets.size}")
                    sheets.firstOrNull()?.let { sh ->
                        println("[sheet0] ${sh.toString().take(500)}")
                        val info = rt.call("getMusicSheetInfo", sh, kotlinx.serialization.json.JsonPrimitive(1))
                        val ml = (info as? kotlinx.serialization.json.JsonObject)?.get("musicList") as? JsonArray
                        out.append(" sheetSongs=${ml?.size ?: -1}")
                    }
                }.onFailure { out.append(" sheetErr=${it.message?.take(160)}") }
                runCatching {
                    val groups = arrOf(rt.call("getTopLists"))
                    val first = groups.firstNotNullOfOrNull { g -> arrOf(g["data"]).firstOrNull() }
                    out.append(" topGroups=${groups.size}")
                    first?.let { t ->
                        val d = rt.call("getTopListDetail", t, kotlinx.serialization.json.JsonPrimitive(1))
                        val ml = (d as? kotlinx.serialization.json.JsonObject)?.get("musicList") as? JsonArray
                        out.append(" topSongs=${ml?.size ?: -1}")
                    }
                }.onFailure { out.append(" topErr=${it.message?.take(160)}") }
            } catch (t: Throwable) {
                out.append(" installErr=${t.message?.take(160)}")
            } finally {
                runCatching { rt.close() }
            }
            println(out)
        }
    }
}
