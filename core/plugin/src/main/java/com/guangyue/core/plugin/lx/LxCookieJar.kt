package com.guangyue.core.plugin.lx

/**
 * 洛雪脚本用的极简 cookie 罐（按域名存）。
 *
 * 洛雪源取链普遍是「先打一个接口拿 token，再拿 token 取直链」两跳，
 * 而服务端认的是 cookie（酷我的 `Invalid cookie`、网易的 `MUSIC_U`…）。
 * 原生环境没有浏览器帮你带 cookie，所以这里自己维护：
 *
 * - 每个响应把 `Set-Cookie` 收进来，按 host 归类；同名覆盖（后到的赢）。
 * - 每个请求出去前，把该 host（含父域）已有的 cookie 拼成 `Cookie` 头。
 *   脚本自己显式写了 `Cookie` 头就用它的，不叠加 —— 脚本更清楚自己要什么。
 *
 * 只在单次 [LxRuntime] 生命周期内有效，跟宿主浏览器的 cookie 池互不影响。
 */
internal class LxCookieJar {

    /** host → (cookie 名 → 值)。 */
    private val store = HashMap<String, LinkedHashMap<String, String>>()

    /** 收一条 `Set-Cookie` 响应头。 */
    fun absorb(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val host = hostOf(url) ?: return
        val jar = store.getOrPut(host) { LinkedHashMap() }
        for (raw in setCookieHeaders) {
            val pair = raw.substringBefore(';').trim()
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=', "").trim()
            if (name.isEmpty()) continue
            val value = pair.substringAfter('=', "").trim()
            // 服务端删 cookie 的写法：值为空且带过期时间。这里简化成「删掉」。
            val expired = raw.contains("Max-Age=0", true) ||
                raw.contains("expires=Thu, 01 Jan 1970", true)
            if (expired) jar.remove(name) else jar[name] = value
        }
        if (jar.isEmpty()) store.remove(host)
    }

    /** 该 url 应该戴上的 `Cookie` 头；没有就返回 null。 */
    fun headerFor(url: String): String? {
        val host = hostOf(url) ?: return null
        val names = LinkedHashSet<String>()
        // 自己 + 所有父域（例如 kwcdn.kuwo.cn 也吃 .kuwo.cn 的 cookie）。
        generateSequence(host) { h ->
            val dot = h.indexOf('.')
            if (dot <= 0 || dot == h.length - 1) null else h.substring(dot + 1)
        }.forEach { h ->
            store[h]?.keys?.let(names::addAll)
        }
        if (names.isEmpty()) return null
        val parts = ArrayList<String>(names.size)
        for (n in names) {
            val v = store[host]?.get(n)
                ?: generateSequence(host) { h ->
                    val dot = h.indexOf('.')
                    if (dot <= 0 || dot == h.length - 1) null else h.substring(dot + 1)
                }.mapNotNull { store[it]?.get(n) }.firstOrNull()
            if (v != null) parts.add("$n=$v")
        }
        return if (parts.isEmpty()) null else parts.joinToString("; ")
    }

    /** 只给测试和诊断用。 */
    fun snapshot(): Map<String, Map<String, String>> =
        store.mapValues { it.value.toMap() }

    private fun hostOf(url: String): String? {
        val scheme = url.indexOf("://")
        if (scheme <= 0) return null
        var rest = url.substring(scheme + 3)
        rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        rest = rest.substringAfter('@', missingDelimiterValue = rest)
        val host = rest.substringBefore(':')
        return host.takeIf { it.isNotEmpty() }?.lowercase()
    }
}
