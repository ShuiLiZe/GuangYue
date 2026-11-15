package com.guangyue.feature.library.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.icons.GuangYueIcons

private fun pageIcon(p: SettingsPage): ImageVector = when (p) {
    SettingsPage.APPEARANCE -> GuangYueIcons.Palette
    SettingsPage.PLAYER -> GuangYueIcons.Sparkles
    SettingsPage.PLAYBACK -> GuangYueIcons.AudioLines
    SettingsPage.STORAGE -> GuangYueIcons.HardDrive
    SettingsPage.ABOUT -> GuangYueIcons.CircleHelp
}

/**
 * Folia SettingsModal（手机版）的外壳：
 * 圆角 28 的大窗（暗 #1f1f22 / 亮白），右上、左下各一个极淡的大圆；顶上「帮助 / 选项」两个 26sp 粗体页签，选中的下面 3dp 白条；
 * 「选项」下一排带图标的分类胶囊（选中：浅底 + 细描边）；页头 20sp 标题 + 说明；分组小标题 = 图标 + 字（灰、加字距）；
 * 分组是 16 圆角的浅底卡片，行之间发丝线，行 = 标题 15sp + 说明 12.5sp + 右侧控件，选项列表放在行下面。
 */
@Composable
internal fun FoliaSettingsShell(
    items: List<SettingItem>,
    pages: List<SettingsPage>,
    page: SettingsPage,
    onPage: (SettingsPage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var help by rememberSaveable { mutableStateOf(false) }
    val dark = Gyp.page.luminance() < 0.5f
    val card = if (dark) Color(0xFF202023) else Color(0xFFFFFFFF)
    val ink = Gyp.ink
    val soft = ink.copy(alpha = 0.5f)
    val fill = ink.copy(alpha = if (dark) 0.05f else 0.04f)
    val hair = ink.copy(alpha = 0.08f)

    Box(modifier.fillMaxSize().background(if (dark) Color(0xFF111113) else Color(0xFFEDEDEF))) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(card).border(1.dp, hair, RoundedCornerShape(28.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(ink.copy(alpha = 0.035f), size.width * 0.42f, Offset(size.width * 0.92f, size.width * 0.12f))
                drawCircle(ink.copy(alpha = 0.03f), size.width * 0.36f, Offset(size.width * 0.02f, size.height * 0.98f))
            }
            Column(Modifier.fillMaxSize()) {
                // 帮助 / 选项
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.Top) {
                    BigTab("帮助", help, ink) { help = true }
                    Spacer(Modifier.width(22.dp))
                    BigTab("选项", !help, ink) { help = false }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(GuangYueIcons.X, "关闭", tint = soft, modifier = Modifier.size(20.dp))
                    }
                }
                if (!help) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pages.forEach { p ->
                            val on = p == page
                            Row(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(if (on) fill.copy(alpha = fill.alpha * 2.4f) else Color.Transparent)
                                    .then(if (on) Modifier.border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { onPage(p) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(pageIcon(p), null, tint = ink.copy(alpha = if (on) 0.95f else 0.7f), modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(p.label, color = ink.copy(alpha = if (on) 0.95f else 0.75f), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                }
                val shown = if (help) items.filter { it.page == SettingsPage.ABOUT } else items.filter { it.page == page }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp)) {
                    if (!help) item("head") {
                        Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                            Text(page.label, color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            if (page.desc.isNotBlank()) Text(page.desc, color = soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                        }
                    }
                    shown.groupBy { it.group }.forEach { (group, rows) ->
                        item("g-$group") {
                            Column(Modifier.padding(top = 18.dp)) {
                                Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (help) GuangYueIcons.CircleHelp else pageIcon(page), null, tint = soft, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(group, color = soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                                }
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill)) {
                                    rows.forEachIndexed { i, it ->
                                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                                                .then(if (it.onClick != null) Modifier.clickable(onClick = it.onClick) else Modifier)
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(it.label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                                if (!it.hint.isNullOrBlank()) Text(it.hint, color = soft, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                                            }
                                            it.trailing?.let { t -> Spacer(Modifier.width(12.dp)); t() }
                                        }
                                        it.below?.let { b -> Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { b() } }
                                    }
                                }
                            }
                        }
                    }
                    if (help) item("project") {
                        Text(
                            "项目：ShuiLiZe/GuangYue · 界面改写自 Folia（maimai993/folia-sonnet）",
                            color = soft, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigTab(text: String, on: Boolean, ink: Color, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
        Text(text, color = ink.copy(alpha = if (on) 1f else 0.4f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) ink else Color.Transparent))
    }
}
