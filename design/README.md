# 广乐 GuangYue · UI 设计稿参数说明

> 画布统一 **390 × 844**（手机竖屏）。纯 HTML + 内联 CSS，全部颜色直接写在 `:root` 变量里，便于迁移到 Compose 的 `Color(...)`。
> 字体一律用系统栈：中文衬线 `Songti SC / Noto Serif CJK SC / Source Han Serif SC / Times New Roman`；无衬线 `-apple-system / SF Pro Text / Segoe UI / Roboto / Noto Sans CJK SC / Microsoft YaHei`。不加载任何网络字体，Compose 侧建议用 `FontFamily.Serif` 与 `FontFamily.Default`。

---

## 0. 全局 Token

| Token | 值 | 用途 |
|---|---|---|
| `--blue` | `#1A73E8` | 唯一强调色：选中胶囊 / 进度条 / 播放按钮 / 当前歌词 |
| `--ink` | `#14202E`（首页）/ `#0F1A2B`（歌词流） | 主文字 |
| `--ink-2` | `#5B6879` | 次级文字 |
| `--ink-3` | `#98A3B1` | 提示文字 |
| 蓝阴影 | `rgba(26,115,232,.34 ~ .55)` | 蓝色元素投影 / 发光 |
| 中性阴影 | `rgba(24,44,72,.06 ~ .24)` | 卡片、悬浮条 |

圆角规范：`4`（拍立得相纸）/ `14`（歌词流封面小图）/ `18`（Tab 项）/ `22`（Tab 容器）/ `28`（首页迷你播放条）/ `30`（深色播放条 · 圆角条）/ `50%`（圆按钮、圆形封面）。

---

## 1. `home.html` 首页（浅色）

**背景（液态大理石）**
- 底色叠加 5 层 `radial-gradient` + `linear-gradient(165deg, #FBFCFE → #F1F4F9 → #EEF2F8)`：
  - `#FFFFFF`（左上）、`#E3EBFA`（右上，蓝雾）、`#F3E9E2`（右侧偏暖）、`#EAF1FB`（左下）、`#F7F3EF`（底部）
- 大理石流纹：内联 SVG 4 条贝塞尔曲线，`feGaussianBlur stdDeviation=6`，描边渐变 `#A9BACE / #C2D2E6 / #CBBFB4 / #E3D5C8`，`mix-blend-mode:multiply`，整体 `opacity .55`。
- 细网点：`radial-gradient(rgba(26,115,232,.16) 1px, transparent 1px)`，`background-size: 7px 7px`，用径向 mask 仅保留中部可见。

**顶栏**（`padding: 26px 20px 0`）
- App 名「广乐」：衬线 700，**27px / letter-spacing 2px**，`#101B28`；右侧 6px 蓝点 `#1A73E8`。
- 搜索胶囊：高 **38px**，左右 padding `13/15px`，圆角 **19px**，底色 `rgba(255,255,255,.55)`，描边 `1px rgba(255,255,255,.9)`，`backdrop-filter: blur(18px) saturate(160%)`，阴影 `0 2px 10px rgba(24,44,72,.07)`；放大镜 SVG 15px，描边 `#5B6879`；文字 13px `#98A3B1`。

**分段胶囊 Tab**（`margin: 22px auto 0`，`padding: 4px`）
- 容器：圆角 **22px**，底色 `rgba(255,255,255,.42)`，描边 `1px rgba(255,255,255,.85)`，阴影 `0 6px 20px rgba(24,44,72,.06)`，`blur(20px)`。
- 项：`padding: 8px 17px`，圆角 **18px**，13px / 字间距 0.6px。
- 选中：底色 `#1A73E8`，文字 `#FFFFFF` 600，阴影 `0 6px 14px rgba(26,115,232,.34)`；未选中：透明白，文字 `#4A5768`。

**Coverflow**（容器高 `330px`，上距 52px）
- 卡片：**150 × 196**，白底相纸 `#FFFFFF`，`padding: 7px 7px 0`，圆角 4px，底部留白 30px 作手写标签，阴影 `0 14px 30px rgba(22,40,66,.16) + 0 2px 6px rgba(22,40,66,.08)`。
- 中间卡：`scale(1.25)`，z=5，投影加深至 `0 26px 46px rgba(20,38,64,.24)`；封面图高度 196px。
- 次侧卡（±2）：`translateX(±118px) scale(.9) rotate(±7deg)`，`opacity .9`。
- 最外侧卡（±1）：`translateX(±208px) scale(.74) rotate(±13deg)`，`opacity .55`，`saturate(.75)`。
- 地面投影：240 × 22 的径向渐变椭圆，`blur(6px)`。
- 封面配色：`#9FB6D6→#5C7BA8` 等 5 组低饱和大理石色，均叠一层左上白色高光。

**标题区**（`padding: 34px 26px 0`）
- 「为你精选」：衬线 700 **30px / letter-spacing 1.5px**，`#101B28`。
- 两行小字：**12.5px / line-height 20px**，`#5B6879`，分隔点 `#C6CFDA`。

**迷你播放条**
- 位置：`left/right: 16px`，`bottom: 16px`，z=20。
- 尺寸：圆角 **28px**，`padding: 10px 12px 10px 10px`，`gap: 11px`；底色 `rgba(255,255,255,.62)`，描边 `1px rgba(255,255,255,.92)`，`blur(26px) saturate(180%)`，阴影 `0 14px 34px rgba(24,44,72,.16)`。
- 封面：**44px 圆形**，内描边 `1.5px rgba(255,255,255,.85)`。
- 歌名 13px/600 `#152131`，歌手 11px `#98A3B1`。
- 进度条：高 **2.5px**，轨道 `rgba(26,115,232,.16)`，进度 38% `#1A73E8`，圆角 2px。
- 播放按钮：**42px 圆**，`#1A73E8`，阴影 `0 8px 18px rgba(26,115,232,.38)`，白色三角 14×16。

---

## 2. `lyrics-stage.html` 歌词舞台（深色）

**背景**
- 主色深海军蓝：`#0B1026`（顶部 `#0C1230` → 底部 `#090E20`）。
- 叠加光斑：`rgba(38,58,118,.55)`、`rgba(20,84,168,.34)`，以及底部 `rgba(10,18,44,.9)`。

**浮动几何（纯 CSS 形状，无外部素材）**
- 大圆：300px，`border 1px rgba(140,175,255,.16)` + 径向光泽 `rgba(120,160,255,.10)`。
- 小圆：190px，`border rgba(255,255,255,.10)`。
- 三角：200px 高，`rgba(120,160,255,.05)`，`rotate(-14deg)`；小三角 100px，`rgba(255,255,255,.045)`，`rotate(18deg)`。
- 细线：1px，线性渐变 `rgba(160,190,255,.22)→透明`，分别 `rotate(-8deg)` / `rotate(6deg)`。
- 光点：5px / 3px，`rgba(160,190,255,.5)`，`box-shadow: 0 0 12px`。

**顶栏**：26px 边距，12px / letter-spacing 1.2px，`rgba(255,255,255,.42)`；右上 32px 描边圆按钮 `rgba(255,255,255,.16)`。

**歌词（逐字散落）**
- 字体：衬线。
- 主行字号 **37–48px**（当前字 **48px**），行距由绝对定位 `top` 控制；每字 `rotate(-8deg ~ +7deg)` + `translateY(-9px ~ +10px)`，字号差 ±8%。
- 普通字：`rgba(255,255,255,.42)`；上一句/下一句：`rgba(255,255,255,.20)`。
- **当前字**：`#FFFFFF`，`text-shadow` 四层发光
  `0 0 6px rgba(26,115,232,.95) / 0 0 18px rgba(26,115,232,.85) / 0 0 40px rgba(60,140,255,.65) / 0 0 70px rgba(26,115,232,.35)`。
- **外圈光环**：`::before` 120px 径向渐变 `rgba(70,150,255,.55)→透明`，`blur(10px)`；`::after` 86px 圆环 `1px rgba(120,180,255,.38)` + `box-shadow 0 0 26px rgba(26,115,232,.55)`。

**翻译行**：距底 150px，居中，12px / letter-spacing 1.6px；当前 `rgba(255,255,255,.78)`，署名 `rgba(255,255,255,.38)`。

**播放条**
- `left/right: 16px`，`bottom: 16px`，圆角 **30px**，`padding: 12px 14px 14px`。
- 底色 `rgba(18,26,54,.55)`，描边 `1px rgba(255,255,255,.12)`，`blur(26px) saturate(160%)`，阴影 `0 18px 44px rgba(0,0,0,.55)`。
- 封面 44px、圆角 14px（暖色 `#F1D3BE→#C8946E→#8E6244`）；歌名 13.5px 600 `#FFFFFF`，歌手 11px `rgba(255,255,255,.45)`。
- 播放按钮 **42px 圆** `#1A73E8`，阴影 `0 8px 20px rgba(26,115,232,.55)`。
- 进度：高 **3px**，轨道 `rgba(255,255,255,.16)`，进度 42% `#1A73E8` + `0 0 10px rgba(26,115,232,.7)`，拖拽点 8px 白色 + 蓝色外发光；时间 10px `rgba(255,255,255,.45)`，等宽数字。

---

## 3. `lyrics-flow.html` 歌词流（浅色）

**背景（封面主色取色模拟，假主色 `#C8946E`）**
- 5 层径向渐变：
  - `rgba(200,148,110,.95)`（左上）、`rgba(232,196,160,.92)`（右上）、`rgba(244,228,212,.95)`（中上）、`rgba(196,142,120,.62)`（左中）、`rgba(170,120,96,.42)`（右下）
  - 线性底：`#F3E4D6 → #EFE3D9 → #E9E0DA → #DFD7D2`
- 3 个 `blur(60px)` 色斑强化柔焦：`rgba(200,148,110,.55)` / `rgba(240,214,186,.75)` / `rgba(238,226,214,.85)`。
- 细网点叠加：`radial-gradient(rgba(120,88,64,.14) 1px, transparent 1px)`，`background-size: 5px 5px`，`multiply`，`opacity .5`。
- 真实实现里用 `Palette API` 取封面主导色 → 提亮/降饱和后生成同样的五层渐变即可。

**顶部**
- `padding: 22px 20px 0`，`gap: 12px`。
- 返回按钮 **36px 圆**：底色 `rgba(255,255,255,.42)`，描边 `1px rgba(255,255,255,.7)`，箭头 `#33415A` 2.2px 描边。
- 封面小图 **36px / 圆角 10px**：`#F1D3BE→#C8946E→#8E6244`，外描边 `rgba(255,255,255,.5)`。
- 歌名 13.5px / 650 `#1A2433`；副标题 11px `rgba(40,52,70,.55)`。

**歌词流（左对齐粗体）**
| 层级 | 字号 | 不透明度 | 模糊 | 颜色 |
|---|---|---|---|---|
| 前 3 行 | 20px | .10 | blur 3.4px | `#0F1A2B` |
| 前 2 行 | 24px | .20 | blur 2.4px | `#0F1A2B` |
| 前 1 行 | 28px | .36 | blur 1.4px | `#0F1A2B` |
| **当前行** | **36px** | **1.0** | **0** | **`#0F1A2B`（深蓝黑）** |
| 后 1 行 | 30px | .30 | blur 1.6px | `#243047` |
| 后 2 行 | 26px | .16 | blur 2.8px | `#2A3852` |
| 后 3 行 | 22px | .08 | blur 4.0px | `#2A3852` |

- 字重 **800**，行高 1.16（当前行 1.18），当前行上下 `margin 8px`；容器 `padding: 40px 28px 0`，垂直居中，行间 `gap 6px`。

**底部控制条**
- `margin: 0 16px 16px`，圆角 **30px**，`padding: 16px 20px 18px`。
- 深色底 `rgba(16,24,38,.82)`，描边 `1px rgba(255,255,255,.08)`，`blur(26px)`，阴影 `0 20px 44px rgba(40,30,22,.34)`。
- 进度：高 **3px**，轨道 `rgba(255,255,255,.15)`，进度 44% `#1A73E8`，8px 白色拖拽点；时间 10px。
- 按钮行：`margin-top: 16px`，`gap: 44px`；上一首/下一首图标 22px 白色 85%；播放按钮 **56px 圆** `#1A73E8`，阴影 `0 10px 24px rgba(26,115,232,.5)`。

---

## 4. Compose 实现要点

- `backdrop-filter: blur()` → `Modifier.blur()` 只作用于自身内容；毛玻璃效果用 `Brush.verticalGradient` + 半透明白/深色叠加近似，或直接 `Color(0x9FFFFFFF)`。
- 逐字散落：每字用 `Text` 放在 `FlowRow`/`Layout` 里，按索引套 `Modifier.rotate(r) .offset(y=dy)`，当前字额外画一个 `drawBehind { drawCircle(radialGradient) }` 作光环。
- 歌词流模糊分级：`Modifier.blur(radius).alpha(a)`，半径 0–4dp，alpha 按上表映射。
- 进度条统一 `2.5–3dp` 高 + 圆角 `RoundedCornerShape(50)`，蓝色 `#1A73E8`，轨道用主色 16% 透明。
- 所有蓝色只用于：选中胶囊、播放按钮、进度条、当前歌词光效 —— 不要扩散到背景或大块填充。
