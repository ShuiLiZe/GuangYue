# 0.0.8 M1c 收尾验收报告

- 工作区：`D:\workspaces\GuangYue`，分支 `folia-ui`，基线 HEAD `5ae4bb4`
- 设备：`emulator-5554`（1080x2400，density 420），已装 `com.guangyue.music` 0.0.8 (versionCode 8)
- 结论：**不打 `0.0.8` tag**。第 1 项（真实播放举证）、第 3 项（edge-to-edge）、第 4 项（lx 功能回归）未达成，其余通过。

## 逐项结果

| # | 项目 | 结果 | 证据 / 说明 |
|---|------|------|-------------|
| 1 | 酷我搜「周杰伦」点播，出声 + 进度条在走，间隔 ≥15s 两张截图 | **FAIL** | 出声已达成、取链已修好，但无法在 15s 窗口内截到进度各不相同的一对图 |
| 2 | 取链失败/播放出错给可读提示并可重试，无原始异常文本 | PASS | `03-error.png`：「这个音源取不到「发如雪」的播放地址」+「用其他音源搜同名歌曲」按钮，界面无堆栈或裸异常 |
| 3 | edge-to-edge：首页/搜索/我的/插件页/播放页各一张 `10-*.png` | **未完成** | 模拟器在验收中途两次崩到 `System UI isn't responding`，App 多次 ANR，时间耗尽 |
| 4 | lx/00-10 功能回归（单选持久化/搜索跟随/首页无假数据）各一张 | **未完成** | 同上 |
| 5 | `gradlew test` 总数/失败数 | PASS | 17 个 testsuite XML，**94 tests / 0 failures / 0 errors / 2 skipped**；`test` 任务 BUILD SUCCESSFUL |
| 6 | `assembleRelease` 签名 APK、路径与 sha256 | PASS | 见下 |

## 第 1 项：播放链路修复与举证失败原因

**已修复的根因**（原 `docs/ui-0.0.8/lx/five-source-lx.js`）：

1. 跨源兜底是「盲兜底」——把某个源的 song id 直接丢给另一个源（`kuwo rid=118990` → 酷狗 `Bad hash` → QQ → 网易 → 咪咕）。酷我对未知 rid 返回 `url="None"`，`LxRuntime.kt:220` 只接受 `http(s)` 直链，于是抛「取链脚本返回的不是直链：None」，最终 `PlayerModule.kt:108` 抛 `ResolutionException`。
2. 脚本内 `request` 未从 `globalThis.lx` 解构，直接 `request is not defined`。

**修复落点**：`docs/ui-0.0.8/lx/five-source-lx.v3.js`（16811 B，已替代旧脚本，同文件副本保留在 `m1c/`）：

- 确定性顺序：①本源 + 本源 id；②本源降音质（320k→128k→flac→flac24bit）；③其它源**必须先搜到该源自己的 id** 才取链，搜不到就如实失败——不再把 id 跨源乱塞。
- 五源取链实现：酷我 `nmobi convert_url_with_sign`（rid 剥 `MUSIC_/MP3_/kuwo_` 前缀并校验纯数字，拒绝 `format=None`）、网易 eapi（`aes-128-ecb` key `e82ckenh8dichen8`，`digest=md5("nobody"+url+"use"+text+"md5forencrypt")`）、咪咕 `MIGUM3.0 pc/listen/v1.0`、酷狗 `trackercdn v2`（`md5(hash+"kgcloudv2")`）、QQ `musicu.fcg` vkey。
- 新增**同源完整版升级** `kwLongVersion()`：酷我对无版权曲目会回 11~30 秒试听片段而不是失败（见下），拿到的 `duration < 60` 时搜同名曲、挑时长最长且可播的版本重新取链。

**真机验证已成功取链**（`adb logcat`）：

```
lx info kw => {"name":"发如雪","singer":"周杰伦","interval":"04:59","musicId":"kuwo_118990",...}
[Free listen] inited={"status":true,...}
lx Free listen 取到链：kw 发如雪
lx fallback for kuwo:kuwo_118990 -> http://kw-er.kuwo.cn/b0531f02.../M5000004Gmy54cGDqK.mp3?bitrate$1&format$mp3...
GuangYueDataSource: resolved kuwo -> http://kw-er.kuwo.cn/b0531f02...
```

播放确实出声：`AudioTrack: stop(...) called with 1016111 frames delivered`、`c2.android.mp3.decoder`、`DMCodecAdapterFactory: Creating an asynchronous MediaCodec adapter for track type audio`，播放页显示「正在播放 / 青花瓷 / 周杰伦 / 0:02 / 0:11 / 音质：高品」。

**为什么截不到合格的两张图**：酷我搜索「周杰伦」返回的绝大多数曲目是**试听片段**，不是完整曲。PC 侧对同一接口逐条 `ffprobe` 实测：

| rid | 曲目 | 接口 duration | 文件实测 |
|-----|------|---------------|----------|
| 118990 | 发如雪 | 11 | 11.546 s / 185336 B / 128 kbps |
| 143849264 | 夜曲 (mp3.2) | 22 | 片段 |
| 152771373 | 淘汰 (Demo版) | 11 | 片段 |
| 166031326 | 烟花易冷 (片段) | 24 | 片段 |
| 23928868 | 青花瓷 | 290 | 290.944 s 完整 |
| 367887397 | 蜗牛 | 216 | 216.581 s 完整 |
| 474678847 | 花海 (DJ 阿若版) | 210 | 210.469 s 完整 |

11 秒片段播完即触发队列自动跳下一首，实测播放页名称在 16 秒内连续跳变（`花海`→`告白气球`、`明明就`→`爱在西元前`），因此「间隔 ≥15 秒、两张进度各不相同」的截图在片段曲目上物理不可得。

**完整版升级路径已单独验证通过**（同脚本、真实网络、真实返回）：

```
输入 musicInfo = {name:"花海", singer:"周杰伦", platform:"kuwo", rid:"155314039"}  // 26s 片段
输出 http://kw-bj.kuwo.cn/3c91f895.../3928017057.ogg?bitrate$100&format$ogg...     // 完整版直链
```

失败点只在**真机联调窗口**：把带 `kwLongVersion` 的 v3 脚本 `adb push` 到 `files/lx/free-listen-5d9887ec/script.js` 后重启 App，点播「晴天」时 16 秒窗口内仍发生切歌（升级需要额外 2~4 次网络往返，片段在升级完成前已经播完并 next）。**该项不计通过**。

现存截图 `01-play-a.png` / `02-play-b.png` 仅作为过程证据保留，**不作为第 1 项通过依据**。

## 第 2 项：失败提示

`03-error.png`（1080x2400）：播放页显示中文提示「这个音源取不到「发如雪」的播放地址」，并提供「用其他音源搜同名歌曲」按钮供重试；界面不出现 `ResolutionException`、堆栈或裸 JSON。

## 第 5 项：单测

命令：`.\gradlew.bat test assembleRelease --console=plain`，结果 `BUILD SUCCESSFUL in 5m 14s`（620 tasks）。

统计 `**/test-results/**/TEST-*.xml`：

```
TOTAL tests=94 failures=0 errors=0 skipped=2 files=17
```

## 第 6 项：签名 APK

- 路径：`app/build/outputs/apk/release/app-release.apk`
- 大小：8737645 B
- SHA-256：`4C5054EACB2C114A6C92282F13652631D496821282C5F60DEACFB1F2A69B61F1`

## 未完成项与下一步

1. **第 3 项 edge-to-edge 五页截图**：需在模拟器稳定后补 `10-home/10-search/10-library/10-plugin/10-player.png`，用 `adb shell dumpsys window windows | grep -E "mStableInsets|mFrame"` 或 `WindowInsets` 日志辅助判定状态栏/导航栏不重叠。
2. **第 4 项 lx 回归**：单选持久化（`lx/04`）、搜索跟随（`lx/06`）、首页无假数据（`lx/10`）各补一张。
3. **第 1 项**：等 `kwLongVersion` 在真机稳定生效（或在播放器侧对 `duration<60` 的曲目延后起播、先完成升级），再重拍间隔 15 秒的两张图。
4. 三项补齐后方可 `git tag 0.0.8 && git push origin 0.0.8`。

## 临时文件处置

`m1c/` 下 `_probe_*.js`、`_host_test*.js`、`_t*.mp3`、`_u*.xml`、`_ms_*.txt` 等 PC 侧探针与中间产物已全部删除。`five-source-lx.v3.js` 为修复后的正式脚本，同时放在 `docs/ui-0.0.8/lx/`。未 `git add docs/port/`。
