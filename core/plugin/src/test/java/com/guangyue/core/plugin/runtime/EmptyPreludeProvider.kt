package com.guangyue.core.plugin.runtime

import android.content.Context
import android.content.ContextWrapper

/**
 * 空 prelude 的 [PreludeProvider] 子类，仅用于测试。
 *
 * [PreludeProvider] 只在 `loadPrelude()` 里碰 context，而 [code] 被 override 成空串，
 * 所以传进去的 context 永远不会被使用；用 Unsafe 分配一个未初始化的 ContextWrapper 占位。
 */
class EmptyPreludeProvider private constructor(context: Context) : PreludeProvider(context) {

    constructor() : this(placeholderContext())

    override fun code(): String = ""

    companion object {
        private fun placeholderContext(): Context = try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            unsafeClass.getMethod("allocateInstance", Class::class.java)
                .invoke(theUnsafe, ContextWrapper::class.java) as Context
        } catch (t: Throwable) {
            throw IllegalStateException("无法构造测试用 Context: " + (t.cause ?: t), t)
        }
    }
}
