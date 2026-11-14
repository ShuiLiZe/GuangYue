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
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = remember(sections) { listOf("全部") + sections.map { it.first } }
    val tiles = remember(sections, tab) {
        if (tab == 0) sections.flatMap { it.second }.distinctBy { it.key } else sections.getOrNull(tab - 1)?.second.orEmpty()
    }
    val compact = LocalConfiguration.current.screenHeightDp < GRID_COMPACT_SCREEN_DP
    val fontScale = LocalDensity.current.fontScale
    val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    Column(
        modifier
            .fillMaxSize()
            .background(groundColor)
            .windowInsetsPadding(insets),
    ) {
        GridHomeHeader(title, tabs, tab, inkColor, compact, onSelect = { tab = it }, onOpenSearch = onOpenSearch)
        if (tiles.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("这里还没有内容", color = inkColor.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            return@Column
        }
        // 换页签 = 换一份列表：焦点回到开头（Folia 列表变化走 'none' 直接跳）。
        key(tab) {
            val pager = rememberPagerState(
                initialPage = GridSliderModel.clampFocus(if (tab == 0) initialFocus else 0, tiles.size),
                pageCount = { tiles.size },
            )
            val scope = rememberCoroutineScope()
            val focus = GridSliderModel.clampFocus(pager.currentPage, tiles.size)
            val focused = tiles[focus]
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
}

/** 顶栏：标题 + 视图胶囊（GridViewTabs：页签横排，选中项带底色）+ 搜索。 */
@Composable
private fun GridHomeHeader(
    title: String, tabs: List<String>, active: Int, ink: Color, compact: Boolean,
    onSelect: (Int) -> Unit, onOpenSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.5.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.06f))
                    .border(1.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, label ->
                    val on = i == active
                    // 触控区 48dp 高，可见的选中底是里面 36dp 的胶囊。
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable { if (!on) onSelect(i) }
                            .semantics { contentDescription = if (on) "$label，已选中" else label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (on) ink else ink.copy(alpha = 0.62f),
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) ink.copy(alpha = 0.14f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onOpenSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GuangYueIcons.Search, contentDescription = "搜索", tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
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
            Spacer(Modifier.width(12.dp))
            Box(Modifier.widthIn(max = 420.dp).horizontalScroll(rememberScrollState())) { actions() }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tile.title, color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                GridSliderModel.secondary(tile), color = ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.horizontalScroll(rememberScrollState())) { actions() }
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
