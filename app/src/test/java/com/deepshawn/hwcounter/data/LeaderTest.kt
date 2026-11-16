package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 组长奖惩：按周期看小组人均作业扣分（与网页 leader.test.mjs、iPad 同一组用例） */
class LeaderTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING
    private val day = 86_400_000L

    private fun cls(): SchoolClass {
        val st = listOf("a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3", "x").map { id ->
            Student(id = id, no = id, name = id, group = when (id[0]) { 'a' -> "一组"; 'b' -> "二组"; 'c' -> "三组"; else -> "" })
        }
        val asg = (0 until 6).map { k ->
            val m = st.associate { it.id to S }.toMutableMap()
            if (k == 0) m["b2"] = M
            if (k in 0..1) { m["c2"] = M; m["c3"] = M }
            Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * day, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = asg, groupLeaders = mapOf("一组" to "a1", "二组" to "b1", "三组" to "c1"))
    }
    private val each = ScoreRules(leader = LeaderRules(period = "assignment"))

    @Test fun perAssignment() {
        val lp = cls().leaderPeriods(each)
        fun pts(id: String) = lp.getValue(id).map { it.points }
        // 作业0：一组零扣分 +3、最少 +1；二组人均 0.67 = 平均 +1；三组人均 1.33 = 平均 2 倍 −2、最多 −1
        assertEquals(listOf(4.0, 4.0, 5.0, 5.0, 5.0, 5.0), pts("a1"))   // 连续 +1、+2、+3（最多 3 段），每段最多 ±5
        assertEquals(1.0, pts("b1")[0], 1e-9)
        assertEquals(5.0, pts("b1")[1], 1e-9)                          // 零扣分 +3、比上一段少 +1、连续 +1
        assertEquals(listOf(-3.0, -5.0, 4.0), pts("c1").take(3))         // 作业1：超 2 倍 −3、最多 −1、连续 −1；作业2：零扣分 +3、比上一段少 +1
        assertNull(lp["a2"]); assertNull(lp["x"])
        // 计入积分与流水
        val c = cls()
        val a1 = c.studentStats(each).first { it.student.id == "a1" }.score
        val off = c.studentStats(each.copy(leader = LeaderRules(enabled = false))).first { it.student.id == "a1" }.score
        assertEquals(off + 28.0, a1, 1e-9)
        assertTrue(c.ledger(each, "c1").any { it.title.startsWith("组长（三组）") && it.delta == -5.0 })
    }

    @Test fun perMonthAndReset() {
        val month = ScoreRules(leader = LeaderRules(period = "month"))
        val lp = cls().leaderPeriods(month)
        assertEquals(1, lp.getValue("a1").size)
        assertEquals(4.0, lp.getValue("a1").single().points, 1e-9)     // 零扣分 +3、最少 +1
        assertEquals(-4.0, lp.getValue("c1").single().points, 1e-9)    // 人均 4 超平均 2 倍 −3、最多 −1
        // 组长积分重置后：重置前的段不计入积分，连续重新算
        val c0 = cls()
        val c = c0.recharge(each, listOf("a1"), 100.0, time = 3 * day + 1, assignment = c0.assignments[3])
        val after = c.leaderPeriods(each).getValue("a1")
        assertEquals(listOf(4.0, 4.0, 5.0, 3.0, 4.0, 5.0), after.map { it.points })
        val score = c.studentStats(each).first { it.student.id == "a1" }.score
        assertEquals(100.0 + 3.0 + 4.0 + 5.0, score, 1e-9)
    }
}
