package com.guangyue.music.nav

import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.download.DownloadQualityPick
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.viewmodel.HomeViewModel
import com.guangyue.feature.player.screen.DownloadQualitySheet

/**
 * 2026-11-12：首页 / 歌单页聚焦卡与曲目行的下载键——点一下按默认音质下，长按弹 [DownloadQualitySheet] 另选。
 * [tracks] 为 null 时不显示。
 */
@Composable
internal fun TrackDownloadPick(tracks: List<TrackEntity>?, vm: HomeViewModel, onClose: () -> Unit) {
    if (tracks.isNullOrEmpty()) return
    val ctx = LocalContext.current
    val options = remember(tracks) { vm.downloadOptionsFor(tracks.first()) }
    val def = remember(options) { DownloadQualityPick.defaultOf(options, vm.defaultDownloadQuality) }
    DownloadQualitySheet(
        dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
        accent = MaterialTheme.colorScheme.primary,
        options = options,
        defaultQuality = def,
        subtitle = if (tracks.size == 1) tracks.first().title else "${tracks.size} 首",
        onPick = { q ->
            val (added, skipped) = vm.downloadTracks(tracks, q, pinQuality = true)
            Toast.makeText(ctx, downloadToastText(tracks, added, skipped, q), Toast.LENGTH_SHORT).show()
            onClose()
        },
        onDismiss = onClose,
    )
}

/** 与歌单页「全部下载」同一套提示；[picked] 选了档位时带上档位名。 */
internal fun downloadToastText(picked: List<TrackEntity>, added: Int, skipped: Int, picked_q: Quality? = null): String = when {
    added > 0 && skipped > 0 -> "已加入下载 $added 首（$skipped 首已下载或在下载中）"
    added > 0 && picked_q != null -> DownloadQualityPick.addedToast(picked_q)
    added > 0 -> if (picked.size == 1) "已加入下载：${picked[0].title}" else "已加入下载 $added 首"
    else -> if (picked.size == 1) "这首已经下载过或正在下载" else "这些歌都已下载或正在下载"
}
