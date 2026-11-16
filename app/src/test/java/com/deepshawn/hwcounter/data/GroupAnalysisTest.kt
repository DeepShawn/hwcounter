package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class GroupAnalysisTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING; private val E = HwStatus.EXCELLENT
    private val day = 86_400_000L

    /** 一组一直交齐；二组前半段差后半段好；三组前半段好后半段差 */
    private fun cls(): SchoolClass {
        val st = listOf("a1", "a2", "b1", "b2", "c1", "c2", "x").map { id ->
            Student(id = id, no = id, name = id, group = when (id[0]) { 'a' -> "一组"; 'b' -> "二组"; 'c' -> "三组"; else -> "" })
        }
        val asg = (0 until 8).map { k ->
            val m = st.associate { it.id to S }.toMutableMap()
            if (k < 4) m["b2"] = M else m["c2"] = M
            if (k == 7) m["a1"] = E
            Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * 3 * day, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = asg, groupLeaders = mapOf("一组" to "a1"))
    }

    @Test fun battleAndProgress() {
        val r = GroupAnalysis.report(cls(), ScoreRules())
        assertEquals("一组", r.teams.first().name); assertEquals(3, r.teams.size)
        val a = r.teams[0]
        assertEquals(0, a.wins); assertEquals(8, a.ties)          // 每次都与另一组并列第一
        assertEquals(8, a.complete); assertEquals(8, a.longestComplete); assertEquals(8, a.completeStreak)
        assertEquals("a1", a.leader?.id)
        assertTrue(a.leaderPoints > 0)
        val b = r.teams.first { it.name == "二组" }
        assertEquals(0.75, b.rate, 1e-9); assertEquals(4, b.complete); assertEquals(4, b.completeStreak)
        // 进步：二组进步、三组退步
        assertEquals("二组", r.progress.first().group); assertTrue(r.progress.first().index > 0)
        assertEquals("三组", r.progress.last().group); assertTrue(r.progress.last().index < 0)
        // 对决：二组 vs 三组 各赢 4 次
        val d = GroupAnalysis.duel(cls(), "二组", "三组")
        assertEquals(4, d.aWins); assertEquals(4, d.bWins); assertEquals(0, d.draws)
        assertTrue(r.weeks.isNotEmpty())
        // 组内进步：二组的 b2
        assertEquals(listOf("b2"), GroupAnalysis.memberProgress(cls(), ScoreRules(), "二组").first.map { it.student.id })
    }
}
