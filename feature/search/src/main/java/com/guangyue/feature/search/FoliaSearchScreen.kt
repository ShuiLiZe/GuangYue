package com.guangyue.feature.search

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons

// ---------------------------------------------------------------------------------------------
// 搜索页 · 照 Folia src/components/app/search（SearchWorkspace / SearchTabs / SearchResultRow / search.css）
//
// 2026-11-15 用户：旧页面（胶囊搜索框 + 圆角卡片列表 + 大理石底）完全不像 Folia。这里按 Folia 手机档重做：
//  - 纸色底（暗 #08080a / 亮 #fafafa），墨色 = 主题字色，强调色 = 主题强调色；
//  - 至上主义构图压在底下（手机 ≤640px：只留右上斜切色块、左下大空心圆、两条发丝线、竖排碎字，整体 0.35）；
//  - 搜索框：左边强调色小方块（搜索中转菱形呼吸）+ 22sp 粗体输入 + 2dp 墨色下划线；右边 44dp 方框 ✕；
//  - 来源页签：13sp 粗体，选中的下面 3dp 强调色条；种类页签：「01 单曲」18sp 900，选中前面 11dp 实心方块；
//  - 状态行：等宽小字「来源 · N 条结果」+ 延伸的发丝线；
//  - 列表平的：方角 48dp 封面、发丝分隔线、行尾三颗 30dp 按钮（收藏 / 加歌单 / 下载）；集合行 58dp 封面 + 后面错开的强调色影子；
//  - 加载：方块、圆、横条三个形依次起落；空 / 失败：空心圆被一根强调色斜杠划过 + 方角描边按钮。
// 手机差异：输入即搜（防抖）保留；行整行点击播放（没有 hover）。
// ---------------------------------------------------------------------------------------------

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
    onClose: (() -> Unit)? = null,
) {
    val phase = remember(state) { state.pagePhase() }
    val dark = Gyp.page.luminance() < 0.5f
    val paper = if (dark) Color(0xFF08080A) else Color(0xFFFAFAFA)
    val ink = Gyp.ink
    val accent = Gyp.accent
    val c = SearchColors(paper, ink, ink.copy(alpha = 0.55f), ink.copy(alpha = 0.09f), accent)

    Box(modifier.fillMaxSize().background(paper).testTag(SearchTags.Root)) {
        SearchComposition(c, state.tab, if (state.searched) state.query else "")
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SearchField(res, c, state.query, phase == SearchPagePhase.Loading, onQueryChange, onSearch, onClose)
                if (state.available.isNotEmpty() || state.availableTypes.size > 1) {
                    SearchTabsRow(c, state, onPickSource, onTabChange)
                }
                SearchMeta(c, state, phase)
            }
            Box(
                Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth().background(paper.copy(alpha = 0.88f)),
            ) {
                when (phase) {
                    SearchPagePhase.NoSource -> VoidState(c, stringResource(res.noSource), stringResource(res.openPlugins), onOpenSourceManager, SearchTags.NoSource)
                    SearchPagePhase.Idle -> IdleBody(res, c, state.history, onPickHistory) { onQueryChange(it); onSearch() }
                    SearchPagePhase.Loading -> Loader(c)
                    SearchPagePhase.Failure -> VoidState(c, state.failure ?: "搜索失败", stringResource(res.retry), onRetry, SearchTags.Failure)
                    SearchPagePhase.Empty -> VoidState(c, stringResource(res.noResults), null, null, SearchTags.Empty)
                    SearchPagePhase.Results -> ResultsBody(res, c, state, onPlay, onDownload, onAddToSheet, onOpenAlbum, onOpenArtist, onRetry)
                }
            }
            if (miniPlayer != null || bottomBar != null) {
                Column(Modifier.fillMaxWidth()) {
                    miniPlayer?.invoke()
                    bottomBar?.invoke()
                }
            }
        }
    }
}

internal data class SearchColors(val paper: Color, val ink: Color, val inkSoft: Color, val hairline: Color, val accent: Color)

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

/** 初始态的推荐关键词（Folia 没有这一块，广乐自拟入口；样式按 Folia 列表做成平的行）。 */
internal val IdleKeywords = listOf("热歌", "华语流行", "摇滚", "纯音乐", "粤语", "爵士", "OST")

// ---------------------------------------------------------------------------------------------
// 构图
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchComposition(c: SearchColors, tab: SearchTab, word: String) {
    // 种类换站位（Folia data-search-kind）：专辑把色块挪到左下、圆到右上；歌单把色块拉成右边竖条。
    val k = when (tab) { SearchTab.ALBUMS -> 1f; SearchTab.SHEETS -> 2f; else -> 0f }
    val p by animateFloatAsState(k, tween(900, easing = FastOutSlowInEasing), label = "kind")
    Box(Modifier.fillMaxSize().alpha(0.35f)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val album = (1f - kotlin.math.abs(p - 1f)).coerceIn(0f, 1f)
            val sheet = (p - 1f).coerceIn(0f, 1f)
            // 斜切色块：右上，polygon(22% 0, 100% 0, 100% 64%, 48% 82%)。
            val sw = minOf(w * 0.4f, 620.dp.toPx()).coerceAtLeast(w * 0.55f)
            val sh = minOf(h * 0.52f, 520.dp.toPx()) * 0.7f
            val left = w + w * 0.04f - sw
            val top = -h * 0.06f
            val dx = -w * 0.58f * album + w * 0.06f * sheet
            val dy = h * 0.62f * album + h * 0.12f * sheet
            val sx = 1f - 0.5f * sheet
            val path = Path().apply {
                moveTo(left + sw * 0.22f * sx, top)
                lineTo(left + sw, top)
                lineTo(left + sw, top + sh * 0.64f * (1f + 0.5f * sheet))
                lineTo(left + sw * (1f - 0.52f * sx), top + sh * 0.82f * (1f + 0.5f * sheet))
                close()
            }
            rotate(180f * album, Offset(left + sw / 2 + dx, top + sh / 2 + dy)) {
                translate(dx, dy) { drawPath(path, c.accent.copy(alpha = 0.92f)) }
            }
            // 大空心圆：左下 56vh。
            val d = h * 0.56f * (1f + 0.15f * album - 0.45f * sheet)
            val cx = -h * 0.10f + d / 2 + (w - h * 0.3f) * album
            val cy = h + h * 0.14f - d / 2 - h * 0.64f * album
            drawCircle(c.ink.copy(alpha = 0.22f), d / 2, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            // 两条发丝线。
            rotate(-9f * (1f - sheet), Offset(0f, h * 0.28f)) {
                drawLine(c.ink.copy(alpha = 0.14f), Offset(-w * 0.1f, h * 0.28f), Offset(w * 1.1f, h * 0.28f), 1f)
            }
            rotate(4f * (1f - sheet), Offset(0f, h * 0.72f)) {
                drawLine(c.ink.copy(alpha = 0.14f), Offset(-w * 0.1f, h * (0.72f - 0.06f * sheet)), Offset(w * 1.1f, h * (0.72f - 0.06f * sheet)), 1f)
            }
        }
        if (word.isNotBlank()) {
            // 竖排碎字：正在搜的词，极淡，贴左边（opacity 0.05；整体再 ×0.35 太淡，这里单独给 0.12）。
            Column(Modifier.padding(start = 6.dp, top = 96.dp).alpha(0.4f)) {
                word.take(8).forEach { ch ->
                    Text(ch.toString(), color = c.ink.copy(alpha = 0.3f), fontSize = 84.sp, fontWeight = FontWeight.Black, lineHeight = 84.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 搜索框
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchField(
    res: SearchStrings,
    c: SearchColors,
    query: String,
    busy: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.weight(1f).drawBottomLine(c.ink, 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldMark(c.accent, busy)
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (query.isEmpty()) {
                    Text(stringResource(res.placeholder), color = c.inkSoft, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(SearchTags.Field),
                )
            }
        }
        // 44dp 方框 ✕：有字时清空，没字时回首页（Folia：回首页）。
        Box(
            Modifier.size(44.dp).border(1.5.dp, c.ink).background(c.paper)
                .clickable { if (query.isNotEmpty() || onClose == null) onQueryChange("") else onClose() }
                .testTag(SearchTags.Clear),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(GuangYueIcons.X, null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.drawBottomLine(color: Color, width: androidx.compose.ui.unit.Dp) = this.drawBehind {
    val px = width.toPx()
    drawLine(color, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
}

@Composable
private fun FieldMark(accent: Color, busy: Boolean) {
    val t = rememberInfiniteTransition(label = "mark")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val rot = if (busy) 180f * a else 0f
    val sc = if (busy) 1f - 0.3f * kotlin.math.sin(Math.PI * a).toFloat() else 1f
    Box(Modifier.size(12.dp).rotate(rot).graphicsScale(sc).background(accent))
}

private fun Modifier.graphicsScale(s: Float) = this.graphicsLayer { scaleX = s; scaleY = s }

// ---------------------------------------------------------------------------------------------
// 来源 / 种类页签、状态行
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchTabsRow(c: SearchColors, state: SearchUiState, onPickSource: (String) -> Unit, onTabChange: (SearchTab) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.available.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                state.available.forEach { p ->
                    val on = p.id == state.sourceId
                    Column(Modifier.clickable { if (!on) onPickSource(p.id) }.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                        Text(
                            platformLabelOf(p), color = if (on) c.ink else c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp, bottom = 9.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) c.accent else Color.Transparent))
                    }
                }
            }
        }
        if (state.availableTypes.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                state.availableTypes.forEachIndexed { i, tab ->
                    val on = tab == state.tab
                    Box(Modifier.clickable { if (!on) onTabChange(tab) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                        if (on) Box(Modifier.offset(x = (-16).dp, y = 9.dp).size(11.dp).background(c.accent))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                (i + 1).toString().padStart(2, '0'), color = (if (on) c.ink else c.inkSoft).copy(alpha = 0.7f),
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp),
                            )
                            Text(tab.label, color = if (on) c.ink else c.inkSoft, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMeta(c: SearchColors, state: SearchUiState, phase: SearchPagePhase) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val count = state.tabCounts.of(state.tab)
        Text(state.sourceLabel.uppercase(), color = c.inkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
        if (phase == SearchPagePhase.Results && count > 0) {
            Text("$count 条结果", color = c.inkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
        }
        Box(Modifier.weight(1f).height(1.dp).background(c.hairline))
    }
}

// ---------------------------------------------------------------------------------------------
// 结果
// ---------------------------------------------------------------------------------------------

@Composable
private fun ResultsBody(
    res: SearchStrings,
    c: SearchColors,
    state: SearchUiState,
    onPlay: (Int) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
    onOpenAlbum: (MusicItem) -> Unit,
    onOpenArtist: (MusicItem) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag(SearchTags.Results)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                when (state.tab) {
                    SearchTab.TRACKS -> itemsIndexed(state.rows, key = { _, r -> "${r.pluginId}:${r.item.id}" }) { i, r ->
                        TrackRow(res, c, r.item, onClick = { onPlay(i) }, onOpenArtist = { onOpenArtist(r.item) }, onOpenAlbum = { onOpenAlbum(r.item) },
                            onAddToSheet = { onAddToSheet(r.item) }, onDownload = { onDownload(r.item) })
                    }
                    SearchTab.ALBUMS -> itemsIndexed(state.albums, key = { _, a -> "${a.platform}:${a.id}" }) { _, a ->
                        CollectionRow(c, a.title, a.artist, a.artwork, disc = true) { onOpenAlbum(a.asMusicItem()) }
                    }
                    SearchTab.SHEETS -> itemsIndexed(state.sheets, key = { _, a -> "${a.platform}:${a.id}" }) { _, a ->
                        CollectionRow(c, a.title, a.artist, a.artwork, disc = false) { onOpenAlbum(a.asMusicItem()) }
                    }
                    SearchTab.ARTISTS -> itemsIndexed(state.artists, key = { _, a -> "${a.platform}:${a.id}" }) { _, a ->
                        CollectionRow(c, a.name, a.fans?.let { "$it 粉丝" } ?: "歌手", a.avatar, disc = false, round = true) { onOpenArtist(a.asMusicItem()) }
                    }
                }
            }
        }
        if (state.failure != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(state.failure, color = c.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(16.dp))
                FlatButton(c, stringResource(res.retry), onRetry)
            }
        }
    }
}

@Composable
private fun TrackRow(
    res: SearchStrings,
    c: SearchColors,
    item: MusicItem,
    onClick: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onAddToSheet: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onClick).drawBottomLine(c.hairline, 1.dp).padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).background(c.ink.copy(alpha = 0.08f))) {
            val art = item.artwork
            if (!art.isNullOrBlank()) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.artist, color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenArtist))
                val album = item.album
                if (!album.isNullOrBlank()) {
                    Text("  /  ", color = c.inkSoft.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(album, color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenAlbum))
                }
            }
        }
        RowAction(c, GuangYueIcons.Star, onAddToSheet)
        RowAction(c, GuangYueIcons.Download, onDownload)
    }
}

@Composable
private fun RowAction(c: SearchColors, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, null, tint = c.ink.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CollectionRow(c: SearchColors, title: String, sub: String, art: String?, disc: Boolean, round: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = onClick).drawBottomLine(c.hairline, 1.dp).padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp)) {
            if (disc) {
                // 专辑封面后面藏一张唱片，露出一截。
                Canvas(Modifier.offset(x = 12.dp, y = 3.dp).size(52.dp)) {
                    drawCircle(c.ink.copy(alpha = 0.9f))
                    for (r in 1..6) drawCircle(c.ink.copy(alpha = 0.7f), size.minDimension / 2 * (0.3f + r * 0.1f), style = Stroke(1f))
                    drawCircle(c.accent, size.minDimension * 0.15f)
                }
            } else if (!round) {
                Box(Modifier.offset(6.dp, 6.dp).size(58.dp).background(c.accent))
            }
            Box(Modifier.size(58.dp).then(if (round) Modifier.clip(CircleShape) else Modifier).background(c.ink.copy(alpha = 0.08f))) {
                if (!art.isNullOrBlank()) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        androidx.compose.material3.Icon(GuangYueIcons.ArrowUpRight, null, tint = c.ink.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// 空闲、加载、空 / 失败
// ---------------------------------------------------------------------------------------------

@Composable
private fun IdleBody(res: SearchStrings, c: SearchColors, history: List<String>, onPickHistory: (String) -> Unit, onPickKeyword: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag(SearchTags.Idle), contentPadding = PaddingValues(bottom = 24.dp)) {
        fun section(title: String, words: List<String>, pick: (String) -> Unit) {
            item("t-$title") {
                Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(c.accent))
                    Spacer(Modifier.width(10.dp))
                    Text(title, color = c.inkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                }
            }
            itemsIndexed(words, key = { _, w -> "$title-$w" }) { i, w ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { pick(w) }.drawBottomLine(c.hairline, 1.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((i + 1).toString().padStart(2, '0'), color = c.inkSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Text(w, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Icon(GuangYueIcons.ArrowUpRight, null, tint = c.ink.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
                }
            }
        }
        if (history.isNotEmpty()) section("最近搜索", history.take(8), onPickHistory)
        section("试试这些", IdleKeywords, onPickKeyword)
    }
}

@Composable
private fun Loader(c: SearchColors) {
    val t = rememberInfiniteTransition(label = "loader")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "a")
    fun lift(delay: Float): Pair<Float, Float> {
        val x = ((a - delay) % 1f + 1f) % 1f
        val k = if (x < 0.4f) x / 0.4f else (1f - (x - 0.4f) / 0.6f)
        val e = kotlin.math.sin(k * Math.PI / 2).toFloat()
        return -14f * e to (0.35f + 0.65f * e)
    }
    Box(Modifier.fillMaxSize().testTag(SearchTags.Loading), contentAlignment = Alignment.Center) {
        Row(Modifier.height(36.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (y1, o1) = lift(0f); val (y2, o2) = lift(0.125f); val (y3, o3) = lift(0.25f)
            Box(Modifier.offset(y = y1.dp).alpha(o1).size(18.dp).background(c.accent))
            Box(Modifier.offset(y = y2.dp).alpha(o2).size(18.dp).clip(CircleShape).background(c.ink))
            Box(Modifier.offset(y = y3.dp).alpha(o3).width(42.dp).height(6.dp).background(c.ink))
        }
    }
}

@Composable
private fun VoidState(c: SearchColors, text: String, action: String?, onAction: (() -> Unit)?, tag: String) {
    Column(Modifier.fillMaxSize().testTag(tag).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(88.dp)) {
            drawCircle(c.ink.copy(alpha = 0.4f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            rotate(-32f) {
                drawLine(c.accent, Offset(-18.dp.toPx(), center.y), Offset(size.width + 18.dp.toPx(), center.y), 6.dp.toPx())
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(text, color = c.ink.copy(alpha = 0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            FlatButton(c, action, onAction)
        }
    }
}

@Composable
private fun FlatButton(c: SearchColors, text: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp).border(1.5.dp, c.ink).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(c.accent))
        Text(text, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

/** 有旧结果时的错误条（测试与旧调用点还在用这个名字）。 */
@Composable
internal fun FailureBanner(res: SearchStrings, failure: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(failure, color = Gyp.ink.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        FlatButton(SearchColors(Gyp.page, Gyp.ink, Gyp.inkSoft, Gyp.ink.copy(alpha = 0.09f), Gyp.accent), stringResource(res.retry), onRetry)
    }
}

internal fun TabCounts.of(tab: SearchTab): Int = when (tab) {
    SearchTab.TRACKS -> tracks
    SearchTab.ALBUMS -> albums
    SearchTab.SHEETS -> sheets
    SearchTab.ARTISTS -> artists
}

internal fun AlbumItem.asMusicItem(): MusicItem = MusicItem(
    platform = platform, id = id, title = title, artist = artist, album = title, artwork = artwork, albumId = id, type = "album",
)

internal fun ArtistItem.asMusicItem(): MusicItem = MusicItem(
    platform = platform, id = id, title = name, artist = name, artwork = avatar, artistId = id, type = "artist",
)
