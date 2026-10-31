package com.guangyue.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1：订阅线路换算 + 内容指纹的纯 JVM 单测（不联网、不需要 Android）。
 */
class SubscriptionRoutesTest {

    private val padaoMain = "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json"

    @Test
    fun defaultSubscriptionIsCommonSixOnPadaoLine() {
        assertEquals(padaoMain, SubscriptionRoutes.DEFAULT_URL)
        assertEquals(padaoMain, SubscriptionRoutes.defaultSubscriptionUrl())
        assertTrue(SubscriptionRoutes.defaultSubscriptionUrl().startsWith(SubscriptionRoutes.PADAO_PREFIX))
    }

    @Test
    fun threeLinesInMainThenBackupOrder() {
        val urls = SubscriptionRoutes.subscriptionUrls()
        assertEquals(3, urls.size)
        assertEquals(
            listOf(
                "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json",
                "https://hebijunge.github.io/musicfree-plugins/sub/githubio/main.json",
                "https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/sub/fastly/main.json",
            ),
            urls,
        )
    }

    @Test
    fun otherCategoriesOnlySwapFileName() {
        for (c in listOf("dj", "radio", "music", "all")) {
            val lines = SubscriptionRoutes.subscriptionUrlsFor(c)
            assertEquals(3, lines.size)
            assertEquals("sub/padao/$c.json", SubscriptionRoutes.relPathOf(lines[0]))
            assertTrue(lines[0].startsWith(SubscriptionRoutes.PADAO_PREFIX))
        }
        assertEquals("sub/padao/all.json", SubscriptionRoutes.relPathOf(SubscriptionRoutes.subscriptionUrlsFor("all")[0]))
        assertEquals(5, SubscriptionRoutes.Category.all.size)
        assertEquals("main", SubscriptionRoutes.Category.of("不认识").id)
    }

    @Test
    fun pluginUrlFallsBackAcrossLines() {
        // 主线路的插件地址 → 三条候选，自身线路排最前
        val candidates = SubscriptionRoutes.candidates(
            "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/qq-source.plugin.v1.9.26.js",
        )
        assertEquals(3, candidates.size)
        assertEquals(
            "https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/qq-source.plugin.v1.9.26.js",
            candidates[0],
        )
        assertEquals(
            "https://hebijunge.github.io/musicfree-plugins/plugins/qq-source.plugin.v1.9.26.js",
            candidates[1],
        )
        assertEquals(
            "https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/plugins/qq-source.plugin.v1.9.26.js",
            candidates[2],
        )
    }

    @Test
    fun backupLineUrlIsRewrittenToPrimaryFirst() {
        // 备用 2（jsDelivr）的地址：自身线路优先，再主 → 备
        val candidates = SubscriptionRoutes.candidates(
            "https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/plugins/kugou-source.plugin.v1.9.24.js",
        )
        assertEquals(3, candidates.size)
        assertTrue(candidates[0].startsWith(SubscriptionRoutes.FASTLY_PREFIX))
        assertTrue(candidates[1].startsWith(SubscriptionRoutes.PADAO_PREFIX))
        assertEquals(SubscriptionRoutes.Line.PADAO, SubscriptionRoutes.lineOfUrl(candidates[1]))
    }

    @Test
    fun rawDirectLinkIsTreatedAsPrimaryLine() {
        val raw = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/kuwo-source.plugin.v1.9.24.js"
        val candidates = SubscriptionRoutes.candidates(raw)
        // 三条线路优先（raw 直链容易超时，只作最后兜底）
        assertEquals(4, candidates.size)
        assertEquals(SubscriptionRoutes.Line.PADAO, SubscriptionRoutes.lineOfUrl(candidates[0]))
        assertEquals(raw, candidates.last())
    }

    @Test
    fun thirdPartyUrlIsNeverRewritten() {
        val mine = "https://example.com/my/sub/main.json"
        assertEquals(listOf(mine), SubscriptionRoutes.candidates(mine))
        assertNull(SubscriptionRoutes.relPathOf(mine))
        assertNull(SubscriptionRoutes.lineOfUrl(mine))
    }

    @Test
    fun legacySubscriptionIsRecognisedForMigration() {
        assertTrue(SubscriptionRoutes.isLegacy("https://hebijunge.github.io/musicfree-plugins/plugins.json"))
        assertTrue(SubscriptionRoutes.isLegacy("https://raw.githubusercontent.com/hebijunge/musicfree-plugins/plugins.json"))
        assertFalse(SubscriptionRoutes.isLegacy(SubscriptionRoutes.defaultSubscriptionUrl()))
        // 新的主线路地址本身也含 github.io 之外的域名，迁移不应误伤
        assertFalse(SubscriptionRoutes.isLegacy("https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json"))
    }

    @Test
    fun pluginFileNameIsStableAcrossLines() {
        val name = "qishui-source.plugin.v1.9.23.js"
        val urls = SubscriptionRoutes.pluginUrls(name)
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.substringAfterLast('/') == name })
    }
}

/**
 * P1：「版本号没变、内容变了」靠内容指纹（sha256）判——这份单测锁定工具类的语义。
 */
class PluginContentHashTest {

    @Test
    fun sha256OfUtf8Text() {
        // 空串与 "abc" 的 sha256 是标准值，用来锁死算法没被改坏
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            PluginContentHash.of(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PluginContentHash.of("abc"),
        )
    }

    @Test
    fun changedDetectsSameVersionRewrite() {
        val old = PluginContentHash.of("module.exports = { version: '1.9.26' };")
        val new = PluginContentHash.of("module.exports = { version: '1.9.26', extra: 1 };")
        assertTrue(PluginContentHash.changed(old, new))
        assertFalse(PluginContentHash.changed(old, old))
    }

    @Test
    fun changedIsCaseInsensitiveAndTolerantWhenMissing() {
        val h = PluginContentHash.of("abc")
        assertFalse(PluginContentHash.changed(h.uppercase(), h.lowercase()))
        // 本地没有记录（老版本装过的插件）→ 只要远端有内容就算「要更新一次」
        assertTrue(PluginContentHash.changed(null, h))
        assertTrue(PluginContentHash.changed("", h))
        // 远端拿不到指纹 → 不敢说变了
        assertFalse(PluginContentHash.changed(h, ""))
        assertFalse(PluginContentHash.changed(h, "   "))
    }
}