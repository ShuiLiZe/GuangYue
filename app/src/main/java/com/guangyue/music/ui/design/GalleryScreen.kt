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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.music.R

/**
 * U1 组件画廊（仅 debug / 截图用，不接入 NavHost）。
 *
 * 分页方式：桌面端所有区块可以一屏铺开，手机上陀螺滚到天荒地老，所以照手机习惯
 * 把画廊切成 4 个 tab，每页单独截图（`gallery-light-{1..4}`）。用 [FoliaTabs] 本身
 * 做分页控件，顺带把 Tab 组件也展示出来。
 *
 * 文案（U1b，Hark 审查第 1 条）：界面文字一律走 `res/values/strings.xml`（中文为默认），
 * 不在 Composable 里写死；有 Folia 简体中文翻译对应键的，在 strings.xml 注释里标出键名。
 * 另：所有分区标题只留中文，不再挂英文副标题（U1 的「颜色 / COLORS」这类已删），
 * 也不做 `uppercase()`（中文没有大小写，大写处理只对英文有意义）。
 *
 * @param page 0=令牌 1=控件 2=内容 3=浮层；截图测试给固定页码，真机调试可点 tab。
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
        val tabLabels: List<String> = listOf(
            stringResource(R.string.gallery_tab_token),
            stringResource(R.string.gallery_tab_control),
            stringResource(R.string.gallery_tab_content),
            stringResource(R.string.gallery_tab_overlay),
        )
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
                    Text(stringResource(R.string.gallery_title), style = Folia.type.headline, color = c.ink)
                    Text(stringResource(R.string.gallery_subtitle), style = Folia.type.caption, color = c.inkSoft)
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
// 第 1 页：令牌
// ---------------------------------------------------------------------------------------------

@Composable
private fun TokenPage() {
    val c = Folia.colors
    Section(stringResource(R.string.section_colors)) { SwatchGrid() }
    Section(stringResource(R.string.section_type)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stage(Folia.type.headline, stringResource(R.string.type_sample_headline), c.ink)
            Stage(Folia.type.section, stringResource(R.string.type_sample_section), c.ink)
            Stage(Folia.type.bodyStrong, stringResource(R.string.type_sample_body_strong), c.ink)
            Stage(Folia.type.body, stringResource(R.string.type_sample_body), c.ink)
            Stage(Folia.type.caption, stringResource(R.string.type_sample_caption), c.inkSoft)
            Stage(Folia.type.control, stringResource(R.string.type_sample_control), c.ink)
            Stage(Folia.type.micro, stringResource(R.string.type_sample_micro), c.inkSoft)
            Stage(Folia.type.numeric, stringResource(R.string.type_sample_numeric), c.inkSoft)
        }
    }
    Section(stringResource(R.string.section_shape)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.shape_sm) to Folia.shapes.sm,
                    stringResource(R.string.shape_md) to Folia.shapes.md,
                ).forEach { (n, sh) ->
                    ShapeChip(n, sh, c.fillStrong, c.ink, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.shape_lg) to Folia.shapes.lg,
                    stringResource(R.string.shape_xl) to Folia.shapes.xl,
                ).forEach { (n, sh) ->
                    ShapeChip(n, sh, c.fillStrong, c.ink, Modifier.weight(1f))
                }
            }
            ShapeChip(stringResource(R.string.shape_pill), Folia.shapes.pill, c.accent, c.onAccent, Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.shape_metrics_note),
                style = Folia.type.caption,
                color = c.inkSoft,
            )
        }
    }
}

@Composable
private fun ShapeChip(
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.height(44.dp).clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Folia.type.caption, color = fg) }
}

// ---------------------------------------------------------------------------------------------
// 第 2 页：控件
// ---------------------------------------------------------------------------------------------

@Composable
private fun ControlPage() {
    val c = Folia.colors
    Section(stringResource(R.string.section_buttons)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoliaButton(
                    stringResource(R.string.play_all), onClick = {}, icon = GuangYueIcons.Play,
                    modifier = Modifier.weight(1f),
                )
                FoliaButton(
                    stringResource(R.string.add_to_queue), onClick = {}, kind = FoliaButtonKind.Secondary,
                    icon = GuangYueIcons.ListPlus, modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoliaButton(
                    stringResource(R.string.download), onClick = {}, kind = FoliaButtonKind.Secondary,
                    icon = GuangYueIcons.Download,
                )
                // 禁用态：整体 40% 透明（Button.tsx:11 `disabled:opacity-40`），不是橙底淡字。
                FoliaButton(stringResource(R.string.disabled_sample), onClick = {}, enabled = false)
                FoliaButton(
                    stringResource(R.string.more), onClick = {}, kind = FoliaButtonKind.Quiet,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaIconButton(GuangYueIcons.Play, stringResource(R.string.cd_play), onClick = {})
                FoliaIconButton(GuangYueIcons.Heart, stringResource(R.string.cd_like), onClick = {})
                FoliaIconButton(GuangYueIcons.ListPlus, stringResource(R.string.cd_queue), onClick = {})
                FoliaIconButton(GuangYueIcons.Download, stringResource(R.string.cd_download), onClick = {})
                FoliaIconButton(GuangYueIcons.Shuffle, stringResource(R.string.cd_shuffle), onClick = {})
                FoliaIconButton(GuangYueIcons.MoreHorizontal, stringResource(R.string.cd_more), onClick = {})
            }
        }
    }
    Section(stringResource(R.string.section_toggle)) {
        var a by remember { mutableStateOf(true) }
        var b by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sample_follow_cover_color),
                    style = Folia.type.body, color = c.ink, modifier = Modifier.weight(1f),
                )
                FoliaToggle(a, { a = it })
            }
            Row(Modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sample_wifi_only),
                    style = Folia.type.body, color = c.ink, modifier = Modifier.weight(1f),
                )
                FoliaToggle(b, { b = it })
            }
        }
    }
    Section(stringResource(R.string.section_segmented)) {
        var seg: Int by remember { mutableStateOf(0) }
        val segLabels = listOf(
            stringResource(R.string.view_list),
            stringResource(R.string.view_grid),
            stringResource(R.string.view_wall),
        )
        val kindTabs = listOf(
            stringResource(R.string.kind_track),
            stringResource(R.string.album),
            stringResource(R.string.artist),
            stringResource(R.string.playlist),
        )
        var demoTab: String by remember { mutableStateOf(kindTabs[0]) }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FoliaSegmented(
                options = listOf(0, 1, 2), selected = seg, onSelect = { seg = it },
                label = { segLabels.getOrElse(it) { "" } },
            )
            FoliaTabs<String>(tabs = kindTabs, selected = demoTab, onSelect = { demoTab = it }, label = { it })
        }
    }
    Section(stringResource(R.string.section_search)) {
        var q by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FoliaSearchField(value = q, onValueChange = { q = it }, leadingIcon = GuangYueIcons.Search)
            FoliaSearchField(
                value = "", onValueChange = {}, shape = FoliaSearchBarShape.Rounded,
                placeholder = stringResource(R.string.search_placeholder_online),
                leadingIcon = GuangYueIcons.Search,
            )
        }
    }
    Section(stringResource(R.string.section_slider)) {
        var p by remember { mutableStateOf(0.32f) }
        var v by remember { mutableStateOf(0.72f) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SliderRow(stringResource(R.string.progress_label), p, { p = it })
            SliderRow(stringResource(R.string.volume_label), v, { v = it })
            SliderRow(stringResource(R.string.disabled_label), 0.5f, {}, enabled = false)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label, style = Folia.type.caption, color = Folia.colors.inkSoft,
            modifier = Modifier.width(64.dp),
        )
        FoliaSlider(value, onValueChange, modifier = Modifier.weight(1f), enabled = enabled)
    }
}

// ---------------------------------------------------------------------------------------------
// 第 3 页：内容
// ---------------------------------------------------------------------------------------------

@Composable
private fun ContentPage() {
    Section(stringResource(R.string.section_card)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    stringResource(R.string.playlist_daily_mix), stringResource(R.string.card_subtitle_daily),
                    coverColor = Color(0xFFE0453F),
                    cover = {
                        // 封面里只留一个短字，避免与卡片标题「每日推荐 · 30 首」重复。
                        Text(stringResource(R.string.song_cover_letter_daily), style = Folia.type.headline, color = Color.White)
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    stringResource(R.string.album_liked), stringResource(R.string.card_subtitle_liked),
                    coverColor = Color(0xFF4B6BD8),
                    cover = {
                        Text(stringResource(R.string.song_cover_letter_night), style = Folia.type.headline, color = Color.White)
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    stringResource(R.string.song_night), stringResource(R.string.song_album_november),
                    coverColor = Color(0xFF2F6F5B),
                    cover = {
                        Text(stringResource(R.string.song_cover_letter_night), style = Folia.type.headline, color = Color.White)
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                FoliaCoverCard(
                    stringResource(R.string.song_sunny), stringResource(R.string.song_album_yehuimei),
                    coverColor = Color(0xFFC9A227),
                    cover = {
                        Text(stringResource(R.string.song_cover_letter_sunny), style = Folia.type.headline, color = Color.White)
                    },
                )
            }
        }
    }
    Section(stringResource(R.string.section_row)) {
        // U1b：行必须放在玻璃面板里（WallToolsQueueIsland.css），选中行的浅底 + 橙竖条才浮得起来。
        FoliaListPanel {
            FoliaSongRow(
                stringResource(R.string.song_night), stringResource(R.string.song_album_november),
                trailingText = "03:44",
                leading = { FoliaRowCover(icon = GuangYueIcons.Music) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
            )
            FoliaSongRow(
                stringResource(R.string.song_rice), stringResource(R.string.song_album_magic),
                trailingText = "03:43",
                leading = { FoliaRowCover(color = Color(0xFF8C5A2B)) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
                selected = true,
            )
            FoliaSongRow(
                stringResource(R.string.song_sunny), stringResource(R.string.song_album_yehuimei),
                trailingText = "04:29",
                leading = { FoliaRowCover(color = Color(0xFFC9A227)) },
                trailingIcon = GuangYueIcons.Plus, onTrailingClick = {}, onLongClick = {},
                showDivider = false,
            )
        }
    }
    Section(stringResource(R.string.section_row_panel)) {
        // 队列面板：面板标题 + 三行，对应 m412-light-panel-queue 的整块玻璃。
        FoliaListPanel {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.queue_title), style = Folia.type.bodyStrong,
                    color = Folia.colors.ink, modifier = Modifier.weight(1f),
                )
                Text("3 ${stringResource(R.string.tracks_unit)}", style = Folia.type.caption, color = Folia.colors.inkSoft)
            }
            FoliaSongRow(
                stringResource(R.string.song_night), stringResource(R.string.song_album_november),
                trailingText = "03:44", showDivider = false,
                leading = { FoliaRowCover(color = Color(0xFF2F6F5B)) },
            )
            FoliaSongRow(
                stringResource(R.string.song_rice), stringResource(R.string.song_album_magic),
                trailingText = "03:43", selected = true,
                leading = { FoliaRowCover(color = Color(0xFF8C5A2B)) },
            )
            FoliaSongRow(
                stringResource(R.string.song_sunny), stringResource(R.string.song_album_yehuimei),
                trailingText = "04:29", showDivider = false,
                leading = { FoliaRowCover(color = Color(0xFFC9A227)) },
            )
        }
    }
    Section(stringResource(R.string.section_empty)) {
        Box(Modifier.fillMaxWidth().clip(Folia.shapes.lg).background(Folia.colors.preview)) {
            FoliaEmptyState(
                GuangYueIcons.Inbox,
                stringResource(R.string.empty_hint),
                actionLabel = stringResource(R.string.empty_action),
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
    Section(stringResource(R.string.section_glass)) {
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
                        stringResource(
                            if (Folia.blur.supported) R.string.glass_on else R.string.glass_off,
                        ),
                        style = Folia.type.bodyStrong, color = c.ink,
                    )
                    Text(stringResource(R.string.glass_note), style = Folia.type.caption, color = c.inkSoft)
                }
            }
        }
    }
    Section(stringResource(R.string.section_sheet)) {
        FoliaButton(
            stringResource(R.string.open_song_menu), onClick = { sheet = true },
            icon = GuangYueIcons.MoreHorizontal,
        )
        Text(
            stringResource(R.string.sheet_hint),
            style = Folia.type.caption, color = c.inkSoft,
        )
    }
    Box(Modifier.fillMaxWidth().height(if (sheet) 420.dp else 0.dp)) {
        FoliaBottomSheet(
            visible = sheet,
            onDismiss = { sheet = false },
            title = stringResource(R.string.song_night),
            subtitle = stringResource(R.string.song_album_november),
        ) {
            FoliaSheetItem(GuangYueIcons.Play, stringResource(R.string.sheet_play_now), onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.ListPlus, stringResource(R.string.sheet_enqueue), onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.Download, stringResource(R.string.sheet_download), onClick = { sheet = false })
            FoliaSheetItem(GuangYueIcons.ListMusic, stringResource(R.string.sheet_view_album), onClick = { sheet = false })
            FoliaSheetItem(
                GuangYueIcons.Heart, stringResource(R.string.sheet_remove_liked),
                onClick = { sheet = false }, danger = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------------------------

/**
 * 分区标题。
 *
 * U1b（Hark 审查第 1 条）：去掉 `uppercase()` 与「中文 / ENGLISH」双语副标题 —— 中文没有大小写，
 * 大写变换只对英文有意义，英文副标题也正是审查里指出的「截图全是英文」的一部分。
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = Folia.type.section, color = Folia.colors.inkSoft)
        content()
    }
}

@Composable
private fun Stage(style: androidx.compose.ui.text.TextStyle, text: String, color: Color) {
    Text(text = text, style = style, color = color, maxLines = 1)
}

/**
 * 颜色令牌网格。
 *
 * token 名保留英文（就是代码里的字段名，方便对照），但每个名字下面另起一行中文说明
 * （Hark 审查第 1 条：token 名可留英文，旁边要有中文）。
 */
@Composable
private fun SwatchGrid() {
    val c = Folia.colors
    val items = listOf(
        "page" to (c.page to R.string.token_page),
        "ink" to (c.ink to R.string.token_ink),
        "inkSoft" to (c.inkSoft to R.string.token_ink_soft),
        "accent" to (c.accent to R.string.token_accent),
        "onAccent" to (c.onAccent to R.string.token_on_accent),
        "card" to (c.card to R.string.token_card),
        "cardShadow" to (c.cardShadow to R.string.token_card_shadow),
        "raise" to (c.raise to R.string.token_raise),
        "track" to (c.track to R.string.token_track),
        "fill" to (c.fill to R.string.token_fill),
        "line" to (c.line to R.string.token_line),
        "panelFill" to (c.panelFill to R.string.token_panel),
        "rowCurrent" to (c.rowCurrent to R.string.token_row_current),
        "rowMarker" to (c.rowMarker to R.string.token_row_marker),
        "text2" to (c.text2 to R.string.token_text2),
        "text3" to (c.text3 to R.string.token_text3),
        "glass" to (c.glassFill to R.string.token_glass),
        "ok" to (c.ok to R.string.token_ok),
        "warn" to (c.warn to R.string.token_warn),
        "error" to (c.error to R.string.token_error),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, pair) ->
                    val (color, labelRes) = pair
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(34.dp).clip(Folia.shapes.sm)
                                .background(color)
                                .androidBorder(1.dp, c.lineStrong, Folia.shapes.sm),
                        )
                        Text(name, style = Folia.type.micro, color = c.inkSoft, maxLines = 1)
                        Text(
                            stringResource(labelRes), style = Folia.type.micro, color = c.text3,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
