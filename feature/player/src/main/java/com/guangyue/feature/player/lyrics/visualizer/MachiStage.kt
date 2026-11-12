// 町景（Folia machi）舞台起步：OpenGL ES 3 自写最小子集（GLSurfaceView），先接「城寨酒吧」白模。
//   · 镜头：MachiCamera.kt（camera.ts 默认构图 + 运镜，正交斜俯视）。运镜按歌曲时间走（氛围时钟未接，暂停时也停）。
//   · 光照：skyLights.ts 夜里的半球光（hemiSky / hemiGround × 2.1）+ 月光（方向 (-6.5, 14, -5)、× 1.35），
//     与 three 一样按线性空间 Lambert（÷π）算，再过 NeutralToneMapping（Khronos PBR Neutral）转 sRGB；亮窗 / 屏面 / 酒吧后墙自发光 × artificial。
//     贴地薄雾按 environment look 的 mist 向 fog 色混。天空 = palette.skyTop → skyBottom 竖向渐变。
//   · 歌词大屏与门头店招的字不进 GL：正交投影下平面矩形投到屏幕上是平行四边形，用同一帧镜头算出四角，
//     Compose 画布按仿射把字贴上去（牌面行由 director.ts 的 resolveMachiBoardTarget 决定；逐字翻牌、跑马灯、全息未接）。
// 还没做：toon 渐变阶、阴影、bloom / 镜头 pass（MachiLens 已有纯函数）、天气、招牌、道具、另两个场景（便利店 / 车站）。
// 只给取证页用（MachiStagePreview），未进歌词样式列表。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.guangyue.core.lyrics.Line
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

private const val MACHI_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNor;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aEmi;
uniform mat4 uVP;
out vec3 vCol;
out vec3 vNor;
out float vEmi;
out float vY;
void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    vCol = aCol; vNor = aNor; vEmi = aEmi; vY = aPos.y;
}
"""

private const val MACHI_FS = """#version 300 es
precision highp float;
in vec3 vCol;
in vec3 vNor;
in float vEmi;
in float vY;
uniform vec3 uSunDir;
uniform vec3 uSun;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform vec3 uFog;
uniform float uMist;
uniform float uArtificial;
out vec4 fragColor;
vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }
// three NeutralToneMapping（Khronos PBR Neutral）。
vec3 neutral(vec3 color) {
    const float startCompression = 0.8 - 0.04;
    const float desaturation = 0.15;
    float x = min(color.r, min(color.g, color.b));
    float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
    color -= offset;
    float peak = max(color.r, max(color.g, color.b));
    if (peak < startCompression) return color;
    float d = 1.0 - startCompression;
    float newPeak = 1.0 - d * d / (peak + d - startCompression);
    color *= newPeak / peak;
    float g = 1.0 - 1.0 / (desaturation * (peak - newPeak) + 1.0);
    return mix(color, vec3(newPeak), g);
}
void main() {
    vec3 n = normalize(vNor);
    vec3 albedo = toLinear(vCol);
    float w = 0.5 * n.y + 0.5;
    vec3 irradiance = mix(uHemiGround, uHemiSky, w) + uSun * max(dot(n, uSunDir), 0.0);
    vec3 lit = albedo * irradiance / 3.14159265;
    vec3 glow = albedo * uArtificial;
    vec3 c = mix(lit, glow, vEmi);
    float mist = uMist * (1.0 - smoothstep(0.0, 2.6, vY)) * (1.0 - vEmi);
    c = mix(c, toLinear(uFog) * 0.35, clamp(mist, 0.0, 1.0));
    fragColor = vec4(toSrgb(neutral(c)), 1.0);
}
"""

private const val MACHI_SKY_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
out float vT;
void main() { gl_Position = vec4(aPos, 0.999, 1.0); vT = aPos.y * 0.5 + 0.5; }
"""

private const val MACHI_SKY_FS = """#version 300 es
precision mediump float;
in float vT;
uniform vec3 uTop;
uniform vec3 uBottom;
out vec4 fragColor;
void main() { fragColor = vec4(mix(uBottom, uTop, smoothstep(0.0, 1.0, vT)), 1.0); }
"""

/** 夜景的光照参数（skyLights.ts + environment.ts 夜 / 晴）。 */
internal class MachiNightLight(val palette: MachiPalette) {
    val look = resolveMachiEnvironmentLook(createMachiEnvironmentState(resolveMachiEnvironmentTarget(MACHI_DAYLIGHT_NIGHT, MachiWeatherKind.CLEAR)))
    val sunDir: FloatArray = run {
        val l = sqrt(6.5 * 6.5 + 14.0 * 14.0 + 5.0 * 5.0)
        floatArrayOf((-6.5 / l).toFloat(), (14 / l).toFloat(), (-5 / l).toFloat())
    }
    private fun lin(hex: String, k: Double): FloatArray = machiRgb(hex).let { c ->
        FloatArray(3) { i -> (Math.pow(c[i].toDouble(), 2.2) * k).toFloat() }
    }
    val sun = lin(palette.moon, look.sunIntensity)
    val hemiSky = lin(palette.hemiSky, look.hemiIntensity)
    val hemiGround = lin(palette.hemiGround, look.hemiIntensity)
}

private class MachiRenderer(
    private val mesh: FloatArray,
    private val light: MachiNightLight,
    private val rig: MachiCameraRig,
    private val timeSeconds: () -> Double,
) : GLSurfaceView.Renderer {
    private var program = 0
    private var skyProgram = 0
    private var vao = 0
    private var skyVao = 0
    private var count = 0
    private var aspectW = 1.0
    private var aspectH = 1.0

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) android.util.Log.e("MachiStage", "shader: " + GLES30.glGetShaderInfoLog(s))
        return s
    }

    private fun link(vs: String, fs: String): Int {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) android.util.Log.e("MachiStage", "link: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun floatBuffer(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = link(MACHI_VS, MACHI_FS)
        skyProgram = link(MACHI_SKY_VS, MACHI_SKY_FS)
        val ids = IntArray(2)
        GLES30.glGenVertexArrays(2, ids, 0)
        vao = ids[0]; skyVao = ids[1]
        val bufs = IntArray(2)
        GLES30.glGenBuffers(2, bufs, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, floatBuffer(mesh), GLES30.GL_STATIC_DRAW)
        val stride = 10 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 36)
        count = mesh.size / 10
        GLES30.glBindVertexArray(skyVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[1])
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, floatBuffer(quad), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectW = width.toDouble(); aspectH = height.toDouble()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val p = light.palette
        // 天空
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(skyProgram)
        val top = machiRgb(p.skyTop); val bottom = machiRgb(p.skyBottom)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uTop"), 1, top, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uBottom"), 1, bottom, 0)
        GLES30.glBindVertexArray(skyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        // 场景
        val pose = synchronized(rig) { rig.setAspect(aspectW, aspectH); rig.pose(timeSeconds(), 1.0) }
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVP"), 1, false, pose.viewProjection(), 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uSunDir"), 1, light.sunDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uSun"), 1, light.sun, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uHemiSky"), 1, light.hemiSky, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uHemiGround"), 1, light.hemiGround, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uFog"), 1, machiRgb(p.fog), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMist"), light.look.mist.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uArtificial"), light.look.artificial.toFloat())
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        GLES30.glBindVertexArray(0)
    }
}

private fun Color.machiHex(): String = String.format("#%06x", toArgb() and 0xFFFFFF)

/**
 * 町景取证舞台（城寨酒吧白模）。[timeSeconds] 为歌曲时间（秒）；颜色取播放页主题（同 Folia Theme 的四色）。
 */
@Composable
fun MachiStagePreview(
    lines: List<Line>,
    timeSeconds: () -> Double,
    backgroundColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    seed: String = "machi",
    songTitle: String = "",
) {
    val palette = remember(backgroundColor, primaryColor, secondaryColor, accentColor, seed) {
        resolveMachiPalette(MachiTheme(backgroundColor.machiHex(), primaryColor.machiHex(), secondaryColor.machiHex(), accentColor.machiHex()), seed)
    }
    val light = remember(palette) { MachiNightLight(palette) }
    val model = remember(seed) { buildMachiKowloon(seed) }
    val rig = remember { MachiCameraRig(machiKowloonFramingPoints(), MachiKowloonLayout.VIEW) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(MachiRenderer(model.mesh, light, rig, timeSeconds))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onPause() },
        )
        Canvas(Modifier.fillMaxSize()) {
            if (frame < 0L) return@Canvas
            val t = timeSeconds()
            val pose = synchronized(rig) { rig.setAspect(size.width.toDouble(), size.height.toDouble()); rig.pose(t, 1.0) }
            fun toPx(v: MachiVec3): FloatArray {
                val n = pose.project(v)
                return floatArrayOf(((n[0] + 1) / 2 * size.width).toFloat(), ((1 - n[1]) / 2 * size.height).toFloat())
            }
            val target = resolveMachiBoardTarget(lines, t)
            val boardText = if (target.isCard) songTitle else lines.getOrNull(target.index)?.fullText.orEmpty()
            drawIntoQuad(machiKowloonScreenCorners().map(::toPx), MachiKowloonLayout.SCREEN_W, MachiKowloonLayout.SCREEN_H, boardText, machiRgbColor(palette.boardLit), 0.34)
            if (songTitle.isNotBlank()) {
                drawIntoQuad(machiKowloonSignCorners().map(::toPx), MachiKowloonLayout.SIGN_W, MachiKowloonLayout.SIGN_H, songTitle, machiRgbColor(palette.neon), 0.62)
            }
        }
    }
}

private fun machiRgbColor(hex: String): Int = machiRgb(hex).let { c ->
    (0xFF shl 24) or ((c[0] * 255).toInt() shl 16) or ((c[1] * 255).toInt() shl 8) or (c[2] * 255).toInt()
}

private val machiTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
}

/**
 * 把一行字贴到投影后的四边形里（左下、右下、右上、左上，屏幕 px）：局部坐标按米，
 * 字高 = 板高 × [heightShare]，太长就按板宽缩，居中；三点定仿射（正交投影下平面矩形投出来就是平行四边形）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoQuad(
    corners: List<FloatArray>, widthM: Double, heightM: Double, text: String, color: Int, heightShare: Double,
) {
    if (text.isBlank()) return
    val scale = 100f // 局部 1 m = 100 单位，免得字号太小被取整
    val w = (widthM * scale).toFloat(); val h = (heightM * scale).toFloat()
    val src = floatArrayOf(0f, 0f, w, 0f, 0f, h)
    val tl = corners[3]; val tr = corners[2]; val bl = corners[0]
    val dst = floatArrayOf(tl[0], tl[1], tr[0], tr[1], bl[0], bl[1])
    val m = android.graphics.Matrix()
    if (!m.setPolyToPoly(src, 0, dst, 0, 3)) return
    val paint = machiTextPaint
    paint.color = color
    paint.textSize = (h * heightShare).toFloat()
    val width = paint.measureText(text)
    val maxW = w * 0.92f
    if (width > maxW) paint.textSize *= maxW / width
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.concat(m)
    nc.clipRect(0f, 0f, w, h)
    val fm = paint.fontMetrics
    nc.drawText(text, w / 2, h / 2 - (fm.ascent + fm.descent) / 2, paint)
    nc.restore()
}
