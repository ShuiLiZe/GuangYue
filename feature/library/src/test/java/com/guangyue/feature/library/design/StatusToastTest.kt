package com.guangyue.feature.library.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusToastTest {
    @Test fun guessType() {
        assertEquals(StatusToastType.ERROR, StatusToasts.guess("取链失败"))
        assertEquals(StatusToastType.ERROR, StatusToasts.guess("该来源不可用"))
        assertEquals(StatusToastType.SUCCESS, StatusToasts.guess("已加入下载队列"))
        assertEquals(StatusToastType.INFO, StatusToasts.guess("正在为 3 首歌匹配歌词和封面…"))
    }

    @Test fun phoneWidths() {
        // 360dp 竖屏：360 − 32 = 328；宽屏竖放封顶 352。
        assertEquals(328.dp, toastMaxWidth(360, 800))
        assertEquals(352.dp, toastMaxWidth(500, 900))
        // 800×360 横屏：72% = 576 → 封顶 448；560×360 → 403.2。
        assertEquals(448.dp, toastMaxWidth(800, 360))
        assertEquals((560 * 0.72f).dp, toastMaxWidth(560, 360))
    }

    @Test fun holdLongerForErrorsAndLongText() {
        assertEquals(2600L, toastHoldMs(StatusToastType.INFO, "短"))
        assertEquals(3600L, toastHoldMs(StatusToastType.ERROR, "短"))
        assertEquals(2600L + 4 * 40L, toastHoldMs(StatusToastType.SUCCESS, "一二三四五六七八九十一二三四五六七八九十"))
    }
}
