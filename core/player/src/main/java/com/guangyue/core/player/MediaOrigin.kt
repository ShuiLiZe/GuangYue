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
) {
    fun toExtraBundle(): android.os.Bundle = android.os.Bundle().apply {
        putString(KEY_PLUGIN_ID, pluginId)
        putString(KEY_QUALITY, quality.wire)
        putString(KEY_DIRECT_URL, directUrl)
        putString(KEY_EXTRA_JSON, ioJson(extra))
        putString(KEY_HEADERS_JSON, ioJson(headers))
    }

    companion object {
        const val KEY_PLUGIN_ID = "guangyue.pluginId"
        const val KEY_QUALITY = "guangyue.quality"
        const val KEY_DIRECT_URL = "guangyue.directUrl"
        const val KEY_EXTRA_JSON = "guangyue.extra"
        const val KEY_HEADERS_JSON = "guangyue.headers"

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

/** MusicFree quality ladder (low/standard/high/super). */
enum class Quality(val wire: String, val label: String) {
    LOW("low", "标准"),
    STANDARD("standard", "高品"),
    HIGH("high", "很高"),
    SUPER("super", "无损");

    fun next(): Quality = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromWire(v: String?): Quality = entries.firstOrNull { it.wire == v } ?: STANDARD
    }
}
