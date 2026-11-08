package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.delay

/**
 * R1f：对译 Folia monet/monetPortraitCrossfade.ts（v0.7.25）—— 莫奈人像封面的图层栈，纯函数便于单测。
 *
 * 换封面是**叠一层**而不是换一张：快速切歌时上一层还没淡完，若成对替换就得扔掉半透明那层、露出底下一瞬
 *（正是交叉淡化要消掉的闪）。叠起来则每张新封面都盖在已有的上面淡入，顶层完全不透明后才把下面的清掉。
 * 广乐没有 Folia 的混音交接（automix hand-off），handoffFrom / leaving 两支不移。
 */
data class MonetPortraitLayer(val key: Int, val src: String)

object MonetPortraitCrossfade {
    /** 默认淡化时长，与莫奈背景层自己的换图一致。 */
    const val FADE_MS = 900

    /** 栈深上限：只有按住「下一首」才会到，此时底层早被盖满。 */
    const val MAX_LAYERS = 4

    fun target(layers: List<MonetPortraitLayer>): String? = layers.lastOrNull()?.src

    /** 解码好的封面压到顶上；与顶层同一地址的重复推送丢弃（URL 变成它自己不算新封面）。 */
    fun push(layers: List<MonetPortraitLayer>, src: String, key: Int): List<MonetPortraitLayer> {
        if (target(layers) == src) return layers
        val next = layers + MonetPortraitLayer(key, src)
        return if (next.size > MAX_LAYERS) next.takeLast(MAX_LAYERS) else next
    }

    /** `key` 那层淡入完成后丢掉它下面的全部；key 已不在栈里说明早被结算过，原样返回。 */
    fun settle(layers: List<MonetPortraitLayer>, key: Int): List<MonetPortraitLayer> {
        val i = layers.indexOfFirst { it.key == key }
        return if (i <= 0) layers else layers.drop(i)
    }
}

/**
 * 对译 MonetPortraitImage.tsx：新地址先在屏外解码（Coil 预取），成功后才叠到现有封面上淡入，
 * 所以相框永远不会空；解不出来的地址（404、截断）保持原封面不打洞。歌曲没有封面时整栈淡出但保留，
 * 下一张封面有东西可以盖。
 */
@Composable
fun MonetPortraitImage(
    src: String?,
    modifier: Modifier = Modifier,
    fadeMs: Int = MonetPortraitCrossfade.FADE_MS,
) {
    val context = LocalContext.current
    var layers by remember { mutableStateOf(emptyList<MonetPortraitLayer>()) }
    var nextKey by remember { mutableStateOf(0) }

    LaunchedEffect(src) {
        if (src.isNullOrBlank()) return@LaunchedEffect
        val ok = runCatching {
            SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(src).build()) is SuccessResult
        }.getOrDefault(false)
        if (!ok) return@LaunchedEffect
        nextKey += 1
        layers = MonetPortraitCrossfade.push(layers, src, nextKey)
    }

    // 只对顶层计时：淡入中途又来新封面会压新顶层并重新计时，所以整栈在最后那张不透明后一次性清掉。
    val settlingKey = if (layers.size > 1) layers.last().key else null
    LaunchedEffect(settlingKey) {
        if (settlingKey == null) return@LaunchedEffect
        delay(fadeMs.toLong())
        layers = MonetPortraitCrossfade.settle(layers, settlingKey)
    }

    val targetOpacity = if (src.isNullOrBlank()) 0f else 1f
    Box(modifier) {
        layers.forEach { layer ->
            key(layer.key) {
                val alpha = remember { Animatable(0f) }
                LaunchedEffect(targetOpacity) {
                    alpha.animateTo(targetOpacity, tween(fadeMs, easing = FastOutSlowInEasing))
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(layer.src).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
                )
            }
        }
    }
}
