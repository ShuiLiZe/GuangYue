// 对应 Folia 文件：src/components/visualizer/monet/VisualizerMonet.tsx（海报版面：斜体歌手 + 竖线 + 大标题 +
// 专辑行 + 歌词轨）、MonetLyricsRail.tsx / monetRailLayout.ts（轨道：当前句落在轨高 46%，前后各几行按距离
// 缩小、变淡、模糊）、MonetRailLine.tsx（当前句底色 primary@0.34，逐字填成 primary@0.98）。
//
// R1b 横屏（宽 > 高）：照 VisualizerMonet.tsx 的 flex-row 逐项对译——左列 flex-1（px-8 py-6，宽≥1024 为 px-14 py-8），
// 头部 mb-3 + 标题块 mb-6 + 轨高 clamp(280px, 52vh, railMax)，整列 justify-center（矮屏上下溢出被裁，与 Folia 同）；
// 右列肖像 flex 0 0 clamp(220px, 28vw, 430px)、px-3 pr-8，内框 clamp(210px, 26vw, 380px)，square 款宽 135.135% 左移 35.135%，
// 圆角 2rem、底色 primary@0.08、右上挂条（bg@0.86，h-14 w-3，-top-3 right-8）。轨上下 mask 渐隐 0/11%/88%/100%。
//
// P3「莫奈」手机版（竖屏）：
//  - 右侧肖像在 Folia 里 `hidden md:flex`，手机本来就不显示，这里不做；
//  - 莫奈背景（monet background worker）不做，沿用舞台的封面取色流体背景 + 通用几何；
//  - 底部频谱、浮动装饰、肖像拖拽、关键词着色不做；说明胶囊（theme.description）没有对应数据，不做。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.guangyue.core.lyrics.Line
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** 轨道一行的视觉档位（monetRailLayout.resolveLineTone）。 */
internal data class MonetTone(val opacity: Float, val scale: Float, val blurDp: Float, val baseAlpha: Float)

internal object MonetRail {
    // monetRailLayout MONET_SCROLL_BEFORE / AFTER
    const val BEFORE = 4
    const val AFTER = 4
    // MONET_RAIL_BASE_MAX_HEIGHT_PX / MONET_RAIL_MIN_ROWS（轨高上限）
    const val RAIL_BASE_MAX_H = 520f
    const val RAIL_BASE_MAX_W = 780f
    const val RAIL_MIN_ROWS = 7
    // monetLyricsModel：行最大宽、肖像列上限、肖像内框上限
    const val ROW_MAX_W = 1520f
    const val PORTRAIT_MAX = 430f
    const val PORTRAIT_INNER_MAX = 380f
    // DEFAULT_MONET_TUNING.fontScale（只乘歌词 / 非当前 / 译文字号，标题与歌手不乘）
    const val TUNING_FONT_SCALE = 1.2f
    const val FOCUS_RATIO = 0.46f
    const val ACTIVE_GAP = 18f
    const val INACTIVE_GAP = 14f

    /** Folia：active 1/1/0；其它按距离 0.9^(d-1) 缩小，等待行比已唱行亮。 */
    fun tone(offset: Int, inactiveScale: Float): MonetTone {
        if (offset == 0) return MonetTone(1f, 1f, 0f, 0.34f)
        val d = max(abs(offset), 1)
        val waiting = offset > 0
        val scale = (inactiveScale * 0.9f.pow(d - 1)).coerceIn(0.68f, 0.92f)
        val opacity = if (waiting) (0.72f - (d - 1) * 0.18f).coerceIn(0.36f, 0.72f)
        else (0.52f - (d - 1) * 0.12f).coerceIn(0.28f, 0.52f)
        val blur = if (waiting) (if (d == 1) 0.7f else 1.8f + (d - 2) * 0.8f) else 1.1f + (d - 1) * 0.7f
        return MonetTone(opacity, scale, blur, if (waiting) 0.46f else 0.36f)
    }

    /** 译文字号 / 歌词字号（Folia translationFontPx = clamp(0.94rem, 1.28vw, 1.14rem) 与 lyricFontPx 同乘 fontScale）。 */
    fun translationRatio(widthDp: Float): Float =
        clampDp(0.94f, 1.28f, 1.14f, widthDp) / clampDp(1.34f, 2.75f, 2.28f, widthDp)

    /** CSS clamp(min, vw, max)，单位 dp。 */
    fun clampDp(minRem: Float, vw: Float, maxRem: Float, widthDp: Float): Float =
        (widthDp * vw / 100f).coerceIn(minRem * 16f, maxRem * 16f)

    /** 一个词的填充进度 0..1（量化到 0.1，避免每帧重组整行）。 */
    fun wordFill(startS: Double, endS: Double, nowS: Double): Float {
        if (nowS <= startS) return 0f
        if (nowS >= endS || endS <= startS) return 1f
        return (((nowS - startS) / (endS - startS)) * 10).toInt() / 10f
    }
}

@Composable
internal fun MonetLyricStage(
    lines: List<Line>,
    currentIndex: Int,
    clock: VisualizerClock,
    title: String,
    artist: String,
    album: String,
    lyricsFontScale: Double,
    primaryColor: Color,
    secondaryColor: Color,
    backgroundColor: Color,
    fontFamily: FontFamily,
    topReserve: Dp,
    coverUrl: String? = null,
    accentColor: Color = primaryColor,
    bottomReserve: Dp,
    reduceMotion: Boolean,
    onSeekToLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fs = lyricsFontScale.toFloat()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val artistDp = MonetRail.clampDp(1f, 1.8f, 1.8f, widthDp) * fs
        val titleDp = MonetRail.clampDp(1.45f, 3.3f, 2.8f, widthDp) * fs
        val lyricDp = MonetRail.clampDp(1.34f, 2.75f, 2.28f, widthDp) * fs
        val inactiveDp = MonetRail.clampDp(1.08f, 2f, 1.48f, widthDp) * fs
        val inactiveScale = (inactiveDp / lyricDp).coerceIn(0.72f, 0.92f)
        val sp = { v: Float -> with(density) { v.dp.toSp() } }
        if (widthDp > heightDp) {
            MonetLandscape(
                lines, currentIndex, clock, title, artist, album, widthDp, heightDp,
                artistDp, titleDp, lyricDp, inactiveScale, primaryColor, secondaryColor, backgroundColor,
                accentColor, fontFamily, coverUrl, reduceMotion, onSeekToLine,
            )
            return@BoxWithConstraints
        }
        // 轨高：clamp(280px, 52vh, …)，手机上再让出顶栏与胶囊、标题区。
        val usable = heightDp - topReserve.value - bottomReserve.value
        val railDp = (heightDp * 0.52f).coerceAtLeast(280f).coerceAtMost(max(usable - 150f, 220f))

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = topReserve, bottom = bottomReserve),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            // ---------- 歌手（斜体）+ 竖线 ----------
            Text(
                text = artist.ifBlank { "Monet" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = primaryColor.copy(alpha = 0.96f),
                    fontStyle = FontStyle.Italic,
                    fontSize = sp(artistDp),
                    fontFamily = fontFamily,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.72f), Color.Transparent)),
                        RoundedCornerShape(1.dp),
                    ),
            )
            Spacer(Modifier.height(12.dp))
            // ---------- 大标题（两行截断）+ 专辑（大写） ----------
            Text(
                text = title.ifBlank { "Monet" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = sp(titleDp),
                    lineHeight = sp(titleDp * 1.18f),
                    fontFamily = fontFamily,
                    shadow = Shadow(backgroundColor.copy(alpha = 0.28f), Offset(0f, 14f), 36f),
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = album.ifBlank { "Monet" }.uppercase(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = secondaryColor.copy(alpha = 0.84f), fontSize = sp(14f)),
            )
            Spacer(Modifier.height(24.dp))
            // ---------- 歌词轨 ----------
            MonetRailBox(
                lines = lines,
                currentIndex = currentIndex,
                clock = clock,
                railHeight = railDp.dp,
                lyricDp = lyricDp,
                translationDp = lyricDp * MonetRail.translationRatio(widthDp),
                inactiveScale = inactiveScale,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                fontFamily = fontFamily,
                reduceMotion = reduceMotion,
                onSeekToLine = onSeekToLine,
            )
        }
    }
}

@Composable
private fun MonetRailBox(
    lines: List<Line>,
    currentIndex: Int,
    clock: VisualizerClock,
    railHeight: Dp,
    lyricDp: Float,
    translationDp: Float,
    inactiveScale: Float,
    primaryColor: Color,
    secondaryColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    onSeekToLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchor = currentIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    // 每行量到的未缩放高度（px），用来按 Folia 的方式从焦点往上下堆叠。
    val heights = remember(lines) { mutableStateMapOf<Int, Int>() }
    // 当前句主文字高（不含译文轨）：Folia 以主文字块居中于轨高 46%，译文挂在下面。
    val mainHeights = remember(lines) { mutableStateMapOf<Int, Int>() }
    val railPx = with(density) { railHeight.toPx() }
    val lyricPx = with(density) { lyricDp.dp.toPx() }
    val activeGap = with(density) { max(MonetRail.ACTIVE_GAP, lyricDp * 0.49f).dp.toPx() }
    val inactiveGap = with(density) { max(MonetRail.INACTIVE_GAP, lyricDp * 0.38f).dp.toPx() }

    val range = (anchor - MonetRail.BEFORE - 1).coerceAtLeast(0)..(anchor + MonetRail.AFTER + 1).coerceAtMost(lines.size - 1)
    // 目标 y（px，行顶）。
    val targets = HashMap<Int, Float>()
    run {
        fun h(i: Int) = (heights[i] ?: lyricPx.roundToInt()).toFloat() * MonetRail.tone(i - anchor, inactiveScale).scale
        // 以整块（主文字+译文）居中于轨高 46%（与 Folia 基准对照：块中心误差 <4dp）。
        targets[anchor] = railPx * MonetRail.FOCUS_RATIO - h(anchor) / 2f
        var y = targets[anchor]!!
        for (i in anchor + 1..range.last) {
            val gap = if (i - 1 == anchor) activeGap else inactiveGap
            y += h(i - 1) + gap
            targets[i] = y
        }
        y = targets[anchor]!!
        for (i in anchor - 1 downTo range.first) {
            val gap = if (i + 1 == anchor) activeGap else inactiveGap
            y -= h(i) + gap
            targets[i] = y
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(railHeight)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent, 0.11f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent,
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            },
    ) {
        for (i in range) {
            val line = lines[i]
            val offset = i - anchor
            val visible = offset in -MonetRail.BEFORE..MonetRail.AFTER
            val tone = MonetRail.tone(offset, inactiveScale)
            key(i) {
                val ty = remember { Animatable(targets[i] ?: 0f) }
                val sc = remember { Animatable(tone.scale) }
                val op = remember { Animatable(0f) }
                val targetY = targets[i] ?: 0f
                val targetOp = if (visible) tone.opacity else 0f
                LaunchedEffect(targetY, reduceMotion) {
                    if (reduceMotion) ty.snapTo(targetY)
                    else ty.animateTo(targetY, spring(dampingRatio = 0.86f, stiffness = 140f))
                }
                LaunchedEffect(tone.scale, reduceMotion) {
                    if (reduceMotion) sc.snapTo(tone.scale)
                    else sc.animateTo(tone.scale, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow))
                }
                LaunchedEffect(targetOp) { op.animateTo(targetOp, tween(280)) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, ty.value.roundToInt()) }
                        .onSizeChanged { heights[i] = it.height }
                        .graphicsLayer {
                            scaleX = sc.value
                            scaleY = sc.value
                            alpha = op.value
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .then(if (tone.blurDp > 0f && !reduceMotion) Modifier.blur(tone.blurDp.dp) else Modifier)
                        .then(
                            if (visible) Modifier.clickableNoRippleMonet { onSeekToLine(i) } else Modifier,
                        ),
                ) {
                    val textStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = if (offset == 0) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = with(density) { lyricDp.dp.toSp() },
                        // Folia measureMonetLineLayout：主文字行高 1.18em，上留 max(0.16em, 8)、下留 max(0.34em, 14)。
                        lineHeight = with(density) { (lyricDp * 1.18f).dp.toSp() },
                    )
                    val padTop = max(lyricDp * 0.16f, 8f).dp
                    val padBottom = max(lyricDp * 0.34f, 14f).dp
                    if (offset == 0) {
                        val text by remember(line, primaryColor) {
                            derivedStateOf { monetActiveText(line, clock.seconds(), primaryColor) }
                        }
                        Text(text = text, style = textStyle, modifier = Modifier.onSizeChanged { mainHeights[i] = it.height }.padding(top = padTop, bottom = padBottom))
                    } else {
                        Text(
                            text = line.fullText,
                            style = textStyle.copy(color = primaryColor.copy(alpha = tone.baseAlpha)),
                            modifier = Modifier.padding(top = padTop, bottom = padBottom),
                        )
                    }
                    val tr = line.translation
                    // Folia 只给当前句排译文轨（MonetRailLine 的 subtitle 行仅 active）。
                    if (offset == 0 && !tr.isNullOrBlank()) {
                        Text(
                            text = tr,
                            // Folia measureMonetSubtitleTrack：行高 1.28em，上留 max(0.45em, 7)、下留 max(0.18em, 5)，字重 500。
                            modifier = Modifier.padding(top = max(translationDp * 0.45f, 7f).dp, bottom = max(translationDp * 0.18f, 5f).dp),
                            style = TextStyle(
                                color = primaryColor.copy(alpha = 0.68f),
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = with(density) { translationDp.dp.toSp() },
                                lineHeight = with(density) { (translationDp * 1.28f).dp.toSp() },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 当前句：已唱的词 primary@0.98，未唱 primary@0.34，正在唱的词按进度插值（MonetRailLine 的逐字填色）。 */
internal fun monetActiveText(line: Line, nowS: Double, primary: Color): AnnotatedString {
    val base = primary.copy(alpha = 0.34f)
    val lit = primary.copy(alpha = 0.98f)
    if (line.words.isEmpty()) {
        val p = MonetRail.wordFill(line.startTime, line.endTime, nowS)
        return AnnotatedString(line.fullText, SpanStyle(color = lerp(base, lit, p)))
    }
    // 词在整句里按顺序定位（英文词之间的空格不在 words 里），未覆盖的字符随前一个词着色。
    return buildAnnotatedString {
        append(line.fullText)
        var cursor = 0
        var lastColor = base
        for (w in line.words) {
            val t = w.text.trim()
            if (t.isEmpty()) continue
            val at = line.fullText.indexOf(t, cursor)
            if (at < 0) continue
            if (at > cursor) addStyle(SpanStyle(color = lastColor), cursor, at)
            val p = MonetRail.wordFill(w.startTime, w.endTime, nowS)
            lastColor = lerp(base, lit, p)
            addStyle(SpanStyle(color = lastColor), at, at + t.length)
            cursor = at + t.length
        }
        if (cursor < line.fullText.length) addStyle(SpanStyle(color = if (nowS >= line.endTime) lit else base), cursor, line.fullText.length)
    }
}

private fun Modifier.clickableNoRippleMonet(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        ),
    )


/** R1b：横屏海报（VisualizerMonet.tsx 的 flex-row）。 */
@Composable
private fun MonetLandscape(
    lines: List<Line>,
    currentIndex: Int,
    clock: VisualizerClock,
    title: String,
    artist: String,
    album: String,
    widthDp: Float,
    heightDp: Float,
    artistDp: Float,
    titleDp: Float,
    lyricDp: Float,
    inactiveScale: Float,
    primaryColor: Color,
    secondaryColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    coverUrl: String?,
    reduceMotion: Boolean,
    onSeekToLine: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val sp = { v: Float -> with(density) { v.dp.toSp() } }
    @Suppress("NAME_SHADOWING") val lyricDp = lyricDp * MonetRail.TUNING_FONT_SCALE
    // Tailwind 断点：sm 640 / lg 1024 / 2xl 1536。
    val padX = when { widthDp >= 1536f -> 80f; widthDp >= 1024f -> 56f; widthDp >= 640f -> 32f; else -> 20f }
    val padY = when { widthDp >= 1024f -> 32f; widthDp >= 640f -> 24f; else -> 20f }
    val railMax = max(MonetRail.RAIL_BASE_MAX_H, lyricDp * 1.18f * MonetRail.RAIL_MIN_ROWS)
    val railDp = (heightDp * 0.52f).coerceIn(280f, max(280f, railMax))
    val portraitCol = (widthDp * 0.28f).coerceIn(220f, MonetRail.PORTRAIT_MAX)
    val portraitInner = (widthDp * 0.26f).coerceIn(210f, MonetRail.PORTRAIT_INNER_MAX)
    val portraitPadEnd = when { widthDp >= 1280f -> 48f; widthDp >= 1024f -> 40f; widthDp >= 640f -> 32f; else -> 20f }
    // headerMaxWidth：max(12rem, 100% - max(0, 0.35135*inner - 3rem))
    val bleed = max(0f, 0.35135f * portraitInner - 48f)

    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxHeight().widthIn(max = MonetRail.ROW_MAX_W.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight().clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val headerMax = max(192f, maxWidth.value - padX * 2 - bleed)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true)
                        .padding(horizontal = padX.dp, vertical = padY.dp),
                ) {
                    Column(Modifier.widthIn(max = headerMax.dp).padding(bottom = 12.dp)) {
                        Text(
                            text = artist.ifBlank { "Monet" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = primaryColor.copy(alpha = 0.96f),
                                fontStyle = FontStyle.Italic,
                                fontSize = sp(artistDp),
                                fontFamily = fontFamily,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(56.dp)
                                .background(
                                    Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.72f), Color.Transparent)),
                                    RoundedCornerShape(1.dp),
                                ),
                        )
                    }
                    Column(Modifier.widthIn(max = headerMax.dp).padding(bottom = 24.dp)) {
                        Text(
                            text = title.ifBlank { "Monet" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = sp(titleDp),
                                lineHeight = sp(titleDp * 1.06f),
                                fontFamily = fontFamily,
                                shadow = Shadow(backgroundColor.copy(alpha = 0.28f), Offset(0f, 14f), 36f),
                            ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = album.ifBlank { "Monet" }.uppercase(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = secondaryColor.copy(alpha = 0.84f), fontSize = sp(14f)),
                        )
                    }
                    MonetRailBox(
                        lines = lines,
                        currentIndex = currentIndex,
                        clock = clock,
                        railHeight = railDp.dp,
                        lyricDp = lyricDp,
                        translationDp = lyricDp * MonetRail.translationRatio(widthDp),
                        inactiveScale = inactiveScale,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        fontFamily = fontFamily,
                        reduceMotion = reduceMotion,
                        onSeekToLine = onSeekToLine,
                        modifier = Modifier.widthIn(max = MonetRail.RAIL_BASE_MAX_W.dp),
                    )
                    // showDescription（默认 true）：mt-auto pt-4 的说明胶囊，矮屏上被裁在下方，但参与整列高度与居中。
                    Box(Modifier.padding(top = 16.dp)) {
                        Row(
                            Modifier
                                .background(backgroundColor.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .border(1.dp, primaryColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(10.dp).height(10.dp).background(accentColor, RoundedCornerShape(50)))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "MONET",
                                style = TextStyle(color = primaryColor.copy(alpha = 0.9f), fontSize = sp(12f), lineHeight = sp(16f)),
                            )
                        }
                    }
                }
            }
            // 右列肖像（md:flex，宽≥768 才有；手机横屏 800dp 起即显示）
            if (widthDp >= 768f) {
                Box(
                    Modifier
                        .width(portraitCol.dp)
                        .padding(start = 12.dp, end = portraitPadEnd.dp),
                    contentAlignment = if (widthDp >= 1024f) Alignment.CenterEnd else Alignment.Center,
                ) {
                    Box(Modifier.widthIn(max = portraitInner.dp).fillMaxWidth()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val w = maxWidth
                            val big = w * 1.35135f
                            Box(
                                Modifier
                                    .requiredWidth(big)
                                    // requiredWidth 居中溢出（左缘在 -(big-w)/2），再左移一半，使左缘落在 -35.135%·w（marginLeft）。
                                    .offset(x = -(big - w) / 2),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .shadow(36.dp, RoundedCornerShape(32.dp), ambientColor = backgroundColor.copy(alpha = 0.45f), spotColor = accentColor.copy(alpha = 0.22f))
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(primaryColor.copy(alpha = 0.08f)),
                                ) {
                                    if (!coverUrl.isNullOrBlank()) {
                                        com.guangyue.feature.player.lyrics.CoverImage(
                                            url = coverUrl,
                                            modifier = Modifier.fillMaxSize(),
                                            accent = accentColor,
                                        )
                                    }
                                }
                                // 挂条
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-32).dp, y = (-12).dp)
                                        .width(12.dp)
                                        .height(56.dp)
                                        .shadow(8.dp, RoundedCornerShape(50))
                                        .background(backgroundColor.copy(alpha = 0.86f), RoundedCornerShape(50)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
