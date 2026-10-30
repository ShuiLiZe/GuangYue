# LX 五源 × 四音质取链矩阵（0.0.8 M1b）

- 脚本：`docs/ui-0.0.8/lx/five-source-lx.js`，平台上报 `[tx, wy, kg, kw, mg]`
- 跑法：`LxFiveSourceMatrixTest`（真实网络，JVM 内跑 QuickJS 运行时），原始输出见 `matrix-result.txt`
- 采集时间：2026-10-30 18:57
- 结论：**8/20 拿到可用直链，其中真正达到请求档位的只有 kw 128k/320k 与 wy 128k/320k 共 4 格**；其余 4 格是源站降级给的低档位（已如实标注）。tx 与 mg 高档位失败原因均为「未登录/非会员」，不是取链实现问题。

## 矩阵总表

| 源 | 曲目 | 128k | 320k | flac | flac24bit |
|---|---|---|---|---|---|
| kw 酷我 | 花海 `474678847` | ✅ 成功 | ✅ 成功 | ⚠️ 降级 →320k mp3 | ❌ 失败 |
| kg 酷狗 | 恒星不忘 `b9b3a55f…` | ⚠️ 降级 hq mp3 | ⚠️ 降级 hq mp3 | ⚠️ 降级 hq mp3 | ⚠️ 降级 hq mp3 |
| tx QQ | 晴天 `0039MnYb0qxYhV` | ❌ 失败 | ❌ 失败 | ❌ 失败 | ❌ 失败 |
| wy 网易 | 屋顶 `5257138` | ✅ 成功 | ✅ 成功 | ⚠️ 降级 →320k mp3 | ⚠️ 降级 →320k mp3 |
| mg 咪咕 | 晴天(Live) `600913000007163534` | ✅ 成功 | ❌ 失败 | ❌ 失败 | ❌ 失败 |

## 逐格明细

### kw 酷我 — 花海(474678847)

| 档位 | 结果 | 实际拿到 | 源站原始返回（截断） | 原因判断 |
|---|---|---|---|---|
| 128k | ✅ 成功 | `M500004OK6pa2JfRwK.mp3` bitrate 128 | `{"format":"mp3","bitrate":128,"url":"http://kw-er.kuwo.cn/…/M500004OK6pa2JfRwK.mp3?bitrate$128&format$mp3…"}` | 正常，达标 |
| 320k | ✅ 成功 | `M800004OK6pa2JfRwK.mp3` bitrate 320 | `{"format":"mp3","bitrate":320,"url":"http://kw-er.kuwo.cn/…/M800004OK6pa2JfRwK.mp3?bitrate$320&format$mp3…"}` | 正常，达标（与 128k 是不同 URL，真 320k） |
| flac | ⚠️ 降级 | 与 320k **同一条** 320k mp3 | 第一次 `br=2000kflac` 回 `format:"mflac"`（加密容器，播放器吃不了），脚本按 `kwFallbacks` 自动下退 `br=320kmp3` 拿到 `M800…mp3` | 酷我无损是自家 mflac/mgg 加密容器，无法直放；按设计逐级下退，**不冒充无损** |
| flac24bit | ❌ 失败 | — | kw 自身不支持该档（`kwQualitys['flac24bit']` 未定义 → `不支持的音质：flac24bit`），兜底链走到 mg：`{"code":"-200","info":"歌曲下线暂不支持播放，敬请期待"}` | 酷我无 24bit 档；跨源兜底时 mg 拿 kw 的 rid 查询，咪咕回「歌曲下线」。非实现缺陷 |

### kg 酷狗 — 恒星不忘(b9b3a55ffb5b95f165839a26120e9fec)

四档**全部返回同一条 URL**：

```
http://fsandroid.tx.kugou.com/202610301857/<sig>/v3/b9b3a55f…/yp/full/ap1005_pi2_ct430100_s3521156714.mp3
```

| 档位 | 结果 | 原因判断 |
|---|---|---|
| 128k | ⚠️ 降级 | tracker 免费档只给 `hq` mp3（约 128-192k），请求档位不影响结果 |
| 320k | ⚠️ 降级 | 同上，tracker 不返回 320k 档 |
| flac | ⚠️ 降级 | 日志 `[酷狗] 请求无损但只有 mp3，已回落`；`extName!=flac` 时如实记录 |
| flac24bit | ⚠️ 降级 | 同上 |

源站原始返回（截断）：`{"status":1,"url":["http://fsandroid.tx.kugou.com/…/ap1005_pi2_ct430100_s3521156714.mp3"],"extName":"mp3","bitRate":…}`

**说明**：`kgTracker(hash,'hq')` 固定请求 hq 档，四条链路对同一首歌必然同 URL。要真正区分四档需要登录态 tracker 或 `br=flac` 参数，当前未登录拿不到。这是「降级」，不是「成功」。

### tx QQ — 晴天(0039MnYb0qxYhV，strMediaMid 003Qui1q2u1Zho)

四档全部失败。

| 档位 | 结果 | 源站原始返回（截断 300 字） | 原因判断 |
|---|---|---|---|
| 128k | ❌ 失败 | `{"req_0":{"code":0,"data":{"midurlinfo":[{"songmid":"0039MnYb0qxYhV","filename":"M500003Qui1q2u1Zho.mp3","purl":"","result":104003,"auth_switch":16897793,…}],"testfile2g":"C400003mAan70zUy5O.m4a?guid=10000&vkey=…"}}}` | `purl` 为空、`result=104003`：未登录 QQ 不发 vkey。响应里的 `testfile2g` 是 30 秒试听片段，**不是**这首歌的直链，已改为报失败 |
| 320k | ❌ 失败 | 同上，`filename:"M800003Qui1q2u1Zho.mp3"`、`purl:""`、`result:104003` | 同上 |
| flac | ❌ 失败 | 同上，`filename:"F000003Qui1q2u1Zho.flac"`、`purl:""`、`result:104003` | 同上，且未登录无无损版权 |
| flac24bit | ❌ 失败 | 最终兜底走到 mg：`{"code":"-200","info":"歌曲下线暂不支持播放，敬请期待"}` | QQ 自身失败后跨源兜底全部落空 |

文件名已正确拼成 `M500/M800/F000 + 003Qui1q2u1Zho`（`strMediaMid` 传递正确），失败发生在 QQ 服务端鉴权，不在拼参。

### wy 网易 — 屋顶(5257138)

| 档位 | 结果 | 实际拿到 | 源站原始返回（截断） | 原因判断 |
|---|---|---|---|---|
| 128k | ✅ 成功 | `br:128001` `level:"standard"` | `{"data":[{"id":5257138,"url":"http://m701.music.126.net/…/9e257ff0610f84e4242cf0127dac6005.mp3?vuutv=…","br":128001,"size":5105833,"code":200,"type":"mp3","fee":8,"level":"standard","encodeType":"mp3","time":319039,"sr":44100}],"code":200}` | 正常，达标 |
| 320k | ✅ 成功 | `br:320001` `level:"exhigh"` | `{"id":5257138,"br":320001,"size":12764517,"level":"exhigh","encodeType":"mp3","fee":8,"code":200,…}` | 正常，达标 |
| flac | ⚠️ 降级 | `br:320001` `level:"exhigh"` | 请求 `br=999000`，返回仍是 `{"br":320001,"level":"exhigh","encodeType":"mp3"}` | 未登录最高只到 exhigh；服务端静默降档，不报错只降 br。**必须按 br 判定，不能按请求档** |
| flac24bit | ⚠️ 降级 | `br:320001` `level:"exhigh"` | 请求 `br=1999000`，返回 `{"br":320001,"level":"exhigh","encodeType":"mp3"}` | 同上，未登录无 24bit 版权 |

**eapi 400 专项结论**：`POST https://interface3.music.163.com/eapi/song/enhance/player/url` 四次请求全部 **HTTP 200 + `code:200`**，说明 `aesEncrypt` 的签名/模式/编码工作正常，当前不存在 eapi 400。历史上出现过 400，根因是 `LxMusicInfo.of` 只给 `musicId`/`id` 不给 `songmid`，脚本用 `ids=[undefined]` 拼请求，eapi 回 `{"msg":"参数错误","code":400}`——**那不是加密错，是入参缺键**；已在 `LxMusicInfo.of` 的 `wy` 分支补 `songmid` 修掉。

### mg 咪咕 — 晴天(Live) contentId 600913000007163534

| 档位 | 结果 | 源站原始返回（截断 300 字） | 原因判断 |
|---|---|---|---|
| 128k | ✅ 成功 | `{"code":"000000","data":{"url":"https://freetyst.nf.migu.cn/public/product9th/product44/2021/07/1112/…/MP3_128_16_Stero/60054704101123747.mp3?channelid=02&msisdn=…&Tim=…&Key=…"}}` | 正常，`toneFlag=PQ` 免登录直接给链 |
| 320k | ❌ 失败 | `{"code":"000000","data":{…,"audioFormatType":"HQ","dialogInfo":{"text":"登录后免费试听"},"cannotCode":"440013"…}}`，无 `url` 字段 | `toneFlag=HQ` 只给「登录后免费试听」，`cannotCode=440013` 即会员/登录限制。跨源兜底到 kw/kg/tx 全部落空（三者都不认咪咕的数字 contentId） |
| flac | ❌ 失败 | 同上，`toneFlag=SQ`，`dialogInfo.text="登录后免费试听"` | 同 320k，SQ 档同样锁登录 |
| flac24bit | ❌ 失败 | `toneFlag=ZQ` 同「登录后免费试听」；兜底链内 `mgMusicUrl` 抛错后 shim 层再抛一次（`source.js:531` → `at <anonymous> (source.js:511:59)`），错误对象非 `Error` 实例，宿主最终报 `failed` | ZQ 档锁登录；同时暴露一个**健壮性缺陷**：咪咕抛出的非 Error 值在兜底链里被二次包装后丢失 message，宿主只能看到 `failed` |

## 本次改动（five-source-lx.js）

诊断出这三个真实缺陷，均已修：

1. **跨源兜底拿别人的 id 要链**（最严重）。旧代码 `.catch(() => apis[k].musicUrl(info.musicInfo, …))` 把混了五个源 id 的整份 `musicInfo` 丢给下一个源，而 `apis[k].search` 根本不存在。后果：QQ 拿到 `F000undefined.flac` 并返回 `testfile2g` 的 30 秒试听片段，被当成功，把 mg 320k/flac 的真实失败和 kw flac24bit 全部盖掉。现改为 `__pick(k, musicInfo)`：按 `musicInfo.platform`（宿主给的 kuwo/kugou/qq/netease/migu）判断这份 id 是否属于目标源，不属于就直接判失败并说明原因。

2. **QQ 把试听片段当直链**。`purl === ''` 时旧代码返回 `sip[0] + testfile2g`，那是 `C400003mAan70zUy5O.m4a` 的 30 秒预览。现改为报失败，并把 `filename` 与 `result` 码带进错误信息。

3. **酷我对未知 rid 回 `url:"None"`**。`{"code":4011,"data":{"format":"None","sig":"None","url":"None"}}` 里 `url` 字段存在但值就是字符串 `"None"`，旧代码当直链返回，宿主报「不是直链：None」看不出原因。现在 `kwMusicUrl` 同时校验 rid 是纯数字、且 `url !== "None"`，错误信息直接写明「该 rid 不是酷我的曲目」。

## 待办 / 需要人工决定

1. **tx 四档全灭**：未登录 QQ 不发 vkey（`result=104003`）。要通需 QQ 登录态（cookie/vkey），属于账号前提，不是代码问题。是否在本期接入 QQ 登录？
2. **mg 320k/flac/flac24bit**：`cannotCode=440013`「登录后免费试听」。同理需要咪咕账号。
3. **wy flac/flac24bit 降为 exhigh**：网易未登录最高 exhigh。要真无损需要 `@wy_token`（脚本头部注释里那个 token 位）。
4. **kw flac 降级**：酷我无损是 mflac/mgg 加密容器，直放不了。要真无损需要解密层，工作量大。
5. **kg 四档同 URL**：`kgTracker` 固定 `br=hq`。要做真区分需改 tracker 参数并验证付费档。
6. **咪咕非 Error 抛值**：兜底链里 `mgMusicUrl` 抛出的值不是 `Error` 实例，二次包装后 message 丢失，宿主只看到 `failed`。建议在兜底链里统一 `new Error(String(e))`。

## 设备实测截图

`20-play.png` **不满足**验收要求：画面停在 0:00/0:00、播放键为三角形（未播放），并有红字「这个音源取不到…」。原因已查明——首页/最近播放的数据全部来自 `netease-74ca894e4a` 插件，行是 **B 站曲目**（`bilibili:BV…`），logcat 里是 `GYHOME: lx fallback for bilibili:BV1… -> null` 与 `GuangYuePlugin: getMediaSource failed for bilibili: 插件已禁用：B站`。这条路径本来就不该由洛雪五源接（B 站曲目不在五源里），所以播放器必然空。要在设备上看到「进度在走」，需要一个从五源搜索进来的曲目；当前版本首页没有这个入口。**此项未完成，需派活方确认入口或在下一期补搜索页。**
