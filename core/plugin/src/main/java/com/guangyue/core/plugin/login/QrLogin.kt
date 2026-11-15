package com.guangyue.core.plugin.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 扫码登录（照 Folia：QQ 音乐 / 网易云 / 酷狗各自的二维码登录）。Folia 走本机的 qq-music-api /
 * NeteaseCloudMusicApi / KuGouMusicApi 服务，这里在手机上直接请求同样的官方接口：
 *  - 网易云：eapi /api/login/qrcode/unikey → 码文本 music.163.com/login?codekey= → /api/login/qrcode/client/login
 *    （800 过期 / 801 等待 / 802 已扫 / 803 成功，Set-Cookie 带 MUSIC_U）。weapi 现在会被风控 -462，必须 eapi。
 *  - QQ：ptlogin ptqrshow（PNG，cookie qrsig）→ ptqrlogin 轮询（66 等待 / 67 已扫 / 65 过期 / 0 成功带 check_sig 地址）
 *    → check_sig 拿 p_skey → graph.qq.com authorize 拿 code → musicu QQConnectLogin.QQLogin 换 musickey。
 *  - 酷狗：login-user.kugou.com v2/qrcode（带签名）→ v2/get_userinfo_qrcode（0 过期 / 1 等待 / 2 已扫 / 4 成功带 token）。
 * 拿到的凭据写进该平台的插件账号（见 [LoginVariableMapper]），音源插件照常用。
 */
enum class QrPlatform(val id: String, val label: String, val title: String, val note: String) {
    QQ("qq", "QQ音乐", "扫码登录QQ音乐", "用 QQ 扫码，确认后自动登录"),
    NETEASE("netease", "网易云", "使用网易云APP扫码", "打开网易云音乐APP > 发现 > 扫一扫"),
    KUGOU("kugou", "酷狗", "使用酷狗音乐APP扫码", "打开酷狗音乐APP扫描二维码"),
    ;

    companion object {
        /** 插件的 platform / 名字归到哪个平台；不是这三家返回 null。 */
        fun of(text: String?): QrPlatform? {
            val t = text?.lowercase() ?: return null
            return when {
                "qq" in t || "腾讯" in t -> QQ
                "网易" in t || "netease" in t || "163" in t -> NETEASE
                "酷狗" in t || "kugou" in t -> KUGOU
                else -> null
            }
        }
    }
}

/** 一张码：文本（自己画成二维码）或现成的 PNG。 */
class QrTicket(val platform: QrPlatform, val key: String, val text: String? = null, val png: ByteArray? = null)

data class LoginSession(
    val platform: String,
    val uid: String,
    val nickname: String,
    val avatar: String,
    /** 写进插件账号的原料：cookie 串和各个字段。 */
    val fields: Map<String, String>,
)

sealed interface QrState {
    data object Waiting : QrState
    data object Scanned : QrState
    data object Expired : QrState
    data class Done(val session: LoginSession) : QrState
    data class Failed(val message: String) : QrState
}

class QrLogin {
    private val store = HashMap<String, MutableList<Cookie>>()
    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(store) {
                for (c in cookies) {
                    val list = store.getOrPut(c.domain) { mutableListOf() }
                    list.removeAll { it.name == c.name && it.path == c.path }
                    list.add(c)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
            store.values.flatten().filter { it.matches(url) }
        }
    }
    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun cookieAt(name: String, domain: String): String? = synchronized(store) {
        store[domain]?.lastOrNull { it.name == name && it.value.isNotBlank() }?.value
    }

    private fun cookie(name: String): String? = synchronized(store) {
        store.values.flatten().lastOrNull { it.name == name && it.value.isNotBlank() }?.value
    }

    suspend fun start(p: QrPlatform): QrTicket = withContext(Dispatchers.IO) {
        synchronized(store) { store.clear() }
        when (p) {
            QrPlatform.NETEASE -> neteaseStart()
            QrPlatform.QQ -> qqStart()
            QrPlatform.KUGOU -> kugouStart()
        }
    }

    suspend fun poll(t: QrTicket): QrState = withContext(Dispatchers.IO) {
        runCatching {
            when (t.platform) {
                QrPlatform.NETEASE -> neteasePoll(t)
                QrPlatform.QQ -> qqPoll(t)
                QrPlatform.KUGOU -> kugouPoll(t)
            }
        }.getOrElse { QrState.Failed(it.message ?: "网络出错") }
    }

    // ———————————————————————— 网易云（eapi） ————————————————————————
    private val neDevice = (1..52).map { "0123456789ABCDEF".random() }.joinToString("")
    private val neHeader: Map<String, String> get() = mapOf(
        "os" to "pc", "appver" to "3.1.17.204416", "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
        "deviceId" to neDevice, "channel" to "netease", "requestId" to "${System.currentTimeMillis()}_0001", "__csrf" to (cookie("__csrf") ?: ""),
    )

    private fun eapiParams(path: String, body: String): String {
        val digest = md5("nobody${path}use${body}md5forencrypt")
        val msg = "$path-36cd479b6b5-$body-36cd479b6b5-$digest".toByteArray()
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        return c.doFinal(msg).joinToString("") { "%02X".format(it) }
    }

    private fun neCall(path: String, data: Map<String, JsonElement>): Pair<JsonObject, okhttp3.Headers> {
        val header = neHeader
        val body = JsonObject(data + mapOf("header" to JsonObject(header.mapValues { JsonPrimitive(it.value) }), "e_r" to JsonPrimitive(false))).toString()
        val url = "https://interface.music.163.com/eapi" + path.removePrefix("/api")
        val cookieHeader = (header + listOfNotNull(cookie("MUSIC_U")?.let { "MUSIC_U" to it }).toMap()).entries.joinToString("; ") { "${it.key}=${it.value}" }
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.17.204416 Safari/537.36")
            .header("Referer", "https://music.163.com")
            .header("Cookie", cookieHeader)
            .post(FormBody.Builder().add("params", eapiParams(path, body)).build())
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            return Json.parseToJsonElement(text).jsonObject to r.headers
        }
    }

    private fun neteaseStart(): QrTicket {
        val (j, _) = neCall("/api/login/qrcode/unikey", mapOf("type" to JsonPrimitive(3)))
        val key = j.str("unikey") ?: error(j.str("message") ?: "网易云没有给二维码")
        return QrTicket(QrPlatform.NETEASE, key, text = "https://music.163.com/login?codekey=$key")
    }

    private fun neteasePoll(t: QrTicket): QrState {
        val (j, headers) = neCall("/api/login/qrcode/client/login", mapOf("key" to JsonPrimitive(t.key), "type" to JsonPrimitive(3)))
        return when (j.str("code")) {
            "801" -> QrState.Waiting
            "802" -> QrState.Scanned
            "800" -> QrState.Expired
            "803" -> {
                // OkHttp 的 CookieJar 已经收下 Set-Cookie；保险起见再从头里抠一次 MUSIC_U。
                val musicU = cookie("MUSIC_U") ?: headers.values("Set-Cookie").firstNotNullOfOrNull { Regex("MUSIC_U=([^;]+)").find(it)?.groupValues?.get(1) }
                    ?: return QrState.Failed("网易云没有返回登录凭据")
                synchronized(store) { store.getOrPut("music.163.com") { mutableListOf() }.add(Cookie.Builder().domain("music.163.com").name("MUSIC_U").value(musicU).build()) }
                val csrf = cookie("__csrf").orEmpty()
                val (acc, _) = runCatching { neCall("/api/nuser/account/get", emptyMap()) }.getOrNull() ?: (JsonObject(emptyMap()) to headers)
                val profile = (acc["profile"] as? JsonObject)
                val uid = profile?.str("userId") ?: (acc["account"] as? JsonObject)?.str("id") ?: ""
                QrState.Done(
                    LoginSession(
                        platform = "netease", uid = uid,
                        nickname = profile?.str("nickname") ?: "网易云用户",
                        avatar = profile?.str("avatarUrl").orEmpty(),
                        fields = mapOf("MUSIC_U" to musicU, "__csrf" to csrf, "cookie" to buildString {
                            append("MUSIC_U=").append(musicU)
                            if (csrf.isNotBlank()) append("; __csrf=").append(csrf)
                        }),
                    ),
                )
            }
            else -> QrState.Failed(j.str("message") ?: "网易云登录失败（${j.str("code")}）")
        }
    }

    // ———————————————————————— QQ（ptlogin） ————————————————————————
    private val qqAppId = "716027609"
    private val qqDaid = "383"
    private val qq3rd = "100497308"

    private fun hash33(s: String, seed: Long = 0): Long {
        var h = seed
        for (c in s) h += (h shl 5) + c.code
        return h and 0x7fffffff
    }

    private fun qqStart(): QrTicket {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow?appid=$qqAppId&e=2&l=M&s=3&d=72&v=4&t=${Math.random()}&daid=$qqDaid&pt_3rd_aid=$qq3rd"
        val png = http.newCall(Request.Builder().url(url).header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().use { it.body?.bytes() }
            ?: error("QQ 没有给二维码")
        val sig = cookie("qrsig") ?: error("QQ 没有给 qrsig")
        return QrTicket(QrPlatform.QQ, sig, png = png)
    }

    private fun qqPoll(t: QrTicket): QrState {
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump&ptqrtoken=${hash33(t.key)}" +
            "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-0-${System.currentTimeMillis()}&js_ver=20102616&js_type=1" +
            "&pt_uistyle=40&aid=$qqAppId&daid=$qqDaid&pt_3rd_aid=$qq3rd&has_onekey=1"
        val text = http.newCall(Request.Builder().url(url).header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().use { it.body?.string().orEmpty() }
        val parts = Regex("'([^']*)'").findAll(text).map { it.groupValues[1] }.toList()
        return when (parts.firstOrNull()) {
            "66" -> QrState.Waiting
            "67" -> QrState.Scanned
            "65" -> QrState.Expired
            "0" -> qqFinish(parts.getOrNull(2).orEmpty())
            else -> QrState.Failed(parts.getOrNull(4)?.ifBlank { null } ?: "QQ 登录失败")
        }
    }

    private fun qqFinish(checkSig: String): QrState {
        if (checkSig.isBlank()) return QrState.Failed("QQ 没有返回登录地址")
        // check_sig 只请求一次（不跟后续跳转，跟了会落到 login_jump 页把会话冲掉），收下 graph.qq.com 的 p_skey / uin。
        http.newCall(Request.Builder().url(checkSig).header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().close()
        val pSkey = cookieAt("p_skey", "graph.qq.com") ?: cookie("p_skey") ?: return QrState.Failed("QQ 没有返回 p_skey")
        val gtk = hash33(pSkey, 5381)
        fun authorize(): Pair<Int, String> {
            val form = FormBody.Builder()
                .add("response_type", "code").add("client_id", qq3rd)
                .add("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/")
                .add("scope", "get_user_info,get_app_friends").add("state", "state").add("switch", "")
                .add("from_ptlogin", "1").add("src", "1").add("update_auth", "1").add("openapi", "1010_1030")
                .add("g_tk", gtk.toString()).add("auth_time", System.currentTimeMillis().toString())
                .add("ui", UUID.randomUUID().toString())
                .build()
            return http.newCall(
                Request.Builder().url("https://graph.qq.com/oauth2.0/authorize")
                    .header("Referer", "https://graph.qq.com/oauth2.0/show?which=Login&display=pc&response_type=code&client_id=$qq3rd")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                    .post(form).build(),
            ).execute().use { r -> r.code to (r.header("Location") ?: r.body?.string().orEmpty()) }
        }
        var (status, loc) = authorize()
        var code = Regex("[?&]code=([^&\"'\\s]+)").find(loc)?.groupValues?.get(1)
        if (code == null) { val again = authorize(); status = again.first; loc = again.second; code = Regex("[?&]code=([^&\"'\\s]+)").find(loc)?.groupValues?.get(1) }
        if (code == null) return QrState.Failed("QQ 授权没有返回 code（HTTP $status${if (loc.isBlank()) "" else "，" + loc.take(60)}）")
        val body = """{"comm":{"g_tk":$gtk,"platform":"yqq","ct":24,"cv":0},"req":{"module":"QQConnectLogin.LoginServer","method":"QQLogin","param":{"code":"$code"}}}"""
        val text = http.newCall(
            Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg").header("Referer", "https://y.qq.com/")
                .post(body.toRequestBody("application/json".toMediaType())).build(),
        ).execute().use { it.body?.string().orEmpty() }
        val data = runCatching { Json.parseToJsonElement(text).jsonObject["req"]!!.jsonObject["data"] as? JsonObject }.getOrNull()
        val musicKey = data?.str("musickey") ?: cookie("qqmusic_key") ?: cookie("qm_keyst") ?: return QrState.Failed("QQ 音乐没有返回 musickey")
        val uin = data?.str("musicid") ?: data?.str("str_musicid") ?: cookie("uin")?.trimStart('o', '0') ?: ""
        val cookieStr = "uin=$uin; qm_keyst=$musicKey; qqmusic_key=$musicKey"
        // 昵称头像：拿不到不影响登录。
        var nick = "QQ音乐用户"
        var avatar = ""
        runCatching {
            val prof = http.newCall(
                Request.Builder().url("https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?format=json&cid=205360838&userid=$uin&reqfrom=1")
                    .header("Referer", "https://y.qq.com/").header("Cookie", cookieStr).build(),
            ).execute().use { it.body?.string().orEmpty() }
            val creator = Json.parseToJsonElement(prof).jsonObject["data"]?.jsonObject?.get("creator")?.jsonObject
            creator?.str("nick")?.let { nick = it }
            creator?.str("headpic")?.let { avatar = it }
        }
        if (avatar.isBlank() && uin.isNotBlank()) avatar = "https://q.qlogo.cn/headimg_dl?dst_uin=$uin&spec=140"
        return QrState.Done(LoginSession("qq", uin, nick, avatar, mapOf("uin" to uin, "musickey" to musicKey, "cookie" to cookieStr)))
    }

    // ———————————————————————— 酷狗 ————————————————————————
    private val kgSalt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    private val kgMid = md5(UUID.randomUUID().toString())

    private fun kgUrl(path: String, extra: Map<String, String>): String {
        val p = linkedMapOf(
            "dfid" to "-", "mid" to kgMid, "uuid" to "-", "appid" to "1005", "clientver" to "20000",
            "clienttime" to System.currentTimeMillis().toString(), "srcappid" to "2919",
        )
        p.putAll(extra)
        val sign = md5(kgSalt + p.entries.map { "${it.key}=${it.value}" }.sorted().joinToString("") + kgSalt)
        val b = "https://login-user.kugou.com$path".toHttpUrl().newBuilder()
        p.forEach { (k, v) -> b.addQueryParameter(k, v) }
        b.addQueryParameter("signature", sign)
        return b.build().toString()
    }

    private fun kgGet(url: String): JsonObject =
        http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute()
            .use { Json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject }

    private fun kugouStart(): QrTicket {
        val j = kgGet(kgUrl("/v2/qrcode", mapOf("type" to "1", "plat" to "4", "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1005&")))
        val d = j["data"] as? JsonObject ?: error("酷狗没有给二维码")
        val key = d.str("qrcode") ?: error("酷狗没有给二维码")
        val img = d.str("qrcode_img")?.substringAfter("base64,")?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
        return QrTicket(QrPlatform.KUGOU, key, text = if (img == null) "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1005&qrcode=$key" else null, png = img)
    }

    private fun kugouPoll(t: QrTicket): QrState {
        val j = kgGet(kgUrl("/v2/get_userinfo_qrcode", mapOf("plat" to "4", "qrcode" to t.key)))
        val d = j["data"] as? JsonObject ?: return QrState.Failed("酷狗登录失败")
        return when (d.str("status")) {
            "1" -> QrState.Waiting
            "2" -> QrState.Scanned
            "0" -> QrState.Expired
            "4" -> {
                val token = d.str("token").orEmpty()
                val userid = d.str("userid").orEmpty()
                QrState.Done(
                    LoginSession(
                        "kugou", userid, d.str("nickname") ?: "酷狗用户", d.str("pic").orEmpty(),
                        mapOf("token" to token, "userid" to userid, "cookie" to "token=$token; userid=$userid"),
                    ),
                )
            }
            else -> QrState.Waiting
        }
    }

    companion object {
        internal fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

/** 把扫码拿到的凭据按插件声明的变量名填进去（各家插件变量名不一）。 */
object LoginVariableMapper {
    fun map(platform: QrPlatform, vars: List<com.guangyue.core.plugin.runtime.PluginUserVariable>, f: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (v in vars) {
            val k = (v.key + " " + v.name).lowercase()
            // 「设为 off 关闭」一类的开关不碰。
            if ("off" in (v.hint ?: "").lowercase() && "cookie" !in k) continue
            val value = when (platform) {
                QrPlatform.QQ -> when {
                    "keyst" in k || "musickey" in k || "qqmusic_key" in k -> f["musickey"]
                    "cookie" in k -> f["cookie"]
                    "uin" in k -> f["uin"]
                    else -> null
                }
                QrPlatform.NETEASE -> when {
                    "music_u" in k -> f["MUSIC_U"]
                    "csrf" in k -> f["__csrf"]
                    "cookie" in k -> f["cookie"]
                    else -> null
                }
                QrPlatform.KUGOU -> when {
                    "token" in k -> f["token"]
                    "userid" in k || "user_id" in k || "uid" in k -> f["userid"]
                    "cookie" in k -> f["cookie"]
                    else -> null
                }
            }
            if (!value.isNullOrBlank()) out[v.key] = value
        }
        return out
    }
}
