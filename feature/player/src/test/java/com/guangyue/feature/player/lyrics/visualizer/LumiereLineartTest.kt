package com.guangyue.feature.player.lyrics.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绘光线稿（lineart/ + rigs 目录各文件 的 lineArt）与 Folia 原版对拍。金标 `lumiere_lineart_golden.txt` 由 Folia v0.7.25 源码在 node 里生成
 * （docs/port/scripts/lumiere-lineart-golden-gen.ts，esbuild 打包后跑）：
 *   LA <宽高比序号> <kind> 路径数 节点数 点数 Σx Σy 加权首尾 Σ线宽 Σalpha Σdelay Σspan 虚线数 Σ虚线周期 Σ节点x加权 Σ节点y Σ节点尺寸 Σ节点delay Σ闪烁相位
 *   （100 个光位 × 宽高比 800/360、360/800，随机种子 `golden:<kind>:art`）；
 *   RT i 点列：基准单元 probe-0:lumiere-p6 镜头 0（optics-raytrace）在 800×360 下前 4 条光路线稿的完整点列。
 */
class LumiereLineartTest {
    private val golden: List<List<String>> by lazy {
        javaClass.classLoader!!.getResourceAsStream("lumiere_lineart_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    }

    private fun near(expected: String, actual: Double, what: String) {
        val e = expected.toDouble()
        assertEquals(what, e, actual, 1e-7 * maxOf(1.0, kotlin.math.abs(e)))
    }

    @Test
    fun everyProfileMatchesFoliaSummary() {
        val aspects = listOf(800.0 / 360, 360.0 / 800)
        val rows = golden.filter { it[0] == "LA" }
        assertEquals(200, rows.size)
        for (row in rows) {
            val aspect = aspects[row[1].toInt()]
            val kind = row[2]
            assertTrue("目录里有 $kind", LumiereCatalog.hasProfile(kind))
            val s = LumiereCatalog.profileOf(kind).lineArt(aspect, lumiereRng("golden:$kind:art"))
            val tag = "$kind@${row[1]}"
            assertEquals("$tag 路径数", row[3].toInt(), s.paths.size)
            assertEquals("$tag 节点数", row[4].toInt(), s.nodes.size)
            var pts = 0; var sx = 0.0; var sy = 0.0; var wx = 0.0; var sw = 0.0; var sa = 0.0; var sd = 0.0; var ss = 0.0; var nd = 0; var ds = 0.0
            s.paths.forEachIndexed { i, q ->
                pts += q.points.size
                for (p in q.points) { sx += p.x; sy += p.y }
                wx += (i % 7 + 1) * (q.points[0].x + q.points.last().y)
                sw += q.width; sa += q.alpha; sd += q.delay; ss += q.span
                q.dash?.let { nd++; ds += it[0] + it[1] }
            }
            var nx = 0.0; var ny = 0.0; var nz = 0.0; var ndl = 0.0; var nt = 0.0
            s.nodes.forEachIndexed { i, n -> nx += n.at.x * (i % 5 + 1); ny += n.at.y; nz += n.size; ndl += n.delay; nt += n.twinklePhase }
            assertEquals("$tag 点数", row[5].toInt(), pts)
            near(row[6], sx, "$tag Σx"); near(row[7], sy, "$tag Σy"); near(row[8], wx, "$tag 首尾")
            near(row[9], sw, "$tag 线宽"); near(row[10], sa, "$tag alpha"); near(row[11], sd, "$tag delay"); near(row[12], ss, "$tag span")
            assertEquals("$tag 虚线数", row[13].toInt(), nd)
            near(row[14], ds, "$tag 虚线周期")
            near(row[15], nx, "$tag 节点x"); near(row[16], ny, "$tag 节点y"); near(row[17], nz, "$tag 节点尺寸"); near(row[18], ndl, "$tag 节点delay"); near(row[19], nt, "$tag 闪烁")
        }
    }

    @Test
    fun baselineRaytraceLinesMatchFolia() {
        val s = LumiereCatalog.profileOf("optics-raytrace").lineArt(800.0 / 360, lumiereRng("probe-0:lumiere-p6:0:optics-raytrace:art"))
        for (row in golden.filter { it[0] == "RT" }) {
            val q = s.paths[row[1].toInt()]
            val pts = row.drop(2)
            assertEquals(pts.size, q.points.size)
            pts.forEachIndexed { i, p ->
                val (x, y) = p.split(',')
                near(x, q.points[i].x, "RT ${row[1]} x$i"); near(y, q.points[i].y, "RT ${row[1]} y$i")
            }
        }
    }

    @Test
    fun timingFollowsScene() {
        // 第 2 个镜头 10–16 秒：9.4 秒开始描，13 秒描完；16.8 秒之后淡没。
        val (d0, f0) = lumiereLineArtTiming(1, 3, 10.0, 16.0, 0.0, 0.0, 9.4, 1.0, 1.0)
        assertEquals(0.0, d0, 1e-9); assertEquals(0.0, f0, 1e-9)
        val (d1, f1) = lumiereLineArtTiming(1, 3, 10.0, 16.0, 0.0, 0.0, 13.0, 1.0, 2.0)
        assertEquals(1.0, d1, 1e-9); assertEquals(2.0, f1, 1e-9)
        val (_, f2) = lumiereLineArtTiming(1, 3, 10.0, 16.0, 0.0, 0.0, 16.9, 1.0, 1.0)
        assertEquals(0.0, f2, 1e-9)
        // 最后一个镜头不淡出。
        val (_, f3) = lumiereLineArtTiming(2, 3, 16.0, 22.0, 0.0, 0.0, 40.0, 1.0, 1.0)
        assertEquals(1.0, f3, 1e-9)
    }
}
