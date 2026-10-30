# 广乐 0.0.8 · Folia 逐页/逐组件对照表

> 目标：把 Folia（`D:\workspaces\_folia-src`，Electron + React + Tailwind）的界面**逐页 1:1 翻译成原生 Compose**，
> 数值照源码；手机只改布局容器（侧栏 → 底部 3 Tab、多列 → 2 列/横滑、统一边距、迷你播放条、inset）。
> 不做「借 Folia 配色自己设计一套」，也不引入 Material 默认样式。
>
> 本表每个单元格读完再改，`✅` 表示数值/形状/动效已与源码一致，`🔧` 表示本轮从「不一致」改成一致，
> `➖` 表示手机 IA 的有意差异（会写原因）。
>
> 并排截图在 `docs/ui-0.0.8/`：Folia 原版在 `folia-*.png`（来源 `docs/ui-0.0.6/folia/`，780×1688），
> 广乐在 `*-light.png` / `*-dark.png`（Roborazzi 1080×2340，Robolectric w411dp-h891dp-xxhdpi）。

---

## 0. 页面级对照（Folia 页面 → 广乐页面）

| Folia 页面（源码） | 广乐页面（Compose） | 手机容器改法 | 状态 |
| --- | --- | --- | --- |
| `src/components/app/AppShell.tsx`（固定全屏 flex-col + 顶部 8 单位拖拽区 + `overflow-hidden`） | `app/.../nav/Root.kt`（`GuangYueRoot` + `ShellColumn`） | 拖拽区去掉（手机无窗口拖动）；`ShellColumn` = 内容 `weight(1f)` + 迷你播放条 + 底栏 | ✅ |
| `src/components/app/Home.tsx` + `home/Grid3D.tsx`（首页 3D 墙 + 顶部标题 + 胶囊导航） | `feature/library/home/HomeScreen.kt` | 3D 墙 → 纵向 LazyColumn + 横滑行；胶囊导航 → 底部 3 Tab | ➖ 3D 墙性能/touch 不适合手机，内容分区保留 |
| `src/library/suites/grid/**`（网格：专辑/艺术家/歌单） | `feature/library/screen/ProfileScreen.kt` + `DownloadScreen.kt` | 多列网格 → 2 列 | ✅ |
| `src/components/modal/settings/**`（设置面板，`kit/*` 设计组件） | `feature/library/screen/SettingsScreen.kt` | 面板 → 整页 | ✅ |
| `src/components/modal/settings/kit/Row.tsx` 等（行/组/按钮…） | `feature/library/design/FoliaKit.kt` | 无改动（手机也这套） | 🔧 见第 2 节 |
| `src/components/app/PlayerPanel.tsx` + `floating-player/**` | `feature/player/screen/PlayerScreen.kt` | 浮动面板 → 全屏播放页；图片旋转改成 `Canvas` 转盘 | ➖ 手机没有「悬浮窗」IA |
| `src/components/app/UnifiedPanel.tsx`（搜索/列表统一面板） | `feature/search/SearchScreen.kt` | 面板 → 整页；顶部音源选择器保留 | ✅ |
| `src/components/modal/**`（对话框） | `GuangYueDesign` / `FoliaMenu` / 各页 `Dialog` | 无改动 | ✅ |

---

## 1. 设计 token（`--st-*` / Tailwind → Compose）

Folia 的 `kit.css` 定义了一套 `--st-*` 变量；广乐在 `design/DesignTokens.kt` 里一比一命名落地。

| Folia 变量 / 类 | Folia 值 | 广乐 Compose | 文件 | 状态 |
| --- | --- | --- | --- | --- |
| `--st-page` | 深 `#09090B` / 浅 `#F5F5F4` | `Gyp.page` | DesignTokens.kt | ✅ |
| `--st-card` | `--st-raise` 之上的卡片底 | `Gyp.card` | 同 | ✅ |
| `--st-line` | 1px ring | `Gyp.hairline`，`drawRing()` 画 1dp 环 | 同 | ✅ |
| `--st-t1` / `--st-t2` / `--st-t3` | 三级文字 | `Gyp.ink` / `Gyp.t2` / `Gyp.t3` | 同 | ✅ |
| `--st-accent` / `--st-on-accent` | 强调色 | `Gyp.accent` / `Gyp.onAccent` | 同 | ✅ |
| `--st-fill` / `--st-fill2` | 胶囊底 / hover 底 | `Gyp.tint` / `Gyp.fill2` | 同 | ✅ |
| `--st-track` / `--st-knob` | 开关轨道 / 滑钮 | `Gyp.track` / `Gyp.knob` | 同 | ✅ |
| `--st-ok` / `--st-warn` / `--st-danger` | 状态色 | `Gyp.ok` / `Gyp.warn` / `Gyp.danger` | 同 | ✅ |
| `rounded-xl` = 12px | 卡片圆角 | `GyRadius.card = 12.dp` | 同 | ✅ |
| `rounded-lg` = 8px | 输入框/分段 | `GyRadius.field = 8.dp` | 同 | ✅ |
| `rounded-md` = 6px | quiet 按钮 | `GyRadius.quiet = 6.dp` | 同 | ✅ |
| `min-h-14` = 56px | 行最小高度 | `GySpace.rowMin = 56.dp` | 同 | ✅ |
| `pl-4` / `pr-4` / `px-4` = 16px | 行内边距 / 页面边距 | `GySpace.pageH = 16.dp` | 同 | ✅ |
| `pl-10` = 40px | 子行缩进 | `GySpace.subIndent = 40.dp` | 同 | ✅ |
| `mt-7` / `mt-5` = 28px / 20px | 组间距 | `GySpace.group = 28.dp` | 同 | ✅ |
| `text-[13px] leading-5` | 行文字 | `GyText.row = 13.sp` / `GyText.rowHeight = 20.sp` | 同 | 🔧 |
| `text-xs leading-[18px]` | 提示文字 | `GyText.hint = 12.sp` / `hintHeight = 18.sp` | 同 | ✅ |
| `text-sm leading-5` | 行标题 | 见第 2 节 `FoliaRow` | 同 | 🔧 |
| 字体 | Folia `font-sans` + 标题 `Serif` | `GuangYueDesign.Sans` / `.Serif`（Noto Serif SC） | GuangYueDesign.kt | ✅ |
| `backdrop-blur-sm/md` | 静态层毛玻璃 | `Modifier.glassPanel()`（只在标题栏/底栏/迷你条上用） | FoliaDraw.kt | ✅ |

---

## 2. 组件级对照（`src/components/modal/settings/kit/*.tsx` → `FoliaKit.kt`）

### 2.1 `Row.tsx` → `FoliaRow`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 行容器 | `relative flex min-h-14 flex-wrap items-center gap-x-6 py-2.5 pr-4 pl-4`（子行 `pl-10`） | `Column(fillMaxWidth().heightIn(min = 56.dp))`，`padding(start = 16/40, end = 16, top = 10, bottom = 10)` | ✅ |
| 分割线 | `.st-row::before`：`top:0;right:0;left:16px;height:1px`（`[data-sub]` 时 `left:40px`，卡片首行不画） | `Modifier.foliaHairline(top = !first, inset = if (sub) 40.dp else 16.dp)` | ✅ |
| 主文字 | `text-[13px] leading-5 text-[var(--st-t1)]` | `fontSize = GyText.row`（13sp）/ `lineHeight = rowHeight`（20sp）/ `color = Gyp.ink` | 🔧 本轮从 14sp 改成 13sp |
| 副文字 | `text-xs leading-[18px] text-[var(--st-t2)]` | `GyText.hint`（12sp / 18sp）+ `Gyp.t2` | ✅ |
| 行尾 | `ml-auto flex max-w-full flex-wrap items-center justify-end gap-2` | `Row(horizontalArrangement = spacedBy(GySpace.s))` 放在 `weight(1f)` 右侧 | ✅ |
| 链接箭头 | `ChevronRight size={16} text-[var(--st-t2)]` | `Icon(ChevronRight, size = 16.dp, tint = Gyp.t2)` | ✅ |
| 前置图标 | `size={18}` | `Icon(size = 18.dp)` | ✅ |
| 开关前面点 | `h-1.5 w-1.5 rounded-full bg-[var(--st-warn)]` | `Box(size = 6.dp, CircleShape, Gyp.warn)` | ✅ |

### 2.2 `Group.tsx` → `FoliaGroup`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 组间距 | `GROUP_SPACING_CLASS = 'mt-7 first:mt-5'`（28px / 20px） | 调用方 `arrangement = spacedBy(GySpace.group)`（28dp） | ✅ |
| 组标题 | `h3 text-[13px] font-semibold leading-5 text-[var(--st-t2)]`，行高 `min-h-6`，`px-1`，`mb-2` | `Text(GyText.label 13sp / labelHeight, SemiBold, Gyp.t2)`，容器 `padding(horizontal = 4.dp).heightIn(min = 24.dp)` + `Spacer(8.dp)` | 🔧 本轮 `px-1` 由 16dp 改 4dp |
| 卡片 | `st-card overflow-hidden rounded-xl bg-[var(--st-card)] shadow-[0_0_0_1px_var(--st-line)]` | `Modifier.foliaCardSurface()` = `clip(12dp) + background(Gyp.card) + drawRing(Gyp.hairline)` | ✅ |
| 脚注 | `mx-1 mt-2 text-xs leading-[18px] text-[var(--st-t3)]` | `Text(GyText.hint/hintHeight, Gyp.t3)` + `padding(horizontal = 4.dp)` | 🔧 同上 |
| 预览块 | `m-3 h-[136px] rounded-lg bg-[var(--st-pv)]` | 未用（广乐的组里没有预览块） | ➖ |

### 2.3 `Toggle.tsx` → `FoliaToggle`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 轨道 | `h-6 w-10 rounded-full bg-[var(--st-track)]`（选中 `--st-accent`） | `size(width = 40.dp, height = 24.dp)`，`CircleShape`，`Gyp.track` / `Gyp.accent` | ✅ |
| 滑钮 | `left-[3px] top-[3px] h-[18px] w-[18px] rounded-full` | `size(18.dp)`，`offset(x = 3.dp, y = 3.dp)` | ✅ |
| 选中位移 | `translate-x-4`（16px） | `offset.x = if (checked) 19.dp else 3.dp`（3 + 16） | ✅ |
| 按下形变 | `group-active:w-[22px] translate-x-3` | `interactionSource` 按下时 `width = 22.dp`，x 回 16dp | ✅ |
| 过渡 | `transition-[translate,width,background-color]`（默认 120ms） | `animateDpAsState(tween(GyMotion.FAST_MS, easeOut))` | ✅ |
| 阴影 | `shadow-[0_1px_2px_rgba(0,0,0,0.3)]` | 未画（Compose 单侧投影成本高，肉眼几乎不可见） | ➖ 有意省略 |

### 2.4 `Segmented.tsx` → `FoliaSegmented`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 容器 | `min-h-8 rounded-lg bg-[var(--st-fill)] p-0.5` | `heightIn(min = 32.dp)`，`RoundedCornerShape(GyRadius.row = 10.dp)`，`Gyp.tint`，`padding(2.dp)` | 🔧 圆角从 8dp 改 10dp（`rounded-lg` 在 kit 里是 10px 的自定义覆盖） |
| 选项 | `h-7 text-[13px] rounded-md px-3`（`fill` 时 `px-1.5 flex-1`） | `height(28.dp)`，`GyText.label`，`padding(horizontal = 12.dp)` | ✅ |
| 选中底 | `layoutId` 的 `motion.span`：`absolute inset-0 bg-[var(--st-raise)]` + `borderRadius: 6` + `shadow 0 1px 2px rgba(0,0,0,0.2)` | `Box(matchParentSize, RoundedCornerShape(6.dp), Gyp.raise)` + `animateDpAsState` 位置 | ✅ |
| 滑动 | `kitTransition('move')` = 0.22s / `cubic-bezier(0.22,1,0.36,1)` | `tween(220, easing = GyMotion.easeOut)` | ✅ |
| 文字色 | 选中 `--st-t1`，未选 `--st-t2` | `Gyp.ink` / `Gyp.t2`，`animateColorAsState(tween(120))` | ✅ |

### 2.5 `Button.tsx` → `FoliaButton`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 基础 | `inline-flex items-center justify-center gap-1.5 whitespace-nowrap` | `Row(horizontalArrangement = spacedBy(6.dp))` | ✅ |
| `fill` | `h-8 rounded-lg bg-[var(--st-fill)] px-3 text-[13px] text-[--st-t1]` | `height(32.dp)`，`GyRadius.fieldShape()`，`Gyp.tint`，`padding(horizontal = 12.dp)`，`GyText.label` | ✅ |
| `primary` | `h-8 rounded-lg bg-[--st-accent] px-3 text-[13px] text-[--st-on-accent]` | 同上，`Gyp.accent` + `Gyp.onAccent` | ✅ |
| `quiet` | `h-6 rounded-md px-1.5 text-xs text-[--st-t2]` | `height(24.dp)`，`GyRadius.quietShape()`，`padding(horizontal = 6.dp)`，`GyText.hint`，`Gyp.t2` | ✅ |
| 图标 | `size={14}` | `Icon(size = 14.dp)` | ✅ |
| 按下 | `enabled:active:scale-[0.97]` | `graphicsLayer { scaleX = scaleY = if (pressed) 0.97f else 1f }` | ✅ |
| 禁用 | `disabled:opacity-40`，**fill 档额外 `disabled:bg-transparent`** | `alpha = 0.4f`，fill 档禁用时底色 `Color.Transparent` | 🔧 这是「空白白胶囊」的根因，本轮改成与 Folia 一致 |
| 过渡 | `transition-[background-color,color,opacity,scale]` | `animateColorAsState` / `animateFloatAsState(tween(120))` | ✅ |

### 2.6 `TextField.tsx` → `FoliaTextField`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 表面 | `h-8 rounded-lg border border-transparent bg-[--st-fill] text-[13px]` | `height(32.dp)`，`GyRadius.fieldShape()`，`border 1dp`（透明 → 聚焦 `Gyp.accent`） | ✅ |
| 内边距 | `px-2.5`（10px） | `padding(horizontal = 10.dp)` | ✅ |
| 占位 | `placeholder:text-[--st-t3]` | `Gyp.t3` | ✅ |
| 聚焦 | `focus-within:border-[--st-accent]`，无外发光 | `animateColorAsState` 边框色 | ✅ |
| 宽度 | 设置面板里固定 `w-[260px]` | 手机改 `fillMaxWidth` | ➖ 手机宽度不足 |

### 2.7 `Status` → `FoliaStatus`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 形状 | 胶囊 `rounded-full px-1.5 text-[11px] leading-[18px]` | `CircleShape`，`padding(horizontal = 6.dp)`，11sp / 18sp | ✅ |
| 底色 | `bg-[var(--st-fill)]` + 语义色文字 | `Gyp.tint` 底 + `FoliaTone` 对应色 | ✅ |
| 圆点 | `h-1.5 w-1.5 rounded-full` | `Box(size = 6.dp, CircleShape)` | ✅ |

### 2.8 `Menu` → `FoliaMenu`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 面板 | `rounded-xl border border-[--st-line] bg-[--st-menu] p-1 shadow-xl` | `clip(12.dp)` + `Gyp.menu` + `drawRing(Gyp.hairline)` + `padding(4.dp)` + 阴影 | ✅ |
| 项 | `h-7 rounded-[6px] px-2 text-[13px] gap-2` | `height(28.dp)`，`RoundedCornerShape(6.dp)`，`padding(horizontal = 8.dp)`，`spacedBy(8.dp)` | ✅ |
| 危险项 | `text-[--st-danger]` | `Gyp.danger` | ✅ |
| 分割线 | `my-1 h-px bg-[--st-line]` | `Spacer(height = 1.dp)` + `Gyp.hairline` | ✅ |
| 出现 | `@keyframes st-pop-in`：`translateY(var(--st-pop-shift)) scale(0.98)` → 1 | `animateFloatAsState` 从 `0.98f` 到 `1f` + `translateY(4dp→0)`，`tween(SHOW_MS = 480, easeOut)` | ✅ |

### 2.9 `Unfold` → `FoliaUnfold`

| 项 | Folia 源码 | 广乐 Compose | 状态 |
| --- | --- | --- | --- |
| 展开 | `grid-template-rows: 0fr → 1fr` + `opacity 0 → 1`，`visibility` 延迟到动画结束 | `animateFloatAsState` 驱动高度（`0 → measuredHeight`）+ `alpha`，`tween(UNFOLD_MS = 300, easeOut)`，结束时才切 `visibility`（Compose 里是 `if (expanded || progress > 0f)`） | ✅ |
| 触发 | 行尾小箭头 `grid h-[18px] w-[18px]` | 行尾「详情/收起」文本 + `animateFloatAsState` 旋转的箭头 | ➖ 手机触控目标更大 |

---

## 3. 页面级组件对照

| 组件 | Folia 源码 | 广乐 Compose | 差异 | 状态 |
| --- | --- | --- | --- | --- |
| 顶栏 | `Grid3D.tsx`：`h1 text-2xl font-bold tracking-tight` + `px-4 md:px-8` + `pt-8`（拖拽区高度） | `FoliaScaffold.kt` 的 `FoliaTopBar`：`GyText.title`（20sp / 32sp）SemiBold Serif，`padding(start = 16, end = 12)`，`top = max(statusBars, displayCutout) + 16`，`bottom = 8` | 手机把 `pt-8`（拖拽区）换成状态栏 inset；字号 24px → 20sp（手机一屏宽度） | 🔧 |
| 底栏 | `Grid3D.tsx` 胶囊导航：`rounded-full backdrop-blur-md p-1` + `px-4 py-1.5 rounded-full text-xs` + 选中 `absolute inset-0 rounded-full bg-white shadow-sm` | `FoliaBottomNav`：`glassPanel(corner = 0)` + `padding(bottom = navigationBars)`，每项 56×28dp mark + 18dp 图标 + `GyText.hint` | 胶囊浮层 → 贴底 Tab（手机惯例）；选中态用 `animateFloatAsState(tween(220, easeOut))` 的 mark 底色，等价于 `layoutId` 滑片 | ➖ |
| 迷你播放条 | `floating-player/**` + `ProgressBar.tsx`（`h-2 rounded-full bg-white/10` + `transition-[width] duration-300 ease-out`） | `FoliaMiniPlayer`：`glassPanel` + 48dp 封面 + 标题/副标题 + `ProgressLine`（2dp，`animateFloatAsState(tween(300, easeOut))`） | 浮动可拖动 → 固定一条 | ➖ |
| 首页分区标题 | `BravaisSeamHome*.tsx` 的分区头 | `FoliaSectionTitle`：`padding(start = 16, end = 16, top = 12, bottom = 8)`，`GyText.label` SemiBold `Gyp.t2` | ✅ | ✅ |
| 首页卡片 | `collection/*.tsx`：`w-full aspect-square rounded-2xl overflow-hidden shadow-lg` | `PosterTile`（152dp 宽，16:10）/ `SquareTile`（112dp）/ `ChartRow`，全部 `GyRadius.cardShape()`（12dp） | 手机横滑，尺寸按屏宽分配；圆角 16px → 12dp 与 `--st` 卡片一致 | 🔧 |
| 空态 | 设置面板的空态（居中图标 + 标题 + 一句说明 + 一个主按钮） | `FoliaEmptyState`：`GyText.emptyTitle`（17sp + 2.72sp 字距）+ `emptySub`（12sp）+ 可选 `FoliaButton(Primary)` | ✅ | ✅ |
| 播放页 | `PlayerPanel.tsx`：旋转封面 + 进度条 + 控制行 | `PlayerScreen.kt`：`Canvas` 转盘（`rememberInfiniteTransition` 线性旋转，对应 CSS `animation: spin 20s linear infinite`）+ 同款进度条 + 控制行 | 面板 → 全屏 | ➖ |
| 搜索页 | `UnifiedPanel.tsx`：顶部搜索框 + 结果列表 + 音源选择 | `SearchScreen.kt`：`FoliaTopBar` + 搜索框 + `FlowChips` 音源选择 + 结果行 | ✅ | ✅ |
| 音源管理 | `settings` 面板里的插件/音源分组 | `PluginManagerScreen.kt`（单选行 + `FoliaStatus` + 行尾 `FoliaMenu` + `FoliaUnfold` 详情） | ✅ | ✅ |
| 取链源管理 | —（Folia 无对应；洛雪自定义源是广乐独有） | `LxSourceScreen.kt`（同一套 `FoliaKit` 组件搭） | ➖ 新增页面，视觉沿用 kit | ✅ |

---

## 4. 动效对照（`kit/motion.ts` → `GyMotion`）

| Folia | 值 | 广乐 Compose | 用在哪 |
| --- | --- | --- | --- |
| `KIT_MOTION.fast` | 0.12s | `GyMotion.FAST_MS = 120` | 按钮/开关/文字色 |
| `KIT_MOTION.move` | 0.22s | `GyMotion.MOVE_MS = 220` | 分段滑片、底栏选中、菜单出现 |
| `KIT_MOTION.unfold` | 0.3s | `GyMotion.UNFOLD_MS = 300` | `FoliaUnfold` 展开 |
| `KIT_MOTION.show` | 0.48s | `GyMotion.SHOW_MS = 480` | 菜单/弹层出现 |
| `KIT_MOTION.flash` | 2.2s | `GyMotion.FLASH_MS = 2200` | 状态提示高亮 |
| `KIT_MOTION.confirm` | 1.6s | `GyMotion.CONFIRM_MS = 1600` | 危险操作二次确认条 |
| `KIT_EASE.out` | `cubic-bezier(0.22,1,0.36,1)` | `GyMotion.easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)` | 默认缓动 |
| `KIT_EASE.standard` | `cubic-bezier(0.4,0,0.2,1)` | `GyMotion.easeStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)` | 位置类 |
| `KIT_EASE.inOut` | `cubic-bezier(0.65,0,0.35,1)` | `GyMotion.easeInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)` | 折叠 |
| `@keyframes st-page-in` | `opacity 0→1, translateY(6px)→0` | `AnimatedVisibility(enter = fadeIn(tween(220)) + slideInVertically { 6.dp 转 px })` | 二级页进入 |
| `@keyframes st-pop-in` | `translateY(var(--st-pop-shift)) scale(0.98)` → 1 | 同上（4dp / 0.98f） | 菜单/弹层 |
| `transition-[width] duration-300 ease-out` | 进度条 | `animateFloatAsState(tween(300, easeOut))` | 迷你播放条 / 播放页进度 |
| 封面旋转 | CSS `animation: spin 20s linear infinite` | `rememberInfiniteTransition().animateFloat(0f→360f, infiniteRepeatable(tween(20000, LinearEasing)))` | 播放页 |

> 没有用 Material 默认动效：Material 的 `FastOutSlowIn`（0.4/0/0.2/1）只在 `easeStandard` 用得上，
> 其余全部照 `cubic-bezier` 原值建 `CubicBezierEasing`；弹簧动画在 Folia 的 kit 里没有用到，
> 因此广乐也没有引入 `spring()`（避免出现 Folia 里不存在的弹跳）。

---

## 5. 掉帧冒烟

`feature/library/.../perf/FrameJank.kt`：
- `TrackScrollFrames()` 在每个页面的根 Composable 里调用一次，`DEBUG` 下逐帧 `withFrameNanos` 采样；
- 单帧 > 25ms 记一次 jank，每 120 帧输出一行 `frames=… jank=… jankRate=…% worst=…ms avg=…ms p95=…ms` 到 logcat（tag `GyJank`）；
- `release` 里 `FrameJank.enabled` 是常量 `false`，整段统计被 R8 摘掉，零体积；
- Robolectric 下自动禁用（`runningUnderRobolectric` 检测），否则帧时钟不推进会挂死 `waitForIdle()`。

`androidx.metrics:performance`（JankStats）+ Macrobenchmark 需要额外坐标和设备侧 profile 安装，
本机离线环境拿不到该坐标，改用上面这套自采样；等坐标可用时把 `TrackScrollFrames()` 的实现换成
`JankStats` 即可，调用点不用动。

---

## 6. 仍未一致的项（诚实清单）

| 项 | Folia | 广乐 | 原因 |
| --- | --- | --- | --- |
| 首页 3D 墙 | `Grid3D.tsx` 的透视/景深/滚轮推进 | 纵向列表 | 手机屏宽与触控模型不支持 3D 墙；内容分区（推荐/最近/榜单）保留 |
| 窗口拖拽区 | `pt-8` 的可拖动标题栏 | 状态栏 inset | 手机没有窗口 |
| 浮动播放器 | 可拖动、可缩放的悬浮面板 | 固定迷你条 + 全屏播放页 | 手机 IA |
| Toggle 阴影 | `shadow-[0_1px_2px_rgba(0,0,0,0.3)]` | 无 | Compose 逐帧投影成本高，2px 阴影肉眼不可见 |
| 组预览块 | `--st-pv` 136px 预览 | 无 | 广乐的组里没有需要预览的设置项 |
| 字体 | 系统 sans + 自定义 Serif | Noto Serif SC（内置） | 手机没有 Folia 打包的西文 sans，改用系统 + 内置衬线标题，字形接近 |
