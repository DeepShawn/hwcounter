package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 旧测试沿用“未交固定扣分”规则，专门测试其他功能 */
val FLAT = ScoreRules(escalation = MissingEscalation(enabled = false))

class EscalationTest {
    private val M = HwStatus.MISSING; private val S = HwStatus.SUBMITTED; private val L = HwStatus.LEAVE; private val N = HwStatus.NONE; private val T = HwStatus.LATE
    private val rules = ScoreRules()

    private fun cls(vararg marks: HwStatus): SchoolClass {
        val st = Student(id = "s1", no = "01", name = "张三")
        return SchoolClass(name = "一班", students = listOf(st), assignments = marks.mapIndexed { i, m -> Assignment(id = "a$i", title = "作业$i", createdAt = (i + 1) * 1000L, statuses = mapOf("s1" to m)) })
    }
    private fun pts(c: SchoolClass) = c.assignments.map { c.autoPoints(rules, "s1").getValue(it.id) }
    private fun score(c: SchoolClass, r: ScoreRules = rules) = c.studentStats(r).first().score

    @Test fun consecutiveDoubles() {
        assertEquals(listOf(-2.0, -4.0, -8.0, -16.0), pts(cls(M, M, M, M)))
        assertEquals(100.0 - 30, score(cls(M, M, M, M)), 1e-9)
    }

    @Test fun leaveAndUnregisteredDoNotBreakButHandingInDoes() {
        // 第一次未交，第二次请假，第三次未交 → 按连续两次
        assertEquals(listOf(-2.0, 0.0, -4.0), pts(cls(M, L, M)))
        assertEquals(listOf(-2.0, 0.0, -4.0), pts(cls(M, N, M)))
        // 中途交过（已交 / 迟交）就重新算
        assertEquals(listOf(-2.0, -4.0, 0.0, -2.0), pts(cls(M, M, S, M)))
        assertEquals(listOf(-2.0, -1.0, -2.0), pts(cls(M, T, M)))
    }

    @Test fun moreThanSixInRoundSwitchesToBaseThree() {
        // 6 次都是单次未交（每次都被打断）：都扣 2；第 7 次起底数 3
        val c = cls(M, S, M, S, M, S, M, S, M, S, M, S, M, M, M)
        val p = pts(c).filterIndexed { i, _ -> c.assignments[i].statuses["s1"] == M }
        assertEquals(listOf(-2.0, -2.0, -2.0, -2.0, -2.0, -2.0, -3.0, -9.0, -27.0), p)
    }

    @Test fun resetStartsANewRoundWithBaseTwo() {
        val c0 = cls(M, S, M, S, M, S, M, S, M, S, M, S, M, M, M, M)
        // 从第 15 次作业（下标 14）起重置：之前累计 7 次以上的次数清零，连续也清零
        val c = c0.recharge(rules, listOf("s1"), 100.0, time = 99_000L, assignment = c0.assignments[14])
        val p = c.autoPoints(rules, "s1")
        assertEquals(-9.0, p.getValue("a13"), 1e-9) // 重置前：本轮第 8 次未交、连续第 2 次 → 3² = 9
        assertEquals(-2.0, p.getValue("a14"), 1e-9)
        assertEquals(-4.0, p.getValue("a15"), 1e-9)
        assertEquals(100.0 - 2 - 4, score(c), 1e-9)
    }

    @Test fun capAndDisabled() {
        val capped = rules.copy(escalation = MissingEscalation(cap = 5.0))
        val c = cls(M, M, M, M)
        assertEquals(listOf(-2.0, -4.0, -5.0, -5.0), c.assignments.map { c.autoPoints(capped, "s1").getValue(it.id) })
        assertEquals(100.0 - 8, score(c, FLAT), 1e-9)
    }

    @Test fun ledgerShowsEscalatedAmounts() {
        val c = cls(M, L, M)
        val rows = c.ledger(rules, "s1").filter { it.kind == LedgerRow.Kind.AUTO }
        assertEquals(listOf(-4.0, -2.0), rows.map { it.delta })
        assertTrue(rows.first().title.contains("连续"))
    }
}
