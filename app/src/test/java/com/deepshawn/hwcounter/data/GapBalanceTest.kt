package com.deepshawn.hwcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 间断未交平衡：与网页 tools/web-tests/scores.test.mjs、iPad Tests/main.swift 同一组用例 */
class GapBalanceTest {
    private val M = HwStatus.MISSING; private val S = HwStatus.SUBMITTED; private val L = HwStatus.LEAVE
    private val rules = ScoreRules(streakBonus = StreakBonus(enabled = false))
    private fun cls(vararg marks: HwStatus) = SchoolClass(
        id = "c", name = "一班", students = listOf(Student(id = "s1", no = "01", name = "甲")),
        assignments = marks.mapIndexed { k, m -> Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * 1000L, statuses = mapOf("s1" to m)) },
    )
    private fun misses(c: SchoolClass, r: ScoreRules = rules) = c.assignments.filter { it.statuses["s1"] == M }.map { c.autoPoints(r, "s1").getValue(it.id) }

    @Test fun defaultOnWindowSixMaxThree() {
        assertTrue(MissingEscalation().gapBalance)
        assertEquals(6, MissingEscalation().gapWindow)
        assertEquals(3, MissingEscalation().gapMax)
    }

    @Test fun everyOtherTimeEscalates() {
        // 隔一次不交一次：2、2、4、4、4（间断最多按第 3 次算）；关闭平衡时每次都只扣 2
        val c = cls(M, S, M, S, M, S, M, S, M, S)
        assertEquals(listOf(-2.0, -2.0, -4.0, -4.0, -4.0), misses(c))
        // 上限可调：最多按第 4 次算、看最近 8 次时 2、2、4、8、8
        assertEquals(listOf(-2.0, -2.0, -4.0, -8.0, -8.0), misses(c, rules.copy(escalation = MissingEscalation(gapWindow = 8, gapMax = 4))))
        assertEquals(List(5) { -2.0 }, misses(c, rules.copy(escalation = MissingEscalation(gapBalance = false))))
    }

    @Test fun intermittentNotLighterThanConsecutiveAtHigherMissRate() {
        // 甲：12 次里连续不交 4 次（33%）；乙：12 次里隔一次不交一次（50%）。开启平衡后乙扣得更多
        val a = cls(M, M, M, M, S, S, S, S, S, S, S, S)
        val b = cls(M, S, M, S, M, S, M, S, M, S, M, S)
        val sa = misses(a).sum(); val sb = misses(b).sum()
        assertEquals(-16.0, sa, 1e-9)
        assertEquals(-20.0, sb, 1e-9)
        assertTrue("乙 $sb 应比甲 $sa 扣得多", sb < sa)
        // 关闭平衡：乙反而扣得少（这就是要解决的问题）
        val off = rules.copy(escalation = MissingEscalation(gapBalance = false))
        assertTrue(misses(b, off).sum() > misses(a, off).sum())
    }

    @Test fun windowSlidesAndLeaveDoesNotCount() {
        // 窗口 3：第 2 次未交时近 3 次里 2 次未交；间隔很久后窗口滑走，回到第 1 次
        val r = rules.copy(escalation = MissingEscalation(gapWindow = 3))
        assertEquals(listOf(-2.0, -2.0, -2.0), misses(cls(M, S, M, S, S, S, M), r))
        // 请假不算记录、不占窗口（正常加倍 2、4、8：近 3 次记录是 未交、已交、未交 → 第 2 次，扣 4）
        assertEquals(listOf(-2.0, -4.0), misses(cls(M, S, L, L, L, M), rules.copy(escalation = MissingEscalation(mode = 1, gapWindow = 3))))
    }

    @Test fun ledgerExplainsBalance() {
        val c = cls(M, S, M, S, M)
        val p = c.autoPointDetails(rules, "s1").getValue("a4")
        assertEquals(1, p.missStreak); assertEquals(3, p.recentMiss)
        assertEquals("近 6 次第 3 次未交（间断未交平衡）", p.streakNote(rules))
        // 窗口里未交次数超过上限时写明按第几次算
        val c2 = cls(M, S, M, S, M, M, S, M)
        val p2 = c2.autoPointDetails(rules.copy(escalation = MissingEscalation(gapMax = 2)), "s1").getValue("a7")
        assertEquals("近 6 次第 4 次未交，按第 2 次算（间断未交平衡）", p2.streakNote(rules.copy(escalation = MissingEscalation(gapMax = 2))))
    }

    @Test fun resetClearsWindow() {
        val c = cls(M, S, M, S, M)
        val reset = c.recharge(rules, listOf("s1"), 100.0, time = 4500L, assignment = c.assignments[4])
        assertEquals(-2.0, reset.autoPoints(rules, "s1").getValue("a4"), 1e-9)
    }
}
