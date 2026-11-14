// 町景（Folia machi）舞台起步：OpenGL ES 3 自写最小子集（GLSurfaceView），先接「城寨酒吧」白模。
//   · 镜头：MachiCamera.kt（camera.ts 默认构图 + 运镜，正交斜俯视）。运镜按歌曲时间走（氛围时钟未接，暂停时也停）。
//   · 光照：skyLights.ts 夜里的半球光（hemiSky / hemiGround × 2.1）+ 月光（方向 (-6.5, 14, -5)、× 1.35），
//     与 three 一样按线性空间 Lambert（÷π）算；亮窗 / 屏面 / 酒吧后墙自发光 × artificial。天空 = palette.skyTop → skyBottom 竖向渐变。
//   · 歌词大屏（逐字点亮 + 换行信号切换）、屏下 LED 跑马灯、门头霓虹店招在 GL 里画（MachiScreen.kt，对译 lyricScreen.ts / ledStrip.ts）。
//   · toon：直射月光走 4 阶渐变（materials.ts createToonGradient）；场景画进线性 HDR 目标，后处理（MachiPost.kt）
//     做墨线 / 贴地薄雾 / 云 / bloom，最后色调映射上屏（同 three：渲染到目标时材质不做色调映射，OutputPass 做）。
//   · 湿路面倒影与光柱：MachiWet.kt（reflection.ts / ground.ts / lightCones.ts）；雨丝 / 水花 / 滴水：MachiWeather.kt（weather.ts，雪未做）。
// 还没做：阴影、景深散景、另两个场景（便利店 / 车站）。
// 取证页（V0cStageHarness）与歌词样式「町景」（LyricFlowScreen）共用 MachiStagePreview。
package com.guangyue.feature.player.lyrics.visualizer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
// 会动的件（便利店自动门）整体平移；其余为 0。
uniform vec3 uOffset;
// 会动的件（风扇、过路车，MachiActors.kt）的模型矩阵；静态网格为单位阵。
uniform mat4 uModel;
out vec3 vCol;
out vec3 vNor;
out float vEmi;
out float vY;
out vec3 vWorld;
void main() {
    vec3 p = (uModel * vec4(aPos, 1.0)).xyz + uOffset;
    gl_Position = uVP * vec4(p, 1.0);
    vCol = aCol; vNor = mat3(uModel) * aNor; vEmi = aEmi; vY = p.y; vWorld = p;
}
"""

private val MACHI_WET_PARS_K = MACHI_WET_PARS.replace("+ uPuddleBias", "+ machiBias")

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
// 亮窗（kowloonScene：litMaterial = (0.28 + 0.6 smoothNight(daylight)) × warm，不乘 artificial）。网格里亮窗的自发光烘成 MACHI_WINDOW_LEVEL（0.88），按它认出来。
uniform float uWindowLevel;
float emission() { return abs(vEmi - 0.88) < 0.0005 ? uWindowLevel : uArtificial * vEmi; }
// 积雪（snow.ts）：朝上的面按 积雪量 × 朝上程度 × 噪声 混成雪色（光照之前改底色）；屋顶下面不积。
uniform float uMachiSnow;
uniform vec3 uMachiSnowColor;
uniform vec4 uMachiRoofs[4];
uniform float uMachiRoofY[4];
// 透明玻璃（materials.ts glass：opacity 0.2，不写深度）；不透明件为 1。
uniform float uAlpha;
// 第二层湿地面（便利店：路面 asphalt 在 y≈0、场地 concrete 在 y≈0.12，puddleBias 各自）。
uniform float uGroundY2;
uniform float uPuddleBias2;
// 场景自己的点光 / 聚光（便利店 konbiniLights.ts：decay 2、cutoff 距离；聚光 smoothstep(coneCos, penumbraCos)）；城寨 0 盏。
uniform int uLightCount;
uniform vec3 uLightPos[6];
uniform vec3 uLightColor[6];
uniform float uLightDist[6];
uniform vec3 uSpotDir[6];
uniform vec2 uSpotCos[6];
// 地上的加色光斑（自发光通道当标签：1 溢出光、2 路灯、3 贩卖机）。
uniform int uAdd;
uniform vec3 uPoolScale;
// uAdd = 2：软影 / 车灯光斑（顶点色是线性原值 × uTint，混合方式由渲染器定）。
uniform vec3 uTint;
// 过路车按底座边缘裁掉（traffic.ts clipToBase：只留 |x|、|z| ≤ half）；0 不裁。
uniform float uClipHalf;
float machiBias;
out vec4 fragColor;
$MACHI_WET_PARS_K
float machiSnowHash(vec2 p) { return fract(sin(dot(p, vec2(41.7, 289.3))) * 43758.5453); }
float machiSnowNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(machiSnowHash(i), machiSnowHash(i + vec2(1.0, 0.0)), u.x), mix(machiSnowHash(i + vec2(0.0, 1.0)), machiSnowHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float machiSheltered(vec3 p) {
    for (int i = 0; i < 4; i++) {
        vec4 r = uMachiRoofs[i];
        if (p.x > r.x && p.x < r.z && p.z > r.y && p.z < r.w && p.y < uMachiRoofY[i] - 0.05) return 1.0;
    }
    return 0.0;
}
vec3 toLinear(vec3 c) { return mix(c * 0.0773993808, pow(c * 0.9478672986 + 0.0521327014, vec3(2.4)), step(0.04045, c)); }
// MeshToonMaterial：直射光走 4 阶渐变图（86/146/206/255，最近邻），半球光照常；线性输出（色调映射在后处理最后一道）。
float toonStep(float dotNL) {
    float coord = clamp(dotNL * 0.5 + 0.5, 0.0, 0.9999);
    int i = int(floor(coord * 4.0));
    return i == 0 ? 86.0 / 255.0 : (i == 1 ? 146.0 / 255.0 : (i == 2 ? 206.0 / 255.0 : 1.0));
}
void main() {
    if (uClipHalf > 0.0 && (abs(vWorld.x) > uClipHalf || abs(vWorld.z) > uClipHalf)) discard;
    if (uAdd == 2) { fragColor = vec4(vCol * uTint, 1.0); return; }
    vec3 n = normalize(vNor);
    vec3 albedo = toLinear(vCol);
    if (uAdd == 1) {
        float k = vEmi < 1.5 ? uPoolScale.x : (vEmi < 2.5 ? uPoolScale.y : uPoolScale.z);
        fragColor = vec4(albedo * k, 1.0);
        return;
    }
    // 倒影 pass（MachiWet.kt）：只有亮的东西映得出，其余写黑当遮挡；贴地的面（含路面本身）丢掉。
    if (uReflectPass == 1) {
        if (vY < uGroundY + 0.01) discard;
        fragColor = vec4(albedo * emission(), uAlpha);
        return;
    }
    if (uMachiSnow > 0.001 && vEmi < 0.001) {
        float machiNoise = machiSnowNoise(vWorld.xz * 2.6) * 0.65 + machiSnowNoise(vWorld.xz * 7.1) * 0.35;
        float machiCover = smoothstep(0.5, 0.85, n.y) * smoothstep(1.0 - uMachiSnow * 0.9, 1.16 - uMachiSnow * 0.9, machiNoise + 0.25);
        machiCover *= 1.0 - machiSheltered(vWorld);
        albedo = mix(albedo, uMachiSnowColor, machiCover * min(1.0, uMachiSnow * 1.4));
    }
    float w = 0.5 * n.y + 0.5;
    vec3 directLight = uSun * toonStep(dot(n, uSunDir));
    vec3 irradiance = mix(uHemiGround, uHemiSky, w) + directLight;
    for (int i = 0; i < 6; i++) {
        if (i >= uLightCount) break;
        vec3 toL = uLightPos[i] - vWorld;
        float d = length(toL);
        vec3 l = toL / max(d, 1e-4);
        float fall = 1.0 / max(d * d, 0.01);
        float cut = clamp(1.0 - pow(d / uLightDist[i], 4.0), 0.0, 1.0);
        fall *= cut * cut;
        if (uSpotCos[i].x > -1.5) fall *= smoothstep(uSpotCos[i].x, uSpotCos[i].y, dot(-l, uSpotDir[i]));
        irradiance += uLightColor[i] * fall * toonStep(dot(n, l));
    }
    vec3 lit = albedo * irradiance / 3.14159265;
    // 湿路面（ground.ts OUTPUT）：只在贴地朝上的路面上。
    bool machiOnB = abs(vY - uGroundY2) < 0.012;
    machiBias = machiOnB ? uPuddleBias2 : uPuddleBias;
    if (uWetOn == 1 && uAlpha > 0.99 && n.y > 0.9 && (abs(vY - uGroundY) < 0.012 || machiOnB) && vEmi < 0.01) {
        float direct = dot(albedo * directLight / 3.14159265, vec3(0.299, 0.587, 0.114));
        vec2 screenUv = gl_FragCoord.xy / uSceneSize;
        $MACHI_WET_OUTPUT
    }
    // 自发光（materials.ts glow：MeshBasicMaterial，颜色 × spec.level × setGlowLevel）：aEmi 就是 level，不再与受光色混合。
    fragColor = vec4(vEmi > 0.001 ? albedo * emission() : lit, uAlpha);
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

/**
 * 光照参数（skyLights.ts + environment.ts + createMachiRuntime.applyEnvironmentColors）。
 * [daylight]：0 夜、0.45 黄昏、1 白天（timeOfDay auto 时按本地时钟，见 resolveMachiDaylight）。
 * [palette] 是按环境推过的调色板：skyTop / skyBottom / hemiSky / hemiGround / moon 在夜、黄昏、白天三套之间插值，
 * 阴天往灰里拉，积雪时地面反光更冷更亮（照 applyEnvironmentColors，线性空间里插值后写回 hex）。
 */
internal class MachiNightLight(
    basePalette: MachiPalette,
    val weather: MachiWeatherKind = MachiWeatherKind.CLEAR,
    daylight: Double = MACHI_DAYLIGHT_NIGHT,
) {
    val state = createMachiEnvironmentState(resolveMachiEnvironmentTarget(daylight, weather))
    val look = resolveMachiEnvironmentLook(state)
    val palette: MachiPalette = machiEnvironmentPalette(basePalette, state, look)
    /** skyLights.update：月光 / 太阳方向在夜、黄昏、白天之间插值。 */
    val sunDir: FloatArray = run {
        val night = machiNorm(-6.5, 14.0, -5.0); val dusk = machiNorm(-11.0, 5.5, 6.0); val day = machiNorm(-6.0, 16.0, 8.0)
        val d = state.daylight
        val (a, b, t) = if (d <= MACHI_DAYLIGHT_DUSK) Triple(night, dusk, d / MACHI_DAYLIGHT_DUSK) else Triple(dusk, day, (d - MACHI_DAYLIGHT_DUSK) / (1 - MACHI_DAYLIGHT_DUSK))
        val v = DoubleArray(3) { a[it] + (b[it] - a[it]) * t }
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        FloatArray(3) { (v[it] / l).toFloat() }
    }
    private fun lin(hex: String, k: Double): FloatArray = machiLinear(hex, k)
    val sun = lin(palette.moon, look.sunIntensity)
    val hemiSky = lin(palette.hemiSky, look.hemiIntensity)
    // 积雪的地面反光已在 palette.hemiGround 里（applyEnvironmentColors）。
    val hemiGround = lin(palette.hemiGround, look.hemiIntensity)
    /** 亮窗亮度（kowloonScene.ts：(0.28 + 0.6 smoothNight) × beat.warm；鼓点未接，warm = 1）。夜里 0.88。 */
    val windowLevel: Double = run {
        val t = ((0.65 - state.daylight) / 0.45).coerceIn(0.0, 1.0)
        0.28 + 0.6 * t * t * (3 - 2 * t)
    }
    /** 薄雾色 = skyBottom × (1.35 − 0.4 daylight)。 */
    val mistColor: FloatArray = machiLinear(palette.skyBottom, 1.35 - 0.4 * state.daylight)
    /** 云：夜里 lerp(skyBottom, moon, 0.25) × 0.7，白天 lerp(skyBottom, 白, 0.55 − 0.25 overcast) × 0.55，按 daylight 混。 */
    val cloudColor: FloatArray = run {
        val sb = machiLinear(palette.skyBottom); val mn = machiLinear(palette.moon)
        val w = 0.55 - 0.25 * look.overcast
        FloatArray(3) {
            val n = (sb[it] + (mn[it] - sb[it]) * 0.25) * 0.7
            val dd = (sb[it] + (1 - sb[it]) * w) * 0.55
            (n + (dd - n) * state.daylight).toFloat()
        }
    }
}

private fun machiNorm(x: Double, y: Double, z: Double): DoubleArray { val l = sqrt(x * x + y * y + z * z); return doubleArrayOf(x / l, y / l, z / l) }

private fun machiLinearToHex(c: DoubleArray): String {
    fun enc(v: Double): Int {
        val x = v.coerceIn(0.0, 1.0)
        val s = if (x <= 0.0031308) x * 12.92 else 1.055 * Math.pow(x, 1 / 2.4) - 0.055
        return kotlin.math.round(s * 255).toInt().coerceIn(0, 255)
    }
    return String.format("#%02x%02x%02x", enc(c[0]), enc(c[1]), enc(c[2]))
}

/** applyEnvironmentColors 里单个键的颜色（线性 RGB）：三套插值 → 阴天去饱和 → hemiGround 积雪偏冷。 */
internal fun machiEnvironmentColor(key: String, night: String, dusk: String, day: String, daylight: Double, overcast: Double, snowCover: Double): DoubleArray {
    val d0 = MACHI_DAYLIGHT_DUSK
    val (a, b, t) = if (daylight <= d0) Triple(night, dusk, daylight / d0) else Triple(dusk, day, (daylight - d0) / (1 - d0))
    val ca = machiLinear(a); val cb = machiLinear(b)
    val c = DoubleArray(3) { ca[it] + (cb[it] - ca[it]) * t }
    val lum = c[0] * 0.2126 + c[1] * 0.7152 + c[2] * 0.0722
    val g = lum * (if (key == "moon") 1.0 else 0.9 + 0.35 * daylight)
    val k = overcast * (if (key == "moon") 0.45 else 0.7)
    for (i in 0..2) c[i] += (g - c[i]) * k
    if (key == "hemiGround") {
        val cold = doubleArrayOf(0.55, 0.6, 0.7)
        for (i in 0..2) c[i] += (cold[i] * (0.35 + 0.65 * daylight) - c[i]) * snowCover * 0.5
    }
    return c
}

/** createMachiRuntime.applyEnvironmentColors 照译（three Color 是线性的，lerp 在线性空间），结果写回 hex。 */
internal fun machiEnvironmentPalette(base: MachiPalette, state: MachiEnvironmentState, look: MachiEnvironmentLook): MachiPalette {
    fun derive(key: String, night: String, dusk: String, day: String) =
        machiLinearToHex(machiEnvironmentColor(key, night, dusk, day, state.daylight, look.overcast, state.snowCover))
    return base.copy(
        skyTop = derive("skyTop", base.skyTop, base.skyTopDusk, base.skyTopDay),
        skyBottom = derive("skyBottom", base.skyBottom, base.skyBottomDusk, base.skyBottomDay),
        hemiSky = derive("hemiSky", base.hemiSky, base.hemiSkyDusk, base.hemiSkyDay),
        hemiGround = derive("hemiGround", base.hemiGround, base.hemiGroundDusk, base.hemiGroundDay),
        moon = derive("moon", base.moon, base.moonDusk, base.moonDay),
    )
}

/**
 * 一个场景交给渲染器的东西（scenes/types.ts MachiScene 的子集）：网格、玻璃、构图、招牌（可空）、光柱、天气布置、
 * 雾里两处灯的位置、湿地面的高度与 puddleBias、倒影平面。[kind] 区分各场景自己的灯光调度（kowloon / konbini）。
 */
internal class MachiSceneSetup(
    val kind: String,
    val mesh: FloatArray,
    val glass: FloatArray?,
    val framing: List<MachiVec3>,
    val view: MachiView,
    val signs: MachiSigns?,
    val cones: List<MachiLightConeSpec>,
    val puddles: MachiPuddles,
    val weather: MachiWeatherLayout,
    val glowA: FloatArray,
    val glowB: FloatArray,
    val mirrorY: Double,
    val wetY: Double,
    val wetBias: Double,
    val wetY2: Double = -100.0,
    val wetBias2: Double = 0.0,
    val lights: List<MachiKonbiniLight> = emptyList(),
    /** 加色光斑网格（便利店）。 */
    val pools: FloatArray? = null,
    /** 自动门两扇（不透明、玻璃）与开合节奏（konbiniSeed doorInterval / doorPhase）。 */
    val doors: List<FloatArray> = emptyList(),
    val doorGlass: List<FloatArray> = emptyList(),
    val doorInterval: Double = 30.0,
    val doorPhase: Double = 0.0,
    /** 会动的件（风扇、过路车）。 */
    val actors: List<MachiActor> = emptyList(),
    /** のぼり旗布面、白气（MachiActors.kt）。 */
    val nobori: MachiNobori? = null,
    val steam: MachiSteam? = null,
    val windYaw: Double = 0.0,
    /** 贴图牌面（便利店玻璃海报、窗内霓虹、屋顶 logo、A 字立牌，MachiKonbiniDecor.kt）。 */
    val decals: MachiDecals? = null,
)

internal fun machiKowloonSetup(palette: MachiPalette, seed: String, title: String, artist: String): MachiSceneSetup {
    val model = buildMachiKowloon(seed)
    val w = resolveMachiKowloonWeather(seed, model.drips)
    return MachiSceneSetup(
        kind = "kowloon", mesh = model.mesh, glass = null, framing = machiKowloonFramingPoints(), view = MachiKowloonLayout.VIEW,
        signs = MachiKowloonSigns(palette, title, artist, seed, model.signs), cones = machiKowloonLightCones(), puddles = w.first, weather = w.second,
        glowA = floatArrayOf(-2.0f, 0f, 1.0f, 3.4f), glowB = floatArrayOf(MachiKowloonLayout.SCREEN_X.toFloat(), 0f, (-1.5 + 0.8).toFloat(), 3.0f),
        mirrorY = MACHI_KOWLOON_GROUND_Y, wetY = MACHI_KOWLOON_GROUND_Y, wetBias = 0.08,
    )
}

/** 便利店：路面 asphalt（y = 0，bias 0）与场地 concrete（y = 0.12，bias 0.08）都是湿地面；倒影平面 0.06。 */
internal fun machiKonbiniSetup(palette: MachiPalette, seed: String, title: String, artist: String): MachiSceneSetup {
    val model = buildMachiKonbini(palette, seed)
    val w = resolveMachiKonbiniWeather(seed)
    val glows = machiKonbiniGlows()
    return MachiSceneSetup(
        kind = "konbini", mesh = model.mesh, glass = model.glass, framing = machiKonbiniFramingPoints(), view = MachiKonbiniLayout.VIEW,
        signs = MachiKonbiniSigns(palette, title, artist, seed, model.boardFaces, model.fasciaFaces, model.propFaces?.pylon.orEmpty()), cones = machiKonbiniLightCones(), puddles = w.first, weather = w.second, glowA = glows.first, glowB = glows.second,
        mirrorY = MachiKonbiniLayout.REFLECT_Y, wetY = MachiKonbiniLayout.ROAD_Y + 0.002, wetBias = 0.0,
        wetY2 = MachiKonbiniLayout.LOT_Y + 0.002, wetBias2 = 0.08,
        lights = machiKonbiniLights(), pools = buildMachiKonbiniPools(palette),
        doors = model.doors, doorGlass = model.doorGlass,
        doorInterval = resolveMachiKonbiniVariation(seed).doorInterval, doorPhase = resolveMachiKonbiniVariation(seed).doorPhase,
        actors = listOf(
            MachiFans(model.propFaces?.fans.orEmpty(), resolveMachiKonbiniVariation(seed).fanSpeed),
            MachiTraffic(MACHI_KONBINI_LANES, MachiKonbiniLayout.ROAD_Y, palette, MachiKonbiniLayout.BASE_HALF),
        ),
        // konbiniScene setContent：曲名（无则专辑 / 町景）、主题名或 新発売（带图标）、おでん。
        nobori = MachiNobori(MACHI_KONBINI_NOBORI, listOf(
            MachiNoboriFace(palette.brand[0], "#ffffff", title.ifEmpty { "町景" }),
            // 图标 = 主题图标 icons[0]；主题没给图标时没有（兜底图标只给招牌用）。
            MachiNoboriFace(palette.brand[2], "#ffffff", "新発売"),
            MachiNoboriFace(palette.brand[1], palette.signText, "おでん"),
        )),
        steam = MachiSteam(MACHI_KONBINI_STEAM), windYaw = w.second.windYaw,
        decals = machiKonbiniDecals(palette, title, artist, model.propFaces?.aFrame?.firstOrNull()),
    )
}

/** 车站：路面 asphalt（y = 0，bias 0）与加油区 concrete（y = 0.1，bias 0.08）是湿地面；倒影平面 0.04。招牌、灯后接。 */
internal fun machiStationSetup(palette: MachiPalette, seed: String, title: String, artist: String, lines: List<Line>): MachiSceneSetup {
    val model = buildMachiStation(palette, seed)
    val t = title.ifEmpty { "MACHI" }
    val total = lines.lastOrNull()?.endTime ?: 0.0
    // stationSigns.ts：货车屏（逐字点亮）、出租车顶灯（第一辆滚下一句，其余滚曲名 + 空車）、罩棚前右（歌手大写，0.82）、
    // 价格立柱两面（播放进度 / 总时长 / 第几句，0.92）、旅馆竖灯箱 MOTEL（1.15）、名字横牌（曲名，1.1）、VACANCY（副歌时 NO 也亮，1.1）。
    val leds = model.andons.mapIndexed { i, a ->
        MachiLedSpec(doubleArrayOf(a[0], a[1], a[2], 0.0, a[3], a[4]), 10f) { target, ls ->
            if (i == 0) resolveMachiTickerText(target, ls, title, artist, "MACHI")
            else "♪ $t${if (artist.isNotEmpty()) " — $artist" else ""}   ◆   空車   ◆   "
        }
    }
    val mf = model.motelFaces
    val statics = listOf(
        MachiStaticSpec(model.canopySigns, 1024, 0.82, { b, _, _ -> paintStationCanopy(b, artist.ifEmpty { t }.uppercase(), palette.signBase, palette.signText, palette.brand) }),
        MachiStaticSpec(model.pylonFaces, 360, 0.92, { b, time, li ->
            paintStationPylon(b, palette.brand, listOf("レギュラー" to formatMachiClock(time), "ハイオク" to formatMachiClock(total), "軽油" to (if (li >= 0) (li + 1).toString().padStart(3, ' ') else "---")))
        }, { time, li -> "${formatMachiClock(time)}|$li" }),
        MachiStaticSpec(listOf(mf[0]), 256, 1.15, { b, _, _ -> paintMotelTall(b, palette.neon) }),
        MachiStaticSpec(listOf(mf[1]), 640, 1.1, { b, _, _ -> paintMotelName(b, t, palette.brand[2]) }),
        MachiStaticSpec(listOf(mf[2]), 384, 1.1, { b, _, li -> paintMotelVacancy(b, isMachiChorusLine(lines.getOrNull(li))) }, { _, li -> isMachiChorusLine(lines.getOrNull(li)).toString() }),
    )
    val S = MachiStationLayout
    val signs = MachiPanelSigns(palette, title, artist, "MACHI", S.TS_W, S.TS_H, listOf(doubleArrayOf(S.TS_X, S.TS_Y, S.TS_Z, 0.0)), leds, statics)
    val w = resolveMachiStationWeather(seed)
    val glows = machiStationGlows()
    return MachiSceneSetup(
        kind = "station", mesh = model.mesh, glass = null, framing = machiStationFramingPoints(), view = MachiStationLayout.VIEW,
        signs = signs, cones = machiStationLightCones(), puddles = w.first, weather = w.second, glowA = glows.first, glowB = glows.second,
        mirrorY = MachiStationLayout.REFLECT_Y, wetY = MachiStationLayout.ROAD_Y + 0.002, wetBias = 0.0,
        wetY2 = MachiStationLayout.APRON_Y + 0.002, wetBias2 = 0.08,
        lights = machiStationLights(palette), pools = buildMachiStationPools(palette),
        actors = listOf(
            MachiTraffic(MACHI_STATION_LANES, MachiStationLayout.ROAD_Y, palette, 6.0),
            MachiStationActors(model.shadows, model.hazardOn, model.hazardOff, model.bulbs),
        ),
        nobori = MachiNobori(MACHI_STATION_NOBORI, listOf(
            MachiNoboriFace(palette.brand[0], "#ffffff", "セルフ"),
            MachiNoboriFace(palette.brand[2], "#ffffff", "洗車"),
            MachiNoboriFace(palette.brand[1], palette.signText, "灯油"),
        )),
        steam = MachiSteam(MACHI_STATION_STEAM), windYaw = w.second.windYaw,
    )
}

private class MachiRenderer(
    private val setup: MachiSceneSetup,
    private val light: MachiNightLight,
    private val rig: MachiCameraRig,
    private val timeSeconds: () -> Double,
    private val lines: List<Line>,
    private val seed: String,
    /** 取证用：镜头停在静止位（Folia 探针 renderAt 刚挂上时运镜幅度为 0）。 */
    private val restCamera: Boolean = false,
    /** 播放暂停时氛围时间停住（clock.ts：雨、涟漪、滴水、闪烁、运镜都定格）。 */
    private val paused: () -> Boolean = { false },
) : GLSurfaceView.Renderer {
    private val mesh = setup.mesh
    private val signs = setup.signs
    private val puddles = setup.puddles
    private val weatherLayout = setup.weather
    private val konbini = setup.kind == "konbini"
    private val station = setup.kind == "station"
    private val canopyColor = machiLinear(MachiStationLayout.ART.getValue("canopyLight"))
    private val clock = MachiClock()
    private val weather = MachiWeatherFx(weatherLayout)
    private val snowCoverColor = machiLinear("#f2f5fa")
    private val roofRects = FloatArray(16).also { a -> weatherLayout.roofs.take(4).forEachIndexed { i, r -> a[i * 4] = r.x0.toFloat(); a[i * 4 + 1] = r.z0.toFloat(); a[i * 4 + 2] = r.x1.toFloat(); a[i * 4 + 3] = r.z1.toFloat() } }
    private val roofYs = FloatArray(4) { i -> weatherLayout.roofs.getOrNull(i)?.y?.toFloat() ?: -100f }
    private val rainColor: FloatArray
    private val rainGlintColor: FloatArray
    private val dripColor: FloatArray
    private val snowColor: FloatArray
    private val snowGlintColor: FloatArray
    init {
        // weather.ts syncColors（线性空间里 lerp）
        val sky = machiLinear(light.palette.skyBottom); val pale = machiLinear("#dfe9ff")
        val street = machiLinear(light.palette.streetLight); val interior = machiLinear(light.palette.interiorLight)
        rainColor = FloatArray(3) { sky[it] + (pale[it] - sky[it]) * 0.72f }
        rainGlintColor = FloatArray(3) { street[it] + (interior[it] - street[it]) * 0.4f }
        dripColor = FloatArray(3) { rainColor[it] + (interior[it] - rainColor[it]) * 0.25f }
        val flake = machiLinear("#f4f7fc"); val hemi = machiLinear(light.palette.hemiSky)
        snowColor = FloatArray(3) { flake[it] + (hemi[it] - flake[it]) * 0.18f }
        snowGlintColor = street
    }
    private val reflection = MachiReflection()
    private val cones = setup.cones.takeIf { it.isNotEmpty() }?.let { MachiLightCones(machiConeMesh(it)) }
    private val lampColor = machiLinear("#e2f2ff")
    // 闪电（director.ts）：副歌开头两下闪；雨量 ≥ 0.5 才有，亮度 × 雨量；半球光 × (1 + 1.5 flash)、月光 × (1 + 1.2 flash)（skyLights.ts）。
    private val lightningTimes = buildMachiLightningTimes(lines)
    private var flash = 0.0
    private var ambient = 0.0
    private val post = MachiPostChain()
    private val invVP = FloatArray(16)
    private val glowColorA = machiLinear("#ff4fb0")
    private val glowColorB = machiLinear(mcMixHex("#ff9fd0", light.palette.interiorLight, 0.45))
    private val glowColorKA = machiLinear(light.palette.interiorLight)
    private val glowColorKB = machiLinear(light.palette.streetLight)
    private var program = 0
    private var skyProgram = 0
    private var vao = 0
    private var skyVao = 0
    private var glassVao = 0
    private var poolVao = 0
    private var poolCount = 0
    private val lightPos = FloatArray(18)
    private val lightColor = FloatArray(18)
    private val lightDist = FloatArray(6)
    private val spotDir = FloatArray(18)
    private val spotCos = FloatArray(12)
    private val spillColor = machiLinear(mcMixHex(MachiKonbiniLayout.ART.getValue("spillLight"), light.palette.interiorLight, 0.5))
    private val vendingColor = machiLinear(light.palette.vendingLight)
    private var interiorLevel = 1.0
    private val doorEvents = if (setup.doors.isEmpty()) emptyList() else buildMachiKonbiniDoorEvents(lines, setup.doorInterval, setup.doorPhase)
    private var doorOpenness = 0.0
    private val doorVaos = IntArray(2)
    private val doorCounts = IntArray(2)
    private val doorGlassVaos = IntArray(2)
    private val doorGlassCounts = IntArray(2)
    private var offset = floatArrayOf(0f, 0f, 0f)
    private fun vaoOf(data: FloatArray): Int {
        val v = IntArray(1); GLES30.glGenVertexArrays(1, v, 0)
        val b = IntArray(1); GLES30.glGenBuffers(1, b, 0)
        GLES30.glBindVertexArray(v[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 40
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 36)
        return v[0]
    }
    private var streetLevel = 1.0
    init {
        setup.lights.take(6).forEachIndexed { i, l ->
            for (k in 0..2) lightPos[i * 3 + k] = l.pos[k].toFloat()
            lightDist[i] = l.distance.toFloat()
            val t = l.target
            if (t != null) {
                val d = DoubleArray(3) { t[it] - l.pos[it] }; val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                for (k in 0..2) spotDir[i * 3 + k] = (d[k] / len).toFloat()
                spotCos[i * 2] = kotlin.math.cos(l.angle).toFloat(); spotCos[i * 2 + 1] = kotlin.math.cos(l.angle * (1 - l.penumbra)).toFloat()
            } else { spotCos[i * 2] = -2f; spotCos[i * 2 + 1] = -1f }
        }
    }
    private val lightBase = setup.lights.map { l -> l.color?.let { machiLinear(it) } }
    // 车站：罩棚 / 旅馆霓虹 / 货车屏的亮度（stationScene direct）。
    private var canopyLevel = 1.0
    private var neonLevel = 1.0
    private var screenLevel = 1.0
    /** konbiniLights / stationLights.sync：店内与溢出 × interior、路灯 × street、罩棚 × canopy、霓虹 × neon、屏 × screen，贩卖机常亮。 */
    private fun updateLights() {
        setup.lights.take(6).forEachIndexed { i, l ->
            val (c0, k) = when (l.kind) {
                "interior" -> glowColorKA to interiorLevel
                "spill" -> spillColor to interiorLevel
                "street" -> glowColorKB to streetLevel
                "canopy" -> glowColorKA to canopyLevel
                "neon" -> glowColorKA to neonLevel
                "screen" -> glowColorKA to screenLevel
                else -> vendingColor to 1.0
            }
            val c = lightBase[i] ?: c0
            for (j in 0..2) lightColor[i * 3 + j] = (c[j] * l.intensity * k).toFloat()
        }
    }
    private var count = 0
    private var glassCount = 0
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
        for (k in 0 until kotlin.math.min(2, setup.doors.size)) {
            doorVaos[k] = vaoOf(setup.doors[k]); doorCounts[k] = setup.doors[k].size / 10
            setup.doorGlass.getOrNull(k)?.takeIf { it.isNotEmpty() }?.let { doorGlassVaos[k] = vaoOf(it); doorGlassCounts[k] = it.size / 10 }
        }
        setup.pools?.takeIf { it.isNotEmpty() }?.let { pools ->
            val pv = IntArray(1); GLES30.glGenVertexArrays(1, pv, 0); poolVao = pv[0]
            val pb = IntArray(1); GLES30.glGenBuffers(1, pb, 0)
            GLES30.glBindVertexArray(poolVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, pb[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, pools.size * 4, floatBuffer(pools), GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
            GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 36)
            poolCount = pools.size / 10
        }
        setup.glass?.takeIf { it.isNotEmpty() }?.let { glass ->
            val gv = IntArray(1); GLES30.glGenVertexArrays(1, gv, 0); glassVao = gv[0]
            val gb = IntArray(1); GLES30.glGenBuffers(1, gb, 0)
            GLES30.glBindVertexArray(glassVao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gb[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, glass.size * 4, floatBuffer(glass), GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
            GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 36)
            glassCount = glass.size / 10
        }
        GLES30.glBindVertexArray(skyVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[1])
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, floatBuffer(quad), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        setup.actors.forEach { a -> a.parts.forEach { p -> if (p.mesh.isNotEmpty()) { p.vao = vaoOf(p.mesh); p.count = p.mesh.size / 10 } } }
        setup.nobori?.init(::link)
        setup.steam?.init(::link)
        setup.decals?.init(::link)
        signs?.init(::link)
        cones?.init(::link)
        weather.init(::link)
        post.init(::link)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectW = width.toDouble(); aspectH = height.toDouble()
        post.resize(width, height)
        reflection.resize(width, height)
    }

    private fun drawMesh(vp: FloatArray, reflectPass: Boolean, wet: Boolean, glass: Boolean = false, pools: Boolean = false) {
        GLES30.glUseProgram(program)
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(u("uVP"), 1, false, vp, 0)
        GLES30.glUniform3f(u("uOffset"), 0f, 0f, 0f)
        GLES30.glUniformMatrix4fv(u("uModel"), 1, false, identity, 0)
        GLES30.glUniform1f(u("uClipHalf"), 0f)
        GLES30.glUniform3fv(u("uSunDir"), 1, light.sunDir, 0)
        val sunK = (1 + 1.2 * flash).toFloat(); val hemiK = (1 + 1.5 * flash).toFloat()
        GLES30.glUniform3fv(u("uSun"), 1, FloatArray(3) { light.sun[it] * sunK }, 0)
        GLES30.glUniform3fv(u("uHemiSky"), 1, FloatArray(3) { light.hemiSky[it] * hemiK }, 0)
        GLES30.glUniform3fv(u("uHemiGround"), 1, FloatArray(3) { light.hemiGround[it] * hemiK }, 0)
        GLES30.glUniform1f(u("uArtificial"), light.look.artificial.toFloat())
        GLES30.glUniform1f(u("uWindowLevel"), light.windowLevel.toFloat())
        GLES30.glUniform1f(u("uMachiSnow"), light.state.snowCover.toFloat())
        GLES30.glUniform3fv(u("uMachiSnowColor"), 1, snowCoverColor, 0)
        GLES30.glUniform4fv(u("uMachiRoofs"), 4, roofRects, 0)
        GLES30.glUniform1fv(u("uMachiRoofY"), 4, roofYs, 0)
        GLES30.glUniform1i(u("uReflectPass"), if (reflectPass) 1 else 0)
        GLES30.glUniform1i(u("uWetOn"), if (wet) 1 else 0)
        GLES30.glUniform1f(u("uGroundY"), setup.wetY.toFloat())
        GLES30.glUniform1f(u("uGroundY2"), setup.wetY2.toFloat())
        GLES30.glUniform1f(u("uPuddleBias2"), setup.wetBias2.toFloat())
        GLES30.glUniform1f(u("uAlpha"), if (glass) 0.2f else 1f)
        val nl = if (reflectPass) 0 else kotlin.math.min(6, setup.lights.size)
        GLES30.glUniform1i(u("uLightCount"), nl)
        if (nl > 0) {
            GLES30.glUniform3fv(u("uLightPos"), 6, lightPos, 0)
            GLES30.glUniform3fv(u("uLightColor"), 6, lightColor, 0)
            GLES30.glUniform1fv(u("uLightDist"), 6, lightDist, 0)
            GLES30.glUniform3fv(u("uSpotDir"), 6, spotDir, 0)
            GLES30.glUniform2fv(u("uSpotCos"), 6, spotCos, 0)
        }
        GLES30.glUniform1i(u("uAdd"), if (pools) 1 else 0)
        // 光斑：溢出 × interior、路灯 × street，都 × (1 − 0.85 daylight)（鼓点 thump 未接）。
        val poolK = 1 - 0.85 * light.state.daylight
        if (station) GLES30.glUniform3f(u("uPoolScale"), (canopyLevel * poolK).toFloat(), (streetLevel * poolK).toFloat(), (screenLevel * poolK).toFloat())
        else GLES30.glUniform3f(u("uPoolScale"), (interiorLevel * poolK).toFloat(), (streetLevel * poolK).toFloat(), poolK.toFloat())
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
            GLES30.glUniform1f(u("uPuddleBias"), setup.wetBias.toFloat())
            GLES30.glUniform1f(u("uWetTime"), ambient.toFloat())
        }
        if (pools) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
            GLES30.glDepthMask(false)
            GLES30.glBindVertexArray(poolVao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, poolCount)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        } else if (glass) {
            // 透明玻璃：不写深度，按 alpha 混合（目标的 alpha 不动）。
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
            GLES30.glDepthMask(false)
            GLES30.glBindVertexArray(glassVao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, glassCount)
            drawDoors(u("uOffset"), doorGlassVaos, doorGlassCounts)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        } else {
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
            drawDoors(u("uOffset"), doorVaos, doorCounts)
            drawActors(MachiDynPart.LIT)
        }
        GLES30.glBindVertexArray(0)
    }

    private val identity = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }

    /** 会动的件：LIT 照常画；SHADOW / ADD 在不透明之后、玻璃之前，混合、不写深度（traffic.ts renderOrder 1）。 */
    private fun drawActors(mode: Int) {
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        for (a in setup.actors) for (p in a.parts) {
            if (p.mode != mode || !p.visible || p.count == 0) continue
            GLES30.glUniformMatrix4fv(u("uModel"), 1, false, p.model, 0)
            GLES30.glUniform1f(u("uClipHalf"), p.clip)
            p.tint?.let { GLES30.glUniform3fv(u("uTint"), 1, it, 0) } ?: GLES30.glUniform3f(u("uTint"), 1f, 1f, 1f)
            GLES30.glBindVertexArray(p.vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, p.count)
        }
        GLES30.glUniformMatrix4fv(u("uModel"), 1, false, identity, 0)
        GLES30.glUniform1f(u("uClipHalf"), 0f)
    }

    /** 软影（dst × (1 − c)）与车灯光斑（加色）。 */
    private fun drawActorOverlays(vp: FloatArray) {
        if (setup.actors.none { a -> a.parts.any { it.mode != MachiDynPart.LIT && it.visible } }) return
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVP"), 1, false, vp, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uOffset"), 0f, 0f, 0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uAdd"), 2)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uReflectPass"), 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        GLES30.glBlendFuncSeparate(GLES30.GL_ZERO, GLES30.GL_ONE_MINUS_SRC_COLOR, GLES30.GL_ZERO, GLES30.GL_ONE)
        drawActors(MachiDynPart.SHADOW)
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
        drawActors(MachiDynPart.ADD)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uAdd"), 0)
        GLES30.glBindVertexArray(0)
    }

    /** 自动门：两扇各沿 x 滑开 direction × 0.86 × openness（左 −1、右 +1）。 */
    private fun drawDoors(loc: Int, vaos: IntArray, counts: IntArray) {
        for (k in 0..1) {
            if (counts[k] == 0) continue
            val dir = if (k == 0) -1f else 1f
            GLES30.glUniform3f(loc, (dir * MachiKonbiniLayout.DOOR_SLIDE * doorOpenness).toFloat(), 0f, 0f)
            GLES30.glBindVertexArray(vaos[k])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, counts[k])
        }
        GLES30.glUniform3f(loc, 0f, 0f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 运镜按氛围时间（camera.ts motionTime：用上一帧累加的 ambient，帧间隔封顶 0.1 s）；幅度 4 s 内从 0 升满（DRIFT_RAMP_SECONDS）。
        val pose = synchronized(rig) {
            rig.setAspect(aspectW, aspectH)
            // 拖拽：闲置回正 + 运镜闸门（一拖运镜停，回到静止位置后 4 秒升满）。
            val gate = rig.step(clock.realDelta.let { if (it <= 0.0) 1.0 / 60 else it }, System.currentTimeMillis())
            if (restCamera) rig.pose(0.0, 0.0) else rig.pose(ambient, kotlin.math.min(1.0, ambient / 4.0) * gate)
        }
        val vp = pose.viewProjection()
        // 氛围时钟（clock.ts MachiClock）：一帧最多走 0.1 s，暂停时 delta = 0。第一帧补 1/60 s，让取证首帧和原来一致。
        clock.paused = paused()
        clock.tick(System.nanoTime() / 1e6)
        val delta = if (clock.realDelta == 0.0 && !clock.paused && ambient == 0.0) 1.0 / 60 else clock.delta
        val t = timeSeconds()
        flash = if (light.state.rain < 0.5) 0.0 else resolveMachiLightning(lightningTimes, t) * light.state.rain
        val target = resolveMachiBoardTarget(lines, t)
        signs?.show(target, lines)
        if (station) {
            val chorus = resolveMachiChorus(lines, target)
            canopyLevel = (0.85 + 0.15 * resolveMachiFlicker(ambient + 1.7, "$seed:canopy", 0.4)) * (1 + 0.08 * chorus) * light.look.artificial
            streetLevel = light.look.streetLamps * (0.75 + 0.25 * resolveMachiFlicker(ambient + 4.2, "$seed:lamp", 0.45))
            neonLevel = light.look.artificial * (0.8 + 0.2 * resolveMachiFlicker(ambient, seed))
            screenLevel = (1 - 0.8 * light.state.daylight) * (1 + 0.2 * chorus)
            updateLights()
        }
        if (konbini) {
            doorOpenness = resolveMachiKonbiniDoorOpenness(doorEvents, t, setup.doorInterval, setup.doorPhase, lines.isEmpty())
            // konbiniScene：interior = (1 + 0.1 chorus + 0.06 doorOpenness) × artificial（beat.warm 未接）。
            interiorLevel = (1 + 0.1 * resolveMachiChorus(lines, target) + 0.06 * doorOpenness) * light.look.artificial
            streetLevel = light.look.streetLamps * (0.75 + 0.25 * resolveMachiFlicker(ambient + 3.1, "$seed:street", 0.45))
            updateLights()
        }
        // 会动的件按氛围时钟（风扇转、车按 9.5 s 窗口开过）。
        val actorFrame = MachiActorFrame(ambient, seed, MACHI_TRAFFIC_CHANCE)
        setup.actors.forEach { it.update(actorFrame) }
        val view = (pose.position - pose.target).normalized()
        val wet = light.state.wetness > 0.001
        // 倒影（reflection.ts）：干的时候路面不取倒影，不画。
        if (wet) {
            val mirror = machiMirrorVP(vp, setup.mirrorY)
            reflection.begin()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            drawMesh(mirror, reflectPass = true, wet = false)
            signs?.draw(
                mirror, floatArrayOf(view.x.toFloat(), view.y.toFloat(), view.z.toFloat()),
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
        if (poolCount > 0) drawMesh(vp, reflectPass = false, wet = false, pools = true)
        drawActorOverlays(vp)
        // のぼり旗（nobori.ts）：wind = 0.35 + 0.65 × min(1, rain)。
        setup.nobori?.draw(vp, ambient, 0.35 + 0.65 * kotlin.math.min(1.0, resolveMachiRainAmount(light.state.rain)), light,
            MachiPointLights(kotlin.math.min(6, setup.lights.size), lightPos, lightColor, lightDist, spotDir, spotCos))
        // 便利店贴图牌面：窗内霓虹 × (0.85 + 0.15 sin(2.2 ambient) + 0.4 chorus)（konbiniSigns update）。
        setup.decals?.let { d ->
            if (konbini) d.gain[2] = 0.85 + 0.15 * kotlin.math.sin(ambient * 2.2) + 0.4 * resolveMachiChorus(lines, target)
            d.draw(vp, light)
        }
        if (glassCount > 0) drawMesh(vp, reflectPass = false, wet = false, glass = true)
        // 光柱（lightCones.ts）：level = 0.55 × lamp × (1 − 0.6 daylight)，lamp = streetLamps × (0.75 + 0.25 flicker)。
        // 便利店（konbiniScene direct）：street = streetLamps × (0.75 + 0.25 flicker(ambient + 3.1, seed:street))。
        val lamp = if (konbini) light.look.streetLamps * (0.75 + 0.25 * resolveMachiFlicker(ambient + 3.1, "$seed:street", 0.45))
            else light.look.streetLamps * (0.75 + 0.25 * resolveMachiFlicker(ambient + 4.2, "$seed:lamp", 0.45))
        cones?.draw(vp, floatArrayOf(view.x.toFloat(), view.y.toFloat(), view.z.toFloat()), lampColor, ambient, lamp * (1 - 0.6 * light.state.daylight))
        // 雨丝 / 水花 / 滴水（MachiWeather.kt）：雨量 = rain × 0.62（audio、chorus 未接，intensity 1）；灯照雨丝程度 1 − 0.9 daylight。
        val vx = view.x; val vz = view.z
        val rl = kotlin.math.sqrt(vx * vx + vz * vz)
        weather.draw(
            vp, floatArrayOf((vz / rl).toFloat(), 0f, (-vx / rl).toFloat()), ambient,
            resolveMachiRainAmount(light.state.rain), light.state.wetness, 1 - 0.9 * light.state.daylight,
            rainColor, rainGlintColor, dripColor,
        )
        // 雪：雪量 = snow × (0.75 + 0.25 intensity)；点大小按场景目标每世界单位像素。
        weather.drawSnow(vp, ambient, light.state.snow, 1 - 0.9 * light.state.daylight, post.sceneH / ((pose.top - pose.bottom) / pose.zoom), snowColor, snowGlintColor)
        // 白气（steam.ts）：朝镜头的软云片，加色、不写深度。
        setup.steam?.let { st ->
            val fx = -view.x; val fy = -view.y; val fz = -view.z
            var rx = -fz; var rz = fx; val rlen = kotlin.math.sqrt(rx * rx + rz * rz); rx /= rlen; rz /= rlen
            val ux = -rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy
            st.draw(vp, floatArrayOf(rx.toFloat(), 0f, rz.toFloat()), floatArrayOf(ux.toFloat(), uy.toFloat(), uz.toFloat()), ambient,
                machiSteamWind(setup.windYaw), machiSteamColor(light.palette.skyBottom))
        }
        // 歌词大屏 / 跑马灯 / 门头霓虹（MachiScreen.kt）
        ambient += delta
        signs?.draw(
            vp, floatArrayOf(view.x.toFloat(), view.y.toFloat(), view.z.toFloat()),
            MachiSignFrame(t, ambient, delta, resolveMachiChorus(lines, target), 0.0, light.look.artificial, resolveMachiFlicker(ambient, seed)),
        )
        // 后处理（MachiPost.kt）：墨线 / 薄雾 / 云 → bloom → 色调映射。薄雾色、云色按 daylight 推（MachiNightLight）；
        // 雾里的两处灯（kowloonScene glowA / glowB）。
        android.opengl.Matrix.invertM(invVP, 0, vp, 0)
        val look = light.look
        val neon = 1.0 * (0.85 + 0.15 * resolveMachiFlicker(ambient, seed))
        // 便利店雾灯（konbiniScene direct）：A = interiorLight × 0.55 × interiorLevel × night，B = streetLight × 0.42 × street × night，
        // interiorLevel = (1 + 0.1 chorus) × artificial（门开合、鼓点未接），night = 1 − 0.85 daylight。
        val night = 1 - 0.85 * light.state.daylight
        post.finish(
            pixelWorldSize = (pose.top - pose.bottom) / pose.zoom / post.sceneH,
            centerDistance = kotlin.math.sqrt((pose.position - pose.target).dot(pose.position - pose.target)),
            near = 0.1, depthRange = 200.0 - 0.1, invVP = invVP,
            atmosphere = MachiAtmosphereFrame(
                time = ambient,
                mistColor = light.mistColor,
                mistAmount = look.mist,
                cloudColor = light.cloudColor,
                cloudAmount = look.clouds,
                haze = look.haze,
                lightning = flash,
                glowA = setup.glowA,
                glowColorA = if (station) {
                    // stationScene：canopyLevel = (0.85 + 0.15 flicker(ambient + 1.7, seed:canopy)) × (1 + 0.08 chorus) × artificial。
                    val canopyLevel = (0.85 + 0.15 * resolveMachiFlicker(ambient + 1.7, "$seed:canopy", 0.4)) * (1 + 0.08 * resolveMachiChorus(lines, target)) * look.artificial
                    FloatArray(3) { (canopyColor[it] * 0.5 * canopyLevel * night).toFloat() }
                } else if (konbini) FloatArray(3) { (glowColorKA[it] * 0.55 * interiorLevel * night).toFloat() } else FloatArray(3) { (glowColorA[it] * 0.42 * neon).toFloat() },
                glowB = setup.glowB,
                glowColorB = if (station) FloatArray(3) { (glowColorKB[it] * 0.42 * lamp * night).toFloat() } else if (konbini) FloatArray(3) { (glowColorKB[it] * 0.42 * lamp * night).toFloat() } else FloatArray(3) { (glowColorB[it] * 0.36 * look.artificial).toFloat() },
                stars = look.stars,
            ),
            bloomStrength = look.bloom,
            streakStrength = look.streaks,
            // 景深按相对默认构图的缩放走（createMachiRuntime：zoom = camera.zoom / defaultView.zoom），光圈默认 f/8。
            lens = resolveMachiLens(MachiLensTuning(), pose.zoom / setup.view.zoom),
        )
    }
}

private fun Color.machiHex(): String = String.format("#%06x", toArgb() and 0xFFFFFF)

/**
 * 町景取证舞台（城寨酒吧白模）。[timeSeconds] 为歌曲时间（秒）；颜色取播放页主题（同 Folia Theme 的四色）。
 * [weather] 是天气 id 或 auto（按 seed 加权，同 Folia resolveMachiWeatherKind）；[restCamera] 让镜头停在静止位。
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
    restCamera: Boolean = false,
    /** auto（按本地时钟，Folia 默认）/ night / dusk / day。 */
    timeOfDay: String = "night",
    paused: () -> Boolean = { false },
    /** 场景：kowloon（城寨酒吧，默认）/ konbini（便利店街角）/ station（加油站停车场）。 */
    scene: String = "kowloon",
    /** 播放页可拖动镜头（Folia OrbitControls）。 */
    draggable: Boolean = false,
) {
    val palette = remember(backgroundColor, primaryColor, secondaryColor, accentColor, seed) {
        resolveMachiPalette(MachiTheme(backgroundColor.machiHex(), primaryColor.machiHex(), secondaryColor.machiHex(), accentColor.machiHex()), seed)
    }
    // timeOfDay auto：按本地时钟取白天程度（environment.ts resolveMachiDaylight）；整点附近重建一次即可，这里随换歌 / 换主题时取。
    val daylight = remember(timeOfDay, seed, palette) {
        val now = java.util.Calendar.getInstance()
        resolveMachiDaylight(timeOfDay, now.get(java.util.Calendar.HOUR_OF_DAY) + now.get(java.util.Calendar.MINUTE) / 60.0 + now.get(java.util.Calendar.SECOND) / 3600.0)
    }
    val light = remember(palette, weather, seed, daylight) { MachiNightLight(palette, runCatching { resolveMachiWeatherKind(weather, seed) }.getOrDefault(MachiWeatherKind.CLEAR), daylight) }
    // scene auto：按歌选（machiTuning.ts resolveMachiSceneId：FNV-1a("machi-scene:" + seed) % 3，顺序 konbini / station / kowloon）。
    val sceneId = if (scene == "auto") listOf("konbini", "station", "kowloon")[(machiHashSeed("machi-scene:$seed") % 3).toInt()] else scene
    val setup = remember(sceneId, palette, songTitle, artist, seed, lines) {
        when (sceneId) {
            "konbini" -> machiKonbiniSetup(palette, seed, songTitle, artist)
            "station" -> machiStationSetup(palette, seed, songTitle, artist, lines)
            else -> machiKowloonSetup(palette, seed, songTitle, artist)
        }
    }
    val rig = remember(setup) { MachiCameraRig(setup.framing, setup.view) }
    Box(modifier) {
      // 换歌（seed）、换主题（palette → light / signs）时整块 GLSurfaceView 重建：factory 只跑一次，渲染器拿的是建时的模型。
      androidx.compose.runtime.key(setup, light, lines, restCamera) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(MachiRenderer(setup, light, rig, timeSeconds, lines, seed, restCamera, paused))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.onPause() },
        )
      }
      // Folia 播放页町景可拖：单指转、双指缩放 / 平移（OrbitControls）；预览（restCamera）不接拖拽。
      if (draggable && !restCamera) {
        Box(
            Modifier.fillMaxSize().pointerInput(rig) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    // 过了触摸阈值才算拖（轻点留给播放页开关控件）。
                    var dragging = false
                    var travel = 0f
                    var prevCentroid = first.position
                    var prevSpan = 0f
                    var prevCount = 1
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val cx = pressed.map { it.position.x }.average().toFloat()
                        val cy = pressed.map { it.position.y }.average().toFloat()
                        val centroid = androidx.compose.ui.geometry.Offset(cx, cy)
                        val span = if (pressed.size >= 2) (pressed[0].position - pressed[1].position).getDistance() else 0f
                        if (!dragging) {
                            travel += (centroid - prevCentroid).getDistance()
                            if (travel > viewConfiguration.touchSlop || pressed.size >= 2) {
                                dragging = true
                                synchronized(rig) { rig.beginDrag() }
                            } else {
                                prevCentroid = centroid; prevCount = pressed.size
                                continue
                            }
                        }
                        if (pressed.size == prevCount) {
                            val d = centroid - prevCentroid
                            val h = size.height.toDouble()
                            synchronized(rig) {
                                if (pressed.size == 1) rig.rotate(d.x.toDouble(), d.y.toDouble(), h)
                                else {
                                    if (prevSpan > 0f && span > 0f) rig.zoomBy((span / prevSpan).toDouble())
                                    rig.pan(d.x.toDouble(), d.y.toDouble(), h)
                                }
                            }
                        }
                        prevCentroid = centroid; prevSpan = span; prevCount = pressed.size
                        ev.changes.forEach { it.consume() }
                    }
                    if (dragging) synchronized(rig) { rig.endDrag(System.currentTimeMillis()) }
                }
            },
        )
      }
    }
}

