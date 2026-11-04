package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class ScoresTest {
    @Test fun studentNumbersUseNaturalOrderWithoutOverflow() {
        val cases = listOf(
            listOf("10", "2", "1") to listOf("1", "2", "10"),
            listOf("010", "2", "001") to listOf("001", "2", "010"),
            listOf("A10", "a2", "A1") to listOf("A1", "a2", "A10"),
            listOf("90071992547409931", "90071992547409930", "2") to listOf("2", "90071992547409930", "90071992547409931"),
            listOf("", "10", " 2 ") to listOf(" 2 ", "10", ""),
        )
        for ((input, expected) in cases) assertEquals(expected, input.sortedWith(::compareStudentNumbers))
        assertEquals(0, compareStudentNumbers("02", "2"))
    }

    private val rules = ScoreRules()
    private fun fixture() = SchoolClass(
        name = "一班", students = listOf(Student(id = "s1", no = "01", name = "张三"), Student(id = "s2", no = "02", name = "李四")),
        assignments = (1..3).map { Assignment(id = "a$it", title = "作业", statuses = mapOf("s1" to HwStatus.MISSING), comments = mapOf("s1" to "补交中")) },
        scores = listOf(ScoreEntry(studentId = "s1", delta = 3.0, reason = "课堂表现"), ScoreEntry(studentId = "s1", delta = -1.0, reason = "迟到")),
    )

    @Test fun changingBaseKeepsAdjustmentsAndMissingCounts() {
        val c = fixture()
        assertEquals(96.0, c.studentStats(rules)[0].score, 0.0)
        val stat = c.studentStats(rules.copy(initial = 0.0))[0]
        assertEquals(-4.0, stat.score, 0.0)
        assertEquals(3, stat.missing)
        assertEquals(2, c.scores.size)
    }

    @Test fun resetPreservesHistoryAndOnlyTouchesSelectedStudentsOnce() {
        val c = fixture()
        val reset = c.resetStudentScores(rules, listOf("s1", "s1", "other-class"), 0.0)
        assertEquals(listOf(0.0, 100.0), reset.studentStats(rules).map { it.score })
        assertEquals(3, reset.studentStats(rules)[0].missing)
        assertEquals(c.assignments, reset.assignments)
        assertEquals(c.scores, reset.scores.take(c.scores.size))
        assertEquals(c.scores.size + 1, reset.scores.size)
        assertEquals("重置分数至 0", reset.scores.last().reason)
        assertEquals(-96.0, reset.scores.last().delta, 0.0)
    }

    @Test fun resetWholeClassAndContinueScoring() {
        val c = fixture().resetStudentScores(rules, listOf("s1", "s2"), 25.5)
        assertEquals(listOf(25.5, 25.5), c.studentStats(rules).map { it.score })
        assertEquals(c.scores.size, c.scores.map { it.id }.toSet().size)
        val next = c.copy(scores = c.scores + ScoreEntry(studentId = "s1", delta = 2.0, reason = "进步"))
        assertEquals(27.5, next.studentStats(rules)[0].score, 0.0)
        assertEquals(37.5, next.studentStats(rules.copy(initial = 110.0))[0].score, 0.0)
        assertEquals(3, next.studentStats(rules)[0].missing)
    }

    @Test fun invalidOrRepeatedResetDoesNotAddRecords() {
        val c = fixture().resetStudentScores(rules, listOf("s1"), -5.0)
        assertEquals(c, c.resetStudentScores(rules, listOf("s1"), -5.0))
        assertEquals(c, c.resetStudentScores(rules, emptyList(), 0.0))
        assertEquals(c, c.resetStudentScores(rules, listOf("missing"), 0.0))
        for (target in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(c, c.resetStudentScores(rules, listOf("s1"), target))
        }
    }
}
