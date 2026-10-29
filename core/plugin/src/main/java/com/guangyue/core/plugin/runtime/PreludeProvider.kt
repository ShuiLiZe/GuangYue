package com.guangyue.core.plugin.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

/**
 * Provides the shared prelude bundle (`assets/prelude.js`) that every plugin runtime loads
 * once at boot: cheerio, crypto-js, dayjs, qs, he and big-integer, pre-bundled by esbuild.
 *
 * Loaded lazily and cached as a String — 560 KB of JS, parsed once per plugin runtime.
 *
 * 为测试留的两个口子（[open] + [protected] [loadPrelude]）：JVM 单测要能在没有 Android
 * Context 的情况下构造一个「空 prelude」的替身，所以 `code()` 做成可覆写，
 * 真正的读资产逻辑收进 `loadPrelude()`。生产路径没有任何变化。
 */
@Singleton
open class PreludeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile private var cached: String? = null

    open fun code(): String = cached ?: synchronized(this) {
        cached ?: loadPrelude().also { cached = it }
    }

    protected fun loadPrelude(): String {
        val asset = runCatching { context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() } }
            .getOrElse {
                // Fall back to the shipped source dir (useful when running unit tests).
                val f = File("core/plugin/src/main/assets/$ASSET_NAME")
                if (f.isFile) f.readText() else ""
            }
        return asset
    }

    companion object {
        const val ASSET_NAME = "prelude.js"
        const val ASSET_META = "prelude.meta.json"
    }
}
