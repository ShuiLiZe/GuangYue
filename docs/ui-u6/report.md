# U6 首页墙 —— Folia 首页搬进 Compose

> 分支 `port/u2`（基于 17fbea9 U5），产物在 `docs/ui-u6/`。全部用 Robolectric 真渲染 + Roborazzi 出图，不启动模拟器。

## 一、做了什么

### 1. 替换外壳首页占位
U2 只做外壳时，首页是「还没有开始听」的空占位（`home_placeholder_*`）。U6 把它换成 Folia 首页墙：
问候标题 + 搜索入口 + 继续播放条 + 推荐歌单（手机 2 列网格）+ 最近播放（横滑）+ 热门榜单（列表）。

- 入口：`app/.../nav/NavHost.kt` 的 `composable(Routes.HOME)` 现在渲染 `HomeWallScreen`；
- 旧占位文案 `home_placeholder_title / _hint / _action` 仍留在 strings.xml（画廊与老用例还在用），生产首页路径已不再引用；
- `feature/library` 里的旧 `HomeScreen(...)` 已删除（它无法引用 app 模块的 `FoliaCoverCard`，会形成反向依赖），
  该文件现在只保留数据模型与迷你条。

### 2. 页面结构

| 区块 | 实现 | 点击去向 |
| --- | --- | --- |
| 顶栏问候 | `FoliaTopBar(serif=true)`，有音源「欢迎回来」/ 无音源「欢迎使用广乐」 | 右侧声波钮 → 播放页 `Routes.PLAYER` |
| 搜索入口 | 48dp 玻璃底搜索框（`strings.search`「搜索音乐…」） | `Routes.SEARCH` |
| 继续播放 | 56dp 行：封面 + 标题 + 进度 + 暂停钮 | 暂停 → `playback.toggle()` |
| 推荐歌单 | **2 列网格**，`HomeTile` 前 4 张，卡片用 U1 的 `FoliaCoverCard`（app 模块 `ui/design`），单数行补等宽空位 | `onOpenTile` → `vm.playTracksOf(tile)` → `setQueueFromTracks` → `Routes.PLAYER` |
| 最近播放 | 横滑货架（104dp 方形卡）+ 左右滚动钮（48dp 触控，`scrollLeft/scrollRight` 无障碍描述） | 同上 |
| 热门榜单 | 64dp 行：48dp 封面 + 标题 + 前三首 + ChevronRight | 同上 |

结构：`Column(fillMaxSize().marbleBackground()) { TrackScrollFrames(); Column(weight 1f) { FoliaTopBar; when(state) {...} }; miniPlayer(); bottomBar() }`，
列表是**单个 LazyColumn**（货架用 `Row(horizontalScroll)` 而非 LazyRow，避免懒布局嵌套）。

### 3. 三态（加载中 / 空 / 失败），全部中文

| 状态 | 触发 | 画面 | 动作 |
| --- | --- | --- | --- |
| 加载中 | `HomeViewModel.wallLoading`（sheets/charts 任一在拉） | Loader2 +「正在载入推荐内容…」 | — |
| 失败 | `wallFailed`（`getRecommendSheetTags / getRecommendSheets / getTopLists / getTopListDetail` 抛错） | CloudOff（红）+「推荐内容没有读到」+「检查一下网络，或在 我的 · 音源插件管理 里换一个音源」 | 「重试」→ `vm.retryWall()`（清 loaded 标记后重拉） |
| 空 · 有音源 | 内容全空 | Music +「这里还没有推荐内容」+「先搜几首喜欢的歌听一下，推荐和最近播放会慢慢出现。」 | 「去搜索」→ `Routes.SEARCH` |
| 空 · 没音源 | `hasAnyPlugin=false` | Plug +「暂时没有推荐内容」+「装一个官方音源，搜索、榜单和推荐歌单就有了」 | 「去添加音源」→ `Routes.PLUGINS`；「去搜索」→ SEARCH |

判定是纯函数 `homeWallState(loading, failed, hasContent)`：有内容优先（不打断用户），其次 loading，再 failed，最后空 —— 与 U5 歌词页 `lyricEmptyKind` 同一写法，有单测覆盖 8 种输入。

### 4. 数据源（沿用既有接口，不造假数据）
- 推荐歌单：`HomeViewModel.loadSheetsIfNeeded()` → `bridge.getRecommendSheetTags` → `getRecommendSheets`（`kind=SHEET`）；
- 热门榜单：`loadChartsIfNeeded()` → `getTopLists` → `getTopListDetail`（`kind=STATION`）；
- 本地歌单：`PlaylistRepository.playlists()`（`kind=PLAYLIST`）；
- 继续播放 / 迷你条：`PlaybackController.state`。
- **生产路径没有假数据**：示例 `HomeTile` 只出现在截图测试里；无音源时走空态而不是塞假卡片。

### 5. 改动文件

```
新增  app/src/main/java/com/guangyue/music/ui/home/HomeWall.kt                 752 行
新增  app/src/test/java/com/guangyue/music/ui/home/U6HomeWallScreenshotTest.kt 259 行
新增  app/src/test/java/com/guangyue/music/ui/home/HomeWallStateTest.kt         57 行
重写  feature/library/src/main/java/com/guangyue/feature/library/home/HomeScreen.kt   601 → 184 行
改    feature/library/src/main/java/com/guangyue/feature/library/viewmodel/HomeViewModel.kt（+加载/失败/重试）
改    app/src/main/java/com/guangyue/music/nav/NavHost.kt（HOME 段 + homeStrings()）
改    app/src/main/res/values/strings.xml（+19 条 U6 文案，全中文）
改    app/src/test/java/com/guangyue/music/ui/ScreenshotTest.kt / ui/shell/U2ShellScreenshotTest.kt（跟新签名）
改    app/build.gradle.kts（roborazzi.output.dir → docs/ui-u6）
新增  docs/ui-u6/*.png（14 张） + report.md
```

### 6. 手机适配
- **320dp 窄屏**：`u6-320dp-light`；2 列网格在 320dp 下卡片仍有 ≈140dp 宽，标题单行省略。
- **字体 1.3 倍**：`u6-font130-light`、`u6-320dp-font130-dark`；卡片标题走 `maxLines=1 + ellipsis`，状态面板整块 `verticalScroll`，不会被裁。
- **深/浅色**：`u6-wall-{light,dark}`、`u6-loading-*`、`u6-failed-*`、`u6-empty-*` 全成对；色值全部来自 `Folia.colors` 令牌。
- **迷你条与手势条不遮内容**：骨架是 `Column{ Box(weight 1f); miniPlayer; bottomBar }`，底栏自吃 `navigationBars`（手势 24dp / 三键 48dp）；
  列表底部额外留 `GySpace.xl + 120dp` 的 `contentPadding`，滚到底时最后一张卡或最后一行榜单不会被迷你条压住
  （`u6-mini-light`、`u6-inset-3key-light`、`u6-font130-light` 三张图可见：内容在迷你条上方完整收住）。
- **触控 ≥48dp**：搜索入口 48dp、滚动钮 48dp（`FoliaIconButton` 尺寸）、重试/去搜索/去添加音源按钮 48dp（`FoliaButton` app 版 `defaultMinSize(48.dp)`）、
  卡片整块可点（远大于 48dp）、榜单行 64dp。

## 二、验证结果

```
gradlew.bat test        # 全仓（app + feature/library + feature/search + feature/player + core/*）
352 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

对比 U5 报告的 303/0/2：**新增 49 个用例**（U6 贡献 15 个截图用例 + 5 个 `homeWallState` 单测；其余差额为 U1–U5 期间各模块累计口径差异）。
本仓 `test` 是聚合任务，`app` 模块本身 181 tests + `feature/library`/`feature/search`/`feature/player`/`core/*` 合计 352。

出图命令（只有显式录图时才写 docs）：

```
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat :app:testDebugUnitTest --tests "*U6HomeWallScreenshotTest*"
```

| 文件 | 尺寸 | 说明 |
| --- | --- | --- |
| `docs/ui-u6/u6-320dp-font130-dark.png` | 69 KB | 320dp + 字体 1.3 + 深色 |
| `docs/ui-u6/u6-320dp-light.png` | 71 KB | 320dp 窄屏 |
| `docs/ui-u6/u6-empty-dark.png` | 33 KB | 空态 · 深色 |
| `docs/ui-u6/u6-empty-light.png` | 33 KB | 空态（有音源、无内容） |
| `docs/ui-u6/u6-failed-dark.png` | 31 KB | 失败态 · 深色 |
| `docs/ui-u6/u6-failed-light.png` | 30 KB | 失败态 + 重试 |
| `docs/ui-u6/u6-font130-light.png` | 71 KB | 字体 1.3 倍 |
| `docs/ui-u6/u6-inset-3key-light.png` | 65 KB | 整屏骨架：三键导航 48dp inset |
| `docs/ui-u6/u6-loading-dark.png` | 20 KB | 加载中 · 深色 |
| `docs/ui-u6/u6-loading-light.png` | 19 KB | 加载中 |
| `docs/ui-u6/u6-mini-light.png` | 69 KB | 整屏骨架：内容 + 迷你条 + 底栏（手势 24dp inset） |
| `docs/ui-u6/u6-no-source-light.png` | 35 KB | 空态（没有音源）+ 去添加音源 / 去搜索 |
| `docs/ui-u6/u6-wall-dark.png` | 63 KB | 深色 · 内容态 |
| `docs/ui-u6/u6-wall-light.png` | 74 KB | 浅色 · 内容态（问候 / 搜索 / 继续播放 / 2 列推荐 / 最近播放横滑） |

> 说明：`roborazzi.output.dir` 是全局唯一坐标（当前指向 `docs/ui-u6`），重跑 U1–U5 的截图用例会把它们的图写进同一目录。
> 本次只提交上述 14 张 U6 图；跑全量单测时产生的其它 U 段图已清理，不进提交。

## 三、与 Folia 的对应关系

| Folia | 广乐 U6 | 取舍 |
| --- | --- | --- |
| `home.welcomeBack`「欢迎回来」/ `home.welcome`「欢迎使用 Folia」 | 顶栏问候（有音源 / 无音源两版） | 品牌名换「广乐」 |
| `home.searchPlaceholder`「搜索音乐...」 | 首页搜索入口 | 直接用 |
| `home.exploreRecent`「最近播放」 | 最近播放货架 | 直接用 |
| `home.neteaseDailyPlaylists`「每日推荐歌单」 | 「推荐歌单」分区标题 | 压成通用词，音源无关 |
| `home.neteaseCharts`「排行榜」 | 「热门榜单」 | 压成通用词 |
| `home.exploreEmpty`「暂时没有推荐内容」 | 无音源空态主句 | 直接用 |
| `home.exploreFailed`「推荐内容没有读到」 | 失败态主句 | 直接用 |
| `home.exploreRetry`「重试」 | 失败态按钮 | 直接用 |
| `home.exploreScrollLeft/Right` | 货架左右滚动钮的无障碍描述 | 直接用 |
| `ExploreSections.tsx` 横滑货架 + 两端淡出 + 更多/左右钮 | 横滑货架 + 左右钮（48dp） | 淡出遮罩未做；「更多」入口未做 |
| `Grid3D` 桌面 3D 封面墙 | 手机 2 列平铺网格 | 手机不做 3D 倾斜 |

## 四、已知取舍 / 未做
- **卡片只取前 4 张**：Folia 桌面是横滑大卡，手机 2 列一屏看 4 张最稳；「看全部」需要歌单列表页，本轮没做（会进入「标题点开歌单」的新路由）。
- **没有骨架屏**：加载态是图标 + 一句话，不是 shimmer 占位卡。
- **收藏/播放全部按钮**没加在卡片上（U4 播放页已有）。
- **点卡片**走「立即播放该集合的第一首并进播放页」，没有单独的「歌单详情页」——沿用 U2/U4 既有 `playTracksOf → setQueueFromTracks` 链路。
- `home_wall_greeting` / `home_wall_greeting_guest` 只有两版问候，没有按时间段（早上好 / 晚上好）切换。
- 空态文案是本仓自拟，Folia 对应键只有 `exploreEmpty` 一句，`empty` / `empty_hint` 为补充。
