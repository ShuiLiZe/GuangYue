// V0：歌词样式调参的状态宿主。
// V1a：加上「样式切换」——当前样式（流光 / 浮名）也走同一个 ViewModel 与同一份落盘偏好。
//
// 对应 Folia 文件：
//   src/components/visualizer/classic/tuning.ts（`classic_tuning` 持久化 + `apply` 注入 tuning）
//   src/components/visualizer/registry.tsx（样式表 + 被选中的样式 id）
//
// 手机化改动：Folia 把调参挂在全局 store（`handleSetClassicTuning`），Compose 侧改成
// `ViewModel` + `StateFlow`，落盘仍走 [AppPreferences.classicTuning]（同一个 SharedPreferences）。
package com.guangyue.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.fume.FumeTuning
import com.guangyue.core.lyrics.PartitaTuning
import com.guangyue.core.lyrics.TiltTuning
import com.guangyue.core.prefs.AppPreferences
import com.guangyue.feature.player.lyrics.LyricStyle
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

    /**
     * 当前歌词渲染样式（V1a）。
     *
     * 同样首帧同步读：`AppPreferences` 存的是 String，`LyricStyle.fromId` 负责
     * `String → 枚举`（未知值回落 [LyricStyle.CLASSIC]）。播放页与歌词页共用同一份，
     * 在播放页切到「浮名」，进歌词页立刻是「浮名」。
     */
    private val _style = MutableStateFlow(LyricStyle.fromId(preferences.lyricStyleId))
    val style: StateFlow<LyricStyle> = _style.asStateFlow()

    /** V1b-4：「浮名」调参（Folia `fume/tuning.ts`，storage.key = 'fume_tuning'）。 */
    private val _fumeTuning = MutableStateFlow(preferences.fumeTuning)
    val fumeTuning: StateFlow<FumeTuning> = _fumeTuning.asStateFlow()

    fun updateFume(tuning: FumeTuning) {
        _fumeTuning.value = tuning
        viewModelScope.launch { preferences.fumeTuning = tuning }
    }

    /** V2a：「云阶」调参（Folia `partita/tuning.ts`，storage.key = 'partita_tuning'）。 */
    private val _partitaTuning = MutableStateFlow(preferences.partitaTuning)
    val partitaTuning: StateFlow<PartitaTuning> = _partitaTuning.asStateFlow()

    /** V2d：「倾诉」调参（Folia `tilt/tuning.ts`，storage.key = 'tilt_tuning'）。 */
    private val _tiltTuning = MutableStateFlow(preferences.tiltTuning)
    val tiltTuning: StateFlow<TiltTuning> = _tiltTuning.asStateFlow()

    fun updateTilt(tuning: TiltTuning) {
        val safe = TiltTuning.sanitize(tuning.splitProbability, tuning.tiltStyleProbability, tuning.colorScheme)
        _tiltTuning.value = safe
        viewModelScope.launch { preferences.tiltTuning = safe }
    }

    fun updatePartita(tuning: PartitaTuning) {
        val safe = PartitaTuning.sanitize(tuning.showGuideLines, tuning.useSemanticLayout, tuning.staggerMin, tuning.staggerMax)
        _partitaTuning.value = safe
        viewModelScope.launch { preferences.partitaTuning = safe }
    }

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

    /**
     * 切样式（V1a）：内存值先生效（弹层选中态与歌词画布立刻跟上），落盘异步。
     *
     * 与 [update] 同一套「先改内存、再 apply」的顺序，不做防抖 —— 样式是单选题，
     * 点一次写一次的量级可忽略。
     */
    fun pickStyle(style: LyricStyle) {
        _style.value = style
        viewModelScope.launch { preferences.lyricStyleId = style.id }
    }
}