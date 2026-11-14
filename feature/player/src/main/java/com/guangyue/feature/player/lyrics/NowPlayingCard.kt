package com.guangyue.feature.player.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

/**
 * 「正在播放 / 接下来播放」卡片（Folia `components/app/overlays/NowPlayingToast.tsx`）。
 *
 * Folia 规则照搬：
 *  - auto 模式：换歌后显示 [NOW_PLAYING_CARD_TIMEOUT_MS]（Folia `stageTrackPillTimeoutSec` 默认 10 秒）再淡出；
 *  - 曲目剩余 ≤ [NEXT_UP_LEAD_MS]（Folia `NEXT_UP_LEAD_SEC = 5`）且推得出下一首时，整卡换成下一首并挂「接下来播放」，
 *    这期间不隐藏；切过去后翻回「正在播放」重新计时；
 *  - 推不出下一首（单曲循环、随机、列表末尾不循环）就不预告（Folia resolveNextUpTrack：宁可不报也不报错）。
 *
 * 手机位置照 folia-sonnet `androidPhoneFit.css`：竖屏横屏都停在左上返回键下方（安全区顶 + 72px、左 12px），
 * 不压底部播放胶囊。尺寸手机化：标签 12sp、歌名 14sp、歌手 12sp（Folia 9/13/11px 在手机上太小）。
 */
data class NowPlayingCardSong(
    val title: String,
    val artist: String,
    val coverUrl: String?,
)

/** Folia `stageTrackPillTimeoutSec` 默认值。 */
const val NOW_PLAYING_CARD_TIMEOUT_MS: Long = 10_000L

/** Folia `NEXT_UP_LEAD_SEC`。 */
const val NEXT_UP_LEAD_MS: Long = 5_000L

const val NowPlayingCardTag = "now-playing-card"

/**
 * 自然播完会接上的那一首在队列里的下标；推不出就 null（Folia resolveNextUpTrack 的移植）。
 *
 * - 单曲循环（repeatMode=1 是 Media3 的 REPEAT_MODE_ONE）原地重播，没有下一首；
 * - 随机：Media3 的随机次序不在队列快照里，猜就会报错，按 Folia「宁可 null」不预告；
 * - 列表循环（REPEAT_MODE_ALL=2）在队尾接回 0；不循环在队尾就是 null。
 */
fun nextUpIndex(queueSize: Int, index: Int, shuffle: Boolean, repeatMode: Int): Int? {
    if (queueSize <= 0 || index !in 0 until queueSize) return null
    if (shuffle || repeatMode == REPEAT_ONE) return null
    val next = index + 1
    return when {
        next < queueSize -> next
        repeatMode == REPEAT_ALL -> 0
        else -> null
    }
}

/** 剩余时间进了预告窗口（Folia App.tsx countdownActive）。 */
fun nextUpCountdown(positionMs: Long, durationMs: Long, leadMs: Long = NEXT_UP_LEAD_MS): Boolean {
    if (durationMs <= 0L) return false
    val left = durationMs - positionMs
    return left in 1..leadMs
}

private const val REPEAT_ONE = 1
private const val REPEAT_ALL = 2

@Composable
fun NowPlayingCard(
    song: NowPlayingCardSong?,
    trackKey: String?,
    nextUp: NowPlayingCardSong?,
    isNextUp: Boolean,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    timeoutMs: Long = NOW_PLAYING_CARD_TIMEOUT_MS,
    /** 截图用：一直显示。 */
    holdOpen: Boolean = false,
) {
    if (song == null) return
    val previewing = isNextUp && nextUp != null
    var visible by remember { mutableStateOf(true) }
    val hold = holdOpen || previewing
    LaunchedEffect(trackKey, hold, timeoutMs) {
        visible = true
        if (hold) return@LaunchedEffect
        delay(timeoutMs)
        visible = false
    }
    val shown = if (previewing) nextUp!! else song
    val label = if (previewing) "接下来播放" else "正在播放"
    val ink = if (darkMode) Color.White else Color(0xFF18181B)
    val conf = LocalConfiguration.current
    // Folia `max-w-[calc(100vw-48px)]`；sonnet 横屏 toast 档 min(72vw, 28rem)。
    val maxW = minOf(conf.screenWidthDp - 48, if (conf.screenWidthDp > conf.screenHeightDp) 360 else 320).dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInHorizontally(tween(350)) { -it / 6 },
        exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 10 },
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .testTag(NowPlayingCardTag)
                .widthIn(min = minOf(240, conf.screenWidthDp - 48).dp, max = maxW)
                .clip(shape)
                .background(if (darkMode) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f))
                .border(1.dp, ink.copy(alpha = 0.10f), shape)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverImage(
                url = shown.coverUrl,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                accent = if (darkMode) Color(0xFF3F3F46) else Color(0xFFD4D4D8),
            )
            AnimatedContent(
                targetState = Triple(label, shown.title, shown.artist),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "nowPlayingCard",
            ) { (l, t, a) ->
                Column {
                    Text(
                        l,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = ink.copy(alpha = 0.50f),
                        maxLines = 1,
                    )
                    Text(
                        t,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        a.ifBlank { "未知歌手" },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink.copy(alpha = if (darkMode) 0.55f else 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
