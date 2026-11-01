package com.guangyue.feature.player.lyrics

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * U5 歌词页文案包。
 *
 * `feature:player` 模块没有资源表（歌词页的中文都在 `app/src/main/res/values/strings.xml` 的
 * `===== U5 歌词页 =====` 段），所以界面只认 [Int] 资源 id，由外壳（`app` 模块的 `lyricStrings()`）
 * 或截图测试注入真值。文案来源逐条标在 strings.xml 的注释里（`zh-CN.ts` 行号，或「自拟」）。
 */
@androidx.compose.runtime.Immutable
data class LyricStrings(
    /** 顶栏返回（Folia "collapse"/"back"）。 */
    val back: Int,
    /** 页标题「歌词」。 */
    val title: Int,
    /** 播放中 / 已暂停的播放键无障碍名。 */
    val play: Int,
    val pause: Int,
    /** 无歌词空态主句。 */
    val empty: Int,
    /** 无歌词空态副句。 */
    val emptyHint: Int,
    /** 正在加载。 */
    val loading: Int,
    /** 加载失败的兜底主句（真实失败原因来自歌词数据源，见 [failureText]）。 */
    val loadFailed: Int,
    /** 失败空态副句。 */
    val failedHint: Int,
    /** 重试按钮。 */
    val retry: Int,
    /** 纯音乐空态主句（Folia "bestLyricsPureMusic"）。 */
    val pureMusic: Int,
    /** 舞台上「等待音乐」占位（Folia 舞台上这个文案是空串，广乐自拟四字）。 */
    val waiting: Int,
    /** 进度条无障碍名。 */
    val seekBar: Int,
    /** 歌词来源标签（"lyrics"），跟来源名拼成「歌词 · 网易云」。 */
    val sourceLabel: Int,
    /** 来源未知时的占位（Folia localMusic "statusNone"「无」）。 */
    val sourceNone: Int,
) {
    companion object
}

/** 歌词空态的三态：加载中 / 失败 / 没有歌词。 */
enum class LyricEmptyKind { Loading, Failed, NoLyrics }

/**
 * 现在该显示哪种空态。
 *
 * 顺序有讲究：正在取词时先给「加载中」（Folia 的 `matchingBestLyrics` 也是这个语义）；
 * 取完还给不出词时再看失败原因；都没有才是「暂无歌词」。
 */
fun lyricEmptyKind(
    lineCount: Int,
    loading: Boolean,
    failed: Boolean,
): LyricEmptyKind? = when {
    lineCount > 0 -> null
    loading -> LyricEmptyKind.Loading
    failed -> LyricEmptyKind.Failed
    else -> LyricEmptyKind.NoLyrics
}

/** 纯音乐判定：数据源明说这首歌没有词（Folia 的 `bestLyricsPureMusic`）。 */
fun lyricIsPureMusic(noLyricsReason: String?): Boolean = noLyricsReason?.contains("纯音乐") == true

/**
 * 失败空态的副句：数据源给的原始提示优先（`LyricsViewModel` 失败时写的是
 * 「歌词获取失败：…」/「暂无歌词」），没有就退回通用的 [LyricStrings.failedHint]。
 */
fun failureText(reason: String?, fallback: String): String =
    reason?.takeIf { it.isNotBlank() } ?: fallback

/** 页头的歌曲副标题：歌手 · 来源；一首都没有时给 Folia 的「无」。 */
fun lyricSourceLine(artist: String?, source: String?): String {
    val who = artist?.trim().orEmpty()
    val where = source?.trim().orEmpty()
    return when {
        who.isEmpty() && where.isEmpty() -> ""
        where.isEmpty() -> who
        who.isEmpty() -> where
        else -> "$who · $where"
    }
}

/**
 * 文案 id 到字符串的映射：由 `app` 侧 `lyricStrings()` 注入取值。
 * 截图用例也预先注入（没有 `Context`），否则注入点拿不到文案。
 */
val LyricStringHost = androidx.compose.runtime.staticCompositionLocalOf<(Int) -> String> {
    { "" }
}
