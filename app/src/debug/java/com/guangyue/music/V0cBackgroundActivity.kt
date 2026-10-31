// V0c 取证专用（仅 debug 源集）：封面取色流体背景单独实拍。
// 用内置测试封面（BundledTestCover）跑 Folia 取色 → 浅/深各拍一张。
package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.guangyue.feature.player.lyrics.visualizer.BackdropPalette
import com.guangyue.feature.player.lyrics.visualizer.BundledTestCover
import com.guangyue.feature.player.lyrics.visualizer.CoverPalette
import com.guangyue.feature.player.lyrics.visualizer.VisualizerBackdrop
import com.guangyue.feature.player.lyrics.visualizer.rememberCoverBackdrop

/** 只画背景层：封面取色 → 流体背景（Folia VisualizerBackdrop）。 */
@Composable
fun V0cBackground(dark: Boolean) {
    val theme: BackdropPalette = rememberCoverBackdrop(BundledTestCover, dark)
    val palette = remember(theme) {
        CoverPalette(
            backgroundColor = theme.background,
            primaryColor = theme.primary,
            accentColor = theme.accent,
            secondaryColor = theme.secondary,
            fromCover = theme.fromCover,
        )
    }
    Box(Modifier.fillMaxSize()) {
        VisualizerBackdrop(
            palette = palette,
            source = BundledTestCover,
            dark = dark,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 入口：
 * adb shell am start -n com.guangyue.music/.V0cBackgroundActivity --ez v0c_dark false
 */
class V0cBackgroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent?.getBooleanExtra("v0c_dark", true) ?: true
        setContent { V0cBackground(dark) }
    }
}
