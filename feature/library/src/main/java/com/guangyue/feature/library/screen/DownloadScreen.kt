package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guangyue.core.download.DownloadProgress
import com.guangyue.core.download.DownloadedTrack
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaEmptyState
import com.guangyue.feature.library.design.FoliaSectionTitle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.viewmodel.DownloadViewModel

/**
 * 下载列表（0.0.33 照 Folia 手机版的页面语言重做；Folia 本身没有下载页）。
 *
 * 外观同搜索页 / 网格集合页：纯深色整页、左上 40dp 圆返回钮 + 粗标题、右侧胶囊动作；
 * 一条页签胶囊切「进行中 / 需要处理 / 已完成」（选中白底黑字，同首页页签）；
 * 行 = Folia 搜索结果卡（圆角卡 + 细描边 + 48dp 封面 + 两行字 + 48dp 圆钮）。
 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onPlayLocal: (String) -> Unit = {},
    onPlayDownloaded: ((com.guangyue.core.data.model.TrackEntity) -> Unit)? = null,
    /** 右上「设置」→ 下载设置。 */
    onOpenSettings: (() -> Unit)? = null,
    vm: DownloadViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.message.collectAsStateWithLifecycle()
    // 默认落在有东西的那一栏：先进行中，其次需要处理，否则已完成。
    var tab by rememberSaveable { mutableStateOf(-1) }
    val shownTab = if (tab >= 0) tab else when {
        state.active.isNotEmpty() -> 0
        state.failed.isNotEmpty() -> 1
        else -> 2
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Gyp.page)
            .statusBarsPadding(),
    ) {
        // ---- 头部：圆返回钮 + 标题 + 胶囊动作
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Gyp.tint).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(GuangYueIcons.ChevronLeft, "返回", tint = Gyp.ink, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("下载", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gyp.ink, maxLines = 1)
                Text(
                    buildString {
                        val running = state.active.count { it.stage == com.guangyue.core.download.DownloadStage.DOWNLOADING }
                        if (running > 0) append("$running 首下载中")
                        if (state.speedText.isNotBlank() && state.speedText != "0 B") {
                            if (isNotEmpty()) append(" · ")
                            append(state.speedText).append("/s")
                        }
                        if (isEmpty()) append("已下载 ").append(state.done.size).append(" 首 · ").append(state.totalSizeText)
                    },
                    fontSize = 12.sp, color = Gyp.t3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.active.isNotEmpty()) Pill("全部停止") { vm.cancelAll() }
            if (state.failed.isNotEmpty() && shownTab == 1) Pill("清理") { vm.clearFinished() }
            if (onOpenSettings != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Gyp.tint).clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.Settings, "下载设置", tint = Gyp.t2, modifier = Modifier.size(18.dp)) }
            }
        }

        // ---- 页签胶囊（同首页 GridHomeHeader 第二行）
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Gyp.tint)
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("进行中" to state.active.size, "需要处理" to state.failed.size, "已完成" to state.done.size)
                    .forEachIndexed { i, (label, n) ->
                        val on = i == shownTab
                        Box(
                            Modifier
                                .heightIn(min = 30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (on) Color.White else Color.Transparent)
                                .clickable { tab = i }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (n > 0) "$label $n" else label,
                                fontSize = 12.sp,
                                color = if (on) Color.Black else Gyp.ink.copy(alpha = 0.6f),
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
            }
        }

        if (toast != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Gyp.tint)
                    .clickable { vm.clearMessage() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(toast.orEmpty(), fontSize = 12.sp, color = Gyp.ink) }
        }

        val empty = when (shownTab) { 0 -> state.active.isEmpty(); 1 -> state.failed.isEmpty(); else -> state.done.isEmpty() }
        if (empty) {
            EmptyDownloads(
                when (shownTab) {
                    0 -> "没有正在下载的歌"
                    1 -> "没有需要处理的任务"
                    else -> "还没有下载过歌"
                },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (shownTab) {
                0 -> items(state.active, key = { "a-" + it.key }) { p -> ActiveRow(p) { vm.cancel(p.key) } }
                1 -> items(state.failed, key = { "f-" + it.key }) { p ->
                    FailedRow(p, onRetry = { vm.retry(p.key) }, onRemove = { vm.remove(p.key) })
                }
                else -> items(state.done, key = { "d-" + it.key }) { t ->
                    DoneRow(
                        track = t,
                        onPlay = {
                            val cb = onPlayDownloaded
                            if (cb != null) vm.preparePlay(t, cb) else onPlayLocal(t.uri)
                        },
                        onRemove = { vm.remove(t.key) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 行（Folia 搜索结果卡）

@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(GyRadius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(Gyp.card)
            .border(1.dp, Gyp.cardRing, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Cover(url: String?) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Gyp.tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(GuangYueIcons.Music, null, tint = Gyp.t3, modifier = Modifier.size(20.dp))
        if (!url.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Gyp.t2, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun Titles(title: String, sub: String, subColor: Color = Gyp.t2, modifier: Modifier) {
    Column(modifier) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 12.sp, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActiveRow(progress: DownloadProgress, onStop: () -> Unit) {
    RowCard {
        Cover(null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Titles(progress.title.ifBlank { "未命名任务" }, progress.artist.ifBlank { "未知艺术家" }, modifier = Modifier)
            Spacer(Modifier.height(6.dp))
            val f = progress.fraction
            val bar = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
            if (f in 0f..1f && progress.stage == com.guangyue.core.download.DownloadStage.DOWNLOADING) {
                LinearProgressIndicator(progress = { f }, modifier = bar, color = Gyp.ink, trackColor = Gyp.tint)
            } else {
                LinearProgressIndicator(modifier = bar, color = Gyp.ink, trackColor = Gyp.tint)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                progress.stageText() + if (progress.bytesTotal > 0) " · " + progress.progressText() else "",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Gyp.t3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        RoundAction(GuangYueIcons.X, "停止", onClick = onStop)
    }
}

@Composable
private fun FailedRow(progress: DownloadProgress, onRetry: () -> Unit, onRemove: () -> Unit) {
    RowCard {
        Cover(null)
        Spacer(Modifier.width(12.dp))
        Titles(progress.title.ifBlank { "未命名任务" }, progress.error ?: progress.stageText(), subColor = Gyp.danger, modifier = Modifier.weight(1f))
        RoundAction(GuangYueIcons.RefreshCw, "重试", tint = Gyp.ink, onClick = onRetry)
        RoundAction(GuangYueIcons.Trash2, "删除", tint = Gyp.t3, onClick = onRemove)
    }
}

@Composable
private fun DoneRow(track: DownloadedTrack, onPlay: () -> Unit, onRemove: () -> Unit) {
    RowCard(onClick = onPlay) {
        Cover(track.artwork)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gyp.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(track.artist.ifBlank { "未知艺术家" }, track.album ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp, color = Gyp.t2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Gyp.tint).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        listOf(track.quality.uppercase(), track.sizeText.uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Gyp.t3, maxLines = 1,
                    )
                }
            }
        }
        RoundAction(GuangYueIcons.Trash2, "删除本地文件", tint = Gyp.t3, onClick = onRemove)
    }
}

@Composable
private fun EmptyDownloads(text: String) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 96.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(GuangYueIcons.Download, null, tint = Gyp.t3, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 14.sp, color = Gyp.t2)
        Spacer(Modifier.height(4.dp))
        Text(
            "在歌单或搜索结果里点下载，歌会存进 音乐/GuangYue/。",
            fontSize = 12.sp, color = Gyp.t3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Gyp.tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, fontSize = 12.sp, color = Gyp.ink, maxLines = 1) }
}
