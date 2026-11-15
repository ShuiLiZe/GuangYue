package com.guangyue.core.data.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QQ 账号歌单解析（夹具照 Folia qqNormalize.normalizeQqCollection 的注释与实测字段：
 * GetPlaylistByUin 的 `v_playlist` 自建条目带 dirName / songNum / dirId，「我喜欢」= dirId 201；
 * 收藏条目是另一套 name / songnum / logo / orderTime / dirType，而且也带 dirId（创建者账号里的，如 36）。
 */
class QqAccountApiTest {

    private val owned = """
        {"code":0,"ts":1,
         "req_0":{"code":0,"data":{"v_playlist":[
            {"tid":8811,"dirId":1,"dirName":"通勤路上","songNum":48,"picUrl":"http://qpic.y.qq.com/a.jpg","dirShow":1,"updateTime":1731600000},
            {"tid":9777066643,"dirId":2,"dirName":"私藏","songNum":0,"bigpicUrl":"//y.gtimg.cn/b.jpg","dirShow":2},
            {"tid":7001,"dirId":201,"dirName":"我喜欢","songNum":326,"picUrl":""}
         ]}},
         "req_1":{"code":0,"data":{"total":2,"v_list":[
            {"tid":5501,"dirId":36,"dirType":0,"name":"华语经典","songnum":100,"logo":"https://y.gtimg.cn/c.jpg","nickname":"QQ音乐官方","orderTime":1700000000},
            {"tid":7001,"dirId":201,"name":"别人的我喜欢","songnum":3}
         ]}}}
    """.trimIndent()

    @Test fun likedFirstThenOwnedThenFavorites() {
        val r = QqAccountApi.parsePlaylists(owned)
        assertTrue(r is QqRead.Ok)
        val list = (r as QqRead.Ok).value
        assertEquals(listOf("7001", "8811", "9777066643", "5501"), list.map { it.id })
        val liked = list[0]
        assertTrue(liked.liked)
        assertTrue(liked.owned)
        assertEquals("我喜欢", liked.name)
        assertEquals(326, liked.trackCount)
        assertNull(liked.cover)
        assertEquals("https://qpic.y.qq.com/a.jpg", list[1].cover)
        assertEquals("https://y.gtimg.cn/b.jpg", list[2].cover)
        assertEquals(0, list[2].trackCount)
    }

    @Test fun favoriteWithDirIdIsNotOwnedNorLiked() {
        val list = (QqAccountApi.parsePlaylists(owned) as QqRead.Ok).value
        val fav = list.last()
        assertFalse(fav.owned)
        assertFalse(fav.liked)
        assertEquals(36, fav.dirId)
        assertEquals("华语经典", fav.name)
        assertEquals(100, fav.trackCount)
        assertEquals("QQ音乐官方", fav.creator)
        // 收藏来的别人的「我喜欢」（同 id）被自建的那张去重掉，也不会被标成自己的。
        assertEquals(1, list.count { it.liked })
    }

    @Test fun likedWithoutNameStillShows() {
        val body = """{"code":0,"req_0":{"code":0,"data":{"v_playlist":[{"tid":0,"dirId":201,"songNum":0}]}},"req_1":{"code":0,"data":{"v_list":[]}}}"""
        val list = (QqAccountApi.parsePlaylists(body) as QqRead.Ok).value
        assertEquals(1, list.size)
        assertEquals("dir201", list[0].id)
        assertEquals("我喜欢", list[0].name)
    }

    @Test fun emptyAccount() {
        val body = """{"code":0,"req_0":{"code":0,"data":{"v_playlist":[]}},"req_1":{"code":0,"data":{"v_list":[],"total":0}}}"""
        val r = QqAccountApi.parsePlaylists(body)
        assertTrue(r is QqRead.Ok)
        assertTrue((r as QqRead.Ok).value.isEmpty())
    }

    @Test fun expiredCodesAreRecognised() {
        // 收藏那份说没登录（1000），自建那份匿名照样回：算登录过期。
        val fav1000 = """{"code":0,"req_0":{"code":0,"data":{"v_playlist":[]}},"req_1":{"code":1000}}"""
        assertEquals(QqRead.Expired, QqAccountApi.parsePlaylists(fav1000))
        val own104401 = """{"code":0,"req_0":{"code":104401},"req_1":{"code":104401}}"""
        assertEquals(QqRead.Expired, QqAccountApi.parsePlaylists(own104401))
        val whole1000 = """{"code":1000}"""
        assertEquals(QqRead.Expired, QqAccountApi.parsePlaylists(whole1000))
    }

    @Test fun otherRefusalsAreFailuresNotExpiry() {
        assertTrue(QqAccountApi.parsePlaylists("""{"code":0,"req_0":{"code":104400}}""") is QqRead.Failed)
        assertTrue(QqAccountApi.parsePlaylists("""{"code":500000}""") is QqRead.Failed)
        assertTrue(QqAccountApi.parsePlaylists("<html>") is QqRead.Failed)
        // 收藏读失败（非登录码）不拖累自建。
        val favBroken = """{"code":0,"req_0":{"code":0,"data":{"v_playlist":[{"tid":1,"dirId":1,"dirName":"a","songNum":1}]}},"req_1":{"code":2001}}"""
        assertEquals(listOf("1"), (QqAccountApi.parsePlaylists(favBroken) as QqRead.Ok).value.map { it.id })
    }

    private val songs = """
        {"code":0,"req_0":{"code":0,"data":{"code":0,"total_song_num":2,"songlist_size":2,
          "dirinfo":{"id":7001,"title":"我喜欢","songnum":2},
          "songlist":[
            {"id":97773,"mid":"0039MnYb0qxYhV","name":"晴天","title":"晴天","interval":269,
             "singer":[{"id":4558,"mid":"0025NhlN2yWrP4","name":"周杰伦"}],
             "album":{"id":8220,"mid":"000MkMni19ClKG","name":"叶惠美","title":"叶惠美"},
             "file":{"media_mid":"0039MnYb0qxYhV"}},
            {"id":1,"mid":"m2","title":"合唱","singer":[{"name":"甲"},{"name":"乙"}],"album":{"mid":"","name":""}},
            {"id":2,"title":"没有 mid 的歌不收"}
          ]}}}
    """.trimIndent()

    @Test fun parsesSongsForPlayback() {
        val page = (QqAccountApi.parseSongs(songs) as QqRead.Ok).value
        assertEquals(2, page.total)
        assertEquals(3, page.listed)
        assertEquals(2, page.songs.size)
        val s = page.songs[0]
        assertEquals("97773", s.id)
        assertEquals("0039MnYb0qxYhV", s.songMid)
        assertEquals("周杰伦", s.artist)
        assertEquals("叶惠美", s.album)
        assertEquals(269L, s.durationSec)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R800x800M000000MkMni19ClKG.jpg", s.artwork)
        assertEquals("甲、乙", page.songs[1].artist)
        assertNull(page.songs[1].artwork)
        // 交给插件 / 洛雪取链的原始对象带 songmid 与 strMediaMid。
        val raw = s.toRaw("QQ音乐")
        assertEquals("0039MnYb0qxYhV", raw["songmid"]!!.jsonPrimitive.content)
        assertEquals("0039MnYb0qxYhV", raw["strMediaMid"]!!.jsonPrimitive.content)
        assertEquals("QQ音乐", raw["platform"]!!.jsonPrimitive.content)
        assertEquals("97773", raw["id"]!!.jsonPrimitive.content)
    }

    @Test fun songsExpiredAndEmpty() {
        assertEquals(QqRead.Expired, QqAccountApi.parseSongs("""{"code":0,"req_0":{"code":1000}}"""))
        val empty = (QqAccountApi.parseSongs("""{"code":0,"req_0":{"code":0,"data":{"songlist":[],"total_song_num":0}}}""") as QqRead.Ok).value
        assertTrue(empty.songs.isEmpty())
        assertEquals(0, empty.total)
    }

    @Test fun requestsCarryCredential() {
        val c = QqCredential("12345", "Q_H_L_abc")
        assertEquals(2, c.loginType)
        assertEquals(1, QqCredential("1", "W_X_k").loginType)
        val body = Json.parseToJsonElement(QqAccountApi.playlistsBody(c)).jsonObject
        val comm = body["comm"]!!.jsonObject
        assertEquals("Q_H_L_abc", comm["authst"]!!.jsonPrimitive.content)
        assertEquals(12345L, comm["uin"]!!.jsonPrimitive.long)
        assertEquals(QqAccountApi.gtk("Q_H_L_abc"), comm["g_tk"]!!.jsonPrimitive.long)
        assertEquals("GetPlaylistByUin", body["req_0"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertEquals("CgiGetPlaylistFavInfo", body["req_1"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertTrue(QqAccountApi.cookie(c).contains("qqmusic_key=Q_H_L_abc"))

        val liked = AccountPlaylist("7001", "我喜欢", dirId = 201, owned = true, liked = true)
        val p = Json.parseToJsonElement(QqAccountApi.songsBody(c, liked, 0, 500)).jsonObject["req_0"]!!.jsonObject["param"]!!.jsonObject
        assertEquals(7001L, p["disstid"]!!.jsonPrimitive.long)
        assertEquals(201L, p["dirid"]!!.jsonPrimitive.long)
        val fav = AccountPlaylist("5501", "华语经典", dirId = 36, owned = false)
        val q = Json.parseToJsonElement(QqAccountApi.songsBody(c, fav, 0, 500)).jsonObject["req_0"]!!.jsonObject["param"]!!.jsonObject
        assertEquals(0L, q["dirid"]!!.jsonPrimitive.long)
    }

    @Test fun credentialFromLoginFieldsAndPluginVariables() {
        assertEquals(QqCredential("12345", "Q_H_L_k"), QqCredential.fromFields(mapOf("uin" to "12345", "musickey" to "Q_H_L_k", "cookie" to "uin=12345; qm_keyst=Q_H_L_k")))
        // 旧版本只写进了插件账号：变量名各异，cookie 串里的 uin 可能是 o0 前缀。
        assertEquals(QqCredential("12345", "Q_H_L_k"), QqCredential.fromFields(mapOf("QQ Cookie" to "uin=o0012345; qqmusic_key=Q_H_L_k; foo=1")))
        assertEquals(QqCredential("777", "W_X_k"), QqCredential.fromFields(mapOf("qm_keyst" to "W_X_k", "uin" to "777")))
        assertNull(QqCredential.fromFields(mapOf("token" to "x")))
        assertNull(QqCredential.fromFields(mapOf("uin" to "1")))
    }
}
