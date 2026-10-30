package com.guangyue.feature.library.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaEmptyState
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyMotion
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.glassPanel
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/** One tile in a home row (歌单 / 专辑 / 最近播放). */
data class HomeTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: String? = null,
    /** 四角标记：歌单/专辑/单曲，决定占位图形。 */
    val kind: HomeTileKind = HomeTileKind.PLAYLIST,
)

enum class HomeTileKind { PLAYLIST, ALBUM, TRACK, STATION, SHEET }

/**
 * 首页：只放真实数据。
 *
 * 0.0.8 起不再有「示例曲目 / 灰色占位」这种假数据：
 * - 没有音源 → 整页空态，只给一个「去添加音源」入口；
 * - 有音源但某一区块拿不到数据（插件不声明 getTopLists / 还没播放过）→ 该区块整个不显示，
 *   而不是塞一块占位。
 * - 推荐 / 榜单来自当前音源的 getTopLists + getTopListDetail；最近播放来自真实播放记录。
 */
@Composable
fun HomeScreen(
    recommended: List<HomeTile>,
    recent: List<HomeTile>,
    charts: List<HomeTile>,
    hasAnyPlugin: Boolean,
    onOpenTile: (HomeTile) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenPlayer: () -> Unit,
    playbackTitle: String,
    playbackArtwork: String?,
    playbackProgress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    miniPlayer: (@Composable () -> Unit)? = null,
    // 0.0.9（U2）：内容墙由 U6 做，这一版先给中文占位空态 + 去搜索入口。
    onOpenSearch: (() -> Unit)? = null,
    placeholderTitle: String = "还没有开始听",
    placeholderHint: String = "搜索一首歌，先听一点，最近播放会出现在这里。",
    placeholderAction: String = "去搜索",
) {
    Column(
        modifier
            .fillMaxSize()
            .marbleBackground(),
    ) {
        // 掉帧冒烟：DEBUG 下逐帧采样，logcat（tag GyJank）每 120 帧一行汇总；
        // release 里 GyJank.Enabled 是常量 false，整段统计被 R8 摘掉。
        com.guangyue.feature.library.perf.TrackScrollFrames()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            FoliaTopBar(
                title = "广乐",
                serif = true,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(GyRadius.fieldShape())
                            .clickable(onClick = onOpenPlayer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = GuangYueIcons.AudioLines,
                            contentDescription = "播放页",
                            tint = Gyp.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            if (!hasAnyPlugin && recommended.isEmpty() && recent.isEmpty() && charts.isEmpty()) {
                // 0.0.9（U2）：U6 才做首页内容墙，这一版先给中文占位空态。
                // 装了音源还是空（说明插件不给榜 / 什么都没播过）则退回「去添加音源」那条。
                val noSource = !hasAnyPlugin && onOpenSearch == null
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = GySpace.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    FoliaEmptyState(
                        title = if (noSource) "空空如也" else placeholderTitle,
                        subtitle = if (noSource) {
                            "还没有音源。装一个官方音源，搜索和榜单就有了。"
                        } else {
                            placeholderHint
                        },
                        action = {
                            Spacer(Modifier.height(GySpace.xs))
                            FoliaButton(
                                text = if (noSource) "去添加音源" else placeholderAction,
                                onClick = if (noSource) onOpenPlugins else (onOpenSearch ?: {}),
                                variant = FoliaButtonVariant.Primary,
                                icon = if (noSource) GuangYueIcons.Plus else GuangYueIcons.Search,
                            )
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 底栏与迷你条由外壳竖排在外，不覆盖内容；这里只留视觉呼吸。
                    contentPadding = PaddingValues(bottom = GySpace.xl),
                ) {
                    // 拿不到推荐就整段不显示（插件不声明 getRecommendSheetTags 时属于正常情况）。
                    if (recommended.isNotEmpty()) {
                        item(key = "recommend-title") {
                            FoliaSectionTitle("推荐歌单")
                        }
                        item(key = "recommend-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = GySpace.pageH),
                                horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                            ) {
                                items(recommended, key = { it.key }) { tile ->
                                    PosterTile(tile = tile, width = 152.dp, onClick = { android.util.Log.d("GYTAP", "poster tap " + tile.key + " :: " + recommended.joinToString(" | ") { it.key }); onOpenTile(tile) })
                                }
                            }
                        }
                    }

                    // 没播放过就不显示「最近播放」这一段。
                    if (recent.isNotEmpty()) {
                        item(key = "recent-title") {
                            FoliaSectionTitle("最近播放")
                        }
                        item(key = "recent-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = GySpace.pageH),
                                horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                            ) {
                                items(recent, key = { it.key }) { tile ->
                                    SquareTile(tile = tile, size = 112.dp, onClick = { onOpenTile(tile) })
                                }
                            }
                        }
                    }

                    // 当前音源不提供 getTopLists 时没有榜单这一块，直接不给标题。
                    if (charts.isNotEmpty()) {
                        item(key = "charts-title") {
                            FoliaSectionTitle("热门榜单")
                        }
                        items(charts, key = { it.key }) { tile ->
                            ChartRow(tile = tile, onClick = { onOpenTile(tile) })
                        }
                    }
                }
            }
        }

        // 迷你播放条浮在底栏上方；底栏本身由 App 外壳传入（二级页与播放页不传）。
        miniPlayer?.invoke()
        bottomBar?.invoke()
    }
}

/** 一段的空状态：一块 4:3 的占位图 + 一行说明（+ 可选引导按钮），不写「暂无内容」这种死文案。 */
@Composable
private fun EmptyStrip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val hairline = Gyp.hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 88.dp)
                .clip(RoundedCornerShape(GyRadius.row))
                .background(Gyp.tint)
                .drawBehind {
                    // Folia 的空态是极简的：只有一层描边和居中的图标，没有插画。
                    drawRect(
                        color = hairline,
                        topLeft = Offset.Zero,
                        size = Size(size.width, 1f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gyp.t3,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(GySpace.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = GyText.row,
                lineHeight = GyText.rowHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(GySpace.xs))
                FoliaButton(
                    text = action,
                    onClick = onAction,
                    variant = FoliaButtonVariant.Fill,
                )
            }
        }
    }
}

/**
 * 海报卡：Folia `Lattice` 的拍立得质感 —— 圆角 4dp、内侧描边 `--st-line`、
 * 封面按 3:4，标题 13/18、副标题 12/18 色 `--st-t2`。
 */
@Composable
private fun PosterTile(tile: HomeTile, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(GyRadius.field))
            .clickable(onClick = onClick),
    ) {
        ArtworkFrame(
            artwork = tile.artwork,
            title = tile.title,
            kind = tile.kind,
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 1.32f),
        )
        Spacer(Modifier.height(GySpace.s))
        Text(
            text = tile.title,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            fontWeight = FontWeight.Medium,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = tile.subtitle,
            fontSize = GyText.hint,
            lineHeight = GyText.hintHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 方图卡（最近播放/专辑）：封面 1:1、圆角 8dp，标题在下方两行内。 */
@Composable
private fun SquareTile(tile: HomeTile, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(size)
            .clip(RoundedCornerShape(GyRadius.field))
            .clickable(onClick = onClick),
    ) {
        ArtworkFrame(
            artwork = tile.artwork,
            title = tile.title,
            kind = tile.kind,
            modifier = Modifier.size(size),
        )
        Spacer(Modifier.height(GySpace.xs))
        Text(
            text = tile.title,
            fontSize = GyText.hint,
            lineHeight = GyText.hintHeight,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 榜单行：左侧 56dp 封面，右侧标题 + 说明，最右一个 chevron。 */
@Composable
private fun ChartRow(tile: HomeTile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = GySpace.pageH, vertical = GySpace.xs)
            .height(GySpace.rowMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkFrame(
            artwork = tile.artwork,
            title = tile.title,
            kind = tile.kind,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(GySpace.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = tile.title,
                fontSize = GyText.row,
                lineHeight = GyText.rowHeight,
                fontWeight = FontWeight.Medium,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.subtitle,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = GuangYueIcons.ChevronRight,
            contentDescription = null,
            tint = Gyp.t3,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 封面框：有图就裁切显示；没图就画 Folia 的占位——底色 `--st-fill` 加一个居中图标，
 * 歌单类再加顶部两条「卡片条」（`Lattice.css` 里 stack 的 76%/88% 两条 5px 高）。
 */
@Composable
private fun ArtworkFrame(
    artwork: String?,
    title: String,
    kind: HomeTileKind,
    modifier: Modifier = Modifier,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector = GuangYueIcons.Music,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GyRadius.field))
            .background(Gyp.fill2),
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val icon = when (kind) {
                HomeTileKind.PLAYLIST -> GuangYueIcons.ListMusic
                HomeTileKind.ALBUM -> GuangYueIcons.Disc3
                HomeTileKind.TRACK -> GuangYueIcons.Music
                HomeTileKind.STATION -> GuangYueIcons.Radio
                // 推荐歌单（音源给的官方歌单）无封面时用列表图标兜底。
                HomeTileKind.SHEET -> GuangYueIcons.ListMusic
            }
            if (kind == HomeTileKind.PLAYLIST || kind == HomeTileKind.ALBUM) {
                PlaylistStack()
            }
            Icon(
                imageVector = if (kind == HomeTileKind.PLAYLIST || kind == HomeTileKind.ALBUM) icon else fallbackIcon,
                contentDescription = null,
                tint = Gyp.t3,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }
    }
}

/** Folia 歌单占位：顶部两条错位卡片条（5px 高，宽 76% / 88%）。 */
@Composable
private fun PlaylistStack() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth(0.76f)
                .height(5.dp)
                .align(Alignment.TopCenter)
                .background(Gyp.t3.copy(alpha = 0.34f)),
        )
        Box(
            Modifier
                .fillMaxWidth(0.88f)
                .height(5.dp)
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .background(Gyp.t3.copy(alpha = 0.58f)),
        )
    }
}

/**
 * 迷你播放条：Folia 的悬浮胶囊（`LatticeChrome.css` 移动端：高 86dp 太厚，这里按手机
 * 习惯取 56dp；圆角走 pill、底为磨砂玻璃、进度线 2dp、时间 12sp 等宽）。
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

/** Folia 顶栏在深色下需要一点分隔：1dp 发丝线。 */
@Composable
internal fun TopHairline(modifier: Modifier = Modifier) {
    val color = Gyp.hairline
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(color) },
    )
}

internal val FoliaMotionEase = GyMotion.easeOut
