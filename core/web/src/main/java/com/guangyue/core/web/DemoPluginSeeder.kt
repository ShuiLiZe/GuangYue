package com.guangyue.core.web

import android.content.Context
import com.guangyue.core.plugin.InstallResult
import com.guangyue.core.plugin.PluginManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 随包附带的演示插件：首次启动时如果没有任何插件，就把 `assets/demo/demo-plugin.js` 装上。
 *
 * 为什么要有它：广乐的数据源来自用户自己导入的 MusicFree 插件，而一个空数据源的新安装
 * 什么都搜不出来（首页、搜索、播放页全是空态），评测和排障都无从下手。装一个刻意写死的
 * 演示源（SoundHelix 公开示例音频 + 手写 LRC）就能让整条链路立刻可验证。
 *
 * 三条硬约束：
 * - **只在插件列表为空时**装一次，用户自己装了别的插件就不动。
 * - 装完立刻标记 `demo_seeded`，用户手动删掉之后不再自动装回来（尊重用户）。
 * - 只读 assets，不联网，不涉及任何第三方平台。
 */
@Singleton
class DemoPluginSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plugins: PluginManager,
) {
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        android.util.Log.i(TAG, "seedIfEmpty 开始")
        val prefs = context.getSharedPreferences("guangyue.demo", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return@withContext
        plugins.load()
        if (plugins.plugins.value.isNotEmpty()) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return@withContext
        }
        // 读取随包的演示插件源码。installCode 本身就是「沙箱探针 → 校验音乐格式 → 落盘」，
        // 探针不过会返回 Failure（例如某次改动把 JS 语法写坏了），这里只记日志、不静默成功。
        val source = runCatching {
            context.assets.open("demo/demo-plugin.js").use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return@withContext
        runCatching { plugins.installCode(source, source = "asset:demo/demo-plugin.js", fallbackName = "广乐演示音源") }
            .onSuccess { result ->
                android.util.Log.i(TAG, "demo 插件安装结果：$result")
                // 只有真的成功了才标记：早先这里无条件标记，一次安装失败（沙箱探针报错）就让
                // 演示源永久缺席——整条搜索 / 播放 / 歌词链路全空，且没有任何入口能自救。
                if (result is InstallResult.Success) {
                    prefs.edit().putBoolean(KEY_SEEDED, true).apply()
                }
            }
            .onFailure { android.util.Log.w(TAG, "demo 插件安装失败", it) }
    }

    private companion object {
        const val TAG = "GuangYueDemo"
        const val KEY_SEEDED = "demo_seeded_v1"
    }
}
