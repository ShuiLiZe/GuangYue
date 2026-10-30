package com.guangyue.core.plugin.lx

import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.runtime.EmptyPreludeProvider
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 用真实的「五源合一」洛雪自定义源在 JVM 里跑一遍取链，验证 LX 运行时对真实脚本可用。
 *
 * 需要网络；脚本从 `build/lx/five-source-lx.js` 或 `docs/ui-0.0.8/lx` 读取，缺失就跳过。
 */
class LxFiveSourceTest {

    private fun scriptFile(): File? {
        val root = File(System.getProperty("guangyue.rootDir") ?: ".").absoluteFile
        return listOf(
            File(root, "build/lx/five-source-lx.js"),
            File(root, "core/plugin/build/lx/five-source-lx.js"),
        ).firstOrNull { it.isFile && it.length() > 1000 }
    }

    @Test
    fun `五源合一样例脚本能装上并上报五个平台`() {
        val script = scriptFile() ?: run {
            println("跳过：找不到 build/lx/five-source-lx.js")
            return
        }
        val runtime = LxRuntime(
            source = LxSource(id = "five-source", name = "五源合一"),
            code = script.readText(),
            http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build(),
            preludeProvider = EmptyPreludeProvider(),
        )
        try {
            runtime.install()
            println("平台 = ${runtime.platforms}")
            assert(runtime.platforms.containsAll(listOf("tx", "wy", "kg", "kw", "mg"))) {
                "平台上报不全：${runtime.platforms}"
            }
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `真实 QQ 歌曲能经五源合一取到直链`() {
        val script = scriptFile() ?: run {
            println("跳过：找不到 build/lx/five-source-lx.js")
            return
        }
        val runtime = LxRuntime(
            source = LxSource(id = "five-source", name = "五源合一"),
            code = script.readText(),
            http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build(),
            preludeProvider = EmptyPreludeProvider(),
        )
        try {
            runtime.install()
            val item = MusicItem(
                platform = "qq",
                id = "003OUlho2HcRHC",
                title = "夜曲",
                artist = "周杰伦",
                raw = JsonObject(
                    mapOf(
                        "songmid" to kotlinx.serialization.json.JsonPrimitive("003OUlho2HcRHC"),
                        "id" to kotlinx.serialization.json.JsonPrimitive("003OUlho2HcRHC"),
                        "interval" to kotlinx.serialization.json.JsonPrimitive("03:46"),
                    ),
                ),
            )
            val info = LxMusicInfo.of("tx", item) ?: error("musicInfo 映射失败")
            val url = runtime.musicUrl("tx", "320k", info)
            println("tx 320k => $url")
            assert(url.startsWith("http")) { "没取到直链：$url" }
        } finally {
            runtime.close()
        }
    }
}