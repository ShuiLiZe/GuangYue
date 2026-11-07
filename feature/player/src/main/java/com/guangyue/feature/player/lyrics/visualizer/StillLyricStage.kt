// 对应 Folia 文件：src/components/visualizer/still/VisualizerStill.tsx（全文）、still/entry.tsx
//
// V2c「静止」：上一句 / 当前句 / 下一句三行静态居中，不做动画；暗角渐变（深色 0.65，浅色 0.3）。
// 手机化：Folia 不挂背景渲染器（renderBackground=false），这里沿用页面的封面底色但不改它，
// 只叠一层暗角；字号照 Tailwind（当前 2.5rem / 其余 text-3xl=1.875rem，译文 text-2xl / text-xl）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import com.guangyue.core.lyrics.Line

/** 暗角：radial-gradient(ellipse at center, transparent 0%, #000 100%)。 */
@Composable
internal fun StillVignette(dark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .alpha(if (dark) 0.65f else 0.3f)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    radius = 1400f,
                ),
            ),
    )
}

@Composable
internal fun StillLyricStage(
    lines: List<Line>,
    currentIndex: Int,
    lyricsFontScale: Double,
    primaryColor: Color,
    secondaryColor: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val index = currentIndex.coerceAtLeast(0).let { if (it >= lines.size) 0 else it }
    val scale = lyricsFontScale.toFloat()
    // lyricsFontScale 已含系统字号：按 dp 折 sp，避免与系统字号双重放大（同流光舞台）。
    val density = LocalDensity.current
    val px: (Float) -> TextUnit = { v -> with(density) { (v * scale).dp.toSp() } }
    // drop-shadow-md 近似。
    val shadow = Shadow(Color.Black.copy(alpha = 0.2f), Offset(0f, 3f), 6f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (offset in -1..1) {
            val line = lines.getOrNull(index + offset)
            if (line == null) {
                Spacer(Modifier.height(80.dp))
                continue
            }
            val current = offset == 0
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (line.fullText.isNotEmpty()) {
                    Text(
                        text = line.fullText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(if (current) 1f else 0.3f),
                        style = TextStyle(
                            color = primaryColor,
                            fontFamily = fontFamily,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = px(if (current) 40f else 30f),
                            lineHeight = px(if (current) 50f else 36f),
                            letterSpacing = 0.02.em,
                            shadow = shadow,
                        ),
                    )
                }
                val tr = line.translation
                if (!tr.isNullOrBlank()) {
                    Text(
                        text = tr,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = if (current) 12.dp else 8.dp)
                            .alpha(if (current) 0.8f else 0.3f),
                        style = TextStyle(
                            color = secondaryColor,
                            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                            fontSize = px(if (current) 24f else 20f),
                            letterSpacing = 0.025.em,
                        ),
                    )
                }
            }
        }
    }
}
