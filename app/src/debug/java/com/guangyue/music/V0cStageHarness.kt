// V0c 取证专用（仅 debug 源集，release 不含）：
// 直接以固定 LRC（「为你弹奏萧邦的夜曲」）构图 LyricFlowScreen，
// 用 extra `v0c_ms` 指定播放时刻、`v0c_dark` 指定浅/深，拍「辉光过程 / 三态 / 字幕层 / 取色背景」。
package com.guangyue.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.guangyue.core.data.lyrics.LyricLine
import com.guangyue.core.data.lyrics.LyricTimeline
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints
import com.guangyue.core.lyrics.Word
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.Quality
import com.guangyue.core.player.QueueItem
import com.guangyue.feature.player.lyrics.LyricFlowScreen
import com.guangyue.feature.player.lyrics.LyricStringHost
import com.guangyue.feature.player.lyrics.LyricStrings
import com.guangyue.feature.player.lyrics.LyricStyle
import com.guangyue.feature.player.lyrics.LyricStyleSheet
import com.guangyue.feature.player.lyrics.LyricStyleSheetPreview
import androidx.compose.foundation.layout.navigationBarsPadding
import com.guangyue.music.nav.lyricStyleStrings
import com.guangyue.feature.player.lyrics.visualizer.fume.fumeGlowOverride
import com.guangyue.feature.player.lyrics.visualizer.runningUnderRobolectricOverride
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme

private const val LRC = "[00:00.00]一群嗜血的蚂蚁\n" +
    "[00:04.00]为你弹奏萧邦的夜曲\n" +
    "[00:08.00]纪念我死去的爱情\n" +
    "[00:12.00]跟夜风一样的声\n" +
    "[00:16.00]我面无表情看孤独的风景\n"

private val TRANSLATIONS = mapOf(
    "为你弹奏萧邦的夜曲" to "For you I play Chopin's nocturne",
    "纪念我死去的爱情" to "Remembering my dead love",
)

private val WORD_SPLIT = mapOf(
    "为你弹奏萧邦的夜曲" to listOf("为你", "弹奏", "萧邦的", "夜曲"),
)

/**
 * V1a 浮名取证：18 句原创中文歌词（与 v1a-folia-ref.png 同一份文案，每句 4s）。
 * 浮名是「整首歌排成一篇文章」，句子太少看不出镜头在文章里移动，所以单独给一份长文案。
 */
private val FUME_TEXTS = listOf(
    "既然无法结伴 就听我把话讲完",
    "灯一盏一盏熄灭 街道留给我一个人",
    "风把旧信封翻到最后一页",
    "你说过的话像雪 落地就没有回声",
    "我把脚步放轻 怕惊醒睡着的光",
    "凌晨三点的窗 只肯收留沉默",
    "有些名字念旧了 会自己发热",
    "我在纸上写诗 写完就撕成雪",
    "日子过得很慢 慢得像一场等待",
    "雨落进旧铁皮屋顶 是一首完整的歌",
    "你走以后 房间学会了屏住呼吸",
    "我把想念放在窗台 让它自己发芽",
    "所有的告别都后来才懂",
    "烟火升到一半就散场",
    "我还没学会在人群里安静",
    "但我知道 天总会亮得比记忆快",
    "于是我把余下的路走得像一首歌",
    "唱完这一句 我就要抬头了",
    // V1b-1 取证：中英混排一句（英文窄、汉字宽），验镜头取点按实测字宽落在正在唱的字上。
    "hold on 别急着说再见",
)

private const val FUME_LINE_SECONDS = 4

/** 整篇浮名文案的时长上界：锚点句被挪到第 0 秒时，最后一句仍在 76s，留到 84s。 */
private const val FUME_LRC_SPAN_MS = 24L * FUME_LINE_SECONDS * 1_000L

private fun fumeLrc(anchorLine: Int = 0, anchorTime: Double = 0.0): String =
    FUME_TEXTS.mapIndexed { i, text ->
        // V1b-1 取证：把 anchorLine 整句挪到 anchorTime，它的行内时间轴才会覆盖定格时刻，
        // 「正在唱」的一词进度不再恒为 0（此前整篇都在定格时刻之前，进度被钳成 0）。
        val start = ((i - anchorLine) * FUME_LINE_SECONDS + anchorTime).toInt()
        "[%02d:%02d.00]%s".format(start / 60, start % 60, text)
    }.joinToString("\n")

private fun buildFumeKernelLines(lrc: String = fumeLrc(), totalMs: Long = FUME_LRC_SPAN_MS): List<Line> =
    LyricTimeline.toLines(parseLrc(lrc), totalMs)

/** 极简 LRC：[mm:ss.xx]文本。 */
private fun parseLrc(raw: String): List<LyricLine> = raw.trim().lines().mapNotNull { row ->
    val match = Regex("""\[(\d{2}):(\d{2})\.(\d{2})](.*)""").find(row) ?: return@mapNotNull null
    val (mm, ss, cs, text) = match.destructured
    LyricLine(
        timeMs = mm.toLong() * 60_000L + ss.toLong() * 1_000L + cs.toLong() * 10L,
        text = text,
    )
}

private fun buildKernelLines(withTranslation: Boolean = true): List<Line> =
    LyricTimeline.toLines(parseLrc(LRC), 20_000L).map { line ->
        val text = line.fullText.trim()
        val translation = if (withTranslation) TRANSLATIONS[text] else null
        val words = WORD_SPLIT[text]
        if (words == null || words.joinToString("") != text) {
            line.copy(translation = translation)
        } else {
            val span = (line.endTime - line.startTime) / words.size
            line.copy(
                words = words.mapIndexed { i, w ->
                    Word(w, line.startTime + span * i, line.startTime + span * (i + 1))
                },
                translation = translation,
            )
        }
    }

/**
 * Folia 基准对照用的同一份歌词（B0）：照搬 Folia dev/probes/visualizerMemory.probe.tsx 的 `cjk=1`
 * 合成歌词——CJK_LINES 循环、每行 3 秒、逐字计时（步长 (3-0.2)/字数）、行尾 2.9 秒、
 * 译文 `translation N`、每 4 行一个副歌。Folia 那边用 `?probe=visualizerMemory&vis=<样式>&cjk=1&speed=0&start=<秒>`
 * 定格同一时刻，这边用 `--es v0c_fixture probe --el v0c_ms <毫秒>`，两张图才是「同一首歌同一时刻」。
 */
private val PROBE_CJK_LINES = listOf(
    "迷失在无边际这幽深的森林",
    "风吹过",
    "我们慢慢走回去，星河与风一起落在很远很远的地方",
    "光落在晨雾里",
    "你说夜色会把所有的名字都轻轻藏起来",
    "晚安",
    "在城市尽头等一场迟到的雨",
    "把回忆折成纸船放进河流",
    "轻轻唱 hello again 在无人的街角",
    "我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔",
)
private const val PROBE_LINE_MS = 3_000L
private const val PROBE_LINE_COUNT = 40

private fun probeLyrics(): List<LyricLine> = (0 until PROBE_LINE_COUNT).map { i ->
    LyricLine(
        timeMs = i * PROBE_LINE_MS,
        text = PROBE_CJK_LINES[i % PROBE_CJK_LINES.size],
        translation = "translation $i",
    )
}

private fun probeKernelLines(): List<Line> = (0 until PROBE_LINE_COUNT).map { i ->
    val start = i * PROBE_LINE_MS / 1000.0
    val text = PROBE_CJK_LINES[i % PROBE_CJK_LINES.size]
    // JS Array.from(string) = 按码点切；这几句全在 BMP 内，等价于逐 char。
    val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
    val step = (PROBE_LINE_MS / 1000.0 - 0.2) / chars.size
    val line = Line(
        words = chars.mapIndexed { o, c -> Word(c, start + o * step, start + (o + 1) * step) },
        startTime = start,
        endTime = start + PROBE_LINE_MS / 1000.0 - 0.1,
        fullText = text,
        translation = "translation $i",
        isChorus = i % 4 == 0,
    )
    line.copy(renderHints = RenderHints.buildLineRenderHints(line))
}

/** 播放中：[positionMs] 决定当前行与逐字进度。 */
private fun playing(positionMs: Long, durationMs: Long = 20_000L, key: String = "v0c") = PlaybackState(
    item = QueueItem(
        // R4：探针歌词用 Folia 探针同一个 seed（visualizerMemory.probe 的 `probe-0`），商籁编排才与基准一致。
        key = key, title = "夜曲", artist = "周杰伦", album = "十一月的萧邦",
        artworkUri = null,
        origin = MediaOrigin(pluginId = "v0c", trackKey = "v0c", quality = Quality.SUPER),
    ),
    isPlaying = true, positionMs = positionMs, durationMs = durationMs, bufferedMs = durationMs,
    shuffle = false, repeatMode = 0, queueSize = 5, index = 1,
)

/** V0c / V0e 舞台：唯一入口，只画 activeLine + 字幕层。 */
@Composable
fun V0cStage(
    positionMs: Long,
    dark: Boolean,
    wordGlow: Float? = null,
    lineOnly: Boolean = false,
    variant: String = "trans",
    /** V1a：`"classic"` = 流光（默认），`"fume"` = 浮名。 */
    style: String = "classic",
    /** V1a：浮名镜头固定推进帧数（模拟器帧时间不可靠，见 FumeBulletinStage 的 settleSteps）。 */
    fumeSteps: Int = 0,
    /** V1a 自查：画块边界与对焦十字。 */
    debugFrames: Boolean = false,
    /** V1a 取景：浮名画面定格时刻（秒）；缺省 = positionMs/1000。 */
    fumeAt: Double = -1.0,
    /** V1b-1 取景：锚点句下标（v0c_fumeline）；-1 = 用 floor(fumeAt/4)。 */
    fumeAnchor: Int = -1,
    /** B0：`"probe"` = 与 Folia 基准探针同一份歌词（见 [PROBE_CJK_LINES]）。 */
    fixture: String = "nightsong",
) {
    val view = LocalView.current
    // V0e 修 Hark 第 1 条：取景 Activity 没有 MainActivity 那段 insets SideEffect，
    // 主题又把 windowLightStatusBar 写死成 true，所以深色下状态栏会是一条白底。
    // 这里按 darkMode 把图标亮暗切回来（背景层 fillMaxSize 已铺到状态栏后面）。
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            // B0：暗场样式（雾笺/花火/绘光）一律浅色图标，与 LyricFlowScreen 的外壳规则一致。
            val barsDark = dark || style == "nebbia" || style == "hanabi" || style == "lumiere" || style.startsWith("machi")
            isAppearanceLightStatusBars = !barsDark
            isAppearanceLightNavigationBars = !barsDark
        }
    }
    FoliaTheme(dark = dark) {
        // V0e 取证：模拟器的 withFrameNanos 推得极慢，帧累积停在 0.1s 左右，所有词都还停在
        // WAITING（整词 opacity 0），舞台就拍成空白。截图取证一律走静态时钟 = positionMs/1000。
        runningUnderRobolectricOverride = true
        // V0e 三态：trans = 有翻译；preview = 当前句无翻译 → 字幕层改画后面几行预告；interlude = 无当前行 → 舞台占位。
        val withTranslation = variant != "preview"
        val isFume = style == "fume"
        // V1b-1：锚点句 = 定格时刻落在第几句（v0c_fumeline 可显式指定），
        // 把这一句整体挪到 0s，镜头/高亮落在第 ceil(fumeAt) 秒的那句上。
        val anchor = if (fumeAnchor >= 0) fumeAnchor else (fumeAt / FUME_LINE_SECONDS).toInt()
        val fumeAnchorLine = anchor.coerceIn(0, FUME_TEXTS.size - 1)
        val fumeLines = remember(isFume, fumeAt, fumeAnchorLine) {
            if (!isFume) {
                emptyList()
            } else if (fumeAt >= 0.0) {
                buildFumeKernelLines(fumeLrc(fumeAnchorLine, 0.0), (FUME_TEXTS.size + 1) * FUME_LINE_SECONDS * 1_000L)
            } else {
                buildFumeKernelLines(fumeLrc(), FUME_LRC_SPAN_MS)
            }
        }
        // 浮名取景：v0c_fumetime 可以单独指定「画面定格在第几秒」，此时当前句也按它取，
        // 否则（例如 v0c_ms 13000 落在第 4 句）镜头会按第 4 句落点去算，画面与定位不符。
        val fumeAtClock = if (fumeAt >= 0.0) fumeAt else positionMs / 1000.0
        val currentIndex = when {
            isFume -> ((fumeAtClock * 1000.0).toLong() / 4_000L).toInt().coerceIn(0, fumeLines.size - 1)
            variant == "preview" -> 0
            variant == "interlude" -> -1
            else -> 1
        }
        val isProbe = fixture == "probe"
        val kernel = remember(withTranslation, isFume, fumeLines, isProbe) {
            when {
                isProbe -> probeKernelLines()
                isFume -> fumeLines
                else -> buildKernelLines(withTranslation)
            }
        }
        // V1a 取证：拍「歌词样式」弹层（浮名条目选中态）。只在 debug 源集，release 无此分支。
        // V1b-2：弹层是半透明毛玻璃，身后必须有一层真实的播放页画面，实拍才看得出「透」。
        // `v0c_style sheet` 时先铺 [LyricFlowScreen] 的浮名页（柔和渐变水面），再把弹层盖在上面；
        // `v0c_style sheetbare` 仍是旧的「只有弹层」形态。
        // 2026-11-12：町景（machi）3D 舞台起步，只在取证页（未进歌词样式列表）。
        if (style == "machi" || style == "machirain" || style == "machisnow" || style == "machiday" || style == "machidusk") {
            com.guangyue.feature.player.lyrics.visualizer.MachiStagePreview(
                lines = kernel,
                timeSeconds = { positionMs / 1000.0 },
                // 与 Folia 探针对齐（dev/probes/visualizerMemory.probe.tsx）：主题 DEFAULT_THEME「午夜墨染」四色、
                // seed "probe-0"、天气 auto（probe-0 按 seed 落在雾）、镜头静止位。
                backgroundColor = Color(0xFF09090B),
                primaryColor = Color(0xFFF4F4F5),
                secondaryColor = Color(0xFF71717A),
                accentColor = Color(0xFFF4F4F5),
                seed = "probe-0",
                songTitle = "晴天",
                // machirain：雨天（湿路面 + 水洼倒影，MachiWet.kt）取证；machisnow：雪。
                weather = when (style) { "machirain" -> "rain"; "machisnow" -> "snow"; else -> "auto" },
                restCamera = true,
                // machiday / machidusk：白天、黄昏（applyEnvironmentColors 三套插值）取证；其余夜。
                timeOfDay = when (style) { "machiday" -> "day"; "machidusk" -> "dusk"; else -> "night" },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (style == "sheet" || style == "sheetbare" || style == "sheetclassic" || style == "sheetpartita" || style == "sheettilt" || style == "sheetcladdagh") {
            CompositionLocalProvider(LyricStringHost provides { id -> v0cString(id) }) {
                if (style == "sheet" || style == "sheetclassic" || style == "sheetpartita" || style == "sheettilt" || style == "sheetcladdagh") {
                    LyricFlowScreen(
                        lyrics = remember { parseLrc(LRC) },
                        currentIndex = 1,
                        strings = V0cStrings,
                        playback = playing(positionMs),
                        accent = Folia.colors.accent,
                        onBack = {},
                        onSeekToLine = {},
                        onManualScroll = {},
                        onTogglePlay = {},
                        onSeek = {},
                        artist = "周杰伦",
                        source = "V0c 取证",
                        darkMode = dark,
                        coverColor = Color(0xFFB4553C),
                        kernelLines = kernel,
                        classicTuning = ClassicTuning(),
                        coverUrl = null,
                        glowStrengthOverride = V0dGlowOverride,
                        lyricStyle = LyricStyle.FUME,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // V1b-4：模拟器 withFrameNanos 推不动 ModalBottomSheet 的入场动画（实拍只有底图、弹层不出），
                // 取证改画面板本体 [LyricStyleSheetPreview]，贴底放在播放页上，与弹层展开后的位置一致。
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    LyricStyleSheetPreview(
                        tuning = ClassicTuning(),
                        strings = lyricStyleStrings(),
                        styleName = v0cString(R.string.lyrics_style_classic),
                        onTuning = {},
                        // V1b-4：`sheetclassic` 拍流光那一组参数，其余拍浮名参数。
                        style = when (style) {
                            "sheetclassic" -> LyricStyle.CLASSIC
                            "sheetpartita" -> LyricStyle.PARTITA
                            "sheettilt" -> LyricStyle.TILT
                            "sheetcladdagh" -> LyricStyle.CLADDAGH
                            else -> LyricStyle.FUME
                        },
                        fumeName = v0cString(R.string.lyrics_style_fume),
                        partitaName = v0cString(R.string.lyrics_style_partita),
                    )
                }
            }
        } else {
        val lines = remember(isProbe) { if (isProbe) probeLyrics() else parseLrc(LRC) }
        val stageIndex = if (isProbe) {
            (positionMs / PROBE_LINE_MS).toInt().coerceIn(0, PROBE_LINE_COUNT - 1)
        } else {
            currentIndex
        }
        // V0d flex-wrap 取证：只画「当前句」的 FlowRow 舞台（等价 Folia 的 70vh 画布），
        // 验证长句在 360dp / 320dp / 1.3× 字号下自动换行、不出屏。
        // extra：`v0c_lineonly`（布尔）+ `v0c_wordglow`（float，辉光强度）。
        CompositionLocalProvider(LyricStringHost provides { id -> v0cString(id) }) {
            LyricFlowScreen(
                lyrics = lines,
                currentIndex = stageIndex,
                strings = V0cStrings,
                playback = if (isProbe) playing(positionMs, PROBE_LINE_COUNT * PROBE_LINE_MS, key = "probe-0") else playing(positionMs),
                accent = Folia.colors.accent,
                onBack = {},
                onSeekToLine = {},
                onManualScroll = {},
                onTogglePlay = {},
                onSeek = {},
                artist = "周杰伦",
                source = "V0c 取证",
                darkMode = dark,
                coverColor = Color(0xFFB4553C),
                kernelLines = kernel,
                classicTuning = ClassicTuning(),
                coverUrl = null,
                glowStrengthOverride = V0dGlowOverride,
                lineOnlyStage = lineOnly,
                lyricStyle = when (style) {
                    "fume" -> LyricStyle.FUME
                    "partita" -> LyricStyle.PARTITA
                    "cadenza" -> LyricStyle.CADENZA
                    "still" -> LyricStyle.STILL
                    "monet" -> LyricStyle.MONET
                    "pendolo" -> LyricStyle.PENDOLO
                    "nebbia" -> LyricStyle.NEBBIA
                    "sonnet" -> LyricStyle.SONNET
                    "hanabi" -> LyricStyle.HANABI
                    "tempera" -> LyricStyle.TEMPERA
                    "lumiere" -> LyricStyle.LUMIERE
                    // machiplayer：歌词样式「町景」走正式播放页（LyricFlowScreen），区别于只拍舞台的 machi。
                    "machiplayer" -> LyricStyle.MACHI
                    "tilt" -> LyricStyle.TILT
                    "claddagh" -> LyricStyle.CLADDAGH
                    else -> LyricStyle.CLASSIC
                },
                fumeSettleSteps = fumeSteps,
                fumeSettleTimeSeconds = fumeAtClock,
                fumeStillFrame = fumeSteps > 0,
                fumeDebugFrames = debugFrames,
                modifier = Modifier.fillMaxSize(),
            )
        }
        }
    }
}

/** 取证不需要真实文案表：id 直接映射成中文（只取截图里会出现的那几个键）。 */
private val V0cStrings = LyricStrings(
    back = 0, title = 1, play = 2, pause = 3, empty = 4, emptyHint = 5, loading = 6,
    loadFailed = 7, failedHint = 8, retry = 9, pureMusic = 10, waiting = 11, seekBar = 12,
    sourceLabel = 13, sourceNone = 14,
)

private fun v0cString(id: Int): String = when (id) {
    0 -> "返回"
    1 -> "歌词"
    2 -> "播放"
    3 -> "暂停"
    4 -> "无可用歌词"
    5 -> "换一个音源或稍后再试"
    6 -> "正在匹配最佳歌词…"
    7 -> "匹配最佳歌词失败"
    8 -> "稍后再试，或在设置里换一个音源"
    9 -> "重试"
    10 -> "纯音乐，无需匹配歌词"
    11 -> "等待音乐"
    12 -> "播放进度"
    13 -> "歌词"
    14 -> "无"
    // 「歌词样式」弹层文案（与 res/values/strings.xml 的 V0 歌词样式段一致）
    R.string.lyrics_style_settings -> "歌词样式"
    R.string.lyrics_style_desc -> "选一种排版与动效"
    R.string.lyrics_style_classic -> "流光"
    R.string.lyrics_style_fume -> "浮名"
    R.string.lyrics_style_rotation -> "字词旋转"
    R.string.lyrics_style_breathing -> "呼吸浮动"
    R.string.lyrics_style_spacing -> "词距"
    R.string.lyrics_style_reset -> "恢复默认"
    R.string.lyrics_style_close -> "关闭"
    R.string.lyrics_style_partita -> "云阶"
    R.string.lyrics_style_cadenza -> "心象"
    R.string.lyrics_style_still -> "静止"
    R.string.lyrics_style_monet -> "莫奈"
    R.string.lyrics_style_pendolo -> "时计"
    R.string.lyrics_style_nebbia -> "雾笺"
    R.string.lyrics_style_sonnet -> "商籁"
    R.string.lyrics_style_hanabi -> "花火"
    R.string.lyrics_style_tempera -> "凝彩"
    R.string.lyrics_style_lumiere -> "绘光"
    R.string.lyrics_style_machi -> "町景"
    R.string.lyrics_style_tilt -> "倾诉"
    R.string.lyrics_style_claddagh -> "回环"
    R.string.lyrics_style_cl_focus -> "主歌词放大倍率"
    R.string.lyrics_style_cl_radius -> "轨道半径"
    R.string.lyrics_style_cl_tilt -> "轨道倾斜度"
    R.string.lyrics_style_cl_axis -> "中间轴线"
    R.string.lyrics_style_cl_spacing -> "字符间距"
    R.string.lyrics_style_tilt_split -> "分行概率"
    R.string.lyrics_style_tilt_style -> "斜体强调概率"
    R.string.lyrics_style_tilt_scheme -> "配色方案"
    R.string.lyrics_style_tilt_scheme_default -> "双色1"
    R.string.lyrics_style_tilt_scheme_swap -> "双色2"
    R.string.lyrics_style_tilt_scheme_accent -> "单色1"
    R.string.lyrics_style_tilt_scheme_primary -> "单色2"
    R.string.lyrics_style_no_options -> "这个样式没有参数"
    R.string.lyrics_style_partita_guide -> "引导线"
    R.string.lyrics_style_partita_semantic -> "语义排列"
    R.string.lyrics_style_partita_stagger_min -> "错位最小值"
    R.string.lyrics_style_partita_stagger_max -> "错位最大值"
    R.string.lyrics_style_fume_hide_print -> "隐藏打印方块"
    R.string.lyrics_style_fume_geometric -> "通用几何图形"
    R.string.lyrics_style_fume_bg_opacity -> "世界背景物体透明度"
    R.string.lyrics_style_fume_text_hold -> "文字停留比例"
    R.string.lyrics_style_fume_camera_speed -> "摄影机移动速度"
    R.string.lyrics_style_fume_tracking -> "摄影机追焦方式"
    R.string.lyrics_style_fume_tracking_stepped -> "定格"
    R.string.lyrics_style_fume_tracking_smooth -> "平滑"
    R.string.lyrics_style_fume_glow -> "当前句辉光强度"
    R.string.lyrics_style_fume_hero_scale -> "大标题比例"
    else -> ""
}

/**
 * 入口：
 * adb shell am start -n com.guangyue.music/.V0cStageActivity --ei v0c_ms 2000 --ez v0c_dark true
 */
/**
 * V0d 取证用：非 null 时把辉光强度钉在这个值上。
 *
 * 模拟器的软件 EGL 上下文中 `withFrameNanos` 推进极慢，[VisualizerClock] 停在前一两秒，
 * 三态一直停在 WAITING，拍不到 active / passed。这里只在 debug 源集里提供一个覆盖开关，
 * 只影响拍图，不进入 release。
 */
internal var V0dGlowOverride: Float? = null
class V0cStageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // V0e：取景也走 edge-to-edge，背景铺到状态栏/导航栏后面。
        enableEdgeToEdge()
        val positionMs = intent?.getLongExtra("v0c_ms", 2_000L) ?: 2_000L
        val dark = intent?.getBooleanExtra("v0c_dark", true) ?: true
    // V0d 取证：给辉光一个固定强度，避开「withFrameNanos 在模拟 EGL 上不推进」导致画面停在 WAITING。
    if (intent?.hasExtra("v0c_strength") == true) {
        V0dGlowOverride = intent.getFloatExtra("v0c_strength", 1f)
        // V1a 取证：同一开关同时钉住浮名的辉光强度，拍光晕时不用赌帧。
        fumeGlowOverride = V0dGlowOverride
    }
        val lineOnly = intent?.getBooleanExtra("v0c_lineonly", false) ?: false
        val variant = intent?.getStringExtra("v0c_variant") ?: "trans"
        val style = intent?.getStringExtra("v0c_style") ?: "classic"
        val fumeSteps = intent?.getIntExtra("v0c_fumesteps", 0) ?: 0
        // 取景定格时刻：浮名镜头按这一时刻落地（见 LyricFlowScreen.fumeSettleTimeSeconds）。
        val fumeSettleTime = intent?.getFloatExtra("v0c_fumetime", -1f) ?: -1f
        val fumeAt = if (fumeSettleTime >= 0f) fumeSettleTime.toDouble() else positionMs / 1000.0
        val fumeAnchor = intent?.getIntExtra("v0c_fumeline", -1) ?: -1
        val debugFrames = intent?.getBooleanExtra("v0c_debugframes", false) ?: false
        val fixture = intent?.getStringExtra("v0c_fixture") ?: "nightsong"
        val wordGlow = if (intent?.hasExtra("v0c_wordglow") == true) {
            intent.getFloatExtra("v0c_wordglow", 1f)
        } else {
            null
        }
        // V1b-1 取证：定格在某一句上时，把那一句的时间轴挪到定格时刻附近（见 V0cStage 里的 fumeLines）。
        if (lineOnly) runningUnderRobolectricOverride = true
        // V1b-1 取证：把取景参数打一行（adb logcat -s V0cStage），用于在报告里写明拍摄条件。
        if (style == "fume" && fumeAt >= 0.0) {
            android.util.Log.i(
                "V0cStage",
                "v1b1 fume_at=$fumeAt position_ms=$positionMs steps=$fumeSteps " +
                    "anchor_line=${if (fumeAnchor >= 0) fumeAnchor else (fumeAt / FUME_LINE_SECONDS).toInt()} " +
                    "text=${FUME_TEXTS.getOrNull(if (fumeAnchor >= 0) fumeAnchor else (fumeAt / FUME_LINE_SECONDS).toInt())}",
            )
        }
        setContent {
            V0cStage(
                positionMs, dark, wordGlow, lineOnly, variant, style, fumeSteps, debugFrames, fumeAt, fumeAnchor,
                fixture,
            )
        }
    }
}
