// 对应 Folia 文件：src/types.ts（DioramaTuning / DEFAULT_DIORAMA_TUNING / DIORAMA_* 常量）、
//   src/components/visualizer/diorama/tuning.ts（resolveStoredDioramaTuning：缺的取默认、数字夹进范围）、
//   diorama/dioramaMoteField.ts（resolveDioramaMoteCircumference / Radial：四舍五入夹到 4..48、1..4）。
// 「镜台」参数：镜头 / 运动 / 音频响应 → 三种跟唱效果（辉光、灵魂出窍 + 当前字出窍、渐变）→ 关键字着色
// → 点云几何（总开关、点云 / 长廊、四族、密度、整体体积、点云辉光）→ 背景粒子（开关、圆周、径向）。
package com.guangyue.core.lyrics

import kotlin.math.floor

data class DioramaTuning(
    val cameraSpeed: Double = 1.0,
    val motionAmount: Double = 1.0,
    val audioReactivity: Double = 1.0,
    /** geometryVisibility.enabled：关掉整层点云，子项偏好保留。 */
    val geometryEnabled: Boolean = true,
    /** geometryVisibility.mode：false = 'clouds'（逐句点云），true = 'corridor'（一条长廊，四族开关不起作用）。 */
    val geometryCorridor: Boolean = false,
    val strands: Boolean = true,
    val blobs: Boolean = true,
    val ribbons: Boolean = true,
    val rings: Boolean = true,
    val particleDensity: Int = 576,
    val particleScale: Double = 1.0,
    val particleGlowEnabled: Boolean = true,
    val particleGlowIntensity: Double = 0.65,
    val showParticles: Boolean = true,
    val backgroundParticleCircumference: Int = 28,
    val backgroundParticleRadial: Int = 2,
    val glowEnabled: Boolean = true,
    val glowIntensity: Double = 1.0,
    val soulEnabled: Boolean = true,
    val soulIntensity: Double = 1.0,
    val soulActiveEnabled: Boolean = false,
    val gradientEnabled: Boolean = false,
    val gradientIntensity: Double = 1.0,
    val keywordColoringEnabled: Boolean = true,
) {
    /** VisualizerDiorama.tsx：每种跟唱效果的「生效强度」（开关关 = 0），当前字出窍要灵魂出窍开着才算。 */
    val effectiveGlow: Double get() = if (glowEnabled) glowIntensity else 0.0
    val effectiveSoul: Double get() = if (soulEnabled) soulIntensity else 0.0
    val effectiveSoulActive: Boolean get() = soulEnabled && soulActiveEnabled
    val effectiveGradient: Double get() = if (gradientEnabled) gradientIntensity else 0.0

    /** SharedPreferences 存一个串（k=v;…），读回走 [decode] + [sanitize]。 */
    fun encode(): String = listOf(
        "cs" to cameraSpeed, "ma" to motionAmount, "ar" to audioReactivity,
        "ge" to geometryEnabled, "gc" to geometryCorridor, "st" to strands, "bl" to blobs, "ri" to ribbons, "rn" to rings,
        "pd" to particleDensity, "ps" to particleScale, "pge" to particleGlowEnabled, "pgi" to particleGlowIntensity,
        "sp" to showParticles, "bc" to backgroundParticleCircumference, "br" to backgroundParticleRadial,
        "gle" to glowEnabled, "gli" to glowIntensity, "soe" to soulEnabled, "soi" to soulIntensity, "sae" to soulActiveEnabled,
        "gre" to gradientEnabled, "gri" to gradientIntensity, "kce" to keywordColoringEnabled,
    ).joinToString(";") { "${it.first}=${it.second}" }

    companion object {
        val DEFAULT = DioramaTuning()
        const val CAMERA_SPEED_MIN = 0.55; const val CAMERA_SPEED_MAX = 1.85
        const val MOTION_MIN = 0.4; const val MOTION_MAX = 1.6
        const val AUDIO_MIN = 0.0; const val AUDIO_MAX = 1.5
        const val DENSITY_MIN = 96; const val DENSITY_MAX = 1536; const val DENSITY_STEP = 24
        const val SCALE_MIN = 0.65; const val SCALE_MAX = 1.6
        const val PARTICLE_GLOW_MIN = 0.1; const val PARTICLE_GLOW_MAX = 1.5
        const val EFFECT_MIN = 0.1; const val EFFECT_MAX = 1.5
        const val MOTE_CIRC_MIN = 4; const val MOTE_CIRC_MAX = 48
        const val MOTE_RADIAL_MIN = 1; const val MOTE_RADIAL_MAX = 4

        private fun d(v: Double, lo: Double, hi: Double, f: Double) = if (!v.isFinite()) f else v.coerceIn(lo, hi)
        /** JS Math.round：.5 往 +∞。 */
        private fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
        private fun i(v: Double, lo: Int, hi: Int, f: Int) = if (!v.isFinite()) f else jsRound(v.coerceIn(lo.toDouble(), hi.toDouble()))

        /** resolveStoredDioramaTuning：每个数字夹进 Folia 的范围（密度 / 圆周 / 径向四舍五入成整数）。 */
        fun sanitize(t: DioramaTuning): DioramaTuning = t.copy(
            cameraSpeed = d(t.cameraSpeed, CAMERA_SPEED_MIN, CAMERA_SPEED_MAX, DEFAULT.cameraSpeed),
            motionAmount = d(t.motionAmount, MOTION_MIN, MOTION_MAX, DEFAULT.motionAmount),
            audioReactivity = d(t.audioReactivity, AUDIO_MIN, AUDIO_MAX, DEFAULT.audioReactivity),
            particleDensity = t.particleDensity.coerceIn(DENSITY_MIN, DENSITY_MAX),
            particleScale = d(t.particleScale, SCALE_MIN, SCALE_MAX, DEFAULT.particleScale),
            particleGlowIntensity = d(t.particleGlowIntensity, PARTICLE_GLOW_MIN, PARTICLE_GLOW_MAX, DEFAULT.particleGlowIntensity),
            backgroundParticleCircumference = t.backgroundParticleCircumference.coerceIn(MOTE_CIRC_MIN, MOTE_CIRC_MAX),
            backgroundParticleRadial = t.backgroundParticleRadial.coerceIn(MOTE_RADIAL_MIN, MOTE_RADIAL_MAX),
            glowIntensity = d(t.glowIntensity, EFFECT_MIN, EFFECT_MAX, DEFAULT.glowIntensity),
            soulIntensity = d(t.soulIntensity, EFFECT_MIN, EFFECT_MAX, DEFAULT.soulIntensity),
            gradientIntensity = d(t.gradientIntensity, EFFECT_MIN, EFFECT_MAX, DEFAULT.gradientIntensity),
        )

        /** 存串读回：缺的、坏的取默认（同 resolveStoredDioramaTuning 的 `?? DEFAULT`），再夹范围。 */
        fun decode(s: String?): DioramaTuning {
            if (s.isNullOrBlank()) return DEFAULT
            val m = s.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun dv(k: String, f: Double) = m[k]?.toDoubleOrNull() ?: f
            fun bv(k: String, f: Boolean) = m[k]?.toBooleanStrictOrNull() ?: f
            fun iv(k: String, lo: Int, hi: Int, f: Int) = m[k]?.toDoubleOrNull()?.let { i(it, lo, hi, f) } ?: f
            val D = DEFAULT
            return sanitize(
                DioramaTuning(
                    cameraSpeed = dv("cs", D.cameraSpeed), motionAmount = dv("ma", D.motionAmount), audioReactivity = dv("ar", D.audioReactivity),
                    geometryEnabled = bv("ge", D.geometryEnabled), geometryCorridor = bv("gc", D.geometryCorridor),
                    strands = bv("st", D.strands), blobs = bv("bl", D.blobs), ribbons = bv("ri", D.ribbons), rings = bv("rn", D.rings),
                    particleDensity = iv("pd", DENSITY_MIN, DENSITY_MAX, D.particleDensity), particleScale = dv("ps", D.particleScale),
                    particleGlowEnabled = bv("pge", D.particleGlowEnabled), particleGlowIntensity = dv("pgi", D.particleGlowIntensity),
                    showParticles = bv("sp", D.showParticles),
                    backgroundParticleCircumference = iv("bc", MOTE_CIRC_MIN, MOTE_CIRC_MAX, D.backgroundParticleCircumference),
                    backgroundParticleRadial = iv("br", MOTE_RADIAL_MIN, MOTE_RADIAL_MAX, D.backgroundParticleRadial),
                    glowEnabled = bv("gle", D.glowEnabled), glowIntensity = dv("gli", D.glowIntensity),
                    soulEnabled = bv("soe", D.soulEnabled), soulIntensity = dv("soi", D.soulIntensity), soulActiveEnabled = bv("sae", D.soulActiveEnabled),
                    gradientEnabled = bv("gre", D.gradientEnabled), gradientIntensity = dv("gri", D.gradientIntensity),
                    keywordColoringEnabled = bv("kce", D.keywordColoringEnabled),
                ),
            )
        }
    }
}
