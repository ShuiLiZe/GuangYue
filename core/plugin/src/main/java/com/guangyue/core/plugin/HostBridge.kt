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
  G.__gy_require = function (id) {
    if (id === 'axios') return G.axios;
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
    return {
      status: res.status || 0,
      statusText: res.statusText || '',
      headers: res.headers || {},
      data: res.data,
      config: {},
      request: {}
    };
  }

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
      var err = new Error('Network Error: ' + (e && e.message ? e.message : String(e)));
      err.config = config;
      throw err;
    }
    var response = makeResponse(raw);
    var status = response.status;
    if (status >= 200 && status < 300) return response;
    if (status >= 300 && status < 400) return response;
    var e2 = new Error('Request failed with status code ' + status);
    e2.response = response;
    e2.config = config;
    throw e2;
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
  G.require = G.__gy_require;
  G.process = {
    env: { NODE_ENV: 'production' },
    platform: 'android',
    version: 'v18.0.0',
    nextTick: function (fn) { G.setTimeout(fn, 0); }
  };
})();
"""

    /**
     * Wraps plugin source into a CommonJS module factory.
     *
     * The source runs inside `function(module, exports, require)` so both
     * `module.exports = {...}` and `exports.foo = ...` work, exactly like in MusicFree.
     */
    fun wrapPluginSource(source: String): String = buildString {
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
     */
    const val CALL_DISPATCHER: String = """
(function(){
  'use strict';
  var G = globalThis;
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
      var result = fn.apply(plugin, args);
      try {
        if (typeof G.__gy_flushTimers === 'function') G.__gy_flushTimers();
        if (result && typeof result.then === 'function') {
          return JSON.stringify({ ok: false, error: 'function ' + fnName + ' returned a Promise; MusicFree plugins must be synchronous' });
        }
        // Serialising the payload twice keeps the envelope safe against any plugin data:
        // `data` carries the JSON *text* of the result, the host parses it a second time.
        // Nothing the plugin returns (toJSON hooks, getters, cycles) can corrupt the envelope.
        var payload = JSON.stringify(result === undefined ? null : result);
        if (payload === undefined) payload = 'null';
        return JSON.stringify({ ok: true, data: payload });
      } catch (e2) {
        return JSON.stringify({ ok: false, error: describe(e2), stack: e2 && e2.stack ? String(e2.stack) : '' });
      }
    } catch (e) {
      return JSON.stringify({ ok: false, error: describe(e), stack: e && e.stack ? String(e.stack) : '' });
    }
  };

  function describe(e) {
    if (!e) return 'unknown error';
    if (typeof e === 'string') return e;
    return (e.message ? String(e.message) : String(e));
  }

  G.__gy_describe = describe;
})();
"""
}
