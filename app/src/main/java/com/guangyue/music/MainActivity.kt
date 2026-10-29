package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.guangyue.core.web.FoliaWebHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity host.
 *
 * 0.0.3 起界面整体换成 Folia 原版（web/ 下由上游源码 Vite 构建，WebViewAssetLoader 提供），
 * 这一层只负责把 [FoliaWebHost] 的 WebView 塞进窗口，并在生命周期里挂/摘原生播放桥。
 * 播放本身仍是 Media3（[com.guangyue.core.player.GuangYuePlaybackService]），
 * 后台/锁屏/通知栏不受影响；Compose 侧不再画任何界面。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var host: FoliaWebHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { host.createWebView() },
                onRelease = { view -> runCatching { view.destroy() } },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { host.resume() }
    }

    override fun onPause() {
        runCatching { host.pause() }
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { host.destroy() }
        super.onDestroy()
    }
}
