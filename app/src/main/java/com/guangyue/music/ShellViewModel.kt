package com.guangyue.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用壳的状态：深浅色偏好。
 *
 * 只保存一个开关；其余页面状态都由各自的 ViewModel 负责，壳这一层不持有任何界面数据，
 * 避免重组时整棵树一起失效。
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _darkMode = MutableStateFlow(preferences.darkMode)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        _darkMode.value = dark
        viewModelScope.launch { preferences.darkMode = dark }
    }
}
