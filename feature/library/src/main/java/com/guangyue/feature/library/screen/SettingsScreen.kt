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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.FoliaGroup
import com.guangyue.feature.library.design.FoliaRow
import com.guangyue.feature.library.design.FoliaSegmented
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.FoliaToggle
import com.guangyue.feature.library.design.FoliaTextField
import com.guangyue.feature.library.design.clickableNoRipple
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.foliaCardSurface
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/**
 * S1：设置（Folia `SettingsModal`：顶部搜索 + 分类 + 分组行）。
 *
 * Folia 桌面是左侧分类列表 + 右侧页面；手机上分类改成搜索框下一条横滑分段（外观 / 播放页 / 播放控制 / 存储与备份 / 关于，
 * 名字取 zh-CN `options.settingsPage*`），页面内仍是 Folia kit 的 Group/Row。搜索：按行名 + 说明过滤，
 * 有输入时不分页，把所有分类里命中的行按分类分组列出（Folia SettingsSearchResults）。
 * 只放广乐真有的开关；Folia 的主题 / 图形 / 交互 / 集成等页等对应功能落地再加。
 */
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lxQuality: String = "320k",
    onPickLxQuality: (String) -> Unit = {},
    /** 歌词样式（id 到名字），当前 id，选中回调；空列表不显示这一行。 */
    lyricStyles: List<Pair<String, String>> = emptyList(),
    lyricStyle: String = "",
    onPickLyricStyle: (String) -> Unit = {},
    /** R2：背景类型（id 到名字）；空列表不显示这一行。 */
    backgrounds: List<Pair<String, String>> = emptyList(),
    background: String = "",
    onPickBackground: (String) -> Unit = {},
    /** 默认播放音质（wire 到名字）。 */
    playQualities: List<Pair<String, String>> = emptyList(),
    playQuality: String = "",
    onPickPlayQuality: (String) -> Unit = {},
    onOpenPlugins: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    versionName: String = "0.0.8",
    /** 0.0.27：资料库界面（首页样式）选项；空列表不显示这一行。 */
    homeStyles: List<HomeStyleOption> = emptyList(),
    homeStyle: String = "",
    onPickHomeStyle: (String) -> Unit = {},
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var page by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(SettingsPage.APPEARANCE) }

    val chevron: @Composable () -> Unit = {
        Icon(GuangYueIcons.ChevronRight, null, tint = Gyp.t3, modifier = Modifier.size(16.dp))
    }
    fun icon(v: androidx.compose.ui.graphics.vector.ImageVector): @Composable () -> Unit = {
        Icon(v, null, tint = Gyp.t2, modifier = Modifier.size(18.dp))
    }

    // 全部设置项（数据驱动：分页与搜索共用一份）。
    val items = buildList {
        add(SettingItem(SettingsPage.APPEARANCE, "外观", "深色模式", "跟随 Folia 的「午夜墨染」深色底", icon(if (darkMode) GuangYueIcons.Moon else GuangYueIcons.Sun),
            trailing = { FoliaToggle(checked = darkMode, onChange = onDarkModeChange) }))
        if (homeStyles.isNotEmpty()) {
            // Folia「设置 → 界面设置 → 资料库界面」（options.librarySuite / librarySuiteDesc）；手机设置没有「界面设置」页，放在外观里。
            add(SettingItem(SettingsPage.APPEARANCE, "资料库", "资料库界面", "首页用哪套界面浏览。切换时保留播放队列。", icon(GuangYueIcons.LayoutGrid),
                below = { HomeStylePicker(homeStyles, homeStyle, onPickHomeStyle) }))
        }
        if (lyricStyles.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYER, "歌词动画", "歌词样式", "播放舞台上的歌词画法；各样式的参数在舞台的样式弹层里调", icon(GuangYueIcons.Sparkles),
                below = {
                    FlowStyleChips(lyricStyles, lyricStyle, onPickLyricStyle)
                }))
        }
        if (backgrounds.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYER, "背景", "背景类型", "歌词舞台的背景：隐现是封面色流体叠像素点阵（需 Android 13+），通用是流体加几何图形", icon(GuangYueIcons.Sparkles),
                below = { FlowStyleChips(backgrounds, background, onPickBackground) }))
        }
        if (playQualities.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYBACK, "音质", "默认播放音质", "新开始播放的歌按这一档取链；音源没有这一档时向下取", icon(GuangYueIcons.AudioLines),
                below = { FlowStyleChips(playQualities, playQuality, onPickPlayQuality) }))
        }
        add(SettingItem(SettingsPage.PLAYBACK, "取链源", "取链源默认音质", "插件取不到播放地址时，按这里选的音质问取链源；播放器切到无损就按无损问", icon(GuangYueIcons.Route),
            below = { FoliaSegmented(value = lxQuality, options = LxQualityOptions, onSelect = onPickLxQuality) }))
        if (onOpenPlugins != null) {
            add(SettingItem(SettingsPage.PLAYBACK, "音源", "音源插件管理", "装音源、选当前音源、看体检结果", icon(GuangYueIcons.Puzzle), trailing = chevron, onClick = onOpenPlugins))
        }
        if (onOpenDownloads != null) {
            add(SettingItem(SettingsPage.STORAGE, "下载", "下载设置", "下载音质、歌词、同时下载数、存储与暂存", icon(GuangYueIcons.Download), trailing = chevron, onClick = onOpenDownloads))
        }
        add(SettingItem(SettingsPage.ABOUT, "关于", "版本", "广乐 $versionName", icon(GuangYueIcons.Info)))
        add(SettingItem(SettingsPage.ABOUT, "关于", "开源许可", "AGPL-3.0 · 界面改写自 Folia", icon(GuangYueIcons.FileText), trailing = chevron, onClick = onOpenLicenses))
    }
    val pages = SettingsPage.entries.filter { p -> items.any { it.page == p } }
    val q = query.trim()
    val shown = if (q.isEmpty()) items.filter { it.page == page }
    else items.filter { it.label.contains(q, true) || (it.hint?.contains(q, true) == true) || it.group.contains(q, true) || it.page.label.contains(q, true) }

    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "设置", onBack = onBack)
            Column(Modifier.padding(horizontal = GySpace.pageH)) {
                FoliaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索设置",
                    leading = { Icon(GuangYueIcons.Search, null, tint = Gyp.t3, modifier = Modifier.size(16.dp)) },
                    trailing = if (query.isNotEmpty()) {
                        { Icon(GuangYueIcons.X, "清空", tint = Gyp.t3, modifier = Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).clickableNoRipple { query = "" }) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (q.isEmpty()) {
                    Spacer(Modifier.height(GySpace.m))
                    // 分类：横滑分段（Folia Tabs 的选中块 = raise + 投影）。
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScrollCompat()
                            .clip(RoundedCornerShape(GyRadius.row))
                            .background(Gyp.tint, RoundedCornerShape(GyRadius.row))
                            .padding(GySpace.hairlinePad),
                    ) {
                        pages.forEach { p ->
                            val on = p == page
                            Box(
                                Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(GyRadius.field))
                                    .background(if (on) Gyp.raise else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickableNoRipple { page = p }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    p.label,
                                    fontSize = GyText.label,
                                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                    color = if (on) Gyp.ink else Gyp.t2,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(GySpace.l))
            }
            LazyColumn(
                contentPadding = PaddingValues(start = GySpace.pageH, end = GySpace.pageH, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(GySpace.group),
            ) {
                if (shown.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "没有找到「$q」相关的设置",
                            fontSize = GyText.row,
                            color = Gyp.t3,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                // 搜索时按「分类 · 组」分组，否则按组。
                val grouped = shown.groupBy { if (q.isEmpty()) it.group else "${it.page.label} · ${it.group}" }
                grouped.forEach { (title, rows) ->
                    item(key = "g-$title") {
                        FoliaGroup(title = title) {
                            rows.forEachIndexed { i, it ->
                                FoliaRow(
                                    label = it.label,
                                    hint = it.hint,
                                    first = i == 0,
                                    leading = it.leading,
                                    trailing = it.trailing,
                                    onClick = it.onClick,
                                )
                                it.below?.let { below ->
                                    Box(Modifier.fillMaxWidth().padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.m)) { below() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 设置分类（Folia settingsNavModel 的手机子集，名字取 zh-CN options.settingsPage*）。 */
enum class SettingsPage(val label: String) {
    APPEARANCE("外观"),
    PLAYER("播放页"),
    PLAYBACK("播放控制"),
    STORAGE("存储与备份"),
    ABOUT("关于"),
}

private class SettingItem(
    val page: SettingsPage,
    val group: String,
    val label: String,
    val hint: String?,
    val leading: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val below: (@Composable () -> Unit)? = null,
)

/** 选项多于分段放得下时用可换行的小胶囊（Folia kit Chips）。 */
@Composable
private fun FlowStyleChips(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (id, name) ->
            val on = id == current
            Text(
                name,
                fontSize = GyText.label,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                color = if (on) Gyp.ink else Gyp.t2,
                modifier = Modifier
                    .clip(RoundedCornerShape(GyRadius.field))
                    .background(if (on) Gyp.raise else Gyp.tint)
                    .clickableNoRipple { if (!on) onPick(id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

/** 取链源的四档音质（洛雪 LX 的 `info.type`）。 */
private val LxQualityOptions = listOf(
    "128k" to "128k",
    "320k" to "320k",
    "flac" to "无损",
    "flac24bit" to "Hi-Res",
)


/** 开源许可：GPL 要求的署名、Folia 致谢、字体许可与第三方组件。 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "开源许可", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GySpace.m),
            ) {
                item(key = "guangyue") {
                    LicenseBlock(
                        title = "广乐 GuangYue",
                        body = "Copyright (C) 2026 GuangYue contributors\n" +
                            "本程序以 GNU Affero General Public License v3.0 发布，源码见项目仓库。",
                    )
                }
                item(key = "folia") {
                    LicenseBlock(
                        title = "Folia · AGPL-3.0",
                        body = "本程序的界面层（布局、配色、字号、间距、图标与交互动效）改写自 Folia，" +
                            "Folia 以 AGPL-3.0 授权，其原始版权归 Folia 贡献者所有。\n" +
                            "原始项目：https://github.com/folia-music/folia\n" +
                            "改写方式：React/CSS 组件 → Jetpack Compose，保留视觉与交互设计。",
                    )
                }
                item(key = "lucide") {
                    LicenseBlock(
                        title = "Lucide 图标 · ISC",
                        body = "界面图标取自 Lucide（https://lucide.dev），以 ISC 许可发布，\n" +
                            "Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 " +
                            "as part of Feather (MIT). All other copyright (c) for Lucide are held by " +
                            "Lucide Contributors 2022.",
                    )
                }
                item(key = "fonts") {
                    LicenseBlock(
                        title = "Noto Serif SC · SIL OFL 1.1",
                        body = "标题所用衬线字体为 Noto Serif SC（子集，仅含界面用到的字形），" +
                            "以 SIL Open Font License 1.1 发布，\n" +
                            "Copyright 2012 Google Inc. All Rights Reserved.\n" +
                            "许可全文见 https://scripts.sil.org/OFL",
                    )
                }
                item(key = "oss") {
                    LicenseBlock(
                        title = "第三方组件",
                        body = "Jetpack Compose / Media3 / Hilt / Room / Coil / OkHttp / " +
                            "QuickJS-Android / kotlinx.serialization / jaudiotagger。" +
                            "各自许可见其发行说明。",
                    )
                }
                item(key = "agpl") {
                    LicenseBlock(
                        title = "AGPL-3.0 摘要",
                        body = "你可以自由使用、修改、再发布本程序，但必须保持同样的 AGPL-3.0 授权、" +
                            "保留版权声明，并在通过网络提供服务时向用户提供完整源码。",
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foliaCardSurface()
            .padding(GySpace.l),
    ) {
        Text(
            text = title,
            fontSize = GyText.row,
            lineHeight = GyText.rowHeight,
            fontWeight = FontWeight.SemiBold,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
        )
        Spacer(Modifier.height(GySpace.xs))
        Text(
            text = body,
            fontSize = GyText.hint,
            lineHeight = GyText.hintHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
        )
    }
}

/** 「关于」页：版本、致谢与仓库地址。 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "关于广乐", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GySpace.pageH),
                verticalArrangement = Arrangement.spacedBy(GySpace.m),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.card))
                        .background(Gyp.tint)
                        .padding(GySpace.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(GyRadius.field))
                            .background(Gyp.raise),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            GuangYueIcons.AudioLines,
                            null,
                            tint = Gyp.ink,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "广乐 GuangYue",
                            fontSize = GyText.emptyTitle,
                            color = Gyp.ink,
                            fontFamily = GuangYueDesign.Serif,
                        )
                        Text(
                            text = "0.0.6 · 原生 Compose",
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                        )
                    }
                }
                Text(
                    text = "一个以插件为音源的安卓音乐播放器。界面逐项对照 Folia 改写为 Jetpack Compose，" +
                        "不含 WebView；感谢 Folia、Lucide 与 Noto 字体的作者。",
                    fontSize = GyText.label,
                    lineHeight = 20.sp,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                )
            }
        }
    }
}

/** 0.0.27：资料库界面的一个选项（Folia librarySuiteOnboarding 的卡片：名字 + 小标签 + 一句说明）。 */
data class HomeStyleOption(val id: String, val title: String, val tag: String, val description: String)

/** 资料库界面选择：每项一整行（≥56dp 触控），选中项带底色与对勾。 */
@Composable
private fun HomeStylePicker(options: List<HomeStyleOption>, current: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val on = o.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .androidxHeightInMin56()
                    .clip(RoundedCornerShape(GyRadius.field))
                    .background(if (on) Gyp.raise else Gyp.tint)
                    .clickableNoRipple { if (!on) onPick(o.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = Gyp.ink)
                        Text(
                            o.tag, fontSize = 12.sp, color = Gyp.t2,
                            modifier = Modifier.padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Gyp.tint)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Text(o.description, fontSize = 12.sp, color = Gyp.t2, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (on) Icon(GuangYueIcons.Check, "已选", tint = Gyp.ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun Modifier.androidxHeightInMin56(): Modifier = this.then(Modifier.heightIn(min = 56.dp))
