package com.guangyue.music.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import kotlinx.coroutines.delay

/**
 * 11-17：Folia「思索」（Ponder）的手机版入口与本页教程。
 *
 * - 灯泡按钮照 Folia PagePonderTouchButton：贴右上角（right-5 top-5，48dp 圆，卡片底 + 1px 线框 + 阴影），
 *   落地 / 换页签时露 4 秒再自己收起；收起后点右上角 96dp 热区把它叫出来（这一下被吃掉，不会同时点到底下的搜索框），
 *   露着时再点才进教程。设置 → 外观 →「触屏思索按钮」可关。
 * - 教程：Folia 的 Ponder 是一块合成舞台 + 光标动画，搬到手机上太重；这里改成直接在真实首页上「挖洞高亮 + 说明卡」，
 *   章节、顺序、文案照 Folia grid-page 的 scenes 与 ponder.captions.pages.*，换成广乐手机版实有的控件
 *   （页签 = 歌单 / 电台 / 专辑 / 本地；没有桌面键盘那一章，换成「平台与播放」）。理由记 HANDOFF。
 */
data class PonderStep(val anchor: String, val text: String)

data class PonderChapter(val title: String, val steps: List<PonderStep>)

object GridPagePonder {
    const val TITLE = "海报墙页面"
    const val SUMMARY = "海报墙怎么组织，怎么移动、打开和搜索。"

    /** 露出来之后停留多久（Folia REVEAL_MS）。 */
    const val REVEAL_MS = 4000L

    /** 右上角热区边长（Folia HOTSPOT_SIZE_PX，按触摸目标给）。 */
    const val HOTSPOT_DP = 96f

    val ALL: List<PonderChapter> = listOf(
        PonderChapter(
            "页头的每个入口",
            listOf(
                PonderStep("help", "标题旁的齿轮打开设置；设置的帮助页里有版本更新说明和思索帮助页。"),
                PonderStep("tabs", "中间页签切换歌单、电台、专辑、本地。不常用的页签可以在设置 → 外观 →「顶部胶囊入口」里关掉。"),
                PonderStep("search", "右侧是跨当前音源的歌曲搜索。点开进入搜索页；它不会筛选下方的卡片。"),
            ),
        ),
        PonderChapter(
            "切换来源与专属操作",
            listOf(
                PonderStep("tabs", "切换页签后，下方卡片轨道换成该来源的集合，并记住各来源最后聚焦的位置。"),
                PonderStep("sourceActions", "这一组只在当前来源需要时出现，例如本地页签的导入文件夹、刷新和导入歌单。"),
            ),
        ),
        PonderChapter(
            "移动焦点并打开集合",
            listOf(
                PonderStep("shelf", "左右滑动移动轨道。点非中央的卡片只会把它移到中央，不会立刻打开。"),
                PonderStep("focusedCard", "再次点击中央卡片才进入集合或开始播放；下方文字和按钮始终描述当前中央卡片。"),
            ),
        ),
        PonderChapter(
            "用地图总览全部集合",
            listOf(PonderStep("map", "点「全部」打开地图，一屏看完当前页签的所有集合，点哪张就跳到哪张。")),
        ),
        PonderChapter(
            "平台与播放",
            listOf(
                PonderStep("provider", "右下角的平台按钮切换当前音源、登录在线平台账号；登录后歌单页签会列出账号里的歌单。"),
                PonderStep("playbar", "开始播放后底部出现悬浮播放条，点歌名进入播放页。"),
            ),
        ),
    )

    /** 只留当前屏上真有的控件那几步；一步都不剩的章节整章去掉（Folia 也只教当前页有的东西）。 */
    fun chapters(present: Set<String>): List<PonderChapter> =
        ALL.map { c -> c.copy(steps = c.steps.filter { it.anchor in present }) }.filter { it.steps.isNotEmpty() }

    /** 热区判定（Folia：clientY <= 96 && clientX >= innerWidth - 96）。 */
    fun inHotspot(x: Float, y: Float, width: Float, hotspotPx: Float): Boolean = y <= hotspotPx && x >= width - hotspotPx
}

/** 给控件挂一个锚点：记下它在根坐标里的位置；控件离开时删掉。 */
fun Modifier.ponderAnchor(key: String, anchors: SnapshotStateMap<String, Rect>?): Modifier =
    if (anchors == null) this else composed {
        DisposableEffect(key) { onDispose { anchors.remove(key) } }
        onGloballyPositioned { anchors[key] = it.boundsInRoot() }
    }

/** 首页根节点用：管灯泡的显隐（落地 / 换页签露 4 秒、右上角热区叫出来）。 */
class PonderButtonState {
    var revealed by mutableStateOf(true)
    var tick by mutableIntStateOf(0)
    fun reveal() { revealed = true; tick++ }
}

/**
 * 挂在首页根 Box 上：Initial 阶段看每次按下。按在右上角热区里且灯泡收着 → 叫出来并吃掉这一下
 * （Folia suppressNextClick：叫出来不等于按它，也不该点到底下的搜索框）；露着就放行给灯泡本身。
 */
fun Modifier.ponderHotspot(state: PonderButtonState?, topInsetPx: Float = 0f): Modifier = if (state == null) this else pointerInput(state, topInsetPx) {
    val spot = GridPagePonder.HOTSPOT_DP * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // 手机上状态栏占了顶上一截：热区从安全区顶边往下量。
        if (!GridPagePonder.inHotspot(down.position.x, down.position.y - topInsetPx, size.width.toFloat(), spot)) return@awaitEachGesture
        if (state.revealed) { state.reveal(); return@awaitEachGesture }
        state.reveal()
        down.consume()
        // 同一次触摸余下的事件也吃掉，免得抬手时落成一次点击。
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            ev.changes.forEach { it.consume() }
            if (ev.changes.all { !it.pressed }) break
        }
    }
}

@Composable
fun PagePonderTouchButton(
    state: PonderButtonState, revealKey: Any?, dark: Boolean, onOpen: () -> Unit,
    modifier: Modifier = Modifier, revealMs: Long = GridPagePonder.REVEAL_MS,
) {
    LaunchedEffect(revealKey) { state.reveal() }
    LaunchedEffect(state.tick) {
        delay(revealMs)
        state.revealed = false
    }
    AnimatedVisibility(
        visible = state.revealed,
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.94f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.94f),
        modifier = modifier,
    ) {
        val card = if (dark) Color(0xFF202023) else Color.White
        val ink = if (dark) Color.White else Color(0xFF111111)
        Box(
            Modifier
                .size(48.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(card)
                .border(1.dp, ink.copy(alpha = 0.10f), CircleShape)
                .clickable(onClick = onOpen)
                .semantics { contentDescription = "思索当前页面" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(GuangYueIcons.Lightbulb, null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 本页教程：整屏暗幕，当前这一步的控件处挖一个圆角洞 + 描边；说明卡放在洞的另一半屏。
 * 点暗幕或「下一步」往下走，顶部章节胶囊可直接跳章（Folia 思索导航的「挑一条」）。
 */
@Composable
fun PagePonderOverlay(
    anchors: Map<String, Rect>,
    dark: Boolean,
    onClose: () -> Unit,
    rootOffset: Offset = Offset.Zero,
    initialChapter: Int = 0,
) {
    val chapters = remember(anchors.keys.toSet()) { GridPagePonder.chapters(anchors.keys.toSet()) }
    var chapter by remember { mutableIntStateOf(initialChapter) }
    var step by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onClose)
    // 锚点要等首页排完一遍才有；没有就先不画（不自动关，免得刚开就没了）。
    if (chapters.isEmpty()) return
    if (chapter !in chapters.indices) { chapter = chapter.coerceIn(0, chapters.size - 1); step = 0 }
    val ch = chapters[chapter.coerceIn(0, chapters.size - 1)]
    if (step !in ch.steps.indices) step = step.coerceIn(0, ch.steps.size - 1)
    val st = ch.steps[step.coerceIn(0, ch.steps.size - 1)]
    val last = chapter == chapters.size - 1 && step == ch.steps.size - 1
    fun next() {
        when {
            step < ch.steps.size - 1 -> step++
            chapter < chapters.size - 1 -> { chapter++; step = 0 }
            else -> onClose()
        }
    }
    fun prev() {
        when {
            step > 0 -> step--
            chapter > 0 -> { chapter--; step = chapters[chapter].steps.size - 1 }
        }
    }
    val rect = anchors[st.anchor]?.translate(-rootOffset)
    val density = LocalDensity.current
    val pad = with(density) { 6.dp.toPx() }
    val radius = with(density) { 14.dp.toPx() }
    val ring = with(density) { 2.dp.toPx() }
    val glow by animateFloatAsState(1f, tween(420), label = "ponderGlow")
    val card = if (dark) Color(0xFF202023) else Color.White
    val ink = if (dark) Color.White else Color(0xFF111111)
    val soft = ink.copy(alpha = 0.55f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = with(density) { maxHeight.toPx() }
        Canvas(
            Modifier.fillMaxSize().clickable(indication = null, interactionSource = null) { next() },
        ) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (rect != null) {
                    addRoundRect(RoundRect(rect.inflate(pad), CornerRadius(radius)))
                }
            }
            drawPath(path, Color.Black.copy(alpha = 0.62f))
            if (rect != null) {
                val rr = RoundRect(rect.inflate(pad), CornerRadius(radius))
                drawPath(Path().apply { addRoundRect(rr) }, Color.White.copy(alpha = 0.85f * glow), style = Stroke(ring))
            }
        }
        // 顶部：标题 + 章节胶囊 + 关闭。
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GuangYueIcons.Lightbulb, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("思索 · ${GridPagePonder.TITLE}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = "关闭思索" },
                    contentAlignment = Alignment.Center,
                ) { Icon(GuangYueIcons.X, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chapters.forEachIndexed { i, c ->
                    val on = i == chapter
                    Box(
                        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(50))
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                            .clickable { chapter = i; step = 0 }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.title, color = if (on) Color.Black else Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        // 说明卡：控件在上半屏就放底部，否则放在章节胶囊下面。
        val atBottom = rect == null || rect.center.y < hPx * 0.5f
        Column(
            Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, end = 16.dp, top = if (atBottom) 0.dp else 104.dp, bottom = 16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .clickable(indication = null, interactionSource = null) {}
                .padding(18.dp),
        ) {
            Text("${chapter + 1} / ${chapters.size} · ${ch.title}", color = soft, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(st.text, color = ink, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ch.steps.indices.forEach { i ->
                        Box(Modifier.size(if (i == step) 8.dp else 6.dp).clip(CircleShape).background(ink.copy(alpha = if (i == step) 0.85f else 0.25f)))
                    }
                }
                val canPrev = chapter > 0 || step > 0
                if (canPrev) {
                    Box(
                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).clickable { prev() }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("上一步", color = soft, fontSize = 14.sp) }
                }
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(ink)
                        .clickable { next() }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (last) "完成" else "下一步", color = card, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
