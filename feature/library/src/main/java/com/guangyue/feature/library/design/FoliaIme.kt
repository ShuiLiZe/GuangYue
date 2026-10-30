package com.guangyue.feature.library.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 键盘（IME）抢位时的外壳编排。
 *
 * 手机版和 Folia 桌面的一条关键差异：桌面没有软键盘，窗口高度不变；手机上一弹键盘，
 * 底栏要么被顶到键盘上面（难看、挡住输入框），要么直接被键盘盖住。这里的选择是
 * **键盘弹起时底栏整条让位**（搜索页把输入框留在底部，配 `imePadding` 抬起来）。
 *
 * 迷你条同理：键盘弹起时它会贴着输入框、把可用高度吃掉，所以也一起收掉。
 */

/** 键盘弹起时是否允许迷你条收掉自己（[ShellColumn] 在底栏让位的页面置 true）。 */
val LocalImeHidesMiniPlayer = staticCompositionLocalOf { false }

/** 当前软键盘（IME）在底部占掉的高度；没弹键盘时为 0。 */
@Composable
internal fun imeBottomPadding(): Dp =
    WindowInsets.ime.asPaddingValues().calculateBottomPadding()

/**
 * 键盘弹起时把自己量成 0dp 高（视觉上消失，但不占位置、不引发整棵树重组）。
 *
 * 只在 [LocalImeHidesMiniPlayer] 为 true 且键盘真的弹起（ime > 0dp）时生效，
 * 否则完全等同于没挂这个 modifier。
 */
@Composable
fun Modifier.collapseOnIme(): Modifier {
    val collapse = LocalImeHidesMiniPlayer.current && imeBottomPadding() > 0.dp
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        if (collapse) {
            layout(placeable.width, 0) { }
        } else {
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}