package com.guangyue.core.data.account

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 酷狗账号直连（照 Folia services/onlineMusic/kugou/kugouProvider.ts：library.getUserPlaylists、
 * catalog.getPlaylistTracks、normalizeCollection / normalizeKugouSong、isKugouLikedPlaylist，
 * kugouTransport 的「error_code 152 = 登录失效」）。Folia 桌面版经 KuGouMusicApi 转发，这里在手机上直接请求它背后的
 * 酷狗网关（照 KuGouMusicApi module/user_playlist.js、playlist_track_all.js、login_token.js 与 util/request.js、
 * util/helper.js 的 Android 签名）：
 *  - 歌单：POST gateway.kugou.com/v7/get_all_list（x-router: cloudlist.service.kugou.com，type 2 = 自建 + 收藏 +
 *    收藏的专辑一起回；专辑 source 2 / 带 musiclib_id 的略过）；「我喜欢」= listid 2 或叫「我喜欢」；
 *  - 歌单曲目：GET gateway.kugou.com/pubsongs/v2/get_other_list_file_nofilt（global_collection_id、begin_idx、pagesize）；
 *  - 扫码拿到的是网页端（appid 1005 / srcappid 2919）的 token。KuGouMusicApi 就拿这个 token 直接调网关；
 *    Folia 手机版的经验是有时网关会回 20017（要安卓 token），那时照 login_token.js 用 /v5/login_by_token 换一个再试。
 *
 * 本文件只放纯函数（签名、拼请求、解析响应、换 token 的加解密），网络与缓存在 [AccountLibrary]。
 */
data class KugouCredential(val userId: String, val token: String) {
    val valid: Boolean get() = userId.isNotBlank() && userId != "0" && token.isNotBlank()

    companion object {
        private val COOKIE_TOKEN = Regex("""(?:^|[;\s])(?:token|t)=([^;\s]+)""")
        private val COOKIE_USER = Regex("""(?:^|[;\s])(?:userid|KugooID)=(\d+)""")

        fun fromCookie(cookie: String?): KugouCredential? {
            if (cookie.isNullOrBlank()) return null
            val token = COOKIE_TOKEN.find(cookie)?.groupValues?.get(1).orEmpty()
            val user = COOKIE_USER.find(cookie)?.groupValues?.get(1).orEmpty()
            return KugouCredential(user, token).takeIf { it.valid }
        }

        /**
         * 从一组字段里认凭据：扫码登录的 fields（token / userid / cookie），或 0.0.30 及以前只写进插件账号的变量
         * （各插件变量名不一：token、userid、user_id、KugooID、酷狗 Cookie……）。vip_token 不算登录 token。
         */
        fun fromFields(fields: Map<String, String>): KugouCredential? {
            var user = ""
            var token = ""
            for ((k, v) in fields) {
                val name = k.lowercase()
                val value = v.trim()
                if (value.isBlank() || '=' in value || ';' in value) continue
                if (token.isBlank() && "token" in name && "vip" !in name) token = value
                if (user.isBlank() && ("userid" in name || "user_id" in name || "kugooid" in name || name == "uid") && value.all { it.isDigit() }) user = value
            }
            if (user.isBlank() || token.isBlank()) {
                for (v in fields.values) {
                    val c = fromCookie(v) ?: continue
                    if (user.isBlank()) user = c.userId
                    if (token.isBlank()) token = c.token
                }
            }
            return KugouCredential(user, token).takeIf { it.valid }
        }
    }
}

/** 酷狗歌单里的一首歌（get_other_list_file_nofilt 的 songs 条目）。整份缓存到本地。 */
@Serializable
data class KugouSong(
    val hash: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    /** mixsongid / album_audio_id：酷狗取链（v5/url）与歌词都认它。 */
    val albumAudioId: String = "",
    val cover: String? = null,
    val durationMs: Long = 0L,
) {
    /**
     * 交给播放 / 下载链路的原始对象（MusicFree 酷狗插件 formatMusicItem 的形状：id = hash，带 album_id /
     * album_audio_id）；洛雪取链源读 hash / albumId（LxSourceManager 的 kg 分支）。
     */
    fun toRaw(platform: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("id", hash)
        put("hash", hash)
        put("songmid", hash)
        put("title", title)
        put("artist", artist)
        put("album", album)
        if (albumId.isNotBlank()) {
            put("albumId", albumId)
            put("album_id", albumId)
        }
        if (albumAudioId.isNotBlank()) {
            put("albumAudioId", albumAudioId)
            put("album_audio_id", albumAudioId)
        }
        cover?.let { put("artwork", it) }
        if (durationMs > 0) put("duration", durationMs / 1000)
    }
}

/** 一次签名好的网关请求：查询参数（含 signature）+ 请求体原文（GET 是空串）。 */
data class KugouRequest(val path: String, val query: Map<String, String>, val body: String, val headers: Map<String, String>)

data class KugouSongPage(val songs: List<KugouSong>, val listed: Int, val total: Int?)

data class KugouPlaylistPage(val playlists: List<AccountPlaylist>, val listed: Int, val total: Int?)

object KugouAccountApi {
    const val GATEWAY = "https://gateway.kugou.com"
    const val PLAYLISTS_PATH = "/v7/get_all_list"
    const val TRACKS_PATH = "/pubsongs/v2/get_other_list_file_nofilt"
    const val LOGIN_TOKEN_PATH = "/v5/login_by_token"

    /** KuGouMusicApi util/config.json：标准版 appid / clientver。扫码也是 appid 1005。 */
    const val APPID = "1005"
    const val CLIENTVER = "20489"

    /** util/helper.js signatureAndroidParams（标准版）的盐。 */
    const val ANDROID_SALT = "OIlwieks28dk2k092lksi2UIkp"

    const val USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"

    /** Folia kugouTransport KUGOU_AUTH_FAILURE_ERROR_CODES：只有 152 算登录失效（别的码不当成退出信号）。 */
    val EXPIRED_CODES = setOf(152)

    /** Folia 手机版：网页 token 直接签安卓网关会回 20017，要先 login_by_token 换安卓 token。 */
    const val NEEDS_APP_TOKEN = 20017

    /** Folia KUGOU_MAX_PAGE_SIZE：一页最多 100。 */
    const val PAGE = 100

    // login_token.js（标准版）的固定 key / iv 与 RSA 公钥（util/crypto.js publicRasKey）。
    private const val LOGIN_AES_KEY = "90b8382a1bb4ccdcf063102053fd75b8"
    private const val LOGIN_AES_IV = "f063102053fd75b8"
    private const val RSA_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ———————————————————————— 签名与设备号 ————————————————————————

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** util/util.js calculateMid：guid 的 MD5 当 16 进制大数，写成十进制。 */
    fun mid(guid: String): String = BigInteger(md5(guid), 16).toString()

    /** util/helper.js signatureAndroidParams：md5(盐 + 按键排序的 k=v 串 + 请求体原文 + 盐)。 */
    fun androidSignature(params: Map<String, String>, body: String = ""): String =
        md5(ANDROID_SALT + params.keys.sorted().joinToString("") { "$it=${params[it]}" } + body + ANDROID_SALT)

    /** util/request.js 的默认参数（dfid / mid / uuid / appid / clientver / clienttime，有登录再带 token / userid）。 */
    fun defaultParams(c: KugouCredential?, mid: String, clientTimeSec: Long, dfid: String = "-"): LinkedHashMap<String, String> =
        linkedMapOf(
            "dfid" to dfid,
            "mid" to mid,
            "uuid" to "-",
            "appid" to APPID,
            "clientver" to CLIENTVER,
            "clienttime" to clientTimeSec.toString(),
        ).apply {
            if (c != null && c.token.isNotBlank()) put("token", c.token)
            if (c != null && c.userId.isNotBlank() && c.userId != "0") put("userid", c.userId)
        }

    /** util/request.js 的请求头（kg-rc / kg-thash / kg-rec / kg-rf 照抄）。 */
    fun headers(mid: String, clientTimeSec: Long, dfid: String = "-", router: String? = null): Map<String, String> = buildMap {
        put("User-Agent", USER_AGENT)
        put("dfid", dfid)
        put("clienttime", clientTimeSec.toString())
        put("mid", mid)
        put("kg-rc", "1")
        put("kg-thash", "5d816a0")
        put("kg-rec", "1")
        put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        if (router != null) put("x-router", router)
    }

    private fun signed(path: String, params: LinkedHashMap<String, String>, body: String, headers: Map<String, String>): KugouRequest {
        params["signature"] = androidSignature(params, body)
        return KugouRequest(path, params, body, headers)
    }

    private fun userIdValue(id: String): JsonPrimitive = id.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(id)

    /** module/user_playlist.js：POST /v7/get_all_list，body = userid / token / total_ver 979 / type 2 / page / pagesize。 */
    fun playlistsRequest(c: KugouCredential, mid: String, clientTimeSec: Long, page: Int, pageSize: Int = PAGE): KugouRequest {
        val body = buildJsonObject {
            put("userid", userIdValue(c.userId))
            put("token", c.token)
            put("total_ver", 979)
            put("type", 2)
            put("page", page)
            put("pagesize", pageSize)
        }.toString()
        val params = defaultParams(c, mid, clientTimeSec).apply {
            put("plat", "1")
            put("userid", c.userId)
            put("token", c.token)
        }
        return signed(PLAYLISTS_PATH, params, body, headers(mid, clientTimeSec, router = "cloudlist.service.kugou.com"))
    }

    /** module/playlist_track_all.js：GET get_other_list_file_nofilt（begin_idx = (page-1)*pagesize）。 */
    fun tracksRequest(c: KugouCredential?, mid: String, clientTimeSec: Long, globalCollectionId: String, begin: Int, pageSize: Int = PAGE): KugouRequest {
        val params = defaultParams(c, mid, clientTimeSec).apply {
            put("area_code", "1")
            put("begin_idx", begin.toString())
            put("plat", "1")
            put("type", "1")
            put("mode", "1")
            put("personal_switch", "1")
            put("extend_fields", "abtags,hot_cmt,popularization")
            put("pagesize", pageSize.toString())
            put("global_collection_id", globalCollectionId)
        }
        return signed(TRACKS_PATH, params, "", headers(mid, clientTimeSec))
    }

    // ———————————————————————— 换安卓 token（login_token.js，标准版：t1 / t2 = 0，不带 dev） ————————————————————————

    /** AES-CBC / PKCS7；key、iv 都是字符串的 UTF-8 字节（32 字符 key = AES-256），出 16 进制小写。 */
    fun aesEncryptHex(plain: String, key: String, iv: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return c.doFinal(plain.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun aesDecryptHex(hex: String, key: String, iv: String): String {
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return String(c.doFinal(bytes), Charsets.UTF_8)
    }

    /** crypto.js cryptoAesEncrypt 不给 key 时：key = md5(临时串) 的 32 位，iv = 它的后 16 位。 */
    fun tempAesKey(temp: String): Pair<String, String> {
        val k = md5(temp).substring(0, 32)
        return k to k.substring(16)
    }

    /** crypto.js cryptoRSAEncrypt：明文右补 0 到密钥长度，裸 RSA（m^e mod n），16 进制小写、补足位数。 */
    fun rsaRawHex(plain: String): String {
        val der = java.util.Base64.getDecoder().decode(RSA_PUBLIC_KEY)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der)) as RSAPublicKey
        val len = (key.modulus.bitLength() + 7) / 8
        val data = plain.toByteArray(Charsets.UTF_8)
        require(data.size <= len) { "明文太长" }
        val padded = ByteArray(len).also { data.copyInto(it) }
        val out = BigInteger(1, padded).modPow(key.publicExponent, key.modulus).toString(16)
        return out.padStart(len * 2, '0')
    }

    /**
     * login_by_token 的请求（[temp] 是 16 位随机串，解 secu_params 要用它）。body 的键顺序照 login_token.js 的 dataMap。
     */
    fun loginByTokenRequest(c: KugouCredential, mid: String, nowMs: Long, temp: String): KugouRequest {
        val sec = nowMs / 1000
        val p3 = aesEncryptHex(buildJsonObject { put("clienttime", sec); put("token", c.token) }.toString(), LOGIN_AES_KEY, LOGIN_AES_IV)
        val (k, iv) = tempAesKey(temp)
        val params = aesEncryptHex("{}", k, iv)
        val pk = rsaRawHex(buildJsonObject { put("clienttime_ms", nowMs); put("key", temp) }.toString())
        val body = buildJsonObject {
            put("dfid", "-")
            put("p3", p3)
            put("plat", 1)
            put("t1", 0)
            put("t2", 0)
            put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            put("pk", pk)
            put("params", params)
            put("userid", c.userId)
            put("clienttime_ms", nowMs)
        }.toString()
        return signed(LOGIN_TOKEN_PATH, defaultParams(c, mid, sec), body, headers(mid, sec, router = "login.user.kugou.com"))
    }

    /** 解 login_by_token 的回答：status 1 才算换到；token 可能明文在 data 里，也可能加密在 secu_params 里。 */
    fun parseLoginByToken(body: String, temp: String): KugouCredential? {
        val root = parseRoot(body) ?: return null
        if (root.pick("status") != "1") return null
        var data = root["data"].obj() ?: return null
        var token = data.pick("token")
        val secu = data.pick("secu_params")
        if (secu != null) {
            val (k, iv) = tempAesKey(temp)
            val plain = runCatching { aesDecryptHex(secu, k, iv) }.getOrNull()
            if (plain != null) {
                val o = runCatching { json.parseToJsonElement(plain) as? JsonObject }.getOrNull()
                if (o != null) {
                    data = JsonObject(data + o)
                    token = o.pick("token") ?: token
                } else if (plain.isNotBlank()) {
                    token = plain.trim().trim('"')
                }
            }
        }
        val user = idText(data.pick("userid")) ?: return null
        return KugouCredential(user, token ?: return null).takeIf { it.valid }
    }

    // ———————————————————————— 解析 ————————————————————————

    private fun parseRoot(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { this[it].text() }
    private fun JsonObject.num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { this[it].text()?.toDoubleOrNull() }

    private fun idText(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "0") return null
        if (s.all { it.isDigit() }) return s
        return s.toDoubleOrNull()?.takeIf { it > 0 && it == Math.floor(it) }?.toLong()?.toString() ?: s
    }

    /** 酷狗拒了这次读取的码：status 0 或 error_code / errcode 不是 0（util/request.js 的判法）。没拒回 null。 */
    fun refusalCode(root: JsonObject): Int? {
        val code = root.num("error_code", "errcode")?.toInt()
        val status = root.num("status")?.toInt()
        if (code != null && code != 0) return code
        if (status == 0) return code ?: -1
        return null
    }

    private fun <T> refusal(root: JsonObject, what: String): QqRead<T>? {
        val code = refusalCode(root) ?: return null
        return if (code in EXPIRED_CODES) QqRead.Expired
        else QqRead.Failed("$what（error_code $code${root.pick("error_msg", "error", "errmsg", "msg")?.let { "，$it" } ?: ""}）")
    }

    /** 回答里的 error_code（没有或为 0 回 null），给「要不要先换安卓 token」用。 */
    fun errorCodeOf(body: String): Int? = parseRoot(body)?.let(::refusalCode)

    /** Folia coverOf：{size} 换成尺寸，纯文件名补成 c1.kgimg.com 地址，补 https。 */
    fun coverUrl(raw: String?, size: Int = 480): String? {
        val s = raw?.trim()?.replace("{size}", size.toString()).orEmpty()
        if (s.isEmpty()) return null
        if (Regex("""^\d{8,}\.(?:jpe?g|png|webp)$""", RegexOption.IGNORE_CASE).matches(s)) {
            return "https://c1.kgimg.com/stdmusic/$size/${s.substring(0, 8)}/$s"
        }
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http://", ignoreCase = true) -> "https://" + s.substring(7)
            else -> s
        }
    }

    private fun coverOf(o: JsonObject): String? {
        val raw = (o["album_info"] ?: o["albuminfo"]).obj()?.pick("sizable_cover", "cover")
            ?: o["audio_info"].obj()?.get("trans_param").obj()?.pick("union_cover")
            ?: o["trans_param"].obj()?.pick("union_cover")
            ?: o.pick("sizable_cover", "Image", "image", "img", "pic", "picUrl", "cover", "coverUrl")
        return coverUrl(raw)
    }

    /** data 里第一个像列表的数组（Folia listOf 的简化：get_all_list 是 data.info，曲目是 data.songs）。 */
    private fun listIn(root: JsonObject, vararg keys: String): JsonArray? {
        val data = root["data"].obj() ?: root
        for (k in keys) data[k].arr()?.let { return it }
        for (k in keys) root[k].arr()?.let { return it }
        return null
    }

    private fun totalOf(root: JsonObject, vararg keys: String): Int? {
        val data = root["data"].obj() ?: root
        return data.num(*keys)?.toInt()?.takeIf { it >= 0 }
    }

    /** getKugouUserCollectionType：source 2 或带 musiclib_id 的是收藏的专辑，不进歌单。 */
    fun isAlbum(o: JsonObject): Boolean = o.pick("source") == "2" || o.pick("musiclib_id", "musicLibId") != null

    /** isKugouOwnedPlaylist：type 缺省或 0 = 自建；1 = 收藏。 */
    fun isOwned(o: JsonObject): Boolean = o.num("type")?.toInt().let { it == null || it == 0 }

    /** isKugouLikedPlaylist：叫「我喜欢」/「我喜欢的音乐」或 listid 2（收藏来的不算）。 */
    fun isLiked(o: JsonObject): Boolean {
        if (isAlbum(o) || !isOwned(o)) return false
        val name = o.pick("name", "listname")?.trim().orEmpty()
        return name == "我喜欢" || name == "我喜欢的音乐" || o.pick("listid", "list_id") == "2"
    }

    /** get_all_list 的一条 → 账号歌单（id = global_collection_id，读曲目用它）。专辑与认不出 id 的回 null。 */
    fun playlist(o: JsonObject): AccountPlaylist? {
        if (isAlbum(o)) return null
        val id = o.pick("global_collection_id", "globalCollectionId", "specialid", "specialId", "id") ?: return null
        val owned = isOwned(o)
        val liked = isLiked(o)
        val name = o.pick("name", "listname", "specialname") ?: if (liked) "我喜欢" else return null
        return AccountPlaylist(
            id = id,
            name = name,
            cover = coverOf(o),
            trackCount = o.num("count", "song_count", "m_count", "total", "music_num")?.toInt(),
            dirId = o.num("listid", "list_id")?.toInt(),
            owned = owned,
            liked = liked,
            creator = o.pick("list_create_username", "create_username", "nickname", "username"),
        )
    }

    /** 解析一页 get_all_list（排序交给 [order]）。listed 是这一页原始条数（含专辑），翻页按它算。 */
    fun parsePlaylistPage(body: String): QqRead<KugouPlaylistPage> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<KugouPlaylistPage>(root, "歌单读取失败")?.let { return it }
        val list = listIn(root, "info", "list", "lists") ?: return QqRead.Failed("缺少歌单")
        val items = list.mapNotNull { it.obj()?.let(::playlist) }
        return QqRead.Ok(KugouPlaylistPage(items, list.size, totalOf(root, "list_count", "total", "total_count")))
    }

    /** 还有没有下一页（pageOfWithRawItemCount：有总数按总数，没有就看这一页是不是满的）。 */
    fun hasMore(page: KugouPlaylistPage, readSoFar: Int, pageSize: Int = PAGE): Boolean =
        if (page.total != null) readSoFar < page.total else page.listed >= pageSize

    /** 我喜欢置顶，接着自建（上游顺序），再接收藏，同 id 只留一次。 */
    fun order(all: List<AccountPlaylist>): List<AccountPlaylist> =
        QqAccountApi.merge(all.filter { it.owned }, all.filterNot { it.owned })

    fun parsePlaylists(body: String): QqRead<List<AccountPlaylist>> =
        when (val r = parsePlaylistPage(body)) {
            is QqRead.Ok -> QqRead.Ok(order(r.value.playlists))
            QqRead.Expired -> QqRead.Expired
            is QqRead.Failed -> r
        }

    private fun artistsOf(o: JsonObject): List<String> {
        val arr = (o["singerinfo"] ?: o["Singers"] ?: o["singers"] ?: o["authors"] ?: o["artists"]).arr()
        if (arr != null) {
            return arr.mapNotNull { e ->
                (e as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: e.obj()?.let { (it["base"].obj() ?: it).pick("name", "author_name", "singername") }
            }
        }
        return o.pick("SingerName", "singername", "author_name", "Singer").orEmpty()
            .split('、', ',', '&', '/').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** normalizeKugouSong（+ normalizeKugouTitleAndArtists：名字是「歌手 - 歌名」时拆开）。没有 hash 的回 null。 */
    fun song(o: JsonObject): KugouSong? {
        val audio = o["audio_info"].obj()
        val hash = (o.pick("FileHash", "fileHash", "hash", "Hash", "audio_hash") ?: audio?.pick("hash", "hash_128"))
            ?.uppercase() ?: return null
        val base = o["base"].obj()
        val album = (o["album_info"] ?: o["albuminfo"] ?: o["albumInfo"]).obj()
        val rawTitle = (o.pick("SongName", "songname", "songName", "ori_audio_name", "OriSongName", "FileName", "filename", "name", "audio_name")
            ?: audio?.pick("ori_audio_name", "audio_name") ?: base?.pick("songname", "audio_name")).orEmpty()
            .replace(Regex("""\.(?:mp3|flac|m4a|ogg|wav|ape)$""", RegexOption.IGNORE_CASE), "")
        var artists = artistsOf(o)
        var title = rawTitle.trim()
        val split = Regex("""^(.+?)\s+-\s+(.+)$""").find(title)
        if (split != null) {
            val left = split.groupValues[1].trim()
            if (artists.isEmpty()) {
                artists = left.split('、', ',', '&', '/').map { it.trim() }.filter { it.isNotEmpty() }
                title = split.groupValues[2].trim()
            } else if (left == artists.joinToString("、") || artists.any { it == left } || left.split('、', ',', '&', '/').map { it.trim() }.toSet() == artists.toSet()) {
                title = split.groupValues[2].trim()
            }
        }
        if (title.isEmpty()) return null
        val albumId = idText(o.pick("AlbumID", "album_id", "albumId") ?: album?.pick("album_id", "albumId", "id") ?: base?.pick("album_id"))
        val dur = (o.num("Duration", "duration", "timelen", "timeLength", "timelength", "time_length")
            ?: audio?.num("duration", "timelength", "duration_128") ?: 0.0)
        return KugouSong(
            hash = hash,
            title = title,
            artist = artists.joinToString("、").ifBlank { "未知艺术家" },
            album = (o.pick("AlbumName", "album_name", "albumName", "albumname") ?: album?.pick("name", "album_name", "albumName") ?: base?.pick("album_name")).orEmpty(),
            albumId = albumId.orEmpty(),
            albumAudioId = idText(o.pick("MixSongID", "mixsongid", "mixSongId", "album_audio_id", "albumAudioId") ?: base?.pick("mixsongid", "album_audio_id")).orEmpty(),
            cover = coverOf(o),
            durationMs = (if (dur > 0 && dur < 10_000) dur * 1000 else dur).toLong(),
        )
    }

    /**
     * 解析一页 get_other_list_file_nofilt。total 只在明显大于一页时才当整张歌单的数（Folia：有的回答里 count 是本页条数）。
     */
    fun parseSongPage(body: String, pageSize: Int = PAGE): QqRead<KugouSongPage> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<KugouSongPage>(root, "歌单曲目读取失败")?.let { return it }
        val list = listIn(root, "songs", "info", "list", "lists") ?: return QqRead.Failed("缺少曲目")
        val total = totalOf(root, "total", "total_count", "count")?.takeIf { it > pageSize }
        return QqRead.Ok(KugouSongPage(list.mapNotNull { it.obj()?.let(::song) }, list.size, total))
    }

    /** 这一页之后还要不要往下翻（短页当末尾；读够了总数也停）。 */
    fun songsMore(page: KugouSongPage, readSoFar: Int, pageSize: Int = PAGE): Boolean =
        page.listed >= pageSize && (page.total == null || readSoFar < page.total)
}
