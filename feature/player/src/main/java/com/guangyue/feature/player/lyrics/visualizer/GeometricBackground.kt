// 对应 Folia 文件：src/components/visualizer/backgrounds/common/GeometricBackground.tsx + GeometricBackground.css
//
// 通用几何背景：15 个漂移自转的形状（圆 / 方 / 三角 / 叉，次要色，透明度 0.11–0.19，30% 实心）
// + 20 个上浮的小光点（强调色）。所有样式共用（静止除外）；浮名由自己的参数决定显隐。
// 手机化：
//  1. Folia 用 Math.random() 每次挂载随机；这里用歌曲种子的确定性随机，换歌换一套，重组不跳。
//  2. 没有音频：形状缩放取 Folia 静音时的 0.95；主题图标（lyricsIcons）暂不支持，图标位照常画形状。
//  3. 暗角（radial transparent 40% → rgba(0,0,0,.6)）不加：广乐的流体背景已带 overlay 渐变，叠加会过暗。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate



private data class GeoShape(
    val type: Int, // 0 圆 1 方 2 三角 3 叉
    val x: Float, val y: Float, val size: Float,
    val duration: Float, val delay: Float, val opacity: Float,
    val reverse: Boolean, val filled: Boolean, val rotation: Float,
)

private data class GeoParticle(val size: Float, val x: Float, val y: Float, val opacity: Float, val duration: Float, val delay: Float)

private class SeededRandom(seed: Long) {
    private var s = seed xor 0x5DEECE66DL
    fun next(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }
}

@Composable
internal fun GeometricBackgroundLayer(
    seed: Long,
    secondaryColor: Color,
    accentColor: Color,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shapes = remember(seed) {
        val r = SeededRandom(seed)
        var iconSlots = 0
        List(15) {
            val icon = iconSlots < 6 && r.next() > 0.7f
            if (icon) { iconSlots++; r.next() }
            GeoShape(
                type = (r.next() * 4).toInt().coerceIn(0, 3),
                x = r.next(), y = r.next(),
                size = 40 + r.next() * 100,
                duration = if (icon) 20 + r.next() * 20 else 30 + r.next() * 30,
                delay = r.next() * 5,
                opacity = 0.11f + r.next() * 0.08f,
                reverse = r.next() > 0.5f,
                filled = r.next() < 0.3f,
                rotation = r.next() * 360,
            )
        }
    }
    val particles = remember(seed) {
        val r = SeededRandom(seed * 31 + 7)
        List(20) { GeoParticle(r.next() * 4 + 1, r.next(), r.next(), r.next() * 0.3f, 15 + r.next() * 20, r.next() * 10) }
    }
    // 动画时钟：暂停时停在原处（Folia animation-play-state: paused）。
    val elapsedNs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) elapsedNs.longValue += (n - last).coerceAtMost(100_000_000L)
                last = n
            }
        }
    }
    val cross = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        val t = elapsedNs.longValue / 1e9f
        val d = density
        shapes.forEach { s ->
            val local = t - s.delay
            val p = if (local <= 0f) 0f else (local % s.duration) / s.duration
            // drift：0 → 50% → 100%：(-15, 30) → (15, -30) → (-15, 30)，自转 0 → 360°（线性）。
            val k = if (p < 0.5f) p * 2 else 2 - p * 2
            val dir = if (s.reverse) -1f else 1f
            val dx = (-15 + 30 * k) * dir * d
            val dy = (30 - 60 * k) * dir * d
            val rot = s.rotation + 360 * p
            val px = s.size * d
            val left = s.x * size.width + dx
            val top = s.y * size.height + dy
            val c = secondaryColor.copy(alpha = s.opacity)
            translate(left + px / 2, top + px / 2) {
                rotate(rot, pivot = Offset.Zero) {
                    scale(0.95f, pivot = Offset.Zero) {
                        val o = Offset(-px / 2, -px / 2)
                        val stroke = (s.type == 0 || s.type == 1) && !s.filled
                        when (s.type) {
                            0 -> if (stroke) drawCircle(c, px / 2, Offset.Zero, style = Stroke(1f * d)) else drawCircle(c, px / 2, Offset.Zero)
                            1 -> if (stroke) drawRect(c, o, Size(px, px), style = Stroke(1f * d)) else drawRect(c, o, Size(px, px))
                            2 -> {
                                cross.reset()
                                cross.moveTo(0f, -px / 2); cross.lineTo(-px / 2, px / 2); cross.lineTo(px / 2, px / 2); cross.close()
                                drawPath(cross, c)
                            }
                            else -> {
                                // polygon(20% 0, 0 20%, 30% 50%, 0 80%, 20% 100%, 50% 70%, 80% 100%, 100% 80%, 70% 50%, 100% 20%, 80% 0, 50% 30%)
                                val pts = floatArrayOf(.2f, 0f, 0f, .2f, .3f, .5f, 0f, .8f, .2f, 1f, .5f, .7f, .8f, 1f, 1f, .8f, .7f, .5f, 1f, .2f, .8f, 0f, .5f, .3f)
                                cross.reset()
                                for (i in pts.indices step 2) {
                                    val x = o.x + pts[i] * px
                                    val y = o.y + pts[i + 1] * px
                                    if (i == 0) cross.moveTo(x, y) else cross.lineTo(x, y)
                                }
                                cross.close()
                                drawPath(cross, c)
                            }
                        }
                    }
                }
            }
        }
        particles.forEach { q ->
            val local = t - q.delay
            if (local <= 0f) return@forEach // backwards fill：首帧 opacity 0
            val p = (local % q.duration) / q.duration
            val a = (if (p < 0.5f) p * 2 else 2 - p * 2) * q.opacity
            if (a <= 0.002f) return@forEach
            drawCircle(
                accentColor.copy(alpha = a),
                radius = q.size * d / 2,
                center = Offset(q.x * size.width + q.size * d / 2, q.y * size.height - 100 * d * p + q.size * d / 2),
            )
        }
    }
}

