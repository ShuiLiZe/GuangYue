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
import com.guangyue.core.plugin.PluginFailureText
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

/** 11-17：搜索页音源条上「本地」的 id。 */
const val LOCAL_SOURCE = "__local__"

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
    /** 0.0.34：单曲翻页（插件一页常只给 10 首）：已加载到第几页 / 是否到底 / 是否在加载下一页。 */
    val musicPage: Int = 1,
    val musicEnd: Boolean = false,
    val loadingMore: Boolean = false,
    /** 11-17：Folia 搜索页音源条最后的「本地」——搜手机里的歌（全部歌曲），不打插件。 */
    val local: Boolean = false,
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
    private val downloadedLibrary: com.guangyue.core.download.DownloadedLibrary,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedState: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 输入即搜的防抖（U3）：停止输入 400ms 后才真正发请求。 */
    private val debounce: DebounceScheduler = object : DebounceScheduler(SEARCH_DEBOUNCE_MS) {
        private var waiter: Job? = null

        override fun enqueue(delayMs: Long, block: () -> Unit) {
            waiter = viewModelScope.launch {
                kotlinx.coroutines.delay(delayMs)
                block()
            }
        }

        override fun onCancel() {
            waiter?.cancel()
            waiter = null
        }

        override fun onFire() {
            search()
        }
    }

    /** 已加载过结果的 Tab（懒加载判断：切到这个 Tab 时 query 没变就不再请求）。 */
    private val loadedTypes = mutableSetOf<String>()
    private var loadedQuery: String = ""

    /** 本地搜索这一次的结果（开播用原曲目，不走插件取链）。 */
    private var localResults: List<com.guangyue.core.data.model.TrackEntity> = emptyList()

    init {
        // 11-17：首页本地页签点搜索框进来（search?local=1），默认选「本地」。
        if (savedState.get<String>("local") == "1") _state.value = _state.value.copy(local = true)
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
                    availableTypes = if (_state.value.local) listOf(SearchTab.TRACKS) else tabsOf(selectable.firstOrNull { it.id == keep }),
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

    /**
     * 输入即搜（U3）。
     *
     * Folia 桌面是回车 / 点放大镜提交（`SearchWorkspace.tsx` 的 `onSubmit`），手机没有回车这个习惯，
     * 所以改成「输入停手 400ms 就搜」。三条规则：
     *  1. 每次输入都先撤掉上一次的防抖，再排新的 —— 连打不会连发请求；
     *  2. 真正发请求时 [search] 里的 `job?.cancel()` 会取消上一次在途请求，结果只认最后一个；
     *  3. 输入被清空 = 回到初始态（历史 / 推荐），不发请求。
     */
    fun onQueryChange(text: String) {
        val previous = _state.value
        if (text.trim().isEmpty()) {
            debounce.cancel()
            job?.cancel()
            job = null
            _state.value = previous.copy(
                query = text,
                loading = false,
                failure = null,
                searched = false,
                rows = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                sheets = emptyList(),
            )
            forgetLoaded()
            return
        }
        val shouldSearch = shouldSearchOnInput(previous, text)
        _state.value = previous.copy(query = text)
        if (!shouldSearch) return
        debounce.schedule()
    }

    /** 立即搜（点键盘上的「搜索」键 / 点搜索框右侧的搜索字样）。 */
    fun searchNow() {
        debounce.cancel()
        search()
    }

    /** 选中一个音源：只用它搜，并记住选择（下次进搜索页默认还是它）。 */
    fun selectSource(id: String) {
        if (id == LOCAL_SOURCE) {
            if (_state.value.local) return
            debounce.cancel()
            job?.cancel()
            _state.value = _state.value.copy(
                local = true, availableTypes = listOf(SearchTab.TRACKS), tab = SearchTab.TRACKS,
                rows = emptyList(), albums = emptyList(), artists = emptyList(), sheets = emptyList(),
                failure = null, searched = false, loading = false,
            )
            forgetLoaded()
            if (_state.value.query.isNotBlank()) search()
            return
        }
        if (_state.value.local) {
            _state.value = _state.value.copy(local = false, rows = emptyList(), searched = false, failure = null)
            forgetLoaded()
            if (id == _state.value.sourceId) {
                _state.value = _state.value.copy(availableTypes = tabsOf(_state.value.selected))
                if (_state.value.query.isNotBlank()) search()
                return
            }
        }
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
        // 已经搜过就立刻用新音源重搜，避免用户以为「换了音源没反应」；
        // U3：先撤掉待发的防抖，免得多发一次。
        // 11-15 修：必须等 setCurrentSource 把插件启用完再搜——以前并发发出去，
        // 点一个没启用的音源第一次必报「插件已禁用」。
        debounce.cancel()
        viewModelScope.launch {
            runCatching { manager.setCurrentSource(id) }
            if (_state.value.sourceId == id && _state.value.query.isNotBlank()) search()
        }
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
        if (_state.value.local) { searchLocal(query); return }
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
                ?: outcome.exceptionOrNull()?.let { it.message ?: "未知错误" }

            if (error != null) {
                // 0.0.9 a-1：原始异常（含异常类名与插件栈帧）只进日志，绝不进 UI state。
                android.util.Log.w(TAG, "search failed plugin=${plugin.id} keyword=$query: $error")
            }

            val empty = items.isEmpty() && albums.isEmpty() && artists.isEmpty()
            val failure = when {
                error != null && empty ->
                    PluginFailureText.searchFailed(label, error)
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
                musicPage = if (type == SearchType.MUSIC) 1 else _state.value.musicPage,
                musicEnd = if (type == SearchType.MUSIC) (result?.isEnd == true || items.isEmpty()) else _state.value.musicEnd,
                loadingMore = false,
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

    /** 0.0.34：单曲列表滑到底时取下一页（插件 search(page=n)），去重后接在后面。 */
    /** 本地搜索：在「全部歌曲」（下载 + 手机媒体库 + 导入文件夹）里按歌名、歌手、专辑找。 */
    private fun searchLocal(query: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failure = null, message = null)
            history.record(query)
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val downloaded = downloadedLibrary.tracks.value.filter { downloadedLibrary.exists(it) }.map { downloadedLibrary.toPlayable(it) }
                    val scan = com.guangyue.feature.library.viewmodel.DeviceAudio.scanDetailed(appContext)
                    // 11-17：支持拼音（全拼 / 首字母，Folia commandSearchIndex 同思路）；ICU Han-Latin 需 API 29+。
                    val toPinyin: ((String) -> String?)? = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        val tr = runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }.getOrNull()
                        tr?.let { t -> { s: String -> runCatching { t.transliterate(s) }.getOrNull() } }
                    } else null
                    val pinyinOf = { f: String -> pinyinCache.getOrPut(f) { LocalSongMatch.keys(f, toPinyin) ?: NO_PINYIN }.takeIf { it !== NO_PINYIN } }
                    com.guangyue.core.data.model.LibraryRules.allSongs(downloaded, scan.tracks).filter { t ->
                        LocalSongMatch.matches(query, listOf(t.title, t.artist, t.album), pinyinOf)
                    }
                }.getOrDefault(emptyList())
            }
            if (!_state.value.local) return@launch
            localResults = found
            _state.value = _state.value.copy(
                loading = false,
                searched = true,
                rows = found.map { t ->
                    SearchRow(
                        pluginId = LOCAL_SOURCE, pluginName = "本地", platformLabel = "本地",
                        item = MusicItem(platform = "local", id = t.key, title = t.title, artist = t.artist,
                            album = t.album.ifBlank { null }, artwork = t.artwork, durationMs = t.durationMs.takeIf { it > 0 }),
                    )
                },
                failure = if (found.isEmpty()) "本地没有「$query」的歌曲" else null,
                musicEnd = true,
                loadingMore = false,
            )
        }
    }

    /** 本地歌曲字段 → 拼音键（全拼, 首字母）的缓存；无汉字记 [NO_PINYIN]。 */
    private val pinyinCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    private val NO_PINYIN = "" to ""

    /** 本地搜索时的开播列表（原曲目）；不是本地搜索返回 null。 */
    fun localPlaylist(): List<com.guangyue.core.data.model.TrackEntity>? =
        if (_state.value.local) localResults.takeIf { it.isNotEmpty() } else null

    fun loadMore() {
        if (_state.value.local) return
        val st = _state.value
        if (st.tab != SearchTab.TRACKS || st.loading || st.loadingMore || st.musicEnd || st.rows.isEmpty()) return
        val plugin = st.selected ?: return
        val query = st.query.trim()
        if (query.isEmpty()) return
        val next = st.musicPage + 1
        _state.value = st.copy(loadingMore = true)
        viewModelScope.launch {
            val result = runCatching { bridge.search(plugin.id, query, next, SearchType.MUSIC) }.getOrNull()
            val cur = _state.value
            if (cur.query.trim() != query || cur.sourceId != plugin.id) return@launch
            val seen = cur.rows.map { it.item.id }.toHashSet()
            val fresh = result?.items.orEmpty().filter { seen.add(it.id) }
            if (fresh.isNotEmpty()) runCatching { tracks.cache(fresh) }
            val label = platformLabelOf(plugin)
            _state.value = cur.copy(
                loadingMore = false,
                musicPage = if (result?.error == null) next else cur.musicPage,
                // 出错 / 没新东西 / 插件说到底了，都当到底，免得一直转。
                musicEnd = result == null || result.error != null || result.isEnd || fresh.isEmpty(),
                rows = cur.rows + fresh.map { SearchRow(pluginId = plugin.id, pluginName = plugin.displayName, platformLabel = label, item = it) },
            )
        }
    }

    /**
     * 用户点了失败提示里的「重试」。
     *
     * 与 [search] 的唯一区别：先让 [PluginManager.notifyReload] 把该插件的 runtime 与
     * [MusicPluginBridge] 的查询缓存丢掉，再重新发请求。
     * 0.0.8 的桥会把「失败」的结果也按 cacheControl 的 TTL 缓存起来，直接重搜只会
     * 拿到同一条失败结果——不真重发就永远修不好。正常搜索路径不受影响，缓存语义不变。
     */
    fun retry() {
        debounce.cancel()
        val plugin = _state.value.selected
        if (plugin != null) {
            runCatching { manager.notifyReload(plugin.platform) }
                .onFailure { android.util.Log.w(TAG, "retry reload failed plugin=${plugin.id}: ${it.message}") }
        }
        forgetLoaded()
        search()
    }

    /** 切 Tab：该插件不支持的 Tab 不会出现；支持但没加载过的类型按需搜（懒加载）。 */
    fun onTabChange(tab: SearchTab) {
        if (tab !in _state.value.availableTypes) return
        _state.value = _state.value.copy(tab = tab)
        debounce.cancel()
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        if (tab.type == SearchType.MUSIC && _state.value.rows.isNotEmpty()) return
        if (tab.type in loadedTypes && loadedQuery == query) return
        search()
    }

    /**
     * U3 清空输入：回到初始态（搜索历史 / 推荐），同时撤掉待发与在途的请求。
     * 与「用户手动删光输入框」等价，单独给搜索框的 × 按钮用。
     */
    fun clearQuery() {
        onQueryChange("")
    }

    /** 下载一条搜索结果。 */
    fun download(item: MusicItem, quality: String = "high") {
        if (item.platform == "local") { _state.value = _state.value.copy(message = "本地歌曲已在手机里，不用下载"); return }
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

    /** 11-18：Folia 搜索行「加入播放队列」：本地结果用原曲目，在线结果按插件条目转成曲目，交给 [append]（PlaybackController.appendTracks）。 */
    fun enqueue(item: MusicItem, append: (List<com.guangyue.core.data.model.TrackEntity>) -> Unit) {
        val localRow = if (item.platform == "local") localResults.firstOrNull { it.key == item.id } else null
        append(listOf(localRow ?: com.guangyue.core.data.model.TrackMapper.fromPlugin(item)))
        _state.value = _state.value.copy(message = "已加入播放队列")
    }

    /** Add a result to the first playlist, creating 「我的收藏」 on demand. */
    fun addToCurrentSheet(item: MusicItem) {
        viewModelScope.launch {
            val existing = playlists.playlists().firstOrNull()?.firstOrNull()?.id
            val id = existing ?: playlists.create("我的收藏")
            val localRow = if (item.platform == "local") localResults.firstOrNull { it.key == item.id } else null
            if (localRow != null) playlists.addTracks(id, listOf(localRow)) else playlists.addTracks(id, listOf(item))
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
        debounce.cancel()
        _state.value = _state.value.copy(query = album.title)
        search()
    }

    fun openArtist(artist: MusicItem) {
        debounce.cancel()
        _state.value = _state.value.copy(query = artist.title)
        search()
    }

    private fun forgetLoaded() {
        loadedTypes.clear()
        loadedQuery = ""
        debounce.cancel()
    }

    private fun tabsOf(plugin: PluginMeta?): List<SearchTab> {
        if (plugin == null) return listOf(SearchTab.TRACKS)
        val types = plugin.supportedTypes()
        val tabs = SearchTab.entries.filter { it.type in types }
        return tabs.ifEmpty { listOf(SearchTab.TRACKS) }
    }

    private companion object {
        /** 与 [MusicPluginBridge] 共用同一个日志 tag，方便 `adb logcat` 一把捞。 */
        const val TAG = "GuangYuePlugin"
    }
}

/** Tab → MusicFree 搜索类型。 */
internal fun SearchTab.typeOf(): String = type

private fun SearchUiState.currentType(): String = tab.type

private fun PluginMeta.supportedTypes(): Set<String> {
    val declared = supportedSearchType.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return if (declared.isEmpty()) setOf(SearchType.MUSIC) else declared.toSet()
}