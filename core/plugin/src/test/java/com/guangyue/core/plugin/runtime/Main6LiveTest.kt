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
}
