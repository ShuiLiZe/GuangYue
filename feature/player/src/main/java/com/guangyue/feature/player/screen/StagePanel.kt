package com.guangyue.feature.player.screen

import androidx.compose.foundation.relocation.bringIntoViewRequester

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
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
    /** 2026-11-08：下载当前歌曲（null = 不显示下载键）。点一下 = 按默认音质下载。 */
    onDownload: (() -> Unit)? = null,
    /** 2026-11-12：长按下载键弹出的档位（从高到低）；空 = 长按不弹。 */
    downloadQualities: List<Quality> = emptyList(),
    /** 弹层里标「默认」的那档（= 点一下实际先试的档）。 */
    downloadDefault: Quality? = null,
    /** 弹层里选了一档。null = 长按不弹。 */
    onDownloadQuality: ((Quality) -> Unit)? = null,
    /** 截图用例：直接打开弹层。 */
    initialQualityPick: Boolean = false,
    /** 11-18 Folia QueueTab 行操作「下一首播放」「移到队尾」；null = 不显示。 */
    onQueueRowNext: ((Int) -> Unit)? = null,
    onQueueRowEnd: ((Int) -> Unit)? = null,
    /** 11-18 Folia QueueTab 头行「保存为歌单」「打乱队列」；null = 不显示。 */
    onSaveQueue: (() -> Unit)? = null,
    onShuffleQueue: (() -> Unit)? = null,
    /** 11-18 Folia ControlsTab：音量（0–1，null = 不显示音量行）、循环三态（ExoPlayer repeatMode 0/1/2）、系统均衡器、歌词样式左右换。 */
    volume: Float? = null,
    onVolume: ((Float) -> Unit)? = null,
    repeatMode: Int? = null,
    onRepeatMode: ((Int) -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    onStepLyricStyle: ((Int) -> Unit)? = null,
    /** 取景器字框里画当前样式的字形（Folia entry.tsx glyph）；null 画通用三横。 */
    lyricStyle: com.guangyue.feature.player.lyrics.LyricStyle? = null,
    /** 11-18 Folia AppearanceSection 第二行「背景类型」取景器：当前背景 id / 名字；null 不画。 */
    backgroundId: String? = null,
    backgroundName: String = "",
    onStepBackground: ((Int) -> Unit)? = null,
    onOpenBackground: () -> Unit = {},
    /** 11-18 Folia AppearanceSection aside：歌词样式行右侧动画强度胶囊（平静 / 标准 / 混乱，点一下轮换）；null 不画。 */
    animationIntensity: com.guangyue.core.lyrics.AnimationIntensity? = null,
    onCycleIntensity: () -> Unit = {},
    /** 11-18 Folia classic renderQuickControls：流光「字词旋转」快捷开关（样式行下方参数行）；null 不画。 */
    classicWordRotation: Boolean? = null,
    onToggleWordRotation: () -> Unit = {},
    /** 11-18 Folia「随机」：这首歌抽到的样式名（样式行下方胶囊，点一下另抽）；null = 没开随机。 */
    randomStyleName: String? = null,
    onRerollStyle: () -> Unit = {},
    /** 11-18 Folia 随机池（选择随机池）：(样式, 名字, 在池里)；null = 不画。 */
    randomPool: List<Triple<com.guangyue.feature.player.lyrics.LyricStyle, String, Boolean>>? = null,
    onRandomPool: (List<com.guangyue.feature.player.lyrics.LyricStyle>) -> Unit = {},
) {
    val colors = stagePanelColors(dark)
    var qualityPick by remember { mutableStateOf(initialQualityPick) }
    val onDownloadLong: (() -> Unit)? =
        if (onDownload != null && onDownloadQuality != null && downloadQualities.isNotEmpty()) ({ qualityPick = true }) else null
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
        // 11-18 横屏（高 < 480dp）：面板是模态的（卡外有收起接点），上沿不必让出顶栏 56dp，只留 12dp——播放列表多露一行多。
        val topMargin = if (maxHeight < 480.dp) 12.dp else TOP_MARGIN
        val maxCardHeight = (maxHeight - STAGE_PANEL_BOTTOM - topMargin).coerceAtLeast(160.dp)
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
                    onDownload = onDownload,
                    onDownloadLong = onDownloadLong,
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
                                    volume = volume,
                                    onVolume = onVolume,
                                    repeatMode = repeatMode,
                                    onRepeatMode = onRepeatMode,
                                    onOpenEqualizer = onOpenEqualizer,
                                    onStepLyricStyle = onStepLyricStyle,
                                    lyricStyle = lyricStyle,
                                    backgroundId = backgroundId,
                                    backgroundName = backgroundName,
                                    onStepBackground = onStepBackground,
                                    onOpenBackground = onOpenBackground,
                                    animationIntensity = animationIntensity,
                                    onCycleIntensity = onCycleIntensity,
                                    classicWordRotation = classicWordRotation,
                                    onToggleWordRotation = onToggleWordRotation,
                                    randomStyleName = randomStyleName,
                                    onRerollStyle = onRerollStyle,
                                    randomPool = randomPool,
                                    onRandomPool = onRandomPool,
                                )
                                StagePanelTab.QUEUE -> QueueBody(
                                    colors = colors,
                                    accent = accent,
                                    queue = queue,
                                    onPick = onPickQueueRow,
                                    onRemove = onRemoveQueueRow,
                                    onNext = onQueueRowNext,
                                    onEnd = onQueueRowEnd,
                                    onSave = onSaveQueue,
                                    onShuffle = onShuffleQueue,
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

    if (qualityPick && onDownloadQuality != null) {
        DownloadQualitySheet(
            dark = dark,
            accent = accent,
            options = downloadQualities,
            defaultQuality = downloadDefault,
            onPick = { q -> qualityPick = false; onDownloadQuality(q) },
            onDismiss = { qualityPick = false },
        )
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
    onDownload: (() -> Unit)? = null,
    onDownloadLong: (() -> Unit)? = null,
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
                    if (onDownload != null) {
                        PanelKey(GuangYueIcons.Download, "下载", colors, onDownload, onDownloadLong)
                    }
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
            if (onDownload != null) {
                PanelKey(GuangYueIcons.Download, "下载", colors, onDownload, onDownloadLong)
                Spacer(Modifier.width(6.dp))
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
private fun PanelKey(
    icon: ImageVector,
    label: String,
    colors: StagePanelColors,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.fill)
            .then(if (onLongClick != null) Modifier.tapOrHold(onClick, onLongClick) else Modifier.clickableNoRipple(onClick))
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

/**
 * 11-18 照 Folia `panelTab/ControlsTab.tsx`：声音（音量行 + 三块）、分组线、外观（歌词样式取景器）。
 *  - 音量行（VolumeRow）：40dp 一行 = 静音键（Volume2/1/X 17）· 滑杆（4dp 轨、强调色填充、16dp 白圆钮）· 百分比 12sp 右对齐 38dp。
 *    拖动时直接改播放器音量（Folia 预听），松手不另存（广乐音量不持久化，每次启动 100%）。
 *  - 三块（SoundTiles / QuickTile）：最低 64dp、圆角 10、内边距 10/9，图标在上、名字在下 12sp；亮起 = raise 底 + 图标强调色。
 *    循环（名字就是当前状态：关闭循环 / 列表循环 / 单曲循环，点一下 关→列表→单曲）；✋ Folia 第二块是「智能过渡」，广乐没有，
 *    换成「随机播放」开关（Folia 随机在别处，广乐面板里原本就有这一项，不丢）；「音频效果器」打开系统均衡器（带箭头）。
 *  - 歌词样式取景器（ModeStepperRow）：‹ [48×30 字形框] 「歌词样式」11.5sp t3 / 当前名 14sp + ▾ ›；两侧 28dp 箭头直接换上一个 / 下一个样式，
 *    点中间开样式面板（✋ Folia 是就地展开小图网格，广乐沿用已有样式面板）。
 *  - 11-18 背景类型取景器（AppearanceSection 第二行）：同一行样式，‹ › 在 隐现 / 通用 间换，点中间开样式面板「背景」页。
 *    ✋ 播放页透明背景、主题来源 / 主题名两行广乐暂无对应。
 */
@Composable
private fun ControlsBody(
    colors: StagePanelColors,
    accent: Color,
    mode: PlayerMode,
    onPickMode: (PlayerMode) -> Unit,
    lyricStyleName: String,
    onOpenLyricStyle: () -> Unit,
    volume: Float?,
    onVolume: ((Float) -> Unit)?,
    repeatMode: Int?,
    onRepeatMode: ((Int) -> Unit)?,
    onOpenEqualizer: (() -> Unit)?,
    onStepLyricStyle: ((Int) -> Unit)?,
    lyricStyle: com.guangyue.feature.player.lyrics.LyricStyle? = null,
    backgroundId: String? = null,
    backgroundName: String = "",
    onStepBackground: ((Int) -> Unit)? = null,
    onOpenBackground: () -> Unit = {},
    animationIntensity: com.guangyue.core.lyrics.AnimationIntensity? = null,
    onCycleIntensity: () -> Unit = {},
    /** 11-18 Folia classic renderQuickControls：流光「字词旋转」快捷开关（样式行下方参数行）；null 不画。 */
    classicWordRotation: Boolean? = null,
    onToggleWordRotation: () -> Unit = {},
    /** 11-18 Folia「随机」：这首歌抽到的样式名（样式行下方胶囊，点一下另抽）；null = 没开随机。 */
    randomStyleName: String? = null,
    onRerollStyle: () -> Unit = {},
    /** 11-18 Folia 随机池（选择随机池）：(样式, 名字, 在池里)；null = 不画。 */
    randomPool: List<Triple<com.guangyue.feature.player.lyrics.LyricStyle, String, Boolean>>? = null,
    onRandomPool: (List<com.guangyue.feature.player.lyrics.LyricStyle>) -> Unit = {},
) {
    var poolOpen by remember { mutableStateOf(false) }
    if (randomPool == null) poolOpen = false
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        if (volume != null && onVolume != null) VolumeRow(colors, accent, volume, onVolume)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val repeat = repeatMode ?: if (mode == PlayerMode.LoopOne) 2 else 0
            QuickTile(
                colors, accent,
                icon = when (repeat) { 2 -> GuangYueIcons.Repeat1; 1 -> GuangYueIcons.Repeat; else -> GuangYueIcons.RepeatOff },
                label = when (repeat) { 2 -> "单曲循环"; 1 -> "列表循环"; else -> "关闭循环" },
                lit = repeat != 0, open = false, modifier = Modifier.weight(1f),
            ) {
                if (onRepeatMode != null) onRepeatMode((repeat + 1) % 3)
                else onPickMode(if (mode == PlayerMode.LoopOne) PlayerMode.Sequential else PlayerMode.LoopOne)
            }
            val shuffleOn = mode == PlayerMode.Shuffle
            QuickTile(colors, accent, GuangYueIcons.Shuffle, "随机播放", lit = shuffleOn, open = false, modifier = Modifier.weight(1f)) {
                onPickMode(if (shuffleOn) PlayerMode.Sequential else PlayerMode.Shuffle)
            }
            if (onOpenEqualizer != null) {
                QuickTile(colors, accent, GuangYueIcons.SlidersHorizontal, "音频效果器", lit = false, open = true, modifier = Modifier.weight(1f), onClick = onOpenEqualizer)
            }
        }
        // GroupLine：mx-1 my-2 h-px。
        Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(colors.line))
        StyleStepperRow(colors, lyricStyleName, onOpenLyricStyle, onStepLyricStyle, glyph = if (randomStyleName != null) ({ c: Color ->
            // Folia drawLyricStyleChoiceGlyph：「随机」项画 Shuffle 18。
            Icon(GuangYueIcons.Shuffle, null, tint = c, modifier = Modifier.size(18.dp))
        }) else lyricStyle?.let { st ->
            { c: Color -> com.guangyue.feature.player.lyrics.StyleGlyph(st, c, Modifier.size(18.dp)) }
        }, aside = animationIntensity?.let { ai -> { QuickChip(colors, intensityLabel(ai), "动画强度：" + intensityLabel(ai), onCycleIntensity) } },
            params = if (randomStyleName != null) ({
                // Folia：随机时参数行是「这首抽到的样式」胶囊，点一下另抽一种（randomPool.reroll）；
                // 旁边「选择随机池」胶囊开随机池（RandomPoolPicker）。
                QuickChip(colors, randomStyleName, "换一种样式：$randomStyleName", onRerollStyle)
                if (randomPool != null) {
                    QuickChip(colors, "选择随机池", "选择随机池：已选 ${randomPool.count { it.third }}/${randomPool.size}") { poolOpen = !poolOpen }
                }
            }) else if (lyricStyle == com.guangyue.feature.player.lyrics.LyricStyle.CLASSIC && classicWordRotation != null) ({
                QuickToggle(colors, accent, classicWordRotation, "字词旋转", GuangYueIcons.RotateCw, onToggleWordRotation)
            }) else null)
        if (poolOpen && randomPool != null) {
            com.guangyue.feature.player.lyrics.RandomPoolGrid(
                rows = randomPool,
                accent = accent,
                ink = colors.t1, t2 = colors.t2, t3 = colors.t3, line = colors.line, fill = colors.fill,
                onChange = onRandomPool,
            )
        }
        if (backgroundId != null) {
            StyleStepperRow(
                colors, backgroundName, onOpenBackground, onStepBackground,
                caption = "背景类型", stepNoun = "背景",
                glyph = { c -> com.guangyue.feature.player.lyrics.BackgroundGlyph(backgroundId, c, Modifier.size(18.dp)) },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Folia VolumeRow + PanelSlider。 */
@Composable
private fun VolumeRow(colors: StagePanelColors, accent: Color, volume: Float, onVolume: (Float) -> Unit) {
    var shown by remember { mutableStateOf(volume) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(volume) { if (!held) shown = volume }
    var beforeMute by remember { mutableStateOf(1f) }
    fun set(v: Float) { shown = v.coerceIn(0f, 1f); onVolume(shown) }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickableNoRipple {
                if (shown > 0f) { beforeMute = shown; set(0f) } else set(beforeMute.takeIf { it > 0f } ?: 1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when { shown <= 0f -> GuangYueIcons.VolumeX; shown < 0.5f -> GuangYueIcons.Volume1; else -> GuangYueIcons.Volume2 },
                contentDescription = "音量", tint = colors.t2, modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(32.dp)
                .androidxPointer(onPress = { held = true }, onRelease = { held = false }) { x, w -> set(((x - 8f) / (w - 16f)).coerceIn(0f, 1f)) },
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = maxWidth
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.fill.copy(alpha = colors.fill.alpha * 1.8f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(accent))
            }
            Box(
                Modifier
                    .offset(x = (track - 16.dp) * shown)
                    .size(16.dp)
                    .graphicsLayer { val s = if (held) 1.15f else 1f; scaleX = s; scaleY = s }
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Text(
            "${kotlin.math.round(shown * 100).toInt()}%",
            fontSize = 12.sp, color = colors.t2, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

/** 滑杆手势：按下即跳到该点（Folia：按在轨道上把圆钮中心带过去），拖动跟手。 */
private fun Modifier.androidxPointer(onPress: () -> Unit, onRelease: () -> Unit, onX: (Float, Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            onPress()
            onX(down.position.x, size.width.toFloat())
            down.consume()
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                onX(c.position.x, size.width.toFloat())
                c.consume()
            }
            onRelease()
        }
    }

/** Folia QuickTile。 */
@Composable
private fun QuickTile(
    colors: StagePanelColors,
    accent: Color,
    icon: ImageVector,
    label: String,
    lit: Boolean,
    open: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 64.dp)
            .then(if (lit) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (lit) colors.raise else colors.fill)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = label }
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (lit) accent else colors.t3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            if (open) Icon(GuangYueIcons.ChevronRight, null, tint = colors.t3.copy(alpha = colors.t3.alpha * 0.7f), modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = if (lit) colors.t1 else colors.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Folia ModeStepperRow（歌词样式 / 背景类型两行共用）。 */
@Composable
private fun StyleStepperRow(
    colors: StagePanelColors,
    name: String,
    onOpen: () -> Unit,
    onStep: ((Int) -> Unit)?,
    caption: String = "歌词样式",
    stepNoun: String = "歌词样式",
    glyph: (@Composable (Color) -> Unit)? = null,
    /** Folia ModeStepperRow aside：中间按钮与右箭头之间的小控件（歌词样式行 = 动画强度胶囊）。 */
    aside: (@Composable () -> Unit)? = null,
    /** Folia ModeStepperRow params：行下方靠右的参数行（各样式自己的快捷开关），与按钮之间留 4 缝。 */
    params: (@Composable () -> Unit)? = null,
) {
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onStep != null) StepArrow(colors, GuangYueIcons.ChevronLeft, "上一个$stepNoun") { onStep(-1) }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickableNoRipple(onOpen)
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)).background(colors.fill)
                    .border(1.dp, colors.line, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // 11-18：画当前样式 / 背景自己的字形（entry.tsx glyph）；没给时 Folia modeGlyphs FALLBACK_GLYPH 三道横线。
                if (glyph != null) glyph(colors.t2) else androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                    val u = size.width / 24f
                    val sw = 1.6f * u
                    fun line(y: Float, x2: Float, a: Float) = drawLine(colors.t2.copy(alpha = colors.t2.alpha * a), androidx.compose.ui.geometry.Offset(5 * u, y * u), androidx.compose.ui.geometry.Offset(x2 * u, y * u), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    line(8f, 19f, 1f); line(12f, 14f, 0.5f); line(16f, 17f, 0.5f)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(caption, fontSize = 11.5.sp, lineHeight = 15.sp, color = colors.t3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 14.sp, lineHeight = 19.sp, color = colors.t1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Icon(GuangYueIcons.ChevronDown, null, tint = colors.t3, modifier = Modifier.size(13.dp))
                }
            }
        }
        if (aside != null) { aside(); Spacer(Modifier.width(4.dp)) }
        if (onStep != null) StepArrow(colors, GuangYueIcons.ChevronRight, "下一个$stepNoun") { onStep(1) }
    }
    if (params != null) {
        // flex min-h-7 justify-end gap-1.5 pt-1
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            params()
        }
    }
  }
}

/** Folia animation.calm / normal / chaotic（zh-CN：平静 / 标准 / 混乱）。 */
internal fun intensityLabel(i: com.guangyue.core.lyrics.AnimationIntensity): String = when (i) {
    com.guangyue.core.lyrics.AnimationIntensity.CALM -> "平静"
    com.guangyue.core.lyrics.AnimationIntensity.NORMAL -> "标准"
    com.guangyue.core.lyrics.AnimationIntensity.CHAOTIC -> "混乱"
}

/** Folia shared/QuickControlToggle：28×28 圆角 8；开 = raise 底 + 强调色图标，关 = fill 底 + t3 图标。 */
@Composable
private fun QuickToggle(colors: StagePanelColors, accent: Color, active: Boolean, label: String, icon: ImageVector, onToggle: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (active) colors.raise else colors.fill)
            .clickableNoRipple(onToggle).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) accent else colors.t3, modifier = Modifier.size(14.dp))
    }
}

/** Folia shared/QuickControlChip：h-7（28dp）、圆角 8、左右 10、12sp、淡底 fill、字 t2。 */
@Composable
private fun QuickChip(colors: StagePanelColors, label: String, title: String, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(colors.fill)
            .clickableNoRipple(onClick).semantics { contentDescription = title }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = colors.t2, maxLines = 1)
    }
}

@Composable
private fun StepArrow(colors: StagePanelColors, icon: ImageVector, label: String, onClick: () -> Unit) {
    // Folia h-7 w-7 圆角 8、Chevron 14、t3；触控区放到 36dp。
    Box(Modifier.size(width = 32.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).clickableNoRipple(onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.t3, modifier = Modifier.size(14.dp))
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

const val StagePanelQueueSaveTag = "stage-panel-queue-save"
const val StagePanelQueueShuffleTag = "stage-panel-queue-shuffle"

// ---------------------------------------------------------------- 播放列表

/**
 * 照 Folia panelTab/QueueTab.tsx：头一行「播放队列 + 数目」（36dp，13sp 半粗 t2，数目常规 t3），
 * 行 48dp、圆角 8、左 12 右 6；正在播的那行铺 fill 底、左缘 3dp 强调色竖条（上下各让 14），
 * 歌名 13sp/19 半粗（不改色），歌手 12sp/17 t3。Folia 的行操作靠指针悬停才露出，
 * 触屏没有悬停，这里常驻三键「下一首播放 / 移到队尾 / 从队列中删除」（15dp 图标，命中 34×40dp）。空队列 = ListMusic 28 + 文案。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueBody(
    colors: StagePanelColors,
    accent: Color,
    queue: List<PlayerQueueRow>,
    onPick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onNext: ((Int) -> Unit)? = null,
    onEnd: ((Int) -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp)) {
        if (queue.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(GuangYueIcons.ListMusic, null, tint = colors.t3, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("播放列表为空", fontSize = 13.sp, color = colors.t3)
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("播放队列", fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.t2)
            Spacer(Modifier.width(6.dp))
            Text("${queue.size}", fontSize = 13.sp, lineHeight = 20.sp, color = colors.t3, modifier = Modifier.weight(1f))
            // Folia：Button quiet「保存为歌单」+ PanelIconButton s「打乱队列」（Shuffle 15）。触屏命中放到 36dp 高。
            if (onSave != null) {
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickableNoRipple { onSave() }
                        .padding(horizontal = 8.dp)
                        .testTag(StagePanelQueueSaveTag),
                    contentAlignment = Alignment.Center,
                ) { Text("保存为歌单", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = colors.t2) }
            }
            if (onShuffle != null && queue.size >= 2) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickableNoRipple { onShuffle() }
                        .semantics { contentDescription = "打乱队列" }
                        .testTag(StagePanelQueueShuffleTag),
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.Shuffle, null, tint = colors.t2, modifier = Modifier.size(15.dp)) }
            }
        }
        // Folia QueueTab：打开时把「正在播的前一行」滚到列表顶（刚播完的还看得见）。请求一块比视口高的矩形，
        // 滚动容器就按上沿对齐（bringIntoView 对超出视口的目标取前沿）。
        val curIdx = queue.indexOfFirst { it.current }
        val anchorIdx = (curIdx - 1).coerceAtLeast(0)
        val anchor = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        LaunchedEffect(curIdx) {
            if (curIdx > 0) runCatching { anchor.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 100_000f)) }
        }
        queue.forEach { row ->
            Box(
                Modifier
                    .then(if (row.index == anchorIdx) Modifier.bringIntoViewRequester(anchor) else Modifier)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (row.current) colors.fill else Color.Transparent)
                    .clickableNoRipple { onPick(row.index) },
            ) {
                if (row.current) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(vertical = 14.dp)
                            .fillMaxHeight()
                            .width(3.dp)
                            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                            .background(accent),
                    )
                }
                Row(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.title,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (row.current) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.t1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (row.subtitle.isNotBlank()) {
                            Text(row.subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = colors.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!row.current) {
                        Spacer(Modifier.width(4.dp))
                        listOfNotNull(
                            onNext?.let { Triple(GuangYueIcons.ListPlus, "下一首播放", it) },
                            onEnd?.let { Triple(GuangYueIcons.ListEnd, "移到队尾", it) },
                            Triple(GuangYueIcons.Trash2, "从队列中删除", onRemove),
                        ).forEach { (icon, label, action) ->
                            Box(
                                Modifier
                                    .size(width = 34.dp, height = 40.dp)
                                    .clickableNoRipple { action(row.index) }
                                    .semantics { contentDescription = label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(icon, null, tint = colors.t3, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
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
