// 对应 Folia 文件：src/components/visualizer/hanabi/（花火）——hanabiProgram.ts（整首编译：段落 → 镜头）、hanabiSceneEntry.ts（段落 → 场景单元，
// 外层 holder 套转场帧）、scene.ts（世界上升 + 旋转 + 镜头内运镜；每个镜头：烟花 → 字底色块 → 字）、text/（逐字精灵与色块）、
// mg/fireworks.ts（精灵池：升空尾迹在下、烟花在上，闪光加色）、mg/textures.ts（白色粒子纹理，着色靠 tint）、hanabiSky.ts（夜空底）。
//
// R7⑥c：旧 P7 精简舞台整体换成 Folia 原版结构。纯逻辑全在 HanabiCore / Timeline / Burst / Color / Frames / Layout / Animations /
// Blocks / Text / Scene（均与 Folia 金标对拍）；这里只做绘制：Pixi 的 Container 变换链换成 android Canvas 的 save / concat / restore，
// 精灵换成预烘的白色 ALPHA_8 纹理（画笔颜色着色），字照 glyphs.ts 建单元时画进图集（字 × px × 填充 / 加粗描边 / 光晕），逐帧只贴图（矩阵含 Pixi 的 skew）。
// 辉光：烟花层 glow + 全屏 bloom 用预烘近似（HanabiBloom.kt：亮精灵叠软光贴图、字叠预烘模糊字，强度随副歌曲线）。
// 未接（与 Folia 的差距）：MG 装饰件、PV 拼贴、段落转场模糊、片尾卡的 PV 拼贴与歌词退场模糊、随拍开花。片尾卡见 HanabiCredits.kt。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val DEG = (180.0 / PI).toFloat()

/** 白色粒子纹理（mg/textures.ts 的 dot / glow / star4 / streak + 装饰件用的 ring / square / triangle / petal），只画一次。 */
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
    /** 细圆环（textures.ts drawRing）。 */
    val ring: Bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val line = 4f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = line; color = android.graphics.Color.WHITE }
        c.drawCircle(40f, 40f, 40f - line, p)
    }
    /** 实心方块（drawSquare）。 */
    val square: Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE) }
    /** 三角碎片（drawTriangle）：重心在纹理中心。 */
    val triangle: Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val cc = 24.0; val r = 48 * 0.48
        val path = Path()
        for (i in 0 until 3) {
            val a = -PI / 2 + (i * PI * 2) / 3
            val x = (cc + cos(a) * r).toFloat(); val y = (cc + sin(a) * r + r * 0.12).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }
    /** 樱花瓣（drawPetal）：上宽下尖，顶端一个小缺口。 */
    val petal: Bitmap = Bitmap.createBitmap(32, 44, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val w = 32f; val h = 44f; val cx = w / 2; val top = h * 0.06f; val bottom = h * 0.96f; val notch = h * 0.16f
        val path = Path()
        path.moveTo(cx, bottom)
        path.cubicTo(cx - w * 0.62f, h * 0.62f, cx - w * 0.5f, top, cx - w * 0.14f, top)
        path.lineTo(cx, top + notch)
        path.lineTo(cx + w * 0.14f, top)
        path.cubicTo(cx + w * 0.5f, top, cx + w * 0.62f, h * 0.62f, cx, bottom)
        path.close()
        c.drawPath(path, p)
    }
}

/** 一首歌的花火渲染器：程序、场景单元缓存、纹理与画笔都只建一次；每帧只改画笔参数。 */
private class HbRenderer(
    private val lines: List<Line>,
    private val program: HbProgram,
    private val theme: HbTheme,
    private val fontScale: Double,
    private val transitions: Boolean,
    /** 片尾卡的曲目信息（Folia metadata：title / artist / album）。 */
    private val songTitle: String? = null,
    private val songArtist: String? = null,
    private val songAlbum: String? = null,
) {
    private val textures = HbTextures().let { t -> arrayOf(t.glow.extractAlpha(), t.dot.extractAlpha(), t.star4.extractAlpha(), t.streak.extractAlpha(),
        t.ring.extractAlpha(), t.square.extractAlpha(), t.triangle.extractAlpha(), t.petal.extractAlpha()) }
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
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        bakeAtlas(scene)
        units[index] = scene
        while (units.size > 3) units.remove(units.keys.first())
        if (atlas.size > 900) {
            // 只留仍在缓存里的单元用到的字。
            val keep = HashSet<String>()
            units.values.forEach { u -> u.built.forEach { b -> b.text?.let { keep.addAll(atlasKeys(it)) } } }
            atlas.keys.retainAll(keep)
        }
        return scene
    }

    // ─── 字形图集（text/glyphs.ts）：每个 字 × 字号 × 变体 在建单元时画一次（ALPHA_8，着色靠画笔颜色），逐帧只贴图。 ───
    private val atlas = HashMap<String, Bitmap>()
    private val bake = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@HbRenderer.typeface; textAlign = Paint.Align.CENTER; color = android.graphics.Color.WHITE }

    private fun key(ch: String, px: Double, v: Int) = "$v|${px.toInt()}|$ch"

    private fun atlasKeys(tx: HbText): List<String> {
        val out = ArrayList<String>()
        val fat = tx.spec.outline
        for (r in tx.rigs) {
            out += key(r.char, r.entryPx, 0)
            if (fat) out += key(r.char, r.entryPx, 1)
            if (tx.spec.sing == "glow") out += key(r.char, r.entryPx, 2)
            out += key(r.char, r.entryPx, 3)
        }
        for (ch in tx.scramble) { out += key(ch, tx.scramblePx, 0); if (fat) out += key(ch, tx.scramblePx, 1) }
        return out
    }

    private fun bakeAtlas(scene: HbScene) {
        for (b in scene.built) b.text?.let { bakeText(it) }
    }

    private fun bakeText(tx: HbText) {
        for (k in atlasKeys(tx)) {
            if (atlas.containsKey(k)) continue
            val parts = k.split('|', limit = 3)
            atlas[k] = bakeGlyph(parts[2], parts[1].toDouble(), parts[0].toInt())
        }
    }

    // ─── 片尾卡（credits.ts / hanabiCreditsLayer.ts）：最后一句唱完前 4 s 预建，尺寸变了重建。 ───
    private var credits: HbCredits? = null
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private fun ensureCredits(t: Double, w: Float, h: Float, frame: HbCreditsFrame): HbCredits? {
        val end = program.lyricEndTime ?: return null
        if (!hasHanabiCredits(songTitle, songArtist, songAlbum)) return null
        credits?.let { if (it.width != w.toDouble() || it.height != h.toDouble()) credits = null }
        if (credits == null && (frame.active || t >= end - HB_CREDITS_PREBUILD)) {
            credits = createHanabiCredits(w.toDouble(), h.toDouble(), theme, songTitle, songArtist, songAlbum, tuning, fontScale, measure).also { cr ->
                cr.text?.let { bakeText(it) }
            }
        }
        return credits
    }

    private fun drawCredits(cr: HbCredits, elapsed: Double, frame: HbCreditsFrame, w: Float, h: Float) {
        val c = canvas
        val time = max(0.0, elapsed)
        cr.update(elapsed)
        alpha = frame.posterAlpha
        c.save()
        // holder：以画面中心为枢轴，上下偏移、缩放（花火片尾恒为 0 / 1）。
        c.translate(w / 2, h / 2 + (frame.posterOffsetY * h).toFloat())
        c.scale(frame.posterScale.toFloat(), frame.posterScale.toFloat())
        c.translate(-w / 2, -h / 2)
        // 烟花层：尾迹 + 金冠 / 千輪 / 柳环（maxParticles 1400）。
        activePlans.clear()
        for (e in cr.bursts) if (time >= e.at && time <= e.at + e.spec.duration) {
            activePlans += cr.plans.getOrPut(e) { hbPlanBurst(e, cr.palette.sparks, cr.unit, max(0.0, tuning.sparkAmount)) }
        }
        var used = 0
        val counting = HbSparkEmit { tex, x, y, ww, hh, r, a, tint -> if (used >= HB_CREDITS_PARTICLES) false else { used++; emit.emit(tex, x, y, ww, hh, r, a, tint) } }
        for (l in cr.launches) if (!hbDrawLaunch(l, time, cr.unit, counting)) break
        if (activePlans.isNotEmpty()) hbDrawBursts(activePlans, time, HB_CREDITS_PARTICLES - used, emit)
        sprite.xfermode = null
        // 曲名：一行「歌词」，放在画面正中（text.view.position = (w/2, h·0.5)）。
        cr.text?.let { tx ->
            c.save(); c.translate(w / 2, h * 0.5f); drawText(tx, time); c.restore()
        }
        // 艺人 / 专辑：居中，字距 = 字号 × spacing（Paint.letterSpacing 以 em 计），超出 0.7 屏宽就按比例缩小（Pixi 是换行）。
        for (d in cr.details) {
            if (d.alpha <= 0.002) continue
            detailPaint.textSize = d.size.toFloat()
            detailPaint.letterSpacing = d.spacing.toFloat()
            val width = detailPaint.measureText(d.text)
            if (width > w * 0.7f) detailPaint.textSize = (d.size * w * 0.7f / width).toFloat()
            detailPaint.color = 0xff000000.toInt() or cr.palette.paper
            detailPaint.alpha = a255(d.alpha)
            val fm = detailPaint.fontMetrics
            c.drawText(d.text, w / 2, (d.y * h + d.offsetY).toFloat() - (fm.ascent + fm.descent) / 2, detailPaint)
        }
        c.restore()
        alpha = 1.0
    }

    /** 全屏 bloom 的预烘版（图集变体 3）：只有模糊、没有实心芯，按 TEXT_BAKE_SCALE 缩小烘，drawGlyph 里放大回去、加色叠在字上。 */
    private fun bakeBloomGlyph(ch: String, px: Double): Bitmap {
        val q = HbGlowLook.TEXT_BAKE_SCALE
        val qpx = px * q
        val side = kotlin.math.ceil(qpx * 1.3 + 2 * qpx * HbGlowLook.TEXT_PAD + 4).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
        val c = android.graphics.Canvas(bmp)
        bake.textSize = qpx.toFloat()
        bake.style = Paint.Style.FILL
        bake.alpha = 255
        bake.maskFilter = android.graphics.BlurMaskFilter((qpx * HbGlowLook.TEXT_BLUR).toFloat().coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
        val fm = bake.fontMetrics
        c.drawText(ch, side / 2f, side / 2f - (fm.ascent + fm.descent) / 2, bake)
        bake.maskFilter = null
        return bmp
    }

    private fun bakeGlyph(ch: String, px: Double, v: Int): Bitmap {
        if (v == 3) return bakeBloomGlyph(ch, px)
        val pad = when (v) { 1 -> HB_OUTLINE_WIDTH * px; 2 -> px * 0.32; else -> 0.0 }
        val side = kotlin.math.ceil(px * 1.3 + 2 * pad + 4).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
        val c = android.graphics.Canvas(bmp)
        bake.textSize = px.toFloat()
        bake.maskFilter = null
        bake.alpha = 255
        val fm = bake.fontMetrics
        val cx = side / 2f
        val cy = side / 2f - (fm.ascent + fm.descent) / 2
        when (v) {
            1 -> { bake.style = Paint.Style.FILL_AND_STROKE; bake.strokeWidth = (HB_OUTLINE_WIDTH * px * 2).toFloat(); bake.strokeJoin = Paint.Join.ROUND; c.drawText(ch, cx, cy, bake) }
            2 -> {
                bake.style = Paint.Style.FILL
                bake.alpha = (0.85 * 255).toInt()
                bake.maskFilter = android.graphics.BlurMaskFilter((px * 0.22).toFloat().coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                c.drawText(ch, cx, cy, bake); c.drawText(ch, cx, cy, bake)
                bake.maskFilter = null
                c.drawText(ch, cx, cy, bake)
            }
            else -> { bake.style = Paint.Style.FILL; c.drawText(ch, cx, cy, bake) }
        }
        bake.style = Paint.Style.FILL
        return bmp
    }

    private fun a255(a: Double) = (a.coerceIn(0.0, 1.0) * alpha * 255).toInt()
    /** 这一帧的全屏 bloom 强度（resolveHanabiScreenBloomStrength），draw 开头算一次。 */
    private var screenBloom = 0.0
    /** 本帧还能叠几张火花光晕。 */
    private var haloBudget = 0

    private val emit = HbSparkEmit { tex, x, y, w, h, r, a, tint ->
        val bmp = when (tex) { HbSparkTexture.DOT -> textures[1]; HbSparkTexture.STAR4 -> textures[2]; HbSparkTexture.STREAK -> textures[3]; else -> textures[0] }
        sprite.color = 0xff000000.toInt() or tint
        sprite.alpha = a255(if (a > 1) 1.0 else a)
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
        // 预烘辉光：亮精灵上叠一张软光贴图（加色），代替 Folia 烟花层 bloom + 全屏 bloom 的整屏模糊。
        if (tex != HbSparkTexture.FLASH && haloBudget > 0 && a >= HbGlowLook.HALO_MIN_ALPHA) {
            val ha = HbGlowLook.sparkHaloAlpha(if (a > 1) 1.0 else a, tuning.glow, screenBloom)
            if (ha > 0.01) {
                haloBudget--
                val hs = (maxOf(w, h) * HbGlowLook.HALO_SIZE / 2).toFloat()
                sprite.alpha = a255(ha)
                sprite.xfermode = additive
                rect.set(x.toFloat() - hs, y.toFloat() - hs, x.toFloat() + hs, y.toFloat() + hs)
                canvas.drawBitmap(textures[0], null, rect, sprite)
                sprite.xfermode = null
            }
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
        haloBudget = HbGlowLook.HALO_BUDGET
        screenBloom = resolveHanabiScreenBloomStrength(tuning.bloom, tuning.chorusBloom, resolveHanabiChorusWeight(program.chorus, t))
        val creditsFrame = resolveHanabiCreditsLayerFrame(t, program.lyricEndTime, songTitle, songArtist, songAlbum)
        val cr = ensureCredits(t, w, h, creditsFrame)
        val lyricAlpha = if (creditsFrame.active) creditsFrame.lyricAlpha else 1.0
        val (_, layers) = resolveHanabiSceneFrames(program, t, transitions)
        for (layer in layers) {
            val f = layer.frame
            if (f.alpha * lyricAlpha <= 0.002) continue
            val scene = unitOf(layer.index, w, h)
            c.save()
            c.translate(w / 2, h / 2 + (f.y * h).toFloat())
            c.rotate((f.rotation * DEG).toFloat())
            c.scale(f.scale.toFloat(), f.scale.toFloat())
            c.translate(-w / 2, -h / 2)
            alpha = f.alpha * lyricAlpha
            drawScene(scene, t, w, h)
            c.restore()
        }
        alpha = 1.0
        // 片尾卡在场景层之上、画框之下（Folia stage：sky → sceneLayer → credits.holder → overlayLayer）。
        if (cr != null && creditsFrame.active && creditsFrame.posterAlpha > 0.002) {
            drawCredits(cr, t - (program.lyricEndTime ?: t), creditsFrame, w, h)
        }
        drawOverlay(w, h)
    }

    private val overlayPalette = resolveHanabiPalette("kawaii", theme, "mixed")
    private val overlayText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /** overlay.ts：左上「花火」朱印、右缘竖排 HANABI、左下三颗烟花色圆点 + 细线、右下角标（静态、小而淡）。 */
    private fun drawOverlay(w: Float, h: Float) {
        val c = canvas
        val p = overlayPalette
        val unit = min(w, h)
        val padX = maxOf(w * 0.07f, unit * 0.06f)
        val padY = maxOf(h * 0.08f, unit * 0.06f)
        val line = maxOf(1.2f, unit / 640f)
        val seal = unit * 0.024f
        val sx = padX + seal; val sy = padY + seal
        shape.color = 0xff000000.toInt() or p.blocks[0]; shape.alpha = (0.85 * 255).toInt()
        c.drawCircle(sx, sy, seal, shape)
        stroke.strokeWidth = line; stroke.color = 0xff000000.toInt() or p.blocks[0]; stroke.alpha = (0.5 * 255).toInt()
        c.drawCircle(sx, sy, seal * 1.28f, stroke)
        overlayText.textSize = seal * 0.66f; overlayText.letterSpacing = -0.04f
        overlayText.color = 0xff000000.toInt() or p.ink
        var fm = overlayText.fontMetrics
        c.drawText("花火", sx, sy - (fm.ascent + fm.descent) / 2, overlayText)
        val letter = unit * 0.017f
        overlayText.textSize = letter; overlayText.letterSpacing = 0f
        overlayText.color = 0xff000000.toInt() or p.paper; overlayText.alpha = (0.55 * 255).toInt()
        fm = overlayText.fontMetrics
        "HANABI".forEachIndexed { i, ch -> c.drawText(ch.toString(), w - padX, padY + letter * (0.6f + i * 1.5f) - (fm.ascent + fm.descent) / 2, overlayText) }
        val stemTop = padY + letter * (0.6f + 6 * 1.5f)
        stroke.color = 0xff000000.toInt() or p.paper; stroke.alpha = (0.35 * 255).toInt()
        c.drawLine(w - padX, stemTop, w - padX, stemTop + unit * 0.08f, stroke)
        val dotY = h - padY
        p.sparks.take(3).forEachIndexed { i, col ->
            shape.color = 0xff000000.toInt() or col; shape.alpha = (0.85 * 255).toInt()
            c.drawCircle(padX + i * unit * 0.018f, dotY, unit * 0.0055f, shape)
        }
        c.drawLine(padX + unit * 0.05f, dotY, padX + unit * 0.16f, dotY, stroke)
        val arm = unit * 0.035f
        stroke.strokeWidth = line * 1.4f; stroke.color = 0xff000000.toInt() or p.accent; stroke.alpha = (0.6 * 255).toInt()
        path.reset(); path.moveTo(w - padX - arm, h - padY); path.lineTo(w - padX, h - padY); path.lineTo(w - padX, h - padY - arm)
        c.drawPath(path, stroke)
    }

    private fun drawScene(scene: HbScene, t: Double, w: Float, h: Float) {
        val c = canvas
        val cam = scene.timeline.camera(t)
        c.save()
        c.translate(w / 2 + cam.dx.toFloat(), h / 2 + cam.dy.toFloat())
        c.rotate((cam.rotation * DEG).toFloat())
        c.scale(cam.scale.toFloat(), cam.scale.toFloat())
        val rise = scene.timeline.rise(t)
        val amb = scene.ambient
        if (amb != null) {
            // ambient.back 在 stage 里、spin 外：按屏幕坐标画、自己做视差。
            amb.update(t, rise)
            c.save(); c.translate(-w / 2, -h / 2)
            for (d in amb.smoke) drawDot(d)
            for (d in amb.stars) drawDot(d)
            amb.drawDistant(t, rise, emit)
            sprite.xfermode = null
            c.restore()
        }
        c.save()
        c.rotate((scene.timeline.spin(t) * DEG).toFloat())
        c.translate(-w / 2, -h / 2 + rise.toFloat())
        for (item in scene.built) {
            if (t < item.lifeStart || t > item.lifeEnd) { item.ornaments = null; continue }
            val orns = item.ornaments ?: hbShotOrnaments(item, w.toDouble(), h.toDouble(), scene.seed, tuning).also { item.ornaments = it }
            for (o in orns) o.update(t)
            val timing = item.timing
            val region = item.shot.profile.region
            val wy = timing.worldY.toFloat()
            c.save()
            c.translate(w / 2, h / 2 + wy)
            c.rotate((-timing.spin * DEG).toFloat())
            c.translate(-w / 2, -(h / 2 + wy))
            c.translate((region.cx * w).toFloat(), (region.cy * h).toFloat() + wy)
            drawFireworks(scene, item, t)
            // scene.ts 层序：fireworks → ornaments(back) → collage(back) → text → ornaments(front)。
            drawOrnaments(orns, false)
            item.text?.let { drawText(it, t) }
            drawOrnaments(orns, true)
            c.restore()
        }
        c.restore()
        if (amb != null) {
            c.translate(-w / 2, -h / 2)
            for (d in amb.bokeh) drawDot(d)
            for (d in amb.embers) drawDot(d)
        }
        c.restore()
    }

    // ─── MG 装饰件（HanabiOrnaments.kt 的节点表）：贴共享纹理 / 画预录路径，只有锯齿线、波线每帧重建路径。 ───
    private class BakedShape(var version: Int, val paths: ArrayList<Path> = ArrayList())
    private val baked = java.util.IdentityHashMap<HbOrnShape, BakedShape>()
    private val ornOval = RectF()
    private val ornStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ornText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = this@HbRenderer.typeface; textAlign = Paint.Align.CENTER }

    private fun bakedOf(shape: HbOrnShape): BakedShape {
        val b = baked[shape] ?: BakedShape(-1).also { baked[shape] = it }
        if (b.version == shape.version) return b
        b.version = shape.version
        while (b.paths.size < shape.passes.size) b.paths += Path()
        shape.passes.forEachIndexed { k, pass ->
            val p = b.paths[k]
            p.reset()
            for (i in pass[5].toInt() until pass[0].toInt()) {
                val o = shape.ops[i]
                when (o[0].toInt()) {
                    0 -> p.moveTo(o[1].toFloat(), o[2].toFloat())
                    1 -> p.lineTo(o[1].toFloat(), o[2].toFloat())
                    2 -> {
                        ornOval.set((o[1] - o[3]).toFloat(), (o[2] - o[3]).toFloat(), (o[1] + o[3]).toFloat(), (o[2] + o[3]).toFloat())
                        p.arcTo(ornOval, (o[4] * DEG).toFloat(), ((o[5] - o[4]) * DEG).toFloat(), false)
                    }
                    3 -> p.addCircle(o[1].toFloat(), o[2].toFloat(), o[3].toFloat(), Path.Direction.CW)
                    4 -> { ornOval.set((o[1] - o[3]).toFloat(), (o[2] - o[4]).toFloat(), (o[1] + o[3]).toFloat(), (o[2] + o[4]).toFloat()); p.addOval(ornOval, Path.Direction.CW) }
                    5 -> p.addRect(o[1].toFloat(), o[2].toFloat(), (o[1] + o[3]).toFloat(), (o[2] + o[4]).toFloat(), Path.Direction.CW)
                    else -> p.close()
                }
            }
        }
        return b
    }

    private fun drawOrnNode(n: HbOrnNode, alphaMul: Double, tintOverride: Int?) {
        val c = canvas
        val tint = tintOverride ?: n.tint
        when (n) {
            is HbOrnSprite -> {
                val bmp = textures[when (n.tex) {
                    HbOrnTex.GLOW -> 0; HbOrnTex.DOT -> 1; HbOrnTex.STAR4 -> 2; HbOrnTex.STREAK -> 3
                    HbOrnTex.RING -> 4; HbOrnTex.SQUARE -> 5; HbOrnTex.TRIANGLE -> 6; HbOrnTex.PETAL -> 7
                }]
                val hw = (abs(n.width) / 2).toFloat(); val hh = (abs(n.height) / 2).toFloat()
                if (hw < 0.05f || hh < 0.05f) return
                sprite.color = 0xff000000.toInt() or tint
                sprite.alpha = a255(n.alpha * alphaMul)
                sprite.xfermode = null
                if (n.rotation != 0.0) {
                    c.save(); c.translate(n.x.toFloat(), n.y.toFloat()); c.rotate((n.rotation * DEG).toFloat())
                    rect.set(-hw, -hh, hw, hh); c.drawBitmap(bmp, null, rect, sprite); c.restore()
                } else {
                    rect.set(n.x.toFloat() - hw, n.y.toFloat() - hh, n.x.toFloat() + hw, n.y.toFloat() + hh)
                    c.drawBitmap(bmp, null, rect, sprite)
                }
            }
            is HbOrnGraphics -> {
                val shapeDef = n.shape
                if (shapeDef.passes.isEmpty()) return
                val b = bakedOf(shapeDef)
                c.save(); c.translate(n.x.toFloat(), n.y.toFloat()); c.rotate((n.rotation * DEG).toFloat()); c.scale(n.scale.toFloat(), n.scale.toFloat())
                ornStroke.color = 0xff000000.toInt() or tint
                ornStroke.alpha = a255(n.alpha * alphaMul)
                shapeDef.passes.forEachIndexed { k, pass ->
                    if (pass[1] == 1.0) ornStroke.style = Paint.Style.FILL
                    else {
                        ornStroke.style = Paint.Style.STROKE
                        ornStroke.strokeWidth = pass[2].toFloat()
                        ornStroke.strokeCap = when (pass[3].toInt()) { 1 -> Paint.Cap.ROUND; 2 -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                        ornStroke.strokeJoin = if (pass[4].toInt() == 1) Paint.Join.ROUND else Paint.Join.MITER
                    }
                    c.drawPath(b.paths[k], ornStroke)
                }
                c.restore()
            }
            is HbOrnText -> {
                ornText.textSize = n.fontSize.toFloat()
                ornText.color = 0xff000000.toInt() or tint
                ornText.alpha = a255(n.alpha * alphaMul)
                // Pixi Text anchor (0.5, 0.54)：文字框高 = ascent + descent，基线在框顶 + ascent。
                val fm = ornText.fontMetrics
                val boxH = fm.descent - fm.ascent
                c.drawText(n.text, n.x.toFloat(), (n.y - 0.54 * boxH - fm.ascent).toFloat(), ornText)
            }
            else -> {}
        }
    }

    private fun drawOrnaments(orns: List<HbOrnament>, front: Boolean) {
        val c = canvas
        for (o in orns) {
            if (!o.visible || (o.spec.layer == "front") != front) continue
            for (n in o.nodes) {
                if (!n.visible || n.parent != null) continue
                if (n is HbOrnHolder) {
                    // 印章：holder 的变换 / 透明度 / tint 传给子节点。
                    c.save(); c.translate(n.x.toFloat(), n.y.toFloat()); c.rotate((n.rotation * DEG).toFloat()); c.scale(n.scale.toFloat(), n.scale.toFloat())
                    for (ch in o.nodes) if (ch.parent === n && ch.visible) drawOrnNode(ch, n.alpha, n.tint)
                    c.restore()
                } else drawOrnNode(n, 1.0, null)
            }
        }
    }

    private val smokeSrc = android.graphics.Rect()

    private fun drawDot(d: HbAmbientDot) {
        if (!d.visible) return
        val bmp = when (d.tex) { 1 -> textures[1]; 2 -> textures[2]; else -> textures[0] }
        sprite.color = 0xff000000.toInt() or d.tint
        sprite.alpha = a255(d.pa)
        val hs = (d.ps / 2).toFloat()
        rect.set(d.px.toFloat() - hs, d.py.toFloat() - hs, d.px.toFloat() + hs, d.py.toFloat() + hs)
        if (d.tex == 3) {
            val inset = (bmp.width * (1 - HB_SMOKE_CROP) / 2).toInt()
            smokeSrc.set(inset, inset, bmp.width - inset, bmp.height - inset)
            canvas.drawBitmap(bmp, smokeSrc, rect, sprite)
        } else canvas.drawBitmap(bmp, null, rect, sprite)
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

    /** 一个精灵：Pixi 的 position / rotation / scale / skew.x（a = cos r·sx，b = sin r·sx，c = −sin(r − k)·sy，d = cos(r − k)·sy），贴图集里的字。 */
    private fun drawGlyph(s: HbSprite, mode: Int, tx: HbText, alphaMul: Double = 1.0, forceAdditive: Boolean = false) {
        if (!s.visible) return
        val bmp = atlas[key(s.glyph, s.glyphPx, mode)] ?: (if (mode == 3) null else atlas[key(s.glyph, s.glyphPx, 0)]) ?: return
        val c = canvas
        val r = s.rotation; val k = s.skew
        mv[0] = (cos(r) * s.sx).toFloat(); mv[1] = (-sin(r - k) * s.sy).toFloat(); mv[2] = s.x.toFloat()
        mv[3] = (sin(r) * s.sx).toFloat(); mv[4] = (cos(r - k) * s.sy).toFloat(); mv[5] = s.y.toFloat()
        mv[6] = 0f; mv[7] = 0f; mv[8] = 1f
        matrix.setValues(mv)
        c.save()
        c.concat(matrix)
        if (mode == 3) c.scale((1 / HbGlowLook.TEXT_BAKE_SCALE).toFloat(), (1 / HbGlowLook.TEXT_BAKE_SCALE).toFloat())
        val half = bmp.width / 2f
        if (s.half != 0) {
            if (tx.halvesLr) { if (s.half == 1) c.clipRect(-half, -half, 0f, half) else c.clipRect(0f, -half, half, half) }
            else { if (s.half == 1) c.clipRect(-half, -half, half, 0f) else c.clipRect(-half, 0f, half, half) }
        }
        glyphPaint.color = 0xff000000.toInt() or s.tint
        glyphPaint.alpha = a255(s.alpha * alphaMul)
        glyphPaint.xfermode = if (s.additive || forceAdditive) additive else null
        c.drawBitmap(bmp, -half, -half, glyphPaint)
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
        // 全屏 bloom（预烘模糊字，加色）：整体辉光常驻，副歌里更亮。
        val textBloom = screenBloom * HbGlowLook.SCREEN_TEXT
        if (textBloom > 0.01) for (r in tx.rigs) drawGlyph(r.main, 3, tx, textBloom, true)
        c.restore()
        glyphPaint.xfermode = null
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
    /** 片尾卡（Folia metadata）：三者全空就不出片尾卡、歌词也不淡出。 */
    songTitle: String? = null,
    songArtist: String? = null,
    songAlbum: String? = null,
) {
    val program = remember(lines, seed) { compileHanabiProgram(lines, seed) }
    val theme = remember(backgroundColor, primaryColor, accentColor, secondaryColor) {
        HbTheme(backgroundColor.toArgb() and 0xffffff, primaryColor.toArgb() and 0xffffff, accentColor.toArgb() and 0xffffff, secondaryColor.toArgb() and 0xffffff)
    }
    val renderer = remember(lines, program, theme, lyricsFontScale, reduceMotion, songTitle, songArtist, songAlbum) {
        HbRenderer(lines, program, theme, lyricsFontScale, !reduceMotion, songTitle, songArtist, songAlbum)
    }
    val bg = backgroundColor.toArgb()
    Canvas(modifier.fillMaxSize()) {
        val t = clock.readForDraw()
        drawIntoCanvas { renderer.draw(it.nativeCanvas, t, size.width, size.height, bg) }
    }
}
