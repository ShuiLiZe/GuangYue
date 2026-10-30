package com.guangyue.feature.library.screen

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.plugin.PluginFailureText
import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.Subscription
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaGroup
import com.guangyue.feature.library.design.FoliaMenu
import com.guangyue.feature.library.design.FoliaMenuItem
import com.guangyue.feature.library.design.FoliaRow
import com.guangyue.feature.library.design.FoliaStatus
import com.guangyue.feature.library.design.FoliaTextField
import com.guangyue.feature.library.design.FoliaTone
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.FoliaUnfold
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.viewmodel.PluginViewModelDefaults

/**
 * 音源管理。
 *
 * 0.0.8 起音源是**单选**：「当前音源」只有一个，装一堆插件不会并发跑（用户 10-30 明确反对
 * 「能同时启用好几个插件」）。列表里每个插件只是「可选」，选中哪个才用哪个。
 *
 * 每行只放得下这些：图标、名称、版本、一句状态（可用 / 只能搜索 / 取不到链 / 搜索失败）和
 * 一行中文原因；原始异常栈收进「详情」，更新 / 排序 / 卸载收进行尾「⋯」。
 *
 * 版面全部用 Folia 设置页的组件（[FoliaGroup] / [FoliaRow] / [FoliaStatus] / [FoliaMenu]），
 * 不另造样式；顶栏用 [FoliaTopBar]，已含状态栏 + 刘海安全区。
 */
@Composable
fun PluginManagerScreen(
    plugins: List<PluginMeta>,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onInstallUri: (android.net.Uri) -> Unit,
    onInstallUrl: (String) -> Unit,
    /** 选择当前音源（单选）。 */
    onSelectSource: (PluginMeta) -> Unit,
    onDelete: (PluginMeta) -> Unit,
    onCheckUpdate: (PluginMeta) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前音源 id（空串 = 还没选）。 */
    currentSourceId: String = "",
    subscriptions: List<Subscription> = emptyList(),
    batchStage: String = "",
    batchFraction: Float = 0f,
    onInstallOfficial: () -> Unit = {},
    onCheckAllUpdates: () -> Unit = {},
    onForgetSubscription: (String) -> Unit = {},
    errorDetails: String? = null,
    onRecheckAll: () -> Unit = {},
    /** 拖动排序：把插件移到第 to 位（0 基）。 */
    onMove: (PluginMeta, Int) -> Unit = { _, _ -> },
    /** 只看可用音源（隐藏体检没通过的）。 */
    hideUnavailable: Boolean = false,
    onToggleHideUnavailable: () -> Unit = {},
    /** 打开「取链源」（洛雪自定义源）管理页。 */
    onOpenLxSources: (() -> Unit)? = null,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onInstallUri(uri) }

    var urlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var detailsOpen by remember { mutableStateOf(false) }

    val shown = if (hideUnavailable) {
        plugins.filter { it.error == null && (it.health == PluginHealth.OK || it.health == PluginHealth.UNKNOWN) }
    } else {
        plugins
    }
    val current = plugins.firstOrNull { it.id == currentSourceId }
        ?: plugins.firstOrNull { it.enabled && it.error == null }

    Column(
        modifier
            .fillMaxSize()
            .marbleBackground(),
    ) {
        // Folia PageHeader：标题 20/32 semibold + 32dp 圆角返回键；不带右侧「全选」。
        FoliaTopBar(title = "音源", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GySpace.pageH),
            verticalArrangement = Arrangement.spacedBy(GySpace.group),
        ) {
            // ---- 当前音源（单选）
            Column {
                Text(
                    text = current?.let { "正在用「${it.displayName}」搜索和取播放地址。装多个音源不会同时运行，换一个才切过去。" }
                        ?: "还没有选音源。选一个之后，搜索和首页都用它。",
                    fontSize = GyText.hint,
                    lineHeight = GyText.hintHeight,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                )
            }

            FoliaGroup(
                title = "已安装音源",
                foot = if (plugins.isEmpty()) null else "${plugins.size} 个可选 · 只有「当前音源」在跑",
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FoliaButton(
                            text = if (hideUnavailable) "已隐藏不可用" else "隐藏不可用",
                            onClick = onToggleHideUnavailable,
                            variant = if (hideUnavailable) FoliaButtonVariant.Primary else FoliaButtonVariant.Quiet,
                        )
                    }
                },
            ) {
                if (shown.isEmpty()) {
                    Column(Modifier.padding(GySpace.pageH)) {
                        Text(
                            text = if (plugins.isEmpty()) "还没有音源。用下面的按钮从官方订阅或本地 .js 文件装一个。"
                            else "所有音源都被筛掉了，关掉「隐藏不可用」看看。",
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                        )
                    }
                } else {
                    shown.forEachIndexed { index, plugin ->
                        SourceRow(
                            plugin = plugin,
                            first = index == 0,
                            selected = plugin.id == current?.id,
                            selectable = plugin.error == null,
                            canMoveUp = index > 0,
                            canMoveDown = index < shown.lastIndex,
                            onSelect = { onSelectSource(plugin) },
                            onCheckUpdate = { onCheckUpdate(plugin) },
                            onDelete = { onDelete(plugin) },
                            onMoveUp = { onMove(plugin, plugins.indexOfFirst { it.id == plugin.id } - 1) },
                            onMoveDown = { onMove(plugin, plugins.indexOfFirst { it.id == plugin.id } + 1) },
                        )
                    }
                }
            }

            // ---- 操作区：全部对齐 Folia Button 规格（h-8 / rounded-lg / 13px），不会出现空白胶囊
            if (busy && batchStage.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(GySpace.xs)) {
                    LinearProgressIndicator(
                        progress = { batchFraction.coerceIn(0f, 1f) },
                        color = Gyp.accent,
                        trackColor = Gyp.track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(batchStage, fontSize = GyText.hint, color = Gyp.t2)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(GySpace.s),
                verticalArrangement = Arrangement.spacedBy(GySpace.s),
            ) {
                FoliaButton(
                    text = if (busy) "安装中…" else "一键安装官方音源",
                    onClick = { if (!busy) onInstallOfficial() },
                    variant = FoliaButtonVariant.Primary,
                    icon = GuangYueIcons.Download,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "检查更新",
                    onClick = { if (!busy) onCheckAllUpdates() },
                    icon = GuangYueIcons.RefreshCw,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "体检一遍",
                    onClick = { if (!busy) onRecheckAll() },
                    icon = GuangYueIcons.ShieldCheck,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "从文件安装",
                    onClick = { if (!busy) picker.launch(arrayOf("*/*")) },
                    icon = GuangYueIcons.Plus,
                    enabled = !busy,
                )
                FoliaButton(
                    text = "从链接安装",
                    onClick = { if (!busy) urlDialog = true },
                    icon = GuangYueIcons.Link2,
                    enabled = !busy,
                )
                if (onOpenLxSources != null) {
                    FoliaButton(
                        text = "取链源",
                        onClick = onOpenLxSources,
                        icon = GuangYueIcons.Link2,
                    )
                }
            }

            if (message != null) {
                MessageBlock(
                    message = message,
                    details = errorDetails,
                    expanded = detailsOpen,
                    onToggle = { detailsOpen = !detailsOpen },
                )
            }

            if (subscriptions.isNotEmpty()) {
                FoliaGroup(title = "订阅源") {
                    subscriptions.forEachIndexed { index, sub ->
                        FoliaRow(
                            label = sub.name.ifBlank { sub.url.substringAfter("://").substringBefore("/") },
                            hint = "${sub.count} 个音源 · ${sub.url}",
                            first = index == 0,
                            trailing = {
                                Icon(
                                    GuangYueIcons.Trash2,
                                    contentDescription = "移除订阅",
                                    tint = Gyp.t2,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .pointerInput(sub.url) { detectTapGestures { onForgetSubscription(sub.url) } },
                                )
                            },
                        )
                    }
                }
            }

            // 0.0.9 a：edge-to-edge 下最后一项会被系统手势条 / 导航栏切掉。
            // 二级页没有自己的窗口 inset，这里显式补齐「手势条高度 + 尾部呼吸位」。
            Spacer(Modifier.height(GySpace.xl))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (urlDialog) {
        UrlDialog(
            text = urlText,
            onTextChange = { urlText = it },
            onConfirm = {
                val target = urlText.trim()
                urlDialog = false
                if (target.isNotEmpty()) onInstallUrl(target)
            },
            onDismiss = { urlDialog = false },
        )
    }
}

/**
 * 一行音源：单选圈 + 图标 + 名称/版本 + 状态 + 一行原因 + 「⋯」菜单。
 *
 * 尺寸照 Folia `Row.tsx`：最小高 56dp、左右各 16dp、上下 10dp，行间发丝线从 16dp 起（子行 40dp）。
 */
@Composable
private fun SourceRow(
    plugin: PluginMeta,
    first: Boolean,
    selected: Boolean,
    selectable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember(plugin.id) { mutableStateOf(false) }
    var detailOpen by remember(plugin.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GySpace.rowMin)
            .foliaRowHairline(first)
            .selectable(selected = selected, role = Role.RadioButton, enabled = selectable) {
                if (!selected) onSelect()
            }
            .padding(start = GySpace.pageH, end = GySpace.xs, top = GySpace.m, bottom = GySpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioMark(selected = selected, enabled = selectable)
            Spacer(Modifier.width(GySpace.m))
            // 0.0.9 a：不再用首字当图标。优先插件自带 iconUrl，其次源站点 favicon，
            // 都取不到才回退首字占位（见 PluginIcon.kt）。
            PluginIcon(plugin)
            Spacer(Modifier.width(GySpace.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.displayName,
                        fontSize = GyText.row,
                        lineHeight = GyText.rowHeight,
                        color = Gyp.ink,
                        fontFamily = GuangYueDesign.Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(GySpace.xs))
                    Text(
                        text = "v${plugin.version}",
                        fontSize = 11.sp,
                        color = Gyp.t3,
                        fontFamily = GuangYueDesign.Sans,
                        maxLines = 1,
                    )
                    if (selected) {
                        Spacer(Modifier.width(GySpace.xs))
                        Text(
                            text = "当前",
                            fontSize = 11.sp,
                            color = Gyp.accentText,
                            fontFamily = GuangYueDesign.Sans,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                val (tone, text) = statusOf(plugin)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoliaStatus(text = text, tone = tone, modifier = Modifier.weight(1f, fill = false))
                    if (plugin.error != null || !plugin.healthNote.isNullOrBlank()) {
                        Spacer(Modifier.width(GySpace.xs))
                        Text(
                            text = if (detailOpen) "收起" else "详情",
                            fontSize = 11.sp,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                            modifier = Modifier
                                .clip(RoundedCornerShape(GyRadius.quiet))
                                .pointerInput(plugin.id) { detectTapGestures { detailOpen = !detailOpen } }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Box {
                Icon(
                    GuangYueIcons.MoreHorizontal,
                    contentDescription = "更多",
                    tint = Gyp.t2,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(GyRadius.field))
                        .pointerInput(plugin.id) { detectTapGestures { menuOpen = true } }
                        .padding(7.dp),
                )
                FoliaMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    items = buildList {
                        add(FoliaMenuItem("检查更新", onCheckUpdate, GuangYueIcons.RefreshCw))
                        if (canMoveUp) add(FoliaMenuItem("上移一位", onMoveUp, GuangYueIcons.ChevronUp))
                        if (canMoveDown) add(FoliaMenuItem("下移一位", onMoveDown, GuangYueIcons.ChevronDown))
                        add(FoliaMenuItem("卸载", onDelete, GuangYueIcons.Trash2, destructive = true, divider = true))
                    },
                )
            }
        }

        // 详情：一行中文原因 +（有的话）插件原始报错，Monospace 可滚动，默认收起。
        FoliaUnfold(expanded = detailOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = GySpace.m, top = GySpace.xs),
                verticalArrangement = Arrangement.spacedBy(GySpace.xxs),
            ) {
                val note = plugin.healthNote
                if (!note.isNullOrBlank()) {
                    Text(
                        text = "体检：" + note.take(200),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Gyp.t2,
                        fontFamily = GuangYueDesign.Sans,
                    )
                }
                val err = plugin.error
                if (err != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(GyRadius.field))
                            .background(Gyp.tint)
                            .padding(GySpace.s),
                    ) {
                        Text(
                            text = err,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Gyp.t2,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

/** Folia 的单选标记：16dp 圆环，选中时内圈填强调色。 */
@Composable
private fun RadioMark(selected: Boolean, enabled: Boolean) {
    val ring = if (selected) Gyp.accent else if (enabled) Gyp.t3 else Gyp.hairline
    val inner = Gyp.menu
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .androidxDrawBehind(selected, ring, inner),
    )
}

/** `drawBehind` 的普通函数包装：避免在 lambda 里直接读 @Composable 的 [Gyp]。 */
private fun Modifier.androidxDrawBehind(selected: Boolean, ring: Color, inner: Color): Modifier =
    this.drawBehind {
        if (selected) {
            drawCircle(ring)
            drawCircle(inner, radius = size.minDimension * 0.18f)
        } else {
            drawCircle(
                ring,
                radius = size.minDimension / 2f - 1f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f),
            )
        }
    }

/** 状态 → (语气, 一句话)。原始报错只进「详情」，行内永远是一句人话。 */
private fun statusOf(plugin: PluginMeta): Pair<FoliaTone, String> {
    val error = plugin.error
    if (error != null) return FoliaTone.Error to ("装不上：" + firstSentence(error))
    return when (plugin.health) {
        PluginHealth.OK -> FoliaTone.Ok to "可用"
        PluginHealth.NO_RESULT -> FoliaTone.Warn to "只能搜索，取不到播放地址"
        PluginHealth.NO_MEDIA -> FoliaTone.Warn to "能搜到歌，但取不到链"
        PluginHealth.SEARCH_FAILED -> FoliaTone.Error to ("搜索失败：" + (plugin.healthNote?.let(::firstSentence) ?: "源站没响应"))
        PluginHealth.UNSUPPORTED -> FoliaTone.Warn to "只能搜索"
        else -> FoliaTone.Neutral to ("还没体检" + (plugin.healthNote?.let { "：" + firstSentence(it) }.orEmpty()))
    }
}

/**
 * 行内一句话：交给统一收口点 [PluginFailureText]（0.0.9 a-1）。
 * 以前这里只切 ` at `，异常类名（`com.guangyue...PluginRuntimeException`）会一路漏到插件行里。
 */
private fun firstSentence(raw: String): String = PluginFailureText.firstSentence(raw)

/** 行顶部发丝线：首行不画；起 16dp（对应 `kit.css` 的 `.st-row::before`）。 */
@Composable
private fun Modifier.foliaRowHairline(first: Boolean): Modifier {
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

@Composable
private fun MessageBlock(
    message: String,
    details: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GySpace.xs)) {
        FoliaStatus(text = message, tone = FoliaTone.Neutral, maxLines = 3)
        if (!details.isNullOrBlank()) {
            FoliaButton(
                text = if (expanded) "收起详情" else "看详情",
                onClick = onToggle,
                variant = FoliaButtonVariant.Quiet,
            )
            FoliaUnfold(expanded = expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.field))
                        .background(Gyp.tint)
                        .padding(GySpace.s),
                ) {
                    Text(
                        text = details,
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

/** URL 安装对话框：Folia 的 `--st-menu` 卡面 + 32dp 圆角 8 输入框 + 32dp 按钮。 */
@Composable
private fun UrlDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 280.dp)
                .clip(RoundedCornerShape(GyRadius.card))
                .background(Gyp.menu)
                .padding(GySpace.pageH),
            verticalArrangement = Arrangement.spacedBy(GySpace.m),
        ) {
            Text(
                text = "从链接安装",
                fontSize = GyText.title,
                lineHeight = GyText.titleHeight,
                fontWeight = FontWeight.SemiBold,
                color = Gyp.ink,
                fontFamily = GuangYueDesign.Sans,
            )
            FoliaTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "https://… 插件 .js 或订阅 JSON",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GySpace.s), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                FoliaButton(text = "取消", onClick = onDismiss, variant = FoliaButtonVariant.Quiet)
                FoliaButton(text = "安装", onClick = onConfirm, variant = FoliaButtonVariant.Primary)
            }
        }
    }
}

/** 官方订阅地址（设置页/空态用）。 */
internal val OfficialSourceUrl: String = PluginViewModelDefaults.OFFICIAL_SOURCE
