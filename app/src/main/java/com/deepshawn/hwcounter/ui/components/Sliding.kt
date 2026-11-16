package com.deepshawn.hwcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滑动切换条的状态：滑块位置（以“第几项”为单位，可以是小数），拖动时跟手，松手吸附到最近一项。
 * 动效关闭时（全局动画倍率为 0）滑块直接跳到目标位置。
 */
class SlideState(val count: Int, initial: Int) {
    val pos = Animatable(initial.toFloat())
    var dragging by mutableStateOf(false)
    /** 当前高亮的一项（拖动时随手指变化） */
    var hot by mutableIntStateOf(initial)
}

/**
 * 可滑动切换的分段容器：[thumb] 画在滑块位置（宽度 = 一项），[content] 依次放各项（用 [SlideState.hot] 判断高亮）。
 * 点按某项切换；在条上左右拖动，滑块跟着手指走，松手选中最近的一项；跨过一项时轻震一下。
 */
@Composable
fun SlidingSegments(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    thumb: @Composable BoxScope.(Modifier) -> Unit,
    content: @Composable (SlideState) -> Unit,
) {
    val state = remember(count) { SlideState(count, selected) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val select by rememberUpdatedState(onSelect)
    val spec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(selected, count) {
        if (!state.dragging) {
            state.hot = selected
            state.pos.animateTo(selected.toFloat(), spec)
        }
    }
    fun posAt(x: Float): Float {
        if (widthPx <= 0 || count <= 0) return 0f
        val seg = widthPx.toFloat() / count
        return (x / seg - 0.5f).coerceIn(0f, (count - 1).toFloat())
    }
    Box(
        modifier
            .onSizeChanged { widthPx = it.width }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        state.dragging = true
                        scope.launch { state.pos.animateTo(posAt(o.x), spring(stiffness = Spring.StiffnessHigh)) }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val p = posAt(change.position.x)
                        scope.launch { state.pos.snapTo(p) }
                        val h = p.roundToInt()
                        if (h != state.hot) { state.hot = h; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    },
                    onDragEnd = {
                        val target = state.pos.value.roundToInt().coerceIn(0, count - 1)
                        state.dragging = false; state.hot = target
                        scope.launch { state.pos.animateTo(target.toFloat(), spec) }
                        select(target)
                    },
                    onDragCancel = {
                        state.dragging = false; state.hot = selected
                        scope.launch { state.pos.animateTo(selected.toFloat(), spec) }
                    },
                )
            },
    ) {
        if (widthPx > 0 && count > 0) {
            val segW = widthPx.toFloat() / count
            thumb(
                Modifier
                    .offset { IntOffset((state.pos.value * segW).roundToInt(), 0) }
                    .width(with(density) { segW.toDp() })
                    .fillMaxHeight(),
            )
        }
        content(state)
    }
}

/**
 * 页面左右滑动切换选项卡：横向滑动超过 72dp 且明显比纵向多时，切到上一项 / 下一项。
 * 只是旁观手势、不拦截：图表、热力图等自己能横向滚动的内容先处理，它们处理过的手势不会触发切换。
 */
fun Modifier.swipeToSwitch(count: Int, selected: Int, onSelect: (Int) -> Unit): Modifier = if (count < 2) this else pointerInput(count, selected) {
    val threshold = 72.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var childScrolled = false
        var dx = 0f; var dy = 0f
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Final)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            dx = ch.position.x - down.position.x; dy = ch.position.y - down.position.y
            // 横向为主的手势被消耗了 = 里面有能横向滚动的内容在处理
            if (ch.isConsumed && abs(dx) > abs(dy)) childScrolled = true
            if (!ch.pressed) break
        }
        if (!childScrolled && abs(dx) > threshold && abs(dx) > abs(dy) * 2) {
            val next = if (dx < 0) selected + 1 else selected - 1
            if (next in 0 until count) onSelect(next)
        }
    }
}
