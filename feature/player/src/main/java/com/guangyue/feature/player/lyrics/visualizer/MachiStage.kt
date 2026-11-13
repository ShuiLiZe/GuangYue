// 町景（Folia machi）舞台起步：OpenGL ES 3 自写最小子集（GLSurfaceView），先接「城寨酒吧」白模。
//   · 镜头：MachiCamera.kt（camera.ts 默认构图 + 运镜，正交斜俯视）。运镜按歌曲时间走（氛围时钟未接，暂停时也停）。
//   · 光照：skyLights.ts 夜里的半球光（hemiSky / hemiGround × 2.1）+ 月光（方向 (-6.5, 14, -5)、× 1.35），
//     与 three 一样按线性空间 Lambert（÷π）算；亮窗 / 屏面 / 酒吧后墙自发光 × artificial。天空 = palette.skyTop → skyBottom 竖向渐变。
//   · 歌词大屏（逐字点亮 + 换行信号切换）、屏下 LED 跑马灯、门头霓虹店招在 GL 里画（MachiScreen.kt，对译 lyricScreen.ts / ledStrip.ts）。
//   · toon：直射月光走 4 阶渐变（materials.ts createToonGradient）；场景画进线性 HDR 目标，后处理（MachiPost.kt）
//     做墨线 / 贴地薄雾 / 云 / bloom，最后色调映射上屏（同 three：渲染到目标时材质不做色调映射，OutputPass 做）。
//   · 湿路面倒影与光柱：MachiWet.kt（reflection.ts / ground.ts / lightCones.ts）；雨天只接了湿度与水洼，雨丝 / 滴水（weather.ts）未做。
// 还没做：阴影、天气粒子、另两个场景（便利店 / 车站）。
// 只给取证页用（MachiStagePreview），未进歌词样式列表。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.guangyue.core.lyrics.Line
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min
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
out vec3 vWorld;
void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    vCol = aCol; vNor = aNor; vEmi = aEmi; vY = aPos.y; vWorld = aPos;
}
"""

private val MACHI_FS = """#version 300 es
precision highp float;
in vec3 vCol;
in vec3 vNor;
in float vEmi;
in float vY;
in vec3 vWorld;
uniform int uReflectPass;
uniform int uWetOn;
uniform vec2 uSceneSize;
uniform vec3 uSunDir;
uniform vec3 uSun;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform float uArtificial;
out vec4 fragColor;
$MACHI_WET_PARS
vec3 toLinear(vec3 c) { return mix(c * 0.0773993808, pow(c * 0.9478672986 + 0.0521327014, vec3(2.4)), step(0.04045, c)); }
// MeshToonMaterial：直射光走 4 阶渐变图（86/146/206/255，最近邻），半球光照常；线性输出（色调映射在后处理最后一道）。
float toonStep(float dotNL) {
    float coord = clamp(dotNL * 0.5 + 0.5, 0.0, 0.9999);
    int i = int(floor(coord * 4.0));
    return i == 0 ? 86.0 / 255.0 : (i == 1 ? 146.0 / 255.0 : (i == 2 ? 206.0 / 255.0 : 1.0));
}
void main() {
    vec3 n = normalize(vNor);
    vec3 albedo = toLinear(vCol);
    // 倒影 pass（MachiWet.kt）：只有亮的东西映得出，其余写黑当遮挡；贴地的面（含路面本身）丢掉。
    if (uReflectPass == 1) {
        if (vY < uGroundY + 0.01) discard;
        fragColor = vec4(albedo * uArtificial * vEmi, 1.0);
        return;
    }
    float w = 0.5 * n.y + 0.5;
    vec3 directLight = uSun * toonStep(dot(n, uSunDir));
    vec3 irradiance = mix(uHemiGround, uHemiSky, w) + directLight;
    vec3 lit = albedo * irradiance / 3.14159265;
    // 湿路面（ground.ts OUTPUT）：只在贴地朝上的路面上。
    if (uWetOn == 1 && n.y > 0.9 && abs(vY - uGroundY) < 0.012 && vEmi < 0.01) {
        float direct = dot(albedo * directLight / 3.14159265, vec3(0.299, 0.587, 0.114));
        vec2 screenUv = gl_FragCoord.xy / uSceneSize;
        $MACHI_WET_OUTPUT
    }
    // 自发光（materials.ts glow：MeshBasicMaterial，颜色 × spec.level × setGlowLevel）：aEmi 就是 level，不再与受光色混合。
    fragColor = vec4(vEmi > 0.001 ? albedo * uArtificial * vEmi : lit, 1.0);
}
"""

private const val MACHI_SKY_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
out float vT;
void main() { gl_Position = vec4(aPos, 0.999, 1.0); vT = aPos.y * 0.5 + 0.5; }
"""

private const val MACHI_SKY_FS = """#version 300 es
precision highp float;
in float vT;
uniform vec3 uTop;
uniform vec3 uBottom;
out vec4 fragColor;
// sky.ts：sRGB 的竖向线性渐变（canvas 渐变在 sRGB 里插值），贴图按 sRGB 解码成线性。
vec3 toLinear(vec3 c) { return mix(c * 0.0773993808, pow(c * 0.9478672986 + 0.0521327014, vec3(2.4)), step(0.04045, c)); }
void main() { fragColor = vec4(toLinear(mix(uBottom, uTop, vT)), 1.0); }
"""

/** 夜景的光照参数（skyLights.ts + environment.ts 夜 / 晴）。 */
internal class MachiNightLight(val palette: MachiPalette, val weather: MachiWeatherKind = MachiWeatherKind.CLEAR) {
    val state = createMachiEnvironmentState(resolveMachiEnvironmentTarget(MACHI_DAYLIGHT_NIGHT, weather))
    val look = resolveMachiEnvironmentLook(state)
    val sunDir: FloatArray = run {
        val l = sqrt(6.5 * 6.5 + 14.0 * 14.0 + 5.0 * 5.0)
        floatArrayOf((-6.5 / l).toFloat(), (14 / l).toFloat(), (-5 / l).toFloat())
    }
    private fun lin(hex: String, k: Double): FloatArray = machiLinear(hex, k)
    val sun = lin(palette.moon, look.sunIntensity)
    val hemiSky = lin(palette.hemiSky, look.hemiIntensity)
    val hemiGround = lin(palette.hemiGround, look.hemiIntensity)
}

private class MachiRenderer(
    private val mesh: FloatArray,
    private val light: MachiNightLight,
    private val rig: MachiCameraRig,
    private val timeSeconds: () -> Double,
    private val lines: List<Line>,
    private val signs: MachiKowloonSigns,
    private val seed: String,
    private val puddles: MachiPuddles,
) : GLSurfaceView.Renderer {
    private val reflection = MachiReflection()
    private val cones = MachiLightCones(machiConeMesh(machiKowloonLightCones()))
    private val lampColor = machiLinear("#e2f2ff")
    private var lastNanos = 0L
    private var ambient = 0.0
    private val post = MachiPostChain()
    private val invVP = FloatArray(16)
    private val glowColorA = machiLinear("#ff4fb0")
    private val glowColorB = machiLinear(mcMixHex("#ff9fd0", light.palette.interiorLight, 0.45))
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
        signs.init(::link)
        cones.init(::link)
        post.init(::link)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectW = width.toDouble(); aspectH = height.toDouble()
        post.resize(width, height)
        reflection.resize(width, height)
    }

    private fun drawMesh(vp: FloatArray, reflectPass: Boolean, wet: Boolean) {
        GLES30.glUseProgram(program)
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glUniform3fv(u("uSunDir"), 1, light.sunDir, 0)
        GLES30.glUniform3fv(u("uSun"), 1, light.sun, 0)
        GLES30.glUniform3fv(u("uHemiSky"), 1, light.hemiSky, 0)
        GLES30.glUniform3fv(u("uHemiGround"), 1, light.hemiGround, 0)
        GLES30.glUniform1f(u("uArtificial"), light.look.artificial.toFloat())
        GLES30.glUniform1i(u("uReflectPass"), if (reflectPass) 1 else 0)
        GLES30.glUniform1i(u("uWetOn"), if (wet) 1 else 0)
        GLES30.glUniform1f(u("uGroundY"), MACHI_KOWLOON_GROUND_Y.toFloat())
        if (wet) {
            // ground.ts 的一组 uniform；路面是 concrete（puddleBias 0.08，kowloonScene surfaces.lot）。
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflection.tex)
            GLES30.glUniform1i(u("uReflection"), 1)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glUniform2f(u("uReflectSize"), reflection.width.toFloat(), reflection.height.toFloat())
            GLES30.glUniform2f(u("uSceneSize"), post.sceneW.toFloat(), post.sceneH.toFloat())
            GLES30.glUniform2f(u("uPuddleOffset"), puddles.offsetX.toFloat(), puddles.offsetZ.toFloat())
            GLES30.glUniform1f(u("uPuddleAmount"), puddles.amount.toFloat())
            GLES30.glUniform1f(u("uRain"), resolveMachiGroundRain(light.state.rain).toFloat())
            GLES30.glUniform1f(u("uWet"), light.state.wetness.toFloat())
            GLES30.glUniform3fv(u("uWetSky"), 1, machiLinear(light.palette.skyBottom), 0)
            GLES30.glUniform1f(u("uReflectStrength"), MACHI_REFLECT_STRENGTH.toFloat())
            GLES30.glUniform1f(u("uPuddleBias"), 0.08f)
            GLES30.glUniform1f(u("uWetTime"), ambient.toFloat())
        }
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        GLES30.glBindVertexArray(0)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 运镜按氛围时间（camera.ts motionTime：用上一帧累加的 ambient，帧间隔封顶 0.1 s）；幅度 4 s 内从 0 升满（DRIFT_RAMP_SECONDS）。
        val pose = synchronized(rig) { rig.setAspect(aspectW, aspectH); rig.pose(ambient, kotlin.math.min(1.0, ambient / 4.0)) }
        val vp = pose.viewProjection()
        val now = System.nanoTime()
        val delta = if (lastNanos == 0L) 1.0 / 60 else min(0.1, (now - lastNanos) / 1e9)
        lastNanos = now
        val t = timeSeconds()
        val target = resolveMachiBoardTarget(lines, t)
        signs.show(target, lines)
        val view = (pose.position - pose.target).normalized()
        val wet = light.state.wetness > 0.001
        // 倒影（reflection.ts）：干的时候路面不取倒影，不画。
        if (wet) {
            val mirror = machiMirrorVP(vp, MACHI_KOWLOON_GROUND_Y)
            reflection.begin()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            drawMesh(mirror, reflectPass = true, wet = false)
            signs.draw(
                mirror, view.z.toFloat(),
                MachiSignFrame(t, ambient + delta, delta, resolveMachiChorus(lines, target), 0.0, light.look.artificial, resolveMachiFlicker(ambient + delta, seed)),
                advance = false,
            )
            reflection.end()
        }
        post.beginScene()
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
        drawMesh(vp, reflectPass = false, wet = wet)
        // 光柱（lightCones.ts）：level = 0.55 × lamp × (1 − 0.6 daylight)，lamp = streetLamps × (0.75 + 0.25 flicker)。
        val lamp = light.look.streetLamps * (0.75 + 0.25 * resolveMachiFlicker(ambient + 4.2, "$seed:lamp", 0.45))
        cones.draw(vp, floatArrayOf(view.x.toFloat(), view.y.toFloat(), view.z.toFloat()), lampColor, ambient, lamp * (1 - 0.6 * light.state.daylight))
        // 歌词大屏 / 跑马灯 / 门头霓虹（MachiScreen.kt）
        ambient += delta
        signs.draw(
            vp, view.z.toFloat(),
            MachiSignFrame(t, ambient, delta, resolveMachiChorus(lines, target), 0.0, light.look.artificial, resolveMachiFlicker(ambient, seed)),
        )
        // 后处理（MachiPost.kt）：墨线 / 薄雾 / 云 → bloom → 色调映射。夜里：mist 色 = skyBottom × 1.35，
        // 云色 = lerp(skyBottom, moon, 0.25) × 0.7；雾里的两处灯（kowloonScene glowA / glowB）。
        android.opengl.Matrix.invertM(invVP, 0, vp, 0)
        val look = light.look
        val skyBottom = machiLinear(p.skyBottom)
        val moon = machiLinear(p.moon)
        val neon = 1.0 * (0.85 + 0.15 * resolveMachiFlicker(ambient, seed))
        post.finish(
            pixelWorldSize = (pose.top - pose.bottom) / pose.zoom / post.sceneH,
            centerDistance = kotlin.math.sqrt((pose.position - pose.target).dot(pose.position - pose.target)),
            near = 0.1, depthRange = 200.0 - 0.1, invVP = invVP,
            atmosphere = MachiAtmosphereFrame(
                time = ambient,
                mistColor = FloatArray(3) { skyBottom[it] * 1.35f },
                mistAmount = look.mist,
                cloudColor = FloatArray(3) { ((skyBottom[it] + (moon[it] - skyBottom[it]) * 0.25f) * 0.7f) },
                cloudAmount = look.clouds,
                haze = look.haze,
                lightning = 0.0,
                glowA = floatArrayOf(-2.0f, 0f, 1.0f, 3.4f),
                glowColorA = FloatArray(3) { (glowColorA[it] * 0.42 * neon).toFloat() },
                glowB = floatArrayOf(MachiKowloonLayout.SCREEN_X.toFloat(), 0f, (-1.5 + 0.8).toFloat(), 3.0f),
                glowColorB = FloatArray(3) { (glowColorB[it] * 0.36 * look.artificial).toFloat() },
                stars = look.stars,
            ),
            bloomStrength = look.bloom,
        )
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
    artist: String = "",
    weather: String = "clear",
) {
    val palette = remember(backgroundColor, primaryColor, secondaryColor, accentColor, seed) {
        resolveMachiPalette(MachiTheme(backgroundColor.machiHex(), primaryColor.machiHex(), secondaryColor.machiHex(), accentColor.machiHex()), seed)
    }
    val light = remember(palette, weather) { MachiNightLight(palette, MachiWeatherKind.entries.firstOrNull { it.id == weather } ?: MachiWeatherKind.CLEAR) }
    val puddles = remember(seed) { resolveMachiPuddles(seed) }
    val model = remember(seed) { buildMachiKowloon(seed) }
    val rig = remember { MachiCameraRig(machiKowloonFramingPoints(), MachiKowloonLayout.VIEW) }
    val signs = remember(palette, songTitle, artist, seed, model) { MachiKowloonSigns(palette, songTitle, artist, seed, model.signs) }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(MachiRenderer(model.mesh, light, rig, timeSeconds, lines, signs, seed, puddles))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onPause() },
        )
    }
}

