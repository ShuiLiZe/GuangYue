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
    __timers.push({ id: id, fn: fn, delay: delay || 0, args: Array.prototype.slice.call(arguments, 2), clear: false });
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
  G.__gy_flushTimers = function () {
    var pending = __timers;
    __timers = [];
    var limit = 200;
    for (var i = 0; i < pending.length && limit-- > 0; i++) {
      var t = pending[i];
      if (t.clear) continue;
      try { t.fn.apply(null, t.args); } catch (e) {
        try { G.__gy_log('error', 'timer: ' + (e && e.message ? e.message : String(e))); } catch (_) {}
      }
    }
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
    attachChain(out);
    return out;
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
   * 这里给响应对象 + 包装后的 Promise 挂一条**已经结算**的最小 then 链：`.then` 立刻同步执行回调，
   * `.catch` 对成功响应是空操作（返回自身），`.finally` 立刻执行。语义与「已 resolve 的 Promise」
   * 一致，且不依赖微任务队列。
   */
  function attachChain(obj, err) {
    if (!obj || typeof obj !== 'object') return obj;
    if (obj.__gyChain) return obj;
    Object.defineProperty(obj, '__gyChain', { value: true, enumerable: false });
    obj.then = function (onOk, onErr) {
      if (err) {
        if (typeof onErr === 'function') {
          try {
            var r = onErr(err);
            return r === undefined ? attachChain({}, null) : r;
          } catch (e) { return attachChain({}, e); }
        }
        return attachChain({}, err);
      }
      if (typeof onOk !== 'function') return obj;
      try {
        var v = onOk(obj);
        return v === undefined ? obj : v;
      } catch (e) {
        return attachChain({}, e);
      }
    };
    obj.catch = function (onErr) {
      if (err && typeof onErr === 'function') {
        try {
          var r = onErr(err);
          return r === undefined ? attachChain({}, null) : r;
        } catch (e) { return attachChain({}, e); }
      }
      return obj;
    };
    obj.finally = function (fn) {
      if (typeof fn === 'function') { try { fn(); } catch (e) { /* ignore */ } }
      return obj;
    };
    return obj;
  }
  G.__gy_attachChain = attachChain;

  function request(config) {
    if (typeof config === 'string') config = { url: config };
    config = config || {};
    var method = String(config.method || 'get').toUpperCase();
    var url = config.url;
    if (!url) throw new Error('axios: url is required');

    var params = config.params;
    if (params && typeof params === 'object') {
      var pairs = [];
      for (var k in params) {
        if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
        var v = params[k];
        if (v === undefined || v === null) continue;
        if (Object.prototype.toString.call(v) === '[object Array]') {
          for (var i = 0; i < v.length; i++) pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v[i]));
        } else {
          pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
        }
      }
      if (pairs.length) url += (url.indexOf('?') >= 0 ? '&' : '?') + pairs.join('&');
    }

    var data = config.data;
    if (data !== undefined && data !== null && typeof data !== 'string') {
      try { data = JSON.stringify(data); } catch (e) { data = String(data); }
    }

    var headers = config.headers || {};
    var flatHeaders = {};
    for (var h in headers) {
      if (!Object.prototype.hasOwnProperty.call(headers, h)) continue;
      var hv = headers[h];
      if (hv === undefined || hv === null) continue;
      flatHeaders[h] = String(hv);
    }

    var payload = JSON.stringify({
      method: method,
      url: String(url),
      headers: flatHeaders,
      body: data === undefined || data === null ? null : String(data),
      responseType: String(config.responseType || 'json'),
      timeout: typeof config.timeout === 'number' ? config.timeout : 0
    });

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
    var response = makeResponse(raw);
    var status = response.status;
    if (status >= 200 && status < 300) return response;
    if (status >= 300 && status < 400) return response;
    // HTTP 非 2xx/3xx 同理：回一个带错误链的响应，让插件的 .catch 能处理。
    var httpErr = new Error('Request failed with status code ' + status);
    httpErr.response = response;
    httpErr.config = config;
    return attachChain(response, httpErr);
  }

  function axios(config) { return request(config); }
  axios.request = request;
  axios.get = function (url, config) {
    config = config || {};
    config.url = url; config.method = 'get';
    return request(config);
  };
  axios.delete = function (url, config) {
    config = config || {}; config.url = url; config.method = 'delete';
    return request(config);
  };
  axios.head = function (url, config) {
    config = config || {}; config.url = url; config.method = 'head';
    return request(config);
  };
  axios.post = function (url, data, config) {
    config = config || {}; config.url = url; config.method = 'post'; config.data = data;
    return request(config);
  };
  axios.put = function (url, data, config) {
    config = config || {}; config.url = url; config.method = 'put'; config.data = data;
    return request(config);
  };
  axios.patch = function (url, data, config) {
    config = config || {}; config.url = url; config.method = 'patch'; config.data = data;
    return request(config);
  };
  axios.defaults = { headers: { common: {} } };
  axios.create = function () { return axios; };
  axios.all = function (arr) { return arr; };
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
    // `async function` 的续体挂在 job 队列上：只挂 then 不驱动 job，`state.done` 永远是 false。
    // 这里把已入队的微任务跑掉（`__gy_drainJobs` 由宿主注入，内部调用 QuickJS 的
    // executePendingJob 直到队列空）。await 的续体可能是**几段**：跑一轮只推进到下一个
    // await 点，所以循环 drain 直到结算，或某一轮没有任何推进为止。
    var rounds = 0;
    while (!state.done && rounds++ < 64) {
      // 定时器要在每一轮再兑现一次：`__gy_call` 只在函数返回后 flush 一轮，
      // 而 `await sleep()`（先 await、再在续体里 setTimeout）把定时器注册在第一次 flush 之后，
      // 不在这里补 flush，那个 Promise 永远不会有 resolve 的机会 → 死等到超时。
      if (typeof G.__gy_flushTimers === 'function') { try { G.__gy_flushTimers(); } catch (e) { /* ignore */ } }
      if (typeof G.__gy_drainJobs !== 'function') break;
      var progressed = false;
      try { progressed = G.__gy_drainJobs() > 0; } catch (e) { break; }
      if (!progressed) break;
    }
    if (!state.done) {
      return {
        ok: false,
        error: 'function ' + fnName + ' returned a Promise that has not settled; ' +
               'MusicFree plugins must return synchronously (use axios with a sync bridge)'
      };
    }
    return state.ok ? { ok: true, value: state.value } : { ok: false, error: describeWithStack(state.error) };
  }

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
