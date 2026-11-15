package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AutoResetAndProgressTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 0): Long = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis
    private val day = 86_400_000L

    private fun cls(): SchoolClass {
        val st = (1..4).map { Student(id = "s$it", no = "0$it", name = "学生$it") }
        // 9 月 1 日起每两天一次作业，共 30 次（到 10 月底）
        val assigns = (0 until 30).map { i ->
            val t = at(2026, 9, 1, 9) + i * 2 * day
            val m = HashMap<String, HwStatus>()
            m["s1"] = HwStatus.SUBMITTED
            m["s2"] = if (i < 15) HwStatus.MISSING else HwStatus.SUBMITTED          // 进步
            m["s3"] = if (i < 15) HwStatus.EXCELLENT else HwStatus.MISSING          // 退步
            m["s4"] = if (i % 3 == 0) HwStatus.LATE else HwStatus.SUBMITTED
            Assignment(id = "a$i", title = "作业$i", subject = if (i % 2 == 0) "数学" else "语文", createdAt = t, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = assigns)
    }

    @Test fun dueMonthsAfterEnablingOnly() {
        val ar = AutoReset(target = 100.0, day = 1, since = at(2026, 9, 15))
        val due = ar.due(at(2026, 11, 20))
        assertEquals(listOf("202610", "202611"), due.map { it.first })
        assertEquals(at(2026, 10, 1), due.first().second)
        assertEquals(at(2026, 12, 1), ar.next(at(2026, 11, 20)))
        assertTrue(ar.copy(enabled = false).due(at(2026, 11, 20)).isEmpty())
    }

    @Test fun applyAddsDeterministicResetsAndUndoSkips() {
        val rules = ScoreRules()
        var c = cls().copy(autoReset = AutoReset(target = 100.0, day = 1, since = at(2026, 9, 15)))
        c = c.applyAutoReset(rules, at(2026, 11, 2))
        assertEquals(listOf("auto-202610", "auto-202611"), c.recharges.map { it.id })
        // 再补一次不会重复
        assertSame(c, c.applyAutoReset(rules, at(2026, 11, 2)))
        // 11 月 1 日起重新计分：11 月还没有作业，s1 = 100
        assertEquals(100.0, c.studentStats(rules).first { it.student.id == "s1" }.score, 1e-9)
        // 撤销 11 月的：记入 skipped，之后不再补回
        c = c.undoRecharge("auto-202611")
        assertEquals(listOf("202611"), c.autoReset!!.skipped)
        c = c.applyAutoReset(rules, at(2026, 11, 3))
        assertEquals(listOf("auto-202610"), c.recharges.map { it.id })
        // 合并时撤销的月份也会被去掉
        val other = c.copy(recharges = c.recharges + c.recharges.first().copy(id = "auto-202611"))
        assertEquals(listOf("auto-202610"), other.dropSkippedAuto().recharges.map { it.id })
    }

    @Test fun calendarWindowsAndPrevious() {
        val c = cls()
        val now = at(2026, 10, 15, 12)
        val oct = c.assignments.count { it.createdAt >= at(2026, 10, 1) && it.createdAt < at(2026, 11, 1) }
        val m = Analytics.window(c, Analytics.Spec(Analytics.Range.MONTH), now)
        assertEquals(oct, m.assignments.size)
        assertEquals(15, m.previous.size) // 9 月 1–29 日共 15 次
        assertEquals("上一月", m.prevLabel)
        assertEquals(15, Analytics.window(c, Analytics.Spec(Analytics.Range.MONTH, offset = -1), now).assignments.size)
        // 周：周一开始，7 天
        val (a, b) = Analytics.periodBounds(Analytics.Range.WEEK, 0, now)
        assertEquals(Calendar.MONDAY, Calendar.getInstance().apply { timeInMillis = a }.get(Calendar.DAY_OF_WEEK))
        assertEquals(7 * day, b - a)
        // 季度：7–9 月
        val (q0, q1) = Analytics.periodBounds(Analytics.Range.QUARTER, -1, now)
        assertEquals(at(2026, 7, 1), q0); assertEquals(at(2026, 10, 1), q1)
        // 自定义：9/1–9/10（含）= 5 次，上一段同长
        val cu = Analytics.window(c, Analytics.Spec(Analytics.Range.CUSTOM, from = at(2026, 9, 1), to = at(2026, 9, 10)), now)
        assertEquals(5, cu.assignments.size)
        assertTrue(cu.previous.isEmpty())
        // 过去的月份：积分变化按月底计算
        val r = Analytics.report(c, ScoreRules(), Analytics.Spec(Analytics.Range.MONTH, offset = -1), now)
        assertEquals(15, r.kpi.assignments)
        assertEquals(-30.0, r.students.first { it.student.id == "s2" }.scoreDelta, 1e-9) // 9 月 15 次未交 × -2
    }

    @Test fun progressComparesBeforeAndAfterReset() {
        val rules = ScoreRules()
        val c0 = cls()
        val c = c0.recharge(rules, c0.students.map { it.id }, 100.0, time = at(2026, 10, 1), effectiveAt = at(2026, 10, 1))
        val pg = Analytics.progress(c, rules)!!
        assertNotNull(pg.reset)
        assertEquals("s2", pg.stars.first().student.id)
        assertEquals("s3", pg.watch.first().student.id)
        assertTrue(pg.stars.none { it.student.id == "s1" })
        assertTrue(pg.stars.first().reasons.first().startsWith("交齐率"))
        // 没有重置时比较前后两半
        val half = Analytics.progress(c0, rules)!!
        assertNull(half.reset)
        assertEquals("s2", half.stars.first().student.id)
    }

    @Test fun moreAnalysisItems() {
        val rules = ScoreRules()
        val c0 = cls()
        val c = c0.recharge(rules, c0.students.map { it.id }, 100.0, time = at(2026, 10, 1), effectiveAt = at(2026, 10, 1))
            .copy(scores = listOf(ScoreEntry(studentId = "s1", delta = 2.0, reason = "主动回答", time = at(2026, 10, 3)), ScoreEntry(studentId = "s4", delta = 1.0, reason = "主动回答", time = at(2026, 10, 4))))
        val r = Analytics.report(c, rules, Analytics.Spec(Analytics.Range.ALL), at(2026, 11, 1))
        // 风险：s3 最近一直未交
        assertEquals("s3", r.risk.first().student.id)
        assertTrue(r.risk.first().prob > 0.9)
        // 各轮：重置前 / 本轮
        assertEquals(2, r.rounds.size)
        assertEquals(15, r.rounds[0].kpi.assignments); assertEquals(15, r.rounds[1].kpi.assignments)
        // 原因统计
        assertEquals("主动回答", r.reasons.first().reason); assertEquals(2, r.reasons.first().count); assertEquals(3.0, r.reasons.first().total, 1e-9)
        assertEquals(3.0, r.sources.manualPlus, 1e-9)
        // 按月汇总（跨 2 个月）
        assertEquals("周", r.bucketUnit)
        assertTrue(r.buckets.size >= 8)
        // 分层：s1 全勤
        assertTrue(r.tiers.first { it.name == "全勤" }.students.any { it.student.id == "s1" })
        // 连续交齐：s1 30 次、s2 15 次
        assertEquals("s1", r.streakers.first().student.id); assertEquals(30, r.streakers.first().handStreak)
        assertTrue(Analytics.text(c, r).contains("进步之星"))
    }
}
