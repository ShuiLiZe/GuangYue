package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.Gyp

/** 插件列表行左侧圆形图标的直径（0.0.8 沿用 28dp）。 */
internal val PluginIconSize = 28.dp

/**
 * 各音源站点自己的图标地址（0.0.9 a 第 3 项）。
 *
 * 只放实测可下载且返回真图片的地址（用 curl -sL 校验 content-type 为 image 类型，
 * 且字节确实是 PNG/ICO/GZIP 图标）。取不到的源（番茄畅听/街声/Y2002/懒人听书/博看/
 * CCTV/火龙/太空DJ…）故意不登记，让它们回退首字占位，而不是挂一个必然失败的死链。
 *
 * 为什么不按插件 `srcUrl` 的 host 取 favicon：官方订阅源里 57 个插件的 `srcUrl`
 * 全在 raw.githubusercontent.com / hebijunge.github.io，按 host 取只会拿到 GitHub 的图标。
 */
private val platformIconUrls: Map<String, String> = buildMap {
    // —— 本段验收的 5 个源 + 番茄畅听 ——
    put("kugou", "https://www.kugou.com/favicon.ico")
    put("kuwo", "https://www.kuwo.cn/static/image/logo.png")
    put("qq", "https://y.qq.com/favicon.ico")
    put("netease", "https://music.163.com/favicon.ico")
    put("qishui", "https://www.douyin.com/favicon.ico") // 汽水音乐无独立图标，用同厂抖音图标
    // 番茄畅听（fanqie.cn）站点已下线，没有可取的站点图标；它自带订阅源也不带 icon 字段。
    // —— 其余可取的源 ——
    put("migu", "https://www.migu.cn/favicon.ico")
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
 * 插件图标的候选地址，按优先级排列：
 * 1. 插件元数据的 `iconUrl`（插件 exports 里的 `icon` 由其兜底，见 `PluginMetaCodec`）；
 * 2. 按 `platform` 查表的站点图标；
 * 3. 插件 `srcUrl` 的站点 favicon（对 GitHub 托管的插件基本无用，留作自建订阅源兜底）。
 * 都没有则返回空表 —— 由调用方回退首字占位。
 */
internal fun pluginIconUrls(plugin: PluginMeta): List<String> {
    val urls = mutableListOf<String>()
    plugin.iconUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { urls += it }
    platformIconUrls[plugin.platform.trim()]?.let { urls += it }
    pluginFaviconUrl(plugin.srcUrl)
        ?.takeIf { it !in urls && !isCodeHost(it) }
        ?.let { urls += it }
    return urls
}

/** 代码托管站（GitHub 等）的 favicon 不是音源图标，不当候选。 */
private fun isCodeHost(url: String): Boolean =
    CODE_HOSTS.any { url.contains(it, ignoreCase = true) }

private val CODE_HOSTS = listOf("githubusercontent.com", "github.io", "github.com", "gitlab.", "gitee.com")

/** 由插件源地址推出站点 favicon；非 http(s) 或取不到 host 时返回 null。 */
private fun pluginFaviconUrl(srcUrl: String?): String? {
    val raw = srcUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
    val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
    return "$scheme://$host/favicon.ico"
}

/**
 * 插件列表行的图标：元数据 iconUrl → 平台站点图标 → 首字占位。
 *
 * 加载中与失败都停留在首字占位上（占位画在图片下方、仅在图片未就绪时参与绘制），
 * 所以既不会闪空白，也不会因为 URL 取不到 / 网络异常而崩；所有候选都失败后停在占位态。
 */
@Composable
internal fun PluginIcon(plugin: PluginMeta, modifier: Modifier = Modifier) {
    val candidates = remember(plugin.id, plugin.iconUrl, plugin.srcUrl, plugin.platform) {
        pluginIconUrls(plugin)
    }
    var index by remember(plugin.id, plugin.iconUrl, plugin.srcUrl) { mutableStateOf(0) }
    var loaded by remember(plugin.id, plugin.iconUrl, plugin.srcUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(PluginIconSize)
            .clip(CircleShape)
            .background(Gyp.tint),
        contentAlignment = Alignment.Center,
    ) {
        val url = candidates.getOrNull(index)
        if (url == null || !loaded) {
            PluginIconFallback(plugin)
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = plugin.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { loaded = false },
                onSuccess = { loaded = true },
                onError = {
                    loaded = false
                    // 元数据图标挂了就退到站点图标；再挂就停在首字占位。
                    if (index < candidates.lastIndex) index += 1
                },
            )
        }
    }
}

/** 首字占位：与 0.0.8 的样式完全一致（28dp 圆 + ink 7% 底 + accentText 半粗首字）。 */
@Composable
private fun PluginIconFallback(plugin: PluginMeta) {
    Text(
        plugin.displayName.take(1),
        fontSize = 13.sp,
        color = Gyp.accentText,
        fontWeight = FontWeight.SemiBold,
        fontFamily = GuangYueDesign.Sans,
    )
}
