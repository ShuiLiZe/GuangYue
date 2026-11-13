// 镜台：歌词栅格——dioramaTextRaster.ts（每个单元烘两张：实字 base + 发光 glow，同一画布尺寸与落笔位置，光晕天然对齐笔画；
// 邻行整行一张实字，超 4096 px 按比例缩字号）与 dioramaLineRasterCache.ts（邻行整行栅格缓存：字体 / 歌词版本变了整体作废，
// 不再需要的行释放，缺的行每帧只栅格化 NEIGHBOR_RASTER_BUDGET 行）逐函数照译。
// 手机版：位图是 ALPHA_8（Folia 也只烘白字，颜色由材质每帧着色），CSS shadowBlur b → 高斯 σ = b/2 → Skia BlurMaskFilter 半径 (σ − 0.5)/0.57735。
// 尺寸 / 摆位是纯函数（测量函数注入），DioramaTextRasterTest 对 Folia 原式。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import com.guangyue.core.lyrics.Line
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

internal const val DIORAMA_RASTER_FONT_PX = 128
internal const val DIORAMA_DEFAULT_FONT_WEIGHT = 700
private const val LINE_BAND_EM = 1.4
private const val GLOW_PAD_EM = 0.7
private const val PLAIN_PAD_EM = 0.2
internal const val DIORAMA_MAX_CANVAS_PX = 4096
private const val GLOW_BLUR_EM = 0.16

// ---- 书写方向（utils/lyrics/textDirection.ts：第一个有方向的字母决定整行）----

private val RTL_SCRIPTS: Set<Character.UnicodeScript> = listOf(
    "ARABIC", "HEBREW", "SYRIAC", "THAANA", "NKO", "SAMARITAN", "MANDAIC", "ADLAM", "HANIFI_ROHINGYA", "YEZIDI",
).mapNotNull { runCatching { Character.UnicodeScript.valueOf(it) }.getOrNull() }.toSet()

internal fun isDioramaRtlText(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        if (!Character.isLetter(cp)) continue
        return Character.UnicodeScript.of(cp) in RTL_SCRIPTS
    }
    return false
}

// ---- 尺寸（纯函数）----

internal data class DioramaRasterGeometry(val canvasWidthPx: Int, val canvasHeightPx: Int, val drawX: Double, val drawY: Double, val advancePx: Int, val fontPx: Int)

/** rasterDioramaUnit 的画布：advance + 两边 0.7em 光晕留边，高 1.4em 带 + 留边，基线居中。 */
internal fun dioramaUnitGeometry(measuredPx: Double): DioramaRasterGeometry {
    val em = DIORAMA_RASTER_FONT_PX
    val pad = ceil(em * GLOW_PAD_EM).toInt()
    val advance = max(1, ceil(measuredPx).toInt())
    val w = advance + pad * 2
    val h = ceil(em * LINE_BAND_EM).toInt() + pad * 2
    return DioramaRasterGeometry(w, h, pad.toDouble(), h / 2.0, advance, em)
}

/** rasterDioramaLine 的画布：0.2em 留边；超 4096 就按比例缩字号（不小于 24 px）再量一次。measureAt(fontPx) 给该字号下整行宽。 */
internal fun dioramaLineGeometry(measureAt: (Int) -> Double): DioramaRasterGeometry {
    var fontPx = DIORAMA_RASTER_FONT_PX
    var advance = max(1, ceil(measureAt(fontPx)).toInt())
    val pad = ceil(fontPx * PLAIN_PAD_EM).toInt()
    if (advance + pad * 2 > DIORAMA_MAX_CANVAS_PX) {
        val shrink = (DIORAMA_MAX_CANVAS_PX - pad * 2).toDouble() / advance
        fontPx = max(24, floor(fontPx * shrink).toInt())
        advance = max(1, ceil(measureAt(fontPx)).toInt())
    }
    val w = advance + pad * 2
    val h = ceil(fontPx * LINE_BAND_EM).toInt() + pad * 2
    return DioramaRasterGeometry(w, h, pad.toDouble(), h / 2.0, advance, fontPx)
}

/** 当前行单元的世界摆位：中心 X（按整行前缀量宽、RTL 从右起）、世界宽高。 */
internal data class DioramaUnitSlot(val centerX: Double, val width: Double, val height: Double)

internal fun dioramaUnitSlot(totalPx: Double, prefixPx: Double, unitAdvancePx: Int, canvasW: Int, canvasH: Int, rtl: Boolean): DioramaUnitSlot {
    val worldPerPx = DIORAMA_LINE_FONT_SIZE / DIORAMA_RASTER_FONT_PX
    val startPx = prefixPx + unitAdvancePx / 2.0
    return DioramaUnitSlot(
        (if (rtl) totalPx / 2 - startPx else -totalPx / 2 + startPx) * worldPerPx,
        canvasW * worldPerPx,
        canvasH * worldPerPx,
    )
}

/** CSS shadowBlur（px）→ Skia BlurMaskFilter 半径：σ = blur / 2，radius = (σ − 0.5) / 0.57735。 */
internal fun dioramaShadowBlurToMaskRadius(shadowBlur: Double): Float = max(0.1, (shadowBlur / 2 - 0.5) / 0.57735).toFloat()

// ---- 画（Android Canvas）----

/** 字体：Typeface + 粗细；key 变了邻行缓存整体作废（= Folia fontSpec）。 */
internal class DioramaFont(val typeface: Typeface?, val weight: Int = DIORAMA_DEFAULT_FONT_WEIGHT) {
    val key: String = "${weight}|${typeface?.hashCode() ?: 0}"
    fun paint(px: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px.toFloat()
        textAlign = Paint.Align.LEFT
        color = 0xffffffff.toInt()
        typeface = resolveTypeface()
    }
    private fun resolveTypeface(): Typeface {
        val base = typeface ?: Typeface.DEFAULT
        return if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    fun measure(text: String, px: Int = DIORAMA_RASTER_FONT_PX): Double = paint(px).measureText(text).toDouble()
}

/** textBaseline = 'middle'：字身框中线落在 y。 */
private fun middleBaseline(p: Paint, y: Double): Float { val fm = p.fontMetrics; return (y - (fm.ascent + fm.descent) / 2).toFloat() }

internal class DioramaUnitRaster(val base: Bitmap, val glow: Bitmap, val canvasWidthPx: Int, val canvasHeightPx: Int, val advancePx: Int) {
    fun recycle() { base.recycle(); glow.recycle() }
}

internal class DioramaLineRaster(val bitmap: Bitmap, val canvasWidthPx: Int, val canvasHeightPx: Int, val advancePx: Int, val fontPx: Int) {
    fun recycle() = bitmap.recycle()
}

/** drawGlowGlyph：纯光晕（不带实字），四层模糊 blur / blur / 2·blur / 2.9·blur，透明度 0.95 / 0.85 / 0.55 / 0.3，加色叠。 */
private fun drawGlowGlyph(c: Canvas, p: Paint, text: String, x: Float, y: Float, blur: Double) {
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    for ((b, a) in listOf(blur to 0.95, blur to 0.85, blur * 2 to 0.55, blur * 2.9 to 0.3)) {
        p.maskFilter = BlurMaskFilter(dioramaShadowBlurToMaskRadius(b), BlurMaskFilter.Blur.NORMAL)
        p.alpha = (a * 255 + 0.5).toInt()
        c.drawText(text, x, y, p)
    }
    p.maskFilter = null
    p.xfermode = null
    p.alpha = 255
}

internal fun rasterDioramaUnit(text: String, font: DioramaFont): DioramaUnitRaster {
    val p = font.paint(DIORAMA_RASTER_FONT_PX)
    val g = dioramaUnitGeometry(p.measureText(text).toDouble())
    val y = middleBaseline(p, g.drawY)
    val base = Bitmap.createBitmap(g.canvasWidthPx, g.canvasHeightPx, Bitmap.Config.ALPHA_8)
    Canvas(base).drawText(text, g.drawX.toFloat(), y, p)
    val glow = Bitmap.createBitmap(g.canvasWidthPx, g.canvasHeightPx, Bitmap.Config.ALPHA_8)
    drawGlowGlyph(Canvas(glow), p, text, g.drawX.toFloat(), y, DIORAMA_RASTER_FONT_PX * GLOW_BLUR_EM)
    return DioramaUnitRaster(base, glow, g.canvasWidthPx, g.canvasHeightPx, g.advancePx)
}

internal fun rasterDioramaLine(text: String, font: DioramaFont): DioramaLineRaster {
    val g = dioramaLineGeometry { px -> font.measure(text, px) }
    val p = font.paint(g.fontPx)
    val bmp = Bitmap.createBitmap(g.canvasWidthPx, g.canvasHeightPx, Bitmap.Config.ALPHA_8)
    Canvas(bmp).drawText(text, g.drawX.toFloat(), middleBaseline(p, g.drawY), p)
    return DioramaLineRaster(bmp, g.canvasWidthPx, g.canvasHeightPx, g.advancePx, g.fontPx)
}

internal class DioramaPlacedUnit(val raster: DioramaUnitRaster, val slot: DioramaUnitSlot)

/** layoutDioramaActiveUnits：当前行逐单元栅格化，按整行前缀量宽放到字距正确的位置；RTL 行从右往左。 */
internal fun layoutDioramaActiveUnits(activeLine: Line?, units: List<DioramaLyricUnit>, font: DioramaFont): Pair<List<DioramaPlacedUnit>, Double>? {
    val full = activeLine?.fullText
    if (full.isNullOrEmpty() || units.isEmpty()) return null
    val totalPx = font.measure(full)
    val rtl = isDioramaRtlText(full)
    val placed = units.map { u ->
        val prefixPx = font.measure(full.substring(0, u.charStart))
        val r = rasterDioramaUnit(full.substring(u.charStart, u.charEnd), font)
        DioramaPlacedUnit(r, dioramaUnitSlot(totalPx, prefixPx, r.advancePx, r.canvasWidthPx, r.canvasHeightPx, rtl))
    }
    return placed to totalPx * DIORAMA_LINE_FONT_SIZE / DIORAMA_RASTER_FONT_PX
}

// ---- 邻行整行栅格缓存（dioramaLineRasterCache.ts）----

/**
 * 按全局行号缓存邻行整行栅格。sync 同步作废 / 释放（便宜），缺的行排进队列；渲染线程每帧调 pump 只栅格化 budget 行
 * （= Folia 每个 requestAnimationFrame 一批）。raster 参数可注入（测试用）。
 */
internal class DioramaLineRasterCache<R : Any>(
    private val raster: (String) -> R,
    private val release: (R) -> Unit,
    private val budget: Int = DIORAMA_NEIGHBOR_RASTER_BUDGET,
) {
    val rasters = HashMap<Int, R>()
    private var font = ""
    private var epoch = -1
    private val queue = ArrayDeque<Pair<Int, String>>()

    fun get(index: Int): R? = rasters[index]

    /** 返回本次是否立刻有变化（释放了行 / 作废了缓存）。 */
    fun sync(visible: List<DioramaVisibleLine>, globalIndex: Int, fontKey: String, linesEpoch: Int): Boolean {
        var changed = false
        if (font != fontKey || epoch != linesEpoch) {
            if (rasters.isNotEmpty()) changed = true
            rasters.values.forEach(release); rasters.clear()
            font = fontKey; epoch = linesEpoch
        }
        val wanted = LinkedHashMap<Int, String>()
        for (e in visible) if (e.line.fullText.isNotEmpty() && e.index != globalIndex) wanted[e.index] = e.line.fullText
        val it = rasters.entries.iterator()
        while (it.hasNext()) { val (k, v) = it.next(); if (k !in wanted) { release(v); it.remove(); changed = true } }
        queue.clear()
        for ((k, t) in wanted) if (k !in rasters) queue.addLast(k to t)
        return changed
    }

    /** 栅格化至多 budget 个缺的行；返回是否有新行落地。 */
    fun pump(): Boolean {
        var n = 0
        var landed = false
        while (n < budget && queue.isNotEmpty()) {
            val (k, t) = queue.removeFirst()
            if (k !in rasters) { rasters[k] = raster(t); landed = true }
            n += 1
        }
        return landed
    }

    val pending: Int get() = queue.size

    /** 镜头正离开的那行：转场这一帧要从逐字降成整行，同步补栅格。 */
    fun ensureOutgoing(outgoingIndex: Int, visible: List<DioramaVisibleLine>) {
        if (outgoingIndex in rasters) return
        val leaving = visible.firstOrNull { it.index == outgoingIndex } ?: return
        if (leaving.line.fullText.isNotEmpty()) rasters[outgoingIndex] = raster(leaving.line.fullText)
    }

    fun disposeAll() { rasters.values.forEach(release); rasters.clear(); queue.clear() }
}
