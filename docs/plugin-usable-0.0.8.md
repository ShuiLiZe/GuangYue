# 广乐 0.0.8 · 插件可用性清单

> 生成时间：2026-10-30T12:41:12.674465900　判据：`search("周杰伦", 1, "music")` 至少返回一条**可映射成列表项**的结果，
> 且拿第一条继续调 `getMediaSource(item, "standard")` 后 `url` 非空。两步都过才算「可用」。

## 结论

| 项 | 数 |
| --- | --- |
| 参检插件 | 57 |
| **可用（能搜 + 能取到播放地址）** | **20** |
| 搜索无结果 | 12 |
| 搜得到但取不到播放地址 | 18 |
| 搜索失败（插件报错 / 异步未结算 / 超时） | 7 |
| 没声明 search | 0 |
| 未体检 | 0 |

0.0.4 的体检把 57 个里的 43 个标成「导入成功」，但那份体检只判断
`search` 返回的 JSON 是不是 null —— 空数组、Promise 没结算、字段名对不上都能过。
这一版改成真搜 + 真取源，可用数降到 **20** 才是装机后用户实际能看到的结果。

## 可用插件（20）

| 插件 | 版本 | platform | 搜索类型 | 首条结果 | 播放地址域名 | 耗时 |
| --- | --- | --- | --- | --- | --- | --- |
| 172Mix | 1.2.1 | 172Mix | music | Dj机长云翔-全国语ProgHouse音乐周杰伦全私货仙剑飘弹鼓专辑172Mix串烧 | mp3.172mix.com | 1694 ms |
| 36DJ | 1.2.2 | 36DJ | music | 滦南Dj阿龙精心打造Jay（周杰伦）舞曲大碟 | tn1.72djapp.cn | 1508 ms |
| B站 | 1.1.4 | bilibili | music | 【无损音质】周杰伦歌曲精选100首 歌词版纯享 地表最高音质 每一首都是顶级文案 中国流行音乐开拓者JAY | dvh4231e.edge.mountaintoys.cn | 1250 ms |
| CCTV听音 | 1.0.2 | cctv | music | [我的爱对你说]歌曲《龙卷风》 演唱：周杰伦 | dhls.cntv.lxdns.com | 1627 ms |
| DJ766 | 1.4.1 | DJ766 | music | 周杰伦- 伤感歌曲Mash Up(DjKansas Melbourne Rmx) | pan.urlkj.com | 1864 ms |
| DJKK | 1.1.2 | DJKK | music | 周杰伦-花海 | st.pgdjz.fun | 3122 ms |
| DJ耶耶耶 | 1.1.2 | DJ耶耶耶 | music | 周杰伦-说好的幸福呢-Myiok Proressive 2026 男声 |  | 2834 ms |
| JOOX音乐 | 1.1.3 | joox | music | 擱淺 | ak-hk.stream.music.joox.com | 2839 ms |
| MixDJ | 1.0.2 | MixDJ | music | 淘金小镇（小博 SE7EN Bootleg 2026） | www.mixdj.cn | 3560 ms |
| 先上DJ | 1.1.1 | 先上DJ | music | 六靖DJ刘波VsDJ一个球-全国语FunkyHouse音乐打造2026伤感车载串烧v32 | listen.dj1234.com | 980 ms |
| 咪咕音乐 | 1.9.19 | migu | music/album/artist/sheet/lyric | 圣诞星（feat. 杨瑞代） | freetyst.nf.migu.cn | 1582 ms |
| 好听轻音乐 | 1.2.3 | 好听轻音乐 | music | 夜曲(很伤感的纯音乐) (Nocturne) | s1.htqyy.com | 615 ms |
| 小熊电音 | 1.0.2 | 小熊电音 | music | 132 - 周杰伦 - Mojito(Djyork耀Dj杰哥 Electro 国语男) | pan.urlkj.com | 1836 ms |
| 我是DJ | 1.1.2 | 我是DJ | music | 周杰伦 - 分裂 (DJ阿福 ProgHouse Rmx) | cdn.54dj.vip | 1155 ms |
| 波比DJ | 1.0.1 | 波比DJ | music | 周杰伦 - 彩虹(Jacky-11 Bounce Mix 国语男) | p2.bobidj.com | 432 ms |
| 清风DJ | 1.2.2 | 清风DJ | music | 周杰伦 - 发如雪 3 叮当竹子(ARS VinaHouse Mix) | pan.urlkj.com | 1296 ms |
| 电音阁 | 1.1.2 | 电音阁 | music | DJAS轩总 - 湘E篙总专属定制（爱的海洋+泪海+还有我全中文Funky House）70分钟DJ串烧 | ys.dianyinge.com | 888 ms |
| 皮狗DJ | 1.1.2 | 皮狗DJ | music | 周杰伦-花海 | st.pgdjz.fun | 5128 ms |
| 魅力DJ | 1.0.1 | 魅力DJ | music | 周杰伦_世界末日_[DJ贺仔_Rmx_2017] | www.djmei.com | 629 ms |
| 黑色音频 | 1.1.2 | 黑色音频 | music | 袁咏琳、周杰伦 - 画沙（DjChotto vs DjK1 ProgHouse Mix国语合唱） | m4a.hy57.com | 1891 ms |

## 搜索失败（7）

| 插件 | 版本 | platform | 原因 |
| --- | --- | --- | --- |
| Apple Music | 1.0.2 | apple-music | com.guangyue.core.plugin.runtime.PluginRuntimeException: apple token step1 failed: index-legacy js not found in homepage at extractJsPath (plugin.js:2:17954) at <anonymous> (plugin.js:2:18953) at gySe |
| DJ多多 | 1.0.2 | djduoduo | com.guangyue.core.plugin.runtime.PluginRuntimeException: djduoduo: sch 响应解密失败 at <anonymous> (plugin.js:2:52361) at gySettle (bridge.js:450:21) at <anonymous> (bridge.js:431:44) at apiGet (plugin.js:2 |
| Jamendo | 1.0.2 | jamendo | com.guangyue.core.plugin.runtime.PluginRuntimeException: 未配置 Jamendo Client ID：请在插件设置中填写（在 https://developer.jamendo.com 注册并在 My Apps 创建应用即可免费获取） at getClientId (plugin.js:2:7786) at gLrkA (plugin.js: |
| SpaceDJ | 1.1.2 | SpaceDJ | 插件内部报错（函数不存在） |
| 水晶舞曲 | 1.2.2 | 水晶舞曲 | 插件超时没返回 |
| 番茄畅听 | 1.2.2 | fanqie | com.guangyue.core.plugin.runtime.PluginRuntimeException: 搜索失败 code=6000 at apiSearch (plugin.js:2:17847) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201) at com.guangyue.co |
| 相声评书 | 1.0.3 | 相声评书 | 插件超时没返回 |

## 搜索无结果（12）

| 插件 | 版本 | platform | 原因 |
| --- | --- | --- | --- |
| 73DJ | 1.0.1 | 73DJ | 搜「周杰伦」没有结果 |
| DJ6 | 1.0.2 | DJ6 | 搜「周杰伦」没有结果 |
| DJ串烧集 | 1.0.1 | djcsj | 搜「周杰伦」没有结果 |
| DJ娱乐 | 1.2.1 | DJ娱乐 | 搜「周杰伦」没有结果 |
| QQDJ | 1.2.2 | QQDJ | 搜「周杰伦」没有结果 |
| 一听音乐 | 1.0.2 | 1ting | 搜「周杰伦」没有结果 |
| 千千音乐 | 1.0.4 | qianqian | 搜「周杰伦」没有结果 |
| 博看听书 | 1.0.2 | bookan | 搜「周杰伦」没有结果 |
| 快音 | 1.0.2 | 快音 | 搜「周杰伦」没有结果 |
| 懒人听书 | 1.0.3 | lrts | 搜「周杰伦」没有结果 |
| 网易云电台 | 1.1.3 | neteaseradio | 搜「周杰伦」没有结果 |
| 聚合 | 1.9.18 | agg | 搜「周杰伦」没有结果 |

## 搜得到但取不到播放地址（18）

| 插件 | 版本 | platform | 原因 |
| --- | --- | --- | --- |
| 5sing | 1.0.2 | 5sing | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 5sing：未取得有效播放链接（hqurl:魔数校验失败(HTTP 206)；hqurl_backup:魔数校验失败(HTTP 206)；squrl:魔数校验失败(HTTP 206)；squrl_backup:魔数校验失败(HTTP 206)；lqurl:魔数校验失败(HTTP 206 |
| 82DJ | 1.2.1 | 82DJ | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 82DJ 直链未通过音频校验（可能被防盗链或站点返回了广告页） at getMediaSource (plugin.js:2:45678) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:2 |
| DJ秀 | 1.0.4 | DJ秀 | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: DJ秀 音源文件头下载为空 at getMediaSource (plugin.js:2:39001) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201) at com.guangyu |
| QQ音乐 | 1.9.26 | qq | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 聚合取链失败：所有可用源均未取得播放链接 at _0x32ce07 (plugin.js:2:442400) at <anonymous> (plugin.js:2:440926) at com.guangyue.core.plugin.runtime.PluginRuntime.ca |
| Y2002电音 | 1.0.2 | y2002 | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: [y2002] 魔数不符: 期望 m4a 实际 未知 at <anonymous> (plugin.js:2:63636) at gySettle (bridge.js:450:21) at <anonymous> (bridge.js:431:44) at verifyMedia ( |
| djuu DJ | 1.1.2 | djuu | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: djuu: 返回内容非音频（魔数 "65,65,65"） at <anonymous> (plugin.js:2:36904) at gySettle (bridge.js:450:21) at <anonymous> (bridge.js:431:44) at probeAudioH |
| 华为音乐 | 1.0.3 | 华为音乐 | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: axios: url is required at request (bridge.js:545:30) at withUrl (bridge.js:665:7) at <anonymous> (bridge.js:667:34) at probeAudioHead (plugin.j |
| 喜马拉雅 | 1.2.1 | ximalaya | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 取链失败：免费/付费通道均未获得通过校验的直链（trackId=310953141） at getMediaSourceImpl (plugin.js:2:64253) at com.guangyue.core.plugin.runtime.PluginRuntime.call(Plu |
| 宝贝DJ | 1.2.1 | 宝贝DJ | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 宝贝DJ 直链未通过音频校验（可能被防盗链或返回了广告页） at getMediaSource (plugin.js:2:25884) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201 |
| 汽水音乐 | 1.9.23 | qishui | getMediaSource 失败：com.dokar.quickjs.QuickJsException: Error: qishui sign 全部端点失败（2 个） at _0x53cbea (plugin.js:2:92854) at xuccH (plugin.js:2:92627) at <anonymous> (plugin.js:2:93271) at gySettle (bridge.js:443:35) at <a |
| 火龙DJ | 1.0.4 | huole | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: probe: empty body at verifyRemoteAudio (plugin.js:2:38033) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201) at com. |
| 猫耳FM | 1.0.3 | 猫耳FM | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: HLS 首分片魔数校验未通过（非 fMP4） at verifyHlsCandidate (plugin.js:2:35124) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201) a |
| 网易云音乐 | 1.9.27 | netease | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 聚合取链失败：所有可用源均未取得播放链接 at _0x248639 (plugin.js:2:219277) at JNQdi (plugin.js:2:216736) at <anonymous> (plugin.js:2:222258) at com.guangyue.core.p |
| 荔枝FM | 1.0.2 | lizhi | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 荔枝FM取链校验未通过：响应体过短(0B) at probeMedia (plugin.js:2:21342) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt:201) at com.gua |
| 蜻蜓FM | 1.0.3 | qingting | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 候选 tier 24 在所有 url 上未通过魔数/大小校验 at getMediaSourceImpl (plugin.js:2:61743) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.k |
| 街声 | 1.0.1 | streetvoice | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 街声取链失败：首分片魔数校验未通过(82,48,65,82) at resolveHlsSource (plugin.js:2:37050) at com.guangyue.core.plugin.runtime.PluginRuntime.call(PluginRuntime.kt: |
| 酷我音乐 | 1.9.24 | kuwo | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 聚合取链失败：所有可用源均未取得播放链接 at _0x451256 (plugin.js:2:252126) at HtcYc (plugin.js:2:249926) at <anonymous> (plugin.js:2:253942) at com.guangyue.core.p |
| 酷狗音乐 | 1.9.24 | kugou | getMediaSource 失败：com.guangyue.core.plugin.runtime.PluginRuntimeException: 酷狗取链失败：未取得播放链接 at _0x3846c6 (plugin.js:2:403919) at WjZUw (plugin.js:2:401704) at <anonymous> (plugin.js:2:406437) at com.guangyue.core.plugin. |

## 没声明 search（0）

_无_

## 未体检（0）

_无_

## 说明

- 判据与实际 App 路径完全一致：`PluginRuntime.call` → `SearchParser.parse` → `getMediaSource`。
  映射层（`data/isEnd`、`title/artist/album/artwork/id`、秒/毫秒）由
  `SearchParserTest` 单测覆盖，本清单只记录真插件跑通与否。
- 结果由 `PluginUsableTest` 逐插件产生（`build/plugin-cache-0.0.4/usable-report.jsonl`），
  本文件由 `PluginUsableReportTest` 汇总，可重跑复现。
- 「搜索无结果」多为源站改版或地区限制；「搜得到但取不到播放地址」说明该源搜索可用但
  取链接被墙/需要会员，App 里会提示「换其他音源搜同名歌曲」。
- 8 个 `async` 长调用插件（B站、火龙DJ、DJ串烧集、蜻蜓FM、喜马拉雅、皮狗DJ、DJKK、82DJ）
  仍未改成真异步任务队列，见 `docs/async-queue-0.0.7.md` 与 CHANGELOG 的延期说明。

