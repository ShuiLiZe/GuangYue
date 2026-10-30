package com.guangyue.feature.search

import androidx.annotation.StringRes

/**
 * U3：搜索页的界面文案。
 *
 * 文案本体一律在 `app/src/main/res/values/strings.xml`（那里标注了每条取自
 * `_folia-src/src/i18n/locales/zh-CN.ts` 的哪个键，或标「自拟」），`feature/search` 通过
 * [SearchStrings] 拿到资源 id，再由 app 层的 `stringResource(SearchStrings.of(this).xxx)` 取字。
 *
 * 为什么不直接在 feature 里 `stringResource(R.string.xxx)`：`R` 生成在 app 模块（namespace
 * `com.guangyue.music`），feature 模块引用不到。用 id 而不是把中文写进 feature，是为了保持
 * 「界面文字只有一个来源（strings.xml）」这条规矩。
 */
data class SearchStrings(
    @StringRes val placeholder: Int,
    @StringRes val clear: Int,
    @StringRes val searching: Int,
    @StringRes val noResults: Int,
    @StringRes val emptySubtitle: Int,
    @StringRes val emptyCategory: Int,
    @StringRes val retry: Int,
    @StringRes val history: Int,
    @StringRes val suggestions: Int,
    @StringRes val addToPlaylist: Int,
    @StringRes val download: Int,
    @StringRes val noSource: Int,
    @StringRes val noSourceHint: Int,
    @StringRes val openPlugins: Int,
) {
    companion object {
        /**
         * 由 app 层把 `com.guangyue.music.R.string.*` 填进来。
         *
         * 默认值是 [DEFAULTS]——空实现只出现在「忘了填」的单元测试里，真实界面在
         * `GuangYueNavHost` 里注入。
         */
        val DEFAULTS = SearchStrings(
            placeholder = 0,
            clear = 0,
            searching = 0,
            noResults = 0,
            emptySubtitle = 0,
            emptyCategory = 0,
            retry = 0,
            history = 0,
            suggestions = 0,
            addToPlaylist = 0,
            download = 0,
            noSource = 0,
            noSourceHint = 0,
            openPlugins = 0,
        )
    }
}
