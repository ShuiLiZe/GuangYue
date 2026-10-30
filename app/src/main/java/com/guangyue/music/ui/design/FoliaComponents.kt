package com.guangyue.music.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Folia 基础组件（手机化版）。
 *
 * 每个组件上方注释注明对应的 Folia 源文件。统一的手机化改动：
 *  - **hover → 按压**：Folia 的 `hover:bg-…` / 鼠标缩放改为按压态（`interactionSource.pressed`），
 *    叠加 `scale(0.97)`（Button.tsx:11 `enabled:active:scale-[0.97]`）+ Compose ripple。
 *  - **触控高度 ≥ 48dp**：Folia 是桌面 32px 控件（`h-8`），手机上所有可点区域 `defaultMinSize(minHeight = 48.dp)`。
 *  - **长按代替右键**（inventory.md 第 0 节红线）：组件自己不弹菜单，由页面决定长按行为。
 *
 * 只依赖 Compose Foundation 与本包 token，不依赖任何 feature 模块。
 */

// ---------------------------------------------------------------------------------------------
// 公共：按压缩放
// ---------------------------------------------------------------------------------------------

/**
 * 按压缩放：按下时缩到 [FoliaMotion.PRESS_SCALE]（0.97），弹簧取 Carousel3D.tsx:42 的 300/30。
 * 等价于 Folia 的 `active:scale-[0.97]`（Button.tsx:11、Chips.tsx:33）。
 */
@Composable
fun Modifier.foliaPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) FoliaMotion.PRESS_SCALE else 1f,
        animationSpec = FoliaMotion.press(),
        label = "folia-press-scale",
    )
    return this.scale(scale)
}

private val ripple24 = androidx.compose.material3.ripple(radius = 24.dp)

// ---------------------------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------------------------

/** Folia 按钮种类。 */
enum class FoliaButtonKind {
    /** `primary`：`bg accent text on-accent`（Button.tsx:16）。 */
    Primary,

    /** `fill`：静默填充 `bg --st-fill`（Button.tsx:15）。 */
    Secondary,

    /** `quiet`：无底色纯文字（Button.tsx:17）。 */
    Quiet,
}

/**
 * Folia 文字按钮。
 *
 * 源：`src/components/modal/settings/kit/Button.tsx:11-18`
 *  - 基类 `transition-[background-color,color,opacity,scale] enabled:active:scale-[0.97] disabled:opacity-40`；
 *  - fill：`h-8 rounded-lg bg fill px-3 text-13px`；
 *  - primary：`bg accent text on-accent`；quiet：`h-6 rounded-md px-1.5 text-xs`。
 *
 * 手机化：高度 32dp → 48dp（触控下限），圆角仍 8dp（`rounded-lg`），字号 13sp，禁用 40% 透明。
 */
@Composable
fun FoliaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: FoliaButtonKind = FoliaButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Folia.colors
    val t = Folia.type
    val s = Folia.spacing
    val interaction = remember { MutableInteractionSource() }

    val bg = when (kind) {
        FoliaButtonKind.Primary -> c.accent
        FoliaButtonKind.Secondary -> c.fill
        FoliaButtonKind.Quiet -> Color.Transparent
    }
    val fg = when (kind) {
        FoliaButtonKind.Primary -> c.onAccent
        FoliaButtonKind.Secondary, FoliaButtonKind.Quiet -> c.ink
    }
    val shape = if (kind == FoliaButtonKind.Quiet) Folia.shapes.sm else Folia.shapes.md

    Box(
        modifier = modifier
            .foliaPressScale(interaction)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = ripple24,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = s.x4Dp, vertical = s.x2_5Dp)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.x2Dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = t.control, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Folia 图标按钮。
 *
 * 源：`src/components/modal/settings/kit/Button.tsx:21-25` —— `ICON_CLASS = h-8 w-8`（32×32），图标 14px。
 * 手机化：32dp → 48dp 触控方框（图标 18dp，保留 Folia 小图标的观感），圆角 `rounded-md` 6dp。
 */
@Composable
fun FoliaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    iconSize: Dp = 18.dp,
) {
    val c = Folia.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .foliaPressScale(interaction)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple24,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: c.inkSoft,
            modifier = Modifier.size(iconSize).alpha(if (enabled) 1f else 0.4f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 封面卡
// ---------------------------------------------------------------------------------------------

/**
 * Folia 封面卡（首页墙面卡片）。
 *
 * 源：基准截图 `m412-dark-home-playlists.png` / `m412-light-search-typing.png` 的 Daily Mix 卡片 +
 * `src/index.css:152-161` `.theme-polaroid-card`（底 `--bg-color` 95%、描边 `--text-primary` 5%、
 * hover 描边 32%）+ `src/library/suites/bravais/bravaisHome.css:17-29`（外框内边距 10px、间隙 10px）。
 *
 * 结构（照截图）：外框圆角 16dp（inventory.md §1「卡片圆角统一 CornerSize(16.dp)」）→ 内嵌封面
 * 圆角 12dp → 左对齐标题 14sp/600 + 副标题 12sp（`--text-secondary`）。
 * 手机化：hover 改按压（底从 95% 提到 [FoliaColorTokens.fillStrong] 一档 + scale 0.97）。
 */
@Composable
fun FoliaCoverCard(
    title: String,
    subtitle: String,
    cover: (@Composable () -> Unit)? = null,
    coverColor: Color? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    aspect: Float = 1f,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Folia.colors
    val t = Folia.type
    val s = Folia.spacing
    val cardShape = Folia.shapes.xl
    val coverShape = Folia.shapes.lg
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            .foliaPressScale(interaction)
            .clip(cardShape)
            .background(if (pressed) c.fillStrong else c.cardFill)
            .border(1.dp, c.cardBorder, cardShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
            )
            .padding(s.x2_5Dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(coverShape)
                .background(coverColor ?: c.preview),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) cover() else Text(title.take(1), style = t.headline, color = Color.White.copy(alpha = 0.9f))
        }
        Row(
            modifier = Modifier.padding(top = s.x2_5Dp, start = s.x0_5Dp, end = s.x0_5Dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = t.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = t.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 歌曲行
// ---------------------------------------------------------------------------------------------

/**
 * Folia 歌曲 / 列表行。
 *
 * 源：`src/components/modal/settings/kit/Row.tsx:88-134`（`min-h-14 py-2.5 pl-4 pr-4`，hover `bg --st-fill`；
 * 标签 `text-sm leading-5`、hint `text-xs leading-[18px] --st-t2`；行间 1px 发丝线左缩进 16px 见
 * `kit.css:62-70`）与 `src/components/app/search/SearchResultRow.tsx:54-151`（封面 48×48 `rounded-xl`
 * `bg-zinc-500/15`、`gap-3`、右侧操作 `rounded-full p-2`、时长 `font-mono text-xs opacity-35`）。
 *
 * 手机化：
 *  - 行高 56px → 56dp 不变（本来就是 ≥48dp），只去掉 hover，按压改 `--st-fill` 底 + ripple；
 *  - 右侧操作按钮从「hover 才出现」改为常驻（触屏没有 hover，否则入口会丢）；
 *  - 副标题压成一行（艺人 • 专辑，inventory.md §3），超长省略。
 */
@Composable
fun FoliaSongRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    selected: Boolean = false,
) {
    val c = Folia.colors
    val t = Folia.type
    val s = Folia.spacing
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (pressed || selected) c.fill else Color.Transparent)
            .drawBehind {
                if (showDivider) {
                    // kit.css:62-70 的发丝线：1px，左缩进 16px。
                    val y = size.height - 0.5f
                    drawLine(
                        color = c.line,
                        start = Offset(16.dp.toPx(), y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = s.x4Dp, vertical = s.x2_5Dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.x3Dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(text = title, style = t.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = t.caption,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (trailingText != null) {
            Text(text = trailingText, style = t.numeric, color = c.text3)
        }
        if (trailingIcon != null && onTrailingClick != null) {
            Box(Modifier.size(40.dp)) {
                FoliaIconButton(
                    icon = trailingIcon,
                    contentDescription = null,
                    onClick = onTrailingClick,
                    tint = c.inkSoft,
                    modifier = Modifier.size(40.dp),
                    iconSize = 16.dp,
                )
            }
        }
    }
}

/** 行首封面缩略图：`h-12 w-12 rounded-xl bg-zinc-500/15`（SearchResultRow.tsx:91）。 */
@Composable
fun FoliaRowCover(
    color: Color? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val c = Folia.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(Folia.shapes.lg)
            .background(color ?: c.coverPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// 滑条
// ---------------------------------------------------------------------------------------------

/** 默认读数格式：百分比（Slider.tsx:58 的 `format` 示例就是 `v => \`${v}%\``）。 */
fun formatPercent(value: Float, range: ClosedFloatingPointRange<Float>): String {
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    return "${(((value - range.start) / span) * 100).toInt()}%"
}

/**
 * Folia 滑条（进度 / 音量通用）。
 *
 * 源：`src/components/modal/settings/kit/Slider.tsx:19-30,80-172`
 *  - 轨道 `h-1 rounded-full`（4dp），已填充 accent、其余 `--st-track`（17% 前景色，kitTheme.ts:45）；
 *  - 拇指 `h-4 w-4 rounded-full bg white`（16dp），阴影 `0 0 0 1px rgba(0,0,0,.14), 0 1px 3px rgba(0,0,0,.35)`；
 *  - 拇指按下放大 1.15（`kit.css:26-32`）；读数 `text-[13px] tabular-nums`、宽 52px（Slider.tsx:168）。
 *
 * 手机化：
 *  - 输入区 20px → 48dp 触控带（视觉轨道仍 4dp 垂直居中）；
 *  - 拖动不吸附刻度（Folia 只在指针拖动时吸附，手机连续取值更顺手）；
 *  - 读数宽度 52dp 与源码 52px 一致。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FoliaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    showValue: Boolean = true,
    displayValue: String = formatPercent(value, valueRange),
    enabled: Boolean = true,
) {
    val c = Folia.colors
    val t = Folia.type
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) FoliaMotion.THUMB_ACTIVE_SCALE else 1f,
        animationSpec = FoliaMotion.fast(),
        label = "thumb-scale",
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Folia.spacing.x3Dp),
    ) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = interaction,
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .scale(thumbScale)
                        .clip(Folia.shapes.pill)
                        .background(Color.White),
                )
            },
            track = { sliderState ->
                val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
                val fraction = ((sliderState.value - valueRange.start) / span).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(Folia.shapes.pill)
                        .background(c.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(Folia.shapes.pill)
                            .background(c.accent),
                    )
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
        )
        if (showValue) {
            Text(text = displayValue, style = t.numeric, color = c.ink, textAlign = TextAlign.End)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 开关
// ---------------------------------------------------------------------------------------------

/**
 * Folia 开关。
 *
 * 源：`src/components/modal/settings/kit/Toggle.tsx:25-28`
 *  - 轨道 `h-6 w-10 rounded-full`（24×40dp），选中 `bg accent`，未选 `--st-track`（17%）；
 *  - 旋钮 18×18、边距 3px，选中用 `on-accent`、未选用 `--st-knob`，阴影 `0 1px 2px rgba(0,0,0,.3)`；
 *  - 按下时旋钮宽度 22px（`active:w-[22px]`，位移缩到 x-3）。
 *
 * 手机化：24dp 轨道包进 48dp 触控区；位移动效改用 spring(300, ζ=0.866)（Folia 主体弹簧，
 * Carousel3D.tsx:42），观感与 CSS transition 一致。
 */
@Composable
fun FoliaToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Folia.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spring = androidx.compose.animation.core.spring<Float>(
        dampingRatio = FoliaMotion.dampingRatio(damping = 30f, stiffness = 300f),
        stiffness = 300f,
    )
    val offset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 16f else 0f,
        animationSpec = spring,
        label = "toggle-offset",
    )
    val thumbW by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 22f else 18f,
        animationSpec = FoliaMotion.fast(),
        label = "toggle-thumb-w",
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp),
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 24.dp)
                .clip(Folia.shapes.pill)
                .background(if (checked) c.accent else c.track),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (3f + offset).dp, y = 3.dp)
                    .size(width = thumbW.dp, height = 18.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.30f),
                            radius = size.height / 2,
                            center = center + Offset(0f, 1.dp.toPx()),
                        )
                    }
                    .background(if (checked) c.onAccent else c.knob, Folia.shapes.pill),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 分段控件
// ---------------------------------------------------------------------------------------------

/**
 * Folia 分段（Segmented）。
 *
 * 源：`src/components/modal/settings/kit/Segmented.tsx:33-59`
 *  - 容器 `min-h-8 rounded-lg bg --st-fill p-0.5`（8dp 圆角、内边距 2px）；
 *  - 项 `h-7 rounded-md text-13px px-3`；
 *  - 选中标记 `layoutId` 滑动、`bg --st-raise`、radius 6、阴影 `0 1px 2px rgba(0,0,0,.2)`。
 *
 * 手机化：容器 32px → 48dp、项 40dp（触控下限），内边距 4dp；`layoutId` 的滑动标记用
 * 动画底色近似（Compose 无 layoutId 等价物，选中项底色 120ms 淡入）。
 */
@Composable
fun <T> FoliaSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Folia.colors
    val t = Folia.type
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(Folia.shapes.md)
            .background(c.fill)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(
                targetValue = if (isSel) c.raise else Color.Transparent,
                animationSpec = FoliaMotion.fast(),
                label = "seg-bg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Folia.shapes.sm)
                    .background(bg)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = Folia.spacing.x3Dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = t.control,
                    color = if (isSel) c.ink else c.inkSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab（下划线式）
// ---------------------------------------------------------------------------------------------

/**
 * Folia 下划线 Tab 条。
 *
 * 源：`src/components/modal/settings/kit/Tabs.tsx:73-107`
 *  - 底部一条 `--st-line`（8%）发丝线；
 *  - 条间距 `gap 18px`、tab `h-9 px-1 text-13px`；选中下划线 `inset-x-1 bottom-0 h-0.5 bg accent radius 2`。
 *
 * 手机化：条高 36px → 48dp 触控；下划线仍 2dp、左右各缩 4dp。
 */
@Composable
fun <T> FoliaTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Folia.colors
    val t = Folia.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = c.line,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(Folia.spacing.sectionGapDp),
    ) {
        tabs.forEach { tab ->
            val isSel = tab == selected
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                    Text(text = label(tab), style = t.control, color = if (isSel) c.ink else c.inkSoft, maxLines = 1)
                }
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSel) c.accent else Color.Transparent),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 搜索框
// ---------------------------------------------------------------------------------------------

/** 搜索框外形：[Pill] 顶栏胶囊（基准截图右上角），[Rounded] 设置面板 `rounded-lg`。 */
enum class FoliaSearchBarShape { Pill, Rounded }

/**
 * Folia 搜索框。
 *
 * 源：`src/components/modal/settings/kit/TextField.tsx:9-12,72` —— `rounded-lg border border-transparent
 * bg --st-fill`，聚焦 `focus-within:border accent`，placeholder `--st-t3`，`h-8 text-13px`；
 * 顶栏那一版是胶囊形（`m412-dark-home-playlists.png`），占位符 `SEARCH DATABASE...`
 * （`src/i18n/locales/en.ts:1579`）。
 *
 * 手机化：32dp → 48dp 高；聚焦描边用 120ms 颜色过渡（motion.ts:15 `fast`）。
 */
@Composable
fun FoliaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "SEARCH DATABASE...",
    shape: FoliaSearchBarShape = FoliaSearchBarShape.Pill,
    leadingIcon: ImageVector? = null,
    onSearch: () -> Unit = {},
) {
    val c = Folia.colors
    val t = Folia.type
    val s = Folia.spacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val clipShape: Shape = if (shape == FoliaSearchBarShape.Pill) Folia.shapes.pill else Folia.shapes.md
    val border by animateColorAsState(
        targetValue = if (focused) c.accent else Color.Transparent,
        animationSpec = FoliaMotion.fast(),
        label = "search-border",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(clipShape)
            .background(c.fill)
            .border(1.dp, border, clipShape)
            .padding(horizontal = s.x4Dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.x2Dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = c.text3, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = t.control, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = t.control.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 毛玻璃面板
// ---------------------------------------------------------------------------------------------

/**
 * Folia 毛玻璃面板。
 *
 * 源：`src/index.css:163-168` `.theme-glass-panel`（底 `--bg-color` 85%、1px 描边 `--text-primary` 12%）+
 * `Carousel3D.tsx:405`（`backdrop-blur-md`）与 `FloatingPlayerControls.tsx:384`（`backdrop-blur-xl`）。
 * Android 12+ 走 `Modifier.blur` 真模糊，低版本退化为半透明底（详见 FoliaBlur.kt）。
 */
@Composable
fun FoliaGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    blurRadius: Dp? = null,
    content: @Composable () -> Unit,
) {
    val c = Folia.colors
    val panelShape = shape ?: Folia.shapes.xl
    val radius = blurRadius ?: Folia.blur.md
    Box(
        modifier = modifier
            .clip(panelShape)
            .background(c.glassFill)                       // index.css:163-168 的 85% 底色
            .androidBorder(1.dp, c.glassBorder, panelShape)  // 12% 描边（自绘，见 FoliaBlur.androidBorder）
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// 空态
// ---------------------------------------------------------------------------------------------

/**
 * Folia 空态。
 *
 * 源：`src/components/panelTab/shell/EmptyState.tsx:15-18` —— `min-h-[180px] grid place-items-center
 * gap-2.5 px-2 py-4`，图标 28px，文案 `max-w-[264px] text-13px leading-5 --st-t2`。
 * 手机化：264px 改成 `widthIn(max = 264.dp)`（窄屏不写死），其余照搬。
 */
@Composable
fun FoliaEmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tint: Color? = null,
) {
    val c = Folia.colors
    val t = Folia.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .padding(horizontal = Folia.spacing.x2Dp, vertical = Folia.spacing.x4Dp),
        verticalArrangement = Arrangement.spacedBy(Folia.spacing.x2_5Dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: c.text3, modifier = Modifier.size(28.dp))
        Text(
            text = text,
            style = t.body,
            color = c.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 264.dp),
        )
        if (actionLabel != null && onAction != null) {
            // 手机化：空态给一个可点的出口（桌面靠全局命令面板），按钮自带 48dp 触控。
            FoliaButton(actionLabel, onClick = onAction, kind = FoliaButtonKind.Secondary)
        }
    }
}


// ---------------------------------------------------------------------------------------------
// 弹层（桌面右键菜单 / 弹窗 / 右上角叉号 → 底部弹层）
// ---------------------------------------------------------------------------------------------

/**
 * 广乐底部弹层容器。
 *
 * 对应 Folia 的**同族浮层**，但换成手机形态：
 *  - `src/index.css:163-168` `.theme-glass-panel`（玻璃面：底色 85% + 12% 描边）——
 *    这里用 [FoliaGlassPanel] 做面板本身，是同一套材质；
 *  - `src/components/modal/settings/kit/kit.css:128-160` `pop-in/out`（translateY + scale .98）
 *    与 `st-page-in`（translateY 6px）—— 进/出场沿用「自下而上 + 轻微缩放」，
 *    手机改成整块从底部推入（[FoliaMotion.springCard]，即 Carousel3D.tsx:42 的 300/30 弹簧）；
 *  - 桌面原本的右键菜单 / 居中弹窗 / 右上角 ×（`Modal` 类组件）—— **一律不收 ×**，
 *    靠「下拉开头（drag handle）」+ 下滑 + 系统返回关闭，见 [FoliaSheetDragHandle]。
 *
 * 交互约定（m00170 红线）：
 *  - 无右上角叉号；
 *  - 点遮罩关闭 = 手机最常见的取消手势，保留；
 *  - 列表项触控高度 ≥56dp，主操作放最上（拇指先够到）。
 *
 * @param visible 是否展开；收起时一次性把进场动画走完，方便截图与复用。
 * @param onDismiss 下滑 / 点遮罩 / 系统返回触发。
 * @param title 可选标题（歌曲名等）。
 * @param subtitle 可选副标题。
 */
@Composable
fun FoliaBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Folia.colors
    val t = Folia.type
    val s = Folia.spacing
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = FoliaMotion.springCard,
        label = "sheet-progress",
    )
    // 系统返回 = 关闭（对应桌面的 Esc）。
    androidx.activity.compose.BackHandler(enabled = visible, onBack = onDismiss)

    if (progress <= 0f) return

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * progress))) {
        FoliaGlassPanel(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 0.dp)
                .graphicsLayer { translationY = (1f - progress) * 420f * density }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            blurRadius = Folia.blur.xl,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FoliaSheetDragHandle()
                if (title != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = s.x4Dp).padding(bottom = 6.dp),
                    ) {
                        Text(title, style = t.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = t.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                content()
            }
        }
    }
}

/**
 * 弹层顶部拖动把手 —— 替代桌面的右上角「×」。
 *
 * 桌面端关闭按钮在窗口右上角，拇指够不到；手机按平台惯例只留一条 36×4dp 把手
 * （Material 3 bottom sheet 的 drag handle 同尺寸），下滑或系统返回即关。
 */
@Composable
fun FoliaSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(Folia.shapes.pill)
            .background(Folia.colors.ink.copy(alpha = 0.28f)),
    )
}

/**
 * 弹层里的一行操作。
 *
 * 桌面右键菜单项高 32px（`DropdownMenu`），手机上抬到 56dp：拇指点击区
 * 与 [FoliaSongRow] 的行高一致，长按菜单出来后不用重新瞄准。
 *
 * @param danger 危险操作（删除 / 移出）用 [FoliaColorTokens.error]。
 */
@Composable
fun FoliaSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val c = Folia.colors
    val tint = if (danger) c.error else c.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = Folia.spacing.x4Dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Folia.spacing.x3Dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = Folia.type.body, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
