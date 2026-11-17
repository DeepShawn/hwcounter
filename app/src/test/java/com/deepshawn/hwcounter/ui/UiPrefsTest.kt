package com.deepshawn.hwcounter.ui

import com.deepshawn.hwcounter.ui.theme.UiPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPrefsTest {
    @Test fun perfModeDefaultsOffAndForcesMotion() {
        assertFalse(UiPrefs().perf)
        assertFalse(UiPrefs().ultra)
        val p = UiPrefs(animations = false, perf = true)
        assertTrue(p.motion)
        assertTrue(p.ultra)
        // 低性能模式优先
        val both = UiPrefs(perf = true, lowPerf = true)
        assertFalse(both.motion)
        assertFalse(both.ultra)
    }
}
