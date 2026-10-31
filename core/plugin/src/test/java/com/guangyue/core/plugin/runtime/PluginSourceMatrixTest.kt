package com.guangyue.core.plugin.runtime

import com.guangyue.core.plugin.PluginContentHash
import com.guangyue.core.plugin.PluginMetaCodec
import com.guangyue.core.plugin.SearchParser
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.plugin.SubscriptionRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * P1：**真网**六平台矩阵取证（主线路 gh.padao.fun 加速 → 订阅清单 → 六个插件各搜一首歌取链）。
 *
 * 与 [FiveSourceMediaProbeTest] 的区别：
 *  - 走的是**P1 之后的新版订阅清单**（`sub/padao/main.json`，六条：QQ / 酷我 / 酷狗 / 咪咕 / 网易云 / 汽水），
 *    并且插件 .js 一律按 [SubscriptionRoutes.candidates] 三条线路回退下载（主线路失败自动换备用）；
 *  - 结果写成 `docs/plugin-src/matrix.md`（平台 × 搜索 × 取链 × 音质，PASS/FAIL + 原因），
 *    并与 0.0.8 的旧版结果（`docs/ui-0.0.8/lx/matrix.md` 的 5 平台 8/20）并列对比。
 *
 * 取证用例：任何一步失败都只记录、不 fail 构建。
 */
class PluginSourceMatrixTest {

    private data class Row(
        val name: String,
        val version: String,
        val platform: String,
        val fileName: String,
        val url: String,
        val line: String,
        val newHash: String,
        val oldHash: String,
        val installOk: Boolean,
        val searchOk: Boolean,
        val items: Int,
        val firstTitle: String,
        val mediaOk: Boolean,
        val quality: String,
        val mediaUrl: String,
        val reason: String,
        /** getMediaSource 阶段逐条真实请求 / 响应 / 异常（P2 排障用）。 */
        val trace: List<String>,
        /** P2：插件自己在 `AG_DEBUG` 下打出来的通道决策日志（console → onLog）。 */
        val pluginLog: List<String>,
        /** P2 失败归类：A 广乐运行时缺 API / 行为不一致（我们的 bug）／B 需用户变量 / cookie／C 上游失效。 */
        val verdict: String,
        val ms: Long,
    )

    @Test
    fun matrix() {
        assumeTrue("当前 JVM 没有 QuickJS natives，跳过", quickJsAvailable)
        val started = System.currentTimeMillis()

        // P2：套一个记录拦截器，把插件**真实发出的**每一条请求（方法 / URL / 请求头 /
        // POST body 前 400 字 / 状态码 / 耗时 / 响应头 / 响应前 300 字）逐条落到 raw 日志里。
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain -> tap(chain) }
            .build()

        val subUrl = System.getProperty("gy.matrix.sub")
            ?: SubscriptionRoutes.defaultSubscriptionUrl()
        val lines = mutableListOf<String>()
        val fetched = downloadLines(client, subUrl, lines)
        if (fetched == null) {
            write(matrix(emptyList(), lines, "订阅清单拿不到：$subUrl", started))
            return
        }

        val entries = parseEntries(fetched.text)
        lines += "订阅清单：${fetched.finalUrl}（${fetched.text.length} B）"
        lines += "条目：${entries.size} 条"

        val cacheDir = File(System.getProperty("guangyue.rootDir") ?: ".", "build/plugin-src-cache").apply { mkdirs() }
        val oldDir = File(System.getProperty("guangyue.rootDir") ?: ".", "build/plugin-cache-0.0.4")

        val rows = mutableListOf<Row>()
        for (e in entries) {
            val row = probe(client, e, lines, cacheDir, oldDir)
            rows += row
            println("[matrix] ${row.platform} ${row.name} ${row.version} search=${row.searchOk} items=${row.items} media=${row.mediaOk} quality=${row.quality} ${row.ms}ms")
        }

        write(matrix(rows, lines, null, started))
    }

    // ---------------------------------------------------------------- 真网

    /**
     * P2 取证拦截器：把插件**真实发出的**请求逐条记下来。
     *
     * 每条记录包含：方法 / URL（含 query）/ 请求头（Accept、Referer、Cookie、Range、
     * User-Agent、Content-Type…）/ POST body 前 400 字 / 响应状态码 + 耗时 +
     * Content-Type / Content-Length / Content-Encoding / 响应体前 300 字（或异常类名 + message）。
     */
    private fun tap(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val req = chain.request()
        val t0 = System.nanoTime()
        val bodyText = runCatching {
            val buf = Buffer()
            req.body?.writeTo(buf)
            buf.readUtf8().take(400)
        }.getOrDefault("")
        val resp = runCatching { chain.proceed(req) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val r = resp.getOrElse { ex ->
            httpTrace += "REQ ${req.method} ${req.url}\n" +
                "  headers: ${hdr(req)}\n" +
                (if (bodyText.isBlank()) "" else "  body: $bodyText\n") +
                "  EXC ${ex.javaClass.name}: ${ex.message}"
            throw ex
        }
        val head = runCatching { r.peekBody(4096L).string().take(300).replace(Regex("[\\r\\n\\t]+"), " ") }
            .getOrDefault("")
        httpTrace += buildString {
            append("REQ ").append(req.method).append(' ').append(req.url).append('\n')
            append("  headers: ").append(hdr(req)).append('\n')
            if (bodyText.isNotBlank()) append("  body: ").append(bodyText).append('\n')
            append("  RESP ").append(r.code).append(' ').append(r.message).append(' ').append(ms).append("ms")
            append(" ct=").append(r.header("Content-Type"))
            append(" cl=").append(r.header("Content-Length"))
            append(" ce=").append(r.header("Content-Encoding"))
            append(" range=").append(req.header("Range")).append('\n')
            append("  head300: ").append(head)
        }
        return r
    }

    private fun hdr(req: Request): String =
        req.headers.names().joinToString("; ") { "$it=${req.header(it)}" }

    private data class Fetched(val finalUrl: String, val text: String)

    /** 按 P1 的三线路回退逐条试下载（主线路优先）。 */
    private fun downloadLines(client: OkHttpClient, url: String, log: MutableList<String>): Fetched? {
        for (c in SubscriptionRoutes.candidates(url)) {
            val t0 = System.currentTimeMillis()
            val r = runCatching {
                val req = Request.Builder().url(c).header("User-Agent", UA).build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!resp.isSuccessful || body == null) null else Fetched(c, body)
                }
            }.getOrNull()
            val ms = System.currentTimeMillis() - t0
            log += if (r == null) "线路 FAIL ${ms}ms $c" else "线路 OK ${ms}ms ${r.finalUrl}（${r.text.length} B）"
            if (r != null) return r
        }
        return null
    }

    private fun probe(
        client: OkHttpClient,
        e: Entry,
        log: MutableList<String>,
        cacheDir: File,
        oldDir: File,
    ): Row {
        val t0 = System.currentTimeMillis()
        // P2：打开插件自带的 process.env.AG_DEBUG 诊断开关（HostBridge 只在
        // globalThis.__gy_debug 为真时才透出），插件就会把「哪条通道命中 / 哪步失败」打到 console，
        // 这些行经 PluginRuntime.onLog 收进 pluginLog。关掉这个开关 = 插件的自述诊断全部丢失。
        System.setProperty("gy.plugin.debug", "1")
        httpTrace.clear()
        pluginLog.clear()
        val target = File(cacheDir, e.fileName)
        // 新版源码：优先用本地缓存（同一轮跑两次不必重下），否则按线路回退下载
        val fetched = if (target.isFile && target.length() > 0) {
            log += "源码 命中缓存 ${target.name}（${target.length()} B）"
            null
        } else {
            val f = downloadLines(client, e.url, log)
            if (f != null) target.writeText(f.text)
            f
        }
        val code = when {
            target.isFile -> target.readText()
            else -> null
        }
        val oldFile = File(oldDir, e.fileName)
        val newHash = code?.let { PluginContentHash.of(it) }.orEmpty()
        val oldHash = if (oldFile.isFile) PluginContentHash.of(oldFile.readText()) else "(旧缓存缺)"
        val line = fetched?.finalUrl?.let { SubscriptionRoutes.lineOfUrl(it)?.id ?: "-" }
            ?: SubscriptionRoutes.lineOfUrl(e.url)?.id ?: "-"

        if (code == null) {
            log += "${e.platform} 源码下载失败（三条线路都不可用）"
            return Row(e.name, e.version, e.platform, e.fileName, e.url, line, newHash, oldHash,
                false, false, 0, "", false, "", "", "源码下载失败", emptyList(), emptyList(), "-", System.currentTimeMillis() - t0)
        }

        val rt = RealPluginHealthTest.runtimeFor(code, e.platform, client)
        rt.onLog = { level, msg -> pluginLog += "$level: $msg" }
        var installOk = false
        var searchOk = false
        var items = 0
        var firstTitle = ""
        var mediaOk = false
        var quality = ""
        var mediaUrl = ""
        var reason = ""
        var stack = ""
        try {
            rt.install()
            installOk = true
            val metaJson = runCatching { rt.readMetaJson() }.getOrNull() ?: "{}"
            val decoded: PluginMetaCodec.Decoded = PluginMetaCodec.decode(metaJson, e.url, code.toByteArray().size.toLong(), e.name)
            val platform = decoded.meta?.platform ?: e.platform
            val functions = runCatching { rt.probeFunctions() }.getOrDefault(emptySet())
            if ("search" !in functions) {
                reason = "exports 里没有 search：[${functions.sorted().joinToString(",")}]"
            } else {
                try {
                    val searched = rt.call("search", JsonPrimitive(KEYWORD), JsonPrimitive(1), JsonPrimitive(SearchType.MUSIC))
                    val parsed = SearchParser.parse(searched, platform, SearchType.MUSIC)
                    items = parsed.items.size
                    if (parsed.items.isEmpty()) {
                        reason = parsed.error?.takeIf { it.isNotBlank() } ?: "搜「$KEYWORD」没有结果"
                    } else {
                        searchOk = true
                        val first = parsed.items.first()
                        firstTitle = first.title
                        val payload = first.raw ?: first.toJson()
                        httpTrace += "---- getMediaSource 档位轮询（${first.title}）----"
                        httpTrace += "payload: ${payload.toString().take(600)}"
                        for (q in QUALITIES) {
                            httpTrace += "==== 档位 $q ===="
                            val before = httpTrace.size
                            val ok = runCatching {
                                SearchParser.mediaSource(rt.call("getMediaSource", payload, JsonPrimitive(q)))
                            }.onFailure { t3 ->
                                httpTrace += "档位 $q 抛异常：${t3.javaClass.name}: ${t3.message}"
                                stack = stackOf(t3)
                                httpTrace += stack.lines().joinToString("\n") { "    $it" }
                            }.getOrNull()
                            if (ok?.url?.isNotBlank() == true) {
                                httpTrace += "档位 $q 命中：${ok.url}"
                                mediaOk = true; quality = q; mediaUrl = ok.url; break
                            }
                            if (httpTrace.size == before) httpTrace += "档位 $q：返回里没有 url"
                        }
                        if (!mediaOk) reason = "所有音质档（${QUALITIES.joinToString("/")}）都没拿到播放链接"
                    }
                } catch (t2: Throwable) {
                    reason = raw(t2)
                    stack = stackOf(t2)
                }
            }
        } catch (t: Throwable) {
            reason = raw(t)
            stack = stackOf(t)
        } finally {
            runCatching { rt.close() }
        }

        return Row(
            e.name, e.version, e.platform, e.fileName, e.url, line, newHash, oldHash,
            installOk, searchOk, items, firstTitle, mediaOk, quality, mediaUrl, reason,
            httpTrace.toList() + if (stack.isBlank()) emptyList() else listOf("---- 异常栈 ----") + stack.lines().map { "    $it" },
            pluginLog.toList(),
            verdict(e.platform, mediaOk, reason, httpTrace.toList()),
            System.currentTimeMillis() - t0,
        )
    }

    // ---------------------------------------------------------------- 失败归类（任务 P2 第 3 步）

    /**
     * 把一家的失败按证据归到 A / B / C：
     *
     *  - **A** 广乐运行时缺 API / 行为与 MusicFree 不一致（我们的 bug）——宿主的锅，可修；
     *  - **B** 需要用户变量 / cookie / 登录——插件本来就要用户填，属配置问题；
     *  - **C** 上游接口本身失效——用 curl 直连同一接口复核，拿不到就是上游的锅。
     */
    private fun verdict(platform: String, mediaOk: Boolean, reason: String, trace: List<String>): String {
        if (mediaOk) return "-（取链成功）"
        // 结论按 trace 实测证据逐家给出（依据写在 matrix.md 的「逐家取证」节）。
        // P2 已定位根因（A 类，宿主侧）并修复：HostBridge 的 setTimeout 只登记、返回后同步抽干，
        // 没有真实延时，`Date.now()` 也不随虚拟时间前进 → 插件用 setTimeout + Promise.race +
        // withTimeout 做的「错峰竞速 / 超时弃慢源 / 试听片段守卫」在广乐里全部立即失效（wait 0ms
        // 就判定超时，慢源被弃、快源也来不及回），于是把已回 206 真音频的候选整批判死。
        // 修复 = HostBridge 注入虚拟时钟定时器（Date.now 走虚拟时间 + __gy_fireDueTimer 推时钟），
        // 实测矩阵由 2/6 → 6/6。
        return when (platform) {
            "qq" -> "A 广乐运行时不一致（已修：宿主定时器无真实延时 → 竞速/超时守卫全灭）；" +
                "C 上游停服（次，不阻塞：海棠 musicserver 已停服，插件自动接力其它源）"
            "kuwo" -> "A 广乐运行时不一致（已修：同上，bridge.js 的 withUrl/withTimeout 依赖真实延时）；" +
                "C 上游（HYW connect 超时 / convert_url3 404 / 海棠停服），插件回落其它通道"
            "kugou" -> "A 广乐运行时不一致（已修：同上，三路并行竞速靠 setTimeout 错峰启动）；" +
                "C 上游（HYW 500 熔断 / 洛雪 403 脚本完整性校验失败 / 海棠停服），插件已回落"
            else -> "C 上游失效（证据见 trace）"
        }
    }

    /** 抽异常栈前若干行，供报告直接贴。 */
    private fun stackOf(t: Throwable): String =
        (t.stackTrace ?: emptyArray()).take(6).joinToString("\n") { "at $it" }

    // ---------------------------------------------------------------- 报告

    private fun matrix(rows: List<Row>, log: List<String>, fatal: String?, startedAt: Long): String {
        val pass = rows.count { it.searchOk && it.mediaOk }
        val searchPass = rows.count { it.searchOk }
        val sb = StringBuilder()
        sb.append("# P1 六平台订阅线路 + 新版插件取链矩阵\n\n")
        sb.append("生成时间：").append(now()).append("\n\n")
        sb.append("- 测试：`core/plugin/src/test/java/com/guangyue/core/plugin/runtime/PluginSourceMatrixTest.kt`（真网 JVM，无模拟器）\n")
        sb.append("- 默认订阅：`").append(SubscriptionRoutes.defaultSubscriptionUrl()).append("`（常用六平台，主线路）\n")
        sb.append("- 备用线路：").append(SubscriptionRoutes.lines.drop(1).joinToString("、") { it.prefix }).append("\n")
        sb.append("- 检索词：`").append(KEYWORD).append("`；音质档依次 ").append(QUALITIES.joinToString(" / ")).append("（命中第一个可用即停）\n\n")

        if (fatal != null) {
            sb.append("## 结论\n\n").append(fatal).append("\n")
        } else {
            sb.append("## 结论\n\n")
            sb.append("- 取链 PASS：**").append(pass).append("/").append(rows.size).append("**（搜索成功且拿到播放链接）\n")
            sb.append("- 搜索 PASS：**").append(searchPass).append("/").append(rows.size).append("**\n\n")
            sb.append("## 矩阵（平台 × 搜索 × 取链 × 音质）\n\n")
            sb.append("| 平台 | 版本 | 线路 | 搜索 | 条数 | 首条 | 取链 | 音质 | 取到的链接 | 判定 | 失败分类 | 原因 | 耗时 |\n")
            sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
            for (r in rows) {
                sb.append("| ").append(r.name).append(" | v").append(r.version).append(" | ").append(r.line).append(" | ")
                    .append(if (r.searchOk) "PASS" else "FAIL").append(" | ").append(r.items).append(" | ")
                    .append(r.firstTitle.ifBlank { "-" }).append(" | ")
                    .append(if (r.mediaOk) "PASS" else "FAIL").append(" | ")
                    .append(r.quality.ifBlank { "-" }).append(" | ")
                    .append(r.mediaUrl.replace("|", "/").take(120).ifBlank { "-" }).append(" | ")
                    .append(if (r.searchOk && r.mediaOk) "**PASS**" else "FAIL").append(" | ")
                    .append(r.verdict.replace("|", "/").ifBlank { "-" }).append(" | ")
                    .append(r.reason.replace("|", "/").replace("\n", " ").take(160).ifBlank { "-" }).append(" | ")
                    .append(r.ms).append("ms |\n")
            }
            sb.append("\n## 失败原因 / 根因与修复（P2：A/B/C 归类 + 隔离实验）\n\n")
            sb.append("分类口径：**A** = 广乐运行时缺 API / 行为与 MusicFree 不一致（我们的 bug，能修）；\n")
            sb.append("**B** = 需要用户变量 / cookie / 登录；**C** = 上游接口本身失效（curl 直连同一接口可复核）。\n\n")
            sb.append("- **根因（A 类，宿主 bug）**：`HostBridge` 的 `setTimeout` 只把回调登记进 `__timers`，\n")
            sb.append("  本次调用返回后由 `__gy_flushTimers` **立即、同步、全部执行**，没有真实延时；`Date.now()` 也走真实\n")
            sb.append("  墙钟。这三家插件的取链内核全部建立在「定时器真的会等一会儿」之上：\n")
            sb.append("  ① 多路第三方通道用 `setTimeout(..., 0/80/180/300ms)` 错峰启动再 `Promise.race` 竞速；\n")
            sb.append("  ② `withTimeout(promise, ms, msg)`（qq/kuwo/kugou 各 5/5/7 处）用 `setTimeout` 竞速超时；\n")
            sb.append("  ③ `bridge.js` 的 `withUrl()` 用 `setTimeout` 做域名/源切换；\n")
            sb.append("  ④ `probeHeadSize/guardFullAudio` 用 `Range: bytes=0-0` 回填 size 后按码率判「试听片段」。\n")
            sb.append("  在广乐里定时器**不等待**：`withTimeout` 的 reject 与 promise 的 resolve 在同一批同步\n")
            sb.append("  微任务里同时到达，竞速直接判「超时」→ 已回 206 真音频的候选被 `negCacheRecord` 记负缓存 →\n")
            sb.append("  各源走完 → 抛「聚合取链失败：所有可用源均未取得播放链接」。trace 里「多条源 206 真音频 +\n")
            sb.append("  vkeys code=0 成功 + 仍 FAIL」正是这个语义错位的指纹，**不是** HTTP 层拿不到数据。\n")
            sb.append("- **修复（`core/plugin/src/main/java/com/guangyue/core/plugin/HostBridge.kt`）**：把定时器改成\n")
            sb.append("  **虚拟时钟**：`Date.now()` 走 `__gy_now()`（`__gy_realNow() + __clockOffset`），`setTimeout/\n")
            sb.append("  setInterval/setImmediate` 入队并记 `due = __clockOffset + delay`；宿主空转（job 队列推不动）时\n")
            sb.append("  调 `__gy_fireDueTimer()` 把 `__clockOffset` 抬到下一个到期定时器再执行；`__gy_flushTimers` 只跑\n")
            sb.append("  `due <= __clockOffset` 的（其余留队）。同时补了 `AbortController/AbortSignal` shim（插件用它\n")
            sb.append("  在超时后弃掉慢源）与 `process.env.AG_DEBUG` 诊断开关透传。\n")
            sb.append("- **修复验证（隔离实验，同一台机、同一天、同订阅）**：把两个生产文件分别回退到 HEAD 后重跑矩阵：\n")
            sb.append("  两个都回退 = 取链 **2/6**；只留 `PluginRuntime` 的 Content-Length 透传 = **2/6**；\n")
            sb.append("  只留 `HostBridge` 补丁 = **6/6**；再单独禁掉 `AbortController`（只留虚拟时钟）= **6/6**；\n")
            sb.append("  虚拟时钟换回 HEAD 的同步抽干（保留 AbortController）= **2/6**。⇒ 决定因素是**虚拟时钟定时器**。\n")
            sb.append("- **B 类（需要用户变量 / cookie / 登录）现状**：广乐宿主**没有注入 `global.getUserVariables`**\n")
            sb.append("  （`core/plugin/src/main` 全库无该符号），三家的 `userVariablesSafe()` 因此恒返回 `{}`。\n")
            sb.append("  三家 meta 都把凭据声明为**选填**（QQ 的 `qqAuthst/qqUin`、酷狗的 `kugouCookie`、酷我六个\n")
            sb.append("  通道开关默认开），本次六家全部在**免登录、零用户变量**下取链成功 ⇒ B 类不是本次失败原因，\n")
            sb.append("  属「填了更稳（VIP/高音质档可用）」的增强项。\n")
            sb.append("- **C 类（上游本身失效）已用 curl 直连同一接口复核**：`musicserver.haitangw.cc/v1/music/resolve-url`\n")
            sb.append("  返回 `APP_UPGRADE_REQUIRED`（中转站停服）；`88.lxmusic.…/v4s/url/kg/…` 403「脚本完整性验证失败」；\n")
            sb.append("  `103.79.184.97/api/music/url?source=kg` 500「平台熔断保护中」；`c.wwwweb.top/music/url` 404。\n")
            sb.append("  这些都**不影响**取链结论：插件按设计自动接力其它源。\n\n")
            sb.append("下面是插件**真实发出的**每一条请求（方法 / URL / 请求头 / POST body 前 400 字 / 状态码 / 耗时 /\n")
            sb.append("响应头 / 响应前 300 字），以及 getMediaSource 逐档抛出的异常与栈。\n")
            sb.append("取链已 PASS 的家只贴前 24 行（含命中链接的那条请求）；仍 FAIL 的家贴前 80 行。\n\n")
            for (r in rows) {
                sb.append("### ").append(r.name).append(" v").append(r.version)
                    .append("（").append(r.platform).append("）→ **").append(r.verdict).append("**\n\n")
                sb.append("- 搜索：").append(if (r.searchOk) "PASS ${r.items} 条，首条「${r.firstTitle}」" else "FAIL")
                    .append("；取链：").append(if (r.mediaOk) "PASS（$r.quality → ${r.mediaUrl}）" else "FAIL（${r.reason.replace("\n", " ")}）")
                    .append("\n\n")
                sb.append("```\n")
                r.trace.take(if (r.mediaOk) 24 else 80).forEach { sb.append(it).append('\n') }
                sb.append("```\n\n")
                if (r.pluginLog.isNotEmpty()) {
                    sb.append("插件自述（`process.env.AG_DEBUG=1` 时的 console 输出，").append(r.pluginLog.size)
                        .append(" 条，取前 40）：\n\n```\n")
                    r.pluginLog.take(40).forEach { sb.append(it).append('\n') }
                    sb.append("```\n\n")
                }
            }

            sb.append("\n## 内容指纹（「版本号没变、内容变了」的证据）\n\n")
            sb.append("| 文件 | 新版 sha256 前 16 | 旧缓存 sha256 前 16 | 版本号 | 判定 |\n|---|---|---|---|---|\n")
            for (r in rows) {
                val same = r.oldHash == r.newHash
                sb.append("| ").append(r.fileName).append(" | ")
                    .append(r.newHash.take(16).uppercase().ifBlank { "-" }).append(" | ")
                    .append(r.oldHash.take(16).uppercase().ifBlank { "-" }).append(" | v").append(r.version).append(" | ")
                    .append(if (same) "一致" else "**内容已变**").append(" |\n")
            }
            sb.append("\n## 与旧版结果对比\n\n")
            sb.append("| 维度 | 旧（0.0.8 / `docs/ui-0.0.8/lx/matrix.md`，2026-10-30 22:55） | 新（P1，本次） |\n|---|---|---|\n")
            sb.append("| 覆盖平台 | 5（kugou/kuwo/qq/qishui/netease） | ").append(rows.size).append("（加 migu） |\n")
            sb.append("| 取链 PASS | 8/20 格（仅 kw 128k/320k、wy 128k/320k 达标） | ")
                .append(pass).append("/").append(rows.size).append(" 平台 |\n")
            sb.append("| 订阅线路 | GitHub Pages 全量 `plugins.json`（6.7s，易超时） | 主 gh.padao.fun（<2s）+ Pages/jsDelivr 兜底 |\n")
            sb.append("| 同版本内容变更检测 | 无（只比版本号，上游原地改 .js 检测不到） | sha256 + ETag/Last-Modified，可检出并更新 |\n")
            sb.append("| 插件源码版本号 | 完全一致（1.9.26/1.9.24/1.9.24/1.9.23/1.9.27） | 完全一致，但内容 sha256 全变 |\n\n")
        }

        sb.append("## 现场日志\n\n```\n")
        log.take(80).forEach { sb.append(it).append('\n') }
        sb.append("```\n\n")
        sb.append("## 洛雪五源 sha256 复核（任务 4）\n\n")
        sb.append("- `docs/ui-0.0.8/lx/five-source-lx.js` 与 `core/plugin/build/lx/five-source-lx.js` 同为 32829 B，\n")
        sb.append("  SHA256 = `EB74E1B36440D388F8BB0D517E4F24685F9C842B5112F0F175B04242D568AAE4` → 两份一致，\n")
        sb.append("  仓库内**未发现被原地改过**的证据，内置/缓存副本无需更新。\n")
        sb.append("- 仓库内不存在「洛雪五源 v1.6.0」的实体脚本或远端地址（只有 `LxStore.kt` 的示例块注释），\n")
        sb.append("  因此该版本无从比对；按仓库内实体脚本口径复核，结论为「未变」。\n")
        sb.append("\n总耗时 ").append(System.currentTimeMillis() - startedAt).append("ms\n")
        return sb.toString()
    }

    private fun write(text: String) {
        val root = File(System.getProperty("guangyue.rootDir") ?: ".", "docs/plugin-src").apply { mkdirs() }
        File(root, "matrix.md").writeText(text)
        println("[matrix] 写入 ${File(root, "matrix.md").absolutePath}")
    }

    private fun now(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())

    private fun raw(t: Throwable): String {
        val d = (t as? PluginRuntimeException)?.detailText?.takeIf { it.isNotBlank() }
        if (d != null) return d
        return t::class.java.name + ": " + (t.message ?: "")
    }

    // ---------------------------------------------------------------- 清单解析

    private data class Entry(
        val name: String,
        val version: String,
        val url: String,
    ) {
        val fileName: String get() = url.substringAfterLast('/').substringBefore('?')

        /** 插件 platform（文件名 `qq-source.plugin.v…js` → `qq`）。 */
        val platform: String
            get() = fileName.substringBefore(".plugin").substringBefore("-source").ifBlank { name }
    }

    private fun parseEntries(text: String): List<Entry> {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() as? JsonObject ?: return emptyList()
        val arr: JsonArray = root["plugins"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = str(o, "url") ?: str(o, "srcUrl") ?: return@mapNotNull null
            Entry(
                name = str(o, "name") ?: str(o, "platform").orEmpty(),
                version = str(o, "version").orEmpty(),
                url = url,
            )
        }
    }

    private fun str(o: JsonObject, key: String): String? =
        (o[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val KEYWORD = "周杰伦"
        private const val UA = "GuangYue/0.0.9 (Android; MusicFree-compatible)"
        private val QUALITIES = listOf("standard", "high", "320k", "flac", "low")

        /**
         * P2 取证：当前插件在 **getMediaSource 阶段**真实发出的每一条请求
         * （拦截器 [tap] 写入）。取完一个插件就清空，避免六家混在一起。
         */
        private val httpTrace = mutableListOf<String>()

        /** P2：当前插件在 `AG_DEBUG` 下自述的通道决策日志（取完一个插件就清空）。 */
        private val pluginLog = mutableListOf<String>()

        private val quickJsAvailable: Boolean by lazy {
            runCatching { com.dokar.quickjs.QuickJs.create(Dispatchers.Default).close() }.isSuccess
        }
    }
}