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
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.em
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
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons

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
    /** 11-18：Folia 搜索行的「加入播放队列」（Plus）；null 不显示。 */
    onEnqueue: ((MusicItem) -> Unit)? = null,
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
    onClose: (() -> Unit)? = null,
    onLoadMore: () -> Unit = {},
) {
    val phase = remember(state) { state.pagePhase() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 11-15 真机对照 Folia 手机版（APK）：搜索是纯深色整页，不铺大理石纹。
            .background(Gyp.page)
            .testTag(SearchTags.Root),
    ) {
        SearchHeader(
            res = res,
            query = state.query,
            loading = phase == SearchPagePhase.Loading,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = { onQueryChange("") },
            onClose = onClose ?: { onQueryChange("") },
            // 11-18：横屏高度只有 ~360dp，进页就弹键盘会把历史/结果整片盖住；横屏改为点框再弹。
            autoFocus = !state.searched &&
                androidx.compose.ui.platform.LocalConfiguration.current.orientation !=
                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
        )

        // 11-18 用户：主页右下已能换音源，搜索页不再放音源条，搜索跟主页当前音源走；
        // 从本地页签进来（搜索本地歌曲）就只搜本地。
        if (false) SourceRail(
            available = state.available,
            sourceId = state.sourceId,
            local = state.local,
            onPickSource = onPickSource,
        )

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
                    onEnqueue = onEnqueue,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onTabChange = onTabChange,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
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
    const val KindTabs = "search-kind-tabs"
    const val Meta = "search-meta"
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
    onClose: () -> Unit,
    /** 11-18：已有结果（返回本页 / 横屏）时不自动弹键盘，免得键盘盖住结果；空页进来仍聚焦。 */
    autoFocus: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    // 进页即聚焦，避免首字被吞（11-15 模拟器打 jay 只进了 ay）。
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (autoFocus) runCatching { focusRequester.requestFocus() } }
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
                        .focusRequester(focusRequester)
                        .testTag(SearchTags.Field),
                )
            }
        }
        Spacer(Modifier.width(GySpace.s))
        // Folia 手机版：输入框右侧独立的圆形关闭钮，点了回首页。
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Gyp.tint)
                .clickableNoRipple(onClose),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = GuangYueIcons.X,
                contentDescription = stringResource(res.clear),
                tint = Gyp.ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 音源选择（Folia SearchWorkspace.tsx:79-95 的一排胶囊）
// ---------------------------------------------------------------------------------------------

/**
 * 音源条：照 Folia SearchTabs `.search-sources`——纯文字页签（13sp 粗体、字距 0.06em、上 6 下 9），
 * 间距 22dp，选中 = 墨色字 + 底下 3dp 强调色条（换来源时色条淡入）；未选中 = 淡墨字。外层 48dp 触控高度。
 */
@Composable
private fun SourceRail(
    available: List<PluginMeta>,
    sourceId: String,
    local: Boolean = false,
    onPickSource: (String) -> Unit,
) {
    val railState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 从本地页签进来：把「本地」滚进视野（Folia 截图里它在最右）。
    androidx.compose.runtime.LaunchedEffect(Unit) { if (local && available.isNotEmpty()) railState.scrollToItem(available.size) }
    LazyRow(
        state = railState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = GySpace.pageH),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(available, key = { it.id }) { plugin ->
            SourceChip(
                label = platformLabelOf(plugin),
                selected = !local && plugin.id == sourceId,
                onClick = { onPickSource(plugin.id) },
            )
        }
        item(key = LOCAL_SOURCE) {
            SourceChip(label = "本地", selected = local, onClick = { onPickSource(LOCAL_SOURCE) })
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val mark by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "src-mark")
    val ink = Gyp.ink
    val soft = Gyp.t2
    val accent = Gyp.accent
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickableNoRipple { if (!selected) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .drawBehind {
                    if (mark > 0f) {
                        val h = 3.dp.toPx()
                        drawRect(accent.copy(alpha = accent.alpha * mark), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
                    }
                }
                .padding(top = 6.dp, bottom = 9.dp),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.06.em,
            color = if (selected) ink else soft,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Folia `search-meta`：「共 N 条」的 N；还能往下翻时带「+」。 */
internal fun searchCountLabel(count: Int, more: Boolean): String = "共 $count${if (more) "+" else ""} 条"

/**
 * 状态行：照 Folia `.search-meta`——来源名 + 「共 N 条」，等宽 11sp 600、字距 0.1em、淡墨，
 * 后面一道 1dp 发丝线延伸到右边。
 */
@Composable
private fun SearchMetaLine(source: String, count: Int, more: Boolean) {
    val soft = Gyp.t2
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GySpace.pageH, vertical = 8.dp).testTag(SearchTags.Meta),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val style = androidx.compose.ui.text.TextStyle(
            fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 0.1.em,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = soft,
        )
        if (source.isNotEmpty()) Text(source, style = style, maxLines = 1)
        if (count > 0) Text(searchCountLabel(count, more), style = style, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(Gyp.ink.copy(alpha = 0.09f)))
    }
}

/**
 * 种类页签：照 Folia SearchTabs `.search-kinds`（手机断点）——编号「01」等宽 11sp + 大字 18sp 900 粗，
 * 间距 14dp、左留 16dp，选中前面一个 11dp 实心强调色方块（换种类时滑过去）。只有一种时不画。
 */
@Composable
private fun SearchKindTabs(tabs: List<SearchTab>, tab: SearchTab, onSelect: (SearchTab) -> Unit) {
    val ink = Gyp.ink
    val soft = Gyp.t2
    val accent = Gyp.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = GySpace.pageH)
            .testTag(SearchTags.KindTabs),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        tabs.forEachIndexed { index, item ->
            val active = item == tab
            val mark by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, tween(220), label = "kind-mark")
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickableNoRipple { if (!active) onSelect(item) }
                    .drawBehind {
                        if (mark > 0f) {
                            val side = 11.dp.toPx() * mark
                            drawRect(accent.copy(alpha = accent.alpha * mark), topLeft = androidx.compose.ui.geometry.Offset((11.dp.toPx() - side) / 2f, size.height / 2f - side / 2f + 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(side, side))
                        }
                    }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = String.format(java.util.Locale.ROOT, "%02d", index + 1),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 0.08.em,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = (if (active) ink else soft).copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = item.label,
                    fontSize = 18.sp,
                    lineHeight = 18.sp,
                    letterSpacing = (-0.01).em,
                    fontWeight = FontWeight.Black,
                    color = if (active) ink else soft,
                    maxLines = 1,
                )
            }
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
    /** 11-18：Folia 搜索行的「加入播放队列」（Plus）；null 不显示。 */
    onEnqueue: ((MusicItem) -> Unit)? = null,
    onOpenAlbum: (MusicItem) -> Unit,
    onOpenArtist: (MusicItem) -> Unit,
    onTabChange: (SearchTab) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().testTag(SearchTags.Results)) {
        if (state.availableTypes.size > 1) {
            SearchKindTabs(tabs = state.availableTypes, tab = state.tab, onSelect = onTabChange)
        }
        SearchMetaLine(
            source = if (state.local) "本地" else state.selected?.let { platformLabelOf(it) }.orEmpty(),
            count = if (state.loading) 0 else state.tabCounts.of(state.tab),
            more = state.tab == SearchTab.TRACKS && !state.musicEnd,
        )
        // 有结果但有错（Folia SearchWorkspace.tsx:112-120 的底部错误条）：不挡结果，给一个重试。
        if (state.failure != null) {
            FailureBanner(res = res, failure = state.failure, onRetry = onRetry)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.tab) {
                SearchTab.TRACKS -> TrackList(res, state.rows, onPlay, onDownload, onAddToSheet, onEnqueue = onEnqueue, loadingMore = state.loadingMore, end = state.musicEnd, onLoadMore = onLoadMore)
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
    onEnqueue: ((MusicItem) -> Unit)? = null,
    loadingMore: Boolean = false,
    end: Boolean = true,
    onLoadMore: () -> Unit = {},
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val nearEnd by remember(rows.size) {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            rows.isNotEmpty() && last >= rows.size - 3
        }
    }
    androidx.compose.runtime.LaunchedEffect(nearEnd, end, rows.size) { if (nearEnd && !end) onLoadMore() }
    // Folia search.css `.search-list:not([data-search-entered]) .search-row`：一组新结果到时逐行从左滑进
    // （每行晚 32ms，前 12 行封顶），之后翻页 / 滚出来的行不再动。以第一行的键认「新的一组」。
    val enterKey = rows.firstOrNull()?.let { "${it.pluginId}:${it.item.id}" }
    var entered by remember(enterKey) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(enterKey) {
        kotlinx.coroutines.delay(ROW_ENTER_MS + 12L * ROW_ENTER_STAGGER_MS)
        entered = true
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Folia .search-list：行贴满列表宽（行自己带 18 / 14 内边距与发丝线），行间无空隙。
        contentPadding = PaddingValues(top = GySpace.xxs, bottom = GySpace.l),
    ) {
        itemsIndexed(rows, key = { _, row -> "${row.pluginId}:${row.item.id}" }) { index, row ->
            ResultCard(
                res = res,
                row = row,
                onClick = { onPlay(index) },
                onDownload = { onDownload(row.item) },
                onAddToSheet = { onAddToSheet(row.item) },
                onEnqueue = onEnqueue?.let { f -> { f(row.item) } },
                enterOrder = if (entered) -1 else index,
            )
        }
        if (rows.isNotEmpty() && (loadingMore || !end)) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(vertical = GySpace.m), contentAlignment = Alignment.Center) {
                    Spinner(18.dp, Gyp.t3)
                }
            }
        }
    }
}

/**
 * 11-18：照 folia-sonnet `components/app/search/SearchResultRow.tsx` + `search.css`（.search-row）：
 * 平铺行（无卡片底、圆角），行高 76，下沿 1px 发丝线（ink 9%），左右内边距 18 / 14，间距 10（≤639px）；
 * 窄屏不显示序号与时长；方角 48 封面；歌名 15 粗体一行省略；第二行 12sp「歌手 / 专辑」（斜杠 40% 透明）；
 * 行尾按钮顺序 收藏到歌单（Star）→ 加入播放队列（Plus），图标 16、60% 透明。
 * ✋ 触控：Folia 窄屏按钮 30px，这里视觉 16dp 图标、命中 44dp；✋ 广乐多一颗下载（在线结果才有）；
 * ✋ 速记簿（NotebookPen）广乐没有，不放。来源角标去掉（Folia 行里没有，来源在上方音源条）。
 */
@Composable
private fun ResultCard(
    res: SearchStrings,
    row: SearchRow,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onAddToSheet: () -> Unit,
    onEnqueue: (() -> Unit)? = null,
    /** ≥0：这一组结果刚到，按序号错开滑进来；-1 不动。 */
    enterOrder: Int = -1,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // .search-row::before：左缘 3dp 强调色竖条（上下各让 10），按住时从下往上长出来（260ms）。
    val bar by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, tween(260), label = "rowBar")
    val accent = Gyp.accent
    val enter = remember { androidx.compose.animation.core.Animatable(if (enterOrder >= 0) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(enterOrder.coerceAtMost(12) * ROW_ENTER_STAGGER_MS)
            enter.animateTo(1f, tween(ROW_ENTER_MS.toInt(), easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
        }
    }
    val item = row.item
    val artist = item.displayArtist.orEmpty().takeIf { it.isNotBlank() }
    val album = item.album?.takeIf { it.isNotBlank() }
    val hairline = Gyp.ink.copy(alpha = 0.09f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .graphicsLayer {
                // search-row-in：from opacity 0、translateX(-22px)。
                alpha = enter.value
                translationX = (1f - enter.value) * -22.dp.toPx()
            }
            .background(if (pressed) Gyp.ink.copy(alpha = 0.04f) else Color.Transparent)
            .drawBehind {
                drawRect(hairline, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                if (bar > 0f) {
                    val inset = 10.dp.toPx()
                    val h = (size.height - inset * 2) * bar
                    drawRect(accent, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - inset - h), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), h))
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            Cover(url = item.artwork, size = 48, square = true)
            // .search-row-play：按住时封面上盖 45% 黑 + 白色播放三角 18。
            if (pressed) Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(GuangYueIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gyp.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist != null || album != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (artist != null) Text(
                        text = artist, fontSize = 12.sp, lineHeight = 16.sp, color = Gyp.t2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (artist != null && album != null) Text(" / ", fontSize = 12.sp, lineHeight = 16.sp, color = Gyp.t2.copy(alpha = 0.4f))
                    if (album != null) Text(
                        text = album, fontSize = 12.sp, lineHeight = 16.sp, color = Gyp.t2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                }
            }
        }
        RowAction(GuangYueIcons.Star, stringResource(res.addToPlaylist), onAddToSheet)
        if (onEnqueue != null) RowAction(GuangYueIcons.Plus, "加入播放队列", onEnqueue)
        // 本地歌已经在手机里，Folia 本地结果没有下载。
        if (row.pluginId != LOCAL_SOURCE) RowAction(GuangYueIcons.Download, stringResource(res.download), onDownload)
    }
}

/** 行尾按钮（.search-row-action）：图标 16、60% 透明；命中 44dp。 */
@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Gyp.ink.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
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
        contentPadding = PaddingValues(top = GySpace.xxs, bottom = GySpace.l),
    ) {
        items(albums, key = { "${it.platform}:${it.id}" }) { album ->
            // 11-18：照 folia-sonnet SearchCollectionRow（专辑）：「歌手 / 年份」。
            val year = album.date?.let { Regex("(19|20)\\d{2}").find(it)?.value }
            CollectionRow(
                name = album.title,
                byline = listOfNotNull(album.artist.takeIf { it.isNotBlank() }, year),
                coverUrl = album.artwork,
                placeholder = GuangYueIcons.Disc,
                round = false,
                onClick = { onOpen(album.asMusicItem()) },
            )
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
        contentPadding = PaddingValues(top = GySpace.xxs, bottom = GySpace.l),
    ) {
        items(artists, key = { "${it.platform}:${it.id}" }) { artist ->
            // ✋ Folia 搜索没有歌手页签；广乐保留，行样式同集合行，头像圆形。
            CollectionRow(
                name = artist.name,
                byline = listOfNotNull(artist.fans?.takeIf { it.isNotBlank() }),
                coverUrl = artist.avatar,
                placeholder = GuangYueIcons.User,
                round = true,
                onClick = { onOpen(artist.asMusicItem()) },
            )
        }
    }
}

/**
 * 11-18：folia-sonnet SearchCollectionRow + search.css（.search-row / .search-collection-art）：平铺行 84dp、下沿发丝线、
 * 左 18 右 14、间距 10；58 方角封面（专辑背后的唱片、歌单背后的主题色影子只在悬停时露出，触屏不画）；
 * 名字 15 粗体一行，第二行 12sp 用「 / 」隔开（40% 透明），行尾 ArrowUpRight 18、35% 透明。窄屏不显示序号与曲目数。
 */
@Composable
private fun CollectionRow(
    name: String,
    byline: List<String>,
    coverUrl: String?,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector,
    round: Boolean,
    onClick: () -> Unit,
) {
    val hairline = Gyp.ink.copy(alpha = 0.09f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .drawBehind {
                drawRect(hairline, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
            .clickableNoRipple(onClick)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape).background(Gyp.ink.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isNullOrBlank()) {
                androidx.compose.material3.Icon(placeholder, null, tint = Gyp.ink.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
            } else {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Gyp.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (byline.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    byline.forEachIndexed { i, part ->
                        if (i > 0) Text(" / ", fontSize = 12.sp, lineHeight = 16.sp, color = Gyp.t2.copy(alpha = 0.4f))
                        Text(
                            part, fontSize = 12.sp, lineHeight = 16.sp, color = Gyp.t2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = if (i == 0) Modifier.weight(1f, fill = false) else Modifier,
                        )
                    }
                }
            }
        }
        androidx.compose.material3.Icon(GuangYueIcons.ArrowUpRight, null, tint = Gyp.ink.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
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
private fun Cover(url: String?, size: Int, square: Boolean = false) {
    val shape = if (square) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(GyRadius.row)
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




















/** Folia search.css search-row-in：520ms，每行错开 32ms。 */
private const val ROW_ENTER_MS = 520L
private const val ROW_ENTER_STAGGER_MS = 32L
