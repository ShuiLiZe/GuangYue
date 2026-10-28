package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.core.data.lyrics.LyricUiState
import com.guangyue.core.player.PlaybackState
import com.guangyue.feature.player.viewmodel.LyricsViewModel
import com.guangyue.feature.library.design.GuangYueDesign.Blue as GuangYueBlue
import kotlin.math.cos
import kotlin.math.sin

/**
 * 歌词舞台（深色）: deep navy, floating geometry, big serif lyric whose characters are scattered
 * with slight rotation, the character being sung glows Google-blue with a halo, the translation
 * sits small at the bottom, and a dark capsule floats underneath.
 *
 * M1 keeps the motion simple (a slow drifting float + a breathing halo) — the per-character
 * rotation and lighting are already in place.
 */
@Composable
fun LyricStageScreen(
    vm: LyricsViewModel,
    state: LyricUiState,
    playback: PlaybackState,
    onTogglePlay: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drift = rememberInfiniteTransition(label = "stage")
    val float by drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "float",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .stageBackground(float),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val line: LyricLine? = state.line
            if (line == null) {
                Text(
                    if (state.lyric.isEmpty) "还没有歌词" else "♪",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                )
            } else {
                Text(
                    text = state.lyric.title ?: playback.item?.title.orEmpty(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(26.dp))
                ScatteredLyricLine(
                    text = line.text,
                    charIndex = state.charIndex,
                    accent = GuangYueBlue,
                )
                Spacer(Modifier.height(26.dp))
                val translation = line.translation
                if (!translation.isNullOrBlank()) {
                    Text(
                        translation,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
            StagePlayerBar(playback)
        }
    }
}

/** Each glyph is a separate Text with its own rotation + vertical offset, like the design. */
@Composable
private fun ScatteredLyricLine(
    text: String,
    charIndex: Int,
    accent: Color,
) {
    val chars = remember(text) { text.toList() }
    // Chunk characters into rows of ~7 so long lines stay readable on a phone.
    val rows = remember(chars) { chars.chunked(7) }
    var global = 0

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.Center) {
                row.forEachIndexed { i, ch ->
                    val index = global + i
                    val sung = charIndex >= 0 && index <= charIndex
                    val current = charIndex >= 0 && index == charIndex
                    // deterministic pseudo-random scatter
                    val seed = (index * 37) % 21
                    val rot = (seed - 10) * 0.7f
                    val dy = ((seed * 13) % 19 - 9).toFloat()

                    Text(
                        text = ch.toString(),
                        fontSize = if (current) 48.sp else 42.sp,
                        lineHeight = 58.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            current -> Color.White
                            sung -> accent.copy(alpha = 0.95f)
                            else -> Color.White.copy(alpha = 0.55f)
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = rot
                                translationY = dy
                                // soft blue halo around the character being sung
                                if (current) {
                                    shadowElevation = 24f
                                    spotShadowColor = accent
                                    ambientShadowColor = accent
                                }
                            }
                            .padding(horizontal = 1.dp),
                    )
                }
            }
            global += row.size
        }
    }
}

@Composable
private fun StagePlayerBar(playback: PlaybackState) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF121A36).copy(alpha = 0.62f))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    playback.item?.title ?: "未在播放",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(playback.progress)
                            .height(2.dp)
                            .background(GuangYueBlue),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { }) {
                Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(alpha = 0.75f))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GuangYueBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = Color.White,
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Filled.SkipNext, null, tint = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

/** Deep navy + drifting outlined geometry + a couple of glowing points. */
private fun Modifier.stageBackground(float: Float): Modifier = this
    .drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF0C1230), Color(0xFF0B1026), Color(0xFF090E20)),
                start = Offset.Zero,
                end = Offset(size.width, size.height * 0.6f),
            ),
        )

        val stroke = Color.White.copy(alpha = 0.07f)
        // Big outline circle, drifting slowly.
        drawCircle(
            color = stroke,
            radius = size.width * 0.46f,
            center = Offset(size.width * 0.78f + float * 12f, size.height * 0.22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
        )
        // Thin triangle wire.
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.52f + float * 8f)
            lineTo(size.width * 0.42f, size.height * 0.30f + float * 8f)
            lineTo(size.width * 0.33f, size.height * 0.72f + float * 8f)
            close()
        }
        drawPath(path, stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        // Gradient hairline.
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, GuangYueBlue.copy(alpha = 0.5f), Color.Transparent)),
            start = Offset(0f, size.height * 0.78f),
            end = Offset(size.width, size.height * 0.72f + float * 6f),
            strokeWidth = 1f,
        )
        // Two glowing points.
        listOf(
            Offset(size.width * 0.2f, size.height * 0.18f) to 2.5f,
            Offset(size.width * 0.86f, size.height * 0.66f) to 1.8f,
        ).forEach { (c, r) ->
            drawCircle(GuangYueBlue.copy(alpha = 0.5f), radius = r.dp.toPx() * 3, center = c)
            drawCircle(Color.White.copy(alpha = 0.8f), radius = r.dp.toPx() * 0.6f, center = c)
        }
    }

/** Slow rotating halo behind the current character. */
@Composable
private fun rememberHaloPhase(): Float {
    val t = rememberInfiniteTransition(label = "halo")
    val v by t.animateFloat(
        0f,
        6.2832f,
        infiniteRepeatable(tween(6000), RepeatMode.Restart),
        label = "phase",
    )
    return v
}
