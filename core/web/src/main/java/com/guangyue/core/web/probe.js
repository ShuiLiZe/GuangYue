/**
 * 插件运行时探针：把一个表达式在 QuickJS 里跑一遍，返回它的 JS 类型与 JSON 化结果。
 *
 * 为什么需要：quickjs-kt 的 `evaluate<T>` 会按 Kotlin 类型做 TypeConverter 转换，失败时抛的
 * 是「No such type converter ...」，与插件自身的错误混在一起，排障时看不到真实原因。这里
 * 统一「字符串进、字符串出」：JS 侧先 `typeof` + `JSON.stringify`，Kotlin 侧只读字符串。
 *
 * 用法：debug 构建里由 `LibraryGateway.debugEval` 调用。
 */
export const PROBE_JS = `
(function () {
  var G = globalThis;
  if (G.__gy_probe) return 'installed';
  G.__gy_probe = function (expr) {
    try {
      var value = eval(expr);
      var type = Object.prototype.toString.call(value);
      var json;
      try { json = JSON.stringify(value); } catch (e) { json = '<unserializable>'; }
      return JSON.stringify({ type: type, typeof: typeof value, json: json });
    } catch (e) {
      return JSON.stringify({ error: String(e && e.message ? e.message : e) });
    }
  };
  return 'installed';
})();
`;
