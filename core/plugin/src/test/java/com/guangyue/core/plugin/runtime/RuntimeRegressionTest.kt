package com.guangyue.core.plugin.runtime

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 0.0.4 全量体检里修掉的**运行时共性**问题的回归用例。
 *
 * 每一条都对应 `docs/plugin-health-0.0.4.md` 里的一类失败，拿最小插件复现，
 * 不依赖网络、不依赖那 6 个真机 fixture。
 */
class RuntimeRegressionTest {

    private fun assumeQuickJs() {
        val rt = runCatching { PluginRuntimeHarness.runtimeFor("module.exports = { platform: 'p' };") }
        assumeTrue("QuickJS native not available on this host", rt.isSuccess)
        runCatching { rt.getOrThrow().install() }
        runCatching { rt.getOrThrow().close() }
    }

    /**
     * 回归：`probeFunctions()` 的表达式必须是完整 IIFE。
     *
     * 0.0.3 拆分时把它拆成了悬空片段（丢了 `(function(){` 和 `var out = []`），解析失败被
     * `runCatching` 吞掉 → 恒返回空集 → 57 个插件全被判成「exports 里没有 search」。
     */
    @Test
    fun `probeFunctions sees every implemented function`() {
        assumeQuickJs()
        val src = """
            module.exports = {
              platform: 'probe_fixture',
              version: '1.0.0',
              name: 'probe fixture',
              search: function (q) { return { isEnd: true, data: [] }; },
              getMediaSource: function (m) { return { url: 'x' }; },
              getLyric: function (m) { return null; }
            };
        """.trimIndent()
        PluginRuntimeHarness.runtimeFor(src).let { rt ->
            rt.install()
            val fns = rt.probeFunctions()
            assertTrue("probe 漏了 search，实际=$fns", "search" in fns)
            assertTrue("probe 漏了 getMediaSource，实际=$fns", "getMediaSource" in fns)
            assertTrue("probe 漏了 getLyric，实际=$fns", "getLyric" in fns)
            assertTrue("probe 多报了未实现的函数：$fns", "getTopLists" !in fns)
        }
    }

    /**
     * 回归：`async function` + 多层 `await` 必须能同步结算。
     *
     * 三个坑叠在一起：
     *  1. `settle()` 只挂 `then` 不驱动 job → `state.done` 永远是 false；
     *  2. 驱动了但只跑一轮 job → 多层 await 只推进到下一个 await 点；
     *  3. 宿主 `__gy_drainJobs` 返回 `args.size`（恒 0）→ JS 侧第一轮就判「无进展」退出。
     *
     * 缺任何一个，喜马拉雅 / 蜻蜓FM / 火龙DJ / 皮狗DJ / B站 都会停在
     * "function search returned a Promise that has not settled"。
     */
    @Test
    fun `async handler with chained awaits settles synchronously`() {
        assumeQuickJs()
        val src = """
            async function step(n) {
              return await Promise.resolve(n + 1);
            }
            async function deep(n) {
              var a = await step(n);
              var b = await step(a);
              var c = await step(b);
              return c;
            }
            module.exports = {
              platform: 'async_fixture',
              version: '1.0.0',
              name: 'async fixture',
              search: async function (q) {
                var v = await deep(0);
                var w = await Promise.all([step(v), step(v)]);
                return { isEnd: true, data: [], total: w[0] + w[1] };
              }
            };
        """.trimIndent()
        PluginRuntimeHarness.runtimeFor(src).let { rt ->
            rt.install()
            val out = rt.call("search", JsonPrimitive("x")).toString()
            assertTrue("多层 await 没结算，返回=$out", "total" in out)
            // 0→1→2→3，再各 +1 = 4+4 = 8
            assertTrue("多层 await 结果不对，返回=$out", "8" in out)
        }
    }

    /**
     * 回归：`axios.get(url).then(...).catch(...)` 必须能跑通。
     *
     * axios 桥是同步的、返回普通对象；插件的 `.then` / `.catch` 在混淆后也是字符串索引调用。
     * 不给响应对象挂已结算链，就只剩一句 "not a function"。
     */
    @Test
    fun `sync axios response carries then and catch`() {
        assumeQuickJs()
        val src = """
            var axios = require('axios');
            module.exports = {
              platform: 'axios_fixture',
              version: '1.0.0',
              name: 'axios fixture',
              search: function (q) {
                var seen = 'untouched';
                var r;
                try {
                r = axios.get('https://example.invalid/nope');
                // 关键：不管请求成功与否，响应对象上 .then / .catch 都要存在且可调用。
                if (!r || typeof r.then !== 'function') return { isEnd: true, data: [], seen: 'no-then' };
                if (typeof r.catch !== 'function') return { isEnd: true, data: [], seen: 'no-catch' };
                // M8：axios 现在返回真 Promise（异步并发 HTTP），结果要等宿主推进后才有。
                return r.then(function () { return { isEnd: true, data: [], seen: 'then' }; })
                        .catch(function () { return { isEnd: true, data: [], seen: 'catch' }; });
                } catch (e) {
                  return { isEnd: true, data: [], seen: 'threw:' + (e && e.message ? e.message : e) };
                }
              }
            };
        """.trimIndent()
        PluginRuntimeHarness.runtimeFor(src).let { rt ->
            rt.install()
            val out = rt.call("search", JsonPrimitive("x")).toString()
            assertTrue("response 上没有 then/catch 链：$out", "then" in out || "catch" in out)
        }
    }

    /** 元信息显式写 `primaryKey: null` 是合法的，不能被类型体检误判。 */
    @Test
    fun `meta with null primaryKey is accepted`() {
        assumeQuickJs()
        val src = """
            module.exports = {
              platform: 'meta_fixture',
              version: '2.0.0',
              name: 'meta fixture',
              cacheControl: 'no-store',
              primaryKey: null,
              search: function (q) { return { isEnd: true, data: [] }; }
            };
        """.trimIndent()
        PluginRuntimeHarness.runtimeFor(src).let { rt ->
            rt.install()
            val meta = rt.readMetaJson()
            assertTrue("platform 丢了：$meta", "meta_fixture" in meta)
            assertTrue("primaryKey 丢了：$meta", "primaryKey" in meta)
        }
    }
}
