package com.guangyue.feature.library.screen

import com.guangyue.core.plugin.PluginMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件图标的候选链（0.0.9 a 第 3 项）。
 *
 * 验收点：插件列表里的图标必须是**该音源自己的**图标，不能是 GitHub 的 favicon，
 * 也不能因为 `srcUrl` 全托管在 raw.githubusercontent.com 就退化成同一张图。
 */
class PluginIconUrlsTest {

    private fun plugin(
        platform: String,
        name: String = platform,
        iconUrl: String? = null,
        srcUrl: String? = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/x.plugin.js",
    ) = PluginMeta(
        id = "$platform-1",
        platform = platform,
        name = name,
        version = "1.0.0",
        srcUrl = srcUrl,
        iconUrl = iconUrl,
        installedAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun kugouUsesKugouSiteIcon() {
        val urls = pluginIconUrls(plugin("kugou", "酷狗音乐"))
        assertEquals(listOf("https://www.kugou.com/favicon.ico"), urls)
    }

    @Test
    fun fiveSourcesEachGetTheirOwnIcon() {
        val icons = listOf("kugou", "kuwo", "qq", "netease", "qishui").associateWith {
            pluginIconUrls(plugin(it)).firstOrNull()
        }
        assertEquals(icons.keys.count { icons[it] != null }, icons.size)
        assertEquals(icons.size, icons.values.toSet().size) // 5 个源 5 个互不相同的图标
    }

    @Test
    fun githubHostedSrcUrlNeverBecomesTheIcon() {
        val urls = pluginIconUrls(plugin("djcsj", "DJ串烧集"))
        assertTrue(urls.none { it.contains("githubusercontent.com") || it.contains("github.io") })
    }

    @Test
    fun metadataIconWinsOverPlatformIcon() {
        val urls = pluginIconUrls(
            plugin("kugou", "酷狗音乐", iconUrl = " https://cdn.example.com/kugou.png "),
        )
        assertEquals("https://cdn.example.com/kugou.png", urls.first())
        assertEquals("https://www.kugou.com/favicon.ico", urls[1])
    }

    @Test
    fun blankMetadataIconIsIgnored() {
        val urls = pluginIconUrls(plugin("qq", "QQ音乐", iconUrl = "   "))
        assertEquals(listOf("https://y.qq.com/favicon.ico"), urls)
    }

    @Test
    fun unknownPlatformFallsBackToPlaceholder() {
        val urls = pluginIconUrls(plugin("some-unknown-source", "某某音源"))
        assertTrue(urls.isEmpty()) // 取不到 → 首字占位
    }

    @Test
    fun ownSrcHostStillWorksAsLastResort() {
        val urls = pluginIconUrls(
            plugin("some-unknown-source", "自建源", srcUrl = "https://music.example.com/plugin.js"),
        )
        assertEquals(listOf("https://music.example.com/favicon.ico"), urls)
        assertFalse(urls.isEmpty())
    }

    @Test
    fun neteaseRadioReusesNeteaseSiteIcon() {
        assertEquals(
            "https://music.163.com/favicon.ico",
            pluginIconUrls(plugin("neteaseradio", "网易云电台")).firstOrNull(),
        )
    }
}
