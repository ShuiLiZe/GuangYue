package com.guangyue.feature.library.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangyue.feature.library.design.icons.GuangYueIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Folia `StatusToast` 的三种类型：错误红圈叹号、成功绿勾、其他蓝色星光。 */
enum class StatusToastType { INFO, SUCCESS, ERROR }

data class StatusToastItem(
    val text: String,
    val type: StatusToastType = StatusToastType.INFO,
    /** 同文案连发也要重新出现、重新计时。 */
    val key: Long = System.nanoTime(),
)

/**
 * App 内提示（替代 `android.widget.Toast`）。任何地方 `StatusToasts.show("已加入下载队列")`，
 * 根布局里的 [StatusToastHost] 负责画。
 */
object StatusToasts {
    private val _current = MutableStateFlow<StatusToastItem?>(null)
    val current: StateFlow<StatusToastItem?> = _current

    fun show(text: String, type: StatusToastType = guess(text)) {
        if (text.isBlank()) return
        _current.value = StatusToastItem(text, type)
    }

    fun dismiss(key: Long) {
        if (_current.value?.key == key) _current.value = null
    }

    /** 没指定类型时按文案猜：「失败 / 无法 / 不可用 / 错误」为错误，「已…」为成功。 */
    internal fun guess(text: String): StatusToastType = when {
        listOf("失败", "无法", "不可用", "错误", "出错").any { it in text } -> StatusToastType.ERROR
        text.startsWith("已") -> StatusToastType.SUCCESS
        else -> StatusToastType.INFO
    }
}

/** 停留时长：Folia 一般提示 ~2.6s，错误多给一秒读。 */
internal fun toastHoldMs(type: StatusToastType, text: String): Long =
    (if (type == StatusToastType.ERROR) 3600L else 2600L) + (text.length - 16).coerceAtLeast(0) * 40L

/**
 * 手机档最大宽度（folia-sonnet androidPhoneFit status-toast）：
 * 竖屏 min(屏宽−32, 22rem=352dp)，横屏 min(72vw, 28rem=448dp)。
 */
internal fun toastMaxWidth(screenWidthDp: Int, screenHeightDp: Int): Dp {
    val landscape = screenWidthDp > screenHeightDp
    return if (landscape) minOf(screenWidthDp * 0.72f, 448f).dp
    else minOf(screenWidthDp - 32f, 352f).dp
}

const val StatusToastTag = "status-toast"

/**
 * 顶部居中的提示胶囊（Folia `components/app/dialogs/StatusToast.tsx` + `statusToast.css`）：
 * 距顶 30px（这里再加状态栏），圆角胶囊、细边、投影；左侧类型图标，文案 500 字重；
 * 进场下滑 18 → 0（320ms），退场上移 10 淡出（220ms）。点一下即收起。
 * 手机档：padding 8/12、圆角 18、图标 16dp；文案按 App 正文下限用 14sp（sonnet 写 12px，那是网页缩放后的值）。
 * 毛玻璃在 Android 上没有廉价的背景模糊，用近乎不透明的底色代替，保证压在任何封面上都读得清。
 */
@Composable
fun StatusToastHost(dark: Boolean, modifier: Modifier = Modifier) {
    val item by StatusToasts.current.collectAsState()
    var shown by remember { mutableStateOf<StatusToastItem?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        val now = item
        if (now == null) {
            visible = false
            return@LaunchedEffect
        }
        if (visible && shown != null && shown?.key != now.key) {
            visible = false
            delay(160)
        }
        shown = now
        visible = true
        delay(toastHoldMs(now.type, now.text))
        visible = false
        StatusToasts.dismiss(now.key)
    }
    val conf = LocalConfiguration.current
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 18.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible && shown != null,
            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { -it / 3 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 5 },
        ) {
            val t = shown ?: return@AnimatedVisibility
            StatusToastPill(
                item = t,
                dark = dark,
                maxWidth = toastMaxWidth(conf.screenWidthDp, conf.screenHeightDp),
                onTap = { StatusToasts.dismiss(t.key); visible = false },
            )
        }
    }
}

@Composable
fun StatusToastPill(
    item: StatusToastItem,
    dark: Boolean,
    maxWidth: Dp,
    onTap: () -> Unit = {},
) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (dark) Color(0xF02A2A2E) else Color(0xF7FFFFFF)
    val edge = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val ink = if (dark) Color.White else Color(0xFF27272A)
    val (icon, iconTint) = when (item.type) {
        StatusToastType.ERROR -> GuangYueIcons.AlertCircle to (if (dark) Color(0xFFF87171) else Color(0xFFEF4444))
        StatusToastType.SUCCESS -> GuangYueIcons.CircleCheck to (if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A))
        StatusToastType.INFO -> GuangYueIcons.Sparkles to (if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB))
    }
    Row(
        Modifier
            .testTag(StatusToastTag)
            .widthIn(max = maxWidth)
            .heightIn(min = 40.dp)
            .shadow(if (dark) 14.dp else 10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(bg, shape)
            .border(1.dp, edge, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(
            item.text,
            color = ink,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
        )
    }
}

/** 取证页用：同 [toastMaxWidth]（internal 跨模块不可见）。 */
fun toastMaxWidthForHarness(screenWidthDp: Int, screenHeightDp: Int): Dp = toastMaxWidth(screenWidthDp, screenHeightDp)
