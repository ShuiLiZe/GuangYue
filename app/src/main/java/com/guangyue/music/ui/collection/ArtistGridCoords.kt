package com.guangyue.music.ui.collection

/**
 * Folia 网格歌手页的排位（library/suites/grid/artist/ArtistGridView.tsx buildArtistGridCoords）：
 * 0 = 圆形头像（-0.95·spacingX, 0），1 = 简介卡（0.65·spacingX, 0）；
 * 热门歌曲占上半（z ≤ -1）、专辑占下半（z ≥ 1）的蜂窝位，各按离原点距离由近到远。返回 (baseX, baseY)，单位 dp。
 */
object ArtistGridCoords {
    fun build(songCount: Int, albumCount: Int, spacingX: Float, spacingY: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>(2 + songCount + albumCount)
        out += (-spacingX * 0.95f) to 0f
        out += (spacingX * 0.65f) to 0f
        out += half(-1 downTo -10, spacingX, spacingY).take(songCount)
        val maxZ = maxOf(10, albumCount / 2 + 5)
        out += half(1..maxZ, spacingX, spacingY).take(albumCount)
        return out
    }

    /** 开场镜头对准简介卡（Folia cameraAtOpen：-biography.base）。 */
    fun cameraAtOpen(spacingX: Float): Pair<Float, Float> = (-spacingX * 0.65f) to 0f

    private fun half(zs: IntProgression, spacingX: Float, spacingY: Float): List<Pair<Float, Float>> {
        val c = ArrayList<Triple<Float, Float, Float>>()
        for (z in zs) for (x in -20..20) {
            val bx = x * spacingX + z * spacingX / 2f
            val by = z * spacingY
            c += Triple(bx, by, bx * bx + by * by)
        }
        // JS Array.sort 稳定：同距离保持生成顺序。
        return c.sortedBy { it.third }.map { it.first to it.second }
    }
}
