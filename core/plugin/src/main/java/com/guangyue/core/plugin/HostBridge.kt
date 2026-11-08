package com.guangyue.core.plugin

/**
 * Result of one plugin invocation, as produced inside QuickJS.
 *
 * The bridge returns JSON text (never Java objects) so nothing from the JS heap leaks into the
 * JVM heap and the runtime can be torn down at any time.
 */
internal object HostBridge {

    /** JS prelude installed before the plugin code: CommonJS + axios + console + timers + env. */
    const val COMMONJS_SHIM: String = """
(function(){
  'use strict';
  var G = globalThis;
  if (G.__GY_BRIDGE_INSTALLED__) return;
  G.__GY_BRIDGE_INSTALLED__ = true;

  // ---------------------------------------------------------------- module system
  var __modules = Object.create(null);
  var __cache = Object.create(null);

  // The plugin itself is registered by the host under this id.
  var MAIN_ID = '__main__';

  G.__gy_defineModule = function (id, factory) { __modules[id] = factory; };
  // 打包进 prelude 的库：它们是 esbuild 打的 IIFE，把成品挂到 globalThis 上
  // （`G.cheerio` / `G.CryptoJS` / `G.dayjs` / `G.Qs` / `G.he` / `G.BigInteger`）。
  // 插件里写的是 `require('cheerio')`，所以 require 必须先认这些全局名；
  // 否则每一条 import 都会走到「Cannot find module」，插件在加载阶段就整段炸掉。
  var __preludeModules = {
    'cheerio': 'cheerio',
    'crypto-js': 'CryptoJS',
    'dayjs': 'dayjs',
    'qs': 'Qs',
    'he': 'he',
    'big-integer': 'BigInteger'
  };
  G.__gy_require = function (id) {
    if (id === 'axios') return G.axios;
    if (Object.prototype.hasOwnProperty.call(__preludeModules, id)) {
      var lib = G[__preludeModules[id]];
      if (lib !== undefined) return lib;
    }
    if (Object.prototype.hasOwnProperty.call(__cache, id)) return __cache[id].exports;
    var factory = __modules[id];
    if (typeof factory !== 'function') {
      throw new Error("Cannot find module '" + id + "'");
    }
    var mod = { id: id, exports: {}, loaded: false };
    __cache[id] = mod;
    var ret = factory.call(mod.exports, mod, mod.exports, G.__gy_require);
    if (ret !== undefined && ret !== null) mod.exports = ret;
    mod.loaded = true;
    return mod.exports;
  };

  // The plugin wrapper calls this with the plugin source turned into a factory.
  G.__gy_boot = function (factory) {
    __modules[MAIN_ID] = factory;
    var exported = G.__gy_require(MAIN_ID);
    G.__plugin = exported;
    return typeof exported === 'object' && exported !== null;
  };

  // ---------------------------------------------------------------- Buffer (MusicFree plugins)
  // MusicFree runs on QuickJS with a partial Node surface; plugins use Buffer for base64 and
  // UTF-8 conversions. We implement just the parts a plugin realistically touches.
  function BufferArg(v, enc) {
    if (typeof v === 'number') return new Uint8Array(v);
    if (typeof v === 'string') {
      if (enc === 'base64') {
        var bin = G.__gy_atob(v);
        var out = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i) & 0xff;
        return out;
      }
      return G.__gy_utf8Encode(v);
    }
    if (v && typeof v.length === 'number') {
      var arr = new Uint8Array(v.length);
      for (var j = 0; j < v.length; j++) arr[j] = v[j] & 0xff;
      return arr;
    }
    return new Uint8Array(0);
  }

  function BufferShim(v, enc) { return BufferArg(v, enc); }
  BufferShim.from = function (v, enc) { return BufferArg(v, enc); };
  BufferShim.alloc = function (n, fill) {
    var a = new Uint8Array(n || 0);
    if (fill !== undefined && fill !== 0) a.fill(typeof fill === 'number' ? fill : 0);
    return a;
  };
  BufferShim.isBuffer = function (v) {
    return !!v && typeof v === 'object' && typeof v.length === 'number' &&
      (v instanceof Uint8Array);
  };
  BufferShim.concat = function (list) {
    var total = 0, i;
    for (i = 0; i < list.length; i++) total += list[i].length;
    var out = new Uint8Array(total), at = 0;
    for (i = 0; i < list.length; i++) { out.set(list[i], at); at += list[i].length; }
    return out;
  };
  G.Buffer = BufferShim;

  // btoa/atob on top of the host's Base64 (kept in Kotlin-free JS so it also works in tests).
  G.__gy_utf8Encode = function (str) {
    var bytes = [];
    for (var i = 0; i < str.length; i++) {
      var c = str.codePointAt(i);
      if (c > 0xffff) i++;
      if (c < 0x80) bytes.push(c);
      else if (c < 0x800) bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
      else if (c < 0x10000) bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
      else bytes.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 0x3f),
        0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
    }
    return new Uint8Array(bytes);
  };
  G.__gy_utf8Decode = function (bytes) {
    var out = '', i = 0;
    while (i < bytes.length) {
      var b = bytes[i++];
      if (b < 0x80) out += String.fromCharCode(b);
      else if (b < 0xe0) out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i++] & 0x3f));
      else if (b < 0xf0) out += String.fromCharCode(((b & 0x0f) << 12) |
        ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f));
      else {
        var cp = ((b & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12) |
          ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f);
        cp -= 0x10000;
        out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      }
    }
    return out;
  };
  G.__gy_atob = function (s) {
    var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var clean = String(s || '').replace(/[^A-Za-z0-9+\/]/g, '');
    var out = '', buf = 0, bits = 0;
    for (var i = 0; i < clean.length; i++) {
      var v = alphabet.indexOf(clean.charAt(i));
      if (v < 0) continue;
      buf = (buf << 6) | v; bits += 6;
      if (bits >= 8) { bits -= 8; out += String.fromCharCode((buf >> bits) & 0xff); }
    }
    return out;
  };
  G.atob = function (s) { return G.__gy_atob(s); };
  G.btoa = function (s) {
    var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var str = String(s || ''), out = '';
    for (var i = 0; i < str.length; i += 3) {
      var c1 = str.charCodeAt(i), c2 = str.charCodeAt(i + 1), c3 = str.charCodeAt(i + 2);
      var has2 = i + 1 < str.length, has3 = i + 2 < str.length;
      out += alphabet.charAt(c1 >> 2);
      out += alphabet.charAt(((c1 & 3) << 4) | (has2 ? c2 >> 4 : 0));
      out += has2 ? alphabet.charAt(((c2 & 15) << 2) | (has3 ? c3 >> 6 : 0)) : '=';
      out += has3 ? alphabet.charAt(c3 & 63) : '=';
    }
    return out;
  };
  // 0.0.8：插件用 `new TextDecoder('gbk'|'utf-8').decode(bytes)` 转码（73DJ 等站点回 GBK 页面）。
  // QuickJS 里没有 TextDecoder，这里补一个：utf-8 用 __gy_utf8Decode（纯 JS），
  // 其它编码（gbk/gb18030/big5）交给宿主，宿主那侧才有 Charset。
  G.__gy_decodeBytes = function (codec, bytes) {
    var u8;
    if (bytes instanceof Uint8Array) u8 = bytes;
    else if (bytes && typeof bytes.length === 'number') u8 = new Uint8Array(bytes);
    else u8 = new Uint8Array(0);
    var name = String(codec || 'utf-8').toLowerCase().replace(/[^a-z0-9]/g, '');
    if (name === '' || name === 'utf8' || name === 'unicode11utf8') return G.__gy_utf8Decode(u8);
    var b64 = G.btoa(String.fromCharCode.apply(null, u8));
    return G.__gy_text_in(b64, name);
  };
  G.TextDecoder = function (label) {
    var codec = label || 'utf-8';
    this.encoding = String(codec).toLowerCase();
    this.decode = function (bytes) { return G.__gy_decodeBytes(codec, bytes); };
  };
  G.TextEncoder = function () {
    this.encoding = 'utf-8';
    this.encode = function (text) {
      var s = String(text == null ? '' : text);
      var enc = G.__gy_utf8Encode(s), u8 = new Uint8Array(enc.length);
      for (var i = 0; i < enc.length; i++) u8[i] = enc.charCodeAt(i) & 0xff;
      return u8;
    };
  };


  // ------------------------------------------------------------ URLSearchParams
  // QuickJS 没有 URLSearchParams / URL（浏览器/Node 才有）。0.0.7 体检里「快音」「36DJ」
  // 直接在 search 阶段报 `'URLSearchParams' is not defined` —— 它们拿它拼 POST 表单。
  // 这里给一个够用的实现：append/delete/get/getAll/has/set/sort/toString/forEach/keys/values/entries
  // 以及 Symbol.iterator，并让 axios 的 params 也认它（String(obj) 走 toString）。
  function GyURLSearchParams(init) {
    var list = [];
    var self = this;
    function appendPair(k, v) { list.push([String(k), v === undefined || v === null ? '' : String(v)]); }
    function fromString(text) {
      var q = String(text || '').replace(/^[?#]/, '');
      if (!q) return;
      var parts = q.split('&');
      for (var i = 0; i < parts.length; i++) {
        if (!parts[i]) continue;
        var idx = parts[i].indexOf('=');
        var k = idx < 0 ? parts[i] : parts[i].slice(0, idx);
        var v = idx < 0 ? '' : parts[i].slice(idx + 1);
        try { appendPair(decodeURIComponent(k.replace(/\+/g, ' ')), decodeURIComponent(v.replace(/\+/g, ' '))); }
        catch (e) { appendPair(k, v); }
      }
    }
    function fromObject(obj) {
      if (!obj) return;
      if (obj instanceof GyURLSearchParams) {
        var pairs = obj.__gyPairs();
        for (var j = 0; j < pairs.length; j++) appendPair(pairs[j][0], pairs[j][1]);
        return;
      }
      if (typeof obj === 'string') { fromString(obj); return; }
      if (Object.prototype.toString.call(obj) === '[object Array]') {
        for (var i = 0; i < obj.length; i++) {
          var pair = obj[i];
          if (pair && pair.length >= 2) appendPair(pair[0], pair[1]);
        }
        return;
      }
      for (var k in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, k) && obj[k] !== undefined) appendPair(k, obj[k]);
      }
    }
    this.__gyPairs = function () { return list; };
    this.append = function (k, v) { appendPair(k, v); };
    this.delete = function (k) {
      var key = String(k);
      for (var i = list.length - 1; i >= 0; i--) if (list[i][0] === key) list.splice(i, 1);
    };
    this.get = function (k) {
      var key = String(k);
      for (var i = 0; i < list.length; i++) if (list[i][0] === key) return list[i][1];
      return null;
    };
    this.getAll = function (k) {
      var key = String(k), out = [];
      for (var i = 0; i < list.length; i++) if (list[i][0] === key) out.push(list[i][1]);
      return out;
    };
    this.has = function (k) {
      var key = String(k);
      for (var i = 0; i < list.length; i++) if (list[i][0] === key) return true;
      return false;
    };
    this.set = function (k, v) {
      var key = String(k), found = -1;
      for (var i = list.length - 1; i >= 0; i--) {
        if (list[i][0] !== key) continue;
        if (found < 0) { found = i; list[i][1] = v === undefined || v === null ? '' : String(v); }
        else list.splice(i, 1);
      }
      if (found < 0) appendPair(k, v);
    };
    this.sort = function () {
      list.sort(function (a, b) { return a[0] < b[0] ? -1 : (a[0] > b[0] ? 1 : 0); });
    };
    this.toString = function () {
      var out = [];
      for (var i = 0; i < list.length; i++) {
        out.push(encodeURIComponent(list[i][0]) + '=' + encodeURIComponent(list[i][1]));
      }
      return out.join('&');
    };
    this.forEach = function (fn, thisArg) {
      for (var i = 0; i < list.length; i++) fn.call(thisArg, list[i][1], list[i][0], self);
    };
    this.keys = function () {
      var i = 0;
      return { next: function () { return i < list.length ? { value: list[i++][0], done: false } : { value: undefined, done: true }; } };
    };
    this.values = function () {
      var i = 0;
      return { next: function () { return i < list.length ? { value: list[i++][1], done: false } : { value: undefined, done: true }; } };
    };
    this.entries = function () {
      var i = 0;
      return { next: function () { return i < list.length ? { value: [list[i][0], list[i++][1]], done: false } : { value: undefined, done: true }; } };
    };
    if (typeof Symbol !== 'undefined' && Symbol.iterator) {
      this[Symbol.iterator] = this.entries;
    }
    fromObject(init);
  }
  G.URLSearchParams = GyURLSearchParams;
  G.__gy_makeSearchParams = function (init) { return new GyURLSearchParams(init); };

  // 极简 URL：插件有时 `new URL(url)` 取 searchParams / host / pathname。
  function GyURL(input, base) {
    var raw = String(input === undefined || input === null ? '' : input);
    var abs = raw;
    if (base && !/^[a-zA-Z][a-zA-Z0-9+.\-]*:/.test(raw)) {
      abs = String(base).replace(/[^/]*$/, '') + raw;
    }
    var m = /^([a-zA-Z][a-zA-Z0-9+.\-]*:)(\/\/([^/?#]*))?([^?#]*)(\?[^#]*)?(#.*)?$/.exec(abs);
    this.href = abs;
    this.protocol = m ? m[1] : '';
    this.host = m && m[3] ? m[3] : '';
    this.hostname = this.host.replace(/:\d+$/, '');
    this.search = m && m[5] ? m[5] : '';
    this.hash = m && m[6] ? m[6] : '';
    this.pathname = m && m[4] ? m[4] : '';
    this.searchParams = new GyURLSearchParams(this.search);
    this.toString = function () { return this.href; };
  }
  G.URL = GyURL;

  // ---------------------------------------------------------------- console
  function fmt(args) {
    var out = [];
    for (var i = 0; i < args.length; i++) {
      var a = args[i];
      if (typeof a === 'string') out.push(a);
      else {
        try { out.push(JSON.stringify(a)); } catch (e) { out.push(String(a)); }
      }
    }
    return out.join(' ');
  }
  function log(level) {
    return function () {
      try {
        G.__gy_log(level, fmt(arguments));
      } catch (e) { /* logging must never break a plugin */ }
    };
  }
  G.console = {
    log: log('log'), info: log('info'), warn: log('warn'),
    error: log('error'), debug: log('debug'), trace: log('trace')
  };

  // ---------------------------------------------------------------- timers
  // QuickJS has no event loop the host can block on: setTimeout is recorded and fired
  // synchronously right after the current call returns (see __gy_flushTimers).
  var __timers = [];
  var __timerSeq = 1;
  G.setTimeout = function (fn, delay) {
    if (typeof fn !== 'function') return 0;
    var id = __timerSeq++;
    var d = Number(delay) || 0;
    __timers.push({ id: id, fn: fn, delay: d, due: Date.now() + (d > 0 ? d : 0), args: Array.prototype.slice.call(arguments, 2), clear: false });
    return id;
  };
  G.setInterval = function (fn, delay) {
    // A repeating timer makes no sense in a single-shot invocation: run it once.
    return G.setTimeout(fn, delay);
  };
  G.clearTimeout = function (id) {
    for (var i = 0; i < __timers.length; i++) if (__timers[i].id === id) __timers[i].clear = true;
  };
  G.clearInterval = G.clearTimeout;
  G.setImmediate = function (fn) { return G.setTimeout(fn, 0); };
  // M8：计时器按到期时间触发（异步 HTTP 之后插件的「超时竞速」要真等）。宿主没有异步 HTTP 时
  // （旧桥 / 测试桩）退回旧行为：一律立即触发。返回本次触发的个数。
  G.__gy_resetTimers = function () { __timers = []; return 'ok'; };
  G.__gy_timersPending = function () {
    for (var i = 0; i < __timers.length; i++) if (!__timers[i].clear) return true;
    return false;
  };
  G.__gy_flushTimers = function () {
    var asyncHttp = typeof G.__gy_http_start === 'function';
    var now = Date.now();
    var pending = __timers;
    __timers = [];
    var limit = 200;
    var fired = 0;
    // 先到期的先触发
    pending.sort(function (a, b) { return a.due - b.due; });
    for (var i = 0; i < pending.length; i++) {
      var t = pending[i];
      if (t.clear) continue;
      if ((asyncHttp && t.due > now) || limit-- <= 0) { __timers.push(t); continue; }
      fired++;
      try { t.fn.apply(null, t.args); } catch (e) {
        try { G.__gy_log('error', 'timer: ' + (e && e.message ? e.message : String(e))); } catch (_) {}
      }
    }
    return fired;
  };

  // ---------------------------------------------------------------- axios
  // MusicFree plugins use `axios.get/post({ url, headers, params, data, responseType })`
  // and `axios({...})`. Every call is forwarded to the host, which performs it with OkHttp
  // and hands back { status, headers, data }.
  function makeResponse(rawJson) {
    var res;
    try { res = JSON.parse(rawJson); } catch (e) { res = { status: 0, data: null, headers: {} }; }
    var out = {
      status: res.status || 0,
      statusText: res.statusText || '',
      headers: res.headers || {},
      data: res.data,
      config: {},
      request: {}
    };
    // gyChain 会返回**新的**链对象（浅拷贝），必须把它的返回值交给插件，
    // 否则插件拿到的原始对象没有 `.then` → `TypeError: ... is not a function`。
    return attachChain(out);
  }

  /**
   * MusicFree 的插件大量把 axios 当 Promise 用：
   *
   * ```
   * axios.get(url).then(function (r) { ... }).catch(function (e) { ... })
   * ```
   *
   * 而我们的 axios 桥是**同步**的（QuickJS 里没有事件循环可阻塞），返回的是一个普通对象。
   * 于是 `.then` 本身就是 `undefined` → `TypeError: ... is not a function`，整类插件在调用阶段死掉。
   *
   * 这里给「已结算的值」挂一条 Promise 语义的同步链。0.0.6 的早期版本只在
   * 「回调返回 undefined」时把链续上，`axios.post(...).then(f).then(g)`、
   * `axios.post(...).then(f).catch(g)`、回调返回普通对象后继续链的写法全部
   * 落到 `TypeError: ... is not a function` —— 这才是 0.0.4 体检 43 个「导入成功」的
   * 插件装机后大面积搜不到的真因（QQ音乐/酷我/酷狗/咪咕/网易云/汽水 全中）。
   *
   * 现在：`.then/.catch/.finally` **恒返回可继续链的已结算对象**，回调返回值无论是
   * undefined、普通对象、数组还是基本类型，都会被打包成下一环；回调抛异常则转成
   * rejected 环，交给后面的 `.catch`；回调返回 thenable 时同步吸收其状态。
   */
  function gyChain(value, error) {
    // 已经是链：拆成「干净值 + 状态」，绝不让链套链（await 一个 thenable 会自解析成死循环）
    if (value && typeof value === 'object' && value.__gyChain) {
      var inner = value.__gyState;
      // 显式传入的 error（例如 HTTP 非 2xx）优先于链上原状态
      return gyChain(inner.value, error || inner.error);
    }
    var state = { ok: !error, value: value, error: error || null };
    var target;
    if (value instanceof Array) {
      // 数组：浅拷贝后挂链，回调 / await 拿到的仍是**普通数组**（不带 then）
      target = value.slice();
    } else if (value && typeof value === 'object') {
      // 响应对象 / 普通对象：浅拷贝字段，保住 res.status / res.data 这类直读
      target = {};
      for (var k in value) {
        if (Object.prototype.hasOwnProperty.call(value, k)) target[k] = value[k];
      }
    } else {
      // 基本类型装进盒子，仍然可链
      target = {};
      target.__gyBox = true;
      target.valueOf = function () { return state.value; };
      target.toString = function () { return state.value === undefined ? '' : String(state.value); };
    }
    Object.defineProperty(target, '__gyChain', { value: true, enumerable: false });
    Object.defineProperty(target, '__gyState', { value: state, enumerable: false, configurable: true });
    target.then = function (onOk, onErr) { return gySettle(state, onOk, onErr); };
    target.catch = function (onErr) { return gySettle(state, null, onErr); };
    target.finally = function (fn) {
      if (typeof fn === 'function') { try { fn(); } catch (e) { /* ignore */ } }
      return target;
    };
    return target;
  }

  function gySettle(state, onOk, onErr) {
    if (!state.ok) {
      if (typeof onErr === 'function') {
        try { return gyChain(onErr(state.error), null); }
        catch (e) { return gyChain(null, e); }
      }
      return gyChain({}, state.error);
    }
    if (typeof onOk !== 'function') return gyChain(state.value, null);
    var out;
    try { out = onOk(state.value); }
    catch (e) { return gyChain(null, e); }
    // 回调又返回了一条链 / 原生 Promise（已结算的 Promise 由宿主 drainJobs 驱动）
    if (out && typeof out === 'object') {
      if (out.__gyState) {
        return out.__gyState.ok ? gyChain(out.__gyState.value, null) : gyChain({}, out.__gyState.error);
      }
      if (out.__gyChain) return out;
    }
    return gyChain(out, null);
  }

  function attachChain(obj, err) { return gyChain(obj, err || null); }
  G.__gy_attachChain = attachChain;

  /**
   * 0.0.8：`axios: url is required` 的三个真因，都在这里兜住——
   *  1) `axios({ url, baseURL, ... })` 里 url 是相对路径（`/api/x`），要拼 baseURL；
   *  2) `axios.get({ url: '...', params: ... })` —— 整个 config 当第一个参数传（不少插件这么写）；
   *  3) `const http = axios.create({ baseURL, headers })` 之后 `http.get('/song')` ——
   *     实例要继承 create 时给的 defaults。
   * 另外把 axios 的 `transformResponse` / `validateStatus` 交给桥层默认行为（已实现），
   * 不再对 300/400 抛「未捕获」。
   */
  function encodeUrlSafely(url) {
    var out = '';
    for (var i = 0; i < url.length; i++) {
      var ch = url.charAt(i);
      var code = url.charCodeAt(i);
      if (code < 128) { out += ch; continue; }
      // 保留已经是 %XX 的序列，其余非 ASCII 一律按 UTF-8 百分号编码。
      try { out += encodeURIComponent(ch); } catch (e) { out += ch; }
    }
    // 空格在 path/query 里也常出现
    out = out.replace(/ /g, '%20');
    // 已经存在的百分号序列不能被二次编码（encodeURIComponent 只会处理非 ASCII，安全）
    return out;
  }

  function resolveUrl(config, baseURL) {
    var url = config.url;
    if (url === undefined || url === null || url === '') return '';
    url = String(url);
    var base = config.baseURL || baseURL || '';
    if (base && !/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url)) {
      if (url.charAt(0) !== '/') url = '/' + url;
      // base 去掉末尾斜杠，避免 // 出现（个别源站 301 到 404）
      while (base.length > 1 && base.charAt(base.length - 1) === '/') base = base.slice(0, -1);
      url = base + url;
    }
    return url;
  }

  function request(config, defaults) {
    defaults = defaults || {};
    if (typeof config === 'string') config = { url: config };
    config = config || {};
    // 合并 defaults：headers 深一层，其余浅覆盖
    var merged = {};
    var k;
    for (k in defaults) if (Object.prototype.hasOwnProperty.call(defaults, k)) merged[k] = defaults[k];
    for (k in config) if (Object.prototype.hasOwnProperty.call(config, k)) merged[k] = config[k];
    if (defaults.headers || config.headers) {
      var hs = {};
      var hsrc = [defaults.headers, config.headers];
      for (var hi = 0; hi < hsrc.length; hi++) {
        var hh = hsrc[hi] || {};
        for (var hk in hh) {
          if (!Object.prototype.hasOwnProperty.call(hh, hk)) continue;
          // axios 的默认头挂在 headers.common / headers.get 下，拍平
          var hv = hh[hk];
          if (hv && typeof hv === 'object' && !(hv instanceof GyURLSearchParams)) {
            for (var sk in hv) {
              if (Object.prototype.hasOwnProperty.call(hv, sk)) hs[sk] = hv[sk];
            }
          } else {
            hs[hk] = hv;
          }
        }
      }
      merged.headers = hs;
    }
    config = merged;

    // 0.0.8：个别插件把完整地址放在 `params` 里再配一个 baseURL，url 本身为空；
    // 先把字符串形式的 params 当候选地址试一次，真拼不上再报 url 缺失。
    if ((config.url === undefined || config.url === null || config.url === '') &&
        typeof config.params === 'string' && /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(config.params)) {
      config.url = config.params;
      config.params = null;
    }
    var method = String(config.method || 'get').toUpperCase();
    var url = resolveUrl(config, defaults.baseURL);
    if (!url) {
      // 不再粗暴 throw（那样 .catch 接不住）；返回一条带校验错误的链，插件能 catch 看到。
      var missErr = new Error('axios: url is required');
      missErr.config = config;
      return attachChain({ status: 0, statusText: 'axios: url is required', headers: {}, data: null, config: config }, missErr);
    }

    // 0.0.8：URL 里带中文/空格时 OkHttp 会直接抛 `bad url`（status -1），这里统一做一次安全编码。
    url = encodeUrlSafely(url);


    var params = config.params;
    // 插件常把 URLSearchParams 实例当 params/data 传（快音、36DJ 就这样）
    if (params instanceof GyURLSearchParams) {
      var qs = params.toString();
      if (qs) url += (url.indexOf('?') >= 0 ? '&' : '?') + qs;
      params = null;
    }
    if (params && typeof params === 'object') {
      var pairs = [];
      for (var k2 in params) {
        if (!Object.prototype.hasOwnProperty.call(params, k2)) continue;
        var v = params[k2];
        if (v === undefined || v === null) continue;
        if (Object.prototype.toString.call(v) === '[object Array]') {
          for (var i = 0; i < v.length; i++) pairs.push(encodeURIComponent(k2) + '=' + encodeURIComponent(v[i]));
        } else {
          pairs.push(encodeURIComponent(k2) + '=' + encodeURIComponent(v));
        }
      }
      if (pairs.length) url += (url.indexOf('?') >= 0 ? '&' : '?') + pairs.join('&');
    }

    var data = config.data;
    if (data !== undefined && data !== null && typeof data !== 'string') {
      if (data instanceof GyURLSearchParams) {
        data = data.toString();
      } else {
        try { data = JSON.stringify(data); } catch (e) { data = String(data); }
      }
    }

    var headers = config.headers || {};
    var flatHeaders = {};
    for (var h in headers) {
      if (!Object.prototype.hasOwnProperty.call(headers, h)) continue;
      var hv2 = headers[h];
      if (hv2 === undefined || hv2 === null) continue;
      flatHeaders[h] = String(hv2);
    }

    var payload = JSON.stringify({
      method: method,
      url: String(url),
      headers: flatHeaders,
      body: data === undefined || data === null ? null : String(data),
      responseType: String(config.responseType || 'json'),
      timeout: typeof config.timeout === 'number' ? config.timeout : 0
    });

    // M8：宿主有异步 HTTP 时，请求并发发出、返回真正的 Promise（与 MusicFree 的 axios 一致）。
    // 以前是同步串行：酷我取链要串行试十几个端点，总耗时超过预算 → 「插件超时没返回」；
    // 插件里 Promise.race/any + setTimeout 的竞速也全部失效（计时器立即触发）。
    if (typeof G.__gy_http_start === 'function') {
      var hid = String(G.__gy_http_start(payload));
      return new Promise(function (resolve, reject) {
        __httpWaiters[hid] = function (raw2) {
          var r = finishResponse(raw2, config);
          var st = r && r.__gyState;
          var plain = {};
          for (var pk in r) {
            if (!Object.prototype.hasOwnProperty.call(r, pk)) continue;
            if (pk === 'then' || pk === 'catch' || pk === 'finally') continue;
            plain[pk] = r[pk];
          }
          if (st && !st.ok) {
            if (st.error && st.error.response) st.error.response = plain;
            reject(st.error);
          } else {
            resolve(plain);
          }
        };
      });
    }

    var raw;
    try {
      raw = G.__gy_http(payload);
    } catch (e) {
      // 网络层失败也要变成**可 catch 的链对象**：插件写的是
      // `axios.get(url).then(...).catch(...)`。throw 出来的异常接不上任何链，
      // `.catch` 就形同虚设 —— 0.0.1 那批插件大量这么写，全挂在这里。
      var netErr = new Error('Network Error: ' + (e && e.message ? e.message : String(e)));
      netErr.config = config;
      return attachChain({ status: 0, headers: {}, data: null, config: config }, netErr);
    }
    return finishResponse(raw, config);
  }

  var __httpWaiters = {};
  G.__gy_resetHttp = function () { __httpWaiters = {}; return 'ok'; };
  // 取回已完成的请求并交给各自的 Promise；返回交付的个数。宿主协议：一次一条，"id\n<响应 JSON>"，空串表示没有。
  G.__gy_deliverHttp = function () {
    if (typeof G.__gy_http_take !== 'function') return 0;
    var n = 0;
    while (n < 64) {
      var s = G.__gy_http_take();
      if (!s) break;
      var nl = s.indexOf('\n');
      var id = s.slice(0, nl);
      var w = __httpWaiters[id];
      delete __httpWaiters[id];
      n++;
      if (typeof w === 'function') {
        try { w(s.slice(nl + 1)); } catch (e) {
          try { G.__gy_log('error', 'http deliver: ' + (e && e.message ? e.message : String(e))); } catch (_) {}
        }
      }
    }
    return n;
  };
  G.__gy_httpPending = function () {
    for (var k in __httpWaiters) if (Object.prototype.hasOwnProperty.call(__httpWaiters, k)) return true;
    return false;
  };

  function finishResponse(raw, config) {
    var response = makeResponse(raw);
    // 0.0.8：`responseType: 'arraybuffer'` 时插件要的是二进制（封面、音频头、魔数校验）。
    // 宿主回的是 Base64 文本，这里在 JS 里自己解成 Uint8Array —— 不能让 Kotlin 直接给
    // ByteArray，那要经 JSON.stringify，会走到 android.util.Base64 那条通路（JVM 单测里是桩）。
    var __rt = config.responseType;
    if (typeof response.data === 'string' && (__rt === 'arraybuffer' || __rt === 'buffer' || __rt === 'blob')) {
      var __bin = G.__gy_atob(response.data);
      var __u8 = new Uint8Array(__bin.length);
      for (var __bi = 0; __bi < __bin.length; __bi++) __u8[__bi] = __bin.charCodeAt(__bi) & 0xff;
      response.data = __u8;
      response.bytes = __u8;
      response.buffer = __u8.buffer;
    }
    var status = response.status;
    // 0.0.8：`validateStatus` 交给插件——默认 2xx/3xx 成功；插件显式给了就按它的判。
    var ok = status >= 200 && status < 400;
    if (typeof config.validateStatus === 'function') {
      try { ok = !!config.validateStatus(status); } catch (e) { /* 用默认 */ }
    }
    if (status === 0) ok = false;
    if (ok) return response;
    // HTTP 非成功同理：回一个带错误链的响应，让插件的 .catch 能处理。
    // 0.0.8：status<=0 表示根本没拿到 HTTP 响应（连不上/超时/证书/被拦截），此时 statusText 里是我们写的人话原因，带上它。
    var httpErr = new Error(
      status > 0
        ? 'Request failed with status code ' + status
        : (response.statusText ? String(response.statusText) + '（status ' + status + '）' : '请求失败（status ' + status + '）')
    );
    httpErr.response = response;
    httpErr.config = config;
    return attachChain(response, httpErr);
  }

  function makeAxios(defaults) {
    var inst = function (config) { return request(config, defaults); };
    inst.request = function (config) { return request(config, defaults); };
    // 0.0.8：`axios.get({ url, params })` 与 `axios.get('/x', { params })` 两种都认
    function withUrl(method, urlOrConfig, config) {
      if (urlOrConfig && typeof urlOrConfig === 'object' && !(urlOrConfig instanceof GyURLSearchParams)) {
        var c = {};
        var k3;
        for (k3 in urlOrConfig) if (Object.prototype.hasOwnProperty.call(urlOrConfig, k3)) c[k3] = urlOrConfig[k3];
        c.method = method;
        return request(c, defaults);
      }
      config = config || {};
      var c2 = {};
      var k4;
      for (k4 in config) if (Object.prototype.hasOwnProperty.call(config, k4)) c2[k4] = config[k4];
      c2.url = urlOrConfig;
      c2.method = method;
      return request(c2, defaults);
    }
    inst.get = function (a, b) { return withUrl('get', a, b); };
    inst.delete = function (a, b) { return withUrl('delete', a, b); };
    inst.head = function (a, b) { return withUrl('head', a, b); };
    inst.options = function (a, b) { return withUrl('options', a, b); };
    inst.post = function (url, data, config) {
      if (url && typeof url === 'object') {
        var c = {}; var k5;
        for (k5 in url) if (Object.prototype.hasOwnProperty.call(url, k5)) c[k5] = url[k5];
        c.method = 'post';
        return request(c, defaults);
      }
      config = config || {};
      var c2 = {}; var k6;
      for (k6 in config) if (Object.prototype.hasOwnProperty.call(config, k6)) c2[k6] = config[k6];
      c2.url = url; c2.method = 'post'; c2.data = data;
      return request(c2, defaults);
    };
    inst.put = function (url, data, config) { return inst.post(url, data, config); };
    inst.patch = function (url, data, config) {
      var r = inst.post(url, data, config);
      return r;
    };
    inst.put = function (url, data, config) {
      if (url && typeof url === 'object') {
        var c = {}; var k7;
        for (k7 in url) if (Object.prototype.hasOwnProperty.call(url, k7)) c[k7] = url[k7];
        c.method = 'put';
        return request(c, defaults);
      }
      config = config || {};
      var c2 = {}; var k8;
      for (k8 in config) if (Object.prototype.hasOwnProperty.call(config, k8)) c2[k8] = config[k8];
      c2.url = url; c2.method = 'put'; c2.data = data;
      return request(c2, defaults);
    };
    inst.defaults = defaults;
    inst.interceptors = {
      request: { use: function () { return 0; }, eject: function () {} },
      response: { use: function () { return 0; }, eject: function () {} }
    };
    var baseURL = defaults.baseURL || '';
    inst.create = function (extra) {
      var merged = {}; var k9;
      for (k9 in defaults) if (Object.prototype.hasOwnProperty.call(defaults, k9)) merged[k9] = defaults[k9];
      extra = extra || {};
      for (k9 in extra) if (Object.prototype.hasOwnProperty.call(extra, k9)) merged[k9] = extra[k9];
      if (!merged.baseURL) merged.baseURL = baseURL;
      var child = makeAxios(merged);
      // 实例本身也是函数：`api({url})`
      return child;
    };
    inst.all = function (arr) { return arr; };
    inst.spread = function (fn) { return function (arr) { return fn.apply(null, arr); }; };
    inst.isAxiosError = function (e) { return !!(e && e.config); };
    return inst;
  }

  var axios = makeAxios({ headers: { common: { Accept: 'application/json, text/plain, */*' } } });
  G.axios = axios;

  // ---------------------------------------------------------------- utils
  G.module = { exports: {} };
  G.exports = G.module.exports;
  // G.require 在此刻还不存在（它在下面 20 行才被替换成包装版），直接赋值会抛
  // `TypeError: cannot set property 'cache' of undefined`，把整个 shim 的执行打断，
  // 之后所有插件都表现为「语法错误 / 不是函数」。改成在包装版里挂 cache。

  // `require('buffer').Buffer` / `require('util')` … are common in MusicFree plugins.
  var __builtinModules = {
    buffer: { Buffer: BufferShim },
    util: { format: fmt, inspect: fmt },
    path: { join: function () {
      var parts = Array.prototype.slice.call(arguments).filter(function (p) { return p; });
      return parts.join('/').replace(/\/+/g, '/');
    } }
  };
  var __gy_requireRaw = G.__gy_require;
  G.require = function (id) {
    if (Object.prototype.hasOwnProperty.call(__builtinModules, id)) return __builtinModules[id];
    return __gy_requireRaw(id);
  };
  G.require.cache = __cache;   // 包装版 require 已经就位，这里才是合法赋值
  G.__gy_require = G.require;
  G.process = {
    env: { NODE_ENV: 'production' },
    platform: 'android',
    version: 'v18.0.0',
    nextTick: function (fn) { G.setTimeout(fn, 0); }
  };
})()
"""

    /**
     * Wraps plugin source into a CommonJS module factory.
     *
     * The source runs inside `function(module, exports, require)` so both
     * `module.exports = {...}` and `exports.foo = ...` work, exactly like in MusicFree.
     */
    fun wrapPluginSource(source: String): String = buildString {
        // 工厂是作为**实参**传给 `__gy_boot(...)` 的函数表达式，所以自身前后都不带 `;`；
        // 整条 `globalThis.__gy_ret = globalThis.__gy_boot( <factory> );` 由宿主以**语句**形式
        // 交给 PluginRuntime.exec 执行（exec 不拼括号，语句分号本来就合法）。
        append("(function(module, exports, require){\n")
        append(source)
        append("\n})")
    }

    /** Call expression that invokes `__plugin.<fn>(...argsJson)` and serialises the result. */
    fun callExpression(fn: String, argsJson: String): String =
        "__gy_call('" + fn.replace("'", "\\'") + "', " + argsJson + ")"

    /**
     * Installed after the plugin module: exposes the call entry point the host uses.
     *
     * Return convention (always a JSON string, never an exception):
     *   {"ok":true,"data":<json>} | {"ok":false,"error":"message","stack":"..."}
     *
     * Async plugins: MusicFree itself requires synchronous handlers, but a lot of shipped
     * plugins are `async function` and simply resolve immediately. Instead of rejecting the
     * whole plugin we accept an already-settled Promise (`thenable`), which the host can read
     * synchronously without an event loop. A *pending* Promise still fails loudly — silently
     * returning `undefined` would corrupt search results.
     */
    const val CALL_DISPATCHER: String = """
(function(){
  'use strict';
  var G = globalThis;
  var SETTLED = { __gySettled: true };

  // 待结算的 async 调用槽位。settle() 只**登记** Promise 状态，绝不在这里驱动 job 队列：
  // 从 JS host 回调里驱动 QuickJS 的 executePendingJob，会把「一个微任务里再起微任务」的链
  // 全部压在**同一次 native 调用**内，墙钟预算只在每轮之间检查 → 真机上实测把
  // DefaultDispatch 钉在 Java_com_dokar_quickjs_QuickJs_executePendingJob 里 100% CPU
  // 空转数分钟（B站/清风DJ/好听轻音乐「搜周杰伦」永远不出结果、也不报错的真因）。
  // 现在由 Kotlin 侧在**顶层**（不在任何 JS 回调里）一次推一个 job，逐步推进 + 逐步检查时限。
  G.__gy_pending = null;

  function settle(result, fnName) {
    if (!result || typeof result.then !== 'function') {
      return { ok: true, value: result };
    }
    var state = { done: false, ok: false, value: undefined, error: null };
    try {
      result.then(function (v) { state.done = true; state.ok = true; state.value = v; },
                  function (e) { state.done = true; state.ok = false; state.error = e; });
    } catch (e) {
      return { ok: false, error: 'function ' + fnName + ' returned a broken Promise: ' + describe(e) };
    }
    G.__gy_pending = state;
    return { pending: true };
  }

  // 顶层轮询入口（Kotlin 侧每推一个 job 之后调用一次）：
  //   { pending: true } → 还没结算，宿主继续 drain
  //   { pending: false, result: <envelope json text> } → 已结算，result 就是 __gy_call 的返回值
  G.__gy_poll = function () {
    var state = G.__gy_pending;
    if (!state) return { pending: false, result: JSON.stringify({ ok: false, error: 'pending call lost' }) };
    if (!state.done) return { pending: true };
    G.__gy_pending = null;
    if (!state.ok) return { pending: false, result: JSON.stringify({ ok: false, error: describeWithStack(state.error) }) };
    var payload = JSON.stringify(state.value === undefined ? null : state.value);
    if (payload === undefined) payload = 'null';
    return { pending: false, result: JSON.stringify({ ok: true, data: payload }) };
  };

  // 宿主在**顶层**驱动 Promise 队列的入口：每次 evaluate 调用一次，最多跑 ms 毫秒。
  // 每次推进都走 `__gy_drainOneJob` —— 那是 Kotlin 注入的 host 函数，内部在 binding 回调里
  // 调 `JS_ExecutePendingJob`（只有这个上下文才能安全推进，直接调会卡死运行时锁）。
  // 到时限或结算就返回，把控制权交回 Kotlin，由 Kotlin 检查总预算。
  G.__gy_pump = function (ms) {
    var started = (typeof Date !== 'undefined' && Date.now) ? Date.now() : 0;
    var advanced = 0;
    while (true) {
      if (G.__gy_pending && G.__gy_pending.done) break;
      if (started && Date.now() - started >= ms) break;
      var moved = false;
      if (typeof G.__gy_flushTimers === 'function') { try { moved = (G.__gy_flushTimers() || 0) > 0; } catch (e) { /* ignore */ } }
      if (typeof G.__gy_deliverHttp === 'function') { try { if (G.__gy_deliverHttp() > 0) moved = true; } catch (e) { /* ignore */ } }
      if (typeof G.__gy_drainOneJob !== 'function') break;
      try { if (G.__gy_drainOneJob() > 0) moved = true; } catch (e) { break; }
      if (!moved) {
        // M8：还有请求在路上或计时器没到期 → 让出几毫秒再看，而不是判「卡死」。
        var waiting = (typeof G.__gy_httpPending === 'function' && G.__gy_httpPending()) ||
                      (typeof G.__gy_timersPending === 'function' && G.__gy_timersPending());
        if (waiting && typeof G.__gy_idle === 'function') { G.__gy_idle(5); continue; }
        break;
      }
      advanced++;
      if (advanced > 20000) break;
    }
    return G.__gy_poll();
  };

  G.__gy_call = function (fnName, argsJson) {
    var args;
    try { args = argsJson ? JSON.parse(argsJson) : []; } catch (e) { args = []; }
    if (Object.prototype.toString.call(args) !== '[object Array]') args = [args];
    try {
      var plugin = G.__plugin;
      if (!plugin) return JSON.stringify({ ok: false, error: 'plugin object missing' });
      var fn = plugin[fnName];
      if (typeof fn !== 'function') {
        return JSON.stringify({ ok: false, missing: true, error: 'function ' + fnName + ' not implemented' });
      }
      var raw = fn.apply(plugin, args);
      if (typeof G.__gy_flushTimers === 'function') G.__gy_flushTimers();
      var s = settle(raw, fnName);
      if (s.pending) {
        // 结果还没到：让 Kotlin 侧在顶层推 job 队列，再用 __gy_poll 取结果。
        return JSON.stringify({ ok: false, pending: true });
      }
      if (!s.ok) {
        return JSON.stringify({ ok: false, error: s.error });
      }
      // Serialising the payload twice keeps the envelope safe against any plugin data:
      // `data` carries the JSON *text* of the result, the host parses it a second time.
      // Nothing the plugin returns (toJSON hooks, getters, cycles) can corrupt the envelope.
      var payload = JSON.stringify(s.value === undefined ? null : s.value);
      if (payload === undefined) payload = 'null';
      return JSON.stringify({ ok: true, data: payload });
    } catch (e) {
      return JSON.stringify({ ok: false, error: describe(e), stack: e && e.stack ? String(e.stack) : '' });
    }
  };

  function describe(e) {
    if (!e) return 'unknown error';
    if (typeof e === 'string') return e;
    return (e.message ? String(e.message) : String(e));
  }

  // 只把「类型错误的拒绝值」（字符串 / null / 数字）也补上现场：插件里 Promise.reject(x)
  // 很常见，丢掉 x 的类型就只有一句 "not a function"，根本看不出它从哪来。
  function describeWithStack(e) {
    var msg = describe(e);
    if (e && typeof e === 'object' && e.stack) return msg + '\n' + String(e.stack);
    return msg + '\n(no stack: rejected with ' + (e === null ? 'null' : typeof e) + ')';
  }

  G.__gy_describe = describe;
})();
"""
}
