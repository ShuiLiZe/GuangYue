package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.viewmodel.DownloadViewModel

/**
 * 下载管理。
 *
 * 三个区块，顺序按「正在发生 → 出错了 → 已归档」排：
 * 1. **进行中**：排队 / 解析直链 / 传输 / 写标签 / 抓歌词 / 刷新媒体库，带实时速度与进度条；
 * 2. **失败**：可重试（复用已写入的 .part 续传）或删除记录；
 * 3. **已完成**：来自落盘索引，卸载重装后依然在，长按/按钮可删除本地文件。
 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onPlayLocal: (String) -> Unit = {},
    vm: DownloadViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.message.collectAsStateWithLifecycle()

    Surface(color = GuangYueDesign.MarbleMid, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = GuangYueDesign.Ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "下载管理",
                        fontSize = 21.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = GuangYueDesign.Ink,
                    )
                    Text(
                        buildString {
                            val running = state.active.count { it.stage == com.guangyue.core.download.DownloadStage.DOWNLOADING }
                            if (running > 0) append("$running 个任务下载中")
                            if (state.speedText.isNotBlank() && state.speedText != "0 B") {
                                if (isNotEmpty()) append(" · ")
                                append(state.speedText).append("/s")
                            }
                            if (isEmpty()) append("已完成 ").append(state.done.size).append(" 首 · ").append(state.totalSizeText)
                        },
                        fontSize = 11.sp,
                        color = GuangYueDesign.InkSoft,
                    )
                }
                if (state.active.isNotEmpty()) {
                    TextAction("全部暂停") { vm.cancelAll() }
                }
                if (state.failed.isNotEmpty()) {
                    TextAction("清理") { vm.clearFinished() }
                }
            }

            if (toast != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(GuangYueDesign.MarbleBlueTint, RoundedCornerShape(10.dp))
                        .clickable { vm.clearMessage() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(toast.orEmpty(), fontSize = 12.sp, color = GuangYueDesign.BlueDeep)
                }
            }

            if (state.isEmpty) {
                EmptyDownloads()
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.active.isNotEmpty()) {
                    item { SectionTitle("进行中", state.active.size) }
                    items(state.active, key = { "a-" + it.key }) { p ->
                        ActiveRow(
                            progress = p,
                            onPause = { vm.cancel(p.key) },
                        )
                    }
                }
                if (state.failed.isNotEmpty()) {
                    item { SectionTitle("需要处理", state.failed.size) }
                    items(state.failed, key = { "f-" + it.key }) { p ->
                        FailedRow(
                            progress = p,
                            onRetry = { vm.retry(p.key) },
                            onRemove = { vm.remove(p.key) },
                        )
                    }
                }
                if (state.done.isNotEmpty()) {
                    item { SectionTitle("已完成", state.done.size) }
                    items(state.done, key = { "d-" + it.key }) { t ->
                        DoneRow(
                            track = t,
                            onPlay = { onPlayLocal(t.uri) },
                            onRemove = { vm.remove(t.key) },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 区块

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = GuangYueDesign.Ink,
        )
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 11.sp, color = GuangYueDesign.InkSoft)
    }
}

@Composable
private fun ActiveRow(progress: DownloadProgress, onPause: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(GuangYueDesign.MarbleBlueTint, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = GuangYueDesign.Blue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    progress.title.ifBlank { "未命名任务" },
                    fontSize = 14.sp,
                    color = GuangYueDesign.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    progress.artist.ifBlank { "未知艺术家" },
                    fontSize = 11.sp,
                    color = GuangYueDesign.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val fraction = progress.fraction
                if (fraction in 0f..1f && progress.stage == com.guangyue.core.download.DownloadStage.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = GuangYueDesign.Blue,
                        trackColor = GuangYueDesign.MarbleMid,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = GuangYueDesign.Blue,
                        trackColor = GuangYueDesign.MarbleMid,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    progress.stageText() + if (progress.bytesTotal > 0) " · " + progress.progressText() else "",
                    fontSize = 10.sp,
                    color = GuangYueDesign.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPause) {
                Icon(Icons.Filled.Pause, "暂停", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FailedRow(progress: DownloadProgress, onRetry: () -> Unit, onRemove: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                null,
                tint = Color(0xFFD0574F),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    progress.title.ifBlank { "未命名任务" },
                    fontSize = 14.sp,
                    color = GuangYueDesign.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    progress.error ?: progress.stageText(),
                    fontSize = 11.sp,
                    color = Color(0xFFD0574F),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, "重试", tint = GuangYueDesign.Blue, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, "删除", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DoneRow(track: DownloadedTrack, onPlay: () -> Unit, onRemove: () -> Unit) {
    Card(onClick = onPlay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(GuangYueDesign.MarbleBlueTint, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = GuangYueDesign.Blue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    fontSize = 14.sp,
                    color = GuangYueDesign.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(track.artist.ifBlank { "未知艺术家" }, track.album ?: "", track.sizeText.uppercase())
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    fontSize = 11.sp,
                    color = GuangYueDesign.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .background(GuangYueDesign.MarbleMid, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    track.quality.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuangYueDesign.InkSoft,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, "删除本地文件", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun EmptyDownloads() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(GuangYueDesign.CardWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Download, null, tint = GuangYueDesign.Blue, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "还没有下载任务",
            fontSize = 15.sp,
            fontFamily = FontFamily.Serif,
            color = GuangYueDesign.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "在搜索结果或播放器里点「下载」，\n文件会存进 音乐/GuangYue/ 并写入 ID3 标签",
            fontSize = 11.sp,
            color = GuangYueDesign.InkSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ 小组件

@Composable
private fun Card(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .background(GuangYueDesign.CardWhite, RoundedCornerShape(14.dp))
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    Box(base) { content() }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .background(GuangYueDesign.MarbleBlueTint, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 11.sp, color = GuangYueDesign.BlueDeep)
    }
}
