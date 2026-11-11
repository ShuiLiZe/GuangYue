// 町景的时间与天气：逐函数对译 Folia v0.7.25 machi/engine/environment.ts（纯函数，三个场景共用）。
// 设置 → 环境目标（白天程度、雨 / 雪 / 雾），再按真实时间阻尼地追过去，所以换天气、天黑天亮都是渐变的；地面的湿度和积雪比降水本身慢。
// 再由环境状态推出灯光、后处理这些标量。金标 machi_env_golden.txt（docs/port/scripts/machi-env-golden-gen.js）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** 天气（不含 auto）：clear / rain / snow / fog。 */
internal enum class MachiWeatherKind(val id: String) { CLEAR("clear"), RAIN("rain"), SNOW("snow"), FOG("fog") }

/** 白天程度：0 夜，约 0.45 黄昏，1 白天。 */
internal const val MACHI_DAYLIGHT_NIGHT = 0.0
internal const val MACHI_DAYLIGHT_DUSK = 0.45
internal const val MACHI_DAYLIGHT_DAY = 1.0

private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)
}

/** 本地时钟 → 白天程度：05:00–06:30 天亮，06:30–16:30 白天，16:30–17:30 落到黄昏，17:30–18:30 入夜。hour 带小数（0..24）。 */
internal fun resolveMachiDaylightAt(hour: Double): Double {
    val h = ((hour % 24) + 24) % 24
    if (h >= 5 && h < 6.5) return smoothstep(5.0, 6.5, h)
    if (h >= 6.5 && h < 16.5) return 1.0
    if (h >= 16.5 && h < 17.5) return 1 - (1 - MACHI_DAYLIGHT_DUSK) * smoothstep(16.5, 17.5, h)
    if (h >= 17.5 && h < 18.5) return MACHI_DAYLIGHT_DUSK * (1 - smoothstep(17.5, 18.5, h))
    return 0.0
}

/** timeOfDay：auto / night / dusk / day；auto 按本地时钟（hour 带小数）。 */
internal fun resolveMachiDaylight(timeOfDay: String, hour: Double): Double = when (timeOfDay) {
    "night" -> MACHI_DAYLIGHT_NIGHT
    "dusk" -> MACHI_DAYLIGHT_DUSK
    "day" -> MACHI_DAYLIGHT_DAY
    else -> resolveMachiDaylightAt(hour)
}

/** “自动”天气按 seed 加权：雨 35%、晴 30%、雾 20%、雪 15%。 */
private val AUTO_WEATHER = listOf(MachiWeatherKind.RAIN to 0.35, MachiWeatherKind.CLEAR to 0.3, MachiWeatherKind.FOG to 0.2, MachiWeatherKind.SNOW to 0.15)

/** weather：auto 或某个天气 id。 */
internal fun resolveMachiWeatherKind(weather: String, seed: String): MachiWeatherKind {
    if (weather != "auto") return MachiWeatherKind.entries.first { it.id == weather }
    var roll = machiHash01(machiHashSeed("machi-weather:$seed"), 0, 0x3d)
    for ((kind, weight) in AUTO_WEATHER) {
        if (roll < weight) return kind
        roll -= weight
    }
    return MachiWeatherKind.RAIN
}

/** 环境此刻的状态（目标只用前四项）：白天程度、雨、雪、雾，再加地面的湿度与积雪（比降水慢）。 */
internal data class MachiEnvironmentState(
    val daylight: Double,
    val rain: Double,
    val snow: Double,
    val fog: Double,
    val wetness: Double = 0.0,
    val snowCover: Double = 0.0,
)

internal fun resolveMachiEnvironmentTarget(daylight: Double, kind: MachiWeatherKind) = MachiEnvironmentState(
    daylight,
    rain = if (kind == MachiWeatherKind.RAIN) 1.0 else 0.0,
    snow = if (kind == MachiWeatherKind.SNOW) 1.0 else 0.0,
    fog = if (kind == MachiWeatherKind.FOG) 1.0 else 0.0,
)

internal fun createMachiEnvironmentState(target: MachiEnvironmentState) = target.copy(
    wetness = if (target.rain > 0.5) 1.0 else if (target.fog > 0.5) 0.35 else 0.0,
    snowCover = if (target.snow > 0.5) 1.0 else 0.0,
)

private const val TAU_DAYLIGHT = 1.6
private const val TAU_PRECIPITATION = 1.4
private const val TAU_FOG = 2.2
private const val TAU_WET_UP = 3.0
private const val TAU_WET_DOWN = 20.0
private const val TAU_SNOW_UP = 8.0
private const val TAU_SNOW_DOWN = 12.0

private fun approach(value: Double, target: Double, tau: Double, delta: Double): Double {
    val next = value + (target - value) * (1 - exp(-delta / tau))
    return if (abs(next - target) < 1e-3) target else next
}

/** 推进 delta 秒：降水、雾、白天程度阻尼地追目标；下雨时路面变湿，停了慢慢干；下雪时慢慢积、停了慢慢化。 */
internal fun stepMachiEnvironment(state: MachiEnvironmentState, target: MachiEnvironmentState, delta: Double): MachiEnvironmentState {
    if (delta <= 0) return state
    val rain = approach(state.rain, target.rain, TAU_PRECIPITATION, delta)
    val snow = approach(state.snow, target.snow, TAU_PRECIPITATION, delta)
    val fog = approach(state.fog, target.fog, TAU_FOG, delta)
    val wetTarget = max(rain, fog * 0.35)
    val wetness = approach(state.wetness, wetTarget, if (wetTarget > state.wetness) TAU_WET_UP else TAU_WET_DOWN, delta)
    val snowCover = approach(state.snowCover, snow, if (snow > state.snowCover) TAU_SNOW_UP else TAU_SNOW_DOWN, delta)
    return MachiEnvironmentState(approach(state.daylight, target.daylight, TAU_DAYLIGHT, delta), rain, snow, fog, wetness, snowCover)
}

/** 由环境状态推出的灯光与后处理标量。 */
internal data class MachiEnvironmentLook(
    val warmth: Double,
    val overcast: Double,
    val artificial: Double,
    val streetLamps: Double,
    val sunIntensity: Double,
    val hemiIntensity: Double,
    val bloom: Double,
    val streaks: Double,
    val clouds: Double,
    val stars: Double,
    val mist: Double,
    val haze: Double,
)

internal fun resolveMachiEnvironmentLook(state: MachiEnvironmentState): MachiEnvironmentLook {
    val daylight = state.daylight
    val warmth = max(0.0, 1 - abs(daylight - MACHI_DAYLIGHT_DUSK) / 0.38)
    val overcast = min(1.0, maxOf(state.rain, state.fog, state.snow * 0.8))
    val night = 1 - daylight
    return MachiEnvironmentLook(
        warmth = warmth,
        overcast = overcast,
        artificial = 0.55 + 0.45 * smoothstep(0.85, 0.2, daylight),
        streetLamps = smoothstep(0.62, 0.3, daylight),
        sunIntensity = (1.35 + (3.0 - 1.35) * daylight) * (1 - 0.55 * overcast * daylight) * (1 - 0.25 * state.fog),
        hemiIntensity = 2.1 + 0.35 * daylight + 0.25 * overcast * daylight,
        bloom = 0.12 + 0.38 * night,
        streaks = 0.55 * night * night,
        clouds = 0.18 + 0.82 * overcast,
        stars = night * (1 - overcast) * smoothstep(0.35, 0.05, daylight),
        mist = 0.16 * (1 - 0.4 * daylight) + 0.1 * state.rain + 0.45 * state.fog + 0.06 * state.snow,
        haze = 0.42 * state.fog + 0.08 * state.snow,
    )
}
