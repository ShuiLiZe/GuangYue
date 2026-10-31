# P1 六平台订阅线路 + 新版插件取链矩阵

生成时间：2026-10-31 19:32:47

- 测试：`core/plugin/src/test/java/com/guangyue/core/plugin/runtime/PluginSourceMatrixTest.kt`（真网 JVM，无模拟器）
- 默认订阅：`https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json`（常用六平台，主线路）
- 备用线路：https://hebijunge.github.io/musicfree-plugins/、https://fastly.jsdelivr.net/gh/hebijunge/musicfree-plugins@master/
- 检索词：`周杰伦`；音质档依次 standard / high / 320k / flac / low（命中第一个可用即停）

## 结论

- 取链 PASS：**3/6**（搜索成功且拿到播放链接）
- 搜索 PASS：**6/6**

## 矩阵（平台 × 搜索 × 取链 × 音质）

| 平台 | 版本 | 线路 | 搜索 | 条数 | 首条 | 取链 | 音质 | 判定 | 原因 | 耗时 |
|---|---|---|---|---|---|---|---|---|---|---|
| QQ音乐 | v1.9.26 | padao | PASS | 10 | 发如雪 | FAIL | - | FAIL | 所有音质档（standard/high/320k/flac/low）都没拿到播放链接 | 21544ms |
| 酷我音乐 | v1.9.24 | padao | PASS | 20 | 晴天 | FAIL | - | FAIL | 所有音质档（standard/high/320k/flac/low）都没拿到播放链接 | 39645ms |
| 酷狗音乐 | v1.9.24 | padao | PASS | 20 | 夜曲 | FAIL | - | FAIL | 所有音质档（standard/high/320k/flac/low）都没拿到播放链接 | 25674ms |
| 咪咕音乐 | v1.9.19 | padao | PASS | 20 | 圣诞星（feat. 杨瑞代） | PASS | standard | **PASS** | - | 1994ms |
| 网易云音乐 | v1.9.27 | padao | PASS | 15 | 布拉格广场 | PASS | flac | **PASS** | - | 11094ms |
| 汽水音乐 | v1.9.23 | padao | PASS | 20 | 因为爱情 | PASS | high | **PASS** | - | 9525ms |

## 内容指纹（「版本号没变、内容变了」的证据）

| 文件 | 新版 sha256 前 16 | 旧缓存 sha256 前 16 | 版本号 | 判定 |
|---|---|---|---|---|
| qq-source.plugin.v1.9.26.js | 70D7D731B0073C6B | 050AB2933058928A | v1.9.26 | **内容已变** |
| kuwo-source.plugin.v1.9.24.js | A8209759BCEC10F7 | FA4B86E323646610 | v1.9.24 | **内容已变** |
| kugou-source.plugin.v1.9.24.js | 40FE2AD33E93DB5F | 7EF85520E223C89E | v1.9.24 | **内容已变** |
| migu-source.plugin.v1.9.19.js | 5AF3029F0AD9AE42 | (旧缓存缺) | v1.9.19 | **内容已变** |
| netease-source.plugin.v1.9.27.js | 70FDC9F0DCF9CCC1 | 8865F4ED84077D45 | v1.9.27 | **内容已变** |
| qishui-source.plugin.v1.9.23.js | AA9AC79F0BB5FF0B | B053F1A254D39849 | v1.9.23 | **内容已变** |

## 与旧版结果对比

| 维度 | 旧（0.0.8 / `docs/ui-0.0.8/lx/matrix.md`，2026-10-30 22:55） | 新（P1，本次） |
|---|---|---|
| 覆盖平台 | 5（kugou/kuwo/qq/qishui/netease） | 6（加 migu） |
| 取链 PASS | 8/20 格（仅 kw 128k/320k、wy 128k/320k 达标） | 3/6 平台 |
| 订阅线路 | GitHub Pages 全量 `plugins.json`（6.7s，易超时） | 主 gh.padao.fun（<2s）+ Pages/jsDelivr 兜底 |
| 同版本内容变更检测 | 无（只比版本号，上游原地改 .js 检测不到） | sha256 + ETag/Last-Modified，可检出并更新 |
| 插件源码版本号 | 完全一致（1.9.26/1.9.24/1.9.24/1.9.23/1.9.27） | 完全一致，但内容 sha256 全变 |

## 现场日志

```
线路 OK 1923ms https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json（1480 B）
订阅清单：https://gh.padao.fun/https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/sub/padao/main.json（1480 B）
条目：6 条
源码 命中缓存 qq-source.plugin.v1.9.26.js（613111 B）
源码 命中缓存 kuwo-source.plugin.v1.9.24.js（497356 B）
源码 命中缓存 kugou-source.plugin.v1.9.24.js（557146 B）
源码 命中缓存 migu-source.plugin.v1.9.19.js（434618 B）
源码 命中缓存 netease-source.plugin.v1.9.27.js（390267 B）
源码 命中缓存 qishui-source.plugin.v1.9.23.js（470689 B）
```

## 洛雪五源 sha256 复核（任务 4）

- `docs/ui-0.0.8/lx/five-source-lx.js` 与 `core/plugin/build/lx/five-source-lx.js` 同为 32829 B，
  SHA256 = `EB74E1B36440D388F8BB0D517E4F24685F9C842B5112F0F175B04242D568AAE4` → 两份一致，
  仓库内**未发现被原地改过**的证据，内置/缓存副本无需更新。
- 仓库内不存在「洛雪五源 v1.6.0」的实体脚本或远端地址（只有 `LxStore.kt` 的示例块注释），
  因此该版本无从比对；按仓库内实体脚本口径复核，结论为「未变」。

总耗时 111408ms
