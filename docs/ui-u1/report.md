# U1 · Folia 设计地基（Compose）

分支 `port/u1`（worktree `D:\workspaces\GuangYue-u1`），基线 commit `1428a05`。
本段只做设计 token + 基础组件 + 画廊 + 截图；未改 NavHost / Root / MainActivity / `theme/Theme.kt`（旧 `GuangYueTheme` 原样保留，新主题另起名 `FoliaTheme`）。

代码位置：
- `app/src/main/java/com/guangyue/music/ui/design/`：`FoliaColors.kt` `FoliaType.kt` `FoliaShapes.kt` `FoliaMotion.kt` `FoliaBlur.kt` `FoliaTheme.kt` `FoliaComponents.kt` `GalleryScreen.kt`
- `app/src/main/res/values/strings.xml`：**U1b 起所有界面文案的唯一来源**（中文为默认语言）
- `app/src/test/java/com/guangyue/music/ui/design/`：`FoliaTokensTest.kt`（13 个纯 JVM 断言）、`FoliaGalleryScreenshotTest.kt`（Roborazzi 截图）、**U1b 新增** `FoliaU1bTest.kt`（18 个：文案/质感 token/手机化尺寸）、`FoliaComponentsTest.kt`（17 个：组件渲染与交互）
- `tools/u1_side_by_side.py`：并排图脚本（PIL）
- `app/build.gradle.kts`：唯一改动是 `roborazzi.output.dir` → `${rootDir}/docs/ui-u1`（1 行）

验证（U1b 重测，`--offline --rerun-tasks` 全量）：`testDebugUnitTest` **89 过 / 0 败 / 0 跳过**，`assembleDebug` BUILD SUCCESSFUL。

> **数字口径更正**：U1 报告里写的「123 过 / 0 败 / 2 跳过」与实跑不符 —— 本 worktree 里
> 全量 `testDebugUnitTest` 实测只有 89 个用例（U1 时是 54 个：`MainActivitySmokeTest` 1 +
> `FoliaGalleryScreenshotTest` 16 + `FoliaTokensTest` 13 + `ScreenshotTest` 24，0 跳过），
> 也不存在跳过用例。`123/0/2` 应当是把别的会话/别的分支的统计抄了过来，U1b 以实测为准并在此改正。
> U1b 新增 35 个用例（18 + 17），**89 > U1 基线 54**，也无失败。

---

## 1. 颜色 token（`FoliaColors.kt`）

每个字段在源码里都有 KDoc 标注出处；下表是汇总。浅/深两套由 `LocalFoliaColors` 提供，`FoliaTheme(dark=…)` 切换。

| 名称 | 浅 | 深 | Folia 出处 |
|---|---|---|---|
| page（--bg-color） | `#f5f5f4` | `#09090b` | `src/services/baseThemes.ts:20-28` DAYLIGHT_THEME / `:9-17` DEFAULT_THEME |
| ink（--text-primary） | `#1c1917` | `#f4f4f5` | 同上 |
| inkSoft（--text-secondary） | `#44403c` | `#71717a` | 同上 |
| accent（--text-accent） | `#ea580c` | `#f4f4f5` | 同上 |
| onAccent（--text-on-accent） | `#ffffff` | `#17171a` | `kit/kitTheme.ts:12-18` ON_ACCENT_LIGHT / ON_ACCENT_DARK |
| win（--st-win） | `#f3f3f4` | `#1a1a1d` | `kit/kitTheme.ts:24-48` |
| side（--st-side） | `#e9e9eb` | `#141416` | 同上 |
| card（--st-card） | `#ffffff` | `#232327` | 同上 |
| menu（--st-menu） | `#ffffff` | `#2b2b30` | 同上 |
| raise（--st-raise） | `#ffffff` | `#45454c` | 同上（选中标记 / 分段当前项） |
| knob（--st-knob） | `#ffffff` | `#ececee` | 同上（开关未选中的钮） |
| preview（--st-pv） | `#ececef` | `#151517` | 同上 |
| ok | `#1e9a5c` | `#56c98d` | 同上 `--st-ok` |
| warn | `#b97a10` | `#e3a949` | 同上 `--st-warn` |
| error | `#d04444` | `#ef6f6f` | 同上 `--st-err` |
| text2 | ink @62% | ink @62% | `--st-t2` = text-primary 62% |
| text3 | ink @42% | ink @42% | `--st-t3` = 42% |
| line | ink @8% | ink @8% | `--st-line` |
| lineStrong | ink @16% | ink @16% | `--st-line2` |
| fill | ink @7% | ink @7% | `--st-fill`（hover/静默底） |
| fillStrong | ink @12% | ink @12% | `--st-fill2` |
| track | ink @17% | ink @17% | `--st-track`（滑条/开关轨道） |
| glassFill | page @85% | page @85% | `src/index.css:163-168` `.theme-glass-panel` color-mix(--bg-color 85%) |
| glassBorder | ink @12% | ink @12% | 同上 border color-mix(--text-primary 12%) |
| cardFill | page @95% | page @95% | `src/index.css:152-161` `.theme-polaroid-card` |
| cardBorder | ink @5% | ink @5% | 同上 |
| rowFill | 黑 @3.5% | 白 @4% | `src/components/app/search/SearchResultRow.tsx:54-151` |
| rowBorder | 黑 @5% | 白 @6% | 同上 |
| coverPlaceholder | zinc-500 @15% | zinc-500 @15% | 同上 `bg-zinc-500/15` |

## 2. 字体（`FoliaType.kt`）

字体栈：`FontFamily.SansSerif`（Android 系统无衬线）+ 由系统 CJK fallback 提供中文；Folia 侧的 `--font-sans`/`--font-cjk-sans` 见 `src/index.css:14-17`（`ui-sans-serif, system-ui, "Noto Sans CJK SC", "Source Han Sans SC", "PingFang SC", "Microsoft YaHei"`）。Compose 不能逐字体族指定 fallback 链，Android 由 `sans-serif` 在 CJK 字符上自动落到 `Noto Sans CJK`，因此这里用系统族 + 明确文档，而不是打包字体。

数值列（行高/字重按 m00169 放大后）：

| token | 字号/行高/字重 | Folia 原值 | 出处 |
|---|---|---|---|
| headline | 24/30/Bold | 22–24px 标题 | inventory §3（`text-2xl` 档） |
| section | 16/22/ExtraBold，字距 0.3 | `15px/800/1.3/letter-spacing:.3em` | `bravaisHome.css:89-95` |
| bodyStrong | 16/22/SemiBold | `text-sm font-semibold`(14px) | `SearchResultRow.tsx:54-151` |
| body | 14/22/Normal | `text-[13px]` | `kit/Button.tsx:14-18`、`kit/Row.tsx:113` |
| control | 14/20/Medium | `text-[13px]` | `kit/Segmented.tsx:46`、`kit/Tabs.tsx:96` |
| caption | 12/18 | `text-xs leading-[18px]` | `kit/Row.tsx:134` |
| micro | 12/16 | `text-[11px] leading-[14px]` | `kit/Slider.tsx:103` |
| tag | 12/12 | `text-[10px]` | `SearchResultRow.tsx`（标签 chip） |
| numeric | 13/18 Monospace | `font-mono text-xs` | `SearchResultRow.tsx`（时长）、`kit/Slider.tsx:159` tabular-nums |

## 3. 形状 / 间距（`FoliaShapes.kt`）

| token | 值 | Folia 出处 |
|---|---|---|
| sm | 6dp | `kit/Segmented.tsx:53-59` 选中标记 radius 6 |
| md | 8dp | `rounded-lg`（`kit/Button.tsx:14-18`、`kit/TextField.tsx:9`） |
| lg | 12dp | `rounded-xl`（`SearchResultRow.tsx` 封面 h-12 w-12 rounded-xl） |
| xl | 16dp | `rounded-2xl` + inventory §1「卡片圆角统一 16dp」 |
| pill | 50% | `rounded-full`（搜索胶囊、分区胶囊、开关轨道） |
| screenPad | 16dp | 手机重定，见 §5 |
| sectionGap | 18dp | `bravaisHome.css:28 --bravais-home-gap:18px` |
| rowMin | 48dp | `kit/Row.tsx:88 min-h-14`(56px) → 手机 48dp 起 |
| rowPadY | 10dp | `kit/Row.tsx:88 py-2.5` |

## 4. 动效（`FoliaMotion.kt`）

Folia 用 framer-motion 的物理 spring（参数是 `stiffness`/`damping`/隐含 `mass=1`），Compose 的 `spring()` 要 `stiffness` + `dampingRatio`（ζ）。换算方法：

```
ω₀ = √(k / m)            // 自然角频率
c_c = 2·√(k·m)           // 临界阻尼
ζ  = c / c_c = c / (2·√(k·m))
```

| token | Folia 原值 | Compose | 出处 |
|---|---|---|---|
| springCard | `{stiffness:300,damping:30}` | `spring(stiffness=300f, dampingRatio=0.866f)`（30/(2·√300)=0.8660） | `Carousel3D.tsx:42,59` |
| springPlayer | `{stiffness:280,damping:28}` | `spring(stiffness=280f, dampingRatio=0.8366f)`（28/(2·√280)=0.8367，取 0.8366 与帧一致） | `usePlayerBottomBarBottomPx.ts:46`；`FloatingPlayerControls.tsx:38` 仅 stiffness 280 |
| fast | 0.12s | `tween(120, easing = easeOut)` | `kit/motion.ts:12-27 KIT_MOTION.fast` |
| move | 0.22s | `tween(220, easeOut)` | 同上 `.move` |
| unfold | 0.30s | `tween(300, easeOut)` | 同上 `.unfold` |
| show | 0.48s | `tween(480, easeOut)` | 同上 `.show` |
| easeOut | `[.22,1,.36,1]` | `CubicBezierEasing(.22f,1f,.36f,1f)` | `kit/motion.ts:31-38 KIT_EASE.out` |
| easeStandard | `[.4,0,.2,1]` | 同上 `.standard` | 同上 |
| easeInOut | `[.65,0,.35,1]` | 同上 `.inOut` | 同上 |
| PRESS_SCALE | `active:scale-[0.97]` | 0.97f | `kit/Button.tsx:11`、`kit/Chips.tsx:33` |
| THUMB_ACTIVE_SCALE | thumb active `scale(1.15)` | 1.15f | `kit/kit.css:26-32` |
| PAGE_IN_OFFSET_DP | `translateY(6px)` | 6f | `kit/kit.css:151-160` |
| POP_SCALE | `scale(.98)` | 0.98f | `kit/kit.css:128-148` |

`FoliaTokensTest` 断言 ζ 与 0.97/1.15 与源码一致。

## 5. 尺寸换算（Folia 原值 → 手机值 → 原因）

m00169 要求：正文 ≥14sp、次要 ≥12sp、触控 ≥48dp、列表行 56–64dp、页面边距 16dp。

| 位置 | Folia 原值 | 手机值 | 原因 |
|---|---|---|---|
| 正文 | 13px | 14sp | 13px @1x 在 360dp 宽屏上偏小，中文字形需更大字面；手机最小可读正文 14sp |
| 次要文字 | 12px(`text-xs`) | 12sp | 保持，已达标 |
| 标签 chip | 10px | 12sp | 10px 中文不可读 |
| 刻度标签 | 11px | 12sp | 同上 |
| 按钮高 | h-8 = 32px | 48dp | 触控区 ≥48dp（Material/Android 无障碍） |
| 图标按钮 | h-8 w-8 = 32px | 48dp 方框（图标 18dp） | 同上，图标视觉尺寸保持 14→18dp 平衡按压区 |
| 开关轨道 | h-6 w-10 = 24×40 | 24×40dp 轨道，外框 48dp 触控 | 视觉不变，仅扩触控区 |
| 分段容器 | min-h-8 = 32px | 48dp（项 40dp） | 触控；分段是高频操作 |
| Tab | h-9 = 36px | 46dp 文本区（整行 ≥48dp） | 触控 |
| 搜索框 | h-8 = 32px，w-260px | 48dp 高，满宽 | 触控 + 手机没有固定面板宽度 |
| 列表行 | min-h-14 = 56px | 56dp（可变长，两行时更高） | 已达标，保持 |
| 滑条轨道 | h-1 = 4px，input h-5 = 20px | 轨 4dp，拇指 16dp，交互高 48dp | 触控；视觉粗细不变 |
| 卡片圆角 | 16–20px | 16dp | inventory §1 统一 16dp |
| 页面左右边距 | `--bravais-home-pad-x:10px`、kit `pl-4`=16px | 16dp | 窄缝是桌面多列策略，手机单列用 16dp |
| 区块间距 | `--bravais-home-gap:18px` | 18dp | 保持 |
| 空态最小高 | 180px | 180dp | 保持 |

## 6. 桌面动作 → 手机动作

| 桌面 (Folia) | 手机 (广乐) | 原因 |
|---|---|---|
| hover 变色/边框加亮 | 按压 `scale(0.97)` + ripple | 触屏无 hover；按压是唯一可靠反馈（`kit/Button.tsx:11`） |
| 卡片 hover 抬起 | 按压 scale 0.97 + ripple | 同上 |
| 行内 hover 才出现的 `＋`（`SearchResultRow.tsx` 右侧 opacity-0 → group-hover） | 常显 48dp 图标按钮 | 手机没有 hover，隐藏按钮等于不可发现 |
| 右键菜单 | 长按底部弹层（`FoliaSongRow(onLongClick=…)` + `FoliaBottomSheet`） | inventory 红线：右键 → 长按 ModalBottomSheet |
| 居中模态弹窗 / 面板 | `FoliaBottomSheet`：底部滑入、下拉/点遮罩/系统返回关闭，**无右上角叉号** | m00170：底部可达拇指；关闭手势即关闭，不用去够右上角 |
| 悬浮播放条（hover 展开） | 底部常驻迷你条 + 全屏播放页（本段只提供组件，页面在后续段落） | 手机浮层易遮挡 + 无 hover |
| 侧栏 + 快捷键 + tooltip | 底部 3 Tab；快捷键/tooltip 不移植 | 手机无物理键盘、无悬停 |
| `backdrop-blur-xl` 浮层 | `FoliaGlassPanel(blurRadius = Folia.blur.xl)`：API 31+ 用 `Modifier.blur`，26–30 降级半透明底 | `RenderEffect` 需 API 31；Robolectric 走降级路径 |
| 自定义滚动条 | 系统滚动条（保留 overscroll） | 触屏靠滑动，自绘滚动条无意义 |

## 7. 组件清单

全部在 `FoliaComponents.kt`，每个组件 KDoc 头部注明对应 Folia 源文件。

| 组件 | Folia 源文件 | 手机化改动及原因 |
|---|---|---|
| `FoliaButton`（Primary/Secondary/Quiet）+ `FoliaButtonKind` | `src/components/modal/settings/kit/Button.tsx:11-25` | h-8/32px → 48dp；`active:scale-0.97` → 按压 0.97 + ripple；图标 14 → 18dp 保持视觉重量 |
| `FoliaIconButton` | 同上 `:21-25 ICON_CLASS` | 32×32 → 48dp 方框（图标 18dp），保证触控 |
| `FoliaCoverCard` | `src/library/suites/bravais/bravaisHome.css` + `SearchResultRow.tsx` 卡片外壳 + inventory §1 | 圆角统一 16dp（内封 12dp）；hover 抬升 → 按压 0.97 + ripple；多列 3–5 → 2 列（由调用方布局决定） |
| `FoliaSongRow` | `src/components/app/search/SearchResultRow.tsx:54-151`、`kit/Row.tsx:88` | 行高 ≥56dp；右侧 `＋` 从 hover 才显改为常显；右侧时长用 `numeric`（`font-mono`）；新增 `onLongClick` 上长按弹层；分隔线用 `--st-line` 内缩 |
| `FoliaRowCover` | 同上（`h-12 w-12 rounded-xl bg-zinc-500/15`） | 48dp 方、12dp 圆角、占位色 `zinc-500/15` 保留；图标居中 20dp |
| `FoliaSlider` | `src/components/modal/settings/kit/Slider.tsx:19-30`、`kit/kit.css:26-32` | 轨 4dp、拇指 16dp 白圆 + 描边阴影；按下拇指 1.15；交互高 48dp（原 input h-5/20px）；数值保留等宽 13sp |
| `FoliaToggle` | `src/components/modal/settings/kit/Toggle.tsx:25-28` | 视觉 40×24dp / 拇指 18dp 照搬；按下拇指加宽 22dp 位移 16dp；外框 48dp 触控；checked 用 accent/onAccent |
| `FoliaSegmented<T>` | `src/components/modal/settings/kit/Segmented.tsx:33-59` | 32dp → 48dp 容器；选中标记用 `raise` 底 + 6dp 圆角 + 阴影，动画走 `FoliaMotion.move()`（替代 layoutId 滑动） |
| `FoliaTabs<T>` | `src/components/modal/settings/kit/Tabs.tsx:73-107` | h-9/36px → 46dp 文本区；下划线 2dp + accent；底部 hairline `--st-line`；gap 18dp 保留 |
| `FoliaSearchField` | `kit/TextField.tsx:9-72` + 基准截图顶栏胶囊搜索框 | 32px 高 → 48dp；默认 `Pill` 形状对齐 Folia 顶栏胶囊；占位符默认「搜索在线歌曲...」（src/i18n/locales/zh-CN.ts:1579 searchDatabase）；可选 `Rounded` |
| `FoliaGlassPanel` | `src/index.css:163-168 .theme-glass-panel` + `FloatingPlayerControls.tsx:384` | 底色 85% 页色 + 12% 描边；blur 按平台分档（31+ `Modifier.blur`，26–30 半透明） |
| `FoliaEmptyState` | `src/components/panelTab/shell/EmptyState.tsx:14-18` | 180dp 最小高、28dp 图标、13px→14sp 文案、最大宽 264dp；新增可选按钮（手机上"下一步动作"比文案更重要） |
| `FoliaBottomSheet` / `FoliaSheetDragHandle` / `FoliaSheetItem` | 桌面 Modal（右键菜单、设置弹窗）；inventory 红线「右键/弹窗 → ModalBottomSheet」 | 底部滑入 + 拖动条 + 遮罩；下拉/点遮罩/`BackHandler` 关闭；**无叉号**；图标行 ≥56dp |
| `Modifier.foliaPressScale` | `kit/Button.tsx:11` | 复用按压 0.97 动画（spring fast） |

`FoliaTheme.kt` 组合上述所有 `CompositionLocal`（colors/type/shapes/spacing/blur），`FoliaTheme(dark: Boolean, content)` 切换明暗；不依赖系统 dark 开关，与 Folia「daylight 布尔」一致（inventory §0）。

## 8. 画廊

`GalleryScreen(dark, page, showSheet)`：debug/测试专用，**未接入 NavHost**。4 页：
1. **Token**：颜色 swatch（页/墨/强调/面板/状态，含明暗）、字体 9 档、圆角与间距。
2. **控件**：按钮（主/次/安静/图标/禁用）、开关、分段、Tab、搜索框（胶囊+圆角两种）、滑条（进度/音量/禁用）。
3. **内容**：封面卡 2 列、歌曲行（普通/选中/无封面）、空态。
4. **浮层**：`FoliaGlassPanel` 毛玻璃演示 + `FoliaBottomSheet` 打开态 + 拖动条 + 菜单项。

## 9. 截图

设备配置：Robolectric + Roborazzi，`w360dp-h800dp-xhdpi` → **720×1600 px**，`sdk=34`，`@GraphicsMode(NATIVE)`。录制命令：

```
$env:GUANGYUE_RECORD_SCREENSHOTS="true"
.\gradlew.bat :app:testDebugUnitTest --tests "com.guangyue.music.ui.design.*"
```

| 文件 | 内容 |
|---|---|
| `gallery-light.png` / `gallery-dark.png` | 画廊第 1 页（token） |
| `gallery-light-2/3/4.png`、`gallery-dark-2/3/4.png` | 画廊第 2/3/4 页（控件 / 内容 / 浮层含底部弹层） |
| `closeup-button-light/dark.png` | 按钮 + 开关 + 分段 + Tab 特写 |
| `closeup-card-light/dark.png` | 封面卡特写 |
| `closeup-row-light/dark.png` | 歌曲行 + 搜索框特写 |
| `closeup-slider-light/dark.png` | 滑条特写（进度/音量/禁用 + 毛玻璃块） |
| `side-card.png` | 左 Folia `m412-light-home-playlists` 卡片区 ↔ 右 `closeup-card-light` |
| `side-dark-card.png` | 左 Folia `m412-dark-home-playlists` ↔ 右 `closeup-card-dark` |
| `side-row.png` | 左 Folia `m412-light-panel-queue` 曲目行 ↔ 右 `closeup-row-light` |
| `side-controls.png` | 左 Folia `m412-light-settings-playback` 表单控件 ↔ 右 `closeup-button-light` |
| `side-sheet.png` | 左 Folia 玻璃面板 ↔ 右 `gallery-light-4`（底部弹层） |

并排图重生成：`python tools/u1_side_by_side.py`（PIL，裁剪框写在脚本里）。

## 10. 推断项（源码里查不到，按下列依据取值）

1. **卡片外框 16dp 圆角**：Folia 是 `bravaisHome.css` 里 per-suite 的圆角，各 suite 不一；inventory §1 明确要求「移植统一 16dp」，故取 16。内封 12dp 取 `rounded-xl`。
2. **正文 14sp / 标签 12sp**：Folia 13px/10px 是桌面 1x 值，m00169 明确正文 ≥14sp、次要 ≥12sp，按此放大。
3. **按钮 48dp 高**：Folia 32px 无对应手机值；M3 与 Android 无障碍规范均要求 48dp 触控。
4. **Tab 46dp**：Folia h-9=36px；取 46dp 留出下划线与内边距，同时整行触控 ≥48dp。
5. **底部弹层圆角 24dp、位移 420px、遮罩 32%**：Folia 无底部弹层（桌面用模态/右键菜单），数值按 M3 底部弹层惯例 + 现有 `springCard` 推定。
6. **毛玻璃降级阈值 API 31**：`Modifier.blur` 走 `RenderEffect`，需 API 31+；26–30 用 85% 半透明底模拟。
7. **`springPlayer` ζ=0.8366**：28/(2·√280)=0.83666，源码只给 stiffness 280 的 `FloatingPlayerControls.tsx:38` 未给 damping，damping 28 取自 `usePlayerBottomBarBottomPx.ts:46`。
8. **颜色 token 的百分比透明度**：`--st-t2/t3/line/fill/track` 在 `kitTheme.ts` 是以 `color-mix`/rgba 形式表达的百分比（62/42/8/16/7/12/17），Compose 用 `Color.copy(alpha=…)` 叠加在 page 上等价实现。
9. **字体族**：Folia 依赖 Inter + Noto Sans CJK；Inter 未随包分发，Android 端用系统 `sans-serif`（Roboto）+ 系统 CJK fallback，字宽略有差异。
10. **`FoliaWallBackdrop`**：Folia 的 lattice 背景是 SVG/CSS 渐变（`wall.css:7-32`），本段只提供占位纯色/渐变实现，正式墙面在首页段落再做。

## 11. 已知问题 / 没做到

- **截图只能整屏 720×1600**：Roborazzi 在 Robolectric 下按窗口尺寸出图，`wrapContentHeight` 不改变输出高度。特写图是同一张图用 PIL 裁掉底部空白得到的（脚本内联在生成流程里），不是"组件高度自适应截图"。
- **毛玻璃在截图里是降级态**：Robolectric 的 `sdk=34` 但无 GPU，`Modifier.blur` 不产生真实模糊；截图里能看到的是半透明底 + 描边。真机 API 31+ 才有效果，需要在后续真机/模拟器段落复核。
- **未接入任何页面**：`GalleryScreen` 只在测试里调用，NavHost/Root 未改，符合段落约束。
- **`tools/u1_side_by_side.py` 里的裁剪框是硬编码像素**：基准图若换版本需要调 `CROPS`。
- 本段耗时超过 70 分钟限时（主要是 Gradle 首次配置 + Roborazzi 输出路径/编码返工），没有遗留编译或测试失败。

---

# U1b · 审查返工

Hark 对 U1 打回 5 条，逐条处理（第 5 条按指示只写说明，不动代码）。

## 12. 文案来源（界面文字 → zh-CN.ts 键名 或 自拟）

**规则**：所有界面文字只存在于 `app/src/main/res/values/strings.xml`（`values/` 即中文默认，
不再需要 `values-zh/`），Composable 里一律 `stringResource(R.string.…)`，禁止硬编码。
取自 Folia `D:\workspaces\_folia-src\src\i18n\locales\zh-CN.ts` 的逐条标键名；zh-CN 里没有的标「自拟」。

| 界面文字 | 来源（zh-CN.ts 键名 / 自拟） |
|---|---|
| 搜索在线歌曲... | `searchDatabase`（zh-CN.ts:1579，对应 en 的 `SEARCH DATABASE...`） |
| 搜索 | `search` |
| 播放全部 | `playAll` |
| 加入队列 | `addToQueue` |
| 加入播放队列 | `enqueue` |
| 下载 / 下载到本地 | `download` / 自拟（弹层动作用带补语的短句） |
| 更多 | `more` |
| 歌曲 / 专辑 / 歌手 / 歌单 | `kind.track` / `kind.album` / `kind.artist` / `kind.playlist` |
| 每日推荐 | `special.daily` |
| 首歌曲 | `tracks` |
| 列表 / 网格 / 墙面 | `libraryBravaisCollection.list` / `rendererGrid` / `wall`（「墙面」取 wall 的磁贴墙义） |
| 立即播放 / 查看专辑 | 自拟（zh-CN 无对应弹层项） |
| 打开歌曲菜单 / 毛玻璃面板 / 底部弹层 / 队列面板 / 封面卡 / 歌曲行 | 自拟（画廊专用术语，Folia 无对应控件名） |
| 令牌 / 控件 / 内容 / 浮层 | 自拟（画廊 tab） |
| 颜色 / 字体 / 圆角与间距 / 按钮 / 开关 / 分段与标签页 / 搜索框 / 滑条 / 空状态 | 自拟（画廊分区标题，**已去掉英文副标题**） |
| 夜曲 / 晴天 / 稻香 | 自拟（真实中文歌名，替代 `Midnight City` / `Cloud Fixture` / `Fixture Artist`） |
| 周杰伦 · 十一月的萧邦 / 叶惠美 / 魔杰座 | 自拟（真实专辑） |
| 每日推荐 · 30 首 / 我喜欢的音乐 · 128 首 | 自拟（替代 `Daily Mix` / `Cloud Fixture`） |
| 这里还什么都没有。搜索一首歌，或者先建一个歌单。 | 自拟（空态） |
| 已禁用 | 自拟（画廊演示禁用态） |
| Android 12+ · 真模糊已启用 / 低版本降级 · 半透明底（无真模糊） | 自拟（画廊浮层说明） |
| 卡片投影 / 玻璃面板底 / 当前行底 / 当前行竖条（token 中文说明） | 自拟（token 名保留英文，旁边加中文解释） |

校验：`FoliaU1bTest.uiStringsAreChinese` 断言 33 个界面文案每条都含中日韩字符；
`sectionTitlesHaveNoEnglishSuffix` 断言 14 个分区标题不含 `/` 和 3 个字母以上的英文；
`sampleDataUsesRealChineseSongs` 断言示例数据里不再出现 6 个英文假数据串。

## 13. 卡片质感（审查第 2 条）

**问题**：U1 的卡片是「浅灰细描边、无投影、平的」，Folia 是白纸底凸起卡 + 柔和投影，浮在模糊背景上。

| 项 | U1 做法 | U1b 改成 | 出处 |
|---|---|---|---|
| 卡片底色 | `cardFill` 自取值（偏灰） | `color-mix(bg 94%, ink)`（浅）/ `88%`（深）→ 浅 `#F6F5F4`、深 `#1C1B1D` | `components/wall/wall.css:34` / `:10` `--lattice-poster-background` |
| 投影 | **无** | `Modifier.shadow(elevation = 16.dp, clip = false, ambient/spot = cardShadow)`，浅 18% 黑、深 45% 黑 | `wall.css:35` / `:12` `--lattice-focus-lift: 0 12px 32px rgb(0 0 0 / 25% \| 45%)` |
| 描边 | 1dp 浅灰（就当卡片边） | 降为 5%/6% 发丝线，且 pressed 才用 `lineStrong` | `kit/kit.css:62-70` 发丝线 |
| 封面内描边 | **无** | 封面外加 `1dp coverRing`（浅 8% 黑 / 深 10% 白） | 实测 `m412-light-home-playlists.png` 封面边缘那圈亮线 |
| 圆角 | 16dp | 保持 16dp（内封 12dp） | inventory §1 |

深色版对照 `m412-dark-home-playlists.png` 同步改：底色 88%、投影 45% 黑。

**列表行容器**（审查同条后半）：U1 只是把行塞进普通 Column，没有「玻璃面板上的行」。

| 项 | U1 | U1b | 出处 |
|---|---|---|---|
| 行容器 | 无面板 | 新增 `FoliaListPanel`：圆角 20dp、底 白62% / 黑40%、描边 黑7% / 白8%、投影 `0 24dp 64dp`、backdrop blur 24dp（Robolectric 走降级） | `components/wall/WallToolsQueueIsland.css:1-20` `.lattice-tools-island` |
| 选中行底 | `fill`（7%，几乎看不出） | `rowCurrent`：白10% / 黑7% | `WallToolsQueueIsland.css:176-177` `.is-current` |
| 选中竖条 | **无** | 行左侧 3×18dp、圆角 2dp、取 accent 色 | `WallToolsQueueIsland.css:249` `.lattice-tools-island-marker` |
| 选中字重 | 不变 | 名称加粗 | `WallToolsQueueIsland.css` `.is-current …-name { font-weight:700 }` |

## 14. 禁用按钮（审查第 3 条）

**问题**：U1 的禁用态是「橙底 + 淡字」——底色仍是 accent，只把文字压淡。

**出处**：`src/components/modal/settings/kit/Button.tsx:11`
```
BASE_CLASS = '… transition-[background-color,color,opacity,scale]
              enabled:active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-40'
```
即禁用 = **整组 `opacity: .4`**，橙色底跟着一起淡，不存在「保留橙底」。
图标按钮另有 `ICON_CLASS.fill … disabled:bg-transparent`（连底色一起去掉）。

**改法**：`.alpha(if (enabled) 1f else 0.4f)` 从文字层提到 `FoliaButton` 最外层 `Box`，
底色/图标/文字整体降到 40%；`FoliaIconButton` 同理（U1 只压图标，现在整体压）。

## 15. 并排图重做（审查第 4 条）

**问题**：左边 Folia 裁切太小、右边是整张 720×1600 整屏图，两边每 dp 像素数不同，没法比。

**改法**（`tools/u1_side_by_side.py` 全文重写）：
- 两边都按**目标组件的紧包围盒**裁切，再统一缩放到 **360dp 宽**。基准图 824×1830 是 @2x（412dp），
  缩放系数 0.874；广乐截图本身就是 720×1600 @xhdpi（360dp），系数 1.0 —— 于是两边 **1dp = 2px**，
  圆角半径、投影扩散、行高、字号可以直接用眼睛量。
- `tight()` 自动按内容包围盒再收一次边，避免大片背景把两栏拉得高低不齐。
- 标注全中文，左栏副标题写明「Folia 截图 <文件名> 裁切 <框>」，右栏写关键 token/交互，便于复核。
- 渲染侧同时修了 `shootFit`：U1 用 `onRoot()` 截到整屏 800dp（下方三分之二空白）；U1b 改成
  `onNodeWithTag(FitTag)` + `cropToContent()` 按「内容非底色底边」裁掉空白，特写图现在
  高度 472–671px（U1 是 1600px）。

7 张（原 5 张 + 审查要求新增 2 张）：

| 文件 | 左（Folia 基准） | 右（广乐） |
|---|---|---|
| `side-card.png` | `m412-light-home-playlists` 封面卡 | `closeup-card-light` |
| `side-dark-card.png` | `m412-dark-home-playlists` 封面卡 | `closeup-card-dark` |
| `side-row.png` | `m412-light-panel-queue` 播放队列面板 | `closeup-row-light`（选中「稻香」） |
| `side-controls.png` | `m412-light-settings-playback` 开关/分段 | `closeup-button-light` |
| `side-sheet.png` | `m412-light-panel-queue` 玻璃面板 | `gallery-light-4`（毛玻璃 + 底部弹层） |
| **`side-search.png`（新增）** | `m412-light-search-typing` 顶栏胶囊搜索 | `gallery-light-2` 搜索框 |
| **`side-dark-controls.png`（新增）** | `m412-dark-settings-playback` 开关/分段 | `closeup-button-dark` |

## 16. 毛玻璃（审查第 5 条，本段不处理）

截图里毛玻璃是**降级态**（Robolectric 无 GPU，`Modifier.blur` 不产生真实模糊，
看到的是 85% 半透底 + 12% 描边）。按指示本段不改实现。
**真机 API 31+ 待 Hark 在模拟器复核。**

## 17. 手机化两张表（U1b 补充行）

审查要求保留的两张表在 §5、§6，U1b 新增的取值补在这里。

| 位置 | Folia 原值 | 手机值 | 原因 |
|---|---|---|---|
| 卡片投影 | `0 12px 32px rgb(0 0 0/25%)`（日光）/ `45%`（深） | `shadow(16.dp)` + 18% / 45% 黑 | CSS 的 12px 偏移+32px 模糊在 Compose 只有 elevation 一个旋钮，16dp 是 Robolectric 截图里能和基准对上「柔和扩散」的最小值；透明度照抄源码 |
| 封面内描边 | 实测可见 1px 亮线 | 1dp，浅 8% 黑 / 深 10% 白 | 1px @2x = 0.5dp 渲染不出来，抬到 1dp |
| 队列面板圆角 | 20px | 20dp | 照搬 |
| 队列面板投影 | `0 24px 64px rgb(0 0 0/42%)` | `shadow(24.dp)` | 同上，取视觉最接近的一档 |
| 选中竖条 | 3×18px、radius 2px | 3×18dp、radius 2dp | 照搬（dp≈px，且 ≥1dp 才能看清） |
| 按钮禁用 | `opacity: .4` | `alpha(0.4f)` | 照搬（整体压，不保橙底） |
| 特写截图宽度 | — | 360dp | 与基准 412dp 缩到 360dp 后同密度，便于并排 |

| 桌面动作 | 手机动作 | 原因 |
|---|---|---|
| 卡片 hover 抬起（`--lattice-focus-lift`） | 投影常驻 + 按压 scale 0.97 | 触屏无 hover，抬起效果必须常显才看得出「凸起」 |
| 行 hover 才显的底色 | 选中行常驻浅底 + 左侧竖条 | 同上，且竖条给「正在播放」一个不依赖颜色的标识 |
| 禁用按钮靠 cursor-not-allowed 提示 | 整体 40% 透明（无鼠标指针可依赖） | 手机上唯一能表达「不可用」的就是视觉降权 |

## 18. U1b 验收

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | **89 过 / 0 败 / 0 跳过**（U1 基线 54/0/0；新增 `FoliaU1bTest` 18 + `FoliaComponentsTest` 17） |
| `assembleDebug` | BUILD SUCCESSFUL |
| 截图重录 | 全部 16 张 gallery/closeup 重录，7 张 side 重生成 |
| commit | `port/u1` 本地提交，消息前缀 `U1b:`，未 push、未打 tag |

## 19. U1b 已知问题

- **用例数没到 123**：任务要求「不少于 123/0/2」，但本 worktree 实测基线是 54 个用例、
  且**没有** 2 个跳过用例 —— 这个基线数字本身对不上（见文首更正）。U1b 把用例从 54 提到 89，
  全绿，但没有为了凑数写无意义断言。如需硬指标，请派活方确认基线口径后再补。
- **卡片投影在浅色截图上仍偏弱**：16dp 已是 Robolectric 软件渲染下不产生脏边界的上限，
  真机上可再压一层 24dp。已在 `side-card.png` 里与基准并排，肉眼可见扩散范围接近。
- **并排图右侧的 `gallery-light-2/4` 是整屏图**（搜索、浮层两张）：这两项 Folia 侧也是整屏裁切，
  没有可紧裁的组件包围盒，所以两张各 1712px 高。其余 5 张已按组件紧裁。
- `tools/u1_side_by_side.py` 的裁切框仍是硬编码像素，基准图换版本需要调 `BOXES`。

