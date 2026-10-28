package com.guangyue.feature.library.design

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind

/**
 * Folia 手机版设计语言的公共「液态/大理石」背景。
 *
 * 之前这段绘制只写在 [com.guangyue.feature.library.home.HomeScreen] 里的一个私有扩展上，
 * 新加的页面（插件管理、下载、搜索…）只能自己硬编码 [GuangYueDesign.Ink] / `Color.White`
 * 来假装一致，于是深色模式下整页变成白底白字。现在提到设计层，所有页面共用同一块底：
 *
 * * 浅色：大理石白 —— 对角线渐变 + 5 团径向柔光（冰蓝 / 暖砂）+ 5 条细白脉络 + 7dp 点阵；
 * * 深色：同一套结构换成深海蓝渐层，柔光换成冷蓝/紫调，脉络与点阵用极低白，保证不刺眼。
 *
 * 用法：`Modifier.marbleBackground()`，或直接 `MarbleBackground { … }`。
 */
@Composable
fun Modifier.marbleBackground(dark: Boolean = isDarkInContext()): Modifier = drawBehind {
    drawMarble(dark)
}

@Composable
fun MarbleBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = isDarkInContext(),
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().marbleBackground(dark)) { content() }
}

@Composable
private fun isDarkInContext(): Boolean {
    // 默认跟系统走，但页面多用 MaterialTheme 的深浅，这里读配色亮度判断，
    // 保证和 GuangYueDesign.Theme(darkTheme = …) 严格一致（比如歌词舞台强制深色）。
    val scheme = MaterialTheme.colorScheme
    return scheme.background.luminanceApprox() < 0.4f
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private fun DrawScope.drawMarble(dark: Boolean) {
    if (dark) {
        drawRect(
            Brush.linearGradient(
                listOf(GuangYueDesign.NavyTop, GuangYueDesign.NavyMid, GuangYueDesign.NavyBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.6f, size.height),
            ),
        )
    } else {
        drawRect(
            Brush.linearGradient(
                listOf(GuangYueDesign.MarbleTop, GuangYueDesign.MarbleMid, GuangYueDesign.MarbleBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.6f, size.height),
            ),
        )
    }

    fun wash(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width * cx, size.height * cy),
                radius = size.width * r,
            ),
            radius = size.width * r,
            center = Offset(size.width * cx, size.height * cy),
        )
    }

    if (dark) {
        // 夜间的「液态玻璃」：冷蓝与一点紫，光晕压得很低，避免整屏发灰。
        wash(0.18f, 0.10f, 0.75f, Color(0xFF1B3A7A), 0.55f)
        wash(0.90f, 0.20f, 0.65f, Color(0xFF2A2350), 0.50f)
        wash(0.32f, 0.58f, 0.85f, Color(0xFF122B57), 0.45f)
        wash(0.82f, 0.86f, 0.75f, Color(0xFF151B3A), 0.55f)
        wash(0.06f, 0.96f, 0.55f, Color(0xFF0A0F22), 0.65f)
    } else {
        wash(0.15f, 0.08f, 0.7f, Color(0xFFE3EBFA), 0.9f)
        wash(0.92f, 0.22f, 0.6f, Color(0xFFF3E9E2), 0.85f)
        wash(0.30f, 0.55f, 0.8f, GuangYueDesign.MarbleBlueTint, 0.7f)
        wash(0.80f, 0.82f, 0.75f, GuangYueDesign.MarbleWarm, 0.7f)
        wash(0.05f, 0.95f, 0.55f, Color(0xFFFFFFFF), 0.8f)
    }

    drawVeins(dark)
    drawDotGrid(dark)
}

/** 五条细贝塞尔「石纹」：浅色用白色、深色用极低白色，方向一致。 */
private fun DrawScope.drawVeins(dark: Boolean) {
    val maxAlpha = if (dark) 0.10f else 0.43f
    val strokeWidth = if (dark) 20f else 26f
    var i = 0
    while (i < 5) {
        val y = size.height * (0.12f + i * 0.19f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(-40f, y)
            cubicTo(
                size.width * 0.25f, y - 60f,
                size.width * 0.55f, y + 70f,
                size.width + 40f, y - 20f,
            )
        }
        drawPath(
            path,
            Color.White.copy(alpha = maxAlpha * (0.7f + 0.1f * (i % 3))),
            style = Stroke(width = strokeWidth),
        )
        i++
    }
}

/** 7dp 点阵：浅色是蓝点 16%，深色是白点 6%，保持同样的节奏感。 */
private fun DrawScope.drawDotGrid(dark: Boolean) {
    val step = 7.dp.toPx()
    val dot = if (dark) Color.White.copy(alpha = 0.06f) else GuangYueDesign.Blue.copy(alpha = 0.16f)
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawCircle(dot, radius = 0.5f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
