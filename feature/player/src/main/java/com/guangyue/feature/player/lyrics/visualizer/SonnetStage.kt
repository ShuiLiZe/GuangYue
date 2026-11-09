// 对应 Folia 文件：src/components/visualizer/sonnet/（商籁）
//   createSonnetPixiRuntime.ts（每帧：当前段落场景 → 当前镜头 → 镜头姿态 → 转场 alpha / 模糊 / 撕裂）
//   sonnetSceneBuilder.ts（段落场景：背景细线 + 竖排主题名 + 每个镜头的 MG / 引导线 / 文字层，镜头 pivot 落在主角词上）
//   sonnetTextViewBuilder.ts（逐字：字重 900/700/300、主题色辉光 dropShadow、青红色差、半主角回声、巨字描边、词块底纹、开口框）
//   sonnetShotFrame.ts（逐帧：机位路径 + 间隙漂移 + 揭示后呼吸 + 焦点跟踪；逐字入场、视差、色差合拢、回声）
//   sonnetGlitchFilter.ts（mono-glitch 横向撕裂，AGSL 原样搬）、sonnetFrameOverlay.ts（四角固定标记，不随转场）
//   VisualizerSonnet.tsx：字幕层（译文）由外壳画（LyricFlowScreen 的 subtitle overlay），这里不画。
// R4「商籁」按 Folia 原版重做（替换 P6 精简版）。未移植：片尾演职员海报（sonnetCredits / outroBlur）、纯音乐五线谱（sonnetStaffView）、
// 智能动感（beatFx）、后期滤镜（默认关）、Folium 模组调制；几何 MG 变体 18–23 / 27–99 暂退回 0–17（见 SonnetGraphics.kt）。
// 手机化：Folia 的 Math.random（巨字景深、引导线碎片、圆的起笔）改成种子随机；字体用系统无衬线（Folia 默认主题 fontStyle sans）。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ───────────────────────────── 字体与量字 ─────────────────────────────

internal class SonnetFonts {
    private val faces = HashMap<Int, Typeface>()
    fun face(weight: Int): Typeface = faces.getOrPut(weight) {
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, weight, false)
        else if (weight >= 600) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val measure = SonnetMeasure { text, weight, size ->
        measurePaint.typeface = face(weight)
        measurePaint.textSize = size.toFloat()
        measurePaint.measureText(text).toDouble()
    }
}

// ───────────────────────────── 场景视图 ─────────────────────────────

internal class SonnetGlyphView(
    val char: String,
    val baseX: Double,
    val baseY: Double,
    val enterX: Double,
    val enterY: Double,
    val entryRotation: Double,
    val finalRotation: Double,
    val startTime: Double,
    val settleTime: Double,
    val zDepth: Double,
    val bgShape: SonnetTextGeo? = null,
    val caOffset: Double = 0.0,
    /** 半主角回声：两层（dirX, dirY, alphaBase）。 */
    val ghosts: List<DoubleArray>? = null,
    val ghostDuration: Double = 0.0,
) {
    // 每帧写入
    var x = 0.0; var y = 0.0; var rot = 0.0; var scale = 1.0; var alpha = 0.0; var progress = 0.0
}

internal class SonnetSegmentView(
    val role: SonnetRole,
    val fontSize: Double,
    val weight: Int,
    val bodyColor: Int,
    val glowColor: Int,
    val glowStrength: Double,
    val glowBlur: Double,
    val guide: SonnetGuide?,
    val frameDecor: SonnetFrameDecor?,
    val glyphs: List<SonnetGlyphView>,
    /** 参与焦点跟踪的字（非底纹）：baseX, baseY, startTime。 */
    val tracking: List<DoubleArray>,
)

internal class SonnetShotView(
    val shot: SonnetShot,
    val segments: List<SonnetSegmentView>,
    val baseX: Double,
    val baseY: Double,
    val basePivotX: Double,
    val basePivotY: Double,
    val mg: SonnetMg,
    val trackSegments: List<SonnetSegmentView>,
    val revealDoneTime: Double,
    val focusRanges: List<Pair<Double, Double>>,
    val phase: Double,
)

internal class SonnetSceneView(
    val index: Int,
    val bgLines: List<DoubleArray>, // x, y, length, colorIndex(0=secondary,1=accent), width, alpha
    val shots: List<SonnetShotView>,
)

internal class SonnetColors(val primary: Int, val secondary: Int, val accent: Int, val background: Int)

/** resolveGlowColorStrength：最亮通道 110→160 之间 smoothstep。 */
private fun glowStrengthOf(rgb: Int): Double {
    val v = max((rgb shr 16) and 255, max((rgb shr 8) and 255, rgb and 255)).toDouble()
    val p = clamp01((v - 110) / 50)
    return p * p * (3 - 2 * p)
}

internal fun buildSonnetScene(
    program: SonnetProgram,
    index: Int,
    width: Double,
    height: Double,
    lyricsFontScale: Double,
    colors: SonnetColors,
    fonts: SonnetFonts,
    glowEnabled: Boolean,
): SonnetSceneView {
    val paragraph = program.paragraphs[index]
    val sceneSeed = sonnetSceneSeed(program, paragraph)
    val bgLines = ArrayList<DoubleArray>()
    val density = Math.round(4 + 1.0 * 5).toInt()
    for (i in 0 until density) {
        val x = ((sceneSeed + i * 97L) % 997) / 997.0 * width
        val y = ((sceneSeed + i * 193L) % 991) / 991.0 * height
        val length = 32.0 + ((sceneSeed + i * 43L) % 180)
        bgLines += doubleArrayOf(x, y, length, if (i % 2 != 0) 0.0 else 1.0, if (i % 3 == 0) 2.0 else 1.0, 0.12 + (i % 4) * 0.04)
    }
    val shots = paragraph.shots.mapIndexed { shotIndex, shot ->
        val compiled = shot.lineIndices.mapNotNull { li -> paragraph.lines.firstOrNull { it.sourceIndex == li } }
        val linesSegments = compiled.map { l -> l.segments.filter { sonnetIsLayoutSegment(it) } }.filter { it.isNotEmpty() }
        val segments = linesSegments.flatten()
        val wordCount = max(1, segments.count { it.isWordLike })
        val fontSize = sonnetShotFontSize(shot.kind, wordCount, width, lyricsFontScale)
        val placements = resolveSonnetTypographyLayout(linesSegments, shot.kind, width, height, fontSize, fonts.measure)
        val shotSeed = sceneSeed + shotIndex * 97L
        val mg = buildSonnetShotMg(shot.kind, width, height, shotSeed, colors.primary, colors.secondary, colors.accent, withIcons = true)
        val views = placements.map { placement ->
            buildSonnetSegmentView(segments[placement.segmentIndex], placement, fontSize, shot, paragraph.kind, width, colors, fonts, glowEnabled)
        }
        val hero = placements.firstOrNull { it.role == SonnetRole.HERO }
        val focusX: Double
        val focusY: Double
        if (shot.kind == SonnetShotKind.POSTER_BLOCKS) { focusX = 0.0; focusY = 0.0 }
        else if (hero != null) { focusX = hero.x; focusY = hero.y }
        else {
            // getLocalBounds 的中心：用版面框近似
            val l = placements.minOfOrNull { it.x - it.measuredWidth / 2 } ?: 0.0
            val r = placements.maxOfOrNull { it.x + it.measuredWidth / 2 } ?: 0.0
            val t = placements.minOfOrNull { it.y - it.measuredHeight / 2 } ?: 0.0
            val b = placements.maxOfOrNull { it.y + it.measuredHeight / 2 } ?: 0.0
            focusX = (l + r) / 2; focusY = (t + b) / 2
        }
        val baseX = width * (if (shot.kind == SonnetShotKind.POSTER_BLOCKS) 0.5 else 0.5 + shot.camera.x)
        val baseY = height * (if (shot.kind == SonnetShotKind.POSTER_BLOCKS) 0.5 else 0.48 + shot.camera.y + (if (shotIndex % 2 != 0) 0.025 else -0.025))
        var track = views.filter { it.role != SonnetRole.DECORATION && it.tracking.isNotEmpty() }
        if (track.isEmpty()) track = views.filter { it.tracking.isNotEmpty() }
        val revealDone = if (track.isNotEmpty()) track.maxOf { it.tracking.last()[2] } else shot.endTime
        val ranges = track.map { (it.tracking.firstOrNull()?.get(2) ?: shot.startTime) to (it.tracking.lastOrNull()?.get(2) ?: shot.endTime) }
        SonnetShotView(
            shot, views, baseX, baseY, focusX, focusY, mg, track, revealDone, ranges,
            phase = (sonnetHash(shot.id) % 1024) / 1024.0 * PI * 2,
        )
    }
    return SonnetSceneView(index, bgLines, shots)
}

private fun buildSonnetSegmentView(
    segment: SonnetSegment,
    original: SonnetPlacement,
    baseFontSize: Double,
    shot: SonnetShot,
    paragraphKind: SonnetParagraphKind,
    width: Double,
    colors: SonnetColors,
    fonts: SonnetFonts,
    glowEnabled: Boolean,
): SonnetSegmentView {
    val fontSize = baseFontSize * original.fontScale
    val normalSeed = sonnetHash("${segment.text}:${segment.startOffset}:${segment.endOffset}:${original.segmentIndex}:normal-offset")
    val (nx, ny) = sonnetNormalOffset(original.role, original.layoutVertical, original.rotation, fontSize, normalSeed / 4294967295.0)
    val placement = SonnetPlacement(
        original.segmentIndex, original.displayText, original.role, original.fontScale, original.measuredWidth, original.measuredHeight,
        original.x + nx, original.y + ny, original.rotation, original.enterX, original.enterY, original.vertical, original.layoutVertical, original.timingPhase,
    )
    val role = placement.role
    val glowColor = if (role.isEmphasis()) colors.primary else colors.accent
    val isDecoration = role == SonnetRole.DECORATION
    val weight = sonnetRoleWeight(role)
    // resolveSonnetSegmentDepth：巨字前后景深 ±0.5–1.3（Folia 用 Math.random，这里按词块种子）
    val depthRng = SonnetRandom(sonnetHash("${segment.text}:${placement.segmentIndex}:${placement.x}:depth"))
    val zDepth = if (!isDecoration) 0.0 else if (depthRng.next() > 0.5) 0.5 + depthRng.next() * 0.8 else -0.5 - depthRng.next() * 0.8
    val hasGlow = glowEnabled && !isDecoration
    val glowBlur = max(12.0, fontSize * 0.18)
    val glowStrength = if (hasGlow) glowStrengthOf(glowColor) else 0.0
    val isSemi = role == SonnetRole.SEMI_HERO
    val ghostNormal = run {
        val sx = if (placement.layoutVertical) 1.0 else 0.0
        val sy = if (placement.layoutVertical) 0.0 else 1.0
        val c = cos(-placement.rotation); val s = sin(-placement.rotation)
        doubleArrayOf(sx * c - sy * s, sx * s + sy * c)
    }
    val ghostSpread = fontSize * 0.85
    val ghostDuration = min(0.7, max(0.4, (shot.endTime - shot.startTime) * 0.12 + 0.1))
    val layout = buildSonnetGlyphLayout(segment, placement, fontSize, { ch -> fonts.measure.width(ch, weight, fontSize) }, shot.startTime, shot.endTime)
    val glyphs = ArrayList<SonnetGlyphView>()
    layout.forEach { g ->
        var ghosts: List<DoubleArray>? = null
        if (isSemi) {
            val side = if (normalSeed % 2 == 0L) 1 else -1
            ghosts = (1..2).map { layer ->
                val factor = if (layer == 1) 1.0 else 1.7
                doubleArrayOf(ghostNormal[0] * side * factor * ghostSpread, ghostNormal[1] * side * factor * ghostSpread, if (layer == 1) 0.3 else 0.16)
            }
        }
        glyphs += SonnetGlyphView(
            char = g.char, baseX = g.baseX, baseY = g.baseY, enterX = g.enterX, enterY = g.enterY,
            entryRotation = g.entryRotation, finalRotation = placement.rotation, startTime = g.startTime, settleTime = g.settleTime,
            zDepth = zDepth,
            caOffset = if (isDecoration) 0.0 else fontSize * (if (role.isEmphasis()) 0.025 else 0.010),
            ghosts = ghosts, ghostDuration = ghostDuration,
        )
    }
    // 词块底纹（buildSonnetTextFixedGeo）
    val textSeed = segment.text.sumOf { it.code } + placement.segmentIndex * 13
    val chorusEffect = paragraphKind == SonnetParagraphKind.CHORUS || textSeed % 100 < 35
    val threshold = if (chorusEffect) 26 else 15
    val hasFrame = sonnetFrameDecorVariant(segment) >= 0
    val allGlyphs = ArrayList<SonnetGlyphView>()
    if (textSeed % 100 < threshold && !isDecoration && segment.isWordLike && !hasFrame && glyphs.isNotEmpty()) {
        val first = glyphs[0]
        allGlyphs += SonnetGlyphView(
            char = "", baseX = placement.x, baseY = placement.y, enterX = placement.enterX, enterY = placement.enterY,
            entryRotation = 0.0, finalRotation = placement.rotation, startTime = first.startTime, settleTime = first.settleTime,
            zDepth = -0.5 - (textSeed % 5) * 0.1,
            bgShape = buildSonnetTextFixedGeo(textSeed, chorusEffect, fontSize, width, colors.primary, colors.secondary, colors.accent),
        )
    }
    allGlyphs.addAll(glyphs)
    val firstStart = allGlyphs.firstOrNull()?.startTime ?: shot.startTime
    val guide = if (isDecoration) null else buildSonnetGuide(segment, placement, fontSize, firstStart, colors.accent, colors.secondary, normalSeed xor 0x5bd1e995L)
    val frame = buildSonnetFrameDecor(segment, placement, fontSize, colors.primary, shot.startTime, shot.endTime, glyphs.firstOrNull()?.startTime ?: segment.startTime)
    return SonnetSegmentView(
        role = role, fontSize = fontSize, weight = weight, bodyColor = colors.primary, glowColor = glowColor,
        glowStrength = glowStrength, glowBlur = glowBlur, guide = guide, frameDecor = frame, glyphs = allGlyphs,
        tracking = allGlyphs.filter { it.bgShape == null }.map { doubleArrayOf(it.baseX, it.baseY, it.startTime) },
    )
}

// ───────────────────────────── 每帧绘制 ─────────────────────────────

internal class SonnetPaints {
    val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val ca = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    val label = Paint(Paint.ANTI_ALIAS_FLAG)
    val star = Paint(Paint.ANTI_ALIAS_FLAG)
    val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
}

/** Lucide「Flower」（sonnetIcons 的兜底图标），24×24 画布，描边。 */
private val FLOWER_PATH: android.graphics.Path by lazy {
    val p = android.graphics.Path()
    p.addCircle(12f, 12f, 3f, android.graphics.Path.Direction.CW)
    // M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5a4.5 4.5 0 1 1 4.5 4.5 4.5 4.5 0 1 1-4.5 4.5：四片圆瓣
    fun petal(cx: Float, cy: Float, start: Float) {
        p.arcTo(android.graphics.RectF(cx - 4.5f, cy - 4.5f, cx + 4.5f, cy + 4.5f), start, 270f, false)
    }
    p.moveTo(12f, 16.5f)
    petal(7.5f, 16.5f, 0f)      // (12,16.5) → (7.5,12)，圆心 (7.5,16.5)
    petal(7.5f, 7.5f, 90f)      // (7.5,12) → (12,7.5)
    petal(16.5f, 7.5f, 180f)    // (12,7.5) → (16.5,12)
    petal(16.5f, 16.5f, 270f)   // (16.5,12) → (12,16.5)
    p.moveTo(12f, 7.5f); p.lineTo(12f, 9f)
    p.moveTo(7.5f, 12f); p.lineTo(9f, 12f)
    p.moveTo(16.5f, 12f); p.lineTo(15f, 12f)
    p.moveTo(12f, 16.5f); p.lineTo(12f, 15f)
    p.moveTo(8f, 8f); p.lineTo(9.88f, 9.88f)
    p.moveTo(14.12f, 9.88f); p.lineTo(16f, 8f)
    p.moveTo(8f, 16f); p.lineTo(9.88f, 14.12f)
    p.moveTo(14.12f, 14.12f); p.lineTo(16f, 16f)
    p
}

private fun deg(rad: Double) = Math.toDegrees(rad).toFloat()

/** 画一个段落场景（Folia scene.container 及其子层）；转场的 alpha / 模糊 / 撕裂由外层 graphicsLayer 处理。 */
internal fun drawSonnetScene(
    c: NativeCanvas,
    program: SonnetProgram,
    scene: SonnetSceneView,
    time: Double,
    width: Double,
    height: Double,
    colors: SonnetColors,
    fonts: SonnetFonts,
    paints: SonnetPaints,
    themeLabel: String,
    camera: Double,
    motion: Double,
) {
    val paragraph = program.paragraphs[scene.index]
    // 场景背景层：底色 10% + 细线 + 竖排主题名
    paints.shape.style = Paint.Style.FILL
    paints.shape.color = sonnetArgb(colors.background, 0.10)
    c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paints.shape)
    paints.shape.style = Paint.Style.STROKE
    scene.bgLines.forEach { l ->
        paints.shape.strokeWidth = l[4].toFloat()
        paints.shape.color = sonnetArgb(if (l[3] == 0.0) colors.secondary else colors.accent, l[5])
        c.drawLine(l[0].toFloat(), l[1].toFloat(), min(width, l[0] + l[2]).toFloat(), l[1].toFloat(), paints.shape)
    }
    paints.label.typeface = fonts.face(700)
    paints.label.textSize = 14f
    paints.label.letterSpacing = 4f / 14f
    paints.label.color = sonnetArgb(colors.primary, 0.2)
    paints.label.textAlign = Paint.Align.LEFT
    c.save()
    c.translate(20f, (height - 20).toFloat())
    c.rotate(-90f)
    c.drawText(themeLabel, 0f, -paints.label.fontMetrics.descent, paints.label)
    c.restore()

    var active = 0
    for (i in scene.shots.indices.reversed()) if (time >= scene.shots[i].shot.startTime) { active = i; break }
    val view = scene.shots.getOrNull(active) ?: return
    drawSonnetShot(c, view, paragraph.kind, time, width, height, colors, fonts, paints, camera, motion)
}

private fun drawSonnetShot(
    c: NativeCanvas,
    view: SonnetShotView,
    paragraphKind: SonnetParagraphKind,
    time: Double,
    width: Double,
    height: Double,
    colors: SonnetColors,
    fonts: SonnetFonts,
    paints: SonnetPaints,
    camera: Double,
    motion: Double,
) {
    val shot = view.shot
    val progress = sonnetShotProgress(shot, time)
    val frame = sonnetShotMotionFrame(shot.kind, progress)
    val gap = max(0.0, time - shot.endTime)
    if (gap > 0) {
        val tail = sonnetShotMotionFrame(shot.kind, 0.8)
        val drift = (1 - exp(-gap * 0.4)) * 2.0
        frame.x += (frame.x - tail.x) * drift
        frame.y += (frame.y - tail.y) * drift
        frame.scale += (frame.scale - tail.scale) * drift
        frame.rotation += (frame.rotation - tail.rotation) * drift
    }
    val breathW = sonnetBreathWeight(time, view.revealDoneTime)
    if (breathW > 0) {
        val b = sonnetCameraBreath(time, view.phase)
        frame.x += b.x * breathW; frame.y += b.y * breathW; frame.scale += b.scale * breathW; frame.rotation += b.rotation * breathW
    }
    var fx = view.basePivotX
    var fy = view.basePivotY
    if (view.trackSegments.isNotEmpty()) {
        val sample = { ft: Double ->
            val w = sonnetFocusWeights(view.focusRanges, ft)
            var x = 0.0; var y = 0.0
            view.trackSegments.forEachIndexed { i, s ->
                if (s.tracking.isEmpty()) return@forEachIndexed
                val p = sonnetSegmentCameraFocus(s.tracking, ft)
                x += p.first * w[i]; y += p.second * w[i]
            }
            x to y
        }
        val focusTime = max(shot.startTime, min(time, shot.endTime))
        val f = sonnetSmoothedFocus(focusTime, shot.startTime, shot.endTime, sample)
        fx = f.first; fy = f.second
    }
    val pivotX = view.basePivotX + (fx - view.basePivotX) * camera
    val pivotY = view.basePivotY + (fy - view.basePivotY) * camera
    val scale = shot.camera.zoom * (1 + (frame.scale - 1) * camera)
    val rotation = (shot.camera.rotation + frame.rotation) * camera
    val cx = view.baseX + frame.x * width * camera
    val cy = view.baseY + frame.y * height * camera

    c.save()
    c.translate(cx.toFloat(), cy.toFloat())
    c.rotate(deg(rotation))
    c.scale(scale.toFloat(), scale.toFloat())
    c.translate(-pivotX.toFloat(), -pivotY.toFloat())

    // ── MG 层：geo → 定格几何（反向转回正）→ HUD/bg → 漂浮小形
    val mg = view.mg
    val mgP = sonnetMgProgress(shot, time)
    mg.geo?.let { g ->
        c.save(); c.rotate(deg(g.rotation)); g.draw(c, mgP, 1.0, paints.shape); c.restore()
    }
    if (mg.fixedParts.isNotEmpty()) {
        c.save()
        c.rotate(deg(-rotation))
        mg.fixedParts.forEach { part ->
            if (part.mask != null) { c.save(); c.clipRect(part.mask); part.g.draw(c, mgP, 1.0, paints.shape); c.restore() }
            else part.g.draw(c, mgP, 1.0, paints.shape)
        }
        c.restore()
    }
    mg.bg.draw(c, mgP, 1.0, paints.shape)
    run {
        val px = frame.x * width * camera * 0.4
        val py = frame.y * height * camera * 0.4
        c.save()
        c.translate(px.toFloat(), py.toFloat())
        c.rotate(deg((time - shot.startTime) * 0.05))
        val ps = (1 + (frame.scale - 1) * 0.3).toFloat()
        c.scale(ps, ps)
        val sceneDuration = max(0.01, shot.endTime - shot.startTime)
        mg.particles.forEach { p ->
            c.save()
            c.translate(p.x.toFloat(), p.y.toFloat())
            c.rotate(deg(p.rotation))
            if (p.shape != null) p.shape.draw(c, 1.0, 1.0, paints.shape)
            else if (p.icon != null) {
                val icon = p.icon
                val entryDuration = minOf(max(0.01, icon.preferredDuration), max(0.08, sceneDuration * 0.18), sceneDuration)
                val entryDelay = clamp01(icon.entryPhase) * max(0.0, sceneDuration - entryDuration)
                val ep = clamp01((time - shot.startTime - entryDelay) / entryDuration)
                val ee = 1 - (1 - ep).pow(3)
                val loop = (sin((time - shot.startTime) * PI * 0.7 + icon.phase) + 1) * 0.5
                val a = min(1.0, 0.85 * ee * (0.72 + loop * 0.03))
                if (a > 0.002) {
                    val s = icon.size * (0.72 + ee * 0.28) * (1 + loop * 0.025) / 24.0
                    c.scale(s.toFloat(), s.toFloat())
                    c.translate(-12f, -12f)
                    paints.icon.strokeWidth = (3.5 * 24.0 / 192.0).toFloat()
                    paints.icon.color = sonnetArgb(icon.color, a)
                    c.drawPath(FLOWER_PATH, paints.icon)
                }
            }
            c.restore()
        }
        c.restore()
    }

    // ── 引导线层
    view.segments.forEach { s ->
        val g = s.guide ?: return@forEach
        if (time < g.startTime || time > g.endTime) return@forEach
        g.draw(c, clamp01((time - g.startTime) / max(0.001, g.endTime - g.startTime)), g.maxAlpha, paints.shape)
    }

    // ── 逐字姿态
    val parallaxBaseX = frame.x * width * camera
    val parallaxBaseY = frame.y * height * camera
    view.segments.forEach { s ->
        s.glyphs.forEach { g ->
            val p = sonnetSegmentProgress(g.startTime, g.settleTime, time)
            val waiting = time < g.startTime
            val offset = (1 - p) * motion
            g.progress = p
            g.alpha = if (waiting) 0.0 else 0.16 + p * 0.84
            val sc = if (s.role.isEmphasis() && shot.kind == SonnetShotKind.TYPE_IMPACT) 0.52 + p * 0.48 else 0.86 + p * 0.14
            g.scale = sc * (1 + g.zDepth * 0.45)
            g.x = g.baseX + g.enterX * offset + parallaxBaseX * g.zDepth * 2.5
            g.y = g.baseY + g.enterY * offset + parallaxBaseY * g.zDepth * 2.5
            g.rot = g.finalRotation + g.entryRotation * offset
        }
    }

    // ── 文字层：底纹 / 开口框（后加的在下）→ 色差层 → 逐字
    val under = ArrayList<Any>()
    view.segments.forEach { s ->
        s.glyphs.firstOrNull { it.bgShape != null }?.let { under += it }
        s.frameDecor?.let { under += it }
    }
    for (item in under.reversed()) {
        if (item is SonnetGlyphView) {
            if (item.alpha <= 0.002) continue
            c.save()
            c.translate(item.x.toFloat(), item.y.toFloat())
            c.rotate(deg(item.rot))
            c.scale(item.scale.toFloat(), item.scale.toFloat())
            val geo = item.bgShape!!
            if (geo.rotation != 0.0) c.rotate(deg(geo.rotation))
            geo.g.draw(c, 1.0, item.alpha, paints.shape)
            c.restore()
        } else if (item is SonnetFrameDecor) {
            item.draw(c, clamp01((time - item.startTime) / max(0.001, item.endTime - item.startTime)), 1.0, paints.shape)
        }
    }
    // 色差（青 / 红，screen 叠加）
    view.segments.forEach { s ->
        if (s.role == SonnetRole.DECORATION) return@forEach
        configureText(paints.ca, s, fonts)
        val hero = s.role.isEmphasis()
        val caA = if (hero) 0.8 else 0.5
        val base = paints.ca.fontMetrics.let { -(it.ascent + it.descent) / 2f }
        s.glyphs.forEach { g ->
            if (g.bgShape != null || g.alpha <= 0.002 || g.caOffset == 0.0) return@forEach
            val o = g.caOffset * (1 - sonnetEaseInOut(g.progress) * 0.8)
            c.save()
            c.translate(g.x.toFloat(), g.y.toFloat())
            c.rotate(deg(g.rot))
            c.scale(g.scale.toFloat(), g.scale.toFloat())
            for (k in 0..1) {
                val tint = if (k == 0) 0x00ffff else 0xff0044
                val dx = if (k == 0) -o else o
                val dy = if (k == 0) o * 0.5 else -o * 0.5
                paints.ca.color = sonnetArgb(tintRgb(s.bodyColor, tint), caA * g.alpha)
                if (s.glowStrength > 0) paints.ca.setShadowLayer(shadowRadius(s.glowBlur), 0f, 0f, sonnetArgb(tintRgb(s.glowColor, tint), 0.8 * s.glowStrength))
                else paints.ca.clearShadowLayer()
                c.drawText(g.char, dx.toFloat(), (dy + base).toFloat(), paints.ca)
            }
            c.restore()
        }
    }
    // 逐字本体
    view.segments.forEach { s ->
        val deco = s.role == SonnetRole.DECORATION
        configureText(paints.text, s, fonts)
        val base = paints.text.fontMetrics.let { -(it.ascent + it.descent) / 2f }
        val outline = max(1.0, min(8.0, s.fontSize * 0.006)).toFloat()
        s.glyphs.forEach { g ->
            if (g.bgShape != null || g.alpha <= 0.002) return@forEach
            c.save()
            c.translate(g.x.toFloat(), g.y.toFloat())
            c.rotate(deg(g.rot))
            c.scale(g.scale.toFloat(), g.scale.toFloat())
            // 半主角回声：描边空心字沿法线散开
            g.ghosts?.let { ghosts ->
                val gp = clamp01((time - g.startTime) / g.ghostDuration)
                if (gp > 0 && gp < 1) {
                    val env = if (gp <= 0.2) gp / 0.2 else (1 - (gp - 0.2) / 0.8).pow(2)
                    val spread = 1 - (1 - gp).pow(3)
                    paints.text.style = Paint.Style.STROKE
                    paints.text.strokeWidth = outline
                    paints.text.clearShadowLayer()
                    ghosts.forEach { gh ->
                        paints.text.color = sonnetArgb(s.glowColor, env * gh[2] * g.alpha)
                        c.drawText(g.char, (gh[0] * spread).toFloat(), (gh[1] * spread + base).toFloat(), paints.text)
                    }
                    configureText(paints.text, s, fonts)
                }
            }
            if (deco) {
                paints.text.style = Paint.Style.STROKE
                paints.text.strokeWidth = outline
                paints.text.color = sonnetArgb(s.glowColor, 0.2 * g.alpha)
            } else {
                paints.text.style = Paint.Style.FILL
                paints.text.color = sonnetArgb(s.bodyColor, g.alpha)
            }
            c.drawText(g.char, 0f, base, paints.text)
            c.restore()
        }
    }
    c.restore()
}

private fun configureText(p: Paint, s: SonnetSegmentView, fonts: SonnetFonts) {
    p.typeface = fonts.face(s.weight)
    p.textSize = s.fontSize.toFloat()
    p.style = Paint.Style.FILL
    if (s.glowStrength > 0 && s.role != SonnetRole.DECORATION) p.setShadowLayer(shadowRadius(s.glowBlur), 0f, 0f, sonnetArgb(s.glowColor, 0.8 * s.glowStrength))
    else p.clearShadowLayer()
}

/** Canvas2D shadowBlur b（σ = b/2）→ Android setShadowLayer 半径（σ = 0.57735·r + 0.5）。 */
private fun shadowRadius(blur: Double) = max(0.5, (blur / 2 - 0.5) / 0.57735).toFloat()

private fun tintRgb(rgb: Int, tint: Int): Int {
    val r = ((rgb shr 16) and 255) * ((tint shr 16) and 255) / 255
    val g = ((rgb shr 8) and 255) * ((tint shr 8) and 255) / 255
    val b = (rgb and 255) * (tint and 255) / 255
    return (r shl 16) or (g shl 8) or b
}

// ───────────────────────────── mono-glitch（sonnetGlitchFilter.ts 原样搬 AGSL） ─────────────────────────────

private const val SONNET_GLITCH_AGSL = """
uniform shader content;
uniform float2 size;
uniform float uAmount;
uniform float uSeed;
float hash(float2 v) { return fract(sin(dot(v, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
    float2 uv = p / size;
    float coarseBand = floor(uv.y * 26.0);
    float fineBand = floor(uv.y * 110.0);
    float coarseNoise = hash(float2(coarseBand, uSeed));
    float fineNoise = hash(float2(fineBand + 41.0, uSeed * 1.37));
    float coarseGate = step(0.58, coarseNoise);
    float fineGate = step(0.88, fineNoise);
    float coarseShift = (hash(float2(coarseBand + 17.0, uSeed)) * 2.0 - 1.0) * coarseGate * uAmount * 0.095;
    float fineShift = (hash(float2(fineBand + 73.0, uSeed)) * 2.0 - 1.0) * fineGate * uAmount * 0.035;
    float2 s = float2(clamp(uv.x + coarseShift + fineShift, 0.0, 1.0), uv.y);
    half4 color = content.eval(s * size);
    float tear = (coarseGate * (coarseNoise - 0.58) + fineGate * 0.12) * uAmount;
    color.rgb *= half(1.0 + tear * 0.42);
    return color;
}
"""

// ───────────────────────────── Compose 舞台 ─────────────────────────────

@Composable
internal fun SonnetStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    primaryColor: Color,
    secondaryColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    @Suppress("UNUSED_PARAMETER") fontFamily: FontFamily,
    reduceMotion: Boolean,
    @Suppress("UNUSED_PARAMETER") topReserve: Dp,
    @Suppress("UNUSED_PARAMETER") bottomReserve: Dp,
    modifier: Modifier = Modifier,
    /** Folia 的 seed（歌曲身份）：同一首歌每次编排一样。 */
    seed: String = "sonnet",
    /** 竖排主题名（Folia `[ THEME ] ${theme.name}`）。 */
    themeName: String = "Midnight Default",
) {
    val program = remember(lines, seed) { compileSonnetProgram(lines, seed) }
    val colors = SonnetColors(primaryColor.toArgb() and 0xffffff, secondaryColor.toArgb() and 0xffffff, accentColor.toArgb() and 0xffffff, backgroundColor.toArgb() and 0xffffff)
    val fonts = remember { SonnetFonts() }
    val paints = remember { SonnetPaints() }
    val cache = remember(program, colors.primary, colors.secondary, colors.accent, lyricsFontScale) { HashMap<String, SonnetSceneView>() }
    val glitchShader = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(SONNET_GLITCH_AGSL) else null }
    val label = "[ THEME ] ${themeName.uppercase()}"
    val camera = if (reduceMotion) 0.0 else 1.0
    val motion = if (reduceMotion) 0.0 else 1.0
    val transitions = !reduceMotion
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (program.paragraphs.isEmpty()) return@graphicsLayer
                    val t = clock.readForDraw()
                    val pi = findSonnetParagraphIndexAtTime(program, t)
                    val shots = program.paragraphs[pi].shots
                    var si = 0
                    for (i in shots.indices.reversed()) if (t >= shots[i].startTime) { si = i; break }
                    val f = sonnetSceneTransitionFrame(program, pi, si, t, transitions)
                    alpha = f.alpha.toFloat().coerceIn(0f, 1f)
                    renderEffect = when {
                        f.blur > 0.01 && Build.VERSION.SDK_INT >= 31 -> {
                            val r = (f.blur * density).toFloat()
                            BlurEffect(r, r, TileMode.Decal)
                        }
                        f.glitch > 0.01 && glitchShader != null && Build.VERSION.SDK_INT >= 33 && size.width > 0f -> {
                            glitchShader.setFloatUniform("size", size.width, size.height)
                            glitchShader.setFloatUniform("uAmount", f.glitch.toFloat())
                            glitchShader.setFloatUniform("uSeed", f.glitchSeed.toFloat())
                            RenderEffect.createRuntimeShaderEffect(glitchShader, "content").asComposeRenderEffect()
                        }
                        else -> null
                    }
                },
        ) {
            if (program.paragraphs.isEmpty()) return@Canvas
            val t = clock.readForDraw()
            val w = size.width / density
            val h = size.height / density
            val pi = findSonnetParagraphIndexAtTime(program, t)
            val key = "$pi:${w.toInt()}x${h.toInt()}"
            val scene = cache.getOrPut(key) {
                if (cache.size > 4) cache.clear()
                buildSonnetScene(program, pi, w.toDouble(), h.toDouble(), lyricsFontScale, colors, fonts, glowEnabled = true)
            }
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density, density)
                drawSonnetScene(nc, program, scene, t, w.toDouble(), h.toDouble(), colors, fonts, paints, label, camera, motion)
                nc.restore()
            }
        }
        // 画面固定标记（overlayContainer，不随段落转场）
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density, density)
                drawSonnetFrameOverlay(nc, (size.width / density).toDouble(), (size.height / density).toDouble(), colors.primary, paints.shape, paints.star)
                nc.restore()
            }
        }
    }
}

@Suppress("unused")
private fun sonnetUnusedAbs(v: Double) = abs(v)
