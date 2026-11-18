package com.guangyue.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** 频谱分析照 Folia AnalyserNode（fftSize 2048 / smoothing 0.6 / −100..−30 dB）+ getEnergy 分频。 */
class AudioSpectrumTest {
    private fun feed(hz: Double, amp: Double, seconds: Double = 0.5, rate: Int = 44100): AudioBands = feedMany(listOf(hz), amp, seconds, rate)

    private fun feedMany(hzs: List<Double>, amp: Double, seconds: Double = 0.5, rate: Int = 44100): AudioBands {
        val a = SpectrumAnalyzer(rate)
        var last: AudioBands? = null
        val n = (rate * seconds).toInt()
        for (i in 0 until n) a.push(hzs.sumOf { amp * sin(2 * PI * it * i / rate) })?.let { last = it }
        return last!!
    }

    @Test fun silenceIsZero() {
        val b = feed(80.0, 0.0)
        assertEquals(0.0, b.power, 1e-9); assertEquals(0.0, b.bass, 1e-9); assertEquals(0.0, b.treble, 1e-9)
    }

    @Test fun bassToneLightsBassOnly() {
        val b = feed(80.0, 0.5)
        assertTrue("bass ${b.bass}", b.bass > 60)
        assertTrue("treble ${b.treble}", b.treble < 5)
        assertTrue("power ${b.power}", b.power > 0)
    }

    @Test fun vocalToneLightsVocal() {
        // 人声段 1000–3500 Hz 有 117 个频点，单音只亮几格（getEnergy 是平均）；铺五个音才像人声。
        val b = feedMany(listOf(1200.0, 1700.0, 2200.0, 2700.0, 3200.0), 0.2)
        assertTrue("vocal ${b.vocal}", b.vocal > 10)
        assertTrue("bass ${b.bass}", b.bass < 5)
        assertTrue("vocal ${b.vocal} > bass ${b.bass}", b.vocal > b.bass)
    }

    @Test fun processMatchesFolia() {
        assertEquals(255.0, SpectrumAnalyzer.process(255.0, 3.0), 1e-9)
        assertEquals(255.0 * 0.125, SpectrumAnalyzer.process(127.5, 3.0), 1e-9)
    }

    @Test fun breathRange() {
        for (t in 0L..20000L step 500) {
            val b = AudioBands.breath(t)
            assertTrue(b.power in 0.0..40.0)
        }
    }
}
