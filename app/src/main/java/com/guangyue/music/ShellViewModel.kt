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
    // 11-15：Folia 手机版只有网格首页；旧设置里存成墙面的也一律回网格。
    private val _homeStyle = MutableStateFlow(com.guangyue.core.prefs.HomeStyles.GRID)
    val homeStyle: StateFlow<String> = _homeStyle.asStateFlow()

    fun setHomeStyle(id: String) {
        if (_homeStyle.value == id) return
        preferences.homeStyleId = id
        _homeStyle.value = preferences.homeStyleId
    }

    /** 0.0.33：歌单详情界面（网格 / 墙面），见 [com.guangyue.core.prefs.AppPreferences.collectionStyleId]。 */
    private val _collectionStyle = MutableStateFlow(preferences.collectionStyleId)
    val collectionStyle: StateFlow<String> = _collectionStyle.asStateFlow()

    fun setCollectionStyle(id: String) {
        if (_collectionStyle.value == id) return
        preferences.collectionStyleId = id
        _collectionStyle.value = preferences.collectionStyleId
    }

    /** 0.0.28：网格首页卡片样式（[com.guangyue.core.prefs.GridCardStyles]）。 */
    private val _gridCardStyle = MutableStateFlow(preferences.gridCardStyle)
    val gridCardStyle: StateFlow<String> = _gridCardStyle.asStateFlow()

    fun setGridCardStyle(id: String) {
        if (_gridCardStyle.value == id) return
        preferences.gridCardStyle = id
        _gridCardStyle.value = preferences.gridCardStyle
    }

    // 11-17：Folia 首页设置「顶部胶囊入口」「记住主页卡片位置」与播放页「播放后进入的视图」。
    private val _homeTabsHidden = MutableStateFlow(preferences.homeTabsHidden)
    val homeTabsHidden: StateFlow<Set<String>> = _homeTabsHidden.asStateFlow()
    fun setHomeTabVisible(tab: String, visible: Boolean) {
        val next = if (visible) _homeTabsHidden.value - tab else _homeTabsHidden.value + tab
        preferences.homeTabsHidden = next
        _homeTabsHidden.value = next
    }

    private val _rememberHomeCard = MutableStateFlow(preferences.rememberHomeCard)
    val rememberHomeCard: StateFlow<Boolean> = _rememberHomeCard.asStateFlow()
    fun setRememberHomeCard(on: Boolean) { preferences.rememberHomeCard = on; _rememberHomeCard.value = on }

    private val _ponderTouchButton = MutableStateFlow(preferences.ponderTouchButton)
    val ponderTouchButton: StateFlow<Boolean> = _ponderTouchButton.asStateFlow()
    fun setPonderTouchButton(on: Boolean) { preferences.ponderTouchButton = on; _ponderTouchButton.value = on }

    /** 本次打开应用期间各页签的卡片位置（Folia：位置仅在本次打开应用期间保留）。 */
    val homeCardMemory: MutableMap<String, Int> = HashMap()

    private val _playbackEntryView = MutableStateFlow(preferences.playbackEntryView)
    val playbackEntryView: StateFlow<String> = _playbackEntryView.asStateFlow()
    fun setPlaybackEntryView(id: String) { preferences.playbackEntryView = id; _playbackEntryView.value = preferences.playbackEntryView }
}
