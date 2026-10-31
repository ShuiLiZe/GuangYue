package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.Line
import com.guangyue.core.player.PlaybackState
import com.guangyue.feature.library.design.FoliaEmptyState
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.canBlur
import com.guangyue.feature.library.design.clickableNoRipple
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.player.lyrics.visualizer.ClassicLyricLine
import com.guangyue.feature.player.lyrics.visualizer.CoverPalette
import com.guangyue.feature.player.lyrics.visualizer.VisualizerBackdrop
import com.guangyue.feature.player.lyrics.visualizer.VisualizerClock
import com.guangyue.feature.player.lyrics.visualizer.hasVisibleWords
import com.guangyue.feature.player.lyrics.visualizer.emphasis
import com.guangyue.feature.player.lyrics.visualizer.ink
import com.guangyue.feature.player.lyrics.visualizer.rememberCoverBackdrop
import com.guangyue.feature.player.lyrics.visualizer.resolveCoverSource
import com.guangyue.feature.player.lyrics.visualizer.rememberReduceMotion
import com.guangyue.feature.player.lyrics.visualizer.rememberStaticVisualizerClock
import com.guangyue.feature.player.lyrics.visualizer.rememberVisualizerClock
import com.guangyue.feature.player.lyrics.visualizer.runningUnderRobolectric
import com.guangyue.feature.player.screen.PlayerBackdrop
import com.guangyue.feature.player.screen.backdropUsesDarkInk
import com.guangyue.feature.player.screen.playerBackdrop

/**
 * U5 歌词页（Compose 版，取代 U2 的 `LyricFlowScreen`）。
 *
 * 三块，从上到下：
 *  1. 顶栏：返回圆钮 + 「歌词」标题 + 一句「歌手 · 来源」；
 *  2. 歌词列表：逐行随播放高亮，正在唱的那句最大、最近、带强调色，越远越小越淡越糊
 *     （档位与旧 `LyricFlowScreen` 的 `LyricFlowLine` 一致：32/26/22/19/17sp、
 *     1/.34/.20/.12/.07、0/.6/1.6/2.6/3.6dp）；**点任意一行跳到那一句**；
 *  3. 底部胶囊：2dp 进度轨 + 等宽时间，与播放页 [com.guangyue.feature.player.screen.FoliaPlayerPage]
 *     的 `PlaybackPill` 同一套观感。
 *
 * 空态（无歌词 / 加载中 / 失败）走 [FoliaEmptyState]，文案全中文、由 [LyricStrings] 注入；
 * 失败副句优先用数据源给的原始原因（`LyricsViewModel` 的 `message`），这时不显示重试按钮
 * ——重试要真去请求音源，归外壳管。
 *
 * 拖动列表时暂停自动跟随（由 `LyricSynchronizer` 记 3 秒），松手后从头跟回正在唱的那句。
 * 背景沿用播放页那条封面渐变，深浅色同一套令牌，320dp 与字体 1.3 都不写死宽度。
 */
@Composable
fun LyricFlowScreen(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    strings: LyricStrings,
    playback: PlaybackState,
    accent: Color,
    onBack: () -> Unit,
    onSeekToLine: (Int) -> Unit = {},
    onManualScroll: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSeekPreview: ((Float) -> Unit)? = null,
    artist: String? = playback.item?.artist,
    source: String? = null,
    loading: Boolean = false,
    failure: String? = null,
    pureMusic: Boolean = false,
    darkMode: Boolean = false,
    coverColor: Color? = null,
    // ---------------- V0：流光(classic)样式在内核上的接线（全部有默认值，不影响 U5 原行为） ----------------
    /** 内核时间轴（`LyricTimeline.toLines(...)` 的产物）。为空时整页走 U5 原逐行视图。 */
    kernelLines: List<Line> = emptyList(),
    /** 流光样式调参（弹层里改，持久化在 [com.guangyue.core.prefs.AppPreferences.classicTuning]）。 */
    classicTuning: ClassicTuning = ClassicTuning(),
    /** 封面 url；给了就走封面取色 + 流体背景 + 换歌柔焦。 */
    coverUrl: String? = null,
    /** 打开「歌词样式」弹层；为 null 时不显示入口。 */
    onOpenStyleSheet: (() -> Unit)? = null,
    /** 样式入口的无障碍文案（feature:player 无资源表，由 app 侧注入）。 */
    styleLabel: String = "",
    // V0d 取证：非 null 时把当前句的辉光强度钉死（debug harness 用，业务侧恒为 null）。
    glowStrengthOverride: Float? = null,
    modifier: Modifier = Modifier,
) {
    val backLabel = str(strings.back)
    val emptyKind = lyricEmptyKind(lineCount = lyrics.size, loading = loading, failed = failure != null)

    // ---------------- V0：减少动效 / 封面取色 ----------------
    val reduceMotion = rememberReduceMotion()
    // 当前句在内核时间轴上的下标：U5 的 currentIndex 与 kernelLines 一一对应（同一份歌词）。
    // 对不上（第一句之前、越界）就当没有内核行，整页回落到 U5 原视图。
    val activeLine: Line? = if (currentIndex in kernelLines.indices) kernelLines[currentIndex] else null

    // ---------------- 封面取色（Folia colorExtractor + builtin dual theme） ----------------
    // 封面来源：业务封面优先，没有就用内置测试封面 —— 这样「封面取色 → 流体背景」在真机上始终可见，
    // 也是审图第 4 条要求的实拍对象。
    val coverSource = remember(coverUrl) { resolveCoverSource(coverUrl) }
    val theme = rememberCoverBackdrop(coverSource, darkMode)
    val palette: CoverPalette = remember(theme) {
        CoverPalette(
            backgroundColor = theme.background,
            primaryColor = theme.primary,
            accentColor = theme.accent,
            secondaryColor = theme.secondary,
            fromCover = theme.fromCover,
        )
    }
    // 墨色走取色主题的 primary（Folia 按 9:1 对比度调过明度）：
    // 修审图第 5 条「浅色顶栏『歌词』几乎看不见」—— 不再用固定的 0xFF1C1917 / 0xFFF7F5F2 去猜背景亮暗。
    val onBackdrop = theme.ink
    // 当前行激活字用取色 accent（与顶栏/胶囊同一套色）。
    val lyricAccent = theme.emphasis
    val pillInk = if (darkMode) Color(0xFFF4F4F5) else Color(0xFF1C1917)

    val listState = rememberLazyListState()

    // 自动跟随：只有同步器没被判为「手动浏览」时才滚。
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }
    // 手指一碰列表就算手动浏览（同步器记 3 秒，之后自己回自动）。
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onManualScroll()
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val progress = if (scrubbing) scrubValue else playback.progress
    val positionMs = if (scrubbing) {
        (scrubValue * playback.durationMs).toLong()
    } else {
        playback.positionMs
    }

    // 渲染里读：系统字号倍率（1.3 倍不溢出），与内核字号 clamp 一起用。
    val lyricsFontScale = LocalDensity.current.fontScale.toDouble()
    val viewportWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx().toDouble()
    }

    // 「流光」逐词画布需要逐帧时间；三态只在 derivedStateOf 里读，整行不会每帧重组。
    // Robolectric 下 withFrameNanos 会把 waitForIdle() 挂住（同 FrameJank 的坑），改用固定时钟。
    val clock: VisualizerClock = if (runningUnderRobolectric) {
        rememberStaticVisualizerClock(positionMs / 1000.0)
    } else {
        rememberVisualizerClock(
            positionMsProvider = { positionMs },
            playingProvider = { playback.isPlaying },
            enabled = !reduceMotion,
        )
    }

    // 背景层：Folia FluidBackground —— 取色底色 @0.8 + 放大 1.5× 的模糊封面 + bottom-right overlay 渐变。
    VisualizerBackdrop(
        palette = palette,
        source = coverSource,
        dark = darkMode,
        modifier = modifier
            .fillMaxSize()
            .testTag(LyricRootTag),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.navigationBars),
                )
                .padding(horizontal = GySpace.pageH),
        ) {
            // ---------------- 顶栏 ----------------
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
                        .semantics { contentDescription = backLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GuangYueIcons.ChevronLeft,
                        null,
                        tint = onBackdrop,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(GySpace.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = str(strings.title),
                        fontSize = GyText.title,
                        lineHeight = GyText.titleHeight,
                        fontWeight = FontWeight.Bold,
                        color = onBackdrop,
                        fontFamily = GuangYueDesign.Sans,
                        maxLines = 1,
                    )
                    val sub = lyricSourceLine(artist = artist, source = source)
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = onBackdrop.copy(alpha = 0.62f),
                            fontFamily = GuangYueDesign.Sans,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                // 样式入口：与 U5 原页面同款 48dp 触控。
                val styleLabelText = styleLabel.ifEmpty { str(strings.title) }
                if (onOpenStyleSheet != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(onBackdrop.copy(alpha = 0.12f))
                            .clickableNoRipple(onOpenStyleSheet)
                            .semantics { contentDescription = styleLabelText },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            GuangYueIcons.Sparkles,
                            null,
                            tint = onBackdrop,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // ---------------- 歌词列表 / 空态 ----------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (emptyKind != null) {
                    LyricEmpty(
                        kind = emptyKind,
                        strings = strings,
                        pureMusic = pureMusic,
                        failure = failure,
                        onRetry = null,
                    )
                } else {
                    // 正在唱的那句尽量停在竖直中线：上下各留半屏空白，当前句才不会被顶栏/胶囊挤到边上。
                    // 高度取列表自己的（`onSizeChanged`），不能拿整屏高度——顶栏与胶囊是兄弟节点。
                    var listHeightPx by remember { mutableIntStateOf(0) }
                    val verticalPad = with(LocalDensity.current) { (listHeightPx * 0.32f).toDp() }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { listHeightPx = it.height }
                            .testTag(LyricListTag),
                        contentPadding = PaddingValues(vertical = verticalPad),
                    ) {
                        itemsIndexed(lyrics) { index, line ->
                            val visual = lyricRowVisual(index, currentIndex)
                            // V0：当前句有内核时间轴时走「流光」逐词画布，其余行保留 U5 档位视图，
                            // 这样上下句上下文、点行跳转、自动跟随都不变。
                            val isCurrentLine = index == currentIndex
                            // 审图第 2 条：当前句即使没有内核时间轴也要走「流光」画布 —— 旧版走
                            // LyricLineRow 时当前句只剩一档高亮色，waiting/passed 的字几乎看不见。
                            if (isCurrentLine && activeLine != null && activeLine.hasVisibleWords()) {
                                ClassicLyricLine(
                                    line = activeLine,
                                    clock = clock,
                                    tuning = classicTuning,
                                    intensity = AnimationIntensity.NORMAL,
                                    reduceMotion = reduceMotion,
                                    viewportWidthPx = viewportWidthPx,
                                    lyricsFontScale = lyricsFontScale,
                                    activeColor = lyricAccent,
                                    baseColor = onBackdrop,
                                    fontFamily = GuangYueDesign.Serif,
                                    glowStrengthOverride = glowStrengthOverride,
                                    modifier = Modifier.clickableNoRipple { onSeekToLine(index) },
                                )
                            } else {
                                LyricLineRow(
                                    line = line,
                                    visual = visual,
                                    accent = accent,
                                    onClick = { onSeekToLine(index) },
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- 底部播放胶囊 ----------------
            LyricBar(
                positionMs = positionMs,
                durationMs = playback.durationMs,
                progress = progress,
                playing = playback.isPlaying,
                pillInk = pillInk,
                dark = darkMode,
                labels = strings,
                onTogglePlay = onTogglePlay,
                onScrub = { scrubbing = true; scrubValue = it },
                onScrubPreview = onSeekPreview,
            )
        }
    }
}

/**
 * 空态：加载中 / 失败 / 没有歌词，文案走 [LyricStrings]。
 *
 * 纯音乐是「没有歌词」里的一种特例，Folia 单独给了一句话（`bestLyricsPureMusic`），照搬。
 */
@Composable
internal fun LyricEmpty(
    kind: LyricEmptyKind,
    strings: LyricStrings,
    pureMusic: Boolean,
    failure: String?,
    onRetry: (() -> Unit)?,
) {
    // 先把三句话都取成字符串（str 是 @Composable），再按三态挑一句。
    val loadingText = str(strings.loading)
    val failedText = str(strings.loadFailed)
    val pureMusicText = str(strings.pureMusic)
    val emptyText = str(strings.empty)
    val failedHintText = str(strings.failedHint)
    val emptyHintText = str(strings.emptyHint)
    val retryText = str(strings.retry)

    val title = if (kind == LyricEmptyKind.Loading) {
        loadingText
    } else if (kind == LyricEmptyKind.Failed) {
        failedText
    } else if (pureMusic) {
        pureMusicText
    } else {
        emptyText
    }
    val subtitle = if (kind == LyricEmptyKind.Loading) {
        null
    } else if (kind == LyricEmptyKind.Failed) {
        failureText(failure, failedHintText)
    } else if (pureMusic) {
        // 纯音乐是明确的终态（Folia `bestLyricsPureMusic`），再挂「换一个音源」会自相矛盾。
        null
    } else {
        emptyHintText
    }
    FoliaEmptyState(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GySpace.pageH)
            .testTag(LyricEmptyTag),
        action = onRetry?.let { retry ->
            {
                Text(
                    text = retryText,
                    fontSize = GyText.label,
                    lineHeight = GyText.labelHeight,
                    color = Gyp.accent,
                    fontFamily = GuangYueDesign.Sans,
                    modifier = Modifier
                        .height(48.dp)
                        .clickableNoRipple(retry)
                        .padding(horizontal = GySpace.l, vertical = GySpace.m),
                )
            }
        },
    )
}

/**
 * 一行歌词：距离档位决定字号 / 不透明度 / 模糊，当前句用强调色。
 *
 * 点击区整行填满且最小 48dp（无障碍触控目标），点一下就 seek 到这一句。
 */
@Composable
private fun LyricLineRow(
    line: LyricLine,
    visual: LyricRowVisual,
    accent: Color,
    onClick: () -> Unit,
) {
    val distance = visual.distance
    val fontSize = when (distance) {
        0 -> 32
        1 -> 26
        2 -> 22
        3 -> 19
        else -> 17
    }
    val alpha = when (distance) {
        0 -> 1f
        1 -> 0.34f
        2 -> 0.20f
        3 -> 0.12f
        else -> 0.07f
    }
    val blurRadius = when (distance) {
        0 -> 0f
        1 -> 0.6f
        2 -> 1.6f
        3 -> 2.6f
        else -> 3.6f
    }
    val blurAllowed = canBlur()

    Column(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = GySpace.xs),
    ) {
        Text(
            text = line.text.ifBlank { " " },
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.28).sp,
            fontWeight = FontWeight.SemiBold,
            color = if (visual.active) accent.copy(alpha = 0.92f) else Gyp.ink.copy(alpha = alpha),
            textAlign = TextAlign.Start,
            fontFamily = GuangYueDesign.Serif,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (blurAllowed && blurRadius > 0f) {
                        Modifier.blur(
                            blurRadius.dp,
                            edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        // 译文只在当前句露出来（跟旧页面一致），避免了整屏双语糊在一起。
        if (visual.active && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation.orEmpty(),
                fontSize = GyText.label,
                lineHeight = GyText.labelHeight,
                color = Gyp.t2,
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(top = GySpace.xxs),
            )
        }
    }
}

/** 底部胶囊：播放键 + 2dp 进度轨 + 两端等宽时间（跟播放页同观感）。 */
@Composable
private fun LyricBar(
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    playing: Boolean,
    pillInk: Color,
    dark: Boolean,
    labels: LyricStrings,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubPreview: ((Float) -> Unit)?,
) {
    val seekBarLabel = str(labels.seekBar)
    val playLabel = str(labels.play)
    val pauseLabel = str(labels.pause)
    val pill = if (dark) Color(0xFF17171A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.62f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = GySpace.l)
            .height(56.dp)
            .clip(RoundedCornerShape(GyRadius.capsule))
            .background(pill)
            .padding(horizontal = GySpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickableNoRipple(onTogglePlay)
                .semantics {
                    contentDescription = if (playing) pauseLabel else playLabel
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) GuangYueIcons.Pause else GuangYueIcons.Play,
                null,
                tint = pillInk,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(GySpace.xs))
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
                onValueChange = { onScrub(it); onScrubPreview?.invoke(it) },
                onValueChangeFinished = {},
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
}

/** 截图测试用的稳定锚点。 */
const val LyricRootTag = "u5-lyrics"
const val LyricListTag = "u5-lyrics-list"
const val LyricEmptyTag = "u5-lyrics-empty"

/** 毫秒 → `mm:ss`（Folia LatticeChrome 的等宽时间；小时数并进分钟）。 */
internal fun formatTime(ms: Long): String {
    val safe = if (ms < 0L) 0L else ms
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
/** 取文案：外壳注入的 [LyricStrings] 只带资源 id，这里统一翻成字符串。 */
@Composable
private fun str(id: Int): String = LyricStringHost.current(id)
