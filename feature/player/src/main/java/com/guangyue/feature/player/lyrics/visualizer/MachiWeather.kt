// 町景天气：雨丝、落地水花、屋檐滴水（照译 Folia machi engine/fx/weather.ts）。
//   · 雨丝：GL_LINES 两千条，顶点着色器按氛围时钟往下推、绕回 RAIN_TOP；落在屋顶上的停在屋顶（kGround 最多 4 块）。
//     灯里的雨丝被照亮（3 处 glint，半径 × glint 程度）。加色、不写深度。
//   · 水花：实例化的扁圆环，每个实例按周期换位置（uRate 1.6）。
//   · 滴水：冷气机前沿、大排档帆布篷前沿、酒吧门头（kowloonScene.ts drips），一段下落一段溅开，跟着路面湿度。
//   · 雪（Points）未做；雨量 = rain × (0.62 + 0.22 audio + 0.12 chorus) × intensity，这里 audio 0、intensity 1。
//   · Math.random 的种子在这里换成固定种子（每次画面一样）。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

private const val RAIN_TOP = 9.5
private const val MAX_ROOFS = 4

/** 一处滴水（weather.ts MachiDrip）。 */
internal class MachiDripSpec(val x: Double, val z: Double, val from: Double, val to: Double, val period: Double, val phase: Double)

internal class MachiRoof(val x0: Double, val z0: Double, val x1: Double, val z1: Double, val y: Double)

internal class MachiWeatherLayout(
    val half: Double,
    val groundY: Double,
    val roofs: List<MachiRoof>,
    val drips: List<MachiDripSpec>,
    /** x, y, z, 半径。 */
    val glints: List<DoubleArray>,
    val windYaw: Double,
    val windSlant: Double,
)

/** kowloonScene.ts kowloonRoofs()：几栋楼并成一块，x1 取最小（照原文），高度取最矮。 */
internal fun machiKowloonRoofs(): List<MachiRoof> {
    fun span(names: List<String>): MachiRoof {
        val bs = names.map { n -> MachiKowloonLayout.BLOCKS.first { it.name == n } }
        return MachiRoof(bs.minOf { it.x0 }, bs.minOf { it.z0 }, bs.minOf { it.x1 }, bs.maxOf { it.z1 }, bs.minOf { it.height })
    }
    val b0 = span(listOf("B0", "B0b"))
    return listOf(
        span(listOf("L0", "L1", "L2")),
        span(listOf("L3", "L4", "L5")),
        MachiRoof(-3.0, b0.z0, -0.3, -2.05, b0.y),
        span(listOf("B1")),
    )
}

/**
 * 场景的天气布置与水洼（kowloonScene.ts 构造里同一条随机序列：先水洼 3 个，再大排档、门头滴水，最后风）。
 * [blockDrips] 是楼面冷气机的滴水（MachiKowloonModel.drips：x, z, from, to, period, phase）。
 */
internal fun resolveMachiKowloonWeather(seed: String, blockDrips: List<DoubleArray>): Pair<MachiPuddles, MachiWeatherLayout> {
    val r = MachiRng(machiHashSeed("kowloon:$seed"))
    val puddles = MachiPuddles(r.next() * 80 - 40, r.next() * 80 - 40, 0.5 + r.next() * 0.25)
    val g = MachiKowloonLayout.LEVEL_GROUND
    val drips = ArrayList<MachiDripSpec>()
    blockDrips.forEach { drips += MachiDripSpec(it[0], it[1], it[2], it[3], it[4], it[5]) }
    var x = MachiKowloonStreet.ST_X0
    while (x <= MachiKowloonStreet.ST_X1 + 0.1) {
        val dx = (r.next() - 0.5) * 0.1
        val period = 1.0 + r.next() * 1.4
        drips += MachiDripSpec(x + dx, MachiKowloonStreet.ST_Z1 + 0.62, 1.95, g, period, r.next())
        x += 0.45
    }
    x = MachiKowloonLayout.SIGN_X - MachiKowloonLayout.SIGN_W / 2
    while (x < MachiKowloonLayout.SIGN_X + MachiKowloonLayout.SIGN_W / 2) {
        val dx = r.next() * 0.2
        val period = 1.3 + r.next() * 1.8
        drips += MachiDripSpec(x + dx, -1.5 + 0.12, MachiKowloonLayout.SIGN_Y - MachiKowloonLayout.SIGN_H / 2 - 0.08, g, period, r.next())
        x += 0.6
    }
    val lampX = MachiKowloonStreet.POLE_X - 0.05; val lampZ = MachiKowloonStreet.POLE_Z + MachiKowloonStreet.POLE_ARM
    val layout = MachiWeatherLayout(
        half = MachiKowloonLayout.BASE_HALF,
        groundY = g,
        roofs = machiKowloonRoofs(),
        drips = drips,
        glints = listOf(
            doubleArrayOf(-2.0, 3.0, 1.0, 2.0),
            doubleArrayOf(MachiKowloonLayout.SCREEN_X, 1.9, -1.5 + 1.0, 2.0),
            doubleArrayOf(lampX - 0.25, 2.6, lampZ + 0.1, 1.6),
        ),
        windYaw = (r.next() - 0.5) * 1.2,
        windSlant = 0.06 + r.next() * 0.12,
    )
    return puddles to layout
}

/** createMachiRuntime.ts：雨量。 */
internal fun resolveMachiRainAmount(envRain: Double, audio: Double = 0.0, chorus: Double = 0.0, intensity: Double = 1.0) =
    envRain * (0.62 + 0.22 * audio + 0.12 * chorus) * intensity

private const val GROUND_GLSL = """
uniform vec4 uRoofs[4];
uniform float uRoofY[4];
uniform float uGroundY;
uniform float uHalf;
float kGround(vec2 p) {
    float ground = uGroundY;
    for (int i = 0; i < 4; i++) {
        vec4 r = uRoofs[i];
        if (p.x > r.x && p.x < r.z && p.y > r.y && p.y < r.w) ground = max(ground, uRoofY[i]);
    }
    return ground;
}
float kHash1(float n) { return fract(sin(n) * 43758.5453123); }
"""

private const val RAIN_VS = """#version 300 es
layout(location = 0) in vec4 aSeed;
layout(location = 1) in float aEnd;
uniform mat4 uVP;
uniform float uTime;
uniform vec2 uWind;
uniform float uLength;
uniform float uSpeed;
uniform float uDensity;
uniform vec4 uGlints[3];
out float vAlpha;
out float vGlint;
$GROUND_GLSL
void main() {
    vec2 base = aSeed.xy;
    float ground = kGround(base);
    float fall = 9.50 - ground;
    float speed = uSpeed * (0.85 + 0.3 * aSeed.w);
    float y = 9.50 - mod(uTime * speed + aSeed.z * fall, fall);
    float drop = 9.50 - y;
    vec3 head = vec3(base.x + uWind.x * drop, y, base.y + uWind.y * drop);
    vec3 dir = normalize(vec3(-uWind.x, 1.0, -uWind.y));
    vec3 position = head + dir * uLength * aEnd * (0.75 + 0.5 * aSeed.w);
    position.y = max(position.y, ground);
    float inside = step(abs(head.x), uHalf + 0.1) * step(abs(head.z), uHalf + 0.1);
    float visible = step(aSeed.w, uDensity);
    vAlpha = (1.0 - aEnd * 0.85) * inside * visible * smoothstep(0.0, 0.6, y - ground);
    vGlint = 0.0;
    for (int i = 0; i < 3; i++) {
        vec3 d = head - uGlints[i].xyz;
        vGlint += exp(-dot(d, d) / (uGlints[i].w * uGlints[i].w));
    }
    gl_Position = uVP * vec4(position, 1.0);
}
"""

private const val RAIN_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
uniform vec3 uGlintColor;
uniform float uOpacity;
in float vAlpha;
in float vGlint;
out vec4 fragColor;
void main() {
    float glint = min(1.0, vGlint);
    vec3 color = mix(uColor, uGlintColor, glint) * (1.0 + 2.2 * glint);
    fragColor = vec4(color * vAlpha * uOpacity, 1.0);
}
"""

private const val SPLASH_VS = """#version 300 es
layout(location = 0) in vec2 aQuad;
layout(location = 1) in vec4 aSeed;
uniform mat4 uVP;
uniform float uTime;
uniform float uRate;
uniform float uDensity;
out vec2 vUv;
out float vLife;
$GROUND_GLSL
void main() {
    float cycle = uTime * uRate * (0.8 + 0.4 * aSeed.w) + aSeed.z;
    float index = floor(cycle);
    float life = fract(cycle);
    vec2 p = vec2(kHash1(index * 12.9898 + aSeed.x * 78.233), kHash1(index * 39.346 + aSeed.y * 11.135)) * 2.0 - 1.0;
    p *= uHalf - 0.05;
    float ground = kGround(p);
    float visible = step(aSeed.w, uDensity);
    float size = (0.06 + 0.24 * life) * visible;
    vUv = aQuad + 0.5;
    vLife = life;
    vec3 local = vec3(aQuad.x * size, 0.0, aQuad.y * size);
    gl_Position = uVP * vec4(local + vec3(p.x, ground + 0.012, p.y), 1.0);
}
"""

private const val SPLASH_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
in vec2 vUv;
in float vLife;
out vec4 fragColor;
void main() {
    float d = length(vUv - 0.5) * 2.0;
    float ring = smoothstep(0.62, 0.86, d) * (1.0 - smoothstep(0.86, 1.0, d));
    float alpha = ring * (1.0 - vLife) * 1.1;
    fragColor = vec4(uColor * alpha, 1.0);
}
"""

private const val DRIP_VS = """#version 300 es
layout(location = 0) in vec2 aQuad;
layout(location = 1) in vec4 aDrip;
layout(location = 2) in vec2 aTiming;
uniform mat4 uVP;
uniform float uTime;
uniform vec3 uRight;
out vec2 vUv;
out float vMode;
out float vFade;
void main() {
    float height = aDrip.z - aDrip.w;
    float fallTime = sqrt(2.0 * height / 9.8);
    float splashTime = 0.28;
    float t = mod(uTime + aTiming.y * aTiming.x, aTiming.x);
    vec3 world;
    if (t < fallTime) {
        float y = aDrip.z - 0.5 * 9.8 * t * t;
        float stretch = 0.03 + 0.05 * (t / fallTime);
        world = vec3(aDrip.x, y, aDrip.y) + uRight * aQuad.x * 0.026 + vec3(0.0, 1.0, 0.0) * aQuad.y * stretch;
        vMode = 0.0;
        vFade = 1.0;
    } else if (t < fallTime + splashTime) {
        float s = (t - fallTime) / splashTime;
        world = vec3(aDrip.x + aQuad.x * (0.05 + 0.16 * s), aDrip.w + 0.014, aDrip.y + aQuad.y * (0.05 + 0.16 * s));
        vMode = 1.0;
        vFade = 1.0 - s;
    } else {
        world = vec3(0.0, -50.0, 0.0);
        vMode = 0.0;
        vFade = 0.0;
    }
    vUv = aQuad + 0.5;
    gl_Position = uVP * vec4(world, 1.0);
}
"""

private const val DRIP_FS = """#version 300 es
precision highp float;
uniform vec3 uColor;
uniform float uAmount;
in vec2 vUv;
in float vMode;
in float vFade;
out vec4 fragColor;
void main() {
    vec2 c = vUv - 0.5;
    float alpha;
    if (vMode < 0.5) {
        alpha = 1.0 - smoothstep(0.3, 0.5, length(c * vec2(1.0, 0.55)));
    } else {
        float d = length(c) * 2.0;
        alpha = smoothstep(0.6, 0.85, d) * (1.0 - smoothstep(0.85, 1.0, d)) * 0.8;
    }
    fragColor = vec4(uColor * alpha * vFade * 1.3 * uAmount, 1.0);
}
"""

/** 雨丝顶点：每条两个端点，各 seed4 + end1。 */
internal fun machiRainSeeds(count: Int, half: Double, seed: Long = 0x5eed): FloatArray {
    val r = MachiRng(seed)
    val out = FloatArray(count * 2 * 5)
    for (i in 0 until count) {
        val s = floatArrayOf(((r.next() * 2 - 1) * (half + 0.6)).toFloat(), ((r.next() * 2 - 1) * (half + 0.6)).toFloat(), r.next().toFloat(), r.next().toFloat())
        for (e in 0..1) {
            val o = (i * 2 + e) * 5
            s.copyInto(out, o); out[o + 4] = e.toFloat()
        }
    }
    return out
}

internal class MachiWeatherFx(
    private val layout: MachiWeatherLayout,
    private val rainCount: Int = 2000,
    private val splashCount: Int = 560,
) {
    private var rainProgram = 0; private var splashProgram = 0; private var dripProgram = 0
    private var rainVao = 0; private var splashVao = 0; private var dripVao = 0
    private val roofRects = FloatArray(16)
    private val roofYs = FloatArray(4) { -100f }
    private val wind = floatArrayOf((sin(layout.windYaw) * layout.windSlant).toFloat(), (cos(layout.windYaw) * layout.windSlant).toFloat())

    init {
        layout.roofs.take(MAX_ROOFS).forEachIndexed { i, r ->
            roofRects[i * 4] = r.x0.toFloat(); roofRects[i * 4 + 1] = r.z0.toFloat(); roofRects[i * 4 + 2] = r.x1.toFloat(); roofRects[i * 4 + 3] = r.z1.toFloat()
            roofYs[i] = r.y.toFloat()
        }
    }

    private fun buffer(data: FloatArray): Int {
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        val fb = java.nio.ByteBuffer.allocateDirect(max(4, data.size * 4)).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        return b[0]
    }

    fun init(link: (String, String) -> Int) {
        rainProgram = link(RAIN_VS, RAIN_FS)
        splashProgram = link(SPLASH_VS, SPLASH_FS)
        dripProgram = link(DRIP_VS, DRIP_FS)
        val v = IntArray(3); GLES30.glGenVertexArrays(3, v, 0)
        rainVao = v[0]; splashVao = v[1]; dripVao = v[2]
        // 雨丝
        GLES30.glBindVertexArray(rainVao)
        buffer(machiRainSeeds(rainCount, layout.half))
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 20, 16)
        // 共用的单位方块（两个三角形，±0.5）
        val quad = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f)
        // 水花
        GLES30.glBindVertexArray(splashVao)
        buffer(quad)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        val r = MachiRng(0x5a1a5L)
        buffer(FloatArray(splashCount * 4) { i -> if (i % 4 == 2) (r.next() * 10).toFloat() else r.next().toFloat() })
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glVertexAttribDivisor(1, 1)
        // 滴水
        GLES30.glBindVertexArray(dripVao)
        buffer(quad)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        val drips = FloatArray(layout.drips.size * 6)
        layout.drips.forEachIndexed { i, d ->
            drips[i * 6] = d.x.toFloat(); drips[i * 6 + 1] = d.z.toFloat(); drips[i * 6 + 2] = d.from.toFloat(); drips[i * 6 + 3] = d.to.toFloat()
            drips[i * 6 + 4] = d.period.toFloat(); drips[i * 6 + 5] = d.phase.toFloat()
        }
        buffer(drips)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 24, 16)
        GLES30.glVertexAttribDivisor(2, 1)
        GLES30.glBindVertexArray(0)
    }

    private fun ground(p: Int) {
        fun u(n: String) = GLES30.glGetUniformLocation(p, n)
        GLES30.glUniform4fv(u("uRoofs"), 4, roofRects, 0)
        GLES30.glUniform1fv(u("uRoofY"), 4, roofYs, 0)
        GLES30.glUniform1f(u("uGroundY"), layout.groundY.toFloat())
        GLES30.glUniform1f(u("uHalf"), layout.half.toFloat())
    }

    /**
     * 画雨丝、水花、滴水（weather.ts update + 各自 renderOrder）。颜色已是线性：
     * rainColor = skyBottom.lerp(#dfe9ff, 0.72)，glintColor = streetLight.lerp(interiorLight, 0.4)，dripColor = rain.lerp(interiorLight, 0.25)。
     */
    fun draw(
        vp: FloatArray, right: FloatArray, time: Double, rain: Double, wetness: Double, glint: Double,
        rainColor: FloatArray, glintColor: FloatArray, dripColor: FloatArray,
    ) {
        val amount = min(1.0, rain)
        val density = max(0.15, amount)
        val showRain = amount > 0.01
        val showDrips = wetness > 0.05 && layout.drips.isNotEmpty()
        if (!showRain && !showDrips) return
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        if (showRain) {
            // 水花（renderOrder 4）
            GLES30.glUseProgram(splashProgram)
            fun s(n: String) = GLES30.glGetUniformLocation(splashProgram, n)
            GLES30.glUniformMatrix4fv(s("uVP"), 1, false, vp, 0)
            ground(splashProgram)
            GLES30.glUniform1f(s("uTime"), time.toFloat())
            GLES30.glUniform1f(s("uRate"), 1.6f)
            GLES30.glUniform1f(s("uDensity"), (min(1.0, amount * 4) * (0.3 + 0.7 * density)).toFloat())
            GLES30.glUniform3fv(s("uColor"), 1, FloatArray(3) { rainColor[it] * 0.8f }, 0)
            GLES30.glBindVertexArray(splashVao)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, splashCount)
            // 雨丝（renderOrder 5）
            GLES30.glUseProgram(rainProgram)
            fun r(n: String) = GLES30.glGetUniformLocation(rainProgram, n)
            GLES30.glUniformMatrix4fv(r("uVP"), 1, false, vp, 0)
            ground(rainProgram)
            GLES30.glUniform1f(r("uTime"), time.toFloat())
            GLES30.glUniform2fv(r("uWind"), 1, wind, 0)
            GLES30.glUniform1f(r("uLength"), 0.42f)
            GLES30.glUniform1f(r("uSpeed"), 11f)
            GLES30.glUniform1f(r("uDensity"), (min(1.0, amount * 4) * (0.35 + 0.65 * density)).toFloat())
            GLES30.glUniform3fv(r("uColor"), 1, rainColor, 0)
            GLES30.glUniform3fv(r("uGlintColor"), 1, glintColor, 0)
            GLES30.glUniform1f(r("uOpacity"), 0.3f)
            val g = FloatArray(12)
            for (i in 0 until 3) {
                val gl = layout.glints.getOrNull(i)
                if (gl == null) { g[i * 4 + 1] = -100f; g[i * 4 + 3] = 0.001f; continue }
                g[i * 4] = gl[0].toFloat(); g[i * 4 + 1] = gl[1].toFloat(); g[i * 4 + 2] = gl[2].toFloat()
                g[i * 4 + 3] = max(0.001, gl[3] * glint).toFloat()
            }
            GLES30.glUniform4fv(r("uGlints"), 3, g, 0)
            GLES30.glBindVertexArray(rainVao)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, rainCount * 2)
        }
        if (showDrips) {
            GLES30.glUseProgram(dripProgram)
            fun d(n: String) = GLES30.glGetUniformLocation(dripProgram, n)
            GLES30.glUniformMatrix4fv(d("uVP"), 1, false, vp, 0)
            GLES30.glUniform1f(d("uTime"), time.toFloat())
            GLES30.glUniform3fv(d("uRight"), 1, right, 0)
            GLES30.glUniform3fv(d("uColor"), 1, dripColor, 0)
            GLES30.glUniform1f(d("uAmount"), min(1.0, wetness * 1.2).toFloat())
            GLES30.glBindVertexArray(dripVao)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, layout.drips.size)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }
}
