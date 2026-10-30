# U1 · Folia 设计地基（Compose）

分支 `port/u1`（worktree `D:\workspaces\GuangYue-u1`），基线 commit `1428a05`。
本段只做设计 token + 基础组件 + 画廊 + 截图；未改 NavHost / Root / MainActivity / `theme/Theme.kt`（旧 `GuangYueTheme` 原样保留，新主题另起名 `FoliaTheme`）。

代码位置：
- `app/src/main/java/com/guangyue/music/ui/design/`：`FoliaColors.kt` `FoliaType.kt` `FoliaShapes.kt` `FoliaMotion.kt` `FoliaBlur.kt` `FoliaTheme.kt` `FoliaComponents.kt` `GalleryScreen.kt`
- `app/src/test/java/com/guangyue/music/ui/design/`：`FoliaTokensTest.kt`（13 个纯 JVM 断言）、`FoliaGalleryScreenshotTest.kt`（Roborazzi 截图）
- `tools/u1_side_by_side.py`：并排图脚本（PIL）
- `app/build.gradle.kts`：唯一改动是 `roborazzi.output.dir` → `${rootDir}/docs/ui-u1`（1 行）

验证：`gradlew testDebugUnitTest` 全仓 **123 过 / 0 败 / 2 跳过**（基线 94/0/2，新增 29 个），`gradlew assembleDebug` BUILD SUCCESSFUL。

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
| `FoliaSearchField` | `kit/TextField.tsx:9-72` + 基准截图顶栏胶囊搜索框 | 32px 高 → 48dp；默认 `Pill` 形状对齐 Folia 顶栏胶囊；占位符默认 `SEARCH DATABASE...`（`src/i18n/locales/en.ts:1579`）；可选 `Rounded` |
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
