package com.guangyue.core.data.account

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 酷狗账号歌单（夹具照 Folia kugouProvider.normalizeCollection / normalizeKugouSong / isKugouLikedPlaylist 读的字段，
 * 与 KuGouMusicApi /user/playlist（v7/get_all_list：data.info）、/playlist/track/all（get_other_list_file_nofilt：
 * data.songs）的回答形状）。签名 / 设备号 / 换 token 的加密值是用 Node 照 KuGouMusicApi util/helper.js、util/util.js、
 * util/crypto.js 的算法对同样的固定输入算出来的。
 */
class KugouAccountApiTest {

    private val cred = KugouCredential("123456789", "abcdef0123456789")

    // ———————— 签名 ————————

    @Test fun midIsDecimalOfMd5() {
        assertEquals("238507642264157098001785129819697759005", KugouAccountApi.mid("guangyue-test-guid"))
    }

    @Test fun playlistsRequestMatchesKuGouMusicApi() {
        val r = KugouAccountApi.playlistsRequest(cred, "111", 1_700_000_000L, page = 1)
        assertEquals("/v7/get_all_list", r.path)
        assertEquals("""{"userid":123456789,"token":"abcdef0123456789","total_ver":979,"type":2,"page":1,"pagesize":100}""", r.body)
        assertEquals("4e7b3713e3777f54c9d636b0441d33fc", r.query["signature"])
        assertEquals("1005", r.query["appid"])
        assertEquals("20489", r.query["clientver"])
        assertEquals("1", r.query["plat"])
        assertEquals("cloudlist.service.kugou.com", r.headers["x-router"])
    }

    @Test fun tracksRequestMatchesKuGouMusicApi() {
        val r = KugouAccountApi.tracksRequest(cred, "111", 1_700_000_000L, "collection_3_123456789_2_0", begin = 100)
        assertEquals("", r.body)
        assertEquals("c41bf74a711a6a7ac9c9eeadb9c8a1fe", r.query["signature"])
        assertEquals("100", r.query["begin_idx"])
        assertEquals("collection_3_123456789_2_0", r.query["global_collection_id"])
    }

    @Test fun loginByTokenCrypto() {
        assertEquals(
            "cf1ffdcaf302f47f92991dacab717818ce992a540dbcf00e8a73b2c42dadfb9f618ff5b2dd81d9741cb7f3e4fa82b9b1dc2cbf9af8bf3cd35bc8c6c16475fe4c",
            KugouAccountApi.aesEncryptHex("""{"clienttime":1700000000,"token":"abcdef0123456789"}""", "90b8382a1bb4ccdcf063102053fd75b8", "f063102053fd75b8"),
        )
        val (k, iv) = KugouAccountApi.tempAesKey("abcdefghijklmnop")
        assertEquals("71d7972d8b00ba9a8c0228465243f08b", KugouAccountApi.aesEncryptHex("{}", k, iv))
        assertEquals(
            "2fc44d7e66b57992ac5bdc24e950d487694f534fba595ec25ae550381c58c06a7e272dd0f4dde70d27539f1a65d4537ea573f1ea6d74c7389258766be231ae968c9f420510bf9d9d9f6f01a0ef8015418201f2fee03aee4c42d52a6e45f1ec884b888e405d95d826e0cb17372c4254694d88e556f33eff005165a903f29136bc",
            KugouAccountApi.rsaRawHex("""{"clienttime_ms":1700000000123,"key":"abcdefghijklmnop"}"""),
        )
        val req = KugouAccountApi.loginByTokenRequest(cred, "111", 1_700_000_000_123L, "abcdefghijklmnop")
        val body = kotlinx.serialization.json.Json.parseToJsonElement(req.body) as kotlinx.serialization.json.JsonObject
        assertEquals(listOf("dfid", "p3", "plat", "t1", "t2", "t3", "pk", "params", "userid", "clienttime_ms"), body.keys.toList())
        assertEquals("71d7972d8b00ba9a8c0228465243f08b", body["params"]!!.jsonPrimitive.content)
        assertEquals("login.user.kugou.com", req.headers["x-router"])
    }

    @Test fun loginByTokenReadsSecuParams() {
        val secu = "e43303aaec8370381395b39c64f5989383494032db0998451a7c2d1a943f8ec2fa4ae0a9b15ef91371cb2fb61406f472"
        val c = KugouAccountApi.parseLoginByToken("""{"status":1,"error_code":0,"data":{"userid":123456789,"secu_params":"$secu"}}""", "abcdefghijklmnop")
        assertEquals(KugouCredential("123456789", "newtoken999"), c)
        assertNull(KugouAccountApi.parseLoginByToken("""{"status":0,"error_code":20018,"data":{}}""", "abcdefghijklmnop"))
        assertEquals(
            KugouCredential("5", "plain"),
            KugouAccountApi.parseLoginByToken("""{"status":1,"data":{"userid":"5","token":"plain"}}""", "x"),
        )
    }

    // ———————— 凭据 ————————

    @Test fun credentialFromQrFieldsAndOldPluginVariables() {
        assertEquals(cred, KugouCredential.fromFields(mapOf("token" to "abcdef0123456789", "userid" to "123456789", "cookie" to "token=abcdef0123456789; userid=123456789")))
        assertEquals(cred, KugouCredential.fromFields(mapOf("酷狗 Cookie" to "KugooID=123456789; t=abcdef0123456789; vip_token=v")))
        assertEquals(cred, KugouCredential.fromFields(mapOf("vip_token" to "nope", "kg_token" to "abcdef0123456789", "user_id" to "123456789")))
        assertNull(KugouCredential.fromFields(mapOf("token" to "abc")))
        assertNull(KugouCredential.fromFields(mapOf("token" to "abc", "userid" to "0")))
    }

    // ———————— 歌单 ————————

    private val playlists = """
        {"status":1,"error_code":0,"data":{"list_count":6,"info":[
          {"listid":8,"name":"通勤路上","global_collection_id":"collection_3_123456789_8_0","count":48,"type":0,"source":1,
           "pic":"http://c1.kgimg.com/custom/{size}/20240101/a.jpg","list_create_userid":123456789,"list_create_username":"广乐测试"},
          {"listid":2,"name":"我喜欢","global_collection_id":"collection_3_123456789_2_0","count":326,"type":0,"source":1,"pic":""},
          {"listid":9,"name":"华语经典","global_collection_id":"collection_3_888_5_0","count":100,"type":1,"source":1,
           "pic":"//c1.kgimg.com/custom/150/b.jpg","list_create_userid":888,"list_create_username":"酷狗音乐"},
          {"listid":10,"name":"叶惠美","type":1,"source":2,"musiclib_id":"960399","count":11},
          {"listid":11,"name":"空歌单","global_collection_id":"collection_3_123456789_11_0","count":0,"type":0},
          {"listid":12,"name":"没有 id 的"}
        ]}}
    """.trimIndent()

    @Test fun likedFirstThenOwnedThenCollectedAlbumsSkipped() {
        val r = KugouAccountApi.parsePlaylists(playlists)
        assertTrue(r is QqRead.Ok)
        val list = (r as QqRead.Ok).value
        assertEquals(
            listOf("collection_3_123456789_2_0", "collection_3_123456789_8_0", "collection_3_123456789_11_0", "collection_3_888_5_0"),
            list.map { it.id },
        )
        assertTrue(list[0].liked)
        assertTrue(list[0].owned)
        assertEquals(326, list[0].trackCount)
        assertNull(list[0].cover)
        assertEquals("https://c1.kgimg.com/custom/480/20240101/a.jpg", list[1].cover)
        assertEquals("https://c1.kgimg.com/custom/150/b.jpg", list[3].cover)
        assertFalse(list[3].owned)
        assertEquals("酷狗音乐", list[3].creator)
        assertEquals(1, list.count { it.liked })
    }

    @Test fun pagingUsesRawCountAndTotal() {
        val page = (KugouAccountApi.parsePlaylistPage(playlists) as QqRead.Ok).value
        assertEquals(6, page.listed)
        assertEquals(6, page.total)
        assertFalse(KugouAccountApi.hasMore(page, 6))
        assertTrue(KugouAccountApi.hasMore(page.copy(total = null, listed = 100), 100))
    }

    @Test fun likedByListIdEvenWithOtherName() {
        val body = """{"status":1,"error_code":0,"data":{"info":[{"listid":2,"name":"红心","global_collection_id":"g2","type":0}]}}"""
        val list = (KugouAccountApi.parsePlaylists(body) as QqRead.Ok).value
        assertTrue(list.single().liked)
    }

    @Test fun error152IsExpiredOtherCodesFail() {
        assertEquals(QqRead.Expired, KugouAccountApi.parsePlaylists("""{"status":0,"error_code":152,"data":[]}"""))
        val r = KugouAccountApi.parsePlaylists("""{"status":0,"error_code":20017,"error_msg":"x"}""")
        assertTrue(r is QqRead.Failed)
        assertEquals(20017, KugouAccountApi.errorCodeOf("""{"status":0,"error_code":20017}"""))
        assertNull(KugouAccountApi.errorCodeOf(playlists))
        assertTrue(KugouAccountApi.parsePlaylists("not json") is QqRead.Failed)
    }

    // ———————— 曲目 ————————

    private val songs = """
        {"status":1,"error_code":0,"data":{"begin_idx":0,"pagesize":100,"count":3,"songs":[
          {"hash":"b0ed1b8ee05bd3d7e6a1d1f44a3e1c11","name":"周杰伦 - 晴天","timelen":269000,"mixsongid":"32042828","album_id":"960399",
           "singerinfo":[{"id":3520,"name":"周杰伦"}],"albuminfo":{"id":960399,"name":"叶惠美"},
           "cover":"http://imge.kugou.com/stdmusic/{size}/20200909/x.jpg"},
          {"hash":"aa11","name":"后来","timelen":341,"singerinfo":[{"id":1,"name":"刘若英"}],
           "trans_param":{"union_cover":"http://imge.kugou.com/stdmusic/{size}/y.jpg"}},
          {"hash":"cc22","name":"陈奕迅、王菲 - 因为爱情.mp3","timelen":214000},
          {"name":"没有 hash 的歌"}
        ]}}
    """.trimIndent()

    @Test fun songsNormalized() {
        val page = (KugouAccountApi.parseSongPage(songs) as QqRead.Ok).value
        assertEquals(4, page.listed)
        assertNull(page.total) // count 3 只是这一页
        assertEquals(3, page.songs.size)
        val s = page.songs[0]
        assertEquals("B0ED1B8EE05BD3D7E6A1D1F44A3E1C11", s.hash)
        assertEquals("晴天", s.title)
        assertEquals("周杰伦", s.artist)
        assertEquals("叶惠美", s.album)
        assertEquals("960399", s.albumId)
        assertEquals("32042828", s.albumAudioId)
        assertEquals(269_000L, s.durationMs)
        assertEquals("https://imge.kugou.com/stdmusic/480/20200909/x.jpg", s.cover)
        assertEquals("后来", page.songs[1].title)
        assertEquals(341_000L, page.songs[1].durationMs)
        assertEquals("https://imge.kugou.com/stdmusic/480/y.jpg", page.songs[1].cover)
        assertEquals("因为爱情", page.songs[2].title)
        assertEquals("陈奕迅、王菲", page.songs[2].artist)
        assertFalse(KugouAccountApi.songsMore(page, 4))
    }

    @Test fun rawCarriesHashAndAlbumForPlayback() {
        val s = (KugouAccountApi.parseSongPage(songs) as QqRead.Ok).value.songs[0]
        val raw = s.toRaw("kugou")
        assertEquals("kugou", raw["platform"]!!.jsonPrimitive.content)
        assertEquals(s.hash, raw["id"]!!.jsonPrimitive.content)
        assertEquals(s.hash, raw["hash"]!!.jsonPrimitive.content)
        assertEquals("960399", raw["album_id"]!!.jsonPrimitive.content)
        assertEquals("32042828", raw["album_audio_id"]!!.jsonPrimitive.content)
        assertEquals(269L, raw["duration"]!!.jsonPrimitive.content.toLong())
    }

    @Test fun songsExpiredAndFullPageAsksMore() {
        assertEquals(QqRead.Expired, KugouAccountApi.parseSongPage("""{"status":0,"error_code":152}"""))
        val full = KugouSongPage(emptyList(), listed = 100, total = 250)
        assertTrue(KugouAccountApi.songsMore(full, 100))
        assertFalse(KugouAccountApi.songsMore(full, 250))
    }
}
