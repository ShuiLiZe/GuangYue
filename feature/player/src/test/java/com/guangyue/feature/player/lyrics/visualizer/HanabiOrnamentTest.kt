package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * R7 花火⑧：MG 装饰件与 Folia hanabi/mg/ornaments.ts 逐节点对拍。金标 `hanabi_ornament_golden.txt`
 * （docs/port/scripts/hanabi-ornament-golden-gen.js，假 Pixi 跑真的 createOrnament）：21 种 × 7 种落点、竖排 / 数量 1.6 变体、数量 0，
 * 每件 10 个时刻，逐节点比显隐 / 位置 / 尺寸 / 旋转 / 透明度 / 色，矢量件比 moveTo/lineTo 点数与坐标和。
 */
class HanabiOrnamentTest {
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 2e-6 + 2e-7 * abs(want))

    private val palette = HbPalette(0x1a1030, 0xfff6ea, listOf(0xe8344e, 0x2f6fe0, 0xf28c28), listOf(0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff), 0xff5d8f, 0xffe08a)
    private val chars = listOf("花", "火", "夏", "夜", "空", "光")

    /** 与金标脚本同式的合成字锚点：6 个字（第 4 个空白），横排或竖排，带一点漂移。 */
    private fun anchors(vertical: Boolean): (Double) -> List<HbGlyphAnchor> = { time ->
        List(6) { i ->
            val along = -150 + 60.0 * i + 4 * sin(time * 1.3 + i)
            val across = 10 * cos(time + i * 0.5)
            HbGlyphAnchor(if (i == 3) " " else chars[i], if (vertical) across else along, if (vertical) along else across, 48.0 + 2 * i,
                10.3 + 0.35 * i, 10.6 + 0.35 * i, 10 + 0.1 * i, min(1.0, max(0.0, (time - 9.9) / 0.4)), i == 3)
        }
    }

    @Test
    fun ornamentsMatchFolia() {
        val lines = javaClass.classLoader!!.getResourceAsStream("hanabi_ornament_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        var orn: HbOrnament? = null
        var tag = ""
        var checked = 0
        for (ln in lines) {
            val f = ln.split(' ')
            when (f[0]) {
                "C" -> {
                    val vertical = f[8] == "1"
                    val bounds = if (vertical) HbBounds(-30.0, -160.0, 60.0, 340.0) else HbBounds(-160.0, -30.0, 340.0, 60.0)
                    val spec = HbOrnamentSpec(f[2], f[3], f[4].toDouble(), f[5].toDouble(), f[6], f[7].toDouble(), "back")
                    orn = HbOrnament(spec, HbOrnamentContext(800.0, 450.0, "s${f[1]}", palette, bounds, anchors(vertical), 10.0, 16.0, 0.8, f[9].toDouble()))
                    tag = "${f[1]} ${f[2]}/${f[3]}"
                    assertEquals("$tag nodes", f[10].toInt(), orn!!.nodes.size)
                }
                "T" -> {
                    val o = orn!!
                    o.update(f[1].toDouble())
                    tag = tag.substringBefore(" t=") + " t=" + f[1]
                    assertEquals("$tag visible", f[2] == "1", o.visible)
                }
                "V" -> assertEquals("$tag visible count", f[1].toInt(), orn!!.nodes.count { it.visible })
                else -> {
                    val k = f[1].toInt()
                    val n = orn!!.nodes[k]
                    val m = "$tag ${f[0]}$k"
                    assertTrue("$m visible", n.visible)
                    val (w, h) = when (n) {
                        is HbOrnSprite -> { assertEquals(m, "s", f[0]); n.width to n.height }
                        is HbOrnGraphics -> { assertEquals(m, "g", f[0]); n.scale to n.scale }
                        is HbOrnHolder -> { assertEquals(m, "c", f[0]); n.scale to n.scale }
                        else -> { assertEquals(m, "t", f[0]); 1.0 to 1.0 }
                    }
                    close("$m x", f[2].toDouble(), n.x); close("$m y", f[3].toDouble(), n.y)
                    close("$m w", f[4].toDouble(), w); close("$m h", f[5].toDouble(), h)
                    close("$m rot", f[6].toDouble(), n.rotation); close("$m alpha", f[7].toDouble(), n.alpha)
                    assertEquals("$m tint", f[8].toInt(), n.tint)
                    if (n is HbOrnGraphics) {
                        assertEquals("$m points", f[9].toInt(), n.shape.points)
                        close("$m sumX", f[10].toDouble(), n.shape.sumX); close("$m sumY", f[11].toDouble(), n.shape.sumY)
                    }
                    checked++
                }
            }
        }
        assertTrue("checked $checked", checked > 5000)
    }

    @Test
    fun aroundBoxWalksClockwise() {
        val p = hbAroundBox(0.0, 10.0, 5.0); assertEquals(-10.0, p[0], 0.0); assertEquals(-5.0, p[1], 0.0)
        val q = hbAroundBox(0.5, 10.0, 5.0); assertEquals(10.0, q[0], 1e-12); assertEquals(5.0, q[1], 1e-12)
    }
}
