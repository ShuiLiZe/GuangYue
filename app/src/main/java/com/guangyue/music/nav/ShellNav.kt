package com.guangyue.music.nav

/**
 * 底栏外壳的纯逻辑（不依赖 Android / Compose，方便单测）。
 *
 * 手机版把 Folia 桌面的侧栏换成「首页 / 搜索 / 我的」底部三 Tab（RULES.md：只改容器）。
 * 这里只放「哪些路由算顶层 Tab」「返回键该去哪」「迷你条该不该占位置」这类判断，
 * 界面本身在 Root.kt / NavHost.kt。
 */
object ShellNav {

    /**
     * 三个顶层 Tab 的路由。
     *
     * 文案不在这里拼：Tab 名走 `stringResource`（`tab_home` / `tab_search` / `tab_profile`），
     * 只有「哪些路由是顶层」这一件事是结构信息。
     */
    val topLevelRoutes: List<String> = listOf(Routes.HOME, Routes.SEARCH, Routes.PROFILE)

    /** 底栏只在顶层 Tab 的根页出现；二级页（插件管理 / 设置 / 下载 / 许可 / 关于）与其上全屏页隐藏。 */
    fun showsBottomBar(route: String?): Boolean = route != null && route in topLevelRoutes

    /** 迷你条只跟着「有没有在播的东西」走；没有播放时它整个不占高度。 */
    fun showsMiniPlayer(item: Any?): Boolean = item != null

    /**
     * 系统返回：子页先返回（交给 NavController 默认行为），Tab 根页返回回首页 Tab，首页再返回退出。
     *
     * 返回 null 表示「这一层不拦截，让系统默认处理」——首页根页返回 null（于是退出 App）。
     */
    fun backTarget(route: String?, isTopLevel: Boolean): String? {
        if (route == null) return null
        if (!isTopLevel) return null
        return if (route == Routes.HOME) null else Routes.HOME
    }
}