package com.guangyue.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.0.9 a-1：界面文案不许再出现异常类名与插件栈帧。
 *
 * 番茄畅听（platform=fanqie）搜索失败时界面上出现的是
 * `com.guangyue.core.plugin.runtime.PluginRuntimeException: 搜索失败 code=6000 at apiSearch (plugin.js:2:17847) at ...`
 * ——用户看不懂，也帮不上忙。这里锁死收口函数的两条硬底线：
 * 输出**不含** `com.guangyue`、**不含** ` at `。
 */
class PluginFailureTextTest {

    /** 真实那种贴到界面上的原文。 */
    private val rawFanqie =
        "com.guangyue.core.plugin.runtime.PluginRuntimeException: 搜索失败 code=6000\n" +
            "at apiSearch (plugin.js:2:17847)\n" +
            "at _0x3846c6 (plugin.js:2:403919)\n" +
            "at Object.call (plugin.js:2:118)"

    @Test
    fun `raw plugin exception loses class name and stack frames`() {
        val out = PluginFailureText.reason(rawFanqie)
        assertFalse(out, out.contains("com.guangyue"))
        assertFalse(out, out.contains(" at "))
        assertTrue(out, out.contains("错误码"))
        assertEquals("源站返回错误码 6000", out)
    }

    @Test
    fun `search failure sentence is one readable line`() {
        val out = PluginFailureText.searchFailed("番茄", rawFanqie)
        assertFalse(out, out.contains("com.guangyue"))
        assertFalse(out, out.contains(" at "))
        assertFalse(out, out.contains("PluginRuntimeException"))
        assertTrue(out, out.startsWith("番茄 音源搜索失败："))
        assertTrue(out, out.endsWith("，换个音源试试"))
    }

    @Test
    fun `plugin written chinese reason survives`() {
        val out = PluginFailureText.reason("酷狗取链失败：未取得播放链接 at _0x3846c6 (plugin.js:2:403919)")
        assertFalse(out, out.contains(" at "))
        assertTrue(out, out.contains("未取得播放链接"))
    }

    @Test
    fun `bare stack frames collapse to the generic hint`() {
        val out = PluginFailureText.reason("at apiSearch (plugin.js:2:17847)")
        assertFalse(out, out.contains(" at "))
        assertFalse(out, out.contains("plugin.js"))
        assertEquals(PluginFailureText.GENERIC, out)
    }

    @Test
    fun `pure english exception never reaches the ui`() {
        val out = PluginFailureText.reason("com.dokar.quickjs.QuickJsException: Error: qishui sign 全部端点失败（3 个）")
        assertFalse(out, out.contains("com."))
        assertFalse(out, out.contains(" at "))
        assertEquals("全部端点失败（3 个）", out)
    }

    @Test
    fun `blank input becomes the generic hint`() {
        assertEquals(PluginFailureText.GENERIC, PluginFailureText.reason(null))
        assertEquals(PluginFailureText.GENERIC, PluginFailureText.reason("   "))
        assertEquals(
            PluginFailureText.GENERIC,
            PluginFailureText.reason("com.guangyue.core.plugin.runtime.PluginRuntimeException:\n"),
        )
    }

    @Test
    fun `keyword failures keep their dedicated wording`() {
        assertEquals("连不上源站（网络或域名失效）", PluginFailureText.reason("java.net.UnknownHostException: api.example.com"))
        assertEquals("源站响应超时", PluginFailureText.reason("Request timeout after 30000ms"))
        assertEquals("插件内部报错（函数不存在）", PluginFailureText.reason("TypeError: x.y is not a function"))
        assertEquals("插件返回的异步结果没结算", PluginFailureText.reason("Promise that has not settled"))
    }

    @Test
    fun `install and health sentences share the same wording`() {
        val install = PluginFailureText.installFailed("com.guangyue.core.plugin.runtime.PluginRuntimeException: 下载失败")
        assertTrue(install, install.startsWith("安装失败："))
        assertFalse(install, install.contains("com.guangyue"))
        assertFalse(install, install.contains(" at "))

        val health = PluginFailureText.healthFailed(rawFanqie)
        assertTrue(health, health.startsWith("体检失败："))
        assertFalse(health, health.contains("com.guangyue"))
        assertFalse(health, health.contains(" at "))

        // firstSentence 与 reason 必须同源，插件页行内标签才不会另一种说法。
        assertEquals(PluginFailureText.reason(rawFanqie), PluginFailureText.firstSentence(rawFanqie))
    }

    @Test
    fun `long chinese reason is truncated rather than dumped`() {
        val long = "源站返回了一长串没人看得懂的东西" + "啊".repeat(200)
        val out = PluginFailureText.reason(long)
        assertFalse(out, out.contains("com.guangyue"))
        assertFalse(out, out.contains(" at "))
        assertTrue(out, out.length <= 61)
    }
}