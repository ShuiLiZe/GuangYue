package com.guangyue.core.plugin

/**
 * 各音源站点自己的图标地址（0.0.9 b 从 feature:library 挪到 core:plugin，纯逻辑、无 Android 依赖）。
 *
 * 只放**真机实测能下载、content-type 是图片、字节确实是图片魔数**的地址（0.0.9 b 重新逐个
 * `curl -sSL` 校验过，见 `docs/ui-0.0.9/b/icon-probe.txt`）。取不到的源故意不登记，让它回退首字
 * 占位，而不是挂一个必然失败的死链。
 *
 * 为什么不按插件 `srcUrl` 的 host 取 favicon：官方订阅源里 57 个插件的 `srcUrl` 全在
 * raw.githubusercontent.com / hebijunge.github.io，按 host 取只会拿到 GitHub 的图标。
 */
object PluginPlatformIcons {

    /** platform（小写）→ 站点图标地址。 */
    val byPlatform: Map<String, String> = buildMap {
        // —— 主流源（Hark 0.0.9 b 验收要求的 6 个）——
        // 酷狗主站对无 Referer / 非浏览器 UA 的 favicon 直连回 403（真机实测），
        // 但同站群的 t1.kugou.com 稳定 200 image/vnd.microsoft.icon，所以用后者。
        put("kugou", "https://t1.kugou.com/favicon.ico")
        put("kuwo", "https://www.kuwo.cn/static/image/logo.png")
        put("qq", "https://y.qq.com/favicon.ico")
        put("netease", "https://music.163.com/favicon.ico")
        // 汽水音乐没有独立站点，用同厂抖音的图标（抖音 CDN，字节自有域名，可达性好）。
        put("qishui", "https://lf1-cdn-tos.bytegoofy.com/goofy/ies/douyin_web/public/favicon.ico")
        put("migu", "https://www.migu.cn/favicon.ico")
        // 番茄畅听：「番茄音乐」就是番茄小说 App 里的音乐频道，所以用番茄小说主站的图标。
        // 注意 fanqienovel.com/favicon.ico 是 200 但 0 字节、fanqienovel.com/favicon.svg 是 301，
        // 真正可用的图是首页 <link rel="shortcut icon"> 指向的字节 CDN 资源（image/png 6940B），
        // 见 icon-probe.txt。
        put(
            "fanqie",
            "https://p1-tt.byteimg.com/origin/novel-static/a3621391ca2e537045168afda6722ee9",
        )
        // —— 其余可取的源 ——
        put("bilibili", "https://www.bilibili.com/favicon.ico")
        put("neteaseradio", "https://music.163.com/favicon.ico")
        put("qianqian", "https://music.91q.com/favicon.ico")
        put("5sing", "https://5sing.kugou.com/favicon.ico")
        put("ximalaya", "https://www.ximalaya.com/favicon.ico")
        put("qingting", "https://www.qingting.fm/favicon.ico")
        put("lizhi", "https://www.lizhi.fm/favicon.ico")
        put("joox", "https://www.joox.com/favicon.ico")
        put("jamendo", "https://www.jamendo.com/favicon.ico")
        put("apple-music", "https://music.apple.com/favicon.ico")
        put("华为音乐", "https://www.huawei.com/favicon.ico")
        put("1ting", "https://www.1ting.com/favicon.ico")
        put("猫耳FM", "https://www.missevan.com/favicon.ico")
        put("djduoduo", "https://www.djduoduo.com/favicon.ico")
        put("DJ耶耶耶", "https://www.djyyy.com/favicon.ico")
        put("DJ766", "https://www.dj766.com/favicon.ico")
        put("82DJ", "https://www.82dj.com/favicon.ico")
        put("36DJ", "https://www.36dj.com/favicon.ico")
        put("172Mix", "https://www.172mix.com/favicon.ico")
        put("djuu", "https://www.djuu.com/favicon.ico")
        put("agg", "https://www.dj97.com/favicon.ico") // 聚合插件无自家站点，退回其上游 DJ97
        put("qt", "https://qtstatic.oss-cn-hangzhou.aliyuncs.com/favicon.ico")
    }

    /**
     * 候选地址，按优先级：
     * 1. 插件元数据的 `iconUrl`；
     * 2. 按 `platform` 查表的站点图标；
     * 3. 插件 `srcUrl` 的站点 favicon（代码托管站除外）。
     * 都没有则返回空表 —— 由调用方回退首字占位。
     */
    fun candidatesFor(plugin: PluginMeta): List<String> {
        val urls = mutableListOf<String>()
        plugin.iconUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { urls += it }
        byPlatform[plugin.platform.trim()]?.let { urls += it }
        pluginFaviconUrl(plugin.srcUrl)
            ?.takeIf { url -> url !in urls && !isCodeHost(url) }
            ?.let { urls += it }
        return urls
    }

    /** 单个 platform 的图标（没有登记就是 null）。 */
    fun urlFor(platform: String): String? = byPlatform[platform.trim()]

    fun isCodeHost(url: String): Boolean = CODE_HOSTS.any { url.contains(it, ignoreCase = true) }

    /** 由插件源地址推出站点 favicon；非 http(s) 或取不到 host 时返回 null。 */
    private fun pluginFaviconUrl(srcUrl: String?): String? {
        val raw = srcUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
        return "$scheme://$host/favicon.ico"
    }

    private val CODE_HOSTS =
        listOf("githubusercontent.com", "github.io", "github.com", "gitlab.", "gitee.com")
}
