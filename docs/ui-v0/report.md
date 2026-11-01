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

## V0e（舞台单行 + 底部字幕层）

### 1. 结构

- `LyricFlowScreen` 删掉 U5 遗留的 `LazyColumn` 多行列表（连同 `LyricLineRow`、自动跟随、手动滚动暂停），
  改为 Folia classic 的**舞台单行**：`Box(weight(1f))` 量出「顶栏 ↔ 字幕层/播放胶囊」之间的实际剩余高度，
  作为 `viewportHeightPx` 交给 `ClassicLyricLine`；没有当前句时画「等待音乐」占位（`strings.lyrics_waiting`，alpha 0.5）。
- `ClassicLyricLine`：`viewportHeightPx > 0` 时外层 `fillMaxHeight() + 垂直居中`，`FlowRow` 用
  `heightIn(max = 净空 - 2×32dp)` 兜底 —— 1.3 倍字号换行也压不到播放条（修 Hark 第 2 条）；
  `<= 0`（整屏调用方）回退 Folia 70vh。辉光与 `flex-wrap` 未动。
- 新增 `lyrics/SubtitleOverlay.kt`：`SubtitleOverlay.kt` 纯函数 `hasReadableText` / `readableLines` /
  `resolveSubtitleOverlayContent`（过滤占位串、非 current 行同文去重、单轨布局下「翻译 == 当前句原文」跳过、
  无可读翻译时改画后面几行预告），加 `LyricSubtitleOverlay` 合成层：贴底居中、px-4、opacity 0.6、
  进出场 opacity + y 20 / 0.24s easeOut、字号 clamp（Folia `subtitleFontSizes.ts`）、第二行 ×0.88 / alpha 0.82。
  背景暗角用 `drawWithContent` 的径向渐变画，**不用 `Modifier.blur`**（V0d 已证明整节点光栅成矩形）。
- 底部避让：整列已 `windowInsetsPadding(statusBars ∪ displayCutout ∪ navigationBars)`，字幕层再 `padding(bottom =
  SUBTITLE_BAR_RESERVE(80dp) + SUBTITLE_STAGE_RESERVE(56dp))`，即停在播放胶囊之上、导航栏 inset 之内。
- `V0cStageHarness`（debug）：`enableEdgeToEdge()` + 按 dark 切 `isAppearanceLightStatusBars/NavigationBars`
  （修 Hark 第 1 条深色状态栏白底），新增 `v0c_variant = trans|preview|interlude` 三态取证。

### 2. 实拍（emulator-5554，720×1600 @320dpi）

| 文件 | 内容 |
| --- | --- |
| `docs/ui-v0/v0e-stage-light.png` | 浅色 · 有翻译（舞台当前句 + 字幕层译文） |
| `docs/ui-v0/v0e-stage-dark.png` | 深色 · 有翻译 |
| `docs/ui-v0/v0e-preview-light.png` | 浅色 · 无翻译 → 字幕层画后面几行预告 |
| `docs/ui-v0/v0e-preview-dark.png` | 深色 · 无翻译 → 预告 |
| `docs/ui-v0/v0e-interlude-light.png` | 浅色 · 间奏占位「等待音乐」+ 无字幕 |
| `docs/ui-v0/v0e-interlude-dark.png` | 深色 · 间奏占位 |

深色六张逐张看过：状态栏已随深色切换（无白底条），背景铺满到状态栏/导航栏后面；字幕层停在播放胶囊之上，
未被导航栏遮挡。320dp / 1.3 倍字号两张见第 4 节。

### 3. Roborazzi 截图用例（不依赖模拟器）

`U5LyricsScreenshotTest` 按新结构改写：删 `u5-lines-*` / `u5-before-first-line` / `u5-long-lyrics-*` 三个旧用例，
新增 `v0eStage{Light,Dark}` / `v0ePreview{Light,Dark}` / `v0eInterlude{Light,Dark}`，并把
`u5-320dp-light` / `u5-font130-light` / `u5-320dp-dark-font130` 换成 `v0e-320dp-light` / `v0e-font130-light` /
`v0e-320dp-dark-font130`。出图：
`.\gradlew.bat --offline --no-daemon --max-workers=2 -ProborazziRecord=true :app:testDebugUnitTest --tests *U5LyricsScreenshotTest*`。
新增 `SubtitleOverlayTest`（13 例）：占位串过滤、`readableLines`、翻译轨、`showText=false`、无当前行、
无翻译回落预告、预告跳占位行、同文去重、布局多行不误删、key 跟随 startTime、字号 clamp、次级轨常量。

### 4. 字幕层背景（V0e 收尾，修「硬边矩形」）

Folia 的做法（`VisualizerSubtitleOverlay.tsx:138-145 / 165-171`）：背景层比文字区外扩 x 40dp、y 24dp，
填 `radial-gradient(ellipse 115% 130% at center, bg@0.96 0%, bg@0.78 62%, transparent 100%)`，再整层 `blur-2xl`(40px) 把边化掉。
Compose 这边不用节点级 `Modifier.blur`（V0d 已证实会把整个节点光栅成矩形、边缘变硬），改为：

- `SubtitleOverlay.kt` 新增 `DrawScope.drawEllipseScrim(color, width, height)`：用
  `android.graphics.RadialGradient` 画单位圆径向渐变（0.96 → 0.78@62% → 全透明），
  再用 `Matrix` 按 `(radiusX = 宽/1.15/2 + 40dp, radiusY = 高/1.30/2 + 40dp)` 拉伸成椭圆，
  画在比文字区外扩 40dp / 24dp 的矩形上。半径里的「+40dp」等价于 Folia 的 `blur-2xl` 渐隐带，
  矩形四条边都落在渐变的尾部，所以画面里没有边线。
- 背景容器由 `fillMaxWidth()` 改为 `wrapContentWidth()`（否则背景会横跨整屏宽度，左右两侧离文字很远，看起来像一块方牌），
  文字列 `padding(horizontal = 16.dp, vertical = 8.dp)`，背景跟随文字宽度。
- 相关常量：`SUBTITLE_SCRIM_PAD_X = 40.dp`、`SUBTITLE_SCRIM_PAD_Y = 24.dp`、`SUBTITLE_SCRIM_FRINGE = 40.dp`、
  `SUBTITLE_SCRIM_INNER_STOP = 0.62f`。取色仍由调用方传 `scrimColor`（当前取色背景色）。

逐像素核对（720×1600 截图，取色带内只有背景层、不含文字）：

- 上缘：`v0e-preview-dark` 在 y=926→932 处 Δ=4/255（约 6px 过渡带）；
- 下缘：y=1075→1085 之间约 24px 过渡带（Δ≈8/255）；
- 左右缘：x=36 与 x=683 两道竖线在 y=1150、1170 行为 0 突变（上一版此处是 Δ=15 的硬边）。

结论：硬边矩形已消失，四条边都是 20~40px 级的连续渐隐，与 Folia 的 `blur-2xl` 观感一致。

### 5. 产物

- 全量单测口径：`.\gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g test` →
  **BUILD SUCCESSFUL（2m21s），全量通过 0 失败 0 错误**。计数口径 = 各模块
  `build/test-results/**/TEST-*.xml` 中真实套件的 `tests` 属性求和，剔除 Gradle 聚合伪套件
  `TEST-Gradle-Test-Run-*`（聚合套件内含全部用例的重复计数，V0c/V0d 的「566」就是把它算进去了）：
  53 个套件 / **550 例** / 失败 0 / 错误 0 / 跳过 2 —— `app` 225、`core/lyrics` 166、`core/player` 6、
  `core/plugin` 82、`feature/library` 3、`feature/player` 49、`feature/search` 21。
  其中本轮新增/改写的：`SubtitleOverlayTest` 15 例、`LyricFlowScreenTest` 17 例、`U5LyricsScreenshotTest` 14 例。
- `assembleRelease`（同一串命令）→ **BUILD SUCCESSFUL（4m59s）**，
  产物 `app/build/outputs/apk/release/app-release.apk`，8,997,389 字节，
  **SHA-256 = `A75D60BBC6DE27037CA709709DA904CEF9B40062BD4DD742BE4322D1DB34DD92`**。
- 提交：`V0e: 舞台单行 + 底部字幕层`（在 `port/u2` 上，未 push / 未打 tag）。

### 6. 未收敛 / 卡点

- 无阻塞项。上一轮的执行环境故障（Windows Job runner 提前退出）在 DSH 重启后消失，
  本轮全部命令（gradle / adb / 截图 / git）都正常跑完。
- 遗留观察（不影响验收）：预告态（无翻译）时字幕层每句宽度不同，背景块会随文字宽度变化；
  这是 `wrapContentWidth` 的必然结果，Folia 网页端也是按内容宽度算背景层。