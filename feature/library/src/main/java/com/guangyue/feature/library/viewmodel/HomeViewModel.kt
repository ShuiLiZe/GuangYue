package com.guangyue.feature.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.db.GuangYueDatabase
import com.guangyue.core.data.model.PlaylistEntity
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.data.repository.PlaylistRepository
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.plugin.InstallResult
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.UpdateCheck
import com.guangyue.feature.library.home.ShelfCard
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

/** Home tab order: 歌单 / 电台 / 专辑 / 本地. */
enum class HomeTab(val label: String) {
    SHELF("歌单"),
    RADIO("电台"),
    ALBUM("专辑"),
    LOCAL("本地"),
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val tracks: TrackRepository,
    db: GuangYueDatabase,
) : ViewModel() {

    private val stationDao = db.stationDao()

    private val _tab = MutableStateFlow(HomeTab.SHELF)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    val cards: StateFlow<List<ShelfCard>> = combine(
        playlists.playlists(),
        tracks.recentlyPlayed(20),
        tracks.localTracks(),
        stationDao.observeAll(),
    ) { shelves, recent, local, stations ->
        when (_tab.value) {
            HomeTab.SHELF -> shelves.map { it.toCard() }.ifEmpty {
                recent.map { ShelfCard(it.key.hashCode().toLong(), it.title, it.artist, it.artwork) }
            }

            HomeTab.RADIO -> stations.map { ShelfCard(it.id, it.name, "电台 · ${it.seedType}", null) }

            HomeTab.ALBUM -> recent.filter { it.album.isNotBlank() }
                .distinctBy { it.album }
                .map { ShelfCard(it.album.hashCode().toLong(), it.album, it.artist, it.artwork) }

            HomeTab.LOCAL -> local.map { ShelfCard(it.key.hashCode().toLong(), it.title, it.artist, it.artwork) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tracks of the currently focused tab, used when the user taps "play". */
    val currentTracks: StateFlow<List<TrackEntity>> = combine(
        playlists.playlists(),
        tracks.recentlyPlayed(50),
        tracks.localTracks(),
    ) { _, recent, local ->
        if (_tab.value == HomeTab.LOCAL) local else recent
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: HomeTab) {
        _tab.value = tab
    }

    private fun PlaylistEntity.toCard() =
        ShelfCard(id = id, title = name, subtitle = description ?: "歌单", artworkUrl = artwork)
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
                _message.value = manager.installCode(code, "local-file").describe()
            } catch (t: Throwable) {
                _message.value = "安装失败：" + (t.message ?: t.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    fun report(text: String) {
        _message.value = text
    }

    fun installFromUrl(url: String) = run {
        if (url.isBlank()) return@run
        _busy.value = true
        viewModelScope.launch {
            try {
                _message.value = manager.installFromUrl(url.trim()).describe()
            } catch (t: Throwable) {
                _message.value = "安装失败：" + (t.message ?: t.javaClass.simpleName)
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
    }

    private fun InstallResult.describe(): String = when (this) {
        is InstallResult.Success -> "已安装「${meta.displayName}」v${meta.version}"
        is InstallResult.Failure -> "安装失败：$message"
        is InstallResult.BatchInstalled -> result.summary()
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
                _message.value = manager.installFromUrl(PluginViewModelDefaults.OFFICIAL_SOURCE).describe()
            } catch (t: Throwable) {
                _message.value = "安装失败：" + (t.message ?: t.javaClass.simpleName)
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
                _message.value = manager.checkUpdates(installDiscovered = true).summary()
            } catch (t: Throwable) {
                _message.value = "检查更新失败：" + (t.message ?: t.javaClass.simpleName)
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
