package com.deepshawn.hwcounter.desktop

import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.studentStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OpsTest {
    private val stu = listOf(Student(id = "a", no = "01", name = "甲", group = "1组"), Student(id = "b", no = "02", name = "乙", group = "1组", longLeave = true), Student(id = "c", no = "03", name = "丙"))
    private val c0 = SchoolClass(id = "c1", name = "班", students = stu)

    @Test fun assignmentFlowMatchesAndroid() {
        val (c1, aid) = Ops.createAssignment(c0, "练习", "数学")
        assertEquals(HwStatus.LEAVE, c1.assignments.single().statuses["b"], "长期请假自动记请假")
        var c = Ops.markReviewFailed(c1, aid, listOf("a"))
        assertTrue(c.assignments.single().failedReview("a"))
        c = Ops.markPlagiarism(c, aid, listOf("c"), emptyList())
        assertTrue(c.assignments.single().unknownProvider("c"), "没选提供者 = 提供者不明")
        c = Ops.setStatus(c, aid, listOf("a", "c"), HwStatus.SUBMITTED)
        val a = c.assignments.single()
        assertFalse("a" in a.reviewFailed); assertTrue(a.plagiarism.isEmpty(), "改状态后取消复审和抄袭")
        c = Ops.fillRemaining(Ops.setStatus(c, aid, listOf("b"), HwStatus.NONE), aid, HwStatus.MISSING)
        assertEquals(HwStatus.MISSING, c.assignments.single().statuses["b"])
    }

    @Test fun scoresResetAndStudents() {
        val rules = ScoreRules()
        var c = Ops.addScore(c0, listOf("a", "c"), 3.0, "")
        assertEquals("加分", c.scores.first().reason)
        c = Ops.recharge(c, rules, listOf("a"), 50.0, "期中", null)
        assertEquals(50.0, c.studentStats(rules).first { it.student.id == "a" }.score)
        c = Ops.undoRecharge(c, c.recharges.single().id)
        assertEquals(103.0, c.studentStats(rules).first { it.student.id == "a" }.score)
        c = Ops.saveStudent(c, stu[0], rep = true, leader = true)
        assertEquals("a", c.groupLeaders["1组"]); assertTrue("a" in c.reps)
        c = Ops.deleteStudent(c, "a")
        assertTrue(c.scores.none { it.studentId == "a" } && "1组" !in c.groupLeaders && "a" !in c.reps)
    }

    @Test fun mergeKeepsBothSides() {
        val (base, aid) = Ops.createAssignment(c0, "练习", "")
        val local = AppData(classes = listOf(Ops.setStatus(base, aid, listOf("a"), HwStatus.SUBMITTED)))
        val remote = AppData(classes = listOf(Ops.setStatus(Ops.addScore(base, listOf("c"), 1.0, "x"), aid, listOf("c"), HwStatus.LATE)))
        val m = Account.merge(local, remote).classes.single()
        assertEquals(HwStatus.SUBMITTED, m.assignments.single().statuses["a"]); assertEquals(HwStatus.LATE, m.assignments.single().statuses["c"]); assertEquals(1, m.scores.size)
    }
}
