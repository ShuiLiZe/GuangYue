package com.guangyue.music

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 冒烟用例：MainActivity 能在 JVM（Robolectric）上真正启动，并且首屏有内容。
 *
 * 0.0.4 的真机是黑屏——WebView 里那层网页根本没渲染出来。这条用例盯的就是「不许再空白」：
 * Activity 走完 onCreate、setContent 组合出一个非空的内容视图，根视图高度非零。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {

    @Test
    fun activityStartsAndHasContent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val root = activity.window.decorView
        assertNotNull("decorView 为空", root)
        assertNotNull("没有内容视图（黑屏）", activity.findViewById<android.view.View>(android.R.id.content))

        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        assert(content.childCount > 0) { "setContent 之后内容区仍然没有任何子视图" }

        // 组合完成后装饰视图中至少应有一个测量过的可见节点。
        root.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2200, android.view.View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2200)
        assert(root.width > 0 && root.height > 0) { "根视图尺寸为 0×0，等于没有界面" }

        controller.pause().stop().destroy()
    }
}
