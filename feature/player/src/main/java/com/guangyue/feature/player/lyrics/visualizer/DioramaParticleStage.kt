package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.guangyue.core.lyrics.Line
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// 镜台粒子：dioramaSceneLayout.ts 的 buildDioramaCorridorSpans / buildDioramaParticleClusters 照译，
// 加一个只画粒子层的 GL 预览（取证页 dioramaprobe / dioramaprobeclouds 用；正式舞台接文字与运镜后再进歌词样式列表）。

/** 隧道模式：当前行窗口（后 6 前 7）的隧道段，换歌时外加离场那条的窗口。 */
internal fun buildDioramaCorridorSpans(
    sequencer: DioramaSequencerState,
    globalIndex: Int,
    transitionOutgoingIndex: Int?,
    visibility: DioramaGeometryVisibility,
): List<DioramaCorridorSpan> {
    if (!visibility.enabled || visibility.mode != DioramaGeometryMode.CORRIDOR) return emptyList()
    val live = buildDioramaCorridorWindow(sequencer, globalIndex, DIORAMA_CORRIDOR_LINES_BEHIND, DIORAMA_CORRIDOR_LINES_AHEAD)
    if (transitionOutgoingIndex == null) return live
    return buildDioramaCorridorWindow(sequencer, transitionOutgoingIndex, DIORAMA_CORRIDOR_LINES_BEHIND, DIORAMA_CORRIDOR_LINES_AHEAD) + live
}

/** 点云模式：挂载窗口（再往前多 2 行防撞余量）每行的阵列锚点，筛可见后只留挂载行的。 */
internal fun buildDioramaParticleClusters(
    sequencer: DioramaSequencerState,
    mountedIndices: List<Int>,
    motion: DioramaMotionParams,
    visibility: DioramaGeometryVisibility,
    particleScale: Double,
): List<DioramaClusterAnchor> {
    if (visibility.mode != DioramaGeometryMode.CLOUDS) return emptyList()
    val mounted = mountedIndices.toSet()
    val clusterIndices = sortedSetOf<Int>()
    for (i in mountedIndices) for (back in 0..DIORAMA_CLUSTER_COLLISION_LINE_SPAN) if (i - back >= 0) clusterIndices += i - back
    val result = ArrayList<DioramaClusterAnchor>()
    for (i in clusterIndices) {
        val resolved = dioramaResolveGlobal(sequencer, i) ?: continue
        val seg = resolved.segment
        val placement = getDioramaTextPlacement(resolved.localIndex, seg.seed, motion.weaveScale)
        val shot = getDioramaShot(resolved.localIndex, seg.lines, seg.seed, motion.subMode)
        buildDioramaFormation(resolved.localIndex, seg.seed, shot, resolved.frame, placement, particleScale).forEachIndexed { slot, piece ->
            result += DioramaClusterAnchor("$i-$slot", i, "${seg.seed}:${resolved.localIndex}:$slot:${piece.kind.id}", piece)
        }
    }
    return selectVisibleDioramaClusters(result, visibility).filter { it.sourceLine in mounted }
}

private fun hexOf(c: Color): String {
    val r = (c.red * 255 + 0.5).toInt(); val g = (c.green * 255 + 0.5).toInt(); val b = (c.blue * 255 + 0.5).toInt()
    return String.format("#%02x%02x%02x", r, g, b)
}

private class DioramaParticleRenderer(
    private val data: DioramaParticleGeometryData,
    private val waveNumberMax: Double,
    private val corridor: Boolean,
    private val readHeadLine: Int,
    private val eye: DioramaVec,
    private val forward: DioramaVec,
    private val up: DioramaVec,
    private val colors: DioramaParticleColors,
    private val background: Color,
    private val glowIntensity: Float,
    private val timeSeconds: () -> Double,
) : GLSurfaceView.Renderer {
    private val contrast = DioramaParticleLayer()
    private val glow = DioramaParticleLayer()
    private val contrastU = DioramaParticleUniforms(colors, 0f, 0f)
    private val glowU = DioramaParticleUniforms(colors, 1f, glowIntensity)
    private val state = DioramaParticleFieldState()
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private var height = 1
    private var lastNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        contrast.init(); glow.init()
        contrast.upload(data); glow.upload(data)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        this.height = height
        Matrix.perspectiveM(proj, 0, 55f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 1000f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val delta = if (lastNanos == 0L) 1.0 / 60 else (now - lastNanos) / 1e9
        lastNanos = now
        val t = timeSeconds()
        // 取证页没有真实音频：按 0.5 s 一拍的合成底鼓 / 中频 / 高频，让涟漪与辉光可见（静态时钟时只剩怠速呼吸）。
        val beat = (t % 0.5) < 0.06
        val bands = DioramaAudioBands(if (beat) 230.0 else 70.0, 110.0, if ((t % 1.0) < 0.08) 200.0 else 90.0, 60.0)
        stepDioramaParticleField(
            state,
            DioramaParticleFrameInput(delta, bands, 1.0, t, corridor, readHeadLine, false, height.toDouble(), waveNumberMax, colors),
            listOf(contrastU, glowU),
        )
        Matrix.setLookAtM(
            view, 0, eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat(),
            (eye.x + forward.x).toFloat(), (eye.y + forward.y).toFloat(), (eye.z + forward.z).toFloat(),
            up.x.toFloat(), up.y.toFloat(), up.z.toFloat(),
        )
        GLES30.glClearColor(background.red, background.green, background.blue, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val cam = floatArrayOf(eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat())
        if (glowIntensity > 0f) glow.draw(glowU, view, proj, cam)
        contrast.draw(contrastU, view, proj, cam)
    }
}

/**
 * 取证预览：探针歌词建一条走廊，镜头停在 timeSeconds 所在行的静止位（行帧后退 HERO_DISTANCE、抬 CAMERA_LIFT，沿 forward 看），
 * 只画粒子两层。corridor = true 画隧道，false 画点云阵列。
 */
@Composable
fun DioramaParticlePreview(
    lines: List<Line>,
    timeSeconds: Double,
    corridor: Boolean,
    backgroundColor: Color,
    primaryColor: Color,
    accentColor: Color,
    secondaryColor: Color,
    seed: String = "probe-0",
    density: Double = 768.0,
    glowIntensity: Float = 0.6f,
    modifier: Modifier = Modifier,
) {
    val setup = remember(lines, corridor, seed, density) {
        val sq = DioramaSequencerState()
        dioramaAppendSegment(sq, seed, lines, 0, DioramaVec(0.0, 0.0, 0.0))
        val total = dioramaTotalGlobalLines(sq)
        val idx = lines.indexOfLast { it.startTime <= timeSeconds }.coerceIn(0, (total - 1).coerceAtLeast(0))
        val vis = DioramaGeometryVisibility(mode = if (corridor) DioramaGeometryMode.CORRIDOR else DioramaGeometryMode.CLOUDS)
        val spans = buildDioramaCorridorSpans(sq, idx, null, vis)
        val clusters = buildDioramaParticleClusters(sq, resolveDioramaMountedIndices(idx, null, total), resolveDioramaMotionParams(), vis, 1.0)
        val (data, k) = buildDioramaParticleBuffer(corridor, clusters, spans, density)
        val frame = dioramaResolveGlobal(sq, idx)!!.frame
        val eye = DioramaVec(
            frame.position.x - frame.forward.x * DIORAMA_HERO_DISTANCE + frame.up.x * DIORAMA_CAMERA_LIFT,
            frame.position.y - frame.forward.y * DIORAMA_HERO_DISTANCE + frame.up.y * DIORAMA_CAMERA_LIFT,
            frame.position.z - frame.forward.z * DIORAMA_HERO_DISTANCE + frame.up.z * DIORAMA_CAMERA_LIFT,
        )
        android.util.Log.i("DioramaProbe", "line=$idx points=${data.pointCount} k=$k corridor=$corridor spans=${spans.size} clusters=${clusters.size}")
        arrayOf(data, k, idx, eye, frame)
    }
    val colors = remember(primaryColor, accentColor, secondaryColor, backgroundColor) {
        resolveDioramaParticleContrastColors(
            DioramaParticleColors(dioramaColorFromHex(hexOf(primaryColor)), dioramaColorFromHex(hexOf(accentColor)), dioramaColorFromHex(hexOf(secondaryColor))),
            dioramaColorFromHex(hexOf(backgroundColor)),
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                val frame = setup[4] as DioramaFrame
                setRenderer(
                    DioramaParticleRenderer(
                        setup[0] as DioramaParticleGeometryData, setup[1] as Double, corridor, setup[2] as Int,
                        setup[3] as DioramaVec, frame.forward, frame.up, colors, backgroundColor, glowIntensity, { timeSeconds },
                    ),
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        // 2026-11-14：与 DioramaStage 一致，离开组合时停 GL 线程（之前缺这一步，点云取证连拍几张后模拟器卡死）。
        onRelease = { it.onPause() },
    )
}
