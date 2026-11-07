package com.guangyue.feature.player.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.player.lyrics.CoverImage

/**
 * P1b：播放舞台上的浮卡面板（Folia `UnifiedPanel` + `panelTab/shell/PanelShell.tsx`）。
 *
 * Folia 没有独立的大封面播放页：播放主视图是歌词舞台，右下角圆形面板键打开一张玻璃浮卡，
 * 卡内自上而下是「歌曲头（封面页签时为大封面 + 信息 + 操作行，其它页签折成 48dp 一行）」
 * 「当前页签内容（超高时在卡内滚动）」「底部页签条」。这里按同样结构移植：
 *  - 卡宽 360dp（不超过屏宽 - 32dp），圆角 20、内边距 12，右下角对齐面板键，
 *    底边在面板键之上 8dp（Folia 圆键之上 60px 的手机版）；顶部至少留 56dp。
 *  - 玻璃色取 `panelTheme.ts` 的 `--pn-glass`，Compose 没有背景模糊，透明度从 0.8 提到 0.94 保证可读。
 *  - 打开 / 关闭：缩放 0.92 + 淡入，变换原点在面板键中心（Folia `transformOrigin: calc(100% - 24px) …`），
 *    时长取 kit `unfold 0.3s` / `move 0.22s`。
 *  - 页签：封面 / 控制 / 播放列表 / 音质（Folia zh-CN `panel.*`；「账号」页签在 Folia 里叫「音质」）。
 *  - 点卡外任意处或返回键关闭（返回键由外壳接）。
 */
enum class StagePanelTab(val label: String) {
    COVER("封面"),
    LYRICS("歌词"),
    CONTROLS("控制"),
    QUEUE("播放列表"),
    QUALITY("音质"),
}

/** 浮卡的配色：Folia `panelTheme.ts` + kit `settingsTokens`（t1/t2/t3/line/fill/raise）。 */
internal data class StagePanelColors(
    val glass: Color,
    val outline: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val line: Color,
    val fill: Color,
    val raise: Color,
    val like: Color,
)

internal fun stagePanelColors(dark: Boolean): StagePanelColors {
    val t1 = if (dark) Color(0xFFF4F4F5) else Color(0xFF18181B)
    return StagePanelColors(
        glass = if (dark) Color(0xF01C1C1F) else Color(0xF0F4F4F5),
        outline = if (dark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f),
        t1 = t1,
        t2 = t1.copy(alpha = 0.62f),
        t3 = t1.copy(alpha = 0.42f),
        line = t1.copy(alpha = 0.08f),
        fill = t1.copy(alpha = 0.07f),
        raise = if (dark) Color.White.copy(alpha = 0.18f) else Color.White,
        like = if (dark) Color(0xFFF2607A) else Color(0xFFE0354F),
    )
}

/** 模式 → 播放器目标开关（与 [nextPlayerModeTarget] 同一套映射，面板里是直接点选而非循环）。 */
fun playerModeTarget(mode: PlayerMode): PlayerModeTarget = when (mode) {
    PlayerMode.Sequential -> PlayerModeTarget(shuffle = false, repeatMode = 0)
    PlayerMode.LoopOne -> PlayerModeTarget(shuffle = false, repeatMode = 2)
    PlayerMode.Shuffle -> PlayerModeTarget(shuffle = true, repeatMode = 0)
}

/** 卡宽：Folia `w-[360px] max-w-[calc(100vw-2rem)]`。 */
fun stagePanelWidth(screenWidth: Dp): Dp = min(STAGE_PANEL_WIDTH, screenWidth - 32.dp)

val STAGE_PANEL_WIDTH: Dp = 360.dp

/**
 * 面板键（舞台胶囊行右侧 56dp 圆键）顶边到内容区底边的距离：胶囊行上下各 12dp + 56dp 高。
 * 浮卡底边再空 8dp。
 */
val STAGE_PANEL_BOTTOM: Dp = 12.dp + 56.dp + 8.dp

const val StagePanelTag = "p1b-stage-panel"
const val StagePanelScrimTag = "p1b-stage-panel-scrim"

@Composable
fun StagePanel(
    open: Boolean,
    tab: StagePanelTab,
    onTab: (StagePanelTab) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
    accent: Color,
    title: String,
    artist: String,
    album: String?,
    coverUrl: String?,
    like: LikeState,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    mode: PlayerMode,
    onPickMode: (PlayerMode) -> Unit,
    lyricStyleName: String,
    onOpenLyricStyle: () -> Unit,
    queue: List<PlayerQueueRow>,
    onPickQueueRow: (Int) -> Unit,
    onRemoveQueueRow: (Int) -> Unit,
    qualities: List<Quality>,
    currentQuality: Quality?,
    qualityDeclared: Boolean,
    onPickQuality: (Quality) -> Unit,
    modifier: Modifier = Modifier,
    /** P1c：歌词页签——时间轴偏移（ms）。 */
    lyricOffsetMs: Long = 0L,
    onLyricOffset: (Long) -> Unit = {},
    /** 歌词来源说明（音源名 / 无歌词）。 */
    lyricSourceNote: String = "",
    /** 截图用例直接给 true，跳过入场动画。 */
    skipEnter: Boolean = false,
) {
    val colors = stagePanelColors(dark)
    val visible = remember { MutableTransitionState(skipEnter && open) }
    LaunchedEffect(open) { visible.targetState = open }

    if (open) {
        // 卡外一层透明接点：点空白处收起（Folia 点播放页其它地方会收起面板）。
        Box(
            Modifier
                .fillMaxSize()
                .testTag(StagePanelScrimTag)
                .clickableNoRipple(onDismiss),
        )
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout).union(WindowInsets.navigationBars),
            ),
    ) {
        val width = stagePanelWidth(maxWidth)
        val maxCardHeight = (maxHeight - STAGE_PANEL_BOTTOM - TOP_MARGIN).coerceAtLeast(160.dp)
        // 变换原点 = 面板键中心：键在卡右缘往里 16 - 16 = 0（卡与键同在 16dp 页边距内），
        // 键心在卡底之下 8 + 28 = 36dp。比例按卡宽、近似卡高（取最大高的一半作基准即可，差几 dp 不可见）。
        val originX = 1f - (28.dp / width)
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(FoliaMotion.unfold()) + scaleIn(
                FoliaMotion.unfold(),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(originX, 1.1f),
            ),
            exit = fadeOut(FoliaMotion.move()) + scaleOut(
                FoliaMotion.move(),
                targetScale = 0.92f,
                transformOrigin = TransformOrigin(originX, 1.1f),
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = STAGE_PANEL_BOTTOM),
        ) {
            Column(
                Modifier
                    .width(width)
                    .heightIn(max = maxCardHeight)
                    .shadow(
                        elevation = 30.dp,
                        shape = CARD_SHAPE,
                        ambientColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.24f),
                        spotColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.24f),
                    )
                    .clip(CARD_SHAPE)
                    .background(colors.glass)
                    .border(1.dp, colors.outline, CARD_SHAPE)
                    // 卡内的点击不能漏到卡外的收起接点上。
                    .clickableNoRipple {}
                    .padding(PADDING)
                    .testTag(StagePanelTag),
            ) {
                val isCover = tab == StagePanelTab.COVER
                // 封面页签：卡高可给大封面的空间 = 卡最大高 - 内边距 - 页签条 - 封面下信息区（约 120dp）。
                val coverRoom = maxCardHeight - PADDING * 2 - DOCK_GAP - DOCK_HEIGHT - COVER_UNDER
                SongHeader(
                    big = isCover,
                    colors = colors,
                    accent = accent,
                    title = title,
                    artist = artist,
                    album = album,
                    coverUrl = coverUrl,
                    coverMax = coverRoom.coerceAtLeast(MIN_COVER),
                    like = like,
                    onToggleLike = onToggleLike,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenLyricStyle = onOpenLyricStyle,
                    onShowCover = { onTab(StagePanelTab.COVER) },
                )
                if (!isCover) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(FoliaMotion.move()) togetherWith fadeOut(FoliaMotion.fast()) },
                            label = "stagePanelTab",
                        ) { shown ->
                            when (shown) {
                                StagePanelTab.CONTROLS -> ControlsBody(
                                    colors = colors,
                                    accent = accent,
                                    mode = mode,
                                    onPickMode = onPickMode,
                                    lyricStyleName = lyricStyleName,
                                    onOpenLyricStyle = onOpenLyricStyle,
                                )
                                StagePanelTab.QUEUE -> QueueBody(
                                    colors = colors,
                                    accent = accent,
                                    queue = queue,
                                    onPick = onPickQueueRow,
                                    onRemove = onRemoveQueueRow,
                                )
                                StagePanelTab.QUALITY -> QualityBody(
                                    colors = colors,
                                    accent = accent,
                                    qualities = qualities,
                                    current = currentQuality,
                                    declared = qualityDeclared,
                                    onPick = onPickQuality,
                                )
                                StagePanelTab.LYRICS -> LyricsBody(
                                    colors = colors,
                                    offsetMs = lyricOffsetMs,
                                    onOffset = onLyricOffset,
                                    sourceNote = lyricSourceNote,
                                )
                                StagePanelTab.COVER -> Spacer(Modifier.height(0.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(DOCK_GAP))
                TabDock(colors = colors, current = tab, onTab = onTab)
            }
        }
    }
}

// ---------------------------------------------------------------- 歌曲头

@Composable
private fun SongHeader(
    big: Boolean,
    colors: StagePanelColors,
    accent: Color,
    title: String,
    artist: String,
    album: String?,
    coverUrl: String?,
    coverMax: Dp,
    like: LikeState,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenLyricStyle: () -> Unit,
    onShowCover: () -> Unit,
) {
    if (big) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth, coverMax)
                CoverImage(
                    url = coverUrl,
                    accent = accent,
                    contentDescription = title,
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.height(COVER_GAP))
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.t1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.isNotBlank()) {
                    Text(artist, fontSize = 13.sp, lineHeight = 18.sp, color = colors.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!album.isNullOrBlank()) {
                    Text(album, fontSize = 12.sp, lineHeight = 17.sp, color = colors.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                // Folia SongActions：喜欢 / 加入歌单 / 歌词样式，一排 36dp 圆角方键。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LikeKey(like = like, colors = colors, onClick = onToggleLike, big = true)
                    PanelKey(GuangYueIcons.ListPlus, "加入歌单", colors, onAddToPlaylist)
                    PanelKey(GuangYueIcons.Sparkles, "歌词样式", colors, onOpenLyricStyle)
                }
            }
        }
    } else {
        // 折叠：48dp 一行，小封面 + 歌名/歌手 + 心形；点这一行回封面页签。
        Row(
            Modifier
                .fillMaxWidth()
                .height(FOLDED)
                .clickableNoRipple(onShowCover),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = coverUrl,
                accent = accent,
                contentDescription = title,
                modifier = Modifier
                    .size(FOLDED)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, fontSize = 12.sp, lineHeight = 18.sp, color = colors.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LikeKey(like = like, colors = colors, onClick = onToggleLike, big = false)
        }
    }
}

@Composable
private fun LikeKey(like: LikeState, colors: StagePanelColors, onClick: () -> Unit, big: Boolean) {
    val liked = like == LikeState.Liked
    val tint by animateColorAsState(if (liked) colors.like else colors.t2, FoliaMotion.fast(), label = "likeTint")
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (big) colors.fill else Color.Transparent)
            .clickableNoRipple { if (like != LikeState.Pending) onClick() }
            .semantics { contentDescription = if (liked) "已喜欢" else "喜欢" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(GuangYueIcons.Heart, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PanelKey(icon: ImageVector, label: String, colors: StagePanelColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.fill)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = colors.t2, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- 页签条

@Composable
private fun TabDock(colors: StagePanelColors, current: StagePanelTab, onTab: (StagePanelTab) -> Unit) {
    Segments(
        colors = colors,
        options = StagePanelTab.entries.map { it to it.label },
        current = current,
        onPick = onTab,
        height = DOCK_HEIGHT,
    )
}

/** Folia `panelTab/shell/Segments.tsx`：底 fill、内边距 2，选中项垫 raise 色小圆角块并滑到新位置。 */
@Composable
private fun <T> Segments(
    colors: StagePanelColors,
    options: List<Pair<T, String>>,
    current: T,
    onPick: (T) -> Unit,
    height: Dp,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.fill)
            .padding(2.dp),
    ) {
        val cell = maxWidth / options.size
        val index = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        val markX by animateDpAsState(cell * index, FoliaMotion.move(), label = "segMark")
        Box(
            Modifier
                .offset(x = markX)
                .width(cell)
                .fillMaxHeight()
                .shadow(1.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(colors.raise),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                val selected = value == current
                val ink by animateColorAsState(if (selected) colors.t1 else colors.t2, FoliaMotion.fast(), label = "segInk")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickableNoRipple { if (!selected) onPick(value) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, color = ink, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 控制

@Composable
private fun ControlsBody(
    colors: StagePanelColors,
    accent: Color,
    mode: PlayerMode,
    onPickMode: (PlayerMode) -> Unit,
    lyricStyleName: String,
    onOpenLyricStyle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        SectionLabel("播放模式", colors)
        Segments(
            colors = colors,
            options = listOf(
                PlayerMode.Sequential to "顺序播放",
                PlayerMode.LoopOne to "单曲循环",
                PlayerMode.Shuffle to "随机播放",
            ),
            current = mode,
            onPick = onPickMode,
            height = 32.dp,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Spacer(Modifier.height(8.dp))
        // Folia AppearanceSection 的「歌词样式」行：左名右值，点开样式窗口。
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickableNoRipple(onOpenLyricStyle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GuangYueIcons.Sparkles, null, tint = colors.t2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("歌词样式", fontSize = 14.sp, color = colors.t1, modifier = Modifier.weight(1f))
            Text(lyricStyleName, fontSize = 13.sp, color = colors.t2, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(GuangYueIcons.ChevronRight, null, tint = colors.t3, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionLabel(text: String, colors: StagePanelColors) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = colors.t3,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
    )
}

// ---------------------------------------------------------------- 歌词

/** Folia SourceTab 的「时间轴偏移」行（LyricTimelineOffsetControl：步长 250ms，可重置）。 */
@Composable
private fun LyricsBody(colors: StagePanelColors, offsetMs: Long, onOffset: (Long) -> Unit, sourceNote: String) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        if (sourceNote.isNotBlank()) {
            SectionLabel("歌词来源 · $sourceNote", colors)
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("时间轴偏移", fontSize = 14.sp, color = colors.t1, modifier = Modifier.weight(1f))
            StepKey("−", "提前 250 毫秒", colors) { onOffset(offsetMs - OFFSET_STEP) }
            Text(
                (if (offsetMs > 0) "+" else "") + "$offsetMs ms",
                fontSize = 13.sp,
                color = if (offsetMs == 0L) colors.t2 else colors.t1,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(76.dp),
            )
            StepKey("+", "推后 250 毫秒", colors) { onOffset(offsetMs + OFFSET_STEP) }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoRipple { if (offsetMs != 0L) onOffset(0L) }
                    .semantics { contentDescription = "重置时间轴偏移" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GuangYueIcons.RotateCcw, null, tint = if (offsetMs == 0L) colors.t3 else colors.t2, modifier = Modifier.size(15.dp))
            }
        }
        Text(
            "正数让歌词更晚出现。每首歌各记一份。",
            fontSize = 12.sp,
            color = colors.t3,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun StepKey(text: String, label: String, colors: StagePanelColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.fill)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, color = colors.t1)
    }
}

private const val OFFSET_STEP = 250L

// ---------------------------------------------------------------- 播放列表

@Composable
private fun QueueBody(
    colors: StagePanelColors,
    accent: Color,
    queue: List<PlayerQueueRow>,
    onPick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        SectionLabel("播放列表 · ${queue.size} 首", colors)
        if (queue.isEmpty()) {
            Text(
                "播放列表是空的",
                fontSize = 13.sp,
                color = colors.t3,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
            return@Column
        }
        queue.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (row.current) colors.fill else Color.Transparent)
                    .clickableNoRipple { onPick(row.index) }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                    if (row.current) {
                        Icon(GuangYueIcons.AudioLines, null, tint = accent, modifier = Modifier.size(14.dp))
                    } else {
                        Text("${row.index + 1}", fontSize = 11.sp, color = colors.t3)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (row.current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (row.current) accent else colors.t1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.subtitle.isNotBlank()) {
                        Text(row.subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = colors.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!row.current) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickableNoRipple { onRemove(row.index) }
                            .semantics { contentDescription = "移出队列" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(GuangYueIcons.X, null, tint = colors.t3, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- 音质

@Composable
private fun QualityBody(
    colors: StagePanelColors,
    accent: Color,
    qualities: List<Quality>,
    current: Quality?,
    declared: Boolean,
    onPick: (Quality) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        SectionLabel(if (declared) "播放音质 · 由当前音源提供" else "播放音质 · 音源未声明，显示全部档位", colors)
        qualities.forEach { q ->
            val selected = q == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.fill else Color.Transparent)
                    .clickableNoRipple { if (!selected) onPick(q) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            q.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.t1,
                        )
                        if (q.isLossless) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "HD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier
                                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(q.subtitle, fontSize = 11.sp, color = colors.t2, maxLines = 1)
                }
                if (selected) {
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(GuangYueIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val PADDING = 12.dp
private val DOCK_HEIGHT = 36.dp
private val DOCK_GAP = 12.dp
private val TOP_MARGIN = 56.dp
private val FOLDED = 48.dp
private val COVER_GAP = 14.dp
private val MIN_COVER = 96.dp
/** 大封面下方：歌名两行 + 歌手 + 专辑 + 操作行的估计高度。 */
private val COVER_UNDER = 14.dp + 44.dp + 18.dp + 17.dp + 10.dp + 36.dp
