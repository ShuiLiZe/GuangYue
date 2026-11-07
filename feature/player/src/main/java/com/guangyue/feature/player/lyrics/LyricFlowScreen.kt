package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicLayout
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.fume.FumeTuning
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
import com.guangyue.feature.player.lyrics.visualizer.fume.FumeBulletinStage
import com.guangyue.feature.player.lyrics.visualizer.fume.FumeStillFrame
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
 * 三块，从上到下（V0e 起换成 Folia classic 的「舞台单行」形态）：
 *  1. 顶栏：返回圆钮 + 「歌词」标题 + 一句「歌手 · 来源」；
 *  2. 舞台：**只画正在唱的那一句**（[ClassicLyricLine]，逐词流光 + flex-wrap）；
 *     没有当前句时画「等待音乐」占位。舞台高度由顶栏与字幕层之间的剩余空间决定，
 *     1.3 倍字号下换行也不会跑到播放条下面；
 *  3. 底部字幕层（[LyricSubtitleOverlay]）：当前句的翻译；这一句没有可读翻译时，
 *     改画后面几行的预告。字幕层贴在播放胶囊之上、导航 inset 之内，不被系统栏遮挡；
 *  4. 底部播放胶囊：2dp 进度轨 + 等宽时间，与播放页 [com.guangyue.feature.player.screen.FoliaPlayerPage]
 *     的 `PlaybackPill` 同一套观感。
 *
 * 空态（无歌词 / 加载中 / 失败）走 [FoliaEmptyState]，文案全中文、由 [LyricStrings] 注入；
 * 失败副句优先用数据源给的原始原因（`LyricsViewModel` 的 `message`），这时不显示重试按钮
 * ——重试要真去请求音源，归外壳管。
 *
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
    // V0d 取证：只画当前句舞台（debug harness 用）。
    lineOnlyStage: Boolean = false,
    // ---------------- V1a：样式切换（流光 / 浮名） ----------------
    /** 当前歌词样式；[LyricStyle.FUME] 时舞台改画「浮名」整篇文章 + 镜头。 */
    lyricStyle: LyricStyle = LyricStyle.CLASSIC,
    /** 「浮名」调参；V1b 才做面板，这里先吃默认值。 */
    fumeTuning: FumeTuning = FumeTuning.DEFAULT,
    /** 取证用：>0 时「浮名」镜头按固定 dt 一次推进这么多帧（模拟器帧时间不可靠）。 */
    fumeSettleSteps: Int = 0,
    /** 取证用：取景定格时刻（秒）。>0 且 [fumeSettleSteps]>0 时，画面固定停在这一时刻。 */
    fumeSettleTimeSeconds: Double = 0.0,
    /** 取证用：把落点镜头放在组合期一次算好（模拟器单帧 3–4s，等异步刷新拍不及）。 */
    fumeStillFrame: Boolean = false,
    /** V1a 自查：浮名舞台画块边界/对焦十字（取景用，正式界面不开）。 */
    fumeDebugFrames: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val backLabel = str(strings.back)
    val emptyKind = lyricEmptyKind(lineCount = lyrics.size, loading = loading, failed = failure != null)

    // ---------------- V0：减少动效 / 封面取色 ----------------
    val reduceMotion = rememberReduceMotion()
    // 当前句在内核时间轴上的下标：U5 的 currentIndex 与 kernelLines 一一对应（同一份歌词）。
    // 对不上（第一句之前、越界）就当没有内核行，整页回落到 U5 原视图。
    val activeLine: Line? = if (currentIndex in kernelLines.indices) kernelLines[currentIndex] else null

    // 字幕层内容：当前句翻译；这一句没有可读翻译时改画后面几行的预告（Folia 同款规则）。
    // `showText` 与舞台一致 —— 空态（加载中 / 失败 / 无歌词）时不画字幕。
    // 浮名不画字幕层：Folia 的浮名把整首歌的每一行都排进文章里自己打印，
    // 当前句若再让字幕层画一遍，就是同一句话在屏上出现两遍、且字幕层是居中小字，
    // 会和镜头里的放大正文抢视觉（实拍里表现为「浮名画了一遍、居中又画了一遍」）。
    val subtitleContent = remember(activeLine, kernelLines, currentIndex, emptyKind, lyricStyle) {
        resolveSubtitleOverlayContent(
            activeLine = activeLine,
            nextLines = kernelLines.drop(currentIndex.coerceAtLeast(0) + 1),
            showText = emptyKind == null && lyricStyle != LyricStyle.FUME,
        )
    }

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
    // 当前句舞台高度用：Folia 的外层是 h-[70vh]，所以需要视口高度（px）。
    val viewportHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx().toDouble()
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

    // V0d 取证：只画当前句的 flex-wrap 舞台（等价 Folia 的 70vh 画布）。
    if (lineOnlyStage) {
        Box(Modifier.fillMaxSize().background(Color(0xFF2A1A16))) {
            if (activeLine != null) {
                ClassicLyricLine(
                    line = activeLine,
                    clock = clock,
                    tuning = classicTuning,
                    intensity = AnimationIntensity.NORMAL,
                    reduceMotion = false,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    lyricsFontScale = lyricsFontScale,
                    activeColor = lyricAccent,
                    baseColor = onBackdrop,
                    fontFamily = GuangYueDesign.Serif,
                    glowStrengthOverride = glowStrengthOverride,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
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
        // ---------------- 浮名：整屏画布（edge-to-edge，系统栏/顶栏/字幕层都浮在它上面） ----------------
        // 对应 Folia VisualizerFume.tsx:333-350：画布是 VisualizerShell 里 absolute inset-0 的一层，
        // 视口 = 整屏（ResizeObserver 量的是那个 h-full w-full 的 div），顶栏 / 字幕层 / 播放条
        // 都是浮在上面的独立层。它**不是**被顶栏和字幕挤压出来的「剩余空间」——
        // 那是流光(classic)舞台的算法（[ClassicLyricLine] 用 stageHeightPx）。
        // 所以浮名画布与 [Column] 平级挂在这一层，拿到的 viewport 就是整屏。
        if (lyricStyle == LyricStyle.FUME && kernelLines.isNotEmpty() && emptyKind == null) {
            FumeBulletinStage(
                lineIndex = currentIndex,
                lines = kernelLines,
                clock = clock,
                tuning = fumeTuning,
                intensity = AnimationIntensity.NORMAL,
                lyricsFontScale = lyricsFontScale,
                activeColor = lyricAccent,
                baseColor = onBackdrop,
                accentColor = lyricAccent,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
                settleSteps = fumeSettleSteps,
                settleTargetTime = fumeSettleTimeSeconds,
                stillCamera = if (fumeStillFrame) FumeStillFrame(fumeSettleSteps.coerceAtLeast(1)) else null,
                debugFrames = fumeDebugFrames,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
            // V1b-3：顶栏高度改为「至少 56dp」，1.3 倍字体时随标题+副标题两行长高，副标题不再被裁掉下半截。
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(vertical = 4.dp),
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

            // ---------------- 舞台（只画当前句）/ 空态 ----------------
            // 舞台高度不再取「视口 70%」——那是 Folia 全屏画布的算法，在手机窄高视口里会顶穿
            // 顶栏与播放条。这里交给 `weight(1f)`：舞台 = 顶栏与「播放条 + 字幕层」之间的剩余空间。
            // 字幕层用 `padding(bottom = SUPERSCRIPT_RESERVE)` 让出净空，舞台因此天然收在字幕之上，
            // 1.3 倍字号换行也不会压到播放条（审图第 2 条）。
            // 舞台高度由剩余空间决定（V0e 修 Hark 第 2 条：1.3 倍字号时当前句第二行会被播放条压住）。
            // 这里量出这块 Box 的实际高度，交给 ClassicLyricLine 当 viewportHeightPx，
            // 它内部的 stageHeight 就落在顶栏与字幕层之间，FlowRow 垂直居中，不再按屏幕 70% 撑出视口。
            var stageHeightPx by remember { mutableIntStateOf(0) }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { stageHeightPx = it.height },
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
                } else if (lyricStyle == LyricStyle.FUME && kernelLines.isNotEmpty()) {
                    // 浮名：画布已经铺在整屏那一层（本函数开头），舞台 Box 里留空，
                    // 只保留顶栏 / 字幕层 / 播放条这些「浮在画布上」的层。
                } else if (activeLine != null && activeLine.hasVisibleWords()) {
                    // 舞台只画正在唱的这一句（Folia classic/Visualizer.tsx 的 activeLine 分支）。
                    ClassicLyricLine(
                        line = activeLine,
                        clock = clock,
                        tuning = classicTuning,
                        intensity = AnimationIntensity.NORMAL,
                        reduceMotion = reduceMotion,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = stageHeightPx.toDouble(),
                        lyricsFontScale = lyricsFontScale,
                        activeColor = lyricAccent,
                        baseColor = onBackdrop,
                        fontFamily = GuangYueDesign.Serif,
                        glowStrengthOverride = glowStrengthOverride,
                        // 舞台 Box 已经居中，内层不能再让 FlowRow 的 fillMaxWidth 把宽度
                        // 撑到整屏（词距是按内核算出的 marginRight，撑满会溢出）。
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = with(LocalDensity.current) { stageHeightPx.toDp() })
                            .clickableNoRipple { if (currentIndex >= 0) onSeekToLine(currentIndex) },
                    )
                } else {
                    // 没有当前句（间奏 / 还没开唱）：Folia 的「等待音乐」占位，opacity 0.5。
                    // 字号走 ClassicLayout.emptyPixelFontSize（clamp(1.5rem, 3.5vw, 2.25rem)），
                    // 与舞台同样的 px -> sp 折算，避免和系统字号双重放大。
                    val emptySp = with(LocalDensity.current) {
                        ClassicLayout.emptyPixelFontSize(
                            fontScale = lyricsFontScale,
                            viewportWidthPx = viewportWidthPx,
                        ).toFloat().toSp()
                    }
                    Text(
                        text = str(strings.waiting),
                        color = theme.secondary.copy(alpha = 0.5f),
                        fontSize = emptySp,
                        fontFamily = GuangYueDesign.Serif,
                        maxLines = 1,
                        modifier = Modifier.testTag(LyricStagePlaceholderTag),
                    )
                }
            }

            // ---------------- 底部字幕层 + 播放胶囊 ----------------
            // 字幕层固定在底部「导航栏 inset 之上、播放胶囊之上」的预留高度里：
            // 播放胶囊自身约占 56dp + 上下 24dp，所以字幕层的净空 = 胶囊 + [SUBTITLE_STAGE_RESERVE]。
            // 舞台的 weight(1f) 因此天然收在字幕层之上，1.3 倍字号换行也压不到播放条（审图第 2 条）。
            LyricSubtitleOverlay(
                content = subtitleContent,
                secondaryColor = theme.secondary,
                scrimColor = theme.background,
                viewportWidthDp = LocalConfiguration.current.screenWidthDp.toFloat(),
                fontFamily = GuangYueDesign.Sans,
                modifier = Modifier.padding(bottom = SUBTITLE_BAR_RESERVE + SUBTITLE_STAGE_RESERVE),
            )

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

/** 底部胶囊：播放键 + 2dp 进度轨 + 两端等宽时间（跟播放页同观感）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
            // Material3 Slider 的默认 thumb 是 20dp 实心白圆，宽度压到 2dp 的轨道上就成了
            // 进度条左端一个白色圆角块（V0e-fix 审图第 3 条）。Folia 的播放条只有「已播轨道 + 剩余轨道」，
            // 所以这里把 thumb / 轨道都自己画：2dp 胶囊，圆头。
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { onScrub(it); onScrubPreview?.invoke(it) },
                onValueChangeFinished = {},
                thumb = {},
                track = { sliderState ->
                    val fraction = sliderState.value.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(pillInk.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(pillInk.copy(alpha = 0.85f)),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
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

/** V0e 起舞台只有一行，列表锚点已废；保留常量避免外壳/测试引用失效。 */
@Deprecated("V0e 舞台单行已取代 U5 多行列表", ReplaceWith("LyricRootTag"))
const val LyricListTag = "u5-lyrics-list"
const val LyricEmptyTag = "u5-lyrics-empty"

/** 舞台「等待音乐」占位（间奏 / 还没开唱）。 */
const val LyricStagePlaceholderTag = "v0e-stage-placeholder"

/** 底部字幕层。 */
const val LyricSubtitleOverlayTag = "v0e-subtitle-overlay"

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
