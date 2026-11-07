// 对应 Folia 文件：src/components/visualizer/tilt/VisualizerTilt.tsx（TiltLine 与舞台）
//
// V2d「倾诉」舞台：当前句拆成 1–4 段竖排居中，段到点才淡入上移；斜体段字距更宽、奇偶字上下错开；
// 每个字按逐字时间脉动放大（普通 1.15，斜体 1.18）。
// 手机化：Folia 每字一个 motion.span；这里每段一行 Row，字的入场用 animateFloatAsState + 延迟，
// 脉动在 graphicsLayer 里读播放时钟（只走绘制，不重组）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Tilt
import com.guangyue.core.lyrics.TiltColorScheme
import com.guangyue.core.lyrics.TiltSegment
import com.guangyue.core.lyrics.TiltTuning

private val TiltEase = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

@Composable
internal fun TiltLyricLine(
    line: Line,
    clock: VisualizerClock,
    tuning: TiltTuning,
    lyricsFontScale: Double,
    primaryColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val vw = LocalConfiguration.current.screenWidthDp.toDouble()
    val styleFor: (Double, Boolean) -> TextStyle = { px, tilt ->
        TextStyle(
            fontSize = with(density) { px.toFloat().dp.toSp() },
            fontFamily = fontFamily,
            fontWeight = if (tilt) FontWeight.Light else FontWeight.Normal,
            fontStyle = if (tilt) FontStyle.Italic else FontStyle.Normal,
        )
    }
    val layout = remember(line.fullText, line.startTime, tuning, lyricsFontScale, vw) {
        val m: (String, Double, Boolean) -> Double = { t, px, tilt ->
            measurer.measure(t, styleFor(px, tilt), softWrap = false).size.width / density.density.toDouble()
        }
        Tilt.buildLayout(line.fullText, line.startTime, tuning, lyricsFontScale, vw, { t, px -> m(t, px, false) }, { t, px -> m(t, px, true) })
    }
    val timings = remember(line, layout) { Tilt.segmentTimings(line, layout) }
    val visibleIndex by remember(line, timings) {
        derivedStateOf { Tilt.visibleSegmentIndex(clock.seconds(), line, timings) }
    }
    val (normalColor, tiltColor) = when (tuning.colorScheme) {
        TiltColorScheme.SWAP -> accentColor to primaryColor
        TiltColorScheme.ACCENT_ALL -> accentColor to accentColor
        TiltColorScheme.PRIMARY_ALL -> primaryColor to primaryColor
        else -> primaryColor to accentColor
    }
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            layout.segments.forEachIndexed { si, seg ->
                key(si, seg.text) {
                    TiltSegmentRow(
                        seg = seg,
                        line = line,
                        start = timings.getOrNull(si)?.first ?: 0.0,
                        end = timings.getOrNull(si)?.second ?: 0.0,
                        visible = si <= visibleIndex,
                        clock = clock,
                        fontScale = lyricsFontScale * layout.scaleMultiplier,
                        vw = vw,
                        color = if (seg.isTilt) tiltColor else normalColor,
                        style = styleFor(0.0, seg.isTilt),
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }
    }
}

@Composable
private fun TiltSegmentRow(
    seg: TiltSegment,
    line: Line,
    start: Double,
    end: Double,
    visible: Boolean,
    clock: VisualizerClock,
    fontScale: Double,
    vw: Double,
    color: Color,
    style: TextStyle,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current
    val boost = if (seg.isShortLastLine && !seg.isTilt) 1.18 else 1.0
    val r = Tilt.REM_PX
    // clamp(3.125rem, 6.875vw, 5.625rem) × 缩放（普通段再乘短尾放大）
    val fontPx = (6.875 * vw / 100 * fontScale * boost).coerceIn(3.125 * r * fontScale * boost, 5.625 * r * fontScale * boost)
    val tiltFontPx = minOf(vw * 0.06875 * fontScale, 5.625 * r * fontScale)
    val yOffset = tiltFontPx / 6
    val graphemes = remember(seg.text) { GraphemeTimings.splitLyricGraphemes(seg.text) }
    val charTimings = remember(seg, start, end, line) { Tilt.charTimings(seg, start, end, line) }
    val lineDur = if (seg.isTilt) 600 else 550
    val lineAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (reduceMotion) 0 else lineDur, easing = TiltEase), label = "tiltLineA")
    val lineY by animateFloatAsState(if (visible) 0f else (if (seg.isTilt) 24f else 20f), tween(if (reduceMotion) 0 else lineDur, easing = TiltEase), label = "tiltLineY")
    val lineScale by animateFloatAsState(if (visible || !seg.isTilt) 1f else 0.92f, tween(if (reduceMotion) 0 else lineDur, easing = TiltEase), label = "tiltLineS")
    val textStyle = style.copy(
        fontSize = with(density) { fontPx.toFloat().dp.toSp() },
        letterSpacing = if (seg.isTilt) 0.15.em else 0.08.em,
        lineHeight = if (seg.isTilt) 1.25.em else 1.35.em,
        color = color,
    )
    Row(
        modifier = Modifier.graphicsLayer {
            alpha = lineAlpha
            translationY = lineY * density.density
            scaleX = lineScale
            scaleY = lineScale
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var ci = 0
        graphemes.forEachIndexed { ti, g ->
            val isSpace = g.isBlank()
            val visualIndex = ci
            if (!isSpace) ci++
            if (isSpace) {
                Box(Modifier.width(with(density) { (fontPx * (if (seg.isTilt) 0.35 else 0.25)).toFloat().dp }))
                return@forEachIndexed
            }
            val stagger = if (visualIndex % 2 == 0) -1f else 1f
            val delay = if (visible && !reduceMotion) visualIndex * (if (seg.isTilt) 50 else 40) else 0
            val a by animateFloatAsState(if (visible) 1f else 0f, tween(if (reduceMotion) 0 else 500, delay, TiltEase), label = "tiltCharA")
            val yTarget = if (!seg.isTilt) 0f else stagger * yOffset.toFloat() * (if (visible) 1f else 2f)
            val y by animateFloatAsState(yTarget, tween(if (reduceMotion) 0 else 500, delay, TiltEase), label = "tiltCharY")
            val timing = charTimings.getOrNull(visualIndex)
            val pulseGain = if (seg.isTilt) 0.18 else 0.15
            Text(
                text = g,
                style = textStyle,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = a
                    translationY = y * density.density
                    val s = if (timing == null || !visible) 1f else (1 + Tilt.pulse(clock.readForDraw(), timing) * pulseGain).toFloat()
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}
