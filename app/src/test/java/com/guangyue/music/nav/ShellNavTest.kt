package com.guangyue.music.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U2 外壳纯逻辑单测：底栏出现条件、迷你条显示条件、系统返回目标。
 *
 * 这三条是「手机上把 Folia 桌面外壳改掉」的核心行为，不依赖 Compose 渲染，直接在 JVM 上断。
 */
class ShellNavTest {

    // ---------- 底栏：只在三个顶层 Tab 根页出现 ----------

    @Test
    fun bottomBarShowsOnAllThreeTopLevelRoots() {
        assertTrue(ShellNav.showsBottomBar(Routes.HOME))
        assertTrue(ShellNav.showsBottomBar(Routes.SEARCH))
        assertTrue(ShellNav.showsBottomBar(Routes.PROFILE))
    }

    @Test
    fun bottomBarHiddenOnEveryNonTopLevelDestination() {
        // 二级页与全屏页：都不该出现底栏（Folia 只在主视图挂底栏）。
        listOf(
            Routes.PLAYER,
            Routes.LYRIC_FLOW,
            Routes.LYRIC_STAGE,
            Routes.PLUGINS,
            Routes.LX_SOURCES,
            Routes.DOWNLOADS,
            Routes.SETTINGS,
            Routes.LICENSES,
            Routes.ABOUT,
        ).forEach { route ->
            assertFalse("$route 不该有底栏", ShellNav.showsBottomBar(route))
        }
    }

    @Test
    fun bottomBarHiddenBeforeFirstDestinationResolves() {
        // 首帧 `currentBackStackEntryAsState()` 还没值，route 为 null，不能崩也不能闪一下底栏。
        assertFalse(ShellNav.showsBottomBar(null))
    }

    @Test
    fun topLevelRoutesAreExactlyThreeInTabOrder() {
        assertEquals(listOf(Routes.HOME, Routes.SEARCH, Routes.PROFILE), ShellNav.topLevelRoutes)
    }

    // ---------- 迷你条：只在「有播放内容」时占位置 ----------

    @Test
    fun miniPlayerHiddenWithoutPlaybackItem() {
        // 没播过 → 迷你条整条不占空间（不是画一条空壳）。
        assertFalse(ShellNav.showsMiniPlayer(null))
    }

    @Test
    fun miniPlayerShownOnceThereIsAPlaybackItem() {
        assertTrue(ShellNav.showsMiniPlayer("qq:夜曲"))
    }

    // ---------- 系统返回：子页先返回 → Tab 根页回首页 → 首页退出 ----------

    @Test
    fun backOnHomeRootIsNotInterceptedSoAppExits() {
        // 首页根页返回 null = 不拦截，交给系统默认（退出 App）。
        assertEquals(null, ShellNav.backTarget(Routes.HOME, isTopLevel = true))
    }

    @Test
    fun backOnSearchOrProfileRootGoesToHomeTab() {
        assertEquals(Routes.HOME, ShellNav.backTarget(Routes.SEARCH, isTopLevel = true))
        assertEquals(Routes.HOME, ShellNav.backTarget(Routes.PROFILE, isTopLevel = true))
    }

    @Test
    fun backOnChildPageIsNotInterceptedSoNavControllerPopsFirst() {
        // 子页（插件管理 / 设置 / 下载…）必须让 NavController 先 pop，回到它自己的 Tab。
        assertEquals(null, ShellNav.backTarget(Routes.PLUGINS, isTopLevel = false))
        assertEquals(null, ShellNav.backTarget(Routes.SETTINGS, isTopLevel = false))
        assertEquals(null, ShellNav.backTarget(Routes.PLAYER, isTopLevel = false))
    }

    // ---------- U5：歌词页是播放页的下级页面 ----------

    @Test
    fun backFromLyricsPagePopsToPlayerFirst() {
        // 歌词页非顶层：返回先由 NavController pop 回播放页，不跳到首页 Tab。
        assertEquals(null, ShellNav.backTarget(Routes.LYRIC_FLOW, isTopLevel = false))
    }

    @Test
    fun lyricsPageDoesNotCarryTheBottomBar() {
        assertEquals(null, ShellNav.backTarget(Routes.LYRIC_FLOW, ShellNav.showsBottomBar(Routes.LYRIC_FLOW)))
    }

    @Test
    fun backBeforeFirstDestinationResolvesIsNotIntercepted() {
        assertEquals(null, ShellNav.backTarget(null, isTopLevel = ShellNav.showsBottomBar(null)))
    }

    /**
     * 组合场景：从「我的 → 插件管理」按返回。
     *
     * 底栏判据用「当前路由是不是顶层」，插件管理不是顶层 → 不拦（先 pop 回我的），
     * 再按一次才落在「我的」根页（顶层且非首页）→ 回首页 Tab。
     */
    @Test
    fun backFromPluginManagerPopsFirstThenFallsBackToHomeTab() {
        val inPlugins = Routes.PLUGINS
        assertEquals(null, ShellNav.backTarget(inPlugins, ShellNav.showsBottomBar(inPlugins)))

        val onProfileRoot = Routes.PROFILE
        assertEquals(Routes.HOME, ShellNav.backTarget(onProfileRoot, ShellNav.showsBottomBar(onProfileRoot)))
    }
}