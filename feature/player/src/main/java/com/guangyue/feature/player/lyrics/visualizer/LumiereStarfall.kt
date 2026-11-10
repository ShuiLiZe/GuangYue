// 绘光 ⑥ 星空开场：照 Folia src/components/visualizer/lumiere/light/starfall.ts 逐函数对译。
// 镜头开始时全黑，数百个光点从画面顶部上方倾泻而下（带拖尾，落定前减速），落定的一瞬闪一下，之后留在原处成为闪烁的星空（上密下疏）；
// 主光柱在倾泻到一半左右点亮（LumiereUnitModel.ignition）。开场之后还有持续的光雨：稀疏的光点沿光柱附近落下，进了光束更亮。
// 全部是 (种子, t) 的闭式函数。纯逻辑（金标单测 LumiereStarfallTest）；画法 drawLumiereStarfall（dot / star / streak 贴图）。
// 场景接法（scene.ts 239–256、459 行）：领头光位有 starfall 就建；能开场（单元 ≥ 2 秒且是开场段）时 opening 取
// min(opening, max(0.8, 单元时长 × 0.4))；不开场的单元星空直接落定（local 加 opening + 20）。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** 一帧里的一颗（点或拖尾）：位置（dp）、宽高（dp）、透明度。visible=false 的不画。 */
internal class LumiereStarSprite {
    var x = 0.0
    var y = 0.0
    var width = 0.0
    var height = 0.0
    var alpha = 0.0
    var visible = false
}

/** createStarfall：width / height 为舞台 dp，seed = 单元种子（内部再加 `:starfall`）。 */
internal class LumiereStarfall(width: Double, private val height: Double, seed: String, val spec: LumiereStarfallSpec) {
    private val aspect = width / height
    private class Star(val x: Double, val y: Double, val fromY: Double, val delay: Double, val fall: Double, val size: Double, val bright: Boolean, val twinklePhase: Double, val twinkleFreq: Double)
    private class Drop(val x: Double, val period: Double, val phase: Double, val size: Double, val sway: Double)

    private val stars: List<Star>
    private val drops: List<Drop>
    /** 点：先星（bright 用十字星贴图）后雨滴，与 Folia points 容器的顺序一致。 */
    val points: List<LumiereStarSprite>
    /** 拖尾：与 points 一一对应（Folia trails 容器在 points 之下）。 */
    val trails: List<LumiereStarSprite>
    /** points[i] 用 star 贴图（否则 dot）。 */
    val brightAt: BooleanArray

    init {
        val rng = lumiereRng("$seed:starfall")
        stars = List(spec.stars) {
            val bright = rng.next() < 0.08
            val x = rng.next() * aspect
            val y = 0.02 + rng.next().pow(1.7) * 0.9
            val fromY = -0.05 - rng.next() * 0.25
            val delay = spec.opening * (0.02 + 0.7 * rng.next().pow(1.4))
            val fall = 0.45 + rng.next() * 0.7
            val size = if (bright) 0.018 + rng.next() * 0.02 else 0.003 + rng.next() * 0.006
            val twinklePhase = rng.next() * PI * 2
            val twinkleFreq = 0.4 + rng.next() * 1.8
            Star(x, y, fromY, delay, fall, size, bright, twinklePhase, twinkleFreq)
        }
        drops = List(spec.rain) {
            // 横向：集中在画面中线（光柱）附近，近似正态。
            val spread = (rng.next() + rng.next() + rng.next() - 1.5) / 1.5
            val x = aspect * (0.5 + spread * spec.rainSpread * 0.5)
            val period = (1.25 / max(spec.rainSpeed, 0.01)) * (0.7 + rng.next() * 0.6)
            val phase = rng.next()
            val size = 0.003 + rng.next() * 0.005
            val sway = (rng.next() - 0.5) * 0.02
            Drop(x, period, phase, size, sway)
        }
        points = List(stars.size + drops.size) { LumiereStarSprite() }
        trails = List(stars.size + drops.size) { LumiereStarSprite() }
        brightAt = BooleanArray(points.size) { it < stars.size && stars[it].bright }
    }

    private fun place(i: Int, x: Double, y: Double, size: Double, alpha: Double, speed: Double) {
        val s = points[i]; val t = trails[i]
        s.visible = alpha > 0.004
        t.visible = s.visible && speed > 0.05
        if (!s.visible) return
        s.x = x * height; s.y = y * height
        s.width = size * height; s.height = size * height
        s.alpha = alpha
        if (t.visible) {
            // 拖尾长度随速度，朝上（锚点在尾端 = 光点处）：x、y 是尾端（光点处），width 是向上的长度，height 是粗细。
            t.x = x * height; t.y = y * height
            t.width = min(0.25, speed * 0.09) * height
            t.height = max(1.5, size * height * 0.7)
            t.alpha = alpha * 0.6
        }
    }

    /** local：镜头开始后的秒数（开场按它算）；time：绝对时刻（闪烁、光雨按它算）。 */
    fun update(local: Double, time: Double, beams: List<LumiereResolvedBeam>, fade: Double) {
        stars.forEachIndexed { i, star ->
            val progress = (local - star.delay) / star.fall
            if (progress <= 0) { points[i].visible = false; trails[i].visible = false; return@forEachIndexed }
            val eased = 1 - (1 - min(1.0, max(0.0, progress))).pow(3)
            val y = star.fromY + (star.y - star.fromY) * eased
            // 下落速度（高度单位 / 秒）：easeOutCubic 的导数。
            val speed = if (progress < 1) ((star.y - star.fromY) * 3 * (1 - progress).pow(2)) / star.fall else 0.0
            val landedAt = star.delay + star.fall
            val flash = if (local >= landedAt) exp(-(local - landedAt) / 0.25) else 0.0
            val twinkle = 0.55 + 0.45 * sin(time * star.twinkleFreq * PI * 2 + star.twinklePhase)
            val lit = lumiereCompressLight(lumiereLightAt(beams, star.x, y))
            val base = if (progress < 1) 0.9 else (if (star.bright) 0.7 else 0.45) * twinkle
            val alpha = min(1.0, max(0.0, (base + lit * 0.8 + flash) * spec.brightness * fade))
            place(i, star.x, y, star.size * (1 + flash * 1.5), alpha, speed)
        }
        // 光雨：开场之后才开始，渐强。
        val rainIn = min(1.0, max(0.0, (local - spec.opening * 0.6) / 1.5))
        drops.forEachIndexed { k, drop ->
            val cycle = (time / drop.period + drop.phase) % 1
            val y = -0.1 + cycle * 1.25
            val x = drop.x + sin(time * 0.7 + drop.phase * 6) * drop.sway
            val lit = lumiereCompressLight(lumiereLightAt(beams, x, y))
            val edge = min(1.0, max(0.0, cycle / 0.08)) * min(1.0, max(0.0, (1 - cycle) / 0.15))
            val alpha = min(1.0, max(0.0, (0.18 + lit * 1.1) * edge * rainIn * fade * spec.brightness))
            place(stars.size + k, x, y, drop.size, alpha, 1.25 / drop.period)
        }
    }
}

/** scene.ts 243–256 行：能开场时收短 opening；返回 (实际规格, local 偏移)。没有星空返回 null。 */
internal fun lumiereStarfallSetup(base: LumiereStarfallSpec?, paragraphOpening: Boolean, unitDuration: Double): Pair<LumiereStarfallSpec, Double>? {
    base ?: return null
    val canOpen = paragraphOpening && unitDuration >= 2
    val spec = if (canOpen) LumiereStarfallSpec(base.stars, min(base.opening, max(0.8, unitDuration * 0.4)), base.rain, base.rainSpeed, base.rainSpread, base.brightness) else base
    return spec to (if (canOpen) 0.0 else spec.opening + 20)
}

private val starRect = RectF()

/** 画星空：先拖尾（streak，锚点在右端、转 90° 朝上），再点（dot / star）。tint = 光色。 */
internal fun drawLumiereStarfall(nc: android.graphics.Canvas, sf: LumiereStarfall, dot: Bitmap, star: Bitmap, streak: Bitmap, paint: Paint, color: Int, layerAlpha: Float) {
    paint.colorFilter = PorterDuffColorFilter((0xff shl 24) or (color and 0xffffff), PorterDuff.Mode.MULTIPLY)
    for (t in sf.trails) {
        if (!t.visible) continue
        val a = t.alpha * layerAlpha
        if (a <= 0.004) continue
        paint.alpha = (a.coerceIn(0.0, 1.0) * 255).toInt()
        // 竖直：从光点处向上 width 长，粗 height。按横向矩形（长 width、粗 height）摆在中点再绕中点转 90°。
        val cx = t.x.toFloat(); val cy = (t.y - t.width / 2).toFloat()
        val half = (t.width / 2).toFloat(); val hw = (t.height / 2).toFloat()
        starRect.set(cx - half, cy - hw, cx + half, cy + hw)
        nc.save(); nc.rotate(90f, cx, cy)
        nc.drawBitmap(streak, null, starRect, paint); nc.restore()
    }
    sf.points.forEachIndexed { i, s ->
        if (!s.visible) return@forEachIndexed
        val a = s.alpha * layerAlpha
        if (a <= 0.004) return@forEachIndexed
        paint.alpha = (a.coerceIn(0.0, 1.0) * 255).toInt()
        val r = (s.width / 2).toFloat()
        starRect.set(s.x.toFloat() - r, s.y.toFloat() - r, s.x.toFloat() + r, s.y.toFloat() + r)
        nc.drawBitmap(if (sf.brightAt[i]) star else dot, null, starRect, paint)
    }
    paint.colorFilter = null
}

/** light/sprites.ts drawStar(256)：径向光晕（半径 0.5）+ 四条长芒（0.98r）+ 四条斜短芒（0.42r）。缩成 128。 */
internal fun lumiereFoliaStarBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = size / 2f
    fun a(v: Float) = android.graphics.Color.argb((v * 255).toInt(), 255, 255, 255)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.RadialGradient(r, r, r * 0.5f, intArrayOf(a(1f), a(0.8f), a(0.14f), a(0f)), floatArrayOf(0f, 0.12f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    p.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.ADD)
    fun ray(angle: Float, length: Float, thickness: Float, alpha: Float) {
        c.save(); c.translate(r, r); c.rotate(angle)
        p.shader = android.graphics.LinearGradient(0f, 0f, length, 0f, a(alpha), a(0f), android.graphics.Shader.TileMode.CLAMP)
        val path = android.graphics.Path().apply { moveTo(0f, -thickness); lineTo(length, 0f); lineTo(0f, thickness); close() }
        c.drawPath(path, p); c.restore()
    }
    for (i in 0 until 4) ray(90f * i, r * 0.98f, size * 0.018f, 0.95f)
    for (i in 0 until 4) ray(90f * i + 45f, r * 0.42f, size * 0.01f, 0.45f)
    return bmp
}
