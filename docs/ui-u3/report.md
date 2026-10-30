# U3 · 搜索页（Folia 样式）移植报告

- **worktree / 分支**：`D:\workspaces\GuangYue-u2` @ `port/u2`，基线 `f4754d0`（U2 外壳）
- **范围**：只做「搜索」Tab 根页；播放器、插件页、洛雪取链等其它部分未动
- **全程未使用 adb / 模拟器**，截图全部来自 Roborazzi（Robolectric + NATIVE 图形）

---

## 1. 做了什么

1. **把「搜索」Tab 根页换成 Folia 搜索页布局**：顶部一行胶囊搜索框（48dp，左侧放大镜 / 加载中转圈，右侧 48dp 清除键）→ 音源胶囊横排（选中 = accent 实心）→ 分类 Tab（该音源支持多类时）→ 结果列表 / 状态页。旧页面顶部那个 20sp「搜索」大标题去掉了，换成 Folia 的一行搜索框。
2. **输入即搜**：400ms 防抖 + 取消上一次请求。文本变化后 400ms 才发；期间再敲一个字就丢弃上一次；清空输入立刻取消在途请求并回到初始态；点推荐词 / 软键盘回车走「立刻搜」路径（不等 400ms）。
3. **明确的状态机**：`NoSource / Idle / Loading / Results / Empty / Failure` 六个状态，判断顺序照 Folia `SearchWorkspace.tsx:44-120`（先加载、再「有错且无结果」、再有结果、再空）。
   - 初始：搜索历史 chips + 推荐搜索 chips
   - 加载中：转圈 + 3 行骨架
   - 空结果：`未找到相关结果` + 一句建议 + 可点推荐词
   - 失败：AlertCircle + 中文原因 + 「重试」按钮（沿用旧页面 `vm.retry()`：清该插件缓存后真重发）
4. **音源多源切换保留入口**：顶部音源胶囊仍是单选（0.0.7 起「只用用户选中的那一个音源」的规矩没变），点一下就换源并重搜，`prefs.lastSourceId` 仍被记住；一个音源都没装时给空态并补了一个「去音源插件管理」按钮（旧页面只有一句话，没有入口）。
5. **播放 / 下载 / 加入歌单全部保留**：点结果行 → `vm.playFrom(index)` → `playback.setQueue(...)` → 跳全屏播放页，与 U2 之前完全一致；行右侧 48dp 的「加入歌单」「下载」常驻（Folia 桌面是 hover 才出的 ＋，触屏改成常驻）。
6. **键盘让位**：页面根容器继续 `imePadding()`；迷你条与底栏由 `ShellColumn` 的 `Modifier.collapseOnIme()` 在键盘弹起时整条让位（U2 已有），因此键盘不会顶起底栏、也不会遮住结果。
7. **触控与长标题**：行最小高度 68dp，右侧按钮 48dp 见方，清除键 48dp，音源胶囊触控区 48dp 高（视觉胶囊 36dp）；中文长标题 `maxLines = 2` + 省略号（Folia 桌面是单行 truncate，手机放宽到两行）。
8. **文案全部收进 `strings.xml`**：旧页面里硬编码的中文（「搜索歌曲、歌手、专辑」「搜索历史」「重试」等 14 条）全部进资源，页面里一个中文字面量都不剩（唯一例外见 §7）。

---

## 2. 文件

### 新增
| 文件 | 内容 |
|---|---|
| `feature/search/src/main/java/com/guangyue/feature/search/SearchPageState.kt` | 状态机 `SearchPagePhase` + `SearchUiState.pagePhase()`；`SEARCH_DEBOUNCE_MS = 400`；`Debouncer` / `DebounceScheduler`；`shouldSearchOnInput()` |
| `feature/search/src/main/java/com/guangyue/feature/search/FoliaSearchScreen.kt` | 新搜索页（约 930 行）：`FoliaSearchScreen(...)` 入口、`SearchHeader` / `SourceRail` / `ResultsBody` / `TrackList` / `ResultCard` / `AlbumGrid` / `ArtistGrid` / `IdleBody` / `LoadingState` / `EmptyState` / `FailureState` / `NoSourceState` / `FailureBanner` / `Spinner` / `Cover`；`object SearchTags`（测试锚点） |
| `feature/search/src/main/java/com/guangyue/feature/search/SearchStrings.kt` | `data class SearchStrings`：feature 模块没有 `R`，用资源 id 传递，app 层 `stringResource` 取值 |
| `feature/search/src/main/java/com/guangyue/feature/search/SearchTab.kt` | `enum class SearchTab` 从旧 `SearchScreen.kt` 抽出（模型层仍要用） |
| `feature/search/src/test/java/com/guangyue/feature/search/SearchPageStateTest.kt` | 13 个新单测：状态机 7 个 + 防抖 6 个 |
| `app/src/test/java/com/guangyue/music/ui/search/U3SearchScreenshotTest.kt` | 14 张 Roborazzi 截图 |
| `tools/u3_side_by_side.py` | 并排图脚本（PIL，基准裁切框写死，换基准需改常量） |

### 修改
| 文件 | 改动 |
|---|---|
| `feature/search/.../SearchViewModel.kt` | `onQueryChange` 加防抖（新输入取消上一次）；新增 `searchNow()` / `clearQuery()`；`selectSource` / `retry` / `onTabChange` / `openAlbum` / `openArtist` / `forgetLoaded` 处 `debounce.cancel()` |
| `app/src/main/java/com/guangyue/music/nav/NavHost.kt` | SEARCH 分支改调 `FoliaSearchScreen`；新增 `internal fun searchStrings()` 把 14 条资源 id 注入 feature 层；新增 `onOpenSourceManager = { navigate(Routes.PLUGINS) }` |
| `app/src/main/res/values/strings.xml` | 新增 U3 段 14 条（每条注释标明取自 zh-CN.ts 哪个键或「自拟」） |
| `app/build.gradle.kts` | `roborazzi.output.dir`：`docs/ui-u2` → `docs/ui-u3` |
| `app/src/test/.../ScreenshotTest.kt`、`.../shell/U2ShellScreenshotTest.kt` | 旧截图测试改调新页面（仅修编译，不改断言） |

### 删除
`feature/search/src/main/java/com/guangyue/feature/search/SearchScreen.kt`（642 行旧搜索页，被 `FoliaSearchScreen.kt` 取代；其中的 `SearchTab` / `TabCounts.of` / `asMusicItem` 已迁到新文件，`FailureBanner` 改为新签名）

---

## 3. Folia 原值 → 手机值

基准：`docs/port/baseline/screenshots/search-results-m412-light.png`（CSS 412×915 @2x，即 824×1830 位图）、`screenshots-v2/m412-*-search-typing.png`。

| 位置 | Folia（桌面 / 412 CSS） | 广乐手机（360dp） | 依据 |
|---|---|---|---|
| 搜索框 | `rounded-2xl`（16px）+ `py-3.5`，约 60dp 高 | **48dp 高、全圆胶囊** | U1 `FoliaSearchField` 已定 48dp；Folia 412 档实测 60dp，48dp 是 Material 最小触控高度 |
| 搜索框内图标 | 左 `left-4`，16px，搜索中换 Loader2 | 左 16dp 图标，加载中换 16dp 转圈 | 同源 |
| 清除按钮 | `rounded-full p-3` + `X size=20`（约 44px） | 48dp 触控方框内 18dp 图标 | 触控 ≥48dp |
| 音源胶囊 | `px-4 py-2 text-xs`，选中 `accentColor` 实心 | 视觉 36dp 高、文字 12sp；**触控区 48dp** | 基准图实测胶囊 36dp |
| 音源 Tab 名 | 「网易云」「Local」「Navidrome」 | 「网易」「QQ」「酷我」（`platformLabelOf` 已有映射） | 手机版沿用 0.0.7 的短名 |
| 结果行高 | `rowHeight = 76`（`SearchResultsList.tsx`） | **最小 68dp**（单行标题时）/ 两行标题时约 84dp | 手机密度更小，两行标题下 76dp 放不下；68dp 仍远大于 48dp 触控 |
| 行卡片 | `rounded-2xl` 16px + 描边 + `bg-black/0.035` | 圆角 **12dp** + `Gyp.card` 底 + 1dp `cardRing` 描边，行间 8dp | 沿用 U1 的 `GyRadius.card`（12dp），与首页/播放页统一 |
| 封面 | `h-12 w-12 rounded-xl`（48px / 12px） | 48dp / 圆角 10dp（`GyRadius.row`） | 同源 |
| 歌名 | `text-sm font-semibold`，单行 `truncate` | 14sp 600，**最多 2 行**省略 | Hark 硬要求「中文长标题两行省略」 |
| 副标题 | `text-xs`，「歌手 · 专辑」 | 12sp，`listOfNotNull(artist, album≠title).join(" · ")` | 同源 |
| 音源角标 | 无（音源在顶部 Tab） | 10sp 胶囊，跟在副标题右侧 | 旧页面「[音源]」角标的替代（Hark 要求保留来源角标） |
| 行右侧动作 | hover 出现的 ＋ | 常驻 48dp「加入歌单」+ 48dp「下载」 | 触屏没 hover；下载是旧页面已有的能力 |
| 加载 | 居中 `Loader2 h-9 w-9` | 28dp 转圈 + 12sp「搜索中...」+ 3 行骨架 | 骨架是手机侧补充（Folia 无骨架） |
| 零结果 | `text-sm opacity-50` 一句 | 17sp 标题 + 12sp 副标题 + 推荐词 chips | 沿用 U1 `FoliaEmptyState` |
| 失败 | `AlertCircle size=32` + 文案 + 描边胶囊按钮 | 28dp 图标 + 13sp 文案 + `FoliaButton(Fill)` | 同源，按钮换 U1 组件 |
| 页面边距 | `px-3 / sm:px-6` | 16dp（`GySpace.pageH`） | 与其它页一致 |
| 分类 Tab | Folia 搜索结果页没有 | `FoliaSegmented`（32dp），仅在音源支持多类时出现 | 保留旧页面能力（单曲/专辑/歌单/歌手） |

---

## 4. 文案来源

全部在 `app/src/main/res/values/strings.xml` 的 U3 段（每条都带注释）。

| key | 文字 | 来源 |
|---|---|---|
| `search_placeholder_folia` | 搜索在线歌曲... | zh-CN.ts `searchDatabase`（:1579） |
| `search_clear` | 清除 | zh-CN.ts `clear`（:1007） |
| `search_searching` | 搜索中... | zh-CN.ts `searching`（:898） |
| `search_no_results` | 未找到相关结果 | zh-CN.ts `noResults`（:1629） |
| `search_retry` | 重试 | zh-CN.ts `retry`（:992） |
| `search_empty_subtitle` | 换个关键词，或换一个音源试试 | 自拟 |
| `search_empty_category` | 这一类没有结果 | 自拟 |
| `search_history` | 搜索历史 | 自拟（zh-CN.ts 无此条目） |
| `search_suggestions` | 推荐搜索 | 自拟 |
| `search_add_to_playlist` | 加入歌单 | 自拟 |
| `search_download` | 下载 | 自拟 |
| `search_no_source` | 还没有可用的音源 | 自拟（旧页面硬编码，本次收进资源） |
| `search_no_source_hint` | 先在「我的 · 音源插件管理」里安装一个插件 | 自拟（同上） |
| `search_open_plugins` | 去音源插件管理 | 自拟 |

失败页正文不是固定文案：由 `PluginFailureText.searchFailed(音源, 原因)` / 「X 音源里没有「关键词」的结果…」生成，均在既有代码里（0.0.9 a 就有）。分类 Tab 名（单曲/专辑/歌单/歌手）沿用旧 `SearchTab.label`。

---

## 5. 截图（限 360×800dp，除 320dp 那张）

全部在 `docs/ui-u3/`，由 `U3SearchScreenshotTest` 生成：

| 文件 | 内容 |
|---|---|
| `u3-initial-{light,dark}.png` | 初始态（历史 + 推荐） |
| `u3-loading-{light,dark}.png` | 加载中（转圈 + 骨架） |
| `u3-results-{light,dark}.png` | 有结果（5 行，第 2 行是超长中文标题） |
| `u3-empty-{light,dark}.png` | 空结果 |
| `u3-failure-{light,dark}.png` | 失败 + 重试 |
| `u3-mini-light.png` | 结果页 + 迷你条（列表底部给迷你条让位） |
| `u3-ime-light.png` | 键盘 inset 300dp：底栏/迷你条让位、列表止于键盘上沿 |
| `u3-320dp-light.png` | 320dp 宽（长标题两行、右侧按钮不挤压） |
| `u3-font130-light.png` | 字体 1.3 倍 |
| `side-search-results-light.png` | **并排**：广乐结果页 vs Folia 基准结果页（中文标注） |
| `side-search-results-dark.png` | 并排：深色 |
| `side-search-header-light.png` | 并排特写：搜索框 + 音源胶囊 |
| `side-search-row-light.png` | 并排特写：结果行 |
| `side-search-empty-light.png` | 并排：零结果 |
| `side-search-typing-light.png` | 并排：加载中 vs Folia 正在搜索 |

> `docs/ui-u3/` 里另有 16 张 `gallery-*.png` / `closeup-*.png`：那是 `FoliaGalleryScreenshotTest`（U1 的设计令牌画廊测试）的产物。它的输出目录跟着 `roborazzi.output.dir` 走，我把它从 `docs/ui-u2` 指到 `docs/ui-u3` 之后，这个老测试再跑就会写到这里。**它们不是 U3 的交付物**，忽略即可。

复现命令：

```powershell
$env:JAVA_HOME='D:\tool\jdk'
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat :app:testDebugUnitTest --tests "*U3SearchScreenshotTest*"
python tools\u3_side_by_side.py
```

---

## 6. 适配自检

| 项 | 结论 | 证据 |
|---|---|---|
| 浅/深 × 初始/加载/结果/空/失败 | ✅ 10 张 | `u3-*-{light,dark}.png` |
| 有迷你条一张 | ✅ | `u3-mini-light.png`（列表不被迷你条压住，迷你条与底栏各自成行） |
| 键盘 inset 一张 | ✅（模拟 inset） | `u3-ime-light.png`；见 §7 限制 |
| 320dp 宽一张 | ✅ | `u3-320dp-light.png`（640×1600px = 320dp@2x） |
| 字体 1.3 倍一张 | ✅ | `u3-font130-light.png`（无溢出、无截断到半个字） |
| 并排图（中文标注） | ✅ 6 张 | `side-search-*.png` |
| 无英文界面文字 | ✅（除数据） | 界面文字全部走 `strings.xml`；`OST` 属推荐词数据。数据侧英文仅出现在歌名/歌手/专辑（截图里是中文 demo 数据） |
| 触控 ≥48dp | ✅ | 清除键 48dp、行内两个动作键 48dp、音源胶囊触控区 48dp、行高 ≥68dp、底栏每项 56dp |
| 中文长标题两行省略 | ✅ | `u3-results-light.png` 第 2 行「以父之名（Live 2004 无与伦比演…」；320dp 与 1.3 倍字下同样两行 |
| 功能不退化 | ✅ | 播放（点行 → `playFrom` → 全屏播放页）、下载、加入歌单、音源切换、分类 Tab、历史、失败重试全部保留 |
| 不新造组件 | ✅ | 只用 `FoliaKit`（`FoliaSegmented` / `FoliaButton` / `FoliaEmptyState` / `FlowChips` / `FoliaSectionTitle`）+ 既有 `FoliaMiniPlayer` / `FoliaBottomNav`，与 U1 的地基一致 |

### 单测 / 构建

| 项 | 结果 |
|---|---|
| `testDebugUnitTest`（全仓） | **232 / 0 fail / 2 skip**（基线 205/0/2，U3 新增 27 个用例：搜索页状态机+防抖 13 个、U3 截图 14 个） |
| 2 个 skip | `PluginHealthReportTest` / `PluginUsableReportTest`，因 `build/plugin-cache-0.0.4/*.jsonl` 不存在而 `Assume` 跳过，与本次改动无关（U2 报告已记载） |
| 新增单测 | `SearchPageStateTest` 13 个：NoSource 优先、初始 Idle、Loading 压过旧结果、失败无结果显示失败页、有旧结果时只出错误条、空结果、任一桶有内容即 Results、新关键词触发、纯空白不触发、清空不触发、防抖只发一次、cancel 丢弃、窗口 400ms |
| `assembleDebug` | ✅ `app/build/outputs/apk/debug/app-debug.apk`（26.7 MB） |

---

## 7. 没做完的 / 已知限制

1. **键盘 inset 是模拟的，不是真的**。Robolectric 下 `WindowInsets.ime` 恒为 0dp（U2 已记过），所以 `u3-ime-light.png` 用的是测试里手工给的 300dp 底部 padding，复刻生产 `.imePadding()` 的抬升效果。生产路径没改：页面根 `imePadding()` + `ShellColumn` 的 `collapseOnIme()`。**真机上还需要一次目视确认**。
2. **骨架屏是新加的**（Folia 只有转圈）。Hark 的原话是「骨架或进度」，我两个都给了；若认为多余，删掉 `LoadingState` 里 `repeat(3)` 那段即可。
3. **推荐词是硬编码常量** `IdleKeywords = listOf("热歌","华语流行",…)`（在 `FoliaSearchScreen.kt` 里）。它属于「填进搜索框的查询数据」，不是界面文案，所以没进 `strings.xml`；若要本地化/可配置，得改成资源数组。
4. **`docs/ui-u3/` 混进了 16 张 U1 画廊图**（见 §5 注）。原因：`roborazzi.output.dir` 是全局的，`FoliaGalleryScreenshotTest` 也读它。要在同一分支同时保留两段截图，需要把输出目录改成按测试类区分——本次未做。
5. **未做真机 / 模拟器验证**（任务要求不允许）。滚动性能、真实封面加载（`AsyncImage`）、真实 IME 行为都只在测试环境验证过；截图里封面是占位图标，因为测试没有网络图片。
6. **未做长列表分页**（Folia 有 `home.loadMore`），旧页面也没有；搜索固定取第 1 页（`bridge.search(..., page = 1, ...)`）。
7. **并排脚本裁切框写死**（`tools/u3_side_by_side.py` 里的 `(0,0,824,1830)` 等），基准换版需要改常量。
8. **Folia 桌面 idle 不是搜索结果页**（是首页墙），所以「初始态（历史 + 推荐）」这一屏没有可对照的 Folia 基准，并排图里只放了结果/空/加载三屏；idle 的设计沿用广乐旧页面。

---

## 8. 提交

```
U3: 搜索页换成 Folia 样式（输入即搜 400ms 防抖 + 取消上次请求 + 六态状态机）
```

只提交本段相关文件；`docs/ui-0.0.8/*`、`docs/ui-0.0.9/*`、`build/` 产物、日志一律未纳入。未 push、未打 tag。
