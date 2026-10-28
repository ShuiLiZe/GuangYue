package com.guangyue.core.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 插件导出元信息 → [PluginMeta] 的宽松解析。
 *
 * 设计要点（这是修复「导入插件全部失败」的核心）：
 *
 * 1. **只认 JSON 文本**。JS 侧用 `JSON.stringify` 把 `platform / version / author / srcUrl /
 *    supportedSearchType / hints / primaryKey / cacheControl` 等字段整体序列化成**一个字符串**，
 *    Kotlin 侧再用 kotlinx.serialization（`isLenient + ignoreUnknownKeys +
 *    coerceInputValues`）解析。全程不经过 QuickJS 的 `TypeConverter`，
 *    因此不会出现 `No such type converter to convert 'boolean ...' to 'java.lang.String'`
 *    （那个异常来自 quickjs-kt 把 JS 返回值按 Kotlin 目标类型做反射转换）。
 * 2. **字段可缺**。缺 `platform` 时由文件夹名兜底（[fallbackPlatform]），缺 `version` 记
 *    `0.0.0`，缺 `cacheControl` 记 `no-cache`，缺 `supportedSearchType` 记 `music`。
 * 3. **类型不符就转字符串**。`version: 1`、`author: 42`、`cacheControl: 0` 之类一律取
 *    JSON 原文（`1` → `"1"`）；数组逐项转字符串并丢弃空项；布尔/数字出现在「字符串字段」
 *    位置时不会让整条元信息报废。
 * 4. **绝不抛**。[decode] 只在 JSON 根本不是对象时才返回失败原因，其余情况一律给出可用的
 *    [PluginMeta]，把「哪个字段被降级」写进 [Decoded.warnings]。
 */
object PluginMetaCodec {

    /** 元信息 DTO：全部字段可缺、全部字段可被标量顶替。 */
    @Serializable
    private data class MetaDto(
        @SerialName("platform") val platform: JsonElement? = null,
        @SerialName("name") val name: JsonElement? = null,
        @SerialName("version") val version: JsonElement? = null,
        @SerialName("srcUrl") val srcUrl: JsonElement? = null,
        @SerialName("author") val author: JsonElement? = null,
        @SerialName("description") val description: JsonElement? = null,
        @SerialName("iconUrl") val iconUrl: JsonElement? = null,
        @SerialName("icon") val icon: JsonElement? = null,
        @SerialName("cacheControl") val cacheControl: JsonElement? = null,
        @SerialName("primaryKey") val primaryKey: JsonElement? = null,
        @SerialName("supportedSearchType") val supportedSearchType: JsonElement? = null,
        @SerialName("supportedQualities") val supportedQualities: JsonElement? = null,
        @SerialName("supportedQuality") val supportedQuality: JsonElement? = null,
        @SerialName("hints") val hints: JsonElement? = null,
        @SerialName("functions") val functions: JsonElement? = null,
    )

    /** [decode] 的结果：要么是元信息，要么是一句中文失败原因。 */
    data class Decoded(
        val meta: PluginMeta?,
        val error: String?,
        val warnings: List<String> = emptyList(),
    ) {
        val isOk: Boolean get() = meta != null
    }

    /**
     * 把 `readMetaJson()` 给出的 JSON 文本解析成 [PluginMeta]。
     *
     * @param source 插件来源（URL 或本地路径），写进 [PluginMeta.source]
     * @param sizeBytes 源码字节数
     * @param fallbackPlatform `platform` 缺失时使用的名字（通常是订阅里的插件名或文件名）
     */
    fun decode(
        json: String,
        source: String,
        sizeBytes: Long = 0L,
        fallbackPlatform: String? = null,
    ): Decoded {
        val obj: JsonObject = Json2.parse(json)?.asObj()
            ?: return Decoded(
                meta = null,
                error = "读不到插件导出的元信息（module.exports 不是对象，或返回值不是 JSON）",
            )

        val dto = runCatching { Json2.json.decodeFromJsonElement(MetaDto.serializer(), obj) }.getOrNull()
            ?: MetaDto()

        val warnings = mutableListOf<String>()

        val platform = text(dto.platform, "platform", warnings)
            ?: cleanPlatform(fallbackPlatform)
            ?: return Decoded(meta = null, error = "插件没有声明 platform 字段")

        val functions = stringSet(dto.functions)

        // supportedSearchType 可能是数组、单个字符串，也可能是写错形状的对象；
        // 后两种都不该把整条元信息带崩，只退到默认的 music。
        val searchTypes = stringList(dto.supportedSearchType)
            .ifEmpty {
                // 老插件可能只写一个 supportedSearchType: 'music'
                val raw = dto.supportedSearchType
                if (raw is JsonPrimitive && raw.isString) {
                    listOf(raw.content.trim()).filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            }
            .ifEmpty { listOf(SearchType.MUSIC) }

        val qualities = stringList(dto.supportedQualities) +
            stringList(dto.supportedQuality) +
            text(dto.supportedQuality, "supportedQuality", null)?.let { listOf(it) }.orEmpty()

        /** 额外键（hints / primaryKey）不参与业务，但必须能被读走而不炸。 */
        readExtra(obj, "hints")
        readExtra(obj, "primaryKey")

        val meta = PluginMeta(
            id = "",
            platform = platform,
            name = text(dto.name, "name", warnings) ?: platform,
            version = text(dto.version, "version", warnings) ?: "0.0.0",
            srcUrl = text(dto.srcUrl, "srcUrl", warnings),
            author = text(dto.author, "author", warnings),
            description = text(dto.description, "description", warnings),
            iconUrl = text(dto.iconUrl, "iconUrl", warnings) ?: text(dto.icon, "icon", warnings),
            supportedSearchType = searchTypes.distinct(),
            supportedQualities = qualities.distinct(),
            cacheControl = text(dto.cacheControl, "cacheControl", warnings) ?: "no-cache",
            functions = functions,
            source = source,
            sizeBytes = sizeBytes,
        )
        return Decoded(meta = meta, error = null, warnings = warnings)
    }

    /**
     * 把任意 [JsonElement] 压成字符串：字符串取内容，数字/布尔取 JSON 原文，对象/数组取紧凑
     * JSON。`null` 与空串返回 `null`。
     */
    fun text(value: JsonElement?, field: String, warnings: MutableList<String>? = null): String? {
        if (value == null) return null
        val p = value as? JsonPrimitive
            ?: run {
                warnings?.add("$field 不是字符串，已转成文本")
                return value.toString().takeIf { it.isNotBlank() && it != "null" }
            }
        if (p.toString() == "null") return null
        if (!p.isString) warnings?.add("$field 不是字符串（${p.content}），已按文本读取")
        return p.content.trim().ifEmpty { null }
    }

    /** 数组 → List<String>；字符串 → 单元素列表；其余丢弃。 */
    fun stringList(value: JsonElement?): List<String> {
        val arr = value.asArr() ?: return emptyList()
        return arr.mapNotNull { item ->
            val p = item as? JsonPrimitive ?: return@mapNotNull null
            if (p.toString() == "null") null else p.content.trim().ifEmpty { null }
        }
    }

    fun stringSet(value: JsonElement?): Set<String> = stringList(value).toSet()

    /** 只读不用的额外键：确认能取出来，取不出也不报错。 */
    fun readExtra(obj: JsonObject, key: String): String? = obj[key]?.let { r ->
        when (r) {
            is JsonPrimitive -> r.content
            else -> r.toString()
        }
    }

    /** 平台名兜底：订阅里的显示名 / 文件名（去掉 `.js`）都行，去掉空白与斜杠。 */
    fun cleanPlatform(raw: String?): String? {
        val s = raw?.trim()?.removeSuffix(".js")?.removeSuffix(".JS")?.trim()
        if (s.isNullOrEmpty()) return null
        val cleaned = s.replace(Regex("[/\\\\\\s]+"), "-").take(48)
        return cleaned.ifEmpty { null }
    }
}
