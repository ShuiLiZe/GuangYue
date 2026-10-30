package com.guangyue.feature.player.lyrics

/**
 * 一行歌词的呈现值（距离当前行多远、是不是正在唱的那句）。
 *
 * 把「第几行、离当前多远」的换算从 Composable 里拎出来，是为了能在 JVM 单测里钉住
 * 衰减档位（见 `LyricFlowScreenTest`）。
 */
data class LyricRowVisual(
    /** 第几行（歌词文档里的下标）。 */
    val index: Int,
    /** 距离正在唱的那句多少行；负数表示在它之前，`null` 表示还没唱到第一句。 */
    val offset: Int?,
    /** 是不是正在唱的那句。 */
    val active: Boolean,
) {
    /** 离当前行的绝对距离，字号 / 不透明度 / 模糊都按它取档；[UNKNOWN] = 还没唱到。 */
    val distance: Int get() = offset?.let { if (it < 0) -it else it } ?: UNKNOWN

    companion object {
        /** 还没开始唱（`LyricUiState.index == -1`）时的距离档：所有行一视同仁。 */
        const val UNKNOWN = 4
    }
}

/**
 * 当前行下标 → 可见行距离：正在唱的那句是 0，之前的是负数。
 *
 * 进度还没到第一句（`currentIndex < 0`）时所有行都按最远档画，不做「假高亮」。
 */
fun lyricRowVisual(index: Int, currentIndex: Int): LyricRowVisual =
    LyricRowVisual(
        index = index,
        offset = if (currentIndex < 0) null else index - currentIndex,
        active = currentIndex >= 0 && index == currentIndex,
    )

/**
 * 点行跳转要落在哪一行上。
 *
 * Folia 的歌词里 `[00:12.00]` 这种空壳行很常见（间奏计时、纯翻译行），点到它时
 * 往前挪到最近的一句真词再 seek，免得跳过去还是同一句；整段都是空行时退回点到的那行。
 */
fun lyricSeekTarget(blankFlags: List<Boolean>, index: Int): Int? {
    if (index !in blankFlags.indices) return null
    var target = index
    while (target > 0 && blankFlags[target]) target--
    return target
}
