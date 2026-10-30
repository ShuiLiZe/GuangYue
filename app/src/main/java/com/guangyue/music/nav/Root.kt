package com.guangyue.music.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guangyue.core.player.PlaybackController
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.design.icons.GuangYueIcons

/** 底部导航的三项（移动端信息架构：首页 / 搜索 / 我的）。 */
private val topLevelRoutes = mapOf(
    Routes.HOME to "首页",
    Routes.SEARCH to "搜索",
    Routes.PROFILE to "我的",
)

private val topLevelNav: List<FoliaNavItem> = listOf(
    FoliaNavItem(Routes.HOME, "首页", GuangYueIcons.House),
    FoliaNavItem(Routes.SEARCH, "搜索", GuangYueIcons.Search),
    FoliaNavItem(Routes.PROFILE, "我的", GuangYueIcons.User),
)

/**
 * App 外壳：Folia 纯色底 + 内容区 + 底部导航。
 *
 * 底部导航只在首页 / 搜索 / 我的三个顶层路由出现；播放、歌词、插件管理、下载管理、设置、许可、
 * 关于都是全屏或二级页，进入即隐藏底栏（与 Folia 只在主视图挂底栏的做法一致）。
 * 系统栏 insets 在顶层页的底栏处补一次，二级页由各自的顶栏 / 列表吃掉。
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
    val showBar = currentRoute in topLevelRoutes

    MarbleBackground(dark = darkMode, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            GuangYueNavHost(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                playback = playback,
                navController = navController,
                // 0.0.8：底栏自己补手势条留白（FoliaBottomNav 内部读 navigationBars），
                // 这里不再重复 padding —— 否则刘海屏上底栏会被抬高一整个手势条高度。
                bottomBar = if (showBar) {
                    {
                        FoliaBottomNav(
                            items = topLevelNav,
                            current = currentRoute,
                            onSelect = { route ->
                                if (route != currentRoute) {
                                    navController.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** 顶部状态栏留白：二级页顶栏统一用它避开刘海 / 灵动岛。 */
@Composable
internal fun statusBarPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** 内容区与底栏之间的标准堆叠：内容 + 迷你播放条 + 底栏。 */
@Composable
internal fun ShellColumn(content: @Composable () -> Unit, miniPlayer: (@Composable () -> Unit)?, bottomBar: (@Composable () -> Unit)?) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        miniPlayer?.invoke()
        bottomBar?.invoke()
    }
}
