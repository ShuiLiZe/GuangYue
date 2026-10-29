package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.data.lyrics.Lyric
import com.guangyue.core.data.lyrics.LyricUiState
import com.guangyue.core.player.PlaybackState
import com.guangyue.feature.player.viewmodel.LyricsViewModel
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.canBlur
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 歌词流（浅色）—— big bold left-aligned lines, the sung line crisp + blue, neighbours blurred
 * and faded. Dragging the list pauses auto-scroll for 3 seconds (handled by the synchronizer),
 * and every control can be hidden with one tap so only the words remain.
 */
@Composable
fun LyricFlowScreen(
    vm: LyricsViewModel,
    state: LyricUiState,
    playback: PlaybackState,
    accent: Color,
    onBack: () -> Unit,
    onSwitchStage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }

    // Follow the music unless the user is manually browsing the lyric.
    LaunchedEffect(state.index, state.manual) {
        if (!state.manual && state.index >= 0) {
            listState.animateScrollToItem(state.index.coerceAtLeast(0))
        }
    }

    // Any drag freezes auto-scroll for 3 s (spec) and reveals the controls again.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            vm.onManualScroll()
            controlsVisible = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .marbleBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(48.dp))
            // 顶部：收起 + 封面标题（对应 LatticeChrome 的曲目头）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GySpace.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple(onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.ChevronDown,
                        "收起",
                        tint = Gyp.ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(GySpace.m))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(GyRadius.field))
                        .background(Gyp.tint),
                ) {
                    CoverImage(
                        url = playback.item?.artworkUri,
                        modifier = Modifier.fillMaxSize(),
                        accent = accent,
                    )
                }
                Spacer(Modifier.width(GySpace.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        playback.item?.title ?: "未在播放",
                        fontSize = GyText.emptyTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = Gyp.ink,
                        fontFamily = GuangYueDesign.Serif,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        playback.item?.artist ?: "广乐",
                        fontSize = GyText.hint,
                        lineHeight = GyText.hintHeight,
                        color = Gyp.t2,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple { controlsVisible = !controlsVisible },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.LayoutGrid,
                        if (controlsVisible) "隐藏控件" else "显示控件",
                        tint = Gyp.t2,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(GySpace.xl))

            Box(Modifier.weight(1f)) {
                if (state.lyric.isEmpty) {
                    com.guangyue.feature.library.design.FoliaEmptyState(
                        title = if (vm.loading.value) "歌词加载中" else "还没有歌词",
                        subtitle = "换一个音源或稍后再试",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(GySpace.xl),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 180.dp, horizontal = GySpace.xl),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.lyric.lines) { index, line ->
                            val distance = if (state.index < 0) index else abs(index - state.index)
                            LyricFlowLine(
                                text = line.text,
                                translation = line.translation,
                                distance = distance,
                                active = index == state.index,
                                accent = accent,
                            )
                        }
                    }
                }
            }

            if (controlsVisible) {
                FlowPlayerBar(
                    playback = playback,
                    onSeek = vm::seekToLine,
                    lyric = state.lyric,
                )
            }
        }
    }
}

/**
 * 一行歌词：字号/不透明度/模糊随与当前行的距离衰减。
 *
 * 模糊只在「非当前行」用，且取决于设备能力（Android 12+ 且非低内存），否则只用透明度区分，
 * 与 Folia 在低端机上的降级一致。
 */
@Composable
private fun LyricFlowLine(
    text: String,
    translation: String?,
    distance: Int,
    active: Boolean,
    accent: Color,
) {
    val fontSize = when (distance) {
        0 -> 32
        1 -> 26
        2 -> 22
        3 -> 19
        else -> 17
    }
    val alpha = when (distance) {
        0 -> 1f
        1 -> 0.34f
        2 -> 0.20f
        3 -> 0.12f
        else -> 0.07f
    }
    val blurAllowed = canBlur()
    val blurRadius = when (distance) {
        0 -> 0f
        1 -> 0.6f
        2 -> 1.6f
        3 -> 2.6f
        else -> 3.6f
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text.ifBlank { " " },
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.28).sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) accent.copy(alpha = 0.92f) else Gyp.ink.copy(alpha = alpha),
            textAlign = TextAlign.Start,
            fontFamily = GuangYueDesign.Serif,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (blurAllowed && blurRadius > 0f) {
                        Modifier.blur(
                            blurRadius.dp,
                            edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                        )
                    } else {
                        Modifier
                    }
                ),
        )
        if (active && !translation.isNullOrBlank()) {
            Text(
                translation,
                fontSize = GyText.label,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(top = GySpace.xs),
            )
        }
    }
}

/** 底部悬浮播放条（Folia LatticeChrome：2dp 进度线 + 等宽时间）。 */
@Composable
private fun FlowPlayerBar(
    playback: PlaybackState,
    onSeek: (Int) -> Unit,
    lyric: Lyric,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH, vertical = GySpace.pageH)
            .height(56.dp)
            .clip(RoundedCornerShape(GyRadius.card))
            .background(Gyp.card)
            .padding(horizontal = GySpace.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(GyRadius.fieldShape())
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GuangYueIcons.ChevronRight,
                    null,
                    tint = Gyp.t2,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(GySpace.m))
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Gyp.track),
                )
                Box(
                    Modifier
                        .fillMaxWidth(playback.progress)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Gyp.ink),
                )
            }
            Spacer(Modifier.width(GySpace.m))
            Text(
                formatTime(playback.positionMs),
                color = Gyp.t3,
                fontSize = GyText.hint,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}