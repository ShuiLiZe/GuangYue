package com.guangyue.music.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import com.guangyue.feature.player.lyrics.CoverImage
import com.guangyue.feature.player.lyrics.visualizer.BundledTestCover
import com.guangyue.feature.player.lyrics.visualizer.CoverPalette
import com.guangyue.feature.player.lyrics.LyricFlowScreen
import com.guangyue.feature.player.lyrics.LyricStyleSheet
import com.guangyue.feature.player.lyrics.LyricStyle
import com.guangyue.feature.player.lyrics.LyricStyleStrings
import com.guangyue.feature.player.lyrics.LyricStrings
import com.guangyue.feature.player.lyrics.visualizer.VisualizerBackdrop
import com.guangyue.music.ui.design.FoliaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.platform.testTag
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.music.ui.design.Folia
import androidx.compose.runtime.remember
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import com.guangyue.feature.player.lyrics.LyricStringHost

/**
 * V0 歌词样式（「流光」渲染器）的截图用例。
 *
 * 与 `U5LyricsScreenshotTest` 同一条链路（真渲染 [LyricFlowScreen]，不碰插件、网络、ExoPlayer），
 * 但喂的是内核的 `Line` 列表（`LyricTimeline.toLines` 的产物），于是画面由**流光样式**渲染：
 * 当前行走 `ClassicLyricLine`（逐词布局 / 逐字辉光 / 呼吸浮动），背景走 [VisualizerBackdrop]
 * （封面取色的柔光渐变）。
 *
     * 真下歌词页：当前行 = 内核第 1 句（`currentIndex = 1`），时间由 `positionMs` 决定。
 * 所以只要把 positionMs 设到目标毫秒，就能拍出「这一时刻」的 waiting / active / passed
 * 以及逐字扫光的连续帧——与 Folia 用 MotionValue 停在某一时刻的做法等价。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V0LyricStyleScreenshotTest {

    /**
     * 本类会喂真实 `Uri` 给 Coil（离线封面夹具），一旦被 `AsyncImage` 取过，
     * `SingletonImageLoader` 就锁定了；而 `ScreenshotTest` 在 `@Before` 里 `setSafe` 假图标。
     * 同一个 JVM 里两个测试类共用一个单例，谁先跑谁定局 → 必须在本类之前就装好同一个假图标加载器，
     * 让两边都只是「再设一次同样的 Factory」，不再触发 Coil 的锁定异常。
     */
    companion object {
        @JvmStatic
        @org.junit.BeforeClass
        fun installSharedImageLoader() {
            com.guangyue.music.ui.installFakePluginIconsOnce()
        }
        const val RootTag = "v0-lyric-style-root"
    }

    /**
     * 本类会喂真实 Uri 给 Coil（离线封面夹具），一旦被 AsyncImage 取过 SingletonImageLoader 就锁定；
     * ScreenshotTest 又在 @Before 里 setSafe 假图标。同一个 JVM 里两类共用一个单例，谁先跑谁定局，
     * 所以在本类开始前先装同一个假图标加载器，两边就都只是"再设一次同样的 Factory"。
     */
    @get:Rule
    val compose = createComposeRule()

    private val outDir: File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        (if (prop.isNullOrBlank()) File("build/roborazzi-v0") else File(prop)).also { it.mkdirs() }
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
                    ) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png"))
    }

    /** 演示句：「为你弹奏萧邦的夜曲」，0.0s–3.2s，四个词（3/2/2/2 字）。 */

    private fun demoKernelLine(): Line = Line(
        words = listOf(
            Word("为你", 0.0, 0.8),
            Word("弹奏", 0.8, 1.6),
            Word("萧邦", 1.6, 2.4),
            Word("夜曲", 2.4, 3.2),
        ),
        startTime = 0.0,
        endTime = 3.2,
        fullText = "为你弹奏萧邦的夜曲",
        translation = "For you I play Chopin's nocturne",
    )

    /** 上下句：空行也照 U5 的规矩保留，用来验「当前行在列表中的位置」不变。 */
    private fun kernelLines(): List<Line> = listOf(
        Line(words = listOf(Word("一群嗜血的蚂蚁", 0.0, 3.0)), startTime = 0.0, endTime = 3.0, fullText = "一群嗜血的蚂蚁"),
        demoKernelLine(),
        Line(words = listOf(Word("纪念我死去的爱情", 4.0, 7.0)), startTime = 4.0, endTime = 7.0, fullText = "纪念我死去的爱情"),
        Line(words = listOf(Word("跟夜风一样的声", 8.0, 11.0)), startTime = 8.0, endTime = 11.0, fullText = "跟夜风一样的声"),
    )

    /** 与内核行一一对应的旧模型行（`LyricFlowScreen` 仍用它渲染非当前行）。 */
    private fun oldLines(): List<LyricLine> = listOf(
        LyricLine(0L, "一群嗜血的蚂蚁"),
        LyricLine(4_000L, "为你弹奏萧邦的夜曲", "For you I play Chopin's nocturne"),
        LyricLine(8_000L, "纪念我死去的爱情"),
        LyricLine(12_000L, "跟夜风一样的声"),
    )

    private fun playing(positionMs: Long) = PlaybackState(
        item = QueueItem(
            key = "netease-1:ne-0",
            title = "夜曲",
            artist = "萧邦",
            album = "十一月的萧邦",
            artworkUri = null,
            origin = MediaOrigin(pluginId = "netease", trackKey = "netease-1:ne-0", quality = Quality.SUPER),
        ),
        isPlaying = true,
        positionMs = positionMs,
        durationMs = 260_000L,
        bufferedMs = 180_000L,
        shuffle = false,
        repeatMode = 0,
        queueSize = 9,
        index = 1,
    )

    /** 演示底色：与播放页/歌词页共用的封面兜底色（截图不联网，注入假色）。 */
    private val coverTint = Color(0xFFB4553C)

    /**
     * 鐪熶笅姝岃瘝椤碉細褰撳墠琛?= 鍐呮牳绗?1 鍙ワ紙`currentIndex = 1`锛夛紝鏃堕棿鐢?`positionMs` 鍐冲畾銆?     *
     * `positionMs` 落在 [0,3200) 内即「当前行正在唱」；> `activeEnd` 后整句回落 passed。
     */
    @Composable
    private fun Stage(
        positionMs: Long,
        dark: Boolean,
        tuning: ClassicTuning = ClassicTuning(),
    ) {
        val strings = remember { testStrings() }
        CompositionLocalProvider(LyricStringHost provides { id -> resolver(strings, id) }) {
            LyricFlowScreen(
                lyrics = oldLines(),
                currentIndex = 1,
                strings = strings,
                playback = playing(positionMs),
                accent = Folia.colors.accent,
                onBack = {},
                onSeekToLine = {},
                onManualScroll = {},
                onTogglePlay = {},
                onSeek = {},
                artist = "萧邦",
                source = "广乐测试",
                darkMode = dark,
                coverColor = coverTint,
                kernelLines = kernelLines(),
                classicTuning = tuning,
            )
        }
    }
    // ---------- 三态 × 浅深：waiting / active 中段 / passed ----------
    /** waiting：还没轮到这句（positionMs 在整首之前，当前行不在唱）。 */
    @Test
    fun v0ClassicWaitingLight() = shoot("v0-classic-waiting-light") { Stage(-1_000L, dark = false) }

    /** waiting（深色）：同上，换个底。 */
    @Test
    fun v0ClassicWaitingDark() = shoot("v0-classic-waiting-dark", dark = true) { Stage(-1_000L, dark = true) }

    /** active 中段（2.0s）：词 0/1 已唱过，词 2「萧邦的」正在逐字扫光，词 3 未到。 */
    @Test
    fun v0ClassicActiveLight() = shoot("v0-classic-active-light") { Stage(2_000L, dark = false) }

    @Test
    fun v0ClassicActiveDark() = shoot("v0-classic-active-dark", dark = true) { Stage(2_000L, dark = true) }

    /** passed（4.6s）：整句唱完（词落回 0.82 透明度 + 漂移角）。 */
    @Test
    fun v0ClassicPassedLight() = shoot("v0-classic-passed-light") { Stage(4_600L, dark = false) }

    @Test
    fun v0ClassicPassedDark() = shoot("v0-classic-passed-dark", dark = true) { Stage(4_600L, dark = true) }

    // 一个 ComposeTestRule 只能 setContent 一次，所以 6 帧拆成 6 个用例，用例名尾号即帧号。
    private val glowFrames = listOf(1_600L, 1_800L, 2_000L, 2_200L, 2_400L, 2_600L)
    @Test
    fun v0GlowFrame01Light() = shoot("v0-glow-frame-01-light") { Stage(glowFrames[0], dark = false) }

    @Test
    fun v0GlowFrame02Light() = shoot("v0-glow-frame-02-light") { Stage(glowFrames[1], dark = false) }

    @Test
    fun v0GlowFrame03Light() = shoot("v0-glow-frame-03-light") { Stage(glowFrames[2], dark = false) }

    @Test
    fun v0GlowFrame04Light() = shoot("v0-glow-frame-04-light") { Stage(glowFrames[3], dark = false) }

    @Test
    fun v0GlowFrame05Light() = shoot("v0-glow-frame-05-light") { Stage(glowFrames[4], dark = false) }

    @Test
    fun v0GlowFrame06Light() = shoot("v0-glow-frame-06-light") { Stage(glowFrames[5], dark = false) }

    @Test
    fun v0GlowFrame01Dark() = shoot("v0-glow-frame-01-dark", dark = true) { Stage(glowFrames[0], dark = true) }

    @Test
    fun v0GlowFrame02Dark() = shoot("v0-glow-frame-02-dark", dark = true) { Stage(glowFrames[1], dark = true) }

    @Test
    fun v0GlowFrame03Dark() = shoot("v0-glow-frame-03-dark", dark = true) { Stage(glowFrames[2], dark = true) }

    @Test
    fun v0GlowFrame04Dark() = shoot("v0-glow-frame-04-dark", dark = true) { Stage(glowFrames[3], dark = true) }

    @Test
    fun v0GlowFrame05Dark() = shoot("v0-glow-frame-05-dark", dark = true) { Stage(glowFrames[4], dark = true) }

    @Test
    fun v0GlowFrame06Dark() = shoot("v0-glow-frame-06-dark", dark = true) { Stage(glowFrames[5], dark = true) }

    // ---------- 鎵嬫満鍖栵細320dp 绐勫睆 / 绯荤粺瀛楀彿 1.3 鍊?----------

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun v0Classic320dpLight() = shoot("v0-classic-320dp-light") { Stage(2_000L, dark = false) }

    @Test
    fun v0ClassicFont130Light() = shoot("v0-classic-font130-light", fontScale = 1.3f) {
        Stage(2_000L, dark = false)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun v0Classic320dpDarkFont130() = shoot("v0-classic-320dp-dark-font130", dark = true, fontScale = 1.3f) {
        Stage(2_000L, dark = true)
    }

    // ---------- tuning 涓夐」寮€鍏崇殑瀵圭収甯?----------

    @Test
    fun v0ClassicRotationOffLight() = shoot("v0-classic-rotation-off-light") {
        Stage(4_600L, dark = false, tuning = ClassicTuning(enableWordRotation = false))
    }

    @Test
    fun v0ClassicBreathingOffLight() = shoot("v0-classic-breathing-off-light") {
        Stage(2_000L, dark = false, tuning = ClassicTuning(breathingFloatMultiplier = 0.0))
    }

    @Test
    fun v0ClassicSpacingMaxLight() = shoot("v0-classic-spacing-max-light") {
        Stage(2_000L, dark = false, tuning = ClassicTuning(wordSpacing = 2.0))
    }

    // ---------- 背景层：封面取色 → 流体底 + 换歌柔焦 ----------

    /** 离线封面（app/src/test/resources/v0-cover-fixture.png）：不联网也能拍到「模糊封面 + overlay 渐变」这层。 */
    private val coverFixtureUrl: String? = run {
        val url = V0LyricStyleScreenshotTest::class.java.classLoader?.getResource("v0-cover-fixture.png")
        url?.toExternalForm()
    }

    @Test
    fun v0BackdropLight() = shoot("v0-backdrop-light") {
        // 封面层的本色：Folia 的 FluidBackground 在没有封面时只画 backgroundColor @0.8，
        // overlay 渐变要等封面画上来才合并（有封面那张见 v0-backdrop-cover-light）。
        VisualizerBackdrop(
            palette = CoverPalette(
                backgroundColor = Color(0xFFF3EAE4),
                primaryColor = Color(0xFF1C1917),
                accentColor = Color(0xFFB4553C),
                secondaryColor = Color(0xFFD8B49E),
                fromCover = true,
            ),
            source = null,
            dark = false,
            reduceMotion = true,
            modifier = Modifier.fillMaxSize(),
        ) { Box(Modifier.fillMaxSize()) }
    }

    @Test
    fun v0BackdropDark() = shoot("v0-backdrop-dark", dark = true) {
        VisualizerBackdrop(
            palette = CoverPalette(
                backgroundColor = Color(0xFF241715),
                primaryColor = Color(0xFFF4F4F5),
                accentColor = Color(0xFFB4553C),
                secondaryColor = Color(0xFF7A4E42),
                fromCover = true,
            ),
            source = null,
            dark = true,
            reduceMotion = true,
            modifier = Modifier.fillMaxSize(),
        ) { Box(Modifier.fillMaxSize()) }
    }

    @Test
    fun v0BackdropCoverLight() = shoot("v0-backdrop-cover-light") {
        VisualizerBackdrop(
            palette = CoverPalette(
                backgroundColor = Color(0xFFF3EAE4),
                primaryColor = Color(0xFF1C1917),
                accentColor = Color(0xFFB4553C),
                secondaryColor = Color(0xFF4A2F3E),
                fromCover = true,
            ),
            source = BundledTestCover,
            dark = false,
            reduceMotion = true,
            modifier = Modifier.fillMaxSize(),
        ) { Box(Modifier.fillMaxSize()) }
    }

    // ---------- 样式弹层（播放页「歌词样式」按钮 → 底部弹层） ----------
    @Test
    fun v0StyleSheetLight() = shoot("v0-style-sheet-light") {
        val strings = remember { testStyleStrings() }
        CompositionLocalProvider(LyricStringHost provides { id -> styleResolver(strings, id) }) {
            LyricStyleSheet(
                tuning = ClassicTuning(),
                strings = strings,
                styleName = "流光",
                onTuning = {},
                onDismiss = {},
                // V1a：样式列表两项，第二项「浮名」（styles.fume = 9 → styleResolver 出中文）。
                fumeName = "浮名",
            )
        }
    }

    @Test
    fun v0StyleSheetDark() = shoot("v0-style-sheet-dark", dark = true) {
        val strings = remember { testStyleStrings() }
        CompositionLocalProvider(LyricStringHost provides { id -> styleResolver(strings, id) }) {
            LyricStyleSheet(
                tuning = ClassicTuning(breathingFloatMultiplier = 1.6, wordSpacing = 1.25),
                strings = strings,
                styleName = "流光",
                onTuning = {},
                onDismiss = {},
                fumeName = "浮名",
                // 深色那帧顺手把选中态拍到「浮名」上，两张图合起来覆盖两种选中态。
                style = LyricStyle.FUME,
            )
        }
    }

    // ---------- 文案脚手架（feature:player 无资源表，测试里给中文 id→文案表） ----------

    private fun testStrings() = LyricStrings(
        back = 1,
        title = 2,
        play = 3,
        pause = 4,
        empty = 5,
        emptyHint = 6,
        loading = 7,
        loadFailed = 8,
        failedHint = 9,
        retry = 10,
        pureMusic = 11,
        waiting = 12,
        seekBar = 13,
        sourceLabel = 14,
        sourceNone = 15,
    )

    private fun resolver(strings: LyricStrings, id: Int): String = when (id) {
        strings.back -> "返回"
        strings.title -> "歌词"
        strings.play -> "播放"
        strings.pause -> "暂停"
        strings.empty -> "暂无歌词"
        strings.emptyHint -> "换一个音源或稍后再试"
        strings.loading -> "正在匹配最佳歌词..."
        strings.loadFailed -> "匹配最佳歌词失败"
        strings.failedHint -> "这首没有歌词可显示，先听音乐吧"
        strings.retry -> "重试"
        strings.pureMusic -> "纯音乐，无需匹配歌词"
        strings.waiting -> "等待音乐"
        strings.seekBar -> "播放进度"
        strings.sourceLabel -> "歌词"
        else -> "好"
    }

    private fun testStyleStrings() = LyricStyleStrings(
        title = 1,
        classic = 2,
        rotation = 3,
        breathing = 4,
        spacing = 5,
        reset = 6,
        desc = 7,
        close = 8,
        // V1a：「浮名」样式名（真实入口是 R.string.lyrics_style_fume，测试里给 id 9）。
        fume = 9,
    )

    private fun styleResolver(strings: LyricStyleStrings, id: Int): String = when (id) {
        strings.title -> "歌词样式"
        strings.classic -> "流光"
        strings.fume -> "浮名"
        strings.rotation -> "字词旋转"
        strings.breathing -> "呼吸浮动"
        strings.spacing -> "词距"
        strings.reset -> "恢复默认"
        strings.desc -> "当前渲染器附加参数"
        else -> "关闭"
    }

}
