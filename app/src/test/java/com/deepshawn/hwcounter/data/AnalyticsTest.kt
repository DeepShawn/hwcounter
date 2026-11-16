package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class AnalyticsTest {
    private val day = 24L * 3600 * 1000
    private val now = 100 * day

    private fun cls(): SchoolClass {
        val st = (1..4).map { Student(id = "s$it", no = "0$it", name = "学生$it", group = if (it <= 2) "一组" else "二组") }
        // 12 次作业：s1 全交；s2 后 3 次连续未交；s3 前半缺交后半全交；s4 请假一次、迟交一次
        val assigns = (1..12).map { i ->
            val m = HashMap<String, HwStatus>()
            m["s1"] = if (i % 4 == 0) HwStatus.EXCELLENT else HwStatus.SUBMITTED
            m["s2"] = if (i >= 10) HwStatus.MISSING else HwStatus.SUBMITTED
            m["s3"] = if (i <= 6) HwStatus.MISSING else HwStatus.SUBMITTED
            m["s4"] = when (i) { 3 -> HwStatus.LEAVE; 5 -> HwStatus.LATE; else -> HwStatus.SUBMITTED }
            Assignment(id = "a$i", title = "作业$i", subject = if (i % 2 == 0) "数学" else "语文", createdAt = now - (13 - i) * day, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = assigns)
    }

    @Test fun threeRanges() {
        val c = cls()
        val all = Analytics.window(c, Analytics.Range.ALL)
        assertEquals(12, all.assignments.size); assertTrue(all.previous.isEmpty())
        val recent = Analytics.window(c, Analytics.Range.RECENT10)
        assertEquals(10, recent.assignments.size); assertEquals("a3", recent.assignments.first().id)
        assertEquals(listOf("a1", "a2"), recent.previous.map { it.id })
        // 没有重置：本轮 = 全部
        assertEquals(12, Analytics.window(c, Analytics.Range.PERIOD).assignments.size)
        // 全班从作业 9 起重置：本轮 4 次，上一时段 8 次
        val reset = c.recharge(FLAT, c.students.map { it.id }, 100.0, time = now, assignment = c.assignments.first { it.id == "a9" })
        val w = Analytics.window(reset, Analytics.Range.PERIOD)
        assertEquals(listOf("a9", "a10", "a11", "a12"), w.assignments.map { it.id })
        assertEquals(8, w.previous.size)
        assertNotNull(w.reset)
        // 只重置了一人，不算全班重置
        val one = c.recharge(FLAT, listOf("s1"), 100.0, time = now, assignment = c.assignments.first { it.id == "a9" })
        assertNull(Analytics.window(one, Analytics.Range.PERIOD).reset)
    }

    @Test fun kpiCountsRatesAndPerfectRuns() {
        val c = cls()
        val k = Analytics.kpi(c, c.assignments)
        assertEquals(9, k.missing); assertEquals(1, k.late); assertEquals(1, k.leave)
        assertEquals(38f / 47, k.handInRate, 1e-4f)
        assertEquals(37f / 47, k.onTimeRate, 1e-4f)
        assertEquals(3, k.perfect)
    }

    @Test fun insightsFindStreaksImprovementAndGroups() {
        val r = Analytics.report(cls(), FLAT, Analytics.Range.ALL, now)
        val s2 = r.students.first { it.student.id == "s2" }
        assertEquals(3, s2.missingStreak)
        val s3 = r.students.first { it.student.id == "s3" }
        assertEquals(6, s3.longestMissing)
        assertEquals("s2", r.attention.first().student.id)
        assertEquals(listOf("s3"), r.improved.map { it.student.id })
        assertEquals(listOf("s1"), r.perfectAttendance.map { it.student.id })
        assertEquals(3, r.longestPerfectRun)
        assertEquals(4, r.rateBins.sumOf { it.second })
        assertEquals(2, r.groupStats.size)
        assertEquals(10, r.scoreTrend.size)   // 积分走势最多取 10 个点
        assertTrue(r.insights.any { it.text.contains("学生2") && it.text.contains("连续") })
        val text = Analytics.text(cls(), r, now)
        assertTrue(text.contains("交齐率")); assertTrue(text.contains("学生2 缺交 3 次（连续 3 次）")); assertTrue(Regex("进步之星（[^）]*）：学生3").containsMatchIn(text))
    }

    @Test fun scoreDeltaWithinPeriodStartsFromResetTarget() {
        val c0 = cls()
        val c = c0.recharge(FLAT, c0.students.map { it.id }, 50.0, time = now, assignment = c0.assignments.first { it.id == "a9" })
        val r = Analytics.report(c, FLAT, Analytics.Range.PERIOD, now)
        // s2 在 a10..a12 未交：50 − 6 = 44，变化 −6
        val s2 = r.students.first { it.student.id == "s2" }
        assertEquals(44.0, s2.score, 1e-9); assertEquals(-6.0, s2.scoreDelta, 1e-9)
        assertEquals("s2", r.fallers.first().student.id)
    }

    @Test fun emptyClassDoesNotCrash() {
        val r = Analytics.report(SchoolClass(name = "空"), FLAT, Analytics.Range.RECENT10, now)
        assertEquals(0, r.kpi.assignments); assertTrue(r.scoreBins.isEmpty()); assertNull(r.previous)
        assertTrue(Analytics.text(SchoolClass(name = "空"), r, now).isNotBlank())
        assertTrue(Analytics.examReport(SchoolClass(name = "空")).exams.isEmpty())
    }

    @Test fun statsHelpers() {
        assertEquals(1.0, Analytics.slope(listOf(1.0, 2.0, 3.0, 4.0)), 1e-9)
        assertEquals(1.0, Analytics.pearson(listOf(1.0, 2.0, 3.0, 4.0), listOf(2.0, 4.0, 6.0, 8.0))!!, 1e-9)
        assertNull(Analytics.pearson(listOf(1.0, 2.0), listOf(1.0, 2.0)))
    }
}
