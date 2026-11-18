package com.deepshawn.hwcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 只读（普通用户 / 未登录预览）：规则、科目、理由用老师的，显示设置用本机的 */
class ReadOnlySettingsTest {
    private val teacher = Settings(rules = ScoreRules(initial = 80.0, missing = -3.0), subjects = listOf("数学"), quickReasons = listOf("好"), theme = "teacherTheme", dark = 1)
    private val local = Settings(theme = "mine", dark = 2, compact = true, haptic = false)

    @Test fun teacherRulesLocalDisplay() {
        val m = teacher.withLocalDisplay(local)
        assertEquals(teacher.rules, m.rules)
        assertEquals(teacher.subjects, m.subjects)
        assertEquals(teacher.quickReasons, m.quickReasons)
        assertEquals("mine", m.theme); assertEquals(2, m.dark); assertEquals(true, m.compact); assertEquals(false, m.haptic)
    }

    @Test fun teacherOwnedDetectsRuleChanges() {
        assertEquals(teacherOwned(teacher), teacherOwned(teacher.copy(theme = "x", dark = 0)))
        assertNotEquals(teacherOwned(teacher), teacherOwned(teacher.copy(rules = teacher.rules.copy(initial = 50.0))))
        assertNotEquals(teacherOwned(teacher), teacherOwned(teacher.copy(subjects = listOf("语文"))))
        assertNotEquals(teacherOwned(teacher), teacherOwned(teacher.copy(quickReasons = emptyList())))
    }
}
