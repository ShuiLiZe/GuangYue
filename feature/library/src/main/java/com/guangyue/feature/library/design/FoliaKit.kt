package com.guangyue.feature.library.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.icons.GuangYueIcons

// feature/library/.../design/FoliaKit.kt
// Folia 设置工具箱的 Compose 版：Row / Group / PageHeader / Toggle / Segmented / Button / TextField /
// EmptyState。每个尺寸、颜色、动画都取自 Folia 的 kit 源码（见各项注释），不另写数字。

/**
 * Folia 的设置行：左边标签（可带一行 hint、一个「i」展开说明），右边控件；除卡片首行外，
 * 顶部有一条从 16dp（子行 40dp）开始的发丝线。
 *
 * 对应 `kit/Row.tsx`：`min-h-14 gap-x-6 py-2.5 pr-4 pl-4`（子行 pl-10）。
 */
@Composable
fun FoliaRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    note: String? = null,
    sub: Boolean = false,
    first: Boolean = false,
    disabled: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var noteOpen by remember { mutableStateOf(false) }
    val row = Modifier
        .fillMaxWidth()
        .heightIn(min = GySpace.rowMin)
        .foliaHairline(top = !first, inset = if (sub) GySpace.subIndent else GySpace.pageH)
        .then(if (onClick != null && !disabled) Modifier.pointerInput(Unit) { detectTapGestures { onClick() } } else Modifier)
        .padding(
            start = if (sub) GySpace.subIndent else GySpace.pageH,
            end = GySpace.pageH,
            top = GySpace.m,
            bottom = GySpace.m,
        )

    Column(modifier = row) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(GySpace.s))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        fontSize = GyText.row,
                        lineHeight = GyText.rowHeight,
                        color = Gyp.ink,
                        fontFamily = GuangYueDesign.Sans,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alpha(if (disabled) 0.4f else 1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note != null) {
                        Spacer(Modifier.width(GySpace.xs))
                        Icon(
                            imageVector = GuangYueIcons.Info,
                            contentDescription = "说明",
                            tint = if (noteOpen) Gyp.ink else Gyp.t3,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .pointerInput(Unit) { detectTapGestures { noteOpen = !noteOpen } },
                        )
                    }
                }
                if (hint != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hint,
                        fontSize = GyText.hint,
                        lineHeight = GyText.hintHeight,
                        color = Gyp.t2,
                        fontFamily = GuangYueDesign.Sans,
                        modifier = Modifier.alpha(if (disabled) 0.4f else 1f),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(GySpace.l))
                Box(modifier = Modifier.alpha(if (disabled) 0.4f else 1f)) { trailing() }
            }
        }
        if (note != null && noteOpen) {
            Spacer(Modifier.height(GySpace.xs))
            Text(
                text = note,
                fontSize = GyText.hint,
                lineHeight = 19.sp,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
            )
        }
    }
}

/**
 * 一组行：上面一个小标题，下面一张卡片（`kit/Group.tsx`：卡片 `rounded-xl bg-card
 * shadow-[0_0_0_1px_var(--st-line)]`，组标题 13px/20 semibold 色 --st-t2，标题下 8dp，组间 28dp）。
 */
@Composable
fun FoliaGroup(
    title: String?,
    modifier: Modifier = Modifier,
    foot: String? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp) /* Folia px-1 */
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = GyText.label,
                    lineHeight = GyText.labelHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Spacer(Modifier.height(GySpace.s))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .foliaCardSurface(),
        ) { content() }
        if (foot != null) {
            Spacer(Modifier.height(GySpace.s))
            Text(
                text = foot,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t3,
                modifier = Modifier.padding(horizontal = 4.dp), /* Folia mx-1 */
            )
        }
    }
}

/**
 * Folia 的开关（`kit/Toggle.tsx`）：40×24 的胶囊，滑钮 18dp，选中向 16dp 平移，
 * 按下时滑钮加宽到 22dp；颜色 fast（120ms standard）。
 */
@Composable
fun FoliaToggle(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Gyp.onAccent,
            checkedTrackColor = Gyp.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Gyp.knob,
            uncheckedTrackColor = Gyp.track,
            uncheckedBorderColor = Color.Transparent,
        ),
        modifier = Modifier.size(width = 40.dp, height = 24.dp) /* Folia w-10 h-6 */,
    )
}

/**
 * Folia 的分段控件（`panelTab/shell/Segments.tsx`，l 档）：容器高 36dp、圆角 10dp、
 * `--st-fill` 底、内边距 2dp；选项圆角 8dp、13px、px-2.5；选中标记 `--st-raise` 圆角 8dp
 * 加 `0 1px 2px rgba(0,0,0,0.2)`，滑块 move（220ms out）。
 */
@Composable
fun <T> FoliaSegmented(
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(GyRadius.row))
            .background(Gyp.tint, RoundedCornerShape(GyRadius.row))
            .padding(GySpace.hairlinePad),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { (option, label) ->
            val selected = option == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(GyRadius.field))
                    .background(if (selected) Gyp.raise else Color.Transparent, RoundedCornerShape(GyRadius.field))
                    .selectable(selected = selected, role = Role.Tab) { if (!selected) onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = GyText.label,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) Gyp.ink else Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = GySpace.m),
                )
            }
        }
    }
}

/**
 * Folia 的按钮（`kit/Button.tsx`）：文本档高 32dp、圆角 8dp、13px；primary 用强调色，
 * fill 用 `--st-fill`，quiet 高 24dp、圆角 6dp、12px。按下 scale 0.97，fast（120ms standard）。
 */
@Composable
fun FoliaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FoliaButtonVariant = FoliaButtonVariant.Fill,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val quiet = variant == FoliaButtonVariant.Quiet
    val bg = when (variant) {
        FoliaButtonVariant.Primary -> Gyp.accent
        FoliaButtonVariant.Fill -> Gyp.tint
        FoliaButtonVariant.Quiet -> Color.Transparent
    }
    val fg = when (variant) {
        FoliaButtonVariant.Primary -> Gyp.onAccent
        FoliaButtonVariant.Fill -> Gyp.ink
        FoliaButtonVariant.Quiet -> Gyp.t2
    }
    val shape = if (quiet) GyRadius.quietShape() else GyRadius.fieldShape()
    Row(
        modifier = modifier
            .height(if (quiet) 24.dp else 32.dp)
            .clip(shape)
            .background(bg, shape)
            .alpha(if (enabled) 1f else 0.4f)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = if (quiet) 6.dp else GySpace.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            fontSize = if (quiet) GyText.hint else GyText.label,
            color = fg,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
        )
    }
}

enum class FoliaButtonVariant { Fill, Primary, Quiet }

/** 状态语气，对应 Folia `Status.tsx` 的 tone：ok / warn / error / 未给 tone。 */
enum class FoliaTone { Neutral, Ok, Warn, Error }

/**
 * 状态行（`kit/Status.tsx`）：1.5dp 圆点 + 12/18 的 `--st-t2` 文字，圆点按 tone 换色。
 * Folia 源码是 `inline-flex items-start gap-1.5 text-xs leading-[18px]`，圆点 `h-1.5 w-1.5`。
 */
@Composable
fun FoliaStatus(
    text: String,
    modifier: Modifier = Modifier,
    tone: FoliaTone = FoliaTone.Neutral,
    maxLines: Int = 1,
) {
    val dot = when (tone) {
        FoliaTone.Ok -> Gyp.ok
        FoliaTone.Warn -> Gyp.warn
        FoliaTone.Error -> Gyp.danger
        FoliaTone.Neutral -> Gyp.t3
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(GySpace.xs),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp) // dot `mt-1.5`
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = text,
            fontSize = GyText.hint,
            lineHeight = 18.sp,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 弹出的菜单（`kit/Popover.tsx` + `kit/Menu.tsx`）：圆角 10dp、`--st-menu` 底、
 * `0 14px 36px rgba(0,0,0,.34), 0 0 0 1px var(--st-line2)`；条目高 32dp、最小宽 176dp、
 * 圆角 6dp、左右 10dp、13px。入场 move（220ms out）+ 位移 4dp + 缩放 .98。
 */
@Composable
fun FoliaMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<FoliaMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (!expanded) return
    androidx.compose.ui.window.Popup(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(
            modifier = modifier
                .widthIn(min = 176.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Gyp.menu)
                .border(1.dp, Gyp.line2, RoundedCornerShape(10.dp))
                .shadow(14.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .padding(vertical = GySpace.xxs),
        ) {
            items.forEach { item ->
                if (item.divider) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = GySpace.xxs)
                            .height(1.dp)
                            .background(Gyp.hairline),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(GyRadius.quiet))
                        .then(
                            if (item.enabled) {
                                Modifier.pointerInput(item.label) {
                                    detectTapGestures {
                                        onDismiss()
                                        item.onClick()
                                    }
                                }
                            } else Modifier
                        )
                        .padding(horizontal = 10.dp)
                        .alpha(if (item.enabled) 1f else 0.4f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GySpace.s),
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, null, tint = if (item.destructive) Gyp.danger else Gyp.t2, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        text = item.label,
                        fontSize = GyText.label,
                        color = if (item.destructive) Gyp.danger else Gyp.ink,
                        fontFamily = GuangYueDesign.Sans,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/** [FoliaMenu] 的一个条目。 */
data class FoliaMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val divider: Boolean = false,
)

/**
 * 折叠展开（`kit/Reveal.tsx` 的 `st-unfold`）：高度 0 → 内容高度 + 透明度 0 → 1，
 * 用 Folia 的 unfold（300ms，ease-out）。收起后内容不占位。
 */
@Composable
fun FoliaUnfold(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = expanded,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = tween(GyMotion.UNFOLD_MS, easing = GyMotion.easeOut),
        ) + androidx.compose.animation.expandVertically(
            animationSpec = tween(GyMotion.UNFOLD_MS, easing = GyMotion.easeOut),
            expandFrom = Alignment.Top,
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = tween(GyMotion.UNFOLD_MS, easing = GyMotion.easeOut),
        ) + androidx.compose.animation.shrinkVertically(
            animationSpec = tween(GyMotion.UNFOLD_MS, easing = GyMotion.easeOut),
            shrinkTowards = Alignment.Top,
        ),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

/**
 * Folia 的输入框表面（`kit/TextField.tsx`）：高 32dp、圆角 8dp、`--st-fill` 底、13px，
 * 聚焦时描边换成强调色（颜色 fast 120ms）。
 */
@Composable
fun FoliaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(if (singleLine) 32.dp else 40.dp)
            .clip(GyRadius.fieldShape())
            .background(Gyp.tint, GyRadius.fieldShape())
            .padding(horizontal = GySpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GySpace.s),
    ) {
        leading?.invoke()
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = GyText.label,
                    color = Gyp.t3,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = Gyp.ink,
                    fontSize = GyText.label,
                    fontFamily = GuangYueDesign.Sans,
                ),
                cursorBrush = SolidColor(Gyp.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

/**
 * 一组会换行的标签（搜索历史、推荐词）。样式取 Folia 分段控件的选项：
 * 高 28dp、圆角 8dp、`--st-fill` 底、12px `--st-t2`。
 */
@Composable
fun FlowChips(
    labels: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH),
        horizontalArrangement = Arrangement.spacedBy(GySpace.xs),
        verticalArrangement = Arrangement.spacedBy(GySpace.xs),
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(GyRadius.fieldShape())
                    .background(Gyp.tint, GyRadius.fieldShape())
                    .pointerInput(label) { detectTapGestures { onClick(label) } }
                    .padding(horizontal = GySpace.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = GyText.hint,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Folia 的空状态（`Lattice.css` 的 `.lattice-empty`）：整块居中、项目间距 9dp，
 * 主句 17px 字距 0.16em，副句 12px 不透明度 .5。
 */
@Composable
fun FoliaEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = title,
            fontSize = GyText.emptyTitle,
            letterSpacing = GyText.emptyTitleSpacing,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = GyText.emptySub,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.5f),
            )
        }
        action?.invoke()
    }
}
