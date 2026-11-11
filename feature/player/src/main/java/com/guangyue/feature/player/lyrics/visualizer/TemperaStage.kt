// 凝彩（Folia tempera）舞台：R6 按原版重做。对应 Folia 文件：src/components/visualizer/tempera/
//   createTemperaPixiRuntime.ts（段落场景 ±1 缓存、预卷、shot 交接、转场、擦除块）、temperaSceneBuilder.ts（纸色薄罩 + 底网点、
//   每 shot：色块层 → 水印层 → 文字层（反色）→ 浮影层）、temperaBlocks.ts（色块入场 / 沿 flow 漂移 / drift 呼吸 / grow 展开）、
//   temperaShotFrame.ts（镜头关键帧 + 呼吸 + 交接推进）、temperaTextView.ts（字、阴影、浮影、碎字、水印）、
//   temperaDifferenceFilter.ts（文字逐像素取离底色更远的墨 / 纸）、temperaOverlay.ts（block-wipe 擦除块）。
// 编排 / 构图 / 排版 / 运动都在 TemperaCore.kt 与 tempera 包里逐函数对译、金标对拍；这里只做绘制。
// 单位：Folia 的 CSS 像素 = 这里的 dp（整个画布按 density 缩放）。
// 手机化取舍（写进对照表）：
//   · 反色：Pixi 着色器逐像素读底色；这里按色块多边形算「偏墨」区域（同样的判据：底色亮度离墨更近 → 字取纸色），
//     文字层先整层画墨色，再用该区域 SrcIn 填纸色。网线 / 细线不参与判定（太细，原版里也几乎不翻色）。
//   · 后处理（镜头畸变 → 颗粒 → 暗角）与 shape-carry 转场模糊见 TemperaPost.kt：每个段落场景录进自己的 GraphicsLayer，
//     挂 RenderEffect（同 Folia 每个 scene 容器一条 filter 链），场景透明度在层上乘（Pixi 容器 alpha 在 filter 之后）。
//   · 未做：渐变色模式、用户图片层、墨染奥行（智能动感，要 htdemucs 鼓点）、片尾海报。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import com.guangyue.core.lyrics.Line
import com.guangyue.feature.player.lyrics.visualizer.tempera.IDLE_TP_TRANSITION
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpBlockOptions
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpCompositionContext
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpDecorLite
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpGlyphMotion
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpGroup
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpHatchSpec
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpMeasure
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpNode
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpNodeType
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpPalette
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpRgb
import com.guangyue.feature.player.lyrics.visualizer.tempera.TpTone
import com.guangyue.feature.player.lyrics.visualizer.tempera.buildDotGrid
import com.guangyue.feature.player.lyrics.visualizer.tempera.buildHatchLines
import com.guangyue.feature.player.lyrics.visualizer.tempera.drawTemperaComposition
import com.guangyue.feature.player.lyrics.visualizer.tempera.easeTemperaEnter
import com.guangyue.feature.player.lyrics.visualizer.tempera.easeTemperaInOut
import com.guangyue.feature.player.lyrics.visualizer.tempera.isTemperaLayoutSegment
import com.guangyue.feature.player.lyrics.visualizer.tempera.pivot
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveShotHandoff
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaBreathWeight
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaCameraBreath
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaCameraFrame
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaGlyphMotion
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaLayout
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaPalette
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaSceneTransitionFrame
import com.guangyue.feature.player.lyrics.visualizer.tempera.resolveTemperaShotExit
import com.guangyue.feature.player.lyrics.visualizer.tempera.tClamp01
import com.guangyue.feature.player.lyrics.visualizer.tempera.temperaBlockSchedule
import com.guangyue.feature.player.lyrics.visualizer.tempera.th01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ───────────────────────────── 仿射（Pixi 的 position · rotation · scale · -pivot） ─────────────────────────────

/** x' = a·x + c·y + e，y' = b·x + d·y + f。 */
internal class TpAffine(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
    operator fun times(o: TpAffine) = TpAffine(
        a * o.a + c * o.b, b * o.a + d * o.b,
        a * o.c + c * o.d, b * o.c + d * o.d,
        a * o.e + c * o.f + e, b * o.e + d * o.f + f,
    )
    fun toMatrix(): Matrix = Matrix().also { m ->
        val v = m.values
        v[Matrix.ScaleX] = a.toFloat(); v[Matrix.SkewY] = b.toFloat()
        v[Matrix.SkewX] = c.toFloat(); v[Matrix.ScaleY] = d.toFloat()
        v[Matrix.TranslateX] = e.toFloat(); v[Matrix.TranslateY] = f.toFloat()
    }
    companion object {
        val IDENTITY = TpAffine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        /** Pixi 容器的局部变换：translate(x, y) · rotate(r) · scale(sx, sy) · translate(-px, -py)。 */
        fun pixi(x: Double, y: Double, rotation: Double = 0.0, sx: Double = 1.0, sy: Double = 1.0, px: Double = 0.0, py: Double = 0.0): TpAffine {
            val cr = cos(rotation); val sr = sin(rotation)
            val a = cr * sx; val b = sr * sx; val c = -sr * sy; val d = cr * sy
            return TpAffine(a, b, c, d, x - (a * px + c * py), y - (b * px + d * py))
        }
    }
}

// ───────────────────────────── 场景（temperaSceneBuilder.ts） ─────────────────────────────

private class TpRenderNode(
    val node: TpNode,
    val options: TpBlockOptions,
    val group: TpGroup?,
    val path: Path,
    val strokeWidth: Float,
    val styleAlpha: Double,
    val pivotX: Double,
    val pivotY: Double,
    val driftPhase: Double,
    /** 只有实心填充参与反色判定。 */
    val solid: Boolean,
    val strokes: List<Pair<Path, Float>> = emptyList(),
)

private class TpGlyphView(val placement: com.guangyue.feature.player.lyrics.visualizer.tempera.TpGlyphPlacement, val motion: TpGlyphMotion, val layout: TextLayoutResult)
private class TpTextItem(val layout: TextLayoutResult, val x: Double, val y: Double, val rotation: Double, val alpha: Double)

private class TpShotView(
    val shot: TpShot,
    val nodes: List<TpRenderNode>,
    val glyphs: List<TpGlyphView>,
    val watermark: TpTextItem?,
    val fragments: List<TpTextItem>,
    val revealDoneTime: Double,
    val idHash: Long,
    val carry: Double,
) {
    var schedulePace = Double.NaN
    var delays = DoubleArray(0)
    var spans = DoubleArray(0)
}

private class TpSceneView(val index: Int, val shots: List<TpShotView>, val screentone: Path)

private fun polyPath(p: DoubleArray, close: Boolean = true): Path = Path().apply {
    if (p.size < 4) return@apply
    moveTo(p[0].toFloat(), p[1].toFloat())
    var i = 2
    while (i + 1 < p.size) { lineTo(p[i].toFloat(), p[i + 1].toFloat()); i += 2 }
    if (close) close()
}

private fun buildRenderNode(item: com.guangyue.feature.player.lyrics.visualizer.tempera.TpItem, seed: Long, index: Int): TpRenderNode {
    val n = item.node
    val nums = n.nums
    val (px, py) = n.pivot()
    val phase = th01(seed, index, 173) * PI * 2
    fun node(path: Path, width: Float, alpha: Double, solid: Boolean = false, strokes: List<Pair<Path, Float>> = emptyList()) =
        TpRenderNode(n, item.options, item.parent, path, width, alpha, px, py, phase, solid, strokes)
    return when (n.type) {
        TpNodeType.FILL -> node(polyPath(nums.copyOfRange(1, nums.size)), 0f, nums[0], solid = true)
        TpNodeType.HOLES -> {
            val path = polyPath(nums.copyOfRange(1, nums.size)).apply {
                fillType = PathFillType.EvenOdd
                n.holes.forEach { h -> if (h.size >= 6) addPath(polyPath(h)) }
            }
            node(path, 0f, nums[0], solid = true)
        }
        TpNodeType.OUTLINE -> node(polyPath(nums.copyOfRange(2, nums.size)), nums[0].toFloat(), nums[1])
        TpNodeType.HATCH -> {
            val spec = TpHatchSpec(nums[0], nums[1], nums[2])
            val lines = buildHatchLines(nums.copyOfRange(4, nums.size), spec)
            val path = Path().apply { lines.forEach { moveTo(it.x1.toFloat(), it.y1.toFloat()); lineTo(it.x2.toFloat(), it.y2.toFloat()) } }
            node(path, spec.width.toFloat(), nums[3])
        }
        TpNodeType.DISCS, TpNodeType.RINGS -> {
            val o = if (n.type == TpNodeType.DISCS) 1 else 2
            val circles = Path()
            var i = o
            while (i + 2 < nums.size) {
                val r = max(0.5, nums[i + 2])
                circles.addOval(Rect(Offset((nums[i]).toFloat(), nums[i + 1].toFloat()), r.toFloat()))
                i += 3
            }
            if (n.type == TpNodeType.DISCS) node(circles, 0f, nums[0], solid = true) else node(circles, nums[0].toFloat(), nums[1])
        }
        TpNodeType.LINES, TpNodeType.CROSSES -> {
            val path = Path()
            if (n.type == TpNodeType.LINES) {
                var i = 2
                while (i + 3 < nums.size) { path.moveTo(nums[i].toFloat(), nums[i + 1].toFloat()); path.lineTo(nums[i + 2].toFloat(), nums[i + 3].toFloat()); i += 4 }
            } else {
                var i = 2
                while (i + 3 < nums.size) {
                    val x = nums[i]; val y = nums[i + 1]; val size = nums[i + 2]; val rot = nums[i + 3]
                    val c = cos(rot + PI / 4) * size; val s = sin(rot + PI / 4) * size
                    path.moveTo((x - c).toFloat(), (y - s).toFloat()); path.lineTo((x + c).toFloat(), (y + s).toFloat())
                    path.moveTo((x - s).toFloat(), (y + c).toFloat()); path.lineTo((x + s).toFloat(), (y - c).toFloat())
                    i += 4
                }
            }
            node(path, nums[0].toFloat(), nums[1])
        }
        TpNodeType.POLYLINE -> node(polyPath(nums.copyOfRange(2, nums.size), close = false), nums[0].toFloat(), nums[1])
        TpNodeType.SQUARES -> {
            val path = Path()
            var i = 1
            while (i + 3 < nums.size) {
                val x = nums[i]; val y = nums[i + 1]; val size = nums[i + 2]
                path.addRect(Rect((x - size / 2).toFloat(), (y - size / 2).toFloat(), (x + size / 2).toFloat(), (y + size / 2).toFloat()))
                i += 4
            }
            node(path, 0f, nums[0])
        }
        TpNodeType.DIAMONDS -> {
            val cx = nums[0]; val cy = nums[1]; val rx = nums[2]; val ry = nums[3]; val rings = max(1, nums[4].toInt())
            val strokes = List(rings) { ring ->
                val k = 1 - ring * 0.22
                polyPath(doubleArrayOf(cx, cy - ry * k, cx + rx * k, cy, cx, cy + ry * k, cx - rx * k, cy)) to (if (ring % 2 == 0) 3.5f else 1.4f)
            }
            node(Path(), 0f, nums[5], strokes = strokes)
        }
    }
}

// ───────────────────────────── 舞台 ─────────────────────────────

@Composable
internal fun TemperaStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    paperColor: Color,
    inkColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    @Suppress("UNUSED_PARAMETER") topReserve: Dp,
    @Suppress("UNUSED_PARAMETER") bottomReserve: Dp,
    modifier: Modifier = Modifier,
    secondaryColor: Color = accentColor,
    /** Folia 的 programSeed（歌曲身份）：同一首歌每次编排一样。 */
    seed: String = "tempera",
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val program = remember(lines, seed) { compileTemperaCoreProgram(lines, seed) }
    val palette = remember(paperColor, inkColor, accentColor, secondaryColor) {
        resolveTemperaPalette(
            TpRgb.ofArgb(paperColor.toArgb()), TpRgb.ofArgb(inkColor.toArgb()),
            TpRgb.ofArgb(accentColor.toArgb()), TpRgb.ofArgb(secondaryColor.toArgb()),
        )
    }
    val style = remember(fontFamily) { TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold) }
    val scenes = remember(program, palette, lyricsFontScale, style) { HashMap<Long, TpSceneView>() }
    val widthCache = remember(style) { HashMap<String, Double>() }
    val layoutCache = remember(style) { HashMap<String, TextLayoutResult>() }
    val inversionPaint = remember { Paint() }
    // 场景层：槽 0 = 当前段，槽 1 = 预卷的下一段（Folia scene cache 里同时上屏的最多两个）。
    val sceneLayers = listOf(rememberGraphicsLayer(), rememberGraphicsLayer())
    val postSlots = remember { listOf(TemperaPostSlot(), TemperaPostSlot()) }

    Canvas(modifier.fillMaxSize()) {
        val dens = density.density.toDouble()
        val w = size.width / dens
        val h = size.height / dens
        if (w <= 0 || h <= 0) return@Canvas
        val t = clock.readForDraw()
        val fontScaleSys = density.fontScale

        fun layoutOf(text: String, fs: Double): TextLayoutResult = layoutCache.getOrPut("$fs|$text") {
            measurer.measure(text, style.copy(fontSize = (fs / fontScaleSys).sp, lineHeight = (fs * 1.2 / fontScaleSys).sp), softWrap = false, maxLines = 1)
        }
        val measure = TpMeasure { text, fs -> widthCache.getOrPut("$fs|$text") { layoutOf(text, fs).size.width / dens } }

        fun buildScene(index: Int): TpSceneView {
            val paragraph = program.paragraphs[index]
            val sceneSeed = sonnetHash("${program.seed}:${paragraph.id}")
            val carry = max(w, h) * 0.09
            val shots = paragraph.shots.mapIndexed { shotIndex, shot ->
                val shotSeed = sceneSeed + shotIndex * 97L
                val ctx = TpCompositionContext(
                    kind = shot.kind,
                    decor = TpDecorLite(shot.decor.motif, shot.decor.hatchAngle, shot.decor.crossCount, shot.decor.scribbleSeed),
                    width = w, height = h, seed = shotSeed, showDecor = true,
                    bleed = carry + max(w, h) * 0.08, flowAngle = shot.flowAngle, gradient = false,
                )
                drawTemperaComposition(ctx)
                val nodes = ctx.items.mapIndexed { i, item -> buildRenderNode(item, shotSeed, i) }
                val sliceSegments = shot.slices.map { slice ->
                    paragraph.lines.firstOrNull { it.sourceIndex == slice.lineIndex }?.segments
                        ?.subList(slice.segmentStart, slice.segmentEnd)?.filter { isTemperaLayoutSegment(it) } ?: emptyList()
                }.filter { it.isNotEmpty() }
                val maxGraphemes = max(3, sliceSegments.maxOfOrNull { segs -> segs.sumOf { it.graphemes.size } } ?: 0)
                val baseFontSize = max(34.0, min(150.0, (w / max(5.0, maxGraphemes * 1.05)) * 1.5)) * lyricsFontScale
                val placements = resolveTemperaLayout(sliceSegments, shot.kind, w, h, baseFontSize, shotSeed, measure)
                val glyphs = placements.filter { it.char.trim().isNotEmpty() }.map { TpGlyphView(it, it.motion(), layoutOf(it.char, it.fontSize)) }
                val wm = shot.decor.watermark?.takeIf { it.text.isNotBlank() }?.let {
                    TpTextItem(layoutOf(it.text, max(48.0, baseFontSize * it.scale)), it.x * w, it.y * h, it.rotation, 0.16)
                }
                val fragments = shot.decor.fragments.map { TpTextItem(layoutOf(it.char, max(12.0, baseFontSize * it.scale)), it.x * w, it.y * h, it.rotation, 0.42) }
                TpShotView(
                    shot, nodes, glyphs, wm, fragments,
                    revealDoneTime = if (glyphs.isNotEmpty()) glyphs.maxOf { it.motion.settleTime } else shot.startTime,
                    idHash = sonnetHash(shot.id), carry = carry,
                )
            }
            val toneSpacing = max(26.0, sqrt((w * h) / 6000))
            val screentone = Path().apply {
                buildDotGrid(w, h, toneSpacing, 1.6).forEach { m -> addRect(Rect((m.x - 0.8).toFloat(), (m.y - 0.8).toFloat(), (m.x + 0.8).toFloat(), (m.y + 0.8).toFloat())) }
            }
            return TpSceneView(index, shots, screentone)
        }

        val key = { i: Int -> (i.toLong() shl 40) or ((w * 8).toLong() shl 20) or (h * 8).toLong() }
        val paragraphIndex = findTemperaParagraphIndexAtTime(program, t)
        // 只留当前段 ±1 的场景（Folia scene cache ±1）。
        scenes.keys.retainAll { k -> val i = (k shr 40).toInt(); abs(i - paragraphIndex) <= 1 && (k and 0xFFFFFFFFFFL) == (key(i) and 0xFFFFFFFFFFL) }

        val paper = Color(palette.paper.argb())
        drawRect(paper)
        if (program.paragraphs.isEmpty()) return@Canvas

        val transitionsEnabled = !reduceMotion
        val outgoing = program.paragraphs[paragraphIndex].transitionOut
        val preRoll = transitionsEnabled && outgoing != null && outgoing.kind != "block-wipe" && t >= outgoing.startTime
        val camera = if (reduceMotion) 0.0 else 1.0
        val motion = if (reduceMotion) 0.0 else 1.0
        val root = TpAffine.pixi(0.0, 0.0, sx = dens, sy = dens)
        val paperL = palette.paper.luminance
        val inkL = palette.ink.luminance
        var wipe: Pair<Double, Double>? = null

        val indices = listOfNotNull(paragraphIndex, if (preRoll && paragraphIndex + 1 < program.paragraphs.size) paragraphIndex + 1 else null)
        for (index in indices) {
            val scene = scenes.getOrPut(key(index)) { buildScene(index) }
            val isIncoming = index == paragraphIndex + 1
            val tf = resolveTemperaSceneTransitionFrame(program, index, t, isIncoming, outgoing, transitionsEnabled)
            if (tf.wipe > 0.001 && tf.wipe < 1.999) wipe = tf.wipe to tf.wipeAngle
            val sceneAlpha = tf.alpha
            if (sceneAlpha <= 0.001) continue
            val sceneM = root * TpAffine.pixi(w / 2 + tf.x * w, h / 2 + tf.y * h, tf.rotation, tf.scale, tf.scale, w / 2, h / 2)
            val slot = if (isIncoming) 1 else 0
            val layer = sceneLayers[slot]
            val sceneSeed = sonnetHash("${program.seed}:${program.paragraphs[index].id}")
            layer.renderEffect = postSlots[slot].effect(
                size.width, size.height, temperaNoiseSeed(sceneSeed),
                if (transitionsEnabled) temperaTransitionBlurSigma(tf.blur, dens) else 0.0,
                post = !reduceMotion,
            )
            layer.alpha = sceneAlpha.toFloat()
            // 不能用 DrawScope 的成员扩展 record{}：它把外层 LayoutNodeDrawScope 本身当 Density 传进去，
            // 块内读 density 会在 CanvasDrawScope ↔ LayoutNodeDrawScope 间无限递归（CI 取证 StackOverflowError）。
            layer.record(Density(density.density, density.fontScale), layoutDirection, IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                // 纸色薄罩 + 底网点。
                withTransform({ transform(sceneM.toMatrix()) }) {
                    drawRect(paper, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()), alpha = 0.35f)
                    drawPath(scene.screentone, Color(palette.tone4.argb()), alpha = 0.05f)
                }
                // 「偏墨」区域（屏幕坐标），给文字反色用；按绘制顺序叠加。
                var dark = Path()
                var active = 0
                for (i in scene.shots.indices.reversed()) if (t >= scene.shots[i].shot.startTime) { active = i; break }
                scene.shots.forEachIndexed { shotIndex, view ->
                    val handing = shotIndex < active && resolveTemperaShotExit(view.shot, t) < 1
                    if (shotIndex != active && !handing) return@forEachIndexed
                    dark = drawShot(view, t, w, h, camera, motion, sceneM, 1.0, palette, paperL, inkL, dark, inversionPaint, reduceMotion)
                }
            }
            drawLayer(layer)
        }
        // API 32 及以下没有着色器链：退回径向渐变暗角（sonnetPrintFilters 的 vignette：smoothstep(0.52, 1.08, r) × 0.85 × 0.6 压黑，r 以屏高为单位）。
        if (!reduceMotion && !temperaSupportsPostShader()) {
            val r = 1.08f * size.height
            val stops = (0..8).map { k ->
                val u = 0.52 + (1.08 - 0.52) * k / 8.0
                val s = ((u - 0.52) / 0.56).coerceIn(0.0, 1.0)
                (u / 1.08).toFloat() to Color.Black.copy(alpha = (s * s * (3 - 2 * s) * 0.85 * 0.6).toFloat())
            }
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(*(listOf(0f to Color.Transparent) + stops).toTypedArray(), center = Offset(size.width / 2, size.height / 2), radius = r))
        }
        wipe?.let { (travel, angle) ->
            val span = hypot(w, h)
            val notch = span * 0.08
            val length = span + notch * 2
            val start = -span / 2 - notch
            val end = start + length
            val half = span / 2
            val offset = (travel - 1) * length
            val m = root * TpAffine.pixi(w / 2 + cos(angle) * offset, h / 2 + sin(angle) * offset, angle)
            withTransform({ transform(m.toMatrix()) }) {
                drawPath(polyPath(doubleArrayOf(start, -half, end, -half, end + notch, 0.0, end, half, start, half, start + notch, 0.0)), Color(palette.tone3.argb()))
            }
        }
    }
}

private fun DrawScope.drawShot(
    view: TpShotView,
    t: Double,
    w: Double,
    h: Double,
    camera: Double,
    motion: Double,
    sceneM: TpAffine,
    sceneAlpha: Double,
    palette: TpPalette,
    paperL: Double,
    inkL: Double,
    darkIn: Path,
    inversionPaint: Paint,
    reduceMotion: Boolean,
): Path {
    var dark = darkIn
    val shot = view.shot
    // ── temperaShotFrame.updateTemperaShot ──
    val duration = max(shot.endTime - shot.startTime, 0.001)
    val rawProgress = (t - shot.startTime) / duration
    val frame = resolveTemperaCameraFrame(shot, rawProgress)
    val breathWeight = resolveTemperaBreathWeight(t, view.revealDoneTime)
    if (breathWeight > 0) {
        val phase = (view.idHash % 1024) / 1024.0 * PI * 2
        val b = resolveTemperaCameraBreath(t, phase)
        frame.x += b.x * breathWeight; frame.y += b.y * breathWeight
        frame.scale += b.scale * breathWeight; frame.rotation += b.rotation * breathWeight
    }
    val handoff = resolveShotHandoff(shot)
    val span = max(w, h)
    val enter = easeTemperaEnter(tClamp01((t - shot.startTime) / (handoff * 0.8)))
    val exit = easeTemperaInOut(resolveTemperaShotExit(shot, t))
    val travel = if (reduceMotion) 0.0 else exit * span * 0.55 - (1 - enter) * span * 0.32
    val cameraX = frame.x * w * camera + cos(shot.flowAngle) * travel
    val cameraY = frame.y * h * camera + sin(shot.flowAngle) * travel
    val shotAlpha = (1 - exit) * sceneAlpha
    if (shotAlpha <= 0.001) return dark
    val scale = (1 + (frame.scale - 1) * camera) * (1 - exit * 0.08)
    val shotM = sceneM * TpAffine.pixi(w / 2 + cameraX, h / 2 + cameraY, frame.rotation * camera, scale, scale, w / 2, h / 2)

    // ── temperaBlocks.updateTime ──
    val paceDuration = max(shot.lyricEndTime - shot.startTime, 0.2)
    if (paceDuration != view.schedulePace) {
        view.schedulePace = paceDuration
        val (d, s) = temperaBlockSchedule(view.nodes.map { it.options }, paceDuration)
        view.delays = d; view.spans = s
    }
    val blockDuration = max(shot.endTime - shot.startTime, 0.2)
    val progress = tClamp01((t - shot.startTime) / blockDuration)
    val creep = easeTemperaInOut(progress) * view.carry * 0.35
    val flowX = cos(shot.flowAngle); val flowY = sin(shot.flowAngle)
    view.nodes.forEachIndexed { i, rn ->
        val e = if (reduceMotion) 1.0 else easeTemperaEnter((t - shot.startTime - view.delays[i]) / view.spans[i])
        if (e <= 0.001) return@forEachIndexed
        val itemAlpha = (rn.options.alpha ?: 1.0) * e
        val behind = if (reduceMotion) 0.0 else (1 - e) * view.carry - creep
        val px = rn.pivotX + (rn.options.enterDX ?: 0.0) * (1 - e) - flowX * behind
        val py = rn.pivotY + (rn.options.enterDY ?: 0.0) * (1 - e) - flowY * behind
        var sx = 1.0; var sy = 1.0; var rot = 0.0
        if (rn.options.drift || rn.options.grow) {
            val float = if (rn.options.drift) 1 + sin(t * 0.5 + rn.driftPhase) * 0.02 else 1.0
            sx = if (rn.options.grow) max(0.0001, e) * float else float
            sy = float
            if (rn.options.drift) rot = sin(t * 0.33 + rn.driftPhase) * 0.012
        }
        var m = shotM
        rn.group?.let { g -> m = m * TpAffine.pixi(g.x, g.y, g.rotation) }
        m = m * TpAffine.pixi(px, py, rot, sx, sy, rn.pivotX, rn.pivotY)
        val rgb = palette.of(rn.node.tone)
        val alpha = (rn.styleAlpha * itemAlpha * shotAlpha * palette.alphaOf(rn.node.tone)).toFloat()
        val color = Color(rgb.argb())
        val matrix = m.toMatrix()
        withTransform({ transform(matrix) }) {
            when {
                rn.strokes.isNotEmpty() -> rn.strokes.forEach { (p, sw) -> drawPath(p, color, alpha = alpha, style = Stroke(sw)) }
                rn.strokeWidth > 0f -> drawPath(rn.path, color, alpha = alpha, style = Stroke(rn.strokeWidth))
                else -> drawPath(rn.path, color, alpha = alpha, style = Fill)
            }
        }
        if (rn.solid) {
            val lum = paperL + (rgb.luminance - paperL) * alpha
            val isDark = abs(lum - inkL) < abs(lum - paperL)
            if (alpha >= 0.5 || isDark) {
                val screen = Path().apply { addPath(rn.path); transform(matrix) }
                if (rn.node.type == TpNodeType.HOLES) screen.fillType = PathFillType.EvenOdd
                val out = Path()
                if (out.op(dark, screen, if (isDark && alpha >= 0.5) PathOperation.Union else PathOperation.Difference)) dark = out
            }
        }
    }

    // ── 水印层（不反色）──
    view.watermark?.let { wm ->
        withTransform({ transform(shotM.toMatrix()) }) { drawTextItem(wm, Color(palette.tone4.argb()), wm.alpha * shotAlpha, 1.0, 1.0, density) }
    }

    // ── 文字层（阴影 + 碎字 + 字，整层反色）──
    val frames = view.glyphs.map { resolveTemperaGlyphMotion(it.motion, t, motion) }
    val ink = Color(palette.ink.argb())
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), inversionPaint)
        withTransform({ transform(shotM.toMatrix()) }) {
            view.fragments.forEach { drawTextItem(it, ink, it.alpha * shotAlpha, 1.0, 1.0, density) }
            view.glyphs.forEachIndexed { i, g ->
                val f = frames[i]
                if (!f.visible) return@forEachIndexed
                val p = g.placement
                val x = p.x + f.x; val y = p.y + f.y
                // 阴影（showDecor）：同字墨色，偏移 0.06 / 0.08 字号，透明度 0.34。
                drawGlyph(g.layout, ink, f.alpha * 0.34 * shotAlpha, x + p.fontSize * 0.06, y + p.fontSize * 0.08, f.rotation, f.scaleX, f.scaleY, density)
                drawGlyph(g.layout, ink, f.alpha * shotAlpha, x, y, f.rotation, f.scaleX, f.scaleY, density)
            }
        }
        drawPath(dark, Color(palette.paper.argb()), blendMode = BlendMode.SrcIn)
        canvas.restore()
    }

    // ── 浮影层（tone4，不反色）──
    if (!reduceMotion) {
        val tone4 = Color(palette.tone4.argb())
        withTransform({ transform(shotM.toMatrix()) }) {
            view.glyphs.forEachIndexed { i, g ->
                val f = frames[i]
                if (!f.visible || f.echoAlpha <= 0.004) return@forEachIndexed
                val p = g.placement
                for (k in 0 until 2) {
                    val depth = 1 + k * 0.85
                    drawGlyph(g.layout, tone4, f.echoAlpha / (k + 1.4) * shotAlpha, p.x + f.echoX * depth, p.y + f.echoY * depth, f.rotation, f.scaleX, f.scaleY, density)
                }
            }
        }
    }
    return dark
}

private fun DrawScope.drawGlyph(layout: TextLayoutResult, color: Color, alpha: Double, x: Double, y: Double, rotation: Double, sx: Double, sy: Double, dens: Float) {
    if (alpha <= 0.002) return
    withTransform({
        translate(x.toFloat(), y.toFloat())
        rotate((rotation * 180 / PI).toFloat(), Offset.Zero)
        scale((sx / dens).toFloat(), (sy / dens).toFloat(), Offset.Zero)
    }) {
        drawText(layout, color = color, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f), alpha = alpha.toFloat().coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawTextItem(item: TpTextItem, color: Color, alpha: Double, sx: Double, sy: Double, dens: Float) =
    drawGlyph(item.layout, color, alpha, item.x, item.y, item.rotation, sx, sy, dens)
