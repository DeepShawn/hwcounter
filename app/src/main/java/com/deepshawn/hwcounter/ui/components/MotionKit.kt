package com.deepshawn.hwcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.deepshawn.hwcounter.ui.theme.LocalUiPrefs
import kotlinx.coroutines.delay

/**
 * 动效工具（只在“界面动效”开启且不是低性能模式时生效；关闭时全部直接显示最终状态）：
 * - [pressScale]：按下时轻微缩小、松手弹回；
 * - [appear]：页面刚打开时，卡片和列表行依次淡入上浮（滚动时新出现的行不再播放，避免干扰）；
 * - [AnimatedCount]：数字从旧值滚动到新值；
 * - [smoothSize]：展开收起时高度平滑过渡。
 */
object Springs {
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)
    val soft = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
}

/** 页面级的入场状态：页面打开的时刻 + 依次递增的序号（决定入场的先后） */
class PageEntrance(val start: Long) { var next = 0 }

val LocalPageEntrance = staticCompositionLocalOf<PageEntrance?> { null }

@Composable
fun motionOn(): Boolean = LocalUiPrefs.current.motion

/** 按下缩放（不拦截点击，只旁观按下与抬起） */
@Composable
fun Modifier.pressScale(scale: Float = 0.97f): Modifier {
    if (!motionOn()) return this
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) scale else 1f, Springs.press, label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                // Final：列表滚动或点击处理过之后再看，滚动时会立刻弹回
                waitForUpOrCancellation(PointerEventPass.Final)
                pressed = false
            }
        }
}

/** 页面打开后 0.7 秒内首次出现的元素：依次淡入、上浮 18dp */
@Composable
fun Modifier.appear(): Modifier {
    if (!motionOn()) return this
    val page = LocalPageEntrance.current ?: return this
    val order = remember { if (System.currentTimeMillis() - page.start < 700) page.next++ else -1 }
    if (order < 0) return this
    val a = remember { Animatable(0f) }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(minOf(order, 10) * 32L)
        a.animateTo(1f, Springs.soft)
    }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * dy }
}

/** 高度变化平滑过渡 */
@Composable
fun Modifier.smoothSize(): Modifier = if (motionOn()) this.animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) else this

/**
 * 数字滚动：从上一次的值（第一次显示时从 0）滚到新值；动效关闭时直接显示。
 * [format] 把当前值格式化成文字。
 */
@Composable
fun animatedValue(value: Double, fromZero: Boolean = true): Double {
    if (!motionOn()) return value
    val a = remember { Animatable(if (fromZero) 0f else value.toFloat()) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    return a.value.toDouble()
}

/** 含数字的文字（如“86%”“42 次”“100.7”）：数字部分滚动，前后缀不变 */
@Composable
fun animatedText(text: String): String {
    if (!motionOn()) return text
    val m = remember(text) { Regex("^([^0-9\\-]*)(-?[0-9]+(?:\\.[0-9]+)?)(.*)$").find(text) } ?: return text
    val (pre, num, post) = m.destructured
    val target = num.toDoubleOrNull() ?: return text
    val decimals = num.substringAfter('.', "").length
    val v = animatedValue(target)
    val shown = if (decimals == 0) Math.round(v).toString() else "%.${decimals}f".format(v)
    return pre + shown + post
}
