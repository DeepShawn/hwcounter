package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 旧测试沿用“未交固定扣分”规则，专门测试其他功能 */
val FLAT = ScoreRules(escalation = MissingEscalation(enabled = false), streakBonus = StreakBonus(enabled = false), roundPoints = false, leader = LeaderRules(enabled = false), groupRank = GroupRankRules(enabled = false))

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
        // 新规：第 1、2 次各扣 2，第 3 次 4，第 4 次 8……
        assertEquals(listOf(-2.0, -2.0, -4.0, -8.0), pts(cls(M, M, M, M)))
        assertEquals(100.0 - 16, score(cls(M, M, M, M)), 1e-9)
        assertEquals("2、2、4、8…", MissingEscalation.seq(2.0))
    }

    @Test fun leaveAndUnregisteredDoNotBreakButHandingInDoes() {
        // 第一次未交，第二次请假，第三次未交 → 按连续两次
        assertEquals(listOf(-2.0, 0.0, -2.0), pts(cls(M, L, M)))
        assertEquals(listOf(-2.0, 0.0, -2.0, -4.0), pts(cls(M, N, M, M)))
        // 中途交过（已交 / 迟交）就重新算
        assertEquals(listOf(-2.0, -2.0, 0.0, -2.0), pts(cls(M, M, S, M)))
        assertEquals(listOf(-2.0, -1.0, -2.0), pts(cls(M, T, M)))
    }

    @Test fun moreThanSixInRoundSwitchesToBaseThree() {
        // 6 次都是单次未交（每次都被打断）：都扣 2；第 7 次起底数 3（3、3、9、27）
        val c = cls(M, S, M, S, M, S, M, S, M, S, M, S, M, M, M, M)
        val p = pts(c).filterIndexed { i, _ -> c.assignments[i].statuses["s1"] == M }
        assertEquals(listOf(-2.0, -2.0, -2.0, -2.0, -2.0, -2.0, -3.0, -3.0, -9.0, -27.0), p)
    }

    @Test fun resetStartsANewRoundWithBaseTwo() {
        val c0 = cls(M, S, M, S, M, S, M, S, M, S, M, S, M, M, M, M)
        // 从第 15 次作业（下标 14）起重置：之前累计 7 次以上的次数清零，连续也清零
        val c = c0.recharge(rules, listOf("s1"), 100.0, time = 99_000L, assignment = c0.assignments[14])
        val p = c.autoPoints(rules, "s1")
        assertEquals(-3.0, p.getValue("a13"), 1e-9) // 重置前：本轮第 8 次未交、连续第 2 次 → 3
        assertEquals(-2.0, p.getValue("a14"), 1e-9)
        assertEquals(-2.0, p.getValue("a15"), 1e-9)
        assertEquals(100.0 - 2 - 2, score(c), 1e-9)
    }

    @Test fun defaultCapIsThirty() {
        assertEquals(30.0, MissingEscalation().cap, 0.0)
        assertEquals(listOf(-2.0, -2.0, -4.0, -8.0, -16.0, -30.0, -30.0), pts(cls(M, M, M, M, M, M, M)))
    }

    @Test fun capAndDisabled() {
        val capped = rules.copy(escalation = MissingEscalation(cap = 5.0))
        val c = cls(M, M, M, M)
        assertEquals(listOf(-2.0, -2.0, -4.0, -5.0), c.assignments.map { c.autoPoints(capped, "s1").getValue(it.id) })
        assertEquals(100.0 - 8, score(c, FLAT), 1e-9)
    }

    @Test fun modeOneIsNormalDoubling() {
        // 第一种：正常加倍 2、4、8、16，封顶 30
        val r = rules.copy(escalation = MissingEscalation(mode = 1))
        val c = cls(M, M, M, M, M)
        assertEquals(listOf(-2.0, -4.0, -8.0, -16.0, -30.0), c.assignments.map { c.autoPoints(r, "s1").getValue(it.id) })
        assertEquals("2、4、8、16…", MissingEscalation.seq(2.0, 1))
        // 交过一次重新算；请假不打断
        val c2 = cls(M, L, M, S, M)
        assertEquals(listOf(-2.0, 0.0, -4.0, 0.0, -2.0), c2.assignments.map { c2.autoPoints(r, "s1").getValue(it.id) })
    }

    @Test fun modeThreeTieredMultipliers() {
        // 第三种：在第二种（2、2、4、8、16、32）上分段：第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2
        val e = MissingEscalation(mode = 3, cap = 0.0)
        val r = rules.copy(escalation = e)
        val c = cls(M, M, M, M, M, M)
        assertEquals(listOf(-2.0, -2.0, -6.0, -12.0, -32.0, -64.0), c.assignments.map { c.autoPoints(r, "s1").getValue(it.id) })
        assertEquals("第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2（按连续未交次数）", e.tiersText())
        // 在第一种（2、4、8、16）上分段，默认上限 30 在乘完倍率后封顶
        val r1 = rules.copy(escalation = MissingEscalation(mode = 3, tierBase = 1))
        assertEquals(listOf(-2.0, -4.0, -12.0, -24.0, -30.0), cls(M, M, M, M, M).let { x -> x.assignments.map { x.autoPoints(r1, "s1").getValue(it.id) } })
        // 自定义分段：第 2 次起 ×3
        val r2 = rules.copy(escalation = MissingEscalation(mode = 3, tiers = listOf(EscalationTier(2, 3.0))))
        assertEquals(listOf(-2.0, -6.0, -12.0), cls(M, M, M).let { x -> x.assignments.map { x.autoPoints(r2, "s1").getValue(it.id) } })
        assertEquals("第 1 次 ×1、第 2 次起 ×3（按连续未交次数）", r2.escalation.tiersText())
    }

    @Test fun modeThreeByTotalCount() {
        // 按本轮累计次数分段：交过一次后连续次数重新算，但累计次数继续，倍率按累计次数
        val r = rules.copy(escalation = MissingEscalation(mode = 3, tierBy = "total"))
        val c = cls(M, S, M, S, M, S, M)
        val p = c.assignments.map { c.autoPoints(r, "s1").getValue(it.id) }.filterIndexed { i, _ -> i % 2 == 0 }
        assertEquals(listOf(-2.0, -2.0, -3.0, -3.0), p)
        // 按连续次数：每次都是连续第 1 次，×1
        val r2 = rules.copy(escalation = MissingEscalation(mode = 3))
        assertEquals(listOf(-2.0, -2.0, -2.0, -2.0), c.assignments.map { c.autoPoints(r2, "s1").getValue(it.id) }.filterIndexed { i, _ -> i % 2 == 0 })
        // 流水里写明分段倍率
        val rows = c.ledger(r, "s1").filter { it.kind == LedgerRow.Kind.AUTO }
        assertTrue(rows.any { it.title.contains("分段 ×1.5") })
    }

    @Test fun ledgerShowsEscalatedAmounts() {
        val c = cls(M, L, M, M)
        val rows = c.ledger(rules, "s1").filter { it.kind == LedgerRow.Kind.AUTO }
        assertEquals(listOf(-4.0, -2.0, -2.0), rows.map { it.delta })
        assertTrue(rows.first().title.contains("连续"))
    }
}

class StreakBonusAndRoundingTest {
    private val M = HwStatus.MISSING; private val S = HwStatus.SUBMITTED; private val E = HwStatus.EXCELLENT; private val L = HwStatus.LEAVE; private val T = HwStatus.LATE
    private val rules = ScoreRules()
    private fun cls(vararg marks: HwStatus): SchoolClass {
        val st = Student(id = "s1", no = "01", name = "张三")
        return SchoolClass(name = "一班", students = listOf(st), assignments = marks.mapIndexed { i, m -> Assignment(id = "a$i", title = "作业$i", createdAt = (i + 1) * 1000L, statuses = mapOf("s1" to m)) })
    }
    private fun pts(c: SchoolClass, r: ScoreRules = rules) = c.assignments.map { c.autoPoints(r, "s1").getValue(it.id) }

    @Test fun bonusAfterFiveConsecutive() {
        // 第 6 次起每次 +1（优秀本身 +1，再加 1）
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 2.0), pts(cls(S, S, S, S, S, S, S, E)))
        // 请假不打断、不计次
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0), pts(cls(S, S, L, S, S, S, S)).let { it })
        // 未交打断；迟交默认也打断
        assertEquals(0.0, pts(cls(S, S, S, S, S, M, S))[6], 0.0)
        assertEquals(0.0, pts(cls(S, S, S, S, S, T, S))[6], 0.0)
        // 迟交算连续时：第 6 次迟交 = −1 + 1
        val late = rules.copy(streakBonus = StreakBonus(lateCounts = true))
        assertEquals(0.0, pts(cls(S, S, S, S, S, T), late)[5], 0.0)
    }

    @Test fun bonusRestartsEachResetRound() {
        val c0 = cls(S, S, S, S, S, S, S, S)
        val c = c0.recharge(rules, listOf("s1"), 100.0, time = 99_000L, assignment = c0.assignments[6])
        val p = c.autoPoints(rules, "s1")
        assertEquals(1.0, p.getValue("a5"), 0.0)
        assertEquals(0.0, p.getValue("a6"), 0.0); assertEquals(0.0, p.getValue("a7"), 0.0)
    }

    @Test fun roundingAwayFromZero() {
        assertEquals(1.0, roundAway(0.5), 0.0); assertEquals(-1.0, roundAway(-0.5), 0.0)
        assertEquals(2.0, roundAway(2.0), 0.0); assertEquals(-3.0, roundAway(-2.1), 0.0); assertEquals(0.0, roundAway(0.0), 0.0)
        val c = cls(S).copy(scores = listOf(ScoreEntry(studentId = "s1", delta = 0.5, reason = "表现"), ScoreEntry(studentId = "s1", delta = -1.5, reason = "迟到")))
        val r = rules.copy(late = -0.5)
        assertEquals(100.0 + 1 - 2, c.studentStats(r).first().score, 1e-9)
        assertEquals(100.0 + 0.5 - 1.5, c.studentStats(r.copy(roundPoints = false)).first().score, 1e-9)
        // 单笔保留原值，只对最终积分取整：100 − 0.5 = 99.5 → 100；负分向下：−2.5 → −3
        assertEquals(listOf(-0.5), pts(cls(T), r))
        assertEquals(100.0, cls(T).studentStats(r).first().score, 1e-9)
        assertEquals(99.5, cls(T).studentStats(r.copy(roundPoints = false)).first().score, 1e-9)
        assertEquals(-3.0, cls(T).studentStats(r.copy(initial = -2.0)).first().score, 1e-9)
    }

    @Test fun ledgerLabelsBonus() {
        val rows = cls(S, S, S, S, S, S).ledger(rules, "s1").filter { it.kind == LedgerRow.Kind.AUTO }
        assertTrue(rows.first().title.contains("连续交第 6 次"))
    }
}
