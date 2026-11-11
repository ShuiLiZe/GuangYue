// 花火 PV 拼贴的纹理与绘制：逐函数对译 Folia hanabi/collage/collagePainters.ts（Canvas 2D → android.graphics.Canvas，按 key 缓存，单元换掉时整份放），
// 绘制照 collageViews.ts / unitCollage.ts 的显示树（Sprite / TilingSprite → drawBitmap / BitmapShader 平铺）。姿态在 HanabiCollage.kt。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEGF = (180.0 / PI).toFloat()

private fun argb(color: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (color and 0xffffff)

// ─── 贴纸形状（SHAPE_PATHS）：Canvas 2D 路径 → android Path。整圆 arc 用 addCircle（2D 的 moveTo + 整圆 arc 等价于一个独立子路径）。 ───

private fun pathHeart(p: Path, c: Float, r: Float) {
    p.moveTo(c, c + r * 0.85f)
    p.cubicTo(c - r * 1.25f, c + r * 0.05f, c - r * 0.95f, c - r * 0.95f, c, c - r * 0.42f)
    p.cubicTo(c + r * 0.95f, c - r * 0.95f, c + r * 1.25f, c + r * 0.05f, c, c + r * 0.85f)
}

private fun pathStar(p: Path, c: Float, r: Float, points: Int, inner: Float, rotation: Double = -PI / 2) {
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) r else r * inner
        val angle = rotation + (i * PI) / points
        val x = c + cos(angle).toFloat() * radius
        val y = c + sin(angle).toFloat() * radius
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
}

private fun pathSparkle(p: Path, c: Float, r: Float) {
    val pinch = r * 0.16f
    p.moveTo(c, c - r)
    p.quadTo(c + pinch, c - pinch, c + r, c)
    p.quadTo(c + pinch, c + pinch, c, c + r)
    p.quadTo(c - pinch, c + pinch, c - r, c)
    p.quadTo(c - pinch, c - pinch, c, c - r)
    p.close()
}

private fun pathFlower(p: Path, c: Float, r: Float) {
    for (i in 0 until 5) {
        val angle = -PI / 2 + (i * PI * 2) / 5
        p.addCircle(c + cos(angle).toFloat() * r * 0.55f, c + sin(angle).toFloat() * r * 0.55f, r * 0.42f, Path.Direction.CW)
    }
}

private fun pathCloud(p: Path, c: Float, r: Float) {
    val bumps = arrayOf(floatArrayOf(-0.5f, 0.15f, 0.38f), floatArrayOf(-0.12f, -0.18f, 0.48f), floatArrayOf(0.38f, -0.02f, 0.42f), floatArrayOf(0.62f, 0.25f, 0.28f), floatArrayOf(0.05f, 0.3f, 0.4f))
    for (b in bumps) p.addCircle(c + b[0] * r, c + b[1] * r, b[2] * r, Path.Direction.CW)
}

private fun pathRibbon(p: Path, c: Float, r: Float) {
    p.moveTo(c, c)
    p.cubicTo(c - r * 0.5f, c - r * 0.75f, c - r * 1.05f, c - r * 0.55f, c - r * 0.95f, c)
    p.cubicTo(c - r * 1.05f, c + r * 0.55f, c - r * 0.5f, c + r * 0.75f, c, c)
    p.cubicTo(c + r * 0.5f, c - r * 0.75f, c + r * 1.05f, c - r * 0.55f, c + r * 0.95f, c)
    p.cubicTo(c + r * 1.05f, c + r * 0.55f, c + r * 0.5f, c + r * 0.75f, c, c)
    p.addCircle(c, c, r * 0.24f, Path.Direction.CW)
}

private fun pathCat(p: Path, c: Float, r: Float) {
    p.addCircle(c, c + r * 0.12f, r * 0.78f, Path.Direction.CW)
    p.moveTo(c - r * 0.72f, c - r * 0.2f); p.lineTo(c - r * 0.62f, c - r * 0.95f); p.lineTo(c - r * 0.12f, c - r * 0.6f); p.close()
    p.moveTo(c + r * 0.72f, c - r * 0.2f); p.lineTo(c + r * 0.62f, c - r * 0.95f); p.lineTo(c + r * 0.12f, c - r * 0.6f); p.close()
}

private fun pathBubble(p: Path, c: Float, r: Float) {
    val w = r * 1.0f
    val h = r * 0.72f
    val top = c - h * 0.95f
    val radius = h * 0.55f
    p.addRoundRect(RectF(c - w, top, c + w, top + h * 1.6f), radius, radius, Path.Direction.CW)
    p.moveTo(c - w * 0.35f, top + h * 1.55f)
    p.lineTo(c - w * 0.62f, top + h * 2.15f)
    p.lineTo(c - w * 0.02f, top + h * 1.55f)
    p.close()
}

private fun shapePath(shape: String, p: Path, c: Float, r: Float) = when (shape) {
    "heart" -> pathHeart(p, c, r)
    "star" -> pathStar(p, c, r, 5, 0.48f)
    "sparkle" -> pathSparkle(p, c, r)
    "flower" -> pathFlower(p, c, r)
    "cloud" -> pathCloud(p, c, r)
    "ribbon" -> pathRibbon(p, c, r)
    "cat" -> pathCat(p, c, r)
    else -> pathBubble(p, c, r)
}

/**
 * lucide 图标（24 单位）：只内置花火的默认图标 Flower（主题图标手机端暂不接）。
 * 原 SVG：circle(12,12,3)；四片花瓣 = 圆心 (7.5,16.5) (7.5,7.5) (16.5,7.5) (16.5,16.5)、半径 4.5 的 270° 圆弧首尾相接；八条短线。
 */
private fun lucideFlower(): Path {
    val p = Path()
    p.addCircle(12f, 12f, 3f, Path.Direction.CW)
    p.moveTo(12f, 16.5f)
    val petals = arrayOf(floatArrayOf(7.5f, 16.5f, 0f), floatArrayOf(7.5f, 7.5f, 90f), floatArrayOf(16.5f, 7.5f, 180f), floatArrayOf(16.5f, 16.5f, 270f))
    for (q in petals) p.arcTo(RectF(q[0] - 4.5f, q[1] - 4.5f, q[0] + 4.5f, q[1] + 4.5f), q[2], 270f, false)
    val lines = floatArrayOf(12f, 7.5f, 12f, 9f, 7.5f, 12f, 9f, 12f, 16.5f, 12f, 15f, 12f, 12f, 16.5f, 12f, 15f,
        8f, 8f, 9.88f, 9.88f, 14.12f, 9.88f, 16f, 8f, 8f, 16f, 9.88f, 14.12f, 14.12f, 14.12f, 16f, 16f)
    var k = 0
    while (k < lines.size) { p.moveTo(lines[k], lines[k + 1]); p.lineTo(lines[k + 2], lines[k + 3]); k += 4 }
    return p
}

/**
 * createCollageTextureCache：每个场景单元一份（贴纸、色带组合在一段里会重复）。
 * scale = 纹理按几倍画（Folia 是渲染倍率 1..2；这里画布坐标已是设备像素，纹理边长按逻辑尺寸 × px，scale 固定 1）。
 * px = Folia 逻辑像素（CSS px）→ 本画布像素：tape / pattern / 网点这些按绝对尺寸画的纹理乘它。
 */
internal class HbCollageTextures(private val px: Float, private val typeface: Typeface) {
    private val cache = HashMap<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clear = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    private fun canvasOf(width: Float, height: Float): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(max(1, ceil(width).toInt()), max(1, ceil(height).toInt()), Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private inline fun cached(key: String, paintIt: () -> Bitmap) = cache[key] ?: paintIt().also { cache[key] = it }

    private fun popPaint(size: Float) = paint.apply { reset(); isAntiAlias = true; typeface = this@HbCollageTextures.typeface; textSize = size; textAlign = Paint.Align.CENTER }
    private fun middle(p: Paint, y: Float): Float { val fm = p.fontMetrics; return y - (fm.ascent + fm.descent) / 2 }

    /** 贴纸：形状 + 一圈粗白边 + 偏移的浅阴影 + 左上高光（猫加五官、对话框加字）。边长 STICKER_PX × px。 */
    fun sticker(shape: String, color: Int, label: String?): Bitmap = cached("sticker:$shape:$color:${label ?: ""}") {
        val side = (HB_STICKER_PX * px).toFloat()
        val (b, cv) = canvasOf(side, side)
        val c = side / 2; val r = side * 0.34f
        val path = Path(); shapePath(shape, path, c, r)
        val p = paint; p.reset(); p.isAntiAlias = true; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        cv.save(); cv.translate(side * 0.035f, side * 0.05f)
        p.color = argb(0x1e1030, 0.28); p.style = Paint.Style.STROKE; p.strokeWidth = side * 0.13f; cv.drawPath(path, p)
        p.style = Paint.Style.FILL; cv.drawPath(path, p)
        cv.restore()
        p.color = 0xffffffff.toInt(); p.style = Paint.Style.STROKE; cv.drawPath(path, p); p.style = Paint.Style.FILL; cv.drawPath(path, p)
        p.color = argb(color); cv.drawPath(path, p)
        cv.save(); cv.clipPath(path)
        p.color = argb(0xffffff, 0.4)
        cv.save(); cv.translate(c - r * 0.35f, c - r * 0.4f); cv.rotate(-0.6f * DEGF); cv.drawOval(RectF(-r * 0.28f, -r * 0.16f, r * 0.28f, r * 0.16f), p); cv.restore()
        cv.restore()
        if (shape == "cat") {
            p.color = argb(HB_CANDY_INK)
            cv.drawCircle(c - r * 0.3f, c + r * 0.08f, r * 0.08f, p); cv.drawCircle(c + r * 0.3f, c + r * 0.08f, r * 0.08f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.07f
            val mouth = Path()
            val a0 = (0.1 * DEGF).toFloat(); val sw = ((PI - 0.2) * DEGF).toFloat()
            mouth.arcTo(RectF(c - r * 0.18f, c + r * 0.19f, c, c + r * 0.37f), a0, sw, true)
            mouth.arcTo(RectF(c, c + r * 0.19f, c + r * 0.18f, c + r * 0.37f), a0, sw, false)
            cv.drawPath(mouth, p)
            p.style = Paint.Style.FILL; p.color = argb(0xff78a0, 0.55)
            cv.drawOval(RectF(c - r * 0.62f, c + r * 0.23f, c - r * 0.38f, c + r * 0.37f), p)
            cv.drawOval(RectF(c + r * 0.38f, c + r * 0.23f, c + r * 0.62f, c + r * 0.37f), p)
        }
        if (label != null) {
            var size = r * 0.62f
            val tp = popPaint(size)
            while (size > 8 * px && tp.measureText(label) > r * 1.7f) { size -= px; tp.textSize = size }
            tp.color = argb(HB_CANDY_INK)
            cv.drawText(label, c, middle(tp, c - r * 0.08f), tp)
        }
        b
    }

    /** 胶带：两端锯齿、半透明，条纹 / 圆点 / 格子。逻辑 160 × 48。 */
    fun tape(color: Int, pattern: String): Bitmap = cached("tape:$color:$pattern") {
        val length = 160f * px; val width = 48f * px; val tooth = 6f * px
        val (b, cv) = canvasOf(length, width)
        val shape = Path(); shape.moveTo(0f, 0f)
        var y = 0f; var k = 0
        while (y <= width + 0.01f) { shape.lineTo(if (k % 2 == 0) 0f else tooth * 0.7f, y); y += tooth; k++ }
        shape.lineTo(length, width)
        k = ((width / tooth).roundToInt()); y = width
        while (y >= -0.01f) { shape.lineTo(if (k % 2 == 0) length else length - tooth * 0.7f, y); y -= tooth; k-- }
        shape.close()
        val p = paint; p.reset(); p.isAntiAlias = true
        p.color = argb(color, 0.78); cv.drawPath(shape, p)
        cv.save(); cv.clipPath(shape)
        when (pattern) {
            "stripe" -> {
                p.color = argb(0xffffff, 0.55); p.style = Paint.Style.STROKE; p.strokeWidth = 7 * px
                var x = -width; while (x < length + width) { cv.drawLine(x, width, x + width, 0f, p); x += 18 * px }
            }
            "dot" -> {
                p.color = argb(0xffffff, 0.65)
                var yy = 8; while (yy < 48) { var x = if ((yy / 16) % 2 == 0) 8 else 16; while (x < 160) { cv.drawCircle(x * px, yy * px, 3.2f * px, p); x += 16 }; yy += 16 }
            }
            else -> {
                p.color = argb(0xffffff, 0.38)
                var yy = 0; while (yy < 48) { var x = if ((yy / 12) % 2 == 0) 0 else 12; while (x < 160) { cv.drawRect(x * px, yy * px, (x + 12) * px, (yy + 12) * px, p); x += 24 }; yy += 12 }
            }
        }
        cv.restore()
        b
    }

    /** 纸片花纹小格（白，平铺）：grid / dots / gingham。逻辑 24。 */
    fun pattern(kind: String): Bitmap = cached("pattern:$kind") {
        val tile = 24f * px
        val (b, cv) = canvasOf(tile, tile)
        val p = paint; p.reset(); p.isAntiAlias = true; p.color = 0xffffffff.toInt()
        when (kind) {
            "grid" -> { p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * px; cv.drawLine(0f, 0.6f * px, tile, 0.6f * px, p); cv.drawLine(0.6f * px, 0f, 0.6f * px, tile, p) }
            "dots" -> { cv.drawCircle(tile / 4, tile / 4, 2.4f * px, p); cv.drawCircle(tile * 3 / 4, tile * 3 / 4, 2.4f * px, p) }
            else -> { p.alpha = (0.55 * 255).roundToInt(); val pa = Path(); pa.addRect(0f, 0f, tile / 2, tile, Path.Direction.CW); pa.addRect(0f, 0f, tile, tile / 2, Path.Direction.CW); pa.fillType = Path.FillType.WINDING; cv.drawPath(pa, p) }
        }
        b
    }

    /** 网点底纹的小格（unitCollage paintDotTile）。 */
    fun dotTile(): Bitmap = cached("dot-tile") {
        val tile = (HB_DOT_TILE * px).toFloat()
        val (b, cv) = canvasOf(tile, tile)
        val p = paint; p.reset(); p.isAntiAlias = true; p.color = 0xffffffff.toInt()
        cv.drawCircle(tile / 4, tile / 4, 1.6f * px, p); cv.drawCircle(tile * 3 / 4, tile * 3 / 4, 1.6f * px, p)
        b
    }

    private fun paintFilm(color: Int, height: Float): Bitmap {
        val frame = height * 1.18f
        val width = (frame * 3).roundToInt().toFloat()
        val (b, cv) = canvasOf(width, height)
        val p = paint; p.reset(); p.isAntiAlias = true
        p.color = argb(HB_CANDY_INK, 0.92); cv.drawRect(0f, 0f, width, height, p)
        val pitch = frame / 4; val holeW = pitch * 0.48f; val holeH = height * 0.11f
        p.xfermode = clear; p.color = 0xff000000.toInt()
        for (k in 0 until 12) {
            val x = k * pitch + (pitch - holeW) / 2
            for (y in floatArrayOf(height * 0.07f, height * 0.93f - holeH)) cv.drawRoundRect(RectF(x, y, x + holeW, y + holeH), holeH * 0.3f, holeH * 0.3f, p)
        }
        p.xfermode = null
        val base = max(0, HB_CANDY.indexOf(color))
        val colors = intArrayOf(color, HB_CANDY[(base + 2) % HB_CANDY.size], HB_CANDY[(base + 4) % HB_CANDY.size])
        val icons = arrayOf("heart", "star", "sparkle")
        for (k in 0 until 3) {
            val x = k * frame + frame * 0.07f; val y = height * 0.25f; val w = frame * 0.86f; val h = height * 0.5f
            p.color = argb(colors[k], 0.92); cv.drawRoundRect(RectF(x, y, x + w, y + h), h * 0.12f, h * 0.12f, p)
            p.color = argb(0xffffff, 0.28); cv.drawRoundRect(RectF(x + w * 0.06f, y + h * 0.1f, x + w * 0.46f, y + h * 0.26f), h * 0.08f, h * 0.08f, p)
            p.color = argb(0xffffff, 0.9)
            cv.save(); cv.translate(x + w / 2, y + h / 2)
            val path = Path(); shapePath(icons[k], path, 0f, h * 0.24f); cv.drawPath(path, p)
            cv.restore()
        }
        return b
    }

    private fun paintCut(color: Int, height: Float): Bitmap {
        val period = max(10f * px, height * 0.9f)
        val (b, cv) = canvasOf(period, height)
        val line = max(2f * px, height * 0.11f)
        val dash = period * 0.55f
        val p = paint; p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = line
        p.color = argb(0x1e1030, 0.35); cv.drawLine(line, height / 2 + line * 0.6f, line + dash - line, height / 2 + line * 0.6f, p)
        p.color = argb(color); cv.drawLine(line, height / 2, line + dash - line, height / 2, p)
        return b
    }

    private fun paintLace(color: Int, height: Float): Bitmap {
        val scallop = max(3f * px, height * 0.21f)
        val width = scallop * 4
        val (b, cv) = canvasOf(width, height)
        val top = height * 0.25f; val bottom = height * 0.75f
        val centers = floatArrayOf(scallop, scallop * 3)
        fun shape(dy: Float): Path {
            val s = Path()
            s.addRect(0f, top + dy, width, bottom + dy, Path.Direction.CW)
            for (cx in centers) {
                s.moveTo(cx + scallop, top + dy)
                s.arcTo(RectF(cx - scallop, top + dy - scallop, cx + scallop, top + dy + scallop), 0f, -180f, false)
                s.moveTo(cx - scallop, bottom + dy)
                s.arcTo(RectF(cx - scallop, bottom + dy - scallop, cx + scallop, bottom + dy + scallop), 180f, -180f, false)
            }
            return s
        }
        val p = paint; p.reset(); p.isAntiAlias = true
        p.color = argb(0x1e1030, 0.22); cv.drawPath(shape(height * 0.05f), p)
        p.color = argb(0xffffff, 0.96); cv.drawPath(shape(0f), p)
        p.xfermode = clear; p.color = 0xff000000.toInt()
        for (cx in centers) {
            for ((y, radius) in listOf(top - scallop * 0.45f to scallop * 0.3f, bottom + scallop * 0.45f to scallop * 0.3f, top + height * 0.075f to height * 0.035f, bottom - height * 0.075f to height * 0.035f))
                cv.drawCircle(cx, y, max(0.8f * px, radius), p)
        }
        p.xfermode = null
        val ribbonTop = height * 0.4f; val ribbonHeight = height * 0.2f
        p.color = argb(color); cv.drawRect(0f, ribbonTop, width, ribbonTop + ribbonHeight, p)
        p.color = argb(0xffffff, 0.45); cv.drawRect(0f, ribbonTop + ribbonHeight * 0.18f, width, ribbonTop + ribbonHeight * 0.18f + max(px, ribbonHeight * 0.16f), p)
        p.color = argb(HB_CANDY_INK, 0.16); cv.drawRect(0f, ribbonTop + ribbonHeight * 0.86f, width, ribbonTop + ribbonHeight * 0.86f + max(px, ribbonHeight * 0.14f), p)
        return b
    }

    /** 装饰带沿长度平铺的一节（高 = 带子粗细）。 */
    fun strip(style: String, color: Int, thickness: Double): Bitmap {
        val height = max(10f * px, thickness.roundToInt().toFloat())
        return cached("strip:$style:$color:$height") {
            when (style) { "film" -> paintFilm(color, height); "cut" -> paintCut(color, height); else -> paintLace(color, height) }
        }
    }

    /** 剪刀（尖朝 +x），边长约线粗 2.3 倍。 */
    fun scissors(thickness: Double): Bitmap {
        val side = max(20f * px, (thickness * 2.3).roundToInt().toFloat())
        return cached("scissors:$side") {
            val (b, cv) = canvasOf(side, side)
            val c = side / 2; val ring = side * 0.15f
            val p = paint; p.reset(); p.isAntiAlias = true; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
            for (dir in intArrayOf(-1, 1)) {
                val s = Path()
                s.moveTo(c - side * 0.12f, c + dir * side * 0.1f)
                s.quadTo(c + side * 0.15f, c - dir * side * 0.02f, c + side * 0.46f, c - dir * side * 0.06f)
                s.quadTo(c + side * 0.12f, c + dir * side * 0.08f, c - side * 0.12f, c + dir * side * 0.16f)
                s.close()
                p.style = Paint.Style.STROKE; p.strokeWidth = side * 0.09f; p.color = argb(HB_CANDY_INK); cv.drawPath(s, p)
                p.style = Paint.Style.FILL; p.color = 0xffffffff.toInt(); cv.drawPath(s, p)
            }
            p.style = Paint.Style.STROKE
            for (dir in intArrayOf(-1, 1)) {
                p.strokeWidth = side * 0.1f; p.color = argb(HB_CANDY_INK); cv.drawCircle(c - side * 0.28f, c + dir * side * 0.2f, ring, p)
                p.strokeWidth = side * 0.06f; p.color = argb(HB_CANDY[0]); cv.drawCircle(c - side * 0.28f, c + dir * side * 0.2f, ring, p)
            }
            p.style = Paint.Style.FILL; p.color = argb(HB_CANDY_INK); cv.drawCircle(c - side * 0.06f, c, side * 0.04f, p)
            b
        }
    }

    /** 重复字的一个单元：空心描边字 + 间隔。 */
    fun echo(text: String, color: Int, size: Double, axis: String): Bitmap {
        val fpx = max(10f * px, size.roundToInt().toFloat())
        return cached("echo:$text:$color:$fpx:$axis") {
            val tp = popPaint(fpx)
            val textWidth = tp.measureText(text)
            val gapX = if (axis == "x") fpx * 0.6f else fpx * 0.2f
            val width = ceil(textWidth + gapX * 2)
            val height = ceil(fpx * (if (axis == "x") 1.25f else 1.3f))
            val (b, cv) = canvasOf(width, height)
            tp.style = Paint.Style.STROKE; tp.strokeJoin = Paint.Join.ROUND; tp.strokeWidth = max(1.5f * px, fpx * 0.045f); tp.color = argb(color)
            cv.drawText(text, width / 2, middle(tp, height * 0.52f), tp)
            b
        }
    }

    /** 粗字贴纸（开场标题卡曲名）：硬阴影 → 白边 → 彩色字 → 深色细描边。 */
    fun sound(text: String, color: Int, size: Double): Bitmap {
        val fpx = max(12f * px, size.roundToInt().toFloat())
        return cached("sound:$text:$color:$fpx") {
            val tp = popPaint(fpx)
            val stroke = fpx * 0.2f; val shadow = fpx * 0.09f
            val width = ceil(tp.measureText(text) + stroke * 2 + shadow + fpx * 0.2f)
            val height = ceil(fpx * 1.35f + stroke * 2 + shadow)
            val (b, cv) = canvasOf(width, height)
            val x = width / 2 - shadow / 2; val y = height / 2 - shadow / 2
            tp.strokeJoin = Paint.Join.ROUND
            tp.style = Paint.Style.STROKE; tp.strokeWidth = stroke * 2
            tp.color = argb(0x2b1f3d, 0.9); cv.drawText(text, x + shadow, middle(tp, y + shadow), tp)
            tp.color = 0xffffffff.toInt(); cv.drawText(text, x, middle(tp, y), tp)
            tp.style = Paint.Style.FILL; tp.color = argb(color); cv.drawText(text, x, middle(tp, y), tp)
            tp.style = Paint.Style.STROKE; tp.strokeWidth = max(px, fpx * 0.04f); tp.color = argb(0x2b1f3d, 0.85); cv.drawText(text, x, middle(tp, y), tp)
            b
        }
    }

    private val flower by lazy { lucideFlower() }

    /** 图标贴纸（lucide Flower）：浅阴影 → 粗白边 → 彩色描线。 */
    fun icon(@Suppress("UNUSED_PARAMETER") name: String, color: Int, size: Double): Bitmap {
        val side = max(16f * px, size.roundToInt().toFloat())
        return cached("icon:$color:$side") {
            val (b, cv) = canvasOf(side, side)
            val pad = side * 0.16f
            val scale = (side - pad * 2) / 24f
            cv.translate(pad, pad); cv.scale(scale, scale)
            val p = paint; p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
            fun draw(c: Int, w: Float, dx: Float = 0f, dy: Float = 0f) { cv.save(); cv.translate(dx, dy); p.color = c; p.strokeWidth = w; cv.drawPath(flower, p); cv.restore() }
            draw(argb(0x1e1030, 0.3), 7.2f, 0.7f, 1f)
            draw(0xffffffff.toInt(), 7.2f)
            draw(argb(color), 2.8f)
            b
        }
    }

    /** 标签：圆角胶囊里一行小字。 */
    fun label(text: String, color: Int, size: Double): Bitmap {
        val fpx = max(10f * px, size.roundToInt().toFloat())
        return cached("label:$text:$color:$fpx") {
            val tp = popPaint(fpx)
            val padX = fpx * 0.7f
            val height = ceil(fpx * 1.7f)
            val width = ceil(tp.measureText(text) + padX * 2)
            val (b, cv) = canvasOf(width + 4 * px, height + 4 * px)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val o = 3 * px; val i = 2 * px
            p.color = argb(0x2b1f3d, 0.85); cv.drawRoundRect(RectF(o, o, o + width, o + height), height / 2, height / 2, p)
            p.color = 0xffffffff.toInt(); cv.drawRoundRect(RectF(0f, 0f, width, height), height / 2, height / 2, p)
            p.color = argb(color); cv.drawRoundRect(RectF(i, i, width - i, height - i), (height - 2 * i) / 2, (height - 2 * i) / 2, p)
            tp.color = argb(0x2b1f3d)
            cv.drawText(text, width / 2, middle(tp, height * 0.54f), tp)
            b
        }
    }

    fun release() { cache.values.forEach { it.recycle() }; cache.clear() }
}

/** collageViews.ts / unitCollage.ts 的显示：一个场景单元一份（纹理缓存跟着单元走）。 */
internal class HbCollageDrawer(px: Float) {
    private val typeface: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 800, false) else Typeface.DEFAULT_BOLD
    val tex = HbCollageTextures(px, typeface)
    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tiled = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaders = java.util.IdentityHashMap<Bitmap, BitmapShader>()
    private val m = Matrix()
    private val rect = RectF()
    private val pose = HbCollagePose()

    private fun a255(a: Double) = (a.coerceIn(0.0, 1.0) * 255).roundToInt()

    private fun shaderOf(b: Bitmap) = shaders.getOrPut(b) { BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    /** 平铺精灵：在 (x, y, w, h) 里铺 bitmap，纹理原点 = 左上 + tile 偏移（Pixi TilingSprite 的 tilePosition）。 */
    private fun tile(c: Canvas, b: Bitmap, x: Float, y: Float, w: Float, h: Float, tx: Float, ty: Float, color: Int, alpha: Double) {
        if (w <= 0f || h <= 0f) return
        val s = shaderOf(b)
        m.setTranslate(x + tx, y + ty)
        s.setLocalMatrix(m)
        tiled.shader = s
        tiled.color = color or (0xff shl 24)
        tiled.alpha = a255(alpha)
        if (color != 0xffffff) tiled.colorFilter = android.graphics.PorterDuffColorFilter(color or (0xff shl 24), PorterDuff.Mode.MULTIPLY) else tiled.colorFilter = null
        c.drawRect(x, y, x + w, y + h, tiled)
        tiled.shader = null; tiled.colorFilter = null
    }

    /** 居中贴一张纹理，边长 = 纹理边长 × s。 */
    private fun spriteAt(c: Canvas, b: Bitmap, x: Float, y: Float, sx: Float, sy: Float, rot: Float, alpha: Double, ax: Float = 0.5f, ay: Float = 0.5f) {
        if (alpha <= 0.002 || sx == 0f || sy == 0f) return
        c.save(); c.translate(x, y); if (rot != 0f) c.rotate(rot * DEGF); c.scale(sx, sy)
        sprite.alpha = a255(alpha)
        c.drawBitmap(b, -b.width * ax, -b.height * ay, sprite)
        c.restore()
    }

    private fun rectFill(c: Canvas, color: Int, alpha: Double, x: Float, y: Float, w: Float, h: Float) {
        fill.color = color or (0xff shl 24); fill.alpha = a255(alpha)
        c.drawRect(x, y, x + max(0f, w), y + max(0f, h), fill)
    }

    /** 一个镜头的拼贴：back = 字后面那层，front = 字前面那层（collageViews.ts 的 back / front 容器）。 */
    fun drawShot(c: Canvas, items: List<HbCollageItem>, time: Double, front: Boolean, alphaMul: Double) {
        for (item in items) {
            val isFront = when (item.kind) { "sticker" -> item.layer != "back"; "tape", "label", "pop", "whoosh", "floater" -> true; else -> false }
            if (isFront != front) continue
            draw(c, item, time, alphaMul)
        }
    }

    private fun draw(c: Canvas, item: HbCollageItem, time: Double, am: Double) {
        when (item.kind) {
            "strip" -> {
                val band = tex.strip(item.style, item.color, item.thickness)
                val p = hbCollagePose(item, time, pose, band.width.toDouble())
                if (!p.visible) return
                val th = item.thickness.toFloat()
                c.save(); c.translate(item.x.toFloat(), item.y.toFloat()); c.rotate(item.rotation.toFloat() * DEGF)
                val x0 = p.bandX0.toFloat(); val w = p.bandWidth.toFloat()
                if (item.style == "film") { val lift = th * 0.14f; rectFill(c, HB_CANDY_INK, 0.3 * am, x0 + lift, -th / 2 + lift * 1.3f, w, th) }
                tile(c, band, x0, -th / 2, w, th, p.tileX.toFloat(), 0f, 0xffffff, am)
                if (p.bladeVisible) {
                    val blade = tex.scissors(item.thickness)
                    spriteAt(c, blade, p.bladeX.toFloat(), 0f, p.bladeSx.toFloat(), p.bladeSy.toFloat(), 0f, p.bladeAlpha * am)
                }
                c.restore()
            }
            "echo" -> {
                val t = tex.echo(item.text, item.color, item.size, item.axis)
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val w = if (item.axis == "x") item.width.toFloat() else t.width.toFloat()
                val h = if (item.axis == "x") t.height.toFloat() else item.height.toFloat()
                c.save(); c.translate(item.x.toFloat(), item.y.toFloat()); c.rotate(item.rotation.toFloat() * DEGF); c.scale(p.sx.toFloat(), p.sy.toFloat())
                tile(c, t, -w / 2, -h / 2, w, h, p.tileX.toFloat(), p.tileY.toFloat(), 0xffffff, p.alpha * am)
                c.restore()
            }
            "patch" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val a = p.alpha * am
                val hw = (item.width / 2).toFloat(); val hh = (item.height / 2).toFloat()
                val border = max(3f, (min(item.width, item.height) * 0.025).toFloat())
                c.save(); c.translate(item.x.toFloat(), p.y.toFloat()); c.rotate(p.rotation.toFloat() * DEGF); c.scale(p.sx.toFloat(), p.sy.toFloat())
                rectFill(c, HB_CANDY_INK, (if (item.dark) 0.35 else 0.3) * a, -hw - border + border * 2.2f, -hh - border + border * 2.6f, item.width.toFloat() + border * 2, item.height.toFloat() + border * 2)
                rectFill(c, 0xffffff, (if (item.dark) 0.9 else 1.0) * a, -hw - border, -hh - border, item.width.toFloat() + border * 2, item.height.toFloat() + border * 2)
                rectFill(c, item.color, (if (item.dark) 0.86 else 1.0) * a, -hw, -hh, item.width.toFloat(), item.height.toFloat())
                if (item.pattern != "plain") tile(c, tex.pattern(item.pattern), -hw, -hh, item.width.toFloat(), item.height.toFloat(), 0f, 0f, 0xffffff, (if (item.dark) 0.16 else 0.45) * a)
                c.restore()
            }
            "window" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val a = p.alpha * am
                val hw = (item.width / 2).toFloat(); val hh = (item.height / 2).toFloat(); val bar = item.bar.toFloat()
                val border = max(2.5f, bar * 0.12f); val offset = bar * 0.32f
                c.save(); c.translate(item.x.toFloat(), item.y.toFloat()); c.rotate(item.rotation.toFloat() * DEGF); c.scale(p.sx.toFloat(), p.sy.toFloat())
                rectFill(c, HB_CANDY[0], 0.85 * a, -hw + offset, -hh + offset, item.width.toFloat(), item.height.toFloat())
                rectFill(c, 0xffffff, a, -hw - border, -hh - border, item.width.toFloat() + border * 2, item.height.toFloat() + border * 2)
                rectFill(c, 0x1d1838, 0.62 * a, -hw, -hh + bar, item.width.toFloat(), item.height.toFloat() - bar)
                val titleColor = HB_CANDY[abs(floor(item.x + item.y + 0.5).toInt()) % HB_CANDY.size]
                rectFill(c, titleColor, a, -hw, -hh, item.width.toFloat(), bar)
                intArrayOf(0xff8fb5, 0xffe27a, 0x8ff0c8).forEachIndexed { k, col ->
                    fill.color = col or (0xff shl 24); fill.alpha = a255(a)
                    c.drawCircle(hw - bar * (0.55f + k * 0.6f), -hh + bar / 2, bar * 0.21f, fill)
                }
                val title = tex.label(item.title, titleColor, item.bar * 0.48)
                spriteAt(c, title, -hw + bar * 0.25f, -hh + bar / 2, 0.92f, 0.92f, 0f, a, 0f, 0.5f)
                c.restore()
            }
            "sticker" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val b = tex.sticker(item.shape, item.color, item.label)
                // 纹理边长 = STICKER_PX × px：姿态里的缩放按 STICKER_PX 算，这里换回纹理像素。
                val k = (HB_STICKER_PX / b.width).toFloat()
                spriteAt(c, b, item.x.toFloat(), p.y.toFloat(), p.sx.toFloat() * k, p.sy.toFloat() * k, p.rotation.toFloat(), am)
            }
            "floater" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val b = tex.sticker(item.shape, item.color, null)
                val k = (HB_STICKER_PX / b.width).toFloat()
                spriteAt(c, b, p.x.toFloat(), p.y.toFloat(), p.sx.toFloat() * k, p.sy.toFloat() * k, p.rotation.toFloat(), p.alpha * am)
            }
            "tape" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                val b = tex.tape(item.color, item.pattern)
                val sx = (item.length / b.width).toFloat(); val sy = (item.width / b.height).toFloat()
                spriteAt(c, b, item.x.toFloat(), item.y.toFloat(), sx * p.sx.toFloat(), sy * p.sy.toFloat(), item.rotation.toFloat(), p.alpha * am)
            }
            "label" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                spriteAt(c, tex.label(item.text, item.color, item.size), item.x.toFloat(), item.y.toFloat(), p.sx.toFloat(), p.sy.toFloat(), item.rotation.toFloat(), am)
            }
            "headline" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                spriteAt(c, tex.sound(item.text, item.color, item.size), item.x.toFloat(), p.y.toFloat(), p.sx.toFloat(), p.sy.toFloat(), p.rotation.toFloat(), am)
            }
            "pop", "whoosh" -> {
                val p = hbCollagePose(item, time, pose)
                if (!p.visible) return
                spriteAt(c, tex.icon(item.icon, item.color, item.size), p.x.toFloat(), p.y.toFloat(), p.sx.toFloat(), p.sy.toFloat(), p.rotation.toFloat(), p.alpha * am)
            }
        }
    }

    /** unitCollage.back：满屏网点底纹（paper 色，按 rise 视差）。画布已在 stage 坐标、左上为原点。 */
    fun drawUnitBack(c: Canvas, u: HbUnitCollage, rise: Double, alphaMul: Double) {
        val w = u.width.toFloat(); val h = u.height.toFloat()
        tile(c, tex.dotTile(), -w * 0.1f, -h * 0.1f, w * 1.2f, h * 1.2f, 0f, u.dotsOffsetY(rise).toFloat(), u.paper, u.dotAlpha * alphaMul)
    }

    /** unitCollage.front：左缘竖排胶片，跟着世界往下走。 */
    fun drawUnitFront(c: Canvas, u: HbUnitCollage, time: Double, rise: Double, alphaMul: Double) {
        val band = tex.strip("film", u.railColor, u.railThickness)
        val len = u.railLength.toFloat(); val th = u.railThickness.toFloat()
        c.save(); c.translate(u.railX.toFloat(), u.railY.toFloat()); c.rotate(-90f)
        tile(c, band, -len / 2, -th / 2, len, th, u.railTileX(time, rise, band.width.toDouble()).toFloat(), 0f, 0xffffff, u.railAlpha * alphaMul)
        c.restore()
    }

    fun release() { tex.release(); shaders.clear() }
}
