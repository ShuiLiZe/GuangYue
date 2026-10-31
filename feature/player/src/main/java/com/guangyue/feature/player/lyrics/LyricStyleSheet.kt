package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.FoliaToggle
import kotlin.math.roundToInt

/**
 * V0「歌词样式」底部弹层
 *
 * 对应 Folia 文件：
 * - `src/components/visualizer/window/`（样式窗口 / 样式切换器，桌面浮窗形态）
 * - `src/components/visualizer/registry.tsx`（`VISUALIZER_REGISTRY`，本段只有 classic 一项可选）
 * - `src/components/visualizer/classic/tuning.ts`（`enableWordRotation` / `breathingFloatMultiplier` /
 *   `wordSpacing` 三项调参，`zeroToTwo` 钳 [0,2]，`storage.resetNotice = 'notifications.classicReset'`）
 * - `src/i18n/locales/zh-CN.ts:915 "visualizerClassic": "流光"`、`:2495 "lyricsStyleSettings"`、
 *   `:2496 "lyricStyles"`、`:2497 "lyricStyleNoOptions"`、`:5363 "lyricStyle"`
 *
 * 手机化改动（原因见 docs/ui-v0/report.md）：
 * 1. Folia 的样式窗口是桌面浮窗（拖拽/多列预览），手机上改成 `ModalBottomSheet`，与 U4 的
 *    `QualityPickerSheet` 同一范式（56dp 行高、14dp 圆角、选中底色 accent@0.10）。
 * 2. 本段只列出「流光」一项，并且不显示其它样式占位（brief 要求）；Folia 的样式窗口会列出全部
 *    内置渲染器。
 * 3. 实时预览：Folia 在浮窗里有独立预览画布；这里直接让歌词页本身实时生效（弹层只列参数，
 *    调整后立刻作用于身后的歌词画布），省一块预览资源。
 */

/** 弹层文案表：与 `app/src/main/res/values/strings.xml` 的「V0 歌词样式」段逐键对应。 */
@Immutable
data class LyricStyleStrings(
    val title: Int,
    val classic: Int,
    val rotation: Int,
    val breathing: Int,
    val spacing: Int,
    val reset: Int,
    val desc: Int,
    val close: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricStyleSheet(
    tuning: ClassicTuning,
    strings: LyricStyleStrings,
    styleName: String,
    onTuning: (ClassicTuning) -> Unit,
    onDismiss: () -> Unit,
    accent: Color = Gyp.accent,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Gyp.card,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = GuangYueIcons.Sparkles,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.width(8.dp))
                Text(
                    text = str(strings.title),
                    fontSize = 17.sp,
                    fontFamily = com.guangyue.feature.library.design.GuangYueDesign.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Gyp.ink,
                )
            }
            Text(
                text = str(strings.desc),
                fontSize = 11.sp,
                color = Gyp.inkSoft,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            // 样式列表：本段唯一可选「流光」（classic）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 4.dp)
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .clickableNoRipple { onDismiss() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(styleName, fontSize = 15.sp, color = Gyp.ink)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(accent, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = GuangYueIcons.Check,
                            contentDescription = null,
                            tint = Gyp.onAccent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            Box(Modifier.height(GySpace.s))

            FoliaToggle(
                checked = tuning.enableWordRotation,
                onChange = { onTuning(tuning.copy(enableWordRotation = it)) },
            )
            Text(
                text = str(strings.rotation),
                fontSize = 13.sp,
                color = Gyp.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(top = 12.dp),
            )

            TuningSliderRow(
                label = str(strings.breathing),
                value = tuning.breathingFloatMultiplier.toFloat(),
                accent = accent,
                onChange = { onTuning(tuning.copy(breathingFloatMultiplier = it.toDouble())) },
            )
            TuningSliderRow(
                label = str(strings.spacing),
                value = tuning.wordSpacing.toFloat(),
                accent = accent,
                onChange = { onTuning(tuning.copy(wordSpacing = it.toDouble())) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickableNoRipple { onTuning(ClassicTuning()) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = GuangYueIcons.RotateCcw,
                        contentDescription = null,
                        tint = Gyp.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(Modifier.width(8.dp))
                    Text(str(strings.reset), fontSize = 13.sp, color = Gyp.accent)
                }
            }
            Box(Modifier.height(GySpace.m))
        }
    }
}

/** 调参滑杆行（0–2 倍，与 `classic/tuning.ts zeroToTwo` 的钳位一致）。 */
@Composable
private fun TuningSliderRow(
    label: String,
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = Gyp.ink)
            Text(
                text = "%.2f×".format(value),
                fontSize = 12.sp,
                color = Gyp.inkSoft,
            )
        }
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = { onChange(it.coerceIn(0f, 2f)) },
                valueRange = 0f..2f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Gyp.track,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 与 U5 同款：弹层里也走 `LyricStringHost`，保证文案与外层同一份资源。 */
@Composable
private fun str(id: Int): String = LyricStringHost.current(id)

/** 取整辅助（滑杆 steps 的落地值，报告里引用 zeroToTwo 的两位小数形态）。 */
internal fun ClassicTuning.percents(): Int = (breathingFloatMultiplier * 100).roundToInt()