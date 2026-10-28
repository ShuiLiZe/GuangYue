package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.guangyue.core.player.PlaybackController
import com.guangyue.music.nav.GuangYueNavHost
import com.guangyue.music.theme.GuangYueTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity host. 广乐 is a music app, so the only screens are the home shelf, search,
 * the player and the lyric surfaces.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by remember { mutableStateOf(false) }
            GuangYueTheme(darkTheme = dark) {
                GuangYueNavHost(darkMode = dark, playback = playback)
            }
        }
    }
}
