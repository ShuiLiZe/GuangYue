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
    SettingsPage.APPEARANCE -> GuangYueIcons.Contrast
    SettingsPage.HOME -> GuangYueIcons.LayoutGrid
    SettingsPage.GENERAL -> GuangYueIcons.SlidersHorizontal
    SettingsPage.PLAYER -> GuangYueIcons.Sparkles
    SettingsPage.PLAYBACK -> GuangYueIcons.AudioLines
    SettingsPage.STORAGE -> GuangYueIcons.Database
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
    version: String = "",
) {
    // Folia APK：设置窗首次打开落在「帮助」页。
    var help by rememberSaveable { mutableStateOf(true) }
    // 11-17：Folia 帮助页两张卡（版本更新 / 思索帮助页）点开的说明窗。
    var helpSheet by rememberSaveable { mutableStateOf<String?>(null) }
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
                    if (help) item("tiles") {
                        // Folia HelpPage：两张带图标的卡（版本更新、思索帮助页）。键盘快捷键两组手机上没有键盘，不放。
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HelpTile(GuangYueIcons.Sparkles, "版本更新", "查看这个版本带来的变化", ink, soft, fill) { helpSheet = "notes" }
                            HelpTile(GuangYueIcons.Lightbulb, "思索帮助页", "了解广乐的基本操作与运行逻辑", ink, soft, fill) { helpSheet = "ponder" }
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
        helpSheet?.let { k ->
            androidx.activity.compose.BackHandler { helpSheet = null }
            HelpSheet(k, version) { helpSheet = null }
        }
    }
}

@Composable
private fun HelpTile(icon: ImageVector, title: String, line: String, ink: Color, soft: Color, fill: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(line, color = soft, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** 帮助卡点开的说明窗（Folia ReleaseNotesModal / 思索帮助页的手机版：一张圆角卡，标题 + 分条说明）。 */
@Composable
internal fun HelpSheet(kind: String, version: String, onClose: () -> Unit) {
    val dark = Gyp.page.luminance() < 0.5f
    val ink = Gyp.ink
    val soft = ink.copy(alpha = 0.55f)
    val title: String
    val intro: String
    val rows: List<Pair<String, String>>
    if (kind == "notes") {
        title = "版本更新"
        intro = "以下是 $version 的新功能与改进"
        rows = HelpTexts.releaseNotes
    } else {
        title = "思索帮助页"
        intro = "广乐的基本操作与运行逻辑"
        rows = HelpTexts.ponder
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(indication = null, interactionSource = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 60.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(if (dark) Color(0xFF26262A) else Color.White)
                .clickable(indication = null, interactionSource = null) {}
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(GuangYueIcons.X, "关闭", tint = soft, modifier = Modifier.size(18.dp))
                }
            }
            Text(intro, color = soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                rows.forEachIndexed { i, (h, d) ->
                    item(i) {
                        Column(Modifier.padding(vertical = 9.dp)) {
                            Text(h, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(d, color = soft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
                        }
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

/** 帮助窗的文案。版本更新每次发版改这里。 */
internal object HelpTexts {
    val releaseNotes: List<Pair<String, String>> = listOf(
        "思索按钮" to "首页右上角多了一颗灯泡（照 Folia 触屏思索按钮）：进首页或换页签时露几秒，收起后点右上角就能叫出来。点它会在首页上逐个圈出齿轮、页签、搜索框、卡片轨道、平台按钮并说明用法。不想要可以在设置 → 通用 →「触屏思索按钮」关掉。横屏讲到卡片轨道时说明卡会缩成窄条，不再挡住轨道。",
        "本地搜索支持拼音" to "搜索页选「本地」时，歌名、歌手、专辑可以用全拼或首字母搜，比如「qingtian」「qt」都能找到《晴天》。",
        "播放页底部胶囊" to "竖屏展开时照 Folia 手机版收成两行：左边播放键、中间歌名、右边播放模式和下一首，下面一行是进度条；右侧面板键挪到胶囊上方，不再和胶囊挤一行。",
        "设置分类照 Folia" to "设置的选项分成 外观、播放页、主页与歌单、播放控制、通用、存储与备份：资料库界面、卡片样式、顶部胶囊入口、记住卡片位置在「主页与歌单」，触屏思索按钮在「通用」。",
        "明暗模式" to "外观里的明暗模式改成 浅色 / 深色 / 跟随系统 三选一；手动选浅色或深色后就不再跟随系统。",
        "网格卡片衰减" to "主页与歌单 → 网格卡片：调歌单网格里远离中心的卡片最小缩到多大、最淡到多淡，可一键恢复默认。",
        "歌单详情" to "信息面板的按钮带上图标，简介太长时在面板里自己滚；横屏时面板变窄、封面压成横幅，「播放全部」不用滚就能点到。",
    )

    val ponder: List<Pair<String, String>> = listOf(
        "思索按钮" to "首页右上角的灯泡：进首页或换页签时露几秒再自己收起，收起后点右上角就能叫出来。点它会在首页上逐个圈出每个入口并说明用法；设置 → 通用 →「触屏思索按钮」可以关掉。",
        "首页卡片" to "左右滑动切换卡片，点中间那张进入歌单或开始播放。分段左边的「全部」一屏列出这一页所有卡片，点哪张就跳到哪张。",
        "页签" to "顶部的歌单、电台、专辑、本地对应各个数据源。本地页签下面再分文件夹、专辑、歌手、歌单，当前那段带字，其余只有图标。",
        "平台按钮" to "右下角的圆形按钮切换当前音源、登录在线平台账号；登录后首页歌单页签会列出账号里的歌单。",
        "播放条与播放页" to "开始播放后底部出现悬浮播放条，点歌名进入播放页。播放页的歌词样式、背景在设置的「播放页」里选，细节参数在播放页的样式弹层里调。",
        "搜索" to "首页右上的搜索框搜在线歌曲、专辑、歌手、歌单，结果按当前音源给。",
        "资料库界面" to "设置 → 主页与歌单 → 资料库界面可以在网格和墙面两套界面之间切换，切换时保留播放队列。",
    )
}
