// 绘光（Folia lumiere）的光场：烟雾 + 体积光 + 眩光 + 焦散 + 干涉条纹，一个全屏着色器。
// 对应 Folia 文件：src/components/visualizer/lumiere/light/lightFieldShader.ts（GLSL 片元着色器 → AGSL 逐行照译，
// 公式、常数、uniform 排布都一样；光束强度的 CPU 那一份在 LumiereCore.kt 的 lumiereBeamMask，两边同一个公式）。
//
// 偏离：
//   1. AGSL（API 33+ 的 RuntimeShader）不支持 break / 变长循环，循环改成固定上界里加条件。
//   2. Folia 的图形组还套了一层 bloom（strength 1.5、threshold 0.42，pixi/bloomFilter.ts，多级降采样）。手机上不做多级模糊，
//      在着色器里给每束光多算一份加宽的柔边（半宽 ×5、软度 1）乘 0.22 叠上去，近似光柱芯的外发光（uBloom 可关）。
//   3. API 32 及以下没有 RuntimeShader：退回按光束几何画的线性渐变梯形（drawLumiereFallbackLight），没有烟雾与眩光的噪声。
//   4. 暗场底不在这里画（Folia 里 uDark 恒为 0，暗场由运行时铺在所有场景之下），所以去掉了 uDark 通路。
package com.guangyue.feature.player.lyrics.visualizer

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val LUMIERE_LIGHT_AGSL = """
uniform float4 uBeamA[6];
uniform float4 uBeamB[6];
uniform float4 uBeamC[6];
uniform float4 uBeamD[6];
uniform float4 uBeamE[6];
uniform float4 uBeamF[6];
uniform float4 uFrame;
uniform float4 uFogA;
uniform float4 uFogB;
uniform float3 uFogColor;
uniform float4 uGlare;
uniform float4 uGlareB;
uniform float3 uGlareColor;
uniform float4 uCaustic;
uniform float4 uCausticFloor;
uniform float4 uCausticB;
uniform float4 uWave;
uniform float4 uWaveB;
uniform float4 uShield;
uniform float4 uShieldB;
uniform float uBloom;

float hash11(float x) {
    float p = fract(x * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float valueNoise1(float x) {
    float i = floor(x);
    float f = x - i;
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), u);
}
float valueNoise2(float2 p) {
    float2 i = floor(p);
    float2 f = p - i;
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(float2 p, float octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    float2x2 rot = float2x2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 6; i++) {
        if (float(i) < octaves) {
            sum += amp * valueNoise2(p);
            norm += amp;
            p = rot * p * 2.03 + float2(17.1, 9.2);
            amp *= 0.5;
        }
    }
    return sum / max(norm, 1e-4);
}
float fogAt(float2 p, float t) {
    float2 drift = uFogB.xy * t;
    float2 q = (p - drift) * uFogA.z;
    float2 w = float2(fbm(q + float2(0.0, t * 0.07), 3.0), fbm(q + float2(5.2, 1.3) - float2(t * 0.05, 0.0), 3.0));
    float n = fbm(q + uFogA.w * (w - 0.5) * 2.0, uFogB.w);
    return smoothstep(0.28, 0.82, n);
}
float band(float x, float duty) {
    float f = fract(x);
    return min(1.0, 1.0 - smoothstep(duty - 0.06, duty + 0.06, f) + smoothstep(0.94, 1.0, f));
}
float goboPattern(float4 E, float u, float v) {
    float pattern = E.x;
    if (pattern < 1.5) return band(u * E.y * 0.5 + E.w, E.z);
    if (pattern < 2.5) return smoothstep(E.z * 0.5, E.z * 0.5 + 0.08, abs(u));
    if (pattern < 3.5) return band(u * E.y * 0.5 + v * E.y * 0.8 + E.w, E.z) * band(u * E.y * 0.5 - v * E.y * 0.8 - E.w, E.z);
    return smoothstep(E.z - 0.12, E.z + 0.12, valueNoise2(float2(u * E.y, v * E.y * 0.6 + E.w)));
}
float goboMask(float4 E, float u, float v) {
    if (E.x < 0.5) return 1.0;
    return 0.12 + 0.88 * goboPattern(E, u, v);
}
float2 beamMask(float4 A, float4 B, float4 C, float coreAmount, float4 E, float reach, float2 p) {
    float2 d = p - A.xy;
    float along = dot(d, A.zw);
    if (along <= 0.0) return float2(0.0);
    float signedPerp = d.x * -A.w + d.y * A.z;
    float half_ = abs(B.x + along * B.y) + 1e-4;
    float s = abs(signedPerp) / half_;
    float edge = smoothstep(1.0, 1.0 - max(B.z, 0.02), s);
    if (edge <= 0.0) return float2(0.0);
    float signedS = s * sign(signedPerp);
    float streak = 1.0 - C.y + C.y * valueNoise1(signedS * C.z + C.w);
    float core = 1.0 - coreAmount + coreAmount * (1.0 - s * s);
    float falloff = exp(-along / max(B.w, 0.001));
    float reachMask = reach > 0.0 ? 1.0 - smoothstep(reach - max(0.04, half_ * 2.5), reach, along) : 1.0;
    float start = smoothstep(0.0, abs(B.x) * 2.0 + 0.01, along);
    return float2(edge * streak * core * falloff * goboMask(E, signedS, along) * reachMask * start * C.x, signedS);
}
float glowMask(float4 A, float4 B, float4 C, float reach, float2 p) {
    float2 d = p - A.xy;
    float along = dot(d, A.zw);
    if (along <= 0.0) return 0.0;
    float perp = abs(d.x * -A.w + d.y * A.z);
    float half_ = abs(B.x + along * B.y) * 5.0 + 0.012;
    float s = perp / half_;
    float edge = smoothstep(1.0, 0.0, s);
    float falloff = exp(-along / max(B.w, 0.001));
    float reachMask = reach > 0.0 ? 1.0 - smoothstep(reach - 0.06, reach + 0.02, along) : 1.0;
    return edge * edge * falloff * reachMask * C.x;
}
float3 spectrumColor(float u) {
    float h = clamp(0.5 + 0.5 * u, 0.0, 1.0) * 0.8;
    float3 k = clamp(abs(fract(float3(h) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return mix(float3(1.0), k, 0.85);
}
float causticField(float2 p, float t) {
    float2 q = p * uCaustic.x * 6.2831853 - 250.0;
    float2 i = q;
    float c = 1.0;
    float inten = 0.005;
    for (int n = 0; n < 4; n++) {
        float tt = t * (1.0 - (3.5 / float(n + 1)));
        i = q + float2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        c += 1.0 / length(float2(q.x / (sin(i.x + tt) / inten), q.y / (cos(i.y + tt) / inten)));
    }
    c /= 4.0;
    c = 1.17 - pow(c, 1.4);
    return clamp(pow(abs(c), 8.0), 0.0, 3.0);
}
float wavePattern(float2 p, float t) {
    float2 d = p - uWaveB.xy;
    float radius = max(uWaveB.z, 1e-3);
    float r = length(d) / radius;
    float mask = smoothstep(1.0, 0.55, r);
    float mode = uWave.x;
    float f = uWave.y;
    float phase = t * uWave.z;
    float v;
    if (mode < 1.5) {
        v = 0.5 + 0.5 * cos(r * r * f * 6.0 - phase);
    } else if (mode < 2.5) {
        float x = d.x / radius;
        float y = d.y / radius;
        float xc = x * (1.0 + 0.35 * y * y);
        v = pow(cos(xc * f * 3.0 - phase * 0.3), 2.0) * exp(-xc * xc * 2.5);
        mask = smoothstep(1.0, 0.25, length(float2(x * 0.85, y * 1.5)));
    } else if (mode < 3.5) {
        float2 s1 = float2(-uWaveB.w * 0.5, 0.0);
        float d1 = length(d - s1);
        float d2 = length(d + s1);
        v = (0.5 + 0.5 * cos((d1 - d2) * f * 20.0)) * (0.5 + 0.5 * cos((d1 + d2) * f * 6.0 - phase));
    } else {
        float x = r * f * 4.0 + 1e-3;
        float sc = sin(x) / x;
        v = min(sc * sc * 4.0, 1.5);
    }
    float2 q = (p - uShield.xy) / max(uShield.zw, float2(1e-3));
    float shield = 1.0 - uShieldB.x * smoothstep(1.0, 0.35, length(q));
    return v * mask * shield * uWave.w;
}
half4 main(float2 fragCoord) {
    float H = uFrame.y;
    float t = uFrame.z;
    float2 p = fragCoord / H;
    float density = fogAt(p, t);
    float tyndall = uFogA.y + (1.0 - uFogA.y) * density * uFogA.x;
    float caustic = uCaustic.w > 0.5 ? causticField(p, t * uCaustic.y + 23.0) : 0.0;
    float causticLift = mix(1.0, 0.25 + 1.5 * caustic, uCaustic.w > 0.5 ? uCaustic.z : 0.0);
    float3 light = float3(0.0);
    float3 glow = float3(0.0);
    for (int i = 0; i < 6; i++) {
        if (float(i) < uFrame.w) {
            float4 D = uBeamD[i];
            float2 m = beamMask(uBeamA[i], uBeamB[i], uBeamC[i], D.w, uBeamE[i], uBeamF[i].y, p);
            float3 color = D.rgb;
            float spectrum = uBeamF[i].x;
            if (spectrum > 0.0) color = mix(color, spectrumColor(m.y) * max(max(color.r, color.g), color.b), spectrum);
            light += color * m.x;
            if (uBloom > 0.0) glow += D.rgb * glowMask(uBeamA[i], uBeamB[i], uBeamC[i], uBeamF[i].y, p);
        }
    }
    light *= tyndall * causticLift;
    light += glow * uBloom;
    if (uCaustic.w > 0.5 && uCausticB.x > 0.0) {
        float2 e = (p - uCausticFloor.xy) / max(uCausticFloor.zw, float2(1e-3));
        light += uFogColor * caustic * uCausticB.x * smoothstep(1.0, 0.6, length(e));
    }
    if (uWave.x > 0.5) light += uFogColor * wavePattern(p, t);
    light += uFogColor * density * uFogB.z;
    if (uGlare.w > 0.0) {
        float2 g = p - uGlare.xy;
        float r = max(uGlare.z, 1e-3);
        float dist = length(g);
        float glare = exp(-dist / r) * 0.55 + exp(-(dist * dist) / (r * r * 0.03));
        float streak = exp(-abs(g.y) / (r * 0.035)) * exp(-abs(g.x) / (r * 7.0)) * uGlareB.x;
        light += uGlareColor * (glare + streak) * uGlare.w;
    }
    float peak = max(max(light.r, light.g), light.b);
    float mapped = 1.0 - exp(-peak);
    float3 c = peak > 1e-5 ? light / peak * mapped : float3(0.0);
    c = mix(c, float3(mapped), 0.3 * mapped * mapped * mapped);
    c += (hash12(fragCoord * 2.0) - 0.5) / 255.0;
    c = clamp(c, 0.0, 1.0);
    float a = max(max(c.r, c.g), c.b);
    return half4(half3(c), half(a));
}
"""

/** 光场一帧的输入（LightFieldFrame）：width / height 为舞台 dp。 */
internal class LumiereFieldInput(
    val frame: LumiereLightFrame, val time: Double, val width: Double, val height: Double,
    val color: DoubleArray, val fogScale: Double, val octaves: Double, val textRegion: LumiereRegion, val bloom: Float,
)

/** createLightField 的 update：把光束、烟雾、眩光、焦散、干涉写进 uniform。 */
@RequiresApi(33)
internal fun RuntimeShader.setLumiereField(input: LumiereFieldInput) {
    val beams = input.frame.beams
    val n = LUMIERE_MAX_BEAMS * 4
    val a = FloatArray(n); val b = FloatArray(n); val c = FloatArray(n); val d = FloatArray(n); val e = FloatArray(n); val f = FloatArray(n)
    beams.take(LUMIERE_MAX_BEAMS).forEachIndexed { i, beam ->
        val o = i * 4
        a[o] = beam.ox.toFloat(); a[o + 1] = beam.oy.toFloat(); a[o + 2] = beam.dx.toFloat(); a[o + 3] = beam.dy.toFloat()
        b[o] = beam.halfWidth.toFloat(); b[o + 1] = beam.tanSpread.toFloat(); b[o + 2] = beam.softness.toFloat(); b[o + 3] = beam.length.toFloat()
        c[o] = beam.intensity.toFloat(); c[o + 1] = beam.streaks.toFloat(); c[o + 2] = beam.streakFreq.toFloat(); c[o + 3] = beam.streakPhase.toFloat()
        d[o] = beam.r.toFloat(); d[o + 1] = beam.g.toFloat(); d[o + 2] = beam.b.toFloat(); d[o + 3] = beam.core.toFloat()
        e[o] = beam.goboPattern.toFloat(); e[o + 1] = beam.goboFreq.toFloat(); e[o + 2] = beam.goboDuty.toFloat(); e[o + 3] = beam.goboPhase.toFloat()
        f[o] = beam.spectrum.toFloat(); f[o + 1] = beam.reach.toFloat()
    }
    setFloatUniform("uBeamA", a); setFloatUniform("uBeamB", b); setFloatUniform("uBeamC", c)
    setFloatUniform("uBeamD", d); setFloatUniform("uBeamE", e); setFloatUniform("uBeamF", f)
    setFloatUniform("uFrame", input.width.toFloat(), input.height.toFloat(), input.time.toFloat(), min(beams.size, LUMIERE_MAX_BEAMS).toFloat())
    val rig = input.frame.rig
    val fog = rig.fog
    setFloatUniform("uFogA", (fog.density * input.fogScale).toFloat(), fog.tyndallBase.toFloat(), fog.scale.toFloat(), fog.warp.toFloat())
    setFloatUniform("uFogB", fog.driftX.toFloat(), fog.driftY.toFloat(), (fog.ambient * input.fogScale).toFloat(), input.octaves.toFloat())
    val col = input.color
    setFloatUniform("uFogColor", col[0].toFloat(), col[1].toFloat(), col[2].toFloat())
    setFloatUniform("uGlareColor", col[0].toFloat(), col[1].toFloat(), col[2].toFloat())
    val aspect = input.width / input.height
    val glare = rig.glare
    setFloatUniform(
        "uGlare", ((glare?.x ?: 0.0) * aspect).toFloat(), (glare?.y ?: 0.0).toFloat(), (glare?.radius ?: 0.0).toFloat(),
        ((glare?.intensity ?: 0.0) * input.frame.glareScale).toFloat(),
    )
    setFloatUniform("uGlareB", (glare?.streak ?: 0.0).toFloat(), 0f, 0f, 0f)
    val caustic = rig.caustic
    val floor = caustic?.floor
    setFloatUniform("uCaustic", (caustic?.scale ?: 0.0).toFloat(), (caustic?.speed ?: 0.0).toFloat(), (caustic?.inBeam ?: 0.0).toFloat(), if (caustic != null) 1f else 0f)
    setFloatUniform("uCausticFloor", ((floor?.get(0) ?: 0.0) * aspect).toFloat(), (floor?.get(1) ?: 0.0).toFloat(), ((floor?.get(2) ?: 0.0) * aspect).toFloat(), (floor?.get(3) ?: 0.0).toFloat())
    setFloatUniform("uCausticB", ((floor?.get(4) ?: 0.0) * input.frame.glareScale).toFloat(), 0f, 0f, 0f)
    val wave = rig.wave
    setFloatUniform(
        "uWave", (wave?.let { LUMIERE_WAVE_ID[it.mode] } ?: 0).toFloat(), (wave?.frequency ?: 0.0).toFloat(), (wave?.speed ?: 0.0).toFloat(),
        ((wave?.strength ?: 0.0) * input.frame.glareScale).toFloat(),
    )
    setFloatUniform("uWaveB", ((wave?.cx ?: 0.0) * aspect).toFloat(), (wave?.cy ?: 0.0).toFloat(), (wave?.radius ?: 0.0).toFloat(), (wave?.separation ?: 0.0).toFloat())
    val r = input.textRegion
    // textRegion 是画面比例（领头光位的 region），与 Folia 一样在这里乘宽高比。
    setFloatUniform("uShield", (r.cx * aspect).toFloat(), r.cy.toFloat(), (r.w * aspect / 2).toFloat(), (r.h / 2).toFloat())
    setFloatUniform("uShieldB", if (wave != null) wave.shield.toFloat() else 0f, 0f, 0f, 0f)
    setFloatUniform("uBloom", input.bloom)
}

/** API 32 及以下：每束光画成从光源出发、沿方向渐隐的梯形（没有烟雾、眩光只画一个径向光晕）。坐标为舞台 dp。 */
internal fun DrawScope.drawLumiereFallbackLight(input: LumiereFieldInput) {
    val h = input.height.toFloat()
    for (beam in input.frame.beams) {
        val length = (if (beam.reach > 0) beam.reach else min(1.6, beam.length * 2.5)).toFloat()
        val ox = beam.ox.toFloat() * h; val oy = beam.oy.toFloat() * h
        val ex = ox + beam.dx.toFloat() * length * h; val ey = oy + beam.dy.toFloat() * length * h
        val nx = -beam.dy.toFloat(); val ny = beam.dx.toFloat()
        val w0 = (abs(beam.halfWidth) + 0.002).toFloat() * h
        val w1 = (abs(beam.halfWidth + length * beam.tanSpread) + 0.002).toFloat() * h
        val path = Path().apply {
            moveTo(ox + nx * w0, oy + ny * w0); lineTo(ex + nx * w1, ey + ny * w1)
            lineTo(ex - nx * w1, ey - ny * w1); lineTo(ox - nx * w0, oy - ny * w0); close()
        }
        val a = (1 - kotlin.math.exp(-beam.intensity)).toFloat().coerceIn(0f, 1f)
        val color = Color(beam.r.toFloat().coerceIn(0f, 1f), beam.g.toFloat().coerceIn(0f, 1f), beam.b.toFloat().coerceIn(0f, 1f))
        drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = a * 0.7f), color.copy(alpha = 0f)), Offset(ox, oy), Offset(ex, ey)))
    }
    input.frame.rig.glare?.let { g ->
        val c = Offset((g.x * input.width).toFloat(), (g.y * input.height).toFloat())
        val r = max(1f, (g.radius * 4 * input.height).toFloat())
        val col = Color(input.color[0].toFloat(), input.color[1].toFloat(), input.color[2].toFloat())
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = (g.intensity * input.frame.glareScale * 0.6).toFloat().coerceIn(0f, 1f)), Color.Transparent), c, r), r, c)
    }
}

internal fun lumiereSupportsShader() = Build.VERSION.SDK_INT >= 33
