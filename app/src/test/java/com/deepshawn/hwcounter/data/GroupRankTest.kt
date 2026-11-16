package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 小组对抗排名与整组加减分（与网页 grouprank.test.mjs、iPad 同一组用例） */
class GroupRankTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING; private val E = HwStatus.EXCELLENT
    private val day = 86_400_000L

    private fun cls(): SchoolClass {
        val st = listOf("a1", "a2", "b1", "b2", "c1", "c2", "x").map { id ->
            Student(id = id, no = id, name = id, group = when (id[0]) { 'a' -> "一组"; 'b' -> "二组"; 'c' -> "三组"; else -> "" })
        }
        val asg = (0 until 2).map { k ->
            val m = st.associate { it.id to S }.toMutableMap()
            m["a1"] = E                 // 一组：全交、有优秀
            m["c2"] = M                 // 三组：每次 1 人未交
            if (k == 1) m["b2"] = HwStatus.LATE
            Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * day, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = asg)
    }
    private val each = ScoreRules(leader = LeaderRules(enabled = false), groupRank = GroupRankRules(period = "assignment"))

    @Test fun rankAndPoints() {
        val ps = cls().groupRankPeriods(each)
        assertEquals(2, ps.size)
        // 作业0：一组（交齐、优秀 50%）> 二组（交齐）> 三组（交齐 50%、扣分最多）
        val p0 = ps[0].standings
        assertEquals(listOf("一组", "二组", "三组"), p0.map { it.group })
        assertEquals(listOf(1, 2, 3), p0.map { it.rank })
        assertEquals(listOf(2.0, 1.0, -1.0), p0.map { it.points })   // 第 1 +2、第 2 +1、最后 −1
        // 综合分：一组 (0.5×1 + 0.25×1 + 0.15×0.5) / 0.9
        assertEquals(Math.round((0.5 + 0.25 + 0.075) / 0.9 * 10000) / 10000.0, p0[0].score, 1e-9)
        // 计入积分（每人）与流水
        val c = cls()
        val on = c.studentStats(each).associate { it.student.id to it.score }
        val off = c.studentStats(each.copy(groupRank = GroupRankRules(enabled = false))).associate { it.student.id to it.score }
        assertEquals(4.0, on.getValue("a2") - off.getValue("a2"), 1e-9)
        assertEquals(-2.0, on.getValue("c1") - off.getValue("c1"), 1e-9)
        assertEquals(0.0, on.getValue("x") - off.getValue("x"), 1e-9)
        assertTrue(c.ledger(each, "a2").any { it.title.startsWith("小组排名（一组）") && it.delta == 2.0 })
    }

    @Test fun tiesAndTwoGroups() {
        // 只有两组且完全相同：并列第 1，都 +2，没有最后一名
        val st = listOf("a1", "a2", "b1", "b2").map { Student(id = it, no = it, name = it, group = if (it[0] == 'a') "一组" else "二组") }
        val c = SchoolClass(name = "班", students = st, assignments = listOf(Assignment(id = "a0", title = "作业", createdAt = day, statuses = st.associate { it.id to S })))
        val p = c.groupRankPeriods(each).single().standings
        assertEquals(listOf(1, 1), p.map { it.rank }); assertEquals(listOf(2.0, 2.0), p.map { it.points })
        // 按周：两次作业在同一周时合成一段
        val week = cls().groupRankPeriods(each.copy(groupRank = GroupRankRules(period = "month")))
        assertEquals(1, week.size)
    }

    @Test fun plusOrMinusOnly() {
        val plus = each.copy(groupRank = GroupRankRules(period = "assignment", mode = "plus"))
        val minus = each.copy(groupRank = GroupRankRules(period = "assignment", mode = "minus"))
        assertEquals(listOf(2.0, 1.0, 0.0), cls().groupRankPeriods(plus)[0].standings.map { it.points })
        assertEquals(listOf(0.0, 0.0, -1.0), cls().groupRankPeriods(minus)[0].standings.map { it.points })
        // 组长：仅加分 / 仅扣分
        val c = cls().copy(groupLeaders = mapOf("三组" to "c1"))
        fun lr(mode: String) = ScoreRules(groupRank = GroupRankRules(enabled = false), leader = LeaderRules(period = "assignment", mode = mode))
        assertTrue(c.leaderPeriods(lr("plus")).getValue("c1").all { it.points >= 0 })
        assertTrue(c.leaderPeriods(lr("minus")).getValue("c1").all { it.points <= 0 })
        assertTrue(c.leaderPeriods(lr("both")).getValue("c1").any { it.points < 0 })
    }
}
