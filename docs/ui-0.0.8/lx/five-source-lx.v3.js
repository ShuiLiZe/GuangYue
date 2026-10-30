/**
 * GuangYue 0.0.8 M1c LX chain script (deterministic rewrite of five-source-lx.js)
 *
 * Why: the original chain did blind cross-source fallback -- it handed one
 * source's song id to another source (kuwo rid=118990 -> kugou Bad hash ->
 * QQ -> wy -> mg), and kuwo answers unknown rid with url="None". Result: every
 * kuwo search result was unplayable ("取不到播放地址").
 *
 * Order is now deterministic:
 *  1. own source, own id (validated per source);
 *  2. own source, quality downgrade (320k -> 128k -> ...);
 *  3. other sources ONLY after searching that source for the same song and
 *     getting that source's own id. No match => honest failure.
 */
const lxApi = globalThis.lx || {}
const EVENT_NAMES = lxApi.EVENT_NAMES || { request: 'request', inited: 'inited' }
const request = lxApi.request || globalThis.request
// lx 运行时把 crypto 放在 globalThis.lx.utils 下（不是全局 lxUtils）
const lxUtils = (globalThis.lx && globalThis.lx.utils) || globalThis.lxUtils || {}
const md5 = (s) => (lxUtils.crypto ? lxUtils.crypto.md5(String(s)) : '')

function req (url, options) {
  return new Promise((resolve, reject) => {
    request(url, options || { method: 'GET', timeout: 15000 }, (err, resp) => {
      if (err) return reject(new Error(String((err && err.message) || err)))
      resolve(resp || {})
    })
  })
}
function need (cond, msg) { if (!cond) throw new Error(msg) }

// ---------------------------------------------------------------- kw (kuwo)
const KW_BR = { '128k': '128kmp3', '320k': '320kmp3', flac: '2000kflac', flac24bit: '4000kflac' }
async function kwFetch (rid, br) {
  const url = 'http://nmobi.kuwo.cn/mobi.s?f=web&source=kwplayer_ar_5.1.0.0_B_jiakong_vh.apk'
    + '&type=convert_url_with_sign&br=' + br + '&rid=' + rid
  const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA, Referer: 'http://kuwo.cn/' } })
  return (resp.body && resp.body.data) || null
}
// Kuwo answers unlicensed tracks with an 11~30s preview clip (duration < 60) instead of
// failing. Playing that clip makes the queue auto-skip every 11s, so upgrade it: search the
// same source for the same song, take the longest playable full-length version.
async function kwLongVersion (song, br) {
  const q = [song && (song.name || song.songname || song.title), song && (song.singer || song.artist)]
    .filter(Boolean).join(' ').trim()
  if (!q) return null
  let list = []
  try {
    const url = 'http://search.kuwo.cn/r.s?all=' + encodeURIComponent(q)
      + '&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA, Referer: 'http://kuwo.cn/' } })
    list = (resp.body && (resp.body.abslist || resp.body.songlist)) || []
  } catch (e) { return null }
  const cands = list
    .map((s) => ({ rid: String(s.MUSICRID || s.musicrid || '').replace(/^MUSIC_/i, ''), dur: Number(s.DURATION || 0) }))
    .filter((c) => /^[0-9]+$/.test(c.rid) && c.dur >= 90)
    .sort((a, b) => b.dur - a.dur)
    .slice(0, 3)
  for (const c of cands) {
    try {
      const d = await kwFetch(c.rid, br)
      if (d && d.url && /^https?:\/\//.test(String(d.url)) && Number(d.duration || 0) >= 90) {
        return String(d.url).replace(/\\u0026/g, '&')
      }
    } catch (e) { /* try next candidate */ }
  }
  return null
}
async function kwUrl (song, quality) {
  const br = KW_BR[quality]
  need(br, 'kuwo: unsupported quality ' + quality)
  const rid = String((song && (song.rid || song.songmid || song.songId)) || '').replace(/^(MUSIC_|MP3_)/i, '').replace(/^kuwo_/i, '')
  need(/^[0-9]+$/.test(rid), 'kuwo: rid is not a kuwo numeric id (' + rid + ')')
  const d = await kwFetch(rid, br)
  need(d && d.url, 'kuwo: no url in reply')
  need(/^https?:\/\//.test(String(d.url)), 'kuwo: track not playable (format=' + d.format + ' bitrate=' + d.bitrate + ')')
  const dur = Number(d.duration || 0)
  if (dur > 0 && dur < 60) {
    const full = await kwLongVersion(song, br)
    if (full) return full
  }
  return String(d.url).replace(/\\u0026/g, '&')
}

// ---------------------------------------------------------------- mg (migu)
const MG_TONE = { '128k': 'PQ', '320k': 'HQ', flac: 'SQ', flac24bit: 'ZQ' }
async function mgUrl (song, quality) {
  const tone = MG_TONE[quality]
  need(tone, 'migu: unsupported quality ' + quality)
  const contentId = String((song && (song.contentId || song.songId || song.songmid)) || '').trim()
  need(contentId, 'migu: missing contentId')
  const url = 'https://c.musicapp.migu.cn/MIGUM3.0/strategy/pc/listen/v1.0?contentId=' + encodeURIComponent(contentId)
    + '&resourceType=2&toneFlag=' + tone
  const resp = await req(url, { method: 'GET', headers: { channel: '0146951', uid: '0', Referer: 'https://music.migu.cn/', 'User-Agent': UA } })
  const body = resp.body
  need(body && body.code, 'migu: reply is not json')
  need(body.code === '000000' && body.data, 'migu: ' + (body.info || body.code))
  const playUrl = body.data.url || body.data.playUrl
  need(playUrl, 'migu: vip only at this quality')
  return String(playUrl)
}

// ---------------------------------------------------------------- kg (kugou)
async function kgUrl (song, quality) {
  const raw = String((song && (song.hash || song.fileHash)) || '')
  // 第三方插件给的 hash 常带来源前缀（`kugou_<hash>` / `kg_<hash>`），先剥离再校验
  const hash = (raw.replace(/^[a-z]+_/i, '') || raw)
  need(/^[0-9a-fA-F]{32}$/.test(hash), 'kugou: bad hash')
  for (const br of ['hq', 'free']) {
    const key = md5(hash + 'kgcloudv2')
    const url = 'https://trackercdn.kugou.com/i/v2/?key=' + key + '&hash=' + hash + '&br=' + br + '&appid=1005&pid=2&cmd=25&behavior=play'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA } })
    const body = resp.body
    if (body && body.status === 1 && body.url && body.url[0]) return String(body.url[0])
    if (body && body.status === 2) throw new Error('kugou: paid/vip only')
  }
  throw new Error('kugou: no playable url')
}

// ---------------------------------------------------------------- wy (netease)
const WY_BR = { '128k': 128000, '320k': 320000, flac: 999000, flac24bit: 1999000 }
let wyCookie = 'os=pc'
function wyEapi (url, object) {
  const key = 'e82ckenh8dichen8'
  const text = JSON.stringify(object)
  const digest = md5('nobody' + url + 'use' + text + 'md5forencrypt')
  const data = url + '-36cd479b6b5-' + text + '-36cd479b6b5-' + digest
  const enc = lxUtils.crypto.aesEncrypt(data, 'aes-128-ecb', key, '')
  let out = ''
  if (typeof enc === 'string') out = enc
  else {
    const bytes = enc && typeof enc.length === 'number' ? enc : []
    for (let i = 0; i < bytes.length; i++) out += ('0' + (bytes[i] & 0xff).toString(16)).slice(-2)
  }
  return out.toUpperCase()
}
async function wyUrl (song, quality) {
  const id = String((song && (song.songmid || song.musicId || song.id)) || '').replace(/^netease_/, '')
  need(/^[0-9]+$/.test(id), 'netease: id is not numeric (' + id + ')')
  const br = WY_BR[quality]
  need(br, 'netease: unsupported quality ' + quality)
  const params = wyEapi('/api/song/enhance/player/url', { ids: '[' + id + ']', br: br })
  const resp = await req('https://interface3.music.163.com/eapi/song/enhance/player/url', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Cookie: wyCookie, 'User-Agent': UA, Referer: 'https://music.163.com/' },
    body: 'params=' + params
  })
  if (resp.headers && resp.headers.cookie) wyCookie = String(resp.headers.cookie)
  const d = resp.body && resp.body.data && resp.body.data[0]
  need(d, 'netease: no data')
  need(d.url && !d.freeTrialInfo, 'netease: no copyright / vip (code=' + d.code + ')')
  return String(d.url)
}

// ---------------------------------------------------------------- tx (QQ)
const TX_BR = { '128k': ['M500', '.mp3'], '320k': ['M800', '.mp3'], flac: ['F000', '.flac'], flac24bit: ['RS01', '.flac'] }
async function txUrl (song, quality) {
  const mid = String((song && (song.songmid || song.strMediaMid || song.mediaMid)) || '')
  const mediaMid = String((song && (song.strMediaMid || song.mediaMid || song.songmid)) || '')
  need(/^[0-9A-Za-z]{8,}$/.test(mediaMid), 'qq: bad songmid')
  const spec = TX_BR[quality] || TX_BR['128k']
  const payload = { req_0: { module: 'vkey.GetVkeyServer', method: 'CgiGetVkey', param: { guid: '10000', songmid: [mid], songtype: [0], uin: '0', loginflag: 1, platform: '20', filename: [spec[0] + mediaMid + spec[1]] } } }
  const resp = await req('https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=' + encodeURIComponent(JSON.stringify(payload)), { method: 'GET', headers: { 'User-Agent': UA, Referer: 'https://y.qq.com/' } })
  const body = resp.body
  const info = body && body.req_0 && body.req_0.data && body.req_0.data.midurlinfo && body.req_0.data.midurlinfo[0]
  need(info && info.purl, 'qq: no url without login (result=' + (info && info.result) + ')')
  const sip = (body.req_0.data.sip && body.req_0.data.sip[0]) || 'http://ws.stream.qqmusic.qq.com/'
  return sip + info.purl
}

// ---------------------------------------------------------------- same-song search
const SEARCHERS = {
  kw: async (q) => {
    const url = 'http://search.kuwo.cn/r.s?all=' + encodeURIComponent(q) + '&ft=music&itemset=web_2013&client=kt&pn=0&rn=5&rformat=json&encoding=utf8'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA } })
    const list = (resp.body && (resp.body.abslist || resp.body.songlist)) || []
    return list.map((s) => ({ rid: String(s.MUSICRID || s.musicrid || '').replace(/^MUSIC_/i, ''), name: s.SONGNAME || s.name, singer: s.ARTIST || s.singer }))
  },
  wy: async (q) => {
    const url = 'https://music.163.com/api/search/get/web?csrf_token=&s=' + encodeURIComponent(q) + '&type=1&offset=0&total=true&limit=5'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA, Referer: 'https://music.163.com/', Cookie: wyCookie } })
    const songs = (resp.body && resp.body.result && resp.body.result.songs) || []
    return songs.map((s) => ({ songmid: String(s.id), name: s.name, singer: (s.artists || []).map((a) => a.name).join(',') }))
  },
  mg: async (q) => {
    const url = 'https://m.music.migu.cn/migu/remoting/scr_search_tag?keyword=' + encodeURIComponent(q) + '&type=2&rows=5&pgc=1'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA, Referer: 'https://m.music.migu.cn/' } })
    const list = (resp.body && resp.body.musics) || []
    return list.map((s) => ({ contentId: String(s.copyrightId || s.id), name: s.songName, singer: s.singerName }))
  },
  kg: async (q) => {
    const url = 'https://songsearch.kugou.com/song_search_v2?keyword=' + encodeURIComponent(q) + '&page=1&pagesize=5&platform=WebFilter&userid=0&clientver=2000&iscorrection=1&privilege_filter=0'
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA } })
    const list = (resp.body && resp.body.data && resp.body.data.lists) || []
    return list.map((s) => ({ hash: String(s.FileHash || '').toLowerCase(), name: s.SongName, singer: s.SingerName }))
  },
  tx: async (q) => {
    const url = 'https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=5&w=' + encodeURIComponent(q)
    const resp = await req(url, { method: 'GET', headers: { 'User-Agent': UA, Referer: 'https://y.qq.com/' } })
    const list = (resp.body && resp.body.data && resp.body.data.song && resp.body.data.song.list) || []
    return list.map((s) => ({ songmid: s.mid, strMediaMid: s.media_mid, name: s.songname, singer: (s.singer || []).map((x) => x.name).join(',') }))
  }
}

const FETCHERS = { kw: kwUrl, kg: kgUrl, wy: wyUrl, tx: txUrl, mg: mgUrl }
const HOST_PLATFORM = { kuwo: 'kw', kugou: 'kg', qq: 'tx', netease: 'wy', migu: 'mg' }
const FALLBACK_ORDER = {
  '128k': ['128k'],
  '320k': ['320k', '128k'],
  flac: ['flac', '320k', '128k'],
  flac24bit: ['flac24bit', 'flac', '320k', '128k']
}
const SONG_KEYS = {
  kw: ['rid', 'songmid', 'songId', 'musicId'],
  wy: ['songmid', 'musicId', 'id'],
  mg: ['contentId', 'songId', 'copyrightId'],
  kg: ['hash', 'fileHash'],
  tx: ['strMediaMid', 'mediaMid', 'songmid']
}

function srcKey (platform) {
  const p = String(platform || '').toLowerCase()
  return HOST_PLATFORM[p] || (FETCHERS[p] ? p : null)
}

/** Drop source prefixes such as `kuwo_118990` / `netease_210049`. */
function stripPrefix (v, key) {
  let s = String(v == null ? '' : v).replace(/^(MUSIC_|MP3_)/i, '')
  const m = /^([a-z]+)_(.+)$/i.exec(s)
  if (m && srcKey(m[1]) === key) s = m[2]
  return s
}

/** Pick the id belonging to this source, in that source's own key order. */
function idFor (key, info) {
  const keys = SONG_KEYS[key] || []
  const pick = (k) => {
    const raw = info[k] != null ? info[k] : info['original_' + k]
    if (raw == null || raw === '') return ''
    const v = stripPrefix(raw, key)
    return v === 'undefined' || v === 'null' ? '' : v
  }
  for (const k of keys) { const v = pick(k); if (v) return v }
  return ''
}

function norm (s) { return String(s == null ? '' : s).toLowerCase().replace(/[\s\-_()（）·、,，.。!！?？'"]/g, '') }

/** Same song: titles contain each other, and at least one artist in common. */
function sameSong (cand, info) {
  if (!cand || !info) return false
  const cn = norm(cand.name)
  const tn = norm(info.name)
  if (!cn || !tn) return false
  if (!(cn === tn || cn.indexOf(tn) >= 0 || tn.indexOf(cn) >= 0)) return false
  const split = (v) => String(v == null ? '' : v).toLowerCase().split(/[,，、/&]+/).map(norm).filter(Boolean)
  const a = split(cand.singer)
  const b = split(info.singer)
  if (!a.length || !b.length) return true
  return a.some((x) => b.some((y) => x === y || x.indexOf(y) >= 0 || y.indexOf(x) >= 0))
}

function searchQuery (info) {
  const singer = String(info.singer || '').split(/[,，、/&]+/)[0].trim()
  return [info.name, singer].filter(Boolean).join(' ')
}

async function resolveOne (key, song, tier) {
  const fn = FETCHERS[key]
  need(fn, 'no such source: ' + key)
  const tiers = FALLBACK_ORDER[tier] || [tier]
  let lastErr
  for (let i = 0; i < tiers.length; i++) {
    try { return await fn(song, tiers[i]) } catch (e) { lastErr = e }
  }
  throw lastErr || new Error('resolve failed')
}

async function musicUrlHandler (source, info) {
  const tier = (info && info.type) || '320k'
  const musicInfo = (info && info.musicInfo) || {}
  const own = srcKey(musicInfo.platform) || (FETCHERS[source] ? source : null)
  const errs = []
  // 1) own source, own id
  if (FETCHERS[source] && (!own || own === source)) {
    try {
      const id = idFor(source, musicInfo)
      need(id, source + ': no usable id for this source')
      const song = Object.assign({}, musicInfo, { songmid: id, rid: id })
      return await resolveOne(source, song, tier)
    } catch (e) { errs.push(source + ': ' + e.message) }
  }
  // 2) other sources: search first, take that source's own id
  const order = Object.keys(FETCHERS).filter((k) => k !== source)
  for (let i = 0; i < order.length; i++) {
    const key = order[i]
    try {
      const q = searchQuery(musicInfo)
      need(q, 'no title, cannot search other sources')
      const hits = (await SEARCHERS[key](q)).filter((c) => sameSong(c, musicInfo))
      need(hits.length > 0, key + ': no same-song hit')
      const song = Object.assign({}, hits[0], { name: musicInfo.name, singer: musicInfo.singer, albumName: musicInfo.albumName })
      return await resolveOne(key, song, tier)
    } catch (e) { errs.push(key + ': ' + e.message) }
  }
  throw new Error('no source can resolve "' + (musicInfo.name || 'this track') + '" (' + errs.join(' | ') + ')')
}

const inited = {
  status: true,
  openDevTools: false,
  sources: {
    kw: { name: '酷我音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] },
    kg: { name: '酷狗音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] },
    tx: { name: 'QQ音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] },
    wy: { name: '网易音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] },
    mg: { name: '咪咕音乐', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] }
  }
}

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0'

const handler = async (payload) => {
  const source = (payload && payload.source) || 'kw'
  const action = payload && payload.action
  if (action && action !== 'musicUrl') throw new Error('unsupported action: ' + action)
  try {
    const url = await musicUrlHandler(source, payload && payload.info)
    return url
  } catch (e) {
    console.log('[gy] ' + source + ' fail: ' + (e && e.message))
    return null
  }
}
globalThis.__lx_handler = handler

if (globalThis.lx && globalThis.lx.send) globalThis.lx.send(EVENT_NAMES.inited, inited)



if (globalThis.lx && globalThis.lx.on) globalThis.lx.on(EVENT_NAMES.request, handler)

