# NOTICE ｜ 广乐 GuangYue 版权与致谢

广乐 GuangYue
Copyright (C) 2026 GuangYue contributors

本程序是自由软件，依据 **GNU Affero General Public License v3.0**（见仓库根目录 `LICENSE`）发布。
你可以转发、修改本程序，但必须保持同样的许可与版权声明；若通过网络向用户提供本程序的
功能，必须向这些用户提供对应的完整源码。

---

## 界面设计来源：Folia

广乐的界面层（布局层级、配色、字号字距、圆角与间距、图标与交互动效）逐项改写自
**Folia** 的界面实现。原项目以 AGPL-3.0 授权，与本项目许可一致。

- 项目名：Folia
- 原仓库：https://github.com/folia-music/folia
- 版权人：Folia 项目作者及贡献者
- 许可：GNU Affero General Public License v3.0

改写方式：把 Folia 的 React + CSS 组件（`web/.work/folia/src` 下的 `styles/`、`components/`、
`library/suites/*`）逐一翻译为 Jetpack Compose 组件，保留其视觉规格与交互逻辑；未复制其业务
代码、插件运行时或文案。0.0.6 起对照的具体文件与数值（`baseThemes.ts`、`kitTheme.ts`、
`kit.css`、`motion.ts`、`kit/*.tsx`、`Segments.tsx`、`LatticeChrome.css`）在
`docs/ui-0.0.6/REPORT.md` 里逐页列出。

## 随包资源

| 资源 | 用途 | 来源 | 许可 |
| --- | --- | --- | --- |
| Lucide 图标（169 枚，转成 `VectorDrawable`/`ImageVector`） | 全应用图标 | https://lucide.dev （Folia 亦用此图标集） | ISC，见 `feature/library/src/main/res/raw/license_lucide.txt` |
| Noto Serif SC 子集 400/500（`noto_serif_sc_subset_400/500.ttf`） | 「广乐」「我的」等衬线标题 | Google Noto Fonts / SIL | SIL Open Font License 1.1，见 `feature/library/src/main/res/raw/license_noto_serif_sc.txt` |

字体子集只含本应用实际用到的字形（约 0.94 MB，两档合计），未整体打包原字体。

---

## 第三方组件

| 组件 | 用途 | 许可 |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Material3 | 界面 | Apache-2.0 |
| AndroidX Media3 (ExoPlayer) | 播放 | Apache-2.0 |
| Hilt / Dagger | 依赖注入 | Apache-2.0 |
| Room | 本地数据库 | Apache-2.0 |
| Coil | 图片加载 | Apache-2.0 |
| OkHttp | 网络 | Apache-2.0 |
| kotlinx.serialization / coroutines | 序列化与并发 | Apache-2.0 |
| QuickJS-Android | 插件运行时 | MIT |
| jaudiotagger | 音频标签 | LGPL-2.1 |

App 内「设置 → 开源许可」页列出了同一份清单。
