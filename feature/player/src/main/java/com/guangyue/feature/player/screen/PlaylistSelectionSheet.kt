package com.guangyue.feature.player.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons

/** 歌单选择弹层里的一行。 */
data class PlaylistChoice(val id: Long, val name: String, val trackCount: Int)

const val PlaylistSelectionSheetTag = "playlist-selection-sheet"

/**
 * 「添加到歌单」弹层（Folia `components/shared/PlaylistSelectionDialog.tsx` + `AddToPlaylistHost.tsx`）。
 *
 * Folia 结构照搬：标题「添加到歌单」、说明「歌单」；顶上一颗「新建歌单」（h-10 圆角 fill 键）；
 * 下面一张圆角卡片列出歌单（每行 min-h-14：歌单名 14 + 「N 首歌曲」12，右侧 + 号），只有列表滚、标题钉住；
 * 没有歌单时写「还没有歌单」。点一行 → 加入并关闭；提交期间整张禁用。
 *
 * 手机化（folia-sonnet androidPhoneFit ② 歌单选择弹窗：面板定高、标题钉住、只有内容区滚）：
 * 底部弹层代替居中对话框；列表高度封顶「屏高 45%」，横屏也不顶出屏；新建歌单走弹层内的输入页
 * （Folia 另开 TextInputDialog「新建歌单 / 输入歌单名称 / 保存」，手机上叠第二个对话框太挤）。
 * 触控 ≥ 48dp、正文 14sp、次要 12sp。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionSheet(
    playlists: List<PlaylistChoice>,
    onSelect: suspend (Long) -> Unit,
    onCreate: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    /** 截图用：直接停在新建页。 */
    initialCreating: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(initialCreating) }
    var submitting by remember { mutableStateOf<Long?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var pendingId by remember { mutableStateOf<Long?>(null) }

    // 提交放在 LaunchedEffect 里跑（弹层内没有现成的协程作用域）。
    LaunchedEffect(pendingId) {
        val id = pendingId ?: return@LaunchedEffect
        runCatching { onSelect(id) }
        pendingId = null
        submitting = null
        onDismiss()
    }
    LaunchedEffect(pendingName) {
        val name = pendingName ?: return@LaunchedEffect
        runCatching { onCreate(name) }
        pendingName = null
        submitting = null
        onDismiss()
    }

    // 横屏（屏高 < 480dp，与胶囊 / 样式面板同一门槛）：标题与说明并一行、间距收档、列表高度按剩余高度算。
    val screenH = LocalConfiguration.current.screenHeightDp
    val compact = screenH < 480
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Gyp.card,
    ) {
        Column(
            Modifier
                .testTag(PlaylistSelectionSheetTag)
                .padding(horizontal = 20.dp)
                .padding(bottom = if (compact) 12.dp else 24.dp),
        ) {
            val title = if (creating) "新建歌单" else "添加到歌单"
            val hint = if (creating) "输入歌单名称" else "歌单"
            if (compact) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink)
                    Spacer(Modifier.width(10.dp))
                    Text(hint, fontSize = 12.sp, color = Gyp.inkSoft, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink)
                Spacer(Modifier.height(2.dp))
                Text(hint, fontSize = 12.sp, color = Gyp.inkSoft)
                Spacer(Modifier.height(14.dp))
            }
            if (creating) {
                CreatePage(
                    busy = submitting != null,
                    onCancel = { creating = false },
                    onSave = { name ->
                        submitting = -1L
                        pendingName = name
                    },
                )
            } else {
                val busy = submitting != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .alpha(if (busy) 0.4f else 1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Gyp.tint)
                        .clickable(enabled = !busy) { creating = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(GuangYueIcons.Plus, null, tint = Gyp.ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新建歌单", fontSize = 14.sp, color = Gyp.ink)
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                if (playlists.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("还没有歌单", fontSize = 14.sp, color = Gyp.inkSoft)
                    }
                } else {
                    // 竖屏封顶屏高 45%；横屏扣掉拖拽条 + 标题 + 新建键 + 状态栏后的余量（约 360dp 屏高 → 140dp，2 行半露出可滚）。
                    val maxList = if (compact) (screenH - 220).dp.coerceAtLeast(112.dp)
                    else (screenH * 0.45f).dp.coerceAtLeast(120.dp)
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxList)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Gyp.tint),
                    ) {
                        items(playlists, key = { it.id }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(enabled = !busy) {
                                        submitting = p.id
                                        pendingId = p.id
                                    }
                                    .alpha(if (busy && submitting != p.id) 0.4f else 1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = Gyp.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${p.trackCount} 首歌曲",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = Gyp.inkSoft,
                                        maxLines = 1,
                                    )
                                }
                                Icon(GuangYueIcons.Plus, null, tint = Gyp.inkSoft, modifier = Modifier.size(16.dp))
                            }
                            if (p != playlists.last()) {
                                HorizontalDivider(Modifier.padding(start = 16.dp), color = Gyp.ink.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePage(
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    initial: String = "",
    confirmLabel: String = "保存",
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val canSave = name.isNotBlank() && !busy
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Gyp.tint)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (name.isEmpty()) Text("输入歌单名称", fontSize = 14.sp, color = Gyp.inkSoft)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Gyp.ink),
            cursorBrush = SolidColor(Gyp.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name.trim()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Gyp.tint)
                .clickable(enabled = !busy, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Text("取消", fontSize = 14.sp, color = Gyp.ink) }
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .alpha(if (canSave) 1f else 0.4f)
                .clip(RoundedCornerShape(12.dp))
                .background(Gyp.ink)
                .clickable(enabled = canSave) { onSave(name.trim()) },
            contentAlignment = Alignment.Center,
        ) { Text(confirmLabel, fontSize = 14.sp, color = Gyp.card, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * 起名弹层（Folia 集合「⋯ 更多 → 改名」：标题「给这个歌单改名」、输入框带原名、取消 / 保存）。
 * 与「新建歌单」页同一套输入行与按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistNameSheet(
    title: String,
    hint: String,
    initial: String,
    onSave: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        val name = pending ?: return@LaunchedEffect
        runCatching { onSave(name) }
        pending = null
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Gyp.card) {
        Column(Modifier.testTag(PlaylistSelectionSheetTag).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink)
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 12.sp, color = Gyp.inkSoft)
            Spacer(Modifier.height(14.dp))
            CreatePage(busy = pending != null, onCancel = onDismiss, onSave = { pending = it }, initial = initial)
        }
    }
}
