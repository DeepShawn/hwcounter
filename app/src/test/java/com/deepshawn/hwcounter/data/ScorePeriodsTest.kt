package com.deepshawn.hwcounter.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScorePeriodsTest {
    private val rules = ScoreRules()
    private fun fixture() = SchoolClass(name = "一班", students = listOf(Student(id = "s1", no = "01", name = "张三")), assignments = listOf(
        Assignment(id = "a1", title = "第一回", createdAt = 1000, statuses = mapOf("s1" to HwStatus.MISSING), comments = mapOf("s1" to "补交中")),
        Assignment(id = "a2", title = "第二回", createdAt = 2000, statuses = mapOf("s1" to HwStatus.EXCELLENT)),
        Assignment(id = "a3", title = "第三回", createdAt = 3000, statuses = mapOf("s1" to HwStatus.LATE)),
    ), scores = listOf(
        ScoreEntry(id = "m0", studentId = "s1", delta = 3.0, reason = "表现", time = 1500),
        ScoreEntry(id = "m1", studentId = "s1", delta = 2.0, reason = "进步", time = 2500),
        ScoreEntry(id = "r0", studentId = "s1", delta = -4.0, reason = "重置分数至 0", time = 2600),
        ScoreEntry(id = "r1", studentId = "s1", delta = 5.0, reason = "调整", kind = "reset", time = 3500),
    ))
    private fun score(c: SchoolClass) = c.studentStats(rules).single().score

    @Test fun periodIsInclusiveAndPreservesAllHistory() {
        val c = fixture(); val reset = c.startScorePeriod("a2", true, 5000)
        assertEquals(104.0, score(c), 0.0); assertEquals(102.0, score(reset), 0.0)
        assertEquals(c.assignments, reset.assignments); assertEquals(c.scores, reset.scores)
        assertEquals(c.studentStats(rules).single().missing, reset.studentStats(rules).single().missing)
        assertEquals(2.0, reset.studentStats(rules.copy(initial = 0.0)).single().score, 0.0)
        assertEquals(1, reset.scoreBoundaryIndex(reset.assignments))
        assertEquals(-1, reset.scoreBoundaryIndex(reset.assignments.takeLast(1)))
        assertEquals(c, c.startScorePeriod("bad"))
    }
    @Test fun manualChoiceAndNewEntries() {
        val c = fixture().copy(scores = fixture().scores + ScoreEntry(id = "future", studentId = "s1", delta = 20.0, reason = "clock", time = 7000))
        val reset = c.startScorePeriod("a2", false, 5000)
        assertEquals(100.0, score(reset), 0.0)
        val next = reset.copy(scores = reset.scores + ScoreEntry(studentId = "s1", delta = 1.5, reason = "new", time = 5000))
        assertEquals(101.5, score(next), 0.0)
        val edited = reset.copy(assignments = reset.assignments.map { if (it.id == "a1") it.copy(statuses = mapOf("s1" to HwStatus.EXCELLENT)) else it })
        assertEquals(100.0, score(edited), 0.0); assertEquals(0, edited.studentStats(rules).single().missing)
    }
    @Test fun compensationResetsWorkWithinPeriodAndRestoreIsReversible() {
        val c = fixture().startScorePeriod("a2", true, 5000)
        val reset = c.resetStudentScores(rules, listOf("s1"), 0.0)
        assertEquals(0.0, score(reset), 0.0)
        assertEquals(99.0, score(reset.startScorePeriod("a3", true, System.currentTimeMillis() + 1000)), 0.0)
        assertEquals(104.0, score(c.restoreScoreHistory(6000)), 0.0)
    }
    @Test fun sameTimestampBoundaryAndDeletedAnchor() {
        val c = fixture().copy(assignments = fixture().assignments.map { it.copy(createdAt = 2000) })
        val reset = c.startScorePeriod("a2", true, 5000)
        assertEquals(listOf(false, true, true), reset.assignments.map { reset.countsTowardScore(it) })
        val removed = reset.copy(assignments = reset.assignments.filter { it.id != "a2" })
        assertEquals("第二回", removed.activeScoreReset?.assignmentTitle)
        assertEquals(1, removed.scoreBoundaryIndex(removed.assignments))
    }
    @Test fun backupRoundTripAndOldDefaults() {
        val c = fixture().startScorePeriod("a2", false, 5000)
        assertEquals(c, Json.decodeFromString<SchoolClass>(Json.encodeToString(c)))
        assertTrue(Json.decodeFromString<SchoolClass>("""{"name":"旧班级"}""").scoreResets.isEmpty())
        val later = c.startScorePeriod("a3", false, 6000)
        assertEquals("a3", later.activeScoreReset?.assignmentId)
        assertNull(later.restoreScoreHistory(7000).activeScoreReset)
    }
}
