// 对应 Folia 文件：
//   src/components/wall/layout.ts（WallMetrics / Bounds / getPitch / overlaps）
//   src/components/wall/blockTemplates.ts（4 套 12×8 区块模板 × 4 种镜像，区块坐标哈希选朝向）
//   src/components/wall/wallSlots.ts（槽位坐标、按视口收集槽位）
//   src/library/suites/bravais/bravaisConstants.ts（BRAVAIS_METRICS、getBravaisScale、overscan）
//
// B1a：Bravais 首页墙的几何底座。无限平铺：区块 (column,row) 的模板由坐标确定，每块 12 个槽。
// B1c：含 blockReflows（点开一格后整块重排），数据在 BlockReflows.kt。
package com.guangyue.core.wall

import kotlin.math.floor

data class WallMetrics(val cellSize: Double, val gap: Double) {
    val pitch: Double get() = cellSize + gap
}

data class Bounds(val left: Double, val right: Double, val top: Double, val bottom: Double) {
    fun overlaps(o: Bounds) = left < o.right && right > o.left && top < o.bottom && bottom > o.top
}

data class BlockSlot(val x: Int, val y: Int, val cols: Int, val rows: Int)

data class WallSlot(
    val key: String,
    val column: Int,
    val row: Int,
    val slotIndex: Int,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val area: Int,
) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}

object Bravais {
    val METRICS = WallMetrics(cellSize = 128.0, gap = 8.0)
    const val OVERSCAN = 420.0
    const val CULL_EDGE_MARGIN = 160.0
    const val NARROW_VIEWPORT_PX = 900.0

    /** 对应 getBravaisScale：视口 < 640 → 0.52，< 1100 → 0.64，否则 0.76。 */
    fun scale(viewportWidth: Double) = if (viewportWidth < 640) 0.52 else if (viewportWidth < 1100) 0.64 else 0.76
}

object BlockTemplates {
    const val BLOCK_COLS = 12
    const val BLOCK_ROWS = 8
    const val SLOTS_PER_BLOCK = 12

    private fun s(x: Int, y: Int, c: Int, r: Int) = BlockSlot(x, y, c, r)

    private val TEMPLATES: List<List<BlockSlot>> = listOf(
        listOf(s(0, 0, 3, 2), s(3, 0, 6, 4), s(9, 0, 3, 2), s(0, 2, 3, 2), s(9, 2, 3, 4), s(0, 4, 4, 4),
            s(4, 4, 2, 2), s(6, 4, 3, 2), s(4, 6, 2, 2), s(6, 6, 2, 2), s(8, 6, 2, 2), s(10, 6, 2, 2)),
        listOf(s(0, 0, 2, 3), s(2, 0, 2, 3), s(4, 0, 2, 3), s(6, 0, 6, 4), s(0, 3, 6, 2), s(6, 4, 2, 4),
            s(8, 4, 2, 4), s(10, 4, 2, 2), s(0, 5, 2, 3), s(2, 5, 2, 3), s(4, 5, 2, 3), s(10, 6, 2, 2)),
        listOf(s(0, 0, 6, 4), s(6, 0, 6, 3), s(6, 3, 4, 3), s(10, 3, 2, 2), s(0, 4, 4, 2), s(4, 4, 2, 2),
            s(10, 5, 2, 3), s(0, 6, 2, 2), s(2, 6, 2, 2), s(4, 6, 2, 2), s(6, 6, 2, 2), s(8, 6, 2, 2)),
        listOf(s(0, 0, 2, 2), s(2, 0, 2, 2), s(4, 0, 4, 3), s(8, 0, 2, 4), s(10, 0, 2, 4), s(0, 2, 2, 3),
            s(2, 2, 2, 2), s(4, 3, 4, 3), s(2, 4, 2, 2), s(8, 4, 4, 4), s(0, 5, 2, 3), s(2, 6, 6, 2)),
    )

    private fun flipX(l: List<BlockSlot>) = l.map { it.copy(x = BLOCK_COLS - it.x - it.cols) }
    private fun flipY(l: List<BlockSlot>) = l.map { it.copy(y = BLOCK_ROWS - it.y - it.rows) }
    private val REFLECTIONS: List<(List<BlockSlot>) -> List<BlockSlot>> = listOf({ it }, ::flipX, ::flipY, { flipX(flipY(it)) })
    private val ORIENTED = TEMPLATES.flatMap { t -> REFLECTIONS.map { it(t) } }
    private val ORIENTED_REFLOWS = BlockReflowData.REFLOWS.flatMap { perSlot -> REFLECTIONS.map { r -> perSlot.map(r) } }
    const val EXPANSION_COLS = 6
    const val EXPANSION_ROWS = 6
    val ORIENTATION_COUNT = ORIENTED.size

    /** 对应 mixBlockCoords（Math.imul + 无符号右移，按 32 位整数算）。 */
    internal fun mix(column: Int, row: Int): Long {
        var v = (column * 0x9e3779b1.toInt()) xor (row * 0x85ebca6b.toInt())
        v = (v xor (v ushr 15)) * 0x2c1b3c6d
        v = v xor (v ushr 12)
        return v.toLong() and 0xFFFFFFFFL
    }

    fun orientation(column: Int, row: Int): Int {
        val n = TEMPLATES.size
        val template = (((column + row * 2) % n) + n) % n
        return template * REFLECTIONS.size + (mix(column, row) % REFLECTIONS.size).toInt()
    }

    fun template(column: Int, row: Int): List<BlockSlot> = ORIENTED[orientation(column, row)]

    /** 对应 getBlockReflow：展开 [expandedSlot] 后整块 12 张的新位置（下标按原槽位）。 */
    fun reflow(column: Int, row: Int, expandedSlot: Int): List<BlockSlot>? =
        if (expandedSlot !in 0 until SLOTS_PER_BLOCK) null else ORIENTED_REFLOWS[orientation(column, row)][expandedSlot]
}

object WallSlots {
    fun key(column: Int, row: Int, slotIndex: Int) = "$column,$row,$slotIndex"

    fun parseKey(key: String): Triple<Int, Int, Int>? {
        val p = key.split(',')
        if (p.size != 3) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        return Triple(n[0], n[1], n[2])
    }

    fun blockSize(m: WallMetrics) = BlockTemplates.BLOCK_COLS * m.pitch to BlockTemplates.BLOCK_ROWS * m.pitch

    private fun toSlot(column: Int, row: Int, index: Int, r: BlockSlot, m: WallMetrics): WallSlot {
        val p = m.pitch
        val x = column * BlockTemplates.BLOCK_COLS * p + r.x * p
        val y = row * BlockTemplates.BLOCK_ROWS * p + r.y * p
        return WallSlot(key(column, row, index), column, row, index, x, y, r.cols * p - m.gap, r.rows * p - m.gap, r.cols * r.rows)
    }

    fun blockSlots(column: Int, row: Int, m: WallMetrics = Bravais.METRICS) =
        BlockTemplates.template(column, row).mapIndexed { i, r -> toSlot(column, row, i, r, m) }

    fun slot(column: Int, row: Int, index: Int, m: WallMetrics = Bravais.METRICS): WallSlot? {
        if (index !in 0 until BlockTemplates.SLOTS_PER_BLOCK) return null
        return toSlot(column, row, index, BlockTemplates.template(column, row)[index], m)
    }

    /** 对应 layoutFocusedBlock。 */
    fun focusedBlock(column: Int, row: Int, focused: Int, m: WallMetrics = Bravais.METRICS): List<WallSlot>? =
        BlockTemplates.reflow(column, row, focused)?.mapIndexed { i, r -> toSlot(column, row, i, r, m) }

    /** 对应 collectWallSlots：与 [bounds] 相交的所有槽（行优先，区块内按槽序）。 */
    fun collect(bounds: Bounds, m: WallMetrics = Bravais.METRICS, limit: Int = Int.MAX_VALUE): List<WallSlot> {
        val (bw, bh) = blockSize(m)
        val out = ArrayList<WallSlot>()
        for (row in floor(bounds.top / bh).toInt()..floor(bounds.bottom / bh).toInt()) {
            for (column in floor(bounds.left / bw).toInt()..floor(bounds.right / bw).toInt()) {
                for (s in blockSlots(column, row, m)) {
                    if (!Bounds(s.x, s.x + s.width, s.y, s.y + s.height).overlaps(bounds)) continue
                    out.add(s)
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }
}

/**
 * 对应 components/wall/rankSlots.ts（不含 reservedPerBlock 预留槽）：离原点越近、越靠水平中线、
 * 面积越大的槽排名越前；同分按 行 → 列 → 槽序。Bravais 把条目按这个顺序放，剩下的槽是空画框。
 */
object RankSlots {
    const val VERTICAL_WEIGHT = 0.9
    const val AREA_WEIGHT = 0.35

    fun score(s: WallSlot, ox: Double, oy: Double, m: WallMetrics = Bravais.METRICS) =
        kotlin.math.abs(s.centerX - ox) + kotlin.math.abs(s.centerY - oy) * VERTICAL_WEIGHT - kotlin.math.sqrt(s.area.toDouble()) * m.pitch * AREA_WEIGHT

    fun rank(candidates: List<WallSlot>, ox: Double, oy: Double, m: WallMetrics = Bravais.METRICS, startKey: String? = null): List<WallSlot> {
        val order = candidates.sortedWith(
            compareBy<WallSlot> { score(it, ox, oy, m) }.thenBy { it.row }.thenBy { it.column }.thenBy { it.slotIndex },
        ).toMutableList()
        val i = if (startKey != null) order.indexOfFirst { it.key == startKey } else -1
        if (i > 0) order.add(0, order.removeAt(i))
        return order
    }
}
