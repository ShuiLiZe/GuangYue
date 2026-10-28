package com.guangyue.core.player

/**
 * Where a media item comes from. Kept in :core:player so the session and the resolver can
 * share it without touching the plugin module.
 */
data class MediaOrigin(
    val pluginId: String,
    /** Library-level key of the track (`platform:songId`), used to fetch its payload. */
    val trackKey: String = "",
    val quality: Quality = Quality.STANDARD,
    /** Opaque MusicFree payload the plugin understands (musicItem object). */
    val extra: Map<String, String> = emptyMap(),
    /** Fallback: a playable url handed over directly (demo plugin, local files). */
    val directUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /**
     * 当前插件声明支持的音质（wire 值）。
     * 空 = 插件没声明，音质弹窗按内置全集渲染。
     */
    val availableQualities: List<String> = emptyList(),
) {
    fun toExtraBundle(): android.os.Bundle = android.os.Bundle().apply {
        putString(KEY_PLUGIN_ID, pluginId)
        putString(KEY_QUALITY, quality.wire)
        putString(KEY_DIRECT_URL, directUrl)
        putStringArray(KEY_QUALITIES, availableQualities.toTypedArray())
        putString(KEY_EXTRA_JSON, ioJson(extra))
        putString(KEY_HEADERS_JSON, ioJson(headers))
    }

    companion object {
        const val KEY_PLUGIN_ID = "guangyue.pluginId"
        const val KEY_QUALITY = "guangyue.quality"
        const val KEY_DIRECT_URL = "guangyue.directUrl"
        const val KEY_EXTRA_JSON = "guangyue.extra"
        const val KEY_HEADERS_JSON = "guangyue.headers"
        const val KEY_QUALITIES = "guangyue.qualities"

        private fun ioJson(map: Map<String, String>): String =
            map.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "\"" + escape(k) + "\":\"" + escape(v) + "\""
            }

        private fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }

        fun fromBundle(bundle: android.os.Bundle?): MediaOrigin? {
            if (bundle == null) return null
            val id = bundle.getString(KEY_PLUGIN_ID) ?: return null
            return MediaOrigin(
                pluginId = id,
                quality = Quality.fromWire(bundle.getString(KEY_QUALITY)),
                directUrl = bundle.getString(KEY_DIRECT_URL)?.takeIf { it.isNotBlank() },
                extra = parseFlatJson(bundle.getString(KEY_EXTRA_JSON)),
                headers = parseFlatJson(bundle.getString(KEY_HEADERS_JSON)),
                availableQualities = bundle.getStringArray(KEY_QUALITIES)?.toList().orEmpty(),
            )
        }

        /** Tiny flat-object reader (avoids depending on kotlinx-serialization here). */
        private fun parseFlatJson(json: String?): Map<String, String> {
            if (json.isNullOrBlank() || json.length < 2) return emptyMap()
            val body = json.trim().removePrefix("{").removeSuffix("}")
            if (body.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            var i = 0
            val n = body.length
            while (i < n) {
                while (i < n && (body[i] == ',' || body[i].isWhitespace())) i++
                if (i >= n || body[i] != '"') break
                val kb = StringBuilder()
                i++
                while (i < n && body[i] != '"') {
                    if (body[i] == '\\' && i + 1 < n) { kb.append(unescape(body, i)); i += 2 } else { kb.append(body[i]); i++ }
                }
                i++ // closing quote
                while (i < n && (body[i].isWhitespace() || body[i] == ':')) i++
                val vb = StringBuilder()
                if (i < n && body[i] == '"') {
                    i++
                    while (i < n && body[i] != '"') {
                        if (body[i] == '\\' && i + 1 < n) { vb.append(unescape(body, i)); i += 2 } else { vb.append(body[i]); i++ }
                    }
                    i++
                } else {
                    while (i < n && body[i] != ',' && body[i] != '}') { vb.append(body[i]); i++ }
                }
                out[kb.toString()] = vb.toString().trim()
            }
            return out
        }

        private fun unescape(s: String, i: Int): Char = when (val c = s[i + 1]) {
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> if (i + 5 < s.length) s.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: '?' else '?'
            else -> c
        }
    }
}

/**
 * MusicFree quality ladder.
 *
 * 前四档是 MusicFree 既有约定（`low/standard/high/super`），后四档是各插件近年扩展出来的
 * 高清档位。插件不认识某个 wire 值时会自行降级，所以「多给一档」是安全的。
 *
 * [order] 用于「音质由低到高」排序与比较；[bitrateHint] 只用于 UI 副标题与下载文件命名，
 * 不代表真实码率。
 */
enum class Quality(val wire: String, val label: String, val order: Int, val bitrateHint: Int = 0) {
    LOW("low", "标准", 0, 128),
    STANDARD("standard", "高品", 1, 192),
    HIGH("high", "很高", 2, 320),
    SUPER("super", "无损", 3),
    LOSSLESS("lossless", "无损 FLAC", 4),
    HIRES("hires", "Hi-Res", 5),
    ATMOS("atmos", "全景声", 6),
    MASTER("master", "母带", 7);

    /** 下一档；已经到顶则回到最低档（UI 长按循环用）。 */
    fun next(): Quality = entries[(ordinal + 1) % entries.size]

    /** 向上一档 / 向下 [steps] 档，越界时夹在端点。 */
    fun up(): Quality = entries.getOrElse(ordinal + 1) { this }

    fun down(): Quality = entries.getOrElse(ordinal - 1) { this }

    /** 是否无损类（FLAC / Hi-Res / 母带 / 全景声）。 */
    val isLossless: Boolean get() = order >= LOSSLESS.order

    /** 副标题：弹窗里的一句人话说明。 */
    val subtitle: String
        get() = when (this) {
            LOW, STANDARD, HIGH -> "${bitrateHint}kbps"
            SUPER, LOSSLESS -> "无损 FLAC"
            HIRES -> "高解析 24bit"
            ATMOS -> "杜比全景声"
            MASTER -> "母带 录音室级"
        }

    companion object {
        /** 在一组候选档位里循环取下一档。 */
        fun List<Quality>.nextOf(current: Quality): Quality {
            if (isEmpty()) return current.next()
            val at = indexOf(current)
            return if (at < 0) first() else this[(at + 1) % size]
        }

        /** 按 [order] 从低到高排列，UI 的弹窗直接用这个顺序。 */
        val ladder: List<Quality> = entries.sortedBy { it.order }

        /** 与 [ladder] 等价的别名。 */
        val ALL: List<Quality> = ladder

        fun fromWire(v: String?): Quality = entries.firstOrNull { it.wire == v } ?: STANDARD

        /** 宽容解析：`flac` / `sq` / `96k` 之类的别名都映射到最近的档位。 */
        fun parse(v: String?): Quality {
            val key = v?.trim()?.lowercase() ?: return STANDARD
            entries.firstOrNull { it.wire == key }?.let { return it }
            return when (key) {
                "sq", "flac", "cd", "lossless", "lossless_flac" -> LOSSLESS
                "hr", "hi-res", "hires", "hires_lossless", "zq" -> HIRES
                "atmos", "dolby", "spatial", "全景声" -> ATMOS
                "master", "mq", "母带" -> MASTER
                "320", "high" -> HIGH
                "128", "low" -> LOW
                else -> STANDARD
            }
        }

        /** 插件声明的音质字符串列表 → 去重、去非法值、按档位从高到低排序。 */
        fun fromPluginList(raw: List<String>?): List<Quality> =
            raw.orEmpty()
                .mapNotNull { wire ->
                    entries.firstOrNull { it.wire.equals(wire.trim(), ignoreCase = true) }
                        ?: wire.trim().takeIf { it.isNotBlank() }?.let { parse(it) }
                }
                .distinct()
                .sortedWith(compareByDescending { it.order })
    }
}
