package com.guangyue.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.download.DownloadManager
import com.guangyue.core.data.repository.PlaylistRepository
import com.guangyue.core.data.repository.SearchHistoryRepository
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.player.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** One row of the result list, tagged with the plugin that produced it. */
data class SearchRow(
    val pluginId: String,
    val pluginName: String,
    /** 结果卡片上的音源角标，如 `[酷我]`。 */
    val platformLabel: String,
    val item: MusicItem,
)

/** 单个插件的搜索产出：要么拿到结果，要么给出一条可显示的原因。 */
private sealed interface SearchOutcome {
    data class Hits(
        val items: List<MusicItem>,
        val albums: List<com.guangyue.core.plugin.AlbumItem> = emptyList(),
        val artists: List<com.guangyue.core.plugin.ArtistItem> = emptyList(),
    ) : SearchOutcome

    data class Miss(val reason: String) : SearchOutcome
}

/** 从平台名里挑一个两字的短标（酷我 / 网易云 / QQ音乐 / 哔哩哔哩）。 */
internal fun platformLabelOf(plugin: PluginMeta): String {
    val raw = plugin.displayName
    val known = listOf(
        "酷我" to "酷我",
        "酷狗" to "酷狗",
        "网易" to "网易",
        "QQ" to "QQ",
        "qq" to "QQ",
        "B站" to "B站",
        "哔哩" to "B站",
        "bilibili" to "B站",
        "咪咕" to "咪咕",
        "百度" to "百度",
        "千千" to "千千",
        "汽水" to "汽水",
    )
    known.firstOrNull { raw.contains(it.first, ignoreCase = true) }?.let { return it.second }
    val cleaned = raw.removeSuffix("音乐").removeSuffix("插件").trim()
    return cleaned.take(4).ifBlank { plugin.platform.take(4) }
}

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    /** Enabled plugins the user narrowed the search to; empty = all. */
    val platforms: List<String> = emptyList(),
    val available: List<PluginMeta> = emptyList(),
    val rows: List<SearchRow> = emptyList(),
    /** 专辑 / 歌手结果（m00161 的分类结果页）。歌单结果插件侧暂以 album 类型返回。 */
    val albums: List<com.guangyue.core.plugin.AlbumItem> = emptyList(),
    val artists: List<com.guangyue.core.plugin.ArtistItem> = emptyList(),
    /** 「全部」筛选条：按插件聚合当前结果数量。 */
    val resultCounts: Map<String, Int> = emptyMap(),
    val errors: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val message: String? = null,
) {
    /** 当前音源筛选下的单曲行（选中单个音源时只留该音源）。 */
    val visibleRows: List<SearchRow>
        get() = if (platforms.isEmpty()) rows else rows.filter { it.pluginId in platforms }

    /** 四个分类 Tab 的计数，直接写在 Tab 标签里。 */
    val tabCounts: TabCounts get() = TabCounts(
        tracks = visibleRows.size,
        albums = albums.count { it.platform.isEmpty() || platforms.isEmpty() || it.platform in platforms },
        sheets = 0,
        artists = artists.count { it.platform.isEmpty() || platforms.isEmpty() || it.platform in platforms },
    )
}

/** 分类 Tab 的计数。 */
data class TabCounts(val tracks: Int, val albums: Int, val sheets: Int, val artists: Int)

/**
 * Search across every enabled plugin.
 *
 * The bridge fans out to all plugins and each one is isolated in its own runtime, so a broken
 * plugin only produces an error line, never a failed search.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bridge: MusicPluginBridge,
    private val manager: PluginManager,
    private val tracks: TrackRepository,
    private val playlists: PlaylistRepository,
    private val history: SearchHistoryRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    companion object {
        /** 同时在搜的插件上限。 */
        const val SEARCH_CONCURRENCY = 6
    }

    init {
        viewModelScope.launch {
            manager.plugins.collect { list ->
                val enabled = list.filter { it.enabled }
                _state.value = _state.value.copy(
                    available = enabled,
                    platforms = _state.value.platforms.filter { id -> enabled.any { it.id == id } },
                )
            }
        }
        viewModelScope.launch {
            history.history().collect { rows ->
                _state.value = _state.value.copy(history = rows.map { it.query })
            }
        }
    }

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
    }

    fun togglePlatform(id: String) {
        val current = _state.value.platforms
        val next = if (id in current) current - id else current + id
        _state.value = _state.value.copy(platforms = next)
    }

    /** 供「我的」页展示最近搜索；与 [search] 共用同一份历史记录。 */
    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errors = emptyList(), message = null)
            history.record(query)

            val enabled = _state.value.available
            val picked = _state.value.platforms
            val targets = if (picked.isEmpty()) enabled else enabled.filter { it.id in picked }

            // 并发控制：同时最多 SEARCH_CONCURRENCY 个插件在搜，其余排队。
            // 源站（酷我 / QQ / B站）对瞬时并发很敏感，一次把全部插件拍过去会被限流或掐连接。
            val gate = Semaphore(SEARCH_CONCURRENCY)
            val results = coroutineScope {
                targets
                    .map { plugin ->
                        async {
                            gate.withPermit { plugin to oneSearch(plugin, query) }
                        }
                    }
                    .awaitAll()
            }

            // 按插件顺序还原，保证同一音源的结果连续成块
            val rows = mutableListOf<SearchRow>()
            val albums = mutableListOf<com.guangyue.core.plugin.AlbumItem>()
            val artists = mutableListOf<com.guangyue.core.plugin.ArtistItem>()
            val errors = mutableListOf<String>()
            for ((plugin, outcome) in results) {
                when (outcome) {
                    is SearchOutcome.Hits -> {
                        outcome.items.forEach { item ->
                            rows += SearchRow(
                                pluginId = plugin.id,
                                pluginName = plugin.displayName,
                                platformLabel = platformLabelOf(plugin),
                                item = item,
                            )
                        }
                        albums += outcome.albums
                        artists += outcome.artists
                    }
                    is SearchOutcome.Miss -> errors += "${plugin.displayName}：${outcome.reason}"
                }
            }
            // Keep the cache warm so playback and lyrics can look the row up by key.
            runCatching { tracks.cache(rows.map { it.item }) }
            _state.value = _state.value.copy(
                loading = false,
                rows = rows,
                albums = albums.distinctBy { "${it.platform}:${it.id}" },
                artists = artists.distinctBy { "${it.platform}:${it.id}" },
                errors = errors,
                resultCounts = rows.groupingBy { it.pluginId }.eachCount(),
            )
        }
    }

    private suspend fun oneSearch(plugin: PluginMeta, query: String): SearchOutcome {
        val probe = runCatching { bridge.search(plugin.id, query, 1, SearchType.MUSIC) }
        val result = probe.getOrElse { t ->
            return SearchOutcome.Miss(t.message?.take(60) ?: "搜索失败")
        }
        val err = result.error
        return when {
            err != null && result.items.isEmpty() -> SearchOutcome.Miss(err)
            result.items.isEmpty() && result.albums.isEmpty() && result.artists.isEmpty() ->
                SearchOutcome.Miss("没有结果")
            else -> SearchOutcome.Hits(result.items, result.albums, result.artists)
        }
    }

    /** 「全部」/ 单个音源选择。传空串表示全部。 */
    fun selectPlatform(id: String) {
        _state.value = if (id.isEmpty()) {
            _state.value.copy(platforms = emptyList())
        } else {
            _state.value.copy(platforms = listOf(id))
        }
    }

    /** 下载一条搜索结果。 */
    fun download(item: MusicItem, quality: String = "high") {
        viewModelScope.launch {
            val ok = runCatching {
                downloads.enqueue(item, com.guangyue.core.player.Quality.fromWire(quality))
            }.getOrDefault(false)
            _state.value = _state.value.copy(message = if (ok) "已加入下载队列" else "该歌曲已在队列或已下载")
        }
    }

    /** Play from [index]; the caller decides where the queue goes. */
    fun playFrom(index: Int, start: (List<QueueItem>, Int) -> Unit) {
        val rows = _state.value.rows
        if (rows.isEmpty()) return
        val items = rows.map { row ->
            val entity = TrackMapper.fromPlugin(row.item)
            QueueItem(
                key = entity.key,
                title = row.item.title,
                artist = row.item.displayArtist,
                album = row.item.album,
                artworkUri = row.item.artwork,
                rawJson = row.item.raw?.toString() ?: row.item.toJson().toString(),
                origin = QueueItem.of(entity).origin,
            )
        }
        start(items, index.coerceIn(0, items.lastIndex))
    }

    /** Add a result to the first playlist, creating 「我的收藏」 on demand. */
    fun addToCurrentSheet(item: MusicItem) {
        viewModelScope.launch {
            val existing = playlists.playlists().firstOrNull()?.firstOrNull()?.id
            val id = existing ?: playlists.create("我的收藏")
            playlists.addTracks(id, listOf(item))
            _state.value = _state.value.copy(message = "已加入歌单")
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * 打开专辑 / 歌手：Folia 里点专辑是「用这张专辑的曲目替换队列」。
     * 广乐暂不内置专辑详情页，这里按专辑名再搜一次并用第一首起播，保持「点了就有反馈」。
     */
    fun openAlbum(album: MusicItem) {
        _state.value = _state.value.copy(query = album.title)
        search()
    }

    fun openArtist(artist: MusicItem) {
        _state.value = _state.value.copy(query = artist.title)
        search()
    }
}
