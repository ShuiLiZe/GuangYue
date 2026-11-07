package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Artwork with a graceful fallback: when there is no url (or loading fails) we paint the album
 * accent colour instead of an empty grey square.
 */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF1A73E8),
    contentDescription: String? = null,
) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(accent.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Text("♪", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        // P1b：封面一律裁成填满（Folia `object-cover`）；B 站等 16:9 封面放进方框不再上下留黑边。
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier,
    )
}
