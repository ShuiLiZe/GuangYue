package com.guangyue.music.nav

import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guangyue.core.player.PlaybackController
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.LocalImeHidesMiniPlayer
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.design.collapseOnIme
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.music.R

/**
 * App 外壳：Folia 纯色底 + 内容区 + 迷你条 + 底部三个 Tab。
 *
 * 底栏只在首页 / 搜索 / 我的三个顶层路由出现；播放、歌词、插件管理、下载、设置、许可、关于
 * 都是全屏或二级页，进入即隐藏底栏（与 Folia 只在主视图挂底栏的做法一致）。
 * 系统级 insets：顶栏由各页自己的 [com.guangyue.feature.library.design.FoliaTopBar] 吃，
 * 底栏由 [FoliaBottomNav] 自己吃 navigationBars，键盘弹起时底栏与迷你条整条让位。
 */
@Composable
fun GuangYueRoot(
    playback: PlaybackController,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    // 0.0.9（U2）：Tab 名走 strings.xml，不再在代码里写中文（values/ 即中文默认）。
    val tabItems = listOf(
        FoliaNavItem(Routes.HOME, stringResource(R.string.tab_home), GuangYueIcons.House),
        FoliaNavItem(Routes.SEARCH, stringResource(R.string.tab_search), GuangYueIcons.Search),
        FoliaNavItem(Routes.PROFILE, stringResource(R.string.tab_profile), GuangYueIcons.User),
    )
    // 系统返回：子页先返回（不拦，交给 NavHost 默认回退）；Tab 根页返回回到首页 Tab；
    // 首页根页不拦，直接退出 App。
    val isTopLevel = ShellNav.showsBottomBar(currentRoute)
    BackHandler(enabled = isTopLevel && currentRoute != Routes.HOME) {
        navController.navigate(Routes.HOME) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    MarbleBackground(dark = darkMode, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            GuangYueNavHost(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                playback = playback,
                navController = navController,
                // 底栏自己补手势条 / 三键导航留白（FoliaBottomNav 内部读 navigationBars），
                // 这里不再重复 padding，否则刘海屏上底栏会被抬高整整一条手势条。
                // 2026-11-14 用户：完全照 Folia —— Folia（含手机版 folia-sonnet）没有底栏。
                // 顶层页只让出系统导航条那一截（铺底色），搜索走首页搜索框，设置走齿轮。
                bottomBar = if (isTopLevel) {
                    {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.fillMaxWidth()
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                                .windowInsetsBottomHeight(WindowInsets.navigationBars),
                        )
                    }
                } else {
                    null
                },
            )
            // 2026-11-14：App 内提示改用 Folia 顶部胶囊（StatusToast），不再用系统 Toast。
            com.guangyue.feature.library.design.StatusToastHost(dark = darkMode)
        }
    }
}

/**
 * 切 Tab：每个 Tab 保留自己的返回栈与滚动位置。
 *
 * `popUpTo(起始页) + saveState` 把当前 Tab 的这一摞存起来，`restoreState` 再把目标 Tab 上次
 * 存的那一摞还原——所以「我的 → 插件管理 → 切到搜索 → 切回我的」仍然停在插件管理页。
 */
private fun switchTab(navController: NavHostController, route: String, current: String) {
    if (route == current) return
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 顶部状态栏留白：二级页顶栏统一用它避开刘海 / 灵动岛。 */
@Composable
internal fun statusBarPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * 内容区与底栏之间的标准堆叠：内容 + 迷你条 + 底栏。
 *
 * 三块是竖排的，内容区拿 `weight(1f)`——**列表不需要自己再留底栏高度**，因为底栏根本不覆盖
 * 内容。键盘弹起时迷你条与底栏整条收起（[collapseOnIme]，量成 0dp 高而不是 if 掉，避免整棵树
 * 重组导致滚动位置抖动）。
 */
@Composable
internal fun ShellColumn(
    content: @Composable () -> Unit,
    miniPlayer: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
) {
    CompositionLocalProvider(LocalImeHidesMiniPlayer provides true) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            miniPlayer?.let {
                Box(Modifier.collapseOnIme()) { it() }
            }
            bottomBar?.let {
                Box(Modifier.collapseOnIme()) { it() }
            }
        }
    }
}