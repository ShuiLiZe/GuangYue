package com.guangyue.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.feature.library.design.FoliaSegmented
import com.guangyue.feature.library.design.FoliaTextField
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/** 搜索结果的四个分类（m00161：单曲 / 专辑 / 歌单 / 歌手）。 */
enum class SearchTab(val label: String) {
    TRACKS("单曲"),
    ALBUMS("专辑"),
    SHEETS("歌单"),
    ARTISTS("歌手"),
}

/**
 * 搜索页（m00161）。
 *
 * 上：搜索框 + 音源切换（横向 chip，含「全部」）；下：结果按四个分类分段展示。
 * 未搜索时给出搜索历史与推荐关键词。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPickAll: () -> Unit,
    onPickPlatform: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onPickHistory: (String) -> Unit,
    onOpenAlbum: (MusicItem) -> Unit,
    onOpenArtist: (MusicItem) -> Unit,
    tab: SearchTab,
    onTabChange: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    miniPlayer: (@Composable () -> Unit)? = null,
) {
    val counts = state.tabCounts
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "搜索")

            FoliaTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "搜索歌曲、歌手、专辑",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GySpace.pageH),
                leading = {
                    Icon(
                        GuangYueIcons.Search,
                        null,
                        tint = Gyp.t3,
                        modifier = Modifier.size(16.dp),
                    )
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.query.isNotEmpty()) {
                            Icon(
                                GuangYueIcons.X,
                                "清空",
                                tint = Gyp.t3,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickableNoRipple { onQueryChange("") },
                            )
                            Spacer(Modifier.width(GySpace.s))
                        }
                        Text(
                            text = "搜索",
                            fontSize = GyText.label,
                            color = Gyp.ink,
                            fontFamily = GuangYueDesign.Sans,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickableNoRipple { onSearch() },
                        )
                    }
                },
            )

            Spacer(Modifier.height(GySpace.m))

            if (state.available.isEmpty()) {
                Spacer(Modifier.height(GySpace.xl))
                com.guangyue.feature.library.design.FoliaEmptyState(
                    title = "还没有可用的音源",
                    subtitle = "先在「我的 · 音源插件管理」里安装一个插件",
                    modifier = Modifier.padding(horizontal = GySpace.xl),
                )
                MiniSlot(bottomBar, miniPlayer)
                return@Column
            }

            // 音源切换：全部 + 每个已启用插件
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = GySpace.pageH),
                horizontalArrangement = Arrangement.spacedBy(GySpace.xs),
            ) {
                item(key = "__all__") {
                    SourcePill(
                        label = "全部",
                        selected = state.platforms.isEmpty(),
                        onClick = onPickAll,
                    )
                }
                items(state.available, key = { it.id }) { plugin ->
                    SourcePill(
                        label = platformLabelOf(plugin),
                        selected = state.platforms.contains(plugin.id),
                        onClick = { onPickPlatform(plugin.id) },
                    )
                }
            }

            Spacer(Modifier.height(GySpace.m))

            if (state.loading) {
                Box(
                    Modifier.fillMaxWidth().height(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        color = Gyp.ink,
                        trackColor = Gyp.tint,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }

            if (state.rows.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty()) {
                SearchDiscovery(
                    history = state.history,
                    onPickHistory = onPickHistory,
                    onPickKeyword = { onQueryChange(it); onSearch() },
                    query = state.query,
                    loading = state.loading,
                    errors = state.errors,
                )
            } else {
                FoliaSegmented(
                    value = tab,
                    options = listOf(
                        SearchTab.TRACKS to "单曲 ${counts.tracks}",
                        SearchTab.ALBUMS to "专辑 ${counts.albums}",
                        SearchTab.SHEETS to "歌单 ${counts.sheets}",
                        SearchTab.ARTISTS to "歌手 ${counts.artists}",
                    ),
                    onSelect = onTabChange,
                    modifier = Modifier.padding(horizontal = GySpace.pageH),
                )
                Spacer(Modifier.height(GySpace.s))

                Box(Modifier.weight(1f)) {
                    when (tab) {
                        SearchTab.TRACKS -> TrackResults(state, onPlay, onDownload, onAddToSheet)
                        SearchTab.ALBUMS -> AlbumGrid(state.albums, onOpenAlbum)
                        SearchTab.ARTISTS -> ArtistGrid(state.artists, onOpenArtist)
                        SearchTab.SHEETS -> SheetGrid(state.albums, onOpenAlbum)
                    }
                }
            }
        }
        MiniSlot(bottomBar, miniPlayer, alignBottom = true)
    }
}

/** 底栏与迷你播放条（同一容器，迷你条悬浮在上）。 */
@Composable
private fun MiniSlot(
    bottomBar: (@Composable () -> Unit)?,
    miniPlayer: (@Composable () -> Unit)?,
    alignBottom: Boolean = false,
) {
    if (bottomBar == null && miniPlayer == null) return
    Column(
        modifier = if (alignBottom) Modifier
            .fillMaxSize()
            .padding(top = 0.dp) else Modifier.fillMaxWidth(),
    ) {
        if (alignBottom) {
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth()) {
            miniPlayer?.invoke()
            bottomBar?.invoke()
        }
    }
}

/** 单曲结果列表。 */
@Composable
private fun TrackResults(
    state: SearchUiState,
    onPlay: (Int) -> Unit,
    onDownload: (MusicItem) -> Unit,
    onAddToSheet: (MusicItem) -> Unit,
) {
    val visible = state.visibleRows
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        itemsIndexed(visible, key = { _, row -> "${row.pluginId}:${row.item.id}" }) { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple { onPlay(index) }
                    .padding(horizontal = GySpace.pageH, vertical = GySpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(url = row.item.artwork, size = 44)
                Spacer(Modifier.width(GySpace.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.item.title,
                        fontSize = GyText.row,
                        lineHeight = GyText.rowHeight,
                        color = Gyp.ink,
                        fontFamily = GuangYueDesign.Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[${row.platformLabel}]",
                            fontSize = GyText.hint,
                            color = Gyp.t3,
                            fontFamily = GuangYueDesign.Sans,
                        )
                        Spacer(Modifier.width(GySpace.xxs))
                        Text(
                            text = row.item.displayArtist,
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    GuangYueIcons.Download,
                    "下载",
                    tint = Gyp.t3,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple { onDownload(row.item) }
                        .padding(GySpace.s),
                )
                Icon(
                    GuangYueIcons.Plus,
                    "加入歌单",
                    tint = Gyp.t3,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .clickableNoRipple { onAddToSheet(row.item) }
                        .padding(GySpace.s),
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "__empty__") {
                com.guangyue.feature.library.design.FoliaEmptyState(
                    title = "没有单曲结果",
                    subtitle = "换首歌名，或换一个音源",
                    modifier = Modifier.padding(vertical = GySpace.xl, horizontal = GySpace.xl),
                )
            }
        }
        items(state.errors, key = { "err:$it" }) { err ->
            Text(
                text = err,
                color = Gyp.danger,
                fontSize = GyText.hint,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(horizontal = GySpace.pageH, vertical = 2.dp),
            )
        }
    }
}

/** 专辑网格（两列，Folia 海报比例 1:1 + 两行文字）。 */
@Composable
private fun AlbumGrid(albums: List<AlbumItem>, onOpen: (MusicItem) -> Unit) {
    if (albums.isEmpty()) {
        com.guangyue.feature.library.design.FoliaEmptyState(
            title = "没有专辑结果",
            subtitle = "换个关键词试试",
            modifier = Modifier.padding(vertical = GySpace.xl, horizontal = GySpace.xl),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = GySpace.pageH,
            end = GySpace.pageH,
            bottom = 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(GySpace.m),
        verticalArrangement = Arrangement.spacedBy(GySpace.m),
    ) {
        items(albums, key = { "${it.platform}:${it.id}" }) { album ->
            Column(Modifier.clickableNoRipple { onOpen(album.asMusicItem()) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(GyRadius.field))
                        .background(Gyp.tint),
                ) {
                    Artwork(url = album.artwork, fill = true)
                }
                Spacer(Modifier.height(GySpace.s))
                Text(
                    text = album.title,
                    fontSize = GyText.label,
                    lineHeight = GyText.labelHeight,
                    color = Gyp.ink,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.artist,
                    fontSize = GyText.hint,
                    lineHeight = GyText.hintHeight,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 歌手网格。 */
@Composable
private fun ArtistGrid(artists: List<ArtistItem>, onOpen: (MusicItem) -> Unit) {
    if (artists.isEmpty()) {
        com.guangyue.feature.library.design.FoliaEmptyState(
            title = "没有歌手结果",
            subtitle = "换个关键词试试",
            modifier = Modifier.padding(vertical = GySpace.xl, horizontal = GySpace.xl),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = GySpace.pageH,
            end = GySpace.pageH,
            bottom = 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(GySpace.m),
        verticalArrangement = Arrangement.spacedBy(GySpace.m),
    ) {
        items(artists, key = { "${it.platform}:${it.id}" }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickableNoRipple { onOpen(artist.asMusicItem()) },
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(Gyp.tint),
                ) {
                    Artwork(url = artist.avatar, fill = true)
                }
                Spacer(Modifier.height(GySpace.xs))
                Text(
                    text = artist.name,
                    fontSize = GyText.label,
                    lineHeight = GyText.labelHeight,
                    color = Gyp.ink,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 歌单网格（沿用专辑网格的版式；插件返回的歌单与专辑同构）。 */
@Composable
private fun SheetGrid(sheets: List<AlbumItem>, onOpen: (MusicItem) -> Unit) {
    if (sheets.isEmpty()) {
        com.guangyue.feature.library.design.FoliaEmptyState(
            title = "没有歌单结果",
            subtitle = "换个关键词试试",
            modifier = Modifier.padding(vertical = GySpace.xl, horizontal = GySpace.xl),
        )
        return
    }
    AlbumGrid(sheets, onOpen)
}

/** 未搜索时的探索区：搜索历史 + 推荐关键词。 */
@Composable
private fun SearchDiscovery(
    history: List<String>,
    onPickHistory: (String) -> Unit,
    onPickKeyword: (String) -> Unit,
    query: String,
    loading: Boolean,
    errors: List<String>,
) {
    if (query.isNotEmpty() && !loading && errors.isEmpty()) {
        com.guangyue.feature.library.design.FoliaEmptyState(
            title = "没有结果",
            subtitle = "换个关键词，或换一个音源",
            modifier = Modifier.padding(top = GySpace.xl, start = GySpace.xl, end = GySpace.xl),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "history-title") {
                com.guangyue.feature.library.design.FoliaSectionTitle(text = "搜索历史")
            }
            item(key = "history") {
                com.guangyue.feature.library.design.FlowChips(
                    labels = history,
                    onClick = onPickHistory,
                )
            }
        }
        item(key = "hot-title") {
            com.guangyue.feature.library.design.FoliaSectionTitle(text = "推荐搜索")
        }
        item(key = "hot") {
            com.guangyue.feature.library.design.FlowChips(
                labels = listOf("热歌", "华语流行", "摇滚", "纯音乐", "粤语", "爵士", "OST"),
                onClick = onPickKeyword,
            )
        }
        items(errors, key = { "err:$it" }) { err ->
            Text(
                text = err,
                color = Gyp.danger,
                fontSize = GyText.hint,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(horizontal = GySpace.pageH, vertical = 2.dp),
            )
        }
    }
}

/** 音源切换胶囊（Folia 分段控件的单选形态）。 */
@Composable
private fun SourcePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(GyRadius.field))
            .background(if (selected) Gyp.raise else Gyp.tint, RoundedCornerShape(GyRadius.field))
            .clickableNoRipple(onClick)
            .padding(horizontal = GySpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = GyText.hint,
            color = if (selected) Gyp.ink else Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 方形封面（无图时画一层 tint 底）。 */
@Composable
private fun Artwork(url: String?, size: Int = 44, fill: Boolean = false) {
    val shape = RoundedCornerShape(GyRadius.field)
    Box(
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxSize() else Modifier.size(size.dp))
            .clip(if (fill) RoundedCornerShape(0.dp) else shape)
            .background(Gyp.tint),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                GuangYueIcons.Music,
                null,
                tint = Gyp.t3,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 把专辑结果转成可点击的 [MusicItem]（进专辑详情时复用同一套打开逻辑）。 */
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

/** 把歌手结果转成可点击的 [MusicItem]。 */
internal fun ArtistItem.asMusicItem(): MusicItem = MusicItem(
    platform = platform,
    id = id,
    title = name,
    artist = name,
    artwork = avatar,
    artistId = id,
    type = "artist",
)
