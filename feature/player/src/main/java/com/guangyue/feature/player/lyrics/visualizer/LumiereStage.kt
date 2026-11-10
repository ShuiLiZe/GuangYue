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
//   1. 第 2 阶段的层先留接口不画（lyricEcho 已在 LumiereEcho.kt、线稿在 LumiereLineart.kt、浮尘与前景散景在 LumiereMotes.kt 接上）：星空点亮（starfall）、
//      十字爆闪（crossBurst）。见 LumierePhase2 里的空实现。主题图标（themeIcons）不做：广乐主题没有 lyricsIcons，Folia 此时也不画。
//   2. 文字组 bloom（strength 1.15、threshold 0.12）用每个已唱字底下一份 BlurMaskFilter 模糊字近似；图形组 bloom 在着色器里近似。
//   3. 段落转场：照 lumiereSceneFrames.ts 在进入窗口里画两层交叉渐变（alpha、scale）；帧里的 blur（闪白 7、拉焦 12 逻辑像素）
//      还没画——Canvas 上整层模糊要 RenderNode + RenderEffect（API 31+），软件 GPU 上太贵，先略。reduceMotion 时硬切（同 staticMode）。
//      两层各用一份 LumiereFogField / LumiereFieldCache。
//   4. 颗粒：Folia 的颗粒来自共享背景层的网点纹理，这里在暗场上铺一层 3 dp 间距的淡网点（LUMIERE_GRAIN_ALPHA）。
//   5. 光场在 2 dp 一像素的离屏合成层里算、最多 20 帧 / 秒（LumiereFieldCache），再放大合成。
//   7. 光色（第 2 阶段第 1 项）：Folia 的 theme.accentColor 是主题里给的强调色，广乐的 theme.emphasis 是封面取出来的饱和色
//      （常偏红），直接当 accentColor 用会把香槟金光染红。缺省 followThemeAccent=false：强调色按 Folia rgbOf 的退路取香槟金，
//      再走原版 resolveLumierePalette（themeColorMix 0.3 不变），光色 = 香槟金、点亮的字 = 主色（白）偏暖。
//   6. 译文由 LyricFlowScreen 的字幕层（LyricSubtitleOverlay）画，同 Folia 的 VisualizerSubtitleOverlay；颜色用 LumiereSubtitleColor
//      （Folia 缺省主题的 secondaryColor 冷灰），不用广乐封面取出的 theme.secondary（取证页偏红）。
//   8. 暗场底：Folia 的暗场 = 主题背景 × 0.06，底下压着 Folia 的共享背景层（中性灰）；广乐的 theme.background 是封面取色（常偏棕），
//      缺省 neutralDarkField=true 取同亮度中性灰。
//   9. 点亮的字：Folia 文字组 bloom（strength 1.15）会把亮字冲到近白、四周有一圈明显辉光。这里字身按受光向白推 55%，
//      再叠宽（光点贴图，1.7 倍字号）、窄（0.2 字号模糊字）两层近似。
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
    /** light/starfall.ts：开场光点倾泻 + 星空 + 光雨。 */
    @Suppress("UNUSED_PARAMETER") fun drawStarfall(scope: DrawScope, unit: LumiereUnitModel, light: LumiereLightFrame, time: Double) = Unit
    /** light/crossBurst.ts：十字爆闪（profile.burst），在字的下面。 */
    @Suppress("UNUSED_PARAMETER") fun drawBursts(scope: DrawScope, unit: LumiereUnitModel, window: LumiereLyricWindow, time: Double) = Unit
}

/**
 * 光场缓存：着色器画进一个 2 dp 一像素的离屏合成层（RenderNode + setUseCompositingLayer，API 29+），每秒最多重录 20 次、
 * 时间不变就不重录，合成时双线性放大贴到舞台上。逐设备像素跑全屏着色器在模拟器的软件 GPU 上把 RenderThread 拖死
 * （取证时 ANR）；软件位图上不能用 RuntimeShader（会崩），所以用硬件离屏层。光场本身是柔的（最细的光路光线半宽约 2 dp），
 * 放大的损失看不出来，代价是光场最多 20 帧 / 秒。画布不是硬件加速时直接整幅画。
 */
internal class LumiereFieldCache {
    private val cell = 2f
    private var node: android.graphics.RenderNode? = null
    private var cols = 0
    private var rows = 0
    private var lastTime = Double.NaN
    private val paint = Paint()

    fun draw(target: android.graphics.Canvas, shader: RuntimeShader, time: Double, w: Float, h: Float, overscan: Float) {
        paint.shader = shader
        if (Build.VERSION.SDK_INT < 29 || !target.isHardwareAccelerated) {
            target.drawRect(-overscan, -overscan, w + overscan, h + overscan, paint)
            return
        }
        val c = ceil((w + overscan * 2) / cell).toInt()
        val r = ceil((h + overscan * 2) / cell).toInt()
        var n = node
        var dirty = false
        if (n == null || c != cols || r != rows) {
            n = n ?: android.graphics.RenderNode("lumiere-light").also { node = it }
            n.setPosition(0, 0, c, r)
            n.setUseCompositingLayer(true, Paint(Paint.FILTER_BITMAP_FLAG))
            cols = c; rows = r; dirty = true
        }
        if (dirty || kotlin.math.abs(time - lastTime) >= 1.0 / 20) {
            val rc = n.beginRecording(c, r)
            rc.scale(1 / cell, 1 / cell)
            rc.translate(overscan, overscan)
            rc.drawRect(-overscan, -overscan, w + overscan, h + overscan, paint)
            n.endRecording()
            lastTime = time
        }
        val saved = target.save()
        target.translate(-overscan, -overscan)
        target.scale(cell, cell)
        target.drawRenderNode(n)
        target.restoreToCount(saved)
    }
}

/** 一个段落单元在当前尺寸下的全部状态（换尺寸重建）。 */
private class LumiereUnitState(val model: LumiereUnitModel, val window: LumiereLyricWindow, val echo: LumiereLyricEcho) {
    /** 每个镜头一组线稿（scene.ts：种子 `${单元种子}:${镜头序号}:${kind}:art`）。 */
    val lineArts: List<LumiereLineArtLayer> = model.shots.mapIndexed { i, shot ->
        LumiereLineArtLayer(shot.profile.lineArt(model.aspect, lumiereRng("${model.seed}:$i:${shot.profile.kind}:art")), model.height)
    }
    /** 浮尘（领头光位的 motes，moteAmount 1）与前景散景（front，没有就不画）：scene.ts 235、339 行。 */
    val motes = LumiereMotes(model.width, model.height, "${model.seed}:motes", model.lead.motes)
    val front: LumiereMotes? = model.lead.front?.let { LumiereMotes(model.width, model.height, "${model.seed}:front", it) }
}

/** light/sprites.ts 的 drawDot(128)：径向渐变 1 → 0.72（0.18）→ 0.2（0.45）→ 0。浮尘用。 */
private fun lumiereFoliaDotBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val w = android.graphics.Color.WHITE
    fun a(v: Float) = android.graphics.Color.argb((v * 255).toInt(), 255, 255, 255)
    p.shader = RadialGradient(64f, 64f, 64f, intArrayOf(w, a(0.72f), a(0.2f), a(0f)), floatArrayOf(0f, 0.18f, 0.45f, 1f), Shader.TileMode.CLAMP)
    android.graphics.Canvas(bmp).drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    return bmp
}

/** light/sprites.ts 的 drawBokeh(128)：半透明圆盘、边缘一圈亮环。前景散景用。 */
private fun lumiereBokehBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun a(v: Float) = android.graphics.Color.argb((v * 255).toInt(), 255, 255, 255)
    p.shader = RadialGradient(64f, 64f, 64f * 0.96f, intArrayOf(a(0.35f), a(0.42f), a(0.75f), a(0f)), floatArrayOf(0f, 0.78f, 0.9f, 1f), Shader.TileMode.CLAMP)
    android.graphics.Canvas(bmp).drawCircle(64f, 64f, 64f * 0.96f, p)
    return bmp
}

/** motes.ts 的绘制：每颗一张贴图，中心在 (x, y)，直径 size，alpha，tint = 光色。 */
private fun drawLumiereMotes(nc: android.graphics.Canvas, motes: LumiereMotes, texture: Bitmap, paint: Paint, color: Int, layerAlpha: Float) {
    paint.colorFilter = PorterDuffColorFilter((0xff shl 24) or (color and 0xffffff), PorterDuff.Mode.MULTIPLY)
    val rect = RectF()
    for (s in motes.sprites) {
        if (!s.visible) continue
        val a = (s.alpha * layerAlpha).coerceIn(0.0, 1.0)
        if (a <= 0.004) continue
        val r = (s.size / 2).toFloat()
        rect.set(s.x.toFloat() - r, s.y.toFloat() - r, s.x.toFloat() + r, s.y.toFloat() + r)
        paint.alpha = (a * 255).toInt()
        nc.drawBitmap(texture, null, rect, paint)
    }
    paint.colorFilter = null
}

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
    /** 光色是否跟随广乐强调色（见文件头偏离 7）。缺省关 = Folia 默认的香槟金。 */
    followThemeAccent: Boolean = false,
    /** 暗场是否取中性灰（见文件头偏离 8）。缺省开。 */
    neutralDarkField: Boolean = true,
) {
    val program = remember(lines, seed, seamless) { compileLumiereProgram(lines, seed, seamless = seamless) }
    val density = LocalDensity.current.density
    val palette = remember(accentColor, primaryColor, secondaryColor, followThemeAccent) {
        resolveLumierePalette(if (followThemeAccent) lumiereRgbOfArgb(accentColor.toArgb()) else null, lumiereRgbOfArgb(primaryColor.toArgb()), secondaryColor?.let { lumiereRgbOfArgb(it.toArgb()) }, 0.3)
    }
    // 暗场底：缺省按主题背景的亮度取中性灰（见文件头偏离 8），不跟封面偏棕。
    val fieldBackground = remember(backgroundColor, neutralDarkField) { if (neutralDarkField) lumiereNeutralOf(backgroundColor) else backgroundColor }
    val darkField = remember(fieldBackground) { resolveLumiereDarkField(lumiereRgbOfArgb(fieldBackground.toArgb()), 0.75) }
    val typeface = remember { if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 500, false) else Typeface.DEFAULT }
    val textPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER } }
    val glowPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER } }
    val measurePaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val spritePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val echoFill = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val echoStroke = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val lineStroke = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val dot = remember { lumiereDotBitmap() }
    val star = remember { lumiereStarBitmap() }
    val moteDot = remember { lumiereFoliaDotBitmap() }
    val bokeh = remember { lumiereBokehBitmap() }
    val grainPaint = remember(density) {
        Paint().apply { shader = BitmapShader(lumiereGrainBitmap(density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); alpha = (LUMIERE_GRAIN_ALPHA * 255).toInt() }
    }
    val shader = remember { if (lumiereSupportsShader()) RuntimeShader(LUMIERE_LIGHT_AGSL) else null }
    val blurs = remember { HashMap<Int, BlurMaskFilter>() }
    // 进入窗口里两层同时画，各用一份烟雾网格与光场缓存（按段落序号奇偶分配，相邻两段不会撞同一份）。
    val fogFields = remember { List(2) { LumiereFogField() } }
    val fieldCaches = remember { List(2) { LumiereFieldCache() } }
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
            LumiereUnitState(model, LumiereLyricWindow(wDp.toDouble(), hDp.toDouble(), model.paragraph.lines, measure, model.textRegion, heroPx, 2, lead.typography, model.typographyOfLine, lead.decay, model.seed),
                LumiereLyricEcho(wDp.toDouble(), hDp.toDouble(), model.paragraph.lines, model.seed, lead.echoSize ?: 0.34))
        }
        Canvas(Modifier.fillMaxSize()) {
            val time = clock.readForDraw()
            if (wDp <= 0f || hDp <= 0f || program.paragraphs.isEmpty()) return@Canvas
            // 暗场底（在所有场景之下，不随转场变化）+ 颗粒。
            drawRect(fieldBackground)
            val dfc = darkField.first
            drawRect(Color(dfc[0].toFloat(), dfc[1].toFloat(), dfc[2].toFloat()), alpha = darkField.second.toFloat())
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, grainPaint) }
            // 段落转场（lumiereSceneFrames.ts + lumiereTransitions.ts）：出场窗口里当前段套 exit 帧；边界之后的进入窗口里
            // 新段套 enter 帧，同时与停在出场终点帧的上一段交叉渐变（两层，上一段在下）。
            val layers = resolveLumiereSceneFrames(program, time, !reduceMotion)
            withTransform({ scale(density, density, Offset.Zero) }) {
                for (layer in layers) {
                    val k = layer.index and 1
                    drawUnit(unitAt(layer.index), time, palette, layer.alpha, layer.scale, shader, fogFields[k], fieldCaches[k], textPaint, glowPaint, spritePaint, dot, star, blurs, echoFill, echoStroke, measure, lineStroke, moteDot, bokeh)
                }
                drawLumiereOverlay(wDp.toDouble(), hDp.toDouble(), palette.light)
            }
        }
    }
}

private fun DrawScope.drawUnit(
    state: LumiereUnitState, time: Double, palette: LumierePalette, alpha: Double, transitionScale: Double, shader: RuntimeShader?, fogField: LumiereFogField, fieldCache: LumiereFieldCache,
    textPaint: Paint, glowPaint: Paint, spritePaint: Paint, dot: Bitmap, star: Bitmap, blurs: HashMap<Int, BlurMaskFilter>,
    echoFill: Paint, echoStroke: Paint, measure: LumiereTextMeasure, lineStroke: Paint, moteDot: Bitmap, bokeh: Bitmap,
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
            fogField.update(time, light.rig.fog, 5, model.width, model.height, overscan.toDouble())
            shader.setLumiereField(input, fogField)
            fieldCache.draw(nc, shader, time, model.width.toFloat(), model.height.toFloat(), overscan)
        }
        nc.restoreToCount(saved)
    }
    if (shader == null) withCameraTransform(model, cam, transitionScale) { drawLumiereFallbackLight(input = LumiereFieldInput(light, time, model.width, model.height, palette.light, 1.0, 5.0, model.lead.region, 0f)) }
    withCameraTransform(model, cam, transitionScale) {
        // 背景歌词碎片（lyricEcho）：光场之上、星空与线稿之下；intensity = smooth(local / 1.2) × exit（scene.ts）。
        val echoIntensity = lumiereSmooth((time - model.paragraph.startTime) / 1.2) * light.exit * layerAlpha
        drawIntoCanvas { canvas -> state.echo.draw(canvas.nativeCanvas, time, light.beams, lumiereHexOf(palette.light), echoIntensity, echoFill, echoStroke, measure) }
        LumierePhase2.drawStarfall(this, model, light, time)
        // 线稿（lineart/）：每个镜头提前 0.6 秒开始描、3.6 秒描完，镜头结束后 0.8 秒淡出；受光的线更亮（scene.ts 458–482 行）。
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val lightHex = lumiereHexOf(palette.light)
            state.lineArts.forEachIndexed { i, layer ->
                val shot = model.shots[i]
                val (draw, fade) = lumiereLineArtTiming(i, model.shots.size, shot.startTime, shot.endTime, model.paragraph.startTime, model.ignition, time, light.exit, shot.profile.artGain)
                if (fade * layerAlpha > 0.003) layer.draw(nc, time, draw, fade * layerAlpha, light.beams, lightHex, lineStroke, spritePaint, star)
            }
        }
        // 浮尘：进了光束才亮（scene.ts 485 行；没有音频，高频加成为 0）。
        state.motes.update(time, light.beams, light.exit)
        drawIntoCanvas { canvas -> drawLumiereMotes(canvas.nativeCanvas, state.motes, moteDot, spritePaint, lumiereHexOf(palette.light), layerAlpha) }
        LumierePhase2.drawBursts(this, model, state.window, time)
        val textIntensity = lumiereSmooth((time - model.paragraph.startTime) / 0.6) * light.exit
        val frame = state.window.frame(time, light.beams, palette.lit, palette.unlit, 0.22, textIntensity * layerAlpha)
        drawIntoCanvas { canvas -> drawWindow(canvas.nativeCanvas, frame, textPaint, glowPaint, spritePaint, dot, star, blurs) }
        // 前景散景（front 容器，在文字之上）。
        state.front?.let { front ->
            front.update(time, light.beams, light.exit)
            drawIntoCanvas { canvas -> drawLumiereMotes(canvas.nativeCanvas, front, bokeh, spritePaint, lumiereHexOf(palette.light), layerAlpha) }
        }
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

/** 0xRRGGBB 向白色混 k（0..1）。 */
private fun lumiereTowardWhite(rgb: Int, k: Double): Int {
    fun ch(shift: Int): Int { val c = (rgb shr shift) and 0xff; return (c + (255 - c) * k).toInt().coerceIn(0, 255) shl shift }
    return ch(16) or ch(8) or ch(0)
}

/** 同亮度的中性灰（Rec.709 亮度，保留 alpha）：绘光暗场底、译文用，不跟封面取出的色相走。 */
internal fun lumiereNeutralOf(color: Color): Color {
    val y = (0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue).coerceIn(0f, 1f)
    return Color(y, y, y, color.alpha)
}

/** 译文字幕在绘光下的颜色：Folia 缺省主题的 secondaryColor（#9CA3AF，冷灰），不跟广乐封面取出的强调色偏红。 */
internal val LumiereSubtitleColor = Color(0xFF9CA3AF)

private fun argbOf(rgb: Int, alpha: Double) = (((alpha.coerceIn(0.0, 1.0) * 255).toInt()) shl 24) or (rgb and 0xffffff)

private val glowRect = RectF()

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
        // 文字组 bloom 近似（偏离 2、9）：宽（光点贴图）、窄（模糊字）两层叠加，点亮的字身向白色推（bloom 叠加在亮处会冲到近白）。
        val core = if (g.lit > 0.05) lumiereTowardWhite(g.color, 0.55 * min(1.0, g.lit)) else g.color
        if (g.lit > 0.05) {
            glowPaint.textSize = px
            // 宽的一层用光点贴图（1.7 倍字号）代替大半径 BlurMaskFilter：后者在 HWUI 上逐字走软件光栅，
            // 第 2 轮取证 5 张里 3 张弹「无响应」。
            spritePaint.colorFilter = PorterDuffColorFilter(argbOf(g.color, 1.0), PorterDuff.Mode.MULTIPLY)
            spritePaint.alpha = (g.alpha * g.lit * 0.4).coerceIn(0.0, 1.0).times(255).toInt()
            val halo = px * 0.85f
            glowRect.set(g.x.toFloat() - halo, g.y.toFloat() - halo, g.x.toFloat() + halo, g.y.toFloat() + halo)
            nc.drawBitmap(dot, null, glowRect, spritePaint)
            spritePaint.colorFilter = null
            val radius = max(1, (px * 0.2f).toInt())
            glowPaint.maskFilter = blurs.getOrPut(radius) { BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL) }
            glowPaint.color = argbOf(core, g.alpha * g.lit * 0.95)
            nc.drawText(g.char, g.x.toFloat(), baseline, glowPaint)
        }
        textPaint.color = argbOf(core, g.alpha)
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

