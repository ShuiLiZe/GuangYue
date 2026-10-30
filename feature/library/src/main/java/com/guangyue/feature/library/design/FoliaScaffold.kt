package com.guangyue.feature.library.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.guangyue.feature.library.design.icons.GuangYueIcons

/**
 * 系统栏高度（px）：状态栏与刘海取大者、手势条、左右刘海。
 *
 * 0.0.8 起全 App 走 edge-to-edge（`enableEdgeToEdge()` 已在 MainActivity 调用），每一页
 * 自己负责把内容放进安全区。`safeDrawing` 已包含 statusBars / IME / navigationBars；
 * 横屏时刘海在左右，再与 `displayCutout` 取大值兜底。
 */
data class FoliaInsets(val top: Int, val bottom: Int, val left: Int, val right: Int)

@Composable
fun rememberFoliaInsets(): FoliaInsets {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    return FoliaInsets(
        top = maxOf(safe.calculateTopPadding(), cutout.calculateTopPadding()).value.toInt(),
        bottom = safe.calculateBottomPadding().value.toInt(),
        left = maxOf(safe.calculateLeftPadding(LayoutDirection.Ltr), cutout.calculateLeftPadding(LayoutDirection.Ltr)).value.toInt(),
        right = maxOf(safe.calculateRightPadding(LayoutDirection.Ltr), cutout.calculateRightPadding(LayoutDirection.Ltr)).value.toInt(),
    )
}

/**
 * 页面的顶部标题。
 *
 * Folia 的标题在窗口标题栏（Electron 去掉的），手机上改成页内标题：字重 semibold、
 * 20/32（`PageHeader.tsx` 的 `text-xl font-semibold leading-8`），左 16dp（`pl-4`）。
 * 二级页的返回键是 32dp 圆角方块（`h-8 w-8 rounded-lg`），图标 16dp，与 Folia 关闭钮同规格。
 *
 * 0.0.8：顶部自动避开状态栏 / 刘海（`WindowInsets.statusBars` ∪ `displayCutout`），
 * 否则标题会和状态栏时间重叠（0.0.7 真机截图就是这么被压住的）。
 */
@Composable
fun FoliaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    serif: Boolean = true,
    actions: @Composable (() -> Unit)? = null,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTop = maxOf(statusTop, cutoutTop)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) GySpace.xs else GySpace.pageH,
                end = GySpace.l,
                top = safeTop + GySpace.pageH,
                bottom = GySpace.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(GyRadius.fieldShape())
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuangYueIcons.ArrowLeft,
                    contentDescription = "返回",
                    tint = Gyp.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(GySpace.xxs))
        }
        Text(
            text = title,
            fontSize = GyText.title,
            lineHeight = GyText.titleHeight,
            fontWeight = FontWeight.SemiBold,
            color = Gyp.ink,
            fontFamily = if (serif) GuangYueDesign.Serif else GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions?.invoke()
    }
}

/** 区块标题（Folia `Group` 的 h3：13/20 semibold，色 --st-t2，上下留 8/10dp）。 */
@Composable
fun FoliaSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = GySpace.pageH, end = GySpace.pageH, top = GySpace.l, bottom = GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            fontWeight = FontWeight.SemiBold,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** 底部导航的一项。 */
data class FoliaNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 底部导航。
 *
 * Folia 在桌面上没有底栏，手机版按 m00161 用三项底部导航。样式不自己发明：高度 56dp、
 * 底为磨砂玻璃（`GyGlass.panel`）+ 顶部 1px 发丝线（`--st-line`），选中项用 Folia 的
 * 分段控件标记（`--st-raise` 胶囊 + 8dp 圆角 + `0 1px 2px rgba(0,0,0,.2)`），
 * 文字 12sp，颜色切换 move（220ms out）。
 */
@Composable
fun FoliaBottomNav(
    items: List<FoliaNavItem>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairline = Gyp.hairline
    // 底栏贴着屏幕底：手势条 / 三键导航的高度要补在底栏内部，否则按钮被手势条压住。
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navBottom)
            .then(Modifier.glassPanel(corner = 0.dp))
            .drawBehind {
                drawRect(
                    color = hairline,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1f),
                )
            }
            .padding(horizontal = GySpace.s, vertical = GySpace.xs),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == current
            val tint by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(GyMotion.MOVE_MS, easing = GyMotion.easeOut),
                label = "navTint",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(GyRadius.field))
                    .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null) {
                        if (!selected) onSelect(item.route)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 28.dp)
                        .clip(RoundedCornerShape(GyRadius.mark))
                        .background(Gyp.raise.copy(alpha = tint)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = lerpColor(Gyp.t2, Gyp.ink, tint),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    fontSize = GyText.hint,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = lerpColor(Gyp.t2, Gyp.ink, tint),
                    fontFamily = GuangYueDesign.Sans,
                    maxLines = 1,
                )
                Spacer(Modifier.height(GySpace.xxs))
            }
        }
    }
}

/** 两色之间按 t（0..1）插值，用来给导航项做 220ms 的颜色过渡，避免整项重组。 */
internal fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)
