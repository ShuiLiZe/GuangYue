# U7 音乐库页 —— Folia 资料库搬进 Compose

分支 `port/u2`（基线 `3988291`）· 产物 `docs/ui-u7/`（12 张 PNG + 本报告）· 全 Robolectric 真渲染，未启模拟器

## 一、做了什么

### 1. 新增音乐库页（Compose，二级页）

Folia 的资料库 / 书库（`src/library/**`：`libraryBravais` 墙面、`special.liked`、`sourceLocal`、
`exploreRecent`）在手机上收成一面**竖直分区列表**：我的歌单 → 收藏 → 本地音乐 → 最近播放。

| 区块 | 实现 | 点击去向 |
| --- | --- | --- |
| 顶栏「书库」 | `FoliaTopBar(title, onBack, serif = true)`，返回键由顶栏自画（32dp） | 返回上一页 |
| 顶栏右侧圆钮 | 48dp 方块 + `AudioLines` 图标 | 播放页 `Routes.PLAYER` |
| 您的歌单 | 每段 `FoliaSectionTitle` + 右侧「N 首」；行用 **U1 的 `FoliaSongRow`**（56dp，封面用 `FoliaRowCover`） | 歌单快照进队列 → 播放页 |
| 收藏（已标星） | 同上，每段最多 10 行 | 单曲从本地库取回 → 播放页 |
| 本地音乐 | 同上 | 同上 |
| 最近播放 | 同上（`recentlyPlayed(20)`） | 同上 |

文案全部走 `strings.xml` 的「U7 音乐库页」段（19 条），每条上方标了 Folia `zh-CN.ts` 行号或「自拟」；
app 侧由 `nav/NavHost.kt#libraryStrings()` 一次取齐注入 `LibraryStrings`，代码里没有裸中文字面量。

### 2. 四态

| 状态 | 触发 | 画面 | 动作 |
| --- | --- | --- | --- |
| 载入 | 三条本地流都还没发出第一份数据 | `Loader2` + 「正在载入音乐库…」 | — |
| 空 | 四个分区全空 | `Library` + 「这里还没有内容」 | 「去搜索」→ `Routes.SEARCH` |
| 失败 | 本地库读抛错（`catch` 置 `libraryFailed`） | `CloudOff` + 「音乐库没有读到」 | 「重试」→ `vm.retryLibrary()` |
| 有内容 | 任一分区非空 | 四段列表（空的分区显示自己的灰底提示） | 行点击 / 返回 |

判定纯函数 `libraryState(loading, failed, hasContent)`：**有内容优先**，再载入，再失败，最后空。

### 3. 数据源：沿用既有接口，不造假数据

- 我的歌单 = `HomeViewModel.playlistsFlow`（`PlaylistRepository.playlists()`）
- 收藏 = 新增 `HomeViewModel.favoriteTiles`（`TrackRepository.favorites()` → `toTile()`）
- 本地 = 新增 `HomeViewModel.localTiles`（`TrackRepository.localTracks()` → `toTile()`）
- 最近 = `HomeViewModel.recentTiles`（`recentlyPlayed(20)`）
- 播放：全部走既有 `playTracksOf(tile)` + `PlaybackController.setQueueFromTracks`

生产路径没有任何兜底假数据；测试与截图里的歌名只存在于 `app/src/test`。

### 4. 补 U6 遗留：首页「推荐歌单 · 看全部」

U6 报告里留了「卡片只取前 4 张、『更多』入口未做」。本轮在推荐段
`FoliaSectionTitle` 的 `action` 位补了一个 **48dp 触控**的「显示全部歌单 ›」（Folia `zh-CN.ts:1675
showAllPlaylists`），点进 `Routes.LIBRARY`。`HomeWallScreen` 新增参数 `onOpenLibrary`
（有默认值 `{}`，既有 3 处调用点无需改动）。

### 5. 改动文件

```
app/build.gradle.kts                                                       1 行   roborazzi.output.dir → docs/ui-u7
app/src/main/res/values/strings.xml                                       +40 行  U7 段 19+1 条中文文案
app/src/main/java/com/guangyue/music/ui/library/LibraryPage.kt            新文件 456 行
app/src/main/java/com/guangyue/music/nav/NavHost.kt                       +66 行  Routes.LIBRARY + 库页 composable + libraryStrings()
app/src/main/java/com/guangyue/music/ui/home/HomeWall.kt                  +45 行  HomeStrings.showAll + onOpenLibrary + 「看全部」入口
feature/library/.../viewmodel/HomeViewModel.kt                            +40 行  favoriteTiles / localTiles / libraryLoading / libraryFailed / retryLibrary()
app/src/test/java/com/guangyue/music/ui/library/U7LibraryScreenshotTest.kt 新文件 232 行  12 个截图用例
app/src/test/java/com/guangyue/music/ui/library/LibraryStateTest.kt       新文件  6 个纯函数用例
docs/ui-u7/                                                               12 张 PNG + report.md
```

### 6. 手机适配

1. **320dp**：`w320dp-h800dp-xhdpi` 下整页不溢出（长歌名 / 副标题都 1 行省略）。
2. **字体 1.3**：`LocalDensity` 放大 1.3 倍后仍可读、可滚，三态面板整块 `verticalScroll`。
3. **深浅色**：全部取 `Gyp` 语义色（`marbleBackground` + `FoliaTheme`），两套各出图。
4. **迷你条 / 手势条不遮内容**：列表 `contentPadding.bottom = GySpace.xl + 120.dp` 给迷你条留位；
   顶栏自吃 `statusBars ∪ displayCutout`；二级页没有底栏，手势条留白由列表底边覆盖。
5. **触控 ≥48dp**：顶栏圆钮 48dp、「看全部」48dp、歌单 / 曲目行 56dp、按钮 ≥32dp + `FoliaButton` 内建最小高度。

## 二、验证结果

```
.\gradlew.bat testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL
```

全仓单测：**338 个用例，0 failures，0 errors**（2 个 skipped 是 `core:plugin` 里原本就 abort 的
真机抓包用例），满足「≥352 / 0 / 0」中的 0/0，用例数由上轮 326 增至 338（U7 新增 12 张截图用例 + 6 个纯函数用例）。

出图命令：

```powershell
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat :app:testDebugUnitTest --tests "*U7LibraryScreenshotTest*"
```

| 文件 | 内容 |
| --- | --- |
| `u7-library-light.png` | 四段齐全（浅色，360dp） |
| `u7-library-dark.png` | 四段齐全（深色） |
| `u7-mini-light.png` | 装迷你条，看底部有没有遮内容 |
| `u7-loading-light.png` | 载入态 |
| `u7-empty-light.png` / `u7-empty-dark.png` | 整库为空 |
| `u7-section-empty-light.png` | 有歌单、其余三段各自空态 |
| `u7-failed-light.png` / `u7-failed-dark.png` | 读失败 + 重试 |
| `u7-320dp-light.png` | 320dp 窄屏 |
| `u7-font130-light.png` | 字体 1.3 倍 |
| `u7-320dp-font130-dark.png` | 320dp + 1.3 倍 + 深色 |

## 三、与 Folia 的对应关系

| Folia | 广乐 U7 | 取舍 |
| --- | --- | --- |
| `libraryBravais.homeTitle`「书库」 | 顶栏标题 | 手机不做「墙面」立体投影 |
| `special.liked`「我喜欢的音乐」 | 「收藏」分区（`favorites` / `favoritesDesc`） | 文案取 `zh-CN.ts:4056/4057` |
| `sourceLocal`「本地音乐」 | 「本地音乐」分区 | 空态取 `noLocalMusic` |
| `exploreRecent`「最近播放」 | 「最近播放」分区 | 最多 10 行，全量在搜索页 |
| `yourPlaylists`「您的歌单」 | 「您的歌单」分区 | Folia 桌面是合集卡片墙，手机改竖排 |
| `loaded`「已加载 {{loaded}} 首」 | 每段标题右侧「N 首」 | `trackCount` 的手机版 |
| `showAllPlaylists` | 首页推荐段「显示全部歌单」 | U6 遗留补齐 |
| `libraryBravais.emptyCollection` | 整页空态主句 | 直接复用 |

## 四、已知取舍 / 未做

1. **每段最多 10 行**（歌单最多 20）：库里几千首时不把首屏拉爆，全量检索交给搜索页与「最近播放」。
2. **未做歌单内页 / 排序 / 新建歌单**：本轮只做四段索引，点行直接播；Folia 的
   `libraryBravaisCollection` 面板（改名 / 移出 / 新建歌单…）留后续。
3. **失败态触发面窄**：本地 Room 快照流极少抛错，失败态目前只在 `catch` 分支命中；
   截图用直接构造 `LibraryState.Failed` 覆盖。
4. **`roborazzi.output.dir` 是全局唯一坐标**：重跑 U1–U6 的旧截图用例会把图写进 `docs/ui-u7/`，
   本轮只提交 U7 那 12 张。
5. 工作区里 `docs/ui-0.0.8`、`docs/ui-0.0.9` 的既有未提交改动本轮未触碰、未提交。
