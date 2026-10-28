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
 */
@Singleton
class PreludeProvider @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var cached: String? = null

    fun code(): String = cached ?: synchronized(this) {
        cached ?: load().also { cached = it }
    }

    private fun load(): String {
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
