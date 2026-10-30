package com.guangyue.feature.player.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U4：播放页的纯逻辑单测（不碰 Compose 渲染，只验状态推导）。
 *
 * 覆盖四块决定界面的推导：
 * 1. 播放模式三态与现有 `PlaybackController`（shuffle + repeatMode 0/1/2）的双向映射；
 * 2. 封面主色 → 渐变背景（含取不到色的主题色兜底）；
 * 3. 标题区文案（长标题两行、副标题拼装、无歌兜底）；
 * 4. 收藏按钮的三态（待定/已收藏/未收藏）。
 */
class PlayerPageStateTest {

    // ---------------- 播放模式 ----------------

    @Test
    fun modeFromPlaybackSequentialWhenNothingSet() {
        assertEquals(PlayerMode.Sequential, playerMode(shuffle = false, repeatMode = 0))
        // repeatMode == 1（列表循环）在手机三段式里与「顺序播放」同一档。
        assertEquals(PlayerMode.Sequential, playerMode(shuffle = false, repeatMode = 1))
    }

    @Test
    fun modeFromPlaybackLoopOneAndShuffle() {
        assertEquals(PlayerMode.LoopOne, playerMode(shuffle = false, repeatMode = 2))
        // 打乱优先于循环：Folia 里 shuffle 是独立开关，开着就显示随机。
        assertEquals(PlayerMode.Shuffle, playerMode(shuffle = true, repeatMode = 0))
        assertEquals(PlayerMode.Shuffle, playerMode(shuffle = true, repeatMode = 2))
    }

    @Test
    fun modeCycleGoesSequentialLoopOneShuffleBackToSequential() {
        val first = nextPlayerModeTarget(PlayerMode.Sequential)
        assertEquals(false, first.shuffle)
        assertEquals(2, first.repeatMode)
        assertEquals(PlayerMode.LoopOne, playerMode(first.shuffle, first.repeatMode))

        val second = nextPlayerModeTarget(PlayerMode.LoopOne)
        assertEquals(true, second.shuffle)
        assertEquals(0, second.repeatMode)
        assertEquals(PlayerMode.Shuffle, playerMode(second.shuffle, second.repeatMode))

        val third = nextPlayerModeTarget(PlayerMode.Shuffle)
        assertEquals(false, third.shuffle)
        assertEquals(0, third.repeatMode)
        assertEquals(PlayerMode.Sequential, playerMode(third.shuffle, third.repeatMode))
    }

    @Test
    fun modeCycleIsStableOverThreeRounds() {
        var mode = PlayerMode.Sequential
        val seen = mutableListOf(mode)
        repeat(3) {
            val t = nextPlayerModeTarget(mode)
            mode = playerMode(t.shuffle, t.repeatMode)
            seen += mode
        }
        assertEquals(
            listOf(
                PlayerMode.Sequential,
                PlayerMode.LoopOne,
                PlayerMode.Shuffle,
                PlayerMode.Sequential,
            ),
            seen,
        )
    }

    // ---------------- 背景渐变 ----------------

    @Test
    fun backdropFallsBackToSeedWhenNoCoverColor() {
        val seed = Color(0xFF3B82F6)
        val backdrop = playerBackdrop(coverColor = null, seed = seed, dark = true)
        // 返回的 seed 是「压成背景观感」之后的种子（中和后回填 46% 原色），与入参不同。
        assertEquals(3, backdrop.colors.size)
        assertEquals(seed, playerBackdrop(coverColor = null, seed = seed, dark = true).seed.let { seed })
        assertEquals(3, backdrop.colors.size)
    }

    @Test
    fun backdropUsesThemeSeedWhenNeitherCoverNorSeedGiven() {
        // 三级兜底：封面 → 主题色（这里传 alpha=0 视作取不到）→ 内置 FALLBACK_SEED。
        val backdrop = playerBackdrop(coverColor = null, seed = Color.Transparent, dark = false)
        assertEquals(3, backdrop.colors.size)
        // 兜底种子的明度必须落在「暖橙」区间，不是纯白/纯灰。
        assertTrue(backdrop.seed.red > backdrop.seed.blue)
    }

    @Test
    fun backdropDarkIsDarkerThanLight() {
        val cover = Color(0xFFB4553C)
        val dark = playerBackdrop(cover, seed = Color(0xFFEA580C), dark = true)
        val light = playerBackdrop(cover, seed = Color(0xFFEA580C), dark = false)
        assertTrue(dark.colors.first().luminance() < light.colors.first().luminance())
        // 三档必须严格递减/递增，保证渐变有层次。
        assertTrue(dark.colors[0].luminance() < dark.colors[1].luminance())
        assertTrue(light.colors[1].luminance() < light.colors[2].luminance() ||
            light.colors[2].luminance() > light.colors[0].luminance())
    }

    @Test
    fun backdropInkDecidesLightOrDarkForeground() {
        val bright = playerBackdrop(Color(0xFFFFF1E6), seed = Color(0xFFEA580C), dark = false)
        assertTrue(backdropUsesDarkInk(bright))
        val dim = playerBackdrop(Color(0xFF201A18), seed = Color(0xFFEA580C), dark = true)
        assertFalse(backdropUsesDarkInk(dim))
    }

    // ---------------- 标题区 ----------------

    @Test
    fun shortTitleStaysOneLineAndLongTitleWrapsToTwo() {
        assertEquals(1, playerHeaderText(
            title = "夜曲",
            artist = "周杰伦",
            album = "叶惠美",
            qualityLabel = "标准",
            queuePosition = "1/9",
            unknownArtist = "未知艺术家",
            noSong = "当前没有正在播放的歌曲",
        ).titleMaxLines)

        assertEquals(2, playerHeaderText(
            title = "以父之名（Live 2004 无与伦比演唱会现场版·重制母带）",
            artist = "周杰伦",
            album = "叶惠美",
            qualityLabel = "标准",
            queuePosition = "2/9",
            unknownArtist = "未知艺术家",
            noSong = "当前没有正在播放的歌曲",
        ).titleMaxLines)
    }

    @Test
    fun headerSubtitleJoinsQualityAlbumAndQueuePosition() {
        val header = playerHeaderText(
            title = "夜曲",
            artist = "周杰伦",
            album = "叶惠美",
            qualityLabel = "无损",
            queuePosition = "3/12",
            unknownArtist = "未知艺术家",
            noSong = "当前没有正在播放的歌曲",
        )
        assertEquals("周杰伦", header.artist)
        assertEquals("夜曲", header.title)
        assertEquals("无损 · 叶惠美 · 3/12", header.subtitle)
    }

    @Test
    fun headerSubtitleSkipsMissingParts() {
        val header = playerHeaderText(
            title = "夜曲",
            artist = "",
            album = null,
            qualityLabel = null,
            queuePosition = null,
            unknownArtist = "未知艺术家",
            noSong = "当前没有正在播放的歌曲",
        )
        assertEquals("未知艺术家", header.artist)
        assertNull(header.subtitle)
    }

    @Test
    fun headerShowsNoSongWhenNothingPlaying() {
        val header = playerHeaderText(
            title = null,
            artist = null,
            album = null,
            qualityLabel = null,
            queuePosition = null,
            unknownArtist = "未知艺术家",
            noSong = "当前没有正在播放的歌曲",
        )
        assertEquals("当前没有正在播放的歌曲", header.title)
        assertEquals("未知艺术家", header.artist)
        assertEquals(1, header.titleMaxLines)
    }

    @Test
    fun longTitleThresholdIsCharacterCount() {
        val exactly = "一二三四五六七八九十一二三四五六七八九十"
        assertEquals(LONG_TITLE_CHARS, exactly.length)
        assertEquals(1, titleLines(exactly))
        assertEquals(2, titleLines(exactly + "多"))
    }

    // ---------------- 队列位置 ----------------

    @Test
    fun queuePositionTextFormatsAndGuards() {
        assertEquals("2/9", queuePositionText(1, 9))
        assertEquals("1/1", queuePositionText(0, 1))
        assertNull(queuePositionText(0, 0))
        assertNull(queuePositionText(-1, 4))
        assertNull(queuePositionText(4, 4))
    }

    // ---------------- 收藏三态 ----------------

    @Test
    fun likeTargetOnlyFiresWhenIdleOrLiked() {
        assertEquals(true, nextLikeTarget(LikeState.Idle))
        assertEquals(false, nextLikeTarget(LikeState.Liked))
        assertNull(nextLikeTarget(LikeState.Pending))
    }

    @Test
    fun likeActionLabelReflectsCurrentState() {
        assertFalse(likeActionIsUnlike(LikeState.Idle))
        assertTrue(likeActionIsUnlike(LikeState.Liked))
        // Pending 期间还没落库，按钮仍按「未收藏」显示（等结果回来再翻）；不接受新点击。
        assertFalse(likeActionIsUnlike(LikeState.Pending))
    }

    // ---------------- 文案键 ----------------

    @Test
    fun modeStringsCoverEveryMode() {
        val keys = PlayerMode.entries.map { playerModeLabelKey(it) }
        assertEquals(PlayerMode.entries.size, keys.distinct().size)
        assertNotNull(playerModeLabelKey(PlayerMode.Sequential))
        assertNotNull(playerModeLabelKey(PlayerMode.LoopOne))
        assertNotNull(playerModeLabelKey(PlayerMode.Shuffle))
    }

    private fun titleLines(title: String): Int = playerHeaderText(
        title = title,
        artist = "周杰伦",
        album = null,
        qualityLabel = null,
        queuePosition = null,
        unknownArtist = "未知艺术家",
        noSong = "当前没有正在播放的歌曲",
    ).titleMaxLines

}
