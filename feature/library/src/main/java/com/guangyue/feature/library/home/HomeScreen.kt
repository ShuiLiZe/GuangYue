package com.guangyue.feature.library.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.marbleBackground
import kotlin.math.abs

/** One card on the home coverflow. */
data class ShelfCard(
    val id: Long,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
)

/**
 * 首页 — the light marble landing page.
 *
 * Layers, bottom to top: liquid-marble gradient → marble veins → fine dot texture → content.
 * Google blue only shows up in the selected tab capsule, the mini progress bar and the play
 * button, exactly as specified.
 */
@Composable
fun HomeScreen(
    cards: List<ShelfCard>,
    selectedTab: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    onSearch: () -> Unit,
    onOpenCard: (ShelfCard) -> Unit,
    onPluginManager: () -> Unit,
    onDownloads: () -> Unit = {},
    playbackTitle: String,
    playbackArtwork: String?,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    var center by remember(cards) { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .marbleBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(44.dp))

            // ── top bar: app name ←→ search capsule
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "广乐",
                    fontSize = 27.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Gyp.ink,
                    modifier = Modifier.clickable { onPluginManager() },
                )
                Spacer(Modifier.weight(1f))
                DownloadEntry(onClick = onDownloads)
                Spacer(Modifier.width(10.dp))
                SearchCapsule(onClick = onSearch)
            }

            Spacer(Modifier.height(20.dp))

            // ── segmented capsule tabs: 歌单 / 电台 / 专辑 / 本地
            SegmentedTabs(tabs = tabs, selected = selectedTab, onSelected = onTabSelected)

            Spacer(Modifier.height(18.dp))

            // ── polaroid coverflow
            Coverflow(
                cards = cards,
                center = center,
                onCenterChange = { center = it },
                onOpen = onOpenCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
            )

            Spacer(Modifier.height(10.dp))

            // ── big serif headline under the flow
            val current = cards.getOrNull(center)
            Text(
                current?.title ?: "还没有歌单",
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Gyp.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Text(
                current?.subtitle ?: "从插件搜索一首歌，收藏成自己的歌单",
                fontSize = 12.sp,
                color = Gyp.inkSoft,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp),
            )

            Spacer(Modifier.weight(1f))

            // ── floating capsule mini player
            MiniPlayerBar(
                title = playbackTitle,
                artwork = playbackArtwork,
                progress = progress,
                isPlaying = isPlaying,
                onTogglePlay = onTogglePlay,
                onOpen = onOpenNowPlaying,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            )

            // ── 底部导航（由 App 外壳传入；单独预览/截图时不传，页面自身仍然完整）
            bottomBar?.invoke()
        }
    }
}

@Composable
private fun SearchCapsule(onClick: () -> Unit) {
    Row(
        Modifier
            .width(168.dp)
            .height(40.dp)
            .clip(CircleShape)
            .background(Gyp.card.copy(alpha = if (Gyp.isDark) 0.10f else 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "搜索",
            tint = Gyp.ink.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "搜索歌曲、歌手",
            fontSize = 12.sp,
            color = Gyp.ink.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 顶栏的下载入口：圆角方块 + 云下载图标，作为「下载管理」页的常驻入口。 */
@Composable
private fun DownloadEntry(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Gyp.card.copy(alpha = if (Gyp.isDark) 0.10f else 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.CloudDownload,
            contentDescription = "下载管理",
            tint = GuangYueDesign.Blue,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SegmentedTabs(tabs: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(GuangYueDesign.RadiusTab.dp))
            .background(Gyp.card.copy(alpha = if (Gyp.isDark) 0.08f else 0.55f))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(GuangYueDesign.RadiusTabItem.dp))
                    .background(if (active) GuangYueDesign.Blue else Color.Transparent)
                    .clickable { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else Gyp.ink.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/**
 * Hand-rolled coverflow: no pager, so we get the exact Folia look — the middle polaroid is
 * scaled up with a long soft shadow, neighbours shrink, tilt and fade outwards.
 */
@Composable
private fun Coverflow(
    cards: List<ShelfCard>,
    center: Int,
    onCenterChange: (Int) -> Unit,
    onOpen: (ShelfCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        // 没有歌单时不画空盒子：下面的「还没有歌单 / 从插件搜索一首歌…」两行大字就是这一块的
        // 空状态，再叠一个「暂无内容」只会重复。
        Box(modifier)
        return
    }

    BoxWithTap(
        modifier = modifier,
        onTap = { onOpen(cards[center.coerceIn(0, cards.lastIndex)]) },
        onSwipeLeft = { onCenterChange((center + 1).coerceAtMost(cards.lastIndex)) },
        onSwipeRight = { onCenterChange((center - 1).coerceAtLeast(0)) },
    ) {
        cards.forEachIndexed { index, card ->
            val offset = index - center
            val distance = abs(offset).coerceAtMost(3)
            val scale by animateFloatAsState(
                targetValue = when (distance) {
                    0 -> 1.25f
                    1 -> 0.9f
                    2 -> 0.74f
                    else -> 0.62f
                },
                label = "scale",
            )
            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.88f
                2 -> 0.55f
                else -> 0.3f
            }
            val shiftPx = offset * 118

            Box(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = shiftPx.dp.toPx()
                        translationY = distance * 6f
                        rotationZ = when (distance) {
                            0 -> 0f
                            1 -> if (offset < 0) -7f else 7f
                            else -> if (offset < 0) -13f else 13f
                        }
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .width(150.dp)
                    .height(196.dp)
                    .then(
                        if (distance == 0) {
                            Modifier.shadow(
                                elevation = 22.dp,
                                shape = RoundedCornerShape(GuangYueDesign.RadiusPolaroid.dp),
                                ambientColor = Color(0x55244872),
                                spotColor = Color(0x66244872),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(GuangYueDesign.RadiusPolaroid.dp))
                    .background(Color.White)
                    .padding(8.dp)
                    .clickable { onCenterChange(index) },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFDCE5F3), Color(0xFFF0E8E0)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (card.artworkUrl != null) {
                        AsyncImage(
                            model = card.artworkUrl,
                            contentDescription = card.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            card.title.take(1),
                            fontFamily = FontFamily.Serif,
                            fontSize = 42.sp,
                            color = Gyp.ink.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

/** Tap = open, horizontal drag = move the coverflow. */
@Composable
private fun BoxWithTap(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .pointerInput(onTap, onSwipeLeft, onSwipeRight) {
                val threshold = 60.dp.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged < -threshold) onSwipeLeft()
                        if (dragged > threshold) onSwipeRight()
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragged = if (dragged * dragAmount > 0f) dragged + dragAmount else dragAmount
                    },
                )
            }
            .pointerInput(onTap) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artwork: String?,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(GuangYueDesign.RadiusCapsule.dp),
                ambientColor = Color(0x88244872),
                spotColor = Color(0x88244872),
            )
            .clip(RoundedCornerShape(GuangYueDesign.RadiusCapsule.dp))
            .background(Gyp.card.copy(alpha = if (Gyp.isDark) 0.10f else 0.68f))
            .clickable { onOpen() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCE5F3)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                AsyncImage(model = artwork, contentDescription = title, modifier = Modifier.fillMaxSize())
            } else {
                Text("♪", color = Gyp.ink.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gyp.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(Gyp.ink.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.5.dp)
                        .background(GuangYueDesign.Blue),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(GuangYueDesign.Blue),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * The signature home background: five stacked soft radial washes (white / ice blue / warm sand)
 * over a diagonal gradient, then SVG-style marble veins, then a 1px every-7px dot grid.
 */


