package com.guangyue.core.player

import kotlin.random.Random

/**
 * 「打乱队列」（Folia usePlaybackQueueController.shuffleQueue）：正在播的那首挪到队首，其余 Fisher–Yates 打乱。
 * 不是随机播放模式：队列本身的顺序变了，之后按新顺序往下播。
 */
object QueueShuffle {
    /** 队列至少两首才能打乱（Folia canShuffleQueue）。 */
    fun canShuffle(size: Int): Boolean = size >= 2

    /** 返回新顺序（旧下标的排列）；[current] 不在范围内时整列打乱。 */
    fun order(size: Int, current: Int, random: Random = Random.Default): List<Int> {
        if (size <= 0) return emptyList()
        val first = current.takeIf { it in 0 until size }
        val rest = (0 until size).filter { it != first }.toMutableList()
        for (i in rest.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = rest[i]; rest[i] = rest[j]; rest[j] = t
        }
        return if (first != null) listOf(first) + rest else rest
    }
}
