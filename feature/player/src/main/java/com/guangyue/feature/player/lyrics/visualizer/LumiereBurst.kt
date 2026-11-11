// 绘光 ⑤ 十字爆闪：照 Folia src/components/visualizer/lumiere/light/crossBurst.ts 逐函数对译。
// EVA 式行内小十字：按种子挑出一部分字，在字被唱到的一瞬（或行首一口气扫过整行）各冒出一个小十字——
// 白闪、竖直光柱急速拉长、横条展开、小冲击环——0.6 秒内消散。只在光位声明了 burst 的镜头覆盖的行里出现。
// 每一帧的画面只由「引爆后的秒数 age」决定。
//   planBursts → lumierePlanBursts（纯函数，金标单测 LumiereBurstTest）
//   createCrossBurst 的 update → lumiereBurstSprites（每个十字六张贴图的几何，金标单测）+ drawLumiereBursts（画）
//   burstLightBoost → lumiereBurstLightBoost
// 贴图：streak（light/sprites.ts drawStreak：横竖两道线性三角渐变相乘）、dot（drawDot）、bokeh（drawBokeh）。
// 偏离：Folia 十字与字共用文字组 bloom；这里没有文字组离屏 bloom，十字直接画（光柱自带 glow 一层）。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 同时存在的小十字上限。 */
internal const val LUMIERE_MAX_BURSTS = 14
/** 一个小十字持续多久（秒）。 */
internal const val LUMIERE_BURST_DURATION = 0.6
/** 「扫过」方式里相邻两个十字的间隔（秒）。 */
private const val LUMIERE_SWEEP_STEP = 0.07

/** 一行可见字的序号与点亮时刻（LumiereLyricWindow.glyphTimes）。 */
internal class LumiereBurstTrigger(val lineIndex: Int, val glyphIndex: Int, val time: Double, val size: Double, val dy: Double, val color: DoubleArray? = null) {
    fun with(size: Double, color: DoubleArray) = LumiereBurstTrigger(lineIndex, glyphIndex, time, size, dy, color)
}

/** planBursts：按光位的 burst 规则挑出引爆的字与时刻（纯函数，按种子确定）。lines[i] = 第 i 行 (字序号, 点亮时刻)。 */
internal fun lumierePlanBursts(spec: LumiereBurstSpec, lines: List<List<Pair<Int, Double>>>, seed: String): List<LumiereBurstTrigger> {
    val triggers = ArrayList<LumiereBurstTrigger>()
    lines.forEachIndexed { lineIndex, glyphs ->
        if (lineIndex < spec.offset || (lineIndex - spec.offset) % max(1, spec.every) != 0) return@forEachIndexed
        val rng = lumiereRng("$seed:burst:$lineIndex")
        val chosen = glyphs.filter { rng.next() < spec.density }.toMutableList()
        // 至少一个：密度低、行又短时也要有。
        if (chosen.isEmpty() && glyphs.isNotEmpty()) chosen += glyphs[floor(rng.next() * glyphs.size).toInt()]
        val sweepStart = glyphs.firstOrNull()?.second ?: 0.0
        chosen.forEachIndexed { order, glyph ->
            val size = 0.7 + rng.next() * 0.6
            val dy = (rng.next() - 0.5) * 0.9
            triggers += LumiereBurstTrigger(lineIndex, glyph.first, if (spec.mode == "sweep") sweepStart + order * LUMIERE_SWEEP_STEP else glyph.second, size, dy)
        }
    }
    return triggers.sortedBy { it.time }
}

/** 一个小十字给光场的提亮（多个叠加时由调用方封顶 0.45）。 */
internal fun lumiereBurstLightBoost(age: Double) = if (age < 0) 0.0 else 0.18 * exp(-age / 0.1)

/** 正在进行的一个爆闪：引爆后秒数、爆点（dp）、竖向光柱长度（dp）、光色。 */
internal class LumiereBurstEvent(val age: Double, val x: Double, val y: Double, val length: Double, val color: DoubleArray)

/** 一张贴图：texture 0 = streak，1 = dot，2 = bokeh；位置相对爆点，rotation 弧度，tint 0xRRGGBB。 */
internal class LumiereBurstSprite(val texture: Int, val x: Double, val y: Double, val width: Double, val height: Double, val alpha: Double, val tint: Int, val rotation: Double)

private fun lumiereClamp01b(v: Double) = min(1.0, max(0.0, v))
private fun lumiereEaseOutExpo(v: Double): Double { val t = lumiereClamp01b(v); return if (t >= 1) 1.0 else 1 - 2.0.pow(-10 * t) }
private fun lumiereEaseOutCubic(v: Double) = 1 - (1 - lumiereClamp01b(v)).pow(3)

/**
 * createCrossBurst 的 update（一个十字）：按 Pixi 子节点顺序给出 ring、竖 glow、横 glow、竖 core、横 core、白闪六张。
 * 超出 [0, BURST_DURATION] 返回空。
 */
internal fun lumiereBurstSprites(event: LumiereBurstEvent): List<LumiereBurstSprite> {
    val age = event.age
    if (age < 0 || age > LUMIERE_BURST_DURATION) return emptyList()
    val l = event.length
    val glow = lumiereHexOf(event.color)
    val core = lumiereHexOf(lumiereMixRgb(event.color, LUMIERE_WHITE, 0.75))
    // 30ms 点亮，之后快速衰减（0.16s 常数），0.6s 内基本消失。
    val envelope = lumiereClamp01b(age / 0.03) * exp(-max(0.0, age - 0.03) / 0.16)
    val spread = 1 + age * 2
    // 竖直光柱：从字脚下 0.3L 到字上方 0.7L（拉丁十字），80ms 内拉满。
    val vLen = max(1.0, l * lumiereEaseOutExpo(age / 0.08))
    // 横条：晚 20ms，100ms 内展开，交点在光柱上段。
    val hLen = max(1.0, l * 0.62 * lumiereEaseOutExpo((age - 0.02) / 0.1))
    val hGate = lumiereClamp01b((age - 0.02) / 0.03)
    val ring = lumiereEaseOutCubic(age / 0.4)
    val ringSize = l * 0.9 * ring
    val flashSize = l * (0.35 + age * 0.8)
    return listOf(
        LumiereBurstSprite(2, 0.0, -l * 0.42, ringSize, ringSize, 0.35 * (1 - ring) * lumiereClamp01b(age / 0.03), glow, 0.0),
        LumiereBurstSprite(0, 0.0, -l * 0.2, vLen, l * 0.1 * spread, 0.6 * envelope, glow, PI / 2),
        LumiereBurstSprite(0, 0.0, -l * 0.42, hLen, l * 0.09 * spread, 0.55 * envelope * hGate, glow, 0.0),
        LumiereBurstSprite(0, 0.0, -l * 0.2, vLen, l * 0.02 * spread, 1.0 * envelope, core, PI / 2),
        LumiereBurstSprite(0, 0.0, -l * 0.42, hLen, l * 0.018 * spread, 0.95 * envelope * hGate, core, 0.0),
        LumiereBurstSprite(1, 0.0, -l * 0.42, flashSize, flashSize, exp(-age / 0.07), core, 0.0),
    )
}

/**
 * scene.ts 315–329 行：每个声明了 burst 的镜头，只在它覆盖的行里按 `${单元种子}:${镜头序号}` 播种挑字；
 * 尺寸乘 spec.size，颜色 = mixRgb(光色, spec.tint, 0.85)（广乐主题没有 wordColors，关键字色分支不走）。
 */
internal fun lumiereSceneBursts(shots: List<LumiereSceneShot>, lineCount: Int, glyphTimes: (Int) -> List<Pair<Int, Double>>, light: DoubleArray, seed: String): List<LumiereBurstTrigger> =
    shots.flatMapIndexed<LumiereSceneShot, LumiereBurstTrigger> { shotIndex, shot ->
        val spec = shot.profile.burst ?: return@flatMapIndexed emptyList<LumiereBurstTrigger>()
        val covered = shot.lines.toSet()
        val color = lumiereMixRgb(light, spec.tint, 0.85)
        lumierePlanBursts(spec, List(lineCount) { if (it in covered) glyphTimes(it) else emptyList() }, "$seed:$shotIndex").map { it.with(it.size * spec.size, color) }
    }.sortedBy { it.time }

/** scene.ts update 里的爆闪部分：正在进行的事件（最多 MAX_BURSTS 个）与给光场的提亮（封顶 0.45）。 */
internal fun lumiereActiveBursts(triggers: List<LumiereBurstTrigger>, time: Double, anchor: (Int, Int) -> Triple<Double, Double, Double>): Pair<List<LumiereBurstEvent>, Double> {
    val events = ArrayList<LumiereBurstEvent>()
    var boost = 0.0
    for (t in triggers) {
        val age = time - t.time
        if (age < 0 || age > LUMIERE_BURST_DURATION) continue
        boost += lumiereBurstLightBoost(age)
        if (events.size < LUMIERE_MAX_BURSTS) {
            val (x, y, fontPx) = anchor(t.lineIndex, t.glyphIndex)
            events += LumiereBurstEvent(age, x, y + fontPx * t.dy, fontPx * t.size, t.color ?: LUMIERE_WHITE)
        }
    }
    return events to min(boost, 0.45)
}

/** 只算提亮（光场要在画十字之前定，避免为了提亮先算字位置）。 */
internal fun lumiereBurstBoost(triggers: List<LumiereBurstTrigger>, time: Double): Double {
    var boost = 0.0
    for (t in triggers) { val age = time - t.time; if (age in 0.0..LUMIERE_BURST_DURATION) boost += lumiereBurstLightBoost(age) }
    return min(boost, 0.45)
}

/** light/sprites.ts drawStreak(512, 32)：横向 0→1→0 线性渐变，再 destination-in 竖向 0→1→0，即两道三角相乘。缩成 128×16。 */
internal fun lumiereStreakBitmap(): Bitmap {
    val w = 128; val h = 16
    val px = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val tx = 1 - kotlin.math.abs((x + 0.5) / w * 2 - 1)
        val ty = 1 - kotlin.math.abs((y + 0.5) / h * 2 - 1)
        px[y * w + x] = ((tx * ty * 255).toInt().coerceIn(0, 255) shl 24) or 0xffffff
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private val burstRect = RectF()
private val burstAdd = android.graphics.PorterDuffXfermode(PorterDuff.Mode.ADD)
/** 光晕层：透明度、放大倍数、细线（光芒）加粗倍数。 */
private const val BURST_HALO_ALPHA = 0.42
private const val BURST_HALO_WIDE = 1.35
private const val BURST_HALO_THICK = 4.0

/** 画一批十字（字的下面）。textures = [streak, dot, bokeh]。layerAlpha = 段落转场透明度。 */
internal fun drawLumiereBursts(nc: android.graphics.Canvas, events: List<LumiereBurstEvent>, textures: Array<Bitmap>, paint: Paint, layerAlpha: Float) {
    for (e in events) {
        val sprites = lumiereBurstSprites(e)
        // 爆闪的光晕：Folia 里爆闪挂在文字组下，跟字共用文字组 bloom（LUMIERE_BLOOM.text：strength 1.15、threshold 0.12、5 级）。
        // 这里不做整层 bloom，按字的做法近似：每个精灵先用柔光点贴图放大加色画一层（宽 × HALO_WIDE、细线加粗 × HALO_THICK），再画本体。
        paint.xfermode = burstAdd
        for (s in sprites) {
            val a = s.alpha * layerAlpha * BURST_HALO_ALPHA
            if (a <= 0.003 || s.width <= 0 || s.height <= 0) continue
            paint.colorFilter = PorterDuffColorFilter((0xff shl 24) or s.tint, PorterDuff.Mode.MULTIPLY)
            paint.alpha = (a.coerceIn(0.0, 1.0) * 255).toInt()
            val cx = (e.x + s.x).toFloat(); val cy = (e.y + s.y).toFloat()
            val hw = (s.width / 2 * BURST_HALO_WIDE).toFloat()
            val hh = (max(s.height / 2 * BURST_HALO_WIDE, min(s.width, s.height) / 2 * BURST_HALO_THICK)).toFloat()
            burstRect.set(cx - hw, cy - hh, cx + hw, cy + hh)
            val tex = textures[if (s.texture == 0) 0 else 1]
            if (s.rotation != 0.0) { nc.save(); nc.rotate((s.rotation * 180 / PI).toFloat(), cx, cy); nc.drawBitmap(tex, null, burstRect, paint); nc.restore() }
            else nc.drawBitmap(tex, null, burstRect, paint)
        }
        paint.xfermode = null
        for (s in sprites) {
            val a = s.alpha * layerAlpha
            if (a <= 0.003 || s.width <= 0 || s.height <= 0) continue
            paint.colorFilter = PorterDuffColorFilter((0xff shl 24) or s.tint, PorterDuff.Mode.MULTIPLY)
            paint.alpha = (a.coerceIn(0.0, 1.0) * 255).toInt()
            val cx = (e.x + s.x).toFloat(); val cy = (e.y + s.y).toFloat()
            val hw = (s.width / 2).toFloat(); val hh = (s.height / 2).toFloat()
            burstRect.set(cx - hw, cy - hh, cx + hw, cy + hh)
            if (s.rotation != 0.0) { nc.save(); nc.rotate((s.rotation * 180 / PI).toFloat(), cx, cy); nc.drawBitmap(textures[s.texture], null, burstRect, paint); nc.restore() }
            else nc.drawBitmap(textures[s.texture], null, burstRect, paint)
        }
    }
    paint.colorFilter = null
}
