package com.guangyue.feature.player.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.player.lyrics.CoverImage
import com.guangyue.feature.player.lyrics.formatTime

/**
 * U4 · 全屏播放页（Folia 播放页的手机形态）。
 *
 * 形态对齐 `docs/port/baseline/screenshots/player-panel-cover-m412-light.png`：
 *  全屏「封面取色的柔光渐变」底 → 左上返回圆钮 → 衬线斜体歌手 / 竖线 / 大字标题 / 副标题
 *  → 底部一张圆角玻璃卡片（大封面 → 标题歌手 → 收藏 / 加入歌单 / 歌词 / 队列 → 两行按钮
 *  → 进度条+时间）→ 卡片下方一条悬浮进度胶囊。
 *
 * 与 Folia 的差异（全部记在 report §3）：
 *  - Folia 桌面把「Cover / Lyrics / Controls / Playlist / Quality」做成 5 段 tab；手机只保留
 *    「队列」一个抽屉（其余是桌面专属：Controls 是音量与音效、Quality 已并成进度胶囊右侧的
 *    音质键、Lyrics 见 U5），所以底部是**一颗队列入口 + 一块可下滑收起的队列卡片**。
 *  - Folia 传输键是 44dp；手机触控目标要求 ≥48dp，统一放到 48dp。
 *
 * 手势：整体下滑（跟手位移 → 松手超过 30% 屏高回弹关闭，否则弹回）+ 系统返回键。
 * 转场：进入时 `graphicsLayer` 从迷你条尺寸（[entryScale]）与底部往上 spring 放大到全屏，
 * 参数取 `FoliaMotion.layoutSpring()`（Folia `FloatingPlayerControls.tsx:38` 的 280/28）。
 */
@Composable
fun FoliaPlayerPage(
    playback: PlaybackState,
    res: PlayerStrings,
    darkMode: Boolean,
    mode: PlayerMode,
    like: LikeState,
    queue: List<PlayerQueueRow>,
    coverColor: Color?,
    accent: Color,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    /** 进度条拖动中生效（松手才 seek），用于拖动时把时间改成跟手位置。 */
    onSeekPreview: ((Float) -> Unit)? = null,
    onCycleMode: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenLyrics: () -> Unit,
    /**
     * V0锛氭挱鏀鹃〉銆屾瓕璇嶆牱寮忋€嶅叆鍙ｏ紙鎵撳紑搴曢儴寮瑰眰锛夈€?
     *
     * 对应 Folia 的样式窗口入口：Folia 把它挂在播放器设置区；手机上收进动作行第五颗按钮。
     * 涓?null 鏃朵笉娓叉煋锛堜緥濡傛埅鍥剧敤渚嬨€佹垨涓嶆敮鎸佹牱寮忕殑瀹夸富锛夈€?
     */
    onOpenLyricStyle: (() -> Unit)? = null,
    /** 「歌词样式」按钮文案（带无障碍语义）；feature:player 无资源表，由 app 侧注入。 */
    lyricStyleLabel: String = "",
    onPickQueueRow: (Int) -> Unit,
    onQuality: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 进入转场：迷你条中心在全屏页里的相对位置（0..1），由外壳算好传进来。 */
    entryOrigin: Pair<Float, Float> = 0.5f to 1f,
    /** 队列抽屉初始状态；生产恒为 false，截图用例用它一次拍出抽屉展开态。 */
    initialQueueOpen: Boolean = false,
    /** 已播放完进入动画后由外壳置 true（测试里可直接给 true 出静态图）。 */
    settled: Boolean = true,
) {
    val backdrop = remember(coverColor, accent, darkMode) {
        playerBackdrop(coverColor = coverColor, seed = accent, dark = darkMode)
    }
    // 渐变底上不再套主题色，文字与控件统一用「当前渐变下的对比色」两档。
    val onBackdrop = if (backdropUsesDarkInk(backdrop)) Color(0xFF1C1917) else Color(0xFFF7F5F2)
    val onBackdropSoft = onBackdrop.copy(alpha = 0.62f)
    val cardInk = Gyp.ink
    val cardInkSoft = Gyp.t2

    val collapseLabel = str(res.collapse)
    val lyricsLabel = str(res.lyrics)
    val queueLabel = str(res.queue)
    // V0：样式按钮文案先在组合上下文里算好（`ifEmpty` 的 lambda 不是 @Composable 上下文）。
    val lyricStyleActionLabel = if (lyricStyleLabel.isBlank()) lyricsLabel else lyricStyleLabel
    val header = playerHeaderText(
        title = playback.item?.title,
        artist = playback.item?.artist,
        album = playback.item?.album,
        qualityLabel = playback.item?.origin?.quality?.label,
        queuePosition = queuePositionText(playback.index, playback.queueSize),
        unknownArtist = str(res.unknownArtist),
        noSong = str(res.noSong),
    )

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val dismissThreshold = screenH * 0.30f
    val dragPx = with(LocalDensity.current) { dragOffset }
    val dismissThresholdPx = with(LocalDensity.current) { dismissThreshold.toPx() }

    // 悬浮进度胶囊：Folia `player-playing-m412-light.png` 里底部那条 2dp 细轨 + 两端时间。
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val shownProgress = if (scrubbing) scrubValue else playback.progress
    val shownPositionMs =
        if (scrubbing) (scrubValue * playback.durationMs).toLong() else playback.positionMs

    // 闃熷垪鎶藉眽 / 姝岃瘝鍏ュ彛
    var queueOpen by remember { mutableStateOf(initialQueueOpen) }

    val enterScale by animateFloatAsState(
        targetValue = if (settled) 1f else entryScale,
        animationSpec = FoliaMotion.layoutSpring(),
        label = "playerEnterScale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = FoliaMotion.layoutSpring(),
        label = "playerEnterAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(backdrop.colors),
            )
            // 跟手下拉关闭：位移实时跟手，松手按里程度决定关闭或回弹。
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > dismissThresholdPx) {
                            onBack()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { _, amount -> dragOffset = (dragOffset + amount).coerceAtLeast(0f) }
            }
            .graphicsLayer {
                translationY = dragPx
                val scale = enterScale * (1f - 0.04f * (dragPx / size.height).coerceIn(0f, 1f))
                scaleX = scale
                scaleY = scale
                alpha = enterAlpha * (1f - 0.25f * (dragPx / size.height).coerceIn(0f, 1f))
                transformOrigin = TransformOrigin(entryOrigin.first, entryOrigin.second)
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 状态栏 / 刘海 / 手势条都不许压控件：顶部吃 statusBars+cutout，底部吃 navigationBars。
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.navigationBars),
                )
                .padding(horizontal = GySpace.pageH),
        ) {
            // ---------- 顶栏：返回圆钮 + 微标题 + 歌词入口（U5 占位） ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(onBackdrop.copy(alpha = 0.12f))
                        .clickableNoRipple(onBack)
                        .semantics { contentDescription = collapseLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.ChevronDown,
                        null,
                        tint = onBackdrop,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = str(res.nowPlaying),
                    fontSize = GyText.hint,
                    letterSpacing = 2.sp,
                    color = onBackdropSoft,
                    fontFamily = GuangYueDesign.Sans,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickableNoRipple(onOpenLyrics)
                        .semantics { contentDescription = lyricsLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.Captions,
                        null,
                        tint = onBackdrop,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ---------- 标题区：斜体歌手 / 竖线 / 两行大标题 / 副标题 ----------
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = header.artist,
                    fontSize = GyText.label,
                    lineHeight = GyText.labelHeight,
                    fontStyle = FontStyle.Italic,
                    fontFamily = GuangYueDesign.Serif,
                    color = onBackdropSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(GySpace.xs))
                Row {
                    // Folia 标题左侧那根 1dp 竖线（`player-panel-cover-m412-light.png`）
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(56.dp)
                            .background(onBackdrop.copy(alpha = 0.28f)),
                    )
                    Spacer(Modifier.width(GySpace.l))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = header.title,
                            fontSize = PLAYER_TITLE_SP,
                            lineHeight = PLAYER_TITLE_LINE_SP,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GuangYueDesign.Serif,
                            color = onBackdrop,
                            maxLines = header.titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = header.subtitle ?: str(res.noSong),
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = onBackdropSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(GySpace.l))

            // ---------- 搴曢儴鍗＄墖 ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Bottom,
            ) {
                PlayerCard(
                    playback = playback,
                    res = res,
                    mode = mode,
                    like = like,
                    header = header,
                    cardColor = if (darkMode) Color(0xFF232327) else Color(0xFFFFFFFF),
                    cardInk = cardInk,
                    cardInkSoft = cardInkSoft,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleLike = onToggleLike,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenLyrics = onOpenLyrics,
                    onOpenLyricStyle = onOpenLyricStyle,
                    lyricStyleLabel = lyricStyleActionLabel,
                    onCycleMode = onCycleMode,
                    onOpenQueue = { queueOpen = !queueOpen },
                    queueOpen = queueOpen,
                )
                Spacer(Modifier.height(GySpace.s))

                // 闃熷垪鎶藉眽锛團olia 鐨?Playlist tab 鎵嬫満鍖栵級
                AnimatedVisibility(
                    visible = queueOpen,
                    enter = slideInVertically(animationSpec = FoliaMotion.move()) { it / 3 } +
                        fadeIn(animationSpec = FoliaMotion.fast()),
                    exit = slideOutVertically(animationSpec = FoliaMotion.move()) { it / 3 } +
                        fadeOut(animationSpec = FoliaMotion.fast()),
                ) {
                    PlayerQueueCard(
                        rows = queue,
                        res = res,
                        cardColor = if (darkMode) Color(0xFF232327) else Color(0xFFFFFFFF),
                        ink = cardInk,
                        inkSoft = cardInkSoft,
                        onPick = onPickQueueRow,
                        onClose = { queueOpen = false },
                    )
                }

                Spacer(Modifier.height(GySpace.s))

                // ---------- 悬浮进度胶囊（Folia player-playing 底部那条） ----------
                PlaybackPill(
                    progress = shownProgress,
                    isPlaying = playback.isPlaying,
                    positionMs = shownPositionMs,
                    durationMs = playback.durationMs,
                    onScrubStart = {
                        scrubbing = true
                        scrubValue = playback.progress
                        onSeekPreview?.invoke(playback.progress)
                    },
                    onScrub = {
                        scrubValue = it
                        onSeekPreview?.invoke(it)
                    },
                    onScrubEnd = {
                        scrubbing = false
                        onSeek(it)
                    },
                    res = res,
                    backdrop = backdrop,
                    darkMode = darkMode,
                    onQuality = onQuality,
                    qualityLabel = playback.item?.origin?.quality?.label ?: Quality.STANDARD.label,
                )
                Spacer(Modifier.height(GySpace.m))
            }
        }

        // 播放失败提示：贴在卡片上方，仍然只给「换音源」一条出路（沿用 0.0.7 的结论）。
        val failure = playback.error
        if (!failure.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 64.dp, start = GySpace.pageH, end = GySpace.pageH),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.row))
                        .background(Gyp.danger.copy(alpha = 0.16f))
                        .padding(horizontal = GySpace.m, vertical = GySpace.s),
                ) {
                    Text(
                        text = failure,
                        fontSize = GyText.hint,
                        color = Gyp.danger,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = str(res.retryHint),
                        fontSize = GyText.hint,
                        color = onBackdropSoft,
                    )
                }
            }
        }
    }
}

/** 转场起点缩放：迷你条 56dp 高在 800dp 屏上约 0.07，这里取一个看得见的 0.86（外壳可覆盖）。 */
private const val entryScale: Float = 0.86f

/** 标题字号：Folia 桌面大标题 34sp；360dp 宽的手机放 24sp（两行放得下 20 个汉字）。 */
private val PLAYER_TITLE_SP = 24.sp
private val PLAYER_TITLE_LINE_SP = 32.sp

/** 封面圆角：Folia 卡片内封面 `rounded-[14%]` 在 320dp 宽下 ≈ 28dp，手机取 20dp。 */
private val COVER_CORNER = 20.dp

@Composable
private fun PlayerCard(
    playback: PlaybackState,
    res: PlayerStrings,
    mode: PlayerMode,
    like: LikeState,
    header: PlayerHeaderText,
    cardColor: Color,
    cardInk: Color,
    cardInkSoft: Color,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenLyrics: () -> Unit,
    /** V0：歌词样式入口；null 时该按钮不渲染。 */
    onOpenLyricStyle: (() -> Unit)?,
    /** V0：歌词样式按钮文案（app 侧注入）。 */
    lyricStyleLabel: String,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    queueOpen: Boolean,
) {
    val addQueueName = str(res.queue)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(CARD_CORNER),
                ambientColor = Color.Black.copy(alpha = 0.24f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(cardColor)
            .padding(GySpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 大封面：圆角 + 内嵌阴影（Folia 的封面是卡片内的一张纸片）
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(10.dp, RoundedCornerShape(COVER_CORNER))
                .clip(RoundedCornerShape(COVER_CORNER))
                .background(Gyp.tint),
        ) {
            CoverImage(
                url = playback.item?.artworkUri,
                modifier = Modifier.fillMaxSize(),
                accent = FALLBACK_SEED,
                contentDescription = playback.item?.title,
            )
            // 鏃犲皝闈㈠厹搴曪細搴曢儴鍘嬩竴灞傚悓鑹叉笎鍙橈紝鎶娿€屾病鏈変换浣曞浘銆嶅彉鎴愩€屾湁璐ㄦ劅鐨勮壊鍧椼€嶃€?
            if (playback.item?.artworkUri.isNullOrBlank()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, FALLBACK_SEED.copy(alpha = 0.28f)),
                            ),
                        ),
                )
            }
        }

        Spacer(Modifier.height(GySpace.l))

        Text(
            text = header.title,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = cardInk,
            fontFamily = GuangYueDesign.Sans,
            maxLines = header.titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = header.artist,
            fontSize = GyText.label,
            lineHeight = GyText.labelHeight,
            color = cardInkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (header.subtitle != null) {
            Text(
                text = header.subtitle,
                fontSize = GyText.hint,
                lineHeight = GyText.hintHeight,
                color = cardInkSoft.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(GySpace.s))

        // 鍔ㄤ綔琛岋細鏀惰棌 / 鍔犲叆姝屽崟 / 闃熷垪 / 姝岃瘝
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GySpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 审图第 6 条：动作行原本只有 4 颗键（收藏/加歌单/队列/歌词），这行在 320dp 上会换行；
            // 第 5 颗「歌词样式」进来后必须能横着放下，所以在这里用 weight 均分而不是固定间距。
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GySpace.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardAction(
                    icon = GuangYueIcons.Heart,
                    active = like == LikeState.Liked,
                    label = if (likeActionIsUnlike(like)) str(res.liked) else str(res.like),
                    ink = cardInk,
                    idle = cardInkSoft,
                    fill = Gyp.tint,
                    onClick = onToggleLike,
                )
                CardAction(
                    icon = GuangYueIcons.ListPlus,
                    active = false,
                    label = str(res.addToPlaylist),
                    ink = cardInk,
                    idle = cardInkSoft,
                    fill = Gyp.tint,
                    onClick = onAddToPlaylist,
                )
                CardAction(
                    icon = GuangYueIcons.ListMusic,
                    active = queueOpen,
                    label = str(res.queue),
                    ink = cardInk,
                    idle = cardInkSoft,
                    fill = Gyp.tint,
                    onClick = onOpenQueue,
                )
                CardAction(
                    icon = GuangYueIcons.Captions,
                    active = false,
                    label = str(res.lyrics),
                    ink = cardInk,
                    idle = cardInkSoft,
                    fill = Gyp.tint,
                    onClick = onOpenLyrics,
                )
                // V0锛氭瓕璇嶆牱寮忓叆鍙ｏ紙Folia 鐨勬牱寮忕獥鍙ｅ湪鎵嬫満涓婃敹鎴愪竴棰楁寜閽?+ 搴曢儴寮瑰眰锛夈€?
                if (onOpenLyricStyle != null) {
                    CardAction(
                        icon = GuangYueIcons.Sparkles,
                        active = false,
                        label = lyricStyleLabel,
                        ink = cardInk,
                        idle = cardInkSoft,
                        fill = Gyp.tint,
                        onClick = onOpenLyricStyle,
                    )
                }
            }
            // U5锛氬師鍏堣繖閲屾寕鐫€銆屾瓕璇嶅姛鑳藉嵆灏嗕笂绾裤€嶇殑灏忓瓧鍗犱綅锛涙瓕璇嶉〉钀藉湴鍚庢挙鎺夛紝
            // 入口只剩上面那颗「歌词」按钮，右侧留白顶住动作行。
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(GySpace.s))

        // 传输键：48dp 触控目标（需求 §4），主键 64dp
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = when (mode) {
                    PlayerMode.Sequential -> GuangYueIcons.Repeat
                    PlayerMode.LoopOne -> GuangYueIcons.Repeat1
                    PlayerMode.Shuffle -> GuangYueIcons.Shuffle
                },
                active = mode != PlayerMode.Sequential,
                label = when (mode) {
                    PlayerMode.Sequential -> str(res.modeSequential)
                    PlayerMode.LoopOne -> str(res.modeLoopOne)
                    PlayerMode.Shuffle -> str(res.modeShuffle)
                },
                ink = cardInk,
                idle = cardInkSoft,
                onClick = onCycleMode,
            )
            TransportButton(
                icon = GuangYueIcons.ChevronLeft,
                active = true,
                label = str(res.previous),
                ink = cardInk,
                idle = cardInkSoft,
                onClick = onPrevious,
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(cardInk)
                    .clickableNoRipple(onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) GuangYueIcons.Pause else GuangYueIcons.Play,
                    contentDescription = if (playback.isPlaying) str(res.pause) else str(res.play),
                    tint = if (playback.isPlaying) Color(0xFFF7F5F2) else Color(0xFFF7F5F2),
                    modifier = Modifier.size(26.dp),
                )
            }
            TransportButton(
                icon = GuangYueIcons.ChevronRight,
                active = true,
                label = str(res.next),
                ink = cardInk,
                idle = cardInkSoft,
                onClick = onNext,
            )
            // 闃熷垪锛欶olia 妗岄潰鍦ㄥ彸涓婁笅鎷夐噷锛屾墜鏈虹粰涓€棰楀父椹婚敭
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onOpenQueue)
                    .semantics { contentDescription = addQueueName },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GuangYueIcons.ListMusic,
                    null,
                    tint = if (queueOpen) cardInk else cardInkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val CARD_CORNER = 20.dp

/** 卡片里的圆形/胶囊动作键：48dp 触控，图标 20dp。 */
@Composable
private fun CardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    label: String,
    ink: Color,
    idle: Color,
    fill: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Gyp.accent.copy(alpha = 0.16f) else fill, CircleShape)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Gyp.accent else idle,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 传输键：48dp 圆（需求要求 ≥48dp；Folia 桌面是 44dp，见 report §3）。 */
@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    label: String,
    ink: Color,
    idle: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickableNoRipple(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) ink else idle,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 队列抽屉：Folia 的 Playlist tab（当前行高亮 + 左侧竖条）。 */
@Composable
private fun PlayerQueueCard(
    rows: List<PlayerQueueRow>,
    res: PlayerStrings,
    cardColor: Color,
    ink: Color,
    inkSoft: Color,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val closeQueueName = str(res.closeQueue)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(cardColor)
            .padding(vertical = GySpace.s, horizontal = GySpace.m),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (rows.isEmpty()) str(res.queueEmpty) else str(res.queueCount).format(rows.size),
                fontSize = GyText.label,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clickableNoRipple(onClose)
                    .semantics { contentDescription = closeQueueName },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GuangYueIcons.ChevronDown,
                    null,
                    tint = inkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        val visible = rows.take(QUEUE_VISIBLE)
        visible.forEachIndexed { i, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(GyRadius.field))
                    .background(if (row.current) ink.copy(alpha = 0.06f) else Color.Transparent)
                    .clickable { onPick(row.index) }
                    .padding(horizontal = GySpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(CircleShape)
                        .background(if (row.current) Gyp.accent else Color.Transparent),
                )
                Spacer(Modifier.width(GySpace.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        fontSize = GyText.label,
                        color = if (row.current) ink else ink.copy(alpha = 0.86f),
                        fontWeight = if (row.current) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.subtitle.isNotBlank()) {
                        Text(
                            text = row.subtitle,
                            fontSize = GyText.hint,
                            color = inkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (rows.size > QUEUE_VISIBLE) {
            Text(
                text = "+${rows.size - QUEUE_VISIBLE}",
                fontSize = GyText.hint,
                color = inkSoft,
                modifier = Modifier.padding(start = GySpace.s, top = GySpace.xxs),
            )
        }
    }
}

/** 队列抽屉在卡片里只露 4 行（360×800 下与 Folia 基准图一致），其余折成「+N」。 */
private const val QUEUE_VISIBLE = 4

/**
 * 底部悬浮进度胶囊：Folia `player-playing-m412-light.png` 那条 = 左时间 / 2dp 细轨 / 右时间，
 * 右侧再挂一颗音质键（Folia 桌面是 Quality tab，手机压成一颗按钮）。
 *
 * 轨道本体用 Slider 实现以获取拖动 + 无障碍语义，视觉压到 2dp 细线（拇指透明、整条可拖）。
 */
@Composable
private fun PlaybackPill(
    progress: Float,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    res: PlayerStrings,
    backdrop: PlayerBackdrop,
    darkMode: Boolean,
    onQuality: () -> Unit,
    qualityLabel: String,
) {
    val seekBarLabel = str(res.seekBar)
    val qualityName = str(res.quality) + " " + qualityLabel
    val pill = if (darkMode) Color(0xFF17171A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.62f)
    // 按胶囊自身的底色（不是整屏背景）决定文字明暗：白胶囊上用深字，深胶囊上用浅字。
    val pillInk = if (darkMode) Color(0xFFF4F4F5) else Color(0xFF1C1917)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(GyRadius.capsule))
                .background(pill)
                .padding(horizontal = GySpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(positionMs),
                fontSize = 11.sp,
                color = pillInk.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(GySpace.s))
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { if (it != progress) onScrub(it) },
                    onValueChangeFinished = { onScrubEnd(progress) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = pillInk.copy(alpha = 0.85f),
                        inactiveTrackColor = pillInk.copy(alpha = 0.22f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .semantics { contentDescription = seekBarLabel },
                )
            }
            Spacer(Modifier.width(GySpace.s))
            Text(
                text = formatTime(durationMs),
                fontSize = 11.sp,
                color = pillInk.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(GySpace.s))
        // 音质键：Folia 的 Quality tab 压成一颗圆钮
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(pill)
                .clickableNoRipple(onQuality)
                .semantics { contentDescription = qualityName },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GuangYueIcons.AudioLines,
                null,
                tint = pillInk,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 队列抽屉的一行（纯数据，测试里直接构造）。 */
data class PlayerQueueRow(
    val index: Int,
    val title: String,
    val subtitle: String,
    val current: Boolean,
)

/** 极简 str()：文案包给的是资源 id，UI 层由外壳用 [PlayerStringHost] 解析成字符串。 */
@Composable
private fun str(id: Int): String = PlayerStringHost.current(id)

/** 文案解析器：测试与外壳各提供一份（外壳走 `stringResource`，测试用中文常量表）。 */
val PlayerStringHost = androidx.compose.runtime.staticCompositionLocalOf<(Int) -> String> {
    { "" }
}
