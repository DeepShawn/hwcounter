package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class ExamAnalysisTest {
    private val day = 86_400_000L

    /** 10 人，三场数学：s0 逐场上升，s9 逐场下滑，其余不变 */
    private fun cls(): SchoolClass {
        val st = (0 until 10).map { Student(id = "s$it", no = "%02d".format(it + 1), name = "学生$it", gender = if (it % 2 == 0) "男" else "女", group = if (it < 5) "一组" else "二组") }
        fun exam(k: Int, f: (Int) -> Double) = Exam(id = "e$k", title = "月考$k", subject = "数学", date = k * 10 * day, scores = st.associate { s -> val i = s.id.drop(1).toInt(); s.id to f(i) })
        val base = { i: Int -> 50.0 + i * 5 } // 50..95
        val exams = listOf(
            exam(1) { i -> when (i) { 0 -> 40.0; 9 -> 98.0; else -> base(i) } },
            exam(2) { i -> when (i) { 0 -> 60.0; 9 -> 80.0; else -> base(i) } },
            exam(3) { i -> when (i) { 0 -> 85.0; 9 -> 58.0; else -> base(i) } },
        )
        val hw = (1..6).map { k -> Assignment(id = "a$k", title = "作业$k", createdAt = k * day, statuses = st.associate { s -> s.id to if (s.id == "s8" && k % 2 == 0) HwStatus.MISSING else HwStatus.SUBMITTED }) }
        return SchoolClass(name = "一班", students = st, exams = exams, assignments = hw)
    }

    @Test fun quantilesDifficultyDiscrimination() {
        assertEquals(2.5, Analytics.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.5), 1e-9)
        assertEquals(1.75, Analytics.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.25), 1e-9)
        val c = cls()
        val d = Analytics.examDeep(c, c.exams[1])
        assertEquals(0.85 - 175.0 / 300, d.discrimination, 1e-9) // 前 3 人 90、85、80；后 3 人 55、60、60
        assertEquals("适中", Analytics.difficultyText(0.72))
        assertEquals("偏低", Analytics.discriminationText(0.1))
        // 名次进退：s0 从第 10 名到更靠前
        assertTrue(d.rankUp.any { it.first.id == "s0" })
        assertTrue(d.rankDown.any { it.first.id == "s9" })
        assertEquals(2, d.genders.size); assertEquals(2, d.groups.size)
    }

    @Test fun progressFlowAndStudentsLists() {
        val c = cls()
        val r = Analytics.examReport(c)
        assertEquals("s0", r.stars.first().student.id)
        assertEquals("s9", r.watch.first().student.id)
        assertEquals(3, r.quality.size)
        val f = r.flow!!
        assertEquals("月考2", f.from.title); assertEquals("月考3", f.to.title)
        assertTrue(f.up >= 1 && f.down >= 1)
        assertEquals(10, f.up + f.down + f.same)
        assertTrue(r.rising.any { it.student.id == "s0" })
        assertTrue(r.falling.any { it.student.id == "s9" })
        // s1 = 55 分 → 临界及格
        assertTrue(r.nearPass.any { it.student.id == "s1" })
        assertEquals(10, r.levels.sumOf { it.second })
        // s8 作业常缺（交齐 50%）但考试 90% → 会但不交
        assertTrue(r.skipButGood.any { it.student.id == "s8" })
        assertTrue(Analytics.text(c, Analytics.report(c, ScoreRules(), Analytics.Range.ALL), exam = r).contains("考试进步之星"))
    }

    @Test fun imbalanceAcrossSubjects() {
        val c0 = cls()
        val chinese = Exam(id = "y1", title = "语文月考", subject = "语文", date = 5 * day, scores = c0.students.associate { it.id to if (it.id == "s5") 40.0 else 75.0 })
        val r = Analytics.examReport(c0.copy(exams = c0.exams + chinese))
        val im = r.imbalance.first { it.student.id == "s5" }
        assertEquals("数学", im.best.first); assertEquals("语文", im.worst.first)
    }
}
