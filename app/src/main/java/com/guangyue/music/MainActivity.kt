package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.player.PlaybackController
import com.guangyue.music.nav.GuangYueRoot
import com.guangyue.music.ui.design.FoliaTheme
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
 *
 * 0.0.9（U2 外壳）：外层换成 U1 的 [FoliaTheme]，深浅色默认**跟随系统**；用户手动切过才用
 * 记录下来的偏好（[ShellViewModel.explicitDark]）。`enableEdgeToEdge()` 让内容画到状态栏 /
 * 手势条 / 三键导航下面，各页自己吃 inset（底栏吃 navigationBars，搜索页吃 ime）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 播放控制器由 Hilt 提供，界面状态全部从它的 StateFlow 派生。 */
    @Inject
    lateinit var playback: PlaybackController

    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 0.0.8：状态栏 / 手势条图标深浅随主题走——浅色主题下用深色图标，深色下用浅色，
        // 否则「系统栏透明 + 内容下移」之后状态栏文字在白底上会看不见。
        enableEdgeToEdge()
        setContent {
            // 0.0.9：默认跟随系统深浅；只有用户显式选过才按偏好覆盖。
            val explicitDark by shell.explicitDark.collectAsStateWithLifecycle()
            val dark = explicitDark ?: isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            FoliaTheme(dark = dark) {
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