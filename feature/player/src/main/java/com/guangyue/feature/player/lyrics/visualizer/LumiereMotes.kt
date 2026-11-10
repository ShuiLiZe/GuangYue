// 绘光浮尘与前景散景：light/motes.ts 逐函数对译（位置是 (种子, t) 的闭式函数，亮度按光场 CPU 公式取）。
// 纯逻辑（JVM 单测可测，金标 LumiereMotesTest）；画法在 LumiereStage.kt 的 drawLumiereMotes（dot / bokeh 贴图）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 一帧里的一颗：位置（dp）、直径（dp）、透明度。visible=false 的不画。 */
internal class LumiereMoteSprite {
    var x = 0.0
    var y = 0.0
    var size = 0.0
    var alpha = 0.0
    var visible = false
}

/** createMotes：width / height 为舞台 dp，seed 同 Folia（浮尘 `${单元种子}:motes`、前景 `${单元种子}:front`）。 */
internal class LumiereMotes(width: Double, private val height: Double, seed: String, private val spec: LumiereMotesSpec) {
    private val aspect = width / height
    private val margin = 0.08
    private class Mote(val bx: Double, val by: Double, val depth: Double, val size: Double, val phaseA: Double, val phaseB: Double, val freqA: Double, val freqB: Double, val twinklePhase: Double, val twinkleFreq: Double)

    private val motes: List<Mote>
    val sprites: List<LumiereMoteSprite>

    init {
        val rng = lumiereRng(seed)
        motes = List(spec.count) {
            val depth = rng.next()
            val bx = rng.next() * (aspect + margin * 2) - margin
            val by = rng.next() * (1 + margin * 2) - margin
            val size = spec.sizeMin + (spec.sizeMax - spec.sizeMin) * depth * depth
            val phaseA = rng.next() * PI * 2
            val phaseB = rng.next() * PI * 2
            val freqA = 0.6 + rng.next() * 0.8
            val freqB = 0.5 + rng.next() * 0.9
            val twinklePhase = rng.next() * PI * 2
            val twinkleFreq = 0.8 + rng.next() * 2.2
            Mote(bx, by, depth, size, phaseA, phaseB, freqA, freqB, twinklePhase, twinkleFreq)
        }
        sprites = List(spec.count) { LumiereMoteSprite() }
    }

    private fun wrap(value: Double, lo: Double, hi: Double): Double {
        val span = hi - lo
        return lo + (((value - lo) % span) + span) % span
    }

    /** update(time, beams, color, intensity)：写进 sprites（同 Folia 每颗 sprite 的 position / width / alpha / visible）。 */
    fun update(time: Double, beams: List<LumiereResolvedBeam>, intensity: Double) {
        val omega = (PI * 2) / max(spec.swirlPeriod, 0.1)
        for (i in motes.indices) {
            val mote = motes[i]
            val s = sprites[i]
            val speed = 0.45 + mote.depth * 0.9
            val swirl = spec.swirl * (0.5 + mote.depth)
            val x = wrap(mote.bx + spec.driftX * speed * time + swirl * sin(time * omega * mote.freqA + mote.phaseA), -margin, aspect + margin)
            val y = wrap(mote.by + spec.driftY * speed * time + swirl * cos(time * omega * mote.freqB + mote.phaseB), -margin, 1 + margin)
            val lit = lumiereCompressLight(lumiereLightAt(beams, x, y)) * spec.gain
            val twinkle = 1 - spec.twinkle + spec.twinkle * (0.5 + 0.5 * sin(time * mote.twinkleFreq * PI * 2 + mote.twinklePhase))
            val alpha = min(1.0, (lit + spec.ambient) * twinkle * intensity)
            s.visible = alpha > 0.004
            if (!s.visible) continue
            s.x = x * height
            s.y = y * height
            s.size = mote.size * height
            s.alpha = alpha
        }
    }
}
