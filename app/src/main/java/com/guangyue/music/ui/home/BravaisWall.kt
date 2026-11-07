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
) {
    // B1d：缝（Seam）横条的页签 = 墙的数据来源；第 0 个是「全部」（各段按 key 去重拼起来）。
    var tab by rememberSaveable { mutableStateOf(0) }  // 进播放页再回来保留页签
    val tiles = remember(sections, tab) {
        if (tab == 0) sections.flatMap { it.second }.distinctBy { it.key } else sections.getOrNull(tab - 1)?.second.orEmpty()
    }
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
                    val y = ((sy - cy) * scale).toFloat() + if (base.y < 0) -seamH / 2 else seamH / 2
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
                            WallFrame(inkColor) { if (!dragging) expanded = null }
                        } else {
                            val enter = onEnterTile?.takeIf { face.kind == HomeTileKind.PLAYLIST || face.kind == HomeTileKind.SHEET || face.kind == HomeTileKind.STATION }
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
                            ) {
                                if (dragging) return@Poster
                                if (isOpen) (enter ?: onOpenTile)(face) else expand(base)
                            }
                        }
                    }
                }
            }
            val seamTop = (-cy * scale).toFloat() - seamH / 2
            Box(
                Modifier
                    .offset { IntOffset(0, (seamTop * density.density).roundToInt()) }
                    .fillMaxWidth()
                    .height(seamH.dp)
                    .zIndex(10f)
                    .background(groundColor),
            ) {
                if (collection != null) {
                    SeamCollectionRow(collection, title, inkColor)
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
                    .background(Brush.verticalGradient(listOf(groundColor.copy(alpha = 0.85f), groundColor.copy(alpha = 0.5f)))),
            )
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
private const val SEAM_COLLECTION_HEIGHT = 148f

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
)

/**
 * 集合信息条（BravaisSeamCrumbs + BravaisSeamTitle + BravaisSeamCollection 元数据行与高频按钮）：
 * 第一行面包屑「‹ 广乐 / 类型」，点根返回；第二行集合名 22/800；第三行「N 首 · 类型」或加载中 / 失败（重试），右侧「播放全部」实心胶囊 +「随机」。
 * 未移：收藏星标、过滤输入、列表面板、「⋯ 更多」、补页进度、每日推荐日期步进。
 */
@Composable
private fun SeamCollectionRow(c: SeamCollection, root: String, ink: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = c.onBack).padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GuangYueIcons.ChevronLeft, contentDescription = "返回", tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                Text(root, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Text(" / ", color = ink.copy(alpha = 0.3f), fontSize = 13.sp)
            Text(c.kindLabel, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.name, color = ink, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = when {
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
            val ready = !c.loading && !c.failed && (c.count ?: 0) > 0
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (ready) ink else ink.copy(alpha = 0.2f))
                    .clickable(enabled = ready, onClick = c.onPlayAll)
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
                    .clickable(enabled = ready, onClick = c.onShuffle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GuangYueIcons.Shuffle, contentDescription = "随机播放", tint = ink.copy(alpha = if (ready) 0.8f else 0.3f), modifier = Modifier.size(17.dp))
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
            if (tile.artwork != null) {
                AsyncImage(
                    model = tile.artwork,
                    contentDescription = tile.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (open) FocusCopy(tile, onPlay, onEnqueue, onEnter, current, playing, onToggle, onEnterPlayback, inQueue)
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

