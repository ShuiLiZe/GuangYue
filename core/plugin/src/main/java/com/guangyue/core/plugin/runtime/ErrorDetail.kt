package com.guangyue.core.plugin.runtime

/**
 * 原始异常的完整取证信息。
 *
 * 为什么单独一个类：`shortReason()` 只给用户一句中文友好文案，但用户反馈问题时需要
 * 真正的现场 —— 原始异常类名 + message + 堆栈。R8 打开后 `t.stackTraceToString()`
 * 往往只剩一行，所以这里**在捕获点**（异常还活着的时候）就把堆栈拍下来，
 * 而不是等到 UI 展开时再去拿。
 *
 * 格式固定为：
 *
 * ```
 * <原始异常类名>: <message>
 *     前 15 行堆栈
 * ```
 */
data class ErrorDetail(
    /** 原始异常类名（如 `com.dokar.quickjs.QuickJsException`）。 */
    val className: String,
    /** 原始 message（未做任何翻译/截断美化）。 */
    val message: String,
    /** 前 [STACK_LINES] 行堆栈；为空时是 `（无堆栈）`。 */
    val stackLines: List<String> = emptyList(),
) {
    /** 供「展开详情」直接展示的文本。 */
    fun format(): String = buildString {
        append(className)
        if (message.isNotBlank()) append(": ").append(message)
        if (stackLines.isEmpty()) {
            append("\n（无堆栈）")
        } else {
            for (line in stackLines) append('\n').append(line)
        }
    }

    override fun toString(): String = format()

    companion object {
        /** 用户要求：前 15 行堆栈。 */
        const val STACK_LINES = 15

        /**
         * 从 [t] 拍下取证信息。
         *
         * 处理三种情况：
         *  1. 普通异常 —— 取 `t::class.java.name`（R8 下也是进去时的真名）+ 堆栈；
         *  2. 带 cause 的包装异常 —— message 里拼上 cause，因为真正的原因常在 cause；
         *  3. 堆栈为空（R8 / 无调试信息）—— 用「链路上各层类名」兜底，至少能看出
         *     异常从哪一层抛出来。
         */
        fun of(t: Throwable): ErrorDetail {
            val frames = runCatching { t.stackTrace }.getOrDefault(emptyArray())
            val lines = frames
                .filter { it.className.isNotBlank() }
                .take(STACK_LINES)
                .map { frame ->
                    "at " + frame.className + "." + frame.methodName +
                        (frame.fileName?.let { "(" + it + ":" + frame.lineNumber + ")" } ?: "(?)")
                }

            val chain = generateSequence(t) { it.cause }.toList()
            val message = chain.mapNotNull { e ->
                val m = runCatching { e.message }.getOrNull() ?: runCatching { e.toString() }.getOrNull()
                m?.takeIf { it.isNotBlank() }
            }.distinct().joinToString(" ← ").ifBlank { "（无 message）" }

            val origin = chain.firstOrNull() ?: t
            val className = runCatching { origin.javaClass.name }.getOrElse { origin.toString() }

            return ErrorDetail(
                className = className,
                message = message,
                // R8 把行号/文件名抹掉后堆栈可能整体为空：至少留一条链路提示，
                // 让用户知道错误发生在宿主桥接层而不是插件业务层。
                stackLines = lines.ifEmpty { listOf("at $className (堆栈已被 R8 移除)") },
            )
        }
    }
}
