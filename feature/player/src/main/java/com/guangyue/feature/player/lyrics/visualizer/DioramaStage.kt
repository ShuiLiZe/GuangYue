// 镜台舞台（取证版）：把粒子层（DioramaParticleLayer 两层）、背景尘埃（DioramaScene.tsx <points> + PointsMaterial）、
// 邻行整行平面、当前行逐单元三层平面（DioramaActiveUnitPlanes.tsx）放进同一个 GLSurfaceView，镜头停在当前行静止位。
// 每帧写入走 DioramaSceneFrame.kt（recycleDioramaMoteWindow / updateDioramaNeighborLines / updateDioramaActiveUnits），
// 雾 = Fog(bg, 12, 30)。运镜 DioramaCameraRig；换歌 / 单曲循环照 VisualizerDiorama.tsx 状态机（新走廊雾外偏移、离场窗口留到飞行结束）。
// 取证页 dioramastage / dioramastageclouds / dioramarig，播放页 LyricStyle.DIORAMA。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val MOTE_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProj;
uniform float uSize;
uniform float uScale;
out float vFogDepth;
void main() {
    vec4 mv = uView * uModel * vec4(aPos, 1.0);
    vFogDepth = -mv.z;
    gl_PointSize = uSize * (uScale / -mv.z);
    gl_Position = uProj * mv;
}
"""

private const val MOTE_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in float vFogDepth;
uniform vec3 uColor;
uniform float uOpacity;
uniform vec3 uFogColor;
uniform float uFogNear;
uniform float uFogFar;
out vec4 fragColor;
vec3 linearToSrgb(vec3 c) { return mix(c * 12.92, pow(c, vec3(0.41666)) * 1.055 - 0.055, vec3(greaterThan(c, vec3(0.0031308)))); }
void main() {
    vec3 c = mix(uColor, uFogColor, smoothstep(uFogNear, uFogFar, vFogDepth));
    fragColor = vec4(linearToSrgb(c), uOpacity);
}
"""

/** 背景尘埃：PointsMaterial（方点、sizeAttenuation、普通混合、不写深度、吃雾）。 */
private class DioramaMoteLayer {
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var buffer: FloatBuffer? = null

    fun init(capacityFloats: Int) {
        program = dioramaLinkProgram(MOTE_VERTEX_SHADER, MOTE_FRAGMENT_SHADER)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        buffer = ByteBuffer.allocateDirect(capacityFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacityFloats * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    fun upload(positions: FloatArray, floats: Int) {
        val b = buffer ?: return
        b.position(0); b.put(positions, 0, floats); b.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, floats * 4, b)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(count: Int, model: FloatArray, view: FloatArray, proj: FloatArray, size: Float, scale: Float, color: DioramaLinearColor, opacity: Float, fog: DioramaLinearColor) {
        if (program == 0 || count == 0) return
        GLES30.glUseProgram(program)
        fun loc(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(loc("uModel"), 1, false, model, 0)
        GLES30.glUniformMatrix4fv(loc("uView"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(loc("uProj"), 1, false, proj, 0)
        GLES30.glUniform1f(loc("uSize"), size)
        GLES30.glUniform1f(loc("uScale"), scale)
        GLES30.glUniform3fv(loc("uColor"), 1, color.toFloats(), 0)
        GLES30.glUniform1f(loc("uOpacity"), opacity)
        GLES30.glUniform3fv(loc("uFogColor"), 1, fog.toFloats(), 0)
        GLES30.glUniform1f(loc("uFogNear"), DIORAMA_FOG_NEAR.toFloat())
        GLES30.glUniform1f(loc("uFogFar"), DIORAMA_FOG_FAR.toFloat())
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }
}

/** Compose → GL 线程的输入：当前歌的歌词与种子（换歌时整体换掉，渲染线程每帧读一次）。 */
private class DioramaStageSong(val lines: List<Line>, val seed: String)

/** colors = 主题目标色（particle 对比色版 + 原四色），渲染线程按 COLOR_DAMP_RATE 阻尼追过去。 */
private class DioramaStageInput(@Volatile var song: DioramaStageSong, @Volatile var colors: Pair<DioramaParticleColors, DioramaDampedColors>)

/** 挂载窗口：当前行（与转场中离场行）的可见行、当前行单元、粒子缓冲、静止位镜头。 */
private class DioramaStageWindow(
    val globalIndex: Int,
    val outgoingIndex: Int?,
    val visible: List<DioramaVisibleLine>,
    val units: List<DioramaLyricUnit>,
    val particles: DioramaParticleGeometryData,
    val waveNumberMax: Double,
    val restEye: DioramaVec,
    val frame: DioramaFrame,
)

private val stageMotion = resolveDioramaMotionParams()

/** VisualizerDiorama.tsx：PRUNE_MARGIN_BEHIND / LOOP_RESTART_MAX_INDEX。 */
private const val DIORAMA_PRUNE_MARGIN_BEHIND = 4
private const val DIORAMA_LOOP_RESTART_MAX_INDEX = 1

private fun buildDioramaStageWindow(sq: DioramaSequencerState, idx: Int, outgoing: Int?, corridor: Boolean, density: Double): DioramaStageWindow {
    val total = dioramaTotalGlobalLines(sq)
    val vis = DioramaGeometryVisibility(mode = if (corridor) DioramaGeometryMode.CORRIDOR else DioramaGeometryMode.CLOUDS)
    val mounted = resolveDioramaMountedIndices(idx, outgoing, total)
    val spans = buildDioramaCorridorSpans(sq, idx, outgoing, vis)
    val clusters = buildDioramaParticleClusters(sq, mounted, stageMotion, vis, 1.0)
    val (data, k) = buildDioramaParticleBuffer(corridor, clusters, spans, density)
    val resolved = dioramaResolveGlobal(sq, idx)!!
    val frame = resolved.frame
    val eye = DioramaVec(
        frame.position.x - frame.forward.x * DIORAMA_HERO_DISTANCE + frame.up.x * DIORAMA_CAMERA_LIFT,
        frame.position.y - frame.forward.y * DIORAMA_HERO_DISTANCE + frame.up.y * DIORAMA_CAMERA_LIFT,
        frame.position.z - frame.forward.z * DIORAMA_HERO_DISTANCE + frame.up.z * DIORAMA_CAMERA_LIFT,
    )
    val visible = buildDioramaVisibleLines(sq, mounted, idx, outgoing, stageMotion.weaveScale)
    val activeLine = resolved.line
    val units = if (activeLine == null) emptyList() else splitDioramaLyricUnits(activeLine, GraphemeTimings.buildDenseLineGraphemeTimeline(activeLine))
    return DioramaStageWindow(idx, outgoing, visible, units, data, k, eye, frame)
}

private class DioramaStageRenderer(
    private val input: DioramaStageInput,
    private val corridor: Boolean,
    private val density: Double,
    private val restCamera: Boolean,
    private val timeSeconds: () -> Double,
) : GLSurfaceView.Renderer {
    private val contrast = DioramaParticleLayer()
    private val glow = DioramaParticleLayer()
    private val contrastU = DioramaParticleUniforms(input.colors.first, 0f, 0f)
    private val glowU = DioramaParticleUniforms(input.colors.first, 1f, 0.6f)
    /** DioramaScene.tsx：damped.x.lerp(target, 1 − exp(−COLOR_DAMP_RATE·Δ))，换主题时文字 / 尘埃 / 雾 / 底色缓缓过渡。 */
    private var damped = input.colors.second
    private val field = DioramaParticleFieldState()
    private val motes = DioramaMoteLayer()
    private val text = DioramaTextLayer()
    private val font = DioramaFont(null)
    private val rig = DioramaCameraRig()
    private val view = FloatArray(16)
    private val camWorld = FloatArray(16)
    private val proj = FloatArray(16)
    private var width = 1
    private var height = 1
    private var lastNanos = 0L
    private var elapsed = 0.0

    private val moteCirc = resolveDioramaMoteCircumference(28.0)
    private val moteRadial = resolveDioramaMoteRadial(2.0)
    private val motePositions = FloatArray(DIORAMA_MOTE_MAX_POINTS * 3)
    private val moteWritten = newDioramaMoteWritten()
    private var trebleEnv = 0.0
    private var powerEnv = 0.0

    private var w: DioramaStageWindow? = null

    // ── VisualizerDiorama.tsx 换歌状态机：一首歌一段走廊，新歌在雾外偏移处生成，镜头一镜到底飞过去 ──
    private val seq = DioramaSequencerState()
    private var song: DioramaStageSong? = null
    private var lastLocalIndex = 0
    private var lastActiveKey: String? = null
    private var transitionEpoch = 0
    private var transitionOutgoing: Int? = null
    private var transitionStart = 0.0
    private val lineRasters = HashMap<Int, DioramaLineRaster>()
    private val lineTextures = HashMap<Int, DioramaGlTexture>()
    private var placed: List<DioramaPlacedUnit> = emptyList()
    private var lineWidth = 0.0
    private val unitBase = ArrayList<DioramaGlTexture>()
    private val unitGlow = ArrayList<DioramaGlTexture>()
    private val looks = ArrayList<DioramaUnitLook>()
    private var lightVals = FloatArray(0)
    private var soulVals = FloatArray(0)
    private var activeLineWorldWidth = 0.0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        contrast.init(); glow.init()
        motes.init(DIORAMA_MOTE_MAX_POINTS * 3)
        text.init()
        // 重建 GL 上下文时旧纹理已失效：清掉缓存，下一帧按当前行重建。
        lineRasters.clear(); lineTextures.clear(); unitBase.clear(); unitGlow.clear(); looks.clear(); placed = emptyList()
        moteWritten.fill(Int.MIN_VALUE)
        w = null
    }

    /** 换行 / 转场起止：粒子缓冲换窗口；邻行栅格按全局行号复用、离窗的释放；当前行单元重栅格化、逐单元状态清零。 */
    private fun enter(idx: Int, outgoing: Int?) {
        val prevIdx = w?.globalIndex
        val nw = buildDioramaStageWindow(seq, idx, outgoing, corridor, density)
        w = nw
        contrast.upload(nw.particles); glow.upload(nw.particles)
        val keep = nw.visible.filter { it.index != idx && it.line.fullText.isNotEmpty() }.map { it.index }.toSet()
        (lineTextures.keys - keep).forEach { lineTextures.remove(it)?.release(); lineRasters.remove(it)?.recycle() }
        for (v in nw.visible) {
            if (v.index !in keep || lineRasters.containsKey(v.index)) continue
            val r = rasterDioramaLine(v.line.fullText, font)
            lineRasters[v.index] = r
            lineTextures[v.index] = DioramaGlTexture(r.bitmap)
        }
        if (prevIdx == idx && placed.isNotEmpty()) return
        unitBase.forEach { it.release() }; unitGlow.forEach { it.release() }
        placed.forEach { it.raster.recycle() }
        unitBase.clear(); unitGlow.clear(); looks.clear(); placed = emptyList(); lineWidth = 0.0
        val active = nw.visible.firstOrNull { it.index == idx }
        layoutDioramaActiveUnits(active?.line, nw.units, font)?.let { (p, lw) ->
            placed = p; lineWidth = lw
            p.forEach { unitBase += DioramaGlTexture(it.raster.base); unitGlow += DioramaGlTexture(it.raster.glow); looks += DioramaUnitLook() }
        }
        lightVals = FloatArray(placed.size); soulVals = FloatArray(placed.size)
    }

    /** 每帧：按 Folia 规则判换歌 / 单曲循环，算全局行号、离场行，过期段剪掉。 */
    private fun resolveGlobal(t: Double): Int {
        val cur = input.song
        val outgoingSeg = seq.segments.lastOrNull()
        val outgoingLocal = if (outgoingSeg != null) min(max(lastLocalIndex, 0), outgoingSeg.span - 1) else 0
        val outgoingGlobal = if (outgoingSeg != null) outgoingSeg.globalStart + outgoingLocal else 0
        val isNewSong = outgoingSeg == null || outgoingSeg.seed != cur.seed
        val lyricsChanged = !isNewSong && song !== cur && outgoingSeg!!.lines != cur.lines
        val lineIndex = cur.lines.indexOfLast { it.startTime <= t }
        val isLoopRestart = outgoingSeg != null && !isNewSong && lineIndex in 0..DIORAMA_LOOP_RESTART_MAX_INDEX && lastLocalIndex > lineIndex
        song = cur
        if (isNewSong) {
            var origin = DioramaVec(0.0, 0.0, 0.0)
            if (outgoingSeg != null) {
                val anchor = dioramaGetFrame(outgoingSeg.frames, outgoingLocal).position
                val off = dioramaPickTransitionOffset(cur.seed, transitionEpoch + 1)
                origin = DioramaVec(anchor.x + off.x, anchor.y + off.y, anchor.z + off.z)
            }
            dioramaAppendSegment(seq, cur.seed, cur.lines, 0, origin)
        } else if (lyricsChanged) {
            dioramaUpdateActiveSegmentLines(seq, cur.lines)
        }
        val activeSeg = seq.segments.last()
        val keyChanged = activeSeg.key != lastActiveKey
        val hadPrevious = lastActiveKey != null
        if (keyChanged) { lastActiveKey = activeSeg.key; lastLocalIndex = 0; moteWritten.fill(Int.MIN_VALUE) }
        if (isLoopRestart) lastLocalIndex = 0
        val starting = (keyChanged && hadPrevious) || isLoopRestart
        if (starting) {
            transitionEpoch += 1
            transitionOutgoing = outgoingGlobal
            transitionStart = elapsed
        } else if (transitionOutgoing != null && elapsed - transitionStart >= DIORAMA_TRANSITION_DURATION) {
            transitionOutgoing = null
        }
        if (lineIndex >= 0) lastLocalIndex = lineIndex
        val rawLocal = if (lineIndex >= 0) lineIndex else lastLocalIndex
        val local = min(max(rawLocal, 0), max(activeSeg.span - 1, 0))
        val g = activeSeg.globalStart + local
        val out = transitionOutgoing
        val pruneFrom = if (out != null) min(g, out) else g
        dioramaPruneSegments(seq, pruneFrom - DIORAMA_PRUNE_MARGIN_BEHIND)
        return g
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        this.width = width; this.height = height
        Matrix.perspectiveM(proj, 0, 55f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 1000f)
    }

    private fun dist(a: DioramaVec, b: DioramaVec): Double { val x = a.x - b.x; val y = a.y - b.y; val z = a.z - b.z; return sqrt(x * x + y * y + z * z) }

    override fun onDrawFrame(gl: GL10?) {
        val nowNanos = System.nanoTime()
        val delta = min(0.1, if (lastNanos == 0L) 1.0 / 60 else (nowNanos - lastNanos) / 1e9)
        lastNanos = nowNanos
        elapsed += delta
        val t = timeSeconds()
        val idx = resolveGlobal(t)
        if (w?.globalIndex != idx || w?.outgoingIndex != transitionOutgoing) enter(idx, transitionOutgoing)
        val win = w ?: return
        val aspect = width.toDouble() / height.coerceAtLeast(1)
        val (particleColors, target) = input.colors
        val colorK = 1 - kotlin.math.exp(-DIORAMA_COLOR_DAMP_RATE * delta)
        damped = DioramaDampedColors(
            damped.primary.lerp(target.primary, colorK), damped.accent.lerp(target.accent, colorK),
            damped.secondary.lerp(target.secondary, colorK), damped.bg.lerp(target.bg, colorK),
        )
        // 还没有真实频谱：按 0.5 s 一拍的合成底鼓 / 中频 / 高频（与 DioramaParticlePreview 同一套），让涟漪与辉光可见。
        val beat = (t % 0.5) < 0.06
        val bands = DioramaAudioBands(if (beat) 230.0 else 70.0, 110.0, if ((t % 1.0) < 0.08) 200.0 else 90.0, 60.0)
        val power = 120.0
        trebleEnv = dioramaStepEnvelope(trebleEnv, min(1.0, bands.treble / 255), 14.0, 3.2, delta)
        powerEnv = dioramaStepEnvelope(powerEnv, min(1.0, power / 255), 18.0, 3.5, delta)
        stepDioramaParticleField(
            field,
            DioramaParticleFrameInput(delta, bands, 1.0, t, corridor, idx, transitionOutgoing != null, height.toDouble(), win.waveNumberMax, particleColors),
            listOf(contrastU, glowU),
        )
        val eye: DioramaVec
        if (restCamera) {
            eye = win.restEye; val f = win.frame
            Matrix.setLookAtM(
                view, 0, eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat(),
                (eye.x + f.forward.x).toFloat(), (eye.y + f.forward.y).toFloat(), (eye.z + f.forward.z).toFloat(),
                f.up.x.toFloat(), f.up.y.toFloat(), f.up.z.toFloat(),
            )
        } else {
            // CameraRig.tsx：读头跟字横移、13 种运镜、漂移、收束；相机矩阵取逆即视图矩阵。
            rig.step(t, delta, elapsed, seq, idx, activeLineWorldWidth, stageMotion, transitionEpoch, 55.0, aspect)
            eye = rig.cameraPosition
            dioramaComposeMatrix(eye, rig.cameraQuaternion, 1.0, camWorld)
            Matrix.invertM(view, 0, camWorld, 0)
        }
        GLES30.glClearColor(dioramaLinearToSrgb(damped.bg.r).toFloat(), dioramaLinearToSrgb(damped.bg.g).toFloat(), dioramaLinearToSrgb(damped.bg.b).toFloat(), 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val cam = floatArrayOf(eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat())

        // 背景尘埃：窗口回收 + 轻微漂移；大小随高频、透明度随整体能量、颜色 secondary→accent 30%。
        if (recycleDioramaMoteWindow(moteWritten, motePositions, seq, idx, dioramaTotalGlobalLines(seq), moteCirc, moteRadial, seq.segments.last().seed)) {
            motes.upload(motePositions, DIORAMA_MOTE_WINDOW_LINES * moteCirc * moteRadial * 3)
        }
        val moteModel = FloatArray(16).also {
            Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, (sin(elapsed * 0.17) * 0.12).toFloat(), (sin(elapsed * 0.11 + 1.7) * 0.09).toFloat(), (cos(elapsed * 0.13) * 0.12).toFloat())
        }
        motes.draw(
            DIORAMA_MOTE_WINDOW_LINES * moteCirc * moteRadial, moteModel, view, proj,
            (0.03 * (1 + 0.42 * trebleEnv)).toFloat(), height / 2f,
            damped.secondary.lerp(damped.accent, 0.3), (0.16 + 0.18 * powerEnv).toFloat(), damped.bg,
        )
        glow.draw(glowU, view, proj, cam)
        contrast.draw(contrastU, view, proj, cam)

        // 邻行（renderOrder 0，透明物体按离镜头远→近）。
        val draws = ArrayList<Pair<Double, DioramaTextDraw>>()
        val metrics = lineRasters.mapValues { DioramaRasterMetrics(it.value.advancePx, it.value.fontPx) }
        val nb = updateDioramaNeighborLines(win.visible, idx, transitionOutgoing, metrics, eye, 55.0, aspect, 1.0, damped)
        for (look in nb) {
            val v = win.visible.first { it.index == look.index }
            val r = lineRasters[look.index] ?: continue
            val tex = lineTextures[look.index] ?: continue
            if (look.opacity <= 0) continue
            val wpp = DIORAMA_LINE_FONT_SIZE / r.fontPx
            draws += dist(v.position, eye) to DioramaTextDraw(
                dioramaComposeMatrix(v.position, v.quaternion, look.scale), (r.canvasWidthPx * wpp).toFloat(), (r.canvasHeightPx * wpp).toFloat(),
                tex, look.color, look.opacity.toFloat(), additive = false, depthTest = true,
            )
        }
        val ordered = draws.sortedByDescending { it.first }.map { it.second }.toMutableList()

        // 当前行：组摆在行位、按英雄距离缩放；单元三层（辉光 17 → 灵魂 18 → 字身 19，都不做深度测试）。
        val active = win.visible.firstOrNull { it.index == idx }
        if (active != null && placed.isNotEmpty()) {
            val fit = resolveDioramaFrameFitScale(lineWidth, DIORAMA_HERO_DISTANCE, 55.0, aspect) * active.placement.scale
            activeLineWorldWidth = lineWidth * fit
            val group = dioramaComposeMatrix(active.position, active.quaternion, fit)
            val life = resolveDioramaTextLife(dist(active.position, eye))
            val breath = 0.9 + 0.1 * sin(elapsed * 1.9)
            updateDioramaActiveUnits(win.units, looks, lightVals, soulVals, t, delta, life, breath, powerEnv, damped, emptyMap(), 1.0, 1.0, false, 0.0)
            fun planeAt(x: Double, y: Double, z: Double, sc: Double): FloatArray {
                val local = FloatArray(16); Matrix.setIdentityM(local, 0)
                Matrix.translateM(local, 0, x.toFloat(), y.toFloat(), z.toFloat())
                Matrix.scaleM(local, 0, sc.toFloat(), sc.toFloat(), 1f)
                return FloatArray(16).also { Matrix.multiplyMM(it, 0, group, 0, local, 0) }
            }
            val glows = ArrayList<DioramaTextDraw>(); val souls = ArrayList<DioramaTextDraw>(); val bases = ArrayList<DioramaTextDraw>()
            placed.forEachIndexed { i, p ->
                val l = looks[i]; val pw = p.slot.width.toFloat(); val ph = p.slot.height.toFloat()
                if (l.glowVisible) glows += DioramaTextDraw(planeAt(p.slot.centerX, 0.0, -0.01, 1.0), pw, ph, unitGlow[i], l.glowColor, l.glowOpacity.toFloat(), additive = true, depthTest = false)
                if (l.soulVisible) souls += DioramaTextDraw(planeAt(p.slot.centerX, l.soulY, -0.005, l.soulScale), pw, ph, unitBase[i], l.soulColor, l.soulOpacity.toFloat(), additive = true, depthTest = false)
                bases += DioramaTextDraw(planeAt(p.slot.centerX, 0.0, 0.0, 1.0), pw, ph, unitBase[i], l.baseColor, l.baseOpacity.toFloat(), additive = false, depthTest = false)
            }
            ordered += glows; ordered += souls; ordered += bases
        } else {
            activeLineWorldWidth = 0.0
        }
        text.draw(ordered, view, proj, damped.bg, DIORAMA_FOG_NEAR.toFloat(), DIORAMA_FOG_FAR.toFloat())
    }
}

private fun stageHex(c: Color): String {
    val r = (c.red * 255 + 0.5).toInt(); val g = (c.green * 255 + 0.5).toInt(); val b = (c.blue * 255 + 0.5).toInt()
    return String.format("#%02x%02x%02x", r, g, b)
}

/**
 * 镜台舞台：一首歌一条走廊，画粒子（隧道 / 点云）+ 背景尘埃 + 邻行 + 当前行逐单元（Folia 默认：普通辉光 1、
 * 灵魂出窍 1、当前字漂移关、渐变关）。restCamera = true 时镜头停在当前行静止位（取证），否则走 DioramaCameraRig。
 * 换歌（lines / seed）、换主题时整块 GLSurfaceView 重建。
 */
@Composable
fun DioramaStage(
    lines: List<Line>,
    timeSeconds: () -> Double,
    backgroundColor: Color,
    primaryColor: Color,
    accentColor: Color,
    secondaryColor: Color,
    seed: String = "probe-0",
    corridor: Boolean = false,
    restCamera: Boolean = false,
    density: Double = 768.0,
    modifier: Modifier = Modifier,
) {
    // 换歌不重建 GLSurfaceView：同一个渲染器里走 Folia 的换歌状态机（新走廊在雾外偏移处生成，镜头一镜到底飞过去）。
    val colors = remember(primaryColor, accentColor, secondaryColor, backgroundColor) {
        val p = dioramaColorFromHex(stageHex(primaryColor)); val a = dioramaColorFromHex(stageHex(accentColor))
        val sc = dioramaColorFromHex(stageHex(secondaryColor)); val bg = dioramaColorFromHex(stageHex(backgroundColor))
        resolveDioramaParticleContrastColors(DioramaParticleColors(p, a, sc), bg) to DioramaDampedColors(p, a, sc, bg)
    }
    // 换歌、换主题都不重建 GLSurfaceView：渲染线程每帧读 input（换主题走颜色阻尼）。
    val input = remember { DioramaStageInput(DioramaStageSong(lines, seed), colors) }
    androidx.compose.runtime.SideEffect {
        val cur = input.song
        if (cur.lines !== lines || cur.seed != seed) input.song = DioramaStageSong(lines, seed)
        if (input.colors != colors) input.colors = colors
    }
    androidx.compose.runtime.key(restCamera, corridor, density) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(DioramaStageRenderer(input, corridor, density, restCamera, timeSeconds))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            onRelease = { it.onPause() },
        )
    }
}

/** 取证预览：定格在 timeSeconds，镜头静止位。 */
@Composable
fun DioramaStagePreview(
    lines: List<Line>,
    timeSeconds: Double,
    corridor: Boolean,
    backgroundColor: Color,
    primaryColor: Color,
    accentColor: Color,
    secondaryColor: Color,
    seed: String = "probe-0",
    density: Double = 768.0,
    restCamera: Boolean = true,
    modifier: Modifier = Modifier,
) = DioramaStage(lines, { timeSeconds }, backgroundColor, primaryColor, accentColor, secondaryColor, seed, corridor, restCamera, density, modifier)
