// 镜台舞台（取证版）：把粒子层（DioramaParticleLayer 两层）、背景尘埃（DioramaScene.tsx <points> + PointsMaterial）、
// 邻行整行平面、当前行逐单元三层平面（DioramaActiveUnitPlanes.tsx）放进同一个 GLSurfaceView，镜头停在当前行静止位。
// 每帧写入走 DioramaSceneFrame.kt（recycleDioramaMoteWindow / updateDioramaNeighborLines / updateDioramaActiveUnits），
// 雾 = Fog(bg, 12, 30)。运镜（DioramaCameraRig）与换歌转场下一步接；取证页 dioramastage / dioramastageclouds。
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

/** 舞台静态部分（主线程建好交给 GL 线程）。 */
private class DioramaStageSetup(
    val sequencer: DioramaSequencerState,
    val total: Int,
    val globalIndex: Int,
    val seed: String,
    val visible: List<DioramaVisibleLine>,
    val units: List<DioramaLyricUnit>,
    val particles: DioramaParticleGeometryData,
    val waveNumberMax: Double,
    val corridor: Boolean,
    val eye: DioramaVec,
    val frame: DioramaFrame,
)

private class DioramaStageRenderer(
    private val s: DioramaStageSetup,
    private val particleColors: DioramaParticleColors,
    private val damped: DioramaDampedColors,
    private val background: Color,
    private val timeSeconds: () -> Double,
) : GLSurfaceView.Renderer {
    private val contrast = DioramaParticleLayer()
    private val glow = DioramaParticleLayer()
    private val contrastU = DioramaParticleUniforms(particleColors, 0f, 0f)
    private val glowU = DioramaParticleUniforms(particleColors, 1f, 0.6f)
    private val field = DioramaParticleFieldState()
    private val motes = DioramaMoteLayer()
    private val text = DioramaTextLayer()
    private val font = DioramaFont(null)
    private val view = FloatArray(16)
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

    private val lineRasters = HashMap<Int, DioramaLineRaster>()
    private val lineTextures = HashMap<Int, DioramaGlTexture>()
    private var placed: List<DioramaPlacedUnit> = emptyList()
    private var lineWidth = 0.0
    private val unitBase = ArrayList<DioramaGlTexture>()
    private val unitGlow = ArrayList<DioramaGlTexture>()
    private val looks = ArrayList<DioramaUnitLook>()
    private var lightVals = FloatArray(0)
    private var soulVals = FloatArray(0)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        contrast.init(); glow.init()
        contrast.upload(s.particles); glow.upload(s.particles)
        motes.init(DIORAMA_MOTE_MAX_POINTS * 3)
        text.init()
        for (v in s.visible) {
            if (v.index == s.globalIndex || v.line.fullText.isEmpty()) continue
            val r = rasterDioramaLine(v.line.fullText, font)
            lineRasters[v.index] = r
            lineTextures[v.index] = DioramaGlTexture(r.bitmap)
        }
        val active = s.visible.firstOrNull { it.index == s.globalIndex }
        layoutDioramaActiveUnits(active?.line, s.units, font)?.let { (p, w) ->
            placed = p; lineWidth = w
            p.forEach { unitBase += DioramaGlTexture(it.raster.base); unitGlow += DioramaGlTexture(it.raster.glow); looks += DioramaUnitLook() }
            lightVals = FloatArray(p.size); soulVals = FloatArray(p.size)
        }
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
        val aspect = width.toDouble() / height.coerceAtLeast(1)
        // 取证页没有真实音频：与 DioramaParticlePreview 同一套合成频段（0.5 s 一拍）。
        val beat = (t % 0.5) < 0.06
        val bands = DioramaAudioBands(if (beat) 230.0 else 70.0, 110.0, if ((t % 1.0) < 0.08) 200.0 else 90.0, 60.0)
        val power = 120.0
        trebleEnv = dioramaStepEnvelope(trebleEnv, min(1.0, bands.treble / 255), 14.0, 3.2, delta)
        powerEnv = dioramaStepEnvelope(powerEnv, min(1.0, power / 255), 18.0, 3.5, delta)
        stepDioramaParticleField(
            field,
            DioramaParticleFrameInput(delta, bands, 1.0, t, s.corridor, s.globalIndex, false, height.toDouble(), s.waveNumberMax, particleColors),
            listOf(contrastU, glowU),
        )
        val eye = s.eye; val f = s.frame
        Matrix.setLookAtM(
            view, 0, eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat(),
            (eye.x + f.forward.x).toFloat(), (eye.y + f.forward.y).toFloat(), (eye.z + f.forward.z).toFloat(),
            f.up.x.toFloat(), f.up.y.toFloat(), f.up.z.toFloat(),
        )
        GLES30.glClearColor(background.red, background.green, background.blue, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val cam = floatArrayOf(eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat())

        // 背景尘埃：窗口回收 + 轻微漂移；大小随高频、透明度随整体能量、颜色 secondary→accent 30%。
        if (recycleDioramaMoteWindow(moteWritten, motePositions, s.sequencer, s.globalIndex, s.total, moteCirc, moteRadial, s.seed)) {
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
        val nb = updateDioramaNeighborLines(s.visible, s.globalIndex, null, metrics, eye, 55.0, aspect, 1.0, damped)
        for (look in nb) {
            val v = s.visible.first { it.index == look.index }
            val r = lineRasters[look.index] ?: continue
            if (look.opacity <= 0) continue
            val wpp = DIORAMA_LINE_FONT_SIZE / r.fontPx
            draws += dist(v.position, eye) to DioramaTextDraw(
                dioramaComposeMatrix(v.position, v.quaternion, look.scale), (r.canvasWidthPx * wpp).toFloat(), (r.canvasHeightPx * wpp).toFloat(),
                lineTextures.getValue(look.index), look.color, look.opacity.toFloat(), additive = false, depthTest = true,
            )
        }
        val ordered = draws.sortedByDescending { it.first }.map { it.second }.toMutableList()

        // 当前行：组摆在行位、按英雄距离缩放；单元三层（辉光 17 → 灵魂 18 → 字身 19，都不做深度测试）。
        val active = s.visible.firstOrNull { it.index == s.globalIndex }
        if (active != null && placed.isNotEmpty()) {
            val fit = resolveDioramaFrameFitScale(lineWidth, DIORAMA_HERO_DISTANCE, 55.0, aspect) * active.placement.scale
            val group = dioramaComposeMatrix(active.position, active.quaternion, fit)
            val life = resolveDioramaTextLife(dist(active.position, eye))
            val breath = 0.9 + 0.1 * sin(elapsed * 1.9)
            updateDioramaActiveUnits(s.units, looks, lightVals, soulVals, t, delta, life, breath, powerEnv, damped, emptyMap(), 1.0, 1.0, false, 0.0)
            fun planeAt(x: Double, y: Double, z: Double, sc: Double): FloatArray {
                val local = FloatArray(16); Matrix.setIdentityM(local, 0)
                Matrix.translateM(local, 0, x.toFloat(), y.toFloat(), z.toFloat())
                Matrix.scaleM(local, 0, sc.toFloat(), sc.toFloat(), 1f)
                return FloatArray(16).also { Matrix.multiplyMM(it, 0, group, 0, local, 0) }
            }
            val glows = ArrayList<DioramaTextDraw>(); val souls = ArrayList<DioramaTextDraw>(); val bases = ArrayList<DioramaTextDraw>()
            placed.forEachIndexed { i, p ->
                val l = looks[i]; val w = p.slot.width.toFloat(); val h = p.slot.height.toFloat()
                if (l.glowVisible) glows += DioramaTextDraw(planeAt(p.slot.centerX, 0.0, -0.01, 1.0), w, h, unitGlow[i], l.glowColor, l.glowOpacity.toFloat(), additive = true, depthTest = false)
                if (l.soulVisible) souls += DioramaTextDraw(planeAt(p.slot.centerX, l.soulY, -0.005, l.soulScale), w, h, unitBase[i], l.soulColor, l.soulOpacity.toFloat(), additive = true, depthTest = false)
                bases += DioramaTextDraw(planeAt(p.slot.centerX, 0.0, 0.0, 1.0), w, h, unitBase[i], l.baseColor, l.baseOpacity.toFloat(), additive = false, depthTest = false)
            }
            ordered += glows; ordered += souls; ordered += bases
        }
        text.draw(ordered, view, proj, damped.bg, DIORAMA_FOG_NEAR.toFloat(), DIORAMA_FOG_FAR.toFloat())
    }
}

private fun stageHex(c: Color): String {
    val r = (c.red * 255 + 0.5).toInt(); val g = (c.green * 255 + 0.5).toInt(); val b = (c.blue * 255 + 0.5).toInt()
    return String.format("#%02x%02x%02x", r, g, b)
}

/**
 * 镜台舞台取证预览：探针歌词一条走廊，镜头停在 timeSeconds 所在行的静止位（后退 HERO_DISTANCE、抬 CAMERA_LIFT），
 * 画粒子（隧道 / 点云）+ 背景尘埃 + 邻行 + 当前行逐单元（默认：普通辉光 1、灵魂出窍 1、当前字漂移关、渐变关）。
 */
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
    modifier: Modifier = Modifier,
) {
    val setup = remember(lines, corridor, seed, density, timeSeconds) {
        val sq = DioramaSequencerState()
        dioramaAppendSegment(sq, seed, lines, 0, DioramaVec(0.0, 0.0, 0.0))
        val total = dioramaTotalGlobalLines(sq)
        val idx = lines.indexOfLast { it.startTime <= timeSeconds }.coerceIn(0, (total - 1).coerceAtLeast(0))
        val vis = DioramaGeometryVisibility(mode = if (corridor) DioramaGeometryMode.CORRIDOR else DioramaGeometryMode.CLOUDS)
        val mounted = resolveDioramaMountedIndices(idx, null, total)
        val motion = resolveDioramaMotionParams()
        val spans = buildDioramaCorridorSpans(sq, idx, null, vis)
        val clusters = buildDioramaParticleClusters(sq, mounted, motion, vis, 1.0)
        val (data, k) = buildDioramaParticleBuffer(corridor, clusters, spans, density)
        val frame = dioramaResolveGlobal(sq, idx)!!.frame
        val eye = DioramaVec(
            frame.position.x - frame.forward.x * DIORAMA_HERO_DISTANCE + frame.up.x * DIORAMA_CAMERA_LIFT,
            frame.position.y - frame.forward.y * DIORAMA_HERO_DISTANCE + frame.up.y * DIORAMA_CAMERA_LIFT,
            frame.position.z - frame.forward.z * DIORAMA_HERO_DISTANCE + frame.up.z * DIORAMA_CAMERA_LIFT,
        )
        val visible = buildDioramaVisibleLines(sq, mounted, idx, null, motion.weaveScale)
        val activeLine = lines.getOrNull(idx)
        val units = if (activeLine == null) emptyList() else splitDioramaLyricUnits(activeLine, GraphemeTimings.buildDenseLineGraphemeTimeline(activeLine))
        android.util.Log.i("DioramaStage", "line=$idx points=${data.pointCount} visible=${visible.size} units=${units.size} corridor=$corridor")
        DioramaStageSetup(sq, total, idx, seed, visible, units, data, k, corridor, eye, frame)
    }
    val colors = remember(primaryColor, accentColor, secondaryColor, backgroundColor) {
        val p = dioramaColorFromHex(stageHex(primaryColor)); val a = dioramaColorFromHex(stageHex(accentColor))
        val sc = dioramaColorFromHex(stageHex(secondaryColor)); val bg = dioramaColorFromHex(stageHex(backgroundColor))
        resolveDioramaParticleContrastColors(DioramaParticleColors(p, a, sc), bg) to DioramaDampedColors(p, a, sc, bg)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setRenderer(DioramaStageRenderer(setup, colors.first, colors.second, backgroundColor) { timeSeconds })
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
    )
}
