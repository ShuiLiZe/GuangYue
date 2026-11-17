package com.guangyue.music.ui.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.icons.GuangYueIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Folia 网格集合页（src/library/suites/grid/collection/GridView.tsx，mode = "tracks"）的手机版。
 *
 * 2026-11-16 用户真机：歌单详情是我们自己的列表 / 拼贴墙，「完全和别人的不一样」。照 Folia 搬：
 *  - 整页：bg 底 + 封面低清放大模糊（日光 0.18 / 夜 0.12，GridView.tsx:1630）；
 *  - 左上 40dp 圆返回钮（left 24 / top 20），顶部居中标题胶囊（18sp 粗 + 描述 12sp 50% 两行），点标题开信息面板；
 *  - 中间是可拖动的蜂窝画布：中心外螺旋排位（hexViewport.ts getHexCubicAtIndex），手机断点
 *    卡 180×280、间距 205×270、maxDistance 420、lod 280–320（GridView.tsx:171）；
 *  - 每张卡按到视口中心的距离缩放 1.1→0.45、不透明 1→0.4、层级 50→1（utils/hexCardFalloff.ts）；
 *    播放钮只在距中心 <40 时出现，加入队列钮在 lod 区间淡出；
 *  - 卡片是拍立得（PolaroidCard.tsx:252）：p-3，方形封面圆角 8，标题 14 粗最多 4 行，歌手 10 / 55%，
 *    专辑 + 时长 9 等宽 / 35%，右下两颗 36dp 圆钮（播放 / 加入队列）。
 *  - 拖动松手惯性：framer inertia power 0.16 / timeConstant 220ms，边界 = 卡片范围 + 视口留白。
 * 手机化：点非中心卡 = 居中（Folia onCenter）；点已居中的卡 = 播放（触屏没有悬停找播放钮的习惯）。
 */
data class GridTrackCard(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: String?,
)

data class GridPanelAction(val label: String, val primary: Boolean = false, val danger: Boolean = false, val enabled: Boolean = true, val run: () -> Unit)

private const val CARD_W = 180f
private const val CARD_H = 280f
private const val SPACING_X = 205f
private const val SPACING_Y = 270f
private const val MAX_DISTANCE = 420f
private const val LOD_START = 280f
private const val LOD_END = 320f
private const val CENTER_SCALE = 1.1f
private const val MIN_SCALE = 0.45f
private const val MIN_OPACITY = 0.4f

private val DIRS = arrayOf(
    intArrayOf(0, 1, -1), intArrayOf(-1, 1, 0), intArrayOf(-1, 0, 1),
    intArrayOf(0, -1, 1), intArrayOf(1, -1, 0), intArrayOf(1, 0, -1),
)

/** hexViewport.ts getHexCubicAtIndex + resizeHexGridCoords：返回 (baseX, baseY)，单位 dp。 */
internal fun hexBase(index: Int): Pair<Float, Float> {
    if (index <= 0) return 0f to 0f
    var radius = 1
    while (index >= 1 + 3 * radius * (radius + 1)) radius++
    val ringStart = 1 + 3 * (radius - 1) * radius
    val ringOffset = index - ringStart
    val side = ringOffset / radius
    val steps = ringOffset % radius + 1
    var x = radius; var y = -radius; var z = 0
    for (s in 0 until side) { x += DIRS[s][0] * radius; y += DIRS[s][1] * radius; z += DIRS[s][2] * radius }
    x += DIRS[side][0] * steps; y += DIRS[side][1] * steps; z += DIRS[side][2] * steps
    return (x * SPACING_X + z * SPACING_X / 2f) to (z * SPACING_Y)
}

/** framer-motion inertia 的指数逼近：1 - e^(-t/τ)，在动画时长内归一。 */
private class InertiaEasing(private val durationMs: Float, private val tauMs: Float = 220f) : Easing {
    private val end = 1f - exp(-durationMs / tauMs)
    override fun transform(fraction: Float): Float = (1f - exp(-fraction * durationMs / tauMs)) / end
}

@Composable
fun GridCollectionView(
    title: String,
    subtitle: String,
    coverUrl: String?,
    cards: List<GridTrackCard>,
    loading: Boolean,
    failed: Boolean,
    ink: Color,
    ground: Color,
    countLabel: String,
    isCurrent: (Int) -> Boolean,
    playing: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Int) -> Unit,
    onTogglePlayback: () -> Unit,
    onEnqueue: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    panelActions: List<GridPanelAction>,
    onEnqueueAll: () -> Unit = {},
) {
    val daylight = (0.299f * ground.red + 0.587f * ground.green + 0.114f * ground.blue) > 0.5f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var panelOpen by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var sortAz by remember { mutableStateOf(false) }
    val bases = remember(cards.size) { Array(cards.size) { hexBase(it) } }

    val bounds = remember(bases, box) {
        if (bases.isEmpty()) floatArrayOf(0f, 0f, 0f, 0f) else {
            val wDp = with(density) { box.width.toDp().value }
            val hDp = with(density) { box.height.toDp().value }
            val bx = max(0f, wDp / 2 - 2 * SPACING_X)
            val by = max(0f, hDp / 2 - 2 * SPACING_Y)
            floatArrayOf(
                -bases.maxOf { it.first } - bx, -bases.minOf { it.first } + bx,
                -bases.maxOf { it.second } - by, -bases.minOf { it.second } + by,
            )
        }
    }
    fun clampX(v: Float) = v.coerceIn(bounds[0], bounds[1])
    fun clampY(v: Float) = v.coerceIn(bounds[2], bounds[3])

    fun centerOn(i: Int) {
        val (bx, by) = bases.getOrNull(i) ?: return
        scope.launch { ox.animateTo(-bx, tween(420, easing = InertiaEasing(420f, 120f))) }
        scope.launch { oy.animateTo(-by, tween(420, easing = InertiaEasing(420f, 120f))) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
            .onSizeChanged { box = it },
    ) {
        // 11-17 照 Folia APK：背景是当前居中那张卡的封面放大模糊（不是集合封面），夜间也看得清。
        val centered by remember(bases) {
            derivedStateOf {
                var best = -1; var bd = Float.MAX_VALUE
                for (i in bases.indices) { val (bx, by) = bases[i]; val d = (bx + ox.value).let { it * it } + (by + oy.value).let { it * it }; if (d < bd) { bd = d; best = i } }
                best
            }
        }
        val bgCover = cards.getOrNull(centered)?.artwork ?: coverUrl
        if (bgCover != null) {
            AsyncImage(
                model = bgCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; alpha = if (daylight) 0.22f else 0.3f }
                    .blur(24.dp),
            )
        }

        // ---------- 蜂窝画布 ----------
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(bases, bounds) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking(); scope.launch { ox.stop(); oy.stop() } },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            val vx = v.x / density.density
                            val vy = v.y / density.density
                            val tx = clampX(ox.value + vx * 0.16f)
                            val ty = clampY(oy.value + vy * 0.16f)
                            scope.launch { ox.animateTo(tx, tween(900, easing = InertiaEasing(900f))) }
                            scope.launch { oy.animateTo(ty, tween(900, easing = InertiaEasing(900f))) }
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = drag.x / density.density
                            val dy = drag.y / density.density
                            // dragElastic 0.05：越界只跟 5%。
                            val nx = ox.value + dx
                            val ny = oy.value + dy
                            val ex = if (nx < bounds[0] || nx > bounds[1]) ox.value + dx * 0.05f else nx
                            val ey = if (ny < bounds[2] || ny > bounds[3]) oy.value + dy * 0.05f else ny
                            scope.launch { ox.snapTo(ex); oy.snapTo(ey) }
                        },
                    )
                },
        ) {
            if (box != IntSize.Zero && cards.isNotEmpty()) {
                val wDp = with(density) { box.width.toDp().value }
                val hDp = with(density) { box.height.toDp().value }
                val dx = ox.value
                val dy = oy.value
                for (i in cards.indices) {
                    val (bx, by) = bases[i]
                    val cx = bx + dx
                    val cy = by + dy
                    if (abs(cx) > wDp / 2 + CARD_W / 2 + 96 || abs(cy) > hDp / 2 + CARD_H / 2 + 96) continue
                    val dist = sqrt(cx * cx + cy * cy)
                    val p = min(dist / MAX_DISTANCE, 1f)
                    val scale = CENTER_SCALE - (CENTER_SCALE - MIN_SCALE) * p
                    val alpha = 1f - (1f - MIN_OPACITY) * p
                    val queueAlpha = when {
                        dist < LOD_START -> 1f
                        dist <= LOD_END -> 1f - (dist - LOD_START) / (LOD_END - LOD_START)
                        else -> 0f
                    }
                    val playAlpha = if (dist < 40f) 1f - dist / 40f else 0f
                    val playScale = if (dist < 40f) 1f - 0.2f * (dist / 40f) else 0.8f
                    val card = cards[i]
                    androidx.compose.runtime.key(card.key) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .offset { IntOffset((cx * density.density).roundToInt(), (cy * density.density).roundToInt()) }
                                .zIndex(50f - 49f * p)
                                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                        ) {
                            PolaroidCard(
                                card = card,
                                ink = ink,
                                ground = ground,
                                daylight = daylight,
                                current = isCurrent(i),
                                playing = playing,
                                queueAlpha = queueAlpha,
                                playAlpha = playAlpha,
                                playScale = playScale,
                                onTap = { if (dist < 40f) onPlay(i) else centerOn(i) },
                                onLong = { onLongPress(i) },
                                onPlay = { if (isCurrent(i)) onTogglePlayback() else onPlay(i) },
                                onQueue = { onEnqueue(i) },
                            )
                        }
                    }
                }
            }
            if (cards.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        loading -> Text("正在加载…", color = ink.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        failed -> {
                            Text("加载失败", color = ink.copy(alpha = 0.4f), fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            Pill("重新加载", ink, ground, primary = true, onClick = onRetry)
                        }
                        else -> Text("这里还没有歌", color = ink.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }
            }
        }

        // ---------- 顶部：返回 + 标题胶囊 ----------
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 20.dp).zIndex(70f)) {
            Box(
                Modifier
                    .padding(start = 24.dp)
                    .size(40.dp)
                    .shadow(6.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(ground)
                    .background(ink.copy(alpha = 0.14f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(GuangYueIcons.ChevronLeft, contentDescription = "返回", tint = ink, modifier = Modifier.size(20.dp)) }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { panelOpen = !panelOpen }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(
                        GuangYueIcons.PanelLeft,
                        contentDescription = "信息", tint = ink.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = ink.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // ---------- 右下列表键 + 列表面板（Folia APK 手机版：圆键开一张浮动面板，排序 / 加入队列 / 关闭） ----------
        if (cards.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .size(40.dp)
                    .shadow(8.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(ground)
                    .background(ink.copy(alpha = 0.08f))
                    .clickable { listOpen = !listOpen }
                    .zIndex(75f),
                contentAlignment = Alignment.Center,
            ) { Icon(GuangYueIcons.List, contentDescription = "列表", tint = ink, modifier = Modifier.size(20.dp)) }
        }
        AnimatedVisibility(
            visible = listOpen,
            enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f),
            exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.96f),
            modifier = Modifier.zIndex(78f),
        ) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { listOpen = false }) {
                val order = remember(cards, sortAz) {
                    if (sortAz) cards.indices.sortedBy { cards[it].title.lowercase() } else cards.indices.toList()
                }
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 64.dp, end = 20.dp, top = 56.dp, bottom = 84.dp)
                        .fillMaxSize()
                        .shadow(24.dp, RoundedCornerShape(32.dp), clip = false)
                        .clip(RoundedCornerShape(32.dp))
                        .background(ground.copy(alpha = 0.94f))
                        .border(1.dp, ink.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Icon(
                                GuangYueIcons.ArrowDownAZ, contentDescription = "按名称排序",
                                tint = ink.copy(alpha = if (sortAz) 1f else 0.6f),
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable { sortAz = !sortAz },
                            )
                            Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onEnqueueAll() }, contentAlignment = Alignment.Center) {
                            Icon(GuangYueIcons.ListEnd, contentDescription = "全部加入队列", tint = ink, modifier = Modifier.size(20.dp))
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { listOpen = false }, contentAlignment = Alignment.Center) {
                            Icon(GuangYueIcons.X, contentDescription = "关闭", tint = ink, modifier = Modifier.size(20.dp))
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(order.size, key = { cards[order[it]].key }) { n ->
                            val i = order[n]
                            val c = cards[i]
                            val on = i == centered
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) ink.copy(alpha = 0.08f) else Color.Transparent)
                                    .pointerInput(c.key) { detectTapGestures(onTap = { centerOn(i); listOpen = false }, onLongPress = { onLongPress(i) }) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.1f))) {
                                    if (c.artwork != null) AsyncImage(model = c.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(c.title, color = if (isCurrent(i)) ink else ink.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = if (isCurrent(i)) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.artist.isNotBlank()) Text(c.artist, color = ink.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 信息面板（Folia cut-in：封面 + 名字 + 数量 + 动作） ----------
        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn() + slideInHorizontally { -it / 6 },
            exit = fadeOut() + slideOutHorizontally { -it / 6 },
            modifier = Modifier.zIndex(80f),
        ) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { panelOpen = false }) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 24.dp)
                        .widthIn(max = 320.dp)
                        .shadow(24.dp, RoundedCornerShape(24.dp), clip = false)
                        .clip(RoundedCornerShape(24.dp))
                        .background(ground.copy(alpha = 0.92f))
                        .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = 0.08f))) {
                        if (coverUrl != null) AsyncImage(model = coverUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
                    Text(countLabel, color = ink.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = ink.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    Column(
                        Modifier.padding(top = 16.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.1f)))
                        Spacer(Modifier.height(8.dp))
                        panelActions.forEach { a ->
                            Pill(a.label, ink, ground, primary = a.primary, danger = a.danger, enabled = a.enabled, full = true) { panelOpen = false; a.run() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, ink: Color, ground: Color, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, full: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        primary -> ink
        else -> ink.copy(alpha = 0.08f)
    }
    val fg = when {
        primary -> ground
        danger -> Color(0xFFEF4444)
        else -> ink
    }
    Box(
        (if (full) Modifier.fillMaxWidth() else Modifier)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PolaroidCard(
    card: GridTrackCard,
    ink: Color,
    ground: Color,
    daylight: Boolean,
    current: Boolean,
    playing: Boolean,
    queueAlpha: Float,
    playAlpha: Float,
    playScale: Float,
    onTap: () -> Unit,
    onLong: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .requiredSize(CARD_W.dp, CARD_H.dp)
            .shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(ground.copy(alpha = 0.95f))
            .border(1.dp, ink.copy(alpha = if (current) 0.32f else 0.05f), shape)
            .pointerInput(card.key) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() })
            }
            .padding(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .background(if (daylight) Color(0x99E4E4E7) else Color(0x9927272A)),
            contentAlignment = Alignment.Center,
        ) {
            if (card.artwork != null) {
                AsyncImage(model = card.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(GuangYueIcons.Disc, contentDescription = null, tint = ink.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(card.title, color = ink.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.artist.isNotBlank()) {
                    Text(card.artist, color = ink.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    if (card.album.isNotBlank()) Text(card.album, color = ink.copy(alpha = 0.35f), fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(card.durationMs), color = ink.copy(alpha = 0.35f), fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playAlpha > 0f) {
                        RoundBtn(ink, Modifier.graphicsLayer { alpha = playAlpha; scaleX = playScale; scaleY = playScale }, onPlay) {
                            if (current && playing) Icon(GuangYueIcons.Pause, contentDescription = "暂停", tint = ink, modifier = Modifier.size(15.dp))
                            else androidx.compose.foundation.Canvas(Modifier.size(14.dp).offset(x = 1.dp)) {
                                // Folia 卡上播放键是实心三角。
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * 0.18f, size.height * 0.08f); lineTo(size.width * 0.92f, size.height * 0.5f)
                                    lineTo(size.width * 0.18f, size.height * 0.92f); close()
                                }
                                drawPath(path, ink)
                            }
                        }
                    }
                    if (queueAlpha > 0f) {
                        RoundBtn(ink, Modifier.graphicsLayer { alpha = queueAlpha }, onQueue) {
                            Icon(GuangYueIcons.Plus, contentDescription = "加入队列", tint = ink, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundBtn(ink: Color, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.size(36.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
