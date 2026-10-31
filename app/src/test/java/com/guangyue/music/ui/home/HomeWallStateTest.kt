package com.guangyue.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * U6 首页墙四态判定（纯逻辑，不起 Robolectric）。
 *
 * 这套判定的价值在于把「加载 / 空 / 失败」的优先级钉死，避免以后谁顺手改一行
 * 让失败态盖掉已经拿到的内容（首页最忌讳内容闪成错误页）。
 */
class HomeWallStateTest {

    @Test
    fun contentWinsOverEverything() {
        // 已经有内容：后到的 loading / failed 都不该把内容顶掉。
        assertEquals(HomeWallState.Content, homeWallState(loading = true, failed = true, hasContent = true))
        assertEquals(HomeWallState.Content, homeWallState(loading = true, failed = false, hasContent = true))
        assertEquals(HomeWallState.Content, homeWallState(loading = false, failed = true, hasContent = true))
        assertEquals(HomeWallState.Content, homeWallState(loading = false, failed = false, hasContent = true))
    }

    @Test
    fun loadingBeatsFailureWhileStillAsking() {
        // 还在拉（可能同时在重试）先显示「正在载入推荐内容…」，别急着报错。
        assertEquals(HomeWallState.Loading, homeWallState(loading = true, failed = true, hasContent = false))
        assertEquals(HomeWallState.Loading, homeWallState(loading = true, failed = false, hasContent = false))
    }

    @Test
    fun failureShowsWhenNothingArrived() {
        assertEquals(HomeWallState.Failed, homeWallState(loading = false, failed = true, hasContent = false))
    }

    @Test
    fun emptyIsTheFallback() {
        // 还没开始拉、或音源没给料：都算空态（给出路，不是报错）。
        assertEquals(HomeWallState.Empty, homeWallState(loading = false, failed = false, hasContent = false))
    }

    @Test
    fun allSixteenCombinationsAreTotal() {
        // 三布尔 8 种输入都必须有确定结论（不留 null / 不抛异常）。
        val seen = mutableSetOf<HomeWallState>()
        for (l in listOf(false, true)) {
            for (f in listOf(false, true)) {
                for (c in listOf(false, true)) {
                    seen += homeWallState(loading = l, failed = f, hasContent = c)
                }
            }
        }
        assertEquals(
            setOf(HomeWallState.Loading, HomeWallState.Content, HomeWallState.Failed, HomeWallState.Empty),
            seen,
        )
    }
}