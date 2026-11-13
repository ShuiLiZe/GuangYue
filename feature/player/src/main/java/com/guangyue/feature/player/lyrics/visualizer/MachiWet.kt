// 町景湿路面倒影与光柱（照译 Folia machi engine/fx/reflection.ts、ground.ts、lightCones.ts）。
//   · 倒影：three 用镜像正交镜头只画 REFLECT 图层（亮的东西 + 楼身/招牌当遮挡）到 0.3 倍分辨率、带 mipmap 的半浮点贴图。
//     这里等价做法：同一个 VP 右乘「按 y = planeY 镜像」矩阵，把整片场景再画一遍进 [MachiReflection]，
//     不发光的面写黑（当遮挡），贴地的面丢弃；湿路面按屏幕坐标取样（镜像 VP 与主 VP 投影一致，地面上一点的倒影就在它自己的屏幕位置）。
//   · 湿路面着色（kPuddle / kRippleLayer / kReflect / kGlint / 水洼内外混合）逐行照译进 MachiStage 的片元着色器（[MACHI_WET_PARS] / [MACHI_WET_OUTPUT]）。
//     暂无：积雪覆盖（uMachiSnow 恒 0）、鼓点涟漪（智能动感未接）、three 点光源（glint 的「灯下才闪」用月光 + 半球光的亮度近似）。
//   · 光柱：路灯一束开口圆锥，加色、不写深度，facing² × axial^1.6 × 雾噪声 × gain × level（level = 0.55 × lamp）。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** kowloonScene.ts reflectionPlaneY = LEVEL.ground。 */
internal const val MACHI_KOWLOON_GROUND_Y = 0.02

/** ground.ts REFLECT_STRENGTH。 */
internal const val MACHI_REFLECT_STRENGTH = 0.75

/** 水洼位置与多少（kowloonScene.ts：offset = random()*80-40 ×2，amount = 0.5 + random()*0.25）。 */
internal data class MachiPuddles(val offsetX: Double, val offsetZ: Double, val amount: Double)

internal fun resolveMachiPuddles(seed: String): MachiPuddles {
    val r = MachiRng(machiHashSeed("kowloon:$seed"))
    val x = r.next() * 80 - 40
    val z = r.next() * 80 - 40
    return MachiPuddles(x, z, 0.5 + r.next() * 0.25)
}

/** createMachiRuntime.ts：uRain = rain > 0.02 ? min(1, 0.45 + 0.5 rain) : 0。 */
internal fun resolveMachiGroundRain(rain: Double): Double = if (rain > 0.02) kotlin.math.min(1.0, 0.45 + 0.5 * rain) else 0.0

/** 按 y = planeY 镜像的 VP（列主序）：vp × R，R 把 y 换成 2·planeY − y。 */
internal fun machiMirrorVP(vp: FloatArray, planeY: Double): FloatArray {
    val r = FloatArray(16)
    android.opengl.Matrix.setIdentityM(r, 0)
    r[5] = -1f
    r[13] = (2 * planeY).toFloat()
    val out = FloatArray(16)
    android.opengl.Matrix.multiplyMM(out, 0, vp, 0, r, 0)
    return out
}

/** 纯函数版（单测用）：镜像矩阵作用在一点上。 */
internal fun machiMirrorPoint(x: Double, y: Double, z: Double, planeY: Double) = doubleArrayOf(x, 2 * planeY - y, z)

internal val MACHI_WET_PARS = """
uniform float uWetTime;
uniform sampler2D uReflection;
uniform vec2 uReflectSize;
uniform vec2 uPuddleOffset;
uniform float uPuddleAmount;
uniform float uRain;
uniform float uWet;
uniform vec3 uWetSky;
uniform float uReflectStrength;
uniform float uPuddleBias;
uniform float uGroundY;
float kHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float kNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(kHash(i), kHash(i + vec2(1.0, 0.0)), u.x), mix(kHash(i + vec2(0.0, 1.0)), kHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float kPuddle(vec2 p) {
    float n = kNoise(p * 0.62 + uPuddleOffset) * 0.62 + kNoise(p * 1.7 - uPuddleOffset * 1.3) * 0.38;
    float threshold = mix(0.78, 0.46, uPuddleAmount) + uPuddleBias;
    return smoothstep(threshold, threshold + 0.05, n);
}
vec3 kRippleLayer(vec2 p, float scale, float t, float salt) {
    vec2 q = p * scale;
    vec2 cell = floor(q);
    vec2 center = cell + 0.22 + 0.56 * vec2(kHash(cell + salt), kHash(cell + salt + 7.13));
    float period = 0.75 + 0.9 * kHash(cell + salt + 3.31);
    float phase = fract(t / period + kHash(cell + salt + 11.7));
    float alive = step(kHash(cell + salt + floor(t / period + kHash(cell + salt + 11.7)) * 0.37), uRain);
    vec2 d = q - center;
    float dist = length(d);
    float radius = phase * 0.46;
    float band = 1.0 - smoothstep(0.0, 0.07, abs(dist - radius));
    float fade = (1.0 - phase) * alive;
    float wave = sin((dist - radius) * 48.0) * band * fade;
    return vec3(normalize(d + 1e-4) * wave, band * fade);
}
vec3 kReflect(vec2 uv, float rough) {
    float bias = mix(0.6, 1.8, rough);
    float reach = mix(10.0, 26.0, rough) / uReflectSize.y;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = -4; i <= 4; i++) {
        float t = float(i) / 4.0;
        float w = 1.0 - abs(t) * 0.75;
        sum += textureLod(uReflection, uv + vec2(0.0, t * reach), bias).rgb * w;
        total += w;
    }
    return sum / (total * 0.6);
}
float kGlint(vec2 p, float t) {
    vec2 q = p * 14.0;
    vec2 cell = floor(q);
    float h = kHash(cell + 3.7);
    float period = 0.5 + h * 1.1;
    float beat = t / period + h * 7.0;
    float drop = floor(beat);
    vec2 center = cell + 0.2 + 0.6 * vec2(kHash(cell + drop * 0.71), kHash(cell + drop * 1.37 + 9.1));
    float d = length(q - center);
    float aa = fwidth(d) + 1e-4;
    return (1.0 - smoothstep(0.16 - aa, 0.16 + aa, d)) * (1.0 - fract(beat)) * step(0.45, h);
}
"""

/** 片元里：lit 是这一点的卡通着色结果（outgoingLight），direct 是直射光亮度，screenUv 是屏幕坐标 0..1。 */
internal val MACHI_WET_OUTPUT = """
    {
        vec2 kp = vWorld.xz;
        float puddle = kPuddle(kp) * smoothstep(0.15, 0.85, uWet);
        vec3 ripple = kRippleLayer(kp, 2.3, uWetTime, 0.0) + kRippleLayer(kp, 3.9, uWetTime * 1.13, 19.0) * 0.7;
        vec2 wobble = vec2(kNoise(kp * 2.7 + 3.1), kNoise(kp * 2.7 - 11.4)) - 0.5;
        vec2 ruv = screenUv + ripple.xy * mix(0.004, 0.018, puddle) + wobble * mix(0.012, 0.005, puddle);
        vec3 reflection = kReflect(ruv, 1.0 - puddle);
        float glint = kGlint(kp, uWetTime) * uRain * smoothstep(0.03, 0.4, direct);
        vec3 wet = lit * mix(1.0, 0.62, uWet) + reflection * uReflectStrength * 0.7 * uWet;
        vec3 pool = lit * 0.36 + uWetSky * 0.16 + reflection * uReflectStrength * 1.35 + ripple.z * uWetSky * 0.6;
        lit = mix(wet, pool, puddle) + glint * (vec3(direct) * 3.0 + reflection);
    }
"""

/** 倒影贴图：0.3 倍分辨率（quality.ts 默认画质的 reflectionScale）、RGBA16F、带 mipmap。 */
internal class MachiReflection(private val scale: Double = 0.3) {
    var fbo = 0; private set
    var tex = 0; private set
    private var depth = 0
    var width = 1; private set
    var height = 1; private set

    fun resize(w: Int, h: Int) {
        release()
        width = max(1, (w * scale).roundToInt()); height = max(1, (h * scale).roundToInt())
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0); tex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        val levels = 1 + (kotlin.math.ln(max(width, height).toDouble()) / kotlin.math.ln(2.0)).toInt()
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, GLES30.GL_RGBA16F, width, height)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val r = IntArray(1); GLES30.glGenRenderbuffers(1, r, 0); depth = r[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height)
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0); fbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depth)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e("MachiStage", "reflection fbo incomplete")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun begin() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun end() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun release() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (tex != 0) GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
        if (depth != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depth), 0)
        fbo = 0; tex = 0; depth = 0
    }
}

/** 一束光柱（lightCones.ts MachiLightCone）。 */
internal class MachiLightConeSpec(val apex: DoubleArray, val target: DoubleArray, val radius: Double, val gain: Double)

/** kowloonScene.ts：路灯头往下偏 (−0.25, ground, +0.1)，半径 1.3，gain 1。lampHead = street.ts head − 0.04 y。 */
internal fun machiKowloonLightCones(): List<MachiLightConeSpec> {
    val hx = MachiKowloonStreet.POLE_X - 0.05
    val hy = MachiKowloonStreet.POLE_LAMP_Y + 0.12 - 0.04
    val hz = MachiKowloonStreet.POLE_Z + MachiKowloonStreet.POLE_ARM
    return listOf(MachiLightConeSpec(doubleArrayOf(hx, hy, hz), doubleArrayOf(hx - 0.25, MACHI_KOWLOON_GROUND_Y, hz + 0.1), 1.3, 1.0))
}

/**
 * 开口圆锥（three ConeGeometry(radius, height, 20, 1, openEnded)）：尖在 apex、轴指向 target。
 * 每个顶点 pos3 + normal3 + uvY + gain = 8 个 float；uv.y 在灯头为 1、底面为 0。
 */
internal fun machiConeMesh(specs: List<MachiLightConeSpec>, segments: Int = 20): FloatArray {
    val out = ArrayList<Float>()
    for (s in specs) {
        val dx = s.target[0] - s.apex[0]; val dy = s.target[1] - s.apex[1]; val dz = s.target[2] - s.apex[2]
        val h = sqrt(dx * dx + dy * dy + dz * dz)
        val ax = doubleArrayOf(dx / h, dy / h, dz / h)
        // 与轴垂直的一组基
        val ref = if (kotlin.math.abs(ax[1]) < 0.9) doubleArrayOf(0.0, 1.0, 0.0) else doubleArrayOf(1.0, 0.0, 0.0)
        var ux = ax[1] * ref[2] - ax[2] * ref[1]; var uy = ax[2] * ref[0] - ax[0] * ref[2]; var uz = ax[0] * ref[1] - ax[1] * ref[0]
        val ul = sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul
        val vx = ax[1] * uz - ax[2] * uy; val vy = ax[2] * ux - ax[0] * uz; val vz = ax[0] * uy - ax[1] * ux
        val slope = s.radius / h
        fun rim(i: Int): DoubleArray {
            val a = 2 * PI * i / segments
            val c = cos(a); val sn = sin(a)
            val rx = ux * c + vx * sn; val ry = uy * c + vy * sn; val rz = uz * c + vz * sn
            // 侧面法线：径向 − 轴向 × slope（归一）
            var nx = rx - ax[0] * slope; var ny = ry - ax[1] * slope; var nz = rz - ax[2] * slope
            val nl = sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl
            return doubleArrayOf(s.target[0] + rx * s.radius, s.target[1] + ry * s.radius, s.target[2] + rz * s.radius, nx, ny, nz)
        }
        fun put(p: DoubleArray, uvY: Double) {
            out += p[0].toFloat(); out += p[1].toFloat(); out += p[2].toFloat()
            out += p[3].toFloat(); out += p[4].toFloat(); out += p[5].toFloat()
            out += uvY.toFloat(); out += s.gain.toFloat()
        }
        for (i in 0 until segments) {
            val a = rim(i); val b = rim(i + 1)
            // 灯头处的法线取两条棱的平均再归一（three 的尖顶点每段各一个，法线同理）。
            val mid = DoubleArray(6) { k -> if (k < 3) s.apex[k] else (a[k] + b[k]) / 2 }
            val ml = sqrt(mid[3] * mid[3] + mid[4] * mid[4] + mid[5] * mid[5])
            for (k in 3..5) mid[k] /= ml
            put(mid, 1.0); put(a, 0.0); put(b, 0.0)
        }
    }
    return out.toFloatArray()
}

private const val CONE_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNor;
layout(location = 2) in float aUvY;
layout(location = 3) in float aGain;
uniform mat4 uVP;
uniform vec3 uViewDir;
out float vUvY;
out vec3 vNor;
out vec3 vWorld;
out float vGain;
void main() {
    vUvY = aUvY; vGain = aGain; vWorld = aPos;
    vNor = aNor;
    gl_Position = uVP * vec4(aPos, 1.0);
}
"""

private const val CONE_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
uniform float uTime;
uniform float uLevel;
in float vUvY;
in vec3 vNor;
uniform vec3 uViewDir;
in vec3 vWorld;
in float vGain;
out vec4 fragColor;
float cHash(vec2 p) { return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }
float cNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(cHash(i), cHash(i + vec2(1.0, 0.0)), u.x), mix(cHash(i + vec2(0.0, 1.0)), cHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    // 正交投影：视线方向处处一样（世界空间里比，等价于 three 在视空间与 (0,0,1) 比）。
    float facing = abs(dot(normalize(vNor), uViewDir));
    float axial = pow(clamp(vUvY, 0.0, 1.0), 1.6);
    float haze = 0.7 + 0.6 * cNoise(vec2(vWorld.x * 1.3 + vWorld.z, vWorld.y * 2.0 - uTime * 0.6));
    float alpha = facing * facing * axial * haze * vGain * uLevel;
    fragColor = vec4(uColor * alpha, 1.0);
}
"""

/** 光柱的一个 draw call（加色、不写深度、双面）。 */
internal class MachiLightCones(private val mesh: FloatArray) {
    private var program = 0
    private var vao = 0
    private var count = 0

    fun init(link: (String, String) -> Int) {
        program = link(CONE_VS, CONE_FS)
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        val buf = java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.size * 4, buf, GLES30.GL_STATIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 28)
        GLES30.glBindVertexArray(0)
        count = mesh.size / 8
    }

    /** viewDir：世界空间里从目标指向镜头的单位向量。level = 0.55 × lamp。 */
    fun draw(vp: FloatArray, viewDir: FloatArray, color: FloatArray, time: Double, lamp: Double) {
        if (count == 0) return
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVP"), 1, false, vp, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uViewDir"), 1, viewDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program, "uColor"), 1, color, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTime"), time.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLevel"), (0.55 * lamp).toFloat())
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }
}
