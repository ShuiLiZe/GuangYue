package com.guangyue.feature.search

/**
 * U3：搜索页可见状态机（纯逻辑，不碰 Android / 协程 / 网络，可单测）。
 *
 * Folia 的搜索页（`src/components/app/search/SearchWorkspace.tsx:44-120`）把「搜索中 / 有错无结果 /
 * 无结果 / 有结果」四种呈现分得很清楚，这里照它的判断顺序抄成纯函数：
 *
 * ```
 *   isSearching            -> Loading      （居中转圈，SearchWorkspace.tsx:96）
 *   error && results==0    -> Failure      （AlertCircle + 重试，:99-108）
 *   results==0             -> Empty        （home.noResults，:110）
 *   else                   -> Results      （列表 + 底部错误条，:112+）
 * ```
 *
 * 广乐比 Folia 多两个前置状态：没装音源（`available` 为空）和初始态（还没搜过、只有历史/推荐）。
 */
enum class SearchPagePhase {
    /** 一个可用音源都没有（旧页面用空态顶上）。 */
    NoSource,

    /** 初始态：历史上 / 推荐关键词。 */
    Idle,

    /** 正在加载（防抖已过、请求在途）。 */
    Loading,

    /** 有结果（单曲 / 专辑 / 歌手 / 歌单任一桶非空）。 */
    Results,

    /** 搜完了但一个字都没有。 */
    Empty,

    /** 失败：请求抛错且没有任何结果可展示。 */
    Failure,
}

/**
 * 从 [SearchUiState] 推出可见状态。判断顺序与 Folia 一致（先加载、再错误、再空）。
 */
fun SearchUiState.pagePhase(): SearchPagePhase {
    if (available.isEmpty()) return SearchPagePhase.NoSource
    val hasAny = rows.isNotEmpty() ||
        albums.isNotEmpty() ||
        artists.isNotEmpty() ||
        sheets.isNotEmpty()
    return when {
        loading -> SearchPagePhase.Loading
        failure != null && !hasAny -> SearchPagePhase.Failure
        hasAny -> SearchPagePhase.Results
        searched -> SearchPagePhase.Empty
        else -> SearchPagePhase.Idle
    }
}

/**
 * 输入即搜的防抖时长。
 *
 * Folia 桌面是回车提交（`SearchWorkspace.tsx` 里 `onSubmit`），没有防抖这个概念；手机上「输入即搜」
 * 是 Hark 定的手机适配项，取 400ms：足够覆盖中文拼音一次上屏，又不会让用户觉得卡。
 */
const val SEARCH_DEBOUNCE_MS: Long = 400L

/**
 * 防抖调度器（纯逻辑，不依赖 Dispatcher）：真实运行时由 `SearchViewModel` 用一个协程 Job 实现，
 * 单测里用 [FakeDebouncer] 手动推进。
 *
 * 语义：新的输入先取消上一次待发，再排一次 [SEARCH_DEBOUNCE_MS] 后的触发；
 * 清空输入则只取消、不触发（对应 `SearchWorkspace.tsx` 里空串不搜索）。
 */
interface Debouncer {
    /** 排一次防抖触发；会先取消上一次 [cancel]。 */
    fun schedule()

    /** 取消待发的触发（输入被清空 / 换音源 / 页面销毁）。 */
    fun cancel()

    /** 还有没有待发的触发。 */
    val pending: Boolean
}

abstract class DebounceScheduler(private val delayMs: Long) : Debouncer {
    private var token: Any? = null

    override val pending: Boolean get() = token != null

    override fun schedule() {
        val next = Any()
        token = next
        enqueue(delayMs) { if (token === next) { token = null; onFire() } }
    }

    override fun cancel() {
        token = null
        onCancel()
    }

    /** 在 [delayMs] 之后执行 [block]（真实实现投放协程，测试实现记账）。 */
    protected abstract fun enqueue(delayMs: Long, block: () -> Unit)

    /** 取消排队中的那一次。 */
    protected abstract fun onCancel()

    protected abstract fun onFire()
}

/**
 * 输入变化的判定：要不要重新发起搜索。
 *
 * - 文本去掉首尾空白后为空 → 不搜（并清掉「已搜过」标记，回到初始态）；
 * - 文本没变（比如只是多敲了个空格）→ 不搜；
 * - 其他情况 → 搜。
 */
fun shouldSearchOnInput(previous: SearchUiState, next: String): Boolean {
    val trimmed = next.trim()
    if (trimmed.isEmpty()) return false
    return trimmed != previous.query.trim()
}



