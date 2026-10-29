package com.guangyue.feature.library.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// feature/library/.../design/FoliaDraw.kt
// Folia 用 CSS 画、Compose 里没有现成组件的两处：设置行顶部的发丝线和卡片的 1dp 描边环。
// 发丝线对应 kit.css 的 `.st-row::before`（left 16px / 子行 40px、高 1px、色 --st-line）；
// 描边对应 Group.tsx 的 `shadow-[0_0_0_1px_var(--st-line)]`。

/** 顶部一条 1px 发丝线，从左侧 [inset] 起（颜色取主题的 `--st-line`）。 */
fun Modifier.drawTopHairline(inset: Dp): Modifier = composed {
    val line = Gyp.hairline
    drawWithContent {
        drawContent()
        drawLine(
            color = line,
            start = Offset(inset.toPx(), 0.5f),
            end = Offset(size.width, 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** 1dp 描边环，画在边界内侧（对应 CSS 的 `0 0 0 1px`）。 */
fun Modifier.drawRing(color: Color, radius: Dp = GyRadius.card): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}

/**
 * 无涟漪点击（Folia 的按钮/行都没有 Material 涟漪，靠 120ms 的底色与描边变化反馈）。
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
)

/** 行顶部发丝线的开关形式：首行不画。 */
fun Modifier.foliaHairline(top: Boolean, inset: Dp): Modifier =
    if (!top) this else this.drawTopHairline(inset)

/** 卡片：`rounded-xl` 底 `--st-card` + 1dp `--st-line` 环。 */
fun Modifier.foliaCardSurface(): Modifier = composed {
    clip(GyRadius.cardShape())
        .background(Gyp.card, GyRadius.cardShape())
        .drawRing(Gyp.hairline)
}

/**
 * 静态层毛玻璃：对应 Folia 的 `backdrop-blur-sm/md` + 半透明白/黑底。
 *
 * 只在「不会动的层」上用（标题栏、底栏、迷你播放条、浮动卡片）——模糊每帧都要重采样背景，
 * 挂在滚动内容上会直接吃掉帧预算。
 *
 *  - Android 12+ 且不是低内存机（[canBlur]）：先 `Modifier.blur` 再叠 [GyGlass.panel] 半透明底色，
 *    等于 CSS 的 backdrop-filter。
 *  - 其它情况（SDK < 31、`ActivityManager.isLowRamDevice`）：不模糊，只叠底色并把透明度提高
 *    （[GyGlass.panel] 本身已经比浅色更实），观感一致、零渲染成本。
 */
fun Modifier.foliaGlass(
    corner: Dp = GyRadius.card,
    blurRadius: Dp = 16.dp,
): Modifier = composed {
    val allowBlur = LocalCanBlur.current
    val shape = RoundedCornerShape(corner)
    val panel = GyGlass.panel
    val ring = GyGlass.border
    this
        .then(if (allowBlur) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier)
        .clip(shape)
        .background(panel, shape)
        .drawRing(ring, corner)
}
