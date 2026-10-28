package com.guangyue.core.plugin.runtime

/**
 * 「插件元信息 → JSON」的解析器与自检插件。
 *
 * 这部分刻意用纯字符串拼出来（不依赖 Kotlin 反射、不依赖 quickjs-kt 的 TypeConverter），
 * 单元测试可以直接把它喂进 JVM 上的 QuickJS 跑，也可以在 Node 里跑。
 *
 * 元信息里任何字段都可能是**任意 JSON 值**：真实订阅源里的插件把 `version` 写成数字、
 * `cacheControl` 写成 `false`、`supportedSearchType` 写成字符串、`hints` 写成数组，
 * 甚至整段不写。解析规则统一为：**是什么就序列化成什么**，把「类型是否贴合」留到
 * Kotlin 侧 [com.guangyue.core.plugin.PluginMetaCodec] 处理。
 */
internal object PluginMetaProbe {

    /**
     * 在已安装好插件的 runtime 里执行，返回插件导出元信息的 JSON **文本**。
     *
     * `__gy_meta_json(keysJson)` 接收要探测的函数名数组（JSON 文本），返回
     * `JSON.stringify({platform, version, author, srcUrl, supportedSearchType, hints,
     * primaryKey, cacheControl, supportedQualities, functions, ... __error?})`。
     */
    const val SCRIPT: String = """
(function(){
  'use strict';
  var G = globalThis;
  if (typeof G.__gy_meta_json === 'function') return;

  function raw(v){
    // 只舍弃「无法 JSON 化」的值：undefined / function / symbol / 循环引用。
    if (v === undefined || v === null) return null;
    var t = typeof v;
    if (t === 'function' || t === 'symbol') return null;
    if (t === 'number' && !isFinite(v)) return null;
    try { JSON.stringify(v); } catch (e) { return null; }
    return v;
  }

  function firstRaw(list){
    for (var i = 0; i < list.length; i++) {
      var v = raw(list[i]);
      if (v !== null && v !== '') return v;
    }
    return null;
  }

  G.__gy_meta_json = function (keysJson) {
    var p = G.__plugin;
    if (!p || typeof p !== 'object') {
      return JSON.stringify({ __error: 'module.exports is not an object' });
    }
    var names = [];
    try { names = keysJson ? JSON.parse(keysJson) : []; } catch (e) { names = []; }
    var fns = [];
    for (var i = 0; i < names.length; i++) {
      var fn = '';
      try { fn = p[names[i]]; } catch (e) { fn = undefined; }
      if (typeof fn === 'function') fns.push(names[i]);
    }
    var out = {
      platform: firstRaw([p.platform, p.name]),
      name: firstRaw([p.name]),
      version: firstRaw([p.version]),
      srcUrl: firstRaw([p.srcUrl]),
      author: firstRaw([p.author]),
      description: firstRaw([p.description]),
      iconUrl: firstRaw([p.iconUrl, p.icon]),
      icon: firstRaw([p.icon]),
      cacheControl: firstRaw([p.cacheControl]),
      primaryKey: firstRaw([p.primaryKey]),
      hints: firstRaw([p.hints]),
      supportedSearchType: firstRaw([p.supportedSearchType]),
      supportedQualities: firstRaw([p.supportedQualities]),
      supportedQuality: firstRaw([p.supportedQuality]),
      functions: fns
    };
    try {
      return JSON.stringify(out);
    } catch (e) {
      return JSON.stringify({ __error: String(e && e.message ? e.message : e) });
    }
  };
})();
"""

    /**
     * 自检插件：每个字段都**故意**不是字符串（布尔、数字、数组、对象、不完整的对象），
     * 或者干脆缺失。`variant` 选择不同变体：
     *
     * | 变体 | platform | version | cacheControl | 其他 |
     * | --- | --- | --- | --- | --- |
     * | `boolean` | `true` | `1` | `false` | author 是数字，hints 是对象 |
     * | `number` | `42` | `1.5` | `0` | primaryKey 是数组 |
     * | `array` | `['list','b']` | `['1','2']` | `['no-cache']` | supportedSearchType 是字符串 |
     * | `missing` | 缺失 | 缺失 | 缺失 | supportedSearchType 是对象 |
     *
     * 每个变体都提供 `search()`，让安装后的插件仍然可用（不会被判成「空插件」）。
     */
    fun selfTestPlugin(variant: String): String = when (variant) {
        "number" -> SELF_TEST_NUMBER
        "array" -> SELF_TEST_ARRAY
        "missing" -> SELF_TEST_MISSING
        else -> SELF_TEST_BOOLEAN
    }

    val SELF_TEST_VARIANTS: List<String> = listOf("boolean", "number", "array", "missing")

    private val COMMON_FUNCTION = """
    search: function (query, page, type) {
      return { isEnd: true, data: [] };
    },
    getMediaSource: function (musicItem, quality) {
      return { url: 'https://example.com/a.mp3' };
    }
"""

    private val SELF_TEST_BOOLEAN: String = """
module.exports = {
  platform: true,
  name: '自检插件-布尔',
  version: 1,
  cacheControl: false,
  author: 1024,
  srcUrl: 42,
  description: ['第一行', '第二行'],
  supportedSearchType: 'music',
  supportedQualities: [320, 128, 'flac'],
  hints: { debug: true, retries: 3 },
  primaryKey: 'id',
$COMMON_FUNCTION
};
""".trimIndent()

    private val SELF_TEST_NUMBER: String = """
module.exports = {
  platform: 42,
  version: 1.5,
  cacheControl: 0,
  author: 3.14,
  supportedQualities: 320,
  hints: [1, 2, 3],
  primaryKey: ['id', 'platform'],
$COMMON_FUNCTION
};
""".trimIndent()

    private val SELF_TEST_ARRAY: String = """
module.exports = {
  platform: ['list', 'b'],
  name: ['演示', 2],
  version: ['1', '2'],
  cacheControl: ['no-cache'],
  srcUrl: ['https://example.com/p.js'],
  supportedSearchType: ['music', 'album', 7, null],
  supportedQualities: ['320', 128, null, 'flac'],
  primaryKey: { a: 1 },
$COMMON_FUNCTION
};
""".trimIndent()

    private val SELF_TEST_MISSING: String = """
module.exports = {
  description: null,
  supportedSearchType: { all: true },
$COMMON_FUNCTION
};
""".trimIndent()

    /** 函数名数组的 JSON，传给 `__gy_meta_json`。 */
    fun keysJson(keys: List<String>): String =
        keys.joinToString(",", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

    /** 调用表达式的完整源码。 */
    fun callSource(keys: List<String>): String =
        "__gy_meta_json(${keysJson(keys)})"
}
