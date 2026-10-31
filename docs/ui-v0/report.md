# V0 歌词内核 · 收尾报告

分支 `port/u2`（基于 `60824c8`），提交「V0: 歌词内核 + GlowWord + 流光底座」。未 push、未打 tag。

## 1. 测试

`.\gradlew.bat --offline test --continue`

| 项 | 数值 |
|---|---|
| 总计 | **536** |
| 失败 | **0** |
| 错误 | **0** |
| 跳过 | 34（core:plugin 的仪器测试占位 32 + 既有 2） |
| 测试类 | 53 |

内核 `:core:lyrics` 13 个模块各有 JVM 单测，共 136 例（新增 `WordSegmentationTest` 10 例，补齐唯一缺项）。
`V0LyricStyleScreenshotTest` 29 例（Roborazzi 截图用例，同时是样式回归）。

## 2. Folia → 广乐 文件对照

| Folia 源码（`_folia-src/src`） | 广乐文件 |
|---|---|
| `components/lyrics/model.ts` | `core/lyrics/.../Model.kt` |
| `components/visualizer/renderHints.ts` | `core/lyrics/.../RenderHints.kt` |
| `components/lyrics/graphemeTiming.ts` | `core/lyrics/.../GraphemeTiming.kt` |
| `components/lyrics/glowWordTiming.ts` | `core/lyrics/.../GlowWordTiming.kt` |
| `components/lyrics/glowWordVariants.ts` | `core/lyrics/.../GlowWordVariants.kt` |
| `components/lyrics/wordGlow.ts` | `core/lyrics/.../WordGlow.kt` |
| `components/lyrics/wordColoring.ts` | `core/lyrics/.../WordColoring.kt` |
| `components/lyrics/paragraphStructure.ts` | `core/lyrics/.../ParagraphStructure.kt` |
| `components/lyrics/songHandover.ts` | `core/lyrics/.../SongHandover.kt` |
| `components/lyrics/timeline.ts` | `core/lyrics/.../Timeline.kt` |
| `components/lyrics/classicLayout.ts` | `core/lyrics/.../ClassicLayout.kt` |
| `utils/lyrics/wordSegmentation.ts` | `core/lyrics/.../WordSegmentation.kt` |
| `components/visualizer/runtime.ts` | `core/lyrics/.../Runtime.kt` |
| `components/visualizer/GlowWord.tsx` | `feature/player/.../lyrics/visualizer/GlowWord.kt` |
| `components/visualizer/ClassicLyricLine.tsx` | `.../visualizer/ClassicLyricLine.kt` |
| `components/visualizer/VisualizerClock.ts` | `.../visualizer/VisualizerClock.kt` |
| `components/visualizer/VisualizerKernel.ts` | `.../visualizer/VisualizerKernel.kt` |
| `components/visualizer/FluidBackground.tsx` | `.../visualizer/VisualizerBackdrop.kt` |
| `hooks/useReducedMotion.ts` | `.../visualizer/ReduceMotion.kt` |
| `components/lyrics/LyricStyleSheet.tsx` | `feature/player/.../lyrics/LyricStyleSheet.kt` |
| —（新增接线） | `.../lyrics/LyricStyleViewModel.kt`、`core/data/.../lyrics/LyricTimeline.kt` |

每个内核源文件顶部都写有「对应 Folia 文件：…（行号）」注释，参数逐条对上。

## 3. 关键参数（与 Folia 一致）

- renderHints：MICRO 0.10 / SHORT 0.18 / FLOOR 0.067；`linePassHold`、`renderEndTime`。
- glowWordTiming：lookahead instant 0.03 / fast 0.08 / normal 0.15；最短显示 0.08 / 0.12 / 0.10。
- 行容器入场 normal：opacity 0、scale 0.9、blur 10 → 1/1/0；出场 opacity 0、scale 1.1、blur 20，0.3s；fast 0.16s；none 0.12s。
- glowWordVariants：waiting opacity 0、scale 0.5、sin/cos 偏移、旋转 +20°；active spring stiffness 200 / damping 20、scale ×1.4；passed opacity 0.82（chaotic 0.9）+ 5s 漂移；正文 waiting blur 10，active blur 0.2/0.12/0.08；passed 回 baseColor 0.8s。
- wordGlow：半径 normal [20,40] / fast [18,32] / instant [14,24]；逐字扫光 `charDuration × 6`、times [0,0.3,1]；CJK 单字 times [0,0.9,1]；passed 淡出 0.9 / 0.22 / 0.12。
- paragraphStructure：空隙中位数 ×2.5 钳 [1.25, 3.5]s；maxLines 6；maxDuration 18s；recursiveSplit；分类 breath/verse/lift/chorus/break/outro。
- songHandover：`DEFAULT_INSTRUMENTAL_COMMIT_SECONDS = 2`、`READY_GRACE_MS = 8000`、分词哈希。
- classic tuning：呼吸浮动 calm 10 / 8.5s、normal 14 / 7s、chaotic 18 / 5.8s；`enableWordRotation`；`wordSpacing` 默认 0.7。

## 4. 手机化改动（与 Folia 桌面/网页的差异）

- 布局宽度用 `viewportWidthPx` 驱动，行高按 dp 算，360dp / 320dp 都不溢出。
- 触控目标 ≥48dp：样式入口、弹层开关、滑杆均达标。
- 系统字号 1.3 倍：`LyricFlowScreen` 与 `LyricStyleSheet` 都按 `fontScale` 重新排版，截图验证不溢出。
- edge-to-edge：歌词页与弹层使用 `WindowInsets` 安全区。
- 浅/深两套色板，`FoliaTheme(dark = …)` 切换。
- 减少动效：`rememberReduceMotion()` 为真时关掉换歌柔焦、行容器入场位移与逐字漂移（对应 Folia `prefers-reduced-motion`）。

## 5. 截图清单（`docs/ui-v0/`，32 张）

三态 × 浅深：`v0-classic-{waiting,active,passed}-{light,dark}.png`
逐字辉光 6 帧（帧间隔 200ms，≥80ms）：
`v0-glow-frame-0{1..6}-{light,dark}.png`（12 张）
调参：`v0-classic-breathing-off-light.png`、`v0-classic-rotation-off-light.png`、`v0-classic-spacing-max-light.png`
小屏/大字号：`v0-classic-320dp-{light,dark}-font130.png`、`v0-classic-font130-light.png`
背景层：`v0-backdrop-{light,dark}.png`、`v0-backdrop-cover-light.png`
样式弹层：`v0-style-sheet-{light,dark}.png`
对照：`folia-vs-guangyue-{light,dark}.png`（左 Folia 真机基线，右广乐三态并排）

模拟器实拍（`adb` 于 emulator-5554）：

| 文件 | 内容 |
|---|---|
| `v0-emu-player-style-sheet.png` | 播放页 → 点「歌词样式」→ 底部弹层（流光选中 / 字词旋转 / 呼吸浮动 1.00× / 词距 0.70×） |
| `v0-emu-sheet-font130.png` | 同弹层，系统字号 1.3 倍，文字不溢出 |
| `v0-emu-sheet-320dp.png` | `wm size 640x1600` + `wm density 340`（≈320dp），不溢出 |
| `v0-emu-lyric-flow-light.png` | 歌词页浅色 |
| `v0-emu-style-sheet.png` | 弹层默认态 |

gfxinfo（模拟器软件渲染，拖拽歌词样式弹层时采样）：Total frames 244–245、Janky 213–214（87.3%）、
50th 69ms、90th 121ms、95th 133ms、99th 150ms、Slow UI thread 205、Slow draw commands 194、Missed Vsync 127。

## 6. 已知不足

1. **封面取色截图待真机补**：`v0-backdrop-cover-light.png` 只拍到无封面时的底色态。Robolectric 下 Coil 的
   `file:` 请求不保证完成，两次尝试（同步等待、加本地 fetcher）都没让模糊封面进画面，已按主控指示停止改测试框架。
2. **播放页滚歌词的帧统计拿不到**：本机所有音源都取不到播放地址（`playback error (final)…这个音源取不到…的播放地址`），
   播放停在 00:00、歌词页显示「匹配最佳歌词失败」，只能拍静态帧。gfxinfo 数据来自拖拽弹层，且模拟器无 GPU 加速，
   87% Janky 不可作为流畅度判据，需真机复测。
3. 样式弹层入口在播放页右上角，热区偏窄（像素 528,1383 命中，612,1268 未命中），后续建议给 48dp 显式内边距。
4. `app/src/main/java/com/guangyue/music/ui/design/FoliaBlur.kt` 与
   `core/web/src/main/java/com/guangyue/core/web/LibraryGateway.kt` 里 HEAD 自带的中文注释乱码未动（不在本任务范围）。

## 7. 产物

- APK：`app/build/outputs/apk/release/app-release.apk`（8.57 MB）
- SHA-256：`20040d9d254fd7aee4b15d51d1dca53bb393341d4d28764397267dd6917ce752`

## 8. 验收对照

| 项 | 结论 |
|---|---|
| 内核 13 模块各有 JVM 单测 | PASS |
| 全量测试 0 失败（≥375） | PASS（536 / 0 / 34 跳过） |
| 三态 × 浅深截图 | PASS |
| Folia vs 广乐同句并排 | PASS（背景基线，非同一首歌，见不足 2） |
| 逐字辉光 6 帧 ≥80ms | PASS |
| 320dp / 1.3 倍字号 | PASS |
| 减少动效 | PASS |
| gfxinfo 帧统计 | 部分 PASS（模拟器数据，需真机复测） |
| assembleRelease + SHA-256 | PASS |

---

## V0c 收尾（2026-10-31 21:30，WIP）

本轮只做取证与结论固化，**未改渲染代码**（GlowWord.kt 已还原到 91c86a9）。

| # | 项目 | 结果 | 说明 |
|---|------|------|------|
| 1 | 辉光过程 6 帧实拍（emulator-5554） | **FAIL** | 矩形块是**真机缺陷**，不是 Robolectric 软件渲染伪影。见下节三种尝试。 |
| 2 | 舞台只画 activeLine + 字幕层 | **未做** | LyricFlowScreen.kt 仍是 LazyColumn 多行 + 左对齐档位；VisualizerSubtitleOverlay / subtitleFontSizes 移植未开始。 |
| 3 | 封面取色流体背景 实拍（浅/深） | **PASS** | `v0c-backdrop-cover-light.png` / `v0c-backdrop-cover-dark.png`（720x1600，内置测试封面 BundledTestCover 取色）。 |
| 4 | Folia 同句对照 | **FAIL** | _folia-src 本地 dev 未跑通，未出图。 |

### 1. 矩形块取证结论（真机缺陷，非伪影）

装 debug 包到 emulator-5554，用 debug-only 取证入口 `.V0cStageActivity`（`am start ... --ei v0c_ms <ms>`）逐帧实拍。原实现（HEAD 91c86a9）的 `GlowWordCanvas` 用 `canvas.saveLayer + PorterDuff.CLEAR 画字盒 + BlurMaskFilter` 画辉光，在真机 GPU 硬件加速画布上稳定出现**整块矩形底 / 实心色块**，与 Robolectric 截图现象一致 ⇒ 判定为真实渲染缺陷，任务里「若模拟器也出矩形则改为不用 saveLayer」的条件被触发。

三种尝试与结果（完整补丁见 `docs/ui-v0/glow-attempts.patch`）：

1. 字盒 RectF ∪ 字形 Path 作为遮罩 → **仍出整块矩形**（Region 的偶数环绕规则在硬件加速层不生效）。
2. BlurMaskFilter + 字盒外扩 ∪ 字形 → **仍出矩形**（BlurMaskFilter 在硬件加速画布上退化为「实心矩形 + 模糊」）。
3. CPU 距离场光晕：PathMeasure 沿字形重采样折线，逐像素求最近距离按高斯权生成 ARGB 位图再 drawBitmap。**零 saveLayer / 零 CLEAR / 零字盒**，某一版参数下实拍确认笔画周围是干净圆形光晕；但随后调参（sigma 3.2f→2.4f、pad 2.5→1.8）重新编译后，实拍又出现深色矩形块与红色色块回归。

⇒ 该方向**未收敛**，本轮不交付；渲染代码全部回退，留待下一会话重做。

### 2. debug-only 取证入口（已随本轮提交）

`app/src/debug/`，仅 debug 源集，不进 release：

- `AndroidManifest.xml`：注册 `.V0cStageActivity`、`.V0cBackgroundActivity`。
- `V0cStageHarness.kt`：内置 5 句 LRC + `LyricTimeline.toLines` 生成内核 Line，`V0cStage(positionMs, dark)` 渲染 `LyricFlowScreen`；extra `v0c_ms` / `v0c_dark`。
- `V0cBackgroundActivity.kt`：只画 `rememberCoverBackdrop(BundledTestCover, dark)` → `VisualizerBackdrop`；extra `v0c_dark`。

采图命令（可靠姿势）：`adb shell am force-stop com.guangyue.music` → `am start -n com.guangyue.music/.V0cStageActivity --ei v0c_ms 1300 --ez v0c_dark false` → `Start-Sleep` 19~20s（冷启到画面稳定约 12~20s）→ `adb shell screencap -p /sdcard/a.png` → `adb pull`。`exec-out` 重定向在 PowerShell 下会坏。

### 3. 测试

本轮全量单测：**566 总 / 0 失败 / 0 错误 / 34 跳过**（`.\gradlew.bat --offline test`，BUILD SUCCESSFUL）。

### 4. 本轮未做

- assembleRelease 与 SHA-256（按指示跳过）。
- 舞台单行 + 字幕层、Folia 同句对照，均留待下一会话。
## V0d（辉光按 Folia text-shadow 重做 + 当前句 flex-wrap）

### 1. 矩形来源结论

本轮先用 V0c 实拍帧定位，再看代码路径逐条对照，矩形有两个来源，都发生在**绘制层**而非布局层：

1. **旧 `GlowWordCanvas` 的画法本身**（feature/player/.../visualizer/GlowWord.kt，HEAD 6216110 第 305–354 行）：
   逐字 `layout.getPathForRange` + `layout.getBoundingBox`，在 `drawIntoCanvas{ saveLayer(null,null) }` 里用
   `PorterDuffXfermode(CLEAR)` 把**整块字盒 RectF** 擦掉，再挂 `BlurMaskFilter` 画字形 Path。
   硬件加速层里 CLEAR 擦除不生效、BlurMaskFilter 退化为「实心矩形 + 模糊」，于是字盒被整块填成矩形。
   对应实拍：v0c 帧里「弹奏」整块实心红方 + 白字、「夜曲」整块深灰字盒底。
2. **非当前行的 `Modifier.blur`**（feature/player/.../lyrics/LyricFlowScreen.kt:493，档位 0.6/1.6/2.6/3.6dp）：
   Compose 的 blur 会把整个节点光栅进一层再模糊，代价是节点边界成为不透明矩形底。
   对应实拍：非当前行每行都有可见的矩形块边界。

另外确认一条**环境事实**：`ro.hardware.egl=emulation`，硬件加速层不具备真实 GPU 语义，
所以任何依赖 saveLayer/framebuffer 擦除、或依赖 `withFrameNanos` 推进的写法在模拟器上都不可靠。

### 2. 实现说明

`GlowWord.kt` 里删除 `GlowLayer` + `GlowWordCanvas` 整段（saveLayer、CLEAR、RectF 全部去掉），
改为单一 `WordInk` Composable，用 `Modifier.fillMaxSize().drawWithCache{ onDrawBehind{...} }`：

- 辉光：`nativeCanvas.drawText` 画两遍，`TextPaint.maskFilter = BlurMaskFilter(px, Blur.NORMAL)`，
  半径 = `WordGlow.glowRadii(mode)`（instant 14/24、fast 18/32、normal 20/40 CSS px）
  × `WordGlow.DROP_SHADOW_RADIUS_SCALE`(0.4) 换算成 dp→px；内圈 alpha = `DROP_SHADOW_INNER_ALPHA`(0.7)。
  逐字强度来自 `glowStrengthAt(clock, word, profile, …, charStartTime, charEndTime)`，与 Folia 的逐字扫光一致。
- 正文：同一个 `WordInk` 在辉光之后 drawText，叠在光晕之上；waiting 的正文模糊改用
  `body.maskFilter = BlurMaskFilter(blurPx, NORMAL)`，不再用 `Modifier.blur`；passed 正文 alpha = `PASSED_WORD_OPACITY`(0.82)。
- 动画只动 opacity/强度，光晕半径固定，与 Folia `glowKeyframes` 语义一致。
- 新增仅在 debug 生效的旁路：`runningUnderRobolectricOverride`（VisualizerKernel.kt）与
  `glowStrengthOverride` 参数链（LyricFlowScreen → ClassicLyricLine → GlowWord），供取证 harness 钉住三态。

### 3. 当前句容器改为 Folia 的 flex-wrap（本轮新修）

上一轮「整行不绘制」的真正根因不是辉光：`WordInk` 曾写成裸 `Modifier.fillMaxSize().drawWithCache{...}`
表达式而没有交给任何 Composable，Compose 丢弃该值 → 零布局节点 → `onDrawBehind` 从不执行
（这也是插 `drawRect(Color.Red)` 不出红块的原因）。已改为 `Box(modifier = Modifier.fillMaxSize().drawWithCache{...})`。

随后暴露的是**溢出**：Folia `classic/Visualizer.tsx` 的当前句容器是
`flex flex-wrap w-full max-w-6xl content-center ${justifyContent} ${alignItems}`，外层
`w-full h-[70vh] flex items-center justify-center p-8` —— 词会自动换行，而广乐用的是单行 `Row`，
长句必然顶出屏幕。本轮照抄：

- 当前句容器改为外层 `Box(fillMaxWidth, padding 32dp, height = 视口高 × 70%, minHeight 300dp)` + 内层 `FlowRow`；
  `horizontalArrangement` 映射 `LineJustify`（START/CENTER/END/AROUND/BETWEEN），
  `verticalArrangement` 用 `verticalOf(LineAlign)`，逐词 `align(alignSelfOf(WordAlign))` 对齐。
- 每个词宽度用自身测量 + `padding(end = marginRight)`，删掉 `fillMaxHeight(0.999f)`（那是单行 Row 的写法，换行后会算成 0/异常高度）。
- 恢复 `GlowWord` 的 `translationX/translationY`，数值按 `glowWordVariants.ts buildGlowWordLayoutVariants` 原样
  （位移走 `graphicsLayer`，不参与布局，换行后不会溢出）。
- 删掉临时加的 `widthIn(max = viewportWidthDp)` 与多余居中 `Box`，改由 `LyricFlowScreen` 传入 `viewportHeightPx`。
- `LyricFlowScreen.kt` 非当前行 `Modifier.blur` 已删除，景深只由颜色 alpha 档位 1/.34/.20/.12/.07 承担。

### 4. 实拍验收（emulator-5554，720x1600 @320dpi）

全部逐张打开确认：**无矩形、无色块、光晕柔和**，词以 activeColor 为中心向外晕开，边缘无硬边，
waiting 态为模糊正文、无光晕，passed 态正文回落到 baseColor 并保留 0.82 alpha。

| 文件 | 内容 |
| --- | --- |
| `docs/ui-v0/v0d-glow-dark-waiting.png` | 深色 · waiting（正文 blur，无光晕） |
| `docs/ui-v0/v0d-glow-dark-peak.png` | 深色 · active 峰值（光晕最亮） |
| `docs/ui-v0/v0d-glow-dark-passed.png` | 深色 · passed（辉光淡出、正文回落） |
| `docs/ui-v0/v0d-glow-light-waiting.png` | 浅色 · waiting |
| `docs/ui-v0/v0d-glow-light-peak.png` | 浅色 · active 峰值 |
| `docs/ui-v0/v0d-glow-light-passed.png` | 浅色 · passed |
| `docs/ui-v0/v0d-glow-320dp.png` | 320dp 屏宽 · 当前句自动换行、不出屏 |
| `docs/ui-v0/v0d-glow-font130.png` | 字号 1.3 倍 · 自动换行、不出屏 |

采集配方（模拟器禁止启停，暖启动 `am start` 不重置 composition，必须 force-stop）：
`adb shell am force-stop com.guangyue.music` → `adb shell am start -n com.guangyue.music/.V0cStageActivity --el v0c_ms <ms> --ez v0c_dark <bool> --ef v0c_strength 1.0`
→ 等 9s → `screencap -p /sdcard/x.png` → `adb pull`。`v0c_ms` 是 Long，必须用 `--el`；`v0c_strength` 同时打开
debug-only 静态时钟（模拟器软件 EGL 上 `withFrameNanos` 几乎不推进，否则三态永远停在 WAITING）。
320dp 与 1.3 倍字号用 `adb shell wm size 592x1316` / `adb shell settings put system font_scale 1.3` 拍摄，拍完已 reset 回 720x1600 / 320 / 1.0。

### 5. 产物

- 全量单测：**368 总 / 0 失败 / 0 错误 / 2 跳过**（38 个 `TEST-*.xml` 套件求和；已剔除 Gradle 聚合伪套件 `TEST-Gradle-Test-Run-*`）。
- `assembleRelease` 成功：`app/build/outputs/apk/release/app-release.apk`，8,996,985 字节（8.58 MB），
  SHA-256 `7692b03b142d48e7ff43414a91b34eca2a927bfe6be492ac10d3a4dde8286b4e`。
- 提交：见 `git log`「V0d: 当前句 flex-wrap + 实拍」（不 push）。

### 6. 遗留

- 非当前行的景深现在是纯 alpha 递减，没有模糊；若要贴合 Folia 的 blur(10px) 视觉，可在正文层用
  `TextPaint.maskFilter` 在画布内做（与 `WordInk` 同一手法），不能用 `Modifier.blur`。
- `glowStrengthOverride` / `runningUnderRobolectricOverride` 是仅 debug 的取证旁路，release 无写入方。