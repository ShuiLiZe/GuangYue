package com.guangyue.core.data.lyrics

/**
 * 11-18：Folia「歌词 → 歌词过滤」（src/utils/lyrics/lyricLineRules.ts）的手机子集：
 * 两个常用预设（版权声明 / 歌词来源说明）、一条「包含这些文字」规则、一条手写正则（高级）。
 * 规则只在显示时套用（不动缓存与解析），任一条命中整行文字即删掉这一行；文字规则忽略大小写与全角
 * （ＯＰ： 当 OP:），手写正则原样编译，写错了不删任何行（Folia writtenInvalid）。
 * ✋ Folia 还有「开头是 / 整行是 / 结尾是」「后接冒号」「仅首尾」、按行点选删除同类、听过的歌预览——手机先不做。
 */
data class LyricFilterSettings(
    val copyright: Boolean = false,
    val lyricSource: Boolean = false,
    /** 「包含」规则的文字：按换行、逗号、顿号分开。 */
    val containsWords: String = "",
    /** 手写正则（Folia「手写规则（高级）」）。 */
    val written: String = "",
) {
    val isEmpty: Boolean get() = !copyright && !lyricSource && words(containsWords).isEmpty() && written.isBlank()
}

object LyricLineRules {
    /** Folia LYRIC_LINE_PRESETS（原样）。 */
    val COPYRIGHT = listOf("版权所有", "未经许可", "未经授权", "不得翻唱", "禁止翻唱", "侵权必究", "all rights reserved", "copyright", "©", "℗")
    val LYRIC_SOURCE = listOf("歌词贡献者", "翻译贡献者", "歌词来源", "歌词来自", "歌词提供", "歌词制作", "歌词校对", "lrc by")

    /** 全角 ASCII（！～）折成半角，一个字换一个字（Folia foldWidth）。 */
    fun foldWidth(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '\uFF01'..'\uFF5E') (c.code - 0xFEE0).toChar() else c)
    }

    private fun wordsRegex(words: List<String>): Regex? {
        val ws = words.map { foldWidth(it).trim() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
        if (ws.isEmpty()) return null
        return Regex(ws.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    /** 手写正则写错时的原因；没写或能编译返回 null。 */
    fun writtenError(pattern: String): String? {
        if (pattern.isBlank()) return null
        return runCatching { Regex(pattern) }.exceptionOrNull()?.message?.lineSequence()?.firstOrNull()
    }

    /** 文字规则（折全角后比对）与手写正则（原文比对）。 */
    fun compile(s: LyricFilterSettings): Pair<List<Regex>, Regex?> {
        val folded = buildList {
            if (s.copyright) wordsRegex(COPYRIGHT)?.let(::add)
            if (s.lyricSource) wordsRegex(LYRIC_SOURCE)?.let(::add)
            wordsRegex(words(s.containsWords))?.let(::add)
        }
        val written = s.written.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
        return folded to written
    }

    fun removes(text: String, compiled: Pair<List<Regex>, Regex?>): Boolean {
        if (text.isBlank()) return false
        val f = foldWidth(text)
        return compiled.first.any { it.containsMatchIn(f) } || compiled.second?.containsMatchIn(text) == true
    }

    /** 删掉命中的行（空白行 / 间奏不动）。没有规则时原样返回。 */
    fun apply(lyric: Lyric, s: LyricFilterSettings): Lyric {
        if (s.isEmpty || lyric.lines.isEmpty()) return lyric
        val c = compile(s)
        val kept = lyric.lines.filterNot { removes(it.text, c) }
        return if (kept.size == lyric.lines.size) lyric else lyric.copy(lines = kept)
    }
}

internal fun words(text: String): List<String> =
    text.split('\n', ',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
