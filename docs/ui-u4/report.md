# U4 · 全屏播放页 + 迷你条展开/收起

> 分支 `port/u2`（先 `git merge folia-ui --ff-only` 快进到 16acc8e，含 U3）。worktree `D:\workspaces\GuangYue-u2`，未触碰 `D:\workspaces\GuangYue`。
> 本段只做播放页与迷你条转场；歌词 U5 再做，本段只留入口。

## 1. 做了什么

1. **全屏播放页换成 Folia 形态**（`FoliaPlayerPage`）：封面取色渐变背景 → 顶部「收起 / 正在播放 / 歌词入口」→ 歌手（衬线斜体）+ 竖线 + 大标题 + 副标题（音质 · 专辑 · 队列位置）→ 底部圆角大卡片（大封面 + 标题 / 歌手 / 副标题 + 动作行「收藏 / 加入歌单 / 队列 / 歌词」+ 传输键「播放模式 / 上一首 / 播放暂停 / 下一首 / 队列」）→ 底部悬浮进度胶囊（左时间 · 2dp 轨 · 右时间 · 音质圆钮）。
2. **封面主色 → 背景渐变**：三级兜底 `封面主色 → 主题色 seed → 常量兜底色`；浅色三档提亮、深色三档压暗，并对种子色做饱和度收敛，避免生图取色时糊成一片。
3. **播放模式三态**：顺序播放 / 单曲循环 / 随机播放，点一次换一态，落到 `setShuffle` + `setRepeatMode`（新增 API，见 §2）。
4. **迷你条 ↔ 全屏展开/收起**：迷你条点击进入播放页，播放页按 `entryOrigin`（触点位置）做缩放+淡入 spring；点收起、按返回键、向下滑（跟手位移 + 超过 30% 屏高判定）三条路一致地先播收起动画再 `popBackStack`。
5. **接现有播放器与队列**：全部走 `PlaybackController`，播放/暂停/切歌/拖动进度/模式/收藏/选队列行都不新增状态机，只读 `PlaybackState` + `queueSnapshot()`。
6. **适配**：`windowInsetsPadding(statusBars ∪ displayCutout ∪ navigationBars)` 让状态栏与手势条都不压控件；所有可点目标 ≥48dp（播放键 64dp）；长标题 >20 字才两行、超出省略。

## 2. 文件

| 类型 | 路径 | 说明 |
|---|---|---|
| 新增 | `feature/player/src/main/java/com/guangyue/feature/player/screen/FoliaPlayerPage.kt` | 播放页 UI（约 930 行，含私有 `PlayerCard` / `CardAction` / `TransportButton` / `PlayerQueueCard` / `PlaybackPill`） |
| 新增 | `feature/player/.../screen/PlayerPageState.kt` | 纯逻辑：模式三态、渐变兜底、标题两行阈值、副标题拼装、队列位置、收藏态流转 |
| 新增 | `feature/player/.../screen/PlayerStrings.kt` | 文案资源 id 容器 + `LikeState` |
| 新增 | `feature/player/.../screen/FoliaMotion.kt` | feature 侧弹簧/曲线（app 模块的同名对象依赖不到，值保持一致） |
| 新增 | `feature/player/src/test/java/com/guangyue/feature/player/screen/PlayerPageStateTest.kt` | 17 个用例 |
| 新增 | `app/src/main/java/com/guangyue/music/nav/PlayerRoute.kt` | `IPlayerRoute` 接线：收藏态、队列行、字符串解析、返回键、收起动画 |
| 新增 | `app/src/test/java/com/guangyue/music/nav/U4PlayerScreenshotTest.kt` | 11 张 Roborazzi 截图用例 |
| 新增 | `docs/ui-u4/*.png`、`docs/ui-u4/make_side_by_side.py` | 截图、并排图、并排图生成脚本 |
| 修改 | `app/src/main/res/values/strings.xml` | 新增「U4 播放页」段 27 个键 |
| 修改 | `app/src/main/java/com/guangyue/music/nav/NavHost.kt` | `Routes.PLAYER` 改调 `IPlayerRoute` |
| 修改 | `app/src/main/java/com/guangyue/music/nav/Root.kt` | 透传 `TrackRepository`（收藏要用），`LocalTrackRepository` 注入 |
| 修改 | `app/src/main/java/com/guangyue/music/MainActivity.kt` | 注入 `TrackRepository` 传入 Root |
| 修改 | `core/player/src/main/java/com/guangyue/core/player/PlaybackController.kt` | 新增 `setRepeatMode(mode: Int)`（原来只有 `cycleRepeat`，三态直设需要） |
| 修改 | `feature/player/build.gradle.kts` | 加 `testOptions`（unitTests 开 AndroidResources/defaultValues）与 `testImplementation(libs.junit)` |
| 修改 | `app/build.gradle.kts` | `roborazzi.output.dir` → `docs/ui-u4` |
| 修改 | `app/src/test/java/com/guangyue/music/ui/design/FoliaGalleryScreenshotTest.kt` | 输出目录后缀固定为 `ui-u1`，避免它的特写图挤进 U4 目录（见 §7） |

## 3. Folia 原值 → 手机值

| 项 | Folia 原值（来源） | 手机值 | 依据 |
|---|---|---|---|
| 大封面圆角 | `rounded-[10px]`（SongHeader.tsx:43） | `20dp` | 手机封面边长约为面板的 2.4 倍，按比例放大；视觉与 baseline 手机图一致 |
| 封面内描边 | `inset 0 0 0 1px var(--st-line)` | 卡片 1dp 白/深描边 + 封面包容在卡片内 | baseline 手机图：封面四周是白卡留边 |
| 卡片圆角 | 面板外壳 `rounded-[20px]`（panelTheme.ts） | `20dp` | 直接沿用 |
| 标题字号 | `text-lg`（18px）`leading-[26px]`、600（SongCredits.tsx:93） | `18sp / 24sp` | 18sp 对齐；行高按手机阅读取 24sp |
| 顶栏大标题 | baseline 手机图为 24sp 级 | `24sp / 32sp`，>20 字两行 | baseline 实图 + 长标题验收要求 |
| 歌手 | `text-[13px] leading-5`、`--st-t2`（62%）（SongCredits.tsx:99） | `13sp`、`ink@62%`（`Gyp.t2`） | 令牌同值 |
| 进度轨 | `h-1.5`（6px）`rounded-full`（ProgressBar.tsx:199） | `2dp` | baseline 手机图是 2dp 细轨（U3 已确认的 m412 形态） |
| 播放主键 | `h-12 w-12`（48px），图标 20（FloatingPlayerControls.tsx:564） | `64dp`，图标 26 | 手机单手主操作放大；且满足 ≥48dp |
| 次键（上一首/下一首/模式/队列） | 桌面 44px | `48dp` | 触控目标下限 48dp |
| 动作键（收藏/加歌单） | `h-10 w-10 rounded-[10px]`（SongActions.tsx:32） | `48dp` 圆形 | 触控下限 + baseline 手机图是圆钮 |
| 面板玻璃 | `--pn-glass` 浅 rgba(244,244,245,.8) / 深 rgba(28,28,31,.8)，blur 40 | `Gyp.card`（#FFFFFF / #232327）不透明 | Compose 模糊在低端机代价高，取同色实底 |
| 面板阴影 | `0 30px 80px rgba(0,0,0,.55)` | `8dp` 阴影 + `hairline` 描边 | 手机一屏一卡，不需要 80px 深投影 |
| Tab dock | 36px 高 5 段（PanelShell.tsx:26） | **不做 Tab 条** | 见 §7：手机只保留一段播放页，不做 Cover/Lyrics/Controls/Playlist/Quality 五段 |
| 展开 spring | `{stiffness: 280, damping: 24}`（FloatingPlayerControls.tsx:36-40） | `spring(dampingRatio(24f,280f), 280f)` | 同参数，换算为 Compose `dampingRatio` |
| 底部条 spring | `{stiffness: 280, damping: 28}`（usePlayerBottomBarBottomPx.ts:46） | `spring(dampingRatio(28f,280f), 280f)` | 同参数 |
| 展开/收起时长曲线 | 开 0.3s unfold / 关 0.22s move，ease out `[0.22,1,0.36,1]`（kit/motion.ts） | `220ms / 300ms`，`CubicBezierEasing(0.22f,1f,0.36f,1f)` | 同值 |
| 下滑收起阈值 | Folia 无（桌面无手势） | 跟手位移，>30% 屏高或速度足够即收起 | 自定，见 §7 |
| 背景渐变 | `[primary, primary→透明, secondary→透明, secondary]`，overlay α0.4，模糊 40×1.5（FluidBackground.tsx） | `verticalGradient` 三段（主色 → 提亮/压暗 → 更深），不做模糊 | Compose 侧用可离屏缓存的线性渐变，避免模糊开销 |
| 无封面兜底 | `linear-gradient(to bottom right, primary, transparent, secondary)` | 主题色 `seed` → 常量橙 `#EA580C` 三级兜底 | 同思路，多一级常量兜底 |

## 4. 文案来源

全部取自 `D:\workspaces\_folia-src\src\i18n\locales\zh-CN.ts`，落到 `app/src/main/res/values/strings.xml` 的「U4 播放页」段。

| 键 | 文字 | 来源 |
|---|---|---|
| `player_now_playing` | 正在播放 | ui :876-1019「正在播放」 |
| `player_collapse` | 收起 | ui「收起」 |
| `player_previous` / `player_next` | 上一首 / 下一首 | ui「上一首」「下一首」 |
| `player_play` / `player_pause` | 播放 / 暂停 | player :3785-3793 |
| `player_shuffle` / `player_mode_shuffle` | 随机播放 | ui「随机」+ queue「打乱队列」合成 |
| `player_mode_sequential` | 顺序播放 | player 循环组（关闭循环 → 顺序语义） |
| `player_mode_loop_one` | 单曲循环 | player :3785-3793「单曲循环」 |
| `player_like` / `player_liked` | 喜欢 / 取消喜欢 | player「收藏」「取消收藏」 |
| `player_add_to_playlist` | 加入歌单 | localMusic.addToPlaylist :3903「添加到歌单」 |
| `player_queue` / `player_close_queue` | 播放队列 / 收起播放队列 | queue :4356-4361、ui「收起播放队列」 |
| `player_lyrics` / `player_lyrics_soon` | 歌词 / 歌词功能即将上线 | panel :1022-1026「歌词动画」+ 自拟补充句（U5 未做，明示占位） |
| `player_queue_count` | 播放队列 %1$d 首 | queue「播放队列」+ 自拟计数后缀 |
| `player_queue_empty` | 队列是空的 | queue「播放列表为空」 |
| `player_no_song` | 当前没有正在播放的歌曲 | ui「当前没有正在播放的歌曲」 |
| `player_unknown_artist` | 未知艺术家 | player「未知歌手」（改「艺术家」与数据层 `toCachedTrack()` 兜底一致） |
| `player_quality` | 音质 | panel「音质」 |
| `player_retry_hint` | 可以在设置里换一个音源再试 | 自拟（错误提示补充） |
| `player_seek_bar` | 播放进度 | 已有键 `progress_label` 同义，U4 独立成键便于读屏 |
| `player_cover_of` | %1$s 的封面 | 自拟（封面无障碍描述） |

## 5. 截图清单与复现

均在 `docs/ui-u4/`，Roborazzi（Robolectric 真渲染，`@GraphicsMode(NATIVE)`，360×800dp xhdpi）：

| 文件 | 内容 |
|---|---|
| `u4-playing-light.png` / `u4-playing-dark.png` | 播放中（浅/深） |
| `u4-paused-light.png` / `u4-paused-dark.png` | 暂停（浅/深） |
| `u4-long-title-light.png` / `u4-long-title-dark.png` | 长标题两行省略（浅/深） |
| `u4-no-cover-light.png` / `u4-no-cover-dark.png` | 无封面兜底（浅/深） |
| `u4-queue-light.png` | 队列抽屉展开 |
| `u4-320dp-light.png` | 320dp 窄屏（640×1600 = 320×800dp） |
| `u4-font130-light.png` | 字体 1.3 倍 |
| `u4-side-by-side-cover-light.png` / `-cover-dark.png` / `-queue-light.png` | 与 Folia 基准并排（中文标注，左基准右手机） |

复现：

```powershell
$env:JAVA_HOME='D:\tool\jdk'
$env:GUANGYUE_RECORD_SCREENSHOTS='true'
.\gradlew.bat :app:testDebugUnitTest --tests "*U4PlayerScreenshotTest*"
# 并排图（需 Pillow）
python docs\ui-u4\make_side_by_side.py
```

基准图来源：`D:\workspaces\GuangYue\docs\port\baseline\screenshots\player-panel-cover-m412-{light,dark}.png` 与 `...\baseline\screenshots-v2\m412-light-panel-queue.png`。

## 6. 适配自检

| 检查项 | 结果 |
|---|---|
| 浅色 / 深色 | 两套各 4 组（播放中 / 暂停 / 长标题 / 无封面）全部出图，渐变、卡片、文字对比度均正常 |
| 320dp | 动作行 4 个 48dp 圆钮 + 说明小字横向排布不溢出、不压成椭圆（`Arrangement.spacedBy`，未用 weight 压按钮） |
| 字体 1.3 倍 | 标题 / 歌手 / 副标题 / 时间不截断、不重叠 |
| 并排图 | 3 张（封面浅、封面深、队列浅），中文标注 |
| 无英文界面文字 | 截图内所有界面文字均为中文；曲名 / 歌手 / 专辑属数据，按验收允许 |
| 触控目标 ≥48dp | 收起 48、歌词入口 48、4 个动作键 48、模式/上一首/下一首/队列 48、播放暂停 64、进度胶囊高 56 |
| 长标题两行 | >20 字符才 `maxLines = 2`，超出 `TextOverflow.Ellipsis` |
| 功能不退化 | 播放/暂停、上一首/下一首、拖动进度（含拖动预览）、模式三态、收藏（写 `TrackRepository.setFavorite`）、队列点选、错误提示重试入口保留 |
| 不新造组件 | 只用 `GuangYueIcons` + FoliaKit 令牌（`Gyp`/`GyRadius`/`GySpace`/`GyText`/`FoliaMotion`），无第三方新组件 |
| 单测 | `testDebugUnitTest` 全仓 **271 / 0 fail / 2 skip**（U3 基线 232/0/2；新增 17 个播放页状态用例 + 21 个 app 侧用例增量），skip 2 仍是 `PluginHealthReportTest` / `PluginUsableReportTest` 因 `build/plugin-cache-*/*.jsonl` 不存在 Assume 跳过 |
| 构建 | `assembleDebug` 成功，`app-debug.apk` 25.5MB |

## 7. 没做完的 / 已知限制

1. **没有照搬 Folia 的 5 段 Tab 条（Cover / Lyrics / Controls / Playlist / Quality）。** Folia 桌面把播放页拆成右侧面板里的五个分段；手机一屏只能承载一段，本段实现为「封面段 + 队列抽屉」，其余分段留给 U5（歌词）与后续音质设置。并排图里这一行差异是刻意的。
2. **封面主色取色未接真实图片解码。** 本段 `coverColor` 由调用方传入；`IPlayerRoute` 目前传 `null`，走「主题色 → 常量橙」两级兜底，所以实机上背景是主题色渐变而非封面取色。取色（Palette）留到有真实封面加载链的段落一起做。
3. **下滑收起阈值自定**：Folia 是桌面应用、没有下滑手势，无原值可抄；本实现取「位移 > 30% 屏高」或「速度足够」，参数集中在一处便于后续调。
4. **`FoliaGalleryScreenshotTest` 输出目录改为固定 `ui-u1`。** `roborazzi.output.dir` 是全局系统属性，U4 把它指到 `docs/ui-u4` 后，U1 画廊的 8 张特写图被写进 U4 目录并因覆盖同名文件失败。已在该用例内把目录后缀固定为 `ui-u1`，属于本段必要的连带修正。
5. **未在真机/模拟器验证**（任务明确不用 adb/模拟器），交互手感（下滑跟手、spring 回弹）只有单元与截图层面证据，需要在实机走一遍。
6. 歌词入口当前只提示「歌词功能即将上线」，不做跳转（U5 接管）。

## 8. 提交

本地 commit，消息以 `U4:` 开头，只提交本段相关文件；不含 `run.log` / `.phase` / `build/` 产物 / 原始日志，也不含他人改动（`docs/ui-0.0.8/*`、`docs/ui-0.0.9/a/*`）。不 push、不 tag。
