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

/**
 * QQ 音乐账号直连（照 Folia services/onlineMusic/qq：qqProvider.getUserPlaylists / getPlaylistTracks、
 * qqNormalize.normalizeQqCollection）。Folia 经 qq-music-api 后端转发，这里在手机上直接请求 QQ 的
 * musicu.fcg（同一组上游接口）：
 *  - 自建歌单：music.musicasset.PlaylistBaseRead / GetPlaylistByUin（`v_playlist`，含「我喜欢」= 目录 201）；
 *  - 收藏歌单：music.musicasset.PlaylistFavRead / CgiGetPlaylistFavInfo（`v_list`）；
 *  - 歌单曲目：music.srfDissInfo.aiDissInfo / uniform_get_Dissinfo（带凭据，读得到不公开的自建歌单）。
 * 凭据是扫码登录换来的 musicid（uin）+ musickey（[com.guangyue.core.plugin.login.QrLogin] 的 qqFinish）。
 *
 * 本文件只放纯函数（拼请求、解析响应），网络与缓存在 [AccountLibrary]，好在 CI 上用夹具单测。
 */
data class QqCredential(val uin: String, val musicKey: String) {
    val valid: Boolean get() = uin.isNotBlank() && musicKey.isNotBlank()

    /** QQ 登录的 musickey 以 Q_H_L 开头，微信登录的以 W_X 开头（tmeLoginType 1 = 微信，2 = QQ）。 */
    val loginType: Int get() = if (musicKey.startsWith("W_X")) 1 else 2

    companion object {
        private val COOKIE_UIN = Regex("""(?:^|[;\s])uin=o?0*(\d+)""")
        private val COOKIE_KEY = Regex("""(?:^|[;\s])(?:qqmusic_key|qm_keyst)=([^;\s]+)""")

        /** 从 cookie 串里抠 uin 与 qqmusic_key / qm_keyst。 */
        fun fromCookie(cookie: String?): QqCredential? {
            if (cookie.isNullOrBlank()) return null
            val uin = COOKIE_UIN.find(cookie)?.groupValues?.get(1).orEmpty()
            val key = COOKIE_KEY.find(cookie)?.groupValues?.get(1).orEmpty()
            return QqCredential(uin, key).takeIf { it.valid }
        }

        /**
         * 从一组字段里认凭据：扫码登录的 fields（uin / musickey / cookie），或旧版本只写进插件账号的变量
         * （各插件变量名不一：qm_keyst、QQ Cookie、uin……）。认不全返回 null。
         */
        fun fromFields(fields: Map<String, String>): QqCredential? {
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
                    val c = fromCookie(v) ?: continue
                    if (uin.isBlank()) uin = c.uin
                    if (key.isBlank()) key = c.musicKey
                }
            }
            return QqCredential(uin, key).takeIf { it.valid }
        }
    }
}

/** 账号里的一张歌单（自建 / 收藏 / 我喜欢）。会整份缓存到本地，重开 App 先显示它。 */
@Serializable
data class AccountPlaylist(
    val id: String,
    val name: String,
    val cover: String? = null,
    val trackCount: Int? = null,
    /** 自建目录号；「我喜欢」是 201。收藏的歌单也带 dirId（创建者账号里的），所以要配合 [owned] 看。 */
    val dirId: Int? = null,
    val owned: Boolean = false,
    val liked: Boolean = false,
    val creator: String? = null,
)

/** 歌单里的一首歌（uniform_get_Dissinfo 的 songlist 条目）。 */
@Serializable
data class AccountSong(
    val songId: String,
    val songMid: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumMid: String = "",
    val mediaMid: String = "",
    val durationSec: Long = 0L,
) {
    /** 歌曲在曲库里的 id：数字 songid 优先（与 MusicFree QQ 插件搜索结果的 id 一致），没有就用 songmid。 */
    val id: String get() = songId.ifBlank { songMid }

    val artwork: String? get() = albumMid.takeIf { it.isNotBlank() }?.let { "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg" }

    /**
     * 交给播放 / 下载链路的原始对象（MusicFree QQ 插件 formatMusicItem 的形状）：插件 getMediaSource 读 songmid，
     * 洛雪取链源读 songmid / strMediaMid（LxSourceManager 的 tx 分支）。
     */
    fun toRaw(platform: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("id", id)
        put("songid", songId)
        put("songmid", songMid)
        put("mid", songMid)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("albummid", albumMid)
        // QQ 插件取链读搜索结果的形状：_src.qq.{mid,mediaMid} + primaryKey（模拟器取证 11-16：
        // 只给 songmid/mid 时 getMediaSource 返回空）。三种写法都给，插件和洛雪源各取所需。
        val media = mediaMid.ifBlank { songMid }
        put("strMediaMid", media)
        put("mediaMid", media)
        put("primaryKey", songMid)
        put("_src", buildJsonObject {
            put("qq", buildJsonObject {
                put("mid", songMid)
                put("mediaMid", media)
            })
        })
        put("_srcOrder", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("qq"))))
        artwork?.let { put("artwork", it) }
        if (durationSec > 0) put("duration", durationSec)
    }
}

/** 一次读账号的结果：读到了 / 登录过期 / 别的失败。 */
sealed interface QqRead<out T> {
    data class Ok<T>(val value: T) : QqRead<T>
    data object Expired : QqRead<Nothing>
    data class Failed(val reason: String) : QqRead<Nothing>
}

data class QqSongPage(val songs: List<AccountSong>, val total: Int?, val listed: Int)

object QqAccountApi {
    const val MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    /** 「我喜欢」歌单的自建目录号（Folia qqNormalize QQ_LIKED_DIR_ID）。 */
    const val LIKED_DIR_ID = 201

    /**
     * 上游说「没登录 / 登录失效」的码：1000（未登录）、104401（凭据过期）。Folia 把上游 1000 / 104401 / 104400 都转成 401
     * 再问 login_status 决定；104400 常常只是参数不被接受，这里不把它算成登录过期，免得把人误踢下线。
     */
    val EXPIRED_CODES = setOf(1000, 104401)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ———————————————————————— 拼请求 ————————————————————————

    /** g_tk：对 musickey 做 hash33（种子 5381），同 QQ 网页版。 */
    fun gtk(key: String): Long {
        var h = 5381L
        for (c in key) h += (h shl 5) + c.code
        return h and 0x7fffffff
    }

    fun cookie(c: QqCredential): String =
        "uin=${c.uin}; qqmusic_key=${c.musicKey}; qm_keyst=${c.musicKey}; tmeLoginType=${c.loginType}"

    fun comm(c: QqCredential): JsonObject = buildJsonObject {
        put("ct", 24)
        put("cv", 4747474)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("notice", 0)
        put("platform", "yqq.json")
        put("needNewCode", 1)
        put("uin", c.uin.toLongOrNull() ?: 0L)
        put("qq", c.uin)
        put("authst", c.musicKey)
        put("tmeLoginType", c.loginType)
        val g = gtk(c.musicKey)
        put("g_tk", g)
        put("g_tk_new_20200303", g)
    }

    /** 不带登录态的 comm（未登录时的读法）：带凭据读失败时回落用。 */
    fun anonComm(): JsonObject = buildJsonObject {
        put("ct", 24)
        put("cv", 4747474)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("notice", 0)
        put("platform", "yqq.json")
        put("needNewCode", 1)
        put("uin", 0)
    }

    /**
     * 匿名歌单详情（Folia song_list_detail / qq-music-api getSongListDetail 同款）：不带 cookie，
     * 读公开歌单（收藏的、自建公开的）。disstid 不是纯数字（自建歌单只有目录号）时返回 null。
     */
    fun cdInfoUrl(p: AccountPlaylist): String? {
        val id = p.id.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0" +
            "&new_format=1&disstid=$id&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
    }

    private fun req(module: String, method: String, param: JsonObject) = buildJsonObject {
        put("module", module)
        put("method", method)
        put("param", param)
    }

    /** 一次请求读自建（req_0）+ 收藏（req_1）两份歌单。 */
    fun playlistsBody(c: QqCredential, favSize: Int = 200): String = buildJsonObject {
        put("comm", comm(c))
        put("req_0", req("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin", buildJsonObject { put("uin", c.uin) }))
        put(
            "req_1",
            req("music.musicasset.PlaylistFavRead", "CgiGetPlaylistFavInfo", buildJsonObject {
                put("uin", c.uin)
                put("offset", 0)
                put("size", favSize)
            }),
        )
    }.toString()

    /** 一页歌单曲目。自建歌单带上目录号（含「我喜欢」201），收藏的歌单只按 disstid 读。 */
    fun songsBody(c: QqCredential?, p: AccountPlaylist, begin: Int, num: Int): String = buildJsonObject {
        put("comm", if (c == null) anonComm() else comm(c))
        put(
            "req_0",
            req("music.srfDissInfo.aiDissInfo", "uniform_get_Dissinfo", buildJsonObject {
                put("disstid", p.id.toLongOrNull() ?: 0L)
                put("dirid", if (p.owned) (p.dirId ?: 0) else 0)
                put("enc_host_uin", "")
                put("tag", 1)
                put("userinfo", 1)
                put("orderlist", 1)
                put("onlysonglist", 0)
                put("song_begin", begin)
                put("song_num", num)
            }),
        )
    }.toString()

    /**
     * 「我喜欢」的另一条读法（qqmusic-api get_fav_song 同款）：DissInfo.CgiGetDiss，disstid 传 0、dirid 201，
     * 靠 comm 里的登录凭据认人。uniform_get_Dissinfo 读「我喜欢」读不到时回落到这条。
     */
    fun likedSongsBody(c: QqCredential, begin: Int, num: Int): String = buildJsonObject {
        put("comm", comm(c))
        put(
            "req_0",
            req("music.srfDissInfo.DissInfo", "CgiGetDiss", buildJsonObject {
                put("disstid", 0)
                put("dirid", LIKED_DIR_ID)
                put("enc_host_uin", "")
                put("tag", 1)
                put("userinfo", 1)
                put("orderlist", 1)
                put("song_begin", begin)
                put("song_num", num)
            }),
        )
    }.toString()

    // ———————————————————————— 解析 ————————————————————————

    private fun parseRoot(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { this[it].text() }
    private fun JsonObject.int(vararg keys: String): Int? = pick(*keys)?.toDoubleOrNull()?.toInt()

    /** 一个子请求（或整次）的码：缺省当 0。 */
    private fun codeOf(o: JsonObject?): Int? = o?.get("code").text()?.toDoubleOrNull()?.toInt()

    private fun <T> refused(code: Int, what: String): QqRead<T> =
        if (code in EXPIRED_CODES) QqRead.Expired else QqRead.Failed("$what（code $code）")

    /** 封面地址补成 https（QQ 有时给 //y.gtimg.cn、有时给 http）。 */
    fun coverUrl(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http://") -> "https://" + s.removePrefix("http://")
            else -> s
        }
    }

    /** GetPlaylistByUin 的一条自建歌单。 */
    fun ownedPlaylist(o: JsonObject): AccountPlaylist? {
        val dirId = o.int("dirId", "dirid")
        val tid = o.pick("tid", "dissid", "id")?.takeIf { it != "0" }
        val liked = dirId == LIKED_DIR_ID
        val id = tid ?: dirId?.let { "dir$it" } ?: return null
        val name = o.pick("dirName", "dirname", "dissname", "name") ?: if (liked) "我喜欢" else return null
        return AccountPlaylist(
            id = id,
            name = name,
            cover = coverUrl(o.pick("bigpicUrl", "picUrl", "picurl", "logo")),
            trackCount = o.int("songNum", "songnum", "song_cnt"),
            dirId = dirId,
            owned = true,
            liked = liked,
        )
    }

    /** CgiGetPlaylistFavInfo 的一条收藏歌单（字段与自建的那套不一样：name / logo / songnum / nickname）。 */
    fun favoritePlaylist(o: JsonObject): AccountPlaylist? {
        val id = o.pick("tid", "dissid", "id")?.takeIf { it != "0" } ?: return null
        val name = o.pick("name", "dissname", "dirName", "title") ?: return null
        return AccountPlaylist(
            id = id,
            name = name,
            cover = coverUrl(o.pick("logo", "bigpicUrl", "picUrl", "picurl", "imgurl")),
            trackCount = o.int("songnum", "songNum", "song_cnt"),
            dirId = o.int("dirId", "dirid"),
            owned = false,
            liked = false,
            creator = o.pick("nickname", "nick", "creator_name"),
        )
    }

    /** 「我喜欢」置顶，接着自建（按上游顺序），再接收藏；同一个 id 只留第一次出现的。 */
    fun merge(owned: List<AccountPlaylist>, favorites: List<AccountPlaylist>): List<AccountPlaylist> {
        val liked = owned.filter { it.liked }
        val rest = owned.filterNot { it.liked }
        val seen = HashSet<String>()
        return (liked + rest + favorites).filter { seen.add(it.id) }
    }

    /** 解析 [playlistsBody] 的响应。 */
    fun parsePlaylists(body: String): QqRead<List<AccountPlaylist>> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        codeOf(root)?.takeIf { it != 0 }?.let { return refused(it, "整次请求被拒") }
        val created = root["req_0"].obj() ?: return QqRead.Failed("缺少自建歌单")
        codeOf(created)?.takeIf { it != 0 }?.let { return refused(it, "自建歌单读取失败") }
        val fav = root["req_1"].obj()
        val favCode = codeOf(fav) ?: 0
        // 收藏那份说没登录：凭据失效了（自建那份对公开歌单匿名也能回，不能只看它）。
        if (favCode in EXPIRED_CODES) return QqRead.Expired
        val owned = created["data"].obj()?.get("v_playlist").arr().orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::ownedPlaylist) }
        // 收藏读失败（别的码）不拖累自建：照样显示读到的。
        val favorites = if (favCode != 0) emptyList() else {
            val data = fav?.get("data").obj()
            (data?.get("v_list") ?: data?.get("v_playlist") ?: data?.get("cdlist")).arr().orEmpty()
                .mapNotNull { (it as? JsonObject)?.let(::favoritePlaylist) }
        }
        return QqRead.Ok(merge(owned, favorites))
    }

    /** songlist 里的一首。 */
    fun song(o: JsonObject): AccountSong? {
        val songObj = o["songInfo"].obj() ?: o
        val mid = songObj.pick("mid", "songmid") ?: return null
        val singers = songObj["singer"].arr().orEmpty().mapNotNull { it.obj()?.pick("name", "title") }
        val album = songObj["album"].obj()
        return AccountSong(
            songId = songObj.pick("id", "songid").orEmpty(),
            songMid = mid,
            title = songObj.pick("title", "name", "songname") ?: return null,
            artist = singers.joinToString("、").ifBlank { "未知艺术家" },
            album = album?.pick("title", "name") ?: songObj.pick("albumname").orEmpty(),
            albumMid = album?.pick("mid", "pmid") ?: songObj.pick("albummid").orEmpty(),
            mediaMid = songObj["file"].obj()?.pick("media_mid") ?: songObj.pick("strMediaMid", "media_mid").orEmpty(),
            durationSec = songObj.pick("interval")?.toLongOrNull() ?: 0L,
        )
    }

    /** 解析 [songsBody] 的响应。 */
    fun parseSongs(body: String): QqRead<QqSongPage> {
        val root = parseRoot(body) ?: return QqRead.Failed("响应读不出来")
        codeOf(root)?.takeIf { it != 0 }?.let { return refused(it, "整次请求被拒") }
        val node = root["req_0"].obj() ?: return QqRead.Failed("缺少歌单曲目")
        codeOf(node)?.takeIf { it != 0 }?.let { return refused(it, "歌单曲目读取失败") }
        val data = node["data"].obj() ?: return QqRead.Failed("缺少歌单曲目")
        codeOf(data)?.takeIf { it != 0 }?.let { return refused(it, "歌单曲目读取失败") }
        val list = data["songlist"].arr().orEmpty()
        val songs = list.mapNotNull { it.obj()?.let(::song) }
        val total = data.int("total_song_num", "songlist_size") ?: data["dirinfo"].obj()?.int("songnum")
        return QqRead.Ok(QqSongPage(songs, total, list.size))
    }

    /**
     * 解析 [cdInfoUrl] 的响应（`cdlist[0].songlist`，一次回整张）。可能包在 jsonp 回调里，先剥掉。
     * 没有 cdlist / 没有歌单名（读不到不公开的歌单时上游回空壳）算失败，交给调用方决定。
     */
    fun parseCdInfo(body: String): QqRead<QqSongPage> {
        val trimmed = body.trim().let { t ->
            val a = t.indexOf('{'); val b = t.lastIndexOf('}')
            if (a > 0 && b > a) t.substring(a, b + 1) else t
        }
        val root = parseRoot(trimmed) ?: return QqRead.Failed("响应读不出来")
        codeOf(root)?.takeIf { it != 0 }?.let { return QqRead.Failed("匿名歌单详情被拒（code $it）") }
        val cd = root["cdlist"].arr()?.firstOrNull().obj() ?: return QqRead.Failed("匿名歌单详情没有 cdlist")
        val list = cd["songlist"].arr().orEmpty()
        if (list.isEmpty() && cd.pick("dissname") == null) return QqRead.Failed("匿名读不到这张歌单")
        val songs = list.mapNotNull { it.obj()?.let(::song) }
        val total = cd.int("total_song_num", "songnum", "cur_song_num")
        return QqRead.Ok(QqSongPage(songs, total, list.size))
    }
}

/**
 * 登录态读不到时回落到未登录读法（GYACCOUNT 日志）：按顺序试 [attempts]，第一份「拿到歌」的就用；
 * 都拿不到时，若有一份是「成功但空」（真的空歌单）就回它，否则抛第一份的错（通常是登录态那份，好提示重新登录）。
 * [expectSongs] = 歌单列表说它有歌：这时「成功但空」也当失败继续往下试。
 */
object QqFallback {
    fun <T> firstWithSongs(
        expectSongs: Boolean,
        attempts: List<Pair<String, () -> List<T>>>,
        log: (String) -> Unit = {},
    ): List<T> {
        var empty: List<T>? = null
        var firstError: Throwable? = null
        for ((name, run) in attempts) {
            val r = runCatching(run)
            val v = r.getOrNull()
            if (v != null && v.isNotEmpty()) {
                log("qq tracks via $name n=${v.size}")
                return v
            }
            if (v != null) { log("qq tracks via $name empty"); if (empty == null) empty = v }
            r.exceptionOrNull()?.let { e ->
                log("qq tracks via $name failed: ${e.message}")
                if (firstError == null) firstError = e
            }
            if (v != null && !expectSongs) return v
        }
        empty?.let { return it }
        throw firstError ?: IllegalStateException("读取失败")
    }
}
