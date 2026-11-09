// 绘光（Folia lumiere）舞台：Compose Canvas 上逐层画 Folia 的场景（不用 WebView）。
// 对应 Folia 文件：src/components/visualizer/lumiere/
//   VisualizerLumiere.tsx + createLumierePixiRuntime.ts（按播放时间找当前段落单元、段落转场的进出帧）、
//   scene.ts（七层叠放：光场 → 背景碎片 → 星空 → 线稿 → 浮尘 → 文字组 → 前景；每帧只由 t 决定）、
//   lumiereDarkField.ts（暗场底）、overlay.ts（取景器四角、对位十字、顶边刻度）、
//   text/lyricWindow.ts 的绘制部分（光斑、光晕、字、闪点）、light/sprites.ts（光点 / 十字星纹理）。
// 编排与排版全在 LumiereCore.kt / LumiereText.kt（纯逻辑、有金标单测），光场着色器在 LumiereLight.kt。
// 单位：舞台按 density 整体缩放，内部一律用 dp（= Folia 的 CSS 像素），常数原样照搬。
//
// 偏离（第 1 阶段）：
//   1. 第 2 阶段的层先留接口不画：背景歌词碎片（lyricEcho）、星空点亮（starfall）、线稿（lineart）、浮尘与前景散景（motes）、
//      十字爆闪（crossBurst）、主题图标。见 LumierePhase2 里的空实现。
//   2. 文字组 bloom（strength 1.15、threshold 0.12）用每个已唱字底下一份 BlurMaskFilter 模糊字近似；图形组 bloom 在着色器里近似。
//   3. 段落转场：Folia 运行时在边界两侧各画一个单元并做交叉渐变 / 模糊；这里只画当前单元，套它的进出帧（alpha、scale），不做模糊。
//   4. 颗粒：Folia 的颗粒来自共享背景层的网点纹理，这里在暗场上铺一层 3 dp 间距的淡网点（LUMIERE_GRAIN_ALPHA）。
//   5. 译文由 LyricFlowScreen 的字幕层（LyricSubtitleOverlay）画，同 Folia 的 VisualizerSubtitleOverlay。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private const val LUMIERE_GRAIN_ALPHA = 0.05f

/** 第 2 阶段的层：接口先留着，舞台每帧按 scene.ts 的顺序调用。 */
internal object LumierePhase2 {
    /** text/lyricEcho.ts：唱到的词采集成巨大空心字，沿主光束漂下去（光场之上、星空之下）。 */
    @Suppress("UNUSED_PARAMETER") fun drawEcho(scope: DrawScope, unit: LumiereUnitModel, light: LumiereLightFrame, time: Double) = Unit
    /** light/starfall.ts：开场光点倾泻 + 星空 + 光雨。 */
    @Suppress("UNUSED_PARAMETER") fun drawStarfall(scope: DrawScope, unit: LumiereUnitModel, light: LumiereLightFrame, time: Double) = Unit
    /** lineart 目录：每个镜头提前 0.6 秒描线稿，镜头结束后 0.8 秒淡出（LumiereProfile.lineArt 给 spec）。 */
    @Suppress("UNUSED_PARAMETER") fun drawLineArt(scope: DrawScope, unit: LumiereUnitModel, light: LumiereLightFrame, time: Double) = Unit
    /** light/motes.ts：浮尘（领头光位的 motes）与前景散景（front）。 */
    @Suppress("UNUSED_PARAMETER") fun drawMotes(scope: DrawScope, unit: LumiereUnitModel, light: LumiereLightFrame, time: Double, front: Boolean) = Unit
    /** light/crossBurst.ts：十字爆闪（profile.burst），在字的下面。 */
    @Suppress("UNUSED_PARAMETER") fun drawBursts(scope: DrawScope, unit: LumiereUnitModel, window: LumiereLyricWindow, time: Double) = Unit
}

/** 一个段落单元在当前尺寸下的全部状态（换尺寸重建）。 */
private class LumiereUnitState(val model: LumiereUnitModel, val window: LumiereLyricWindow)

/** light/sprites.ts 的 dot：柔和的高斯光点（白色，按 tint 上色）。 */
private fun lumiereDotBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val stops = FloatArray(9) { it / 8f }
    val colors = IntArray(9) { i -> val r = stops[i]; val a = exp(-(r * r) * 4.5f) * (1 - r); android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), 255, 255, 255) }
    p.shader = RadialGradient(size / 2f, size / 2f, size / 2f, colors, stops, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    return bmp
}

/** light/sprites.ts 的 star：亮核 + 横竖两道细长光芒。 */
private fun lumiereStarBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val half = size / 2f
    for (y in 0 until size) for (x in 0 until size) {
        val dx = (x + 0.5f - half) / half; val dy = (y + 0.5f - half) / half
        val core = exp(-(dx * dx + dy * dy) * 40f)
        val rays = exp(-dy * dy * 900f) * (1 - kotlin.math.abs(dx)) + exp(-dx * dx * 900f) * (1 - kotlin.math.abs(dy))
        val a = min(1f, core + rays * 0.9f)
        if (a > 0.004f) { p.color = android.graphics.Color.argb((a * 255).toInt(), 255, 255, 255); c.drawPoint(x.toFloat(), y.toFloat(), p) }
    }
    return bmp
}

/** 颗粒：3 dp 一格的网点（按 density 换算成像素后平铺）。 */
private fun lumiereGrainBitmap(density: Float): Bitmap {
    val cell = max(2, (3 * density).toInt())
    val bmp = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    android.graphics.Canvas(bmp).drawCircle(cell / 2f, cell / 2f, max(0.6f, 0.35f * density), p)
    return bmp
}

@Composable
internal fun LumiereStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    @Suppress("UNUSED_PARAMETER") fontFamily: FontFamily,
    reduceMotion: Boolean,
    @Suppress("UNUSED_PARAMETER") topReserve: Dp,
    @Suppress("UNUSED_PARAMETER") bottomReserve: Dp,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color.White,
    secondaryColor: Color? = null,
    backgroundColor: Color = Color.Black,
    /** Folia 的 seed（歌曲身份）：同一首歌每次编排一样。 */
    seed: String = "lumiere",
    /** 轨迹过渡（Folia tuning.seamlessTransitions）。Folia 缺省开，但基准截图是按段落切单元的，这里缺省关，见 compileLumiereProgram。 */
    seamless: Boolean = false,
) {
    val program = remember(lines, seed, seamless) { compileLumiereProgram(lines, seed, seamless = seamless) }
    val density = LocalDensity.current.density
    val palette = remember(accentColor, primaryColor, secondaryColor) {
        resolveLumierePalette(lumiereRgbOfArgb(accentColor.toArgb()), lumiereRgbOfArgb(primaryColor.toArgb()), secondaryColor?.let { lumiereRgbOfArgb(it.toArgb()) }, 0.3)
    }
    val darkField = remember(backgroundColor) { resolveLumiereDarkField(lumiereRgbOfArgb(backgroundColor.toArgb()), 0.75) }
    val typeface = remember { if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 500, false) else Typeface.DEFAULT }
    val textPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER } }
    val glowPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER } }
    val measurePaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val spritePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val dot = remember { lumiereDotBitmap() }
    val star = remember { lumiereStarBitmap() }
    val grainPaint = remember(density) {
        Paint().apply { shader = BitmapShader(lumiereGrainBitmap(density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); alpha = (LUMIERE_GRAIN_ALPHA * 255).toInt() }
    }
    val shader = remember { if (lumiereSupportsShader()) RuntimeShader(LUMIERE_LIGHT_AGSL) else null }
    val blurs = remember { HashMap<Int, BlurMaskFilter>() }
    val measure = remember(measurePaint) { LumiereTextMeasure { text, px -> measurePaint.textSize = px.toFloat(); measurePaint.measureText(text).toDouble() } }
    val motion = if (reduceMotion) 0.6 else 1.0
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wDp = constraints.maxWidth / density
        val hDp = constraints.maxHeight / density
        val units = remember(program, wDp, hDp, lyricsFontScale, motion) { HashMap<Int, LumiereUnitState>() }
        fun unitAt(index: Int): LumiereUnitState = units.getOrPut(index) {
            val model = LumiereUnitModel(program.paragraphs[index], program.seed, wDp.toDouble(), hDp.toDouble(), motion)
            val lead = model.lead
            val heroPx = lead.heroSize * hDp * lyricsFontScale.coerceIn(0.6, 1.6)
            LumiereUnitState(model, LumiereLyricWindow(wDp.toDouble(), hDp.toDouble(), model.paragraph.lines, measure, model.textRegion, heroPx, 2, lead.typography, model.typographyOfLine, lead.decay, model.seed))
        }
        Canvas(Modifier.fillMaxSize()) {
            val time = clock.readForDraw()
            if (wDp <= 0f || hDp <= 0f || program.paragraphs.isEmpty()) return@Canvas
            // 暗场底（在所有场景之下，不随转场变化）+ 颗粒。
            drawRect(backgroundColor)
            val dfc = darkField.first
            drawRect(Color(dfc[0].toFloat(), dfc[1].toFloat(), dfc[2].toFloat()), alpha = darkField.second.toFloat())
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, grainPaint) }
            val index = findLumiereParagraphIndexAtTime(program, time)
            val state = unitAt(index)
            // 段落转场（lumiereTransitions.ts）：出场窗口里套 exit 帧；上一段转场之后 enterDuration 秒里套 enter 帧。
            var alpha = 1.0
            var scale = 1.0
            program.paragraphs[index].transitionOut?.let { out ->
                if (time >= out.startTime) {
                    val f = lumiereTransitionFrame(out.kind, true, (time - out.startTime) / max(out.endTime - out.startTime, 1e-3))
                    if (out.kind != "lights-out") { alpha *= f.alpha; scale *= f.scale }
                }
            }
            program.paragraphs.getOrNull(index - 1)?.transitionOut?.let { prev ->
                val enter = lumiereEnterDuration(prev.kind, prev.startTime, prev.endTime)
                if (time < prev.endTime + enter) {
                    val f = lumiereTransitionFrame(prev.kind, false, (time - prev.endTime) / enter)
                    alpha *= f.alpha; scale *= f.scale
                }
            }
            withTransform({ scale(density, density, Offset.Zero) }) {
                drawUnit(state, time, palette, alpha, scale, shader, textPaint, glowPaint, spritePaint, dot, star, blurs)
                drawLumiereOverlay(wDp.toDouble(), hDp.toDouble(), palette.light)
            }
        }
    }
}

private fun DrawScope.drawUnit(
    state: LumiereUnitState, time: Double, palette: LumierePalette, alpha: Double, transitionScale: Double, shader: RuntimeShader?,
    textPaint: Paint, glowPaint: Paint, spritePaint: Paint, dot: Bitmap, star: Bitmap, blurs: HashMap<Int, BlurMaskFilter>,
) {
    val model = state.model
    val w = model.width.toFloat(); val h = model.height.toFloat()
    val light = model.light(time, palette)
    val cam = model.camera(time)
    val layerAlpha = alpha.toFloat().coerceIn(0f, 1f)
    if (layerAlpha <= 0.003f) return
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val saved = if (layerAlpha < 0.999f) nc.saveLayerAlpha(null, (layerAlpha * 255).toInt()) else nc.save()
        // 转场缩放绕画面中心；运镜：先减 pivot，再缩放、旋转，最后移到 position（与 Pixi 容器同义）。
        nc.translate(w / 2, h / 2); nc.scale(transitionScale.toFloat(), transitionScale.toFloat()); nc.translate(-w / 2, -h / 2)
        nc.translate(cam.x.toFloat(), cam.y.toFloat())
        nc.rotate((cam.rotation * 180 / PI).toFloat())
        nc.scale(cam.scale.toFloat(), cam.scale.toFloat())
        nc.translate(-cam.pivotX.toFloat(), -cam.pivotY.toFloat())
        val input = LumiereFieldInput(light, time, model.width, model.height, palette.light, 1.0, 5.0, model.lead.region, 0.22f)
        val overscan = ceil(max(model.width, model.height) * 0.02).toFloat()
        if (shader != null && Build.VERSION.SDK_INT >= 33) {
            shader.setLumiereField(input)
            val fieldPaint = Paint()
            fieldPaint.shader = shader
            nc.drawRect(-overscan, -overscan, w + overscan, h + overscan, fieldPaint)
        }
        nc.restoreToCount(saved)
    }
    if (shader == null) withCameraTransform(model, cam, transitionScale) { drawLumiereFallbackLight(input = LumiereFieldInput(light, time, model.width, model.height, palette.light, 1.0, 5.0, model.lead.region, 0f)) }
    withCameraTransform(model, cam, transitionScale) {
        LumierePhase2.drawEcho(this, model, light, time)
        LumierePhase2.drawStarfall(this, model, light, time)
        LumierePhase2.drawLineArt(this, model, light, time)
        LumierePhase2.drawMotes(this, model, light, time, front = false)
        LumierePhase2.drawBursts(this, model, state.window, time)
        val textIntensity = lumiereSmooth((time - model.paragraph.startTime) / 0.6) * light.exit
        val frame = state.window.frame(time, light.beams, palette.lit, palette.unlit, 0.22, textIntensity * layerAlpha)
        drawIntoCanvas { canvas -> drawWindow(canvas.nativeCanvas, frame, textPaint, glowPaint, spritePaint, dot, star, blurs) }
        LumierePhase2.drawMotes(this, model, light, time, front = true)
    }
}

private inline fun DrawScope.withCameraTransform(model: LumiereUnitModel, cam: LumiereTransform, transitionScale: Double, block: DrawScope.() -> Unit) {
    val w = model.width.toFloat(); val h = model.height.toFloat()
    withTransform({
        scale(transitionScale.toFloat(), transitionScale.toFloat(), Offset(w / 2, h / 2))
        translate(cam.x.toFloat() - cam.pivotX.toFloat(), cam.y.toFloat() - cam.pivotY.toFloat())
        rotate((cam.rotation * 180 / PI).toFloat(), Offset(cam.pivotX.toFloat(), cam.pivotY.toFloat()))
        scale(cam.scale.toFloat(), cam.scale.toFloat(), Offset(cam.pivotX.toFloat(), cam.pivotY.toFloat()))
    }, block)
}

private fun argbOf(rgb: Int, alpha: Double) = (((alpha.coerceIn(0.0, 1.0) * 255).toInt()) shl 24) or (rgb and 0xffffff)

/** lyricWindow.ts 的绘制：光斑 → 光晕 → 字（底下垫一份模糊字近似文字组 bloom）→ 闪点。 */
private fun drawWindow(nc: android.graphics.Canvas, frame: LumiereWindowFrame, textPaint: Paint, glowPaint: Paint, spritePaint: Paint, dot: Bitmap, star: Bitmap, blurs: HashMap<Int, BlurMaskFilter>) {
    val rect = RectF()
    for (spot in frame.spots) {
        spritePaint.colorFilter = PorterDuffColorFilter(argbOf(spot.color, 1.0), PorterDuff.Mode.MULTIPLY)
        spritePaint.alpha = (spot.alpha.coerceIn(0.0, 1.0) * 255).toInt()
        nc.save(); nc.rotate((spot.rotation * 180 / PI).toFloat(), spot.x.toFloat(), spot.y.toFloat())
        rect.set((spot.x - spot.width / 2).toFloat(), (spot.y - spot.height / 2).toFloat(), (spot.x + spot.width / 2).toFloat(), (spot.y + spot.height / 2).toFloat())
        nc.drawBitmap(dot, null, rect, spritePaint); nc.restore()
    }
    for (g in frame.glyphs) {
        if (g.haloAlpha <= 0) continue
        spritePaint.colorFilter = PorterDuffColorFilter(argbOf(g.haloColor, 1.0), PorterDuff.Mode.MULTIPLY)
        spritePaint.alpha = (g.haloAlpha.coerceIn(0.0, 1.0) * 255).toInt()
        rect.set((g.x - g.haloSize / 2).toFloat(), (g.y - g.haloSize * 0.45).toFloat(), (g.x + g.haloSize / 2).toFloat(), (g.y + g.haloSize * 0.45).toFloat())
        nc.drawBitmap(dot, null, rect, spritePaint)
    }
    for (g in frame.glyphs) {
        if (g.alpha <= 0.003) continue
        val px = g.fontPx.toFloat()
        textPaint.textSize = px
        val fm = textPaint.fontMetrics
        val baseline = (g.y - (fm.ascent + fm.descent) / 2).toFloat()
        nc.save()
        nc.rotate((g.rotation * 180 / PI).toFloat(), g.x.toFloat(), g.y.toFloat())
        if (g.lit > 0.05) {
            val radius = max(1, (px * 0.22f).toInt())
            glowPaint.maskFilter = blurs.getOrPut(radius) { BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL) }
            glowPaint.textSize = px
            glowPaint.color = argbOf(g.color, g.alpha * g.lit * 0.85)
            nc.drawText(g.char, g.x.toFloat(), baseline, glowPaint)
        }
        textPaint.color = argbOf(g.color, g.alpha)
        nc.drawText(g.char, g.x.toFloat(), baseline, textPaint)
        nc.restore()
    }
    for (g in frame.glyphs) {
        if (g.starAlpha <= 0) continue
        spritePaint.colorFilter = PorterDuffColorFilter(argbOf(g.starColor, 1.0), PorterDuff.Mode.MULTIPLY)
        spritePaint.alpha = (g.starAlpha.coerceIn(0.0, 1.0) * 255).toInt()
        nc.save(); nc.rotate((g.starRotation * 180 / PI).toFloat(), g.starX.toFloat(), g.starY.toFloat())
        rect.set((g.starX - g.starSize / 2).toFloat(), (g.starY - g.starSize / 2).toFloat(), (g.starX + g.starSize / 2).toFloat(), (g.starY + g.starSize / 2).toFloat())
        nc.drawBitmap(star, null, rect, spritePaint); nc.restore()
    }
    spritePaint.colorFilter = null
}

/** overlay.ts：取景器四角、两侧对位十字、顶边两组刻度。坐标为舞台 dp。 */
private fun DrawScope.drawLumiereOverlay(width: Double, height: Double, light: DoubleArray) {
    val color = Color(light[0].toFloat(), light[1].toFloat(), light[2].toFloat())
    val unit = min(width, height)
    val (padX, padY) = lumiereFrameInsets(width, height)
    val arm = (unit * 0.045).toFloat()
    val line = max(1.2, unit / 600).toFloat()
    val alpha = 0.5f
    val px = padX.toFloat(); val py = padY.toFloat(); val w = width.toFloat(); val h = height.toFloat()
    for ((x, y, sx, sy) in listOf(listOf(px, py, 1f, 1f), listOf(w - px, py, -1f, 1f), listOf(w - px, h - py, -1f, -1f), listOf(px, h - py, 1f, -1f))) {
        drawLine(color, Offset(x, y + sy * arm), Offset(x, y), line, alpha = alpha)
        drawLine(color, Offset(x, y), Offset(x + sx * arm, y), line, alpha = alpha)
    }
    val mark = (unit * 0.012).toFloat()
    for (x in listOf(px, w - px)) {
        val y = h / 2
        drawCircle(color, mark * 0.6f, Offset(x, y), alpha = alpha * 0.8f, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
        drawLine(color, Offset(x - mark, y), Offset(x + mark, y), line, alpha = alpha * 0.8f)
        drawLine(color, Offset(x, y - mark), Offset(x, y + mark), line, alpha = alpha * 0.8f)
    }
    for (side in listOf(-1f, 1f)) for (k in 0 until 4) {
        val x = w / 2 + side * (unit * 0.1 + k * unit * 0.018).toFloat()
        drawLine(color, Offset(x, py), Offset(x + side * (unit * 0.009).toFloat(), py), line, alpha = alpha * 0.55f)
    }
}

