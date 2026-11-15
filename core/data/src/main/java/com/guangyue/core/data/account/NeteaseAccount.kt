package com.guangyue.core.data.account

import com.guangyue.core.plugin.login.NeteaseCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 网易云账号直连（照 Folia services/onlineMusic/netease：neteaseProvider.library.getUserPlaylists、
 * netease.ts getPlaylistTracks / getSongDetail、normalizeCollection / normalizeNeteaseSong、
 * 301 / 401 / 403 = 没登录或登录失效）。Folia 经 NeteaseCloudMusicApi 后端转发，这里在手机上直接用 eapi
 * （[NeteaseCrypto]，扫码登录同一套）请求它背后的同一组接口：
 *  - 谁登录着：/api/nuser/account/get（profile.userId；code 200 但没有 profile = 没登录）；
 *  - 歌单：/api/user/playlist（自建 + 收藏一起回；「我喜欢的音乐」= specialType 5 且创建者是自己）；
 *  - 歌单曲目：/api/v6/playlist/detail 拿 trackIds（module/playlist_track_all.js 的第一步），
 *    再 /api/v3/song/detail 按 id 分批补全（第二步）。
 * 凭据是扫码登录换来的 MUSIC_U（+ __csrf），见 [com.guangyue.core.plugin.login.QrLogin] 的 neteasePoll。
 *
 * 本文件只放纯函数（拼请求、解析响应），网络与缓存在 [AccountLibrary]。
 */
data class NeteaseCredential(val musicU: String, val csrf: String = "") {
    val valid: Boolean get() = musicU.isNotBlank()

    companion object {
        private val COOKIE_MUSIC_U = Regex("""(?:^|[;\s])MUSIC_U=([^;\s]+)""")
        private val COOKIE_CSRF = Regex("""(?:^|[;\s])__csrf=([^;\s]+)""")

        fun fromCookie(cookie: String?): NeteaseCredential? {
            if (cookie.isNullOrBlank()) return null
            val u = COOKIE_MUSIC_U.find(cookie)?.groupValues?.get(1).orEmpty()
            val csrf = COOKIE_CSRF.find(cookie)?.groupValues?.get(1).orEmpty()
            return NeteaseCredential(u, csrf).takeIf { it.valid }
        }

        /**
         * 从一组字段里认凭据：扫码登录的 fields（MUSIC_U / __csrf / cookie），或 0.0.30 及以前只写进插件账号的变量
         * （各插件变量名不一：MUSIC_U、music_u、cookie、网易云 Cookie……）。认不出 MUSIC_U 返回 null。
         */
        fun fromFields(fields: Map<String, String>): NeteaseCredential? {
            var u = ""
            var csrf = ""
            for ((k, v) in fields) {
                val name = k.lowercase()
                val value = v.trim()
                if (value.isBlank()) continue
                if (u.isBlank() && "music_u" in name && '=' !in value && ';' !in value) u = value
                if (csrf.isBlank() && "csrf" in name && '=' !in value) csrf = value
            }
            for (v in fields.values) {
                val c = fromCookie(v) ?: continue
                if (u.isBlank()) u = c.musicU
                if (csrf.isBlank()) csrf = c.csrf
            }
            return NeteaseCredential(u, csrf).takeIf { it.valid }
        }
    }
}

/** 网易云歌单里的一首歌（/api/v3/song/detail 或 v6/playlist/detail tracks 的条目）。整份缓存到本地。 */
@Serializable
data class NeteaseSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    val cover: String? = null,
    val durationMs: Long = 0L,
) {
    /**
     * 交给播放 / 下载链路的原始对象（MusicFree 网易云插件 formatMusicItem 的形状）：插件 getMediaSource 读 id；
     * 洛雪取链源读 songmid / musicId（LxSourceManager 的 wy 分支），三者都是网易云的歌曲 id。
     */
    fun toRaw(platform: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("id", id)
        put("songmid", id)
        put("musicId", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        if (albumId.isNotBlank()) put("albumId", albumId)
        cover?.let { put("artwork", it) }
        if (durationMs > 0) put("duration", durationMs / 1000)
    }
}

/** v6/playlist/detail 的结论：全部曲目 id（顺序即歌单顺序）+ 它顺带给出的那部分曲目。 */
data class NeteasePlaylistDetail(val trackIds: List<String>, val tracks: List<NeteaseSong>)

object NeteaseAccountApi {
    const val ACCOUNT_PATH = "/api/nuser/account/get"
    const val PLAYLISTS_PATH = "/api/user/playlist"
    const val DETAIL_PATH = "/api/v6/playlist/detail"
    const val SONG_DETAIL_PATH = "/api/v3/song/detail"

    /** 「我喜欢的音乐」在 /user/playlist 里的标记（Folia NETEASE_LIKED_PLAYLIST_SPECIAL_TYPE）。 */
    const val LIKED_SPECIAL_TYPE = 5

    /** 网易云说「没登录 / 登录失效」的码（Folia neteaseFailure：301、401、403）。 */
    val EXPIRED_CODES = setOf(301, 401, 403)

    /** 一次 /user/playlist 读多少张。 */
    const val PLAYLIST_PAGE = 1000

    /** 一次 /song/detail 补多少首。 */
    const val SONG_DETAIL_BATCH = 400

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ———————————————————————— 拼请求（接口参数，交给 NeteaseCrypto.eapiBody 加 header 再加密） ————————————————————————

    fun accountData(): Map<String, JsonElement> = emptyMap()

    /** module/user_playlist.js：uid / limit / offset / includeVideo。 */
    fun playlistsData(uid: String, offset: Int, limit: Int = PLAYLIST_PAGE): Map<String, JsonElement> = linkedMapOf(
        "uid" to JsonPrimitive(uid),
        "limit" to JsonPrimitive(limit),
        "offset" to JsonPrimitive(offset),
        "includeVideo" to JsonPrimitive(true),
    )

    /** module/playlist_track_all.js 第一步：n = 100000 要全部 trackIds，s = 8 个最近收藏者。 */
    fun detailData(id: String): Map<String, JsonElement> = linkedMapOf(
        "id" to JsonPrimitive(id),
        "n" to JsonPrimitive(100000),
        "s" to JsonPrimitive(8),
    )

    /** module/song_detail.js：c = `[{"id":1},{"id":2}]`（字符串）。 */
    fun songDetailData(ids: List<String>): Map<String, JsonElement> = linkedMapOf(
        "c" to JsonPrimitive(ids.joinToString(",", "[", "]") { "{\"id\":$it}" }),
    )

    /** 一次 eapi 请求的 `params`：同样的 path / 参数 / header 必然得到同样的密文。 */
    fun params(path: String, data: Map<String, JsonElement>, header: Map<String, String>): String =
        NeteaseCrypto.eapiParams(path, NeteaseCrypto.eapiBody(data, header))

    // ———————————————————————— 解析 ————————————————————————

    private fun parseRoot(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { this[it].text() }
    private fun JsonObject.int(vararg keys: String): Int? = pick(*keys)?.toDoubleOrNull()?.toInt()

    /** 数字 id 统一成不带小数点的文本（JSON 里偶尔是 1.0E9 这样的写法）。 */
    private fun idText(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "0") return null
        if (s.all { it.isDigit() }) return s
        return s.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }?.toString() ?: s
    }

    private fun codeOf(o: JsonObject): Int? = o["code"].text()?.toDoubleOrNull()?.toInt()

    /** 不是 200 的码：301 / 401 / 403 算登录失效，别的算这一次失败。没有码也算失败（Folia：缺码不是成功）。 */
    private fun <T> refusal(root: JsonObject, what: String): QqRead<T>? {
        val code = codeOf(root) ?: return QqRead.Failed("$what：响应没有状态码")
        if (code == 200) return null
        return if (code in EXPIRED_CODES) QqRead.Expired
        else QqRead.Failed("$what（code $code${root.pick("message", "msg")?.let { "，$it" } ?: ""}）")
    }

    /** 封面补成 https（Folia toHttps）。 */
    fun coverUrl(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http://") -> "https://" + s.removePrefix("http://")
            else -> s
        }
    }

    /** /nuser/account/get：登录着的账号 uid；code 200 却没有 profile / account = 没有人登录（Folia getLoginStatus 回 null）。 */
    fun parseAccount(body: String): QqRead<String> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<String>(root, "读账号")?.let { return it }
        val uid = idText(root["profile"].obj()?.pick("userId", "id"))
            ?: idText(root["account"].obj()?.pick("id", "userId"))
            ?: return QqRead.Expired
        return QqRead.Ok(uid)
    }

    /** /user/playlist 的一条：创建者是自己 = 自建；specialType 5 且自建 = 我喜欢的音乐（收藏来的别人的「喜欢」不算）。 */
    fun playlist(o: JsonObject, uid: String): AccountPlaylist? {
        val id = idText(o.pick("id")) ?: return null
        val creator = o["creator"].obj()
        val creatorId = idText(creator?.pick("userId", "id")) ?: idText(o.pick("userId"))
        val subscribed = o.pick("subscribed") == "true"
        val owned = creatorId == uid && !subscribed
        val liked = owned && o.int("specialType") == LIKED_SPECIAL_TYPE
        return AccountPlaylist(
            id = id,
            name = o.pick("name") ?: if (liked) "我喜欢的音乐" else return null,
            cover = coverUrl(o.pick("coverImgUrl", "coverUrl", "picUrl")),
            trackCount = o.int("trackCount", "size"),
            owned = owned,
            liked = liked,
            creator = creator?.pick("nickname"),
        )
    }

    data class PlaylistPage(val playlists: List<AccountPlaylist>, val listed: Int, val more: Boolean)

    /** 解析一页 /user/playlist（排序交给 [QqAccountApi.merge]：我喜欢置顶 → 自建 → 收藏）。 */
    fun parsePlaylistPage(body: String, uid: String): QqRead<PlaylistPage> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<PlaylistPage>(root, "歌单读取失败")?.let { return it }
        val list = root["playlist"].arr() ?: return QqRead.Failed("缺少歌单")
        val items = list.mapNotNull { it.obj()?.let { o -> playlist(o, uid) } }
        val more = root.pick("more") == "true"
        return QqRead.Ok(PlaylistPage(items, list.size, more))
    }

    /** 几页 /user/playlist 合成账号歌单：我喜欢置顶，接着自建（上游顺序），再接收藏，同 id 只留一次。 */
    fun order(all: List<AccountPlaylist>): List<AccountPlaylist> =
        QqAccountApi.merge(all.filter { it.owned }, all.filterNot { it.owned })

    /** 一页就完的常见情形：解析 + 排好序。 */
    fun parsePlaylists(body: String, uid: String): QqRead<List<AccountPlaylist>> =
        when (val r = parsePlaylistPage(body, uid)) {
            is QqRead.Ok -> QqRead.Ok(order(r.value.playlists))
            QqRead.Expired -> QqRead.Expired
            is QqRead.Failed -> r
        }

    /** 一首歌（normalizeNeteaseSong：name、ar / artists、al / album、dt / duration）。 */
    fun song(o: JsonObject): NeteaseSong? {
        val base = o["simpleSong"].obj() ?: o
        val id = idText(base.pick("id")) ?: return null
        val title = base.pick("name") ?: return null
        val artists = (base["ar"] ?: base["artists"]).arr().orEmpty().mapNotNull { it.obj()?.pick("name") }
        val al = (base["al"] ?: base["album"]).obj()
        return NeteaseSong(
            id = id,
            title = title,
            artist = artists.joinToString("、").ifBlank { "未知艺术家" },
            album = al?.pick("name").orEmpty(),
            albumId = idText(al?.pick("id")).orEmpty(),
            cover = coverUrl(al?.pick("picUrl", "coverUrl")),
            durationMs = base.pick("dt", "duration", "durationMs")?.toDoubleOrNull()?.toLong() ?: 0L,
        )
    }

    /** 解析 v6/playlist/detail：trackIds（没有就退回 tracks 的顺序）+ 顺带给的曲目。 */
    fun parseDetail(body: String): QqRead<NeteasePlaylistDetail> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<NeteasePlaylistDetail>(root, "歌单曲目读取失败")?.let { return it }
        val p = root["playlist"].obj() ?: return QqRead.Failed("缺少歌单")
        val tracks = p["tracks"].arr().orEmpty().mapNotNull { it.obj()?.let(::song) }
        val ids = p["trackIds"].arr().orEmpty().mapNotNull { idText(it.obj()?.pick("id") ?: it.text()) }
        return QqRead.Ok(NeteasePlaylistDetail(ids.ifEmpty { tracks.map { it.id } }.distinct(), tracks))
    }

    /** 解析 /v3/song/detail。 */
    fun parseSongDetail(body: String): QqRead<List<NeteaseSong>> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        refusal<List<NeteaseSong>>(root, "歌曲信息读取失败")?.let { return it }
        return QqRead.Ok(root["songs"].arr().orEmpty().mapNotNull { it.obj()?.let(::song) })
    }

    /** 还缺哪些 id 要去 /song/detail 补（分批，每批 [batch] 个）。 */
    fun missingBatches(detail: NeteasePlaylistDetail, batch: Int = SONG_DETAIL_BATCH): List<List<String>> {
        val have = detail.tracks.mapTo(HashSet()) { it.id }
        return detail.trackIds.filterNot { it in have }.chunked(batch)
    }

    /** 按 trackIds 的顺序排好；网易云没给出的 id（下架等）略过。 */
    fun assemble(detail: NeteasePlaylistDetail, extra: List<NeteaseSong>): List<NeteaseSong> {
        val byId = LinkedHashMap<String, NeteaseSong>()
        for (s in detail.tracks + extra) byId.putIfAbsent(s.id, s)
        return detail.trackIds.mapNotNull { byId[it] }
    }
}
