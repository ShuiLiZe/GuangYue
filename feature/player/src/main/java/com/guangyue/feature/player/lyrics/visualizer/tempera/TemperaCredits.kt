// 凝彩片尾海报（Folia temperaSceneBuilder.ts buildTemperaCreditsPoster + temperaCreditsLayer.ts resolveTemperaCreditsFrame），逐函数对译。
// 海报用 shot 构图同一套词汇：满版 tone1 底、三块圆心都在画外的 tone2→tone4 圆盘（一块带网线）+ 墨色缝线、贯穿线、角落记号，
// 歌名居中、歌手 · 专辑在下；最后一段唱完 0.35 s 起出现，圆盘沿各自方向渐近蠕动（标题不动，反色一直在重切）。
// 只产出纯数据（节点 + 入场 / 漂移参数），绘制交给 TemperaStage；金标 tempera_credits_golden.txt 逐节点对拍。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import com.guangyue.feature.player.lyrics.visualizer.sonnetHash
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 片尾一帧（resolveTemperaCreditsFrame）。 */
internal class TpCreditsFrame(
    val active: Boolean,
    val lyricAlpha: Double,
    val posterAlpha: Double,
    val posterOffsetY: Double,
    val posterScale: Double,
)

/** 最后一段的歌词尾巴唱完后海报升起。 */
internal fun resolveTemperaCreditsFrame(time: Double, finalEndTime: Double): TpCreditsFrame {
    val lyricAlpha = 1 - easeTemperaInOut((time - finalEndTime - 0.1) / 0.9)
    val posterProgress = easeTemperaInOut((time - finalEndTime - 0.9) / 1.1)
    return TpCreditsFrame(
        active = time > finalEndTime + 0.35,
        lyricAlpha = lyricAlpha,
        posterAlpha = posterProgress,
        posterOffsetY = (1 - posterProgress) * 0.06,
        posterScale = 0.96 + posterProgress * 0.04,
    )
}

internal fun hasTemperaCreditsMetadata(title: String?, artist: String?, album: String?) =
    !title.isNullOrBlank() || !artist.isNullOrBlank() || !album.isNullOrBlank()

/** 海报上的一个节点（= Folia credits 容器的一个 child）。node 为 null 时是标题层。 */
internal class TpCreditsItem(
    val node: TpNode?,
    val baseX: Double,
    val baseY: Double,
    val pivotX: Double,
    val pivotY: Double,
    val baseAlpha: Double,
    val enterDX: Double,
    val enterDY: Double,
    val delay: Double,
    val driftX: Double,
    val driftY: Double,
    val grow: Double,
) {
    var alpha = 1.0
    var visible = true
    var x = baseX
    var y = baseY
    var scale = 1.0
}

/** 标题层的一行（Pixi Text：居中锚点、wordWrap）。 */
internal class TpCreditsLine(val text: String, val fontSize: Double, val offsetY: Double, val alpha: Double)

internal class TpCreditsPoster(
    /** 满版底色（不动，不进 items）。 */
    val ground: TpNode,
    /** 按 Folia addChild 的次序；标题层是 node == null 的那一项。 */
    val items: List<TpCreditsItem>,
    val lines: List<TpCreditsLine>,
    val wrapWidth: Double,
    val seed: Long,
) {
    /** `elapsed` = 海报出现后的秒数（之前为负）。 */
    fun updateTime(elapsed: Double) {
        val creep = creditsCreep(elapsed)
        for (item in items) {
            val enter = easeTemperaEnter((elapsed - item.delay) / 1.1)
            item.alpha = item.baseAlpha * enter
            item.visible = enter > 0.001
            item.x = item.baseX + item.enterDX * (1 - enter) + item.driftX * creep
            item.y = item.baseY + item.enterDY * (1 - enter) + item.driftY * creep
            if (item.grow != 0.0) item.scale = 1 + item.grow * creep
        }
    }
}

/** 渐近：一直在动，永远不跑远。 */
private fun creditsCreep(elapsed: Double) = 1 - exp(-max(0.0, elapsed) / 7)

private const val CREDITS_DISC_COUNT = 3

private fun boundsCenterOf(p: DoubleArray): Pair<Double, Double> {
    var a = Double.POSITIVE_INFINITY; var b = Double.NEGATIVE_INFINITY; var c = Double.POSITIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
    var i = 0
    while (i + 1 < p.size) { a = min(a, p[i]); b = max(b, p[i]); c = min(c, p[i + 1]); d = max(d, p[i + 1]); i += 2 }
    return if (a.isInfinite()) 0.0 to 0.0 else ((a + b) / 2) to ((c + d) / 2)
}

/**
 * buildTemperaCreditsPoster。[titleHeight] 量标题那行的高（Pixi `title.height`：按字号、换行宽度排出来的高），
 * 副标题贴在它下面。
 */
internal fun buildTemperaCreditsPoster(
    title: String?,
    artist: String?,
    album: String?,
    width: Double,
    height: Double,
    lyricsFontScale: Double,
    showDecor: Boolean = true,
    titleHeight: (text: String, fontSize: Double, wrapWidth: Double) -> Double,
): TpCreditsPoster {
    val seed = sonnetHash("${title ?: ""}|${artist ?: ""}")
    val diagonal = hypot(width, height)
    val bleed = diagonal * 0.25
    val halfWidth = width / 2
    val halfHeight = height / 2
    val flip = if (th01(seed, 1, 229) > 0.5) 1 else -1
    val items = ArrayList<TpCreditsItem>()

    fun add(
        node: TpNode?, x: Double = 0.0, y: Double = 0.0, pivotX: Double = 0.0, pivotY: Double = 0.0,
        enterDX: Double = 0.0, enterDY: Double = 0.0, delay: Double = 0.0, driftX: Double = 0.0, driftY: Double = 0.0, grow: Double = 0.0,
    ) {
        items += TpCreditsItem(node, x, y, pivotX, pivotY, 1.0, enterDX, enterDY, delay, driftX, driftY, grow)
    }

    val ground = drawPolygonFill(rectPolygon(-halfWidth - bleed, -halfHeight - bleed, width + bleed * 2, height + bleed * 2), TpTone.TONE1, 1.0)

    val tones = listOf(TpTone.TONE2, TpTone.TONE3, TpTone.TONE4)
    val baseAngle = th01(seed, 4, 241) * PI * 2
    val hatchIndex = floor(th01(seed, 5, 251) * CREDITS_DISC_COUNT).toInt() % CREDITS_DISC_COUNT
    for (index in 0 until CREDITS_DISC_COUNT) {
        val angle = baseAngle + (index * PI * 2) / CREDITS_DISC_COUNT + (th01(seed, index, 257) - 0.5) * 0.7
        val distance = diagonal * (0.58 + th01(seed, index, 263) * 0.14)
        val radius = distance + diagonal * (0.06 + th01(seed, index, 269) * 0.1)
        val polygon = circlePolygon(0.0, 0.0, radius, 72)
        val cx = cos(angle) * distance
        val cy = sin(angle) * distance
        val delay = 0.08 + index * 0.16
        val enterDX = cos(angle) * diagonal * 0.16
        val enterDY = sin(angle) * diagonal * 0.16
        val driftX = -cos(angle) * diagonal * 0.024
        val driftY = -sin(angle) * diagonal * 0.024
        fun place(node: TpNode, itemDelay: Double, pivot: Pair<Double, Double> = 0.0 to 0.0) =
            add(node, cx, cy, pivot.first, pivot.second, enterDX, enterDY, itemDelay, driftX, driftY)
        place(drawPolygonFill(polygon, tones[index], 1.0), delay)
        if (index == hatchIndex) {
            place(drawHatchFill(polygon, buildHatchSpec(seed, 271), TpTone.PAPER, 0.28), delay + 0.06, boundsCenterOf(polygon))
        }
        place(drawPolygonOutline(polygon, TpTone.INK, 2.4, 0.8), delay + 0.04)
    }

    val lines = buildCrossingLines(seed, 31, width, height, 2).map { TpLine(it.x1 - halfWidth, it.y1 - halfHeight, it.x2 - halfWidth, it.y2 - halfHeight) }
    add(drawLines(lines, TpTone.TONE4, 1.3, 0.6), delay = 0.5, enterDX = width * 0.2)

    if (showDecor) {
        val cornerX = halfWidth * 0.66 * flip
        val cornerY = halfHeight * 0.62
        add(drawCrossMarks(buildCrossRow(seed, 71, cornerX - width * 0.1, cornerY, 5, width * 0.05, 8.0), TpTone.TONE4, 2.0, 0.8), delay = 0.62, enterDX = -width * 0.06)
        val box = rectPolygon(-cornerX - 19, -cornerY - 19, 38.0, 38.0)
        val (bx, by) = boundsCenterOf(box)
        add(drawHatchFill(box, buildHatchSpec(seed, 67), TpTone.TONE4, 0.7), bx, by, bx, by, delay = 0.68, grow = 0.06)
        add(drawPolygonOutline(box, TpTone.TONE4, 1.2, 0.6), delay = 0.72)
    }

    val wrapWidth = min(width * 0.72, diagonal * 0.52)
    val subtitle = listOf(artist, album).filter { !it.isNullOrEmpty() }.joinToString(" - ")
    val titleSize = max(26.0, min(width, height) * 0.085 * lyricsFontScale)
    val titleText = title?.trim()?.takeIf { it.isNotEmpty() } ?: "\u266a"
    val textLines = ArrayList<TpCreditsLine>()
    textLines += TpCreditsLine(titleText, titleSize, if (subtitle.isNotEmpty()) -titleSize * 0.35 else 0.0, 1.0)
    if (subtitle.isNotEmpty()) {
        textLines += TpCreditsLine(subtitle, max(14.0, titleSize * 0.34), titleHeight(titleText, titleSize, wrapWidth) / 2 + titleSize * 0.22, 0.75)
    }
    add(null, enterDY = diagonal * 0.03, delay = 0.78)

    return TpCreditsPoster(ground, items, textLines, wrapWidth, seed).also { it.updateTime(0.0) }
}
