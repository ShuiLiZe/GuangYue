# 0.0.8 M1d 补验收报告

- 工作区：`D:\workspaces\GuangYue`，分支 `folia-ui`，起点 HEAD `f787344`（M1c 报告之后的提交）
- 设备：`emulator-5554` / AVD `gy34`（720x1600，320dpi，2 核 2GB），Android 14
- 包：`com.guangyue.music` 0.0.8（versionCode 8），release 签名包
- 结论：**M1d 三项全部 PASS**，已打 tag `0.0.8`。

## 逐项结果

| # | 项目 | 结果 | 截图 / 证据 |
|---|------|------|-------------|
| 1 | 真实播放：完整版曲目（≥120s）播放，两张间隔 ≥20s、进度不同的截图 + `dumpsys media_session`(state=3) 与同刻进度 | **PASS** | `01-play-a.png`（0:40/4:19，23:03:37）、`02-play-b.png`（1:25/4:19，23:04:22，间隔 45s）、`play-proof.txt` |
| 2 | edge-to-edge：首页 / 搜索 / 我的 / 插件页 / 播放页五张，状态栏与导航栏不压内容 | **PASS** | `10-home.png`、`10-search.png`、`10-me.png`、`10-plugins.png`、`10-player.png` |
| 3 | 回归：插件页单选当前音源强杀重启仍保持 / 搜索跟随当前音源 / 首页无假数据 | **PASS** | `20-plugins-after-restart.png`、`20-search-follows-source.png`、`20-home-no-fake-data.png`（另有 `20-plugins-source.png` 作改动前对照） |
| 4 | `gradlew test` 总数 / 失败数 | PASS | **94 tests / 0 failures / 0 errors / 2 skipped**（17 个 suite，含 Gradle 汇总行） |
| 5 | 签名 release APK 路径与 SHA-256 | PASS | 见「构建产物」 |

## 第 1 项：真实播放

曲目：**星晴 / 周杰伦**，来源咪咕（搜索 `zhoujielun`，结果第 5 条 `[咪咕]`），播放页时长 **4:19 = 259 秒 ≥ 120 秒**，是完整曲（咪咕 128k 直链，非试听片段）。

- 帧 A `01-play-a.png`：2026-10-30 23:03:37 +08:00，界面 `0:40 / 4:19`，中间是暂停按钮（=正在播放）。
- 帧 B `02-play-b.png`：2026-10-30 23:04:22 +08:00，界面 `1:25 / 4:19`。两帧间隔 45 秒，进度不同。
- 同一时刻 `adb shell dumpsys media_session`（`play-proof.txt`，原始输出归档在 `.cli/m1d/ms-proof-a.txt`、`ms-proof-b.txt`）：

```
Media button session is com.guangyue.music/androidx.media3.session.id. (userId=0)
androidx.media3.session.id. com.guangyue.music/androidx.media3.session.id. (userId=0)
state=PlaybackState {state=PLAYING(3), position=39918, buffered position=131030, speed=1.0, ...}   ← A
state=PlaybackState {state=PLAYING(3), position=87337, buffered position=196571, speed=1.0, ...}   ← B
metadata: size=10, description=星晴, 周杰伦, 寂寞边境 月光爱人 情歌精选
```

`position` 39918ms → 87337ms（+47.4s），两次都是 `state=PLAYING(3)`。`dumpsys audio` 同刻 `AudioPlaybackConfiguration ... state:started ... content=CONTENT_TYPE_MUSIC`，audio focus `pack: com.guangyue.music gain: GAIN loss: none`。

## 第 2 项：edge-to-edge

五页均为 `<activity>` 全屏 + `enableEdgeToEdge()`，实测：

- 状态栏（顶部 ~104px）与内容区分离：首页标题「广东」、搜索标题「搜索」、我的标题「我的」、插件页「← 音源」、播放页「∨ 正在播放」都在状态栏下方，无文字被状态栏图标遮挡。
- 导航栏（底部手势条）与内容不重叠：底栏 tab 文字（首页/搜索/我的）位于手势条上方；列表页最后一行可用滚动让出安全区。
- 首屏列表与 mini 播放条都按 `navigationBarsPadding` 上提，无被系统条切掉的按钮。

## 第 3 项：回归

1. **单选持久化**：插件页当前音源 = 咪咕音乐（橙点单选，状态「可用」），header「正在用「咪咕音乐」搜索和取播放地址」。`adb shell am force-stop com.guangyue.music`（pid 清空）→ `am start` 新进程 pid 13796 → 我的 → 音源插件管理：仍是 `正在用「咪咕音乐」` + 咪咕橙点选中，其余全为空心圆点（单选成立）。截图：重启用 `20-plugins-after-restart.png`，切换前用 `20-plugins-source.png`。
2. **搜索跟随当前音源**：搜索页 header「音源 咪咕」+ chip 行咪咕高亮，结果 20 条全部标 `[咪咕]`（`20-search-follows-source.png`）。
3. **首页无假数据**：推荐歌单 3 张真实封面卡（「网络流行热歌，越听越…」「网络热歌 | 拨开迷雾抓…」等）+ 最近播放 3 张真实卡（含播放过的歌单封面），无占位矩形、无写死文案（`20-home-no-fake-data.png`）。

## 本次为达成验收所改的代码（3 个文件，均与本三项直接相关）

1. `core/player/src/main/java/com/guangyue/core/player/GuangYuePlaybackService.kt`
   - 新增 `override fun onStartCommand`（返回 `START_NOT_STICKY`）。
   - 新增 `companion object.start(context)`，用 `context.startService` 拉起服务。
   - **修复的真缺陷**：`GuangYuePlaybackService`（Media3 `MediaLibrarySession`）此前从未被任何代码 start/bind，全仓库 grep 只有注释命中。后果：能在放歌，但系统里**没有 media session** —— 通知栏/锁屏控制、系统媒体键全都没有，`dumpsys media_session` 也查不到 `state=PLAYING`。这是第 1 项此前拿不到 state=3 的根因。
   - 第一版用 `startForegroundService` 会让服务在会话连上之前就被系统记成前台无通知，触发 `ForegroundServiceDidNotStartInTimeException` 直接崩进程（logcat 已实测），故改为 `startService`，由 media3 在会话建立后自己走 `startForeground`。
2. `core/player/src/main/java/com/guangyue/core/player/PlayerModule.kt`
   - `PluginMediaSourceResolver` 构造注入 `@ApplicationContext app: Context`（供上面 `start()` 使用）。
   - 插件取链成功分支里先 `GuangYuePlaybackService.start(app)` 再返回播放地址，保证一开播就有会话。
   - （M1c 后未提交的部分）插件 `getMediaSource` 加了 8 秒预算 `PLUGIN_RESOLVE_BUDGET_MS`，超时落洛雪取链源，避免弱网下点播放干转 40 秒。
3. `docs/ui-0.0.8/lx/five-source-lx.v3.js`（同副本 `docs/ui-0.0.8/m1c/five-source-lx.v3.js`）
   - `lxUtils` 兼容 shim：`const lxUtils = (globalThis.lx && globalThis.lx.utils) || globalThis.lxUtils || {}`，修 `'lxUtils' is not defined`。
   - `kgUrl` 先剥离第三方插件 hash 的来源前缀（`kugou_<hash>` / `kg_<hash>`）再校验 32 位十六进制，修 `kugou: bad hash`。

## 构建产物

- 命令：`.\gradlew.bat test assembleRelease --console=plain` → `BUILD SUCCESSFUL in 8m 14s`；单测另用 `test --rerun-tasks` 全量重跑，`BUILD SUCCESSFUL in 6m 14s`。
- 单测：`TOTAL tests=94 skipped=2 failures=0 errors=0`（17 个 testsuite，含 Gradle 汇总行；实际断言 92 条，另 2 个 suite 内各 1 个 up-to-date 副本行）。
- APK：`app/build/outputs/apk/release/app-release.apk`，**8737645 B**，SHA-256 `2F60807DDED4993A1822A518FAA0CB850B485BDC9B68D5FDDB3C34298BA1940C`。
- 该 APK 已 `adb install -r` 到 emulator-5554，并在此包上完成上述全部截图取证。

## 遗留 / 备注

- 插件体检层：酷狗/酷我/QQ/汽水/网易云当前都报「能搜到歌，但取不到链」，番茄畅听「搜索失败」，只有咪咕与 B站「可用」。插件侧取链缺陷属 0.0.9 及以后事项，本任务不处理；四项五源矩阵单测（`LxFiveSourceMatrixTest`）仍显示 kw/kg/wy/mg(128k) 四源可出链。
- 播放器「上一首/下一首/切歌」在缺 session 时曾出现界面与音频状态不同步，补上 session 后已消失，但未做长时间压测。
- `docs/port/` 未 `git add`。
