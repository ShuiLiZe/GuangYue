// 对应 Folia 文件：
//   src/library/suites/bravais/BravaisWall.tsx、BravaisTile.tsx（墙与砖的组装）
//   src/library/suites/bravais/bravaisConstants.ts（METRICS 128/8、getBravaisScale、OVERSCAN 420）
//   src/components/wall/rankSlots.ts（条目按离原点的排名落位）
//   src/components/wall/useWallPointerPan.ts（拖动平移）
//   src/components/app/lattice/Lattice.css（.lattice-poster：方角封面、歌单顶部两条「纸边」）
//   src/library/suites/bravais/bravais.css（.is-wall 空画框：主色 7% 发丝线）
//
// B1b：首页 Bravais 墙（无限平铺、可拖动）。
// 手机化 / 未移：
//  1. 只做平移 + 惯性；点开一格的整块重排（blockReflows）、翻牌、缝（Seam）页面、聚焦卡留到 B1c 之后。
//  2. 条目有限：按排名填满前 N 个槽，其余是空画框（Folia bravaisFiniteWall 也是这样收尾）。
//  3. 排名原点 = 初始镜头中心；拖动时不重排（Folia 同：排名只在换数据时重算）。
package com.guangyue.music.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guangyue.core.wall.Bounds
import com.guangyue.core.wall.Bravais
import com.guangyue.core.wall.RankSlots
import com.guangyue.core.wall.WallSlots
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BravaisWall(
    tiles: List<HomeTile>,
    inkColor: Color,
    groundColor: Color,
    onOpenTile: (HomeTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val vw = maxWidth.value.toDouble()
        val vh = maxHeight.value.toDouble()
        val scale = Bravais.scale(vw)
        val (bw, bh) = WallSlots.blockSize(Bravais.METRICS)
        // 镜头（世界坐标，左上角）：开场把区块 (0,0) 的中心放到视口中心。
        val startX = bw / 2 - vw / scale / 2
        val startY = bh / 2 - vh / scale / 2
        val camX = remember { Animatable(startX.toFloat()) }
        val camY = remember { Animatable(startY.toFloat()) }
        // 排名：初始镜头中心附近 3×3 区块，条目按排名落位（换数据才重算）。
        val ranked = remember(tiles, vw, vh) {
            val ox = bw / 2
            val oy = bh / 2
            val pool = (-1..1).flatMap { r -> (-1..1).flatMap { c -> WallSlots.blockSlots(c, r) } }
            RankSlots.rank(pool, ox, oy).take(tiles.size).mapIndexed { i, s -> s.key to tiles[i] }.toMap()
        }
        var dragging by remember { mutableStateOf(false) }
        val tracker = remember { VelocityTracker() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(scale) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            tracker.resetTracking()
                            scope.launch { camX.stop(); camY.stop() }
                        },
                        onDragEnd = {
                            dragging = false
                            val v = tracker.calculateVelocity()
                            val k = density.density * scale.toFloat()
                            scope.launch { camX.animateDecay(-v.x / k, exponentialDecay(1.6f)) }
                            scope.launch { camY.animateDecay(-v.y / k, exponentialDecay(1.6f)) }
                        },
                        onDragCancel = { dragging = false },
                    ) { change, delta ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val k = density.density * scale.toFloat()
                        scope.launch {
                            camX.snapTo(camX.value - delta.x / k)
                            camY.snapTo(camY.value - delta.y / k)
                        }
                    }
                },
        ) {
            val cx = camX.value.toDouble()
            val cy = camY.value.toDouble()
            val over = Bravais.OVERSCAN / scale
            val view = Bounds(cx - over, cx + vw / scale + over, cy - over, cy + vh / scale + over)
            WallSlots.collect(view).forEach { slot ->
                val tile = ranked[slot.key]
                val x = ((slot.x - cx) * scale).toFloat()
                val y = ((slot.y - cy) * scale).toFloat()
                val w = (slot.width * scale).toFloat().dp
                val h = (slot.height * scale).toFloat().dp
                Box(
                    Modifier
                        .offset { IntOffset((x * density.density).roundToInt(), (y * density.density).roundToInt()) }
                        .size(w, h),
                ) {
                    if (tile == null) WallFrame(inkColor) else Poster(tile, inkColor, groundColor, scale) { if (!dragging) onOpenTile(tile) }
                }
            }
        }
    }
}

/** 空画框：透明底 + 主色 7% 的 1px 内描边（bravais.css `.is-wall`）。 */
@Composable
private fun WallFrame(ink: Color) {
    Box(Modifier.fillMaxSize().border(1.dp, ink.copy(alpha = 0.07f)))
}

/** 一块砖：方角封面铺满；歌单 / 电台顶部留 16 单位给两条「纸边」（Lattice.css is-kind-playlist）。 */
@Composable
private fun Poster(tile: HomeTile, ink: Color, ground: Color, scale: Double, onClick: () -> Unit) {
    val stacked = tile.kind == HomeTileKind.PLAYLIST || tile.kind == HomeTileKind.SHEET || tile.kind == HomeTileKind.STATION
    val top = if (stacked) (16 * scale).toFloat().dp else 0.dp
    Box(Modifier.fillMaxSize().clickable(onClick = onClick)) {
        if (stacked) {
            // 两条：76% 宽 5 高 @y0（ink 34% 混 ground）、88% 宽 5 高 @y8（ink 58%）。
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width
                val s = (scale * density).toFloat()
                fun mix(a: Float) = Color(
                    ground.red + (ink.red - ground.red) * a,
                    ground.green + (ink.green - ground.green) * a,
                    ground.blue + (ink.blue - ground.blue) * a,
                )
                drawRect(mix(0.34f), Offset(u * 0.12f, 0f), androidx.compose.ui.geometry.Size(u * 0.76f, 5 * s))
                drawRect(mix(0.58f), Offset(u * 0.06f, 8 * s), androidx.compose.ui.geometry.Size(u * 0.88f, 5 * s))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = top)
                .background(ink.copy(alpha = 0.06f)),
        ) {
            if (tile.artwork != null) {
                AsyncImage(
                    model = tile.artwork,
                    contentDescription = tile.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

