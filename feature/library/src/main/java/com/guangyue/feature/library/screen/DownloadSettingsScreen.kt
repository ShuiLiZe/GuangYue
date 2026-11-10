package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guangyue.core.download.DownloadPolicy
import com.guangyue.core.download.DownloadProgress
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.FoliaButton
import com.guangyue.feature.library.design.FoliaButtonVariant
import com.guangyue.feature.library.design.FoliaGroup
import com.guangyue.feature.library.design.FoliaRow
import com.guangyue.feature.library.design.FoliaSegmented
import com.guangyue.feature.library.design.FoliaToggle
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground
import com.guangyue.feature.library.viewmodel.DownloadSettingsViewModel

/**
 * 下载设置（以前「我的 · 下载设置」直接跳到下载管理，一个设置都没有）。
 *
 * 每一项都真生效：音质 / 同时下载数 在入队时读，歌词两项在 Worker 写文件时读；SharedPreferences 落盘，重启还在。
 * 对照 Folia：默认音质（只降不升）、歌词导出「LRC 附带翻译」、存储页「已缓存歌曲 / 清空缓存」。
 * Folia 没有的（仅 Wi-Fi、文件命名格式、内嵌封面开关、自选目录）不做，也不放假开关。
 */
@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DownloadSettingsViewModel = hiltViewModel(),
) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val libraryText by vm.libraryText.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCache() }

    val chevron: @Composable () -> Unit = { Icon(GuangYueIcons.ChevronRight, null, tint = Gyp.t3, modifier = Modifier.size(16.dp)) }
    fun icon(v: androidx.compose.ui.graphics.vector.ImageVector): @Composable () -> Unit = {
        Icon(v, null, tint = Gyp.t2, modifier = Modifier.size(18.dp))
    }

    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "下载设置", onBack = onBack)
            if (message != null) {
                Text(
                    message.orEmpty(),
                    fontSize = GyText.label,
                    color = Gyp.ink,
                    modifier = Modifier
                        .padding(horizontal = GySpace.pageH, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.field))
                        .background(Gyp.tint)
                        .clickableNoRipple { vm.consumeMessage() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = GySpace.pageH, end = GySpace.pageH, top = GySpace.s, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(GySpace.group),
            ) {
                item(key = "quality") {
                    FoliaGroup(title = "音质") {
                        FoliaRow(
                            label = "下载音质",
                            hint = "按这一档取下载地址；音源没有这一档时往下降一档再试，不会往上",
                            first = true,
                            leading = icon(GuangYueIcons.AudioLines),
                        )
                        Box(Modifier.fillMaxWidth().padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.m)) {
                            QualityChips(cfg.quality.wire) { vm.pickQuality(it) }
                        }
                    }
                }
                item(key = "lyric") {
                    FoliaGroup(title = "歌词") {
                        FoliaRow(
                            label = "保存歌词 .lrc",
                            hint = "和歌曲同名的 .lrc 文件，并写进音频标签，其他播放器也能显示",
                            first = true,
                            leading = icon(GuangYueIcons.FileText),
                            trailing = { FoliaToggle(checked = cfg.saveLyric, onChange = vm::setSaveLyric) },
                        )
                        FoliaRow(
                            label = "LRC 附带翻译",
                            hint = "翻译写成与原文同一时间戳的下一行",
                            disabled = !cfg.saveLyric,
                            leading = icon(GuangYueIcons.FileText),
                            trailing = { FoliaToggle(checked = cfg.lyricTranslation, onChange = vm::setTranslation, enabled = cfg.saveLyric) },
                        )
                    }
                }
                item(key = "concurrency") {
                    FoliaGroup(title = "同时下载") {
                        FoliaRow(
                            label = "同时下载数",
                            hint = "超出的歌排队，前面的下完自动接上",
                            first = true,
                            leading = icon(GuangYueIcons.Download),
                        )
                        Box(Modifier.fillMaxWidth().padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.m)) {
                            FoliaSegmented(
                                value = cfg.concurrency,
                                options = (DownloadPolicy.MIN_CONCURRENCY..DownloadPolicy.MAX_CONCURRENCY).map { it to "$it 首" },
                                onSelect = vm::setConcurrency,
                            )
                        }
                    }
                }
                item(key = "storage") {
                    FoliaGroup(title = "存储") {
                        FoliaRow(
                            label = "存储位置",
                            hint = "歌曲在 Music/GuangYue；歌词 .lrc 系统不许放进 Music 时存到 Download/GuangYue",
                            first = true,
                            leading = icon(GuangYueIcons.Download),
                        )
                        FoliaRow(
                            label = "已下载歌曲",
                            hint = libraryText,
                            leading = icon(GuangYueIcons.AudioLines),
                            trailing = chevron,
                            onClick = onOpenDownloads,
                        )
                        FoliaRow(
                            label = "下载暂存",
                            hint = "中断留下的进度文件和已删歌曲的封面、歌词：${DownloadProgress.formatBytes(cacheBytes)}",
                            leading = icon(GuangYueIcons.FileText),
                            trailing = {
                                FoliaButton(
                                    text = "清理",
                                    onClick = vm::clearCache,
                                    variant = FoliaButtonVariant.Fill,
                                    enabled = cacheBytes > 0L,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 下载音质胶囊（与设置页「默认播放音质」同一组档位）。 */
@Composable
private fun QualityChips(current: String, onPick: (String) -> Unit) {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Quality.ladder.forEach { q ->
            val on = q.wire == current
            Text(
                q.label,
                fontSize = GyText.label,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                color = if (on) Gyp.ink else Gyp.t2,
                modifier = Modifier
                    .clip(RoundedCornerShape(GyRadius.field))
                    .background(if (on) Gyp.raise else Gyp.tint)
                    .clickableNoRipple { if (!on) onPick(q.wire) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}
