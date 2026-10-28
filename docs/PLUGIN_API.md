# 广乐插件 API（MusicFree 协议兼容）

广乐只做**通用播放壳 + 插件运行时**。App 不内置、不预装任何音源插件；每个插件都由使用者自己在「插件管理」页从本地文件或 URL 安装。插件就是一个 CommonJS 的 `.js` 文本文件，`module.exports` 一个对象。

> 边界声明：本项目不提供任何第三方平台的音源插件，也不提供代理中转、绕过付费/VIP 的取流逻辑。本文档只描述协议本身，方便你自己写或装你信得过的插件。

---

## 1. 安装方式

| 方式 | 入口 | 说明 |
| --- | --- | --- |
| 本地文件 | 插件页 →「从文件安装」 | 系统文件选择器，选任意 `.js` 文本文件 |
| 网络地址 | 插件页 →「从链接安装」 | 填 `https://…/xxx.js`，广乐下载后校验再保存 |
| 更新 | 插件条目「检查更新」 | 读取插件里的 `srcUrl`，拉取后比对 `version` |

插件保存位置：`filesDir/plugins/<pluginId>/`，索引在 `filesDir/plugins/index.json`。

安装时会做一次沙箱探针：真正跑一遍 `module.exports`，读不到元信息、或不是合法插件对象（例如误选了 HTML 错误页）就直接拒绝，不落盘。

## 2. 插件元信息

`module.exports` 上可以带这些字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `platform` | `string` | ✅ | 平台标识，唯一区分来源，例如 `demo` |
| `version` | `string` | ✅ | 语义化版本，更新检查用 |
| `name` / `hint` | `string` | | 显示名与说明 |
| `author` | `string` | | 作者 |
| `srcUrl` | `string` | | 插件自身的下载地址，用于「检查更新」 |
| `supportedSearchType` | `string[]` | | `music` / `album` / `artist` / `sheet`，默认 `['music']` |
| `defaultSearchType` | `string` | | 默认搜索类型 |
| `cacheControl` | `string` | | `no-cache` / `no-store` 时搜索结果不缓存 |
| `appVersion` | `string` | | 目标 MusicFree 版本，仅展示 |
| `primaryKey` | `string[]` | | 自定义主键字段，缺省 `platform + id` |

`quality` 枚举：`low` / `standard` / `high` / `super`。

## 3. 函数签名

所有函数都是 `async`，参数是普通对象，返回普通对象或 `null`。返回 `null` 表示「本插件不支持该能力」，广乐会**优雅降级**（列表留空、不报错）。某个函数根本没写，效果一样。

### `search(query, page, type)`
```js
async search(query, page, type) {
  return { isEnd: true, data: [musicItem, …] };
}
```
`page` 从 1 开始；`isEnd` 为真表示没有下一页。

### `getMediaSource(musicItem, quality)`
仅在有播放意图时才调用（懒解析）。返回：
```js
{ url: 'https://…/x.mp3', quality: 'standard', headers: { Referer: '…' } }
```
也支持分段/多音轨：`{ url: [{ url, bitrate }] }`。解析失败会**自动重取一次**。

### `getLyric(musicItem)`
```js
{ rawLrc: '[00:01.00]第一行', translation: '[00:01.00]译' }
```
`rawLrc` 支持多时间戳行 `[00:01.00][00:20.00]同一句`、`[offset:±毫秒]`、`[ti:]/[ar:]/[al:]`。

### 其它（可选，缺了自动降级）
- `getMusicInfo(musicItem)` → 补全单曲信息
- `getAlbumInfo(albumItem, page)` → `{ isEnd, albumItem, musicList }`
- `getArtistWorks(artistItem, page, type)` → `{ isEnd, data }`
- `importMusicSheet(url, importSource)` → **返回歌单链接数组**（不是歌单内容）
- `importMusicItem(url)` → 单曲信息
- `getTopLists()` → 排行榜分类 `[{ title, data: [{ id, title }] }]`
- `getTopListDetail(topListItem, page)` → `{ isEnd, musicList }`
- `getRecommendSheetTags()` / `getRecommendSheetsByTag(tag, page)` → 推荐歌单

## 4. musicItem 结构

```js
{
  id: 'demo-1',
  platform: 'demo',
  title: '晨光小段',
  artist: 'SoundHelix 演示',
  album: '广乐演示曲目',
  artwork: 'https://…/cover.jpg',
  duration: 372000,          // 毫秒
  qualitys: ['standard'],    // 可选
}
```

## 5. 内置垫片（启动时注入，全局可用）

每个插件有**独立** QuickJS runtime，启动时先注入 `assets/prelude.js`（esbuild 打好的 bundle），再把插件代码和 `module.exports` 垫片注入。

| 全局 | 版本 | 说明 |
| --- | --- | --- |
| `cheerio` | 1.2.0 | HTML 解析，`cheerio.load(html)` |
| `CryptoJS` | 4.1.1 | MD5 / AES / HmacSHA256 / Base64 … |
| `dayjs` | 1.11.23 | 日期时间 |
| `Qs` | 6.17.0 | query string 编解码 |
| `he` | 2.0.0 | HTML 实体解码 |
| `BigInteger` | 1.6.52 | 大整数 |
| `axios(config)` | — | 映射到 OkHttp，见下 |
| `console` | — | `log/info/warn/error/debug` → 插件日志页 |
| `setTimeout` / `clearTimeout` / `setInterval` / `clearInterval` | — | 定时器 |
| `Buffer` | — | `from` / `toString` / `concat` / subarray 基础实现 |

### axios 映射
```js
const res = await axios({
  method: 'GET',            // 小写亦可
  url: 'https://…',
  headers: { 'User-Agent': '…' },
  params: { q: '关键词' },    // → URL query
  data: { a: 1 },            // POST body，对象自动 JSON
  responseType: 'json',      // json | text | arraybuffer
  timeout: 15000,
});
res.data          // 已按 responseType 解析
res.status / res.headers
```
`arraybuffer` 返回的 `data` 是一个可下标访问的字节数组，带 `.byteLength`。

## 6. 运行时限制

- 每个插件一个独立 runtime，进程内互不可见。
- 单次调用超时 **15 秒**，超时抛错，宿主捕获后返回降级值。
- runtime 有内存上限，超限即中断该次调用。
- 网络只经 `axios` 垫片；插件不能直接读文件、不能用 Android 原生 API。
- 搜索结果有 TTL 缓存（默认 5 分钟，`cacheControl: no-cache|no-store` 则关闭）。

## 7. 最小可用插件

```js
module.exports = {
  platform: 'hello',
  version: '1.0.0',
  supportedSearchType: ['music'],
  async search(query) {
    return { isEnd: true, data: [{
      id: '1', platform: 'hello', title: query, artist: '未知', duration: 60000,
    }] };
  },
  async getMediaSource(musicItem) {
    return { url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3' };
  },
  async getLyric() {
    return { rawLrc: '[00:00.00]你好，这里是歌词' };
  },
};
```

完整示例见 `docs/demo-plugin.js`：只有 SoundHelix 公开示例音频与手写 LRC，用来联调「搜索 → 播放 → 歌词」。
