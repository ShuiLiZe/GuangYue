package com.guangyue.core.player

/**
 * 把 ExoPlayer 的报错原文换成播放页能直接显示的一句人话。
 *
 * 背景（0.0.8 收尾实测）：`PlaybackException.message` 往往只有 `Source error`，
 * 真正的原因藏在 `cause` 里的 `ResolutionException("这个音源取不到「X」的播放地址")`。
 * 直接把 `message` 贴到界面上，用户只会看到一句无信息量的英文，等于没有提示。
 * 这里做一次收敛：插件文案原样保留，其余统一给出下一步动作。
 */
object PlaybackErrorText {

    // 「这个音源取不到「X」的播放地址」——前缀也算文案的一部分，去掉就成了半句话。
    private val PLUGIN_WORDING = Regex("这个音源取不到「.+?」的播放地址")

    fun readable(message: String?, causeMessage: String? = null): String {
        val raw = message?.trim().orEmpty()
        val cause = causeMessage?.trim().orEmpty()

        // 插件侧已经给了「取不到「X」的播放地址」这种完整句子，优先原样显示。
        PLUGIN_WORDING.find(cause)?.let { return it.value }
        PLUGIN_WORDING.find(raw)?.let { return it.value }

        return when {
            raw.lowercase().startsWith("source error") -> NO_SOURCE
            raw.isBlank() -> FAILED_HINT
            raw.length > 80 -> FAILED_HINT
            else -> "播放失败：$raw"
        }
    }

    const val NO_SOURCE: String = "这个音源取不到播放地址"
    const val FAILED_HINT: String = "播放失败，换一个音源再试"
}
