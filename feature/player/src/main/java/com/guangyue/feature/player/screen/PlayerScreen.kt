package com.guangyue.feature.player.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.GuangYueDesign.Blue as GuangYueBlue
import com.guangyue.feature.player.lyrics.CoverImage
import com.guangyue.feature.player.lyrics.formatTime
/**
 * 播放页: polaroid cover, title, seek slider and the transport, on the same marble light
 * background as the home screen. Controls can be hidden to leave just the cover.
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sliderValue = if (dragging) dragValue else playback.progress

    val ink = if (darkMode) Color(0xFFF2F5FB) else Color(0xFF16202E)
    val cardBg = if (darkMode) Color(0xFF1A2236) else Color.White

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "收起",
                tint = ink,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
            )
            Text(
                "正在播放",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = ink.copy(alpha = 0.55f),
            )
            Icon(
                Icons.Outlined.Lyrics,
                contentDescription = "歌词",
                tint = accent,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onOpenLyrics() },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Polaroid card
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(cardBg)
                .padding(10.dp),
        ) {
            CoverImage(
                url = playback.item?.artworkUri,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp)),
                accent = accent,
                contentDescription = playback.item?.title,
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            playback.item?.title ?: "还没有在播放",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Serif,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            playback.item?.artist ?: "从首页挑一首歌吧",
            fontSize = 13.sp,
            color = ink.copy(alpha = 0.55f),
        )

        Spacer(Modifier.height(18.dp))

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
                thumbColor = GuangYueBlue,
                activeTrackColor = GuangYueBlue,
                inactiveTrackColor = ink.copy(alpha = 0.14f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), fontSize = 11.sp, color = ink.copy(alpha = 0.5f))
            Text(formatTime(playback.durationMs), fontSize = 11.sp, color = ink.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "随机",
                    tint = if (playback.shuffle) GuangYueBlue else ink.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, "上一首", tint = ink)
            }
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(GuangYueBlue),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, "下一首", tint = ink)
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = "循环",
                    tint = if (playback.repeatMode != 0) GuangYueBlue else ink.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FavoriteBorder, "收藏", tint = ink.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(14.dp))
            Text(
                "音质：${playback.item?.origin?.quality?.label ?: Quality.STANDARD.label}",
                fontSize = 11.sp,
                color = ink.copy(alpha = 0.5f),
            )
            Spacer(Modifier.size(14.dp))
            Icon(Icons.Filled.Favorite, "收藏", tint = Color.Transparent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(26.dp))
    }
}
