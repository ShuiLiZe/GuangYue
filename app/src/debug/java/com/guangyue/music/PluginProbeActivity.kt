package com.guangyue.music

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.guangyue.core.plugin.PluginHealthChecker
import com.guangyue.core.plugin.PluginManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M8 真机/模拟器取证入口（仅 debug）：导入订阅 → 对订阅里的每个插件跑一次 App 自己的体检
 * （PluginHealthChecker：bridge.search「周杰伦」→ bridge.getMediaSource），结果打 logcat `GYPROBE`。
 *
 * adb shell am start -n com.guangyue.music/.PluginProbeActivity --es sub <订阅 json 地址>
 */
@AndroidEntryPoint
class PluginProbeActivity : ComponentActivity() {
    @Inject lateinit var manager: PluginManager
    @Inject lateinit var checker: PluginHealthChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sub = intent?.getStringExtra("sub")
        val only = intent?.getStringExtra("only")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                Log.w(TAG, "start sub=$sub only=$only")
                manager.load()
                Log.w(TAG, "loaded ${manager.plugins.value.size}")
                if (!sub.isNullOrBlank()) {
                    val r = manager.installOrUpdateAll(sub, force = true)
                    Log.w(TAG, "install result=${r.toString().take(200)}")
                }
                val targets = manager.plugins.value.filter { p ->
                    p.error == null && (only == null || p.platform.contains(only) || p.displayName.contains(only)) &&
                        (sub.isNullOrBlank() || (p.source.contains("padao") || (p.srcUrl ?: "").contains("padao")) || only != null)
                }
                Log.w(TAG, "targets=${targets.map { it.displayName + "@" + it.version }}")
                for (p in targets) {
                    manager.setCurrentSource(p.id)
                    val rep = checker.check(p.id)
                    Log.w(TAG, "RESULT ${rep.name} ${p.version} health=${rep.health} ${rep.ms}ms note=${rep.note}")
                }
                Log.w(TAG, "DONE")
            }.onFailure { Log.e(TAG, "probe failed", it) }
        }
    }

    private companion object { const val TAG = "GYPROBE" }
}
