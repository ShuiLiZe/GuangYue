package com.guangyue.feature.search

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
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.GuangYueDesign

/**
 * Search across plugins. Results are grouped by the plugin that produced them; the plugin
 * filter chips at the top narrow the fan-out.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPickPlatforms: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onAddToSheet: (com.guangyue.core.plugin.MusicItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GuangYueDesign.MarbleBottom)
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = GuangYueDesign.Ink)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text("搜索歌曲、歌手", color = GuangYueDesign.InkSoft, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = GuangYueDesign.Ink, fontSize = 14.sp),
                        cursorBrush = SolidColor(GuangYueDesign.Blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "搜索",
                    color = GuangYueDesign.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onSearch() },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.available.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                "还没有可用的插件。先去「插件管理」安装一个插件，再回来搜索。",
                color = GuangYueDesign.InkSoft,
                fontSize = 13.sp,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.available.take(4).forEach { plugin ->
                val picked = plugin.id in state.platforms
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (picked) GuangYueDesign.Blue else Color.White)
                        .clickable { onPickPlatforms(plugin.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        plugin.displayName,
                        color = if (picked) Color.White else GuangYueDesign.InkSoft,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GuangYueDesign.Blue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(state.rows) { index, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onPlay(index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = row.item.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GuangYueDesign.MarbleBlueTint),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.item.title,
                            color = GuangYueDesign.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${row.item.displayArtist} · ${row.pluginName}",
                            color = GuangYueDesign.InkSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onAddToSheet(row.item) }) {
                        Icon(Icons.Filled.Add, "收藏", tint = GuangYueDesign.InkSoft, modifier = Modifier.size(18.dp))
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        "播放",
                        tint = GuangYueDesign.Blue,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (state.rows.isEmpty() && !state.loading && state.query.isNotEmpty()) {
                item {
                    Text(
                        "没有找到结果。换首歌名试试，或检查插件是否已启用。",
                        color = GuangYueDesign.InkSoft,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            state.errors.forEach { err ->
                item {
                    Text(err, color = Color(0xFFB4472E), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
