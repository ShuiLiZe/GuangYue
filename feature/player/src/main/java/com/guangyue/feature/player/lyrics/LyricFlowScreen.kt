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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.guangyue.feature.library.design.GuangYueDesign.Blue as GuangYueBlue
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
            .lyricFlowBackground(accent)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            // single tap area: toggle controls
                        }
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(48.dp))
            // Cover + title block, left aligned like the design.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.55f)),
                ) {
                    CoverImage(
                        url = playback.item?.artworkUri,
                        modifier = Modifier.fillMaxSize(),
                        accent = accent,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        playback.item?.title ?: "未在播放",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F1A2B),
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        playback.item?.artist ?: "广乐",
                        fontSize = 13.sp,
                        color = Color(0xFF0F1A2B).copy(alpha = 0.55f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(Modifier.weight(1f)) {
                if (state.lyric.isEmpty) {
                    Text(
                        if (vm.loading.value) "歌词加载中…" else "这首歌还没有歌词",
                        color = Color(0xFF0F1A2B).copy(alpha = 0.45f),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 180.dp, horizontal = 28.dp),
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

            // Floating capsule player bar (only the lyric view hides it).
            if (controlsVisible) {
                FlowPlayerBar(
                    playback = playback,
                    onSeek = vm::seekToLine,
                    lyric = state.lyric,
                )
            }
        }

        // Tap "hide controls" affordance at top-right.
        IconButton(
            onClick = { controlsVisible = !controlsVisible },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (controlsVisible) "隐藏控件" else "显示控件",
                tint = Color(0xFF0F1A2B).copy(alpha = 0.6f),
            )
        }
    }
}

/** One line in the flow: 800 weight, sized + blurred by distance from the sung line. */
@Composable
private fun LyricFlowLine(
    text: String,
    translation: String?,
    distance: Int,
    active: Boolean,
    accent: Color,
) {
    val fontSize = when (distance) {
        0 -> 36
        1 -> 30
        2 -> 26
        3 -> 23
        else -> 20
    }
    val alpha = when (distance) {
        0 -> 1f
        1 -> 0.34f
        2 -> 0.20f
        3 -> 0.12f
        else -> 0.07f
    }
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
            fontWeight = FontWeight.ExtraBold,
            color = if (active) accent.copy(alpha = 0.92f) else Color(0xFF0F1A2B).copy(alpha = alpha),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = 1f }
                .blur(blurRadius.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
        )
        if (active && !translation.isNullOrBlank()) {
            Text(
                translation,
                fontSize = 14.sp,
                color = Color(0xFF0F1A2B).copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Bottom capsule with cover, progress and transport. */
@Composable
private fun FlowPlayerBar(
    playback: PlaybackState,
    onSeek: (Int) -> Unit,
    lyric: Lyric,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF101828).copy(alpha = 0.86f))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { }) {
                Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(alpha = 0.8f))
            }
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(playback.progress)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(GuangYueBlue),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatTime(playback.positionMs),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
    }
}

private fun Modifier.lyricFlowBackground(accent: Color): Modifier = this.drawBehind {
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFFF3E4D6), Color(0xFFECE7E4), Color(0xFFDFD7D2)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    // Album-colour haze: big soft radial blobs, no blur() needed (gradients are free).
    drawCircle(
        Brush.radialGradient(
            listOf(accent.copy(alpha = 0.34f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 0.16f),
            radius = size.width * 0.55f,
        ),
        radius = size.width * 0.55f,
        center = Offset(size.width * 0.18f, size.height * 0.16f),
    )
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF9FB6D9).copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height * 0.55f),
            radius = size.width * 0.6f,
        ),
        radius = size.width * 0.6f,
        center = Offset(size.width * 0.9f, size.height * 0.55f),
    )
    // 5px dot texture.
    val step = 5.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(Color(0xFF0F1A2B).copy(alpha = 0.05f), radius = 0.6f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
