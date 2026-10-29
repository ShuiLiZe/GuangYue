package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginMeta
import okhttp3.OkHttpClient

/**
 * 用真实的 [PluginRuntime] 跑自写测试插件：不依赖 Hilt、不碰任何第三方平台。
 *
 * 0.0.1 的 bug 只在 R8 产物里复现，所以这条路径必须能被普通 JUnit 用例直接调用
 * （见 `R8PluginLoadTest`）。
 */
object PluginRuntimeHarness {

    /** 不走网络的 OkHttp：测试插件只返回固定数据，任何请求都会立刻失败。 */
    fun offlineHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            throw java.io.IOException("网络在测试里被禁用：${chain.request().url}")
        }
        .build()

    fun runtimeFor(code: String, platform: String = "gy_test"): PluginRuntime = PluginRuntime(
        meta = PluginMeta(
            id = platform,
            platform = platform,
            name = platform,
            version = "1",
            srcUrl = null,
            author = "GuangYue CI",
            source = "test://$platform",
        ),
        code = code,
        http = offlineHttp(),
        preludeProvider = EmptyPreludeProvider(),
    )
}
