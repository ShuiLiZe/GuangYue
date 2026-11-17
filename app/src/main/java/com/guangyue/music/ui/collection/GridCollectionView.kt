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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.layout.layout
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

/**
 * 11-18 网格歌手页（Folia library/suites/grid/artist/ArtistGridView.tsx）的歌手信息：
 * 头像圆卡 + 简介卡排在中间一行，热门歌曲在上半、专辑在下半（[ArtistGridCoords]）。
 */
data class GridArtistHead(
    val name: String,
    val alias: String = "",
    val description: String? = null,
    val picture: String? = null,
    val trackCount: Int? = null,
    val albumCount: Int? = null,
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
internal fun hexBase(index: Int, spacingX: Float = SPACING_X, spacingY: Float = SPACING_Y): Pair<Float, Float> {
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
    return (x * spacingX + z * spacingX / 2f) to (z * spacingY)
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
    /** 取证用：开场就打开信息面板。 */
    initialPanelOpen: Boolean = false,
    /** 11-17：Folia 设置「卡片最小尺寸 / 最小透明度」（离中心最远的卡缩到 / 淡到多少）。 */
    minScale: Float = MIN_SCALE,
    minOpacity: Float = MIN_OPACITY,
    /** 11-17：Folia「全画幅封面」——封面铺满整张卡，歌名歌手叠在渐变遮罩上。 */
    fullBleedCover: Boolean = false,
    /** 11-17：Folia「正方形卡片」（只在全画幅时生效）：面积不变的正方形，间距同比（squareGridCardBox）。 */
    squareCards: Boolean = false,
    /** 11-18：不为 null = Folia 网格歌手页（头像 + 简介 + 热门歌曲 + 专辑）。 */
    artist: GridArtistHead? = null,
    /** 歌手页下半的专辑卡（点居中的那张 = 进专辑）。 */
    albums: List<GridTrackCard> = emptyList(),
    onOpenAlbum: (Int) -> Unit = {},
    /** 信息面板里的简介（null = 用 [subtitle]）。 */
    panelDescription: String? = null,
) {
    val square = squareCards && fullBleedCover
    val side = kotlin.math.round(sqrt(CARD_W * CARD_H))
    val cardW = if (square) side else CARD_W
    val cardH = if (square) side else CARD_H
    val spacingX = if (square) kotlin.math.round(SPACING_X * side / CARD_W) else SPACING_X
    val spacingY = if (square) kotlin.math.round(SPACING_Y * side / CARD_H) else SPACING_Y
    val daylight = (0.299f * ground.red + 0.587f * ground.green + 0.114f * ground.blue) > 0.5f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    val isArtist = artist != null
    val ox = remember { Animatable(if (isArtist) ArtistGridCoords.cameraAtOpen(spacingX).first else 0f) }
    val oy = remember { Animatable(0f) }
    var bioOpen by remember { mutableStateOf(false) }
    // 歌手页：0 头像、1 简介、2.. 热门歌曲、其后专辑；其它页：下标 = 曲目下标。
    fun slotOfSong(i: Int) = if (isArtist) i + 2 else i
    fun slotOfAlbum(j: Int) = 2 + cards.size + j
    var panelOpen by remember { mutableStateOf(initialPanelOpen) }
    var listOpen by remember { mutableStateOf(false) }
    var sortAz by remember { mutableStateOf(false) }
    val bases = remember(cards.size, albums.size, isArtist, square) {
        if (isArtist) ArtistGridCoords.build(cards.size, albums.size, spacingX, spacingY).toTypedArray()
        else Array(cards.size) { hexBase(it, spacingX, spacingY) }
    }

    val bounds = remember(bases, box) {
        if (bases.isEmpty()) floatArrayOf(0f, 0f, 0f, 0f) else {
            val wDp = with(density) { box.width.toDp().value }
            val hDp = with(density) { box.height.toDp().value }
            val bx = max(0f, wDp / 2 - 2 * spacingX)
            val by = max(0f, hDp / 2 - 2 * spacingY)
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
        // 歌手页：Folia 背景固定用第一首热门歌曲的封面。
        val bgCover = if (isArtist) cards.firstOrNull()?.artwork ?: artist?.picture ?: coverUrl
        else cards.getOrNull(centered)?.artwork ?: coverUrl
        if (bgCover != null) {
            AsyncImage(
                model = bgCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.1f; scaleY = 1.1f; alpha = if (daylight) 0.18f else 0.12f }
                    .blur(30.dp),
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
            if (box != IntSize.Zero && (cards.isNotEmpty() || isArtist)) {
                val wDp = with(density) { box.width.toDp().value }
                val hDp = with(density) { box.height.toDp().value }
                val dx = ox.value
                val dy = oy.value
                if (artist != null) {
                    // Folia：两行淡水印「时下流行 / POPULAR」「歌手专辑 / ALBUMS」，钉在头像簇旁（层级 80、6% 不透明）。
                    ArtistWatermark("时下流行 / POPULAR", dx - spacingX * 2.55f, dy - spacingY * 0.42f, ink, density.density)
                    if (albums.isNotEmpty()) ArtistWatermark("歌手专辑 / ALBUMS", dx + spacingX * 0.45f, dy + spacingY * 0.52f, ink, density.density)
                }
                for (n in bases.indices) {
                    val (bx, by) = bases[n]
                    val cx = bx + dx
                    val cy = by + dy
                    val halfW = if (isArtist && n == 1) 180f else cardW / 2
                    if (abs(cx) > wDp / 2 + halfW + 96 || abs(cy) > hDp / 2 + cardH / 2 + 96) continue
                    val dist = sqrt(cx * cx + cy * cy)
                    val p = min(dist / MAX_DISTANCE, 1f)
                    val scale = CENTER_SCALE - (CENTER_SCALE - minScale) * p
                    val alpha = 1f - (1f - minOpacity) * p
                    if (artist != null && n < 2) {
                        androidx.compose.runtime.key(if (n == 0) "__artist_avatar__" else "__artist_bio__") {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .offset { IntOffset((cx * density.density).roundToInt(), (cy * density.density).roundToInt()) }
                                    .zIndex(50f - 49f * p + if (n == 1) 5f else 0f)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                            ) {
                                if (n == 0) ArtistAvatar(artist.picture, ground, ink) { centerOn(0) }
                                else ArtistBioCard(artist, ink, daylight) { if (centered == 1) bioOpen = true else centerOn(1) }
                            }
                        }
                        continue
                    }
                    val songIdx = if (isArtist) n - 2 else n
                    if (songIdx >= cards.size) {
                        val j = songIdx - cards.size
                        val album = albums.getOrNull(j) ?: continue
                        androidx.compose.runtime.key("album:" + album.key) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .offset { IntOffset((cx * density.density).roundToInt(), (cy * density.density).roundToInt()) }
                                    .zIndex(50f - 49f * p)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                            ) {
                                PolaroidCard(
                                    card = album, cardW = cardW, cardH = cardH, fullBleed = fullBleedCover,
                                    ink = ink, ground = ground, daylight = daylight, current = false, playing = false,
                                    queueAlpha = 0f, playAlpha = 0f, playScale = 0.8f,
                                    // Folia openWhenFocusedOnCardClick：专辑卡点居中的那张才进专辑。
                                    onTap = { if (dist < 40f) onOpenAlbum(j) else centerOn(n) },
                                    onLong = {}, onPlay = { onOpenAlbum(j) }, onQueue = {},
                                )
                            }
                        }
                        continue
                    }
                    val i = songIdx
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
                                cardW = cardW,
                                cardH = cardH,
                                fullBleed = fullBleedCover,
                                ink = ink,
                                ground = ground,
                                daylight = daylight,
                                current = isCurrent(i),
                                playing = playing,
                                queueAlpha = queueAlpha,
                                playAlpha = playAlpha,
                                playScale = playScale,
                                onTap = { if (dist < 40f) onPlay(i) else centerOn(n) },
                                onLong = { onLongPress(i) },
                                onPlay = { if (isCurrent(i)) onTogglePlayback() else onPlay(i) },
                                onQueue = { onEnqueue(i) },
                            )
                        }
                    }
                }
            }
            if (cards.isEmpty() && !isArtist) {
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
            if (artist != null) {
                // Folia ArtistHeaderPillButton「热门歌曲加入队列」：窄屏只剩图标（h-10 w-10），底色同返回键，返回键右侧 gap-4。
                Box(
                    Modifier
                        .padding(start = 24.dp + 40.dp + 16.dp)
                        .size(40.dp)
                        .shadow(6.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(ground.copy(alpha = 0.6f))
                        .background(if (daylight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = cards.isNotEmpty(), onClick = onEnqueueAll)
                        .graphicsLayer { alpha = if (cards.isNotEmpty()) 1f else 0.4f },
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.ListPlus, contentDescription = "热门歌曲加入队列", tint = ink, modifier = Modifier.size(16.dp)) }
                if (loading || failed) {
                    // Folia：右上角加载 / 重试胶囊（bg 65%）。
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp)
                            .clip(CircleShape)
                            .background(ground.copy(alpha = 0.65f))
                            .clickable(enabled = failed, onClick = onRetry)
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(GuangYueIcons.RefreshCw, null, tint = ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (failed) "重试" else "正在加载…", color = ink, fontSize = 12.sp)
                    }
                }
            }
            Box(
                Modifier
                    .padding(start = 24.dp)
                    .size(40.dp)
                    .shadow(6.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(ground.copy(alpha = 0.6f))
                    .background(if (daylight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(GuangYueIcons.ChevronLeft, contentDescription = "返回", tint = ink, modifier = Modifier.size(20.dp)) }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    // 歌手页左边多一颗胶囊（到 120dp）：标题仍居中，只有放不下时才往右让，不压住胶囊、也不被两侧硬挤成「周…」。
                    .then(
                        if (isArtist) Modifier.layout { m, c ->
                            val left = 128.dp.roundToPx(); val right = 24.dp.roundToPx()
                            val pl = m.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth - left - right).coerceAtLeast(0)))
                            layout(c.maxWidth, pl.height) {
                                pl.place(((c.maxWidth - pl.width) / 2).coerceIn(left, (c.maxWidth - right - pl.width).coerceAtLeast(left)), 0)
                            }
                        } else Modifier.padding(horizontal = 72.dp),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    // Folia GridViewChrome 标题块：bg-color 20% 底 + backdrop-blur（手机上只铺底色）。
                    .background(ground.copy(alpha = 0.2f))
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
        // 歌手页（Folia GridListSearchButton「查看专辑」）列的是专辑；没有专辑时列热门歌曲。
        val listAlbums = isArtist && albums.isNotEmpty()
        val listCards = if (listAlbums) albums else cards
        fun slotOfListRow(i: Int) = if (listAlbums) slotOfAlbum(i) else slotOfSong(i)
        if (listCards.isNotEmpty()) {
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
                val order = remember(listCards, sortAz) {
                    if (sortAz) listCards.indices.sortedBy { listCards[it].title.lowercase() } else listCards.indices.toList()
                }
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 64.dp, end = 20.dp, top = 8.dp, bottom = 72.dp)
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
                            Text(if (listAlbums) "歌手专辑 / ALBUMS" else title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
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
                        items(order.size, key = { listCards[order[it]].key }) { n ->
                            val i = order[n]
                            val c = listCards[i]
                            val on = slotOfListRow(i) == centered
                            val cur = !listAlbums && isCurrent(i)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) ink.copy(alpha = 0.08f) else Color.Transparent)
                                    .pointerInput(c.key, listAlbums) {
                                        detectTapGestures(
                                            onTap = {
                                                centerOn(slotOfListRow(i)); listOpen = false
                                                // Folia：居中、收起，320ms 后进专辑。
                                                if (listAlbums) scope.launch { kotlinx.coroutines.delay(320); onOpenAlbum(i) }
                                            },
                                            onLongPress = { if (!listAlbums) onLongPress(i) },
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.1f))) {
                                    if (c.artwork != null) AsyncImage(model = c.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(c.title, color = if (cur) ink else ink.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.artist.isNotBlank()) Text(c.artist, color = ink.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
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
            // 11-17：手机横屏（矮屏）照 folia-sonnet androidPhoneFit ①：面板贴 10/10、宽 min(320, 46vw)、内边距 14，
            // 封面压成 64dp 横幅，按钮排收一档——让「播放全部」不用滚就露出来。竖屏不变。
            val conf = androidx.compose.ui.platform.LocalConfiguration.current
            val cutCompact = conf.screenHeightDp < 480
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { panelOpen = false }) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .then(
                            if (cutCompact) Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                                .widthIn(max = minOf(320f, conf.screenWidthDp * 0.46f).dp)
                            else Modifier.padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 24.dp).widthIn(max = 320.dp),
                        )
                        .shadow(24.dp, RoundedCornerShape(24.dp), clip = false)
                        .clip(RoundedCornerShape(24.dp))
                        .background(ground.copy(alpha = 0.92f))
                        .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .padding(if (cutCompact) 14.dp else 24.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .then(if (cutCompact) Modifier.height(64.dp) else Modifier.aspectRatio(1f))
                            .clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = 0.08f)),
                    ) {
                        val panelCover = artist?.picture ?: coverUrl
                        if (panelCover != null) AsyncImage(model = panelCover, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(title, color = ink, fontSize = if (cutCompact) 16.sp else 20.sp, fontWeight = FontWeight.Bold, lineHeight = if (cutCompact) 21.sp else 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (cutCompact) 10.dp else 16.dp))
                    Text(countLabel, color = ink.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    val about = panelDescription ?: subtitle
                    if (about.isNotBlank()) {
                        // Folia：简介 text-xs、opacity 65、max-h-40 自己滚（长简介不把按钮排推远）。
                        Text(
                            about, color = ink.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 18.sp,
                            maxLines = if (cutCompact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (cutCompact) 8.dp else 12.dp)
                                .then(if (cutCompact) Modifier else Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())),
                        )
                    }
                    Column(
                        Modifier.padding(top = if (cutCompact) 8.dp else 16.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (cutCompact) 4.dp else 8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.1f)))
                        Spacer(Modifier.height(if (cutCompact) 4.dp else 8.dp))
                        panelActions.forEach { a ->
                            Pill(a.label, ink, ground, primary = a.primary, danger = a.danger, enabled = a.enabled, full = true, compact = cutCompact) { panelOpen = false; a.run() }
                        }
                    }
                }
            }
        }

        // ---------- 简介全文（Folia Biography 浮层：黑 60% 底 + 圆角 24 卡，点外面收起） ----------
        AnimatedVisibility(visible = bioOpen && artist != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.zIndex(100f)) {
            val a = artist ?: return@AnimatedVisibility
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { bioOpen = false }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val conf = androidx.compose.ui.platform.LocalConfiguration.current
                Column(
                    Modifier
                        .widthIn(max = 576.dp)
                        .fillMaxWidth()
                        .heightIn(max = (conf.screenHeightDp * 0.7f).dp)
                        .shadow(24.dp, RoundedCornerShape(24.dp), clip = false)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (daylight) Color.White.copy(alpha = 0.92f) else Color(0xFF18181B).copy(alpha = 0.92f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(24.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("BIOGRAPHY", color = ink.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(a.name, color = ink, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            if (a.alias.isNotBlank()) Text(a.alias, color = ink.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { bioOpen = false }, contentAlignment = Alignment.Center) {
                            Icon(GuangYueIcons.X, contentDescription = "关闭", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        a.description?.takeIf { it.isNotBlank() } ?: "暂无详细介绍",
                        color = ink.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 16.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

/** Folia 歌手页水印：24sp 特粗衬线、字距 0.25em、6% 不透明，以 (x, y) 为中心。 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ArtistWatermark(text: String, x: Float, y: Float, ink: Color, density: Float) {
    Text(
        text,
        color = ink.copy(alpha = 0.06f),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Serif,
        letterSpacing = 6.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset((x * density).roundToInt(), (y * density).roundToInt()) }
            .zIndex(80f),
    )
}

/** Folia 歌手头像圆卡：200dp、白 10% 4dp 描边、底色 bg 20%；没有图时一枚唱片。 */
@Composable
private fun ArtistAvatar(picture: String?, ground: Color, ink: Color, onTap: () -> Unit) {
    Box(
        Modifier
            .requiredSize(200.dp)
            .clip(CircleShape)
            .background(ground.copy(alpha = 0.2f))
            .border(4.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(GuangYueIcons.Disc, null, tint = ink.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
        }
    }
}

/**
 * Folia 简介卡（360×200 手机断点）：rounded-3xl p-6、日光白 60% / 夜 zinc-900 60% + 描边；
 * 名字 30sp 特粗一行、别名 12sp 50%、48×2 天蓝短线；简介 12sp 65%；底部发丝线下「N 首 • M 专辑」10sp 40%。
 */
@Composable
private fun ArtistBioCard(a: GridArtistHead, ink: Color, daylight: Boolean, onTap: () -> Unit) {
    val stats = listOfNotNull(a.trackCount?.let { "$it 首" }, a.albumCount?.let { "$it 专辑" }).joinToString(" • ")
    Column(
        Modifier
            .requiredSize(width = 360.dp, height = 200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (daylight) Color.White.copy(alpha = 0.6f) else Color(0xFF18181B).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = if (daylight) 0.3f else 0.1f), RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(24.dp),
    ) {
        Text(a.name, color = ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (a.alias.isNotBlank()) {
            Text(a.alias, color = ink.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        Box(Modifier.padding(top = 8.dp).size(width = 48.dp, height = 2.dp).clip(CircleShape).background(Color(0xFF38BDF8).copy(alpha = 0.6f)))
        Text(
            a.description?.takeIf { it.isNotBlank() } ?: "暂无详细介绍",
            color = ink.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 19.sp,
            // Folia overflow-hidden 会把行拦腰切；这里按能放下的整行收尾加省略号。
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
        Text(stats, color = ink.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Pill(label: String, ink: Color, ground: Color, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, full: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    // 11-17：Folia GridViewCutInPanel 按钮排每颗都带 14px 图标（Play 实心 / ListPlus / Plus / RefreshCw …），主键 py-3，其余 py-2.5。
    val icon = if (full) panelIconFor(label) else null
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
            // 紧凑档：sonnet 按钮 padding 6px、字号 11px；手机触控最低 40dp。
            .then(
                if (compact) Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 6.dp)
                else Modifier.heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = if (primary) 12.dp else 10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(if (compact) 12.dp else 14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = fg, fontSize = if (compact) 11.sp else 12.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

/** 面板按钮的图标（照 Folia GridViewCutInPanel 各键）。 */
private fun panelIconFor(label: String): androidx.compose.ui.graphics.vector.ImageVector? = when {
    label.startsWith("播放") -> GuangYueIcons.Play
    label.contains("队列") -> GuangYueIcons.ListPlus
    label.contains("添加到歌单") -> GuangYueIcons.Plus
    label.contains("下载") -> GuangYueIcons.Download
    label.contains("重新加载") || label.contains("刷新") -> GuangYueIcons.RefreshCw
    label.contains("改名") || label.contains("重命名") -> GuangYueIcons.Pencil
    label.contains("删除") -> GuangYueIcons.Trash2
    else -> null
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PolaroidCard(
    card: GridTrackCard,
    cardW: Float,
    cardH: Float,
    fullBleed: Boolean,
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
    val actions: @Composable () -> Unit = {
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
    val meta: @Composable (Float) -> Unit = { a ->
        if (card.album.isNotBlank()) Text(card.album, color = ink.copy(alpha = a), fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatDuration(card.durationMs), color = ink.copy(alpha = a), fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace)
    }
    if (fullBleed) {
        // Folia PolaroidCard fullBleedCover：封面铺满，上面一层静态渐变（日间白、夜间黑，26%→100%：0/30/74/93%），字在底部 p-3。
        val shade = if (daylight) Color.White else Color.Black
        Box(
            Modifier
                .requiredSize(cardW.dp, cardH.dp)
                .shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(if (daylight) Color(0x99E4E4E7) else Color(0x9927272A))
                .border(1.dp, ink.copy(alpha = if (current) 0.32f else 0.05f), shape)
                .pointerInput(card.key) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() })
                },
        ) {
            if (card.artwork != null) {
                AsyncImage(model = card.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(GuangYueIcons.Disc, contentDescription = null, tint = ink.copy(alpha = 0.2f), modifier = Modifier.size(56.dp).align(Alignment.Center))
            }
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to shade.copy(alpha = 0f), 0.26f to shade.copy(alpha = 0f), 0.5f to shade.copy(alpha = 0.3f),
                        0.74f to shade.copy(alpha = 0.74f), 1f to shade.copy(alpha = 0.93f),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, color = ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (card.artist.isNotBlank()) {
                    Text(card.artist, color = ink.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) { meta(0.55f) }
                    actions()
                }
            }
        }
        return
    }
    Column(
        Modifier
            .requiredSize(cardW.dp, cardH.dp)
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
                    meta(0.35f)
                }
                actions()
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
