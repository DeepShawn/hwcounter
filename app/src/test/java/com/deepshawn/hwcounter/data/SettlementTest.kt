package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class SettlementTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING; private val T = HwStatus.LATE; private val L = HwStatus.LEAVE
    private val rules = ScoreRules()

    /** 每人一串状态，按顺序为第 1..n 次作业 */
    private fun cls(vararg rows: Pair<String, List<HwStatus>>, groups: Map<String, String> = emptyMap()): SchoolClass {
        val st = rows.map { (id, _) -> Student(id = id, no = id, name = id, group = groups[id] ?: "") }
        val n = rows.maxOf { it.second.size }
        val assigns = (0 until n).map { i -> Assignment(id = "a$i", title = "作业$i", createdAt = (i + 1) * 1000L, statuses = rows.mapNotNull { (id, l) -> l.getOrNull(i)?.let { id to it } }.toMap()) }
        return SchoolClass(name = "一班", students = st, assignments = assigns)
    }
    private fun line(s: Settlement, id: String) = s.lines.first { it.studentId == id }

    @Test fun tiersByHandInRate() {
        val c = cls(
            "full" to List(20) { S },
            "late" to List(19) { S } + T,                 // 100% 但有迟交 → ≥95% +3
            "r90" to List(18) { S } + listOf(M, S),       // 95%
            "r85" to List(17) { S } + listOf(M, M, M),    // 85% → 0；连续缺交 3 次 −2
            "r75" to List(15) { S } + List(5) { M },      // 75% → −2；缺交 5 次 −3；连续 5 次 −2
            "low" to List(10) { S } + List(10) { M },
            "few" to listOf(S, L, L),                      // 应交 1 次 → 不结算
        )
        val p = Settle.preview(c, rules, 1_000_000L)
        assertEquals(5.0, line(p, "full").delta, 0.0)
        assertEquals(3.0, line(p, "late").delta, 0.0)
        assertEquals(3.0, line(p, "r90").delta, 0.0)
        assertEquals(-2.0, line(p, "r85").delta, 0.0)
        assertEquals(-2.0 - 3 - 2, line(p, "r75").delta, 0.0)
        assertEquals(-5.0 - 3 - 2, line(p, "low").delta, 0.0)
        assertTrue(line(p, "few").skipped)
        assertTrue(line(p, "r75").reasons.any { it.contains("缺交 5 次") })
    }

    @Test fun groupAndProgressBonus() {
        val c0 = cls(
            "a1" to List(8) { M } + List(8) { S }, "a2" to List(16) { S },
            "b1" to List(16) { S }.mapIndexed { i, x -> if (i == 12) M else x }, "b2" to List(16) { S }.mapIndexed { i, x -> if (i == 13) M else x },
            groups = mapOf("a1" to "一组", "a2" to "一组", "b1" to "二组", "b2" to "二组"),
        )
        // 从第 9 次作业起全班重置：上一轮 a1 交齐 0%，本轮 100% → 进步奖
        val c = c0.recharge(rules, c0.students.map { it.id }, 100.0, time = 50_000L, assignment = c0.assignments[8])
        val p = Settle.preview(c, rules, 1_000_000L)
        assertTrue(line(p, "a1").reasons.any { it.startsWith("进步奖") })
        // 本轮一组 100%，二组有缺交 → 一组小组奖
        assertTrue(line(p, "a2").reasons.any { it.startsWith("小组奖") })
        assertFalse(line(p, "b1").reasons.any { it.startsWith("小组奖") })
        assertEquals(5.0 + 2 + 2, line(p, "a1").delta, 0.0)
    }

    @Test fun applyThenNewRoundCarriesPointsAndUndoRemovesAll() {
        val c = cls("x" to List(6) { S }, "y" to List(6) { M })
        val p = Settle.preview(c, rules, 1_000_000L)
        val after = Settle.apply(c, p)
        assertEquals(1, after.settlements.size)
        assertEquals(2, after.scores.count { it.settlementId == p.id })
        val before = c.studentStats(rules).associate { it.student.id to it.score }
        val now = after.studentStats(rules).associate { it.student.id to it.score }
        assertEquals(before.getValue("x") + 5, now.getValue("x"), 1e-9)
        assertTrue(after.ledger(rules, "x").any { it.kind == LedgerRow.Kind.SETTLE })
        val undone = Settle.undo(after, p.id)
        assertEquals(c.scores, undone.scores); assertTrue(undone.settlements.isEmpty())
    }

    @Test fun monthlyResetSettlesPreviousRoundOnce() {
        val c0 = cls("x" to List(6) { S }, "y" to List(6) { M })
        val r = c0.recharge(rules, c0.students.map { it.id }, 100.0, time = 10_000L, effectiveAt = 10_000L)
        val auto = r.copy(recharges = r.recharges.map { it.copy(id = "auto-202612") })
        val rs = rules.copy(settlement = SettlementRules(autoSince = 1L))
        val s1 = Settle.applyAuto(auto, rs)
        assertEquals(listOf("auto-settle-202612"), s1.settlements.map { it.id })
        assertSame(s1, Settle.applyAuto(s1, rs).let { if (it == s1) s1 else it })
        // 结算分计入新一轮：x 重置为 100，再 +5
        assertEquals(105.0, s1.studentStats(rs).first { it.student.id == "x" }.score, 1e-9)
        // 撤销后不再补回
        val u = Settle.undo(s1, "auto-settle-202612")
        assertTrue(Settle.applyAuto(u, rs).settlements.isEmpty())
        // 开启联动之前的定期重置不追溯
        assertTrue(Settle.applyAuto(auto, rules.copy(settlement = SettlementRules(autoSince = 20_000L))).settlements.isEmpty())
    }
}
