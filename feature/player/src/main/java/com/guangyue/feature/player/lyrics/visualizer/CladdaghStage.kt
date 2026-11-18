// 对应 Folia 文件：src/components/visualizer/claddagh/VisualizerCladdagh.tsx（环半径、行偏移弹簧、渲染窗口、中轴线）
//                CladdaghRingLine.tsx（逐字绘制）
//
// V2e「回环」舞台。手机化：
//  1. 每字的 CSS blur（离焦最多 8px）在画布里用透明度折减近似（×(1 − blur/16)）。
//  2. 音频电平取 core:player AudioLevels（ExoPlayer 旁路 FFT，对应 Folia AnalyserNode）：环随平滑低音放大
//     （ringScale 随动画强度），中轴线随低音 / 人声变色伸缩，副歌时加辉光。
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
import com.guangyue.core.lyrics.AnimationIntensity
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
    intensity: AnimationIntensity = AnimationIntensity.NORMAL,
    secondaryColor: Color? = null,
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
        // useSpring(bass, 150/25)、useSpring(vocal, 120/24)；副歌辉光每帧 ±0.05 渐变。
        val bassSpring = remember { AudioSpring(150.0, 25.0) }
        val vocalSpring = remember { AudioSpring(120.0, 24.0) }
        val glowRef = remember { doubleArrayOf(0.0) }
        Canvas(Modifier.fillMaxSize()) {
            val d = density.density
            val cx = size.width / 2
            val cy = size.height / 2
            // 先读时钟：每帧重画，电平才跟得上。
            val time = clock.readForDraw()
            val now = System.nanoTime()
            val bands = StageAudio.bands(now)
            val bass01 = (bassSpring.step(bands.bass, now) / 255.0).coerceIn(0.0, 1.0)
            val vocal01 = (vocalSpring.step(bands.vocal, now) / 255.0).coerceIn(0.0, 1.0)
            val isChorus = lines.getOrNull(center)?.isChorus == true
            val gTarget = if (isChorus) 1.0 else 0.0
            glowRef[0] = if (abs(gTarget - glowRef[0]) > 0.05) (glowRef[0] + kotlin.math.sign(gTarget - glowRef[0]) * 0.05).coerceIn(0.0, 1.0) else gTarget
            // 中轴线：宽 300、高 4，旋转 90° − 倾角，两端渐隐；颜色主色→强调色（同色取次色，再同取白），
            // 透明度 0.2→0.95，长 ×(1 + bass²·1.5)、粗 ×(1 + bass²·0.5)。
            if (tuning.showAxisLine) {
                val ratio = Claddagh.axisColorRatio(bass01, vocal01).toFloat()
                var to = accentColor
                if (to == primaryColor && secondaryColor != null) to = secondaryColor
                if (to == primaryColor) to = Color.White
                val c = Color(
                    red = primaryColor.red + (to.red - primaryColor.red) * ratio,
                    green = primaryColor.green + (to.green - primaryColor.green) * ratio,
                    blue = primaryColor.blue + (to.blue - primaryColor.blue) * ratio,
                    alpha = 0.2f + 0.75f * ratio,
                )
                val bassSqr = (bass01 * bass01).toFloat()
                val halfW = 150 * d * (1f + bassSqr * 1.5f)
                val halfH = 2 * d * (1f + bassSqr * 0.5f)
                rotate((90 - tuning.ellipseTiltDeg).toFloat(), pivot = Offset(cx, cy)) {
                    val glow = glowRef[0].toFloat()
                    if (glow > 0.001f) {
                        // drop-shadow(0 0 (4 + bass·12)·g px) 用一圈更宽更淡的渐隐条近似。
                        val gs = ((4 + bass01 * 12) * glow).toFloat() * d
                        val gc = c.copy(alpha = c.alpha * glow * 0.45f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent, 0.2f to gc, 0.8f to gc, 1f to Color.Transparent,
                                startX = cx - halfW - gs, endX = cx + halfW + gs,
                            ),
                            topLeft = Offset(cx - halfW - gs, cy - halfH - gs),
                            size = androidx.compose.ui.geometry.Size(2 * (halfW + gs), 2 * (halfH + gs)),
                        )
                    }
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent, 0.2f to c, 0.8f to c, 1f to Color.Transparent,
                            startX = cx - halfW, endX = cx + halfW,
                        ),
                        topLeft = Offset(cx - halfW, cy - halfH),
                        size = androidx.compose.ui.geometry.Size(2 * halfW, 2 * halfH),
                    )
                }
            }
            if (lines.isEmpty() || center < 0) return@Canvas
            val ringRx = rx * Claddagh.ringScale(bass01, intensity)
            val lineOffset = offset.value.toDouble()
            val active = glyphs(center)
            for (idx in (center - 1)..(center + 2)) {
                val line = lines.getOrNull(idx) ?: continue
                val gs = glyphs(idx)
                val frames = Claddagh.frame(gs, line, idx, center, center, lines, active, time, lineOffset, ringRx, fontPx, tuning)
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
