/**
 * 广乐 demo 插件（demo-plugin.js）
 *
 * 用途：联调「搜索 → 播放 → 歌词」全链路。
 * 内容：只有 SoundHelix 的公开免版权示例 MP3，以及本文件里手写的 LRC 歌词。
 * 本插件不访问任何第三方音乐平台，也不解析任何平台直链。
 *
 * 安装：广乐 → 插件 → 从文件安装 → 选中本文件。
 * 协议：MusicFree 插件协议（CommonJS），见 docs/PLUGIN_API.md。
 */
module.exports = {
  platform: 'demo',
  version: '1.0.0',
  appVersion: '0.1.0',
  author: '广乐',
  srcUrl: 'https://raw.githubusercontent.com/YG224586/GuangYue/main/docs/demo-plugin.js',
  cacheControl: 'no-cache',
  // low / standard / high / super —— demo 只有标准音质
  supportedSearchType: ['music', 'album', 'artist'],
  defaultSearchType: 'music',

  /**
   * @param {string} query 关键词
   * @param {number} page 从 1 开始
   * @param {string} type 'music' | 'album' | 'artist' | 'sheet'
   */
  search(query, page, type) {
    if (type && type !== 'music') {
      return { isEnd: true, data: [] };
    }
    const keyword = (query || '').trim();
    const data = tracks
      .filter((t) => !keyword || t.title.includes(keyword) || t.artist.includes(keyword))
      .map((t) => ({
        id: t.id,
        platform: 'demo',
        title: t.title,
        artist: t.artist,
        album: t.album,
        artwork: t.artwork,
        duration: t.duration,
        qualitys: ['standard'],
      }));
    return { isEnd: true, data };
  },

  /** 播放时才解析直链。广乐拿到 url 后交给 Media3。 */
  getMediaSource(musicItem, quality) {
    const t = tracks.find((x) => x.id === musicItem.id) || tracks[0];
    return { url: t.url, quality: quality || 'standard' };
  },

  getLyric(musicItem) {
    const t = tracks.find((x) => x.id === musicItem.id);
    return { rawLrc: t ? t.lrc : '' };
  },

  getMusicInfo(musicItem) {
    const t = tracks.find((x) => x.id === musicItem.id) || tracks[0];
    return { id: t.id, title: t.title, artist: t.artist, album: t.album, artwork: t.artwork, duration: t.duration };
  },
};

// ---------------------------------------------------------------------------
/**
 * 一组可以立刻播放的示例曲目。
 *
 * MusicFree 的榜单/推荐位协议里，插件就是这样把一页歌交给宿主：返回 `{ isEnd, data: [...] }`。
 * 广乐用它给「歌单」Tab 铺一组示例曲目，同时也是返回标准结构的范例。
 */
module.exports.getRecommendSheetDetail = function (sheetItem, page) {
  return { isEnd: true, sheetItem: sheetItem, data: allItems() };
};

/** 频道/榜单的父级列表（MusicFree `getTopLists`，返回 `[{ title, data: [topListItem] }]`）。 */
module.exports.getTopLists = function () {
  return [
    {
      title: '广乐示例',
      data: [
        { id: 'demo-top', platform: 'demo', title: '演示曲目', description: 'SoundHelix 公开示例音频' },
      ],
    },
  ];
};

/** 「把这批歌给我」的第二条通道：MusicFree `getTopListDetail`。 */
module.exports.getTopListDetail = function (topListItem, page) {
  return { isEnd: true, data: allItems() };
};

function allItems() {
  return tracks.map(function (t) {
    return {
      id: t.id,
      platform: 'demo',
      title: t.title,
      artist: t.artist,
      album: t.album,
      artwork: t.artwork,
      duration: t.duration,
      qualitys: ['standard'],
    };
  });
}
// 数据：SoundHelix 公开示例 MP3（soundhelix.com，作者 T. Schürger，公开发布供演示使用）
// ---------------------------------------------------------------------------
const tracks = [
  {
    id: 'demo-1',
    title: '晨光小段',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    artwork: null,
    duration: 372000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
    lrc: `[ti:晨光小段]
[ar:SoundHelix 演示]
[al:广乐演示曲目]
[offset:0]
[00:00.00]晨光小段 · 演示歌词
[00:04.00]第一行从这里开始
[00:09.50]把音量调小一点
[00:15.00]街角的风在响
[00:21.00]我们慢慢往前走
[00:27.50]不必急着回家
[00:34.00]路灯一盏一盏亮起来
[00:41.00]影子被拉得很长
[00:48.00]如果你也听见了
[00:55.00]就跟着节拍点头
[01:02.00]这一段没有歌词
[01:09.00]只有呼吸和风声
[01:16.50]把这一刻留住
[01:24.00]留到下一次见面
[01:32.00]副歌在这里
[01:40.00]重复一遍
[01:48.00]我们慢慢往前走
[01:56.00]不必急着回家
[02:05.00]天色要暗下来了
[02:14.00]再走一小段就好
[02:23.00]再走一小段就好`,
  },
  {
    id: 'demo-2',
    title: '午后的窗',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    artwork: null,
    duration: 300000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
    lrc: `[ti:午后的窗]
[ti:午后的窗]
[ar:SoundHelix 演示]
[offset:-200]
[00:00.00]午后的窗 演示歌词
[00:06.00][00:42.00]阳光落在桌面上
[00:12.00][00:48.00]尘埃在光里漂浮
[00:19.00]一杯水 一个下午
[00:26.00]什么都不必说
[00:34.00]窗帘被风掀起一角
[00:55.00]远处有人在笑
[01:04.00]就这样待着吧
[01:13.00]让时间自己走`,
  },
  {
    id: 'demo-3',
    title: '夜行',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    artwork: null,
    duration: 391000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
    lrc: `[ti:夜行]
[ar:SoundHelix 演示]
[offset:0]
[00:00.00]夜行 · 演示歌词
[00:08.00]末班车开走了
[00:16.00]站台上只剩我一个
[00:25.00]手机屏幕亮了一下
[00:34.00]不是你要找的人
[00:44.00]沿着铁轨往前走
[00:54.00]两旁是没关灯的窗
[01:05.00]每一扇后面都有人
[01:16.00]每一个故事都不相同
[01:28.00]夜很长 路也很长
[01:40.00]但总会走到天亮`,
  },
];
