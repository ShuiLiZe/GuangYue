// 取证专用（仅 debug 源集）：用假数据直接构图集合层（曲库 / 我喜欢），验证少量曲目时的布局。
// extra：lib_n 曲目数（默认 1）、lib_list "auto" | "list" | "wall"、lib_dark 深色、lib_art 是否带（坏的）封面地址。
package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.music.ui.design.FoliaTheme
import com.guangyue.music.ui.home.BravaisWall
import com.guangyue.music.ui.home.SeamCollection

private val SAMPLE = listOf(
    "发如雪" to "周杰伦", "晴天" to "周杰伦", "七里香" to "周杰伦", "Yesterday" to "The Beatles",
    "后来" to "刘若英", "夜曲" to "周杰伦", "红豆" to "王菲", "十年" to "陈奕迅",
    "Hotel California" to "Eagles", "稻香" to "周杰伦", "平凡之路" to "朴树", "光年之外" to "邓紫棋",
    "倔强" to "五月天", "小幸运" to "田馥甄", "演员" to "薛之谦", "成都" to "赵雷",
)

class LibraryShotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val n = intent?.getIntExtra("lib_n", 1) ?: 1
        val mode = intent?.getStringExtra("lib_list") ?: "auto"
        val dark = intent?.getBooleanExtra("lib_dark", false) ?: false
        val art = intent?.getBooleanExtra("lib_art", true) ?: true
        val tiles = (0 until n).map { i ->
            val (t, a) = SAMPLE[i % SAMPLE.size]
            HomeTile(
                key = "ctrack:$i:mediastore:$i",
                title = t,
                subtitle = a,
                // 手机本地歌常见：专辑封面地址在，但读不出来（没有内嵌封面）。
                artwork = if (art) "content://media/external/audio/albumart/${900000 + i}" else null,
                kind = HomeTileKind.TRACK,
            )
        }
        setContent {
            FoliaTheme(dark = dark) {
                val ink = MaterialTheme.colorScheme.onBackground
                val ground = MaterialTheme.colorScheme.background
                Box(Modifier.fillMaxSize().background(ground)) {
                    BravaisWall(
                        sections = listOf("曲目" to tiles),
                        title = "广乐",
                        inkColor = ink,
                        groundColor = ground,
                        onOpenTile = {},
                        onOpenSearch = {},
                        onDownloadTile = {},
                        isCurrent = { it.key == tiles.firstOrNull()?.key },
                        playing = true,
                        initialListMode = when (mode) { "list" -> true; "wall" -> false; else -> null },
                        collection = SeamCollection(
                            name = "我喜欢",
                            kindLabel = "曲库",
                            count = n,
                            loading = false,
                            failed = false,
                            onBack = {},
                            onPlayAll = {},
                            onShuffle = {},
                            onRetry = {},
                            onDownloadAll = {},
                            onFillMeta = {},
                        ),
                    )
                }
            }
        }
    }
}
