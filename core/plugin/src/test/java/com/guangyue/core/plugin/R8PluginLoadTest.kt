package com.guangyue.core.plugin

import com.guangyue.core.plugin.runtime.ErrorDetail
import com.guangyue.core.plugin.runtime.PluginMetaProbe
import com.guangyue.core.plugin.runtime.PluginRuntimeException
import com.guangyue.core.plugin.runtime.PluginRuntimeHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 后的插件加载回归测试。
 *
 * 0.0.1 的 release 包在真机上 57 个插件全部导入失败，报
 * `No such type converter to convert 'boolean (Kotlin reflection is not available)'
 * to 'java.lang.String'`。JVM 单测不走 R8，所以一直没测出来。
 *
 * 这一点用**真实的 PluginRuntime** 端到端跑自写插件：JS 沙箱、require 垫片、axios 桥、
 * console、Buffer、async 方法、缺字段全部覆盖，只要任何一处退回 quickjs-kt 的
 * TypeConverter，这个用例就会失败。
 */
class R8PluginLoadTest {

    /**
     * QuickJS 本地库是 Android 的 `.so`（ELF），Windows/Linux-macOS 的 JVM 加载不了：
     * `QjsProbe` 实测 `UnsatisfiedLinkError: no quickjs in java.library.path`。
     * 没有本地库时这两个用例无法在 JVM 上真实跑起来，直接 skip，
     * 而不是让 CI 一直红 —— 真机 / 开了 R8 的构建上它们才有意义。
     * 判断方式：试着初始化 `QuickJs` 类，失败就 skip。
     */
    private val quickJsAvailable: Boolean by lazy {
        runCatching { com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close() }
            .isSuccess
    }

    private fun assumeQuickJs() {
        org.junit.Assume.assumeTrue(
            "QuickJS 本地库在当前 JVM 不可用（Android .so），跳过真实桥接用例",
            quickJsAvailable,
        )
    }

    /**
     * 形态刻意模仿常见 MusicFree 插件（字段名、可选字段、异步函数、缺字段、Buffer 用法），
     * 但完全不接触任何第三方平台：不发请求，只返回固定的假数据。
     */
    private val musicFreeStylePlugin = """
module.exports = {
  platform: 'gy_test',
  version: 1,
  author: 'GuangYue CI',
  srcUrl: 'https://example.invalid/gy_test.js',
  supportedSearchType: ['music', 'album'],
  hints: { debug: true, retries: 3, nested: { deep: [1, 2, 3] } },
  primaryKey: 'id',
  cacheControl: false,
  supportedQualities: [320, 128, 'flac'],

  search: function (query, page, type) {
    console.log('search called', query, page, { type: type });
    var buf = Buffer.from('54657374', 'base64');
    return {
      isEnd: false,
      data: [{
        id: 'song-1',
        title: 'test ' + query,
        artist: ['A', 'B'],
        album: null,
        duration: '00:03:12',
        extra: { lrc: false, score: 99.5, bufLen: buf.length, bufText: buf.toString() }
      }]
    };
  },

  getMediaSource: async function (item, quality) {
    return { url: 'https://example.invalid/' + item.id + '.mp3', headers: { Referer: 'x' } };
  },

  getLyric: function (item) {
    return { rawLrc: '[00:00]hello', translation: null };
  }
};
"""

    @Test
    fun `plugin with boolean number array nested object async and missing fields loads`() {
        assumeQuickJs()
        val rt = PluginRuntimeHarness.runtimeFor(musicFreeStylePlugin)
        try {
            // 1) 加载：module.exports 里有 boolean(number/数组/嵌套对象) —— 0.0.1 就是死在这一步
            rt.install()
            assertTrue("runtime 应该存活", rt.isAlive)

            // 2) 元信息：JS 侧 JSON.stringify → Kotlin 侧 kotlinx.serialization
            val metaJson = rt.readMetaJson()
            assertTrue("meta json 应该是对象：$metaJson", metaJson.trimStart().startsWith("{"))
            val meta = rt.readMeta(source = "https://example.invalid/gy_test.js", sizeBytes = 1024)
            assertEquals("gy_test", meta.platform)
            assertEquals("1", meta.version)

            // 3) 函数探测：缺字段的 getTopLists 不应出现
            val fns = rt.probeFunctions()
            assertTrue("应该识别 search：$fns", fns.contains("search"))
            assertTrue("应该识别 getMediaSource：$fns", fns.contains("getMediaSource"))
            assertTrue("没有实现的方法不应被识别：$fns", !fns.contains("getTopLists"))

            // 4) 普通同步方法 + console + Buffer
            val search = rt.call("search", kotlinx.serialization.json.JsonPrimitive("hello"))
            assertTrue("search 返回：$search", search.toString().contains("isEnd"))
            assertTrue("search 数据不对：$search", search.toString().contains("test hello"))
            assertTrue("Buffer 没生效：$search", search.toString().contains("bufText"))

            // 5) async 函数（已 settle 的 Promise 必须能同步取回）
            //    注意：插件里读的是 `item.id`，所以要传对象 `{id:'song-1'}`，
            //    传裸字符串会得到 `undefined.mp3`（0.0.2 时代没跑过这条 async 路径，一直没暴露）。
            val media = rt.call(
                "getMediaSource",
                kotlinx.serialization.json.buildJsonObject {
                    put("id", kotlinx.serialization.json.JsonPrimitive("song-1"))
                },
            )
            assertTrue("getMediaSource 返回：$media", media.toString().contains("song-1.mp3"))

            // 6) 未实现的函数 → Missing，而不是崩掉整个插件
            var missing = false
            try {
                rt.call("getTopLists")
            } catch (e: PluginRuntimeException.Missing) {
                missing = true
            }
            assertTrue("getTopLists 应该报 Missing", missing)
        } finally {
            rt.close()
        }
    }

    /** 四种「字段类型都不对」的变体：boolean / number / array / missing。 */
    @Test
    fun `metadata whose fields are not strings still loads`() {
        assumeQuickJs()
        for (variant in PluginMetaProbe.SELF_TEST_VARIANTS) {
            val rt = PluginRuntimeHarness.runtimeFor(
                PluginMetaProbe.selfTestPlugin(variant),
                platform = "selftest_$variant",
            )
            try {
                rt.install()
                val meta = rt.readMeta(
                    source = "selftest://$variant",
                    sizeBytes = 1024,
                    fallbackPlatform = "selftest_$variant",
                )
                assertTrue("变体 $variant 的 platform 不应为空", meta.platform.isNotBlank())
            } finally {
                rt.close()
            }
        }
    }

    /** 原始异常现场：类名 + message + 最多 15 行堆栈。 */
    @Test
    fun `error detail keeps original class message and stack`() {
        val boom = IllegalArgumentException("boom from plugin")
        val wrapped = PluginRuntimeException("插件加载失败", boom)
        val text = wrapped.detailText
        assertTrue("应含原始类名：$text", text.contains("java.lang.IllegalArgumentException"))
        assertTrue("应含原始 message：$text", text.contains("boom from plugin"))
        val stackCount = text.lines().count { it.trimStart().startsWith("at ") }
        assertTrue("堆栈行数应在 1..15 之间，实际 $stackCount", stackCount in 1..15)

        val r8Boom = RuntimeException(
            "No such type converter to convert 'boolean (Kotlin reflection is not available)' to 'java.lang.String'",
        )
        assertTrue(ErrorDetail.of(r8Boom).format().contains("No such type converter"))
    }
}
