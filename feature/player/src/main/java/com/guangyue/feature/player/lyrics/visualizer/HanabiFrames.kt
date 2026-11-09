// 花火段落转场：逐函数对译 Folia hanabiTransitions.ts（上升 ascend / 闪白 flash / 开花 bloom 的出场帧与进场帧）
// 与 hanabiSceneFrames.ts（边界后旧段落按原速继续滑走、与新段落交叉渐变）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.max
import kotlin.math.min

internal class HbTransitionFrame(val alpha: Double = 1.0, val scale: Double = 1.0, val blur: Double = 0.0, val y: Double = 0.0, val rotation: Double = 0.0)
internal class HbLayerFrame(val index: Int, val frame: HbTransitionFrame)

private val HB_IDLE = HbTransitionFrame()

private fun launch(p: Double) = if (p <= 0) 0.0 else if (p >= 1) 1 + 2 * (p - 1) else p * p * p * (2 - p)

internal fun hbTransitionFrame(kind: String, exit: Boolean, progress: Double): HbTransitionFrame {
    val p = max(0.0, if (exit) progress else min(1.0, progress))
    if (kind == "ascend") {
        if (exit) {
            val e = launch(p)
            return HbTransitionFrame(y = 0.32 * e, blur = 10 * min(e, 1.0), alpha = 1 - 0.7 * hbEaseSeptic((p - 0.5) / 0.5), rotation = 0.55 * e)
        }
        val k = launch(1 - p)
        return HbTransitionFrame(y = -0.26 * k, blur = 10 * k, alpha = 1 - 0.7 * k, rotation = -0.5 * k)
    }
    if (kind == "flash") {
        val near = if (exit) hbEaseSeptic(p) else 1 - hbEaseSeptic(p)
        return HbTransitionFrame(scale = 1 + 0.05 * near, blur = 9 * near * near, alpha = 1 - 0.55 * near * near, rotation = (if (exit) 0.12 else -0.12) * near)
    }
    if (exit) {
        val e = hbEaseSeptic(p)
        return HbTransitionFrame(scale = 1 + 0.18 * e, blur = 5 * e, alpha = 1 - e, rotation = 0.3 * e)
    }
    val k = 1 - hbEaseSeptic(p)
    return HbTransitionFrame(scale = 1 - 0.08 * k, blur = 4 * k, alpha = 1 - k, rotation = -0.25 * k)
}

internal fun hbEnterDuration(kind: String, start: Double, end: Double): Double {
    val (lo, hi) = if (kind == "flash") 0.2 to 0.45 else 0.4 to 0.9
    return max(lo, min(hi, end - start))
}

private fun hbSmoothF(v: Double): Double { val t = min(1.0, max(0.0, v)); return t * t * (3 - 2 * t) }

private fun exitFrameOf(program: HbProgram, index: Int, time: Double, beyond: Boolean = false): HbTransitionFrame {
    val out = program.paragraphs.getOrNull(index)?.transitionOut ?: return HB_IDLE
    if (time < out.startTime) return HB_IDLE
    val progress = (time - out.startTime) / max(out.endTime - out.startTime, 1e-3)
    return hbTransitionFrame(out.kind, true, if (beyond) progress else min(progress, 1.0))
}

/** resolveHanabiSceneFrames：这一刻要画哪些段落场景、各自的转场帧（先旧后新）。 */
internal fun resolveHanabiSceneFrames(program: HbProgram, time: Double, transitionsEnabled: Boolean): Pair<Int, List<HbLayerFrame>> {
    if (program.paragraphs.isEmpty()) return -1 to emptyList()
    val active = findHanabiParagraphIndexAtTime(program, time)
    if (!transitionsEnabled) return active to listOf(HbLayerFrame(active, HB_IDLE))
    val paragraph = program.paragraphs[active]
    val current = exitFrameOf(program, active, time)
    val prevOut = if (active > 0) program.paragraphs[active - 1].transitionOut else null
    if (prevOut != null) {
        val enterDuration = hbEnterDuration(prevOut.kind, prevOut.startTime, prevOut.endTime)
        val progress = (time - paragraph.startTime) / enterDuration
        if (progress >= 0 && progress < 1) {
            val enter = hbTransitionFrame(prevOut.kind, false, progress)
            val weight = hbSmoothF(progress)
            val leaving = exitFrameOf(program, active - 1, time, true)
            val layers = ArrayList<HbLayerFrame>()
            if (leaving.alpha * (1 - weight) > 0.002) {
                layers += HbLayerFrame(active - 1, HbTransitionFrame(leaving.alpha * (1 - weight), leaving.scale, leaving.blur, leaving.y, leaving.rotation))
            }
            layers += HbLayerFrame(active, HbTransitionFrame(
                min(enter.alpha, weight) * current.alpha, enter.scale * current.scale, max(enter.blur, current.blur),
                enter.y + current.y, enter.rotation + current.rotation,
            ))
            return active to layers
        }
    }
    return active to listOf(HbLayerFrame(active, current))
}
