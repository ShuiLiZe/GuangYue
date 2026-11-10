package com.guangyue.feature.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.db.GuangYueDatabase
import com.guangyue.core.data.model.PlaylistEntity
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.data.repository.PlaylistRepository
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.plugin.InstallResult
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginFailureText
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.plugin.UpdateCheck
import com.guangyue.core.prefs.AppPreferences
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * 首页的数据：三段内容（推荐 / 最近 / 榜单）+ 是否装过插件。
 *
 * 内容全部来自本地库与已启用插件；插件、下载这类管理入口不进首页（m00161）。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val tracks: TrackRepository,
    private val plugins: PluginManager,
    private val bridge: MusicPluginBridge,
    db: GuangYueDatabase,
    private val downloadedLibrary: com.guangyue.core.download.DownloadedLibrary,
    private val downloads: com.guangyue.core.download.DownloadManager,
) : ViewModel() {

    /**
     * 0.0.19：歌单 / 榜单页「全部下载」与聚焦卡单曲下载。
     * 返回 (新加入数, 已下载或正在下的数)，界面据此给提示。
     */
    fun downloadTracks(list: List<TrackEntity>, quality: com.guangyue.core.player.Quality): Pair<Int, Int> {
        var added = 0
        // 本地扫描来的歌本来就在手机里，不用下。
        for (t in list) if (t.source != TrackEntity.SOURCE_LOCAL && downloads.enqueue(t, quality)) added++
        return added to (list.size - added)
    }

    /** 已安装插件（只统计能当音源用的），供空状态文案判断。 */
    init {
        android.util.Log.d("GYHOME", "VM CREATED plugins=" + plugins.plugins.value.size)
        // B2 修：冷启动直接进首页时插件表从没加载（只有音源页 / 搜索页会 load），currentSource 一直是 null，
        // 推荐歌单 / 榜单永远拉不到；这里与 SearchViewModel 一样先 load 一次（幂等）。
        viewModelScope.launch { plugins.load() }
    }

    val enabledPlugins: StateFlow<List<PluginMeta>> =
        plugins.plugins.map { list ->
            android.util.Log.d("GYHOME", "plugins total=" + list.size + " enabled=" + list.count { it.enabled } + " ok=" + list.count { it.error == null } + " hs=" + list.count { it.hasSearch })
            if (list.isNotEmpty() && list.none { it.error == null && it.hasSearch }) {
                android.util.Log.d("GYHOME", "list empty? size=" + list.size + " sample=" + list.take(3).map { it.id + "/en=" + it.enabled + "/err=" + it.error + "/hs=" + it.hasSearch })
            }
            list.filter { it.error == null && it.hasSearch }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前音源：全局唯一，决定首页「推荐 / 榜单」从哪个音源拉数据。 */
    val currentSource: StateFlow<PluginMeta?> =
        plugins.plugins.map { list -> list.firstOrNull { it.enabled && it.error == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 本地歌单（推荐段的第一来源）。 */
    val playlistsFlow: StateFlow<List<HomeTile>> = playlists.playlists()
        .map { list -> list.map { it.toTile() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近播放（最多 20 条，按播放时间倒序由 DAO 保证）。 */
    val recentTiles: StateFlow<List<HomeTile>> = tracks.recentlyPlayed(20)
        .map { list -> list.map { it.toTile() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近播放的原始曲目，点歌时直接进队列。 */
    val currentTracks: StateFlow<List<TrackEntity>> = tracks.recentlyPlayed(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _charts = MutableStateFlow<List<HomeTile>>(emptyList())
    val charts: StateFlow<List<HomeTile>> = _charts.asStateFlow()

    private val _loadingCharts = MutableStateFlow(false)
    val loadingCharts: StateFlow<Boolean> = _loadingCharts.asStateFlow()

    /** 「热门榜单」区块是否显示：插件声明了 getTopLists 且真拉到了榜单才有。 */
    private val _hasCharts = MutableStateFlow(false)
    val hasCharts: StateFlow<Boolean> = _hasCharts.asStateFlow()

    /** 插件返回的榜单条目（按 key 缓存），点「热门榜单」时用它播放。 */
    private val _chartItems = MutableStateFlow<Map<String, MusicItem>>(emptyMap())
    val chartItems: StateFlow<Map<String, MusicItem>> = _chartItems.asStateFlow()

    private var chartsLoaded = false
    private var chartsFor: String? = null

    private val _sheets = MutableStateFlow<List<HomeTile>>(emptyList())

    /** 推荐段：当前音源的官方推荐歌单（`getRecommendSheetTags` 双层结构）。 */
    val sheets: StateFlow<List<HomeTile>> = _sheets.asStateFlow()

    private val _sheetLeaves = MutableStateFlow<Map<String, JsonObject>>(emptyMap())

    private var sheetsLoaded = false
    private var sheetsFor: String? = null

    // ---------- U6 首页墙的四态（loading / failed 只有这两项要从 ViewModel 出，
    // 空态由「没数据」+ hasAnyPlugin 在 UI 侧判断） ----------

    private val _loadingSheets = MutableStateFlow(false)
    private val _sheetsFailed = MutableStateFlow(false)
    private val _chartsFailed = MutableStateFlow(false)

    /** 推荐段或榜单段还在拉：首页墙显示「正在载入推荐内容…」。 */
    val wallLoading: StateFlow<Boolean> = combine(_loadingSheets, _loadingCharts) { a, b -> a || b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 推荐段或榜单段报错：只有真没内容时才显示失败态（有内容优先显示内容）。 */
    val wallFailed: StateFlow<Boolean> = combine(_sheetsFailed, _chartsFailed) { a, b -> a || b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 首页墙「重试」：清掉「已拉过」标记并重新拉推荐段与榜单段。 */
    fun retryWall() {
        sheetsLoaded = false
        sheetsFor = null
        chartsLoaded = false
        chartsFor = null
        _sheetsFailed.value = false
        _chartsFailed.value = false
        loadSheetsIfNeeded()
        loadChartsIfNeeded()
    }


    /**
     * 拉一次推荐歌单：只问**当前音源**。
     *
     * 0.0.7 首页「推荐」段只显示本地歌单，没有任何音源内容；插件明明声明了
     * `getRecommendSheetTags` 却没人用。0.0.8 改成真正的音源推荐歌单，拿不到就整段不显示。
     */
    fun loadSheetsIfNeeded() {
        android.util.Log.d("GYHOME", "loadSheetsIfNeeded src=" + currentSource.value?.id + " hasRecommend=" + currentSource.value?.hasRecommend + " loaded=" + sheetsLoaded + " for=" + sheetsFor)
        val source = currentSource.value ?: return
        if (sheetsLoaded && sheetsFor == source.id) return
        // 换音源后要重拉：hasRecommend 是上一次的结论，不能拿它挡新音源。
        sheetsLoaded = true
        sheetsFor = source.id
        viewModelScope.launch {
            _loadingSheets.value = true
            _sheetsFailed.value = false
            _sheets.value = emptyList()
            _sheetLeaves.value = emptyMap()
            // functions 为空 = 插件没声明能力清单，不代表不支持；直接试一次，拿到空就整段不显示。
            if (source.functions.isNotEmpty() && !source.hasRecommend) {
                _loadingSheets.value = false
                return@launch
            }
            val tags = runCatching { bridge.getRecommendSheetTags(source.id) }.getOrElse {
                android.util.Log.d("GYHOME", "tags ERR " + it.message)
                _sheetsFailed.value = true
                emptyList()
            }
            android.util.Log.d("GYHOME", "tags=" + tags.size)
            val leaves = linkedMapOf<String, JsonObject>()
            val tiles = mutableListOf<HomeTile>()
            for (tag in tags) {
                // getRecommendSheetTags 返回 [{title, data:[{id,title}, ...]}, ...]，
                // 内层每一项才是能喂给 getRecommendSheetsByTag 的「分类叶子」。
                val children = (tag["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                for (leaf in children) {
                    val id = (leaf["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                    if (leaves.containsKey(id)) continue
                    leaves[id] = leaf
                    if (leaves.size >= 12) break
                }
                if (leaves.size >= 12) break
            }
            for ((id, leaf) in leaves) {
                android.util.Log.d("GYHOME", "leaf=" + id + " -> " + runCatching { bridge.getRecommendSheets(source.id, leaf, 1).size }.getOrElse { "ERR:" + it.message })
                val sheetsOfLeaf = runCatching { bridge.getRecommendSheets(source.id, leaf, 1) }
                    .getOrElse { _sheetsFailed.value = true; emptyList() }
                for (sheet in sheetsOfLeaf) {
                    val name = (sheet["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (sheet["name"] as? JsonPrimitive)?.contentOrNull
                        ?: "推荐歌单"
                    val artwork = (sheet["artwork"] as? JsonPrimitive)?.contentOrNull
                        ?: (sheet["coverImg"] as? JsonPrimitive)?.contentOrNull
                    val desc = (sheet["description"] as? JsonPrimitive)?.contentOrNull
                    tiles += HomeTile(
                        key = "sheet:$id:" + ((sheet["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()),
                        title = name,
                        subtitle = desc?.takeIf { it.isNotBlank() } ?: "推荐歌单",
                        artwork = artwork,
                        kind = HomeTileKind.SHEET,
                    )
                    if (tiles.size >= 8) break
                }
                if (tiles.size >= 8) break
            }
            _sheetLeaves.value = leaves
            _sheets.value = tiles.distinctBy { it.key }
            _loadingSheets.value = false
        }
    }

    /**
     * 拉一次榜单：用当前音源的 `getTopLists()` 真数据。
     *
     * 0.0.7 这里是「拿关键词『热歌』去 search」，既不是榜单、也会给每个插件都发一次请求。
     * 0.0.8 改成只问**当前音源**要它的官方榜单；插件没实现 getTopLists（或返回空）就
     * **不显示这个区块**，而不是塞假数据。
     */
    fun loadChartsIfNeeded() {
        val source = currentSource.value ?: return
        if (chartsLoaded && chartsFor == source.id) return
        chartsLoaded = true
        chartsFor = source.id
        viewModelScope.launch {
            _loadingCharts.value = true
            _chartsFailed.value = false
            _hasCharts.value = false
            _charts.value = emptyList()
            if (!source.hasTopLists) {
                _loadingCharts.value = false
                return@launch
            }
            val items = mutableMapOf<String, MusicItem>()
            val tiles = mutableListOf<HomeTile>()
            val boards = runCatching { bridge.getTopLists(source.id) }
                .getOrElse { _chartsFailed.value = true; emptyList() }
            for (board in boards) {
                // getTopLists 返回的是树形结构：每个 board 可能自己带一排子榜单（data 数组），
                // 子项才是能喂给 getTopListDetail 的那一条。
                val children = (board["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val leaves = if (children.isNotEmpty() && children.first().containsKey("id")) children
                else listOf(board)
                for (leaf in leaves) {
                    val title = (leaf["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (leaf["name"] as? JsonPrimitive)?.contentOrNull
                        ?: "排行榜"
                    val songs = runCatching { bridge.getTopListDetail(source.id, leaf, 1) }
                        .getOrElse { _chartsFailed.value = true; emptyList() }
                    if (songs.isEmpty()) continue
                    val top = songs.first()
                    val key = "board:" + board["id"].let { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } +
                        ":" + leaf["id"].let { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } +
                        ":" + leaf["page"].let { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
                    items[key] = top
                    tiles += HomeTile(
                        key = key,
                        title = title,
                        subtitle = songs.take(3).joinToString(" / ") { it.title },
                        artwork = top.artwork,
                        kind = HomeTileKind.STATION,
                    )
                    if (tiles.size >= 8) break
                }
                if (tiles.size >= 8) break
            }
            _chartItems.value = items
            _charts.value = tiles
            _hasCharts.value = tiles.isNotEmpty()
            _loadingCharts.value = false
        }
    }

    /** 推荐段：本地歌单优先，没有歌单时用最近播放补齐，保证首屏不空。 */
    fun recommended(playlists: List<HomeTile>, recent: List<HomeTile>): List<HomeTile> {
        android.util.Log.d("GYHOME", "recommended sheets=" + playlists.size + " recent=" + recent.size)
        return if (playlists.isNotEmpty()) playlists else recent
    }

    // ---------- U7 音乐库页：收藏 / 本地两份列表 + 读取状态 ----------
    //
    // 「我的」页只要个数（likedCount / downloadedCount），库页要整份列表，所以另开两条流；
    // 映射沿用 toTile()：key 都是 track:<key>，点一行走既有 playTracksOf 从本地库取回来播。

    /** 本地库读失败标记（Room 快照流，只有读的时候可能抛）。 */
    private val _libraryFailed = MutableStateFlow(false)

    /** 库页是否读失败；失败时界面显示失败态 + 重试。 */
    val libraryFailed: StateFlow<Boolean> = _libraryFailed.asStateFlow()

    /** 收藏（已标星）曲目，库页「收藏」分区。 */
    val favoriteTiles: StateFlow<List<HomeTile>> = tracks.favorites()
        .catch {
            _libraryFailed.value = true
            emit(emptyList())
        }
        .map { list -> list.map { it.toTile() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本地曲库（下载 / 导入的本地音乐），库页「本地音乐」分区。 */
    val localTiles: StateFlow<List<HomeTile>> = tracks.localTracks()
        .catch {
            _libraryFailed.value = true
            emit(emptyList())
        }
        .map { list -> list.map { it.toTile() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 库页首帧：三条本地流都还没发出第一份数据前置 true（Room 通常一帧内就到）。 */
    val libraryLoading: StateFlow<Boolean> =
        combine(playlistsFlow, favoriteTiles, localTiles) { _, _, _ -> false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 库页「重试」：清掉失败标记，再摸一次本地库让流重发。 */
    fun retryLibrary() {
        _libraryFailed.value = false
        viewModelScope.launch { runCatching { tracks.count() } }
    }

    /** 「我的」页的计数：我喜欢 / 本地下载。 */
    val likedCount: StateFlow<Int> = tracks.favorites()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // 0.0.18：本地下载记在 DownloadedLibrary（下载管理页同一份），以前数的是库表 local 行，永远是 0。
    val downloadedCount: StateFlow<Int> = downloadedLibrary.tracks
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 点开一个首页 / 歌单卡片后播放它。
     *
     * 歌单卡片取该歌单的曲目；**榜单卡片**重新找当前音源把整张榜单的曲目拉一次（而不是只播
     * 缓存里那一首）；最近播放的曲目走本地库。
     */
    /** B2：集合页取曲目，空 / 失败也回调（null = 出错）。 */
    fun loadTracksOf(tile: HomeTile, onDone: (List<TrackEntity>?) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { resolveTracks(tile) }.getOrNull())
        }
    }

    fun playTracksOf(tile: HomeTile, onReady: (List<TrackEntity>, Int) -> Unit) {
        viewModelScope.launch {
            val list = resolveTracks(tile)
            if (list.isNotEmpty()) onReady(list, 0)
        }
    }

    private suspend fun resolveTracks(tile: HomeTile): List<TrackEntity> {
        run {
            val list = when (tile.kind) {
                HomeTileKind.PLAYLIST -> {
                    val id = tile.key.substringAfter("playlist:").toLongOrNull()
                    if (id == null) emptyList()
                    else runCatching { playlists.snapshot(id) }.getOrDefault(emptyList())
                }
                HomeTileKind.STATION -> tracksOfChart(tile)
                HomeTileKind.SHEET -> { android.util.Log.d("GYHOME", "sheet leaf=" + _sheetLeaves.value.keys + " leafObj=" + _sheetLeaves.value[tile.key.removePrefix("sheet:").substringBefore(":")]); tracksOfSheet(tile) }
                HomeTileKind.TRACK, HomeTileKind.ALBUM -> {
                    val cached = _chartItems.value[tile.key]
                    if (cached == null) {
                        val key = tile.key.removePrefix("track:")
                        runCatching { listOfNotNull(tracks.byKey(key)) }.getOrDefault(emptyList())
                    } else {
                        listOf(cached.toEntity())
                    }
                }
            }
            android.util.Log.d("GYHOME", "playTracksOf kind=" + tile.kind + " key=" + tile.key + " n=" + list.size + " src=" + currentSource.value?.id)
            return list
        }
    }

    /**
     * 推荐歌单卡片 → 该歌单的全部曲目。
     *
     * key 形如 `sheet:<分类叶子id>:<歌单id>`；用当前音源重新问一次详情，
     * 而不是把歌单本身当歌喂给取链（0.0.8 之前点推荐卡片会报「取不到播放地址」）。
     */
    private suspend fun tracksOfSheet(tile: HomeTile): List<TrackEntity> {
        val source = currentSource.value ?: return emptyList()
        val body = tile.key.removePrefix("sheet:")
        val leafId = body.substringBefore(":")
        val sheetId = body.substringAfter(":", "")
        val leaf = _sheetLeaves.value[leafId] ?: return emptyList()
        val raw = runCatching { bridge.getRecommendSheetInfo(source.id, leaf, sheetId, 1) }.getOrDefault(emptyList())
        android.util.Log.d("GYHOME", "sheetItems n=" + raw.size + " first=" + (raw.firstOrNull()?.let { it.title + "/" + it.artist + "/" + it.platform + "/" + it.id }))
        val ents = raw.map { e ->
            val ent = e.toEntity()
            // 插件原始对象要落库：播放层走 TrackMapper.toMusicItem 还原 MusicItem，
            // 洛雪取链源靠它的 raw 拿 songmid/hash 之类的字段，丢了就只能取到「取不到地址」。
            if (e.raw != null) ent.copy(rawJson = e.raw.toString()) else ent
        }
        return ents
    }

    /** 榜单卡片 → 整张榜单的曲目实体（当前音源重新拉一次）。 */
    private suspend fun tracksOfChart(tile: HomeTile): List<TrackEntity> {
        val source = currentSource.value ?: return emptyList()
        val parts = tile.key.removePrefix("board:").split(":")
        if (parts.size < 2) return emptyList()
        val boardId = parts[0]
        val leafId = parts[1]
        val page = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val boards = runCatching { bridge.getTopLists(source.id) }.getOrDefault(emptyList())
        val leaf = boards.firstOrNull { o -> (o["id"] as? JsonPrimitive)?.contentOrNull == boardId }
            ?.let { b -> (b["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull {
                (it["id"] as? JsonPrimitive)?.contentOrNull == leafId
            } }
            ?: return listOfNotNull(_chartItems.value[tile.key]?.toEntity())
        return runCatching { bridge.getTopListDetail(source.id, leaf, page) }
            .getOrDefault(emptyList())
            .map { it.toEntity() }
    }

    private fun MusicItem.toEntity() = TrackEntity(
        key = "${platform}:${id}",
        title = title,
        artist = displayArtist,
        album = album.orEmpty(),
        artwork = artwork,
        source = platform,
        platform = platform,
        songId = id,
        // 0.0.19：榜单 / 单曲也带上插件原始对象（下载与洛雪取链要 songmid/hash 等字段）。
        rawJson = raw?.toString(),
    )

    private fun PlaylistEntity.toTile() = HomeTile(
        key = "playlist:$id",
        title = name,
        subtitle = description?.takeIf { it.isNotBlank() } ?: "本地歌单",
        artwork = artwork,
        kind = HomeTileKind.PLAYLIST,
    )

    private fun TrackEntity.toTile() = HomeTile(
        key = "track:$key",
        title = title,
        subtitle = artist,
        artwork = artwork,
        kind = HomeTileKind.TRACK,
    )
}

@HiltViewModel
class PluginViewModel @Inject constructor(
    private val manager: PluginManager,
    private val checker: com.guangyue.core.plugin.PluginHealthChecker,
    private val prefs: AppPreferences,
) : ViewModel() {

    val plugins: StateFlow<List<PluginMeta>> = manager.plugins

    /** 已记录的订阅源。 */
    val subscriptions: StateFlow<List<com.guangyue.core.plugin.Subscription>> = manager.subscriptions

    /** 批量安装进度（跨多个插件下载时逐步推进）。 */
    val batchProgress: StateFlow<com.guangyue.core.plugin.BatchProgress> = manager.progress

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 与 [message] 配套的失败明细，UI 上以「详情」展开。 */
    private val _messageDetails = MutableStateFlow<String?>(null)
    val messageDetails: StateFlow<String?> = _messageDetails.asStateFlow()

    private fun say(text: String, details: String? = null) {
        _message.value = text
        _messageDetails.value = details
    }

    /** 0.0.8：当前音源（全局唯一）。 */
    val currentSource: StateFlow<String> =
        manager.plugins.map { list -> list.firstOrNull { it.enabled && it.error == null }?.id.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** 「隐藏不可用」筛选状态：只看体检通过、或还没体检的音源。 */
    private val _hideUnavailable = MutableStateFlow(false)
    val hideUnavailable: StateFlow<Boolean> = _hideUnavailable.asStateFlow()

    fun toggleHideUnavailable() {
        _hideUnavailable.value = !_hideUnavailable.value
    }

    /** 选一个音源作为当前音源（单选；持久化在 [AppPreferences.currentSourceId]，与搜索页同步）。 */
    fun selectSource(id: String) {
        viewModelScope.launch {
            manager.setCurrentSource(id)
            prefs.currentSourceId = id
            _message.value = "已切换到「${manager.find(id)?.displayName ?: id}」"
        }
    }

    /** 排序：把插件移到第 [to] 位（0 基）。0.0.8 起排序收进每行的「⋯」菜单。 */
    fun moveTo(id: String, to: Int) {
        viewModelScope.launch { manager.moveTo(id, to) }
    }

    init {
        viewModelScope.launch { manager.load() }
    }

    fun installFromUri(uri: android.net.Uri) = run {
        _busy.value = true
        viewModelScope.launch {
            _message.value = manager.installFromUri(uri).describe()
            _busy.value = false
        }
    }

    fun installFromText(code: String) = run {
        _busy.value = true
        viewModelScope.launch {
            try {
                say(manager.installCode(code, "local-file").describe(), lastDetails)
            } catch (t: Throwable) {
                logRaw("install", t)
                say(PluginFailureText.installFailed(t.message), lastDetails)
            } finally {
                _busy.value = false
            }
        }
    }

    fun report(text: String) {
        say(text)
    }

    fun installFromUrl(url: String) = run {
        if (url.isBlank()) return@run
        _busy.value = true
        viewModelScope.launch {
            try {
                say(manager.installFromUrl(url.trim()).describe(), lastDetails)
            } catch (t: Throwable) {
                logRaw("install", t)
                say(PluginFailureText.installFailed(t.message), lastDetails)
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggle(plugin: PluginMeta, enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(plugin.id, enabled) }
    }

    fun delete(plugin: PluginMeta) {
        viewModelScope.launch { manager.delete(plugin.id) }
    }

    fun checkUpdate(plugin: PluginMeta) {
        _busy.value = true
        viewModelScope.launch {
            _message.value = when (val r = manager.checkUpdate(plugin.id)) {
                is UpdateCheck.Latest -> "「${plugin.displayName}」已是最新（v${r.current}）"
                is UpdateCheck.Available -> {
                    val result = manager.update(plugin.id)
                    if (result is InstallResult.Success) {
                        "「${plugin.displayName}」已更新到 v${r.latest}"
                    } else {
                        // 0.0.9 a-1：Failure.message 可能是原始异常（含类名/栈帧），过一遍收口。
                        "更新失败：" + PluginFailureText.reason((result as? InstallResult.Failure)?.message)
                    }
                }
                is UpdateCheck.Error -> "检查更新失败：${PluginFailureText.reason(r.message)}"
                UpdateCheck.NoSource -> "该插件没有 srcUrl，无法检查更新"
                UpdateCheck.Unknown -> "无法检查更新"
            }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
        _messageDetails.value = null
    }

    /** 最近一次批量结果的失败明细，供 [say] 一并带给 UI。 */
    private var lastDetails: String? = null

    private fun InstallResult.describe(): String = when (this) {
        is InstallResult.Success -> {
            lastDetails = null
            "已安装「${meta.displayName}」v${meta.version}"
        }
        is InstallResult.Failure -> {
            lastDetails = null
            // 0.0.9 a-1：安装失败原因统一过一遍中文收口（防 installCode 内部把异常原文带出来）。
            "安装失败：${PluginFailureText.reason(message)}"
        }
        is InstallResult.BatchInstalled -> {
            lastDetails = result.details()
            result.summary()
        }
    }

    // ------------------------------------------------------------ 订阅源 / 一键安装

    /** 内置官方音源订阅地址。 */
    fun officialSourceUrl(): String = PluginViewModelDefaults.OFFICIAL_SOURCE

    /** 一键安装/更新官方音源：拉订阅 → 增量装缺的、升级旧的。 */
    fun installOfficial() = run {
        requireIdle() ?: return@run
        _busy.value = true
        viewModelScope.launch {
            try {
                say(manager.installFromUrl(PluginViewModelDefaults.OFFICIAL_SOURCE).describe(), lastDetails)
            } catch (t: Throwable) {
                logRaw("install", t)
                say(PluginFailureText.installFailed(t.message), lastDetails)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 检查所有订阅源 + 已装插件的版本，自动增量更新。 */
    fun checkAllUpdates() = run {
        requireIdle() ?: return@run
        _busy.value = true
        viewModelScope.launch {
            try {
                val r = manager.checkUpdates(installDiscovered = true)
                say(
                    r.summary(),
                    r.failed.takeIf { it.isNotEmpty() }
                        ?.joinToString("\n") { (n, m) -> "· $n：${PluginFailureText.reason(m)}" },
                )
            } catch (t: Throwable) {
                logRaw("checkUpdates", t)
                say(PluginFailureText.updateFailed(t.message))
            } finally {
                _busy.value = false
            }
        }
    }

    /** 全选 / 全不选。0.0.8 已无多选，保留给旧调用点，实际等同于「选第一个 / 一个都不选」。 */
    suspend fun setAllEnabledCompat(enabled: Boolean) {
        if (enabled) manager.plugins.value.firstOrNull()?.let { manager.setCurrentSource(it.id) }
        else manager.clearCurrentSource()
    }

    /**
     * 体检全部已安装插件（真搜一次 + 真取一次播放地址）。
     *
     * 0.0.8 起体检覆盖所有插件而不是「已启用的」——插件页每行的状态标签就来自这份结果。
     */
    fun recheckAll() = run {
        requireIdle() ?: return@run
        _busy.value = true
        viewModelScope.launch {
            try {
                val targets = manager.plugins.value.count { it.error == null }
                if (targets == 0) {
                    say("还没有安装任何音源，先装一个")
                    return@launch
                }
                say("正在体检 $targets 个音源…")
                val reports = checker.checkAll { done, total, report ->
                    _message.value = "体检中 $done/$total：${report.name} → ${healthText(report.health)}"
                }
                val ok = reports.count { it.health == com.guangyue.core.plugin.PluginHealth.OK }
                val failed = reports.filter { it.health != com.guangyue.core.plugin.PluginHealth.OK }
                say(
                    if (ok > 0) "体检完成：$ok/${reports.size} 个音源真能搜能播"
                    else "体检完成：${reports.size} 个音源都没通过",
                    failed.takeIf { it.isNotEmpty() }
                        ?.joinToString("\n") {
                            // 0.0.9 a-1：体检明细里的原因也走统一收口，不会再带异常类名/栈帧。
                            "· ${it.name}：${healthText(it.health)}（${PluginFailureText.reason(it.note)}）"
                        },
                )
            } catch (t: Throwable) {
                logRaw("health check", t)
                say(PluginFailureText.healthFailed(t.message))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun healthText(health: String): String = when (health) {
        com.guangyue.core.plugin.PluginHealth.OK -> "可用"
        com.guangyue.core.plugin.PluginHealth.NO_RESULT -> "搜索无结果"
        com.guangyue.core.plugin.PluginHealth.NO_MEDIA -> "取不到播放地址"
        com.guangyue.core.plugin.PluginHealth.SEARCH_FAILED -> "搜索失败"
        com.guangyue.core.plugin.PluginHealth.UNSUPPORTED -> "未声明 search"
        else -> "未体检"
    }

    fun forgetSubscription(url: String) {
        viewModelScope.launch {
            manager.forgetSubscription(url)
            _message.value = "已移除订阅源"
        }
    }

    private fun requireIdle(): Unit? {
        if (_busy.value) {
            _message.value = "上一个任务还在进行，请稍候"
            return null
        }
        return Unit
    }

    /**
     * 0.0.9 a-1：安装/更新/体检失败时，原始异常（异常类名 + 插件中文原因 + JS 栈帧）
     * 只写日志，界面一律走 [PluginFailureText] 的中文人话。
     */
    private fun logRaw(what: String, t: Throwable) {
        android.util.Log.w(TAG, "$what failed: ${t.message ?: t.javaClass.name}")
    }

    private companion object {
        /** 与 [MusicPluginBridge] / 搜索页共用同一个日志 tag。 */
        const val TAG = "GuangYuePlugin"
    }
}

/** 插件页的固定常量（不放进 Composable，便于单测）。 */
object PluginViewModelDefaults {
    /** 官方插件源（MusicFree 生态的公益音源集合）。 */
    const val OFFICIAL_SOURCE = "https://hebijunge.github.io/musicfree-plugins/plugins.json"
}

/**
 * 下载管理的界面状态。
 *
 * 只把「渲染需要的东西」整理好，Composable 里不再做任何过滤/求和，
 * 这样 3 个区块（进行中 / 失败 / 已完成）在重组时互不影响。
 */
data class DownloadUiState(
    val active: List<com.guangyue.core.download.DownloadProgress> = emptyList(),
    val failed: List<com.guangyue.core.download.DownloadProgress> = emptyList(),
    val done: List<com.guangyue.core.download.DownloadedTrack> = emptyList(),
    val speedText: String = "",
    val totalSizeText: String = "",
) {
    val isEmpty: Boolean get() = active.isEmpty() && failed.isEmpty() && done.isEmpty()
}

/**
 * 下载管理页的 ViewModel。
 *
 * 数据来源有两条：进行中/失败来自 [DownloadStore]（内存 + 阶段实时刷新），
 * 已完成来自 [DownloadedLibrary]（落盘索引，冷启动也在）。两条 flow 合成一个 UI 状态。
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val manager: com.guangyue.core.download.DownloadManager,
) : ViewModel() {

    private val library: com.guangyue.core.download.DownloadedLibrary get() = manager.library

    val tasks: StateFlow<List<com.guangyue.core.download.DownloadProgress>> =
        manager.storeRef.tasks.map { map ->
            map.values.sortedBy { it.updatedAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<DownloadUiState> = combine(tasks, library.tracks) { list, done ->
        val active = list.filter { it.isActive }
        val failed = list.filter { it.isFailed || it.stage == com.guangyue.core.download.DownloadStage.CANCELED }
        DownloadUiState(
            active = active,
            failed = failed,
            done = done.sortedByDescending { it.downloadedAt },
            speedText = com.guangyue.core.download.DownloadProgress.formatBytes(active.sumOf { it.bytesPerSecond }),
            totalSizeText = com.guangyue.core.download.DownloadProgress.formatBytes(done.sumOf { it.sizeBytes }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), DownloadUiState())

    /** 暂停单任务（WorkManager 取消；已写入的 .part 保留，重试时接着下）。 */
    fun cancel(key: String) {
        manager.cancel(key)
        _message.value = "已暂停下载"
    }

    fun retry(key: String) {
        manager.retry(key)
        _message.value = "已重新排队"
    }

    init {
        // 用户在文件管理器 / 系统音乐 App 里删掉的歌，列表和计数里也去掉。
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val n = runCatching { library.pruneMissing() }.getOrDefault(0)
            if (n > 0) _message.value = "有 $n 首本地文件已不在，已从列表移除"
        }
    }

    /** 离线播放：后台备好本地封面 / 歌词，回主线程交给播放器。 */
    fun preparePlay(track: com.guangyue.core.download.DownloadedTrack, start: (com.guangyue.core.data.model.TrackEntity) -> Unit) {
        viewModelScope.launch {
            if (!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { library.exists(track) }) {
                library.remove(track.key, deleteFile = true)
                _message.value = "本地文件已被删除，已从列表移除"
                return@launch
            }
            val entity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { library.toPlayable(track) }
            start(entity)
        }
    }

    fun remove(key: String) {
        val wasDone = library.find(key) != null
        library.remove(key)
        // 0.0.19：连同落盘的待下载请求一起删，否则下次启动又冒出来。
        if (!wasDone) manager.cancel(key)
        manager.storeRef.remove(key)
        _message.value = if (wasDone) "已删除本地文件和歌词" else "已移除"
    }

    fun cancelAll() {
        manager.cancelAll()
        _message.value = "已暂停全部下载"
    }

    /** 清理失败/取消记录，不删已下载的文件。 */
    fun clearFinished() {
        manager.clearFinished(deleteFiles = false)
        _message.value = "已清理失败记录"
    }

    fun clearMessage() {
        _message.value = null
    }
}
