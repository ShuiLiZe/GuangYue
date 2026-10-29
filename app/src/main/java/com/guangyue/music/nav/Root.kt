package com.guangyue.music.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guangyue.core.player.PlaybackController
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.design.GuangYueDesign

/** 底部导航的一项。 */
private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home),
    BottomTab(Routes.SEARCH, "搜索", Icons.Filled.Search),
    BottomTab(Routes.PLUGINS, "插件", Icons.Filled.Extension),
    BottomTab(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

/** 全屏路由：这些页面不显示底部导航（Folia 的播放页 / 歌词页同样是全屏）。 */
private val fullScreenRoutes = setOf(
    Routes.PLAYER,
    Routes.LYRIC_FLOW,
    Routes.LYRIC_STAGE,
    Routes.DOWNLOADS,
)

/**
 * App 外壳：大理石背景 + 内容区 + 底部导航。
 *
 * 底部导航只在首页/搜索/插件三个顶层路由出现；播放、歌词、下载是全屏页面。
 * 这里不做任何数据加载，避免外壳重组牵动整棵树。
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
    val showBar = currentRoute !in fullScreenRoutes

    MarbleBackground(dark = darkMode, modifier = Modifier.fillMaxSize()) {
        GuangYueNavHost(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            playback = playback,
            navController = navController,
            rootBar = if (showBar) {
                {
                    BottomBar(
                        currentRoute = currentRoute,
                        onSelect = { tab ->
                            if (tab.route != currentRoute) {
                                navController.navigate(tab.route) {
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

@Composable
private fun BottomBar(
    currentRoute: String,
    onSelect: (BottomTab) -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gyp.card.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp)
            .padding(top = 6.dp, bottom = 6.dp + navBottom * 0.4f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomTabs.forEach { tab ->
            BottomBarItem(
                tab = tab,
                selected = tab.route == currentRoute,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) GuangYueDesign.Blue else Gyp.inkSoft
    val pill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "tab",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember0(),
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Spacer(
                Modifier
                    .size(width = 44.dp, height = 26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(GuangYueDesign.Blue.copy(alpha = 0.12f * pill)),
            )
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun remember0(): MutableInteractionSource =
    androidx.compose.runtime.remember { MutableInteractionSource() }

@Suppress("unused")
private val barColor: Color = GuangYueDesign.CardWhite

@Suppress("unused")
private val barWidth: Modifier = Modifier.width(0.dp)




