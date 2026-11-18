package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.Gyp

/**
 * 11-18 照 Folia 歌词样式窗口的样式架（VisPlayground `PictureGrid`，窄窗 4 列 SHELF_COLUMNS.narrow）：
 * 16:10 的画框（`--st-pv` 底 + 1px `--st-line` 内描边，圆角 8），名字在框下 12/18 居中；选中 = 框外 3dp 一圈 2dp 强调色环，名字半粗 t1。
 * 头行「歌词样式」13 半粗 t2 + 数量 t3。整架最高 250，多了在架里滚。
 * ✋ Folia 框里是该样式的实时小预览（LyricStylePicture）；广乐多数样式是 GL 舞台，一屏同时起 16 个太重，
 * 框里画 Folia 各样式 entry.tsx 的线性字形（modeGlyphs，24 视框、1.6 线宽），放大到 28dp。
 */
@Composable
internal fun LyricStylePictureGrid(
    rows: List<Pair<LyricStyle, String>>,
    selected: LyricStyle,
    accent: Color,
    title: String,
    onPick: (LyricStyle) -> Unit,
    columns: Int = 4,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Gyp.t2)
            Text(" ${rows.size}", fontSize = 13.sp, lineHeight = 20.sp, color = Gyp.t3, modifier = Modifier.padding(start = 2.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    line.forEach { (style, name) ->
                        StylePicture(style, name, style == selected, accent, Modifier.weight(1f)) { onPick(style) }
                    }
                    repeat(columns - line.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StylePicture(style: LyricStyle, name: String, chosen: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (chosen) {
                // Folia：-inset-[3px] rounded-[11px] inset 2px 强调色环。
                Box(Modifier.fillMaxSize().padding(0.dp).border(2.dp, accent, RoundedCornerShape(11.dp)))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gyp.preview)
                    .border(1.dp, Gyp.line2, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                StyleGlyph(style, if (chosen) Gyp.ink else Gyp.t2, Modifier.size(28.dp))
            }
        }
        Text(
            name,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            color = if (chosen) Gyp.ink else Gyp.t2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        )
    }
}

/** 一笔字形：SVG path（24 视框）、不透明度、是否填充、绕 (12,12) 旋转角。 */
private class GlyphStroke(val d: String, val alpha: Float = 1f, val fill: Boolean = false, val rotate: Float = 0f, val width: Float = 1.6f)

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = "M${cx - rx},${cy}a$rx,$ry 0 1,0 ${2 * rx},0a$rx,$ry 0 1,0 ${-2 * rx},0Z"
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0,1 $r,${r}v${h - 2 * r}a$r,$r 0 0,1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0,1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0,1 $r,${-r}Z"

/** Folia 各样式 entry.tsx 的 glyph（逐笔照抄）。 */
private fun glyphOf(style: LyricStyle): List<GlyphStroke> = when (style) {
    LyricStyle.CLASSIC -> listOf(GlyphStroke("M3 12h18", 0.35f), GlyphStroke("M3 12h8"))
    LyricStyle.CADENZA -> listOf(GlyphStroke(circle(9.5f, 12.5f, 3.5f)), GlyphStroke("M4 6.5h3.5", 0.45f), GlyphStroke("M16 8h4", 0.45f), GlyphStroke("M15 17h4.5", 0.45f))
    LyricStyle.PARTITA -> listOf(GlyphStroke("M4 7h7"), GlyphStroke("M8.5 12h7", 0.7f), GlyphStroke("M13 17h7", 0.45f))
    LyricStyle.FUME -> listOf(GlyphStroke("M6 7.5h9", 0.3f), GlyphStroke("M1 12h22"), GlyphStroke("M9 16.5h9", 0.3f))
    LyricStyle.TILT -> listOf(GlyphStroke("M4 9h16"), GlyphStroke("M5 16.5l14-3", 0.55f))
    LyricStyle.CLADDAGH -> listOf(GlyphStroke(ellipse(12f, 12f, 9f, 4.5f), rotate = -18f), GlyphStroke(circle(18f, 9f, 1.5f), fill = true))
    LyricStyle.MONET -> listOf(GlyphStroke("M3 9.5h7"), GlyphStroke("M3 13.5h5", 0.55f), GlyphStroke(rect(14f, 5f, 7f, 14f, 1.5f), 0.55f))
    LyricStyle.PENDOLO -> listOf(GlyphStroke(circle(8f, 12f, 5f)), GlyphStroke(circle(8f, 12f, 1.4f), fill = true), GlyphStroke("M16 10h5", 0.55f), GlyphStroke("M16 14h4", 0.55f))
    LyricStyle.NEBBIA -> listOf(GlyphStroke(rect(3.5f, 4.5f, 17f, 15f, 1.5f)), GlyphStroke("M7 14c2-3.5 4-3.5 5 0s3 3 5-1", 0.6f), GlyphStroke("M8 8v1.5M15.5 7.5v2", 0.5f))
    LyricStyle.SONNET -> listOf(GlyphStroke("M4 7V4.5h2.5M17.5 4.5H20V7M20 17v2.5h-2.5M6.5 19.5H4V17"), GlyphStroke("M8.5 12h7", 0.6f))
    LyricStyle.HANABI -> listOf(GlyphStroke("M12 21v-8", 0.5f), GlyphStroke("M12 9V4M12 9l3.5-3.5M12 9l-3.5-3.5M12 9h5M12 9H7M12 9l3.5 3.5M12 9l-3.5 3.5"))
    LyricStyle.TEMPERA -> listOf(GlyphStroke(rect(4f, 4f, 16f, 6.5f, 1f)), GlyphStroke(rect(4f, 13.5f, 16f, 6.5f, 1f), 0.45f), GlyphStroke("M4 12h16", 0.7f))
    LyricStyle.LUMIERE -> listOf(GlyphStroke("M10 3.5 6 15M14 3.5 18 15", 0.5f), GlyphStroke("M6 19h12"))
    LyricStyle.MACHI -> listOf(GlyphStroke("M4 20V11h11v9"), GlyphStroke("M6.5 11V8.5h6V11", 0.6f), GlyphStroke("M9 20v-4.5h3V20", 0.6f), GlyphStroke("M18 4l-1.2 3M21 6l-1.2 3M18.5 11l-1.2 3", 0.5f))
    LyricStyle.DIORAMA -> listOf(GlyphStroke(rect(3.5f, 12f, 5f, 5f, 1f), 0.45f), GlyphStroke(rect(9.5f, 6f, 6.5f, 6.5f, 1f)), GlyphStroke(rect(17f, 13.5f, 4f, 4f, 1f), 0.45f))
    LyricStyle.STILL -> listOf(GlyphStroke("M5 7.5h14", 0.35f), GlyphStroke("M3 12h18"), GlyphStroke("M6 16.5h12", 0.35f))
    // Folia FALLBACK_GLYPH
    else -> listOf(GlyphStroke("M5 8h14"), GlyphStroke("M5 12h9", 0.5f), GlyphStroke("M5 16h12", 0.5f))
}

@Composable
internal fun StyleGlyph(style: LyricStyle, color: Color, modifier: Modifier = Modifier) {
    val strokes = remember(style) { glyphOf(style).map { it to PathParser().parsePathString(it.d).toPath() } }
    GlyphCanvas(strokes, color, modifier)
}

@Composable
private fun GlyphCanvas(strokes: List<Pair<GlyphStroke, androidx.compose.ui.graphics.Path>>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            strokes.forEach { (g, path) ->
                rotate(g.rotate, pivot = androidx.compose.ui.geometry.Offset(12f, 12f)) {
                    drawPath(
                        path, color.copy(alpha = color.alpha * g.alpha),
                        style = if (g.fill) Fill else Stroke(width = g.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}


/** Folia backgrounds/<id>/entry.tsx 的 glyph（隐现：上层点阵 + 下层流体；通用：三角 + 圆）。 */
private fun backgroundGlyphOf(id: String): List<GlyphStroke> = when (id) {
    "latent" -> listOf(GlyphStroke("M4.5 7.5h.01M9 7.5h.01M13.5 7.5h.01M18 7.5h.01", 0.55f, width = 2.2f), GlyphStroke("M3 15c3-3.5 6-3.5 9 0s6 3.5 9 0"))
    "common" -> listOf(GlyphStroke("M4 17l5-8 5 8z"), GlyphStroke(circle(17.5f, 8.5f, 3f), 0.55f))
    else -> listOf(GlyphStroke("M5 8h14"), GlyphStroke("M5 12h9", 0.5f), GlyphStroke("M5 16h12", 0.5f))
}

/**
 * Folia VisPlayground「背景」页 BACKGROUND_KIND_GROUP：PictureGrid 3 列（画框同样式架），框里画背景字形。
 * ✋ Folia 框里是背景层的实时小画面（LivePicture），这里同样式架只画字形。
 */
@Composable
internal fun BackgroundPictureGrid(options: List<Pair<String, String>>, selected: String, accent: Color, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(3).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { (id, name) ->
                    val chosen = id == selected
                    val strokes = remember(id) { backgroundGlyphOf(id).map { it to PathParser().parsePathString(it.d).toPath() } }
                    Column(
                        Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                            if (chosen) Box(Modifier.fillMaxSize().border(2.dp, accent, RoundedCornerShape(11.dp)))
                            Box(
                                Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Gyp.preview).border(1.dp, Gyp.line2, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphCanvas(strokes, if (chosen) Gyp.ink else Gyp.t2, Modifier.size(28.dp))
                            }
                        }
                        Text(
                            name, fontSize = 12.sp, lineHeight = 18.sp,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) Gyp.ink else Gyp.t2, maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                repeat(3 - line.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 背景字形（Folia BackgroundModeGlyph），给播放面板「背景类型」取景器用。 */
@Composable
internal fun BackgroundGlyph(id: String, color: Color, modifier: Modifier = Modifier) {
    val strokes = remember(id) { backgroundGlyphOf(id).map { it to PathParser().parsePathString(it.d).toPath() } }
    GlyphCanvas(strokes, color, modifier)
}
