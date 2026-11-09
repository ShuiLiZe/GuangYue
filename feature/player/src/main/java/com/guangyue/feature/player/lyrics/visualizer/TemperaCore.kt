// 凝彩（Folia tempera）纯逻辑第一块：逐函数对译编排，不依赖 Android，可单测（金标 tempera_golden.txt）。
// 对应 Folia 文件：src/components/visualizer/tempera/
//   types.ts（TEMPERA_SHOT_KINDS 121 种、TEMPERA_TRANSITION_KINDS、TEMPERA_DECOR_MOTIFS）、
//   temperaShotProfiles.ts（每种构图的排版区域 / 入场向量 / 镜头位移 / mood / sharedDecor，表由 node 从源码导出，原样照搬）、
//   temperaRandom.ts（= visualizerSeedHash，复用 sonnetHash / sonnetMix / sonnetHash01）、
//   temperaProgram.ts（段落 → shot → slice 编译、bridge shot、段落转场、预卷）。
// 分词同商籁：保存的分词 → 自动分词 → BreakIterator（ICU，与 Intl.Segmenter 同源）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── 构图表（temperaShotProfiles.ts） ─────────────────────────────

internal enum class TpAlign { CENTER, LEFT, RIGHT }
internal enum class TpMood { QUIET, NEUTRAL, LOUD }

/** 歌词排版框，单位是视口比例。 */
internal class TpRegion(
    val cx: Double, val cy: Double, val w: Double, val h: Double,
    val align: TpAlign, val rotation: Double, val fontScale: Double,
)

internal class TpProfile(
    val kind: String,
    val region: TpRegion,
    /** 入场向量（字号倍数）。 */
    val enterX: Double,
    val enterY: Double,
    val travel: Double,
    val zoomStart: Double,
    val zoomEnd: Double,
    val mood: TpMood,
    val sharedDecor: Boolean,
)

/** TEMPERA_SHOT_KINDS 的顺序（chooseWithoutRepeat 依赖这个顺序）。 */
internal val TEMPERA_PROFILES: List<TpProfile> = listOf(
    TpProfile("duo-split", TpRegion(0.5, 0.52, 0.86, 0.46, TpAlign.CENTER, 0.0, 1.0), 0.0, 1.3, 0.11, 1.06, 1.13, TpMood.NEUTRAL, true),
    TpProfile("quad-split", TpRegion(0.5, 0.5, 0.82, 0.4, TpAlign.CENTER, 0.0, 1.0), 0.9, 0.9, 0.09, 1.08, 1.16, TpMood.LOUD, true),
    TpProfile("tri-column", TpRegion(0.5, 0.5, 0.7, 0.5, TpAlign.CENTER, 0.0, 0.95), -1.2, 0.0, 0.12, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("thirds-stack", TpRegion(0.5, 0.5, 0.8, 0.28, TpAlign.CENTER, 0.0, 1.0), 0.0, 1.1, 0.13, 1.03, 1.11, TpMood.NEUTRAL, true),
    TpProfile("checker-quad", TpRegion(0.5, 0.5, 0.76, 0.36, TpAlign.CENTER, 0.0, 1.0), 0.8, -0.8, 0.1, 1.1, 1.02, TpMood.LOUD, true),
    TpProfile("corner-wedge", TpRegion(0.44, 0.56, 0.68, 0.4, TpAlign.LEFT, -0.03, 1.0), -1.4, 0.5, 0.1, 1.05, 1.14, TpMood.LOUD, true),
    TpProfile("diagonal-halves", TpRegion(0.5, 0.5, 0.78, 0.4, TpAlign.CENTER, -0.075, 1.0), 1.1, 1.1, 0.12, 1.06, 1.14, TpMood.NEUTRAL, true),
    TpProfile("cross-axis", TpRegion(0.5, 0.5, 0.66, 0.3, TpAlign.CENTER, 0.0, 1.0), 0.0, 1.2, 0.08, 1.12, 1.03, TpMood.LOUD, true),
    TpProfile("offset-halves", TpRegion(0.5, 0.5, 0.8, 0.4, TpAlign.CENTER, 0.0, 1.0), 0.9, 0.6, 0.11, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("stair-blocks", TpRegion(0.5, 0.5, 0.72, 0.38, TpAlign.CENTER, -0.03, 1.0), -1.0, 0.8, 0.12, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("pillar-gap", TpRegion(0.5, 0.5, 0.34, 0.62, TpAlign.CENTER, 0.0, 0.8), 0.0, 1.2, 0.07, 1.1, 1.02, TpMood.LOUD, true),
    TpProfile("corner-quad", TpRegion(0.46, 0.46, 0.68, 0.36, TpAlign.CENTER, 0.0, 1.0), -0.9, -0.9, 0.1, 1.07, 1.15, TpMood.NEUTRAL, true),
    TpProfile("sliver-stack", TpRegion(0.5, 0.5, 0.76, 0.3, TpAlign.CENTER, 0.0, 1.0), 1.2, 0.0, 0.13, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("band-strip", TpRegion(0.5, 0.52, 0.78, 0.26, TpAlign.CENTER, 0.0, 0.92), 0.5, 1.05, 0.12, 1.03, 1.1, TpMood.NEUTRAL, true),
    TpProfile("horizon-band", TpRegion(0.5, 0.36, 0.8, 0.3, TpAlign.CENTER, 0.0, 1.0), 0.0, -1.1, 0.14, 1.02, 1.1, TpMood.NEUTRAL, true),
    TpProfile("deep-dive", TpRegion(0.5, 0.58, 0.76, 0.34, TpAlign.CENTER, 0.0, 1.0), 0.0, 1.6, 0.16, 1.04, 1.14, TpMood.NEUTRAL, true),
    TpProfile("tone-ramp", TpRegion(0.5, 0.5, 0.82, 0.38, TpAlign.CENTER, 0.0, 1.0), 1.2, 0.4, 0.11, 1.05, 1.12, TpMood.NEUTRAL, true),
    TpProfile("double-band", TpRegion(0.5, 0.5, 0.78, 0.22, TpAlign.CENTER, 0.0, 0.88), 0.8, 0.0, 0.12, 1.03, 1.11, TpMood.NEUTRAL, true),
    TpProfile("tilt-band", TpRegion(0.5, 0.5, 0.8, 0.26, TpAlign.CENTER, -0.1, 1.0), -1.1, 0.5, 0.13, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("edge-rails", TpRegion(0.5, 0.5, 0.74, 0.4, TpAlign.CENTER, 0.0, 1.0), 0.0, 1.0, 0.09, 1.02, 1.09, TpMood.QUIET, true),
    TpProfile("gradient-wall", TpRegion(0.5, 0.44, 0.82, 0.36, TpAlign.CENTER, 0.0, 1.0), 0.5, -0.9, 0.15, 1.04, 1.13, TpMood.NEUTRAL, true),
    TpProfile("terrace", TpRegion(0.46, 0.52, 0.72, 0.34, TpAlign.LEFT, 0.0, 1.0), -1.2, 0.4, 0.14, 1.05, 1.12, TpMood.NEUTRAL, true),
    TpProfile("frame-window", TpRegion(0.5, 0.5, 0.64, 0.5, TpAlign.CENTER, 0.0, 0.95), 0.0, 0.95, 0.05, 1.14, 1.03, TpMood.NEUTRAL, true),
    TpProfile("double-frame", TpRegion(0.5, 0.5, 0.58, 0.4, TpAlign.CENTER, 0.0, 0.9), 0.6, 0.6, 0.06, 1.12, 1.02, TpMood.NEUTRAL, true),
    TpProfile("circle-window", TpRegion(0.5, 0.5, 0.5, 0.34, TpAlign.CENTER, 0.0, 0.88), 0.0, 0.8, 0.05, 1.16, 1.04, TpMood.QUIET, true),
    TpProfile("ladder-frame", TpRegion(0.52, 0.5, 0.6, 0.4, TpAlign.LEFT, 0.0, 0.9), -0.9, 0.6, 0.07, 1.1, 1.02, TpMood.QUIET, true),
    TpProfile("corner-brackets", TpRegion(0.5, 0.5, 0.56, 0.3, TpAlign.CENTER, 0.0, 0.85), 0.0, 0.6, 0.04, 1.06, 1.01, TpMood.QUIET, true),
    TpProfile("inset-box", TpRegion(0.5, 0.5, 0.6, 0.42, TpAlign.CENTER, 0.0, 0.92), 0.0, 0.8, 0.06, 1.1, 1.02, TpMood.QUIET, true),
    TpProfile("bracket-pair", TpRegion(0.5, 0.5, 0.54, 0.34, TpAlign.CENTER, 0.0, 0.88), 1.0, 0.0, 0.05, 1.08, 1.01, TpMood.QUIET, true),
    TpProfile("arch-window", TpRegion(0.5, 0.54, 0.5, 0.34, TpAlign.CENTER, 0.0, 0.86), 0.0, 0.9, 0.06, 1.14, 1.03, TpMood.QUIET, true),
    TpProfile("grid-cells", TpRegion(0.5, 0.5, 0.72, 0.22, TpAlign.CENTER, 0.0, 0.8), 0.7, 0.7, 0.07, 1.06, 1.13, TpMood.NEUTRAL, true),
    TpProfile("keyhole", TpRegion(0.5, 0.6, 0.42, 0.3, TpAlign.CENTER, 0.0, 0.78), 0.0, 1.0, 0.05, 1.12, 1.02, TpMood.QUIET, true),
    TpProfile("poster-panel", TpRegion(0.4, 0.5, 0.58, 0.62, TpAlign.LEFT, -0.045, 1.0), -1.5, 0.35, 0.08, 1.05, 1.12, TpMood.LOUD, true),
    TpProfile("diamond-stack", TpRegion(0.54, 0.5, 0.6, 0.42, TpAlign.RIGHT, 0.035, 1.0), 1.3, -0.5, 0.09, 1.07, 1.15, TpMood.LOUD, true),
    TpProfile("slash-poster", TpRegion(0.46, 0.5, 0.66, 0.44, TpAlign.LEFT, -0.09, 1.0), -1.2, 1.0, 0.11, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("arrow-wedge", TpRegion(0.5, 0.34, 0.72, 0.26, TpAlign.CENTER, 0.0, 1.0), 0.0, -1.2, 0.13, 1.04, 1.13, TpMood.LOUD, true),
    TpProfile("edge-bleed", TpRegion(0.56, 0.5, 0.6, 0.44, TpAlign.LEFT, 0.0, 1.0), 1.4, 0.3, 0.1, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("triangle-mass", TpRegion(0.5, 0.42, 0.68, 0.3, TpAlign.CENTER, 0.0, 1.0), 0.8, -1.0, 0.12, 1.05, 1.14, TpMood.LOUD, true),
    TpProfile("ribbon-cross", TpRegion(0.5, 0.5, 0.62, 0.3, TpAlign.CENTER, 0.05, 1.0), -1.0, -0.8, 0.11, 1.08, 1.16, TpMood.LOUD, true),
    TpProfile("half-disc", TpRegion(0.44, 0.44, 0.6, 0.34, TpAlign.LEFT, 0.0, 1.0), -1.3, 0.4, 0.1, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("stacked-slabs", TpRegion(0.52, 0.5, 0.62, 0.4, TpAlign.CENTER, -0.05, 1.0), 1.1, 0.6, 0.1, 1.07, 1.15, TpMood.LOUD, true),
    TpProfile("wedge-pair", TpRegion(0.5, 0.5, 0.44, 0.44, TpAlign.CENTER, 0.0, 0.82), 0.0, 1.1, 0.09, 1.1, 1.02, TpMood.LOUD, true),
    TpProfile("quiet-line", TpRegion(0.5, 0.5, 0.6, 0.28, TpAlign.CENTER, 0.0, 0.58), 0.0, 0.7, 0.03, 1.0, 1.04, TpMood.QUIET, true),
    TpProfile("starfield-dots", TpRegion(0.5, 0.5, 0.62, 0.26, TpAlign.CENTER, 0.0, 0.66), 0.4, 0.5, 0.04, 1.02, 1.08, TpMood.QUIET, true),
    TpProfile("ripple-lines", TpRegion(0.5, 0.46, 0.66, 0.28, TpAlign.CENTER, 0.0, 0.72), 0.0, 0.9, 0.06, 1.03, 1.1, TpMood.QUIET, true),
    TpProfile("hair-grid", TpRegion(0.5, 0.5, 0.64, 0.26, TpAlign.CENTER, 0.0, 0.62), 0.4, 0.6, 0.04, 1.01, 1.06, TpMood.QUIET, true),
    TpProfile("margin-rule", TpRegion(0.54, 0.5, 0.62, 0.26, TpAlign.LEFT, 0.0, 0.66), -0.8, 0.3, 0.05, 1.02, 1.07, TpMood.QUIET, true),
    TpProfile("dot-drift", TpRegion(0.5, 0.48, 0.6, 0.26, TpAlign.CENTER, 0.0, 0.68), 0.6, 0.6, 0.05, 1.03, 1.09, TpMood.QUIET, true),
    TpProfile("arc-sweep", TpRegion(0.5, 0.5, 0.6, 0.26, TpAlign.CENTER, 0.0, 0.7), 0.0, 0.8, 0.06, 1.04, 1.1, TpMood.QUIET, true),
    TpProfile("blank-page", TpRegion(0.5, 0.5, 0.56, 0.24, TpAlign.CENTER, 0.0, 0.6), 0.0, 0.5, 0.03, 1.0, 1.03, TpMood.QUIET, true),
    TpProfile("cinema-scope", TpRegion(0.5, 0.5, 0.74, 0.22, TpAlign.CENTER, 0.0, 0.86), 0.9, 0.0, 0.09, 1.04, 1.11, TpMood.NEUTRAL, true),
    TpProfile("cinema-wide", TpRegion(0.5, 0.5, 0.7, 0.3, TpAlign.CENTER, 0.0, 0.9), 0.0, 0.9, 0.08, 1.06, 1.13, TpMood.NEUTRAL, true),
    TpProfile("cinema-academy", TpRegion(0.5, 0.5, 0.5, 0.4, TpAlign.CENTER, 0.0, 0.88), 0.0, 0.8, 0.06, 1.1, 1.02, TpMood.QUIET, true),
    TpProfile("cinema-square", TpRegion(0.5, 0.5, 0.42, 0.4, TpAlign.CENTER, 0.0, 0.84), 0.7, 0.7, 0.05, 1.12, 1.03, TpMood.QUIET, true),
    TpProfile("cinema-portrait", TpRegion(0.5, 0.5, 0.32, 0.46, TpAlign.CENTER, 0.0, 0.8), 0.0, 1.0, 0.06, 1.08, 1.16, TpMood.NEUTRAL, true),
    TpProfile("cinema-tall", TpRegion(0.5, 0.5, 0.24, 0.5, TpAlign.CENTER, 0.0, 0.72), 0.8, 0.4, 0.07, 1.05, 1.14, TpMood.NEUTRAL, true),
    TpProfile("cinema-twin", TpRegion(0.33, 0.5, 0.3, 0.32, TpAlign.CENTER, 0.0, 0.76), -0.9, 0.3, 0.08, 1.06, 1.13, TpMood.NEUTRAL, true),
    TpProfile("bubble-drift", TpRegion(0.5, 0.5, 0.72, 0.36, TpAlign.CENTER, 0.0, 0.92), 0.5, 1.0, 0.09, 1.05, 1.12, TpMood.NEUTRAL, true),
    TpProfile("cloud-window", TpRegion(0.5, 0.52, 0.46, 0.3, TpAlign.CENTER, 0.0, 0.84), 0.0, 0.8, 0.05, 1.14, 1.03, TpMood.QUIET, true),
    TpProfile("heart-burst", TpRegion(0.5, 0.52, 0.5, 0.3, TpAlign.CENTER, 0.0, 0.95), 0.8, 0.8, 0.1, 1.08, 1.16, TpMood.LOUD, true),
    TpProfile("sparkle-field", TpRegion(0.5, 0.48, 0.64, 0.26, TpAlign.CENTER, 0.0, 0.7), 0.4, 0.6, 0.04, 1.02, 1.08, TpMood.QUIET, true),
    TpProfile("petal-arc", TpRegion(0.5, 0.6, 0.7, 0.3, TpAlign.CENTER, 0.0, 0.9), 0.0, 1.1, 0.1, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("scallop-band", TpRegion(0.5, 0.5, 0.76, 0.24, TpAlign.CENTER, 0.0, 0.88), 0.7, 0.0, 0.12, 1.03, 1.1, TpMood.NEUTRAL, true),
    TpProfile("ribbon-loop", TpRegion(0.5, 0.5, 0.68, 0.22, TpAlign.CENTER, 0.04, 0.86), -1.1, 0.6, 0.11, 1.07, 1.15, TpMood.LOUD, true),
    TpProfile("round-plate", TpRegion(0.5, 0.5, 0.56, 0.36, TpAlign.CENTER, 0.0, 0.86), 0.0, 0.7, 0.05, 1.1, 1.02, TpMood.QUIET, true),
    TpProfile("halo-burst", TpRegion(0.5, 0.5, 0.46, 0.3, TpAlign.CENTER, 0.0, 0.95), 0.0, 0.9, 0.08, 1.12, 1.02, TpMood.LOUD, true),
    TpProfile("iris-hole", TpRegion(0.28, 0.68, 0.44, 0.28, TpAlign.CENTER, 0.0, 0.78), -1.0, 0.6, 0.09, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("slot-rail", TpRegion(0.5, 0.66, 0.72, 0.3, TpAlign.CENTER, 0.0, 0.9), 0.6, 0.9, 0.12, 1.04, 1.11, TpMood.NEUTRAL, true),
    TpProfile("punch-row", TpRegion(0.5, 0.5, 0.76, 0.34, TpAlign.CENTER, 0.0, 0.92), 1.1, 0.0, 0.13, 1.03, 1.1, TpMood.NEUTRAL, true),
    TpProfile("film-gate", TpRegion(0.5, 0.82, 0.66, 0.2, TpAlign.CENTER, 0.0, 0.8), 0.0, 1.1, 0.08, 1.08, 1.02, TpMood.LOUD, true),
    TpProfile("cross-vent", TpRegion(0.5, 0.82, 0.66, 0.2, TpAlign.CENTER, 0.0, 0.8), 0.0, 1.0, 0.07, 1.1, 1.02, TpMood.LOUD, true),
    TpProfile("louvre-slats", TpRegion(0.5, 0.8, 0.7, 0.22, TpAlign.CENTER, 0.0, 0.82), 0.8, 0.7, 0.11, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("ring-eye", TpRegion(0.5, 0.5, 0.26, 0.18, TpAlign.CENTER, 0.0, 0.62), 0.0, 0.7, 0.05, 1.14, 1.03, TpMood.QUIET, true),
    TpProfile("notch-stack", TpRegion(0.32, 0.5, 0.5, 0.34, TpAlign.CENTER, 0.0, 0.84), -1.1, 0.5, 0.1, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("wedge-gap", TpRegion(0.5, 0.78, 0.7, 0.24, TpAlign.CENTER, 0.0, 0.82), 0.0, 1.2, 0.12, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("dot-sieve", TpRegion(0.75, 0.5, 0.38, 0.3, TpAlign.CENTER, 0.0, 0.78), 0.9, 0.4, 0.06, 1.03, 1.09, TpMood.QUIET, true),
    TpProfile("sight-mark", TpRegion(0.5, 0.5, 0.46, 0.2, TpAlign.CENTER, 0.0, 0.85), 0.0, 0.8, 0.06, 1.12, 1.02, TpMood.LOUD, true),
    TpProfile("dial-scale", TpRegion(0.5, 0.5, 0.36, 0.2, TpAlign.CENTER, 0.0, 0.8), 0.7, 0.5, 0.05, 1.1, 1.02, TpMood.NEUTRAL, true),
    TpProfile("chevron-run", TpRegion(0.5, 0.52, 0.72, 0.3, TpAlign.CENTER, 0.0, 0.9), 0.0, -1.1, 0.14, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("tally-column", TpRegion(0.38, 0.5, 0.6, 0.32, TpAlign.LEFT, 0.0, 0.76), -0.9, 0.3, 0.05, 1.02, 1.08, TpMood.QUIET, true),
    TpProfile("grid-focus", TpRegion(0.5, 0.5, 0.62, 0.22, TpAlign.CENTER, 0.0, 0.85), 0.8, 0.6, 0.09, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("axis-caps", TpRegion(0.5, 0.72, 0.66, 0.24, TpAlign.CENTER, 0.0, 0.82), 0.0, 1.2, 0.13, 1.06, 1.15, TpMood.LOUD, true),
    TpProfile("strobe-slats", TpRegion(0.5, 0.5, 0.3, 0.36, TpAlign.CENTER, 0.0, 0.8), 1.0, 0.0, 0.1, 1.08, 1.16, TpMood.LOUD, true),
    TpProfile("offset-plate", TpRegion(0.48, 0.62, 0.4, 0.2, TpAlign.CENTER, 0.0, 0.85), -0.9, -0.6, 0.09, 1.07, 1.15, TpMood.LOUD, true),
    TpProfile("radial-comb", TpRegion(0.42, 0.4, 0.62, 0.3, TpAlign.CENTER, 0.0, 0.88), -1.2, -0.4, 0.11, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("bracket-target", TpRegion(0.5, 0.28, 0.66, 0.24, TpAlign.CENTER, 0.0, 0.85), 0.0, -0.9, 0.06, 1.04, 1.1, TpMood.QUIET, true),
    TpProfile("flow-channel", TpRegion(0.32, 0.5, 0.5, 0.3, TpAlign.CENTER, 0.0, 0.85), -1.0, 0.5, 0.13, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("twin-channel", TpRegion(0.5, 0.5, 0.4, 0.3, TpAlign.CENTER, 0.0, 0.85), 0.0, 1.2, 0.14, 1.03, 1.11, TpMood.NEUTRAL, true),
    TpProfile("reed-run", TpRegion(0.5, 0.5, 0.3, 0.3, TpAlign.CENTER, 0.0, 0.72), 0.0, 0.9, 0.1, 1.02, 1.09, TpMood.QUIET, true),
    TpProfile("taper-channel", TpRegion(0.28, 0.5, 0.42, 0.3, TpAlign.CENTER, 0.0, 0.82), -1.1, 0.6, 0.12, 1.05, 1.14, TpMood.NEUTRAL, true),
    TpProfile("chain-ports", TpRegion(0.32, 0.5, 0.5, 0.32, TpAlign.CENTER, 0.0, 0.8), -0.8, 0.7, 0.11, 1.03, 1.1, TpMood.QUIET, true),
    TpProfile("dash-channel", TpRegion(0.3, 0.5, 0.46, 0.3, TpAlign.CENTER, 0.0, 0.84), 0.0, 1.3, 0.15, 1.04, 1.13, TpMood.NEUTRAL, true),
    TpProfile("window-run", TpRegion(0.28, 0.5, 0.42, 0.3, TpAlign.CENTER, 0.0, 0.82), 0.0, 1.2, 0.14, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("bridge-span", TpRegion(0.5, 0.5, 0.72, 0.16, TpAlign.CENTER, 0.0, 0.85), 1.2, 0.0, 0.12, 1.08, 1.16, TpMood.LOUD, true),
    TpProfile("braid-channel", TpRegion(0.5, 0.5, 0.34, 0.3, TpAlign.CENTER, 0.0, 0.8), 0.0, 1.1, 0.13, 1.06, 1.15, TpMood.LOUD, true),
    TpProfile("port-ladder", TpRegion(0.3, 0.5, 0.46, 0.3, TpAlign.CENTER, 0.0, 0.78), -0.9, 0.4, 0.09, 1.02, 1.09, TpMood.QUIET, true),
    TpProfile("apex-mass", TpRegion(0.5, 0.3, 0.62, 0.2, TpAlign.CENTER, 0.0, 0.9), 0.0, -1.0, 0.12, 1.04, 1.13, TpMood.LOUD, true),
    TpProfile("ziggurat", TpRegion(0.5, 0.32, 0.5, 0.18, TpAlign.CENTER, 0.0, 0.85), 0.0, -0.9, 0.1, 1.06, 1.15, TpMood.LOUD, true),
    TpProfile("slab-wall", TpRegion(0.36, 0.5, 0.5, 0.24, TpAlign.CENTER, 0.0, 0.88), 1.2, 0.0, 0.11, 1.05, 1.12, TpMood.NEUTRAL, true),
    TpProfile("cantilever", TpRegion(0.42, 0.42, 0.6, 0.14, TpAlign.CENTER, 0.0, 0.8), -1.3, 0.0, 0.13, 1.04, 1.12, TpMood.LOUD, true),
    TpProfile("pylon-pair", TpRegion(0.5, 0.22, 0.7, 0.12, TpAlign.CENTER, 0.0, 0.75), 0.0, -0.8, 0.09, 1.08, 1.02, TpMood.LOUD, true),
    TpProfile("bunker-slit", TpRegion(0.5, 0.48, 0.72, 0.2, TpAlign.CENTER, 0.0, 0.86), 0.9, 0.4, 0.08, 1.06, 1.13, TpMood.NEUTRAL, true),
    TpProfile("plinth-stack", TpRegion(0.5, 0.32, 0.44, 0.16, TpAlign.CENTER, 0.0, 0.82), 0.0, -0.8, 0.1, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("buttress-run", TpRegion(0.5, 0.26, 0.7, 0.2, TpAlign.CENTER, 0.0, 0.86), 0.8, -0.8, 0.12, 1.03, 1.11, TpMood.LOUD, true),
    TpProfile("void-core", TpRegion(0.5, 0.2, 0.66, 0.18, TpAlign.CENTER, 0.0, 0.84), 0.0, -1.1, 0.11, 1.07, 1.15, TpMood.LOUD, true),
    TpProfile("shear-block", TpRegion(0.5, 0.5, 0.56, 0.2, TpAlign.CENTER, 0.0, 0.88), 1.0, 0.5, 0.12, 1.06, 1.14, TpMood.NEUTRAL, true),
    TpProfile("ridge-line", TpRegion(0.5, 0.42, 0.72, 0.2, TpAlign.CENTER, 0.0, 0.9), 0.0, -1.0, 0.14, 1.03, 1.12, TpMood.NEUTRAL, true),
    TpProfile("chasm", TpRegion(0.25, 0.5, 0.38, 0.24, TpAlign.CENTER, 0.0, 0.82), -1.2, 0.4, 0.1, 1.06, 1.14, TpMood.LOUD, true),
    TpProfile("overhang", TpRegion(0.44, 0.58, 0.6, 0.22, TpAlign.CENTER, 0.0, 0.88), 0.0, 1.1, 0.11, 1.08, 1.02, TpMood.LOUD, true),
    TpProfile("step-well", TpRegion(0.5, 0.26, 0.66, 0.2, TpAlign.CENTER, 0.0, 0.85), 0.0, -0.9, 0.09, 1.04, 1.12, TpMood.NEUTRAL, true),
    TpProfile("pier-row", TpRegion(0.5, 0.4, 0.76, 0.14, TpAlign.CENTER, 0.0, 0.82), 1.1, 0.0, 0.13, 1.03, 1.1, TpMood.NEUTRAL, true),
    TpProfile("revetment", TpRegion(0.46, 0.3, 0.6, 0.2, TpAlign.CENTER, 0.0, 0.86), -1.0, -0.6, 0.12, 1.05, 1.13, TpMood.NEUTRAL, true),
    TpProfile("tower-crop", TpRegion(0.28, 0.5, 0.44, 0.24, TpAlign.LEFT, 0.0, 0.74), -0.8, 0.3, 0.06, 1.02, 1.09, TpMood.QUIET, true),
    TpProfile("lintel", TpRegion(0.5, 0.5, 0.76, 0.16, TpAlign.CENTER, 0.0, 0.9), -1.1, 0.0, 0.1, 1.02, 1.08, TpMood.QUIET, true),
    TpProfile("rubble-fan", TpRegion(0.56, 0.4, 0.5, 0.22, TpAlign.CENTER, 0.0, 0.84), 1.2, -0.5, 0.13, 1.07, 1.16, TpMood.LOUD, true),
    TpProfile("gnomon", TpRegion(0.66, 0.34, 0.46, 0.2, TpAlign.CENTER, 0.0, 0.78), 0.9, -0.4, 0.07, 1.03, 1.1, TpMood.QUIET, true),
    TpProfile("monogatari-card", TpRegion(0.5, 0.5, 0.78, 0.5, TpAlign.CENTER, 0.0, 1.15), 0.0, 0.7, 0.03, 1.02, 1.07, TpMood.QUIET, false),
    TpProfile("monogatari-rule", TpRegion(0.5, 0.46, 0.76, 0.4, TpAlign.CENTER, 0.0, 1.05), 0.6, 0.0, 0.04, 1.03, 1.09, TpMood.QUIET, false),
    TpProfile("monogatari-edge", TpRegion(0.52, 0.5, 0.72, 0.46, TpAlign.LEFT, 0.0, 1.05), -0.8, 0.0, 0.05, 1.02, 1.08, TpMood.NEUTRAL, false),
    TpProfile("monogatari-stack", TpRegion(0.5, 0.5, 0.44, 0.62, TpAlign.CENTER, 0.0, 1.0), 0.0, 0.9, 0.04, 1.05, 1.12, TpMood.QUIET, false),
    TpProfile("monogatari-flash", TpRegion(0.5, 0.5, 0.82, 0.44, TpAlign.CENTER, 0.0, 1.3), 0.0, 0.5, 0.02, 1.08, 1.01, TpMood.LOUD, false)
)

private val TEMPERA_PROFILE_BY_KIND: Map<String, TpProfile> = TEMPERA_PROFILES.associateBy { it.kind }

internal fun temperaShotProfile(kind: String): TpProfile = TEMPERA_PROFILE_BY_KIND[kind] ?: TEMPERA_PROFILES[0]

internal fun temperaShotCandidates(moods: Set<TpMood>): List<String> {
    val c = TEMPERA_PROFILES.filter { it.mood in moods }.map { it.kind }
    return c.ifEmpty { TEMPERA_PROFILES.map { it.kind } }
}

internal val TEMPERA_TRANSITION_KINDS = listOf("block-wipe", "camera-pan", "shape-carry")
internal val TEMPERA_DECOR_MOTIFS = listOf("diamonds", "hatch-twin", "band-cross", "poster-diamond", "doodle")

// ───────────────────────────── 类型（types.ts） ─────────────────────────────

internal enum class TpParagraphKind(val id: String) { BREATH("breath"), VERSE("verse"), LIFT("lift"), CHORUS("chorus"), BREAK("break"), OUTRO("outro") }
internal enum class TpBoundary(val id: String) { SONG_START("song-start"), TIME_GAP("time-gap"), METADATA("metadata"), DURATION_CAP("duration-cap"), LINE_CAP("line-cap") }

internal class TpCompiledLine(val sourceIndex: Int, val line: Line, val renderEndTime: Double, val segments: List<SonnetSegment>)
internal class TpCamera(val x: Double, val y: Double, val zoom: Double, val rotation: Double)
internal class TpFragment(val char: String, val x: Double, val y: Double, val rotation: Double, val scale: Double)
internal class TpWatermark(val text: String, val x: Double, val y: Double, val rotation: Double, val scale: Double)
internal class TpDecor(
    val motif: String,
    val hatchAngle: Double,
    val crossCount: Int,
    val scribbleSeed: Long,
    val fragments: List<TpFragment>,
    val watermark: TpWatermark?,
)
internal class TpSlice(val lineIndex: Int, val segmentStart: Int, val segmentEnd: Int)
internal class TpShot(
    val id: String,
    val kind: String,
    /** 预卷会把段首 shot 提前，所以可变。 */
    var startTime: Double,
    val endTime: Double,
    /** 本 shot 最后一个字唱完的时刻；按歌词节奏走的东西（色块 / 图片入场）用它，不用平铺出来的 endTime。 */
    val lyricEndTime: Double,
    val slices: List<TpSlice>,
    val isBridge: Boolean,
    val camera: TpCamera,
    val cameraEnd: TpCamera,
    /** 本 shot 图形的行进方向（弧度），相邻 shot 只小幅转向，以竖直为主轴。 */
    val flowAngle: Double,
    val decor: TpDecor,
)
internal class TpTransition(val kind: String, val startTime: Double, val endTime: Double)
internal class TpParagraph(
    val id: String,
    val kind: TpParagraphKind,
    val boundary: TpBoundary,
    val startTime: Double,
    val endTime: Double,
    val lines: List<TpCompiledLine>,
    val shots: List<TpShot>,
    val transitionOut: TpTransition?,
)
internal class TpProgram(val seed: String, val paragraphGapThreshold: Double, val paragraphs: List<TpParagraph>)

// ───────────────────────────── 编译（temperaProgram.ts） ─────────────────────────────

private fun tpClamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

private fun tpMedian(values: List<Double>): Double {
    if (values.isEmpty()) return 0.5
    val s = values.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2 else s[m]
}

internal fun temperaParagraphGapThreshold(lines: List<Line>): Double {
    val gaps = (1 until lines.size).map { i ->
        lines[i].startTime - min(RenderHints.getLineRenderEndTime(lines[i - 1]), lines[i].startTime)
    }.filter { it > 0 }
    return tpClamp(tpMedian(gaps) * 2.5, 1.25, 3.5)
}

private val TP_PUNCTUATION_ONLY = Regex("^[\\s\\p{P}\\p{S}]+$")
private val TP_WHITESPACE_ONLY = Regex("^\\s+$")
private val TP_SINGLE_HAN = Regex("^\\p{IsHan}$")

private fun tpMetadataChanged(previous: Line, next: Line): Boolean =
    (previous.blockIndex != null && next.blockIndex != null && previous.blockIndex != next.blockIndex) ||
        (previous.songPart != null && next.songPart != null && previous.songPart != next.songPart)

/**
 * buildTemperaSegments：词级片段 + 逐字时间；标点黏到前一个词上，且**只给零时长的标点重新计时**
 * （钉到前一个词的结束），否则逗号会跟着后一个词入场、还把本片段的结束拖到那里。
 */
internal fun buildTemperaSegments(line: Line, split: SonnetWordSplitter = defaultSonnetWordSplitter): List<SonnetSegment> {
    if (line.fullText.isEmpty()) return emptyList()
    val timeline = GraphemeTimings.buildDenseLineGraphemeTimeline(line)
    val graphemeStrings = GraphemeTimings.splitLyricGraphemes(line.fullText)
    val rangeStart = IntArray(graphemeStrings.size)
    val rangeEnd = IntArray(graphemeStrings.size)
    var cursor = 0
    graphemeStrings.forEachIndexed { i, g -> rangeStart[i] = cursor; cursor += g.length; rangeEnd[i] = cursor }
    val segments = ArrayList<SonnetSegment>()
    var offset = 0
    split(line).forEach { part ->
        val startOffset = offset
        val endOffset = offset + part.length
        offset = endOffset
        val gs = ArrayList<SonnetGrapheme>()
        for (i in graphemeStrings.indices) {
            if (rangeEnd[i] > startOffset && rangeStart[i] < endOffset) {
                val t = timeline.getOrNull(i) ?: continue
                gs += SonnetGrapheme(t.char, t.startTime, t.endTime)
            }
        }
        segments += SonnetSegment(
            text = line.fullText.substring(startOffset, min(endOffset, line.fullText.length)),
            startOffset = startOffset,
            endOffset = endOffset,
            startTime = gs.firstOrNull()?.startTime ?: line.startTime,
            endTime = gs.lastOrNull()?.endTime ?: line.endTime,
            graphemes = gs,
            isWordLike = !TP_PUNCTUATION_ONLY.matches(part),
        )
    }
    val sticky = ArrayList<SonnetSegment>()
    for (segment in segments) {
        val previous = sticky.lastOrNull()
        if (previous != null && !segment.isWordLike && !TP_WHITESPACE_ONLY.matches(segment.text)) {
            val tail = previous.endTime
            val merged = segment.graphemes.map { g -> if (g.endTime > g.startTime) g else g.copy(startTime = tail, endTime = tail) }
            sticky[sticky.size - 1] = previous.copy(
                text = previous.text + segment.text,
                endOffset = segment.endOffset,
                endTime = (listOf(previous.endTime) + merged.map { it.endTime }).max(),
                graphemes = previous.graphemes + merged,
            )
        } else sticky += segment
    }
    return sticky
}

private class TpDraft(val lines: List<TpCompiledLine>, val boundary: TpBoundary)

private fun tpSplitOversized(draft: TpDraft): List<TpDraft> {
    val output = ArrayList<TpDraft>()
    var remaining = draft.lines
    var boundary = draft.boundary
    var guard = 0
    while (remaining.size > 6 || (remaining.size > 1 && (remaining.last().renderEndTime - remaining[0].line.startTime) > 18)) {
        if (guard++ > 1000) break
        val candidates = ArrayList<Pair<Int, Double>>()
        for (k in 2 until remaining.size - 1) candidates += k to (remaining[k].line.startTime - remaining[k - 1].renderEndTime)
        val raw = candidates.filter { !it.second.isNaN() }.sortedByDescending { it.second }.firstOrNull()?.first ?: min(4, remaining.size - 1)
        val splitIndex = max(1, raw)
        output += TpDraft(remaining.subList(0, splitIndex), boundary)
        remaining = remaining.subList(splitIndex, remaining.size)
        boundary = if (output.last().lines.size >= 6) TpBoundary.LINE_CAP else TpBoundary.DURATION_CAP
    }
    output += TpDraft(remaining, boundary)
    return output
}

private val TP_CHORUS_RE = Regex("chorus|副歌", RegexOption.IGNORE_CASE)
private val TP_BREAK_RE = Regex("bridge|break|間奏|ブリッジ", RegexOption.IGNORE_CASE)
private val TP_EXCLAIM_RE = Regex("[!?！？…]")

private fun tpClassify(lines: List<TpCompiledLine>, index: Int, total: Int): TpParagraphKind {
    if (lines.any { it.line.isChorus || TP_CHORUS_RE.containsMatchIn(it.line.songPart ?: "") }) return TpParagraphKind.CHORUS
    if (lines.any { TP_BREAK_RE.containsMatchIn(it.line.songPart ?: "") }) return TpParagraphKind.BREAK
    if (index == total - 1) return TpParagraphKind.OUTRO
    val duration = lines.last().renderEndTime - lines[0].line.startTime
    val segmentCount = lines.sumOf { l -> l.segments.count { it.isWordLike } }
    val punctuationCount = lines.sumOf { TP_EXCLAIM_RE.findAll(it.line.fullText).count() }
    if (duration <= 3.5 || segmentCount <= 3) return TpParagraphKind.BREATH
    if (punctuationCount >= 2 || segmentCount / max(duration, 1.0) > 2.5) return TpParagraphKind.LIFT
    return TpParagraphKind.VERSE
}

/** temperaRandom.chooseWithoutRepeat。 */
internal fun <T> temperaChoose(choices: List<T>, seed: String, previous: T?): T {
    val start = (sonnetHash(seed) % choices.size).toInt()
    for (offset in choices.indices) {
        val candidate = choices[(start + offset) % choices.size]
        if (candidate != previous) return candidate
    }
    return choices[start]
}

private class TpChunk(val lineIndex: Int, val segmentStart: Int, val segmentEnd: Int, val startTime: Double, val endTime: Double, val lyricEndTime: Double)

private fun tpRenderable(s: SonnetSegment) = s.text.trim().isNotEmpty() && s.graphemes.isNotEmpty()

/** 单字中文词之间不切（我/想/你），粘连最多撑到 3.2s。 */
private const val SINGLE_HAN_GLUE_MAX_SECONDS = 3.2

private fun tpSingleHan(s: SonnetSegment) = s.isWordLike && TP_SINGLE_HAN.matches(s.text)

private fun tpBuildChunks(lines: List<TpCompiledLine>, seed: String, paragraphIndex: Int, wholeLine: Boolean): List<TpChunk> {
    val chunks = ArrayList<TpChunk>()
    lines.forEach { line ->
        val usable = line.segments.withIndex().filter { tpRenderable(it.value) }
        if (usable.isEmpty()) return@forEach
        if (wholeLine) {
            val first = usable[0]
            val last = usable.last()
            val end = max(last.value.endTime, first.value.startTime + 0.2)
            chunks += TpChunk(line.sourceIndex, first.index, last.index + 1, first.value.startTime, end, end)
            return@forEach
        }
        var segmentStart = usable[0].index
        var startTime = usable[0].value.startTime
        var words = 0
        usable.forEachIndexed { order, entry ->
            words += 1
            val chunkSeed = sonnetHash("$seed:$paragraphIndex:${line.sourceIndex}:${chunks.size}")
            val target = 2 + floor(sonnetHash01(chunkSeed, 1, 179) * 3).toInt()
            val spent = entry.value.endTime - startTime
            val isLast = order == usable.size - 1
            if (!isLast && words < target && spent < 2.2) return@forEachIndexed
            val next = usable.getOrNull(order + 1)
            if (!isLast && spent < SINGLE_HAN_GLUE_MAX_SECONDS && next != null && tpSingleHan(entry.value) &&
                tpSingleHan(next.value) && next.value.startOffset == entry.value.endOffset
            ) return@forEachIndexed
            val end = max(entry.value.endTime, startTime + 0.2)
            chunks += TpChunk(line.sourceIndex, segmentStart, entry.index + 1, startTime, end, end)
            if (next != null) {
                segmentStart = next.index
                startTime = next.value.startTime
                words = 0
            }
        }
    }
    // 平铺：每个 shot 一直演到下一个开场；lyricEndTime 不平铺。
    return chunks.mapIndexed { i, c ->
        TpChunk(c.lineIndex, c.segmentStart, c.segmentEnd, c.startTime, max(c.startTime + 0.2, chunks.getOrNull(i + 1)?.startTime ?: c.endTime), c.lyricEndTime)
    }
}

private fun tpCameraKeys(kind: String, seed: Long, flowAngle: Double): Pair<TpCamera, TpCamera> {
    val jx = (sonnetHash01(seed, 1, 11) - 0.5) * 0.02
    val jy = (sonnetHash01(seed, 2, 23) - 0.5) * 0.02
    val jz = sonnetHash01(seed, 3, 37) * 0.025
    val jr = (sonnetHash01(seed, 4, 51) - 0.5) * 0.012
    val p = temperaShotProfile(kind)
    val tx = cos(flowAngle) * p.travel
    val ty = sin(flowAngle) * p.travel
    return TpCamera(-tx / 2 + jx, -ty / 2 + jy, p.zoomStart + jz, jr) to TpCamera(tx / 2 + jx, ty / 2 + jy, p.zoomEnd + jz, -jr)
}

private fun tpCodePoints(s: String): List<String> = s.codePoints().toArray().map { String(Character.toChars(it)) }

private fun tpFragments(pool: String, count: Int, seed: Long): List<TpFragment> {
    val chars = tpCodePoints(pool).filter { it.isNotBlank() && !TP_PUNCTUATION_ONLY.matches(it) }
    if (chars.isEmpty() || count <= 0) return emptyList()
    return List(count) { i ->
        val ch = chars[floor(sonnetHash01(seed, i, 13) * chars.size).toInt() % chars.size]
        val onLeft = sonnetHash01(seed, i, 29) > 0.5
        val edge = 0.03 + sonnetHash01(seed, i, 41) * 0.13
        TpFragment(
            char = ch,
            x = if (onLeft) edge else 1 - edge,
            y = 0.08 + sonnetHash01(seed, i, 53) * 0.84,
            rotation = (sonnetHash01(seed, i, 67) - 0.5) * 0.5,
            scale = 0.26 + sonnetHash01(seed, i, 71) * 0.2,
        )
    }
}

private fun tpWatermark(pool: List<String>, seed: Long, allowed: Boolean): TpWatermark? {
    val words = pool.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 12 }
    if (!allowed || words.isEmpty() || sonnetHash01(seed, 4, 107) > 0.62) return null
    return TpWatermark(
        text = words[floor(sonnetHash01(seed, 5, 109) * words.size).toInt() % words.size],
        x = 0.28 + sonnetHash01(seed, 6, 113) * 0.44,
        y = 0.26 + sonnetHash01(seed, 7, 127) * 0.48,
        rotation = (sonnetHash01(seed, 8, 131) - 0.5) * 0.5,
        scale = 2.6 + sonnetHash01(seed, 9, 137) * 1.9,
    )
}

private fun tpDecor(paragraphKind: TpParagraphKind, shotKind: String, seedKey: String, fragmentPool: String, watermarkPool: List<String>, previousMotif: String?): TpDecor {
    val seed = sonnetHash(seedKey)
    val motif = temperaChoose(TEMPERA_DECOR_MOTIFS, seedKey, previousMotif)
    val mood = temperaShotProfile(shotKind).mood
    val sparse = mood == TpMood.QUIET || paragraphKind == TpParagraphKind.BREAK || paragraphKind == TpParagraphKind.OUTRO
    return TpDecor(
        motif = motif,
        hatchAngle = (sonnetHash01(seed, 1, 83) - 0.5) * (PI / 2),
        crossCount = 1 + floor(sonnetHash01(seed, 2, 89) * 3).toInt(),
        scribbleSeed = sonnetMix(seed, 97),
        fragments = if (sparse) tpFragments(fragmentPool, 3 + floor(sonnetHash01(seed, 3, 101) * 3).toInt(), seed) else emptyList(),
        watermark = tpWatermark(watermarkPool, seed, mood != TpMood.LOUD),
    )
}

/** 竖直是凝彩的主轴：每个 shot 小幅转向，再往轴上拉回。 */
private fun tpFlowAngle(previous: Double?, seed: Long): Double {
    if (previous == null) {
        val sign = if (sonnetHash01(seed, 6, 71) > 0.5) 1 else -1
        return sign * PI / 2 + (sonnetHash01(seed, 8, 79) - 0.5) * 0.4
    }
    val axis = (if (sin(previous) >= 0) 1 else -1) * PI / 2
    return previous + (axis - previous) * 0.3 + (sonnetHash01(seed, 7, 73) - 0.5) * 0.5
}

private class TpCarry(var kind: String?, var motif: String?, var flow: Double?)

private fun tpBuildShots(
    lines: List<TpCompiledLine>,
    songLines: List<TpCompiledLine>,
    kind: TpParagraphKind,
    paragraphIndex: Int,
    seed: String,
    carry: TpCarry,
    wholeLine: Boolean,
): List<TpShot> {
    val paragraphEnd = lines.lastOrNull()?.renderEndTime ?: 0.0
    val chunks = tpBuildChunks(lines, seed, paragraphIndex, wholeLine)
    val byIndex = lines.associateBy { it.sourceIndex }
    return chunks.mapIndexed { shotIndex, chunk ->
        val line = byIndex[chunk.lineIndex]
        val sliceSegments = line?.segments?.subList(chunk.segmentStart, chunk.segmentEnd) ?: emptyList()
        val sliceText = sliceSegments.joinToString("") { it.text }
        val wordCount = sliceSegments.count { it.isWordLike }
        val sparse = kind == TpParagraphKind.BREATH || (kind != TpParagraphKind.CHORUS && wordCount <= 2)
        val moods = when {
            sparse -> setOf(TpMood.QUIET)
            kind == TpParagraphKind.CHORUS -> setOf(TpMood.NEUTRAL, TpMood.LOUD)
            else -> setOf(TpMood.QUIET, TpMood.NEUTRAL, TpMood.LOUD)
        }
        val shotKind = temperaChoose(temperaShotCandidates(moods), "$seed:$paragraphIndex:$shotIndex:$sliceText", carry.kind)
        carry.kind = shotKind
        val cameraSeed = sonnetHash("$seed:$paragraphIndex:$shotIndex:camera")
        val flowAngle = tpFlowAngle(carry.flow, cameraSeed)
        carry.flow = flowAngle
        val (start, end) = tpCameraKeys(shotKind, cameraSeed, flowAngle)
        val outsideSlice = lines.flatMap { item ->
            if (item.sourceIndex == chunk.lineIndex) item.segments.filterIndexed { i, _ -> i < chunk.segmentStart || i >= chunk.segmentEnd }
            else item.segments
        }
        val decorPool = outsideSlice.ifEmpty { songLines.flatMap { item -> if (item.sourceIndex == chunk.lineIndex) emptyList() else item.segments } }
        val decor = tpDecor(
            kind, shotKind, "$seed:$paragraphIndex:$shotIndex:decor",
            decorPool.joinToString("") { it.text },
            decorPool.filter { it.isWordLike }.map { it.text },
            carry.motif,
        )
        carry.motif = decor.motif
        TpShot(
            id = "p$paragraphIndex-s$shotIndex",
            kind = shotKind,
            startTime = chunk.startTime,
            endTime = if (shotIndex == chunks.size - 1) max(chunk.endTime, paragraphEnd) else chunk.endTime,
            lyricEndTime = chunk.lyricEndTime,
            slices = listOf(TpSlice(chunk.lineIndex, chunk.segmentStart, chunk.segmentEnd)),
            isBridge = false,
            camera = start,
            cameraEnd = end,
            flowAngle = flowAngle,
            decor = decor,
        )
    }
}

private const val BRIDGE_MIN_GAP = 1.2
private const val BRIDGE_MAX_LENGTH = 5.0

private fun tpBridgeShots(
    paragraphKind: TpParagraphKind,
    paragraphIndex: Int,
    seed: String,
    fragmentPool: String,
    gapStart: Double,
    gapEnd: Double,
    carry: TpCarry,
): List<TpShot> {
    val gap = gapEnd - gapStart
    if (gap < BRIDGE_MIN_GAP) return emptyList()
    val count = min(3, max(1, ceil(gap / BRIDGE_MAX_LENGTH).toInt()))
    val step = gap / count
    return List(count) { index ->
        val shotKind = temperaChoose(temperaShotCandidates(setOf(TpMood.QUIET, TpMood.NEUTRAL)), "$seed:$paragraphIndex:bridge$index", carry.kind)
        carry.kind = shotKind
        val cameraSeed = sonnetHash("$seed:$paragraphIndex:bridge$index:camera")
        val flowAngle = tpFlowAngle(carry.flow, cameraSeed)
        carry.flow = flowAngle
        val (start, end) = tpCameraKeys(shotKind, cameraSeed, flowAngle)
        val decor = tpDecor(paragraphKind, shotKind, "$seed:$paragraphIndex:bridge$index:decor", fragmentPool, emptyList(), carry.motif)
        carry.motif = decor.motif
        TpShot(
            id = "p$paragraphIndex-b$index",
            kind = shotKind,
            startTime = gapStart + step * index,
            endTime = gapStart + step * (index + 1),
            lyricEndTime = gapStart + step * (index + 1),
            slices = emptyList(),
            isBridge = true,
            camera = start,
            cameraEnd = end,
            flowAngle = flowAngle,
            decor = decor,
        )
    }
}

internal fun compileTemperaCoreProgram(
    lines: List<Line>,
    seed: String = "tempera",
    wholeLineLyrics: Boolean = false,
    split: SonnetWordSplitter = defaultSonnetWordSplitter,
): TpProgram {
    val compiled = lines.mapIndexed { i, line ->
        TpCompiledLine(
            sourceIndex = i,
            line = line,
            renderEndTime = max(line.startTime, min(RenderHints.getLineRenderEndTime(line), lines.getOrNull(i + 1)?.startTime ?: Double.POSITIVE_INFINITY)),
            segments = buildTemperaSegments(line, split),
        )
    }
    val threshold = temperaParagraphGapThreshold(lines)
    val drafts = ArrayList<TpDraft>()
    var current = ArrayList<TpCompiledLine>()
    var currentBoundary = TpBoundary.SONG_START
    compiled.forEachIndexed { index, line ->
        val previous = compiled.getOrNull(index - 1)
        val gap = if (previous != null) line.line.startTime - previous.renderEndTime else 0.0
        val boundary = when {
            previous != null && tpMetadataChanged(previous.line, line.line) -> TpBoundary.METADATA
            previous != null && gap >= threshold -> TpBoundary.TIME_GAP
            else -> null
        }
        if (boundary != null && current.isNotEmpty()) {
            drafts += tpSplitOversized(TpDraft(current, currentBoundary))
            current = ArrayList()
            currentBoundary = boundary
        }
        current += line
    }
    if (current.isNotEmpty()) drafts += tpSplitOversized(TpDraft(current, currentBoundary))

    val carry = TpCarry(null, null, null)
    var previousTransition: String? = null
    val paragraphs = drafts.mapIndexed { index, draft ->
        val kind = tpClassify(draft.lines, index, drafts.size)
        val lyricShots = tpBuildShots(draft.lines, compiled, kind, index, seed, carry, wholeLineLyrics)
        lyricShots.lastOrNull()?.let { carry.kind = it.kind; carry.motif = it.decor.motif; carry.flow = it.flowAngle }
        val next = drafts.getOrNull(index + 1)
        val endTime = draft.lines.last().renderEndTime
        val gap = if (next != null) next.lines[0].line.startTime - endTime else 0.0
        val bridgeShots = if (next != null) tpBridgeShots(
            kind, index, seed, draft.lines.joinToString("") { it.line.fullText }, endTime, next.lines[0].line.startTime, carry,
        ) else emptyList()
        val shots = lyricShots + bridgeShots
        shots.lastOrNull()?.let { carry.kind = it.kind; carry.motif = it.decor.motif; carry.flow = it.flowAngle }
        val transitionKind = if (next != null) temperaChoose(TEMPERA_TRANSITION_KINDS, "$seed:$index:transition", previousTransition) else null
        if (transitionKind != null) previousTransition = transitionKind
        val transitionDuration = if (next != null) min(1.0, max(0.35, max(gap, 0.0) + 0.3)) else 0.0
        val transitionEnd = next?.lines?.get(0)?.line?.startTime ?: endTime
        TpParagraph(
            id = "tempera-p$index",
            kind = kind,
            boundary = draft.boundary,
            startTime = draft.lines[0].line.startTime,
            endTime = endTime,
            lines = draft.lines,
            shots = shots,
            transitionOut = transitionKind?.let { TpTransition(it, max(draft.lines[0].line.startTime, transitionEnd - transitionDuration), transitionEnd) },
        )
    }
    // 预卷：段首构图在上一段转场时就开始搭（block-wipe 除外）；逐字时间不动。
    paragraphs.forEachIndexed { index, paragraph ->
        val incoming = paragraph.shots.firstOrNull() ?: return@forEachIndexed
        val transition = paragraphs.getOrNull(index - 1)?.transitionOut ?: return@forEachIndexed
        if (transition.kind == "block-wipe") return@forEachIndexed
        incoming.startTime = min(incoming.startTime, max(transition.startTime, paragraphs[index - 1].endTime))
    }
    return TpProgram(seed, threshold, paragraphs)
}

internal fun findTemperaParagraphIndexAtTime(program: TpProgram, time: Double): Int {
    for (i in program.paragraphs.indices.reversed()) if (time >= program.paragraphs[i].startTime) return i
    return 0
}
