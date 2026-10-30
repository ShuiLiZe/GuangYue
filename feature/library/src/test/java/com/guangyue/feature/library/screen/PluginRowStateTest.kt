package com.guangyue.feature.library.screen

import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.PluginPlatformIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件管理页「状态」文案 + 图标候选的装配（0.0.9 b）。
 *
 * core:plugin 那边已经单测了地址表本身；这里守两件 UI 侧的事：
 * 1. 播放实测（洛雪兜底成功）后 health 会被写成 ok，列表必须显示「可用」；
 * 2. 每一行拿到的图标候选非空且不含代码托管站，避免又退回首字占位。
 */
class PluginRowStateTest {

    private fun plugin(platform: String, name: String, health: String, note: String? = null) = PluginMeta(
        id = "$platform-1",
        platform = platform,
        name = name,
        version = "1.9.24",
        srcUrl = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/x.plugin.js",
        health = health,
        healthNote = note,
        installedAt = 0L,
        updatedAt = 0L,
    )

    /** 0.0.9 b：Hark 点名的「能出声但状态没更新」的两个源。 */
    @Test
    fun playedPluginShowsAvailable() {
        listOf("kuwo" to "酷我音乐", "qishui" to "汽水音乐").forEach { (platform, name) ->
            val meta = plugin(platform, name, "ok", "播放实测：洛雪取链源「Free listen」")
            assertEquals("可用", meta.healthLabel)
            assertTrue(meta.isHealthy)
        }
    }

    @Test
    fun staleNoMediaStillReadsAsNoChain() {
        assertEquals("取不到链", plugin("kugou", "酷狗音乐", "no-media").healthLabel)
    }

    @Test
    fun eachMainstreamRowHasIconCandidates() {
        val rows = listOf(
            "kugou" to "酷狗音乐",
            "kuwo" to "酷我音乐",
            "qq" to "QQ音乐",
            "netease" to "网易云音乐",
            "qishui" to "汽水音乐",
            "migu" to "咪咕音乐",
            "fanqie" to "番茄畅听",
        )
        rows.forEach { (platform, name) ->
            val urls = PluginPlatformIcons.candidatesFor(plugin(platform, name, "ok"))
            assertTrue("$name 没有图标候选", urls.isNotEmpty())
            assertTrue("$name 的候选落到了代码托管站：$urls", urls.none(PluginPlatformIcons::isCodeHost))
        }
    }
}
