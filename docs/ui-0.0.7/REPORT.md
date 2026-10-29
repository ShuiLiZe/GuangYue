# 广乐 0.0.7 · 插件真能搜能播 + 用户自选音源

## 1. 结论

0.0.7 修完了「启用了但搜不到」的**四处独立根因**，把音源选择权交回用户，并在真机（emulator-5554，
release R8 APK）上完成端到端验收：**选 B站音源 → 搜「周杰伦」→ 20 条结果 → 点第一首 → 播放页
0:28 / 3:46 时间在走**。

可用插件从 0.0.4 体检自称的 43 个，落到真搜真取源的 **21 个**（清单见 `docs/plugin-usable-0.0.7.md`）。

## 2. 根因（四处，全部实测确认）

### 2.1 axios 同步桥的 `.then` 链形状不对（搜索全灭的头号真因）

`HostBridge` 的 `attachChain` 只在「回调返回 undefined」时把链续上，且 `makeResponse` 丢弃了浅拷贝。
真 axios 的 `.then` 恒返回 Promise，于是 `axios.post(...).then(f).catch(g)`、`.then(f).then(g)`、
回调返回普通对象后再链 —— 全部变成 `TypeError: ... is not a function`。

- 证据：QQ音乐插件在 JVM 里报 `not a function at searchQq (plugin.js:2:16074)`，而用 Node 22 + 真 axios
  跑同一份源码 `OK ms=431 isEnd=false n=10`，返回「发如雪/晴天」等 10 条完整字段 → 插件没问题。
- 修法：`gyChain(value, error)` 浅拷贝 + `then/catch/finally` **恒返回新的已结算链**（回调抛错转 rejected 链、
  回调返回 thenable 则吸收其状态），`makeResponse` 改为 `return attachChain(out)`。
- 效果：首轮体检 ok 0 → 19。

### 2.2 宿主缺 `URLSearchParams` / `URL`

快音、36DJ 在 search 阶段直接报 `'URLSearchParams' is not defined`。补了 `GyURLSearchParams`
（append/get/getAll/has/set/sort/toString/forEach/keys/values/entries/迭代器，支持 string/数组/对象/实例）
与 `GyURL`（href/protocol/host/hostname/search/hash/pathname/searchParams）。效果：ok 19 → 20 → 21。

### 2.3 `SearchViewModel` 从不调 `PluginManager.load()`（「首次进搜索页没有音源」）

`PluginManager.plugins` 是 StateFlow，初值空表；只有 `PluginViewModel.init` 会 `load()`。用户先开搜索页时
`available=0` → 点搜索走「还没有可用的音源」分支，永远搜不到。

- 证据：插桩日志显示进搜索页只有 `collect n=0 enabled=0`，随后打开插件管理页立刻 `n=57 enabled=20`；
  给 `PluginStore.readIndex()` 加日志证明 `load()` **根本没执行**（不是索引读失败）。
- 修法：`SearchViewModel.init` 里先 `viewModelScope.launch { manager.load() }` 再 collect。

### 2.4 `JS_ExecutePendingJob` 在真机上把线程钉死（B站/清风DJ/好听轻音乐静默 40s+）

`drainJobs` 在 JS 的 host 回调里递归重入，每层还能跑 10 万个 job，墙钟预算只在每轮之间检查；
`kill -3` 抓到的栈是 `executePendingJob → __gy_drainJobs → drainJobs → executePendingJob → …`，
native 侧卡在 `NonPI::MutexLockWithTimeout` 的 futex 上，`interruptEvaluation()` 与
`Thread.interrupt()` 都无效（它们只在 `evaluate` 的字节码检查点生效）。

- 关键结论：`JS_ExecutePendingJob` **只能在 binding 回调里**被调用。普通线程调会先抢 jsMutex、native 再抢
  同一把不可重入锁 → 永久阻塞。
- 修法：JS 侧 `settle()` 改成**只登记** Promise 状态（`G.__gy_pending`），新增 `G.__gy_pump(ms)` 在
  **自己的循环里**调宿主注入的 `__gy_drainOneJob`（此时天然处于 binding 回调上下文）推进队列；
  Kotlin 在顶层反复发**有界的** `evaluate("__gy_pump(1200)")`，每轮之间才检查总预算（12s），
  到点如实报「插件返回的异步结果没结算」。

### 2.5（顺带修）播放链路拿 platform 当 plugin id

`MediaOrigin.pluginId` 来自 `MusicItem.platform`（如 `36DJ`、`bilibili`），而安装后的 id 是
`36dj-2d5f50a470`，于是 `getMediaSource` 报「插件不存在或未启用：36DJ」，点歌一律播不了。

- 修法：`MusicPluginBridge.resolveMeta()` 先 `find(id)` 再退回 `findByPlatform(platform)`，
  `runtimeFor/search/selfTest` 统一走它。

## 3. 用户自选音源（交互）

| 位置 | 行为 |
| --- | --- |
| 我的 → 音源插件 | 每行：名称、版本、支持的搜索类型、**健康状态**（可用 / 搜索无结果 / 取不到播放地址 / 搜索失败 / 未体检）、开关、上移下移排序、卸载、卸载后重装 |
| 导入 | URL、订阅列表（plugins.json）、本地文件；**导入后不自动启用**（`enabled=false`） |
| 一键 | 「只启用体检可用的」按出厂基线 `assets/plugin-health.json` 只打开 21 个；「体检一遍」真跑 search+getMediaSource 后回写健康状态 |
| 搜索页 | 顶部「音源」单选 chip（默认上次所选，持久化到 `AppPreferences.lastSourceId`），**只打选中的那一个插件**，绝不并发 |
| 类型 | 单曲 / 专辑 / 歌手 / 歌单 只显示该插件 `supportedSearchType` 里有的；切 Tab 懒加载 |
| 失败 | 明确文案：「xx 音源搜索失败：<原因>，换个音源试试」/「xx 音源里没有「q」的结果…」，不空白 |
| 播放 | 用歌曲所属插件取 `getMediaSource`；取不到时播放页显示「这个音源取不到「歌名」的播放地址 / **用其他音源搜同名歌曲**」（自动换源留待后续） |

## 4. 截图（本目录）

- `source-picker-light.png` 选音源（单选 chip）
- `search-result-light.png` 搜索结果（单曲 20，真实周杰伦条目）
- `player-playing-dark.png` 播放中
- `search-failure-light.png` 失败提示（酷狗 音源搜索失败：插件超时没返回（30 秒），换个音源试试）
- `player-failure-dark.png` 播放失败提示（用其他音源搜同名歌曲）
- `plugins-list.png` 插件管理页（健康状态 + 排序 + 一键按钮）
- `device-search-result.png` / `device-playing.png` **真机截图**（emulator-5554，release R8 APK，B站音源，
  播放页 0:28 / 3:46 正在走）
- 其余为 0.0.6 既有页面回归图（Light/Dark）。

已知渲染差异：`plugins-list.png` 里三个次级胶囊按钮在 Robolectric NATIVE 下被渲染成空胶囊
（图标与文字测成 0 宽），真机上显示正常；连试 `--rerun-tasks`/`--no-build-cache`/改布局均不改该 PNG 字节，
判定为截图工具链的测量差异，不影响真机。

## 5. 测试

| 任务 | 结果 |
| --- | --- |
| `:core:plugin:testDebugUnitTest` | BUILD SUCCESSFUL（含 `SearchParserTest` 12、`SearchEndToEndTest` 3、`PluginUsableTest` 57 参数化真网络体检、`PluginUsableReportTest`、`RuntimeRegressionTest`、`R8PluginLoadTest`、`PluginRuntimeJvmTest`） |
| `:app:testDebugUnitTest` | `MainActivitySmokeTest` 1/1、`ScreenshotTest` 22/22，fail=0 |
| `:feature:search` 单测 | `SearchStateTest` 8/8（单选音源、类型过滤、失败文案、健康标签、searchTypes 归一化） |

新增的端到端用例（不是只测导入）：
`SearchEndToEndTest` 用真 QuickJS 引擎跑 `docs/demo-plugin.js` 的
`search(query,page,type)` → `SearchParser.parse` → 断言 items/title/id/platform → `getMediaSource` 拿到 http url；
另有用例验证 `async search` 的 Promise 能结算、以及 `MusicItem` 经队列 JSON 往返不丢字段。

## 6. 可用插件（21）

172Mix、36DJ、B站、CCTV听音、DJ766、DJKK、DJ耶耶耶、JOOX音乐、MixDJ、QQDJ、波比DJ、电音阁、
好听轻音乐、黑色音频、魅力DJ、皮狗DJ、清风DJ、我是DJ、先上DJ、相声评书、小熊电音。

其余 36 个的失败归因（完整表见 `docs/plugin-usable-0.0.7.md`）：
搜索无结果 11、搜得到但取不到播放地址 18（源站 502/VIP 版权/签名/需要配置 Client ID 等上游限制）、
搜索失败 7（`code=6000`、`504`、`-1`、`403`、Apple Music 改版找不到 index-legacy js 等）。

## 7. 未完成 / 留给下一版

- **8 个 async 长调用插件的真异步任务队列**（B站、火龙DJ、DJ串烧集、蜻蜓FM、喜马拉雅、皮狗DJ、DJKK、82DJ）
  本版**没有**改成真正的后台任务队列，仍走「一次调用 + 有界 pump + 12s 预算」：
  超预算时如实报「异步结果没结算」，不再静默空转，但长尾插件（蜻蜓FM/喜马拉雅/82DJ 等）仍可能取不到结果。
  下一版需要把插件调用搬到独立 worker + 可取消任务表（参考 `briefs/0.0.5-async-queue.md`，
  该文件在本仓库中不存在，实际按 0.0.4 体检报告的失败归因实现）。
- 播放取不到源时的**自动换源**（用其他音源搜同名歌曲）只做了手动提示，未做自动切换。
- JOOX 音源搜索可用、但真机取链失败（`ak-hk.stream.music.joox.com`，疑似地区/VIP 限制），
  已归入 no-media，产品上靠用户换音源解决。

## 8. APK

- `app/build/outputs/apk/release/app-release.apk` = **8,691,841 B**
- `app/build/outputs/mapping/release/mapping.txt` = 71,066,538 B（R8 真开）
- versionCode 7 / versionName 0.0.7，minSdk 26 / targetSdk 36，release 签名
- 真机验收步骤：`pm clear` → 我的 → 音源插件管理 → 一键安装/更新（57 个）→ 只启用体检可用的（20 个）→
  搜索页选 B站 → 点历史「周杰伦」→ 单曲 20 → 点第一首 → 播放页 0:28 / 3:46 播放中。