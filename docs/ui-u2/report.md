# U2 外壳（底部 3 Tab / 导航 / 迷你条 / edge-to-edge）报告

**范围**：只做外壳这一段（`D:\workspaces\GuangYue-u2`，分支 `port/u2`，基线 `9633e7d`）。
内容墙留给 U6，播放页/歌词页保持旧样式（U2 只保证入口不丢）。

---

## 1. 做了什么

| # | 事项 | 结果 |
|---|---|---|
| 1 | 底部 3 Tab（首页 / 搜索 / 我的），选中态 = Folia 侧栏的 `raise` 胶囊 + 图标与文字提亮 | 复刻 Folia `AppNav`/侧栏选中态；Tab 名全部走 `strings.xml` |
| 2 | Navigation Compose 每 Tab 独立返回栈 + 滚动位置 | `popUpTo(graph.findStartDestination().id){ saveState = true } + launchSingleTop + restoreState` |
| 3 | 系统返回：子页先 pop → Tab 根页回首页 Tab → 首页再返回退出 | `ShellNav.backTarget()` 纯逻辑 + `BackHandler(enabled = 是顶层且不是首页)` |
| 4 | 现有页面全部可达 | 搜索→「搜索」；我的→我的（喜欢/下载/最近/歌单/插件管理/下载设置/外观/开源许可/关于）；播放页→迷你条点按 + 首页右上角；歌词页→播放页内 |
| 5 | 迷你条：封面 / 歌名 / 歌手 / 播放暂停 / **下一首** / 进度线，有播放才占位 | `FoliaMiniPlayer` 新增 `onNext`；显示条件 = `PlaybackState.item != null` |
| 6 | 迷你条点按进现有播放页 | `onOpen = navigate(Routes.PLAYER)`，没新造播放器，接的是 `PlaybackController` |
| 7 | edge-to-edge + 全套 inset | `enableEdgeToEdge()`；底栏吃 `navigationBars`（面板铺到底、留白在面板**内**）；状态栏由 `FoliaTopBar` 吃；搜索页 `imePadding()`；键盘弹起时迷你条与底栏整条让位（`collapseOnIme`） |
| 8 | 主题跟随系统 + 可手动覆盖 | `AppPreferences.hasDarkMode`（是否显式选过）→ 没选过用 `isSystemInDarkTheme()`；外层换 `FoliaTheme(dark)` |
| 9 | 首页占位空态（中文，U6 再做墙） | 无音源 → 「空空如也 / 去添加音源」；有音源但没数据 → 「还没有开始听 / 去搜索」 |
| 10 | 截图与并排图 | `docs/ui-u2/` 16 张 360×800dp + 5 张并排图 |

**验证**：`:app:compileDebugKotlin` ✅、`:app:testDebugUnitTest` ✅、`assembleDebug` ✅（见 §5）。

---

## 2. 文件清单

**新增**
- `app/src/main/java/com/guangyue/music/nav/ShellNav.kt` —— 外壳纯逻辑（顶层路由表 / 底栏显示 / 迷你条显示 / 返回目标）
- `feature/library/src/main/java/com/guangyue/feature/library/design/FoliaIme.kt` —— `LocalImeHidesMiniPlayer`、`Modifier.collapseOnIme()`
- `app/src/test/java/com/guangyue/music/nav/ShellNavTest.kt` —— 10 个单测（返回栈 + 迷你条显示条件）
- `app/src/test/java/com/guangyue/music/ui/shell/U2ShellScreenshotTest.kt` —— 16 个 Roborazzi 截图用例
- `tools/u2_side_by_side.py` —— 并排图脚本（PIL）
- `docs/ui-u2/` —— 截图 + 并排图 + 本报告

**改动**
- `app/src/main/java/com/guangyue/music/nav/Root.kt` —— Tab 名走 `stringResource`；`BackHandler`；`switchTab`；`ShellColumn` 接 `collapseOnIme`
- `app/src/main/java/com/guangyue/music/nav/NavHost.kt` —— 迷你条接 `onNext`、三条文案走资源；首页接 `onOpenSearch` 与占位文案
- `app/src/main/java/com/guangyue/music/MainActivity.kt` —— 外层换 `FoliaTheme`；`explicitDark ?: isSystemInDarkTheme()`
- `app/src/main/java/com/guangyue/music/ShellViewModel.kt` —— `darkMode: StateFlow<Boolean>` → `explicitDark: StateFlow<Boolean?>`
- `core/data/src/main/java/com/guangyue/core/prefs/AppPreferences.kt` —— 新增 `hasDarkMode`
- `feature/library/.../design/FoliaScaffold.kt` —— `FoliaBottomNav` 增加 `navBarInset: Dp?`，inset 移到玻璃面板内部
- `feature/library/.../design/icons/GuangYueIcons.kt` —— 新增 `SkipForward`（lucide `skip-forward` 同款矢量）
- `feature/library/.../home/HomeScreen.kt` —— 迷你条 `onNext` + 标签参数；首页占位空态参数
- `feature/search/.../SearchScreen.kt` —— `imePadding()`
- `app/src/main/res/values/strings.xml` —— U2 文案键（见 §3 表 3）
- `app/build.gradle.kts` —— `roborazzi.output.dir` → `docs/ui-u2`

---

## 3. 三张表

### 表 1 · Folia 原值 → 手机值 → 原因

| 项 | Folia 原值（桌面） | 手机值 | 原因 |
|---|---|---|---|
| 主导航位置 | 左侧竖栏 `Sidebar`（图标 + 文字，垂直排列） | 底部横排 3 Tab | 手机竖屏宽 360dp，侧栏吃掉 1/6 宽；拇指可达区在底部 |
| 导航项命中区 | 侧栏行高 36px | 每项 = 屏宽/3 × 56dp | 触控 ≥48dp；三等分最稳 |
| 导航选中态 | `--st-raise` 胶囊 + 文字色提升 | 同（`Gyp.raise` 28dp 高胶囊） | 直接复用 token，不改色 |
| 导航容器 | 侧栏 `--bg-side` 实色 | 玻璃面板 `GyGlass.panel` + 顶部 1px 发丝线 | 内容 edge-to-edge 滚到底，实色会切断画面 |
| 导航文字 | 13px | 12sp（`GyText.hint`） | Tab 名短（2 字），12sp 在 3 等分里不挤 |
| 迷你条位置 | 内容**顶部**悬浮（docs/port 基准 m412-light-mini-bar） | 底栏**正上方** | 手机单手拇指区在底部；顶栏要留给页面标题 |
| 迷你条高度 | 89dp（三行：标题/按钮/进度） | 56dp（两行：标题歌手/进度） | 手机屏幕更矮，压到 56dp 仍 ≥48dp 触控 |
| 迷你条播放键 | 40dp 黑圆 | 36dp 橙圆（`Gyp.accent`）+ 右侧 48dp 下一首 | 播放/暂停是主操作保留橙；下一首补 48dp 命中区 |
| 列表底部留白 | 无（桌面无底栏） | 迷你条 56dp + 底栏 ~64dp + `navigationBars` 由底栏面板内部吃掉 | 滚到底时最后一行不能被壳盖住 |
| 页边距 | 10–16px | 16dp（`GySpace.pageH`） | U1b 既定 |

### 表 2 · 桌面动作 → 手机动作 → 原因

| 桌面动作 | 手机动作 | 原因 |
|---|---|---|
| 点侧栏切换页面 | 点底栏 Tab | 同「主导航位置」 |
| 点侧栏项后仍保留「上一个页面的返回」 | 每 Tab 独立返回栈（切 Tab 不丢栈、切回原位） | 手机切 Tab 频繁，单栈会把另一 Tab 的位置冲掉 |
| 按 Esc / 点面包屑回上一级 | 系统返回手势 / 返回键：子页先 pop → Tab 根页回首页 → 首页退出 | 安卓返回是系统级手势，必须接管，否则一按就退出 App |
| hover 才显示的操作按钮 | 常显 48dp 图标按钮 | 手机无 hover |
| 右键菜单 | 长按底部弹层（`FoliaBottomSheet`） | 手机无右键 |
| 迷你条在顶部、点开覆盖式播放面板 | 迷你条在底部、点开全屏播放页 | 播放页是独立路由，返回手势可回 |
| 拖窗口改宽度 | 不做 | 手机无窗口 |

### 表 3 · 文案来源

| key | 文案 | 来源 |
|---|---|---|
| `tab_home` | 首页 | **自拟**（zh-CN.ts 无底栏键；侧栏 `libraryBravais.homeTitle`=书库、`libraryTui.homeTitle`=曲库） |
| `tab_search` | 搜索 | zh-CN.ts 顶层 `"search": "搜索"`（:91） |
| `tab_profile` | 我的 | **自拟**（Folia 桌面无「我的」侧栏项；`ProfileScreen` 既有文案沿用） |
| `mini_play` | 播放 | zh-CN.ts `"play": "播放"`（:4539） |
| `mini_pause` | 暂停 | zh-CN.ts `"pause": "暂停"`（:4540） |
| `mini_next` | 下一首 | zh-CN.ts `"next": "下一首"`（:4538）/ `"nextTrack"`（:880） |
| `mini_open_player` | 打开播放页 | **自拟**（无障碍标签） |
| `cd_song_cover` | 歌曲封面 | **自拟**（无障碍标签） |
| `home_placeholder_title` | 还没有开始听 | **自拟**（U2 占位，U6 换成真内容） |
| `home_placeholder_hint` | 搜索一首歌，先听一点，最近播放会出现在这里。 | **自拟** |
| `home_placeholder_action` | 去搜索 | **自拟** |

`app_name`=广乐（既有）。原有 `strings.xml` 的 U1 键不动。

---

## 4. 手机适配清单逐条自检

| 项 | 结论 | 证据 |
|---|---|---|
| 触控 ≥48dp | ✅ 底栏每项 113×56dp（360dp 屏）；迷你条播放键 36dp 视觉 + 48dp 方框命中；下一首 48dp | `u2-mini-light.png`、`FoliaBottomNav`（`weight(1f)` 高 56dp） |
| 文字不溢出 | ✅ 1.3 倍字体（`u2-font130-light.png`）与 320dp 宽（`u2-320dp-light.png`）下歌名/歌手/Tab 名单行截断、无换行重叠；Tab 名 2 字 | 两张图 |
| 状态栏不遮内容 | ✅ `FoliaTopBar` 自己算 `max(statusBars, displayCutout)` 顶部留白；`enableEdgeToEdge()` 已开 | 截图顶部标题距屏边 ~16dp + 状态栏区 |
| 手势条 / 三键导航不遮内容 | ✅ 底栏面板铺到屏幕最底、在面板**内**留 `navigationBars`：手势 24dp（`u2-inset-gesture-light.png`）/ 三键 48dp（`u2-inset-3key-light.png`），图标与文字都未被压 | 两张图对比 |
| 列表底部留白 | ✅ 首页 `LazyColumn` 的 `contentPadding.bottom` 之外还有壳层给的迷你条 + 底栏高度（壳在内容之外竖排，不压在内容上） | `u2-mini-light.png` 滚到底 |
| 键盘 | ✅ 搜索页 `imePadding()`；壳层迷你条与底栏 `collapseOnIme()`（量 0dp，不被顶起、不遮搜索框） | 代码 + Robolectric 无法模拟 IME，**未出图**（见 §6） |
| 1.3 倍字体 | ✅ Tab 名 / 歌名 / 歌手都不截断；Tab 栏高度由 12sp 文字 + 胶囊决定，放大后仍整行可见 | `u2-font130-light.png` |
| 深色无漏色 | ✅ 页面底 `#09090B`、迷你条玻璃底、底栏玻璃底、选中胶囊 `#45454C` 全为深色系；无白底残留 | `u2-home-dark.png`、`u2-mini-dark.png`、`u2-profile-dark.png`、`u2-search-dark.png` |
| 截图无英文界面文字 | ✅ Tab/按钮/标题全中文；歌名「夜曲」「周杰伦」等为数据，允许 | 全部 U2 截图 |
| 320dp 小屏 | ✅ 三栏 Tab 宽度 106dp 仍 ≥48dp；卡片两列不溢出 | `u2-320dp-light.png` |

---

## 5. 测试与构建（实测）

全部在 `D:\workspaces\GuangYue-u2` 用 JDK `D:\tool\jdk` 跑：

| 命令 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest`（含 16 张截图录制 + 新单测） | BUILD SUCCESSFUL |
| 全仓 `testDebugUnitTest` | **BUILD SUCCESSFUL，205 tests / 0 failures / 2 skipped** |
| `assembleDebug` | BUILD SUCCESSFUL |

- 任务书写的口径「162/0/1」与实测不符：**改动前实测 183/0/1，改动后 205/0/2**（+22）。
  两个 skip 都是 `org.junit.AssumptionViolatedException`（`PluginHealthReportTest`、`PluginUsableReportTest`），
  原因是本机缺 `build/plugin-cache-0.0.4/*.jsonl` 现场，与 U2 改动无关；增量 = 新增 22 个用例，跳过数 +1 属既有的环境依赖。
- 新增用例：`ShellNavTest` 11 个（底栏显示条件、迷你条显示条件、返回目标、以及「插件管理 → 我的根 → 首页」的组合场景）；
  `U2ShellScreenshotTest` 15 个（浅/深 × 3 Tab、有无迷你条、三键/手势 inset、1.3 倍字体、320dp）。
- 截图重录命令：`$env:GUANGYUE_RECORD_SCREENSHOTS="true"; .\gradlew.bat :app:testDebugUnitTest --tests "*U2ShellScreenshotTest*"`，产物写到 `docs/ui-u2/`。

## 6. 没做完 / 已知限制

1. **键盘弹起时壳层的让位只在代码里，没出截图**：Robolectric 的 Compose 测试拿不到真实 IME inset（`WindowInsets.ime` 恒为 0）。逻辑是 `imeBottomPadding() > 0.dp` 就把迷你条/底栏量成 0dp，需真机复核。
2. **毛玻璃在截图里是降级态**：Robolectric 无 GPU，`Modifier.blur` 不真模糊，迷你条/底栏在截图里是半透明白（`GyGlass.panel` 浅色 `0xA6FFFFFF`），真机（API31+）才是真模糊。
3. **首页内容墙不做**：U2 只给占位空态，真实推荐/榜单按 RULES 归 U6。
4. **旧页面仍是旧样式**：搜索页、我的页、插件管理、下载、设置、播放页、歌词页保留 U1 之前的样式（任务允许），只有壳层与首页占位用了新设计语言。
5. **并排图裁切框是硬编码像素**：基准图若重拍，需同步改 `tools/u2_side_by_side.py` 里的 `(1268,1372)` / `(1484,1830)` 等常量。
6. **没在真机/模拟器验**：本段按任务书要求全程不用 adb/模拟器，只做 Roborazzi 截图 + 单测 + 构建。
