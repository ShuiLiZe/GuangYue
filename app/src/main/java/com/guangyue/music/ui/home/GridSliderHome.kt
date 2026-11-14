// 对应 Folia 文件：
//   src/library/suites/grid/home/Grid3DSlider.tsx（滑条：居中吸附、离中心越远越小越淡、聚焦卡上移 6px、底部标题块）
//   src/library/suites/grid/home/grid3DSliderModel.ts（GRID3D_CARD_GAP 48、紧凑档 GRID3D_COMPACT_*、显示文案）
//   src/library/suites/grid/home/Grid3DSliderCard.tsx（图片卡 / 拍立得卡；默认拍立得 grid3d_card_style = 'card'）
//   src/library/suites/grid/home/GridViewTabs.tsx（首页视图胶囊：选中项带底色）
//   folia-sonnet src/styles/androidPhoneFit.css ⑤（横屏紧凑档：高度是硬约束，滚动容器上下 20px、放大 1.12、
//   拍立得卡只留封面 + 6px 窄白边、标题块收细；封面按容器高度反算）
//
// 0.0.27：首页的第二种样式「网格」（Folia 网格首页的 3D 滑卡），与封面墙（Bravais）在「设置 → 外观 → 资料库界面」切换。
// 数据与回调与 BravaisWall 同一套（推荐 / 榜单 / 最近）。
// 手机化 / 未移：
//  1. 滚动用 HorizontalPager（固定页宽 = 卡宽、页距 48、两侧留白让首尾卡也能居中），一次甩动最多跨 8 张后吸附居中；
//     Folia 是原生横向滚动 + 惯性 + 吸附（滚轮 / 键盘 / 远跳 leap 都是桌面输入，未移）。
//  2. 变换逐字照 updateCardTransforms：t = min(距离 / 600, 1)，缩放 峰值 −(峰值 − 0.5)·t、不透明度 max(0.15, 1 − 0.85t)、
//     上移 6·(1 − t)、层级 10 − 9t。Folia 的「3D」就是这组缩放与淡出，没有绕轴旋转。
//  3. 点非居中卡 = 滑到它；点居中卡 = Folia onSelect（集合 → 进集合页；单曲 → 播放并进歌词页）。
//  4. 底部标题块下面多了一排操作（播放 / 暂停、进入播放页、加入队列、下载、进入）：手机上没有悬停与右键，
//     与封面墙聚焦卡的操作一致。Folia 标题块的「N 首歌曲」这边没有曲目数，换成「来源 · 类型」。
//  5. 右上角 GridHomeActions（导入文件夹 / 刷新 / 批量选择等本地库动作）与 Navidrome、地图视图未移。
package com.guangyue.music.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.player.screen.tapOrHold
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 滑卡的纯部分（单测覆盖）：尺寸、变换、文案。数值出处见文件头。 */
object GridSliderModel {
    /** GRID3D_CARD_GAP。 */
    const val CARD_GAP_DP = 48f

    /** 紧凑档滚动容器上下内边距（GRID3D_COMPACT_SCROLLER_PAD_PX）。 */
    const val COMPACT_PAD_DP = 20f

    /** 竖屏的上下留白：要兜住「卡高 ×(峰值 − 1)/2 + 6」的放大溢出（Pager 只在交叉轴多留 15dp 不裁）。 */
    const val TALL_PAD_DP = 40f

    /** GRID3D_COMPACT_PEAK_SCALE。 */
    const val COMPACT_PEAK_SCALE = 1.12f

    /** GRID3D_COMPACT_MIN_COVER_PX。 */
    const val COMPACT_MIN_COVER_DP = 64f

    /** 窄屏 / 紧凑档的封面上限（Grid3DSlider：手机宽度 192，≥768 宽 208）。 */
    fun coverCeiling(widthDp: Float): Float = if (widthDp >= 768f) 208f else 192f

    /** 拍立得卡：p-4 白边、封面 mb-4、文字区 pt-2 + 标题行 + mt-1 副标题行（行高按字号 1.4 倍、跟字体缩放）。 */
    fun polaroidExtraDp(fontScale: Float): Float = 16f + 16f + 16f + 8f + (14f * 1.4f + 4f + 12f * 1.4f) * fontScale

    /** updateCardTransforms 的 maxDist / minScale。 */
    const val MAX_DIST_DP = 600f
    const val MIN_SCALE = 0.5f

    /** 放大峰值：紧凑档 1.12；否则图片卡 1.25、拍立得 1.2。 */
    fun peakScale(compact: Boolean, polaroid: Boolean): Float = when {
        compact -> COMPACT_PEAK_SCALE
        polaroid -> 1.2f
        else -> 1.25f
    }

    /**
     * 卡宽（dp）：按容器高度反算「放大之后整个装得下」的宽度，再夹到 [下限, 上限]。
     * 紧凑档卡片是 1:1（拍立得只留封面），竖屏拍立得卡比宽多 [polaroidExtraDp]。
     * 高度量不到（≤0）时退回上限。
     */
    fun coverSize(compact: Boolean, widthDp: Float, sliderHeightDp: Float, polaroid: Boolean, fontScale: Float = 1f): Float {
        val ceiling = coverCeiling(widthDp)
        if (sliderHeightDp <= 0f) return ceiling
        val peak = peakScale(compact, polaroid)
        val pad = if (compact) COMPACT_PAD_DP else TALL_PAD_DP
        val extra = if (polaroid && !compact) polaroidExtraDp(fontScale) else 0f
        val fit = floor((sliderHeightDp - pad * 2) / peak - extra)
        // 竖屏还要给左右邻卡露个边：卡宽不超过屏宽的 62%。
        val widthCap = if (compact) ceiling else min(ceiling, floor(widthDp * 0.62f))
        return max(COMPACT_MIN_COVER_DP, min(widthCap, fit))
    }

    /** 一张卡的外观（离视口中心 [distDp]）。 */
    data class Look(val scale: Float, val alpha: Float, val liftDp: Float, val z: Float)

    fun look(distDp: Float, peak: Float): Look {
        val t = min(abs(distDp) / MAX_DIST_DP, 1f)
        return Look(
            scale = peak - (peak - MIN_SCALE) * t,
            alpha = max(0.15f, 1f - 0.85f * t),
            liftDp = -6f * (1f - t),
            z = max(1f, Math.round(10f - 9f * t).toFloat()),
        )
    }

    fun kindLabel(kind: HomeTileKind): String = when (kind) {
        HomeTileKind.TRACK -> "歌曲"
        HomeTileKind.ALBUM -> "专辑"
        HomeTileKind.STATION -> "电台"
        HomeTileKind.SHEET -> "歌单"
        HomeTileKind.PLAYLIST -> "歌单"
    }

    /** 标题块第二行：来源 · 类型（Folia 是「N 首歌曲 • 描述」，这边没有曲目数）。 */
    fun secondary(tile: HomeTile): String =
        listOf(tile.subtitle.trim(), kindLabel(tile.kind)).filter { it.isNotBlank() }.joinToString(" · ")

    /** 点居中卡时是「进入集合」还是「播放」：歌单 / 推荐歌单 / 电台 / 专辑进入，单曲播放（同封面墙聚焦卡）。 */
    fun opensCollection(tile: HomeTile): Boolean =
        tile.kind == HomeTileKind.PLAYLIST || tile.kind == HomeTileKind.SHEET || tile.kind == HomeTileKind.STATION ||
            tile.kind == HomeTileKind.ALBUM || com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(tile.key)

    /** 页签切换 / 数据变短后把焦点夹回范围内（clampFocusedIndex）。 */
    fun clampFocus(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)
}

/** 屏高低于此值（dp）即走紧凑档（与封面墙信息条同一判据）。 */
private const val GRID_COMPACT_SCREEN_DP = SEAM_COMPACT_SCREEN_DP

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridSliderHome(
    sections: List<Pair<String, List<HomeTile>>>,
    title: String,
    inkColor: Color,
    groundColor: Color,
    onOpenTile: (HomeTile) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onEnqueueTile: (HomeTile) -> Unit = {},
    onEnterTile: ((HomeTile) -> Unit)? = null,
    isCurrent: (HomeTile) -> Boolean = { false },
    playing: Boolean = false,
    onTogglePlayback: () -> Unit = {},
    onEnterPlayback: (() -> Unit)? = null,
    isQueued: (HomeTile) -> Boolean = { false },
    onDownloadTile: ((HomeTile) -> Unit)? = null,
    onDownloadTileLong: ((HomeTile) -> Unit)? = null,
    /** 拍立得卡（Folia 默认 'card'）；false = 纯图片封面。 */
    polaroid: Boolean = true,
    /** 取证用：开场聚焦第几张。 */
    initialFocus: Int = 0,
    /** 开场停在第几个页签。 */
    initialTab: Int = 0,
    /** 某个页签没内容时的说明（Folia 各页签的空态 / 不可用原因）。 */
    emptyText: (String) -> String = { "这里还没有内容" },
    /** folia-sonnet 首页头部标题旁的齿轮（onOpenSettings）；null 不显示。 */
    onOpenSettings: (() -> Unit)? = null,
    /** 页签胶囊末尾的「去别处」页签（Folia extraTabs）：点了直接跳页，不切换本页列表。 */
    extraTabs: List<Pair<String, () -> Unit>> = emptyList(),
    /** 搜索框占位（Folia home.searchDatabase）。 */
    searchHint: String = "搜索在线歌曲...",
    /** Folia 首页背后的动态背景（可视化背景层）；null 时退回焦点封面大模糊。 */
    background: (@Composable () -> Unit)? = null,
    /** 右下角平台按钮（Folia SwitcherPill / ProviderConnectButton）。 */
    providerButton: (@Composable () -> Unit)? = null,
) {
    // Folia 首页页签就是各个数据源本身（歌单 / 推荐 / 探索 / 专辑 / 本地），没有「全部」。
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, max(0, sections.size - 1))) }
    val tabs = remember(sections) { sections.map { it.first } }
    val tiles = remember(sections, tab) {
        sections.getOrNull(tab)?.second.orEmpty()
    }
    val compact = LocalConfiguration.current.screenHeightDp < GRID_COMPACT_SCREEN_DP
    val fontScale = LocalDensity.current.fontScale
    val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    // folia-sonnet 首页背后是一层当前焦点封面的大模糊（gridBackdrop / backdrop-blur），手机版同样保留。
    var backdrop by remember { mutableStateOf<Any?>(null) }
    val darkInk = inkColor.red + inkColor.green + inkColor.blue > 1.5f
    Box(modifier.fillMaxSize().background(groundColor)) {
    if (background != null) {
        background()
        // Folia Grid3D 根节点 mainBg：夜间 bg-black/20、日间 bg-white/40，压在动态背景上。
        Box(Modifier.fillMaxSize().background(if (darkInk) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f)))
    } else if (backdrop != null) {
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f; alpha = 0.55f }
                .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(72.dp) else Modifier),
        )
        Box(Modifier.fillMaxSize().background(groundColor.copy(alpha = 0.55f)))
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(insets),
    ) {
        GridHomeHeader(
            title, tabs, tab, inkColor, compact,
            onSelect = { tab = it }, onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings, extraTabs = extraTabs, searchHint = searchHint,
        )
        if (tiles.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(emptyText(tabs.getOrNull(tab).orEmpty()), color = inkColor.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
            return@Column
        }
        // 换页签 = 换一份列表：焦点回到开头（Folia 列表变化走 'none' 直接跳）。
        key(tab) {
            val pager = rememberPagerState(
                initialPage = GridSliderModel.clampFocus(if (tab == initialTab) initialFocus else 0, tiles.size),
                pageCount = { tiles.size },
            )
            val scope = rememberCoroutineScope()
            val focus = GridSliderModel.clampFocus(pager.currentPage, tiles.size)
            val focused = tiles[focus]
            androidx.compose.runtime.LaunchedEffect(focused.artwork) { backdrop = focused.artwork }
            fun select(t: HomeTile) {
                val enter = onEnterTile?.takeIf { GridSliderModel.opensCollection(t) }
                (enter ?: onOpenTile)(t)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val w = maxWidth.value
                val h = maxHeight.value
                val cover = GridSliderModel.coverSize(compact, w, h, polaroid, fontScale)
                val peak = GridSliderModel.peakScale(compact, polaroid)
                val pitch = cover + GridSliderModel.CARD_GAP_DP
                val pad = if (compact) GridSliderModel.COMPACT_PAD_DP else GridSliderModel.TALL_PAD_DP
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = max(0f, (w - cover) / 2f).dp, vertical = pad.dp),
                    pageSize = PageSize.Fixed(cover.dp),
                    pageSpacing = GridSliderModel.CARD_GAP_DP.dp,
                    beyondViewportPageCount = 2,
                    flingBehavior = PagerDefaults.flingBehavior(state = pager, pagerSnapDistance = PagerSnapDistance.atMost(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    val tile = tiles[page]
                    val pagesAway = (pager.currentPage - page) + pager.currentPageOffsetFraction
                    val look = GridSliderModel.look(pagesAway * pitch, peak)
                    Box(
                        Modifier
                            .zIndex(look.z)
                            .graphicsLayer {
                                scaleX = look.scale
                                scaleY = look.scale
                                alpha = look.alpha
                                translationY = look.liftDp * density
                            },
                    ) {
                        SliderCard(
                            tile = tile,
                            cover = cover,
                            compact = compact,
                            polaroid = polaroid,
                            focused = page == focus,
                            ink = inkColor,
                            ground = groundColor,
                        ) {
                            if (page == focus) select(tile) else scope.launch { pager.animateScrollToPage(page) }
                        }
                    }
                }
            }
            GridCaption(
                tile = focused,
                ink = inkColor,
                compact = compact,
                current = isCurrent(focused),
                playing = playing,
                inQueue = isQueued(focused),
                onPlay = { onOpenTile(focused) },
                onToggle = onTogglePlayback,
                onEnterPlayback = onEnterPlayback,
                onEnqueue = { onEnqueueTile(focused) },
                onEnter = onEnterTile?.takeIf { GridSliderModel.opensCollection(focused) }?.let { { it(focused) } },
                onDownload = onDownloadTile?.takeIf { focused.kind == HomeTileKind.TRACK }?.let { { it(focused) } },
                onDownloadLong = onDownloadTileLong?.takeIf { focused.kind == HomeTileKind.TRACK }?.let { { it(focused) } },
            )
        }
    }
    if (providerButton != null) {
        // Folia SwitcherPill：右下角浮着的平台按钮（fixed bottom-4 right-4）。
        Box(
            Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(16.dp),
        ) { providerButton() }
    }
    }
}

/**
 * 顶栏，照 folia-sonnet Grid3D.tsx 的 data-folia-home-header（手机宽度 = grid-cols-2，p-4，gap-y-4）：
 * 第一行左边「标题 + 齿轮」（text-2xl bold），右边半宽的搜索框（rounded-full，py-2 pl-10）；
 * 第二行跨两列居中的页签胶囊（p-1 rounded-full scale-90，选中项白底黑字，未选 60% 字色），胶囊内横滑。
 * 横屏（紧凑档）照 androidPhoneFit.css：头部 padding 6/12、标题 1rem、搜索框上下 4px、页签 24dp 高。
 */
@Composable
private fun GridHomeHeader(
    title: String, tabs: List<String>, active: Int, ink: Color, compact: Boolean,
    onSelect: (Int) -> Unit, onOpenSearch: () -> Unit,
    onOpenSettings: (() -> Unit)?, extraTabs: List<Pair<String, () -> Unit>>, searchHint: String,
) {
    val dark = ink.red + ink.green + ink.blue > 1.5f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = ink.copy(alpha = 0.9f), fontSize = if (compact) 16.sp else 24.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                )
                if (onOpenSettings != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onOpenSettings)
                            .semantics { contentDescription = "设置" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(GuangYueIcons.Settings, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.05f))
                    .border(1.dp, (if (dark) Color.White else Color.Black).copy(alpha = 0.10f), RoundedCornerShape(50))
                    .clickable(onClick = onOpenSearch)
                    .semantics { contentDescription = "搜索" }
                    .padding(start = 12.dp, end = 14.dp, top = if (compact) 6.dp else 9.dp, bottom = if (compact) 6.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GuangYueIcons.Search, null, tint = ink.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(searchHint, color = ink.copy(alpha = 0.4f), fontSize = if (compact) 12.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
        Row(
            Modifier
                .graphicsLayer { scaleX = 0.9f; scaleY = 0.9f }
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = if (dark) 0.10f else 0.05f))
                .padding(if (compact) 2.dp else 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val all = tabs.mapIndexed { i, l -> Triple(l, i == active) { if (i != active) onSelect(i) } } +
                extraTabs.map { (l, go) -> Triple(l, false, go) }
            all.forEach { (label, on, go) ->
                Box(
                    Modifier
                        .heightIn(min = if (compact) 24.dp else 28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable(onClick = go)
                        .semantics { contentDescription = if (on) "$label，已选中" else label }
                        .padding(horizontal = if (compact) 8.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) Color.Black else ink.copy(alpha = 0.6f),
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 一张卡（Grid3DSliderCard）：拍立得（圆角 12、白边 16、封面圆角 8 + 标题 / 副标题）或纯图片（圆角 16）。紧凑档拍立得只留封面 + 6dp 白边。 */
@Composable
private fun SliderCard(
    tile: HomeTile, cover: Float, compact: Boolean, polaroid: Boolean, focused: Boolean,
    ink: Color, ground: Color, onClick: () -> Unit,
) {
    val desc = tile.title
    if (!polaroid) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(cover.dp)
                .shadow(if (focused) 16.dp else 0.dp, shape)
                .clip(shape)
                .border(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) 0.30f else 0.10f), shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = desc },
        ) { CoverArt(tile) }
        return
    }
    val shape = RoundedCornerShape(12.dp)
    val padding = if (compact) 6.dp else 16.dp
    Column(
        Modifier
            .width(cover.dp)
            // 投影只给居中卡：邻卡带着半透明缩小后，投影会在浅色底上露成一圈方形灰边。
            .shadow(if (focused) 8.dp else 0.dp, shape)
            .clip(shape)
            .background(mixColor(ground, ink, 0.03f))
            .border(1.dp, ink.copy(alpha = if (focused) 0.18f else 0.05f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(padding),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) { CoverArt(tile) }
        if (!compact) {
            Spacer(Modifier.height(16.dp))
            Text(
                tile.title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                GridSliderModel.secondary(tile), color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CoverArt(tile: HomeTile) {
    Box(Modifier.fillMaxSize()) {
        ArtPlaceholder(tile.title, showTitle = false)
        if (tile.artwork != null) {
            AsyncImage(model = tile.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 底部标题块（data-grid3d-caption）+ 操作排。竖屏：标题 22 粗 / 副标题 13、下面居中一排操作；
 * 紧凑档（横屏）：标题 15 / 副标题 12 靠左，操作排放在同一行右侧，把竖向空间还给卡片。
 */
@Composable
private fun GridCaption(
    tile: HomeTile, ink: Color, compact: Boolean,
    current: Boolean, playing: Boolean, inQueue: Boolean,
    onPlay: () -> Unit, onToggle: () -> Unit, onEnterPlayback: (() -> Unit)?,
    onEnqueue: () -> Unit, onEnter: (() -> Unit)?,
    onDownload: (() -> Unit)?, onDownloadLong: (() -> Unit)?,
) {
    var tapped by remember(tile.key) { mutableStateOf(false) }
    val queued = tapped || inQueue
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(
                icon = if (current && playing) GuangYueIcons.Pause else GuangYueIcons.Play,
                label = if (current) (if (playing) "暂停" else "继续播放") else "立即播放",
                ink = ink, solid = true,
                onClick = if (current) onToggle else onPlay,
            )
            if (current && onEnterPlayback != null) {
                RoundAction(GuangYueIcons.Maximize, "进入播放页", ink, onClick = onEnterPlayback)
            }
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = if (queued) 0.06f else 0.10f))
                    .clickable { if (!queued) { onEnqueue(); tapped = true } }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (queued) "✓ 已在队列" else "+ 加入队列",
                    color = ink.copy(alpha = if (queued) 0.6f else 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                )
            }
            if (onDownload != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(ink.copy(alpha = 0.10f))
                        .then(if (onDownloadLong != null) Modifier.tapOrHold(onDownload, onDownloadLong) else Modifier.clickable(onClick = onDownload))
                        .semantics { contentDescription = "下载" },
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.Download, contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(18.dp)) }
            }
            if (onEnter != null) {
                RoundAction(GuangYueIcons.ArrowUpRight, "进入", ink, onClick = onEnter)
            }
        }
    }
    if (compact) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tile.title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    GridSliderModel.secondary(tile), color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Folia Grid3DSlider 标题块：h3 text-2xl font-bold + p text-xs opacity-50 font-mono mt-1，
            // 下面不放操作按钮 —— 点中间那张卡进入 / 播放，播放控制交给底部悬浮播放条。
            Text(
                tile.title, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                GridSliderModel.secondary(tile), color = ink.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ink: Color,
    solid: Boolean = false, onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (solid) ink else ink.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (solid) (if (ink.isDarkInk()) Color.White else Color.Black) else ink.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
    }
}

private fun Color.isDarkInk(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

private fun mixColor(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * 右下角平台按钮，照 Folia SwitcherPill：rounded-[26px] 菜单底色 + p-0.5，里面一个 40dp 圆形平台头像；
 * 没有登录头像时显示当前音源名的首字。点开 = 音源 / 插件 / 登录管理（Folia 的平台切换器）。
 */
@Composable
fun ProviderPill(label: String?, dark: Boolean, onClick: () -> Unit) {
    val menu = if (dark) Color(0xFF1F1F22) else Color(0xFFFFFFFF)
    Box(
        Modifier
            .shadow(8.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(menu)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5312B))
                .clickable(onClick = onClick)
                .semantics { contentDescription = "音源与登录" + (label?.let { "：$it" } ?: "") },
            contentAlignment = Alignment.Center,
        ) {
            val ch = label?.trim()?.firstOrNull()?.toString()
            if (ch != null) {
                Text(ch, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(GuangYueIcons.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
