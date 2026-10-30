# U5 歌词页（Compose）

基线 `03727a5`（U4 全屏播放页），分支 `port/u2`。目标：把 Folia 歌词体验搬成 Compose 歌词视图，
替换播放页那颗「歌词」按键原先的「歌词功能即将上线」小字占位。

> 目录说明：本目录 `< 2026-10-31 07:40` 的 `u2-*` / `u3-*` / `u4-*` 是 U2/U3/U4 用例的**副产物**——
> `app/build.gradle.kts` 的 `roborazzi.output.dir` 只有一个全局坐标，U5 把它指到了 `docs/ui-u5/`，
> 于是老用例重跑时也写进这里。已排除出提交；旧的、权威的一份仍在 `docs/ui-u2|u3|u4/`。
> 本任务产物是下面的 **`u5-*.png`（13 张）**。

## 一、做了什么

### 1. 入口与替换占位

- 播放页第 4 颗动作键「歌词」（`GuangYueIcons.Captions` → `onOpenLyrics`）在 U4 已接到
  `Routes.LYRIC_FLOW`，U5 直接把它做成真页面，无需改导航图。
- `FoliaPlayerPage.kt` 里那行 10sp 的 `str(res.lyricsSoon)` 小字占位整块删除（换成弹性空白），
  文案键 `player_lyrics_soon` 不再被引用（`PlayerRoute.kt` 里 `lyricsSoon` 暂时映射到
  `player_lyrics`，保持 `PlayerStrings` 数据类形状不变，避免牵动 U4 的 11 个截图用例）。

### 2. 歌词页 `feature/player/.../lyrics/LyricFlowScreen.kt`（整体重写，旧「歌词流」页退役）

从上到下三块：

| 区域 | 内容 |
| --- | --- |
| 顶栏 | 48dp 返回圆钮（`ChevronLeft`）+ 标题「歌词」+ 副标题「歌手 · 来源」（缺一侧就只显示另一侧） |
| 列表 | `LazyColumn` 逐行歌词：当前句 32sp 强调色、上下各按距离递减；点任意一行 seek 到那句 |
| 底部胶囊 | 播放/暂停键 + 2dp 进度轨 + 两端等宽时间（与播放页 `PlaybackPill` 同观感） |

逐行衰减档（沿用旧 `LyricFlowLine`，与 Folia `LatticeLyrics` 的视觉层级一致）：

| 距离 | 字号 | 不透明度 | 模糊 |
| --- | --- | --- | --- |
| 0（正在唱） | 32sp | 1.00 | 0（强调色 0.92） |
| 1 | 26sp | 0.34 | 0.6dp |
| 2 | 22sp | 0.20 | 1.6dp |
| 3 | 19sp | 0.12 | 2.6dp |
| ≥4 / 还没开始唱 | 17sp | 0.07 | 3.6dp（`canBlur()` 门控） |

行为：

- **自动跟随**：`LaunchedEffect(currentIndex)` → `animateScrollToItem`，当前句始终在列表竖直中线
  附近（上下各留 32% 列表高度的空白，用 `onSizeChanged` 量列表自身高度，不拿整屏高度）。
- **手动浏览**：手指一拖就 `onManualScroll()`，交给既有 `LyricSynchronizer` 记 3 秒，之后自动回跟。
- **点行跳转**：整行 48dp 高的点击区 → `onSeekToLine(index)` → `LyricsViewModel.seekToLine` +
  `resumeAutoScroll`。点到空壳行（`[00:12.00]` 那种只有时间没字的间奏行）会往前挪到最近的真词。
- **译文**：只在当前句下方露出一行（避免整屏双语糊在一起）。

### 3. 中文空态（三种，全部有截图）

| 状态 | 主句 | 副句 | 文案来源 |
| --- | --- | --- | --- |
| 加载中 | 正在匹配最佳歌词… | — | `zh-CN.ts` 354 `matchingBestLyrics` |
| 无歌词 | 无可用歌词 | 换一个音源或稍后再试 | `zh-CN.ts` 890 `noLyricsAvailable`（副句自拟） |
| 纯音乐 | 纯音乐，无需匹配歌词 | —（终态，不挂「换音源」提示） | `zh-CN.ts` 358 `bestLyricsPureMusic` |
| 失败 | 匹配最佳歌词失败 | 优先显示数据源真实原因（如「歌词获取失败：…」），无则「稍后再试，或在设置里换一个音源」 | `zh-CN.ts` 361 `bestLyricsMatchFailed`（副句自拟） |

判定优先级：加载中 > 失败 > 有歌词（有歌词就画列表，旧 message 不遮内容）。

### 4. 数据源

**接的是既有真实数据源，没有造假数据**：`LyricsViewModel`（插件 `bridge.getLyric` / `QueueItem.rawJson`
内嵌 LRC → `LrcParser.parse` → `mergeTranslation`）+ `LyricSynchronizer`（逐行状态机）。
`NavHost.kt` 的 `LYRIC_FLOW` 路由把这些 StateFlow 直接喂给新页面。
唯一新增的接口是 UI 侧的三个回调（`onSeekToLine` / `onManualScroll` / `onTogglePlay`），
以及空态的重试按钮插槽（`onRetry`，当前外壳不传——重试要真打音源，归后续接线）。

### 5. 新增/改动文件

```
新增  feature/player/src/main/java/.../lyrics/LyricStrings.kt       文案包（Int 资源 id）+ 三态/纯音乐/副句/副标题纯函数
新增  feature/player/src/main/java/.../lyrics/LyricRow.kt           行距离档 + 点行跳转落点
重写  feature/player/src/main/java/.../lyrics/LyricFlowScreen.kt    歌词页本体（旧 367 行 → 496 行）
改    feature/player/src/main/java/.../screen/FoliaPlayerPage.kt     删「歌词功能即将上线」占位（3+/9-）
改    app/src/main/java/.../nav/PlayerRoute.kt                      lyricsSoon 改指 player_lyrics；新增 lyricStrings()
改    app/src/main/java/.../nav/NavHost.kt                          LYRIC_FLOW 接真数据 + 注入 LyricStringHost
改    app/src/main/res/values/strings.xml                           新增「===== U5 歌词页 =====」13 键（全中文，逐条标行号）
新增  feature/player/src/test/java/.../lyrics/LyricFlowScreenTest.kt 17 用例
新增  app/src/test/java/.../nav/U5LyricsScreenshotTest.kt            13 张截图用例
改    app/src/test/java/.../nav/ShellNavTest.kt                       +2 用例（歌词页返回/底栏）
改    app/build.gradle.kts                                          roborazzi.output.dir → docs/ui-u5
```

### 6. 手机适配

- **320dp**：`u5-320dp-light.png` / `u5-320dp-dark-font130.png`——长句自然换行，底部胶囊时间不被挤掉；
  顶栏标题与副标题单行省略。整页不写死宽度，全部走 `fillMaxWidth` + `GySpace.pageH` 边距。
- **字体 1.3**：`u5-font130-light.png` / 上面那张窄屏深色——当前句 32sp×1.3 ≈ 41.6sp 仍不裁切。
- **深浅色**：同一套 `Gyp` 令牌 + `playerBackdrop` 封面渐变，深浅各有一组截图；墨色由 `backdropUsesDarkInk` 判定。
- 触控目标：返回钮 48dp、播放键 48dp、歌词行整行点击区 ≥48dp。

## 二、验证结果

### 单测

```
.\gradlew.bat testDebugUnitTest      → 303 tests, 0 failures, 0 errors, 2 skipped
```

- 基线 271/0/2（U4 记录）→ 本次 **303/0/2**，净增 32：U5 截图 13 + `LyricFlowScreenTest` 17 + ShellNav 2。
- 2 个 skip 与基线一致（`PluginUsableReportTest` / `PluginHealthReportTest` 的环境型 `assumeTrue`）。

### 截图

```
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat :app:testDebugUnitTest --tests "*U5LyricsScreenshotTest*"
# → docs/ui-u5/u5-*.png（360×800dp/xhdpi = 720×1600px；320dp 用例为 640×1600px）
```

| 文件 | 内容 |
| --- | --- |
| `u5-lines-light.png` / `u5-lines-dark.png` | 逐行高亮主场景，当前句强调色 + 上下递减（第 5 句带译文） |
| `u5-before-first-line-light.png` | 还没唱到第一句（index = -1）：全行同档，不做假高亮 |
| `u5-loading-light.png` | 加载中空态 |
| `u5-no-lyrics-light.png` / `u5-no-lyrics-dark.png` | 无歌词空态（浅/深） |
| `u5-pure-music-light.png` | 纯音乐空态 |
| `u5-failed-light.png` | 失败空态，副句显示数据源原因 |
| `u5-long-lyrics-light.png` / `u5-long-lyrics-dark.png` | 26 行长歌词，首屏带上下对称空白、长句换行 |
| `u5-320dp-light.png` | 320dp 窄屏 |
| `u5-font130-light.png` | 字体 1.3 倍 |
| `u5-320dp-dark-font130.png` | 320dp + 深色 + 1.3 倍三重约束叠加 |

## 三、与 Folia 的对应关系

| Folia | 这里 |
| --- | --- |
| `LatticeLyrics.tsx`（逐行渲染 + 当前句放大 + 距离衰减） | `LyricLineRow`，衰减档沿用旧 `LyricFlowLine` |
| `zh-CN.ts:2124 lyrics` 「歌词」 | `lyrics_title` |
| `zh-CN.ts:354 matchingBestLyrics` 「正在匹配最佳歌词...」 | `lyrics_loading`（省略号改全角「…」） |
| `zh-CN.ts:890 noLyricsAvailable` 「无可用歌词」 | `lyrics_empty` |
| `zh-CN.ts:358 bestLyricsPureMusic` 「纯音乐，无需匹配歌词」 | `lyrics_pure_music` |
| `zh-CN.ts:361 bestLyricsMatchFailed` 「匹配最佳歌词失败」 | `lyrics_load_failed` |
| `zh-CN.ts:3869 statusNone` 「无」 | `lyrics_source_none` |
| `SourceTab.tsx` 的歌词来源行 | 顶栏副标题「歌手 · 来源」 |
| `LyricTimelineOffsetControl`（时间轴偏移） | 未搬：属面板设置项，不在本次全屏歌词视图范围 |
| `OnlineLyricsTab` 的「在线匹配 / 导入」 | 未搬：需要打音源与文件选择器，留给后续 |

## 四、已知取舍 / 未做

1. **时间轴偏移、在线匹配、导入导出歌词** 未搬（属播放面板的歌词 tab 设置项，不是全屏歌词视图）。
   后续若要做，落点是播放面板 tab，不是本页。
2. 空态不给「重试」按钮：重试要真去请求音源，需外壳接线（`LyricFlowScreen(onRetry)` 插槽已留）。
3. 点行跳转对空壳行的处理是「往前挪到最近真词」；若整段都是空行，停留在点击行。
4. 无模拟器，全部验证走 Robolectric + Roborazzi；真机上还需确认插件返回的 `origin.pluginId`
   在副标题里显示成中文源名（当前直接显示 pluginId，例如 `netease`）——这是 U5 唯一还没对齐的细节。
