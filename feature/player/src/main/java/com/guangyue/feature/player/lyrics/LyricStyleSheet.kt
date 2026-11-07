@file:OptIn(ExperimentalMaterial3Api::class)

package com.guangyue.feature.player.lyrics

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.PartitaTuning
import com.guangyue.core.lyrics.fume.FumeCameraTrackingMode
import com.guangyue.core.lyrics.fume.FumeTuning
import com.guangyue.feature.library.design.GyMotion
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import kotlin.math.roundToInt

/**
 * V0「歌词样式」底部弹层（V1b-2：去 Material 风格 + 滑杆对齐）
 *
 * 对应 Folia 文件：
 * - `src/components/visualizer/window/`（样式窗口 / 样式切换器，桌面浮窗形态）
 * - `src/components/visualizer/registry.tsx`（`VISUALIZER_REGISTRY`，本段只有 classic 一项可选）
 * - `src/components/visualizer/classic/tuning.ts`（`enableWordRotation` / `breathingFloatMultiplier` /
 *   `wordSpacing` 三项调参，`zeroToTwo` 钳 [0,2]，`storage.resetNotice = 'notifications.classicReset'`）
 * - `src/components/modal/settings/kit/Slider.tsx:23`（`THUMB_INSET = 8`）、`:32-42`（轨道 `h-1`、拇指 `h-4 w-4`）、
 *   `:120-140`（stacked：数值在本行右端、轨道独占下一行）
 * - `src/components/modal/settings/kit/Toggle.tsx:16-30`（40×24 胶囊、18dp 滑钮、按下加宽到 22dp）
 * - `src/index.css:163-168`（`.theme-glass-panel`：`--bg-color` 85% + `--text-primary` 12% 描边）
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
 * 6. V1b-2：面板、开关、滑杆全部自绘/自配，不再用 Material 的 `Slider` / `Switch` 默认外观。
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
    /**
     * V1b-4：「浮名参数」各行文案（Folia zh-CN `options.fume*`）。同样默认 0，老调用点不传不报错；
     * 0 时该行标签退回空串（取证 / NavHost 都显式传值）。
     */
    val fumeHidePrintSymbols: Int = 0,
    val fumeGeometricBackground: Int = 0,
    val fumeBackgroundObjectOpacity: Int = 0,
    val fumeTextHoldRatio: Int = 0,
    val fumeCameraSpeed: Int = 0,
    val fumeCameraTrackingMode: Int = 0,
    val fumeCameraTrackingStepped: Int = 0,
    val fumeCameraTrackingSmooth: Int = 0,
    val fumeGlowIntensity: Int = 0,
    val fumeHeroScale: Int = 0,
    /** V2a：「云阶」样式名与参数文案（Folia zh-CN `ui.visualizerPartita` / `options.partita*`）。 */
    val partita: Int = 0,
    val partitaGuideLines: Int = 0,
    val partitaSemanticLayout: Int = 0,
    val partitaStaggerMin: Int = 0,
    val partitaStaggerMax: Int = 0,
    /** V2b：「心象」样式名（Folia `ui.visualizerCadenze`）与「这个样式没有参数」（`lyricStyleNoOptions`）。 */
    val cadenza: Int = 0,
    val noOptions: Int = 0,
)

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
    /**
     * V1b-2：面板形状。手机入口用底部弹层默认的「上方两角圆、贴底」；
     * 取证截图（[SheetPreview]）用 28dp 全圆角，四角一起看得到。
     */
    sheetShape: RoundedCornerShape = LyricSheetShape,
    /** V1b-4：「浮名」调参（Folia `fume/tuning.ts`）。选中浮名时面板换成这一组。 */
    fumeTuning: FumeTuning = FumeTuning.DEFAULT,
    onFumeTuning: (FumeTuning) -> Unit = {},
    partitaName: String = "",
    partitaTuning: PartitaTuning = PartitaTuning.DEFAULT,
    onPartitaTuning: (PartitaTuning) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = sheetShape,
        // Material 默认在弹层底下盖一层 32% 遮罩；它会把身后的封面调色板压灰，
        // 毛玻璃面板（半透底）就透不出播放页的画面。这里收到 10%，与播放页浮层一致。
        scrimColor = Color.Black.copy(alpha = 0.10f),
        // Material 的拖拽把手（4dp×32dp 灰条）不属于这套视觉。
        dragHandle = null,
    ) {
        LyricStylePanelContent(
            tuning = tuning,
            strings = strings,
            styleName = styleName,
            onTuning = onTuning,
            accent = accent,
            style = style,
            fumeName = fumeName,
            onStyle = onStyle,
            fumeTuning = fumeTuning,
            onFumeTuning = onFumeTuning,
            partitaName = partitaName,
            partitaTuning = partitaTuning,
            onPartitaTuning = onPartitaTuning,
        )
    }
}

/**
 * 面板本体（V1b-2 抽出）：毛玻璃底 + 10dp 圆角 + 发丝描边 + 手柄，内容为一列。
 *
 * 抽出来是为了能在同一份代码上做「弹层之外」的确定性截图（[SheetPreview]），
 * 免得实拍每次都要赌弹层动画的结束时刻。
 */
@Composable
private fun LyricStylePanelContent(
    tuning: ClassicTuning,
    strings: LyricStyleStrings,
    styleName: String,
    onTuning: (ClassicTuning) -> Unit,
    accent: Color,
    style: LyricStyle,
    fumeName: String,
    onStyle: (LyricStyle) -> Unit,
    fumeTuning: FumeTuning = FumeTuning.DEFAULT,
    onFumeTuning: (FumeTuning) -> Unit = {},
    partitaName: String = "",
    partitaTuning: PartitaTuning = PartitaTuning.DEFAULT,
    onPartitaTuning: (PartitaTuning) -> Unit = {},
) {
    // Folia `index.css:163-168` `.theme-glass-panel`：`--bg-color` 85% 底 + `--text-primary` 12% 描边。
    // 面板改用 `--st-card` 一族（与弹层其它行同源），透明度取 85%，透出身后的封面调色板。
    val glassFill = Gyp.card.copy(alpha = 0.85f)
    val glassBorder = Gyp.line2
    // V1b-4：浮名参数一长串，面板最高占屏 82%（留出状态栏与身后舞台），超出部分在面板里滚。
    val maxPanelHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.82f).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxPanelHeight)
            .clip(LyricSheetShape)
            .background(glassFill)
            .border(1.dp, glassBorder, LyricSheetShape)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        // V1b-4：浮名有 8 项参数，360dp 高的屏放不下 → 整列可滚（手柄与标题一起滚走，和 Folia 设置窗一致）。
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // 手柄：自绘 36×4、圆角胶囊、`--st-t3`（Material 默认把手是 32×4 的纯灰）。
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(Gyp.t3, RoundedCornerShape(999.dp)),
                )
            }
            Spacer(Modifier.height(12.dp))
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
            if (partitaName.isNotBlank()) {
                StyleRow(
                    name = partitaName,
                    selected = style == LyricStyle.PARTITA,
                    accent = accent,
                    onClick = { onStyle(LyricStyle.PARTITA) },
                )
            }
            if (strings.cadenza != 0) {
                StyleRow(
                    name = str(strings.cadenza),
                    selected = style == LyricStyle.CADENZA,
                    accent = accent,
                    onClick = { onStyle(LyricStyle.CADENZA) },
                )
            }

            Box(Modifier.height(GySpace.s))

            // Folia kit/Row.tsx：一行 = 左标签、右控件（`flex min-h-14 items-center`，标签在左、控件在右）。
            // 原来开关单独一行、标签在下一行（两行错位），这里合成同一行。
            if (style == LyricStyle.FUME) {
                FumeTuningRows(
                    tuning = fumeTuning,
                    strings = strings,
                    accent = accent,
                    onTuning = onFumeTuning,
                )
            } else if (style == LyricStyle.CADENZA) {
                // Folia cadenza/entry.tsx 没有 renderSettingsPanel：窗口里显示「这个样式没有参数」。
                Text(
                    text = str(strings.noOptions),
                    fontSize = 13.sp,
                    color = Gyp.t2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else if (style == LyricStyle.PARTITA) {
                PartitaTuningRows(
                    tuning = partitaTuning,
                    strings = strings,
                    accent = accent,
                    onTuning = onPartitaTuning,
                )
            } else {
                ToggleRow(
                    label = str(strings.rotation),
                    checked = tuning.enableWordRotation,
                    accent = accent,
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickableNoRipple {
                        // 恢复默认只作用于当前样式（Folia 各模式各自的 handleReset*Tuning）。
                        when (style) {
                            LyricStyle.CADENZA -> Unit
                            LyricStyle.FUME -> onFumeTuning(FumeTuning.DEFAULT)
                            LyricStyle.PARTITA -> onPartitaTuning(PartitaTuning.DEFAULT)
                            else -> onTuning(ClassicTuning())
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = GuangYueIcons.RotateCcw,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(Modifier.width(8.dp))
                    Text(str(strings.reset), fontSize = 13.sp, color = accent)
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
 *
 * V1b-2：毛玻璃面板底色本身就带 alphe，选中行再叠 accent@0.10 会显得发脏，
 * 这里把选中底改成 accent@0.14 + 1dp accent@0.22 描边，未选中行给 `--st-fill` 7%，
 * 两行都在玻璃面上读得出层次。
 */
@Composable
private fun StyleRow(
    name: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp)
            .background(
                if (selected) accent.copy(alpha = 0.14f) else Gyp.fill2,
                shape,
            )
            .then(
                if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.22f), shape) else Modifier,
            )
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
    accent: Color,
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
        // V1b-2：原来的 [FoliaToggle] 是 Material `Switch` 换色（Material 的拇指/轨迹尺寸与波形弧），
        // 换成本文件里的自绘 [FoliaSwitch]（Folia `kit/Toggle.tsx` 的 40×24 胶囊 + 18dp 滑钮）。
        FoliaSwitch(checked = checked, accent = accent, onChange = onChange)
    }
}

/**
 * Folia `kit/Toggle.tsx:16-30` 的自绘开关。
 *
 * - 胶囊 40×24（`h-6 w-10`）、全圆角；开 = `--st-accent`，关 = `--st-track`；
 * - 滑钮 18dp（`h-[18px] w-[18px]`），距两边各 3dp（`left-[3px] top-[3px]`），开启平移 16dp（`translate-x-4`）；
 * - 按下时滑钮加宽到 22dp、开启态少推 4dp（`group-active:w-[22px]` / `translate-x-3`），
 *   时长 `st-t-move` = [GyMotion.MOVE_MS] 220ms。
 */
@Composable
private fun FoliaSwitch(
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    // V1b-4 修：同滑杆，pointerInput 只按 checked 重启，回调须读最新值。
    val latestOnChange by rememberUpdatedState(onChange)
    val knobWidth by animateDpAsState(
        targetValue = if (pressed) 22.dp else 18.dp,
        animationSpec = tween(GyMotion.MOVE_MS, easing = GyMotion.easeStandard),
        label = "folioSwitchKnobWidth",
    )
    val knobOffset by animateDpAsState(
        targetValue = when {
            checked && pressed -> 12.dp
            checked -> 16.dp
            else -> 0.dp
        },
        animationSpec = tween(GyMotion.MOVE_MS, easing = GyMotion.easeStandard),
        label = "folioSwitchKnobOffset",
    )
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) accent else Gyp.track,
        animationSpec = tween(GyMotion.MOVE_MS, easing = GyMotion.easeStandard),
        label = "foliaSwitchTrack",
    )
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(trackColor, CircleShape)
            .pointerInput(checked) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { latestOnChange(!checked) },
                )
            },
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + knobOffset, y = 3.dp)
                .size(width = knobWidth, height = 18.dp)
                .shadow(elevation = 1.dp, shape = CircleShape)
                .background(if (checked) Gyp.onAccent else Gyp.knob, CircleShape),
        )
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
    /** V1b-4：值域与步长（Folia `SliderRow` 的 min / max / step），默认是流光的 `zeroToTwo`。 */
    min: Float = 0f,
    max: Float = 2f,
    step: Float = 0.1f,
    /** Folia `panelRows.tsx` 的 `times`（`1.00x`）/ `percent`（`50%`）。 */
    format: (Float) -> String = ::formatTimes,
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
                text = format(value),
                fontSize = 13.sp,
                color = Gyp.t2,
                style = androidx.compose.ui.text.TextStyle(
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        FoliaSlider(
            value = value,
            accent = accent,
            onChange = onChange,
            contentDescription = label,
            min = min,
            max = max,
            step = step,
        )
    }
}

/**
 * V1b-2 滑杆自检用的语义/标签常量：截图测试按 [testTag] 抓节点，按 [desc] 找控件。
 */
internal const val FoliaSliderTestTagPrefix = "folia-slider-"
internal const val FoliaThumbTestTagPrefix = "folia-thumb-"

/**
 * Folia `kit/Slider.tsx` 的视觉与手感，用 Compose 基础能力重画（不用 Material Slider ——
 * 它的点状刻度轨道 + 大号拇指与 Folia tuning 面板完全不同）。
 *
 * - 轨道：高 1dp（`h-1`）全圆角，未填充段 `Gyp.track`，填充段 accent；
 * - 滑钮：16dp 纯白圆点（`h-4 w-4 bg-white`），外加 1px 深色描边 + 下偏移阴影；
 * - 拖动：整个 44dp 高的行都吃手势（pointerInput），换算成 0..2 的值后按 0.1 取整。
 *
 * ## V1b-2 对齐口径（根因见 [FoliaSliderGeometry]）
 * 轨道可视段就是**滑钮中心的行程** `[inset, width - inset]`（Folia `THUMB_INSET = 8`），
 * 填充末端与滑钮中心是同一个表达式 `fillEndPx`，滑钮左缘 = 中心 − 半径。
 * 因此 0% / 50% / 100% 三处的中心都恰好压在填充末端上。
 */
@Composable
private fun FoliaSlider(
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    contentDescription: String,
    min: Float = 0f,
    max: Float = 2f,
    step: Float = 0.1f,
) {
    // V1b-4 修：pointerInput 只按宽度重启，协程里会一直拿着第一次组合的 onChange ——
    // 它闭包里的 tuning 是旧的，拖完 A 再拖 B，A 会被旧值写回。改为始终读最新的回调。
    val latestOnChange by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val thumbDp = FoliaSliderThumbDp
    val thumbInsetPx = with(density) { FoliaSliderThumbInsetDp.dp.toPx() }
    val trackColor = Gyp.track
    var trackWidthPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // 按下时滑钮放大到 18dp（Folia `st-slider:active`），但**中心不动** —— 定位与半径分开算。
    val thumbSizeNow = if (dragging) FoliaSliderThumbPressedDp else thumbDp
    val thumbSizePx = with(density) { thumbSizeNow.toPx() }
    // 拖动中或点按落点都立即收值：手势里先给值、再把 dragging 打开，
    // 保证滑钮的「按下放大」只影响半径、不影响中心。
    val fraction = FoliaSliderGeometry.fractionOf(value, min, max)
    // 填充末端与滑钮中心同源：两者都等于 [FoliaSliderGeometry.fillEndPx]。
    val fillEndPx = FoliaSliderGeometry.fillEndPx(trackWidthPx, thumbInsetPx, fraction)
    val thumbLeftPx = FoliaSliderGeometry.thumbLeftPx(trackWidthPx, thumbInsetPx, fraction, thumbSizePx)
    val dragModifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        // 热区本身带 testTag：UI 测试用它读滑杆的实际宽度与左缘，再和滑钮矩形一起断言对齐。
        .testTag(FoliaSliderTestTagPrefix + contentDescription)
        .onSizeChanged { trackWidthPx = it.width.toFloat() }
        // 只留一个 pointerInput 块：detectTapGestures 与 detectHorizontalDragGestures 写在两个
        // pointerInput 里会争抢同一次 down 事件，拖动前几帧不收值（滑钮跟不上手指）。
        .pointerInput(trackWidthPx, min, max, step) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragging = true
                    latestOnChange(FoliaSliderGeometry.valueForPositionPx(offset.x, trackWidthPx, thumbInsetPx, min, max, step))
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onHorizontalDrag = { change, _ ->
                    latestOnChange(FoliaSliderGeometry.valueForPositionPx(change.position.x, trackWidthPx, thumbInsetPx, min, max, step))
                },
            )
        }
    Box(modifier = dragModifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxWidth().height(FoliaSliderTrackDp)) {
            val trackHeight = size.height
            val radius = CornerRadius(trackHeight / 2f)
            // 未填充段：与滑钮行程同宽（两端各让出 inset），不是整行宽 ——
            // 否则 0% 时轨道还比填充多出一截 8dp 的「前导」，看上去就是错位。
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(thumbInsetPx, 0f),
                size = Size((size.width - thumbInsetPx * 2f).coerceAtLeast(1f), trackHeight),
                cornerRadius = radius,
            )
            // 填充段：0 → 滑钮中心，同样的 inset 起点。画到 fillEndPx + 半轨道高，
            // 让圆角端头在 thumb 遮住之前仍是完整圆头，不漏「半个圆」的缺口。
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.92f), accent),
                    startX = thumbInsetPx,
                    endX = thumbInsetPx + (size.width - thumbInsetPx * 2f).coerceAtLeast(1f),
                ),
                topLeft = Offset(thumbInsetPx, 0f),
                size = Size(
                    (fillEndPx - thumbInsetPx + trackHeight / 2f).coerceAtLeast(trackHeight),
                    trackHeight,
                ),
                cornerRadius = radius,
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbLeftPx.roundToInt(), 0) }
                .size(thumbSizeNow)
                // Folia `shadow-[0_0_0_1px_rgba(0,0,0,0.14),0_1px_3px_rgba(0,0,0,0.35)]`
                .shadow(elevation = 3.dp, shape = CircleShape)
                .background(FoliaSliderThumb, CircleShape)
                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.14f), shape = CircleShape)
                // V1b-2：给滑钮一个 testTag + 描述，截图测试据此读它的实际绘制中心（px）。
                .testTag(FoliaThumbTestTagPrefix + contentDescription)
                .semantics { this.contentDescription = contentDescription },
        )
    }
}

/**
 * 滑杆几何（V1b-2 抽出成纯函数，供 JVM 单测与 Compose 截图测试锁住「滑块中心 = 填充末端」）。
 *
 * 坐标都相对 44dp 拖动热区的左边缘，单位 px：
 * - 轨道可视区间是 `[inset, width - inset]`，滑钮在两端时贴边、中心正好落在 inset / width − inset；
 * - 填充末端 `fillEndPx` 与滑钮中心是同一个值；
 * - 滑钮左缘 = 中心 − 半径（按下放大到 18dp 时只是半径变大，中心不动）。
 *
 * ## 根因（V1a-fix3 的定位）
 * 修复前：填充 = `(宽度 − 2×8dp) × fraction + 8dp`，而滑钮位置 = `padding = 8dp × fraction × 2`
 * （= 8dp × value）。两者公式不同源、后者**不减半径**，于是 1.00x 时填充到正中间、滑钮才挪 8dp，
 * 肉眼看就是「填充到一半、滑块还贴在左边」——这就是实拍 `v1a-style-sheet.png` 里的错位。
 */
internal object FoliaSliderGeometry {

    /** 可拖动宽度（扣掉两端 inset），至少 1px 防止除零。 */
    fun usableWidthPx(trackWidthPx: Float, insetPx: Float): Float =
        (trackWidthPx - insetPx * 2f).coerceAtLeast(1f)

    /** 填充末端 = 滑钮中心（fraction 取 0..1，值域 0..2 已在外层归一）。 */
    fun fillEndPx(trackWidthPx: Float, insetPx: Float, fraction: Float): Float =
        usableWidthPx(trackWidthPx, insetPx) * fraction.coerceIn(0f, 1f) + insetPx

    /** 滑钮左缘：中心 − 半径，使滑块中心恒等于填充末端。 */
    fun thumbLeftPx(trackWidthPx: Float, insetPx: Float, fraction: Float, thumbSizePx: Float): Float =
        fillEndPx(trackWidthPx, insetPx, fraction) - thumbSizePx * 0.5f

    /** 手势落点 → 0..2 的值（按 0.1 取整，与 Folia `zeroToTwo` 的 19 个中间值一致）。 */
    fun valueForPositionPx(xPx: Float, trackWidthPx: Float, insetPx: Float): Float =
        valueForPositionPx(xPx, trackWidthPx, insetPx, 0f, 2f, 0.1f)

    /**
     * V1b-4：通用值域。落点 → [min, max]，按 [step] 从 min 起取整（Folia `<input type=range step>`），
     * 再按 0.0001 收口，去掉 0.55 + 7×0.05 这类浮点尾巴。
     */
    fun valueForPositionPx(xPx: Float, trackWidthPx: Float, insetPx: Float, min: Float, max: Float, step: Float): Float {
        val usable = usableWidthPx(trackWidthPx, insetPx)
        val f = ((xPx - insetPx) / usable).coerceIn(0f, 1f)
        val raw = min + (max - min) * f
        val snapped = if (step > 0f) min + ((raw - min) / step).roundToInt() * step else raw
        return ((snapped.coerceIn(min, max)) * 10000f).roundToInt() / 10000f
    }

    /** 值 → 0..1 的位置比例（值域退化时按 0）。 */
    fun fractionOf(value: Float, min: Float, max: Float): Float =
        if (max <= min) 0f else ((value.coerceIn(min, max) - min) / (max - min))
}

/** 弹层 / 面板圆角：14dp，与 V0 的 `QualityPickerSheet` 同一档。 */
private val LyricSheetShape = RoundedCornerShape(14.dp)

/** 轨道可视高度 2dp（Folia `h-1` = 4px @2x，这里取 2dp 的细线）。 */
private val FoliaSliderTrackDp = 2.dp

/** Folia kit 滑杆：滑钮 16dp（`h-4 w-4`）。 */
private val FoliaSliderThumbDp = 16.dp

/** 滑钮底色：纯白（Folia `bg-white`），在深色轨道上也读得出「实心圆点」。 */
private val FoliaSliderThumb = Color.White

/** 按下时 `st-slider:active` 的 1.15 倍（16 × 1.15 ≈ 18.4 → 取 18dp）。 */
private val FoliaSliderThumbPressedDp = 18.dp

/** 滑钮中心左右各让出的距离（对应 Folia `Slider.tsx:23` 的 `THUMB_INSET = 8`）。 */
internal const val FoliaSliderThumbInsetDp = 8f

/** 与 U5 同款：弹层里也走 `LyricStringHost`，保证文案与外层同一份资源。 */
@Composable
private fun str(id: Int): String = LyricStringHost.current(id)

/** 取整辅助（滑杆 steps 的落地值，报告里引用 zeroToTwo 的两位小数形态）。 */
internal fun ClassicTuning.percents(): Int = (breathingFloatMultiplier * 100).roundToInt()

/**
 * V1b-2 取证 / 截图测试用：不经过 `ModalBottomSheet`（它有自己的入场动画与遮罩），
 * 直接把面板本体画在一块给定的底上，用于 Robolectric 确定性截图。
 *
 * 只给测试与截图 Harness 用，生产入口仍走 [LyricStyleSheet]。
 */
@Composable
fun LyricStyleSheetPreview(
    tuning: ClassicTuning,
    strings: LyricStyleStrings,
    styleName: String,
    onTuning: (ClassicTuning) -> Unit,
    accent: Color = Gyp.accent,
    style: LyricStyle = LyricStyle.CLASSIC,
    fumeName: String = "",
    onStyle: (LyricStyle) -> Unit = {},
    fumeTuning: FumeTuning = FumeTuning.DEFAULT,
    onFumeTuning: (FumeTuning) -> Unit = {},
    partitaName: String = "",
    partitaTuning: PartitaTuning = PartitaTuning.DEFAULT,
    onPartitaTuning: (PartitaTuning) -> Unit = {},
) {
    LyricStylePanelContent(
        tuning = tuning,
        strings = strings,
        styleName = styleName,
        onTuning = onTuning,
        accent = accent,
        style = style,
        fumeName = fumeName,
        onStyle = onStyle,
        fumeTuning = fumeTuning,
        onFumeTuning = onFumeTuning,
        partitaName = partitaName,
        partitaTuning = partitaTuning,
        onPartitaTuning = onPartitaTuning,
    )
}


/** Folia `panelRows.tsx`：`times = value.toFixed(2) + "x"`。 */
internal fun formatTimes(value: Float): String = "%.2fx".format(value)

/** Folia `panelRows.tsx`：`percent = Math.round(value * 100) + "%"`。 */
internal fun formatPercent(value: Float): String = "${(value * 100f).roundToInt()}%"

/**
 * V1b-4「浮名参数」一组，逐行照 Folia `visualizer/settingsPanels.tsx` 的 `FumeSettingsPanel`：
 * 隐藏打印方块（开关）→ 通用几何图形（开关，显示的是「开」，存的是 disable）→ 世界背景物体透明度（0..1 / 0.05 / %）
 * → 文字停留比例（0..1 / 0.05 / %）→ 摄影机移动速度（0.55..1.85 / 0.05 / x）→ 摄影机追焦方式（分段：定格 / 平滑）
 * → 当前句辉光强度（0..1.8 / 0.05 / x）→ 大标题比例（0.82..1.32 / 0.02 / x）。
 * 写回一律过 [FumeTuning.sanitize]（= Folia `tuning.ts` 的 merge 钳位）。
 */
@Composable
private fun FumeTuningRows(
    tuning: FumeTuning,
    strings: LyricStyleStrings,
    accent: Color,
    onTuning: (FumeTuning) -> Unit,
) {
    fun emit(t: FumeTuning) = onTuning(
        FumeTuning.sanitize(
            hidePrintSymbols = t.hidePrintSymbols,
            disableGeometricBackground = t.disableGeometricBackground,
            backgroundObjectOpacity = t.backgroundObjectOpacity,
            textHoldRatio = t.textHoldRatio,
            cameraTrackingMode = t.cameraTrackingMode,
            cameraSpeed = t.cameraSpeed,
            glowIntensity = t.glowIntensity,
            heroScale = t.heroScale,
        ),
    )
    ToggleRow(
        label = str(strings.fumeHidePrintSymbols),
        checked = tuning.hidePrintSymbols,
        accent = accent,
        onChange = { emit(tuning.copy(hidePrintSymbols = it)) },
    )
    ToggleRow(
        label = str(strings.fumeGeometricBackground),
        checked = !tuning.disableGeometricBackground,
        accent = accent,
        onChange = { shown -> emit(tuning.copy(disableGeometricBackground = !shown)) },
    )
    TuningSliderRow(
        label = str(strings.fumeBackgroundObjectOpacity),
        value = tuning.backgroundObjectOpacity.toFloat(),
        accent = accent,
        min = 0f, max = 1f, step = 0.05f, format = ::formatPercent,
        onChange = { emit(tuning.copy(backgroundObjectOpacity = it.toDouble())) },
    )
    TuningSliderRow(
        label = str(strings.fumeTextHoldRatio),
        value = tuning.textHoldRatio.toFloat(),
        accent = accent,
        min = 0f, max = 1f, step = 0.05f, format = ::formatPercent,
        onChange = { emit(tuning.copy(textHoldRatio = it.toDouble())) },
    )
    TuningSliderRow(
        label = str(strings.fumeCameraSpeed),
        value = tuning.cameraSpeed.toFloat(),
        accent = accent,
        min = 0.55f, max = 1.85f, step = 0.05f,
        onChange = { emit(tuning.copy(cameraSpeed = it.toDouble())) },
    )
    SegmentedRow(
        label = str(strings.fumeCameraTrackingMode),
        options = listOf(
            FumeCameraTrackingMode.STEPPED to str(strings.fumeCameraTrackingStepped),
            FumeCameraTrackingMode.SMOOTH to str(strings.fumeCameraTrackingSmooth),
        ),
        selected = tuning.cameraTrackingMode,
        onChange = { emit(tuning.copy(cameraTrackingMode = it)) },
    )
    TuningSliderRow(
        label = str(strings.fumeGlowIntensity),
        value = tuning.glowIntensity.toFloat(),
        accent = accent,
        min = 0f, max = 1.8f, step = 0.05f,
        onChange = { emit(tuning.copy(glowIntensity = it.toDouble())) },
    )
    TuningSliderRow(
        label = str(strings.fumeHeroScale),
        value = tuning.heroScale.toFloat(),
        accent = accent,
        min = 0.82f, max = 1.32f, step = 0.02f,
        onChange = { emit(tuning.copy(heroScale = it.toDouble())) },
    )
}

/**
 * Folia `kit/Row.tsx` + `kit/Segmented.tsx`：左标签、右分段。
 * 分段容器 `--st-fill` 底、8dp 圆角、2dp 内边距；每段高 28dp、13sp；
 * 选中段垫 `--st-raise` 6dp 圆角 + 1dp 投影，文字 `--st-t1`，未选 `--st-t2`。
 * 手机上整段热区垫到 48dp 高（触控），视觉仍是 32dp 的胶囊。
 */
@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Gyp.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Gyp.tint)
                .padding(2.dp),
        ) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .then(
                            if (isSel) {
                                Modifier
                                    .shadow(1.dp, RoundedCornerShape(6.dp))
                                    .background(Gyp.raise, RoundedCornerShape(6.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickableNoRipple { if (!isSel) onChange(value) }
                        .semantics { contentDescription = text }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        color = if (isSel) Gyp.ink else Gyp.t2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


/** Folia `settingsPanels.tsx`：`pixels = value + "px"`。 */
internal fun formatPixels(value: Float): String = "${value.roundToInt()}px"

/**
 * V2a「云阶参数」，逐行照 Folia `PartitaSettingsPanel`：引导线（开关）→ 语义排列（开关）
 * → 错位最小值 / 最大值（0..180，步长 5，`px`）。写回过 [PartitaTuning.sanitize]（小的在前）。
 */
@Composable
private fun PartitaTuningRows(
    tuning: PartitaTuning,
    strings: LyricStyleStrings,
    accent: Color,
    onTuning: (PartitaTuning) -> Unit,
) {
    fun emit(t: PartitaTuning) = onTuning(
        PartitaTuning.sanitize(t.showGuideLines, t.useSemanticLayout, t.staggerMin, t.staggerMax),
    )
    ToggleRow(
        label = str(strings.partitaGuideLines),
        checked = tuning.showGuideLines,
        accent = accent,
        onChange = { emit(tuning.copy(showGuideLines = it)) },
    )
    ToggleRow(
        label = str(strings.partitaSemanticLayout),
        checked = tuning.useSemanticLayout,
        accent = accent,
        onChange = { emit(tuning.copy(useSemanticLayout = it)) },
    )
    TuningSliderRow(
        label = str(strings.partitaStaggerMin),
        value = tuning.staggerMin.toFloat(),
        accent = accent,
        min = 0f, max = 180f, step = 5f, format = ::formatPixels,
        onChange = { emit(tuning.copy(staggerMin = it.toDouble())) },
    )
    TuningSliderRow(
        label = str(strings.partitaStaggerMax),
        value = tuning.staggerMax.toFloat(),
        accent = accent,
        min = 0f, max = 180f, step = 5f, format = ::formatPixels,
        onChange = { emit(tuning.copy(staggerMax = it.toDouble())) },
    )
}
