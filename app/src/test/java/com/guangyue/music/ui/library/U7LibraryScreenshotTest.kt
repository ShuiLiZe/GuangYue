package com.guangyue.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.music.nav.libraryStrings
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U7 音乐库页的 JVM 截图（Robolectric 真渲染 + Roborazzi），照 U1/U2/U5/U6 的写法：
 * 360×800dp / xhdpi 打底，输出目录由 `app/build.gradle.kts` 的 `roborazzi.output.dir`
 * 决定（U7 段为 `docs/ui-u7`），只有 `GUANGYUE_RECORD_SCREENSHOTS=true` 时才写盘。
 *
 * 覆盖（验收清单原样对齐）：浅 / 深两套、空态、失败态、320dp 窄屏、字体 1.3 倍，
 * 再加「装有迷你条」的一张，看手势条 inset 有没有遮内容。
 *
 * 文案走真资源：直接调 `nav/libraryStrings()`（Robolectric 读 `strings.xml`），
 * 与生产路径同一份中文串，测试里不另写一遍字面量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U7LibraryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: java.io.File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) java.io.File("build/roborazzi-u7") else java.io.File(prop)
        dir.mkdirs()
        dir
    }

    private fun shoot(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
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
        compose.onRoot().captureRoboImage(java.io.File(outDir, "$name.png"))
    }

    // ---------- 数据（与 U6 同一批，全是中文） ----------

    private val playlists = listOf(
        HomeTile("playlist:1", "夜间城市流行", "本地歌单", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:2", "通勤路上", "早高峰的十首歌", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:3", "深夜书房", "本地歌单", null, HomeTileKind.PLAYLIST),
    )

    private val favorites = listOf(
        HomeTile("track:1", "夜曲", "周杰伦", null, HomeTileKind.TRACK),
        HomeTile("track:2", "红豆", "王菲", null, HomeTileKind.TRACK),
    )

    private val local = listOf(
        HomeTile("track:11", "平凡之路", "朴树", null, HomeTileKind.TRACK),
        HomeTile("track:12", "海阔天空", "Beyond", null, HomeTileKind.TRACK),
    )

    private val recent = listOf(
        HomeTile("track:21", "晴天", "周杰伦", null, HomeTileKind.TRACK),
        HomeTile("track:22", "稻香", "周杰伦", null, HomeTileKind.TRACK),
    )

    // ---------- 整屏骨架：二级页 = 内容 + 迷你条（没有底栏） ----------

    @Composable
    private fun Shell(withMini: Boolean, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            if (withMini) {
                Box(Modifier.padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.s)) { Mini() }
                Box(Modifier.height(GESTURE))
            }
        }
    }

    @Composable
    private fun Mini() {
        FoliaMiniPlayer(
            title = "夜曲",
            subtitle = "周杰伦",
            artwork = null,
            progress = 0.35f,
            isPlaying = true,
            onTogglePlay = {},
            onOpen = {},
            onNext = {},
            playLabel = "播放",
            pauseLabel = "暂停",
            nextLabel = "下一首",
        )
    }

    @Composable
    private fun Library(
        state: LibraryState,
        playlists: List<HomeTile> = this@U7LibraryScreenshotTest.playlists,
        favorites: List<HomeTile> = this@U7LibraryScreenshotTest.favorites,
        local: List<HomeTile> = this@U7LibraryScreenshotTest.local,
        recent: List<HomeTile> = this@U7LibraryScreenshotTest.recent,
    ) {
        LibraryPageScreen(
            playlists = playlists,
            favorites = favorites,
            local = local,
            recent = recent,
            strings = libraryStrings(),
            state = state,
            onOpenTile = {},
            onOpenSearch = {},
            onOpenPlayer = {},
            onRetry = {},
            onBack = {},
        )
    }

    // ---------- 浅 / 深 ----------

    @Test
    fun libraryLight() = shoot("u7-library-light") { Library(LibraryState.Content) }

    @Test
    fun libraryDark() = shoot("u7-library-dark", dark = true) { Library(LibraryState.Content) }

    @Test
    fun libraryWithMiniPlayerLight() = shoot("u7-mini-light") {
        Shell(withMini = true) { Library(LibraryState.Content) }
    }

    // ---------- 加载中 ----------

    @Test
    fun libraryLoadingLight() = shoot("u7-loading-light") {
        Library(LibraryState.Loading, playlists = emptyList(), favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    // ---------- 空 ----------

    @Test
    fun libraryEmptyLight() = shoot("u7-empty-light") {
        Library(LibraryState.Empty, playlists = emptyList(), favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    @Test
    fun libraryEmptyDark() = shoot("u7-empty-dark", dark = true) {
        Library(LibraryState.Empty, playlists = emptyList(), favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    @Test
    fun librarySectionEmptyLight() = shoot("u7-section-empty-light") {
        Library(LibraryState.Content, playlists = playlists, favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    // ---------- 失败 ----------

    @Test
    fun libraryFailedLight() = shoot("u7-failed-light") {
        Library(LibraryState.Failed, playlists = emptyList(), favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    @Test
    fun libraryFailedDark() = shoot("u7-failed-dark", dark = true) {
        Library(LibraryState.Failed, playlists = emptyList(), favorites = emptyList(), local = emptyList(), recent = emptyList())
    }

    // ---------- 320dp 窄屏 / 字体 1.3 倍 ----------

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun librarySmallScreen320dp() = shoot("u7-320dp-light") {
        Shell(withMini = true) { Library(LibraryState.Content) }
    }

    @Test
    fun libraryFontScale130() = shoot("u7-font130-light", fontScale = 1.3f) {
        Shell(withMini = true) { Library(LibraryState.Content) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun librarySmallScreenFontScale130Dark() =
        shoot("u7-320dp-font130-dark", dark = true, fontScale = 1.3f) {
            Shell(withMini = true) { Library(LibraryState.Content) }
        }

    private companion object {
        const val RootTag = "u7-library"

        /** Android 手势条高度：24dp。 */
        val GESTURE = 24.dp

        /** Android 三键导航栏高度：48dp（未在用例里用，留给后续窄屏 inset 用例）。 */
        val THREE_KEY = 48.dp
    }
}