package com.guangyue.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音源图标候选地址的纯逻辑单测（0.0.9 b）。
 *
 * 只断言「哪个源、什么顺序、哪些 URL」，不联网；真机上能不能取到由
 * feature:library 的 PluginIconLoader 探测（logcat TAG=GYICON），实测记录见
 * docs/ui-0.0.9/b/icon-probe.txt。
 */
class PluginPlatformIconsTest {

    private fun plugin(
        platform: String,
        name: String = platform,
        iconUrl: String? = null,
        srcUrl: String = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/x.plugin.js",
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

    // —— 主流源：每个源都要有站点自己的真实图标 ——

    @Test
    fun kugouUsesKugouSiteIcon() {
        // 酷狗主站 favicon 对 App 直连回 403（真机实测），改用同站群的 t1.kugou.com。
        val urls = PluginPlatformIcons.candidatesFor(plugin("kugou", "酷狗音乐"))
        assertEquals(listOf("https://t1.kugou.com/favicon.ico"), urls)
    }

    @Test
    fun mainstreamSourcesEachGetTheirOwnIcon() {
        val six = listOf("kugou", "kuwo", "qq", "netease", "qishui", "migu", "fanqie")
        val icons = six.associateWith { PluginPlatformIcons.candidatesFor(plugin(it)).firstOrNull() }
        assertTrue(icons.values.all { it != null })
        assertEquals(six.size, icons.values.toSet().size) // 每个源一个独立图标，不许共用
    }

    @Test
    fun fanqieHasAnIconEvenThoughItsSiteHasNone() {
        // fanqienovel.com/favicon.ico 真机 200 但 0 字节，所以直接给字节 CDN 上那张图。
        val url = PluginPlatformIcons.urlFor("fanqie")
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://"))
        assertEquals(listOf(url), PluginPlatformIcons.candidatesFor(plugin("fanqie", "番茄畅听")))
    }

    @Test
    fun qishuiDoesNotPointAtDouyinHomepageFavicon() {
        // a 版用 https://www.douyin.com/favicon.ico：那地址 302 到 CDN 且经常 404。
        val url = PluginPlatformIcons.urlFor("qishui")
        assertNotNull(url)
        assertFalse(url!!.contains("www.douyin.com"))
    }

    @Test
    fun mainstreamIconsAreAllHttps() {
        listOf("kugou", "kuwo", "qq", "netease", "qishui", "migu", "fanqie", "bilibili").forEach { platform ->
            val url = PluginPlatformIcons.urlFor(platform)
            assertNotNull("$platform 没有图标", url)
            assertTrue("$platform 的图标不是 https：$url", url!!.startsWith("https://"))
        }
    }

    @Test
    fun neteaseRadioReusesNeteaseSiteIcon() {
        val urls = PluginPlatformIcons.candidatesFor(plugin("neteaseradio", "网易云电台"))
        assertEquals(listOf("https://music.163.com/favicon.ico"), urls)
    }

    // —— 候选顺序：插件自带 > 平台映射 > 源站 host ——

    @Test
    fun metadataIconWinsOverPlatformIcon() {
        val urls = PluginPlatformIcons.candidatesFor(
            plugin("kugou", "酷狗音乐", iconUrl = " https://cdn.example.com/kugou.png "),
        )
        assertEquals("https://cdn.example.com/kugou.png", urls.first())
        assertEquals("https://t1.kugou.com/favicon.ico", urls[1])
    }

    @Test
    fun blankMetadataIconIsIgnored() {
        val urls = PluginPlatformIcons.candidatesFor(plugin("qq", "QQ音乐", iconUrl = "   "))
        assertEquals(listOf("https://y.qq.com/favicon.ico"), urls)
    }

    @Test
    fun unknownPlatformFallsBackToPlaceholder() {
        val urls = PluginPlatformIcons.candidatesFor(plugin("some-unknown-source", "某某音源"))
        assertTrue(urls.isEmpty()) // 取不到 → 首字占位
    }

    @Test
    fun ownSrcHostStillWorksAsLastResort() {
        val urls = PluginPlatformIcons.candidatesFor(
            plugin("some-unknown-source", "自建源", srcUrl = "https://music.example.com/plugin.js"),
        )
        assertEquals(listOf("https://music.example.com/favicon.ico"), urls)
        assertFalse(urls.isEmpty())
    }

    @Test
    fun githubHostedSrcUrlNeverBecomesTheIcon() {
        // 插件脚本全托管在 GitHub Pages，/favicon.ico 是 GitHub 的图标，会张冠李戴。
        val urls = PluginPlatformIcons.candidatesFor(plugin("djcsj", "DJ串烧集"))
        assertTrue(urls.none { it.contains("githubusercontent.com") || it.contains("github.io") })
    }

    @Test
    fun codeHostsAreRecognised() {
        assertTrue(PluginPlatformIcons.isCodeHost("https://raw.githubusercontent.com/a/b.js"))
        assertTrue(PluginPlatformIcons.isCodeHost("https://hebijunge.github.io/x.js"))
        assertTrue(PluginPlatformIcons.isCodeHost("https://gitee.com/a/b.js"))
        assertFalse(PluginPlatformIcons.isCodeHost("https://www.kugou.com/favicon.ico"))
        assertFalse(PluginPlatformIcons.isCodeHost("https://y.qq.com/favicon.ico"))
    }

    @Test
    fun unknownPlatformUrlIsNull() {
        assertNull(PluginPlatformIcons.urlFor("some-unknown-source"))
    }

    @Test
    fun everyMappedIconIsAnAbsoluteHttpsUrl() {
        PluginPlatformIcons.byPlatform.forEach { (platform, url) ->
            assertTrue("$platform 的图标不是 https：$url", url.startsWith("https://"))
            assertTrue("$platform 的图标像占位：$url", url.length > "https://a.b/".length)
        }
    }

    @Test
    fun hiddenOrUnusablePlatformsFallBackInsteadOfGuessing() {
        // 「快音」这类没有可用站点图标的源，不要瞎编 URL，留空走首字占位。
        assertTrue(PluginPlatformIcons.candidatesFor(plugin("快音", "快音")).isEmpty())
    }

    // —— 状态：播放实测成功后就是「可用」 ——

    @Test
    fun playbackMeasuredHealthIsOk() {
        assertEquals("可用", plugin("kuwo").copy(health = PluginHealth.OK).healthLabel)
        assertEquals("取不到链", plugin("qishui").copy(health = PluginHealth.NO_MEDIA).healthLabel)
    }
}
