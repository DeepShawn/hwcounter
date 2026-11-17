package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 作业复审：不合格记一次未交（参与连续 / 累计），这一次扣分翻倍 */
class ReviewTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING
    private val day = 86_400_000L

    private fun cls(statuses: List<HwStatus>, failed: Set<Int> = emptySet()): SchoolClass {
        val st = Student(id = "s", no = "1", name = "甲")
        val a = statuses.mapIndexed { k, x -> Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * day, statuses = mapOf("s" to x), reviewFailed = if (k in failed) listOf("s") else emptyList()) }
        return SchoolClass(name = "一班", students = listOf(st), assignments = a)
    }

    @Test fun failedReviewDoublesTheEscalatedPenalty() {
        val rules = ScoreRules()
        // 第 1 次未交（复审不合格）：2 × 2 = 4；第 2 次连续未交：仍扣 2
        val p = cls(listOf(M, M), failed = setOf(0)).autoPointDetails(rules, "s")
        assertEquals(-4.0, p.getValue("a0").points, 1e-9)
        assertTrue(p.getValue("a0").reviewFailed)
        assertEquals(-2.0, p.getValue("a1").points, 1e-9)
        // 复审不合格算一次未交，连续计数接着往上
        assertEquals(2, p.getValue("a1").missStreak)
        val stat = cls(listOf(S, M), failed = setOf(1)).studentStats(rules).single()
        assertEquals(1, stat.missing)
        assertEquals(100.0 - 4.0, stat.score, 1e-9)
    }

    @Test fun laterInAStreakAndCap() {
        // 连续第 3 次（4 分）复审不合格 → 8；单次上限 30 作用于普通扣分，翻倍后可到 60
        val p = cls(listOf(M, M, M), failed = setOf(2)).autoPointDetails(ScoreRules(), "s")
        assertEquals(-8.0, p.getValue("a2").points, 1e-9)
        val q = cls(List(7) { M }, failed = setOf(6)).autoPointDetails(ScoreRules(), "s")
        assertEquals(-60.0, q.getValue("a6").points, 1e-9)
    }

    @Test fun disabledOrChangedStatus() {
        val off = ScoreRules(review = ReviewRules(enabled = false))
        assertEquals(-2.0, cls(listOf(M), failed = setOf(0)).autoPointDetails(off, "s").getValue("a0").points, 1e-9)
        // 状态已经不是未交：标记不生效
        val c = cls(listOf(S), failed = setOf(0))
        assertFalse(c.assignments[0].failedReview("s"))
        assertEquals(0.0, c.autoPointDetails(ScoreRules(), "s").getValue("a0").points, 1e-9)
        // 不用递增扣分时：固定未交分 × 倍数
        val flat = ScoreRules(escalation = MissingEscalation(enabled = false), review = ReviewRules(multiplier = 3.0))
        assertEquals(-6.0, cls(listOf(M), failed = setOf(0)).autoPointDetails(flat, "s").getValue("a0").points, 1e-9)
        // 流水里写明原因
        val row = cls(listOf(M), failed = setOf(0)).ledger(ScoreRules(), "s").first { it.kind == LedgerRow.Kind.AUTO }
        assertTrue(row.title.contains("复审不合格"))
    }
}
