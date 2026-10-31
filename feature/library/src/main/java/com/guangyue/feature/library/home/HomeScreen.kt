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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * 迷你播放条（0.0.9(U2) 起的外壳组件，U6 沿用）。
 *
 * 56dp 胶囊 + 38dp 封面 + 36dp 播放键 + 48dp「下一首」；位置由 `ShellColumn` 管：在底栏之上、
 * 内容之下，手势条 inset 由底栏自吃，所以这里不需要再吃 inset。
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.m, vertical = GySpace.xs)
            .height(56.dp)
            .clip(RoundedCornerShape(GyRadius.capsule))
            .glassPanel(corner = GyRadius.capsule)
            .clickable(onClick = onOpen)
            .padding(horizontal = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(GyRadius.field))
                .background(Gyp.fill2),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                AsyncImage(model = artwork, contentDescription = title, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    imageVector = GuangYueIcons.Music,
                    contentDescription = null,
                    tint = Gyp.t3,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(GySpace.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = GyText.label,
                lineHeight = GyText.labelHeight,
                fontWeight = FontWeight.Medium,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(GySpace.xxs))
            ProgressLine(progress = progress)
        }
        Spacer(Modifier.width(GySpace.s))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Gyp.accent)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                contentDescription = if (isPlaying) pauseLabel else playLabel,
                tint = Gyp.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        // 0.0.9（U2）：「下一首」——Folia 桌面悬浮条上没有这一颗（桌面靠 hover 出的
        // 悬浮控件 / 快捷键），手机版按需求补一颗 48dp 触控的图标按钮。
        if (onNext != null) {
            Spacer(Modifier.width(GySpace.xs))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuangYueIcons.SkipForward,
                    contentDescription = nextLabel,
                    tint = Gyp.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 一条 2dp 的进度线（Folia 进度条高 2px，圆角 4px，底 `--st-track`、前景强调色）。 */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
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
                .background(Gyp.accent),
        )
    }
}