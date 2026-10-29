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
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.plugin.UpdateCheck
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    /** 已启用的插件，供「推荐/榜单」去拉数据，也用来决定空状态文案。 */
    val enabledPlugins: StateFlow<List<PluginMeta>> =
        plugins.plugins.map { list -> list.filter { it.enabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** 插件返回的榜单条目（按标题缓存），点「热门榜单」时用它播放。 */
    private val _chartItems = MutableStateFlow<Map<String, MusicItem>>(emptyMap())
    val chartItems: StateFlow<Map<String, MusicItem>> = _chartItems.asStateFlow()

    private var chartsLoaded = false

    /**
     * 拉一次榜单：每个已启用插件搜一个通用关键词，取第一页结果做成条目。
     *
     * 只在首屏调用一次；失败就保持空列表，让首页显示引导，不弹错误。
     */
    fun loadChartsIfNeeded() {
        if (chartsLoaded) return
        chartsLoaded = true
        viewModelScope.launch {
            val plugins = enabledPlugins.value
            if (plugins.isEmpty()) return@launch
            _loadingCharts.value = true
            val tiles = mutableListOf<HomeTile>()
            val items = mutableMapOf<String, MusicItem>()
            plugins.take(6).forEach { plugin ->
                val hits = runCatching {
                    bridge.search(plugin.id, "热歌", 1, SearchType.MUSIC).items
                }.getOrDefault(emptyList())
                hits.take(6).forEach { item ->
                    val key = "chart:${plugin.id}:${item.title}:${item.displayArtist}"
                    if (!items.containsKey(key)) {
                        items[key] = item
                        tiles += HomeTile(
                            key = key,
                            title = item.title,
                            subtitle = item.displayArtist.ifBlank { plugin.displayName },
                            artwork = item.artwork,
                            kind = HomeTileKind.TRACK,
                        )
                    }
                }
            }
            _chartItems.value = items
            _charts.value = tiles
            _loadingCharts.value = false
        }
    }

    /** 推荐段：本地歌单优先，没有歌单时用最近播放补齐，保证首屏不空。 */
    fun recommended(playlists: List<HomeTile>, recent: List<HomeTile>): List<HomeTile> =
        if (playlists.isNotEmpty()) playlists else recent

    /** 「我的」页的计数：我喜欢 / 本地下载。 */
    val likedCount: StateFlow<Int> = tracks.favorites()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val downloadedCount: StateFlow<Int> = tracks.localTracks()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 点开一个首页 / 歌单卡片后播放它。
     *
     * 歌单卡片取该歌单的曲目；单曲卡片（榜单、最近播放）从榜单缓存里补齐来源信息。
     * 拿不到曲目时不回调，由界面保持原状。
     */
    fun playTracksOf(tile: HomeTile, onReady: (List<TrackEntity>, Int) -> Unit) {
        viewModelScope.launch {
            val list = when (tile.kind) {
                HomeTileKind.PLAYLIST -> {
                    val id = tile.key.substringAfter("playlist:").toLongOrNull()
                    if (id == null) {
                        emptyList()
                    } else {
                        runCatching { playlists.snapshot(id) }.getOrDefault(emptyList())
                    }
                }
                HomeTileKind.TRACK, HomeTileKind.ALBUM, HomeTileKind.STATION -> {
                    val item = _chartItems.value[tile.key]
                    if (item == null) {
                        val key = tile.key.removePrefix("track:")
                        runCatching { listOfNotNull(tracks.byKey(key)) }.getOrDefault(emptyList())
                    } else {
                        listOf(
                            TrackEntity(
                                key = "${item.platform}:${item.id}",
                                title = item.title,
                                artist = item.displayArtist,
                                album = item.album.orEmpty(),
                                artwork = item.artwork,
                                source = item.platform,
                            ),
                        )
                    }
                }
            }
            if (list.isNotEmpty()) onReady(list, 0)
        }
    }

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
                say("安装失败：" + (t.message ?: t.javaClass.simpleName), lastDetails)
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
                say("安装失败：" + (t.message ?: t.javaClass.simpleName), lastDetails)
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
                        "更新失败：" + ((result as? InstallResult.Failure)?.message ?: "未知原因")
                    }
                }
                is UpdateCheck.Error -> "检查更新失败：${r.message}"
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
            "安装失败：$message"
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
                say("安装失败：" + (t.message ?: t.javaClass.simpleName), lastDetails)
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
                say(r.summary(), r.failed.takeIf { it.isNotEmpty() }?.joinToString("\n") { (n, m) -> "· $n：${m.lineSequence().first()}" })
            } catch (t: Throwable) {
                say("检查更新失败：" + (t.message ?: t.javaClass.simpleName))
            } finally {
                _busy.value = false
            }
        }
    }

    /** 全选 / 全不选。 */
    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch { manager.setAllEnabled(enabled) }
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

    fun remove(key: String) {
        library.remove(key)
        manager.storeRef.remove(key)
        _message.value = "已删除本地文件"
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
