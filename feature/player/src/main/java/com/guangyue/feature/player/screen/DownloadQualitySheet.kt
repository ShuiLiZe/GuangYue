package com.guangyue.feature.player.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.download.DownloadQualityPick
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons

const val DownloadQualitySheetTag = "download-quality-sheet"

/**
 * 2026-11-12：长按下载键弹出的「选择下载音质」面板。
 *
 * 材质与播放页浮卡（[StagePanel]）同一块玻璃：`--pn-glass` 底 + 发丝描边 + 20 圆角 + 大阴影，
 * 行样式照音质页签（左档位名 + 码率 / 格式副标题，右侧「默认」小标）。点一档即按那档加入下载并收起；
 * 点卡外或点「取消」收起。播放页与首页 / 歌单页的聚焦卡共用这一个面板。
 */
@Composable
fun DownloadQualitySheet(
    dark: Boolean,
    accent: Color,
    options: List<Quality>,
    defaultQuality: Quality?,
    onPick: (Quality) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = DownloadQualityPick.TITLE,
    subtitle: String? = null,
) {
    val colors = stagePanelColors(dark)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.36f else 0.22f))
            .clickableNoRipple(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .shadow(30.dp, SHEET_SHAPE, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(SHEET_SHAPE)
                .background(colors.glass)
                .border(1.dp, colors.outline, SHEET_SHAPE)
                .clickableNoRipple {}
                .padding(12.dp)
                .testTag(DownloadQualitySheetTag),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(GuangYueIcons.Download, null, tint = colors.t2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.t1)
                    Text(subtitle ?: DownloadQualityPick.HINT, fontSize = 12.sp, lineHeight = 17.sp, color = colors.t3, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { q ->
                    val isDefault = q == defaultQuality
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDefault) colors.fill else Color.Transparent)
                            .clickableNoRipple { onPick(q) }
                            .semantics { contentDescription = "按「${DownloadQualityPick.label(q)}」下载" }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(DownloadQualityPick.label(q), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = colors.t1)
                            Text(DownloadQualityPick.subtitle(q), fontSize = 12.sp, lineHeight = 16.sp, color = colors.t3)
                        }
                        if (isDefault) {
                            Text(
                                DownloadQualityPick.DEFAULT_TAG,
                                fontSize = 11.sp,
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.fill)
                    .clickableNoRipple(onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("取消", fontSize = 14.sp, color = colors.t2) }
        }
    }
}

/** 点一下 = [onClick]，长按 = [onLongClick]（null 时只有点按）；无水波纹，与面板键手感一致。 */
fun Modifier.tapOrHold(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.then(
        Modifier.combinedClickable(
            interactionSource = null,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
    )

private val SHEET_SHAPE = RoundedCornerShape(20.dp)
