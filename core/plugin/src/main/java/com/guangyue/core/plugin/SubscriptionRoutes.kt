package com.guangyue.core.plugin

/**
 * P1：官方插件订阅的**三条线路**与**分类清单**（纯数据层，不含 UI）。
 *
 * 上游 `hebijunge/musicfree-plugins` 从 2026-10-31 起把订阅拆成按分类的小清单，并把主线路
 * 改走 `gh.padao.fun` 加速（实测 < 2s），GitHub Pages 降为备用（实测 6.7s，容易超时）：
 *
 * | 线路 | 前缀 | 说明 |
 * |---|---|---|
 * | padao | `https://gh.padao.fun/https://raw.githubusercontent.com/…` | 主线路 |
 * | githubio | `https://hebijunge.github.io/musicfree-plugins/` | 备用 1 |
 * | fastly | `https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/` | 备用 2 |
 *
 * 分类清单在仓库里是 `sub/<线路>/<分类>.json`（分类文件名 `main` / `dj` / `radio` / `music` / `all`），
 * 同一条线路里的插件 .js 地址也带上同一条线路的前缀，所以「换线路」= 换前缀 + 换 `sub/<线路>/` 段。
 * [candidates] 就是这套换算：给定任意一个官方地址，按「当前线路优先、再主→备」给出候选列表，
 * 主线路超时/失败时 [PluginManager.download] 会自动换下一条。
 */
object SubscriptionRoutes {

    /** 上游仓库（raw）根：padao 线路把它整段拼在自己的前缀后面。 */
    const val UPSTREAM_RAW = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/"

    const val PADAO_PREFIX = "https://gh.padao.fun/"
    const val GITHUBIO_PREFIX = "https://hebijunge.github.io/musicfree-plugins/"
    const val FASTLY_PREFIX = "https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/"

    /** 一条线路。顺序即「主 → 备」的优先级。 */
    enum class Line(val id: String, val label: String, val prefix: String, val subSegment: String) {
        PADAO("padao", "加速线路（padao）", PADAO_PREFIX, "padao"),
        GITHUBIO("githubio", "GitHub Pages（备用）", GITHUBIO_PREFIX, "githubio"),
        FASTLY("fastly", "jsDelivr（备用）", FASTLY_PREFIX, "fastly"),
    }

    /** 按优先级排好的全部线路。 */
    val lines: List<Line> = Line.entries.toList()

    fun lineOf(id: String): Line? = Line.entries.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- 分类

    /** 一个订阅分类（清单文件 = `sub/<线路>/<file>`）。 */
    data class Category(
        val id: String,
        val file: String,
        val name: String,
        val desc: String,
        /** 清单里的插件条数（上游三线路一致）。 */
        val count: Int,
    ) {
        /** 是否默认订阅（常用六平台）。 */
        val isDefault: Boolean get() = id == DEFAULT_ID

        companion object {
            /** 默认订阅：常用六平台。 */
            const val DEFAULT_ID = "main"

            val MAIN = Category(
                id = "main", file = "main.json", name = "常用六平台",
                desc = "QQ音乐 / 酷我 / 酷狗 / 咪咕 / 网易云 / 汽水（推荐只装这一份）", count = 6,
            )
            val DJ = Category(
                id = "dj", file = "dj.json", name = "DJ / 舞曲 / 电音",
                desc = "DJ 站与电音站合集", count = 30,
            )
            val RADIO = Category(
                id = "radio", file = "radio.json", name = "听书 / 电台 / 播客",
                desc = "有声书、FM、播客", count = 10,
            )
            val MUSIC = Category(
                id = "music", file = "music.json", name = "其它音乐平台",
                desc = "B站、5sing、千千、Apple Music…", count = 11,
            )
            val ALL = Category(
                id = "all", file = "all.json", name = "全部插件",
                desc = "全部 57 个插件", count = 57,
            )

            /** 可选订阅：默认那份排第一。 */
            val all: List<Category> = listOf(MAIN, DJ, RADIO, MUSIC, ALL)

            val ids: List<String> get() = all.map { it.id }

            /** 未知 id 一律回落到默认分类（设置项里存了脏值时不让订阅整个失效）。 */
            fun of(id: String?): Category =
                all.firstOrNull { it.id == id?.trim()?.lowercase() } ?: MAIN

            /** 默认订阅分类。 */
            fun default(): Category = MAIN
        }
    }

    /** 默认订阅分类（常用六平台）。 */
    val defaultCategory: Category get() = Category.default()

    /** 全部可选分类：数据层给设置项与后续 UI 用。 */
    val availableCategories: List<Category> get() = Category.all

    // ---------------------------------------------------------------- 地址换算

    /** 某个分类在 [line] 线路上的清单地址。 */
    fun urlFor(category: Category, line: Line): String = build(relPath(category, line), line)

    fun urlFor(categoryId: String, line: Line): String = urlFor(Category.of(categoryId), line)

    /** 分类清单的三条线路地址，主线路在前。 */
    fun subscriptionUrls(category: Category = Category.default()): List<String> =
        lines.map { urlFor(category, it) }

    fun subscriptionUrlsFor(categoryId: String): List<String> =
        subscriptionUrls(Category.of(categoryId))

    /** 插件 .js 在仓库内的相对路径（三线路共用，只有前缀与 `sub/<线路>/` 段不同）。 */
    private fun pluginRelPath(fileName: String): String =
        "plugins/" + fileName.trim().removePrefix("/")

    private fun relPath(category: Category, line: Line): String = "sub/${line.subSegment}/${category.file}"

    /** 线路前缀 + 仓库内相对路径。 */
    fun build(path: String, line: Line): String = when (line) {
        Line.PADAO -> PADAO_PREFIX + UPSTREAM_RAW + path
        Line.GITHUBIO -> GITHUBIO_PREFIX + path
        Line.FASTLY -> FASTLY_PREFIX + path
    }

    /**
     * 从任意官方地址里取出「仓库内相对路径」（如 `plugins/qq-source.plugin.v1.9.26.js`、
     * `sub/padao/main.json`）；不属于官方仓库时返回 null（用户自己贴的第三方订阅不动它）。
     */
    fun relPathOf(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return null
        if (u.startsWith(PADAO_PREFIX)) {
            val inner = u.removePrefix(PADAO_PREFIX)
            // 包了两层时要继续剥，避免 `gh.padao.fun/https://raw.githubusercontent.com/…` 判成「非官方」
            return if (inner.startsWith(UPSTREAM_RAW)) relPathOf(inner) else null
        }
        val path = when {
            u.startsWith(UPSTREAM_RAW) -> u.removePrefix(UPSTREAM_RAW)
            u.startsWith(GITHUBIO_PREFIX) -> u.removePrefix(GITHUBIO_PREFIX)
            u.startsWith(FASTLY_PREFIX) -> u.removePrefix(FASTLY_PREFIX)
            u.contains("githubusercontent.com/hebijunge/musicfree-plugins/") ->
                u.substringAfter("musicfree-plugins/").substringAfter('/', missingDelimiterValue = "")
            else -> return null
        }
        return path.substringBefore('?').substringBefore('#').trim().trimStart('/').ifEmpty { null }
    }

    /** 把仓库内相对路径换算到 [line] 线路（`sub/<线路>/` 段按线路替换）。 */
    fun onLine(path: String, line: Line): String {
        val p = path.trim().trimStart('/')
        val fixed = if (p.startsWith("sub/")) "sub/${line.subSegment}/${p.substringAfter("sub/").substringAfter('/')}"
        else p
        return build(fixed, line)
    }

    /** 该地址属于哪条线路（`raw.githubusercontent.com` 直链视为 padao 线路的源）。 */
    fun lineOfUrl(url: String): Line? {
        val u = url.trim()
        return when {
            u.startsWith(PADAO_PREFIX) -> Line.PADAO
            u.startsWith(GITHUBIO_PREFIX) -> Line.GITHUBIO
            u.startsWith(FASTLY_PREFIX) -> Line.FASTLY
            u.startsWith(UPSTREAM_RAW) -> Line.PADAO
            else -> null
        }
    }

    /** 插件 .js 的三条线路地址（主线路在前）；未知来源返回空。 */
    fun pluginUrls(fileNameOrUrl: String): List<String> {
        val path = (if (fileNameOrUrl.startsWith("http")) relPathOf(fileNameOrUrl) else pluginRelPath(fileNameOrUrl))
            ?: return emptyList()
        return lines.map { onLine(path, it) }
    }

    /**
     * 下载候选列表：**当前线路优先，再按「主 → 备」顺序逐个兜底**。
     *
     * - 已知官方地址（三条线路任一 / raw 直链）→ 该地址换算出的三条线路地址，命中自身那条排最前；
     * - 其它地址（用户自贴订阅、本地路径）→ 原样返回，不做换线。
     */
    fun candidates(url: String): List<String> {
        val u = url.trim()
        if (u.isEmpty()) return emptyList()
        val path = relPathOf(u) ?: return listOf(u)
        val variants = lines.map { onLine(path, it) }
        val hit = variants.indexOfFirst { it == u }
        // 命中某条线路 → 该线路优先；原始 raw 直链（三条线路之外）→ 线路优先，raw 最后再兜底
        if (hit < 0) return (variants + u).distinct()
        // 自身线路优先，其后按 主 → 备 的顺序
        return (listOf(variants[hit]) + variants.filterIndexed { i, _ -> i != hit }).distinct()
    }

    // ---------------------------------------------------------------- 旧地址迁移

    /** 0.0.8 及以前的内置订阅（Pages 全量 57 个）：用户已存的旧地址要迁到新的默认订阅。 */
    const val LEGACY_PAGES_URL = "https://hebijunge.github.io/musicfree-plugins/plugins.json"

    /** 同样老的 raw 直链写法。 */
    const val LEGACY_RAW_URL = "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/plugins.json"

    /** 全部旧写法。 */
    val legacyUrls: List<String> = listOf(LEGACY_PAGES_URL, LEGACY_RAW_URL)

    /** 这个地址是不是旧的内置订阅（迁移判定）。 */
    fun isLegacy(url: String): Boolean = url.trim() in legacyUrls

    /** 新的默认订阅地址（主线路）。 */
    fun defaultSubscriptionUrl(): String = subscriptionUrls(Category.default()).first()

    /** 默认订阅地址（常用六平台 · 主线路），列表场景直接引用这个名字。 */
    val DEFAULT_URL: String get() = defaultSubscriptionUrl()
}