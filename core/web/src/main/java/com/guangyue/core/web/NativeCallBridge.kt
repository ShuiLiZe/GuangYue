package com.guangyue.core.web

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 广乐 ↔ Folia Web 的唯一 RPC 通道。
 *
 * Folia 侧 `window.GuangYueNative.call(method, paramsJson)` 返回一个 Promise；原生侧把结果
 * 通过 `WebView.evaluateJavascript("window.__gyResolve(id, ok, json)")` 回填。所有方法都跑在
 * IO 线程上（@JavascriptInterface 本身就在 WebView 的 JavaBridge 线程，绝不能在主线程做 IO），
 * 只有 `evaluateJavascript` 必须回主线程。
 *
 * 之所以用“整串 JSON + 目标 id”的路由，而不是每个功能写一个 @JavascriptInterface：插件的
 * search/getLyric/getMusicInfo 等方法签名各不相同，注册成一个个注解方法会让 ProGuard 与
 * 反射之间的关系变得脆弱，也难在前端做统一的 Promise 化。这里只保留一个注解方法，路由在
 * Kotlin 侧显式完成，R8 只需 keep 这一个类与它自己的方法。
 */
class NativeCallBridge(
    private val scope: CoroutineScope,
    private val dispatcher: BridgeDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val callIds = AtomicLong(0)

    /** id → 取消回调（用于页面销毁时把还在跑的任务一起停掉）。 */
    private val pending = ConcurrentHashMap<Long, () -> Unit>()

    @JavascriptInterface
    fun call(method: String, paramsJson: String): String {
        val id = callIds.incrementAndGet()
        val params: JsonObject = runCatching {
            json.parseToJsonElement(paramsJson.ifBlank { "{}" })
        }.getOrNull() as? JsonObject ?: JsonObject(emptyMap())

        val job = scope.launch {
            try {
                val result = dispatcher.dispatch(method, params, this@launch)
                resolve(id, ok = true, payload = result)
            } catch (e: CancellationException) {
                resolve(id, ok = false, payload = error("cancelled", e.message))
                throw e
            } catch (e: Throwable) {
                resolve(id, ok = false, payload = error(e.javaClass.simpleName, e.message))
            } finally {
                pending.remove(id)
            }
        }
        pending[id] = { job.cancel() }
        return id.toString()
    }

    /** 页面要主动中止一个还没返回的调用（例如搜索框又敲了一个字）。 */
    @JavascriptInterface
    fun cancel(id: String) {
        id.toLongOrNull()?.let { callIds ->
            pending.remove(callIds)?.invoke()
        }
    }

    fun cancelAll() {
        pending.values.toList().forEach { runCatching { it() } }
        pending.clear()
    }

    private fun resolve(id: Long, ok: Boolean, payload: JsonElement) {
        val text = json.encodeToString(JsonElement.serializer(), payload)
        val quoted = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))
        dispatcher.postJavascript("window.__gyResolve && window.__gyResolve($id, $ok, $quoted);")
    }

    private fun error(code: String, message: String?): JsonObject = buildJsonObject {
        put("error", JsonPrimitive(code))
        put("message", JsonPrimitive(message ?: code))
    }
}

/** 桥接层需要的能力：跑 JS、拿上下文等。放在接口里便于测试替换。 */
interface BridgeDispatcher {
    /** 在主线程执行一段 JS。 */
    fun postJavascript(script: String)

    /**
     * 处理一个方法的调用。挂起函数，运行在 IO 调度器上；返回值必须可被 kotlinx-serialization
     * 编码成 JSON（JsonObject / JsonArray / JsonPrimitive / null）。
     */
    suspend fun dispatch(method: String, params: JsonObject, scope: CoroutineScope): JsonElement

    /** 主动向页面推一条事件（播放状态、下载进度等）。 */
    fun emit(event: String, payload: JsonElement)
}

// --------------------------------------------------------------------- helpers

internal fun String?.orEmptyJson(): String = this ?: ""

internal fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement?.asArrOrNull(): JsonArray? = this as? JsonArray

internal fun JsonObject.str(key: String): String? {
    val v = this[key] as? JsonPrimitive ?: return null
    if (v is JsonNull) return null
    return v.content.takeIf { it.isNotEmpty() }
}

internal fun JsonObject.strOr(key: String, fallback: String): String = str(key) ?: fallback

internal fun JsonObject.int(key: String, fallback: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.toInt() ?: fallback

internal fun JsonObject.long(key: String, fallback: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.toLong() ?: fallback

internal fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.content?.trim()?.toBooleanStrictOrNull() ?: fallback

internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.stringMap(key: String): Map<String, String> {
    val obj = objOrNull(key) ?: return emptyMap()
    return obj.mapNotNull { (k, v) ->
        val text = (v as? JsonPrimitive)?.content ?: return@mapNotNull null
        k to text
    }.toMap()
}
