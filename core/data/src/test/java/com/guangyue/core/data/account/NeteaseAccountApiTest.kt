package com.guangyue.core.data.account

import com.guangyue.core.plugin.login.NeteaseCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网易云账号歌单解析（夹具照 Folia neteaseProvider.normalizeCollection / normalizeNeteaseSong 读的字段与
 * NeteaseCloudMusicApi /user/playlist、/v6/playlist/detail、/v3/song/detail 的回答形状：
 * 「我喜欢的音乐」= specialType 5 且 creator.userId 是自己；收藏的歌单 subscribed=true，创建者是别人）。
 */
class NeteaseAccountApiTest {

    private val uid = "32953014"

    private val playlists = """
        {"version":"0","more":false,"code":200,"playlist":[
          {"id":3001,"name":"深夜写代码","coverImgUrl":"http://p1.music.126.net/a.jpg","trackCount":87,"specialType":0,"subscribed":false,
           "userId":32953014,"creator":{"userId":32953014,"nickname":"广乐测试"}},
          {"id":2001,"name":"广乐测试喜欢的音乐","coverImgUrl":"https://p2.music.126.net/l.jpg","trackCount":512,"specialType":5,"subscribed":false,
           "userId":32953014,"creator":{"userId":32953014,"nickname":"广乐测试"}},
          {"id":4001,"name":"别人喜欢的音乐","coverImgUrl":"","trackCount":30,"specialType":5,"subscribed":true,
           "userId":1,"creator":{"userId":1,"nickname":"路人"}},
          {"id":5001,"name":"云音乐飙升榜","coverImgUrl":"//p1.music.126.net/c.jpg","trackCount":100,"specialType":10,"subscribed":true,
           "userId":1,"creator":{"userId":1,"nickname":"网易云音乐"}},
          {"id":3002,"name":"空歌单","trackCount":0,"specialType":0,"subscribed":false,"creator":{"userId":32953014,"nickname":"广乐测试"}}
        ]}
    """.trimIndent()

    @Test fun likedFirstThenOwnedThenSubscribed() {
        val r = NeteaseAccountApi.parsePlaylists(playlists, uid)
        assertTrue(r is QqRead.Ok)
        val list = (r as QqRead.Ok).value
        assertEquals(listOf("2001", "3001", "3002", "4001", "5001"), list.map { it.id })
        assertTrue(list[0].liked)
        assertTrue(list[0].owned)
        assertEquals(512, list[0].trackCount)
        assertEquals("https://p1.music.126.net/a.jpg", list[1].cover)
        assertEquals("https://p1.music.126.net/c.jpg", list[4].cover)
        assertEquals("网易云音乐", list[4].creator)
    }

    @Test fun someoneElsesLikedListIsNotOurs() {
        val list = (NeteaseAccountApi.parsePlaylists(playlists, uid) as QqRead.Ok).value
        val other = list.first { it.id == "4001" }
        assertFalse(other.liked)
        assertFalse(other.owned)
        assertEquals(1, list.count { it.liked })
    }

    @Test fun emptyPlaylistKeepsZeroAndNoCover() {
        val list = (NeteaseAccountApi.parsePlaylists(playlists, uid) as QqRead.Ok).value
        val empty = list.first { it.id == "3002" }
        assertEquals(0, empty.trackCount)
        assertNull(empty.cover)
        assertTrue(empty.owned)
    }

    @Test fun noPlaylistsIsOkAndEmpty() {
        val r = NeteaseAccountApi.parsePlaylists("""{"code":200,"more":false,"playlist":[]}""", uid)
        assertEquals(QqRead.Ok(emptyList<AccountPlaylist>()), r)
    }

    @Test fun pagingReadsMoreFlag() {
        val p = NeteaseAccountApi.parsePlaylistPage("""{"code":200,"more":true,"playlist":[{"id":1,"name":"a","creator":{"userId":9}}]}""", uid)
        val page = (p as QqRead.Ok).value
        assertTrue(page.more)
        assertEquals(1, page.listed)
    }

    @Test fun expiredCodes() {
        assertEquals(QqRead.Expired, NeteaseAccountApi.parsePlaylists("""{"code":301,"msg":"需要登录"}""", uid))
        assertEquals(QqRead.Expired, NeteaseAccountApi.parseAccount("""{"code":401}"""))
        assertEquals(QqRead.Expired, NeteaseAccountApi.parseDetail("""{"code":403}"""))
        // code 200 但没有 profile / account：没有人登录（MUSIC_U 失效后网易云就这样回）。
        assertEquals(QqRead.Expired, NeteaseAccountApi.parseAccount("""{"code":200,"account":null,"profile":null}"""))
    }

    @Test fun otherRefusalsAreFailuresNotExpiry() {
        assertTrue(NeteaseAccountApi.parsePlaylists("""{"code":-462,"message":"风控"}""", uid) is QqRead.Failed)
        assertTrue(NeteaseAccountApi.parsePlaylists("""{"playlist":[]}""", uid) is QqRead.Failed)
        assertTrue(NeteaseAccountApi.parsePlaylists("<html>502</html>", uid) is QqRead.Failed)
        assertTrue(NeteaseAccountApi.parseSongDetail("""{"code":500}""") is QqRead.Failed)
    }

    @Test fun accountUid() {
        val r = NeteaseAccountApi.parseAccount("""{"code":200,"account":{"id":32953014},"profile":{"userId":32953014,"nickname":"广乐测试"}}""")
        assertEquals(QqRead.Ok(uid), r)
        assertEquals(QqRead.Ok("7"), NeteaseAccountApi.parseAccount("""{"code":200,"account":{"id":7},"profile":null}"""))
    }

    private val detail = """
        {"code":200,"playlist":{"id":2001,"trackCount":3,
          "trackIds":[{"id":186016,"v":1},{"id":1901371647,"v":2},{"id":999}],
          "tracks":[{"name":"晴天","id":186016,"ar":[{"id":6452,"name":"周杰伦"}],"al":{"id":18905,"name":"叶惠美","picUrl":"http://p1.music.126.net/q.jpg"},"dt":269000}]},
         "privileges":[]}
    """.trimIndent()

    private val songDetail = """
        {"code":200,"songs":[
          {"name":"孤勇者","id":1901371647,"ar":[{"id":2116,"name":"陈奕迅"},{"id":1,"name":"合唱"}],"al":{"id":1,"name":"孤勇者","picUrl":"https://p2.music.126.net/g.jpg"},"dt":256000}
        ],"privileges":[]}
    """.trimIndent()

    @Test fun tracksInPlaylistOrderAndMissingFilledByBatches() {
        val d = (NeteaseAccountApi.parseDetail(detail) as QqRead.Ok).value
        assertEquals(listOf("186016", "1901371647", "999"), d.trackIds)
        assertEquals(listOf(listOf("1901371647", "999")), NeteaseAccountApi.missingBatches(d))
        assertEquals(listOf(listOf("1901371647"), listOf("999")), NeteaseAccountApi.missingBatches(d, 1))
        val extra = (NeteaseAccountApi.parseSongDetail(songDetail) as QqRead.Ok).value
        val songs = NeteaseAccountApi.assemble(d, extra)
        // 999 网易云没给（下架）：略过，不留空位。
        assertEquals(listOf("晴天", "孤勇者"), songs.map { it.title })
        val q = songs[0]
        assertEquals("周杰伦", q.artist)
        assertEquals("叶惠美", q.album)
        assertEquals("18905", q.albumId)
        assertEquals("https://p1.music.126.net/q.jpg", q.cover)
        assertEquals(269000L, q.durationMs)
        assertEquals("陈奕迅、合唱", songs[1].artist)
    }

    @Test fun detailWithoutTrackIdsFallsBackToTracks() {
        val d = (NeteaseAccountApi.parseDetail("""{"code":200,"playlist":{"tracks":[{"id":5,"name":"a","ar":[]}]}}""") as QqRead.Ok).value
        assertEquals(listOf("5"), d.trackIds)
        assertEquals("未知艺术家", d.tracks[0].artist)
    }

    @Test fun rawCarriesIdsForPluginAndLx() {
        val song = NeteaseSong("186016", "晴天", "周杰伦", "叶惠美", "18905", "https://p1.music.126.net/q.jpg", 269000)
        val raw = song.toRaw("网易云音乐")
        assertEquals("网易云音乐", raw["platform"]!!.jsonPrimitive.content)
        for (k in listOf("id", "songmid", "musicId")) assertEquals("186016", raw[k]!!.jsonPrimitive.content)
        assertEquals("269", raw["duration"]!!.jsonPrimitive.content)
        assertEquals("18905", raw["albumId"]!!.jsonPrimitive.content)
    }

    @Test fun songDetailRequestShape() {
        val c = NeteaseAccountApi.songDetailData(listOf("1", "22"))["c"] as JsonPrimitive
        assertEquals("""[{"id":1},{"id":22}]""", c.content)
        val p = NeteaseAccountApi.playlistsData(uid, 0)
        assertEquals(uid, p["uid"]!!.jsonPrimitive.content)
        assertEquals("1000", p["limit"]!!.jsonPrimitive.content)
    }

    @Test fun credentialFromFieldsAndCookies() {
        assertEquals(NeteaseCredential("abc", "tok"), NeteaseCredential.fromFields(mapOf("MUSIC_U" to "abc", "__csrf" to "tok", "cookie" to "MUSIC_U=abc; __csrf=tok")))
        // 0.0.30 插件变量：只有一条 cookie。
        assertEquals(NeteaseCredential("xyz", "c1"), NeteaseCredential.fromFields(mapOf("网易云Cookie" to "os=pc; MUSIC_U=xyz; __csrf=c1")))
        assertEquals(NeteaseCredential("u2", ""), NeteaseCredential.fromFields(mapOf("music_u" to "u2")))
        assertNull(NeteaseCredential.fromFields(mapOf("cookie" to "os=pc", "音质" to "standard")))
    }

    // ———————— 加密 ————————

    @Test fun eapiKnownAnswer() {
        // openssl enc -aes-128-ecb -K hex("e82ckenh8dichen8") 对同一明文算出的密文。
        val expected = "B794DDEDF8B618087E534DC803FDE82C5E0023679BCAEF6A43314E846D0A2C02AACDE0BA806BEBADAA74446848D5F0C2C63483300408358B42E1AD10F9C440EC921E9F8099B76BE976AC78708A2344517B838744B67BBFB0460965D9E439C989B46B557F65DC6CBB9C0A485C115F15FD"
        assertEquals(expected, NeteaseCrypto.eapiParams("/api/user/playlist", """{"uid":"1","limit":"1000"}"""))
    }

    @Test fun eapiRequestIsRepeatableAndRoundTrips() {
        val header = NeteaseCrypto.desktopHeader("ABCDEF0123", "csrf1", "1700000000000_0001")
        val data = NeteaseAccountApi.playlistsData(uid, 0)
        val a = NeteaseAccountApi.params(NeteaseAccountApi.PLAYLISTS_PATH, data, header)
        val b = NeteaseAccountApi.params(NeteaseAccountApi.PLAYLISTS_PATH, data, header)
        assertEquals(a, b)
        val plain = NeteaseCrypto.eapiDecrypt(a)
        val parts = plain.split("-36cd479b6b5-")
        assertEquals("/api/user/playlist", parts[0])
        val body = Json.parseToJsonElement(parts[1]).jsonObject
        assertEquals(uid, body["uid"]!!.jsonPrimitive.content)
        assertEquals("false", body["e_r"]!!.jsonPrimitive.content)
        assertEquals("csrf1", (body["header"] as JsonObject)["__csrf"]!!.jsonPrimitive.content)
        assertEquals(NeteaseCrypto.md5Hex("nobody/api/user/playlistuse${parts[1]}md5forencrypt"), parts[2])
        // 换一个 requestId，密文就不同（header 进了加密体）。
        val c = NeteaseAccountApi.params(NeteaseAccountApi.PLAYLISTS_PATH, data, header + ("requestId" to "x"))
        assertFalse(a == c)
    }

    @Test fun eapiUrlAndCookie() {
        assertEquals("https://interface.music.163.com/eapi/v6/playlist/detail", NeteaseCrypto.eapiUrl("/api/v6/playlist/detail"))
        assertEquals("os=pc; MUSIC_U=m", NeteaseCrypto.cookieHeader(mapOf("os" to "pc"), "m"))
        assertEquals("os=pc", NeteaseCrypto.cookieHeader(mapOf("os" to "pc"), ""))
    }
}
