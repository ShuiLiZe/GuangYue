// 对应 Folia 文件：src/components/visualizer/claddagh/VisualizerCladdagh.tsx（环半径、行偏移弹簧、渲染窗口、中轴线）
//                CladdaghRingLine.tsx（逐字绘制）
//
// V2e「回环」舞台。手机化：
//  1. 每字的 CSS blur（离焦最多 8px）在画布里用透明度折减近似（×(1 − blur/16)）。
//  2. 没有音频电平：环不随低音放大，中轴线固定为主色 20% 透明度的渐隐线段。
//  3. 字号 72px × 歌词字号倍率按 dp 计（同流光，避免与系统字号双重放大）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.guangyue.core.lyrics.Claddagh
import com.guangyue.core.lyrics.CladdaghTuning
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun CladdaghStage(
    lines: List<Line>,
    centerIndex: Int,
    clock: VisualizerClock,
    tuning: CladdaghTuning,
    lyricsFontScale: Double,
    primaryColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value.toDouble()
        val rx = min(w * 0.44, 560.0) * tuning.radiusScale
        // 手机化：Folia 72px 字配的是桌面 Rx≈352（800 宽）；窄屏按 Rx 比例缩字（下限 0.55），否则字挤成一团。
        val fontPx = 72.0 * lyricsFontScale * (min(w * 0.44, 560.0) / 352.0).coerceIn(0.55, 1.0)
        val style = TextStyle(
            fontSize = with(density) { fontPx.toFloat().dp.toSp() },
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = Claddagh.LETTER_SPACING_EM.em,
        )
        val spacingScale = (1 + tuning.focusScaleRatio) / (1 + CladdaghTuning.DEFAULT.focusScaleRatio)
        val cache = remember(lines, rx, fontPx, tuning) { HashMap<Int, List<com.guangyue.core.lyrics.CladdaghGlyph>>() }
        fun glyphs(i: Int) = cache.getOrPut(i) {
            val l = lines.getOrNull(i) ?: return@getOrPut emptyList()
            val tl = Claddagh.adjustTimeline(GraphemeTimings.buildDenseLineGraphemeTimeline(l), l)
            Claddagh.spacing(tl, fontPx, rx, spacingScale, tuning.letterSpacingOffset) { prefix ->
                measurer.measure(prefix, style, softWrap = false).size.width / density.density.toDouble()
            }
        }
        val center = centerIndex.coerceAtLeast(-1)
        // 行偏移：spring(stiffness 55, damping 14, mass 0.9)；跨行 > 1 直接跳。
        val offset = remember { Animatable((center * PI).toFloat()) }
        LaunchedEffect(center) {
            val target = (center * PI).toFloat()
            if (reduceMotion || abs(target - offset.value) > PI * 1.5) offset.snapTo(target)
            else offset.animateTo(target, spring(dampingRatio = (14.0 / (2 * kotlin.math.sqrt(55.0 * 0.9))).toFloat(), stiffness = 55f / 0.9f))
        }
        Canvas(Modifier.fillMaxSize()) {
            val d = density.density
            val cx = size.width / 2
            val cy = size.height / 2
            // 中轴线：宽 300、高 4，旋转 90° − 倾角，两端渐隐。
            if (tuning.showAxisLine) {
                val c = primaryColor.copy(alpha = 0.2f)
                rotate((90 - tuning.ellipseTiltDeg).toFloat(), pivot = Offset(cx, cy)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent, 0.2f to c, 0.8f to c, 1f to Color.Transparent,
                            startX = cx - 150 * d, endX = cx + 150 * d,
                        ),
                        topLeft = Offset(cx - 150 * d, cy - 2 * d),
                        size = androidx.compose.ui.geometry.Size(300 * d, 4 * d),
                    )
                }
            }
            if (lines.isEmpty() || center < 0) return@Canvas
            val time = clock.readForDraw()
            val lineOffset = offset.value.toDouble()
            val active = glyphs(center)
            for (idx in (center - 1)..(center + 2)) {
                val line = lines.getOrNull(idx) ?: continue
                val gs = glyphs(idx)
                val frames = Claddagh.frame(gs, line, idx, center, center, lines, active, time, lineOffset, rx, fontPx, tuning)
                frames.forEachIndexed { i, f ->
                    if (f == null) return@forEachIndexed
                    val blurFade = (1 - f.blur / 16).coerceIn(0.4, 1.0)
                    val a = (f.opacity * f.alpha * blurFade).toFloat().coerceIn(0f, 1f)
                    if (a < 0.003f) return@forEachIndexed
                    val m = f.colorMix.toFloat()
                    val color = Color(
                        red = primaryColor.red + (accentColor.red - primaryColor.red) * m,
                        green = primaryColor.green + (accentColor.green - primaryColor.green) * m,
                        blue = primaryColor.blue + (accentColor.blue - primaryColor.blue) * m,
                        alpha = a,
                    )
                    val layout = measurer.measure(gs[i].char, style, softWrap = false)
                    val glow = f.glowRadius
                    translate(cx + (f.x * d).toFloat(), cy + (f.y * d).toFloat()) {
                        rotate(f.rotate.toFloat(), pivot = Offset.Zero) {
                            scale(f.scale.toFloat(), pivot = Offset.Zero) {
                                drawText(
                                    layout,
                                    color = color,
                                    topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f),
                                    shadow = if (glow > 0.5) Shadow(color.copy(alpha = a * ((glow - 0.5) / 2.0).toFloat().coerceIn(0f, 1f)), Offset.Zero, (glow * d).toFloat()) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
