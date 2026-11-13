package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// 镜台粒子的 GL 部分：Folia dioramaParticleShaders.ts（自发光软点着色器，点云 / 隧道两模式、对比层 + 加色辉光层共用）
// 与 dioramaParticleMaterials.ts（主题色对背景的自适应对比、两层材质的 uniform 默认值与混合方式）照译。
// 着色器改写成 GLSL ES 3.00：three 自动注入的 position / cameraPosition / viewMatrix / projectionMatrix 改为显式声明，
// varying → in/out，gl_FragColor → fragColor；数学逐行不动。

// ───────────────────────── dioramaParticleMaterials.ts：颜色 ─────────────────────────

/** 线性工作空间的 RGB（对应 THREE.Color，ColorManagement 开启）。 */
internal data class DioramaLinearColor(val r: Double, val g: Double, val b: Double) {
    fun lerp(t: DioramaLinearColor, a: Double) = DioramaLinearColor(r + (t.r - r) * a, g + (t.g - g) * a, b + (t.b - b) * a)
    fun toFloats() = floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat())
}

internal fun dioramaSrgbToLinear(c: Double): Double = if (c < 0.04045) c * 0.0773993808 else (c * 0.9478672986 + 0.0521327014).pow(2.4)
internal fun dioramaLinearToSrgb(c: Double): Double = if (c < 0.0031308) c * 12.92 else 1.055 * c.pow(0.41666) - 0.055

/** new THREE.Color('#rrggbb')：十六进制按 sRGB 解码进线性空间。 */
internal fun dioramaColorFromHex(hex: String): DioramaLinearColor {
    val v = hex.removePrefix("#").let { if (it.length == 3) it.map { ch -> "$ch$ch" }.joinToString("") else it }.toLong(16)
    return DioramaLinearColor(
        dioramaSrgbToLinear(((v shr 16) and 255) / 255.0),
        dioramaSrgbToLinear(((v shr 8) and 255) / 255.0),
        dioramaSrgbToLinear((v and 255) / 255.0),
    )
}

internal data class DioramaParticleColors(val primary: DioramaLinearColor, val accent: DioramaLinearColor, val secondary: DioramaLinearColor)

private fun relativeLuminance(c: DioramaLinearColor) = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b

internal fun dioramaParticleContrastRatio(fg: DioramaLinearColor, bg: DioramaLinearColor): Double {
    val f = relativeLuminance(fg); val b = relativeLuminance(bg)
    return (max(f, b) + 0.05) / (min(f, b) + 0.05)
}

private const val CALM_EMISSION = 0.92 * 0.95
private const val CONTRAST_LAYER_ALPHA = 0.72

/** 静止近点真正落到屏上的像素：着色器的 0.92×0.95 发光、对比层 α0.72、在 sRGB 编码值上混合。 */
private fun asDisplayed(c: DioramaLinearColor, bg: DioramaLinearColor): DioramaLinearColor {
    fun ch(s: Double, d: Double) = dioramaSrgbToLinear(
        dioramaLinearToSrgb(s * CALM_EMISSION) * CONTRAST_LAYER_ALPHA + dioramaLinearToSrgb(d) * (1 - CONTRAST_LAYER_ALPHA),
    )
    return DioramaLinearColor(ch(c.r, bg.r), ch(c.g, bg.g), ch(c.b, bg.b))
}

internal fun dioramaParticleDisplayedContrastRatio(c: DioramaLinearColor, bg: DioramaLinearColor): Double =
    dioramaParticleContrastRatio(asDisplayed(c, bg), bg)

private val LIGHT_TARGET = dioramaColorFromHex("#ffffff")
private val DARK_TARGET = dioramaColorFromHex("#050505")

/** 已够清楚就不动；否则朝背景对比更大的白 / 黑方向挪最小的量（10 次二分）。 */
private fun adaptColorToBackground(c: DioramaLinearColor, bg: DioramaLinearColor, minimum: Double): DioramaLinearColor {
    if (dioramaParticleDisplayedContrastRatio(c, bg) >= minimum) return c
    val target = if (dioramaParticleDisplayedContrastRatio(LIGHT_TARGET, bg) >= dioramaParticleDisplayedContrastRatio(DARK_TARGET, bg)) LIGHT_TARGET else DARK_TARGET
    var low = 0.0; var high = 1.0
    repeat(10) {
        val amount = (low + high) * 0.5
        if (dioramaParticleDisplayedContrastRatio(c.lerp(target, amount), bg) >= minimum) high = amount else low = amount
    }
    return c.lerp(target, high)
}

internal fun resolveDioramaParticleContrastColors(colors: DioramaParticleColors, bg: DioramaLinearColor) = DioramaParticleColors(
    adaptColorToBackground(colors.primary, bg, 4.6),
    adaptColorToBackground(colors.accent, bg, 4.1),
    adaptColorToBackground(colors.secondary, bg, 4.6),
)

// ───────────────────────── 材质 uniform ─────────────────────────

/** buildUniforms 的默认值；每帧由场把时间、模式、涟漪源等写进来。glowPass：0 = 对比层，1 = 辉光层。 */
internal class DioramaParticleUniforms(colors: DioramaParticleColors, val glowPass: Float, var glow: Float) {
    var time = 0f
    var corridor = 0f
    var amplitude = 0.34f
    var maxSwell = 0.29f
    var waveNumberMax = 8f
    var detail = 0f
    val rippleSource = FloatArray(DIORAMA_RIPPLE_COUNT * 4)
    val rippleShape = FloatArray(DIORAMA_RIPPLE_COUNT * 4)
    var offsetGain = 0f
    var flow = 1f
    var formation = 1f
    var scatter = 4f
    var sizeBase = 0.052f
    var sizeGain = 0.3f
    var pulse = 1f
    var spectralCentroid = 0.5f
    var viewportHeight = 1f
    var primary = colors.primary
    var accent = colors.accent
    var secondary = colors.secondary

    /** lerpDioramaParticleMaterialColors。 */
    fun lerpColors(colors: DioramaParticleColors, amount: Double) {
        primary = primary.lerp(colors.primary, amount)
        accent = accent.lerp(colors.accent, amount)
        secondary = secondary.lerp(colors.secondary, amount)
    }
}

// ───────────────────────── dioramaParticleShaders.ts ─────────────────────────

private fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

internal val DIORAMA_PARTICLE_VERTEX_SHADER = """#version 300 es
precision highp float;
#define RIPPLE_COUNT $DIORAMA_RIPPLE_COUNT

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aAnchor;
layout(location = 3) in vec3 aScale;
layout(location = 4) in float aPhase;
layout(location = 5) in vec3 aStyle;
layout(location = 6) in vec2 aWave;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform vec3 cameraPosition;

uniform float uTime;
uniform float uCorridor;
uniform float uAmplitude;
uniform float uMaxSwell;
uniform float uWaveNumberMax;
uniform float uDetail;
uniform vec4 uRippleSource[RIPPLE_COUNT];
uniform vec4 uRippleShape[RIPPLE_COUNT];
uniform float uOffsetGain;
uniform float uFlow;
uniform float uFormation;
uniform float uScatter;
uniform float uPulse;
uniform float uSpectralCentroid;
uniform float uViewportHeight;
uniform float uSizeBase;
uniform float uSizeGain;
uniform float uGlow;
uniform float uGlowPass;
uniform vec3 uPrimaryColor;
uniform vec3 uAccentColor;
uniform vec3 uSecondaryColor;

out vec3 vColor;
out float vAlpha;
out float vReaction;

float hermite(float value) { return value * value * (3.0 - 2.0 * value); }
float hash11(float n) { return fract(sin(n) * 43758.5453123); }

float resolveLife(float distanceToCamera) {
    float farT = clamp((${f1(DIORAMA_SHAPE_FADE_IN_END)} - distanceToCamera) / ${f1(DIORAMA_SHAPE_FADE_IN_END - DIORAMA_SHAPE_FADE_IN_START)}, 0.0, 1.0);
    float nearT = clamp((distanceToCamera - ${f1(DIORAMA_SHAPE_DISSOLVE_END)}) / ${f1(DIORAMA_SHAPE_DISSOLVE_START - DIORAMA_SHAPE_DISSOLVE_END)}, 0.0, 1.0);
    return hermite(farT) * hermite(nearT);
}

float ripplePacket(float r, float age, vec4 shape) {
    float dr = r - age * shape.y;
    float k = min(shape.w, uWaveNumberMax);
    float packet = exp(-(dr * dr) / (shape.z * shape.z)) * sin(dr * k);
    return shape.x * packet * exp(-age * 1.15) * exp(-r * 0.35);
}

float cloudRipples(vec3 unitPos, float phase) {
    float spin = cos(phase);
    float spun = sin(phase);
    float total = 0.0;
    for (int i = 0; i < RIPPLE_COUNT; i += 1) {
        vec4 source = uRippleSource[i];
        vec4 shape = uRippleShape[i];
        float age = uTime - source.w;
        if (shape.x <= 0.0 || age < 0.0) continue;
        vec3 origin = vec3(source.x * spin - source.z * spun, source.y, source.x * spun + source.z * spin);
        total += ripplePacket(distance(unitPos, origin), age, shape);
    }
    return total;
}

vec2 corridorSurfaceDelta(vec2 w, vec2 origin) {
    float dAngle = w.y - origin.y;
    return vec2(w.x - origin.x, atan(sin(dAngle), cos(dAngle)));
}

float corridorRipples(vec2 w) {
    float total = 0.0;
    for (int i = 0; i < RIPPLE_COUNT; i += 1) {
        vec4 source = uRippleSource[i];
        vec4 shape = uRippleShape[i];
        float age = uTime - source.w;
        if (shape.x <= 0.0 || age < 0.0) continue;
        total += ripplePacket(length(corridorSurfaceDelta(w, source.xy)), age, shape);
    }
    return total;
}

float cloudIdle(vec3 unitPos) {
    return 0.11 * sin(unitPos.x * 1.7 + uTime * 0.5) * cos(unitPos.z * 1.4 - uTime * 0.37)
         + 0.06 * sin(unitPos.y * 2.1 - uTime * 0.66);
}

float corridorIdle(vec2 w) {
    float t = uTime * uFlow;
    float region = 0.6 + 0.4 * sin(w.x * 0.42 - t * 0.33);
    float ripple = 0.62 * sin(w.x * 1.25 - t * 1.15)
                 + 0.30 * sin(w.x * 0.8 + w.y * 2.0 - t * 0.8)
                 + 0.08 * sin(w.y * 3.0 + t * 0.5);
    return region * ripple;
}

float cloudDetail(vec3 p) {
    float k = uWaveNumberMax * 0.7;
    return sin(p.x * k + uTime * 3.1) * sin(p.y * k - uTime * 2.6) * sin(p.z * k + uTime * 2.2);
}

float corridorDetail(vec2 w) {
    float k = uWaveNumberMax * 0.7;
    float n = max(1.0, floor(k));
    return sin(w.x * k + uTime * 3.1) * sin(w.y * n - uTime * 2.4);
}

void main() {
    float colorSlot = aStyle.y;
    float isFar = aStyle.z;
    vec3 normalDir = normalize(aNormal);
    float localRadius = aScale.z;

    vec3 basePos = position;
    basePos.y *= aScale.y;

    vec3 unitPos = position / localRadius;
    bool corridor = uCorridor > 0.5;
    float audio = corridor ? corridorRipples(aWave) : cloudRipples(unitPos, aPhase);
    float idle = corridor ? corridorIdle(aWave) : cloudIdle(unitPos);

    float detail = uDetail * smoothstep(0.06, 0.45, abs(audio));
    audio += (corridor ? corridorDetail(aWave) : cloudDetail(unitPos)) * detail * 0.3;

    float swell = clamp(audio + idle, -1.6, 1.6) * uAmplitude;
    vec3 displaced = basePos + normalDir * (swell * localRadius);
    float d = clamp(abs(clamp(audio, -1.6, 1.6) * uAmplitude) / max(uMaxSwell, 0.0001), 0.0, 1.0);

    displaced.z += sin(uTime * 0.6 + aPhase) * 0.12 * uOffsetGain * localRadius;

    float scatter = 1.0 - uFormation;
    if (scatter > 0.001) {
        float r1 = hash11(aPhase * 12.9898 + aWave.x * 78.233);
        float r2 = hash11(aPhase * 39.346 + aWave.y * 11.135 + 3.7);
        float r3 = hash11(r1 * 91.7 + r2 * 47.3);
        vec3 spread = normalize(normalDir * 0.75 + vec3(r1 - 0.5, r2 - 0.5, r3 - 0.5) * 1.6);
        displaced += spread * (scatter * scatter * uScatter * (0.55 + r3 * 0.9));
    }

    displaced *= aScale.x * uPulse;
    vec3 worldPosition = aAnchor + displaced;

    float life = resolveLife(distance(worldPosition, cameraPosition));
    vec4 mvPosition = viewMatrix * vec4(worldPosition, 1.0);

    float glowScale = mix(1.0, 1.9 + uGlow * 1.3, uGlowPass);
    float sizeWorld = (uSizeBase + (pow(d, 3.0) + detail * 0.35) * uSizeGain) * glowScale;
    float projected = sizeWorld * uViewportHeight * projectionMatrix[1][1] * 0.5 / max(-mvPosition.z, 0.1);
    gl_PointSize = clamp(projected, mix(1.4, 2.0, uGlowPass), mix(26.0, 64.0, uGlowPass));
    gl_Position = projectionMatrix * mvPosition;

    float gradientPhase = (
        dot(basePos, vec3(0.2, 0.16, 0.13))
        + aPhase * 0.05
        + colorSlot * 0.5
        + uTime * 0.02
        + uSpectralCentroid * 0.3
    ) * 6.283;
    vec3 wgt = max(vec3(0.0), 0.5 + 0.5 * cos(gradientPhase - vec3(0.0, 2.094, 4.188)));
    wgt *= wgt; wgt /= max(wgt.x + wgt.y + wgt.z, 0.001);
    vec3 baseColor = uPrimaryColor * wgt.x + uAccentColor * wgt.y + uSecondaryColor * wgt.z;
    vec3 hotColor = mix(uAccentColor, uSecondaryColor, smoothstep(0.2, 0.9, uSpectralCentroid));
    float hot = min(1.0, smoothstep(0.1, 0.75, d) * 0.75 + detail * 0.3);
    vColor = mix(baseColor, hotColor, hot) * (0.92 + d * 0.5);

    float layerBase = mix(0.72, 0.5, isFar);
    float formed = smoothstep(0.0, 0.45, uFormation);
    vAlpha = formed * (uGlowPass > 0.5
        ? life * smoothstep(0.1, 0.8, d) * 1.15
        : life * min(1.0, layerBase + d * 0.28));
    vReaction = d;
}
"""

internal const val DIORAMA_PARTICLE_FRAGMENT_SHADER = """#version 300 es
precision highp float;
uniform float uGlow;
uniform float uGlowPass;

in vec3 vColor;
in float vAlpha;
in float vReaction;
out vec4 fragColor;

vec3 dioramaLinearToSRGB(vec3 linear) {
    vec3 safe = max(linear, vec3(0.0));
    return mix(
        pow(safe, vec3(0.41666)) * 1.055 - vec3(0.055),
        safe * 12.92,
        vec3(lessThanEqual(safe, vec3(0.0031308)))
    );
}

void main() {
    vec2 point = gl_PointCoord - vec2(0.5);
    float radius = length(point);

    if (uGlowPass > 0.5) {
        float halo = pow(clamp(1.0 - radius * 2.0, 0.0, 1.0), 1.7);
        float alpha = min(0.5, halo * vAlpha * uGlow * 0.4);
        if (alpha < 0.003) discard;
        fragColor = vec4(dioramaLinearToSRGB(vColor * (1.0 + uGlow * 0.25)), alpha);
        return;
    }

    float core = 1.0 - smoothstep(0.14, 0.48, radius);
    float alpha = core * vAlpha;
    if (alpha < 0.01) discard;
    float emission = 0.95 + vReaction * 0.22;
    fragColor = vec4(dioramaLinearToSRGB(vColor * emission), alpha);
}
"""

// ───────────────────────── GL 层（单次 draw call 画一整套点） ─────────────────────────

/**
 * 一层点：对比层（普通混合 SRC_ALPHA / ONE_MINUS_SRC_ALPHA）或辉光层（加色 SRC_ALPHA / ONE），都不写深度。
 * 必须在 GL 线程调用；upload 换缓冲（换歌 / 换窗口 / 改密度），draw 每帧。
 */
internal class DioramaParticleLayer {
    private var program = 0
    private var vao = 0
    private val vbos = IntArray(7)
    private var count = 0

    fun init() {
        program = dioramaLinkProgram(DIORAMA_PARTICLE_VERTEX_SHADER, DIORAMA_PARTICLE_FRAGMENT_SHADER)
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(7, vbos, 0)
    }

    fun upload(data: DioramaParticleGeometryData) {
        if (vao == 0) return
        count = data.pointCount
        GLES30.glBindVertexArray(vao)
        val arrays = listOf(data.positions to 3, data.normals to 3, data.anchors to 3, data.scales to 3, data.phases to 1, data.styles to 3, data.waves to 2)
        arrays.forEachIndexed { loc, (arr, size) ->
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[loc])
            val fb = ByteBuffer.allocateDirect(max(4, arr.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(arr); position(0) }
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, arr.size * 4, fb, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, size * 4, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    /** view / projection 为列主序 4×4（与 three 一致），camera 为世界坐标。 */
    fun draw(u: DioramaParticleUniforms, view: FloatArray, projection: FloatArray, camera: FloatArray) {
        if (program == 0 || count == 0) return
        GLES30.glUseProgram(program)
        fun loc(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(loc("viewMatrix"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(loc("projectionMatrix"), 1, false, projection, 0)
        GLES30.glUniform3fv(loc("cameraPosition"), 1, camera, 0)
        GLES30.glUniform1f(loc("uTime"), u.time)
        GLES30.glUniform1f(loc("uCorridor"), u.corridor)
        GLES30.glUniform1f(loc("uAmplitude"), u.amplitude)
        GLES30.glUniform1f(loc("uMaxSwell"), u.maxSwell)
        GLES30.glUniform1f(loc("uWaveNumberMax"), u.waveNumberMax)
        GLES30.glUniform1f(loc("uDetail"), u.detail)
        GLES30.glUniform4fv(loc("uRippleSource"), DIORAMA_RIPPLE_COUNT, u.rippleSource, 0)
        GLES30.glUniform4fv(loc("uRippleShape"), DIORAMA_RIPPLE_COUNT, u.rippleShape, 0)
        GLES30.glUniform1f(loc("uOffsetGain"), u.offsetGain)
        GLES30.glUniform1f(loc("uFlow"), u.flow)
        GLES30.glUniform1f(loc("uFormation"), u.formation)
        GLES30.glUniform1f(loc("uScatter"), u.scatter)
        GLES30.glUniform1f(loc("uPulse"), u.pulse)
        GLES30.glUniform1f(loc("uSpectralCentroid"), u.spectralCentroid)
        GLES30.glUniform1f(loc("uViewportHeight"), u.viewportHeight)
        GLES30.glUniform1f(loc("uSizeBase"), u.sizeBase)
        GLES30.glUniform1f(loc("uSizeGain"), u.sizeGain)
        GLES30.glUniform1f(loc("uGlow"), u.glow)
        GLES30.glUniform1f(loc("uGlowPass"), u.glowPass)
        GLES30.glUniform3fv(loc("uPrimaryColor"), 1, u.primary.toFloats(), 0)
        GLES30.glUniform3fv(loc("uAccentColor"), 1, u.accent.toFloats(), 0)
        GLES30.glUniform3fv(loc("uSecondaryColor"), 1, u.secondary.toFloats(), 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        if (u.glowPass > 0.5f) GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        else GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() {
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); GLES30.glDeleteBuffers(7, vbos, 0) }
        if (program != 0) GLES30.glDeleteProgram(program)
        vao = 0; program = 0; count = 0
    }
}

private fun dioramaCompile(type: Int, src: String): Int {
    val s = GLES30.glCreateShader(type)
    GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
    val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
    if (ok[0] == 0) {
        android.util.Log.e("DioramaGl", "shader: " + GLES30.glGetShaderInfoLog(s))
        GLES30.glDeleteShader(s); return 0
    }
    return s
}

internal fun dioramaLinkProgram(vs: String, fs: String): Int {
    val v = dioramaCompile(GLES30.GL_VERTEX_SHADER, vs); val f = dioramaCompile(GLES30.GL_FRAGMENT_SHADER, fs)
    if (v == 0 || f == 0) return 0
    val p = GLES30.glCreateProgram()
    GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f); GLES30.glLinkProgram(p)
    val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
    GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
    if (ok[0] == 0) { android.util.Log.e("DioramaGl", "link: " + GLES30.glGetProgramInfoLog(p)); GLES30.glDeleteProgram(p); return 0 }
    return p
}
