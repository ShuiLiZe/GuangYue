package com.guangyue.feature.player.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyGlass
import com.guangyue.feature.library.design.GyMotion
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.glassPanel
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.player.lyrics.CoverImage
import com.guangyue.feature.player.lyrics.formatTime

/**
 * 全屏播放页（m00161）。
 *
 * 手势：向上滑进入（由外壳处理），下滑关闭；页内左右滑切换「封面 / 歌词」两页。
 * 视觉取 Folia 的 LatticeChrome：控件条最宽 480dp、按钮 44×44 圆角 12dp 图标 20dp、
 * 进度条 2dp、时间 12px 等宽且不透明度 .6；封面是圆角卡片，标题衬线。
 */
@Composable
fun IPlayerRoute(
    playback: PlaybackState,
    accent: Color,
    darkMode: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onQuality: () -> Unit,
    onOpenLyrics: () -> Unit,
    lyricPreview: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sliderValue = if (dragging) dragValue else playback.progress
    var page by remember { mutableIntStateOf(0) } // 0 封面 / 1 歌词
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var closeOffset by remember { mutableFloatStateOf(0f) }

    val ink = Gyp.ink
    val alpha by animateFloatAsState(
        targetValue = if (page == 0) 1f else 0f,
        animationSpec = tween(GyMotion.MOVE_MS, easing = GyMotion.easeOut),
        label = "coverAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .marbleBackground()
            // 下滑关闭：位移超过 180dp 或快速下甩即回退
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (closeOffset > 180.dp.toPx()) onBack() else closeOffset = 0f
                    },
                    onDragCancel = { closeOffset = 0f },
                ) { _, amount -> closeOffset = (closeOffset + amount).coerceAtLeast(0f) }
            }
            .offset { IntOffset(0, closeOffset.toInt()) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GySpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(GySpace.xl))

            // 顶栏：收起 / 正在播放 / 歌词
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.ChevronDown,
                        "收起",
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "正在播放",
                    fontSize = GyText.hint,
                    letterSpacing = 2.sp,
                    color = Gyp.t2,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple { onOpenLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.Captions,
                        "歌词",
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(GySpace.l))

            // 左右滑切页：封面 / 歌词
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(page) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffset < -60.dp.toPx() && page == 0) page = 1
                                if (dragOffset > 60.dp.toPx() && page == 1) page = 0
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        ) { _, amount -> dragOffset += amount }
                    },
            ) {
                // 封面
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(GyRadius.card))
                        .background(Gyp.card),
                ) {
                    CoverImage(
                        url = playback.item?.artworkUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(GyRadius.card))
                            .padding(GySpace.s),
                        accent = accent,
                        contentDescription = playback.item?.title,
                    )
                }
                if (alpha < 1f) {
                    // 歌词页（叠在封面上，透明度随页码过渡）
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(GyRadius.card))
                            .background(Gyp.preview),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(GySpace.s),
                        ) {
                            Icon(
                                GuangYueIcons.Captions,
                                null,
                                tint = Gyp.t3,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = lyricPreview.ifBlank { "点击「歌词」进入逐行滚动" },
                                fontSize = GyText.emptyTitle,
                                color = ink,
                                fontFamily = GuangYueDesign.Serif,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = GySpace.l),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(GySpace.xl))

            Text(
                text = playback.item?.title ?: "还没有在播放",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                fontFamily = GuangYueDesign.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GySpace.xxs))
            Text(
                text = playback.item?.artist ?: "从首页挑一首歌吧",
                fontSize = GyText.label,
                color = Gyp.t2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 0.0.7：取不到播放地址不能再沉默。错误来自 PlaybackController（getMediaSource 返回空 /
            // 两次重试都失败），这里直接告诉用户下一步该做什么——换一个音源搜同名歌曲。
            val failure = playback.error
            if (!failure.isNullOrBlank()) {
                Spacer(Modifier.height(GySpace.m))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.row))
                        .background(Gyp.danger.copy(alpha = 0.10f))
                        .padding(horizontal = GySpace.m, vertical = 8.dp),
                ) {
                    Text(
                        text = failure,
                        fontSize = GyText.hint,
                        color = Gyp.danger,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "用其他音源搜同名歌曲",
                        fontSize = GyText.hint,
                        color = Gyp.t2,
                    )
                }
            }

            Spacer(Modifier.height(GySpace.l))

            // 进度条：Folia LatticeChrome 的 2dp 细线
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek(dragValue)
                },
                colors = SliderDefaults.colors(
                    thumbColor = ink,
                    activeTrackColor = ink,
                    inactiveTrackColor = Gyp.track,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatTime(playback.positionMs),
                    fontSize = GyText.hint,
                    color = Gyp.t3,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    text = formatTime(playback.durationMs),
                    fontSize = GyText.hint,
                    color = Gyp.t3,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(GySpace.l))

            // 传输控件：44dp 圆角 12dp 按钮、20dp 图标，主按钮 68dp
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    icon = GuangYueIcons.Shuffle,
                    active = playback.shuffle,
                    onClick = onToggleShuffle,
                    contentDescription = "随机",
                )
                TransportButton(
                    icon = GuangYueIcons.ChevronLeft,
                    active = true,
                    onClick = onPrevious,
                    contentDescription = "上一首",
                    rotate180 = false,
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(ink)
                        .clickableNoRipple(onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                        contentDescription = "播放/暂停",
                        tint = Gyp.onAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                TransportButton(
                    icon = GuangYueIcons.ChevronRight,
                    active = true,
                    onClick = onNext,
                    contentDescription = "下一首",
                )
                TransportButton(
                    icon = if (playback.repeatMode == 2) GuangYueIcons.Repeat1 else GuangYueIcons.Repeat,
                    active = playback.repeatMode != 0,
                    onClick = onCycleRepeat,
                    contentDescription = "循环",
                )
            }

            Spacer(Modifier.height(GySpace.m))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(GyRadius.field))
                        .clickableNoRipple(onQuality)
                        .padding(horizontal = GySpace.s, vertical = GySpace.xxs),
                ) {
                    Text(
                        text = "音质：${playback.item?.origin?.quality?.label ?: Quality.STANDARD.label}",
                        fontSize = GyText.hint,
                        color = Gyp.t2,
                    )
                }
            }

            Spacer(Modifier.height(GySpace.xl))
        }
    }
}

/** LatticeChrome 的传输按钮：44×44、圆角 12dp、图标 20dp，选中时用主色。 */
@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    rotate180: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(GyRadius.card))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Gyp.ink else Gyp.t2,
            modifier = Modifier
                .size(20.dp)
                .then(
                    if (rotate180) {
                        Modifier.padding(0.dp)
                    } else {
                        Modifier
                    }
                ),
        )
    }
}
