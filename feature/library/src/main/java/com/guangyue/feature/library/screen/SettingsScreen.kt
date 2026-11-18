package com.guangyue.feature.library.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.FoliaGroup
import com.guangyue.feature.library.design.FoliaRow
import com.guangyue.feature.library.design.FoliaSegmented
import com.guangyue.feature.library.design.FoliaTopBar
import com.guangyue.feature.library.design.FoliaToggle
import com.guangyue.feature.library.design.FoliaTextField
import com.guangyue.feature.library.design.clickableNoRipple
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GyRadius
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.GyText
import com.guangyue.feature.library.design.Gyp
import com.guangyue.feature.library.design.foliaCardSurface
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.design.marbleBackground

/**
 * S1：设置（Folia `SettingsModal`：顶部搜索 + 分类 + 分组行）。
 *
 * Folia 桌面是左侧分类列表 + 右侧页面；手机上分类改成搜索框下一条横滑分段（外观 / 播放页 / 播放控制 / 存储与备份 / 关于，
 * 名字取 zh-CN `options.settingsPage*`），页面内仍是 Folia kit 的 Group/Row。搜索：按行名 + 说明过滤，
 * 有输入时不分页，把所有分类里命中的行按分类分组列出（Folia SettingsSearchResults）。
 * 只放广乐真有的开关；Folia 的主题 / 图形 / 交互 / 集成等页等对应功能落地再加。
 */
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lxQuality: String = "320k",
    onPickLxQuality: (String) -> Unit = {},
    /** 歌词样式（id 到名字），当前 id，选中回调；空列表不显示这一行。 */
    lyricStyles: List<Pair<String, String>> = emptyList(),
    lyricStyle: String = "",
    onPickLyricStyle: (String) -> Unit = {},
    /** R2：背景类型（id 到名字）；空列表不显示这一行。 */
    backgrounds: List<Pair<String, String>> = emptyList(),
    background: String = "",
    onPickBackground: (String) -> Unit = {},
    /** 町景三项（标题、说明、选项、当前、回调）；空列表不显示。 */
    machiChoices: List<SettingChoice> = emptyList(),
    /** 默认播放音质（wire 到名字）。 */
    playQualities: List<Pair<String, String>> = emptyList(),
    playQuality: String = "",
    onPickPlayQuality: (String) -> Unit = {},
    onOpenPlugins: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    versionName: String = "0.0.8",
    /** 0.0.27：资料库界面（首页样式）选项；空列表不显示这一行。 */
    homeStyles: List<HomeStyleOption> = emptyList(),
    homeStyle: String = "",
    onPickHomeStyle: (String) -> Unit = {},
    /** 0.0.28：网格首页的歌单卡片样式（Folia grid3dCardStyle）；null 不显示（只在网格首页时显示，同 Folia GRID_ONLY）。 */
    gridCardStyle: String? = null,
    onPickGridCardStyle: (String) -> Unit = {},
    /** 11-17：Folia「顶部胶囊入口」：四个页签与各自是否显示；空列表不显示。 */
    homeTabs: List<Pair<String, Boolean>> = emptyList(),
    onToggleHomeTab: (String, Boolean) -> Unit = { _, _ -> },
    /** 11-17：Folia「记住主页卡片位置」；null 不显示。 */
    rememberHomeCard: Boolean? = null,
    onRememberHomeCard: (Boolean) -> Unit = {},
    /** 11-17：Folia「播放后进入的视图」（player / stay）；null 不显示。 */
    playbackEntryView: String? = null,
    onPickPlaybackEntryView: (String) -> Unit = {},
    /** 11-17：Folia 通用设置「思索 → 触屏思索按钮」；null 不显示。 */
    ponderTouchButton: Boolean? = null,
    onPonderTouchButton: (Boolean) -> Unit = {},
    /** 11-17：Folia「明暗模式」浅色 / 深色 / 跟随系统；null 不显示（退回深色开关）。 */
    daylightChoice: String? = null,
    onPickDaylight: (String) -> Unit = {},
    /** 11-17：Folia「网格卡片 → 卡片最小尺寸 / 最小透明度」；null 不显示。 */
    gridFalloff: Pair<Float, Float>? = null,
    /** 11-17：Folia「全画幅封面 / 正方形卡片」（first / second）；null 不显示。 */
    gridCardShape: Pair<Boolean, Boolean>? = null,
    onGridCardShape: (Boolean, Boolean) -> Unit = { _, _ -> },
    onGridFalloff: (Float, Float) -> Unit = { _, _ -> },
    /** 11-18：Folia「动画与性能」：静态模式 / 关闭主页动态背景 / 降低动态效果·跟随系统设置；null 不显示。 */
    motion: MotionSettingsUi? = null,
    /** 11-18：Folia「歌词 → 时间与过滤 → 全局时间偏移」（毫秒，±2000）；null 不显示。 */
    globalLyricOffsetMs: Long? = null,
    onGlobalLyricOffset: (Long) -> Unit = {},
    /** 11-18：Folia「歌词过滤」手机子集；null 不显示。 */
    lyricFilter: LyricFilterUi? = null,
    /** 打开时先显示的分类（取证用；正常进入是「外观」）。 */
    initialPage: SettingsPage = SettingsPage.APPEARANCE,
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var page by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialPage) }

    val chevron: @Composable () -> Unit = {
        Icon(GuangYueIcons.ChevronRight, null, tint = Gyp.t3, modifier = Modifier.size(16.dp))
    }
    fun icon(v: androidx.compose.ui.graphics.vector.ImageVector): @Composable () -> Unit = {
        Icon(v, null, tint = Gyp.t2, modifier = Modifier.size(18.dp))
    }

    // 11-18 Folia GeneralPage「启动与窗口 → 进入应用后自动播放」（useAudioSettingsStore autoPlayOnLaunch，默认关）。
    // 存在 core/player PlaybackSession 读的同一份 SharedPreferences（playback_session / auto_play_on_launch）。
    val launchCtx = androidx.compose.ui.platform.LocalContext.current
    val launchPrefs = androidx.compose.runtime.remember { launchCtx.applicationContext.getSharedPreferences("playback_session", android.content.Context.MODE_PRIVATE) }
    var autoPlayOnLaunch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(launchPrefs.getBoolean("auto_play_on_launch", false)) }

    val behavior by com.guangyue.core.player.PlaybackBehavior.flow(launchCtx).collectAsState()
    var playbackFade by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.guangyue.core.player.PlaybackFade.enabled(launchCtx)) }
    // 全部设置项（数据驱动：分页与搜索共用一份）。
    val items = buildList {
        // Folia PlaybackPage「播放行为」（options.settingsHeadingPlaybackBehavior）。
        add(SettingItem(SettingsPage.PLAYBACK, "声音", "暂停/播放淡入淡出", "暂停前淡出，恢复时淡入。", icon(GuangYueIcons.AudioWaveform),
            trailing = { FoliaToggle(checked = playbackFade, onChange = { on -> playbackFade = on; com.guangyue.core.player.PlaybackFade.set(launchCtx, on) }) }))
        add(SettingItem(SettingsPage.PLAYBACK, "播放行为", "加入队列的默认位置", null, icon(GuangYueIcons.ListPlus),
            below = { FoliaSegmented(value = if (behavior.queueAddNext) "next" else "append", options = listOf("append" to "追加到末尾", "next" to "追加到下一首"),
                onSelect = { v -> com.guangyue.core.player.PlaybackBehavior.update(launchCtx) { it.copy(queueAddNext = v == "next") } }) }))
        add(SettingItem(SettingsPage.PLAYBACK, "播放行为", "无法播放时自动跳过", "歌曲需要会员或购买、只能试听、所在地区不可播放，或原因不明无法播放时，倒计时后跳到下一首。网络问题不会跳过。", icon(GuangYueIcons.SkipForward),
            trailing = { FoliaToggle(checked = behavior.autoSkipUnplayable, onChange = { on -> com.guangyue.core.player.PlaybackBehavior.update(launchCtx) { it.copy(autoSkipUnplayable = on) } }) }))
        add(SettingItem(SettingsPage.PLAYBACK, "播放行为", "播放时阻止休眠", "播放期间保持屏幕常亮。", icon(GuangYueIcons.Sun),
            trailing = { FoliaToggle(checked = behavior.preventDisplaySleep, onChange = { on -> com.guangyue.core.player.PlaybackBehavior.update(launchCtx) { it.copy(preventDisplaySleep = on) } }) }))
        add(SettingItem(SettingsPage.GENERAL, "启动与窗口", "进入应用后自动播放", "打开应用时续播上次的歌曲。关闭时恢复上次的歌曲并保持暂停。", icon(GuangYueIcons.Play),
            trailing = {
                FoliaToggle(checked = autoPlayOnLaunch, onChange = { on ->
                    autoPlayOnLaunch = on
                    launchPrefs.edit().putBoolean("auto_play_on_launch", on).apply()
                    com.guangyue.feature.library.design.StatusToasts.show(if (on) "启动后将自动续播上次的歌曲" else "启动后将恢复上次的歌曲并保持暂停")
                })
            }))
        if (daylightChoice != null) {
            // Folia AppearancePage「主题 → 明暗模式」（options.daylightMode*、followSystemThemeDesc），分段照它的顺序 浅色 / 深色 / 跟随系统。
            add(SettingItem(SettingsPage.APPEARANCE, "主题", "明暗模式", "根据系统设置自动切换亮色 / 暗色主题；手动切换明暗模式后将关闭此选项。", icon(if (darkMode) GuangYueIcons.Moon else GuangYueIcons.Sun),
                below = { FoliaSegmented(value = daylightChoice, options = listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统"), onSelect = onPickDaylight) }))
        } else {
            add(SettingItem(SettingsPage.APPEARANCE, "外观", "深色模式", "跟随 Folia 的「午夜墨染」深色底", icon(if (darkMode) GuangYueIcons.Moon else GuangYueIcons.Sun),
                trailing = { FoliaToggle(checked = darkMode, onChange = onDarkModeChange) }))
        }
        if (homeStyles.isNotEmpty()) {
            // Folia「设置 → 界面设置 → 资料库界面」（options.librarySuite / librarySuiteDesc）；11-17 起照 Folia settingsNavModel 放「主页与歌单」。
            add(SettingItem(SettingsPage.HOME, "资料库", "资料库界面", "首页、歌单和歌手页用哪套界面浏览。切换时保留播放队列。", icon(GuangYueIcons.LayoutGrid),
                below = { HomeStylePicker(homeStyles, homeStyle, onPickHomeStyle) }))
        }
        if (gridCardStyle != null) {
            // Folia「设置 → 首页 → 歌单卡片样式」（options.grid3dCardStyle / grid3dCardStyleDesc，选项顺序 image、card）。
            add(SettingItem(SettingsPage.HOME, "资料库", "歌单卡片样式", "选择首页歌单卡片样式：纯图片封面或经典的拍立得文本卡片。", icon(GuangYueIcons.LayoutGrid),
                below = { FlowStyleChips(listOf("image" to "纯图片封面", "card" to "拍立得卡片"), gridCardStyle, onPickGridCardStyle) }))
        }
        homeTabs.forEach { (name, on) ->
            add(SettingItem(SettingsPage.HOME, "顶部胶囊入口", "显示$name", null, icon(GuangYueIcons.LayoutGrid),
                trailing = { FoliaToggle(checked = on, onChange = { v -> onToggleHomeTab(name, v) }) }))
        }
        if (rememberHomeCard != null) {
            add(SettingItem(SettingsPage.HOME, "记住主页卡片位置", "记住主页卡片位置", "切换顶部入口或从播放界面返回时，回到各入口上次浏览的卡片。位置仅在本次打开应用期间保留。", icon(GuangYueIcons.Pin),
                trailing = { FoliaToggle(checked = rememberHomeCard, onChange = onRememberHomeCard) }))
        }
        if (gridCardShape != null || gridFalloff != null) {
            // Folia HomePage 网格卡片组的 preview：GridCardsPreview（七张卡，衰减照 computeHexCardFrame）。
            val (pvBleed, pvSquare) = gridCardShape ?: (false to false)
            val (pvScale, pvOpacity) = gridFalloff ?: (0.45f to 0.4f)
            add(SettingItem(SettingsPage.HOME, "网格卡片", "", null, null,
                below = { GridCardsPreview(pvScale, pvOpacity, pvBleed, pvSquare) }))
        }
        if (gridCardShape != null) {
            val (bleed, square) = gridCardShape
            add(SettingItem(SettingsPage.HOME, "网格卡片", "全画幅封面", "让封面图铺满整张网格卡片，歌名和歌手改为叠在封面上的渐变遮罩里显示，做法和队列拼贴一致。", icon(GuangYueIcons.Images),
                trailing = { FoliaToggle(checked = bleed, onChange = { onGridCardShape(it, square) }) }))
            // Folia：正方形只在全画幅打开后才有意义（Reveal when fullBleedCover）。
            if (bleed) {
                add(SettingItem(SettingsPage.HOME, "网格卡片", "正方形卡片", "让卡片高宽相等，方形封面完整显示，不再被上下裁掉。卡片面积保持不变，变矮的同时会变宽，网格间距同步调整。", icon(GuangYueIcons.Square),
                    trailing = { FoliaToggle(checked = square, onChange = { onGridCardShape(bleed, it) }) }))
            }
        }
        if (gridFalloff != null) {
            // Folia HomePage「网格卡片」组（options.gridViewMinCardScale / gridViewMinCardOpacity，hexCardFalloff 范围与默认），作用于歌单详情的集合网格。
            val (minScale, minOpacity) = gridFalloff
            add(SettingItem(SettingsPage.HOME, "网格卡片", "卡片最小尺寸", "远离中心的卡片最多缩小到多少。调高外围更好辨认，调低纵深感更强。", icon(GuangYueIcons.Grid2x2),
                below = { PercentSlider(minScale, 0.2f..1.1f) { onGridFalloff(it, minOpacity) } }))
            add(SettingItem(SettingsPage.HOME, "网格卡片", "卡片最小透明度", "远离中心的卡片最多淡到多少。调高外围更清楚，调低视线更集中在中心。", icon(GuangYueIcons.Blend),
                below = { PercentSlider(minOpacity, 0f..1f) { onGridFalloff(minScale, it) } }))
            if (minScale != 0.45f || minOpacity != 0.4f) {
                add(SettingItem(SettingsPage.HOME, "网格卡片", "恢复默认衰减", null, icon(GuangYueIcons.RotateCcw),
                    onClick = { onGridFalloff(0.45f, 0.4f) }))
            }
        }
        if (ponderTouchButton != null) {
            // Folia GeneralPage 的「思索」组（options.ponderTouchButton / ponderTouchButtonDesc），在「通用」页。
            add(SettingItem(SettingsPage.GENERAL, "思索", "触屏思索按钮", "触屏设备右上角的灯泡。", icon(GuangYueIcons.Lightbulb),
                trailing = { FoliaToggle(checked = ponderTouchButton, onChange = onPonderTouchButton) }))
        }
        if (playbackEntryView != null) {
            add(SettingItem(SettingsPage.PLAYER, "播放后进入的视图", "播放后进入的视图", "点击播放后默认打开哪个视图。", icon(GuangYueIcons.Play),
                below = { FlowStyleChips(listOf("player" to "可视化", "stay" to "留在原处"), playbackEntryView, onPickPlaybackEntryView) }))
        }
        if (lyricStyles.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYER, "歌词动画", "歌词样式", "播放舞台上的歌词画法；各样式的参数在舞台的样式弹层里调", icon(GuangYueIcons.Sparkles),
                below = {
                    FlowStyleChips(lyricStyles, lyricStyle, onPickLyricStyle)
                }))
        }
        if (backgrounds.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYER, "背景", "背景类型", "歌词舞台的背景：隐现是封面色流体叠像素点阵（需 Android 13+），通用是流体加几何图形", icon(GuangYueIcons.Sparkles),
                below = { FlowStyleChips(backgrounds, background, onPickBackground) }))
        }
        machiChoices.forEach { c ->
            add(SettingItem(SettingsPage.PLAYER, "町景", c.title, c.desc, icon(GuangYueIcons.Aperture),
                below = { FlowStyleChips(c.options, c.selected, c.onPick) }))
        }
        if (motion != null) {
            // Folia GraphicsPage：「性能与背景」组（options.labPerformanceSection）。
            add(SettingItem(SettingsPage.GRAPHICS, "性能与背景", "静态模式", "禁用几何背景以节省资源", icon(GuangYueIcons.Gauge),
                trailing = { FoliaToggle(checked = motion.staticMode, onChange = motion.onStaticMode) }))
            add(SettingItem(SettingsPage.GRAPHICS, "性能与背景", "关闭主页动态背景", "主页不再播放背景动画，降低 GPU 占用", icon(GuangYueIcons.Images),
                trailing = { FoliaToggle(checked = motion.homeBackgroundOff, onChange = motion.onHomeBackgroundOff) }))
            // Folia「降低动态效果」组（options.reduceMotionSection*）。各分区开关（队列拼贴 / 混音过渡 / 歌单展开转场 / Monet 背景漂移 / 界面微动效 / 设置页平滑滚动）广乐没有对应动效，不列。
            add(SettingItem(SettingsPage.GRAPHICS, "降低动态效果", "跟随系统设置",
                "由系统的动画设置决定全部动效" + if (motion.followSystem && motion.systemReducing) "。系统正在要求减少动态效果，播放页与主页的动效都已降低" else "",
                icon(GuangYueIcons.Sparkles),
                trailing = { FoliaToggle(checked = motion.followSystem, onChange = motion.onFollowSystem) }))
        }
        if (globalLyricOffsetMs != null) {
            // Folia LyricsPage「时间与过滤」组 → 全局时间偏移（GlobalLyricOffsetModal 的 ±50 / ±10 步进，±2000ms）。
            add(SettingItem(SettingsPage.LYRICS, "时间与过滤", "全局时间偏移", "整体平移歌词时间轴，补偿蓝牙等输出延迟。", icon(GuangYueIcons.Timer),
                trailing = { Text("${if (globalLyricOffsetMs > 0) "+" else ""}$globalLyricOffsetMs ms", color = Gyp.t2, fontSize = 13.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")) },
                below = { OffsetStepper(globalLyricOffsetMs, onGlobalLyricOffset) }))
        }
        if (lyricFilter != null) {
            // Folia LyricFilterSettingsModal 的「过滤规则」：常用预设两条 + 按文字添加（包含）+ 手写规则（高级）。
            add(SettingItem(SettingsPage.LYRICS, "歌词过滤", "版权声明", "如「未经许可不得翻唱」「版权所有」", icon(GuangYueIcons.Filter),
                trailing = { FoliaToggle(checked = lyricFilter.copyright, onChange = lyricFilter.onCopyright) }))
            add(SettingItem(SettingsPage.LYRICS, "歌词过滤", "歌词来源说明", "如「歌词贡献者」「翻译贡献者」", icon(GuangYueIcons.Filter),
                trailing = { FoliaToggle(checked = lyricFilter.lyricSource, onChange = lyricFilter.onLyricSource) }))
            add(SettingItem(SettingsPage.LYRICS, "歌词过滤", "包含这些文字的行", "多个文字用逗号或顿号隔开，含其中任一个的行会被删掉（不分大小写、全半角）。", icon(GuangYueIcons.Filter),
                below = { FoliaTextField(value = lyricFilter.words, onValueChange = lyricFilter.onWords, placeholder = "输入文字", modifier = Modifier.fillMaxWidth()) }))
            add(SettingItem(SettingsPage.LYRICS, "歌词过滤", "手写规则（高级）", lyricFilter.writtenError?.let { "规则格式有误，未删除任何行：$it" } ?: "输入正则表达式，命中的行会被删掉。示例：^(作词|作曲)\\s*[:：]", icon(GuangYueIcons.Filter),
                below = { FoliaTextField(value = lyricFilter.written, onValueChange = lyricFilter.onWritten, placeholder = "输入正则表达式", modifier = Modifier.fillMaxWidth()) }))
        }
        if (playQualities.isNotEmpty()) {
            add(SettingItem(SettingsPage.PLAYBACK, "音质", "默认播放音质", "新开始播放的歌按这一档取链；音源没有这一档时向下取", icon(GuangYueIcons.AudioLines),
                below = { FlowStyleChips(playQualities, playQuality, onPickPlayQuality) }))
        }
        // 11-18：取链源、音源插件照 Folia 归到「连接与集成」（Folia 外接音乐服务 Navidrome 等都在这一页）。
        add(SettingItem(SettingsPage.INTEGRATION, "取链源", "取链源默认音质", "插件取不到播放地址时，按这里选的音质问取链源；播放器切到无损就按无损问", icon(GuangYueIcons.Route),
            below = { FoliaSegmented(value = lxQuality, options = LxQualityOptions, onSelect = onPickLxQuality) }))
        if (onOpenPlugins != null) {
            add(SettingItem(SettingsPage.INTEGRATION, "音源", "音源插件管理", "装音源、选当前音源、看体检结果", icon(GuangYueIcons.Puzzle), trailing = chevron, onClick = onOpenPlugins))
        }
        if (onOpenDownloads != null) {
            add(SettingItem(SettingsPage.STORAGE, "下载", "下载设置", "下载音质、歌词、同时下载数、存储与暂存", icon(GuangYueIcons.Download), trailing = chevron, onClick = onOpenDownloads))
        }
        add(SettingItem(SettingsPage.ABOUT, "关于", "版本", "广乐 $versionName", icon(GuangYueIcons.Info)))
        add(SettingItem(SettingsPage.ABOUT, "关于", "开源许可", "AGPL-3.0 · 界面改写自 Folia", icon(GuangYueIcons.FileText), trailing = chevron, onClick = onOpenLicenses))
    }
    val pages = SettingsPage.entries.filter { p -> items.any { it.page == p } }
    val q = query.trim()
    val shown = if (q.isEmpty()) items.filter { it.page == page }
    else items.filter { it.label.contains(q, true) || (it.hint?.contains(q, true) == true) || it.group.contains(q, true) || it.page.label.contains(q, true) }

    // 2026-11-15 用户：设置页不像 Folia。照 Folia SettingsModal 手机版：圆角大窗、「帮助 / 选项」大页签、
    // 带图标的分类胶囊、页头（标题 + 说明）、带图标的分组小标题、分组卡片里的行（标题 + 说明 + 右侧控件）。
    FoliaSettingsShell(
        items = items,
        pages = pages.filter { it != SettingsPage.ABOUT },
        page = if (page == SettingsPage.ABOUT) SettingsPage.APPEARANCE else page,
        onPage = { page = it },
        onClose = onBack,
        modifier = modifier,
        version = versionName,
        startOnOptions = initialPage != SettingsPage.APPEARANCE,
    )
}

/** 设置分类（Folia settingsNavModel 的手机子集，顺序与名字照它：外观/播放页/主页与歌单/播放控制/通用/存储与备份，zh-CN options.settingsPage*）。 */
enum class SettingsPage(val label: String, val desc: String = "") {
    APPEARANCE("外观", "主题与深浅色。"),
    PLAYER("播放页", "播放后进入的视图、歌词动画、背景与町景。"),
    HOME("主页与歌单", "资料库界面、卡片样式、顶部胶囊入口与卡片位置。"),
    GRAPHICS("动画与性能", "背景动画与动态效果。"),
    PLAYBACK("播放控制", "默认播放音质与播放行为。"),
    LYRICS("歌词", "歌词时间轴。"),
    GENERAL("通用", "启动与思索等通用行为。"),
    INTEGRATION("连接与集成", "音源插件与取链源。"),
    STORAGE("存储与备份", "下载与本机存储。"),
    ABOUT("关于"),
}

internal class SettingItem(
    val page: SettingsPage,
    val group: String,
    val label: String,
    val hint: String?,
    val leading: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val below: (@Composable () -> Unit)? = null,
)

/**
 * 选项多于分段放得下时用可换行的小胶囊（Folia kit/Chips.tsx）：每颗都是同一层弱填充（--st-fill），
 * 选没选看左边 16dp 的小方框——选中是强调色实心 + 对勾，未选是 1.5dp 描边。
 * （以前选中项用 --st-raise 白底，浅色模式白卡上看着像「没选」，未选的灰底反而像选中。）
 */
@Composable
private fun FlowStyleChips(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (id, name) ->
            val on = id == current
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gyp.tint)
                    .clickableNoRipple { if (!on) onPick(id) }
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .then(
                            if (on) Modifier.background(Gyp.accent)
                            else Modifier.border(1.5.dp, Gyp.t3, RoundedCornerShape(5.dp)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(GuangYueIcons.Check, null, tint = Gyp.onAccent, modifier = Modifier.size(12.dp))
                }
                Text(
                    name,
                    fontSize = 13.sp,
                    color = if (on) Gyp.ink else Gyp.t2,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

/** 取链源的四档音质（洛雪 LX 的 `info.type`）。 */
private val LxQualityOptions = listOf(
    "128k" to "128k",
    "320k" to "320k",
    "flac" to "无损",
    "flac24bit" to "Hi-Res",
)


/** 开源许可：GPL 要求的署名、Folia 致谢、字体许可与第三方组件。 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "开源许可", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GySpace.m),
            ) {
                item(key = "guangyue") {
                    LicenseBlock(
                        title = "广乐 GuangYue",
                        body = "Copyright (C) 2026 GuangYue contributors\n" +
                            "本程序以 GNU Affero General Public License v3.0 发布，源码见项目仓库。",
                    )
                }
                item(key = "folia") {
                    LicenseBlock(
                        title = "Folia · AGPL-3.0",
                        body = "本程序的界面层（布局、配色、字号、间距、图标与交互动效）改写自 Folia，" +
                            "Folia 以 AGPL-3.0 授权，其原始版权归 Folia 贡献者所有。\n" +
                            "原始项目：https://github.com/folia-music/folia\n" +
                            "改写方式：React/CSS 组件 → Jetpack Compose，保留视觉与交互设计。",
                    )
                }
                item(key = "lucide") {
                    LicenseBlock(
                        title = "Lucide 图标 · ISC",
                        body = "界面图标取自 Lucide（https://lucide.dev），以 ISC 许可发布，\n" +
                            "Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 " +
                            "as part of Feather (MIT). All other copyright (c) for Lucide are held by " +
                            "Lucide Contributors 2022.",
                    )
                }
                item(key = "fonts") {
                    LicenseBlock(
                        title = "Noto Serif SC · SIL OFL 1.1",
                        body = "标题所用衬线字体为 Noto Serif SC（子集，仅含界面用到的字形），" +
                            "以 SIL Open Font License 1.1 发布，\n" +
                            "Copyright 2012 Google Inc. All Rights Reserved.\n" +
                            "许可全文见 https://scripts.sil.org/OFL",
                    )
                }
                item(key = "oss") {
                    LicenseBlock(
                        title = "第三方组件",
                        body = "Jetpack Compose / Media3 / Hilt / Room / Coil / OkHttp / " +
                            "QuickJS-Android / kotlinx.serialization / jaudiotagger。" +
                            "各自许可见其发行说明。",
                    )
                }
                item(key = "agpl") {
                    LicenseBlock(
                        title = "AGPL-3.0 摘要",
                        body = "你可以自由使用、修改、再发布本程序，但必须保持同样的 AGPL-3.0 授权、" +
                            "保留版权声明，并在通过网络提供服务时向用户提供完整源码。",
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foliaCardSurface()
            .padding(GySpace.l),
    ) {
        Text(
            text = title,
            fontSize = GyText.row,
            lineHeight = GyText.rowHeight,
            fontWeight = FontWeight.SemiBold,
            color = Gyp.ink,
            fontFamily = GuangYueDesign.Sans,
        )
        Spacer(Modifier.height(GySpace.xs))
        Text(
            text = body,
            fontSize = GyText.hint,
            lineHeight = GyText.hintHeight,
            color = Gyp.t2,
            fontFamily = GuangYueDesign.Sans,
        )
    }
}

/** 「关于」页：版本、致谢与仓库地址。 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().marbleBackground()) {
        Column(Modifier.fillMaxSize()) {
            FoliaTopBar(title = "关于广乐", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GySpace.pageH),
                verticalArrangement = Arrangement.spacedBy(GySpace.m),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GyRadius.card))
                        .background(Gyp.tint)
                        .padding(GySpace.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GySpace.m),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(GyRadius.field))
                            .background(Gyp.raise),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            GuangYueIcons.AudioLines,
                            null,
                            tint = Gyp.ink,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "广乐 GuangYue",
                            fontSize = GyText.emptyTitle,
                            color = Gyp.ink,
                            fontFamily = GuangYueDesign.Serif,
                        )
                        Text(
                            text = "0.0.6 · 原生 Compose",
                            fontSize = GyText.hint,
                            lineHeight = GyText.hintHeight,
                            color = Gyp.t2,
                            fontFamily = GuangYueDesign.Sans,
                        )
                    }
                }
                Text(
                    text = "一个以插件为音源的安卓音乐播放器。界面逐项对照 Folia 改写为 Jetpack Compose，" +
                        "不含 WebView；感谢 Folia、Lucide 与 Noto 字体的作者。",
                    fontSize = GyText.label,
                    lineHeight = 20.sp,
                    color = Gyp.t2,
                    fontFamily = GuangYueDesign.Sans,
                )
            }
        }
    }
}

/** 0.0.27：资料库界面的一个选项（Folia librarySuiteOnboarding 的卡片：名字 + 小标签 + 一句说明）。 */
data class HomeStyleOption(val id: String, val title: String, val tag: String, val description: String)

/** 资料库界面选择：每项一整行（≥56dp 触控），选中项带底色与对勾。 */
@Composable
private fun HomeStylePicker(options: List<HomeStyleOption>, current: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val on = o.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .androidxHeightInMin56()
                    .clip(RoundedCornerShape(GyRadius.field))
                    // 选中项弱填充 + 对勾，未选透明：浅色模式卡片本身是白的，用 --st-raise 白底标选中会看反。
                    .background(if (on) Gyp.tint else androidx.compose.ui.graphics.Color.Transparent)
                    .clickableNoRipple { if (!on) onPick(o.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = Gyp.ink)
                        Text(
                            o.tag, fontSize = 12.sp, color = Gyp.t2,
                            modifier = Modifier.padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Gyp.tint)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Text(o.description, fontSize = 12.sp, color = Gyp.t2, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (on) Icon(GuangYueIcons.Check, "已选", tint = Gyp.ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun Modifier.androidxHeightInMin56(): Modifier = this.then(Modifier.heightIn(min = 56.dp))


/** 设置页一行「标题 + 说明 + 一排选项」。 */
data class SettingChoice(
    val title: String,
    val desc: String,
    val options: List<Pair<String, String>>,
    val selected: String,
    val onPick: (String) -> Unit,
)

/** Folia kit/Slider：步长 0.01，右侧百分比读数（tnum）。 */
@Composable
private fun PercentSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { v -> onChange(Math.round(v * 100f) / 100f) },
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Gyp.ink,
                activeTrackColor = Gyp.accent,
                inactiveTrackColor = Gyp.ink.copy(alpha = 0.15f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${Math.round(value * 100f)}%",
            color = Gyp.t2,
            fontSize = 13.sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 44.dp),
        )
    }
}


/** Folia「动画与性能」页广乐能落地的三项（值 + 回调）。 */
class MotionSettingsUi(
    val staticMode: Boolean,
    val onStaticMode: (Boolean) -> Unit,
    val homeBackgroundOff: Boolean,
    val onHomeBackgroundOff: (Boolean) -> Unit,
    val followSystem: Boolean,
    val onFollowSystem: (Boolean) -> Unit,
    /** 系统此刻是否要求减少动态效果（Folia reduceMotionForcedNotice）。 */
    val systemReducing: Boolean = false,
)

/** 全局时间偏移步进（Folia GlobalLyricOffsetModal：-50 / -10 … +10 / +50，另有归零）。 */
@Composable
private fun OffsetStepper(value: Long, onChange: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(-50L, -10L, 10L, 50L).forEach { step ->
            StepChip(if (step > 0) "+$step" else "$step") { onChange((value + step).coerceIn(-2000L, 2000L)) }
        }
        if (value != 0L) StepChip("归零") { onChange(0L) }
    }
}

@Composable
private fun StepChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Gyp.ink.copy(alpha = 0.06f))
            .border(1.dp, Gyp.ink.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Gyp.ink.copy(alpha = 0.85f), fontSize = 13.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}


/** Folia「歌词过滤」手机子集的值与回调（core/data LyricLineRules）。 */
class LyricFilterUi(
    val copyright: Boolean,
    val onCopyright: (Boolean) -> Unit,
    val lyricSource: Boolean,
    val onLyricSource: (Boolean) -> Unit,
    val words: String,
    val onWords: (String) -> Unit,
    val written: String,
    val onWritten: (String) -> Unit,
    val writtenError: String? = null,
)
