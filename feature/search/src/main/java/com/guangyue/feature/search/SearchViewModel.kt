package com.guangyue.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.data.repository.PlaylistRepository
import com.guangyue.core.data.repository.SearchHistoryRepository
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.download.DownloadManager
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import com.guangyue.core.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the result list, tagged with the plugin that produced it. */
data class SearchRow(
    val pluginId: String,
    val pluginName: String,
    /** 结果卡片上的音源角标，如 `[酷我]`。 */
    val platformLabel: String,
    val item: MusicItem,
)

/** 从平台名里挑一个两字的短标（酷我 / 网易云 / QQ音乐 / 哔哩哔哩）。 */
internal fun platformLabelOf(plugin: PluginMeta): String {
    val raw = plugin.displayName
    val platform = plugin.platform
    // 中文名先按关键词认；插件名是空的（只写了 platform）时按 platform 的英文 slug 认，
    // 否则「kugou」这种会被截成「kugo」，选择器上四个音源看起来全是乱码。
    val known = listOf(
        "酷我" to "酷我", "kuwo" to "酷我",
        "酷狗" to "酷狗", "kugou" to "酷狗",
        "网易" to "网易", "netease" to "网易",
        "QQ" to "QQ", "qq" to "QQ",
        "B站" to "B站", "哔哩" to "B站", "bilibili" to "B站",
        "咪咕" to "咪咕", "migu" to "咪咕",
        "百度" to "百度", "baidu" to "百度",
        "千千" to "千千", "qianqian" to "千千",
        "汽水" to "汽水", "qishui" to "汽水",
    )
    known.firstOrNull { raw.contains(it.first, ignoreCase = true) }?.let { return it.second }
    known.firstOrNull { platform.contains(it.first, ignoreCase = true) }?.let { return it.second }
    val cleaned = raw.removeSuffix("音乐").removeSuffix("插件").trim()
    return cleaned.take(4).ifBlank { platform.take(4) }
}

/**
 * 搜索页状态。
 *
 * 0.0.7 起**只搜一个音源**：[sourceId] 指向用户选中的那一个插件，[search] 也只打它。
 * 0.0.5/0.0.6 是「并发打所有已启用插件」，导入 57 个插件后一次搜索会同时打 57 个源站，
 * 本机被限流、结果还混成一锅（用户根本分不清哪条来自哪个音源）。
 */
data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    /** 已启用、可被选中的音源（选择器的候选，不会全都发请求）。 */
    val available: List<PluginMeta> = emptyList(),
    /** 当前选中的音源 id；空串 = 没有可用音源。 */
    val sourceId: String = "",
    /** 当前选中的音源只支持哪几个 Tab（按它的 supportedSearchType 决定）。 */
    val availableTypes: List<SearchTab> = listOf(SearchTab.TRACKS),
    /** 当前 Tab。切到没加载过的类型时才发请求（懒加载）。 */
    val tab: SearchTab = SearchTab.TRACKS,
    val rows: List<SearchRow> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val sheets: List<AlbumItem> = emptyList(),
    /** 这一次搜索的失败原因（一句话，直接显示给用户）；成功时为 null。 */
    val failure: String? = null,
    /** 已有搜索结果（含空结果），用来区分「还没搜」与「搜了没东西」。 */
    val searched: Boolean = false,
    val history: List<String> = emptyList(),
    val message: String? = null,
) {
    /** 当前选中的音源。 */
    val selected: PluginMeta? get() = available.firstOrNull { it.id == sourceId }

    /** 选中音源的短标，搜索失败时拼进提示里。 */
    val sourceLabel: String get() = selected?.let { platformLabelOf(it) } ?: "音源"

    /** 四个分类 Tab 的计数。 */
    val tabCounts: TabCounts
        get() = TabCounts(
            tracks = rows.size,
            albums = albums.size,
            sheets = sheets.size,
            artists = artists.size,
        )
}

/** 分类 Tab 的计数。 */
data class TabCounts(val tracks: Int, val albums: Int, val sheets: Int, val artists: Int)

/**
 * 用户自选音源的搜索页（0.0.7）。
 *
 * 关键不变量：**同一时刻只有一个插件的 runtime 在跑搜索**。选择器只是候选列表，
 * 不会因为用户导入了一堆插件就把请求同时拍到所有源站上。
 *
 * 类型切换（单曲/专辑/歌手/歌单）只显示该插件 supportedSearchType 里有的那几个，
 * 切到没加载过的 Tab 才按需请求（懒加载）。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bridge: MusicPluginBridge,
    private val manager: PluginManager,
    private val tracks: TrackRepository,
    private val playlists: PlaylistRepository,
    private val history: SearchHistoryRepository,
    private val downloads: DownloadManager,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 已加载过结果的 Tab（懒加载判断：切到这个 Tab 时 query 没变就不再请求）。 */
    private val loadedTypes = mutableSetOf<String>()
    private var loadedQuery: String = ""

    init {
        // 0.0.7 修：以前只有插件管理页会调 manager.load()，搜索页直接进来时
        // PluginManager.plugins 还是初值空列表 → available 为空 → 点搜索永远走
        // 「还没有可用的音源」分支，装好插件也搜不到。这里必须自己拉一次。
        viewModelScope.launch { manager.load() }
        viewModelScope.launch {
            manager.plugins.collect { list ->
                // 0.0.8：搜索页顶部列出所有能搜的音源（已装、没装坏），
                // 但全局只有一个是「当前音源」——列表里排在最前，选中即切换。
                val selectable = list.filter { it.error == null && it.hasSearch }
                val active = list.firstOrNull { it.enabled && it.error == null && it.hasSearch }
                val keep = _state.value.sourceId.takeIf { id -> selectable.any { it.id == id } }
                    ?: prefs.lastSourceId.takeIf { id -> selectable.any { it.id == id } }
                    ?: active?.id
                    ?: selectable.firstOrNull()?.id.orEmpty()
                val previous = _state.value.sourceId
                _state.value = _state.value.copy(
                    available = selectable,
                    sourceId = keep,
                    availableTypes = tabsOf(selectable.firstOrNull { it.id == keep }),
                )
                if (keep != previous) {
                    prefs.lastSourceId = keep
                    forgetLoaded()
                }
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

    /** 选中一个音源：只用它搜，并记住选择（下次进搜索页默认还是它）。 */
    fun selectSource(id: String) {
        if (id == _state.value.sourceId) return
        val plugin = _state.value.available.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            sourceId = id,
            availableTypes = tabsOf(plugin),
            rows = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            sheets = emptyList(),
            failure = null,
            searched = false,
        )
        prefs.lastSourceId = id
        forgetLoaded()
        // 0.0.8：音源是全局单选——搜索页换音源要同时切换「当前音源」，
        // 这样首页榜单、插件页的单选圈、取链都跟着变。
        viewModelScope.launch { manager.setCurrentSource(id) }
        // 已经搜过就立刻用新音源重搜，避免用户以为「换了音源没反应」
        if (_state.value.query.isNotBlank()) search()
    }

    /** 供「我的」页展示最近搜索；与 [search] 共用同一份历史记录。 */
    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    /**
     * 按当前 Tab 的类型搜当前选中的那一个音源。
     *
     * 失败一定要有话说：超时 / 插件报错 / 空结果都会写进 [SearchUiState.failure]，
     * 文案格式固定为「xx 音源搜索失败：原因，换个音源试试」。
     */
    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        val plugin = _state.value.selected
        if (plugin == null) {
            _state.value = _state.value.copy(
                failure = "还没有可用的音源，先去「我的 · 音源插件管理」启用一个",
                searched = true,
            )
            return
        }
        val type = _state.value.currentType()
        if (type !in plugin.supportedTypes()) {
            _state.value = _state.value.copy(
                failure = "${platformLabelOf(plugin)} 音源不支持这种搜索，换个音源试试",
                searched = true,
            )
            return
        }

        job?.cancel()
        val label = platformLabelOf(plugin)
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failure = null, message = null)
            history.record(query)

            val outcome = runCatching { bridge.search(plugin.id, query, 1, type) }
            val result = outcome.getOrNull()
            val items = result?.items.orEmpty()
            val albums = result?.albums.orEmpty()
            val artists = result?.artists.orEmpty()
            val error = result?.error
                ?: outcome.exceptionOrNull()?.let { it.message?.take(80) ?: "未知错误" }

            val empty = items.isEmpty() && albums.isEmpty() && artists.isEmpty()
            val failure = when {
                error != null && empty ->
                    "$label 音源搜索失败：${shortReason(error)}，换个音源试试"
                empty ->
                    "$label 音源里没有「$query」的结果，换个关键词或音源试试"
                else -> null
            }

            if (!empty) {
                runCatching { tracks.cache(items) }
                loadedTypes += type
                loadedQuery = query
            }

            val rows = items.map { item ->
                SearchRow(
                    pluginId = plugin.id,
                    pluginName = plugin.displayName,
                    platformLabel = label,
                    item = item,
                )
            }
            _state.value = _state.value.copy(
                loading = false,
                rows = if (type == SearchType.MUSIC) rows else _state.value.rows,
                albums = if (type == SearchType.ALBUM) albums else _state.value.albums,
                artists = if (type == SearchType.ARTIST) artists else _state.value.artists,
                sheets = if (type == SearchType.SHEET) albums else _state.value.sheets,
                failure = failure,
                searched = true,
            )
        }
    }

    /** 切 Tab：该插件不支持的 Tab 不会出现；支持但没加载过的类型按需搜（懒加载）。 */
    fun onTabChange(tab: SearchTab) {
        if (tab !in _state.value.availableTypes) return
        _state.value = _state.value.copy(tab = tab)
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        if (tab.type == SearchType.MUSIC && _state.value.rows.isNotEmpty()) return
        if (tab.type in loadedTypes && loadedQuery == query) return
        search()
    }

    /** 下载一条搜索结果。 */
    fun download(item: MusicItem, quality: String = "high") {
        viewModelScope.launch {
            val ok = runCatching { downloads.enqueue(item, Quality.fromWire(quality)) }.getOrDefault(false)
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

    private fun forgetLoaded() {
        loadedTypes.clear()
        loadedQuery = ""
    }

    /** 把插件报错压成一句能塞进提示里的短句。 */
    private fun shortReason(raw: String): String {
        val line = raw.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "未知错误" }
        return when {
            line.contains("超时", ignoreCase = true) -> "插件超时没返回（30 秒）"
            line.contains("Promise that has not settled", ignoreCase = true) ->
                "插件返回的异步结果没结算"
            line.contains("not a function", ignoreCase = true) -> "插件内部报错（函数不存在）"
            line.contains("Unable to resolve host", ignoreCase = true) ||
                line.contains("UnknownHost", ignoreCase = true) -> "连不上源站（网络或域名失效）"
            line.contains("timeout", ignoreCase = true) -> "源站响应超时"
            else -> line.take(60)
        }
    }

    private fun tabsOf(plugin: PluginMeta?): List<SearchTab> {
        if (plugin == null) return listOf(SearchTab.TRACKS)
        val types = plugin.supportedTypes()
        val tabs = SearchTab.entries.filter { it.type in types }
        return tabs.ifEmpty { listOf(SearchTab.TRACKS) }
    }
}

/** Tab → MusicFree 搜索类型。 */
internal fun SearchTab.typeOf(): String = type

private fun SearchUiState.currentType(): String = tab.type

private fun PluginMeta.supportedTypes(): Set<String> {
    val declared = supportedSearchType.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return if (declared.isEmpty()) setOf(SearchType.MUSIC) else declared.toSet()
}