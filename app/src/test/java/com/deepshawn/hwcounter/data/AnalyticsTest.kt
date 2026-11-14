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

    @Test fun rangesPickRecentAndPreviousWindow() {
        val (cur, prev) = Analytics.pick(cls().assignments, Analytics.Range.RECENT10, now)
        assertEquals(10, cur.size); assertEquals("a3", cur.first().id)
        assertEquals(listOf("a1", "a2"), prev.map { it.id })
        assertEquals(12, Analytics.pick(cls().assignments, Analytics.Range.DAYS30, now).first.size)
        assertTrue(Analytics.pick(cls().assignments, Analytics.Range.ALL, now).second.isEmpty())
    }

    @Test fun kpiCountsRatesAndPerfectRuns() {
        val c = cls()
        val k = Analytics.kpi(c, c.assignments)
        // 应交 = 12*4 − 1 请假 = 47；缺交 = 3 + 6 = 9；交齐 = 38；按时 = 37
        assertEquals(9, k.missing); assertEquals(1, k.late); assertEquals(1, k.leave)
        assertEquals(38f / 47, k.handInRate, 1e-4f)
        assertEquals(37f / 47, k.onTimeRate, 1e-4f)
        assertEquals(3, k.perfect) // a7, a8, a9
    }

    @Test fun insightsFindStreaksAndImprovement() {
        val r = Analytics.report(cls(), ScoreRules(), Analytics.Range.ALL, now)
        val s2 = r.students.first { it.student.id == "s2" }
        assertEquals(3, s2.missingStreak)
        assertEquals("s2", r.attention.first().student.id)
        assertEquals(listOf("s3"), r.improved.map { it.student.id })
        assertEquals(listOf("s1"), r.perfectAttendance.map { it.student.id })
        assertEquals(2, r.subjects.size)
        assertEquals(12, r.points.size)
        assertEquals(4, r.scoreBins.sumOf { it.second })
        assertEquals("一组", r.groups.first().first)
        val text = Analytics.text(cls(), r, now)
        assertTrue(text.contains("交齐率")); assertTrue(text.contains("学生2 缺交 3 次（连续 3 次）")); assertTrue(text.contains("进步之星】学生3"))
    }

    @Test fun emptyClassDoesNotCrash() {
        val r = Analytics.report(SchoolClass(name = "空"), ScoreRules(), Analytics.Range.RECENT10, now)
        assertEquals(0, r.kpi.assignments); assertTrue(r.scoreBins.isEmpty()); assertNull(r.previous)
        assertTrue(Analytics.text(SchoolClass(name = "空"), r, now).isNotBlank())
    }
}
