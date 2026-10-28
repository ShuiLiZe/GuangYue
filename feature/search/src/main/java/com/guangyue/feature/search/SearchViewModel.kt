package com.guangyue.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.model.TrackMapper
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
    val item: MusicItem,
)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    /** Enabled plugins the user narrowed the search to; empty = all. */
    val platforms: List<String> = emptyList(),
    val available: List<PluginMeta> = emptyList(),
    val rows: List<SearchRow> = emptyList(),
    val errors: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val message: String? = null,
)

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
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

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

            val rows = mutableListOf<SearchRow>()
            val errors = mutableListOf<String>()
            for (plugin in targets) {
                val result = runCatching {
                    bridge.search(plugin.id, query, 1, SearchType.MUSIC)
                }.getOrNull()
                if (result == null || (result.items.isEmpty() && result.error != null)) {
                    errors += "${plugin.displayName}：${result?.error ?: "没有结果"}"
                    continue
                }
                result.items.forEach { rows += SearchRow(plugin.id, plugin.displayName, it) }
            }
            // Keep the cache warm so playback and lyrics can look the row up by key.
            runCatching { tracks.cache(rows.map { it.item }) }
            _state.value = _state.value.copy(loading = false, rows = rows, errors = errors)
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
}
