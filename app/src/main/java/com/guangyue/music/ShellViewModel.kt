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
 * App 外壳的状态：深浅色偏好。
 *
 * 0.0.9 起主题**默认跟随系统**（`uiMode`），只有用户在「我的」里手动切过一次，
 * 才记成显式偏好并一直用它。所以这里暴露的是「显式偏好」——`null` 表示还没选过，
 * 界面层用它去和 `isSystemInDarkTheme()` 兜底（见 [com.guangyue.music.MainActivity]）。
 *
 * 其余页面状态都各自 ViewModel 负责，外壳这一层不持有任何界面数据，避免重组时整棵树失效。
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _explicitDark = MutableStateFlow(
        if (preferences.hasDarkMode) preferences.darkMode else null,
    )

    /** `null` = 用户没选过，跟随系统；`true/false` = 用户显式指定。 */
    val explicitDark: StateFlow<Boolean?> = _explicitDark.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        _explicitDark.value = dark
        viewModelScope.launch { preferences.darkMode = dark }
    }

    /** 0.0.27：首页样式（[com.guangyue.core.prefs.HomeStyles]：wall = 封面墙，grid = 3D 滑卡）。首帧同步读。 */
    private val _homeStyle = MutableStateFlow(preferences.homeStyleId)
    val homeStyle: StateFlow<String> = _homeStyle.asStateFlow()

    fun setHomeStyle(id: String) {
        if (_homeStyle.value == id) return
        preferences.homeStyleId = id
        _homeStyle.value = preferences.homeStyleId
    }

    /** 0.0.28：网格首页卡片样式（[com.guangyue.core.prefs.GridCardStyles]）。 */
    private val _gridCardStyle = MutableStateFlow(preferences.gridCardStyle)
    val gridCardStyle: StateFlow<String> = _gridCardStyle.asStateFlow()

    fun setGridCardStyle(id: String) {
        if (_gridCardStyle.value == id) return
        preferences.gridCardStyle = id
        _gridCardStyle.value = preferences.gridCardStyle
    }
}
