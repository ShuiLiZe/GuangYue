# 广乐 0.0.6 · 界面与 Folia 逐项对齐报告

> 基线：0.0.5（daab131）→ 0.0.6（分支 `folia-ui`）
> 对照方法：本机跑 Folia 原版（`web/.work/folia`，v0.7.23，`npx vite --port 3000`，Playwright 驱动本机 Chrome，视口 390×844 @2x，深/浅两套）逐页截图；广乐用 Roborazzi 出同尺寸截图（`:app:testDebugUnitTest`，`GUANGYUE_RECORD_SCREENSHOTS=true`）。两侧原始截图在 `docs/ui-0.0.6/`（广乐，1233×2673）与 `docs/ui-0.0.6/folia/`（Folia，780×1688），并排图在 `docs/ui-0.0.6/pairs/`（左 Folia、右 广乐），逐像素指标见 `pairs/metrics.txt` 与 `pairs/metrics.json`。

---

## 1. 结论

| 项 | 结果 |
|---|---|
| 底色 | **完全一致**：深 `#09090B`、浅 `#F5F5F4`（Folia `baseThemes.ts` 的 DEFAULT_THEME / DAYLIGHT_THEME），纯色、无渐变 —— 0.0.5 的大理石渐变已删 |
| 图标 | 全部换成 Folia 用的 169 枚 Lucide 图标（`GuangYueIcons`，ImageVector），Material Icons（core + extended）依赖已从全部模块移除 |
| 字体 | 标题/区块标题用 Noto Serif SC（与 Folia `index.css` 的 `Folia Noto Serif SC` 同族，400 + 500 两个字重），子集化后 **989,860 B < 1 MB** |
| 毛玻璃 | Android 12+ 且非 `isLowRamDevice` 才启用 `RenderEffect`/`Modifier.blur`（`LocalCanBlur`）；否则降级为纯半透明，图标/模糊只在静态层 |
| 导航 | 按手机信息架构重排：底部 3 Tab（首页/搜索/我的）+ 二级页 + 全屏播放页；插件与下载移入「我的」二级页；二级页与播放页隐藏底栏 |
| APK | release **8,672,942 B（8.27 MiB / 8.67 MB）** ≤ 9 MB 目标；0.0.5 为 8,194,554 B |
| 性能 | 首页滚动稳态掉帧率 **0.46%–1.11%**，p95 = 16 ms（满 60 fps） |
| 测试 | `:core:plugin:test` **75/75 通过**；`:app:testDebugUnitTest` **16/16 通过**（含 15 张 Roborazzi 截图） |

像素差异率（同尺寸归一化后逐像素，容差 12/255）：

| 并排图 | 页面 | 差异像素率 | 平均绝对差 |
|---|---|---|---|
| `pair-01-home-dark.png` | 首页 深色 | 15.34% | 6.64 |
| `pair-02-home-light.png` | 首页 浅色 | 22.91% | 8.69 |
| `pair-03-home-filled-dark.png` | 首页 有数据 深色 | 33.10% | 13.98 |
| `pair-03b-home-filled-light.png` | 首页 有数据 浅色 | 39.56% | 14.53 |
| `pair-04-settings-dark.png` | 设置 深色 | 71.14% | 23.41 |
| `pair-05-settings-light.png` | 设置 浅色 | 30.72% | 30.90 |
| `pair-06-playlists-dark.png` | 歌单库 / 我的 深色 | 44.90% | 15.37 |
| `pair-06b-playlists-light.png` | 歌单库 / 我的 浅色 | 33.74% | 13.93 |
| `pair-07-local-player.png` | 本地库 / 播放页 | 55.50% | 31.43 |
| `pair-08-albums-search-dark.png` | 专辑库 / 搜索 深色 | 12.37% | 6.38 |
| `pair-08b-albums-search-light.png` | 专辑库 / 搜索 浅色 | 19.40% | 8.44 |
| `pair-09-plugins.png` | 设置 / 音源插件 | 34.07% | 35.55 |
| `pair-10-player-dark.png` | 首页 / 播放页 深色 | 55.53% | 31.55 |
| `pair-11-licenses.png` | 设置 / 开源许可 | 38.38% | 36.08 |
| `pair-12-about.png` | 设置 / 关于 | 23.78% | 29.93 |

> 注意：这几组不是「同一页面」的严格对照 —— Folia 是桌面信息架构（歌单/电台/专辑/本地 五个顶部分段 + Bravais 墙面），广乐按任务要求改成手机结构（首页/搜索/我的 三 Tab）。因此差异率里既有「同一视觉规则是否一致」的差异，也有「页面内容本来就不同」的差异。设置页之所以差异最大，是因为 Folia 设置是分栏桌面面板（左栏 12 个分区、右栏内容），广乐是单列手机设置页。

---

## 2. 逐项差异（0.0.5 自报 7 条）

### 2.1 图标 → Folia 原版 Lucide

- 从 Folia 的 `node_modules/lucide-react` 提取其**实际引用到的 219 个图标名**，再按广乐用到的 169 枚生成 `feature/library/src/main/java/com/guangyue/feature/library/design/icons/GuangYueIcons.kt`（每枚 24×24 viewport，path 直接照抄 lucide 的 `node` 数据，stroke 2 / round cap & join）。
- 全部页面（首页、搜索、我的、设置、许可、关于、插件、下载、播放、歌词、音质选择、歌词舞台）已无任何 `androidx.compose.material.icons` 引用；`material-icons-core` 与 `material-icons-extended` 从 `app`、`feature/library`、`feature/search`、`feature/player`、`core/plugin` 的 `build.gradle.kts` 全部删除。
- 图标映射替代：`ErrorOutline → AlertCircle`、`MusicNote → Music`、`SkipPrevious/Next → ChevronLeft/Right`、`ExpandLess/More → ChevronUp/Down`、`ArrowBack → ArrowLeft`、`Add → Plus`、`Link → Link2`、`Refresh → RefreshCw`、`Delete → Trash2`、`Hearing → AudioLines`。
- 许可：Lucide 为 ISC（Cole Bemis 2013–2022 + Lucide Contributors 2022），全文随包在 `res/raw/license_lucide.txt`，App 内「开源许可」页可读。

### 2.2 背景 → Folia 底色

- Folia 源码 `src/services/baseThemes.ts`：`DEFAULT_THEME`（午夜墨染）bg `#09090b`；`DAYLIGHT_THEME`（日光素白）bg `#f5f5f4`、accent `#ea580c`；`index.css` body `background-color: #09090b; color: #e4e4e7`。**没有渐变，也没有大理石流纹**。
- 广乐 0.0.6：`Gyp.page` = 深 `#09090B` / 浅 `#F5F5F4`；`Modifier.marbleBackground()` 只填纯色（函数名沿用，实现已换成纯色）；0.0.5 的 5 层 radial-gradient + SVG 贝塞尔流纹 + 网点全部删除（原 `design/README.md` 是 0.0.5 旧稿，0.0.6 不再采用）。
- 实测取样（并排图同一坐标）：深色 Folia `#09090B` / 广乐 `#09090B`，浅色 Folia `#F5F5F4` / 广乐 `#F5F5F4`。

### 2.3 字体

- Folia 的衬线字体是 `Folia Noto Serif SC`（`@fontsource/noto-serif-sc` 的 400 与 500 两份 woff2，见 `src/index.css` `@font-face`）。
- 广乐打包 Noto Serif SC 的 **400 与 500 两个子集**：`res/font/noto_serif_sc_subset_400.ttf`（493,052 B）+ `noto_serif_sc_subset_500.ttf`（496,808 B）= **989,860 B（0.944 MB < 1 MB 目标）**，按广乐实际用到的字形裁剪过（缺字校验为空）。
- `GuangYueDesign.Serif` = `FontFamily(400 Normal, 500 Medium, 500 SemiBold)`；标题、区块标题、空态标题、播放页歌名走 Serif，正文走 `FontFamily.SansSerif`（Folia 正文是 `Inter` + CJK 无衬线栈，广乐不打包 Inter，回退系统无衬线）。
- 许可：SIL OFL 1.1，全文在 `res/raw/license_noto_serif_sc.txt`，App 内许可页单列一条。

### 2.4 模糊 / 毛玻璃

- `MarbleBackground.kt`：`canBlur()` = `SDK_INT >= 31 && !ActivityManager.isLowRamDevice`；结果写入 `LocalCanBlur`（`staticCompositionLocalOf`），`Modifier.glassPanel()` / `Modifier.foliaGlass()` 只在 `LocalCanBlur.current` 为真时挂 `Modifier.blur(16.dp, BlurredEdgeTreatment.Unbounded)`，否则只用 `GyGlass.panel` 半透明 + `GyGlass.border` 发丝边框。
- 玻璃面板色照 Folia `AppShell.tsx`：深 `rgba(0,0,0,0.40)`（0x66000000）/ 浅 `rgba(255,255,255,0.65)`（0xA6FFFFFF）；边线深 `rgba(255,255,255,0.10)` / 浅 `rgba(0,0,0,0.05)`。
- 模糊只用在静态层（顶栏、底栏、迷你播放条、播放控件条），列表项与滚动内容不加模糊；歌词流的逐行模糊在 `canBlur()` 为假时退化为纯透明度分级。

### 2.5 空库首页

- `HomeScreen` 的三段（推荐歌单 / 最近播放 / 热门榜单）在无数据时都渲染 `EmptyStrip`：88 dp 占位方块（歌单/专辑画 Folia 的「两条 5px 顶卡条 76%/88%」——照 `Lattice.css` 的合集卡片条）+ 说明文字 + 「去添加音源」按钮，点击直达「音源插件」二级页。
- 无插件时顶部额外不显示插件入口卡片（按任务要求：首页只放内容）。设备实测 `uiautomator dump` 首页文本：`广乐 / 推荐歌单 / 还没有音源插件 / 去添加音源 / 最近播放 / 还没有播放记录 / 热门榜单 / 添加音源后这里出现榜单 / 去添加音源 / 首页 / 搜索 / 我的`。

### 2.6 导航

- Folia 桌面版没有底部 Tab：主视图是 home / player / lattice，靠 Electron 标题栏 + 应用内 view 切换，唯一的「分段」是播放侧面板 `UnifiedPanel` 底部 `Segments`（36 dp 高、圆角 10 dp、选中标记圆角 8 dp、13 px 文字）。
- 广乐 0.0.6 按任务要求改成手机信息架构：**底部 3 Tab（首页 / 搜索 / 我的）**，Tab 项本身沿用 Folia Segments 的规格（56×28 dp 选中胶囊、圆角 8 dp、18 dp 图标 + 12 sp 标签、`tween(220ms, easeOut)` 过渡）；二级页（音源插件 / 下载管理 / 设置 / 开源许可 / 关于）用 `FoliaTopBar` 的返回方块 + 标题，**底栏隐藏**；播放页全屏、底栏隐藏，上滑进入 / 下滑关闭（位移阈值 180 dp 或甩动），播放页内左右滑切「封面 / 歌词」。
- 0.0.5 的自定义青蓝胶囊 Tab 与封面流已删除。

### 2.7 逐页数值对齐

数值全部取自 Folia 源码（`src/components/modal/settings/kit/*`、`src/services/motion.ts`、`kitTheme.ts`、`AppShell.tsx`、`LatticeChrome.css`、`Segments.tsx`）：

| 令牌 | Folia 源码值 | 广乐实现 |
|---|---|---|
| 圆角 | 卡片 12 / 行 10 / 输入框 8 / 小按钮 6 / 选中标记 8 / 胶囊 999 | `GyRadius{card=12,row=10,field=8,quiet=6,mark=8,capsule=999}` |
| 间距 | 组上距 28（首组 20）/ 页面横 16 / 行最小高 56 / 行上下 10 / 子行缩进 40 | `GySpace{group=28,pageH=16,rowMin=56,m=10,subIndent=40}` |
| 字号 | 标题 20/32 semibold、组标题 13/20 semibold、行 14/20、说明 12/18 | `GyText{title=20/32,label=13/20,row=14/20,hint=12/18}` |
| 颜色 | `--st-t1/t2/t3` = 主文字 / 62% / 42%；`--st-line` 8%、`--st-fill` 7%、`--st-track` 17% | `Gyp.ink/t2/t3/hairline/tint/track`（按 ink 的 alpha 计算） |
| 开关 | 40×24、圆角 full、滑钮 18 dp、`shadow 0 1px 2px rgba(0,0,0,.3)`、位移 16 dp、按下加宽 22 dp | `FoliaToggle`（Material3 Switch 40×24 + Folia 配色） |
| 分段 | 容器 36 dp / 圆角 10 / `--st-fill`；选项 32 dp / 圆角 8 / 选中 `--st-raise` | `FoliaSegmented` |
| 按钮 | fill/primary 高 32 圆角 8 13 px；quiet 高 24 圆角 6 12 px；按下 `scale(0.97)` | `FoliaButton(Fill/Primary/Quiet)` |
| 输入框 | 高 32 / 圆角 8 / `--st-fill` / 13 px / focus 边 `--st-accent` | `FoliaTextField` |
| 动效 | fast 120 ms、move 220 ms、unfold 300 ms、show 480 ms；ease out `[.22,1,.36,1]`、standard `[.4,0,.2,1]`、inOut `[.65,0,.35,1]` | `GyMotion` |
| 播放控件 | 44×44、圆角 12、图标 20；时间 12 px 等宽 tabular-nums 60% | `TransportButton`、`FlowPlayerBar` |

新增/重写的设计层文件：`design/DesignTokens.kt`（令牌）、`design/GuangYueDesign.kt`（主题/字体）、`design/MarbleBackground.kt`（底色 + 玻璃）、`design/FoliaKit.kt`（Row/Group/Toggle/Segmented/Button/TextField/EmptyState/FlowChips）、`design/FoliaScaffold.kt`（TopBar/SectionTitle/BottomNav）、`design/FoliaDraw.kt`（发丝线/描边/卡片面/玻璃/无涟漪点击）。

---

## 3. 剩余差异（未做到「一模一样」的部分）

1. **信息架构不同（有意为之）**：Folia 是桌面五分段 + Bravais 墙面 + 侧面板；广乐是手机三 Tab + 二级页 + 全屏播放页。因此首页/歌单/专辑/本地没有逐一对应页，并排图里这几组差异率不具可比性。Folia 的「Bravais 无限墙面」「Lattice 队列拼贴」「思索模式」「凝彩可视化」都没有实现 —— 它们属于播放视图，不属于 0.0.6 范围。
2. **设置页仍是单列手机版**：Folia 是「左栏 12 个分区 + 右栏内容」的桌面面板（分区：外观/播放页/主页与歌单/播放控制/歌词/通用/快捷操作/连接与集成/存储与备份/性能与动效/开发者/帮助）。广乐只有「外观 / 关于」两组共 4 行。差异率 71.14%（深）/30.72%（浅）主要来自这里。
3. **正文无衬线字体不是 Inter**：Folia 正文 `Inter` + CJK 栈，广乐回退系统无衬线（`FontFamily.SansSerif`）。Inter 只需再加 ~300 KB，但本版为守住 9 MB 预算没有打包。
4. **毛玻璃是近似**：Folia 用 CSS `backdrop-filter` 直接模糊下层像素；Compose 的 `Modifier.blur` 只模糊**元素自身内容**，无法模糊背后内容。广乐的做法是「半透明面板 + 前景可模糊」，视觉接近但不是像素级同效。Android 12 以下与低端机是纯半透明。
5. **图标不是 1:1 全部**：Folia 引用 219 枚 Lucide，广乐打包 169 枚（用不到的没带）；少数语义替代（AlertCircle 代 ErrorOutline 等）—— 见 2.1。
6. **透明窗口圆角/标题栏**：Folia 桌面版有 Electron 标题栏（`TITLEBAR_REVEAL_HEIGHT=56`、win32 圆角 8 / 其它 18）。Android 侧用系统状态栏 + insets，标题栏不可移植，未实现。
7. **字体字重只有 400/500**：Folia 衬线只用 400/500，广乐一致；但广乐把 500 也用于 SemiBold（无 600 子集），个别标题的字重比 Folia 轻一档。
8. **没有 Macrobenchmark**：本机离线，Gradle 缓存里没有 `androidx.metrics:performance`，`com.android.test` 插件也需联网，故改用自研 `FrameJank` + `withFrameNanos` 打点（见第 5 节）。

---

## 4. APK

| 项 | 值 |
|---|---|
| release APK | **8,672,942 字节**（8.27 MiB / 8.67 MB） |
| 0.0.5 基线 | 8,194,554 字节 |
| 增量 | +478,388 字节，其中两个字体子集合计 989,860 B（APK 内压缩后 428,186 B） |
| 目标 | ≤ 9 MB ✅（余量约 0.33 MB） |
| 优化 | R8 + 资源压缩开启；`classes.dex` 压缩后 3,734,592 / 原始 8,271,268；APK 共 248 entries |
| baseline profile | `assets/dexopt/baseline.prof`（10,465 B）+ `baseline.profm`（1,283 B）随包 ✅ |

超出预算的处理：没有超，余量 0.33 MB。为守住预算，Log 类调试代码已随 R8 剥除，`FrameJank` 只在 debug 构建启用。

---

## 5. 性能数据

`feature/library/.../perf/FrameJank.kt`：debug 构建下用 `withFrameNanos` 逐帧打点，每 120 帧汇总一次到 logcat tag `GyJank`；判据 25 ms（> 25 ms 记为 jank）。Robolectric 下强制关闭（否则 `compose.waitForIdle()` 永不空闲，截图测试会挂死）。

设备：Android 14（sdk 34）模拟器 `gy34`，1080×2400 @420 dpi，GPU swiftshader_indirect；debug APK（25,521,323 B）。

| 场景 | 帧数 | jank 帧 | 掉帧率 | 最差帧 | 平均 | p95 |
|---|---|---|---|---|---|---|
| 冷启动后单次上滑 | 120 | 5 | 4.17% | 349 ms | 22.7 ms | 16 ms |
| 连续 6 次上滑+下滑 | 600 | 5 | 0.83% | 349 ms | 17.3 ms | 16 ms |
| 同上累计 | 1,080 | 5 | **0.46%** | 349 ms | 16.7 ms | 16 ms |
| force-stop 重启后 8 组上滑+下滑 | 720 | 8 | **1.11%** | 166 ms | 16.5 ms | 16 ms |

结论：**稳态掉帧率 0.46%–1.11%，p95 = 16 ms（满 60 fps）**；166–349 ms 的长帧只出现在冷启动首帧（字体加载 + 首屏重组），滚动过程中不再出现。

LazyColumn / LazyGrid 均使用稳定 key：推荐歌单 `playlist:<id>`、最近播放 `track:<key>`、榜单 `chart:<plugin>:<title>:<artist>`、搜索结果 `platform:id`。

---

## 6. 测试

| 任务 | 结果 |
|---|---|
| `:core:plugin:test` | **75 用例，0 失败 0 错误** ✅（未改动插件运行时源码，只删了两行 material-icons 依赖） |
| `:app:testDebugUnitTest` | **16 用例，0 失败 0 错误** ✅ `MainActivitySmokeTest` 1 个 + `ScreenshotTest` 15 个 |
| `:app:assembleRelease` | BUILD SUCCESSFUL ✅ |
| `:app:compileDebugKotlin` / `:feature:*:compileDebugKotlin` | BUILD SUCCESSFUL ✅ |

截图用例（`app/src/test/java/com/guangyue/music/ui/ScreenshotTest.kt`，Robolectric SDK 34、411×891 dp @xxhdpi、NATIVE 图形模式）：`home-empty-light`、`home-empty-dark`、`home-filled-light`、`home-filled-dark`、`search-empty-light`、`search-empty-dark`、`profile-light`、`profile-dark`、`profile-filled`、`player-idle-dark`、`plugins-empty`、`settings-light`、`settings-dark`、`licenses`、`about`。

---

## 7. 许可

- 根 `LICENSE`：AGPL-3.0 全文（34,020 B）。
- 根 `NOTICE` / `THIRD_PARTY.md`：致谢 Folia（AGPL-3.0，https://github.com/folia-music/folia），列出随包资源与许可：
  - Lucide 图标（ISC）→ `res/raw/license_lucide.txt`（3,208 B）
  - Noto Serif SC 子集 400/500（SIL OFL 1.1）→ `res/raw/license_noto_serif_sc.txt`（6,222 B）
- App 内「我的 → 开源许可」页列出 6 块：广乐 AGPL-3.0、Folia AGPL-3.0、Lucide ISC、Noto Serif SC SIL OFL 1.1、第三方组件、AGPL-3.0 摘要。

---

## 8. 文件清单

新增：
- `feature/library/.../design/{DesignTokens,GuangYueDesign,MarbleBackground,FoliaKit,FoliaScaffold,FoliaDraw}.kt`
- `feature/library/.../design/icons/GuangYueIcons.kt`（169 枚）
- `feature/library/.../perf/FrameJank.kt`
- `feature/library/.../screen/ProfileScreen.kt`
- `feature/library/src/main/res/font/noto_serif_sc_subset_{400,500}.ttf`、`res/raw/license_{lucide,noto_serif_sc}.txt`
- `NOTICE`、`docs/ui-0.0.6/**`

重写：
- 页面：`HomeScreen`、`ProfileScreen`、`SearchScreen`、`SettingsScreen`（含 Licenses/About）、`DownloadScreen`、`PluginManagerScreen`、`PlayerScreen`、`LyricFlowScreen`、`LyricStageScreen`、`QualityPickerSheet`
- 状态：`HomeViewModel`、`SearchViewModel`
- 外壳：`app/.../nav/Root.kt`、`nav/NavHost.kt`
- 测试：`ScreenshotTest.kt`
- 构建：`app/build.gradle.kts`（versionCode 6 / versionName 0.0.6 / roborazzi 输出目录 / 删 material-icons）、`feature/{library,search,player}/build.gradle.kts`、`core/plugin/build.gradle.kts`（仅删依赖行）

未改：`core/plugin` 的插件运行时源码（`src/main/**`）。