package com.guangyue.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备上的插件体检（0.0.7）。
 *
 * 0.0.4 的体检只看「导入成功 + search 返回值不是 null」，43/57 被判可用，但装机后大量音源
 * 搜不到东西。这里改成真跑两步：
 *
 * ```
 * search("周杰伦") → 至少一条结果
 * getMediaSource(第一条) → 拿得到非空 url
 * ```
 *
 * 两步都过才算 [PluginHealth.OK]。只对**已启用**的插件跑，插件内部串行、插件之间并发
 * [CONCURRENCY]，避免一次把全部源站打一遍。
 */
@Singleton
class PluginHealthChecker @Inject constructor(
    private val bridge: MusicPluginBridge,
    private val manager: PluginManager,
) {

    data class Report(val id: String, val platform: String, val name: String, val health: String, val note: String?, val ms: Long)

    /** 体检一个插件，返回 (health, note)。 */
    suspend fun check(id: String, keyword: String = KEYWORD): Report = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val meta = manager.find(id)
        var platform = meta?.platform ?: id
        var health = PluginHealth.SEARCH_FAILED
        var note: String? = null
        try {
            val result = bridge.search(id, keyword, 1, SearchType.MUSIC)
            val first = result.items.firstOrNull()
            when {
                result.error != null && first == null -> {
                    health = PluginHealth.SEARCH_FAILED
                    note = short(result.error.orEmpty())
                }
                first == null -> {
                    health = PluginHealth.NO_RESULT
                    note = "搜「$keyword」没有结果"
                }
                else -> {
                    platform = first.platform.ifBlank { platform }
                    val url = bridge.getMediaSource(id, first.raw, first, QUALITY)?.url.orEmpty()
                    if (url.isBlank()) {
                        health = PluginHealth.NO_MEDIA
                        note = "「${first.title}」取不到播放地址"
                    } else {
                        health = PluginHealth.OK
                        note = null
                    }
                }
            }
        } catch (t: Throwable) {
            health = PluginHealth.SEARCH_FAILED
            note = short(t.message ?: t.javaClass.simpleName)
        }
        Report(id, platform, meta?.displayName ?: id, health, note, System.currentTimeMillis() - started)
    }

    /**
     * 体检所有已启用插件并把结果写回 [PluginManager]。
     *
     * @param markEnabled true 时同时按结果开关（OK 的留下、其余关掉）——「只启用体检可用的」一步到位版。
     */
    suspend fun checkAll(
        keyword: String = KEYWORD,
        markEnabled: Boolean = false,
        onProgress: (done: Int, total: Int, report: Report) -> Unit = { _, _, _ -> },
    ): List<Report> = withContext(Dispatchers.IO) {
        manager.load()
        val targets = manager.plugins.value.filter { it.enabled && it.error == null }.take(MAX_TARGETS)
        if (targets.isEmpty()) return@withContext emptyList()
        val gate = Semaphore(CONCURRENCY)
        val done = AtomicInteger(0)
        val total = targets.size
        val reports = coroutineScope {
            targets
                .map { plugin ->
                    async(Dispatchers.IO) {
                        val report = gate.withPermit { check(plugin.id, keyword) }
                        onProgress(done.incrementAndGet(), total, report)
                        report
                    }
                }
                .awaitAll()
        }
        manager.recordHealth(
            reports.associate { it.id to (it.health to it.note) },
            markEnabled = markEnabled,
        )
        reports
    }

    private fun short(raw: String): String {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        return when {
            text.contains("Promise that has not settled") -> "插件返回的异步结果没结算"
            text.contains("超时") || text.contains("InterruptedException") -> "插件超时没返回"
            text.contains("not a function") -> "插件内部报错（函数不存在）"
            text.contains("UnknownHost") || text.contains("Unable to resolve host") -> "连不上源站"
            else -> text.take(80)
        }
    }

    companion object {
        const val KEYWORD = "周杰伦"
        const val CONCURRENCY = 2
        const val MAX_TARGETS = 20
        /** `getMediaSource(musicItem, quality)` 的第二参数：体检只关心能不能拿到地址。 */
        const val QUALITY = "standard"
    }
}