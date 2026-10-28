package com.guangyue.core.plugin

import kotlinx.serialization.Serializable

/**
 * 一条订阅源记录。
 *
 * MusicFree 生态里的「插件订阅」就是一个 JSON：
 *
 * ```json
 * {
 *   "name": "MusicFree 插件源",
 *   "plugins": [
 *     { "name": "网易云", "url": "https://…/netease.js", "version": "0.4.0" }
 *   ]
 * }
 * ```
 *
 * 订阅源本身只记录 URL；具体插件条目每次拉取时重新解析，因此订阅源内容更新后
 * [PluginManager.checkUpdates] 能直接看到新插件与新版本。
 */
@Serializable
data class Subscription(
    /** 订阅源 URL，作为主键。 */
    val url: String,
    /** 订阅源自带的名称，缺省用 host 兜底。 */
    val name: String = "",
    /** 最近一次成功拉取的时间。 */
    val addedAt: Long = System.currentTimeMillis(),
    val checkedAt: Long = 0L,
    /** 最近一次拉取到的插件条目数。 */
    val count: Int = 0,
)

/** 订阅 JSON 里的单个插件条目。 */
@Serializable
data class SubscriptionEntry(
    val name: String = "",
    val url: String,
    val version: String = "",
) {
    val displayName: String get() = name.ifBlank { url.substringAfterLast('/').removeSuffix(".js") }
}

/** 一个解析完成（可能来自缓存）的订阅源。 */
@Serializable
data class SubscriptionSource(
    @kotlinx.serialization.Transient val url: String = "",
    val name: String = "",
    val plugins: List<SubscriptionEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = plugins.isEmpty()
}

/** 批量安装 / 增量更新的统计结果。 */
data class BatchInstallResult(
    val total: Int = 0,
    val installed: List<PluginMeta> = emptyList(),
    val updated: List<PluginMeta> = emptyList(),
    val skipped: List<String> = emptyList(),
    val failed: List<Pair<String, String>> = emptyList(),
    /** 本次处理的订阅源（非订阅 JSON 时为空）。 */
    val subscriptions: List<Subscription> = emptyList(),
) {
    val successCount: Int get() = installed.size + updated.size
    val isSubscription: Boolean get() = subscriptions.isNotEmpty()

    /** 一行给 UI 的摘要（不带失败明细，明细走 [details]）。 */
    fun summary(): String = buildString {
        if (total == 0) {
            append("订阅源里没有插件")
            return@buildString
        }
        append("共 $total 个插件：")
        append("新装 ${installed.size}")
        if (updated.isNotEmpty()) append(" · 更新 ${updated.size}")
        if (skipped.isNotEmpty()) append(" · 已最新 ${skipped.size}")
        if (failed.isNotEmpty()) append(" · 失败 ${failed.size}")
    }

    /**
     * 失败明细（可展开详情里显示）。
     *
     * 每个失败插件一行「名字：原因」，最多 40 条 —— 一次性导入 57 个插件时，全量贴进去
     * 会把页面撑爆；超出部分只报数量。
     */
    fun details(): String? {
        if (failed.isEmpty()) return null
        return buildString {
            failed.take(40).forEach { (name, msg) ->
                append("· ").append(name).append("：").append(msg.lineSequence().first()).append('\n')
            }
            if (failed.size > 40) append("· …另有 ${failed.size - 40} 个\n")
        }.trimEnd()
    }
}

/** 一次「检查更新」的总体结果。 */
data class BatchUpdateResult(
    val checked: Int = 0,
    val updated: List<PluginMeta> = emptyList(),
    val failed: List<Pair<String, String>> = emptyList(),
    /** 本轮新发现的插件（订阅源里有、本地没装的）。 */
    val discovered: List<SubscriptionEntry> = emptyList(),
) {
    fun summary(): String = buildString {
        append("检查 $checked 个插件：更新 ${updated.size}")
        if (discovered.isNotEmpty()) append(" · 新增可装 ${discovered.size}")
        if (failed.isNotEmpty()) append(" · 失败 ${failed.size}")
    }
}
