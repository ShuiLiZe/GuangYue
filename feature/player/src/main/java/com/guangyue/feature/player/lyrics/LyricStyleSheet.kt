package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 * 2. V1a 起列出两项：「流光」(classic) 与「浮名」(fume) —— 对应 Folia 的
 *    `zh-CN.ts:915 "visualizerClassic"` 与 `ui.visualizerFume`；其余内置渲染器仍不列。
 * 3. 实时预览：Folia 在浮窗里有独立预览画布；这里直接让歌词页本身实时生效（弹层只列参数，
 *    调整后立刻作用于身后的歌词画布），省一块预览资源。
 * 4. V1a 的样式行只切样式、**不**关弹层：选中态（右侧 accent 圆 + Check）要在原地看得见，
 *    与 Folia 样式窗口「点一下立刻换渲染器、窗口留着」的手感一致；下滑 / 点外部才关。
 * 5. tuning 面板仍只服务「流光」（V1b 才补「浮名」自己那套 tuning UI）。
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
    /**
     * V1a：「浮名」（Folia key `ui.visualizerFume`）的样式名。
     *
     * 放构造参数末尾并给默认值 0：既有调用点（V0 截图用例 / 旧 NavHost）不传也不会编译失败；
     * 真实入口（NavHost）与测试都显式传值。0 不是合法资源 id，[LyricStringHost.current] 收到 0
     * 时由宿主兜底，不会把 0 当文案显示。
     */
    val fume: Int = 0,
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
    /** V1a：当前选中样式（默认 [LyricStyle.CLASSIC]，老调用点不传也保持「流光」选中）。 */
    style: LyricStyle = LyricStyle.CLASSIC,
    /** V1a：「浮名」的样式名文案（文案 id 现取现用，这里只收已解析好的中文）。 */
    fumeName: String = "",
    /** V1a：点样式行 → 只切换样式，**不**关弹层（让用户看到选中态，对应 Folia 样式窗口的即选即显）。 */
    onStyle: (LyricStyle) -> Unit = {},
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

            // 样式列表：V1a 两项 —— 「流光」(classic) 与「浮名」(fume)。
            // 与 Folia 样式窗口一致：一行 = 左样式名、右选中标记；点击只切样式、不关弹层。
            StyleRow(
                name = styleName,
                selected = style == LyricStyle.CLASSIC,
                accent = accent,
                onClick = { onStyle(LyricStyle.CLASSIC) },
            )
            StyleRow(
                // 文案 id 现取现用：这里拿的是 NavHost 已解析好的中文，绝不把 id 当字符串显示。
                // fumeName 缺失（老调用点 / 只传 styleName）时退回 classic 名，避免空行。
                name = fumeName.ifBlank { styleName },
                selected = style == LyricStyle.FUME,
                accent = accent,
                onClick = { onStyle(LyricStyle.FUME) },
            )

            Box(Modifier.height(GySpace.s))

            // Folia kit/Row.tsx：一行 = 左标签、右控件（`flex min-h-14 items-center`，标签在左、控件在右）。
            // 原来开关单独一行、标签在下一行（两行错位），这里合成同一行。
            ToggleRow(
                label = str(strings.rotation),
                checked = tuning.enableWordRotation,
                onChange = { onTuning(tuning.copy(enableWordRotation = it)) },
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

/**
 * 样式行（V1a）：一行 = 左侧样式名 + 右侧选中标记。
 *
 * 外观沿用 V0 唯一的「流光」行：56dp 高（≥48dp 触控）、14dp 圆角、accent@0.10 底；
 * 右侧 20dp accent 实心圆 + Check 只在**选中**时显示 —— 未选中时用等宽 Spacer 占位，
 * 保证两行文字左端对齐、320dp 窄屏不因标记出现/消失而抖动或溢出。
 * 点击走 [clickableNoRipple]（与弹层其它行同一手感），且**不**触发关闭。
 */
@Composable
private fun StyleRow(
    name: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp)
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                color = Gyp.ink,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(16.dp))
            if (selected) {
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
            } else {
                // 未选中：占位不画标记，两行右侧留白一致（HeightIn 保持 56dp 行高不塌）。
                Box(Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 开关行：照 Folia `kit/Row.tsx` + `panelRows.tsx ToggleRow` —— 一行里「左标签、右开关」，
 * 行高 min 56dp（`min-h-14`）、右侧留 16dp（`pr-4`）。
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickableNoRipple { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Gyp.ink,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(24.dp))
        FoliaToggle(checked = checked, onChange = onChange)
    }
}

/**
 * 调参滑杆行（0–2 倍，与 `classic/tuning.ts zeroToTwo` 的钳位一致）。
 *
 * 外观照 Folia `kit/Slider.tsx`
 * - `stacked` 形态：数值在标签行右端（13px、tabular-nums、`--st-t2`），轨道独占下一行；
 * - 轨道高 1dp（`h-1`）、全圆角，填充段用 accent、未填充段用 track（`linear-gradient` 的两段）；
 * - 滑钮 16dp 纯白圆点（`h-4 w-4 bg-white`）+ `0 0 0 1px rgba(0,0,0,0.14), 0 1px 3px rgba(0,0,0,0.35)`
 *   的双层描边阴影，按下时放大到 1.15 倍（`st-slider:active` 的 transform 缩放）；
 * - 不用 Material 的默认点状轨道 / 大圆拇指。
 */
@Composable
private fun TuningSliderRow(
    label: String,
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // stacked：标签与数值同一行，数值靠右（Folia Slider stacked 的 `<output class="ml-auto">`）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, lineHeight = 20.sp, color = Gyp.ink)
            Text(
                text = "%.2fx".format(value),
                fontSize = 13.sp,
                color = Gyp.t2,
                style = androidx.compose.ui.text.TextStyle(
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        // 轨道：1dp 圆角线 + 白点滑钮，拖动热区 44dp（Folia input 高 20px，手机上抬到 44dp 保触控）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            FoliaSlider(
                value = value,
                accent = accent,
                onChange = onChange,
            )
        }
    }
}

/**
 * Folia `kit/Slider.tsx` 的视觉与手感，用 Compose 基础能力重画（不用 Material Slider ——
 * 它的点状刻度轨道 + 大号拇指与 Folia tuning 面板完全不同）。
 *
 * - 轨道：高 1dp（`h-1`）全圆角，未填充段 `Gyp.track`，填充段 accent 线性渐变；
 * - 滑钮：16dp 纯白圆点（`h-4 w-4 bg-white`），外加 1px 深色描边 + 1px 下偏移阴影；
 * - 拖动：整个 44dp 高的行都吃手势（pointerInput），换算成 0..2 的值后按 0.1 取整。
 */
@Composable
private fun FoliaSlider(
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val thumbDp = 16.dp
    val thumbInsetPx = with(density) { FoliaSliderThumbInsetDp.dp.toPx() }
    val trackColor = Gyp.track
    var trackWidthPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction = (value.coerceIn(0f, 2f) / 2f)
    val active = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .onSizeChanged { trackWidthPx = it.width.toFloat() }
        .pointerInput(trackWidthPx, accent) {
            detectTapGestures(
                onPress = { offset ->
                    dragging = true
                    val usable = (trackWidthPx - thumbInsetPx * 2f).coerceAtLeast(1f)
                    val raw = ((offset.x - thumbInsetPx) / usable).coerceIn(0f, 1f) * 2f
                    onChange((raw * 10f).roundToInt() / 10f)
                    tryAwaitRelease()
                    dragging = false
                },
            )
        }
        .pointerInput(trackWidthPx, accent) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragging = true
                    val usable = (trackWidthPx - thumbInsetPx * 2f).coerceAtLeast(1f)
                    val raw = ((offset.x - thumbInsetPx) / usable).coerceIn(0f, 1f) * 2f
                    onChange((raw * 10f).roundToInt() / 10f)
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onHorizontalDrag = { change, _ ->
                    val usable = (trackWidthPx - thumbInsetPx * 2f).coerceAtLeast(1f)
                    val raw = ((change.position.x - thumbInsetPx) / usable).coerceIn(0f, 1f) * 2f
                    onChange((raw * 10f).roundToInt() / 10f)
                },
            )
        }
    Box(modifier = active, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val trackHeight = size.height
            drawRoundRect(color = trackColor, cornerRadius = CornerRadius(trackHeight / 2f))
            val fillEnd = (size.width - thumbInsetPx * 2f) * fraction + thumbInsetPx
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.92f), accent), startX = 0f, endX = size.width),
                size = Size(fillEnd.coerceAtLeast(1f), trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = (FoliaSliderThumbInsetDp.dp * fraction * 2f))
                .size(if (dragging) 18.dp else thumbDp)
                .shadow(elevation = 1.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
                .border(width = 0.5.dp, color = Color.Black.copy(alpha = 0.14f), shape = CircleShape),
        )
    }
}

/** Folia kit 滑杆：轨道 1dp（`h-1`），滑钮 16dp（`h-4 w-4`），图形本身不做 Material 的默认样式。 */
private val FoliaSliderTrackDp = 20f
private val FoliaSliderThumb = Color.White

/** 滑钮中心左右各让出的距离（对应 Folia `THUMB_INSET = 8`）。 */
private const val FoliaSliderThumbInsetDp = 8f

/** Folia tuning 的 step：0–2 之间按 0.1 取整（zeroToTwo 保留两位小数，滑杆点击数 19 个中间值）。 */
private const val FOLIA_SLIDER_STEPS = 19

/** 与 U5 同款：弹层里也走 `LyricStringHost`，保证文案与外层同一份资源。 */
@Composable
private fun str(id: Int): String = LyricStringHost.current(id)

/** 取整辅助（滑杆 steps 的落地值，报告里引用 zeroToTwo 的两位小数形态）。 */
internal fun ClassicTuning.percents(): Int = (breathingFloatMultiplier * 100).roundToInt()