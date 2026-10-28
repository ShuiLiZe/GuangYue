package com.guangyue.core.player

/**
 * 把「插件声明的音质 wire 值」翻译成可渲染的 [Quality] 阶梯，并提供在阶梯内循环切换的能力。
 *
 * 插件（尤其是聚合源）常见的声明形态有：
 *
 * ```js
 * supportedQualities: ['128k', '320k', 'flac', 'flac24bit']
 * qualitys: [{ name: '低品', type: '128k' }, { name: '无损', type: 'flac' }]
 * ```
 *
 * 这些字符串五花八门，直接 `Quality.fromWire` 会全部退化成 STANDARD。这里做一次宽容映射，
 * 保证「插件给什么档位，播放器就显示什么档位」，并且总能落回内置全集不会崩。
 */
object QualityLadder {

    /** 常见插件音质别名 → 内置档位。 */
    private val ALIASES: List<Pair<List<String>, Quality>> = listOf(
        listOf("128k", "128", "low", "低品", "标准", "standard") to Quality.LOW,
        listOf("192k", "192", "中品", "medium") to Quality.STANDARD,
        listOf("320k", "320", "high", "高品", "很高") to Quality.HIGH,
        listOf("sq", "flac", "无损", "cd") to Quality.LOSSLESS,
        listOf("hires", "hi-res", "hr", "flac24bit", "24bit", "96k", "192k24", "zq") to Quality.HIRES,
        listOf("atmos", "dolby", "全景声", "空间音频") to Quality.ATMOS,
        listOf("master", "母带", "mq", "studio") to Quality.MASTER,
    )

    /**
     * 插件音质字符串 → [Quality]。
     *
     * 先按 wire 精确匹配，再按别名表（大小写不敏感）匹配，最后看字符串里是否带关键字。
     * 仍然认不出来就丢掉（返回 null），避免给用户一个切了也没用的假档位。
     */
    fun map(raw: String): Quality? {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return null
        Quality.entries.firstOrNull { it.wire.equals(key, ignoreCase = true) }?.let { return it }
        ALIASES.firstOrNull { (names, _) -> names.any { key == it } }?.let { return it.second }
        ALIASES.firstOrNull { (names, _) -> names.any { key.contains(it) } }?.let { return it.second }
        return null
    }

    /** 插件声明列表 → 去重、按档位从高到低。 */
    fun of(raw: List<String>?): List<Quality> = raw.orEmpty()
        .mapNotNull(::map)
        .distinct()
        .sortedByDescending { it.order }

    /** 插件没声明时使用的默认全集（从高到低）。 */
    fun default(): List<Quality> = Quality.entries.sortedByDescending { it.order }

    /**
     * 当前档位 [current] 在 [ladder] 中的下一档（更高优先，到顶后回到最低档）。
     *
     * 这样「点一下音质按钮」= 升一档，符合大多数播放器的肌肉记忆。
     */
    fun cycle(ladder: List<Quality>, current: Quality): Quality {
        if (ladder.isEmpty()) return current.next()
        val sorted = ladder.sortedBy { it.order }
        val at = sorted.indexOf(current)
        if (at >= 0) return sorted[(at + 1) % sorted.size]
        // 当前档不在插件列表里：取不超过 current 的最高档，没有就取最低档
        return sorted.lastOrNull { it.order <= current.order } ?: sorted.first()
    }

    /**
     * 请求档位 [requested] 不可用时，降级到插件支持的最高档。
     *
     * 用于「用户默认音质是母带，但当前插件只有 320k」这类情况，避免直接播放失败。
     */
    fun fallback(ladder: List<Quality>, requested: Quality): Quality {
        if (ladder.isEmpty()) return requested
        if (requested in ladder) return requested
        return ladder.filter { it.order <= requested.order }.maxByOrNull { it.order }
            ?: ladder.minByOrNull { it.order }
            ?: requested
    }
}
