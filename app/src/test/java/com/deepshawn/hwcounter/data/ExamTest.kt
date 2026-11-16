package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class ExamTest {
    private val st = (1..5).map { Student(id = "s$it", no = "0$it", name = listOf("张三", "李四", "王五", "赵六", "孙七")[it - 1], group = if (it <= 3) "一组" else "二组") }

    @Test fun bandsAndAbsence() {
        val r = ExamRules()
        assertEquals(2.0, r.deltaFor(100.0, 100.0), 0.0)
        assertEquals(2.0, r.deltaFor(90.0, 100.0), 0.0)
        assertEquals(1.0, r.deltaFor(85.0, 100.0), 0.0)
        assertEquals(0.0, r.deltaFor(70.0, 100.0), 0.0)
        assertEquals(-1.0, r.deltaFor(59.5, 100.0), 0.0)
        assertEquals(2.0, r.deltaFor(140.0, 150.0), 0.0) // 按得分率
        assertEquals(0.0, r.copy(enabled = false).deltaFor(100.0, 100.0), 0.0)
        val e = Exam(title = "期中", scores = mapOf("s1" to 95.0), absent = listOf("s2"))
        assertEquals(-3.0, r.copy(absent = -3.0).deltaFor(e, "s2"), 0.0)
        assertEquals(0.0, r.deltaFor(e.copy(autoScore = false), "s1"), 0.0)
    }

    @Test fun examPointsCountInScoresAndRespectResets() {
        val e = Exam(id = "e1", title = "期中", date = 5_000, scores = mapOf("s1" to 95.0, "s2" to 50.0))
        var c = SchoolClass(name = "一班", students = st, exams = listOf(e))
        val rules = FLAT
        fun score(id: String) = c.studentStats(rules).first { it.student.id == id }.score
        assertEquals(102.0, score("s1"), 0.0); assertEquals(99.0, score("s2"), 0.0); assertEquals(100.0, score("s3"), 0.0)
        assertTrue(c.ledger(rules, "s1").any { it.kind == LedgerRow.Kind.EXAM && it.delta == 2.0 })
        val base = c
        c = base.recharge(rules, st.map { it.id }, 100.0, time = 9_000)
        assertEquals(100.0, score("s1"), 0.0) // 立即重置：之前的考试不再计分
        c = base.recharge(rules, st.map { it.id }, 100.0, time = 9_500, effectiveAt = 4_000)
        assertEquals(102.0, score("s1"), 0.0) // 从考试之前起重置：考试照常计分
    }

    @Test fun statsRanksAndReport() {
        val e1 = Exam(id = "e1", title = "一测", subject = "数学", date = 1, scores = mapOf("s1" to 90.0, "s2" to 80.0, "s3" to 80.0, "s4" to 50.0), absent = listOf("s5"))
        val e2 = Exam(id = "e2", title = "二测", subject = "数学", date = 2, scores = mapOf("s1" to 70.0, "s2" to 95.0, "s3" to 85.0, "s4" to 55.0, "s5" to 60.0))
        val s = e1.stat(st)
        assertEquals(4, s.count); assertEquals(1, s.absent); assertEquals(0, s.missing)
        assertEquals(75.0, s.avg, 1e-9); assertEquals(80.0, s.median, 1e-9); assertEquals(0.75, s.passRate, 1e-9)
        assertEquals(mapOf("s1" to 1, "s2" to 2, "s3" to 2, "s4" to 4), e1.ranks())
        val c = SchoolClass(name = "一班", students = st, exams = listOf(e1, e2))
        assertEquals("e1", c.previousExam(e2)?.id)
        val r = Analytics.examReport(c)
        assertEquals(2, r.exams.size)
        val zhang = r.students.first { it.student.id == "s1" }
        assertEquals(-2, zhang.rankChange) // 第 1 → 第 3
        assertEquals("s2", r.improvers.first().student.id)
        assertTrue(r.struggling.any { it.student.id == "s4" })
        assertTrue(r.insights.isNotEmpty())
    }

    @Test fun importRecognizesNameAndScoreColumns() {
        val rows = listOf(
            listOf("学号", "姓名", "数学", "语文"),
            listOf("01", "张三", "95", "88"),
            listOf("02", "李四", "缺考", "76"),
            listOf("3", "王五", "81.5", "90"),
            listOf("", "路人甲", "70", "60"),
        )
        val r = ExamImport.recognize(rows, st)
        assertEquals(listOf("数学", "语文"), r.columns.map { it.header })
        assertEquals(mapOf("s1" to 95.0, "s3" to 81.5), r.scoresOf(0))
        assertEquals(listOf("s2"), r.absentOf(0))
        assertEquals(1, r.unmatched.size)
        assertEquals(listOf("s4", "s5"), r.missingStudents)
        assertEquals(100.0, ExamImport.guessFull(95.0), 0.0)
        assertEquals(150.0, ExamImport.guessFull(132.0), 0.0)
    }

    @Test fun groupLeaders() {
        var c = SchoolClass(name = "一班", students = st)
        c = c.setGroupLeader("一组", "s2")
        assertEquals("李四", c.leaderOf("一组")?.name)
        assertNull(c.copy(students = c.students.map { if (it.id == "s2") it.copy(group = "二组") else it }).leaderOf("一组"))
        assertNull(c.setGroupLeader("一组", null).leaderOf("一组"))
    }

    @Test fun jsonRoundTrip() {
        val c = SchoolClass(name = "一班", students = st, exams = listOf(Exam(title = "期中", scores = mapOf("s1" to 99.5))), groupLeaders = mapOf("一组" to "s1"))
        val d = AppData(classes = listOf(c), settings = Settings(rules = ScoreRules(exam = ExamRules(absent = -2.0))))
        val back = AppViewModel.json.decodeFromString(AppData.serializer(), AppViewModel.json.encodeToString(AppData.serializer(), d))
        assertEquals(d, back)
        val legacy = AppViewModel.json.decodeFromString(AppData.serializer(), """{"classes":[{"name":"旧"}],"settings":{"rules":{"initial":90}}}""")
        assertTrue(legacy.classes[0].exams.isEmpty()); assertEquals(ExamRules(), legacy.settings.rules.exam)
    }
}
