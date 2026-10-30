package com.guangyue.core.plugin.lx

import kotlinx.serialization.Serializable

/**
 * 一个洛雪（LX Music）自定义源。
 *
 * 与 MusicFree 插件是两套完全不同的东西：MusicFree 插件导出 `module.exports`，
 * 洛雪自定义源只跟 `globalThis.lx` 打交道（`lx.on` 注册 `request` 事件、
 * `lx.send(EVENT_NAMES.inited, {sources})` 上报平台、`lx.request` 发 HTTP），
 * 而且它**只提供取播放链接**（`action: 'musicUrl'`），没有搜索。
 *
 * 因此广乐把它作为独立类型「取链源」：装多个也不会并发跑，播放时按启用顺序
 * 依次问，谁先给出链接就用谁。
 */
@Serializable
data class LxSource(
    val id: String,
    /** 脚本头注释里的 `@name`。 */
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val homepage: String? = null,
    /** 从链接安装时记下来源地址，方便「检查更新」。 */
    val srcUrl: String? = null,
    /** 用户开关：只有启用中的才会被问。 */
    val enabled: Boolean = false,
    val sortOrder: Int = 0,
    /** 脚本 `inited` 上报的平台键：tx / wy / kg / kw / mg。 */
    val platforms: List<String> = emptyList(),
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sizeBytes: Long = 0L,
    /** 加载失败时的中文原因；能跑就是 null。 */
    val error: String? = null,
    /** 体检结论，一行中文。 */
    val note: String? = null,
) {
    val displayName: String get() = name.ifBlank { id }

    /** 「QQ音乐 / 网易云 / 酷狗 / 酷我 / 咪咕」这类可读平台名，用于列表副标题。 */
    val platformLabel: String
        get() = platforms.mapNotNull { LxPlatforms.label(it) }.distinct()
            .joinToString(" / ")
            .ifBlank { "未上报平台" }

    val statusLabel: String get() = if (error == null) "可用" else "不可用"
}

/** 洛雪平台键 ↔ 广乐插件 platform 的双向映射。 */
object LxPlatforms {

    /** 洛雪那边认的五个源键。 */
    val ALL: List<String> = listOf("tx", "wy", "kg", "kw", "mg")

    private val LABELS = mapOf(
        "tx" to "QQ音乐",
        "wy" to "网易云",
        "kg" to "酷狗",
        "kw" to "酷我",
        "mg" to "咪咕",
    )

    fun label(key: String): String? = LABELS[key.lowercase()]

    /**
     * 广乐侧的 `MusicItem.platform` / `TrackEntity.platform` → 洛雪源键。
     *
     * 插件里写什么的都有（`qq`、`tx`、`qqmusic`、`netease`、`wy`、`163`……），
     * 这里做包容匹配；认不出来就返回 null（说明这条链路洛雪接不了）。
     */
    fun of(platform: String): String? {
        val p = platform.lowercase().replace("-", "").replace("_", "").replace(" ", "")
        return when {
            p.isEmpty() -> null
            p.contains("qq") || p == "tx" || p.contains("qqmusic") -> "tx"
            p.contains("netease") || p == "wy" || p == "163" || p.contains("cloudmusic") ->
                "wy"
            p.contains("kugou") || p == "kg" -> "kg"
            p.contains("kuwo") || p == "kw" -> "kw"
            p.contains("migu") || p == "mg" -> "mg"
            else -> null
        }
    }
}

/** 洛雪音质档（`info.type`）：128k / 320k / flac / flac24bit。 */
enum class LxQuality(val wire: String, val label: String) {
    LOW("128k", "128k"),
    HIGH("320k", "320k"),
    LOSSLESS("flac", "无损"),
    HIRES("flac24bit", "Hi-Res");

    companion object {
        fun parse(wire: String?): LxQuality? =
            entries.firstOrNull { it.wire.equals(wire?.trim(), ignoreCase = true) }

        fun labelOf(wire: String?): String = parse(wire)?.label ?: (wire ?: "")

        /**
         * 把播放器当前档位翻译成洛雪音质。
         *
         * 用户点了「无损 / Hi-Res」就照他要的档问；其余（标准 / 高品 / 很高）跟随
         * 设置里的默认音质 —— 洛雪只有 128k 与 320k 两档有损，多出来的档位没有意义。
         */
        fun pick(playerWire: String?, fallbackWire: String): String {
            val fb = parse(fallbackWire) ?: HIGH
            return when (playerWire?.lowercase()) {
                "super", "lossless" -> LOSSLESS.wire
                "hires", "master", "atmos" -> HIRES.wire
                else -> fb.wire
            }
        }
    }
}