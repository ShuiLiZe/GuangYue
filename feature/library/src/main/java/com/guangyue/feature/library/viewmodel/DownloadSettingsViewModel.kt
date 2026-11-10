package com.guangyue.feature.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.download.DownloadConfig
import com.guangyue.core.download.DownloadManager
import com.guangyue.core.download.DownloadProgress
import com.guangyue.core.player.Quality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 下载设置页：设置读写走 [com.guangyue.core.download.DownloadSettings]，占用与清理走 [DownloadManager]。 */
@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val manager: DownloadManager,
) : ViewModel() {

    val config: StateFlow<DownloadConfig> = manager.settings.config

    /** 「已下载 N 首 · 占用 X」。 */
    val libraryText: StateFlow<String> = manager.library.tracks.map { list ->
        if (list.isEmpty()) "还没有下载过歌曲"
        else "已下载 ${list.size} 首 · 占用 ${DownloadProgress.formatBytes(list.sumOf { it.sizeBytes })}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), "")

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshCache()
    }

    fun refreshCache() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { runCatching { manager.cacheBytes() }.getOrDefault(0L) }
        }
    }

    fun pickQuality(wire: String) = manager.settings.setQuality(Quality.fromWire(wire))
    fun setSaveLyric(on: Boolean) = manager.settings.setSaveLyric(on)
    fun setTranslation(on: Boolean) = manager.settings.setLyricTranslation(on)
    fun setConcurrency(n: Int) = manager.settings.setConcurrency(n)

    fun clearCache() {
        viewModelScope.launch {
            val busy = manager.snapshot().any { it.isActive }
            val freed = withContext(Dispatchers.IO) { runCatching { manager.clearCache() }.getOrDefault(0L) }
            _cacheBytes.value = withContext(Dispatchers.IO) { runCatching { manager.cacheBytes() }.getOrDefault(0L) }
            _message.value = when {
                freed > 0L -> "已清理，释放了 ${DownloadProgress.formatBytes(freed)}" + if (busy) "（下载中的进度已保留）" else ""
                busy -> "有歌正在下载，进度文件先保留"
                else -> "没有可清理的暂存"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
