package com.guangyue.feature.library.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.marbleBackground

/**
 * 设置（Folia 这一层只有「外观」「开源许可」两项，其余都放在插件管理里）。
 */
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().marbleBackground()) {
            TopBar(title = "设置", onBack = onBack)

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    top = 8.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GySpace.s),
            ) {
                item(key = "appearance") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            tint = Gyp.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("深色模式", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Gyp.ink)
                            Text("夜间用深蓝底，省电也更护眼", fontSize = 12.sp, color = Gyp.inkSoft)
                        }
                        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                    }
                }

                item(key = "licenses") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .then(Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = Gyp.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("开源许可", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Gyp.ink)
                            Text(
                                "广乐以 AGPL-3.0 发布，界面改写自 Folia",
                                fontSize = 12.sp,
                                color = Gyp.inkSoft,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = onOpenLicenses) {
                            Text("查看", color = GuangYueDesign.Blue, fontSize = 13.sp)
                        }
                    }
                }

                item(key = "version") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "广乐 0.0.5 · 原生界面",
                        fontSize = 12.sp,
                        color = Gyp.inkSoft,
                    )
                }
            }
        }
    }
}

/** 开源许可：列出 GPL 要求的署名与许可全文链接。 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().marbleBackground()) {
            TopBar(title = "开源许可", onBack = onBack)
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    top = 8.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        title = "Folia",
                        body = "本程序的界面层（布局、配色、字号、间距、图标与交互动效）改写自 Folia，" +
                            "Folia 以 AGPL-3.0 授权。\n" +
                            "原始项目：https://github.com/folia-music/folia\n" +
                            "改写方式：React/CSS 组件 → Jetpack Compose，保留视觉与交互设计。",
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
    Surface(
        color = Gyp.card,
        shape = RoundedCornerShape(GyRadius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink)
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 12.5.sp, lineHeight = 19.sp, color = Gyp.inkSoft)
        }
    }
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Gyp.ink,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink)
    }
}





