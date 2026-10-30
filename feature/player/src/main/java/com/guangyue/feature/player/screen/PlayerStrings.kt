package com.guangyue.feature.player.screen

/**
 * U4 · 播放页文案包。
 *
 * `feature:player` 模块**没有资源表**（字符串都在 `app/src/main/res/values/strings.xml`），
 * 所以 UI 只认 [Int] 资源 id，由外壳（`app` 模块的 `playerStrings()`）或截图测试提供真值。
 * 这样播放页既能进 App，也能在 Robolectric 截图测试里脱离资源单独渲染。
 *
 * 文案全部中文，来源见 `docs/ui-u4/report.md` §4（逐条标了 `zh-CN.ts` 行号或「自拟」）。
 */
@androidx.compose.runtime.Immutable
data class PlayerStrings(
    /** 顶栏微标题「正在播放」。 */
    val nowPlaying: Int,
    /** 收起播放页（返回圆钮的无障碍名）。 */
    val collapse: Int,
    val previous: Int,
    val next: Int,
    val play: Int,
    val pause: Int,
    val shuffle: Int,
    /** 三态播放模式：顺序 / 单曲 / 随机。 */
    val modeSequential: Int,
    val modeLoopOne: Int,
    val modeShuffle: Int,
    val like: Int,
    val liked: Int,
    val addToPlaylist: Int,
    val queue: Int,
    val lyrics: Int,
    /** 歌词入口的占位提示（U5 才做）。 */
    val lyricsSoon: Int,
    /** 队列标题，带 `%1$d` 占位。 */
    val queueCount: Int,
    val queueEmpty: Int,
    val noSong: Int,
    val unknownArtist: Int,
    val quality: Int,
    val retryHint: Int,
    val closeQueue: Int,
    val seekBar: Int,
    val coverOf: Int,
)

/**
 * 收藏按钮的三态：未收藏 / 已收藏 / 提交中。
 *
 * 「提交中」不是装饰：`TrackRepository.setFavorite` 走 DAO，连点会写两次相反的最终态，
 * 所以在途期间按钮要挡住第二次点击（[nextLikeTarget] 返回 null 正是这个意思）。
 */
enum class LikeState { Idle, Liked, Pending }

/**
 * 点收藏之后要写入的目标值；`null` = 当前不该响应（提交中）。
 * 与 [PlayerMode] 的 `nextPlayerModeTarget` 同构：给目标态，不给「翻转」。
 */
fun nextLikeTarget(current: LikeState): Boolean? = when (current) {
    LikeState.Idle -> true
    LikeState.Liked -> false
    LikeState.Pending -> null
}

/** 当前这颗心是不是「已收藏」的实心态（决定图标描边/填色与无障碍名）。 */
fun likeActionIsUnlike(current: LikeState): Boolean = current == LikeState.Liked
