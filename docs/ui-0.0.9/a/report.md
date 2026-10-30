# 广乐 0.0.9 a 段报告

## 5 插件离机取证（Lead 汇总用）

取证方式：JVM 单测真联网（QuickJS + OkHttp，真插件源码），双通道取证 ——
`core/plugin/src/test/java/com/guangyue/core/plugin/runtime/FiveSourceMediaProbeTest.kt`（走 0.0.8 基线同款 `search → getMediaSource` 两步真人路径，产出 5 音质逐条异常原文）
+ `FiveSourceHttpTapTest.kt`（把 PluginRuntime 的 OkHttpClient 换成插桩版，逐请求记录 方法/状态码/字节数/响应头/前 120 字节/请求头）。
两条通道结论一致，且对每个非 2xx 端点用 `curl.exe` 独立复验（离机证据）。

基线（0.0.8）报错与本次复现逐字一致，说明不是 0.0.9 引入的回归。

### 5 行矩阵

| 插件 | 版本 | 取链结果 | 判定类别 | 需要什么才能修 |
|---|---|---|---|---|
| 酷狗音乐 kugou | 1.9.24 | 失败（standard/high/320k/flac/low 全败）；错误 `酷狗取链失败：未取得播放链接` | **(d) 为主 + (b) 次**，非 (a) | 插件上游更新：官方 `m.kugou.com/app/i/getSongInfo.php?cmd=playInfo` 200 但 `status:0 / bitRate:0 / fileSize:0` 且**无播放地址**（源站改版）；自建聚合适配器多数已死（`103.79.184.97` 500「酷狗所有音源均获取失败」、`88.lxmusic…` 403「脚本完整性验证失败」、`musicserver.haitangw.cc` 200+`APP_UPGRADE_REQUIRED`）。但同一轮它已从 `fsandroid.tx.kugou.com` 拿到 **206 真音频**（ID3 头 / `fLaC` 魔数，Range bytes=0-63）却仍抛「未取得播放链接」→ 取链验收逻辑自身失效。App 侧无桥能力缺口可修 |
| 酷我音乐 kuwo | 1.9.24 | 失败（flac 另报 `hyw kw flac24bit not fLaC: empty`） | **(d) 为主 + (b) 次**，非 (a) | 官方 `nmobi/mobi.kuwo.cn/mobi.s?type=convert_url3&…` → **404**（该端点已下线，curl 复验 0.19s/404）；`kw-er.kuwo.cn` 音频回收到 **410**；`music.cute521.cn` 代理 403。而 `nmobi.kuwo.cn/mobi.s?f=web&type=convert_url_with_sign` → 200 并给出 `car-lw/car-er.kuwo.cn` 真音频 **206**，插件仍整体判失败 → 需插件上游更新聚合与候选验收（对齐 convert_url 端点形态） |
| QQ音乐 qq | 1.9.26 | 失败（5 音质全败） | **(f) 环境抖动/（b）次之**，非 (a) | 唯一非 2xx 只有自建中转 `tang.api.s01s.cn/music_open_api.php` **连接超时 ×2**（curl 直连 200/1.46s ⇒ 主机活着，是插件自身 `SOURCE_TIMEOUT=4500ms` 内没等到）；官方 `u.y.qq.com`、`ws.stream.qqmusic.qq.com` 均 200 但未产出可播 url。需要：中转重试/容错 + 换可用中转 |
| 汽水音乐 qishui | 1.9.23 | **standard 失败，high/320k/flac 成功**（换源到酷我，真 206 音频） | **(c) 签名契约变更**，非 (a) | 自签名端点 `POST api.vsaa.cn/api/music/soda/sign-pc.php` → 400；curl 复验 422 `Failed to deserialize the JSON body into the target type: missing field \`instance\`` ⇒ 端点活着但**契约改了**；备份 `211.161.236.45` TLS 坏（`SEC_E_INVALID_TOKEN`）。需要：插件跟随源站签名契约更新（或直接砍掉该端点走已可用的换源链） |
| 网易云音乐 netease | 1.9.27 | 失败（重复调用出现 `（负缓存 60s）`） | **(d) 插件/源站失效**，非 (a) | 5 个自建适配器全灭：`api.cenguigui.cn` **521**（两侧都死）、`yinyue.haitangw.net/wy/wy.php` **404**、`api.cxzja.cn` 与 `music-api.gdstudio.xyz` 直连 4–5s **超过插件自身 4.5s 预算**被自己丢弃；官方 `interface3.music.163.com` 200 但无可用 url。需要：插件上游更新/换源 |

**类别 (a)「App 网络桥缺接口/缺能力」被证据排除**：5 源每一轮请求都真实到达源站（大量 200/206），桥的 `__gy_http` 甚至成功回传了 206 音频字节；全程无 `无法加载模块`、无 `not a function`、无「宿主桥未就绪」。同源共性问题不在桥的 HTTP 能力上。

### 候选（未证实，不要当结论用）

桥的 **重定向语义** 与插件期望不符：5 个插件都传 `maxRedirects:0` 并（qishui changelog 明示）依赖 `responseURL` 跟随检测，而 `HostBridge.kt`/`assets/prelude.js` 里 `responseURL`/`finalUrl`/`maxRedirects` 出现次数均为 **0**，重定向交给 OkHttp 的 `followRedirects` 处理，插件只看得到最终 status。本次 5 个失败里没有任何一条错误文本指向重定向，故仅登记为候选，未修改桥。

### 未触碰的禁改文件 / 需要协调的一处

- 结论**不需要**改 `PluginManager.kt` / `PluginHealthChecker.kt` / `PluginMetaCodec.kt` / `SearchViewModel.kt` / `SearchScreen.kt` / `PluginManagerScreen.kt` / `HomeViewModel.kt` 中的任何一处；本段 5 个失败均由插件自身或源站造成。
- 若后续要把「插件取链失败」的错误原文透传到 UI（0.0.8 只有 `聚合取链失败：所有可用源均未取得播放链接` 这类聚合文案，没有 `detailText` 栈），需要在上述 UI/VM 文件里改动 —— 属他人写范围，未动，交 Lead 协调。

### 证据文件（全部 UTF-8，绝对路径）

- 结构化矩阵：`D:\workspaces\GuangYue\docs\ui-0.0.9\a\five-source-evidence.jsonl`
- 插件异常原文（含 detailText 栈）：`docs\ui-0.0.9\a\five-source-raw\{kugou,kuwo,qq,qishui,netease}.log`
- 逐请求 HTTP 取证：`docs\ui-0.0.9\a\five-source-raw\tap-{kugou,kuwo,qq,qishui,netease}.log`
- 新增测试类：`core\plugin\src\test\java\com\guangyue\core\plugin\runtime\FiveSourceMediaProbeTest.kt`、`FiveSourceHttpTapTest.kt`（均无断言、失败只作数据、QuickJS natives 缺失时 assumeTrue 跳过，不会让 `:core:plugin:testDebugUnitTest` 失败）

### before / after

before = 0.0.8 基线原文（5 源搜得到取不到）；after = 本次同路径复现，错误逐字相同（kugou/kuwo/qq/netease 同文案，qishui 同 `qishui sign 全部端点失败`）。
代码层「可改的」修复：**无** —— 5 源失败原因均在插件/源站侧，App 侧改动无法让它变成可用；故本段未改任何生产代码（`PluginRuntime.kt` / `prelude.js` / `jsprelude/**` 均未修改），只落证据与新增取证测试。
## 第 1 件：番茄畅听失败提示中文化 + 重试

**改了什么**

| 文件 | 改动 |
|---|---|
| `core/plugin/src/main/java/com/guangyue/core/plugin/PluginFailureText.kt`（新增） | 唯一收口点：`reason(raw)` 剥掉 `com.guangyue…Exception:` 类名、`at xx (plugin.js:…)` 栈帧、前导英文噪声，按关键字映射成中文原因（`搜索失败 code=6000` → 「源站返回错误码 6000」），兜底「源站没响应，稍后重试」。硬底线 `isUiSafe`：输出永不含 `com.guangyue`、永不含 `" at "` |
| `core/plugin/.../PluginHealthChecker.kt` | `short()` 改为 `PluginFailureText.reason(raw)` 薄壳，删掉原来 `text.length>60 → take(60)` 这条漏原始异常的口子 |
| `core/plugin/.../PluginManager.kt` | `shortReason()` 的 `else -> first().take(120)` 改为 `PluginFailureText.reason(message)`；`超时` 分支改为中文句 |
| `feature/search/.../SearchViewModel.kt` | 失败条改 `PluginFailureText.searchFailed(label, error)`；删除本地 `shortReason()`（`else -> line.take(60)` 漏点）；原始异常只进 `Log.w("GuangYuePlugin", …)`；新增 `fun retry()`：先 `manager.notifyReload(platform)` 清插件 runtime 与查询缓存再重搜，绕过负缓存 |
| `feature/search/.../SearchScreen.kt` | 失败条尾部加「重试」按钮（`FoliaButton(Quiet, GuangYueIcons.RefreshCw)`），参数 `onRetry` 带默认值不破坏旧调用点 |
| `feature/library/.../viewmodel/HomeViewModel.kt` | 安装/更新/体检三类失败的 `say("…" + t.message)` 全部改 `PluginFailureText.installFailed/updateFailed/healthFailed` + `logRaw()` 写日志 |
| `feature/library/.../screen/PluginManagerScreen.kt` | `firstSentence` / `statusOf` 两处走 `PluginFailureText`（首字占位以外的报错文案） |
| `app/.../nav/NavHost.kt` | 搜索页接线 `onRetry = { vm.retry() }` |
| `core/plugin/src/test/.../PluginFailureTextTest.kt`（新增） | 9 用例，含「番茄原文 → 输出不含 com.guangyue / 不含 ` at ` / 等于『源站返回错误码 6000』」 |

**怎么验证的（真机 gy34，0.0.9 包）**

- 插件页选「番茄畅听」为当前音源 → 搜索页搜 `ZHOUJIELUN` → 失败条显示：
  `番茄畅听 音源搜索失败：源站返回错误码 6000，换个音源试试` + 「重试」按钮，**界面无任何堆栈/英文类名**（截图 `fanqie-fail-banner.png`）。
- 点「重试」：`logcat -s GuangYuePlugin` 出现新一条 `search failed plugin=fanqie-… keyword=ZHOUJIELUN: 搜索失败 code=6000 / at apiSearch (plugin.js:2:17847)` —— 证明确实重新发了请求，**原始异常只落在日志里**；界面仍是那句中文提示（截图 `fanqie-fail-retry.png`）。
- 插件列表里番茄那一行状态同样变成 `搜索失败：源站返回错误码…`，展开详情不再出现 `com.guangyue.core.plugin.runtime.PluginRuntimeException`（截图 `plugins-list-*.png`）。

## 第 2 件：酷狗 / 酷我 / QQ / 汽水 / 网易云 取链

见下面「5 插件矩阵」。结论：**5 个全部失败，且全部是插件上游/源站侧失效，不是 App 网络桥缺能力**。本段零生产代码改动（`PluginRuntime.kt` / `prelude.js` / `jsprelude/**` 均未动），只落了离机取证测试与证据。

## 第 3 件：插件图标

**根因**：官方订阅源 `plugins.json` 只有 name/url/version，**没有 icon 字段**；插件源码里也 0 命中 `icon/logo/avatar`；而 57 个插件的 `srcUrl` 全托管在 `raw.githubusercontent.com` / `hebijunge.github.io`，所以「按 srcUrl host 取 favicon」只会全部拿到 GitHub 图标 —— 0.0.8 全是占位图的原因就在这。

**改了什么**

- `feature/library/.../screen/PluginIcon.kt`（重写，158 行）：新增 28 条 `platform → 站点图标 URL` 映射（逐一 `curl` 校验过 content-type 与字节魔数，见证据），取图标顺序 = ① 元数据 `iconUrl` ② platform 映射 ③ srcUrl 同 host 的 favicon（`isCodeHost()` 排除 github/gitee 等代码托管站，避免拿到 GitHub 图标）④ 首字占位。加载失败自动轮换候选，全失败才落占位。
- `feature/library/.../screen/PluginManagerScreen.kt`：`SourceRow` 里原来的「首字方块」整块换成 `PluginIcon(plugin)`。
- `feature/library/build.gradle.kts`：补 `testImplementation(libs.junit)`。
- `feature/library/src/test/.../PluginIconUrlsTest.kt`（新增，8 用例）：5 源各拿各站图标、github 托管的 srcUrl 永不成为图标、元数据优先、未知 platform 落占位。
- `app/src/test/.../FakePluginIcons.kt`（新增）+ `ScreenshotTest.kt`：JVM 截图环境没有网络，给 Coil 装一个只对 http(s) 生效的假 Fetcher（按 URL 画确定性的彩色圆点），让 Robolectric 截图能看到图标而不是占位。

**怎么验证的**

- 单测 `PluginIconUrlsTest` 8/8 过，断言「酷狗= kugou.com、QQ = y.qq.com、网易云 = 163.com、汽水 = 抖音、酷我 = kuwo.cn 且五者互不相同」。
- 真机：插件列表每一行都渲染成 `PluginIcon`（截图 `plugins-list-*.png`）；图标 URL 的可达性在离机用 `curl` 逐个验证过（`.cli/a/ico/`），本次模拟器在受限网络下抓不到远端 PNG 时按设计回落首字占位，不会出现破图。
- JVM 截图（`plugins-list-icons-light.png` / `-dark.png`）证明图标链路接的是「真图标位」而不是写死首字：图里 5 行显示的是各自站点图标，番茄一行因为没有可取图标按设计显示首字占位。

## 第 4 件：插件列表底部 inset

**改了什么**：`feature/library/.../screen/PluginManagerScreen.kt` 内层滚动 `Column` 末尾、原有 `Spacer(height = GySpace.xl)` 之后追加
`Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))`，并加注释说明 edge-to-edge。

**如何确认只补 navigationBars 就够**：插件页是二级页，`app/.../nav/Root.kt:52` `showBar = currentRoute in topLevelRoutes` ⇒ 插件页没有底栏、也没有迷你条（迷你条只由 `NavHost.kt` 注入三个顶层页），所以只需要 navigationBars 高度。

**怎么验证的**：真机滚到列表最底（57 项），最后一项「小熊电音」完整可见，其下方还有「57 个可选」、「一键安装官方音源」按钮组、「订阅源」卡片，**系统手势条上方留有留白，没有任何内容被遮住**（截图 `plugins-bottom-inset-light.png`）。对比 0.0.8 基线 `before-fanqie-raw-error.png` 里 B站那一行贴着手势条被切。

## 5 插件矩阵

### A. 真机实测（gy34，0.0.9 包；每个都跑「搜 ZHOUJIELUN → 点第一首 → 看播放页状态」）

| 插件 | 取链结果（0.0.9 真机） | 能否出声 | 失败原因 |
|---|---|---|---|
| 酷狗音乐 1.9.24 | 搜到 20 首，点第一首「夜曲」→ 播放页 `这个音源取不到「夜曲」的播放地址`，0:00/0:00，进度不走 | **否** | 见下表 B 的离机取证 |
| 酷我音乐 1.9.24 | 搜到 20 首，点「晴天」→ **直接开始播放**，`0:00 / 0:11` 进度前进、按钮变暂停 | **是** | 高品档能出链；flac 档仍失败（见 B） |
| QQ 音乐 1.9.26 | 搜到 20 首，点「发如雪」→ `这个音源取不到「发如雪」的播放地址` | **否** | 见下表 B |
| 汽水音乐 1.9.23 | 搜到 20 首，点「因为爱情」→ **直接开始播放**，`0:07 / 3:22` 进度前进 | **是** | standard 档失败、高品档换源酷我后可出声（见 B） |
| 网易云音乐 1.9.27 | 搜到 20 首，点「骑士精神」→ `这个音源取不到「骑士精神」的播放地址` | **否** | 见下表 B |

截图：`plugin-kugou.png` / `plugin-kuwo.png` / `plugin-qq.png` / `plugin-qishui.png` / `plugin-netease.png`。

**和 0.0.8 的差别要说清**：0.0.8 装机现场这 5 个源全部是「能搜到歌，但取不到链」；0.0.9 真机上**酷我、汽水已经能真出声**，酷狗体检降为「只能搜索，取不到播放地址」。本段没有改插件运行时、也没有改插件源码，这个差别来自插件自身的高品档换源链路 + 源站波动，**不是本段代码修出来的**，不能记成修复。

### B. 离机逐请求取证（5 音质全试，证据见 `five-source-evidence.jsonl` 与 `five-source-raw/`）

| 插件 | 离机取证结果 | 失败原因 |
|---|---|---|
| 酷狗音乐 1.9.24 | 失败，5 音质全败，`酷狗取链失败：未取得播放链接` | 插件/源站失效为主：官方 `m.kugou.com/app/i/getSongInfo.php?cmd=playInfo` 200 但 `status:0/bitRate:0/fileSize:0` 无 url；自建聚合源 `103.79.184.97` 500、`88.lxmusic` 403「脚本完整性验证失败」、`musicserver.haitangw.cc` 200+`APP_UPGRADE_REQUIRED`。同一轮已从 `fsandroid.tx.kugou.com` 拿到 206 真音频（ID3/`fLaC` 魔数）却仍判失败 ⇒ 插件取链验收逻辑自身失效。需插件上游更新 |
| 酷我音乐 1.9.24 | standard 档失败（flac 另报 `hyw kw flac24bit not fLaC: empty`） | `mobi.kuwo.cn/mobi.s?type=convert_url3` 已下线 → 404（curl 复验 404/0.19s）；`kw-er.kuwo.cn` 音频 410；`music.cute521.cn` 403。可用的 `convert_url_with_sign` → 200 → `car-lw/car-er.kuwo.cn` 206 未被插件采纳。需插件上游更新 |
| QQ音乐 1.9.26 | 失败，5 音质全败 | 自建中转 `tang.api.s01s.cn/music_open_api.php` 连接超时 ×2（curl 直连 200/1.46s），插件自身 `SOURCE_TIMEOUT=4500ms` 没等到；官方 `u.y.qq.com`、`ws.stream.qqmusic.qq.com` 200 但无 url。需插件换中转/加重试容错 |
| 汽水音乐 1.9.23 | **standard 失败，high/320k/flac 成功**（换源到酷我，206 真音频） | 签名端点 `POST api.vsaa.cn/api/music/soda/sign-pc.php` 400，curl 复验 422 `missing field \`instance\`` ⇒ 契约变更；备源 `211.161.236.45` TLS 失败。需插件跟随签名契约 |
| 网易云音乐 1.9.27 | 失败 | 自建适配器全灭：`api.cenguigui.cn` 521、`yinyue.haitangw.net` 404、`api.cxzja.cn`(3.8s) 与 `music-api.gdstudio.xyz`(5.1s) 超插件 4.5s 预算被丢弃；官方 `interface3.music.163.com` 200 无 url。需插件上游更新 |

**类别「App 网络桥缺接口」被排除**：5 个插件的每一轮请求都真实到达源站（大量 200/206），桥 `__gy_http` 甚至成功回传过 206 音频字节；全程没有「无法加载模块」/「not a function」/「宿主桥未就绪」。所以本段没有「App 侧可修的共性缺陷」。

仍登记为候选（未证实、未改）：5 个插件都传 `maxRedirects: 0` 并有人依赖 `responseURL`，而 `HostBridge.kt`/`prelude.js` 里 `responseURL` / `finalUrl` / `maxRedirects` 出现次数均为 0，重定向被 OkHttp 的 `followRedirects` 吞掉。本段 5 个失败没有任何一条错误文本指向重定向，故未动桥。

## 验证汇总

- 单测：`:core:plugin` / `:core:player` / `:feature:search` / `:core:data` / `:core:download` / `:app` / `:core:web` / `:feature:library` / `:feature:player` 全跑，`TOTAL tests=113 failures=0 errors=0 skipped=2`（0.0.8 基线 94/0/2，本段新增 9 个 `PluginFailureTextTest` + 8 个 `PluginIconUrlsTest` + 2 个取证探针）。跳过 2 个与基线相同（需要离线插件缓存的报告类用例）。
- 真机：gy34（720x1600、320dpi），0.0.9 release 包（versionCode 9 / versionName 0.0.9），`adb install -r` 后 `dumpsys package` 核对版本号。
- 截图清单（`docs/ui-0.0.9/a/`）：`fanqie-fail-banner.png`、`fanqie-fail-retry.png`、`plugins-list-icons-light.png`、`plugins-list-icons-dark.png`、`plugins-bottom-inset-light.png`、`fanqie-plugin-row.png`，以及 5 个插件的截图 `plugin-kugou.png` / `plugin-kuwo.png` / `plugin-qq.png` / `plugin-qishui.png` / `plugin-netease.png`（内容为各自插件行状态 + 取链失败提示条）。
