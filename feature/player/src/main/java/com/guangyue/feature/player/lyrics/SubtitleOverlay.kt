package com.guangyue.feature.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.ClassicLayout
import com.guangyue.core.lyrics.Line

/**
 * 底部字幕层：只画「当前行翻译」（广乐歌词模型没有 romanization），
 * 当前行没有可读翻译时改画后面几行的预告 —— 照抄 Folia VisualizerSubtitleOverlay.tsx。
 *
 * 纯函数部分（[hasReadableText] / [resolveSubtitleOverlayContent]）不依赖 Compose，单测直接跑在 JVM 上。
 */

/** 字幕行来源。对应 Folia 的 SubtitleOverlayRowSource。 */
enum class SubtitleRowSource { Current, Translation, Next }

/** 一行字幕；key 在同一行内唯一。 */
data class SubtitleRow(val key: String, val source: SubtitleRowSource, val text: String)

/** 字幕层内容；key 是当前行的 startTime（秒），用于进出场动效，没有当前行时 -1。 */
data class SubtitleOverlayContent(
    val key: Double,
    val visible: Boolean,
    val rows: List<SubtitleRow>,
    val previews: List<String>,
)

/** 广乐只有翻译轨，因此单行布局即默认布局（Folia 默认 'translation'）。 */
val DEFAULT_SUBTITLE_LAYOUT: List<SubtitleRowSource> = listOf(SubtitleRowSource.Translation)

/** Folia：'//'、'◑◒◑'、破折号这类占位串不是显示文本，只有含字母或数字才画（CJK 算 \p{L}）。 */
fun hasReadableText(text: String?): Boolean {
    if (text.isNullOrEmpty()) return false
    return text.any { it.isLetterOrDigit() }
}

/** 滤掉没有可读文字的行（Folia `nextLines.filter(next => hasReadableText(next.fullText))`）。 */
fun readableLines(lines: List<Line>): List<Line> = lines.filter { hasReadableText(it.fullText) }

/**
 * 解析字幕层要画的内容。对应 Folia `resolveVisualizerSubtitleOverlayContent`：
 * - 不可见 -> 不渲染；
 * - 逐行取文本，取不到或不可读就跳过（滤占位串）；
 * - 非 current 行与已推入的非 next 行同文则只画一次；
 * - 单轨布局下「翻译 == 当前句原文」（已是拉丁歌词）不再重复，因为舞台已经画了当前句；
 * - 整行没有任何可画内容、且有当前行时，改画后面几行的预告。
 */
fun resolveSubtitleOverlayContent(
    activeLine: Line?,
    nextLines: List<Line>,
    showText: Boolean = true,
    layout: List<SubtitleRowSource> = DEFAULT_SUBTITLE_LAYOUT,
): SubtitleOverlayContent {
    val lineKey = activeLine?.startTime ?: -1.0
    if (!showText) {
        return SubtitleOverlayContent(lineKey, visible = false, rows = emptyList(), previews = emptyList())
    }
    val line = activeLine
    if (line == null) {
        // 没有当前行就没有字幕（Folia 的预告也只在有 activeLine 时给）。
        return SubtitleOverlayContent(lineKey, visible = false, rows = emptyList(), previews = emptyList())
    }
    val previewLines = readableLines(nextLines)
    val rows = mutableListOf<SubtitleRow>()
    var nextIndex = 0
    val lineText = line.fullText.trim()
    for (source in layout) {
        if (source == SubtitleRowSource.Next) {
            val next = previewLines.getOrNull(nextIndex)
            nextIndex += 1
            if (next != null) {
                rows += SubtitleRow(key = "next-$nextIndex", source = source, text = next.fullText.trim())
            }
            continue
        }
        val raw = when (source) {
            SubtitleRowSource.Current -> line.fullText
            SubtitleRowSource.Translation -> line.translation
            SubtitleRowSource.Next -> null
        }
        val text = raw?.trim()
        if (!hasReadableText(text)) continue
        // 非 current 行与已推入的非 next 行同文 -> 只画一次。
        if (source != SubtitleRowSource.Current &&
            rows.any { it.source != SubtitleRowSource.Next && it.text == text }
        ) {
            continue
        }
        // 单轨布局里没有 current 行，翻译与当前句原文相同等于把舞台内容重画一遍 -> 跳过。
        if (source != SubtitleRowSource.Current &&
            layout.none { it == SubtitleRowSource.Current } &&
            text == lineText
        ) {
            continue
        }
        rows += SubtitleRow(key = source.name.lowercase(), source = source, text = text!!)
    }
    // 2026-11-09 真机：预告只取后两行（Folia nextLines = slice(i+1, i+3)），之前把余下整首都排上来，在每个舞台中间堆成一大块小字。
    val previews = if (rows.isEmpty() && activeLine != null) previewLines.take(SUBTITLE_PREVIEW_COUNT).map { it.fullText.trim() } else emptyList()
    return SubtitleOverlayContent(lineKey, visible = true, rows = rows, previews = previews)
}

/** 字幕层默认不透明度（Folia subtitleOverlayOpacity 默认 0.6）。 */
const val SUBTITLE_OVERLAY_OPACITY = 0.6f

/** 第二行相对首行的缩小与降透明（Folia subtitleTrackStyle.ts）。 */
const val SUBTITLE_SECONDARY_SIZE_FACTOR = 0.88f
const val SUBTITLE_SECONDARY_OPACITY = 0.82f

/** 第二行上间距 0.25em 的近似值（Folia SECONDARY_TRACK_GAP_EM）。 */
private val SUBTITLE_SECONDARY_GAP = 4.dp

/** 渐变里 bg@0.78 对应的色标位置（Folia `bg@0.78 62%`）。 */
private const val SUBTITLE_SCRIM_INNER_STOP = 0.62f

/** 预告行整体透明度。层次由 alpha 承担，不用 Modifier.blur（V0d 已证明会光栅成矩形）。 */
const val SUBTITLE_PREVIEW_OPACITY = 0.72f

/** 无译文时预告的行数（Folia 取当前句之后两行）。 */
const val SUBTITLE_PREVIEW_COUNT = 2

/** 字幕背景层相对文字区的外扩（Folia `-inset-x-10 -inset-y-6`，即 40dp / 24dp）。 */
val SUBTITLE_SCRIM_PAD_X = 40.dp
val SUBTITLE_SCRIM_PAD_Y = 24.dp

/**
 * 椭圆渐变边缘的渐隐带宽度（Folia 的 `blur-2xl` = 40px）。渐变最外的 transparent stop
 * 落在这条带之外，四条边才真正化开；否则椭圆在矩形边上仍剩近 0.5 的 alpha，就是一条硬线。
 */
val SUBTITLE_SCRIM_FRINGE = 40.dp

/** 进出场时长（Folia 0.24s easeOut）。 */
const val SUBTITLE_ENTER_MS = 240

/** 舞台区与字幕层之间的净空：舞台限定在这段净空之上，1.3 倍字号也不会被播放条遮。 */
val SUBTITLE_STAGE_RESERVE = 56.dp

/** 播放胶囊（56dp）+ 上下 12dp 外边距的预留高度：字幕层要停在胶囊之上，不能压在进度条上。 */
val SUBTITLE_BAR_RESERVE = 80.dp

/** 底部字幕层。贴底、水平居中，由调用方保证已在导航栏 inset 与播放条之上。 */
@Composable
internal fun LyricSubtitleOverlay(
    content: SubtitleOverlayContent,
    secondaryColor: Color,
    scrimColor: Color,
    viewportWidthDp: Float,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    /** Folia subtitleOverlayOpacity（0.2–1，默认 0.6）。 */
    opacity: Float = SUBTITLE_OVERLAY_OPACITY,
    /** Folia subtitleOverlayBackground：字幕背后的扩散光晕。 */
    background: Boolean = true,
    /** Folia subtitleUpcomingLyricsBlur：下一句（预告 / next 行）加 1px 模糊。 */
    upcomingBlur: Boolean = true,
) {
    if (!content.visible || (content.rows.isEmpty() && content.previews.isEmpty())) return

    val enter = remember(content.key) { Animatable(0f) }
    LaunchedEffect(content.key) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(durationMillis = SUBTITLE_ENTER_MS, easing = FastOutSlowInEasing))
    }

    val sizes = ClassicLayout.resolveSubtitleFontSizes(1.0)
    // 字号算成 dp 再交给 sp —— sp 已含系统 fontScale，重复相乘会双重放大。
    val primaryDp = clampFontDp(
        sizes.translationMinRem, sizes.translationPreferredVw, sizes.translationMaxRem, viewportWidthDp,
    )
    val upcomingFontSize = clampFontDp(
        sizes.upcomingMinRem, sizes.upcomingPreferredVw, sizes.upcomingMaxRem, viewportWidthDp,
    ).sp
    val primaryFontSize = primaryDp.sp
    val secondaryFontSize = (primaryDp * SUBTITLE_SECONDARY_SIZE_FACTOR).sp

    // 背景层按「最宽行宽度 × 全部行高度」算，所以先量 Column 自己（它内部按 wrap 排）。
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    // V0e-fix2：整层透明度只作为「系数」参与，不再用 graphicsLayer/Modifier.alpha 做节点级 alpha 图层。
    // alpha < 1 的图层会把节点离屏合成、并裁剪到节点边界，drawBehind 里超出节点的上下外扩被切掉，
    // 暗区上下就成了两条直线（左右方向因为节点还有余量，看起来是渐隐）。
    val overlayAlpha = enter.value.coerceIn(0f, 1f) * opacity.coerceIn(0.2f, 1f)
    // Folia getUpcomingLyricsClassName：blur-[1px]。只模糊单个文字节点（Unbounded 不裁边），不碰背景层。
    val upcomingMod = if (upcomingBlur) Modifier.blur(1.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier
    val enterShiftY = with(LocalDensity.current) { (1f - enter.value) * 20.dp.toPx() }

    Box(
        modifier = modifier
            // 本节点独占一整行宽度：背景可以四边溢出，不会被父级 pageH 内边距裁成矩形。
            .fillMaxWidth()
            .testTag(LyricSubtitleOverlayTag)
            .drawBehind {
                // Folia：背景层比文字区外扩 x 40dp / y 24dp，填
                // radial-gradient(ellipse 115% 130% at center, bg@0.96 0%, bg@0.78 62%, transparent 100%)，
                // 再整体 blur-2xl 把边缘化掉。这里不碰节点级模糊（V0d 已证明 Modifier.blur 把整节点
                // 光栅成矩形、边缘变硬），改为按同一套参数自绘椭圆径向渐变，四条边都落在渐隐带里。
                //
                // V0e-fix2：本节点上没有任何 alpha 图层，绘制不被裁剪；矩形再向外扩到渐变终点之外，
                // 所以四条边的 alpha 都是 0，暗区的边只由渐变给出，不可能是直线。
                val padX = SUBTITLE_SCRIM_PAD_X.toPx()
                val padY = SUBTITLE_SCRIM_PAD_Y.toPx()
                val textWidth = if (contentWidthPx > 0) contentWidthPx.toFloat() else size.width
                val textHeight = if (contentHeightPx > 0) contentHeightPx.toFloat() else size.height
                val boxWidth = textWidth + padX * 2f
                val boxHeight = textHeight + padY * 2f
                if (background) translate(top = enterShiftY) {
                    drawEllipseScrim(
                        color = scrimColor,
                        width = boxWidth,
                        height = boxHeight,
                        alphaScale = overlayAlpha,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // 只做位移（不设 alpha）：文字与 scrim 的进场位移保持一致，材质不再进离屏图层。
                .graphicsLayer { translationY = enterShiftY }
                .onSizeChanged {
                    contentWidthPx = it.width
                    contentHeightPx = it.height
                },
        ) {
            if (content.rows.isNotEmpty()) {
                content.rows.forEachIndexed { index, row ->
                    val secondary = index > 0
                    // Folia：next 行单行截断并吃下一句模糊，其余行可换行。
                    val upcomingRow = row.source == SubtitleRowSource.Next
                    Text(
                        text = row.text,
                        color = secondaryColor.copy(
                            alpha = (if (secondary) SUBTITLE_SECONDARY_OPACITY else 1f) * overlayAlpha,
                        ),
                        fontSize = if (secondary) secondaryFontSize else primaryFontSize,
                        fontWeight = if (secondary) FontWeight.Normal else FontWeight.Medium,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        // 不加文字阴影：Shadow 会在字形外按 blurRadius 铺一层近似矩形，就是审图里的「硬边方块」。
                        style = TextStyle(),
                        maxLines = if (upcomingRow) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (secondary) SUBTITLE_SECONDARY_GAP else 0.dp).then(if (upcomingRow) upcomingMod else Modifier),
                    )
                }
            } else {
                content.previews.forEach { text ->
                    Text(
                        text = text,
                        color = secondaryColor.copy(alpha = SUBTITLE_PREVIEW_OPACITY * overlayAlpha),
                        fontSize = upcomingFontSize,
                        fontWeight = FontWeight.Normal,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        // 同上：不加文字阴影。
                        style = TextStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = upcomingMod,
                    )
                }
            }
        }
    }
}

/** clamp(minRem*16, preferredVw*宽度/100, maxRem*16)，单位 dp。对应 Folia subtitleFontSizes.ts。 */
internal fun clampFontDp(minRem: Double, preferredVw: Double, maxRem: Double, viewportWidthDp: Float): Float {
    val min = (minRem * 16.0).toFloat()
    val value = (preferredVw * viewportWidthDp / 100.0).toFloat()
    val max = (maxRem * 16.0).toFloat()
    return value.coerceIn(min, max)
}

/**
 * 字幕背景：椭圆径向渐变，对应 CSS
 * `radial-gradient(ellipse <rx> <ry> at center, bg@0.96 0%, bg@0.78 62%, transparent 100%)`。
 *
 * 做法是「单位圆径向渐变 + 矩阵拉伸」：先按 115% / 130% 得到椭圆主半径，再各自加一条
 * [SUBTITLE_SCRIM_FRINGE] 的渐隐带，这样矩形四条边都落在渐变尾部（alpha≈0），看不到硬边。
 * 不用节点级 Modifier.blur —— V0d 已证明它会把整节点光栅成方块。
 */
private fun DrawScope.drawEllipseScrim(
    color: Color,
    width: Float,
    height: Float,
    alphaScale: Float,
) {
    if (alphaScale <= 0f) return
    val fringe = SUBTITLE_SCRIM_FRINGE.toPx()
    val radiusX = width / 1.15f / 2f + fringe
    val radiusY = height / 1.30f / 2f + fringe
    val shader = android.graphics.RadialGradient(
        0f,
        0f,
        1f,
        intArrayOf(
            // 整层透明度乘进渐变的颜色里（而不是套节点级 alpha 图层）。
            color.copy(alpha = 0.96f * alphaScale).toArgb(),
            color.copy(alpha = 0.78f * alphaScale).toArgb(),
            0,
        ),
        floatArrayOf(0f, SUBTITLE_SCRIM_INNER_STOP, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    ).apply {
        setLocalMatrix(
            android.graphics.Matrix().apply {
                setScale(radiusX, radiusY)
                // 渐变中心 = 节点中心（文字在这里居中），四个方向对称。
                postTranslate(size.width / 2f, size.height / 2f)
            },
        )
    }
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.shader = shader
            this.alpha = 1f
            blendMode = BlendMode.SrcOver
        }
        // V0e-fix2：矩形向外扩到渐变终点（alpha=0）之外，四边的 alpha 都是 0 —— 暗区边界只由
        // 渐变本身给出。旧写法矩形只覆盖节点本身，下边还比节点底少 PAD_Y，再被节点 alpha 图层
        // 裁到节点边界，于是上、下各出现一条直线。
        val bleed = maxOf(radiusX, radiusY) + fringe
        canvas.drawRect(-bleed, -bleed, size.width + bleed, size.height + bleed, paint)
    }
}
