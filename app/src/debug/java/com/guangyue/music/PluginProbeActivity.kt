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
    @Inject lateinit var bridge: com.guangyue.core.plugin.MusicPluginBridge

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
                val kw = intent?.getStringExtra("kw")
                if (kw != null && intent?.getStringExtra("play") != null) {
                    // 0.0.34 取证：翻页 + 搜索结果取链 + 「账号同步形状」的原始对象取链（--es play 1）。
                    val p = targets.first()
                    manager.setCurrentSource(p.id)
                    for (pg in 1..3) {
                        val r = runCatching { bridge.search(p.id, kw, pg, "music") }.getOrNull()
                        Log.w(TAG, "PAGE $pg n=${r?.items?.size} end=${r?.isEnd} err=${r?.error?.take(120)}")
                    }
                    val first = bridge.search(p.id, kw, 1, "music").items.firstOrNull()
                    Log.w(TAG, "RAW ${first?.raw.toString().take(900)}")
                    for (q in listOf("standard", "high")) {
                        val ms = runCatching { bridge.getMediaSource(p.id, first?.raw, first, q) }
                        Log.w(TAG, "MS search $q url=${ms.getOrNull()?.url?.take(100)} exc=${ms.exceptionOrNull()?.message?.take(200)}")
                    }
                    val r0 = first?.raw
                    if (r0 != null) {
                        fun s(k: String) = (r0[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val synced = kotlinx.serialization.json.buildJsonObject {
                            put("platform", kotlinx.serialization.json.JsonPrimitive(p.platform))
                            put("id", kotlinx.serialization.json.JsonPrimitive(s("songid").ifBlank { s("id") }))
                            put("songid", kotlinx.serialization.json.JsonPrimitive(s("songid").ifBlank { s("id") }))
                            put("songmid", kotlinx.serialization.json.JsonPrimitive(s("songmid")))
                            put("mid", kotlinx.serialization.json.JsonPrimitive(s("songmid")))
                            put("title", kotlinx.serialization.json.JsonPrimitive(s("title")))
                            put("artist", kotlinx.serialization.json.JsonPrimitive(s("artist")))
                            put("album", kotlinx.serialization.json.JsonPrimitive(s("album")))
                            put("albummid", kotlinx.serialization.json.JsonPrimitive(s("albummid")))
                        }
                        Log.w(TAG, "SYNCED $synced")
                        val ms = runCatching { bridge.getMediaSource(p.id, synced, null, "standard") }
                        Log.w(TAG, "MS synced url=${ms.getOrNull()?.url?.take(100)} exc=${ms.exceptionOrNull()?.message?.take(200)}")
                    }
                    Log.w(TAG, "DONE")
                    return@runCatching
                }
                if (kw != null) {
                    // 搜索取证：--es kw <词> --es only <平台>，四类各打前 3 条名称 + 图片地址。
                    for (p in targets.take(1)) {
                        manager.setCurrentSource(p.id)
                        for (t in listOf("music", "album", "sheet", "artist")) {
                            val r = runCatching { bridge.search(p.id, kw, 1, t) }.getOrNull()
                            val rows = (r?.items?.map { it.title to it.artwork } ?: emptyList()) +
                                (r?.albums?.map { it.title to it.artwork } ?: emptyList()) +
                                (r?.artists?.map { it.name to it.avatar } ?: emptyList())
                            Log.w(TAG, "SEARCH ${p.displayName} $t n=${rows.size} err=${r?.error} " + rows.take(3).joinToString(" | ") { "${it.first} -> ${it.second}" })
                            rows.firstNotNullOfOrNull { it.second }?.let { url ->
                                // 图片通不通：OkHttp 直取一次（Coil 失败不打日志）。
                                val res = runCatching {
                                    okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build()).execute().use { "${it.code} ${it.body?.contentLength()}" }
                                }.getOrElse { e -> "EXC ${e.javaClass.simpleName} ${e.message}" }
                                Log.w(TAG, "IMG $t $res")
                                val coil = runCatching {
                                    val req = coil3.request.ImageRequest.Builder(this@PluginProbeActivity).data(url).build()
                                    coil3.SingletonImageLoader.get(this@PluginProbeActivity).execute(req).let { rr ->
                                        if (rr is coil3.request.ErrorResult) "ERR ${rr.throwable}" else "OK"
                                    }
                                }.getOrElse { e -> "EXC $e" }
                                Log.w(TAG, "COIL $t $coil")
                            }
                        }
                    }
                    Log.w(TAG, "DONE")
                    return@runCatching
                }
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
