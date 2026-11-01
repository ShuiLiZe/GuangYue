package com.guangyue.music.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.core.data.lyrics.LyricTimeline
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import com.guangyue.feature.player.lyrics.LyricFlowScreen
import com.guangyue.feature.player.lyrics.LyricStringHost
import com.guangyue.feature.player.lyrics.LyricStrings
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
 * U5 歌词页截图（Roborazzi，360×800dp / xhdpi → 720×1600px）。
 *
 * V0e：列表层已经被「舞台单行 + 底部字幕层」取代 —— 本文件按新结构更新：
 *  - 舞台：只画当前句（[kernelLines] 必须一起喂，否则没有 `activeLine`，舞台会退化成「等待音乐」占位）；
 *  - 字幕层：当前句有可读翻译 → 画翻译；没有 → 改画后面几行的预告（Folia `upcomingLines` 规则）；
 *  - 三种中文空态、320dp 窄屏、字体 1.3 倍照旧。
 *
 * 只跑 Compose：直接喂 [LyricLine] 列表与 [PlaybackState]，不碰插件、网络与 ExoPlayer。
 * 出图：`-ProborazziRecord=true -ProborazziOutputDir=<目录>` 后
 * `.\gradlew.bat :app:testDebugUnitTest --tests "*U5LyricsScreenshotTest*"`，产物默认落 `build/roborazzi-u5`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U5LyricsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) File("build/roborazzi-u5") else File(prop)
        dir.mkdirs()
        dir
    }

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
     * 歌词页本体：走 `feature` 的 [LyricFlowScreen]，文案用中文常量表注入
     * （生产里由 [lyricStrings] 读 strings.xml；两边键完全一致，这里不必起 `Context`）。
     *
     * V0e：列表层换成「舞台单行 + 底部字幕层」后，必须同时喂 [kernelLines]
     * （生产里由 `LyricTimeline.toLines` 从 [LyricLine] 造出来），否则 `activeLine` 恒为 null，
     * 舞台会一直停在「等待音乐」占位。
     */
    @Composable
    private fun Lyrics(
        lines: List<LyricLine> = lyrics(),
        currentIndex: Int = 4,
        dark: Boolean = false,
        loading: Boolean = false,
        failure: String? = null,
        pureMusic: Boolean = false,
        coverColor: Color? = COVER_TINT,
        showTranslation: Boolean = true,
    ) {
        val res = remember { testStrings() }
        val kernel = remember(lines, showTranslation) {
            val built = LyricTimeline.toLines(lines = lines, durationMs = 260_000L)
            if (showTranslation) built else built.map { it.copy(translation = null) }
        }
        CompositionLocalProvider(LyricStringHost provides { id -> resolver(res, id) }) {
            LyricFlowScreen(
                lyrics = lines,
                currentIndex = currentIndex,
                strings = res,
                playback = playing(),
                accent = Folia.colors.accent,
                onBack = {},
                onSeekToLine = {},
                onManualScroll = {},
                onTogglePlay = {},
                onSeek = {},
                artist = "周杰伦",
                source = "网易云音乐",
                loading = loading,
                failure = failure,
                pureMusic = pureMusic,
                darkMode = dark,
                coverColor = coverColor,
                kernelLines = kernel,
            )
        }
    }

    // ---------------- V0e 舞台单行 + 底部字幕层 ----------------

    /** 当前句有可读翻译 → 舞台画当前句、字幕层画译文。 */
    @Test
    fun v0eStageLight() = shoot("v0e-stage-light") { Lyrics(currentIndex = 4) }

    @Test
    fun v0eStageDark() = shoot("v0e-stage-dark", dark = true) { Lyrics(currentIndex = 4, dark = true) }

    /** 当前句没有翻译 → 字幕层改画后面几行的预告（Folia `upcomingLines`）。 */
    @Test
    fun v0ePreviewLight() = shoot("v0e-preview-light") {
        Lyrics(lines = lyricsWithoutTranslation(), currentIndex = 0)
    }

    @Test
    fun v0ePreviewDark() = shoot("v0e-preview-dark", dark = true) {
        Lyrics(lines = lyricsWithoutTranslation(), currentIndex = 0, dark = true)
    }

    /** 还没唱到第一句（index = -1）→ 舞台「等待音乐」占位 + 无字幕。 */
    @Test
    fun v0eInterludeLight() = shoot("v0e-interlude-light") { Lyrics(currentIndex = -1) }

    @Test
    fun v0eInterludeDark() = shoot("v0e-interlude-dark", dark = true) {
        Lyrics(currentIndex = -1, dark = true)
    }

    // ---------------- 中文空态 ----------------

    @Test
    fun u5Loading() = shoot("u5-loading-light") {
        Lyrics(lines = emptyList(), currentIndex = -1, loading = true)
    }

    @Test
    fun u5NoLyrics() = shoot("u5-no-lyrics-light") {
        Lyrics(lines = emptyList(), currentIndex = -1)
    }

    @Test
    fun u5NoLyricsDark() = shoot("u5-no-lyrics-dark", dark = true) {
        Lyrics(lines = emptyList(), currentIndex = -1, dark = true)
    }

    @Test
    fun u5PureMusic() = shoot("u5-pure-music-light") {
        Lyrics(lines = emptyList(), currentIndex = -1, pureMusic = true)
    }

    @Test
    fun u5Failed() = shoot("u5-failed-light") {
        Lyrics(lines = emptyList(), currentIndex = -1, failure = "歌词获取失败：插件没有返回 LRC")
    }

    // ---------------- 手机适配（V0e：长列表用例随列表层一起删除） ----------------

    /** 320dp 窄屏：舞台长句换行、字幕层与底部胶囊都不被导航栏遮住。 */
    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun u5NarrowLight() = shoot("v0e-320dp-light") { Lyrics(currentIndex = 4) }

    /** 字体 1.3 倍：舞台只画当前句、高度由剩余空间决定，不能压到播放条上。 */
    @Test
    fun u5Font130Light() = shoot("v0e-font130-light", fontScale = 1.3f) { Lyrics(currentIndex = 4) }

    /** 深色 + 窄屏 + 大字，三个约束叠加一次。 */
    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun u5NarrowDarkFont130() = shoot("v0e-320dp-dark-font130", dark = true, fontScale = 1.3f) {
        Lyrics(currentIndex = 4, dark = true)
    }

    // ---------------- 数据 ----------------

    /** 8 行带译文的中文歌词，`index = 4` 落在第 5 行。 */
    private fun lyrics(): List<LyricLine> = listOf(
        "00:12.00" to "天空灰得像哭过",
        "00:17.40" to "离开你以后 并没有更自由",
        "00:23.10" to "酸酸的空气 嗅出我们的距离",
        "00:28.90" to "一幕椎心的结局 像在呼吸",
        "00:34.60" to "你说你也会难过 我不相信",
        "00:40.20" to "牵着你陪着我也只是曾经",
        "00:45.80" to "希望他是真的比我还要爱你",
        "00:51.30" to "我才会逼自己离开",
    ).mapIndexed { index, (_, text) ->
        LyricLine(
            timeMs = 12_000L + index * 5_600L,
            text = text,
            translation = if (index % 2 == 0) "The sky is as grey as if it had wept" else null,
        )
    }

    /** 全行都没有翻译的歌词：用来拍「当前句无可读翻译 → 字幕层改画预告」。 */
    private fun lyricsWithoutTranslation(): List<LyricLine> = listOf(
        "00:12.00" to "天空灰得像哭过",
        "00:17.40" to "离开你以后 并没有更自由",
        "00:23.10" to "酸酸的空气 嗅出我们的距离",
        "00:28.90" to "一幕椎心的结局 像在呼吸",
        "00:34.60" to "你说你也会难过 我不相信",
    ).mapIndexed { index, (_, text) ->
        LyricLine(timeMs = 12_000L + index * 5_600L, text = text, translation = null)
    }

    private fun playing(): PlaybackState = PlaybackState(
        item = QueueItem(
            key = "netease-1:ne-0",
            title = "夜曲",
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
        positionMs = 56_000L,
        durationMs = 260_000L,
        bufferedMs = 180_000L,
        shuffle = false,
        repeatMode = 0,
        queueSize = 9,
        index = 1,
    )

    /**
     * 中文文案表：与 `app/src/main/res/values/strings.xml` 的 `===== U5 歌词页 =====` 段逐键对应。
     * 写字面量是为了让截图不依赖 Robolectric 读资源，且一眼能核对「无英文界面文字」。
     */
    private fun testStrings() = LyricStrings(
        back = 1, title = 2, play = 3, pause = 4, empty = 5, emptyHint = 6,
        loading = 7, loadFailed = 8, failedHint = 9, retry = 10, pureMusic = 11,
        waiting = 12, seekBar = 13, sourceLabel = 14, sourceNone = 15,
    )

    private fun resolver(res: LyricStrings, id: Int): String = when (id) {
        res.back -> "返回"
        res.title -> "歌词"
        res.play -> "播放"
        res.pause -> "暂停"
        res.empty -> "无可用歌词"
        res.emptyHint -> "换一个音源或稍后再试"
        res.loading -> "正在匹配最佳歌词…"
        res.loadFailed -> "匹配最佳歌词失败"
        res.failedHint -> "稍后再试，或在设置里换一个音源"
        res.retry -> "重试"
        res.pureMusic -> "纯音乐，无需匹配歌词"
        res.waiting -> "等待音乐"
        res.seekBar -> "播放进度"
        res.sourceLabel -> "歌词"
        res.sourceNone -> "无"
        else -> ""
    }

    companion object {
        const val RootTag = "u5-lyrics-root"

        /** 假封面主色（赭橙），模拟「从封面抽到色」的浅/深渐变。 */
        val COVER_TINT = Color(0xFFB4553C)
    }
}
