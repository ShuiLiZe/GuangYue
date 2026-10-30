package com.guangyue.core.plugin

/**
 * 插件失败提示的**唯一**收口点（0.0.9 a-1）。
 *
 * 界面里只允许出现中文人话。这些东西一律不许进 UI：
 * - 异常类名：`com.guangyue.core.plugin.runtime.PluginRuntimeException`、`com.dokar.quickjs.QuickJsException`；
 * - 栈帧：`at apiSearch (plugin.js:2:17847)`、`at _0x451256 (plugin.js:2:252126)`；
 * - 纯英文异常：认不出中文就退成 [GENERIC]，绝不把英文原文摊给用户。
 *
 * 原始异常只走 `android.util.Log`（tag `GuangYuePlugin`），不要塞进 UI state。
 * 插件自己写的中文原因可保留并按人话显示：`搜索失败 code=6000` → 「源站返回错误码 6000」，
 * 「未取得播放链接」原样保留。
 *
 * 本文件是纯 Kotlin（不依赖 Android），可在 JVM 单测里直接断言。
 */
object PluginFailureText {

    /** 什么都认不出来时的兜底。 */
    const val GENERIC = "源站没响应，稍后重试"

    private const val MAX_CHARS = 60

    private val SPACES = Regex("\\s+")
    /** 栈帧：`at apiSearch (plugin.js:2:17847)`。 */
    private val STACK_FRAME = Regex("""\bat\s+\S+\s*\([^)]*\)""")
    /** 没带括号的残留栈帧：` at _0x3846c6` / ` at apiSearch.call`。 */
    private val BARE_FRAME = Regex("""\s+at\s+(?=\S*[._(])""")
    /** 全限定异常类名：`com.guangyue.core.plugin.runtime.PluginRuntimeException:`。 */
    private val FQN_EXCEPTION =
        Regex("""(?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*(?:Exception|Error|Throwable)\b:?""")
    /** 裸类名：`PluginRuntimeException:` / `QuickJsException:`。 */
    private val SIMPLE_EXCEPTION = Regex("""\b[A-Za-z_$][\w$]*(?:Exception|Error|Throwable)\b:?""")
    /** 中文前面那截纯英文技术前缀（`Error: qishui sign 全部端点失败` → `全部端点失败`）。 */
    private val LEADING_ASCII = Regex("""^[\x20-\x7E]+(?=[\u4e00-\u9fff])""")
    /** 去完类名后开头剩下的分隔符。 */
    private val LEADING_NOISE = Regex("""^[\s:：,，。、\-—|]+""")
    /** 结尾多余的分隔符。 */
    private val TRAILING_NOISE = Regex("""[\s:：,，。、\-—|]+$""")
    private val CJK = Regex("[\u4e00-\u9fff]")
    /** 插件自写的错误码：`搜索失败 code=6000`。 */
    private val ERROR_CODE = Regex("""(?:搜索|取链|请求|接口)?失败\s*code\s*[=:：]?\s*(\d+)""")

    /**
     * 把插件报错压成一句能直接塞进 UI 的中文短句。
     *
     * 输出保证：不含 `com.guangyue`、不含 ` at `、不含英文异常类名、不含纯英文句子。
     * 认不出来时给 [GENERIC]。
     */
    fun reason(raw: String?): String {
        val text = normalize(raw)
        if (text.isEmpty()) return GENERIC

        // 中文优先：插件自己写的中文原因比英文异常名更具体，也更能让用户看懂。
        if (CJK.containsMatchIn(text)) {
            ERROR_CODE.find(text)?.let { return "源站返回错误码 ${it.groupValues[1]}" }
            val candidate = shorten(clean(text))
            if (isSafeChinese(candidate)) return candidate
        }

        keyword(text)?.let { return it }

        val fallback = shorten(stripClassName(text))
        return if (CJK.containsMatchIn(fallback) && isUiSafe(fallback)) fallback else GENERIC
    }

    /** 统一的「搜索失败」整句：[searchFailed] 之外别处不要再拼措辞。 */
    fun searchFailed(label: String, raw: String?): String =
        "$label 音源搜索失败：${reason(raw)}，换个音源试试"

    fun installFailed(raw: String?): String = "安装失败：${reason(raw)}"

    fun updateFailed(raw: String?): String = "检查更新失败：${reason(raw)}"

    fun healthFailed(raw: String?): String = "体检失败：${reason(raw)}"

    /** 行内状态标签用的一小句（与 [reason] 同一套映射）。 */
    fun firstSentence(raw: String?): String = reason(raw)

    // ------------------------------------------------------------------ 内部

    private fun normalize(raw: String?): String =
        raw?.takeIf { it.isNotBlank() }?.replace(SPACES, " ")?.trim().orEmpty()

    /** 去掉栈帧，保留类名/主机名等关键字信息（后面还要靠它们认错因）。 */
    private fun dropFrames(text: String): String =
        text.replace(STACK_FRAME, " ").replace(BARE_FRAME, " ").replace(SPACES, " ").trim()

    /** 剥掉异常类名与各种噪音，只留可能的中文原因。 */
    private fun stripClassName(text: String): String {
        var s = text.replace(FQN_EXCEPTION, " ").replace(SIMPLE_EXCEPTION, " ")
        s = s.replace(SPACES, " ").trim()
        s = s.replace(LEADING_ASCII, "")
        s = s.replace(Regex("""^[Ee]rror\s*[:：]?\s*"""), "").trim()
        s = s.replace(LEADING_NOISE, "").replace(TRAILING_NOISE, "").trim()
        return s
    }

    private fun clean(raw: String?): String = stripClassName(dropFrames(normalize(raw)))

    private fun isUiSafe(text: String): Boolean =
        text.isNotEmpty() && !text.contains("com.guangyue") && !text.contains(" at ")

    private fun isSafeChinese(text: String): Boolean = CJK.containsMatchIn(text) && isUiSafe(text)

    /** 已知的英文/技术关键字 → 中文人话；认不出来返回 null。 */
    private fun keyword(raw: String): String? {
        val text = dropFrames(normalize(raw))
        val lower = text.lowercase()
        return when {
            text.contains("Promise that has not settled") -> "插件返回的异步结果没结算"
            text.contains("超时") || text.contains("InterruptedException") ||
                text.contains("CancellationException") -> "插件超时没返回（30 秒）"
            lower.contains("not a function") -> "插件内部报错（函数不存在）"
            text.contains("Unable to resolve host") || text.contains("UnknownHost") ||
                lower.contains("enotfound") -> "连不上源站（网络或域名失效）"
            text.contains("status code -1") -> "网络请求失败（重定向或 TLS 被源站拒）"
            text.contains("429") -> "请求被源站限流（429）"
            text.contains("403") -> "源站拒绝访问（403）"
            text.contains("404") -> "接口不存在（404）"
            lower.contains("timeout") || lower.contains("timed out") -> "源站响应超时"
            text.contains("SSL") || text.contains("Certificate") -> "证书校验失败"
            text.contains("url is required") -> "插件请求没给地址"
            lower.contains("missing") -> "插件缺少接口实现"
            lower.contains("unsupported") -> "插件不支持这个操作"
            lower.contains("quickjs") -> "插件脚本执行出错"
            lower.contains("unknown plugin error") -> GENERIC
            else -> null
        }
    }

    private fun shorten(text: String): String =
        if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "…" else text
}