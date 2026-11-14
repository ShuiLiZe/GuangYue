// 镜台（Folia diorama）每帧写入：逐函数对译 src/components/visualizer/diorama/dioramaSceneFrame.ts
//   recycleDioramaMoteWindow    尘埃窗口跟读头回收（每行一个环形槽位，读头不动就不写）
//   updateDioramaNeighborLines  邻行按英雄距离定尺寸，按距离 / 偏移定透明度与颜色
//   updateDioramaActiveUnits    当前行逐单元显现 + 三种独立跟唱效果（普通辉光、灵魂出窍、渐变）
// Folia 直接写 three 的 mesh / material；这里写进普通结构（DioramaNeighborLook / DioramaUnitLook），渲染层照着画。
// lightVals / soulVals 与 Folia 一样是 Float32 数组（每帧写回都截成 float），金样逐位对得上。
// DioramaSceneFrameTest 对 node 直接跑 Folia dioramaSceneFrame.ts（three 0.185.1）的金样。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private fun fClamp01(v: Double) = min(1.0, max(0.0, v))

/**
 * recycleDioramaMoteWindow：读头前后 -BEHIND..+AHEAD 每行一个槽位；槽里已是这一行就跳过，
 * 否则按（夹到歌词范围内的）锚行帧外推写入。返回是否有槽位被重写。
 */
internal fun recycleDioramaMoteWindow(
    written: IntArray,
    motePositions: FloatArray,
    sequencer: DioramaSequencerState,
    globalIndex: Int,
    total: Int,
    moteCircumference: Int,
    moteRadial: Int,
    seed: String?,
): Boolean {
    val lastLine = max(0, total - 1)
    var dirty = false
    for (line in (globalIndex - DIORAMA_MOTE_LINES_BEHIND)..(globalIndex + DIORAMA_MOTE_LINES_AHEAD)) {
        val slot = dioramaMoteSlot(line)
        if (written[slot] == line) continue
        val anchorLine = min(max(line, 0), lastLine)
        val resolved = dioramaResolveGlobal(sequencer, anchorLine) ?: continue
        writeDioramaMoteLine(motePositions, extendDioramaFrame(resolved.frame, line - anchorLine), line, moteCircumference, moteRadial, seed)
        written[slot] = line
        dirty = true
    }
    return dirty
}

/** 新建尘埃槽位表：全部标成“没写过”（Folia 用 NaN / 未定义，这里用 Int.MIN_VALUE）。 */
internal fun newDioramaMoteWritten(): IntArray = IntArray(DIORAMA_MOTE_WINDOW_LINES) { Int.MIN_VALUE }

/** 阻尼后的主题色（线性空间）。 */
internal data class DioramaDampedColors(
    val primary: DioramaLinearColor,
    val accent: DioramaLinearColor,
    val secondary: DioramaLinearColor,
    val bg: DioramaLinearColor,
)

/** 一条邻行这一帧怎么画：整体缩放、透明度、颜色。 */
internal data class DioramaNeighborLook(val index: Int, val scale: Double, val opacity: Double, val color: DioramaLinearColor)

/** 栅格量出的整行宽（px）与字号（px）——只用这两个数定世界宽度。 */
internal data class DioramaRasterMetrics(val advancePx: Int, val fontPx: Int)

private fun dist(a: DioramaVec, b: DioramaVec): Double {
    val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * updateDioramaNeighborLines：每条邻行按“英雄距离下读起来合适”定尺寸（再乘摆位缩放与全局字号），
 * 不朝向镜头、不按实时距离缩放；离场走廊用 primary 成簇淡出，过去行亮（primary），未来行暗（secondary）。
 * 没有栅格的行（还没排进预算）跳过，与 Folia 一样。
 */
internal fun updateDioramaNeighborLines(
    visibleLines: List<DioramaVisibleLine>,
    globalIndex: Int,
    transitionOutgoingIndex: Int?,
    rasters: Map<Int, DioramaRasterMetrics>,
    camPos: DioramaVec,
    fov: Double,
    aspect: Double,
    lyricsFontScale: Double,
    damped: DioramaDampedColors,
): List<DioramaNeighborLook> {
    val out = ArrayList<DioramaNeighborLook>()
    for (v in visibleLines) {
        if (v.index == globalIndex) continue
        val raster = rasters[v.index] ?: continue
        val worldWidth = raster.advancePx * (DIORAMA_LINE_FONT_SIZE / raster.fontPx)
        val fit = resolveDioramaFrameFitScale(worldWidth, DIORAMA_HERO_DISTANCE, fov, aspect) * v.placement.scale * lyricsFontScale
        val life = resolveDioramaTextLife(dist(v.position, camPos))
        out += if (v.isOutgoing) {
            DioramaNeighborLook(v.index, fit, resolveDioramaOutgoingLineOpacity(v.index - (transitionOutgoingIndex ?: v.index)) * life, damped.primary)
        } else {
            val offset = v.index - globalIndex
            DioramaNeighborLook(v.index, fit, resolveDioramaNeighborLineOpacity(offset) * life, if (offset < 0) damped.primary else damped.secondary)
        }
    }
    return out
}

/** 当前行一个单元这一帧的三张平面：底字、加色辉光、加色灵魂（灵魂平面会抬起、放大）。 */
internal class DioramaUnitLook {
    var baseOpacity = 0.0
    var baseColor = DioramaLinearColor(1.0, 1.0, 1.0)
    var glowOpacity = 0.0
    var glowColor = DioramaLinearColor(1.0, 1.0, 1.0)
    var glowVisible = false
    var soulOpacity = 0.0
    var soulColor = DioramaLinearColor(1.0, 1.0, 1.0)
    var soulY = 0.0
    var soulScale = 1.0
    var soulVisible = false
}

/** 渐变跟唱的“唱过色”：主题强调色；调色板退化（强调≈主色，如墨染 / 素白）时朝中性灰拉开明度，不引入色相。 */
internal fun resolveDioramaSungTint(damped: DioramaDampedColors): DioramaLinearColor {
    val a = damped.accent; val p = damped.primary
    val tintSeparation = abs(a.r - p.r) + abs(a.g - p.g) + abs(a.b - p.b)
    if (tintSeparation >= 0.4) return a
    val deficit = 1 - tintSeparation / 0.4
    val primaryLum = (p.r + p.g + p.b) / 3
    val accentLum = (a.r + a.g + a.b) / 3
    val targetLum = if (primaryLum > 0.5) accentLum * (1 - 0.5 * deficit) else accentLum + (1 - accentLum) * 0.55 * deficit
    return a.lerp(DioramaLinearColor(targetLum, targetLum, targetLum), deficit)
}

/**
 * updateDioramaActiveUnits：底字显现总在跑；普通辉光点亮辉光平面，灵魂出窍驱动灵魂平面，渐变给底色染色。
 * 颜色只算一次（底字），辉光 / 灵魂只读不改。looks / lightVals / soulVals 长度 ≥ 单元数（Folia 缺平面就跳过该单元）。
 */
internal fun updateDioramaActiveUnits(
    activeLineUnits: List<DioramaLyricUnit>,
    looks: List<DioramaUnitLook?>,
    lightVals: FloatArray?,
    soulVals: FloatArray?,
    now: Double,
    delta: Double,
    life: Double,
    breath: Double,
    powerEnv: Double,
    damped: DioramaDampedColors,
    keywordUnitColors: Map<Int, DioramaLinearColor>,
    glowIntensity: Double,
    soulIntensity: Double,
    soulActiveEnabled: Boolean,
    gradientIntensity: Double,
) {
    val gradientStrength = min(1.5, gradientIntensity)
    val sungTint = resolveDioramaSungTint(damped)
    val gradHot01 = min(1.0, max(0.0, (gradientStrength - 0.1) / 1.4))
    val k = 0.8 - 0.18 * gradHot01
    val gradDeep = DioramaLinearColor(sungTint.r * k, sungTint.g * k, sungTint.b * k)

    activeLineUnits.forEachIndexed { i, unit ->
        val look = looks.getOrNull(i)
        if (look == null || lightVals == null || soulVals == null) return@forEachIndexed
        val isCurrent = now >= unit.startTime && now < unit.endTime
        val sung = now >= unit.endTime
        val span = max(unit.endTime - unit.startTime, 0.001)
        val sungMix = if (sung) 1.0 else if (isCurrent) fClamp01((now - unit.startTime) / span) else 0.0

        lightVals[i] = dioramaStepEnvelope(lightVals[i].toDouble(), if (isCurrent) 1.0 else 0.0, 14.0, 4.5, delta).toFloat()
        val gradientEnergy = resolveDioramaGradientEnergy(now, unit.startTime, unit.endTime) * gradientStrength

        look.baseOpacity = min(
            1.0,
            (DIORAMA_UNSUNG_UNIT_OPACITY + (DIORAMA_ACTIVE_LINE_OPACITY - DIORAMA_UNSUNG_UNIT_OPACITY) * sungMix + 0.08 * min(1.0, gradientEnergy)) * life,
        )
        val unitTarget = keywordUnitColors[i] ?: if (gradientIntensity > 0) gradDeep else sungTint
        val unitProgress = if (gradientIntensity > 0) gradientEnergy else lightVals[i].toDouble() * 1.15
        look.baseColor = damped.primary.lerp(unitTarget, fClamp01(unitProgress))

        val glowStrength = min(1.5, glowIntensity)
        val glowLevel = lightVals[i].toDouble() * life * breath * (0.6 + 0.4 * powerEnv) * glowStrength
        look.glowOpacity = min(1.0, DIORAMA_UNIT_GLOW_MAX_OPACITY * glowLevel)
        look.glowColor = look.baseColor
        look.glowVisible = glowLevel > 0.012

        soulVals[i] = dioramaStepEnvelope(soulVals[i].toDouble(), if (isCurrent) 1.0 else 0.0, 12.0, 2.2, delta).toFloat()
        val soulStrength = min(1.5, soulIntensity)
        val flightMix = if (sung) dioramaSmoothstep01(fClamp01((now - unit.endTime) / DIORAMA_SOUL_HANDOFF_SECONDS)) else 0.0
        val activeReach = if (soulActiveEnabled) soulStrength else 0.0
        val onGlyph = (1 - flightMix) * activeReach
        val flown = flightMix * soulStrength
        look.soulColor = look.baseColor
        look.soulOpacity = min(1.0, DIORAMA_SOUL_MAX_OPACITY * life * soulVals[i].toDouble() * (onGlyph + flown))
        look.soulY = DIORAMA_LINE_FONT_SIZE * (DIORAMA_SOUL_ACTIVE_LIFT_EM * onGlyph + DIORAMA_SOUL_DETACH_LIFT_EM * flown)
        look.soulScale = 1 + DIORAMA_SOUL_ACTIVE_SWELL * onGlyph + DIORAMA_SOUL_DETACH_SWELL * flown
        look.soulVisible = look.soulOpacity > 0.015
    }
}
