package com.guangyue.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.perf.TrackScrollFrames
import com.guangyue.music.ui.design.FoliaRowCover
import com.guangyue.music.ui.design.FoliaSongRow

/**
 * U7 音乐库页的四态。
 *
 * 判定顺序与首页墙（[com.guangyue.music.ui.home.homeWallState]）完全一致：
 * 有内容就先照常显示（哪怕后台还在补），再看载入 / 失败，最后才是空。
 */
enum class LibraryState {
    /** 库里一个字都还没读到（首帧之前）。 */
    Loading,
    /** 有内容。 */
    Content,
    /** 没内容也没在载入。 */
    Empty,
    /** 读本地库出错。 */
    Failed,
}

/** 纯函数，便于单测（对应 U6 的 `homeWallState`）。 */
fun libraryState(loading: Boolean, failed: Boolean, hasContent: Boolean): LibraryState {
    return when {
        hasContent -> LibraryState.Content
        loading -> LibraryState.Loading
        failed -> LibraryState.Failed
        else -> LibraryState.Empty
    }
}

/** 「收藏 · 12 首」这类计数文案：`%d` 由调用方填。 */
fun countText(pattern: String, count: Int): String = pattern.replace("%d", count.toString())

/**
 * 音乐库页文案（中文串由 app 层取 `R.string.library_*` 注入，见 `nav/NavHost.kt#libraryStrings`）。
 *
 * 每条来源都标在 `app/src/main/res/values/strings.xml` 的「U7 音乐库页」段里：
 * 对得上 Folia `zh-CN.ts` 键名的都标了行号，对不上的标「自拟」。
 */
@Immutable
data class LibraryStrings(
    /** zh-CN.ts 6 "libraryBravais.homeTitle"「书库」。 */
    val title: String,
    /** zh-CN.ts 1476 "yourPlaylists"「您的歌单」。 */
    val playlistsTitle: String,
    /** zh-CN.ts 4056 "favorites"「收藏」。 */
    val favoritesTitle: String,
    /** zh-CN.ts 4057 "favoritesDesc"「已标星歌曲」。 */
    val favoritesSubtitle: String,
    /** zh-CN.ts 563 "sourceLocal"「本地音乐」。 */
    val localTitle: String,
    /** zh-CN.ts 1652 "exploreRecent"「最近播放」。 */
    val recentTitle: String,
    /** 每段标题右侧的「N 首」（zh-CN.ts 10 "trackCount"）。 */
    val countPattern: String,
    /** 载入中主句。 */
    val loading: String,
    /** 失败主句。 */
    val failed: String,
    /** 失败副句。 */
    val failedHint: String,
    /** zh-CN.ts 1641 "exploreRetry"「重试」。 */
    val retry: String,
    /** zh-CN.ts 9 "libraryBravais.emptyCollection"「这里还没有内容」。 */
    val empty: String,
    /** 整库为空的副句。 */
    val emptyHint: String,
    /** 整库为空时的动作「去搜索」。 */
    val emptyAction: String,
    /** zh-CN.ts 3908 "noPlaylistsFound"「还没有歌单」。 */
    val emptyPlaylists: String,
    /** 收藏段为空。 */
    val emptyFavorites: String,
    /** zh-CN.ts 3816 "noLocalMusic"「暂无本地音乐」。 */
    val emptyLocal: String,
    /** 最近播放段为空。 */
    val emptyRecent: String,
    /** 顶部进播放页圆钮的无障碍名。 */
    val openPlayer: String,
)

/** 每段最多铺多少行（库里几千首也不会把首屏拉爆），其余靠搜索 / 最近播放兜。 */
private const val SECTION_MAX_ROWS = 10

/**
 * 音乐库页（U7）——把 Folia 的资料库 / 书库（Folia `src/library` 目录）搬成手机版 Compose。
 *
 * 与 Folia 的对应：
 * - 页面标题 = `libraryBravais.homeTitle`「书库」；Folia 的「墙面」在手机上是竖直分区列表；
 * - 四个分区 = 我的歌单（`yourPlaylists`，Folia 歌单合集）/ 收藏（`favorites`，Folia 的
 *   `special.liked`）/ 本地音乐（`sourceLocal`）/ 最近播放（`exploreRecent`）；
 * - 每段标题右侧带「N 首」（Folia `loaded`「已加载 {{loaded}} 首」的手机版）。
 *
 * 数据全部来自本地库（[com.guangyue.feature.library.viewmodel.HomeViewModel] 的
 * playlistsFlow / favoriteTiles / localTiles / recentLibrary），**生产路径不造任何假数据**：
 * 某段为空就显示该段自己的中文空态，整库为空才显示整页空态。
 *
 * 手机适配：内容整屏可滚并给迷你条 / 底栏留出底边；顶部 inset 由 [FoliaTopBar] 自吃；
 * 每行 ≥48dp 触控（歌单 / 曲目行都是 56dp）；字体放大到 1.3 倍时三态区自己可滚。
 */
@Composable
fun LibraryPageScreen(
    playlists: List<HomeTile>,
    favorites: List<HomeTile>,
    local: List<HomeTile>,
    recent: List<HomeTile>,
    strings: LibraryStrings,
    state: LibraryState,
    onOpenTile: (HomeTile) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val hasContent = playlists.isNotEmpty() || favorites.isNotEmpty() ||
        local.isNotEmpty() || recent.isNotEmpty()
    val shown = libraryState(
        loading = state == LibraryState.Loading,
        failed = state == LibraryState.Failed,
        hasContent = hasContent,
    )

    Column(modifier.fillMaxSize().marbleBackground()) {
        TrackScrollFrames()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            FoliaTopBar(
                title = strings.title,
                onBack = onBack,
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
                // key(state) 让「重试」重建整块，动画不带状态，截图与单测都可复现（同 U6）。
                LibraryState.Loading -> key(state) {
                    StatusPanel(
                        icon = GuangYueIcons.Loader2,
                        title = strings.loading,
                        tint = Gyp.t3,
                    )
                }

                LibraryState.Failed -> key(state) {
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

                LibraryState.Empty -> key(state) {
                    StatusPanel(
                        icon = GuangYueIcons.Library,
                        title = strings.empty,
                        subtitle = strings.emptyHint,
                        tint = Gyp.t3,
                        primaryAction = strings.emptyAction,
                        primaryIcon = GuangYueIcons.Search,
                        onPrimaryAction = onOpenSearch,
                    )
                }

                LibraryState.Content -> Sections(
                    playlists = playlists,
                    favorites = favorites,
                    local = local,
                    recent = recent,
                    strings = strings,
                    onOpenTile = onOpenTile,
                )
            }
        }
        miniPlayer?.invoke()
        bottomBar?.invoke()
    }
}

/** 有内容时的四段：我的歌单 / 收藏 / 本地音乐 / 最近播放。 */
@Composable
private fun Sections(
    playlists: List<HomeTile>,
    favorites: List<HomeTile>,
    local: List<HomeTile>,
    recent: List<HomeTile>,
    strings: LibraryStrings,
    onOpenTile: (HomeTile) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 底边给迷你条 + 底栏 + 手势条留出位置（外层 Shell 是 Column 布局，不这样留会遮内容）。
        contentPadding = PaddingValues(
            start = GySpace.pageH,
            end = GySpace.pageH,
            top = GySpace.xs,
            bottom = GySpace.xl + 120.dp,
        ),
    ) {
        item("playlists-title") {
            SectionTitle(strings.playlistsTitle, countText(strings.countPattern, playlists.size))
        }
        if (playlists.isEmpty()) {
            item("playlists-empty") { SectionEmpty(GuangYueIcons.ListMusic, strings.emptyPlaylists) }
        } else {
            items(playlists.take(20), key = { it.key }) { tile ->
                PlaylistRow(tile = tile, onClick = { onOpenTile(tile) })
            }
        }

        item("favorites-title") {
            SectionTitle(strings.favoritesTitle, countText(strings.countPattern, favorites.size))
        }
        if (favorites.isEmpty()) {
            item("favorites-empty") { SectionEmpty(GuangYueIcons.Star, strings.emptyFavorites) }
        } else {
            items(favorites.take(SECTION_MAX_ROWS), key = { it.key }) { tile ->
                TrackRow(tile = tile, onClick = { onOpenTile(tile) })
            }
        }

        item("local-title") {
            SectionTitle(strings.localTitle, countText(strings.countPattern, local.size))
        }
        if (local.isEmpty()) {
            item("local-empty") { SectionEmpty(GuangYueIcons.HardDrive, strings.emptyLocal) }
        } else {
            items(local.take(SECTION_MAX_ROWS), key = { it.key }) { tile ->
                TrackRow(tile = tile, onClick = { onOpenTile(tile) })
            }
        }

        item("recent-title") {
            SectionTitle(strings.recentTitle, countText(strings.countPattern, recent.size))
        }
        if (recent.isEmpty()) {
            item("recent-empty") { SectionEmpty(GuangYueIcons.History, strings.emptyRecent) }
        } else {
            items(recent.take(SECTION_MAX_ROWS), key = { it.key }) { tile ->
                TrackRow(tile = tile, onClick = { onOpenTile(tile) })
            }
        }
    }
}

/** 分区标题 + 右侧「N 首」。 */
@Composable
private fun SectionTitle(title: String, count: String) {
    FoliaSectionTitle(
        text = title,
        action = {
            Text(
                text = count,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(end = GySpace.xs),
            )
        },
    )
}

/** 歌单一行：封面缩略图 + 名字 + 说明（Folia 歌单合集的一行）。 */
@Composable
private fun PlaylistRow(tile: HomeTile, onClick: () -> Unit) {
    FoliaSongRow(
        title = tile.title,
        subtitle = tile.subtitle,
        leading = { FoliaRowCover(icon = GuangYueIcons.ListMusic) },
        onClick = onClick,
    )
}

/** 曲目一行：封面缩略图 + 歌名 + 歌手。 */
@Composable
private fun TrackRow(tile: HomeTile, onClick: () -> Unit) {
    FoliaSongRow(
        title = tile.title,
        subtitle = tile.subtitle,
        leading = { FoliaRowCover(icon = GuangYueIcons.Music) },
        onClick = onClick,
    )
}

/** 分区自己的空态：一行灰底提示（与「我的」页的 EmptyHint 同一副样子）。 */
@Composable
private fun SectionEmpty(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GySpace.xs)
            .clip(RoundedCornerShape(GyRadius.card))
            .background(Gyp.tint)
            .padding(horizontal = GySpace.l, vertical = GySpace.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GySpace.m),
    ) {
        Icon(icon, contentDescription = null, tint = Gyp.t3, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
        )
    }
}

/**
 * 整页三态（载入 / 失败 / 空）共用的面板。
 *
 * 与 U6 首页墙的 StatusPanel 同形：88dp 圆角图标块 + 主句 + 副句 + 1~2 颗按钮；
 * 整块可滚，字体放大到 1.3 倍也不会把按钮挤出屏幕。
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(GySpace.l))
        Text(
            text = title,
            fontSize = GyText.emptyTitle,
            lineHeight = GyText.titleHeight,
            letterSpacing = GyText.emptyTitleSpacing,
            fontWeight = FontWeight.SemiBold,
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