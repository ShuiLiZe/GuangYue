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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QualityLadder
import com.guangyue.feature.library.design.GuangYueDesign

/**
 * 音质切换弹窗。
 *
 * 档位不是写死的四档：由插件声明的 `supportedQualities` 动态渲染
 * （见 [QualityLadder.of]），插件没声明时退回 [QualityLadder.default]。
 * 选中的档位会被 [onSelect] 同时写进播放器（立即重解析直链）与偏好存储（下一首歌沿用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPickerSheet(
    /** 插件声明的 wire 列表（可为空）。 */
    declared: List<String>,
    current: Quality,
    onSelect: (Quality) -> Unit,
    onDismiss: () -> Unit,
    accent: Color = GuangYueDesign.Blue,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = QualityLadder.of(declared).ifEmpty { QualityLadder.default() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GuangYueDesign.MarbleMid,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Hearing, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "播放音质",
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = GuangYueDesign.Ink,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (declared.isEmpty()) {
                    "当前插件未声明音质，已显示全部档位"
                } else {
                    "共 ${options.size} 档 · 由当前音源提供"
                },
                fontSize = 11.sp,
                color = GuangYueDesign.InkSoft,
            )
            Spacer(Modifier.height(14.dp))

            options.sortedByDescending { it.order }.forEach { quality ->
                QualityRow(
                    quality = quality,
                    selected = quality == current,
                    accent = accent,
                    onClick = {
                        onSelect(quality)
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun QualityRow(
    quality: Quality,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp)
            .background(
                if (selected) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quality.label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) accent else GuangYueDesign.Ink,
                )
                if (quality.isLossless) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text("HD", fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(quality.subtitle, fontSize = 11.sp, color = GuangYueDesign.InkSoft)
        }
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
