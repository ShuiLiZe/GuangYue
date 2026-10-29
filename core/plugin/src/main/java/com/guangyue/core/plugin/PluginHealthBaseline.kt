package com.guangyue.core.plugin

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 出厂体检基线（0.0.7）。
 *
 * 打包进 `assets/plugin-health.json` 的这份表是 `PluginUsableTest`（真 QuickJS 引擎 + 真网络）
 * 对订阅源全部插件跑出来的实测结论：每个 platform 能不能「搜索出结果」、能不能
 * 「getMediaSource 拿到播放地址」。安装插件时如果本地还没有体检记录，就用这份基线填上，
 * 用户点「只启用体检可用的」当场就能得到一份真能搜能播的列表，不必先在手机上等 57 次超时。
 *
 * 表里没有的 platform（用户自己装的第三方插件）保持 [PluginHealth.UNKNOWN]，
 * 由「体检」按钮在设备上实测。
 */
object PluginHealthBaseline {

    private const val TAG = "PluginHealthBaseline"
    private const val ASSET = "plugin-health.json"

    /** platform（小写）→ (health, note)。 */
    private var table: Map<String, Pair<String, String?>>? = null

    /** 读一次缓存住；assets 缺失或格式坏掉时返回空表（功能降级，不抛）。 */
    fun lookup(platform: String): Pair<String, String?>? {
        val loaded = table ?: load().also { table = it }
        return loaded[platform.trim().lowercase()]
    }

    fun size(): Int = (table ?: load().also { table = it }).size

    private fun load(): Map<String, Pair<String, String?>> = runCatching {
        val ctx = PluginAssetContext.get() ?: return emptyMap()
        val text = ctx.assets.open(ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        val root = Json2.parse(text)?.jsonObject ?: return emptyMap()
        val entries = root["plugins"]?.jsonArray ?: return emptyMap()
        buildMap {
            entries.forEach { element ->
                val o = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val platform = o.textOrNull("platform").orEmpty().lowercase()
                if (platform.isEmpty()) return@forEach
                val health = o.textOrNull("health") ?: PluginHealth.UNKNOWN
                put(platform, health to o.textOrNull("note"))
            }
        }
    }.getOrElse {
        Log.w(TAG, "读取体检基线失败：${it.message}")
        emptyMap()
    }
}

/**
 * 让 core:plugin 在不持有 Context 的地方也能读到 assets。
 *
 * 生产环境由 [PluginManager] 注入 applicationContext；JVM 单测里没有 Android，
 * 直接保持 null，基线查询退化成「未知」。
 */
internal object PluginAssetContext {

    @Volatile
    private var ctx: android.content.Context? = null

    fun install(context: android.content.Context) {
        ctx = context.applicationContext
    }

    fun get(): android.content.Context? = ctx
}

/** 基线里一条记录的便捷读取（`health` / `note` 都是可选字段）。 */
private fun JsonObject.textOrNull(key: String): String? = runCatching {
    this[key]?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }