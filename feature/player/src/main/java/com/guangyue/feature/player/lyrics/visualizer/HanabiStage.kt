// 对应 Folia 文件：src/components/visualizer/hanabi/（花火）——hanabiProgram.ts（整首编译：段落→镜头，前奏 / 段后长空隙
// 出间奏镜头，一串スターマイン 一路升空）、hanabiSky.ts（夜空四色标：上深靛、中堇、近地平线一抹桃紫，色相往主题强调色偏 0.3）、
// mg/burstPlan.ts（烟花解析模型：p(τ) = p0 + v0·(1−e^{−kτ})/k + vt·(τ − (1−e^{−kτ})/k)，尾迹沿轨迹往回取点）、
// mg/fireworks.ts（升空尾迹 + 星 + 闪光）、color.ts（祭 festival 调色：朱红 / 瑠璃 / 柿色色块，金红青绿白火花）、
// text/lyricText.ts + text/blocks.ts + text/animations.ts（逐字精灵：字 + 硬阴影 + 字底色块，rise 入场、被唱弹跳变色、ascend 退场）。
//
// P7「花火」手机版：不做 Pixi 精灵池 / 拼贴 MG 图库 / 副歌全屏辉光 / 片尾卡。保留：夜空渐变与星点、每句开唱放一朵（菊 / 牡丹 /
// 柳 / 輪 / 千輪 轮换），长空隙里一串スターマイン；字底色块三种（逐字方块 tile、整行色带 band、斜带 slant），逐字升入、
// 唱到的字弹一下染金、换句时整行向上飞走。烟花全由时刻算出（闭式解），seek 到哪一刻画面都一样。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ───────────────────────────── 程序（纯函数，可单测） ─────────────────────────────

/** 烟花的种类（Folia BurstKind 里挑手机画得动的五种）。 */
internal enum class HanabiKind(val life: Double) {
    /** 菊：带尾迹的球状放射，末段转白，内有一小圈芯。 */
    KIKU(2.2),
    /** 牡丹：无尾迹的饱满色点，中途变色。 */
    BOTAN(1.7),
    /** 柳：长尾迹、重阻尼，金色垂下。 */
    YANAGI(3.4),
    /** 輪：一圈环（略透视成椭圆）。 */
    RING(1.9),
    /** 千輪：先散出几颗子弹，再各自开一朵小花。 */
    SENRIN(2.6),
}

/** 一发烟花：开花时刻、开花点（占宽 / 高比例）、发射点 x、种类、主副色序号、大小、种子。 */
internal data class HanabiBurst(
    val at: Double,
    val x: Float,
    val y: Float,
    val launchX: Float,
    val kind: HanabiKind,
    val color: Int,
    val color2: Int,
    val scale: Float,
    val seed: Int,
    /** 间奏里的スターマイン（小一些、密一些）。 */
    val bridge: Boolean,
)

internal object HanabiTiming {
    /** 升空用时（秒）：开花前这么久从底边发射。 */
    const val LAUNCH_S = 0.9
    /** 空隙长过这么多秒就铺间奏镜头（Folia bridgeGap 的手机取值）。 */
    const val BRIDGE_GAP_S = 3.2
    /** 间奏里スターマイン的间隔。 */
    const val BRIDGE_STEP_S = 0.75
    /** 字入场：开唱前多早开始升入、每字错开、单字入场时长。 */
    const val ENTER_LEAD_S = 0.38
    const val ENTER_STAGGER_S = 0.03
    const val ENTER_S = 0.32
    /** 换句时上一句飞走的时长；长空隙里唱完再挂这么久才飞走。 */
    const val EXIT_S = 0.55
    const val HOLD_S = 1.4
}

internal fun hanabiHash(s: String): Int {
    var h = 0x811C9DC5.toInt()
    s.forEach { h = (h xor it.code) * 0x01000193 }
    return h
}

private fun unit01(seed: Int, salt: Int): Float {
    var x = seed * 0x27d4eb2d + salt * 0x165667b1
    x = x xor (x ushr 15); x *= 0x2c1b3c6d; x = x xor (x ushr 12)
    return ((x ushr 8) and 0xffff) / 65535f
}

/** 整首编译：每句开唱一朵（长句中段再补一朵），前奏 / 段间长空隙 / 尾奏铺一串スターマイン。种类相邻不重复。 */
internal fun compileHanabi(lines: List<Line>, songEnd: Double? = null): List<HanabiBurst> {
    if (lines.isEmpty()) return emptyList()
    val out = ArrayList<HanabiBurst>()
    val kinds = HanabiKind.values().toList()
    var prevKind: HanabiKind? = null
    var side = 0
    fun add(at: Double, tag: String, bridge: Boolean) {
        val seed = hanabiHash("$tag@${(at * 100).toInt()}")
        var kind = kinds[Math.floorMod(seed, kinds.size)]
        if (bridge && kind == HanabiKind.YANAGI && unit01(seed, 9) < 0.5f) kind = HanabiKind.KIKU
        if (kind == prevKind) kind = kinds[(kind.ordinal + 1) % kinds.size]
        prevKind = kind
        side = 1 - side
        // 左右交替落点，避免两朵叠在一起；高度在上部 16%–40%。
        val x = if (side == 0) 0.2f + unit01(seed, 1) * 0.3f else 0.5f + unit01(seed, 1) * 0.3f
        val y = 0.16f + unit01(seed, 2) * (if (bridge) 0.3f else 0.22f)
        val c = Math.floorMod(seed ushr 3, 5)
        out += HanabiBurst(
            at = at, x = x, y = y,
            launchX = (x + (unit01(seed, 3) - 0.5f) * 0.16f).coerceIn(0.08f, 0.92f),
            kind = kind, color = c, color2 = (c + 1 + Math.floorMod(seed ushr 7, 4)) % 5,
            scale = if (bridge) 0.62f + unit01(seed, 4) * 0.3f else 0.9f + unit01(seed, 4) * 0.25f,
            seed = seed, bridge = bridge,
        )
    }
    fun bridge(from: Double, to: Double, tag: String) {
        if (to - from < HanabiTiming.BRIDGE_GAP_S) return
        var t = from + 0.4
        var k = 0
        while (t < to - 0.6) {
            add(t, "$tag#$k", bridge = true)
            // 节奏忽快忽慢：两快一慢。
            t += HanabiTiming.BRIDGE_STEP_S * (if (k % 3 == 2) 1.6 else 0.8)
            k++
        }
    }
    val first = lines.first().startTime
    if (first >= HanabiTiming.LAUNCH_S + 1.5) bridge(HanabiTiming.LAUNCH_S, first - 0.2, "intro")
    lines.forEachIndexed { i, line ->
        val start = max(line.startTime, HanabiTiming.LAUNCH_S)
        add(start, "line$i:${line.fullText}", bridge = false)
        if (line.endTime - line.startTime > 4.5) add((line.startTime + line.endTime) / 2, "mid$i", bridge = false)
        val next = lines.getOrNull(i + 1)?.startTime ?: max(songEnd ?: 0.0, line.endTime + 8.0)
        bridge(line.endTime + 0.3, next - 0.2, "gap$i")
    }
    out.sortBy { it.at }
    return out
}

/** 此刻该显示的句子：开唱前 ENTER_LEAD_S 起算，最后一个已开始的句子；都没开始时为 -1。 */
internal fun hanabiLineAt(lines: List<Line>, t: Double): Int {
    var idx = -1
    lines.forEachIndexed { i, l -> if (l.startTime - HanabiTiming.ENTER_LEAD_S <= t) idx = i }
    return idx
}

/** 第 [i] 句开始飞走的时刻：下一句开唱前一点；后面是长空隙则唱完再挂 HOLD_S。 */
internal fun hanabiExitAt(lines: List<Line>, i: Int): Double {
    val l = lines[i]
    val next = lines.getOrNull(i + 1)?.startTime
    val hold = l.endTime + HanabiTiming.HOLD_S
    return if (next == null) hold else min(next - HanabiTiming.ENTER_LEAD_S * 0.5, max(hold, l.endTime))
}

// ───────────────────────────── 排版 ─────────────────────────────

private enum class HanabiBlock { TILE, BAND, SLANT }

private class HanabiGlyph(val layout: TextLayoutResult, val cx: Float, val cy: Float, val w: Float, val start: Double, val order: Int, val row: Int)

private class HanabiLineLayout(
    val glyphs: List<HanabiGlyph>,
    val size: Float,
    val block: HanabiBlock,
    val colorA: Color,
    val colorB: Color,
    /** 每行的左、右、中心 y。 */
    val rows: List<Triple<Float, Float, Float>>,
)

private val OnePx = androidx.compose.ui.unit.Density(1f, 1f)

private fun layoutHanabiLine(line: Line, li: Int, w: Float, cy: Float, sMax: Float, sMin: Float, measurer: TextMeasurer, base: TextStyle): HanabiLineLayout {
    val units = nebbiaUnits(line)
    val seed = hanabiHash("hl$li:${line.fullText}")
    val block = HanabiBlock.values()[Math.floorMod(seed, 3)]
    val pairs = listOf(0xFFE60033 to 0xFF1E50A2, 0xFF1E50A2 to 0xFFD9480F, 0xFFD9480F to 0xFFE60033)
    val (a, b) = pairs[Math.floorMod(seed ushr 4, 3)]
    if (units.isEmpty()) return HanabiLineLayout(emptyList(), sMax, block, Color(a), Color(b), emptyList())
    val ref = 100f
    val refStyle = base.copy(fontSize = ref.sp)
    val widths = units.map { (u, _) -> measurer.measure(u, refStyle, density = OnePx).size.width.toFloat() }
    // 逐字方块要字距，斜带要词间隔（Folia blockLayoutParams：tile tracking 0.14 / minAdvance 0.86，slant wordGap 0.72 的收敛版）。
    val track = when (block) { HanabiBlock.TILE -> 0.2f; HanabiBlock.BAND -> 0.06f; HanabiBlock.SLANT -> 0.1f }
    val maxW = w * 0.84f
    var s = sMax
    var rows: List<List<Int>>
    while (true) {
        val r = ArrayList<MutableList<Int>>(); var cur = ArrayList<Int>(); var cw = 0f
        units.indices.forEach { k ->
            val uw = max(widths[k] / ref * s, if (block == HanabiBlock.TILE) s * 0.86f else 0f)
            val add = if (cur.isEmpty()) uw else uw + s * track
            if (cur.isNotEmpty() && cw + add > maxW) { r += cur; cur = ArrayList(); cw = 0f }
            cw += if (cur.isEmpty()) uw else uw + s * track
            cur += k
        }
        if (cur.isNotEmpty()) r += cur
        rows = r
        if (rows.size <= 3 || s <= sMin) break
        s = max(sMin, s * 0.9f)
    }
    val style = base.copy(fontSize = s.sp)
    val lineH = s * 1.5f
    val top = cy - rows.size * lineH / 2f
    val glyphs = ArrayList<HanabiGlyph>()
    val rowBoxes = ArrayList<Triple<Float, Float, Float>>()
    var order = 0
    rows.forEachIndexed { ri, row ->
        val ws = row.map { max(widths[it] / ref * s, if (block == HanabiBlock.TILE) s * 0.86f else 0f) }
        val rowW = ws.sum() + (row.size - 1) * s * track
        // 行与行错开一点（PV 字幕的阶梯感）。
        val shift = if (rows.size > 1) (ri - (rows.size - 1) / 2f) * s * 0.5f else 0f
        var x = (w - rowW) / 2f + shift
        val y = top + lineH * (ri + 0.5f)
        rowBoxes += Triple(x, x + rowW, y)
        row.forEachIndexed { k, idx ->
            val (txt, t0) = units[idx]
            glyphs += HanabiGlyph(measurer.measure(txt, style, density = OnePx), x + ws[k] / 2f, y, ws[k], t0, order++, ri)
            x += ws[k] + s * track
        }
    }
    return HanabiLineLayout(glyphs, s, block, Color(a), Color(b), rowBoxes)
}

// ───────────────────────────── 画 ─────────────────────────────

/** 祭 festival 的火花色（Folia color.ts）+ 主题强调色。 */
private val HanabiSparks = listOf(Color(0xFFFFCC1A), Color(0xFFFF3355), Color(0xFF3CC8FF), Color(0xFF6DFF9A), Color(0xFFFFFFFF))
private val HanabiInk = Color(0xFF0B0D24)
private val HanabiGold = Color(0xFFFFC400)

private fun hueOf(c: Color): Pair<Float, Float> {
    val mx = max(c.red, max(c.green, c.blue)); val mn = min(c.red, min(c.green, c.blue))
    if (mx == mn) return 0f to 0f
    val d = mx - mn
    val l = (mx + mn) / 2f
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) { c.red -> (c.green - c.blue) / d + (if (c.green < c.blue) 6f else 0f); c.green -> (c.blue - c.red) / d + 2f; else -> (c.red - c.green) / d + 4f }
    return h / 6f to s
}

/** 夜空四色标（Folia resolveHanabiSkyStops）：色相沿最短方向往强调色偏 0.3；强调色是灰时不偏。 */
internal fun hanabiSky(accent: Color): List<Color> {
    val (hue, sat) = hueOf(accent)
    fun lean(base: Float): Float {
        if (sat < 0.2f) return base
        var d = hue - base
        if (d > 0.5f) d -= 1f
        if (d < -0.5f) d += 1f
        return base + d * 0.3f
    }
    fun hsl(h: Float, s: Float, l: Float) = Color.hsl((((h % 1f) + 1f) % 1f) * 360f, s, l)
    return listOf(hsl(lean(0.66f), 0.62f, 0.06f), hsl(lean(0.7f), 0.5f, 0.11f), hsl(lean(0.8f), 0.48f, 0.19f), hsl(lean(0.9f), 0.55f, 0.27f))
}

/** 闭式解：阻尼 k、终端下落速度 vt 时，τ 秒后的位移。 */
private fun disp(v: Float, k: Float, tau: Float) = v * (1f - exp(-k * tau)) / k
private fun fallOf(vt: Float, k: Float, tau: Float) = vt * (tau - (1f - exp(-k * tau)) / k)

private fun DrawScope.drawStars(cx: Float, cy: Float, r: Float, tau: Float, life: Float, n: Int, seed: Int, kind: HanabiKind, c1: Color, c2: Color) {
    val k = when (kind) { HanabiKind.YANAGI -> 2.2f; HanabiKind.BOTAN -> 3.6f; else -> 3.2f }
    val vt = when (kind) { HanabiKind.YANAGI -> r * 0.85f; HanabiKind.BOTAN -> r * 0.22f; else -> r * 0.3f }
    val trail = when (kind) { HanabiKind.YANAGI -> 0.55f; HanabiKind.KIKU -> 0.16f; HanabiKind.RING -> 0.08f; else -> 0f }
    val p = (tau / life).coerceIn(0f, 1f)
    val fade = (1f - p).pow(1.4f)
    val dot = r * (if (kind == HanabiKind.BOTAN) 0.034f else 0.024f)
    for (i in 0 until n) {
        // 球面上均匀取方向再投影到屏幕（斐波那契球）：屏上看是中间密边上疏的立体感。
        val (dx, dy) = if (kind == HanabiKind.RING) {
            val a = i / n.toFloat() * 2f * PI.toFloat() + unit01(seed, 11)
            cos(a) to sin(a) * 0.55f
        } else {
            val zz = 1f - 2f * (i + 0.5f) / n
            val rr = sqrt(max(0f, 1f - zz * zz))
            val a = i * 2.39996f + unit01(seed, 12) * 6.283f
            cos(a) * rr to sin(a) * rr
        }
        val jitter = 0.92f + unit01(seed, 100 + i) * 0.16f
        val v = r * k * jitter
        fun at(t: Float) = Offset(cx + disp(dx * v, k, t), cy + disp(dy * v, k, t) + fallOf(vt, k, t))
        val pos = at(tau)
        // 菊末段转白；牡丹中途换副色；柳全程金色。
        val col = when (kind) {
            HanabiKind.KIKU -> androidx.compose.ui.graphics.lerp(c1, Color.White, ((p - 0.55f) / 0.35f).coerceIn(0f, 1f))
            HanabiKind.BOTAN -> if (p < 0.45f + unit01(seed, 200 + i) * 0.1f) c1 else c2
            HanabiKind.YANAGI -> Color(0xFFFFC56B)
            else -> c1
        }
        // 末段闪烁（菊、柳）。
        val tw = if ((kind == HanabiKind.KIKU || kind == HanabiKind.YANAGI) && p > 0.6f) 0.55f + 0.45f * sin(tau * 38f + i * 1.7f) else 1f
        val a = (fade * tw).coerceIn(0f, 1f)
        if (trail > 0f && tau > 0.02f) {
            val back = at(max(0f, tau - trail))
            drawLine(col.copy(alpha = a * 0.45f), back, pos, dot * 0.9f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        drawCircle(col.copy(alpha = a), dot, pos, blendMode = BlendMode.Plus)
    }
    // 菊的芯：一小圈短命的内层星。
    if (kind == HanabiKind.KIKU && p < 0.3f) {
        val cn = n / 5
        for (i in 0 until cn) {
            val a2 = i / cn.toFloat() * 6.283f
            val v = r * 3.6f * 0.42f
            val pos = Offset(cx + disp(cos(a2) * v, 3.6f, tau), cy + disp(sin(a2) * v, 3.6f, tau))
            drawCircle(c2.copy(alpha = 1f - p / 0.3f), dot * 0.8f, pos, blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawBurst(b: HanabiBurst, t: Double, w: Float, h: Float, accent: Color) {
    val tau = (t - b.at).toFloat()
    val palette = HanabiSparks + accent
    val c1 = palette[b.color % palette.size]
    val c2 = palette[b.color2 % palette.size]
    val cx = b.x * w
    val cy = b.y * h
    val r = min(w, h) * 0.24f * b.scale
    if (tau < 0f) {
        // 升空：从底边沿略弯的轨迹减速上升，拖一条火星尾迹。
        val lp = (1f + tau / HanabiTiming.LAUNCH_S.toFloat()).coerceIn(0f, 1f)
        if (lp <= 0f) return
        fun pt(q: Float): Offset {
            val e = 1f - (1f - q).pow(2.2f)
            return Offset(b.launchX * w + (cx - b.launchX * w) * e, h * 1.02f + (cy - h * 1.02f) * e)
        }
        val head = pt(lp)
        val tail = pt(max(0f, lp - 0.18f))
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFD9A0).copy(alpha = 0.8f)), tail, head), tail, head, r * 0.018f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFFF1D6), r * 0.016f, head, blendMode = BlendMode.Plus)
        return
    }
    val life = b.kind.life.toFloat()
    if (tau > life + 0.1f) return
    // 开花闪光。
    if (tau < 0.28f) {
        val fa = (1f - tau / 0.28f) * 0.55f
        drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = fa), Color.White.copy(alpha = fa * 0.4f), Color.Transparent), Offset(cx, cy), r * 0.9f), r * 0.9f, Offset(cx, cy), blendMode = BlendMode.Plus)
    }
    val n = if (b.bridge) 30 else 48
    if (b.kind == HanabiKind.SENRIN) {
        // 千輪：先散出 6 颗子弹（0.5 秒），再各开一朵小牡丹。
        val k = 3.0f
        for (j in 0 until 6) {
            val a = j / 6f * 6.283f + unit01(b.seed, 30)
            val v = r * 0.62f * k
            val sub = Offset(cx + disp(cos(a) * v, k, min(tau, 0.5f)), cy + disp(sin(a) * v, k, min(tau, 0.5f)))
            if (tau < 0.5f) drawCircle(c1.copy(alpha = 0.9f), r * 0.02f, sub, blendMode = BlendMode.Plus)
            else drawStars(sub.x, sub.y, r * 0.34f, tau - 0.5f, life - 0.5f, 14, b.seed + j, HanabiKind.BOTAN, if (j % 2 == 0) c1 else c2, Color.White)
        }
    } else {
        drawStars(cx, cy, r, tau, life, if (b.kind == HanabiKind.RING) 36 else n, b.seed, b.kind, c1, c2)
    }
}

private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val y = x - 1f; return 1f + c3 * y * y * y + c1 * y * y }

private fun DrawScope.drawLyricLine(l: HanabiLineLayout, t: Double, exitAt: Double, lineStart: Double, h: Float, reduceMotion: Boolean) {
    if (l.glyphs.isEmpty()) return
    val s = l.size
    val ex = ((t - exitAt) / HanabiTiming.EXIT_S).toFloat().coerceIn(0f, 1f)
    if (ex >= 1f) return
    val enter0 = lineStart - HanabiTiming.ENTER_LEAD_S
    fun enterOf(order: Int): Float = if (reduceMotion) (if (t >= enter0) 1f else 0f)
        else ((t - enter0 - order * HanabiTiming.ENTER_STAGGER_S) / HanabiTiming.ENTER_S).toFloat().coerceIn(0f, 1f)
    // ascend 退场：整行向上飞走，逐字错开淡出。
    val lift = if (reduceMotion) 0f else ex * ex * h * 0.14f
    val pad = s * 0.18f
    // 1) 字底色块。
    when (l.block) {
        HanabiBlock.TILE -> l.glyphs.forEach { g ->
            val e = enterOf(g.order)
            if (e <= 0f) return@forEach
            val a = (1f - ex).coerceIn(0f, 1f)
            val sc = easeOutBack(e).coerceAtLeast(0f)
            val side = max(g.w, s * 0.86f) + pad
            val c = if (g.order % 2 == 0) l.colorA else l.colorB
            withTransform({ translate(g.cx, g.cy - lift); rotate(if (g.order % 2 == 0) -4f else 3f, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
                drawRect(c.copy(alpha = a), Offset(-side / 2f, -side / 2f), Size(side, side))
            }
        }
        HanabiBlock.BAND, HanabiBlock.SLANT -> l.rows.forEachIndexed { ri, (x0, x1, y) ->
            // 色带从左边拉出，跟着本行第一个字的入场。
            val firstOrder = l.glyphs.firstOrNull { it.row == ri }?.order ?: 0
            val e = enterOf(firstOrder).let { 1f - (1f - it).pow(3f) }
            if (e <= 0f) return@forEachIndexed
            val a = (1f - ex).coerceIn(0f, 1f)
            val bh = s * 1.24f
            val left = x0 - pad * 2f
            val right = left + (x1 + pad * 2f - left) * e
            val c = if (ri % 2 == 0) l.colorA else l.colorB
            val yy = y - lift
            if (l.block == HanabiBlock.BAND) {
                drawRect(c.copy(alpha = a), Offset(left, yy - bh / 2f), Size(right - left, bh))
                drawRect(HanabiGold.copy(alpha = a), Offset(left, yy + bh / 2f - s * 0.08f), Size(right - left, s * 0.08f))
            } else {
                val sk = bh * 0.32f
                val path = Path().apply {
                    moveTo(left + sk, yy - bh / 2f); lineTo(right + sk, yy - bh / 2f)
                    lineTo(right - sk, yy + bh / 2f); lineTo(left - sk, yy + bh / 2f); close()
                }
                drawPath(path, c.copy(alpha = a))
            }
        }
    }
    // 2) 字：硬阴影 + 白字；唱到的字弹一下、染金。
    l.glyphs.forEach { g ->
        val e = enterOf(g.order)
        if (e <= 0f) return@forEach
        val ga = (1f - ((ex - g.order * 0.02f) / 0.7f)).coerceIn(0f, 1f) * min(1f, e * 2f)
        if (ga <= 0f) return@forEach
        // rise 入场：从下方升起，带纵向拉伸，到位时压扁回弹。
        val rise = (1f - easeOutBack(e)) * s * 0.9f
        val stretch = if (e < 0.6f) 1f + (0.6f - e) * 0.5f else 1f - sin((e - 0.6f) / 0.4f * PI.toFloat()) * 0.1f
        val sung = t >= g.start
        val since = (t - g.start).toFloat()
        val bounce = if (sung && !reduceMotion && since < 0.32f) sin(since / 0.32f * PI.toFloat()) * s * 0.16f else 0f
        val swell = if (sung && !reduceMotion && since < 0.32f) 1f + sin(since / 0.32f * PI.toFloat()) * 0.1f else 1f
        val col = if (sung) androidx.compose.ui.graphics.lerp(Color.White, HanabiGold, min(1f, since / 0.12f)) else Color.White
        val tl = Offset(-g.layout.size.width / 2f, -g.layout.size.height / 2f)
        val exLift = if (reduceMotion) 0f else ex * g.order * s * 0.05f
        withTransform({ translate(g.cx, g.cy + rise - bounce - lift - exLift); scale(swell, swell * stretch, Offset.Zero) }) {
            drawText(g.layout, HanabiInk, tl + Offset(s * 0.07f, s * 0.07f), alpha = ga * 0.9f)
            drawText(g.layout, col, tl, alpha = ga)
        }
    }
}

@Composable
internal fun HanabiStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val bursts = remember(lines) { compileHanabi(lines) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val bottomPx = with(density) { bottomReserve.toPx() }
        val sMin = with(density) { 22.sp.toPx() } * lyricsFontScale.toFloat()
        val sMax = min(w * 0.11f, h * 0.064f) * lyricsFontScale.toFloat()
        val style = remember(fontFamily) { TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black) }
        // 歌词放在烟花下面、底部胶囊上面。
        val textY = min(h * 0.64f, h - bottomPx - h * 0.12f)
        val cache = remember(lines, w, h, lyricsFontScale, style) { HashMap<Int, HanabiLineLayout>() }
        val sky = remember(accentColor) { hanabiSky(accentColor) }
        val stars = remember(w, h) { List(110) { Triple(unit01(77, it * 3) * w, unit01(77, it * 3 + 1) * h * 0.72f, unit01(77, it * 3 + 2)) } }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.readForDraw()
            val drift = if (reduceMotion) 0f else t.toFloat()
            // 1) 夜空：四色标竖向渐变 + 星点 + 近地平线的祭典光。
            drawRect(Brush.verticalGradient(0f to sky[0], 0.45f to sky[1], 0.82f to sky[2], 1f to sky[3]))
            stars.forEach { (x, y, k) ->
                val a = (0.25f + 0.5f * k) * (0.7f + 0.3f * sin(drift * (0.8f + k * 2f) + k * 40f))
                drawCircle(Color.White.copy(alpha = a), 0.6f + k * 1.4f, Offset(x, y))
            }
            drawRect(Brush.verticalGradient(0.78f to Color.Transparent, 1f to Color(0xFFFF9E7A).copy(alpha = 0.18f)))
            // 2) 烟花：只画时间窗内的（升空 0.9 秒 + 最长寿命 3.4 秒）。
            bursts.forEach { b ->
                if (t < b.at - HanabiTiming.LAUNCH_S || t > b.at + b.kind.life + 0.1) return@forEach
                drawBurst(b, t, w, h, accentColor)
            }
            // 3) 歌词：当前句 + 正在飞走的上一句。
            if (w <= 0f || h <= 0f) return@Canvas
            val cur = hanabiLineAt(lines, t)
            for (li in max(0, cur - 1)..cur) {
                if (li < 0) continue
                val line = lines[li]
                val layout = cache.getOrPut(li) { layoutHanabiLine(line, li, w, textY, sMax, max(1f, min(sMin, sMax)), measurer, style) }
                drawLyricLine(layout, t, hanabiExitAt(lines, li), line.startTime, h, reduceMotion)
            }
        }
    }
}
