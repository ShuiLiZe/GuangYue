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
| `docs/ui-v0/v0e-320dp.png` | 宽 320dp（`wm density 360`，拍完 reset）· 无翻译 → 预告 |
| `docs/ui-v0/v0e-font130.png` | 系统字号 1.3（`font_scale 1.3`，拍完恢复 1.0）· 无翻译 → 预告 |

八张全部在 V0e-fix 修复后重拍（`v0c_ms = 6000`，拍摄时 `mCurrentFocus` 均为
`com.guangyue.music/.V0cStageActivity`，无 ANR 弹窗），文件时间 11:31:26 ~ 11:32:53。
逐张看过：状态栏已随深色切换（无白底条），背景铺满到状态栏/导航栏后面；字幕层停在播放胶囊之上，
未被导航栏遮挡；播放胶囊里只剩「图标 + 时间 + 进度条」，没有多余的白色圆角块。

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
- 背景层基准宽度取 **Column 的实测宽度（= 最宽行宽度）与全部行高度**，再各边外扩 40dp / 24dp：
  容器本身 `fillMaxWidth()`（这样背景可以四边溢出、不被父级 `pageH` 内边距裁成矩形），
  子 `Column` 用 `onSizeChanged` 把自身尺寸回填给绘制层。第一版按容器宽度算，320dp / 1.3 倍字号下
  最长行会跑出背景框外，就是这么修的。
- 背景**不用** `drawWithCache`（外部状态变化时不一定重算，会留着上一版的半成品宽度），用 `drawBehind`。
- 文字**不加** `TextStyle(shadow = Shadow(...))`：阴影在字形外按 `blurRadius` 铺一层近似矩形，
  正是审图里那块「硬边方块」的来源之一。
- 相关常量：`SUBTITLE_SCRIM_PAD_X = 40.dp`、`SUBTITLE_SCRIM_PAD_Y = 24.dp`、`SUBTITLE_SCRIM_FRINGE = 40.dp`、
  `SUBTITLE_SCRIM_INNER_STOP = 0.62f`。取色仍由调用方传 `scrimColor`（当前取色背景色）。

逐像素核对（720×1600 截图，取色带内只有背景层、不含文字）：

- 上缘：`v0e-preview-dark` 在 y=926→932 处 Δ=4/255（约 6px 过渡带）；
- 下缘：y=1075→1085 之间约 24px 过渡带（Δ≈8/255）；
- 左右缘：x=36 与 x=683 两道竖线在 y=1150、1170 行为 0 突变（上一版此处是 Δ=15 的硬边）；
- `v0e-320dp` / `v0e-font130`：上缘一行内 Δ≈14、左缘在 y=1150 行 0 突变，同为窄过渡带；
  四行文字（含最长行「我面无表情看孤独的风景」）全部落在暗区内。

结论：硬边矩形已消失，四条边都是 20~40px 级的连续渐隐，与 Folia 的 `blur-2xl` 观感一致。

同一轮还修掉播放胶囊里的白色圆角块：`LyricBar` 原来用 Material3 `Slider` 只把 `thumbColor` 设成透明，
thumb 仍是 20dp 实心白圆。现改为自绘 —— `thumb = {}`，`track` 画 2dp 圆角胶囊（底 `pillInk@0.22`、
已播 `pillInk@0.85`），只剩「时间 + 进度条」。

### 5. 产物

- 全量单测口径：`.\gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g test` →
  **BUILD SUCCESSFUL（1m54s），全量通过 0 失败 0 错误**。计数口径 = 各模块
  `build/test-results/**/TEST-*.xml` 中真实套件的 `tests` 属性求和，剔除 Gradle 聚合伪套件
  `TEST-Gradle-Test-Run-*`（聚合套件内含全部用例的重复计数，V0c/V0d 的「566」就是把它算进去了）：
  54 个套件 / **552 例** / 失败 0 / 错误 0 / 跳过 2 —— `app` 225、`core/lyrics` 166、`core/player` 6、
  `core/plugin` 82、`feature/library` 3、`feature/player` 49、`feature/search` 21。
  其中 V0e 新增/改写的：`SubtitleOverlayTest` 15 例、`LyricFlowScreenTest` 17 例、`U5LyricsScreenshotTest` 14 例。
- `assembleRelease`（同一串命令）→ **BUILD SUCCESSFUL（5m14s）**，
  产物 `app/build/outputs/apk/release/app-release.apk`，8,997,389 字节，
  **SHA-256 = `74D0F6DEACCE3BCB567C8292BBFBA8F39A2050E6EB786884D2DAEBB6395C6664`**。
- 提交：`V0e: 舞台单行 + 底部字幕层`（`bbad5f0`）+ `V0e-fix: 字幕层渐变 + 重拍`（在 `port/u2` 上，未 push / 未打 tag）。

### 6. 未收敛 / 卡点

- 无阻塞项。上一轮的执行环境故障（Windows Job runner 提前退出）在 DSH 重启后消失，
  本轮全部命令（gradle / adb / 截图 / git）都正常跑完。
- 遗留观察（不影响验收）：预告态（无翻译）时字幕层每句宽度不同，背景块会随「最宽行」宽度变化；
  这是按内容宽度算背景层的必然结果，Folia 网页端也是同一算法。


## V0e-fix2: 字幕暗区不裁剪

**问题**：2e7665b 的字幕暗区左右已渐隐，但上下被一刀切成直线（preview / 320dp / font130 里第 4 行
「我面无表情看孤独的风景」一半落在暗区外；stage 的英文译文那条暗带上下也是直边）。

**根因（与 Hark 判断一致，已核实）**：画 scrim 的 Box 同时挂了
.graphicsLayer { alpha = enter.value * SUBTITLE_OVERLAY_OPACITY; translationY = ... }，
alpha 恒为 0.6×enter < 1 → 该节点永远走离屏图层，绘制内容被裁到节点边界；而旧 drawEllipseScrim
用 canvas.drawRect(0, 0, size.width, size.height) 铺渐变，实际矩形覆盖的是 y ∈ [-PAD_Y, H-PAD_Y]
（上侧外扩 24dp 被图层裁掉 = 节点顶一条直线；下侧比节点底少 24dp = 节点底上方一条直线）。
左右方向矩形越界落到屏幕外，所以看不到直边。

**修法**（只改 feature/player/.../lyrics/SubtitleOverlay.kt，其余文件零改动）：
1. 删掉含 scrim 节点上的 graphicsLayer(alpha=…)；整层透明度改为 overlayAlpha = enter.value × 0.6
   乘进渐变的三个色标（0.96α / 0.78α / 0）与文字颜色（主行 ×0.82（次级轨）×overlayAlpha、
   预告行 ×0.72×overlayAlpha）。进场动画保留，改为只做 translationY 位移（ENTER × (1-enter)，20dp），
   对 Column 与 scrim 同时生效，位移不产生图层裁剪。
2. 渐变中心改为节点中心（文字居中处），绘制矩形向外扩到渐变终点之外
   （bleed = max(radiusX, radiusY) + SUBTITLE_SCRIM_FRINGE，四边画到 -bleed … size+bleed）。
   矩形四边的 alpha 都是 0，**暗区边界只由渐变本身给出**，任何节点级裁剪都不再可能造成直边。
3. 未动：舞台位置/尺寸、LyricFlowScreen 的 padding(bottom = BAR_RESERVE + STAGE_RESERVE)、
   单行换行、辉光、播放胶囊、字幕层的屏幕位置与字号。逐张对比 2e7665b 截图，文字位置完全一致。

**实拍**（emulator-5554，720×1600 @320dpi，v0c_ms = 6000，mCurrentFocus 均为
com.guangyue.music/.V0cStageActivity，无 ANR 弹窗）。8 张 docs/ui-v0/v0e-*.png 全部重拍：

| 文件 | 拍摄条件 | 时间 |
| --- | --- | --- |
| v0e-stage-light.png | v0c_dark false，有翻译 | 12:22 |
| v0e-stage-dark.png | v0c_dark true，有翻译 | 12:28 |
| v0e-preview-light.png | v0c_variant preview，浅色 | 12:31 |
| v0e-preview-dark.png | v0c_variant preview，深色 | 12:46 |
| v0e-interlude-{light,dark}.png | v0c_variant interlude，间奏占位 | 12:46 |
| v0e-320dp.png | wm density 360 拍完 wm density reset | 12:47 |
| v0e-font130.png | font_scale 1.3 拍完恢复 1.0 | 12:48 |

放大逐张审图（字幕区裁到 y 860~1260 再 2 倍放大，新旧左右并排）：
- stage：旧图译文那圈暗带上下是两条直线横贯全宽；新图是完整椭圆，上缘在文字上方约 240px 开始渐暗、
  下缘渐隐到胶囊之上，**四边均无直线**；英文译文位置与旧图逐像素一致。
- preview / 320dp / font130：4 行全部落在暗区内，第 4 行「我面无表情看孤独的风景」下沿距暗区
  下缘仍有渐变余量；左右无竖边、上下无横边。font130 里第 4 行文字外接矩形 y=1050~1098 仍在椭圆内。
- interlude：无字幕、无暗区，与 V0e 一致（无回归）。
- 数值核对（取横向 x=0~200 的无文字背景列，逐行均值，y 860~1260）：
  preview-light 最大行间跳变 0.74/255、preview-dark 0.80、interlude 0.47~0.52；
  stage-light 12.57 与 font130 8.50 的跳变都出现在「文字字形边缘」那一行（stage 的 x=0~200 里有译文左侧笔画），
  不是背景台阶；同一 x 带里 y 上下一行即可恢复，属文字不是暗区边。

**产物**：test → **BUILD SUCCESSFUL（1m16s）**，53 个真实套件 / **550 例** / 失败 0 / 错误 0 / 跳过 2
（app 225、core/lyrics 166、core/player 6、core/plugin 80、feature/library 3、feature/player 49、
feature/search 21；口径同上，剔除 TEST-Gradle-Test-Run-* 聚合套件）；
assembleRelease → **BUILD SUCCESSFUL（4m9s）**，app/build/outputs/apk/release/app-release.apk 8,997,389 字节，
**SHA-256 = 25CB6085D008A3E7CD1939B6332359172E0A317D03102BCF116C508AF05FC702**。
提交：V0e-fix2: 字幕暗区不裁剪（port/u2，未 push / 未打 tag / 未动 stash）。

**过程中排掉的两个坑**：-Dorg.gradle.jvmargs=-Xmx2g 在 PowerShell 里必须加引号（否则被拆成
Task '.gradle.jvmargs=-Xmx2g' not found）；v0c_variant 是 String extra，要用 --es 传（--ez 会报
Invalid boolean value: preview）。另外拍摄中途模拟器进程崩过一次（adb devices 变空、qemu 进程消失），
按同一 AVD gy34 原地重启后继续，分辨率/密度/字号都已核对回默认值。


## V1a: 浮名样式内核与镜头

把 Folia 网页端「浮名」（visualizer/fume）的排版与镜头算法移植到 Android，作为 `LyricStyle.FUME`
的正式实现。内核放在 `core/lyrics`（纯 Kotlin，可 JVM 单测），绘制层放在 `feature/player`。
`LyricStyle.CLASSIC`（流光）路径一行未动。

### 1. Folia 文件 → Kotlin 文件对照

| Folia（`src/components/visualizer/fume/`） | 广乐（Kotlin） | 说明 |
|---|---|---|
| `fumeMath.ts` + `fumeTypes.ts` | `core/lyrics/.../fume/FumeCore.kt`（`FumeMath` / `FumeViewport` / `FumeVariant` / `FumeTuning` / 数据模型） | 数值工具、视口、变体、调参、块与文章的不可变模型 |
| `fumeArticleLayout.ts` + `fumeTextMeasure.ts` + `tuning.ts` | `core/lyrics/.../fume/FumeArticleLayoutBuilder.kt` | 纸张宽度、列数、字号二分、贪心折行、hero（大标题块）挑选、确定性打散（seeded FNV-1a）、缓存键 |
| `fumeCamera.ts` + `fumeCameraStep.ts` | `core/lyrics/.../fume/FumeCamera.kt`（`FumeCamera` 纯函数 + `FumeCameraStep` 步进器） | 焦点点、镜头缩放、重定向时长、文章概览镜头、跨屏飞行桥、弹簧积分 |
| `fumeReveal.ts` | `core/lyrics/.../fume/FumeReveal.kt` | 词/字素打印进度、行内截断时刻（`linePassCutoffTime`） |
| `fumeTextStyle.ts` | `core/lyrics/.../fume/FumeTextStyles.kt` | 等待/打印/已读三态样式、颜色轨时长、屏幕剔除、静态层透明度 |
| `fumeCanvasText.ts` + `fumeLiveRaster.ts` | `feature/player/.../visualizer/fume/FumeStage.kt`（`FumeBulletinStage`） | 世界坐标绘制、逐字素墨色（打印/颜色轨/底影/辉光）、打印印戳 |
| `fumeTextMeasure.ts`（canvas.measureText） | `feature/player/.../visualizer/fume/FumeTextMeasurerAndroid.kt` | 用 `TextMeasurer` 量字宽，结果喂回排版器 |
| `VisualizerFume.tsx` 的 canvas 挂载层 | `feature/player/.../lyrics/LyricFlowScreen.kt` | 浮名 Canvas 铺在 `VisualizerBackdrop` 的 content 层（整屏），顶栏/播放胶囊浮在它上面 |
| `useFumeCanvasStage.ts` 的每帧循环 | `FumeStage.kt` 的 `LaunchedEffect` + `withFrameNanos` | 帧时钟取 `VisualizerClock`，相机状态由组件持有 |

单测：`FumeArticleLayoutBuilderTest`、`FumeCameraTest`、`FumeRevealTest`、`FumeTextStylesTest`（均在 `core/lyrics/src/test`）。

### 2. 手机化改动及原因

1. **浮名 Canvas 整屏 edge-to-edge。** Folia 的 canvas 是 `VisualizerShell` 内 `absolute inset-0` 的全屏层，
   顶栏/字幕/播放条都是浮在其上的独立层。原实现把 `FumeBulletinStage` 挂在被顶栏与字幕层挤压的
   `weight(1f)` 舞台 Box 里，日志里视口只剩 328×84dp，文章排版被压成一条横缝。
   现改为在 `VisualizerBackdrop` 的 content lambda 中、与 `Column` 平级渲染，视口变为 360×800dp（整屏，
   状态栏与导航栏下方区域一并使用）；舞台 Box 内浮名分支留空，`LyricSubtitleOverlay` 在浮名样式下不渲染
   （Folia 浮名把整首歌排进文章自己打印，字幕层会再补一份居中译文，实拍中表现为同一句出现两遍）。
   流光走原路径，仍用被挤压后的舞台高度。
2. **删掉 hero「按块宽等比缩进」。** 前任在 `drawLiveBlock` 里按「块宽 vs 视口宽」给整块加了一层
   `scale(fitScale)`，Folia 没有这段。已整段删除：镜头的 scale 只由 `FumeCamera.cameraScaleForBlock`
   决定 —— 目标行高 `clamp(min(视口宽,高) × 0.115, 64, 124)` dp 除以块行高，再 clamp 到 `0.88–2.2`。
   块比屏幕宽、行尾未唱的字出屏，都是 Folia 原样；长句靠镜头焦点沿正在打印的字素向右平移来读
   （`FumeCamera.steppedBlockFocusPoint`：`x = clamp(行左 + widthBetweenOffsets(行首, 已打印字素), 行左, 行右)`）。
3. **dp 世界坐标 + density 一次缩放。** 全部排版与镜头计算都在 dp 空间完成，Canvas 里只在最外层
   做一次 `scale(density)`，再 `translate(视口中心) → scale(camera.scale) → translate(-camera)`，
   与 Folia 的 `translate(cx,cy) → scale(s) → translate(-x,-y)` 逐一对应；两处 `scale` 都显式传
   `pivot = Offset.Zero`，避免 Compose 默认以中心为轴带来的二次偏移。
4. **文字中线对齐。** `resolveBlockTiming` 给出 `baselineOffset = 行高 × 0.52`（CJK）/ `0.5`（拉丁），
   绘制时把 `TextLayoutResult` 的顶边拉到 `中线 − 文本高 × 0.5`，与 Folia 的 `textBaseline = middle` 一致。
5. **固定帧推进替代 `withFrameNanos`。** 模拟器上 `withFrameNanos` 常常只出一帧，实拍无法复现「正在打印」。
   取景开关 `v0c_fumesteps N` 让镜头以 `staticMode` 直落目标机位并把时间推到该句渲染末尾前 0.05s，
   `v0c_fumetime T` 指定结算到哪一秒；两者只在 debug 取证源集里生效，业务代码默认关闭。
6. **文本绘制走 `TextLayoutResult`。** `DrawScope.drawText(TextLayoutResult)` 而不是 `drawText(String)`，
   否则字宽与排版器的 `measureWidth` 不一致，逐字素定位会漂移。

### 3. 实拍（emulator-5554，720×1600 @320dpi）

| 截图 | 说明 |
|---|---|
| `v1a-fume-active-light.png` | `fumetime 5.2 / dark false`，浅色，当前句放大、正在打印，活动字带光晕 |
| `v1a-fume-active-dark.png` | 同参数 `dark true`，深色，其余块仅剩极淡等待底影 |
| `v1a-fume-late-dark.png` | `fumetime 19.6 / dark true`，句中后段：正在唱的字落在屏中，已唱的字在左侧、行尾出屏 |
| `v1a-fume-passed-dark.png` | `fumetime 11.9 / dark true`（V1a-fix2 重拍成深色），上一句刚唱完，末行沿颜色轨褪成已读淡影 |
| `v1a-fume-320dp.png` | `wm size 640x1600` + `wm density 340`（320dp 宽），窄屏单栏排版（拍完已 `wm size/density reset`） |
| `v1a-fume-font130.png` | `settings put system font_scale 1.3` 下的浮名（拍完已还原 1.0） |
| `v1a-style-sheet.png` | 歌词样式弹层：流光 / 浮名两行，浮名选中态（debug 源集 `v0c_style sheet` 直接渲染） |
| `v1a-classic-regress.png` | `v0c_ms 2000 / style classic`，流光舞台照常，无回归 |

逐张看过：浮名各张里当前句按 `cameraScaleForBlock` 放大到约 2.2 倍，字在屏内、行尾未唱部分出屏，
块外无多余的居中译文；背景是封面调色板的整屏渐变，顶栏与播放胶囊浮在画布之上，状态栏/导航栏区域
也被画布覆盖（edge-to-edge 生效）。光晕是贴着字形的柔和外发光，4× 放大看不到任何偏移副本。
流光那张仍然是单行居中 + 底部字幕层，与 V0e-fix2 的现状一致。

### 3b. 光晕实现（V1a-fix）

原来 `drawGlow` 在字周围画 6 圈低透明度偏移副本，实拍表现为粉色错位重影。现改为 Compose 的
`TextStyle.shadow`：`offset = Offset.Zero`、`blurRadius = round(dp)`，等价 Folia 的 `canvas.shadowBlur`
+ `shadowColor`。两个来源都按 Folia 的公式取值 —— 等待/已读块取 `fumeBlockFrame.ts:142-160` 的
`(2 + fontPx×0.1) × 0.65 × passedGlowBase × glowMultiplier`，当前行取 `fumeCanvasText.ts:142-160` 的
`lineGlowBlur`（hero 12 / 其余 8 + 包络 × fontPx × 系数），阴影色取 `accent(alpha × 1.35)`。
模糊半径直接给世界坐标（dp）的原值，不再乘 `density × camera.scale`：外层 `withTransform` 已把
density 与相机缩放写进画布矩阵，Skia 的模糊核会随之缩放，再乘一次就是双重换算（320dpi 下约 4.4 倍）。
为此把前任改到一半留下的 `cameraScale` 形参与 `FumeGlowSample`（未定义类）一并收拾干净，并删掉
只服务旧 `drawGlow` 的 `cos/sin`。

### 3c. 镜头焦点跟字（V1a-fix2）

实拍里当前句行首永远压在屏幕中线、正在唱的字贴到右边缘甚至被切，是镜头取点算错。三处根因：

1. `FumeCameraStep.step` 用 `printedGraphemeCount`（整字数），镜头只会按整字跳台阶。改用
   `FumeReveal.continuousPrintedGraphemeProgress`（含小数），并修掉它「词已开始但没打完」时
   提前返回整数值的分支。
2. 取点公式按 Folia `fumeCamera.ts:17-110` 的 `resolveSteppedBlockFocusPoint` /
   `resolveSmoothBlockFocusPoint`：`focus.x = block.x + line.left + widthBetweenOffsets(行起点, 已打印偏移)`。
   Kotlin 侧新增 `FumeMath.offsetAnchorX(lineOffset, lineWidth, graphemeCount)` ——
   行宽按字素数均分成 N 档，第 n 个字素左缘 = 行左缘 + n × (行宽 / N)，再在相邻两档间按小数插值。
   旧实现拿块内 `advances` 去索引行内序号，整行宽度会被算成行末（镜头推到右边缘）或恒为行首。
3. 重定向相位：新的一句开始跟随时 `retarget.startedAt` 取当前时刻、`fromX/fromY` 取当前镜头位置；
   固定帧推进（`staticMode`，取景 harness / reduceMotion）没有真实帧时长，相位永远推不动，
   `entryFocusBias` 恒为 1 会把镜头钉回句首 —— 现在 `staticMode` 下转场直接视为已完成。

JVM 单测锁住（`FumeCameraTest`）：给定块与进度断言 `smoothBlockFocusPoint(...).x`
（行首 104 → 每档 +50 → 行右缘 304）、小数插值一例，以及 `staticMode` 按该时刻 `printedProgress` 落点
（10s → lineLeft+200）。

### 4. 过程中排掉的坑

- 先把屏上那行居中小字误认成「浮名排版没生效」，实为 `LyricSubtitleOverlay` 的译文层叠在上面。
  定位手段：`uiautomator dump` 看 `TextView` 的 `bounds` 与字号，确认它属于字幕层而不是 Canvas。
- `Snapshot.withoutReadObservation` 包裹绘制期读取 `cameraState` 并不能修复「只出一帧」，
  真因是模拟器帧调度；不要在这上面继续做对照实验。
- 模拟器重启后 debug APK 会丢（`Activity class does not exist`），需 `adb install -r -t` 重装。

### 5. 产物

- `gradlew.bat --offline --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2g" test` →
  **BUILD SUCCESSFUL**，57 个真实套件 / **587 例** / 失败 0 / 跳过 2
  （口径同 V0：`**/build/test-results/**/TEST-*.xml` 的 `tests` 求和，剔除 `TEST-Gradle-Test-Run-*` 聚合伪套件。
  V1a 时的 58/586 与本轮 57/587 的差异来自 Roborazzi 截图套件按环境动态跳过整类，不是用例丢失；
  本轮新增的镜头单测都在这 587 里。）
- `assembleRelease` → **BUILD SUCCESSFUL（与 test 同一条命令，3m43s）**，产物
  `app/build/outputs/apk/release/app-release.apk`，9,030,557 字节，
  **SHA-256 = `322C17D0C033A27BB4D81D9488B7AB4DF9FCAAD818F64CDC432E4D772658EBA3`**
  （V1a 的 `434EB8A3…`、V1a-fix 的 `293911A4…` 是改动前的包；release 体积未变，字节差异来自重新打包）。
- 提交：`V1a: 浮名样式内核与镜头` → `V1a-fix: 浮名光晕改 shadowBlur + 补拍`
  → `V1a-fix2: 镜头焦点跟字 + 补拍`（均只在 `port/u2` 本地，未 push / 未打 tag / 未动 stash）。

### 6. 未做项（归 V1b）

- **tuning 面板**：`FumeTuning`（隐藏打印符号、几何背景开关与不透明度、文字停留比、镜头跟随模式
  STEPPED/SMOOTH、镜头速度、辉光强度、hero 缩放）已在核心里做了 clamp 与解析，但没有 UI；
  Folia 的 `tuning.ts` 面板未移植。
- **几何背景**：`FumeBackground.ts` 的几何形（漂浮方块/线框）与 `FumeBackgroundViewResolver` 的
  视口选择只留了接口占位，当前固定 `disableGeometricBackground = true`。
- **上滑换歌 / 双指缩放的手机手势映射**：Folia 靠滚轮与拖拽驱动概览镜头，手机端交互待定。
- 极长行（超过约 40 字）在 360dp 宽屏上的断行效果尚未与网页端逐像素比对。

## V1b-1: 镜头取点改实测字宽（收尾）

### 1. 问题与根因

V1a-fix2 的取点用 `FumeMath.offsetAnchorX`：把行宽按字素数均分成 N 档，第 n 字素左缘 = 行左缘 + n × 行宽/N。
纯汉字行看不出问题，中英混排一定偏 —— 同一行里 `o` 只有 11dp、`世` 有 20dp，均分档位与实测字形不是一回事，
行末 `glyphOffsets` 收口、行尾按行宽补齐这些细节均分公式也一条都没吃进去。

### 2. 改法（对齐 Folia）

| Folia（`src/components/visualizer/fume/`） | 广乐实现 |
|---|---|
| `fumeTextMeasure.ts:72-90` `widthBetweenOffsets(prepared, segmentMetas, from, to)` | `FumeRenderLine.blockWidthBetween(fromOffset, toOffset)`（行内版，终点 ≥ 行尾按行宽收口） |
| `fumeTextMeasure.ts:113-122` `resolveGlyphAdvance(renderLine, graphemeIndex)` | `FumeRenderLine.glyphAdvance(lineLocalIndex)`（下一 glyphOffset − 当前，末字素取行宽 − 当前，`max(…, 0)`） |
| `fumeCamera.ts:83-96` `resolvePointOnRenderLine` | `FumeCamera.centeredGlyphFocusPoint`：`whole = floor(行内偏移)`、`fractional = 偏移 − whole`、`x = block.x + line.left + blockWidthBetween(行起点, 行起点+whole) + glyphAdvance(whole) × (0.5 + fractional)` |
| `fumeCamera.ts:59-62` `findIndex(l => offset <= l.end)` | `smoothBlockFocusPoint` 的 `indexOfFirst { effectiveOffset <= it.end }`（V1b-1 由 `<` 改回 `<=`） |
| `fumeCamera.ts:33` `find(l => effectiveOffset <= l.end)` | `steppedBlockFocusPoint` 同一判定 |

- **删掉的**：`FumeMath.offsetAnchorX` 及其 KDoc；`FumeCore.kt` 里 `blockWidthBetween` 上重复的一段 KDoc 合并成一条，
  并把 V1b-1 的实测宽度口径写进去。
- **与 Folia 的一处有意偏离**：Folia 的 `x` 是字素左缘 + `advance × fractional`；本仓取**字心**
  （`advance × (0.5 + fractional)`，V1a-fix3 为修「正在唱的字落在屏宽 60~67%」引入），且不夹到行左/右缘 ——
  夹取会让末字永远到不了屏心。行末（`offset == 行内字素数`）两边同值：Folia 的 `localGlyphIndex` 越界 ⇒
  `advance = 0`、`fractional = 0`，本仓 `glyphAdvance(whole)` 同样越界取 0，都落在「行左缘 + 行宽」= 行右缘。

### 3. FumeCameraTest 修正

- `smooth focus point measures width from the line start offset`：行末 `offset = 4.0`（= 行内字素数）断言
  `x = 行左缘 + 200`（行右缘），注释写明与 `fumeCamera.ts:83-96` 同值的原因；补 `centeredGlyphFocusPoint(…, 4.5).x`
  与 `line.blockWidthBetween(line.start, line.end) == 200`，确认 200 是逐字推进给出的。
- 新增 `smooth focus point uses measured glyph widths on a mixed CJK and latin line`：`Hello 世界`
  `glyphOffsets = 0,11,22,33,44,55,61,81`、行宽 101（8 字素，均分会给 12.625/档）。断言 `blockWidthBetween(0,6) == 61`、
  `glyphAdvance(6) == 20`、第 7 字素字心 `x = 行左缘 + 71`、小数推进 `6.5 → 81`、行尾 `8.0 / 9.0 → 101`（= 实测行宽）。
- 新增 `smooth focus point at the line end follows Folia without jumping back`：`offset == 首行 end` 时不回跳首行，
  取首行末字字心（行宽收口给它 140 的 advance ⇒ 60 + 70 = 130）。
- 新增 `phone viewport lowers the target line height floor to 40dp` / `short line centres the whole sentence inside the phone viewport` /
  `long line focus lands on the screen centre`：360dp 短边下限 40、放得下就整句居中、放不下跟字且字心落屏宽 50%±2dp。

### 4. V0cStageHarness（debug 取证，release 不编入，已验证可编译）

1. `FUME_TEXTS` 追加中英混排句「hold on 别急着说再见」。
2. `LRC` 常量改为 `fun fumeLrc(anchorLine, anchorTime)`：把锚点句整体挪到 `anchorTime`，其行内时间轴才覆盖定格时刻
   （此前整篇都在定格时刻之前，打印进度被钳成 0，画面只有总览远景、没有「正在唱」）。
3. 新增 `v0c_fumeline`（int）显式指定锚点句下标；不传时退回 `floor(fumeAt / 4)`。
4. `V0cStageActivity` 在 `style == "fume" && fumeAt >= 0` 打一行
   `V0cStage: v1b1 fume_at=… position_ms=… steps=… anchor_line=… text=…`，截图条件直接引这行日志。

### 5. 实拍（emulator-5554，720×1600 @320dpi，v0c_dark true）

| 截图 | 拍摄条件 |
|---|---|
| `docs/ui-v0/v1b-1-mixed.png` | `am start … --es v0c_style fume --ef v0c_fumetime 1.5 --ei v0c_fumesteps 24 --ei v0c_fumeline 20 --ez v0c_dark true`，force-stop 后冷启，等 26s `screencap`；logcat `v1b1 fume_at=1.5 … anchor_line=20`。当前句「hold on 别急着说再见」，粉色高亮的正在唱的字是 `h`，镜头把它放在屏宽约 43%（字心约 305px / 720px），是实测宽度的推进位置，不是均分档位 |

被否的取景（记下避免重走）：`fumetime 20.9 / steps 24` 是「凌晨三点的窗」中段（跟字正常，但不是含英文那句）；
`steps 0` + `v0c_fumetime` 会按真实帧时钟跑，模拟器 `withFrameNanos` 推不动、画面停在 position 末端且构图错乱 —— 取景必须 `steps ≥ 1`。

### 6. 产物

- 单测：`.\gradlew.bat --offline --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2g" :core:lyrics:test --rerun-tasks`
  → **BUILD SUCCESSFUL in 35s**；`core/lyrics` 18 个套件 / **208 例** / 失败 0 / 错误 0 / 跳过 0
  （计数口径：`core/lyrics/build/test-results/test/TEST-*.xml` 的 `tests` 求和；`FumeCameraTest` 15 例全绿）。原始输出末尾：

```
> Task :core:lyrics:compileKotlin
w: file:///D:/workspaces/GuangYue-u2/core/lyrics/src/main/kotlin/com/guangyue/core/lyrics/fume/FumeCamera.kt:409:54 Unnecessary non-null assertion (!!) on a non-null receiver of type 'FumeCameraViewTarget'.
> Task :core:lyrics:compileJava NO-SOURCE
> Task :core:lyrics:classes UP-TO-DATE
> Task :core:lyrics:jar
> Task :core:lyrics:compileTestKotlin
w: file:///D:/workspaces/GuangYue-u2/core/lyrics/src/test/kotlin/com/guangyue/core/lyrics/GlowWordVariantsTest.kt:53:40 Unnecessary non-null assertion (!!) on a non-null receiver of type 'SpringSpec'.
> Task :core:lyrics:compileTestJava NO-SOURCE
> Task :core:lyrics:testClasses UP-TO-DATE
> Task :core:lyrics:test
BUILD SUCCESSFUL in 35s
4 actionable tasks: 4 executed
```

  两条 `Unnecessary non-null assertion` 都是既有告警（`FumeCamera.kt:409` 的 `input.overviewCamera!!`、`GlowWordVariantsTest.kt:53`），本步未引入。
- 提交：`bb4ffdd` `V1b-1: 镜头取点改实测字宽 + 重拍中英混排`（`port/u2`，未 push / 未打 tag / 未动 stash）。
  滑杆相关的 `LyricStyleSheet.kt` 修改与未跟踪的 `FoliaSliderGeometryTest.kt` **未纳入本次提交**，原样留在工作区（V1b-2 范围）。
## P1b 舞台浮卡面板（2026-11-08，Hark 直接做）

- 依据 Folia `UnifiedPanel.tsx` + `panelTab/shell/PanelShell.tsx / SongHeader.tsx / TabDock.tsx / Segments.tsx / panelTheme.ts`。
- 舞台右下面板键不再整页跳 PLAYER：点开在键上方浮出玻璃卡（宽 360dp、不超过屏宽-32，圆角 20，内边距 12，顶部至少留 56dp），面板键变 X；点卡外 / X / 返回键收起。入场缩放 0.92 + 淡入（kit unfold 300ms），出场 move 220ms，变换原点在面板键。
- 卡内：歌曲头（封面页签 = 大方封面 + 歌名两行 + 歌手 + 专辑 + 喜欢/加入歌单/歌词样式三键；其它页签折成 48dp 一行，点回封面页签）→ 页签内容（超高卡内滚动）→ 底部 36dp 分段页签条「封面 / 控制 / 播放列表 / 音质」（Folia zh-CN `panel.*`，选中块滑动）。
- 控制：播放模式三段直选（顺序 / 单曲循环 / 随机）+「歌词样式」行（右侧当前样式名，点开样式弹层）。播放列表：当前曲高亮 + 其它行可移出。音质：按插件声明档位，未声明显示全部。
- 有意偏离：Compose 无背景模糊，玻璃不透明度 0.8 → 0.94；Folia 控制页的音量/均衡器/主题区手机上不做（系统音量键）。
- 顺带：`CoverImage` 改 `ContentScale.Crop`（Folia object-cover），B 站 16:9 封面不再留黑边。
- 测试：`:feature:player:testDebugUnitTest` 6 套件 68 例 0 失败（新增 `StagePanelTest` 4 例）。`:app:installDebug` 成功。
- 实拍：`docs/ui-v0/p1b-panel-{cover,controls,queue,quality}-dark.png`、`p1b-panel-cover-light.png`。
- 提交：`4e7d3cc`、`d5336d3`、`f49e9ba`（port/u2 已 push）。PLAYER 路由暂留（无入口），后续确认无引用后删。

## B2 Bravais 集合层（2026-11-08，Hark 直接做）

- 依据 Folia `library/suites/bravais/BravaisCollection.tsx`（集合 = 同一面墙换成集合曲目）、`BravaisSeamCollection.tsx / BravaisSeamCrumbs.tsx / BravaisFocusCardBody.tsx`。
- 首页墙：歌单 / 推荐歌单 / 榜单（PLAYLIST/SHEET/STATION）聚焦卡右下多一颗「进入」键（ArrowUpRight），再点一下聚焦卡也是进入；单曲仍是播放。
- 新路由 `collection`：墙数据换成该集合曲目（TRACK 砖，点开聚焦卡可播放 / 加入队列，播放时整个集合作队列从该曲开始）；缝换成集合信息条（高 148）：面包屑「‹ 广乐 / 歌单」返回、集合名 22/800、「N 首 · 歌单」或加载中 / 失败 / 空（点击重试），右侧「播放全部」实心胶囊 +「随机」。
- HomeViewModel 新增 `loadTracksOf`（空 / 出错也回调），集合页与首页共用 HOME 栈的 ViewModel（推荐歌单取曲依赖首页已拉的分类叶子）。
- 未移：收藏星标、过滤输入、列表面板、「⋯ 更多」、补页、集合飞入转场 collectionFlight、歌手页。
- 已知：模拟器上酷我推荐歌单 getMusicSheetInfo 返回 0 首（插件侧，归 M8），所以实拍是加载态；单测未新增（纯 UI 接线）。
- 实拍：`b2-focus-enter.png`（聚焦卡进入键）、`b2-collection-light.png`（集合信息条）、`p1b-panel-open-key-light.png`（面板开时圆键反色为关闭键）。
- 提交：`c009355`、`bf218e1` 及实拍提交（port/u2 已 push）。

## 首页冷启动修复 + S1 设置页（2026-11-08，Hark 直接做）

- 修：首页冷启动从不加载插件表（只有音源页 / 搜索页会 `PluginManager.load()`），`currentSource` 一直 null，推荐歌单 / 榜单永远拉不到；HomeViewModel init 里先 load 一次（幂等）。实测冷启动后 8 个推荐歌单正常出现。
- 调试：`getMusicSheetInfo` 空 / 失败打 `GYSHEET` 日志。酷我插件对推荐歌单抛 `empty sheet`（插件 JS 混淆，归 M8）。
- S1 设置页（Folia `SettingsModal` + `settingsNavModel.ts` 手机版）：顶部「搜索设置」，下面分类横滑分段（外观 / 播放页 / 播放控制 / 存储与备份 / 关于，名字取 zh-CN `options.settingsPage*`），页内 Folia kit Group/Row；搜索按行名 / 说明 / 组名过滤，跨分类按「分类 · 组」列出，无结果有提示。
- 新增设置：歌词样式（七种，与舞台共用 LyricStyleViewModel 落盘）、默认播放音质（`PlaybackController.setDefaultQuality`）、音源插件管理 / 下载设置入口、版本取 BuildConfig。原有深色模式、取链源音质、开源许可保留。
- 入口：「我的」顶栏新增齿轮（以前 SETTINGS 路由没有任何入口）。
- 实拍：`s1-settings-{appearance,player,playback}-light.png`。提交：`82fb129`、`39b7c48`、`4feb4a3`、`67c3d3c` 及实拍提交（port/u2 已 push）。

## P1c 面板「歌词」页签：时间轴偏移（2026-11-08，Hark 直接做）

- 依据 Folia `panelTab/SourceTab.tsx` + `LyricTimelineOffsetControl.tsx`（步长 250ms、可重置）+ `utils/lyrics/lyricOffsetMemory`（每首歌记一份）+ `incomingLyricClock.ts`（歌词时间 = 播放时间 − 偏移，正数歌词更晚）。
- 面板页签变为「封面 / 歌词 / 控制 / 播放列表 / 音质」（与基准 player-panel-*-m412 的五页签一致）。歌词页：歌词来源行（音源 · N 行 / 暂无歌词 / 加载中）+「时间轴偏移」− 值 + 重置，说明「正数让歌词更晚出现。每首歌各记一份。」
- LyricsViewModel：偏移按歌 key 存 SharedPreferences `lyric_offsets`（0 时删除），换歌时读回；行同步用 `position − offset`。LyricFlowScreen 新参数 `lyricOffsetMs`，逐字时钟同样减去偏移。
- 测试：`:feature:player:testDebugUnitTest` 6 套件 68 例 0 失败（StagePanelTest 页签断言更新）。
- 已知（非本步引入）：`:app:testDebugUnitTest` 里 Robolectric 截图套件（U5LyricsScreenshotTest、V0LyricStyleScreenshotTest 等）在本机全部 `AppNotIdleException`，单例约 1 分钟超时，整套跑不完，本步未用它们验收；需另查（疑似逐帧时钟在 Robolectric 下不空闲）。
- 实拍：`p1c-panel-lyrics-light.png`（+500 ms）。提交：`02ca340` 及实拍提交。

## 测试修复：截图套件 AppNotIdleException（2026-11-08）

- 原因：V2 通用几何背景 `GeometricBackgroundLayer` 在播放中跑 `withFrameNanos` 死循环，Robolectric 的 `waitForIdle` 永远等不到空闲，凡渲染 `LyricFlowScreen` 的截图用例都卡 1 分钟后 `AppNotIdleException`。
- 修：Robolectric 下按暂停画静帧（`paused = … || runningUnderRobolectric`），与逐帧时钟的既有处理一致。提交 `b264df3`。
- 结果：`U5LyricsScreenshotTest` 14/0、`V0LyricStyleScreenshotTest` 29/0；全量 `testDebugUnitTest :core:lyrics:test --continue`：67 套件 645 例 0 失败（BUILD SUCCESSFUL）。

## B1h 聚焦卡：正在播放 / 已在队列（2026-11-08）

- 依据 Folia `BravaisFocusCardBody.tsx`：当前在播的那块，播放键变「暂停 / 继续播放」（toggles），旁边多一颗「进入播放页」（Maximize2）；「加入队列」在已入队时显示「✓ 已在队列」且不再重复加入。
- 实现：BravaisWall 新参数 `isCurrent / playing / onTogglePlayback / onEnterPlayback / isQueued`；NavHost 用 `playback.queueSnapshot()` 的 key 集合判断（砖 key：首页单曲 `track:<key>`，集合页 `ctrack:<i>:<key>`），首页与集合页都接上。文案取 zh-CN `libraryBravais.*`。
- 验收：编译安装通过；模拟器上 B 站源取不到播放地址，最近播放不更新，未拍到「当前在播」态，留真机验收。提交 `be681ad`。

## P2 浮动播放胶囊（2026-11-08）

- 依据 Folia `FloatingPlayerControls.tsx` 展开态（grid-cols-[1fr_auto_1fr]、p-3、rounded-full、玻璃 + shadow-2xl）与基准 `screenshots-v2/m412-light-mini-bar.png`。
- 首页 / 搜索 / 我的底栏上方的迷你条改为三行胶囊：居中歌名 14/700（点进播放舞台）；左槽播放模式（顺序 / 单曲 / 随机，点按循环）、中间 48dp 实心圆播放键（底 = 主字色、图标 = 页底色）、右槽下一首；底行等宽时间 · 2dp 进度（主字色）· 时长。
- 手机化：Folia 默认槽位是「循环 + 歌词时间轴」，手机右槽换成下一首（原迷你条就有）；封面缩略图去掉（Folia 胶囊没有）。
- 截图用 Robolectric（`GUANGYUE_RECORD_SCREENSHOTS=true`，U2ShellScreenshotTest 喂了时间与模式槽）：`p2-mini-capsule-{light,dark}.png`。模拟器上 B 站源取不到链，实机态待真机。
- 提交：`db5a1dc`、`a596e2e` 及截图提交。

## P2b 舞台播放胶囊折叠 / 展开（2026-11-08）

- 依据 Folia `FloatingPlayerControls.tsx`：折叠态 CollapsedView 只有「时间 · 进度 · 时长」；展开态三行网格（居中歌名 / 左槽·48dp 实心播放键·右槽 / 进度行）。
- 舞台底部胶囊：默认折叠（56dp，点一下展开）；展开为 rounded 30 + p-3：歌名（点它收起）、播放模式（顺序/单曲/随机循环）、实心播放键（底 = 胶囊墨色）、下一首、可拖进度（松手 seek）。5 秒无操作自动收起（Folia 鼠标移开即收，手机用计时）。
- 修 bug：P1a 起舞台进度拖动后 `scrubbing` 永远为 true、从不调用 `onSeek`——拖完进度卡死、不跳转；现在松手 `onSeek(scrubValue)` 并复位。
- 截图用例：U5LyricsScreenshotTest 新增 `p2bBarExpanded{Light,Dark}`（`initialBarExpanded`，Robolectric 下不自动收起）。实拍 `p2b-bar-expanded-{light,dark}.png`。U5 套件 16/0。
- 提交：`046de78`、`daf422c`、`60cd6e0` 及截图提交。

## P3「莫奈」样式：海报版面（2026-11-08，Hark 直接做）

- 依据 Folia `visualizer/monet/VisualizerMonet.tsx`（左列：斜体歌手 clamp(1rem,1.8vw,1.8rem)@0.96 → 1×56 渐隐竖线 → 大标题 clamp(1.45rem,3.3vw,2.8rem) 600 两行截断 + 背景色投影 → 专辑大写 14@secondary0.84）、`MonetLyricsRail.tsx / monetRailLayout.ts`（当前句落在轨高 46%，前后行按 0.9^(d-1) 缩小、等待行比已唱行亮、按距离模糊，行距 active 18 / inactive 14，弹簧滚动）、`MonetRailLine.tsx`（当前句底色 primary@0.34，逐词填到 @0.98）。zh-CN `ui.visualizerMonet` = 莫奈，排在样式列表回环之后（order 90）。
- 这就是基准 player-playing-m412 左上那组「斜体歌手 + 竖线 + 大标题」——它是莫奈样式的版面，不是舞台通用顶栏，所以做成第八个样式而不是改所有样式的顶栏。选莫奈时顶栏只留返回键（标题由海报承担），字幕层不画（译文排在每句下）。
- 不做（手机/数据原因）：右侧肖像（Folia 本身 `hidden md:flex`，手机不显示）、莫奈背景 worker（沿用封面取色流体 + 通用几何）、底部频谱、浮动装饰、关键词着色、说明胶囊（无 theme.description 数据）。
- 点轨道上任一行跳到该句；减少动效时不做弹簧与模糊。
- 测试：`MonetRailTest` 4 例（档位、CSS clamp、逐词着色保留空格）。`:app:installDebug` 通过。
- 实拍：`p3-monet-dark.png`、`p3-monet-light.png`（V0cStageActivity `--es v0c_style monet`），并排对照 `p3-monet-compare.jpg`（左为基准）。

## P4「时计」样式：钟轮弧线歌词（2026-11-08，Hark 直接做）

- 依据 Folia `visualizer/pendolo/VisualizerPendolo.tsx` + `pendoloGeometry.ts`（轮心 (0, 0.5)、半径 0.42·min(w,h)、100° 弧 9 行一窗、行块高时角距加宽 (h1+h2+24)/(2R)、透明度 max(0.12, cos(0.75a)^2.5·(1−0.18d))、当前句 ×1.25、其余 max(0.7, 1−0.08d)、文字自身转 0.35a、左中为原点）、`pendoloTimeline.ts`（110° 可见、边缘 28° 渐隐）、`pendoloClockworkDraw2d.ts` + `pendoloClockworkScene.ts`（subtle 档 m=0.6：中心暗渐变 1.65R、五道导环、60 刻度、36 齿主轮、六辐内轮、斜面双环、扭索纹 48 道、12 铆钉、中心小齿轮反转 2.5 倍）、`PendoloActiveLyricSweep.tsx`（当前句底 primary@0.52，填 mix(primary, accent, 0.32)）。zh-CN `ui.visualizerPendolo` = 时计，样式列表排在莫奈后（order 100）。
- 擒纵：tick 弹簧追当前句下标，整轮按 −(tick−当前)·角距转，钟芯齿轮跟着同一角度走。点弧上任一行跳到该句。减少动效时直接落位。
- 不做：WebGL 机芯（摆轮 / 游丝 / 行星齿轮 / 日内瓦机构 / 平衡轮图标）、智能动感冲击波、副歌光晕与标记、表盘封面（Folia 默认关）。扫光改逐词着色。
- 测试：`PendoloGeometryTest` 3 例 + `MonetRailTest` 4 例 0 失败；`:app:installDebug` 通过。
- 实拍：`p4-pendolo-{dark,light}.png`（`--es v0c_style pendolo`）。手机窄屏下当前句 + 译文块较高，下一句贴得近但不重叠。

## P5a 删除 PLAYER 旧整页路由（2026-11-08 06:05）
- P1b 起面板已是舞台浮卡，全仓无 navigate(Routes.PLAYER) 入口；删除 NavHost 的 composable(Routes.PLAYER) 块、Routes.PLAYER 常量与 IPlayerRoute import，ShellNavTest 去掉两处引用。提交 95a1d86。

## P5b 雾笺样式（Folia nebbia 手机版，2026-11-08 06:25）
- 新增 LyricStyle.NEBBIA（id `nebbia`，中文「雾笺」= zh-CN.ts ui.visualizerNebbia），无参数；接法同莫奈/时计（strings、弹层列表与无参数分支、NavHost 两处、PlayerRoute、V0cStageHarness 两处、LyricFlowScreen 整屏层）。
- visualizer/NebbiaStage.kt：夜色散景光斑 + 底部街灯暖光；离屏图层铺雾（冷灰、下浓上淡、凝结细点），歌词字形以 BlendMode.DstOut 逐字擦开（宽软描边 + 实心），中日韩逐字、拉丁按词，字的擦开时刻取逐词时间、词内均分。
- 排版：三个槽位 0.26/0.51/0.76H 轮换（第 i 与 i+3 句共槽），字号从 min(0.085H, 0.17W) 往下试到多行 ≤ 0.2H；横向左/中/右按句子种子挑，逐字 ±3.5° 倾斜。
- 时间：句子在下一句开唱与本句唱完取晚者后再留 1.5 s，之后 8 s 被雾吞回（Folia breathFade 8）；共槽的 i+3 句开唱时强制清零。约 28% 的字收笔挂水珠，0.8 s 后缓慢下淌。
- 未做：WebGL 折射/城市建筑缓存/雨刷颠簸/哈气/片尾曲名。单测 NebbiaTimingTest（2 例）。实拍 docs/ui-v0/p5-nebbia-9000.png、p5-nebbia-14000.png、p5-nebbia-pair.jpg。

## P6 商籁样式（Folia sonnet 手机版，2026-11-08 07:20）

- 依据 Folia `visualizer/sonnet/`：sonnetProgram.ts（切段 → 镜头 → 7 种版式 → 3 种转场）、sonnetTypographyRoles/Layout.ts（主角词评分、版式字号倍数与竖排）、sonnetGuides / FrameDecor / BackgroundDecor / StaffView（定位记号、开口框、漂浮小形、La Folia 五线谱）、DEFAULT_SONNET_TUNING（外框 full、巨字底纹）。
- 新文件 `visualizer/SonnetStage.kt`：`compileSonnet` 纯函数编排（空隙阈值 = 中位间隔×2.5 夹 1.25–3.5 秒；段 ≤6 行 ≤18 秒；竖屏一镜 ≤3 行 ≤6 秒；版式不与上一镜重复；段落交界转场 fast-blur / mono-glitch / camera-pull）；词块用系统分词（Android 为 ICU 词典），单字碎块两两合并、最长 4 字。
- 画面：type-impact 大字居中、editorial-column / mask-reveal 竖排主角柱 + 另一侧配角、fragment-collage 绕中心散落旋转、tracking-ribbon 斜色带反白 + 镜头横向跟唱、poster-blocks 种子切格 + 主角格实心反白、quiet-tableau 留白小字；词按唱到时刻逐个打进（ExpoOut），字到前 0.35 秒落点出现「+」定位记号，正在唱的词下划线推进；约 40% 词块套开口框与角饰；背后描边巨字底纹；外框角标（段号.镜号、时间码、版式名）与五线谱游标。
- 不做：Pixi 纹理池、镜头 MG 图库、后期滤镜（颗粒 / 半调 / 色散）、设置面板参数。
- 单测 `SonnetProgramTest`（镜头 ≤3 行且覆盖全部行、相邻版式不重复、空隙开新段带转场、词块计时与主角、中文块 ≤4 字不丢字）。
- 实拍：`p6-sonnet-4500.png`（碎片拼贴）、`p6-sonnet-dark.png`（色带）、`p6-sonnet-15500.png`（遮罩揭开竖排）。

## P7 花火样式（Folia hanabi，2026-11-08）

- 新文件 `feature/player/.../visualizer/HanabiStage.kt`：对照 Folia `visualizer/hanabi/`（hanabiProgram / hanabiSky / mg/burstPlan / mg/fireworks / color / text/lyricText+blocks+animations）。
- 程序 `compileHanabi`（纯函数）：每句开唱放一朵（长于 4.5 秒的句子中段补一朵），前奏、段间 ≥3.2 秒的空隙、尾奏铺スターマイン（两快一慢）；种类菊 / 牡丹 / 柳 / 輪 / 千輪 相邻不重复，左右交替落在上部 16%–46%。
- 烟花用 Folia burstPlan 的闭式解（阻尼 + 终端下落），升空尾迹 0.9 秒、开花闪光、菊末段转白与闪烁、牡丹中途变色、柳金色长尾、千輪子弹再开小花；BlendMode.Plus 叠加。seek 到任何时刻画面一致。
- 夜空：四色标渐变（色相往强调色偏 0.3）+ 星点 + 地平线桃色祭典光。
- 歌词：烟花下方、底部胶囊之上；字底色块三种（逐字方块 tile / 整行色带 band 带金边 / 斜带 slant），祭色朱红 / 瑠璃 / 柿色；逐字 rise 升入（拉伸后压扁回弹），唱到的字弹跳 + 染金，换句整行 ascend 飞走。硬阴影墨色 #0B0D24。
- 不做：Pixi 精灵池、拼贴 MG 图库、副歌全屏辉光、片尾卡、设置面板（无参数）。
- 单测 `HanabiProgramTest`（2 例）通过；实拍 `docs/ui-v0/p7-hanabi.jpg`（4.8 秒 band 版 + 菊；9.3 秒 tile 版 + 牡丹余烬）。
