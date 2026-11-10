package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.foliaCardSurface
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.home.HomeTile

/**
 * 「我的」页（m00161）。
 *
 * 内容顺序：四个快捷入口（我喜欢 / 全部歌曲 / 本地下载 / 最近播放）→ 我的歌单（横滑）→ 设置组
 * （音源插件管理、下载设置、外观、开源许可、关于）。插件管理与下载管理都是二级页，
 * 从这里进入，不出现在首页。
 */
@Composable
fun ProfileScreen(
    likedCount: Int,
    downloadedCount: Int,
    recentCount: Int,
    playlists: List<HomeTile>,
    isDark: Boolean,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenPlaylist: (HomeTile) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onToggleDark: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    miniPlayer: (@Composable () -> Unit)? = null,
    /** S1：顶栏齿轮 → 设置页（Folia 标题旁的 Settings 键）。 */
    onOpenSettings: (() -> Unit)? = null,
    /** 2026-11-10：「全部歌曲」（Folia 本地页签的 all-songs）：已下载 + 手机本地音乐。 */
    allSongsCount: Int = 0,
    onOpenAllSongs: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(
                title = "我的",
                actions = {
                    if (onOpenSettings != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(GyRadius.fieldShape())
                                .background(Gyp.tint)
                                .clickableNoRipple(onOpenSettings),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(GuangYueIcons.Settings, contentDescription = "设置", tint = Gyp.ink, modifier = Modifier.size(16.dp))
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(GyRadius.fieldShape())
                            .background(Gyp.tint)
                            .clickableNoRipple(onToggleDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isDark) GuangYueIcons.Sun else GuangYueIcons.Moon,
                            contentDescription = if (isDark) "切换到浅色" else "切换到深色",
                            tint = Gyp.ink,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "library") {
                    // 2026-11-10：四个入口排 2×2（一行四个在 320dp 上字会挤掉），
                    // 我喜欢 / 全部歌曲在上（Folia 本地页签的 local-favorites、all-songs 两张特殊卡）。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GySpace.pageH, vertical = GySpace.s),
                        verticalArrangement = Arrangement.spacedBy(GySpace.s),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GySpace.s)) {
                            QuickTile(
                                title = "我喜欢",
                                count = likedCount,
                                icon = GuangYueIcons.Heart,
                                onClick = onOpenLiked,
                                modifier = Modifier.weight(1f),
                            )
                            if (onOpenAllSongs != null) {
                                QuickTile(
                                    title = "全部歌曲",
                                    count = allSongsCount,
                                    icon = GuangYueIcons.ListMusic,
                                    onClick = onOpenAllSongs,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GySpace.s)) {
                            QuickTile(
                                title = "本地下载",
                                count = downloadedCount,
                                icon = GuangYueIcons.Download,
                                onClick = onOpenDownloads,
                                modifier = Modifier.weight(1f),
                            )
                            QuickTile(
                                title = "最近播放",
                                count = recentCount,
                                icon = GuangYueIcons.History,
                                onClick = onOpenRecent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "playlists-title") {
                    FoliaSectionTitle(text = "我的歌单")
                }

                item(key = "playlists") {
                    if (playlists.isEmpty()) {
                        EmptyHint(
                            icon = GuangYueIcons.ListMusic,
                            text = "还没有歌单",
                            action = null,
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = GySpace.pageH),
                            horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                        ) {
                            items(playlists, key = { it.key }) { tile ->
                                PlaylistMini(tile = tile, onClick = { onOpenPlaylist(tile) })
                            }
                        }
                    }
                }

                item(key = "settings-title") {
                    FoliaSectionTitle(text = "设置")
                }

                item(key = "settings") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = GySpace.pageH)
                            .fillMaxWidth()
                            .foliaCardSurface(),
                    ) {
                        com.guangyue.feature.library.design.FoliaRow(
                            label = "音源插件管理",
                            hint = "装音源、选当前音源、看体检结果",
                            first = true,
                            leading = {
                                Icon(
                                    GuangYueIcons.Puzzle,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                Icon(
                                    GuangYueIcons.ChevronRight,
                                    null,
                                    tint = Gyp.t3,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = onOpenPlugins,
                        )
                        com.guangyue.feature.library.design.FoliaRow(
                            label = "下载设置",
                            hint = "下载音质、歌词、同时下载数、存储与暂存",
                            leading = {
                                Icon(
                                    GuangYueIcons.Download,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                Icon(
                                    GuangYueIcons.ChevronRight,
                                    null,
                                    tint = Gyp.t3,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = onOpenDownloadSettings,
                        )
                        com.guangyue.feature.library.design.FoliaRow(
                            label = "外观",
                            hint = if (isDark) "深色" else "浅色",
                            leading = {
                                Icon(
                                    GuangYueIcons.Palette,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                com.guangyue.feature.library.design.FoliaToggle(
                                    checked = isDark,
                                    onChange = { onToggleDark() },
                                )
                            },
                        )
                        com.guangyue.feature.library.design.FoliaRow(
                            label = "开源许可",
                            hint = "AGPL-3.0 · 界面改写自 Folia",
                            leading = {
                                Icon(
                                    GuangYueIcons.FileText,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                Icon(
                                    GuangYueIcons.ChevronRight,
                                    null,
                                    tint = Gyp.t3,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = onOpenLicenses,
                        )
                        com.guangyue.feature.library.design.FoliaRow(
                            label = "关于广乐",
                            // 以前写死 0.0.8；按安装包实际版本显示。
                            hint = androidx.compose.ui.platform.LocalContext.current.let { c ->
                                runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: ""
                            },
                            leading = {
                                Icon(
                                    GuangYueIcons.Info,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                Icon(
                                    GuangYueIcons.ChevronRight,
                                    null,
                                    tint = Gyp.t3,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = onOpenAbout,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            miniPlayer?.invoke()
            bottomBar?.invoke()
        }
    }
}

/** 「我喜欢 / 本地下载 / 最近播放」三张方块入口。 */
@Composable
private fun QuickTile(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(GyRadius.card))
            .foliaCardSurface()
            .clickableNoRipple(onClick)
            .padding(GySpace.l),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = Gyp.ink, modifier = Modifier.size(20.dp))
        Column {
            Text(
                text = title,
                fontSize = GyText.row,
                lineHeight = GyText.rowHeight,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (count > 0) "$count" else "—",
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
            )
        }
    }
}

/** 歌单方块（104dp），沿用首页的海报卡比例。 */
@Composable
private fun PlaylistMini(tile: HomeTile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickableNoRipple(onClick),
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(GyRadius.field))
                .background(Gyp.tint),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.artwork != null) {
                AsyncImage(
                    model = tile.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    GuangYueIcons.ListMusic,
                    null,
                    tint = Gyp.t3,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(GySpace.s))
        Text(
            text = tile.title,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一行灰底提示（歌单为空时）。 */
@Composable
private fun EmptyHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH)
            .clip(RoundedCornerShape(GyRadius.card))
            .background(Gyp.tint)
            .padding(horizontal = GySpace.l, vertical = GySpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GySpace.m),
    ) {
        Icon(icon, null, tint = Gyp.t3, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}
