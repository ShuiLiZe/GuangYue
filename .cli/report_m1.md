# 广乐 GuangYue · M1 报告

日期：2026-10-28
结论：**M1 全部交付项完成，`gradlew assembleDebug` 通过，代码已推送到私有仓库 `ShuiLiZe/GuangYue`（branch `main`，commit `1058911`）。**

---

## 1. 交付结果

| 项 | 状态 | 位置 |
| --- | --- | --- |
| 多模块骨架 + assembleDebug | ✅ | APK：`app/build/outputs/apk/debug/app-debug.apk`（28.5 MB） |
| :core:plugin 插件运行时 | ✅ | quickjs-kt，每插件独立 runtime，15s 超时 + 内存上限，CommonJS 垫片，axios→OkHttp，prelude 注入，插件安装/启停/更新 |
| :core:player | ✅ | Media3 `MediaLibrarySession` + `MediaSessionService`，`ResolvingDataSource` 懒解析 getMediaSource，失败重取一次 |
| 歌词 | ✅ | LRC 多时间戳 / `[offset]` / 翻译合并，二分查找定位当前行 |
| Folia 风格 UI | ✅（舞台动画为简版） | 首页 / 播放页 / 歌词流 / 歌词舞台 / 插件管理，深浅双模式 |
| demo 插件 | ✅ | `docs/demo-plugin.js`（仅 SoundHelix 公开示例音频 + 手写 LRC） |
| docs/PLUGIN_API.md | ✅ | `docs/PLUGIN_API.md` |

仓库：https://github.com/ShuiLiZe/GuangYue（private）

## 2. 模块树

```
GuangYue/
├── app/                    壳：Application、MainActivity、NavHost、Theme、图标
├── core/
│   ├── plugin/             MusicPluginBridge / PluginRuntime / PluginManager / PluginStore /
│   │                       HostBridge / MusicFreeModels / PreludeProvider
│   │   ├── src/main/assets/prelude.js      esbuild 产物（558 KB）
│   │   └── jsprelude/      prelude 源码工程（build.mjs / verify.mjs / src/index.js）
│   ├── player/             PlaybackController / GuangYuePlaybackService / PluginMediaSourceResolver /
│   │                       MediaDataSourceProvider / LibraryTree / MediaOrigin
│   ├── download/           WorkManager 下载队列（:core:download）
│   └── data/               Room（TrackEntity / PlaylistEntity / Station / SearchHistory）
│                           + LrcParser / LyricSynchronizer / Repositories
├── feature/
│   ├── search/             搜索页 + SearchViewModel（多插件并发搜索）
│   ├── player/             播放页 / 歌词流 / 歌词舞台 / LyricsViewModel / CoverImage
│   └── library/            首页（coverflow）、插件管理页、GuangYueDesign 设计令牌
├── docs/                   PLUGIN_API.md、demo-plugin.js
├── design/                 三张 HTML 设计稿（home / lyrics-flow / lyrics-stage）
└── gradle/libs.versions.toml
```

## 3. 关键文件

- `core/plugin/.../PluginRuntime.kt` — 单插件 QuickJS runtime；`module.exports` + `require()` 垫片；宿主回调（`__gy_http`、`__gy_timer`、`__gy_console`）。
- `core/plugin/.../MusicPluginBridge.kt` — 按 `platform+id` 路由，注入 prelude，统一超时/内存/异常收敛，TTL 搜索缓存。
- `core/plugin/.../PluginManager.kt` — 安装（本地文件/URL）、沙箱探针校验、启用禁用、`checkUpdate`/`update`。
- `core/plugin/jsprelude/src/index.js` — 为 QuickJS 补 `atob`/`Buffer`，把 cheerio / CryptoJS / dayjs / Qs / he / BigInteger 挂到 `globalThis`。
- `core/player/.../PluginDataSourceFactory.kt` + `MediaDataSourceProvider.kt` — `ResolvingDataSource`：真开始播时才取直链，失败重取一次。
- `core/data/.../LrcParser.kt` / `LyricSynchronizer.kt` — 解析与二分同步。
- `feature/library/.../HomeScreen.kt` — 大理石渐变 + 网点、分段胶囊 Tab、拍立得 coverflow、悬浮胶囊迷你播放条。
- `feature/player/.../LyricFlowScreen.kt` — 封面取色模糊底、当前行蓝调、前后行模糊淡出、拖动后暂停自动滚动 3 秒。
- `feature/player/.../LyricStageScreen.kt` — 深海军蓝底 + 浮动几何、衬线大字逐字散落微旋转、当前字蓝光光环。
- `app/.../nav/NavHost.kt` — 路由与依赖注入装配；插件页接系统文件选择器与 URL 对话框。

## 4. 实际使用版本

| 组件 | 任务书要求 | 实际使用 |
| --- | --- | --- |
| Gradle | 9.8.2 | **9.7.1**（降级，见 §5） |
| AGP | 9.4.1 | 9.4.1 |
| Kotlin | 2.4.21 | 2.4.21 |
| KSP | 2.3.12 | 2.3.12 |
| Compose BOM | 2026.09.00 | 2026.09.00 |
| Media3 | 1.11.1 | 1.11.1 |
| Hilt | 2.60.1 | 2.60.1 |
| hilt-navigation-compose / hilt-work | 1.4.0 | 1.4.0 |
| Room | 2.8.5 | 2.8.5 |
| WorkManager | 2.12.0 | 2.12.0 |
| OkHttp | 5.5.1 | 5.5.1 |
| quickjs-kt-android | 1.0.15 | 1.0.15 |
| jaudiotagger | 3.0.1 | 3.0.1 |
| coil3 | 3.6.3 | 3.6.3 |
| navigation-compose | 2.10.2 | 2.10.2 |
| palette-ktx | 1.1.0 | 1.1.0 |
| prelude 依赖 | — | cheerio 1.2.0 / crypto-js 4.1.1 / dayjs 1.11.23 / qs 6.17.0 / he 2.0.0 / big-integer 1.6.52 |

包名 `com.guangyue.music`，minSdk 26，compileSdk / targetSdk 36。

## 5. 编译结果与降级说明

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**，248 tasks。
- **Gradle 9.8.2 → 9.7.1**：`services.gradle.org` 的分发包重定向到 GitHub releases，本机网络 `curl exit 35` 下载失败；工作区自带的 `gradle-9.8.2-bin.zip` 实测只有 2 MB 且 SHA256（`ea7be7…`）与官方（`21bd5d…`）不符，是损坏文件，已弃用。本机 `~/.gradle` 内已有可用的 9.7.1 分发包，故锁定 9.7.1。换网络后改 `gradle/wrapper/gradle-wrapper.properties` 一行即可升回。
- 依赖仓库全部走阿里云镜像（`settings.gradle.kts`），因为 Maven Central 直连极慢。
- AGP 9 的内置 Kotlin 支持按新写法配置；KSP 与 Hilt 在 AGP 9 下正常。

## 6. 已知问题

1. **单元测试缺失**：LRC 解析与 `MediaOrigin` 序列化还没写 JVM 单测，目前靠编译 + 手工路径保证。
2. **歌词舞台是简版**：逐字高亮、光环、浮动几何已实现，但入场/退场编排与逐字时间轴尚未对齐到字级（只到行级 + 均匀拆分）。
3. **插件的 `getAlbumInfo` / `getArtistWorks` / `importMusicSheet` / `getTopLists`** 已在桥接层实现调用与数据结构，但 UI 侧目前只用到 `search` / `getMediaSource` / `getLyric`，其余入口留到 M2 接。
4. **下载模块**只打通了 WorkManager 队列与插件取流，断点续传与下载页 UI 未做。
5. **未在真机/模拟器上跑过**：本机能编出 APK，但没有跑过 instrumentation；UI 的滚动/取色效果需实机验收。
6. prelude 的 `Buffer` 是精简实现（`from`/`toString`/`concat`/`subarray`），个别插件若深度依赖 Node 的 Buffer API 可能需扩垫片。

## 7. M2 建议

1. **补测试**：LrcParser（多时间戳、offset、翻译合并）、LyricSynchronizer 二分、MediaOrigin 编码往返，全部 JVM 单测。
2. **插件能力闭环**：把 `getAlbumInfo` / `getArtistWorks` / `getTopLists` / `importMusicSheet` 接到专辑页、歌手页、排行榜与歌单导入 UI，做插件调用日志页（`console.*` 已有通道）。
3. **歌词升级**：舞台模式做字级时间轴（用行时长对字数加权分布 + 弹性缓动），加逐行入场编排；歌词流加双指缩放与长按复制。
4. **下载体验**：断点续传、后台通知进度、失败重试策略，以及「本地」Tab 的扫描入库。
5. **实机验收**：在 Android 10+ 与 Android 14 各跑一遍，确认前台服务保活、通知控制、锁屏控制、取色性能。
6. 升回 Gradle 9.8.2（换网络或预置分发包）后跑一轮回归。

## 8. 边界执行说明

App 只做通用壳与插件运行时；**没有内置、预装、适配或调试任何第三方平台音源插件**（含反编译的酷我/酷狗/QQ/网易云/B站插件），**没有写任何代理中转取高音质直链、绕过付费/VIP 的逻辑**。所有插件由使用者自己在「插件管理」页从本地文件或 URL 安装。demo 插件只使用 SoundHelix 公开示例音频与手写 LRC。本次会话中没有收到要求违反上述边界的需求。
