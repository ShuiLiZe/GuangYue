# 0.0.9 b · 插件图标真机补修 + 状态跟随播放

范围：只修 a 段两个不过项 —— ①插件列表在真机上看不到真实图标；②酷我/汽水能出声，列表却仍写「能搜到歌，但取不到链」。分支 `folia-ui`，本地提交，未推。

## 一、根因

### 1. 真机无图标（a 段 8 行全是首字母圆圈）

a 段 `PluginIcon` 的写法是 `AsyncImage(model = "https://...")` + `onError { index++ }`，在真机上必挂，四个原因叠加：

1. **URL 在设备上不通。** 同一批地址在主机 curl 是 200，在模拟器里 `https://www.kugou.com/favicon.ico` 回 **403**（主机 200 说明不是 DNS/证书问题，是站点按 UA/来源拦了模拟器），番茄 `https://fanqienovel.com/favicon.ico` 回 **200 但 0 字节** `text/plain` —— Coil 拿到空 body 直接判失败。URL 只要一个不对，a 段那张表就整条不可用，而且 `onError` 拿不到状态码，无从区分。
2. **没有回退链。** 一个 platform 只挂一个 URL，挂了就只剩首字圆圈，没有第二个候选，也没有「从 srcUrl 同站点推 /favicon.ico」的兜底。
3. **model 是裸 String。** Coil 3 对裸字符串走 `DefaultRequestOptions`，尺寸未定就按原图解，`Precision` 也是精确模式；28dp 的图标去解 16KB 的 migu ico 既慢又容易失败，列表滚动时还会让请求排队。
4. **Coil 日志静默**（默认不开 verbose），所以真机 logcat 里一条 Coil 输出都没有，a 段靠 Roborazzi 假 Fetcher（`app/src/test/.../FakePluginIcons.kt`，对所有 http(s) 画彩点）出图，等于用假网络证明真网络 —— Hark 判不过是对的。

### 2. 状态不跟播放走

- 列表状态只有两个来源：安装时的出厂基线 `PluginHealthBaseline.lookup(platform)`（读 `core/plugin/src/main/assets/plugin-health.json`）和手动「体检一遍」→ `PluginHealthChecker.checkAll()` → `manager.recordHealth()`。
- 体检的判据是**插件自身** `bridge.getMediaSource()` 返回非空 URL。但真机播放链路是 `PluginMediaSourceResolver`：先插件直出（8s 预算），失败再落到**洛雪取链源**兜底。酷我插件直出失败、洛雪成功 → 真机有声，而体检查的恰好是失败那一半，于是永远停在 no-media。
- 播放成功后没有任何代码回写健康状态，所以状态与实际播放长期不一致。

## 二、改了什么

| 文件 | 改动 |
| --- | --- |
| `core/plugin/src/main/java/com/guangyue/core/plugin/PluginPlatformIcons.kt`（新） | 平台 → 图标 URL 表（约 30 条，仅收录真机能取到或主机实测 200+非空字节的），`candidatesFor(plugin)` 生成候选链：插件自带 `iconUrl` → 平台表 → `srcUrl` 同 host 的 `/favicon.ico`（GitHub/Gitee 等代码托管站排除，避免把仓库头像当站点图标），`urlFor`、`isCodeHost` |
| `feature/library/.../screen/PluginIconLoader.kt`（新） | `probe()` 用独立 OkHttp（4s 连接/读超时、6s 整调、跟随重定向）先 HEAD-ish GET 判 2xx 且 body ≥1 字节；`decode()` 再用 Coil 真解一次 112×112，两者都过才返回；全失败返回 null |
| `feature/library/.../screen/PluginIcon.kt`（重写） | `produceState` 在 IO 上跑 `firstUsable()` 逐条探测，命中才 `AsyncImage(ImageRequest.data(url).size(56).precision(INEXACT))`；未命中/加载中画首字圆圈。探测在后台协程，列表滚动不等图标 |
| `feature/library/.../screen/PluginManagerScreen.kt` | `PluginIcon` 外包 `key(plugin.id)`，避免行复用串图标；详情面板文本由原始异常改为 `firstSentence(note)`（`healthNote` 里存的是未清洗栈帧） |
| `core/plugin/.../PlaybackHealthHook.kt`（新） | 播放实测回写：`recordPlayback(pluginId, via)`，只在当前不是 OK 时改成 `OK` + note「播放实测：<取链途径>」，不改 `enabled`、不触发重新体检。注释里写明三种状态来源与刷新时机 |
| `core/plugin/.../lx/LxSourceManager.kt` | 记 `lastResolvedBy`（命中源 displayName），供回写 note 记录到底哪条取链源出的链 |
| `core/player/.../PlayerModule.kt` | `PluginMediaSourceResolver` 注入 hook：插件直出成功 → `recordPlayback(key, "插件直出")`；洛雪兜底成功 → `recordPlayback(key, "洛雪取链源「Free listen」")` |
| `core/plugin/src/test/.../PluginPlatformIconsTest.kt`（替换原 `PluginIconUrlsTest.kt`） | 16 用例：主流源全部 https、7 个主流源各不相同、酷狗必须是 `t1.kugou.com`、番茄不能是空 favicon、汽水不能是 `www.douyin.com`、`iconUrl` 优先且空白忽略、GitHub 源不取仓库图标、未知平台给空表等 |
| `feature/library/src/test/.../PluginRowStateTest.kt`（新） | 3 用例：`OK` → 「可用」、`NO_MEDIA` → 「取不到链」，并断言投放给 UI 的候选链非空 |

### 状态来源与刷新时机（写死在这份文档里，避免以后再对不上）

| 来源 | 何时写 | 判据 |
| --- | --- | --- |
| 出厂基线 `PluginHealthBaseline` | 安装插件时（`installCode`） | 打包在 assets 里的静态结论 |
| 体检 `PluginHealthChecker.checkAll` | 进页面自动跑 / 点「体检一遍」 | **仅**插件自身 `getMediaSource` 是否有 URL |
| 播放实测 `PlaybackHealthHook` | 每次播放取链成功后 | 实际能出声即 `OK`（含洛雪兜底），只升不降 |

三者都落 `PluginManager.recordHealth()` → 写 index.json → `notifyReload()` → `_plugins` 更新，UI 立刻重绘。列表状态因此永远以「最近一次真实播放成功」为上限，体检结果只用于发现新问题、不会把已经验证能播的源打回去。

## 三、怎么验证的（全部真机 gy34，adb）

1. **图标**：logcat `TAG=GYICON` 逐条打印 `icon <platform> -> <url>`，7 个主流源全部命中（酷狗换 `t1.kugou.com` 后从 403 变 200；番茄改用首页 `<link rel="shortcut icon">` 指向的 `p1-tt.byteimg.com` PNG，6940 字节）。逐条地址、状态码、content-type、字节数见 `icon-probe.txt`。
2. **截图**（`adb shell screencap -p /sdcard/x.png` + `adb pull`，非 Roborazzi）：
   - `device-plugin-list-light.png`：浅色，酷狗蓝 K、咪咕蓝圆标、酷我橙 K、QQ 黄绿音符、汽水抖音音符、番茄红番茄、网易云红圆、B 站蓝电视，全部真实站点图；酷我状态「可用」。
   - `device-plugin-list-dark.png`：同上深色（App 内「外观」开关切，不是系统夜间模式），酷我「可用·当前」。
   - `device-plugin-list-bottom-dark.png`：滚到底部，「57 个可选·只有当前音源在跑」+ 一键安装/检查更新/体检一遍/从文件安装/从链接安装/取链源 + 订阅源，底部 inset 正常。
3. **状态跟随**：选酷我 → 搜「周杰伦」→ 播晴天，logcat `GYHEALTH: playback ok kuwo via=洛雪取链源「Free listen」 -> ok`，同批 `lx Free listen 取到链：kw 晴天`、`lx fallback for kuwo:kuwo_228908 -> http://kw-er.kuwo.cn/...mp3`；回插件页酷我由「能搜到歌，但取不到链」变「可用」，`files/plugins/index.json` 里 `health=ok`、`healthNote="播放实测：洛雪取链源「Free listen」"`。未播过的源保持原结论（只升不降）。
4. **构建**：`.\gradlew.bat testDebugUnitTest` → **126 tests / 0 failed / 2 skipped**（≥115/0/2）；`:app:assembleDebug` 成功，`adb install -r` 成功，真机 versionName 0.0.9、versionCode 9。

## 四、没做到的

- 番茄、汽水的「当前音源」下插件列表里其余源仍是「能搜到歌，但取不到链」——它们确实还没经过真实播放，按「只升不降」设计就该是这个状态；要变成「可用」需各播一首，或点「体检一遍」按插件直出口径重判。
- 29 个中文名/DJ 类小源没有可靠站点图标，仍用首字圆圈（宁可占位，不编 URL）。清单见 `icon-probe.txt` 末段。
- 一次踩坑记录：改 `PluginManagerScreen.kt` 时用 PowerShell `Set-Content` 处理含中文的 Kotlin 文件会把 UTF-8 写成 GBK 乱码，最后是一整份重写修好的；这类文件以后只用编辑器写。
