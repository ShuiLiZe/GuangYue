# Folia 样式基准截图（B0）

还原度规则（2026-11-08）：每个歌词样式按 Folia 原版重做，完成标准 = 与 Folia 同一首歌同一时刻的基准图并排对照。

## 同一首歌、同一时刻

- 歌词：Folia `dev/probes/visualizerMemory.probe.tsx` 的 `cjk=1` 合成歌（10 句中文长短句循环，每行 3 秒，逐字计时，译文 `translation N`，每 4 行副歌）。GuangYue 侧在 `app/src/debug/.../V0cStageHarness.kt` 的 `PROBE_CJK_LINES` 逐字照搬。
- 时刻：默认 t = 37.5 s（第 12 行「我们慢慢走回去，星河与风一起落在很远很远的地方」唱到一半）。
- 主题：Folia 探针用 `DEFAULT_THEME`（无封面）；GuangYue 取证页用封面色 #B4553C——底色差异是已知干扰项，重做时把取证页主题对齐到 Folia DEFAULT_THEME。

## 拍 Folia

1. `cd D:\workspaces\_folia-src; npx vite --port 3000`（Node 22 可跑）。
2. `node docs/port/baseline/scripts/vis-capture.cjs [样式,…] [秒] [输出目录]`（用 playwright 自带 chromium，已 `npx playwright install chromium`；本机无 Chrome）。
3. 视口：`p800` = 800×360 CSS × 2 = 1600×720（与 gy34 模拟器横屏同像素），`d1600` = 1600×720 桌面宽窗。
4. URL：`/dev-probe.html?probe=visualizerMemory&vis=<样式>&cjk=1&speed=0&start=<秒>`，定格后等 7 s 截图。

## 拍 GuangYue

- 模拟器横屏：`adb shell wm user-rotation lock 1`（竖屏 `lock 0`）。
- `powershell -File docs/port/baseline/scripts/gy-vis-shot.ps1 -styles monet,… -ms 37500 -dark true`
  = `am start -n com.guangyue.music/.V0cStageActivity --es v0c_fixture probe --el v0c_ms 37500 --es v0c_style <样式>`，等 24 s 截图（冷启动慢，14 s 会拍到白屏）。

## 产物

- `screenshots/visualizer/folia-<样式>-t37.5-{p800,d1600}.png`、`gy-<样式>-t37.5-land.png`
- `compare/b0-<样式>-compare.jpg`（上 Folia、下 GuangYue），`compare/b0-overview.jpg`（七种一览）。

## B0 结论（重做前的现状差距）

| 样式 | Folia 原版 | GuangYue 现状 |
|---|---|---|
| 莫奈 | 左侧竖排歌词列表（上一句/当前/译文/下一句），右侧圆角卡片 + 点阵颗粒 | 横屏只剩标题区，歌词被挤出画面 |
| 时计 | 左侧大齿轮机芯，歌词在齿轮右侧沿弧排开，上下句在轮缘 | 大体对，但齿轮过小、缺上下句与机芯层次 |
| 雾笺 | 城市夜景光斑 + 黑色水墨字块散落 | 光斑对，字成整行排版，缺墨块碎裂 |
| 商籁 | 关键词组 + 线框几何（圆环、花形、对角线） | 换成了五线谱横排海报，版式不同 |
| 花火 | 斜向彩字长条 + 大圆弧 + 烟花 + 侧边纹带，满屏 | 只有一朵烟花 + 一条横向红底字 |
| 凝彩 | 灰阶对角分割 + 巨型立体字「与风」 | 横屏布局破碎（白底 + 红条） |
| 绘光 | 暗场光路折线 + 逐字大小变化的歌词带 | 同心圆光环 + 单行字，光位族不对 |

重做顺序：按源码体量从小到大——莫奈(183 KB) → 时计(230 KB) → 雾笺(273 KB, 4 个着色器) → 商籁 → 绘光 → 凝彩 → 花火。
