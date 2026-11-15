package com.guangyue.core.plugin.login

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 加密与请求拼装（照 NeteaseCloudMusicApi util/crypto.js 的 eapi + request.js 的 eapi 分支）。
 * 扫码登录（[QrLogin]）与账号歌单读取（core/data NeteaseAccountApi）共用；全是纯函数，同样的输入必然得到同样的密文，
 * 好在 CI 上单测。weapi 现在会被风控 -462，不用。
 *
 *  - params = AES-128-ECB(PKCS5, key "e82ckenh8dichen8") 加密
 *    `path-36cd479b6b5-body-36cd479b6b5-md5("nobody" + path + "use" + body + "md5forencrypt")`，转大写十六进制；
 *  - 地址 = https://interface.music.163.com/eapi + path 去掉开头的 /api；
 *  - body = 接口参数 + `header`（客户端信息，同时也作为 Cookie 发）+ `e_r: false`（回答不加密，直接是 JSON）。
 */
object NeteaseCrypto {
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_SEP = "-36cd479b6b5-"

    const val HOST = "https://interface.music.163.com"
    const val APP_VERSION = "3.1.17.204416"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VERSION Safari/537.36"
    const val REFERER = "https://music.163.com"

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun cipher(mode: Int) = Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
        init(mode, SecretKeySpec(EAPI_KEY.toByteArray(), "AES"))
    }

    /** eapi 的 `params` 表单值（[path] 以 /api 开头，[body] 是要发的 JSON 文本）。 */
    fun eapiParams(path: String, body: String): String {
        val digest = md5Hex("nobody${path}use${body}md5forencrypt")
        val msg = "$path$EAPI_SEP$body$EAPI_SEP$digest".toByteArray()
        return cipher(Cipher.ENCRYPT_MODE).doFinal(msg).joinToString("") { "%02X".format(it) }
    }

    /** [eapiParams] 的逆运算（单测与排查用）：还原成 `path-…-body-…-digest` 明文。 */
    fun eapiDecrypt(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return String(cipher(Cipher.DECRYPT_MODE).doFinal(bytes))
    }

    /** /api/xxx → https://interface.music.163.com/eapi/xxx */
    fun eapiUrl(path: String): String = "$HOST/eapi" + path.removePrefix("/api")

    /** 桌面客户端的 header（[QrLogin] 一直用的这一套）。[requestId] 由调用方给，好让请求体可重复。 */
    fun desktopHeader(deviceId: String, csrf: String, requestId: String): Map<String, String> = linkedMapOf(
        "os" to "pc",
        "appver" to APP_VERSION,
        "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit",
        "deviceId" to deviceId,
        "channel" to "netease",
        "requestId" to requestId,
        "__csrf" to csrf,
    )

    /** 要加密的 JSON 文本：接口参数 + header + e_r=false（键序固定，同样的输入得到同样的文本）。 */
    fun eapiBody(data: Map<String, JsonElement>, header: Map<String, String>): String =
        JsonObject(
            data + mapOf(
                "header" to JsonObject(header.mapValues { JsonPrimitive(it.value) }),
                "e_r" to JsonPrimitive(false),
            ),
        ).toString()

    /** Cookie 头：header 各项 + MUSIC_U（有的话）。 */
    fun cookieHeader(header: Map<String, String>, musicU: String?): String =
        (header + listOfNotNull(musicU?.takeIf { it.isNotBlank() }?.let { "MUSIC_U" to it }).toMap())
            .entries.joinToString("; ") { "${it.key}=${it.value}" }

    /** 随机 52 位十六进制设备号（同 [QrLogin]）。 */
    fun randomDeviceId(): String = (1..52).map { "0123456789ABCDEF".random() }.joinToString("")
}
