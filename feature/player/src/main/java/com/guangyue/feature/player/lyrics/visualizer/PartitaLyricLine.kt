// 对应 Folia 文件：
//   src/components/visualizer/partita/VisualizerPartita.tsx（舞台：当前句、呼吸浮动、字号 clamp）
//   src/components/visualizer/partita/PartitaChunk.tsx（chunk 三态、引导线、chunk 内逐词随机偏移）
//   src/components/visualizer/GlowWord.tsx（每个词仍由流光同一个 GlowWord 渲染）
//
// V2a「云阶」：当前句切成若干 chunk 竖排，左右交错，像乐谱上的一级级台阶。
// 手机化改动：
//  1. Folia 的引导线是 rgba(255,255,255,.14/.22)（只考虑深色主题），这里改用舞台基色同样透明度，浅色主题下也看得见。
//  2. chunk 的 spring（stiffness 200 / damping 20）用 Compose spring 等价参数；离场 0.4s easeOut。
//  3. CSS perspective 无等价物，不做 3D（旋转仍是 Z 轴），同流光。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.GlowWordRenderProfile
import com.guangyue.core.lyrics.GlowWordTiming
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.PartitaChunk
import com.guangyue.core.lyrics.PartitaLayoutBuilder
import com.guangyue.core.lyrics.PartitaTuning

private enum class ChunkStatus { WAITING, ACTIVE, PASSED }

@Composable
internal fun PartitaLyricLine(
    line: Line,
    clock: VisualizerClock,
    tuning: PartitaTuning,
    intensity: AnimationIntensity,
    reduceMotion: Boolean,
    viewportWidthDp: Double,
    windowHeightDp: Double,
    lyricsFontScale: Double,
    activeColor: Color,
    baseColor: Color,
    fontFamily: FontFamily,
    glowStrengthOverride: Float? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 布局缓存键 = (行, 强度, 窗口高度档, 调参)；同 Folia buildPartitaLayoutCacheKey（高度按 24 一档）。
    val heightBucket = (windowHeightDp / 24.0).let { kotlin.math.round(it) }
    val layout = remember(line, intensity, heightBucket, tuning) {
        PartitaLayoutBuilder.build(line, intensity, windowHeightDp, tuning)
    }
    val profile = remember(line) { GlowWordTiming.resolveGlowWordRenderProfile(line) } ?: return
    val fontPx = PartitaLayoutBuilder.fontSizePx(layout, lyricsFontScale, viewportWidthDp) * density.density
    val fontSizeSp = with(density) { fontPx.toFloat().toSp() }.value

    // 舞台呼吸浮动（VisualizerPartita.tsx lyricContainerFloat）：calm 10/8.5s，normal 14/7s，chaotic 18/5.8s。
    val (distance, durationS) = when (intensity) {
        AnimationIntensity.CALM -> 10f to 8.5
        AnimationIntensity.CHAOTIC -> 18f to 5.8
        else -> 14f to 7.0
    }
    val floatY: Float
    val floatScale: Float
    if (reduceMotion) {
        floatY = 0f; floatScale = 1f
    } else {
        val ms = (durationS * 1000).toInt()
        val transition = rememberInfiniteTransition(label = "partitaFloat")
        val y by transition.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = ms
                    0f at 0 using FastOutSlowInEasing
                    -distance at ms / 4 using FastOutSlowInEasing
                    0f at ms / 2 using FastOutSlowInEasing
                    distance * 0.45f at ms * 3 / 4 using FastOutSlowInEasing
                },
                RepeatMode.Restart,
            ),
            label = "partitaFloatY",
        )
        val s by transition.animateFloat(
            initialValue = 1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = ms
                    1f at 0 using FastOutSlowInEasing
                    1.01f at ms / 4 using FastOutSlowInEasing
                    1f at ms / 2 using FastOutSlowInEasing
                    0.995f at ms * 3 / 4 using FastOutSlowInEasing
                },
                RepeatMode.Restart,
            ),
            label = "partitaFloatScale",
        )
        floatY = y; floatScale = s
    }

    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                translationY = floatY * density.density
                scaleX = floatScale
                scaleY = floatScale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            layout.chunks.forEach { chunk ->
                androidx.compose.runtime.key(chunk.config.id) {
                    PartitaChunkView(
                        chunk = chunk,
                        clock = clock,
                        profile = profile,
                        intensity = intensity,
                        reduceMotion = reduceMotion,
                        showGuideLines = tuning.showGuideLines,
                        activeColor = activeColor,
                        baseColor = baseColor,
                        fontSizeSp = fontSizeSp,
                        fontFamily = fontFamily,
                        isChorus = line.isChorus,
                        glowStrengthOverride = glowStrengthOverride,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartitaChunkView(
    chunk: PartitaChunk,
    clock: VisualizerClock,
    profile: GlowWordRenderProfile,
    intensity: AnimationIntensity,
    reduceMotion: Boolean,
    showGuideLines: Boolean,
    activeColor: Color,
    baseColor: Color,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    isChorus: Boolean,
    glowStrengthOverride: Float?,
) {
    val density = LocalDensity.current
    val start = chunk.chunkWords.first().startTime
    val end = GlowWordTiming.getGlowWordActiveEndTime(chunk.chunkWords.last(), profile)
    val status by remember(chunk, profile) {
        derivedStateOf {
            val t = clock.seconds()
            when {
                t >= start - profile.wordLookahead && t <= end -> ChunkStatus.ACTIVE
                t > end -> ChunkStatus.PASSED
                else -> ChunkStatus.WAITING
            }
        }
    }
    val left = chunk.rowIndex % 2 == 0
    val waiting = status == ChunkStatus.WAITING
    val active = status == ChunkStatus.ACTIVE
    // active：spring(200,20) + opacity 0.1s；其余：0.4s easeOut。
    val springSpec = spring<Float>(dampingRatio = 20f / (2f * kotlin.math.sqrt(200f)), stiffness = 200f)
    val outSpec = tween<Float>(400, easing = FastOutSlowInEasing)
    val alpha by animateFloatAsState(if (waiting) 0f else 1f, if (active) tween(100) else outSpec, label = "chunkAlpha")
    val scale by animateFloatAsState(if (waiting) 0.85f else 1f, if (active) springSpec else outSpec, label = "chunkScale")
    val targetX = if (waiting) chunk.config.x + (if (left) -40.0 else 40.0) else chunk.config.x
    val x by animateFloatAsState(targetX.toFloat(), if (active) springSpec else outSpec, label = "chunkX")
    val guide by animateFloatAsState(if (waiting) 0f else 1f, tween(400, easing = FastOutSlowInEasing), label = "chunkGuide")
    val lineColor = when (status) {
        ChunkStatus.ACTIVE -> activeColor
        ChunkStatus.PASSED -> baseColor.copy(alpha = 0.22f)
        ChunkStatus.WAITING -> baseColor.copy(alpha = 0.14f)
    }

    Row(
        modifier = Modifier
            .padding(bottom = (chunk.config.marginBottomRem * 16).dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationX = x * density.density
                translationY = chunk.config.y.toFloat() * density.density
                rotationZ = chunk.config.rotate.toFloat()
            }
            .drawBehind {
                if (!showGuideLines || guide <= 0f) return@drawBehind
                val px = density.density
                val c = lineColor.copy(alpha = lineColor.alpha * guide)
                val stroke = 1f * px
                // 竖线：贴左（或右）外 8dp，自底边下 16dp 起向上 32dp，scaleY 从底部长出来。
                val vx = if (left) -8f * px else size.width + 8f * px
                val bottom = size.height + 16f * px
                drawLine(c, Offset(vx, bottom), Offset(vx, bottom - 32f * px * guide), stroke)
                // 横线：底边下 8dp，宽 = 100% + 36dp，从左（或右）端展开。
                val hy = size.height + 8f * px
                val w = (size.width + 36f * px) * guide
                if (left) {
                    val x0 = -16f * px
                    drawLine(c, Offset(x0, hy), Offset(x0 + w, hy), stroke)
                } else {
                    val x1 = size.width + 16f * px
                    drawLine(c, Offset(x1, hy), Offset(x1 - w, hy), stroke)
                }
            }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chunk.displayWords.forEachIndexed { index, word ->
            val config = PartitaLayoutBuilder.wordConfig(chunk, index, intensity)
            Box(
                modifier = Modifier.padding(end = if (index < chunk.displayWords.lastIndex) config.marginRight.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlowWord(
                    word = word,
                    config = config,
                    profile = profile,
                    clock = clock,
                    activeColor = activeColor,
                    baseColor = baseColor,
                    intensity = intensity,
                    enableWordRotation = true,
                    fontSizeSp = fontSizeSp,
                    fontFamily = fontFamily,
                    reduceMotion = reduceMotion,
                    isChorus = isChorus,
                    glowStrengthOverride = glowStrengthOverride,
                )
            }
        }
    }
}
