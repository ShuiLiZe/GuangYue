package com.guangyue.music.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guangyue.feature.library.design.icons.GuangYueIcons

/**
 * U1 组件画廊（仅 debug / 截图用，不接入 NavHost）。
 *
 * 分页方式：桌面端所有区块可以一屏铺开，手机上陀螺滚到天荒地老，所以照手机习惯
 * 把画廊切成 4 个 tab，每页单独截图（`gallery-light-{1..4}`）。用 [FoliaTabs] 本身
 * 做分页控件，顺带把 Tab 组件也展示出来。
 *
 * @param page 0=Token 1=控件 2=内容 3=浮层；截图测试给固定页码，真机调试可点 tab。
 * @param showSheet 是否把底部弹层示例展开（截图用；默认 false）。
 */
@Composable
fun GalleryScreen(
    dark: Boolean,
    modifier: Modifier = Modifier,
    page: Int = 0,
    showSheet: Boolean = false,
) {
    FoliaTheme(dark = dark) {
        val tabLabels: List<String> = listOf("Token", "控件", "内容", "浮层")
        var tab: Int by remember { mutableStateOf(page.coerceIn(0, 3)) }
        val c = Folia.colors
        val s = Folia.spacing
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(c.page)
                .padding(horizontal = s.screenPadDp, vertical = s.x4Dp),
            verticalArrangement = Arrangement.spacedBy(s.x4Dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("广乐 · U1 设计地基", style = Folia.type.headline, color = c.ink)
                    Text("Folia token + 基础组件 · 360×800dp", style = Folia.type.caption, color = c.inkSoft)
                }
            }
            FoliaTabs<Int>(
                tabs = listOf(0, 1, 2, 3),
                selected = tab,
                label = { index -> tabLabels[index] },
                onSelect = { picked -> tab = picked },
            )
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(s.sectionGapDp),
            ) {
                when (tab) {
                    0 -> TokenPage()
                    1 -> ControlPage()
                    2 -> ContentPage()
                    else -> OverlayPage(showSheet = showSheet)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 第 1 页：Token
// ---------------------------------------------------------------------------------------------

@Composable
private fun TokenPage() {
    val c = Folia.colors
    Section("颜色 / Colors") { SwatchGrid() }
    Section("字体 / Type") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stage(Folia.type.headline, "headline 24/30 Bold", c.ink)
            Stage(Folia.type.section, "SECTION 15/800", c.ink)
            Stage(Folia.type.bodyStrong, "bodyStrong 14/600 夜曲", c.ink)
            Stage(Folia.type.body, "body 14/22 夜曲 Nocturne", c.ink)
            Stage(Folia.type.caption, "caption 12/18 艺人 · 专辑 · 2024", c.inkSoft)
            Stage(Folia.type.control, "control 14 按钮文字", c.ink)
            Stage(Folia.type.micro, "micro 12 刻度名", c.inkSoft)
            Stage(Folia.type.numeric, "03:24 / 04:12", c.inkSoft)
        }
    }
    Section("圆角与间距 / Shape & Spacing") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sm 6" to Folia.shapes.sm, "md 8" to Folia.shapes.md).forEach { (n, sh) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(sh).background(c.fillStrong),
                        contentAlignment = Alignment.Center,
                    ) { Text(n, style = Folia.type.caption, color = c.ink) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("lg 12" to Folia.shapes.lg, "xl 16 · 卡片" to Folia.shapes.xl).forEach { (n, sh) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(sh).background(c.fillStrong),
                        contentAlignment = Alignment.Center,
                    ) { Text(n, style = Folia.type.caption, color = c.ink) }
                }
            }
            Box(Modifier.fillMaxWidth().height(44.dp).clip(Folia.shapes.pill).background(c.accent),
                contentAlignment = Alignment.Center) {
                Text("pill 胶囊", style = Folia.type.caption, color = c.onAccent)
            }
            Text(
                "页面边距 16dp · 分区间距 18dp · 行高 ≥56dp · 触控 ≥48dp",
                style = Folia.type.caption,
                color = c.inkSoft,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 第 2 页：控件
// ---------------------------------------------------------------------------------------------

@Composable
private fun ControlPage() {
    val c = Folia.colors
    Section("按钮 / Buttons") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoliaButton("播放全部", onClick = {}, icon = GuangYueIcons.Play, modifier = Modifier.weight(1f))
                FoliaButton(
                    "加入队列", onClick = {}, kind = FoliaButtonKind.Secondary,
                    icon = GuangYueIcons.ListPlus, modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoliaButton("下载", onClick = {}, kind = FoliaButtonKind.Secondary, icon = GuangYueIcons.Download)
                FoliaButton("已禁用", onClick = {}, enabled = false)
                FoliaButton("更多", onClick = {}, kind = FoliaButtonKind.Quiet)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaIconButton(GuangYueIcons.Play, "播放", onClick = {})
                FoliaIconButton(GuangYueIcons.Heart, "喜欢", onClick = {})
                FoliaIconButton(GuangYueIcons.ListPlus, "加入队列", onClick = {})
                FoliaIconButton(GuangYueIcons.Download, "下载", onClick = {})
                FoliaIconButton(GuangYueIcons.Shuffle, "随机", onClick = {})
                FoliaIconButton(GuangYueIcons.MoreHorizontal, "更多", onClick = {})
            }
        }
    }
    Section("开关 / Toggle") {
        var a by remember { mutableStateOf(true) }
        var b by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("跟随专辑封面取色", style = Folia.type.body, color = c.ink, modifier = Modifier.weight(1f))
                FoliaToggle(a, { a = it })
            }
            Row(Modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("仅 Wi-Fi 下载", style = Folia.type.body, color = c.ink, modifier = Modifier.weight(1f))
                FoliaToggle(b, { b = it })
            }
        }
    }
    Section("分段与 Tab / Segmented & Tabs") {
        var seg: Int by remember { mutableStateOf(0) }
        var demoTab: String by remember { mutableStateOf("歌曲") }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FoliaSegmented(options = listOf(0, 1, 2), selected = seg, onSelect = { seg = it }, label = { listOf("列表", "网格", "墙面").getOrElse(it) { "" } })
            FoliaTabs<String>(tabs = listOf("歌曲", "专辑", "艺人", "歌单"), selected = demoTab, onSelect = { demoTab = it }, label = { it })
        }
    }
    Section("搜索框 / Search") {
        var q by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FoliaSearchField(value = q, onValueChange = { q = it }, leadingIcon = GuangYueIcons.Search)
            FoliaSearchField(
                value = "", onValueChange = {}, shape = FoliaSearchBarShape.Rounded,
                placeholder = "搜索歌曲 / 专辑 / 艺人", leadingIcon = GuangYueIcons.Search,
            )
        }
    }
    Section("滑条 / Slider") {
        var p by remember { mutableStateOf(0.32f) }
        var v by remember { mutableStateOf(0.72f) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("播放进度", style = Folia.type.caption, color = c.inkSoft, modifier = Modifier.width(64.dp))
                FoliaSlider(p, { p = it }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("音量", style = Folia.type.caption, color = c.inkSoft, modifier = Modifier.width(64.dp))
                FoliaSlider(v, { v = it }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("禁用态", style = Folia.type.caption, color = c.inkSoft, modifier = Modifier.width(64.dp))
                FoliaSlider(0.5f, {}, modifier = Modifier.weight(1f), enabled = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 第 3 页：内容
// ---------------------------------------------------------------------------------------------

@Composable
private fun ContentPage() {
    val c = Folia.colors
    Section("封面卡 / Cover card") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    "Daily Mix", "18 首 · 每日推荐",
                    coverColor = Color(0xFFE0453F),
                    cover = {
                        Text("Mix 1", style = Folia.type.headline, color = Color.White)
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    "Cloud Fixture", "网易云 · 32 首",
                    coverColor = Color(0xFF4B6BD8),
                    cover = {
                        Text("Cloud", style = Folia.type.headline, color = Color.White)
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    "夜曲", "周杰伦 · 十一月的萧邦",
                    coverColor = Color(0xFF2F6F5B),
                    cover = { Text("夜曲", style = Folia.type.headline, color = Color.White) },
                )
            }
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    "晴天", "周杰伦 · 叶惠美",
                    coverColor = Color(0xFFC9A227),
                    cover = { Text("晴天", style = Folia.type.headline, color = Color.White) },
                )
            }
        }
    }
    Section("歌曲行 / Song row") {
        Column(Modifier.clip(Folia.shapes.xl).background(c.preview)) {
            FoliaSongRow(
                "夜曲", "周杰伦 · 十一月的萧邦", trailingText = "03:44",
                leading = { FoliaRowCover(icon = GuangYueIcons.Music) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
            )
            FoliaSongRow(
                "Midnight City", "M83 · Hurry Up, We're Dreaming", trailingText = "04:03",
                leading = { FoliaRowCover(color = Color(0xFF2F6F5B)) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
                selected = true,
            )
            FoliaSongRow(
                "晴天", "周杰伦 · 叶惠美", trailingText = "04:29",
                leading = { FoliaRowCover(color = Color(0xFFC9A227)) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
                showDivider = false,
            )
        }
    }
    Section("空态 / Empty") {
        Box(Modifier.fillMaxWidth().clip(Folia.shapes.lg).background(c.preview)) {
            FoliaEmptyState(
                GuangYueIcons.Inbox,
                "这里还什么都没有。搜索一首歌，或者先建一个歌单。",
                actionLabel = "去搜索",
                onAction = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 第 4 页：浮层（毛玻璃 + 底部弹层）
// ---------------------------------------------------------------------------------------------

@Composable
private fun OverlayPage(showSheet: Boolean) {
    val c = Folia.colors
    var sheet by remember { mutableStateOf(showSheet) }
    Section("毛玻璃 / Glass panel") {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            FoliaBlurLayer(Folia.blur.xl, Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(c.accent.copy(alpha = 0.55f), Color(0xFF4B6BD8).copy(alpha = 0.5f)),
                        ),
                    ),
                )
            }
            FoliaGlassPanel(Modifier.align(Alignment.Center).padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (Folia.blur.supported) "Android 12+ · Modifier.blur(24dp)" else "低版本降级 · 半透明底",
                        style = Folia.type.bodyStrong, color = c.ink,
                    )
                    Text("index.css:163-168 · 底色 85% + 描边 12%", style = Folia.type.caption, color = c.inkSoft)
                }
            }
        }
    }
    Section("底部弹层 / Bottom sheet") {
        FoliaButton("打开歌曲菜单", onClick = { sheet = true }, icon = GuangYueIcons.MoreHorizontal)
        Text(
            "桌面右键菜单 → 长按列表行 → 底部弹层（下拉或系统返回关闭，无右上角叉号）",
            style = Folia.type.caption, color = c.inkSoft,
        )
    }
    Box(Modifier.fillMaxWidth().height(if (sheet) 420.dp else 0.dp)) {
        FoliaBottomSheet(
            visible = sheet,
            onDismiss = { sheet = false },
            title = "夜曲",
            subtitle = "周杰伦 · 十一月的萧邦",
        ) {
            FoliaSheetItem(GuangYueIcons.Play, "立即播放", onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.ListPlus, "加入播放队列", onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.Download, "下载到本地", onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.ListMusic, "查看专辑", onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.Heart, "移出喜欢", onClick = { sheet = false }, danger = true)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = Folia.type.section, color = Folia.colors.inkSoft)
        content()
    }
}

@Composable
private fun Stage(style: androidx.compose.ui.text.TextStyle, text: String, color: Color) {
    Text(text = text, style = style, color = color, maxLines = 1)
}

@Composable
private fun SwatchGrid() {
    val c = Folia.colors
    val items = listOf(
        "page" to c.page, "ink" to c.ink, "inkSoft" to c.inkSoft, "accent" to c.accent,
        "onAccent" to c.onAccent, "card" to c.card, "raise" to c.raise, "track" to c.track,
        "fill" to c.fill, "line" to c.line, "text2" to c.text2, "text3" to c.text3,
        "glass" to c.glassFill, "ok" to c.ok, "warn" to c.warn, "error" to c.error,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(34.dp).clip(Folia.shapes.sm)
                                .background(color)
                                .androidBorder(1.dp, c.lineStrong, Folia.shapes.sm),
                        )
                        Text(name, style = Folia.type.micro, color = c.inkSoft)
                    }
                }
            }
        }
    }
}
