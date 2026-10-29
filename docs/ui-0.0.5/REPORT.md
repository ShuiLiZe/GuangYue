# 广乐 0.0.5 · 原生 Compose 界面截图与设计对照

## 一、本轮做了什么

0.0.4 的真机黑屏根因是 `MainActivity` 只挂了一个 WebView，Folia 网页界面没有渲染出来。0.0.5 把整条 WebView 路线拆掉，界面改成原生 Jetpack Compose + Material3：

- `MainActivity` 变成纯 Compose 宿主（`enableEdgeToEdge()` + `setContent`），`FoliaWebHost` / `NativeCallBridge` / `MediaRelay` 全部不再参与构建。
- 删掉 `app/src/main/assets/folia`（20.9 MB 的 Folia 网页构建产物）与 `foliaWeb` Gradle 任务，`core:web` 从 app 依赖里移除。
- 新增应用外壳 `GuangYueRoot`：底部四项导航（首页 / 搜索 / 插件 / 设置），播放页、歌词页、歌词舞台、下载页为全屏路由（自动隐藏底栏）。
- 新增设置页（深色模式开关 + 版本号）与开源许可页（广乐 AGPL-3.0 / Folia 归属 / 第三方组件 / AGPL 摘要）。
- 全屏播放页补上歌词区占位（一条「下一句」预览条，点击进歌词页）。
- 仓库根目录加 AGPL-3.0 `LICENSE` 与 `THIRD_PARTY.md`。

## 二、截图清单与位置

全部截图在 `docs/ui-0.0.5/`，由 Roborazzi 在 JVM 上真实渲染 Compose（Robolectric `GraphicsMode.NATIVE`，411×891dp @xxhdpi，输出 1233×2673）：

| 文件 | 页面 |
| --- | --- |
| `home-light.png` | 首页（浅色）：顶栏 + 分段 Tab + 空状态 + 迷你播放条 + 底部导航 |
| `home-dark.png` | 首页（深色） |
| `home-no-bar.png` | 首页（不含底部导航，验证外壳与页面互不依赖） |
| `search-empty.png` | 搜索页空状态（未装插件） |
| `player-idle.png` | 全屏播放页（深色，无歌曲） |
| `plugins-empty.png` | 插件管理页（未装插件） |
| `settings.png` | 设置页 |
| `licenses.png` | 开源许可页 |

同目录下另有 `light-*.png` / `dark-*.png`（home / player / lyricflow / lyricstage / plugins），是 0.0.4 之前 `design/shots/` 里的历史设计稿，留着作对照基准。

复现命令：

```
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat --offline :app:testDebugUnitTest --tests 'com.guangyue.music.ui.ScreenshotTest'
```

> 注意：Roborazzi 的 `roborazzi.output.dir` 系统属性在当前版本不生效，图片实际落盘在 `<module>/docs/ui-0.0.5/`，需要手动复制到仓库根 `docs/ui-0.0.5/`。

## 三、与设计图的主要差异

设计参照是 `design/README.md` 里的 token 表 + `design/shots/*`（仓库里没有 `design/ui_folia_style_v2.png` 这个文件，实际以 shots 与 README 数值为准）。按可见程度排序：

1. **首页首屏没有 Coverflow 卡片流**：0.0.4 的首页有斜置拍立得卡片（150×196 白卡，scale 1.25/0.9/0.74/0.62，rotation ±7°/±13°）作为主视觉。现在库里没有任何歌单，空状态直接铺满中段，卡片流的视觉冲击暂时看不到。数据一进来就会恢复（`Coverflow` 代码仍在 `HomeScreen.kt` 里）。
2. **底部导航栏是新增的**，设计稿里没有：设计稿的导航靠页面内分段 Tab + 各处入口，0.0.5 按 brief 要求加了四等分底部栏（44×26 圆角胶囊高亮 + 11sp 标签）。这是有意的产品改动，不是复刻偏差。
3. **背景是大理石渐变而非纯色**：设计稿首页接近纸白 #F7F9FC 纯底。当前用 `MarbleBackground`（对角渐变 + 径向柔光 + 细脉络），浅色下偏冷偏亮，深色下是深海军蓝。观感更「厚」，但和设计稿的极简白有差距。成本考虑：柔光是 5 次径向渐变绘制，比实时模糊便宜得多，低端机不用降级。若要更贴设计稿，把 `MarbleBackground` 的透明度调低即可，一行改动。
4. **搜索胶囊文案与宽度**：设计稿是「搜索歌曲 / 专辑 / 歌单」，实机 168dp 宽下会换行溢出，改成「搜索歌曲、歌手」并单行截断。
5. **播放页歌词区是条状占位，不是居中滚动的歌词**：brief 要求「歌词区占位」，真正的逐行高亮滚动在歌词页（`LyricFlowScreen` / `LyricStageScreen`）。占位条点击可直接进歌词页。
6. **字体**：设计稿指定衬线体（`Noto Serif SC` 一类）。APK 没有内置衬线中文字体，只能用系统 `FontFamily.Serif`，在部分 ROM 上会回退成黑体，「广乐」两个字的衬线细节与设计稿不同。内置字体要额外 3–5 MB，本轮没做。
7. **图标用 Material Icons 而非 Folia 原版图标**：底栏、返回、歌词、检查更新等都是 `Icons.Filled.*` / `Icons.Outlined.*`。Folia 的图标资源没有转成 VectorDrawable。
8. **插件页「全选」开关与官方音源卡**：设计稿的插件页是列表 + 单项开关，0.0.5 在顶部多了一张官方音源卡（一键安装 / 更新 / 检查更新）和一个全局「全选」开关，属于功能扩展。
9. **没有模糊（blur）效果**：设计稿部分区域有毛玻璃。Compose 的 `Modifier.blur` 在 Android 12 以下不走 RenderEffect，成本高且会掉帧，这里统一用半透明卡片 + 渐变替代，视觉接近、开销低。
10. **空状态文案与设计稿不同**：写的是真实引导语（「从插件搜索一首歌，收藏成自己的歌单」），不是占位假数据。

## 四、性能相关的实现取舍

- 列表全部走 `LazyColumn` / `LazyRow`，`items(key = ...)` 用稳定的业务 key（歌曲 key / 插件 id / 卡片 id），不做无 key 的 `items {}`。
- 图片统一走 Coil，`CoverImage` 按目标尺寸解码（给 `ImageRequest` 的 `size` 绑定到 Composable 的实测尺寸），不整图解码。
- 背景的渐变与柔光在 `drawBehind` 里画，不进 Compose 的重组路径；不调用 `Modifier.blur`。
- 深色/浅色通过 `Gyp` 语义色（`@Composable @ReadOnlyComposable`）按上下文取色，避免在 Composable 里做字符串/颜色计算。
- Compose 编译器强跳过（StrongSkipping）在 Kotlin 2.4 + Compose 编译器 1.12 下默认开启，无需额外 flag（实测加 flag 只会得到「已默认开启」告警）。
- release 开 R8（`isMinifyEnabled` + `isShrinkResources`）与 optimizer 规则，`compileReleaseArtProfile` 会合并 Compose 自带的 baseline profile。

## 五、验证结果

- `:core:plugin:test`：75 个用例，0 失败（`--rerun-tasks` 强制重跑确认）。
- `:app:testDebugUnitTest`：通过。含 `MainActivitySmokeTest`（Robolectric 启动 MainActivity，断言 decorView 非空、`R.id.content` 下有子视图、measure+layout 后尺寸 > 0，即首屏不是空白）与 `ScreenshotTest`（8 张截图）。
- release APK：`app/build/outputs/apk/release/app-release.apk`，8,194,554 字节（7.81 MB），对比 0.0.4 的 17.6 MB 减少约 56%。
