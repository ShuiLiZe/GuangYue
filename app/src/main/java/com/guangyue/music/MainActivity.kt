package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guangyue.core.player.PlaybackController
import com.guangyue.music.nav.GuangYueRoot
import com.guangyue.music.theme.GuangYueTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 唯一 Activity。
 *
 * 0.0.4 之前这一层只是一个 WebView 容器（core/web 的 FoliaWebHost），0.0.5 起整屏界面都是原生
 * Compose：[GuangYueRoot] 负责底部导航 + 导航图，各页面来自 feature 模块。
 *
 * WebView、WebViewAssetLoader、JS 桥、assets/folia 全部不再加载，主线程不再需要跑浏览器内核，
 * 首帧只做一次 Compose 组合。播放仍然是 Media3（core/player 的 GuangYuePlaybackService），
 * 后台 / 锁屏 / 通知栏不受影响。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 播放控制器由 Hilt 提供，界面状态全部从它的 StateFlow 派生。 */
    @Inject
    lateinit var playback: PlaybackController

    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by shell.darkMode.collectAsStateWithLifecycle()
            GuangYueTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    GuangYueRoot(
                        playback = playback,
                        darkMode = dark,
                        onDarkModeChange = shell::setDarkMode,
                    )
                }
            }
        }
    }
}
