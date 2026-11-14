package com.guangyue.feature.library.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.glassPanel
import com.guangyue.feature.library.design.icons.GuangYueIcons

/**
 * 首页墙的一块卡片（歌单 / 专辑 / 单曲 / 榜单 / 推荐歌单）。
 *
 * U6 起首页墙的 UI 放在 app 模块的 `com.guangyue.music.ui.home.HomeWallScreen`（因为「封面卡用
 * U1 设计地基组件」= app 模块 `ui/design` 里的 `FoliaCoverCard`，feature 模块引用不到 app），
 * 这里只保留数据形状，供 HomeViewModel 与 app 层共用。
 */
data class HomeTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: String? = null,
    val kind: HomeTileKind = HomeTileKind.PLAYLIST,
)

/** 卡片类型：点进去之后同一个 `onOpenTile`，但取歌单/榜单/推荐歌单歌单里曲目的方式不同。 */
enum class HomeTileKind { PLAYLIST, ALBUM, TRACK, STATION, SHEET }

/**
 * 浮动播放胶囊（P2：Folia `FloatingPlayerControls` 展开态，基准 screenshots-v2/m412-*-mini-bar）。
 *
 * 三行网格（grid-cols-[1fr_auto_1fr]）：第一行居中歌名 14/700（点它进播放舞台）；第二行左右两个槽位
 * （Folia 默认是循环模式 + 歌词，手机上左 = 播放模式、右 = 下一首）夹着 48dp 实心圆播放键（底 = 主字色、
 * 图标 = 底色）；第三行「时间 · 2dp 进度条 · 时长」。外壳 rounded-full + p-3 + 玻璃（浅 white/60、深 black/40）+ shadow-2xl。
 * 位置仍由 `ShellColumn` 管：底栏之上、内容之下。
 */
@Composable
fun FoliaMiniPlayer(
    title: String,
    subtitle: String,
    artwork: String?,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    playLabel: String = "播放",
    pauseLabel: String = "暂停",
    nextLabel: String = "下一首",
    /** 有时长时第三行显示「当前 / 总长」。 */
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    /** 左槽：播放模式（图标 + 点按循环）；null 时左槽空。 */
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modeLabel: String = "",
    onCycleMode: (() -> Unit)? = null,
) {
    if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < MINI_COMPACT_SCREEN_DP) {
        FoliaMiniPlayerCompact(title, progress, isPlaying, onTogglePlay, onOpen, modifier, onNext, playLabel, pauseLabel, nextLabel, positionMs, durationMs, modeIcon, modeLabel, onCycleMode)
        return
    }
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.m, vertical = GySpace.xs)
            .androidxShadow(shape)
            .clip(shape)
            .glassPanel(corner = 30.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (modeIcon != null && onCycleMode != null) {
                    SlotKey(modeIcon, modeLabel, onCycleMode)
                }
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gyp.ink)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                    contentDescription = if (isPlaying) pauseLabel else playLabel,
                    tint = Gyp.page,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onNext != null) SlotKey(GuangYueIcons.SkipForward, nextLabel, onNext)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(miniTime(positionMs), fontSize = 11.sp, color = Gyp.ink.copy(alpha = 0.6f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            ProgressLine(progress = progress, modifier = Modifier.weight(1f), fill = Gyp.ink)
            Spacer(Modifier.width(8.dp))
            Text(miniTime(durationMs), fontSize = 11.sp, color = Gyp.ink.copy(alpha = 0.6f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

/** 屏高低于此值（dp）时胶囊用横屏紧凑形态（360dp 宽机横屏约 336dp 高）。 */
const val MINI_COMPACT_SCREEN_DP = 480

/** 横屏胶囊宽度上限：folia-sonnet androidPhoneFit 横屏 `width: min(78vw, 26rem)`（26rem = 416px）。 */
private const val MINI_COMPACT_MAX_WIDTH_DP = 416

/**
 * 横屏紧凑胶囊（2026-11-14，参照 folia-sonnet androidPhoneFit.css 横屏档）：手机横屏又宽又矮，竖屏那套三行胶囊
 * 占去约四成屏高、还横贯整屏。改成居中、宽度封顶 min(78%, 416dp) 的两行：
 * 第一行「模式槽 · 歌名（12→14sp，点进播放舞台）· 播放键 · 下一首」，第二行「时间 · 进度线 · 时长」；行距收到 0。
 * 播放键视觉 40dp（Folia 横屏 34px 再放大到广乐触控规范），触控区仍 48dp。
 */
@Composable
private fun FoliaMiniPlayerCompact(
    title: String,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    onNext: (() -> Unit)?,
    playLabel: String,
    pauseLabel: String,
    nextLabel: String,
    positionMs: Long,
    durationMs: Long,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    modeLabel: String,
    onCycleMode: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(28.dp)
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val maxW = minOf(screenW * 0.78f, MINI_COMPACT_MAX_WIDTH_DP.toFloat()).dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = maxW)
                .fillMaxWidth()
                .padding(vertical = GySpace.xs)
                .androidxShadow(shape)
                .clip(shape)
                .glassPanel(corner = 28.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (modeIcon != null && onCycleMode != null) SlotKey(modeIcon, modeLabel, onCycleMode, touch = 48.dp)
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gyp.ink,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 6.dp),
                )
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Gyp.ink), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                            contentDescription = if (isPlaying) pauseLabel else playLabel,
                            tint = Gyp.page,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                if (onNext != null) SlotKey(GuangYueIcons.SkipForward, nextLabel, onNext, touch = 48.dp)
            }
            Row(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(miniTime(positionMs), fontSize = 11.sp, color = Gyp.ink.copy(alpha = 0.6f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                ProgressLine(progress = progress, modifier = Modifier.weight(1f), fill = Gyp.ink)
                Spacer(Modifier.width(8.dp))
                Text(miniTime(durationMs), fontSize = 11.sp, color = Gyp.ink.copy(alpha = 0.6f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SlotKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, touch: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        Modifier.size(touch).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Gyp.ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}

private fun Modifier.androidxShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.shadow(elevation = 18.dp, shape = shape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)))

private fun miniTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
}

/** 一条 2dp 的进度线（Folia 进度条高 2px，圆角 4px，底 `--st-track`、前景强调色）。 */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, fill: androidx.compose.ui.graphics.Color = Gyp.accent) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(CircleShape)
            .background(Gyp.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill),
        )
    }
}