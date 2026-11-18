package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.guangyue.feature.library.design.Gyp

/** Folia kitTheme SAMPLE_COVERS。 */
private val SampleCovers = listOf(0xFF6D5A8C, 0xFF3F6F7A, 0xFFA8664B, 0xFF56745A, 0xFF8A4F5D, 0xFF4D5F8F, 0xFF8C7A45).map { Color(it) }

/**
 * 11-18 照 Folia settings/pages/pictures.tsx `GridCardsPreview`：七张卡一排，中间那张在网格中心、两端在最远处，
 * 尺寸与透明度按 hexCardFalloff computeHexCardFrame（中心 1.1 → minScale、1 → minOpacity、层级 50 → 1）。
 * 卡 66×90（全画幅 + 正方形 = 78×78），间距 10；封面色块高 60（全画幅铺满），下面两道字条。预览框 136 高、圆角 8、--st-pv 底。
 */
@Composable
internal fun GridCardsPreview(minScale: Float, minOpacity: Float, fullBleedCover: Boolean, squareCards: Boolean) {
    val (w, h) = if (fullBleedCover && squareCards) 78 to 78 else 66 to 90
    val pitch = w + 10
    val count = 7
    val middle = (count - 1) / 2f
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(8.dp)).background(Gyp.preview),
        contentAlignment = Alignment.Center,
    ) {
        for (i in 0 until count) {
            val baseX = (i - middle) * pitch
            val progress = (kotlin.math.abs(baseX) / (middle * pitch)).coerceIn(0f, 1f)
            val scale = 1.1f - (1.1f - minScale) * progress
            val alpha = 1f - (1f - minOpacity) * progress
            val bar = if (fullBleedCover) Color.White else Gyp.ink
            Box(
                Modifier
                    .zIndex(50f - 49f * progress)
                    .offset(x = baseX.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .size(w.dp, h.dp)
                    .shadow(3.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Gyp.card)
                    .border(1.dp, Gyp.line2, RoundedCornerShape(6.dp)),
            ) {
                Box(Modifier.fillMaxWidth().then(if (fullBleedCover) Modifier.fillMaxHeight() else Modifier.height(60.dp)).background(SampleCovers[i]))
                Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth()) {
                    Box(Modifier.padding(bottom = 9.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(bar.copy(alpha = 0.8f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f).height(4.dp).clip(CircleShape).background(bar.copy(alpha = 0.35f)))
                }
            }
        }
    }
}
