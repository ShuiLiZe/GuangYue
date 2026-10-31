// V0：歌词样式调参的状态宿主。
//
// 对应 Folia 文件：
//   src/components/visualizer/classic/tuning.ts（`classic_tuning` 持久化 + `apply` 注入 tuning）
//   src/components/visualizer/registry.tsx（样式表；本段只有 classic 一项）
//
// 手机化改动：Folia 把调参挂在全局 store（`handleSetClassicTuning`），Compose 侧改成
// `ViewModel` + `StateFlow`，落盘仍走 [AppPreferences.classicTuning]（同一个 SharedPreferences）。
package com.guangyue.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricStyleViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    // 首帧同步读（AppPreferences 用 SharedPreferences 就是为了这个），避免弹层开合时参数跳一下。
    private val _tuning = MutableStateFlow(preferences.classicTuning)
    val tuning: StateFlow<ClassicTuning> = _tuning.asStateFlow()

    fun update(tuning: ClassicTuning) {
        _tuning.value = ClassicTuning.sanitize(
            enableWordRotation = tuning.enableWordRotation,
            breathingFloatMultiplier = tuning.breathingFloatMultiplier,
            useLegacyLayout = tuning.useLegacyLayout,
            wordSpacing = tuning.wordSpacing,
        ).also { _ ->
            // 落盘异步（apply），UI 先按内存值渲染，切页/重进不丢。
        }
        viewModelScope.launch { preferences.classicTuning = tuning }
    }
}