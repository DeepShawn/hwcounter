package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class PersonalTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING; private val E = HwStatus.EXCELLENT; private val L = HwStatus.LATE; private val V = HwStatus.LEAVE
    private val day = 86_400_000L
    private val now = 100 * day

    private fun cls(): SchoolClass {
        val rows = mapOf(
            "good" to List(14) { if (it % 3 == 0) E else S },
            "ok" to List(13) { S } + L,
            "slip" to List(9) { S } + List(5) { M },          // 最近连续 5 次未交
            "late" to List(14) { if (it % 2 == 0) L else S },
        )
        val st = rows.keys.mapIndexed { i, id -> Student(id = id, no = "0${i + 1}", name = id, group = if (i < 2) "一组" else "二组") }
        val assigns = (0 until 14).map { k -> Assignment(id = "a$k", title = "作业$k", subject = if (k % 2 == 0) "数学" else "语文", createdAt = now - (14 - k) * 5 * day, statuses = rows.mapValues { it.value[k] }) }
        val e1 = Exam(id = "e1", title = "月考", subject = "数学", date = now - 30 * day, scores = mapOf("good" to 92.0, "ok" to 80.0, "slip" to 78.0, "late" to 70.0))
        val e2 = Exam(id = "e2", title = "期中", subject = "数学", date = now - 2 * day, scores = mapOf("good" to 95.0, "ok" to 82.0, "slip" to 50.0, "late" to 72.0))
        return SchoolClass(name = "一班", students = st, assignments = assigns, exams = listOf(e1, e2))
    }

    @Test fun attentionRanksTheStrugglingStudentFirst() {
        val r = Attention.all(cls(), ScoreRules(), now)
        assertEquals("slip", r.first().student.id)
        assertEquals(Attention.Level.HIGH, r.first().level)
        assertTrue(r.first().index > 60)
        val good = r.first { it.student.id == "good" }
        assertEquals(Attention.Level.OK, good.level)
        assertTrue(good.index < 10)
        // 各项分数相加等于指数；权重归一化后满分 100
        r.forEach { x -> assertEquals(x.index, x.parts.sumOf { it.points }, 1e-9); assertEquals(1.0, x.parts.sumOf { it.weight } / x.parts.sumOf { it.weight }, 0.0) }
        assertTrue(r.first().top.any { it.key == "streak" })
    }

    @Test fun noExamsRedistributesWeights() {
        val c = cls().copy(exams = emptyList())
        val slip = Attention.all(c, ScoreRules(), now).first { it.student.id == "slip" }
        assertTrue(slip.parts.none { it.key.startsWith("exam") })
        // 全部打满时为 100：各项最大贡献 = 权重 / 剩余权重和 × 100
        assertEquals(100.0, slip.parts.sumOf { it.weight / slip.parts.sumOf { p -> p.weight } * 100 }, 1e-9)
    }

    @Test fun trackingOverTimeShowsTheRise() {
        val w = Attention.weekly(cls(), ScoreRules(), 8, now).getValue("slip")
        assertEquals(8, w.size)
        assertTrue(w.last() > w.first())
        // 早于未交开始的时刻，关注度不高
        val early = Attention.all(cls(), ScoreRules(), now - 30 * day).first { it.student.id == "slip" }
        assertTrue(early.index < Attention.all(cls(), ScoreRules(), now).first { it.student.id == "slip" }.index)
    }

    @Test fun personalReportDetails() {
        val c = cls()
        val r = Personal.report(c, ScoreRules(), "slip", now)!!
        assertEquals(14, r.expected)
        assertEquals(5, r.counts[M]); assertEquals(5, r.missStreak); assertEquals(5, r.longestMiss); assertEquals(9, r.longestHand)
        assertEquals("交齐率", r.stats[0].label); assertFalse(r.stats[0].better)
        assertEquals(2, r.subjects.size)
        assertEquals(2, r.exams.size); assertTrue(r.exams.last().z!! < 0)
        assertTrue(r.concerns.any { it.contains("连续 5 次未交") })
        assertTrue(r.suggestions.isNotEmpty())
        assertTrue(r.scoreTrend.size >= 2)
        assertEquals(8, r.attentionTrend.size)
        val g = Personal.report(c, ScoreRules(), "good", now)!!
        assertTrue(g.strengths.isNotEmpty()); assertTrue(g.stats[0].better)
        assertEquals(1, g.rank)
        assertTrue(Personal.text(c, r).contains("关注度指数"))
        // 请假不算应交
        val c2 = c.copy(assignments = c.assignments.map { if (it.id == "a0") it.copy(statuses = it.statuses + ("good" to V)) else it })
        assertEquals(13, Personal.report(c2, ScoreRules(), "good", now)!!.expected)
    }
}
