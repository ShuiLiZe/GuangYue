package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 花火全屏辉光：副歌权重 / bloom 强度对拍 Folia hanabiScreenBloom.ts（金值由 esbuild 打包源码后 node 算，bloom 0.8、chorusBloom 1.3 为 Folia 默认）。 */
class HanabiBloomTest {
    private val golden = """
0 0.00000000000 0.360000000000
9.4 0.00000000000 0.360000000000
9.6 0.0429687500000 0.410273437500
9.9 0.500000000000 0.945000000000
10.1 0.843750000000 1.34718750000
10.3 1.00000000000 1.53000000000
15 1.00000000000 1.53000000000
19.8 1.00000000000 1.53000000000
20 0.912847483095 1.42803155522
20.3 0.567993989482 1.02455296769
20.7 0.0871525169046 0.461968444778
21 0.00000000000 0.360000000000
29.7 0.156250000000 0.542812500000
30.2 0.957031250000 1.47972656250
30.9 0.976709241172 1.50274981217
31.2 0.699474079639 1.17838467318
31.6 0.843750000000 1.34718750000
32.9 0.976709241172 1.50274981217
33.4 0.432006010518 0.865447032307
34 0.00000000000 0.360000000000
""".trim().lines()

    @Test
    fun chorusWeightAndStrengthMatchFolia() {
        val spans = listOf(HbChorusSpan(10.0, 20.0), HbChorusSpan(30.0, 31.0), HbChorusSpan(31.5, 33.0))
        for (row in golden) {
            val p = row.split(' ')
            val w = resolveHanabiChorusWeight(spans, p[0].toDouble())
            assertEquals(row, p[1].toDouble(), w, 1e-9)
            assertEquals(row, p[2].toDouble(), resolveHanabiScreenBloomStrength(0.8, 1.3, w), 1e-9)
        }
    }

    @Test
    fun sparkHaloAlphaClamps() {
        assertEquals(1.0, HbGlowLook.sparkHaloAlpha(1.0, 2.0, 5.0), 1e-12)
        assertEquals(0.0, HbGlowLook.sparkHaloAlpha(0.0, 1.0, 1.0), 1e-12)
    }
}
