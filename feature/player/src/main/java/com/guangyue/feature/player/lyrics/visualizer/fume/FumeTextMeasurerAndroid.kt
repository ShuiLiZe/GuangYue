// V1a「浮名」(fume) 绘制层：Android 侧的文本测量实现。
//
// 为什么测量必须走 Compose 的 [TextMeasurer]，而不是裸 android.graphics.Paint：
//  - Folia 排版阶段量出来的 advances / glyphOffsets 会被直接当成绘制坐标（segment.x、
//    segment.measuredGlyphOffsets），所以「量」与「画」必须是同一套字体度量；
//  - 本项目的衬线族是打包进来的 Noto Serif SC 子集（GuangYueDesign.Serif = FontFamily(Font(...))），
//    它没有可用的系统字面名。若用 Typeface.create("serif") 去量、用 Compose 的 FontFamily 去画，
//    字形推进宽度会有细微差异，多行 / 多段拼接时会累积成可见错位。
//  - 因此这里把「和绘制同源」的 TextMeasurer 包一层：同 fontFamily、同 FontWeight、同 sp 折算，
//    量出来的宽度与之后 drawText 的落点严格一致。Paint 路径只留作异常兜底。
package com.guangyue.feature.player.lyrics.visualizer.fume

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.guangyue.core.lyrics.fume.FumeTextMeasurer
import com.guangyue.core.lyrics.fume.FumeVariant
import com.guangyue.core.lyrics.fume.fontWeight

/**
 * [FumeTextMeasurer] 的 Android 实现。
 *
 * 带 LRU 缓存（key = fontPx + variant + text），因为排版构建时会按字素反复量同一批前缀文本。
 * 另外暴露 [baselineInsetPx]：Compose 的 `drawText` 以文字盒顶部对齐，而 Folia 的绘制公式给的是
 * baseline（`block.y + renderLine.top + baselineOffset`），绘制层用这个差值把两者对齐。
 */
internal class FumeTextMeasurerAndroid(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val fontFamily: FontFamily,
    private val cacheCapacity: Int = 4096,
) : FumeTextMeasurer {

    /** 单行 layout 的首行 baseline（相对文字盒顶部，px）。同一个字体 + 字号下与文本无关。 */
    private val baselineInsets = HashMap<Int, Float>()

    private val styles = HashMap<Int, TextStyle>()

    private val widthCache = object : LinkedHashMap<String, Double>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>?): Boolean =
            size > cacheCapacity
    }

    /** 兜底用：字体资源还没就绪、TextMeasurer 抛异常时给一个近似宽度。 */
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun measureWidth(text: String, fontPx: Double, variant: FumeVariant): Double {
        if (text.isEmpty() || fontPx <= 0.0 || !fontPx.isFinite()) return 0.0
        val key = "$fontPx|${variant.ordinal}|$text"
        widthCache[key]?.let { return it }
        val width = runCatching {
            measurer.measure(text = text, style = styleFor(fontPx, variant)).size.width.toDouble()
        }.getOrElse { fallbackMeasure(text, fontPx, variant) }
        widthCache[key] = width
        return width
    }

    /**
     * Compose `drawText(topLeft = ...)` 到 Folia baseline 的纵向差值（px）。
     * 绘制层落点 = `baselineY - baselineInsetPx(fontPx, variant)`。
     */
    fun baselineInsetPx(fontPx: Double, variant: FumeVariant): Float {
        val key = styleKey(fontPx, variant)
        baselineInsets[key]?.let { return it }
        val inset = runCatching {
            measurer.measure(text = BASELINE_PROBE, style = styleFor(fontPx, variant)).firstBaseline
        }.getOrElse { fallbackAscent(fontPx, variant) }
        baselineInsets[key] = inset
        return inset
    }

    private fun styleKey(fontPx: Double, variant: FumeVariant): Int =
        Math.round(fontPx * 1000.0).toInt() * 4 + variant.ordinal

    private fun styleFor(fontPx: Double, variant: FumeVariant): TextStyle =
        styles.getOrPut(styleKey(fontPx, variant)) {
            TextStyle(
                fontSize = with(density) { fontPx.toFloat().toSp() },
                fontWeight = FontWeight(variant.fontWeight()),
                fontFamily = fontFamily,
            )
        }

    /** 只做兜底：字体栈异常时按「半角 0.55em / CJK 1em」粗估。 */
    private fun fallbackMeasure(text: String, fontPx: Double, variant: FumeVariant): Double {
        fallbackPaint.textSize = fontPx.toFloat()
        fallbackPaint.typeface = Typeface.create("serif", Typeface.NORMAL)
        fallbackPaint.isFakeBoldText = variant == FumeVariant.HERO
        return fallbackPaint.measureText(text).toDouble()
    }

    private fun fallbackAscent(fontPx: Double, variant: FumeVariant): Float {
        fallbackPaint.textSize = fontPx.toFloat()
        fallbackPaint.typeface = Typeface.create("serif", Typeface.NORMAL)
        return -fallbackPaint.fontMetrics.ascent
    }

    private companion object {
        const val BASELINE_PROBE = "字"
    }
}