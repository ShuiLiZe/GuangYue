package com.guangyue.feature.library.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.plugin.lx.LxSource
import com.guangyue.core.plugin.lx.LxSourceManager
import com.guangyue.core.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 取链源（洛雪自定义源）管理页的状态机。
 *
 * 只做转发与提示，真正的装载/试跑逻辑在 [LxSourceManager] —— 那边是 Singleton，
 * 播放链路（`PluginMediaSourceResolver`）也用它，两边必须看到同一份列表。
 */
@HiltViewModel
class LxViewModel @Inject constructor(
    private val manager: LxSourceManager,
    private val prefs: AppPreferences,
) : ViewModel() {

    val sources: StateFlow<List<LxSource>> = manager.sources
    val busy: StateFlow<Boolean> = manager.busy
    val message: StateFlow<String?> = manager.message

    private val _errorDetails = MutableStateFlow<String?>(null)
    val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()

    /** 默认音质（128k / 320k / flac / flac24bit）。 */
    private val _quality = MutableStateFlow(prefs.lxDefaultQuality)
    val quality: StateFlow<String> = _quality.asStateFlow()

    init {
        viewModelScope.launch { manager.load() }
    }

    fun pickQuality(wire: String) {
        prefs.lxDefaultQuality = wire
        _quality.value = prefs.lxDefaultQuality
    }

    fun installFromUri(uri: Uri) {
        viewModelScope.launch {
            val code = runCatching {
                manager.installFromUriCompat(uri)
            }.getOrElse { t ->
                manager.report("读不到这个文件：${t.message ?: "未知原因"}")
                _errorDetails.value = t.stackTraceToString()
                return@launch
            }
            if (code) _errorDetails.value = null else _errorDetails.value = manager.lastErrorDetails
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            val ok = manager.installFromUrl(url)
            _errorDetails.value = if (ok) null else manager.lastErrorDetails
        }
    }

    fun installFromCode(code: String, srcUrl: String? = null) {
        viewModelScope.launch {
            val ok = manager.installScript(code, srcUrl)
            _errorDetails.value = if (ok) null else manager.lastErrorDetails
        }
    }

    fun toggle(source: LxSource, enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(source.id, enabled) }
    }

    fun delete(source: LxSource) {
        viewModelScope.launch { manager.delete(source.id) }
    }

    fun recheck(source: LxSource) {
        viewModelScope.launch {
            manager.recheck(source.id)
            _errorDetails.value = manager.lastErrorDetails
        }
    }

    fun recheckAll() {
        viewModelScope.launch {
            manager.recheckAll()
            _errorDetails.value = manager.lastErrorDetails
        }
    }

    fun move(source: LxSource, to: Int) {
        viewModelScope.launch { manager.move(source.id, to) }
    }

    fun clearMessage() = manager.clearMessage()
}