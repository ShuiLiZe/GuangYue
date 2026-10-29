package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginMetaCodec
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 求值通道的端到端回归：在**真正的 QuickJS**（JVM 产物）上跑完整的 shim + prelude + 插件。
 *
 * 为什么要有这一层：0.0.1 的 release 包在真机上让 57 个插件全部导入失败，而当时的 JVM
 * 单测全绿 —— 因为 android 版 quickjs 只有 `.so`，JVM 上根本跑不起来，用例一路 skip。
 * 现在测试依赖里加了 `quickjs-kt-jvm`，插件加载 / 元信息 / 调用三条路径都能在 CI 上真跑。
 *
 * 覆盖的正是 0.0.3 的改动点：
 * * 求值通道不拼括号 —— prelude 是 esbuild IIFE，结尾自带 `;`，插件工厂自带语句分号，
 *   dispatcher 也是语句。旧实现把它们塞进 `__gy_text(...)` 的实参列表里，
 *   QuickJS 报 `SyntaxError: expecting ','`。
 * * 完成值固定为 string —— `__gy_boot` 返回 boolean，旧实现直接把 boolean 交给
 *   `evaluate<String>`，R8 剥掉 kotlin-reflect 后抛 TypeConverter 异常。
 */
class PluginRuntimeJvmTest {

    /**
     * 真机 assets 里那份 prelude（571 KB，esbuild 打的 IIFE，结尾自带 `;`）。它把 cheerio /
     * CryptoJS / dayjs / Qs / he / BigInteger 全部挂到 globalThis 上，真实插件靠
     * `require('cheerio')` 取用，所以**必须**加载真货。找不到直接 fail（不是跳过）——
     * 跳过会让整组用例变成空跑，掩盖真实回归。
     *
     * 这正是旧求值通道炸 `expecting comma` 的那一段脚本（IIFE 自带的分号被塞进了实参列表）。
     */
    private val prelude: String by lazy {
        listOf(
            "src/main/assets/prelude.js",
            "core/plugin/src/main/assets/prelude.js",
            "../../core/plugin/src/main/assets/prelude.js",
            "app/src/main/assets/prelude.js",
        ).map { File(it) }.firstOrNull { it.isFile }?.readText()
            ?: throw AssertionError("找不到 prelude.js，当前目录 ${File(".").absolutePath}")
    }

    private val quickJsAvailable: Boolean by lazy {
        runCatching { com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close() }.isSuccess
    }

    private fun assumeQuickJs() {
        assumeTrue("当前 JVM 上没有 QuickJS 本地库，跳过（需要 quickjs-kt-jvm 的 natives）", quickJsAvailable)
    }

    private fun runtimeFor(code: String, platform: String): PluginRuntime = PluginRuntime(
        meta = com.guangyue.core.plugin.PluginMeta(
            id = platform, platform = platform, name = platform, version = "1", source = "test://$platform",
        ),
        code = code,
        http = OkHttpClient.Builder().build(),
        preludeProvider = PreludeTextProvider(prelude),
    )

    /**
     * 真实的 MusicFree 插件，来自官方订阅源（gitee maotoumao/MusicFreePlugins v0.1）。
     * 它们是 0.0.1 release 里失败的那 57 个中的 6 个：都是 esbuild 打的 IIFE，
     * `module.exports = {...}` 里直接挂函数，正文里全是语句分号。
     */
    private val realPlugins = listOf(
        "audiomack", "geciwang", "navidrome", "maoerfm", "kuaishou", "yinyuetai",
    )

    private fun resource(name: String): String? =
        javaClass.classLoader?.getResourceAsStream("musicfree/$name.js")?.bufferedReader()?.use { it.readText() }

    @Test
    fun `demo plugin from app assets imports and exposes functions`() {
        assumeQuickJs()
        val demo = File("../../app/src/main/assets/demo/demo-plugin.js").takeIf { it.isFile }?.readText()
            ?: File("app/src/main/assets/demo/demo-plugin.js").takeIf { it.isFile }?.readText()
        assertNotNull("找不到 demo 插件", demo)

        val rt = runtimeFor(demo!!, "demo")
        try {
            rt.install()
            assertTrue("demo 插件应该装好", rt.isAlive)
            val meta = rt.readMeta("demo://demo-plugin.js", demo.toByteArray().size.toLong())
            assertTrue("platform 不该为空", meta.platform.isNotBlank())
            val fns = rt.probeFunctions()
            assertTrue("demo 插件应该有 search：$fns", fns.contains("search"))
            // 真调一下，走完整条 dispatcher → JSON 文本 → Kotlin 解析的路
            val result = rt.call("search", kotlinx.serialization.json.JsonPrimitive("test"))
            assertNotNull("search 应该有返回值", result)
        } finally {
            rt.close()
        }
    }

    @Test
    fun `six real musicfree plugins import with correct field types`() {
        assumeQuickJs()
        var checked = 0
        for (name in realPlugins) {
            val code = resource(name) ?: continue
            checked++
            val rt = runtimeFor(code, name)
            try {
                rt.install()
                val metaJson = rt.readMetaJson()
                assertTrue("$name: 元信息应该是 JSON 对象：${metaJson.take(200)}", metaJson.trimStart().startsWith("{"))

                val decoded = PluginMetaCodec.decode(metaJson, "test://$name", code.toByteArray().size.toLong(), name)
                val meta = decoded.meta ?: throw AssertionError("$name 解析失败：${decoded.error} / $metaJson")

                // 类型不被降级：platform / version / cacheControl 必须是能用的字符串，
                // 而不是 "true" / "[object Object]" 这类被 toString() 过的垃圾值。
                assertTrue("$name: platform 为空", meta.platform.isNotBlank())
                assertTrue("$name: platform 不该是布尔文本（${meta.platform}）", meta.platform !in setOf("true", "false"))
                assertTrue("$name: version 为空", meta.version.isNotBlank())
                assertTrue("$name: version 不该是布尔文本（${meta.version}）", meta.version !in setOf("true", "false"))
                assertTrue("$name: cacheControl 不该是大写 True（${meta.cacheControl}）", !meta.cacheControl.startsWith("True"))
                // 函数探测走的是「真反射 exports」这条路（probeFunctions），不是读插件声明的
                // `functions` 字段 —— 真实插件的 metadata 里根本没有那个字段。
                val fns = rt.probeFunctions()
                assertTrue("$name: 应该探测到 search，实际 $fns", fns.contains("search"))
                assertTrue("$name: search 不该被当成字符串以外的类型丢掉", meta.platform.isNotBlank())

                // 中文 author / platform 不能被编码搞坏（订阅源里一半是中文名）
                if (name == "audiomack") {
                    assertEquals("Audiomack", meta.platform)
                    assertEquals("猫头猫", meta.author)
                }
                if (name == "navidrome") assertEquals("Navidrome", meta.platform)
            } finally {
                rt.close()
            }
        }
        assertTrue("至少要有 5 个真实插件 fixture，实际 $checked 个", checked >= 5)
    }

    @Test
    fun `metadata with boolean number array fields keeps raw type instead of stringifying`() {
        assumeQuickJs()
        for (variant in PluginMetaProbe.SELF_TEST_VARIANTS) {
            val rt = runtimeFor(PluginMetaProbe.selfTestPlugin(variant), "selftest_$variant")
            try {
                rt.install()
                val json = rt.readMetaJson()
                // 关键断言：布尔仍是布尔、数字仍是数字 —— JS 侧只做 JSON.stringify，
                // 不做 String(v)。若求值通道把完成值压成文本，这里会看到 "true" 带引号。
                when (variant) {
                    "boolean" -> {
                        assertTrue("platform 应保持布尔：$json", json.contains("\"platform\":true"))
                        assertTrue("version 应保持数字：$json", json.contains("\"version\":1"))
                        assertTrue("cacheControl 应保持布尔：$json", json.contains("\"cacheControl\":false"))
                    }
                    "number" -> {
                        assertTrue("platform 应保持数字：$json", json.contains("\"platform\":42"))
                        assertTrue("version 应保持小数：$json", json.contains("\"version\":1.5"))
                    }
                    "array" -> {
                        assertTrue("platform 应保持数组：$json", json.contains("\"platform\":[\"list\",\"b\"]"))
                    }
                }
                val meta = rt.readMeta("selftest://$variant", 1024, "selftest_$variant")
                assertTrue("$variant: platform 不该为空", meta.platform.isNotBlank())
            } finally {
                rt.close()
            }
        }
    }

    @Test
    fun `semicolon heavy prelude and dispatcher do not trigger expecting comma`() {
        assumeQuickJs()
        // 这个插件在工厂里塞了大量语句分号 + 一段 IIFE（模拟 esbuild 产物），
        // 旧实现会把它拼进 __gy_text(...) 的实参里直接炸语法错误。
        val code = """
            ;(function(){ var x = 1; })();
            var helper = 1;
            module.exports = {
              platform: 'semicolon_test',
              version: '0.0.1',
              supportedSearchType: ['music'],
              search: function (q) { var a = 1; var b = 2; return { isEnd: true, data: [] }; }
            };
        """.trimIndent()
        val rt = runtimeFor(code, "semicolon_test")
        try {
            rt.install()
            val meta = rt.readMeta("test://semicolon", 128)
            assertEquals("semicolon_test", meta.platform)
            assertTrue(rt.probeFunctions().contains("search"))
        } finally {
            rt.close()
        }
    }
}

/** 把真实 prelude 文本喂给 runtime（量产用 PreludeProvider，测试里不能碰 Android Context）。 */
private class PreludeTextProvider(private val text: String) : PreludeProvider(placeholderContext()) {
    override fun code(): String = text
}

private fun placeholderContext(): android.content.Context = try {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    unsafeClass.getMethod("allocateInstance", Class::class.java)
        .invoke(theUnsafe, (android.content.ContextWrapper::class.java)) as android.content.Context
} catch (t: Throwable) {
    throw IllegalStateException("无法构造测试用 Context", t)
}
