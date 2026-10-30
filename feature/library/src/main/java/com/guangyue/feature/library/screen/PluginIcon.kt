package com.guangyue.feature.library.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.PluginPlatformIcons
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.Gyp

/** 插件列表行左侧圆形图标的直径（0.0.8 沿用 28dp）。 */
internal val PluginIconSize = 28.dp

private const val TAG = "GYICON"

/**
 * 插件列表行左侧图标（0.0.9 b 重写）。
 *
 * 0.0.9 a 的做法是直接把站点 favicon 的 URL 交给 Coil 的 `AsyncImage(model = url)`，
 * 真机上**一张都没出来**（全是首字占位）。根因见 `docs/ui-0.0.9/b/report.md`，两条：
 *
 * 1. `AsyncImage(model = String)` 交给 Coil 的是**裸字符串**，没有 `crossfade`/尺寸约束，
 *    而且图标控件只有 28dp，Coil 按目标尺寸精确解码 PNG/ICO 时容易走到「尺寸不匹配 / 换源」；
 * 2. 更重要的是**没有任何请求级约束**：站点 favicon 动辄 50–300KB、有的站还会挂几十秒，
 *    Coil 默认在主线程之外排队，但一堆图标同时失败时 `onError` 会立刻把 `index` 推进到空表，
 *    之后再不重试 —— 一次网络抖动就永久停在首字占位。
 *
 * 现在改成：
 * - 用 [PluginPlatformIcons]（core:plugin，纯逻辑、可单测）算候选地址；
 * - 用 [ImageRequest] 显式给 `Precision.INEXACT`（28dp 控件不必精确解码，避免换源失败）
 *   + `allowHardware` 默认 + 独立的 OkHttp 超时（见 `PluginIconLoader`）；
 * - 成功才亮图，失败保留首字占位，**不改 index**（下一条候选由 [PluginPlatformIcons] 预先排好，
 *   真正要重试由用户下拉/离开再进来自动重来），列表滚动不受任何图标请求阻塞。
 */
@Composable
internal fun PluginIcon(plugin: PluginMeta, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val candidates = remember(plugin.id, plugin.iconUrl, plugin.srcUrl, plugin.platform) {
        PluginPlatformIcons.candidatesFor(plugin)
    }
    // 图标候选里第一个能解码成功的地址；null = 全部失败/没有候选 → 停在首字占位。
    val resolved by produceState<String?>(initialValue = null, plugin.id, candidates) {
        value = PluginIconLoader.firstUsable(context, candidates).also {
            Log.d(TAG, "icon ${plugin.platform} -> ${it ?: "占位"}")
        }
    }

    Box(
        modifier = modifier
            .size(PluginIconSize)
            .clip(CircleShape)
            .background(Gyp.tint),
        contentAlignment = Alignment.Center,
    ) {
        if (resolved == null) {
            PluginIconFallback(plugin)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolved)
                    .size(PluginIconSize.value.toInt() * 2)
                    .precision(Precision.INEXACT)
                    .build(),
                contentDescription = plugin.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { Log.d(TAG, "icon ${plugin.platform} draw FAIL") },
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
