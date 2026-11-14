// 对应 Folia 文件：
//   src/library/suites/bravais/BravaisWall.tsx、BravaisTile.tsx（墙与砖的组装）
//   src/library/suites/bravais/bravaisConstants.ts（METRICS 128/8、getBravaisScale、OVERSCAN 420）
//   src/components/wall/rankSlots.ts（条目按离原点的排名落位）
//   src/components/wall/useWallPointerPan.ts（拖动平移）
//   src/components/app/lattice/Lattice.css（.lattice-poster：方角封面、歌单顶部两条「纸边」）
//   src/library/suites/bravais/bravais.css（.is-wall 空画框：主色 7% 发丝线）
//
// B1b：首页 Bravais 墙（无限平铺、可拖动）。
// 手机化 / 未移：
//  1. 平移 + 惯性；B1c 起点一下砖 → 整块按 blockReflows 让位、该砖长到 6×6 并显示标题（聚焦卡简化版：
//     标题 / 来源行 / 立即播放 + 加入队列（B1g），Folia 的歌手专辑链接、菜单未移），镜头 0.42s 移到它中心；
//     再点它才播放，点空画框收起。
//  B1e：首次挂载与换页签走整墙入场 / 出场→入场波次（bravaisWallWave）；同页签内数据变化走绕 Y 翻牌（flipPlan，起点为横缝）。
//  2. 条目有限：按排名填满前 N 个槽，其余是空画框（Folia bravaisFiniteWall 也是这样收尾）。
//  3. 排名原点 = 初始镜头中心；拖动时不重排（Folia 同：排名只在换数据时重算）。
package com.guangyue.music.ui.home

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import com.guangyue.core.wall.WallWave
import com.guangyue.core.wall.WallFlip
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import com.guangyue.feature.library.design.icons.GuangYueIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.guangyue.core.wall.WallSlot
import androidx.compose.ui.geometry.Offset
import com.guangyue.feature.player.screen.tapOrHold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guangyue.core.wall.BlockTemplates
import com.guangyue.core.wall.Bounds
import com.guangyue.core.wall.Bravais
import com.guangyue.core.wall.RankSlots
import com.guangyue.core.wall.WallSlots
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BravaisWall(
    sections: List<Pair<String, List<HomeTile>>>,
    title: String,
    inkColor: Color,
    groundColor: Color,
    onOpenTile: (HomeTile) -> Unit,
    onOpenSearch: () -> Unit,
    onEnqueueTile: (HomeTile) -> Unit = {},
    modifier: Modifier = Modifier,
    /** B2：歌单 / 榜单 / 专辑 / 电台聚焦后的「进入」（Folia 聚焦卡的进入键 + 再点一下聚焦卡 = 进入集合）。null 时一律直接播放。 */
    onEnterTile: ((HomeTile) -> Unit)? = null,
    /** B2：集合层。非 null 时缝换成集合信息条（面包屑返回 + 大标题 + 「N 首」+ 播放全部 / 随机），不画首页页签。 */
    collection: SeamCollection? = null,
    /** B1h：正在播放的那块（聚焦卡的播放键变暂停 / 继续，多一颗「进入播放页」）。 */
    isCurrent: (HomeTile) -> Boolean = { false },
    playing: Boolean = false,
    onTogglePlayback: () -> Unit = {},
    onEnterPlayback: (() -> Unit)? = null,
    /** B1h：「已在队列」按真实队列判断。 */
    isQueued: (HomeTile) -> Boolean = { false },
    /** 0.0.19：聚焦卡单曲下载（只对歌曲块显示）。null 时不显示下载键。 */
    onDownloadTile: ((HomeTile) -> Unit)? = null,
    /** 2026-11-12：长按下载键（选音质）。null 时长按无反应、只有点按。 */
    onDownloadTileLong: ((HomeTile) -> Unit)? = null,
    /** 集合层初始是列表（true）/ 墙（false）；null = 按曲目数自动选（[CollectionLayout.preferList]）。取证用。 */
    initialListMode: Boolean? = null,
    /** 集合层过滤词初值（取证用；平时空）。 */
    initialFilterQuery: String = "",
) {
    // B1d：缝（Seam）横条的页签 = 墙的数据来源；第 0 个是「全部」（各段按 key 去重拼起来）。
    var tab by rememberSaveable { mutableStateOf(0) }  // 进播放页再回来保留页签
    val sourceTiles = remember(sections, tab) {
        if (tab == 0) sections.flatMap { it.second }.distinctBy { it.key } else sections.getOrNull(tab - 1)?.second.orEmpty()
    }
    // 2026-11-14 集合层过滤（Folia collectionQuery + 缝里「过滤当前页」）：只收窄当前墙 / 列表，不发请求；首页不过滤。
    var filterQuery by rememberSaveable(collection?.name) { mutableStateOf(initialFilterQuery) }
    val tiles = remember(sourceTiles, filterQuery, collection != null) {
        if (collection == null) sourceTiles else CollectionFilter.apply(sourceTiles, filterQuery)
    }
    val seamFilter = if (collection == null) null else SeamFilter(
        query = filterQuery,
        onChange = { filterQuery = it },
        matches = tiles.size,
        total = sourceTiles.size,
        shown = tiles,
    )
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val vw = maxWidth.value.toDouble()
        val vh = maxHeight.value.toDouble()
        // 手机化：展开后的 6×6 砖（808 单位宽）要整张放进屏幕、两边各留 12dp，所以比例取两者较小值
        //（412dp 宽约 0.48，Folia 窄屏是 0.52）。
        val expandedWidth = BlockTemplates.EXPANSION_COLS * Bravais.METRICS.pitch - Bravais.METRICS.gap
        val scale = minOf(Bravais.scale(vw), (vw - 24) / expandedWidth)
        val (bw, bh) = WallSlots.blockSize(Bravais.METRICS)
        // 镜头（世界坐标，左上角）：开场把区块 (0,0) 的中心放到视口中心。
        val startX = bw / 2 - vw / scale / 2
        val startY = bh / 2 - vh / scale / 2
        val camX = remember { Animatable(startX.toFloat()) }
        val camY = remember { Animatable(startY.toFloat()) }
        // 排名：初始镜头中心附近 3×3 区块，条目按排名落位（换数据才重算）。
        // 缝：竖屏横条（axis y），放在世界 y=0 的区块边界上（区块边界不切砖）；上半墙上移、下半墙下移各半条高。
        val seamH = if (collection != null) SEAM_COLLECTION_HEIGHT else SEAM_HOME_HEIGHT
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val ranked = remember(tiles, vw, vh) {
            val ox = bw / 2
            val oy = bh / 2
            val pool = (-1..1).flatMap { r -> (-1..1).flatMap { c -> WallSlots.blockSlots(c, r) } }
            RankSlots.rank(pool, ox, oy).take(tiles.size).mapIndexed { i, s -> s.key to tiles[i] }.toMap()
        }
        var dragging by remember { mutableStateOf(false) }
        // B1e 整墙入场：首次挂载与每次换页签各算一次（token）；入场窗口过了才进视口的砖不动（Folia entrance.until）。
        val waveToken = remember(tab) { System.nanoTime() }
        val firstToken = remember { waveToken }
        var expanded by remember { mutableStateOf<String?>(null) }
        val expandedAt = expanded?.let { WallSlots.parseKey(it) }
        val reflowed = remember(expanded) {
            expandedAt?.let { (c, r, i) -> WallSlots.focusedBlock(c, r, i)?.associateBy { it.key } } ?: emptyMap()
        }
        fun expand(slot: WallSlot) {
            expanded = slot.key
            val g = WallSlots.focusedBlock(slot.column, slot.row, slot.slotIndex)?.get(slot.slotIndex) ?: slot
            val spec = tween<Float>(420, easing = FastOutSlowInEasing)
            scope.launch { camX.animateTo((g.centerX - vw / scale / 2).toFloat(), spec) }
            scope.launch { camY.animateTo((g.centerY - vh / scale / 2).toFloat(), spec) }
        }
        val tracker = remember { VelocityTracker() }
        // 2026-11-10 用户真机：「我喜欢」只有 1 首，集合页却是一整面空画框，找不到那首歌。
        // 集合层：曲目少（≤ LIST_DEFAULT_MAX）或多到墙放不下（> 3×3 区块 108 槽）时默认用列表，信息条上可切换。
        var listPref by rememberSaveable(collection?.name) { mutableStateOf(initialListMode) }
        val listMode = collection != null && (listPref ?: CollectionLayout.preferList(sourceTiles.size))
        if (listMode && collection != null) {
            Box(Modifier.fillMaxSize()) {
                CollectionTrackList(
                    tiles = tiles,
                    topPad = statusTop + seamH,
                    ink = inkColor,
                    ground = groundColor,
                    loading = collection.loading,
                    isCurrent = isCurrent,
                    playing = playing,
                    onPlay = onOpenTile,
                    onEnqueue = onEnqueueTile,
                    onDownload = onDownloadTile,
                    onDownloadLong = onDownloadTileLong,
                )
                Box(
                    Modifier
                        .offset { IntOffset(0, (statusTop * density.density).roundToInt()) }
                        .fillMaxWidth()
                        .height(seamH.dp)
                        .zIndex(10f)
                        .background(groundColor),
                ) {
                    SeamCollectionRow(collection, title, inkColor, listMode = true, onToggleList = { listPref = false }, filter = seamFilter)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .zIndex(15f)
                        .background(groundColor),
                )
            }
        } else {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(scale) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            tracker.resetTracking()
                            scope.launch { camX.stop(); camY.stop() }
                        },
                        onDragEnd = {
                            dragging = false
                            val v = tracker.calculateVelocity()
                            val k = density.density * scale.toFloat()
                            scope.launch { camX.animateDecay(-v.x / k, exponentialDecay(1.6f)) }
                            scope.launch { camY.animateDecay(-v.y / k, exponentialDecay(1.6f)) }
                        },
                        onDragCancel = { dragging = false },
                    ) { change, delta ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val k = density.density * scale.toFloat()
                        scope.launch {
                            camX.snapTo(camX.value - delta.x / k)
                            camY.snapTo(camY.value - delta.y / k)
                        }
                    }
                },
        ) {
            val cx = camX.value.toDouble()
            val cy = camY.value.toDouble()
            val over = Bravais.OVERSCAN / scale
            val view = Bounds(cx - over, cx + vw / scale + over, cy - over, cy + vh / scale + over)
            WallSlots.collect(view).forEach { base ->
                key(base.key) {
                    val slot = reflowed[base.key] ?: base
                    val target = ranked[base.key]
                    // 显示中的内容 + 抬起量（1 = 抬起淡出，0 = 落定）。出场完才换成 target。
                    var tile by remember { mutableStateOf(target) }
                    val lift = remember { Animatable(if (waveToken == firstToken) 1f else 0f) }
                    val rot = remember { Animatable(0f) }
                    val lastWave = remember { mutableLongStateOf(waveToken) }
                    val cornerX = camX.value.toDouble()
                    val cornerY = camY.value.toDouble()
                    LaunchedEffect(waveToken, target) {
                        val st = WallWave.stagger(base.x, base.y, cornerX, cornerY)
                        val age = (System.nanoTime() - waveToken) / 1_000_000
                        if (waveToken == firstToken && tile == target) {
                            // 首次入场：保持抬起，按错开落回；窗口过了直接落定。
                            if (age > WallWave.MAX_DELAY_MS + WallWave.IN_MS) { lift.snapTo(0f); return@LaunchedEffect }
                            delay((st - age).coerceAtLeast(0))
                            lift.animateTo(0f, tween(WallWave.IN_MS, easing = LAND))
                            return@LaunchedEffect
                        }
                        if (tile == target) return@LaunchedEffect
                        if (lastWave.longValue == waveToken) {
                            // 同一页签里数据变了：绕 Y 翻牌，从横缝向两边错开（flipPlan，origin = seam-edges y）。
                            delay(WallFlip.delayFromSeam(base.y + base.height / 2.0))
                            rot.animateTo(90f, tween(WallFlip.OUT_MS, easing = FLIP_OUT))
                            tile = target
                            rot.snapTo(-90f)
                            rot.animateTo(0f, tween(WallFlip.IN_MS, easing = FLIP_IN))
                            return@LaunchedEffect
                        }
                        lastWave.longValue = waveToken
                        if (age > WallWave.inDelay(st) + WallWave.IN_MS) { tile = target; lift.snapTo(0f); return@LaunchedEffect }
                        delay((WallWave.outDelay(st) - age).coerceAtLeast(0))
                        lift.animateTo(1f, tween(WallWave.OUT_MS, easing = RISE))
                        tile = target
                        delay((WallWave.inDelay(st) - (System.nanoTime() - waveToken) / 1_000_000).coerceAtLeast(0))
                        lift.animateTo(0f, tween(WallWave.IN_MS, easing = LAND))
                    }
                    val isOpen = base.key == expanded
                    val spec = tween<Float>(360, easing = FastOutSlowInEasing)
                    val sx by animateFloatAsState(slot.x.toFloat(), spec, label = "x")
                    val sy by animateFloatAsState(slot.y.toFloat(), spec, label = "y")
                    val sw by animateFloatAsState(slot.width.toFloat(), spec, label = "w")
                    val sh by animateFloatAsState(slot.height.toFloat(), spec, label = "h")
                    val x = ((sx - cx) * scale).toFloat()
                    // 顶栏固定在屏幕顶（用户 11-08：往下滑顶栏跟着走不行），墙不再在世界 y=0 处劈开让位，
                    // 整面墙在顶栏底下滑过；开场下移一条栏高，首屏不被盖住。
                    val y = ((sy - cy) * scale).toFloat() + seamH / 2
                    Box(
                        Modifier
                            .offset { IntOffset((x * density.density).roundToInt(), (y * density.density).roundToInt()) }
                            .size((sw * scale).toFloat().dp, (sh * scale).toFloat().dp)
                            .zIndex(if (isOpen) 20f else 0f)
                            .graphicsLayer {
                                val p = lift.value
                                translationY = -(WallWave.LIFT * scale).toFloat() * this.density * p
                                val k = 1f - 0.12f * p
                                scaleX = k
                                scaleY = k
                                alpha = 1f - p
                                rotationY = rot.value
                                cameraDistance = (1400.0 * scale).toFloat() * this.density  // CSS perspective(1400px)，世界单位
                            },
                    ) {
                        val face = tile
                        if (face == null) {
                            // 集合层按真实曲目数画：没有曲目的槽不画空画框（首页仍按 Folia 留发丝线画框）。
                            if (collection == null) WallFrame(inkColor) { if (!dragging) expanded = null }
                            else Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = null) { if (!dragging) expanded = null })
                        } else {
                            val enter = onEnterTile?.takeIf { face.kind == HomeTileKind.PLAYLIST || face.kind == HomeTileKind.SHEET || face.kind == HomeTileKind.STATION || com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(face.key) }
                            Poster(
                                face, inkColor, groundColor, scale, isOpen,
                                onPlay = { onOpenTile(face) },
                                onEnqueue = { onEnqueueTile(face) },
                                onEnter = enter?.let { { it(face) } },
                                current = isCurrent(face),
                                playing = playing,
                                onToggle = onTogglePlayback,
                                onEnterPlayback = onEnterPlayback,
                                inQueue = isQueued(face),
                                onDownload = onDownloadTile?.takeIf { face.kind == HomeTileKind.TRACK }?.let { { it(face) } },
                                onDownloadLong = onDownloadTileLong?.takeIf { face.kind == HomeTileKind.TRACK }?.let { { it(face) } },
                            ) {
                                if (dragging) return@Poster
                                if (isOpen) (enter ?: onOpenTile)(face) else expand(base)
                            }
                        }
                    }
                }
            }
            val seamTop = statusTop
            Box(
                Modifier
                    .offset { IntOffset(0, (seamTop * density.density).roundToInt()) }
                    .fillMaxWidth()
                    .height(seamH.dp)
                    .zIndex(10f)
                    .background(groundColor),
            ) {
                if (collection != null) {
                    SeamCollectionRow(collection, title, inkColor, listMode = false, onToggleList = { listPref = true }, filter = seamFilter)
                } else {
                    SeamHomeRow(title, listOf("全部") + sections.map { it.first }, tab, inkColor, onSelect = { tab = it; expanded = null }, onOpenSearch = onOpenSearch)
                }
            }
            // 状态栏：墙铺满全屏（Folia 同），顶上加一道纸色渐隐让状态栏图标看得清。
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .zIndex(15f)
                    .background(groundColor),
            )
        }
    }
    }
}

/** 入场落回 cubic-bezier(.2,.9,.3,1.04)、出场抬起 cubic-bezier(.4,0,1,1)（BravaisTile.tsx）。 */
private val LAND = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.04f)
private val RISE = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val FLIP_OUT = CubicBezierEasing(0.55f, 0f, 0.9f, 0.45f)
private val FLIP_IN = CubicBezierEasing(0.2f, 0.7f, 0.25f, 1f)

/** 对应 bravaisConstants BRAVAIS_SEAM_ROW_HOME_HEIGHT = 72。 */
private const val SEAM_HOME_HEIGHT = 72f

/** 集合层信息条高：Folia 横条完整态 212（桌面）；手机只留面包屑 + 标题 + 元数据/按钮三行，取 148。 */
private const val SEAM_COLLECTION_HEIGHT = 182f

/** B2：集合层信息条的数据（BravaisSeamCollection 的手机子集）。 */
data class SeamCollection(
    val name: String,
    val kindLabel: String,
    val count: Int?,
    val loading: Boolean,
    val failed: Boolean,
    val onBack: () -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onRetry: () -> Unit,
    /** 0.0.19：整张歌单 / 榜单下载。 */
    val onDownloadAll: (() -> Unit)? = null,
    /** 2026-11-10：曲库「补全信息」（缺歌词 / 封面的歌自动匹配）。null 时不显示。 */
    val onFillMeta: (() -> Unit)? = null,
    /** 补全进行中（按钮置灰、文字换成「补全中…」）。 */
    val fillingMeta: Boolean = false,
    /**
     * 2026-11-14：过滤时「播放全部 / 随机」只放命中的曲目（Folia playScope：过滤后的 contextTracks）。
     * 参数是命中的块（原顺序）与是否随机。null 时过滤中也放整张。
     */
    val onPlayTiles: ((tiles: List<HomeTile>, shuffle: Boolean) -> Unit)? = null,
    /** 2026-11-14 歌手页：圆形头像（Folia BravaisSeamArtist 头像），null 时只有标题。 */
    val avatar: String? = null,
)

/**
 * 集合信息条（BravaisSeamCrumbs + BravaisSeamTitle + BravaisSeamCollection 元数据行与高频按钮）：
 * 第一行面包屑「‹ 广乐 / 类型」，点根返回；第二行集合名 22/800；第三行「N 首 · 类型」或加载中 / 失败（重试），右侧「播放全部」实心胶囊 +「随机」。
 * 第三行（2026-11-14）：「过滤当前页」输入位（BravaisSeamFilterField：漏斗 + 下划线，有词时右边匹配数 + 清除），过滤时元数据行换成「命中 / 总数」。
 * 未移：收藏星标、列表面板、「⋯ 更多」、补页进度、每日推荐日期步进；「播放全部」仍放整张（Folia 过滤时放命中范围）。
 */
@Composable
private fun SeamCollectionRow(c: SeamCollection, root: String, ink: Color, listMode: Boolean = false, onToggleList: (() -> Unit)? = null, filter: SeamFilter? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = c.onBack).padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GuangYueIcons.ChevronLeft, contentDescription = "返回", tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                Text(root, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Text(" / ", color = ink.copy(alpha = 0.3f), fontSize = 13.sp)
            Text(c.kindLabel, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (c.onDownloadAll != null || c.onFillMeta != null) Spacer(Modifier.weight(1f))
            if (c.onFillMeta != null) {
                // 2026-11-10：曲库补全歌词 / 封面（自动匹配当前音源）。
                val ready = !c.loading && !c.failed && (c.count ?: 0) > 0 && !c.fillingMeta
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.08f))
                        .clickable(enabled = ready, onClick = c.onFillMeta)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GuangYueIcons.WandSparkles, contentDescription = null, tint = ink.copy(alpha = if (ready) 0.8f else 0.3f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (c.fillingMeta) "补全中…" else "补全信息", color = ink.copy(alpha = if (ready) 0.8f else 0.3f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                if (c.onDownloadAll != null) Spacer(Modifier.width(6.dp))
            }
            if (c.onDownloadAll != null) {
                // 0.0.19：整张下载。窄屏（270dp）上第三行放不下第三颗键，放到面包屑行右端。
                val ready = !c.loading && !c.failed && (c.count ?: 0) > 0
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.08f))
                        .clickable(enabled = ready, onClick = c.onDownloadAll)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GuangYueIcons.Download, contentDescription = null, tint = ink.copy(alpha = if (ready) 0.8f else 0.3f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部下载", color = ink.copy(alpha = if (ready) 0.8f else 0.3f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.avatar != null) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(ink.copy(alpha = 0.08f))) {
                    AsyncImage(model = c.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                c.name, color = ink, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (filter != null && (c.count ?: 0) > 0) {
            Spacer(Modifier.height(8.dp))
            SeamFilterField(filter, ink)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val filtering = filter != null && CollectionFilter.normalize(filter.query).isNotEmpty()
            val meta = when {
                filtering && filter != null -> "${CollectionFilter.matchLabel(filter.matches, filter.total)} 首 · ${c.kindLabel}"
                c.loading -> "加载中…"
                c.failed -> "加载失败，点此重试"
                c.count == 0 -> "没有取到曲目 · 点此重试"
                c.count != null -> "${c.count} 首 · ${c.kindLabel}"
                else -> c.kindLabel
            }
            Text(
                meta,
                color = ink.copy(alpha = if (c.failed) 0.85f else 0.62f),
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .then(if (c.failed || c.count == 0) Modifier.clickable(onClick = c.onRetry) else Modifier),
            )
            val scoped = filtering && filter != null && c.onPlayTiles != null
            val ready = !c.loading && !c.failed && (c.count ?: 0) > 0 && (!scoped || (filter?.matches ?: 0) > 0)
            val playAll: () -> Unit = { if (scoped && filter != null) c.onPlayTiles?.invoke(filter.shown, false) else c.onPlayAll() }
            val shuffleAll: () -> Unit = { if (scoped && filter != null) c.onPlayTiles?.invoke(filter.shown, true) else c.onShuffle() }
            if (onToggleList != null && (c.count ?: 0) > 0) {
                // 列表 / 墙 切换（手机加的）：列表里能直接看到每首歌并点播。
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.08f))
                        .clickable(onClick = onToggleList),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (listMode) GuangYueIcons.LayoutGrid else GuangYueIcons.List,
                        contentDescription = if (listMode) "切换为封面墙" else "切换为列表",
                        tint = ink.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (ready) ink else ink.copy(alpha = 0.2f))
                    .clickable(enabled = ready, onClick = playAll)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GuangYueIcons.Play, contentDescription = null, tint = Color.White.takeIf { ink.luminanceDark() } ?: Color.Black, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放全部", color = Color.White.takeIf { ink.luminanceDark() } ?: Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.08f))
                    .clickable(enabled = ready, onClick = shuffleAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GuangYueIcons.Shuffle, contentDescription = "随机播放", tint = ink.copy(alpha = if (ready) 0.8f else 0.3f), modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** 缝里过滤输入位的状态（Folia BravaisSeamFilter 子集）。 */
data class SeamFilter(
    val query: String,
    val onChange: (String) -> Unit,
    val matches: Int,
    val total: Int,
    /** 命中的块（原顺序）；过滤时「播放全部 / 随机」的范围。 */
    val shown: List<HomeTile> = emptyList(),
)

/**
 * 「过滤当前页」输入位（BravaisSeamFilterField 手机版）：缝的字色、一道下划线（聚焦时加深），漏斗图标 + 占位「过滤当前页」；
 * 有词时右边是匹配数与清除键。键盘「完成」收起输入法（Folia ↓ / Enter 把焦点交给墙，手机没有方向键）。
 */
@Composable
private fun SeamFilterField(f: SeamFilter, ink: Color) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val line = ink.copy(alpha = if (focused) 0.6f else 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GuangYueIcons.Filter, contentDescription = null, tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (f.query.isEmpty()) {
                Text("过滤当前页", color = ink.copy(alpha = 0.4f), fontSize = 13.sp, maxLines = 1)
            }
            BasicTextField(
                value = f.query,
                onValueChange = f.onChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 13.sp),
                cursorBrush = SolidColor(ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "过滤当前页" },
            )
        }
        if (f.query.isNotEmpty()) {
            Text(CollectionFilter.matchLabel(f.matches, f.total), color = ink.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable { f.onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GuangYueIcons.X, contentDescription = "清除过滤", tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 主色是深色（浅色主题）时按钮上的字用白色。 */
private fun Color.luminanceDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/**
 * 缝里的首页横条（BravaisSeamHome 的 HomeSeamHead + bravaisSeamRow.css `.bravais-seam-home-row`）：
 * 标题 15/800 字距 0.3em；页签 14/700、内边距 8×12、圆角 10，选中时字色转主色、底 = 当前色 14%；右侧搜索键。
 * 手机化：页签放不下时整段横向滚（Folia 也是 overflowing 时横滚）；扫描进度、直达入口、管理隐藏、折叠键未移。
 */
@Composable
private fun SeamHomeRow(title: String, tabs: List<String>, active: Int, ink: Color, onSelect: (Int) -> Unit, onOpenSearch: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.5.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, label ->
                val on = i == active
                Text(
                    label,
                    color = if (on) ink else ink.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) ink.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GuangYueIcons.Search, contentDescription = "搜索", tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}

/** 空画框：透明底 + 主色 7% 的 1px 内描边（bravais.css `.is-wall`）。 */
@Composable
private fun WallFrame(ink: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().border(1.dp, ink.copy(alpha = 0.07f)).clickable(indication = null, interactionSource = null, onClick = onClick))
}

/** 一块砖：方角封面铺满；歌单 / 电台顶部留 16 单位给两条「纸边」（Lattice.css is-kind-playlist）。 */
@Composable
private fun Poster(
    tile: HomeTile, ink: Color, ground: Color, scale: Double, open: Boolean,
    onPlay: () -> Unit = {}, onEnqueue: () -> Unit = {}, onEnter: (() -> Unit)? = null,
    current: Boolean = false, playing: Boolean = false, onToggle: () -> Unit = {}, onEnterPlayback: (() -> Unit)? = null,
    inQueue: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onDownloadLong: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val stacked = !open && tile.kind == HomeTileKind.PLAYLIST || tile.kind == HomeTileKind.SHEET || tile.kind == HomeTileKind.STATION
    val top = if (stacked) (16 * scale).toFloat().dp else 0.dp
    Box(Modifier.fillMaxSize().clickable(onClick = onClick)) {
        if (stacked) {
            // 两条：76% 宽 5 高 @y0（ink 34% 混 ground）、88% 宽 5 高 @y8（ink 58%）。
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width
                val s = (scale * density).toFloat()
                fun mix(a: Float) = Color(
                    ground.red + (ink.red - ground.red) * a,
                    ground.green + (ink.green - ground.green) * a,
                    ground.blue + (ink.blue - ground.blue) * a,
                )
                drawRect(mix(0.34f), Offset(u * 0.12f, 0f), androidx.compose.ui.geometry.Size(u * 0.76f, 5 * s))
                drawRect(mix(0.58f), Offset(u * 0.06f, 8 * s), androidx.compose.ui.geometry.Size(u * 0.88f, 5 * s))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = top)
                .background(ink.copy(alpha = 0.06f)),
        ) {
            // 没有封面 / 封面读不出来（手机本地歌常见）：先垫一块按歌名取色的占位块（首字 + 歌名），
            // 封面加载成功就盖在上面；失败时 AsyncImage 什么都不画，占位块露出来，不会是空白方块。
            ArtPlaceholder(tile.title, showTitle = !open)
            if (tile.artwork != null) {
                AsyncImage(
                    model = tile.artwork,
                    contentDescription = tile.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (open) FocusCopy(tile, onPlay, onEnqueue, onEnter, current, playing, onToggle, onEnterPlayback, inQueue, onDownload, onDownloadLong)
        }
    }
}

/**
 * 聚焦卡文字（BravaisFocusCardBody）：底部渐暗，大标题、来源行（歌手 · 类型），底部两个按钮：纯图标「立即播放」
 * 与「加入队列」（点过显示「✓ 已加入队列」）。歌手 / 专辑链接、更多菜单、正在播放时的暂停与「进入」键未移。
 */
@Composable
private fun FocusCopy(
    tile: HomeTile, onPlay: () -> Unit, onEnqueue: () -> Unit, onEnter: (() -> Unit)? = null,
    current: Boolean = false, playing: Boolean = false, onToggle: () -> Unit = {}, onEnterPlayback: (() -> Unit)? = null,
    inQueue: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onDownloadLong: (() -> Unit)? = null,
) {
    var tapped by remember(tile.key) { mutableStateOf(false) }
    val queued = tapped || inQueue
    val kindLabel = when (tile.kind) {
        HomeTileKind.TRACK -> "歌曲"
        HomeTileKind.ALBUM -> "专辑"
        HomeTileKind.STATION -> "电台"
        else -> "歌单"
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))),
    ) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Text(tile.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(tile.subtitle, kindLabel).filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 正在播放的这块：播放键 = 暂停 / 继续，旁边「进入播放页」（BravaisFocusCardBody toggles + enterPlayback）。
                Box(
                    Modifier.size(40.dp).background(Color.White, CircleShape).clickable(onClick = if (current) onToggle else onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (current && playing) GuangYueIcons.Pause else GuangYueIcons.Play,
                        contentDescription = if (current) (if (playing) "暂停" else "继续播放") else "立即播放",
                        tint = Color.Black,
                    )
                }
                if (current && onEnterPlayback != null) {
                    Box(
                        Modifier.padding(start = 10.dp).size(40.dp).background(Color.White.copy(alpha = 0.18f), CircleShape).clickable(onClick = onEnterPlayback),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GuangYueIcons.Maximize, contentDescription = "进入播放页", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = if (queued) 0.10f else 0.18f), CircleShape)
                        .clickable { if (!queued) { onEnqueue(); tapped = true } }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (queued) "✓ 已在队列" else "+ 加入队列", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (onDownload != null) {
                    // 0.0.19：单曲下载（Folia 聚焦卡没有；手机上歌单页要能挑着下，放在加入队列旁）。
                    Box(
                        Modifier.padding(start = 10.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f), CircleShape)
                            .then(if (onDownloadLong != null) Modifier.tapOrHold(onDownload, onDownloadLong) else Modifier.clickable(onClick = onDownload)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GuangYueIcons.Download, contentDescription = "下载", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                if (onEnter != null) {
                    Spacer(Modifier.weight(1f))
                    // B2：集合的「进入」（BravaisFocusCardBody 的 enter 键，箭头朝右上）。
                    Box(
                        Modifier.size(40.dp).background(Color.White.copy(alpha = 0.18f), CircleShape).clickable(onClick = onEnter),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GuangYueIcons.ArrowUpRight, contentDescription = "进入", tint = Color.White) }
                }
            }
        }
    }
}


/** 集合层布局选择（纯函数，单测覆盖）。 */
object CollectionLayout {
    /** 曲目数 ≤ 这个数默认用列表（墙上一屏只看得到一两块，少量曲目找不到歌）。 */
    const val LIST_DEFAULT_MAX = 12

    /** 墙的排名池是开场 3×3 区块（每块 12 槽），超过就放不下，也用列表。 */
    const val WALL_CAPACITY = 9 * BlockTemplates.SLOTS_PER_BLOCK

    fun preferList(count: Int): Boolean = count <= LIST_DEFAULT_MAX || count > WALL_CAPACITY

    /** 占位块的首字：第一个字母 / 数字 / 汉字（按码点取，emoji 不拆半）；没有就 null（画音符）。 */
    fun initial(title: String): String? {
        val t = title.trim()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) return String(Character.toChars(cp)).uppercase()
            i += Character.charCount(cp)
        }
        return null
    }

    /** 占位块色相：按歌名稳定取（同一首歌每次一样）。 */
    fun hue(title: String): Float = ((title.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
}

/** 无封面占位：按歌名取色的色块 + 首字（没有就音符），够大时底部再写歌名。 */
@Composable
internal fun ArtPlaceholder(title: String, showTitle: Boolean = true, modifier: Modifier = Modifier) {
    val bg = remember(title) { Color.hsl(CollectionLayout.hue(title), 0.32f, 0.46f) }
    val initial = remember(title) { CollectionLayout.initial(title) }
    BoxWithConstraints(modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth.value, maxHeight.value)
        if (initial != null) {
            Text(initial, color = Color.White.copy(alpha = 0.92f), fontSize = (side * 0.42f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        } else {
            Icon(GuangYueIcons.Music, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size((side * 0.42f).dp))
        }
        if (showTitle && side >= 72f && title.isNotBlank()) {
            Text(
                title, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 集合层的曲目列表（手机加的，Folia 集合层是墙 + 列表面板；列表面板未移，这里用一张简单列表代替）：
 * 封面（或占位块）+ 歌名 + 歌手，点一行就播放；右侧「加入队列」「下载」。
 */
@Composable
private fun CollectionTrackList(
    tiles: List<HomeTile>,
    topPad: Float,
    ink: Color,
    ground: Color,
    loading: Boolean,
    isCurrent: (HomeTile) -> Boolean,
    playing: Boolean,
    onPlay: (HomeTile) -> Unit,
    onEnqueue: (HomeTile) -> Unit,
    onDownload: ((HomeTile) -> Unit)?,
    onDownloadLong: ((HomeTile) -> Unit)? = null,
) {
    if (tiles.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(top = topPad.dp), contentAlignment = Alignment.Center) {
            Text(if (loading) "加载中…" else "这里还没有歌曲", color = ink.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().background(ground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = (topPad + 4f).dp, bottom = 200.dp),
    ) {
        items(tiles.size, key = { tiles[it].key }) { i ->
            val t = tiles[i]
            val cur = isCurrent(t)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(t) }
                    .background(if (cur) ink.copy(alpha = 0.06f) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) {
                    ArtPlaceholder(t.title, showTitle = false)
                    if (t.artwork != null) {
                        AsyncImage(model = t.artwork, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        t.title, color = ink, fontSize = 15.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (t.subtitle.isNotBlank()) {
                        Text(
                            (if (cur) (if (playing) "正在播放 · " else "已暂停 · ") else "") + t.subtitle,
                            color = ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { onEnqueue(t) },
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.ListPlus, contentDescription = "加入队列", tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                if (onDownload != null) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .then(if (onDownloadLong != null) Modifier.tapOrHold({ onDownload(t) }, { onDownloadLong(t) }) else Modifier.clickable { onDownload(t) }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GuangYueIcons.Download, contentDescription = "下载", tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
