package com.guangyue.feature.library.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.plugin.PluginFunctions
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.feature.library.design.GuangYueDesign

/**
 * 插件管理 — install from a local file or a URL, enable/disable, check for updates.
 *
 * The app ships zero plugins; every entry here was added by the user. A plugin whose `search`
 * is missing is still listed but flagged, and calls degrade gracefully at runtime.
 */
@Composable
fun PluginManagerScreen(
    plugins: List<PluginMeta>,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onInstallUri: (android.net.Uri) -> Unit,
    onInstallUrl: (String) -> Unit,
    onToggle: (PluginMeta, Boolean) -> Unit,
    onDelete: (PluginMeta) -> Unit,
    onCheckUpdate: (PluginMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Any local file works: MusicFree plugins are plain .js text.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onInstallUri(uri) }

    var urlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("从链接安装插件", fontFamily = FontFamily.Serif) },
            text = {
                BasicTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = GuangYueDesign.Ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = urlText.trim()
                    urlDialog = false
                    if (target.isNotEmpty()) onInstallUrl(target)
                }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { urlDialog = false }) { Text("取消") }
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(GuangYueDesign.MarbleMid)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = GuangYueDesign.Ink,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "插件",
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = GuangYueDesign.Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "广乐只提供插件运行时，音源由你自己从文件或链接安装。",
            fontSize = 12.sp,
            color = GuangYueDesign.InkSoft,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("从文件安装", Icons.Filled.Add) {
                if (!busy) picker.launch(arrayOf("*/*"))
            }
            PillButton("从链接安装", Icons.Filled.Link) {
                if (!busy) urlDialog = true
            }
        }

        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 12.sp, color = GuangYueDesign.BlueDeep)
        }

        Spacer(Modifier.height(16.dp))

        if (plugins.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    "还没有插件。\n点上方按钮安装一个 .js 插件文件或链接。",
                    fontSize = 13.sp,
                    color = GuangYueDesign.InkSoft,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        onToggle = { onToggle(plugin, it) },
                        onDelete = { onDelete(plugin) },
                        onCheckUpdate = { onCheckUpdate(plugin) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginMeta,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(GuangYueDesign.Blue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                plugin.displayName.take(1),
                color = GuangYueDesign.Blue,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plugin.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GuangYueDesign.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "v${plugin.version}",
                    fontSize = 11.sp,
                    color = GuangYueDesign.InkSoft,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append("platform: ${plugin.platform}")
                    if (plugin.supportedSearchType.isNotEmpty()) {
                        append(" · ")
                        append(plugin.supportedSearchType.joinToString("/"))
                    }
                    val impl = PluginFunctions.ALL.count { plugin.functions.contains(it) }
                    append(" · $impl 个函数")
                },
                fontSize = 11.sp,
                color = GuangYueDesign.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val err = plugin.error
            if (err != null) {
                Text(err, fontSize = 11.sp, color = Color(0xFFC0392B), maxLines = 2)
            }
        }
        IconButton(onClick = onCheckUpdate) {
            Icon(Icons.Filled.Refresh, "检查更新", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, "删除", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
        }
        Switch(
            checked = plugin.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GuangYueDesign.Blue,
            ),
        )
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(GuangYueDesign.Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
