package com.guangyue.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.glassPanel
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.library.home.ProgressLine
import com.guangyue.feature.library.perf.TrackScrollFrames
import com.guangyue.music.ui.design.FoliaCoverCard
import kotlinx.coroutines.launch

/**
 * 首页墙四态（U6）。
 *
 * 0.0.9 之前首页只有一个「还没有开始听」占位；U6 变成真内容墙之后，加载 / 空 / 失败必须分开：
 * 没音源是**空**（给出路），音源报错是**失败**（给重试），还在问音源是**加载**。
 * 判定写成纯函数（见 [homeWallState]），截图与单测都直接喂状态，不碰 ViewModel / 网络。
 */
enum class HomeWallState { Loading, Content, Failed, Empty }

/**
 * 四态判定（纯函数，可单测）：
 * 1. 已经有内容 → [HomeWallState.Content]（后到的失败不该盖掉已有内容）；
 * 2. 没内容且还在拉 → [HomeWallState.Loading]；
 * 3. 没内容且拉失败 → [HomeWallState.Failed]；
 * 4. 其余（还没开始 / 音源没给数据）→ [HomeWallState.Empty]，给出路。
 */
fun homeWallState(loading: Boolean, failed: Boolean, hasContent: Boolean): HomeWallState = when {
    hasContent -> HomeWallState.Content
    loading -> HomeWallState.Loading
    failed -> HomeWallState.Failed
    else -> HomeWallState.Empty
}

/**
 * 首页墙文案（中文串由 app 层取 `R.string.home_wall_*` 注入，见 `nav/NavHost.kt#homeStrings`）。
 *
 * 每条的来源都标在 `app/src/main/res/values/strings.xml` 的「U6 首页墙」段里：
 * Folia 的键（`zh-CN.ts`）能对上的都对上了，对不上的标「自拟」。
 */
@Immutable
data class HomeStrings(
    /** zh-CN.ts 1613 "welcomeBack"「欢迎回来」：有音源时的顶部问候。 */
    val greeting: String,
    /** zh-CN.ts 1450 "welcome"「欢迎使用 Folia」→ 广乐自己的问候：没音源时用。 */
    val greetingGuest: String,
    /** zh-CN.ts 1477 "searchPlaceholder"「搜索音乐...」：搜索入口。 */
    val search: String,
    /** 推荐歌单段标题。 */
    val recommendTitle: String,
    /** zh-CN.ts 1652 "exploreRecent"「最近播放」。 */
    val recentTitle: String,
    /** 榜单段标题。 */
    val chartsTitle: String,
    /** 载入中主句。 */
    val loading: String,
    /** zh-CN.ts 1640 "exploreFailed"「推荐内容没有读到」。 */
    val failed: String,
    /** 失败时的出路副句。 */
    val failedHint: String,
    /** zh-CN.ts 1641 "exploreRetry"「重试」。 */
    val retry: String,
    /** zh-CN.ts 1639 "exploreEmpty"「暂时没有推荐内容」：没音源主句。 */
    val noSource: String,
    /** 没音源副句。 */
    val noSourceHint: String,
    /** 没音源动作「去添加音源」。 */
    val noSourceAction: String,
    /** 有音源但墙是空的主句。 */
    val empty: String,
    /** 有音源但墙是空的副句。 */
    val emptyHint: String,
    /** 有音源但墙是空的动作「去搜索」。 */
    val emptyAction: String,
    /** 顶部进播放页圆钮的无障碍名。 */
    val openPlayer: String,
    /** zh-CN.ts 1642/1643 "exploreScrollLeft/Right"「向左滚动」「向右滚动」。 */
    val scrollLeft: String,
    val scrollRight: String,
)

/**
 * 首页墙（U6）——把 Folia 首页搬成手机版 Compose。
 *
 * 与 Folia 的对应（`src/components/app/home/explore/ExploreSections.tsx`、`ownSections.ts`、
 * `src/i18n/locales/zh-CN.ts`）：
 * - 顶部问候 = `home.welcomeBack` 那一行；右侧一颗圆钮进播放页（Folia 顶部是搜索框，
 *   手机上搜索在下面有独立入口）；
 * - 「推荐歌单」= 音源 `getRecommendSheetTags` 的叶子结果，**手机 2 列网格**（Folia 桌面是横滑大卡）；
 * - 「最近播放」= `home.exploreRecent`，横滑分区（Folia 的 `ownSectionsOf` 就是自己记的最近播放）；
 * - 「热门榜单」= 音源 `getTopLists`，竖排一行一条（Folia 也是横滑，竖排更好点）。
 *
 * 假数据（`playlist:1`「夜间城市流行」之类）只出现在测试与画廊；生产路径一律走音源，
 * 拿不到就整段不显示，不塞任何兜底假数据。
 *
 * 手机适配：内容整屏可滚，底栏 / 迷你条在 [bottomBar] / [miniPlayer] 槽里（由外壳
 * `ShellColumn` 摆位，手势条 inset 由底栏自吃）；触控一律 ≥48dp；字体放大到 1.3 倍时
 * 三态区自己可滚，不会把按钮挤出屏幕。
 */
@Composable
fun HomeWallScreen(
    recommended: List<HomeTile>,
    recent: List<HomeTile>,
    charts: List<HomeTile>,
    hasAnyPlugin: Boolean,
    strings: HomeStrings,
    state: HomeWallState,
    onOpenTile: (HomeTile) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSearch: () -> Unit,
    playbackTitle: String,
    playbackArtwork: String?,
    playbackProgress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val hasContent = recommended.isNotEmpty() || recent.isNotEmpty() || charts.isNotEmpty()
    // ViewModel 只发 Loading / Failed；「Content 但没数据」= 空态（没音源，或音源没给内容）。
    val shown = homeWallState(
        loading = state == HomeWallState.Loading,
        failed = state == HomeWallState.Failed,
        hasContent = hasContent,
    )

    Column(modifier.fillMaxSize().marbleBackground()) {
        TrackScrollFrames()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            FoliaTopBar(
                title = if (hasAnyPlugin) strings.greeting else strings.greetingGuest,
                serif = true,
                actions = {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(GyRadius.field + 4.dp))
                            .clickable(onClick = onOpenPlayer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = GuangYueIcons.AudioLines,
                            contentDescription = strings.openPlayer,
                            tint = Gyp.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            when (shown) {
                // key(retryState) 让「重试」重建整块，动画不带状态，截图与单测都可复现。
                HomeWallState.Loading -> key(state) {
                    StatusPanel(
                        icon = GuangYueIcons.Loader2,
                        title = strings.loading,
                        tint = Gyp.t3,
                    )
                }

                HomeWallState.Failed -> key(state) {
                    StatusPanel(
                        icon = GuangYueIcons.CloudOff,
                        title = strings.failed,
                        subtitle = strings.failedHint,
                        tint = Gyp.danger,
                        primaryAction = strings.retry,
                        primaryIcon = GuangYueIcons.RefreshCw,
                        onPrimaryAction = onRetry,
                    )
                }

                HomeWallState.Empty -> key(state) {
                    if (hasAnyPlugin) {
                        StatusPanel(
                            icon = GuangYueIcons.Music,
                            title = strings.empty,
                            subtitle = strings.emptyHint,
                            tint = Gyp.t3,
                            primaryAction = strings.emptyAction,
                            primaryIcon = GuangYueIcons.Search,
                            onPrimaryAction = onOpenSearch,
                        )
                    } else {
                        StatusPanel(
                            icon = GuangYueIcons.Plug,
                            title = strings.noSource,
                            subtitle = strings.noSourceHint,
                            tint = Gyp.t3,
                            primaryAction = strings.noSourceAction,
                            primaryIcon = GuangYueIcons.Plug,
                            onPrimaryAction = onOpenPlugins,
                            secondaryAction = strings.emptyAction,
                            onSecondaryAction = onOpenSearch,
                        )
                    }
                }

                HomeWallState.Content -> Wall(
                    recommended = recommended,
                    recent = recent,
                    charts = charts,
                    strings = strings,
                    onOpenTile = onOpenTile,
                    onOpenSearch = onOpenSearch,
                    playbackTitle = playbackTitle,
                    playbackArtwork = playbackArtwork,
                    playbackProgress = playbackProgress,
                    isPlaying = isPlaying,
                    onTogglePlay = onTogglePlay,
                )
            }
        }
        miniPlayer?.invoke()
        bottomBar?.invoke()
    }
}

/** 有内容时的墙：搜索入口（有在听的歌再加一条继续播放）+ 2 列推荐网格 + 两个横滑分区 + 榜单。 */
@Composable
private fun Wall(
    recommended: List<HomeTile>,
    recent: List<HomeTile>,
    charts: List<HomeTile>,
    strings: HomeStrings,
    onOpenTile: (HomeTile) -> Unit,
    onOpenSearch: () -> Unit,
    playbackTitle: String,
    playbackArtwork: String?,
    playbackProgress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 底部留出迷你条 + 底栏的高度（上层 Shell 是 Column 布局会盖住内容底边），
        contentPadding = PaddingValues(bottom = GySpace.xl + 120.dp),
    ) {
        item("search") {
            SearchEntry(text = strings.search, onClick = onOpenSearch)
            if (playbackTitle.isNotBlank()) {
                ContinueRow(
                    title = playbackTitle,
                    artwork = playbackArtwork,
                    progress = playbackProgress,
                    isPlaying = isPlaying,
                    onTogglePlay = onTogglePlay,
                )
            }
        }

        if (recommended.isNotEmpty()) {
            item("recommend-title") { FoliaSectionTitle(text = strings.recommendTitle) }
            // 手机上「推荐歌单」= 2 列网格（Folia 桌面是横滑大卡；2 列一屏能看到 4 张封面）。
            item("recommend-grid") {
                Column {
                    recommended.take(4).chunked(2).forEach { rowTiles ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = GySpace.pageH,
                                    end = GySpace.pageH,
                                    top = GySpace.xxs,
                                    bottom = GySpace.xxs,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                        ) {
                            rowTiles.forEach { tile ->
                                Box(Modifier.weight(1f)) {
                                    PosterCard(tile = tile, onClick = { onOpenTile(tile) })
                                }
                            }
                            // 单数行补一个等宽空位，卡片不会被拉成整行宽。
                            if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item("recent") {
                Shelf(
                    title = strings.recentTitle,
                    tiles = recent,
                    cardWidth = 104.dp,
                    aspect = 1f,
                    strings = strings,
                    onOpenTile = onOpenTile,
                )
            }
        }

        if (charts.isNotEmpty()) {
            item("charts-title") { FoliaSectionTitle(text = strings.chartsTitle) }
            items(charts, key = { it.key }) { tile ->
                Box(Modifier.padding(horizontal = GySpace.xs)) {
                    ChartRow(tile = tile, onClick = { onOpenTile(tile) })
                }
            }
        }
    }
}

/** 顶部搜索入口（走既有搜索路由）。玻璃底、48dp 触控。 */
@Composable
private fun SearchEntry(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH, vertical = GySpace.xs)
            .height(48.dp)
            .clip(RoundedCornerShape(GyRadius.field))
            .glassPanel(corner = GyRadius.field)
            .clickable(onClick = onClick)
            .padding(horizontal = GySpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = GuangYueIcons.Search,
            contentDescription = null,
            tint = Gyp.t2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(GySpace.s))
        Text(
            text = text,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「继续播放」一条（有在听的歌才出现；播放键 48dp 触控）。 */
@Composable
private fun ContinueRow(
    title: String,
    artwork: String?,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH, vertical = GySpace.xs)
            .height(56.dp)
            .clip(RoundedCornerShape(GyRadius.row))
            .glassPanel(corner = GyRadius.row)
            .padding(horizontal = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
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
        Spacer(Modifier.width(GySpace.s))
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
            Spacer(Modifier.height(GySpace.xxs))
            ProgressLine(progress = progress)
        }
        Spacer(Modifier.width(GySpace.s))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(GyRadius.field + 4.dp))
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                contentDescription = null,
                tint = Gyp.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 一个横滑分区：标题 + 左右滚动钮 + 横滑卡片行。
 *
 * 用 `Row(horizontalScroll)`（而不是 LazyRow）：一行最多 ~30 条，全量布局很便宜，而且分区放在
 * `LazyColumn` 里时不会出现「嵌套懒布局」的测量问题。左右钮就是 Folia 桌面
 * `home.exploreScrollLeft/Right`（ExploreSections.tsx 里那两颗 h-8 w-8）保留到手机上，48dp 触控。
 */
@Composable
private fun Shelf(
    title: String,
    tiles: List<HomeTile>,
    cardWidth: Dp,
    aspect: Float,
    strings: HomeStrings,
    onOpenTile: (HomeTile) -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { (cardWidth + GySpace.m).toPx() * 2 }
    Column {
        FoliaSectionTitle(
            text = title,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScrollButton(
                        icon = GuangYueIcons.ChevronLeft,
                        label = strings.scrollLeft,
                        onClick = {
                            scope.launch { scroll.animateScrollBy(-step) }
                        },
                    )
                    ScrollButton(
                        icon = GuangYueIcons.ChevronRight,
                        label = strings.scrollRight,
                        onClick = {
                            scope.launch { scroll.animateScrollBy(step) }
                        },
                    )
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = GySpace.pageH),
            horizontalArrangement = Arrangement.spacedBy(GySpace.m),
        ) {
            tiles.forEach { tile ->
                Box(Modifier.width(cardWidth)) {
                    PosterCard(tile = tile, aspect = aspect, onClick = { onOpenTile(tile) })
                }
            }
        }
    }
}

/** 横滑分区左右的滚动钮（Folia 是 32dp；手机给 48dp 触控）。 */
@Composable
private fun ScrollButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(GyRadius.field + 4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Gyp.t2,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 封面卡：直接复用 U1 设计地基的 [FoliaCoverCard]，封面槽填 [ArtworkFrame]。 */
@Composable
private fun PosterCard(tile: HomeTile, onClick: () -> Unit, aspect: Float = 1.2f) {
    FoliaCoverCard(
        title = tile.title,
        subtitle = tile.subtitle,
        cover = { ArtworkFrame(tile = tile, modifier = Modifier.fillMaxSize()) },
        onClick = onClick,
        aspect = aspect,
    )
}

/** 封面内容：真图（coil3）/ 歌单叠卡 + 类型图标。 */
@Composable
private fun ArtworkFrame(tile: HomeTile, modifier: Modifier = Modifier) {
    Box(modifier.background(Gyp.fill2), contentAlignment = Alignment.Center) {
        if (tile.artwork != null) {
            AsyncImage(
                model = tile.artwork,
                contentDescription = tile.title,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (tile.kind == HomeTileKind.PLAYLIST || tile.kind == HomeTileKind.ALBUM) {
                PlaylistStack()
            }
            Icon(
                imageVector = kindIcon(tile.kind),
                contentDescription = null,
                tint = Gyp.t3,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun kindIcon(kind: HomeTileKind): ImageVector = when (kind) {
    HomeTileKind.PLAYLIST -> GuangYueIcons.ListMusic
    HomeTileKind.ALBUM -> GuangYueIcons.Disc3
    HomeTileKind.TRACK -> GuangYueIcons.Music
    HomeTileKind.STATION -> GuangYueIcons.Radio
    HomeTileKind.SHEET -> GuangYueIcons.ListMusic
}

/** 榜单一条：封面 + 标题 + 副标题（前 3 首），整行 ≥56dp 触控。 */
@Composable
private fun ChartRow(tile: HomeTile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(GyRadius.row))
            .clickable(onClick = onClick)
            .padding(horizontal = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(GyRadius.field))
                .background(Gyp.fill2),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.artwork != null) {
                AsyncImage(
                    model = tile.artwork,
                    contentDescription = tile.title,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = GuangYueIcons.Radio,
                    contentDescription = null,
                    tint = Gyp.t3,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(GySpace.m))
        Column(Modifier.weight(1f)) {
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
        Spacer(Modifier.width(GySpace.s))
        Icon(
            imageVector = GuangYueIcons.ChevronRight,
            contentDescription = null,
            tint = Gyp.t3,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Folia 歌单卡上的两条错位卡片条（表示「一叠歌单」）。 */
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
 * 加载 / 失败 / 空三态面板（Folia 对应：骨架屏 / `exploreFailed` + `exploreRetry` / `exploreEmpty`）。
 *
 * 内容居中偏上（不贴顶也不掉到整屏正中）；整块可滚，字体放大到 1.3 倍也不会把按钮挤出屏幕；
 * 底栏与迷你条在自己的槽里，这里不会遮到。
 */
@Composable
private fun StatusPanel(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color,
    primaryAction: String? = null,
    primaryIcon: ImageVector? = null,
    secondaryAction: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(GyRadius.card))
                .background(Gyp.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(GySpace.l))
        Text(
            text = title,
            fontSize = GyText.emptyTitle,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = GyText.emptyTitleSpacing,
            color = Gyp.ink,
            textAlign = TextAlign.Center,
            fontFamily = GuangYueDesign.Sans,
            modifier = Modifier.padding(horizontal = GySpace.xl),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(GySpace.xs))
            Text(
                text = subtitle,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                textAlign = TextAlign.Center,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(horizontal = GySpace.xl),
            )
        }
        if (primaryAction != null && onPrimaryAction != null) {
            Spacer(Modifier.height(GySpace.l))
            FoliaButton(
                text = primaryAction,
                onClick = onPrimaryAction,
                variant = FoliaButtonVariant.Primary,
                icon = primaryIcon,
            )
        }
        if (secondaryAction != null && onSecondaryAction != null) {
            Spacer(Modifier.height(GySpace.s))
            FoliaButton(
                text = secondaryAction,
                onClick = onSecondaryAction,
                variant = FoliaButtonVariant.Fill,
                icon = GuangYueIcons.Search,
            )
        }
        Spacer(Modifier.height(GySpace.xl))
    }
}