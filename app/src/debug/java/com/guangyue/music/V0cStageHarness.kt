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
)

private val FUME_LRC = FUME_TEXTS.mapIndexed { i, text ->
    val start = i * 4
    "[%02d:%02d.00]%s".format(start / 60, start % 60, text)
}.joinToString("\n")

private fun buildFumeKernelLines(): List<Line> =
    LyricTimeline.toLines(parseLrc(FUME_LRC), (FUME_TEXTS.size + 1) * 4_000L)

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

/** 播放中：[positionMs] 决定当前行与逐字进度。 */
private fun playing(positionMs: Long) = PlaybackState(
    item = QueueItem(
        key = "v0c", title = "夜曲", artist = "周杰伦", album = "十一月的萧邦",
        artworkUri = null,
        origin = MediaOrigin(pluginId = "v0c", trackKey = "v0c", quality = Quality.SUPER),
    ),
    isPlaying = true, positionMs = positionMs, durationMs = 20_000L, bufferedMs = 20_000L,
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
) {
    val view = LocalView.current
    // V0e 修 Hark 第 1 条：取景 Activity 没有 MainActivity 那段 insets SideEffect，
    // 主题又把 windowLightStatusBar 写死成 true，所以深色下状态栏会是一条白底。
    // 这里按 darkMode 把图标亮暗切回来（背景层 fillMaxSize 已铺到状态栏后面）。
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    FoliaTheme(dark = dark) {
        // V0e 取证：模拟器的 withFrameNanos 推得极慢，帧累积停在 0.1s 左右，所有词都还停在
        // WAITING（整词 opacity 0），舞台就拍成空白。截图取证一律走静态时钟 = positionMs/1000。
        runningUnderRobolectricOverride = true
        // V0e 三态：trans = 有翻译；preview = 当前句无翻译 → 字幕层改画后面几行预告；interlude = 无当前行 → 舞台占位。
        val withTranslation = variant != "preview"
        val isFume = style == "fume"
        val fumeLines = remember(isFume) { if (isFume) buildFumeKernelLines() else emptyList() }
        // 浮名取景：v0c_fumetime 可以单独指定「画面定格在第几秒」，此时当前句也按它取，
        // 否则（例如 v0c_ms 13000 落在第 4 句）镜头会按第 4 句落点去算，画面与定位不符。
        val fumeAtClock = if (fumeAt >= 0.0) fumeAt else positionMs / 1000.0
        val currentIndex = when {
            isFume -> ((fumeAtClock * 1000.0).toLong() / 4_000L).toInt().coerceIn(0, fumeLines.size - 1)
            variant == "preview" -> 0
            variant == "interlude" -> -1
            else -> 1
        }
        val kernel = remember(withTranslation, isFume) {
            if (isFume) fumeLines else buildKernelLines(withTranslation)
        }
        // V1a 取证：拍「歌词样式」弹层（浮名条目选中态）。只在 debug 源集，release 无此分支。
        if (style == "sheet") {
            CompositionLocalProvider(LyricStringHost provides { id -> v0cString(id) }) {
                LyricStyleSheet(
                    tuning = ClassicTuning(),
                    strings = lyricStyleStrings(),
                    styleName = v0cString(R.string.lyrics_style_classic),
                    onTuning = {},
                    onDismiss = {},
                    style = LyricStyle.FUME,
                    fumeName = v0cString(R.string.lyrics_style_fume),
                )
            }
        } else {
        val lines = remember { parseLrc(LRC) }
        // V0d flex-wrap 取证：只画「当前句」的 FlowRow 舞台（等价 Folia 的 70vh 画布），
        // 验证长句在 360dp / 320dp / 1.3× 字号下自动换行、不出屏。
        // extra：`v0c_lineonly`（布尔）+ `v0c_wordglow`（float，辉光强度）。
        CompositionLocalProvider(LyricStringHost provides { id -> v0cString(id) }) {
            LyricFlowScreen(
                lyrics = lines,
                currentIndex = currentIndex,
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
                lineOnlyStage = lineOnly,
                lyricStyle = if (style == "fume") LyricStyle.FUME else LyricStyle.CLASSIC,
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
        val debugFrames = intent?.getBooleanExtra("v0c_debugframes", false) ?: false
        val wordGlow = if (intent?.hasExtra("v0c_wordglow") == true) {
            intent.getFloatExtra("v0c_wordglow", 1f)
        } else {
            null
        }
        if (lineOnly) runningUnderRobolectricOverride = true
        setContent { V0cStage(positionMs, dark, wordGlow, lineOnly, variant, style, fumeSteps, debugFrames, fumeAt) }
    }
}
