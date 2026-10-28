package com.guangyue.feature.library.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.plugin.PluginFunctions
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.Subscription
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.viewmodel.PluginViewModelDefaults

/**
 * 插件管理。
 *
 * 顶部常驻「一键安装 / 更新官方音源」与「全选 / 全不选」，下面列出订阅源与已装插件。
 * 广乐本身不带任何音源，音源全部来自订阅 JSON 或用户自己的 .js 文件。
 *
 * 视觉已统一到 Folia 手机版设计语言：大理石/液态渐变底 + 悬浮胶囊 + 圆角卡片 + 单一谷歌蓝
 * 强调色；颜色全部走 [Gyp] 语义令牌，深色模式不再出现白底白字。
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
    /** 订阅源列表。 */
    subscriptions: List<Subscription> = emptyList(),
    /** 批量安装进度（0..1，isIdle 时隐藏）。 */
    batchStage: String = "",
    batchFraction: Float = 0f,
    /** 一键安装 / 更新官方音源。 */
    onInstallOfficial: () -> Unit = {},
    /** 检查全部订阅源 + 已装插件的更新。 */
    onCheckAllUpdates: () -> Unit = {},
    /** 全选 / 全不选。 */
    onSetAllEnabled: (Boolean) -> Unit = {},
    onForgetSubscription: (String) -> Unit = {},
    /** 最近一次失败的插件详情（原始报错 + 元信息 JSON），供「展开详情」。 */
    errorDetails: String? = null,
) {
    // Any local file works: MusicFree plugins are plain .js text.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onInstallUri(uri) }

    var urlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var detailsOpen by remember { mutableStateOf(false) }

    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            shape = GyRadius.cardShape(),
            containerColor = Gyp.card,
            titleContentColor = Gyp.ink,
            textContentColor = Gyp.inkSoft,
            title = { Text("从链接安装插件 / 订阅", fontFamily = FontFamily.Serif, fontSize = 18.sp) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(GyRadius.fieldShape())
                        .background(Gyp.tint)
                        .border(1.dp, Gyp.hairline, GyRadius.fieldShape())
                        .padding(12.dp),
                ) {
                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = Gyp.ink),
                        cursorBrush = SolidColor(Gyp.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (urlText.isEmpty()) {
                        Text(
                            "https://… 或订阅 JSON 地址",
                            fontSize = 14.sp,
                            color = Gyp.inkSoft.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = urlText.trim()
                    urlDialog = false
                    if (target.isNotEmpty()) onInstallUrl(target)
                }) { Text("安装", color = Gyp.accentText) }
            },
            dismissButton = {
                TextButton(onClick = { urlDialog = false }) { Text("取消", color = Gyp.inkSoft) }
            },
        )
    }

    val allOn = plugins.isNotEmpty() && plugins.all { it.enabled }

    Column(
        modifier
            .fillMaxSize()
            .marbleBackground()
            .padding(horizontal = GySpace.pageH),
    ) {
        Spacer(Modifier.height(GySpace.topSafe))

        // ---- 顶部：返回 + 标题 + 全选
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Gyp.cardTranslucent)
                    .border(1.dp, Gyp.hairline, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Gyp.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(GySpace.m))
            Text(
                "插件",
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Gyp.ink,
            )
            Spacer(Modifier.weight(1f))
            Text("全选", fontSize = 12.sp, color = Gyp.inkSoft)
            Spacer(Modifier.width(GySpace.xs))
            Switch(
                checked = allOn,
                onCheckedChange = { onSetAllEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Gyp.accent,
                    uncheckedThumbColor = Gyp.inkSoft,
                    uncheckedTrackColor = Gyp.tint,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        Spacer(Modifier.height(GySpace.s))
        Text(
            "广乐只提供插件运行时，音源由你自己从订阅源或文件安装。",
            fontSize = 12.sp,
            color = Gyp.inkSoft,
        )

        Spacer(Modifier.height(GySpace.l))

        // ---- 常驻的一键安装 / 更新条
        OfficialSourceBar(
            busy = busy,
            onInstallOfficial = onInstallOfficial,
            onCheckAllUpdates = onCheckAllUpdates,
        )

        if (busy && batchStage.isNotEmpty()) {
            Spacer(Modifier.height(GySpace.m))
            LinearProgressIndicator(
                progress = { batchFraction.coerceIn(0f, 1f) },
                color = Gyp.accent,
                trackColor = Gyp.tint,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(GySpace.xs))
            Text(batchStage, fontSize = 11.sp, color = Gyp.inkSoft)
        }

        Spacer(Modifier.height(GySpace.l))
        Row(horizontalArrangement = Arrangement.spacedBy(GySpace.m)) {
            PillButton("从文件安装", Icons.Filled.Add) {
                if (!busy) picker.launch(arrayOf("*/*"))
            }
            PillButton("从链接安装", Icons.Filled.Link, filled = false) {
                if (!busy) urlDialog = true
            }
        }

        if (message != null) {
            Spacer(Modifier.height(GySpace.m))
            MessageCard(
                message = message,
                details = errorDetails,
                expanded = detailsOpen,
                onToggleDetails = { detailsOpen = !detailsOpen },
            )
        }

        Spacer(Modifier.height(GySpace.l))

        if (plugins.isEmpty() && subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    "还没有插件。\n点上方「一键安装官方音源」，或安装一个 .js 插件文件 / 订阅链接。",
                    fontSize = 13.sp,
                    color = Gyp.inkSoft,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (subscriptions.isNotEmpty()) {
                    item { SectionTitle("订阅源") }
                    items(subscriptions, key = { "sub:${it.url}" }) { sub ->
                        SubscriptionRow(sub, onForget = { onForgetSubscription(sub.url) })
                    }
                    item { SectionTitle("已安装插件") }
                }
                items(plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        onToggle = { onToggle(plugin, it) },
                        onDelete = { onDelete(plugin) },
                        onCheckUpdate = { onCheckUpdate(plugin) },
                    )
                }
                item { Spacer(Modifier.height(GySpace.xl)) }
            }
        }
    }
}

/**
 * 简短中文 + 可展开详情的提示卡。
 *
 * 批量安装失败时这里只给一句话（例如「12/57 安装成功，45 个失败」），
 * 想看到底哪个插件哪一步炸的，点「详情」展开原始报错与元信息。
 */
@Composable
private fun MessageCard(
    message: String,
    details: String?,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GyRadius.rowShape())
            .background(Gyp.tint)
            .border(1.dp, Gyp.hairline, GyRadius.rowShape())
            .padding(horizontal = GySpace.m, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                message,
                fontSize = 12.sp,
                color = Gyp.accentText,
                modifier = Modifier.weight(1f),
            )
            if (!details.isNullOrBlank()) {
                Text(
                    if (expanded) "收起" else "详情",
                    fontSize = 11.sp,
                    color = Gyp.inkSoft,
                    modifier = Modifier
                        .clip(GyRadius.capsuleShape())
                        .clickable { onToggleDetails() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "详情",
                    tint = Gyp.inkSoft,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded && !details.isNullOrBlank()) {
            Column {
                Spacer(Modifier.height(GySpace.s))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(GyRadius.fieldShape())
                        .background(Gyp.card.copy(alpha = 0.6f))
                        .padding(10.dp),
                ) {
                    Text(
                        details.orEmpty(),
                        fontSize = 10.sp,
                        color = Gyp.inkSoft,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .height(180.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialSourceBar(
    busy: Boolean,
    onInstallOfficial: () -> Unit,
    onCheckAllUpdates: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GyRadius.cardShape())
            .background(Gyp.cardTranslucent)
            .border(1.dp, Gyp.hairline, GyRadius.cardShape())
            .padding(GySpace.m),
    ) {
        Text(
            "官方音源",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gyp.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            PluginViewModelDefaults.OFFICIAL_SOURCE,
            fontSize = 10.sp,
            color = Gyp.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(GyRadius.capsuleShape())
                    .background(if (busy) Gyp.accent.copy(alpha = 0.45f) else Gyp.accent)
                    .clickable(enabled = !busy) { onInstallOfficial() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (busy) "安装中…" else "一键安装 / 更新",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                Modifier
                    .clip(GyRadius.capsuleShape())
                    .border(1.dp, Gyp.hairline, GyRadius.capsuleShape())
                    .background(Gyp.card)
                    .clickable(enabled = !busy) { onCheckAllUpdates() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Gyp.accentText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("检查更新", color = Gyp.accentText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = Gyp.inkSoft,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = GySpace.xs, bottom = 2.dp),
    )
}

@Composable
private fun SubscriptionRow(sub: Subscription, onForget: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(GyRadius.rowShape())
            .background(Gyp.cardTranslucent)
            .border(1.dp, Gyp.hairline, GyRadius.rowShape())
            .padding(start = GySpace.m, top = 10.dp, bottom = 10.dp, end = GySpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, null, tint = Gyp.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(GySpace.s))
        Column(Modifier.weight(1f)) {
            Text(
                sub.name.ifBlank { sub.url.substringAfter("://").substringBefore("/") },
                fontSize = 12.sp,
                color = Gyp.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${sub.count} 个插件 · ${sub.url}",
                fontSize = 10.sp,
                color = Gyp.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Filled.Delete, "移除订阅", tint = Gyp.inkSoft, modifier = Modifier.size(16.dp))
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
    var showError by remember(plugin.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(GyRadius.cardShape())
            .background(Gyp.cardTranslucent)
            .border(1.dp, Gyp.hairline, GyRadius.cardShape())
            .padding(GySpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Gyp.tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    plugin.displayName.take(1),
                    color = Gyp.accentText,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(GySpace.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plugin.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gyp.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("v${plugin.version}", fontSize = 11.sp, color = Gyp.inkSoft)
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
                    color = Gyp.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCheckUpdate) {
                Icon(Icons.Filled.Refresh, "检查更新", tint = Gyp.inkSoft, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = Gyp.inkSoft, modifier = Modifier.size(18.dp))
            }
            Switch(
                checked = plugin.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Gyp.accent,
                    uncheckedThumbColor = Gyp.inkSoft,
                    uncheckedTrackColor = Gyp.tint,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        // 安装/探测失败时的简短中文提示，多行内容点一下展开详情。
        val err = plugin.error
        if (err != null) {
            Spacer(Modifier.height(GySpace.s))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(GyRadius.fieldShape())
                    .clickable(enabled = err.contains('\n')) { showError = !showError }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    err.lineSequence().first().take(90),
                    fontSize = 11.sp,
                    color = Gyp.danger,
                    maxLines = if (showError) Int.MAX_VALUE else 2,
                    modifier = Modifier.weight(1f),
                )
                if (err.contains('\n')) {
                    Icon(
                        if (showError) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showError) "收起" else "详情",
                        tint = Gyp.inkSoft,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(GyRadius.capsuleShape())
            .background(if (filled) Gyp.accent else Gyp.cardTranslucent)
            .border(
                1.dp,
                if (filled) Color.Transparent else Gyp.hairline,
                GyRadius.capsuleShape(),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) Color.White else Gyp.accentText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (filled) Color.White else Gyp.accentText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
