package com.guangyue.core.plugin.lx

import com.guangyue.core.plugin.runtime.EmptyPreludeProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * 洛雪运行时的契约测试：不联网，全部用假的宿主响应喂脚本。
 *
 * 这里测的是「洛雪自定义源 API 到底需要宿主给什么」——
 * 1. `lx.send(EVENT_NAMES.inited, {sources})` 能被读到；
 * 2. `lx.on(EVENT_NAMES.request, handler)` 注册的处理器能通过 `musicUrl` 拿回字符串；
 * 3. `lx.request` 的回调三参 `(err, response, opts)` 与响应字段 `statusCode/headers/body` 齐全；
 * 4. 二进制（Range 探测）响应必须是「一字符一字节」，否则脚本的魔数守卫永远失败；
 * 5. 脚本 reject 的错误能带出中文可读原因，而不是一段原始栈。
 */
class LxRuntimeTest {

    private val source = LxSource(id = "lx-test", name = "测试源", version = "1.0")

    /** 最小可用的洛雪源：只回一个固定直链。 */
    private val simpleSource = """
        var EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
        var sources = {
          tx: { name: 'QQ音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k'] },
          wy: { name: '网易云', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k'] }
        };
        lx.on(EVENT_NAMES.request, function (payload) {
          return new Promise(function (resolve, reject) {
            if (!payload || !payload.info || !payload.info.musicInfo) {
              reject(new Error('musicInfo missing'));
              return;
            }
            resolve('https://cdn.example.com/' + payload.source + '/' + payload.info.musicInfo.songmid + '.mp3');
          });
        });
        lx.send(EVENT_NAMES.inited, { status: true, sources: sources });
    """.trimIndent()

    /** 走 HTTP 的源：用 Range 拿 64 字节校验魔数，再返回 JSON 里的直链。 */
    private val httpSource = """
        var EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
        function head(url) {
          return new Promise(function (resolve, reject) {
            lx.request(url, { method: 'GET', headers: { Range: 'bytes=0-63' }, timeout: 5000 }, function (err, res) {
              if (err) { reject(err); return; }
              resolve(res);
            });
          });
        }
        lx.on(EVENT_NAMES.request, function (payload) {
          return head('https://api.example.com/head').then(function (res) {
            var body = res.body || '';
            if (body.charCodeAt(0) !== 0x66 || body.charCodeAt(1) !== 0x4C) {
              throw new Error('guard: expect flac got other');
            }
            return payload.source === 'kg' ? 'https://cdn.example.com/a.flac' : 'https://cdn.example.com/a.mp3';
          });
        });
        lx.send(EVENT_NAMES.inited, { status: true, sources: { kg: { name: '酷狗', qualitys: ['flac'] } } });
    """.trimIndent()

    private fun runtime(code: String, client: OkHttpClient): LxRuntime = LxRuntime(
        source = source,
        code = code,
        http = client,
        preludeProvider = EmptyPreludeProvider(),
    )

    private fun musicInfo(id: String = "003OUlho2HcRHC") =
        JsonObject(mapOf("songmid" to JsonPrimitive(id), "name" to JsonPrimitive("夜曲")))

    @Test
    fun `安装后能读到脚本上报的平台`() {
        val rt = runtime(simpleSource, offlineHttp())
        try {
            rt.install()
            assertEquals(listOf("tx", "wy"), rt.platforms)
        } finally {
            rt.close()
        }
    }

    @Test
    fun `musicUrl 返回脚本 resolve 的直链`() {
        val rt = runtime(simpleSource, offlineHttp())
        try {
            rt.install()
            val url = rt.musicUrl("tx", "320k", musicInfo())
            assertEquals("https://cdn.example.com/tx/003OUlho2HcRHC.mp3", url)
        } finally {
            rt.close()
        }
    }

    @Test
    fun `脚本没注册处理器时给出人话错误`() {
        val rt = runtime(
            """
            lx.send(lx.EVENT_NAMES.inited, { status: true, sources: { tx: { name: 'QQ音乐' } } });
            """.trimIndent(),
            offlineHttp(),
        )
        try {
            rt.install()
            fail("没有注册 handler 应当安装失败")
        } catch (e: LxRuntimeException) {
            assertTrue(e.message!!.contains("request 事件处理器"))
        } finally {
            rt.close()
        }
    }

    @Test
    fun `没有上报音源的脚本装不上`() {
        val rt = runtime("lx.on(lx.EVENT_NAMES.request, function () { return 'x'; });", offlineHttp())
        try {
            rt.install()
            fail("空 sources 应当安装失败")
        } catch (e: LxRuntimeException) {
            assertTrue(e.message!!.contains("没有上报"))
        } finally {
            rt.close()
        }
    }

    @Test
    fun `Range 探测拿到的二进制是一字符一字节`() {
        // flac 魔数前三字节 0x66 0x4C 0x61 在 UTF-8 下只有 0x61 合法，所以必须走 latin-1 通道。
        val fake = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request(), fake, "audio/flac") }
            .build()
        val rt = runtime(httpSource, client)
        try {
            rt.install()
            assertEquals(listOf("kg"), rt.platforms)
            assertEquals("https://cdn.example.com/a.flac", rt.musicUrl("kg", "flac", musicInfo()))
        } finally {
            rt.close()
        }
    }

    @Test
    fun `JSON 响应解析成对象并以 UTF-8 交给脚本`() {
        val body = """{"data":{"name":"夜曲"}}""".toByteArray(Charsets.UTF_8)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request(), body, "application/json; charset=utf-8") }
            .build()
        // 洛雪桌面版把 JSON 响应**解析成对象**再交给脚本（源里直接写 resp.body.data[0]、
        // resp.body.code），所以这里断言的是「拿到的是对象且中文没乱码」。
        val code = """
            lx.on(lx.EVENT_NAMES.request, function () {
              return new Promise(function (resolve, reject) {
                lx.request('https://api.example.com/json', { method: 'GET' }, function (err, res) {
                  if (err) { reject(err); return; }
                  resolve(res.body.data.name === '夜曲' ? 'https://cdn.example.com/ok.mp3' : 'bad');
                });
              });
            });
            lx.send(lx.EVENT_NAMES.inited, { status: true, sources: { wy: { name: '网易云' } } });
        """.trimIndent()
        val rt = runtime(code, client)
        try {
            rt.install()
            assertEquals("https://cdn.example.com/ok.mp3", rt.musicUrl("wy", "320k", musicInfo()))
        } finally {
            rt.close()
        }
    }

    @Test
    fun `网络失败以可读原因冒出来`() {
        val rt = runtime(
            """
            lx.on(lx.EVENT_NAMES.request, function () {
              return new Promise(function (resolve, reject) {
                lx.request('https://api.example.com/down', { method: 'GET' }, function (err, res) {
                  if (err) { reject(err); return; }
                  resolve('unexpected');
                });
              });
            });
            lx.send(lx.EVENT_NAMES.inited, { status: true, sources: { tx: { name: 'QQ音乐' } } });
            """.trimIndent(),
            offlineHttp(),
        )
        try {
            rt.install()
            assertEquals(listOf("tx"), rt.platforms)
            rt.musicUrl("tx", "320k", musicInfo())
            fail("网络被禁用时不该拿到链接")
        } catch (e: LxRuntimeException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("网络在测试里被禁用"))
        } finally {
            rt.close()
        }
    }

    @Test
    fun `lxcrypto 的 md5 与标准一致`() {
        val code = """
            // musicUrl 的返回值必须是直链（宿主会拒非 http（s）值且报
            // 「取链脚本返回的不是直链」），所以把 md5 拼进 URL 里断言。
            lx.on(lx.EVENT_NAMES.request, function () {
              return 'https://example.com/' + lx.utils.crypto.md5('abc') + '.mp3';
            });
            lx.send(lx.EVENT_NAMES.inited, { status: true, sources: { tx: { name: 'QQ音乐' } } });
        """.trimIndent()
        val rt = runtime(code, offlineHttp())
        try {
            rt.install()
            assertEquals(
                "https://example.com/900150983cd24fb0d6963f7d28e17f72.mp3",
                rt.musicUrl("tx", "320k", musicInfo()),
            )
        } finally {
            rt.close()
        }
    }

    private fun offlineHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            throw IOException("网络在测试里被禁用：${chain.request().url}")
        }
        .build()

    private fun respond(request: Request, bytes: ByteArray, contentType: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .header("Content-Type", contentType)
            .body(bytes.toResponseBody())
            .build()
}