package com.guangyue.feature.library.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.plugin.lx.LxSource
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaGroup
import com.guangyue.feature.library.design.FoliaMenu
import com.guangyue.feature.library.design.FoliaMenuItem
import com.guangyue.feature.library.design.FoliaStatus
import com.guangyue.feature.library.design.FoliaTextField
import com.guangyue.feature.library.design.FoliaTone
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.FoliaUnfold
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/**
 * 取链源管理（洛雪 LX Music 自定义源）。
 *
 * 为什么单独一页而不是塞进「音源」列表：洛雪源跟 MusicFree 插件是两种东西 ——
 * 它不能搜索、不能看详情，唯一作用是「插件取不到播放地址时，再问它要一次链接」。
 * 混在一张列表里会让人误以为它们是同一层可选音源，所以在这里单独分组。
 */
@Composable
fun LxSourceScreen(
    sources: List<LxSource>,
    busy: Boolean,
    message: String?,
    errorDetails: String?,
    onBack: () -> Unit,
    onInstallCode: (String, String?) -> Unit,
    onInstallUrl: (String) -> Unit,
    onInstallUri: (Uri) -> Unit,
    onToggle: (LxSource, Boolean) -> Unit,
    onDelete: (LxSource) -> Unit,
    onRecheck: (LxSource) -> Unit,
    onRecheckAll: () -> Unit,
    onMove: (LxSource, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 设置里的默认音质（128k / 320k / flac / flac24bit）。 */
    defaultQuality: String = "320k",
    onPickQuality: (String) -> Unit = {},
) {
    var detailsOpen by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onInstallUri(uri) }

    Column(
        modifier
            .fillMaxSize()
            .marbleBackground(),
    ) {
        FoliaTopBar(title = "取链源", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GySpace.pageH),
            verticalArrangement = Arrangement.spacedBy(GySpace.group),
        ) {
            Text(
                text = "取链源只提供播放地址，不能搜索。插件自己取不到链时，按这里的顺序挨个问，" +
                    "谁先给出链接就用谁；只对 QQ / 网易 / 酷狗 / 酷我 / 咪咕 的歌有用。",
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
            )

            FoliaGroup(
                title = "已装取链源",
                foot = if (sources.isEmpty()) null
                else "${sources.size} 个 · ${sources.count { it.enabled && it.error == null }} 个在用",
            ) {
                if (sources.isEmpty()) {
                    Column(Modifier.padding(GySpace.pageH)) {
                        Text(
                            text = "还没有取链源。洛雪自定义源是一个 .js 文件，下面两种方式都能导入。",
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                        )
                    }
                } else {
                    sources.forEachIndexed { index, source ->
                        LxSourceRow(
                            source = source,
                            first = index == 0,
                            canMoveUp = index > 0,
                            canMoveDown = index < sources.lastIndex,
                            onToggle = { onToggle(source, it) },
                            onDelete = { onDelete(source) },
                            onRecheck = { onRecheck(source) },
                            onMoveUp = { onMove(source, index - 1) },
                            onMoveDown = { onMove(source, index + 1) },
                        )
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    color = Gyp.accent,
                    trackColor = Gyp.track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(GyRadius.capsuleShape()),
                )
            }

            // ---- 默认音质：洛雪协议只有这四档
            FoliaGroup(title = "默认音质", foot = "取链源用这一档；播放器切到无损时按无损问") {
                LxQualityRow(defaultQuality, onPickQuality)
            }

            // ---- 操作区：与「音源」页同一套 Button 规格（h-8 / rounded-lg / 13px / 图标 14dp）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(GySpace.s),
                verticalArrangement = Arrangement.spacedBy(GySpace.s),
            ) {
                FoliaButton(
                    text = "从文件导入",
                    onClick = { if (!busy) picker.launch(arrayOf("*/*")) },
                    variant = FoliaButtonVariant.Primary,
                    icon = GuangYueIcons.Plus,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "从链接导入",
                    onClick = { if (!busy) urlDialog = true },
                    icon = GuangYueIcons.Link2,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "全部体检",
                    onClick = { if (!busy) onRecheckAll() },
                    icon = GuangYueIcons.ShieldCheck,
                    enabled = !busy,
                )
            }

            if (message != null) {
                Column(verticalArrangement = Arrangement.spacedBy(GySpace.xs)) {
                    FoliaStatus(text = message, tone = FoliaTone.Neutral, maxLines = 3)
                    if (!errorDetails.isNullOrBlank()) {
                        FoliaButton(
                            text = if (detailsOpen) "收起详情" else "看详情",
                            onClick = { detailsOpen = !detailsOpen },
                            variant = FoliaButtonVariant.Quiet,
                        )
                        FoliaUnfold(expanded = detailsOpen) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(GyRadius.fieldShape())
                                    .background(Gyp.tint)
                                    .padding(GySpace.s),
                            ) {
                                Text(
                                    text = errorDetails,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = Gyp.t2,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(GySpace.xl))
        }
    }

    if (urlDialog) {
        LxUrlDialog(
            text = urlText,
            onTextChange = { urlText = it },
            onDismiss = { urlDialog = false },
            onConfirm = {
                val url = urlText.trim()
                urlDialog = false
                urlText = ""
                if (url.isNotEmpty()) onInstallUrl(url)
            },
        )
    }
}

/** 一条取链源：图标 / 名称 / 版本 / 状态标签 / 一行原因，行尾「⋯」放体检与卸载。 */
@Composable
private fun LxSourceRow(
    source: LxSource,
    first: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRecheck: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var detailOpen by remember(source.id) { mutableStateOf(false) }
    var menuOpen by remember(source.id) { mutableStateOf(false) }
    val ok = source.error == null

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GySpace.rowMin)
            .lxRowHairline(first)
            .selectable(
                selected = source.enabled,
                role = Role.Switch,
                enabled = ok,
                onClick = { onToggle(!source.enabled) },
            )
            .padding(start = GySpace.pageH, end = GySpace.xs, top = GySpace.m, bottom = GySpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(GyRadius.markShape())
                    .background(Gyp.tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.displayName.take(1).ifBlank { "源" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gyp.accentText,
                )
            }
            Spacer(Modifier.size(GySpace.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.displayName,
                        fontSize = GyText.row,
                        lineHeight = GyText.rowHeight,
                        color = Gyp.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(GySpace.xs))
                    if (source.version.isNotBlank()) {
                        Text(text = "v${source.version}", fontSize = 11.sp, color = Gyp.t3)
                    }
                }
                Spacer(Modifier.size(GySpace.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoliaStatus(
                        text = when {
                            !ok -> "不可用"
                            source.enabled -> "在用"
                            else -> "未启用"
                        },
                        tone = when {
                            !ok -> FoliaTone.Error
                            source.enabled -> FoliaTone.Ok
                            else -> FoliaTone.Neutral
                        },
                    )
                    Spacer(Modifier.size(GySpace.xs))
                    Text(
                        text = source.reasonLine(),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Gyp.t2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box {
                Icon(
                    imageVector = GuangYueIcons.MoreHorizontal,
                    contentDescription = "更多",
                    tint = Gyp.t2,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(GyRadius.fieldShape())
                        .pointerInput(source.id) { detectTapGestures { menuOpen = true } }
                        .padding(7.dp),
                )
                FoliaMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    items = buildList {
                        add(FoliaMenuItem("重新体检", onRecheck, GuangYueIcons.ShieldCheck))
                        if (source.error != null || source.note != null) {
                            add(
                                FoliaMenuItem(
                                    label = if (detailOpen) "收起详情" else "看详情",
                                    onClick = { detailOpen = !detailOpen },
                                ),
                            )
                        }
                        if (canMoveUp) add(FoliaMenuItem("上移一位", onMoveUp, GuangYueIcons.ChevronUp))
                        if (canMoveDown) add(FoliaMenuItem("下移一位", onMoveDown, GuangYueIcons.ChevronDown))
                        add(
                            FoliaMenuItem(
                                "卸载",
                                onDelete,
                                GuangYueIcons.Trash2,
                                destructive = true,
                                divider = true,
                            ),
                        )
                    },
                )
            }
        }

        FoliaUnfold(expanded = detailOpen) {
            Column(
                Modifier.padding(start = 40.dp, top = GySpace.xs, end = GySpace.m),
                verticalArrangement = Arrangement.spacedBy(GySpace.xxs),
            ) {
                source.note?.let {
                    Text(text = it, fontSize = 11.sp, lineHeight = 16.sp, color = Gyp.t2)
                }
                source.error?.let {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Gyp.t2,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                source.srcUrl?.let {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Gyp.t3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 列表上只出现一行中文原因，原始异常栈收进「看详情」。 */
private fun LxSource.reasonLine(): String = when {
    error != null -> error.orEmpty()
    platforms.isEmpty() -> "没有上报可用的音乐平台"
    else -> note ?: "按顺序问它取链"
}

@Composable
private fun LxQualityRow(current: String, onPick: (String) -> Unit) {
    val options = listOf(
        "128k" to "128k",
        "320k" to "320k",
        "flac" to "无损",
        "flac24bit" to "Hi-Res",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(GySpace.pageH),
        horizontalArrangement = Arrangement.spacedBy(GySpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (wire, label) ->
            FoliaButton(
                text = label,
                onClick = { onPick(wire) },
                variant = if (wire == current) FoliaButtonVariant.Primary else FoliaButtonVariant.Fill,
            )
        }
    }
}

/** 导入链接对话框：与「音源」页的 UrlDialog 同一套卡面规格。 */
@Composable
private fun LxUrlDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 280.dp)
                .clip(GyRadius.cardShape())
                .background(Gyp.menu)
                .padding(GySpace.pageH),
            verticalArrangement = Arrangement.spacedBy(GySpace.m),
        ) {
            Text(
                text = "导入取链源",
                fontSize = GyText.title,
                lineHeight = GyText.titleHeight,
                fontWeight = FontWeight.SemiBold,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
            )
            FoliaTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "https://… 洛雪自定义源 .js",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(GySpace.s),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.weight(1f))
                FoliaButton(text = "取消", onClick = onDismiss, variant = FoliaButtonVariant.Quiet)
                FoliaButton(text = "导入", onClick = onConfirm, variant = FoliaButtonVariant.Primary)
            }
        }
    }
}

/** 行顶发丝线：首行不画，起 16dp（对应 `kit.css` 的 `.st-row::before`）。 */
@Composable
private fun Modifier.lxRowHairline(first: Boolean): Modifier {
    if (first) return this
    val hairline = Gyp.hairline
    val inset = GySpace.pageH.value
    return this.drawBehind {
        drawRect(
            color = hairline,
            topLeft = androidx.compose.ui.geometry.Offset(inset * density, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - inset * density, 1f),
        )
    }
}