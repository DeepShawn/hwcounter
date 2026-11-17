package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Windows 触摸屏手指滑动 / 按住鼠标拖动：页面要能滚动，点按仍然有效 */
@OptIn(ExperimentalComposeUiApi::class)
class DragScrollTest {
    private val y = mutableFloatStateOf(0f)
    private val clicks = mutableIntStateOf(0)
    private val btnY = mutableFloatStateOf(0f)

    private fun scene() = ImageComposeScene(800, 600, Density(1f)) {
        HwTheme(Prefs()) {
            Page("测试", null) {
                Box(Modifier.fillMaxWidth().height(80.dp).onGloballyPositioned { btnY.floatValue = it.positionInRoot().y + 40f }.clickable { clicks.intValue++ })
                repeat(40) { i -> Box(Modifier.fillMaxWidth().height(60.dp).then(if (i == 20) Modifier.onGloballyPositioned { y.floatValue = it.positionInRoot().y } else Modifier)) }
            }
        }
    }

    private fun ImageComposeScene.frames(t: Long, n: Int = 3): Long { var tt = t; repeat(n) { render(tt); tt += 16_000_000L }; return tt }

    @Test fun dragScrollsThePage() {
        val s = scene(); var t = s.frames(0)
        val before = y.floatValue
        val primary = PointerButtons(isPrimaryPressed = true)
        s.sendPointerEvent(PointerEventType.Press, Offset(400f, 500f), buttons = primary)
        for (k in 1..20) { s.sendPointerEvent(PointerEventType.Move, Offset(400f, 500f - k * 15f), buttons = primary); t = s.frames(t, 1) }
        s.sendPointerEvent(PointerEventType.Release, Offset(400f, 200f))
        t = s.frames(t, 30)
        assertTrue(before - y.floatValue >= 250f, "拖动后页面应向上滚动：之前 $before，之后 ${y.floatValue}")
        s.close()
    }

    @Test fun wheelStillScrolls() {
        val s = scene(); var t = s.frames(0)
        val before = y.floatValue
        repeat(5) { s.sendPointerEvent(PointerEventType.Scroll, Offset(400f, 300f), scrollDelta = Offset(0f, 3f)); t = s.frames(t, 2) }
        t = s.frames(t, 30)
        assertTrue(y.floatValue < before, "滚轮也要能滚动：之前 $before，之后 ${y.floatValue}")
        s.close()
    }

    @Test fun tapStillClicks() {
        val s = scene(); var t = s.frames(0)
        val primary = PointerButtons(isPrimaryPressed = true)
        val at = Offset(400f, btnY.floatValue)
        s.sendPointerEvent(PointerEventType.Move, at); t = s.frames(t, 1)
        s.sendPointerEvent(PointerEventType.Press, at, buttons = primary); t = s.frames(t, 1)
        s.sendPointerEvent(PointerEventType.Release, at); s.frames(t, 2)
        assertEquals(1, clicks.intValue, "点按要触发点击")
        s.close()
    }
}
