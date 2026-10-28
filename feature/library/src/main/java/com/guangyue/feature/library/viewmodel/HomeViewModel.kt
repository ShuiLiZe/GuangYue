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
    }
}
