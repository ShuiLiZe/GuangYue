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
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.foliaCardSurface
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/**
 * 设置（「我的」里的二级页）。
 *
 * 布局按 Folia 设置窗口的 Group/Row 规格：组卡片圆角 12dp、1dp `--st-line` 描边、
 * 组标题 13/20 semibold `--st-t2`、行最小高 56dp、行首发丝线自 16dp 起。
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
) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "设置", onBack = onBack)

            LazyColumn(
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item(key = "appearance") {
                    FoliaGroup(title = "外观") {
                        FoliaRow(
                            label = "深色模式",
                            hint = "跟随 Folia 的「午夜墨染」深色底",
                            first = true,
                            leading = {
                                Icon(
                                    if (darkMode) GuangYueIcons.Moon else GuangYueIcons.Sun,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                FoliaToggle(checked = darkMode, onChange = onDarkModeChange)
                            },
                        )
                    }
                }

                item(key = "about") {
                    FoliaGroup(title = "关于") {
                        FoliaRow(
                            label = "版本",
                            hint = "广乐 0.0.8",
                            first = true,
                            leading = {
                                Icon(
                                    GuangYueIcons.Info,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        FoliaRow(
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
                    }
                }
                // 取链源的默认音质：插件自己取不到链时按这一档问洛雪源。
                item(key = "lx-quality") {
                    FoliaGroup(
                        title = "取链源",
                        foot = "插件取不到播放地址时，按这里选的音质问取链源；播放器切到无损就按无损问。",
                    ) {
                        FoliaRow(
                            label = "默认音质",
                            first = true,
                            leading = {
                                Icon(
                                    GuangYueIcons.AudioLines,
                                    null,
                                    tint = Gyp.t2,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailing = {
                                FoliaSegmented(
                                    value = lxQuality,
                                    options = LxQualityOptions,
                                    onSelect = onPickLxQuality,
                                    fill = false,
                                )
                            },
                        )
                    }
            }
            }
        }
    }
}

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
