// 对应 Folia 文件：src/components/visualizer/cadenza/VisualizerCadenza.tsx（draw 循环、行包络、合唱涟漪）
//                cadenzaOverlayNodes.ts（DOM 叠字 + text-shadow 辉光）
//
// V2b「心象」舞台：当前句的词按 [Cadenza.prepare] 的落点散在焦点（屏高 42%）周围，
// 每帧按状态（waiting/active/passed）指数平滑到目标位置 / 缩放 / 透明度。
// 手机化改动：
//  1. Folia 的辉光是 DOM text-shadow（3 层 40px），这里用 drawText 的 Shadow 一层模拟；逐字辉光（非 CJK）
//     暂按整词处理（取各字辉光最大值）。
//  2. 单词的 CSS blur（入场 10px）在 Compose 画布里没有廉价等价物，用透明度过渡代替。
//  3. 没有音频电平：audioPower 取 0，motionEnergy = 0（只剩 sin 抬升 3dp）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.Cadenza
import com.guangyue.core.lyrics.CadenzaAnimState
import com.guangyue.core.lyrics.CadenzaStatus
import com.guangyue.core.lyrics.CadenzaTuning
import com.guangyue.core.lyrics.Line
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

@Composable
internal fun CadenzaStage(
    line: Line?,
    clock: VisualizerClock,
    tuning: CadenzaTuning,
    intensity: AnimationIntensity,
    reduceMotion: Boolean,
    lyricsFontScale: Double,
    activeColor: Color,
    baseColor: Color,
    fontFamily: FontFamily,
    /** 取证：>0 时不跑帧循环，组合期按固定 dt 推进这么多帧（模拟器帧时间不可靠）。 */
    settleSteps: Int = 0,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val vw = maxWidth.value.toDouble()
        val vh = maxHeight.value.toDouble()
        // entry.tsx：fontScale × 歌词字号倍率。
        val effective = tuning.copy(fontScale = tuning.fontScale * lyricsFontScale)
        val fontFor: (Double) -> TextStyle = { px ->
            TextStyle(
                fontSize = with(density) { (px * density.density).toFloat().toSp() },
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
        val prepared = remember(line, vw, vh, effective, intensity) {
            line?.let { l ->
                Cadenza.prepare(l, vw, vh, effective, intensity) { text, px ->
                    if (text.isEmpty()) 0.0
                    else measurer.measure(text, fontFor(px), softWrap = false).size.width / density.density.toDouble()
                }
            }
        }
        val states = remember(prepared) { HashMap<String, CadenzaAnimState>() }
        val tick = remember { mutableIntStateOf(0) }
        val timing = remember(line) { line?.let { Cadenza.resolveLineTiming(it) } }

        fun advance(time: Double, dt: Double) {
            val p = prepared ?: return
            val lt = timing ?: return
            val focusY = vh * 0.42 + sin(time * 2.3) * 3.0
            val ordered = p.placements.sortedBy { Cadenza.wordStatus(time, lt, it.word).ordinal.let { o -> if (o == 0) 0 else if (o == 2) 1 else 2 } }
            ordered.forEachIndexed { i, pl ->
                val st = Cadenza.wordStatus(time, lt, pl.word)
                states[pl.id] = Cadenza.step(states[pl.id], pl, i, st, time, dt, lt, vw, focusY, 0.0, tuning, intensity)
            }
        }

        if (settleSteps > 0) {
            val t = clock.readForDraw()
            repeat(settleSteps) { advance(t, 1.0 / 60.0) }
        } else {
            LaunchedEffect(prepared, reduceMotion) {
                var last = 0L
                while (true) {
                    withFrameNanos { n ->
                        val dt = if (last == 0L) 1.0 / 60.0 else ((n - last) / 1e9).coerceIn(1.0 / 240.0, 0.05)
                        last = n
                        advance(clock.readForDraw(), if (reduceMotion) 1.0 else dt)
                        tick.intValue++
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue // 订阅帧
            val p = prepared ?: return@Canvas
            val l = line ?: return@Canvas
            val lt = timing ?: return@Canvas
            val time = clock.readForDraw()
            val env = Cadenza.lineEnvelope(time, l, lt)
            if (env.opacity <= 0.01) return@Canvas
            val d = density.density
            val style = fontFor(p.fontPx)
            // 行包络：transform-origin 50% 42%，整层缩放 + 透明度。
            scale(env.scale.toFloat(), pivot = Offset(size.width * 0.5f, size.height * 0.42f)) {
                for (status in listOf(CadenzaStatus.WAITING, CadenzaStatus.PASSED, CadenzaStatus.ACTIVE)) {
                    for (pl in p.placements) {
                        if (Cadenza.wordStatus(time, lt, pl.word) != status) continue
                        val s = states[pl.id] ?: continue
                        val alpha = (s.bodyAlpha * env.opacity).toFloat()
                        if (s.bodyAlpha < 0.015 && s.glowAlpha < 0.015) continue
                        val anchorX = (s.x + pl.width / 2) * d
                        val anchorY = (s.y - pl.height * 0.42) * d
                        val progress = Cadenza.wordProgress(time, lt.wordRevealMode, pl.word)
                        if (status == CadenzaStatus.ACTIVE && !pl.isInterlude && l.isChorus) {
                            val vwid = pl.width * s.scale
                            val r = max(vwid, pl.height * s.scale * 1.08) * (0.55 + progress * 0.45)
                            drawCircle(
                                color = activeColor.copy(alpha = (0.45 * (1 - progress) * s.bodyAlpha).toFloat().coerceIn(0f, 1f)),
                                radius = (r * d).toFloat(),
                                center = Offset(((s.x - (vwid - pl.width) / 2 + vwid / 2) * d).toFloat(), ((s.y - pl.height * s.scale * 0.42) * d).toFloat()),
                                style = Stroke(1.2f * d),
                            )
                        }
                        val glow = if (lt.wordRevealMode == com.guangyue.core.lyrics.WordRevealMode.NORMAL && !Cadenza.isCjk(pl.text)) {
                            val n = com.guangyue.core.lyrics.GraphemeTimings.splitLyricGraphemes(pl.text).size
                            (0 until n).maxOfOrNull { gi ->
                                Cadenza.charGlow(time, pl.word, pl.fragmentStartInWord + gi, max(pl.wordGraphemeCount, n), pl.wordGraphemeTimings)
                            } ?: 0.0
                        } else {
                            Cadenza.glowEnvelope(time, lt, pl.word)
                        } * s.glowAlpha.coerceIn(0.0, 1.0) * max(tuning.glowIntensity, 0.0)
                        val mixT = s.activeMix.toFloat().coerceIn(0f, 1f)
                        val color = Color(
                            red = baseColor.red + (activeColor.red - baseColor.red) * mixT,
                            green = baseColor.green + (activeColor.green - baseColor.green) * mixT,
                            blue = baseColor.blue + (activeColor.blue - baseColor.blue) * mixT,
                            alpha = alpha.coerceIn(0f, 1f),
                        )
                        val layout = measurer.measure(pl.text, style, softWrap = false)
                        val g = glow.coerceIn(0.0, 1.6)
                        translate(anchorX.toFloat(), anchorY.toFloat()) {
                            rotate(s.rotation.toFloat(), pivot = Offset.Zero) {
                                scale(s.scale.toFloat(), pivot = Offset.Zero) {
                                    // 基线落在局部 y = height*0.42（Folia：anchorY = 基线 − height*0.42，文本盒再下移 textY − ascent）。
                                    val ox = (-pl.width / 2 * d).toFloat()
                                    val baseline = (pl.height * 0.42 * d).toFloat()
                                    drawText(
                                        layout,
                                        color = color,
                                        topLeft = Offset(ox, baseline - layout.firstBaseline),
                                        shadow = if (g > 0.01) Shadow(activeColor.copy(alpha = (min1(g * 0.98) * alpha).toFloat()), Offset.Zero, 40f * d * 0.5f) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun min1(v: Double) = if (v > 0.98) 0.98 else v

@Suppress("unused")
private fun Double.near(o: Double) = abs(this - o) < 1e-6
