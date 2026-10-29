/**
 * 广乐 demo 插件（demo-plugin.js）
 *
 * 只用来联调「搜索 → 播放 → 歌词 → 全屏歌词」这条链，用的是 SoundHelix 公开的示例
 * MP3（soundhelix.com，作者 T. Schürger，公开提供用于演示）与本文手写的 LRC。
 *
 * 本插件不访问任何第三方音乐平台，不解析任何平台直链，不包含任何平台适配或 VIP 绕过逻辑。
 *
 * 安装：广乐 → 插件 → 从链接安装 / 从文件安装，选中本文件即可。
 * 协议：MusicFree 插件协议（CommonJS），见 docs/PLUGIN_API.md。
 */
const TRACKS = [
  {
    id: 'demo-1',
    title: '晨光小段',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    duration: 372000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
    lrc: [
      '[ti:晨光小段]',
      '[ar:SoundHelix 演示]',
      '[al:广乐演示曲目]',
      '[offset:0]',
      '[00:00.00]晨光小段 · 演示歌词',
      '[00:04.00]第一行从这里开始',
      '[00:09.50]把音量调小一点',
      '[00:15.00]街角的风在响',
      '[00:21.00]我们慢慢往前走',
      '[00:27.50]不必急着回家',
      '[00:34.00]路灯一盏一盏亮起来',
      '[00:41.00]影子被拉得很长',
      '[00:48.00]如果你也听见了',
      '[00:55.00]就跟着节奏点头',
      '[01:02.00]这一段没有歌词',
      '[01:09.00]只有呼吸和风声',
      '[01:16.50]把这一刻留住',
      '[01:24.00]留到下一次见面',
      '[01:32.00]副歌在这里',
      '[01:40.00]重复一遍',
      '[01:48.00]我们慢慢往前走',
      '[01:56.00]不必急着回家',
      '[02:05.00]天色要暗下来了',
      '[02:14.00]再走一小段就好',
      '[02:23.00]再走一小段就好',
    ].join('\n'),
  },
  {
    id: 'demo-2',
    title: '午后的窗',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    duration: 300000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
    lrc: [
      '[ti:午后的窗]',
      '[ar:SoundHelix 演示]',
      '[offset:-200]',
      '[00:00.00]午后的窗 · 演示歌词',
      '[00:06.00][00:42.00]阳光落在桌面上',
      '[00:12.00][00:48.00]尘埃在光里漂浮',
      '[00:19.00]一杯水 一个下午',
      '[00:26.00]什么都不必说',
      '[00:34.00]窗帘被风掀起一角',
      '[00:55.00]远处有人在笑',
      '[01:04.00]就这样待着吧',
      '[01:13.00]让时间自己走',
    ].join('\n'),
  },
  {
    id: 'demo-3',
    title: '夜行',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    duration: 391000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
    lrc: [
      '[ti:夜行]',
      '[ar:SoundHelix 演示]',
      '[offset:0]',
      '[00:00.00]夜行 · 演示歌词',
      '[00:08.00]末班车开走了',
      '[00:16.00]站台上只剩我一个',
      '[00:25.00]手机屏幕亮了一下',
      '[00:34.00]不是你要找的人',
      '[00:44.00]沿着铁轨往前走',
      '[00:54.00]两旁是没关灯的窗',
      '[01:05.00]每一扇后面都有人',
      '[01:16.00]每一个故事都不相同',
      '[01:28.00]夜很长 路也很长',
      '[01:40.00]但总会走到天亮',
    ].join('\n'),
  },
  {
    id: 'demo-4',
    title: '海边的信号',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    duration: 340000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3',
    lrc: [
      '[ti:海边的信号]',
      '[ar:SoundHelix 演示]',
      '[00:00.00]海边的信号 · 演示歌词',
      '[00:07.00]天线在海风里摇',
      '[00:15.00]收到的都是噪声',
      '[00:24.00]你说那边在下雨',
      '[00:33.00]我这里太阳很大',
      '[00:43.00]把频率调低一点',
      '[00:52.00]也许能听见你',
    ].join('\n'),
  },
  {
    id: 'demo-5',
    title: '慢速练习',
    artist: 'SoundHelix 演示',
    album: '广乐演示曲目',
    duration: 318000,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3',
    lrc: [
      '[ti:慢速练习]',
      '[ar:SoundHelix 演示]',
      '[00:00.00]慢速练习 · 演示歌词',
      '[00:05.00]先把手放下来',
      '[00:12.00]再慢慢抬起来',
      '[00:20.00]不用做对',
      '[00:28.00]只要做完',
      '[00:36.00]再一遍',
      '[00:45.00]再来一遍',
    ].join('\n'),
  },
];

const COVER = 'https://www.soundhelix.com/favicon.ico';

const toItem = (t) => ({
  id: t.id,
  platform: 'demo',
  title: t.title,
  artist: t.artist,
  album: t.album,
  artwork: COVER,
  duration: t.duration,
  qualitys: ['standard', 'high'],
});

module.exports = {
  platform: 'demo',
  version: '1.1.0',
  author: '广乐',
  name: '广乐演示音源',
  description: '离线可用的演示插件：SoundHelix 公开示例音频 + 手写 LRC，用来联调搜索、播放与歌词。',
  srcUrl: 'https://raw.githubusercontent.com/ShuiLiZe/GuangYue/main/docs/demo-plugin.js',
  cacheControl: 'no-cache',
  supportedSearchType: ['music', 'album', 'artist'],
  defaultSearchType: 'music',

  search(query, page, type) {
    if (type && type !== 'music') return { isEnd: true, data: [] };
    const keyword = (query || '').trim();
    const data = TRACKS
      .filter((t) => !keyword || t.title.includes(keyword) || t.artist.includes(keyword))
      .map(toItem);
    return { isEnd: true, data };
  },

  getMediaSource(musicItem, quality) {
    const track = TRACKS.find((t) => t.id === musicItem.id) || TRACKS[0];
    return { url: track.url };
  },

  getLyric(musicItem) {
    const track = TRACKS.find((t) => t.id === musicItem.id);
    return { rawLrc: track ? track.lrc : '' };
  },

  getMusicInfo(musicItem) {
    const track = TRACKS.find((t) => t.id === musicItem.id) || TRACKS[0];
    return toItem(track);
  },

  getTopLists() {
    return [
      {
        title: '广乐演示',
        data: [{ id: 'demo-top', platform: 'demo', title: '演示曲目', description: 'SoundHelix 公开示例音频' }],
      },
    ];
  },

  getTopListDetail(topListItem, page) {
    return { isEnd: true, data: TRACKS.map(toItem) };
  },
};
