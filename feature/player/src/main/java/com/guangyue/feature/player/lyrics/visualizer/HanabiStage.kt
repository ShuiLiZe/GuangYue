// 对应 Folia 文件：src/components/visualizer/hanabi/（花火）——hanabiProgram.ts（整首编译：段落 → 镜头）、hanabiSceneEntry.ts（段落 → 场景单元，
// 外层 holder 套转场帧）、scene.ts（世界上升 + 旋转 + 镜头内运镜；每个镜头：烟花 → 字底色块 → 字）、text/（逐字精灵与色块）、
// mg/fireworks.ts（精灵池：升空尾迹在下、烟花在上，闪光加色）、mg/textures.ts（白色粒子纹理，着色靠 tint）、hanabiSky.ts（夜空底）。
//
// R7⑥c：旧 P7 精简舞台整体换成 Folia 原版结构。纯逻辑全在 HanabiCore / Timeline / Burst / Color / Frames / Layout / Animations /
// Blocks / Text / Scene（均与 Folia 金标对拍）；这里只做绘制：Pixi 的 Container 变换链换成 android Canvas 的 save / concat / restore，
// 精灵换成预烘的白色纹理位图 + 颜色滤镜（滤镜按颜色缓存，不逐帧新建），字用 Paint.drawText（字号 = 图集格 px，矩阵含 Pixi 的 skew）。
// 未接（与 Folia 的差距）：环境层、MG 装饰件、PV 拼贴、烟花辉光与全屏 bloom、段落转场模糊、画框装饰、片尾卡、随拍开花。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val DEG = (180.0 / PI).toFloat()

/** 白色粒子纹理（mg/textures.ts 的 dot / glow / star4 / streak），只画一次。 */
private class HbTextures {
    val glow: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun w(a: Double) = android.graphics.Color.argb((a * 255).toInt(), 255, 255, 255)
        p.shader = RadialGradient(32f, 32f, 32f, intArrayOf(w(1.0), w(0.95), w(0.55), w(0.18), w(0.05), w(0.0)), floatArrayOf(0f, 0.1f, 0.22f, 0.42f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 64f, 64f, p)
    }
    val dot: Bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(16f, 16f, 16f, intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, 0x00ffffff), floatArrayOf(0f, 1f - 2f / 16f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 32f, 32f, p)
    }
    val star4: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cc = 32f; val r = 64f * 0.48f
        p.shader = RadialGradient(cc, cc, r * 0.5f, intArrayOf(android.graphics.Color.argb(230, 255, 255, 255), 0x00ffffff), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 64f, 64f, p)
        p.shader = null; p.color = android.graphics.Color.WHITE
        val pinch = r * 0.1f
        val path = Path()
        path.moveTo(cc, cc - r)
        path.quadTo(cc + pinch, cc - pinch, cc + r, cc)
        path.quadTo(cc + pinch, cc + pinch, cc, cc + r)
        path.quadTo(cc - pinch, cc + pinch, cc - r, cc)
        path.quadTo(cc - pinch, cc - pinch, cc, cc - r)
        path.close()
        c.drawPath(path, p)
    }
    val streak: Bitmap = Bitmap.createBitmap(16, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun w(a: Double) = android.graphics.Color.argb((a * 255).toInt(), 255, 255, 255)
        p.shader = LinearGradient(0f, 0f, 0f, 64f, intArrayOf(w(1.0), w(0.6), w(0.28)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 16f, 64f, p)
        p.shader = LinearGradient(0f, 0f, 16f, 0f, intArrayOf(w(0.0), w(0.85), w(1.0), w(0.85), w(0.0)), floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawRect(0f, 0f, 16f, 64f, p)
    }
}

/** 一首歌的花火渲染器：程序、场景单元缓存、纹理与画笔都只建一次；每帧只改画笔参数。 */
private class HbRenderer(
    private val lines: List<Line>,
    private val program: HbProgram,
    private val theme: HbTheme,
    private val fontScale: Double,
    private val transitions: Boolean,
) {
    private val textures = HbTextures()
    private val tuning = HbTuning()
    private val typeface: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 800, false) else Typeface.DEFAULT_BOLD
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@HbRenderer.typeface; textSize = 100f }
    private val measureCache = HashMap<String, Double>()
    private val measure: (String) -> Double = { ch ->
        measureCache.getOrPut(ch) { val w = measurePaint.measureText(ch) / 100.0; if (w.isFinite() && w > 0) w else hbEstimateAdvance(ch) }
    }
    private val units = LinkedHashMap<Int, HbScene>()
    private var unitW = 0f
    private var unitH = 0f

    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val tintFilters = HashMap<Int, PorterDuffColorFilter>()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@HbRenderer.typeface; textAlign = Paint.Align.CENTER }
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.SQUARE }
    private val sky = Paint()
    private var skyH = -1f
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()
    private val mv = FloatArray(9)
    private var alpha = 1.0
    private lateinit var canvas: android.graphics.Canvas

    private fun unitOf(index: Int, w: Float, h: Float): HbScene {
        if (w != unitW || h != unitH) { units.clear(); unitW = w; unitH = h }
        units[index]?.let { return it }
        val scene = hbBuildSceneUnit(program, index, lines, w.toDouble(), h.toDouble(), theme, tuning, fontScale, measure)
        units[index] = scene
        while (units.size > 3) units.remove(units.keys.first())
        return scene
    }

    private fun filterOf(tint: Int): PorterDuffColorFilter {
        tintFilters[tint]?.let { return it }
        if (tintFilters.size > 384) tintFilters.clear()
        return PorterDuffColorFilter(0xff000000.toInt() or tint, PorterDuff.Mode.MULTIPLY).also { tintFilters[tint] = it }
    }

    private fun a255(a: Double) = (a.coerceIn(0.0, 1.0) * alpha * 255).toInt()

    private val emit = HbSparkEmit { tex, x, y, w, h, r, a, tint ->
        val bmp = when (tex) { HbSparkTexture.DOT -> textures.dot; HbSparkTexture.STAR4 -> textures.star4; HbSparkTexture.STREAK -> textures.streak; else -> textures.glow }
        sprite.alpha = a255(if (a > 1) 1.0 else a)
        sprite.colorFilter = filterOf(tint)
        sprite.xfermode = if (tex == HbSparkTexture.FLASH) additive else null
        val hw = (w / 2).toFloat(); val hh = (h / 2).toFloat()
        if (r != 0.0) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate((r * DEG).toFloat())
            rect.set(-hw, -hh, hw, hh)
            canvas.drawBitmap(bmp, null, rect, sprite)
            canvas.restore()
        } else {
            rect.set(x.toFloat() - hw, y.toFloat() - hh, x.toFloat() + hw, y.toFloat() + hh)
            canvas.drawBitmap(bmp, null, rect, sprite)
        }
        true
    }

    fun draw(c: android.graphics.Canvas, t: Double, w: Float, h: Float, background: Int) {
        if (w <= 0f || h <= 0f) return
        canvas = c
        // 共享背景 + 夜空底（tuning.sky 0.85）。
        c.drawColor(background)
        if (skyH != h) {
            skyH = h
            val stops = resolveHanabiSkyStops(theme)
            sky.shader = LinearGradient(0f, 0f, 0f, h, stops.map { 0xff000000.toInt() or hbHslHex(it[1], it[2], it[3]) }.toIntArray(), stops.map { it[0].toFloat() }.toFloatArray(), Shader.TileMode.CLAMP)
        }
        sky.alpha = (tuning.sky * 255).toInt()
        c.drawRect(0f, 0f, w, h, sky)
        if (program.paragraphs.isEmpty()) return
        val (_, layers) = resolveHanabiSceneFrames(program, t, transitions)
        for (layer in layers) {
            val f = layer.frame
            if (f.alpha <= 0.002) continue
            val scene = unitOf(layer.index, w, h)
            c.save()
            c.translate(w / 2, h / 2 + (f.y * h).toFloat())
            c.rotate((f.rotation * DEG).toFloat())
            c.scale(f.scale.toFloat(), f.scale.toFloat())
            c.translate(-w / 2, -h / 2)
            alpha = f.alpha
            drawScene(scene, t, w, h)
            c.restore()
        }
        alpha = 1.0
    }

    private fun drawScene(scene: HbScene, t: Double, w: Float, h: Float) {
        val c = canvas
        val cam = scene.timeline.camera(t)
        c.save()
        c.translate(w / 2 + cam.dx.toFloat(), h / 2 + cam.dy.toFloat())
        c.rotate((cam.rotation * DEG).toFloat())
        c.scale(cam.scale.toFloat(), cam.scale.toFloat())
        c.rotate((scene.timeline.spin(t) * DEG).toFloat())
        c.translate(-w / 2, -h / 2 + scene.timeline.rise(t).toFloat())
        for (item in scene.built) {
            if (t < item.lifeStart || t > item.lifeEnd) continue
            val timing = item.timing
            val region = item.shot.profile.region
            val wy = timing.worldY.toFloat()
            c.save()
            c.translate(w / 2, h / 2 + wy)
            c.rotate((-timing.spin * DEG).toFloat())
            c.translate(-w / 2, -(h / 2 + wy))
            c.translate((region.cx * w).toFloat(), (region.cy * h).toFloat() + wy)
            drawFireworks(scene, item, t)
            item.text?.let { drawText(it, t) }
            c.restore()
        }
        c.restore()
    }

    private val activePlans = ArrayList<HbBurstPlan>()

    private fun drawFireworks(scene: HbScene, item: HbBuiltShot, t: Double) {
        activePlans.clear()
        for (e in item.bursts) {
            if (e.at > t) break
            if (t <= e.at + e.spec.duration) activePlans += scene.planOf(item, e)
        }
        var launchBudget = 0
        for (l in item.launches) launchBudget += hbLaunchDemand(l, t)
        launchBudget = min(launchBudget, (HB_SHOT_PARTICLES * 0.2).toInt())
        var used = 0
        val counting = HbSparkEmit { tex, x, y, ww, hh, r, a, tint -> if (used >= HB_SHOT_PARTICLES) false else { used++; emit.emit(tex, x, y, ww, hh, r, a, tint) } }
        for (l in item.launches) {
            if (used >= launchBudget) break
            if (!hbDrawLaunch(l, t, scene.unit, counting)) break
        }
        if (activePlans.isNotEmpty()) hbDrawBursts(activePlans, t, HB_SHOT_PARTICLES - used, emit)
        sprite.xfermode = null
        sprite.colorFilter = null
    }

    // ─── 字与色块 ───

    private fun drawBlocks(tx: HbText, back: Boolean) {
        val c = canvas
        for (piece in tx.blocks.pieces) for (part in piece.parts) {
            if (part.back != back || !part.visible) continue
            c.save()
            c.translate(part.x.toFloat(), part.y.toFloat())
            c.rotate((part.rotation * DEG).toFloat())
            c.scale(part.sx.toFloat(), part.sy.toFloat())
            for (op in part.ops) {
                val color = 0xff000000.toInt() or (if (op.color >= 0) op.color else part.tint)
                val a = a255(part.alpha * op.alpha)
                val f = op.f
                when (op.kind) {
                    HbShapeOp.RRECT -> { shape.color = color; shape.alpha = a; rect.set(f[0].toFloat(), f[1].toFloat(), (f[0] + f[2]).toFloat(), (f[1] + f[3]).toFloat()); c.drawRoundRect(rect, f[4].toFloat(), f[4].toFloat(), shape) }
                    HbShapeOp.CIRCLE -> { shape.color = color; shape.alpha = a; c.drawCircle(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), shape) }
                    HbShapeOp.RECT -> { shape.color = color; shape.alpha = a; c.drawRect(f[0].toFloat(), f[1].toFloat(), (f[0] + f[2]).toFloat(), (f[1] + f[3]).toFloat(), shape) }
                    HbShapeOp.POLY -> {
                        path.reset(); path.moveTo(f[0].toFloat(), f[1].toFloat())
                        var k = 2; while (k + 1 < f.size) { path.lineTo(f[k].toFloat(), f[k + 1].toFloat()); k += 2 }
                        path.close(); shape.color = color; shape.alpha = a; c.drawPath(path, shape)
                    }
                    HbShapeOp.TICKET -> {
                        val len = f[0].toFloat(); val th = f[1].toFloat(); val r = f[2].toFloat()
                        path.reset()
                        path.moveTo(-len / 2, -th / 2); path.lineTo(len / 2, -th / 2); path.lineTo(len / 2, -r)
                        rect.set(len / 2 - r, -r, len / 2 + r, r); path.arcTo(rect, -90f, -180f)
                        path.lineTo(len / 2, th / 2); path.lineTo(-len / 2, th / 2); path.lineTo(-len / 2, r)
                        rect.set(-len / 2 - r, -r, -len / 2 + r, r); path.arcTo(rect, 90f, -180f)
                        path.close(); shape.color = color; shape.alpha = a; c.drawPath(path, shape)
                    }
                    HbShapeOp.POLYLINE -> {
                        path.reset(); path.moveTo(f[1].toFloat(), f[2].toFloat())
                        var k = 3; while (k + 1 < f.size) { path.lineTo(f[k].toFloat(), f[k + 1].toFloat()); k += 2 }
                        stroke.strokeWidth = f[0].toFloat(); stroke.color = color; stroke.alpha = a; c.drawPath(path, stroke)
                    }
                }
            }
            c.restore()
        }
    }

    /** 一个精灵：Pixi 的 position / rotation / scale / skew.x（a = cos r·sx，b = sin r·sx，c = −sin(r − k)·sy，d = cos(r − k)·sy）。 */
    private fun drawGlyph(s: HbSprite, mode: Int, tx: HbText) {
        if (!s.visible) return
        val c = canvas
        val r = s.rotation; val k = s.skew
        mv[0] = (cos(r) * s.sx).toFloat(); mv[1] = (-sin(r - k) * s.sy).toFloat(); mv[2] = s.x.toFloat()
        mv[3] = (sin(r) * s.sx).toFloat(); mv[4] = (cos(r - k) * s.sy).toFloat(); mv[5] = s.y.toFloat()
        mv[6] = 0f; mv[7] = 0f; mv[8] = 1f
        matrix.setValues(mv)
        c.save()
        c.concat(matrix)
        val px = s.glyphPx.toFloat()
        text.textSize = px
        text.color = 0xff000000.toInt() or s.tint
        text.alpha = a255(s.alpha)
        text.xfermode = if (s.additive) additive else null
        if (s.half != 0) {
            if (tx.halvesLr) { if (s.half == 1) c.clipRect(-px, -px, 0f, px) else c.clipRect(0f, -px, px, px) }
            else { if (s.half == 1) c.clipRect(-px, -px, px, 0f) else c.clipRect(-px, 0f, px, px) }
        }
        val fm = text.fontMetrics
        val baseline = -(fm.ascent + fm.descent) / 2
        when (mode) {
            1 -> { text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = (2 * HB_OUTLINE_WIDTH * px).toFloat(); text.strokeJoin = Paint.Join.ROUND }
            2 -> { text.style = Paint.Style.STROKE; text.strokeWidth = px * 0.3f; text.strokeJoin = Paint.Join.ROUND; text.alpha = a255(s.alpha * 0.45) }
            else -> text.style = Paint.Style.FILL
        }
        c.drawText(s.glyph, 0f, baseline, text)
        c.restore()
    }

    private fun drawText(tx: HbText, t: Double) {
        tx.update(t)
        val c = canvas
        c.save()
        c.translate(tx.shakeX.toFloat(), tx.shakeY.toFloat())
        drawBlocks(tx, true)
        drawBlocks(tx, false)
        c.save()
        if (tx.wipeActive) {
            path.reset()
            for (q in tx.wipeQuads.values) {
                path.moveTo(q[0].toFloat(), q[1].toFloat()); path.lineTo(q[2].toFloat(), q[3].toFloat())
                path.lineTo(q[4].toFloat(), q[5].toFloat()); path.lineTo(q[6].toFloat(), q[7].toFloat()); path.close()
            }
            c.clipPath(path)
        }
        val fat = tx.spec.outline
        if (tx.spec.hardShadow) for (r in tx.rigs) drawGlyph(r.shadow, if (fat) 1 else 0, tx)
        if (fat) for (r in tx.rigs) drawGlyph(r.outline, 1, tx)
        if (tx.spec.sing == "glow") for (r in tx.rigs) drawGlyph(r.halo, 2, tx)
        for (r in tx.rigs) { drawGlyph(r.fxA, 0, tx); drawGlyph(r.fxB, 0, tx) }
        for (r in tx.rigs) drawGlyph(r.main, 0, tx)
        c.restore()
        text.xfermode = null
        if (tx.cursorVisible) {
            c.save()
            c.translate(tx.cursorX.toFloat(), tx.cursorY.toFloat())
            c.rotate((tx.cursorRot * DEG).toFloat())
            shape.color = 0xff000000.toInt() or tx.cursorTint; shape.alpha = a255(1.0)
            c.drawRect((-tx.cursorW / 2).toFloat(), (-tx.cursorH / 2).toFloat(), (tx.cursorW / 2).toFloat(), (tx.cursorH / 2).toFloat(), shape)
            c.restore()
        }
        for (u in tx.underlines) {
            if (!u.visible) continue
            c.save()
            c.translate(u.px.toFloat(), u.py.toFloat())
            c.rotate((u.angle * DEG).toFloat())
            shape.color = 0xff000000.toInt() or u.tint; shape.alpha = a255(u.alpha)
            c.drawRect(0f, (-u.sy / 2).toFloat(), u.sx.toFloat(), (u.sy / 2).toFloat(), shape)
            c.restore()
        }
        c.restore()
    }
}

@Composable
internal fun HanabiStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    @Suppress("UNUSED_PARAMETER") fontFamily: FontFamily,
    reduceMotion: Boolean,
    @Suppress("UNUSED_PARAMETER") bottomReserve: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0B0D24),
    primaryColor: Color = Color.White,
    secondaryColor: Color = accentColor,
    /** Folia 的 programSeed（歌曲身份）：同一首歌每次编排一样。 */
    seed: String = "hanabi",
) {
    val program = remember(lines, seed) { compileHanabiProgram(lines, seed) }
    val theme = remember(backgroundColor, primaryColor, accentColor, secondaryColor) {
        HbTheme(backgroundColor.toArgb() and 0xffffff, primaryColor.toArgb() and 0xffffff, accentColor.toArgb() and 0xffffff, secondaryColor.toArgb() and 0xffffff)
    }
    val renderer = remember(lines, program, theme, lyricsFontScale, reduceMotion) { HbRenderer(lines, program, theme, lyricsFontScale, !reduceMotion) }
    val bg = backgroundColor.toArgb()
    Canvas(modifier.fillMaxSize()) {
        val t = clock.readForDraw()
        drawIntoCanvas { renderer.draw(it.nativeCanvas, t, size.width, size.height, bg) }
    }
}
