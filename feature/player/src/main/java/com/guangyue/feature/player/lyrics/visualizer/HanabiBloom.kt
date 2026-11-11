// 花火辉光：Folia hanabiScreenBloom.ts（副歌权重 + 全屏 bloom 强度，逐函数对译）+ mg/glow.ts（烟花层 bloom）的手机近似。
// Folia 用 dual-filter bloom（亮部提取 → 逐级降采样 → 上采样叠加），整屏离屏渲染；模拟器 / 低端机上每帧整屏模糊会 ANR，
// 这里改成「预烘」：每颗亮精灵叠一张软光贴图（加色），每个字叠一张建单元时预烘好的模糊字（加色），强度跟 Folia 同一条曲线走。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.max
import kotlin.math.min

/** 整体辉光 1 与副歌辉光 1 时 bloom 的强度（HANABI_SCREEN_BLOOM）。 */
internal const val HB_SCREEN_BLOOM_BASE = 0.45
internal const val HB_SCREEN_BLOOM_CHORUS = 0.9

private const val RAMP_IN_BEFORE = 0.5
private const val RAMP_IN_AFTER = 0.3
private const val RAMP_OUT_BEFORE = 0.2
private const val RAMP_OUT_AFTER = 0.9

private fun hbSmooth(value: Double): Double {
    val t = min(1.0, max(0.0, value))
    return t * t * (3 - 2 * t)
}

/** resolveHanabiChorusWeight：time 时刻的副歌权重 0..1（时段按时间排好）。 */
internal fun resolveHanabiChorusWeight(spans: List<HbChorusSpan>, time: Double): Double {
    var weight = 0.0
    for (span in spans) {
        if (time < span.startTime - RAMP_IN_BEFORE) break
        if (time > span.endTime + RAMP_OUT_AFTER) continue
        val rampIn = hbSmooth((time - (span.startTime - RAMP_IN_BEFORE)) / (RAMP_IN_BEFORE + RAMP_IN_AFTER))
        val rampOut = 1 - hbSmooth((time - (span.endTime - RAMP_OUT_BEFORE)) / (RAMP_OUT_BEFORE + RAMP_OUT_AFTER))
        weight = max(weight, min(rampIn, rampOut))
    }
    return weight
}

/** resolveHanabiScreenBloomStrength。 */
internal fun resolveHanabiScreenBloomStrength(bloom: Double, chorusBloom: Double, chorusWeight: Double) =
    max(0.0, bloom) * HB_SCREEN_BLOOM_BASE + max(0.0, chorusBloom) * HB_SCREEN_BLOOM_CHORUS * chorusWeight

/**
 * 预烘辉光的手机系数（不是 Folia 的值，是对着 Folia 截图调的近似）：
 * 光晕贴图边长 = 精灵长边 × HALO_SIZE；烟花光晕透明度 = 精灵 α × (glow × FIREWORK + 全屏强度 × SCREEN_SPARK)；
 * 字的光晕透明度 = 字 α × 全屏强度 × SCREEN_TEXT（Folia 全屏 bloom 阈值 0.5：只有亮字、亮火花进 bloom）。
 */
internal object HbGlowLook {
    const val HALO_SIZE = 2.6
    const val FIREWORK = 0.26
    const val SCREEN_SPARK = 0.12
    const val SCREEN_TEXT = 0.42
    /** 预烘模糊字：模糊半径 / 留边，按字号比例。 */
    const val TEXT_BLUR = 0.3
    const val TEXT_PAD = 0.5

    fun sparkHaloAlpha(alpha: Double, glow: Double, screen: Double) =
        min(1.0, alpha * (max(0.0, glow) * FIREWORK + screen * SCREEN_SPARK))
}
