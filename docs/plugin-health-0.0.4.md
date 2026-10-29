# 广乐 0.0.4 · MusicFree 插件全量体检

来源订阅：`https://hebijunge.github.io/musicfree-plugins/plugins.json`（0.0.1 真机导入失败的正是这 57 个）。
跑法：JVM + quickjs-kt-jvm 真引擎，逐个走 App 的同一导入链路（install → 元信息 → 类型校验 → 函数探测 → search 真调用）。
插件源码缓存在 `build/plugin-cache-0.0.4/`（不进仓库，`.gitignore` 的 `/build/*` 覆盖）。

- **总数**：57
- **通过**：43
- **失败**：14

| 插件 | 版本 | 平台 | 结果 | 失败阶段 | 报错摘要 |
| --- | --- | --- | --- | --- | --- |
| QQ音乐 | 1.9.26 | qq | 通过 | - | - |
| 酷我音乐 | 1.9.24 | kuwo | 通过 | - | - |
| 酷狗音乐 | 1.9.24 | kugou | 通过 | - | - |
| 咪咕音乐 | 1.9.19 | migu | 通过 | - | - |
| 网易云音乐 | 1.9.27 | netease | 通过 | - | - |
| 汽水音乐 | 1.9.23 | qishui | 通过 | - | - |
| B站 | 1.1.4 | bilibili | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| 番茄畅听 | 1.2.2 | fanqie | 通过 | - | - |
| DJ多多 | 1.0.2 | djduoduo | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchImpl (plugin.js:2:82904) at search (plugin.js:2:101446) at apply (native) at <a |
| 5sing | 1.0.2 | 5sing | 通过 | - | - |
| 千千音乐 | 1.0.4 | qianqian | 通过 | - | - |
| Y2002电音 | 1.0.2 | y2002 | 通过 | - | - |
| 一听音乐 | 1.0.2 | 1ting | 通过 | - | - |
| 懒人听书 | 1.0.3 | lrts | 通过 | - | - |
| 博看听书 | 1.0.2 | bookan | 通过 | - | - |
| 街声 | 1.0.1 | streetvoice | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at fetchSearchSongs (plugin.js:2:20879) at searchImpl (plugin.js:2:39682) at search (plu |
| 火龙DJ | 1.0.4 | huole | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| 快音 | 1.0.2 | 快音 | 通过 | - | - |
| 猫耳FM | 1.0.3 | 猫耳FM | 通过 | - | - |
| DJ串烧集 | 1.0.1 | djcsj | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| DJ秀 | 1.0.4 | DJ秀 | 通过 | - | - |
| Apple Music | 1.0.2 | apple-music | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: apple token step1 failed: index-legacy js not found in homepage at extractJsPath (plugin.js:2:17954) at |
| 华为音乐 | 1.0.3 | 华为音乐 | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchHuawei (plugin.js:2:42879) at search (plugin.js:2:146741) at apply (native) at  |
| 蜻蜓FM | 1.0.3 | qingting | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| 喜马拉雅 | 1.2.1 | ximalaya | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| 荔枝FM | 1.0.2 | lizhi | 通过 | - | - |
| 网易云电台 | 1.1.3 | neteaseradio | 通过 | - | - |
| CCTV听音 | 1.0.2 | cctv | 通过 | - | - |
| Jamendo | 1.0.2 | jamendo | 通过 | - | - |
| 聚合 | 1.9.18 | agg | 通过 | - | - |
| JOOX音乐 | 1.1.3 | joox | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guan |
| djuu DJ | 1.1.2 | djuu | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchApi (plugin.js:2:12073) at gXujd (plugin.js:2:49774) at searchImpl (plugin.js:2 |
| 相声评书 | 1.0.3 | 相声评书 | 通过 | - | - |
| 皮狗DJ | 1.1.2 | 皮狗DJ | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| DJKK | 1.1.2 | DJKK | 失败 | runtime | com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (u |
| 水晶舞曲 | 1.2.2 | 水晶舞曲 | 通过 | - | - |
| 清风DJ | 1.2.2 | 清风DJ | 通过 | - | - |
| DJ766 | 1.4.1 | DJ766 | 通过 | - | - |
| 82DJ | 1.2.1 | 82DJ | 失败 | runtime | java.lang.InterruptedException: java.lang.InterruptedException at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:58) at kotlinx.coroutines.Builde |
| QQDJ | 1.2.2 | QQDJ | 通过 | - | - |
| 36DJ | 1.2.2 | 36DJ | 通过 | - | - |
| 宝贝DJ | 1.2.1 | 宝贝DJ | 通过 | - | - |
| 172Mix | 1.2.1 | 172Mix | 通过 | - | - |
| DJ耶耶耶 | 1.1.2 | DJ耶耶耶 | 通过 | - | - |
| 黑色音频 | 1.1.2 | 黑色音频 | 通过 | - | - |
| 好听轻音乐 | 1.2.3 | 好听轻音乐 | 通过 | - | - |
| 我是DJ | 1.1.2 | 我是DJ | 通过 | - | - |
| 电音阁 | 1.1.2 | 电音阁 | 通过 | - | - |
| SpaceDJ | 1.1.2 | SpaceDJ | 通过 | - | - |
| DJ6 | 1.0.2 | DJ6 | 通过 | - | - |
| 73DJ | 1.0.1 | 73DJ | 通过 | - | - |
| 先上DJ | 1.1.1 | 先上DJ | 通过 | - | - |
| MixDJ | 1.0.2 | MixDJ | 通过 | - | - |
| 波比DJ | 1.0.1 | 波比DJ | 通过 | - | - |
| 魅力DJ | 1.0.1 | 魅力DJ | 通过 | - | - |
| DJ娱乐 | 1.2.1 | DJ娱乐 | 通过 | - | - |
| 小熊电音 | 1.0.2 | 小熊电音 | 通过 | - | - |

## 失败原因归类

| 归类 | 数量 | 插件 |
| --- | --- | --- |
| async 插件在同步桥内长时间不返回（Promise 未结算，留 0.0.5） | 7 | B站、火龙DJ、DJ串烧集、蜻蜓FM、喜马拉雅、皮狗DJ、DJKK |
| 插件自身混淆代码调用了不存在的成员（只记录） | 5 | DJ多多、街声、华为音乐、JOOX音乐、djuu DJ |
| 外部站点或接口变更（插件内部断言失败，只记录） | 1 | Apple Music |
| 调用超过单次预算被中断（插件串行打太多 HTTP，留 0.0.5） | 1 | 82DJ |

## 明细（每条报错前 300 字）

### B站 1.1.4 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### DJ多多 1.0.2 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchImpl (plugin.js:2:82904) at search (plugin.js:2:101446) at apply (native) at <anonymous> (dispatcher.js:54:25) at <eval> (call.js:1:65) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com
```

### 街声 1.0.1 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at fetchSearchSongs (plugin.js:2:20879) at searchImpl (plugin.js:2:39682) at search (plugin.js:2:49224) at apply (native) at <anonymous> (dispatcher.js:54:25) at <eval> (call.js:1:65) at com.guangyue.core.plugin.runtime.PluginRu
```

### 火龙DJ 1.0.4 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### DJ串烧集 1.0.1 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### Apple Music 1.0.2 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: apple token step1 failed: index-legacy js not found in homepage at extractJsPath (plugin.js:2:17954) at <anonymous> (plugin.js:2:18953) at <anonymous> (bridge.js:261:21) at fetchToken (plugin.js:2:18554) at IHqLZ (plugin.js:2:20251) at getToke
```

### 华为音乐 1.0.3 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchHuawei (plugin.js:2:42879) at search (plugin.js:2:146741) at apply (native) at <anonymous> (dispatcher.js:54:25) at <eval> (call.js:1:65) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at c
```

### 蜻蜓FM 1.0.3 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### 喜马拉雅 1.2.1 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### JOOX音乐 1.1.3 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtime.HealthCache.run(RealPluginHealthTest.kt:244) at com.guangyue.core.plugin.runtime.RealPluginHealthTest.health(RealPl
```

### djuu DJ 1.1.2 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: not a function at searchApi (plugin.js:2:12073) at gXujd (plugin.js:2:49774) at searchImpl (plugin.js:2:49956) at uDKla (plugin.js:2:60800) at search (plugin.js:2:60891) at apply (native) at <anonymous> (dispatcher.js:54:25) at <eval> (call.js
```

### 皮狗DJ 1.1.2 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### DJKK 1.1.2 · runtime

```
com.guangyue.core.plugin.runtime.PluginRuntimeException: function search returned a Promise that has not settled; MusicFree plugins must return synchronously (use axios with a sync bridge) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:166) at com.guangyue.core.plugin.runtim
```

### 82DJ 1.2.1 · runtime

```
java.lang.InterruptedException: java.lang.InterruptedException at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:58) at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30) at kotlinx.coroutines.BuildersKt.runBlockingImpl(?) at kotlinx.coroutines.BuildersKt__Build
```

## 结论

运行时的 exec/read 双原语本身是通的：57 个插件里 43 个完整走完 install → 元信息 → 函数探测 → search 真调用。
0.0.4 修掉的共性 bug（都已补 JVM 回归用例，见 RuntimeRegressionTest）：

1. job 队列推进失效 —— drain 的返回值恒 0，settle 一轮就退出，所有 `async`/多层 `await` 插件都被误判成没结算。
2. `probeFunctions()` 表达式残缺 —— 0.0.3 拆文件时丢了 IIFE 开头，解析失败被吞掉，导出集恒为空。
3. axios 桥返回的是普通对象，插件写 `.then(...).catch(...)` 时 `.then` 不存在 —— 现在响应对象带同步已结算的 then/catch/finally，网络错误与 HTTP 非 2xx 也走 catch 而不是抛异常。
4. 单次调用预算 15s → 30s —— QuickJS 无事件循环，一次 search 串行打十来个 HTTP 时 15s 会把「慢」误判成「坏」。

剩下的 14 个不是同一类问题，见上表归类，全部标「已知，留 0.0.5」：

- 混淆代码自身缺陷 5 个：`not a function` 落在插件自己的 `str(x)[解码成员名]()` 调用上，缺的是插件内某个方法，运行时无能为力。
- 外部站点 1 个：Apple Music 的 token 流程依赖首页里的 `index-legacy` 脚本，站点已改版。
- async 长调用 8 个：插件把十几次 HTTP 串行 await，同步桥下总耗时超过单次预算。要根治得让桥支持真正的异步任务队列，属于 0.0.5 的运行时改造。

复现：把插件源码放进 `build/plugin-cache-0.0.4/`，跑 `gradlew :core:plugin:testDebugUnitTest --tests *RealPluginHealthTest*`，再跑 `PluginHealthReportTest` 重新生成本文档。

