// 绘光背景歌词碎片（lyricEcho）。
// 对应 Folia 文件：src/components/visualizer/lumiere/text/lyricEcho.ts（+ scene.ts 里 createLyricEcho / echo.update 的调用参数）。
// 唱到的词采集成巨大的空心字碎片，在主光束光源附近出现，沿主光束漂下去，约 7 秒淡出；漂的途中字彼此散开、转动、斜切。
// 平时很淡（0.05），光束里被照亮（+0.4 × compressLight(lightAt)）。每帧只由 t 决定。
//
// 随机量的抽取顺序与 Folia 逐项相同（种子 `${单元种子}:echo`，每个词 7 次 + 每个字 3 次）。
// 偏离：
//   1. Folia 把一行先栅格化成空心字画布（描边 + 0.14 透明度填充），再按字切成精灵；这里每帧直接用 Paint 画字
//      （FILL 0.14 + STROKE，描边宽 = 字号 × 0.012），省掉按需栅格化 / 释放（PREPARE）那一套。
//   2. 关键字色（keywords / keywordEchoColor）留到第 3 项接；目前碎片一律用光色。
//   3. Pixi 的 skew(x, 0) 按 Pixi 的矩阵公式（a=cos(r)·sx, b=sin(r)·sx, c=−sin(r−k)·sy, d=cos(r−k)·sy）手算成 Android Matrix。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.GraphemeTimings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** 一个碎片存在多久（秒）、沿光束漂多快（高度单位 / 秒）、从离光源多远的地方出现。 */
private const val ECHO_LIFE = 7.0
private const val ECHO_SPEED = 0.075
private const val ECHO_BIRTH_DISTANCE = 0.08
private const val ECHO_LETTER_SPACING = 0.04
private const val ECHO_OUTLINE = 0.012

internal class LumiereEchoGlyph(val char: String, val blank: Boolean, val dx: Double, val dy: Double, val speed: Double, val spin: Double) {
    /** 字在词里的位置（dp，按最大字号）；首次画到时量。 */
    var offset = Double.NaN
}

internal class LumiereEchoFragment(
    val birth: Double, val across: Double, val speed: Double, val scale: Double, val rotation: Double,
    val stretch: Double, val shear: Double, val spin: Double, val lineIndex: Int, val start: Int, val end: Int,
    val glyphs: List<LumiereEchoGlyph>,
)

/** createLyricEcho 的纯逻辑部分（随机量、碎片表）+ 每帧绘制。size = 最大字号占画面高度，opacity = tuning.echo。 */
internal class LumiereLyricEcho(
    private val width: Double,
    private val height: Double,
    private val lines: List<Line>,
    seed: String,
    size: Double,
    private val opacity: Double = 1.0,
    split: LumiereWordSplitter = defaultSonnetWordSplitter,
) {
    val fontPx = size * height
    val fragments: List<LumiereEchoFragment>

    init {
        val rng = lumiereRng("$seed:echo")
        val out = ArrayList<LumiereEchoFragment>()
        lines.forEachIndexed { lineIndex, line ->
            val graphemes = GraphemeTimings.splitLyricGraphemes(line.fullText)
            val timings = lumiereGlyphTimings(line)
            for (word in lumiereSegmentWords(line, split)) {
                if (word.blank) continue
                val end = min(word.end, graphemes.size)
                if (end <= word.start) continue
                val birth = timings.getOrNull(word.start)?.start ?: line.startTime
                val across = (rng.next() - 0.5) * 2.8
                val speed = 0.7 + rng.next() * 0.7
                val scale = 0.4 + rng.next() * 0.6
                val rotation = (rng.next() - 0.5) * 0.9
                val stretch = 0.75 + rng.next() * 0.7
                val shear = (rng.next() - 0.5) * 0.12
                val spin = (rng.next() - 0.5) * 0.08
                val glyphs = graphemes.subList(word.start, end).map { ch ->
                    val angle = rng.next() * PI * 2
                    val gs = 0.3 + rng.next() * 0.9
                    val gspin = (rng.next() - 0.5) * 0.5
                    LumiereEchoGlyph(ch, ch.trim().isEmpty(), cos(angle), sin(angle), gs, gspin)
                }
                out += LumiereEchoFragment(birth, across, speed, scale, rotation, stretch, shear, spin, lineIndex, word.start, end, glyphs)
            }
        }
        fragments = out
    }

    private val lineSlices = HashMap<Int, List<LumiereGlyphSlice>>()
    private val matrix = Matrix()
    private val values = FloatArray(9)

    private fun ensureOffsets(fragment: LumiereEchoFragment, measure: LumiereTextMeasure) {
        if (!fragment.glyphs.first().offset.isNaN()) return
        val slices = lineSlices.getOrPut(fragment.lineIndex) { lumiereGlyphSlices(lines[fragment.lineIndex].fullText, fontPx, measure, ECHO_LETTER_SPACING) }
        val charX = DoubleArray(slices.size + 1)
        for (i in slices.indices) charX[i + 1] = charX[i] + slices[i].charWidth
        val left = charX[fragment.start]
        val right = charX[min(fragment.end, slices.size)]
        val middle = (left + right) / 2
        fragment.glyphs.forEachIndexed { k, g ->
            val i = fragment.start + k
            val w = slices.getOrNull(i)?.charWidth ?: 0.0
            g.offset = charX[min(i, slices.size)] + w / 2 - middle
        }
    }

    /** echo.update：beams = 当前光束（第一束是主光束），color = 光色 0xRRGGBB，intensity = smooth(local/1.2) × exit。 */
    fun draw(nc: Canvas, time: Double, beams: List<LumiereResolvedBeam>, color: Int, intensity: Double, fill: Paint, stroke: Paint, measure: LumiereTextMeasure) {
        val beam = beams.firstOrNull()
        val ox = beam?.ox ?: (width / height) / 2
        val oy = beam?.oy ?: 0.0
        val bx = beam?.dx ?: 0.0
        val by = beam?.dy ?: 1.0
        fun halfAt(distance: Double) = (if (beam != null) beam.halfWidth + distance * beam.tanSpread else 0.08) + 0.04
        val px = fontPx.toFloat()
        fill.textSize = px; stroke.textSize = px
        fill.textAlign = Paint.Align.CENTER; stroke.textAlign = Paint.Align.CENTER
        stroke.style = Paint.Style.STROKE; stroke.strokeWidth = (fontPx * ECHO_OUTLINE).toFloat()
        fill.style = Paint.Style.FILL
        val fm = fill.fontMetrics
        val baseline = -(fm.ascent + fm.descent) / 2
        for (fragment in fragments) {
            val age = time - fragment.birth
            if (age < 0 || age > ECHO_LIFE) continue
            val presence = lumiereSmooth(age / 0.9) * (1 - lumiereSmooth((age - (ECHO_LIFE - 2.5)) / 2.5))
            val alpha = presence * intensity * opacity
            if (alpha <= 0.003) continue
            ensureOffsets(fragment, measure)
            // 沿光束漂：离光源的距离随时间增长，横向位置按光束在那里的半宽换算（光束摆动时跟着走）。
            val distance = ECHO_BIRTH_DISTANCE + ECHO_SPEED * fragment.speed * age
            val across = fragment.across * halfAt(distance)
            val x = (ox + bx * distance - by * across) * height
            val y = (oy + by * distance + bx * across) * height
            val r = fragment.rotation + fragment.spin * age
            val sx = fragment.scale * fragment.stretch
            val sy = fragment.scale
            val k = fragment.shear * age
            values[0] = (cos(r) * sx).toFloat(); values[1] = (-sin(r - k) * sy).toFloat(); values[2] = x.toFloat()
            values[3] = (sin(r) * sx).toFloat(); values[4] = (cos(r - k) * sy).toFloat(); values[5] = y.toFloat()
            values[6] = 0f; values[7] = 0f; values[8] = 1f
            matrix.setValues(values)
            // 拆开：字彼此散开（按 age^1.3 加速）、各自转动。
            val splitK = 0.05 * age.pow(1.3)
            val lit = lumiereCompressLight(lumiereLightAt(beams, x / height, y / height))
            val glyphAlpha = (alpha * (0.05 + 0.4 * lit)).coerceIn(0.0, 1.0)
            fill.color = (((glyphAlpha * 0.14 * 255).toInt()) shl 24) or (color and 0xffffff)
            stroke.color = (((glyphAlpha * 255).toInt()) shl 24) or (color and 0xffffff)
            nc.save()
            nc.concat(matrix)
            for (g in fragment.glyphs) {
                if (g.blank) continue
                val gx = (g.offset + g.dx * fontPx * splitK * g.speed).toFloat()
                val gy = (g.dy * fontPx * splitK * g.speed).toFloat()
                nc.save()
                nc.translate(gx, gy)
                nc.rotate((g.spin * splitK * 4 * 180 / PI).toFloat())
                nc.drawText(g.char, 0f, baseline, fill)
                nc.drawText(g.char, 0f, baseline, stroke)
                nc.restore()
            }
            nc.restore()
        }
    }
}

