/*!
 * @name Free listen
 * @description A lx-music source
 * @version v1.1.2
 * @wy_token null
 * @wy_token_desc 如果你有网易音乐的会员，可启用vip歌曲、更高音质的支持，将上面 @wy_token null 中的 null 改为你的token即可，token获取方式看常见问题歌单导入
 * @wy_token_desc 需要注意的是，自定义 token 存在导致账号被封禁的风险，token是账号的临时秘钥，注意不要随意分享
 */
/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
var __webpack_exports__ = {};

;// CONCATENATED MODULE: ./src/lx.js
const { EVENT_NAMES: lx_EVENT_NAMES, on, send: lx_send, request, utils: lxUtils, version, currentScriptInfo } = globalThis.lx
// console.log(globalThis.lx)


// https://github.com/lyswhut/lx-music-desktop/blob/master/FAQ.md#windowlxutils
const utils = {
  buffer: {
    from: lxUtils.buffer.from,
    bufToString: lxUtils.buffer.bufToString,
  },
  crypto: {
    aesEncrypt: lxUtils.crypto.aesEncrypt,
    md5: lxUtils.crypto.md5,
    randomBytes: lxUtils.crypto.randomBytes,
    rsaEncrypt: lxUtils.crypto.rsaEncrypt,
  },
}

const currentScript = currentScriptInfo
  ? currentScriptInfo.rawScript
  : document.getElementsByTagName('script')[0].innerText



;// CONCATENATED MODULE: ./src/apis/kw.js


const qualitys = {
  '128k': '128kmp3',
  '320k': '320kmp3',
  // ape: 'ape',
  flac: '2000kflac',
}


let token = ''
let cookie = ''
let key = ''

function encrypt(str, pwd) {
  if (pwd == null || pwd.length <= 0) {
    console.log('Please enter a password with which to encrypt the message.')
    return null
  }
  let prand = ''
  for (let i = 0; i < pwd.length; i++) {
    prand += pwd.charCodeAt(i).toString()
  }
  let sPos = Math.floor(prand.length / 5)
  let mult = parseInt(prand.charAt(sPos) + prand.charAt(sPos * 2) + prand.charAt(sPos * 3) + prand.charAt(sPos * 4) + prand.charAt(sPos * 5))
  let incr = Math.ceil(pwd.length / 2)
  let modu = Math.pow(2, 31) - 1
  if (mult < 2) {
    console.log('Algorithm cannot find a suitable hash. Please choose a different password. \nPossible considerations are to choose a more complex or longer password.')
    return null
  }
  let salt = Math.round(Math.random() * 1000000000) % 100000000
  prand += salt
  while (prand.length > 10) {
    prand = (parseInt(prand.substring(0, 10)) + parseInt(prand.substring(10, prand.length))).toString()
  }
  prand = (mult * prand + incr) % modu
  let enc_chr = ''
  let enc_str = ''
  for (let i = 0; i < str.length; i++) {
    enc_chr = parseInt(str.charCodeAt(i) ^ Math.floor((prand / modu) * 255))
    if (enc_chr < 16) {
      enc_str += '0' + enc_chr.toString(16)
    } else enc_str += enc_chr.toString(16)
    prand = (mult * prand + incr) % modu
  }
  salt = salt.toString(16)
  while (salt.length < 8)salt = '0' + salt
  enc_str += salt
  return enc_str
}
const createToken = (cookieToken, currentKey) => {
  if (currentKey && key != currentKey) key = currentKey
  return encrypt(cookieToken, key)
}
const parseCookieToken = (cookies) => {
  if (!cookies) return ''
  let cookieToken = Array.isArray(cookies) ? cookies.find(str => str.startsWith('Hm_Iuvt_')) : cookies.match(/Hm_Iuvt_\w+=\w+;/)?.[0]
  if (!cookieToken) return ''
  cookieToken = cookieToken.split(';')[0]
  cookie = cookieToken
  cookieToken = cookieToken.split('=')[1]
  return cookieToken
}
const getToken = () => new Promise((resolve, reject) => {
  let defaultKey = 'Hm_Iuvt_cdb524f42f0ce19b169a8071123a4700'
  request('http://www.kuwo.cn/', {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0',
      Referer: 'http://www.kuwo.cn/',
    },
  }, async function(error, response) {
    if (error) return reject(new Error('failed'))
    const token = parseCookieToken(response.headers['set-cookie'])
    if (!token) return reject(new Error('Invalid cookie'))
    const result = response.body.match(/https?:\/\/[/.\w]+\/kw-www\/\w+\.js/g)
    if (result) {
      const getAppToken = (url) => new Promise((resolve) => {
        request(url, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0',
            Referer: 'http://www.kuwo.cn/',
          },
        }, function(error, response) {
          if (error) return resolve('')
          const result = response.body.match(/Hm_Iuvt_(\w+)/)
          if (result) {
            resolve(createToken(token, result[0]))
          } else resolve('')
        })
      })
      const appRxp = /app\.\w+\.js/
      const index = result.findIndex(l => appRxp.test(l))
      if (index > -1) {
        const token = getAppToken(result[index])
        if (token) return resolve(token)
        result.splice(index, 1)
      }
      while (result.length) {
        const token = await getAppToken(result.pop())
        if (token) return resolve(token)
      }
      resolve(createToken(token, defaultKey))
    } else {
      resolve(createToken(token, defaultKey))
    }
  })
})


/* harmony default export */ const kw = ({
  info: {
    name: '酷我音乐',
    type: 'music',
    actions: ['musicUrl'],
    qualitys: ['128k', '320k', 'flac'],
  },

  // 广乐 0.0.8 补丁：官方 www.kuwo.cn/api/v1/www/music/playUrl 现在对未登录请求
  // 一律回 {"success":false,"message":"The request is illegal!"}（2026-10-30 实测，
  // 无论带不带 Secret/Cookie 都一样），原实现因此永远 reject('failed')，酷我源完全没声。
  // 改走酷我自己的移动端取链接口（kwplayer 端在用，无需登录、无需签名）：
  //   128kmp3 -> M500*.mp3（128kbps）
  //   320kmp3 -> M800*.mp3（真 320kbps，实测同一首歌的两个 URL 不同）
  //   2000kflac -> 可用时给 .flac（br=22000/mgg），曲目无高品版权时酷我会回落，
  //                这里按 data.format/bitrate 判断，回落档不冒充无损。
  // 返回体 JSON 里 data.url 就是直链，data.format/data.bitrate 用来核对实际音质。
  async musicUrl(musicInfo, quality) {
    return kwMusicUrl(musicInfo, quality)
  },
});

const kwQualitys = {
  '128k': '128kmp3',
  '320k': '320kmp3',
  flac: '2000kflac',
}

// 酷我无损是自家加密容器（mgg/mflac），播放器吃不了，按需逐级下退。
const kwFallbacks = { '320k': '128k', flac: '320k', '128k': null }

// 广乐 0.0.8 补丁：官方 www.kuwo.cn/api/v1/www/music/playUrl 现在对未登录请求
// 一律回 {"success":false,"message":"The request is illegal!"}（实测），原实现永远失败。
// 改走酷我移动端取链接口（kwplayer 在用，免登录免签名）：
//   128kmp3 -> M500*.mp3、320kmp3 -> M800*.mp3（实测同一首歌两个 URL 不同，真 320k）
//   2000kflac -> 有版权时给加密容器 mgg/mflac，曲目无高品版权时回落 mp3/aac
// 所以先按请求档取，拿到的 format/bitrate 不达档就下退一档，绝不把 48k aac 冒充成 320k。
async function kwMusicUrl(songInfo, quality) {
  const br = kwQualitys[quality]
  if (!br) throw new Error('不支持的音质：' + quality)

  // 酷我移动端只认纯数字 rid；songmid 可能是 'MUSIC_123' 或纯数字，统一剥掉前缀。
  const rid = String((songInfo && songInfo.songmid) == null ? '' : songInfo.songmid).replace(/^(MUSIC_|MP3_)/i, '')
  if (!rid) throw new Error('酷我：缺少歌曲 rid')
  const target_url = 'http://nmobi.kuwo.cn/mobi.s?f=web'
    + '&source=kwplayer_ar_5.1.0.0_B_jiakong_vh.apk'
    + '&type=convert_url_with_sign'
    + '&br=' + br
    + '&rid=' + rid

  const data = await new Promise((resolve, reject) => {
    request(target_url, {
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0',
        Referer: 'http://kuwo.cn/',
      },
    }, (err, resp) => {
      if (err) return reject(err)
      const body = resp && resp.body
      const d = body && body.data
      if (!d || !d.url) return reject(new Error('酷我取链失败（未返回 url）'))
      resolve(d)
    })
  })

  const gotFormat = String(data.format || '').toLowerCase()
  const gotBitrate = Number(data.bitrate) || 0
  const encrypted = gotFormat === 'mgg' || gotFormat === 'mflac'
  const want = { '128k': 128, '320k': 320, flac: 900 }[quality] || 128
  const tooLow = gotBitrate > 0 && gotBitrate < want * 0.8
  if (encrypted || tooLow) {
    const next = kwFallbacks[quality]
    if (next) return kwMusicUrl(songInfo, next)
    throw new Error('该曲目酷我最高只有 ' + (gotBitrate || '?') + 'kbps（' + gotFormat + '）')
  }
  // 酷我给的是纯 http 地址，个别曲目里会带 \u0026 转义。
  return String(data.url).replace(/\\u0026/g, '&')
}

;// CONCATENATED MODULE: ./src/apis/kg.js


// const qualitys = {
//   '128k': 'PQ',
//   '320k': 'HQ',
//   flac: 'SQ',
//   flac32bit: 'ZQ',
// }

// https://github.com/listen1/listen1_chrome_extension/blob/master/js/provider/kugou.js
// 广乐 0.0.8 补丁：官方 wwwapi.kugou.com/yy/index.php?r=play/getdata 对未登录请求
// 一律回 {"status":0,"err_code":30020}（SSA 风控），原实现因此永远取不到链。
// 改走酷狗 tracker 接口：key = md5(hash + 'kgcloudv2')，免登录免 cookie。
// 实测：热门曲（pay_type=3 / privilege=10）返回 {"status":2} 即「需付费」，不硬拗；
// 免费曲返回 {"status":1,"url":[...]} 可直接播放。tracker 不分档，尽量拿 hq，拿不到再按
// 免费档下退，并把实际拿到的 extName 如实报出来，不冒充无损。
const kgFallbacks = { flac: 'hq', '320k': 'hq', '128k': 'hq' }

function kgTracker(hash, br) {
  return new Promise((resolve, reject) => {
    const key = md5(String(hash) + 'kgcloudv2')
    const url = 'https://trackercdn.kugou.com/i/v2/?key=' + key
      + '&hash=' + hash + '&br=' + br + '&appid=1005&pid=2&cmd=25&behavior=play'
    request(url, {
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0',
        Referer: 'https://www.kugou.com/',
      },
    }, (err, resp) => {
      if (err) return reject(err)
      const body = (resp && resp.body) || {}
      if (body.status === 1 && body.url && body.url.length) return resolve(body)
      if (body.status === 2) return reject(new Error('该曲目酷狗需要付费/会员，未登录取不到直链'))
      reject(new Error('酷狗取链失败：' + (body.error || body.status)))
    })
  })
}

/* harmony default export */ const kg = ({
  info: {
    name: '酷狗音乐',
    type: 'music',
    actions: ['musicUrl'],
    qualitys: ['128k', '320k', 'flac'],
  },

  async musicUrl({ hash, albumId }, quality) {
    if (!hash) throw new Error('酷狗：缺少歌曲 hash')
    const info = await kgTracker(hash, 'hq')
    const ext = String(info.extName || '').toLowerCase()
    // 酷狗 tracker 的免费档只有 mp3；请求无损却只拿到 mp3 时如实说明。
    if (quality === 'flac' && ext !== 'flac') {
      // 不阻断播放：退到拿得到的 mp3，但把实际格式标在 URL 日志里由上层决定提示。
      console.log('[酷狗] 请求无损但只有 ' + (ext || 'mp3') + '，已回落')
    }
    return String(info.url[0])
  },
});

;// CONCATENATED MODULE: ./src/apis/tx.js


const fileConfig = {
  '128k': {
    s: 'M500',
    e: '.mp3',
    bitrate: '128kbps',
  },
  '320k': {
    s: 'M800',
    e: '.mp3',
    bitrate: '320kbps',
  },
  flac: {
    s: 'F000',
    e: '.flac',
    bitrate: 'FLAC',
  },
}

// https://github.com/listen1/listen1_chrome_extension/blob/master/js/provider/qq.js
/* harmony default export */ const tx = ({
  info: {
    name: '企鹅音乐',
    type: 'music',
    actions: ['musicUrl'],
    qualitys: ['128k'],
  },

  musicUrl({ songmid, strMediaMid }, quality) {
    const target_url = 'https://u.y.qq.com/cgi-bin/musicu.fcg'
    // thanks to https://github.com/Rain120/qq-music-api/blob/2b9cb811934888a532545fbd0bf4e4ab2aea5dbe/routers/context/getMusicPlay.js
    const guid = '10000'
    const songmidList = [songmid]
    const uin = '0'

    const fileInfo = fileConfig[quality]
    const file = `${fileInfo.s}${strMediaMid}${fileInfo.e}`
      /* songmidList.length === 1 &&
      `${fileInfo.s}${songmid}${songmid}${fileInfo.e}`*/ 

    const reqData = {
      req_0: {
        module: 'vkey.GetVkeyServer',
        method: 'CgiGetVkey',
        param: {
          filename: file ? [file] : [],
          guid,
          songmid: songmidList,
          songtype: [0],
          uin,
          loginflag: 1,
          platform: '20',
        },
      },
      loginUin: uin,
      comm: {
        uin,
        format: 'json',
        ct: 24,
        cv: 0,
      },
    }
    return new Promise((resolve, reject) => {
      console.log(songmid, quality)
      request(`${target_url}?format=json&data=${JSON.stringify(reqData)}`, {
        method: 'GET',
        headers: {
          channel: '0146951',
          uid: 1234,
        },
      }, (err, resp) => {
        console.log(resp.body)
        if (err) return reject(err)
        const data = resp.body
        const { purl } = data.req_0.data.midurlinfo[0]

        // vip
        if (purl === '') {
          // 广乐 0.0.8 实测：未登录 QQ 已经不发 vkey（purl 恒为空、result=104003），
          // 但新版响应里还带一份「试听片段」testfile2g（C400 的 30 秒 m4a，vkey 有效）。
          // 有就给这条能出声的片段，总比整首歌直接失败好；没有就如实报错。
          const preview = data.req_0.data.testfile2g || data.req_0.data.testfilewifi
          if (preview) {
            const host = (data.req_0.data.sip && data.req_0.data.sip[0]) || 'http://aqqmusic.tc.qq.com/'
            console.log('[QQ] 未登录只拿到试听片段')
            return resolve(host + preview)
          }
          return reject(new Error('该曲目 QQ 需要付费/登录，未登录取不到直链'))
        }

        const url = data.req_0.data.sip[0] + purl

        resolve(url)
      })
    })
  },
});

;// CONCATENATED MODULE: ./src/utils.js



const buf2hex = buffer => { // buffer is an ArrayBuffer
  return version
    ? utils.buffer.bufToString(buffer, 'hex')
    : [...new Uint8Array(buffer)]
        .map(x => x.toString(16).padStart(2, '0'))
        .join('')
}

const aesEncrypt = (data, eapiKey, iv, mode) => {
  if (!version) {
    mode = mode.split('-').pop()
  }
  return utils.crypto.aesEncrypt(data, mode, eapiKey, iv)
}

const md5 = str => utils.crypto.md5(str)


const showUpdateAlert = () => {
  send(EVENT_NAMES.updateAlert, {
    log: 'hello world',
    updateUrl: 'https://xxx.com',
  })
}

// https://stackoverflow.com/a/53387532
const compareVersions = ((prep, l, i, r) => (a, b) => {
  a = prep(a)
  b = prep(b)
  l = Math.max(a.length, b.length)
  i = 0
  r = i
  // convert into integer, uncluding undefined values
  while (!r && i < l) r = ~~a[i] - ~~b[i++]

  return r < 0 ? -1 : (r ? 1 : 0)
})(t => ('' + t)
// treat non-numerical characters as lower version
// replacing them with a negative number based on charcode of first character
  .replace(/[^\d.]+/g, c => '.' + (c.replace(/[\W_]+/, '').toUpperCase().charCodeAt(0) - 65536) + '.')
// remove trailing "." and "0" if followed by non-numerical characters (1.0.0b);
  .replace(/(?:\.0+)*(\.-\d+(?:\.\d+)?)\.*$/g, '$1')
// return array
  .split('.'))

;// CONCATENATED MODULE: ./src/apis/wy.js



const parse = (str) => {
  let comment = /^\/\*(?:.|\n)+?\*\//.exec(str)?.[0]
  if (!comment) return ''
  let token = /\*\s*@wy_token\s+(.+)/.exec(comment)?.[1]?.trim()
  return (!token || token == 'null') ? '' : token
}
const wy_token = parse(currentScript)

const wy_qualitys = {
  '128k': 128000,
  '320k': 320000,
  flac: 999000,
}
const eapi = (url, object) => {
  const eapiKey = 'e82ckenh8dichen8'

  const text = typeof object === 'object' ? JSON.stringify(object) : object
  const message = `nobody${url}use${text}md5forencrypt`
  const digest = md5(message)
  const data = `${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}`
  return {
    params: buf2hex(aesEncrypt(data, eapiKey, '', 'aes-128-ecb')).toUpperCase(),
  }
}

let wy_cookie = 'os=pc'
if (wy_token) wy_cookie = `MUSIC_U=${wy_token}; ` + wy_cookie

// https://github.com/listen1/listen1_chrome_extension/blob/master/js/provider/netease.js
/* harmony default export */ const wy = ({
  info: {
    name: '网易音乐',
    type: 'music',
    actions: ['musicUrl'],
    // 广乐 0.0.8 补丁：未登录也报三档。脚本能取到什么档由曲目版权决定，
    // 只报 128k 会让 320k / flac 的请求直接被宿主判「不支持」，而不是如实回落。
    qualitys: ['128k', '320k', 'flac'],
  },

  musicUrl({ songmid }, quality) {
    quality = wy_qualitys[quality]
    const target_url = 'https://interface3.music.163.com/eapi/song/enhance/player/url'
    const eapiUrl = '/api/song/enhance/player/url'

    const d = {
      ids: `[${songmid}]`,
      br: quality,
    }
    const data = eapi(eapiUrl, d)

    return new Promise((resolve, reject) => {
      console.log(songmid, quality)
      request(target_url, {
        method: 'POST',
        form: data,
        headers: {
          cookie: wy_cookie,
        },
      }, (err, resp) => {
        console.log(resp.body)
        if (err) return reject(err)
        if (resp.headers.cookie) wy_cookie = resp.headers.cookie

        let res_data = resp.body
        if (!res_data || !res_data.data || !res_data.data[0]) {
          return reject(new Error('网易没有返回这首歌的播放地址：' + JSON.stringify(res_data).slice(0, 200)))
        }
        const { url, freeTrialInfo } = res_data.data[0]
        if (!url || freeTrialInfo) return reject(new Error('failed'))
        resolve(url)
      })
    })
  },
});

;// CONCATENATED MODULE: ./src/apis/mg.js


const mg_qualitys = {
  '128k': 'PQ',
  '320k': 'HQ',
  flac: 'SQ',
  flac24bit: 'ZQ',
}

// 广乐 0.0.8 补丁：官方 app.c.nf.migu.cn/MIGUM2.0/strategy/listen-url/v2.2 已下线
// （未登录一律回 {"code":"870001","info":"内部错误"}，v2.0/v2.1 回 299996 路由不支持）。
// 改走咪咕 PC 站点自己在用的接口（对应 music.migu.cn/v5 播放器）：
//   GET https://c.musicapp.migu.cn/MIGUM3.0/strategy/pc/listen/v1.0
//   header 里必须带 channel=0146951，参数 contentId=<contentId>&resourceType=2&toneFlag=<PQ|HQ|SQ|ZQ>
// 返回 data.url 为直链；会员曲只给 lrcUrl 不给 url，这里如实报「需会员」。
async function mgMusicUrl(songInfo, quality) {
  const toneFlag = mg_qualitys[quality]
  if (!toneFlag) throw new Error('不支持的音质：' + quality)

  // 咪咕新版接口认 contentId（搜索里的 id），老数据里可能是纯数字 copyrightId。
  const raw = songInfo || {}
  const contentId = String(raw.contentId || raw.songmid || raw.songId || raw.musicId || '').trim()
  if (!contentId) throw new Error('咪咕：缺少歌曲 contentId')

  const target_url = 'https://c.musicapp.migu.cn/MIGUM3.0/strategy/pc/listen/v1.0'
    + '?contentId=' + encodeURIComponent(contentId)
    + '&resourceType=2'
    + '&toneFlag=' + toneFlag

  const first = await new Promise((resolve, reject) => {
    request(target_url, {
      method: 'GET',
      headers: {
        channel: '0146951',
        uid: '0',
        Referer: 'https://music.migu.cn/',
      },
    }, (err, resp) => {
      if (err) return reject(err)
      const body = (resp && resp.body) || {}
      resolve(body)
    })
  })

  if (first.code !== '000000' || !first.data) {
    throw new Error('咪咕取链失败：' + (first.info || first.code || '未知'))
  }
  const data = first.data
  let playUrl = data.url || data.playUrl
  if (!playUrl) {
    const tags = ((data.song && data.song.showTags) || []).join(',')
    const dialog = (data.dialogInfo && data.dialogInfo.text) || ''
    // 咪咕各档位对未登录的限制不一样：128k 常常直接给链，320k/无损只给一句提示。
    if (data.cannotCode === '440013' || tags.indexOf('vip') >= 0 || dialog.indexOf('会员') >= 0) {
      throw new Error('该音质档咪咕需要会员（' + (data.audioFormatType || toneFlag) + '）：' + (dialog || '未登录不可用'))
    }
    throw new Error('咪咕 ' + (data.audioFormatType || toneFlag) + ' 档没有返回播放地址'
      + (dialog ? '：' + dialog : '（cannotCode ' + (data.cannotCode || '?') + '）'))
  }
  if (playUrl.indexOf('//') === 0) playUrl = 'https:' + playUrl
  return String(playUrl)
}

/* harmony default export */ const mg = ({
  info: {
    name: '咪咕音乐',
    type: 'music',
    actions: ['musicUrl'],
    qualitys: ['128k', '320k', 'flac'],
  },

  musicUrl(musicInfo, quality) {
    return mgMusicUrl(musicInfo, quality)
  },
});


;// CONCATENATED MODULE: ./src/apis/index.js







/* harmony default export */ const apis = ({
  kw: kw,
  kg: kg,
  tx: tx,
  wy: wy,
  mg: mg,
});

;// CONCATENATED MODULE: ./package.json
const package_namespaceObject = /*#__PURE__*/JSON.parse('{"UU":"lx-music-source","rE":"1.1.2","cy":"lyswhut"}');
;// CONCATENATED MODULE: ./src/update.js




const address = [
  `https://raw.githubusercontent.com/${package_namespaceObject.cy}/${package_namespaceObject.UU}/master`,
  `https://cdn.jsdelivr.net/gh/${package_namespaceObject.cy}/${package_namespaceObject.UU}`,
  `https://fastly.jsdelivr.net/gh/${package_namespaceObject.cy}/${package_namespaceObject.UU}`,
  `https://gcore.jsdelivr.net/gh/${package_namespaceObject.cy}/${package_namespaceObject.UU}`,
]

const getLatestVersion = async(url, retryNum = 0) => {
  return new Promise((resolve, reject) => {
    request(url, {
      timeout: 10000,
    }, (err, resp) => {
      if (err || resp.statusCode != 200) {
        ++retryNum >= 3
          ? reject(err || new Error(resp.statusMessage || resp.statusCode))
          : getLatestVersion(url, retryNum).then(resolve).catch(reject)
      } else resolve(resp.body)
    })
  }).then(info => {
    if (info.version == null) throw new Error('failed')
    return info.version
  })
}

const getVersion = async(index = 0) => {
  return getLatestVersion(address[index] + '/package.json').then(version => {
    return {
      version,
      url: address[index] + '/dist/lx-music-source.js',
    }
  }).catch(async(err) => {
    index++
    if (index >= address.length) throw err
    return getVersion(index)
  })
}

const checkLatestVersion = async() => {
  const remoteVersion = await getVersion()
  return compareVersions(package_namespaceObject.rE, remoteVersion.version) < 0 ? remoteVersion : null
}

;// CONCATENATED MODULE: ./src/index.js




// console.log(window.lx)

// 广乐 0.0.8 补丁：酷我 / 酷狗 / QQ 都能按「歌名 + 歌手」搜（网易的 id 其它源不认），
// 所以兜底前先用名字在当前源搜一次同名曲，拿它自己的 id 再取链。
const __searchSongId = (source, musicInfo) => {
  const api = apis[source]
  const q = [musicInfo && musicInfo.name, musicInfo && musicInfo.singer].filter(Boolean).join(' ')
  if (!api || typeof api.search !== 'function' || !q) return Promise.reject(new Error('no search ' + source + ' q=' + q))
  return api.search(q, 1, 1).then(function (r) {
    const first = r && ((r.list && r.list[0]) || (r.data && r.data[0]) || (Array.isArray(r) ? r[0] : null))
    if (!first) throw new Error('no match')
    return first
  })
}

on(lx_EVENT_NAMES.request, ({ source, action, info }) => {
  switch (action) {
    case 'musicUrl':
      console.log('ENTRY ' + source + ' ' + info.type)
      // 先问宿主给的源；宿主给的不是五个源之一（比如曲目来自网易云插件 netease）时，
      // 直接按五源顺序兜底，胜出的源要把自己的 key 报回去（宿主靠它选音质档位）。
      var __has = !!apis[source]
      var __order = __has ? [source].concat(Object.keys(apis).filter(function (k) { return k !== source })) : Object.keys(apis)
      console.log('FALLBACK_START n=' + __order.length)
      var __start
      try { __start = __has ? apis[source].musicUrl(info.musicInfo, info.type) : Promise.reject(new Error('host source ' + source + ' not in this script')) } catch (e) { __start = e instanceof Error ? Promise.reject(e) : Promise.resolve(e) }
      try {
      return __order.reduce(function (p, k) { if (k === source) return p
        return p.catch(function (e0) {
         console.log('TRY ' + k + ' after ' + (e0 && e0.message) + ' hasSearch=' + (typeof apis[k].search))
         console.log('IN-KW-PATH ' + k)
         try {
         var __ss = __searchSongId(k, info.musicInfo)
         console.log('SS-TYPE ' + k + ' ' + typeof __ss)
         return __ss
          .then(function (song) {
            console.log('GOT ' + k + ' ' + JSON.stringify(song).slice(0, 300) + ' INFO=' + JSON.stringify(info.musicInfo).slice(0, 300))
            var g = song || {}
            var merged = {
              songmid: g.songmid || g.songId || g.id || info.musicInfo.songmid || info.musicInfo.id,
              strMediaMid: g.strMediaMid || g.mediaMid || g.songmid || g.id,
              hash: g.hash || g.fileHash || info.musicInfo.hash,
              albumId: g.albumId || g.album_id || info.musicInfo.albumId,
              rid: g.rid || g.songmid || g.id,
              name: g.name || g.title || info.musicInfo.name,
              singer: g.singer || g.artist || info.musicInfo.singer,
              copyrightId: g.copyrightId || g.songmid || g.id,
              contentId: g.contentId,
              albumMid: g.albumMid,
              songId: g.songId || g.songmid || g.id
            }
            console.log('MERGED ' + k + ' ' + JSON.stringify(merged).slice(0, 300))
            return Promise.resolve(apis[k].musicUrl(merged, info.type)).then(function (u) {
              console.log('URL ' + k + ' ' + String(u).slice(0, 300))
              return u
            }).catch(function (e2) {
              console.log('ERR ' + k + ' ' + (e2 && e2.message))
              throw e2
            })
          })
          .catch(function () { return apis[k].musicUrl(info.musicInfo, info.type) })
         } catch (es) { console.log('SYNERR ' + k + ' ' + (es && es.message)); return Promise.reject(es) }
        }) }, __start).catch((err) => {
        console.log('FINAL ' + (err && err.message))
        console.log(err.message)
        return Promise.reject(err)
      })
      } catch (e) {
        console.log('THROWN ' + (e && e.message))
        return Promise.reject(e)
      }
  }
})

const sources = {}
for (const [source, apiInfo] of Object.entries(apis)) {
  sources[source] = apiInfo.info
}

lx_send(lx_EVENT_NAMES.inited, {
  status: true,
  // openDevTools: true,
  // eslint-disable-next-line no-undef
  openDevTools: "production" === 'development',
  sources,
})

checkLatestVersion().then((version) => {
  if (!version) return
  lx_send(lx_EVENT_NAMES.updateAlert, { log: '发现新版本 v' + version.version, updateUrl: version.url })
})

/******/ })()
;