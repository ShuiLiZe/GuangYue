package com.guangyue.feature.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.feature.library.design.FlowChips
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaEmptyState
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaSegmented
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

// ---------------------------------------------------------------------------------------------
// U3 · 搜索页（Folia 样式）
//
// 视觉基准：`src/components/app/search/SearchWorkspace.tsx` + `SearchResultRow.tsx`
// （手机档截图 `docs/port/baseline/screenshots/search-{idle,results,empty}-m412-light.png`）。
//
// 和 Folia 桌面的差别（手机适配，写在 docs/ui-u3/report.md §3）：
//   - 顶部「Folia 顶栏 + 搜索框」→ 手机只留一行搜索框（顶栏让位给搜索框，Folia 桌面是侧栏 + 顶栏双栏）；
//   - 回车提交 → 输入即搜（防抖 400ms，见 SearchPageState.kt）；
//   - hover 才冒出的 ＋ / 播放浮层 → 常驻按下即播（触屏没有 hover）；
//   - `rowHeight 76px` 结果行 → 一行一卡，卡片 12dp 圆角 + 100% 行可点，最小 64dp 高；
//   - 键盘弹起：整页 imePadding，迷你条 / 底栏由 ShellColumn 的 collapseOnIme() 让位。
// ---------------------------------------------------------------------------------------------

/**
 * 搜索页。
 *
 * 参数里的 [onSearch] 是「立即搜」（键盘搜索键 / 清空后点搜索字样）；输入变化走 [onQueryChange]，
 * 防抖与取消在 ViewModel 里（[SearchViewModel.onQueryChange]）。
 */
@Composable
fun FoliaSearchScreen(
    res: SearchStrings,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPickSource: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onPickHistory: (String) -> Unit,
    onOpenAlbum: (MusicItem) -> Unit,
    onOpenArtist: (MusicItem) -> Unit,
    onTabChange: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    miniPlayer: (@Composable () -> Unit)? = null,
    onRetry: () -> Unit = {},
    onOpenSourceManager: (() -> Unit)? = null,
) {
    val phase = remember(state) { state.pagePhase() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .marbleBackground()
            .testTag(SearchTags.Root),
    ) {
        SearchHeader(
            res = res,
            query = state.query,
            loading = phase == SearchPagePhase.Loading,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = { onQueryChange("") },
        )

        if (state.available.isNotEmpty()) {
            SourceRail(
                available = state.available,
                sourceId = state.sourceId,
                onPickSource = onPickSource,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (phase) {
                SearchPagePhase.NoSource -> NoSourceState(res, onOpenSourceManager)
                SearchPagePhase.Idle -> IdleBody(
                    res = res,
                    history = state.history,
                    onPickHistory = onPickHistory,
                    onPickKeyword = { keyword ->
                        onQueryChange(keyword)
                        onSearch()
                    },
                )
                SearchPagePhase.Loading -> LoadingState(res, state.query)
                SearchPagePhase.Failure -> FailureState(
                    res = res,
                    message = state.failure.orEmpty(),
                    onRetry = onRetry,
                )
                SearchPagePhase.Empty -> EmptyState(
                    res = res,
                    keywords = state.history.ifEmpty { IdleKeywords },
                    onPickKeyword = { keyword ->
                        onQueryChange(keyword)
                        onSearch()
                    },
                )
                SearchPagePhase.Results -> ResultsBody(
                    res = res,
                    state = state,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onAddToSheet = onAddToSheet,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onTabChange = onTabChange,
                    onRetry = onRetry,
                )
            }
        }

        // 迷你条 + 底栏是 Shell 的一行，不叠在内容上（ShellColumn，U2）；这里只负责在键盘弹起时
        // 跟着 imePadding 一起上浮。
        if (miniPlayer != null || bottomBar != null) {
            Column(Modifier.fillMaxWidth()) {
                miniPlayer?.invoke()
                bottomBar?.invoke()
            }
        }
    }
}

/** 截图 / 测试用的稳定锚点（避免测试去猜中文文案）。 */
object SearchTags {
    const val Root = "search-root"
    const val Field = "search-field"
    const val Clear = "search-clear"
    const val Results = "search-results"
    const val Loading = "search-loading"
    const val Empty = "search-empty"
    const val Failure = "search-failure"
    const val Idle = "search-idle"
    const val NoSource = "search-no-source"
    const val Mini = "search-mini"
}

/** 初始态与空结果页共用的推荐关键词（Folia 桌面没有这一块，属于广乐自拟的「入口」。 */
internal val IdleKeywords = listOf("热歌", "华语流行", "摇滚", "纯音乐", "粤语", "爵士", "OST")

// ---------------------------------------------------------------------------------------------
// 顶栏：一行搜索框（Folia SearchWorkspace.tsx:44-78）
// ---------------------------------------------------------------------------------------------

/**
 * 搜索框：`rounded-full bg-black/5 px-4` 的一个胶囊，左边放大镜、中间输入、右边 × 清除。
 *
 * Folia 原值：`rounded-2xl border` 高 52（`py-3.5 text-sm`）；手机改为 48dp 高胶囊
 * （U1 的 FoliaSearchField 同款高度，`kit/TextField.tsx:9-72` 32px → 48dp）。
 * 最小触控 48dp：× 按钮 48dp 方框装 20dp 图标（Folia 是 `p-3` 44px）。
 */
@Composable
private fun SearchHeader(
    res: SearchStrings,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 0.0.14 真机：搜索框顶到状态栏上了，这里自己让出状态栏高度。
            .statusBarsPadding()
            .padding(start = GySpace.pageH, end = GySpace.pageH, top = GySpace.m, bottom = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(shape)
                .background(Gyp.tint)
                .border(1.dp, if (focused) Gyp.accent else Color.Transparent, shape)
                .padding(start = GySpace.pageH, end = GySpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                // Folia：isSearching 时左侧图标换成转圈的 Loader2（SearchWorkspace.tsx:52-58）。
                Spinner(size = 16.dp, color = Gyp.t3)
            } else {
                androidx.compose.material3.Icon(
                    imageVector = GuangYueIcons.Search,
                    contentDescription = null,
                    tint = Gyp.t3,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(GySpace.s))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(res.placeholder),
                        fontSize = GyText.label,
                        fontFamily = GuangYueDesign.Sans,
                        color = Gyp.t3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = GyText.label,
                        lineHeight = GyText.labelHeight,
                        color = Gyp.ink,
                        fontFamily = GuangYueDesign.Sans,
                    ),
                    cursorBrush = SolidColor(Gyp.accent),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SearchTags.Field),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(SearchTags.Clear)
                        .clickableNoRipple(onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = GuangYueIcons.X,
                        contentDescription = stringResource(res.clear),
                        tint = Gyp.t2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 音源选择（Folia SearchWorkspace.tsx:79-95 的一排胶囊）
// ---------------------------------------------------------------------------------------------

/**
 * 音源胶囊：选中 = accent 实心 + onAccent 字（Folia 桌面就是主题色实心胶囊），未选中 = 浅底。
 *
 * 高度 36dp，但外层给 48dp 的触控高度（Folia `px-4 py-2 text-xs` 32px → 手机 36dp + 48dp 触摸区）。
 */
@Composable
private fun SourceRail(
    available: List<PluginMeta>,
    sourceId: String,
    onPickSource: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = GySpace.pageH),
        horizontalArrangement = Arrangement.spacedBy(GySpace.xs),
    ) {
        items(available, key = { it.id }) { plugin ->
            SourceChip(
                label = platformLabelOf(plugin),
                selected = plugin.id == sourceId,
                onClick = { onPickSource(plugin.id) },
            )
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) Gyp.accent else Gyp.tint)
                .padding(horizontal = GySpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = if (selected) Gyp.onAccent else Gyp.t2,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 结果
// ---------------------------------------------------------------------------------------------

@Composable
private fun ResultsBody(
    res: SearchStrings,
    state: SearchUiState,
    onPlay: (Int) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
    onOpenAlbum: (MusicItem) -> Unit,
    onOpenArtist: (MusicItem) -> Unit,
    onTabChange: (SearchTab) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag(SearchTags.Results)) {
        if (state.availableTypes.size > 1) {
            FoliaSegmented(
                value = state.tab,
                options = state.availableTypes.map { tab -> tab to "${tab.label} ${state.tabCounts.of(tab)}" },
                onSelect = onTabChange,
                modifier = Modifier.padding(horizontal = GySpace.pageH, vertical = GySpace.xs),
            )
        }
        // 有结果但有错（Folia SearchWorkspace.tsx:112-120 的底部错误条）：不挡结果，给一个重试。
        if (state.failure != null) {
            FailureBanner(res = res, failure = state.failure, onRetry = onRetry)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.tab) {
                SearchTab.TRACKS -> TrackList(res, state.rows, onPlay, onDownload, onAddToSheet)
                SearchTab.ALBUMS -> AlbumGrid(res, state.albums, onOpenAlbum)
                SearchTab.SHEETS -> AlbumGrid(res, state.sheets, onOpenAlbum)
                SearchTab.ARTISTS -> ArtistGrid(res, state.artists, onOpenArtist)
            }
        }
    }
}

/**
 * 单曲结果列表。
 *
 * 一行一卡（`SearchResultRow.tsx:78-151`）：`rounded-2xl` 卡片、1dp 描边、48dp 圆角 12dp 封面、
 * 两行文字（歌名 16sp/600 一行省略 + 「歌手 · 专辑」12sp）、右侧常驻「加入歌单」按钮。
 * 整行点击 = 播放（Folia 是 hover 封面出播放键，触屏改成整行）。
 *
 * 触控：行最小高度 68dp；右侧按钮 48dp 见方。
 */
@Composable
private fun TrackList(
    res: SearchStrings,
    rows: List<SearchRow>,
    onPlay: (Int) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GySpace.pageH, end = GySpace.pageH, top = GySpace.xxs, bottom = GySpace.l),
        verticalArrangement = Arrangement.spacedBy(GySpace.s),
    ) {
        itemsIndexed(rows, key = { _, row -> "${row.pluginId}:${row.item.id}" }) { index, row ->
            ResultCard(
                res = res,
                row = row,
                onClick = { onPlay(index) },
                onDownload = { onDownload(row.item) },
                onAddToSheet = { onAddToSheet(row.item) },
            )
        }
    }
}

@Composable
private fun ResultCard(
    res: SearchStrings,
    row: SearchRow,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onAddToSheet: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(GyRadius.card)
    val item = row.item
    val subtitle = listOfNotNull(
        item.displayArtist.orEmpty().takeIf { it.isNotBlank() },
        item.album?.takeIf { it.isNotBlank() && it != item.title },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(if (pressed) Gyp.fill2 else Gyp.card)
            .border(1.dp, Gyp.cardRing, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = GySpace.l, vertical = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GySpace.l),
    ) {
        Cover(url = item.artwork, size = 48)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = (GyText.row.value + 1).sp,
                lineHeight = GyText.rowHeight,
                fontWeight = FontWeight.SemiBold,
                color = Gyp.ink,
                // 中文长标题折两行再省略（Folia 桌面是 truncate 一行，手机放宽到两行）。
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    fontSize = GyText.hint,
                    lineHeight = GyText.hintHeight,
                    color = Gyp.t2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(GySpace.xs))
                SourceBadge(label = row.platformLabel)
            }
        }
        // Folia 的 ＋（加入播放队列）：触屏常驻，48dp 触控。
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickableNoRipple(onAddToSheet),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = GuangYueIcons.Plus,
                contentDescription = stringResource(res.addToPlaylist),
                tint = Gyp.t2,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickableNoRipple(onDownload),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = GuangYueIcons.Download,
                contentDescription = stringResource(res.download),
                tint = Gyp.t3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 来源角标：Folia `span rounded-full border border-current/10 px-2 py-0.5 text-[10px]`。手机 12sp 下限。 */
@Composable
private fun SourceBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Gyp.tint)
            .padding(horizontal = GySpace.xs, vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = Gyp.t3,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumGrid(
    res: SearchStrings,
    albums: List<AlbumItem>,
    onOpen: (MusicItem) -> Unit,
) {
    if (albums.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(GySpace.xl),
            contentAlignment = Alignment.TopCenter,
        ) {
            FoliaEmptyState(
                title = stringResource(res.emptyCategory),
                subtitle = stringResource(res.emptySubtitle),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GySpace.pageH,
            end = GySpace.pageH,
            top = GySpace.xxs,
            bottom = GySpace.l,
        ),
        verticalArrangement = Arrangement.spacedBy(GySpace.s),
    ) {
        items(albums, key = { "${it.platform}:${it.id}" }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(GyRadius.card))
                    .background(Gyp.card)
                    .border(1.dp, Gyp.cardRing, RoundedCornerShape(GyRadius.card))
                    .clickableNoRipple { onOpen(album.asMusicItem()) }
                    .padding(horizontal = GySpace.l, vertical = GySpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GySpace.l),
            ) {
                Cover(url = album.artwork, size = 48)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontSize = (GyText.row.value + 1).sp,
                        lineHeight = GyText.rowHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = Gyp.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (album.artist.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = album.artist,
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistGrid(
    res: SearchStrings,
    artists: List<ArtistItem>,
    onOpen: (MusicItem) -> Unit,
) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(GySpace.xl), contentAlignment = Alignment.TopCenter) {
            FoliaEmptyState(
                title = stringResource(res.emptyCategory),
                subtitle = stringResource(res.emptySubtitle),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GySpace.pageH,
            end = GySpace.pageH,
            top = GySpace.xxs,
            bottom = GySpace.l,
        ),
        verticalArrangement = Arrangement.spacedBy(GySpace.s),
    ) {
        items(artists, key = { "${it.platform}:${it.id}" }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(GyRadius.card))
                    .background(Gyp.card)
                    .border(1.dp, Gyp.cardRing, RoundedCornerShape(GyRadius.card))
                    .clickableNoRipple { onOpen(artist.asMusicItem()) }
                    .padding(horizontal = GySpace.l, vertical = GySpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GySpace.l),
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Gyp.tint)) {
                    if (artist.avatar != null) {
                        AsyncImage(
                            model = artist.avatar,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = GuangYueIcons.User,
                            contentDescription = null,
                            tint = Gyp.t3,
                            modifier = Modifier.align(Alignment.Center).size(20.dp),
                        )
                    }
                }
                Text(
                    text = artist.name,
                    fontSize = (GyText.row.value + 1).sp,
                    lineHeight = GyText.rowHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = Gyp.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 状态：初始 / 加载 / 空 / 失败 / 无音源
// ---------------------------------------------------------------------------------------------

/** 初始态：搜索历史 + 推荐搜索（Folia 桌面没有，广乐的入口区，沿用旧页面的两块）。 */
@Composable
private fun IdleBody(
    res: SearchStrings,
    history: List<String>,
    onPickHistory: (String) -> Unit,
    onPickKeyword: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SearchTags.Idle),
        contentPadding = PaddingValues(bottom = GySpace.l),
    ) {
        if (history.isNotEmpty()) {
            item("history-title") { FoliaSectionTitle(stringResource(res.history)) }
            item("history") { FlowChips(history, onPickHistory) }
        }
        item("suggest-title") { FoliaSectionTitle(stringResource(res.suggestions)) }
        item("suggest") { FlowChips(IdleKeywords, onPickKeyword) }
    }
}

/**
 * 加载中：Folia 是一个居中转圈的 Loader2（`h-9 w-9 opacity-45`）。
 * 手机额外给 3 条骨架行，让「正在搜」这件事在长列表里不至于只剩一个小圈。
 */
@Composable
private fun LoadingState(
    res: SearchStrings,
    query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GySpace.pageH).testTag(SearchTags.Loading),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GySpace.xl))
        Spinner(size = 28.dp, color = Gyp.t3)
        Spacer(Modifier.height(GySpace.s))
        Text(
            text = stringResource(res.searching),
            fontSize = GyText.hint,
            color = Gyp.t3,
            fontFamily = GuangYueDesign.Sans,
        )
        Spacer(Modifier.height(GySpace.l))
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(GyRadius.card))
                    .background(Gyp.card)
                    .padding(horizontal = GySpace.l, vertical = GySpace.s)
                    .padding(bottom = GySpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GySpace.l),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(GyRadius.card)).background(Gyp.tint))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GySpace.xs)) {
                    Box(Modifier.fillMaxWidth(0.62f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Gyp.tint))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Gyp.tint))
                }
            }
            Spacer(Modifier.height(GySpace.s))
        }
    }
}

/** 空结果：Folia `home.noResults` 一句小字居中（SearchWorkspace.tsx:110），这里补一句可以点的推荐。 */
@Composable
private fun EmptyState(
    res: SearchStrings,
    keywords: List<String>,
    onPickKeyword: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().testTag(SearchTags.Empty),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GySpace.xl * 2))
        FoliaEmptyState(
            title = stringResource(res.noResults),
            subtitle = stringResource(res.emptySubtitle),
        )
        Spacer(Modifier.height(GySpace.l))
        FlowChips(keywords.take(4), onPickKeyword)
    }
}

/** 失败：Folia `search.error` + 重试按钮（SearchWorkspace.tsx:99-108，居中、AlertCircle 32）。 */
@Composable
private fun FailureState(
    res: SearchStrings,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GySpace.xl).testTag(SearchTags.Failure),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GySpace.xl * 2))
        androidx.compose.material3.Icon(
            imageVector = GuangYueIcons.AlertCircle,
            contentDescription = null,
            tint = Gyp.danger,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(GySpace.m))
        Text(
            text = message,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = Gyp.t2,
            textAlign = TextAlign.Center,
            fontFamily = GuangYueDesign.Sans,
        )
        Spacer(Modifier.height(GySpace.m))
        FoliaButton(
            text = stringResource(res.retry),
            onClick = onRetry,
            variant = FoliaButtonVariant.Fill,
            icon = GuangYueIcons.RefreshCw,
        )
    }
}

/** 一个音源都没装：旧页面的空态 + 去插件管理的入口（旧页面只有一句话，没入口）。 */
@Composable
private fun NoSourceState(
    res: SearchStrings,
    onOpenSourceManager: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GySpace.xl).testTag(SearchTags.NoSource),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GySpace.xl * 2))
        FoliaEmptyState(
            title = stringResource(res.noSource),
            subtitle = stringResource(res.noSourceHint),
        )
        if (onOpenSourceManager != null) {
            Spacer(Modifier.height(GySpace.m))
            FoliaButton(
                text = stringResource(res.openPlugins),
                onClick = onOpenSourceManager,
                variant = FoliaButtonVariant.Fill,
                icon = GuangYueIcons.Plug,
            )
        }
    }
}

/** 有结果时的底部错误条（Folia SearchWorkspace.tsx:112-120 居中小字 + 重试）。 */
@Composable
internal fun FailureBanner(
    res: SearchStrings,
    failure: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH, vertical = GySpace.xxs)
            .clip(RoundedCornerShape(GyRadius.field))
            .background(Gyp.danger.copy(alpha = 0.12f))
            .padding(horizontal = GySpace.m, vertical = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GySpace.s),
    ) {
        Text(
            text = failure,
            fontSize = GyText.hint,
            lineHeight = GyText.hintHeight,
            color = Gyp.danger,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FoliaButton(
            text = stringResource(res.retry),
            onClick = onRetry,
            variant = FoliaButtonVariant.Quiet,
            icon = GuangYueIcons.RefreshCw,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 小零件
// ---------------------------------------------------------------------------------------------

/** 48dp 见方的封面：Folia `h-12 w-12 rounded-xl bg-zinc-500/15`。 */
@Composable
private fun Cover(url: String?, size: Int) {
    val shape = RoundedCornerShape(GyRadius.row)
    Box(Modifier.size(size.dp).clip(shape).background(Gyp.tint)) {
        if (url.isNullOrBlank()) {
            androidx.compose.material3.Icon(
                imageVector = GuangYueIcons.Music,
                contentDescription = null,
                tint = Gyp.t3,
                modifier = Modifier.align(Alignment.Center).size(20.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Folia 的 `Loader2 animate-spin`：一圈缺口环旋转（不用 Material 的转圈，保证截图可复现）。 */
@Composable
private fun Spinner(size: androidx.compose.ui.unit.Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinner-angle",
    )
    androidx.compose.foundation.Canvas(Modifier.size(size).rotate(angle)) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 280f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = this.size.minDimension / 8f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

// 状态里的 counts 快捷取法（U2 之前 SearchScreen 里的同名函数搬过来，保持旧用法可用）。
internal fun TabCounts.of(tab: SearchTab): Int = when (tab) {
    SearchTab.TRACKS -> tracks
    SearchTab.ALBUMS -> albums
    SearchTab.SHEETS -> sheets
    SearchTab.ARTISTS -> artists
}

// 保留旧页面的内部帮助函数（app 模块的旧截图测试还在用）。
internal fun AlbumItem.asMusicItem(): MusicItem = MusicItem(
    platform = platform,
    id = id,
    title = title,
    artist = artist,
    album = title,
    artwork = artwork,
    albumId = id,
    type = "album",
)

internal fun ArtistItem.asMusicItem(): MusicItem = MusicItem(
    platform = platform,
    id = id,
    title = name,
    artist = name,
    artwork = avatar,
    artistId = id,
    type = "artist",
)



















