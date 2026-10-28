package com.guangyue.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复现并锁死 v0.0.1 的元信息崩溃：
 *
 * 真机上导入订阅里的 57 个插件全部失败，统一报
 * `No such type converter to convert 'boolean (Kotlin reflection is not available)' to
 * 'java.lang.String'`。根因是壳把插件导出的 `platform / version / author / cacheControl …`
 * 直接当 Kotlin `String` 读（quickjs-kt 会反射找 TypeConverter），插件把它们写成布尔、数字、
 * 数组时就炸；R8 去掉 Kotlin 反射后连「是个 Boolean」都认不出，于是同一条报错覆盖全部插件。
 *
 * 修复后的链路是：JS 侧 `JSON.stringify` → 字符串 → [PluginMetaCodec] 用 kotlinx.serialization
 * 宽松解析。下面这些用例只用字符串输入，所以既不需要 QuickJS，也不需要设备。
 */
class PluginMetaCodecTest {

    private fun decode(json: String, fallback: String? = null) =
        PluginMetaCodec.decode(json = json, source = "subscription://test", sizeBytes = 1024, fallbackPlatform = fallback)

    @Test
    fun `platform written as boolean is read as text instead of throwing`() {
        // 真机上第一条炸的就是这种：platform: true
        val decoded = decode(
            """
            {"platform":true,"name":"布尔插件","version":1,"cacheControl":false,
             "author":1024,"srcUrl":42,"supportedSearchType":"music",
             "supportedQualities":[320,128,"flac"],"hints":{"debug":true,"retries":3},
             "primaryKey":"id","functions":["search","getMediaSource"]}
            """.trimIndent()
        )

        assertNull(decoded.error)
        val meta = decoded.meta!!
        assertEquals("true", meta.platform)
        assertEquals("布尔插件", meta.name)
        assertEquals("1", meta.version)          // number → "1"
        assertEquals("false", meta.cacheControl) // boolean → "false"
        assertEquals("1024", meta.author)
        assertEquals("42", meta.srcUrl)
        assertEquals(listOf("music"), meta.supportedSearchType)
        assertEquals(listOf("320", "128", "flac"), meta.supportedQualities)
        assertEquals(setOf("search", "getMediaSource"), meta.functions)
        assertTrue("降级要留下痕迹", decoded.warnings.isNotEmpty())
    }

    @Test
    fun `numeric and array shaped meta keeps working`() {
        val numeric = decode(
            """
            {"platform":42,"version":1.5,"cacheControl":0,"author":3.14,
             "supportedQualities":320,"hints":[1,2,3],"primaryKey":["id","platform"],
             "functions":["search"]}
            """.trimIndent()
        )
        assertEquals("42", numeric.meta?.platform)
        assertEquals("1.5", numeric.meta?.version)
        assertEquals("0", numeric.meta?.cacheControl)
        assertEquals(listOf("music"), numeric.meta?.supportedSearchType) // 缺失 → 默认 music
        assertEquals(setOf("search"), numeric.meta?.functions)

        val array = decode(
            """
            {"platform":["list","b"],"name":["演示",2],"version":["1","2"],
             "cacheControl":["no-cache"],"srcUrl":["https://example.com/p.js"],
             "supportedSearchType":["music","album",7,null],
             "supportedQualities":["320",128,null,"flac"],
             "primaryKey":{"a":1},"functions":["search"]}
            """.trimIndent()
        )
        val arrayMeta = array.meta
        assertNotNull(arrayMeta)
        assertEquals("""["list","b"]""", arrayMeta!!.platform) // 数组整体转紧凑 JSON 文本
        // 数组形态的标量字段同样被压成文本，而不是让整条元信息失败
        assertEquals("""["no-cache"]""", arrayMeta.cacheControl)
        assertEquals(listOf("music", "album", "7"), arrayMeta.supportedSearchType)
        assertEquals(listOf("320", "128", "flac"), arrayMeta.supportedQualities)
        // 整条元信息不能被任一畸形字段带崩
        assertEquals("search", arrayMeta.functions.first())
    }

    @Test
    fun `missing fields fall back to sane defaults`() {
        val decoded = decode(
            """{"description":null,"supportedSearchType":{"all":true},"functions":["search"]}""",
            fallback = "示例插件.js",
        )
        val meta = decoded.meta
        assertNotNull(meta)
        assertEquals("示例插件", meta!!.platform) // 用订阅里的插件名兜底
        assertEquals("0.0.0", meta.version)
        assertEquals("no-cache", meta.cacheControl)
        assertEquals(meta.platform, meta.name)
        // 对象形态的 supportedSearchType 不参与搜索类型，退回默认 music
        assertEquals(listOf("music"), meta.supportedSearchType)
    }

    @Test
    fun `empty or non object json returns a chinese error not an exception`() {
        val blank = decode("{}")
        assertNull(blank.meta)
        assertNotNull(blank.error)

        val array = decode("[]")
        assertNull(array.meta)
        assertTrue(array.error!!.contains("元信息"))

        val garbage = decode("not json at all")
        assertNull(garbage.meta)
    }

    @Test
    fun `hints and primaryKey of any shape are readable`() {
        val obj = Json2.parse("""{"hints":{"debug":true},"primaryKey":["id"]}""")!!.asObj()!!
        assertNotNull(PluginMetaCodec.readExtra(obj, "hints"))
        assertNotNull(PluginMetaCodec.readExtra(obj, "primaryKey"))
        assertNull(PluginMetaCodec.readExtra(obj, "missing"))
    }

    @Test
    fun `cleanPlatform strips extension and whitespace`() {
        assertEquals("插件A", PluginMetaCodec.cleanPlatform(" 插件A.js "))
        assertEquals("a-b", PluginMetaCodec.cleanPlatform("a/b"))
        assertNull(PluginMetaCodec.cleanPlatform("   "))
    }
}
