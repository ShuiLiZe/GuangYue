package com.guangyue.core.plugin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 11-18 用户：「QQ 音乐我登录了还是不能听，我有会员」。插件拿登录 cookie 取链常常是空的；
 * 这里用扫码登录拿到的 uin + musickey 直接问 QQ 音乐官方取链（vkey.GetVkeyServer / CgiGetVkey），
 * 会员账号能拿到会员歌的地址。取不到再交回插件。
 */
object QqVkey {
    data class Cred(val uin: String, val key: String) {
        val loginType: Int get() = if (key.startsWith("W_X")) 1 else 2
    }

    private val COOKIE_UIN = Regex("""(?:^|[;\s])uin=o?0*(\d+)""")
    private val COOKIE_KEY = Regex("""(?:^|[;\s])(?:qqmusic_key|qm_keyst)=([^;\s]+)""")

    /** 从插件账号变量里认 uin / musickey（同 core:data QqCredential.fromFields）。 */
    fun credOf(fields: Map<String, String>): Cred? {
        var uin = ""
        var key = ""
        for ((k, v) in fields) {
            val name = k.lowercase()
            val value = v.trim()
            if (value.isBlank()) continue
            if (key.isBlank() && ("musickey" in name || "keyst" in name || "qqmusic_key" in name) && '=' !in value) key = value
            if (uin.isBlank() && "uin" in name && "cookie" !in name) uin = value.trimStart('o', 'O').trimStart('0')
        }
        if (uin.isBlank() || key.isBlank()) {
            for (v in fields.values) {
                val u = COOKIE_UIN.find(v)?.groupValues?.get(1).orEmpty()
                val kk = COOKIE_KEY.find(v)?.groupValues?.get(1).orEmpty()
                if (u.isNotBlank() && kk.isNotBlank()) { uin = u; key = kk; break }
            }
        }
        return if (uin.isNotBlank() && key.isNotBlank()) Cred(uin, key) else null
    }

    fun isQq(meta: PluginMeta?): Boolean {
        val s = (meta?.platform.orEmpty() + " " + meta?.name.orEmpty()).lowercase()
        return "qq" in s
    }

    private fun str(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** 歌曲 mid：QQ 的 songmid 是 14 位字母数字，纯数字的是 songid，不能用。 */
    fun songMid(o: JsonObject): String? =
        listOfNotNull(str(o, "songmid"), str(o, "mid"), str(o, "id")).firstOrNull { it.any { c -> c.isLetter() } }

    fun mediaMid(o: JsonObject): String? =
        str(o, "strMediaMid", "mediamid", "media_mid", "mediaMid")?.takeIf { it.any { c -> c.isLetter() } }

    /** 音质 → 先试的文件名前缀，由高到低降级。 */
    fun candidates(quality: String): List<Pair<String, String>> {
        val flac = "F000" to ".flac"
        val hq = "M800" to ".mp3"
        val std = "M500" to ".mp3"
        val low = "C400" to ".m4a"
        return when (quality.lowercase()) {
            "super" -> listOf(flac, hq, std, low)
            "high" -> listOf(hq, std, low)
            "low" -> listOf(std, low)
            else -> listOf(std, low)
        }
    }

    fun body(c: Cred, mid: String, filenames: List<String>): String = buildJsonObject {
        put("comm", buildJsonObject {
            put("uin", c.uin); put("format", "json"); put("ct", 19); put("cv", 0)
            put("authst", c.key); put("tmeLoginType", c.loginType)
        })
        put("req_0", buildJsonObject {
            put("module", "vkey.GetVkeyServer")
            put("method", "CgiGetVkey")
            put("param", buildJsonObject {
                put("filename", buildJsonArray { filenames.forEach { add(it) } })
                put("guid", "10000")
                put("songmid", buildJsonArray { filenames.forEach { _ -> add(mid) } })
                put("songtype", buildJsonArray { filenames.forEach { _ -> add(0) } })
                put("uin", c.uin)
                put("loginflag", 1)
                put("platform", "20")
            })
        })
    }.toString()

    /** 返回按 filenames 顺序第一条有 purl 的完整地址。 */
    fun parse(json: String): String? {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val data = (root["req_0"] as? JsonObject)?.get("data") as? JsonObject ?: return null
        val sip = ((data["sip"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: "https://ws.stream.qqmusic.qq.com/"
        val infos = data["midurlinfo"] as? JsonArray ?: return null
        for (e in infos) {
            val purl = ((e as? JsonObject)?.get("purl") as? JsonPrimitive)?.contentOrNull
            if (!purl.isNullOrBlank()) return sip + purl
        }
        return null
    }

    fun fetch(http: OkHttpClient, c: Cred, payload: JsonObject, quality: String): MediaSourceResult? {
        val mid = songMid(payload) ?: return null
        val media = mediaMid(payload) ?: mid
        val names = candidates(quality).map { (p, ext) -> p + mid + media + ext }
        val req = Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
            .header("Cookie", "uin=o${c.uin}; qqmusic_key=${c.key}; qm_keyst=${c.key}; tmeLoginType=${c.loginType}")
            .post(body(c, mid, names).toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body.string() else return null }
        val url = parse(text) ?: return null
        return MediaSourceResult(url)
    }
}
