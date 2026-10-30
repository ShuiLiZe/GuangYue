package com.guangyue.music.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import com.guangyue.feature.player.screen.FoliaPlayerPage
import com.guangyue.feature.player.screen.LikeState
import com.guangyue.feature.player.screen.PlayerMode
import com.guangyue.feature.player.screen.PlayerQueueRow
import com.guangyue.feature.player.screen.PlayerStringHost
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * U4：全屏播放页的截图（Roborazzi，360×800dp / xhdpi → 720×1600px）。
 *
 * 覆盖 Hark 验收要求的组合：
 *  - 360×800dp × 浅/深：播放中 / 暂停 / 长标题 / 无封面兜底；
 *  - 320dp 一张（窄屏）；
 *  - 字体 1.3 倍一张（放大不溢出）；
 *  - 队列抽屉展开一张（迷你条 → 全屏之后的下一个层级）。
 *
 * 这里只跑 Compose：直接喂 [PlaybackState] / [PlayerQueueRow]，不碰 ExoPlayer、插件与网络。
 * 出图：`$env:GUANGYUE_RECORD_SCREENSHOTS='true'` 后
 * `.\gradlew.bat :app:testDebugUnitTest --tests "*U4PlayerScreenshotTest*"`，产物落 `docs/ui-u4/`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U4PlayerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) File("build/roborazzi-u4") else File(prop)
        dir.mkdirs()
        dir
    }

    // ---------------- 截图脚手架 ----------------

    private fun shoot(
        name: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * fontScale),
            ) {
                FoliaTheme(dark = dark) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Folia.colors.page)
                            .testTag(RootTag),
                    ) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png"))
    }

    /**
     * 播放页本体：走 `feature` 的 [FoliaPlayerPage]，文案用中文常量表注入
     * （生产里由 [playerStrings] 读 strings.xml；两边键完全一致，这里不必起 `Context`）。
     */
    @Composable
    private fun Player(
        state: PlaybackState,
        dark: Boolean = false,
        mode: PlayerMode = PlayerMode.Sequential,
        like: LikeState = LikeState.Idle,
        coverColor: Color? = COVER_TINT,
        queueOpen: Boolean = false,
    ) {
        val res = remember { testStrings() }
        CompositionLocalProvider(PlayerStringHost provides { id -> resolver(res, id) }) {
            FoliaPlayerPage(
                playback = state,
                res = res,
                darkMode = dark,
                mode = mode,
                like = like,
                queue = rows(state),
                coverColor = coverColor,
                accent = Folia.colors.accent,
                onTogglePlay = {},
                onNext = {},
                onPrevious = {},
                onSeek = {},
                onCycleMode = {},
                onToggleLike = {},
                onAddToPlaylist = {},
                onOpenLyrics = {},
                onPickQueueRow = {},
                onQuality = {},
                onBack = {},
                initialQueueOpen = queueOpen,
            )
        }
    }

    // ---------------- 用例：360×800dp ----------------

    @Test
    fun u4PlayingLight() = shoot("u4-playing-light") { Player(state = playing()) }

    @Test
    fun u4PlayingDark() = shoot("u4-playing-dark", dark = true) {
        Player(state = playing(), dark = true)
    }

    @Test
    fun u4PausedLight() = shoot("u4-paused-light") {
        Player(state = playing().copy(isPlaying = false, positionMs = 48_000L))
    }

    @Test
    fun u4PausedDark() = shoot("u4-paused-dark", dark = true) {
        Player(state = playing().copy(isPlaying = false, positionMs = 48_000L), dark = true)
    }

    /** 长标题：24 字中文标题走两行省略，歌手与副标题不让位。 */
    @Test
    fun u4LongTitleLight() = shoot("u4-long-title-light") {
        Player(state = playing(longTitle = true))
    }

    @Test
    fun u4LongTitleDark() = shoot("u4-long-title-dark", dark = true) {
        Player(state = playing(longTitle = true), dark = true)
    }

    /** 无封面兜底：`coverColor = null` → 主题色渐变，封面区画强调色底 + 音符。 */
    @Test
    fun u4NoCoverLight() = shoot("u4-no-cover-light") {
        Player(state = playing(), coverColor = null)
    }

    @Test
    fun u4NoCoverDark() = shoot("u4-no-cover-dark", dark = true) {
        Player(state = playing(), coverColor = null, dark = true)
    }

    /** 队列抽屉展开：当前行高亮 + 左侧强调色竖条。 */
    @Test
    fun u4QueueLight() = shoot("u4-queue-light") {
        Player(state = playing(), queueOpen = true)
    }

    // ---------------- 用例：窄屏 / 大字 ----------------

    /** 320dp 窄屏：五个传输键仍是 48dp，副标题先省略。 */
    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun u4NarrowLight() = shoot("u4-320dp-light") { Player(state = playing()) }

    /** 字体 1.3 倍：标题两行不被裁、进度时间不挤到换行。 */
    @Test
    fun u4Font130Light() = shoot("u4-font130-light", fontScale = 1.3f) {
        Player(state = playing())
    }

    // ---------------- 数据 ----------------

    private fun playing(longTitle: Boolean = false): PlaybackState = PlaybackState(
        item = QueueItem(
            key = "netease-1:ne-0",
            title = if (longTitle) {
                "以父之名（Live 2004 无与伦比演唱会现场版·重制母带）"
            } else {
                "夜曲"
            },
            artist = "周杰伦",
            album = "叶惠美",
            artworkUri = null,
            origin = MediaOrigin(
                pluginId = "netease",
                trackKey = "netease-1:ne-0",
                quality = Quality.SUPER,
            ),
        ),
        isPlaying = true,
        positionMs = 72_000L,
        durationMs = 260_000L,
        bufferedMs = 180_000L,
        shuffle = false,
        repeatMode = 0,
        queueSize = 9,
        index = 1,
    )

    private fun rows(state: PlaybackState): List<PlayerQueueRow> = listOf(
        "夜曲" to "周杰伦",
        "以父之名（Live 2004 无与伦比演唱会现场版·重制母带）" to "周杰伦",
        "七里香" to "周杰伦",
        "告白气球" to "周杰伦",
        "晴天" to "周杰伦",
        "一路向北" to "周杰伦",
    ).mapIndexed { index, (title, artist) ->
        PlayerQueueRow(
            index = index,
            title = title,
            subtitle = artist,
            current = index == state.index,
        )
    }

    /**
     * 中文文案表：与 `app/src/main/res/values/strings.xml` 的 `===== U4 播放页 =====` 段逐键对应。
     * 这里写字面量是为了让截图不依赖 Robolectric 读资源，且一眼能核对「无英文界面文字」。
     */
    private fun testStrings() = com.guangyue.feature.player.screen.PlayerStrings(
        nowPlaying = 1, collapse = 2, previous = 3, next = 4, play = 5, pause = 6,
        shuffle = 7, modeSequential = 8, modeLoopOne = 9, modeShuffle = 10,
        like = 11, liked = 12, addToPlaylist = 13, queue = 14, lyrics = 15,
        lyricsSoon = 16, queueCount = 17, queueEmpty = 18, noSong = 19,
        unknownArtist = 20, quality = 21, retryHint = 22, closeQueue = 23,
        seekBar = 24, coverOf = 25,
    )

    private fun resolver(
        res: com.guangyue.feature.player.screen.PlayerStrings,
        id: Int,
    ): String = when (id) {
        res.nowPlaying -> "正在播放"
        res.collapse -> "收起"
        res.previous -> "上一首"
        res.next -> "下一首"
        res.play -> "播放"
        res.pause -> "暂停"
        res.shuffle -> "随机播放"
        res.modeSequential -> "顺序播放"
        res.modeLoopOne -> "单曲循环"
        res.modeShuffle -> "随机播放"
        res.like -> "喜欢"
        res.liked -> "取消喜欢"
        res.addToPlaylist -> "加入歌单"
        res.queue -> "播放队列"
        res.lyrics -> "歌词"
        res.lyricsSoon -> "歌词功能即将上线"
        res.queueCount -> "播放队列 %1\$d 首"
        res.queueEmpty -> "队列是空的"
        res.noSong -> "当前没有正在播放的歌曲"
        res.unknownArtist -> "未知艺术家"
        res.quality -> "音质"
        res.retryHint -> "可以在设置里换一个音源再试"
        res.closeQueue -> "收起播放队列"
        res.seekBar -> "播放进度"
        res.coverOf -> "%1\$s 的封面"
        else -> ""
    }

    companion object {
        const val RootTag = "u4-player"

        /** 假封面主色（赭橙），模拟"从封面抽到色"的浅/深渐变。 */
        val COVER_TINT = Color(0xFFB4553C)
    }
}
