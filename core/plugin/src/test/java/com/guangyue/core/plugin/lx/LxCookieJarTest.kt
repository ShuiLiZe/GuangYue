package com.guangyue.core.plugin.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 洛雪取链两跳靠 cookie：`LxCookieJar` 必须按域名收发。
 *
 * 真实故障：酷我 `getToken` 拿到的 cookie 没带到第二跳，服务端回「Invalid cookie」，
 * 取链直接失败。
 */
class LxCookieJarTest {

    @Test
    fun `keeps cookie for the same host across requests`() {
        val jar = LxCookieJar()
        jar.absorb("https://www.kuwo.cn/api/login", listOf("kw_token=ABC123; Path=/; HttpOnly"))
        assertEquals("kw_token=ABC123", jar.headerFor("https://www.kuwo.cn/api/song"))
    }

    @Test
    fun `sends cookie to subdomains of the same registrable host`() {
        val jar = LxCookieJar()
        jar.absorb("https://kuwo.cn/auth", listOf("token=xyz; Domain=kuwo.cn"))
        assertEquals("token=xyz", jar.headerFor("https://m.kuwo.cn/api/song"))
        assertEquals("token=xyz", jar.headerFor("https://kuwo.cn/api/song"))
    }

    @Test
    fun `does not leak cookie to unrelated hosts`() {
        val jar = LxCookieJar()
        jar.absorb("https://www.kuwo.cn/api/login", listOf("kw_token=ABC"))
        assertNull(jar.headerFor("https://music.163.com/api/song"))
        assertNull(jar.headerFor("https://kwcdn.kuwo.cn.cn.evil.example/x"))
    }

    @Test
    fun `later set-cookie for the same name wins`() {
        val jar = LxCookieJar()
        jar.absorb("https://www.kuwo.cn/a", listOf("kw_token=OLD"))
        jar.absorb("https://www.kuwo.cn/b", listOf("kw_token=NEW"))
        assertEquals("kw_token=NEW", jar.headerFor("https://www.kuwo.cn/c"))
    }

    @Test
    fun `collects multiple cookies and joins them`() {
        val jar = LxCookieJar()
        jar.absorb(
            "https://music.163.com/api/x",
            listOf("MUSIC_U=aaa; Path=/", "NMTID=bbb; Path=/"),
        )
        val header = jar.headerFor("https://music.163.com/api/y")!!
        assertTrue(header.contains("MUSIC_U=aaa"))
        assertTrue(header.contains("NMTID=bbb"))
        assertTrue(header.contains("; "))
    }

    @Test
    fun `expired cookie is dropped`() {
        val jar = LxCookieJar()
        jar.absorb("https://www.kuwo.cn/a", listOf("kw_token=ABC"))
        jar.absorb("https://www.kuwo.cn/a", listOf("kw_token=; Max-Age=0; Path=/"))
        assertNull(jar.headerFor("https://www.kuwo.cn/b"))
    }

    @Test
    fun `attributes after the value are not part of the value`() {
        val jar = LxCookieJar()
        jar.absorb(
            "https://www.kuwo.cn/a",
            listOf("kw_token=ABC123; Path=/; HttpOnly; SameSite=Lax"),
        )
        assertEquals("kw_token=ABC123", jar.headerFor("https://www.kuwo.cn/b"))
    }

    @Test
    fun `host matching is case insensitive and ignores port`() {
        val jar = LxCookieJar()
        jar.absorb("https://WWW.Kuwo.CN:443/a", listOf("t=1"))
        assertEquals("t=1", jar.headerFor("https://www.kuwo.cn/b"))
    }
}
