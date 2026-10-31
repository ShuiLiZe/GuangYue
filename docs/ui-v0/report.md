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
