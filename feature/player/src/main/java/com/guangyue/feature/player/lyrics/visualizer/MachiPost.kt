// 町景后处理链：逐函数对译 Folia v0.7.25 machi/engine/fx/post.ts + postPasses.ts（MachiAtmospherePass）
// + three 0.185 UnrealBloomPass（strength = look.bloom，radius 0.62，threshold 0.86，5 级 mip，核 6/10/14/18/22、σ = 核/3）
// + OutputPass（NeutralToneMapping、sRGB）。
//   场景（线性 HDR，RGBA16F；设备不支持浮点颜色附件时退 RGBA8）+ 深度贴图 → 氛围（深度拉普拉斯墨线、贴地薄雾与两处灯的
//   雾中散射、天空的云）→ 亮部提取（半分辨率）→ 5 级逐级降采样的可分离高斯 → 合成时把 5 级按 lerpBloomFactor 加回去
//   → 色调映射 + sRGB 上屏。Folia 的 bloom 是先合成进半分辨率再加色叠回，这里在最后一道里直接相加，结果相同、少两遍。
// 模拟器（swiftshader）省力：场景目标长边封顶 1600 px（Folia 的画质分档也按 DPR 降），bloom 从半分辨率起。
// 镜头 pass 只做了色差 0.014 / 暗角 0.55 / 颗粒 0.032 / 晴夜星星（合进最后一道）；横向光条、景深散景（lensPass.ts 圆盘取样，景深前先把 bloom / 光条叠进一张 HDR）已做；抗锯齿用 FXAA（three FXAAShader，输出后在 sRGB 上做）。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val FS_VS = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

private const val NOISE_GLSL = """
float pHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float pNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(pHash(i), pHash(i + vec2(1.0, 0.0)), u.x), mix(pHash(i + vec2(0.0, 1.0)), pHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float pFbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * pNoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amplitude *= 0.5;
    }
    return value;
}
"""

private const val ATMOSPHERE_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform highp sampler2D tDepth;
uniform vec2 uTexel;
uniform float uInkScale;
uniform float uDepthRange;
uniform float uThreshold;
uniform vec3 uInk;
uniform float uInkStrength;
uniform mat4 uInvVP;
uniform float uTime;
uniform vec3 uMistColor;
uniform float uMistAmount;
uniform float uMistHeight;
uniform vec2 uWind;
uniform vec4 uGlowA;
uniform vec3 uGlowColorA;
uniform vec4 uGlowB;
uniform vec3 uGlowColorB;
uniform vec3 uCloudColor;
uniform float uCloudAmount;
uniform float uHaze;
uniform vec2 uHazeRange;
uniform float uLightning;
uniform vec3 uLightningColor;
uniform float uAspect;
in vec2 vUv;
out vec4 fragColor;
""" + NOISE_GLSL + """
float depthAt(vec2 uv) { return texture(tDepth, uv).x * uDepthRange; }
void main() {
    vec4 color = texture(tDiffuse, vUv);
    float raw = texture(tDepth, vUv).x;
    float d = raw * uDepthRange;
    bool sky = raw > 0.9999;
    vec2 inkStep = uTexel * uInkScale;
    float l = depthAt(vUv - vec2(inkStep.x, 0.0));
    float r = depthAt(vUv + vec2(inkStep.x, 0.0));
    float u = depthAt(vUv + vec2(0.0, inkStep.y));
    float b = depthAt(vUv - vec2(0.0, inkStep.y));
    float lu = depthAt(vUv + vec2(-inkStep.x, inkStep.y));
    float rb = depthAt(vUv + vec2(inkStep.x, -inkStep.y));
    float ru = depthAt(vUv + inkStep);
    float lb = depthAt(vUv - inkStep);
    float laplace = abs(l + r - 2.0 * d) + abs(u + b - 2.0 * d) + 0.5 * (abs(lu + rb - 2.0 * d) + abs(ru + lb - 2.0 * d));
    float line = smoothstep(uThreshold, uThreshold * 2.6, laplace) * (sky ? 0.0 : 1.0);
    vec3 rgb = mix(color.rgb, uInk, line * uInkStrength);
    if (sky) {
        vec2 p = vec2(vUv.x * uAspect, vUv.y) * 2.4 + vec2(uTime * 0.012, 0.0);
        float cloud = smoothstep(mix(0.62, 0.38, uCloudAmount), 0.85, pFbm(p)) * smoothstep(0.0, 0.55, vUv.y) * (0.35 + 0.65 * uCloudAmount);
        float wisp = smoothstep(0.55, 0.9, pFbm(p * 2.3 + vec2(uTime * 0.02, 3.1))) * (0.5 + 0.5 * uCloudAmount);
        rgb += uCloudColor * (cloud * 0.55 + wisp * 0.18);
        rgb += uLightningColor * uLightning * (0.18 + 1.1 * cloud + 0.4 * wisp);
        rgb = mix(rgb, uMistColor, uHaze * 0.7);
    } else {
        vec4 w4 = uInvVP * vec4(vUv * 2.0 - 1.0, raw * 2.0 - 1.0, 1.0);
        vec3 world = w4.xyz / w4.w;
        float height = max(world.y, 0.0);
        float drift = pFbm(world.xz * 0.32 + uWind * uTime) * 0.65 + pFbm(world.xz * 0.9 - uWind * uTime * 1.7) * 0.35;
        float density = uMistAmount * exp(-height / uMistHeight) * (0.35 + 0.9 * drift);
        vec2 da = world.xz - uGlowA.xz;
        vec2 db = world.xz - uGlowB.xz;
        vec3 lit = uMistColor
            + uGlowColorA * exp(-dot(da, da) / (uGlowA.w * uGlowA.w)) * exp(-height / 2.2)
            + uGlowColorB * exp(-dot(db, db) / (uGlowB.w * uGlowB.w)) * exp(-height / 3.0);
        rgb = mix(rgb, lit, clamp(density, 0.0, 0.85));
        rgb = mix(rgb, uMistColor, uHaze * (0.3 + 0.7 * smoothstep(uHazeRange.x, uHazeRange.y, d)));
        rgb += rgb * uLightning * 0.18;
    }
    fragColor = vec4(rgb, color.a);
}
"""

// LuminosityHighPassShader（defaultColor 0、smoothWidth 0.01）。
private const val BRIGHT_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform float uThreshold;
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec4 texel = texture(tDiffuse, vUv);
    float v = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
    float alpha = smoothstep(uThreshold, uThreshold + 0.01, v);
    fragColor = mix(vec4(0.0), texel, alpha);
}
"""

private const val BLUR_FS = """#version 300 es
precision highp float;
uniform sampler2D colorTexture;
uniform vec2 invSize;
uniform vec2 direction;
uniform int uRadius;
uniform float gaussianCoefficients[22];
in vec2 vUv;
out vec4 fragColor;
void main() {
    float weightSum = gaussianCoefficients[0];
    vec3 diffuseSum = texture(colorTexture, vUv).rgb * weightSum;
    for (int i = 1; i < 22; i++) {
        if (i >= uRadius) break;
        float x = float(i);
        float w = gaussianCoefficients[i];
        vec2 uvOffset = direction * invSize * x;
        diffuseSum += (texture(colorTexture, vUv + uvOffset).rgb + texture(colorTexture, vUv - uvOffset).rgb) * w;
    }
    fragColor = vec4(diffuseSum, 1.0);
}
"""

// UnrealBloomPass 合成（加色叠回）+ OutputPass（NeutralToneMapping、sRGB）。
private const val OUTPUT_FS = """#version 300 es
precision highp float;
uniform sampler2D tScene;
uniform sampler2D blur1;
uniform sampler2D blur2;
uniform sampler2D blur3;
uniform sampler2D blur4;
uniform sampler2D blur5;
uniform float bloomStrength;
uniform float bloomRadius;
uniform highp sampler2D tDepth;
uniform float uAberration;
uniform float uVignette;
uniform float uGrain;
uniform float uStars;
uniform float uTime;
uniform float uAspect;
uniform sampler2D tStreak;
uniform float uStreak;
uniform sampler2D tBokeh;
uniform float uDofMix;
uniform vec2 uBlend;
uniform float uComposited;
uniform float uDepthRange;
uniform float uFocus;
uniform float uFocusBand;
uniform float uFocusFalloff;
uniform float uCocRadius;
uniform float uTiltRadius;
float machiCoc(float depth01, vec2 uv) {
    float offset = depth01 * uDepthRange - uFocus;
    float coc = smoothstep(uFocusBand, uFocusBand + uFocusFalloff, abs(offset)) * uCocRadius * (offset < 0.0 ? -1.0 : 1.0);
    float tilt = smoothstep(0.2, 0.55, abs(uv.y - 0.5)) * uTiltRadius;
    return abs(coc) >= tilt ? coc : tilt;
}
in vec2 vUv;
out vec4 fragColor;
float lerpBloomFactor(float factor) { return mix(factor, 1.2 - factor, bloomRadius); }
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
vec3 srgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}
float gHash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    // lensPass.ts 的色差（红蓝沿径向错开）；bloom 只在中心取一次（Folia 是 bloom 叠回后整张错开，差别看不出）。
    vec2 centered = vUv - 0.5;
    float edge = dot(centered, centered);
    vec2 shift = centered * edge * uAberration;
    vec3 color = vec3(texture(tScene, vUv + shift).r, texture(tScene, vUv).g, texture(tScene, vUv - shift).b);
    if (uComposited < 0.5 && bloomStrength > 0.0) {
        color += 3.0 * bloomStrength * (
            lerpBloomFactor(1.0) * texture(blur1, vUv).rgb +
            lerpBloomFactor(0.8) * texture(blur2, vUv).rgb +
            lerpBloomFactor(0.6) * texture(blur3, vUv).rgb +
            lerpBloomFactor(0.4) * texture(blur4, vUv).rgb +
            lerpBloomFactor(0.2) * texture(blur5, vUv).rgb);
    }
    // 横向镜头光条（postPasses.ts MachiStreakPass）：tint (0.62, 0.74, 1.0) × strength。
    if (uComposited < 0.5 && uStreak > 0.0) color += texture(tStreak, vUv).rgb * vec3(0.62, 0.74, 1.0) * uStreak;
    // 景深（lensPass.ts）：自己离焦够多就用散景；虚掉的前景盖到这里也用散景。
    float depth01 = texture(tDepth, vUv).x;
    if (uDofMix > 0.0) {
        vec4 bokeh = texture(tBokeh, vUv);
        float own = smoothstep(uBlend.x, uBlend.y, abs(machiCoc(depth01, vUv)));
        float amount = (own + bokeh.a - own * bokeh.a) * uDofMix;
        color = mix(color, bokeh.rgb, clamp(amount, 0.0, 1.0));
    }
    // 晴夜的星星、暗角、颗粒（lensPass.ts）。
    if (uStars > 0.001 && depth01 > 0.9999) {
        vec2 sp = vec2(vUv.x * uAspect, vUv.y) * 140.0;
        vec2 cell = floor(sp);
        float starSeed = gHash(cell);
        vec2 starAt = vec2(gHash(cell + 3.7), gHash(cell + 9.1)) * 0.6 + 0.2;
        float star = step(0.984, starSeed) * smoothstep(0.24, 0.0, length(fract(sp) - starAt))
            * (0.55 + 0.45 * sin(uTime * (0.8 + starSeed * 3.0) + starSeed * 60.0));
        color += vec3(0.85, 0.9, 1.0) * star * uStars * 1.3 * smoothstep(0.1, 0.55, vUv.y);
    }
    vec2 v = centered * vec2(uAspect, 1.0);
    float vignette = smoothstep(1.15, 0.35, length(v) * 1.15);
    color *= mix(1.0, vignette, uVignette);
    float grain = gHash(vUv * vec2(1733.0, 977.0) + fract(uTime * 7.31)) - 0.5;
    color += grain * uGrain * (0.25 + dot(color, vec3(0.3333)));
    fragColor = vec4(srgb(neutral(max(color, 0.0))), 1.0);
}
"""

// postPasses.ts BRIGHT_FRAGMENT / STREAK_BLUR_FRAGMENT（光条：亮处四点均值、阈值 1.1，再横向三次 1/4/14 像素步长模糊）。
private const val STREAK_BRIGHT_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform vec2 uTexel;
uniform float uThreshold;
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec3 c = texture(tDiffuse, vUv + uTexel * vec2(-1.0, -1.0)).rgb
        + texture(tDiffuse, vUv + uTexel * vec2(1.0, -1.0)).rgb
        + texture(tDiffuse, vUv + uTexel * vec2(-1.0, 1.0)).rgb
        + texture(tDiffuse, vUv + uTexel * vec2(1.0, 1.0)).rgb;
    c *= 0.25;
    float luminance = dot(c, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(c * smoothstep(uThreshold, uThreshold + 0.5, luminance), 1.0);
}
"""

private const val STREAK_BLUR_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform vec2 uStep;
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec3 sum = texture(tDiffuse, vUv).rgb * 0.2;
    float weights[4];
    weights[0] = 0.17; weights[1] = 0.13; weights[2] = 0.08; weights[3] = 0.04;
    for (int i = 0; i < 4; i++) {
        vec2 offset = uStep * float(i + 1);
        sum += (texture(tDiffuse, vUv + offset).rgb + texture(tDiffuse, vUv - offset).rgb) * weights[i];
    }
    fragColor = vec4(sum, 1.0);
}
"""

// 景深前先把 bloom / 光条叠进一张 HDR（Folia 的镜头 pass 在 bloom、光条之后）。
private const val COMPOSITE_FS = """#version 300 es
precision highp float;
uniform sampler2D tScene;
uniform sampler2D blur1;
uniform sampler2D blur2;
uniform sampler2D blur3;
uniform sampler2D blur4;
uniform sampler2D blur5;
uniform float bloomStrength;
uniform float bloomRadius;
uniform sampler2D tStreak;
uniform float uStreak;
in vec2 vUv;
out vec4 fragColor;
float lerpBloomFactor(float factor) { return mix(factor, 1.2 - factor, bloomRadius); }
void main() {
    vec3 color = texture(tScene, vUv).rgb;
    if (bloomStrength > 0.0) {
        color += 3.0 * bloomStrength * (
            lerpBloomFactor(1.0) * texture(blur1, vUv).rgb +
            lerpBloomFactor(0.8) * texture(blur2, vUv).rgb +
            lerpBloomFactor(0.6) * texture(blur3, vUv).rgb +
            lerpBloomFactor(0.4) * texture(blur4, vUv).rgb +
            lerpBloomFactor(0.2) * texture(blur5, vUv).rgb);
    }
    if (uStreak > 0.0) color += texture(tStreak, vUv).rgb * vec3(0.62, 0.74, 1.0) * uStreak;
    fragColor = vec4(color, 1.0);
}
"""

// lensPass.ts PREFILTER / DOWNSAMPLE / GATHER / TENT（散景：以收代散的黄金角圆盘取样）。
private const val DOF_PREFILTER_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform highp sampler2D tDepth;
uniform vec2 uSourceTexel;
in vec2 vUv;
out vec4 fragColor;
uniform float uDepthRange;
uniform float uFocus;
uniform float uFocusBand;
uniform float uFocusFalloff;
uniform float uCocRadius;
uniform float uTiltRadius;
float machiCoc(float depth01, vec2 uv) {
    float offset = depth01 * uDepthRange - uFocus;
    float coc = smoothstep(uFocusBand, uFocusBand + uFocusFalloff, abs(offset)) * uCocRadius * (offset < 0.0 ? -1.0 : 1.0);
    float tilt = smoothstep(0.2, 0.55, abs(uv.y - 0.5)) * uTiltRadius;
    return abs(coc) >= tilt ? coc : tilt;
}
float widest(float a, float b) { return abs(a) >= abs(b) ? a : b; }
void main() {
    vec2 o = uSourceTexel * 0.5;
    vec2 p0 = vUv + vec2(-o.x, -o.y);
    vec2 p1 = vUv + vec2(o.x, -o.y);
    vec2 p2 = vUv + vec2(-o.x, o.y);
    vec2 p3 = vUv + vec2(o.x, o.y);
    vec3 color = (texture(tDiffuse, p0).rgb + texture(tDiffuse, p1).rgb + texture(tDiffuse, p2).rgb + texture(tDiffuse, p3).rgb) * 0.25;
    float coc = widest(
        widest(machiCoc(texture(tDepth, p0).x, p0), machiCoc(texture(tDepth, p1).x, p1)),
        widest(machiCoc(texture(tDepth, p2).x, p2), machiCoc(texture(tDepth, p3).x, p3))
    );
    fragColor = vec4(color, coc);
}
"""

private const val DOF_DOWNSAMPLE_FS = """#version 300 es
precision highp float;
uniform sampler2D tSource;
uniform vec2 uSourceTexel;
in vec2 vUv;
out vec4 fragColor;
float widest(float a, float b) { return abs(a) >= abs(b) ? a : b; }
void main() {
    vec2 o = uSourceTexel * 0.5;
    vec4 s0 = texture(tSource, vUv + vec2(-o.x, -o.y));
    vec4 s1 = texture(tSource, vUv + vec2(o.x, -o.y));
    vec4 s2 = texture(tSource, vUv + vec2(-o.x, o.y));
    vec4 s3 = texture(tSource, vUv + vec2(o.x, o.y));
    fragColor = vec4((s0.rgb + s1.rgb + s2.rgb + s3.rgb) * 0.25, widest(widest(s0.a, s1.a), widest(s2.a, s3.a)));
}
"""

private const val DOF_GATHER_FS = """#version 300 es
precision highp float;
uniform sampler2D tSource;
uniform vec2 uTexel;
uniform float uMaxCoc;
uniform float uLevelScale;
uniform float uTaps;
in vec2 vUv;
out vec4 fragColor;
const mat2 GOLDEN_TURN = mat2(-0.73736888, 0.67549029, -0.67549029, -0.73736888);
const float MARGIN = 1.2;
void main() {
    vec4 center = texture(tSource, vUv);
    float centerCoc = center.a / uLevelScale;
    vec4 far = vec4(0.0);
    vec4 near = vec4(0.0);
    vec2 direction = vec2(1.0, 0.0);
    for (int i = 0; i < 48; i++) {
        if (float(i) >= uTaps) break;
        float radius = sqrt((float(i) + 0.5) / uTaps) * uMaxCoc;
        vec4 tap = texture(tSource, vUv + direction * radius * uTexel);
        direction = GOLDEN_TURN * direction;
        float tapCoc = tap.a / uLevelScale;
        float farCoc = max(min(centerCoc, tapCoc), 0.0);
        float farWeight = clamp((farCoc - radius + MARGIN) / MARGIN, 0.0, 1.0);
        float nearWeight = clamp((-tapCoc - radius + MARGIN) / MARGIN, 0.0, 1.0) * step(1.0, -tapCoc);
        far += vec4(tap.rgb, 1.0) * farWeight;
        near += vec4(tap.rgb, 1.0) * nearWeight;
    }
    vec3 farColor = far.a > 0.0 ? far.rgb / far.a : center.rgb;
    vec3 nearColor = near.a > 0.0 ? near.rgb / near.a : center.rgb;
    float nearAlpha = clamp(near.a * 3.14159 / uTaps, 0.0, 1.0);
    fragColor = vec4(mix(farColor, nearColor, nearAlpha), nearAlpha);
}
"""

private const val DOF_TENT_FS = """#version 300 es
precision highp float;
uniform sampler2D tSource;
uniform vec2 uTexel;
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec2 o = uTexel * 0.5;
    fragColor = (texture(tSource, vUv + vec2(-o.x, -o.y)) + texture(tSource, vUv + vec2(o.x, -o.y))
        + texture(tSource, vUv + vec2(-o.x, o.y)) + texture(tSource, vUv + vec2(o.x, o.y))) * 0.25;
}
"""

/** lensPass.ts resolveMachiBokehLevel：散景用第几层（0 = 一半分辨率）、最大圆在那层几像素、取几个样、合几倍。 */
internal data class MachiBokehLevel(val level: Int, val levelCoc: Double, val taps: Int, val scale: Int)

internal fun resolveMachiBokehLevel(maxCocPx: Double): MachiBokehLevel {
    var level = 0
    var levelCoc = maxCocPx / 2
    while (levelCoc > 8 && level < DOF_LEVELS - 1) { level += 1; levelCoc /= 2 }
    val taps = min(48, max(12, ceil(PI * levelCoc * levelCoc / 4).toInt()))
    return MachiBokehLevel(level, levelCoc, taps, 1 shl (level + 1))
}

// 抗锯齿（post.ts：FXAA 在输出之后的 sRGB 上做，边缘检测才准）：three FXAAShader（r185）照抄。
private const val FXAA_FS = """#version 300 es
precision highp float;
uniform sampler2D tDiffuse;
uniform vec2 resolution;
in vec2 vUv;
out vec4 fragColor;
// 结构体拆成局部变量（SwiftShader 对返回含 bool 的结构体不稳），算式与 three FXAAShader 逐行一致。
const float EDGE_GUESS = 8.0;
float lum(vec2 uv) { return dot(texture(tDiffuse, uv).rgb, vec3(0.3, 0.59, 0.11)); }
float edgeStepAt(int i) { return i == 0 ? 1.0 : (i == 1 ? 1.5 : (i == 5 ? 4.0 : 2.0)); }
void main() {
    vec2 ts = resolution;
    vec2 uv = vUv;
    float m = lum(uv);
    float n = lum(uv + ts * vec2(0.0, 1.0));
    float e = lum(uv + ts * vec2(1.0, 0.0));
    float s = lum(uv + ts * vec2(0.0, -1.0));
    float w = lum(uv + ts * vec2(-1.0, 0.0));
    float ne = lum(uv + ts * vec2(1.0, 1.0));
    float nw = lum(uv + ts * vec2(-1.0, 1.0));
    float se = lum(uv + ts * vec2(1.0, -1.0));
    float sw = lum(uv + ts * vec2(-1.0, -1.0));
    float highest = max(max(max(max(n, e), s), w), m);
    float lowest = min(min(min(min(n, e), s), w), m);
    float contrast = highest - lowest;
    if (contrast < max(0.0312, 0.063 * highest)) { fragColor = texture(tDiffuse, uv); return; }
    float f = (2.0 * (n + e + s + w) + ne + nw + se + sw) / 12.0;
    f = clamp(abs(f - m) / contrast, 0.0, 1.0);
    float bf = smoothstep(0.0, 1.0, f);
    float pixelBlend = bf * bf;
    float horizontal = abs(n + s - 2.0 * m) * 2.0 + abs(ne + se - 2.0 * e) + abs(nw + sw - 2.0 * w);
    float vertical = abs(e + w - 2.0 * m) * 2.0 + abs(ne + nw - 2.0 * n) + abs(se + sw - 2.0 * s);
    bool isHorizontal = horizontal >= vertical;
    float pL = isHorizontal ? n : e;
    float nL = isHorizontal ? s : w;
    float pG = abs(pL - m);
    float nG = abs(nL - m);
    float pixelStep = isHorizontal ? ts.y : ts.x;
    float oppositeL; float gradient;
    if (pG < nG) { pixelStep = -pixelStep; oppositeL = nL; gradient = nG; } else { oppositeL = pL; gradient = pG; }
    vec2 uvEdge = uv;
    vec2 edgeStep;
    if (isHorizontal) { uvEdge.y += pixelStep * 0.5; edgeStep = vec2(ts.x, 0.0); }
    else { uvEdge.x += pixelStep * 0.5; edgeStep = vec2(0.0, ts.y); }
    float edgeL = (m + oppositeL) * 0.5;
    float threshold = gradient * 0.25;
    vec2 puv = uvEdge + edgeStep;
    float pDelta = lum(puv) - edgeL;
    bool pAtEnd = abs(pDelta) >= threshold;
    for (int i = 1; i < 6; i++) {
        if (pAtEnd) break;
        puv += edgeStep * edgeStepAt(i);
        pDelta = lum(puv) - edgeL;
        pAtEnd = abs(pDelta) >= threshold;
    }
    if (!pAtEnd) puv += edgeStep * EDGE_GUESS;
    vec2 nuv = uvEdge - edgeStep;
    float nDelta = lum(nuv) - edgeL;
    bool nAtEnd = abs(nDelta) >= threshold;
    for (int i = 1; i < 6; i++) {
        if (nAtEnd) break;
        nuv -= edgeStep * edgeStepAt(i);
        nDelta = lum(nuv) - edgeL;
        nAtEnd = abs(nDelta) >= threshold;
    }
    if (!nAtEnd) nuv -= edgeStep * EDGE_GUESS;
    float pD = isHorizontal ? puv.x - uv.x : puv.y - uv.y;
    float nD = isHorizontal ? uv.x - nuv.x : uv.y - nuv.y;
    float shortest; bool deltaSign;
    if (pD <= nD) { shortest = pD; deltaSign = pDelta >= 0.0; } else { shortest = nD; deltaSign = nDelta >= 0.0; }
    float edgeBlend = (deltaSign == (m - edgeL >= 0.0)) ? 0.0 : 0.5 - shortest / (pD + nD);
    float finalBlend = max(pixelBlend, edgeBlend);
    if (isHorizontal) uv.y += pixelStep * finalBlend; else uv.x += pixelStep * finalBlend;
    fragColor = texture(tDiffuse, uv);
}
"""

private const val DOF_LEVELS = 5
private const val KERNELS = 5
private val KERNEL_SIZES = intArrayOf(6, 10, 14, 18, 22)
private const val SCENE_MAX_SIDE = 1600

/** 氛围 pass 每帧的量（post.ts MachiAtmosphereFrame）。颜色都是线性。 */
internal class MachiAtmosphereFrame(
    val time: Double,
    val mistColor: FloatArray,
    val mistAmount: Double,
    val cloudColor: FloatArray,
    val cloudAmount: Double,
    val haze: Double,
    val lightning: Double,
    val glowA: FloatArray,
    val glowColorA: FloatArray,
    val glowB: FloatArray,
    val glowColorB: FloatArray,
    val stars: Double = 0.0,
)

private class Target(val fbo: Int, val tex: Int, val w: Int, val h: Int)

internal class MachiPostChain {
    private var atmosphereProgram = 0
    private var brightProgram = 0
    private var blurProgram = 0
    private var outputProgram = 0
    private var streakBrightProgram = 0
    private var streakBlurProgram = 0
    private var streakA: Target? = null
    private var streakB: Target? = null
    private var streakResult = 0
    private var compositeProgram = 0
    private var dofPrefilterProgram = 0
    private var dofDownsampleProgram = 0
    private var dofGatherProgram = 0
    private var dofTentProgram = 0
    private var comp: Target? = null
    private var ldr: Target? = null
    private var fxaaProgram = 0
    private val dofChain = arrayOfNulls<Target>(DOF_LEVELS)
    private val dofWorkA = arrayOfNulls<Target>(DOF_LEVELS)
    private val dofWorkB = arrayOfNulls<Target>(DOF_LEVELS)
    private var emptyVao = 0
    private var sceneFbo = 0
    private var sceneTex = 0
    private var depthTex = 0
    private var atmo: Target? = null
    private var bright: Target? = null
    private val horizontal = arrayOfNulls<Target>(KERNELS)
    private val vertical = arrayOfNulls<Target>(KERNELS)
    private var floatColor = false
    var sceneW = 0; private set
    var sceneH = 0; private set
    private var screenW = 0
    private var screenH = 0
    private val coefficients = Array(KERNELS) { i ->
        val radius = KERNEL_SIZES[i]
        val sigma = radius / 3.0
        FloatArray(22) { k -> if (k < radius) (0.39894 * exp(-0.5 * k * k / (sigma * sigma)) / sigma).toFloat() else 0f }
    }

    fun init(link: (String, String) -> Int) {
        atmosphereProgram = link(FS_VS, ATMOSPHERE_FS)
        brightProgram = link(FS_VS, BRIGHT_FS)
        blurProgram = link(FS_VS, BLUR_FS)
        outputProgram = link(FS_VS, OUTPUT_FS)
        streakBrightProgram = link(FS_VS, STREAK_BRIGHT_FS)
        streakBlurProgram = link(FS_VS, STREAK_BLUR_FS)
        compositeProgram = link(FS_VS, COMPOSITE_FS)
        dofPrefilterProgram = link(FS_VS, DOF_PREFILTER_FS)
        dofDownsampleProgram = link(FS_VS, DOF_DOWNSAMPLE_FS)
        dofGatherProgram = link(FS_VS, DOF_GATHER_FS)
        dofTentProgram = link(FS_VS, DOF_TENT_FS)
        fxaaProgram = link(FS_VS, FXAA_FS)
        val ids = IntArray(1); GLES30.glGenVertexArrays(1, ids, 0); emptyVao = ids[0]
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        floatColor = ext.contains("GL_EXT_color_buffer_float") || ext.contains("GL_EXT_color_buffer_half_float")
    }

    private fun colorTexture(w: Int, h: Int): Int {
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        if (floatColor) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun ldrTexture(w: Int, h: Int): Int {
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun target(w: Int, h: Int, ldrColor: Boolean = false): Target {
        val tex = if (ldrColor) ldrTexture(w, h) else colorTexture(w, h)
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        return Target(f[0], tex, w, h)
    }

    private fun release() {
        val texs = ArrayList<Int>(); val fbos = ArrayList<Int>()
        if (sceneFbo != 0) { fbos += sceneFbo; texs += sceneTex; texs += depthTex }
        (listOf(atmo, bright, streakA, streakB, comp, ldr) + horizontal + vertical + dofChain + dofWorkA + dofWorkB).filterNotNull().forEach { fbos += it.fbo; texs += it.tex }
        if (texs.isNotEmpty()) GLES30.glDeleteTextures(texs.size, texs.toIntArray(), 0)
        if (fbos.isNotEmpty()) GLES30.glDeleteFramebuffers(fbos.size, fbos.toIntArray(), 0)
        sceneFbo = 0; atmo = null; bright = null; streakA = null; streakB = null; comp = null; ldr = null
        horizontal.fill(null); vertical.fill(null); dofChain.fill(null); dofWorkA.fill(null); dofWorkB.fill(null)
    }

    fun resize(width: Int, height: Int) {
        release()
        screenW = width; screenH = height
        val k = min(1.0, SCENE_MAX_SIDE.toDouble() / max(width, height))
        sceneW = max(1, (width * k).roundToInt()); sceneH = max(1, (height * k).roundToInt())
        sceneTex = colorTexture(sceneW, sceneH)
        val d = IntArray(1); GLES30.glGenTextures(1, d, 0); depthTex = d[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, sceneW, sceneH, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val f = IntArray(1); GLES30.glGenFramebuffers(1, f, 0); sceneFbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneTex, 0)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, depthTex, 0)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE && floatColor) {
            android.util.Log.w("MachiPost", "RGBA16F 场景目标不完整，退 RGBA8")
            floatColor = false
            resize(width, height)
            return
        }
        atmo = target(sceneW, sceneH)
        val qw = max(1, (sceneW / 4.0).roundToInt()); val qh = max(1, (sceneH / 4.0).roundToInt())
        streakA = target(qw, qh); streakB = target(qw, qh)
        comp = target(sceneW, sceneH)
        ldr = target(screenW, screenH, ldrColor = true)
        for (i in 0 until DOF_LEVELS) {
            val div = (1 shl (i + 1)).toDouble()
            val w = max(1, (sceneW / div).roundToInt()); val h = max(1, (sceneH / div).roundToInt())
            dofChain[i] = target(w, h); dofWorkA[i] = target(w, h); dofWorkB[i] = target(w, h)
        }
        var rx = (sceneW / 2.0).roundToInt(); var ry = (sceneH / 2.0).roundToInt()
        bright = target(max(1, rx), max(1, ry))
        for (i in 0 until KERNELS) {
            horizontal[i] = target(max(1, rx), max(1, ry))
            vertical[i] = target(max(1, rx), max(1, ry))
            rx = (rx / 2.0).roundToInt(); ry = (ry / 2.0).roundToInt()
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /** 场景画进这里（调用方清屏、画天空 / 楼 / 招牌）。 */
    fun beginScene() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glViewport(0, 0, sceneW, sceneH)
    }

    private fun fullscreen() {
        GLES30.glBindVertexArray(emptyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun bind(unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    }

    /**
     * 后处理并上屏。pixelWorldSize：一个场景像素代表的世界尺寸；centerDistance：镜头到目标点（雾的远近）；
     * depthRange = far − near；invVP：视图投影矩阵的逆（列主序）。
     */
    fun finish(
        pixelWorldSize: Double, centerDistance: Double, near: Double, depthRange: Double, invVP: FloatArray,
        atmosphere: MachiAtmosphereFrame, bloomStrength: Double, streakStrength: Double = 0.0,
        lens: MachiLens? = null,
    ) {
        val a = atmo ?: return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        // 1. 氛围
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, a.fbo)
        GLES30.glViewport(0, 0, a.w, a.h)
        val p = atmosphereProgram
        GLES30.glUseProgram(p)
        fun u(n: String) = GLES30.glGetUniformLocation(p, n)
        bind(0, sceneTex); GLES30.glUniform1i(u("tDiffuse"), 0)
        bind(1, depthTex); GLES30.glUniform1i(u("tDepth"), 1)
        GLES30.glUniform2f(u("uTexel"), 1f / sceneW, 1f / sceneH)
        GLES30.glUniform1f(u("uInkScale"), 1f)
        GLES30.glUniform1f(u("uDepthRange"), depthRange.toFloat())
        GLES30.glUniform1f(u("uThreshold"), max(0.004, pixelWorldSize * 0.7).toFloat())
        GLES30.glUniform3fv(u("uInk"), 1, srgbHexToLinear(0x0b0f1a), 0)
        GLES30.glUniform1f(u("uInkStrength"), 0.82f)
        GLES30.glUniformMatrix4fv(u("uInvVP"), 1, false, invVP, 0)
        GLES30.glUniform1f(u("uTime"), atmosphere.time.toFloat())
        GLES30.glUniform3fv(u("uMistColor"), 1, atmosphere.mistColor, 0)
        GLES30.glUniform1f(u("uMistAmount"), atmosphere.mistAmount.toFloat())
        GLES30.glUniform1f(u("uMistHeight"), 0.55f)
        GLES30.glUniform2f(u("uWind"), 0.05f, 0.02f)
        GLES30.glUniform4fv(u("uGlowA"), 1, atmosphere.glowA, 0)
        GLES30.glUniform3fv(u("uGlowColorA"), 1, atmosphere.glowColorA, 0)
        GLES30.glUniform4fv(u("uGlowB"), 1, atmosphere.glowB, 0)
        GLES30.glUniform3fv(u("uGlowColorB"), 1, atmosphere.glowColorB, 0)
        GLES30.glUniform3fv(u("uCloudColor"), 1, atmosphere.cloudColor, 0)
        GLES30.glUniform1f(u("uCloudAmount"), atmosphere.cloudAmount.toFloat())
        GLES30.glUniform1f(u("uHaze"), atmosphere.haze.toFloat())
        GLES30.glUniform2f(u("uHazeRange"), (centerDistance - near - 8).toFloat(), (centerDistance - near + 14).toFloat())
        GLES30.glUniform1f(u("uLightning"), atmosphere.lightning.toFloat())
        GLES30.glUniform3fv(u("uLightningColor"), 1, srgbHexToLinear(0xc9d8ff), 0)
        GLES30.glUniform1f(u("uAspect"), sceneW.toFloat() / max(1, sceneH))
        fullscreen()
        // 2. bloom：亮部 → 5 级可分离高斯
        val bloomOn = bloomStrength > 0.005
        if (bloomOn) {
            val br = bright!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, br.fbo)
            GLES30.glViewport(0, 0, br.w, br.h)
            GLES30.glUseProgram(brightProgram)
            bind(0, a.tex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(brightProgram, "tDiffuse"), 0)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(brightProgram, "uThreshold"), 0.86f)
            fullscreen()
            GLES30.glUseProgram(blurProgram)
            val uTex = GLES30.glGetUniformLocation(blurProgram, "colorTexture")
            val uInv = GLES30.glGetUniformLocation(blurProgram, "invSize")
            val uDir = GLES30.glGetUniformLocation(blurProgram, "direction")
            val uRad = GLES30.glGetUniformLocation(blurProgram, "uRadius")
            val uCoef = GLES30.glGetUniformLocation(blurProgram, "gaussianCoefficients")
            GLES30.glUniform1i(uTex, 0)
            var input = br.tex
            for (i in 0 until KERNELS) {
                val h = horizontal[i]!!; val v = vertical[i]!!
                GLES30.glUniform1i(uRad, KERNEL_SIZES[i])
                GLES30.glUniform1fv(uCoef, 22, coefficients[i], 0)
                GLES30.glUniform2f(uInv, 1f / h.w, 1f / h.h)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, h.fbo); GLES30.glViewport(0, 0, h.w, h.h)
                bind(0, input); GLES30.glUniform2f(uDir, 1f, 0f); fullscreen()
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, v.fbo)
                bind(0, h.tex); GLES30.glUniform2f(uDir, 0f, 1f); fullscreen()
                input = v.tex
            }
        }
        // 2b. 光条（MachiStreakPass）：Folia 取 bloom 叠回后的画面找亮处，这里取氛围输出（bloom 在最后一道才加），阈值附近略少一点。
        val streakOn = streakStrength > 0.005
        if (streakOn) {
            val sa = streakA!!; val sb = streakB!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sa.fbo); GLES30.glViewport(0, 0, sa.w, sa.h)
            GLES30.glUseProgram(streakBrightProgram)
            bind(0, a.tex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(streakBrightProgram, "tDiffuse"), 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(streakBrightProgram, "uTexel"), 1f / sceneW, 1f / sceneH)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(streakBrightProgram, "uThreshold"), 1.1f)
            fullscreen()
            GLES30.glUseProgram(streakBlurProgram)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(streakBlurProgram, "tDiffuse"), 0)
            val uStep = GLES30.glGetUniformLocation(streakBlurProgram, "uStep")
            var src = sa; var dst = sb
            for (spread in intArrayOf(1, 4, 14)) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
                bind(0, src.tex)
                GLES30.glUniform2f(uStep, spread.toFloat() / sa.w, 0f)
                fullscreen()
                val t = src; src = dst; dst = t
            }
            streakResult = src.tex
        }
        // 2c. 景深（lensPass.ts）：对焦 = 镜头到目标点距离 − near（tuning.focus 'center'）；离焦圆按场景目标高度换像素。
        val cocPx = max(0.0, lens?.coc ?: 0.0) * sceneH
        val tiltPx = max(0.0, lens?.tilt ?: 0.0) * sceneH
        val dofOn = lens != null && (cocPx > 0.5 || tiltPx > 0.5)
        val bokeh = resolveMachiBokehLevel(max(cocPx, tiltPx))
        var bokehTex = a.tex
        fun cocUniforms(prog: Int) {
            fun n(k: String) = GLES30.glGetUniformLocation(prog, k)
            GLES30.glUniform1f(n("uDepthRange"), depthRange.toFloat())
            GLES30.glUniform1f(n("uFocus"), (centerDistance - near).toFloat())
            GLES30.glUniform1f(n("uFocusBand"), (lens?.focusBand ?: 3.0).toFloat())
            GLES30.glUniform1f(n("uFocusFalloff"), (lens?.focusFalloff ?: 7.0).toFloat())
            GLES30.glUniform1f(n("uCocRadius"), if (dofOn) cocPx.toFloat() else 0f)
            GLES30.glUniform1f(n("uTiltRadius"), if (dofOn) tiltPx.toFloat() else 0f)
        }
        if (dofOn) {
            val c = comp!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, c.fbo); GLES30.glViewport(0, 0, c.w, c.h)
            val cp = compositeProgram
            GLES30.glUseProgram(cp)
            bind(0, a.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(cp, "tScene"), 0)
            for (i in 0 until KERNELS) {
                bind(1 + i, vertical[i]!!.tex)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(cp, "blur${i + 1}"), 1 + i)
            }
            GLES30.glUniform1f(GLES30.glGetUniformLocation(cp, "bloomStrength"), if (bloomOn) bloomStrength.toFloat() else 0f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(cp, "bloomRadius"), 0.62f)
            bind(7, if (streakOn) streakResult else a.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(cp, "tStreak"), 7)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(cp, "uStreak"), if (streakOn) streakStrength.toFloat() else 0f)
            fullscreen()
            val first = dofChain[0]!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, first.fbo); GLES30.glViewport(0, 0, first.w, first.h)
            GLES30.glUseProgram(dofPrefilterProgram)
            bind(0, c.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(dofPrefilterProgram, "tDiffuse"), 0)
            bind(1, depthTex); GLES30.glUniform1i(GLES30.glGetUniformLocation(dofPrefilterProgram, "tDepth"), 1)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(dofPrefilterProgram, "uSourceTexel"), 1f / sceneW, 1f / sceneH)
            cocUniforms(dofPrefilterProgram)
            fullscreen()
            GLES30.glUseProgram(dofDownsampleProgram)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(dofDownsampleProgram, "tSource"), 0)
            for (i in 1..bokeh.level) {
                val src = dofChain[i - 1]!!; val dst = dofChain[i]!!
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo); GLES30.glViewport(0, 0, dst.w, dst.h)
                bind(0, src.tex)
                GLES30.glUniform2f(GLES30.glGetUniformLocation(dofDownsampleProgram, "uSourceTexel"), 1f / src.w, 1f / src.h)
                fullscreen()
            }
            val src = dofChain[bokeh.level]!!; val ga = dofWorkA[bokeh.level]!!; val gb = dofWorkB[bokeh.level]!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ga.fbo); GLES30.glViewport(0, 0, ga.w, ga.h)
            val gp = dofGatherProgram
            GLES30.glUseProgram(gp)
            bind(0, src.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(gp, "tSource"), 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(gp, "uTexel"), 1f / src.w, 1f / src.h)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(gp, "uMaxCoc"), bokeh.levelCoc.toFloat())
            GLES30.glUniform1f(GLES30.glGetUniformLocation(gp, "uLevelScale"), bokeh.scale.toFloat())
            GLES30.glUniform1f(GLES30.glGetUniformLocation(gp, "uTaps"), bokeh.taps.toFloat())
            fullscreen()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, gb.fbo)
            GLES30.glUseProgram(dofTentProgram)
            bind(0, ga.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(dofTentProgram, "tSource"), 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(dofTentProgram, "uTexel"), 1f / src.w, 1f / src.h)
            fullscreen()
            bokehTex = gb.tex
        }
        // 3. 合成 + 色调映射（进 LDR 目标，再 FXAA 上屏）
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ldr!!.fbo)
        GLES30.glViewport(0, 0, screenW, screenH)
        val o = outputProgram
        GLES30.glUseProgram(o)
        bind(0, if (dofOn) comp!!.tex else a.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(o, "tScene"), 0)
        for (i in 0 until KERNELS) {
            bind(1 + i, vertical[i]!!.tex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(o, "blur${i + 1}"), 1 + i)
        }
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "bloomStrength"), if (bloomOn) bloomStrength.toFloat() else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "bloomRadius"), 0.62f)
        bind(6, depthTex); GLES30.glUniform1i(GLES30.glGetUniformLocation(o, "tDepth"), 6)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uAberration"), 0.014f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uVignette"), 0.55f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uGrain"), 0.032f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uStars"), atmosphere.stars.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uTime"), atmosphere.time.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uAspect"), sceneW.toFloat() / max(1, sceneH))
        bind(7, if (streakOn) streakResult else a.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(o, "tStreak"), 7)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uStreak"), if (streakOn) streakStrength.toFloat() else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uComposited"), if (dofOn) 1f else 0f)
        bind(8, bokehTex); GLES30.glUniform1i(GLES30.glGetUniformLocation(o, "tBokeh"), 8)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(o, "uDofMix"), if (dofOn) 1f else 0f)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(o, "uBlend"), 0.5f * bokeh.scale, 1.5f * bokeh.scale)
        cocUniforms(o)
        fullscreen()
        // 4. FXAA 上屏
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glUseProgram(fxaaProgram)
        bind(0, ldr!!.tex); GLES30.glUniform1i(GLES30.glGetUniformLocation(fxaaProgram, "tDiffuse"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(fxaaProgram, "resolution"), 1f / screenW, 1f / screenH)
        fullscreen()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }
}

/** three Color.setHex（sRGB → 线性，按 sRGB 传递函数）。 */
internal fun srgbHexToLinear(rgb: Int): FloatArray = FloatArray(3) { i ->
    val c = ((rgb shr (16 - 8 * i)) and 0xff) / 255.0
    (if (c < 0.04045) c * 0.0773993808 else Math.pow(c * 0.9478672986 + 0.0521327014, 2.4)).toFloat()
}

/** 十六进制颜色串 → 线性（同上）。 */
internal fun machiLinear(hex: String, k: Double = 1.0): FloatArray =
    srgbHexToLinear(Integer.parseInt(hex.removePrefix("#").take(6), 16)).let { c -> FloatArray(3) { i -> (c[i] * k).toFloat() } }
