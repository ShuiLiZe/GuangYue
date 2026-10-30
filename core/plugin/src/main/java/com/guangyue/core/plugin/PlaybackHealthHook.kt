package com.guangyue.core.plugin

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「体检结果要和实际播放一致」（0.0.9 b 第 2 项）。
 *
 * 现象：酷我 / 汽水在真机上点播放**真的出声了**，插件管理页却还写着「能搜到歌，但取不到链」。
 * 原因不在 UI —— UI 原样渲染 `PluginMeta.health`，是 health 本身就过期了：
 *
 * * [PluginHealthChecker] 的判定标准是「插件自己的 `getMediaSource` 能拿到地址」；
 * * 但播放链路 [com.guangyue.core.player.PluginMediaSourceResolver] 在插件取不到链时，
 *   **会再问一遍洛雪取链源**（自带五源脚本，实测酷我 1 秒出链）。于是「能出声」和
 *   「health = no-media」同时成立，两者的口径根本不是一回事。
 *
 * 修法是让播放结果反哺体检：播放链路取链成功（无论插件直出还是洛雪兜底）之后调用
 * [recordPlayback]，把这一条插件标成 [PluginHealth.OK]，并把命中的来源写进 healthNote，
 * 管理页随即刷新成「可用」。
 *
 * 注意这里只往「可用」方向修正，**不会**把已经 OK 的改坏，也**不**替用户打开/关闭音源
 * （`markEnabled` 恒 false）——播放过一首歌不等于用户要把它设成当前音源。
 *
 * 用独立的 IO 作用域，调用方（播放线程）不等待落盘，不拖慢起播。
 *
 * ## 状态的来源与刷新时机（0.0.9 b 写清）
 *
 * | 来源 | 写入口 | 时机 |
 * | --- | --- | --- |
 * | 出厂基线 | [PluginHealthBaseline.lookup] | 装/更新插件时给一次 |
 * | 体检 | [PluginHealthChecker.checkAll] → `PluginManager.recordHealth` | 用户点「体检」或自动体检跑完 |
 * | 播放实测 | **本类** | 每次真正取链成功（插件直出 / 洛雪兜底）时，异步 |
 *
 * 三者都落到 `PluginMeta.health(+ healthNote + healthAt)` → `PluginManager.recordHealth` 写索引并
 * `notifyReload`，`_plugins` StateFlow 更新，插件管理页与「详情」随即重画。不需要重启、不需要手动刷新。
 */
@Singleton
class PlaybackHealthHook @Inject constructor(
    private val manager: PluginManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @param pluginId 播放时用的插件 id 或 platform
     * @param via 「洛雪取链源『Free listen』」「插件直出」这类来源说明，写进 healthNote
     */
    fun recordPlayback(pluginId: String?, via: String) {
        val key = pluginId?.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            runCatching {
                val meta = manager.find(key) ?: manager.findByPlatform(key) ?: return@launch
                if (meta.health == PluginHealth.OK) return@launch
                Log.d("GYHEALTH", "playback ok ${meta.platform} via=$via -> ok")
                manager.recordHealth(
                    mapOf(meta.id to (PluginHealth.OK to noteOf(via))),
                    markEnabled = false,
                )
            }.onFailure { Log.w("GYHEALTH", "recordPlayback failed: ${it.message}") }
        }
    }

    private fun noteOf(via: String): String = "播放实测：$via"
}
