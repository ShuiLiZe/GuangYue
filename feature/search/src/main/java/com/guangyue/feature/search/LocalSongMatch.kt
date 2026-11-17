package com.guangyue.feature.search

/**
 * 11-17：本地搜索支持拼音。Folia 的命令窗 / 设置搜索用 pinyin-pro 做全拼与首字母匹配（commandSearchIndex.ts），
 * 本地歌曲搜索这里照同一个思路：歌名、歌手、专辑任一项命中原文、全拼（去空格）或首字母都算。
 *
 * 纯函数，拼音由调用方给（安卓 API 29+ 用 ICU「Han-Latin」，更低版本给 null 只按原文）。
 * [toPinyin] 返回按音节空格分开的小写拉丁串，如「晴天」→「qing tian」。
 */
object LocalSongMatch {
    /** 查询词规整：小写、去空白；全是 ASCII 字母数字时才走拼音匹配。 */
    fun normalize(query: String): String = query.lowercase().filterNot { it.isWhitespace() }

    fun isPinyinQuery(q: String): Boolean = q.isNotEmpty() && q.all { it in 'a'..'z' || it in '0'..'9' || it == '\'' }

    /** 一段文字的拼音键：全拼（去空格）与首字母。没有汉字时返回 null（原文匹配已覆盖）。 */
    fun keys(text: String, toPinyin: ((String) -> String?)?): Pair<String, String>? {
        if (toPinyin == null || text.none { it.code in 0x3400..0x9FFF }) return null
        val py = toPinyin(text)?.lowercase() ?: return null
        val tokens = py.split(' ', '\t', '-', '·').filter { it.isNotBlank() }
        val full = tokens.joinToString("") { t -> t.filter { it.isLetterOrDigit() } }
        val initials = tokens.mapNotNull { t -> t.firstOrNull { it.isLetterOrDigit() } }.joinToString("")
        return full to initials
    }

    fun matches(query: String, fields: List<String>, pinyinOf: (String) -> Pair<String, String>?): Boolean {
        val raw = query.trim()
        if (raw.isEmpty()) return false
        if (fields.any { it.contains(raw, ignoreCase = true) }) return true
        val q = normalize(raw)
        if (fields.any { it.lowercase().filterNot { c -> c.isWhitespace() }.contains(q) }) return true
        if (!isPinyinQuery(q)) return false
        val qq = q.replace("'", "")
        return fields.any { f ->
            val k = pinyinOf(f) ?: return@any false
            k.first.contains(qq) || k.second.contains(qq)
        }
    }
}
