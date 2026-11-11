package com.guangyue.feature.player.lyrics

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.animation.animateContentSize

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.guangyue.feature.player.lyrics.visualizer.PartitaLyricLine
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
    /** P1a：舞台底部胶囊右侧的「面板」键（Folia 播放条旁的 sliders 键），打开封面/队列面板；null 不画。 */
    onOpenPanel: (() -> Unit)? = null,
    /** P1b：浮卡面板开着时面板键改画 X（Folia PanelHandle 打开后是关闭键）。 */
    panelOpen: Boolean = false,
    /** P1c：歌词时间轴偏移（ms，正数歌词更晚）；逐字时钟按「播放时间 - 偏移」走。 */
    lyricOffsetMs: Long = 0L,
    /** P2b：展开胶囊的右槽下一首、左槽播放模式。 */
    onNext: (() -> Unit)? = null,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modeLabel: String = "",
    onCycleMode: (() -> Unit)? = null,
    /** R2：背景类型——true =「隐现」（Folia 默认，API 33+），false =「通用」（流体 + 几何）。 */
    latentBackground: Boolean = true,
    /** 截图用：播放胶囊一开始就是展开态。 */
    initialBarExpanded: Boolean = false,
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
    /** V2e：「回环」调参（Folia claddagh/tuning.ts）。 */
    claddaghTuning: com.guangyue.core.lyrics.CladdaghTuning = com.guangyue.core.lyrics.CladdaghTuning.DEFAULT,
    /** V2d：「倾诉」调参（Folia tilt/tuning.ts）。 */
    tiltTuning: com.guangyue.core.lyrics.TiltTuning = com.guangyue.core.lyrics.TiltTuning.DEFAULT,
    /** V2a：「云阶」调参（Folia partita/tuning.ts）。 */
    partitaTuning: com.guangyue.core.lyrics.PartitaTuning = com.guangyue.core.lyrics.PartitaTuning.DEFAULT,
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
    // B0 修：雾笺 / 花火 / 绘光自带固定暗场（Folia 外壳 #05070a / 夜空 / 暗场），与 App 深浅无关。
    // 浅色模式下顶栏、胶囊、系统栏图标仍按浅色主题画成深色，压在夜色上看不清——这三种样式一律按深色画外壳。
    val darkFieldStyle = lyricStyle == LyricStyle.NEBBIA || lyricStyle == LyricStyle.HANABI ||
        lyricStyle == LyricStyle.LUMIERE
    val themeDark = darkMode
    @Suppress("NAME_SHADOWING")
    val darkMode = themeDark || darkFieldStyle
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
            showText = emptyKind == null && lyricStyle != LyricStyle.FUME && lyricStyle != LyricStyle.STILL && lyricStyle != LyricStyle.MONET && lyricStyle != LyricStyle.PENDOLO && lyricStyle != LyricStyle.NEBBIA && lyricStyle != LyricStyle.HANABI && lyricStyle != LyricStyle.TEMPERA,
            // R5：绘光画译文（Folia VisualizerSubtitleOverlay 在绘光上照常显示，基准图里是「translation 12」）。
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
        rememberStaticVisualizerClock((positionMs - lyricOffsetMs) / 1000.0)
    } else {
        rememberVisualizerClock(
            positionMsProvider = { positionMs - lyricOffsetMs },
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

    // ---------------- 沉浸：播放时 3 秒无触摸隐藏顶栏与胶囊（Folia usePlayerChromeAutoHide 的 auto-hide 档） ----------------
    // 任意触摸立刻显出并重新计时；隐藏时的第一下触摸只用来显出（不穿透到按键 / 画布）。横屏另隐藏系统栏。
    val immersiveReady = kernelLines.isNotEmpty() && emptyKind == null
    var chromeTouch by remember { mutableIntStateOf(0) }
    var chromeHidden by remember { mutableStateOf(false) }
    LaunchedEffect(chromeTouch, playback.isPlaying, immersiveReady, panelOpen) {
        chromeHidden = false
        if (immersiveReady && playback.isPlaying && !panelOpen && !runningUnderRobolectric) {
            kotlinx.coroutines.delay(3_000)
            chromeHidden = true
        }
    }
    val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (chromeHidden) 0f else 1f,
        androidx.compose.animation.core.tween(300),
        label = "chromeAlpha",
    )
    val immersiveConf = LocalConfiguration.current
    val immersiveLandscape = immersiveConf.screenWidthDp > immersiveConf.screenHeightDp
    val immersiveView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(immersiveLandscape, immersiveReady) {
        // context 可能是 ContextWrapper（主题包装 / 取证入口），逐层解包找 Activity，否则系统栏藏不掉（CI 横屏图仍有状态栏）。
        var ctx: android.content.Context? = immersiveView.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val w = (ctx as? android.app.Activity)?.window
        val c = w?.let { androidx.core.view.WindowCompat.getInsetsController(it, immersiveView) }
        val hide = immersiveLandscape && immersiveReady && c != null
        if (hide) {
            c!!.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (hide) c?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    val chromeTouchModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                    if (chromeHidden) e.changes.forEach { it.consume() }
                    chromeTouch++
                } else if (chromeHidden) {
                    e.changes.forEach { it.consume() }
                }
            }
        }
    }

    // 背景层：Folia FluidBackground —— 取色底色 @0.8 + 放大 1.5× 的模糊封面 + bottom-right overlay 渐变。
    VisualizerBackdrop(
        palette = palette,
        source = coverSource,
        dark = darkMode,
        modifier = modifier
            .fillMaxSize()
            .then(chromeTouchModifier)
            .testTag(LyricRootTag),
    ) {
        // Folia backgrounds/common：通用几何背景（静止不画背景；浮名按自己的「通用几何图形」开关）。
        if (lyricStyle != LyricStyle.STILL && !(lyricStyle == LyricStyle.FUME && fumeTuning.disableGeometricBackground)) {
            if (latentBackground && com.guangyue.feature.player.lyrics.visualizer.latentBackgroundSupported) {
                // Folia 默认背景「隐现」（DEFAULT_VISUALIZER_BACKGROUND_MODE = 'latent'）。
                com.guangyue.feature.player.lyrics.visualizer.LatentBackgroundLayer(
                    coverUrl = coverUrl,
                    palette = palette,
                    paused = !playback.isPlaying,
                    staticMode = reduceMotion,
                )
            } else com.guangyue.feature.player.lyrics.visualizer.GeometricBackgroundLayer(
                seed = (coverUrl ?: "").hashCode().toLong(),
                secondaryColor = theme.secondary,
                accentColor = lyricAccent,
                // Robolectric 下逐帧循环会让 waitForIdle 永远等不到空闲（截图套件 AppNotIdleException），按暂停画静帧。
                paused = !playback.isPlaying || reduceMotion || runningUnderRobolectric,
            )
        }
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
        // V2b 心象：同样是 VisualizerShell 里铺满的一层（焦点在容器高 42%），顶栏 / 字幕 / 播放条浮在上面。
        if (lyricStyle == LyricStyle.CADENZA && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.CadenzaStage(
                line = activeLine,
                clock = clock,
                tuning = com.guangyue.core.lyrics.CadenzaTuning.DEFAULT,
                intensity = AnimationIntensity.NORMAL,
                reduceMotion = reduceMotion,
                lyricsFontScale = lyricsFontScale,
                activeColor = lyricAccent,
                baseColor = onBackdrop,
                fontFamily = GuangYueDesign.Serif,
                settleSteps = if (fumeStillFrame) 90 else 0,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // V2e 回环：VisualizerShell 里铺满的一层（环心在容器正中），字幕层照常浮在上面。
        if (lyricStyle == LyricStyle.CLADDAGH && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.CladdaghStage(
                lines = kernelLines,
                centerIndex = currentIndex,
                clock = clock,
                tuning = claddaghTuning,
                lyricsFontScale = lyricsFontScale,
                primaryColor = onBackdrop,
                accentColor = lyricAccent,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
            )
        }
        // V2c 静止：Folia 整屏一层（absolute inset-0）+ 暗角；字幕层不画（译文由本层自己排在每句下）。
        if (lyricStyle == LyricStyle.STILL && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.StillVignette(dark = darkMode)
            com.guangyue.feature.player.lyrics.visualizer.StillLyricStage(
                lines = kernelLines,
                currentIndex = currentIndex,
                lyricsFontScale = lyricsFontScale,
                primaryColor = onBackdrop,
                secondaryColor = theme.secondary,
                fontFamily = GuangYueDesign.Serif,
            )
        }
        // P3 莫奈：海报版面一层（歌手 / 竖线 / 大标题 / 专辑 / 歌词轨），让出顶栏与底部胶囊。
        if (lyricStyle == LyricStyle.MONET && kernelLines.isNotEmpty() && emptyKind == null) {
            val monetConf = LocalConfiguration.current
            val monetLandscape = monetConf.screenWidthDp > monetConf.screenHeightDp
            // R1b 的横屏沉浸已上提为全样式通用（见函数开头「沉浸」段）。
            com.guangyue.feature.player.lyrics.visualizer.MonetLyricStage(
                lines = kernelLines,
                currentIndex = currentIndex,
                clock = clock,
                title = playback.item?.title.orEmpty(),
                artist = artist.orEmpty(),
                album = playback.item?.album.orEmpty(),
                lyricsFontScale = lyricsFontScale,
                primaryColor = onBackdrop,
                secondaryColor = theme.secondary,
                backgroundColor = theme.background,
                fontFamily = FontFamily.SansSerif, // Folia DEFAULT_THEME 为无衬线
                // R1b：横屏照 Folia 整屏排（不让顶栏与胶囊，矮屏上下溢出被裁）；竖屏保留适配版式。
                topReserve = if (monetLandscape) 0.dp else 64.dp,
                bottomReserve = if (monetLandscape) 0.dp else 104.dp,
                coverUrl = coverUrl,
                accentColor = lyricAccent,
                reduceMotion = reduceMotion,
                onSeekToLine = onSeekToLine,
                modifier = if (monetLandscape) Modifier else Modifier.windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.navigationBars),
                ),
            )
        }
        // B0 修：雾笺 / 花火 / 绘光是固定暗场（与 App 深浅无关），浅色模式下系统栏图标仍跟主题走成深色，
        // 压在夜色上看不清。暗场样式在场时把状态栏 / 手势条图标强制成浅色，离开时还原为跟随主题。
        val darkFieldStage = darkFieldStyle
        val barsView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(darkFieldStage, themeDark) {
            val window = (barsView.context as? android.app.Activity)?.window
            val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, barsView) }
            if (darkFieldStage && controller != null) {
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
            onDispose {
                if (darkFieldStage && controller != null) {
                    controller.isAppearanceLightStatusBars = !themeDark
                    controller.isAppearanceLightNavigationBars = !themeDark
                }
            }
        }
        // P5 雾笺：整屏夜色玻璃（Folia 外壳 inset-0 背景 #05070a），歌词排在全屏上、不让顶栏。
        if (lyricStyle == LyricStyle.NEBBIA && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.NebbiaStage(
                lines = kernelLines,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                accentColor = lyricAccent,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // R7 花火：Folia hanabi 原版结构（段落 → 镜头；世界上升 + 旋转；烟花 → 字底色块 → 逐字精灵），整屏铺满。
        if (lyricStyle == LyricStyle.HANABI && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.HanabiStage(
                lines = kernelLines,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                accentColor = theme.accent,
                // Folia 花火用主题字体栈（默认无衬线）800 字重；舞台自己用 sans-serif 800 画字。
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                reduceMotion = reduceMotion,
                bottomReserve = 104.dp,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = theme.background,
                primaryColor = theme.primary,
                secondaryColor = theme.secondary,
                seed = playback.item?.key ?: "hanabi",
            )
        }
        // R5 绘光：Folia lumiere 原版对译（段落 → 镜头 → 光位；暗场 + 光场着色器 + 歌词窗口 + 画框），整屏铺满。
        if (lyricStyle == LyricStyle.LUMIERE && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.LumiereStage(
                lines = kernelLines,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                accentColor = lyricAccent,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
                topReserve = 136.dp,
                bottomReserve = 128.dp,
                modifier = Modifier.fillMaxSize(),
                primaryColor = onBackdrop,
                secondaryColor = theme.secondary,
                backgroundColor = theme.background,
                seed = playback.item?.key ?: "lumiere",
            )
        }
        // R6 凝彩：Folia tempera 原版对译（段落→shot→构图色块 + 拼贴排版 + 文字反色），整屏铺满。
        if (lyricStyle == LyricStyle.TEMPERA && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.TemperaStage(
                lines = kernelLines,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                paperColor = theme.background,
                inkColor = onBackdrop,
                accentColor = theme.accent,
                // Folia 凝彩用主题字体栈（默认无衬线）600 字重。
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                reduceMotion = reduceMotion,
                topReserve = 136.dp,
                bottomReserve = 128.dp,
                // R6：Folia 凝彩画布铺满舞台；tone2 混副色（temperaPalette duo）。
                modifier = Modifier.fillMaxSize(),
                secondaryColor = theme.secondary,
                seed = playback.item?.key ?: "tempera",
            )
        }
        // R4 商籁：Folia sonnet 原版对译（PV 镜头：段落→镜头→版式→机位跟踪），整屏铺满。
        if (lyricStyle == LyricStyle.SONNET && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.SonnetStage(
                lines = kernelLines,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                primaryColor = onBackdrop,
                secondaryColor = theme.secondary,
                backgroundColor = theme.background,
                accentColor = lyricAccent,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
                topReserve = 64.dp,
                bottomReserve = 104.dp,
                // R4：Folia 商籁画布铺满整个舞台（inset-0），译文由字幕层画（VisualizerSubtitleOverlay）。
                modifier = Modifier.fillMaxSize(),
                seed = playback.item?.key ?: "sonnet",
                themeName = if (darkMode) "Midnight Default" else "Daylight Default",
            )
        }
        // P4 时计：钟轮在左缘正中，歌词沿弧线排布；整屏铺满（同 Folia）。
        if (lyricStyle == LyricStyle.PENDOLO && kernelLines.isNotEmpty() && emptyKind == null) {
            com.guangyue.feature.player.lyrics.visualizer.PendoloStage(
                lines = kernelLines,
                currentIndex = currentIndex,
                clock = clock,
                lyricsFontScale = lyricsFontScale,
                primaryColor = onBackdrop,
                secondaryColor = theme.secondary,
                accentColor = lyricAccent,
                backgroundColor = theme.background,
                fontFamily = GuangYueDesign.Serif,
                reduceMotion = reduceMotion,
                onSeekToLine = onSeekToLine,
                // Folia 时计铺满整个视口（VisualizerShell inset-0），轮心 (0, 0.5h)、半径 0.42·min(w,h)；
                // 顶栏与底部胶囊叠在上面，不再让出（让出后横屏半径只剩 ~67dp，上下句被挤出弧外）。
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
                    .alpha(chromeAlpha)
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
                Column(Modifier.weight(1f).alpha(if (lyricStyle == LyricStyle.MONET && kernelLines.isNotEmpty() && emptyKind == null) 0f else 1f)) {
                    // P1a：舞台成了播放主视图，顶栏写歌名（Folia 舞台左上衬线标题），没有歌时退回「歌词」。
                    Text(
                        text = playback.item?.title?.takeIf { it.isNotBlank() } ?: str(strings.title),
                        fontSize = GyText.title,
                        lineHeight = GyText.titleHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = onBackdrop,
                        fontFamily = GuangYueDesign.Serif,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                // 2026-11-08 横竖屏切换键：系统「自动旋转」关着也能横过来（用户反馈横屏感应不到）。
                StageRotateKey(tint = onBackdrop)
                Spacer(Modifier.width(GySpace.s))
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
                } else if ((lyricStyle == LyricStyle.FUME || lyricStyle == LyricStyle.CADENZA || lyricStyle == LyricStyle.STILL || lyricStyle == LyricStyle.CLADDAGH || lyricStyle == LyricStyle.MONET || lyricStyle == LyricStyle.PENDOLO || lyricStyle == LyricStyle.NEBBIA || lyricStyle == LyricStyle.SONNET || lyricStyle == LyricStyle.HANABI || lyricStyle == LyricStyle.TEMPERA || lyricStyle == LyricStyle.LUMIERE) && kernelLines.isNotEmpty()) {
                    // 浮名：画布已经铺在整屏那一层（本函数开头），舞台 Box 里留空，
                    // 只保留顶栏 / 字幕层 / 播放条这些「浮在画布上」的层。
                } else if (lyricStyle == LyricStyle.TILT && activeLine != null && activeLine.hasVisibleWords()) {
                    // V2d 倾诉（Folia VisualizerTilt 的 activeLine 分支）。
                    com.guangyue.feature.player.lyrics.visualizer.TiltLyricLine(
                        line = activeLine,
                        clock = clock,
                        tuning = tiltTuning,
                        lyricsFontScale = lyricsFontScale,
                        primaryColor = onBackdrop,
                        accentColor = lyricAccent,
                        fontFamily = GuangYueDesign.Serif,
                        reduceMotion = reduceMotion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { if (currentIndex >= 0) onSeekToLine(currentIndex) },
                    )
                } else if (lyricStyle == LyricStyle.PARTITA && activeLine != null && activeLine.hasVisibleWords()) {
                    // V2a 云阶：当前句切 chunk 竖排错落（Folia VisualizerPartita 的 activeLine 分支）。
                    val stageDensity = LocalDensity.current
                    PartitaLyricLine(
                        line = activeLine,
                        clock = clock,
                        tuning = partitaTuning,
                        intensity = AnimationIntensity.NORMAL,
                        reduceMotion = reduceMotion,
                        viewportWidthDp = LocalConfiguration.current.screenWidthDp.toDouble(),
                        windowHeightDp = LocalConfiguration.current.screenHeightDp.toDouble(),
                        lyricsFontScale = lyricsFontScale,
                        activeColor = lyricAccent,
                        baseColor = onBackdrop,
                        fontFamily = GuangYueDesign.Serif,
                        glowStrengthOverride = glowStrengthOverride,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = with(stageDensity) { stageHeightPx.toDp() })
                            .clickableNoRipple { if (currentIndex >= 0) onSeekToLine(currentIndex) },
                    )
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
                secondaryColor = if (lyricStyle == LyricStyle.LUMIERE) com.guangyue.feature.player.lyrics.visualizer.LumiereSubtitleColor else theme.secondary,
                scrimColor = theme.background,
                viewportWidthDp = LocalConfiguration.current.screenWidthDp.toFloat(),
                fontFamily = GuangYueDesign.Sans,
                // 横屏屏高只有 ~400dp，竖屏那套 136dp 预留会把字幕顶到屏幕正中、压在正文上（用户 11-10 实拍）；
                // 横屏照 Folia subtitleBottomPx 紧贴底栏之上。
                modifier = Modifier.padding(bottom = if (immersiveLandscape) 4.dp else SUBTITLE_BAR_RESERVE + SUBTITLE_STAGE_RESERVE),
            )

            // ---------------- 底部播放胶囊 ----------------
            Row(Modifier.fillMaxWidth().alpha(chromeAlpha), verticalAlignment = Alignment.CenterVertically) {
                LyricBar(
                    modifier = Modifier.weight(1f),
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
                    onScrubEnd = {
                        onSeek(scrubValue)
                        scrubbing = false
                    },
                    title = playback.item?.title.orEmpty(),
                    onNext = onNext,
                    modeIcon = modeIcon,
                    modeLabel = modeLabel,
                    onCycleMode = onCycleMode,
                    initialExpanded = initialBarExpanded,
                )
                if (onOpenPanel != null) {
                    val panelPill = if (darkMode) Color(0xFF17171A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.62f)
                    // P1b：面板打开时圆键反色成实心关闭键（Folia 基准 player-panel-*-m412：浅色黑底白 X）。
                    val keyBg by androidx.compose.animation.animateColorAsState(
                        if (panelOpen) (if (darkMode) Color(0xFFF4F4F5) else Color(0xFF18181B)) else panelPill,
                        label = "panelKeyBg",
                    )
                    val keyInk by androidx.compose.animation.animateColorAsState(
                        if (panelOpen) (if (darkMode) Color(0xFF18181B) else Color.White) else pillInk,
                        label = "panelKeyInk",
                    )
                    Spacer(Modifier.width(GySpace.s))
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(keyBg)
                            .clickableNoRipple(onOpenPanel)
                            .semantics { contentDescription = if (panelOpen) "关闭面板" else "播放面板" },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.Crossfade(targetState = panelOpen, label = "panelKey") { opened ->
                            Icon(
                                if (opened) GuangYueIcons.X else GuangYueIcons.SlidersHorizontal,
                                null,
                                tint = keyInk,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
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
/**
 * 舞台底部播放胶囊（P2b：Folia `FloatingPlayerControls` 折叠 / 展开两态）。
 *
 * 折叠：一条胶囊只有「时间 · 进度 · 时长」（Folia CollapsedView，h-8 px-4 的手机放大版 56dp）；点一下展开。
 * 展开：rounded 30 + p-3，三行——居中歌名、左模式 / 中 48dp 实心播放键 / 右下一首、可拖的进度行；
 * 5 秒无操作或点歌名收起（Folia 鼠标移开即收，手机用计时代替）。
 * 拖进度：松手才 seek（修 P1a 起拖动后不 seek、进度卡在拖动值的问题）。
 */
@Composable
private fun LyricBar(
    modifier: Modifier = Modifier,
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
    onScrubEnd: () -> Unit = {},
    title: String = "",
    onNext: (() -> Unit)? = null,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modeLabel: String = "",
    onCycleMode: (() -> Unit)? = null,
    initialExpanded: Boolean = false,
) {
    val seekBarLabel = str(labels.seekBar)
    val playLabel = str(labels.play)
    val pauseLabel = str(labels.pause)
    val pill = if (dark) Color(0xFF17171A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.62f)
    var expanded by remember { mutableStateOf(initialExpanded) }
    var touch by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(expanded, touch) {
        if (expanded && !runningUnderRobolectric) {
            kotlinx.coroutines.delay(5_000)
            expanded = false
        }
    }
    val shape = RoundedCornerShape(if (expanded) 30.dp else GyRadius.capsule)
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = GySpace.l)
            .clip(shape)
            .background(pill)
            .animateContentSize(),
    ) {
        if (!expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickableNoRipple { expanded = true; touch++ }
                    .semantics { contentDescription = if (playing) "$pauseLabel / 展开播放控制" else "$playLabel / 展开播放控制" }
                    .padding(horizontal = GySpace.pageH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(positionMs), fontSize = 11.sp, color = pillInk.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(GySpace.s))
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(pillInk.copy(alpha = 0.22f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(pillInk.copy(alpha = 0.85f)),
                    )
                }
                Spacer(Modifier.width(GySpace.s))
                Text(formatTime(durationMs), fontSize = 11.sp, color = pillInk.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickableNoRipple { expanded = false },
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (modeIcon != null && onCycleMode != null) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickableNoRipple { onCycleMode(); touch++ }
                                    .semantics { contentDescription = modeLabel },
                                contentAlignment = Alignment.Center,
                            ) { Icon(modeIcon, null, tint = pillInk.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(pillInk)
                            .clickableNoRipple { onTogglePlay(); touch++ }
                            .semantics { contentDescription = if (playing) pauseLabel else playLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) GuangYueIcons.Pause else GuangYueIcons.Play,
                            null,
                            tint = if (dark) Color(0xFF17171A) else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (onNext != null) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickableNoRipple { onNext(); touch++ }
                                    .semantics { contentDescription = "下一首" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(GuangYueIcons.SkipForward, null, tint = pillInk.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(positionMs), fontSize = 11.sp, color = pillInk.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(GySpace.s))
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { onScrub(it); onScrubPreview?.invoke(it); touch++ },
                        onValueChangeFinished = onScrubEnd,
                        thumb = {},
                        track = { sliderState ->
                            val fraction = sliderState.value.coerceIn(0f, 1f)
                            Box(
                                Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(pillInk.copy(alpha = 0.22f)),
                            ) {
                                Box(Modifier.fillMaxWidth(fraction).height(2.dp).clip(RoundedCornerShape(1.dp)).background(pillInk.copy(alpha = 0.85f)))
                            }
                        },
                        modifier = Modifier.weight(1f).height(36.dp).semantics { contentDescription = seekBarLabel },
                    )
                    Spacer(Modifier.width(GySpace.s))
                    Text(formatTime(durationMs), fontSize = 11.sp, color = pillInk.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                }
            }
        }
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


/**
 * 2026-11-08：舞台顶栏的横竖屏切换键。
 *
 * 点一下：竖屏 → 强制横屏（SENSOR_LANDSCAPE，跟重力在左右横屏间翻，不看系统「自动旋转」开关）；
 * 横屏 → 强制竖屏。离开舞台时恢复跟随系统（UNSPECIFIED）；旋转本身会重建 Activity，
 * 那时 isChangingConfigurations 为 true，不复位。
 */
@Composable
internal fun StageRotateKey(tint: androidx.compose.ui.graphics.Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (activity != null) {
        androidx.compose.runtime.DisposableEffect(activity) {
            onDispose {
                if (!activity.isChangingConfigurations) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickableNoRipple {
                activity?.requestedOrientation = if (landscape) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            .semantics { contentDescription = if (landscape) "切换竖屏" else "切换横屏" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GuangYueIcons.Smartphone,
            null,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (landscape) 0f else 90f),
        )
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
